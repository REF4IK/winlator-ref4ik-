package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class BlurEffect extends Effect {
    private float intensity = 2.0f;
    private float samples = 5.0f;

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public float getSamples() {
        return samples;
    }

    public void setSamples(float samples) {
        this.samples = samples;
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new BlurMaterial();
    }

    private class BlurMaterial extends ScreenMaterial {
        public BlurMaterial() {
            super();
            setUniformNames("resolution", "screenTexture", "intensity", "samples");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform vec2 resolution;",
                    "uniform float intensity;",
                    "uniform float samples;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    int n = int(clamp(samples, 1.0, 20.0));",
                    "    vec4 color = vec4(0.0);",
                    "    float total = 0.0;",
                    "    float offset = intensity / resolution.x;",
                    "    for (int i = 0; i < n; i++) {",
                    "        float weight = 1.0 - abs(float(i) - float(n - 1) / 2.0) / float(n - 1);",
                    "        vec2 offsetCoord = vec2(offset * float(i - n / 2), 0.0);",
                    "        color += texture2D(screenTexture, vUV + offsetCoord) * weight;",
                    "        total += weight;",
                    "    }",
                    "    vec4 vertColor = vec4(0.0);",
                    "    float vertTotal = 0.0;",
                    "    for (int j = 0; j < n; j++) {",
                    "        float weight = 1.0 - abs(float(j) - float(n - 1) / 2.0) / float(n - 1);",
                    "        vec2 offsetCoord = vec2(0.0, offset * float(j - n / 2));",
                    "        vertColor += texture2D(screenTexture, vUV + offsetCoord) * weight;",
                    "        vertTotal += weight;",
                    "    }",
                    "    vec4 finalColor = (color / total + vertColor / vertTotal) / 2.0;",
                    "    gl_FragColor = vec4(finalColor.rgb, texture2D(screenTexture, vUV).a);",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();
            setUniformFloat("intensity", Math.max(0.0f, Math.min(intensity, 10.0f)));
            setUniformFloat("samples", Math.max(1.0f, Math.min(samples, 20.0f)));
        }
    }
}