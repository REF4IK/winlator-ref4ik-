package com.winlator.cmod.fexcore;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;

import androidx.preference.PreferenceManager;

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

public class FEXCorePresetManager {
    private static final String KEY_CUSTOM_PRESETS = "fexcore_custom_presets";
    private static final String FEX_PRESET_FILE_EXTENSION = ".fexx";

    public static EnvVars getEnvVars(Context context, String id) {
        EnvVars envVars = new EnvVars();

        if (id.equals(FEXCorePreset.STABILITY)) {
            envVars.put("FEX_TSOENABLED", "1");
            envVars.put("FEX_VECTORTSOENABLED", "1");
            envVars.put("FEX_MEMCPYSETTSOENABLED", "1");
            envVars.put("FEX_HALFBARRIERTSOENABLED", "1");
            envVars.put("FEX_STRICTINPROCESSSPLITLOCKS", "1");
            envVars.put("FEX_KERNELUNALIGNEDATOMICBACKPATCHING", "1");
            envVars.put("FEX_X87REDUCEDPRECISION", "0");
            envVars.put("FEX_MULTIBLOCK", "0");
            envVars.put("FEX_SMCCHECKS", "full");
            envVars.put("FEX_SMC_CHECKS", "full");
            envVars.put("FEX_SMALLTSCSCALE", "0");
            envVars.put("FEX_HIDEHYBRID", "0");
            envVars.put("FEX_HIDEHYPERVISORBIT", "0");
            envVars.put("FEX_VOLATILEMETADATA", "0");
            envVars.put("FEX_MONOHACKS", "1");
            envVars.put("FEX_DISABLEL2CACHE", "0");
            envVars.put("FEX_DYNAMICL1CACHE", "0");
            envVars.put("FEX_SILENTLOG", "0");
            envVars.put("FEX_PROFILESTATS", "0");
        }
        else if (id.equals(FEXCorePreset.COMPATIBILITY)) {
            envVars.put("FEX_TSOENABLED", "1");
            envVars.put("FEX_VECTORTSOENABLED", "1");
            envVars.put("FEX_MEMCPYSETTSOENABLED", "1");
            envVars.put("FEX_HALFBARRIERTSOENABLED", "1");
            envVars.put("FEX_STRICTINPROCESSSPLITLOCKS", "0");
            envVars.put("FEX_KERNELUNALIGNEDATOMICBACKPATCHING", "1");
            envVars.put("FEX_X87REDUCEDPRECISION", "0");
            envVars.put("FEX_MULTIBLOCK", "1");
            envVars.put("FEX_SMCCHECKS", "full");
            envVars.put("FEX_SMC_CHECKS", "full");
            envVars.put("FEX_SMALLTSCSCALE", "0");
            envVars.put("FEX_HIDEHYBRID", "0");
            envVars.put("FEX_HIDEHYPERVISORBIT", "0");
            envVars.put("FEX_VOLATILEMETADATA", "0");
            envVars.put("FEX_MONOHACKS", "1");
            envVars.put("FEX_DISABLEL2CACHE", "0");
            envVars.put("FEX_DYNAMICL1CACHE", "0");
            envVars.put("FEX_SILENTLOG", "0");
            envVars.put("FEX_PROFILESTATS", "0");
        }
        else if (id.equals(FEXCorePreset.INTERMEDIATE)) {
            envVars.put("FEX_TSOENABLED", "1");
            envVars.put("FEX_VECTORTSOENABLED", "0");
            envVars.put("FEX_MEMCPYSETTSOENABLED", "0");
            envVars.put("FEX_HALFBARRIERTSOENABLED", "1");
            envVars.put("FEX_STRICTINPROCESSSPLITLOCKS", "0");
            envVars.put("FEX_KERNELUNALIGNEDATOMICBACKPATCHING", "1");
            envVars.put("FEX_X87REDUCEDPRECISION", "1");
            envVars.put("FEX_MULTIBLOCK", "1");
            envVars.put("FEX_SMCCHECKS", "mtrack");
            envVars.put("FEX_SMC_CHECKS", "mtrack");
            envVars.put("FEX_SMALLTSCSCALE", "1");
            envVars.put("FEX_HIDEHYBRID", "0");
            envVars.put("FEX_HIDEHYPERVISORBIT", "0");
            envVars.put("FEX_VOLATILEMETADATA", "1");
            envVars.put("FEX_MONOHACKS", "0");
            envVars.put("FEX_DISABLEL2CACHE", "0");
            envVars.put("FEX_DYNAMICL1CACHE", "0");
            envVars.put("FEX_SILENTLOG", "0");
            envVars.put("FEX_PROFILESTATS", "0");
        }
        else if (id.equals(FEXCorePreset.PERFORMANCE)) {
            envVars.put("FEX_TSOENABLED", "0");
            envVars.put("FEX_VECTORTSOENABLED", "0");
            envVars.put("FEX_MEMCPYSETTSOENABLED", "0");
            envVars.put("FEX_HALFBARRIERTSOENABLED", "0");
            envVars.put("FEX_STRICTINPROCESSSPLITLOCKS", "0");
            envVars.put("FEX_KERNELUNALIGNEDATOMICBACKPATCHING", "1");
            envVars.put("FEX_X87REDUCEDPRECISION", "1");
            envVars.put("FEX_MULTIBLOCK", "1");
            envVars.put("FEX_SMCCHECKS", "mtrack");
            envVars.put("FEX_SMC_CHECKS", "mtrack");
            envVars.put("FEX_SMALLTSCSCALE", "1");
            envVars.put("FEX_HIDEHYBRID", "1");
            envVars.put("FEX_HIDEHYPERVISORBIT", "0");
            envVars.put("FEX_VOLATILEMETADATA", "1");
            envVars.put("FEX_MONOHACKS", "0");
            envVars.put("FEX_DISABLEL2CACHE", "0");
            envVars.put("FEX_DYNAMICL1CACHE", "0");
            envVars.put("FEX_SILENTLOG", "0");
            envVars.put("FEX_PROFILESTATS", "0");
        }
        else if (id.equals(FEXCorePreset.EXTREME)) {
            envVars.put("FEX_TSOENABLED", "0");
            envVars.put("FEX_VECTORTSOENABLED", "0");
            envVars.put("FEX_MEMCPYSETTSOENABLED", "0");
            envVars.put("FEX_HALFBARRIERTSOENABLED", "0");
            envVars.put("FEX_STRICTINPROCESSSPLITLOCKS", "0");
            envVars.put("FEX_KERNELUNALIGNEDATOMICBACKPATCHING", "0");
            envVars.put("FEX_X87REDUCEDPRECISION", "1");
            envVars.put("FEX_MULTIBLOCK", "1");
            envVars.put("FEX_SMCCHECKS", "none");
            envVars.put("FEX_SMC_CHECKS", "none");
            envVars.put("FEX_SMALLTSCSCALE", "1");
            envVars.put("FEX_HIDEHYBRID", "1");
            envVars.put("FEX_HIDEHYPERVISORBIT", "1");
            envVars.put("FEX_VOLATILEMETADATA", "1");
            envVars.put("FEX_MONOHACKS", "0");
            envVars.put("FEX_DISABLEL2CACHE", "1");
            envVars.put("FEX_DYNAMICL1CACHE", "1");
            envVars.put("FEX_SILENTLOG", "0");
            envVars.put("FEX_PROFILESTATS", "0");
        }
        else if (id != null && id.startsWith(FEXCorePreset.CUSTOM)) {
            for (String[] preset : customPresetsIterator(context)) {
                if (preset.length >= 3 && preset[0].equals(id)) {
                    envVars.putAll(preset[2]);
                    break;
                }
            }
        }

        return envVars;
    }

    public static ArrayList<FEXCorePreset> getPresets(Context context) {
        ArrayList<FEXCorePreset> presets = new ArrayList<>();
        presets.add(new FEXCorePreset(FEXCorePreset.STABILITY, context.getString(com.winlator.cmod.R.string.stability)));
        presets.add(new FEXCorePreset(FEXCorePreset.COMPATIBILITY, context.getString(com.winlator.cmod.R.string.compatibility)));
        presets.add(new FEXCorePreset(FEXCorePreset.INTERMEDIATE, context.getString(com.winlator.cmod.R.string.intermediate)));
        presets.add(new FEXCorePreset(FEXCorePreset.PERFORMANCE, context.getString(com.winlator.cmod.R.string.performance)));
        presets.add(new FEXCorePreset(FEXCorePreset.EXTREME, context.getString(com.winlator.cmod.R.string.extreme)));
        for (String[] preset : customPresetsIterator(context)) {
            if (preset.length >= 2) presets.add(new FEXCorePreset(preset[0], preset[1]));
        }
        return presets;
    }

    public static FEXCorePreset getPreset(Context context, String id) {
        for (FEXCorePreset preset : getPresets(context)) if (preset.id.equals(id)) return preset;
        return null;
    }

    private static Iterable<String[]> customPresetsIterator(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String customPresetsStr = preferences.getString(KEY_CUSTOM_PRESETS, "");
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

    public static int getNextPresetId(Context context) {
        int maxId = 0;
        for (String[] preset : customPresetsIterator(context)) {
            if (preset.length == 0) continue;
            try {
                maxId = Math.max(maxId, Integer.parseInt(preset[0].replace(FEXCorePreset.CUSTOM + "-", "")));
            } catch (Exception ignored) {
            }
        }
        return maxId + 1;
    }

    public static void editPreset(Context context, String id, String name, EnvVars envVars) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String customPresetsStr = preferences.getString(KEY_CUSTOM_PRESETS, "");

        if (id != null) {
            String[] customPresets = customPresetsStr.split(",");
            for (int i = 0; i < customPresets.length; i++) {
                String[] preset = customPresets[i].split("\\|");
                if (preset.length > 0 && preset[0].equals(id)) {
                    customPresets[i] = id + "|" + name + "|" + envVars.toString();
                    break;
                }
            }
            customPresetsStr = String.join(",", customPresets);
        }
        else {
            String preset = FEXCorePreset.CUSTOM + "-" + getNextPresetId(context) + "|" + name + "|" + envVars.toString();
            customPresetsStr += (!customPresetsStr.isEmpty() ? "," : "") + preset;
        }
        preferences.edit().putString(KEY_CUSTOM_PRESETS, customPresetsStr).apply();
    }

    public static void duplicatePreset(Context context, String id) {
        ArrayList<FEXCorePreset> presets = getPresets(context);
        FEXCorePreset originPreset = null;
        for (FEXCorePreset preset : presets) {
            if (preset.id.equals(id)) {
                originPreset = preset;
                break;
            }
        }
        if (originPreset == null) return;

        String newName;
        for (int i = 1;; i++) {
            newName = originPreset.name + " (" + i + ")";
            boolean found = false;
            for (FEXCorePreset preset : presets) {
                if (preset.name.equals(newName)) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
        }

        editPreset(context, null, newName, getEnvVars(context, originPreset.id));
    }

    public static void removePreset(Context context, String id) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String oldCustomPresetsStr = preferences.getString(KEY_CUSTOM_PRESETS, "");
        String newCustomPresetsStr = "";

        String[] customPresets = oldCustomPresetsStr.split(",");
        for (int i = 0; i < customPresets.length; i++) {
            String[] preset = customPresets[i].split("\\|");
            if (preset.length > 0 && !preset[0].equals(id)) {
                newCustomPresetsStr += (!newCustomPresetsStr.isEmpty() ? "," : "") + customPresets[i];
            }
        }

        preferences.edit().putString(KEY_CUSTOM_PRESETS, newCustomPresetsStr).apply();
    }

    public static void exportPreset(Context context, String id) {
        if (context == null || id == null) return;

        File presetFile = null;
        FEXCorePreset preset = getPreset(context, id);
        if (preset == null) {
            AppUtils.showToast(context, "Failed to export preset");
            return;
        }

        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            AppUtils.showToast(context, "Failed to export preset");
            return;
        }

        presetFile = new File(baseDir, preset.name + FEX_PRESET_FILE_EXTENSION);

        try {
            FileOutputStream fos = new FileOutputStream(presetFile);
            PrintWriter pw = new PrintWriter(fos);
            pw.write("ID:" + preset.id + "\n");
            pw.write("Name:" + preset.name + "\n");
            pw.write("EnvVars:" + getEnvVars(context, preset.id).toString() + "\n");
            pw.close();
            fos.close();
        } catch (IOException ignored) {
        }

        if (presetFile.exists()) {
            AppUtils.showToast(context, "Preset " + presetFile.getName() + " exported successfully at " + presetFile.getParentFile().getPath());
        } else {
            AppUtils.showToast(context, "Failed to export preset");
        }
    }

    public static File[] listExportedPresets(Context context) {
        File baseDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File[] files = baseDir.listFiles((dir, name) -> name != null && name.toLowerCase(Locale.ENGLISH).endsWith(FEX_PRESET_FILE_EXTENSION));
        return files != null ? files : new File[0];
    }

    public static void importPreset(Context context, File file) {
        if (file == null || !file.exists()) {
            AppUtils.showToast(context, "Failed to import preset");
            return;
        }
        try {
            FileInputStream fis = new FileInputStream(file);
            importPreset(context, fis);
            fis.close();
            AppUtils.showToast(context, "Preset imported successfully");
        }
        catch (IOException ignored) {
            AppUtils.showToast(context, "Failed to import preset");
        }
    }

    public static void importPreset(Context context, InputStream stream) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String customPresetStr = preferences.getString(KEY_CUSTOM_PRESETS, "");
        ArrayList<String> lines = new ArrayList<>();

        try {
            String[] preset = new String[3];
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            for (int i = 0; i < lines.size(); i++) {
                String[] contents = lines.get(i).split(":", 2);
                if (contents.length < 2) continue;
                switch (contents[0]) {
                    case "ID":
                        preset[0] = contents[1];
                        break;
                    case "Name":
                        preset[1] = contents[1];
                        break;
                    case "EnvVars":
                        preset[2] = contents[1];
                        break;
                }
            }
            if (preset[1] != null && preset[2] != null) {
                customPresetStr = customPresetStr + (!customPresetStr.equals("") ? "," : "") +
                        FEXCorePreset.CUSTOM + "-" + getNextPresetId(context) + "|" + preset[1] + "|" + preset[2];
            }
        } catch (IOException ignored) {
        }

        preferences.edit().putString(KEY_CUSTOM_PRESETS, customPresetStr).apply();
    }

    public static void loadSpinner(Spinner spinner, String selectedId) {
        Context context = spinner.getContext();
        ArrayList<FEXCorePreset> presets = getPresets(context);

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
            return ((FEXCorePreset) adapter.getItem(selectedPosition)).id;
        }
        else return FEXCorePreset.COMPATIBILITY;
    }
}
