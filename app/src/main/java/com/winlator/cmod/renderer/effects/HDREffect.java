package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class HDREffect extends Effect {
    private float contrast;

    public HDREffect() {
        super();
        this.contrast = 0.4f; // Default HDR contrast value (40)
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new HDREffectMaterial();
    }

    // Getters and Setters
    public float getContrast() {
        return contrast;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    // Inner class implementing the HDR effect shader material
    private class HDREffectMaterial extends ScreenMaterial {
        public HDREffectMaterial() {
            super();
            setUniformNames("contrast", "screenTexture");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform float contrast;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    vec4 color = texture2D(screenTexture, vUV);",
                    "    // HDR effect - increase contrast",
                    "    color.rgb = (color.rgb - 0.5) * clamp(contrast + 1.0, 0.5, 2.0) + 0.5;",
                    "    gl_FragColor = color;",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();

            float contrast = HDREffect.this.getContrast();

            // Clamp the values to ensure they are within a reasonable range
            contrast = Math.max(0.0f, Math.min(contrast, 2.0f)); // Clamping between 0.0 and 2.0

            setUniformFloat("contrast", contrast);
        }
    }
}
