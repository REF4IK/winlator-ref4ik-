package com.winlator.cmod.steam.store

import android.content.Context
import com.winlator.cmod.steam.utils.Net
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

// Клиент публичной витрины Steam. Ключ не нужен.
// Endpoints: featured, featuredcategories, appdetails, storesearch,
// appreviews, packagedetails. Локаль ru с fallback на us/english.
object SteamStoreApi {
    private const val STORE = "https://store.steampowered.com/api"
    private const val REVIEWS = "https://store.steampowered.com/appreviews"

    private fun cacheDir(context: Context): File {
        val dir = File(context.filesDir, "steam_store_cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun readCache(context: Context, key: String, maxAgeMs: Long): String? {
        return try {
            val f = File(cacheDir(context), "$key.json")
            if (!f.exists()) return null
            if (System.currentTimeMillis() - f.lastModified() > maxAgeMs) return null
            f.readText().takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private fun writeCache(context: Context, key: String, body: String) {
        try {
            File(cacheDir(context), "$key.json").writeText(body)
        } catch (_: Exception) { }
    }

    private suspend fun get(context: Context, url: String, cacheKey: String?, maxAgeMs: Long): String? =
        withContext(Dispatchers.IO) {
            cacheKey?.let { readCache(context, it, maxAgeMs) }?.let { return@withContext it }
            try {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "WinlatorCMOD/1.0")
                    .build()
                Net.http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext cacheKey?.let { readCache(context, it, Long.MAX_VALUE) }
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@withContext cacheKey?.let { readCache(context, it, Long.MAX_VALUE) }
                    cacheKey?.let { writeCache(context, it, body) }
                    body
                }
            } catch (_: Exception) {
                cacheKey?.let { readCache(context, it, Long.MAX_VALUE) }
            }
        }

    private fun parsePrice(obj: JSONObject?): StorePrice {
        if (obj == null) return StorePrice(isFree = true)
        val currency = obj.optString("currency", "")
        val initial = obj.optLong("initial", 0L)
        val final = obj.optLong("final", initial)
        val discount = obj.optInt("discount_percent", 0)
        if (initial <= 0 && final <= 0) return StorePrice(currency = currency, isFree = true)
        return StorePrice(currency, initial, final, discount)
    }

    private fun parseStoreApp(obj: JSONObject): StoreApp? {
        val id = obj.optInt("id", 0)
        if (id <= 0) return null
        val price = if (obj.has("price_overview")) parsePrice(obj.optJSONObject("price_overview"))
        else {
            val initial = obj.optLong("original_price", -1L)
            val final = obj.optLong("final_price", -1L)
            if (initial < 0 && final < 0) StorePrice(isFree = obj.optBoolean("discounted", false).not() && obj.optInt("discount_percent", 0) == 0)
            else StorePrice(
                currency = obj.optString("currency", ""),
                initial = if (initial >= 0) initial else final,
                final = if (final >= 0) final else initial,
                discountPercent = obj.optInt("discount_percent", obj.optInt("discounted_percent", 0)),
            )
        }
        return StoreApp(
            id = id,
            name = obj.optString("name", ""),
            type = obj.optString("type", ""),
            discounted = obj.optBoolean("discounted", price.hasDiscount),
            price = price,
            largeCapsuleImage = obj.optString("large_capsule_image", ""),
            smallCapsuleImage = obj.optString("small_capsule_image", ""),
            headerImage = obj.optString("header_image", ""),
            headline = obj.optString("headline", ""),
            controllerSupport = obj.optString("controller_support", ""),
        )
    }

    suspend fun loadHome(context: Context, cc: String = "ru", lang: String = "russian"): StoreHome =
        withContext(Dispatchers.IO) {
            var hero: List<StoreApp> = emptyList()
            val cats = mutableListOf<StoreCategory>()
            var daily: StoreApp? = null
            // featured — hero-карусель
            val featBody = get(context, "$STORE/featured/?cc=$cc&l=$lang", "featured_$cc", 30 * 60 * 1000L)
            if (featBody == null) {
                // fallback на английский регион
                val fb = get(context, "$STORE/featured/?cc=us&l=english", "featured_us", 30 * 60 * 1000L)
                fb?.let { hero = parseFeatured(it) }
            } else hero = parseFeatured(featBody)
            // featuredcategories — полки
            val catBody = get(context, "$STORE/featuredcategories/?cc=$cc&l=$lang", "featcats_$cc", 30 * 60 * 1000L)
                ?: get(context, "$STORE/featuredcategories/?cc=us&l=english", "featcats_us", 30 * 60 * 1000L)
            catBody?.let { parseCategories(it, cats) { daily = it } }
            StoreHome(hero = hero, categories = cats, dailyDeal = daily)
        }

    private fun parseFeatured(body: String): List<StoreApp> {
        val out = mutableListOf<StoreApp>()
        try {
            val root = JSONObject(body)
            // hero: large_capsules + featured_win + featured_mac — иначе витрина полупустая
            val keys = listOf("large_capsules", "featured_win", "featured_mac", "featured_linux")
            for (k in keys) {
                val arr = root.optJSONArray(k) ?: continue
                for (i in 0 until arr.length()) {
                    parseStoreApp(arr.optJSONObject(i) ?: continue)?.let { out.add(it) }
                }
            }
        } catch (_: Exception) { }
        return out.distinctBy { it.id }
    }

    private fun parseCategories(body: String, out: MutableList<StoreCategory>, onDaily: (StoreApp) -> Unit) {
        try {
            val root = JSONObject(body)
            fun shelf(key: String, title: String) {
                val obj = root.optJSONObject(key) ?: return
                val items = obj.optJSONArray("items") ?: return
                val list = mutableListOf<StoreApp>()
                for (i in 0 until items.length()) {
                    parseStoreApp(items.optJSONObject(i) ?: continue)?.let { list.add(it) }
                }
                if (list.isNotEmpty()) out.add(StoreCategory(key, title.ifBlank { obj.optString("name", key) }, list.distinctBy { it.id }))
            }
            shelf("top_sellers", "Лидеры продаж")
            shelf("specials", "Скидки")
            shelf("new_releases", "Новинки")
            shelf("coming_soon", "Скоро выйдут")
            shelf("free", "Бесплатные")
            shelf("most_played", "Самые играемые")
            shelf("concurrent_users", "Сейчас в игре")
            // запасные полки из layout/раскладки, если основные пустые
            try {
                val layout = root.optString("layout", "")
                if (layout.isNotBlank()) {
                    val arr = root.optJSONArray("layout") ?: root.optJSONArray("items")
                    if (arr != null) {
                        val list = mutableListOf<StoreApp>()
                        for (i in 0 until arr.length()) {
                            parseStoreApp(arr.optJSONObject(i) ?: continue)?.let { list.add(it) }
                        }
                        if (list.isNotEmpty()) out.add(StoreCategory("layout", "Рекомендуемое", list.distinctBy { it.id }))
                    }
                }
            } catch (_: Exception) { }
            // ежедневное предложение — отдельная структура
            val daily = root.optJSONObject("cat_dailydeal")
            daily?.let {
                parseStoreApp(it)?.let { app -> onDaily(app) }
            }
            // жанры — только заголовки для чипсов
            val genres = root.optJSONObject("genres")
            if (genres != null) {
                val names = mutableListOf<String>()
                val keys = genres.keys()
                while (keys.hasNext()) {
                    val g = genres.optJSONObject(keys.next()) ?: continue
                    g.optString("name", "").takeIf { it.isNotBlank() }?.let { names.add(it) }
                }
                if (names.isNotEmpty()) out.add(StoreCategory("genres", "Жанры", names.take(12).mapIndexed { i, n ->
                    StoreApp(id = -(i + 1), name = n)
                }))
            }
        } catch (_: Exception) { }
    }

    suspend fun search(context: Context, term: String, cc: String = "ru", lang: String = "russian"): List<StoreApp> =
        withContext(Dispatchers.IO) {
            val q = term.trim().takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
            val enc = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
            val body = get(context, "$STORE/storesearch/?term=$enc&l=$lang&cc=$cc", null, 0L)
                ?: get(context, "$STORE/storesearch/?term=$enc&l=english&cc=us", null, 0L)
                ?: return@withContext emptyList()
            val out = mutableListOf<StoreApp>()
            try {
                val items = JSONObject(body).optJSONArray("items") ?: return@withContext out
                for (i in 0 until items.length()) {
                    val o = items.optJSONObject(i) ?: continue
                    val id = o.optInt("id", 0)
                    if (id <= 0) continue
                    // Цена: объект price {currency,initial,final} + discount_percent рядом
                    val priceObj = o.optJSONObject("price")
                    val initial = priceObj?.optLong("initial", 0L) ?: 0L
                    val final = priceObj?.optLong("final", initial) ?: initial
                    val discount = o.optInt("discount_percent", o.optInt("discounted_percent",
                        priceObj?.optInt("discount_percent", 0) ?: 0))
                    val currency = priceObj?.optString("currency", "").orEmpty()
                        .ifBlank { o.optString("currency", "") }
                    val plats = mutableListOf<String>()
                    o.optJSONObject("platforms")?.let { p ->
                        if (p.optBoolean("windows", false)) plats.add("win")
                        if (p.optBoolean("mac", false)) plats.add("mac")
                        if (p.optBoolean("linux", false)) plats.add("linux")
                    }
                    out.add(StoreApp(
                        id = id,
                        name = o.optString("name", ""),
                        type = o.optString("type", ""),
                        discounted = discount > 0 || o.optBoolean("discounted", false),
                        price = if (initial <= 0 && final <= 0)
                            StorePrice(currency = currency, isFree = true)
                        else StorePrice(currency, initial, final, discount),
                        smallCapsuleImage = o.optString("tiny_image", ""),
                        headerImage = o.optString("small_capsule", o.optString("tiny_image", "")),
                        metascore = o.optInt("metascore", 0),
                        platforms = plats,
                        controllerSupport = o.optString("controller_support", ""),
                    ))
                }
            } catch (_: Exception) { }
            out.distinctBy { it.id }
        }

    // Лёгкий автокомплит: search/suggest отдаёт JSON с HTML-куском results_html.
    // Без кэша, вызывается с debounce из ViewModel.
    suspend fun suggest(context: Context, term: String, cc: String = "ru", lang: String = "russian"): List<StoreSuggest> =
        withContext(Dispatchers.IO) {
            val q = term.trim().takeIf { it.length >= 2 } ?: return@withContext emptyList()
            val enc = URLEncoder.encode(q, StandardCharsets.UTF_8.name())
            val body = get(context, "https://store.steampowered.com/search/suggest?term=$enc&f=games&cc=$cc&l=$lang", null, 0L)
                ?: get(context, "https://store.steampowered.com/search/suggest?term=$enc&f=games&cc=us&l=english", null, 0L)
                ?: return@withContext emptyList()
            try {
                val html = JSONObject(body).optString("results_html", "")
                if (html.isBlank()) return@withContext emptyList()
                parseSuggestHtml(html)
            } catch (_: Exception) { emptyList() }
        }

    private fun parseSuggestHtml(html: String): List<StoreSuggest> {
        val out = mutableListOf<StoreSuggest>()
        try {
            // Режем по якорям <a ... data-ds-appid="123" ...>...</a>
            val anchorRe = Regex("<a\\b[^>]*data-ds-appid=\"(\\d+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            for (m in anchorRe.findAll(html)) {
                val id = m.groupValues[1].toIntOrNull() ?: continue
                if (id <= 0 || out.any { it.id == id }) continue
                val inner = m.groupValues[2]
                fun div(cls: String): String {
                    val r = Regex("<div\\b[^>]*class=\"$cls\"[^>]*>(.*?)</div>", RegexOption.DOT_MATCHES_ALL)
                        .find(inner)?.groupValues?.get(1).orEmpty()
                    return unescapeSuggest(r.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim())
                }
                val img = Regex("<img\\b[^>]*src=\"([^\"]+)\"").find(inner)?.groupValues?.get(1).orEmpty()
                val name = div("match_name")
                if (name.isBlank()) continue
                out.add(StoreSuggest(id = id, name = name, image = img, priceText = div("match_price")))
                if (out.size >= 8) break
            }
        } catch (_: Exception) { }
        return out
    }

    private fun unescapeSuggest(s: String): String =
        s.replace("&#039;", "'").replace("&#39;", "'").replace("&quot;", "\"")
            .replace("&amp;", "&").replace("&nbsp;", " ").trim()

    suspend fun loadDetail(context: Context, appId: Int, lang: String = "russian", cc: String = "ru"): StoreDetail? =
        withContext(Dispatchers.IO) {
            val body = get(context, "$STORE/appdetails?appids=$appId&l=$lang&cc=$cc", "app_$appId", 6 * 60 * 60 * 1000L)
                ?: get(context, "$STORE/appdetails?appids=$appId&l=english&cc=us", "app_${appId}_en", 6 * 60 * 60 * 1000L)
                ?: return@withContext null
            try {
                val appObj = JSONObject(body).optJSONObject(appId.toString()) ?: return@withContext null
                if (!appObj.optBoolean("success", false)) return@withContext null
                val d = appObj.getJSONObject("data")
                val shots = mutableListOf<StoreScreenshot>()
                d.optJSONArray("screenshots")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        shots.add(StoreScreenshot(o.optString("path_thumbnail", ""), o.optString("path_full", "")))
                    }
                }
                val movies = mutableListOf<StoreMovie>()
                d.optJSONArray("movies")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val mp4 = o.optJSONObject("mp4")
                        movies.add(StoreMovie(o.optString("name", ""), o.optString("thumbnail", ""),
                            mp4?.optString("max", "").orEmpty(), mp4?.optString("480", "").orEmpty()))
                    }
                }
                val genres = mutableListOf<String>()
                d.optJSONArray("genres")?.let { arr ->
                    for (i in 0 until arr.length()) genres.add(arr.optJSONObject(i)?.optString("description", "").orEmpty())
                }
                val cats = mutableListOf<String>()
                var fullCtrl = false; var cloud = false; var ach = false; var demo = false
                d.optJSONArray("categories")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val desc = o.optString("description", "")
                        if (desc.isNotBlank()) cats.add(desc)
                        when (o.optInt("id", 0)) {
                            28 -> fullCtrl = true
                            23 -> cloud = true
                            22 -> ach = true
                        }
                    }
                }
                d.optJSONArray("demos")?.let { if (it.length() > 0) demo = true }
                val pcReq = d.optJSONObject("pc_requirements")
                val meta = d.optJSONObject("metacritic")
                val rel = d.optJSONObject("release_date")
                val devs = mutableListOf<String>()
                d.optJSONArray("developers")?.let { for (i in 0 until it.length()) devs.add(it.optString(i)) }
                val pubs = mutableListOf<String>()
                d.optJSONArray("publishers")?.let { for (i in 0 until it.length()) pubs.add(it.optString(i)) }
                val editions = mutableListOf<StoreEdition>()
                d.optJSONArray("package_groups")?.let { groups ->
                    for (gi in 0 until groups.length()) {
                        val g = groups.optJSONObject(gi) ?: continue
                        g.optJSONArray("subs")?.let { subs ->
                            for (si in 0 until subs.length()) {
                                val s = subs.optJSONObject(si) ?: continue
                                val edFinal = s.optLong("price_in_cents_with_discount", -1L)
                                val edInitial = s.optLong("price_in_cents", if (edFinal >= 0) edFinal else 0L)
                                val edCurrency = d.optJSONObject("price_overview")?.optString("currency", "").orEmpty()
                                editions.add(StoreEdition(
                                    title = s.optString("option_text", g.optString("title", "")),
                                    note = s.optString("option_description", ""),
                                    price = if (edInitial <= 0 && (if (edFinal >= 0) edFinal else 0L) <= 0)
                                        StorePrice(currency = edCurrency)
                                    else StorePrice(
                                        currency = edCurrency,
                                        initial = edInitial,
                                        final = if (edFinal >= 0) edFinal else edInitial,
                                    ),
                                    packageId = s.optInt("packageid", 0),
                                ))
                            }
                        }
                    }
                }
                // DLC и демо-id из тех же данных
                val dlcIds = mutableListOf<Int>()
                d.optJSONArray("dlc")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val id = arr.optInt(i, 0)
                        if (id > 0) dlcIds.add(id)
                    }
                }
                var demoAppId = 0
                d.optJSONArray("demos")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val id = arr.optJSONObject(i)?.optInt("appid", 0) ?: 0
                        if (id > 0) { demoAppId = id; break }
                    }
                }
                // отзывы: сводка + первая страница
                val summary = loadReviewSummary(context, appId)
                val firstPage = loadReviewPage(context, appId)
                val reviews = firstPage.reviews
                StoreDetail(
                    appId = appId,
                    name = d.optString("name", ""),
                    type = d.optString("type", ""),
                    shortDescription = stripStoreHtml(d.optString("short_description", "")),
                    aboutText = stripStoreHtml(d.optString("about_the_game", d.optString("detailed_description", ""))),
                    headerImage = d.optString("header_image", ""),
                    developers = devs, publishers = pubs,
                    genres = genres.filter { it.isNotBlank() },
                    categories = cats,
                    screenshots = shots, movies = movies,
                    releaseDate = rel?.optString("date", "").orEmpty(),
                    comingSoon = rel?.optBoolean("coming_soon", false) == true,
                    price = parsePrice(d.optJSONObject("price_overview")),
                    editions = editions,
                    metacriticScore = meta?.optInt("score", 0) ?: 0,
                    metacriticUrl = meta?.optString("url", "").orEmpty(),
                    minRequirements = stripStoreHtml(pcReq?.optString("minimum", "").orEmpty()),
                    recRequirements = stripStoreHtml(pcReq?.optString("recommended", "").orEmpty()),
                    supportedLanguages = d.optString("supported_languages", "").let { stripStoreHtml(it) },
                    reviewSummary = summary,
                    reviews = reviews,
                    reviewsCursor = firstPage.cursor,
                    hasDemo = demo || demoAppId > 0, demoAppId = demoAppId,
                    dlcAppIds = dlcIds.distinct(),
                    fullController = fullCtrl, hasCloud = cloud, hasAchievements = ach,
                )
            } catch (_: Exception) { null }
        }

    private suspend fun loadReviewSummary(context: Context, appId: Int): StoreReviewSummary {
        val body = get(context, "$REVIEWS/$appId?json=1&language=all&purchase_type=all&num_per_page=0", "revsum_$appId", 6 * 60 * 60 * 1000L)
            ?: return StoreReviewSummary()
        return try {
            val s = JSONObject(body).optJSONObject("query_summary") ?: return StoreReviewSummary()
            val total = s.optInt("total_reviews", 0)
            val pos = s.optInt("total_positive", 0)
            StoreReviewSummary(
                scoreDesc = s.optString("review_score_desc", ""),
                positivePercent = if (total > 0) (pos * 100 / total) else 0,
                totalReviews = total,
            )
        } catch (_: Exception) { StoreReviewSummary() }
    }

    // Отзывы с курсорной пагинацией и фильтром типа.
    // reviewType: all / positive / negative. cursor: "" первая страница, иначе из прошлой.
    suspend fun loadReviewPage(
        context: Context, appId: Int, count: Int = 10,
        cursor: String = "", reviewType: String = "all",
    ): ReviewPage {
        val encCursor = URLEncoder.encode(cursor, StandardCharsets.UTF_8.name())
        val cursorParam = if (cursor.isBlank()) "" else "&cursor=$encCursor"
        val typeParam = if (reviewType == "positive" || reviewType == "negative") "&review_type=$reviewType" else ""
        val urlRu = "$REVIEWS/$appId?json=1&language=russian&purchase_type=all&num_per_page=$count$typeParam$cursorParam"
        val urlAll = "$REVIEWS/$appId?json=1&language=all&purchase_type=all&num_per_page=$count$typeParam$cursorParam"
        val body = get(context, urlRu, null, 0L) ?: get(context, urlAll, null, 0L) ?: return ReviewPage()
        val out = mutableListOf<StoreReview>()
        var next = ""
        try {
            val root = JSONObject(body)
            next = root.optString("cursor", "")
            val arr = root.optJSONArray("reviews") ?: return ReviewPage(out, next)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val author = o.optJSONObject("author")
                out.add(StoreReview(
                    author = author?.optString("steamid", "").orEmpty(),
                    playtimeHours = (author?.optLong("playtime_forever", 0L) ?: 0L) / 60f,
                    votedUp = o.optBoolean("voted_up", true),
                    votesUp = o.optInt("votes_up", 0),
                    text = o.optString("review", "").take(4000),
                    timestamp = o.optLong("timestamp_created", 0L),
                ))
            }
        } catch (_: Exception) { }
        return ReviewPage(out.distinctBy { it.text + it.timestamp }, next)
    }

    // Совместимость: первая страница без курсора (с кэшем).
    suspend fun loadReviews(context: Context, appId: Int, count: Int = 6): List<StoreReview> {
        val body = get(context, "$REVIEWS/$appId?json=1&language=russian&purchase_type=all&num_per_page=$count", "revs_${appId}", 60 * 60 * 1000L)
            ?: get(context, "$REVIEWS/$appId?json=1&language=all&purchase_type=all&num_per_page=$count", "revs_${appId}_all", 60 * 60 * 1000L)
            ?: return emptyList()
        val out = mutableListOf<StoreReview>()
        try {
            val arr = JSONObject(body).optJSONArray("reviews") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val author = o.optJSONObject("author")
                out.add(StoreReview(
                    author = author?.optString("steamid", "").orEmpty(),
                    playtimeHours = (author?.optLong("playtime_forever", 0L) ?: 0L) / 60f,
                    votedUp = o.optBoolean("voted_up", true),
                    votesUp = o.optInt("votes_up", 0),
                    text = o.optString("review", "").take(1200),
                    timestamp = o.optLong("timestamp_created", 0L),
                ))
            }
        } catch (_: Exception) { }
        return out.distinctBy { it.text + it.timestamp }
    }

        // Догрузка header_image для игр без PICS-арта: лёгкий батч
    // appdetails&filters= с pacing между чанками, файловый кэш 24ч.
    // Возвращает только найденные (id -> абсолютный URL).
    suspend fun loadArtHeaders(context: Context, ids: List<Int>): Map<Int, String> =
        withContext(Dispatchers.IO) {
            val out = mutableMapOf<Int, String>()
            val list = ids.distinct().filter { it > 0 }.take(60)
            if (list.isEmpty()) return@withContext out
            var first = true
            for (chunk in list.chunked(10)) {
                if (!first) kotlinx.coroutines.delay(1500L) // pacing против 429
                first = false
                val key = "hdr_" + chunk.sorted().joinToString("_")
                val body = get(
                    context,
                    "$STORE/appdetails?appids=${chunk.joinToString(",")}&filters=basic,header_image&l=english&cc=us",
                    key, 24 * 60 * 60 * 1000L,
                ) ?: continue
                try {
                    val root = JSONObject(body)
                    for (id in chunk) {
                        val appObj = root.optJSONObject(id.toString()) ?: continue
                        if (!appObj.optBoolean("success", false)) continue
                        val url = appObj.optJSONObject("data")?.optString("header_image", "").orEmpty()
                        if (url.isNotBlank()) out[id] = url
                    }
                } catch (_: Exception) { }
            }
            out
        }

    // Полнотекстовый каталог как в Steam: search/results отдаёт JSON с HTML.
    // Фильтры: таб, скидки, только Win, бесплатно, тип, сортировка. Пагинация через start.
    suspend fun searchCatalog(
        context: Context, q: StoreCatalogQuery,
        cc: String = "ru", lang: String = "russian",
    ): StoreCatalogPage = withContext(Dispatchers.IO) {
        try {
            val enc = URLEncoder.encode(q.term.trim(), StandardCharsets.UTF_8.name())
            val sb = StringBuilder("https://store.steampowered.com/search/results/?query&start=${q.start}")
                .append("&count=${StoreCatalogQuery.COUNT}&dynamic_data=&sort_by=${q.sort}&snr=&infinite=1")
            if (q.term.isNotBlank()) sb.append("&term=$enc")
            if (q.tab.isNotBlank()) sb.append("&filter=${q.tab}")
            if (q.onlySpecials) sb.append("&specials=1")
            if (q.osWin) sb.append("&os=win")
            if (q.freeOnly) sb.append("&maxprice=free")
            when (q.type) {
                "game" -> sb.append("&category1=998")
                "demo" -> sb.append("&category1=10")
                "dlc" -> sb.append("&category1=21")
            }
            sb.append("&cc=$cc&l=$lang&hide_adult_content_violations=1")
            val body = get(context, sb.toString(), null, 0L) ?: return@withContext StoreCatalogPage(failed = true)
            val root = JSONObject(body)
            // Steam отдаёт success числом 1, а не boolean — принимаем оба
            val ok = root.optBoolean("success", false) || root.optInt("success", 0) == 1
            if (!ok) return@withContext StoreCatalogPage(failed = true)
            val total = root.optInt("total_count", 0)
            val html = root.optString("results_html", "")
            if (html.isBlank()) return@withContext StoreCatalogPage(emptyList(), total)
            val currency = if (cc.equals("ru", true)) "RUB" else if (cc.equals("us", true)) "USD" else cc.uppercase()
            StoreCatalogPage(parseCatalogHtml(html, currency), total)
        } catch (_: Exception) { StoreCatalogPage() }
    }

    private fun parseCatalogHtml(html: String, currency: String): List<StoreApp> {
        val out = mutableListOf<StoreApp>()
        try {
            val doc = org.jsoup.Jsoup.parseBodyFragment(html)
            for (a in doc.select("a.search_result_row")) {
                val id = a.attr("data-ds-appid").toIntOrNull() ?: continue
                if (id <= 0 || out.any { it.id == id }) continue
                val name = a.selectFirst(".title")?.text().orEmpty().trim()
                if (name.isBlank()) continue
                val img = a.selectFirst("img")?.attr("src").orEmpty()
                val pctRaw = a.selectFirst(".discount_pct")?.text().orEmpty()
                val discount = Regex("-(\\d+)%").find(pctRaw)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val finalRaw = a.selectFirst(".discount_final_price")?.text().orEmpty()
                    .ifBlank { a.selectFirst(".search_price")?.text().orEmpty() }
                val (final, free) = parseCatalogPrice(finalRaw)
                out.add(StoreApp(
                    id = id,
                    name = name,
                    discounted = discount > 0,
                    price = if (free) StorePrice(currency = currency, isFree = true)
                    else StorePrice(currency = currency, initial = final, final = final, discountPercent = discount),
                    smallCapsuleImage = img,
                    headerImage = img,
                    priceText = finalRaw.trim(),
                ))
            }
        } catch (_: Exception) { }
        return out
    }

    private fun parseCatalogPrice(raw: String): Pair<Long, Boolean> {
        val s = raw.trim()
        if (s.isEmpty()) return 0L to false
        val low = s.lowercase()
        if (low.contains("free") || low.contains("бесплатно") || low.contains("играть")) return 0L to true
        // "1 299 руб." / "$19.99" / "19,99€" → копейки
        val num = Regex("[\\d\\s.,]+").find(s)?.value?.replace(Regex("\\s+"), "") ?: return 0L to false
        return try {
            val cents = if (num.contains('.') || num.contains(',')) {
                val norm = num.replace(',', '.')
                // "1.299" (тысячи) vs "19.99" (копейки): две цифры после точки = копейки
                val frac = norm.substringAfter('.', "")
                if (frac.length == 2) (norm.toDouble() * 100).toLong()
                else norm.replace(".", "").toDouble().toLong() * 100
            } else {
                num.toDouble().toLong() * 100
            }
            cents to false
        } catch (_: Exception) { 0L to false }
    }
    // Лёгкий батч для рядов DLC: имя+картинка+цена по списку id.
    suspend fun loadStoreApps(context: Context, ids: List<Int>, cc: String = "ru", lang: String = "russian"): List<StoreApp> =
        withContext(Dispatchers.IO) {
            val list = ids.distinct().filter { it > 0 }.take(30)
            if (list.isEmpty()) return@withContext emptyList()
            val out = mutableListOf<StoreApp>()
            // appdetails тянет батчи — режем по 10 чтобы не упереться в лимит
            for (chunk in list.chunked(10)) {
                val body = get(context, "$STORE/appdetails?appids=${chunk.joinToString(",")}" +
                    "&filters=name,type,header_image,price_overview&l=$lang&cc=$cc", null, 0L)
                    ?: continue
                try {
                    val root = JSONObject(body)
                    for (id in chunk) {
                        val appObj = root.optJSONObject(id.toString()) ?: continue
                        if (!appObj.optBoolean("success", false)) continue
                        val d = appObj.optJSONObject("data") ?: continue
                        out.add(StoreApp(
                            id = id,
                            name = d.optString("name", ""),
                            type = d.optString("type", ""),
                            price = parsePrice(d.optJSONObject("price_overview")),
                            headerImage = d.optString("header_image", ""),
                        ))
                    }
                } catch (_: Exception) { }
            }
            out
        }

    // Новости по игре. Без ключа: ISteamNews/GetNewsForApp.
    suspend fun loadNewsForApp(context: Context, appId: Int, count: Int = 8, maxLen: Int = 600): List<NewsItem> {
        val url = "https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=$appId&count=$count&maxlength=$maxLen&format=json"
        val body = get(context, url, "news_$appId", 60 * 60 * 1000L) ?: return emptyList()
        val out = mutableListOf<NewsItem>()
        try {
            val items = JSONObject(body).optJSONObject("appnews")?.optJSONArray("newsitems") ?: return out
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                out.add(NewsItem(
                    gid = o.optString("gid", ""),
                    appId = appId,
                    title = o.optString("title", ""),
                    url = o.optString("url", ""),
                    author = o.optString("author", ""),
                    contents = o.optString("contents", ""),
                    feedLabel = o.optString("feedlabel", ""),
                    dateSeconds = o.optLong("date", 0L),
                ))
            }
        } catch (_: Exception) { }
        return out
    }

    suspend fun loadNewsForApps(context: Context, appIds: List<Int>, perApp: Int = 3): List<NewsItem> {
        val all = mutableListOf<NewsItem>()
        for (id in appIds.distinct().take(12)) {
            val items = loadNewsForApp(context, id, perApp)
            all.addAll(items)
        }
        return all.distinctBy { it.gid.ifBlank { it.title + it.dateSeconds } }.sortedByDescending { it.dateSeconds }
    }
}
