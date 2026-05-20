package com.winlator.cmod.widget;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.card.MaterialCardView;
import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.SensorReader;
import com.winlator.cmod.core.StringUtils;

import java.util.Locale;

public class FrameRating extends FrameLayout implements Runnable {
    private static final String TAG = "FrameRating";

    private final Context context;
    private final FpsCounterConfig config;
    private final ActivityManager activityManager;
    private final BatteryManager batteryManager;
    private final String totalRAM;
    private final SensorReader sensorReader = new SensorReader();
    private long lastFrameTimestampNs;

    private long lastTime;
    private int frameCount;
    private float lastFPS;
    private String renderer;
    private String gpuName;
    private float batteryTemperature = -1.0f;
    private float batteryVoltage = -1.0f;
    private float batteryCurrent = -1.0f;
    private boolean batteryReceiverRegistered;

    private LinearLayout rootLayout;
    private final FlowLayout contentLayout;
    private final MaterialCardView counterCard;
    private final LinearLayout layoutFPS;
    private final LinearLayout layoutRenderer;
    private final LinearLayout layoutGPU;
    private final LinearLayout layoutGPULoad;
    private final LinearLayout layoutGPUTemp;
    private final LinearLayout layoutFrameTimeGraph;
    private final LinearLayout layoutRAM;
    private final LinearLayout layoutCPULoad;
    private final LinearLayout layoutCPUTemp;
    private final LinearLayout layoutBatteryTemp;
    private final LinearLayout layoutBatteryVoltage;

    private final TextView tvFPS;
    private final TextView tvRenderer;
    private final TextView tvGPU;
    private final TextView tvGPULoad;
    private final TextView tvGPUTemp;
    private final TextView tvFrameTime;
    private final TextView tvRAM;
    private final TextView tvCPULoad;
    private final TextView tvCPUTemp;
    private final TextView tvBatteryTemp;
    private final TextView tvBatteryVoltage;

    private final TextView tvFPSLabel;
    private final TextView tvRendererLabel;
    private final TextView tvGPULabel;
    private final TextView tvGPULoadLabel;
    private final TextView tvGPUTempLabel;
    private final TextView tvFrameTimeLabel;
    private final TextView tvRAMLabel;
    private final TextView tvCPULoadLabel;
    private final TextView tvCPUTempLabel;
    private final TextView tvBatteryTempLabel;
    private final TextView tvBatteryVoltageLabel;
    private final FrameTimeGraphView frameTimeGraphView;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int temperature = intent.getIntExtra("temperature", -1);
            batteryTemperature = temperature != -1 ? temperature / 10.0f : -1.0f;
            int voltage = intent.getIntExtra("voltage", -1);
            batteryVoltage = voltage != -1 ? voltage / 1000.0f : -1.0f;
            refreshBatteryCurrent();
        }
    };

    public FrameRating(Context context, Container container) {
        this(context, container, null);
    }

    public FrameRating(Context context, Container container, AttributeSet attrs) {
        this(context, container, attrs, 0);
    }

    public FrameRating(Context context, Container container, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
        this.config = new FpsCounterConfig(context);
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        this.batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        this.totalRAM = getTotalRAM();

        View view = LayoutInflater.from(context).inflate(R.layout.frame_rating, this, false);

        tvFPS = view.findViewById(R.id.TVFPS);
        tvRenderer = view.findViewById(R.id.TVRenderer);
        tvGPU = view.findViewById(R.id.TVGPU);
        tvGPULoad = view.findViewById(R.id.TVGPULoad);
        tvGPUTemp = view.findViewById(R.id.TVGPUTemp);
        tvFrameTime = view.findViewById(R.id.TVFrameTime);
        tvRAM = view.findViewById(R.id.TVRAM);
        tvCPULoad = view.findViewById(R.id.TVCPULoad);
        tvCPUTemp = view.findViewById(R.id.TVCPUTemp);
        tvBatteryTemp = view.findViewById(R.id.TVBatteryTemp);
        tvBatteryVoltage = view.findViewById(R.id.TVBatteryVoltage);

        tvFPSLabel = view.findViewById(R.id.TVFPSLabel);
        tvRendererLabel = view.findViewById(R.id.TVRendererLabel);
        tvGPULabel = view.findViewById(R.id.TVGPULabel);
        tvGPULoadLabel = view.findViewById(R.id.TVGPULoadLabel);
        tvGPUTempLabel = view.findViewById(R.id.TVGPUTempLabel);
        tvFrameTimeLabel = view.findViewById(R.id.TVFrameTimeLabel);
        tvRAMLabel = view.findViewById(R.id.TVRAMLabel);
        tvCPULoadLabel = view.findViewById(R.id.TVCPULoadLabel);
        tvCPUTempLabel = view.findViewById(R.id.TVCPUTempLabel);
        tvBatteryTempLabel = view.findViewById(R.id.TVBatteryTempLabel);
        tvBatteryVoltageLabel = view.findViewById(R.id.TVBatteryVoltageLabel);

        rootLayout = view.findViewById(R.id.fps_counter_root);
        contentLayout = view.findViewById(R.id.FPSCounterContent);
        counterCard = view.findViewById(R.id.FPSCounterCard);
        layoutFPS = view.findViewById(R.id.LayoutFPS);
        layoutRenderer = view.findViewById(R.id.LayoutRenderer);
        layoutGPU = view.findViewById(R.id.LayoutGPU);
        layoutGPULoad = view.findViewById(R.id.LayoutGPULoad);
        layoutGPUTemp = view.findViewById(R.id.LayoutGPUTemp);
        layoutFrameTimeGraph = view.findViewById(R.id.LayoutFrameTimeGraph);
        layoutRAM = view.findViewById(R.id.LayoutRAM);
        layoutCPULoad = view.findViewById(R.id.LayoutCPULoad);
        layoutCPUTemp = view.findViewById(R.id.LayoutCPUTemp);
        layoutBatteryTemp = view.findViewById(R.id.LayoutBatteryTemp);
        layoutBatteryVoltage = view.findViewById(R.id.LayoutBatteryVoltage);

        frameTimeGraphView = view.findViewById(R.id.FrameTimeGraphView);

        addView(view);
        updateModuleVisibility();
        updateOrientation();
        updateScaleAndTextSize();
        setupDragging();
    }

    private String getTotalRAM() {
        if (activityManager == null) {
            return "N/A";
        }
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return StringUtils.formatBytes(memoryInfo.totalMem);
    }

    private String getUsedRAM() {
        if (activityManager == null) {
            return "N/A";
        }
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
        return StringUtils.formatBytes(usedMem, false);
    }

    public void reset() {
        Log.d(TAG, "Resetting FrameRating");
        renderer = null;
        gpuName = null;
        lastFPS = 0f;
        frameCount = 0;
        lastTime = 0L;
        lastFrameTimestampNs = 0L;
        if (frameTimeGraphView != null) {
            frameTimeGraphView.reset();
        }
    }

    public void setRenderer(String renderer) {
        this.renderer = renderer;
    }

    public void setGpuName(String gpuName) {
        this.gpuName = gpuName;
    }

    private String getCPUTemperature() {
        return sensorReader.getCpuTemperature();
    }

    private String getGpuTemperature() {
        return sensorReader.getGpuTemperature();
    }

    private String getGpuLoad() {
        return sensorReader.getGpuLoad();
    }

    private String getCpuLoad() {
        return sensorReader.getCpuLoad();
    }

    public void updateOrientation() {
        boolean isHorizontal = config.isHorizontalLayout();
        Log.d(TAG, "Updating orientation to: " + (isHorizontal ? "horizontal" : "vertical"));
        int flexWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
        if (contentLayout != null) {
            contentLayout.setVertical(!isHorizontal);
            ViewGroup.LayoutParams clp = contentLayout.getLayoutParams();
            if (clp != null) clp.width = flexWidth;
            setHorizontalSeparatorsVisible(contentLayout, isHorizontal);
            // Tighten per-item end margins in horizontal mode so more indicators fit in one row.
            float density = getResources().getDisplayMetrics().density;
            int itemEndMarginPx = Math.round((isHorizontal ? 4f : 12f) * density);
            int sepStartMarginPx = Math.round((isHorizontal ? 4f : 10f) * density);
            applyHorizontalSpacing(contentLayout, itemEndMarginPx, sepStartMarginPx);
        }
        if (counterCard != null && counterCard.getLayoutParams() != null) {
            counterCard.getLayoutParams().width = flexWidth;
        }
        if (rootLayout != null && rootLayout.getLayoutParams() != null) {
            rootLayout.getLayoutParams().width = flexWidth;
        }
        ViewGroup.LayoutParams selfLp = getLayoutParams();
        if (selfLp != null) selfLp.width = flexWidth;
        requestLayout();
    }

    private void applyHorizontalSpacing(ViewGroup container, int itemEndMarginPx, int sepStartMarginPx) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).rightMargin = itemEndMarginPx;
                ((ViewGroup.MarginLayoutParams) lp).setMarginEnd(itemEndMarginPx);
            }
            if (child instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) child;
                for (int j = 0; j < vg.getChildCount(); j++) {
                    View inner = vg.getChildAt(j);
                    if ("horizontal_separator".equals(inner.getTag())) {
                        ViewGroup.LayoutParams ilp = inner.getLayoutParams();
                        if (ilp instanceof ViewGroup.MarginLayoutParams) {
                            ((ViewGroup.MarginLayoutParams) ilp).leftMargin = sepStartMarginPx;
                            ((ViewGroup.MarginLayoutParams) ilp).setMarginStart(sepStartMarginPx);
                        }
                    }
                }
            }
        }
    }

    private void setHorizontalSeparatorsVisible(View view, boolean visible) {
        Object tag = view.getTag();
        if ("horizontal_separator".equals(tag)) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                setHorizontalSeparatorsVisible(viewGroup.getChildAt(i), visible);
            }
        }
    }

    public void updateModuleVisibility() {
        setModuleVisibility(layoutFPS, config.isModuleVisible(FpsCounterConfig.Module.FPS));
        setModuleVisibility(layoutRenderer, config.isModuleVisible(FpsCounterConfig.Module.RENDERER));
        setModuleVisibility(layoutGPU, config.isModuleVisible(FpsCounterConfig.Module.GPU));
        setModuleVisibility(layoutGPULoad, config.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD));
        setModuleVisibility(layoutGPUTemp, config.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP));
        setModuleVisibility(layoutFrameTimeGraph, config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH));
        setModuleVisibility(layoutRAM, config.isModuleVisible(FpsCounterConfig.Module.RAM));
        setModuleVisibility(layoutCPULoad, config.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD));
        setModuleVisibility(layoutCPUTemp, config.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP));
        setModuleVisibility(layoutBatteryTemp, config.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP));
        setModuleVisibility(layoutBatteryVoltage, config.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE));
        updateBackgroundOpacity();
        updateScaleAndTextSize();
    }

    private void setModuleVisibility(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    public void updateBackgroundOpacity() {
        int opacity = config.getBackgroundOpacity();
        int backgroundColor = Color.argb(opacity, 0, 0, 0);
        if (counterCard != null) {
            counterCard.setCardBackgroundColor(backgroundColor);
        }
        setModuleBackground(layoutFPS, Color.TRANSPARENT);
        setModuleBackground(layoutRenderer, Color.TRANSPARENT);
        setModuleBackground(layoutGPU, Color.TRANSPARENT);
        setModuleBackground(layoutGPULoad, Color.TRANSPARENT);
        setModuleBackground(layoutGPUTemp, Color.TRANSPARENT);
        setModuleBackground(layoutFrameTimeGraph, Color.TRANSPARENT);
        setModuleBackground(layoutRAM, Color.TRANSPARENT);
        setModuleBackground(layoutCPULoad, Color.TRANSPARENT);
        setModuleBackground(layoutCPUTemp, Color.TRANSPARENT);
        setModuleBackground(layoutBatteryTemp, Color.TRANSPARENT);
        setModuleBackground(layoutBatteryVoltage, Color.TRANSPARENT);
    }

    private void setModuleBackground(View view, int backgroundColor) {
        if (view != null) {
            view.setBackgroundColor(backgroundColor);
        }
    }

    public void updateScaleAndTextSize() {
        int scale = config.getCounterScale();
        float scaleFactor = scale / 100.0f;
        setPivotX(0f);
        setPivotY(0f);
        setScaleX(scaleFactor);
        setScaleY(scaleFactor);

        int textSize = 11;
        applyTextSize(tvFPS, textSize);
        applyTextSize(tvRenderer, textSize);
        applyTextSize(tvGPU, textSize);
        applyTextSize(tvGPULoad, textSize);
        applyTextSize(tvGPUTemp, textSize);
        applyTextSize(tvFrameTime, textSize);
        applyTextSize(tvRAM, textSize);
        applyTextSize(tvCPULoad, textSize);
        applyTextSize(tvCPUTemp, textSize);
        applyTextSize(tvBatteryTemp, textSize);
        applyTextSize(tvBatteryVoltage, textSize);
        applyTextSize(tvFPSLabel, textSize);
        applyTextSize(tvRendererLabel, textSize);
        applyTextSize(tvGPULabel, textSize);
        applyTextSize(tvGPULoadLabel, textSize);
        applyTextSize(tvGPUTempLabel, textSize);
        applyTextSize(tvFrameTimeLabel, textSize);
        applyTextSize(tvRAMLabel, textSize);
        applyTextSize(tvCPULoadLabel, textSize);
        applyTextSize(tvCPUTempLabel, textSize);
        applyTextSize(tvBatteryTempLabel, textSize);
        applyTextSize(tvBatteryVoltageLabel, textSize);

        post(this::clampToParentBounds);
    }

    private void applyTextSize(TextView view, int textSize) {
        if (view != null) {
            view.setTextSize(textSize);
        }
    }

    private void clampToParentBounds() {
        View parent = (View) getParent();
        if (parent == null) {
            return;
        }

        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        int scaledWidth = (int) Math.ceil(getWidth() * getScaleX());
        int scaledHeight = (int) Math.ceil(getHeight() * getScaleY());
        if (parentWidth <= 0 || parentHeight <= 0 || scaledWidth <= 0 || scaledHeight <= 0) {
            return;
        }

        float maxX = Math.max(0, parentWidth - scaledWidth);
        float maxY = Math.max(0, parentHeight - scaledHeight);
        setX(Math.max(0, Math.min(getX(), maxX)));
        setY(Math.max(0, Math.min(getY(), maxY)));
    }

    private void setupDragging() {
        if (rootLayout == null) {
            return;
        }

        final float[] dX = new float[1];
        final float[] dY = new float[1];
        final boolean[] dragging = new boolean[1];

        View.OnTouchListener dragListener = (v, event) -> {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    dragging[0] = true;
                    dX[0] = getX() - event.getRawX();
                    dY[0] = getY() - event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        return false;
                    }
                    float newX = event.getRawX() + dX[0];
                    float newY = event.getRawY() + dY[0];
                    View parent = (View) getParent();
                    if (parent != null) {
                        int parentWidth = parent.getWidth();
                        int parentHeight = parent.getHeight();
                        int scaledWidth = (int) Math.ceil(getWidth() * getScaleX());
                        int scaledHeight = (int) Math.ceil(getHeight() * getScaleY());
                        if (parentWidth > 0 && parentHeight > 0 && scaledWidth > 0 && scaledHeight > 0) {
                            float maxX = Math.max(0, parentWidth - scaledWidth);
                            float maxY = Math.max(0, parentHeight - scaledHeight);
                            newX = Math.max(0, Math.min(newX, maxX));
                            newY = Math.max(0, Math.min(newY, maxY));
                        }
                    }
                    setX(newX);
                    setY(newY);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging[0] = false;
                    return true;
                default:
                    return false;
            }
        };

        applyTouchListenerRecursively(rootLayout, dragListener);
    }

    private static void applyTouchListenerRecursively(View root, View.OnTouchListener listener) {
        root.setOnTouchListener(listener);
        root.setClickable(true);
        root.setFocusable(false);
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyTouchListenerRecursively(vg.getChildAt(i), listener);
            }
        }
    }

    public FpsCounterConfig getConfig() {
        return config;
    }

    public void update() {
        boolean profiling = ProfilingSession.getInstance().isActive();
        boolean overlayVisible = config.isEnabled() && getVisibility() == View.VISIBLE;
        if (!overlayVisible && !profiling) {
            return;
        }

        long frameTimestampNs = SystemClock.elapsedRealtimeNanos();
        if (lastFrameTimestampNs != 0L && overlayVisible && config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)) {
            float frameTimeMs = (frameTimestampNs - lastFrameTimestampNs) / 1_000_000.0f;
            if (frameTimeGraphView != null) {
                frameTimeGraphView.addFrameTimeSample(frameTimeMs);
            }
        }
        lastFrameTimestampNs = frameTimestampNs;

        if (lastTime == 0L) {
            lastTime = SystemClock.elapsedRealtime();
        }
        long time = SystemClock.elapsedRealtime();
        if (time >= lastTime + 500L) {
            lastFPS = ((float) (frameCount * 1000)) / (time - lastTime);
            if (profiling) {
                ProfilingSession.getInstance().addFpsSample(lastFPS);
            }
            post(this);
            lastTime = time;
            frameCount = 0;
        }
        frameCount++;
    }

    @Override
    public void run() {
        boolean profiling = ProfilingSession.getInstance().isActive();
        boolean overlayVisible = config.isEnabled() && getVisibility() == View.VISIBLE;
        if (!overlayVisible && !profiling) {
            return;
        }
        if (profiling) {
            collectProfilingSensorSample();
        }
        if (!overlayVisible) {
            return;
        }

        if (config.isModuleVisible(FpsCounterConfig.Module.FPS)) {
            tvFPS.setText(String.format(Locale.ENGLISH, "%.1f", lastFPS));
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.RENDERER)) {
            tvRenderer.setText(renderer != null ? renderer : "OpenGL");
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU)) {
            tvGPU.setText(gpuName != null ? gpuName : GPUInformation.getRenderer());
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU_LOAD)) {
            tvGPULoad.setText(getGpuLoad());
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.GPU_TEMP)) {
            tvGPUTemp.setText(getGpuTemperature());
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.FRAME_TIME_GRAPH)) {
            float frameTimeMs = lastFPS > 0f ? (1000.0f / lastFPS) : 0f;
            tvFrameTime.setText(frameTimeMs > 0f
                ? String.format(Locale.ENGLISH, "%.1fms", frameTimeMs)
                : "--");
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.RAM)) {
            tvRAM.setText(getUsedRAM() + " / " + totalRAM);
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.CPU_LOAD)) {
            tvCPULoad.setText(getCpuLoad());
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.CPU_TEMP)) {
            tvCPUTemp.setText(getCPUTemperature());
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.BATTERY_TEMP)) {
            tvBatteryTemp.setText(batteryTemperature != -1.0f
                ? String.format(Locale.ENGLISH, "%.1fC", batteryTemperature)
                : "N/A");
        }
        if (config.isModuleVisible(FpsCounterConfig.Module.BATTERY_VOLTAGE)) {
            refreshBatteryCurrent();
            float powerWatts = getBatteryPowerWatts();
            if (powerWatts != -1.0f) {
                tvBatteryVoltage.setText(String.format(Locale.ENGLISH, "%.2fW", powerWatts));
            } else {
                tvBatteryVoltage.setText("N/A");
            }
        }
    }

    private void collectProfilingSensorSample() {
        ProfilingSession.getInstance().collectSample(context);
    }

    private void refreshBatteryCurrent() {
        if (batteryManager == null) {
            batteryCurrent = -1.0f;
            return;
        }
        batteryCurrent = sensorReader.readCurrentAmpsFromSysfs(batteryManager);
    }

    private float getBatteryPowerWatts() {
        return sensorReader.getBatteryPowerWatts(batteryManager, batteryVoltage, batteryCurrent);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!batteryReceiverRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(batteryReceiver, filter);
            batteryReceiverRegistered = true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (batteryReceiverRegistered) {
            try {
                context.unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            batteryReceiverRegistered = false;
        }
    }
}
