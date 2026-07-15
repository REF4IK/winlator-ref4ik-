package com.winlator.cmod.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Environment
import android.widget.VideoView
import android.widget.MediaController
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import java.util.concurrent.ConcurrentHashMap
import coil.request.ImageRequest
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import com.winlator.cmod.core.DohOkHttp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.core.ShortcutCoverFetcher
import com.winlator.cmod.core.gameconfig.GameConfigManager
import com.winlator.cmod.core.gameconfig.CloudConfigRepoV2
import java.io.File
import java.io.FileWriter

private val STEAM_GAME_INFO_CACHE = ConcurrentHashMap<Int, SteamGameInfo>()
private val STEAM_NAME_TO_APP_ID_CACHE = ConcurrentHashMap<String, Int>()

/**
 * Compose-версия ShortcutsFragment.
 * Список шорткатов в grid-режиме, FAB для импорта, диалог выбора контейнера.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(
    refreshKey: Int = 0,
    isGridView: Boolean = true,
    onToggleView: () -> Unit = {},
    onImportGame: (Container) -> Unit = {},
    onOpenShortcutSettings: (Shortcut) -> Unit = {},
    onBack: () -> Unit = {},
    onShowSteamInfo: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val spanCount = if (isLandscape) 4 else 2
    var refreshKeyInternal by remember { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKeyInternal++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val manager = remember(refreshKey, refreshKeyInternal) { ContainerManager(ctx) }
    var showContainerPicker by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf(false) }
    var shortcutForClone by remember { mutableStateOf<Shortcut?>(null) }
    var showCommunityConfigs by remember { mutableStateOf(false) }
    var showPublishDialog by remember { mutableStateOf<Shortcut?>(null) }
    var isPublishing by remember { mutableStateOf(false) }
    var contextShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var showPropertiesFor by remember { mutableStateOf<Shortcut?>(null) }
    var shortcutForSteamInfo by remember { mutableStateOf<Shortcut?>(null) }

    LaunchedEffect(shortcutForSteamInfo) {
        onShowSteamInfo(shortcutForSteamInfo != null)
    }

    // Собираем все шорткаты из всех контейнеров
    val shortcuts = remember(refreshKey, refreshKeyInternal) {
        manager.loadShortcuts()
    }

    BackHandler(enabled = showContainerPicker || shortcutForSteamInfo != null || showCommunityConfigs) {
        when {
            showCommunityConfigs -> showCommunityConfigs = false
            shortcutForSteamInfo != null -> shortcutForSteamInfo = null
            showContainerPicker -> showContainerPicker = false
        }
    }

    if (showCommunityConfigs) {
        CommunityConfigsScreen(onBack = { showCommunityConfigs = false }, contextShortcut = contextShortcut)
    } else {
Column(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
        if (shortcuts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VideogameAsset,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_shortcuts),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else if (isGridView) {
            // Grid view — большие карточки
            LazyVerticalGrid(
                columns = GridCells.Fixed(spanCount),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shortcuts, key = { it.hashCode() }) { shortcut ->
                    ShortcutLargeCard(
                        shortcut = shortcut,
                        onClick = { runShortcut(ctx, shortcut) },
                        onLongClick = { shortcutForSteamInfo = shortcut },
                    )
                }
            }
        } else {
            // List view — компактные карточки
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shortcuts) { shortcut ->
                    ShortcutCard(
                        shortcut = shortcut,
                        onClick = { runShortcut(ctx, shortcut) },
                        onLongClick = { shortcutForSteamInfo = shortcut },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                pendingImport = true
                showContainerPicker = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
    } // Column wrapper end

    // Диалог выбора контейнера
    if (showContainerPicker) {
        ContainerPickerDialog(
            containers = manager.containers,
            onDismiss = { showContainerPicker = false; pendingImport = false },
            onPick = { container ->
                showContainerPicker = false
                if (pendingImport) {
                    pendingImport = false
                    onImportGame(container)
                } else {
                    pendingImport = false
                    AppUtils.showToast(ctx, "Add shortcut to ${container.name}")
                }
            },
        )
    }



    // Диалог выбора контейнера для клонирования
    shortcutForClone?.let { s ->
        ContainerPickerDialog(
            containers = manager.containers,
            onDismiss = { shortcutForClone = null },
            onPick = { c ->
                shortcutForClone = null
                val ok = s.cloneToContainer(c)
                if (ok) {
                    AppUtils.showToast(ctx, "Cloned to ${c.name}")
                    refreshKeyInternal++
                } else {
                    AppUtils.showToast(ctx, "Clone failed")
                }
            },
        )
    }

    // Диалог Properties
    showPropertiesFor?.let { s ->
        val prefs = MmkvPreferences("playtime_stats")
        val playtimeKey = "${s.name}_playtime"
        val playCountKey = "${s.name}_play_count"
        val totalPlaytime = prefs.getLong(playtimeKey, 0)
        val playCount = prefs.getInt(playCountKey, 0)
        val seconds = (totalPlaytime / 1000) % 60
        val minutes = (totalPlaytime / (1000 * 60)) % 60
        val hours = (totalPlaytime / (1000 * 60 * 60)) % 24
        val days = totalPlaytime / (1000 * 60 * 60 * 24)
        val playtime = "${days}d ${String.format("%02d", hours)}h ${String.format("%02d", minutes)}m ${String.format("%02d", seconds)}s"

        AlertDialog(
            onDismissRequest = { showPropertiesFor = null },
            title = { Text("Properties", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Number of times played: $playCount", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Playtime: $playtime", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            prefs.edit().remove(playtimeKey).remove(playCountKey).apply()
                            AppUtils.showToast(ctx, "Properties reset")
                            showPropertiesFor = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reset Properties") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPropertiesFor = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // Диалог Steam Info
    shortcutForSteamInfo?.let { s ->
        SteamInfoDialog(
            shortcut = s,
            onDismiss = { shortcutForSteamInfo = null },
            onOpenSettings = {
                onOpenShortcutSettings(s)
            },
            onCloneClick = {
                shortcutForSteamInfo = null
                shortcutForClone = s
            },
            onPropertiesClick = {
                showPropertiesFor = s
            },
            onRefresh = {
                refreshKeyInternal++
            },
            onSearchConfigs = {
                showCommunityConfigs = true
                shortcutForSteamInfo = null
            }
        )
    }

    // Publish dialog
    showPublishDialog?.let { s ->
        var pubDesc by remember(s) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPublishDialog = null },
            title = { Text(stringResource(R.string.publish_config)) },
            text = {
                Column {
                    Text(s.name, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pubDesc,
                        onValueChange = { pubDesc = it },
                        label = { Text(stringResource(R.string.config_description_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!isPublishing) {
                            isPublishing = true
                            val config = GameConfigManager.buildGameConfig(s.container, s, pubDesc)
                            CloudConfigRepoV2.uploadConfig(config, object : CloudConfigRepoV2.UploadCallback {
                                override fun onComplete(success: Boolean, sha: String, uploadToken: String, error: String?) {
                                    isPublishing = false
                                    showPublishDialog = null
                                    AppUtils.showToast(ctx, if (success) ctx.getString(R.string.config_published_success) else "Error: $error")
                                }
                            })
                        }
                    },
                    enabled = !isPublishing
                ) { Text(if (isPublishing) "..." else stringResource(R.string.publish)) }
            },
            dismissButton = {
                TextButton(onClick = { showPublishDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    } // else block for showCommunityConfigs
}

// ---- Вспомогательные функции для popup-меню ----

private fun addShortcutToHomeScreen(ctx: android.content.Context, shortcut: Shortcut) {
    val shortcutManager = ctx.getSystemService(ShortcutManager::class.java)
    if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
        val icon = shortcut.displayIcon ?: BitmapFactory.decodeResource(ctx.resources, R.drawable.icon_shortcut)
        val intent = Intent(ctx, XServerDisplayActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        intent.putExtra("container_id", shortcut.container.id)
        intent.putExtra("shortcut_path", shortcut.file?.absolutePath ?: "")

        val info = ShortcutInfo.Builder(ctx, shortcut.getExtra("uuid"))
            .setShortLabel(shortcut.name)
            .setLongLabel(shortcut.name)
            .setIcon(Icon.createWithBitmap(icon))
            .setIntent(intent)
            .build()
        shortcutManager.requestPinShortcut(info, null)
    }
}

private fun disableShortcutOnScreen(ctx: android.content.Context, shortcut: Shortcut) {
    try {
        val shortcutManager = ctx.getSystemService(ShortcutManager::class.java)
        shortcutManager?.disableShortcuts(
            listOf(shortcut.getExtra("uuid")),
            ctx.getString(R.string.shortcut_not_available)
        )
    } catch (_: Exception) {}
}

private fun exportShortcutToFrontend(ctx: android.content.Context, shortcut: Shortcut) {
    try {
        val frontendDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Winlator/Frontend")
        if (!frontendDir.exists()) frontendDir.mkdirs()

        // FRONTEND_INSTRUCTIONS.txt
        val instrFile = File(frontendDir, "FRONTEND_INSTRUCTIONS.txt")
        if (!instrFile.exists()) {
            FileWriter(instrFile).use { w ->
                w.write("Instructions for adding Winlator shortcuts to Frontends (WIP):\n\n")
                w.write("Daijisho:\n1. Add Winlator as a custom platform\n2. Point to $frontendDir\n3. Set emulation profile\n\n")
                w.write("Beacon:\n1. Scan $frontendDir\n2. The launch command is already configured\n")
            }
        }
        // metadata.pegasus.txt
        val metaFile = File(frontendDir, "metadata.pegasus.txt")
        FileWriter(metaFile).use { w ->
            w.write("collection: Windows\nshortname: windows\nextensions: desktop\n")
            w.write("launch: am start -n ${ctx.packageName}/.XServerDisplayActivity -e shortcut_path {file.path} --activity-clear-task --activity-clear-top --activity-no-history\n")
        }
        // Копируем файл шортката
        val exportFile = File(frontendDir, shortcut.file?.name ?: "${shortcut.name}.desktop")
        FileUtils.copy(shortcut.file, exportFile)
        AppUtils.showToast(ctx, "Exported to ${exportFile.absolutePath}")
    } catch (e: Exception) {
        AppUtils.showToast(ctx, "Export failed: ${e.message}")
    }
}

// ---- Карточка как в старом ShortcutsFragment (list_item.xml) — компактная ----
@Composable
private fun ShortcutCard(
    shortcut: Shortcut,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var coverFile by remember(shortcut) { mutableStateOf<File?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(shortcut, refreshTrigger) {
        coverFile = ShortcutCoverFetcher.loadCoverArt(context, shortcut, landscape = false)
        if (coverFile == null) {
            ShortcutCoverFetcher.fetchCoverArt(context, shortcut, landscape = false) {
                refreshTrigger++
            }
        }
    }

    var icon by remember(shortcut) { mutableStateOf(shortcut.getDisplayIcon()?.asImageBitmap()) }

    LaunchedEffect(shortcut, coverFile, icon) {
        if (coverFile == null && icon == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val extracted = shortcut.extractAndSaveIcon()
                if (extracted != null) {
                    icon = extracted.asImageBitmap()
                }
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Иконка 60x60 с тёмным фоном
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (coverFile != null) {
                    coil.compose.AsyncImage(
                        model = coverFile,
                        contentDescription = shortcut.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon!!,
                        contentDescription = shortcut.name,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        Icons.Filled.VideogameAsset,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            // Текст
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortcut.container.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val wineVer = shortcut.container.wineVersion
                if (wineVer.isNotEmpty()) {
                    Text(
                        text = wineVer,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // Кнопка ⋮
            IconButton(onClick = onLongClick, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = null)
            }
        }
    }
}

// ---- Карточка как в старом ShortcutsFragment (grid_item.xml) — большая ----
@Composable
private fun ShortcutLargeCard(
    shortcut: Shortcut,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    var coverFile by remember(shortcut) { mutableStateOf<File?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(shortcut, refreshTrigger) {
        coverFile = ShortcutCoverFetcher.loadCoverArt(context, shortcut, landscape = false)
        if (coverFile == null) {
            ShortcutCoverFetcher.fetchCoverArt(context, shortcut, landscape = false) {
                refreshTrigger++
            }
        }
    }

    var icon by remember(shortcut) { mutableStateOf(shortcut.getDisplayIcon()?.asImageBitmap()) }

    LaunchedEffect(shortcut, coverFile, icon) {
        if (coverFile == null && icon == null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val extracted = shortcut.extractAndSaveIcon()
                if (extracted != null) {
                    icon = extracted.asImageBitmap()
                }
            }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Cover art 228dp
            if (coverFile != null) {
                coil.compose.AsyncImage(
                    model = coverFile,
                    contentDescription = shortcut.name,
                    modifier = Modifier.fillMaxWidth().height(228.dp),
                    contentScale = ContentScale.Crop,
                )
            } else if (icon != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(228.dp).background(Color(0xFF0D0D1A)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = icon!!,
                        contentDescription = shortcut.name,
                        modifier = Modifier.size(80.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(228.dp).background(Color(0xFF0D0D1A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.VideogameAsset,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                }
            }
            // Верхний градиент для кнопки меню
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent),
                        )
                    ),
            )
            // Нижний градиент для текста
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        )
                    ),
            )
            // Кнопка ⋮
            IconButton(
                onClick = onLongClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(40.dp),
            ) {
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White)
            }
            // Текст снизу: имя → контейнер → wine-badge
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 48.dp, bottom = 10.dp),
            ) {
                Text(
                    text = shortcut.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = shortcut.container.name,
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                val wineVer = shortcut.container.wineVersion
                if (wineVer.isNotEmpty()) {
                    Surface(
                        color = Color(0xFF1A73E8).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(top = 3.dp),
                    ) {
                        Text(
                            text = wineVer,
                            color = Color.White.copy(alpha = 0.87f),
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun runShortcut(ctx: android.content.Context, shortcut: Shortcut) {
    try {
        val intent = Intent(ctx, XServerDisplayActivity::class.java)
        intent.putExtra("container_id", shortcut.container.id)
        intent.putExtra("shortcut_path", shortcut.file?.absolutePath ?: "")
        ctx.startActivity(intent)
    } catch (e: Exception) {
        AppUtils.showToast(ctx, "Cannot start: ${e.message}")
    }
}

// ---- SteamDB / Steam API Game Information Card implementation ----

class SteamGameInfo(
    val appId: Int,
    val name: String,
    val description: String,
    val headerImage: String,
    val releaseDate: String,
    val developers: List<String>,
    val publishers: List<String>,
    val genres: List<String>,
    val screenshots: List<String>,
    val trailerUrl: String?,
    val controllerSupport: String,
    val minRequirements: String,
    val recRequirements: String,
    val supportedLanguages: String,
    val metacriticScore: Int,
    val metacriticUrl: String,
    val reviewScoreDesc: String,
    val positivePercent: Int,
    val totalReviews: Int
)

fun SteamGameInfo.toJson(): JSONObject {
    val json = JSONObject()
    json.put("appId", appId)
    json.put("name", name)
    json.put("description", description)
    json.put("headerImage", headerImage)
    json.put("releaseDate", releaseDate)
    json.put("developers", org.json.JSONArray().apply { developers.forEach { put(it) } })
    json.put("publishers", org.json.JSONArray().apply { publishers.forEach { put(it) } })
    json.put("genres", org.json.JSONArray().apply { genres.forEach { put(it) } })
    json.put("screenshots", org.json.JSONArray().apply { screenshots.forEach { put(it) } })
    json.put("trailerUrl", trailerUrl ?: JSONObject.NULL)
    json.put("controllerSupport", controllerSupport)
    json.put("minRequirements", minRequirements)
    json.put("recRequirements", recRequirements)
    json.put("supportedLanguages", supportedLanguages)
    json.put("metacriticScore", metacriticScore)
    json.put("metacriticUrl", metacriticUrl)
    json.put("reviewScoreDesc", reviewScoreDesc)
    json.put("positivePercent", positivePercent)
    json.put("totalReviews", totalReviews)
    return json
}

fun JSONObject.toSteamGameInfo(): SteamGameInfo {
    val appId = optInt("appId")
    val name = optString("name", "")
    val description = optString("description", "")
    val headerImage = optString("headerImage", "")
    val releaseDate = optString("releaseDate", "")
    
    val developers = ArrayList<String>()
    optJSONArray("developers")?.let { arr ->
        for (i in 0 until arr.length()) {
            developers.add(arr.optString(i))
        }
    }
    
    val publishers = ArrayList<String>()
    optJSONArray("publishers")?.let { arr ->
        for (i in 0 until arr.length()) {
            publishers.add(arr.optString(i))
        }
    }
    
    val genres = ArrayList<String>()
    optJSONArray("genres")?.let { arr ->
        for (i in 0 until arr.length()) {
            genres.add(arr.optString(i))
        }
    }
    
    val screenshots = ArrayList<String>()
    optJSONArray("screenshots")?.let { arr ->
        for (i in 0 until arr.length()) {
            screenshots.add(arr.optString(i))
        }
    }
    
    val trailerUrl = if (has("trailerUrl") && !isNull("trailerUrl")) optString("trailerUrl") else null
    val controllerSupport = optString("controllerSupport", "")
    val minRequirements = optString("minRequirements", "")
    val recRequirements = optString("recRequirements", "")
    val supportedLanguages = optString("supportedLanguages", "")
    val metacriticScore = optInt("metacriticScore", 0)
    val metacriticUrl = optString("metacriticUrl", "")
    val reviewScoreDesc = optString("reviewScoreDesc", "")
    val positivePercent = optInt("positivePercent", 0)
    val totalReviews = optInt("totalReviews", 0)
    
    return SteamGameInfo(
        appId = appId,
        name = name,
        description = description,
        headerImage = headerImage,
        releaseDate = releaseDate,
        developers = developers,
        publishers = publishers,
        genres = genres,
        screenshots = screenshots,
        trailerUrl = trailerUrl,
        controllerSupport = controllerSupport,
        minRequirements = minRequirements,
        recRequirements = recRequirements,
        supportedLanguages = supportedLanguages,
        metacriticScore = metacriticScore,
        metacriticUrl = metacriticUrl,
        reviewScoreDesc = reviewScoreDesc,
        positivePercent = positivePercent,
        totalReviews = totalReviews
    )
}

private fun getDiskCacheFile(context: Context, appId: Int): File {
    val dir = File(context.cacheDir, "steam_info_cache")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "steam_info_${appId}.json")
}

private fun saveToDiskCache(context: Context, info: SteamGameInfo) {
    try {
        val file = getDiskCacheFile(context, info.appId)
        val jsonStr = info.toJson().toString()
        FileUtils.writeString(file, jsonStr)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun loadFromDiskCache(context: Context, appId: Int): SteamGameInfo? {
    try {
        val file = getDiskCacheFile(context, appId)
        if (file.exists() && file.isFile) {
            val lines = FileUtils.readLines(file)
            val jsonStr = lines.joinToString("\n")
            if (jsonStr.isNotEmpty()) {
                val json = JSONObject(jsonStr)
                return json.toSteamGameInfo()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

private fun getGameInfoFromCache(context: Context, appId: Int): SteamGameInfo? {
    val memInfo = STEAM_GAME_INFO_CACHE[appId]
    if (memInfo != null) return memInfo
    val diskInfo = loadFromDiskCache(context, appId)
    if (diskInfo != null) {
        STEAM_GAME_INFO_CACHE[appId] = diskInfo
        return diskInfo
    }
    return null
}

data class SteamSearchResult(
    val id: Int,
    val name: String,
    val tinyImage: String
)

private fun fetchAppIdByName(query: String, callback: (Int?) -> Unit) {
    val cleanQuery = query.replace("_", " ").replace("-", " ")
    val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
    val url = "https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=us"
    val request = Request.Builder().url(url).build()

    DohOkHttp.get().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback(null)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                if (!response.isSuccessful) {
                    callback(null)
                    return
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val items = json.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val first = items.getJSONObject(0)
                    callback(first.getInt("id"))
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                callback(null)
            }
        }
    })
}

private fun searchGamesOnSteam(query: String, callback: (List<SteamSearchResult>) -> Unit) {
    val cleanQuery = query.replace("_", " ").replace("-", " ")
    val encoded = URLEncoder.encode(cleanQuery, StandardCharsets.UTF_8.name())
    val url = "https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=us"
    val request = Request.Builder().url(url).build()

    DohOkHttp.get().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback(emptyList())
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                if (!response.isSuccessful) {
                    callback(emptyList())
                    return
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val items = json.optJSONArray("items") ?: return callback(emptyList())
                val list = mutableListOf<SteamSearchResult>()
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    list.add(SteamSearchResult(
                        id = obj.getInt("id"),
                        name = obj.getString("name"),
                        tinyImage = obj.optString("tiny_image", "")
                    ))
                }
                callback(list)
            } catch (e: Exception) {
                callback(emptyList())
            }
        }
    })
}

private fun fetchGameDetails(appId: Int, locale: String, callback: (SteamGameInfo?) -> Unit) {
    val url = "https://store.steampowered.com/api/appdetails?appids=$appId&l=$locale"
    val request = Request.Builder().url(url).build()

    DohOkHttp.get().newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            callback(null)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                if (!response.isSuccessful) {
                    callback(null)
                    return
                }
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val appObj = json.optJSONObject(appId.toString())
                if (appObj != null && appObj.optBoolean("success", false)) {
                    val data = appObj.getJSONObject("data")
                    
                    val name = data.getString("name")
                    val description = data.optString("short_description", "").ifEmpty { data.optString("detailed_description", "") }
                    val headerImage = data.optString("header_image", "")
                    
                    val releaseObj = data.optJSONObject("release_date")
                    val releaseDate = releaseObj?.optString("date", "") ?: ""
                    
                    val developers = mutableListOf<String>()
                    val devsArr = data.optJSONArray("developers")
                    if (devsArr != null) {
                        for (i in 0 until devsArr.length()) developers.add(devsArr.getString(i))
                    }
                    
                    val publishers = mutableListOf<String>()
                    val pubsArr = data.optJSONArray("publishers")
                    if (pubsArr != null) {
                        for (i in 0 until pubsArr.length()) publishers.add(pubsArr.getString(i))
                    }
                    
                    val genres = mutableListOf<String>()
                    val genresArr = data.optJSONArray("genres")
                    if (genresArr != null) {
                        for (i in 0 until genresArr.length()) {
                            genres.add(genresArr.getJSONObject(i).getString("description"))
                        }
                    }
                    
                    val screenshots = mutableListOf<String>()
                    val ssArr = data.optJSONArray("screenshots")
                    if (ssArr != null) {
                        for (i in 0 until ssArr.length()) {
                            screenshots.add(ssArr.getJSONObject(i).getString("path_full"))
                        }
                    }
                    
                    var trailerUrl: String? = null
                    val moviesArr = data.optJSONArray("movies")
                    if (moviesArr != null && moviesArr.length() > 0) {
                        val firstMovie = moviesArr.getJSONObject(0)
                        
                        var rawUrl = firstMovie.optString("hls_h264", "").ifEmpty {
                            firstMovie.optString("dash_h264", "")
                        }
                        
                        if (rawUrl.isEmpty()) {
                            val mp4Obj = firstMovie.optJSONObject("mp4")
                            rawUrl = mp4Obj?.optString("max", "")?.ifEmpty { mp4Obj?.optString("480", "") } ?: ""
                        }
                        
                        if (rawUrl.isEmpty()) {
                            val webmObj = firstMovie.optJSONObject("webm")
                            rawUrl = webmObj?.optString("max", "")?.ifEmpty { webmObj?.optString("480", "") } ?: ""
                        }
                        
                        if (rawUrl.isNotEmpty()) {
                            trailerUrl = rawUrl.replace("http://", "https://")
                        }
                    }
                    
                    var controllerSupport = ""
                    val categories = data.optJSONArray("categories")
                    if (categories != null) {
                        for (i in 0 until categories.length()) {
                            val cat = categories.getJSONObject(i)
                            val catId = cat.optInt("id")
                            if (catId == 28) {
                                controllerSupport = "full"
                            } else if (catId == 18 && controllerSupport != "full") {
                                controllerSupport = "partial"
                            }
                        }
                    }

                    val pcReq = data.optJSONObject("pc_requirements")
                    val minRequirements = pcReq?.optString("minimum", "") ?: ""
                    val recRequirements = pcReq?.optString("recommended", "") ?: ""

                    val supportedLanguages = data.optString("supported_languages", "")

                    val metacriticObj = data.optJSONObject("metacritic")
                    val metacriticScore = metacriticObj?.optInt("score", 0) ?: 0
                    val metacriticUrl = metacriticObj?.optString("url", "") ?: ""

                    val reviewsUrl = "https://store.steampowered.com/appreviews/$appId?json=1&language=all&purchase_type=all"
                    val reviewsRequest = Request.Builder().url(reviewsUrl).build()
                    DohOkHttp.get().newCall(reviewsRequest).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            callback(SteamGameInfo(
                                appId = appId,
                                name = name,
                                description = description,
                                headerImage = headerImage,
                                releaseDate = releaseDate,
                                developers = developers,
                                publishers = publishers,
                                genres = genres,
                                screenshots = screenshots,
                                trailerUrl = trailerUrl,
                                controllerSupport = controllerSupport,
                                minRequirements = minRequirements,
                                recRequirements = recRequirements,
                                supportedLanguages = supportedLanguages,
                                metacriticScore = metacriticScore,
                                metacriticUrl = metacriticUrl,
                                reviewScoreDesc = "",
                                positivePercent = 0,
                                totalReviews = 0
                            ))
                        }

                        override fun onResponse(call: Call, response: Response) {
                            var reviewScoreDesc = ""
                            var positivePercent = 0
                            var totalReviews = 0
                            try {
                                val body = response.body?.string() ?: ""
                                val reviewsJson = JSONObject(body)
                                val summary = reviewsJson.optJSONObject("query_summary")
                                if (summary != null) {
                                    reviewScoreDesc = summary.optString("review_score_desc", "")
                                    val totalPositive = summary.optInt("total_positive", 0)
                                    totalReviews = summary.optInt("total_reviews", 0)
                                    positivePercent = if (totalReviews > 0) (totalPositive * 100 / totalReviews) else 0
                                }
                            } catch (e: Exception) {}

                            callback(SteamGameInfo(
                                appId = appId,
                                name = name,
                                description = description,
                                headerImage = headerImage,
                                releaseDate = releaseDate,
                                developers = developers,
                                publishers = publishers,
                                genres = genres,
                                screenshots = screenshots,
                                trailerUrl = trailerUrl,
                                controllerSupport = controllerSupport,
                                minRequirements = minRequirements,
                                recRequirements = recRequirements,
                                supportedLanguages = supportedLanguages,
                                metacriticScore = metacriticScore,
                                metacriticUrl = metacriticUrl,
                                reviewScoreDesc = reviewScoreDesc,
                                positivePercent = positivePercent,
                                totalReviews = totalReviews
                            ))
                        }
                    })
                } else {
                    callback(null)
                }
            } catch (e: Exception) {
                callback(null)
            }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteamInfoDialog(
    shortcut: Shortcut,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onCloneClick: () -> Unit,
    onPropertiesClick: () -> Unit,
    onRefresh: () -> Unit,
    onSearchConfigs: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val locale = java.util.Locale.getDefault().language
    val isRussian = locale == "ru"

    val initialAppId = remember(shortcut) {
        shortcut.getExtra("steamAppId").toIntOrNull() ?: STEAM_NAME_TO_APP_ID_CACHE[shortcut.name]
    }
    val initialInfo = remember(initialAppId) {
        initialAppId?.let { getGameInfoFromCache(ctx, it) }
    }

    var appIdState by remember(shortcut) { mutableStateOf<Int?>(initialAppId) }
    var gameInfo by remember(shortcut) { mutableStateOf<SteamGameInfo?>(initialInfo) }
    var isLoading by remember(shortcut) { mutableStateOf(initialInfo == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf(shortcut.name) }
    var searchResults by remember { mutableStateOf<List<SteamSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var activeFullScreenScreenshotIndex by remember { mutableStateOf<Int?>(null) }
    var activeTrailerUrl by remember { mutableStateOf<String?>(null) }

    fun loadDetails(appId: Int) {
        val cachedInfo = getGameInfoFromCache(ctx, appId)
        if (cachedInfo != null) {
            gameInfo = cachedInfo
            isLoading = false
            errorMessage = null
            return
        }
        isLoading = true
        errorMessage = null
        fetchGameDetails(appId, if (isRussian) "russian" else "english") { info ->
            if (info != null) {
                STEAM_GAME_INFO_CACHE[appId] = info
                saveToDiskCache(ctx, info)
                gameInfo = info
                isLoading = false
                // Save appId to shortcut as cache
                shortcut.putExtra("steamAppId", appId.toString())
                shortcut.saveData()
            } else {
                isLoading = false
                errorMessage = if (isRussian) "Не удалось загрузить детали игры." else "Failed to load game details."
            }
        }
    }

    fun performSearch(query: String) {
        isSearching = true
        searchGamesOnSteam(query) { results ->
            isSearching = false
            searchResults = results
            if (results.isEmpty()) {
                errorMessage = if (isRussian) "Игры не найдены. Попробуйте другой запрос." else "No games found. Try another search query."
            } else {
                errorMessage = null
            }
        }
    }

    LaunchedEffect(shortcut) {
        if (gameInfo != null) return@LaunchedEffect
        val cachedAppId = shortcut.getExtra("steamAppId").toIntOrNull() ?: STEAM_NAME_TO_APP_ID_CACHE[shortcut.name]
        if (cachedAppId != null && cachedAppId > 0) {
            appIdState = cachedAppId
            loadDetails(cachedAppId)
        } else {
            fetchAppIdByName(shortcut.name) { resolvedId ->
                if (resolvedId != null && resolvedId > 0) {
                    STEAM_NAME_TO_APP_ID_CACHE[shortcut.name] = resolvedId
                    appIdState = resolvedId
                    loadDetails(resolvedId)
                } else {
                    isLoading = false
                    performSearch(shortcut.name)
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            )
                    ) {
                        if (gameInfo != null) {
                            val heroUrl = remember(gameInfo) {
                                "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${gameInfo!!.appId}/library_hero.jpg"
                            }
                            var backgroundModel by remember(heroUrl) { mutableStateOf<Any>(heroUrl) }
                            
                            Box(modifier = Modifier.fillMaxSize()) {
                                coil.compose.AsyncImage(
                                    model = backgroundModel,
                                    onError = {
                                        if (backgroundModel == heroUrl) {
                                            backgroundModel = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${gameInfo!!.appId}/capsule_616x353.jpg"
                                        }
                                    },
                                    contentDescription = gameInfo!!.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                
                                if (backgroundModel == heroUrl) {
                                    val logoUrl = remember(gameInfo) {
                                        "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/${gameInfo!!.appId}/logo.png"
                                    }
                                    coil.compose.AsyncImage(
                                        model = logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .height(90.dp)
                                            .padding(horizontal = 16.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                var displayIcon by remember(shortcut) { mutableStateOf(shortcut.getDisplayIcon()) }

                                LaunchedEffect(shortcut, displayIcon) {
                                    if (displayIcon == null) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            val extracted = shortcut.extractAndSaveIcon()
                                            if (extracted != null) {
                                                displayIcon = extracted
                                            }
                                        }
                                    }
                                }

                                if (displayIcon != null) {
                                    coil.compose.AsyncImage(
                                        model = displayIcon,
                                        contentDescription = shortcut.name,
                                        modifier = Modifier.size(80.dp)
                                    )
                                } else {
                                    Icon(
                                        Icons.Filled.VideogameAsset,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = gameInfo?.name ?: shortcut.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                runShortcut(ctx, shortcut)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isRussian) "ИГРАТЬ" else "PLAY",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionGridItem(
                                        icon = Icons.Filled.Settings,
                                        label = if (isRussian) "Настройки" else "Settings",
                                        onClick = onOpenSettings,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.AddToHomeScreen,
                                        label = if (isRussian) "На экран" else "Add Shortcut",
                                        onClick = {
                                            if (shortcut.getExtra("uuid").isEmpty()) shortcut.genUUID()
                                            addShortcutToHomeScreen(ctx, shortcut)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.ContentCopy,
                                        label = if (isRussian) "Клон" else "Clone",
                                        onClick = onCloneClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    ActionGridItem(
                                        icon = Icons.Filled.Cloud,
                                        label = if (isRussian) "Конфиги" else "Configs",
                                        onClick = {
                                            onDismiss()
                                            onSearchConfigs()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.IosShare,
                                        label = if (isRussian) "Экспорт" else "Export",
                                        onClick = { exportShortcutToFrontend(ctx, shortcut) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.Info,
                                        label = if (isRussian) "Свойства" else "Properties",
                                        onClick = onPropertiesClick,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionGridItem(
                                        icon = Icons.Filled.Delete,
                                        label = if (isRussian) "Удалить" else "Delete",
                                        iconTint = MaterialTheme.colorScheme.error,
                                        onClick = {
                                            val ok = shortcut.file?.delete() ?: false
                                            if (ok) {
                                                disableShortcutOnScreen(ctx, shortcut)
                                                AppUtils.showToast(ctx, if (isRussian) "Ярлык удален" else "Shortcut removed")
                                                onRefresh()
                                                onDismiss()
                                            } else {
                                                AppUtils.showToast(ctx, if (isRussian) "Ошибка удаления" else "Failed to remove")
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (isRussian) "Загрузка информации из Steam..." else "Loading information from Steam...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        } else if (gameInfo != null) {
                            val info = gameInfo!!
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (info.reviewScoreDesc.isNotEmpty() || info.totalReviews > 0) {
                                    Card(
                                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Filled.ThumbUp,
                                                    contentDescription = "Rating",
                                                    tint = Color(0xFF4CAF50),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    text = "${info.positivePercent}%",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF4CAF50)
                                                )
                                            }
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = if (isRussian) {
                                                    when (info.reviewScoreDesc.lowercase()) {
                                                        "overwhelmingly positive" -> "Крайне положительные"
                                                        "very positive" -> "Очень положительные"
                                                        "positive" -> "Положительные"
                                                        "mostly positive" -> "В основном положительные"
                                                        "mixed" -> "Смешанные"
                                                        "mostly negative" -> "В основном отрицательные"
                                                        "negative" -> "Отрицательные"
                                                        "very negative" -> "Очень отрицательные"
                                                        "overwhelmingly negative" -> "Крайне отрицательные"
                                                        else -> info.reviewScoreDesc
                                                    }
                                                } else info.reviewScoreDesc,
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(1.dp))
                                            Text(
                                                text = if (isRussian) "${java.text.NumberFormat.getInstance().format(info.totalReviews)} отзывов"
                                                       else "${java.text.NumberFormat.getInstance().format(info.totalReviews)} reviews",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.VideogameAsset,
                                                contentDescription = "Controller",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (isRussian) "Геймпад" else "Controller",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = if (info.controllerSupport == "full") {
                                                if (isRussian) "Полная" else "Full"
                                            } else if (info.controllerSupport == "partial") {
                                                if (isRussian) "Частичная" else "Partial"
                                            } else {
                                                if (isRussian) "Нет данных" else "No Data"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (info.controllerSupport == "full") Color(0xFF4CAF50) else if (info.controllerSupport == "partial") Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }

                                if (info.metacriticScore > 0) {
                                    Card(
                                        modifier = Modifier.weight(0.8f).fillMaxHeight(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "Metacritic",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = if (info.metacriticScore >= 75) Color(0xFF66CC33)
                                                                else if (info.metacriticScore >= 50) Color(0xFFFFCC33)
                                                                else Color(0xFFFF3333),
                                                        shape = RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = info.metacriticScore.toString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (info.releaseDate.isNotEmpty()) {
                                        MetadataRow(label = if (isRussian) "Дата релиза" else "Release Date", value = info.releaseDate)
                                    }
                                    if (info.developers.isNotEmpty()) {
                                        MetadataRow(label = if (isRussian) "Разработчик" else "Developer", value = info.developers.joinToString(", "))
                                    }
                                    if (info.publishers.isNotEmpty()) {
                                        MetadataRow(label = if (isRussian) "Издатель" else "Publisher", value = info.publishers.joinToString(", "))
                                    }
                                    if (info.genres.isNotEmpty()) {
                                        MetadataRow(label = if (isRussian) "Жанры" else "Genres", value = info.genres.joinToString(", "))
                                    }
                                }
                            }

                            Text(
                                text = if (isRussian) "Описание" else "Description",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            val plainDescription = remember(info.description) {
                                try {
                                    android.text.Html.fromHtml(info.description, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                } catch (e: Throwable) {
                                    info.description.replace("<[^>]*>".toRegex(), "").trim()
                                }
                            }

                            Text(
                                text = plainDescription,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            if (info.screenshots.isNotEmpty() || info.trailerUrl != null) {
                                Text(
                                    text = if (isRussian) "Скриншоты и видео" else "Screenshots & Video",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (info.trailerUrl != null) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .width(160.dp)
                                                    .height(90.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable { activeTrailerUrl = info.trailerUrl }
                                            ) {
                                                coil.compose.AsyncImage(
                                                    model = info.headerImage,
                                                    contentDescription = "Video preview",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.4f))
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(percent = 50))
                                                        .align(Alignment.Center),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.PlayArrow,
                                                        contentDescription = "Play Video",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    var idx = 0
                                    items(info.screenshots) { ssUrl ->
                                        val currentIndex = idx++
                                        Box(
                                            modifier = Modifier
                                                .width(160.dp)
                                                .height(90.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { activeFullScreenScreenshotIndex = currentIndex }
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = ssUrl,
                                                contentDescription = "screenshot",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }
                            }

                            if (info.minRequirements.isNotEmpty() || info.recRequirements.isNotEmpty()) {
                                var showRequirements by remember { mutableStateOf(false) }
                                
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { showRequirements = !showRequirements }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isRussian) "Системные требования" else "System Requirements",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(
                                            imageVector = if (showRequirements) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = "Toggle Requirements",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    
                                    if (showRequirements) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (info.minRequirements.isNotEmpty()) {
                                                val minText = remember(info.minRequirements) {
                                                    try {
                                                        android.text.Html.fromHtml(info.minRequirements, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                                    } catch (e: Throwable) {
                                                        info.minRequirements.replace("<[^>]*>".toRegex(), "").trim()
                                                    }
                                                }
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = if (isRussian) "Минимальные требования:" else "Minimum Requirements:",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = minText,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                            
                                            if (info.recRequirements.isNotEmpty()) {
                                                val recText = remember(info.recRequirements) {
                                                    try {
                                                        android.text.Html.fromHtml(info.recRequirements, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                                                    } catch (e: Throwable) {
                                                        info.recRequirements.replace("<[^>]*>".toRegex(), "").trim()
                                                    }
                                                }
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Text(
                                                            text = if (isRussian) "Рекомендуемые требования:" else "Recommended Requirements:",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.secondary
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            text = recText,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = if (isRussian) "Не та игра? Найти вручную:" else "Wrong game? Search manually:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    label = { Text(if (isRussian) "Название игры" else "Game Name") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = { 
                                        gameInfo = null
                                        performSearch(searchQuery) 
                                    },
                                    enabled = searchQuery.trim().isNotEmpty()
                                ) {
                                    Text(if (isRussian) "Поиск" else "Search")
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp).navigationBarsPadding())
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = if (isRussian) "Поиск игры в Steam" else "Search game on Steam",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        label = { Text(if (isRussian) "Введите название" else "Enter name") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { performSearch(searchQuery) },
                                        enabled = searchQuery.trim().isNotEmpty()
                                    ) {
                                        Text(if (isRussian) "Искать" else "Search")
                                    }
                                }

                                if (isSearching) {
                                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                } else if (searchResults.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        searchResults.forEach { res ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { 
                                                        appIdState = res.id
                                                        loadDetails(res.id) 
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(60.dp, 30.dp)
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color.DarkGray)
                                                    ) {
                                                        if (res.tinyImage.isNotEmpty()) {
                                                            coil.compose.AsyncImage(
                                                                model = res.tinyImage,
                                                                contentDescription = res.name,
                                                                modifier = Modifier.fillMaxSize(),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column {
                                                        Text(text = res.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(text = "AppID: ${res.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Box(modifier = Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = errorMessage ?: (if (isRussian) "Игра не найдена в Steam. Вы можете настроить ярлык кнопками выше." else "Game details not found on Steam. You can manage the shortcut using actions above."),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    activeFullScreenScreenshotIndex?.let { startIndex ->
        val pagerState = rememberPagerState(
            initialPage = startIndex,
            pageCount = { gameInfo?.screenshots?.size ?: 0 }
        )
        Dialog(
            onDismissRequest = { activeFullScreenScreenshotIndex = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val ssUrl = gameInfo?.screenshots?.getOrNull(page)
                    if (ssUrl != null) {
                        coil.compose.AsyncImage(
                            model = ssUrl,
                            contentDescription = "screenshot_fullscreen",
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                IconButton(
                    onClick = { activeFullScreenScreenshotIndex = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }

                Text(
                    text = "${pagerState.currentPage + 1} / ${gameInfo?.screenshots?.size ?: 0}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    activeTrailerUrl?.let { trailerUrl ->
        Dialog(
            onDismissRequest = { activeTrailerUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                domStorageEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                            webChromeClient = android.webkit.WebChromeClient()
                            webViewClient = android.webkit.WebViewClient()
                            
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                    <style>
                                        body, html { margin: 0; padding: 0; width: 100vw; height: 100vh; overflow: hidden; background-color: black; }
                                        video { position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: contain; }
                                    </style>
                                    <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                                </head>
                                <body>
                                    <video id="video" controls autoplay playsinline></video>
                                    <script>
                                        var video = document.getElementById('video');
                                        var videoSrc = '$trailerUrl';
                                        if (Hls.isSupported()) {
                                            var hls = new Hls();
                                            hls.loadSource(videoSrc);
                                            hls.attachMedia(video);
                                            hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                                video.play();
                                            });
                                        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                            video.src = videoSrc;
                                            video.addEventListener('loadedmetadata', function() {
                                                video.play();
                                            });
                                        }
                                    </script>
                                </body>
                                </html>
                            """.trimIndent()
                            
                            loadDataWithBaseURL("https://video.akamai.steamstatic.com/", html, "text/html", "UTF-8", null)
                        }
                    },
                    onRelease = { webView ->
                        webView.destroy()
                    }
                )

                IconButton(
                    onClick = { activeTrailerUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionGridItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
