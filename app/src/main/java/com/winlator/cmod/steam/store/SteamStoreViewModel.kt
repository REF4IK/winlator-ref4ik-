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
    val detailLoading: Boolean = false,
    val detail: StoreDetail? = null,
    val detailError: String? = null,
    val newsLoading: Boolean = false,
    val news: List<NewsItem> = emptyList(),
    val wishlist: Set<Int> = emptySet(),
)

class SteamStoreViewModel : ViewModel() {
    private val appContext = PluviaApp.instance.applicationContext
    private val _ui = MutableStateFlow(StoreUiState(wishlist = loadWishlist()))
    val ui: StateFlow<StoreUiState> = _ui.asStateFlow()

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
            _ui.update { it.copy(searchResults = emptyList(), searchLoading = false) }
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
    }

    fun loadDetail(appId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(detailLoading = true, detailError = null, detail = null) }
            runCatching { SteamStoreApi.loadDetail(appContext, appId) }
                .onSuccess { d ->
                    if (d == null) _ui.update { it.copy(detailLoading = false, detailError = "Не удалось загрузить") }
                    else _ui.update { it.copy(detailLoading = false, detail = d) }
                }
                .onFailure { e -> _ui.update { it.copy(detailLoading = false, detailError = e.localizedMessage) } }
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
