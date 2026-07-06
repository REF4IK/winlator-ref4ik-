package com.winlator.cmod.widget

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R
import com.winlator.cmod.container.Container
import com.winlator.cmod.core.GPUInformation
import com.winlator.cmod.core.SensorReader
import com.winlator.cmod.core.StringUtils
import java.util.Locale

class FrameRating @JvmOverloads constructor(
    context: Context,
    private val container: Container? = null,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), Runnable {

    companion object {
        private const val TAG = "FrameRating"
        private const val MAX_FRAME_TIME_MS = 50.0f
        private const val TARGET_60_FPS_MS = 16.67f
        private const val TARGET_30_FPS_MS = 33.33f
        private const val DEFAULT_SAMPLE_COUNT = 40
    }

    private val config = FpsCounterConfig(context)
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?
    private val totalRAM = getTotalRAM()
    private val sensorReader = SensorReader()
    private var lastFrameTimestampNs: Long = 0

    private var lastTime: Long = 0
    private var frameCount = 0
    private var lastFPS = 0f
    private var renderer: String? = null
    private var gpuName: String? = null
    private var batteryTemperature = -1.0f
    private var batteryVoltage = -1.0f
    private var batteryCurrent = -1.0f
    private var batteryReceiverRegistered = false

    // State variables for Compose
    private var fpsState by mutableStateOf("0")
    private var rendererState by mutableStateOf("")
    private var gpuState by mutableStateOf("")
    private var gpuLoadState by mutableStateOf("")
    private var gpuTempState by mutableStateOf("")
    private var frameTimeState by mutableStateOf("--")
    private var ramState by mutableStateOf("")
    private var cpuLoadState by mutableStateOf("")
    private var cpuTempState by mutableStateOf("")
    private var batteryTempState by mutableStateOf("")
    private var batteryVoltageState by mutableStateOf("")

    // Graph samples state
    private val samples = mutableStateListOf<Float>().apply {
        repeat(DEFAULT_SAMPLE_COUNT) { add(TARGET_60_FPS_MS) }
    }
    private var nextIndex = 0

    // Configuration states
    private var isHorizontalLayout by mutableStateOf(false)
    private var backgroundOpacity by mutableStateOf(153)

    // Module visibility states
    private var showFps by mutableStateOf(true)
    private var showRenderer by mutableStateOf(true)
    private var showGpu by mutableStateOf(true)
    private var showGpuLoad by mutableStateOf(true)
    private var showGpuTemp by mutableStateOf(true)
    private var showFrameTimeGraph by mutableStateOf(true)
    private var showRam by mutableStateOf(true)
    private var showCpuLoad by mutableStateOf(true)
    private var showCpuTemp by mutableStateOf(true)
    private var showBatteryTemp by mutableStateOf(true)
    private var showBatteryVoltage by mutableStateOf(true)

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val temperature = intent.getIntExtra("temperature", -1)
            batteryTemperature = if (temperature != -1) temperature / 10.0f else -1.0f
            val voltage = intent.getIntExtra("voltage", -1)
            batteryVoltage = if (voltage != -1) voltage / 1000.0f else -1.0f
            refreshBatteryCurrent()
        }
    }

    init {
        // Compose view integration
        val composeView = ComposeView(context).apply {
            setContent {
                FrameRatingContent()
            }
        }
        addView(composeView)

        updateModuleVisibility()
        updateOrientation()
        updateScaleAndTextSize()
        setupDragging(composeView)
    }

    // Compose Content
    @Composable
    private fun FrameRatingContent() {
        val bgColor = ComposeColor(0f, 0f, 0f, backgroundOpacity / 255f)
        val strokeColor = ComposeColor(1f, 1f, 1f, 0.2f)

        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(1.dp, strokeColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        ) {
            if (isHorizontalLayout) {
                // Horizontal Layout
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RenderModules()
                }
            } else {
                // Vertical Layout
                Column(
                    modifier = Modifier.wrapContentSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RenderModules()
                }
            }
        }
    }

    @Composable
    private fun RenderModules() {
        if (showGpu && gpuState.isNotEmpty()) {
            HudItem(label = "GPU", value = gpuState, color = ComposeColor(0xFF19D07E))
        }
        if (showGpuLoad && gpuLoadState.isNotEmpty()) {
            HudItem(label = "GPU", value = gpuLoadState, color = ComposeColor(0xFF19D07E))
        }
        if (showRenderer && rendererState.isNotEmpty()) {
            HudItem(label = "API", value = rendererState, color = ComposeColor(0xFFF05A9D))
        }
        if (showGpuTemp && gpuTempState.isNotEmpty()) {
            HudItem(label = "GPU", value = gpuTempState, color = ComposeColor(0xFF19D07E))
        }
        if (showRam && ramState.isNotEmpty()) {
            HudItem(label = "RAM", value = ramState, color = ComposeColor(0xFFE556D8))
        }
        if (showCpuLoad && cpuLoadState.isNotEmpty()) {
            HudItem(label = "CPU", value = cpuLoadState, color = ComposeColor(0xFF42B6FF))
        }
        if (showCpuTemp && cpuTempState.isNotEmpty()) {
            HudItem(label = "CPU", value = cpuTempState, color = ComposeColor(0xFF42B6FF))
        }
        if (showBatteryTemp && batteryTempState.isNotEmpty()) {
            HudItem(label = "BAT", value = batteryTempState, color = ComposeColor(0xFFA6E84B))
        }
        if (showBatteryVoltage && batteryVoltageState.isNotEmpty()) {
            HudItem(label = "PWR", value = batteryVoltageState, color = ComposeColor(0xFFF3DE47))
        }
        if (showFps) {
            HudItem(label = "FPS", value = fpsState, color = ComposeColor(0xFFFF5F73))
        }
        if (showFrameTimeGraph) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = "FT",
                    color = ComposeColor(0xFFFF5F73),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                FrameTimeGraphCompose()
                Text(
                    text = frameTimeState,
                    color = ComposeColor(0xFFF8F8F8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    @Composable
    private fun HudItem(label: String, value: String, color: ComposeColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = value,
                color = ComposeColor(0xFFF2F2F2),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    @Composable
    private fun FrameTimeGraphCompose() {
        Canvas(modifier = Modifier.size(width = 58.dp, height = 16.dp)) {
            val width = size.width
            val height = size.height

            // Draw guides
            val y60 = height - (TARGET_60_FPS_MS / MAX_FRAME_TIME_MS * height)
            drawLine(
                color = ComposeColor(0x55FFFFFF),
                start = Offset(0f, y60),
                end = Offset(width, y60),
                strokeWidth = 1f
            )

            val y30 = height - (TARGET_30_FPS_MS / MAX_FRAME_TIME_MS * height)
            drawLine(
                color = ComposeColor(0x44FF5252),
                start = Offset(0f, y30),
                end = Offset(width, y30),
                strokeWidth = 1f
            )

            val pointsCount = samples.size
            if (pointsCount > 1) {
                val spacing = width / (pointsCount - 1)
                val linePath = ComposePath()
                val fillPath = ComposePath()

                for (i in 0 until pointsCount) {
                    val start = nextIndex
                    val sample = samples[(start + i) % pointsCount]
                    val x = i * spacing
                    val y = height - (sample / MAX_FRAME_TIME_MS * height).coerceIn(0f, height)

                    if (i == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(width, height)
                fillPath.close()

                drawPath(fillPath, color = ComposeColor(0x33FFB300))
                drawPath(linePath, color = ComposeColor(0xFFFFB300), style = Stroke(width = 1.8.dp.toPx()))
            }
        }
    }

    private fun getTotalRAM(): String {
        if (activityManager == null) return "N/A"
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return StringUtils.formatBytes(memoryInfo.totalMem)
    }

    private fun getUsedRAM(): String {
        if (activityManager == null) return "N/A"
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val usedMem = memoryInfo.totalMem - memoryInfo.availMem
        return StringUtils.formatBytes(usedMem, false)
    }

    fun reset() {
        Log.d(TAG, "Resetting FrameRating")
        renderer = null
        gpuName = null
        lastFPS = 0f
        frameCount = 0
        lastTime = 0
        lastFrameTimestampNs = 0
        samples.clear()
        repeat(DEFAULT_SAMPLE_COUNT) { samples.add(TARGET_60_FPS_MS) }
        nextIndex = 0
    }

    fun setRenderer(renderer: String?) {
        this.renderer = renderer
    }

    fun setGpuName(gpuName: String?) {
        this.gpuName = gpuName
    }

    fun updateOrientation() {
        isHorizontalLayout = config.isHorizontalLayout()
        val flexWidth = ViewGroup.LayoutParams.WRAP_CONTENT
        val clp = layoutParams
        if (clp != null) {
            clp.width = flexWidth
        }
        requestLayout()
    }

    fun updateModuleVisibility() {
        showFps = config.isModuleVisible(FpsCounterConfig.Module.FPS)
        showRenderer = config.isModuleVisible(FpsCounterConfig.Module.RENDERER)
        showGpu = config.isModuleVisible(FpsCounterConfig.Module.GPU)
        showGpuLoad = config.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD)
        showGpuTemp = config.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP)
        showFrameTimeGraph = config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)
        showRam = config.isModuleVisible(FpsCounterConfig.Module.RAM)
        showCpuLoad = config.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD)
        showCpuTemp = config.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP)
        showBatteryTemp = config.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP)
        showBatteryVoltage = config.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE)
        updateBackgroundOpacity()
        updateScaleAndTextSize()
    }

    fun updateBackgroundOpacity() {
        backgroundOpacity = config.backgroundOpacity
    }

    fun updateScaleAndTextSize() {
        val scale = config.counterScale
        val scaleFactor = scale / 100.0f
        pivotX = 0f
        pivotY = 0f
        scaleX = scaleFactor
        scaleY = scaleFactor

        post { clampToParentBounds() }
    }

    private fun clampToParentBounds() {
        val parentView = parent as? View ?: return
        val parentWidth = parentView.width
        val parentHeight = parentView.height
        val scaledWidth = Math.ceil((width * scaleX).toDouble()).toInt()
        val scaledHeight = Math.ceil((height * scaleY).toDouble()).toInt()

        if (parentWidth <= 0 || parentHeight <= 0 || scaledWidth <= 0 || scaledHeight <= 0) return

        val maxX = Math.max(0f, (parentWidth - scaledWidth).toFloat())
        val maxY = Math.max(0f, (parentHeight - scaledHeight).toFloat())
        x = Math.max(0f, Math.min(x, maxX))
        y = Math.max(0f, Math.min(y, maxY))
    }

    private fun setupDragging(targetView: View) {
        val dX = FloatArray(1)
        val dY = FloatArray(1)
        val dragging = BooleanArray(1)

        targetView.setOnTouchListener { _, event ->
            val action = event.actionMasked
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    dragging[0] = true
                    dX[0] = x - event.rawX
                    dY[0] = y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging[0]) return@setOnTouchListener false
                    var newX = event.rawX + dX[0]
                    var newY = event.rawY + dY[0]
                    val parentView = parent as? View
                    if (parentView != null) {
                        val parentWidth = parentView.width
                        val parentHeight = parentView.height
                        val scaledWidth = Math.ceil((width * scaleX).toDouble()).toInt()
                        val scaledHeight = Math.ceil((height * scaleY).toDouble()).toInt()
                        if (parentWidth > 0 && parentHeight > 0 && scaledWidth > 0 && scaledHeight > 0) {
                            val maxX = Math.max(0f, (parentWidth - scaledWidth).toFloat())
                            val maxY = Math.max(0f, (parentHeight - scaledHeight).toFloat())
                            newX = Math.max(0f, Math.min(newX, maxX))
                            newY = Math.max(0f, Math.min(newY, maxY))
                        }
                    }
                    x = newX
                    y = newY
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging[0] = false
                    true
                }
                else -> false
            }
        }
    }

    fun getConfig(): FpsCounterConfig {
        return config
    }

    fun update() {
        val profiling = ProfilingSession.getInstance().isActive()
        val overlayVisible = config.isEnabled && visibility == View.VISIBLE
        if (!overlayVisible && !profiling) return

        val frameTimestampNs = SystemClock.elapsedRealtimeNanos()
        if (lastFrameTimestampNs != 0L && overlayVisible && config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)) {
            val frameTimeMs = (frameTimestampNs - lastFrameTimestampNs) / 1000000.0f
            val clamped = Math.max(0f, Math.min(MAX_FRAME_TIME_MS, frameTimeMs))
            samples[nextIndex] = clamped
            nextIndex = (nextIndex + 1) % samples.size
        }
        lastFrameTimestampNs = frameTimestampNs

        if (lastTime == 0L) {
            lastTime = SystemClock.elapsedRealtime()
        }
        val time = SystemClock.elapsedRealtime()
        if (time >= lastTime + 500L) {
            lastFPS = (frameCount * 1000).toFloat() / (time - lastTime)
            if (profiling) {
                ProfilingSession.getInstance().addFpsSample(lastFPS)
            }
            post(this)
            lastTime = time
            frameCount = 0
        }
        frameCount++
    }

    override fun run() {
        val profiling = ProfilingSession.getInstance().isActive()
        val overlayVisible = config.isEnabled && visibility == View.VISIBLE
        if (!overlayVisible && !profiling) return

        if (profiling) {
            collectProfilingSensorSample()
        }
        if (!overlayVisible) return

        if (config.isModuleVisible(FpsCounterConfig.Module.FPS)) {
            fpsState = String.format(Locale.ENGLISH, "%.1f", lastFPS)
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.RENDERER)) {
            rendererState = renderer ?: "OpenGL"
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU)) {
            gpuState = gpuName ?: GPUInformation.getRenderer()
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD)) {
            gpuLoadState = sensorReader.gpuLoad
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP)) {
            gpuTempState = sensorReader.gpuTemperature
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)) {
            val frameTimeMs = if (lastFPS > 0f) 1000.0f / lastFPS else 0f
            frameTimeState = if (frameTimeMs > 0f) String.format(Locale.ENGLISH, "%.1fms", frameTimeMs) else "--"
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.RAM)) {
            ramState = "${getUsedRAM()} / $totalRAM"
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD)) {
            cpuLoadState = sensorReader.cpuLoad
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP)) {
            cpuTempState = sensorReader.cpuTemperature
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP)) {
            batteryTempState = if (batteryTemperature != -1.0f) String.format(Locale.ENGLISH, "%.1fC", batteryTemperature) else "N/A"
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE)) {
            refreshBatteryCurrent()
            val powerWatts = getBatteryPowerWatts()
            batteryVoltageState = if (powerWatts != -1.0f) String.format(Locale.ENGLISH, "%.2fW", powerWatts) else "N/A"
        }
    }

    private fun collectProfilingSensorSample() {
        ProfilingSession.getInstance().collectSample(context)
    }

    private fun refreshBatteryCurrent() {
        if (batteryManager == null) {
            batteryCurrent = -1.0f
            return
        }
        batteryCurrent = sensorReader.readCurrentAmpsFromSysfs(batteryManager)
    }

    private fun getBatteryPowerWatts(): Float {
        return sensorReader.getBatteryPowerWatts(batteryManager, batteryVoltage, batteryCurrent)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!batteryReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
            batteryReceiverRegistered = true
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (batteryReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (ignored: IllegalArgumentException) {
            }
            batteryReceiverRegistered = false
        }
    }
}
