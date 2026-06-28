package com.winlator.cmod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.ui.screens.ShortcutSettingsScreen
import com.winlator.cmod.ui.theme.WinlatorTheme
import java.io.File

class ShortcutSettingsActivity : ComponentActivity() {
    companion object {
        const val EXTRA_CONTAINER_ID = "container_id"
        const val EXTRA_SHORTCUT_PATH = "shortcut_path"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val containerId = intent.getIntExtra(EXTRA_CONTAINER_ID, -1)
        val shortcutPath = intent.getStringExtra(EXTRA_SHORTCUT_PATH)

        if (containerId < 0 || shortcutPath == null) {
            finish()
            return
        }

        val manager = ContainerManager(this)
        val container = manager.getContainerById(containerId)
        val shortcutFile = File(shortcutPath)
        if (container == null || !shortcutFile.exists()) {
            finish()
            return
        }
        val shortcut = Shortcut(container, shortcutFile)

        setContent {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val isDarkMode = prefs.getBoolean("dark_mode", false)
            WinlatorTheme(darkTheme = isDarkMode) {
                ShortcutSettingsScreen(
                    shortcut = shortcut,
                    onBack = { finish() }
                )
            }
        }
    }
}
