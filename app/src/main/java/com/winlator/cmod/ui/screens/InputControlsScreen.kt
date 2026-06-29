package com.winlator.cmod.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.widget.SeekBar
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.preference.PreferenceManager
import com.winlator.cmod.ControlsEditorActivity
import com.winlator.cmod.GamePadTestActivity
import com.winlator.cmod.IconManagerActivity
import com.winlator.cmod.R
import com.winlator.cmod.contentdialog.ContentDialog
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.core.HttpUtils
import com.winlator.cmod.inputcontrols.ControlsProfile
import com.winlator.cmod.inputcontrols.InputControlsManager
import com.winlator.cmod.math.Mathf
import com.winlator.cmod.widget.InputControlsView
import com.winlator.cmod.widget.ProfilePreviewView
import org.json.JSONObject

private const val INPUT_CONTROLS_URL = "https://raw.githubusercontent.com/brunodev85/winlator/main/input_controls/%s"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputControlsScreen(
    selectedProfileId: Int = 0,
    onOpenGamepadTest: () -> Unit = {},
    onOpenIconManager: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val manager = remember { InputControlsManager(ctx) }
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(ctx) }
    val density = LocalDensity.current

    var profiles by remember { mutableStateOf(manager.profiles ?: emptyList()) }
    var currentProfile by remember {
        mutableStateOf<ControlsProfile?>(
            if (selectedProfileId > 0) manager.getProfile(selectedProfileId) else null
        )
    }
    var cursorSpeedProgress by remember {
        mutableStateOf(((currentProfile?.cursorSpeed ?: 1.0f) * 100).toInt())
    }
    var overlayOpacityProgress by remember {
        mutableStateOf((prefs.getFloat("overlay_opacity", InputControlsView.DEFAULT_OVERLAY_OPACITY) * 100).toInt())
    }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadProfilesList by remember { mutableStateOf<List<String>?>(null) }
    var selectedDownloadIndices by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var controllersKey by remember { mutableStateOf(0) }
    var profileExpanded by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val jsonStr = FileUtils.readString(ctx, uri)
                val imported = manager.importProfile(JSONObject(jsonStr))
                currentProfile = imported
                profiles = manager.profiles ?: emptyList()
                cursorSpeedProgress = (imported.cursorSpeed * 100).toInt()
                AppUtils.showToast(ctx, "Profile imported")
            } catch (_: Exception) {
                AppUtils.showToast(ctx, R.string.unable_to_import_profile)
            }
        }
    }

    fun reloadProfiles() {
        controllersKey++
        profiles = manager.profiles ?: emptyList()
    }

    fun onProfileChanged(p: ControlsProfile?) {
        currentProfile = p
        cursorSpeedProgress = ((p?.cursorSpeed ?: 1.0f) * 100).toInt()
    }

    val controllers = remember(currentProfile?.id, controllersKey) {
        currentProfile?.loadControllers() ?: arrayListOf()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---- Profile ----
        Text(stringResource(R.string.profile), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = currentProfile?.name ?: stringResource(R.string.no_profile_selected),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                )
                // Invisible click area on top of the field
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(end = 48.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { profileExpanded = true },
                )
                DropdownMenu(
                    expanded = profileExpanded,
                    onDismissRequest = { profileExpanded = false },
                ) {
                    profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            onClick = {
                                onProfileChanged(profile)
                                profileExpanded = false
                            },
                        )
                    }
                    if (profiles.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("(No profiles)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = { profileExpanded = false },
                        )
                    }
                }
            }
            IconButton(onClick = {
                ContentDialog.prompt(ctx, R.string.profile_name, null) { name ->
                    val p = manager.createProfile(name)
                    onProfileChanged(p); reloadProfiles()
                }
            }) { Icon(Icons.Filled.Add, stringResource(R.string.add)) }
            IconButton(onClick = {
                val p = currentProfile ?: return@IconButton
                ContentDialog.prompt(ctx, R.string.profile_name, p.name) { name ->
                    p.setName(name); p.save(); reloadProfiles()
                }
            }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
            IconButton(onClick = {
                val p = currentProfile ?: return@IconButton
                ContentDialog.confirm(ctx, R.string.do_you_want_to_duplicate_this_profile) {
                    val dup = manager.duplicateProfile(p)
                    onProfileChanged(dup); reloadProfiles()
                }
            }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.duplicate)) }
            IconButton(onClick = {
                val p = currentProfile ?: return@IconButton
                ContentDialog.confirm(ctx, R.string.do_you_want_to_remove_this_profile) {
                    manager.removeProfile(p)
                    onProfileChanged(null); reloadProfiles()
                }
            }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error) }
        }

        HorizontalDivider()

        // ---- Preview button ----
        FilledTonalButton(
            onClick = { showPreviewDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.controls_profile_preview))
        }

        HorizontalDivider()

        // ---- Cursor Speed ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cursor_speed), modifier = Modifier.weight(1f))
            Text("$cursorSpeedProgress%", style = MaterialTheme.typography.bodyMedium)
        }
        AndroidView(
            factory = { ctx2 ->
                SeekBar(ctx2).apply {
                    max = 200; min = 10
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                            cursorSpeedProgress = progress
                            currentProfile?.let { p ->
                                p.cursorSpeed = progress / 100.0f; p.save()
                            }
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }
            },
            update = { it.progress = cursorSpeedProgress },
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- Overlay Opacity ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.overlay_opacity), modifier = Modifier.weight(1f))
            Text("$overlayOpacityProgress%", style = MaterialTheme.typography.bodyMedium)
        }
        AndroidView(
            factory = { ctx2 ->
                SeekBar(ctx2).apply {
                    max = 100; min = 10
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                val rounded = Mathf.roundTo(progress.toFloat(), 5f).toInt()
                                overlayOpacityProgress = rounded
                                prefs.edit().putFloat("overlay_opacity", rounded / 100.0f).apply()
                            }
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }
            },
            update = { it.progress = overlayOpacityProgress },
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        // ---- Import / Export ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(onClick = { showImportMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.import_profile))
                }
                DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.open_file)) },
                        onClick = { showImportMenu = false; importLauncher.launch(arrayOf("*/*")) },
                        leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_file)) },
                        onClick = { showImportMenu = false; showDownloadDialog = true },
                        leadingIcon = { Icon(Icons.Filled.CloudDownload, null) },
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    val p = currentProfile ?: return@OutlinedButton
                    val f = manager.exportProfile(p)
                    if (f != null) AppUtils.showToast(ctx, "Profile exported to ${f.path}")
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.FileUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.export_profile))
            }
        }

        HorizontalDivider()

        // ---- Action buttons ----
        Button(
            onClick = {
                val profile = currentProfile
                if (profile != null) {
                    val intent = Intent(ctx, ControlsEditorActivity::class.java).apply {
                        putExtra("profile_id", profile.id)
                    }
                    ctx.startActivity(intent)
                } else {
                    AppUtils.showToast(ctx, R.string.no_profile_selected)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.controls_editor))
        }
        OutlinedButton(onClick = onOpenGamepadTest, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gamepad_test))
        }
        OutlinedButton(onClick = onOpenIconManager, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.icon_manager))
        }

        HorizontalDivider()

        // ---- External Controllers ----
        Text(stringResource(R.string.external_controllers), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider()
        if (controllers.isEmpty()) {
            Text(
                stringResource(R.string.no_items_to_display),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            controllers.forEach { controller ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Gamepad, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(controller.getName(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${controller.getControllerBindingCount()} ${stringResource(R.string.bindings)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            ContentDialog.confirm(ctx, R.string.do_you_want_to_remove_this_controller) {
                                currentProfile?.let { p ->
                                    p.removeController(controller); p.save()
                                }
                            }
                        }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove), tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }

    // ---- Preview Dialog ----
    if (showPreviewDialog && currentProfile != null) {
        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = { Text(stringResource(R.string.controls_profile_preview)) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(ComposeColor(0xFF1a1a1a)),
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { ctx2 ->
                            ProfilePreviewView(ctx2).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setProfile(currentProfile)
                            }
                        },
                        update = { it.setProfile(currentProfile) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // ---- Download Dialog ----
    if (showDownloadDialog) {
        LaunchedEffect(showDownloadDialog) {
            if (downloadProfilesList == null) {
                HttpUtils.download(String.format(INPUT_CONTROLS_URL, "index.txt")) { result ->
                    if (result != null && result.isNotEmpty()) {
                        downloadProfilesList = result.split("\n").filter { it.isNotBlank() }
                        selectedDownloadIndices = downloadProfilesList!!.map { false }
                    } else {
                        AppUtils.showToast(ctx, R.string.unable_to_load_profile_list)
                        showDownloadDialog = false
                    }
                }
            }
        }

        if (downloadProfilesList != null) {
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false; downloadProfilesList = null },
                title = { Text("Download Profiles") },
                text = {
                    Column {
                        downloadProfilesList!!.forEachIndexed { i, name ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = selectedDownloadIndices.getOrElse(i) { false },
                                    onCheckedChange = { checked ->
                                        selectedDownloadIndices = selectedDownloadIndices.toMutableList().also {
                                            if (i < it.size) it[i] = checked
                                        }
                                    }
                                )
                                Text(name, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    HttpUtils.download(String.format(INPUT_CONTROLS_URL, name)) { jsonStr ->
                                        if (jsonStr != null) {
                                            (ctx as? Activity)?.runOnUiThread {
                                                ContentDialog(ctx, com.winlator.cmod.R.layout.input_controls_dialog).apply {
                                                    setTitle("Preview: $name"); show()
                                                }
                                            }
                                        }
                                    }
                                }) { Icon(Icons.Filled.Visibility, "Preview") }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedNames = downloadProfilesList!!.filterIndexed { i, _ -> selectedDownloadIndices.getOrElse(i) { false } }
                        if (selectedNames.isNotEmpty()) {
                            ContentDialog.confirm(ctx, R.string.do_you_want_to_download_the_selected_profiles) {
                                for (name in selectedNames) {
                                    HttpUtils.download(String.format(INPUT_CONTROLS_URL, name)) { jsonStr ->
                                        if (jsonStr != null) {
                                            manager.importProfile(JSONObject(jsonStr))
                                        }
                                    }
                                }
                                (ctx as? Activity)?.runOnUiThread {
                                    reloadProfiles()
                                    AppUtils.showToast(ctx, "Downloaded ${selectedNames.size} profile(s)")
                                }
                            }
                        }
                        showDownloadDialog = false; downloadProfilesList = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { showDownloadDialog = false; downloadProfilesList = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}
