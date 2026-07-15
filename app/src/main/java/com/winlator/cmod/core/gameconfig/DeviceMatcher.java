package com.winlator.cmod.core.gameconfig;

import android.os.Build;

import java.util.regex.Pattern;

public class DeviceMatcher {

    private final String deviceModel;
    private final String gpuRenderer;
    private final String socModel;

    public DeviceMatcher() {
        deviceModel = (Build.MANUFACTURER + " " + Build.MODEL).toLowerCase();
        gpuRenderer = getGpuRenderer();
        socModel = getSocModel();
    }

    public DeviceMatcher(String deviceModel, String gpuRenderer) {
        this.deviceModel = deviceModel != null ? deviceModel.toLowerCase() : "";
        this.gpuRenderer = gpuRenderer != null ? gpuRenderer.toLowerCase() : "";
        this.socModel = "";
    }

    private String getGpuRenderer() {
        try {
            String renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
            return renderer != null ? renderer.toLowerCase() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String getSocModel() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                String soc = Build.SOC_MODEL;
                if (soc != null && !soc.isEmpty()) return soc.toLowerCase();
            } catch (Exception ignored) {}
        }
        return Build.HARDWARE != null ? Build.HARDWARE.toLowerCase() : "";
    }

    public static class MatchResult {
        public final boolean deviceMatch;
        public final boolean gpuMatch;
        public final boolean socMatch;
        public final int score;

        MatchResult(boolean device, boolean gpu, boolean soc, int score) {
            this.deviceMatch = device;
            this.gpuMatch = gpu;
            this.socMatch = soc;
            this.score = score;
        }
    }

    public MatchResult match(String configDevice, String configGpu) {
        if (configDevice == null && configGpu == null) {
            return new MatchResult(false, false, false, 0);
        }

        int score = 0;
        boolean deviceMatch = false;
        boolean gpuMatch = false;
        boolean socMatch = false;

        String cDev = configDevice != null ? configDevice.toLowerCase().trim() : "";
        String cGpu = configGpu != null ? configGpu.toLowerCase().trim() : "";

        if (!cDev.isEmpty() && !deviceModel.isEmpty()) {
            if (cDev.equals(deviceModel) || deviceModel.contains(cDev) || cDev.contains(deviceModel)) {
                deviceMatch = true;
                score += 50;
            }
        }

        if (!cGpu.isEmpty() && !gpuRenderer.isEmpty()) {
            String normalizedConfigGpu = normalizeGpuName(cGpu);
            String normalizedUserGpu = normalizeGpuName(gpuRenderer);

            if (normalizedConfigGpu.equals(normalizedUserGpu) ||
                normalizedConfigGpu.contains(normalizedUserGpu) ||
                normalizedUserGpu.contains(normalizedConfigGpu)) {
                gpuMatch = true;
                score += 40;
            }
        }

        if (!socModel.isEmpty() && !cDev.isEmpty() && cDev.contains(socModel)) {
            socMatch = true;
            score += 30;
        }

        return new MatchResult(deviceMatch, gpuMatch, socMatch, score);
    }

    private String normalizeGpuName(String gpu) {
        return gpu
            .replaceAll("\\(tm\\)", "")
            .replaceAll("\\(r\\)", "")
            .replaceAll("[/\\-]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    public static boolean isHigherScore(MatchResult a, MatchResult b) {
        return a.score > b.score;
    }
}
