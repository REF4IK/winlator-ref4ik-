package com.winlator.cmod.renderer;

import android.opengl.GLES20;
import android.util.Log;

import com.winlator.cmod.renderer.effects.Effect;
import com.winlator.cmod.renderer.effects.RenderScaleEffect;
import com.winlator.cmod.renderer.effects.ToonEffect;
import com.winlator.cmod.renderer.material.ShaderMaterial;

import java.util.ArrayList;
import java.util.List;

public class EffectComposer {
    // Constants
    private static final String TAG = "EffectComposer";
    private boolean isRendering = false;

    // Instance fields
    private final List<Effect> effects = new ArrayList<>();
    private RenderTarget readBuffer;
    private RenderTarget writeBuffer;
    private RenderTarget sceneBuffer;
    private int sceneBufferWidth;
    private int sceneBufferHeight;
    private final GLRenderer renderer;


    public static final boolean logEnabled = false;

    private void logString(String message) {
        if (logEnabled) Log.d(TAG, message);
    }

    // Constructor
    public EffectComposer(GLRenderer renderer) {
        this.renderer = renderer;
//        Log.d(TAG, "EffectComposer created");
    }

    // Initializes the buffers if they are not already initialized
    private void initBuffers() {
//        Log.d(TAG, "initBuffers() called");

        if (readBuffer == null) {
            readBuffer = new RenderTarget();
            readBuffer.allocateFramebuffer(renderer.getSurfaceWidth(), renderer.getSurfaceHeight());
//            Log.d(TAG, "Initialized readBuffer with size: " + renderer.getSurfaceWidth() + "x" + renderer.getSurfaceHeight());
        }

        if (writeBuffer == null) {
            writeBuffer = new RenderTarget();
            writeBuffer.allocateFramebuffer(renderer.getSurfaceWidth(), renderer.getSurfaceHeight());
//            Log.d(TAG, "Initialized writeBuffer with size: " + renderer.getSurfaceWidth() + "x" + renderer.getSurfaceHeight());
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
//        Log.d(TAG, "getEffect() called for: " + effectClass.getSimpleName());

        for (Effect effect : effects) {
            if (effect.getClass() == effectClass) {
//                Log.d(TAG, "Effect found: " + effectClass.getSimpleName());
                return effectClass.cast(effect);
            }
        }
//        Log.d(TAG, "Effect not found: " + effectClass.getSimpleName());
        return null;
    }

    // Checks if there are any effects present
    public synchronized boolean hasEffects() {
        boolean hasEffects = !effects.isEmpty();
//        Log.d(TAG, "hasEffects() called. Effects present: " + hasEffects);
        return hasEffects;
    }

    // Removes a specific effect from the composer
    public synchronized void removeEffect(Effect effect) {
        effects.remove(effect);
        renderer.xServerView.requestRender();
    }


    // Renders all the effects in the composer
    public synchronized void render(boolean forceFullscreen) {
        // Check for recursive rendering
        if (isRendering) {
//            Log.d(TAG, "Render already in progress, skipping.");
            return;
        }

        isRendering = true; // Set flag to true

//        Log.d(TAG, "render() called");

        try {
            initBuffers();

            // Detect render-scale: if the first effect implements RenderScaleEffect,
            // render the scene into a smaller sceneBuffer and let that effect upscale it.
            Effect firstEffect = effects.isEmpty() ? null : effects.get(0);
            RenderScaleEffect renderScale = (firstEffect instanceof RenderScaleEffect)
                    ? (RenderScaleEffect) firstEffect : null;
            boolean useSceneBuffer = renderScale != null && hasEffects();
            int sceneW = renderer.surfaceWidth;
            int sceneH = renderer.surfaceHeight;
            if (useSceneBuffer) {
                sceneW = Math.max(1, renderScale.getRenderWidth(renderer, renderer.surfaceWidth));
                sceneH = Math.max(1, renderScale.getRenderHeight(renderer, renderer.surfaceHeight));
                if (sceneW == renderer.surfaceWidth && sceneH == renderer.surfaceHeight) {
                    // No actual downscale: skip the sceneBuffer path.
                    useSceneBuffer = false;
                }
            }

            if (useSceneBuffer) {
                ensureSceneBuffer(sceneW, sceneH);
                // Render scene into sceneBuffer at scene size.
                int savedW = renderer.surfaceWidth;
                int savedH = renderer.surfaceHeight;
                // Save the current view transformation values (computed for the
                // full surface) and recompute for the smaller scene buffer so the
                // scene fills it correctly regardless of fullscreen state.
                ViewTransformation vt = renderer.viewTransformation;
                int sViewOffX = vt.viewOffsetX, sViewOffY = vt.viewOffsetY;
                int sViewW = vt.viewWidth, sViewH = vt.viewHeight;
                float sAspect = vt.aspect;
                float sScaleX = vt.sceneScaleX, sScaleY = vt.sceneScaleY;
                float sOffX = vt.sceneOffsetX, sOffY = vt.sceneOffsetY;

                renderer.surfaceWidth = sceneW;
                renderer.surfaceHeight = sceneH;
                vt.update(sceneW, sceneH, renderer.getXServerWidth(), renderer.getXServerHeight());
                try {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, sceneBuffer.getFramebuffer());
                    GLES20.glViewport(0, 0, sceneW, sceneH);
                    renderer.setViewportNeedsUpdate(true);
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    renderer.renderScene(forceFullscreen);
                } finally {
                    renderer.surfaceWidth = savedW;
                    renderer.surfaceHeight = savedH;
                    vt.viewOffsetX = sViewOffX; vt.viewOffsetY = sViewOffY;
                    vt.viewWidth = sViewW; vt.viewHeight = sViewH;
                    vt.aspect = sAspect;
                    vt.sceneScaleX = sScaleX; vt.sceneScaleY = sScaleY;
                    vt.sceneOffsetX = sOffX; vt.sceneOffsetY = sOffY;
                    renderer.setViewportNeedsUpdate(true);
                }
            } else {
                // Set up framebuffer if there are effects to render
                if (hasEffects()) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, readBuffer.getFramebuffer());
                } else {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                }

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                // Draw the scene only once, then run the post-processing chain on top.
                renderer.renderScene(forceFullscreen);
            }

            for (int i = 0; i < effects.size(); i++) {
                Effect effect = effects.get(i);
                boolean renderToScreen = (i == effects.size() - 1);
                int targetFramebuffer = renderToScreen ? 0 : writeBuffer.getFramebuffer();

                // First effect with RenderScaleEffect: source = sceneBuffer (scene-size).
                boolean firstUsesSceneBuffer = (i == 0) && useSceneBuffer;

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer);
                GLES20.glViewport(0, 0, renderer.surfaceWidth, renderer.surfaceHeight);
                renderer.setViewportNeedsUpdate(true);

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                int sourceTexId = firstUsesSceneBuffer ? sceneBuffer.getTextureId() : readBuffer.getTextureId();
                renderEffect(effect, sourceTexId);
                swapBuffers();
            }

        } finally {
            isRendering = false;
        }
    }

    private void ensureSceneBuffer(int width, int height) {
        if (sceneBuffer != null && sceneBufferWidth == width && sceneBufferHeight == height) return;
        if (sceneBuffer != null) {
            // Reallocation requires GL teardown; for simplicity we leak and replace
            // (xServer geometry is stable during a session, so this happens at most once).
        }
        sceneBuffer = new RenderTarget();
        sceneBuffer.allocateFramebuffer(width, height);
        sceneBufferWidth = width;
        sceneBufferHeight = height;
    }

    // Renders a single effect (legacy entry point, defaults to readBuffer source)
    private void renderEffect(Effect effect) {
        renderEffect(effect, readBuffer != null ? readBuffer.getTextureId() : 0);
    }

    // Renders a single effect with the given source texture id
    private void renderEffect(Effect effect, int sourceTextureId) {
//        Log.d(TAG, "renderEffect() called for: " + effect.getClass().getSimpleName());

        ShaderMaterial material = effect.getMaterial();
        if (material == null) {
//            Log.e(TAG, "Material is null for effect: " + effect.getClass().getSimpleName());
            return;
        }

        material.use();
        // Set uniform values
        material.setUniformVec2("resolution", renderer.surfaceWidth, renderer.surfaceHeight);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTextureId);
        material.setUniformInt("screenTexture", 0);
        effect.onUse(material, renderer);

        // Bind the quad vertices to the shader program
        renderer.getQuadVertices().bind(material.programId);
//        Log.d(TAG, "Quad vertices bound to program ID: " + material.programId);

        // Draw the quad
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, renderer.quadVertices.count());
//        Log.d(TAG, "Quad drawn");

        // Unbind the texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
//        Log.d(TAG, "Texture unbound");
    }

    // Swaps the read and write buffers
    private void swapBuffers() {
        RenderTarget tmp = writeBuffer;
        writeBuffer = readBuffer;
        readBuffer = tmp;
//        Log.d(TAG, "swapBuffers() called. Buffers swapped.");
    }

    // Add a method to add the ToonEffect
    public synchronized void toggleToonEffect() {
        ToonEffect toonEffect = getEffect(ToonEffect.class);
        if (toonEffect != null) {
            removeEffect(toonEffect); // Remove if already present
            logString("ToonEffect removed");
        } else {
            addEffect(new ToonEffect()); // Add if not present
            logString("ToonEffect added");
        }
        renderer.xServerView.requestRender();
    }


}
