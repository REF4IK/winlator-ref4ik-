package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Spinner;

import com.winlator.cmod.R;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.widget.SeekBar;

public class AudioDriverConfigDialog extends ContentDialog {
    private static final String TAG = "AudioDriverConfigDialog";
    // Предустановленные значения задержки аудио (в миллисекундах)
    private static final int[] LATENCY_VALUES = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120};
    
    public AudioDriverConfigDialog(final View anchor) {
        super(anchor.getContext(), R.layout.audio_driver_config_dialog);
        Context context = anchor.getContext();
        setIcon(R.drawable.icon_audio_settings);
        setTitle(context.getString(R.string.audio_driver) + " " + context.getString(R.string.configuration));
        
        final Spinner sPerformanceMode = findViewById(R.id.SPerformanceMode);
        final SeekBar sbVolume = findViewById(R.id.SBVolume);
        final Spinner sLatencyMillis = findViewById(R.id.SLatencyMillis);
        
        // Получаем конфигурацию и мигрируем старый формат с ';' на новый формат с ','
        String configString = anchor.getTag() != null ? anchor.getTag().toString() : "";
        Log.d(TAG, "Загружена конфигурация (до миграции): " + configString);
        configString = configString.replace(';', ',');
        Log.d(TAG, "Загружена конфигурация (после миграции): " + configString);
        KeyValueSet config = new KeyValueSet(configString);
        sPerformanceMode.setSelection(config.getInt("performanceMode", 1));
        sbVolume.setValue(config.getFloat("volume", 1.0f) * 100.0f);
        
        // Находим ближайшее значение задержки в списке и устанавливаем его
        int savedLatency = config.getInt("latencyMillis", 20);
        Log.d(TAG, "Загруженное значение latencyMillis: " + savedLatency);
        int latencyIndex = findClosestLatencyIndex(savedLatency);
        Log.d(TAG, "Установлен индекс spinner: " + latencyIndex + " (значение: " + LATENCY_VALUES[latencyIndex] + " мс)");
        sLatencyMillis.setSelection(latencyIndex);
        
        setOnConfirmCallback(() -> {
            KeyValueSet newConfig = new KeyValueSet();
            newConfig.put("performanceMode", sPerformanceMode.getSelectedItemPosition());
            newConfig.put("volume", sbVolume.getValue() / 100.0f);
            
            // Получаем выбранное значение задержки из массива
            int selectedIndex = sLatencyMillis.getSelectedItemPosition();
            int latency = LATENCY_VALUES[selectedIndex];
            newConfig.put("latencyMillis", latency);
            
            String savedConfig = newConfig.toString();
            Log.d(TAG, "Сохраняется конфигурация: " + savedConfig);
            anchor.setTag(savedConfig);
        });
    }
    
    /**
     * Находит индекс ближайшего значения задержки в предустановленном списке
     */
    private int findClosestLatencyIndex(int targetLatency) {
        int closestIndex = 1; // По умолчанию 20 мс (индекс 1)
        int minDifference = Math.abs(LATENCY_VALUES[closestIndex] - targetLatency);
        
        for (int i = 0; i < LATENCY_VALUES.length; i++) {
            int difference = Math.abs(LATENCY_VALUES[i] - targetLatency);
            if (difference < minDifference) {
                minDifference = difference;
                closestIndex = i;
            }
        }
        
        return closestIndex;
    }
}
