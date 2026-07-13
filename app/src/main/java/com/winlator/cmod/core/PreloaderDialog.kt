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
import coil.compose.AsyncImage

class PreloaderDialog(private val activity: Activity) {
    private var dialog: Dialog? = null

    private var isVisible by mutableStateOf(false)
    private var titleText by mutableStateOf<CharSequence?>(null)
    private var subtitleText by mutableStateOf<CharSequence?>(null)
    private var stageText by mutableStateOf<CharSequence?>(null)
    private var coverArtBitmap by mutableStateOf<Bitmap?>(null)
    private var _steamAppId by mutableStateOf<String?>(null)

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
                .background(Color(0xFF0F1016))
        ) {
            val appId = _steamAppId?.toIntOrNull()

            // Full-screen background: Steam Hero Image or Local Cover Art
            if (appId != null && appId > 0) {
                val heroUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/library_hero.jpg"
                val capsuleUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/capsule_616x353.jpg"
                var backgroundModel by remember(appId) { mutableStateOf<Any>(heroUrl) }

                AsyncImage(
                    model = backgroundModel,
                    onError = {
                        if (backgroundModel == heroUrl) {
                            backgroundModel = capsuleUrl
                        }
                    },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                coverArtBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Full-screen gradient overlay (transparent top → dark bottom)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.05f),
                                Color.Black.copy(alpha = 0.25f),
                                Color.Black.copy(alpha = 0.65f),
                                Color(0xDD0F1016)
                            )
                        )
                    )
            )

            // Game logo overlay (if Steam App ID is present)
            if (appId != null && appId > 0) {
                val logoUrl = "https://shared.fastly.steamstatic.com/store_item_assets/steam/apps/$appId/logo.png"
                AsyncImage(
                    model = logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .height(72.dp)
                        .fillMaxWidth(0.5f)
                        .align(Alignment.TopStart)
                        .padding(start = 24.dp, top = 24.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // Content Column (aligned to BottomStart for that premium Steam Deck overlay look)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Bottom
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.95f, animationSpec = tween(800, easing = EaseOutCubic))
                ) {
                    Column(
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val displayTitle = titleText?.toString() ?: activity.getString(R.string.app_name)
                        val displaySubtitle = subtitleText?.toString()

                        // Only show title if we don't have a Steam logo (to avoid duplicate title display)
                        if (appId == null || appId <= 0) {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.9f),
                                        offset = Offset(2f, 4f),
                                        blurRadius = 8f
                                    )
                                ),
                                fontSize = 36.sp,
                                color = Color.White,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        if (!displaySubtitle.isNullOrEmpty()) {
                            Text(
                                text = displaySubtitle,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.9f),
                                        offset = Offset(1f, 2f),
                                        blurRadius = 4f
                                    )
                                ),
                                fontSize = 18.sp,
                                color = Color(0xFFD3E0F6),
                                letterSpacing = 0.3.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        // Loading bar
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
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.9f),
                                            offset = Offset(1f, 2f),
                                            blurRadius = 4f
                                        )
                                    ),
                                    color = Color.White.copy(alpha = 0.6f),
                                    letterSpacing = 0.5.sp
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
    fun setSteamAppId(steamAppId: String?) {
        this._steamAppId = steamAppId
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
