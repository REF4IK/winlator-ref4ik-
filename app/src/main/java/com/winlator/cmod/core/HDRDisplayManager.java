package com.winlator.cmod.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ColorSpace;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

/**
 * HDR Display Manager - manages HDR10 display detection and configuration
 * 
 * Features:
 * - HDR10 display detection
 * - Color space enumeration  
 * - Display capabilities analysis
 * - HDR metadata support
 * - Dynamic range configuration
 */
public class HDRDisplayManager {
    private static final String TAG = "HDRDisplayManager";
    
    // HDR10 constants
    public static final int HDR_TYPE_DOLBY_VISION = 1;
    public static final int HDR_TYPE_HDR10 = 2;
    public static final int HDR_TYPE_HLG = 3;
    public static final int HDR_TYPE_HDR10_PLUS = 4;
    
    // Color space constants
    public static final int COLOR_SPACE_SRGB = 0;
    public static final int COLOR_SPACE_DISPLAY_P3 = 1;
    public static final int COLOR_SPACE_BT2020 = 2;
    public static final int COLOR_SPACE_ADOBE_RGB = 3;
    
    // HDR capability flags
    public static final int HDR_CAPABILITY_NONE = 0;
    public static final int HDR_CAPABILITY_HDR10 = 1;
    public static final int HDR_CAPABILITY_DOLBY_VISION = 2;
    public static final int HDR_CAPABILITY_HLG = 4;
    public static final int HDR_CAPABILITY_HDR10_PLUS = 8;
    
    private final Context context;
    private final DisplayManager displayManager;
    private HDRDisplayInfo primaryDisplayInfo;
    private List<HDRDisplayInfo> availableDisplays;
    private HDRStatusListener statusListener;
    
    public HDRDisplayManager(@NonNull Context context) {
        this.context = context;
        this.displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        this.availableDisplays = new ArrayList<>();
        initialize();
    }
    
    /**
     * Initialize HDR display detection
     */
    private void initialize() {
        detectHDRDisplays();
        
        // Register display change listener for API 17+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            registerDisplayListener();
        }
    }
    
    /**
     * Detect all available HDR displays
     */
    @SuppressLint("NewApi")
    private void detectHDRDisplays() {
        availableDisplays.clear();
        
        if (displayManager != null) {
            Display[] displays = displayManager.getDisplays();
            
            for (Display display : displays) {
                HDRDisplayInfo displayInfo = analyzeDisplay(display);
                availableDisplays.add(displayInfo);
                
                // Set primary display
                if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
                    primaryDisplayInfo = displayInfo;
                }
                
                Log.d(TAG, "Display " + display.getDisplayId() + 
                      " HDR capabilities: " + displayInfo.getHdrCapabilitiesString());
            }
        }
    }
    
    /**
     * Analyze individual display for HDR capabilities
     */
    @SuppressLint("NewApi")
    private HDRDisplayInfo analyzeDisplay(Display display) {
        HDRDisplayInfo.Builder builder = new HDRDisplayInfo.Builder(display);
        
        // Check HDR capabilities (Android 7.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();
            if (hdrCapabilities != null) {
                builder.setHdrCapabilities(hdrCapabilities);
                builder.setHdrCapabilityFlags(parseHdrTypes(hdrCapabilities.getSupportedHdrTypes()));
                builder.setMaxLuminance(hdrCapabilities.getDesiredMaxLuminance());
                builder.setMinLuminance(hdrCapabilities.getDesiredMinLuminance());
                builder.setMaxAverageLuminance(hdrCapabilities.getDesiredMaxAverageLuminance());
            }
        }
        
        // Check wide color gamut support (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setWideColorGamut(display.isWideColorGamut());
            
            // Get supported color modes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Display.Mode[] supportedModes = display.getSupportedModes();
                builder.setSupportedModes(supportedModes);
            }
        }
        
        // Analyze color space support
        analyzeColorSpaceSupport(builder);
        
        return builder.build();
    }
    
    /**
     * Analyze color space support for the display
     */
    @SuppressLint("NewApi")
    private void analyzeColorSpaceSupport(HDRDisplayInfo.Builder builder) {
        List<Integer> supportedColorSpaces = new ArrayList<>();
        
        // sRGB is always supported
        supportedColorSpaces.add(COLOR_SPACE_SRGB);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Check for Display P3 support
            if (isColorSpaceSupported(ColorSpace.Named.DISPLAY_P3)) {
                supportedColorSpaces.add(COLOR_SPACE_DISPLAY_P3);
            }
            
            // Check for BT.2020 support  
            if (isColorSpaceSupported(ColorSpace.Named.BT2020)) {
                supportedColorSpaces.add(COLOR_SPACE_BT2020);
            }
            
            // Check for Adobe RGB support
            if (isColorSpaceSupported(ColorSpace.Named.ADOBE_RGB)) {
                supportedColorSpaces.add(COLOR_SPACE_ADOBE_RGB);
            }
        }
        
        builder.setSupportedColorSpaces(supportedColorSpaces);
    }
    
    /**
     * Check if specific color space is supported
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private boolean isColorSpaceSupported(ColorSpace.Named colorSpaceName) {
        try {
            ColorSpace colorSpace = ColorSpace.get(colorSpaceName);
            return colorSpace != null;
        } catch (Exception e) {
            Log.w(TAG, "Color space " + colorSpaceName + " not supported", e);
            return false;
        }
    }
    
    /**
     * Parse HDR types from system constants
     */
    @SuppressLint("NewApi")
    private int parseHdrTypes(int[] hdrTypes) {
        int capabilities = HDR_CAPABILITY_NONE;
        
        if (hdrTypes != null) {
            for (int hdrType : hdrTypes) {
                switch (hdrType) {
                    case Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION:
                        capabilities |= HDR_CAPABILITY_DOLBY_VISION;
                        break;
                    case Display.HdrCapabilities.HDR_TYPE_HDR10:
                        capabilities |= HDR_CAPABILITY_HDR10;
                        break;
                    case Display.HdrCapabilities.HDR_TYPE_HLG:
                        capabilities |= HDR_CAPABILITY_HLG;
                        break;
                    case Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS:
                        capabilities |= HDR_CAPABILITY_HDR10_PLUS;
                        break;
                }
            }
        }
        
        return capabilities;
    }
    
    /**
     * Register display change listener
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void registerDisplayListener() {
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.d(TAG, "Display added: " + displayId);
                detectHDRDisplays();
                notifyDisplayChanged();
            }
            
            @Override
            public void onDisplayRemoved(int displayId) {
                Log.d(TAG, "Display removed: " + displayId);
                detectHDRDisplays();
                notifyDisplayChanged();
            }
            
            @Override
            public void onDisplayChanged(int displayId) {
                Log.d(TAG, "Display changed: " + displayId);
                detectHDRDisplays();
                notifyDisplayChanged();
            }
        }, null);
    }
    
    /**
     * Check if primary display supports HDR10
     */
    public boolean isHDR10Supported() {
        return primaryDisplayInfo != null && 
               primaryDisplayInfo.supportsHDR10();
    }
    
    /**
     * Check if primary display supports wide color gamut
     */
    public boolean isWideColorGamutSupported() {
        return primaryDisplayInfo != null && 
               primaryDisplayInfo.isWideColorGamut();
    }
    
    /**
     * Get primary display HDR information
     */
    public HDRDisplayInfo getPrimaryDisplayInfo() {
        return primaryDisplayInfo;
    }
    
    /**
     * Get all available displays with HDR information
     */
    public List<HDRDisplayInfo> getAvailableDisplays() {
        return new ArrayList<>(availableDisplays);
    }
    
    /**
     * Get recommended HDR configuration for current display
     */
    public HDRConfiguration getRecommendedHDRConfiguration() {
        if (!isHDR10Supported()) {
            return HDRConfiguration.createSDRConfiguration();
        }
        
        HDRConfiguration.Builder configBuilder = new HDRConfiguration.Builder();
        
        // Configure based on display capabilities
        if (primaryDisplayInfo.supportsHDR10()) {
            configBuilder.setHdrMode(HDRConfiguration.HDR_MODE_HDR10);
            
            // Choose best color space
            if (primaryDisplayInfo.supportsColorSpace(COLOR_SPACE_BT2020)) {
                configBuilder.setColorSpace(COLOR_SPACE_BT2020);
            } else if (primaryDisplayInfo.supportsColorSpace(COLOR_SPACE_DISPLAY_P3)) {
                configBuilder.setColorSpace(COLOR_SPACE_DISPLAY_P3);
            } else {
                configBuilder.setColorSpace(COLOR_SPACE_SRGB);
            }
            
            // Configure luminance based on display capabilities
            configBuilder.setMaxLuminance(primaryDisplayInfo.getMaxLuminance());
            configBuilder.setMinLuminance(primaryDisplayInfo.getMinLuminance());
            configBuilder.setMaxAverageLuminance(primaryDisplayInfo.getMaxAverageLuminance());
        }
        
        return configBuilder.build();
    }
    
    /**
     * Set HDR status change listener
     */
    public void setHDRStatusListener(HDRStatusListener listener) {
        this.statusListener = listener;
    }
    
    /**
     * Notify display configuration changed
     */
    private void notifyDisplayChanged() {
        if (statusListener != null) {
            statusListener.onHDRDisplayChanged(primaryDisplayInfo);
        }
    }
    
    /**
     * Get HDR support summary for debugging
     */
    public String getHDRSupportSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("HDR Display Support Summary:\n");
        summary.append("Primary Display HDR10: ").append(isHDR10Supported()).append("\n");
        summary.append("Wide Color Gamut: ").append(isWideColorGamutSupported()).append("\n");
        
        if (primaryDisplayInfo != null) {
            summary.append("Max Luminance: ").append(primaryDisplayInfo.getMaxLuminance()).append(" nits\n");
            summary.append("Min Luminance: ").append(primaryDisplayInfo.getMinLuminance()).append(" nits\n");
            summary.append("Supported Color Spaces: ").append(primaryDisplayInfo.getSupportedColorSpacesString()).append("\n");
            summary.append("HDR Capabilities: ").append(primaryDisplayInfo.getHdrCapabilitiesString()).append("\n");
        }
        
        summary.append("Total Displays: ").append(availableDisplays.size()).append("\n");
        
        return summary.toString();
    }
    
    /**
     * Interface for HDR status change notifications
     */
    public interface HDRStatusListener {
        void onHDRDisplayChanged(HDRDisplayInfo displayInfo);
    }
}