package com.winlator.cmod

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.withContext
import java.io.File

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
                val prefsRef = preferences
                val themeMode = ThemePrefs.resolveThemeMode(prefsRef)
                val themeModeState = prefsRef.observeString(ThemePrefs.THEME_MODE, themeMode)
                val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
                val darkMode = ThemePrefs.isDarkFromMode(themeModeState.value, systemDark)
                val wallpaperPath by prefsRef.observeString(ThemePrefs.UI_WALLPAPER, "")
                val wallpaperBlur by prefsRef.observeInt(ThemePrefs.UI_WALLPAPER_BLUR, 20)
                val wallpaperDarken by prefsRef.observeInt(ThemePrefs.UI_WALLPAPER_DARKEN, 40)

                WinlatorTheme(darkTheme = darkMode) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        var wallpaperBitmap by remember(wallpaperPath, wallpaperBlur) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(wallpaperPath, wallpaperBlur) {
                            wallpaperBitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                if (wallpaperPath.isNotEmpty()) {
                                    loadWallpaperBitmap(wallpaperPath, wallpaperBlur)
                                } else null
                            }
                        }
                        if (wallpaperBitmap != null) {
                            Image(
                                bitmap = wallpaperBitmap!!.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = (wallpaperDarken.coerceIn(0, 100) / 100f)))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                            )
                        }
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

    private fun loadWallpaperBitmap(path: String, blurRadius: Int): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxDim / (sample * 2) >= 1024) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val source = BitmapFactory.decodeFile(path, options) ?: return null

            val radius = blurRadius.coerceIn(0, 100)
            if (radius <= 0) return source

            // Блюр на уменьшенной копии, затем возврат к рабочему размеру
            val small = Bitmap.createScaledBitmap(source, 1024, (1024.0 * source.height / source.width).toInt(), true)
            if (small !== source) source.recycle()
            val blurred = stackBlur(small, radius)
            if (blurred !== small) small.recycle()
            blurred
        } catch (e: Exception) {
            android.util.Log.e("WinlatorTheme", "Wallpaper load failed", e)
            null
        }
    }

    private fun stackBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        var pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val passes = 3
        var r = radius
        for (pass in 0 until passes) {
            r = (radius * (pass + 1)) / passes
            if (r < 1) r = 1
            pixels = boxBlur1D(pixels, width, height, r, horizontal = true)
            pixels = boxBlur1D(pixels, width, height, r, horizontal = false)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun boxBlur1D(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
        val output = IntArray(pixels.size)
        val length = if (horizontal) width else height
        val other = if (horizontal) height else width
        for (line in 0 until other) {
            // скользящее окно
            var accR = 0; var accG = 0; var accB = 0
            var count = 0
            for (i in -radius until length + radius) {
                if (i + radius < length) {
                    val idx = if (horizontal) (line * width + (i + radius).coerceIn(0, length - 1))
                    else ((i + radius).coerceIn(0, length - 1) * width + line)
                    val p = pixels[idx]
                    accR += (p shr 16) and 0xFF
                    accG += (p shr 8) and 0xFF
                    accB += p and 0xFF
                    count++
                }
                if (i - radius - 1 >= 0) {
                    val idx = if (horizontal) (line * width + (i - radius - 1).coerceIn(0, length - 1))
                    else ((i - radius - 1).coerceIn(0, length - 1) * width + line)
                    val p = pixels[idx]
                    accR -= (p shr 16) and 0xFF
                    accG -= (p shr 8) and 0xFF
                    accB -= p and 0xFF
                    count--
                }
                if (i >= 0 && i < length && count > 0) {
                    val outIdx = if (horizontal) (line * width + i) else (i * width + line)
                    output[outIdx] = (0xFF shl 24) or
                        (((accR / count).coerceIn(0, 255)) shl 16) or
                        (((accG / count).coerceIn(0, 255)) shl 8) or
                        ((accB / count).coerceIn(0, 255))
                }
            }
        }
        return output
    }
}
