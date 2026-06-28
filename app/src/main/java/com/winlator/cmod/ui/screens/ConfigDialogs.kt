package com.winlator.cmod.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.winlator.cmod.R
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.widget.SeekBar

// =====================================================================
// Audio Driver Config Dialog (Compose)
// =====================================================================

/**
 * Compose-версия AudioDriverConfigDialog.
 * Поля: performanceMode, volume, latencyMillis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDriverConfigDialogCompose(
    initialConfig: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Парсим конфиг
    val initial = remember(initialConfig) { parseKeyValueSet(initialConfig.ifEmpty { "performanceMode=1,volume=1.0,latencyMillis=20" }) }
    var performanceMode by remember { mutableStateOf(initial["performanceMode"]?.toIntOrNull() ?: 1) }
    var volume by remember { mutableStateOf(((initial["volume"]?.toFloatOrNull() ?: 1.0f) * 100f).toInt()) }
    var latencyIndex by remember {
        mutableStateOf(findClosestLatencyIndex(initial["latencyMillis"]?.toIntOrNull() ?: 20))
    }

    val latencyValues = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Заголовок с иконкой
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.VolumeUp, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.audio_driver) + " " + stringResource(R.string.configuration),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()

                // Performance Mode
                Text(stringResource(R.string.performance_mode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val performanceModes = listOf(
                    stringResource(R.string.high_performance) to 0,
                    stringResource(R.string.low_latency) to 1,
                    stringResource(R.string.medium_latency) to 2,
                )
                val performanceLabels = performanceModes.map { it.first }
                ConfigSpinnerRow(
                    items = performanceLabels,
                    selectedIndex = performanceMode.coerceIn(0, performanceModes.lastIndex),
                    onSelected = { performanceMode = performanceModes[it].second },
                )

                // Volume
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.volume), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("$volume%", style = MaterialTheme.typography.bodyMedium)
                }
                ConfigSeekBar(
                    value = volume,
                    onValueChange = { volume = it },
                    valueRange = 0..100,
                )

                // Latency
                Text(stringResource(R.string.latency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = latencyValues.map { "$it мс" },
                    selectedIndex = latencyIndex,
                    onSelected = { latencyIndex = it },
                )

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val newConfig = "performanceMode=$performanceMode,volume=${volume / 100.0f},latencyMillis=${latencyValues[latencyIndex]}"
                        onConfirm(newConfig)
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Graphics Driver Config Dialog (Compose)
// =====================================================================

/**
 * Compose-версия GraphicsDriverConfigDialog.
 * Поля: version, blacklistedExtensions, maxDeviceMemory, adrenotoolsTurnip, frameSync,
 * presentMode, resourceType, bcnEmulation, bcnEmulationType, bcnEmulationCache, blit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphicsDriverConfigDialogCompose(
    context: Context,
    graphicsDriver: String,
    initialConfig: String,
    availableVersions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = remember(initialConfig) { parseKeyValueSet(initialConfig) }
    var selectedVersion by remember { mutableStateOf(initial["version"] ?: "System") }
    var maxDeviceMemory by remember { mutableStateOf(initial["maxDeviceMemory"] ?: "0") }
    var frameSync by remember { mutableStateOf(initial["frameSync"] ?: "Normal") }
    var presentMode by remember { mutableStateOf(initial["presentMode"] ?: "mailbox") }
    var resourceType by remember { mutableStateOf(initial["resourceType"] ?: "auto") }
    var bcnEmulation by remember { mutableStateOf(initial["bcnEmulation"] ?: "auto") }
    var bcnEmulationType by remember { mutableStateOf(initial["bcnEmulationType"] ?: "compute") }
    var bcnEmulationCache by remember { mutableStateOf(initial["bcnEmulationCache"] ?: "0") }
    var adrenotoolsTurnip by remember { mutableStateOf(initial["adrenotoolsTurnip"] != "0") }
    var enableBlit by remember { mutableStateOf(initial["blit"] == "1") }
    // Чёрный список расширений (для простоты показываем счётчик)
    val initialBlacklist = remember(initialConfig) { initial["blacklistedExtensions"] ?: "" }
    val blacklistedExtensions = remember { mutableStateOf(initialBlacklist) }

    val versions = if (availableVersions.isEmpty()) listOf("System", "Turnip", "WineD3D") else availableVersions
    val frameSyncOptions = listOf("Normal", "Always", "Never")
    val presentModeOptions = listOf("mailbox", "fifo", "immediate", "relaxed")
    val resourceTypeOptions = listOf("auto", "vk_memory", "dumb", "shared")
    val bcnEmulationOptions = listOf("auto", "disabled", "enable")
    val bcnEmulationTypeOptions = listOf("compute", "copy", "compute_copy")
    val bcnEmulationCacheOptions = listOf("0", "1")
    val maxMemoryOptions = (0..6).map { (it * 1024).toString() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.graphics_driver_configuration),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HorizontalDivider()

                // Version
                Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = versions,
                    selectedIndex = versions.indexOf(selectedVersion).coerceAtLeast(0),
                    onSelected = { selectedVersion = versions[it] },
                )

                // Download button (заглушка — открывает лог)
                Button(
                    onClick = {
                        AppUtils.showToast(context, context.getString(R.string.download_graphics_drivers))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.download_graphics_drivers))
                }

                // Available extensions (информационно)
                Text(stringResource(R.string.available_extensions), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                val extensionCount = remember { 128 }
                OutlinedTextField(
                    value = "$extensionCount System Extensions",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Max Device Memory
                Text(stringResource(R.string.max_device_memory), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = maxMemoryOptions,
                    selectedIndex = maxMemoryOptions.indexOf(maxDeviceMemory).coerceAtLeast(0),
                    onSelected = { maxDeviceMemory = maxMemoryOptions[it] },
                )

                // Frame Sync
                Text(stringResource(R.string.frame_synchronization), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = frameSyncOptions,
                    selectedIndex = frameSyncOptions.indexOf(frameSync).coerceAtLeast(0),
                    onSelected = { frameSync = frameSyncOptions[it] },
                )

                // Present Mode
                Text(stringResource(R.string.present_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = presentModeOptions,
                    selectedIndex = presentModeOptions.indexOf(presentMode).coerceAtLeast(0),
                    onSelected = { presentMode = presentModeOptions[it] },
                )

                // Resource Type
                Text(stringResource(R.string.resource_type), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = resourceTypeOptions,
                    selectedIndex = resourceTypeOptions.indexOf(resourceType).coerceAtLeast(0),
                    onSelected = { resourceType = resourceTypeOptions[it] },
                )

                // BCn Emulation
                Text("BCn Emulation", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = bcnEmulationOptions,
                    selectedIndex = bcnEmulationOptions.indexOf(bcnEmulation).coerceAtLeast(0),
                    onSelected = { bcnEmulation = bcnEmulationOptions[it] },
                )

                // BCn Emulation Type
                Text("BCn Emulation Type", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = bcnEmulationTypeOptions,
                    selectedIndex = bcnEmulationTypeOptions.indexOf(bcnEmulationType).coerceAtLeast(0),
                    onSelected = { bcnEmulationType = bcnEmulationTypeOptions[it] },
                )

                // BCn Emulation Cache
                Text("BCn Emulation Cache", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = bcnEmulationCacheOptions,
                    selectedIndex = bcnEmulationCacheOptions.indexOf(bcnEmulationCache).coerceAtLeast(0),
                    onSelected = { bcnEmulationCache = bcnEmulationCacheOptions[it] },
                )

                // Adrenotools Turnip
                ConfigSwitchRow(stringResource(R.string.adrenotools_turnip), adrenotoolsTurnip) { adrenotoolsTurnip = it }

                // Enable Blit
                ConfigSwitchRow(stringResource(R.string.enable_blit), enableBlit) { enableBlit = it }

                // Blacklisted extensions
                if (blacklistedExtensions.value.isNotEmpty()) {
                    Text("Blacklisted: ${blacklistedExtensions.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val newConfig = buildString {
                            append("version=$selectedVersion")
                            append(";blacklistedExtensions=${blacklistedExtensions.value}")
                            append(";maxDeviceMemory=$maxDeviceMemory")
                            append(";adrenotoolsTurnip=${if (adrenotoolsTurnip) "1" else "0"}")
                            append(";frameSync=$frameSync")
                            append(";presentMode=$presentMode")
                            append(";resourceType=$resourceType")
                            append(";bcnEmulation=$bcnEmulation")
                            append(";bcnEmulationType=$bcnEmulationType")
                            append(";bcnEmulationCache=$bcnEmulationCache")
                            append(";blit=${if (enableBlit) "1" else "0"}")
                        }
                        onConfirm(newConfig)
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

// =====================================================================
// DXVK Config Dialog (Compose)
// =====================================================================

/**
 * Compose-версия DXVKConfigDialog.
 * Поля: version, framerate, maxDeviceMemory, async, asyncCache, tearFree, maxFrameLatency,
 * tilerMode, deferSurfaceCreation, disableMsaa, maxTessFactor, syncInterval, maxFeatureLevel,
 * forceRefreshRate, shaderModel, floatEmulation, samplerAnisotropy, enableMemoryDefrag,
 * lowerSinCos, clampNegativeLodBias, samplerLodBias, numCompilerThreads,
 * enableGraphicsPipelineLibrary, relaxedBarriers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DXVKConfigDialogCompose(
    context: Context,
    initialConfig: String,
    availableVersions: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = remember(initialConfig) { parseKeyValueSet(initialConfig) }
    var version by remember { mutableStateOf(initial["version"] ?: "1.10.1") }
    var framerate by remember { mutableStateOf(initial["framerate"] ?: "0") }
    var maxDeviceMemory by remember { mutableStateOf(initial["maxDeviceMemory"] ?: "0") }
    var async by remember { mutableStateOf(initial["async"] == "1") }
    var asyncCache by remember { mutableStateOf(initial["asyncCache"] == "1") }
    var tearFree by remember { mutableStateOf(initial["tearFree"] ?: "Auto") }
    var maxFrameLatency by remember { mutableStateOf(initial["maxFrameLatency"] ?: "0") }
    var tilerMode by remember { mutableStateOf(initial["tilerMode"] ?: "Auto") }
    var deferSurfaceCreation by remember { mutableStateOf(initial["deferSurfaceCreation"] == "1") }
    var disableMsaa by remember { mutableStateOf(initial["disableMsaa"] == "1") }
    var maxTessFactor by remember { mutableStateOf(initial["maxTessFactor"] ?: "0") }
    var syncInterval by remember { mutableStateOf(initial["syncInterval"] ?: "-1") }
    var maxFeatureLevel by remember { mutableStateOf(initial["maxFeatureLevel"] ?: "12_1") }
    var forceRefreshRate by remember { mutableStateOf(initial["forceRefreshRate"] ?: "0") }
    var shaderModel by remember { mutableStateOf(initial["shaderModel"] ?: "3") }
    var floatEmulation by remember { mutableStateOf(initial["floatEmulation"] ?: "Auto") }
    var samplerAnisotropy by remember { mutableStateOf(initial["samplerAnisotropy"] ?: "-1") }
    var enableMemoryDefrag by remember { mutableStateOf(initial["enableMemoryDefrag"] ?: "Auto") }
    var lowerSinCos by remember { mutableStateOf(initial["lowerSinCos"] ?: "Auto") }
    var clampNegativeLodBias by remember { mutableStateOf(initial["clampNegativeLodBias"] ?: "Auto") }
    var samplerLodBias by remember { mutableStateOf(initial["samplerLodBias"] ?: "0.0") }
    var numCompilerThreads by remember { mutableStateOf(initial["numCompilerThreads"] ?: "0") }
    var enableGraphicsPipelineLibrary by remember { mutableStateOf(initial["enableGraphicsPipelineLibrary"] ?: "Auto") }
    var relaxedBarriers by remember { mutableStateOf(initial["relaxedBarriers"] ?: "Auto") }

    val versions = if (availableVersions.isEmpty()) listOf("1.10.1", "1.10.2", "1.10.3", "2.0", "2.1") else availableVersions
    val autoOnOff = listOf("Auto", "True", "False")
    val numOptions = (0..8).map { it.toString() }
    val syncIntervalOptions = listOf("-1", "0", "1", "2", "3", "4")
    val featureLevelOptions = listOf("12_1", "12_0", "11_1", "11_0", "10_0")
    val refreshRateOptions = listOf("0", "30", "60", "75", "90", "120", "144", "165")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.95f).padding(4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Text("DXVK " + stringResource(R.string.configuration), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()

                // Version
                Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                ConfigSpinnerRow(
                    items = versions,
                    selectedIndex = versions.indexOf(version).coerceAtLeast(0),
                    onSelected = { version = versions[it] },
                )
                Button(
                    onClick = {
                        AppUtils.showToast(context, context.getString(R.string.download_dxvk))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.download_dxvk)) }

                // Framerate
                DxvkRowWithHelp(
                    title = stringResource(R.string.frame_rate),
                    helpResId = 0,
                    spinnerItems = numOptions,
                    selectedIndex = numOptions.indexOf(framerate).coerceAtLeast(0),
                    onSelected = { framerate = numOptions[it] },
                )
                // Max Device Memory
                DxvkRowWithHelp(
                    title = stringResource(R.string.max_device_memory),
                    helpResId = 0,
                    spinnerItems = numOptions,
                    selectedIndex = numOptions.indexOf(maxDeviceMemory).coerceAtLeast(0),
                    onSelected = { maxDeviceMemory = numOptions[it] },
                )
                // Async
                ConfigSwitchRow("Async Pipeline", async) { async = it }
                ConfigSwitchRow("Async Cache", asyncCache) { asyncCache = it }
                // Tear Free
                DxvkRowWithHelp(
                    title = stringResource(R.string.tear_free),
                    helpResId = R.string.dxvk_tear_free_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(tearFree).coerceAtLeast(0),
                    onSelected = { tearFree = autoOnOff[it] },
                )
                // Max Frame Latency
                DxvkRowWithHelp(
                    title = "Max Frame Latency",
                    helpResId = R.string.dxvk_max_frame_latency_help,
                    spinnerItems = numOptions,
                    selectedIndex = numOptions.indexOf(maxFrameLatency).coerceAtLeast(0),
                    onSelected = { maxFrameLatency = numOptions[it] },
                )
                // Tiler Mode
                DxvkRowWithHelp(
                    title = "Tiler Mode",
                    helpResId = R.string.dxvk_tiler_mode_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(tilerMode).coerceAtLeast(0),
                    onSelected = { tilerMode = autoOnOff[it] },
                )
                // Max Tess Factor
                DxvkRowWithHelp(
                    title = "Max Tess Factor",
                    helpResId = R.string.dxvk_max_tess_factor_help,
                    spinnerItems = numOptions,
                    selectedIndex = numOptions.indexOf(maxTessFactor).coerceAtLeast(0),
                    onSelected = { maxTessFactor = numOptions[it] },
                )
                // Sync Interval
                DxvkRowWithHelp(
                    title = "Sync Interval",
                    helpResId = R.string.dxvk_sync_interval_help,
                    spinnerItems = syncIntervalOptions,
                    selectedIndex = syncIntervalOptions.indexOf(syncInterval).coerceAtLeast(0),
                    onSelected = { syncInterval = syncIntervalOptions[it] },
                )
                // Max Feature Level
                DxvkRowWithHelp(
                    title = "Max Feature Level",
                    helpResId = R.string.dxvk_max_feature_level_help,
                    spinnerItems = featureLevelOptions,
                    selectedIndex = featureLevelOptions.indexOf(maxFeatureLevel).coerceAtLeast(0),
                    onSelected = { maxFeatureLevel = featureLevelOptions[it] },
                )
                // Force Refresh Rate
                DxvkRowWithHelp(
                    title = "Force Refresh Rate",
                    helpResId = R.string.dxvk_force_refresh_rate_help,
                    spinnerItems = refreshRateOptions,
                    selectedIndex = refreshRateOptions.indexOf(forceRefreshRate).coerceAtLeast(0),
                    onSelected = { forceRefreshRate = refreshRateOptions[it] },
                )
                // Shader Model
                DxvkRowWithHelp(
                    title = "Shader Model",
                    helpResId = R.string.dxvk_shader_model_help,
                    spinnerItems = listOf("1", "2", "3"),
                    selectedIndex = listOf("1", "2", "3").indexOf(shaderModel).coerceAtLeast(0),
                    onSelected = { shaderModel = listOf("1", "2", "3")[it] },
                )
                // Float Emulation
                DxvkRowWithHelp(
                    title = "Float Emulation",
                    helpResId = R.string.dxvk_float_emulation_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(floatEmulation).coerceAtLeast(0),
                    onSelected = { floatEmulation = autoOnOff[it] },
                )
                // Sampler Anisotropy
                DxvkRowWithHelp(
                    title = "Sampler Anisotropy",
                    helpResId = R.string.dxvk_sampler_anisotropy_help,
                    spinnerItems = listOf("-1", "1", "2", "4", "8", "16"),
                    selectedIndex = listOf("-1", "1", "2", "4", "8", "16").indexOf(samplerAnisotropy).coerceAtLeast(0),
                    onSelected = { samplerAnisotropy = listOf("-1", "1", "2", "4", "8", "16")[it] },
                )
                // Enable Memory Defrag
                DxvkRowWithHelp(
                    title = "Enable Memory Defrag",
                    helpResId = R.string.dxvk_enable_memory_defrag_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(enableMemoryDefrag).coerceAtLeast(0),
                    onSelected = { enableMemoryDefrag = autoOnOff[it] },
                )
                // Lower Sin Cos
                DxvkRowWithHelp(
                    title = "Lower Sin Cos",
                    helpResId = R.string.dxvk_lower_sin_cos_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(lowerSinCos).coerceAtLeast(0),
                    onSelected = { lowerSinCos = autoOnOff[it] },
                )
                // Clamp Negative LOD Bias
                DxvkRowWithHelp(
                    title = "Clamp Negative LOD Bias",
                    helpResId = R.string.dxvk_clamp_negative_lod_bias_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(clampNegativeLodBias).coerceAtLeast(0),
                    onSelected = { clampNegativeLodBias = autoOnOff[it] },
                )
                // Sampler LOD Bias
                DxvkRowWithHelp(
                    title = "Sampler LOD Bias",
                    helpResId = R.string.dxvk_sampler_lod_bias_help,
                    spinnerItems = listOf("0.0", "0.5", "1.0", "1.5", "2.0", "-0.5", "-1.0"),
                    selectedIndex = listOf("0.0", "0.5", "1.0", "1.5", "2.0", "-0.5", "-1.0").indexOf(samplerLodBias).coerceAtLeast(0),
                    onSelected = { samplerLodBias = listOf("0.0", "0.5", "1.0", "1.5", "2.0", "-0.5", "-1.0")[it] },
                )
                // Num Compiler Threads
                DxvkRowWithHelp(
                    title = "Num Compiler Threads",
                    helpResId = R.string.dxvk_num_compiler_threads_help,
                    spinnerItems = numOptions,
                    selectedIndex = numOptions.indexOf(numCompilerThreads).coerceAtLeast(0),
                    onSelected = { numCompilerThreads = numOptions[it] },
                )
                // Enable Graphics Pipeline Library
                DxvkRowWithHelp(
                    title = "Enable Graphics Pipeline Library",
                    helpResId = R.string.dxvk_enable_graphics_pipeline_library_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(enableGraphicsPipelineLibrary).coerceAtLeast(0),
                    onSelected = { enableGraphicsPipelineLibrary = autoOnOff[it] },
                )
                // Relaxed Barriers
                DxvkRowWithHelp(
                    title = "Relaxed Barriers",
                    helpResId = R.string.dxvk_relaxed_barriers_help,
                    spinnerItems = autoOnOff,
                    selectedIndex = autoOnOff.indexOf(relaxedBarriers).coerceAtLeast(0),
                    onSelected = { relaxedBarriers = autoOnOff[it] },
                )
                // Switches
                ConfigSwitchRow("Defer Surface Creation", deferSurfaceCreation) { deferSurfaceCreation = it }
                ConfigSwitchRow("Disable MSAA", disableMsaa) { disableMsaa = it }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val newConfig = buildString {
                            append("version=$version")
                            append(",framerate=$framerate")
                            append(",maxDeviceMemory=$maxDeviceMemory")
                            append(",async=${if (async) "1" else "0"}")
                            append(",asyncCache=${if (asyncCache) "1" else "0"}")
                            append(",tearFree=$tearFree")
                            append(",maxFrameLatency=$maxFrameLatency")
                            append(",tilerMode=$tilerMode")
                            append(",deferSurfaceCreation=${if (deferSurfaceCreation) "1" else "0"}")
                            append(",disableMsaa=${if (disableMsaa) "1" else "0"}")
                            append(",maxTessFactor=$maxTessFactor")
                            append(",syncInterval=$syncInterval")
                            append(",maxFeatureLevel=$maxFeatureLevel")
                            append(",forceRefreshRate=$forceRefreshRate")
                            append(",shaderModel=$shaderModel")
                            append(",floatEmulation=$floatEmulation")
                            append(",samplerAnisotropy=$samplerAnisotropy")
                            append(",enableMemoryDefrag=$enableMemoryDefrag")
                            append(",lowerSinCos=$lowerSinCos")
                            append(",clampNegativeLodBias=$clampNegativeLodBias")
                            append(",samplerLodBias=$samplerLodBias")
                            append(",numCompilerThreads=$numCompilerThreads")
                            append(",enableGraphicsPipelineLibrary=$enableGraphicsPipelineLibrary")
                            append(",relaxedBarriers=$relaxedBarriers")
                        }
                        onConfirm(newConfig)
                    }) {
                        Text(stringResource(R.string.ok))
                    }
                }
            }
        }
    }
}

// =====================================================================
// Утилиты (helpers)
// =====================================================================

@Composable
fun ConfigSpinnerRow(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = items.getOrElse(selectedIndex) { items.firstOrNull() ?: "" }
    Box {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { idx, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelected(idx); expanded = false },
                )
            }
        }
    }
}

@Composable
fun ConfigSeekBar(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange = 0..100,
) {
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toInt()) },
        valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
    )
}

@Composable
fun ConfigSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DxvkRowWithHelp(
    title: String,
    helpResId: Int,
    spinnerItems: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (helpResId != 0) {
                IconButton(onClick = { AppUtils.showToast(ctx, helpResId) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.HelpOutline, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        ConfigSpinnerRow(items = spinnerItems, selectedIndex = selectedIndex, onSelected = onSelected)
    }
}

// Простой парсер key=value через запятую (как в KeyValueSet)
private fun parseKeyValueSet(s: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    if (s.isEmpty()) return result
    s.split(",", ";").forEach { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size == 2) result[parts[0].trim()] = parts[1].trim()
    }
    return result
}

private fun findClosestLatencyIndex(target: Int): Int {
    val values = listOf(10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)
    var bestIdx = 1 // 20 мс
    var minDiff = kotlin.math.abs(values[bestIdx] - target)
    for (i in values.indices) {
        val diff = kotlin.math.abs(values[i] - target)
        if (diff < minDiff) {
            minDiff = diff
            bestIdx = i
        }
    }
    return bestIdx
}
