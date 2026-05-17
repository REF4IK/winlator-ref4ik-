package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class VignetteEffect extends Effect {
    private float intensity;
    private float radius;

    public VignetteEffect() {
        super();
        this.intensity = 0.5f; // Default vignette intensity
        this.radius = 0.8f;    // Default vignette radius
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new VignetteEffectMaterial();
    }

    // Getters and Setters
    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    // Inner class implementing the Vignette effect shader material
    private class VignetteEffectMaterial extends ScreenMaterial {
        public VignetteEffectMaterial() {
            super();
            setUniformNames("intensity", "radius", "screenTexture");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform float intensity;",
                    "uniform float radius;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    vec4 texelColor = texture2D(screenTexture, vUV);",
                    "    vec2 coord = vUV - 0.5;", // Center coordinates
                    "    float dist = length(coord * 2.0);", // Distance from center (normalized)
                    "    float vignette = smoothstep(radius, radius - intensity, dist);", // Vignette calculation
                    "    vec3 color = texelColor.rgb * vignette;", // Apply vignette effect
                    "    gl_FragColor = vec4(color, texelColor.a);", // Output final color
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();

            float intensity = VignetteEffect.this.getIntensity();
            float radius = VignetteEffect.this.getRadius();

            // Clamp the values to ensure they are within a reasonable range
            intensity = Math.max(0.0f, Math.min(intensity, 1.0f)); // Clamping between 0.0 and 1.0
            radius = Math.max(0.0f, Math.min(radius, 1.0f)); // Clamping between 0.0 and 1.0

            setUniformFloat("intensity", intensity);
            setUniformFloat("radius", radius);
        }
    }
}