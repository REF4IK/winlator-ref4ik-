package com.winlator.cmod.core;

import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SensorReader {

    private static final String TAG = "SensorReader";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private long lastCpuTotal = -1L;
    private long lastCpuIdle = -1L;
    private long lastMaliGpuInfoMs = -1L;
    private long lastMaliGpuInfoWallMs = 0L;

    public String getCpuTemperature() {
        for (String path : SystemSensorPaths.CPU_TEMP_FILES) {
            String temperature = parseTemperature(readFirstLine(path));
            if (temperature != null) {
                return temperature;
            }
        }
        String dynamicTemperature = getDynamicCpuTemperature();
        if (dynamicTemperature != null) {
            return dynamicTemperature;
        }
        String hwmonTemp = getHwmonCpuTemperature();
        if (hwmonTemp != null) {
            return hwmonTemp;
        }
        return "N/A";
    }

    public String getGpuTemperature() {
        for (String path : SystemSensorPaths.GPU_TEMP_FILES) {
            String temperature = parseTemperature(readFirstLine(path));
            if (temperature != null) {
                return temperature;
            }
        }
        String dynamic = getDynamicGpuTemperature();
        if (!"N/A".equals(dynamic)) return dynamic;
        String hwmonTemp = getHwmonGpuTemperature();
        if (hwmonTemp != null) return hwmonTemp;
        return "N/A";
    }

    public String getGpuLoad() {
        for (String path : SystemSensorPaths.GPU_LOAD_FILES) {
            String parsed = parseGpuLoad(readFirstLine(path));
            if (parsed != null) return parsed;
        }
        String maliDelta = getMaliDeltaGpuLoad();
        if (maliDelta != null) return maliDelta;
        String dynamicLoad = getDynamicGpuLoad();
        if (dynamicLoad != null) return dynamicLoad;
        return "N/A";
    }

    public String getCpuLoad() {
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

    public float getBatteryPowerWatts(BatteryManager batteryManager, float batteryVoltage, float batteryCurrent) {
        float directPower = readPowerWattsFromSysfs();
        if (directPower != -1.0f) {
            return directPower;
        }

        float voltage = batteryVoltage != -1.0f ? batteryVoltage : readVoltageVoltsFromSysfs();
        float current = batteryCurrent != -1.0f ? batteryCurrent : readCurrentAmpsFromSysfs(batteryManager);
        if (voltage != -1.0f && current != -1.0f) {
            return voltage * current;
        }
        return -1.0f;
    }

    public float readCurrentAmpsFromSysfs(BatteryManager batteryManager) {
        if (batteryManager != null) {
            int currentMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (currentMicroAmps != Integer.MIN_VALUE) {
                return Math.abs(currentMicroAmps) / 1000000.0f;
            }
        }
        for (String path : SystemSensorPaths.BATTERY_CURRENT_FILES) {
            Long value = readLong(path);
            if (value != null && value != 0L) {
                long absValue = Math.abs(value);
                return absValue > 1000 ? absValue / 1000000.0f : absValue / 1000.0f;
            }
        }
        return -1.0f;
    }

    public void resetCpuState() {
        lastCpuTotal = -1L;
        lastCpuIdle = -1L;
    }

    public void resetMaliState() {
        lastMaliGpuInfoMs = -1L;
        lastMaliGpuInfoWallMs = 0L;
    }

    public void reset() {
        resetCpuState();
        resetMaliState();
    }

    public static Float parseTempCelsius(String formatted) {
        if (formatted == null || formatted.isEmpty() || "N/A".equals(formatted)) return null;
        try {
            String num = formatted.replace("C", "").replace("\u00B0", "").trim();
            return Float.parseFloat(num);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Float parsePercent(String formatted) {
        if (formatted == null || formatted.isEmpty() || "N/A".equals(formatted)) return null;
        try {
            return Float.parseFloat(formatted.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String getDynamicCpuTemperature() {
        File thermalDir = new File(SystemSensorPaths.THERMAL_ZONE_DIR);
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

    @SuppressWarnings("unchecked")
    private String getDynamicGpuTemperature() {
        File thermalDir = new File(SystemSensorPaths.THERMAL_ZONE_DIR);
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

        File devfreqDir = new File(SystemSensorPaths.DEVFREQ_DIR);
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

    private String getMaliDeltaGpuLoad() {
        String raw = readNthLine(SystemSensorPaths.MALI_GPU_INFO_PATH, 1);
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

    private String getDynamicGpuLoad() {
        File devfreqDir = new File(SystemSensorPaths.DEVFREQ_DIR);
        File[] entries = devfreqDir.listFiles();
        if (entries == null) {
            return null;
        }
        for (File entry : entries) {
            String name = entry.getName().toLowerCase(Locale.ENGLISH);
            if (!name.contains("gpu") && !name.contains("kgsl") && !name.contains("mali") && !name.contains("g3d")) {
                continue;
            }
            for (String fileName : SystemSensorPaths.GPU_LOAD_FILE_NAMES) {
                String parsed = parseGpuLoad(readFirstLine(new File(entry, fileName).getAbsolutePath()));
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    public int getMediaTekGpuFreq() {
        String raw = readFile(SystemSensorPaths.MEDIATEK_GPU_FREQ_DUMP);
        if (raw == null || raw.isEmpty()) return -1;
        Pattern p = Pattern.compile("(?:g_freq_new_init_keep|g_cur_gpu_freq)\\s+=\\s+(\\d+)");
        for (String line : raw.split("\n")) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { break; }
            }
        }
        return -1;
    }

    public String getCpuPresent() {
        return readFile(SystemSensorPaths.CPU_PRESENT_PATH);
    }

    public String getCpuCoreCtlState() {
        return readFile(SystemSensorPaths.CPU_CORE_CTL_GLOBAL_STATE);
    }

    private String getHwmonCpuTemperature() {
        File hwmonDir = new File(SystemSensorPaths.HWMON_DIR);
        File[] entries = hwmonDir.listFiles();
        if (entries == null) return null;
        for (File entry : entries) {
            if (!entry.getName().startsWith("hwmon")) continue;
            String name = readFirstLine(new File(entry, "name").getAbsolutePath());
            if (name != null) {
                String nameLower = name.toLowerCase(Locale.ENGLISH);
                if (nameLower.contains("cpu") || nameLower.contains("core") || nameLower.contains("temp") || nameLower.contains("soc")) {
                    String temp = parseHwmonTemp(entry);
                    if (temp != null) return temp;
                }
            }
        }
        return null;
    }

    private String getHwmonGpuTemperature() {
        File hwmonDir = new File(SystemSensorPaths.HWMON_DIR);
        File[] entries = hwmonDir.listFiles();
        if (entries == null) return null;
        for (File entry : entries) {
            if (!entry.getName().startsWith("hwmon")) continue;
            String name = readFirstLine(new File(entry, "name").getAbsolutePath());
            if (name != null) {
                String nameLower = name.toLowerCase(Locale.ENGLISH);
                if (nameLower.contains("gpu") || nameLower.contains("mali") || nameLower.contains("kgsl") || nameLower.contains("g3d")) {
                    String temp = parseHwmonTemp(entry);
                    if (temp != null) return temp;
                }
            }
        }
        return null;
    }

    private String parseHwmonTemp(File hwmonEntry) {
        String temp = parseTemperature(readFirstLine(new File(hwmonEntry, "temp1_input").getAbsolutePath()));
        if (temp != null) return temp;
        File deviceDir = new File(hwmonEntry, "device");
        if (deviceDir.exists()) {
            temp = parseTemperature(readFirstLine(new File(deviceDir, "temp1_input").getAbsolutePath()));
            if (temp != null) return temp;
        }
        return null;
    }

    private String readFile(String path) {
        File file = new File(path);
        if (!file.exists()) return null;
        try (RandomAccessFile reader = new RandomAccessFile(file, "r")) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
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

    private CpuTimes readCpuTimes() {
        File file = new File(SystemSensorPaths.CPU_PROC_STAT);
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

    private static CpuTimes parseCpuTimes(String raw) {
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

    static String parseTemperature(String rawValue) {
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

    static String parseGpuLoad(String rawValue) {
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

    private float readPowerWattsFromSysfs() {
        for (String path : SystemSensorPaths.BATTERY_POWER_FILES) {
            Long value = readLong(path);
            if (value != null && value != 0L) {
                long absValue = Math.abs(value);
                return absValue > 1000 ? absValue / 1000000.0f : absValue;
            }
        }
        return -1.0f;
    }

    private float readVoltageVoltsFromSysfs() {
        for (String path : SystemSensorPaths.BATTERY_VOLTAGE_FILES) {
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

    static String readFirstLine(String path) {
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

    static String readNthLine(String path, int lineIndex) {
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

    private static class CpuTimes {
        final long total;
        final long idle;

        CpuTimes(long total, long idle) {
            this.total = total;
            this.idle = idle;
        }
    }
}
