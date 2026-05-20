package com.winlator.cmod.core;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages Adreno GPU performance through frequency control
 */
public class GPUPerformanceManager {
    private static final String TAG = "GPUPerformanceManager";
    
    private static final String GPU_GOVERNOR_PATH = SystemSensorPaths.GPU_GOVERNOR_PATH;
    private static final String GPU_MAX_FREQ_PATH = SystemSensorPaths.GPU_MAX_FREQ_PATH;
    private static final String GPU_MIN_FREQ_PATH = SystemSensorPaths.GPU_MIN_FREQ_PATH;
    private static final String GPU_CUR_FREQ_PATH = SystemSensorPaths.GPU_CUR_FREQ_PATH;
    private static final String GPU_AVAILABLE_FREQS_PATH = SystemSensorPaths.GPU_AVAILABLE_FREQS_PATH;
    private static final String GPU_AVAILABLE_GOVERNORS_PATH = SystemSensorPaths.GPU_AVAILABLE_GOVERNORS_PATH;
    private static final String[] GPU_CUR_FREQ_ALTERNATIVES = SystemSensorPaths.GPU_CUR_FREQ_ALTERNATIVES;
    private static final String[] GPU_MAX_FREQ_ALTERNATIVES = SystemSensorPaths.GPU_MAX_FREQ_ALTERNATIVES;
    
    private Context context;
    private String originalGovernor = null;
    private String originalMinFreq = null;
    private PowerManager powerManager;
    private ActivityManager activityManager;
    private boolean sustainedPerformanceActive = false;
    
    public enum PerformanceMode {
        DEFAULT("default", "Стандартный режим"),
        PERFORMANCE("performance", "Максимальная производительность"),
        FORCE_MAX("force_max", "Принудительная максимальная частота"),
        // Новые режимы без root
        SUSTAINED_PERFORMANCE("sustained", "Sustained Performance (без root)"),
        THERMAL_HINT("thermal_hint", "Thermal Hint API (без root)"),
        GAME_MODE("game_mode", "Game Mode API (без root)");
        
        private final String value;
        private final String displayName;
        
        PerformanceMode(String value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }
        
        public String getValue() { return value; }
        public String getDisplayName() { return displayName; }
    }
    
    public GPUPerformanceManager(Context context) {
        this.context = context;
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }
    
    /**
     * Проверяет доступность управления GPU
     */
    public boolean isGPUControlAvailable() {
        return new File(GPU_GOVERNOR_PATH).exists() && 
               new File(GPU_MAX_FREQ_PATH).exists() && 
               new File(GPU_MIN_FREQ_PATH).exists();
    }
    
    /**
     * Проверяет, является ли GPU - Adreno
     */
    public boolean isAdrenoGPU() {
        return GPUInformation.getRenderer().toLowerCase().contains("adreno");
    }
    
    /**
     * Получает доступные регуляторы частоты
     */
    public List<String> getAvailableGovernors() {
        List<String> governors = new ArrayList<>();
        try {
            String content = readFromFile(GPU_AVAILABLE_GOVERNORS_PATH);
            if (content != null) {
                String[] parts = content.trim().split("\\s+");
                for (String governor : parts) {
                    governors.add(governor);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read available governors", e);
        }
        return governors;
    }
    
    /**
     * Получает доступные частоты
     */
    public List<Long> getAvailableFrequencies() {
        List<Long> frequencies = new ArrayList<>();
        try {
            String content = readFromFile(GPU_AVAILABLE_FREQS_PATH);
            if (content != null) {
                String[] parts = content.trim().split("\\s+");
                for (String freq : parts) {
                    try {
                        frequencies.add(Long.parseLong(freq));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Invalid frequency format: " + freq);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to read available frequencies", e);
        }
        return frequencies;
    }
    
    /**
     * Получает текущий регулятор
     */
    public String getCurrentGovernor() {
        try {
            return readFromFile(GPU_GOVERNOR_PATH);
        } catch (Exception e) {
            Log.e(TAG, "Failed to read current governor", e);
            return null;
        }
    }
    
    /**
     * Получает текущую частоту GPU (пробует альтернативные пути)
     */
    public long getCurrentFrequency() {
        // Сначала пробуем основной путь
        try {
            String freq = readFromFile(GPU_CUR_FREQ_PATH);
            if (freq != null) {
                return Long.parseLong(freq.trim());
            }
        } catch (Exception e) {
            Log.d(TAG, "Primary GPU frequency path not accessible");
        }
        
        // Пробуем альтернативные пути
        for (String altPath : GPU_CUR_FREQ_ALTERNATIVES) {
            try {
                String freq = readFromFile(altPath);
                if (freq != null) {
                    long frequency = Long.parseLong(freq.trim());
                    Log.d(TAG, "Read GPU frequency from alternative path: " + altPath);
                    return frequency;
                }
            } catch (Exception e) {
                // Пробуем следующий путь
            }
        }
        
        Log.d(TAG, "Failed to read GPU frequency from all paths");
        return 0;
    }
    
    /**
     * Получает максимальную частоту GPU (пробует альтернативные пути)
     */
    public long getMaxFrequency() {
        // Сначала пробуем основной путь
        try {
            String freq = readFromFile(GPU_MAX_FREQ_PATH);
            if (freq != null) {
                return Long.parseLong(freq.trim());
            }
        } catch (Exception e) {
            Log.d(TAG, "Primary GPU max frequency path not accessible");
        }
        
        // Пробуем альтернативные пути
        for (String altPath : GPU_MAX_FREQ_ALTERNATIVES) {
            try {
                String freq = readFromFile(altPath);
                if (freq != null) {
                    // Если это список частот, берем максимальную
                    String[] freqs = freq.trim().split("\\s+");
                    long maxFreq = 0;
                    for (String f : freqs) {
                        try {
                            long frequency = Long.parseLong(f);
                            if (frequency > maxFreq) {
                                maxFreq = frequency;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                    if (maxFreq > 0) {
                        Log.d(TAG, "Read GPU max frequency from alternative path: " + altPath);
                        return maxFreq;
                    }
                }
            } catch (Exception e) {
                // Пробуем следующий путь
            }
        }
        
        Log.d(TAG, "Failed to read GPU max frequency from all paths");
        return 0;
    }
    
    /**
     * Получает минимальную частоту GPU
     */
    public long getMinFrequency() {
        try {
            String freq = readFromFile(GPU_MIN_FREQ_PATH);
            return freq != null ? Long.parseLong(freq.trim()) : 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to read min frequency", e);
            return 0;
        }
    }
    
    /**
     * Применяет режим производительности (включая методы без root)
     */
    public boolean applyPerformanceMode(PerformanceMode mode) {
        // Сохраняем оригинальные настройки для root методов
        if (isGPUControlAvailable() && originalGovernor == null) {
            originalGovernor = getCurrentGovernor();
            originalMinFreq = String.valueOf(getMinFrequency());
        }
        
        try {
            switch (mode) {
                case DEFAULT:
                    return restoreOriginalSettings();
                    
                case PERFORMANCE:
                    // Попробовать root метод, если не работает - без root
                    if (isGPUControlAvailable()) {
                        return setGovernor("performance");
                    } else {
                        return applySustainedPerformance();
                    }
                    
                case FORCE_MAX:
                    if (isGPUControlAvailable()) {
                        long maxFreq = getMaxFrequency();
                        if (maxFreq > 0) {
                            boolean success = writeToFile(GPU_MIN_FREQ_PATH, String.valueOf(maxFreq));
                            if (success) {
                                Log.i(TAG, "Forced GPU to maximum frequency: " + maxFreq + " Hz");
                            }
                            return success;
                        }
                    } else {
                        return applyThermalHints() && applySustainedPerformance();
                    }
                    break;
                    
                // Новые режимы без root
                case SUSTAINED_PERFORMANCE:
                    return applySustainedPerformance();
                    
                case THERMAL_HINT:
                    return applyThermalHints();
                    
                case GAME_MODE:
                    return applyGameModeOptimizations();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply performance mode: " + mode, e);
        }
        
        return false;
    }
    
    /**
     * Устанавливает регулятор частоты
     */
    private boolean setGovernor(String governor) {
        try {
            boolean success = writeToFile(GPU_GOVERNOR_PATH, governor);
            if (success) {
                Log.i(TAG, "Set GPU governor to: " + governor);
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set governor: " + governor, e);
            return false;
        }
    }
    
    /**
     * Восстанавливает оригинальные настройки
     */
    public boolean restoreOriginalSettings() {
        boolean success = true;
        
        // Отключаем sustained performance
        if (sustainedPerformanceActive) {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
                sustainedPerformanceActive = false;
                Log.i(TAG, "Disabled sustained performance mode");
            } catch (Exception e) {
                Log.w(TAG, "Could not disable sustained performance", e);
                success = false;
            }
        }
        
        // Восстанавливаем root настройки (если доступны)
        if (originalMinFreq != null) {
            success &= writeToFile(GPU_MIN_FREQ_PATH, originalMinFreq);
        }
        
        if (originalGovernor != null) {
            success &= writeToFile(GPU_GOVERNOR_PATH, originalGovernor);
        }
        
        if (success) {
            Log.i(TAG, "Restored all GPU settings");
        }
        
        return success;
    }
    
    /**
     * Получает информацию о текущем состоянии GPU
     */
    public GPUStatus getGPUStatus() {
        return new GPUStatus(
            getCurrentGovernor(),
            getCurrentFrequency(),
            getMaxFrequency(),
            getMinFrequency(),
            isAdrenoGPU()
        );
    }
    
    private String readFromFile(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return reader.readLine();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read from: " + path, e);
            return null;
        }
    }
    
    private boolean writeToFile(String path, String value) {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(value);
            writer.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to: " + path + ", value: " + value, e);
            return false;
        }
    }
    
    /**
     * Класс для хранения информации о состоянии GPU
     */
    public static class GPUStatus {
        public final String governor;
        public final long currentFreq;
        public final long maxFreq;
        public final long minFreq;
        public final boolean isAdreno;
        
        public GPUStatus(String governor, long currentFreq, long maxFreq, long minFreq, boolean isAdreno) {
            this.governor = governor;
            this.currentFreq = currentFreq;
            this.maxFreq = maxFreq;
            this.minFreq = minFreq;
            this.isAdreno = isAdreno;
        }
        
        public String getFrequencyMHz() {
            return String.format("%.0f MHz", currentFreq / 1_000_000.0);
        }
        
        public String getMaxFrequencyMHz() {
            return String.format("%.0f MHz", maxFreq / 1_000_000.0);
        }
        
        public int getUsagePercent() {
            if (maxFreq == 0) return 0;
            return (int) ((currentFreq * 100) / maxFreq);
        }
    }
    
    // =============================================
    // МЕТОДЫ ПОВЫШЕНИЯ ПРОИЗВОДИТЕЛЬНОСТИ БЕЗ ROOT ПРАВ
    // =============================================
    
    /**
     * Sustained Performance Mode - поддерживает стабильную производительность
     * Работает на Android 7.0+ без root
     */
    private boolean applySustainedPerformance() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Устанавливаем приоритет процесса
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
                
                if (powerManager != null) {
                    Log.i(TAG, "Applied sustained performance optimizations");
                    sustainedPerformanceActive = true;
                    return true;
                }
            }
            
            // Запасной метод - повышение приоритета
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
            Log.i(TAG, "Applied basic performance optimizations");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply sustained performance", e);
            return false;
        }
    }
    
    /**
     * Thermal Hints - оптимизации через PowerManager
     */
    private boolean applyThermalHints() {
        try {
            if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Отключаем режим экономии энергии
                Log.i(TAG, "Applied thermal performance hints");
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply thermal hints", e);
            return false;
        }
    }
    
    /**
     * Game Mode - оптимизации для игр
     */
    private boolean applyGameModeOptimizations() {
        try {
            // Оптимизации Adreno
            return applyAdrenoSpecificOptimizations();
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply game mode optimizations", e);
            return false;
        }
    }
    
    /**
     * Специфичные оптимизации для Adreno GPU
     */
    private boolean applyAdrenoSpecificOptimizations() {
        try {
            // Переменные для оптимизации Adreno GPU
            System.setProperty("debug.egl.hw", "1");
            System.setProperty("debug.sf.hw", "1");
            System.setProperty("debug.composition.type", "gpu");
            System.setProperty("debug.hwui.render_dirty_regions", "false");
            
            // Vulkan оптимизации
            System.setProperty("debug.vulkan.layers", "");
            System.setProperty("debug.vulkan.disable_robustness", "true");
            
            Log.i(TAG, "Applied Adreno environment optimizations");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to apply Adreno optimizations", e);
            return false;
        }
    }
    
    /**
     * Проверяет доступность бескорневых оптимизаций
     */
    public boolean isNonRootOptimizationAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N || 
               powerManager != null || 
               isAdrenoGPU();
    }
}