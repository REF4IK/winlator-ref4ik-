package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class GrayscaleEffect extends Effect {
    private float intensity;

    public GrayscaleEffect() {
        super();
        this.intensity = 1.0f; // Default grayscale intensity (full effect)
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new GrayscaleEffectMaterial();
    }

    // Getters and Setters
    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    // Inner class implementing the Grayscale effect shader material
    private class GrayscaleEffectMaterial extends ScreenMaterial {
        public GrayscaleEffectMaterial() {
            super();
            setUniformNames("intensity", "screenTexture");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform float intensity;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    vec4 texelColor = texture2D(screenTexture, vUV);",
                    "    vec3 color = texelColor.rgb;",
                    "    // Calculate grayscale using luminance weights",
                    "    float gray = dot(color, vec3(0.299, 0.587, 0.114));",
                    "    vec3 grayscale = vec3(gray);",
                    "    // Blend original color with grayscale based on intensity",
                    "    vec3 finalColor = mix(color, grayscale, intensity);",
                    "    gl_FragColor = vec4(finalColor, texelColor.a);",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();

            float intensity = GrayscaleEffect.this.getIntensity();

            // Clamp the values to ensure they are within a reasonable range
            intensity = Math.max(0.0f, Math.min(intensity, 1.0f)); // Clamping between 0.0 and 1.0

            setUniformFloat("intensity", intensity);
        }
    }
}