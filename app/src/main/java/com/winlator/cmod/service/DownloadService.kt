package com.winlator.cmod.service

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import com.winlator.cmod.steam.data.DownloadInfo
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object DownloadService {
    var baseDataDirPath: String = ""
        private set
    var baseCacheDirPath: String = ""
        private set
    var baseExternalAppDirPath: String = ""
        private set
    var externalVolumePaths: List<String> = emptyList()
        private set
    var appContext: Context? = null
        private set

    fun populateDownloadService(context: Context) {
        appContext = context.applicationContext
        baseDataDirPath = context.dataDir.path
        baseCacheDirPath = context.cacheDir.path
        val extFiles = context.getExternalFilesDir(null)
        baseExternalAppDirPath = extFiles?.parentFile?.path ?: ""

        val storageManager = context.getSystemService(StorageManager::class.java)
        externalVolumePaths = context.getExternalFilesDirs(null)
            .filterNotNull()
            .filter { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            .filter { storageManager?.getStorageVolume(it)?.isPrimary != true }
            .map { it.absolutePath }
            .distinct()
    }

    fun getAllDownloads(): List<Pair<String, DownloadInfo>> {
        val list = mutableListOf<Pair<String, DownloadInfo>>()
        SteamService.getAllDownloads().forEach { (id, info) -> list.add("STEAM_$id" to info) }
        return list
    }

    fun pauseAll() {
        SteamService.pauseAll()
    }

    fun resumeAll() {
        SteamService.resumeAll()
    }

    fun cancelAll() {
        SteamService.cancelAll()
    }

    fun clearCompletedDownloads() {
        SteamService.clearCompletedDownloads()
    }

    fun pauseDownload(id: String) {
        val appId = id.removePrefix("STEAM_").toIntOrNull() ?: return
        SteamService.pauseDownload(appId)
    }

    fun resumeDownload(id: String) {
        val appId = id.removePrefix("STEAM_").toIntOrNull() ?: return
        SteamService.resumeDownload(appId)
    }

    fun cancelDownload(id: String) {
        val appId = id.removePrefix("STEAM_").toIntOrNull() ?: return
        SteamService.cancelDownload(appId)
    }

    fun getSizeFromStoreDisplay(appId: Int): String {
        val depots = SteamService.getDownloadableDepots(appId)
        val installBytes = depots.values.sumOf { it.manifests["public"]?.size ?: 0L }
        return StorageUtils.formatBinarySize(installBytes)
    }

    suspend fun getSizeOnDiskDisplay(appId: Int, setResult: (String) -> Unit) {
        withContext(Dispatchers.IO) {
            if (SteamService.isAppInstalled(appId)) {
                val appSizeText = StorageUtils.formatBinarySize(
                    StorageUtils.getFolderSize(SteamService.getAppDirPath(appId)),
                )
                Timber.d("Steam app $appId size on disk $appSizeText")
                setResult(appSizeText)
            }
        }
    }
}
