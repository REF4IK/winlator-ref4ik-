package com.winlator.cmod.ui.screens

import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.viewinterop.AndroidView
import com.winlator.cmod.R
import java.io.File

/**
 * Экран подгонки обоев: зум щипком и перемещение пальцем (как в галерее).
 * Apply сохраняет подгонку для ТЕКУЩЕЙ ориентации (портрет/ландшафт)
 * и не закрывает экран — можно повернуть телефон и настроить другой режим.
 */
@Composable
fun WallpaperAdjustScreen(
    path: String,
    initialScale: Float = 1f,
    initialOffsetX: Float = 0f,
    initialOffsetY: Float = 0f,
    onApply: (scale: Float, offsetRatioX: Float, offsetRatioY: Float) -> Unit,
    onCancel: () -> Unit,
) {
    var scale by remember { mutableStateOf(initialScale) }
    var offsetX by remember { mutableStateOf(initialOffsetX) }
    var offsetY by remember { mutableStateOf(initialOffsetY) }
    var viewSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    BackHandler(onBack = onCancel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.5f, 4f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            },
    ) {
        // Контент: видео / гиф / картинка
        val type = wallpaperPreviewType(path)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .onSizeChanged { viewSize = it.toSize() },
        ) {
            when (type) {
                "video" -> WallpaperPreviewVideo(path)
                "gif" -> coil.compose.AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                else -> coil.compose.AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        // Подсказка: текущий режим (вертикальный/горизонтальный)
        Text(
            text = stringResource(
                R.string.wallpaper_adjust_hint,
                stringResource(if (isLandscape) R.string.wallpaper_adjust_landscape else R.string.wallpaper_adjust_portrait),
            ),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        )

        // Кнопки
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel), tint = Color.White)
            }
            FilledIconButton(
                onClick = {
                    val w = viewSize.width
                    val h = viewSize.height
                    val ratioX = if (w > 0) offsetX / w else 0f
                    val ratioY = if (h > 0) offsetY / h else 0f
                    onApply(scale, ratioX, ratioY)
                },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF4FC3F7), contentColor = Color(0xFF0B0D12)),
            ) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.wallpaper_adjust_apply), modifier = Modifier.size(30.dp))
            }
            IconButton(
                onClick = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                modifier = Modifier.size(52.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.wallpaper_adjust_reset), tint = Color.White)
            }
        }
    }
}

@Composable
private fun WallpaperPreviewVideo(path: String) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer?.release() } catch (_: Exception) {}
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                        val mp = MediaPlayer()
                        try {
                            mp.setSurface(Surface(surface))
                            mp.setDataSource(path)
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            val applyFit: (Int, Int) -> Unit = { vw, vh ->
                                if (vw > 0 && vh > 0 && width > 0 && height > 0) {
                                    val s = minOf(width.toFloat() / vw, height.toFloat() / vh)
                                    val dx = (width - vw * s) / 2f
                                    val dy = (height - vh * s) / 2f
                                    setTransform(android.graphics.Matrix().apply {
                                        setScale(s, s)
                                        postTranslate(dx, dy)
                                    })
                                }
                            }
                            mp.setOnVideoSizeChangedListener { _, vw, vh -> applyFit(vw, vh) }
                            mp.setOnPreparedListener {
                                applyFit(mp.videoWidth, mp.videoHeight)
                                it.start()
                            }
                            mp.setOnErrorListener { _, _, _ -> true }
                            mp.prepareAsync()
                            mediaPlayer = mp
                        } catch (_: Exception) {
                            try { mp.release() } catch (_: Exception) {}
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                }
            }
        }
    )
}

private fun wallpaperPreviewType(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "mp4", "webm", "mkv", "mov" -> "video"
        "gif", "webp" -> "gif"
        else -> "image"
    }
}
