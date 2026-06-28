package com.winlator.cmod.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes for all screens in the app.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    data object Shortcuts : Screen("shortcuts", "Shortcuts", Icons.Filled.VideogameAsset)
    data object Containers : Screen("containers", "Containers", Icons.Filled.Storage)
    data object InputControls : Screen("input_controls", "Input Controls", Icons.Filled.Gamepad)
    data object Saves : Screen("saves", "Saves", Icons.Filled.Save)
    data object Box86_64RC : Screen("box86_64_rc", "Box64 RC File", Icons.Filled.Code)
    data object Contents : Screen("contents", "Contents", Icons.Filled.Extension)
    data object Steam : Screen("steam", "Steam", Icons.Filled.Folder)
    data object Adrenotools : Screen("adrenotools", "Adreno GPU Drivers", Icons.Filled.Adb)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object GPUPerformance : Screen("gpu_performance", "GPU Performance", Icons.Filled.Memory)
    data object About : Screen("about", "About", Icons.Filled.Info)
    data object FileManager : Screen("file_manager", "File Manager", Icons.Filled.Description)
    data object ContainerDetail : Screen("container_detail/{containerId}", "Container", Icons.Filled.Storage) {
        fun createRoute(containerId: Int = -1) = "container_detail/$containerId"
    }
}
