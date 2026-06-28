package com.winlator.cmod.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.container.Container
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.StringUtils
import com.winlator.cmod.core.WineInfo
import com.winlator.cmod.inputcontrols.ControlsProfile
import com.winlator.cmod.inputcontrols.InputControlsManager
import com.winlator.cmod.contents.ContentsManager
import com.winlator.cmod.core.DefaultVersion
import com.winlator.cmod.box86_64.Box86_64Preset
import com.winlator.cmod.box86_64.Box86_64PresetManager
import com.winlator.cmod.box86_64.Box86_64EditPresetDialog
import com.winlator.cmod.fexcore.FEXCorePreset
import com.winlator.cmod.fexcore.FEXCorePresetManager
import com.winlator.cmod.fexcore.FEXCoreEditPresetDialog
import com.winlator.cmod.fexcore.FEXCoreManager
import com.winlator.cmod.winhandler.WinHandler
import com.winlator.cmod.box86_64.rc.RCManager
import com.winlator.cmod.box86_64.rc.RCFile
import com.winlator.cmod.midi.MidiManager
import com.winlator.cmod.contentdialog.ShortcutSettingsDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutSettingsScreen(
    shortcut: Shortcut,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val manager = remember { ContainerManager(ctx) }
    val contentsManager = remember { ContentsManager(ctx) }
    val inputControlsManager = remember { InputControlsManager(ctx) }
    val sp = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx) }
    val container = shortcut.container
    val isLegacyModeEnabled = remember { sp.getBoolean("legacy_mode_enabled", false) }

    // Determine if Wine is arm64EC
    val wineVersion = remember { container.wineVersion }
    val wineInfo = remember { WineInfo.fromIdentifier(ctx, contentsManager, wineVersion) }
    val isArm64EC = remember { wineInfo.isArm64EC() }

    // States
    var name by remember { mutableStateOf(shortcut.name) }
    var customIcon by remember { mutableStateOf<Bitmap?>(null) }
    var execArgs by remember { mutableStateOf(shortcut.getExtra("execArgs")) }
    val screenSizeEntries = remember { ctx.resources.getStringArray(R.array.screen_size_entries).toList() }
    val initialScreenSize = remember { shortcut.getExtra("screenSize", container.screenSize) }
    var screenSize by remember {
        mutableStateOf(if (screenSizeEntries.contains(initialScreenSize)) initialScreenSize else "Custom")
    }
    var customScreenWidth by remember {
        mutableStateOf(if (!screenSizeEntries.contains(initialScreenSize) && initialScreenSize.contains("x")) {
            initialScreenSize.split("x").getOrElse(0) { "" }
        } else "")
    }
    var customScreenHeight by remember {
        mutableStateOf(if (!screenSizeEntries.contains(initialScreenSize) && initialScreenSize.contains("x")) {
            initialScreenSize.split("x").getOrElse(1) { "" }
        } else "")
    }
    var isCustomScreen by remember { mutableStateOf(screenSize == "Custom") }
    var graphicsDriver by remember { mutableStateOf(shortcut.getExtra("graphicsDriver", container.graphicsDriver)) }
    var graphicsDriverConfig by remember { mutableStateOf(shortcut.getExtra("graphicsDriverConfig", container.graphicsDriverConfig)) }
    var dxwrapper by remember { mutableStateOf(shortcut.getExtra("dxwrapper", container.dxWrapper)) }
    var dxwrapperConfig by remember { mutableStateOf(shortcut.getExtra("dxwrapperConfig", container.getDXWrapperConfig())) }
    var ddrawrapper by remember { mutableStateOf(shortcut.getExtra("ddrawrapper", container.dDrawWrapper)) }
    var audioDriver by remember { mutableStateOf(shortcut.getExtra("audioDriver", container.audioDriver)) }
    var audioDriverConfig by remember { mutableStateOf(shortcut.getExtra("audioDriverConfig", container.audioDriverConfig)) }
    var emulator by remember {
        val raw = shortcut.getExtra("emulator", container.emulator) ?: ""
        mutableStateOf(if (raw.lowercase() == "box64") "Box64" else "FEXCore")
    }
    var emulator64 by remember {
        val raw = shortcut.getExtra("emulator64", container.emulator) ?: ""
        mutableStateOf(if (raw.lowercase() == "box64") "Box64" else "FEXCore")
    }
    var midiSoundFont by remember { mutableStateOf(shortcut.getExtra("midiSoundFont", container.midiSoundFont)) }
    var forceFullscreen by remember { mutableStateOf(shortcut.getExtra("forceFullscreen", "0") == "1") }

    // Secondary exec
    var useSecondaryExec by remember {
        mutableStateOf(shortcut.getExtra("secondaryExec", "").isNotEmpty())
    }
    var secondaryExec by remember { mutableStateOf(shortcut.getExtra("secondaryExec")) }
    var execDelay by remember { mutableStateOf(shortcut.getExtra("execDelay", "0")) }

    var fullscreenStretched by remember { mutableStateOf(shortcut.getExtra("fullscreenStretched", "0") == "1") }
    var startupSelection by remember { mutableStateOf(shortcut.getExtra("startupSelection", container.startupSelection.toString())) }
    var controlsProfileId by remember { mutableStateOf(shortcut.getExtra("controlsProfile", "0")) }
    var box64Version by remember { mutableStateOf(shortcut.getExtra("box64Version", container.box64Version)) }
    var box64Preset by remember { mutableStateOf(shortcut.getExtra("box64Preset", container.box64Preset)) }
    var fexcoreVersion by remember { mutableStateOf(shortcut.getExtra("fexcoreVersion", container.getFEXCoreVersion())) }
    val initialFexPreset = container.getFEXCorePreset()
    var fexcorePreset by remember { mutableStateOf(shortcut.getExtra("fexcorePreset", initialFexPreset)) }
    var rcfileId by remember { mutableStateOf(shortcut.getExtra("rcfileId", container.getRCFileId().toString())) }

    // inputType as bitmask (matching old Java code)
    val initialInputType = shortcut.getExtra("inputType", container.inputType.toString()).toIntOrNull() ?: WinHandler.DEFAULT_INPUT_TYPE.toInt()
    var enableXInput by remember { mutableStateOf((initialInputType and WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()) != 0) }
    var enableDInput by remember { mutableStateOf((initialInputType and WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()) != 0) }
    var dinputMapperStandard by remember { mutableStateOf((initialInputType and WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt()) != 0) }

    var disableXinput by remember { mutableStateOf(shortcut.getExtra("disableXinput", "0") == "1") }
    var touchscreenMode by remember { mutableStateOf(shortcut.getExtra("simTouchScreen", "0") == "1") }

    // Sharpness — defaults 100 like old code
    var sharpnessEffect by remember { mutableStateOf(shortcut.getExtra("sharpnessEffect", "None")) }
    var sharpnessLevel by remember { mutableStateOf(shortcut.getExtra("sharpnessLevel", "100")) }
    var sharpnessDenoise by remember { mutableStateOf(shortcut.getExtra("sharpnessDenoise", "100")) }

    // CPU lists as comma-separated strings like old code
    var cpuList by remember { mutableStateOf(shortcut.getExtra("cpuList", container.getCPUList(true))) }
    var cpuListWoW64 by remember { mutableStateOf(shortcut.getExtra("cpuListWoW64", container.getCPUListWoW64(true))) }
    var winComponents by remember { mutableStateOf(shortcut.getExtra("wincomponents", container.winComponents)) }
    var envVars by remember { mutableStateOf(shortcut.getExtra("envVars", container.envVars)) }

    // Dialogs
    var showGraphicsConfig by remember { mutableStateOf(false) }
    var showDxConfig by remember { mutableStateOf(false) }
    var showAudioConfig by remember { mutableStateOf(false) }

    val dialogHelper = remember { ShortcutSettingsDialog(ctx, shortcut) }
    var versionRefreshTrigger by remember { mutableStateOf(0) }

    val box64Versions = remember(isArm64EC, versionRefreshTrigger) {
        val list = mutableListOf<String>()
        val resId = if (isArm64EC) R.array.wowbox64_version_entries else R.array.box64_version_entries
        list.addAll(ctx.resources.getStringArray(resId))

        val contentType = if (isArm64EC) {
            com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
        } else {
            com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_BOX64
        }

        contentsManager.getProfiles(contentType)?.forEach { profile ->
            if (profile.remoteUrl == null || com.winlator.cmod.contents.ContentsManager.getInstallDir(ctx, profile).exists()) {
                val entryName = com.winlator.cmod.contents.ContentsManager.getEntryName(profile)
                val firstDashIndex = entryName.indexOf('-')
                if (firstDashIndex >= 0) {
                    list.add(entryName.substring(firstDashIndex + 1))
                }
            }
        }
        list
    }

    val fexcoreVersions = remember(versionRefreshTrigger) {
        val list = mutableListOf<String>()
        list.addAll(ctx.resources.getStringArray(R.array.fexcore_version_entries))
        contentsManager.getProfiles(com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_FEXCORE)?.forEach { profile ->
            if (profile.remoteUrl == null || com.winlator.cmod.contents.ContentsManager.getInstallDir(ctx, profile).exists()) {
                val entryName = com.winlator.cmod.contents.ContentsManager.getEntryName(profile)
                val firstDashIndex = entryName.indexOf('-')
                if (firstDashIndex >= 0) {
                    list.add(entryName.substring(firstDashIndex + 1))
                }
            }
        }
        list
    }

    val graphicsDriverVersions = remember { ctx.resources.getStringArray(R.array.wrapper_graphics_driver_version_entries).toList() }
    val dxvkVersions = remember { ctx.resources.getStringArray(R.array.dxvk_version_entries).toList() }

    // Icon picker
    val iconPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    customIcon = bmp
                    shortcut.saveCustomIcon(bmp)
                }
            } catch (_: Exception) {}
        }
    }

    // Preset import launchers
    var presetsRefreshTrigger by remember { mutableStateOf(0) }

    val box64ImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                    Box86_64PresetManager.importPreset("box64", ctx, inputStream)
                    presetsRefreshTrigger++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val fexcoreImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                ctx.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FEXCorePresetManager.importPreset(ctx, inputStream)
                    presetsRefreshTrigger++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Emulator enable/disable based on arm64EC
    LaunchedEffect(isArm64EC) {
        if (!isArm64EC) {
            emulator = "box64"
            emulator64 = "box64"
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    // Rename if needed (inline, matching old ShortcutSettingsDialog.renameShortcut via reflection)
                    val newName = name.trim()
                    if (newName.isNotEmpty() && newName != shortcut.name) {
                        val parent = shortcut.file.parentFile
                        val oldDesktopFile = shortcut.file
                        val oldName = shortcut.name
                        val newDesktopFile = File(parent, "$newName.desktop")

                        // Handle Windows case-insensitivity by using a temporary file name if only case changed
                        val renamed = if (oldDesktopFile.canonicalPath.equals(newDesktopFile.canonicalPath, ignoreCase = true)) {
                            val tempFile = File(parent, "${newName}_temp.desktop")
                            oldDesktopFile.renameTo(tempFile) && tempFile.renameTo(newDesktopFile)
                        } else {
                            !newDesktopFile.isFile && oldDesktopFile.renameTo(newDesktopFile)
                        }

                        if (renamed) {
                            // Use reflection to update final fields (same as old Java code)
                            try {
                                val fileField = Shortcut::class.java.getDeclaredField("file")
                                fileField.isAccessible = true
                                fileField.set(shortcut, newDesktopFile)
                                val nameField = Shortcut::class.java.getDeclaredField("name")
                                nameField.isAccessible = true
                                nameField.set(shortcut, newName)
                            } catch (_: Exception) {}
                            // Delete old file if it still exists and path actually changed
                            if (oldDesktopFile.exists() && oldDesktopFile.absolutePath != newDesktopFile.absolutePath) {
                                oldDesktopFile.delete()
                            }
                        }

                        // Rename link file if exists, using correct oldName variable
                        val linkFile = File(parent, "$oldName.lnk")
                        if (linkFile.isFile) {
                            val newLinkFile = File(parent, "$newName.lnk")
                            if (oldName.equals(newName, ignoreCase = true)) {
                                val tempLink = File(parent, "${newName}_temp.lnk")
                                linkFile.renameTo(tempLink) && tempLink.renameTo(newLinkFile)
                            } else if (!newLinkFile.isFile) {
                                linkFile.renameTo(newLinkFile)
                            }
                        }
                    }

                    // Build inputType bitmask (matching old Java save logic)
                    var finalInputType = 0
                    if (enableXInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_XINPUT.toInt()
                    if (enableDInput) finalInputType = finalInputType or WinHandler.FLAG_INPUT_TYPE_DINPUT.toInt()
                    finalInputType = finalInputType or (if (dinputMapperStandard) WinHandler.FLAG_DINPUT_MAPPER_STANDARD.toInt() else WinHandler.FLAG_DINPUT_MAPPER_XINPUT.toInt())

                    shortcut.putExtra("inputType", finalInputType.toString())

                    shortcut.putExtra("execArgs", execArgs.ifEmpty { null })
                    shortcut.putExtra("screenSize", if (isCustomScreen) "${customScreenWidth}x${customScreenHeight}" else if (screenSize != container.screenSize) screenSize else null)
                    shortcut.putExtra("graphicsDriver", if (graphicsDriver != container.graphicsDriver) graphicsDriver else null)
                    shortcut.putExtra("graphicsDriverConfig", if (graphicsDriverConfig != container.graphicsDriverConfig) graphicsDriverConfig else null)
                    shortcut.putExtra("dxwrapper", if (dxwrapper != container.dxWrapper) dxwrapper else null)
                    shortcut.putExtra("dxwrapperConfig", if (dxwrapperConfig != container.getDXWrapperConfig()) dxwrapperConfig else null)
                    shortcut.putExtra("ddrawrapper", if (ddrawrapper != container.dDrawWrapper) ddrawrapper else null)
                    shortcut.putExtra("audioDriver", if (audioDriver != container.audioDriver) audioDriver else null)
                    val emuSave = emulator.lowercase()
                    shortcut.putExtra("emulator", if (emuSave != container.emulator.lowercase()) emuSave else null)
                    val emu64Save = emulator64.lowercase()
                    shortcut.putExtra("emulator64", if (emu64Save != container.emulator.lowercase()) emu64Save else null)
                    shortcut.putExtra("midiSoundFont", if (midiSoundFont != container.midiSoundFont) midiSoundFont else null)
                    shortcut.putExtra("forceFullscreen", if (forceFullscreen) "1" else null)

                    // Secondary exec
                    if (useSecondaryExec) {
                        val secExec = secondaryExec.trim()
                        val delay = execDelay.trim()
                        shortcut.putExtra("secondaryExec", secExec.ifEmpty { null })
                        shortcut.putExtra("execDelay", delay.ifEmpty { null })
                    } else {
                        shortcut.putExtra("secondaryExec", null)
                        shortcut.putExtra("execDelay", null)
                    }

                    shortcut.putExtra("fullscreenStretched", if (fullscreenStretched) "1" else null)
                    shortcut.putExtra("disableXinput", if (disableXinput) "1" else null)
                    shortcut.putExtra("simTouchScreen", if (touchscreenMode) "1" else "0")

                    shortcut.putExtra("box64Version", if (box64Version != container.box64Version) box64Version else null)
                    val box64PresetSave = box64Preset
                    shortcut.putExtra("box64Preset", if (box64PresetSave != container.box64Preset) box64PresetSave else null)

                    shortcut.putExtra("rcfileId", if (rcfileId.toIntOrNull() != container.getRCFileId()) rcfileId else null)

                    shortcut.putExtra("fexcoreVersion", if (fexcoreVersion != container.getFEXCoreVersion()) fexcoreVersion else null)
                    shortcut.putExtra("fexcorePreset", if (fexcorePreset != container.getFEXCorePreset()) fexcorePreset else null)

                    val startupIdx = startupSelection.toIntOrNull() ?: 0
                    shortcut.putExtra("startupSelection", if (startupIdx.toByte() != container.startupSelection) startupSelection else null)

                    val profileList = inputControlsManager.getProfiles(true) ?: arrayListOf<ControlsProfile>()
                    val controlsProfileInt = controlsProfileId.toIntOrNull() ?: 0
                    shortcut.putExtra("controlsProfile", if (controlsProfileInt > 0) controlsProfileId else null)

                    shortcut.putExtra("sharpnessEffect", sharpnessEffect)
                    shortcut.putExtra("sharpnessLevel", sharpnessLevel)
                    shortcut.putExtra("sharpnessDenoise", sharpnessDenoise)

                    shortcut.putExtra("cpuList", if (cpuList != container.getCPUList(true)) cpuList else null)
                    shortcut.putExtra("cpuListWoW64", if (cpuListWoW64 != container.getCPUListWoW64(true)) cpuListWoW64 else null)
                    shortcut.putExtra("wincomponents", if (winComponents != container.winComponents) winComponents else null)
                    shortcut.putExtra("envVars", envVars.ifEmpty { null })

                    shortcut.saveData()
                    AppUtils.showToast(ctx, R.string.saved)
                    onBack()
                },
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.save), tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Name + Custom Icon ----
            SectionTitle(stringResource(R.string.name))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SectionTitle(stringResource(R.string.custom_game_icon))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val displayIcon = customIcon ?: shortcut.displayIcon
                    if (displayIcon != null) {
                        Image(bitmap = displayIcon.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
                    } else {
                        Icon(Icons.Filled.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                }
                OutlinedButton(onClick = { iconPickerLauncher.launch("image/*") }) { Text(stringResource(R.string.select_icon)) }
                if (customIcon != null || shortcut.getExtra("customIconPath").isNotEmpty()) {
                    OutlinedButton(onClick = {
                        customIcon = null
                        shortcut.removeCustomIcon()
                    }) { Text(stringResource(R.string.remove_icon)) }
                }
            }

            HorizontalDivider()

            // ---- Screen Size ----
            SectionTitle(stringResource(R.string.screen_size))
            val screenSizeEntries = ctx.resources.getStringArray(R.array.screen_size_entries).toList()
            SettingsSpinner(entries = screenSizeEntries, selected = screenSize, onSelected = {
                screenSize = it; isCustomScreen = it == "Custom"
            })
            if (isCustomScreen) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = customScreenWidth, onValueChange = { customScreenWidth = it.filter { c -> c.isDigit() } }, label = { Text("W") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = customScreenHeight, onValueChange = { customScreenHeight = it.filter { c -> c.isDigit() } }, label = { Text("H") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }

            // ---- Graphics Driver ----
            SectionTitle(stringResource(R.string.graphics_driver))
            SettingsSpinnerWithConfig(
                entries = ctx.resources.getStringArray(R.array.graphics_driver_entries).toList(),
                selected = graphicsDriver,
                onSelected = { graphicsDriver = it },
                onConfig = { showGraphicsConfig = true },
            )
            val driverVersion = remember(graphicsDriverConfig) {
                com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog.getVersion(graphicsDriverConfig)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Current Version: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(driverVersion, color = Color(0xFF0087FF), style = MaterialTheme.typography.bodyMedium)
            }

            // ---- DX Wrapper (with help button) ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.dxwrapper))
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { AppUtils.showHelpBox(ctx, null, R.string.dxwrapper_help_content) }, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
            SettingsSpinnerWithConfig(
                entries = ctx.resources.getStringArray(R.array.dxwrapper_entries).toList(),
                selected = dxwrapper,
                onSelected = { dxwrapper = it },
                onConfig = { showDxConfig = true },
            )

            // ---- DDraw Wrapper ----
            SectionTitle(stringResource(R.string.ddraw_wrapper))
            val ddrawEntries = ctx.resources.getStringArray(R.array.ddrawrapper_entries).toList()
            SettingsSpinner(entries = ddrawEntries, selected = ddrawrapper, onSelected = { ddrawrapper = it })

            // ---- Audio Driver ----
            SectionTitle(stringResource(R.string.audio_driver))
            SettingsSpinnerWithConfig(
                entries = ctx.resources.getStringArray(R.array.audio_driver_entries).toList(),
                selected = audioDriver,
                onSelected = { audioDriver = it },
                onConfig = { showAudioConfig = true },
            )

            // ---- 64bit Emulator (only if arm64EC) ----
            if (isArm64EC) {
                SectionTitle("64bit Emulator")
                val emulatorEntries = ctx.resources.getStringArray(R.array.emulator_entries).toList()
                SettingsSpinner(entries = emulatorEntries, selected = emulator64, onSelected = { emulator64 = it })
            }

            // ---- DLL Emulator ----
            SectionTitle(stringResource(R.string.dll_emulator))
            val emulatorEntries = ctx.resources.getStringArray(R.array.emulator_entries).toList()
            SettingsSpinner(
                entries = emulatorEntries,
                selected = emulator,
                onSelected = { emulator = it },
                enabled = isArm64EC,
            )

            // ---- MIDI SoundFont ----
            SectionTitle(stringResource(R.string.midi_sound_font))
            val midiFiles = remember { MidiManager.getSF2Files(ctx) }
            val midiEntries = remember(midiFiles) {
                val list = mutableListOf("None", MidiManager.DEFAULT_SF2_FILE)
                midiFiles?.forEach { list.add(it.name) }
                list
            }
            SettingsSpinner(entries = midiEntries, selected = if (midiSoundFont.isEmpty()) "None" else midiSoundFont, onSelected = {
                midiSoundFont = if (it == "None") "" else it
            })

            HorizontalDivider()

            // ---- Secondary Executable ----
            SectionTitle(stringResource(R.string.secondary_exec))
            SettingsSwitch(stringResource(R.string.use_secondary_executable), useSecondaryExec) { useSecondaryExec = it }
            if (useSecondaryExec) {
                OutlinedTextField(
                    value = secondaryExec,
                    onValueChange = { secondaryExec = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.enter_secondary_exec_path)) }
                )
                OutlinedTextField(
                    value = execDelay,
                    onValueChange = { execDelay = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.enter_delay_seconds)) }
                )
            }

            HorizontalDivider()

            // ---- Tab Layout ----
            var currentTab by remember { mutableStateOf(0) }
            val tabTitles = listOf(
                stringResource(R.string.win_components),
                stringResource(R.string.environment_variables),
                stringResource(R.string.advanced)
            )

            TabRow(selectedTabIndex = currentTab, modifier = Modifier.fillMaxWidth()) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            when (currentTab) {
                0 -> {
                    WinComponentsTab(winComponents = winComponents, onWinComponentsChange = { winComponents = it })
                }
                1 -> {
                    EnvVarsTab(envVars = envVars, onEnvVarsChange = { envVars = it })
                }
                2 -> {
                    // ---- Advanced Tab Content ----
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ======== Box86/Box64 FieldSet ========
                        FieldSetCard(label = stringResource(R.string.box86_box64)) {
                            // ---- Box64 Version + Download ----
                            SectionTitle("Box64 ${stringResource(R.string.version)}")
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) {
                                    SettingsSpinner(entries = box64Versions, selected = box64Version, onSelected = { box64Version = it })
                                }
                                Spacer(Modifier.width(8.dp))
                                FilledIconButton(
                                    onClick = {
                                        val contentType = if (isArm64EC) {
                                            com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                                        } else {
                                            com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_BOX64
                                        }
                                        dialogHelper.showVersionDownloadDialog(contentType, "Box64") { version ->
                                            box64Version = version
                                            versionRefreshTrigger++
                                        }
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.download))
                                }
                            }

                            // ---- Box64 Preset ----
                            SectionTitle("Box64 Preset")
                            val box64Presets = remember(presetsRefreshTrigger) { Box86_64PresetManager.getPresets("box64", ctx) }
                            val box64PresetNames = remember(box64Presets) { box64Presets.map { it.name } }
                            val selectedBox64PresetName = box64Presets.firstOrNull { it.id == box64Preset }?.name ?: box64Preset
                            SettingsSpinner(
                                entries = box64PresetNames,
                                selected = selectedBox64PresetName,
                                onSelected = { name ->
                                    val preset = box64Presets.firstOrNull { p -> p.name == name }
                                    if (preset != null) box64Preset = preset.id
                                }
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = {
                                    val dialog = Box86_64EditPresetDialog(ctx, "box64", null)
                                    dialog.setOnConfirmCallback { presetsRefreshTrigger++ }
                                    dialog.show()
                                }) { Icon(Icons.Filled.Add, "Add Preset") }

                                IconButton(onClick = {
                                    val dialog = Box86_64EditPresetDialog(ctx, "box64", box64Preset)
                                    dialog.setOnConfirmCallback { presetsRefreshTrigger++ }
                                    dialog.show()
                                }) { Icon(Icons.Filled.Edit, "Edit Preset") }

                                IconButton(onClick = {
                                    Box86_64PresetManager.duplicatePreset("box64", ctx, box64Preset)
                                    presetsRefreshTrigger++
                                }) { Icon(Icons.Filled.ContentCopy, "Duplicate Preset") }

                                if (box64Preset.startsWith("custom-")) {
                                    IconButton(onClick = {
                                        Box86_64PresetManager.removePreset("box64", ctx, box64Preset)
                                        presetsRefreshTrigger++
                                    }) { Icon(Icons.Filled.Delete, "Remove Preset") }
                                }

                                IconButton(onClick = {
                                    Box86_64PresetManager.exportPreset("box64", ctx, box64Preset)
                                }) { Icon(Icons.Filled.IosShare, "Export Preset") }

                                IconButton(onClick = {
                                    box64ImportLauncher.launch("*/*")
                                }) { Icon(Icons.Filled.FileOpen, "Import Preset") }
                            }

                            // ---- RC File ----
                            SectionTitle(stringResource(R.string.box86_64_rc_files))
                            val rcManager = remember { RCManager(ctx) }
                            val rcFiles: List<RCFile> = remember { rcManager.rcFiles }
                            val rcFileNames = remember(rcFiles) { rcFiles.map { it.name } }
                            val rcfileIdInt = rcfileId.toIntOrNull() ?: 0
                            val selectedRcFileName = rcFiles.getOrNull(rcfileIdInt)?.name ?: rcFiles.firstOrNull()?.name ?: ""
                            SettingsSpinner(
                                entries = rcFileNames,
                                selected = selectedRcFileName,
                                onSelected = { name ->
                                    val idx = rcFiles.indexOfFirst { r -> r.name == name }
                                    if (idx >= 0) rcfileId = idx.toString()
                                }
                            )
                        }

                        // ======== FEXCore FieldSet (only if arm64EC) ========
                        if (isArm64EC) {
                            FieldSetCard(label = stringResource(R.string.fexcore_config)) {
                                // ---- FEXCore Version + Download ----
                                SectionTitle("FEXCore ${stringResource(R.string.version)}")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.weight(1f)) {
                                        SettingsSpinner(entries = fexcoreVersions, selected = fexcoreVersion, onSelected = { fexcoreVersion = it })
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    FilledIconButton(
                                        onClick = {
                                            val contentType = com.winlator.cmod.contents.ContentProfile.ContentType.CONTENT_TYPE_FEXCORE
                                            dialogHelper.showVersionDownloadDialog(contentType, "FEXCore") { version ->
                                                fexcoreVersion = version
                                                versionRefreshTrigger++
                                            }
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.download))
                                    }
                                }

                                // ---- FEXCore Preset ----
                                SectionTitle("FEXCore ${stringResource(R.string.preset)}")
                                val fexPresets = remember(presetsRefreshTrigger) { FEXCorePresetManager.getPresets(ctx) }
                                val selectedPresetName = fexPresets.find { it.id == fexcorePreset }?.name ?: fexcorePreset
                                SettingsSpinner(
                                    entries = fexPresets.map { it.name },
                                    selected = selectedPresetName,
                                    onSelected = { name -> fexcorePreset = fexPresets.find { it.name == name }?.id ?: name }
                                )
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    IconButton(onClick = {
                                        val dialog = FEXCoreEditPresetDialog(ctx, null)
                                        dialog.setOnConfirmCallback { presetsRefreshTrigger++ }
                                        dialog.show()
                                    }) { Icon(Icons.Filled.Add, "Add Preset") }

                                    IconButton(onClick = {
                                        val dialog = FEXCoreEditPresetDialog(ctx, fexcorePreset)
                                        dialog.setOnConfirmCallback { presetsRefreshTrigger++ }
                                        dialog.show()
                                    }) { Icon(Icons.Filled.Edit, "Edit Preset") }

                                    IconButton(onClick = {
                                        FEXCorePresetManager.duplicatePreset(ctx, fexcorePreset)
                                        presetsRefreshTrigger++
                                    }) { Icon(Icons.Filled.ContentCopy, "Duplicate Preset") }

                                    if (fexcorePreset.startsWith("custom-")) {
                                        IconButton(onClick = {
                                            FEXCorePresetManager.removePreset(ctx, fexcorePreset)
                                            presetsRefreshTrigger++
                                        }) { Icon(Icons.Filled.Delete, "Remove Preset") }
                                    }

                                    IconButton(onClick = {
                                        FEXCorePresetManager.exportPreset(ctx, fexcorePreset)
                                    }) { Icon(Icons.Filled.IosShare, "Export Preset") }

                                    IconButton(onClick = {
                                        fexcoreImportLauncher.launch("*/*")
                                    }) { Icon(Icons.Filled.FileOpen, "Import Preset") }
                                }
                            }
                        }

                        // ======== System FieldSet ========
                        FieldSetCard(label = stringResource(R.string.system)) {
                            SectionTitle(stringResource(R.string.startup_selection))
                            val startupSelectionEntries = ctx.resources.getStringArray(R.array.startup_selection_entries).toList()
                            val startupSelectionInt = startupSelection.toIntOrNull() ?: 0
                            val selectedStartupSelection = startupSelectionEntries.getOrElse(startupSelectionInt) { startupSelectionEntries.first() }
                            SettingsSpinner(
                                entries = startupSelectionEntries,
                                selected = selectedStartupSelection,
                                onSelected = { name ->
                                    val idx = startupSelectionEntries.indexOf(name)
                                    if (idx >= 0) startupSelection = idx.toString()
                                },
                            )
                        }

                        // ======== Input Controls FieldSet ========
                        FieldSetCard(label = stringResource(R.string.input_controls)) {
                            // ---- Controls Profile ----
                            SectionTitle(stringResource(R.string.profile))
                            val profileList = remember { inputControlsManager.getProfiles(true) ?: arrayListOf() }
                            val selectedProfile = profileList.find { it.id.toString() == controlsProfileId }?.name ?: stringResource(R.string.none)
                            SettingsSpinner(
                                entries = listOf(stringResource(R.string.none)) + profileList.map { it.name },
                                selected = selectedProfile,
                                onSelected = { name ->
                                    val profile = profileList.find { it.name == name }
                                    controlsProfileId = profile?.id?.toString() ?: "0"
                                },
                            )

                            SettingsSwitch(stringResource(R.string.disable_xinput_for_shortcut), disableXinput) { disableXinput = it }
                            SettingsSwitch(stringResource(R.string.simulate_touch_screen), touchscreenMode) { touchscreenMode = it }
                        }

                        // ======== Game Controller FieldSet ========
                        FieldSetCard(label = stringResource(R.string.game_controller)) {
                            if (isLegacyModeEnabled) {
                                Text(
                                    "You are in 7.1.2 legacy input mode. Advanced input settings are not available.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                // DInput Mapper Type (shown first like old layout)
                                if (enableDInput) {
                                    SectionTitle(stringResource(R.string.directinput_mapper_type))
                                    val dinputEntries = ctx.resources.getStringArray(R.array.dinput_mapper_type_entries).toList()
                                    SettingsSpinner(
                                        entries = dinputEntries,
                                        selected = if (dinputMapperStandard) dinputEntries.getOrElse(0) { "" } else dinputEntries.getOrElse(1) { "" },
                                        onSelected = { selected -> dinputMapperStandard = (dinputEntries.indexOf(selected) == 0) },
                                    )
                                }

                                // XInput checkbox + help
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SettingsSwitch(stringResource(R.string.enable_xinput_for_wine_game), enableXInput, modifier = Modifier.weight(1f)) {
                                        enableXInput = it
                                        if (it && enableDInput) {
                                            Toast.makeText(ctx, R.string.enable_xinput_and_dinput_same_time, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    IconButton(onClick = { AppUtils.showHelpBox(ctx, null, R.string.help_xinput) }, modifier = Modifier.size(22.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }

                                // DInput checkbox + help
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SettingsSwitch(stringResource(R.string.enable_dinput_for_wine_game), enableDInput, modifier = Modifier.weight(1f)) {
                                        enableDInput = it
                                        if (it && enableXInput) {
                                            Toast.makeText(ctx, R.string.enable_xinput_and_dinput_same_time, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    IconButton(onClick = { AppUtils.showHelpBox(ctx, null, R.string.help_dinput) }, modifier = Modifier.size(22.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // ======== System (Exec Args + Fullscreen + Force Fullscreen) FieldSet ========
                        FieldSetCard(label = stringResource(R.string.system)) {
                            // ---- Exec Args with correct popup items ----
                            SectionTitle(stringResource(R.string.exec_arguments))
                            val extraArgsItems = listOf(
                                "-force-gfx-direct",
                                "-force-d3d11-singlethreaded",
                                "-force-dx9",
                                "-force-d3d9",
                                "-force-d3d11",
                                "--force-gfx-direct",
                                "--force-d3d11-singlethreaded",
                                "--force-dx9",
                                "--force-d3d9",
                                "--force-d3d11",
                                "/d3d9"
                            )
                            var showArgsMenu by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = execArgs,
                                    onValueChange = { execArgs = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = { showArgsMenu = true }) {
                                            Icon(Icons.Filled.Menu, "Arguments helper")
                                        }
                                    }
                                )
                                DropdownMenu(expanded = showArgsMenu, onDismissRequest = { showArgsMenu = false }) {
                                    extraArgsItems.forEach { arg ->
                                        DropdownMenuItem(
                                            text = { Text(arg) },
                                            onClick = {
                                                if (!execArgs.contains(arg)) {
                                                    execArgs = if (execArgs.trim().isEmpty()) arg else "${execArgs.trim()} $arg"
                                                }
                                                showArgsMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            SettingsSwitch(stringResource(R.string.fullscreen_stretched), fullscreenStretched) { fullscreenStretched = it }
                            SettingsSwitch(stringResource(R.string.force_fullscreen), forceFullscreen) { forceFullscreen = it }
                        }

                        // ======== VkBaSalt FieldSet ========
                        FieldSetCard(label = "VkBaSalt") {
                            SectionTitle(stringResource(R.string.vkbasalt_sharpness_effects))
                            val sharpnessEntries = ctx.resources.getStringArray(R.array.vkbasalt_sharpness_entries).toList()
                            SettingsSpinner(
                                entries = sharpnessEntries,
                                selected = sharpnessEffect,
                                onSelected = { sharpnessEffect = it }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.vkbasalt_sharpness_level), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text("${sharpnessLevel}%")
                            }
                            Slider(
                                value = sharpnessLevel.toFloatOrNull() ?: 100f,
                                valueRange = 0f..100f,
                                onValueChange = { sharpnessLevel = it.toInt().toString() }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.vkbasalt_sharpness_denoise), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text("${sharpnessDenoise}%")
                            }
                            Slider(
                                value = sharpnessDenoise.toFloatOrNull() ?: 100f,
                                valueRange = 0f..100f,
                                onValueChange = { sharpnessDenoise = it.toInt().toString() }
                            )
                        }

                        // ======== Processor Affinity (CPU checkboxes) ========
                        SectionTitle(stringResource(R.string.processor_affinity))
                        CPUCheckboxList(cpuList = cpuList, onCpuListChange = { cpuList = it })
                        SectionTitle(stringResource(R.string.processor_affinity_32_bit_apps))
                        CPUCheckboxList(cpuList = cpuListWoW64, onCpuListChange = { cpuListWoW64 = it })
                    }
                }
            }

            Spacer(Modifier.height(72.dp))
        }
    }

    // Config dialogs
    if (showGraphicsConfig) {
        GraphicsDriverConfigDialogCompose(
            context = ctx, graphicsDriver = graphicsDriver,
            initialConfig = graphicsDriverConfig, availableVersions = graphicsDriverVersions,
            onDismiss = { showGraphicsConfig = false },
            onConfirm = { graphicsDriverConfig = it; showGraphicsConfig = false },
        )
    }
    if (showDxConfig) {
        DXVKConfigDialogCompose(
            context = ctx, initialConfig = dxwrapperConfig, availableVersions = dxvkVersions,
            onDismiss = { showDxConfig = false },
            onConfirm = { dxwrapperConfig = it; showDxConfig = false },
        )
    }
    if (showAudioConfig) {
        AudioDriverConfigDialogCompose(
            initialConfig = audioDriverConfig,
            onDismiss = { showAudioConfig = false },
            onConfirm = { audioDriverConfig = it; showAudioConfig = false },
        )
    }
}

// ---- CPU Checkbox List (matches old CPUListView) ----
@Composable
private fun CPUCheckboxList(cpuList: String, onCpuListChange: (String) -> Unit) {
    val numProcessors = remember { Runtime.getRuntime().availableProcessors() }
    val checkedList = remember(cpuList) { cpuList.split(",").map { it.trim() }.filter { it.isNotEmpty() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until numProcessors) {
            val isChecked = checkedList.isEmpty() || checkedList.contains(i.toString())
            Box(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            val currentCheckedSet = if (checkedList.isEmpty()) {
                                (0 until numProcessors).map { it.toString() }.toSet()
                            } else {
                                checkedList.toSet()
                            }
                            val nextCheckedSet = if (checked) {
                                currentCheckedSet + i.toString()
                            } else {
                                currentCheckedSet - i.toString()
                            }
                            onCpuListChange(nextCheckedSet.map { it.toInt() }.sorted().joinToString(","))
                        }
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("CPU $i", style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            }
        }
    }
}

// ---- FieldSet Card (matches old FieldSet style) ----
@Composable
private fun FieldSetCard(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            fontWeight = FontWeight.Bold
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

// ---- Helper composables ----
@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsSpinner(entries: List<String>, selected: String, onSelected: (String) -> Unit, enabled: Boolean = true) {
    var expanded by remember { mutableStateOf(false) }
    val display = if (selected.isEmpty()) entries.firstOrNull() ?: "" else selected
    Box {
        OutlinedTextField(value = display, onValueChange = {}, readOnly = true, enabled = enabled, modifier = Modifier.fillMaxWidth(), trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) })
        if (enabled) {
            Box(Modifier.matchParentSize().clickable { expanded = true })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { entry ->
                DropdownMenuItem(text = { Text(entry) }, onClick = { onSelected(entry); expanded = false })
            }
        }
    }
}

@Composable
private fun SettingsSpinnerWithConfig(entries: List<String>, selected: String, onSelected: (String) -> Unit, onConfig: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            var expanded by remember { mutableStateOf(false) }
            val display = if (selected.isEmpty()) entries.firstOrNull() ?: "" else selected
            Box {
                OutlinedTextField(value = display, onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth(), trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) })
                Box(Modifier.matchParentSize().clickable { expanded = true })
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    entries.forEach { entry -> DropdownMenuItem(text = { Text(entry) }, onClick = { onSelected(entry); expanded = false }) }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = onConfig, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.configuration))
        }
    }
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, modifier: Modifier = Modifier, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
