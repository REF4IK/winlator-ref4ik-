package com.winlator.cmod.ui

import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R
import com.winlator.cmod.TerminalActivity
import com.winlator.cmod.container.ContainerManager
import com.winlator.cmod.container.Shortcut
import com.winlator.cmod.core.AppUtils
import com.winlator.cmod.core.FileUtils
import com.winlator.cmod.ui.navigation.Screen
import com.winlator.cmod.ui.screens.*

/**
 * Navigation drawer items corresponding to the main menu.
 */
private val drawerItems = listOf(
    DrawerItem("Shortcuts", Icons.Filled.VideogameAsset, Screen.Shortcuts),
    DrawerItem("Containers", Icons.Filled.Storage, Screen.Containers),
    DrawerItem("Input Controls", Icons.Filled.Gamepad, Screen.InputControls),
    DrawerItem("Saves", Icons.Filled.Save, Screen.Saves),
    DrawerItem("Box64 RC", Icons.Filled.Code, Screen.Box86_64RC),
    DrawerItem("Contents", Icons.Filled.Extension, Screen.Contents),
    DrawerItem("Steam", Icons.Filled.Folder, Screen.Steam),
    DrawerItem("Adreno GPU Drivers", Icons.Filled.Adb, Screen.Adrenotools),
    DrawerItem("Settings", Icons.Filled.Settings, Screen.Settings),
    DrawerItem("About", Icons.Filled.Info, Screen.About),
    DrawerItem("File Manager", Icons.Filled.Description, Screen.FileManager),
)

private data class DrawerItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen,
)

private fun getScreenTitle(screen: Screen): String {
    return when (screen) {
        Screen.Shortcuts -> "Shortcuts"
        Screen.Containers -> "Containers"
        Screen.InputControls -> "Input Controls"
        Screen.Saves -> "Saves"
        Screen.Box86_64RC -> "Box64 RC"
        Screen.Contents -> "Contents"
        Screen.Adrenotools -> "Adreno GPU Drivers"
        Screen.Settings -> "Settings"
        else -> "Winlator CMOD"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinlatorApp(
    isDarkMode: Boolean,
    preferences: SharedPreferences,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onOpenSteam: () -> Unit,
    onOpenFileManager: () -> Unit,
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Containers) }
    var showGPUPerformance by remember { mutableStateOf(false) }
    var showAdrenotools by remember { mutableStateOf(false) }
    var showDriverStore by remember { mutableStateOf(false) }
    var showInstalledComponents by remember { mutableStateOf(false) }
    var showContainerEdit by remember { mutableStateOf(false) }
    var editContainerId by remember { mutableStateOf(0) }
    var isContainerEditMode by remember { mutableStateOf(false) }
    var containersRefreshKey by remember { mutableStateOf(0) }
    var showContainerImportInfo by remember { mutableStateOf(false) }
    var showImportGame by remember { mutableStateOf(false) }
    var importGameContainerId by remember { mutableStateOf(0) }
    var isShortcutsGridView by remember { mutableStateOf(true) }
    var showShortcutSettings by remember { mutableStateOf(false) }
    var shortcutSettingsShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var fileManagerContainerId by remember { mutableStateOf(-1) }

    // Скрываем только верхний статус-бар (часы, батарея), нижний навигационный бар оставляем видимым, поддерживая вырез (notch)
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    DisposableEffect(Unit) {
        if (activity != null) {
            // Разрешаем отображение под вырезом (notch)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val layoutParams = activity.window.attributes
                layoutParams.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                activity.window.attributes = layoutParams
            }
            activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity.window.setDecorFitsSystemWindows(false)
                val controller = activity.window.decorView.windowInsetsController
                if (controller != null) {
                    controller.hide(android.view.WindowInsets.Type.statusBars())
                    controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
            }
        }
        onDispose { }
    }
    LaunchedEffect(isDarkMode) {
        if (activity != null) {
            val window = activity.window
            window.navigationBarColor = if (isDarkMode) {
                android.graphics.Color.parseColor("#121212")
            } else {
                android.graphics.Color.parseColor("#FFFFFF")
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val controller = window.decorView.windowInsetsController
                controller?.setSystemBarsAppearance(
                    if (isDarkMode) 0 else android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val decorView = window.decorView
                var flags = decorView.systemUiVisibility
                flags = if (isDarkMode) {
                    flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
                decorView.systemUiVisibility = flags
            }
        }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val appContainerManager = remember(context) { ContainerManager(context) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            val importDir = FileUtils.getFileFromUri(context, uri)
            if (importDir == null || !importDir.isDirectory) {
                AppUtils.showToast(context, "Invalid container directory.")
                return@rememberLauncherForActivityResult
            }
            Thread {
                try {
                    appContainerManager.importContainer(importDir) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            containersRefreshKey++
                            AppUtils.showToast(context, "Container imported successfully.")
                        }
                    }
                } catch (e: Exception) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        AppUtils.showToast(context, "Error importing container: ${e.message}")
                    }
                }
            }.start()
        }
    }

    // Обработка системной кнопки "Назад":
    // 1) открыт Driver Store → закрыть его
    // 2) открыт Adrenotools → закрыть его
    // 3) открыт GPU-экран → закрыть его
    // 4) открыт drawer → закрыть его
    // 5) не на главном экране → вернуться на Containers (главный)
    // 6) иначе → стандартное поведение (выход/сворачивание приложения)
    BackHandler(enabled = showContainerEdit || showInstalledComponents || showDriverStore || showAdrenotools || showGPUPerformance || showImportGame || showShortcutSettings || drawerState.isOpen || currentScreen != Screen.Containers) {
        when {
            showContainerEdit -> { showContainerEdit = false; containersRefreshKey++ }
            showInstalledComponents -> showInstalledComponents = false
            showDriverStore -> showDriverStore = false
            showAdrenotools -> showAdrenotools = false
            showGPUPerformance -> showGPUPerformance = false
            showImportGame -> { showImportGame = false; containersRefreshKey++ }
            showShortcutSettings -> { showShortcutSettings = false }
            drawerState.isOpen -> scope.launch { drawerState.close() }
            currentScreen != Screen.Containers -> currentScreen = Screen.Containers
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Image(
                        painter = painterResource(R.drawable.logowi),
                        contentDescription = "Logo",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(top = 16.dp, bottom = 8.dp)
                    )
                    // Navigation items
                    drawerItems.forEach { item ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = if (currentScreen == item.screen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                )
                            },
                            selected = currentScreen == item.screen,
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            onClick = {
                                currentScreen = item.screen
                                scope.launch { drawerState.close() }
                                 when (item.screen) {
                                     Screen.Steam -> onOpenSteam()
                                     Screen.FileManager -> fileManagerContainerId = -1
                                     else -> { }
                                 }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = getScreenTitle(currentScreen)
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            if (currentScreen == Screen.Shortcuts) {
                                IconButton(onClick = { isShortcutsGridView = !isShortcutsGridView }) {
                                    Icon(
                                        if (isShortcutsGridView) Icons.Filled.GridOn else Icons.Filled.List,
                                        contentDescription = "Toggle view",
                                    )
                                }
                            }
                            if (currentScreen == Screen.Containers) {
                                IconButton(onClick = {
                                    context.startActivity(android.content.Intent(context, TerminalActivity::class.java))
                                }) { Icon(Icons.Filled.Terminal, contentDescription = "Open Terminal") }
                                IconButton(onClick = { showContainerImportInfo = true }) {
                                    Icon(Icons.Filled.Download, contentDescription = "Import Container")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    when (currentScreen) {
                    Screen.Shortcuts -> ShortcutsScreen(
                        refreshKey = containersRefreshKey,
                        isGridView = isShortcutsGridView,
                        onToggleView = { isShortcutsGridView = !isShortcutsGridView },
                        onImportGame = { container ->
                            importGameContainerId = container.id
                            showImportGame = true
                        },
                        onOpenShortcutSettings = { shortcut ->
                            shortcutSettingsShortcut = shortcut
                            showShortcutSettings = true
                        },
                    )
                    Screen.Containers -> ContainersScreen(
                        containerManager = appContainerManager,
                        refreshKey = containersRefreshKey,
                        onCreateContainer = {
                            editContainerId = 0
                            isContainerEditMode = false
                            showContainerEdit = true
                        },
                        onEditContainer = { id ->
                            editContainerId = id
                            isContainerEditMode = true
                            showContainerEdit = true
                        },
                        onOpenFileBrowser = { id ->
                            fileManagerContainerId = id
                            currentScreen = Screen.FileManager
                        }
                    )
                    Screen.InputControls -> InputControlsScreen()
                    Screen.Saves -> FragmentHostScreen(
                        fragmentClass = com.winlator.cmod.SavesFragment::class.java,
                    )
                    Screen.Box86_64RC -> FragmentHostScreen(
                        fragmentClass = com.winlator.cmod.Box86_64RCFragment::class.java,
                    )
                    Screen.Contents -> ContentsScreen(
                        onBack = { currentScreen = Screen.Containers },
                        onOpenInstalledComponents = { showInstalledComponents = true },
                    )
                    Screen.Adrenotools -> AdrenotoolsScreen(
                        onBack = { currentScreen = Screen.Containers },
                        onOpenDriverStore = { showDriverStore = true },
                    )
                    Screen.Settings -> SettingsScreen(
                        preferences = preferences,
                        appContext = context,
                        onDarkModeChange = onDarkModeChange,
                        onLanguageChange = onLanguageChange,
                        onTransitionAnimationChange = { anim ->
                            preferences?.edit()?.putString("transition_animation", anim)?.apply()
                        },
                        onConfirmSave = {
                            // Как в оригинале: BTConfirm сохраняет настройки и переходит на Containers (главный экран)
                            currentScreen = Screen.Containers
                            com.winlator.cmod.core.AppUtils.showToast(context, "Настройки сохранены")
                        },
                        onOpenGPUPerformance = { showGPUPerformance = true },
                    )
                    Screen.About -> AboutScreen()
                    Screen.FileManager -> FileManagerScreen(
                        containerId = fileManagerContainerId,
                        onBack = { currentScreen = Screen.Containers }
                    )
                    else -> PlaceholderScreen(
                        title = "Winlator CMOD",
                        subtitle = "Select a section from the menu",
                    )
                }
                }
            }

            // Overlay-экраны поверх всего (со своим TopAppBar, без дублирования)
            if (showGPUPerformance) {
                GPUPerformanceScreen(onBack = { showGPUPerformance = false })
            }
            if (showDriverStore) {
                DriverStoreScreen(
                    onBack = { showDriverStore = false },
                    onDriverInstalled = { /* список Adrenotools обновится при возврате */ },
                )
            }
            if (showInstalledComponents) {
                InstalledComponentsScreen(onBack = { showInstalledComponents = false })
            }
            if (showContainerEdit) {
                ContainerEditScreen(
                    containerId = editContainerId,
                    isEditMode = isContainerEditMode,
                    onBack = { showContainerEdit = false; containersRefreshKey++ },
                )
            }

            if (showContainerImportInfo) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showContainerImportInfo = false },
                    title = { androidx.compose.material3.Text("Import Container") },
                    text = { androidx.compose.material3.Text("This option will allow you to restore an exported container. To proceed, click OK and select your 'xuser-' directory. The container's settings will need to be configured after a successful import, but all files and shortcuts should be restored if you are restoring a real container. Beware, the directory you select will be copied into the app's storage directory, so be sure you have enough space. You can delete your copy afterward.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showContainerImportInfo = false; importLauncher.launch(null) }) {
                            androidx.compose.material3.Text("OK")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showContainerImportInfo = false }) {
                            androidx.compose.material3.Text("Cancel")
                        }
                    },
                )
            }
            if (showImportGame) {
                val container = appContainerManager.getContainerById(importGameContainerId)
                if (container != null) {
                    ImportGameScreen(
                        container = container,
                        onBack = { showImportGame = false; containersRefreshKey++ },
                    )
                }
            }
            if (showShortcutSettings) {
                val shortcut = shortcutSettingsShortcut
                if (shortcut != null) {
                    ShortcutSettingsScreen(
                        shortcut = shortcut,
                        onBack = {
                            showShortcutSettings = false
                            containersRefreshKey++
                        },
                    )
                }
            }
        }
    }
}