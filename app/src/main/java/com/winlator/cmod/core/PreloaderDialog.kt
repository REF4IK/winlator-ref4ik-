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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.winlator.cmod.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class PreloaderDialog(private val activity: Activity) {
    private var dialog: Dialog? = null

    private var isVisible by mutableStateOf(false)
    private var titleText by mutableStateOf<CharSequence?>(null)
    private var subtitleText by mutableStateOf<CharSequence?>(null)
    private var stageText by mutableStateOf<CharSequence?>(null)
    private var coverArtBitmap by mutableStateOf<Bitmap?>(null)

    private var exitTriggered by mutableStateOf(false)
    private var onFadeOutComplete: (() -> Unit)? = null

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
                        SteamDeckBootScreen()
                    }
                }
            }
            setContentView(composeView)

            window?.let { win ->
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            }

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
    private fun SteamDeckBootScreen() {
        var visible by remember { mutableStateOf(false) }
        var stageAlpha by remember { mutableStateOf(1f) }
        var prevStage by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            visible = true
        }

        LaunchedEffect(stageText) {
            stageText?.let { prevStage = it.toString() }
            stageAlpha = 0f
            delay(50)
            stageAlpha = 1f
        }

        val infiniteTransition = rememberInfiniteTransition()
        val glowAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1117))
        ) {
            coverArtBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.15f)
                        .graphicsLayer { alpha = 0.15f + glowAlpha * 0.08f }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x00000000),
                                Color(0x80000000)
                            ),
                            radius = 1.2f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp, vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.92f, animationSpec = tween(800, easing = EaseOutCubic))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        coverArtBitmap?.let { bmp ->
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 400.dp)
                                    .aspectRatio(16f / 9f)
                                    .shadow(24.dp, RoundedCornerShape(12.dp), ambientColor = Color(0x4000A5FF), spotColor = Color(0x4000A5FF))
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1A1D24))
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0x00FFFFFF),
                                                    Color(0x00FFFFFF),
                                                    Color(0x80000000)
                                                )
                                            )
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                        }

                        val displayTitle = titleText?.toString() ?: activity.getString(R.string.app_name)
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Light,
                            color = Color.White,
                            letterSpacing = 2.sp,
                            textAlign = TextAlign.Center
                        )

                        subtitleText?.toString()?.let { subtitle ->
                            if (subtitle.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFA0A5B0),
                                    letterSpacing = 1.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(1500, delayMillis = 500))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingBar()

                        Spacer(modifier = Modifier.height(16.dp))

                        val displayStage = stageText?.toString() ?: ""
                        AnimatedContent(
                            targetState = displayStage,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                            }
                        ) { stage ->
                            if (stage.isNotEmpty()) {
                                Text(
                                    text = stage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B7280),
                                    letterSpacing = 0.5.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LoadingBar() {
        val infiniteTransition = rememberInfiniteTransition()
        val offset by infiniteTransition.animateFloat(
            initialValue = -200f,
            targetValue = 200f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Restart
            )
        )

        Box(
            modifier = Modifier
                .width(160.dp)
                .height(2.dp)
                .background(Color(0xFF1E2230))
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .width(60.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0x0000A5FF),
                                Color(0xFF00A5FF),
                                Color(0x0000A5FF)
                            )
                        )
                    )
            )
        }
    }

    @Synchronized
    fun show(textResId: Int) {
        if (isShowing()) return
        close()
        if (dialog == null) create()
        exitTriggered = false
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
        activity.runOnUiThread {
            exitTriggered = true
            close()
        }
    }

    fun isShowing(): Boolean {
        return dialog != null && dialog!!.isShowing
    }
}
