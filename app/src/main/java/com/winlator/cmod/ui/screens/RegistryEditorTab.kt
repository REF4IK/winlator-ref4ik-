package com.winlator.cmod.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.core.MmkvPreferences
import com.winlator.cmod.core.WineRegistryEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private const val HIVE_USER = 0
private const val HIVE_SYSTEM = 1

private val REG_VALUE_TYPES = listOf("String", "Dword", "Hex")

private const val PREFS_SPOOF_IDENTIFIER = "reg_spoof_identifier"
private const val PREFS_SPOOF_VENDOR = "reg_spoof_vendor"
private const val PREFS_SPOOF_MHZ = "reg_spoof_mhz"
private const val PREFS_SPOOF_FEATURESET = "reg_spoof_featureset"

private data class CpuSpoofPreset(
    val name: String,
    val identifier: String,
    val vendor: String,
    val mhz: Int,
    val featureSet: String,
)

private val PRESET_ARM = CpuSpoofPreset(
    name = "ARM",
    identifier = "ARMv8 (64-bit) Family 8 Model D82 Revision 1",
    vendor = "ARM",
    mhz = 2016,
    featureSet = "f4",
)

private val PRESET_INTEL = CpuSpoofPreset(
    name = "Intel x86",
    identifier = "Intel64 Family 6 Model 70 Stepping 1",
    vendor = "GenuineIntel",
    mhz = 2016,
    featureSet = "2b7bbfff",
)

@Composable
fun RegistryEditorTab(
    containerRootDir: File?,
    wineVersion: String,
    wineArch: String,
    onToast: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val sp = remember { MmkvPreferences() }
    var hive by remember { mutableStateOf(HIVE_USER) }
    var currentPath by remember { mutableStateOf("") }
    var refreshKey by remember { mutableStateOf(0) }

    val regFile = remember(containerRootDir, hive) {
        if (containerRootDir == null) null
        else File(containerRootDir, if (hive == HIVE_USER) ".wine/user.reg" else ".wine/system.reg")
    }
    val hivePrefix = if (hive == HIVE_USER) "HKEY_CURRENT_USER" else "HKEY_LOCAL_MACHINE"

    var subKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var values by remember { mutableStateOf<List<WineRegistryEditor.RegValue>>(emptyList()) }
    var fileExists by remember { mutableStateOf(false) }

    LaunchedEffect(regFile, currentPath, refreshKey) {
        if (regFile == null || !regFile.isFile) {
            subKeys = emptyList()
            values = emptyList()
            fileExists = false
            return@LaunchedEffect
        }
        fileExists = true
        WineRegistryEditor(regFile).use { reg ->
            subKeys = reg.getSubKeys(currentPath)
            values = reg.getValues(currentPath)
        }
    }

    var showAddKeyDialog by remember { mutableStateOf(false) }
    var editingValue by remember { mutableStateOf<WineRegistryEditor.RegValue?>(null) }
    var addingValue by remember { mutableStateOf(false) }
    var deleteKeyConfirm by remember { mutableStateOf<String?>(null) }
    var deleteValueConfirm by remember { mutableStateOf<WineRegistryEditor.RegValue?>(null) }
    var showSpoofDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null || regFile == null) return@rememberLauncherForActivityResult
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                onToast(ctx.getString(R.string.registry_import_failed))
                return@rememberLauncherForActivityResult
            }
            val content = decodeRegText(bytes)
            scope.launch(Dispatchers.IO) {
                WineRegistryEditor(regFile).use { it.importRegFile(content) }
                refreshKey++
            }
            onToast(ctx.getString(R.string.registry_import_done))
        } catch (e: Exception) {
            onToast(ctx.getString(R.string.registry_import_failed) + ": ${e.message}")
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri == null || regFile == null) return@rememberLauncherForActivityResult
        val path = currentPath
        val prefix = hivePrefix
        scope.launch(Dispatchers.IO) {
            try {
                val text = WineRegistryEditor(regFile).use { it.exportReg(path, prefix) }
                ctx.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(text.toByteArray(Charsets.UTF_8))
                }
                onToast(ctx.getString(R.string.registry_export_done))
            } catch (e: Exception) {
                onToast(ctx.getString(R.string.registry_export_failed) + ": ${e.message}")
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (regFile == null) {
            Text(
                stringResource(R.string.registry_created),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            return@Column
        }

        SectionCard(title = stringResource(R.string.registry_editor), icon = Icons.Filled.AccountTree) {
            Text(
                stringResource(R.string.registry_current_wine, "$wineVersion (${wineArch})"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = hive == HIVE_USER,
                    onClick = { hive = HIVE_USER; currentPath = "" },
                    label = { Text(stringResource(R.string.registry_hive_user)) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = hive == HIVE_SYSTEM,
                    onClick = { hive = HIVE_SYSTEM; currentPath = "" },
                    label = { Text(stringResource(R.string.registry_hive_system)) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (!fileExists) {
                Text(
                    stringResource(R.string.registry_file_not_found),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                return@SectionCard
            }

            // Хлебные крошки
            val pathParts = if (currentPath.isEmpty()) emptyList() else currentPath.split("\\")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scrollState = rememberScrollState()
                Row(Modifier.horizontalScroll(scrollState), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.registry_root),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { currentPath = "" }.padding(4.dp)
                    )
                    pathParts.forEachIndexed { index, part ->
                        Text(">", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val targetPath = pathParts.subList(0, index + 1).joinToString("\\")
                        val isLast = index == pathParts.size - 1
                        Text(
                            part,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLast) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                            fontWeight = if (isLast) androidx.compose.ui.text.font.FontWeight.Bold else null,
                            modifier = if (isLast) Modifier.padding(4.dp) else Modifier.clickable { currentPath = targetPath }.padding(4.dp)
                        )
                    }
                }
            }

            // Кнопки действий: ключи/значения
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { showAddKeyDialog = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.CreateNewFolder, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.registry_add_key), maxLines = 1)
                }
                OutlinedButton(onClick = { addingValue = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.registry_add_value), maxLines = 1)
                }
            }
            // Кнопки действий: импорт/экспорт/спуфинг
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.registry_import), maxLines = 1)
                }
                OutlinedButton(onClick = { exportLauncher.launch("registry_export.reg") }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.registry_export), maxLines = 1)
                }
            }
            if (hive == HIVE_SYSTEM) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showSpoofDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Memory, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.registry_cpu_spoof), maxLines = 1)
                    }
                }
            }

            // Подключи
            if (subKeys.isNotEmpty()) {
                Text(
                    "Subkeys (${subKeys.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                subKeys.forEach { subKey ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                            .clickable { currentPath = if (currentPath.isEmpty()) subKey else "$currentPath\\$subKey" },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(subKey, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Значения
            Text(
                "Values (${values.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            if (values.isEmpty()) {
                Text(
                    stringResource(R.string.registry_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                values.forEach { value ->
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                            .clickable { editingValue = value },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    value.name ?: stringResource(R.string.registry_default_value),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "${value.type}: ${value.value.ifEmpty { "(empty)" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2
                                )
                            }
                            IconButton(onClick = { deleteValueConfirm = value }) {
                                Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (currentPath.isNotEmpty()) {
                TextButton(
                    onClick = { deleteKeyConfirm = currentPath },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.registry_delete_key), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // Диалог добавления ключа
    if (showAddKeyDialog) {
        var keyName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddKeyDialog = false },
            title = { Text(stringResource(R.string.registry_add_key)) },
            text = {
                Column {
                    Text("Path: ${if (currentPath.isEmpty()) "\\" else currentPath}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = keyName,
                        onValueChange = { keyName = it },
                        label = { Text(stringResource(R.string.registry_key_name)) },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = keyName.trim()
                    if (trimmed.isNotEmpty() && !trimmed.contains("\\")) {
                        val newKey = if (currentPath.isEmpty()) trimmed else "$currentPath\\$trimmed"
                        scope.launch(Dispatchers.IO) {
                            WineRegistryEditor(regFile).use { reg ->
                                reg.setStringValue(newKey, null, "")
                            }
                            refreshKey++
                        }
                    }
                    showAddKeyDialog = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { showAddKeyDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Диалог добавления/редактирования значения
    if (addingValue || editingValue != null) {
        val existing = editingValue
        var valueName by remember(existing) { mutableStateOf(existing?.name ?: "") }
        var valueType by remember(existing) { mutableStateOf(existing?.type?.let { t ->
            if (t in REG_VALUE_TYPES) t else if (t == "Qword") "Hex" else "String"
        } ?: "String") }
        var valueText by remember(existing) { mutableStateOf(existing?.value ?: "") }

        AlertDialog(
            onDismissRequest = { addingValue = false; editingValue = null },
            title = { Text(if (existing != null) stringResource(R.string.registry_edit_value) else stringResource(R.string.registry_add_value)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = valueName,
                        onValueChange = { valueName = it },
                        label = { Text(stringResource(R.string.registry_value_name)) },
                        singleLine = true,
                        enabled = existing == null
                    )
                    var typeExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.registry_type) + ": $valueType", modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                            REG_VALUE_TYPES.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = { valueType = type; typeExpanded = false }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text(stringResource(R.string.registry_value)) },
                        singleLine = valueType != "Hex",
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            WineRegistryEditor(regFile).use { reg ->
                                val name = valueName.trim().ifEmpty { null }
                                when (valueType) {
                                    "Dword" -> {
                                        val clean = valueText.trim().removePrefix("0x")
                                        if (clean.isEmpty()) throw IllegalArgumentException("empty")
                                        reg.setDwordValue(currentPath, name, java.lang.Long.decode("0x" + clean).toInt())
                                    }
                                    "Hex" -> {
                                        val clean = valueText.filter { it.isDigit() || it in "abcdefABCDEF" }
                                        if (clean.isEmpty()) throw IllegalArgumentException("empty")
                                        reg.setHexValue(currentPath, name, clean)
                                    }
                                    else -> reg.setStringValue(currentPath, name, valueText)
                                }
                            }
                            refreshKey++
                            onToast(ctx.getString(R.string.registry_saved))
                        } catch (e: Exception) {
                            onToast(ctx.getString(R.string.registry_invalid_hex))
                        }
                    }
                    addingValue = false
                    editingValue = null
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { addingValue = false; editingValue = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Диалог спуфинга CPU
    if (showSpoofDialog) {
        val autoPreset = if (wineArch.equals("arm64ec", true)) PRESET_ARM else PRESET_INTEL
        var spoofIdentifier by remember { mutableStateOf(sp.getString(PREFS_SPOOF_IDENTIFIER, null) ?: autoPreset.identifier) }
        var spoofVendor by remember { mutableStateOf(sp.getString(PREFS_SPOOF_VENDOR, null) ?: autoPreset.vendor) }
        var spoofMhz by remember { mutableStateOf(sp.getString(PREFS_SPOOF_MHZ, null) ?: autoPreset.mhz.toString()) }
        var spoofFeatureSet by remember { mutableStateOf(sp.getString(PREFS_SPOOF_FEATURESET, null) ?: autoPreset.featureSet) }
        var spoofPresetName by remember { mutableStateOf(if (sp.getString(PREFS_SPOOF_IDENTIFIER, null) == null) autoPreset.name else "") }

        fun applyPreset(preset: CpuSpoofPreset) {
            spoofIdentifier = preset.identifier
            spoofVendor = preset.vendor
            spoofMhz = preset.mhz.toString()
            spoofFeatureSet = preset.featureSet
            spoofPresetName = preset.name
        }

        AlertDialog(
            onDismissRequest = { showSpoofDialog = false },
            title = { Text(stringResource(R.string.registry_cpu_spoof)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = spoofPresetName == PRESET_ARM.name,
                            onClick = { applyPreset(PRESET_ARM) },
                            label = { Text(PRESET_ARM.name) }
                        )
                        FilterChip(
                            selected = spoofPresetName == PRESET_INTEL.name,
                            onClick = { applyPreset(PRESET_INTEL) },
                            label = { Text(PRESET_INTEL.name) }
                        )
                    }
                    Text(
                        stringResource(R.string.registry_cpu_spoof_hint, wineArch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = spoofIdentifier,
                        onValueChange = { spoofIdentifier = it; spoofPresetName = "" },
                        label = { Text("Identifier") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = spoofVendor,
                        onValueChange = { spoofVendor = it; spoofPresetName = "" },
                        label = { Text("VendorIdentifier") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = spoofMhz,
                            onValueChange = { spoofMhz = it.filter { c -> c.isDigit() }; spoofPresetName = "" },
                            label = { Text("~MHz") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = spoofFeatureSet,
                            onValueChange = { spoofFeatureSet = it.filter { c -> c.isDigit() || c in "abcdefABCDEF" }; spoofPresetName = "" },
                            label = { Text("FeatureSet (hex)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            WineRegistryEditor(regFile).use { reg ->
                                val numCpus = Runtime.getRuntime().availableProcessors()
                                for (i in 0 until numCpus) {
                                    val key = "Hardware\\Description\\System\\CentralProcessor\\$i"
                                    reg.setStringValue(key, "Identifier", spoofIdentifier)
                                    reg.setStringValue(key, "VendorIdentifier", spoofVendor)
                                    reg.setDwordValue(key, "~MHz", spoofMhz.toIntOrNull() ?: 0)
                                    reg.setDwordValue(key, "FeatureSet", spoofFeatureSet.toLongOrNull(16)?.toInt() ?: 0)
                                }
                            }
                            refreshKey++
                        }
                        sp.edit()
                            .putString(PREFS_SPOOF_IDENTIFIER, spoofIdentifier)
                            .putString(PREFS_SPOOF_VENDOR, spoofVendor)
                            .putString(PREFS_SPOOF_MHZ, spoofMhz)
                            .putString(PREFS_SPOOF_FEATURESET, spoofFeatureSet)
                            .apply()
                        showSpoofDialog = false
                        onToast(ctx.getString(R.string.registry_cpu_spoof_done))
                    }) {
                        Text(stringResource(R.string.apply))
                    }
                    TextButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            WineRegistryEditor(regFile).use { reg ->
                                val numCpus = Runtime.getRuntime().availableProcessors()
                                for (i in 0 until numCpus) {
                                    val key = "Hardware\\Description\\System\\CentralProcessor\\$i"
                                    reg.removeValue(key, "Identifier")
                                    reg.removeValue(key, "VendorIdentifier")
                                    reg.removeValue(key, "~MHz")
                                    reg.removeValue(key, "FeatureSet")
                                }
                            }
                            refreshKey++
                        }
                        sp.edit()
                            .remove(PREFS_SPOOF_IDENTIFIER)
                            .remove(PREFS_SPOOF_VENDOR)
                            .remove(PREFS_SPOOF_MHZ)
                            .remove(PREFS_SPOOF_FEATURESET)
                            .apply()
                        showSpoofDialog = false
                        onToast(ctx.getString(R.string.registry_cpu_spoof_cleared))
                    }) {
                        Text(stringResource(R.string.registry_cpu_spoof_clear), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showSpoofDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        )
    }

    // Подтверждение удаления ключа
    deleteKeyConfirm?.let { keyPath ->
        AlertDialog(
            onDismissRequest = { deleteKeyConfirm = null },
            title = { Text(stringResource(R.string.registry_delete_key)) },
            text = { Text(stringResource(R.string.registry_confirm_delete_key, keyPath)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        WineRegistryEditor(regFile).use { reg ->
                            reg.removeKey(keyPath, true)
                        }
                        refreshKey++
                    }
                    deleteKeyConfirm = null
                }) { Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteKeyConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // Подтверждение удаления значения
    deleteValueConfirm?.let { value ->
        AlertDialog(
            onDismissRequest = { deleteValueConfirm = null },
            title = { Text(stringResource(R.string.registry_delete_value)) },
            text = { Text(stringResource(R.string.registry_confirm_delete_value, value.name ?: "(Default)")) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        WineRegistryEditor(regFile).use { reg ->
                            reg.removeValue(currentPath, value.name)
                        }
                        refreshKey++
                    }
                    deleteValueConfirm = null
                }) { Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteValueConfirm = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private fun decodeRegText(bytes: ByteArray): String {
    val start = if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) 2 else 0
    if (start == 2) return String(bytes, start, bytes.size - start, Charsets.UTF_16LE)
    if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
    }
    return String(bytes, Charsets.UTF_8)
}
