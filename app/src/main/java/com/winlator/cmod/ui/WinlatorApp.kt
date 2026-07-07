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
import androidx.compose.ui.res.stringResource
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
    DrawerItem(R.string.shortcuts, Icons.Filled.VideogameAsset, Screen.Shortcuts),
    DrawerItem(R.string.containers, Icons.Filled.Storage, Screen.Containers),
    DrawerItem(R.string.input_controls, Icons.Filled.Gamepad, Screen.InputControls),
    DrawerItem(R.string.saves, Icons.Filled.Save, Screen.Saves),
    DrawerItem(R.string.box64_rc_file, Icons.Filled.Code, Screen.Box86_64RC),
    DrawerItem(R.string.contents, Icons.Filled.Extension, Screen.Contents),
    DrawerItem(R.string.steam, Icons.Filled.Folder, Screen.Steam),
    DrawerItem(R.string.adrenotools_gpu_drivers, Icons.Filled.Adb, Screen.Adrenotools),
    DrawerItem(R.string.settings, Icons.Filled.Settings, Screen.Settings),
    DrawerItem(R.string.about, Icons.Filled.Info, Screen.About),
    DrawerItem(R.string.file_manager, Icons.Filled.Description, Screen.FileManager),
)

private data class DrawerItem(
    val labelResId: Int,
    val icon: ImageVector,
    val screen: Screen,
)

@Composable
private fun getScreenTitle(screen: Screen): String {
    return when (screen) {
        Screen.Shortcuts -> stringResource(R.string.shortcuts)
        Screen.Containers -> stringResource(R.string.containers)
        Screen.InputControls -> stringResource(R.string.input_controls)
        Screen.Saves -> stringResource(R.string.saves)
        Screen.Box86_64RC -> stringResource(R.string.box64_rc_file)
        Screen.Contents -> stringResource(R.string.contents)
        Screen.Adrenotools -> stringResource(R.string.adrenotools_gpu_drivers)
        Screen.Settings -> stringResource(R.string.settings)
        Screen.Steam -> stringResource(R.string.steam)
        Screen.About -> stringResource(R.string.about)
        Screen.FileManager -> stringResource(R.string.file_manager)
        Screen.GamepadTest -> stringResource(R.string.gamepad_test)
        Screen.IconManager -> stringResource(R.string.icon_manager)
        Screen.Terminal -> stringResource(R.string.terminal)
        else -> "Winlator CMOD"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinlatorApp(
    isDarkMode: Boolean,
    preferences: SharedPreferences,
    initialScreen: Screen = Screen.Containers,
    selectedProfileId: Int = 0,
    onDarkModeChange: (Boolean) -> Unit,
    onLanguageChange: (String) -> Unit,
    onOpenSteam: () -> Unit,
    onOpenFileManager: () -> Unit,
) {
    var currentScreen by remember { mutableStateOf<Screen>(initialScreen) }
    var showGPUPerformance by remember { mutableStateOf(false) }
    var showAdrenotools by remember { mutableStateOf(false) }
    var showDriverStore by remember { mutableStateOf(false) }
    var showInstalledComponents by remember { mutableStateOf(false) }
    var showContainerEdit by remember { mutableStateOf(false) }
    var editContainerId by remember { mutableStateOf(0) }
    var isContainerEditMode by remember { mutableStateOf(false) }
    var containersRefreshKey by remember { mutableStateOf(0) }
    var adrenotoolsRefreshKey by remember { mutableStateOf(0) }
    var showContainerImportInfo by remember { mutableStateOf(false) }
    var showImportGame by remember { mutableStateOf(false) }
    var importGameContainerId by remember { mutableStateOf(0) }
    var isShortcutsGridView by remember { mutableStateOf(true) }
    var showShortcutSettings by remember { mutableStateOf(false) }
    var shortcutSettingsShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var fileManagerContainerId by remember { mutableStateOf(-1) }
    var hideTopBarBySteamInfo by remember { mutableStateOf(false) }

    // First launch dialog state
    var showFirstLaunchDialog by remember { mutableStateOf(false) }
    var firstLaunchDarkMode by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val imageFs = remember { com.winlator.cmod.xenvironment.ImageFs.find(context) }
    var isInstalling by remember {
        mutableStateOf(!imageFs.isValid() || imageFs.version < com.winlator.cmod.xenvironment.ImageFsInstaller.LATEST_VERSION)
    }
    var installProgress by remember { mutableIntStateOf(0) }

    LaunchedEffect(isInstalling) {
        if (isInstalling) {
            com.winlator.cmod.xenvironment.ImageFsInstaller.installFromAssets(
                context,
                object : com.winlator.cmod.xenvironment.ImageFsInstaller.OnProgressListener {
                    override fun onProgress(progress: Int) {
                        installProgress = progress
                    }
                    override fun onFinished(success: Boolean) {
                        isInstalling = false
                        containersRefreshKey++
                    }
                }
            )
        }
    }

    // После завершения установки ImageFs проверяем, нужно ли показать диалог первого запуска
    val isFirstLaunch = remember {
        !preferences.getBoolean("first_launch_completed", false)
    }

    LaunchedEffect(isInstalling, isFirstLaunch) {
        if (!isInstalling && isFirstLaunch) {
            firstLaunchDarkMode = false
            showFirstLaunchDialog = true
        }
    }

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

    if (isInstalling) {
        InstallerScreen(progress = installProgress, isFinished = installProgress >= 100)
    } else {
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
                                    contentDescription = stringResource(item.labelResId),
                                    tint = if (currentScreen == item.screen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(item.labelResId),
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
                    if (!hideTopBarBySteamInfo) {
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
                                        currentScreen = Screen.Terminal
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
                    }
                },
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (hideTopBarBySteamInfo) PaddingValues(0.dp) else padding),
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
                        onShowSteamInfo = { show -> hideTopBarBySteamInfo = show }
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
                    Screen.InputControls -> InputControlsScreen(
                        selectedProfileId = selectedProfileId,
                        onOpenGamepadTest = { currentScreen = Screen.GamepadTest },
                        onOpenIconManager = { currentScreen = Screen.IconManager }
                    )
                    Screen.GamepadTest -> GamepadTestScreen(
                        onBack = { currentScreen = Screen.InputControls }
                    )
                    Screen.IconManager -> IconManagerScreen(
                        onBack = { currentScreen = Screen.InputControls }
                    )
                    Screen.Terminal -> TerminalScreen(
                        onBack = { currentScreen = Screen.Containers }
                    )
                    Screen.Saves -> FragmentHostScreen(
                        fragmentClass = com.winlator.cmod.SavesFragment::class.java,
                    )
                    Screen.Box86_64RC -> Box86_64RCScreen(
                        onBack = { currentScreen = Screen.Containers }
                    )
                    Screen.Contents -> ContentsScreen(
                        onBack = { currentScreen = Screen.Containers },
                        onOpenInstalledComponents = { showInstalledComponents = true },
                    )
                    Screen.Adrenotools -> AdrenotoolsScreen(
                        refreshKey = adrenotoolsRefreshKey,
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
                        onReinstallImageFs = { isInstalling = true }
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
                    onDriverInstalled = { adrenotoolsRefreshKey++ },
                )
            }
            if (showInstalledComponents) {
                InstalledComponentsScreen(onBack = { showInstalledComponents = false })
            }
            if (showContainerEdit) {
                ContainerEditScreen(
                    containerManager = appContainerManager,
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

            // Диалог первого запуска — поверх всего
            if (showFirstLaunchDialog) {
                FirstLaunchDialog(
                    preferences = preferences,
                    isDarkMode = firstLaunchDarkMode,
                    onThemeSelected = { dark ->
                        firstLaunchDarkMode = dark
                        // Немедленно обновляем pref, чтобы тема применилась
                        preferences.edit().putBoolean("dark_mode", dark).apply()
                    },
                    onDismiss = {
                        showFirstLaunchDialog = false
                        // Применяем выбранную тему через recreate в Activity
                        onDarkModeChange(firstLaunchDarkMode)
                    },
                )
            }
        }
    }
    }
}