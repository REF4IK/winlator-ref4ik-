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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.ShortcutCoverFetcher
import java.io.File
import java.io.FileWriter

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
    var shortcutForMenu by remember { mutableStateOf<Shortcut?>(null) }
    var shortcutForClone by remember { mutableStateOf<Shortcut?>(null) }
    var showPropertiesFor by remember { mutableStateOf<Shortcut?>(null) }
    var shortcutForSteamInfo by remember { mutableStateOf<Shortcut?>(null) }

    // Собираем все шорткаты из всех контейнеров
    val shortcuts = remember(refreshKey, refreshKeyInternal) {
        manager.loadShortcuts()
    }

    BackHandler(enabled = showContainerPicker || shortcutForMenu != null) {
        when {
            shortcutForMenu != null -> shortcutForMenu = null
            showContainerPicker -> showContainerPicker = false
        }
    }

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
                        onLongClick = { shortcutForMenu = shortcut },
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
                        onLongClick = { shortcutForMenu = shortcut },
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

    // Контекстное меню шортката (6 пунктов как в старом PopupMenu)
    shortcutForMenu?.let { s ->
        AlertDialog(
            onDismissRequest = { shortcutForMenu = null },
            title = { Text(s.name) },
            text = {
                Column {
                    // Запустить — работает
                    Row(modifier = Modifier.fillMaxWidth().clickable { runShortcut(ctx, s); shortcutForMenu = null }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.run))
                    }
                    // Настройки — открывает ShortcutSettingsScreen (Compose overlay)
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        shortcutForMenu = null
                        onOpenShortcutSettings(s)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.shortcut_settings))
                    }
                    // Добавить на главный экран — через ShortcutManager
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        shortcutForMenu = null
                        if (s.getExtra("uuid").isEmpty()) s.genUUID()
                        addShortcutToHomeScreen(ctx, s)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AddToHomeScreen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.add_to_homescreen))
                    }
                    // Удалить — удаление файла + disableShortcutOnScreen
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        shortcutForMenu = null
                        val ok = s.file?.delete() ?: false
                        if (ok) {
                            disableShortcutOnScreen(ctx, s)
                            AppUtils.showToast(ctx, "Shortcut removed")
                            refreshKeyInternal++
                        } else {
                            AppUtils.showToast(ctx, "Failed to remove")
                        }
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                    }
                    // Export for Frontend
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        shortcutForMenu = null
                        exportShortcutToFrontend(ctx, s)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.IosShare, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Export for Frontend")
                    }
                    // Clone to Another Container
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        shortcutForMenu = null
                        shortcutForClone = s
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ContentCopy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Clone to Another Container")
                    }
                    // SteamDB Info
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        shortcutForMenu = null
                        shortcutForSteamInfo = s
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Movie, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(if (java.util.Locale.getDefault().language == "ru") "Информация Steam" else "Steam Info")
                    }
                    // Properties
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        shortcutForMenu = null
                        showPropertiesFor = s
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Properties")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { shortcutForMenu = null }) { Text(stringResource(R.string.cancel)) }
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
        val prefs = ctx.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE)
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
            onDismiss = { shortcutForSteamInfo = null }
        )
    }
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

    val icon = remember(shortcut) { shortcut.icon?.asImageBitmap() }
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
                        bitmap = icon,
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

    val icon = remember(shortcut) { shortcut.icon?.asImageBitmap() }
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
                        bitmap = icon,
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
    val trailerUrl: String?
)

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
                        val mp4Obj = firstMovie.optJSONObject("mp4")
                        val rawUrl = mp4Obj?.optString("max") ?: mp4Obj?.optString("480")
                        trailerUrl = rawUrl?.replace("http://", "https://")
                    }
                    
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
                        trailerUrl = trailerUrl
                    ))
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
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val locale = java.util.Locale.getDefault().language
    val isRussian = locale == "ru"

    var appIdState by remember { mutableStateOf<Int?>(null) }
    var gameInfo by remember { mutableStateOf<SteamGameInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var searchQuery by remember { mutableStateOf(shortcut.name) }
    var searchResults by remember { mutableStateOf<List<SteamSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var activeFullScreenScreenshot by remember { mutableStateOf<String?>(null) }

    fun loadDetails(appId: Int) {
        isLoading = true
        errorMessage = null
        fetchGameDetails(appId, if (isRussian) "russian" else "english") { info ->
            if (info != null) {
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
        val cachedAppId = shortcut.getExtra("steamAppId").toIntOrNull()
        if (cachedAppId != null && cachedAppId > 0) {
            appIdState = cachedAppId
            loadDetails(cachedAppId)
        } else {
            fetchAppIdByName(shortcut.name) { resolvedId ->
                if (resolvedId != null && resolvedId > 0) {
                    appIdState = resolvedId
                    loadDetails(resolvedId)
                } else {
                    isLoading = false
                    performSearch(shortcut.name)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = gameInfo?.name ?: (if (isRussian) "Информация об игре" else "Game Information"),
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

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (isRussian) "Загрузка информации из Steam..." else "Loading information from Steam...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (gameInfo != null) {
                    val info = gameInfo!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        ) {
                            coil.compose.AsyncImage(
                                model = info.headerImage,
                                contentDescription = info.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.7f)
                                            )
                                        )
                                    )
                            )
                            Text(
                                text = "AppID: ${info.appId}",
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

                            if (info.trailerUrl != null) {
                                Text(
                                    text = if (isRussian) "Видео / Трейлер" else "Video / Trailer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AndroidView(
                                        modifier = Modifier.fillMaxSize(),
                                        factory = { context ->
                                            VideoView(context).apply {
                                                setVideoPath(info.trailerUrl)
                                                val mediaController = MediaController(context)
                                                mediaController.setAnchorView(this)
                                                setMediaController(mediaController)
                                                setOnPreparedListener { 
                                                    seekTo(100) 
                                                }
                                            }
                                        }
                                    )
                                }
                            }

                            if (info.screenshots.isNotEmpty()) {
                                Text(
                                    text = if (isRussian) "Скриншоты" else "Screenshots",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(info.screenshots) { ssUrl ->
                                        Box(
                                            modifier = Modifier
                                                .width(160.dp)
                                                .height(90.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable { activeFullScreenScreenshot = ssUrl }
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
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (searchResults.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchResults) { res ->
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
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = errorMessage ?: (if (isRussian) "Поиск еще не выполнялся." else "No search performed yet."),
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

    activeFullScreenScreenshot?.let { ssUrl ->
        Dialog(
            onDismissRequest = { activeFullScreenScreenshot = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { activeFullScreenScreenshot = null },
                contentAlignment = Alignment.Center
            ) {
                coil.compose.AsyncImage(
                    model = ssUrl,
                    contentDescription = "screenshot_fullscreen",
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { activeFullScreenScreenshot = null },
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
