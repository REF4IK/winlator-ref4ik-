package com.winlator.cmod.steam.store

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winlator.cmod.PluviaApp
import com.winlator.cmod.steam.utils.PrefManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Состояние витрины: дом, поиск, деталь, новости, вишлист.
data class StoreUiState(
    val homeLoading: Boolean = true,
    val home: StoreHome = StoreHome(),
    val homeError: String? = null,
    val searchQuery: String = "",
    val searchLoading: Boolean = false,
    val searchResults: List<StoreApp> = emptyList(),
    val suggest: List<StoreSuggest> = emptyList(),
    val catalogOpen: Boolean = false,
    val catalogFiltersExpanded: Boolean = false,
    val catalogQuery: StoreCatalogQuery = StoreCatalogQuery(),
    val catalogItems: List<StoreApp> = emptyList(),
    val catalogTotal: Int = 0,
    val catalogLoading: Boolean = false,
    val catalogLoadingMore: Boolean = false,
    val catalogError: Boolean = false,
    val similarApps: List<StoreApp> = emptyList(),
    val similarLoading: Boolean = false,
    val similarForAppId: Int = 0,
    val detailLoading: Boolean = false,
    val detail: StoreDetail? = null,
    val detailError: String? = null,
    val reviewType: String = "all",
    val reviewsLoadingMore: Boolean = false,
    val dlcApps: List<StoreApp> = emptyList(),
    val dlcLoading: Boolean = false,
    val newsLoading: Boolean = false,
    val news: List<NewsItem> = emptyList(),
    val wishlist: Set<Int> = emptySet(),
)

class SteamStoreViewModel : ViewModel() {
    private val appContext = PluviaApp.instance.applicationContext
    private val _ui = MutableStateFlow(StoreUiState(wishlist = loadWishlist()))
    val ui: StateFlow<StoreUiState> = _ui.asStateFlow()
    private var suggestJob: kotlinx.coroutines.Job? = null

    init { refreshHome() }

    fun refreshHome() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(homeLoading = true, homeError = null) }
            runCatching { SteamStoreApi.loadHome(appContext) }
                .onSuccess { home -> _ui.update { it.copy(homeLoading = false, home = home) } }
                .onFailure { e -> _ui.update { it.copy(homeLoading = false, homeError = e.localizedMessage) } }
        }
    }

    fun setSearchQuery(q: String) {
        _ui.update { it.copy(searchQuery = q) }
        if (q.isBlank()) {
            suggestJob?.cancel()
            _ui.update { it.copy(searchResults = emptyList(), searchLoading = false, suggest = emptyList()) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(searchLoading = true) }
            val res = runCatching { SteamStoreApi.search(appContext, q) }.getOrDefault(emptyList())
            // не затираем более свежий запрос
            if (q == _ui.value.searchQuery) {
                _ui.update { it.copy(searchLoading = false, searchResults = res) }
            }
        }
        // Лёгкий suggest с debounce — дропдаун под полем ввода
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300L)
            if (q != _ui.value.searchQuery) return@launch
            val s = runCatching { SteamStoreApi.suggest(appContext, q) }.getOrDefault(emptyList())
            if (q == _ui.value.searchQuery) {
                _ui.update { it.copy(suggest = s) }
            }
        }
    }

    fun clearSuggest() { _ui.update { it.copy(suggest = emptyList()) } }

    // Полнотекстовый каталог с фильтрами
    fun openCatalog(query: StoreCatalogQuery = StoreCatalogQuery(), expandFilters: Boolean = false) {
        _ui.update { it.copy(catalogOpen = true, catalogFiltersExpanded = expandFilters, catalogQuery = query.copy(start = 0), catalogItems = emptyList(), catalogTotal = 0) }
        runCatalog()
    }

    fun closeCatalog() { _ui.update { it.copy(catalogOpen = false) } }

    fun setCatalogQuery(query: StoreCatalogQuery) {
        _ui.update { it.copy(catalogQuery = query.copy(start = 0), catalogItems = emptyList(), catalogTotal = 0) }
        runCatalog()
    }

    fun runCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(catalogLoading = true, catalogError = false) }
            val q = _ui.value.catalogQuery
            val page = runCatching { SteamStoreApi.searchCatalog(appContext, q) }.getOrDefault(StoreCatalogPage(failed = true))
            if (q == _ui.value.catalogQuery) {
                _ui.update { it.copy(catalogLoading = false, catalogItems = page.items, catalogTotal = page.totalCount, catalogError = page.failed) }
            } else _ui.update { it.copy(catalogLoading = false) }
        }
    }

    fun loadMoreCatalog() {
        val st = _ui.value
        if (st.catalogLoading || st.catalogLoadingMore) return
        if (st.catalogItems.size >= st.catalogTotal && st.catalogTotal > 0) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(catalogLoadingMore = true) }
            val q = _ui.value.catalogQuery.copy(start = _ui.value.catalogItems.size)
            val page = runCatching { SteamStoreApi.searchCatalog(appContext, q) }.getOrDefault(StoreCatalogPage())
            _ui.update {
                it.copy(
                    catalogLoadingMore = false,
                    catalogQuery = q,
                    catalogItems = (it.catalogItems + page.items).distinctBy { a -> a.id },
                    catalogTotal = if (page.totalCount > 0) page.totalCount else it.catalogTotal,
                )
            }
        }
    }

    fun loadDetail(appId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(detailLoading = true, detailError = null, detail = null, dlcApps = emptyList(), reviewType = "all", similarApps = emptyList(), similarForAppId = 0) }
            runCatching { SteamStoreApi.loadDetail(appContext, appId) }
                .onSuccess { d ->
                    if (d == null) _ui.update { it.copy(detailLoading = false, detailError = appContext.getString(com.winlator.cmod.R.string.store_load_failed)) }
                    else {
                        _ui.update { it.copy(detailLoading = false, detail = d) }
                        if (d.dlcAppIds.isNotEmpty()) loadDlcApps(appId, d.dlcAppIds)
                        if (d.genres.isNotEmpty()) loadSimilar(appId, d.genres)
                    }
                }
                .onFailure { e -> _ui.update { it.copy(detailLoading = false, detailError = e.localizedMessage) } }
        }
    }

    // Похожие: поиск по первому жанру, сортировка по оценке, без самой игры
    private fun loadSimilar(appId: Int, genres: List<String>) {
        val genre = genres.firstOrNull { it.isNotBlank() } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(similarLoading = true, similarForAppId = appId) }
            val page = runCatching {
                SteamStoreApi.searchCatalog(appContext, StoreCatalogQuery(term = genre, sort = "reviews_DESC"))
            }.getOrDefault(StoreCatalogPage())
            if (_ui.value.detail?.appId == appId) {
                _ui.update { it.copy(similarLoading = false, similarApps = page.items.filter { a -> a.id != appId }.take(10)) }
            } else _ui.update { it.copy(similarLoading = false) }
        }
    }

    private fun loadDlcApps(appId: Int, ids: List<Int>) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(dlcLoading = true) }
            val apps = runCatching { SteamStoreApi.loadStoreApps(appContext, ids) }.getOrDefault(emptyList())
            // не подмешиваем чужой деталке
            if (_ui.value.detail?.appId == appId) {
                _ui.update { it.copy(dlcLoading = false, dlcApps = apps) }
            }
        }
    }

    fun setReviewType(type: String) {
        val d = _ui.value.detail ?: return
        if (type == _ui.value.reviewType || _ui.value.reviewsLoadingMore) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(reviewType = type, reviewsLoadingMore = true) }
            val page = runCatching { SteamStoreApi.loadReviewPage(appContext, d.appId, 10, "", type) }
                .getOrDefault(ReviewPage())
            if (_ui.value.detail?.appId == d.appId) {
                _ui.update {
                    it.copy(
                        reviewsLoadingMore = false,
                        detail = it.detail?.copy(reviews = page.reviews, reviewsCursor = page.cursor),
                    )
                }
            } else _ui.update { it.copy(reviewsLoadingMore = false) }
        }
    }

    fun loadMoreReviews() {
        val d = _ui.value.detail ?: return
        val cursor = d.reviewsCursor
        if (cursor.isBlank() || _ui.value.reviewsLoadingMore) return
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(reviewsLoadingMore = true) }
            val type = _ui.value.reviewType
            val page = runCatching { SteamStoreApi.loadReviewPage(appContext, d.appId, 10, cursor, type) }
                .getOrDefault(ReviewPage())
            if (_ui.value.detail?.appId == d.appId) {
                _ui.update {
                    val cur = it.detail ?: return@update it
                    it.copy(
                        reviewsLoadingMore = false,
                        detail = cur.copy(
                            reviews = (cur.reviews + page.reviews).distinctBy { r -> r.text + r.timestamp },
                            reviewsCursor = page.cursor,
                        ),
                    )
                }
            } else _ui.update { it.copy(reviewsLoadingMore = false) }
        }
    }

    fun clearDetail() { _ui.update { it.copy(detail = null, detailError = null) } }

    fun refreshNews(appIds: List<Int>, gameNames: Map<Int, String> = emptyMap()) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(newsLoading = true) }
            val items = runCatching { SteamStoreApi.loadNewsForApps(appContext, appIds) }.getOrDefault(emptyList())
            val named = items.map { n -> n.copy(gameName = gameNames[n.appId] ?: n.gameName) }
            _ui.update { it.copy(newsLoading = false, news = named) }
        }
    }

    fun toggleWishlist(appId: Int) {
        val cur = _ui.value.wishlist.toMutableSet()
        if (!cur.add(appId)) cur.remove(appId)
        _ui.update { it.copy(wishlist = cur) }
        saveWishlist(cur)
    }

    fun isWishlisted(appId: Int): Boolean = _ui.value.wishlist.contains(appId)

    private fun loadWishlist(): Set<Int> {
        return try {
            val raw = appContext.getSharedPreferences("steam_store_prefs", android.content.Context.MODE_PRIVATE)
                .getString("wishlist", "").orEmpty()
            raw.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    private fun saveWishlist(set: Set<Int>) {
        try {
            appContext.getSharedPreferences("steam_store_prefs", android.content.Context.MODE_PRIVATE)
                .edit().putString("wishlist", set.joinToString(",")).apply()
        } catch (_: Exception) { }
    }
}
