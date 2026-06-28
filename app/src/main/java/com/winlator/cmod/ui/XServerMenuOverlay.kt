package com.winlator.cmod.ui

import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.XServerDisplayActivity

class XServerMenuController(private val activity: XServerDisplayActivity) {
    private val composeOverlay: ComposeView = activity.findViewById(R.id.ComposeOverlay)
    var isMenuVisible by mutableStateOf(false)
        private set

    init {
        composeOverlay.setContent {
            val isDark = com.winlator.cmod.contentdialog.ContentDialog.shouldUseDarkDialog(activity)
            com.winlator.cmod.ui.theme.WinlatorTheme(darkTheme = isDark) {
                if (isMenuVisible) {
                    XServerMenuOverlay(
                        activity = activity,
                        onDismiss = { hideMenu() }
                    )
                }
            }
        }

        // Set ViewTree owners on composeOverlay via reflection
        try {
            val viewClass = Class.forName("android.view.View")
            val viewTreeLifecycleOwnerClass = Class.forName("androidx.lifecycle.ViewTreeLifecycleOwner")
            val setLifecycleMethod = viewTreeLifecycleOwnerClass.getMethod("set", viewClass, Class.forName("androidx.lifecycle.LifecycleOwner"))
            val viewTreeViewModelStoreOwnerClass = Class.forName("androidx.lifecycle.ViewTreeViewModelStoreOwner")
            val setViewModelStoreMethod = viewTreeViewModelStoreOwnerClass.getMethod("set", viewClass, Class.forName("androidx.lifecycle.ViewModelStoreOwner"))
            val viewTreeSavedStateRegistryOwnerClass = Class.forName("androidx.savedstate.ViewTreeSavedStateRegistryOwner")
            val setSavedStateMethod = viewTreeSavedStateRegistryOwnerClass.getMethod("set", viewClass, Class.forName("androidx.savedstate.SavedStateRegistryOwner"))

            val setOwners = { view: View ->
                setLifecycleMethod.invoke(null, view, activity)
                setViewModelStoreMethod.invoke(null, view, activity)
                setSavedStateMethod.invoke(null, view, activity)
            }

            setOwners(composeOverlay)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun showMenu() {
        isMenuVisible = true
        composeOverlay.visibility = View.VISIBLE
    }

    fun hideMenu() {
        isMenuVisible = false
        composeOverlay.visibility = View.GONE
    }


    fun toggleMenu() {
        if (isMenuVisible) hideMenu() else showMenu()
    }
}

@Composable
fun XServerMenuOverlay(
    activity: XServerDisplayActivity,
    onDismiss: () -> Unit
) {
    val isDark = com.winlator.cmod.contentdialog.ContentDialog.shouldUseDarkDialog(activity)
    val preferences = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity) }
    val enableLogs = remember {
        preferences.getBoolean("enable_wine_debug", false) || preferences.getBoolean("enable_box86_64_logs", false)
    }

    var isPaused by remember { mutableStateOf(activity.isPaused) }

    // List of menu items to render
    data class XMenuItem(
        val id: Int,
        val titleRes: Int?,
        val titleString: String?,
        val iconRes: Int
    )

    val menuItems = remember(isPaused, enableLogs) {
        val list = mutableListOf(
            XMenuItem(R.id.main_menu_keyboard, R.string.keyboard, null, R.drawable.icon_keyboard),
            XMenuItem(R.id.main_menu_input_controls, R.string.input_controls, null, R.drawable.icon_input_controls),
            XMenuItem(R.id.main_menu_toggle_fullscreen, R.string.toggle_fullscreen, null, R.drawable.icon_fullscreen),
            XMenuItem(R.id.main_menu_pip_mode, R.string.pip_mode, null, R.drawable.ic_picture_in_picture_alt),
            XMenuItem(R.id.main_menu_frame_generation, R.string.lsfg_title, null, R.drawable.icon_screen_effect),
            XMenuItem(R.id.main_menu_screen_effects, R.string.screen_effect, null, R.drawable.icon_screen_effect),
            XMenuItem(R.id.main_menu_task_manager, R.string.task_manager, null, R.drawable.icon_task_manager),
            XMenuItem(R.id.main_menu_fps_counter, R.string.fps_counter, null, R.drawable.icon_debug),
            XMenuItem(R.id.main_menu_active_windows, R.string.active_windows, null, R.drawable.icon_window_list),
            XMenuItem(
                R.id.main_menu_pause,
                null,
                if (isPaused) "Resume" else "Pause",
                if (isPaused) R.drawable.icon_play else R.drawable.icon_pause
            ),
            XMenuItem(R.id.main_menu_winetricks, null, "Winetricks", R.drawable.icon_wine),
            XMenuItem(R.id.main_menu_terminal, null, "Debug Terminal", R.drawable.icon_env_var)
        )
        if (enableLogs) {
            list.add(XMenuItem(R.id.main_menu_logs, R.string.logs, null, R.drawable.icon_debug))
        }
        list.add(XMenuItem(R.id.main_menu_exit, R.string.exit, null, R.drawable.icon_exit))
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        // Sliding Drawer Card
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(Color(0xFF242424), Color(0xFF161616))
                        } else {
                            listOf(Color(0xFFFFFFFF), Color(0xFFEEEEEE))
                        }
                    )
                )
                .clickable(enabled = false) {} // Prevent click propagation
                .padding(top = 24.dp, bottom = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Menu Items List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(menuItems) { item ->
                        val title = item.titleRes?.let { stringResource(it) } ?: item.titleString ?: ""
                        Surface(
                            onClick = {
                                activity.handleXServerMenuAction(item.id)
                                if (item.id == R.id.main_menu_pause) {
                                    isPaused = !isPaused
                                }
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(item.iconRes),
                                    contentDescription = title,
                                    modifier = Modifier.size(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
