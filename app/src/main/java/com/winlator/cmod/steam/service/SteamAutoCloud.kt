package com.winlator.cmod.steam.service

import androidx.room.withTransaction
import com.winlator.cmod.steam.data.PostSyncInfo
import com.winlator.cmod.steam.data.SaveFilePattern
import com.winlator.cmod.steam.data.SteamApp
import com.winlator.cmod.steam.data.UserFileInfo
import com.winlator.cmod.steam.data.UserFilesDownloadResult
import com.winlator.cmod.steam.data.UserFilesUploadResult
import com.winlator.cmod.steam.enums.PathType
import com.winlator.cmod.steam.enums.SaveLocation
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.service.SteamService.Companion.FileChanges
import com.winlator.cmod.steam.service.SteamService.Companion.getAppDirPath
import com.winlator.cmod.steam.utils.FileUtils
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.utils.SteamUtils
import `in`.dragonbra.javasteam.enums.EPlatformType
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.AppFileChangeList
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.AppFileInfo
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Date
import java.util.stream.Collectors
import java.util.zip.ZipInputStream
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.time.measureTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.OutputStream
import java.net.SocketTimeoutException

/**
 * [Steam Auto Cloud](https://partner.steamgames.com/doc/features/cloud#steam_auto-cloud)
 */
object SteamAutoCloud {

    private const val MAX_USER_FILE_RETRIES = 3

    private fun streamingShaHash(path: Path): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        val buffer = ByteArray(8192)
        BufferedInputStream(Files.newInputStream(path)).use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest()
    }

    private fun findPlaceholderWithin(aString: String): Sequence<MatchResult> =
        Regex("%\\w+%").findAll(aString)

    private inline fun InputStream.copyTo(
        out: OutputStream,
        bufferSize: Int = 8 * 1024,
        progress: (Long) -> Unit,
    ) {
        val buf = ByteArray(bufferSize)
        var bytesRead: Int
        var total = 0L
        while (read(buf).also { bytesRead = it } >= 0) {
            if (bytesRead == 0) continue
            out.write(buf, 0, bytesRead)
            total += bytesRead
            progress(total)
        }
    }

    fun syncUserFiles(
        appInfo: SteamApp,
        clientId: Long,
        steamInstance: SteamService,
        steamCloud: SteamCloud,
        preferredSave: SaveLocation = SaveLocation.None,
        parentScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
        prefixToPath: (String) -> String,
        overrideLocalChangeNumber: Long? = null,
        onProgress: ((message: String, progress: Float) -> Unit)? = null,
    ): Deferred<PostSyncInfo?> = parentScope.async {
        val postSyncInfo: PostSyncInfo?

        Timber.i("Retrieving save files of ${appInfo.name}")

        val uploadRootRemap: Map<String, String> = appInfo.ufs.saveFilePatterns
            .filter { it.uploadRoot != it.root }
            .associate { "%${it.uploadRoot.name}%" to it.root.name }

        // Full-prefix remap: addPath сдвигает локальный подкаталог относительно
        // облачного пути (rootoverride/uploadRoot). Ключей два (со слэшем и без),
        // т.к. облачные префиксы иногда приходят с trailing slash.
        val cloudPrefixToLocalPath: Map<String, String> = appInfo.ufs.saveFilePatterns
            .filter { it.uploadPath != it.path }
            .flatMap { saveFile ->
                val localPath = Paths.get(prefixToPath(saveFile.root.name), saveFile.substitutedPath).pathString
                val cloudPath = saveFile.uploadPath
                    .replace("\\", "/")
                    .replace("{64BitSteamID}", SteamUtils.getSteamId64().toString())
                    .replace("{Steam3AccountID}", SteamUtils.getSteam3AccountId().toString())
                    .trim('/')
                val cloudRoot = "%${saveFile.uploadRoot.name}%"
                val cloudPrefixes = if (cloudPath.isBlank()) {
                    listOf(cloudRoot)
                } else {
                    listOf(
                        "$cloudRoot$cloudPath",
                        "$cloudRoot/$cloudPath",
                    )
                }
                cloudPrefixes.map { cloudKey -> cloudKey to localPath }
            }
            .toMap()

        val getPathTypePairs: (AppFileChangeList) -> List<Pair<String, String>> = { fileList ->
            fileList.pathPrefixes
                .map {
                    var matchResults = findPlaceholderWithin(it).map { it.value }.toList()
                    val bare = if (it.startsWith("ROOT_MOD")) listOf("ROOT_MOD") else emptyList()

                    Timber.i("Mapping prefix $it and found $matchResults")

                    if (matchResults.isEmpty()) {
                        matchResults = List(1) { PathType.DEFAULT.name }
                    }

                    matchResults + bare
                }
                .flatten()
                .distinct()
                .map { placeholder ->
                    val localRootName = uploadRootRemap[placeholder] ?: placeholder
                    placeholder to prefixToPath(localRootName)
                }
        }

        val convertPrefixes: (AppFileChangeList) -> List<String> = { fileList ->
            val pathTypePairs = getPathTypePairs(fileList)

            fileList.pathPrefixes.map { prefix ->
                // Сначала full-prefix match: покрывает addPath, где облачный путь
                // опускает подпапку из локального. Берём самый длинный ключ.
                val cloudPrefix = prefix.trimEnd('/')
                cloudPrefixToLocalPath.entries
                    .filter { (cloudKey, _) -> cloudPrefix == cloudKey || cloudPrefix.startsWith("$cloudKey/") }
                    .maxByOrNull { (cloudKey, _) -> cloudKey.length }
                    ?.let { (cloudKey, localPath) ->
                        Paths.get(localPath, cloudPrefix.removePrefix(cloudKey).trimStart('/')).pathString
                    }
                    ?: run {
                        var modified = prefix

                        val prefixContainsNoPlaceholder = findPlaceholderWithin(prefix).none()

                        if (prefixContainsNoPlaceholder) {
                            modified = Paths.get(PathType.DEFAULT.name, prefix).pathString
                        }

                        pathTypePairs.forEach {
                            modified = modified.replace(it.first, it.second)
                        }

                        if (modified == prefix) {
                            modified = Paths.get(prefixToPath(PathType.DEFAULT.name), modified).toString()
                        }

                        modified
                    }
            }
        }

        val getFilePrefix: (AppFileInfo, AppFileChangeList) -> String = { file, fileList ->
            if (file.pathPrefixIndex < fileList.pathPrefixes.size) {
                Paths.get(fileList.pathPrefixes[file.pathPrefixIndex]).pathString
            } else {
                ""
            }
        }

        val getFilePrefixPath: (AppFileInfo, AppFileChangeList) -> String = { file, fileList ->
            Paths.get(getFilePrefix(file, fileList), file.filename).pathString
        }

        val getFullFilePath: (AppFileInfo, AppFileChangeList) -> Path = getFullFilePath@{ file, fileList ->
            val gameInstallPrefix = "%${PathType.GameInstall.name}%"
            if (file.filename.startsWith(gameInstallPrefix)) {
                // Steam API иногда кладёт префикс в filename вместо pathPrefixIndex.
                // Убираем встроенный префикс (и ведущий слэш) до голого имени.
                val stripped = file.filename.removePrefix(gameInstallPrefix).trimStart('/')
                // Windows rootoverride может маппить GameInstall в другой каталог —
                // качаем туда, чтобы игра нашла сейвы.
                val remapped = cloudPrefixToLocalPath[gameInstallPrefix]
                return@getFullFilePath if (remapped != null) {
                    Paths.get(remapped, stripped)
                } else {
                    Paths.get(
                        prefixToPath(PathType.GameInstall.name),
                        stripped,
                    )
                }
            }

            val convertedPrefixes = convertPrefixes(fileList)

            if (file.pathPrefixIndex < fileList.pathPrefixes.size) {
                Paths.get(convertedPrefixes[file.pathPrefixIndex], file.filename)
            } else {
                // if the file does not reference any prefix then we need to set it to the default path
                Paths.get(prefixToPath(PathType.DEFAULT.name), file.filename)
            }
        }

        val getFilesDiff: (List<UserFileInfo>, List<UserFileInfo>) -> Pair<Boolean, FileChanges> = { currentFiles, oldFiles ->
            val overlappingFiles = currentFiles.filter { currentFile ->
                oldFiles.any { currentFile.prefixPath == it.prefixPath }
            }

            val newFiles = currentFiles.filter { currentFile ->
                !oldFiles.any { currentFile.prefixPath == it.prefixPath }
            }

            val deletedFiles = oldFiles.filter { oldFile ->
                !currentFiles.any { oldFile.prefixPath == it.prefixPath }
            }

            val modifiedFiles = overlappingFiles.filter { file ->
                oldFiles.first {
                    it.prefixPath == file.prefixPath
                }.let {
                    Timber.i("Comparing SHA of ${it.prefixPath} and ${file.prefixPath}")
                    Timber.i("[${it.sha.joinToString(", ")}]\n[${file.sha.joinToString(", ")}]")

                    !it.sha.contentEquals(file.sha)
                }
            }

            val changesExist = newFiles.isNotEmpty() || deletedFiles.isNotEmpty() || modifiedFiles.isNotEmpty()

            changesExist to FileChanges(deletedFiles, modifiedFiles, newFiles)
        }

        val hasHashConflicts: (Map<String, List<UserFileInfo>>, AppFileChangeList) -> Boolean =
            { localUserFiles, fileList ->
                fileList.files.any { file ->
                    Timber.i("Checking for " + "${getFilePrefix(file, fileList)} in ${localUserFiles.keys}")

                    localUserFiles[getFilePrefix(file, fileList)]?.let { localUserFile ->
                        localUserFile.firstOrNull {
                            Timber.i("Comparing ${file.filename} and ${it.filename}")

                            it.filename == file.filename
                        }?.let {
                            Timber.i("Comparing SHA of ${getFilePrefixPath(file, fileList)} and ${it.prefixPath}")
                            Timber.i("[${file.shaFile.joinToString(", ")}]\n[${it.sha.joinToString(", ")}]")

                            !file.shaFile.contentEquals(it.sha)
                        }
                    } == true
                }
            }

        val getLocalUserFilesAsPrefixMap: () -> Map<String, List<UserFileInfo>> = {
            val savePatterns = appInfo.ufs.saveFilePatterns.filter { userFile -> userFile.root.isWindows }

            if (savePatterns.isNotEmpty()) {
                val result = mutableMapOf<String, MutableList<UserFileInfo>>()

                savePatterns.forEach { userFile ->
                    val basePath = Paths.get(prefixToPath(userFile.root.toString()), userFile.substitutedPath)

                    Timber.i("Looking for saves in $basePath with pattern ${userFile.pattern} (prefix ${userFile.prefix})")

                    val files = FileUtils.findFilesRecursive(
                        rootPath = basePath,
                        pattern = userFile.pattern,
                        maxDepth = 5,
                    ).map {
                        val sha = streamingShaHash(it)

                        Timber.i("Found ${it.pathString}\n\tin ${userFile.prefix}\n\twith sha [${sha.joinToString(", ")}]")

                        val relativePath = basePath.relativize(it).pathString

                        UserFileInfo(
                            root = userFile.root,
                            path = userFile.substitutedPath,
                            filename = relativePath,
                            timestamp = Files.getLastModifiedTime(it).toMillis(),
                            sha = sha,
                            cloudRoot = userFile.uploadRoot,
                            cloudPath = userFile.uploadPath,
                        )
                    }.collect(Collectors.toList())

                    Timber.i("Found ${files.size} file(s) in $basePath for pattern ${userFile.pattern}")

                    val prefixKey = Paths.get(userFile.prefix).pathString
                    result.getOrPut(prefixKey) { mutableListOf() }.addAll(files)
                }

                result
            } else {
                // Fallback: no UFS patterns; scan SteamUserData root recursively (depth 5)
                val rootType = PathType.SteamUserData
                val basePath = Paths.get(prefixToPath(rootType.toString()))

                Timber.i("No UFS patterns; scanning $basePath recursively (depth 5) under ${rootType.name}")

                val files = FileUtils.findFilesRecursive(
                    rootPath = basePath,
                    pattern = "*",
                    maxDepth = 5,
                ).map {
                    val sha = streamingShaHash(it)

                    val relativePath = basePath.relativize(it).pathString

                    Timber.i("Found ${it.pathString}\n\tin %${rootType.name}%\n\twith sha [${sha.joinToString(", ")}]")

                    // Store relative path in filename; empty path component
                    UserFileInfo(rootType, "", relativePath, Files.getLastModifiedTime(it).toMillis(), sha)
                }.collect(Collectors.toList())

                Timber.i("Found ${files.size} file(s) in $basePath for fallback recursive scan")

                mapOf(Paths.get("%${rootType.name}%").pathString to files)
            }
        }

        val fileChangeListToUserFiles: (AppFileChangeList) -> List<UserFileInfo> = { appFileListChange ->
            val pathTypePairs = getPathTypePairs(appFileListChange)

            appFileListChange.files.map {
                UserFileInfo(
                    root = if (it.pathPrefixIndex < pathTypePairs.size) {
                        PathType.from(pathTypePairs[it.pathPrefixIndex].first)
                    } else {
                        PathType.GameInstall
                    },
                    path = if (it.pathPrefixIndex < pathTypePairs.size) {
                        appFileListChange.pathPrefixes[it.pathPrefixIndex]
                    } else {
                        ""
                    },
                    filename = it.filename,
                    timestamp = it.timestamp.time,
                    sha = it.shaFile,
                )
            }
        }

        val buildUrl: (Boolean, String, String) -> String = { useHttps, urlHost, urlPath ->
            val scheme = if (useHttps) "https://" else "http://"
            "$scheme${urlHost}$urlPath"
        }

        // Скачивание одного файла: докачка через Range + .part, ретраи наружу.
        // Возвращает число байт файла или null при неуспехе.
        suspend fun attemptDownloadSingleFile(file: AppFileInfo, fileList: AppFileChangeList): Long? {
            val prefixedPath = getFilePrefixPath(file, fileList)
            val actualFilePath = getFullFilePath(file, fileList)

            Timber.i("$prefixedPath -> $actualFilePath")

            val fileDownloadInfo = try {
                steamCloud.clientFileDownload(appInfo.id, prefixedPath).await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to fetch download info for %s", prefixedPath)
                return null
            }

            if (fileDownloadInfo.urlHost.isEmpty()) {
                Timber.w("URL host of $prefixedPath was empty")
                return null
            }

            val totalFileSize = fileDownloadInfo.rawFileSize.toLong()
            val isZipped = fileDownloadInfo.fileSize != fileDownloadInfo.rawFileSize
            val partPath = actualFilePath.resolveSibling("${actualFilePath.fileName}.part")

            if (isZipped) {
                // Сжатый ответ по Range не докачать — качаем с нуля.
                runCatching { Files.deleteIfExists(partPath) }
            }

            val resumeOffset = if (!isZipped && totalFileSize > 0) {
                runCatching { Files.size(partPath) }.getOrDefault(0L).coerceIn(0L, totalFileSize)
            } else {
                0L
            }

            onProgress?.invoke("Downloading ${file.filename}", -1f)
            val httpUrl = with(fileDownloadInfo) {
                buildUrl(useHttps, urlHost, urlPath)
            }

            Timber.i("Downloading $httpUrl (resumeOffset=$resumeOffset)")

            val headers = Headers.headersOf(
                *fileDownloadInfo.requestHeaders
                    .map { listOf(it.name, it.value) }
                    .flatten()
                    .toTypedArray(),
            )

            val requestBuilder = Request.Builder()
                .url(httpUrl)
                .headers(headers)
            if (resumeOffset > 0) {
                requestBuilder.header("Range", "bytes=$resumeOffset-")
            }

            val httpClient = steamInstance.steamClient!!.configuration.httpClient

            val response = try {
                withTimeout(SteamService.requestTimeout) {
                    httpClient.newCall(requestBuilder.build()).execute()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w("Could not download $actualFilePath: %s", e.message)
                return null
            }

            response.use { resp ->
                if (resp.code == 416) {
                    // Диапазон невалиден: часть уже полная — фиксируем её.
                    val partSize = runCatching { Files.size(partPath) }.getOrDefault(-1L)
                    if (!isZipped && totalFileSize > 0 && partSize == totalFileSize) {
                        runCatching { Files.createDirectories(actualFilePath.parent) }
                        runCatching { Files.move(partPath, actualFilePath, StandardCopyOption.REPLACE_EXISTING) }
                        onProgress?.invoke("Downloading ${file.filename}", 1f)
                        return totalFileSize
                    }
                    runCatching { Files.deleteIfExists(partPath) }
                    return null
                }

                if (!resp.isSuccessful) {
                    Timber.w("File download of $prefixedPath was unsuccessful: ${resp.code}")
                    return null
                }

                // 206 = сервер принял Range, 200 при resumeOffset > 0 = игнор, качаем с нуля.
                val append = resp.code == 206 && resumeOffset > 0
                val startOffset = if (append) resumeOffset else 0L

                try {
                    var totalBytesRead = startOffset
                    var lastReportedProgress = -1f
                    val progressThreshold = 0.01f // Update every 1%

                    val copyToFile: (InputStream) -> Unit = { input ->
                        Files.createDirectories(partPath.parent)
                        FileOutputStream(partPath.toString(), append).use { fs ->
                            input.copyTo(fs, 8 * 1024) { bytesRead ->
                                totalBytesRead = startOffset + bytesRead
                                if (totalFileSize > 0) {
                                    val currentProgress = (totalBytesRead.toFloat() / totalFileSize).coerceIn(0f, 1f)
                                    if (currentProgress - lastReportedProgress >= progressThreshold || currentProgress >= 1f) {
                                        onProgress?.invoke("Downloading ${file.filename}", currentProgress)
                                        lastReportedProgress = currentProgress
                                    }
                                }
                            }
                        }
                    }

                    val streamed = try {
                        withTimeout(SteamService.responseTimeout) {
                            val body = resp.body ?: return@withTimeout false
                            body.byteStream().use { copyToFile(it) }
                            true
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w("Could not download $actualFilePath: %s", e.message)
                        false
                    }
                    if (!streamed) return null

                    val downloadedSize = runCatching { Files.size(partPath) }.getOrDefault(-1L)
                    if (totalFileSize > 0 && downloadedSize != totalFileSize) {
                        Timber.w("Size mismatch for $prefixedPath: got $downloadedSize, expected $totalFileSize (.part kept for resume)")
                        return null
                    }

                    Files.createDirectories(actualFilePath.parent)

                    if (isZipped) {
                        var unzipped = false
                        Files.newInputStream(partPath).use { fileInput ->
                            ZipInputStream(fileInput).use { zipInput ->
                                val entry = zipInput.nextEntry

                                if (entry == null) {
                                    Timber.w("Downloaded user file $prefixedPath has no zip entries")
                                } else {
                                    FileOutputStream(actualFilePath.toString()).use { out ->
                                        zipInput.copyTo(out, 8 * 1024) { }
                                    }
                                    unzipped = true

                                    if (zipInput.nextEntry != null) {
                                        Timber.e("Downloaded user file $prefixedPath has more than one zip entry")
                                    }
                                }
                            }
                        }
                        if (!unzipped) return null
                        runCatching { Files.deleteIfExists(partPath) }
                    } else {
                        try {
                            Files.move(partPath, actualFilePath, StandardCopyOption.REPLACE_EXISTING)
                        } catch (e: Exception) {
                            Timber.w("Could not finalize $actualFilePath: ${e.message}")
                            return null
                        }
                    }

                    return fileDownloadInfo.rawFileSize.toLong()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w("Could not download $actualFilePath: %s", e.message)
                    return null
                }
            }
        }

        val downloadFiles: (AppFileChangeList, CoroutineScope) -> Deferred<UserFilesDownloadResult> = { fileList, parentScope ->
            parentScope.async {
                var filesDownloaded = 0
                var bytesDownloaded = 0L
                val totalFiles = fileList.files.size

                fileList.files.forEach { file ->
                    var downloadedBytes: Long? = null

                    for (attempt in 1..MAX_USER_FILE_RETRIES) {
                        downloadedBytes = attemptDownloadSingleFile(file, fileList)
                        if (downloadedBytes != null) break
                        if (attempt < MAX_USER_FILE_RETRIES) {
                            Timber.w("Retrying download of ${file.filename} (attempt ${attempt + 1}/$MAX_USER_FILE_RETRIES)")
                            delay(1000L * attempt)
                        }
                    }

                    if (downloadedBytes != null) {
                        filesDownloaded++
                        bytesDownloaded += downloadedBytes
                    } else {
                        Timber.w("Giving up download of ${file.filename} after $MAX_USER_FILE_RETRIES attempts")
                    }
                }

                if (totalFiles > 0) {
                    onProgress?.invoke("Download complete", 1.0f)
                }

                UserFilesDownloadResult(filesDownloaded, bytesDownloaded)
            }
        }

        val uploadFiles: (FileChanges, CoroutineScope) -> Deferred<UserFilesUploadResult> = { fileChanges, parentScope ->
            parentScope.async {
                var filesUploaded = 0
                var bytesUploaded = 0L

                val filesToDelete = fileChanges.filesDeleted.map { it.prefixPath }

                val filesToUpload = fileChanges.filesCreated
                    .union(fileChanges.filesModified)
                    .map { it.prefixPath to it }
                    // Filter out entries whose files no longer exist at upload time
                    .filter { Files.exists(it.second.getAbsPath(prefixToPath)) }

                val totalFiles = filesToUpload.size

                Timber.i(
                    "Beginning app upload batch with ${filesToDelete.size} file(s) to delete " +
                        "and ${filesToUpload.size} file(s) to upload",
                )

                val uploadBatchResponse = steamCloud.beginAppUploadBatch(
                    appId = appInfo.id,
                    machineName = SteamUtils.getMachineName(steamInstance),
                    clientId = clientId,
                    filesToDelete = filesToDelete,
                    filesToUpload = filesToUpload.map { it.first },
                    appBuildId = appInfo.branches[PrefManager.getSteamSelectedBranch(appInfo.id).ifBlank { "public" }]?.buildId
                        ?: appInfo.branches["public"]?.buildId
                        ?: 0,
                ).await()

                var uploadBatchSuccess = true

                filesToUpload.map { it.second }.forEachIndexed { index, file ->
                    val absFilePath = file.getAbsPath(prefixToPath)

                    val fileSize = try {
                        Files.size(absFilePath).toInt()
                    } catch (e: Exception) {
                        Timber.w("Skipping upload of ${file.prefixPath}: ${e.javaClass.simpleName}: ${e.message}")
                        uploadBatchSuccess = false
                        return@forEachIndexed
                    }

                    Timber.i("Beginning upload of ${file.prefixPath} whose timestamp is ${file.timestamp}")

                    // Report start of upload
                    onProgress?.invoke("Uploading ${file.filename}", 0f)

                    val uploadInfo = steamCloud.beginFileUpload(
                        appId = appInfo.id,
                        filename = if (appInfo.ufs.saveFilePatterns.isEmpty()) {
                            file.path + file.filename
                        } else {
                            file.prefixPath
                        },
                        fileSize = fileSize,
                        rawFileSize = fileSize,
                        fileSha = file.sha,
                        timestamp = Date(file.timestamp),
                        uploadBatchId = uploadBatchResponse.batchID,
                    ).await()

                    var uploadFileSuccess = true
                    var bytesUploadedForFile = 0L
                    var lastReportedProgress = -1f
                    val progressThreshold = 0.01f // Update every 1% change

                    RandomAccessFile(absFilePath.pathString, "r").use { fs ->
                        uploadInfo.blockRequests.forEach { blockRequest ->
                            val httpUrl = buildUrl(
                                blockRequest.useHttps,
                                blockRequest.urlHost,
                                blockRequest.urlPath,
                            )

                            Timber.i("Uploading to $httpUrl")

                            val byteArray = ByteArray(blockRequest.blockLength)

                            fs.seek(blockRequest.blockOffset)

                            val bytesRead = fs.read(byteArray, 0, blockRequest.blockLength)

                            Timber.i("Read $bytesRead byte(s) for block")

                            val mediaType = if (blockRequest.requestHeaders.any { it.name.equals("Content-Type", ignoreCase = true) }) {
                                blockRequest.requestHeaders.first { it.name.equals("Content-Type", ignoreCase = true) }.value.toMediaTypeOrNull()
                            } else {
                                "application/octet-stream".toMediaTypeOrNull()
                            }

                            val requestBody = byteArray.toRequestBody(mediaType)

                            val headers = Headers.headersOf(
                                *blockRequest.requestHeaders
                                    .map { listOf(it.name, it.value) }
                                    .flatten()
                                    .toTypedArray(),
                            )

                            val request = Request.Builder()
                                .url(httpUrl)
                                .put(requestBody)
                                .headers(headers)
                                .addHeader("Accept", "text/html,*/*;q=0.9")
                                .addHeader("accept-encoding", "gzip,identity,*;q=0")
                                .addHeader("accept-charset", "ISO-8859-1,utf-8,*;q=0.7")
                                .addHeader("user-agent", "Valve/Steam HTTP Client 1.0")
                                .build()

                            val httpClient = steamInstance.steamClient!!.configuration.httpClient

                            Timber.i("Sending request to ${request.url} using\n$request")

                            try {
                                withTimeout(SteamService.requestTimeout) {
                                    val response = httpClient.newCall(request).execute()

                                    if (!response.isSuccessful) {
                                        Timber.w(
                                            "Failed to upload part of %s: %s, %s",
                                            file.prefixPath,
                                            response.message,
                                            response?.body.toString(),
                                        )

                                        uploadFileSuccess = false
                                        uploadBatchSuccess = false
                                    } else {
                                        // Update progress after successful block upload
                                        bytesUploadedForFile += blockRequest.blockLength
                                        if (fileSize > 0) {
                                            val currentProgress = (bytesUploadedForFile.toFloat() / fileSize).coerceIn(0f, 1f)
                                            // Only update if progress changed by at least 1% or we're at 100%
                                            if (currentProgress - lastReportedProgress >= progressThreshold || currentProgress >= 1f) {
                                                onProgress?.invoke("Uploading ${file.filename}", currentProgress)
                                                lastReportedProgress = currentProgress
                                            }
                                        }
                                    }
                                    response.close()
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error uploading block")
                                uploadFileSuccess = false
                                uploadBatchSuccess = false
                            }
                        }
                    }

                    if (uploadFileSuccess) {
                        filesUploaded++
                        bytesUploaded += fileSize
                    }

                    val commitSuccess = steamCloud.commitFileUpload(
                        transferSucceeded = uploadFileSuccess,
                        appId = appInfo.id,
                        fileSha = file.sha,
                        filename = if (appInfo.ufs.saveFilePatterns.isEmpty()) {
                            file.path + file.filename
                        } else {
                            file.prefixPath
                        },
                    ).await()

                    Timber.i("File ${file.prefixPath} commit success: $commitSuccess")
                }

                steamCloud.completeAppUploadBatch(
                    appId = appInfo.id,
                    batchId = uploadBatchResponse.batchID,
                    batchEResult = if (uploadBatchSuccess) EResult.OK else EResult.Fail,
                ).await()

                if (totalFiles > 0) {
                    onProgress?.invoke("Upload complete", 1.0f)
                }

                UserFilesUploadResult(uploadBatchSuccess, uploadBatchResponse.appChangeNumber, filesUploaded, bytesUploaded)
            }
        }

        var syncResult = SyncResult.Success
        var remoteTimestamp = 0L
        var localTimestamp = 0L
        var localBytes = 0L
        var remoteBytes = 0L
        var uploadsRequired = false
        var uploadsCompleted = true

        // sync metrics
        var filesUploaded = 0
        var filesDownloaded = 0
        var filesDeleted = 0
        var filesManaged = 0
        var bytesUploaded = 0L
        var bytesDownloaded = 0L
        var microsecTotal = 0L
        var microsecInitCaches = 0L
        var microsecValidateState = 0L
        var microsecAcLaunch = 0L
        var microsecAcPrepUserFiles = 0L
        var microsecAcExit = 0L
        var microsecDeleteFiles = 0L
        var microsecDownloadFiles = 0L
        var microsecUploadFiles = 0L

        microsecTotal = measureTime {
            val localAppChangeNumber = overrideLocalChangeNumber ?: steamInstance.changeNumbersDao.getByAppId(appInfo.id)?.changeNumber ?: -1

            val changeNumber = if (localAppChangeNumber >= 0) localAppChangeNumber else 0

            // retrieve existing user files from local storage first so we can detect missing saves
            val localUserFilesMap: Map<String, List<UserFileInfo>>
            val allLocalUserFiles: List<UserFileInfo>

            microsecInitCaches = measureTime {
                localUserFilesMap = getLocalUserFilesAsPrefixMap()
                allLocalUserFiles = localUserFilesMap.map { it.value }.flatten()
            }.inWholeMicroseconds

            // If local saves are missing but we have a stored change number, request full file list
            // (change number 0) instead of a delta, so the cloud returns all files for download
            val effectiveChangeNumber = if (allLocalUserFiles.isEmpty() && changeNumber > 0) {
                Timber.w("No local saves found but stored changeNumber=$changeNumber; requesting full file list from cloud")
                0
            } else {
                changeNumber
            }

            val appFileListChange = steamCloud.getAppFileListChange(appInfo.id, effectiveChangeNumber).await()

            val cloudAppChangeNumber = appFileListChange.currentChangeNumber

            Timber.i("AppChangeNumber: $localAppChangeNumber -> $cloudAppChangeNumber (requested with changeNumber=$effectiveChangeNumber)")

            appFileListChange.printFileChangeList(appInfo)

            val downloadUserFiles: (CoroutineScope) -> Deferred<PostSyncInfo?> = { parentScope ->
                parentScope.async {
                    Timber.i("Downloading cloud user files")

                    val remoteUserFiles = fileChangeListToUserFiles(appFileListChange)
                    val filesDiff = getFilesDiff(remoteUserFiles, allLocalUserFiles).second
                    microsecDeleteFiles = measureTime {
                        var totalFilesDeleted = 0

                        filesDiff.filesDeleted.forEach {
                            val deleted = Files.deleteIfExists(it.getAbsPath(prefixToPath))
                            if (deleted) totalFilesDeleted++
                        }

                        filesDeleted = totalFilesDeleted
                    }.inWholeMicroseconds

                    microsecDownloadFiles = measureTime {
                        val downloadInfo = downloadFiles(appFileListChange, parentScope).await()
                        filesDownloaded = downloadInfo.filesDownloaded
                        bytesDownloaded = downloadInfo.bytesDownloaded
                    }.inWholeMicroseconds

                    val updatedLocalFiles: Map<String, List<UserFileInfo>>
                    val hasLocalChanges: Boolean
                    microsecValidateState = measureTime {
                        updatedLocalFiles = getLocalUserFilesAsPrefixMap()
                        hasLocalChanges = hasHashConflicts(updatedLocalFiles, appFileListChange)
                        filesManaged = updatedLocalFiles.size
                    }.inWholeMicroseconds

                    if (hasLocalChanges) {
                        Timber.e("Failed to download latest user files after $MAX_USER_FILE_RETRIES tries")

                        syncResult = SyncResult.DownloadFail

                        return@async PostSyncInfo(syncResult)
                    }

                    with(steamInstance) {
                        db.withTransaction {
                            fileChangeListsDao.insert(appInfo.id, updatedLocalFiles.map { it.value }.flatten())
                            changeNumbersDao.insert(appInfo.id, cloudAppChangeNumber)
                        }
                    }

                    return@async null
                }
            }

            val uploadUserFiles: (CoroutineScope) -> Deferred<Unit> = { parentScope ->
                parentScope.async {
                    Timber.i("Uploading local user files")

                    val fileChanges = steamInstance.fileChangeListsDao.getByAppId(appInfo.id)!!.let {
                        val result = getFilesDiff(allLocalUserFiles, it.userFileInfo)

                        result.second
                    }

                    uploadsRequired = fileChanges.filesCreated.isNotEmpty() || fileChanges.filesModified.isNotEmpty()

                    val uploadResult: UserFilesUploadResult

                    microsecUploadFiles = measureTime {
                        uploadResult = uploadFiles(fileChanges, parentScope).await()
                        filesUploaded = uploadResult.filesUploaded
                        bytesUploaded = uploadResult.bytesUploaded
                        uploadsCompleted = uploadsRequired && uploadResult.uploadBatchSuccess
                    }.inWholeMicroseconds

                    filesManaged = allLocalUserFiles.size

                    if (uploadResult.uploadBatchSuccess) {
                        with(steamInstance) {
                            db.withTransaction {
                                fileChangeListsDao.insert(appInfo.id, allLocalUserFiles)
                                changeNumbersDao.insert(appInfo.id, uploadResult.appChangeNumber)
                            }
                        }
                    } else {
                        syncResult = SyncResult.UpdateFail
                    }
                }
            }

            val remoteHasFiles = appFileListChange.files.isNotEmpty()
            val localHasFiles = allLocalUserFiles.isNotEmpty()
            val forcingDownloadMissingLocal = remoteHasFiles && !localHasFiles && cloudAppChangeNumber >= 0
            val effectiveLocalAppChangeNumber = if (forcingDownloadMissingLocal) {
                Timber.w(
                    "Cloud has ${appFileListChange.files.size} file(s) but no local saves; forcing download (changeNumber=$cloudAppChangeNumber)"
                )
                -1
            } else {
                localAppChangeNumber
            }

            if (effectiveLocalAppChangeNumber < cloudAppChangeNumber) {
                microsecAcLaunch = measureTime {
                    var hasLocalChanges: Boolean

                    microsecAcPrepUserFiles = measureTime {
                        hasLocalChanges = if (forcingDownloadMissingLocal) {
                            false
                        } else {
                            steamInstance.fileChangeListsDao.getByAppId(appInfo.id)?.let {
                                getFilesDiff(allLocalUserFiles, it.userFileInfo).first
                            } == true
                        }
                    }.inWholeMicroseconds

                    if (!hasLocalChanges) {
                        Timber.i("No local changes but new cloud user files")

                        downloadUserFiles(parentScope).await()?.let {
                            return@async it
                        }
                    } else {
                        Timber.i("Found local changes and new cloud user files, conflict resolution...")

                        when (preferredSave) {
                            SaveLocation.Local -> {
                                uploadUserFiles(parentScope).await()
                            }

                            SaveLocation.Remote -> {
                                downloadUserFiles(parentScope).await()?.let {
                                    return@async it
                                }
                            }

                            SaveLocation.None -> {
                                syncResult = SyncResult.Conflict
                                remoteTimestamp = appFileListChange.files.map { it.timestamp.time }.maxOrNull() ?: 0L
                                localTimestamp = allLocalUserFiles.map { it.timestamp }.maxOrNull() ?: 0L
                                localBytes = allLocalUserFiles.sumOf { userFile ->
                                    runCatching { Files.size(userFile.getAbsPath(prefixToPath)) }.getOrDefault(0L)
                                }
                                remoteBytes = appFileListChange.files.sumOf { file ->
                                    runCatching { file.rawFileSize.toLong() }.getOrDefault(0L)
                                }
                            }
                        }
                    }
                }.inWholeMicroseconds
            } else if (effectiveLocalAppChangeNumber == cloudAppChangeNumber) {
                microsecAcExit = measureTime {
                    val hasLocalChanges = steamInstance.fileChangeListsDao.getByAppId(appInfo.id)
                        ?.let {
                            val result = getFilesDiff(allLocalUserFiles, it.userFileInfo)
                            result.first
                        } == true

                    if (hasLocalChanges) {
                        Timber.i("Found local changes and no new cloud user files")

                        uploadUserFiles(parentScope).await()
                    } else {
                        Timber.i("No local changes and no new cloud user files, doing nothing...")

                        syncResult = SyncResult.UpToDate
                    }
                }.inWholeMicroseconds
            } else {
                Timber.e("Local change number greater than cloud $localAppChangeNumber > $cloudAppChangeNumber")

                syncResult = SyncResult.UnknownFail
            }
        }.inWholeMicroseconds

        val microsecBuildSyncList = (microsecTotal - (microsecInitCaches + microsecValidateState + microsecAcLaunch + microsecAcExit)).coerceAtLeast(0L)

        steamCloud.appCloudSyncStats(
            appId = appInfo.id,
            platformType = EPlatformType.Android64,
            blockingAppLaunch = microsecAcLaunch > 0,
            filesUploaded = filesUploaded,
            filesDownloaded = filesDownloaded,
            filesDeleted = filesDeleted,
            bytesUploaded = bytesUploaded,
            bytesDownloaded = bytesDownloaded,
            microsecTotal = microsecTotal,
            microsecInitCaches = microsecInitCaches,
            microsecValidateState = microsecValidateState,
            microsecAcLaunch = microsecAcLaunch,
            microsecAcPrepUserFiles = microsecAcPrepUserFiles,
            microsecAcExit = microsecAcExit,
            microsecBuildSyncList = microsecBuildSyncList,
            microsecDeleteFiles = microsecDeleteFiles,
            microsecDownloadFiles = microsecDownloadFiles,
            microsecUploadFiles = microsecUploadFiles,
            filesManaged = filesManaged,
        )

        postSyncInfo = PostSyncInfo(
            syncResult = syncResult,
            remoteTimestamp = remoteTimestamp,
            localTimestamp = localTimestamp,
            uploadsRequired = uploadsRequired,
            uploadsCompleted = uploadsCompleted,
            filesUploaded = filesUploaded,
            filesDownloaded = filesDownloaded,
            filesDeleted = filesDeleted,
            filesManaged = filesManaged,
            bytesUploaded = bytesUploaded,
            bytesDownloaded = bytesDownloaded,
            localBytes = localBytes,
            remoteBytes = remoteBytes,
            microsecTotal = microsecTotal,
            microsecInitCaches = microsecInitCaches,
            microsecValidateState = microsecValidateState,
            microsecAcLaunch = microsecAcLaunch,
            microsecAcPrepUserFiles = microsecAcPrepUserFiles,
            microsecAcExit = microsecAcExit,
            microsecBuildSyncList = microsecBuildSyncList,
            microsecDeleteFiles = microsecDeleteFiles,
            microsecDownloadFiles = microsecDownloadFiles,
            microsecUploadFiles = microsecUploadFiles,
        )

        postSyncInfo
    }

    private fun AppFileChangeList.printFileChangeList(appInfo: SteamApp) {
        with(this) {
            Timber.i(
                "GetAppFileListChange(${appInfo.id}):" +
                    "\n\tTotal Files: ${files.size}" +
                    "\n\tCurrent Change Number: $currentChangeNumber" +
                    "\n\tIs Only Delta: $isOnlyDelta" +
                    "\n\tApp BuildID Hwm: $appBuildIDHwm" +
                    "\n\tPath Prefixes: \n\t\t${pathPrefixes.joinToString("\n\t\t")}" +
                    "\n\tMachine Names: \n\t\t${machineNames.joinToString("\n\t\t")}" +
                    files.joinToString {
                        "\n\t${it.filename}:" +
                            "\n\t\tshaFile: ${it.shaFile}" +
                            "\n\t\ttimestamp: ${it.timestamp}" +
                            "\n\t\trawFileSize: ${it.rawFileSize}" +
                            "\n\t\tpersistState: ${it.persistState}" +
                            "\n\t\tplatformsToSync: ${it.platformsToSync}" +
                            "\n\t\tpathPrefixIndex: ${it.pathPrefixIndex}" +
                            "\n\t\tmachineNameIndex: ${it.machineNameIndex}"
                    },
            )
        }
    }
}
