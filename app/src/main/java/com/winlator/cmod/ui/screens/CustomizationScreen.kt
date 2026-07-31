package com.winlator.cmod.ui.screens

import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.ui.theme.ThemePrefs
import com.winlator.cmod.ui.theme.observeString
import org.json.JSONObject
import java.io.File

/**
 * Подменю «Кастомизация интерфейса»: масштаб, шрифт, углы, своя тема, обои, экспорт/импорт.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizationScreen(
    preferences: SharedPreferences?,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = preferences ?: MmkvPreferences()

    fun saveBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
    fun saveInt(key: String, value: Int) { prefs.edit().putInt(key, value).apply() }
    fun saveFloat(key: String, value: Float) { prefs.edit().putFloat(key, value).apply() }
    fun saveString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    // Реактивное наблюдение за текущей темой: секция «Своя тема» мгновенно
    // реагирует на выбор темы во вложенном «Менеджере тем».
    val themeId by prefs.observeString("theme_id", "midnight")
    var uiScale by remember { mutableStateOf(prefs.getFloat(ThemePrefs.UI_SCALE, 1f)) }
    var fontScale by remember { mutableStateOf(prefs.getFloat(ThemePrefs.FONT_SCALE, 1f)) }
    var cornerRadius by remember { mutableStateOf(prefs.getString(ThemePrefs.CORNER_RADIUS, "small") ?: "small") }
    var uiWallpaper by remember { mutableStateOf(prefs.getString(ThemePrefs.UI_WALLPAPER, "") ?: "") }
    var uiWallpaperBlur by remember { mutableStateOf(prefs.getInt(ThemePrefs.UI_WALLPAPER_BLUR, 20)) }
    var uiWallpaperDarken by remember { mutableStateOf(prefs.getInt(ThemePrefs.UI_WALLPAPER_DARKEN, 40)) }
    var customPrimary by remember { mutableStateOf(prefs.getInt(ThemePrefs.CUSTOM_PRIMARY, prefs.getInt("custom_theme_color", 0xFF1A6C59.toInt()))) }
    var customSecondary by remember { mutableStateOf(prefs.getInt(ThemePrefs.CUSTOM_SECONDARY, 0xFF8CD5BC.toInt())) }
    var customBackground by remember { mutableStateOf(prefs.getInt(ThemePrefs.CUSTOM_BACKGROUND, 0xFF121212.toInt())) }
    var customSurface by remember { mutableStateOf(prefs.getInt(ThemePrefs.CUSTOM_SURFACE, 0xFF161616.toInt())) }

    var showCornerRadiusDialog by remember { mutableStateOf(false) }
    var showColorPickerFor by remember { mutableStateOf<String?>(null) }
    var showThemeManager by remember { mutableStateOf(false) }

    val cornerRadiusOptions = listOf(
        "small" to ctx.getString(com.winlator.cmod.R.string.corner_radius_small),
        "medium" to ctx.getString(com.winlator.cmod.R.string.corner_radius_medium),
        "large" to ctx.getString(com.winlator.cmod.R.string.corner_radius_large),
    )
    fun cornerRadiusLabel(v: String) = cornerRadiusOptions.firstOrNull { it.first == v }?.second ?: ctx.getString(com.winlator.cmod.R.string.corner_radius_small)

    fun themeNameRes(id: String): Int = when (id) {
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
    val currentThemeName = stringResource(themeNameRes(themeId))

    val wallpaperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val input = ctx.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
                val target = File(ctx.filesDir, "ui_wallpaper.jpg")
                target.outputStream().use { out -> input.copyTo(out) }
                input.close()
                uiWallpaper = target.absolutePath
                saveString(ThemePrefs.UI_WALLPAPER, target.absolutePath)
                toast("Wallpaper applied")
            } catch (e: Exception) {
                toast("Wallpaper: ${e.message}")
            }
        }
    }

    fun removeWallpaper() {
        uiWallpaper = ""
        saveString(ThemePrefs.UI_WALLPAPER, "")
        try { File(ctx.filesDir, "ui_wallpaper.jpg").delete() } catch (_: Exception) {}
    }

    val exportThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val json = JSONObject()
                json.put("theme_id", themeId)
                json.put("theme_mode", ThemePrefs.resolveThemeMode(prefs))
                json.put("dynamic_color", prefs.getBoolean(ThemePrefs.DYNAMIC_COLOR, false))
                json.put("custom_theme_color", prefs.getInt("custom_theme_color", 0xFF1A6C59.toInt()))
                json.put("custom_theme_primary", customPrimary)
                json.put("custom_theme_secondary", customSecondary)
                json.put("custom_theme_background", customBackground)
                json.put("custom_theme_surface", customSurface)
                json.put("ui_scale", uiScale)
                json.put("font_scale", fontScale)
                json.put("corner_radius", cornerRadius)
                json.put("ui_wallpaper_blur", uiWallpaperBlur)
                json.put("ui_wallpaper_darken", uiWallpaperDarken)
                val output = ctx.contentResolver.openOutputStream(uri) ?: return@rememberLauncherForActivityResult
                output.write(json.toString(2).toByteArray())
                output.close()
                toast("Theme exported")
            } catch (e: Exception) {
                toast("Export: ${e.message}")
            }
        }
    }

    val importThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val input = ctx.contentResolver.openInputStream(uri) ?: return@rememberLauncherForActivityResult
                val json = JSONObject(input.readBytes().toString(Charsets.UTF_8))
                input.close()
                val editor = prefs.edit()
                json.optString("theme_id")?.let { editor.putString("theme_id", it) }
                json.optString("theme_mode")?.let { editor.putString(ThemePrefs.THEME_MODE, it) }
                editor.putBoolean(ThemePrefs.DYNAMIC_COLOR, json.optBoolean("dynamic_color", false))
                editor.putInt("custom_theme_color", json.optInt("custom_theme_color", 0xFF1A6C59.toInt()))
                editor.putInt(ThemePrefs.CUSTOM_PRIMARY, json.optInt("custom_theme_primary", customPrimary))
                editor.putInt(ThemePrefs.CUSTOM_SECONDARY, json.optInt("custom_theme_secondary", customSecondary))
                editor.putInt(ThemePrefs.CUSTOM_BACKGROUND, json.optInt("custom_theme_background", customBackground))
                editor.putInt(ThemePrefs.CUSTOM_SURFACE, json.optInt("custom_theme_surface", customSurface))
                editor.putFloat(ThemePrefs.UI_SCALE, json.optDouble("ui_scale", uiScale.toDouble()).toFloat())
                editor.putFloat(ThemePrefs.FONT_SCALE, json.optDouble("font_scale", fontScale.toDouble()).toFloat())
                editor.putString(ThemePrefs.CORNER_RADIUS, json.optString("corner_radius", cornerRadius))
                editor.putInt(ThemePrefs.UI_WALLPAPER_BLUR, json.optInt("ui_wallpaper_blur", uiWallpaperBlur))
                editor.putInt(ThemePrefs.UI_WALLPAPER_DARKEN, json.optInt("ui_wallpaper_darken", uiWallpaperDarken))
                editor.apply()
                (ctx as? android.app.Activity)?.recreate()
            } catch (e: Exception) {
                toast("Import: ${e.message}")
            }
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.winlator.cmod.R.string.customization)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Менеджер тем
            SectionHeader(stringResource(com.winlator.cmod.R.string.theme_manager), Icons.Filled.Palette)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.Palette,
                    title = stringResource(com.winlator.cmod.R.string.theme_manager),
                    subtitle = currentThemeName,
                    onClick = { showThemeManager = true }
                )
            }

            // Масштаб и шрифт
            SectionHeader(stringResource(com.winlator.cmod.R.string.interface_scale), Icons.Filled.ZoomIn)
            SettingsCard {
                SliderSettingRow(
                    title = stringResource(com.winlator.cmod.R.string.ui_scale),
                    value = uiScale,
                    valueRange = 0.85f..1.3f,
                    label = "${(uiScale * 100).toInt()}%",
                ) { uiScale = it; saveFloat(ThemePrefs.UI_SCALE, it) }
                SettingsDivider()
                SliderSettingRow(
                    title = stringResource(com.winlator.cmod.R.string.font_scale),
                    value = fontScale,
                    valueRange = 0.85f..1.3f,
                    label = "${(fontScale * 100).toInt()}%",
                ) { fontScale = it; saveFloat(ThemePrefs.FONT_SCALE, it) }
            }

            // Углы
            SectionHeader(stringResource(com.winlator.cmod.R.string.corner_radius), Icons.Filled.CropSquare)
            SettingsCard {
                SettingsClickRow(
                    icon = Icons.Filled.CropSquare,
                    title = stringResource(com.winlator.cmod.R.string.corner_radius),
                    subtitle = cornerRadiusLabel(cornerRadius),
                    onClick = { showCornerRadiusDialog = true }
                )
            }

            // Своя тема
            SectionHeader(stringResource(com.winlator.cmod.R.string.custom_color), Icons.Filled.Brush)
            SettingsCard {
                if (themeId != "custom") {
                    Text(
                        stringResource(com.winlator.cmod.R.string.custom_theme_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
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
                                    val isSelected = customPrimary == color.toArgb()
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
                                                customPrimary = color.toArgb()
                                                saveInt(ThemePrefs.CUSTOM_PRIMARY, color.toArgb())
                                                saveInt("custom_theme_color", color.toArgb())
                                            }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row2Colors.forEach { color ->
                                    val isSelected = customPrimary == color.toArgb()
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
                                                customPrimary = color.toArgb()
                                                saveInt(ThemePrefs.CUSTOM_PRIMARY, color.toArgb())
                                                saveInt("custom_theme_color", color.toArgb())
                                            }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))

                        CustomColorRow(
                            label = stringResource(com.winlator.cmod.R.string.custom_primary),
                            color = customPrimary,
                            onClick = { showColorPickerFor = ThemePrefs.CUSTOM_PRIMARY }
                        )
                        CustomColorRow(
                            label = stringResource(com.winlator.cmod.R.string.custom_secondary),
                            color = customSecondary,
                            onClick = { showColorPickerFor = ThemePrefs.CUSTOM_SECONDARY }
                        )
                        CustomColorRow(
                            label = stringResource(com.winlator.cmod.R.string.custom_background),
                            color = customBackground,
                            onClick = { showColorPickerFor = ThemePrefs.CUSTOM_BACKGROUND }
                        )
                        CustomColorRow(
                            label = stringResource(com.winlator.cmod.R.string.custom_surface),
                            color = customSurface,
                            onClick = { showColorPickerFor = ThemePrefs.CUSTOM_SURFACE }
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { showColorPickerFor = ThemePrefs.CUSTOM_PRIMARY },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Palette, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(com.winlator.cmod.R.string.custom_color_picker))
                        }
                    }
                }
            }

            // Обои интерфейса
            SectionHeader(stringResource(com.winlator.cmod.R.string.ui_wallpaper), Icons.Filled.Image)
            SettingsCard {
                SettingsButtonRow(
                    icon = Icons.Filled.AddPhotoAlternate,
                    title = stringResource(com.winlator.cmod.R.string.select_wallpaper),
                    onClick = { wallpaperLauncher.launch(arrayOf("image/*")) }
                )
                if (uiWallpaper.isNotEmpty()) {
                    SettingsDivider()
                    SettingsButtonRow(
                        icon = Icons.Filled.Delete,
                        title = stringResource(com.winlator.cmod.R.string.remove_wallpaper),
                        onClick = { removeWallpaper() }
                    )
                }
                SettingsDivider()
                SliderSettingRow(
                    title = stringResource(com.winlator.cmod.R.string.wallpaper_blur),
                    value = uiWallpaperBlur.toFloat(),
                    valueRange = 0f..100f,
                    label = "$uiWallpaperBlur%",
                ) { uiWallpaperBlur = it.toInt(); saveInt(ThemePrefs.UI_WALLPAPER_BLUR, it.toInt()) }
                SettingsDivider()
                SliderSettingRow(
                    title = stringResource(com.winlator.cmod.R.string.wallpaper_darken),
                    value = uiWallpaperDarken.toFloat(),
                    valueRange = 0f..100f,
                    label = "$uiWallpaperDarken%",
                ) { uiWallpaperDarken = it.toInt(); saveInt(ThemePrefs.UI_WALLPAPER_DARKEN, it.toInt()) }
            }

            // Экспорт/импорт темы
            SectionHeader(stringResource(com.winlator.cmod.R.string.theme_transfer), Icons.Filled.FileDownload)
            SettingsCard {
                SettingsButtonRow(
                    icon = Icons.Filled.FileUpload,
                    title = stringResource(com.winlator.cmod.R.string.export_theme),
                    onClick = { exportThemeLauncher.launch("winlator_theme.json") }
                )
                SettingsDivider()
                SettingsButtonRow(
                    icon = Icons.Filled.FileDownload,
                    title = stringResource(com.winlator.cmod.R.string.import_theme),
                    onClick = { importThemeLauncher.launch(arrayOf("application/json", "text/json", "*/*")) }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showCornerRadiusDialog) {
        ChoiceDialog(
            title = stringResource(com.winlator.cmod.R.string.corner_radius),
            options = cornerRadiusOptions,
            selected = cornerRadius,
            onDismiss = { showCornerRadiusDialog = false },
            onSelect = { value ->
                cornerRadius = value
                saveString(ThemePrefs.CORNER_RADIUS, value)
                showCornerRadiusDialog = false
            }
        )
    }

    // Вложенный экран «Менеджер тем»
    if (showThemeManager) {
        ThemeManagerScreen(
            preferences = prefs,
            onBack = { showThemeManager = false }
        )
    }

    showColorPickerFor?.let { slot ->
        val initial = when (slot) {
            ThemePrefs.CUSTOM_SECONDARY -> customSecondary
            ThemePrefs.CUSTOM_BACKGROUND -> customBackground
            ThemePrefs.CUSTOM_SURFACE -> customSurface
            else -> customPrimary
        }
        ColorPickerDialog(
            initialColor = initial,
            onColorSelected = { colorArgb ->
                when (slot) {
                    ThemePrefs.CUSTOM_SECONDARY -> { customSecondary = colorArgb; saveInt(ThemePrefs.CUSTOM_SECONDARY, colorArgb) }
                    ThemePrefs.CUSTOM_BACKGROUND -> { customBackground = colorArgb; saveInt(ThemePrefs.CUSTOM_BACKGROUND, colorArgb) }
                    ThemePrefs.CUSTOM_SURFACE -> { customSurface = colorArgb; saveInt(ThemePrefs.CUSTOM_SURFACE, colorArgb) }
                    else -> {
                        customPrimary = colorArgb
                        saveInt(ThemePrefs.CUSTOM_PRIMARY, colorArgb)
                        saveInt("custom_theme_color", colorArgb)
                    }
                }
            },
            onDismiss = { showColorPickerFor = null },
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
private fun SettingsClickRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
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
private fun SettingsButtonRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
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
private fun CustomColorRow(label: String, color: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(Color(color), RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.Edit, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SliderSettingRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    label: String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = 8,
        )
    }
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
