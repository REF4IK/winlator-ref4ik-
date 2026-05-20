package com.winlator.cmod.core;

public final class SystemSensorPaths {

    private SystemSensorPaths() {}

    public static final String CPU_PROC_STAT = "/proc/stat";

    public static final String CPU_CUR_FREQ_TEMPLATE =
            "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq";

    public static final String CPU_MAX_FREQ_TEMPLATE =
            "/sys/devices/system/cpu/cpu%d/cpufreq/cpuinfo_max_freq";

    public static final String[] CPU_TEMP_FILES = {
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

    public static final String THERMAL_ZONE_DIR = "/sys/class/thermal";

    public static final String[] GPU_LOAD_FILES = {
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
        "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpubusy",
        "/sys/kernel/gpu/gpu_busy"
    };

    public static final String[] GPU_LOAD_FILE_NAMES = {
        "gpubusy", "gpu_busy", "gpu_load", "load", "busy_percentage",
        "utilisation", "utilization", "gpu_utilization"
    };

    public static final String MALI_GPU_INFO_PATH = "/sys/class/misc/mali0/device/gpuinfo";

    public static final String DEVFREQ_DIR = "/sys/class/devfreq";

    public static final String[] GPU_TEMP_FILES = {
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

    public static final String GPU_GOVERNOR_PATH =
            "/sys/class/kgsl/kgsl-3d0/devfreq/governor";
    public static final String GPU_MAX_FREQ_PATH =
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq";
    public static final String GPU_MIN_FREQ_PATH =
            "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq";
    public static final String GPU_CUR_FREQ_PATH =
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq";
    public static final String GPU_AVAILABLE_FREQS_PATH =
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_frequencies";
    public static final String GPU_AVAILABLE_GOVERNORS_PATH =
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_governors";

    public static final String[] GPU_CUR_FREQ_ALTERNATIVES = {
        "/sys/class/kgsl/kgsl-3d0/gpuclk",
        "/sys/class/kgsl/kgsl-3d0/gpu_clock",
        "/sys/kernel/gpu/gpu_clock",
        "/sys/kernel/gpu/clock",
        "/sys/devices/platform/kgsl-3d0.0/kgsl/kgsl-3d0/gpuclk",
        "/d/clk/gpu/clk_rate"
    };

    public static final String[] GPU_MAX_FREQ_ALTERNATIVES = {
        "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
        "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"
    };

    public static final String[] BATTERY_CURRENT_FILES = {
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/main/current_now",
        "/sys/class/power_supply/usb/current_now",
        "/sys/class/power_supply/battery/current_avg",
        "/sys/class/power_supply/bms/current_avg"
    };

    public static final String[] BATTERY_VOLTAGE_FILES = {
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/bms/voltage_now",
        "/sys/class/power_supply/main/voltage_now",
        "/sys/class/power_supply/battery/voltage_avg",
        "/sys/class/power_supply/bms/voltage_avg"
    };

    public static final String[] BATTERY_POWER_FILES = {
        "/sys/class/power_supply/battery/power_now",
        "/sys/class/power_supply/bms/power_now"
    };

    public static final String HWMON_DIR = "/sys/class/hwmon";

    public static final String CPU_PRESENT_PATH = "/sys/devices/system/cpu/present";

    public static final String CPU_CORE_CTL_GLOBAL_STATE = "/sys/devices/system/cpu/cpu0/core_ctl/global_state";

    public static final String MEDIATEK_GPU_FREQ_DUMP = "/proc/gpufreq/gpufreq_var_dump";

    public static final String MEDIATEK_PMIC_DIR = "/sys/devices/platform/mt-pmic";

    public static final String REGULATOR_DIR = "/sys/class/regulator";

    public static String cpuCurFreqPath(int cpuIndex) {
        return String.format(CPU_CUR_FREQ_TEMPLATE, cpuIndex);
    }

    public static String cpuMaxFreqPath(int cpuIndex) {
        return String.format(CPU_MAX_FREQ_TEMPLATE, cpuIndex);
    }
}
