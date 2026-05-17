package com.winlator.cmod.inputcontrols;

/**
 * Класс для настроек гироскопа
 */
public class GyroSettings {
    private boolean enabled = false;
    private float sensitivity = 1.0f;
    
    public GyroSettings() {
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public float getSensitivity() {
        return sensitivity;
    }
    
    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
    }
}
