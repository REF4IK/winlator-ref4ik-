package com.winlator.cmod.steam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Badge
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winlator.cmod.R
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.steam.enums.AppType
import com.winlator.cmod.steam.enums.ControllerSupport
import com.winlator.cmod.steam.enums.DownloadPhase
import com.winlator.cmod.steam.enums.PathType
import com.winlator.cmod.steam.enums.SaveLocation
import com.winlator.cmod.steam.enums.SyncResult
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.ui.SteamContainerOption
import androidx.compose.material3.HorizontalDivider
import com.winlator.cmod.steam.ui.AchievementsSheet
import com.winlator.cmod.steam.ui.SteamFriendsScreen
import com.winlator.cmod.steam.ui.SteamGameDetailUi
import com.winlator.cmod.steam.ui.SteamLibraryGameUi
import com.winlator.cmod.steam.ui.SteamLibraryUiState
import com.winlator.cmod.steam.ui.SteamLibraryViewModel
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.workshop.WorkshopManager
import com.winlator.cmod.ui.theme.WinlatorTheme
import com.winlator.cmod.utils.StorageUtils
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SteamViewMode { GRID, LIST, COMPACT }

private enum class SteamTab {
    DOWNLOADS,
    STEAM,
}

private enum class SteamContentFilter {
    GAMES,
    DLC,
    APPLICATIONS,
    TOOLS,
}

class SteamLibraryActivity : ComponentActivity() {
    private var pendingSelectedAppId by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingSelectedAppId = intent.getIntExtra(EXTRA_SELECTED_APP_ID, 0).takeIf { it > 0 }
        SteamService.initLoginStatus(this)
        SteamService.start(this)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            WinlatorTheme {
                val viewModel: SteamLibraryViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()
                val context = LocalContext.current
                val activity = this@SteamLibraryActivity
                val currentSelectedGameId by rememberUpdatedState(state.selectedGame?.appId)
                var pendingExportGameId by rememberSaveable { mutableStateOf<Int?>(null) }
                var pendingImportGameId by rememberSaveable { mutableStateOf<Int?>(null) }
                val loginLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult(),
                ) {
                    viewModel.onLoginFinished()
                }
                val folderPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree(),
                ) { uri ->
                    val selectedGameId = currentSelectedGameId ?: return@rememberLauncherForActivityResult
                    if (uri == null) return@rememberLauncherForActivityResult

                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                    }

                    val resolvedPath = resolveFolderPath(context, uri)
                    if (resolvedPath.isNullOrBlank()) {
                        Toast.makeText(context, context.getString(R.string.steam_library_folder_resolve_failed), Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.setCustomInstallPath(selectedGameId, resolvedPath)
                    }
                }
                val savesExportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/zip"),
                ) { uri ->
                    val appId = pendingExportGameId
                    pendingExportGameId = null
                    if (uri == null || appId == null) return@rememberLauncherForActivityResult
                    activity.lifecycleScope.launch {
                        val exported = SteamGameActions.exportSaves(activity, appId, uri)
                        val message = if (exported) {
                            activity.getString(R.string.steam_library_saves_export_success)
                        } else {
                            activity.getString(R.string.steam_library_saves_export_failed)
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                }
                val savesImportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    val appId = pendingImportGameId
                    pendingImportGameId = null
                    if (uri == null || appId == null) return@rememberLauncherForActivityResult
                    activity.lifecycleScope.launch {
                        val imported = SteamGameActions.importSaves(activity, appId, uri)
                        val message = if (imported) {
                            activity.getString(R.string.steam_library_saves_import_success)
                        } else {
                            activity.getString(R.string.steam_library_saves_import_failed)
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.loadContainers()
                }

                SteamLibraryScreen(
                    state = state,
                    requestedSelectedGameId = pendingSelectedAppId,
                    onBack = ::finish,
                    onLogin = { loginLauncher.launch(Intent(this, SteamLoginActivity::class.java)) },
                    onRefresh = viewModel::refreshLibrary,
                    onLogout = viewModel::logOut,
                    onSetOfflineMode = viewModel::setOfflineMode,
                    onDismissError = viewModel::clearError,
                    onSelectGame = viewModel::setSelectedGame,
                    onSelectContainer = viewModel::setSelectedContainer,
                    onPickInstallFolder = { folderPickerLauncher.launch(null) },
                    onDownload = viewModel::downloadGame,
                    onPauseDownload = viewModel::pauseDownload,
                    onResumeDownload = viewModel::resumeDownload,
                    onCancelDownload = viewModel::cancelDownload,
                    onPlay = viewModel::playGame,
                    onDelete = viewModel::deleteGame,
                    onOpenSettings = { appId, containerId ->
                        val opened = SteamGameActions.openShortcutSettings(activity, appId, containerId)
                        if (!opened) {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.steam_library_settings_unavailable),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onCreateShortcut = { appId, containerId ->
                        activity.lifecycleScope.launch {
                            val created = SteamGameActions.addShortcutToHomeScreen(activity, appId, containerId)
                            val message = if (created) {
                                activity.getString(R.string.steam_library_shortcut_created)
                            } else {
                                activity.getString(R.string.steam_library_shortcut_failed)
                            }
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExportSaves = { appId, gameName ->
                        pendingExportGameId = appId
                        val safeName = gameName
                            .replace(":", "")
                            .replace("/", "_")
                            .replace("\\", "_")
                            .replace(" ", "_")
                            .ifBlank { "steam_game" }
                        savesExportLauncher.launch("${safeName}_saves.zip")
                    },
                    onImportSaves = { appId ->
                        pendingImportGameId = appId
                        savesImportLauncher.launch(arrayOf("application/zip"))
                    },
                    onUploadCloudSaves = { appId ->
                        activity.lifecycleScope.launch {
                            val synced = SteamGameActions.syncCloudSaves(activity, appId, SaveLocation.Local)
                            val message = if (synced) {
                                activity.getString(R.string.steam_library_cloud_upload_success)
                            } else {
                                activity.getString(R.string.steam_library_cloud_upload_failed)
                            }
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDownloadCloudSaves = { appId ->
                        activity.lifecycleScope.launch {
                            val synced = SteamGameActions.syncCloudSaves(activity, appId, SaveLocation.Remote)
                            val message = if (synced) {
                                activity.getString(R.string.steam_library_cloud_download_success)
                            } else {
                                activity.getString(R.string.steam_library_cloud_download_failed)
                            }
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSelectedGameRequestConsumed = {
                        pendingSelectedAppId = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSelectedAppId = intent.getIntExtra(EXTRA_SELECTED_APP_ID, 0).takeIf { it > 0 }
    }

    companion object {
        const val EXTRA_SELECTED_APP_ID = "steam_library_selected_app_id"
    }
}

private fun resolveFolderPath(context: android.content.Context, uri: Uri): String? {
    val directPath = FileUtils.getFilePathFromUri(context, uri)
    if (!directPath.isNullOrBlank()) return directPath

    return try {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        when {
            docId.startsWith("primary:") -> {
                val path = docId.substringAfter(":")
                val storage = android.os.Environment.getExternalStorageDirectory().path
                if (path.isBlank()) storage else "$storage/$path"
            }
            docId.contains(":") -> {
                val parts = docId.split(":", limit = 2)
                if (parts.size == 2) {
                    if (parts[1].isBlank()) "/storage/${parts[0]}" else "/storage/${parts[0]}/${parts[1]}"
                } else {
                    uri.path
                }
            }
            else -> uri.path
        }
    } catch (_: Exception) {
        uri.path
    }
}

@Composable
private fun SteamLibraryScreen(
    state: SteamLibraryUiState,
    requestedSelectedGameId: Int?,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onSetOfflineMode: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    onSelectGame: (Int) -> Unit,
    onSelectContainer: (Int) -> Unit,
    onPickInstallFolder: (Int) -> Unit,
    onDownload: (Int) -> Unit,
    onPauseDownload: (Int) -> Unit,
    onResumeDownload: (Int) -> Unit,
    onCancelDownload: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onOpenSettings: (Int, Int?) -> Unit,
    onCreateShortcut: (Int, Int?) -> Unit,
    onExportSaves: (Int, String) -> Unit,
    onImportSaves: (Int) -> Unit,
    onUploadCloudSaves: (Int) -> Unit,
    onDownloadCloudSaves: (Int) -> Unit,
    onSelectedGameRequestConsumed: () -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(SteamTab.STEAM) }
    var contentFilter by rememberSaveable { mutableStateOf(SteamContentFilter.GAMES) }
    var selectedOverlayGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var loginPlaceholderUnlocked by rememberSaveable { mutableStateOf(false) }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showFriends by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf<Int?>(null) }
    var viewMode by rememberSaveable { mutableStateOf(SteamViewMode.GRID) }

    val tabGames = when (tab) {
        SteamTab.DOWNLOADS -> state.games.filter { it.isDownloading || it.installed }
        SteamTab.STEAM -> state.games
    }
    val visibleGames = tabGames
        .filter { it.matchesContentFilter(contentFilter) }
        .filter { game ->
            searchQuery.isBlank() ||
                game.name.contains(searchQuery, ignoreCase = true) ||
                game.subtitle.contains(searchQuery, ignoreCase = true)
        }
    val filteredSteamCount = tabGames.count { it.matchesContentFilter(contentFilter) }
    val selectedContainerId = state.selectedGame?.assignedContainerId ?: state.selectedContainerId
    val selectedGame = state.selectedGame?.takeIf { it.appId == selectedOverlayGameId }
    val profileName = state.profile.name.ifBlank { "Steam" }
    val hasOfflineLibrary = state.games.isNotEmpty()
    val hasStoredSession = remember(state.isLoggedIn, state.isOfflineMode) {
        state.isLoggedIn || state.isOfflineMode || SteamService.hasStoredCredentials(context)
    }
    val shouldShowStartupLoader = !state.isLoggedIn && !loginPlaceholderUnlocked && hasStoredSession && !hasOfflineLibrary

    LaunchedEffect(state.isLoggedIn, state.isConnected) {
        if (state.isLoggedIn) {
            loginPlaceholderUnlocked = true
        } else if (!loginPlaceholderUnlocked) {
            delay(1200)
            loginPlaceholderUnlocked = true
        }
    }

    LaunchedEffect(requestedSelectedGameId, state.games) {
        val targetAppId = requestedSelectedGameId ?: return@LaunchedEffect
        val targetGame = state.games.firstOrNull { it.appId == targetAppId } ?: return@LaunchedEffect
        selectedOverlayGameId = targetGame.appId
        onSelectGame(targetGame.appId)
        onSelectedGameRequestConsumed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SteamTopBar(
                tab = tab,
                isLoggedIn = state.isLoggedIn,
                steamCount = filteredSteamCount,
                canSearch = state.isLoggedIn || hasOfflineLibrary,
                viewMode = viewMode,
                onBack = onBack,
                onRefresh = onRefresh,
                onSelectTab = { tab = it },
                onSearchClick = {
                    searchVisible = !searchVisible
                    if (!searchVisible) {
                        searchQuery = ""
                    }
                },
                onMenuClick = { menuExpanded = true },
                onViewModeChange = { viewMode = it },
            )

            if (searchVisible) {
                SteamSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        searchQuery = ""
                        searchVisible = false
                    },
                )
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                Text(
                    text = stringResource(R.string.steam_library_content_types_title),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                SteamContentFilter.entries.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(text = filter.label()) },
                        trailingIcon = {
                            if (contentFilter == filter) {
                                Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        },
                        onClick = {
                            contentFilter = filter
                            menuExpanded = false
                        },
                    )
                }
                if (state.isLoggedIn || hasStoredSession) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.steam_library_refresh)) },
                        leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                    )
                }
                if (state.isOfflineMode || (!state.isLoggedIn && hasStoredSession)) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.steam_library_go_online)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSetOfflineMode(false)
                            onRefresh()
                        },
                    )
                } else if (state.isLoggedIn) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.steam_library_go_offline)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onSetOfflineMode(true)
                        },
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.steam_library_autoupdate_title)) },
                    trailingIcon = {
                        var autoUpdateEnabled by remember { mutableStateOf(PrefManager.autoUpdateEnabled) }
                        Switch(
                            checked = autoUpdateEnabled,
                            onCheckedChange = {
                                autoUpdateEnabled = it
                                PrefManager.autoUpdateEnabled = it
                            },
                        )
                    },
                    onClick = {},
                )
                if (PrefManager.autoUpdateEnabled) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.steam_library_autoupdate_wifi_only)) },
                        trailingIcon = {
                            var wifiOnly by remember { mutableStateOf(PrefManager.autoUpdateWifiOnly) }
                            Switch(
                                checked = wifiOnly,
                                onCheckedChange = {
                                    wifiOnly = it
                                    PrefManager.autoUpdateWifiOnly = it
                                },
                            )
                        },
                        onClick = {},
                    )
                }
                if (state.isLoggedIn || hasStoredSession) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.steam_library_logout_action)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onLogout()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.steam_library_login_button)) },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onLogin()
                        },
                    )
                }
            }

            if (state.isLoggedIn || hasOfflineLibrary || hasStoredSession) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(28.dp)) {
                            Box(
                                modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.profile.avatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = state.profile.avatarUrl,
                                        contentDescription = profileName,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            // Online indicator dot
                            if (state.isLoggedIn) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(if (state.profile.isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.steam_library_connected_as, profileName),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            if (state.isLoggedIn && state.profile.isOnline && state.profile.currentGame?.isNotBlank() == true) {
                                    Text(
                                        text = "Playing ${state.profile.currentGame ?: ""}",
                                        color = MaterialTheme.colorScheme.tertiary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                            }
                        }
                    }
                    if (state.isLoggedIn) {
                        val onlineCount = SteamService.friendsList.value.count { it.isOnline }
                        IconButton(onClick = { showFriends = true }, modifier = Modifier.size(28.dp)) {
                            Box {
                                Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.steam_friends_title), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                if (onlineCount > 0) {
                                    Badge(modifier = Modifier.align(Alignment.TopEnd).size(14.dp), containerColor = MaterialTheme.colorScheme.primary) {
                                        Text(text = onlineCount.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    if (state.isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
                }
            }
            if (state.errorMessage != null) {
                SteamInlineMessage(
                    title = stringResource(R.string.steam_library_error_title),
                    body = state.errorMessage,
                    onDismiss = onDismissError,
                )
            }

            when {
                state.containers.isEmpty() -> SteamPlaceholder(
                    title = stringResource(R.string.steam_no_containers),
                    body = stringResource(R.string.steam_library_no_containers_message),
                )
                shouldShowStartupLoader -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        strokeWidth = 3.dp,
                    )
                }
                !state.isLoggedIn && !hasOfflineLibrary && !hasStoredSession -> SteamPlaceholder(
                    title = stringResource(R.string.steam_library_login_title),
                    body = stringResource(R.string.steam_library_login_hint),
                    actionLabel = stringResource(R.string.steam_library_login_button),
                    onAction = onLogin,
                )
                visibleGames.isEmpty() -> SteamPlaceholder(
                    title = stringResource(R.string.steam_library_empty_title),
                    body = stringResource(R.string.steam_library_empty_filtered),
                )
                else -> Box(modifier = Modifier.fillMaxSize()) {
                    when (viewMode) {
                        SteamViewMode.GRID -> {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(152.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(visibleGames, key = { it.appId }) { game ->
                                    SteamGameCard(
                                        game = game,
                                        onClick = {
                                            selectedOverlayGameId = game.appId
                                            onSelectGame(game.appId)
                                        },
                                    )
                                }
                            }
                        }
                        SteamViewMode.LIST -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(visibleGames, key = { it.appId }) { game ->
                                    SteamGameListCard(
                                        game = game,
                                        onClick = {
                                            selectedOverlayGameId = game.appId
                                            onSelectGame(game.appId)
                                        },
                                    )
                                }
                            }
                        }
                        SteamViewMode.COMPACT -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(visibleGames, key = { it.appId }) { game ->
                                    SteamGameCompactCard(
                                        game = game,
                                        onClick = {
                                            selectedOverlayGameId = game.appId
                                            onSelectGame(game.appId)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedOverlayGameId != null) {
            SteamGameOverlay(
                game = selectedGame,
                activeTab = tab,
                containers = state.containers,
                selectedContainerId = selectedContainerId,
                onClose = { selectedOverlayGameId = null },
                onSelectContainer = onSelectContainer,
                onPickInstallFolder = onPickInstallFolder,
                onDownload = { appId ->
                    selectedOverlayGameId = appId
                    tab = SteamTab.DOWNLOADS
                    onSelectGame(appId)
                    onDownload(appId)
                },
                onPauseDownload = onPauseDownload,
                onResumeDownload = onResumeDownload,
                onCancelDownload = onCancelDownload,
                onPlay = onPlay,
                onDelete = onDelete,
                onOpenSettings = onOpenSettings,
                onCreateShortcut = onCreateShortcut,
                onExportSaves = onExportSaves,
                onImportSaves = onImportSaves,
                onUploadCloudSaves = onUploadCloudSaves,
                onDownloadCloudSaves = onDownloadCloudSaves,
                isOfflineMode = state.isOfflineMode,
            )
        }

        if (showFriends) {
            SteamFriendsScreen(onBack = { showFriends = false })
        }
    }
}

@Composable
private fun SteamTopBar(
    tab: SteamTab,
    isLoggedIn: Boolean,
    steamCount: Int,
    canSearch: Boolean,
    viewMode: SteamViewMode,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (SteamTab) -> Unit,
    onSearchClick: () -> Unit,
    onMenuClick: () -> Unit,
    onViewModeChange: (SteamViewMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundActionButton(Icons.AutoMirrored.Filled.ArrowBack, onBack)
            RoundActionButton(Icons.Filled.Refresh, onRefresh)
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SteamTabButton(
                    label = stringResource(R.string.steam_library_tab_downloads),
                    selected = tab == SteamTab.DOWNLOADS,
                    onClick = { onSelectTab(SteamTab.DOWNLOADS) },
                )
                SteamTabButton(
                    label = if (steamCount > 0) {
                        stringResource(R.string.steam_library_tab_steam_count, steamCount)
                    } else {
                        stringResource(R.string.steam_library_tab_steam)
                    },
                    selected = tab == SteamTab.STEAM,
                    onClick = { onSelectTab(SteamTab.STEAM) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // View mode toggle
            if (tab == SteamTab.STEAM) {
                SteamViewButton(
                    icon = Icons.Filled.GridView,
                    selected = viewMode == SteamViewMode.GRID,
                    onClick = { onViewModeChange(SteamViewMode.GRID) },
                )
                SteamViewButton(
                    icon = Icons.Filled.FormatListBulleted,
                    selected = viewMode == SteamViewMode.LIST,
                    onClick = { onViewModeChange(SteamViewMode.LIST) },
                )
                SteamViewButton(
                    icon = Icons.Filled.ViewCompact,
                    selected = viewMode == SteamViewMode.COMPACT,
                    onClick = { onViewModeChange(SteamViewMode.COMPACT) },
                )
            }
            RoundActionButton(Icons.Filled.Search, onSearchClick, enabled = canSearch)
            RoundActionButton(Icons.Filled.Tune, onMenuClick)
        }
    }
}

@Composable
private fun RoundActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Int = 48,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size((size * 0.42f).dp),
        )
    }
}

@Composable
private fun SteamTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SteamViewButton(icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SteamInlineMessage(title: String, body: String, onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = null)
            }
        }
    }
}

@Composable
private fun SteamPlaceholder(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(30.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.width(540.dp).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (actionLabel != null && onAction != null) {
                    Button(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamGameCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    val context = LocalContext.current
    val capsuleRequest = remember(game.appId, game.capsuleUrl) {
        ImageRequest.Builder(context)
            .data(game.capsuleUrl)
            .crossfade(false)
            .memoryCacheKey("steam-grid-${game.appId}")
            .diskCacheKey(game.capsuleUrl)
            .size(480, 224)
            .build()
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(104.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                AsyncImage(
                    model = capsuleRequest,
                    contentDescription = game.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                if (game.isDownloading || game.installed) {
                    StatusBadge(
                        text = if (game.isDownloading) game.statusLine else stringResource(R.string.steam_library_installed_badge),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = game.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.isDownloading) {
                    LinearProgressIndicator(
                        progress = { game.downloadProgress },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamGameListCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    val context = LocalContext.current
    val capsuleRequest = remember(game.appId, game.capsuleUrl) {
        ImageRequest.Builder(context)
            .data(game.capsuleUrl)
            .crossfade(false)
            .memoryCacheKey("steam-list-${game.appId}")
            .diskCacheKey(game.capsuleUrl)
            .size(240, 112)
            .build()
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Box(modifier = Modifier.width(128.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))) {
                AsyncImage(
                    model = capsuleRequest,
                    contentDescription = game.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(game.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (game.subtitle.isNotBlank()) {
                    Text(game.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (game.isDownloading) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = { game.downloadProgress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                }
            }
            if (game.installed) {
                Box(modifier = Modifier.padding(8.dp).align(Alignment.CenterVertically)) {
                    StatusBadge(text = stringResource(R.string.steam_library_installed_badge))
                }
            }
        }
    }
}

@Composable
private fun SteamGameCompactCard(game: SteamLibraryGameUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(game.capsuleUrl)
                    .crossfade(false)
                    .memoryCacheKey("steam-compact-${game.appId}")
                    .size(80, 80)
                    .build(),
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(game.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (game.subtitle.isNotBlank()) {
                Text(game.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (game.installed) {
            Text(stringResource(R.string.steam_library_installed_badge), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }
        if (game.isDownloading) {
            Text(game.statusLine, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

private fun SteamLibraryGameUi.matchesContentFilter(filter: SteamContentFilter): Boolean {
    return when (filter) {
        SteamContentFilter.GAMES -> appType == AppType.game || appType == AppType.demo
        SteamContentFilter.DLC -> appType == AppType.dlc
        SteamContentFilter.APPLICATIONS -> appType == AppType.application
        SteamContentFilter.TOOLS -> appType == AppType.tool
    }
}

@Composable
private fun SteamContentFilter.label(): String {
    return when (this) {
        SteamContentFilter.GAMES -> stringResource(R.string.steam_library_content_filter_games)
        SteamContentFilter.DLC -> stringResource(R.string.steam_library_content_filter_dlc)
        SteamContentFilter.APPLICATIONS -> stringResource(R.string.steam_library_content_filter_applications)
        SteamContentFilter.TOOLS -> stringResource(R.string.steam_library_content_filter_tools)
    }
}

@Composable
private fun SteamGameOverlay(
    game: SteamGameDetailUi?,
    activeTab: SteamTab,
    containers: List<SteamContainerOption>,
    selectedContainerId: Int?,
    onClose: () -> Unit,
    onSelectContainer: (Int) -> Unit,
    onPickInstallFolder: (Int) -> Unit,
    onDownload: (Int) -> Unit,
    onPauseDownload: (Int) -> Unit,
    onResumeDownload: (Int) -> Unit,
    onCancelDownload: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onOpenSettings: (Int, Int?) -> Unit,
    onCreateShortcut: (Int, Int?) -> Unit,
    onExportSaves: (Int, String) -> Unit,
    onImportSaves: (Int) -> Unit,
    onUploadCloudSaves: (Int) -> Unit,
    onDownloadCloudSaves: (Int) -> Unit,
    isOfflineMode: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val compactLayout = activeTab == SteamTab.DOWNLOADS
    var isLaunchingGame by remember(game?.appId) { mutableStateOf(false) }
    var showContentManager by remember(game?.appId) { mutableStateOf(false) }
    var showWorkshopManager by remember(game?.appId) { mutableStateOf(false) }
    var showBranchPicker by remember(game?.appId) { mutableStateOf(false) }
    var showAchievements by remember(game?.appId) { mutableStateOf(false) }
    var workshopRefreshToken by remember(game?.appId) { mutableStateOf(0) }
    val appInfo = remember(game?.appId) { game?.let { SteamService.getAppInfoOf(it.appId) } }
    var selectedBranch by remember(game?.appId) {
        mutableStateOf(game?.let { PrefManager.getSteamSelectedBranch(it.appId) } ?: "public")
    }
    var playtimeText by remember(game?.appId) { mutableStateOf("--") }
    var lastPlayedText by remember(game?.appId) { mutableStateOf("--") }
    var cloudDifference by remember(game?.appId) { mutableStateOf<Boolean?>(null) }
    var updatePending by remember(game?.appId, selectedBranch) { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(isLaunchingGame) {
        if (isLaunchingGame) {
            delay(12000)
            isLaunchingGame = false
        }
    }

    LaunchedEffect(game?.appId, selectedBranch) {
        val currentGame = game ?: return@LaunchedEffect
        val canReachSteam = !isOfflineMode && SteamService.isConnected && SteamService.isLoggedIn
        val steamId64 = SteamService.userSteamId?.convertToUInt64()
            ?: PrefManager.steamUserSteamId64.takeIf { it != 0L }

        if (canReachSteam && steamId64 != null && steamId64 != 0L) {
            val ownedGame = withContext(Dispatchers.IO) {
                SteamService.getOwnedGames(steamId64).firstOrNull { it.appId == currentGame.appId }
            }
            playtimeText = ownedGame?.let { formatPlaytimeHours(it.playtimeForever) } ?: "--"
            lastPlayedText = ownedGame?.rtimeLastPlayed
                ?.takeIf { it > 0 }
                ?.let(::formatSteamTimestamp)
                ?: formatInstallTimestamp(currentGame.installPath)
        } else {
            playtimeText = "--"
            lastPlayedText = formatInstallTimestamp(currentGame.installPath)
        }

        if (currentGame.installed && canReachSteam) {
            cloudDifference = withContext(Dispatchers.IO) { SteamService.cloudSavesDiffer(currentGame.appId) }
            updatePending = withContext(Dispatchers.IO) {
                SteamService.isUpdatePending(currentGame.appId, selectedBranch)
            }
        } else {
            cloudDifference = null
            updatePending = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            .clickable(
                enabled = !isLaunchingGame && !showContentManager && !showWorkshopManager && !showBranchPicker,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (game == null) {
            CircularProgressIndicator()
            return@Box
        }

        var savesMenuExpanded by remember { mutableStateOf(false) }
        var cloudMenuExpanded by remember { mutableStateOf(false) }
        val showInstalledActions = game.installed
        val showFolderPicker = activeTab == SteamTab.STEAM && !game.installed && !game.isDownloading
        val isDownloadSession = game.isDownloading && !showInstalledActions
        val isPausedDownload = game.downloadPhase == DownloadPhase.PAUSED
        val enabledWorkshopIds = remember(game.appId, workshopRefreshToken) {
            PrefManager.getSteamWorkshopEnabledItemIds(game.appId)
        }
        val availableBranches = remember(appInfo?.branches, selectedBranch) {
            buildList {
                add("public")
                appInfo?.branches
                    ?.filterValues { !it.pwdRequired }
                    ?.keys
                    ?.sorted()
                    ?.forEach(::add)
                if (selectedBranch.isNotBlank()) {
                    add(selectedBranch)
                }
            }.distinct()
        }
        val controllerSupportLabel = remember(appInfo?.controllerSupport) {
            when (appInfo?.controllerSupport ?: ControllerSupport.none) {
                ControllerSupport.full -> context.getString(R.string.steam_library_controller_support_full)
                ControllerSupport.partial -> context.getString(R.string.steam_library_controller_support_partial)
                ControllerSupport.none -> context.getString(R.string.steam_library_controller_support_none)
            }
        }
        val steamInputAvailable = remember(game.appId) {
            runCatching { SteamService.resolveSteamControllerVdfText(game.appId) != null }.getOrDefault(false)
        }
        val canPauseDownload = game.downloadPhase == DownloadPhase.PREPARING ||
            game.downloadPhase == DownloadPhase.VERIFYING ||
            game.downloadPhase == DownloadPhase.PATCHING ||
            game.downloadPhase == DownloadPhase.DOWNLOADING ||
            game.downloadPhase == DownloadPhase.QUEUED

        Card(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Full-screen hero image
                AsyncImage(
                    model = game.heroUrl.ifBlank { game.capsuleUrl },
                    contentDescription = game.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // Full-screen gradient overlay (transparent top → dark bottom)
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.65f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.87f),
                            ),
                        ),
                    ),
                )
                // Close button
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)) {
                    RoundActionButton(Icons.Filled.Close, onClose, size = 40)
                }
                // Game info at top-left
                Column(
                    modifier = Modifier.align(Alignment.TopStart).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (game.logoUrl.isNotBlank()) {
                        AsyncImage(
                            model = game.logoUrl,
                            contentDescription = game.name,
                            modifier = Modifier
                                .height(36.dp)
                                .fillMaxWidth(0.42f),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Text(
                        game.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = if (compactLayout) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (game.subtitle.isNotBlank()) {
                        Text(
                            game.subtitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = if (compactLayout) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Content panel at bottom — compact & transparent
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = if (compactLayout) 6.dp else 8.dp, vertical = if (compactLayout) 4.dp else 6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                        StatusBadge(text = stringResource(R.string.steam_library_tab_steam), solid = true)
                        if (updatePending == true) {
                            StatusBadge(text = stringResource(R.string.steam_library_update_pending_badge))
                        }
                        if (isDownloadSession) {
                            DownloadProgressCard(game = game, compactLayout = compactLayout)
                            if (!game.currentFileName.isNullOrBlank()) {
                                DetailInfoCard(
                                    title = stringResource(R.string.steam_library_current_file),
                                    value = game.currentFileName,
                                )
                            }
                            DetailInfoCard(
                                title = stringResource(R.string.steam_library_install_path),
                                value = game.installPath,
                            )
                        } else if (showInstalledActions) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CompactStatCard(
                                    icon = Icons.Filled.Storage,
                                    label = stringResource(R.string.steam_library_size),
                                    value = "${formatBinarySize(game.downloadSizeBytes)} / ${formatBinarySize(game.installSizeBytes)}",
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.Refresh,
                                    label = stringResource(R.string.steam_library_playtime),
                                    value = playtimeText,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.Check,
                                    label = stringResource(R.string.steam_library_last_played),
                                    value = lastPlayedText,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CompactStatCard(
                                    icon = Icons.Filled.CloudUpload,
                                    label = stringResource(R.string.steam_library_cloud_status),
                                    value = when (cloudDifference) {
                                        true -> stringResource(R.string.steam_library_cloud_changed)
                                        false -> stringResource(R.string.steam_library_cloud_in_sync)
                                        null -> stringResource(R.string.steam_library_cloud_unknown)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.Settings,
                                    label = stringResource(R.string.steam_library_controller_support),
                                    value = controllerSupportLabel,
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.Home,
                                    label = stringResource(R.string.steam_library_workshop_title),
                                    value = if (enabledWorkshopIds.isEmpty()) {
                                        stringResource(R.string.steam_library_workshop_disabled)
                                    } else {
                                        stringResource(
                                            R.string.steam_library_workshop_enabled_count,
                                            enabledWorkshopIds.size,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                CompactStatCard(
                                    icon = Icons.Filled.Storage,
                                    label = stringResource(R.string.steam_library_size),
                                    value = "${formatBinarySize(game.downloadSizeBytes)} / ${formatBinarySize(game.installSizeBytes)}",
                                    modifier = Modifier.weight(1f),
                                )
                                CompactStatCard(
                                    icon = Icons.Filled.FolderOpen,
                                    label = stringResource(R.string.steam_library_available_space),
                                    value = formatBinarySize(game.availableBytes),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            DetailInfoCard(
                                title = stringResource(R.string.steam_library_install_path),
                                value = game.installPath,
                            )
                            if (game.isDownloading || game.downloadProgress > 0f) {
                                DownloadProgressCard(game = game, compactLayout = compactLayout)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (!isDownloadSession) {
                            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(if (compactLayout) 6.dp else 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            ContainerDropdown(
                                                containers = containers,
                                                selectedContainerId = selectedContainerId,
                                                onSelectContainer = onSelectContainer,
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                        if (showFolderPicker) {
                                            OutlinedButton(
                                                onClick = { onPickInstallFolder(game.appId) },
                                                modifier = Modifier.weight(1f).height(36.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.steam_library_choose_folder), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.steam_library_download_status_title),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        StatusBadge(text = game.statusLine)
                                    }
                                    if (!game.currentFileName.isNullOrBlank()) {
                                        Text(
                                            text = "${stringResource(R.string.steam_library_download_file_label)}: ${game.currentFileName}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }

                        if (showInstalledActions) {
                            GradientButton(
                                text = stringResource(R.string.steam_library_play),
                                icon = Icons.Filled.PlayArrow,
                                onClick = {
                                    isLaunchingGame = true
                                    onPlay(game.appId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.Settings,
                                    text = stringResource(R.string.steam_library_settings),
                                    onClick = { onOpenSettings(game.appId, selectedContainerId) },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Home,
                                    text = stringResource(R.string.steam_library_shortcut),
                                    onClick = { onCreateShortcut(game.appId, selectedContainerId) },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.Refresh,
                                    text = stringResource(R.string.steam_library_update),
                                    onClick = {
                                        scope.launch {
                                            val started = withContext(Dispatchers.IO) {
                                                SteamService.downloadApp(
                                                    appId = game.appId,
                                                    downloadableDepots = SteamService.getDownloadableDepots(game.appId),
                                                    userSelectedDlcAppIds = SteamService.getInstalledDlcDepotsOf(game.appId).orEmpty(),
                                                    branch = selectedBranch,
                                                    includeInstalledDepots = true,
                                                    enableVerify = false,
                                                )
                                            }
                                            Toast.makeText(
                                                context,
                                                if (started != null) {
                                                    context.getString(R.string.steam_library_update_started)
                                                } else {
                                                    context.getString(R.string.steam_library_update_failed)
                                                },
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Check,
                                    text = stringResource(R.string.steam_library_verify_files),
                                    onClick = {
                                        scope.launch {
                                            val started = withContext(Dispatchers.IO) {
                                                SteamService.downloadApp(
                                                    appId = game.appId,
                                                    downloadableDepots = SteamService.getDownloadableDepots(game.appId),
                                                    userSelectedDlcAppIds = SteamService.getInstalledDlcDepotsOf(game.appId).orEmpty(),
                                                    branch = selectedBranch,
                                                    includeInstalledDepots = true,
                                                    enableVerify = true,
                                                )
                                            }
                                            Toast.makeText(
                                                context,
                                                if (started != null) {
                                                    context.getString(R.string.steam_library_verify_started)
                                                } else {
                                                    context.getString(R.string.steam_library_verify_failed)
                                                },
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.Storage,
                                    text = stringResource(R.string.steam_library_content_action),
                                    onClick = { showContentManager = true },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.FolderOpen,
                                    text = stringResource(R.string.steam_library_workshop_action),
                                    onClick = { showWorkshopManager = true },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Star,
                                    text = stringResource(R.string.steam_library_achievements),
                                    onClick = { showAchievements = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.Tune,
                                    text = stringResource(R.string.steam_library_change_branch),
                                    onClick = { showBranchPicker = true },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamMenuActionTile(
                                    icon = Icons.Filled.Save,
                                    text = stringResource(R.string.steam_library_saves),
                                    expanded = savesMenuExpanded,
                                    onExpandedChange = { savesMenuExpanded = it },
                                    actions = listOf(
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_export_saves),
                                            icon = Icons.Filled.Save,
                                            onClick = { onExportSaves(game.appId, game.name) },
                                        ),
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_import_saves),
                                            icon = Icons.Filled.FolderOpen,
                                            onClick = { onImportSaves(game.appId) },
                                        ),
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                                SteamMenuActionTile(
                                    icon = Icons.Filled.CloudDownload,
                                    text = stringResource(R.string.steam_library_cloud_saves),
                                    expanded = cloudMenuExpanded,
                                    onExpandedChange = { cloudMenuExpanded = it },
                                    actions = listOf(
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_cloud_upload),
                                            icon = Icons.Filled.CloudUpload,
                                            onClick = { onUploadCloudSaves(game.appId) },
                                        ),
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_cloud_download),
                                            icon = Icons.Filled.CloudDownload,
                                            onClick = { onDownloadCloudSaves(game.appId) },
                                        ),
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_force_cloud_sync),
                                            icon = Icons.Filled.Refresh,
                                            onClick = {
                                                scope.launch {
                                                    val steamId = SteamService.userSteamId
                                                    if (steamId == null) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.steam_library_login_required),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } else {
                                                        val result = withContext(Dispatchers.IO) {
                                                            SteamGameActions.forceCloudSync(context, game.appId)
                                                        }
                                                        val message = when (result) {
                                                            SyncResult.Success -> context.getString(R.string.steam_library_cloud_force_success)
                                                            SyncResult.UpToDate -> context.getString(R.string.steam_library_cloud_force_up_to_date)
                                                            else -> context.getString(R.string.steam_library_cloud_force_failed)
                                                        }
                                                        if (result == SyncResult.Success || result == SyncResult.UpToDate) {
                                                            cloudDifference = false
                                                        }
                                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                        ),
                                        SteamMenuAction(
                                            label = stringResource(R.string.steam_library_browse_online_saves),
                                            icon = Icons.Filled.Search,
                                            onClick = {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://store.steampowered.com/account/remotestorageapp/?appid=${game.appId}"),
                                                    ),
                                                )
                                            },
                                        ),
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                SteamActionTile(
                                    icon = Icons.Filled.PhotoLibrary,
                                    text = stringResource(R.string.steam_library_screenshots),
                                    onClick = {
                                        val steamId64 = SteamService.userSteamId?.convertToUInt64()
                                            ?: PrefManager.steamUserSteamId64
                                        if (steamId64 != 0L) {
                                            val url = "https://steamcommunity.com/profiles/$steamId64/screenshots/?appid=${game.appId}"
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Delete,
                                    text = stringResource(R.string.steam_library_uninstall),
                                    onClick = { onDelete(game.appId) },
                                    modifier = Modifier.weight(1f),
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            }
                        } else {
                            if (game.isDownloading) {
                                GradientButton(
                                    text = if (isPausedDownload) {
                                        stringResource(R.string.steam_library_resume_download)
                                    } else {
                                        stringResource(R.string.steam_library_pause_download)
                                    },
                                    icon = if (isPausedDownload) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    onClick = {
                                        if (canPauseDownload) {
                                            onPauseDownload(game.appId)
                                        } else {
                                            onResumeDownload(game.appId)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Delete,
                                    text = stringResource(R.string.steam_library_cancel_download),
                                    onClick = { onCancelDownload(game.appId) },
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                GradientButton(
                                    text = stringResource(R.string.steam_library_download),
                                    icon = Icons.Filled.CloudDownload,
                                    onClick = { onDownload(game.appId) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                SteamActionTile(
                                    icon = Icons.Filled.Storage,
                                    text = stringResource(R.string.steam_library_content_action),
                                    onClick = { showContentManager = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showContentManager) {
            SteamContentManagerSheet(
                game = game,
                selectedBranch = selectedBranch,
                onDismiss = { showContentManager = false },
                onInstall = { selectedDlcIds ->
                    showContentManager = false
                    scope.launch {
                        val started = withContext(Dispatchers.IO) {
                            SteamService.downloadApp(
                                appId = game.appId,
                                downloadableDepots = SteamService.getDownloadableDepots(game.appId),
                                userSelectedDlcAppIds = selectedDlcIds,
                                branch = selectedBranch,
                                includeInstalledDepots = false,
                                enableVerify = false,
                            )
                        }
                        Toast.makeText(
                            context,
                            if (started != null) {
                                context.getString(R.string.steam_library_content_install_started)
                            } else {
                                context.getString(R.string.steam_library_content_install_failed)
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        if (showWorkshopManager) {
            SteamWorkshopManagerSheet(
                game = game,
                currentEnabledIds = enabledWorkshopIds,
                onDismiss = { showWorkshopManager = false },
                onSave = { enabledIds ->
                    showWorkshopManager = false
                    PrefManager.setSteamWorkshopEnabledItemIds(game.appId, enabledIds)
                    workshopRefreshToken++
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            WorkshopManager.syncEnabledItems(
                                context = context,
                                appId = game.appId,
                                containerId = selectedContainerId,
                                enabledIds = enabledIds,
                            )
                        }
                        val message = when {
                            result.failedCount > 0 -> context.getString(R.string.steam_library_workshop_sync_failed)
                            enabledIds.isEmpty() -> context.getString(R.string.steam_library_workshop_cleared)
                            else -> context.getString(
                                R.string.steam_library_workshop_sync_success,
                                result.syncedCount,
                                result.skippedCount,
                                result.removedCount,
                            )
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }

        if (showBranchPicker) {
            SteamBranchPickerSheet(
                gameName = game.name,
                availableBranches = availableBranches,
                currentBranch = selectedBranch,
                onDismiss = { showBranchPicker = false },
                onConfirm = { branch ->
                    selectedBranch = branch
                    PrefManager.setSteamSelectedBranch(game.appId, branch)
                    showBranchPicker = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.steam_library_branch_selected, branch),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }

        if (showAchievements && game != null) {
            AchievementsSheet(appId = game.appId, onDismiss = { showAchievements = false })
        }

        if (game != null && isLaunchingGame) {
            SteamLaunchOverlay(game = game)
        }

    }
}
}

@Composable
private fun DownloadProgressCard(game: SteamGameDetailUi, compactLayout: Boolean) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.steam_library_download_panel_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
                StatusBadge(text = game.statusLine)
            }
            LinearProgressIndicator(
                progress = { game.downloadProgress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            DetailMiniLine(stringResource(R.string.steam_library_downloaded), buildProgressText(game.downloadedBytes, game.totalBytes))
            DetailMiniLine(stringResource(R.string.steam_library_speed), formatSpeed(game.speedBytesPerSec))
            DetailMiniLine(stringResource(R.string.steam_library_eta), formatEta(game.etaMs))
            if (!game.currentFileName.isNullOrBlank()) {
                Text(
                    text = "${stringResource(R.string.steam_library_download_file_label)}: ${game.currentFileName}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SteamLaunchOverlay(game: SteamGameDetailUi) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.62f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp),
                ) {
                    AsyncImage(
                        model = game.heroUrl.ifBlank { game.capsuleUrl },
                        contentDescription = game.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.18f),
                                        Color.Black.copy(alpha = 0.34f),
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                                ),
                            ),
                    )
                    StatusBadge(
                        text = stringResource(R.string.steam_library_launching_title),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = game.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (game.subtitle.isNotBlank()) {
                            Text(
                                text = game.subtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.steam_library_launching_status),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.4.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.steam_library_launching_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerDropdown(
    containers: List<SteamContainerOption>,
    selectedContainerId: Int?,
    onSelectContainer: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedContainer = containers.firstOrNull { it.id == selectedContainerId }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(36.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = selectedContainer?.name ?: stringResource(R.string.steam_library_select_container),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            containers.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onSelectContainer(option.id)
                    },
                )
            }
        }
    }
}
@Composable
private fun StatusBadge(text: String, modifier: Modifier = Modifier, solid: Boolean = false) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).background(
            if (solid) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ).padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = if (solid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun DetailInfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailMiniLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompactStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(
            MaterialTheme.colorScheme.primary,
        ).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            Text(text, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DeleteGameButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.steam_library_delete_game),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private data class SteamMenuAction(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun SteamActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SteamMenuActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<SteamMenuAction>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SteamActionTile(
            icon = icon,
            text = text,
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label) },
                    leadingIcon = { Icon(action.icon, contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        action.onClick()
                    },
                )
            }
        }
    }
}

private fun formatBinarySize(bytes: Long): String {
    return if (bytes > 0L) StorageUtils.formatBinarySize(bytes) else "--"
}

private fun buildProgressText(downloadedBytes: Long, totalBytes: Long): String {
    return if (totalBytes > 0L) {
        "${StorageUtils.formatBinarySize(downloadedBytes)} / ${StorageUtils.formatBinarySize(totalBytes)}"
    } else if (downloadedBytes > 0L) {
        StorageUtils.formatBinarySize(downloadedBytes)
    } else {
        "--"
    }
}

private fun formatSpeed(bytesPerSecond: Long?): String {
    return if (bytesPerSecond != null && bytesPerSecond > 0L) {
        "${StorageUtils.formatBinarySize(bytesPerSecond, 1)}/s"
    } else {
        "--"
    }
}

private fun formatEta(etaMs: Long?): String {
    if (etaMs == null || etaMs <= 0L) return "--"
    return DateUtils.formatElapsedTime(etaMs / 1000L)
}

private fun formatReleaseDate(releaseDateSeconds: Long): String {
    if (releaseDateSeconds <= 0L) return "--"
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(releaseDateSeconds * 1000L))
}

private fun formatPlaytimeHours(minutes: Int): String {
    if (minutes <= 0) return "--"
    if (minutes < 60) return "${minutes}m"

    val hours = minutes / 60
    if (hours < 24) return "${hours}h"

    val days = hours / 24
    val remainingHours = hours % 24
    return if (remainingHours == 0) {
        "${days}d"
    } else {
        "${days}d ${remainingHours}h"
    }
}

private fun formatSteamTimestamp(timestampSeconds: Int): String {
    if (timestampSeconds <= 0) return "--"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(timestampSeconds * 1000L))
}

private fun formatInstallTimestamp(path: String): String {
    if (path.isBlank()) return "--"
    val target = File(path)
    val lastModified = when {
        target.isFile -> target.lastModified()
        target.isDirectory -> target.lastModified()
        else -> 0L
    }
    if (lastModified <= 0L) return "--"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(lastModified))
}
