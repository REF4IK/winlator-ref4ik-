package com.winlator.cmod

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.winlator.cmod.core.MmkvPreferences
import com.google.android.material.appbar.MaterialToolbar
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.contentdialog.SaveEditDialog
import com.winlator.cmod.contentdialog.SaveSettingsDialog
import com.winlator.cmod.core.Callback
import com.winlator.cmod.core.PreloaderDialog
import com.winlator.cmod.saves.Save
import com.winlator.cmod.saves.SaveManager
import com.winlator.cmod.steam.SteamLibraryActivity
import com.winlator.cmod.ui.WinlatorApp
import com.winlator.cmod.ui.theme.ThemePrefs
import com.winlator.cmod.ui.theme.WinlatorTheme
import com.winlator.cmod.ui.theme.observeFloat
import com.winlator.cmod.ui.theme.observeInt
import com.winlator.cmod.ui.theme.observeString
import com.winlator.cmod.xenvironment.ImageFsInstaller

class MainActivity : AppCompatActivity() {
    companion object {
        @JvmField
        var PACKAGE_NAME: String = ""

        const val PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE: Byte = 1
        const val PERMISSION_NOTIFICATIONS_REQUEST_CODE: Byte = 5
        const val OPEN_FILE_REQUEST_CODE: Byte = 2
        const val EDIT_INPUT_CONTROLS_REQUEST_CODE: Byte = 3
        const val OPEN_DIRECTORY_REQUEST_CODE: Byte = 4

        @JvmField
        val CONTAINER_PATTERN_COMPRESSION_LEVEL: Byte = 9
    }

    private lateinit var preferences: SharedPreferences
    private lateinit var containerManager: ContainerManager

    // Compatibility fields for old Java fragments
    @JvmField
    val preloaderDialog = PreloaderDialog(this)

    @JvmField
    var saveSettingsDialog: SaveSettingsDialog? = null

    @JvmField
    var saveEditDialog: SaveEditDialog? = null

    private var openFileCallback: Callback<Uri>? = null

    private val saveManager by lazy { SaveManager(this) }

    @JvmField
    var isDarkMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PACKAGE_NAME.isEmpty()) {
            PACKAGE_NAME = packageName
        }

        // Корневой layout: скрытый Toolbar + ComposeView
        // Toolbar нужен, чтобы старые Java-фрагменты могли вызывать
        // getSupportActionBar() без NullPointerException
        val rootLayout = android.widget.FrameLayout(this)

        val hiddenToolbar = MaterialToolbar(this).apply {
            id = android.view.View.generateViewId()
            layoutParams = android.view.ViewGroup.LayoutParams(0, 0)
            visibility = android.view.View.GONE
        }
        rootLayout.addView(hiddenToolbar)

        // Важно: вызываем setSupportActionBar ДО setContentView
        setSupportActionBar(hiddenToolbar)
        supportActionBar?.hide()

        preferences = MmkvPreferences()
        containerManager = ContainerManager(this)

        isDarkMode = preferences.getBoolean("dark_mode", false)

        val editInputControls = intent.getBooleanExtra("edit_input_controls", false)
        val selectedProfileId = intent.getIntExtra("selected_profile_id", 0)

        if (editInputControls && selectedProfileId > 0) {
            val editorIntent = Intent(this, ControlsEditorActivity::class.java).apply {
                putExtra("profile_id", selectedProfileId)
            }
            startActivityForResult(editorIntent, EDIT_INPUT_CONTROLS_REQUEST_CODE.toInt())
        }

        val initialScreen = if (editInputControls) com.winlator.cmod.ui.navigation.Screen.InputControls else com.winlator.cmod.ui.navigation.Screen.Containers

        // Добавляем ComposeView в rootLayout
        val composeView = ComposeView(this).apply {
            setContent {
                val prefsRef = preferences
                val themeMode = ThemePrefs.resolveThemeMode(prefsRef)
                val themeModeState = prefsRef.observeString(ThemePrefs.THEME_MODE, themeMode)
                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                val darkMode = ThemePrefs.isDarkFromMode(themeModeState.value, systemDark)
                val wallpaperPath by prefsRef.observeString(ThemePrefs.UI_WALLPAPER, "")
                val wallpaperBlur by prefsRef.observeInt(ThemePrefs.UI_WALLPAPER_BLUR, 20)
                val wallpaperDarken by prefsRef.observeInt(ThemePrefs.UI_WALLPAPER_DARKEN, 40)
                // Подгонка хранится отдельно для портрета и ландшафта
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val wallpaperScale by prefsRef.observeFloat(com.winlator.cmod.ui.screens.wallpaperScaleKey(isLandscape), 1f)
                val wallpaperOffsetX by prefsRef.observeFloat(com.winlator.cmod.ui.screens.wallpaperOffsetXKey(isLandscape), 0f)
                val wallpaperOffsetY by prefsRef.observeFloat(com.winlator.cmod.ui.screens.wallpaperOffsetYKey(isLandscape), 0f)

                WinlatorTheme(darkTheme = darkMode) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.winlator.cmod.ui.screens.WallpaperLayer(
                            path = wallpaperPath,
                            blur = wallpaperBlur,
                            darken = wallpaperDarken,
                            scale = wallpaperScale,
                            offsetRatioX = wallpaperOffsetX,
                            offsetRatioY = wallpaperOffsetY,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent,
                        ) {
                            WinlatorApp(
                                isDarkMode = darkMode,
                                preferences = prefsRef,
                                initialScreen = initialScreen,
                                selectedProfileId = selectedProfileId,
                                onDarkModeChange = { enabled ->
                                    preferences.edit().putString(
                                        ThemePrefs.THEME_MODE,
                                        if (enabled) ThemePrefs.THEME_MODE_DARK else ThemePrefs.THEME_MODE_LIGHT,
                                    ).apply()
                                    preferences.edit().putBoolean("dark_mode", enabled).apply()
                                },
                                onLanguageChange = { lang ->
                                    applyLanguage(lang)
                                    preferences.edit().putString("app_language", lang).apply()
                                    recreate()
                                },
                                onOpenSteam = {
                                    startActivity(Intent(this@MainActivity, SteamLibraryActivity::class.java))
                                },
                                onOpenFileManager = {
                                    val intent = Intent(this@MainActivity, FileManagerActivity::class.java)
                                    startActivityForResult(intent, 1001)
                                },
                                onImageFsReady = { requestPermissionsAfterInstall() },
                            )
                        }
                    }
                }
            }
        }
        rootLayout.addView(composeView, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        setContentView(rootLayout)
    }

    // ---------- Compatibility methods for old Java fragments ----------

    fun onSaveAdded() {
        // Called from SaveEditDialog/SaveSettingsDialog after save operation
    }

    fun showSaveEditDialog(save: Save) {
        saveEditDialog = SaveEditDialog(this, saveManager, containerManager, save).apply {
            show()
        }
    }

    fun setOpenFileCallback(callback: Callback<Uri>) {
        openFileCallback = callback
    }

    fun toggleDrawer() {
        // Drawer toggle handled in Compose layer
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE.toInt() -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionStep = 1
                    requestNextPermission()
                } else {
                    finish()
                }
            }
            PERMISSION_NOTIFICATIONS_REQUEST_CODE.toInt() -> {
                permissionStep = 2
                requestNextPermission()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_FILE_REQUEST_CODE.toInt() && resultCode == RESULT_OK && data != null) {
            openFileCallback?.call(data.data)
            openFileCallback = null
        } else if (requestCode == EDIT_INPUT_CONTROLS_REQUEST_CODE.toInt()) {
            setResult(RESULT_OK)
            finish()
        }
    }

    /**
     * Запрос runtime-разрешений после завершения распаковки ImageFs и закрытия
     * диалога первого запуска. Вызывается из Compose через onImageFsReady.
     * Шаги строго по очереди, каждый следующий — только после ответа
     * пользователя на предыдущий: память → уведомления (Android 13+) →
     * доступ ко всем файлам (Android 11+).
     */
    private var permissionStep = 0

    fun requestPermissionsAfterInstall() {
        permissionStep = 0
        requestNextPermission()
    }

    private fun requestNextPermission() {
        when (permissionStep) {
            0 -> {
                val hasWritePermission = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                val hasReadPermission = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasWritePermission || !hasReadPermission) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                        ),
                        PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE.toInt(),
                    )
                } else {
                    permissionStep = 1
                    requestNextPermission()
                }
            }
            1 -> {
                val needNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                if (needNotifications) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        PERMISSION_NOTIFICATIONS_REQUEST_CODE.toInt(),
                    )
                } else {
                    permissionStep = 2
                    requestNextPermission()
                }
            }
            2 -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                    showAllFilesAccessDialog()
                }
            }
        }
    }

    private fun showAllFilesAccessDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.all_files_access_required)
            .setMessage(R.string.all_files_access_message)
            .setPositiveButton(R.string.okay) { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyLanguage(languageCode: String) {
        when (languageCode) {
            "system" -> androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.getEmptyLocaleList()
            )
            "en" -> androidx.core.os.LocaleListCompat.forLanguageTags("en").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
            "ru" -> androidx.core.os.LocaleListCompat.forLanguageTags("ru").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
            "zh" -> androidx.core.os.LocaleListCompat.forLanguageTags("zh").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
            "pt" -> androidx.core.os.LocaleListCompat.forLanguageTags("pt").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
            "pt-rBR" -> androidx.core.os.LocaleListCompat.forLanguageTags("pt-BR").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
            "pl" -> androidx.core.os.LocaleListCompat.forLanguageTags("pl").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
            "es" -> androidx.core.os.LocaleListCompat.forLanguageTags("es").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
            "ja" -> androidx.core.os.LocaleListCompat.forLanguageTags("ja").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
            "ar" -> androidx.core.os.LocaleListCompat.forLanguageTags("ar").let {
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(it)
            }
        }
    }

    private fun showAboutDialog() {
        val dialog = ContentDialog(this, R.layout.about_dialog)
        dialog.findViewById<android.widget.LinearLayout>(R.id.LLBottomBar).visibility = android.view.View.GONE
        dialog.show()
    }
}
