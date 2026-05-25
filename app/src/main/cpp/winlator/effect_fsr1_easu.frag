#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

vec3 FsrEasuCF(vec2 p) { return texture(screenTexture, p).rgb; }

void FsrEasuCon(out vec4 con0, out vec4 con1, out vec4 con2, out vec4 con3,
                vec2 inputViewportInPixels, vec2 inputSizeInPixels, vec2 outputSizeInPixels) {
    con0 = vec4(
        inputViewportInPixels.x / outputSizeInPixels.x,
        inputViewportInPixels.y / outputSizeInPixels.y,
        0.5 * inputViewportInPixels.x / outputSizeInPixels.x - 0.5,
        0.5 * inputViewportInPixels.y / outputSizeInPixels.y - 0.5
    );
    con1 = vec4(1.0 / inputSizeInPixels.x, 1.0 / inputSizeInPixels.y, 1.0 / inputSizeInPixels.x, -1.0 / inputSizeInPixels.y);
    con2 = vec4(-1.0 / inputSizeInPixels.x, 2.0 / inputSizeInPixels.y, 1.0 / inputSizeInPixels.x, 2.0 / inputSizeInPixels.y);
    con3 = vec4(0.0, 4.0 / inputSizeInPixels.y, 0.0, 0.0);
}

void FsrEasuTapF(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len, float lob, float clp, vec3 c) {
    vec2 v = vec2(off.x * dir.x + off.y * dir.y, off.x * (-dir.y) + off.y * dir.x);
    v *= len;
    float d2 = min(dot(v, v), clp);
    float wB = 0.4 * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = 1.5625 * wB - 0.5625;
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void FsrEasuSetF(inout vec2 dir, inout float len, float w, float lA, float lB, float lC, float lD, float lE) {
    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = max(abs(dc), abs(cb));
    lenX = 1.0 / max(lenX, 1e-6);
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) * lenX, 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;
    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = max(abs(ec), abs(ca));
    lenY = 1.0 / max(lenY, 1e-6);
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) * lenY, 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

void FsrEasuF(out vec3 pix, vec2 ip, vec4 con0, vec4 con1, vec4 con2, vec4 con3) {
    vec2 pp = ip * con0.xy + con0.zw;
    vec2 fp = floor(pp);
    pp -= fp;
    vec2 p0 = fp * con1.xy + con1.zw;
    vec2 p1 = p0 + con2.xy;
    vec2 p2 = p0 + con2.zw;
    vec2 p3 = p0 + con3.xy;
    vec4 off = vec4(-0.5, 0.5, -0.5, 0.5) * con1.xxyy;
    vec3 bC = FsrEasuCF(p0 + off.xw); float bL = bC.b * 0.5 + (bC.r * 0.5 + bC.g);
    vec3 cC = FsrEasuCF(p0 + off.yw); float cL = cC.b * 0.5 + (cC.r * 0.5 + cC.g);
    vec3 iC = FsrEasuCF(p1 + off.xw); float iL = iC.b * 0.5 + (iC.r * 0.5 + iC.g);
    vec3 jC = FsrEasuCF(p1 + off.yw); float jL = jC.b * 0.5 + (jC.r * 0.5 + jC.g);
    vec3 fC = FsrEasuCF(p1 + off.yz); float fL = fC.b * 0.5 + (fC.r * 0.5 + fC.g);
    vec3 eC = FsrEasuCF(p1 + off.xz); float eL = eC.b * 0.5 + (eC.r * 0.5 + eC.g);
    vec3 kC = FsrEasuCF(p2 + off.xw); float kL = kC.b * 0.5 + (kC.r * 0.5 + kC.g);
    vec3 lC = FsrEasuCF(p2 + off.yw); float lL = lC.b * 0.5 + (lC.r * 0.5 + lC.g);
    vec3 hC = FsrEasuCF(p2 + off.yz); float hL = hC.b * 0.5 + (hC.r * 0.5 + hC.g);
    vec3 gC = FsrEasuCF(p2 + off.xz); float gL = gC.b * 0.5 + (gC.r * 0.5 + gC.g);
    vec3 oC = FsrEasuCF(p3 + off.yz); float oL = oC.b * 0.5 + (oC.r * 0.5 + oC.g);
    vec3 nC = FsrEasuCF(p3 + off.xz); float nL = nC.b * 0.5 + (nC.r * 0.5 + nC.g);
    vec2 dir = vec2(0.0);
    float len = 0.0;
    FsrEasuSetF(dir, len, (1.0 - pp.x) * (1.0 - pp.y), bL, eL, fL, gL, jL);
    FsrEasuSetF(dir, len, pp.x * (1.0 - pp.y), cL, fL, gL, hL, kL);
    FsrEasuSetF(dir, len, (1.0 - pp.x) * pp.y, fL, iL, jL, kL, nL);
    FsrEasuSetF(dir, len, pp.x * pp.y, gL, jL, kL, lL, oL);
    float dirR = dir.x * dir.x + dir.y * dir.y;
    bool zro = dirR < (1.0 / 32768.0);
    dirR = inversesqrt(max(dirR, 1e-6));
    if (zro) { dir = vec2(1.0, 0.0); dirR = 1.0; }
    dir *= dirR;
    len = 0.5 * len;
    len *= len;
    float stretch = (dir.x * dir.x + dir.y * dir.y) / max(max(abs(dir.x), abs(dir.y)), 1e-6);
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = 1.0 / max(lob, 1e-6);
    vec3 min4 = min(min(fC, gC), min(jC, kC));
    vec3 max4 = max(max(fC, gC), max(jC, kC));
    vec3 aC = vec3(0.0);
    float aW = 0.0;
    FsrEasuTapF(aC, aW, vec2(0.0, -1.0) - pp, dir, len2, lob, clp, bC);
    FsrEasuTapF(aC, aW, vec2(1.0, -1.0) - pp, dir, len2, lob, clp, cC);
    FsrEasuTapF(aC, aW, vec2(-1.0, 1.0) - pp, dir, len2, lob, clp, iC);
    FsrEasuTapF(aC, aW, vec2(0.0, 1.0) - pp, dir, len2, lob, clp, jC);
    FsrEasuTapF(aC, aW, vec2(0.0, 0.0) - pp, dir, len2, lob, clp, fC);
    FsrEasuTapF(aC, aW, vec2(-1.0, 0.0) - pp, dir, len2, lob, clp, eC);
    FsrEasuTapF(aC, aW, vec2(1.0, 1.0) - pp, dir, len2, lob, clp, kC);
    FsrEasuTapF(aC, aW, vec2(2.0, 1.0) - pp, dir, len2, lob, clp, lC);
    FsrEasuTapF(aC, aW, vec2(2.0, 0.0) - pp, dir, len2, lob, clp, hC);
    FsrEasuTapF(aC, aW, vec2(1.0, 0.0) - pp, dir, len2, lob, clp, gC);
    FsrEasuTapF(aC, aW, vec2(1.0, 2.0) - pp, dir, len2, lob, clp, oC);
    FsrEasuTapF(aC, aW, vec2(0.0, 2.0) - pp, dir, len2, lob, clp, nC);
    pix = min(max4, max(min4, aC / max(aW, 1e-6)));
}

void main() {
    vec2 inputResolution = vec2(pc.params[0], pc.params[1]);
    vec2 outputResolution = vec2(pc.params[2], pc.params[3]);
    float preserveAspect = pc.params[4];
    if (preserveAspect > 0.5) {
        float inputAspect = inputResolution.x / inputResolution.y;
        float outputAspect = outputResolution.x / outputResolution.y;
        vec2 scaledOutput = outputResolution;
        if (outputAspect > inputAspect) {
            scaledOutput.x = outputResolution.y * inputAspect;
        } else {
            scaledOutput.y = outputResolution.x / inputAspect;
        }
        vec2 offset = 0.5 * (outputResolution - scaledOutput);
        vec2 coord = gl_FragCoord.xy - offset;
        if (coord.x < 0.0 || coord.x > scaledOutput.x || coord.y < 0.0 || coord.y > scaledOutput.y) {
            fragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }
        vec3 color;
        vec4 con0, con1, con2, con3;
        FsrEasuCon(con0, con1, con2, con3, inputResolution, inputResolution, scaledOutput);
        FsrEasuF(color, coord, con0, con1, con2, con3);
        fragColor = vec4(color, 1.0);
    } else {
        vec3 color;
        vec4 con0, con1, con2, con3;
        FsrEasuCon(con0, con1, con2, con3, inputResolution, inputResolution, outputResolution);
        FsrEasuF(color, gl_FragCoord.xy, con0, con1, con2, con3);
        fragColor = vec4(color, texture(screenTexture, vUV).a);
    }
}
