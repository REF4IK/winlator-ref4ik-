package com.winlator.cmod.core

import android.content.Context
import android.content.Intent
import android.net.Uri
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
 * Источник правды — ТОЛЬКО asset `cronyx.json` в latest-релизе:
 * versionCode + builds[] {package, url}.
 * APK подбирается по packageName установленного приложения,
 * новее — по versionCode. Подпись проверяет сам Android при установке.
 * Нет json / битый json — показ причины, legacy-фолбэка нет.
 */
object UpdateManager {
    private const val TAG = "UpdateManager"

    // Репозиторий вида "user/repo"
    const val UPDATE_REPO = "REF4IK/CronyX-"

    private const val RELEASES_URL = "https://api.github.com/repos/$UPDATE_REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$UPDATE_REPO/releases/latest"

    private const val PREF_NOTIFY = "update_notify_enabled"
    private const val PREF_SKIP = "update_skip_version"

    /** Почему нет обновления: только для диагностики в диалоге. */
    enum class ManifestError { NONE, NO_MANIFEST, DOWNLOAD_FAILED, PARSE_ERROR, NO_BUILD }

    data class UpdateInfo(
        val tagName: String,
        val notes: String,
        val apkUrl: String?,
        val versionCode: Int = -1,
        val noBuildForPackage: Boolean = false,
        val error: ManifestError = ManifestError.NONE,
    ) {
        /** Новее ли релиз установленной версии. Только по живому манифесту. */
        val isNewer: Boolean
            get() = error == ManifestError.NONE && versionCode > BuildConfig.VERSION_CODE
    }

    data class CronyxBuild(
        val pkg: String,
        val url: String,
    )

    data class CronyxManifest(
        val versionName: String,
        val versionCode: Int,
        val notes: String,
        val builds: List<CronyxBuild>,
    )

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
     *
     * Путь: releases/latest → asset cronyx.json → подбор APK по
     * packageName. Без json обновления нет, только причина в UpdateInfo.error.
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
                    val assets = json.optJSONArray("assets")
                    val manifestUrl = findManifestUrl(assets)
                    if (manifestUrl == null) {
                        Log.w(TAG, "check: no manifest in latest=$tag")
                        onResult(UpdateInfo(tagName = tag, notes = notes, apkUrl = null, error = ManifestError.NO_MANIFEST))
                        return@Thread
                    }
                    val mbody = downloadText(manifestUrl)
                    if (mbody == null) {
                        Log.w(TAG, "check: manifest download failed")
                        onResult(UpdateInfo(tagName = tag, notes = notes, apkUrl = null, error = ManifestError.DOWNLOAD_FAILED))
                        return@Thread
                    }
                    val info = resolveFromManifest(ctx, mbody, tag, notes)
                    if (info == null) {
                        Log.w(TAG, "check: manifest parse failed")
                        onResult(UpdateInfo(tagName = tag, notes = notes, apkUrl = null, error = ManifestError.PARSE_ERROR))
                        return@Thread
                    }
                    Log.i(TAG, "check: manifest v=${info.tagName} vc=${info.versionCode} apk=${info.apkUrl} noBuild=${info.noBuildForPackage}")
                    onResult(info)
                }
            } catch (e: Exception) {
                Log.w(TAG, "check failed", e)
                onResult(null)
            }
        }.start()
    }

    /** URL манифеста в релизе. Терпимо к имени: содержит cronyx + .json. */
    private fun findManifestUrl(assets: JSONArray?): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").lowercase()
            if (name.contains("cronyx") && name.endsWith(".json")) {
                return asset.optString("browser_download_url").takeIf { it.startsWith("https://") }
            }
        }
        return null
    }

    /** Скачивание текстового файла (манифест). Лимит 128 КБ. */
    private fun downloadText(url: String): String? {
        return try {
            val request = okhttp3.Request.Builder().url(url)
                .header("User-Agent", "Winlator-CMOD")
                .build()
            DohOkHttp.get().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "manifest: HTTP ${response.code}")
                    return null
                }
                val text = response.body?.string() ?: return null
                if (text.length > 128 * 1024) {
                    Log.w(TAG, "manifest too large: ${text.length}")
                    return null
                }
                text
            }
        } catch (e: Exception) {
            Log.w(TAG, "manifest download failed", e)
            null
        }
    }

    /**
     * Разбор cronyx.json и подбор APK по packageName установленного приложения.
     * Подпись проверяет Android при установке (чужой ключ не встанет).
     * null — парсинг не удался, вызывающий вернёт PARSE_ERROR.
     */
    private fun resolveFromManifest(ctx: Context, body: String, fallbackTag: String, fallbackNotes: String): UpdateInfo? {
        return try {
            val manifest = parseManifest(body, fallbackTag, fallbackNotes) ?: return null
            val installedPkg = ctx.packageName
            val matched = manifest.builds.firstOrNull { it.pkg.equals(installedPkg, ignoreCase = true) }
            if (matched == null) {
                return UpdateInfo(
                    tagName = manifest.versionName,
                    notes = manifest.notes,
                    apkUrl = null,
                    versionCode = manifest.versionCode,
                    noBuildForPackage = true,
                    error = ManifestError.NO_BUILD,
                )
            }
            UpdateInfo(
                tagName = manifest.versionName,
                notes = manifest.notes,
                apkUrl = matched.url,
                versionCode = manifest.versionCode,
            )
        } catch (e: Exception) {
            Log.w(TAG, "manifest parse failed", e)
            null
        }
    }

    /** Парсинг cronyx.json. null при битом JSON. Комменты и висячие запятые терпит. */
    private fun parseManifest(body: String, fallbackTag: String, fallbackNotes: String): CronyxManifest? {
        val json = JSONObject(lenientJson(body))
        val versionName = json.optString("versionName", "").ifEmpty { json.optString("version", "") }.ifEmpty { fallbackTag }
        if (versionName.isEmpty()) return null
        val versionCode = json.optInt("versionCode", -1)
        if (versionCode < 0) return null
        val notes = json.optString("notes", "").trim().ifEmpty { fallbackNotes }
        val builds = mutableListOf<CronyxBuild>()
        val arr = json.optJSONArray("builds") ?: return CronyxManifest(versionName, versionCode, notes, builds)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pkg = o.optString("package", "").trim()
            if (pkg.isEmpty()) continue
            val url = o.optString("url", "").ifEmpty { o.optString("apkUrl", "") }.ifEmpty { o.optString("browser_download_url", "") }.trim()
            if (!url.startsWith("https://")) continue
            builds.add(CronyxBuild(pkg = pkg, url = url))
        }
        return CronyxManifest(versionName = versionName, versionCode = versionCode, notes = notes, builds = builds)
    }

    /**
     * Чистка ручного JSON: вырезает //- и slash-star комментарии
     * (строки не трогает, https:// в url целы), висячие запятые, BOM.
     */
    private fun lenientJson(body: String): String {
        val sb = StringBuilder(body.length)
        var inStr = false
        var esc = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (inStr) {
                sb.append(c)
                if (esc) esc = false
                else if (c == '\\') esc = true
                else if (c == '"') inStr = false
                i++
            } else {
                when {
                    c == '"' -> { inStr = true; sb.append(c); i++ }
                    c == '/' && i + 1 < body.length && body[i + 1] == '/' -> {
                        while (i < body.length && body[i] != '\n') i++
                    }
                    c == '/' && i + 1 < body.length && body[i + 1] == '*' -> {
                        i += 2
                        while (i + 1 < body.length && !(body[i] == '*' && body[i + 1] == '/')) i++
                        i += 2
                    }
                    else -> { sb.append(c); i++ }
                }
            }
        }
        return sb.toString().replace(Regex(",\\s*([}\\]])"), "$1").trim().trimStart('﻿')
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
