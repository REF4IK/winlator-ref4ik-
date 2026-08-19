package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class PixelateEffect extends Effect {
    private float pixelSize = 4.0f;

    public float getPixelSize() {
        return pixelSize;
    }

    public void setPixelSize(float pixelSize) {
        this.pixelSize = pixelSize;
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new PixelateMaterial();
    }

    private class PixelateMaterial extends ScreenMaterial {
        public PixelateMaterial() {
            super();
            setUniformNames("resolution", "screenTexture", "pixelSize");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform vec2 resolution;",
                    "uniform float pixelSize;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    float pSize = clamp(pixelSize, 1.0, 50.0);",
                    "    vec2 pixelatedUV = floor(vUV * resolution / pSize) * pSize / resolution;",
                    "    gl_FragColor = texture2D(screenTexture, pixelatedUV);",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();
            setUniformFloat("pixelSize", Math.max(1.0f, Math.min(pixelSize, 50.0f)));
        }
    }
}