package com.winlator.cmod.steam.workshop

import android.content.Context
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.PrefManager
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetUserFiles_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.SteamID
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import org.json.JSONArray
import org.json.JSONObject
import android.system.Os
import android.system.ErrnoException

object WorkshopManager {
    private const val TAG = "SteamWorkshopManager"
    private const val MAX_PAGES = 50
    private const val PAGE_SIZE = 100
    private const val COMPLETE_MARKER = ".workshop_complete"
    private const val META_FILE = ".workshop_meta.json"
    private const val COPY_SENTINEL = ".winlator_workshop"
    private val httpClient = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    suspend fun getSubscribedItems(
        appId: Int,
        steamClient: SteamClient,
        steamId: SteamID,
    ): WorkshopFetchResult {
        Timber.tag(TAG).i("Fetching workshop subscriptions for appId=$appId")

        val unifiedMessages = steamClient.getHandler<SteamUnifiedMessages>()
        if (unifiedMessages == null) {
            Timber.tag(TAG).w("getSubscribedItems: SteamUnifiedMessages handler is null for appId=$appId")
            return WorkshopFetchResult(items = emptyList(), succeeded = false)
        }

        val publishedFile = unifiedMessages.createService(PublishedFile::class.java)

        val allItems = mutableListOf<WorkshopItem>()
        var fetchedAtLeastOnePage = false
        var allPagesSucceeded = false
        var page = 1

        while (page <= MAX_PAGES) {
            val result = fetchSubscribedFilesViaRPC(
                publishedFile = publishedFile,
                appId = appId,
                steamId = steamId,
                page = page,
            )

            if (result == null) {
                Timber.tag(TAG).w("getSubscribedItems: page $page returned null (timeout or error) for appId=$appId")
                if (page == 1) {
                    // If first page fails, wait 2s and retry once
                    kotlinx.coroutines.delay(2000L)
                    val retryResult = fetchSubscribedFilesViaRPC(
                        publishedFile = publishedFile,
                        appId = appId,
                        steamId = steamId,
                        page = page,
                    )
                    if (retryResult != null) {
                        Timber.tag(TAG).i("getSubscribedItems: retry succeeded for page $page")
                        fetchedAtLeastOnePage = true
                        allItems.addAll(retryResult.items.map { item ->
                            if (item.appId == 0) item.copy(appId = appId) else item
                        })
                        if (retryResult.items.isEmpty() || allItems.size >= retryResult.totalResults) {
                            allPagesSucceeded = true
                            break
                        }
                        page++
                        continue
                    }
                }
                break
            }

            fetchedAtLeastOnePage = true
            allItems.addAll(result.items.map { item ->
                if (item.appId == 0) item.copy(appId = appId) else item
            })

            if (result.items.isEmpty() || allItems.size >= result.totalResults) {
                allPagesSucceeded = true
                break
            }
            page++
        }

        Timber.tag(TAG).i("Workshop fetch complete for appId=$appId: ${allItems.size} items, succeeded=$fetchedAtLeastOnePage")
        return WorkshopFetchResult(
            items = allItems.sortedBy { it.title.lowercase() },
            succeeded = fetchedAtLeastOnePage,
            isComplete = allPagesSucceeded,
        )
    }

    fun parseEnabledIds(idsString: String?): Set<Long> =
        (idsString ?: "").split(",").mapNotNull { it.trim().toLongOrNull() }.filter { it > 0L }.toSet()

    fun getWorkshopContentDir(containerRootPath: String, appId: Int): File {
        // Stage workshop outside container for persistence across container rebuilds
        val ctx = SteamService.instance?.applicationContext
        if (ctx != null) {
            return File(ctx.filesDir, "workshop/$appId")
        }
        return File(
            containerRootPath,
            ".wine/drive_c/Program Files (x86)/Steam/steamapps/workshop/content/$appId",
        )
    }

    fun cleanupUnsubscribedItems(
        subscribedItems: List<WorkshopItem>,
        workshopContentDir: File,
    ): Int {
        if (!workshopContentDir.exists()) return 0
        val subscribedIds = subscribedItems.map { it.publishedFileId }.toSet()
        val entries = workshopContentDir.listFiles() ?: return 0

        var removedCount = 0
        entries.forEach { entry ->
            // Убираем и orphan .partial-каталоги без числового соседа
            val baseName = entry.name.removeSuffix(".partial")
            val id = baseName.toLongOrNull() ?: return@forEach
            if (id in subscribedIds) return@forEach
            if (entry.isDirectory || entry.name.endsWith(".partial")) {
                if (entry.deleteRecursively()) {
                    removedCount++
                    Timber.tag(TAG).d("Removed unsubscribed workshop item: ${entry.name}")
                }
            }
        }
        if (removedCount > 0) {
            Timber.tag(TAG).i("Cleaned up $removedCount unsubscribed workshop items")
        }
        return removedCount
    }

    fun getItemsNeedingSync(
        items: List<WorkshopItem>,
        workshopContentDir: File,
    ): List<WorkshopItem> {
        return items.filter { item ->
            if (item.fileUrl.isBlank()) {
                return@filter false
            }

            val itemDir = File(workshopContentDir, item.publishedFileId.toString())
            val partialDir = File(workshopContentDir, "${item.publishedFileId}.partial")
            val markerFile = File(itemDir, COMPLETE_MARKER)

            // DepotDownloader оставляет .partial после завершения — чистим stale,
            // чтобы не было ложного re-download
            if (markerFile.exists() && partialDir.exists()) {
                partialDir.deleteRecursively()
                Timber.tag(TAG).d("Cleaned leftover .partial for ${item.publishedFileId} '${item.title}'")
            }

            if (!markerFile.exists() || partialDir.exists()) {
                return@filter true
            }
            val contentFiles = itemDir.listFiles()?.filter { !it.name.startsWith(".") }.orEmpty()
            if (contentFiles.isEmpty()) {
                return@filter true
            }

            val savedTimestamp = runCatching { markerFile.readText().trim().toLongOrNull() }.getOrNull()
            savedTimestamp == null || item.timeUpdated > savedTimestamp
        }
    }

    suspend fun syncEnabledItems(
        context: Context,
        appId: Int,
        containerId: Int?,
        enabledIds: Set<Long>,
        onStatus: (String) -> Unit = {},
    ): WorkshopSyncResult = withContext(Dispatchers.IO) {
        val containerManager = ContainerManager(context)
        val container = containerId?.let(containerManager::getContainerById)
            ?: PrefManager.preferredSteamContainerId.takeIf { it != 0 }?.let(containerManager::getContainerById)
            ?: containerManager.containers.firstOrNull()
            ?: return@withContext WorkshopSyncResult(failedCount = 1)

        val workshopContentDir = getWorkshopContentDir(container.rootDir.absolutePath, appId)
        workshopContentDir.mkdirs()

        if (enabledIds.isEmpty()) {
            val removed = cleanupAllWorkshopItems(workshopContentDir)
            return@withContext WorkshopSyncResult(removedCount = removed)
        }

        val steamClient = SteamService.instance?.steamClient ?: return@withContext WorkshopSyncResult(failedCount = 1)
        val steamId = SteamService.userSteamId ?: return@withContext WorkshopSyncResult(failedCount = 1)
        val fetchResult = getSubscribedItems(appId, steamClient, steamId)
        if (!fetchResult.succeeded) {
            return@withContext WorkshopSyncResult(failedCount = 1)
        }

        val enabledItems = fetchResult.items.filter { it.publishedFileId in enabledIds }
        val removed = if (fetchResult.isComplete) cleanupUnsubscribedItems(enabledItems, workshopContentDir) else 0
        val itemsToSync = getItemsNeedingSync(enabledItems, workshopContentDir)

        // Проверка места перед sync (x2 с запасом на распаковку)
        val totalSyncBytes = itemsToSync.sumOf { it.fileSizeBytes }
        checkDiskSpace(workshopContentDir, totalSyncBytes * 2)?.let { spaceError ->
            Timber.tag(TAG).e("$spaceError for appId=$appId")
            onStatus(spaceError)
            return@withContext WorkshopSyncResult(failedCount = itemsToSync.size, removedCount = removed)
        }

        if (itemsToSync.isEmpty()) {
            updateMarkerTimestamps(enabledItems, workshopContentDir)
            return@withContext WorkshopSyncResult(
                skippedCount = enabledItems.size,
                removedCount = removed,
            )
        }

        var syncedCount = 0
        var unsupportedCount = 0
        var failedCount = 0

        itemsToSync.forEachIndexed { index, item ->
            try {
                ensureActiveStatus(index, itemsToSync.size, item.title, onStatus)
                if (item.fileUrl.isNotBlank()) {
                    // Download via HTTP (most common)
                    downloadWorkshopItem(item, workshopContentDir)
                    // Download preview image
                    downloadWorkshopPreview(item, workshopContentDir)
                    writeItemMeta(item, workshopContentDir)
                    syncedCount++
                } else if (item.manifestId != 0L) {
                    // Try depot-based download via SteamService
                    val depotDownloaded = downloadWorkshopItemDepot(
                        context = context,
                        appId = appId,
                        item = item,
                        workshopContentDir = workshopContentDir,
                    )
                    if (depotDownloaded) {
                        writeItemMeta(item, workshopContentDir)
                        syncedCount++
                    } else {
                        unsupportedCount++
                    }
                } else {
                    unsupportedCount++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedCount++
                Timber.tag(TAG).w(e, "Failed to sync workshop item ${item.publishedFileId} for appId=$appId")
            }
        }

        updateMarkerTimestamps(enabledItems, workshopContentDir)
        cacheWorkshopMetadata(appId, enabledItems, workshopContentDir)

        WorkshopSyncResult(
            syncedCount = syncedCount,
            skippedCount = enabledItems.size - itemsToSync.size,
            removedCount = removed,
            unsupportedCount = unsupportedCount,
            failedCount = failedCount,
        )
    }

    private data class SubscribedFilesPage(
        val items: List<WorkshopItem>,
        val totalResults: Int,
    )

    /**
     * Try to download a workshop item via Steam depot system using its manifestId.
     * This is a fallback for items without a direct HTTP fileUrl.
     * Uses SteamService's depot download infrastructure.
     */
    private suspend fun downloadWorkshopItemDepot(
        context: Context,
        appId: Int,
        item: WorkshopItem,
        workshopContentDir: File,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // First try to resolve the file URL via Steam PublishedFile API
            val resolvedUrl = resolveFileUrl(appId, item.publishedFileId)
            if (!resolvedUrl.isNullOrBlank()) {
                // Download via HTTP with the resolved URL
                val resolvedItem = item.copy(fileUrl = resolvedUrl)
                downloadWorkshopItem(resolvedItem, workshopContentDir)
                return@withContext true
            }

            // If no URL available, try to use manifestId with Steam CDN
            // Steam workshop manifestId (hcontent_file) can be used to construct a CDN URL
            if (item.manifestId != 0L) {
                val cdnUrl = buildSteamCdnUrl(item.manifestId, appId)
                val cdnItem = item.copy(fileUrl = cdnUrl)
                downloadWorkshopItem(cdnItem, workshopContentDir)
                return@withContext true
            }

            Timber.tag(TAG).w("No download method available for workshop item ${item.publishedFileId}")
            false
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Depot download failed for workshop item ${item.publishedFileId}")
            false
        }
    }

    /**
     * Resolve a workshop item's file download URL via Steam PublishedFile.GetDetails API.
     */
    private suspend fun resolveFileUrl(appId: Int, publishedFileId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val steamClient = SteamService.instance?.steamClient ?: return@withContext null
            val unifiedMessages = steamClient.getHandler<SteamUnifiedMessages>() ?: return@withContext null
            val publishedFile = unifiedMessages.createService(PublishedFile::class.java)

            val request = CPublishedFile_GetDetails_Request.newBuilder()
                .addPublishedfileids(publishedFileId)
                .setIncludetags(false)
                .build()

            val response = withTimeoutOrNull(15_000L) {
                publishedFile.getDetails(request).toFuture().await()
            } ?: return@withContext null

            if (response.result != EResult.OK) return@withContext null

            val details = response.body.build().publishedfiledetailsList
                .firstOrNull { it.publishedfileid == publishedFileId }
                ?: return@withContext null

            val fileUrl = details.fileUrl ?: ""
            fileUrl.ifBlank { null }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to resolve file URL for workshop item $publishedFileId")
            null
        }
    }

    /**
     * Build a Steam CDN URL for a workshop item using its manifest ID.
     * This is the URL pattern used by the Steam depot system for individual files.
     */
    private fun buildSteamCdnUrl(manifestId: Long, appId: Int): String {
        return "https://steamcdn-a.akamaihd.net/steamcommunity/public/images/items/$appId/$manifestId"
    }

    private suspend fun fetchSubscribedFilesViaRPC(
        publishedFile: PublishedFile,
        appId: Int,
        steamId: SteamID,
        page: Int,
    ): SubscribedFilesPage? = withContext(Dispatchers.IO) {
        try {
            val request = CPublishedFile_GetUserFiles_Request.newBuilder().apply {
                steamid = steamId.convertToUInt64()
                appid = appId
                this.page = page
                numperpage = PAGE_SIZE
                type = "mysubscriptions"
                filetype = 0xFFFFFFFF.toInt()
            }.build()

            val response = withTimeoutOrNull(60_000L) {
                publishedFile.getUserFiles(request).toFuture().await()
            }

            if (response == null) {
                Timber.tag(TAG).w("Workshop page $page timed out (60s) for appId=$appId")
                return@withContext null
            }

            Timber.tag(TAG).i("Workshop page $page result=${response.result} for appId=$appId")
            if (response.result != EResult.OK) {
                Timber.tag(TAG).w("Workshop page $page failed with EResult=${response.result} for appId=$appId")
                return@withContext null
            }

            val body = response.body.build()
            val items = body.publishedfiledetailsList.map { details ->
                WorkshopItem(
                    publishedFileId = details.publishedfileid,
                    appId = if (details.consumerAppid != 0) details.consumerAppid else appId,
                    title = details.title.ifEmpty { details.publishedfileid.toString() },
                    fileSizeBytes = details.fileSize,
                    manifestId = details.hcontentFile,
                    timeUpdated = details.timeUpdated.toLong(),
                    fileUrl = details.fileUrl ?: "",
                    fileName = details.filename ?: "",
                    previewUrl = details.previewUrl ?: "",
                )
            }

            Timber.tag(TAG).i("Workshop page $page: ${items.size} items of ${body.total} total for appId=$appId")
            SubscribedFilesPage(
                items = items,
                totalResults = body.total,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to fetch workshop subscriptions for appId=$appId page=$page")
            null
        }
    }

    private fun updateMarkerTimestamps(
        items: List<WorkshopItem>,
        workshopContentDir: File,
    ) {
        items.forEach { item ->
            val itemDir = File(workshopContentDir, item.publishedFileId.toString())
            if (!itemDir.isDirectory) return@forEach
            val markerFile = File(itemDir, COMPLETE_MARKER)
            markerFile.writeText(item.timeUpdated.toString())
        }
    }

    private fun cleanupAllWorkshopItems(workshopContentDir: File): Int {
        if (!workshopContentDir.exists()) return 0
        var removed = 0
        workshopContentDir.listFiles()?.forEach { entry ->
            if (entry.deleteRecursively()) {
                removed++
            }
        }
        return removed
    }

    /**
     * Проверка свободного места. Возвращает текст ошибки или null если места хватает.
     * Эталон GameNative WorkshopManager.checkDiskSpace.
     */
    fun checkDiskSpace(dir: File, requiredBytes: Long): String? {
        val spaceDir = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }
        val availableBytes = spaceDir?.usableSpace ?: -1L
        if (requiredBytes > 0 && availableBytes >= 0 && requiredBytes > availableBytes) {
            val reqMB = String.format(java.util.Locale.US, "%.0f", requiredBytes / 1_048_576.0)
            val avlMB = String.format(java.util.Locale.US, "%.0f", availableBytes / 1_048_576.0)
            return "Not enough space (need $reqMB MB, have $avlMB MB)"
        }
        return null
    }

    /** Per-item meta .workshop_meta.json рядом с контентом (title/size/time/preview). */
    private fun writeItemMeta(item: WorkshopItem, workshopContentDir: File) {
        val itemDir = File(workshopContentDir, item.publishedFileId.toString())
        if (!itemDir.isDirectory) return
        runCatching {
            val meta = JSONObject().apply {
                put("id", item.publishedFileId)
                put("app_id", item.appId)
                put("title", item.title)
                put("file_size", item.fileSizeBytes)
                put("time_updated", item.timeUpdated)
                if (item.fileName.isNotBlank()) put("file_name", item.fileName)
                if (item.previewUrl.isNotBlank()) put("preview_url", item.previewUrl)
                if (File(itemDir, "preview.jpg").exists()) put("preview_file", "preview.jpg")
            }
            File(itemDir, ".workshop_meta.json").writeText(meta.toString(2))
        }
    }

    private fun findPreviewImage(itemDir: File): File? =
        itemDir.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith("preview.", ignoreCase = true) &&
                it.extension.lowercase() in setOf("jpg", "jpeg", "png", "gif")
        }

    private fun isWorkshopPayloadFile(file: File): Boolean =
        file.isFile && !file.name.startsWith(".") &&
            !file.name.startsWith("preview.", ignoreCase = true)

    private fun workshopPayloadFiles(itemDir: File): Sequence<File> =
        itemDir.walkTopDown().filter { isWorkshopPayloadFile(it) }

    private fun workshopPayloadSize(itemDir: File): Long =
        if (itemDir.isDirectory) workshopPayloadFiles(itemDir).sumOf { it.length() } else 0L

    /** Результат best-effort проверки обновлений перед запуском (эталон PluviaMain:2328). */
    data class WorkshopUpdateCheck(
        val itemsToSync: List<WorkshopItem>,
        val allItems: List<WorkshopItem>,
        val workshopContentDir: File,
        val totalUpdateBytes: Long,
    )

    /**
     * Best-effort проверка обновлений воркшопа. Возвращает null когда
     * обновлений нет (маркеры при этом освежаются) или проверка невозможна.
     * Никогда не бросает наружу — запуск игры не блочится.
     */
    suspend fun checkForWorkshopUpdates(
        appId: Int,
        enabledIds: Set<Long>,
        containerRootPath: String,
    ): WorkshopUpdateCheck? = withContext(Dispatchers.IO) {
        if (enabledIds.isEmpty()) return@withContext null
        val steamClient = SteamService.instance?.steamClient ?: return@withContext null
        val steamId = SteamService.userSteamId ?: return@withContext null

        val fetchResult = runCatching { getSubscribedItems(appId, steamClient, steamId) }.getOrNull()
            ?: return@withContext null
        if (!fetchResult.succeeded || !fetchResult.isComplete) {
            Timber.tag(TAG).w("Workshop fetch incomplete/failed for appId=$appId; skipping update check")
            return@withContext null
        }

        val items = fetchResult.items.filter { it.publishedFileId in enabledIds }
        val workshopContentDir = getWorkshopContentDir(containerRootPath, appId)
        if (items.isEmpty()) return@withContext null

        cleanupUnsubscribedItems(items, workshopContentDir)
        val itemsToSync = getItemsNeedingSync(items, workshopContentDir)
        if (itemsToSync.isEmpty()) {
            updateMarkerTimestamps(items, workshopContentDir)
            return@withContext null
        }
        WorkshopUpdateCheck(
            itemsToSync = itemsToSync,
            allItems = items,
            workshopContentDir = workshopContentDir,
            totalUpdateBytes = itemsToSync.sumOf { it.fileSizeBytes },
        )
    }

    private fun ensureActiveStatus(
        index: Int,
        total: Int,
        title: String,
        onStatus: (String) -> Unit,
    ) {
        onStatus("$title (${index + 1}/$total)")
    }

    private fun downloadWorkshopPreview(item: WorkshopItem, workshopContentDir: File) {
        if (item.previewUrl.isBlank()) return
        val itemDir = File(workshopContentDir, item.publishedFileId.toString())
        val previewFile = File(itemDir, "preview.jpg")
        if (previewFile.exists()) {
            // Re-download if item was updated
            if (item.timeUpdated <= previewFile.lastModified() / 1000) return
            previewFile.delete()
        }
        try {
            val request = Request.Builder().url(item.previewUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        previewFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to download preview for workshop item ${item.publishedFileId}")
        }
    }

    private fun downloadWorkshopItem(item: WorkshopItem, workshopContentDir: File) {
        val itemDir = File(workshopContentDir, item.publishedFileId.toString())
        val partialDir = File(workshopContentDir, "${item.publishedFileId}.partial")
        itemDir.mkdirs()
        partialDir.deleteRecursively()

        val fileName = resolveOutputFileName(item)
        val destination = File(itemDir, fileName)
        val tempFile = File(itemDir, "$fileName.part")
        val request = Request.Builder().url(item.fileUrl).build()

        httpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body ?: error("Empty body")
            tempFile.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }
        }

        if (!tempFile.renameTo(destination)) {
            tempFile.copyTo(destination, overwrite = true)
            tempFile.delete()
        }

        itemDir.listFiles()
            ?.filter { it.isFile && !it.name.startsWith(".") && it.name != destination.name }
            ?.forEach { it.delete() }
    }

    private fun resolveOutputFileName(item: WorkshopItem): String {
        return item.publishedFileId.toString()
    }

    fun generateWorkshopModsJson(appId: Int, gameInstallPath: String, containerRootPath: String) {
        val steamSettingsDir = File(gameInstallPath, "steam_settings")
        if (!steamSettingsDir.exists()) steamSettingsDir.mkdirs()

        val modsDir = File(steamSettingsDir, "mods")
        if (modsDir.exists()) modsDir.deleteRecursively()
        modsDir.mkdirs()

        val enabledIds = PrefManager.getSteamWorkshopEnabledItemIds(appId)
        val workshopContentDir = getWorkshopContentDir(containerRootPath, appId)

        // Load cached metadata
        val metaFile = File(workshopContentDir, META_FILE)
        val cachedMeta = if (metaFile.exists()) {
            try {
                JSONObject(metaFile.readText())
            } catch (e: Exception) {
                JSONObject()
            }
        } else {
            JSONObject()
        }

        val jsonArray = JSONArray()

        for (id in enabledIds) {
            val idStr = id.toString()
            val sourceDir = File(workshopContentDir, idStr)
            if (sourceDir.exists() && sourceDir.isDirectory) {
                val targetDir = File(modsDir, idStr)

                try {
                    Os.symlink(sourceDir.absolutePath, targetDir.absolutePath)
                } catch (e: Exception) {
                    sourceDir.copyRecursively(targetDir, overwrite = true)
                }

                // Кладём preview в steam_settings/mod_images/<id>/ для gbe_fork
                findPreviewImage(sourceDir)?.let { preview ->
                    runCatching {
                        val imagesDir = File(steamSettingsDir, "mod_images/$idStr")
                        imagesDir.mkdirs()
                        val dest = File(imagesDir, preview.name)
                        if (!dest.isFile || dest.length() != preview.length()) {
                            preview.copyTo(dest, overwrite = true)
                        }
                    }
                }

                // Build rich metadata (эталон GameNative buildModsJson)
                val payloads = workshopPayloadFiles(sourceDir).toList()
                val primary = payloads.minByOrNull { it.name }
                val meta = cachedMeta.optJSONObject(idStr)
                val modObj = JSONObject().apply {
                    put("id", idStr)
                    put("title", meta?.optString("title")?.takeIf { it.isNotBlank() } ?: "Workshop Item $idStr")
                    if (primary != null) {
                        put("primary_filename", primary.name)
                        put("primary_filesize", primary.length())
                    }
                    put("total_files_sizes", workshopPayloadSize(sourceDir))
                    val timeUpdated = meta?.optLong("time_updated", 0L) ?: 0L
                    if (timeUpdated > 0) put("time_updated", timeUpdated)
                    findPreviewImage(sourceDir)?.let { put("preview_filename", it.name) }
                    if (meta != null) {
                        if (meta.has("description")) put("description", meta.getString("description"))
                        if (meta.has("preview_url")) put("preview_url", meta.getString("preview_url"))
                        if (meta.has("preview_file")) put("preview_file", meta.getString("preview_file"))
                        if (meta.has("file_size")) put("file_size", meta.getLong("file_size"))
                        if (meta.has("author")) put("author", meta.getString("author"))
                    }
                }
                jsonArray.put(modObj)
            }
        }

        val modsJsonFile = File(steamSettingsDir, "mods.json")
        if (jsonArray.length() > 0) {
            modsJsonFile.writeText(jsonArray.toString(2))
        } else {
            if (modsJsonFile.exists()) modsJsonFile.delete()
        }

        // Fan-out в папку игры по детекту (SymlinkIntoDir/CopyIntoDir), best-effort
        runCatching {
            val gameName = runCatching {
                com.winlator.cmod.steam.service.SteamService.getAppInfoOf(appId)?.name
            }.getOrNull().orEmpty()
            val detection = WorkshopModPathDetector().detect(
                gameInstallDir = File(gameInstallPath),
                winePrefix = File(containerRootPath),
                gameName = gameName,
            )
            applyModPathStrategy(detection.strategy, workshopContentDir, enabledIds)
        }
    }

    /**
     * Раскладка модов в папку игры по стратегии детекта.
     * Трогаем только наши симлинки (в workshop/content) и каталоги с сентинелом.
     */
    private fun applyModPathStrategy(
        strategy: WorkshopModPathStrategy,
        workshopContentDir: File,
        enabledIds: Set<Long>,
    ) {
        val (targetDirs, useCopy) = when (strategy) {
            is WorkshopModPathStrategy.Standard -> return
            is WorkshopModPathStrategy.SymlinkIntoDir -> strategy.effectiveDirs to false
            is WorkshopModPathStrategy.CopyIntoDir -> strategy.effectiveDirs to true
        }
        val activeDirs = enabledIds.mapNotNull { id ->
            File(workshopContentDir, id.toString())
                .takeIf { it.isDirectory }
                ?.let { id.toString() to it }
        }.toMap()
        if (activeDirs.isEmpty()) return

        targetDirs.forEach { targetDir ->
            runCatching { targetDir.mkdirs() }
            if (!targetDir.isDirectory) return@forEach
            // Чистим stale наши записи
            targetDir.listFiles()?.forEach { entry ->
                val isOurs = when {
                    java.nio.file.Files.isSymbolicLink(entry.toPath()) -> isWorkshopContentSymlink(entry, workshopContentDir)
                    entry.isDirectory -> File(entry, COPY_SENTINEL).isFile
                    else -> false
                }
                if (isOurs && entry.name !in activeDirs) {
                    runCatching { entry.deleteRecursively() }
                }
            }
            // Кладём активные (fan-out по effectiveDirs)
            activeDirs.forEach { (idStr, srcDir) ->
                val link = File(targetDir, idStr)
                if (java.nio.file.Files.isSymbolicLink(link.toPath())) {
                    if (isWorkshopContentSymlink(link, workshopContentDir)) return@forEach
                    runCatching { java.nio.file.Files.deleteIfExists(link.toPath()) }
                } else if (link.exists()) {
                    if (link.isDirectory && File(link, COPY_SENTINEL).isFile) {
                        link.deleteRecursively()
                    } else {
                        return@forEach // чужое — не трогаем
                    }
                }
                if (useCopy) {
                    runCatching {
                        srcDir.copyRecursively(link, overwrite = true)
                        File(link, COPY_SENTINEL).writeText(srcDir.absolutePath)
                    }
                } else {
                    runCatching { Os.symlink(srcDir.absolutePath, link.absolutePath) }
                        .onFailure {
                            runCatching {
                                srcDir.copyRecursively(link, overwrite = true)
                                File(link, COPY_SENTINEL).writeText(srcDir.absolutePath)
                            }
                        }
                }
            }
        }
        Timber.tag(TAG).i("Applied workshop strategy ${strategy::class.simpleName} to ${targetDirs.size} dir(s)")
    }

    private fun isWorkshopContentSymlink(entry: File, workshopContentDir: File): Boolean {
        if (!java.nio.file.Files.isSymbolicLink(entry.toPath())) return false
        return runCatching {
            val raw = java.nio.file.Files.readSymbolicLink(entry.toPath())
            val resolved = if (raw.isAbsolute) raw else entry.toPath().parent.resolve(raw)
            val text = runCatching { resolved.toRealPath().toString() }
                .getOrElse { resolved.normalize().toAbsolutePath().toString() }
            text.contains("workshop/content/") || text.startsWith(workshopContentDir.absolutePath)
        }.getOrDefault(false)
    }

    /**
     * Cache workshop item metadata for later use in mods.json generation.
     */
    fun cacheWorkshopMetadata(appId: Int, items: List<WorkshopItem>, workshopContentDir: File) {
        val metaFile = File(workshopContentDir, META_FILE)
        val meta = JSONObject()
        for (item in items) {
            val idStr = item.publishedFileId.toString()
            val obj = JSONObject().apply {
                put("title", item.title.ifEmpty { idStr })
                put("file_size", item.fileSizeBytes)
                put("time_updated", item.timeUpdated)
                put("preview_url", item.previewUrl)
                if (item.previewUrl.isNotBlank()) put("preview_file", "preview.jpg")
                if (item.fileName.isNotBlank()) put("file_name", item.fileName)
            }
            meta.put(idStr, obj)
        }
        workshopContentDir.mkdirs()
        metaFile.writeText(meta.toString(2))
        Timber.tag(TAG).i("Cached metadata for ${items.size} workshop items (appId=$appId)")
    }
}
