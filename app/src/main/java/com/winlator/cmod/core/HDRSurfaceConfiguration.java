package com.winlator.cmod.core;

import android.annotation.SuppressLint;
import android.graphics.ColorSpace;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import javax.microedition.khronos.egl.EGL10;

/**
 * HDR Surface Configuration - manages OpenGL ES surface configuration for HDR10 rendering
 */
public class HDRSurfaceConfiguration {
    private static final String TAG = "HDRSurfaceConfiguration";
    
    // EGL extensions for HDR
    private static final String EGL_EXT_GL_COLORSPACE_BT2020_LINEAR = "EGL_EXT_gl_colorspace_bt2020_linear";
    private static final String EGL_EXT_GL_COLORSPACE_BT2020_PQ = "EGL_EXT_gl_colorspace_bt2020_pq";
    private static final String EGL_EXT_GL_COLORSPACE_DISPLAY_P3_LINEAR = "EGL_EXT_gl_colorspace_display_p3_linear";
    private static final String EGL_EXT_GL_COLORSPACE_DISPLAY_P3_PQ = "EGL_EXT_gl_colorspace_display_p3_pq";
    
    // EGL color space constants
    private static final int EGL_GL_COLORSPACE_KHR = 0x309D;
    private static final int EGL_GL_COLORSPACE_SRGB_KHR = 0x3089;
    private static final int EGL_GL_COLORSPACE_LINEAR_KHR = 0x308A;
    private static final int EGL_GL_COLORSPACE_BT2020_LINEAR_EXT = 0x333F;
    private static final int EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340;
    private static final int EGL_GL_COLORSPACE_DISPLAY_P3_LINEAR_EXT = 0x3362;
    private static final int EGL_GL_COLORSPACE_DISPLAY_P3_PQ_EXT = 0x3363;
    
    // OpenGL extensions for HDR
    private static final String GL_EXT_COLOR_BUFFER_FLOAT = "GL_EXT_color_buffer_float";
    private static final String GL_EXT_COLOR_BUFFER_HALF_FLOAT = "GL_EXT_color_buffer_half_float";
    private static final String GL_OES_TEXTURE_HALF_FLOAT = "GL_OES_texture_half_float";
    private static final String GL_EXT_TEXTURE_NORM16 = "GL_EXT_texture_norm16";
    
    private final HDRConfiguration hdrConfiguration;
    private final HDRDisplayInfo displayInfo;
    private boolean isHDRSupported = false;
    private boolean isWideColorSupported = false;
    private boolean isFloatBufferSupported = false;
    private boolean isHalfFloatBufferSupported = false;
    private boolean is10BitSupported = false;
    
    public HDRSurfaceConfiguration(HDRConfiguration hdrConfiguration, HDRDisplayInfo displayInfo) {
        this.hdrConfiguration = hdrConfiguration;
        this.displayInfo = displayInfo;
        
        analyzeCapabilities();
    }
    
    /**
     * Analyze system capabilities for HDR rendering
     */
    private void analyzeCapabilities() {
        // Check display HDR support
        isHDRSupported = displayInfo != null && displayInfo.supportsHDR();
        isWideColorSupported = displayInfo != null && displayInfo.isWideColorGamut();
        
        // Check OpenGL extensions
        checkOpenGLExtensions();
        
        // Check EGL extensions  
        checkEGLExtensions();
        
        Log.d(TAG, "HDR Capabilities Analysis:");
        Log.d(TAG, "  HDR Supported: " + isHDRSupported);
        Log.d(TAG, "  Wide Color Supported: " + isWideColorSupported);
        Log.d(TAG, "  Float Buffer Supported: " + isFloatBufferSupported);
        Log.d(TAG, "  Half Float Buffer Supported: " + isHalfFloatBufferSupported);
        Log.d(TAG, "  10-bit Supported: " + is10BitSupported);
    }
    
    /**
     * Check OpenGL extensions for HDR support
     */
    private void checkOpenGLExtensions() {
        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if (extensions != null) {
            isFloatBufferSupported = extensions.contains(GL_EXT_COLOR_BUFFER_FLOAT);
            isHalfFloatBufferSupported = extensions.contains(GL_EXT_COLOR_BUFFER_HALF_FLOAT) ||
                                       extensions.contains(GL_OES_TEXTURE_HALF_FLOAT);
            is10BitSupported = extensions.contains(GL_EXT_TEXTURE_NORM16);
        }
    }
    
    /**
     * Check EGL extensions for HDR support
     */
    private void checkEGLExtensions() {
        EGLDisplay display = EGL14.eglGetCurrentDisplay();
        if (display != EGL14.EGL_NO_DISPLAY) {
            String extensions = EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS);
            if (extensions != null) {
                boolean hasBT2020Linear = extensions.contains(EGL_EXT_GL_COLORSPACE_BT2020_LINEAR);
                boolean hasBT2020PQ = extensions.contains(EGL_EXT_GL_COLORSPACE_BT2020_PQ);
                boolean hasP3Linear = extensions.contains(EGL_EXT_GL_COLORSPACE_DISPLAY_P3_LINEAR);
                boolean hasP3PQ = extensions.contains(EGL_EXT_GL_COLORSPACE_DISPLAY_P3_PQ);
                
                Log.d(TAG, "EGL HDR Extensions:");
                Log.d(TAG, "  BT2020 Linear: " + hasBT2020Linear);
                Log.d(TAG, "  BT2020 PQ: " + hasBT2020PQ);
                Log.d(TAG, "  P3 Linear: " + hasP3Linear);
                Log.d(TAG, "  P3 PQ: " + hasP3PQ);
            }
        }
    }
    
    /**
     * Create HDR-capable EGL configuration
     */
    public HDREGLConfigChooser createHDREGLConfigChooser() {
        return new HDREGLConfigChooser(hdrConfiguration);
    }
    
    /**
     * Get recommended surface attributes for HDR rendering
     */
    public int[] getHDRSurfaceAttributes() {
        if (!hdrConfiguration.isHDREnabled() || !isHDRSupported) {
            // Return standard SDR attributes
            return new int[] { EGL14.EGL_NONE };
        }
        
        int colorSpace = getEGLColorSpace();
        if (colorSpace != EGL14.EGL_NONE) {
            return new int[] {
                EGL_GL_COLORSPACE_KHR, colorSpace,
                EGL14.EGL_NONE
            };
        }
        
        return new int[] { EGL14.EGL_NONE };
    }
    
    /**
     * Get appropriate EGL color space for current configuration
     */
    private int getEGLColorSpace() {
        if (!hdrConfiguration.isHDREnabled()) {
            return EGL_GL_COLORSPACE_SRGB_KHR;
        }
        
        switch (hdrConfiguration.getColorSpace()) {
            case HDRDisplayManager.COLOR_SPACE_BT2020:
                // Use PQ (Perceptual Quantizer) for HDR10
                if (hdrConfiguration.getHdrMode() == HDRConfiguration.HDR_MODE_HDR10) {
                    return EGL_GL_COLORSPACE_BT2020_PQ_EXT;
                } else {
                    return EGL_GL_COLORSPACE_BT2020_LINEAR_EXT;
                }
                
            case HDRDisplayManager.COLOR_SPACE_DISPLAY_P3:
                if (hdrConfiguration.getHdrMode() == HDRConfiguration.HDR_MODE_HDR10) {
                    return EGL_GL_COLORSPACE_DISPLAY_P3_PQ_EXT;
                } else {
                    return EGL_GL_COLORSPACE_DISPLAY_P3_LINEAR_EXT;
                }
                
            case HDRDisplayManager.COLOR_SPACE_SRGB:
            default:
                return EGL_GL_COLORSPACE_SRGB_KHR;
        }
    }
    
    /**
     * Configure GLSurfaceView for HDR rendering
     */
    @SuppressLint("NewApi")
    public void configureGLSurfaceView(GLSurfaceView glSurfaceView) {
        // Set EGL context version
        glSurfaceView.setEGLContextClientVersion(3);
        
        // Use custom HDR EGL config chooser
        glSurfaceView.setEGLConfigChooser(createHDREGLConfigChooser());
        
        // Configure color space (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
            hdrConfiguration.isEnableWideColorGamut() && isWideColorSupported) {
            
            ColorSpace colorSpace = getAndroidColorSpace();
            if (colorSpace != null) {
                // This would require custom surface creation
                Log.d(TAG, "Would configure color space: " + colorSpace.getName());
            }
        }
        
        // Preserve EGL context
        glSurfaceView.setPreserveEGLContextOnPause(true);
    }
    
    /**
     * Get Android ColorSpace for current configuration
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private ColorSpace getAndroidColorSpace() {
        switch (hdrConfiguration.getColorSpace()) {
            case HDRDisplayManager.COLOR_SPACE_DISPLAY_P3:
                return ColorSpace.get(ColorSpace.Named.DISPLAY_P3);
            case HDRDisplayManager.COLOR_SPACE_BT2020:
                return ColorSpace.get(ColorSpace.Named.BT2020);
            case HDRDisplayManager.COLOR_SPACE_ADOBE_RGB:
                return ColorSpace.get(ColorSpace.Named.ADOBE_RGB);
            case HDRDisplayManager.COLOR_SPACE_SRGB:
            default:
                return ColorSpace.get(ColorSpace.Named.SRGB);
        }
    }
    
    /**
     * Get framebuffer format for HDR rendering
     */
    public int getHDRFramebufferFormat() {
        if (!hdrConfiguration.isHDREnabled()) {
            return GLES20.GL_RGBA;
        }
        
        if (hdrConfiguration.isEnableBitDepth10() && is10BitSupported) {
            // Use 10-bit format if available
            return GLES30.GL_RGB10_A2;
        } else if (isHalfFloatBufferSupported) {
            // Use 16-bit half float
            return GLES30.GL_RGBA16F;
        } else if (isFloatBufferSupported) {
            // Use 32-bit float
            return GLES30.GL_RGBA32F;
        } else {
            // Fallback to standard 8-bit
            return GLES20.GL_RGBA;
        }
    }
    
    /**
     * Get internal format for HDR textures
     */
    public int getHDRTextureInternalFormat() {
        return getHDRFramebufferFormat();
    }
    
    /**
     * Get pixel format for HDR textures
     */
    public int getHDRTextureFormat() {
        if (hdrConfiguration.isEnableBitDepth10() && is10BitSupported) {
            return GLES20.GL_RGBA;
        } else if (isHalfFloatBufferSupported || isFloatBufferSupported) {
            return GLES20.GL_RGBA;
        } else {
            return GLES20.GL_RGBA;
        }
    }
    
    /**
     * Get pixel type for HDR textures
     */
    public int getHDRTextureType() {
        if (hdrConfiguration.isEnableBitDepth10() && is10BitSupported) {
            return GLES30.GL_UNSIGNED_INT_2_10_10_10_REV;
        } else if (isHalfFloatBufferSupported) {
            return GLES30.GL_HALF_FLOAT;
        } else if (isFloatBufferSupported) {
            return GLES20.GL_FLOAT;
        } else {
            return GLES20.GL_UNSIGNED_BYTE;
        }
    }
    
    /**
     * Check if HDR rendering is available
     */
    public boolean isHDRRenderingAvailable() {
        return isHDRSupported && 
               hdrConfiguration.isHDREnabled() && 
               (isFloatBufferSupported || isHalfFloatBufferSupported || is10BitSupported);
    }
    
    /**
     * Get HDR capability summary
     */
    public String getCapabilitySummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("HDR Surface Configuration:\n");
        summary.append("  HDR Rendering Available: ").append(isHDRRenderingAvailable()).append("\n");
        summary.append("  Display HDR Support: ").append(isHDRSupported).append("\n");
        summary.append("  Wide Color Support: ").append(isWideColorSupported).append("\n");
        summary.append("  Float Buffer Support: ").append(isFloatBufferSupported).append("\n");
        summary.append("  Half Float Buffer Support: ").append(isHalfFloatBufferSupported).append("\n");
        summary.append("  10-bit Support: ").append(is10BitSupported).append("\n");
        summary.append("  Recommended Format: ").append(getFramebufferFormatName(getHDRFramebufferFormat())).append("\n");
        summary.append("  EGL Color Space: ").append(getEGLColorSpaceName()).append("\n");
        
        return summary.toString();
    }
    
    private String getFramebufferFormatName() {
        int format = getHDRFramebufferFormat();
        switch (format) {
            case GLES20.GL_RGBA: return "RGBA8";
            default: return "Unknown";
        }
        // Note: Advanced HDR formats handled separately due to GLES30 requirement
    }
    
    private String getFramebufferFormatName(int format) {
        if (format == GLES30.GL_RGB10_A2) return "RGB10_A2";
        if (format == GLES30.GL_RGBA16F) return "RGBA16F";
        if (format == GLES30.GL_RGBA32F) return "RGBA32F";
        if (format == GLES20.GL_RGBA) return "RGBA8";
        return "Unknown";
    }
    
    private String getEGLColorSpaceName() {
        int colorSpace = getEGLColorSpace();
        switch (colorSpace) {
            case EGL_GL_COLORSPACE_SRGB_KHR: return "sRGB";
            case EGL_GL_COLORSPACE_LINEAR_KHR: return "Linear";
            case EGL_GL_COLORSPACE_BT2020_LINEAR_EXT: return "BT2020 Linear";
            case EGL_GL_COLORSPACE_BT2020_PQ_EXT: return "BT2020 PQ";
            case EGL_GL_COLORSPACE_DISPLAY_P3_LINEAR_EXT: return "Display P3 Linear";
            case EGL_GL_COLORSPACE_DISPLAY_P3_PQ_EXT: return "Display P3 PQ";
            default: return "Unknown";
        }
    }
    
    // Getters
    public boolean isHDRSupported() { return isHDRSupported; }
    public boolean isWideColorSupported() { return isWideColorSupported; }
    public boolean isFloatBufferSupported() { return isFloatBufferSupported; }
    public boolean isHalfFloatBufferSupported() { return isHalfFloatBufferSupported; }
    public boolean is10BitSupported() { return is10BitSupported; }
}