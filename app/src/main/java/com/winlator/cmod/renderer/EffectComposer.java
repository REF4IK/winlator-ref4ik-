package com.winlator.cmod.renderer;

import android.opengl.GLES20;
import android.opengl.GLES30;
import android.util.Log;

import com.winlator.cmod.renderer.effects.BlurEffect;
import com.winlator.cmod.renderer.effects.CRTEffect;
import com.winlator.cmod.renderer.effects.ColorEffect;
import com.winlator.cmod.renderer.effects.Effect;
import com.winlator.cmod.renderer.effects.FXAAEffect;
import com.winlator.cmod.renderer.effects.GrayscaleEffect;
import com.winlator.cmod.renderer.effects.HDREffect;
import com.winlator.cmod.renderer.effects.NTSCCombinedEffect;
import com.winlator.cmod.renderer.effects.PixelateEffect;
import com.winlator.cmod.renderer.effects.SepiaEffect;
import com.winlator.cmod.renderer.effects.SharpenEffect;
import com.winlator.cmod.renderer.effects.SmoothEffect;
import com.winlator.cmod.renderer.effects.ToonEffect;
import com.winlator.cmod.renderer.effects.VignetteEffect;
import com.winlator.cmod.renderer.material.ShaderMaterial;

import java.util.ArrayList;
import java.util.List;

public class EffectComposer {
    // Constants mirroring VulkanRenderer.EFFECT_* so callers can share the same int codes.
    public static final int EFFECT_NONE = 0;
    public static final int EFFECT_COLOR = 1;
    public static final int EFFECT_FXAA = 2;
    public static final int EFFECT_CRT = 3;
    public static final int EFFECT_TOON = 4;
    public static final int EFFECT_VIGNETTE = 5;
    public static final int EFFECT_SEPIA = 6;
    public static final int EFFECT_BLUR = 7;
    public static final int EFFECT_PIXELATE = 8;
    public static final int EFFECT_GRAYSCALE = 9;
    public static final int EFFECT_SHARPEN = 10;
    public static final int EFFECT_SMOOTH = 11;
    public static final int EFFECT_HDR = 12;
    public static final int EFFECT_NTSC = 13;

    // Instance fields
    private final List<Effect> effects = new ArrayList<>();
    private RenderTarget readBuffer;
    private RenderTarget writeBuffer;
    private final GLRenderer renderer;

    // Constructor
    public EffectComposer(GLRenderer renderer) {
        this.renderer = renderer;
    }

    // Initializes the buffers if they are not already initialized
    private void initBuffers() {
        if (readBuffer == null) {
            readBuffer = new RenderTarget();
            readBuffer.allocateFramebuffer(renderer.getSurfaceWidth(), renderer.getSurfaceHeight());
        }

        if (writeBuffer == null) {
            writeBuffer = new RenderTarget();
            writeBuffer.allocateFramebuffer(renderer.getSurfaceWidth(), renderer.getSurfaceHeight());
        }
    }

    public synchronized void addEffect(Effect effect) {
        if (!effects.contains(effect)) {
            effects.add(effect);
        }
        renderer.xServerView.requestRender();
    }

    // Gets an effect by its class type
    public synchronized <T extends Effect> T getEffect(Class<T> effectClass) {
        for (Effect effect : effects) {
            if (effect.getClass() == effectClass) {
                return effectClass.cast(effect);
            }
        }
        return null;
    }

    // Checks if there are any effects present
    public synchronized boolean hasEffects() {
        return !effects.isEmpty();
    }

    // Removes a specific effect from the composer
    public synchronized void removeEffect(Effect effect) {
        if (effects.remove(effect)) {
        }
        renderer.xServerView.requestRender();
    }

    // Clears all effects
    public synchronized void clearEffects() {
        effects.clear();
        renderer.xServerView.requestRender();
    }

    // Applies a set of effects by type code and parameters, mirroring VulkanRenderer.setEffects.
    // Each effect instance is created/configured once here; render() picks up the list.
    public synchronized void setEffects(int[] types, float[][] paramsArr) {
        effects.clear();
        if (types != null) {
            for (int i = 0; i < types.length; i++) {
                float[] params = (paramsArr != null && i < paramsArr.length) ? paramsArr[i] : null;
                Effect effect = createEffect(types[i], params);
                if (effect != null) effects.add(effect);
            }
        }
        renderer.xServerView.requestRender();
    }

    private Effect createEffect(int type, float[] params) {
        switch (type) {
            case EFFECT_COLOR: {
                ColorEffect e = new ColorEffect();
                if (params != null && params.length >= 3) {
                    e.setBrightness(params[0]);
                    e.setContrast(params[1]);
                    e.setGamma(params[2]);
                }
                return e;
            }
            case EFFECT_HDR: {
                return new HDREffect();
            }
            case EFFECT_FXAA: {
                return new FXAAEffect();
            }
            case EFFECT_CRT: {
                return new CRTEffect();
            }
            case EFFECT_TOON: {
                return new ToonEffect();
            }
            case EFFECT_VIGNETTE: {
                VignetteEffect e = new VignetteEffect();
                if (params != null && params.length >= 2) {
                    e.setIntensity(params[0]);
                    e.setRadius(params[1]);
                }
                return e;
            }
            case EFFECT_SEPIA: {
                SepiaEffect e = new SepiaEffect();
                if (params != null && params.length >= 1) {
                    e.setIntensity(params[0]);
                }
                return e;
            }
            case EFFECT_BLUR: {
                BlurEffect e = new BlurEffect();
                if (params != null && params.length >= 2) {
                    e.setIntensity(params[0]);
                    e.setSamples(params[1]);
                }
                return e;
            }
            case EFFECT_PIXELATE: {
                PixelateEffect e = new PixelateEffect();
                if (params != null && params.length >= 1) {
                    e.setPixelSize(params[0]);
                }
                return e;
            }
            case EFFECT_GRAYSCALE: {
                GrayscaleEffect e = new GrayscaleEffect();
                if (params != null && params.length >= 1) {
                    e.setIntensity(params[0]);
                }
                return e;
            }
            case EFFECT_SHARPEN: {
                SharpenEffect e = new SharpenEffect();
                if (params != null && params.length >= 1) {
                    e.setIntensity(params[0]);
                }
                return e;
            }
            case EFFECT_SMOOTH: {
                SmoothEffect e = new SmoothEffect();
                if (params != null && params.length >= 1) {
                    e.setSmoothness(params[0]);
                }
                return e;
            }
            case EFFECT_NTSC: {
                return new NTSCCombinedEffect();
            }
            default:
                return null;
        }
    }

    // True when the composer has anything to do this frame.
    public synchronized boolean isActive() {
        return hasEffects();
    }

    // Renders all the effects in the composer
    public synchronized void render() {
        if (!hasEffects()) return;

        initBuffers();

        if (hasEffects()) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, readBuffer.getFramebuffer());
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }

        // Draw the initial frame
        renderer.drawFrame();

        // Iterate through each effect and render it
        for (int i = 0; i < effects.size(); i++) {
            Effect effect = effects.get(i);
            boolean renderToScreen = (i == effects.size() - 1);
            int targetFramebuffer = renderToScreen ? 0 : writeBuffer.getFramebuffer();

            // Trivial pure-copy stage: an effect with no shader material is not a real effect, so a
            // single full-frame glBlitFramebuffer (GLES30, LINEAR) of the read buffer replaces the
            // program-bind + clear + textured-quad.
            if (effect.getMaterial() == null) {
                blitReadBufferTo(targetFramebuffer);
                swapBuffers();
                continue;
            }

            // Bind appropriate framebuffer
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer);

            GLES20.glViewport(0, 0, renderer.surfaceWidth, renderer.surfaceHeight);
            renderer.setViewportNeedsUpdate(true);

            // Clear the buffer
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            // Render the effect
            renderEffect(effect);

            // Swap the read and write buffers
            swapBuffers();
        }
    }

    // Renders a single effect
    private void renderEffect(Effect effect) {
        ShaderMaterial material = effect.getMaterial();
        if (material == null) {
            return;
        }

        material.use();

        // Bind the quad vertices to the shader program
        renderer.getQuadVertices().bind(material.programId);

        // Set uniform values
        material.setUniformVec2("resolution", renderer.surfaceWidth, renderer.surfaceHeight);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, readBuffer.getTextureId());
        material.setUniformInt("screenTexture", 0);

        // Draw the quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, renderer.quadVertices.count());

        // Unbind the texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    // Pure copy/scale of the read buffer into the given framebuffer using glBlitFramebuffer
    private void blitReadBufferTo(int targetFramebuffer) {
        int w = renderer.surfaceWidth;
        int h = renderer.surfaceHeight;
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, readBuffer.getFramebuffer());
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, targetFramebuffer);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GLES20.GL_COLOR_BUFFER_BIT, GLES20.GL_LINEAR);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    // Swaps the read and write buffers
    private void swapBuffers() {
        RenderTarget tmp = writeBuffer;
        writeBuffer = readBuffer;
        readBuffer = tmp;
    }

    // Add a method to add the ToonEffect
    public synchronized void toggleToonEffect() {
        ToonEffect toonEffect = getEffect(ToonEffect.class);
        if (toonEffect != null) {
            removeEffect(toonEffect);
            Log.d("EffectComposer", "ToonEffect removed");
        } else {
            addEffect(new ToonEffect());
            Log.d("EffectComposer", "ToonEffect added");
        }
        renderer.xServerView.requestRender();
    }

}