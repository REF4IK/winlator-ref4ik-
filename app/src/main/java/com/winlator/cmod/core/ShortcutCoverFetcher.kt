package com.winlator.cmod.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.gson.GsonBuilder
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.steamgrid.SteamGridDBApi
import com.winlator.cmod.steamgrid.SteamGridGridsResponse
import com.winlator.cmod.steamgrid.SteamGridGridsResponseDeserializer
import com.winlator.cmod.steamgrid.SteamGridSearchResponse
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ShortcutCoverFetcher {
    private const val STEAMGRID_BASE_URL = "https://www.steamgriddb.com/api/v2/"
    private const val STEAMGRID_API_KEY = "4765cce5e92f8406ab0f5346c3b5e3ba"

    private val COVER_ART_EXECUTOR = Executors.newFixedThreadPool(5)

    private val COVER_ART_HTTP = DohOkHttp.get().newBuilder()
        .callTimeout(90, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val STEAMGRID_LAST_FAIL = ConcurrentHashMap<String, Long>()
    private val STEAM_APPID_CACHE = ConcurrentHashMap<String, Int>()

    private val STEAM_ASSET_HOSTS = arrayOf(
        "cdn.cloudflare.steamstatic.com",
        "cdn.akamai.steamstatic.com",
        "steamcdn-a.akamaihd.net"
    )

    private val ANIMATED_EXTENSIONS = arrayOf(".gif", ".webp", ".apng")

    fun getCachedCoverFile(context: Context, shortcutName: String, landscape: Boolean): File? {
        val cacheDir = File(context.filesDir, "coverArtCache")
        for (ext in ANIMATED_EXTENSIONS) {
            val name = if (landscape) "${shortcutName}_l$ext" else "$shortcutName$ext"
            val f = File(cacheDir, name)
            if (f.exists()) return f
        }
        val staticFile = File(cacheDir, coverCacheFileName(shortcutName, landscape))
        if (staticFile.exists()) return staticFile
        return null
    }

    private fun coverCacheFileName(shortcutName: String, landscape: Boolean): String {
        return if (landscape) "${shortcutName}_l.png" else "$shortcutName.png"
    }

    fun loadCoverArt(context: Context, shortcut: Shortcut, landscape: Boolean): File? {
        // 1. Check custom cover art
        val customPath = shortcut.customCoverArtPath
        if (!customPath.isNullOrEmpty()) {
            val f = File(customPath)
            if (f.isFile) return f
        }

        // 2. Check default cover art in container folder
        val containerCover = File(File(shortcut.container.rootDir, "app_data/cover_arts"), "${shortcut.name}.png")
        if (containerCover.isFile) return containerCover

        // 3. Check cache
        val cached = getCachedCoverFile(context, shortcut.name, landscape)
        if (cached != null && cached.isFile) return cached

        return null
    }

    fun fetchCoverArt(context: Context, shortcut: Shortcut, landscape: Boolean, onDone: () -> Unit) {
        if (loadCoverArt(context, shortcut, landscape) != null) return

        COVER_ART_EXECUTOR.execute {
            try {
                val appId = resolveSteamAppIdByStoreSearch(shortcut.name)
                if (appId == null || appId <= 0) {
                    Log.w("CoverArt", "Steam CDN: appid not found name=${shortcut.name}, falling back to SteamGridDB")
                    fetchSteamGridCoverArt(context, shortcut, landscape, onDone)
                    return@execute
                }

                for (url in buildSteamCoverUrls(appId, landscape)) {
                    if (!shortcut.customCoverArtPath.isNullOrEmpty()) return@execute
                    if (getCachedCoverFile(context, shortcut.name, landscape) != null) return@execute

                    val bmp = downloadBitmap(url)
                    if (bmp != null) {
                        cacheCoverArt(context, bmp, shortcut.name, landscape)
                        onDone()
                        Log.i("CoverArt", "Steam CDN cover art downloaded name=${shortcut.name} appId=$appId landscape=$landscape")
                        return@execute
                    }
                }

                Log.w("CoverArt", "Steam CDN: all urls failed name=${shortcut.name} appId=$appId, falling back to SteamGridDB")
                fetchSteamGridCoverArt(context, shortcut, landscape, onDone)
            } catch (e: Exception) {
                Log.w("CoverArt", "Steam CDN cover art failed name=${shortcut.name}", e)
                fetchSteamGridCoverArt(context, shortcut, landscape, onDone)
            }
        }
    }

    private fun fetchSteamGridCoverArt(context: Context, shortcut: Shortcut, landscape: Boolean, onDone: () -> Unit) {
        val lastFail = STEAMGRID_LAST_FAIL[shortcut.name]
        if (lastFail != null && (System.currentTimeMillis() - lastFail) < 10 * 60 * 1000L) {
            fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
            return
        }

        // Skip SteamGridDB search for very short names (likely abbreviations with no match)
        if (shortcut.name.trim().length < 3) {
            Log.w("CoverArt", "SteamGrid: name too short (len<3), skipping search name=${shortcut.name}")
            fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
            return
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(STEAMGRID_BASE_URL)
            .client(DohOkHttp.get())
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val api = retrofit.create(SteamGridDBApi::class.java)
        val searchName = normalizeSteamGridSearchName(shortcut.name)
        val call = api.searchGame("Bearer $STEAMGRID_API_KEY", searchName)

        call.enqueue(object : retrofit2.Callback<SteamGridSearchResponse> {
            override fun onResponse(call: retrofit2.Call<SteamGridSearchResponse>, response: retrofit2.Response<SteamGridSearchResponse>) {
                if (!response.isSuccessful || response.body()?.data.isNullOrEmpty()) {
                    Log.w("CoverArt", "SteamGrid search failed/empty name=${shortcut.name}")
                    STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                    fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
                    return
                }

                // --- Name validation: find the best matching result ---
                val data = response.body()!!.data
                var bestId = -1
                var bestScore = -1
                var bestResultName = ""

                for (result in data) {
                    val resultName = result.name?.trim() ?: continue
                    if (resultName.isEmpty()) continue
                    val score = computeMatchScore(shortcut.name, resultName)
                    Log.d("CoverArt", "SteamGrid result: query=${shortcut.name} -> result=$resultName id=${result.id} score=$score")
                    if (score > bestScore) {
                        bestScore = score
                        bestId = result.id
                        bestResultName = resultName
                    }
                }

                if (bestId <= 0 || bestScore < 70) {
                    Log.w("CoverArt", "SteamGrid: no good match found name=${shortcut.name} bestScore=$bestScore bestResult=$bestResultName")
                    STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                    fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
                    return
                }

                Log.i("CoverArt", "SteamGrid: selected match name=${shortcut.name} -> result=$bestResultName id=$bestId score=$bestScore")
                fetchGridsForGame(context, bestId, shortcut, landscape, onDone)
            }

            override fun onFailure(call: retrofit2.Call<SteamGridSearchResponse>, t: Throwable) {
                Log.w("CoverArt", "SteamGrid search error name=${shortcut.name}", t)
                STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
            }
        })
    }

    private fun fetchGridsForGame(context: Context, gameId: Int, shortcut: Shortcut, landscape: Boolean, onDone: () -> Unit) {
        val gson = GsonBuilder()
            .registerTypeAdapter(SteamGridGridsResponse::class.java, SteamGridGridsResponseDeserializer())
            .create()

        val retrofit = Retrofit.Builder()
            .baseUrl(STEAMGRID_BASE_URL)
            .client(DohOkHttp.get())
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        val api = retrofit.create(SteamGridDBApi::class.java)
        val dimensions = if (landscape) "920x430,460x215" else "600x900"
        val auth = "Bearer $STEAMGRID_API_KEY"

        val animatedCall = api.getAnimatedGridsByGameId(
            auth, gameId, "alternate,blurred,white_logo,material,no_logo", dimensions, "animated", "image/webp", "false", "false"
        )

        animatedCall.enqueue(object : retrofit2.Callback<SteamGridGridsResponse> {
            override fun onResponse(call: retrofit2.Call<SteamGridGridsResponse>, response: retrofit2.Response<SteamGridGridsResponse>) {
                if (response.isSuccessful && !response.body()?.data.isNullOrEmpty()) {
                    var best: SteamGridGridsResponse.Grid? = null
                    var apngFallback: SteamGridGridsResponse.Grid? = null
                    for (g in response.body()!!.data) {
                        val mime = g.mime?.lowercase(Locale.US) ?: ""
                        val ext = if (g.url != null) urlToExtension(g.url) else ""
                        if (mime.contains("webp") || ext == ".webp" || mime.contains("gif") || ext == ".gif") {
                            best = g
                            break
                        }
                        if (apngFallback == null && (mime.contains("png") || ext == ".png")) {
                            apngFallback = g
                        }
                    }
                    if (best == null) best = apngFallback
                    if (best?.url != null && best.url.isNotEmpty()) {
                        Log.i("CoverArt", "Animated cover found name=${shortcut.name} mime=${best.mime} url=${best.url}")
                        downloadCoverArt(context, best.url, shortcut, landscape, true, best.mime, onDone)
                        return
                    }
                }
                Log.i("CoverArt", "No WebP animated cover for name=${shortcut.name}, trying any animated cover")
                fetchAnyAnimatedGridsFallback(context, gameId, shortcut, landscape, api, dimensions, auth, onDone)
            }

            override fun onFailure(call: retrofit2.Call<SteamGridGridsResponse>, t: Throwable) {
                fetchAnyAnimatedGridsFallback(context, gameId, shortcut, landscape, api, dimensions, auth, onDone)
            }
        })
    }

    private fun fetchAnyAnimatedGridsFallback(
        context: Context, gameId: Int, shortcut: Shortcut, landscape: Boolean,
        api: SteamGridDBApi, dimensions: String, auth: String, onDone: () -> Unit
    ) {
        val anyAnimatedCall = api.getAnimatedGridsByGameId(
            auth, gameId, "alternate,blurred,white_logo,material,no_logo", dimensions, "animated", null, "false", "false"
        )
        anyAnimatedCall.enqueue(object : retrofit2.Callback<SteamGridGridsResponse> {
            override fun onResponse(call: retrofit2.Call<SteamGridGridsResponse>, response: retrofit2.Response<SteamGridGridsResponse>) {
                if (response.isSuccessful && !response.body()?.data.isNullOrEmpty()) {
                    val grid = response.body()!!.data[0]
                    if (!grid.url.isNullOrEmpty()) {
                        downloadCoverArt(context, grid.url, shortcut, landscape, true, grid.mime, onDone)
                        return
                    }
                }
                fetchStaticGridsFallback(context, gameId, shortcut, landscape, api, dimensions, auth, onDone)
            }

            override fun onFailure(call: retrofit2.Call<SteamGridGridsResponse>, t: Throwable) {
                fetchStaticGridsFallback(context, gameId, shortcut, landscape, api, dimensions, auth, onDone)
            }
        })
    }

    private fun fetchStaticGridsFallback(
        context: Context, gameId: Int, shortcut: Shortcut, landscape: Boolean,
        api: SteamGridDBApi, dimensions: String, auth: String, onDone: () -> Unit
    ) {
        val gridsCall = api.getGridsByGameId(auth, gameId, "alternate", dimensions, "static")
        gridsCall.enqueue(object : retrofit2.Callback<SteamGridGridsResponse> {
            override fun onResponse(call: retrofit2.Call<SteamGridGridsResponse>, response: retrofit2.Response<SteamGridGridsResponse>) {
                if (!response.isSuccessful || response.body()?.data.isNullOrEmpty()) {
                    Log.w("CoverArt", "SteamGrid grids failed/empty name=${shortcut.name}")
                    STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                    fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
                    return
                }
                val url = response.body()!!.data[0].url
                if (url.isNullOrEmpty()) {
                    STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                    fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
                    return
                }
                downloadCoverArt(context, url, shortcut, landscape, isAnimated = false, mime = null, onDone = onDone)
            }

            override fun onFailure(call: retrofit2.Call<SteamGridGridsResponse>, t: Throwable) {
                Log.w("CoverArt", "SteamGrid grids error name=${shortcut.name}", t)
                STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
            }
        })
    }

    private fun fetchSteamHeaderFallback(context: Context, shortcut: Shortcut, landscape: Boolean, onDone: () -> Unit) {
        COVER_ART_EXECUTOR.execute {
            try {
                val appId = resolveSteamAppIdByStoreSearch(shortcut.name)
                if (appId == null || appId <= 0) {
                    Log.w("CoverArt", "Steam fallback: appid not found name=${shortcut.name}")
                    return@execute
                }

                for (url in buildSteamHeaderUrls(appId, landscape)) {
                    val bmp = downloadBitmap(url) ?: continue
                    cacheCoverArt(context, bmp, shortcut.name, landscape)
                    onDone()
                    return@execute
                }
                Log.w("CoverArt", "Steam fallback: all header urls failed name=${shortcut.name} appid=$appId")
            } catch (e: Exception) {
                Log.w("CoverArt", "Steam fallback failed name=${shortcut.name}", e)
            }
        }
    }

    private fun downloadCoverArt(
        context: Context, url: String, shortcut: Shortcut, landscape: Boolean,
        isAnimated: Boolean = false, mime: String? = null, onDone: () -> Unit
    ) {
        COVER_ART_EXECUTOR.execute {
            try {
                val request = Request.Builder().url(url).build()
                COVER_ART_HTTP.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@execute
                    val body = response.body ?: return@execute
                    val bytes = body.bytes()
                    if (bytes.isEmpty()) return@execute

                    var ext = urlToExtension(url)
                    if (mime != null && mime.contains("webp")) ext = ".webp"
                    val storeAsFile = isAnimated || isAnimatedBytes(bytes, ext)

                    if (storeAsFile) {
                        val cacheDir = File(context.filesDir, "coverArtCache")
                        if (!cacheDir.exists()) cacheDir.mkdirs()
                        val animExt = detectAnimExtension(bytes, url, mime)
                        val animFileName = if (landscape) "${shortcut.name}_l$animExt" else "${shortcut.name}$animExt"
                        val animFile = File(cacheDir, animFileName)
                        FileOutputStream(animFile).use { fos ->
                            fos.write(bytes)
                            fos.flush()
                        }
                        onDone()
                    } else {
                        val coverArt = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (coverArt != null) {
                            cacheCoverArt(context, coverArt, shortcut.name, landscape)
                            onDone()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("CoverArt", "Download failed name=${shortcut.name} url=$url", e)
                if (url.contains("steamgriddb.com")) {
                    STEAMGRID_LAST_FAIL[shortcut.name] = System.currentTimeMillis()
                    fetchSteamHeaderFallback(context, shortcut, landscape, onDone)
                }
            }
        }
    }

    private fun buildSteamCoverUrls(appId: Int, landscape: Boolean): List<String> {
        val urls = ArrayList<String>()
        val primaryHost = "https://shared.steamstatic.com/store_item_assets/steam/apps"

        if (landscape) {
            urls.add("$primaryHost/$appId/library_hero.jpg")
            urls.add("$primaryHost/$appId/header.jpg")
            urls.add("$primaryHost/$appId/capsule_616x353.jpg")
            urls.add("$primaryHost/$appId/capsule_231x87.jpg")

            for (host in STEAM_ASSET_HOSTS) {
                urls.add("https://$host/steam/apps/$appId/library_hero.jpg")
                urls.add("https://$host/steam/apps/$appId/header.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_616x353.jpg")
            }
        } else {
            urls.add("$primaryHost/$appId/library_600x900_2x.jpg")
            urls.add("$primaryHost/$appId/library_600x900.jpg")
            urls.add("$primaryHost/$appId/capsule_616x353.jpg")
            urls.add("$primaryHost/$appId/header.jpg")

            for (host in STEAM_ASSET_HOSTS) {
                urls.add("https://$host/steam/apps/$appId/library_600x900_2x.jpg")
                urls.add("https://$host/steam/apps/$appId/library_600x900.jpg")
                urls.add("https://$host/steam/apps/$appId/header.jpg")
            }
        }
        return urls
    }

    private fun buildSteamHeaderUrls(appId: Int, landscape: Boolean): List<String> {
        val urls = ArrayList<String>()
        for (host in STEAM_ASSET_HOSTS) {
            if (landscape) {
                urls.add("https://$host/steam/apps/$appId/library_hero.jpg")
                urls.add("https://$host/steam/apps/$appId/header.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_616x353.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_231x87.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_184x69.jpg")
            } else {
                urls.add("https://$host/steam/apps/$appId/library_600x900_2x.jpg")
                urls.add("https://$host/steam/apps/$appId/library_600x900.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_616x353.jpg")
                urls.add("https://$host/steam/apps/$appId/capsule_184x69.jpg")
                urls.add("https://$host/steam/apps/$appId/header.jpg")
            }
        }
        return urls
    }

    private fun resolveSteamAppIdByStoreSearch(queryName: String): Int? {
        val q = queryName.trim()
        if (q.length < 3) return null

        val cached = STEAM_APPID_CACHE[queryName]
        if (cached != null && cached > 0) return cached

        try {
            val encoded = URLEncoder.encode(queryName, StandardCharsets.UTF_8.name())
            val url = "https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=us"

            val request = Request.Builder().url(url).build()
            DohOkHttp.get().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: return null
                if (items.length() == 0) return null

                val qNorm = q.lowercase(Locale.US)
                val qTokens = qNorm.split("[^a-z0-9]+".toRegex()).toTypedArray()
                var bestScore = -1
                var bestId = 0
                var bestName = ""

                for (i in 0 until items.length()) {
                    val it = items.optJSONObject(i) ?: continue
                    val name = it.optString("name", "").trim()
                    if (name.isEmpty()) continue
                    val id = it.optInt("id", 0)
                    if (id <= 0) continue

                    val nNorm = name.lowercase(Locale.US)
                    var score = 0

                    if (nNorm == qNorm) {
                        score += 100
                    } else if (nNorm.contains(qNorm) || qNorm.contains(nNorm)) {
                        score += 20
                    }

                    val nTokens = nNorm.split("[^a-z0-9]+".toRegex()).toTypedArray()
                    var matched = 0
                    var qTokenCount = 0

                    for (qt in qTokens) {
                        if (qt.isEmpty()) continue
                        qTokenCount++

                        var found = false
                        for (nt in nTokens) {
                            if (nt.isEmpty()) continue
                            if (nt == qt) {
                                found = true
                                break
                            }
                        }
                        if (found) matched++
                    }

                    if (qTokenCount > 0) {
                        score += matched * 10
                        if (matched == qTokenCount) score += 60
                    }

                    if (qTokenCount == 1 && nNorm != qNorm) {
                        var nTokenCount = 0
                        for (nt in nTokens) {
                            if (nt.isNotEmpty()) nTokenCount++
                        }
                        if (nTokenCount > 1) score -= 50
                    }

                    // Stricter: if no tokens matched AND no substring relation, force zero
                    if (qTokenCount > 0 && matched == 0 && !nNorm.contains(qNorm) && !qNorm.contains(nNorm)) {
                        score = 0
                    } else if (qTokenCount > 0 && matched == 0) {
                        score -= 20
                    }

                    // Short query penalty (< 5 chars): need higher confidence
                    if (qNorm.length < 5) score -= 20

                    if (score > bestScore) {
                        bestScore = score
                        bestId = id
                        bestName = name
                    }
                }

                // Increased threshold from 60 to 75
                if (bestId > 0 && bestScore >= 75) {
                    Log.i("CoverArt", "Steam storesearch match name=$queryName -> appid=$bestId ($bestName) score=$bestScore")
                    STEAM_APPID_CACHE[queryName] = bestId
                    return bestId
                }

                Log.w("CoverArt", "Steam storesearch: no good match name=$queryName bestScore=$bestScore bestName=$bestName")
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun normalizeSteamGridSearchName(name: String): String {
        val normalized = name.trim()
        val lower = normalized.lowercase(Locale.US).replace("[^a-z0-9]+".toRegex(), " ").trim()
        if (lower == "gta 4" || lower == "gta iv") return "Grand Theft Auto IV"
        if (lower == "gta 5" || lower == "gta v") return "Grand Theft Auto V"
        if (lower == "gta sa") return "Grand Theft Auto San Andreas"
        if (lower == "gta vc") return "Grand Theft Auto Vice City"
        if (lower == "gta 3" || lower == "gta iii") return "Grand Theft Auto III"
        if (lower == "gta 6" || lower == "gta vi") return "Grand Theft Auto VI"
        if (lower == "re4" || lower == "re 4") return "Resident Evil 4"
        if (lower == "re5" || lower == "re 5") return "Resident Evil 5"
        if (lower == "re6" || lower == "re 6") return "Resident Evil 6"
        if (lower == "re7" || lower == "re 7") return "Resident Evil 7"
        if (lower == "re8" || lower == "re 8") return "Resident Evil Village"
        if (lower == "re2" || lower == "re 2") return "Resident Evil 2"
        if (lower == "re3" || lower == "re 3") return "Resident Evil 3"
        if (lower == "re1" || lower == "re 1") return "Resident Evil"
        if (lower == "rdr2" || lower == "rdr 2") return "Red Dead Redemption 2"
        if (lower == "rdr" || lower == "rdr1" || lower == "rdr 1") return "Red Dead Redemption"
        if (lower == "doom 2016" || lower == "doom2016" || lower == "doom4" || lower == "doom 4") return "DOOM"
        if (lower == "doom eternal" || lower == "doometernal") return "Doom Eternal"
        if (lower == "witcher3" || lower == "tw3" || lower == "witcher 3") return "The Witcher 3 Wild Hunt"
        if (lower == "witcher2" || lower == "tw2" || lower == "witcher 2") return "The Witcher 2"
        if (lower == "witcher1" || lower == "tw1" || lower == "witcher 1") return "The Witcher"
        if (lower == "kcd" || lower == "kingdom come" || lower == "kingdomcome") return "Kingdom Come Deliverance"
        if (lower == "kcd2" || lower == "kingdom come 2" || lower == "kingdomcome2") return "Kingdom Come Deliverance II"
        if (lower == "cp2077" || lower == "cyberpunk 2077" || lower == "cyberpunk2077") return "Cyberpunk 2077"
        if (lower == "d4" || lower == "diablo 4" || lower == "diablo4") return "Diablo IV"
        if (lower == "d3" || lower == "diablo 3" || lower == "diablo3") return "Diablo III"
        if (lower == "hl2" || lower == "half life 2") return "Half-Life 2"
        if (lower == "hl1" || lower == "half life" || lower == "half life 1") return "Half-Life"
        if (lower == "sms" || lower == "supermarioshine") return "Super Mario Sunshine"
        if (lower == "hades 1" || lower == "hades1") return "Hades"
        return normalized
    }

    /**
     * Вычисляет score совпадения между именем ярлыка и именем результата.
     * Используется для валидации результатов SteamGridDB.
     */
    private fun computeMatchScore(queryName: String, resultName: String): Int {
        val q = queryName.trim().lowercase(Locale.US)
        val n = resultName.trim().lowercase(Locale.US)
        if (q.isEmpty() || n.isEmpty()) return 0

        val qTokens = q.split("[^a-z0-9]+".toRegex()).filter { it.isNotEmpty() }
        val nTokens = n.split("[^a-z0-9]+".toRegex()).filter { it.isNotEmpty() }
        if (qTokens.isEmpty()) return 0

        var score = 0

        // Exact match
        if (n == q) {
            score += 100
        } else if (n.contains(q) || q.contains(n)) {
            score += 20
        }

        // Token matching
        val matched = qTokens.count { qt -> nTokens.any { nt -> nt == qt } }
        score += matched * 10
        if (matched == qTokens.size) score += 60

        // Penalty for single short token overmatch
        if (qTokens.size == 1 && n != q && nTokens.size > 1) {
            score -= if (qTokens[0].length < 4) 60 else 50
        }

        // Strict: no token match + no substring relation = force zero
        if (matched == 0 && !n.contains(q) && !q.contains(n)) {
            score = 0
        } else if (matched == 0) {
            score -= 20
        }

        // Short query penalty
        if (q.length < 5) score -= 20

        return score
    }

    private fun downloadBitmap(url: String): Bitmap? {
        try {
            val request = Request.Builder().url(url).build()
            COVER_ART_HTTP.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val bytes = body.bytes()
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun isAnimatedBytes(bytes: ByteArray, ext: String): Boolean {
        if (".gif".equals(ext, ignoreCase = true)) return true
        if (".png".equals(ext, ignoreCase = true)) {
            if (bytes.size > 8) {
                var offset = 8
                while (offset + 8 <= bytes.size) {
                    val chunkLen = ((bytes[offset].toInt() and 0xFF) shl 24) or
                            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 3].toInt() and 0xFF)
                    val chunkType = String(bytes, offset + 4, 4, StandardCharsets.US_ASCII)
                    if ("acTL" == chunkType) return true
                    if ("IDAT" == chunkType) break
                    offset += 8 + chunkLen + 4
                }
            }
        }
        if (".webp".equals(ext, ignoreCase = true)) {
            if (bytes.size >= 12) {
                val riffSize = ((bytes[7].toInt() and 0xFF) shl 24) or
                        ((bytes[6].toInt() and 0xFF) shl 16) or
                        ((bytes[5].toInt() and 0xFF) shl 8) or
                        (bytes[4].toInt() and 0xFF)
                var offset = 12
                while (offset + 8 <= bytes.size) {
                    val chunk = String(bytes, offset, 4)
                    val chunkSize = ((bytes[offset + 7].toInt() and 0xFF) shl 24) or
                            ((bytes[offset + 6].toInt() and 0xFF) shl 16) or
                            ((bytes[offset + 5].toInt() and 0xFF) shl 8) or
                            (bytes[offset + 4].toInt() and 0xFF)
                    if ("ANIM" == chunk) return true
                    offset += 8 + chunkSize + (chunkSize and 1)
                    if (offset > riffSize + 8) break
                }
            }
        }
        return false
    }

    private fun urlToExtension(url: String): String {
        val path = url.split("[?#]".toRegex()).toTypedArray()[0]
        val dot = path.lastIndexOf('.')
        if (dot in 0 until path.length - 1) {
            val ext = path.substring(dot).lowercase(Locale.US)
            if (ext == ".gif" || ext == ".webp" || ext == ".png" || ext == ".jpg" || ext == ".jpeg") return ext
        }
        return ".png"
    }

    private fun detectAnimExtension(bytes: ByteArray, url: String, mime: String?): String {
        if (mime != null && mime.contains("webp")) return ".webp"
        val urlExt = urlToExtension(url)
        if (".gif" == urlExt) return ".gif"
        if (".webp" == urlExt && isAnimatedBytes(bytes, urlExt)) return ".webp"
        if (".png" == urlExt && isAnimatedBytes(bytes, urlExt)) return ".png"
        return if (isAnimatedBytes(bytes, urlExt)) ".gif" else ".png"
    }

    private fun cacheCoverArt(context: Context, coverArt: Bitmap, shortcutName: String, landscape: Boolean) {
        try {
            val cacheDir = File(context.filesDir, "coverArtCache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val coverFile = File(cacheDir, coverCacheFileName(shortcutName, landscape))
            FileOutputStream(coverFile).use { outputStream ->
                coverArt.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
        } catch (ignored: IOException) {
        }
    }
}
