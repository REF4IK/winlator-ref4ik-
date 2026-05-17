package com.winlator.cmod.core;

/**
 * HDR Configuration - manages HDR10 rendering configuration
 */
public class HDRConfiguration {
    // HDR modes
    public static final int HDR_MODE_DISABLED = 0;
    public static final int HDR_MODE_AUTO = 1;
    public static final int HDR_MODE_HDR10 = 2;
    public static final int HDR_MODE_DOLBY_VISION = 3;
    public static final int HDR_MODE_HLG = 4;
    public static final int HDR_MODE_HDR10_PLUS = 5;
    
    // Tone mapping modes
    public static final int TONE_MAPPING_DISABLED = 0;
    public static final int TONE_MAPPING_REINHARD = 1;
    public static final int TONE_MAPPING_HABLE = 2;
    public static final int TONE_MAPPING_ACES = 3;
    public static final int TONE_MAPPING_AUTO = 4;
    
    private final int hdrMode;
    private final int colorSpace;
    private final float maxLuminance;
    private final float minLuminance;
    private final float maxAverageLuminance;
    private final int toneMapping;
    private final float gamma;
    private final boolean enableBitDepth10;
    private final boolean enableWideColorGamut;
    private final float saturationBoost;
    private final float contrastEnhancement;
    
    private HDRConfiguration(Builder builder) {
        this.hdrMode = builder.hdrMode;
        this.colorSpace = builder.colorSpace;
        this.maxLuminance = builder.maxLuminance;
        this.minLuminance = builder.minLuminance;
        this.maxAverageLuminance = builder.maxAverageLuminance;
        this.toneMapping = builder.toneMapping;
        this.gamma = builder.gamma;
        this.enableBitDepth10 = builder.enableBitDepth10;
        this.enableWideColorGamut = builder.enableWideColorGamut;
        this.saturationBoost = builder.saturationBoost;
        this.contrastEnhancement = builder.contrastEnhancement;
    }
    
    /**
     * Create default SDR configuration
     */
    public static HDRConfiguration createSDRConfiguration() {
        return new Builder()
                .setHdrMode(HDR_MODE_DISABLED)
                .setColorSpace(HDRDisplayManager.COLOR_SPACE_SRGB)
                .setMaxLuminance(100.0f)
                .setMinLuminance(0.1f)
                .setMaxAverageLuminance(100.0f)
                .setToneMapping(TONE_MAPPING_DISABLED)
                .setGamma(2.2f)
                .setEnableBitDepth10(false)
                .setEnableWideColorGamut(false)
                .build();
    }
    
    /**
     * Create default HDR10 configuration
     */
    public static HDRConfiguration createHDR10Configuration() {
        return new Builder()
                .setHdrMode(HDR_MODE_HDR10)
                .setColorSpace(HDRDisplayManager.COLOR_SPACE_BT2020)
                .setMaxLuminance(1000.0f)
                .setMinLuminance(0.01f)
                .setMaxAverageLuminance(400.0f)
                .setToneMapping(TONE_MAPPING_AUTO)
                .setGamma(2.4f)
                .setEnableBitDepth10(true)
                .setEnableWideColorGamut(true)
                .setSaturationBoost(1.1f)
                .setContrastEnhancement(1.2f)
                .build();
    }
    
    // Getters
    public int getHdrMode() { return hdrMode; }
    public int getColorSpace() { return colorSpace; }
    public float getMaxLuminance() { return maxLuminance; }
    public float getMinLuminance() { return minLuminance; }
    public float getMaxAverageLuminance() { return maxAverageLuminance; }
    public int getToneMapping() { return toneMapping; }
    public float getGamma() { return gamma; }
    public boolean isEnableBitDepth10() { return enableBitDepth10; }
    public boolean isEnableWideColorGamut() { return enableWideColorGamut; }
    public float getSaturationBoost() { return saturationBoost; }
    public float getContrastEnhancement() { return contrastEnhancement; }
    
    /**
     * Check if HDR is enabled
     */
    public boolean isHDREnabled() {
        return hdrMode != HDR_MODE_DISABLED;
    }
    
    /**
     * Check if tone mapping is enabled
     */
    public boolean isToneMappingEnabled() {
        return toneMapping != TONE_MAPPING_DISABLED;
    }
    
    /**
     * Get HDR mode name
     */
    public String getHdrModeName() {
        switch (hdrMode) {
            case HDR_MODE_DISABLED: return "Disabled";
            case HDR_MODE_AUTO: return "Auto";
            case HDR_MODE_HDR10: return "HDR10";
            case HDR_MODE_DOLBY_VISION: return "Dolby Vision";
            case HDR_MODE_HLG: return "HLG";
            case HDR_MODE_HDR10_PLUS: return "HDR10+";
            default: return "Unknown";
        }
    }
    
    /**
     * Get color space name
     */
    public String getColorSpaceName() {
        switch (colorSpace) {
            case HDRDisplayManager.COLOR_SPACE_SRGB: return "sRGB";
            case HDRDisplayManager.COLOR_SPACE_DISPLAY_P3: return "Display P3";
            case HDRDisplayManager.COLOR_SPACE_BT2020: return "BT.2020";
            case HDRDisplayManager.COLOR_SPACE_ADOBE_RGB: return "Adobe RGB";
            default: return "Unknown";
        }
    }
    
    /**
     * Get tone mapping name
     */
    public String getToneMappingName() {
        switch (toneMapping) {
            case TONE_MAPPING_DISABLED: return "Disabled";
            case TONE_MAPPING_REINHARD: return "Reinhard";
            case TONE_MAPPING_HABLE: return "Hable/Uncharted";
            case TONE_MAPPING_ACES: return "ACES";
            case TONE_MAPPING_AUTO: return "Auto";
            default: return "Unknown";
        }
    }
    
    @Override
    public String toString() {
        return "HDRConfiguration{" +
                "hdrMode=" + getHdrModeName() +
                ", colorSpace=" + getColorSpaceName() +
                ", maxLuminance=" + maxLuminance +
                ", minLuminance=" + minLuminance +
                ", toneMapping=" + getToneMappingName() +
                ", gamma=" + gamma +
                ", enableBitDepth10=" + enableBitDepth10 +
                ", enableWideColorGamut=" + enableWideColorGamut +
                '}';
    }
    
    /**
     * Builder class for HDRConfiguration
     */
    public static class Builder {
        private int hdrMode = HDR_MODE_DISABLED;
        private int colorSpace = HDRDisplayManager.COLOR_SPACE_SRGB;
        private float maxLuminance = 100.0f;
        private float minLuminance = 0.1f;
        private float maxAverageLuminance = 100.0f;
        private int toneMapping = TONE_MAPPING_DISABLED;
        private float gamma = 2.2f;
        private boolean enableBitDepth10 = false;
        private boolean enableWideColorGamut = false;
        private float saturationBoost = 1.0f;
        private float contrastEnhancement = 1.0f;
        
        public Builder setHdrMode(int hdrMode) {
            this.hdrMode = hdrMode;
            return this;
        }
        
        public Builder setColorSpace(int colorSpace) {
            this.colorSpace = colorSpace;
            return this;
        }
        
        public Builder setMaxLuminance(float maxLuminance) {
            this.maxLuminance = Math.max(100.0f, Math.min(10000.0f, maxLuminance));
            return this;
        }
        
        public Builder setMinLuminance(float minLuminance) {
            this.minLuminance = Math.max(0.001f, Math.min(1.0f, minLuminance));
            return this;
        }
        
        public Builder setMaxAverageLuminance(float maxAverageLuminance) {
            this.maxAverageLuminance = Math.max(100.0f, Math.min(4000.0f, maxAverageLuminance));
            return this;
        }
        
        public Builder setToneMapping(int toneMapping) {
            this.toneMapping = toneMapping;
            return this;
        }
        
        public Builder setGamma(float gamma) {
            this.gamma = Math.max(1.0f, Math.min(3.0f, gamma));
            return this;
        }
        
        public Builder setEnableBitDepth10(boolean enableBitDepth10) {
            this.enableBitDepth10 = enableBitDepth10;
            return this;
        }
        
        public Builder setEnableWideColorGamut(boolean enableWideColorGamut) {
            this.enableWideColorGamut = enableWideColorGamut;
            return this;
        }
        
        public Builder setSaturationBoost(float saturationBoost) {
            this.saturationBoost = Math.max(0.5f, Math.min(2.0f, saturationBoost));
            return this;
        }
        
        public Builder setContrastEnhancement(float contrastEnhancement) {
            this.contrastEnhancement = Math.max(0.5f, Math.min(2.0f, contrastEnhancement));
            return this;
        }
        
        public HDRConfiguration build() {
            return new HDRConfiguration(this);
        }
    }
}