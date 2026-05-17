package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.GLRenderer;

/**
 * Marker interface for effects that want the scene to be rendered at a custom
 * (typically lower) resolution into a dedicated scene buffer, then upscaled
 * by this effect. Used by {@link FSR1EasuEffect}.
 */
public interface RenderScaleEffect {
    int getRenderWidth(GLRenderer renderer, int outputWidth);
    int getRenderHeight(GLRenderer renderer, int outputHeight);
}
