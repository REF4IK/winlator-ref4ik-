package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class PixelateEffect extends Effect {
    private float pixelSize;

    public PixelateEffect() {
        super();
        this.pixelSize = 5.0f; // Default pixel size
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new PixelateEffectMaterial();
    }

    // Getters and Setters
    public float getPixelSize() {
        return pixelSize;
    }

    public void setPixelSize(float pixelSize) {
        this.pixelSize = pixelSize;
    }

    // Inner class implementing the Pixelate effect shader material
    private class PixelateEffectMaterial extends ScreenMaterial {
        public PixelateEffectMaterial() {
            super();
            setUniformNames("pixelSize", "resolution", "screenTexture");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform float pixelSize;",
                    "uniform vec2 resolution;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    // Calculate the pixelated UV coordinates",
                    "    vec2 pixelatedUV = floor(vUV * resolution / pixelSize) * pixelSize / resolution;",
                    "    // Sample the texture at the pixelated coordinates",
                    "    vec4 texelColor = texture2D(screenTexture, pixelatedUV);",
                    "    gl_FragColor = texelColor;",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();

            float pixelSize = PixelateEffect.this.getPixelSize();

            // Clamp the values to ensure they are within a reasonable range
            pixelSize = Math.max(1.0f, Math.min(pixelSize, 50.0f)); // Clamping between 1.0 and 50.0

            setUniformFloat("pixelSize", pixelSize);
            setUniformVec2("resolution", 1920.0f, 1080.0f); // Default resolution, can be adjusted as needed
        }
    }
}