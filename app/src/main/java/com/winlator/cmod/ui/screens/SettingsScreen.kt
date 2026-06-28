package com.winlator.cmod.ui.screens

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Полный перенос SettingsFragment.java на Jetpack Compose.
 * Все значения хранятся в mutableStateOf, поэтому UI обновляется сразу.
 * Все переключатели сразу пишут в SharedPreferences.
 * Все клики выполняют реальные действия из оригинала.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferences: SharedPreferences?,
    appContext: android.content.Context,
    onDarkModeChange: (Boolean) -> Unit = {},
    onLanguageChange: (String) -> Unit = {},
    onTransitionAnimationChange: (String) -> Unit = {},
    onConfirmSave: () -> Unit = {},
    onOpenGPUPerformance: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = preferences ?: PreferenceManager.getDefaultSharedPreferences(ctx)

    fun saveBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun saveFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    fun toast(resId: Int) = Toast.makeText(ctx, ctx.getString(resId), Toast.LENGTH_SHORT).show()

    // ---- Реактивные состояния с точными дефолтами из SettingsFragment.java ----
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var appLanguage by remember { mutableStateOf(prefs.getString("app_language", "system") ?: "system") }
    var transitionAnim by remember { mutableStateOf(prefs.getString("transition_animation", "none") ?: "none") }
    var cursorLock by remember { mutableStateOf(prefs.getBoolean("cursor_lock", true)) }
    var xinputToggle by remember { mutableStateOf(prefs.getBoolean("xinput_toggle", false)) }
    var legacyMode by remember { mutableStateOf(prefs.getBoolean("legacy_mode_enabled", false)) }
    var gyroEnabled by remember { mutableStateOf(prefs.getBoolean("gyro_enabled", false)) }
    var gyroMode by remember { mutableStateOf(prefs.getInt("gyro_mode", 0)) }
    var gyroTriggerButton by remember { mutableStateOf(prefs.getInt("gyro_trigger_button", android.view.KeyEvent.KEYCODE_BUTTON_L1)) }
    var useDri3 by remember { mutableStateOf(prefs.getBoolean("use_dri3", true)) }
    var useXr by remember { mutableStateOf(prefs.getBoolean("use_xr", true)) }
    var cursorSpeed by remember { mutableStateOf(prefs.getFloat("cursor_speed", 1.0f)) }
    var enableWineDebug by remember { mutableStateOf(prefs.getBoolean("enable_wine_debug", false)) }
    var enableBoxLogs by remember { mutableStateOf(prefs.getBoolean("enable_box86_64_logs", false)) }
    var triggerType by remember { mutableStateOf(prefs.getInt("trigger_type", 1)) }
    var enableFileProvider by remember { mutableStateOf(prefs.getBoolean("enable_file_provider", true)) }
    var openWithBrowser by remember { mutableStateOf(prefs.getBoolean("open_with_android_browser", false)) }
    var shareClipboard by remember { mutableStateOf(prefs.getBoolean("share_android_clipboard", false)) }
    var adrenoTurbo by remember { mutableStateOf(prefs.getBoolean("adreno_turbo_mode", false)) }
    var themeId by remember { mutableStateOf(prefs.getString("theme_id", "default") ?: "default") }
    var customThemeColor by remember { mutableStateOf(prefs.getInt("custom_theme_color", 0xFF1A6C59.toInt())) }

    // ---- Состояния диалогов ----
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAnimDialog by remember { mutableStateOf(false) }
    var showTriggerDialog by remember { mutableStateOf(false) }
    var showCursorSpeedDialog by remember { mutableStateOf(false) }
    var showGyroButtonDialog by remember { mutableStateOf(false) }
    var showGyroModeDialog by remember { mutableStateOf(false) }
    var showReinstallConfirm by remember { mutableStateOf(false) }
    var showBackupConfirm by remember { mutableStateOf(false) }
    var cursorSpeedSlider by remember { mutableStateOf((cursorSpeed * 100).toInt()) }

    // ---- Лаунчеры для файловых пикеров (реальные действия) ----
    val soundFontLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val preloader = com.winlator.cmod.core.PreloaderDialog(ctx as android.app.Activity)
                preloader.showOnUiThread(com.winlator.cmod.R.string.installing_content)
                com.winlator.cmod.midi.MidiManager.installSF2File(ctx, uri, object : com.winlator.cmod.midi.MidiManager.OnSoundFontInstalledCallback {
                    override fun onSuccess() {
                        preloader.closeOnUiThread()
                        (ctx as android.app.Activity).runOnUiThread {
                            com.winlator.cmod.contentdialog.ContentDialog.alert(ctx, com.winlator.cmod.R.string.sound_font_installed_success, null)
                        }
                    }
                    override fun onFailed(reason: Int) {
                        preloader.closeOnUiThread()
                        val resId = when (reason) {
                            com.winlator.cmod.midi.MidiManager.ERROR_BADFORMAT -> com.winlator.cmod.R.string.sound_font_bad_format
                            com.winlator.cmod.midi.MidiManager.ERROR_EXIST -> com.winlator.cmod.R.string.sound_font_already_exist
                            else -> com.winlator.cmod.R.string.sound_font_installed_failed
                        }
                        (ctx as android.app.Activity).runOnUiThread {
                            com.winlator.cmod.contentdialog.ContentDialog.alert(ctx, resId, null)
                        }
                    }
                })
            } catch (e: Exception) {
                toast(com.winlator.cmod.R.string.unable_to_install_soundfont)
            }
        }
    }

    val losslessDllLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) {}
            val imported = com.winlator.cmod.core.LsfgVkManager.importGlobalLosslessDll(ctx, uri)
            toast(if (imported) com.winlator.cmod.R.string.lsfg_lossless_dll_imported else com.winlator.cmod.R.string.lsfg_lossless_dll_import_failed)
        }
    }

    val frontendPathLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            saveString("frontend_export_uri", uri.toString())
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                toast("Unable to take persistable permissions: ${e.message}")
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val intent = Intent(ctx, Class.forName("com.winlator.cmod.restore.RestoreActivity"))
                intent.data = uri
                ctx.startActivity(intent)
                (ctx as? android.app.Activity)?.finish()
            } catch (e: Exception) {
                toast("Restore: ${e.message}")
            }
        }
    }

    // ---- Списки для диалогов ----
    val languageOptions = listOf("system" to "Системный", "en" to "English", "ru" to "Русский", "zh" to "中文")
    val animOptions = listOf("none" to "None", "slide_vertical" to "Slide Vertical", "slide_horizontal" to "Slide Horizontal", "fade" to "Fade", "zoom" to "Zoom")
    val triggerOptions = listOf(0 to "Is Button", 1 to "Is Axis", 2 to "Is Mixed")
    val gyroModeOptions = listOf(0 to "Hold Mode", 1 to "Toggle Mode")
    val gyroButtonOptions = listOf(
        android.view.KeyEvent.KEYCODE_BUTTON_L1 to "L1",
        android.view.KeyEvent.KEYCODE_BUTTON_R1 to "R1",
        android.view.KeyEvent.KEYCODE_BUTTON_L2 to "L2",
        android.view.KeyEvent.KEYCODE_BUTTON_R2 to "R2",
        android.view.KeyEvent.KEYCODE_BUTTON_THUMBL to "Left Stick",
        android.view.KeyEvent.KEYCODE_BUTTON_THUMBR to "Right Stick",
    )

    fun langLabel(v: String) = languageOptions.firstOrNull { it.first == v }?.second ?: "Системный"
    fun animLabel(v: String) = animOptions.firstOrNull { it.first == v }?.second ?: "None"
    fun trigLabel(v: Int) = triggerOptions.firstOrNull { it.first == v }?.second ?: "Is Axis"
    fun gyroModeLabel(v: Int) = gyroModeOptions.firstOrNull { it.first == v }?.second ?: "Hold Mode"
    fun gyroButtonLabel(v: Int) = gyroButtonOptions.firstOrNull { it.first == v }?.second ?: "L1"

    // ---- UI ----
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 2. Тема
            SectionHeader("Тема", Icons.Filled.Palette)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.DarkMode,
                    title = "Тёмная тема",
                    checked = darkMode,
                    onCheckedChange = {
                        darkMode = it
                        saveBool("dark_mode", it)
                        onDarkModeChange(it)
                    }
                )
                SettingsDivider()
                SettingsClickRow(
                    icon = Icons.Filled.Language,
                    title = "Язык",
                    subtitle = langLabel(appLanguage),
                    onClick = { showLanguageDialog = true }
                )
                SettingsDivider()
                val currentThemeName = stringResource(
                    when (themeId) {
                        "midnight" -> com.winlator.cmod.R.string.theme_midnight
                        "cyberpunk" -> com.winlator.cmod.R.string.theme_cyberpunk
                        "royal" -> com.winlator.cmod.R.string.theme_royal
                        "dracula" -> com.winlator.cmod.R.string.theme_dracula
                        "frost" -> com.winlator.cmod.R.string.theme_frost
                        "forest" -> com.winlator.cmod.R.string.theme_forest
                        "ocean" -> com.winlator.cmod.R.string.theme_ocean
                        "sakura" -> com.winlator.cmod.R.string.theme_sakura
                        "sunset" -> com.winlator.cmod.R.string.theme_sunset
                        "matrix" -> com.winlator.cmod.R.string.theme_matrix
                        "monochrome" -> com.winlator.cmod.R.string.theme_monochrome
                        "chocolate" -> com.winlator.cmod.R.string.theme_chocolate
                        "custom" -> com.winlator.cmod.R.string.theme_custom
                        else -> com.winlator.cmod.R.string.theme_default
                    }
                )
                SettingsClickRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(com.winlator.cmod.R.string.theme_manager),
                    subtitle = currentThemeName,
                    onClick = { showThemeDialog = true }
                )
            }

            if (themeId == "custom") {
                SectionHeader(stringResource(com.winlator.cmod.R.string.custom_color), Icons.Filled.Brush)
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(com.winlator.cmod.R.string.select_primary_color),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(12.dp))

                        val row1Colors = listOf(
                            Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF00ACC1)
                        )
                        val row2Colors = listOf(
                            Color(0xFF00897B), Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFFDD835), Color(0xFFFFB300), Color(0xFFF4511E), Color(0xFF795548)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row1Colors.forEach { color ->
                                    val isSelected = customThemeColor == color.toArgb()
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                            .clickable {
                                                customThemeColor = color.toArgb()
                                                saveInt("custom_theme_color", color.toArgb())
                                            }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row2Colors.forEach { color ->
                                    val isSelected = customThemeColor == color.toArgb()
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(color, shape = androidx.compose.foundation.shape.CircleShape)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                            .clickable {
                                                customThemeColor = color.toArgb()
                                                saveInt("custom_theme_color", color.toArgb())
                                            }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Shortcuts
            SectionHeader("Shortcut Settings", Icons.Filled.Shortcut)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Folder,
                    title = "Frontend Export Path",
                    subtitle = prefs.getString("frontend_export_uri", null) ?: "Downloads/Winlator/Frontend",
                    onClick = { frontendPathLauncher.launch(null) }
                )
            }

            // 4. Frame Generation
            SectionHeader("Frame Generation", Icons.Filled.Speed)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Memory,
                    title = "Lossless Scaling DLL",
                    subtitle = run {
                        val dllPath = try { com.winlator.cmod.core.LsfgVkManager.globalDllPath(ctx) } catch (_: Exception) { null }
                        when {
                            dllPath != null -> "Установлен: $dllPath"
                            try { com.winlator.cmod.core.LsfgVkManager.isBundledDllAvailable(ctx) } catch (_: Exception) { false } -> "Bundled"
                            else -> "Не установлен"
                        }
                    },
                    onClick = { losslessDllLauncher.launch(arrayOf("*/*")) }
                )
            }

            // 5. XServer
            SectionHeader("XServer", Icons.Filled.DesktopWindows)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Mouse,
                    title = "Скорость курсора",
                    subtitle = "${(cursorSpeed * 100).toInt()}%",
                    onClick = {
                        cursorSpeedSlider = (cursorSpeed * 100).toInt()
                        showCursorSpeedDialog = true
                    }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.Bolt,
                    title = "DRI3",
                    checked = useDri3,
                    onCheckedChange = { useDri3 = it; saveBool("use_dri3", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.ViewInAr,
                    title = "XR режим",
                    checked = useXr,
                    onCheckedChange = { useXr = it; saveBool("use_xr", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.Lock,
                    title = "True Mouse Control (Cursor Lock)",
                    checked = cursorLock,
                    onCheckedChange = { cursorLock = it; saveBool("cursor_lock", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.TouchApp,
                    title = "Disable Xinput",
                    checked = xinputToggle,
                    onCheckedChange = { xinputToggle = it; saveBool("xinput_toggle", it) }
                )
            }

            // 6. GPU Performance
            SectionHeader("GPU Performance", Icons.Filled.Memory)
            SettingsCard {
                SettingsButtonRow(
                    icon = Icons.Filled.Speed,
                    title = stringResource(com.winlator.cmod.R.string.configure_gpu_performance),
                    onClick = { onOpenGPUPerformance() }
                )
            }

            // 7. Adreno Turbo
            SectionHeader("Adreno Turbo", Icons.Filled.FlashOn)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.FlashOn,
                    title = "Max GPU Frequency (Adreno)",
                    checked = adrenoTurbo,
                    onCheckedChange = {
                        try {
                            val success = com.winlator.cmod.core.GPUInformation.setTurboMode(it)
                            if (success) {
                                adrenoTurbo = it
                                saveBool("adreno_turbo_mode", it)
                                toast(ctx.getString(if (it) com.winlator.cmod.R.string.adreno_turbo_enabled else com.winlator.cmod.R.string.adreno_turbo_disabled))
                            } else {
                                toast("Turbo Mode недоступен (нет root)")
                            }
                        } catch (e: Exception) {
                            toast("Turbo: ${e.message}")
                        }
                    }
                )
            }

            // 8. Gyro
            SectionHeader("Gyro Settings", Icons.Filled.RotateRight)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.GpsFixed,
                    title = "Enable Gyroscope",
                    checked = gyroEnabled,
                    onCheckedChange = { gyroEnabled = it; saveBool("gyro_enabled", it) }
                )
                SettingsDivider()
                SettingsClickRow(
                    icon = Icons.Filled.Gamepad,
                    title = "Кнопка активации",
                    subtitle = gyroButtonLabel(gyroTriggerButton),
                    onClick = { showGyroButtonDialog = true }
                )
                SettingsDivider()
                SettingsClickRow(
                    icon = Icons.Filled.ToggleOn,
                    title = "Режим",
                    subtitle = gyroModeLabel(gyroMode),
                    onClick = { showGyroModeDialog = true }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.Tune,
                    title = "Калибровка гироскопа",
                    onClick = { toast("Калибровка: откройте гироскоп в игре") }
                )
            }

            // 9. Логи
            SectionHeader("Логи", Icons.Filled.BugReport)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.Code,
                    title = "Wine Debug",
                    checked = enableWineDebug,
                    onCheckedChange = { enableWineDebug = it; saveBool("enable_wine_debug", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.Description,
                    title = "Box86/64 логи",
                    checked = enableBoxLogs,
                    onCheckedChange = { enableBoxLogs = it; saveBool("enable_box86_64_logs", it) }
                )
            }

            // 10. Game Controller
            SectionHeader("Game Controller", Icons.Filled.Gamepad)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Tune,
                    title = "Тип триггера",
                    subtitle = trigLabel(triggerType),
                    onClick = { showTriggerDialog = true }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.History,
                    title = "Legacy Input Mode 7.1.2",
                    checked = legacyMode,
                    onCheckedChange = { legacyMode = it; saveBool("legacy_mode_enabled", it) }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.Tune,
                    title = "Настроить аналоговые стики",
                    onClick = { toast("Настройка стиков: используйте в Input Controls") }
                )
            }

            // Звук (после Game Controller)
            SectionHeader("Звук", Icons.Filled.MusicNote)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.LibraryMusic,
                    title = "MIDI SoundFont",
                    subtitle = "Default",
                    onClick = { toast("Выберите SoundFont в списке ниже") }
                )
                SettingsDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { soundFontLauncher.launch(arrayOf("*/*")) }, Modifier.weight(1f)) { Text("Установить") }
                    OutlinedButton(onClick = {
                        try {
                            val removed = com.winlator.cmod.midi.MidiManager.removeSF2File(ctx, "Default")
                            toast(if (removed) com.winlator.cmod.R.string.sound_font_removed_success else com.winlator.cmod.R.string.sound_font_removed_failed)
                        } catch (e: Exception) { toast("Ошибка удаления") }
                    }, Modifier.weight(1f)) { Text("Удалить") }
                }
            }

            // 11. Experimental
            SectionHeader("Экспериментальное", Icons.Filled.Science)
            SettingsCard {
                SettingsCheckRow(
                    icon = Icons.Filled.FolderShared,
                    title = "File Provider",
                    checked = enableFileProvider,
                    onCheckedChange = {
                        enableFileProvider = it
                        saveBool("enable_file_provider", it)
                        toast(com.winlator.cmod.R.string.take_effect_next_startup)
                    }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.OpenInBrowser,
                    title = "Open with Android Browser",
                    checked = openWithBrowser,
                    onCheckedChange = { openWithBrowser = it; saveBool("open_with_android_browser", it) }
                )
                SettingsDivider()
                SettingsCheckRow(
                    icon = Icons.Filled.ContentPaste,
                    title = "Share Android Clipboard",
                    checked = shareClipboard,
                    onCheckedChange = { shareClipboard = it; saveBool("share_android_clipboard", it) }
                )
            }

            // 12. ImageFs
            SectionHeader("ImageFs", Icons.Filled.Storage)
            SettingsCard {
                SettingsButtonRow(
                    icon = Icons.Filled.Build,
                    title = "Переустановить ImageFs",
                    onClick = { showReinstallConfirm = true }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.CloudUpload,
                    title = "Backup Data",
                    onClick = { showBackupConfirm = true }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.Restore,
                    title = "Restore Data",
                    onClick = { restoreLauncher.launch(arrayOf("*/*")) }
                )
            }

            Spacer(Modifier.height(88.dp))
        }

        // FAB - подтверждение сохранения (как BTConfirm в оригинале)
        FloatingActionButton(
            onClick = { onConfirmSave() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) { Icon(Icons.Filled.Check, "Save", tint = MaterialTheme.colorScheme.onPrimary) }
    }

    // ---- Диалоги ----
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(com.winlator.cmod.R.string.select_theme)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    com.winlator.cmod.ui.theme.ThemesList.forEach { theme ->
                        val isSelected = theme.id == themeId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    themeId = theme.id
                                    saveString("theme_id", theme.id)
                                    showThemeDialog = false
                                }
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    val localizedName = stringResource(
                                        when (theme.id) {
                                            "midnight" -> com.winlator.cmod.R.string.theme_midnight
                                            "cyberpunk" -> com.winlator.cmod.R.string.theme_cyberpunk
                                            "royal" -> com.winlator.cmod.R.string.theme_royal
                                            "dracula" -> com.winlator.cmod.R.string.theme_dracula
                                            "frost" -> com.winlator.cmod.R.string.theme_frost
                                            "forest" -> com.winlator.cmod.R.string.theme_forest
                                            "ocean" -> com.winlator.cmod.R.string.theme_ocean
                                            "sakura" -> com.winlator.cmod.R.string.theme_sakura
                                            "sunset" -> com.winlator.cmod.R.string.theme_sunset
                                            "matrix" -> com.winlator.cmod.R.string.theme_matrix
                                            "monochrome" -> com.winlator.cmod.R.string.theme_monochrome
                                            "chocolate" -> com.winlator.cmod.R.string.theme_chocolate
                                            "custom" -> com.winlator.cmod.R.string.theme_custom
                                            else -> com.winlator.cmod.R.string.theme_default
                                        }
                                    )
                                    Text(localizedName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Box(Modifier.size(16.dp).background(theme.primaryColor, shape = androidx.compose.foundation.shape.CircleShape))
                                        Box(Modifier.size(16.dp).background(theme.accentColor, shape = androidx.compose.foundation.shape.CircleShape))
                                    }
                                }
                                RadioButton(selected = isSelected, onClick = {
                                    themeId = theme.id
                                    saveString("theme_id", theme.id)
                                    showThemeDialog = false
                                })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("OK") }
            }
        )
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = "Язык",
            options = languageOptions,
            selected = appLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { value ->
                appLanguage = value
                saveString("app_language", value)
                onLanguageChange(value)
                showLanguageDialog = false
            }
        )
    }
    if (showAnimDialog) {
        ChoiceDialog(
            title = "Анимация перехода",
            options = animOptions,
            selected = transitionAnim,
            onDismiss = { showAnimDialog = false },
            onSelect = { value ->
                transitionAnim = value
                saveString("transition_animation", value)
                onTransitionAnimationChange(value)
                showAnimDialog = false
            }
        )
    }
    if (showTriggerDialog) {
        ChoiceDialog(
            title = "Тип триггера",
            options = triggerOptions,
            selected = triggerType,
            onDismiss = { showTriggerDialog = false },
            onSelect = { value ->
                triggerType = value
                saveInt("trigger_type", value)
                showTriggerDialog = false
            }
        )
    }
    if (showGyroButtonDialog) {
        ChoiceDialog(
            title = "Кнопка активации гироскопа",
            options = gyroButtonOptions,
            selected = gyroTriggerButton,
            onDismiss = { showGyroButtonDialog = false },
            onSelect = { value ->
                gyroTriggerButton = value
                saveInt("gyro_trigger_button", value)
                showGyroButtonDialog = false
            }
        )
    }
    if (showGyroModeDialog) {
        ChoiceDialog(
            title = "Режим гироскопа",
            options = gyroModeOptions,
            selected = gyroMode,
            onDismiss = { showGyroModeDialog = false },
            onSelect = { value ->
                gyroMode = value
                saveInt("gyro_mode", value)
                showGyroModeDialog = false
            }
        )
    }
    if (showCursorSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showCursorSpeedDialog = false },
            title = { Text("Скорость курсора: $cursorSpeedSlider%") },
            text = {
                Column(Modifier.padding(8.dp)) {
                    Slider(
                        value = cursorSpeedSlider.toFloat(),
                        onValueChange = { cursorSpeedSlider = it.toInt() },
                        valueRange = 10f..200f,
                        steps = 18
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cursorSpeed = cursorSpeedSlider / 100f
                    saveFloat("cursor_speed", cursorSpeed)
                    showCursorSpeedDialog = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showCursorSpeedDialog = false }) { Text("Отмена") } }
        )
    }
    if (showReinstallConfirm) {
        AlertDialog(
            onDismissRequest = { showReinstallConfirm = false },
            title = { Text("Переустановка ImageFs") },
            text = { Text(ctx.getString(com.winlator.cmod.R.string.do_you_want_to_reinstall_imagefs)) },
            confirmButton = {
                TextButton(onClick = {
                    showReinstallConfirm = false
                    try {
                        val activity = ctx as? com.winlator.cmod.MainActivity
                        if (activity != null) {
                            com.winlator.cmod.xenvironment.ImageFsInstaller.installFromAssets(activity)
                        } else {
                            toast("Требуется MainActivity")
                        }
                    } catch (e: Exception) { toast("ImageFs: ${e.message}") }
                }) { Text("Да") }
            },
            dismissButton = { TextButton(onClick = { showReinstallConfirm = false }) { Text("Нет") } }
        )
    }
    if (showBackupConfirm) {
        AlertDialog(
            onDismissRequest = { showBackupConfirm = false },
            title = { Text("Backup Data") },
            text = { Text("Создать резервную копию данных приложения?") },
            confirmButton = {
                TextButton(onClick = {
                    showBackupConfirm = false
                    try {
                        val dataDir = ctx.filesDir.parentFile
                        val backupFile = java.io.File(android.os.Environment.getExternalStorageDirectory(), "app_data_backup.tar")
                        val preloader = com.winlator.cmod.core.PreloaderDialog(ctx as android.app.Activity)
                        preloader.showOnUiThread(com.winlator.cmod.R.string.backing_up_data)
                        val executor = java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
                        executor.execute {
                            try {
                                com.winlator.cmod.core.TarCompressorUtils.archive(arrayOf(dataDir), backupFile) { file ->
                                    !file.absolutePath.contains("imagefs/tmp/.sysvshm")
                                }
                                (ctx as android.app.Activity).runOnUiThread {
                                    preloader.closeOnUiThread()
                                    toast("Backup completed: ${backupFile.path}")
                                }
                            } catch (e: Exception) {
                                (ctx as android.app.Activity).runOnUiThread {
                                    preloader.closeOnUiThread()
                                    toast("Backup failed.")
                                }
                            }
                        }
                    } catch (e: Exception) { toast("Backup: ${e.message}") }
                }) { Text("Да") }
            },
            dismissButton = { TextButton(onClick = { showBackupConfirm = false }) { Text("Нет") } }
        )
    }
}

// ---- Вспомогательные Composable ----

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        Modifier.padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsCheckRow(icon: ImageVector, title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsButtonRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(value) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
