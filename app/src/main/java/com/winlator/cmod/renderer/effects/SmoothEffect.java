package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.material.ScreenMaterial;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public class SmoothEffect extends Effect {
    private float smoothness = 1.0f;

    public float getSmoothness() {
        return smoothness;
    }

    public void setSmoothness(float smoothness) {
        this.smoothness = smoothness;
    }

    @Override
    protected ShaderMaterial createMaterial() {
        return new SmoothMaterial();
    }

    private class SmoothMaterial extends ScreenMaterial {
        public SmoothMaterial() {
            super();
            setUniformNames("resolution", "screenTexture", "smoothness");
        }

        @Override
        protected String getFragmentShader() {
            return String.join("\n", new CharSequence[]{
                    "precision highp float;",
                    "uniform sampler2D screenTexture;",
                    "uniform vec2 resolution;",
                    "uniform float smoothness;",
                    "varying vec2 vUV;",
                    "void main() {",
                    "    vec2 texelSize = vec2(1.0) / resolution;",
                    "    float s = clamp(smoothness, 0.0, 2.0);",
                    "    vec4 center = texture2D(screenTexture, vUV);",
                    "    vec4 topLeft     = texture2D(screenTexture, vUV + vec2(-texelSize.x, -texelSize.y) * s);",
                    "    vec4 topRight    = texture2D(screenTexture, vUV + vec2( texelSize.x, -texelSize.y) * s);",
                    "    vec4 bottomLeft  = texture2D(screenTexture, vUV + vec2(-texelSize.x,  texelSize.y) * s);",
                    "    vec4 bottomRight = texture2D(screenTexture, vUV + vec2( texelSize.x,  texelSize.y) * s);",
                    "    vec4 top    = texture2D(screenTexture, vUV + vec2(0.0, -texelSize.y) * s);",
                    "    vec4 bottom = texture2D(screenTexture, vUV + vec2(0.0,  texelSize.y) * s);",
                    "    vec4 left   = texture2D(screenTexture, vUV + vec2(-texelSize.x, 0.0) * s);",
                    "    vec4 right  = texture2D(screenTexture, vUV + vec2( texelSize.x, 0.0) * s);",
                    "    vec4 corners = (topLeft + topRight + bottomLeft + bottomRight) * 0.125;",
                    "    vec4 sides   = (top + bottom + left + right) * 0.125;",
                    "    vec4 smoothed = center * 0.5 + corners + sides;",
                    "    gl_FragColor = smoothed;",
                    "}"
            });
        }

        @Override
        public void use() {
            super.use();
            setUniformFloat("smoothness", Math.max(0.0f, Math.min(smoothness, 2.0f)));
        }
    }
}