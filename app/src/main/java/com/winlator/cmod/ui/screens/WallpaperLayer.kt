package com.winlator.cmod.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Единый слой обоев интерфейса: картинка (с блюром) / GIF / видео.
 * Используется как фон приложения и как подложка вложенных экранов,
 * чтобы закрывать предыдущее меню, показывая при этом обои.
 */

// Подгонка обоев хранится отдельно для портретного и ландшафтного режима,
// чтобы при повороте телефона картинка не сбивалась
fun wallpaperScaleKey(landscape: Boolean) =
    if (landscape) "ui_wallpaper_scale_landscape" else "ui_wallpaper_scale_portrait"

fun wallpaperOffsetXKey(landscape: Boolean) =
    if (landscape) "ui_wallpaper_offset_x_landscape" else "ui_wallpaper_offset_x_portrait"

fun wallpaperOffsetYKey(landscape: Boolean) =
    if (landscape) "ui_wallpaper_offset_y_landscape" else "ui_wallpaper_offset_y_portrait"

fun wallpaperResetAdjustKeys(prefs: android.content.SharedPreferences) {
    prefs.edit()
        .putFloat(wallpaperScaleKey(false), 1f)
        .putFloat(wallpaperOffsetXKey(false), 0f)
        .putFloat(wallpaperOffsetYKey(false), 0f)
        .putFloat(wallpaperScaleKey(true), 1f)
        .putFloat(wallpaperOffsetXKey(true), 0f)
        .putFloat(wallpaperOffsetYKey(true), 0f)
        .apply()
}

@Composable
fun WallpaperLayer(
    path: String,
    blur: Int,
    darken: Int,
    scale: Float,
    offsetRatioX: Float,
    offsetRatioY: Float,
    modifier: Modifier = Modifier,
    allowVideo: Boolean = true,
) {
    val type = remember(path) { wallpaperTypeOf(path) }
    var bitmap by remember(path, blur) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path, blur) {
        bitmap = withContext(Dispatchers.IO) {
            if (path.isNotEmpty() && type == "image") loadWallpaperBitmap(path, blur) else null
        }
    }

    // В горизонтальном режиме обои не показываем — обычный фон
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = modifier) {
        if (isLandscape) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            )
            return@Box
        }
        when (type) {
            "gif" -> {
                // Фон под гифкой, пока она грузится (иначе мигает белым)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                )
                coil.compose.AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetRatioX * size.width
                            translationY = offsetRatioY * size.height
                        },
                    contentScale = ContentScale.Crop,
                )
                WallpaperDarken(darken = darken)
            }
            "video" -> {
                if (allowVideo) {
                    // Фон под видео, пока плеер не готов (иначе мигает белым)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                    )
                    AnimatedVideoWallpaper(
                        path = path,
                        scale = scale,
                        offsetRatioX = offsetRatioX,
                        offsetRatioY = offsetRatioY,
                        modifier = Modifier.fillMaxSize(),
                    )
                    WallpaperDarken(darken = darken)
                } else {
                    // В подложках вложенных экранов видео не запускаем —
                    // иначе два плеера одновременно и лаги
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                    )
                }
            }
            else -> {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetRatioX * size.width
                                translationY = offsetRatioY * size.height
                            },
                        contentScale = ContentScale.Crop,
                    )
                    WallpaperDarken(darken = darken)
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperDarken(darken: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = (darken.coerceIn(0, 100) / 100f)))
    )
}

private fun wallpaperTypeOf(path: String): String {
    if (path.isBlank()) return ""
    val ext = path.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "gif", "webp" -> "gif"
        "mp4", "webm", "mkv", "mov" -> "video"
        else -> "image"
    }
}

@Composable
private fun AnimatedVideoWallpaper(
    path: String,
    scale: Float,
    offsetRatioX: Float,
    offsetRatioY: Float,
    modifier: Modifier = Modifier,
) {
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var surfaceView by remember { mutableStateOf<android.view.SurfaceView?>(null) }
    var videoW by remember { mutableIntStateOf(0) }
    var videoH by remember { mutableIntStateOf(0) }
    var viewW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }

    // SurfaceView рисуется аппаратно отдельным слоем (не через композицию
    // Compose) — видео идёт плавно. Подгонка задаётся размером/позицией вью.
    LaunchedEffect(scale, offsetRatioX, offsetRatioY, videoW, videoH, viewW, viewH) {
        val sv = surfaceView ?: return@LaunchedEffect
        if (videoW > 0 && videoH > 0 && viewW > 0 && viewH > 0) {
            val fit = minOf(viewW.toFloat() / videoW, viewH.toFloat() / videoH)
            val total = fit * scale.coerceIn(0.5f, 4f)
            val w = (videoW * total).toInt()
            val h = (videoH * total).toInt()
            val left = ((viewW - w) / 2f + offsetRatioX * viewW).toInt()
            val top = ((viewH - h) / 2f + offsetRatioY * viewH).toInt()
            sv.layoutParams = android.widget.FrameLayout.LayoutParams(
                w, h, android.view.Gravity.TOP or android.view.Gravity.START,
            ).apply {
                leftMargin = left
                topMargin = top
            }
            // Первый кадр уже с правильным размером/позицией
            try {
                val mp = mediaPlayer
                if (mp != null && !mp.isPlaying) mp.start()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { mediaPlayer?.release() } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mediaPlayer) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    try { mediaPlayer?.start() } catch (_: Exception) {}
                }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    try { mediaPlayer?.pause() } catch (_: Exception) {}
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.view.SurfaceView(ctx).apply {
                surfaceView = this
                // Поверхность позади всей view-иерархии (под Compose)
                setZOrderOnTop(false)
                setZOrderMediaOverlay(false)
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        val mp = MediaPlayer()
                        try {
                            mp.setDisplay(holder)
                            mp.setDataSource(path)
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            mp.setOnVideoSizeChangedListener { _, vw, vh ->
                                videoW = vw
                                videoH = vh
                            }
                            mp.setOnPreparedListener {
                                videoW = mp.videoWidth
                                videoH = mp.videoHeight
                                // start вызывается после применения подгонки
                            }
                            mp.setOnErrorListener { _, _, _ -> true }
                            mp.prepareAsync()
                            mediaPlayer = mp
                        } catch (_: Exception) {
                            try { mp.release() } catch (_: Exception) {}
                        }
                    }
                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                        viewW = width
                        viewH = height
                    }
                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {}
                })
            }
        }
    )
}

private fun loadWallpaperBitmap(path: String, blurRadius: Int): Bitmap? {
    return try {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxDim / (sample * 2) >= 1024) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val source = BitmapFactory.decodeFile(path, options) ?: return null

        val radius = blurRadius.coerceIn(0, 100)
        if (radius <= 0) return source

        val small = Bitmap.createScaledBitmap(source, 1024, (1024.0 * source.height / source.width).toInt(), true)
        if (small !== source) source.recycle()
        val blurred = stackBlur(small, radius)
        if (blurred !== small) small.recycle()
        blurred
    } catch (e: Exception) {
        android.util.Log.e("WallpaperLayer", "Wallpaper load failed", e)
        null
    }
}

private fun stackBlur(source: Bitmap, radiusIn: Int): Bitmap {
    val radius = radiusIn.coerceIn(1, 100)
    val width = source.width
    val height = source.height
    var pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    val r = if (radius < 1) 1 else if (radius > 100) 100 else radius
    for (i in 0 until 3) {
        pixels = boxBlur1D(pixels, width, height, r, horizontal = true)
        pixels = boxBlur1D(pixels, width, height, r, horizontal = false)
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlur1D(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
    val output = IntArray(pixels.size)
    val length = if (horizontal) width else height
    val other = if (horizontal) height else width
    for (line in 0 until other) {
        var accR = 0; var accG = 0; var accB = 0
        var count = 0
        for (i in -radius until length + radius) {
            if (i + radius < length) {
                val idx = if (horizontal) (line * width + (i + radius).coerceIn(0, length - 1))
                else ((i + radius).coerceIn(0, length - 1) * width + line)
                val p = pixels[idx]
                accR += (p shr 16) and 0xFF
                accG += (p shr 8) and 0xFF
                accB += p and 0xFF
                count++
            }
            if (i - radius - 1 >= 0) {
                val idx = if (horizontal) (line * width + (i - radius - 1).coerceIn(0, length - 1))
                else ((i - radius - 1).coerceIn(0, length - 1) * width + line)
                val p = pixels[idx]
                accR -= (p shr 16) and 0xFF
                accG -= (p shr 8) and 0xFF
                accB -= p and 0xFF
                count--
            }
            if (i >= 0 && i < length && count > 0) {
                val outIdx = if (horizontal) (line * width + i) else (i * width + line)
                output[outIdx] = (0xFF shl 24) or
                    (((accR / count).coerceIn(0, 255)) shl 16) or
                    (((accG / count).coerceIn(0, 255)) shl 8) or
                    ((accB / count).coerceIn(0, 255))
            }
        }
    }
    return output
}
