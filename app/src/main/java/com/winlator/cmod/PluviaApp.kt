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
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import android.os.Build.VERSION.SDK_INT
import java.io.File
import com.tencent.mmkv.MMKV

class PluviaApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val cacheDir = File(filesDir, "steam_image_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        return ImageLoader.Builder(this)
            .okHttpClient {
                com.winlator.cmod.core.DohOkHttp.get().newBuilder()
                    .cache(okhttp3.Cache(cacheDir, 512L * 1024 * 1024))
                    .addNetworkInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        val cacheControl = response.header("Cache-Control")
                        if (cacheControl != null && (cacheControl.contains("no-store") || cacheControl.contains("no-cache") || cacheControl.contains("max-age=0"))) {
                            response.newBuilder()
                                .removeHeader("Cache-Control")
                                .removeHeader("Pragma")
                                .header("Cache-Control", "public, max-age=604800, stale-while-revalidate=2592000")
                                .build()
                        } else if (cacheControl == null) {
                            response.newBuilder()
                                .header("Cache-Control", "public, max-age=604800, stale-while-revalidate=2592000")
                                .build()
                        } else {
                            response
                        }
                    }
                    .build()
            }
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        MMKV.initialize(this)
        MMKV.defaultMMKV(MMKV.MULTI_PROCESS_MODE, null)

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
