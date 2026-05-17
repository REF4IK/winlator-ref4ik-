package com.winlator.cmod.core;

/**
 * HDR Color Space Utilities - handles color space conversions and tone mapping for HDR10
 */
public class HDRColorUtils {
    
    // Standard gamma values
    public static final float GAMMA_SRGB = 2.2f;
    public static final float GAMMA_BT709 = 2.4f;
    public static final float GAMMA_BT2020 = 2.4f;
    
    // Standard white point luminance values (nits)
    public static final float SDR_WHITE_LUMINANCE = 100.0f;
    public static final float HDR_PEAK_LUMINANCE = 1000.0f;
    public static final float HDR_MAX_LUMINANCE = 10000.0f;
    
    // Color primaries for different color spaces
    // Format: [Red X, Red Y, Green X, Green Y, Blue X, Blue Y, White X, White Y]
    
    // sRGB/Rec. 709 primaries
    private static final float[] SRGB_PRIMARIES = {
        0.6400f, 0.3300f,  // Red
        0.3000f, 0.6000f,  // Green  
        0.1500f, 0.0600f,  // Blue
        0.3127f, 0.3290f   // White point (D65)
    };
    
    // DCI-P3 primaries
    private static final float[] P3_PRIMARIES = {
        0.6800f, 0.3200f,  // Red
        0.2650f, 0.6900f,  // Green
        0.1500f, 0.0600f,  // Blue
        0.3127f, 0.3290f   // White point (D65)
    };
    
    // Rec. 2020 primaries
    private static final float[] BT2020_PRIMARIES = {
        0.7080f, 0.2920f,  // Red
        0.1700f, 0.7970f,  // Green
        0.1310f, 0.0460f,  // Blue
        0.3127f, 0.3290f   // White point (D65)
    };
    
    /**
     * Convert sRGB to linear RGB
     */
    public static float[] sRGBToLinear(float[] srgb) {
        float[] linear = new float[srgb.length];
        for (int i = 0; i < srgb.length; i++) {
            linear[i] = sRGBToLinear(srgb[i]);
        }
        return linear;
    }
    
    /**
     * Convert single sRGB value to linear
     */
    public static float sRGBToLinear(float srgb) {
        if (srgb <= 0.04045f) {
            return srgb / 12.92f;
        } else {
            return (float) Math.pow((srgb + 0.055f) / 1.055f, 2.4f);
        }
    }
    
    /**
     * Convert linear RGB to sRGB
     */
    public static float[] linearToSRGB(float[] linear) {
        float[] srgb = new float[linear.length];
        for (int i = 0; i < linear.length; i++) {
            srgb[i] = linearToSRGB(linear[i]);
        }
        return srgb;
    }
    
    /**
     * Convert single linear value to sRGB
     */
    public static float linearToSRGB(float linear) {
        if (linear <= 0.0031308f) {
            return 12.92f * linear;
        } else {
            return 1.055f * (float) Math.pow(linear, 1.0f / 2.4f) - 0.055f;
        }
    }
    
    /**
     * Apply PQ (Perceptual Quantizer) curve for HDR10
     * SMPTE ST 2084 standard
     */
    public static float[] applyPQCurve(float[] linear) {
        float[] pq = new float[linear.length];
        for (int i = 0; i < linear.length; i++) {
            pq[i] = applyPQCurve(linear[i]);
        }
        return pq;
    }
    
    /**
     * Apply PQ curve to single value
     */
    public static float applyPQCurve(float linear) {
        // Normalize to [0, 1] range (10000 nits max)
        float normalized = Math.max(0.0f, Math.min(1.0f, linear / HDR_MAX_LUMINANCE));
        
        // PQ constants
        float m1 = 0.1593017578125f;  // 2610/16384
        float m2 = 78.84375f;         // 2523/32
        float c1 = 0.8359375f;        // 3424/4096
        float c2 = 18.8515625f;       // 2413/128
        float c3 = 18.6875f;          // 2392/128
        
        float powM1 = (float) Math.pow(normalized, m1);
        float numerator = c1 + c2 * powM1;
        float denominator = 1.0f + c3 * powM1;
        
        return (float) Math.pow(numerator / denominator, m2);
    }
    
    /**
     * Remove PQ curve (inverse operation)
     */
    public static float[] removePQCurve(float[] pq) {
        float[] linear = new float[pq.length];
        for (int i = 0; i < pq.length; i++) {
            linear[i] = removePQCurve(pq[i]);
        }
        return linear;
    }
    
    /**
     * Remove PQ curve from single value
     */
    public static float removePQCurve(float pq) {
        // PQ constants
        float m1 = 0.1593017578125f;
        float m2 = 78.84375f;
        float c1 = 0.8359375f;
        float c2 = 18.8515625f;
        float c3 = 18.6875f;
        
        float powInvM2 = (float) Math.pow(Math.max(0.0f, pq), 1.0f / m2);
        float numerator = Math.max(0.0f, powInvM2 - c1);
        float denominator = c2 - c3 * powInvM2;
        
        float normalized = (float) Math.pow(numerator / Math.max(1e-10f, denominator), 1.0f / m1);
        
        return normalized * HDR_MAX_LUMINANCE;
    }
    
    /**
     * Reinhard tone mapping operator
     */
    public static float[] reinhardToneMapping(float[] hdr, float maxLuminance) {
        float[] sdr = new float[hdr.length];
        for (int i = 0; i < hdr.length; i++) {
            sdr[i] = reinhardToneMapping(hdr[i], maxLuminance);
        }
        return sdr;
    }
    
    /**
     * Reinhard tone mapping for single value
     */
    public static float reinhardToneMapping(float hdr, float maxLuminance) {
        float normalized = hdr / maxLuminance;
        return normalized / (1.0f + normalized);
    }
    
    /**
     * Hable (Uncharted 2) tone mapping operator
     */
    public static float[] hableToneMapping(float[] hdr, float exposure) {
        float[] sdr = new float[hdr.length];
        for (int i = 0; i < hdr.length; i++) {
            sdr[i] = hableToneMapping(hdr[i], exposure);
        }
        return sdr;
    }
    
    /**
     * Hable tone mapping for single value
     */
    public static float hableToneMapping(float hdr, float exposure) {
        float A = 0.15f;  // Shoulder strength
        float B = 0.50f;  // Linear strength  
        float C = 0.10f;  // Linear angle
        float D = 0.20f;  // Toe strength
        float E = 0.02f;  // Toe numerator
        float F = 0.30f;  // Toe denominator
        
        float x = hdr * exposure;
        float numerator = x * (A * x + C * B) + D * E;
        float denominator = x * (A * x + B) + D * F;
        
        float whiteScale = 1.0f / hableToneMappingFunction(11.2f, A, B, C, D, E, F);
        
        return (numerator / Math.max(denominator, 1e-10f) - E / F) * whiteScale;
    }
    
    private static float hableToneMappingFunction(float x, float A, float B, float C, float D, float E, float F) {
        return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
    }
    
    /**
     * ACES (Academy Color Encoding System) tone mapping
     */
    public static float[] acesToneMapping(float[] hdr) {
        float[] sdr = new float[hdr.length];
        for (int i = 0; i < hdr.length; i++) {
            sdr[i] = acesToneMapping(hdr[i]);
        }
        return sdr;
    }
    
    /**
     * ACES tone mapping for single value
     */
    public static float acesToneMapping(float hdr) {
        float a = 2.51f;
        float b = 0.03f;
        float c = 2.43f;
        float d = 0.59f;
        float e = 0.14f;
        
        float numerator = hdr * (a * hdr + b);
        float denominator = hdr * (c * hdr + d) + e;
        
        return Math.max(0.0f, Math.min(1.0f, numerator / Math.max(denominator, 1e-10f)));
    }
    
    /**
     * Convert between color spaces using matrix transformation
     */
    public static float[] convertColorSpace(float[] rgb, float[] fromMatrix, float[] toMatrix) {
        // This is a simplified version - in practice you'd use proper 3x3 matrix operations
        // For now, return the input (identity transformation)
        return rgb.clone();
    }
    
    /**
     * Get color space conversion matrix for sRGB to BT.2020
     */
    public static float[] getSRGBToBT2020Matrix() {
        // Simplified matrix - in practice this would be a full 3x3 transformation matrix
        return new float[] {
            0.627404f, 0.329283f, 0.043313f,
            0.069097f, 0.919540f, 0.011362f,
            0.016391f, 0.088013f, 0.895595f
        };
    }
    
    /**
     * Get color space conversion matrix for BT.2020 to sRGB
     */
    public static float[] getBT2020ToSRGBMatrix() {
        // Simplified matrix - in practice this would be a full 3x3 transformation matrix
        return new float[] {
            1.703831f, -0.621793f, -0.082038f,
            -0.130179f, 1.140895f, -0.010716f,
            -0.023974f, -0.128940f, 1.152914f
        };
    }
    
    /**
     * Apply color matrix transformation
     */
    public static float[] applyColorMatrix(float[] rgb, float[] matrix) {
        if (rgb.length != 3 || matrix.length != 9) {
            throw new IllegalArgumentException("Invalid input dimensions");
        }
        
        float[] result = new float[3];
        result[0] = matrix[0] * rgb[0] + matrix[1] * rgb[1] + matrix[2] * rgb[2];
        result[1] = matrix[3] * rgb[0] + matrix[4] * rgb[1] + matrix[5] * rgb[2];
        result[2] = matrix[6] * rgb[0] + matrix[7] * rgb[1] + matrix[8] * rgb[2];
        
        return result;
    }
    
    /**
     * Calculate luminance from RGB values
     */
    public static float calculateLuminance(float r, float g, float b) {
        // Rec. 709 luma coefficients
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }
    
    /**
     * Calculate luminance for BT.2020 color space
     */
    public static float calculateLuminanceBT2020(float r, float g, float b) {
        // Rec. 2020 luma coefficients
        return 0.2627f * r + 0.6780f * g + 0.0593f * b;
    }
    
    /**
     * Clamp RGB values to valid range
     */
    public static float[] clampRGB(float[] rgb) {
        float[] clamped = new float[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            clamped[i] = Math.max(0.0f, Math.min(1.0f, rgb[i]));
        }
        return clamped;
    }
    
    /**
     * Apply gamma correction
     */
    public static float[] applyGamma(float[] linear, float gamma) {
        float[] gamma_corrected = new float[linear.length];
        for (int i = 0; i < linear.length; i++) {
            gamma_corrected[i] = (float) Math.pow(Math.max(0.0f, linear[i]), 1.0f / gamma);
        }
        return gamma_corrected;
    }
    
    /**
     * Remove gamma correction
     */
    public static float[] removeGamma(float[] gamma_corrected, float gamma) {
        float[] linear = new float[gamma_corrected.length];
        for (int i = 0; i < gamma_corrected.length; i++) {
            linear[i] = (float) Math.pow(Math.max(0.0f, gamma_corrected[i]), gamma);
        }
        return linear;
    }
    
    /**
     * Convert RGB to HSV
     */
    public static float[] rgbToHsv(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        
        float h = 0.0f; // Hue
        float s = max == 0.0f ? 0.0f : delta / max; // Saturation
        float v = max; // Value
        
        if (delta != 0.0f) {
            if (max == r) {
                h = ((g - b) / delta) % 6.0f;
            } else if (max == g) {
                h = (b - r) / delta + 2.0f;
            } else {
                h = (r - g) / delta + 4.0f;
            }
            h *= 60.0f;
            if (h < 0.0f) h += 360.0f;
        }
        
        return new float[] { h, s, v };
    }
    
    /**
     * Convert HSV to RGB
     */
    public static float[] hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1.0f - Math.abs((h / 60.0f) % 2.0f - 1.0f));
        float m = v - c;
        
        float r = 0.0f, g = 0.0f, b = 0.0f;
        
        if (h >= 0.0f && h < 60.0f) {
            r = c; g = x; b = 0.0f;
        } else if (h >= 60.0f && h < 120.0f) {
            r = x; g = c; b = 0.0f;
        } else if (h >= 120.0f && h < 180.0f) {
            r = 0.0f; g = c; b = x;
        } else if (h >= 180.0f && h < 240.0f) {
            r = 0.0f; g = x; b = c;
        } else if (h >= 240.0f && h < 300.0f) {
            r = x; g = 0.0f; b = c;
        } else if (h >= 300.0f && h < 360.0f) {
            r = c; g = 0.0f; b = x;
        }
        
        return new float[] { r + m, g + m, b + m };
    }
    
    /**
     * Apply saturation boost
     */
    public static float[] applySaturationBoost(float[] rgb, float boost) {
        float[] hsv = rgbToHsv(rgb[0], rgb[1], rgb[2]);
        hsv[1] = Math.max(0.0f, Math.min(1.0f, hsv[1] * boost));
        return hsvToRgb(hsv[0], hsv[1], hsv[2]);
    }
    
    /**
     * Apply contrast enhancement
     */
    public static float[] applyContrastEnhancement(float[] rgb, float contrast) {
        float[] enhanced = new float[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            enhanced[i] = Math.max(0.0f, Math.min(1.0f, 
                (rgb[i] - 0.5f) * contrast + 0.5f));
        }
        return enhanced;
    }
}