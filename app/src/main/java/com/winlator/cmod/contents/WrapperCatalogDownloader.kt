package com.winlator.cmod.contents

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

object WrapperCatalogDownloader {
    private const val TAG = "WrapperCatalogDownloader"

    suspend fun install(
        context: Context,
        entry: WrapperCatalogEntry,
        onProgress: (Int) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "wrapper_dl").apply { mkdirs() }
        val archive = File(cacheDir, "${safe(entry.id)}.tzst")
        if (archive.exists()) archive.delete()
        try {
            onProgress(0)
            var ok = false
            ok = Downloader.downloadFile(entry.url, archive) { downloaded, total ->
                val fraction = if (total > 0) downloaded.toFloat() / total else 0f
                onProgress((fraction.coerceIn(0f, 1f) * 100f).toInt())
            }
            if (!ok) {
                Log.w(TAG, "download failed: ${entry.url}")
                return@withContext null
            }
            if (entry.checksum.isNotBlank()) {
                val actual = md5Upper(archive)
                if (!actual.equals(entry.checksum, ignoreCase = true)) {
                    Log.w(TAG, "checksum mismatch for ${entry.id}: expected ${entry.checksum} got $actual")
                    archive.delete()
                    return@withContext null
                }
            }
            onProgress(100)
            val identifier = WrapperManager(context)
                .importWrapper(Uri.fromFile(archive), entry.name, entry.id, entry.version)
            if (identifier == null) Log.w(TAG, "import failed for ${entry.id}")
            identifier
        } catch (t: Throwable) {
            Log.w(TAG, "install failed for ${entry.id}", t)
            null
        } finally {
            archive.delete()
        }
    }

    suspend fun installToSlot(
        context: Context,
        entry: WrapperCatalogEntry,
        slotFileName: String,
        onProgress: (Int) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "wrapper_dl").apply { mkdirs() }
        val archive = File(cacheDir, "${safe(entry.id)}.tzst")
        if (archive.exists()) archive.delete()
        try {
            onProgress(0)
            val ok = Downloader.downloadFile(entry.url, archive) { downloaded, total ->
                val fraction = if (total > 0) downloaded.toFloat() / total else 0f
                onProgress((fraction.coerceIn(0f, 1f) * 100f).toInt())
            }
            if (!ok) {
                Log.w(TAG, "download failed: ${entry.url}")
                return@withContext false
            }
            if (entry.checksum.isNotBlank()) {
                val actual = md5Upper(archive)
                if (!actual.equals(entry.checksum, ignoreCase = true)) {
                    Log.w(TAG, "checksum mismatch for ${entry.id}: expected ${entry.checksum} got $actual")
                    return@withContext false
                }
            }
            onProgress(100)
            val wm = WrapperManager(context)
            val result = wm.installOverride(slotFileName, Uri.fromFile(archive))
            if (result) {
                wm.recordSlotCatalog(slotFileName, entry.id, entry.version, entry.name)
            } else {
                Log.w(TAG, "installOverride failed for ${entry.id} -> $slotFileName")
            }
            result
        } catch (t: Throwable) {
            Log.w(TAG, "installToSlot failed for ${entry.id}", t)
            false
        } finally {
            archive.delete()
        }
    }

    private fun md5Upper(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02X".format(it) }
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
