#version 450

layout(binding = 0) uniform sampler2D screenTexture;
layout(location = 0) in vec2 vUV;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform PushConstants {
    vec2 resolution;
    float params[8];
} pc;

#define FSR_RCAS_LIMIT (0.25 - (1.0 / 16.0))

void FsrRcasCon(out float con, float sharpness) { con = exp2(-sharpness); }

vec4 FsrRcasLoadF(vec2 p) { return texture(screenTexture, (p + 0.5) / pc.resolution); }

vec3 FsrRcasF(vec2 ip, float con) {
    vec2 sp = vec2(ip);
    vec3 b = FsrRcasLoadF(sp + vec2(0.0, -1.0)).rgb;
    vec3 d = FsrRcasLoadF(sp + vec2(-1.0, 0.0)).rgb;
    vec3 e = FsrRcasLoadF(sp).rgb;
    vec3 f = FsrRcasLoadF(sp + vec2(1.0, 0.0)).rgb;
    vec3 h = FsrRcasLoadF(sp + vec2(0.0, 1.0)).rgb;
    float bL = b.b * 0.5 + (b.r * 0.5 + b.g);
    float dL = d.b * 0.5 + (d.r * 0.5 + d.g);
    float eL = e.b * 0.5 + (e.r * 0.5 + e.g);
    float fL = f.b * 0.5 + (f.r * 0.5 + f.g);
    float hL = h.b * 0.5 + (h.r * 0.5 + h.g);
    float nz = 0.25 * bL + 0.25 * dL + 0.25 * fL + 0.25 * hL - eL;
    float nzRange = max(max(max(bL, dL), eL), max(fL, hL)) - min(min(min(bL, dL), eL), min(fL, hL));
    nz = clamp(abs(nz) / max(nzRange, 1e-6), 0.0, 1.0);
    nz = -0.5 * nz + 1.0;
    float mn4R = min(min(min(b.r, d.r), f.r), h.r);
    float mn4G = min(min(min(b.g, d.g), f.g), h.g);
    float mn4B = min(min(min(b.b, d.b), f.b), h.b);
    float mx4R = max(max(max(b.r, d.r), f.r), h.r);
    float mx4G = max(max(max(b.g, d.g), f.g), h.g);
    float mx4B = max(max(max(b.b, d.b), f.b), h.b);
    float hitMinR = min(mn4R, e.r) / max(4.0 * mx4R, 1e-6);
    float hitMinG = min(mn4G, e.g) / max(4.0 * mx4G, 1e-6);
    float hitMinB = min(mn4B, e.b) / max(4.0 * mx4B, 1e-6);
    float hitMaxR = (1.0 - max(mx4R, e.r)) / min(4.0 * mn4R - 4.0, -1e-6);
    float hitMaxG = (1.0 - max(mx4G, e.g)) / min(4.0 * mn4G - 4.0, -1e-6);
    float hitMaxB = (1.0 - max(mx4B, e.b)) / min(4.0 * mn4B - 4.0, -1e-6);
    float lobeR = max(-hitMinR, hitMaxR);
    float lobeG = max(-hitMinG, hitMaxG);
    float lobeB = max(-hitMinB, hitMaxB);
    float lobe = max(-FSR_RCAS_LIMIT, min(max(max(lobeR, lobeG), lobeB), 0.0)) * con;
    float rcpL = 1.0 / (4.0 * lobe + 1.0);
    return vec3(
        (lobe * b.r + lobe * d.r + lobe * h.r + lobe * f.r + e.r) * rcpL,
        (lobe * b.g + lobe * d.g + lobe * h.g + lobe * f.g + e.g) * rcpL,
        (lobe * b.b + lobe * d.b + lobe * h.b + lobe * f.b + e.b) * rcpL
    );
}

void main() {
    float con;
    FsrRcasCon(con, pc.params[0]);
    vec3 color = FsrRcasF(floor(gl_FragCoord.xy), con);
    fragColor = vec4(color, texture(screenTexture, vUV).a);
}
