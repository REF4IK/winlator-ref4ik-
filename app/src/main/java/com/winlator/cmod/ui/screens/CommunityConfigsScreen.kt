package com.winlator.cmod.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.BuildConfig
import com.winlator.cmod.R
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.core.gameconfig.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private enum class StoreFilter { ALL, STEAM, TITLE }
private enum class SortMode { CONFIGS, NAME, DEVICES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityConfigsScreen(onBack: () -> Unit = {}, contextShortcut: com.winlator.cmod.container.Shortcut? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { ContainerManager(ctx) }
    val configIndex = remember { ConfigIndex() }
    val gameMatcher = remember { GameMatcher() }

    var catalog by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var matchesMyDevice by remember { mutableStateOf(false) }
    var storeFilter by remember { mutableStateOf(StoreFilter.ALL) }
    var sort by remember { mutableStateOf(SortMode.CONFIGS) }
    var selectedIdentity by remember { mutableStateOf<String?>(null) }
    var selectedGameName by remember { mutableStateOf("") }

    var configEntries by remember { mutableStateOf<List<ConfigFileEntry>>(emptyList()) }
    var configsLoading by remember { mutableStateOf(false) }
    var configRefreshTrigger by remember { mutableStateOf(0) }

    var detailConfig by remember { mutableStateOf<GameConfig?>(null) }
    var detailEntry by remember { mutableStateOf<ConfigFileEntry?>(null) }
    var showDetail by remember { mutableStateOf(false) }
    var showContainerPicker by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var showApplyProgress by remember { mutableStateOf(false) }
    var applyProgressText by remember { mutableStateOf("") }

    val deviceMatcher = remember { DeviceMatcher() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            configIndex.getIndex(ctx.filesDir) { idx ->
                catalog = idx
                loading = false
            }
        }
    }

    val games = remember(catalog) {
        val list = mutableListOf<GameCandidate>()
        if (catalog != null) {
            for (key in catalog!!.keys()) {
                val entry = catalog!!.optJSONObject(key) ?: continue
                list.add(GameCandidate(
                    key = key,
                    name = entry.optString("name", ""),
                    folders = mutableListOf(),
                    configCount = entry.optInt("config_count", 0),
                    score = 0
                ))
            }
        }
        list
    }

    val visible = remember(games, query, matchesMyDevice, storeFilter, sort) {
        var base = if (query.trim().length >= 2) gameMatcher.search(query, catalog) else games
        base = base.filter { true }
        if (matchesMyDevice) {
            base = base.filter { g ->
                val entry = catalog?.optJSONObject(g.key) ?: return@filter false
                val devs = entry.optJSONArray("devices")
                if (devs == null) return@filter false
                for (i in 0 until devs.length()) {
                    val d = devs.optJSONObject(i) ?: continue
                    val m = d.optString("m", "")
                    val s = d.optString("s", "")
                    val result = deviceMatcher.match(m, s)
                    if (result.score > 0) return@filter true
                }
                false
            }
        }
        when (sort) {
            SortMode.CONFIGS -> base.sortedByDescending { it.configCount }
            SortMode.NAME -> base.sortedBy { it.name.lowercase() }
            SortMode.DEVICES -> base.sortedByDescending {
                catalog?.optJSONObject(it.key)?.optJSONArray("devices")?.length() ?: 0
            }
        }
    }

    val selectedGame = selectedIdentity?.let { id -> games.firstOrNull { it.key == id } }

    if (showApplyProgress) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.config_apply)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(applyProgressText, fontSize = 14.sp)
                }
            },
            confirmButton = {}
        )
    }

    if (showDetail && detailConfig != null && detailEntry != null) {
        val shortcut = contextShortcut
        ConfigDetailDialog(
            config = detailConfig!!,
            entry = detailEntry!!,
            gameName = selectedGameName,
            deviceMatcher = deviceMatcher,
            onDismiss = { showDetail = false },
            onApply = {
                showDetail = false
                if (shortcut != null) {
                    showApplyProgress = true
                    applyProgressText = ctx.getString(R.string.bundle_downloading)
                    scope.launch(Dispatchers.IO) {
                        try {
                            val bundleUrl = detailEntry?.bundleUrl
                            if (!bundleUrl.isNullOrEmpty()) {
                                val latch = java.util.concurrent.CountDownLatch(1)
                                var applyError: String? = null
                                val applier = BundledConfigApplier(ctx)
                                applier.applyFromUrl(bundleUrl, object : BundledConfigApplier.ApplyCallback {
                                    override fun onProgress(status: String) {
                                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) { applyProgressText = status }
                                    }
                                    override fun onComplete(success: Boolean, message: String, config: GameConfig?) {
                                        if (!success) applyError = message
                                        latch.countDown()
                                    }
                                })
                                latch.await(120, java.util.concurrent.TimeUnit.SECONDS)
                                if (applyError != null) {
                                    withContext(Dispatchers.Main) { showApplyProgress = false; statusMessage = "Error: $applyError" }
                                    return@launch
                                }
                            }
                            withContext(Dispatchers.Main) { applyProgressText = "Применение настроек..." }
                            val cs = detailConfig!!.containerSettings
                            fun put(key: String, value: String) { if (value.isNotEmpty()) shortcut.putExtra(key, value) }
                            cs?.let { s ->
                                put("dxwrapper", s.optString("dxwrapper"))
                                put("dxwrapperConfig", s.optString("dxwrapperConfig"))
                                put("graphicsDriver", s.optString("graphicsDriver"))
                                put("graphicsDriverConfig", s.optString("graphicsDriverConfig"))
                                put("displayRenderer", s.optString("displayRenderer"))
                                put("audioDriver", s.optString("audioDriver"))
                                put("audioDriverConfig", s.optString("audioDriverConfig"))
                                put("box64Version", s.optString("box64Version"))
                                put("box64Preset", s.optString("box64Preset"))
                                put("fexcoreVersion", s.optString("fexcoreVersion"))
                                put("fexcorePreset", s.optString("fexcorePreset"))
                                put("emulator", s.optString("emulator"))
                                put("screenSize", s.optString("screenSize").split(" ")[0])
                                put("envVars", s.optString("envVars"))
                            }
                            shortcut.saveData()
                            val dlSha = detailEntry?.sha
                            if (dlSha != null) {
                                try {
                                    val dlBody = org.json.JSONObject().apply { put("sha", dlSha) }
                                    val conn = java.net.URL(BuildConfig.CLOUDFLARE_WORKER_URL + "/api/download").openConnection() as java.net.HttpURLConnection
                                    conn.requestMethod = "POST"
                                    conn.setRequestProperty("Content-Type", "application/json")
                                    conn.doOutput = true
                                    conn.outputStream.write(dlBody.toString().toByteArray())
                                    conn.responseCode
                                    conn.disconnect()
                                } catch (_: Exception) {}
                            }
                            withContext(Dispatchers.Main) {
                                showApplyProgress = false
                                statusMessage = "Applied to ${shortcut.name}"
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                showApplyProgress = false
                                statusMessage = "Error: ${e.message}"
                            }
                        }
                    }
                } else {
                    showContainerPicker = true
                }
            }
        )
    }

    if (showContainerPicker && detailConfig != null) {
        val containers = remember { manager.containers }
        ContainerPickerDialog(
            containers = containers,
            onDismiss = { showContainerPicker = false },
            onPick = { container ->
                showContainerPicker = false
                scope.launch(Dispatchers.IO) {
                    try {
                        GameConfigManager.applyGameConfig(detailConfig!!, container, null)
                        withContext(Dispatchers.Main) {
                            statusMessage = "Applied to ${container.name}"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            statusMessage = "Error: ${e.message}"
                        }
                    }
                }
            }
        )
    }

    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedGame != null) {
                        IconButton(onClick = {
                            selectedIdentity = null
                            configEntries = emptyList()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.community_back))
                        }
                    }
                    Icon(
                        Icons.Default.GridView,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp).padding(end = 8.dp),
                    )
                    Text(
                        text = selectedGame?.name ?: stringResource(R.string.community_configs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    var refreshingState by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            refreshingState = true
                            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                configIndex.refreshIndex(ctx.filesDir) { idx ->
                                    catalog = idx
                                    refreshingState = false
                                }
                                if (selectedGameName.isNotEmpty()) {
                                    configsLoading = true
                                    configRefreshTrigger++
                                    fetchGameConfigs(selectedGameName) { files ->
                                        configEntries = files
                                        configsLoading = false
                                    }
                                }
                            }
                        },
                        enabled = !refreshingState
                    ) {
                        if (refreshingState) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel)) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth >= 600.dp
                    when {
                        loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        selectedGame != null -> DevicePanel(
                            gameName = selectedGame.name,
                            entries = configEntries,
                            loading = configsLoading,
                            matchesMyDevice = matchesMyDevice,
                            deviceMatcher = deviceMatcher,
                            onToggleDevice = { matchesMyDevice = !matchesMyDevice },
                            onEntryClick = { entry ->
                                detailEntry = entry
                                selectedGameName = selectedGame.name
                                scope.launch(Dispatchers.IO) {
                                    CloudConfigRepoV2.fetchConfigFile(entry.downloadUrl) { config ->
                                        detailConfig = config
                                        showDetail = true
                                    }
                                }
                            },
                            wide = wide,
                        )
                        else -> CatalogPanel(
                            query = query,
                            onQueryChange = { query = it },
                            storeFilter = storeFilter,
                            onStoreFilterChange = { storeFilter = it },
                            matchesMyDevice = matchesMyDevice,
                            onToggleDevice = { matchesMyDevice = !matchesMyDevice },
                            sort = sort,
                            onSortChange = { sort = it },
                            visible = visible,
                            onGameClick = { candidate ->
                                selectedIdentity = candidate.key
                                selectedGameName = candidate.name
                                configsLoading = true
                                scope.launch(Dispatchers.IO) {
                                    fetchGameConfigs(candidate.name) { files ->
                                        configEntries = files
                                        configsLoading = false
                                    }
                                }
                            },
                            wide = wide,
                        )
                    }
                }

                if (statusMessage.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        statusMessage,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    storeFilter: StoreFilter,
    onStoreFilterChange: (StoreFilter) -> Unit,
    matchesMyDevice: Boolean,
    onToggleDevice: () -> Unit,
    sort: SortMode,
    onSortChange: (SortMode) -> Unit,
    visible: List<GameCandidate>,
    onGameClick: (GameCandidate) -> Unit,
    wide: Boolean,
) {
    @Composable
    fun Controls(modifier: Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.community_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = storeFilter == StoreFilter.ALL,
                            onClick = { onStoreFilterChange(StoreFilter.ALL) },
                            label = { Text(stringResource(R.string.all), fontSize = 12.sp) },
                        )
                        FilterChip(
                            selected = storeFilter == StoreFilter.STEAM,
                            onClick = { onStoreFilterChange(StoreFilter.STEAM) },
                            label = { Text("Steam", fontSize = 12.sp) },
                        )
                        FilterChip(
                            selected = storeFilter == StoreFilter.TITLE,
                            onClick = { onStoreFilterChange(StoreFilter.TITLE) },
                            label = { Text("Title", fontSize = 12.sp) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = matchesMyDevice,
                            onClick = onToggleDevice,
                            label = { Text(stringResource(R.string.community_filter_device), fontSize = 12.sp) },
                            leadingIcon = if (matchesMyDevice) {{ Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp)) }} else null,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(stringResource(R.string.community_sort_label), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = sort == SortMode.CONFIGS,
                            onClick = { onSortChange(SortMode.CONFIGS) },
                            label = { Text(stringResource(R.string.community_sort_configs), fontSize = 12.sp) },
                        )
                        FilterChip(
                            selected = sort == SortMode.NAME,
                            onClick = { onSortChange(SortMode.NAME) },
                            label = { Text(stringResource(R.string.name), fontSize = 12.sp) },
                        )
                        FilterChip(
                            selected = sort == SortMode.DEVICES,
                            onClick = { onSortChange(SortMode.DEVICES) },
                            label = { Text(stringResource(R.string.community_sort_devices), fontSize = 12.sp) },
                        )
                    }
                    Text(
                        stringResource(R.string.community_games_count, visible.size),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    @Composable
    fun GameList(modifier: Modifier) {
        if (visible.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SearchOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Text(stringResource(R.string.community_no_match), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.key }) { g ->
                    Card(
                        onClick = { onGameClick(g) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            FilledIconButton(
                                onClick = { onGameClick(g) },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.size(40.dp),
                            ) { Icon(Icons.Default.Gamepad, null, modifier = Modifier.size(22.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(g.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${g.configCount} configs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Controls(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp))
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            GameList(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Controls(Modifier.padding(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            GameList(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun DevicePanel(
    gameName: String,
    entries: List<ConfigFileEntry>,
    loading: Boolean,
    matchesMyDevice: Boolean,
    deviceMatcher: DeviceMatcher,
    onToggleDevice: () -> Unit,
    onEntryClick: (ConfigFileEntry) -> Unit,
    wide: Boolean,
) {
    @Composable
    fun Header() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledIconButton(
                    onClick = {},
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.size(40.dp),
                ) { Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(gameName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("${entries.size} configs", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilterChip(
                    selected = matchesMyDevice,
                    onClick = onToggleDevice,
                    label = { Text(stringResource(R.string.community_filter_device), fontSize = 11.sp) },
                    leadingIcon = if (matchesMyDevice) {{ Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(14.dp)) }} else null,
                )
            }
        }
    }

    @Composable
    fun ConfigList(modifier: Modifier) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.community_loading_configs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            } else if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.community_no_configs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val shown = if (!matchesMyDevice) entries
                else entries.filter { deviceMatcher.match(it.device, it.soc).score > 0 }

                if (shown.isEmpty()) {
                    Text(stringResource(R.string.community_no_configs_device), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
                } else {
                    val dateFmt = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
                    shown.forEach { entry ->
                        val match = deviceMatcher.match(entry.device, entry.soc)
                        Card(
                            onClick = { onEntryClick(entry) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    FilledIconButton(
                                        onClick = { onEntryClick(entry) },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = if (match.score > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                            contentColor = if (match.score > 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier.size(36.dp),
                                    ) { Icon(if (entry.bundleUrl.isNotEmpty()) Icons.Default.Extension else Icons.Default.Tune, null, modifier = Modifier.size(20.dp)) }
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.device.ifBlank { entry.name },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = if (match.score > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                        val pieces = mutableListOf<String>()
                                        if (entry.soc.isNotEmpty()) pieces.add(entry.soc)
                                        if (entry.timestamp > 0) pieces.add(dateFmt.format(Date(entry.timestamp)))
                                        if (pieces.isNotEmpty()) Text(pieces.joinToString(" · "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (match.score > 0) {
                                        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                                            Text(stringResource(R.string.community_match_badge), fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Default.ThumbUp, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("${entry.votesUp}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Icon(Icons.Default.ThumbDown, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${entry.votesDown}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (entry.downloads > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                            Text("${entry.downloads}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    if (entry.bundleUrl.isNotEmpty()) {
                                        Surface(color = MaterialTheme.colorScheme.tertiary, shape = RoundedCornerShape(4.dp)) {
                                            Text(stringResource(R.string.config_preview), fontSize = 9.sp, color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(280.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp)) {
                Header()
            }
            Box(Modifier.fillMaxHeight().width(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
            ConfigList(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Header()
            ConfigList(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConfigDetailDialog(
    config: GameConfig,
    entry: ConfigFileEntry,
    gameName: String,
    deviceMatcher: DeviceMatcher,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val ctx = LocalContext.current
    val social = remember { SocialManager() }
    var votesUp by remember { mutableStateOf(entry.votesUp) }
    var votesDown by remember { mutableStateOf(entry.votesDown) }
    var voted by remember { mutableStateOf(social.hasVoted(entry.sha)) }
    var voting by remember { mutableStateOf(false) }
    var comments by remember { mutableStateOf<JSONArray?>(null) }
    var commentText by remember { mutableStateOf("") }
    var commenting by remember { mutableStateOf(false) }

    LaunchedEffect(entry.sha) {
        withContext(Dispatchers.IO) {
            social.getComments(entry.sha) { c -> comments = c }
        }
    }

    val matchResult = deviceMatcher.match(entry.device, entry.soc)
    val dateStr = if (config.exportedAt > 0) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(config.exportedAt)) else ""

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (entry.bundleUrl.isNotEmpty()) Icons.Default.Extension else Icons.Default.Tune,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp).padding(end = 8.dp),
                    )
                    Text(stringResource(R.string.community_config_details), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Provenance card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(config.gameName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            val hw = config.device.ifBlank { "" }
                            if (hw.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(hw, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (dateStr.isNotEmpty()) dateStr else "—", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (config.description.isNotBlank()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                Text(config.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (matchResult.score > 0) {
                                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(6.dp)) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                        Text(stringResource(R.string.community_matches_device), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }

                    // Settings card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(stringResource(R.string.community_what_sets), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            config.containerSettings?.let { cs ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    @Composable fun row(label: String, value: String) {
                                        val clean = value.substringBefore(";").substringBefore(",").trim()
                                        if (clean.isBlank()) return
                                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp))
                                            Text(clean, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    if (cs.has("wineVersion") && cs.optString("wineVersion").isNotBlank()) row("Wine", cs.optString("wineVersion"))
                                    if (cs.has("screenSize") && cs.optString("screenSize").isNotBlank()) row("Screen", cs.optString("screenSize"))
                                    parseVersion(cs.optString("dxwrapperConfig", ""), "version")?.let { row("DXVK", it) }
                                    parseVersion(cs.optString("dxwrapperConfig", ""), "vkd3dVersion")?.let { row("VKD3D", it) }
                                    val wrapper = cs.optString("graphicsDriver", "")
                                    val gpuRaw = cs.optString("graphicsDriverConfig", "")
                                    val gpuVer = com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog.getVersion(gpuRaw)
                                    if (gpuVer != null && gpuVer.isNotEmpty()) {
                                        row(stringResource(R.string.driver), listOfNotNull(wrapper.ifBlank { null }, gpuVer).joinToString(" · "))
                                    }
                                    if (cs.has("audioDriver") && cs.optString("audioDriver").isNotBlank()) row("Audio", cs.optString("audioDriver"))
                                    if (cs.has("displayRenderer") && cs.optString("displayRenderer").isNotBlank()) row("Render", cs.optString("displayRenderer"))
                                    if (cs.has("emulator") && cs.optString("emulator").isNotBlank()) row("Translator", cs.optString("emulator"))
                                    val b64 = if (cs.has("box64Version")) cs.optString("box64Version") else ""
                                    val b64p = if (cs.has("box64Preset")) cs.optString("box64Preset") else ""
                                    if (b64.isNotBlank() || b64p.isNotBlank()) row("Box64", listOfNotNull(b64.ifBlank { null }, b64p.ifBlank { null }).joinToString(" · "))
                                    val fex = if (cs.has("fexcoreVersion")) cs.optString("fexcoreVersion") else ""
                                    val fexp = if (cs.has("fexcorePreset")) cs.optString("fexcorePreset") else ""
                                    if (fex.isNotBlank() || fexp.isNotBlank()) row("FEX", listOfNotNull(fex.ifBlank { null }, fexp.ifBlank { null }).joinToString(" · "))
                                    if (cs.has("envVars") && cs.optString("envVars").isNotBlank()) {
                                        val envStr = cs.optString("envVars")
                                        val clean = envStr.substringBefore(";").substringBefore(",").trim()
                                        if (clean.isNotBlank()) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                            Text("Env vars", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            clean.split(" ").filter { it.isNotBlank() }.forEach { env ->
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text("•", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                                    Text(env, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Components card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(stringResource(R.string.components), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                            if (entry.bundleUrl.isNotEmpty()) {
                                Surface(color = MaterialTheme.colorScheme.tertiary, shape = RoundedCornerShape(6.dp)) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onTertiary)
                                        Text(stringResource(R.string.config_preview), fontSize = 12.sp, color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Medium)
                                    }
                                }
                            } else {
                                val resolver = remember { ComponentResolver(ctx) }
                                var components by remember { mutableStateOf<List<ComponentResolver.ComponentStatus>>(emptyList()) }
                                var componentsLoaded by remember { mutableStateOf(false) }
                                LaunchedEffect(config.containerSettings) {
                                    withContext(Dispatchers.IO) {
                                        resolver.resolve(config.containerSettings) { result ->
                                            components = result; componentsLoaded = true
                                        }
                                    }
                                }
                                if (!componentsLoaded) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text("Checking...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val missingCnt = components.count { !it.installed && it.downloadable }
                                        var installStats by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
                                        var curDl by remember { mutableStateOf<String?>(null) }
                                        var dlMsg by remember { mutableStateOf<String?>(null) }

                                        components.forEach { comp ->
                                            val res = installStats[comp.label]
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilledIconButton(
                                                    onClick = {},
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = when {
                                                            comp.installed || res == "ok" -> MaterialTheme.colorScheme.primaryContainer
                                                            res == "fail" -> MaterialTheme.colorScheme.errorContainer
                                                            else -> MaterialTheme.colorScheme.surfaceContainer
                                                        },
                                                        contentColor = when {
                                                            comp.installed || res == "ok" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                            res == "fail" -> MaterialTheme.colorScheme.onErrorContainer
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                    ),
                                                    modifier = Modifier.size(32.dp),
                                                ) {
                                                    Icon(
                                                        when {
                                                            comp.installed || res == "ok" -> Icons.Default.Check
                                                            res == "fail" -> Icons.Default.Close
                                                            else -> Icons.Default.Download
                                                        },
                                                        null,
                                                        modifier = Modifier.size(16.dp),
                                                    )
                                                }
                                                Text("${comp.label} ${comp.version}", fontSize = 13.sp,
                                                    color = when { comp.installed || res == "ok" -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface },
                                                    modifier = Modifier.weight(1f))
                                                if (curDl?.startsWith(comp.label) == true) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                            }
                                        }

                                        if (dlMsg != null) Text(dlMsg!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (missingCnt > 0 && installStats.isEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Button(
                                                    onClick = {
                                                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                                            val st = mutableMapOf<String, String>()
                                                            val todo = components.filter { !it.installed && it.downloadable }
                                                            for ((i, comp) in todo.withIndex()) {
                                                                withContext(Dispatchers.Main) { curDl = comp.label; dlMsg = "Downloading ${i+1}/${todo.size}: ${comp.label}..." }
                                                                val latch = java.util.concurrent.CountDownLatch(1)
                                                                try {
                                                                    ComponentResolver.installComponent(comp, ctx, object : ComponentResolver.InstallCallback {
                                                                        override fun onComplete(success: Boolean, message: String?) {
                                                                            st[comp.label] = if (success) "ok" else "fail"
                                                                            latch.countDown()
                                                                        }
                                                                    })
                                                                } catch (e: Exception) {
                                                                    st[comp.label] = "fail"
                                                                    latch.countDown()
                                                                }
                                                                latch.await(60, java.util.concurrent.TimeUnit.SECONDS)
                                                            }
                                                            withContext(Dispatchers.Main) {
                                                                installStats = st; curDl = null
                                                                val ok = st.count { it.value == "ok" }
                                                                val fail = st.count { it.value == "fail" }
                                                                dlMsg = "$ok installed, $fail failed"
                                                                if (fail == 0) { dlMsg = null; onApply() }
                                                            }
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                ) { Text("Download All ($missingCnt)") }
                                                OutlinedButton(onClick = { onApply() }) { Text("Skip") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Social card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text("Rating", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.ThumbUp, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.community_votes, votesUp), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.ThumbDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$votesDown", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                                    Text("${entry.downloads}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        if (voted || voting) return@FilledTonalButton
                                        voting = true
                                        social.vote(entry.sha, true) { success, up, down, err ->
                                            voting = false
                                            if (success) { votesUp = up; votesDown = down; voted = true }
                                        }
                                    },
                                    enabled = !voted && !voting,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                ) {
                                    if (voting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else {
                                        Icon(Icons.Default.ThumbUp, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (voted) stringResource(R.string.community_voted) else stringResource(R.string.community_upvote))
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        if (voted || voting) return@OutlinedButton
                                        voting = true
                                        social.vote(entry.sha, false) { success, up, down, err ->
                                            voting = false
                                            if (success) { votesUp = up; votesDown = down; voted = true }
                                        }
                                    },
                                    enabled = !voted && !voting,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) {
                                    Icon(Icons.Default.ThumbDown, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.community_downvote))
                                }
                            }
                        }
                    }

                    // Comments card
                    if (!entry.sha.isNullOrBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Text(stringResource(R.string.community_comments), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                }

                                val commentList = comments
                                if (commentList == null || commentList.length() == 0) {
                                    Text(stringResource(R.string.community_no_comments), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (i in 0 until commentList.length()) {
                                            val c = commentList.optJSONObject(i) ?: continue
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp),
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Icon(Icons.Default.Person, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                        val nick = c.optString("nickname", "")
                                                        val date = c.optString("date", "").take(10)
                                                        val head = listOf(nick, date).filter { it.isNotBlank() }.joinToString(" · ")
                                                        if (head.isNotBlank()) Text(head, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                    Text(c.optString("text", ""), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                                }
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = { if (it.length <= 500) commentText = it },
                                        placeholder = { Text(stringResource(R.string.community_add_comment), fontSize = 13.sp) },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    FilledIconButton(
                                        onClick = {
                                            val text = commentText.trim()
                                            if (text.isEmpty() || commenting) return@FilledIconButton
                                            commenting = true
                                            val nick = Build.MANUFACTURER + "_" + Build.MODEL
                                            social.postComment(entry.sha, text, nick) { success, err ->
                                                commenting = false
                                                if (success) {
                                                    commentText = ""
                                                    social.getComments(entry.sha) { c -> comments = c }
                                                }
                                            }
                                        },
                                        enabled = commentText.isNotBlank() && !commenting,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    ) {
                                        if (commenting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                        else Icon(Icons.Default.Send, null)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApply, enabled = config != null) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.config_apply))
                    }
                }
            }
        }
    }
}

private data class ConfigFileEntry(
    val name: String,
    val downloadUrl: String,
    val sha: String = "",
    val votesUp: Int = 0,
    val votesDown: Int = 0,
    val downloads: Int = 0,
    val device: String = "",
    val soc: String = "",
    val timestamp: Long = 0,
    val bundleUrl: String = "",
)

private fun parseVersion(configStr: String, key: String): String? {
    if (configStr.isBlank()) return null
    for (part in configStr.split("[,;]")) {
        val kv = part.split("=", limit = 2)
        if (kv.size == 2 && kv[0].trim() == key) return kv[1].trim().ifEmpty { null }
    }
    return null
}

private fun fetchGameConfigs(gameName: String, callback: (List<ConfigFileEntry>) -> Unit) {
    val RAW_BASE = "https://raw.githubusercontent.com"

    BundleRepoClient.fetchBundles(gameName) { bundles ->
        CloudConfigRepoV2.fetchConfigsForGame(gameName) { configFiles ->
            val list = mutableListOf<ConfigFileEntry>()

            if (bundles != null) {
                for (i in 0 until bundles.length()) {
                    try {
                        val b = bundles.getJSONObject(i)
                        val sha = b.optString("sha", "")
                        val configUrl = b.optString("configUrl", "")
                        val zipUrl = b.optString("zipUrl", "")
                        val device = b.optString("device", "")
                        val gpu = b.optString("gpu", "")
                        val voteUp = b.optInt("votes_up", 0)
                        val voteDown = b.optInt("votes_down", 0)
                        val dlCount = b.optInt("downloads", 0)
                        val createdAt = b.optString("createdAt", "")
                        val ts = try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(createdAt)?.time ?: 0 } catch (_: Exception) { 0 }
                        if (configUrl.isNotEmpty()) {
                            val displayName = device.ifEmpty { "Bundle-${sha.take(8)}" }
                            list.add(ConfigFileEntry(
                                name = displayName,
                                downloadUrl = configUrl,
                                sha = sha,
                                votesUp = voteUp,
                                votesDown = voteDown,
                                downloads = dlCount,
                                device = device,
                                soc = gpu,
                                timestamp = ts,
                                bundleUrl = zipUrl,
                            ))
                        }
                    } catch (_: Exception) {}
                }
            }

            if (configFiles != null) {
                for (i in 0 until configFiles.length()) {
                    try {
                        val obj = configFiles.getJSONObject(i)
                            val filename = obj.optString("filename", "")
                            val sha = obj.optString("sha", "")
                            val device = obj.optString("device", "")
                            val soc = obj.optString("soc", "")
                            val votesUp = obj.optInt("votes_up", 0)
                            val votesDown = obj.optInt("votes_down", 0)
                            val dateStr = obj.optString("date", "")
                            val ts = try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(dateStr)?.time ?: 0 } catch (_: Exception) { 0 }
                            if (filename.isNotEmpty()) {
                                val url = "$RAW_BASE/REF4IK/winlator-ref4ik-configs/main/configs/" +
                                    gameName.replace(Regex("[^a-zA-Z0-9_]"), "_") + "/$filename"
                                list.add(ConfigFileEntry(filename, url, sha, votesUp, votesDown, device = device, soc = soc, timestamp = ts))
                            }
                    } catch (_: Exception) {}
                }
            }
            callback(list)
        }
    }
}
