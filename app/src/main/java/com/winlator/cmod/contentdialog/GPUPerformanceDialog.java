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
            modeNames[i] = GPUPerformanceManager.PerformanceMode.values()[i].getDisplayName();
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), 
            android.R.layout.simple_spinner_item, modeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sPerformanceMode.setAdapter(adapter);
        
        // Загружаем сохраненный режим
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
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
                Toast.makeText(getContext(), "Управление GPU через root недоступно. Используются бескорневые оптимизации.", 
                    Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Управление GPU недоступно. Требуются root права или новая версия Android.", 
                    Toast.LENGTH_LONG).show();
                btApply.setEnabled(false);
                btRestore.setEnabled(false);
            }
        } else if (!gpuManager.isAdrenoGPU()) {
            Toast.makeText(getContext(), "Обнаружен не-Adreno GPU. Функция может не работать корректно.", 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private void updateGPUInfo() {
        GPUPerformanceManager.GPUStatus status = gpuManager.getGPUStatus();
        
        String gpuRenderer = com.winlator.cmod.core.GPUInformation.getRenderer();
        tvGPUInfo.setText(String.format("GPU: %s\nРегулятор: %s", 
            gpuRenderer, status.governor != null ? status.governor : "Неизвестно"));
        
        if (status.currentFreq > 0) {
            tvCurrentFreq.setText(String.format("Текущая частота: %s (%d%%)", 
                status.getFrequencyMHz(), status.getUsagePercent()));
        } else {
            tvCurrentFreq.setText("Текущая частота: Недоступно");
        }
        
        if (status.maxFreq > 0) {
            tvMaxFreq.setText(String.format("Максимальная частота: %s", 
                status.getMaxFrequencyMHz()));
        } else {
            tvMaxFreq.setText("Максимальная частота: Недоступно");
        }
    }
    
    private void applyPerformanceMode() {
        int selectedIndex = sPerformanceMode.getSelectedItemPosition();
        GPUPerformanceManager.PerformanceMode mode = 
            GPUPerformanceManager.PerformanceMode.values()[selectedIndex];
        
        boolean success = gpuManager.applyPerformanceMode(mode);
        
        if (success) {
            // Сохраняем выбранный режим
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putString(PREF_GPU_PERFORMANCE_MODE, mode.getValue()).apply();
            
            Toast.makeText(getContext(), 
                "Режим производительности применен: " + mode.getDisplayName(), 
                Toast.LENGTH_SHORT).show();
            
            // Обновляем информацию
            updateGPUInfo();
        } else {
            Toast.makeText(getContext(), 
                "Не удалось применить режим производительности. Проверьте права доступа.", 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private void restoreOriginalSettings() {
        boolean success = gpuManager.restoreOriginalSettings();
        
        if (success) {
            // Сбрасываем сохраненный режим
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putString(PREF_GPU_PERFORMANCE_MODE, "default").apply();
            
            // Устанавливаем спиннер на "default"
            sPerformanceMode.setSelection(0);
            
            Toast.makeText(getContext(), 
                "Оригинальные настройки GPU восстановлены", 
                Toast.LENGTH_SHORT).show();
            
            // Обновляем информацию
            updateGPUInfo();
        } else {
            Toast.makeText(getContext(), 
                "Не удалось восстановить настройки GPU", 
                Toast.LENGTH_LONG).show();
        }
    }
}