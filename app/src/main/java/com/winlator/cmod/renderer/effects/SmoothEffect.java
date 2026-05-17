package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class SmoothEffect extends Effect {
    private float smoothness;

    public SmoothEffect() {
        super();
        this.smoothness = 1.0f; // Default smoothness level (0.0 - 2.0)
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new SmoothEffectMaterial();
    }

    // Getters and Setters
    public float getSmoothness() {
        return smoothness;
    }

    public void setSmoothness(float smoothness) {
        this.smoothness = smoothness;
    }

    // Inner class implementing the Smooth effect shader material
    private class SmoothEffectMaterial extends ScreenMaterial {
        public SmoothEffectMaterial() {
            super();
            setUniformNames("smoothness", "resolution", "screenTexture");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform float smoothness;",
                    "uniform vec2 resolution;",
                    "varying vec2 vUV;",
                    "",
                    "void main() {",
                    "    // Calculate texel size",
                    "    vec2 texelSize = vec2(1.0) / resolution;",
                    "    ",
                    "    // Sample the center pixel",
                    "    vec4 center = texture2D(screenTexture, vUV);",
                    "    ",
                    "    // Bilinear interpolation with 4 neighboring pixels",
                    "    vec4 topLeft = texture2D(screenTexture, vUV + vec2(-texelSize.x, -texelSize.y) * smoothness);",
                    "    vec4 topRight = texture2D(screenTexture, vUV + vec2(texelSize.x, -texelSize.y) * smoothness);",
                    "    vec4 bottomLeft = texture2D(screenTexture, vUV + vec2(-texelSize.x, texelSize.y) * smoothness);",
                    "    vec4 bottomRight = texture2D(screenTexture, vUV + vec2(texelSize.x, texelSize.y) * smoothness);",
                    "    ",
                    "    // Additional diagonal samples for smoother result",
                    "    vec4 top = texture2D(screenTexture, vUV + vec2(0.0, -texelSize.y) * smoothness);",
                    "    vec4 bottom = texture2D(screenTexture, vUV + vec2(0.0, texelSize.y) * smoothness);",
                    "    vec4 left = texture2D(screenTexture, vUV + vec2(-texelSize.x, 0.0) * smoothness);",
                    "    vec4 right = texture2D(screenTexture, vUV + vec2(texelSize.x, 0.0) * smoothness);",
                    "    ",
                    "    // Weighted average for smooth interpolation",
                    "    vec4 corners = (topLeft + topRight + bottomLeft + bottomRight) * 0.125;",
                    "    vec4 sides = (top + bottom + left + right) * 0.125;",
                    "    vec4 smoothed = center * 0.5 + corners + sides;",
                    "    ",
                    "    // Output the smoothed color",
                    "    gl_FragColor = smoothed;",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();

            float smoothness = SmoothEffect.this.getSmoothness();

            // Clamp the smoothness value to ensure it's within a reasonable range
            smoothness = Math.max(0.0f, Math.min(smoothness, 2.0f)); // Clamping between 0.0 and 2.0

            setUniformFloat("smoothness", smoothness);
            setUniformVec2("resolution", 1920.0f, 1080.0f); // Default resolution, can be adjusted as needed
        }
    }
}
