package com.winlator.cmod.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object SteamImageCache {
    private const val TAG = "SteamImageCache"
    private var cacheDir: File? = null

    fun getDir(context: Context): File {
        val dir = cacheDir ?: File(context.filesDir, "steam_images").also { cacheDir = it }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCachedFile(context: Context, url: String): File? {
        val file = File(getDir(context), urlToFileName(url))
        val exists = file.exists() && file.length() > 0
        if (exists) return file
        return null
    }

    suspend fun downloadIfNeeded(context: Context, url: String): File? = withContext(Dispatchers.IO) {
        try {
            getCachedFile(context, url)?.let { return@withContext it }
            val file = File(getDir(context), urlToFileName(url))
            val tmp = File(file.parentFile, "${file.name}.tmp")

            // Try HttpURLConnection first (works through VPN)
            var success = tryHttpUrlConnection(url, tmp)
            
            // Fallback to DohOkHttp
            if (!success) {
                success = tryDohOkHttp(url, tmp)
            }

            if (success && tmp.length() > 0) {
                tmp.renameTo(file)
                Log.d(TAG, "Downloaded OK: $url -> ${file.name} (${file.length()} bytes)")
                file
            } else {
                tmp.delete()
                Log.w(TAG, "Download failed: $url")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download error $url: ${e.message}")
            null
        }
    }

    private fun tryHttpUrlConnection(url: String, tmp: File): Boolean {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Winlator")
            conn.instanceFollowRedirects = true
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(tmp).use { out -> input.copyTo(out) }
                }
                conn.disconnect()
                tmp.length() > 0
            } else {
                conn.disconnect()
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "HttpURLConnection failed $url: ${e.message}")
            false
        }
    }

    private fun tryDohOkHttp(url: String, tmp: File): Boolean {
        return try {
            val client = DohOkHttp.get()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.byteStream()?.use { input ->
                        java.io.FileOutputStream(tmp).use { out -> input.copyTo(out) }
                    }
                    tmp.length() > 0
                } else false
            }
        } catch (e: Exception) {
            Log.w(TAG, "DohOkHttp failed $url: ${e.message}")
            false
        }
    }

    private fun urlToFileName(url: String): String {
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val ext = url.substringAfterLast(".", "jpg")
        return "${hash.take(16)}.$ext"
    }
}
