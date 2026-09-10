package com.winlator.cmod.steam.achievements

import android.content.Context
import android.os.FileObserver
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.SteamUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Следит за Goldberg-файлом GSE Saves/{appId}/achievements.json.
 * При новых анлоках зовёт [onUnlock] и дебаунсом (5с) заливает прогресс в Steam (best-effort).
 * Неуспешная заливка уходит в очередь pending_goldberg_syncs.json.
 */
class AchievementWatcher(
    private val appId: Int,
    context: Context,
    private val onUnlock: (name: String, displayName: String, iconUrl: String?) -> Unit = { _, _, _ -> },
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var observer: FileObserver? = null
    private var uploadJob: Job? = null
    private val notifiedNames = mutableSetOf<String>()
    private val uploadedNames = mutableSetOf<String>()
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        val dirs = SteamService.getGseSaveDirs(appId)
        if (dirs.isEmpty()) {
            Timber.tag("achievements").d("No GSE dirs for appId=$appId, watcher idle")
            return
        }
        // Снапшот уже открытых, чтобы не уведомлять о старых при старте.
        for (dir in dirs) {
            val achFile = File(dir, "achievements.json")
            if (!achFile.isFile) continue
            try {
                val json = JSONObject(achFile.readText(Charsets.UTF_8))
                for (name in json.keys()) {
                    if (json.optJSONObject(name)?.optBoolean("earned", false) == true) {
                        notifiedNames.add(name)
                        uploadedNames.add(name)
                    }
                }
            } catch (e: Exception) {
                Timber.tag("achievements").w(e, "Failed to snapshot ${achFile.absolutePath}")
            }
        }
        Timber.tag("achievements").d("Watcher started for appId=$appId, seeded=${notifiedNames.size}")

        val watchDir = dirs.firstOrNull { it.isDirectory } ?: dirs.first().apply { mkdirs() }
        @Suppress("DEPRECATION")
        val fileObserver = object : FileObserver(watchDir, CLOSE_WRITE or MOVED_TO or MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "achievements.json") {
                    checkForNewUnlocks(File(watchDir, "achievements.json"))
                }
            }
        }
        fileObserver.startWatching()
        observer = fileObserver
    }

    fun stop() {
        started = false
        uploadJob?.cancel()
        uploadJob = null
        runCatching { observer?.stopWatching() }
        observer = null
        scope.cancel()
        Timber.tag("achievements").d("Watcher stopped for appId=$appId")
    }

    private fun checkForNewUnlocks(achFile: File) {
        if (!achFile.isFile) return
        var hasNewUnlocks = false
        try {
            val json = JSONObject(achFile.readText(Charsets.UTF_8))
            for (name in json.keys()) {
                val entry = json.optJSONObject(name) ?: continue
                if (!entry.optBoolean("earned", false)) continue
                if (!notifiedNames.add(name)) continue
                hasNewUnlocks = true
                val meta = SteamService.getCachedAchievementMeta(appId, name)
                onUnlock(name, meta?.first ?: name, meta?.second)
                Timber.tag("achievements").i("Achievement unlocked: $name for appId=$appId")
            }
        } catch (e: Exception) {
            Timber.tag("achievements").w(e, "Failed to parse achievements.json for appId=$appId")
        }
        if (hasNewUnlocks) scheduleUpload()
    }

    /** Дебаунс заливки: ждём 5с после последнего анлока, чтобы не спамить сервер. */
    private fun scheduleUpload() {
        uploadJob?.cancel()
        uploadJob = scope.launch {
            delay(UPLOAD_DEBOUNCE_MS)
            uploadToSteam()
        }
    }

    private suspend fun uploadToSteam() {
        if (!SteamService.isLoggedIn) {
            Timber.tag("achievements").w("Not logged in, queueing sync for appId=$appId")
            SteamUtils.queuePendingGoldbergSync(appContext, appId)
            return
        }
        runCatching { SteamService.syncAchievementsFromGoldberg(appId) }
            .onSuccess {
                uploadedNames.addAll(notifiedNames)
                Timber.tag("achievements").i("Upload succeeded for appId=$appId")
            }
            .onFailure {
                Timber.tag("achievements").w(it, "Upload failed for appId=$appId, queueing retry")
                SteamUtils.queuePendingGoldbergSync(appContext, appId)
            }
    }

    companion object {
        private const val UPLOAD_DEBOUNCE_MS = 5_000L
    }
}
