package com.winlator.cmod.steam.store

// Модели витрины Steam. Источник — публичные endpoints без ключа:
// store.steampowered.com/api/featured, featuredcategories, appdetails,
// storesearch, appreviews + api.steampowered.com/ISteamNews.

data class StorePrice(
    val currency: String = "",
    val initial: Long = 0L, // в копейках/центах
    val final: Long = 0L,
    val discountPercent: Int = 0,
    val isFree: Boolean = false,
) {
    val hasDiscount: Boolean get() = discountPercent > 0 && final < initial
    fun format(value: Long): String {
        if (currency.isBlank()) return "${value / 100}"
        val amount = value / 100.0
        val symbol = when (currency.uppercase()) {
            "RUB" -> "руб."
            "USD" -> "$"
            "EUR" -> "€"
            "KZT" -> "₸"
            "BYN" -> "Br"
            "UAH" -> "₴"
            else -> currency
        }
        return if (amount % 1.0 == 0.0) "${amount.toInt()} $symbol" else "%.2f $symbol".format(amount)
    }
    val finalText: String get() = if (isFree) "Бесплатно" else format(final)
    val initialText: String get() = format(initial)
}

data class StoreApp(
    val id: Int,
    val name: String = "",
    val type: String = "",
    val discounted: Boolean = false,
    val price: StorePrice = StorePrice(),
    val largeCapsuleImage: String = "",
    val smallCapsuleImage: String = "",
    val headerImage: String = "",
    val headline: String = "",
    val controllerSupport: String = "",
    val metascore: Int = 0,
    val platforms: List<String> = emptyList(), // win/mac/linux
    val priceText: String = "", // сырая строка цены из HTML-каталога
) {
    // Заглушка-капсула CDN, если API не отдал картинку
    val capsuleFallback: String get() =
        "https://shared.steamstatic.com/store_item_assets/steam/apps/$id/header.jpg"
    val bestCapsule: String get() =
        largeCapsuleImage.ifBlank { smallCapsuleImage.ifBlank { headerImage.ifBlank { capsuleFallback } } }
    // Короткие метки ОС для карточек
    val platformLabels: String get() = buildList {
        if ("win" in platforms) add("Win")
        if ("mac" in platforms) add("Mac")
        if ("linux" in platforms) add("Linux")
    }.joinToString(" • ")
}

// Лёгкий автокомплит: search/suggest отдаёт HTML в JSON
data class StoreSuggest(
    val id: Int,
    val name: String = "",
    val image: String = "",
    val priceText: String = "",
)

// Страница отзывов с курсором для пагинации
data class ReviewPage(
    val reviews: List<StoreReview> = emptyList(),
    val cursor: String = "",
)

data class StoreCategory(
    val key: String,
    val title: String,
    val items: List<StoreApp> = emptyList(),
)

// Полнотекстовый каталог search/results: запрос + фильтры как в Steam
data class StoreCatalogQuery(
    val term: String = "",
    val tab: String = "", // topsellers|newreleases|specials|comingsoon|пусто
    val onlySpecials: Boolean = false,
    val osWin: Boolean = false,
    val freeOnly: Boolean = false,
    val type: String = "", // ""|game|demo|dlc
    val sort: String = "", // ""|price_ASC|price_DESC|name_ASC|released_DESC|reviews_DESC
    val start: Int = 0,
) {
    companion object {
        const val COUNT = 25
    }
}

data class StoreCatalogPage(
    val items: List<StoreApp> = emptyList(),
    val totalCount: Int = 0,
    val failed: Boolean = false, // сеть/Steam не ответил — отличать от пустого поиска
)

data class StoreHome(
    val hero: List<StoreApp> = emptyList(), // large_capsules
    val categories: List<StoreCategory> = emptyList(),
    val dailyDeal: StoreApp? = null,
)

data class StoreScreenshot(
    val thumbnail: String = "",
    val full: String = "",
)

data class StoreMovie(
    val name: String = "",
    val thumbnail: String = "",
    val mp4Max: String = "",
    val mp4Small: String = "",
)

data class StoreReviewSummary(
    val scoreDesc: String = "",
    val positivePercent: Int = 0,
    val totalReviews: Int = 0,
)

data class StoreReview(
    val author: String = "",
    val playtimeHours: Float = 0f,
    val votedUp: Boolean = true,
    val votesUp: Int = 0,
    val text: String = "",
    val timestamp: Long = 0L,
)

data class StoreEdition(
    val title: String = "",
    val note: String = "",
    val price: StorePrice = StorePrice(),
    val packageId: Int = 0,
)

data class StoreDetail(
    val appId: Int,
    val name: String = "",
    val type: String = "",
    val shortDescription: String = "",
    val aboutText: String = "",
    val headerImage: String = "",
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val categories: List<String> = emptyList(),
    val screenshots: List<StoreScreenshot> = emptyList(),
    val movies: List<StoreMovie> = emptyList(),
    val releaseDate: String = "",
    val comingSoon: Boolean = false,
    val price: StorePrice = StorePrice(),
    val editions: List<StoreEdition> = emptyList(),
    val metacriticScore: Int = 0,
    val metacriticUrl: String = "",
    val minRequirements: String = "",
    val recRequirements: String = "",
    val supportedLanguages: String = "",
    val reviewSummary: StoreReviewSummary = StoreReviewSummary(),
    val reviews: List<StoreReview> = emptyList(),
    val reviewsCursor: String = "",
    val hasDemo: Boolean = false,
    val demoAppId: Int = 0,
    val dlcAppIds: List<Int> = emptyList(),
    val fullController: Boolean = false,
    val hasCloud: Boolean = false,
    val hasAchievements: Boolean = false,
) {
    val storeUrl: String get() = "https://store.steampowered.com/app/$appId"
}

data class NewsItem(
    val gid: String = "",
    val appId: Int = 0,
    val gameName: String = "",
    val title: String = "",
    val url: String = "",
    val author: String = "",
    val contents: String = "",
    val feedLabel: String = "",
    val dateSeconds: Long = 0L,
) {
    // Короткий текст без HTML для превью
    val plainPreview: String get() {
        var s = contents
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\[/?[^\\]]*\\]"), " ")
            .replace("&nbsp;", " ").replace("&amp;", "&")
            .replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("\\s+"), " ").trim()
        if (s.length > 220) s = s.take(220).trimEnd() + "…"
        return s
    }
}

// Чистка HTML описаний appdetails для показа в Compose
fun stripStoreHtml(html: String): String {
    return html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n\n")
        .replace(Regex("(?i)</(li|h[1-6]|div)>"), "\n")
        .replace(Regex("(?i)<li[^>]*>"), "• ")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\n{3,}"), "\n\n")
        .replace(Regex("[ \t]+"), " ")
        .trim()
}
