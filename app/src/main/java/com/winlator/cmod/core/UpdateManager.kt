package com.winlator.cmod.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.winlator.cmod.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Встроенный апдейтер: проверка новых версий через GitHub Releases API,
 * скачивание APK с прогрессом и установка через FileProvider.
 *
 * Источник правды — tag релиза (например "7.1.5x-cmod") и APK-asset в релизе.
 */
object UpdateManager {
    private const val TAG = "UpdateManager"

    // Репозиторий вида "user/repo"
    const val UPDATE_REPO = "REF4IK/CronyX-"

    private const val RELEASES_URL = "https://api.github.com/repos/$UPDATE_REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$UPDATE_REPO/releases/latest"

    private const val PREF_NOTIFY = "update_notify_enabled"
    private const val PREF_SKIP = "update_skip_version"

    data class UpdateInfo(
        val tagName: String,
        val notes: String,
        val apkUrl: String?,
    ) {
        /** Новее ли релиз установленной версии. */
        val isNewer: Boolean
            get() = compareVersions(tagName, BuildConfig.VERSION_NAME) > 0
    }

    // ── Preferences ──────────────────────────────────────────────────────

    fun isNotifyEnabled(ctx: Context): Boolean =
        MmkvPreferences().getBoolean(PREF_NOTIFY, true)

    fun setNotifyEnabled(ctx: Context, enabled: Boolean) =
        MmkvPreferences().edit().putBoolean(PREF_NOTIFY, enabled).apply()

    fun skippedVersion(ctx: Context): String =
        MmkvPreferences().getString(PREF_SKIP, "") ?: ""

    fun skipVersion(ctx: Context, version: String) =
        MmkvPreferences().edit().putString(PREF_SKIP, version).apply()

    // ── Проверка ────────────────────────────────────────────────────────

    /**
     * Запрос к GitHub API на фоновом потоке. [onResult] вызывается на том же
     * потоке (не UI). При сетевой ошибке — null.
     */
    fun check(ctx: Context, onResult: (UpdateInfo?) -> Unit) {
        Thread {
            try {
                val request = okhttp3.Request.Builder().url(RELEASES_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Winlator-CMOD")
                    .build()
                DohOkHttp.get().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "check: HTTP ${response.code}")
                        onResult(null)
                        return@Thread
                    }
                    val body = response.body?.string() ?: run {
                        onResult(null)
                        return@Thread
                    }
                    val json = JSONObject(body)
                    val tag = json.optString("tag_name", "")
                    val notes = json.optString("body", "").trim()
                    val apkUrl = pickApkUrl(json.optJSONArray("assets"))
                    val info = UpdateInfo(tagName = tag, notes = notes, apkUrl = apkUrl)
                    Log.i(TAG, "check: latest=$tag installed=${BuildConfig.VERSION_NAME} apk=$apkUrl")
                    onResult(info)
                }
            } catch (e: Exception) {
                Log.w(TAG, "check failed", e)
                onResult(null)
            }
        }.start()
    }

    private fun pickApkUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").lowercase()
            if (name.endsWith(".apk") && !name.endsWith("-universal.apk")) {
                return asset.optString("browser_download_url")
            }
        }
        // фолбэк: любой apk
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").lowercase()
            if (name.endsWith(".apk")) {
                return asset.optString("browser_download_url")
            }
        }
        return null
    }

    // ── Скачивание ──────────────────────────────────────────────────────

    /**
     * Скачивает APK в cacheDir с прогрессом 0..1. По завершении вызывает
     * [onDone] с файлом или null при ошибке. Работает на фоновом потоке.
     */
    fun downloadApk(ctx: Context, url: String, onProgress: (Float) -> Unit, onDone: (File?) -> Unit) {
        Thread {
            val target = File(ctx.cacheDir, "update.apk")
            try {
                val request = okhttp3.Request.Builder().url(url).build()
                DohOkHttp.get().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "download: HTTP ${response.code}")
                        onDone(null)
                        return@Thread
                    }
                    val body = response.body ?: run {
                        onDone(null)
                        return@Thread
                    }
                    val total = body.contentLength()
                    var written = 0L
                    FileOutputStream(target).use { fos ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (body.byteStream().read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            written += read
                            if (total > 0) onProgress(written.toFloat() / total)
                        }
                        fos.flush()
                    }
                    Log.i(TAG, "download done: ${target.length()} bytes")
                    onDone(target)
                }
            } catch (e: Exception) {
                Log.w(TAG, "download failed", e)
                try { target.delete() } catch (_: Exception) {}
                onDone(null)
            }
        }.start()
    }

    // ── Установка ───────────────────────────────────────────────────────

    /** Запускает системный установщик для скачанного APK. */
    fun installApk(ctx: Context, apkFile: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(ctx, "${BuildConfig.APPLICATION_ID}.tileprovider", apkFile)
            val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "install failed", e)
            false
        }
    }

    /** Сравнение версий вида "7.1.5x-cmod" / "v1.2.3": только числовые сегменты. */
    fun compareVersions(a: String, b: String): Int {
        val sa = a.lowercase().trim().trimStart('v').split("[^0-9]+".toRegex()).filter { it.isNotEmpty() }
        val sb = b.lowercase().trim().trimStart('v').split("[^0-9]+".toRegex()).filter { it.isNotEmpty() }
        val len = maxOf(sa.size, sb.size)
        for (i in 0 until len) {
            val va = sa.getOrNull(i)?.toIntOrNull() ?: 0
            val vb = sb.getOrNull(i)?.toIntOrNull() ?: 0
            if (va != vb) return va - vb
        }
        return 0
    }
}
