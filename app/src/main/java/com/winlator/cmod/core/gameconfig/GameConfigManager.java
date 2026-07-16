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
        config.gpu = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER);
        config.gpu = config.gpu != null ? config.gpu : "Unknown";
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
        if (shortcut != null && config.shortcutExtraData != null) {
            java.util.Iterator<String> keys = config.shortcutExtraData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    String value = config.shortcutExtraData.getString(key);
                    shortcut.putExtra(key, value);
                } catch (JSONException e) {
                }
            }
            shortcut.saveData();
        } else if (shortcut == null && config.containerSettings != null) {
            try {
                if (config.containerSettings.has("dxwrapper"))
                    container.setDXWrapper(config.containerSettings.getString("dxwrapper"));
                if (config.containerSettings.has("dxwrapperConfig"))
                    container.setDXWrapperConfig(config.containerSettings.getString("dxwrapperConfig"));
                if (config.containerSettings.has("graphicsDriver"))
                    container.setGraphicsDriver(config.containerSettings.getString("graphicsDriver"));
                if (config.containerSettings.has("graphicsDriverConfig"))
                    container.setGraphicsDriverConfig(config.containerSettings.getString("graphicsDriverConfig"));
                if (config.containerSettings.has("displayRenderer"))
                    container.setDisplayRenderer(config.containerSettings.getString("displayRenderer"));
                if (config.containerSettings.has("audioDriver"))
                    container.setAudioDriver(config.containerSettings.getString("audioDriver"));
                if (config.containerSettings.has("audioDriverConfig"))
                    container.setAudioDriverConfig(config.containerSettings.getString("audioDriverConfig"));
                if (config.containerSettings.has("box64Version"))
                    container.setBox64Version(config.containerSettings.getString("box64Version"));
                if (config.containerSettings.has("box64Preset"))
                    container.setBox64Preset(config.containerSettings.getString("box64Preset"));
                if (config.containerSettings.has("fexcoreVersion"))
                    container.setFEXCoreVersion(config.containerSettings.getString("fexcoreVersion"));
                if (config.containerSettings.has("fexcorePreset"))
                    container.setFEXCorePreset(config.containerSettings.getString("fexcorePreset"));
                if (config.containerSettings.has("emulator"))
                    container.setEmulator(config.containerSettings.getString("emulator"));
                if (config.containerSettings.has("wineVersion"))
                    container.setWineVersion(config.containerSettings.getString("wineVersion"));
                if (config.containerSettings.has("screenSize"))
                    container.setScreenSize(config.containerSettings.getString("screenSize"));
                if (config.containerSettings.has("envVars"))
                    container.setEnvVars(config.containerSettings.getString("envVars"));
                container.setCPUList(Container.getFallbackCPUList());
                container.setCPUListWoW64(Container.getFallbackCPUListWoW64());
                container.saveData();
            } catch (JSONException e) {
            }
        }
    }
}
