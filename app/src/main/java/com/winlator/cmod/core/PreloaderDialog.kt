package com.winlator.cmod.core

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import kotlinx.coroutines.delay

class PreloaderDialog(private val activity: Activity) {
    private var dialog: Dialog? = null

    // Compose states
    private var isVisible by mutableStateOf(false)
    private var titleText by mutableStateOf<CharSequence?>(null)
    private var subtitleText by mutableStateOf<CharSequence?>(null)
    private var stageText by mutableStateOf<CharSequence?>(null)
    private var coverArtBitmap by mutableStateOf<Bitmap?>(null)

    private fun create() {
        if (dialog != null) return
        dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
            requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            setCancelable(false)
            setCanceledOnTouchOutside(false)

            val composeView = ComposeView(activity).apply {
                setContent {
                    val isDark = com.winlator.cmod.contentdialog.ContentDialog.shouldUseDarkDialog(activity)
                    com.winlator.cmod.ui.theme.WinlatorTheme(darkTheme = isDark) {
                        PreloaderContent()
                    }
                }
            }
            setContentView(composeView)

            window?.let { win ->
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }

            // Set ViewTree owners via reflection
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

                window?.decorView?.let { setOwners(it) }
                setOwners(composeView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Composable
    private fun PreloaderContent() {
        var dots by remember { mutableStateOf("") }
        LaunchedEffect(stageText) {
            var count = 0
            while (true) {
                dots = ".".repeat(count)
                count = (count + 1) % 4
                delay(400)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF16151A), Color(0xFF0C0B0E))
                    )
                )
        ) {
            // Blurred cover art background
            coverArtBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp)
                        .alpha(0.35f)
                )
            }

            // Centered layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val displayTitle = titleText?.toString() ?: activity.getString(R.string.app_name)
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                subtitleText?.toString()?.let { subtitle ->
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                LinearProgressIndicator(
                    modifier = Modifier.width(240.dp).height(3.dp),
                    color = Color(0xFF26C6BE),
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                val displayStage = stageText?.toString() ?: ""
                if (displayStage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stripTrailingDots(displayStage) + dots,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }

    @Synchronized
    fun show(textResId: Int) {
        if (isShowing()) return
        close()
        if (dialog == null) create()
        stageText = activity.getString(textResId)
        dialog?.show()
        isVisible = true
    }

    fun showOnUiThread(textResId: Int) {
        activity.runOnUiThread { show(textResId) }
    }

    @Synchronized
    fun setTitle(title: CharSequence?) {
        this.titleText = title
    }

    @Synchronized
    fun setSubtitle(subtitle: CharSequence?) {
        this.subtitleText = subtitle
    }

    fun setSubtitleOnUiThread(subtitle: CharSequence?) {
        activity.runOnUiThread { setSubtitle(subtitle) }
    }

    @Synchronized
    fun setStage(stage: CharSequence?) {
        this.stageText = stage
    }

    fun setStageOnUiThread(stage: CharSequence?) {
        activity.runOnUiThread { setStage(stage) }
    }

    @Synchronized
    fun updateText(text: String?) {
        setStage(text)
    }

    fun updateTextOnUiThread(text: String?) {
        activity.runOnUiThread { updateText(text) }
    }

    @Synchronized
    fun setCoverArt(bitmap: Bitmap?) {
        this.coverArtBitmap = bitmap
    }

    fun setCoverArtOnUiThread(bitmap: Bitmap?) {
        activity.runOnUiThread { setCoverArt(bitmap) }
    }

    @Synchronized
    fun close() {
        if (dialog == null) return
        val d = dialog
        dialog = null
        isVisible = false
        try {
            d?.dismiss()
        } catch (ignored: Exception) {
        }
    }

    fun closeOnUiThread() {
        activity.runOnUiThread { close() }
    }

    fun isShowing(): Boolean {
        return dialog != null && dialog!!.isShowing
    }

    private fun stripTrailingDots(s: String?): String {
        if (s == null) return ""
        var end = s.length
        while (end > 0) {
            val c = s[end - 1]
            if (c == '.' || c == '\u2026' || c == ' ') end--
            else break
        }
        return s.substring(0, end)
    }
}
