package com.winlator.cmod.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;

import com.winlator.cmod.R;
import com.winlator.cmod.core.SensorReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ProfilingSession {
    private static final ProfilingSession INSTANCE = new ProfilingSession();

    public static ProfilingSession getInstance() {
        return INSTANCE;
    }

    private volatile boolean active;
    private String targetProcessName;
    private int targetPid;
    private long startElapsedMs;
    private long stopElapsedMs;

    private final List<Float> fpsSamples = new ArrayList<>();

    private float maxCpuTempC = Float.NEGATIVE_INFINITY;
    private float maxGpuTempC = Float.NEGATIVE_INFINITY;
    private float maxBatteryTempC = Float.NEGATIVE_INFINITY;
    private float maxCpuLoadPct = Float.NEGATIVE_INFINITY;
    private float maxGpuLoadPct = Float.NEGATIVE_INFINITY;
    private long maxRamUsedBytes = 0;

    private float sumCpuTempC, sumGpuTempC, sumBatteryTempC, sumCpuLoadPct, sumGpuLoadPct;
    private int sensorSamples;
    private long sumRamUsedBytes;
    private int ramSamples;

    private final SensorReader sensorReader = new SensorReader();

    private ProfilingSession() {}

    public synchronized void start(String processName, int pid) {
        reset();
        this.targetProcessName = processName;
        this.targetPid = pid;
        this.startElapsedMs = SystemClock.elapsedRealtime();
        this.active = true;
    }

    public synchronized Result stop() {
        if (!active) return null;
        this.stopElapsedMs = SystemClock.elapsedRealtime();
        this.active = false;
        return buildResult();
    }

    public boolean isActive() {
        return active;
    }

    public String getTargetProcessName() {
        return targetProcessName;
    }

    public synchronized void addFpsSample(float fps) {
        if (!active) return;
        if (fps <= 0f || Float.isNaN(fps) || Float.isInfinite(fps)) return;
        fpsSamples.add(fps);
    }

    public synchronized void addSensorSample(Float cpuTempC, Float gpuTempC, Float batteryTempC,
                                             Float cpuLoadPct, Float gpuLoadPct, Long ramUsedBytes) {
        if (!active) return;
        boolean any = false;
        if (cpuTempC != null) { maxCpuTempC = Math.max(maxCpuTempC, cpuTempC); sumCpuTempC += cpuTempC; any = true; }
        if (gpuTempC != null) { maxGpuTempC = Math.max(maxGpuTempC, gpuTempC); sumGpuTempC += gpuTempC; any = true; }
        if (batteryTempC != null) { maxBatteryTempC = Math.max(maxBatteryTempC, batteryTempC); sumBatteryTempC += batteryTempC; any = true; }
        if (cpuLoadPct != null) { maxCpuLoadPct = Math.max(maxCpuLoadPct, cpuLoadPct); sumCpuLoadPct += cpuLoadPct; any = true; }
        if (gpuLoadPct != null) { maxGpuLoadPct = Math.max(maxGpuLoadPct, gpuLoadPct); sumGpuLoadPct += gpuLoadPct; any = true; }
        if (ramUsedBytes != null) {
            maxRamUsedBytes = Math.max(maxRamUsedBytes, ramUsedBytes);
            sumRamUsedBytes += ramUsedBytes;
            ramSamples++;
        }
        if (any) sensorSamples++;
    }

    public void collectSample(Context context) {
        if (!active) return;
        Float cpuTempC = SensorReader.parseTempCelsius(sensorReader.getCpuTemperature());
        Float gpuTempC = SensorReader.parseTempCelsius(sensorReader.getGpuTemperature());
        Float cpuLoadPct = SensorReader.parsePercent(sensorReader.getCpuLoad());
        Float gpuLoadPct = SensorReader.parsePercent(sensorReader.getGpuLoad());
        Long ramUsed = null;
        if (context != null) {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                ramUsed = mi.totalMem - mi.availMem;
            }
        }
        addSensorSample(cpuTempC, gpuTempC, null, cpuLoadPct, gpuLoadPct, ramUsed);
    }

    public SensorReader getSensorReader() {
        return sensorReader;
    }

    private void reset() {
        fpsSamples.clear();
        maxCpuTempC = maxGpuTempC = maxBatteryTempC = maxCpuLoadPct = maxGpuLoadPct = Float.NEGATIVE_INFINITY;
        maxRamUsedBytes = 0;
        sumCpuTempC = sumGpuTempC = sumBatteryTempC = sumCpuLoadPct = sumGpuLoadPct = 0f;
        sensorSamples = 0;
        sumRamUsedBytes = 0;
        ramSamples = 0;
        targetProcessName = null;
        targetPid = 0;
        startElapsedMs = stopElapsedMs = 0;
    }

    private Result buildResult() {
        Result r = new Result();
        r.processName = targetProcessName;
        r.pid = targetPid;
        r.durationMs = Math.max(0, stopElapsedMs - startElapsedMs);
        r.fpsSampleCount = fpsSamples.size();
        r.fpsTimeline = new ArrayList<>(fpsSamples);

        if (!fpsSamples.isEmpty()) {
            List<Float> sorted = new ArrayList<>(fpsSamples);
            Collections.sort(sorted);
            float sum = 0f;
            float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
            for (Float f : sorted) {
                sum += f;
                if (f < min) min = f;
                if (f > max) max = f;
            }
            r.avgFps = sum / sorted.size();
            r.minFps = min;
            r.maxFps = max;
            r.low1Fps = percentileAverage(sorted, 0.01f);
            r.low01Fps = percentileAverage(sorted, 0.001f);
        }

        r.maxCpuTempC = finiteOrNaN(maxCpuTempC);
        r.maxGpuTempC = finiteOrNaN(maxGpuTempC);
        r.maxBatteryTempC = finiteOrNaN(maxBatteryTempC);
        r.maxCpuLoadPct = finiteOrNaN(maxCpuLoadPct);
        r.maxGpuLoadPct = finiteOrNaN(maxGpuLoadPct);
        r.maxRamUsedBytes = maxRamUsedBytes;
        r.avgRamUsedBytes = ramSamples > 0 ? sumRamUsedBytes / ramSamples : 0;

        if (sensorSamples > 0) {
            r.avgCpuTempC = sumCpuTempC / sensorSamples;
            r.avgGpuTempC = sumGpuTempC / sensorSamples;
            r.avgBatteryTempC = sumBatteryTempC / sensorSamples;
            r.avgCpuLoadPct = sumCpuLoadPct / sensorSamples;
            r.avgGpuLoadPct = sumGpuLoadPct / sensorSamples;
        }
        return r;
    }

    private static float finiteOrNaN(float v) {
        return Float.isInfinite(v) ? Float.NaN : v;
    }

    /** Average of the worst {@code fraction} of sorted-ascending samples (e.g. 0.01 → 1% low). */
    private static float percentileAverage(List<Float> sortedAsc, float fraction) {
        if (sortedAsc.isEmpty()) return Float.NaN;
        int n = Math.max(1, (int) Math.ceil(sortedAsc.size() * fraction));
        float sum = 0f;
        for (int i = 0; i < n; i++) sum += sortedAsc.get(i);
        return sum / n;
    }

    public static class Result {
        public String processName;
        public int pid;
        public long durationMs;
        public int fpsSampleCount;
        public List<Float> fpsTimeline = new ArrayList<>();
        public float avgFps = Float.NaN, minFps = Float.NaN, maxFps = Float.NaN;
        public float low1Fps = Float.NaN, low01Fps = Float.NaN;
        public float maxCpuTempC = Float.NaN, avgCpuTempC = Float.NaN;
        public float maxGpuTempC = Float.NaN, avgGpuTempC = Float.NaN;
        public float maxBatteryTempC = Float.NaN, avgBatteryTempC = Float.NaN;
        public float maxCpuLoadPct = Float.NaN, avgCpuLoadPct = Float.NaN;
        public float maxGpuLoadPct = Float.NaN, avgGpuLoadPct = Float.NaN;
        public long maxRamUsedBytes;
        public long avgRamUsedBytes;

        private static String fps(float v) {
            return Float.isNaN(v) ? "—" : String.format(Locale.ENGLISH, "%.1f", v);
        }
        private static String temp(float v) {
            return Float.isNaN(v) ? "—" : String.format(Locale.ENGLISH, "%.1f °C", v);
        }
        private static String pct(float v) {
            return Float.isNaN(v) ? "—" : String.format(Locale.ENGLISH, "%.0f%%", v);
        }
        private static String bytes(long v) {
            if (v <= 0) return "—";
            float mb = v / (1024f * 1024f);
            if (mb < 1024f) return String.format(Locale.ENGLISH, "%.1f MB", mb);
            return String.format(Locale.ENGLISH, "%.2f GB", mb / 1024f);
        }
        private static String duration(long ms) {
            long s = ms / 1000;
            long m = s / 60;
            return String.format(Locale.ENGLISH, "%d:%02d", m, s % 60);
        }

        /** Localized formatter using app string resources. */
        public String format(Context ctx) {
            return ctx.getString(R.string.profile_result_body,
                    processName == null ? "?" : processName,
                    pid,
                    duration(durationMs),
                    fpsSampleCount,
                    fps(avgFps), fps(minFps), fps(maxFps),
                    fps(low1Fps), fps(low01Fps),
                    temp(avgCpuTempC), temp(maxCpuTempC),
                    temp(avgGpuTempC), temp(maxGpuTempC),
                    temp(avgBatteryTempC), temp(maxBatteryTempC),
                    pct(avgCpuLoadPct), pct(maxCpuLoadPct),
                    pct(avgGpuLoadPct), pct(maxGpuLoadPct),
                    bytes(avgRamUsedBytes), bytes(maxRamUsedBytes));
        }
    }
}
