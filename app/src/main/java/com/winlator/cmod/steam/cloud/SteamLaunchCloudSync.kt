package com.winlator.cmod.steam.cloud

import android.content.Context
import com.winlator.cmod.steam.data.PostSyncInfo
import com.winlator.cmod.steam.enums.SaveLocation
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.service.SteamService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Синк облака при запуске игры (best-effort).
 *
 * Снапшот локальных сейвов → [SteamService.beginLaunchApp].
 * Конфликт local/remote отдаётся наружу через [onConflictChoice]
 * (там UI показывает [SteamCloudConflictDialog]).
 * BACKUP_BOTH: локальное уже лежит в снапшоте — берём Remote.
 * Существующий force-путь (SaveLocation) не тронут.
 */
object SteamLaunchCloudSync {
    suspend fun syncBeforeLaunch(
        context: Context,
        appId: Int,
        prefixToPath: (String) -> String,
        isOffline: Boolean = false,
        parentScope: CoroutineScope? = null,
        onConflictChoice: (suspend (CloudConflictData) -> CloudConflictChoice)? = null,
        onProgress: ((message: String, progress: Float) -> Unit)? = null,
    ): PostSyncInfo = withContext(Dispatchers.IO) {
        if (isOffline || !SteamService.isConnected || !SteamService.isLoggedIn) {
            return@withContext PostSyncInfo(SyncResult.UpToDate)
        }

        runCatching {
            SteamSaveSnapshotManager.snapshotBeforeUpload(context.applicationContext, appId, prefixToPath)
        }.onFailure { e ->
            Timber.w(e, "Launch cloud sync: snapshot failed for appId=$appId")
        }

        val scope = parentScope ?: this

        try {
            SteamService.beginLaunchApp(
                appId = appId,
                parentScope = scope,
                ignorePendingOperations = true,
                preferredSave = SaveLocation.None,
                prefixToPath = prefixToPath,
                isOffline = false,
                onProgress = onProgress,
                onConflict = onConflictChoice?.let { choose ->
                    { info: PostSyncInfo ->
                        val data = CloudConflictData(
                            localTimestamp = info.localTimestamp,
                            remoteTimestamp = info.remoteTimestamp,
                            localBytes = info.localBytes,
                            remoteBytes = info.remoteBytes,
                        )
                        when (choose(data)) {
                            CloudConflictChoice.USE_LOCAL -> SaveLocation.Local
                            CloudConflictChoice.USE_REMOTE -> SaveLocation.Remote
                            // Локальное уже забэкаплено снапшотом выше — забираем облако.
                            CloudConflictChoice.BACKUP_BOTH -> SaveLocation.Remote
                        }
                    }
                },
            ).await()
        } catch (e: Exception) {
            Timber.e(e, "Launch cloud sync failed for appId=$appId")
            PostSyncInfo(SyncResult.UnknownFail)
        }
    }
}
