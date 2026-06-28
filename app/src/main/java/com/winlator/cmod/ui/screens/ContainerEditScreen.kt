package com.winlator.cmod.ui.screens

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.box86_64.Box86_64Preset
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.contents.ContentProfile
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.core.WineRegistryEditor
import com.winlator.cmod.core.WineThemeManager
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.winhandler.WinHandler
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Полный перенос ContainerDetailFragment.java на Jetpack Compose.
 * Редактирование/создание контейнера со всеми настройками.
 * Config-диалоги вызываются через оригинальные Java-классы (нужен dummy View с tag).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerEditScreen(
    containerId: Int,
    isEditMode: Boolean,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val containerManager = remember { ContainerManager(ctx) }
    val contentsManager = remember { ContentsManager(ctx) }
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx) }

    val container = remember(isEditMode, containerId) {
        if (isEditMode) containerManager.getContainerById(containerId) else null
    }

    // ---- Состояния всех полей ----
    var name by remember { mutableStateOf(container?.name ?: "Container-${System.currentTimeMillis().toString().takeLast(4)}") }
    var screenSize by remember { mutableStateOf(container?.screenSize ?: Container.DEFAULT_SCREEN_SIZE) }
    var customScreenWidth by remember { mutableStateOf("") }
    var customScreenHeight by remember { mutableStateOf("") }
    var isCustomScreen by remember { mutableStateOf(screenSize == "Custom") }

    val wineVersions = remember { loadWineVersions(ctx, contentsManager) }
    var wineVersion by remember { mutableStateOf(container?.wineVersion ?: WineInfo.MAIN_WINE_VERSION.identifier()) }

    var graphicsDriver by remember { mutableStateOf(container?.graphicsDriver ?: Container.DEFAULT_GRAPHICS_DRIVER) }
    var graphicsDriverConfig by remember { mutableStateOf(container?.graphicsDriverConfig ?: Container.DEFAULT_GRAPHICSDRIVERCONFIG) }
    var dxwrapper by remember { mutableStateOf(container?.dxWrapper ?: Container.DEFAULT_DXWRAPPER) }
    var dxwrapperConfig by remember { mutableStateOf(container?.getDXWrapperConfig() ?: Container.DEFAULT_DXWRAPPERCONFIG) }
    var ddrawrapper by remember { mutableStateOf(container?.dDrawWrapper ?: Container.DEFAULT_DDRAWRAPPER) }
    var audioDriver by remember { mutableStateOf(container?.audioDriver ?: Container.DEFAULT_AUDIO_DRIVER) }
    var audioDriverConfig by remember { mutableStateOf(container?.audioDriverConfig ?: "performanceMode=1,volume=1.0,latencyMillis=20") }
    var emulator by remember { mutableStateOf(container?.emulator ?: Container.DEFAULT_EMULATOR) }
    var midiSoundFont by remember { mutableStateOf(container?.midiSoundFont ?: "") }
    var lcAll by remember { mutableStateOf(container?.getLC_ALL() ?: (Locale.getDefault().language + "_" + Locale.getDefault().country + ".UTF-8")) }
    var fullscreenStretched by remember { mutableStateOf(container?.isFullscreenStretched ?: false) }

    // Wine Configuration (registry keys)
    var csmt by remember { mutableStateOf(3) }
    var gpuNamePos by remember { mutableStateOf(0) }
    var offscreenRenderingMode by remember { mutableStateOf("fbo") }
    var strictShaderMath by remember { mutableStateOf(1) }
    var videoMemorySize by remember { mutableStateOf("2048") }
    var mouseWarpOverride by remember { mutableStateOf("disable") }
    var logPixels by remember { mutableStateOf(96) }
    var desktopTheme by remember { mutableStateOf(container?.desktopTheme ?: WineThemeManager.DEFAULT_DESKTOP_THEME) }

    // Win Components
    var winComponents by remember { mutableStateOf(container?.winComponents ?: Container.DEFAULT_WINCOMPONENTS) }

    // Environment Variables
    var envVars by remember { mutableStateOf(container?.envVars ?: Container.DEFAULT_ENV_VARS) }

    // Drives
    var drives by remember { mutableStateOf(container?.drives ?: Container.DEFAULT_DRIVES) }

    // Advanced
    var box64Version by remember { mutableStateOf(container?.box64Version ?: DefaultVersion.BOX64) }
    var box64Preset by remember { mutableStateOf(container?.box64Preset ?: sp.getString("box64_preset", Box86_64Preset.COMPATIBILITY) ?: Box86_64Preset.COMPATIBILITY) }
    var rcfileId by remember { mutableStateOf(container?.getRCFileId() ?: 0) }
    var fexcoreVersion by remember { mutableStateOf(container?.getFEXCoreVersion() ?: DefaultVersion.FEXCORE) }
    var fexcorePreset by remember { mutableStateOf(container?.getFEXCorePreset() ?: FEXCorePreset.INTERMEDIATE) }
    var startupSelection by remember { mutableStateOf((container?.startupSelection ?: Container.STARTUP_SELECTION_ESSENTIAL).toInt()) }
    var wow64Mode by remember { mutableStateOf(container?.isWoW64Mode ?: true) }

    // Game Controller
    val legacyMode = remember { sp.getBoolean("legacy_mode_enabled", false) }
    var enableXInput by remember {
        val it = (container?.inputType ?: WinHandler.DEFAULT_INPUT_TYPE).toInt()
        mutableStateOf(legacyMode || (it and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0)
    }
    var enableDInput by remember {
        val it = (container?.inputType ?: WinHandler.DEFAULT_INPUT_TYPE).toInt()
        mutableStateOf((it and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0)
    }
    var dinputMapperType by remember {
        val it = (container?.inputType ?: WinHandler.DEFAULT_INPUT_TYPE).toInt()
        mutableStateOf(if ((it and WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt()) != 0) 1 else 0)
    }
    var sdl2Toggle by remember { mutableStateOf(envVars.contains("SDL_XINPUT_ENABLED=1")) }

    // CPU lists
    var cpuList by remember { mutableStateOf(container?.cpuList ?: Container.getFallbackCPUList()) }
    var cpuListWoW64 by remember { mutableStateOf(container?.cpuListWoW64 ?: Container.getFallbackCPUListWoW64()) }

    // XR
    var primaryController by remember { mutableStateOf(container?.primaryController ?: 1) }
    var controllerMapping by remember {
        val mappings = Container.XrControllerMapping.values()
        if (container != null) {
            val bytes = ByteArray(mappings.size)
            mappings.forEachIndexed { i, m -> bytes[i] = container.getControllerMapping(m) }
            mutableStateOf(String(bytes))
        } else {
            mutableStateOf(String(ByteArray(10)))
        }
    }

    // Загрузка registry keys из user.reg (edit mode)
    LaunchedEffect(container) {
        if (isEditMode && container != null) {
            val userReg = File(container.rootDir, ".wine/user.reg")
            if (userReg.exists()) {
                WineRegistryEditor(userReg).use { reg ->
                    csmt = reg.getDwordValue("Software\\Wine\\Direct3D", "csmt", 3) ?: 3
                    val gpuId = reg.getDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", 1728) ?: 1728
                    gpuNamePos = findGpuNamePosition(ctx, gpuId)
                    offscreenRenderingMode = reg.getStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", "fbo") ?: "fbo"
                    strictShaderMath = reg.getDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", 1) ?: 1
                    videoMemorySize = reg.getStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", "2048") ?: "2048"
                    mouseWarpOverride = reg.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable") ?: "disable"
                    logPixels = reg.getDwordValue("Control Panel\\Desktop", "LogPixels", 96) ?: 96
                }
            }
        }
    }

    var currentTab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.wine_version) to 0,
        stringResource(R.string.win_components) to 1,
        stringResource(R.string.environment_variables) to 2,
        stringResource(R.string.drives) to 3,
        stringResource(R.string.advanced) to 4,
        stringResource(R.string.xr) to 5,
    )

    var showPreloader by remember { mutableStateOf(false) }
    var preloaderText by remember { mutableStateOf("") }

    // Состояния для Compose-диалогов конфигурации
    var showGraphicsConfigDialog by remember { mutableStateOf(false) }
    var showDxConfigDialog by remember { mutableStateOf(false) }
    var showAudioConfigDialog by remember { mutableStateOf(false) }

    // Списки доступных версий (из ресурсов, fallback — массивы по умолчанию)
    val graphicsDriverVersions = remember { ctx.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries).toList() }
    val dxvkVersions = remember { ctx.resources.getStringArray(R.array.dxvk_version_entries).toList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditMode) R.string.edit_container else R.string.new_container)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    saveContainer(
                        ctx = ctx, isEditMode = isEditMode, container = container,
                        containerManager = containerManager, contentsManager = contentsManager,
                        name = name,
                        screenSize = if (isCustomScreen) "${customScreenWidth}x${customScreenHeight}" else screenSize,
                        envVars = envVars, graphicsDriver = graphicsDriver, graphicsDriverConfig = graphicsDriverConfig,
                        dxwrapper = dxwrapper, ddrawrapper = ddrawrapper, dxwrapperConfig = dxwrapperConfig,
                        audioDriver = audioDriver, audioDriverConfig = audioDriverConfig, emulator = emulator,
                        wincomponents = winComponents, drives = drives, fullscreenStretched = fullscreenStretched,
                        cpuList = cpuList, cpuListWoW64 = cpuListWoW64, wow64Mode = wow64Mode,
                        startupSelection = startupSelection, box64Version = box64Version, box64Preset = box64Preset,
                        fexcoreVersion = fexcoreVersion, fexcorePreset = fexcorePreset, desktopTheme = desktopTheme,
                        rcfileId = rcfileId, midiSoundFont = midiSoundFont, lcAll = lcAll,
                        primaryController = primaryController, controllerMapping = controllerMapping,
                        wineVersion = wineVersion, enableXInput = enableXInput, enableDInput = enableDInput,
                        dinputMapperType = dinputMapperType, sdl2Toggle = sdl2Toggle, legacyMode = legacyMode,
                        csmt = csmt, gpuNamePos = gpuNamePos, offscreenRenderingMode = offscreenRenderingMode,
                        strictShaderMath = strictShaderMath, videoMemorySize = videoMemorySize,
                        mouseWarpOverride = mouseWarpOverride, logPixels = logPixels,
                        onPreloader = { text -> showPreloader = true; preloaderText = text },
                        onPreloaderClose = { showPreloader = false },
                        onDone = onBack,
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- Верхний блок (вне вкладок) ----
            SectionCard(title = stringResource(R.string.contents), icon = Icons.Filled.Settings) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                )
                SpinnerRow(
                    label = stringResource(R.string.screen_size),
                    entries = ctx.resources.getStringArray(R.array.screen_size_entries).toList(),
                    selected = screenSize,
                    onSelected = { screenSize = it; isCustomScreen = it == "Custom" },
                )
                if (isCustomScreen) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customScreenWidth, onValueChange = { customScreenWidth = it.filter { c -> c.isDigit() } },
                            label = { Text("W") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customScreenHeight, onValueChange = { customScreenHeight = it.filter { c -> c.isDigit() } },
                            label = { Text("H") }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                    }
                }
                SpinnerRow(
                    label = stringResource(R.string.wine_version),
                    entries = wineVersions, selected = wineVersion, enabled = !isEditMode,
                    onSelected = { wineVersion = it },
                )
                SpinnerRowWithConfig(
                    label = stringResource(R.string.graphics_driver),
                    entries = ctx.resources.getStringArray(R.array.graphics_driver_entries).toList(),
                    selected = graphicsDriver, onSelected = { graphicsDriver = it },
                    onConfigClick = { showGraphicsConfigDialog = true },
                )
                SpinnerRowWithConfig(
                    label = stringResource(R.string.dxwrapper),
                    entries = ctx.resources.getStringArray(R.array.dxwrapper_entries).toList(),
                    selected = dxwrapper, onSelected = { dxwrapper = it },
                    onConfigClick = { showDxConfigDialog = true },
                )
                SpinnerRow(
                    label = stringResource(R.string.ddraw_wrapper),
                    entries = ctx.resources.getStringArray(R.array.ddrawrapper_entries).toList(),
                    selected = ddrawrapper, onSelected = { ddrawrapper = it },
                )
                SpinnerRowWithConfig(
                    label = stringResource(R.string.audio_driver),
                    entries = ctx.resources.getStringArray(R.array.audio_driver_entries).toList(),
                    selected = audioDriver, onSelected = { audioDriver = it },
                    onConfigClick = { showAudioConfigDialog = true },
                )
                SpinnerRow(
                    label = stringResource(R.string.emulator),
                    entries = ctx.resources.getStringArray(R.array.emulator_entries).toList(),
                    selected = emulator, onSelected = { emulator = it },
                )
                val lcAllEntries = remember { ctx.resources.getStringArray(R.array.some_lc_all).toList() }
                val lcAllNames = remember { ctx.resources.getStringArray(R.array.some_lc_all_names).toList() }
                val lcAllDisplayName = remember(lcAll) {
                    val code = lcAll.replace(".UTF-8", "")
                    val idx = lcAllEntries.indexOf(code)
                    if (idx >= 0) lcAllNames[idx] else lcAll
                }
                SpinnerRow(
                    label = stringResource(R.string.locale),
                    entries = lcAllNames, selected = lcAllDisplayName,
                    onSelected = { name ->
                        val idx = lcAllNames.indexOf(name)
                        if (idx >= 0) lcAll = lcAllEntries[idx] + ".UTF-8"
                    },
                )
                SwitchRow(stringResource(R.string.fullscreen_stretched), fullscreenStretched) { fullscreenStretched = it }
            }

            // ---- Вкладки ----
            ScrollableTabRow(
                selectedTabIndex = currentTab,
                edgePadding = 0.dp,
            ) {
                tabs.forEachIndexed { index, (title, _) ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = { Text(title, maxLines = 1, softWrap = false) }
                    )
                }
            }

            when (currentTab) {
                0 -> WineConfigTab(
                    desktopTheme = desktopTheme, onDesktopThemeChange = { desktopTheme = it },
                    csmt = csmt, onCsmtChange = { csmt = it },
                    gpuNamePos = gpuNamePos, onGpuNamePosChange = { gpuNamePos = it },
                    offscreenRenderingMode = offscreenRenderingMode, onOffscreenRenderingModeChange = { offscreenRenderingMode = it },
                    strictShaderMath = strictShaderMath, onStrictShaderMathChange = { strictShaderMath = it },
                    videoMemorySize = videoMemorySize, onVideoMemorySizeChange = { videoMemorySize = it },
                    mouseWarpOverride = mouseWarpOverride, onMouseWarpOverrideChange = { mouseWarpOverride = it },
                    logPixels = logPixels, onLogPixelsChange = { logPixels = it },
                )
                1 -> WinComponentsTab(winComponents = winComponents, onWinComponentsChange = { winComponents = it })
                2 -> EnvVarsTab(envVars = envVars, onEnvVarsChange = { envVars = it })
                3 -> DrivesTab(drives = drives, onDrivesChange = { drives = it })
                4 -> AdvancedTab(
                    box64Version = box64Version, onBox64VersionChange = { box64Version = it },
                    box64Preset = box64Preset, onBox64PresetChange = { box64Preset = it },
                    rcfileId = rcfileId, onRcfileIdChange = { rcfileId = it },
                    fexcoreVersion = fexcoreVersion, onFexcoreVersionChange = { fexcoreVersion = it },
                    fexcorePreset = fexcorePreset, onFexcorePresetChange = { fexcorePreset = it },
                    startupSelection = startupSelection, onStartupSelectionChange = { startupSelection = it },
                    wow64Mode = wow64Mode, onWow64ModeChange = { wow64Mode = it },
                    cpuList = cpuList, onCpuListChange = { cpuList = it },
                    cpuListWoW64 = cpuListWoW64, onCpuListWoW64Change = { cpuListWoW64 = it },
                    legacyMode = legacyMode,
                    enableXInput = enableXInput, onEnableXInputChange = { enableXInput = it },
                    enableDInput = enableDInput, onEnableDInputChange = { enableDInput = it },
                    dinputMapperType = dinputMapperType, onDinputMapperTypeChange = { dinputMapperType = it },
                    sdl2Toggle = sdl2Toggle, onSdl2ToggleChange = { sdl2Toggle = it },
                )
                5 -> XRTab(
                    primaryController = primaryController, onPrimaryControllerChange = { primaryController = it },
                    controllerMapping = controllerMapping, onControllerMappingChange = { controllerMapping = it },
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showPreloader) {
        AlertDialog(
            onDismissRequest = {}, confirmButton = {},
            title = { Text(preloaderText) },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
        )
    }

    // ---- Compose-диалоги конфигурации ----
    if (showGraphicsConfigDialog) {
        GraphicsDriverConfigDialogCompose(
            context = ctx,
            graphicsDriver = graphicsDriver,
            initialConfig = graphicsDriverConfig,
            availableVersions = graphicsDriverVersions,
            onDismiss = { showGraphicsConfigDialog = false },
            onConfirm = { newConfig ->
                graphicsDriverConfig = newConfig
                showGraphicsConfigDialog = false
            },
        )
    }
    if (showDxConfigDialog) {
        if (dxwrapper == "dxvk") {
            DXVKConfigDialogCompose(
                context = ctx,
                initialConfig = dxwrapperConfig,
                availableVersions = dxvkVersions,
                onDismiss = { showDxConfigDialog = false },
                onConfirm = { newConfig ->
                    dxwrapperConfig = newConfig
                    showDxConfigDialog = false
                },
            )
        } else {
            // VKD3D: используем тот же DXVK-диалог (или DXVKConfigDialog.java для vkd3d)
            DXVKConfigDialogCompose(
                context = ctx,
                initialConfig = dxwrapperConfig,
                availableVersions = dxvkVersions,
                onDismiss = { showDxConfigDialog = false },
                onConfirm = { newConfig ->
                    dxwrapperConfig = newConfig
                    showDxConfigDialog = false
                },
            )
        }
    }
    if (showAudioConfigDialog) {
        AudioDriverConfigDialogCompose(
            initialConfig = audioDriverConfig,
            onDismiss = { showAudioConfigDialog = false },
            onConfirm = { newConfig ->
                audioDriverConfig = newConfig
                showAudioConfigDialog = false
            },
        )
    }
}

// ---- Вспомогательные функции для config-диалогов ----
// Диалоги конфигурации реализованы на Compose (см. ConfigDialogs.kt).
// Внутри Compose-кода достаточно вызвать GraphicsDriverConfigDialogCompose / DXVKConfigDialogCompose /
// AudioDriverConfigDialogCompose. Эти функции оставлены как заглушки-обёртки для совместимости
// сигнатур (используются из onConfigClick = { showGraphicsDriverConfigDialog(...) }), но
// реальный вызов перенесён в сам @Composable (см. блок overlay в конце ContainerEditScreen).
private fun showGraphicsDriverConfigDialog(
    ctx: Context, graphicsDriver: String, currentConfig: String, onResult: (String) -> Unit
) { /* вызов перенесён в ContainerEditScreen через state showGraphicsConfigDialog */ }

private fun showDxWrapperConfigDialog(
    ctx: Context, dxwrapper: String, currentConfig: String, onResult: (String) -> Unit
) { /* вызов перенесён в ContainerEditScreen через state showDxConfigDialog */ }

private fun showAudioDriverConfigDialog(
    ctx: Context, currentConfig: String, onResult: (String) -> Unit
) { /* вызов перенесён в ContainerEditScreen через state showAudioConfigDialog */ }

// ---- Загрузка данных ----

private fun loadWineVersions(ctx: Context, contentsManager: ContentsManager): List<String> {
    val list = mutableListOf<String>()
    // Все встроенные версии из массива (proton-9.0-x86_64, proton-9.0-arm64ec, ...)
    val versions = ctx.resources.getStringArray(R.array.wine_entries)
    list.addAll(versions)
    // Установленные профили Wine (только установленные, без дубликатов)
    contentsManager.syncContents()
    val profiles = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE) ?: emptyList()
    val deduped = linkedMapOf<String, String>()
    for (profile in profiles) {
        val installed = profile.remoteUrl == null || ContentsManager.getInstallDir(ctx, profile).exists()
        if (!installed) continue
        val entryName = ContentsManager.getEntryName(profile)
        deduped[entryName] = entryName
    }
    list.addAll(deduped.values)
    return list.distinct()
}

private fun loadGpuCards(ctx: Context): JSONArray {
    return try { JSONArray(com.winlator.cmod.core.FileUtils.readString(ctx, "gpu_cards.json")) }
    catch (_: Exception) { JSONArray() }
}

private fun findGpuNamePosition(ctx: Context, gpuId: Int): Int {
    val json = loadGpuCards(ctx)
    for (i in 0 until json.length()) {
        val obj = json.optJSONObject(i) ?: continue
        if (obj.optInt("deviceID", 0) == gpuId) return i
    }
    return 0
}

private fun getGpuId(ctx: Context, position: Int): Int {
    val json = loadGpuCards(ctx)
    if (position in 0 until json.length()) {
        return json.optJSONObject(position)?.optInt("deviceID", 1728) ?: 1728
    }
    return 1728
}

private fun getGpuVendorId(ctx: Context, position: Int): Int {
    val json = loadGpuCards(ctx)
    if (position in 0 until json.length()) {
        return json.optJSONObject(position)?.optInt("vendorID", 0x1002) ?: 0x1002
    }
    return 0x1002
}

private val SDL2_ENV_VARS = listOf(
    "SDL_JOYSTICK_WGI=0", "SDL_XINPUT_ENABLED=1", "SDL_JOYSTICK_RAWINPUT=0",
    "SDL_JOYSTICK_HIDAPI=1", "SDL_DIRECTINPUT_ENABLED=0", "SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS=1",
    "SDL_HINT_FORCE_RAISEWINDOW=0", "SDL_ALLOW_TOPMOST=0", "SDL_MOUSE_FOCUS_CLICKTHROUGH=1"
)

private fun saveContainer(
    ctx: Context, isEditMode: Boolean, container: Container?,
    containerManager: ContainerManager, contentsManager: ContentsManager,
    name: String, screenSize: String, envVars: String,
    graphicsDriver: String, graphicsDriverConfig: String,
    dxwrapper: String, ddrawrapper: String, dxwrapperConfig: String,
    audioDriver: String, audioDriverConfig: String, emulator: String,
    wincomponents: String, drives: String, fullscreenStretched: Boolean,
    cpuList: String, cpuListWoW64: String, wow64Mode: Boolean,
    startupSelection: Int, box64Version: String, box64Preset: String,
    fexcoreVersion: String, fexcorePreset: String, desktopTheme: String,
    rcfileId: Int, midiSoundFont: String, lcAll: String,
    primaryController: Int, controllerMapping: String, wineVersion: String,
    enableXInput: Boolean, enableDInput: Boolean, dinputMapperType: Int,
    sdl2Toggle: Boolean, legacyMode: Boolean,
    csmt: Int, gpuNamePos: Int, offscreenRenderingMode: String,
    strictShaderMath: Int, videoMemorySize: String, mouseWarpOverride: String, logPixels: Int,
    onPreloader: (String) -> Unit, onPreloaderClose: () -> Unit, onDone: () -> Unit,
) {
    // Обработка SDL2 env vars
    var finalEnvVars = envVars
    if (sdl2Toggle) {
        for (ev in SDL2_ENV_VARS) {
            if (!finalEnvVars.contains(ev)) finalEnvVars += (if (finalEnvVars.isEmpty()) "" else " ") + ev
        }
    } else {
        for (ev in SDL2_ENV_VARS) {
            finalEnvVars = finalEnvVars.replace(ev, "").replace("\\s{2,}".toRegex(), " ").trim()
        }
    }

    // Input type
    var finalInputType = 0
    if (legacyMode) {
        finalInputType = WinHandler.DEFAULT_INPUT_TYPE.toInt()
    } else {
        if (enableXInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
        if (enableDInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
        finalInputType = finalInputType or (if (dinputMapperType == 0) WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt() else WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt())
    }

    if (isEditMode && container != null) {
        container.name = name
        container.screenSize = screenSize
        container.envVars = finalEnvVars
        container.cpuList = cpuList
        container.cpuListWoW64 = cpuListWoW64
        container.graphicsDriver = graphicsDriver
        container.graphicsDriverConfig = graphicsDriverConfig
        container.dxWrapper = dxwrapper
        container.dDrawWrapper = ddrawrapper
        container.dxWrapperConfig = dxwrapperConfig
        container.audioDriver = audioDriver
        container.audioDriverConfig = audioDriverConfig
        container.emulator = emulator
        container.winComponents = wincomponents
        container.drives = drives
        container.isFullscreenStretched = fullscreenStretched
        container.inputType = finalInputType
        container.isWoW64Mode = wow64Mode
        container.startupSelection = startupSelection.toByte()
        container.box64Version = box64Version
        container.box64Preset = box64Preset
        container.setFEXCoreVersion(fexcoreVersion)
        container.setFEXCorePreset(fexcorePreset)
        container.desktopTheme = desktopTheme
        container.setRcfileId(rcfileId)
        container.setMidiSoundFont(midiSoundFont)
        container.setLC_ALL(lcAll)
        container.primaryController = primaryController
        container.setControllerMapping(controllerMapping)
        container.saveData()

        saveWineRegistryKeys(ctx, container, csmt, gpuNamePos, offscreenRenderingMode, strictShaderMath, videoMemorySize, mouseWarpOverride, logPixels)
        onDone()
    } else {
        onPreloader(ctx.getString(R.string.creating_container))
        val data = JSONObject()
        try {
            data.put("name", name)
            data.put("screenSize", screenSize)
            data.put("envVars", finalEnvVars)
            data.put("cpuList", cpuList)
            data.put("cpuListWoW64", cpuListWoW64)
            data.put("graphicsDriver", graphicsDriver)
            data.put("graphicsDriverConfig", graphicsDriverConfig)
            data.put("dxwrapper", dxwrapper)
            data.put("ddrawrapper", ddrawrapper)
            data.put("dxwrapperConfig", dxwrapperConfig)
            data.put("audioDriver", audioDriver)
            data.put("audioDriverConfig", audioDriverConfig)
            data.put("emulator", emulator)
            data.put("wincomponents", wincomponents)
            data.put("drives", drives)
            data.put("fullscreenStretched", fullscreenStretched)
            data.put("inputType", finalInputType)
            data.put("wow64Mode", wow64Mode)
            data.put("startupSelection", startupSelection)
            data.put("box64Version", box64Version)
            data.put("box64Preset", box64Preset)
            data.put("fexcoreVersion", fexcoreVersion)
            data.put("fexcorePreset", fexcorePreset)
            data.put("desktopTheme", desktopTheme)
            data.put("rcfileId", rcfileId)
            data.put("midiSoundFont", midiSoundFont)
            data.put("lc_all", lcAll)
            data.put("primaryController", primaryController)
            data.put("controllerMapping", controllerMapping)
            data.put("wineVersion", wineVersion)
            data.put("csmtEnabled", csmt)
            data.put("gpuDeviceId", getGpuId(ctx, gpuNamePos))
            data.put("offscreenRenderingMode", offscreenRenderingMode)
            data.put("strictShaderMath", strictShaderMath)
            data.put("videoMemorySize", videoMemorySize)
            data.put("mouseWarpOverride", mouseWarpOverride)
        } catch (e: Exception) { android.widget.Toast.makeText(ctx, e.message, android.widget.Toast.LENGTH_LONG).show() }

        containerManager.createContainerAsync(data, contentsManager) { createdContainer ->
            if (createdContainer != null) {
                saveWineRegistryKeys(ctx, createdContainer, csmt, gpuNamePos, offscreenRenderingMode, strictShaderMath, videoMemorySize, mouseWarpOverride, logPixels)
            }
            (ctx as? Activity)?.runOnUiThread {
                onPreloaderClose()
                onDone()
            }
        }
    }
}

private fun saveWineRegistryKeys(
    ctx: Context, container: Container,
    csmt: Int, gpuNamePos: Int, offscreenRenderingMode: String,
    strictShaderMath: Int, videoMemorySize: String, mouseWarpOverride: String, logPixels: Int,
) {
    val userReg = File(container.rootDir, ".wine/user.reg")
    if (!userReg.exists()) return
    val gpuId = getGpuId(ctx, gpuNamePos)
    val gpuVendorId = getGpuVendorId(ctx, gpuNamePos)
    WineRegistryEditor(userReg).use { reg ->
        reg.setDwordValue("Software\\Wine\\Direct3D", "csmt", if (csmt == 0) 0 else 3)
        reg.setDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", gpuId)
        reg.setDwordValue("Software\\Wine\\Direct3D", "VideoPciVendorID", gpuVendorId)
        reg.setStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", offscreenRenderingMode)
        reg.setDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", strictShaderMath)
        reg.setStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", videoMemorySize)
        reg.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl")
        reg.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled")
        reg.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", mouseWarpOverride)
        reg.setDwordValue("Control Panel\\Desktop", "LogPixels", logPixels)
    }
}
