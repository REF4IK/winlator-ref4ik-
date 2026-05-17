package com.winlator.cmod.core;

import com.winlator.cmod.container.Container;

import java.util.Locale;

public abstract class LsfgQuickMenuHelper {
    public static class Settings {
        public final int multiplier;
        public final float flowScale;
        public final boolean performanceMode;
        public final boolean hdrMode;
        public final String presentMode;
        public final boolean noFp16;

        public Settings(int multiplier, float flowScale, boolean performanceMode) {
            this(multiplier, flowScale, performanceMode, false, LsfgVkManager.PRESENT_MODE_FIFO, false);
        }

        public Settings(int multiplier, float flowScale, boolean performanceMode,
                        boolean hdrMode, String presentMode, boolean noFp16) {
            this.multiplier = sanitizeMultiplier(multiplier);
            this.flowScale = sanitizeFlowScale(flowScale);
            this.performanceMode = performanceMode;
            this.hdrMode = hdrMode;
            this.presentMode = LsfgVkManager.sanitizePresentMode(presentMode);
            this.noFp16 = noFp16;
        }
    }

    public static boolean isAvailable(Container container) {
        return LsfgVkManager.isArmed(container);
    }

    public static Settings readSettings(Container container) {
        return new Settings(
                LsfgVkManager.multiplier(container),
                LsfgVkManager.flowScale(container),
                LsfgVkManager.performanceMode(container),
                LsfgVkManager.hdrMode(container),
                LsfgVkManager.presentMode(container),
                LsfgVkManager.noFp16(container)
        );
    }

    public static int sanitizeMultiplier(int multiplier) {
        return multiplier < 2 ? 0 : Math.max(2, Math.min(4, multiplier));
    }

    public static float sanitizeFlowScale(float flowScale) {
        return Math.max(0.25f, Math.min(1.0f, flowScale));
    }

    public static void applySettings(Container container, Settings settings) {
        int multiplier = sanitizeMultiplier(settings.multiplier);
        float flowScale = sanitizeFlowScale(settings.flowScale);
        String presentMode = LsfgVkManager.sanitizePresentMode(settings.presentMode);

        container.putExtra(LsfgVkManager.EXTRA_MULTIPLIER, String.valueOf(multiplier));
        container.putExtra(LsfgVkManager.EXTRA_FLOW_SCALE, String.format(Locale.US, "%.2f", flowScale));
        container.putExtra(LsfgVkManager.EXTRA_PERFORMANCE_MODE, String.valueOf(settings.performanceMode));
        container.putExtra(LsfgVkManager.EXTRA_HDR_MODE, String.valueOf(settings.hdrMode));
        container.putExtra(LsfgVkManager.EXTRA_PRESENT_MODE, presentMode);
        container.putExtra(LsfgVkManager.EXTRA_NO_FP16, String.valueOf(settings.noFp16));
        container.putExtra(LsfgVkManager.EXTRA_ENABLED, multiplier >= 2 ? "true" : "false");
        container.saveData();

        boolean enabled = multiplier >= 2;
        LsfgVkManager.updateConfigAtRuntime(
                container,
                enabled,
                enabled ? multiplier : 2,
                flowScale,
                settings.performanceMode,
                settings.hdrMode,
                presentMode,
                settings.noFp16
        );
    }
}
