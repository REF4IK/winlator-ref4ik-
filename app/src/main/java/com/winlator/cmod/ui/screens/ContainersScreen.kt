package com.winlator.cmod.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.FileManagerActivity
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity
import com.winlator.cmod.XrActivity
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contentdialog.StorageInfoDialog
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.xenvironment.ImageFs
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainersScreen(
    containerManager: ContainerManager?,
    refreshKey: Int = 0,
    onCreateContainer: () -> Unit = {},
    onEditContainer: (Int) -> Unit = {},
    onOpenFileBrowser: (Int) -> Unit = {},
) {
    val ctx = LocalContext.current
    val manager = containerManager ?: remember(ctx, refreshKey) { ContainerManager(ctx) }
    var containers by remember(refreshKey) { mutableStateOf(manager.containers ?: emptyList()) }
    var showPreloader by remember { mutableStateOf(false) }
    var preloaderText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var menuContainer by remember { mutableStateOf<Container?>(null) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var confirmTitle by remember { mutableStateOf("") }

    fun reload() {
        manager.reload()
        containers = manager.containers ?: emptyList()
    }

    LaunchedEffect(refreshKey) { reload() }

    Scaffold(
        topBar = {},
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (ImageFs.find(ctx).isValid()) onCreateContainer() },
                containerColor = MaterialTheme.colorScheme.primary,
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add), tint = MaterialTheme.colorScheme.onPrimary) }
        },
    ) { padding ->
        if (containers.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.containers), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(containers, key = { it.id }) { container ->
                    ContainerCard(
                        container = container,
                        onRun = { runContainer(ctx, container) },
                        onMenu = { menuContainer = container; showMenu = true },
                    )
                }
            }
        }
    }

    if (showMenu && menuContainer != null) {
        val c = menuContainer!!
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = {
                showMenu = false; onEditContainer(c.id)
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = {
                showMenu = false
                confirmTitle = ctx.getString(R.string.do_you_want_to_duplicate_this_container)
                confirmAction = {
                    showPreloader = true; preloaderText = ctx.getString(R.string.duplicating_container)
                    manager.duplicateContainerAsync(c) {
                        (ctx as? Activity)?.runOnUiThread { showPreloader = false; reload() }
                    }
                }
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.remove)) }, onClick = {
                showMenu = false
                confirmTitle = ctx.getString(R.string.do_you_want_to_remove_this_container)
                confirmAction = {
                    showPreloader = true; preloaderText = ctx.getString(R.string.removing_container)
                    for (shortcut in manager.loadShortcuts()) {
                        if (shortcut.container == c) {
                            com.winlator.cmod.ShortcutsFragment.disableShortcutOnScreen(ctx, shortcut)
                        }
                    }
                    manager.removeContainerAsync(c) {
                        (ctx as? Activity)?.runOnUiThread { showPreloader = false; reload() }
                    }
                }
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.storage_info)) }, onClick = {
                showMenu = false; StorageInfoDialog(ctx as Activity, c).show()
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.container_file_manager)) }, onClick = {
                showMenu = false
                if (c.rootDir == null || !c.rootDir.isDirectory) {
                    AppUtils.showToast(ctx, R.string.container_file_manager_unavailable)
                } else {
                    onOpenFileBrowser(c.id)
                }
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.reconfigure)) }, onClick = {
                showMenu = false
                confirmTitle = ctx.getString(R.string.do_you_want_to_reconfigure_wine)
                confirmAction = { File(c.rootDir, ".wine/.update-timestamp").delete() }
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.export_container)) }, onClick = {
                showMenu = false
                showPreloader = true; preloaderText = ctx.getString(R.string.exporting_container)
                manager.exportContainer(c) {
                    (ctx as? Activity)?.runOnUiThread {
                        showPreloader = false
                        val backupDir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Winlator/Backups/Containers"
                        )
                        AppUtils.showToast(ctx, "Container exported successfully to ${backupDir.path}")
                    }
                }
            })
        }
    }

    if (confirmAction != null) {
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(confirmTitle) },
            confirmButton = { TextButton(onClick = { val a = confirmAction; confirmAction = null; a?.invoke() }) { Text(stringResource(R.string.ok)) } },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showPreloader) {
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text(preloaderText) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        )
    }
}

private fun runContainer(ctx: android.content.Context, container: Container) {
    if (!XrActivity.isEnabled(ctx)) {
        val intent = Intent(ctx, XServerDisplayActivity::class.java)
        intent.putExtra("container_id", container.id)
        ctx.startActivity(intent)
    } else {
        XrActivity.openIntent(ctx as Activity, container.id, null)
    }
}

@Composable
fun ContainerCard(
    container: Container,
    onRun: () -> Unit,
    onMenu: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.icon_container),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = container.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val wv = container.wineVersion
                if (!wv.isNullOrEmpty()) {
                    Text(
                        text = wv,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onRun,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(24.dp))
            }
            IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, null) }
        }
    }
}
