package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class SharpenEffect extends Effect {
    private float intensity;

    public SharpenEffect() {
        super();
        this.intensity = 1.0f; // Default sharpen intensity
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new SharpenEffectMaterial();
    }

    // Getters and Setters
    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    // Inner class implementing the Sharpen effect shader material
    private class SharpenEffectMaterial extends ScreenMaterial {
        public SharpenEffectMaterial() {
            super();
            setUniformNames("intensity", "screenTexture", "resolution");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform float intensity;",
                    "uniform vec2 resolution;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    vec2 texelSize = 1.0 / resolution;",
                    "    vec4 color = texture2D(screenTexture, vUV);",
                    "    // Sample neighboring pixels",
                    "    vec4 north = texture2D(screenTexture, vUV + vec2(0.0, texelSize.y));",
                    "    vec4 south = texture2D(screenTexture, vUV - vec2(0.0, texelSize.y));",
                    "    vec4 east = texture2D(screenTexture, vUV + vec2(texelSize.x, 0.0));",
                    "    vec4 west = texture2D(screenTexture, vUV - vec2(texelSize.x, 0.0));",
                    "    // Apply sharpening kernel",
                    "    vec4 sharpened = color * (1.0 + 4.0 * intensity) - (north + south + east + west) * intensity;",
                    "    gl_FragColor = sharpened;",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();

            float intensity = SharpenEffect.this.getIntensity();

            // Clamp the values to ensure they are within a reasonable range
            intensity = Math.max(0.0f, Math.min(intensity, 5.0f)); // Clamping between 0.0 and 5.0

            setUniformFloat("intensity", intensity);
            setUniformVec2("resolution", 1920.0f, 1080.0f); // Default resolution, can be adjusted as needed
        }
    }
}