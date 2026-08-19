package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class VignetteEffect extends Effect {
    private float intensity = 0.5f;
    private float radius = 0.5f;

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = radius;
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new VignetteMaterial();
    }

    private class VignetteMaterial extends ScreenMaterial {
        public VignetteMaterial() {
            super();
            setUniformNames("resolution", "screenTexture", "intensity", "radius");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform float intensity;",
                    "uniform float radius;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    vec4 texelColor = texture2D(screenTexture, vUV);",
                    "    vec2 coord = vUV - 0.5;",
                    "    float dist = length(coord * 2.0);",
                    "    float vignette = smoothstep(radius, radius - intensity, dist);",
                    "    vec3 color = texelColor.rgb * vignette;",
                    "    gl_FragColor = vec4(color, texelColor.a);",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();
            setUniformFloat("intensity", Math.max(0.0f, Math.min(intensity, 1.0f)));
            setUniformFloat("radius", Math.max(0.0f, Math.min(radius, 1.0f)));
        }
    }
}