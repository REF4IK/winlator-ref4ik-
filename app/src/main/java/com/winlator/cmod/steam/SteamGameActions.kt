package com.winlator.cmod.steam

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.net.Uri
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.steam.enums.PathType
import com.winlator.cmod.steam.enums.SaveLocation
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.PrefManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

object SteamGameActions {
    private const val STEAM_LOADER_WIN_PATH = "C:/Program Files (x86)/Steam/steamclient_loader_x64.exe"
    private const val STEAM_LOADER_EXEC = "wine $STEAM_LOADER_WIN_PATH"

    fun findSteamShortcut(context: Context, appId: Int): Shortcut? {
        val appIdString = appId.toString()
        return ContainerManager(context).loadShortcuts().firstOrNull { shortcut ->
            (shortcut.getExtra("game_source") == "STEAM" && shortcut.getExtra("app_id") == appIdString) ||
                (shortcut.getExtra("gameSource") == "STEAM" && shortcut.getExtra("steamAppId") == appIdString)
        }
    }

    fun ensureSteamShortcut(context: Context, appId: Int, preferredContainerId: Int? = null): Shortcut? {
        findSteamShortcut(context, appId)?.let { existing ->
            syncSteamShortcutMetadata(existing, appId)
            return existing
        }

        val containerManager = ContainerManager(context)
        val container = preferredContainerId?.let(containerManager::getContainerById)
            ?: containerManager.containers.firstOrNull()
            ?: return null
        val appInfo = SteamService.getAppInfoOf(appId) ?: return null
        val installPath = SteamService.getAppDirPath(appId)
        val launchExecutable = SteamService.getInstalledExe(appId)
        val desktopDir = container.desktopDir
        if (!desktopDir.exists()) {
            desktopDir.mkdirs()
        }

        // Дубль на диске, но мимо индекса (переименование/битый индекс) — переиспользовать, не плодить
        findSteamShortcutFile(desktopDir, appId)?.let { orphan ->
            Timber.i("Reusing orphan Steam shortcut ${orphan.name} for appId=$appId")
            return Shortcut(container, orphan).also { shortcut ->
                syncSteamShortcutMetadata(shortcut, appId, installPath, launchExecutable)
            }
        }

        val safeName = appInfo.name.replace("/", "_").replace("\\", "_")
        val primaryFile = File(desktopDir, "$safeName.desktop")
        val shortcutFile = when {
            !primaryFile.exists() -> primaryFile
            isSameSteamApp(primaryFile, appId) -> primaryFile
            else -> File(desktopDir, "${safeName}_$appId.desktop").also {
                if (it.exists()) Timber.w("Shortcut name collision for appId=$appId, reusing ${it.name}")
            }
        }
        writeShortcutFile(shortcutFile, container, appInfo.name, appId, installPath, launchExecutable)
        return Shortcut(container, shortcutFile).also { shortcut ->
            syncSteamShortcutMetadata(shortcut, appId, installPath, launchExecutable)
        }
    }

    fun openShortcutSettings(context: Context, appId: Int, preferredContainerId: Int? = null): Boolean {
        val shortcut = ensureSteamShortcut(context, appId, preferredContainerId) ?: return false
        context.startActivity(
            Intent(context, SteamShortcutSettingsActivity::class.java).apply {
                putExtra(SteamShortcutSettingsActivity.EXTRA_APP_ID, appId)
                putExtra(
                    SteamShortcutSettingsActivity.EXTRA_CONTAINER_ID,
                    preferredContainerId ?: shortcut.container.id,
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return true
    }

    suspend fun addShortcutToHomeScreen(
        context: Context,
        appId: Int,
        preferredContainerId: Int? = null,
    ): Boolean = withContext(Dispatchers.Main) {
        val shortcut = ensureSteamShortcut(context, appId, preferredContainerId) ?: return@withContext false
        requestPinnedHomeShortcut(context, shortcut)
    }

    const val SAVE_ARCHIVE_MANIFEST = "manifest.json"
    const val SAVE_ARCHIVE_FILES_PREFIX = "files/"

    suspend fun exportSaves(context: Context, appId: Int, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val roots = resolveExportRoots(context, appId)
        if (roots.isEmpty()) {
            return@withContext false
        }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutputStream ->
                zipOutputStream.putNextEntry(ZipEntry(SAVE_ARCHIVE_MANIFEST))
                zipOutputStream.write(buildSaveManifest(appId, roots).toByteArray(Charsets.UTF_8))
                zipOutputStream.closeEntry()
                roots.forEach { (rootId, dir) ->
                    zipOutputStream.putNextEntry(ZipEntry("$SAVE_ARCHIVE_FILES_PREFIX$rootId/"))
                    zipOutputStream.closeEntry()
                    zipDirectory(zipOutputStream, dir, "$SAVE_ARCHIVE_FILES_PREFIX$rootId")
                }
            }
            true
        } ?: false
    }

    suspend fun importSaves(context: Context, appId: Int, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val manifest = readSaveManifest(context, uri)
        if (manifest != null && manifest.optInt("appId", appId) != appId) {
            return@withContext false
        }
        val rootMap = resolveImportRoots(context, appId, manifest)
        val goldbergSettingsDir = File(SteamService.getAppDirPath(appId), "steam_settings")
        val shortcut = findSteamShortcut(context, appId)
        val prefixDir = shortcut?.let { File(it.container.rootDir, ".wine/drive_c/users/xuser") }
        var importedAnything = false

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    val destination = resolveImportDestination(
                        entryName = name,
                        rootMap = rootMap,
                        goldbergSettingsDir = goldbergSettingsDir,
                        prefixDir = prefixDir,
                    )

                    if (destination != null) {
                        if (entry.isDirectory) {
                            destination.mkdirs()
                        } else {
                            destination.parentFile?.mkdirs()
                            FileOutputStream(destination).use { output ->
                                zipInputStream.copyTo(output)
                            }
                        }
                        importedAnything = true
                    }

                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
        } ?: return@withContext false

        importedAnything
    }

    suspend fun syncCloudSaves(context: Context, appId: Int, preferredSave: SaveLocation): Boolean =
        withContext(Dispatchers.IO) {
            activateContainerForSteamApp(context, appId)
            val accountId = SteamService.userSteamId?.accountID?.toLong()
                ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                ?: 0L
            val prefixToPath: (String) -> String = { prefix ->
                PathType.from(prefix).toAbsPath(context, appId, accountId)
            }
            val info = SteamService.forceSyncUserFiles(
                appId = appId,
                prefixToPath = prefixToPath,
                preferredSave = preferredSave,
            ).await()
            val success = info.syncResult == SyncResult.Success || info.syncResult == SyncResult.UpToDate
            if (success) {
                SteamService.clearPendingCloudSync(context, appId)
            }
            success
        }

    suspend fun forceCloudSync(context: Context, appId: Int): SyncResult = withContext(Dispatchers.IO) {
        activateContainerForSteamApp(context, appId)
        val accountId = SteamService.userSteamId?.accountID?.toLong()
            ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
            ?: 0L
        val prefixToPath: (String) -> String = { prefix ->
            PathType.from(prefix).toAbsPath(context, appId, accountId)
        }
        SteamService.forceSyncUserFiles(
            appId = appId,
            prefixToPath = prefixToPath,
            preferredSave = SaveLocation.None,
        ).await().syncResult
    }

    private fun activateContainerForSteamApp(context: Context, appId: Int) {
        val containerManager = ContainerManager(context)
        val shortcutContainerId = findSteamShortcut(context, appId)?.container?.id
        val targetContainer = shortcutContainerId?.let(containerManager::getContainerById)
            ?: PrefManager.preferredSteamContainerId.takeIf { it != 0 }?.let(containerManager::getContainerById)
            ?: containerManager.containers.firstOrNull()
            ?: return
        containerManager.activateContainer(targetContainer)
    }

    private fun syncSteamShortcutMetadata(
        shortcut: Shortcut,
        appId: Int,
        installPath: String = SteamService.getAppDirPath(appId),
        launchExecutable: String = SteamService.getInstalledExe(appId),
    ) {
        shortcut.putExtra("game_source", "STEAM")
        shortcut.putExtra("app_id", appId.toString())
        shortcut.putExtra("container_id", shortcut.container.id.toString())
        shortcut.putExtra("game_install_path", installPath)
        shortcut.putExtra("launch_exe_path", launchExecutable)
        // execArgs не трогаем — там пользовательские аргументы запуска (preserve per-game settings)
        rewriteExecLine(shortcut.file, STEAM_LOADER_EXEC)
        shortcut.saveData()
    }

    private fun isSameSteamApp(file: File, appId: Int): Boolean {
        val wanted = appId.toString()
        var source: String? = null
        var id: String? = null
        var inExtra = false
        for (raw in FileUtils.readLines(file)) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inExtra = line.equals("[Extra Data]", ignoreCase = true)
                continue
            }
            if (!inExtra || line.isEmpty() || line.startsWith("#")) continue
            val eq = line.indexOf('=')
            if (eq < 0) continue
            when (line.substring(0, eq).trim()) {
                "game_source", "gameSource" -> source = line.substring(eq + 1).trim()
                "app_id", "steamAppId" -> id = line.substring(eq + 1).trim()
            }
            if (source == "STEAM" && id == wanted) return true
        }
        return false
    }

    private fun findSteamShortcutFile(desktopDir: File, appId: Int): File? {
        val files = desktopDir.listFiles { f -> f.isFile && f.name.endsWith(".desktop", ignoreCase = true) }
            ?: return null
        return files.firstOrNull { isSameSteamApp(it, appId) }
    }

    private fun writeShortcutFile(
        shortcutFile: File,
        container: Container,
        gameName: String,
        appId: Int,
        installPath: String,
        launchExecutable: String,
    ) {
        shortcutFile.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }

        val content = buildString {
            append("[Desktop Entry]\n")
            append("Type=Application\n")
            append("Name=").append(gameName).append("\n")
            append("Exec=").append(STEAM_LOADER_EXEC).append("\n")
            append("Icon=steam_icon_").append(appId).append("\n")
            append("\n[Extra Data]\n")
            append("game_source=STEAM\n")
            append("app_id=").append(appId).append("\n")
            append("container_id=").append(container.id).append("\n")
            append("game_install_path=").append(installPath).append("\n")
            append("launch_exe_path=").append(launchExecutable).append("\n")
            append("use_container_defaults=1\n")
        }
        FileUtils.writeString(shortcutFile, content)
    }

    private fun rewriteExecLine(shortcutFile: File, execValue: String) {
        val lines = FileUtils.readLines(shortcutFile)
        val rewritten = StringBuilder()
        var execUpdated = false
        for (line in lines) {
            if (line.startsWith("Exec=")) {
                rewritten.append("Exec=").append(execValue).append("\n")
                execUpdated = true
            } else {
                rewritten.append(line).append("\n")
            }
        }
        if (!execUpdated) {
            rewritten.append("Exec=").append(execValue).append("\n")
        }
        FileUtils.writeString(shortcutFile, rewritten.toString())
    }

    private fun requestPinnedHomeShortcut(context: Context, shortcut: Shortcut): Boolean {
        if (shortcut.getExtra("uuid").isEmpty()) {
            shortcut.genUUID()
        }
        val shortcutId = shortcut.getExtra("uuid")
        if (shortcutId.isEmpty()) {
            return false
        }

        val canonicalShortcutPath = shortcut.file.absolutePath
        val shortcutPathHash = canonicalShortcutPath.hashCode()
        val pinShortcutId =
            "shortcut_${shortcut.container.id}_${shortcutId}_${shortcutPathHash.toUInt().toString(16)}"

        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!shortcutManager.isRequestPinShortcutSupported) {
            return false
        }

        val launchIntent = Intent(context, XServerDisplayActivity::class.java).apply {
            val containerIdForLaunch = shortcut.getExtra("container_id").toIntOrNull() ?: shortcut.container.id
            val launchData = Uri.Builder()
                .scheme("winlator")
                .authority(BuildConfig.APPLICATION_ID)
                .appendPath("shortcut")
                .appendQueryParameter("uuid", shortcutId)
                .appendQueryParameter("container", containerIdForLaunch.toString())
                .appendQueryParameter("hash", shortcutPathHash.toString())
                .build()
            action = Intent.ACTION_VIEW
            data = launchData
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("container_id", containerIdForLaunch)
            putExtra("shortcut_path", canonicalShortcutPath)
            putExtra("shortcut_name", shortcut.name)
            putExtra("shortcut_uuid", shortcutId)
            putExtra("shortcut_path_hash", shortcutPathHash)
        }

        val artworkBitmap = shortcut.coverArt ?: shortcut.icon
        val shortcutIcon = artworkBitmap?.let { Icon.createWithBitmap(it) }
            ?: Icon.createWithResource(context, R.drawable.icon_shortcut)

        val pinShortcutInfo = ShortcutInfo.Builder(context, pinShortcutId)
            .setShortLabel(shortcut.name)
            .setLongLabel(shortcut.name)
            .setIcon(shortcutIcon)
            .setIntent(launchIntent)
            .build()

        return shortcutManager.requestPinShortcut(pinShortcutInfo, null)
    }

    private fun exportAccountId(): Long {
        return SteamService.userSteamId?.accountID?.toLong()
            ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
            ?: 0L
    }

    private fun saveRootId(rootName: String, path: String): String {
        val normalized = path.replace('\\', '/').trim('/').lowercase().ifEmpty { "root" }
        return "${rootName.lowercase()}/$normalized"
    }

    // Корни экспорта: UFS-паттерны игры + SteamUserData + steam_settings/saves.
    // Только непустые каталоги. Фолбэк на широкие xuser-папки, если UFS пуст.
    private fun resolveExportRoots(context: Context, appId: Int): LinkedHashMap<String, File> {
        val roots = LinkedHashMap<String, File>()
        val accountId = exportAccountId()
        val prefixToPath: (String) -> String = { prefix ->
            PathType.from(prefix).toAbsPath(context, appId, accountId)
        }
        val patterns = SteamService.getAppInfoOf(appId)?.ufs?.saveFilePatterns
            .orEmpty()
            .filter { it.root.isWindows }

        if (patterns.isNotEmpty()) {
            patterns.filter { it.root != PathType.SteamUserData }.forEach { pattern ->
                val dir = File(prefixToPath(pattern.root.name), pattern.substitutedPath)
                if (dir.isDirectory && dir.list()?.isNotEmpty() == true) {
                    roots.putIfAbsent(saveRootId(pattern.root.name, pattern.path), dir)
                }
            }
            val userDataDir = File(prefixToPath(PathType.SteamUserData.name))
            if (userDataDir.isDirectory && userDataDir.list()?.isNotEmpty() == true) {
                roots.putIfAbsent("steamuserdata", userDataDir)
            }
        }

        val goldbergSaves = File(SteamService.getAppDirPath(appId), "steam_settings/saves")
        if (goldbergSaves.isDirectory && goldbergSaves.list()?.isNotEmpty() == true) {
            roots.putIfAbsent("saves", goldbergSaves)
        }

        if (roots.isEmpty()) {
            val shortcut = findSteamShortcut(context, appId)
            if (shortcut != null) {
                val prefixDir = File(shortcut.container.rootDir, ".wine/drive_c/users/xuser")
                listOf("Documents", "Saved Games", "AppData").forEach { name ->
                    val dir = File(prefixDir, name)
                    if (dir.isDirectory && dir.list()?.isNotEmpty() == true) {
                        roots.putIfAbsent(name, dir)
                    }
                }
            }
        }

        return roots
    }

    private fun buildSaveManifest(appId: Int, roots: Map<String, File>): String {
        val manifest = JSONObject()
        manifest.put("appId", appId)
        manifest.put("exportedAt", System.currentTimeMillis())
        val rootsArray = JSONArray()
        roots.forEach { (id, dir) ->
            val entry = JSONObject()
            entry.put("id", id)
            entry.put("path", dir.absolutePath)
            rootsArray.put(entry)
        }
        manifest.put("roots", rootsArray)
        return manifest.toString()
    }

    // Первый проход по архиву: ищем manifest.json нового формата. null = старый формат.
    private fun readSaveManifest(context: Context, uri: Uri): JSONObject? {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        try {
                            if (!entry.isDirectory && entry.name.replace('\\', '/') == SAVE_ARCHIVE_MANIFEST) {
                                val manifest = JSONObject(zip.readBytes().decodeToString())
                                if (manifest.has("roots")) return manifest
                                return null
                            }
                        } finally {
                            zip.closeEntry()
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        return null
    }

    // Маппинг rootId манифеста на текущие каталоги (UFS может отличаться от момента экспорта).
    private fun resolveImportRoots(context: Context, appId: Int, manifest: JSONObject?): Map<String, File> {
        val accountId = exportAccountId()
        val prefixToPath: (String) -> String = { prefix ->
            PathType.from(prefix).toAbsPath(context, appId, accountId)
        }
        val known = mutableMapOf<String, File>()
        SteamService.getAppInfoOf(appId)?.ufs?.saveFilePatterns
            .orEmpty()
            .filter { it.root.isWindows && it.root != PathType.SteamUserData }
            .forEach { pattern ->
                known[saveRootId(pattern.root.name, pattern.path)] =
                    File(prefixToPath(pattern.root.name), pattern.substitutedPath)
            }
        known["steamuserdata"] = File(prefixToPath(PathType.SteamUserData.name))
        known["saves"] = File(SteamService.getAppDirPath(appId), "steam_settings")
        findSteamShortcut(context, appId)?.let { shortcut ->
            val prefixDir = File(shortcut.container.rootDir, ".wine/drive_c/users/xuser")
            listOf("Documents", "Saved Games", "AppData").forEach { name ->
                known.putIfAbsent(name, File(prefixDir, name))
            }
        }

        if (manifest == null) return known
        val resolved = mutableMapOf<String, File>()
        val rootsArray = manifest.optJSONArray("roots") ?: return known
        for (i in 0 until rootsArray.length()) {
            val entry = rootsArray.optJSONObject(i) ?: continue
            val id = entry.optString("id").takeIf { it.isNotEmpty() } ?: continue
            resolved[id] = known[id] ?: File(entry.optString("path"))
        }
        return resolved.ifEmpty { known }
    }

    private fun resolveImportDestination(
        entryName: String,
        rootMap: Map<String, File>,
        goldbergSettingsDir: File,
        prefixDir: File?,
    ): File? {
        // Новый формат: files/{rootId}/{rel}. rootId сам содержит '/',
        // поэтому ищем самый длинный известный префикс.
        if (entryName.startsWith(SAVE_ARCHIVE_FILES_PREFIX)) {
            if (entryName == SAVE_ARCHIVE_MANIFEST) return null
            val remainder = entryName.removePrefix(SAVE_ARCHIVE_FILES_PREFIX)
            if (remainder.isEmpty()) return null
            val match = rootMap.keys
                .filter { remainder == it || remainder.startsWith("$it/") }
                .maxByOrNull { it.length }
                ?: return null
            val base = rootMap[match] ?: return null
            val rel = remainder.removePrefix(match).trimStart('/')
            if (rel.isEmpty()) return base
            return containedFile(base, rel)
        }
        // Старый формат: обратная совместимость.
        return when {
            entryName.startsWith("saves/") -> containedFile(goldbergSettingsDir, entryName.removePrefix("saves/"))
            prefixDir != null && (
                entryName.startsWith("Documents/") ||
                    entryName.startsWith("Saved Games/") ||
                    entryName.startsWith("AppData/")
                ) -> {
                val slash = entryName.indexOf('/')
                containedFile(File(prefixDir, entryName.substring(0, slash)), entryName.substring(slash + 1))
            }
            else -> null
        }
    }

    // Защита от zip-slip: цель обязана лежать внутри base, абсолютные пути запрещены.
    private fun containedFile(base: File, rel: String): File? {
        val normalized = rel.replace('\\', '/').trimStart('/')
        if (normalized.isEmpty() || normalized.contains(':')) return null
        val baseCanon = runCatching { base.canonicalPath }.getOrNull() ?: return null
        val dest = File(base, normalized)
        val destCanon = runCatching { dest.canonicalPath }.getOrNull() ?: return null
        if (destCanon != baseCanon && !destCanon.startsWith("$baseCanon/")) return null
        return dest
    }

    private fun zipDirectory(zipOutputStream: ZipOutputStream, dir: File, baseName: String) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            val entryName = if (baseName.isEmpty()) child.name else "$baseName/${child.name}"
            if (child.isDirectory) {
                zipOutputStream.putNextEntry(ZipEntry("$entryName/"))
                zipOutputStream.closeEntry()
                zipDirectory(zipOutputStream, child, entryName)
            } else {
                zipOutputStream.putNextEntry(ZipEntry(entryName))
                child.inputStream().use { input ->
                    input.copyTo(zipOutputStream)
                }
                zipOutputStream.closeEntry()
            }
        }
    }
}
