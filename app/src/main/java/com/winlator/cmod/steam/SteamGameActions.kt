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

        val safeName = appInfo.name.replace("/", "_").replace("\\", "_")
        val shortcutFile = File(desktopDir, "$safeName.desktop")
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

    suspend fun exportSaves(context: Context, appId: Int, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val shortcut = findSteamShortcut(context, appId)
        val directories = mutableListOf<File>()
        val goldbergSaves = File(SteamService.getAppDirPath(appId), "steam_settings/saves")
        if (goldbergSaves.exists() && goldbergSaves.isDirectory) {
            directories += goldbergSaves
        }
        if (shortcut != null) {
            val prefixDir = File(shortcut.container.rootDir, ".wine/drive_c/users/xuser")
            listOf("Documents", "Saved Games", "AppData").forEach { name ->
                val dir = File(prefixDir, name)
                if (dir.exists() && dir.isDirectory) {
                    directories += dir
                }
            }
        }
        if (directories.isEmpty()) {
            return@withContext false
        }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutputStream ->
                directories.forEach { dir ->
                    zipOutputStream.putNextEntry(ZipEntry("${dir.name}/"))
                    zipOutputStream.closeEntry()
                    zipDirectory(zipOutputStream, dir, dir.name)
                }
            }
            true
        } ?: false
    }

    suspend fun importSaves(context: Context, appId: Int, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val shortcut = findSteamShortcut(context, appId)
        val goldbergSettingsDir = File(SteamService.getAppDirPath(appId), "steam_settings")
        val prefixDir = shortcut?.let { File(it.container.rootDir, ".wine/drive_c/users/xuser") }
        var importedAnything = false

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val destination = when {
                        name.startsWith("saves/") -> File(goldbergSettingsDir, name)
                        prefixDir != null && (
                            name.startsWith("Documents/") ||
                                name.startsWith("Saved Games/") ||
                                name.startsWith("AppData/")
                            ) -> File(prefixDir, name)
                        else -> null
                    }

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
        shortcut.putExtra("execArgs", null)
        rewriteExecLine(shortcut.file, STEAM_LOADER_EXEC)
        shortcut.saveData()
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
