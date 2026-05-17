package com.winlator.cmod;

public class SectionHeader extends InstalledComponent {
    public SectionHeader(String type) {
        super(type, "", type, 0, 0, null, null);
    }

    @Override
    public boolean isSectionHeader() {
        return true;
    }
}
