package com.winlator.cmod

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.winlator.cmod.service.DownloadService
import com.winlator.cmod.steam.events.EventDispatcher
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.utils.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.security.Security

class PluviaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())

        PrefManager.init(this)
        if (PrefManager.enableSteamLogs && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }

        DownloadService.populateDownloadService(this)
        NetworkMonitor.init(this)
        com.winlator.cmod.db.PluviaDatabase.init(this)

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { SteamService.repairInstalledMetadataFromDisk() }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                currentForegroundActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentForegroundActivity === activity) {
                    currentForegroundActivity = null
                }
            }

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                if (currentForegroundActivity === activity) {
                    currentForegroundActivity = null
                }
            }
        })

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("PluviaApp", "CRASH in thread ${thread.name}", throwable)
        }
    }

    companion object {
        lateinit var instance: PluviaApp
            private set

        @Volatile
        var currentForegroundActivity: Activity? = null
            private set

        @JvmField
        val events = EventDispatcher()
    }
}
