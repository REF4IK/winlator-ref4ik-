package com.winlator.cmod

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.ui.screens.ControlsEditorScreen
import com.winlator.cmod.ui.theme.ThemePrefs
import com.winlator.cmod.ui.theme.WinlatorTheme

class ControlsEditorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppUtils.hideSystemUI(this)

        val profileId = intent.getIntExtra("profile_id", 0)
        val prefs = MmkvPreferences()

        setContent {
            val darkMode = ThemePrefs.isDarkFromMode(
                ThemePrefs.resolveThemeMode(prefs),
                isSystemInDarkTheme(),
            )
            WinlatorTheme(darkTheme = darkMode) {
                ControlsEditorScreen(
                    profileId = profileId,
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_up)
    }
}
