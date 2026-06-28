package com.winlator.cmod.ui

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.winlator.cmod.ui.screens.ProcessUiInfo
import com.winlator.cmod.ui.screens.TaskManagerScreen
import com.winlator.cmod.ui.screens.XServerMenuDialog

/**
 * Bridge to show Compose dialogs from Java activities (e.g. XServerDisplayActivity).
 */
object ComposeBridge {

    private var overlayView: ComposeView? = null

    fun showXServerMenu(activity: Activity, isPaused: Boolean = false) {
        showOverlay(activity) {
            XServerMenuDialog(
                isPaused = isPaused,
                onDismiss = { hideOverlay(activity) },
                onItemSelected = { itemId ->
                    hideOverlay(activity)
                    // Route back to Java activity via reflection-like approach
                    // The activity should handle the menu item
                }
            )
        }
    }

    fun showTaskManager(activity: Activity) {
        showOverlay(activity) {
            TaskManagerScreen(
                processes = emptyList(),
                onBack = { hideOverlay(activity) },
            )
        }
    }

    private fun showOverlay(activity: Activity, content: @Composable () -> Unit) {
        hideOverlay(activity)
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setContent {
                content()
            }
        }
        overlayView = composeView
        (activity.window.decorView as FrameLayout).addView(composeView)
    }

    private fun hideOverlay(activity: Activity) {
        overlayView?.let {
            (activity.window.decorView as FrameLayout).removeView(it)
        }
        overlayView = null
    }
}
