package com.winlator.cmod.core;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

/**
 * HDR10 Metadata Handler - manages HDR10 static and dynamic metadata
 * 
 * Supports:
 * - Static metadata (SMPTE ST 2086, CTA-861.3)
 * - Dynamic metadata (SMPTE ST 2094-40, HDR10+)
 * - Mastering display information
 * - Content light level information
 */
public class HDR10MetadataHandler {
    private static final String TAG = "HDR10MetadataHandler";
    
    // HDR10 Static Metadata Type 1 (SMPTE ST 2086)
    public static class StaticMetadata {
        // Mastering display color primaries (in 0.00002 increments)
        public int displayPrimaryRed_x;     // Red primary x chromaticity coordinate
        public int displayPrimaryRed_y;     // Red primary y chromaticity coordinate
        public int displayPrimaryGreen_x;   // Green primary x chromaticity coordinate
        public int displayPrimaryGreen_y;   // Green primary y chromaticity coordinate
        public int displayPrimaryBlue_x;    // Blue primary x chromaticity coordinate
        public int displayPrimaryBlue_y;    // Blue primary y chromaticity coordinate
        public int whitePoint_x;            // White point x chromaticity coordinate
        public int whitePoint_y;            // White point y chromaticity coordinate
        
        // Mastering display luminance (in 0.0001 cd/m² increments)
        public int maxDisplayMasteringLuminance;  // Maximum luminance
        public int minDisplayMasteringLuminance;  // Minimum luminance
        
        // Content light level information (in cd/m²)
        public int maxContentLightLevel;          // Maximum content light level
        public int maxFrameAverageLightLevel;     // Maximum frame-average light level
        
        /**
         * Create standard BT.2020 metadata
         */
        public static StaticMetadata createBT2020Metadata() {
            StaticMetadata metadata = new StaticMetadata();
            
            // BT.2020 color primaries (converted to 0.00002 increments)
            metadata.displayPrimaryRed_x = 35400;     // 0.708
            metadata.displayPrimaryRed_y = 14600;     // 0.292
            metadata.displayPrimaryGreen_x = 8500;    // 0.170
            metadata.displayPrimaryGreen_y = 39850;   // 0.797
            metadata.displayPrimaryBlue_x = 6550;     // 0.131
            metadata.displayPrimaryBlue_y = 2300;     // 0.046
            metadata.whitePoint_x = 15635;            // 0.3127 (D65)
            metadata.whitePoint_y = 16450;            // 0.3290 (D65)
            
            // Standard HDR10 luminance values
            metadata.maxDisplayMasteringLuminance = 10000000;  // 1000 cd/m² (in 0.0001 increments)
            metadata.minDisplayMasteringLuminance = 5;         // 0.0005 cd/m² (in 0.0001 increments)
            
            // Default content light levels
            metadata.maxContentLightLevel = 1000;              // 1000 cd/m²
            metadata.maxFrameAverageLightLevel = 400;           // 400 cd/m²
            
            return metadata;
        }
        
        /**
         * Create metadata from display capabilities
         */
        public static StaticMetadata createFromDisplayInfo(HDRDisplayInfo displayInfo) {
            StaticMetadata metadata = createBT2020Metadata();
            
            if (displayInfo != null) {
                // Update luminance values based on display capabilities
                float maxLuminance = displayInfo.getMaxLuminance();
                float minLuminance = displayInfo.getMinLuminance();
                float maxAvgLuminance = displayInfo.getMaxAverageLuminance();
                
                if (maxLuminance > 0) {
                    metadata.maxDisplayMasteringLuminance = Math.round(maxLuminance * 10000);
                }
                if (minLuminance > 0) {
                    metadata.minDisplayMasteringLuminance = Math.round(minLuminance * 10000);
                }
                if (maxAvgLuminance > 0) {
                    metadata.maxFrameAverageLightLevel = Math.round(maxAvgLuminance);
                }
                
                Log.d(TAG, "Created metadata from display: max=" + maxLuminance + ", min=" + minLuminance);
            }
            
            return metadata;
        }
        
        /**
         * Convert to byte array for transmission
         */
        public byte[] toByteArray() {
            // HDR10 Static Metadata Type 1 format (26 bytes)
            byte[] data = new byte[26];
            
            // Mastering display color primaries (12 bytes)
            writeUint16(data, 0, displayPrimaryRed_x);
            writeUint16(data, 2, displayPrimaryRed_y);
            writeUint16(data, 4, displayPrimaryGreen_x);
            writeUint16(data, 6, displayPrimaryGreen_y);
            writeUint16(data, 8, displayPrimaryBlue_x);
            writeUint16(data, 10, displayPrimaryBlue_y);
            
            // White point (4 bytes)
            writeUint16(data, 12, whitePoint_x);
            writeUint16(data, 14, whitePoint_y);
            
            // Mastering display luminance (8 bytes)
            writeUint32(data, 16, maxDisplayMasteringLuminance);
            writeUint32(data, 20, minDisplayMasteringLuminance);
            
            // Content light level (4 bytes)
            writeUint16(data, 24, maxContentLightLevel);
            writeUint16(data, 26, maxFrameAverageLightLevel);
            
            return data;
        }
        
        private void writeUint16(byte[] data, int offset, int value) {
            data[offset] = (byte) (value & 0xFF);
            data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        }
        
        private void writeUint32(byte[] data, int offset, int value) {
            data[offset] = (byte) (value & 0xFF);
            data[offset + 1] = (byte) ((value >> 8) & 0xFF);
            data[offset + 2] = (byte) ((value >> 16) & 0xFF);
            data[offset + 3] = (byte) ((value >> 24) & 0xFF);
        }
        
        @Override
        public String toString() {
            return "StaticMetadata{" +
                    "red=(" + (displayPrimaryRed_x / 50000.0f) + ", " + (displayPrimaryRed_y / 50000.0f) + "), " +
                    "green=(" + (displayPrimaryGreen_x / 50000.0f) + ", " + (displayPrimaryGreen_y / 50000.0f) + "), " +
                    "blue=(" + (displayPrimaryBlue_x / 50000.0f) + ", " + (displayPrimaryBlue_y / 50000.0f) + "), " +
                    "white=(" + (whitePoint_x / 50000.0f) + ", " + (whitePoint_y / 50000.0f) + "), " +
                    "maxLum=" + (maxDisplayMasteringLuminance / 10000.0f) + " cd/m², " +
                    "minLum=" + (minDisplayMasteringLuminance / 10000.0f) + " cd/m², " +
                    "maxCLL=" + maxContentLightLevel + " cd/m², " +
                    "maxFALL=" + maxFrameAverageLightLevel + " cd/m²" +
                    "}";
        }
    }
    
    // HDR10+ Dynamic Metadata (SMPTE ST 2094-40)
    public static class DynamicMetadata {
        public int targetedSystemDisplayMaximumLuminance;
        public int targetedSystemDisplayActualPeakLuminance;
        public int[] maxscl = new int[3];  // Maximum of maxRGB for each color component
        public int[] averageMaxrgb = new int[3];  // Average of maxRGB for each color component
        public int numDistributionMaxrgbPercentiles;
        public int[] distributionMaxrgbPercentages;
        public int[] distributionMaxrgbPercentiles;
        public float fractionBrightPixels;
        
        /**
         * Create basic dynamic metadata
         */
        public static DynamicMetadata createBasicMetadata(float maxLuminance) {
            DynamicMetadata metadata = new DynamicMetadata();
            
            metadata.targetedSystemDisplayMaximumLuminance = Math.round(maxLuminance);
            metadata.targetedSystemDisplayActualPeakLuminance = Math.round(maxLuminance);
            
            // Default values for RGB components
            for (int i = 0; i < 3; i++) {
                metadata.maxscl[i] = Math.round(maxLuminance);
                metadata.averageMaxrgb[i] = Math.round(maxLuminance * 0.75f);
            }
            
            metadata.fractionBrightPixels = 0.1f;  // 10% bright pixels
            
            return metadata;
        }
    }
    
    private StaticMetadata currentStaticMetadata;
    private DynamicMetadata currentDynamicMetadata;
    private final HDRDisplayInfo displayInfo;
    
    public HDR10MetadataHandler(HDRDisplayInfo displayInfo) {
        this.displayInfo = displayInfo;
        initializeMetadata();
    }
    
    /**
     * Initialize metadata based on display capabilities
     */
    private void initializeMetadata() {
        if (displayInfo != null && displayInfo.supportsHDR10()) {
            currentStaticMetadata = StaticMetadata.createFromDisplayInfo(displayInfo);
            currentDynamicMetadata = DynamicMetadata.createBasicMetadata(displayInfo.getMaxLuminance());
            
            Log.d(TAG, "Initialized HDR10 metadata:");
            Log.d(TAG, "Static: " + currentStaticMetadata.toString());
        } else {
            Log.w(TAG, "Display does not support HDR10, using default metadata");
            currentStaticMetadata = StaticMetadata.createBT2020Metadata();
            currentDynamicMetadata = DynamicMetadata.createBasicMetadata(1000.0f);
        }
    }
    
    /**
     * Get current static metadata
     */
    public StaticMetadata getStaticMetadata() {
        return currentStaticMetadata;
    }
    
    /**
     * Get current dynamic metadata
     */
    public DynamicMetadata getDynamicMetadata() {
        return currentDynamicMetadata;
    }
    
    /**
     * Update metadata for new content
     */
    public void updateMetadataForContent(float maxContentLuminance, float avgContentLuminance) {
        if (currentStaticMetadata != null) {
            currentStaticMetadata.maxContentLightLevel = Math.round(maxContentLuminance);
            currentStaticMetadata.maxFrameAverageLightLevel = Math.round(avgContentLuminance);
            
            Log.d(TAG, "Updated content metadata: maxCLL=" + maxContentLuminance + ", maxFALL=" + avgContentLuminance);
        }
        
        if (currentDynamicMetadata != null) {
            currentDynamicMetadata.targetedSystemDisplayActualPeakLuminance = Math.round(maxContentLuminance);
        }
    }
    
    /**
     * Create HDR10 metadata packet for video output
     */
    public byte[] createHDR10MetadataPacket() {
        if (currentStaticMetadata == null) {
            return null;
        }
        
        // Create InfoFrame packet for HDR10 static metadata
        // Based on CTA-861.3 specification
        byte[] packet = new byte[32];
        
        // Header
        packet[0] = (byte) 0x87;  // HDR Static Metadata InfoFrame Type
        packet[1] = (byte) 0x01;  // Version
        packet[2] = (byte) 0x1A;  // Length (26 bytes)
        packet[3] = 0;           // Checksum (calculated later)
        
        // Data bytes
        packet[4] = (byte) 0x00;  // EOTF: SDR Gamma
        packet[5] = (byte) 0x00;  // Static Metadata Descriptor ID: Type 1
        
        // Copy static metadata
        byte[] metadata = currentStaticMetadata.toByteArray();
        System.arraycopy(metadata, 0, packet, 6, Math.min(metadata.length, 26));
        
        // Calculate checksum
        int checksum = 0;
        for (int i = 0; i < packet.length; i++) {
            if (i != 3) {  // Skip checksum byte itself
                checksum += packet[i] & 0xFF;
            }
        }
        packet[3] = (byte) (256 - (checksum & 0xFF));
        
        return packet;
    }
    
    /**
     * Validate metadata values
     */
    public boolean validateMetadata() {
        if (currentStaticMetadata == null) {
            Log.e(TAG, "Static metadata is null");
            return false;
        }
        
        // Check luminance values
        if (currentStaticMetadata.maxDisplayMasteringLuminance <= currentStaticMetadata.minDisplayMasteringLuminance) {
            Log.e(TAG, "Invalid luminance range");
            return false;
        }
        
        // Check content light levels
        if (currentStaticMetadata.maxContentLightLevel < currentStaticMetadata.maxFrameAverageLightLevel) {
            Log.e(TAG, "Invalid content light levels");
            return false;
        }
        
        // Check color primaries are within valid range
        int[] primaries = {
            currentStaticMetadata.displayPrimaryRed_x, currentStaticMetadata.displayPrimaryRed_y,
            currentStaticMetadata.displayPrimaryGreen_x, currentStaticMetadata.displayPrimaryGreen_y,
            currentStaticMetadata.displayPrimaryBlue_x, currentStaticMetadata.displayPrimaryBlue_y,
            currentStaticMetadata.whitePoint_x, currentStaticMetadata.whitePoint_y
        };
        
        for (int primary : primaries) {
            if (primary < 0 || primary > 50000) {  // 0.0 to 1.0 in 0.00002 increments
                Log.e(TAG, "Color primary out of range: " + primary);
                return false;
            }
        }
        
        Log.d(TAG, "Metadata validation passed");
        return true;
    }
    
    /**
     * Apply HDR tone curve to luminance values
     */
    public float applyHDRToneCurve(float linearLuminance) {
        // Apply PQ (Perceptual Quantizer) curve
        return HDRColorUtils.applyPQCurve(linearLuminance / HDRColorUtils.HDR_MAX_LUMINANCE);
    }
    
    /**
     * Remove HDR tone curve from luminance values
     */
    public float removeHDRToneCurve(float pqLuminance) {
        // Remove PQ curve
        return HDRColorUtils.removePQCurve(pqLuminance);
    }
    
    /**
     * Get metadata summary for debugging
     */
    public String getMetadataSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("HDR10 Metadata Summary:\n");
        
        if (currentStaticMetadata != null) {
            summary.append("Static Metadata: ").append(currentStaticMetadata.toString()).append("\n");
        } else {
            summary.append("Static Metadata: Not available\n");
        }
        
        if (currentDynamicMetadata != null) {
            summary.append("Dynamic Metadata: Available\n");
            summary.append("  Target Luminance: ").append(currentDynamicMetadata.targetedSystemDisplayMaximumLuminance).append(" cd/m²\n");
        } else {
            summary.append("Dynamic Metadata: Not available\n");
        }
        
        summary.append("Validation: ").append(validateMetadata() ? "Passed" : "Failed").append("\n");
        
        return summary.toString();
    }
}