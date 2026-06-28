package com.winlator.cmod.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R

data class XServerMenuItem(
    val id: String,
    val icon: ImageVector,
    val title: String,
)

private val xserverMenuItems = listOf(
    XServerMenuItem("keyboard", Icons.Filled.Keyboard, "Keyboard"),
    XServerMenuItem("input_controls", Icons.Filled.Gamepad, "Input Controls"),
    XServerMenuItem("toggle_fullscreen", Icons.Filled.Fullscreen, "Toggle Fullscreen"),
    XServerMenuItem("pip_mode", Icons.Filled.PictureInPictureAlt, "PiP Mode"),
    XServerMenuItem("frame_generation", Icons.Filled.Speed, "Frame Generation"),
    XServerMenuItem("screen_effects", Icons.Filled.Tune, "Screen Effects"),
    XServerMenuItem("task_manager", Icons.Filled.Memory, "Task Manager"),
    XServerMenuItem("fps_counter", Icons.Filled.BugReport, "FPS Counter"),
    XServerMenuItem("active_windows", Icons.Filled.Window, "Active Windows"),
    XServerMenuItem("pause", Icons.Filled.Pause, "Pause/Resume"),
    XServerMenuItem("winetricks", Icons.Filled.WineBar, "Winetricks"),
    XServerMenuItem("terminal", Icons.Filled.Terminal, "Debug Terminal"),
    XServerMenuItem("logs", Icons.Filled.Description, "Logs"),
    XServerMenuItem("exit", Icons.Filled.ExitToApp, "Exit"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XServerMenuDialog(
    isPaused: Boolean = false,
    onDismiss: () -> Unit,
    onItemSelected: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.9f).padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Menu, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "XServer Menu",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                HorizontalDivider()

                // Items
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                ) {
                    xserverMenuItems.forEach { item ->
                        val isPauseItem = item.id == "pause"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemSelected(item.id) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = if (isPauseItem && isPaused) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                if (isPauseItem && isPaused) "Resume" else item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (isPauseItem && isPaused) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                        if (item != xserverMenuItems.last()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}
