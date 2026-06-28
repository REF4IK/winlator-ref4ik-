package com.winlator.cmod.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R

// Note: InputControlsScreen is defined in InputControlsScreen.kt (real implementation)

@Composable
fun SavesScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.saves),
        subtitle = "Manage your cloud saves"
    )
}

@Composable
fun Box86_64RCScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.box64_rc_file),
        subtitle = "Configure Box86/Box64 environment variables"
    )
}

@Composable
fun ContentsScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.contents),
        subtitle = "Browse and manage content packs"
    )
}

@Composable
fun AdrenotoolsScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.adrenotools_gpu_drivers),
        subtitle = "Manage Adrenotools GPU drivers"
    )
}

@Composable
fun FileManagerScreen() {
    PlaceholderScreen(
        title = stringResource(R.string.file_manager),
        subtitle = "Browse and manage files"
    )
}
