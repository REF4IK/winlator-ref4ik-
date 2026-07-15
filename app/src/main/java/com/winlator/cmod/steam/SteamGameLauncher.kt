package com.winlator.cmod.steam

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.WineUtils
import com.winlator.cmod.steam.data.SteamApp
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.enums.PathType
import com.winlator.cmod.steam.enums.SaveLocation
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.utils.SteamUtils
import com.winlator.cmod.xenvironment.ImageFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object SteamGameLauncher {
    private const val STEAM_LOADER_WIN_PATH = "C:/Program Files (x86)/Steam/steamclient_loader_x64.exe"
    private const val STEAM_LOADER_EXEC = "wine $STEAM_LOADER_WIN_PATH"

    fun launch(context: Context, app: SteamApp, preferredContainerId: Int? = null) {
        val gameInstallPath = SteamService.getAppDirPath(app.id)
        val gameDir = File(gameInstallPath)
        if (!gameDir.exists()) {
            Toast.makeText(context, context.getString(R.string.steam_game_not_installed, app.name), Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val containerManager = ContainerManager(context)
            val launchExecutable = withContext(Dispatchers.IO) {
                SteamService.getInstalledExe(app.id)
            }
            val allShortcuts = containerManager.loadShortcuts()
            var shortcut = allShortcuts.firstOrNull {
                it.getExtra("game_source") == "STEAM" && it.getExtra("app_id") == app.id.toString()
            }
            val container = when {
                shortcut != null -> shortcut.container
                preferredContainerId != null -> containerManager.getContainerById(preferredContainerId)
                else -> containerManager.containers.firstOrNull()
            }

            if (container == null) {
                Toast.makeText(context, R.string.steam_no_containers, Toast.LENGTH_LONG).show()
                return@launch
            }

            val offlineLaunch = PrefManager.steamOfflineMode || !SteamService.isConnected || !SteamService.isLoggedIn

            withContext(Dispatchers.IO) {
                containerManager.activateContainer(container)
            }

            val accountId = SteamService.userSteamId?.accountID?.toLong()
                ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toLong()
                ?: 0L
            val prefixToPath: (String) -> String = { prefix ->
                PathType.from(prefix).toAbsPath(context, app.id, accountId)
            }

            val hasPendingLocalCloudSync = SteamService.hasPendingCloudSync(context, app.id)
            var preferredSave = if (hasPendingLocalCloudSync) SaveLocation.Local else SaveLocation.None

            var launchSyncInfo = withContext(Dispatchers.IO) {
                SteamService.beginLaunchApp(
                    appId = app.id,
                    ignorePendingOperations = true,
                    preferredSave = preferredSave,
                    prefixToPath = prefixToPath,
                    isOffline = offlineLaunch,
                ).await()
            }

            if (launchSyncInfo.syncResult == SyncResult.Conflict) {
                preferredSave = if (hasPendingLocalCloudSync || launchSyncInfo.localTimestamp >= launchSyncInfo.remoteTimestamp) {
                    SaveLocation.Local
                } else {
                    SaveLocation.Remote
                }

                launchSyncInfo = withContext(Dispatchers.IO) {
                    SteamService.beginLaunchApp(
                        appId = app.id,
                        ignorePendingOperations = true,
                        preferredSave = preferredSave,
                        prefixToPath = prefixToPath,
                        isOffline = offlineLaunch,
                    ).await()
                }
            }

            when (launchSyncInfo.syncResult) {
                SyncResult.Success, SyncResult.UpToDate -> {
                    if (preferredSave == SaveLocation.Local && SteamService.isConnected && SteamService.isLoggedIn) {
                        SteamService.clearPendingCloudSync(context, app.id)
                    }
                }
                SyncResult.UpdateFail, SyncResult.UnknownFail -> {
                    if (hasPendingLocalCloudSync) {
                        SteamService.queuePendingCloudSync(context, app.id)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.steam_library_cloud_upload_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                SyncResult.DownloadFail -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.steam_library_cloud_download_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                else -> Unit
            }

            val preparedShortcut = withContext(Dispatchers.IO) {
                runCatching {
                    containerManager.activateContainer(container)
                    mountADrive(container, gameInstallPath)
                    container.saveData()
                    
                    try {
                        com.winlator.cmod.steam.workshop.WorkshopManager.generateWorkshopModsJson(
                            app.id, 
                            gameInstallPath, 
                            container.rootDir.absolutePath
                        )
                    } catch (e: Exception) {
                        // Ignore errors in generating workshop mods to not prevent game launch
                    }
                    
                    prepareSteamLaunchEnvironment(context, container, app, gameInstallPath, offlineLaunch)

                    if (shortcut == null) {
                        val shortcutFile = ensureSteamShortcut(container, app, gameInstallPath, launchExecutable)
                        Shortcut(container, shortcutFile)
                    } else {
                        shortcut!!.putExtra("game_source", "STEAM")
                        shortcut!!.putExtra("app_id", app.id.toString())
                        shortcut!!.putExtra("game_install_path", gameInstallPath)
                        shortcut!!.putExtra("launch_exe_path", launchExecutable)
                        shortcut!!.putExtra("execArgs", null)
                        rewriteExecLine(shortcut!!.file, STEAM_LOADER_EXEC)
                        shortcut!!.saveData()
                        shortcut
                    }
                }.getOrNull()
            }

            if (preparedShortcut == null) {
                Toast.makeText(context, R.string.steam_library_launch_prepare_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            shortcut = preparedShortcut

            // Notify Steam that we're launching a game
            if (!offlineLaunch) {
                withContext(Dispatchers.IO) {
                    SteamService.notifyGameRunningFromWineProcesses(
                        appId = app.id,
                        shortcutName = app.name,
                    )
                }
            }

            val intent = Intent(context, XServerDisplayActivity::class.java).apply {
                putExtra("container_id", shortcut!!.container.id)
                putExtra("shortcut_path", shortcut!!.file.path)
                putExtra("shortcut_name", shortcut!!.name)
                putExtra("return_to_steam_library", true)
                putExtra("steam_library_app_id", app.id)
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }

    private fun ensureSteamShortcut(
        container: Container,
        app: SteamApp,
        installPath: String,
        launchExecutable: String,
    ): File {
        val desktopDir = container.desktopDir
        if (!desktopDir.exists()) desktopDir.mkdirs()

        val safeName = app.name.replace("/", "_").replace("\\", "_")
        val shortcutFile = File(desktopDir, "$safeName.desktop")
        val content = StringBuilder()
        content.append("[Desktop Entry]\n")
        content.append("Type=Application\n")
        content.append("Name=${app.name}\n")
        content.append("Exec=$STEAM_LOADER_EXEC\n")
        content.append("Icon=steam_icon_${app.id}\n")
        content.append("\n[Extra Data]\n")
        content.append("game_source=STEAM\n")
        content.append("app_id=${app.id}\n")
        content.append("container_id=${container.id}\n")
        content.append("game_install_path=$installPath\n")
        content.append("launch_exe_path=$launchExecutable\n")
        content.append("use_container_defaults=1\n")
        FileUtils.writeString(shortcutFile, content.toString())
        return shortcutFile
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

    private fun mountADrive(container: Container, gamePath: String) {
        val currentDrives = container.drives ?: Container.DEFAULT_DRIVES
        val updated = StringBuilder()
        for (drive in Container.drivesIterator(currentDrives)) {
            if (drive[0] != "A") {
                updated.append(drive[0]).append(':').append(drive[1])
            }
        }
        updated.append("A:").append(gamePath)
        container.drives = updated.toString()
    }

    private suspend fun prepareSteamLaunchEnvironment(
        context: Context,
        container: Container,
        app: SteamApp,
        gameInstallPath: String,
        isOffline: Boolean,
    ) {
        val imageFs = ImageFs.find(context)
        val steamDir = File(container.rootDir, ".wine/drive_c/Program Files (x86)/Steam")
        val gameDir = File(gameInstallPath)
        val steamId64 = SteamService.userSteamId?.convertToUInt64()?.toString()
            ?: PrefManager.steamUserSteamId64.takeIf { it != 0L }?.toString()
            ?: "76561198000000000"
        val steamUserDataId = SteamService.userSteamId?.accountID?.toLong()?.toString()
            ?: PrefManager.steamUserAccountId.takeIf { it != 0 }?.toString()
            ?: steamId64
        val ticketBase64 = if (isOffline) {
            null
        } else {
            runCatching {
                SteamService.instance?.getEncryptedAppTicketBase64(app.id)
            }.getOrNull()
        }

        SteamClientManager.ensureColdClientSupportReady(context)
        steamDir.mkdirs()
        SteamUtils.backupSteamclientFiles(context, app.id)
        WineUtils.ensureSteamappsCommonSymlink(container, gameInstallPath)
        SteamUtils.putBackSteamDlls(gameInstallPath)
        SteamUtils.createAppManifest(context, app.id)
        SteamUtils.autoLoginUserChanges(imageFs)
        SteamUtils.skipFirstTimeSteamSetup(container.rootDir)
        SteamUtils.updateOrModifyLocalConfig(imageFs, container, app.id.toString(), steamUserDataId)
        SteamUtils.setupLightweightSteamConfig(imageFs, steamId64)
        SteamUtils.writeCompleteSettingsDir(steamDir, app.id, isOffline = isOffline, forceDlc = true, ticketBase64 = ticketBase64)
        SteamUtils.enrichSteamSettings(context, app.id, File(steamDir, "steam_settings"))
        writeGameSteamSettings(context, app.id, gameDir, ticketBase64, isOffline)
        SteamUtils.writeColdClientIni(app.id, container)
        copySteamRuntimeIntoGameDir(steamDir, gameDir)
    }

    private fun writeGameSteamSettings(
        context: Context,
        appId: Int,
        gameDir: File,
        ticketBase64: String?,
        isOffline: Boolean,
    ) {
        val candidateDirs = linkedSetOf<File>()
        if (gameDir.exists()) {
            gameDir.walkTopDown().maxDepth(8).forEach { file ->
                if (!file.isFile) return@forEach
                val name = file.name.lowercase()
                if (name == "steam_api.dll" || name == "steam_api64.dll" || name == "steamclient.dll" || name == "steamclient64.dll") {
                    file.parentFile?.let { candidateDirs += it }
                    runCatching { SteamUtils.generateInterfacesFile(file) }
                }
            }
        }
        if (candidateDirs.isEmpty()) candidateDirs += gameDir

        candidateDirs.forEach { dir ->
            SteamUtils.writeCompleteSettingsDir(dir, appId, isOffline = isOffline, ticketBase64 = ticketBase64)
            SteamUtils.enrichSteamSettings(context, appId, File(dir, "steam_settings"))
        }
    }

    private fun copySteamRuntimeIntoGameDir(steamDir: File, gameDir: File) {
        val embeddedSteamDir = File(gameDir, "Steam")
        if (embeddedSteamDir.exists()) return

        runCatching {
            embeddedSteamDir.mkdirs()
            val steamChildren = steamDir.listFiles() ?: return
            steamChildren.forEach { child ->
                val name = child.name.lowercase()
                if (name == "dumps" || name == "steamapps" || name == "userdata") return@forEach
                FileUtils.copy(child, File(embeddedSteamDir, child.name))
            }
        }
    }
}
