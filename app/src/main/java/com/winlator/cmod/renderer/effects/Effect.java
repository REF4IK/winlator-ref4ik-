package com.winlator.cmod.renderer.effects;

import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.material.ShaderMaterial;

public abstract class Effect {
    // Instance field for the shader material
    private ShaderMaterial material;

    // Constructor
    public Effect() {
        // Initialize the material (if needed)
    }

    // Abstract method to be implemented by subclasses for creating the material
    protected ShaderMaterial createMaterial() {
        // Returning null indicates that the subclass is responsible for creating the material
        return null;
    }

    // Returns the material associated with this effect
    public ShaderMaterial getMaterial() {
        // If material is not initialized, create it using the abstract method
        if (material == null) {
            material = createMaterial();
        }
        return material;
    }

    /**
     * Hook called by EffectComposer after the standard uniforms (screenTexture, resolution)
     * have been set, but before the quad is drawn. Subclasses can set their own uniforms here.
     */
    public void onUse(ShaderMaterial material, GLRenderer renderer) {
    }
}
