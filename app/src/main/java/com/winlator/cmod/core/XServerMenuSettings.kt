package com.winlator.cmod.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.ui.graphics.vector.ImageVector

data class XServerMenuItem(
    val id: String,
    val icon: ImageVector,
    val title: String,
)

/**
 * Пункты меню X-Server: пользователь может скрывать неиспользуемые
 * в настройках кастомизации. Exit скрыть нельзя.
 */
object XServerMenuSettings {
    const val PREF_HIDDEN = "xserver_menu_hidden"

    const val ID_EXIT = "exit"

    const val ID_PAUSE = "pause"
    const val ID_WINETRICKS = "winetricks"
    const val ID_TERMINAL = "terminal"

    val DEFAULT_HIDDEN: Set<String> = setOf(ID_PAUSE, ID_WINETRICKS, ID_TERMINAL)

    val items = listOf(
        XServerMenuItem("keyboard", Icons.Filled.Keyboard, "Keyboard"),
        XServerMenuItem("input_controls", Icons.Filled.Gamepad, "Input Controls"),
        XServerMenuItem("toggle_fullscreen", Icons.Filled.Fullscreen, "Toggle Fullscreen"),
        XServerMenuItem("pip_mode", Icons.Filled.PictureInPictureAlt, "PiP Mode"),
        XServerMenuItem("frame_generation", Icons.Filled.Speed, "Frame Generation"),
        XServerMenuItem("screen_effects", Icons.Filled.Tune, "Screen Effects"),
        XServerMenuItem("task_manager", Icons.Filled.Memory, "Task Manager"),
        XServerMenuItem("fps_counter", Icons.Filled.BugReport, "FPS Counter"),
        XServerMenuItem("active_windows", Icons.Filled.Window, "Active Windows"),
        XServerMenuItem(ID_PAUSE, Icons.Filled.Pause, "Pause/Resume"),
        XServerMenuItem(ID_WINETRICKS, Icons.Filled.WineBar, "Winetricks"),
        XServerMenuItem(ID_TERMINAL, Icons.Filled.Terminal, "Debug Terminal"),
        XServerMenuItem("logs", Icons.Filled.Description, "Logs"),
        XServerMenuItem(ID_EXIT, Icons.Filled.ExitToApp, "Exit"),
    )

    fun hiddenIds(): Set<String> {
        val stored = MmkvPreferences().getStringSet(PREF_HIDDEN, null) ?: return HashSet(DEFAULT_HIDDEN)
        return stored.filterTo(HashSet()) { it != ID_EXIT }
    }

    fun setHidden(ids: Set<String>) {
        val filtered = ids.filterTo(HashSet()) { it != ID_EXIT }
        MmkvPreferences().edit().putStringSet(PREF_HIDDEN, filtered).apply()
    }

    fun isVisible(id: String): Boolean = id !in hiddenIds()

    fun visibleItems(): List<XServerMenuItem> = items.filter { isVisible(it.id) }
}
