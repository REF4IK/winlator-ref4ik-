package com.winlator.cmod;

import com.winlator.cmod.contents.ContentProfile;

public class InstalledComponent {
    public enum ComponentCategory {
        CONTENT,
        ADRENOTOOLS,
        SOUNDFONT
    }

    public String name;
    public String version;
    public String type;
    public long sizeBytes;
    public int iconResId;
    public ComponentCategory category;
    public Object identifier;

    public InstalledComponent(String name, String version, String type, long sizeBytes, int iconResId, ComponentCategory category, Object identifier) {
        this.name = name;
        this.version = version;
        this.type = type;
        this.sizeBytes = sizeBytes;
        this.iconResId = iconResId;
        this.category = category;
        this.identifier = identifier;
    }

    public boolean isSectionHeader() {
        return false;
    }
}
