package com.winlator.cmod.core.gameconfig;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ConfigDiffEngine {

    public static class DiffEntry {
        public final String key;
        public final String currentValue;
        public final String newValue;
        public final boolean changed;

        public DiffEntry(String key, String current, String newVal) {
            this.key = key;
            this.currentValue = current;
            this.newValue = newVal;
            this.changed = !stringEquals(current, newVal);
        }

        private static boolean stringEquals(String a, String b) {
            if (a == null && b == null) return true;
            if (a == null) return b.isEmpty();
            if (b == null) return a.isEmpty();
            return a.equals(b);
        }
    }

    public static class DiffResult {
        public final List<DiffEntry> containerDiffs;
        public final List<DiffEntry> shortcutDiffs;
        public final int changedCount;

        public DiffResult(List<DiffEntry> container, List<DiffEntry> shortcut) {
            this.containerDiffs = container;
            this.shortcutDiffs = shortcut;
            this.changedCount = (int) container.stream().filter(d -> d.changed).count() +
                                (int) shortcut.stream().filter(d -> d.changed).count();
        }
    }

    public static DiffResult computeDiff(GameConfig config, Container container, Shortcut shortcut) {
        List<DiffEntry> containerDiffs = new ArrayList<>();
        List<DiffEntry> shortcutDiffs = new ArrayList<>();

        if (config.containerSettings != null) {
            Iterator<String> keys = config.containerSettings.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    String newVal = config.containerSettings.optString(key, "");
                    String currentVal = getContainerField(container, key);
                    containerDiffs.add(new DiffEntry(key, currentVal, newVal));
                } catch (Exception ignored) {}
            }
        }

        if (config.shortcutExtraData != null && shortcut != null) {
            Iterator<String> keys = config.shortcutExtraData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    String newVal = config.shortcutExtraData.optString(key, "");
                    String currentVal = shortcut.getExtra(key);
                    shortcutDiffs.add(new DiffEntry(key, currentVal, newVal));
                } catch (Exception ignored) {}
            }
        }

        return new DiffResult(containerDiffs, shortcutDiffs);
    }

    private static String getContainerField(Container c, String key) {
        switch (key) {
            case "screenSize": return c.getScreenSize();
            case "envVars": return c.getEnvVars();
            case "graphicsDriver": return c.getGraphicsDriver();
            case "graphicsDriverConfig": return c.getGraphicsDriverConfig();
            case "displayRenderer": return c.getDisplayRenderer();
            case "emulator": return c.getEmulator();
            case "dxwrapper": return c.getDXWrapper();
            case "ddrawrapper": return c.getDDrawWrapper();
            case "dxwrapperConfig": return c.getDXWrapperConfig();
            case "audioDriver": return c.getAudioDriver();
            case "audioDriverConfig": return c.getAudioDriverConfig();
            case "wincomponents": return c.getWinComponents();
            case "drives": return c.getDrives();
            case "showFPS": return String.valueOf(c.isShowFPS());
            case "wow64Mode": return String.valueOf(c.isWoW64Mode());
            case "box64Preset": return c.getBox64Preset();
            case "box64Version": return c.getBox64Version();
            case "fexcorePreset": return c.getFEXCorePreset();
            case "fexcoreVersion": return c.getFEXCoreVersion();
            case "desktopTheme": return c.getDesktopTheme();
            case "wineVersion": return c.getWineVersion();
            default: return "";
        }
    }
}
