package com.winlator.cmod.steam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.winlator.cmod.PluviaApp
import com.winlator.cmod.R
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.db.PluviaDatabase
import com.winlator.cmod.steam.SteamGameLauncher
import com.winlator.cmod.steam.data.SteamApp
import com.winlator.cmod.steam.events.AndroidEvent
import com.winlator.cmod.steam.enums.AppType
import com.winlator.cmod.steam.enums.DownloadPhase
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.utils.getAvatarURL
import com.winlator.cmod.utils.StorageUtils
import `in`.dragonbra.javasteam.enums.EPersonaState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job

private enum class SteamPresenceStatus {
    ONLINE,
    AWAY,
    OFFLINE,
}

data class SteamContainerOption(
    val id: Int,
    val name: String,
)

data class SteamProfileUi(
    val name: String = "Steam",
    val avatarUrl: String = "",
    val status: String = "",
    val statusMode: String = SteamPresenceStatus.OFFLINE.name,
    val currentGame: String? = null,
)

data class SteamLibraryGameUi(
    val appId: Int,
    val name: String,
    val subtitle: String,
    val appType: AppType,
    val capsuleUrl: String,
    val heroUrl: String,
    val logoUrl: String,
    val installed: Boolean,
    val isDownloading: Boolean,
    val downloadPhase: DownloadPhase?,
    val downloadProgress: Float,
    val statusLine: String,
    val assignedContainerId: Int?,
)

data class SteamGameDetailUi(
    val appId: Int,
    val name: String,
    val subtitle: String,
    val appType: AppType,
    val capsuleUrl: String,
    val heroUrl: String,
    val logoUrl: String,
    val installed: Boolean,
    val isDownloading: Boolean,
    val downloadPhase: DownloadPhase?,
    val downloadProgress: Float,
    val statusLine: String,
    val installPath: String,
    val downloadSizeBytes: Long,
    val installSizeBytes: Long,
    val availableBytes: Long,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long?,
    val etaMs: Long?,
    val currentFileName: String?,
    val releaseDateSeconds: Long,
    val assignedContainerId: Int?,
)

data class SteamLibraryUiState(
    val isConnected: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isOfflineMode: Boolean = PrefManager.steamOfflineMode,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val profile: SteamProfileUi = SteamProfileUi(),
    val containers: List<SteamContainerOption> = emptyList(),
    val selectedContainerId: Int? = null,
    val selectedGameId: Int? = null,
    val totalGamesCount: Int = 0,
    val games: List<SteamLibraryGameUi> = emptyList(),
    val selectedGame: SteamGameDetailUi? = null,
)

class SteamLibraryViewModel : ViewModel() {
    private val appContext = PluviaApp.instance.applicationContext
    private val db = PluviaDatabase.getInstance()

    private val _uiState = MutableStateFlow(SteamLibraryUiState())
    val uiState: StateFlow<SteamLibraryUiState> = _uiState.asStateFlow()

    private var cachedApps: List<SteamApp> = emptyList()
    private val manifestSizeCache = mutableMapOf<Int, SteamService.ManifestSizes>()
    private val manifestRequests = mutableSetOf<Int>()
    private val selectedContainersByAppId = mutableMapOf<Int, Int>()
    private val pendingRefreshJobs = mutableMapOf<Int, Job>()
    private val onDownloadStatusChanged: (AndroidEvent.DownloadStatusChanged) -> Unit = { event ->
        viewModelScope.launch(Dispatchers.IO) {
            refreshDownloadDrivenUi(event.appId)
        }
    }
    private val onLibraryInstallStatusChanged: (AndroidEvent.LibraryInstallStatusChanged) -> Unit = { event ->
        viewModelScope.launch(Dispatchers.IO) {
            refreshDownloadDrivenUi(event.appId)
        }
    }

    init {
        SteamService.start(appContext)
        loadContainers()
        PluviaApp.events.on<AndroidEvent.DownloadStatusChanged, Unit>(onDownloadStatusChanged)
        PluviaApp.events.on<AndroidEvent.LibraryInstallStatusChanged, Unit>(onLibraryInstallStatusChanged)

        viewModelScope.launch {
            SteamService.isConnectedFlow.collectLatest { connected ->
                _uiState.update { it.copy(isConnected = connected) }
                refreshProfile()
            }
        }

        viewModelScope.launch {
            SteamService.isLoggedInFlow.collectLatest { loggedIn ->
                _uiState.update { it.copy(isLoggedIn = loggedIn) }
                refreshProfile()
                if (loggedIn) {
                    refreshLibrary()
                } else {
                    rebuildUi()
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            db.steamAppDao().getAllOwnedApps().collectLatest { apps ->
                cachedApps = apps
                rebuildUi()
            }
        }

        viewModelScope.launch {
            while (isActive) {
                val hasActiveDownloads = _uiState.value.games.any { it.isDownloading }
                if (hasActiveDownloads) {
                    delay(450)
                    refreshDownloadDrivenUi()
                } else {
                    delay(5000)
                    refreshProfile()
                    refreshSelectedGame(_uiState.value.selectedGameId)
                }
            }
        }
    }

    fun loadContainers() {
        val containerManager = ContainerManager(appContext)
        val containers = containerManager.containers.map { SteamContainerOption(it.id, it.name) }
        val preferredId = when {
            containers.isEmpty() -> null
            containers.any { it.id == PrefManager.preferredSteamContainerId } -> PrefManager.preferredSteamContainerId
            else -> containers.first().id
        }
        if (preferredId != null) {
            PrefManager.preferredSteamContainerId = preferredId
        }
        _uiState.update { current ->
            current.copy(
                containers = containers,
                selectedContainerId = preferredId,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            rebuildUi()
        }
    }

    fun setSelectedGame(appId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(selectedGameId = appId) }
            refreshSelectedGame(appId)
            ensureManifestSizes(appId)
        }
    }

    fun setSelectedContainer(containerId: Int) {
        PrefManager.preferredSteamContainerId = containerId
        val selectedAppId = _uiState.value.selectedGameId
        selectedAppId?.let {
            selectedContainersByAppId[it] = containerId
        }
        _uiState.update { current ->
            current.copy(
                selectedContainerId = containerId,
                selectedGame = current.selectedGame?.let { detail ->
                    if (selectedAppId != null && detail.appId == selectedAppId) {
                        detail.copy(assignedContainerId = containerId)
                    } else {
                        detail
                    }
                },
            )
        }
    }

    fun setCustomInstallPath(appId: Int, customInstallPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SteamService.setCustomInstallPath(appId, customInstallPath)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        errorMessage = throwable.localizedMessage
                            ?: appContext.getString(R.string.steam_library_folder_resolve_failed),
                    )
                }
            }
            refreshSelectedGame(appId)
        }
    }

    fun refreshLibrary() {
        if (_uiState.value.isOfflineMode) {
            viewModelScope.launch(Dispatchers.IO) {
                rebuildUi()
                refreshProfile()
            }
            return
        }
        if (!_uiState.value.isLoggedIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { SteamService.refreshOwnedGamesFromServer() }
            }
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Steam refresh failed",
                    )
                }
            } else {
                rebuildUi()
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun downloadGame(appId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val installPath = buildInstallPath(appId)
            val branch = PrefManager.getSteamSelectedBranch(appId)
            val result = runCatching {
                SteamService.downloadApp(
                    appId = appId,
                    downloadableDepots = SteamService.getDownloadableDepots(appId),
                    userSelectedDlcAppIds = emptyList(),
                    branch = branch,
                    includeInstalledDepots = false,
                    enableVerify = false,
                    customInstallPath = installPath,
                )
            }
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        errorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: appContext.getString(R.string.steam_library_download_failed),
                    )
                }
            }
            refreshDownloadDrivenUi(appId)
            scheduleBurstRefresh(appId)
        }
    }

    fun pauseDownload(appId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            SteamService.pauseDownload(appId)
            refreshDownloadDrivenUi(appId)
            scheduleBurstRefresh(appId)
        }
    }

    fun resumeDownload(appId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            SteamService.resumeDownload(appId)
            refreshDownloadDrivenUi(appId)
            scheduleBurstRefresh(appId)
        }
    }

    fun cancelDownload(appId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            SteamService.cancelDownload(appId)
            refreshDownloadDrivenUi(appId)
            scheduleBurstRefresh(appId)
        }
    }

    fun deleteGame(appId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                SteamService.deleteApp(appId)
            }
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        errorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: appContext.getString(R.string.fm_delete_fail),
                    )
                }
            }
            manifestSizeCache.remove(appId)
            refreshDownloadDrivenUi(appId)
            scheduleBurstRefresh(appId)
        }
    }

    fun playGame(appId: Int) {
        val app = cachedApps.firstOrNull { it.id == appId } ?: return
        val assignedContainer = selectedContainersByAppId[appId] ?: PrefManager.preferredSteamContainerId.takeIf { it != 0 }
        SteamGameLauncher.launch(appContext, app, assignedContainer)
    }

    fun logOut() {
        PrefManager.steamOfflineMode = false
        SteamService.logOut()
        _uiState.update { it.copy(isOfflineMode = false, games = emptyList(), selectedGame = null, selectedGameId = null) }
        refreshProfile()
    }

    fun onLoginFinished() {
        refreshLibrary()
        refreshProfile()
    }

    fun setOfflineMode(enabled: Boolean) {
        PrefManager.steamOfflineMode = enabled
        _uiState.update { it.copy(isOfflineMode = enabled) }
        viewModelScope.launch(Dispatchers.IO) {
            rebuildUi()
            refreshProfile()
            if (!enabled && _uiState.value.isLoggedIn) {
                refreshLibrary()
            }
        }
    }

    override fun onCleared() {
        PluviaApp.events.off<AndroidEvent.DownloadStatusChanged, Unit>(onDownloadStatusChanged)
        PluviaApp.events.off<AndroidEvent.LibraryInstallStatusChanged, Unit>(onLibraryInstallStatusChanged)
        super.onCleared()
    }

    private fun refreshProfile() {
        val persona = SteamService.instance?.localPersona?.value
        val offlineMode = PrefManager.steamOfflineMode
        val statusMode = when {
            offlineMode -> SteamPresenceStatus.OFFLINE
            persona?.state == EPersonaState.Online -> SteamPresenceStatus.ONLINE
            persona?.state == EPersonaState.Away || persona?.state == EPersonaState.Snooze || persona?.state == EPersonaState.Busy -> SteamPresenceStatus.AWAY
            else -> SteamPresenceStatus.OFFLINE
        }
        val statusLabel = when (statusMode) {
            SteamPresenceStatus.ONLINE -> appContext.getString(R.string.steam_library_status_online)
            SteamPresenceStatus.AWAY -> appContext.getString(R.string.steam_library_status_away)
            SteamPresenceStatus.OFFLINE -> appContext.getString(R.string.steam_library_status_offline)
        }
        _uiState.update { current ->
            current.copy(
                isOfflineMode = offlineMode,
                profile = SteamProfileUi(
                    name = persona?.name?.ifBlank { PrefManager.steamUserName.ifBlank { "Steam" } }
                        ?: PrefManager.steamUserName.ifBlank { "Steam" },
                    avatarUrl = (persona?.avatarHash ?: PrefManager.steamUserAvatarHash).getAvatarURL(),
                    status = statusLabel,
                    statusMode = statusMode.name,
                    currentGame = persona?.gameName?.takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    private suspend fun rebuildUi() {
        val selectedGameId = determineSelectedGameId()

        val games = cachedApps.map(::buildLibraryGameUi)

        _uiState.update { current ->
            current.copy(
                isOfflineMode = PrefManager.steamOfflineMode,
                selectedContainerId = PrefManager.preferredSteamContainerId.takeIf { it != 0 },
                selectedGameId = selectedGameId,
                totalGamesCount = cachedApps.size,
                games = games,
            )
        }

        refreshSelectedGame(selectedGameId)
        selectedGameId?.let { ensureManifestSizes(it) }
    }

    private suspend fun refreshDownloadDrivenUi(focusedAppId: Int? = _uiState.value.selectedGameId) {
        val currentState = _uiState.value
        if (currentState.games.isEmpty()) {
            refreshSelectedGame(focusedAppId)
            return
        }

        val appById = cachedApps.associateBy { it.id }
        val interestingIds = buildSet {
            focusedAppId?.let(::add)
            currentState.games.filter { it.isDownloading }.forEach { add(it.appId) }
        }

        if (interestingIds.isEmpty()) {
            refreshSelectedGame(focusedAppId)
            return
        }

        val updatedGames = currentState.games.map { currentGame ->
            if (currentGame.appId !in interestingIds) {
                currentGame
            } else {
                appById[currentGame.appId]?.let(::buildLibraryGameUi) ?: currentGame
            }
        }

        _uiState.update { current ->
            current.copy(
                isOfflineMode = PrefManager.steamOfflineMode,
                selectedContainerId = PrefManager.preferredSteamContainerId.takeIf { it != 0 },
                totalGamesCount = cachedApps.size,
                games = updatedGames,
            )
        }

        refreshSelectedGame(focusedAppId)
    }

    private suspend fun refreshSelectedGame(appId: Int?) {
        val selectedGame = cachedApps.firstOrNull { it.id == appId }?.let { buildSelectedGameDetail(it) }
        _uiState.update { current ->
            current.copy(
                isOfflineMode = PrefManager.steamOfflineMode,
                selectedContainerId = PrefManager.preferredSteamContainerId.takeIf { it != 0 },
                selectedGameId = appId,
                selectedGame = selectedGame,
            )
        }
    }

    private fun determineSelectedGameId(): Int? {
        val currentSelected = _uiState.value.selectedGameId
        return when {
            cachedApps.isEmpty() -> null
            currentSelected != null && cachedApps.any { it.id == currentSelected } -> currentSelected
            else -> cachedApps.first().id
        }
    }

    private fun isTrackedDownloadPhase(status: DownloadPhase?): Boolean {
        return when (status) {
            DownloadPhase.PREPARING,
            DownloadPhase.VERIFYING,
            DownloadPhase.PATCHING,
            DownloadPhase.DOWNLOADING,
            DownloadPhase.QUEUED,
            DownloadPhase.PAUSED,
            -> true
            else -> false
        }
    }

    private fun buildLibraryGameUi(app: SteamApp): SteamLibraryGameUi {
        val downloadInfo = SteamService.getAppDownloadInfo(app.id)
        val status = downloadInfo?.getStatusFlow()?.value
        val progress = downloadInfo?.getProgress()?.coerceIn(0f, 1f) ?: 0f
        val installed = SteamService.isAppInstalled(app.id)
        val statusLine = when {
            installed && status != DownloadPhase.DOWNLOADING -> appContext.getString(R.string.steam_library_installed_badge)
            status == DownloadPhase.PAUSED -> appContext.getString(R.string.steam_library_status_paused)
            status == DownloadPhase.QUEUED -> appContext.getString(R.string.steam_library_status_queued)
            isTrackedDownloadPhase(status) -> {
                appContext.getString(
                    R.string.steam_library_downloading_progress,
                    (progress * 100f).toInt(),
                )
            }
            else -> appContext.getString(R.string.steam_library_ready_badge)
        }

        return SteamLibraryGameUi(
            appId = app.id,
            name = app.name,
            subtitle = listOfNotNull(
                app.developer.takeIf { it.isNotBlank() },
                app.publisher.takeIf { it.isNotBlank() },
            ).joinToString(" / "),
            appType = app.type,
            capsuleUrl = app.getCapsuleUrl(),
            heroUrl = app.getHeroUrl(),
            logoUrl = app.getLogoUrl(),
            installed = installed,
            isDownloading = isTrackedDownloadPhase(status),
            downloadPhase = status,
            downloadProgress = progress,
            statusLine = statusLine,
            assignedContainerId = selectedContainersByAppId[app.id] ?: PrefManager.preferredSteamContainerId.takeIf { it != 0 },
        )
    }

    private suspend fun buildSelectedGameDetail(app: SteamApp): SteamGameDetailUi = withContext(Dispatchers.IO) {
        val currentSelected = _uiState.value.selectedGame
        val downloadInfo = SteamService.getAppDownloadInfo(app.id)
        val status = downloadInfo?.getStatusFlow()?.value
        val progress = downloadInfo?.getProgress()?.coerceIn(0f, 1f) ?: 0f
        val installed = SteamService.isAppInstalled(app.id)
        val installPath = buildInstallPath(app.id)
        val manifestSizes = manifestSizeCache[app.id] ?: SteamService.ManifestSizes()
        val availableBytes = if (
            currentSelected?.appId == app.id &&
            isTrackedDownloadPhase(status)
        ) {
            currentSelected.availableBytes
        } else {
            runCatching { StorageUtils.getAvailableSpace(installPath) }.getOrDefault(currentSelected?.availableBytes ?: 0L)
        }
        val bytesProgress = downloadInfo?.getBytesProgress() ?: (0L to manifestSizes.downloadSize)
        val statusLine = when {
            installed && status != DownloadPhase.DOWNLOADING -> appContext.getString(R.string.steam_library_installed_badge)
            status == DownloadPhase.PREPARING -> appContext.getString(R.string.steam_library_status_downloading)
            status == DownloadPhase.VERIFYING -> appContext.getString(R.string.steam_library_status_downloading)
            status == DownloadPhase.PATCHING -> appContext.getString(R.string.steam_library_status_downloading)
            status == DownloadPhase.DOWNLOADING -> appContext.getString(R.string.steam_library_status_downloading)
            status == DownloadPhase.PAUSED -> appContext.getString(R.string.steam_library_status_paused)
            status == DownloadPhase.QUEUED -> appContext.getString(R.string.steam_library_status_queued)
            else -> appContext.getString(R.string.steam_library_ready_badge)
        }

        SteamGameDetailUi(
            appId = app.id,
            name = app.name,
            subtitle = listOfNotNull(
                app.developer.takeIf { it.isNotBlank() },
                app.publisher.takeIf { it.isNotBlank() },
            ).joinToString(" / "),
            appType = app.type,
            capsuleUrl = app.getCapsuleUrl(),
            heroUrl = app.getHeroUrl(),
            logoUrl = app.getLogoUrl(),
            installed = installed,
            isDownloading = isTrackedDownloadPhase(status),
            downloadPhase = status,
            downloadProgress = progress,
            statusLine = statusLine,
            installPath = installPath,
            downloadSizeBytes = manifestSizes.downloadSize,
            installSizeBytes = manifestSizes.installSize,
            availableBytes = availableBytes,
            downloadedBytes = bytesProgress.first,
            totalBytes = bytesProgress.second,
            speedBytesPerSec = downloadInfo?.getCurrentDownloadSpeed(),
            etaMs = downloadInfo?.getEstimatedTimeRemaining(),
            currentFileName = downloadInfo?.getCurrentFileNameFlow()?.value,
            releaseDateSeconds = app.releaseDate,
            assignedContainerId = selectedContainersByAppId[app.id] ?: PrefManager.preferredSteamContainerId.takeIf { it != 0 },
        )
    }

    private fun ensureManifestSizes(appId: Int) {
        if (manifestSizeCache.containsKey(appId) || manifestRequests.contains(appId)) return
        manifestRequests.add(appId)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                SteamService.getSelectedManifestSizes(
                    appId = appId,
                    branch = PrefManager.getSteamSelectedBranch(appId),
                )
            }.onSuccess { sizes ->
                manifestSizeCache[appId] = sizes
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.localizedMessage ?: "Failed to calculate sizes")
                }
            }
            manifestRequests.remove(appId)
            if (_uiState.value.selectedGameId == appId) {
                refreshSelectedGame(appId)
            }
        }
    }

    private fun buildInstallPath(appId: Int): String {
        return SteamService.getAppDirPath(appId)
    }

    private fun scheduleBurstRefresh(appId: Int) {
        pendingRefreshJobs.remove(appId)?.cancel()
        pendingRefreshJobs[appId] = viewModelScope.launch(Dispatchers.IO) {
            repeat(6) {
                delay(500)
                refreshDownloadDrivenUi(appId)
            }
            pendingRefreshJobs.remove(appId)
        }
    }
}
