package com.winlator.cmod.core.gameconfig;

import android.content.Context;

import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.DefaultVersion;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ComponentResolver {

    public static class ComponentInfo {
        public final String name;
        public final String version;
        public final boolean installed;
        public final boolean compatible;

        public ComponentInfo(String name, String version, boolean installed, boolean compatible) {
            this.name = name;
            this.version = version;
            this.installed = installed;
            this.compatible = compatible;
        }
    }

    public static List<ComponentInfo> resolveComponents(GameConfig config, Context context) {
        List<ComponentInfo> result = new ArrayList<>();
        ContentsManager contentsManager = new ContentsManager(context);
        contentsManager.syncContents();

        if (config.containerSettings == null) return result;

        result.add(checkComponent(contentsManager, "DXVK", config.containerSettings.optString("dxwrapper", "")));
        result.add(checkComponent(contentsManager, "VKD3D", config.containerSettings.optString("dxwrapperConfig", "")));
        result.add(checkComponent(contentsManager, "Box64", config.containerSettings.optString("box64Version", "")));
        result.add(checkComponent(contentsManager, "FEXCore", config.containerSettings.optString("fexcoreVersion", "")));

        return result;
    }

    private static ComponentInfo checkComponent(ContentsManager mgr, String type, String value) {
        if (value == null || value.isEmpty()) {
            return new ComponentInfo(type, "", true, true);
        }

        boolean installed = isComponentInstalled(mgr, type, value);
        boolean compatible = isComponentCompatible(type, value);

        return new ComponentInfo(type, value, installed, compatible);
    }

    private static boolean isComponentInstalled(ContentsManager mgr, String type, String version) {
        try {
            ContentProfile.ContentType ct = ContentProfile.ContentType.valueOf("CONTENT_TYPE_" + type.toUpperCase());
            java.util.List<ContentProfile> profiles = mgr.getProfiles(ct);
            if (profiles != null) {
                for (ContentProfile p : profiles) {
                    if (p.verName != null && p.verName.contains(version)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isComponentCompatible(String type, String version) {
        return true;
    }
}
