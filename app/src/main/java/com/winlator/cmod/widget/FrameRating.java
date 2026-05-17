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
import com.winlator.cmod.core.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FrameRating extends FrameLayout implements Runnable {
    private static final String TAG = "FrameRating";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final String[] GPU_LOAD_FILES = {
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
        "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
        "/sys/class/kgsl/kgsl-3d0/busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/load",
        "/sys/class/devfreq/gpu/load",
        "/sys/class/devfreq/gpu/gpu_load",
        "/sys/class/devfreq/gpufreq/load",
        "/sys/class/devfreq/mali/load",
        "/sys/class/misc/mali0/device/utilisation",
        "/sys/class/misc/mali0/device/utilization",
        "/sys/class/misc/mali0/device/gpu_utilization",
        "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpubusy"
    };
    private static final String[] GPU_LOAD_FILE_NAMES = {
        "gpubusy", "gpu_busy", "gpu_load", "load", "busy_percentage",
        "utilisation", "utilization", "gpu_utilization"
    };
    private static final String[] GPU_TEMP_FILES = {
        "/sys/class/kgsl/kgsl-3d0/temp",
        "/sys/class/kgsl/kgsl-3d0/gpu_temp",
        "/sys/class/kgsl/kgsl-3d0/device/temp",
        "/sys/class/devfreq/gpu/temp",
        "/sys/class/devfreq/gpu/temperature",
        "/sys/class/devfreq/gpufreq/temp",
        "/sys/class/misc/mali0/device/temp",
        "/sys/class/misc/mali0/device/temperature",
        "/sys/class/thermal/thermal_zone10/temp",
        "/sys/devices/virtual/thermal/thermal_zone10/temp"
    };
    private static final String[] BATTERY_CURRENT_FILES = {
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/main/current_now",
        "/sys/class/power_supply/usb/current_now",
        "/sys/class/power_supply/battery/current_avg",
        "/sys/class/power_supply/bms/current_avg"
    };
    private static final String[] BATTERY_VOLTAGE_FILES = {
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/bms/voltage_now",
        "/sys/class/power_supply/main/voltage_now",
        "/sys/class/power_supply/battery/voltage_avg",
        "/sys/class/power_supply/bms/voltage_avg"
    };
    private static final String[] BATTERY_POWER_FILES = {
        "/sys/class/power_supply/battery/power_now",
        "/sys/class/power_supply/bms/power_now"
    };
    private static final String[] CPU_SENSOR_FILES = {
        "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
        "/sys/devices/system/cpu/cpu0/cpufreq/FakeShmoo_cpu_temp",
        "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/temperature",
        "/sys/devices/platform/omap/omap_temp_sensor.0/temperature",
        "/sys/devices/platform/tegra_tmon/temp1_input",
        "/sys/devices/platform/s5p-tmu/temperature",
        "/sys/devices/platform/s5p-tmu/curr_temp",
        "/sys/devices/virtual/thermal/thermal_zone10/temp",
        "/sys/devices/virtual/thermal/thermal_zone1/temp",
        "/sys/devices/virtual/thermal/thermal_zone0/temp",
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/class/thermal/thermal_zone1/temp",
        "/sys/class/thermal/thermal_zone3/temp",
        "/sys/class/thermal/thermal_zone4/temp",
        "/sys/class/hwmon/hwmon0/device/temp1_input",
        "/sys/class/hwmon/hwmonX/temp1_input",
        "/sys/class/i2c-adapter/i2c-4/4-004c/temperature",
        "/sys/kernel/debug/tegra_thermal/temp_tj",
        "/sys/htc/cpu_temp",
        "/sys/devices/platform/tegra-i2c.3/i2c-4/4-004c/ext_temperature",
        "/sys/devices/platform/tegra-tsensor/tsensor_temperature",
        "/sys/devices/virtual/sec/sec-lp-thermistor/temperature"
    };

    private final Context context;
    private final FpsCounterConfig config;
    private final ActivityManager activityManager;
    private final BatteryManager batteryManager;
    private final String totalRAM;
    private long lastFrameTimestampNs;

    private long lastTime;
    private int frameCount;
    private float lastFPS;
    private String renderer;
    private String gpuName;
    private long lastCpuTotal = -1L;
    private long lastCpuIdle = -1L;
    private long lastMaliGpuInfoMs = -1L;
    private long lastMaliGpuInfoWallMs = 0L;
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
        for (String path : CPU_SENSOR_FILES) {
            String temperature = parseTemperature(readFirstLine(path));
            if (temperature != null) {
                return temperature;
            }
        }
        String dynamicTemperature = getDynamicCpuTemperature();
        if (dynamicTemperature != null) {
            return dynamicTemperature;
        }
        return "N/A";
    }

    private static int cpuThermalZonePriority(String type) {
        if (type.contains("cpu-silicon")) return 0;
        if (type.contains("cpu-0"))       return 1;
        if (type.contains("cpu") && !type.contains("gpu")) return 2;
        if (type.contains("cputop"))      return 3;
        if (type.contains("tsens") || type.contains("soc") || type.contains("ap")) return 4;
        return -1;
    }

    private static int gpuThermalZonePriority(String type) {
        if (type.contains("gpu-silicon")) return 0;
        if (type.contains("gpu"))         return 1;
        if (type.contains("kgsl"))        return 2;
        if (type.contains("mali") || type.contains("g3d")) return 3;
        return -1;
    }

    @SuppressWarnings("unchecked")
    private String getDynamicCpuTemperature() {
        File thermalDir = new File("/sys/class/thermal");
        File[] entries = thermalDir.listFiles();
        if (entries == null) return null;
        List<String>[] buckets = new List[5];
        for (int i = 0; i < buckets.length; i++) buckets[i] = new ArrayList<>();
        for (File entry : entries) {
            if (!entry.getName().startsWith("thermal_zone")) continue;
            String type = readFirstLine(new File(entry, "type").getAbsolutePath());
            if (type == null) continue;
            int priority = cpuThermalZonePriority(type.toLowerCase(Locale.ENGLISH));
            if (priority >= 0 && priority < buckets.length) {
                buckets[priority].add(new File(entry, "temp").getAbsolutePath());
            }
        }
        for (List<String> bucket : buckets) {
            for (String path : bucket) {
                String temp = parseTemperature(readFirstLine(path));
                if (temp != null) return temp;
            }
        }
        return null;
    }

    private String parseTemperature(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        try {
            int temperature = Integer.parseInt(rawValue.trim());
            if (temperature > 100 && temperature <= 1000) {
                return String.format(Locale.ENGLISH, "%.1fC", temperature / 10.0f);
            }
            if (temperature > 1000 && temperature <= 10000) {
                return String.format(Locale.ENGLISH, "%.1fC", temperature / 100.0f);
            }
            if (temperature > 10000) {
                return String.format(Locale.ENGLISH, "%.1fC", (temperature + 500) / 1000.0f);
            }
            if (temperature > 0 && temperature <= 100) {
                return String.format(Locale.ENGLISH, "%dC", temperature);
            }
        } catch (NumberFormatException e) {
            Log.d(TAG, "Failed to parse temperature: " + rawValue);
        }
        return null;
    }

    private String getGpuLoad() {
        for (String path : GPU_LOAD_FILES) {
            String parsed = parseGpuLoad(readFirstLine(path));
            if (parsed != null) return parsed;
        }
        String maliDelta = getMaliDeltaGpuLoad();
        if (maliDelta != null) return maliDelta;
        String dynamicLoad = getDynamicGpuLoad();
        if (dynamicLoad != null) return dynamicLoad;
        return "N/A";
    }

    private String getMaliDeltaGpuLoad() {
        String raw = readNthLine("/sys/class/misc/mali0/device/gpuinfo", 1);
        if (raw == null) return null;
        String[] parts = raw.trim().split("\\s+");
        if (parts.length == 0) return null;
        long gpuMs;
        try { gpuMs = Long.parseLong(parts[parts.length - 1]); } catch (NumberFormatException e) { return null; }
        long now = SystemClock.elapsedRealtime();
        long prevMs = lastMaliGpuInfoMs;
        long prevWall = lastMaliGpuInfoWallMs;
        lastMaliGpuInfoMs = gpuMs;
        lastMaliGpuInfoWallMs = now;
        if (prevMs < 0 || prevWall <= 0) return null;
        long wallDelta = now - prevWall;
        if (wallDelta <= 0) return null;
        long gpuDelta = Math.max(0L, gpuMs - prevMs);
        int pct = (int) Math.min(100L, (gpuDelta * 100L) / wallDelta);
        return String.format(Locale.ENGLISH, "%d%%", pct);
    }

    private String readNthLine(String path, int lineIndex) {
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            String line = null;
            for (int i = 0; i <= lineIndex; i++) {
                line = br.readLine();
                if (line == null) break;
            }
            br.close();
            return line;
        } catch (Exception e) {
            return null;
        }
    }

    private String getDynamicGpuLoad() {
        File devfreqDir = new File("/sys/class/devfreq");
        File[] entries = devfreqDir.listFiles();
        if (entries == null) {
            return null;
        }
        for (File entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ENGLISH);
            if (!name.contains("gpu") && !name.contains("kgsl") && !name.contains("mali") && !name.contains("g3d")) {
                continue;
            }
            for (String fileName : GPU_LOAD_FILE_NAMES) {
                String parsed = parseGpuLoad(readFirstLine(new File(entry, fileName).getAbsolutePath()));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private String getGpuTemperature() {
        for (String path : GPU_TEMP_FILES) {
            String temperature = parseTemperature(readFirstLine(path));
            if (temperature != null) {
                return temperature;
            }
        }
        return getDynamicGpuTemperature();
    }

@SuppressWarnings("unchecked")
    private String getDynamicGpuTemperature() {
        File thermalDir = new File("/sys/class/thermal");
        File[] entries = thermalDir.listFiles();
        if (entries != null) {
            List<String>[] buckets = new List[4];
            for (int i = 0; i < buckets.length; i++) buckets[i] = new ArrayList<>();
            for (File entry : entries) {
                if (!entry.getName().startsWith("thermal_zone")) continue;
                String type = readFirstLine(new File(entry, "type").getAbsolutePath());
                if (type == null) continue;
                int priority = gpuThermalZonePriority(type.toLowerCase(Locale.ENGLISH));
                if (priority >= 0 && priority < buckets.length) {
                    buckets[priority].add(new File(entry, "temp").getAbsolutePath());
                }
            }
            for (List<String> bucket : buckets) {
                for (String path : bucket) {
                    String temp = parseTemperature(readFirstLine(path));
                    if (temp != null) return temp;
                }
            }
        }

        File devfreqDir = new File("/sys/class/devfreq");
        File[] devfreqEntries = devfreqDir.listFiles();
        if (devfreqEntries == null) return "N/A";
        for (File entry : devfreqEntries) {
            String name = entry.getName().toLowerCase(Locale.ENGLISH);
            if (!name.contains("gpu") && !name.contains("kgsl") && !name.contains("mali") && !name.contains("g3d")) continue;
            String temperature = parseTemperature(readFirstLine(new File(entry, "temp").getAbsolutePath()));
            if (temperature == null) {
                temperature = parseTemperature(readFirstLine(new File(entry, "temperature").getAbsolutePath()));
            }
            if (temperature != null) return temperature;
        }
        return "N/A";
    }

    private String getCpuLoad() {
        CpuTimes cpuTimes = readCpuTimes();
        if (cpuTimes == null) {
            return "N/A";
        }

        if (lastCpuTotal < 0L || lastCpuIdle < 0L) {
            lastCpuTotal = cpuTimes.total;
            lastCpuIdle = cpuTimes.idle;
            return "0%";
        }

        long totalDelta = cpuTimes.total - lastCpuTotal;
        long idleDelta = cpuTimes.idle - lastCpuIdle;
        lastCpuTotal = cpuTimes.total;
        lastCpuIdle = cpuTimes.idle;

        if (totalDelta <= 0L || idleDelta < 0L) {
            return "0%";
        }
        float usage = ((totalDelta - idleDelta) * 100f) / totalDelta;
        usage = Math.max(0f, Math.min(100f, usage));
        return String.format(Locale.ENGLISH, "%.0f%%", usage);
    }

    private CpuTimes readCpuTimes() {
        File file = new File("/proc/stat");
        if (!file.exists()) {
            return null;
        }
        CpuTimes summedCores = null;
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("cpu ")) {
                    return parseCpuTimes(line);
                }
                if (line.startsWith("cpu") && line.length() > 3 && Character.isDigit(line.charAt(3))) {
                    CpuTimes coreTimes = parseCpuTimes(line);
                    if (coreTimes != null) {
                        summedCores = summedCores == null
                                ? coreTimes
                                : new CpuTimes(summedCores.total + coreTimes.total, summedCores.idle + coreTimes.idle);
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Failed to read CPU load from /proc/stat: " + e.getMessage());
        }
        return summedCores;
    }

    private CpuTimes parseCpuTimes(String raw) {
        String[] parts = raw.trim().split("\\s+");
        if (parts.length < 5) {
            return null;
        }
        try {
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0L;
            long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0L;
            long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0L;
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0L;

            long idleAll = idle + iowait;
            long total = user + nice + system + idle + iowait + irq + softirq + steal;
            return new CpuTimes(total, idleAll);
        } catch (NumberFormatException e) {
            Log.d(TAG, "Failed to parse CPU load: " + raw);
            return null;
        }
    }

    private String parseGpuLoad(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(rawValue);
        long[] numbers = new long[4];
        int count = 0;
        while (matcher.find() && count < numbers.length) {
            numbers[count++] = Long.parseLong(matcher.group(1));
        }

        if (count == 0) {
            return null;
        }

        if (count >= 2 && numbers[1] > 0 && numbers[0] <= numbers[1]) {
            float usage = (numbers[0] * 100f) / numbers[1];
            usage = Math.max(0f, Math.min(100f, usage));
            return String.format(Locale.ENGLISH, "%.0f%%", usage);
        }

        long value = numbers[0];
        if (value >= 0 && value <= 100) {
            return String.format(Locale.ENGLISH, "%d%%", value);
        }

        return null;
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
        Float cpuTempC = parseTempCelsius(getCPUTemperature());
        Float gpuTempC = parseTempCelsius(getGpuTemperature());
        Float batTempC = batteryTemperature != -1.0f ? batteryTemperature : null;
        Float cpuLoadPct = parsePercent(getCpuLoad());
        Float gpuLoadPct = parsePercent(getGpuLoad());
        Long ramUsed = null;
        if (activityManager != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(mi);
            ramUsed = mi.totalMem - mi.availMem;
        }
        ProfilingSession.getInstance().addSensorSample(cpuTempC, gpuTempC, batTempC, cpuLoadPct, gpuLoadPct, ramUsed);
    }

    private static Float parseTempCelsius(String formatted) {
        if (formatted == null || formatted.isEmpty() || "N/A".equals(formatted)) return null;
        try {
            String num = formatted.replace("C", "").replace("\u00B0", "").trim();
            return Float.parseFloat(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Float parsePercent(String formatted) {
        if (formatted == null || formatted.isEmpty() || "N/A".equals(formatted)) return null;
        try {
            return Float.parseFloat(formatted.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void refreshBatteryCurrent() {
        if (batteryManager == null) {
            batteryCurrent = -1.0f;
            return;
        }
        int currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        batteryCurrent = currentMicroAmps == Integer.MIN_VALUE ? -1.0f : Math.abs(currentMicroAmps) / 1000000.0f;
    }

    private float getBatteryPowerWatts() {
        float directPower = readPowerWattsFromSysfs();
        if (directPower != -1.0f) {
            return directPower;
        }

        float voltage = batteryVoltage != -1.0f ? batteryVoltage : readVoltageVoltsFromSysfs();
        float current = batteryCurrent != -1.0f ? batteryCurrent : readCurrentAmpsFromSysfs();
        if (voltage != -1.0f && current != -1.0f) {
            return voltage * current;
        }
        return -1.0f;
    }

    private float readPowerWattsFromSysfs() {
        for (String path : BATTERY_POWER_FILES) {
            Long value = readLong(path);
            if (value != null && value != 0L) {
                long absValue = Math.abs(value);
                return absValue > 1000 ? absValue / 1000000.0f : absValue;
            }
        }
        return -1.0f;
    }

    private float readCurrentAmpsFromSysfs() {
        for (String path : BATTERY_CURRENT_FILES) {
            Long value = readLong(path);
            if (value != null && value != 0L) {
                long absValue = Math.abs(value);
                return absValue > 1000 ? absValue / 1000000.0f : absValue / 1000.0f;
            }
        }
        return -1.0f;
    }

    private float readVoltageVoltsFromSysfs() {
        for (String path : BATTERY_VOLTAGE_FILES) {
            Long value = readLong(path);
            if (value != null && value != 0L) {
                long absValue = Math.abs(value);
                return absValue > 10000 ? absValue / 1000000.0f : absValue / 1000.0f;
            }
        }
        return -1.0f;
    }

    private Long readLong(String path) {
        String raw = readFirstLine(path);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            Log.d(TAG, "Failed to parse long from " + path + ": " + raw);
            return null;
        }
    }

    private String readFirstLine(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        }
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            return reader.readLine();
        } catch (Exception e) {
            Log.d(TAG, "Failed to read " + path + ": " + e.getMessage());
            return null;
        }
    }

    private static class CpuTimes {
        final long total;
        final long idle;

        CpuTimes(long total, long idle) {
            this.total = total;
            this.idle = idle;
        }
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
