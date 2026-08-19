package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class GrayscaleEffect extends Effect {
    private float intensity = 1.0f;

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new GrayscaleMaterial();
    }

    private class GrayscaleMaterial extends ScreenMaterial {
        public GrayscaleMaterial() {
            super();
            setUniformNames("resolution", "screenTexture", "intensity");
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
                    "    float gray = dot(color, vec3(0.299, 0.587, 0.114));",
                    "    vec3 grayscale = vec3(gray);",
                    "    vec3 finalColor = mix(color, grayscale, intensity);",
                    "    gl_FragColor = vec4(finalColor, texelColor.a);",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();
            setUniformFloat("intensity", Math.max(0.0f, Math.min(intensity, 1.0f)));
        }
    }
}