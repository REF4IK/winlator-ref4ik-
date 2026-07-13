package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.core.GPUPerformanceManager;

import java.util.Arrays;
import java.util.List;

/**
 * Диалог настройки производительности GPU
 */
public class GPUPerformanceDialog extends ContentDialog {
    private static final String PREF_GPU_PERFORMANCE_MODE = "gpu_performance_mode";
    
    private GPUPerformanceManager gpuManager;
    private Spinner sPerformanceMode;
    private TextView tvGPUInfo;
    private TextView tvCurrentFreq;
    private TextView tvMaxFreq;
    private Button btApply;
    private Button btRestore;
    
    public GPUPerformanceDialog(Context context) {
        super(context, R.layout.gpu_performance_dialog);
        this.gpuManager = new GPUPerformanceManager(context);
        
        setTitle(context.getString(R.string.configure_gpu_performance));
        setupViews();
        updateGPUInfo();
    }
    
    private void setupViews() {
        sPerformanceMode = findViewById(R.id.SPerformanceMode);
        tvGPUInfo = findViewById(R.id.TVGPUInfo);
        tvCurrentFreq = findViewById(R.id.TVCurrentFreq);
        tvMaxFreq = findViewById(R.id.TVMaxFreq);
        btApply = findViewById(R.id.BTApply);
        btRestore = findViewById(R.id.BTRestore);
        
        // Настройка спиннера режимов производительности
        String[] modeNames = new String[GPUPerformanceManager.PerformanceMode.values().length];
        for (int i = 0; i < GPUPerformanceManager.PerformanceMode.values().length; i++) {
            modeNames[i] = getContext().getString(GPUPerformanceManager.PerformanceMode.values()[i].getDisplayNameResId());
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), 
            android.R.layout.simple_spinner_item, modeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sPerformanceMode.setAdapter(adapter);
        
        // Загружаем сохраненный режим
        SharedPreferences prefs = new com.winlator.cmod.core.MmkvPreferences();
        String savedMode = prefs.getString(PREF_GPU_PERFORMANCE_MODE, "default");
        for (int i = 0; i < GPUPerformanceManager.PerformanceMode.values().length; i++) {
            if (GPUPerformanceManager.PerformanceMode.values()[i].getValue().equals(savedMode)) {
                sPerformanceMode.setSelection(i);
                break;
            }
        }
        
        // Обработчики кнопок
        btApply.setOnClickListener(v -> applyPerformanceMode());
        btRestore.setOnClickListener(v -> restoreOriginalSettings());
        
        // Проверяем доступность функций
        if (!gpuManager.isGPUControlAvailable()) {
            if (gpuManager.isNonRootOptimizationAvailable()) {
                Toast.makeText(getContext(), getContext().getString(R.string.gpu_control_root_unavailable), 
                    Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), getContext().getString(R.string.gpu_control_unavailable), 
                    Toast.LENGTH_LONG).show();
                btApply.setEnabled(false);
                btRestore.setEnabled(false);
            }
        } else if (!gpuManager.isAdrenoGPU()) {
            Toast.makeText(getContext(), getContext().getString(R.string.gpu_non_adreno_warning), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private void updateGPUInfo() {
        GPUPerformanceManager.GPUStatus status = gpuManager.getGPUStatus();
        
        String gpuRenderer = com.winlator.cmod.core.GPUInformation.getRenderer();
        tvGPUInfo.setText(String.format(getContext().getString(R.string.gpu_info_format), 
            gpuRenderer, status.governor != null ? status.governor : getContext().getString(R.string.gpu_governor_unknown)));
        
        if (status.currentFreq > 0) {
            tvCurrentFreq.setText(String.format(getContext().getString(R.string.gpu_current_freq_format), 
                status.getFrequencyMHz(), status.getUsagePercent()));
        } else {
            tvCurrentFreq.setText(getContext().getString(R.string.gpu_current_freq_unavailable));
        }
        
        if (status.maxFreq > 0) {
            tvMaxFreq.setText(String.format(getContext().getString(R.string.gpu_max_freq_format), 
                status.getMaxFrequencyMHz()));
        } else {
            tvMaxFreq.setText(getContext().getString(R.string.gpu_max_freq_unavailable));
        }
    }
    
    private void applyPerformanceMode() {
        int selectedIndex = sPerformanceMode.getSelectedItemPosition();
        GPUPerformanceManager.PerformanceMode mode = 
            GPUPerformanceManager.PerformanceMode.values()[selectedIndex];
        
        boolean success = gpuManager.applyPerformanceMode(mode);
        
        if (success) {
            // Сохраняем выбранный режим
            SharedPreferences prefs = new com.winlator.cmod.core.MmkvPreferences();
            prefs.edit().putString(PREF_GPU_PERFORMANCE_MODE, mode.getValue()).apply();
            
            Toast.makeText(getContext(), 
                String.format(getContext().getString(R.string.gpu_performance_applied), getContext().getString(mode.getDisplayNameResId())), 
                Toast.LENGTH_SHORT).show();
            
            // Обновляем информацию
            updateGPUInfo();
        } else {
            Toast.makeText(getContext(), 
                getContext().getString(R.string.gpu_performance_apply_failed), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private void restoreOriginalSettings() {
        boolean success = gpuManager.restoreOriginalSettings();
        
        if (success) {
            // Сбрасываем сохраненный режим
            SharedPreferences prefs = new com.winlator.cmod.core.MmkvPreferences();
            prefs.edit().putString(PREF_GPU_PERFORMANCE_MODE, "default").apply();
            
            // Устанавливаем спиннер на "default"
            sPerformanceMode.setSelection(0);
            
            Toast.makeText(getContext(), 
                getContext().getString(R.string.gpu_settings_restored), 
                Toast.LENGTH_SHORT).show();
            
            // Обновляем информацию
            updateGPUInfo();
        } else {
            Toast.makeText(getContext(), 
                getContext().getString(R.string.gpu_settings_restore_failed), 
                Toast.LENGTH_LONG).show();
        }
    }
}