package com.winlator.cmod.steam.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

// HEAD-probe сортировка CDN-хостов по latency. Эталон: GameNative CdnRankingUtils.
// Любой HTTP-ответ (включая 4xx) = хост жив. Провал всех проб = исходный порядок.
object CdnRankingUtils {
    const val DEFAULT_PROBE_TIMEOUT_MS = 3000L
    const val MAX_PROBE_URLS = 12

    suspend fun rankBaseUrlsByHeadProbe(
        baseUrls: List<String>,
        httpClient: OkHttpClient,
        timeoutMs: Long = DEFAULT_PROBE_TIMEOUT_MS,
    ): List<String> = withContext(Dispatchers.IO) {
        val urls = baseUrls.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_PROBE_URLS)
        if (urls.size <= 1) return@withContext urls.ifEmpty { baseUrls }

        // У общего клиента таймауты минутные — для проб свой, с таймаутом 3с.
        val probeClient = runCatching {
            httpClient.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs + 1000L, TimeUnit.MILLISECONDS)
                .build()
        }.getOrElse { httpClient }

        val scored = urls.map { url ->
            async {
                val start = System.nanoTime()
                val success = withTimeoutOrNull(timeoutMs + 1000L) {
                    runCatching {
                        val request = Request.Builder()
                            .url(url)
                            .head()
                            .header("User-Agent", "Valve/Steam HTTP Client 1.0")
                            .build()
                        probeClient.newCall(request).execute().use { response ->
                            response.code in 200..499
                        }
                    }.getOrDefault(false)
                } ?: false
                val elapsedMs = (System.nanoTime() - start) / 1_000_000
                Triple(url, success, elapsedMs)
            }
        }.awaitAll()

        if (scored.none { it.second }) {
            Timber.w("CdnRanking: all ${urls.size} probes failed, keeping original order")
            return@withContext urls
        }
        scored
            .sortedWith(compareByDescending<Triple<String, Boolean, Long>> { it.second }.thenBy { it.third })
            .map { it.first }
    }
}
