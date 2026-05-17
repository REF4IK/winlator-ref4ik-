package com.winlator.cmod.core;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.steam.service.SteamService;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public abstract class LsfgVkManager {
    private static final String TAG = "LsfgVkManager";

    public static final int LOSSLESS_SCALING_APP_ID = 993090;
    public static final String EXTRA_ENABLED = "lsfgEnabled";
    public static final String EXTRA_MULTIPLIER = "lsfgMultiplier";
    public static final String EXTRA_FLOW_SCALE = "lsfgFlowScale";
    public static final String EXTRA_PERFORMANCE_MODE = "lsfgPerformanceMode";
    public static final String EXTRA_HDR_MODE = "lsfgHdrMode";
    public static final String EXTRA_PRESENT_MODE = "lsfgPresentMode";
    public static final String EXTRA_NO_FP16 = "lsfgNoFp16";

    public static final String PRESENT_MODE_FIFO = "fifo";
    public static final String PRESENT_MODE_MAILBOX = "mailbox";
    public static final String PRESENT_MODE_IMMEDIATE = "immediate";
    private static final String DEFAULT_PRESENT_MODE = PRESENT_MODE_FIFO;

    private static final String LOSSLESS_DLL_NAME = "Lossless.dll";
    private static final String CONFIG_RELATIVE_PATH = ".config/lsfg-vk/conf.toml";
    private static final String LIB_RELATIVE_DIR = ".local/lib";
    private static final String LAYER_RELATIVE_DIR = ".local/share/vulkan/implicit_layer.d";
    private static final String DLL_RELATIVE_DIR = ".local/share/lsfg-vk";
    private static final String LIB_FILENAME = "liblsfg-vk-layer.so";
    private static final String MANIFEST_FILENAME = "VkLayer_LS_frame_generation.json";
    private static final String VERSION_FILENAME = ".lsfg_vk_runtime_version";
    private static final String PROCESS_EXE_IDENTIFIER = "winlator-lsfg";
    private static final String RUNTIME_VERSION = "v1.4.0-android-arm64-v8a-ahb-no-props";
    private static final String ASSET_DIR = "lsfg_vk/android_arm64_v8a";
    private static final String ASSET_LIB = ASSET_DIR + "/" + LIB_FILENAME;
    private static final String ASSET_MANIFEST = ASSET_DIR + "/" + MANIFEST_FILENAME;
    private static final String ASSET_DLL = "lsfg_vk/" + LOSSLESS_DLL_NAME;

    public static boolean isDllAvailable() {
        return findSteamDll() != null;
    }

    public static boolean isGlobalDllAvailable(Context context) {
        File dllFile = globalDllFile(context);
        return dllFile != null && dllFile.isFile() && dllFile.length() > 0;
    }

    public static boolean isBundledDllAvailable(Context context) {
        return bundledDllSize(context) > 0;
    }

    public static File globalDllFile(Context context) {
        if (context == null) return null;
        return new File(context.getFilesDir(), "lsfg-vk/" + LOSSLESS_DLL_NAME);
    }

    public static String globalDllPath(Context context) {
        File dllFile = globalDllFile(context);
        return dllFile != null && dllFile.isFile() && dllFile.length() > 0 ? dllFile.getAbsolutePath() : null;
    }

    public static boolean importGlobalLosslessDll(Context context, Uri uri) {
        File dllFile = globalDllFile(context);
        if (context == null || dllFile == null || uri == null) return false;

        File parent = dllFile.getParentFile();
        if (parent != null) parent.mkdirs();

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new java.io.FileOutputStream(dllFile)) {
            if (inputStream == null) return false;
            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            FileUtils.chmod(dllFile, 0644);
            Log.i(TAG, "Imported global Lossless.dll to " + dllFile.getAbsolutePath() + " size=" + dllFile.length());
            return dllFile.isFile() && dllFile.length() > 0;
        }
        catch (Throwable t) {
            Log.e(TAG, "Failed to import global Lossless.dll", t);
            return false;
        }
    }

    public static boolean ownsLosslessScaling() {
        try {
            return SteamService.Companion.getAppInfoOf(LOSSLESS_SCALING_APP_ID) != null;
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static boolean isEnabled(Container container) {
        return container != null && parseBool(container.getExtra(EXTRA_ENABLED, "false"));
    }

    public static boolean isArmed(Container container) {
        return isEnabled(container) && (containerDllPath(container) != null || isDllAvailable());
    }

    public static int multiplier(Container container) {
        int value = parseInt(container != null ? container.getExtra(EXTRA_MULTIPLIER, "0") : "0", 0);
        if (value == 0) return 0;
        return Math.max(2, Math.min(4, value));
    }

    public static float flowScale(Container container) {
        float value = parseFloat(container != null ? container.getExtra(EXTRA_FLOW_SCALE, "0.30") : "0.30", 0.30f);
        return Math.max(0.25f, Math.min(1.0f, value));
    }

    public static boolean performanceMode(Container container) {
        return container != null && parseBool(container.getExtra(EXTRA_PERFORMANCE_MODE, "false"));
    }

    public static boolean hdrMode(Container container) {
        return container != null && parseBool(container.getExtra(EXTRA_HDR_MODE, "false"));
    }

    public static String presentMode(Container container) {
        if (container == null) return DEFAULT_PRESENT_MODE;
        String raw = container.getExtra(EXTRA_PRESENT_MODE, DEFAULT_PRESENT_MODE);
        return sanitizePresentMode(raw);
    }

    public static boolean noFp16(Container container) {
        return container != null && parseBool(container.getExtra(EXTRA_NO_FP16, "false"));
    }

    public static String sanitizePresentMode(String raw) {
        if (raw == null) return DEFAULT_PRESENT_MODE;
        String value = raw.trim().toLowerCase(Locale.US);
        if (PRESENT_MODE_MAILBOX.equals(value) || PRESENT_MODE_IMMEDIATE.equals(value) || PRESENT_MODE_FIFO.equals(value)) {
            return value;
        }
        return DEFAULT_PRESENT_MODE;
    }

    public static String containerDllPath(Container container) {
        if (container == null || container.getRootDir() == null) return null;
        File dllFile = new File(container.getRootDir(), DLL_RELATIVE_DIR + "/" + LOSSLESS_DLL_NAME);
        return dllFile.isFile() ? dllFile.getAbsolutePath() : null;
    }

    public static File containerDllFile(Container container) {
        if (container == null || container.getRootDir() == null) return null;
        return new File(container.getRootDir(), DLL_RELATIVE_DIR + "/" + LOSSLESS_DLL_NAME);
    }

    public static boolean importLosslessDll(Context context, Container container, Uri uri) {
        File dllFile = containerDllFile(container);
        if (context == null || dllFile == null || uri == null) return false;

        File parent = dllFile.getParentFile();
        if (parent != null) parent.mkdirs();

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new java.io.FileOutputStream(dllFile)) {
            if (inputStream == null) return false;
            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            FileUtils.chmod(dllFile, 0644);
            Log.i(TAG, "Imported Lossless.dll to " + dllFile.getAbsolutePath() + " size=" + dllFile.length());
            return dllFile.isFile() && dllFile.length() > 0;
        }
        catch (Throwable t) {
            Log.e(TAG, "Failed to import Lossless.dll", t);
            return false;
        }
    }

    public static boolean ensureRuntimeInstalled(Context context, Container container) {
        if (context == null || container == null || container.getRootDir() == null) return false;

        File rootDir = container.getRootDir();
        File localLibDir = new File(rootDir, LIB_RELATIVE_DIR);
        File layerDir = new File(rootDir, LAYER_RELATIVE_DIR);
        File dllDir = new File(rootDir, DLL_RELATIVE_DIR);
        File libFile = new File(localLibDir, LIB_FILENAME);
        File manifestFile = new File(layerDir, MANIFEST_FILENAME);
        File versionFile = new File(layerDir, VERSION_FILENAME);

        String installedVersion = versionFile.isFile() ? FileUtils.readString(versionFile).trim() : "";
        boolean needsInstall = !RUNTIME_VERSION.equals(installedVersion) || !libFile.isFile() || !manifestFile.isFile();
        boolean success = true;

        if (needsInstall) {
            try {
                localLibDir.mkdirs();
                layerDir.mkdirs();
                FileUtils.copy(context, ASSET_LIB, libFile);
                FileUtils.copy(context, ASSET_MANIFEST, manifestFile);
                FileUtils.writeString(versionFile, RUNTIME_VERSION);
                FileUtils.chmod(libFile, 0755);
                FileUtils.chmod(manifestFile, 0644);
                FileUtils.chmod(versionFile, 0644);
                success = libFile.isFile() && manifestFile.isFile();
            }
            catch (Throwable t) {
                Log.e(TAG, "Failed to install LSFG runtime", t);
                success = false;
            }
        }

        File steamDll = findSteamDll();
        File globalDll = globalDllFile(context);
        File sourceDll = steamDll != null ? steamDll : globalDll != null && globalDll.isFile() ? globalDll : null;
        File dllFile = new File(dllDir, LOSSLESS_DLL_NAME);
        if (sourceDll != null) {
            try {
                if (!dllFile.isFile() || dllFile.length() != sourceDll.length()) {
                    dllDir.mkdirs();
                    FileUtils.copy(sourceDll, dllFile);
                    FileUtils.chmod(dllFile, 0644);
                }
            }
            catch (Throwable t) {
                Log.e(TAG, "Failed to copy Lossless.dll into container", t);
                success = false;
            }
        }
        else if (isBundledDllAvailable(context)) {
            try {
                long bundledDllSize = bundledDllSize(context);
                if (!dllFile.isFile() || dllFile.length() != bundledDllSize) {
                    dllDir.mkdirs();
                    FileUtils.copy(context, ASSET_DLL, dllFile);
                    FileUtils.chmod(dllFile, 0644);
                }
            }
            catch (Throwable t) {
                Log.e(TAG, "Failed to copy bundled Lossless.dll into container", t);
                success = false;
            }
        }
        else if (isEnabled(container)) {
            success = false;
        }

        return success;
    }

    public static boolean writeConfig(Container container) {
        if (container == null || container.getRootDir() == null) return false;

        String dllPath = containerDllPath(container);
        boolean enabled = isEnabled(container) && dllPath != null;
        File configFile = configFile(container);
        File parent = configFile.getParentFile();
        if (parent != null) parent.mkdirs();

        boolean ok = FileUtils.writeString(configFile, buildConfigToml(
                dllPath,
                enabled,
                multiplier(container),
                flowScale(container),
                performanceMode(container),
                hdrMode(container),
                presentMode(container),
                noFp16(container)
        ));
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    public static boolean applyLaunchEnv(Container container, EnvVars envVars) {
        if (container == null || envVars == null || container.getRootDir() == null) return false;

        envVars.remove("DISABLE_LSFG");
        envVars.remove("LSFG_CONFIG");
        envVars.remove("LSFG_PROCESS");

        String dllPath = containerDllPath(container);
        boolean armed = isEnabled(container) && dllPath != null;
        if (!armed) {
            disableLayerInContainer(container);
            envVars.put("DISABLE_LSFG", "1");
            return false;
        }

        File layerDir = new File(container.getRootDir(), LAYER_RELATIVE_DIR);
        File manifestFile = new File(layerDir, MANIFEST_FILENAME);
        if (!manifestFile.isFile()) return false;

        envVars.put("LSFG_CONFIG", configFile(container).getAbsolutePath());
        envVars.put("LSFG_PROCESS", PROCESS_EXE_IDENTIFIER);

        String currentLayerPath = envVars.get("VK_LAYER_PATH");
        String layerPath = layerDir.getAbsolutePath();
        envVars.put("VK_LAYER_PATH", currentLayerPath.isEmpty() ? layerPath : currentLayerPath + ":" + layerPath);
        Log.i(TAG, "LSFG armed with multiplier=" + multiplier(container));
        return true;
    }

    public static boolean updateConfigAtRuntime(Container container, boolean enabled, int multiplier, float flowScale, boolean performanceMode) {
        return updateConfigAtRuntime(container, enabled, multiplier, flowScale, performanceMode,
                hdrMode(container), presentMode(container), noFp16(container));
    }

    public static boolean updateConfigAtRuntime(Container container, boolean enabled, int multiplier,
                                                float flowScale, boolean performanceMode,
                                                boolean hdrMode, String presentMode, boolean noFp16) {
        if (container == null || container.getRootDir() == null) return false;

        String dllPath = containerDllPath(container);
        File configFile = configFile(container);
        if (!configFile.isFile()) return false;

        int effectiveMultiplier = enabled && dllPath != null ? Math.max(2, Math.min(4, multiplier)) : 1;
        boolean ok = FileUtils.writeString(configFile, buildConfigToml(
                dllPath,
                true,
                effectiveMultiplier,
                flowScale,
                performanceMode && enabled,
                hdrMode,
                sanitizePresentMode(presentMode),
                noFp16
        ));
        if (ok) FileUtils.chmod(configFile, 0644);
        return ok;
    }

    private static void disableLayerInContainer(Container container) {
        File manifest = new File(container.getRootDir(), LAYER_RELATIVE_DIR + "/" + MANIFEST_FILENAME);
        if (manifest.exists() && !manifest.delete()) {
            Log.w(TAG, "Failed to remove disabled LSFG manifest: " + manifest);
        }
    }

    private static File findSteamDll() {
        try {
            File dll = new File(SteamService.Companion.getAppDirPath(LOSSLESS_SCALING_APP_ID), LOSSLESS_DLL_NAME);
            return dll.isFile() ? dll : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    private static long bundledDllSize(Context context) {
        if (context == null) return 0;
        try (InputStream inputStream = context.getAssets().open(ASSET_DLL)) {
            byte[] buffer = new byte[StreamUtils.BUFFER_SIZE];
            long total = 0;
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                total += length;
            }
            return total;
        }
        catch (Throwable t) {
            return 0;
        }
    }

    private static File configFile(Container container) {
        return new File(container.getRootDir(), CONFIG_RELATIVE_PATH);
    }

    private static String buildConfigToml(String dllPath, boolean enabled, int multiplier, float flowScale,
                                          boolean performanceMode, boolean hdrMode, String presentMode, boolean noFp16) {
        StringBuilder builder = new StringBuilder();
        builder.append("version = 1\n\n");
        builder.append("[global]\n");
        if (dllPath != null && !dllPath.isEmpty()) {
            builder.append("dll = ").append(tomlString(dllPath)).append("\n");
        }
        builder.append("no_fp16 = ").append(noFp16 ? "true" : "false").append("\n\n");

        if (enabled && dllPath != null && !dllPath.isEmpty()) {
            builder.append("[[game]]\n");
            builder.append("exe = ").append(tomlString(PROCESS_EXE_IDENTIFIER)).append("\n");
            builder.append("multiplier = ").append(Math.max(1, Math.min(4, multiplier))).append("\n");
            builder.append("flow_scale = ").append(String.format(Locale.US, "%.2f", Math.max(0.25f, Math.min(1.0f, flowScale)))).append("\n");
            builder.append("performance_mode = ").append(performanceMode ? "true" : "false").append("\n");
            builder.append("hdr_mode = ").append(hdrMode ? "true" : "false").append("\n");
            builder.append("experimental_present_mode = ").append(tomlString(sanitizePresentMode(presentMode))).append("\n");
        }
        return builder.toString();
    }

    private static String tomlString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean parseBool(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }
}
