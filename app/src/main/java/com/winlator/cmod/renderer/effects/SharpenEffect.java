package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class SharpenEffect extends Effect {
    private float intensity = 1.0f;

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new SharpenMaterial();
    }

    private class SharpenMaterial extends ScreenMaterial {
        public SharpenMaterial() {
            super();
            setUniformNames("resolution", "screenTexture", "intensity");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform vec2 resolution;",
                    "uniform float intensity;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    vec2 texelSize = 1.0 / resolution;",
                    "    vec4 color = texture2D(screenTexture, vUV);",
                    "    float amount = clamp(intensity, 0.0, 5.0);",
                    "    vec4 north = texture2D(screenTexture, vUV + vec2(0.0, texelSize.y));",
                    "    vec4 south = texture2D(screenTexture, vUV - vec2(0.0, texelSize.y));",
                    "    vec4 east  = texture2D(screenTexture, vUV + vec2(texelSize.x, 0.0));",
                    "    vec4 west  = texture2D(screenTexture, vUV - vec2(texelSize.x, 0.0));",
                    "    vec4 sharpened = color * (1.0 + 4.0 * amount) - (north + south + east + west) * amount;",
                    "    gl_FragColor = sharpened;",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();
            setUniformFloat("intensity", Math.max(0.0f, Math.min(intensity, 5.0f)));
        }
    }
}