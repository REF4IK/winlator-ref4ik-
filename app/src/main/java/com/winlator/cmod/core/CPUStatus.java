package com.winlator.cmod.core;

public abstract class CPUStatus {
    public static short[] getCurrentClockSpeeds() {
        int numProcessors = Runtime.getRuntime().availableProcessors();
        short[] clockSpeeds = new short[numProcessors];
        for (int i = 0; i < numProcessors; i++) {
            int currFreq = FileUtils.readInt(SystemSensorPaths.cpuCurFreqPath(i));
            clockSpeeds[i] = (short)(currFreq / 1000);
        }
        return clockSpeeds;
    }

    public static short getMaxClockSpeed(int cpuIndex) {
        int maxFreq = FileUtils.readInt(SystemSensorPaths.cpuMaxFreqPath(cpuIndex));
        return (short)(maxFreq / 1000);
    }
}