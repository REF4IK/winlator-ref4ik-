package com.winlator.cmod.core;

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

    public native static String getVersion();
    public native static String getRenderer();
    public native static long getMemorySize();
    public native static String[] enumerateExtensions();
    
    // Adreno GPU Turbo Mode (Eden-style)
    public native static boolean setTurboMode(boolean enable);
    public native static boolean isTurboModeActive();
    public native static boolean isAdrenoGPU();

    static {
        System.loadLibrary("winlator");
    }
}
