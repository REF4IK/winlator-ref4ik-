package com.winlator.cmod.box86_64;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.EnvVars;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

public abstract class Box86_64PresetManager {
    private static final String BOX_PRESET_FILE_EXTENSION = ".boxx";

    public static EnvVars getEnvVars(String prefix, Context context, String id) {
        String ucPrefix = prefix.toUpperCase(Locale.ENGLISH);
        EnvVars envVars = new EnvVars();

        if (id.equals(Box86_64Preset.STABILITY)) {
            envVars.put(ucPrefix+"_DYNAREC_SAFEFLAGS", "2");
            envVars.put(ucPrefix+"_DYNAREC_FASTNAN", "0");
            envVars.put(ucPrefix+"_DYNAREC_FASTROUND", "0");
            envVars.put(ucPrefix+"_DYNAREC_X87DOUBLE", "1");
            envVars.put(ucPrefix+"_DYNAREC_BIGBLOCK", "0");
            envVars.put(ucPrefix+"_DYNAREC_STRONGMEM", "2");
            envVars.put(ucPrefix+"_DYNAREC_FORWARD", "128");
            envVars.put(ucPrefix+"_DYNAREC_CALLRET", "0");
            envVars.put(ucPrefix+"_DYNAREC_WAIT", "0");
            envVars.put(ucPrefix+"_DYNAREC_DIV0", "1");
            envVars.put(ucPrefix+"_DYNAREC_IGNOREINT3", "0");
            envVars.put(ucPrefix+"_DYNAREC_MULTIBLOCK", "0");
            envVars.put(ucPrefix+"_DYNAREC_SMCCHECKS", "full");
            envVars.put(ucPrefix+"_DYNAREC_SMALLTSCSCALE", "0");
            envVars.put(ucPrefix+"_DYNAREC_TSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_VECTORTSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_MEMCPYSETTSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_HALFBARRIERTSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_X87REDUCEDPRECISION", "0");
            envVars.put(ucPrefix+"_DYNAREC_VOLATILEMETADATA", "0");
            envVars.put(ucPrefix+"_DYNAREC_MONOHACKS", "1");
            if (ucPrefix.equals("BOX64")) {
                envVars.put("BOX64_AVX", "0");
                envVars.put("BOX64_UNITYPLAYER", "1");
                envVars.put("BOX64_MMAP32", "0");
                envVars.put("BOX64_CPUTYPE", "0");
                envVars.put("BOX64_HIDEHYPERVISORBIT", "0");
                envVars.put("BOX64_RDTSC1GHZ", "0");
                envVars.put("BOX64_DYNAREC_NOARCH", "0");
                envVars.put("BOX64_DYNAREC_SEP", "1");
                envVars.put("BOX64_AES", "1");
                envVars.put("BOX64_PCLMULQDQ", "1");
                envVars.put("BOX64_SHAEXT", "1");
                envVars.put("BOX64_SSE42", "1");
                envVars.put("BOX64_SSE_FLUSHTO0", "0");
                envVars.put("BOX64_X87_NO80BITS", "0");
                envVars.put("BOX64_UNITY", "0");
            }
        }
        else if (id.equals(Box86_64Preset.COMPATIBILITY)) {
            envVars.put(ucPrefix+"_DYNAREC_SAFEFLAGS", "2");
            envVars.put(ucPrefix+"_DYNAREC_FASTNAN", "0");
            envVars.put(ucPrefix+"_DYNAREC_FASTROUND", "0");
            envVars.put(ucPrefix+"_DYNAREC_X87DOUBLE", "1");
            envVars.put(ucPrefix+"_DYNAREC_BIGBLOCK", "0");
            envVars.put(ucPrefix+"_DYNAREC_STRONGMEM", "1");
            envVars.put(ucPrefix+"_DYNAREC_FORWARD", "128");
            envVars.put(ucPrefix+"_DYNAREC_CALLRET", "0");
            envVars.put(ucPrefix+"_DYNAREC_WAIT", "1");
            envVars.put(ucPrefix+"_DYNAREC_DIV0", "1");
            envVars.put(ucPrefix+"_DYNAREC_IGNOREINT3", "0");
            envVars.put(ucPrefix+"_DYNAREC_MULTIBLOCK", "1");
            envVars.put(ucPrefix+"_DYNAREC_SMCCHECKS", "mtrack");
            envVars.put(ucPrefix+"_DYNAREC_SMALLTSCSCALE", "0");
            envVars.put(ucPrefix+"_DYNAREC_TSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_VECTORTSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_MEMCPYSETTSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_HALFBARRIERTSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_X87REDUCEDPRECISION", "0");
            envVars.put(ucPrefix+"_DYNAREC_VOLATILEMETADATA", "0");
            envVars.put(ucPrefix+"_DYNAREC_MONOHACKS", "1");
            if (ucPrefix.equals("BOX64")) {
                envVars.put("BOX64_AVX", "0");
                envVars.put("BOX64_UNITYPLAYER", "1");
                envVars.put("BOX64_MMAP32", "0");
                envVars.put("BOX64_CPUTYPE", "0");
                envVars.put("BOX64_HIDEHYPERVISORBIT", "0");
                envVars.put("BOX64_RDTSC1GHZ", "0");
                envVars.put("BOX64_DYNAREC_NOARCH", "0");
                envVars.put("BOX64_DYNAREC_SEP", "1");
                envVars.put("BOX64_AES", "1");
                envVars.put("BOX64_PCLMULQDQ", "1");
                envVars.put("BOX64_SHAEXT", "1");
                envVars.put("BOX64_SSE42", "1");
                envVars.put("BOX64_SSE_FLUSHTO0", "0");
                envVars.put("BOX64_X87_NO80BITS", "0");
                envVars.put("BOX64_UNITY", "0");
            }
        }
        else if (id.equals(Box86_64Preset.INTERMEDIATE)) {
            envVars.put(ucPrefix+"_DYNAREC_SAFEFLAGS", "2");
            envVars.put(ucPrefix+"_DYNAREC_FASTNAN", "1");
            envVars.put(ucPrefix+"_DYNAREC_FASTROUND", "0");
            envVars.put(ucPrefix+"_DYNAREC_X87DOUBLE", "1");
            envVars.put(ucPrefix+"_DYNAREC_BIGBLOCK", "1");
            envVars.put(ucPrefix+"_DYNAREC_STRONGMEM", "0");
            envVars.put(ucPrefix+"_DYNAREC_FORWARD", "128");
            envVars.put(ucPrefix+"_DYNAREC_CALLRET", "1");
            envVars.put(ucPrefix+"_DYNAREC_WAIT", "1");
            envVars.put(ucPrefix+"_DYNAREC_DIV0", "0");
            envVars.put(ucPrefix+"_DYNAREC_IGNOREINT3", "0");
            envVars.put(ucPrefix+"_DYNAREC_MULTIBLOCK", "1");
            envVars.put(ucPrefix+"_DYNAREC_SMCCHECKS", "mtrack");
            envVars.put(ucPrefix+"_DYNAREC_SMALLTSCSCALE", "0");
            envVars.put(ucPrefix+"_DYNAREC_TSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_VECTORTSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_MEMCPYSETTSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_HALFBARRIERTSOENABLED", "1");
            envVars.put(ucPrefix+"_DYNAREC_X87REDUCEDPRECISION", "1");
            envVars.put(ucPrefix+"_DYNAREC_VOLATILEMETADATA", "0");
            envVars.put(ucPrefix+"_DYNAREC_MONOHACKS", "0");
            if (ucPrefix.equals("BOX64")) {
                envVars.put("BOX64_AVX", "0");
                envVars.put("BOX64_UNITYPLAYER", "0");
                envVars.put("BOX64_MMAP32", "1");
                envVars.put("BOX64_CPUTYPE", "0");
                envVars.put("BOX64_HIDEHYPERVISORBIT", "0");
                envVars.put("BOX64_RDTSC1GHZ", "0");
                envVars.put("BOX64_DYNAREC_NOARCH", "0");
                envVars.put("BOX64_DYNAREC_SEP", "1");
                envVars.put("BOX64_AES", "1");
                envVars.put("BOX64_PCLMULQDQ", "1");
                envVars.put("BOX64_SHAEXT", "1");
                envVars.put("BOX64_SSE42", "1");
                envVars.put("BOX64_SSE_FLUSHTO0", "0");
                envVars.put("BOX64_X87_NO80BITS", "0");
                envVars.put("BOX64_UNITY", "0");
            }
        }
        else if (id.equals(Box86_64Preset.PERFORMANCE)) {
            envVars.put(ucPrefix+"_DYNAREC_SAFEFLAGS", "1");
            envVars.put(ucPrefix+"_DYNAREC_FASTNAN", "1");
            envVars.put(ucPrefix+"_DYNAREC_FASTROUND", "1");
            envVars.put(ucPrefix+"_DYNAREC_X87DOUBLE", "0");
            envVars.put(ucPrefix+"_DYNAREC_BIGBLOCK", "3");
            envVars.put(ucPrefix+"_DYNAREC_STRONGMEM", "0");
            envVars.put(ucPrefix+"_DYNAREC_FORWARD", "512");
            envVars.put(ucPrefix+"_DYNAREC_CALLRET", "1");
            envVars.put(ucPrefix+"_DYNAREC_WAIT", "1");
            envVars.put(ucPrefix+"_DYNAREC_DIV0", "0");
            envVars.put(ucPrefix+"_DYNAREC_IGNOREINT3", "0");
            envVars.put(ucPrefix+"_DYNAREC_MULTIBLOCK", "1");
            envVars.put(ucPrefix+"_DYNAREC_SMCCHECKS", "mtrack");
            envVars.put(ucPrefix+"_DYNAREC_SMALLTSCSCALE", "1");
            envVars.put(ucPrefix+"_DYNAREC_TSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_VECTORTSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_MEMCPYSETTSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_HALFBARRIERTSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_X87REDUCEDPRECISION", "1");
            envVars.put(ucPrefix+"_DYNAREC_VOLATILEMETADATA", "0");
            envVars.put(ucPrefix+"_DYNAREC_MONOHACKS", "0");
            if (ucPrefix.equals("BOX64")) {
                envVars.put("BOX64_AVX", "1");
                envVars.put("BOX64_UNITYPLAYER", "0");
                envVars.put("BOX64_MMAP32", "1");
                envVars.put("BOX64_CPUTYPE", "0");
                envVars.put("BOX64_HIDEHYPERVISORBIT", "0");
                envVars.put("BOX64_RDTSC1GHZ", "1");
                envVars.put("BOX64_DYNAREC_NOARCH", "0");
                envVars.put("BOX64_DYNAREC_SEP", "1");
                envVars.put("BOX64_AES", "1");
                envVars.put("BOX64_PCLMULQDQ", "1");
                envVars.put("BOX64_SHAEXT", "1");
                envVars.put("BOX64_SSE42", "1");
                envVars.put("BOX64_SSE_FLUSHTO0", "0");
                envVars.put("BOX64_X87_NO80BITS", "0");
                envVars.put("BOX64_UNITY", "0");
            }
        }
        else if (id.equals(Box86_64Preset.EXTREME)) {
            envVars.put(ucPrefix+"_DYNAREC_SAFEFLAGS", "0");
            envVars.put(ucPrefix+"_DYNAREC_FASTNAN", "1");
            envVars.put(ucPrefix+"_DYNAREC_FASTROUND", "1");
            envVars.put(ucPrefix+"_DYNAREC_X87DOUBLE", "0");
            envVars.put(ucPrefix+"_DYNAREC_BIGBLOCK", "3");
            envVars.put(ucPrefix+"_DYNAREC_STRONGMEM", "0");
            envVars.put(ucPrefix+"_DYNAREC_FORWARD", "1024");
            envVars.put(ucPrefix+"_DYNAREC_CALLRET", "1");
            envVars.put(ucPrefix+"_DYNAREC_WAIT", "0");
            envVars.put(ucPrefix+"_DYNAREC_DIV0", "0");
            envVars.put(ucPrefix+"_DYNAREC_IGNOREINT3", "1");
            envVars.put(ucPrefix+"_DYNAREC_MULTIBLOCK", "1");
            envVars.put(ucPrefix+"_DYNAREC_SMCCHECKS", "none");
            envVars.put(ucPrefix+"_DYNAREC_SMALLTSCSCALE", "1");
            envVars.put(ucPrefix+"_DYNAREC_TSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_VECTORTSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_MEMCPYSETTSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_HALFBARRIERTSOENABLED", "0");
            envVars.put(ucPrefix+"_DYNAREC_X87REDUCEDPRECISION", "1");
            envVars.put(ucPrefix+"_DYNAREC_VOLATILEMETADATA", "1");
            envVars.put(ucPrefix+"_DYNAREC_MONOHACKS", "0");
            if (ucPrefix.equals("BOX64")) {
                envVars.put("BOX64_AVX", "2");
                envVars.put("BOX64_UNITYPLAYER", "0");
                envVars.put("BOX64_MMAP32", "1");
                envVars.put("BOX64_CPUTYPE", "0");
                envVars.put("BOX64_HIDEHYPERVISORBIT", "1");
                envVars.put("BOX64_RDTSC1GHZ", "1");
                envVars.put("BOX64_DYNAREC_NOARCH", "2");
                envVars.put("BOX64_DYNAREC_SEP", "1");
                envVars.put("BOX64_AES", "1");
                envVars.put("BOX64_PCLMULQDQ", "1");
                envVars.put("BOX64_SHAEXT", "1");
                envVars.put("BOX64_SSE42", "1");
                envVars.put("BOX64_SSE_FLUSHTO0", "0");
                envVars.put("BOX64_X87_NO80BITS", "0");
                envVars.put("BOX64_UNITY", "0");
            }
        }
        else if (id.startsWith(Box86_64Preset.CUSTOM)) {
            for (String[] preset : customPresetsIterator(prefix, context)) {
                if (preset[0].equals(id)) {
                    envVars.putAll(preset[2]);
                    break;
                }
            }
        }

        File dynarecCacheDir = new File(context.getCacheDir(), prefix.toLowerCase(Locale.ENGLISH) + "_cache");
        if (!dynarecCacheDir.exists()) {
            dynarecCacheDir.mkdirs();
        }
        envVars.put(ucPrefix + "_DYNAREC_SAVE", "1");
        envVars.put(ucPrefix + "_DYNAREC_CACHE", "1");
        envVars.put(ucPrefix + "_DYNAREC_CACHE_DIR", dynarecCacheDir.getAbsolutePath());

        return envVars;
    }

    public static ArrayList<Box86_64Preset> getPresets(String prefix, Context context) {
        ArrayList<Box86_64Preset> presets = new ArrayList<>();
        presets.add(new Box86_64Preset(Box86_64Preset.STABILITY, context.getString(R.string.stability)));
        presets.add(new Box86_64Preset(Box86_64Preset.COMPATIBILITY, context.getString(R.string.compatibility)));
        presets.add(new Box86_64Preset(Box86_64Preset.INTERMEDIATE, context.getString(R.string.intermediate)));
        presets.add(new Box86_64Preset(Box86_64Preset.PERFORMANCE, context.getString(R.string.performance)));
        presets.add(new Box86_64Preset(Box86_64Preset.EXTREME, context.getString(R.string.extreme)));
        for (String[] preset : customPresetsIterator(prefix, context)) presets.add(new Box86_64Preset(preset[0], preset[1]));
        return presets;
    }

    public static Box86_64Preset getPreset(String prefix, Context context, String id) {
        for (Box86_64Preset preset : getPresets(prefix, context)) if (preset.id.equals(id)) return preset;
        return null;
    }

    private static Iterable<String[]> customPresetsIterator(String prefix, Context context) {
        SharedPreferences preferences = new com.winlator.cmod.core.MmkvPreferences();
        final String customPresetsStr = preferences.getString(prefix+"_custom_presets", "");
        final String[] customPresets = customPresetsStr.split(",");
        final int[] index = {0};
        return () -> new Iterator<String[]>() {
            @Override
            public boolean hasNext() {
                return index[0] < customPresets.length && !customPresetsStr.isEmpty();
            }

            @Override
            public String[] next() {
                return customPresets[index[0]++].split("\\|");
            }
        };
    }

    public static int getNextPresetId(Context context, String prefix) {
        int maxId = 0;
        for (String[] preset : customPresetsIterator(prefix, context)) {
            maxId = Math.max(maxId, Integer.parseInt(preset[0].replace(Box86_64Preset.CUSTOM+"-", "")));
        }
        return maxId+1;
    }

    public static void editPreset(String prefix, Context context, String id, String name, EnvVars envVars) {
        String key = prefix+"_custom_presets";
        SharedPreferences preferences = new com.winlator.cmod.core.MmkvPreferences();
        String customPresetsStr = preferences.getString(key, "");

        if (id != null) {
            String[] customPresets = customPresetsStr.split(",");
            for (int i = 0; i < customPresets.length; i++) {
                String[] preset = customPresets[i].split("\\|");
                if (preset[0].equals(id)) {
                    customPresets[i] = id+"|"+name+"|"+envVars.toString();
                    break;
                }
            }
            customPresetsStr = String.join(",", customPresets);
        }
        else {
            String preset = Box86_64Preset.CUSTOM+"-"+getNextPresetId(context, prefix)+"|"+name+"|"+envVars.toString();
            customPresetsStr += (!customPresetsStr.isEmpty() ? "," : "")+preset;
        }
        preferences.edit().putString(key, customPresetsStr).apply();
    }

    public static void duplicatePreset(String prefix, Context context, String id) {
        ArrayList<Box86_64Preset> presets = getPresets(prefix, context);
        Box86_64Preset originPreset = null;
        for (Box86_64Preset preset : presets) {
            if (preset.id.equals(id)) {
                originPreset = preset;
                break;
            }
        }
        if (originPreset == null) return;

        String newName;
        for (int i = 1;;i++) {
            newName = originPreset.name+" ("+i+")";
            boolean found = false;
            for (Box86_64Preset preset : presets) {
                if (preset.name.equals(newName)) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
        }

        editPreset(prefix, context, null, newName, getEnvVars(prefix, context, originPreset.id));
    }

    public static void removePreset(String prefix, Context context, String id) {
        String key = prefix+"_custom_presets";
        SharedPreferences preferences = new com.winlator.cmod.core.MmkvPreferences();
        String oldCustomPresetsStr = preferences.getString(key, "");
        String newCustomPresetsStr = "";

        String[] customPresets = oldCustomPresetsStr.split(",");
        for (int i = 0; i < customPresets.length; i++) {
            String[] preset = customPresets[i].split("\\|");
            if (!preset[0].equals(id)) newCustomPresetsStr += (!newCustomPresetsStr.isEmpty() ? "," : "")+customPresets[i];
        }

        preferences.edit().putString(key, newCustomPresetsStr).apply();
    }

    public static void loadSpinner(String prefix, Spinner spinner, String selectedId) {
        Context context = spinner.getContext();
        ArrayList<Box86_64Preset> presets = getPresets(prefix, context);

        int selectedPosition = 0;
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id.equals(selectedId)) {
                selectedPosition = i;
                break;
            }
        }

        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, presets));
        spinner.setSelection(selectedPosition);
    }

    public static String getSpinnerSelectedId(Spinner spinner) {
        SpinnerAdapter adapter = spinner.getAdapter();
        int selectedPosition = spinner.getSelectedItemPosition();
        if (adapter != null && adapter.getCount() > 0 && selectedPosition >= 0) {
            return ((Box86_64Preset)adapter.getItem(selectedPosition)).id;
        }
        else return Box86_64Preset.COMPATIBILITY;
    }

    public static void exportPreset(String prefix, Context context, String id) {
        if (context == null || id == null) return;

        Box86_64Preset preset = getPreset(prefix, context, id);
        if (preset == null) {
            AppUtils.showToast(context, "Failed to export preset");
            return;
        }

        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            AppUtils.showToast(context, "Failed to export preset");
            return;
        }

        File presetFile = new File(baseDir, preset.name + BOX_PRESET_FILE_EXTENSION);
        try {
            FileOutputStream fos = new FileOutputStream(presetFile);
            PrintWriter pw = new PrintWriter(fos);
            pw.write("Type:" + prefix + "\n");
            pw.write("Name:" + preset.name + "\n");
            pw.write("EnvVars:" + getEnvVars(prefix, context, preset.id).toString() + "\n");
            pw.close();
            fos.close();
        }
        catch (IOException ignored) {
        }

        if (presetFile.exists()) {
            AppUtils.showToast(context, "Preset " + presetFile.getName() + " exported successfully at " + presetFile.getParentFile().getPath());
        }
        else {
            AppUtils.showToast(context, "Failed to export preset");
        }
    }

    public static File[] listExportedPresets(Context context) {
        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File[] files = baseDir.listFiles((dir, name) -> name != null && name.toLowerCase(Locale.ENGLISH).endsWith(BOX_PRESET_FILE_EXTENSION));
        return files != null ? files : new File[0];
    }

    public static void importPreset(String prefix, Context context, File file) {
        if (file == null || !file.exists()) {
            AppUtils.showToast(context, "Failed to import preset");
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            importPreset(prefix, context, fis);
            fis.close();
            AppUtils.showToast(context, "Preset imported successfully");
        }
        catch (IOException ignored) {
            AppUtils.showToast(context, "Failed to import preset");
        }
    }

    public static void importPreset(String prefix, Context context, InputStream stream) {
        if (context == null || stream == null) return;

        SharedPreferences preferences = new com.winlator.cmod.core.MmkvPreferences();
        String customPresetStr = preferences.getString(prefix + "_custom_presets", "");
        ArrayList<String> lines = new ArrayList<>();

        try {
            String importedType = null;
            String name = null;
            String envVars = null;

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }

            for (int i = 0; i < lines.size(); i++) {
                String[] contents = lines.get(i).split(":", 2);
                if (contents.length < 2) continue;
                switch (contents[0]) {
                    case "Type":
                        importedType = contents[1];
                        break;
                    case "Name":
                        name = contents[1];
                        break;
                    case "EnvVars":
                        envVars = contents[1];
                        break;
                }
            }

            if (importedType != null && !importedType.equals(prefix)) return;

            if (name != null && envVars != null) {
                customPresetStr = customPresetStr + (!customPresetStr.equals("") ? "," : "") +
                        Box86_64Preset.CUSTOM + "-" + getNextPresetId(context, prefix) + "|" + name + "|" + envVars;
                preferences.edit().putString(prefix + "_custom_presets", customPresetStr).apply();
            }
        }
        catch (IOException ignored) {
        }
    }
}
