package com.winlator.cmod.core;

import android.content.Context;

import java.util.Locale;

public abstract class GPUInformation {

    public static boolean isAdreno6xx() {
        return getRenderer().toLowerCase(Locale.ENGLISH).matches(".*adreno[^6]+6[0-9]{2}.*");
    }

    public static boolean isAdreno7xx() {
        return getRenderer().toLowerCase(Locale.ENGLISH).matches(".*adreno[^7]+7[0-9]{2}.*");
    }

    public static boolean isAdreno8xx() {
        return getRenderer().toLowerCase(Locale.ENGLISH).matches(".*adreno[^8]+8[0-9]{2}.*");
    }

    /**
     * Проверяет, является ли GPU Adreno. Если передан context и driverName,
     * проверка выполняется для указанного драйвера (динамически).
     */
    public static boolean isAdrenoGPU(Context context) {
        return getRenderer(null, context).toLowerCase(Locale.ENGLISH).contains("adreno");
    }

    /**
     * Проверяет, поддерживается ли драйвер на текущем устройстве.
     * Не-Adreno GPU поддерживает только "System" драйвер.
     */
    public static boolean isDriverSupported(String driverName, Context context) {
        if (!isAdrenoGPU(context) && !driverName.equals("System"))
            return false;

        String renderer = getRenderer(driverName, context);
        return !renderer.toLowerCase(Locale.ENGLISH).contains("unknown");
    }

    // --- Native-методы БЕЗ драйвера (обратная совместимость) ---

    public native static String getVersion();
    public native static int getVendorID();
    public native static String getRenderer();
    public native static long getMemorySize();
    public native static String[] enumerateExtensions();

    // --- Native-методы С драйвером (динамические расширения) ---
    // Загружают указанный драйвер через adrenotools и возвращают
    // информацию/расширения именно для этого драйвера.

    public native static String getVersionWithDriver(String driverName, Context context);
    public native static int getVendorIDWithDriver(String driverName, Context context);
    public native static String getRendererWithDriver(String driverName, Context context);
    public native static String[] enumerateExtensionsWithDriver(String driverName, Context context);

    /**
     * Удобные Java-обёртки: если driverName == null/"System"/пустой —
     * возвращают информацию о системном драйвере (через старые native-методы).
     * Иначе — загружают указанный драйвер и возвращают его данные.
     */
    public static String getRenderer(String driverName, Context context) {
        if (driverName == null || driverName.isEmpty() || driverName.equalsIgnoreCase("System"))
            return getRenderer();
        try {
            return getRendererWithDriver(driverName, context);
        } catch (Throwable t) {
            return getRenderer();
        }
    }

    public static String getVersion(String driverName, Context context) {
        if (driverName == null || driverName.isEmpty() || driverName.equalsIgnoreCase("System"))
            return getVersion();
        try {
            return getVersionWithDriver(driverName, context);
        } catch (Throwable t) {
            return getVersion();
        }
    }

    public static int getVendorID(String driverName, Context context) {
        if (driverName == null || driverName.isEmpty() || driverName.equalsIgnoreCase("System"))
            return getVendorID();
        try {
            return getVendorIDWithDriver(driverName, context);
        } catch (Throwable t) {
            return getVendorID();
        }
    }

    public static String[] enumerateExtensions(String driverName, Context context) {
        if (driverName == null || driverName.isEmpty() || driverName.equalsIgnoreCase("System"))
            return enumerateExtensions();
        try {
            return enumerateExtensionsWithDriver(driverName, context);
        } catch (Throwable t) {
            return enumerateExtensions();
        }
    }

    // Adreno GPU Turbo Mode (Eden-style)
    public native static boolean setTurboMode(boolean enable);
    public native static boolean isTurboModeActive();
    public native static boolean isAdrenoGPU();

    static {
        System.loadLibrary("winlator");
    }
}
