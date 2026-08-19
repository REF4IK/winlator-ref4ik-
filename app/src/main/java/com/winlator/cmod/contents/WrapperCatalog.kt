package com.winlator.cmod.contents

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

data class WrapperCatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val license: String,
    val url: String,
    val fileSize: Long,
    val checksum: String,
    val version: Int,
    val gpuTargets: List<String>,
)

object WrapperCatalog {
    private const val TAG = "WrapperCatalog"
    const val URL = "https://raw.githubusercontent.com/The412Banner/winlator-contents/main/wrappers.json"
    private const val CACHE_FILE = "wrapper_catalog.json"

    enum class Source { NETWORK, CACHE, NONE }
    data class Result(val entries: List<WrapperCatalogEntry>, val source: Source)

    private fun cacheFile(context: Context) = File(context.filesDir, CACHE_FILE)

    fun loadCached(context: Context): Result {
        val json = Downloader.downloadString(URL)
        if (json != null) {
            val parsed = parse(json)
            if (parsed.isNotEmpty()) {
                runCatching { cacheFile(context).writeText(json) }
                    .onFailure { Log.w(TAG, "failed to cache catalog", it) }
                return Result(parsed, Source.NETWORK)
            }
            if (looksLikeCatalog(json)) {
                runCatching { cacheFile(context).writeText(json) }
                return Result(emptyList(), Source.NETWORK)
            }
        }
        val cache = cacheFile(context)
        if (cache.isFile) {
            val cached = runCatching { parse(cache.readText()) }.getOrNull()
            if (!cached.isNullOrEmpty()) return Result(cached, Source.CACHE)
        }
        return Result(emptyList(), Source.NONE)
    }

    fun load(): List<WrapperCatalogEntry> =
        Downloader.downloadString(URL)?.let { parse(it) } ?: emptyList()

    private fun looksLikeCatalog(json: String): Boolean {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return false
        return root.has("wrappers")
    }

    private fun parse(json: String): List<WrapperCatalogEntry> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val mirrorBase = root.optString("mirrorBase")
        val arr = root.optJSONArray("wrappers") ?: return emptyList()
        val out = ArrayList<WrapperCatalogEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").ifBlank { o.optString("name") }.trim()
            if (id.isEmpty()) continue
            val url = o.optString("url").ifBlank {
                if (mirrorBase.isBlank()) "" else mirrorBase.trimEnd('/') + "/" + id + ".tzst"
            }
            if (url.isEmpty()) continue
            out.add(
                WrapperCatalogEntry(
                    id = id,
                    name = o.optString("name").ifBlank { id },
                    description = o.optString("description"),
                    author = o.optString("author"),
                    license = o.optString("license"),
                    url = url,
                    fileSize = o.optString("file_size").toLongOrNull() ?: o.optLong("file_size", 0L),
                    checksum = o.optString("file_checksum").trim().uppercase(),
                    version = o.optInt("version", 1),
                    gpuTargets = parseGpuTargets(o),
                )
            )
        }
        return out
    }

    private fun parseGpuTargets(o: JSONObject): List<String> {
        val arr = o.optJSONArray("gpuTargets") ?: o.optJSONArray("gpu_targets")
        if (arr == null || arr.length() == 0) return listOf("all")
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val t = arr.optString(i).trim().lowercase()
            if (t.isNotEmpty()) out.add(t)
        }
        return if (out.isEmpty()) listOf("all") else out.toList()
    }
}
