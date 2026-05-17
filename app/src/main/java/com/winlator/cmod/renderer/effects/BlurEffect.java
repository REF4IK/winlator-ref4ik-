package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class BlurEffect extends Effect {
    private float intensity;
    private int samples;

    public BlurEffect() {
        super();
        this.intensity = 1.0f; // Default blur intensity
        this.samples = 5;      // Default number of samples for blur
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new BlurEffectMaterial();
    }

    // Getters and Setters
    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public int getSamples() {
        return samples;
    }

    public void setSamples(int samples) {
        this.samples = samples;
    }

    // Inner class implementing the Blur effect shader material
    private class BlurEffectMaterial extends ScreenMaterial {
        public BlurEffectMaterial() {
            super();
            setUniformNames("intensity", "samples", "resolution", "screenTexture");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform float intensity;",
                    "uniform int samples;",
                    "uniform vec2 resolution;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    vec4 color = vec4(0.0);",
                    "    float total = 0.0;",
                    "    float offset = intensity / resolution.x;",
                    "    // Gaussian-like blur with weighted samples",
                    "    for (int i = 0; i < samples; i++) {",
                    "        float weight = 1.0 - abs(float(i) - float(samples - 1) / 2.0) / float(samples - 1);",
                    "        vec2 offsetCoord = vec2(offset * float(i - samples / 2), 0.0);",
                    "        color += texture2D(screenTexture, vUV + offsetCoord) * weight;",
                    "        total += weight;",
                    "    }",
                    "    // Vertical pass",
                    "    vec4 vertColor = vec4(0.0);",
                    "    float vertTotal = 0.0;",
                    "    for (int i = 0; i < samples; i++) {",
                    "        float weight = 1.0 - abs(float(i) - float(samples - 1) / 2.0) / float(samples - 1);",
                    "        vec2 offsetCoord = vec2(0.0, offset * float(i - samples / 2));",
                    "        vertColor += texture2D(screenTexture, vUV + offsetCoord) * weight;",
                    "        vertTotal += weight;",
                    "    }",
                    "    // Combine horizontal and vertical passes",
                    "    vec4 finalColor = (color / total + vertColor / vertTotal) / 2.0;",
                    "    gl_FragColor = vec4(finalColor.rgb, texture2D(screenTexture, vUV).a);",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();

            float intensity = BlurEffect.this.getIntensity();
            int samples = BlurEffect.this.getSamples();

            // Clamp the values to ensure they are within a reasonable range
            intensity = Math.max(0.0f, Math.min(intensity, 10.0f)); // Clamping between 0.0 and 10.0
            samples = Math.max(1, Math.min(samples, 20)); // Clamping between 1 and 20

            setUniformFloat("intensity", intensity);
            setUniformInt("samples", samples);
            setUniformVec2("resolution", 1920.0f, 1080.0f); // Default resolution, can be adjusted as needed
        }
    }
}