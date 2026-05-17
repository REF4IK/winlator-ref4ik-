package com.winlator.cmod.core;

import android.opengl.EGL14;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * HDR EGL Config Chooser - selects optimal EGL configuration for HDR rendering
 */
public class HDREGLConfigChooser implements GLSurfaceView.EGLConfigChooser {
    private static final String TAG = "HDREGLConfigChooser";
    
    // Standard config attributes
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_OPENGL_ES3_BIT = 64;
    
    private final HDRConfiguration hdrConfiguration;
    private final int redSize;
    private final int greenSize; 
    private final int blueSize;
    private final int alphaSize;
    private final int depthSize;
    private final int stencilSize;
    
    public HDREGLConfigChooser(HDRConfiguration hdrConfiguration) {
        this(hdrConfiguration, 8, 8, 8, 8, 16, 0);
    }
    
    public HDREGLConfigChooser(HDRConfiguration hdrConfiguration,
                              int redSize, int greenSize, int blueSize, int alphaSize,
                              int depthSize, int stencilSize) {
        this.hdrConfiguration = hdrConfiguration;
        this.redSize = redSize;
        this.greenSize = greenSize;
        this.blueSize = blueSize;
        this.alphaSize = alphaSize;
        this.depthSize = depthSize;
        this.stencilSize = stencilSize;
    }
    
    @Override
    public EGLConfig chooseConfig(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display) {
        // Get all available configurations
        EGLConfig[] configs = getConfigs(egl, display);
        if (configs == null || configs.length == 0) {
            Log.e(TAG, "No EGL configs available");
            return null;
        }
        
        // Find best HDR-capable configuration
        EGLConfig bestConfig = findBestHDRConfig(egl, display, configs);
        
        if (bestConfig != null) {
            logConfigDetails(egl, display, bestConfig);
            return bestConfig;
        }
        
        // Fallback to standard configuration
        Log.w(TAG, "No HDR-capable config found, falling back to standard config");
        return findFallbackConfig(egl, display, configs);
    }
    
    /**
     * Get all available EGL configurations
     */
    private EGLConfig[] getConfigs(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display) {
        int[] numConfigs = new int[1];
        
        if (!egl.eglGetConfigs(display, null, 0, numConfigs)) {
            Log.e(TAG, "Unable to retrieve number of EGL configs");
            return null;
        }
        
        EGLConfig[] configs = new EGLConfig[numConfigs[0]];
        if (!egl.eglGetConfigs(display, configs, numConfigs[0], numConfigs)) {
            Log.e(TAG, "Unable to retrieve EGL configs");
            return null;
        }
        
        return configs;
    }
    
    /**
     * Find best HDR-capable configuration
     */
    private EGLConfig findBestHDRConfig(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display, 
                                       EGLConfig[] configs) {
        EGLConfig bestConfig = null;
        int bestScore = -1;
        
        for (EGLConfig config : configs) {
            int score = scoreHDRConfig(egl, display, config);
            if (score > bestScore) {
                bestScore = score;
                bestConfig = config;
            }
        }
        
        Log.d(TAG, "Best HDR config score: " + bestScore);
        return bestConfig;
    }
    
    /**
     * Score HDR configuration based on capabilities
     */
    private int scoreHDRConfig(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display, 
                              EGLConfig config) {
        int[] value = new int[1];
        int score = 0;
        
        // Check if config meets minimum requirements
        if (!meetsMinimumRequirements(egl, display, config)) {
            return -1;
        }
        
        // Prefer higher bit depth for HDR
        if (hdrConfiguration.isHDREnabled()) {
            // Red channel bits
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_RED_SIZE, value);
            int redBits = value[0];
            
            // Green channel bits  
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_GREEN_SIZE, value);
            int greenBits = value[0];
            
            // Blue channel bits
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_BLUE_SIZE, value);
            int blueBits = value[0];
            
            // Alpha channel bits
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_ALPHA_SIZE, value);
            int alphaBits = value[0];
            
            // Score based on bit depth
            if (hdrConfiguration.isEnableBitDepth10()) {
                // Prefer 10-bit channels (or close to it)
                if (redBits >= 10 && greenBits >= 10 && blueBits >= 10) {
                    score += 1000;
                } else if (redBits >= 8 && greenBits >= 8 && blueBits >= 8) {
                    score += 500;
                }
            } else {
                // Standard 8-bit is sufficient
                if (redBits >= 8 && greenBits >= 8 && blueBits >= 8) {
                    score += 300;
                }
            }
            
            // Bonus for alpha channel
            if (alphaBits >= 8) {
                score += 100;
            }
            
            // Check for floating point support
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_COLOR_BUFFER_TYPE, value);
            if (value[0] == EGL10.EGL_RGB_BUFFER) {
                score += 50;
            }
        } else {
            // For SDR, prefer standard 8-bit
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_RED_SIZE, value);
            if (value[0] == 8) score += 100;
            
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_GREEN_SIZE, value);
            if (value[0] == 8) score += 100;
            
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_BLUE_SIZE, value);
            if (value[0] == 8) score += 100;
        }
        
        // Depth buffer preference
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_DEPTH_SIZE, value);
        if (value[0] >= depthSize) {
            score += 50;
        }
        
        // Stencil buffer preference
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_STENCIL_SIZE, value);
        if (value[0] >= stencilSize) {
            score += 25;
        }
        
        // Sample buffers (anti-aliasing)
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_SAMPLE_BUFFERS, value);
        if (value[0] > 0) {
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_SAMPLES, value);
            score += value[0]; // Add number of samples
        }
        
        return score;
    }
    
    /**
     * Check if configuration meets minimum requirements
     */
    private boolean meetsMinimumRequirements(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display,
                                           EGLConfig config) {
        int[] value = new int[1];
        
        // Check surface type
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_SURFACE_TYPE, value);
        if ((value[0] & EGL10.EGL_WINDOW_BIT) == 0) {
            return false;
        }
        
        // Check renderable type (OpenGL ES 3.0+)
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_RENDERABLE_TYPE, value);
        if ((value[0] & EGL_OPENGL_ES3_BIT) == 0 && (value[0] & EGL_OPENGL_ES2_BIT) == 0) {
            return false;
        }
        
        // Check minimum color bits
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_RED_SIZE, value);
        if (value[0] < redSize) return false;
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_GREEN_SIZE, value);
        if (value[0] < greenSize) return false;
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_BLUE_SIZE, value);
        if (value[0] < blueSize) return false;
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_ALPHA_SIZE, value);
        if (value[0] < alphaSize) return false;
        
        return true;
    }
    
    /**
     * Find fallback configuration for standard rendering
     */
    private EGLConfig findFallbackConfig(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display,
                                        EGLConfig[] configs) {
        int[] configSpec = {
            EGL10.EGL_RED_SIZE, redSize,
            EGL10.EGL_GREEN_SIZE, greenSize,
            EGL10.EGL_BLUE_SIZE, blueSize,
            EGL10.EGL_ALPHA_SIZE, alphaSize,
            EGL10.EGL_DEPTH_SIZE, depthSize,
            EGL10.EGL_STENCIL_SIZE, stencilSize,
            EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL10.EGL_NONE
        };
        
        EGLConfig[] fallbackConfigs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        
        if (egl.eglChooseConfig(display, configSpec, fallbackConfigs, 1, numConfigs)) {
            if (numConfigs[0] > 0) {
                return fallbackConfigs[0];
            }
        }
        
        // Try with OpenGL ES 2.0 if ES 3.0 fails
        configSpec[15] = EGL_OPENGL_ES2_BIT;
        if (egl.eglChooseConfig(display, configSpec, fallbackConfigs, 1, numConfigs)) {
            if (numConfigs[0] > 0) {
                return fallbackConfigs[0];
            }
        }
        
        Log.e(TAG, "No suitable EGL configuration found");
        return null;
    }
    
    /**
     * Log details of selected configuration
     */
    private void logConfigDetails(EGL10 egl, javax.microedition.khronos.egl.EGLDisplay display,
                                 EGLConfig config) {
        int[] value = new int[1];
        
        Log.d(TAG, "Selected EGL Configuration:");
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_RED_SIZE, value);
        Log.d(TAG, "  Red: " + value[0] + " bits");
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_GREEN_SIZE, value);
        Log.d(TAG, "  Green: " + value[0] + " bits");
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_BLUE_SIZE, value);
        Log.d(TAG, "  Blue: " + value[0] + " bits");
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_ALPHA_SIZE, value);
        Log.d(TAG, "  Alpha: " + value[0] + " bits");
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_DEPTH_SIZE, value);
        Log.d(TAG, "  Depth: " + value[0] + " bits");
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_STENCIL_SIZE, value);
        Log.d(TAG, "  Stencil: " + value[0] + " bits");
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_SAMPLE_BUFFERS, value);
        if (value[0] > 0) {
            egl.eglGetConfigAttrib(display, config, EGL10.EGL_SAMPLES, value);
            Log.d(TAG, "  Multisampling: " + value[0] + "x");
        } else {
            Log.d(TAG, "  Multisampling: None");
        }
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_RENDERABLE_TYPE, value);
        String renderableType = "";
        if ((value[0] & EGL_OPENGL_ES3_BIT) != 0) {
            renderableType += "ES3 ";
        }
        if ((value[0] & EGL_OPENGL_ES2_BIT) != 0) {
            renderableType += "ES2 ";
        }
        Log.d(TAG, "  Renderable Type: " + renderableType);
        
        egl.eglGetConfigAttrib(display, config, EGL10.EGL_COLOR_BUFFER_TYPE, value);
        String bufferType = (value[0] == EGL14.EGL_RGB_BUFFER) ? "RGB" : "Luminance";
        Log.d(TAG, "  Color Buffer Type: " + bufferType);
    }
}