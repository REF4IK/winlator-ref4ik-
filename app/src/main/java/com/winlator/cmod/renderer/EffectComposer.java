package com.winlator.cmod.renderer;

import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Choreographer;

import com.winlator.cmod.renderer.effects.Effect;
import com.winlator.cmod.renderer.effects.FrameGenerationEffect;
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

    private FrameGenerationEffect frameGenerationEffect;

    // Vsync-aligned scheduling via Choreographer (more precise than Handler.postDelayed).
    // Each scheduled callback fires at the next vsync slot after the requested delay.
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean scheduledCallbackPending = false;
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            scheduledCallbackPending = false;
            if (renderer != null && renderer.xServerView != null) {
                renderer.xServerView.requestRender();
            }
        }
    };

    private void scheduleRender(long delayMs) {
        // Choreographer must be accessed from a Looper thread; route through main handler.
        mainHandler.post(() -> {
            if (scheduledCallbackPending) {
                Choreographer.getInstance().removeFrameCallback(frameCallback);
            }
            scheduledCallbackPending = true;
            if (delayMs <= 0) {
                Choreographer.getInstance().postFrameCallback(frameCallback);
            } else {
                Choreographer.getInstance().postFrameCallbackDelayed(frameCallback, delayMs);
            }
        });
    }

    private void cancelScheduledRender() {
        mainHandler.post(() -> {
            if (scheduledCallbackPending) {
                Choreographer.getInstance().removeFrameCallback(frameCallback);
                scheduledCallbackPending = false;
            }
        });
    }

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
            if (effect instanceof FrameGenerationEffect) {
                frameGenerationEffect = (FrameGenerationEffect) effect;
                Log.d(TAG, "FrameGenerationEffect added");
            }
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
        if (effects.remove(effect)) {
            if (effect == frameGenerationEffect) {
                frameGenerationEffect = null;
            }
        }
        renderer.xServerView.requestRender();
    }

    private int determineFrameSequence() {
        if (frameGenerationEffect != null && frameGenerationEffect.isEnabled()) {
            int frameType = frameGenerationEffect.getFrameToDisplay();
            if (frameType == 1 && !frameGenerationEffect.isReadyForGeneration()) {
                logString("Generation not ready yet, showing real frame instead");
                return 0;
            }
            return frameType;
        }
        return 0;
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

            int currentSequence = determineFrameSequence();

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

                if (effect == frameGenerationEffect && frameGenerationEffect != null) {
                    // FrameGenerationEffect only
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer);
                    GLES20.glViewport(0, 0, renderer.surfaceWidth, renderer.surfaceHeight);
                    renderer.setViewportNeedsUpdate(true);

                    // Do not clear buffer (generation uses its own textures)
                    frameGenerationEffect.prepareFrame(renderer.surfaceWidth, renderer.surfaceHeight, currentSequence);

                    renderEffect(effect);

                    if (!renderToScreen) {
                        swapBuffers();
                    }
                } else {
                    // Other effects
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, targetFramebuffer);
                    GLES20.glViewport(0, 0, renderer.surfaceWidth, renderer.surfaceHeight);
                    renderer.setViewportNeedsUpdate(true);

                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    int sourceTexId = firstUsesSceneBuffer ? sceneBuffer.getTextureId() : readBuffer.getTextureId();
                    renderEffect(effect, sourceTexId);
                    swapBuffers();
                }
            }

            if (frameGenerationEffect != null && frameGenerationEffect.isEnabled()) {
                long delayMs = frameGenerationEffect.getScheduledRenderDelayMs();
                if (delayMs >= 0) {
                    scheduleRender(delayMs);
                } else {
                    cancelScheduledRender();
                }
            } else {
                cancelScheduledRender();
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
        if (effect instanceof FrameGenerationEffect) {
            ((FrameGenerationEffect) effect).setupShaderUniforms();
        } else {
            // Set uniform values
            material.setUniformVec2("resolution", renderer.surfaceWidth, renderer.surfaceHeight);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, sourceTextureId);
            material.setUniformInt("screenTexture", 0);
            effect.onUse(material, renderer);
        }

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

    public synchronized void configureFrameGeneration(int targetFPS, int mode) {
        if (frameGenerationEffect != null) {
            frameGenerationEffect.setTargetFPS(targetFPS);
            frameGenerationEffect.setGenerationMode(mode);
        }
        renderer.xServerView.requestRender();
    }

    public void setDisplayRefreshRate(int refreshRate) {
        if (frameGenerationEffect != null) {
            frameGenerationEffect.setDisplayRefreshRate(refreshRate);
        }
    }

    public synchronized void setFrameGenerationPreset(int preset) {
        if (frameGenerationEffect == null) return;
        if (preset == FrameGenerationEffect.PRESET_DISABLED) {
            if (frameGenerationEffect.isEnabled()) frameGenerationEffect.toggleGeneration();
            return;
        }
        int mode;
        float flow;
        switch (preset) {
            case FrameGenerationEffect.PRESET_FAST:     mode = FrameGenerationEffect.MODE_FAST;     flow = 0.2f; break;
            case FrameGenerationEffect.PRESET_SMOOTH:   mode = FrameGenerationEffect.MODE_FAST;     flow = 0.4f; break;
            case FrameGenerationEffect.PRESET_BALANCED: mode = FrameGenerationEffect.MODE_BALANCED; flow = 0.6f; break;
            case FrameGenerationEffect.PRESET_ENHANCED: mode = FrameGenerationEffect.MODE_BALANCED; flow = 0.8f; break;
            case FrameGenerationEffect.PRESET_CLEAR:    mode = FrameGenerationEffect.MODE_QUALITY;  flow = 0.6f; break;
            case FrameGenerationEffect.PRESET_EXTREME:  mode = FrameGenerationEffect.MODE_QUALITY;  flow = 0.8f; break;
            default: return;
        }
        // Rebuilds shader material if mode changed (replaces frameGenerationEffect with a new instance).
        setGenerationMode(mode);
        if (frameGenerationEffect != null) {
            frameGenerationEffect.setFlowScale(flow);
            if (!frameGenerationEffect.isEnabled()) frameGenerationEffect.toggleGeneration();
        }
        renderer.xServerView.requestRender();
    }

    public void setGenerationMode(int mode) {
        if (frameGenerationEffect != null) {
            if (frameGenerationEffect.isEnabled()) {
                Log.d(TAG, "FrameGenerationEffect restart");
                int targetFPS = frameGenerationEffect.getTargetFPS();
                frameGenerationEffect.toggleGeneration();
                removeEffect(frameGenerationEffect);

                frameGenerationEffect = new FrameGenerationEffect();
                addEffect(frameGenerationEffect);
                frameGenerationEffect.toggleGeneration();
                frameGenerationEffect.setTargetFPS(targetFPS);
            }
            frameGenerationEffect.setGenerationMode(mode);
        }
    }

    public void setGenerationMultiplier(int multiplier) {
        if (frameGenerationEffect != null) {
            frameGenerationEffect.setGenerationMultiplier(multiplier);
        }
    }

    public synchronized FrameGenerationSettings getFrameGenerationSettings() {
        if (frameGenerationEffect != null) {
            return new FrameGenerationSettings(
                    frameGenerationEffect.getTargetFPS(),
                    frameGenerationEffect.isAutoDetectFPS(),
                    frameGenerationEffect.getCurrentRealFrameInterval(),
                    frameGenerationEffect.getCurrentTargetFrameInterval()
            );
        }
        return null;
    }

    public static class FrameGenerationSettings {
        public final int targetFPS;
        public final boolean autoDetect;
        public final long realInterval;
        public final long targetInterval;

        public FrameGenerationSettings(int targetFPS, boolean autoDetect, long realInterval, long targetInterval) {
            this.targetFPS = targetFPS;
            this.autoDetect = autoDetect;
            this.realInterval = realInterval;
            this.targetInterval = targetInterval;
        }
    }

}
