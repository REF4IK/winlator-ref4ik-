package com.winlator.cmod.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.core.HDRDisplayManager;
import com.winlator.cmod.core.HDRConfiguration;
import com.winlator.cmod.core.HDRSurfaceConfiguration;
import com.winlator.cmod.xserver.XServer;

import java.util.concurrent.atomic.AtomicBoolean;

@SuppressLint("ViewConstructor")
public class XServerView extends GLSurfaceView {
    private final GLRenderer renderer;
    private final AtomicBoolean renderPending = new AtomicBoolean(false);
    private HDRDisplayManager hdrDisplayManager;
    private HDRConfiguration hdrConfiguration;
    private HDRSurfaceConfiguration hdrSurfaceConfiguration;

    public XServerView(Context context, XServer xServer) {
        super(context);
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        
        // Initialize HDR support
        initializeHDRSupport(context);
        
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        renderer = new GLRenderer(this, xServer);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);
    }
    
    /**
     * Initialize HDR support for the surface view
     */
    private void initializeHDRSupport(Context context) {
        try {
            hdrDisplayManager = new HDRDisplayManager(context);
            
            if (hdrDisplayManager.isHDR10Supported()) {
                // Get recommended configuration
                hdrConfiguration = hdrDisplayManager.getRecommendedHDRConfiguration();
                
                // Create surface configuration
                hdrSurfaceConfiguration = new HDRSurfaceConfiguration(
                    hdrConfiguration, hdrDisplayManager.getPrimaryDisplayInfo());
                
                // Configure the GLSurfaceView for HDR
                hdrSurfaceConfiguration.configureGLSurfaceView(this);
                
                android.util.Log.d("XServerView", "HDR10 support initialized");
                android.util.Log.d("XServerView", hdrSurfaceConfiguration.getCapabilitySummary());
            } else {
                android.util.Log.d("XServerView", "HDR10 not supported on this display");
            }
        } catch (Exception e) {
                android.util.Log.e("XServerView", "Failed to initialize HDR support", e);
        }
    }

    @Override
    public void requestRender() {
        renderPending.set(true);
        super.requestRender();
    }

    public void onFrameStarted() {
        renderPending.set(false);
    }

    public HDRDisplayManager getHdrDisplayManager() {
        return hdrDisplayManager;
    }

    public HDRConfiguration getHdrConfiguration() {
        return hdrConfiguration;
    }

    public HDRSurfaceConfiguration getHdrSurfaceConfiguration() {
        return hdrSurfaceConfiguration;
    }

    public GLRenderer getRenderer() {
        return renderer;
    }
}
