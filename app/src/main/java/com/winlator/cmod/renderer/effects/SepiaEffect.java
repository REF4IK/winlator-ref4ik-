package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class SepiaEffect extends Effect {
    private float intensity = 1.0f;

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new SepiaMaterial();
    }

    private class SepiaMaterial extends ScreenMaterial {
        public SepiaMaterial() {
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
                    "    float sepiaR = (color.r * 0.393) + (color.g * 0.769) + (color.b * 0.189);",
                    "    float sepiaG = (color.r * 0.349) + (color.g * 0.686) + (color.b * 0.168);",
                    "    float sepiaB = (color.r * 0.272) + (color.g * 0.534) + (color.b * 0.131);",
                    "    vec3 sepiaColor = vec3(sepiaR, sepiaG, sepiaB);",
                    "    vec3 finalColor = mix(color, sepiaColor, intensity);",
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