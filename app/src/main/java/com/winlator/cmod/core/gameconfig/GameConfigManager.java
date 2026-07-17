package com.winlator.cmod.core.gameconfig;

import android.os.Build;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import org.json.JSONException;
import org.json.JSONObject;

public class GameConfigManager {

    public static GameConfig buildGameConfig(Container container, Shortcut shortcut, String description) {
        GameConfig config = new GameConfig();
        config.gameName = shortcut.name;
        config.description = description != null ? description : "";
        config.device = Build.MANUFACTURER + " " + Build.MODEL;
        try {
            config.gpu = com.winlator.cmod.core.GPUInformation.getRenderer();
        } catch (Throwable ignored) {}
        if (config.gpu == null) {
            config.gpu = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
        }
        if (config.gpu == null && Build.VERSION.SDK_INT >= 31) {
            config.gpu = Build.SOC_MODEL;
        }
        if (config.gpu == null || config.gpu.isEmpty()) {
            config.gpu = "Unknown";
        }
        if (config.gpu != null && !config.gpu.equals("Unknown")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("adreno[^0-9]*([0-9]{3})", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(config.gpu);
            if (m.find()) config.gpu = "Adreno " + m.group(1);
        }
        config.exportedAt = System.currentTimeMillis();

        try {
            JSONObject cs = new JSONObject();
            cs.put("wineVersion", container.getWineVersion());
            cs.put("screenSize", container.getScreenSize());
            cs.put("dxwrapper", container.getDXWrapper());
            cs.put("dxwrapperConfig", container.getDXWrapperConfig());
            cs.put("graphicsDriver", container.getGraphicsDriver());
            cs.put("graphicsDriverConfig", container.getGraphicsDriverConfig());
            cs.put("displayRenderer", container.getDisplayRenderer());
            cs.put("audioDriver", container.getAudioDriver());
            cs.put("audioDriverConfig", container.getAudioDriverConfig());
            cs.put("box64Version", container.getBox64Version());
            cs.put("box64Preset", container.getBox64Preset());
            cs.put("fexcoreVersion", container.getFEXCoreVersion());
            cs.put("fexcorePreset", container.getFEXCorePreset());
            cs.put("emulator", container.getEmulator());
            cs.put("envVars", container.getEnvVars());
            config.containerSettings = cs;
        } catch (JSONException e) {
            config.containerSettings = new JSONObject();
        }

        config.shortcutExtraData = new JSONObject();
        try {
            java.util.Iterator<String> keys = shortcut.getExtraKeys();
            if (keys != null) {
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = shortcut.getExtra(key);
                    if (value != null && !value.isEmpty()) {
                        config.shortcutExtraData.put(key, value);
                    }
                }
            }
        } catch (JSONException e) {
        }

        return config;
    }

    public static void applyGameConfig(GameConfig config, Container container, Shortcut shortcut) {
        if (config.containerSettings == null) return;

        try {
            if (shortcut != null) {
                applyToShortcut(config, shortcut);
            } else {
                applyToContainer(config, container);
            }
        } catch (JSONException e) {
        }
    }

    private static void applyToShortcut(GameConfig config, Shortcut shortcut) throws JSONException {
        JSONObject cs = config.containerSettings;

        putShortcutIf(shortcut, "dxwrapper", cs, "dxwrapper");
        putShortcutIf(shortcut, "dxwrapperConfig", cs, "dxwrapperConfig");
        putShortcutIf(shortcut, "graphicsDriver", cs, "graphicsDriver");
        putShortcutIf(shortcut, "graphicsDriverConfig", cs, "graphicsDriverConfig");
        putShortcutIf(shortcut, "displayRenderer", cs, "displayRenderer");
        putShortcutIf(shortcut, "audioDriver", cs, "audioDriver");
        putShortcutIf(shortcut, "audioDriverConfig", cs, "audioDriverConfig");
        putShortcutIf(shortcut, "box64Version", cs, "box64Version");
        putShortcutIf(shortcut, "box64Preset", cs, "box64Preset");
        putShortcutIf(shortcut, "fexcoreVersion", cs, "fexcoreVersion");
        putShortcutIf(shortcut, "fexcorePreset", cs, "fexcorePreset");
        putShortcutIf(shortcut, "emulator", cs, "emulator");
        putShortcutIf(shortcut, "wineVersion", cs, "wineVersion");
        putShortcutIf(shortcut, "screenSize", cs, "screenSize");
        putShortcutIf(shortcut, "envVars", cs, "envVars");
        shortcut.putExtra("cpuList", Container.getFallbackCPUList());
        shortcut.putExtra("cpuListWoW64", Container.getFallbackCPUListWoW64());

        // Apply original shortcutExtraData
        if (config.shortcutExtraData != null) {
            java.util.Iterator<String> keys = config.shortcutExtraData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                shortcut.putExtra(key, config.shortcutExtraData.optString(key));
            }
        }
        shortcut.saveData();
    }

    private static void applyToContainer(GameConfig config, Container container) throws JSONException {
        JSONObject cs = config.containerSettings;

        if (cs.has("dxwrapper")) container.setDXWrapper(cs.getString("dxwrapper"));
        if (cs.has("dxwrapperConfig")) container.setDXWrapperConfig(cs.getString("dxwrapperConfig"));
        if (cs.has("graphicsDriver")) container.setGraphicsDriver(cs.getString("graphicsDriver"));
        if (cs.has("graphicsDriverConfig")) container.setGraphicsDriverConfig(cs.getString("graphicsDriverConfig"));
        if (cs.has("displayRenderer")) container.setDisplayRenderer(cs.getString("displayRenderer"));
        if (cs.has("audioDriver")) container.setAudioDriver(cs.getString("audioDriver"));
        if (cs.has("audioDriverConfig")) container.setAudioDriverConfig(cs.getString("audioDriverConfig"));
        if (cs.has("box64Version")) container.setBox64Version(cs.getString("box64Version"));
        if (cs.has("box64Preset")) container.setBox64Preset(cs.getString("box64Preset"));
        if (cs.has("fexcoreVersion")) container.setFEXCoreVersion(cs.getString("fexcoreVersion"));
        if (cs.has("fexcorePreset")) container.setFEXCorePreset(cs.getString("fexcorePreset"));
        if (cs.has("emulator")) container.setEmulator(cs.getString("emulator"));
        if (cs.has("wineVersion")) container.setWineVersion(cs.getString("wineVersion"));
        if (cs.has("screenSize")) container.setScreenSize(cs.getString("screenSize"));
        if (cs.has("envVars")) container.setEnvVars(cs.getString("envVars"));
        container.setCPUList(Container.getFallbackCPUList());
        container.setCPUListWoW64(Container.getFallbackCPUListWoW64());
        container.saveData();
    }

    private static void putShortcutIf(Shortcut shortcut, String key, JSONObject source, String sourceKey) throws JSONException {
        if (source.has(sourceKey)) {
            shortcut.putExtra(key, source.optString(sourceKey));
        }
    }
}
