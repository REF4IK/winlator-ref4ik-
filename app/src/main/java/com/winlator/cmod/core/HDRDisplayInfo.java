package com.winlator.cmod.core;

import android.annotation.SuppressLint;
import android.os.Build;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * HDR Display Information - contains detailed HDR capabilities of a display
 */
public class HDRDisplayInfo {
    private final Display display;
    private final int displayId;
    private final String displayName;
    private final boolean isWideColorGamut;
    private final int hdrCapabilityFlags;
    private final float maxLuminance;
    private final float minLuminance;
    private final float maxAverageLuminance;
    private final List<Integer> supportedColorSpaces;
    private final Display.Mode[] supportedModes;
    private final Display.HdrCapabilities hdrCapabilities;
    
    private HDRDisplayInfo(Builder builder) {
        this.display = builder.display;
        this.displayId = builder.display.getDisplayId();
        this.displayName = builder.displayName;
        this.isWideColorGamut = builder.isWideColorGamut;
        this.hdrCapabilityFlags = builder.hdrCapabilityFlags;
        this.maxLuminance = builder.maxLuminance;
        this.minLuminance = builder.minLuminance;
        this.maxAverageLuminance = builder.maxAverageLuminance;
        this.supportedColorSpaces = new ArrayList<>(builder.supportedColorSpaces);
        this.supportedModes = builder.supportedModes;
        this.hdrCapabilities = builder.hdrCapabilities;
    }
    
    /**
     * Get the associated Display object
     */
    public Display getDisplay() {
        return display;
    }
    
    /**
     * Get display ID
     */
    public int getDisplayId() {
        return displayId;
    }
    
    /**
     * Get display name
     */
    public String getDisplayName() {
        return displayName != null ? displayName : "Display " + displayId;
    }
    
    /**
     * Check if display supports wide color gamut
     */
    public boolean isWideColorGamut() {
        return isWideColorGamut;
    }
    
    /**
     * Check if display supports HDR10
     */
    public boolean supportsHDR10() {
        return (hdrCapabilityFlags & HDRDisplayManager.HDR_CAPABILITY_HDR10) != 0;
    }
    
    /**
     * Check if display supports Dolby Vision
     */
    public boolean supportsDolbyVision() {
        return (hdrCapabilityFlags & HDRDisplayManager.HDR_CAPABILITY_DOLBY_VISION) != 0;
    }
    
    /**
     * Check if display supports HLG (Hybrid Log-Gamma)
     */
    public boolean supportsHLG() {
        return (hdrCapabilityFlags & HDRDisplayManager.HDR_CAPABILITY_HLG) != 0;
    }
    
    /**
     * Check if display supports HDR10+
     */
    public boolean supportsHDR10Plus() {
        return (hdrCapabilityFlags & HDRDisplayManager.HDR_CAPABILITY_HDR10_PLUS) != 0;
    }
    
    /**
     * Get HDR capability flags
     */
    public int getHdrCapabilityFlags() {
        return hdrCapabilityFlags;
    }
    
    /**
     * Get maximum luminance in nits
     */
    public float getMaxLuminance() {
        return maxLuminance;
    }
    
    /**
     * Get minimum luminance in nits
     */
    public float getMinLuminance() {
        return minLuminance;
    }
    
    /**
     * Get maximum average luminance in nits
     */
    public float getMaxAverageLuminance() {
        return maxAverageLuminance;
    }
    
    /**
     * Check if specific color space is supported
     */
    public boolean supportsColorSpace(int colorSpace) {
        return supportedColorSpaces.contains(colorSpace);
    }
    
    /**
     * Get list of supported color spaces
     */
    public List<Integer> getSupportedColorSpaces() {
        return new ArrayList<>(supportedColorSpaces);
    }
    
    /**
     * Get supported display modes
     */
    @Nullable
    public Display.Mode[] getSupportedModes() {
        return supportedModes;
    }
    
    /**
     * Get HDR capabilities object (Android 7.0+)
     */
    @Nullable
    public Display.HdrCapabilities getHdrCapabilities() {
        return hdrCapabilities;
    }
    
    /**
     * Check if any HDR format is supported
     */
    public boolean supportsHDR() {
        return hdrCapabilityFlags != HDRDisplayManager.HDR_CAPABILITY_NONE;
    }
    
    /**
     * Get best supported color space for HDR
     */
    public int getBestHDRColorSpace() {
        if (supportsColorSpace(HDRDisplayManager.COLOR_SPACE_BT2020)) {
            return HDRDisplayManager.COLOR_SPACE_BT2020;
        } else if (supportsColorSpace(HDRDisplayManager.COLOR_SPACE_DISPLAY_P3)) {
            return HDRDisplayManager.COLOR_SPACE_DISPLAY_P3;
        } else {
            return HDRDisplayManager.COLOR_SPACE_SRGB;
        }
    }
    
    /**
     * Get string representation of HDR capabilities
     */
    public String getHdrCapabilitiesString() {
        if (!supportsHDR()) {
            return "None";
        }
        
        List<String> capabilities = new ArrayList<>();
        
        if (supportsHDR10()) {
            capabilities.add("HDR10");
        }
        if (supportsDolbyVision()) {
            capabilities.add("Dolby Vision");
        }
        if (supportsHLG()) {
            capabilities.add("HLG");
        }
        if (supportsHDR10Plus()) {
            capabilities.add("HDR10+");
        }
        
        return String.join(", ", capabilities);
    }
    
    /**
     * Get string representation of supported color spaces
     */
    public String getSupportedColorSpacesString() {
        List<String> colorSpaceNames = new ArrayList<>();
        
        for (int colorSpace : supportedColorSpaces) {
            switch (colorSpace) {
                case HDRDisplayManager.COLOR_SPACE_SRGB:
                    colorSpaceNames.add("sRGB");
                    break;
                case HDRDisplayManager.COLOR_SPACE_DISPLAY_P3:
                    colorSpaceNames.add("Display P3");
                    break;
                case HDRDisplayManager.COLOR_SPACE_BT2020:
                    colorSpaceNames.add("BT.2020");
                    break;
                case HDRDisplayManager.COLOR_SPACE_ADOBE_RGB:
                    colorSpaceNames.add("Adobe RGB");
                    break;
            }
        }
        
        return String.join(", ", colorSpaceNames);
    }
    
    @Override
    public String toString() {
        return "HDRDisplayInfo{" +
                "displayId=" + displayId +
                ", displayName='" + displayName + '\'' +
                ", isWideColorGamut=" + isWideColorGamut +
                ", hdrCapabilities='" + getHdrCapabilitiesString() + '\'' +
                ", maxLuminance=" + maxLuminance +
                ", minLuminance=" + minLuminance +
                ", supportedColorSpaces='" + getSupportedColorSpacesString() + '\'' +
                '}';
    }
    
    /**
     * Builder class for HDRDisplayInfo
     */
    public static class Builder {
        private final Display display;
        private String displayName;
        private boolean isWideColorGamut = false;
        private int hdrCapabilityFlags = HDRDisplayManager.HDR_CAPABILITY_NONE;
        private float maxLuminance = 100.0f; // Standard SDR luminance
        private float minLuminance = 0.1f;
        private float maxAverageLuminance = 100.0f;
        private List<Integer> supportedColorSpaces = new ArrayList<>();
        private Display.Mode[] supportedModes;
        private Display.HdrCapabilities hdrCapabilities;
        
        public Builder(@NonNull Display display) {
            this.display = display;
            this.displayName = getDisplayName(display);
            
            // Initialize with basic sRGB support
            supportedColorSpaces.add(HDRDisplayManager.COLOR_SPACE_SRGB);
        }
        
        @SuppressLint("NewApi")
        private String getDisplayName(Display display) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                return display.getName();
            }
            return "Display " + display.getDisplayId();
        }
        
        public Builder setDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder setWideColorGamut(boolean isWideColorGamut) {
            this.isWideColorGamut = isWideColorGamut;
            return this;
        }
        
        public Builder setHdrCapabilityFlags(int hdrCapabilityFlags) {
            this.hdrCapabilityFlags = hdrCapabilityFlags;
            return this;
        }
        
        public Builder setMaxLuminance(float maxLuminance) {
            this.maxLuminance = maxLuminance;
            return this;
        }
        
        public Builder setMinLuminance(float minLuminance) {
            this.minLuminance = minLuminance;
            return this;
        }
        
        public Builder setMaxAverageLuminance(float maxAverageLuminance) {
            this.maxAverageLuminance = maxAverageLuminance;
            return this;
        }
        
        public Builder setSupportedColorSpaces(List<Integer> supportedColorSpaces) {
            this.supportedColorSpaces = new ArrayList<>(supportedColorSpaces);
            return this;
        }
        
        public Builder addSupportedColorSpace(int colorSpace) {
            if (!supportedColorSpaces.contains(colorSpace)) {
                supportedColorSpaces.add(colorSpace);
            }
            return this;
        }
        
        public Builder setSupportedModes(Display.Mode[] supportedModes) {
            this.supportedModes = supportedModes;
            return this;
        }
        
        public Builder setHdrCapabilities(Display.HdrCapabilities hdrCapabilities) {
            this.hdrCapabilities = hdrCapabilities;
            return this;
        }
        
        public HDRDisplayInfo build() {
            return new HDRDisplayInfo(this);
        }
    }
}