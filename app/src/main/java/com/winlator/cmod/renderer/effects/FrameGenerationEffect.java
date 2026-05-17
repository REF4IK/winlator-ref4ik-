package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.EffectComposer;
import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;
import android.opengl.GLES20;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public class FrameGenerationEffect extends Effect {
    public static final int MODE_FAST = 0;
    public static final int MODE_BALANCED = 1;
    public static final int MODE_QUALITY = 2;

    // GameHub-style presets: combine mode + flowScale into one selector
    public static final int PRESET_DISABLED = 0;
    public static final int PRESET_FAST     = 1; // MODE_FAST,     flow=0.2
    public static final int PRESET_SMOOTH   = 2; // MODE_FAST,     flow=0.4
    public static final int PRESET_BALANCED = 3; // MODE_BALANCED, flow=0.6 (default)
    public static final int PRESET_ENHANCED = 4; // MODE_BALANCED, flow=0.8
    public static final int PRESET_CLEAR    = 5; // MODE_QUALITY,  flow=0.6
    public static final int PRESET_EXTREME  = 6; // MODE_QUALITY,  flow=0.8

    private int currentMode = MODE_BALANCED;
    private float flowScale = 0.6f;
    private int generationMultiplier = 2;
    private int generatedSubFrameIndex = 0;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;

    // Timing
    private long lastRealFrameTimeNs = 0;
    private long lastAnyFrameShownTimeNs = 0;
    private long nextFrameTimeNs = 0;

    private long currentRealFrameIntervalNs = 33333333;  // 30 FPS
    private long currentTargetFrameIntervalNs = 16666666;  // 60 FPS

    private static final long MIN_FRAME_INTERVAL_NS = 8 * NANOS_PER_MILLISECOND;  // 8ms
    private static final long MAX_FRAME_INTERVAL_NS = 1000 * NANOS_PER_MILLISECOND;  // 1000ms

    private boolean isEnabled = false;
    private float blendFactor = 0.5f;

    // Buffers for frames
    private int texturePrev = -1;
    private int textureCurr = -1;

    // Flags
    private boolean hasFirstFrame = false;
    private boolean hasSecondFrame = false;
    private boolean waitingForSecondFrame = true;

    // FPS
    public static final int FPS_AUTO = 0;
    public static final int FPS_15 = 15;
    public static final int FPS_20 = 20;
    public static final int FPS_25 = 25;
    public static final int FPS_30 = 30;
    public static final int FPS_45 = 45;
    public static final int FPS_60 = 60;

    private int targetFPS = FPS_30;
    private boolean autoDetectFPS = false;

    private List<Long> realFrameIntervals = new ArrayList<>();
    private static final int FRAME_HISTORY_SIZE = 10;

    private static final String TAG = "FrameGeneration";

    // Uniform locations
    //private boolean uniformsCached = false;
    public int uIsEnabledLoc = -1;
    private int uBlendFactorLoc = -1;
    private int uTexturePrevLoc = -1;
    private int uTextureCurrLoc = -1;
    private int uResolutionLoc = -1;
    private int uFlowScaleLoc = -1;
    private int uSubFrameIndexLoc = -1;

    // Display refresh rate
    private int displayRefreshRate = 60; // 60 Hz
    private int realFrameDisplayCount = 0;
    private int generatedFrameDisplayCount = 0;

    // Current frame to display
    private int currentDisplayFrameType = 0; // 0 - real, 1 - generated
    private int currentFrameDisplayCount = 0;

    private int currentSequence = 0;

    private boolean currentRealFrameCaptured = false;
    private int currentRealFrameIndex = 0;

    private int capturedRealFrame = -1;
    private boolean hasCapturedFrame = false;

    // Pre-allocated texture ring buffer — avoids per-frame GPU alloc/dealloc stalls
    private final int[] capturePool = new int[]{-1, -1, -1};
    private int capturePoolIndex = 0;
    private int capturePoolWidth = 0;
    private int capturePoolHeight = 0;
    private boolean skipFirstRealDisplay = false;

    private int currentWidth = 0;
    private int currentHeight = 0;

    private void LogString(String message) {
        if (EffectComposer.logEnabled)
            Log.d(TAG, message);
    }

    public FrameGenerationEffect() {
        super();
        updateFrameIntervals();
        calculateDisplayCounts();
        LogString("Effect created with target FPS: " + targetFPS);
    }

    @Override
    protected ShaderMaterial createMaterial() {
        switch (currentMode) {
            case MODE_FAST:
                Log.d(TAG, "Fast generation mode selected");
                return new FastFrameGenerationMaterial();
            case MODE_QUALITY:
                Log.d(TAG, "Quality generation mode selected");
                return new QualityFrameGenerationMaterial();
            case MODE_BALANCED:
            default:
                Log.d(TAG, "Balanced generation mode selected");
                return new OptimizedFrameGenerationMaterial();
        }
    }

    public void setGenerationMode(int mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;

            cleanup();
            resetState();
            resetUniformLocations();
        }
    }

    public int getCurrentMode() {
        return currentMode;
    }

    public void setFlowScale(float scale) {
        this.flowScale = Math.max(0f, Math.min(1f, scale));
    }

    public float getFlowScale() {
        return flowScale;
    }

    public void setGenerationMultiplier(int multiplier) {
        this.generationMultiplier = Math.max(2, Math.min(4, multiplier));
        updateFrameIntervals();
    }

    public int getGenerationMultiplier() {
        return generationMultiplier;
    }

    private void resetUniformLocations() {
        uIsEnabledLoc = -1;
        uBlendFactorLoc = -1;
        uTexturePrevLoc = -1;
        uTextureCurrLoc = -1;
        uResolutionLoc = -1;
        uFlowScaleLoc = -1;
        uSubFrameIndexLoc = -1;
    }

    /**
     * GameHub-style presets. Each preset is a tuned (mode + flowScale) pair.
     * PRESET_DISABLED also turns generation off.
     */
    public void setPreset(int preset) {
        switch (preset) {
            case PRESET_DISABLED:
                if (isEnabled) toggleGeneration();
                return;
            case PRESET_FAST:
                setGenerationMode(MODE_FAST);     setFlowScale(0.2f); break;
            case PRESET_SMOOTH:
                setGenerationMode(MODE_FAST);     setFlowScale(0.4f); break;
            case PRESET_BALANCED:
                setGenerationMode(MODE_BALANCED); setFlowScale(0.6f); break;
            case PRESET_ENHANCED:
                setGenerationMode(MODE_BALANCED); setFlowScale(0.8f); break;
            case PRESET_CLEAR:
                setGenerationMode(MODE_QUALITY);  setFlowScale(0.6f); break;
            case PRESET_EXTREME:
                setGenerationMode(MODE_QUALITY);  setFlowScale(0.8f); break;
            default:
                return;
        }
        if (!isEnabled) toggleGeneration();
    }

    public void toggleGeneration() {
        isEnabled = !isEnabled;
        LogString("Generation " + (isEnabled ? "ENABLED" : "DISABLED"));

        if (isEnabled) {
            clearHistory();
            long currentTimeNs = System.nanoTime();
            lastRealFrameTimeNs = currentTimeNs;
            lastAnyFrameShownTimeNs = currentTimeNs;
            nextFrameTimeNs = currentTimeNs;

            hasFirstFrame = false;
            hasSecondFrame = false;
            waitingForSecondFrame = true;
            currentDisplayFrameType = 0;
            currentFrameDisplayCount = 0;

            currentRealFrameCaptured = false;
            currentRealFrameIndex = 0;
        }
    }

    private void updateFrameIntervals() {
        if (autoDetectFPS) {
            currentRealFrameIntervalNs = calculateAverageFrameInterval();
            currentTargetFrameIntervalNs = currentRealFrameIntervalNs / generationMultiplier;
        } else {
            currentRealFrameIntervalNs = NANOS_PER_SECOND / targetFPS;
            currentTargetFrameIntervalNs = currentRealFrameIntervalNs / generationMultiplier;
        }

        currentTargetFrameIntervalNs = Math.max(MIN_FRAME_INTERVAL_NS,
                Math.min(currentTargetFrameIntervalNs, currentRealFrameIntervalNs));

        calculateDisplayCounts();

        LogString(String.format("Intervals updated: real=%.1fms, target=%.1fms, auto=%b",
                currentRealFrameIntervalNs / (double)NANOS_PER_MILLISECOND,
                currentTargetFrameIntervalNs / (double)NANOS_PER_MILLISECOND,
                autoDetectFPS));
    }

    private void calculateDisplayCounts() {
        long frameDurationNs = NANOS_PER_SECOND / displayRefreshRate;

        realFrameDisplayCount = (int) Math.max(1, currentTargetFrameIntervalNs / frameDurationNs);

        generatedFrameDisplayCount = (int) Math.max(1, currentTargetFrameIntervalNs / frameDurationNs);

        LogString(String.format("Display counts: real=%d, generated=%d (refresh rate=%d Hz)",
                realFrameDisplayCount, generatedFrameDisplayCount, displayRefreshRate));
    }

    private long calculateAverageFrameInterval() {
        if (realFrameIntervals.isEmpty()) {
            //return 67 * NANOS_PER_MILLISECOND;  // 67 ms
            return 33333333;  // 33.333333 ms
        }

        long sum = 0;
        for (long interval : realFrameIntervals) {
            sum += interval;
        }

        long average = sum / realFrameIntervals.size();
        return Math.max(MIN_FRAME_INTERVAL_NS, Math.min(average, MAX_FRAME_INTERVAL_NS));
    }

    private void clearHistory() {
        for (int i = 0; i < capturePool.length; i++) {
            if (capturePool[i] != -1) {
                GLES20.glDeleteTextures(1, new int[]{capturePool[i]}, 0);
                capturePool[i] = -1;
            }
        }
        capturePoolIndex = 0;
        capturePoolWidth = 0;
        capturePoolHeight = 0;
        texturePrev = -1;
        textureCurr = -1;
        capturedRealFrame = -1;
    }

    /**
     * Computes which generated sub-frame index should be displayed right now,
     * based on elapsed wall-clock time since the last real frame.
     * Index 0 = first generated sub-frame, multiplier-2 = last generated.
     * Returns multiplier-1 (or larger) if cycle has already passed.
     */
    private int computeExpectedSubFrameIndex() {
        if (currentRealFrameIntervalNs <= 0 || generationMultiplier < 2) return 0;
        long subIntervalNs = currentRealFrameIntervalNs / generationMultiplier;
        long elapsedNs = System.nanoTime() - lastRealFrameTimeNs;
        // Position N is at time N*subInterval. Sub-frames occupy positions 1..multiplier-1.
        // Map elapsed time to sub-frame index: index = floor(elapsed / subInterval) - 1.
        int pos = (int)(elapsedNs / Math.max(1, subIntervalNs));
        int idx = pos - 1;
        return Math.max(0, idx);
    }

    public int getFrameToDisplay() {
        if (!isEnabled) {
            LogString("Generation not enabled, showing real frame");
            return 0;
        }

        int requiredDisplayCount = (currentDisplayFrameType == 0) ?
                realFrameDisplayCount : generatedFrameDisplayCount;

        if (currentFrameDisplayCount < requiredDisplayCount) {
            currentFrameDisplayCount++;

            if (currentDisplayFrameType == 0 && currentFrameDisplayCount == 1 && skipFirstRealDisplay) {
                skipFirstRealDisplay = false;
                currentDisplayFrameType = 1;
                currentFrameDisplayCount = 0;
                LogString("Skipping first real display, switching to GENERATED");
                return getFrameToDisplay();
            }

            LogString(String.format("Continue showing %s frame (%d/%d)",
                    currentDisplayFrameType == 0 ? "REAL" : "GENERATED",
                    currentFrameDisplayCount, requiredDisplayCount));
            return currentDisplayFrameType;
        }

        currentFrameDisplayCount = 1;

        if (currentDisplayFrameType == 0) {
            // Real -> Generated. Compute expected sub-frame index from elapsed time
            // so we skip stale sub-frames if the GL thread was overloaded.
            generatedSubFrameIndex = computeExpectedSubFrameIndex();
            if (generatedSubFrameIndex >= generationMultiplier - 1) {
                // Too late \u2014 entire cycle of generated frames already missed.
                // Stay on real frame; wait for next game frame.
                generatedSubFrameIndex = 0;
                LogString("All generated sub-frames missed, staying on REAL");
                return 0;
            }
            currentDisplayFrameType = 1;
            LogString("Switching to GENERATED frame (sub-frame " + generatedSubFrameIndex + ")");
        } else {
            // Already in generated, advance. Use time to skip missed sub-frames.
            int expected = computeExpectedSubFrameIndex();
            int newIndex = Math.max(generatedSubFrameIndex + 1, expected);
            if (newIndex < generationMultiplier - 1) {
                if (newIndex > generatedSubFrameIndex + 1) {
                    LogString("Frame-drop recovery: skip " + (newIndex - generatedSubFrameIndex - 1) + " sub-frames");
                }
                generatedSubFrameIndex = newIndex;
                LogString("Generated sub-frame " + generatedSubFrameIndex + "/" + (generationMultiplier - 1));
            } else {
                generatedSubFrameIndex = 0;
                currentDisplayFrameType = 0;
                currentRealFrameIndex = 0;
                currentRealFrameCaptured = false;

                if (hasCapturedFrame && capturedRealFrame != -1) {
                    texturePrev = textureCurr;
                    textureCurr = capturedRealFrame;
                    capturedRealFrame = -1;
                    hasCapturedFrame = false;
                    LogString("Using captured real frame for next cycle");
                }

                LogString("Switching to REAL frame");
            }
        }

        return currentDisplayFrameType;
    }

    public void setTargetFPS(int fps) {
        if (fps == FPS_AUTO) {
            autoDetectFPS = true;
            LogString("Auto FPS detection enabled");
        } else {
            autoDetectFPS = false;
            this.targetFPS = fps;
            LogString("Target FPS set to: " + fps);
        }
        updateFrameIntervals();
        resetState();
    }

    public int getDisplayRefreshRate() {
        return displayRefreshRate;
    }

    public void setDisplayRefreshRate(int refreshRate) {
        if (refreshRate != displayRefreshRate) {
            displayRefreshRate = refreshRate;
            calculateDisplayCounts();
            LogString("Display refresh rate set to: " + refreshRate + " Hz");
        }
    }

    public int getTargetFPS() {
        return targetFPS;
    }

    public boolean isAutoDetectFPS() {
        return autoDetectFPS;
    }

    public long getCurrentRealFrameInterval() {
        return currentRealFrameIntervalNs / NANOS_PER_MILLISECOND;
    }

    public long getCurrentTargetFrameInterval() {
        return currentTargetFrameIntervalNs / NANOS_PER_MILLISECOND;
    }

    public void prepareFrame(int width, int height, int sequence) {
        this.currentWidth = width;
        this.currentHeight = height;

        this.currentSequence = sequence;

        if (!isEnabled) return;

        long currentTimeNs = System.nanoTime();

        if (sequence == 0) {
            // Real frame
            currentRealFrameIndex++;

            LogString(String.format("Real frame display #%d/%d, captured=%b",
                    currentRealFrameIndex, realFrameDisplayCount, currentRealFrameCaptured));

            boolean shouldCapture = false;
            int capturePoint = realFrameDisplayCount / 2;

            if (!currentRealFrameCaptured && currentRealFrameIndex >= capturePoint) {
                shouldCapture = true;
                currentRealFrameCaptured = true;
                LogString("Capturing real frame at mid-point of display cycle");
            }

            if (shouldCapture) {
                if (lastRealFrameTimeNs != 0) {
                    long intervalNs = currentTimeNs - lastRealFrameTimeNs;
                    realFrameIntervals.add(intervalNs);
                    if (realFrameIntervals.size() > FRAME_HISTORY_SIZE) {
                        realFrameIntervals.remove(0);
                    }
                    if (autoDetectFPS) {
                        updateFrameIntervals();
                    }
                }

                int newTextureId = captureCurrentFrameSimple(width, height);
                if (newTextureId == -1) return;

                if (!hasFirstFrame) {
                    textureCurr = newTextureId;
                    hasFirstFrame = true;
                    waitingForSecondFrame = true;
                    LogString("Captured first real frame");
                } else if (waitingForSecondFrame) {
                    texturePrev = textureCurr;
                    textureCurr = newTextureId;
                    hasSecondFrame = true;
                    waitingForSecondFrame = false;
                    LogString("Captured second real frame, ready for generation");
                } else {
                    capturedRealFrame = newTextureId;
                    hasCapturedFrame = true;
                    skipFirstRealDisplay = true;
                    LogString("Captured real frame for NEXT cycle (delayed display)");
                }

                lastRealFrameTimeNs = currentTimeNs;
                lastAnyFrameShownTimeNs = currentTimeNs;
            } else {
                lastAnyFrameShownTimeNs = currentTimeNs;
                LogString("Skipping capture - already captured this cycle");
            }

        } else if (sequence == 1) {
            // Generated frame
            LogString("Preparing GENERATED frame (sequence=1)");

            if (hasFirstFrame && hasSecondFrame &&
                    texturePrev != -1 && textureCurr != -1) {

                long timeSinceRealFrameNs = currentTimeNs - lastRealFrameTimeNs;
                // Index-based blend factor for deterministic x2/x3/x4 multiplier
                blendFactor = (float)(generatedSubFrameIndex + 1) / (float)generationMultiplier;

                lastAnyFrameShownTimeNs = currentTimeNs;

                LogString(String.format("Generated: prev=%d, curr=%d, blend=%.3f (time since real: %.2fms)",
                        texturePrev, textureCurr, blendFactor,
                        timeSinceRealFrameNs / (double)NANOS_PER_MILLISECOND));
            } else {
                LogString("Not enough frames for generation yet");
            }
        }
    }

    public void setupShaderUniforms() {
        ShaderMaterial material = getMaterial();
        if (material == null || material.getProgram() == 0) return;

        int program = material.getProgram();

        if (uIsEnabledLoc == -1) {
            //if (!uniformsCached) {
                uIsEnabledLoc = GLES20.glGetUniformLocation(program, "uIsEnabled");
                uBlendFactorLoc = GLES20.glGetUniformLocation(program, "uBlendFactor");
                uTexturePrevLoc = GLES20.glGetUniformLocation(program, "uTexturePrev");
                uTextureCurrLoc = GLES20.glGetUniformLocation(program, "uTextureCurr");
                uResolutionLoc = GLES20.glGetUniformLocation(program, "resolution");
                uFlowScaleLoc = GLES20.glGetUniformLocation(program, "uFlowScale");
                uSubFrameIndexLoc = GLES20.glGetUniformLocation(program, "uSubFrameIndex");
                //uniformsCached = true;
            //
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        int prevTex = (texturePrev != -1) ? texturePrev : (textureCurr != -1 ? textureCurr : 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prevTex);
        GLES20.glUniform1i(uTexturePrevLoc, 1);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        if (textureCurr != -1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureCurr);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
        GLES20.glUniform1i(uTextureCurrLoc, 2);

        if (uResolutionLoc != -1 && currentWidth > 0 && currentHeight > 0) {
            GLES20.glUniform2f(uResolutionLoc, currentWidth, currentHeight);
        }

        if (uFlowScaleLoc != -1) {
            GLES20.glUniform1f(uFlowScaleLoc, flowScale);
        }
        if (uSubFrameIndexLoc != -1) {
            GLES20.glUniform1i(uSubFrameIndexLoc, generatedSubFrameIndex);
        }

        GLES20.glUniform1i(uIsEnabledLoc, 0);
        GLES20.glUniform1f(uBlendFactorLoc, 0.0f);

        if (isEnabled && currentSequence == 1) {
            boolean canShowGenerated = texturePrev != -1 && textureCurr != -1 && !waitingForSecondFrame;

            if (canShowGenerated) {
                GLES20.glUniform1i(uIsEnabledLoc, 1);
                GLES20.glUniform1f(uBlendFactorLoc, blendFactor);

                long currentTimeNs = System.nanoTime();
                long timeSinceRealNs = currentTimeNs - lastRealFrameTimeNs;
                LogString(String.format("Showing GENERATED frame, resolution=%dx%d, blend=%.3f (%.2fms since real)",
                        currentWidth, currentHeight, blendFactor, timeSinceRealNs / (double)NANOS_PER_MILLISECOND));
            } else {
                LogString("Cannot show generated, showing REAL frame instead");
                GLES20.glUniform1i(uIsEnabledLoc, 0);
                GLES20.glUniform1f(uBlendFactorLoc, 0.0f);
            }
        } else {
            GLES20.glUniform1i(uIsEnabledLoc, 0);
            GLES20.glUniform1f(uBlendFactorLoc, 0.0f);
            LogString(String.format("Showing REAL frame, resolution=%dx%d (sequence=%d, enabled=%b)",
                    currentWidth, currentHeight, currentSequence, isEnabled));
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    }

    public void cleanup() {
        clearHistory();
        LogString("Effect cleaned up");
    }

    public boolean isEnabled() { return isEnabled; }

    private int captureCurrentFrameSimple(int width, int height) {
        if (width != capturePoolWidth || height != capturePoolHeight) {
            for (int i = 0; i < capturePool.length; i++) {
                if (capturePool[i] != -1) {
                    GLES20.glDeleteTextures(1, new int[]{capturePool[i]}, 0);
                    capturePool[i] = -1;
                }
            }
            capturePoolIndex = 0;
            capturePoolWidth = width;
            capturePoolHeight = height;
        }

        int slot = capturePoolIndex;
        capturePoolIndex = (capturePoolIndex + 1) % capturePool.length;

        if (capturePool[slot] == -1) {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            capturePool[slot] = textures[0];
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, capturePool[slot]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            LogString("Pool slot " + slot + " allocated: " + capturePool[slot]);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, capturePool[slot]);
        }

        GLES20.glCopyTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        LogString("Captured to pool slot " + slot + ": " + capturePool[slot]);
        return capturePool[slot];
    }

    public void resetState() {
        clearHistory();
        hasFirstFrame = false;
        hasSecondFrame = false;
        waitingForSecondFrame = true;
        lastRealFrameTimeNs = 0;
        lastAnyFrameShownTimeNs = 0;
        nextFrameTimeNs = 0;
        currentDisplayFrameType = 0;
        currentFrameDisplayCount = 0;

        currentRealFrameCaptured = false;
        currentRealFrameIndex = 0;

        capturedRealFrame = -1;
        hasCapturedFrame = false;
        skipFirstRealDisplay = false;
        generatedSubFrameIndex = 0;
    }

    public boolean isReadyForGeneration() {
        return hasFirstFrame && hasSecondFrame && texturePrev != -1 && textureCurr != -1;
    }

    /**
     * Time-based scheduling for generated sub-frames (GameHub-style approach).
     * Returns delay in ms until the next render should occur:
     *   <0 — don't schedule (wait for next real frame from game)
     *    0 — render immediately
     *   >0 — postDelayed in ms
     * Targets are absolute: each sub-frame N should display at lastRealFrameTime + N * subInterval.
     */
    public long getScheduledRenderDelayMs() {
        if (!isEnabled || !hasFirstFrame || !hasSecondFrame) return -1;
        if (currentRealFrameIntervalNs <= 0 || generationMultiplier < 2) return -1;

        long currentTimeNs = System.nanoTime();
        long timeSinceRealNs = currentTimeNs - lastRealFrameTimeNs;

        // Safety: if game has stalled (no real frame for >1.8x expected interval), stop generating
        if (timeSinceRealNs > currentRealFrameIntervalNs * 18 / 10) return -1;

        long subIntervalNs = currentRealFrameIntervalNs / generationMultiplier;

        // Determine which sub-frame is NEXT to display.
        // currentDisplayFrameType reflects what was just rendered (set in getFrameToDisplay).
        int nextSubIndex;
        if (currentDisplayFrameType == 0) {
            // Just rendered real → next is generated sub-frame 0 (= 1st position after real)
            nextSubIndex = 1;
        } else {
            // Just rendered generated sub-frame at index `generatedSubFrameIndex`.
            // If that was the last (index == multiplier - 2), no more generated frames in this cycle.
            if (generatedSubFrameIndex >= generationMultiplier - 2) return -1;
            nextSubIndex = generatedSubFrameIndex + 2;
        }

        long targetTimeNs = lastRealFrameTimeNs + (long) nextSubIndex * subIntervalNs;
        long delayNs = targetTimeNs - currentTimeNs;
        if (delayNs <= 0) return 0; // we're already late, render now
        return delayNs / NANOS_PER_MILLISECOND;
    }

    private class FastFrameGenerationMaterial extends ScreenMaterial {
        public FastFrameGenerationMaterial() {
            super();
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision mediump float;",
                    "varying vec2 vUV;",
                    "uniform sampler2D uTexturePrev;",
                    "uniform sampler2D uTextureCurr;",
                    "uniform int uIsEnabled;",
                    "uniform float uBlendFactor;",
                    "uniform float uFlowScale;",
                    "",
                    "void main() {",
                    "    if (uIsEnabled == 1) {",
                    "        // Simple linear mixing",
                    "        vec4 prev = texture2D(uTexturePrev, vUV);",
                    "        vec4 curr = texture2D(uTextureCurr, vUV);",
                    "        vec4 result = mix(prev, curr, uBlendFactor);",
                    "",
                    "        // Minimal post-processing",
                    "        float contrast = 1.04;",
                    "        result.rgb = ((result.rgb - 0.5) * contrast) + 0.5;",
                    "",
                    "        gl_FragColor = result;",
                    "    } else {",
                    "        //gl_FragColor = texture2D(uTextureCurr, vUV);",
                    "        vec4 prev = texture2D(uTexturePrev, vUV);",
                    "        // Minimal post-processing",
                    "        float contrast = 1.04;",
                    "        prev.rgb = ((prev.rgb - 0.5) * contrast) + 0.5;",
                    "        gl_FragColor = prev;",
                    "    }",
                    "}"
            });
        }
    }

    private class OptimizedFrameGenerationMaterial extends ScreenMaterial {
        public OptimizedFrameGenerationMaterial() {
            super();
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision mediump float;",
                    "varying vec2 vUV;",
                    "uniform sampler2D uTexturePrev;",
                    "uniform sampler2D uTextureCurr;",
                    "uniform int uIsEnabled;",
                    "uniform float uBlendFactor;",
                    "uniform float uFlowScale;",
                    "uniform vec2 resolution;",
                    "uniform int uSubFrameIndex;",
                    "",
                    "// Quick motion assessment (3x3 at 4-pixel steps, covers +-4px)",
                    "vec2 fastMotionEstimate(vec2 uv) {",
                    "    vec2 texel = 1.0 / resolution;",
                    "    vec3 centerColor = texture2D(uTextureCurr, uv).rgb;",
                    "    float minDiff = 9999.0;",
                    "    vec2 bestMotion = vec2(0.0);",
                    "",
                    "    for (float dy = -1.0; dy <= 1.0; dy += 1.0) {",
                    "        for (float dx = -1.0; dx <= 1.0; dx += 1.0) {",
                    "            vec2 offset = vec2(dx, dy) * texel * 4.0;",
                    "            float diff = distance(centerColor, texture2D(uTexturePrev, uv + offset).rgb);",
                    "            if (diff < minDiff) {",
                    "                minDiff = diff;",
                    "                bestMotion = offset;",
                    "            }",
                    "        }",
                    "    }",
                    "",
                    "    if (minDiff > 0.2) return vec2(0.0);",
                    "    return bestMotion;",
                    "}",
                    "",
                    "// Fast edge detector (brightness only)",
                    "float fastEdgeDetection(vec2 uv) {",
                    "    vec2 texel = 1.0 / resolution;",
                    "",
                    "    // We only use brightness for speed",
                    "    float center = texture2D(uTextureCurr, uv).r;",
                    "    float right = texture2D(uTextureCurr, uv + vec2(texel.x, 0.0)).r;",
                    "    float left = texture2D(uTextureCurr, uv - vec2(texel.x, 0.0)).r;",
                    "    float up = texture2D(uTextureCurr, uv + vec2(0.0, texel.y)).r;",
                    "    float down = texture2D(uTextureCurr, uv - vec2(0.0, texel.y)).r;",
                    "",
                    "    // Simple Sobel",
                    "    float gx = right - left;",
                    "    float gy = up - down;",
                    "",
                    "    return sqrt(gx * gx + gy * gy);",
                    "}",
                    "",
                    "// The main function of frame generation",
                    "vec4 generateFrame(vec2 uv) {",
                    "    if (uIsEnabled != 1) {",
                    "        return texture2D(uTextureCurr, uv);",
                    "    }",
                    "",
                    "    // 1. Textures",
                    "    vec4 colorPrev = texture2D(uTexturePrev, uv);",
                    "    vec4 colorCurr = texture2D(uTextureCurr, uv);",
                    "",
                    "    // 2. Quick motion assessment",
                    "    vec2 motion = fastMotionEstimate(uv);",
                    "    float motionLength = length(motion);",
                    "",
                    "    // 3. Edge detection",
                    "    float edgeStrength = fastEdgeDetection(uv);",
                    "",
                    "    // 4. Bidirectional warping",
                    "    if (motionLength > 0.001) {",
                    "        float t = uBlendFactor;",
                    "        float motionScale = uFlowScale;",
                    "        vec2 fwdUV = clamp(uv + motion * motionScale * t, 0.0, 1.0);",
                    "        vec2 bwdUV = clamp(uv - motion * motionScale * (1.0 - t), 0.0, 1.0);",
                    "        vec4 fwdColor = texture2D(uTexturePrev, fwdUV);",
                    "        vec4 bwdColor = texture2D(uTextureCurr, bwdUV);",
                    "        return mix(fwdColor, bwdColor, t);",
                    "    } else if (edgeStrength > 0.1) {",
                    "        // Sharp edges - less interpolation",
                    "        return mix(colorPrev, colorCurr, uBlendFactor * 0.5);",
                    "    } else {",
                    "        // Regular interpolation for smooth areas",
                    "        return mix(colorPrev, colorCurr, uBlendFactor);",
                    "    }",
                    "}",
                    "",
                    "// Simple sharpness improvement",
                    "vec4 applySharpen(vec4 color, vec2 uv) {",
                    "    vec2 texel = 1.0 / resolution;",
                    "    vec4 blurred = vec4(0.0);",
                    "    blurred += texture2D(uTextureCurr, uv) * 0.5;",
                    "    blurred += texture2D(uTextureCurr, uv + vec2(texel.x, 0.0)) * 0.125;",
                    "    blurred += texture2D(uTextureCurr, uv - vec2(texel.x, 0.0)) * 0.125;",
                    "    blurred += texture2D(uTextureCurr, uv + vec2(0.0, texel.y)) * 0.125;",
                    "    blurred += texture2D(uTextureCurr, uv - vec2(0.0, texel.y)) * 0.125;",
                    "",
                    "    // Unsharp mask",
                    "    return color + (color - blurred) * 0.3;",
                    "}",
                    "",
                    "void main() {",
                    "    vec2 uv = vUV;",
                    "",
                    "    if (uIsEnabled == 1) {",
                    "        if (uSubFrameIndex > 0) {",
                    "            float t = uBlendFactor;",
                    "            vec4 p = texture2D(uTexturePrev, uv);",
                    "            vec4 c = texture2D(uTextureCurr, uv);",
                    "            gl_FragColor = clamp(mix(p, c, t), 0.0, 1.0);",
                    "            return;",
                    "        }",
                    "        // Generate frame",
                    "        vec4 generated = generateFrame(uv);",
                    "",
                    "        // Easy post-processing",
                    "        float luminance = dot(generated.rgb, vec3(0.299, 0.587, 0.114));",
                    "        ",
                    "        // Auto-contrast",
                    "        float contrast = 1.05;",
                    "        generated.rgb = ((generated.rgb - 0.5) * contrast) + 0.5;",
                    "        ",
                    "        // Light saturation",
                    "        generated.rgb = mix(vec3(luminance), generated.rgb, 1.05);",
                    "        ",
                    "        // Slight sharpening (only if there are edges)",
                    "        float edge = fastEdgeDetection(uv);",
                    "        if (edge > 0.05) {",
                    "            generated = applySharpen(generated, uv);",
                    "        }",
                    "        ",
                    "        gl_FragColor = clamp(generated, 0.0, 1.0);",
                    "    } else {",
                    "        // Real frame",
                    "        //gl_FragColor = texture2D(uTextureCurr, uv);",
                    "        vec4 prev = texture2D(uTexturePrev, uv);",
                    "",
                    "        // Easy post-processing",
                    "        float luminance = dot(prev.rgb, vec3(0.299, 0.587, 0.114));",
                    "        ",
                    "        // Auto-contrast",
                    "        float contrast = 1.05;",
                    "        prev.rgb = ((prev.rgb - 0.5) * contrast) + 0.5;",
                    "        ",
                    "        // Light saturation",
                    "        prev.rgb = mix(vec3(luminance), prev.rgb, 1.05);",
                    "        gl_FragColor = prev;",
                    "    }",
                    "}"
            });
        }
    }

    private class QualityFrameGenerationMaterial extends ScreenMaterial {
        public QualityFrameGenerationMaterial() {
            super();
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision mediump float;",
                    "varying vec2 vUV;",
                    "uniform sampler2D uTexturePrev;",
                    "uniform sampler2D uTextureCurr;",
                    "uniform int uIsEnabled;",
                    "uniform float uBlendFactor;",
                    "uniform float uFlowScale;",
                    "uniform vec2 resolution;",
                    "uniform int uSubFrameIndex;",
                    "",
                    "// Hierarchical motion estimation: 5x5 coarse (8px steps) + 3x3 fine (1px)",
                    "vec2 enhancedMotionEstimate(vec2 uv) {",
                    "    vec2 texel = 1.0 / resolution;",
                    "    vec3 centerColor = texture2D(uTextureCurr, uv).rgb;",
                    "    float minDiff = 9999.0;",
                    "    vec2 bestMotion = vec2(0.0);",
                    "",
                    "    // Level 1: 5x5 coarse search at 8-pixel steps (covers +-16px)",
                    "    for (float dy = -2.0; dy <= 2.0; dy += 1.0) {",
                    "        for (float dx = -2.0; dx <= 2.0; dx += 1.0) {",
                    "            vec2 offset = vec2(dx, dy) * texel * 8.0;",
                    "            float diff = distance(centerColor, texture2D(uTexturePrev, uv + offset).rgb);",
                    "            float penalty = length(vec2(dx, dy)) * 0.02;",
                    "            if (diff + penalty < minDiff) {",
                    "                minDiff = diff + penalty;",
                    "                bestMotion = offset;",
                    "            }",
                    "        }",
                    "    }",
                    "",
                    "    // Level 2: 3x3 fine refinement at 1-pixel steps around coarse result",
                    "    float fineDiff = minDiff;",
                    "    vec2 fineMotion = bestMotion;",
                    "    for (float dy = -1.0; dy <= 1.0; dy += 1.0) {",
                    "        for (float dx = -1.0; dx <= 1.0; dx += 1.0) {",
                    "            vec2 offset = bestMotion + vec2(dx, dy) * texel;",
                    "            float diff = distance(centerColor, texture2D(uTexturePrev, uv + offset).rgb);",
                    "            if (diff < fineDiff) {",
                    "                fineDiff = diff;",
                    "                fineMotion = offset;",
                    "            }",
                    "        }",
                    "    }",
                    "",
                    "    if (fineDiff > 0.25) return vec2(0.0);",
                    "    return fineMotion;",
                    "}",
                    "",
                    "// Improved Edge Detector (simplified Sobel)",
                    "float enhancedEdgeDetection(vec2 uv) {",
                    "    vec2 texel = 1.0 / resolution;",
                    "",
                    "    // Simple Sobel 3x3 (only 4 neighbors for speed)",
                    "    float center = texture2D(uTextureCurr, uv).r;",
                    "    float right = texture2D(uTextureCurr, uv + vec2(texel.x, 0.0)).r;",
                    "    float left = texture2D(uTextureCurr, uv - vec2(texel.x, 0.0)).r;",
                    "    float up = texture2D(uTextureCurr, uv + vec2(0.0, texel.y)).r;",
                    "    float down = texture2D(uTextureCurr, uv - vec2(0.0, texel.y)).r;",
                    "",
                    "    float gx = (right - left) * 2.0;",
                    "    float gy = (up - down) * 2.0;",
                    "",
                    "    float edge = sqrt(gx * gx + gy * gy);",
                    "    return clamp(edge * 2.0, 0.0, 1.0);",
                    "}",
                    "",
                    "// Quick texture assessment (local variation)",
                    "float fastTextureDetection(vec2 uv) {",
                    "    vec2 texel = 1.0 / resolution;",
                    "",
                    "    // We take 4 neighboring pixels",
                    "    vec3 c1 = texture2D(uTextureCurr, uv + vec2(texel.x, 0.0)).rgb;",
                    "    vec3 c2 = texture2D(uTextureCurr, uv - vec2(texel.x, 0.0)).rgb;",
                    "    vec3 c3 = texture2D(uTextureCurr, uv + vec2(0.0, texel.y)).rgb;",
                    "    vec3 c4 = texture2D(uTextureCurr, uv - vec2(0.0, texel.y)).rgb;",
                    "",
                    "    // Evaluating the variation",
                    "    float variation = 0.0;",
                    "    variation += distance(c1, c2);",
                    "    variation += distance(c3, c4);",
                    "",
                    "    return clamp(variation * 2.0, 0.0, 1.0);",
                    "}",
                    "",
                    "// Adaptive mixing",
                    "vec4 adaptiveBlending(vec2 uv, vec2 motion) {",
                    "    vec4 prev = texture2D(uTexturePrev, uv);",
                    "    vec4 curr = texture2D(uTextureCurr, uv);",
                    "",
                    "    float motionLength = length(motion);",
                    "    float edgeStrength = enhancedEdgeDetection(uv);",
                    "",
                    "    // Bidirectional warp + disocclusion mask",
                    "    if (motionLength > 0.001) {",
                    "        float t = uBlendFactor;",
                    "        float motionScale = uFlowScale;",
                    "",
                    "        vec2 fwdUV = clamp(uv + motion * motionScale * t, 0.0, 1.0);",
                    "        vec2 bwdUV = clamp(uv - motion * motionScale * (1.0 - t), 0.0, 1.0);",
                    "        vec4 fwdColor = texture2D(uTexturePrev, fwdUV);",
                    "        vec4 bwdColor = texture2D(uTextureCurr, bwdUV);",
                    "",
                    "        float disocclusion = distance(fwdColor.rgb, curr.rgb);",
                    "        float confidence = 1.0 - clamp(disocclusion * 3.0, 0.0, 1.0);",
                    "",
                    "        float adaptiveBlend = t;",
                    "        if (edgeStrength > 0.2) {",
                    "            adaptiveBlend = mix(t, 0.5, edgeStrength * 0.5);",
                    "        }",
                    "",
                    "        vec4 warped = mix(fwdColor, bwdColor, adaptiveBlend);",
                    "        vec4 fallback = mix(prev, curr, t);",
                    "        return mix(fallback, warped, confidence);",
                    "    }",
                    "",
                    "    // For static scenes",
                    "    float textureDetail = fastTextureDetection(uv);",
                    "",
                    "    if (textureDetail < 0.1) {",
                    "        // Homogeneous areas - smooth interpolation",
                    "        return mix(prev, curr, uBlendFactor);",
                    "    } else {",
                    "        // Texture areas - improved interpolation",
                    "        // Add some neighboring pixels for smoothing",
                    "        vec2 texel = 1.0 / resolution;",
                    "        vec4 result = mix(prev, curr, uBlendFactor) * 0.5;",
                    "        result += texture2D(uTexturePrev, uv + vec2(texel.x * 0.5, 0.0)) * 0.125;",
                    "        result += texture2D(uTexturePrev, uv - vec2(texel.x * 0.5, 0.0)) * 0.125;",
                    "        result += texture2D(uTextureCurr, uv + vec2(0.0, texel.y * 0.5)) * 0.125;",
                    "        result += texture2D(uTextureCurr, uv - vec2(0.0, texel.y * 0.5)) * 0.125;",
                    "",
                    "        return result;",
                    "    }",
                    "}",
                    "",
                    "// Improved sharpness filter (fast unsharp mask)",
                    "vec4 fastSharpen(vec4 color, vec2 uv) {",
                    "    vec2 texel = 1.0 / resolution;",
                    "",
                    "    // Simple blur (cross)",
                    "    vec4 blurred = color * 0.4;",
                    "    blurred += texture2D(uTextureCurr, uv + vec2(texel.x, 0.0)) * 0.15;",
                    "    blurred += texture2D(uTextureCurr, uv - vec2(texel.x, 0.0)) * 0.15;",
                    "    blurred += texture2D(uTextureCurr, uv + vec2(0.0, texel.y)) * 0.15;",
                    "    blurred += texture2D(uTextureCurr, uv - vec2(0.0, texel.y)) * 0.15;",
                    "",
                    "    // Unsharp mask",
                    "    float amount = 0.4;",
                    "    vec4 sharpened = color + (color - blurred) * amount;",
                    "",
                    "    return clamp(sharpened, 0.0, 1.0);",
                    "}",
                    "",
                    "// Improved color correction",
                    "vec4 enhancedColorCorrection(vec4 color) {",
                    "    // Automatic contrast",
                    "    float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));",
                    "",
                    "    // Adaptive contrast",
                    "    float adaptiveContrast = 1.08;",
                    "    if (luminance > 0.8) adaptiveContrast = 1.04;",
                    "    if (luminance < 0.2) adaptiveContrast = 1.12;",
                    "",
                    "    color.rgb = ((color.rgb - 0.5) * adaptiveContrast) + 0.5;",
                    "",
                    "    // Light saturation",
                    "    float saturation = 1.06;",
                    "    vec3 gray = vec3(luminance);",
                    "    color.rgb = mix(gray, color.rgb, saturation);",
                    "",
                    "    // Light gamma correction",
                    "    color.rgb = pow(color.rgb, vec3(0.98));",
                    "",
                    "    return color;",
                    "}",
                    "",
                    "// Main function",
                    "void main() {",
                    "    if (uSubFrameIndex > 0 && uIsEnabled == 1) {",
                    "        float t = uBlendFactor;",
                    "        vec4 p = texture2D(uTexturePrev, vUV);",
                    "        vec4 c = texture2D(uTextureCurr, vUV);",
                    "        gl_FragColor = clamp(mix(p, c, t), 0.0, 1.0);",
                    "        return;",
                    "    }",
                    "",
                    "    if (uIsEnabled != 1) {",
                    "        //gl_FragColor = texture2D(uTextureCurr, vUV);",
                    "        vec4 prev = texture2D(uTexturePrev, vUV);",
                    "        // 3. Post-processing",
                    "        prev = enhancedColorCorrection(prev);",
                    "",
                    "        // 4. Conditional sharpening",
                    "        float edgeStrength = enhancedEdgeDetection(vUV);",
                    "        if (edgeStrength > 0.15) {",
                    "            prev = fastSharpen(prev, vUV);",
                    "        }",
                    "",
                    "        // 5. Light noise reduction for homogeneous areas",
                    "        float textureDetail = fastTextureDetection(vUV);",
                    "        if (textureDetail < 0.08) {",
                    "            vec2 texel = 1.0 / resolution;",
                    "            prev = prev * 0.6;",
                    "            prev += texture2D(uTexturePrev, vUV + vec2(texel.x, 0.0)) * 0.1;",
                    "            prev += texture2D(uTexturePrev, vUV - vec2(texel.x, 0.0)) * 0.1;",
                    "            prev += texture2D(uTexturePrev, vUV + vec2(0.0, texel.y)) * 0.1;",
                    "            prev += texture2D(uTexturePrev, vUV - vec2(0.0, texel.y)) * 0.1;",
                    "        }",
                    "",
                    "        gl_FragColor = clamp(prev, 0.0, 1.0);",
                    "        return;",
                    "    }",
                    "",
                    "    // 1. Motion assessment",
                    "    vec2 motion = enhancedMotionEstimate(vUV);",
                    "",
                    "    // 2. Adaptive mixing",
                    "    vec4 generated = adaptiveBlending(vUV, motion);",
                    "",
                    "    // 3. Post-processing",
                    "    generated = enhancedColorCorrection(generated);",
                    "",
                    "    // 4. Conditional sharpening",
                    "    float edgeStrength = enhancedEdgeDetection(vUV);",
                    "    if (edgeStrength > 0.15) {",
                    "        generated = fastSharpen(generated, vUV);",
                    "    }",
                    "",
                    "    // 5. Light noise reduction for homogeneous areas",
                    "    float textureDetail = fastTextureDetection(vUV);",
                    "    if (textureDetail < 0.08) {",
                    "        vec2 texel = 1.0 / resolution;",
                    "        generated = generated * 0.6;",
                    "        generated += texture2D(uTextureCurr, vUV + vec2(texel.x, 0.0)) * 0.1;",
                    "        generated += texture2D(uTextureCurr, vUV - vec2(texel.x, 0.0)) * 0.1;",
                    "        generated += texture2D(uTextureCurr, vUV + vec2(0.0, texel.y)) * 0.1;",
                    "        generated += texture2D(uTextureCurr, vUV - vec2(0.0, texel.y)) * 0.1;",
                    "    }",
                    "",
                    "    gl_FragColor = clamp(generated, 0.0, 1.0);",
                    "}"
            });
        }
    }

}
