package com.winlator.cmod.steam.cloud

import android.content.Context
import com.winlator.cmod.steam.data.PostSyncInfo
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.service.SteamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Синк облака при выходе из игры (best-effort).
 *
 * Оффлайн/не залогинен → задача в pending CSV ([SteamService.queuePendingCloudSync]).
 * Успех → pending чистится, неуспех → ставится в очередь.
 * Существующий [SteamService.handleAppExitCloudSync] не тронут.
 */
object SteamExitCloudSync {
    suspend fun syncOnExit(
        context: Context,
        appId: Int,
        prefixToPath: (String) -> String,
        onProgress: ((message: String, progress: Float) -> Unit)? = null,
    ): PostSyncInfo = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext

        if (!SteamService.isConnected || !SteamService.isLoggedIn) {
            SteamService.queuePendingCloudSync(appContext, appId)
            return@withContext PostSyncInfo(SyncResult.UpToDate)
        }

        val info = try {
            SteamService.closeApp(
                appId = appId,
                isOffline = false,
                prefixToPath = prefixToPath,
                onProgress = onProgress,
            ).await()
        } catch (e: Exception) {
            Timber.e(e, "Exit cloud sync failed for appId=$appId")
            PostSyncInfo(SyncResult.UnknownFail)
        }

        if (info.syncResult == SyncResult.Success || info.syncResult == SyncResult.UpToDate) {
            SteamService.clearPendingCloudSync(appContext, appId)
        } else {
            Timber.w("Exit cloud sync incomplete for appId=$appId result=${info.syncResult}")
            SteamService.queuePendingCloudSync(appContext, appId)
        }

        info
    }
}
