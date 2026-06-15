package com.winlator.cmod.steam

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import coil.compose.AsyncImage
import com.winlator.cmod.R
import com.winlator.cmod.steam.data.DepotInfo
import com.winlator.cmod.steam.service.SteamService
import com.winlator.cmod.steam.ui.SteamGameDetailUi
import com.winlator.cmod.steam.utils.PrefManager
import com.winlator.cmod.steam.workshop.WorkshopItem
import com.winlator.cmod.steam.workshop.WorkshopManager
import com.winlator.cmod.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SteamSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.steam_library_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF121722),
                unfocusedContainerColor = Color(0xFF121722),
                focusedBorderColor = Color(0xFF284A70),
                unfocusedBorderColor = Color(0xFF22354F),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedPlaceholderColor = Color(0xFF8091AA),
                unfocusedPlaceholderColor = Color(0xFF8091AA),
            ),
            shape = RoundedCornerShape(18.dp),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF141A24))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
fun SteamContentManagerSheet(
    game: SteamGameDetailUi,
    selectedBranch: String,
    onDismiss: () -> Unit,
    onInstall: (List<Int>) -> Unit,
) {
    val context = LocalContext.current
    var loading by remember(game.appId, selectedBranch) { mutableStateOf(true) }
    var loadFailed by remember(game.appId, selectedBranch) { mutableStateOf(false) }
    val dlcApps = remember(game.appId) { mutableStateListOf<Pair<Int, String>>() }
    val selectedDlcIds = remember(game.appId) { mutableStateMapOf<Int, Boolean>() }
    val installableDlcIds = remember(game.appId) { mutableStateMapOf<Int, Boolean>() }
    val perItemSizeText = remember(game.appId, selectedBranch) { mutableStateMapOf<Int, Pair<String, String>>() }
    var baseGameSizeText by remember(game.appId, selectedBranch) { mutableStateOf("--" to "--") }
    var availableSpaceBytes by remember(game.appId, selectedBranch) { mutableStateOf(0L) }
    var totalDownloadSizeText by remember(game.appId, selectedBranch) { mutableStateOf("--") }
    var totalInstallSizeText by remember(game.appId, selectedBranch) { mutableStateOf("--") }
    val installedDlcIds = remember(game.appId) {
        SteamService.getInstalledDlcDepotsOf(game.appId).orEmpty().toSet()
    }
    val isInstalled = remember(game.appId) { SteamService.isAppInstalled(game.appId) }

    LaunchedEffect(game.appId, selectedBranch) {
        loading = true
        loadFailed = false
        dlcApps.clear()
        selectedDlcIds.clear()
        installableDlcIds.clear()
        perItemSizeText.clear()

        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (!PrefManager.steamOfflineMode && SteamService.isConnected && SteamService.isLoggedIn) {
                    SteamService.refreshAppMetadataFromSteam(game.appId, refreshRelatedDlcs = true)
                }

                // Use the comprehensive WinNative-style function
                val selectableDlcApps = SteamService.getSelectableDlcAppsOf(game.appId)
                val availableBytes = runCatching {
                    StorageUtils.getAvailableSpace(SteamService.getAppDirPath(game.appId))
                }.getOrDefault(0L)

                val dlcEntries = mutableListOf<SteamContentDlcEntry>()
                val dlcSizes = mutableMapOf<Int, Pair<String, String>>()

                for (dlcApp in selectableDlcApps) {
                    val dlcDepots = SteamService.getDownloadableDepots(dlcApp.id)
                        .values
                        .map { depot ->
                            if (depot.dlcAppId == SteamService.INVALID_APP_ID) {
                                depot.copy(dlcAppId = dlcApp.id)
                            } else {
                                depot
                            }
                        }

                    val defaultSelected = installedDlcIds.contains(dlcApp.id)

                    dlcEntries += SteamContentDlcEntry(
                        appId = dlcApp.id,
                        name = dlcApp.name.ifBlank { "DLC ${dlcApp.id}" },
                        installable = dlcDepots.isNotEmpty(),
                        defaultSelected = defaultSelected,
                    )
                    dlcSizes[dlcApp.id] = if (dlcDepots.isNotEmpty()) {
                        calculateDepotSizes(dlcDepots, selectedBranch)
                    } else {
                        "--" to "--"
                    }
                }

                // Base game info
                val baseDepots = SteamService.getMainAppDepots(game.appId)
                val baseSizes = calculateDepotSizes(baseDepots.values, selectedBranch)

                SteamContentLoadResult(
                    availableBytes = availableBytes,
                    baseSizes = baseSizes,
                    dlcEntries = dlcEntries.sortedBy { it.name.lowercase() },
                    dlcSizes = dlcSizes,
                )
            }
        }

        result.onSuccess { contentResult ->
            availableSpaceBytes = contentResult.availableBytes
            baseGameSizeText = contentResult.baseSizes.first to contentResult.baseSizes.second
            contentResult.dlcEntries.forEach { entry ->
                dlcApps += entry.appId to entry.name
                selectedDlcIds[entry.appId] = entry.defaultSelected
                installableDlcIds[entry.appId] = entry.installable
            }
            contentResult.dlcSizes.forEach { (appId, sizePair) ->
                perItemSizeText[appId] = sizePair.first to sizePair.second
            }
        }.onFailure {
            loadFailed = true
        }

        loading = false
    }

    LaunchedEffect(game.appId, selectedBranch, selectedDlcIds.toMap()) {
        if (loading) return@LaunchedEffect
        val enabledDlcIds = selectedDlcIds
            .filterValues { it }
            .keys
            .filter { installableDlcIds[it] == true }
        val sizes = withContext(Dispatchers.IO) {
            SteamService.getSelectedManifestSizes(
                appId = game.appId,
                userSelectedDlcAppIds = enabledDlcIds,
                branch = selectedBranch,
            )
        }
        totalDownloadSizeText = formatBinarySize(sizes.downloadSize)
        totalInstallSizeText = formatBinarySize(sizes.installSize)
    }

    val availableSpaceText by remember(availableSpaceBytes) {
        derivedStateOf { formatBinarySize(availableSpaceBytes) }
    }
    val canInstall by remember(selectedDlcIds.toMap(), installableDlcIds.toMap(), totalInstallSizeText, availableSpaceBytes, isInstalled) {
        derivedStateOf {
            val selectedNewDlc = selectedDlcIds
                .filterValues { it }
                .keys
                .any { it !in installedDlcIds && installableDlcIds[it] == true }
            (!isInstalled || selectedNewDlc) && availableSpaceBytes > 0L
        }
    }

    SteamFullscreenSheet(
        title = stringResource(R.string.steam_library_content_manager_title),
        subtitle = game.name,
        onDismiss = onDismiss,
    ) {
        when {
            loading -> SteamCenteredState(stringResource(R.string.steam_library_loading))
            loadFailed -> SteamCenteredState(stringResource(R.string.steam_library_content_manager_failed))
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SteamInfoBanner(
                        title = stringResource(R.string.steam_library_branch),
                        body = selectedBranch,
                    )
                    SteamInfoBanner(
                        title = stringResource(R.string.steam_library_download_install_compact),
                        body = context.getString(
                            R.string.steam_library_download_install_available,
                            totalDownloadSizeText,
                            totalInstallSizeText,
                            availableSpaceText,
                        ),
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            SteamSelectableRow(
                                title = stringResource(R.string.steam_library_content_base_game),
                                subtitle = "${baseGameSizeText.first} / ${baseGameSizeText.second}",
                                checked = true,
                                enabled = false,
                                onCheckedChange = {},
                            )
                        }
                        items(dlcApps, key = { it.first }) { (appId, name) ->
                            val checked = selectedDlcIds[appId] == true
                            val sizeText = perItemSizeText[appId]?.let { "${it.first} / ${it.second}" } ?: "--"
                            SteamSelectableRow(
                                title = name,
                                subtitle = sizeText,
                                checked = checked,
                                enabled = installableDlcIds[appId] == true,
                                onCheckedChange = { selectedDlcIds[appId] = it },
                            )
                        }
                        if (dlcApps.isEmpty()) {
                            item {
                                SteamCenteredState(
                                    text = stringResource(R.string.steam_library_content_empty),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                )
                            }
                        }
                    }
                    Button(
                        enabled = canInstall,
                        onClick = {
                            onInstall(
                                selectedDlcIds
                                    .filterValues { it }
                                    .keys
                                    .filter { installableDlcIds[it] == true }
                                    .sorted(),
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(stringResource(R.string.steam_library_install_selected))
                    }
                }
            }
        }
    }
}

@Composable
fun SteamWorkshopManagerSheet(
    game: SteamGameDetailUi,
    currentEnabledIds: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
) {
    val steamClient = SteamService.instance?.steamClient
    val steamId = SteamService.userSteamId
    val workshopItems = remember(game.appId) { mutableStateListOf<WorkshopItem>() }
    val selectedIds = remember(game.appId) { mutableStateMapOf<Long, Boolean>() }
    var isLoading by remember(game.appId) { mutableStateOf(true) }
    var fetchFailed by remember(game.appId) { mutableStateOf(false) }
    var searchQuery by remember(game.appId) { mutableStateOf("") }

    LaunchedEffect(game.appId) {
        isLoading = true
        fetchFailed = false
        workshopItems.clear()
        selectedIds.clear()
        searchQuery = ""

        if (steamClient == null || steamId == null) {
            fetchFailed = true
            isLoading = false
            return@LaunchedEffect
        }

        val result = withContext(Dispatchers.IO) {
            WorkshopManager.getSubscribedItems(game.appId, steamClient, steamId)
        }
        if (!result.succeeded) {
            fetchFailed = true
        } else {
            workshopItems.addAll(result.items)
            result.items.forEach { item ->
                selectedIds[item.publishedFileId] = currentEnabledIds.contains(item.publishedFileId)
            }
        }
        isLoading = false
    }

    val visibleItems by remember(workshopItems, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                workshopItems.toList()
            } else {
                workshopItems.filter { it.title.contains(searchQuery, ignoreCase = true) }
            }
        }
    }
    val totalSelectedSize by remember(selectedIds.toMap(), workshopItems) {
        derivedStateOf {
            workshopItems
                .filter { selectedIds[it.publishedFileId] == true }
                .sumOf { it.fileSizeBytes }
        }
    }

    SteamFullscreenSheet(
        title = stringResource(R.string.steam_library_workshop_title),
        subtitle = game.name,
        onDismiss = onDismiss,
    ) {
        when {
            isLoading -> SteamCenteredState(stringResource(R.string.steam_library_loading))
            fetchFailed -> SteamCenteredState(stringResource(R.string.steam_library_workshop_failed))
            else -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SteamSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = { searchQuery = "" },
                    )
                    SteamInfoBanner(
                        title = stringResource(R.string.steam_library_workshop_selected),
                        body = stringResource(
                            R.string.steam_library_workshop_selected_summary,
                            selectedIds.count { it.value },
                            workshopItems.size,
                            formatBinarySize(totalSelectedSize),
                        ),
                    )
                    if (workshopItems.isEmpty()) {
                        SteamCenteredState(stringResource(R.string.steam_library_workshop_empty), modifier = Modifier.weight(1f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visibleItems, key = { it.publishedFileId }) { item ->
                                SteamWorkshopRow(
                                    item = item,
                                    checked = selectedIds[item.publishedFileId] == true,
                                    onCheckedChange = { selectedIds[item.publishedFileId] = it },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            onSave(selectedIds.filterValues { it }.keys)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(stringResource(R.string.steam_library_apply_and_sync))
                    }
                }
            }
        }
    }
}

@Composable
fun SteamBranchPickerSheet(
    gameName: String,
    availableBranches: List<String>,
    currentBranch: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var selectedBranch by remember(currentBranch) { mutableStateOf(currentBranch) }

    SteamFullscreenSheet(
        title = stringResource(R.string.steam_library_change_branch_title),
        subtitle = gameName,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SteamInfoBanner(
                title = stringResource(R.string.steam_library_branch_current),
                body = currentBranch,
            )
            availableBranches.forEach { branch ->
                SteamBranchRow(
                    branch = branch,
                    selected = branch == selectedBranch,
                    onSelect = { selectedBranch = branch },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                enabled = selectedBranch != currentBranch,
                onClick = { onConfirm(selectedBranch) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.steam_library_apply_branch))
            }
        }
    }
}

@Composable
private fun SteamFullscreenSheet(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E131B)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF111722), Color(0xFF0C1016)),
                        ),
                    )
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = subtitle,
                            color = Color(0xFF8FA4BF),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF151D29))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                    }
                }
                HorizontalDivider(color = Color(0xFF223654))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SteamCenteredState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color(0xFFB8C6D9),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SteamInfoBanner(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B27)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title.uppercase(),
                color = Color(0xFF7F94B0),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SteamSelectableRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B27)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = if (enabled) Color.White else Color(0xFF90A0B8),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF90A0B8),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null)
        }
    }
}

@Composable
private fun SteamWorkshopRow(
    item: WorkshopItem,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131B27)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1A2432)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.previewUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.previewUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = item.title.take(1).uppercase(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatBinarySize(item.fileSizeBytes),
                    color = Color(0xFF8FA4BF),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun SteamBranchRow(
    branch: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFF14304A) else Color(0xFF131B27),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = branch,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun calculateDepotSizes(
    depots: Collection<DepotInfo>,
    branch: String,
): Pair<String, String> {
    val installBytes = depots.sumOf { resolveManifestSize(it, branch) }
    val downloadBytes = depots.sumOf { resolveManifestDownload(it, branch) }
    return formatBinarySize(downloadBytes) to formatBinarySize(installBytes)
}

private fun resolveManifestSize(
    depot: DepotInfo,
    branch: String,
): Long {
    val manifest = depot.manifests[branch]
        ?: depot.encryptedManifests[branch]
        ?: depot.manifests["public"]
        ?: depot.encryptedManifests["public"]
    return manifest?.size ?: 0L
}

private fun resolveManifestDownload(
    depot: DepotInfo,
    branch: String,
): Long {
    val manifest = depot.manifests[branch]
        ?: depot.encryptedManifests[branch]
        ?: depot.manifests["public"]
        ?: depot.encryptedManifests["public"]
    return manifest?.download ?: 0L
}

private fun formatBinarySize(bytes: Long): String {
    return if (bytes > 0L) StorageUtils.formatBinarySize(bytes) else "--"
}

private data class SteamContentDlcEntry(
    val appId: Int,
    val name: String,
    val installable: Boolean,
    val defaultSelected: Boolean,
)

private data class SteamContentLoadResult(
    val availableBytes: Long,
    val baseSizes: Pair<String, String>,
    val dlcEntries: List<SteamContentDlcEntry>,
    val dlcSizes: Map<Int, Pair<String, String>>,
)
