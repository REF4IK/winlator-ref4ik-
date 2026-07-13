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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import com.winlator.cmod.ui.theme.WinlatorTheme
import com.winlator.cmod.xenvironment.ImageFsInstaller

class MainActivity : AppCompatActivity() {
    companion object {
        @JvmField
        var PACKAGE_NAME: String = ""

        const val PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE: Byte = 1
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
                var darkMode by remember { mutableStateOf(isDarkMode) }

                WinlatorTheme(darkTheme = darkMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        WinlatorApp(
                            isDarkMode = darkMode,
                            preferences = preferences,
                            initialScreen = initialScreen,
                            selectedProfileId = selectedProfileId,
                            onDarkModeChange = { enabled ->
                                darkMode = enabled
                                isDarkMode = enabled
                                preferences.edit().putBoolean("dark_mode", enabled).apply()
                                recreate()
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
                        )
                    }
                }
            }
        }
        rootLayout.addView(composeView, android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        setContentView(rootLayout)

        // Request permissions
        requestAppPermissions()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showAllFilesAccessDialog()
        }
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
        if (requestCode == PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE.toInt()) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreate()
            } else {
                finish()
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

    private fun requestAppPermissions(): Boolean {
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasManageStoragePermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                Environment.isExternalStorageManager()

        if (hasWritePermission && hasReadPermission && hasManageStoragePermission) {
            return false
        }

        if (!hasWritePermission || !hasReadPermission) {
            val perms = arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
            ActivityCompat.requestPermissions(
                this, perms,
                PERMISSION_WRITE_EXTERNAL_STORAGE_REQUEST_CODE.toInt(),
            )
        }
        return true
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
        }
    }

    private fun showAboutDialog() {
        val dialog = ContentDialog(this, R.layout.about_dialog)
        dialog.findViewById<android.widget.LinearLayout>(R.id.LLBottomBar).visibility = android.view.View.GONE
        dialog.show()
    }
}
