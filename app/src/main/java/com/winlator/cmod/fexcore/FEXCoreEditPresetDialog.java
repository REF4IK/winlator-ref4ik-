package com.winlator.cmod.fexcore;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.ArrayUtils;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

public class FEXCoreEditPresetDialog extends ContentDialog {
    private final Context context;
    private final FEXCorePreset preset;
    private final boolean readonly;
    private Runnable onConfirmCallback;

    private boolean isDarkMode;

    public FEXCoreEditPresetDialog(@NonNull Context context, String presetId) {
        super(context, R.layout.box86_64_edit_preset_dialog);
        this.context = context;

        preset = presetId != null ? FEXCorePresetManager.getPreset(context, presetId) : null;
        readonly = preset != null && !preset.isCustom();

        String title = StringUtils.getString(context, "fexcore_preset");
        setTitle(title != null ? title : "FEXCore preset");
        setIcon(R.drawable.icon_env_var);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        isDarkMode = sharedPreferences.getBoolean("dark_mode", false);

        TextView environmentVariablesLabel = findViewById(R.id.TVEnvironmentVariables);
        applyFieldSetLabelStyle(environmentVariablesLabel, isDarkMode);

        final EditText etName = findViewById(R.id.ETName);
        etName.getLayoutParams().width = AppUtils.getPreferredDialogWidth(context);
        etName.setEnabled(!readonly);

        if (preset != null) {
            etName.setText(preset.name);
        }
        else {
            etName.setText(context.getString(R.string.preset) + "-" + FEXCorePresetManager.getNextPresetId(context));
        }

        applyDarkThemeToEditText(etName);

        loadEnvVarsList();

        super.setOnConfirmCallback(() -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) return;
            name = name.replaceAll("[,\\|]+", "");
            FEXCorePresetManager.editPreset(context, preset != null ? preset.id : null, name, getEnvVars());
            if (onConfirmCallback != null) onConfirmCallback.run();
        });
    }

    @Override
    public void setOnConfirmCallback(Runnable onConfirmCallback) {
        this.onConfirmCallback = onConfirmCallback;
    }

    private EnvVars getEnvVars() {
        EnvVars envVars = new EnvVars();
        LinearLayout parent = findViewById(R.id.LLContent);
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            String name = ((TextView) child.findViewById(R.id.TextView)).getText().toString();

            Spinner spinner = child.findViewById(R.id.Spinner);
            ToggleButton toggleButton = child.findViewById(R.id.ToggleButton);
            EditText editText = child.findViewById(R.id.EditText);

            boolean toggleSwitch = toggleButton.getVisibility() == View.VISIBLE;
            boolean editTextMode = editText != null && editText.getVisibility() == View.VISIBLE;

            String value;
            if (editTextMode) {
                value = editText.getText().toString();
            }
            else if (toggleSwitch) {
                value = toggleButton.isChecked() ? "1" : "0";
            }
            else {
                value = spinner.getSelectedItem().toString();
            }

            envVars.put(name, value);
        }
        return envVars;
    }

    private void loadEnvVarsList() {
        try {
            LinearLayout parent = findViewById(R.id.LLContent);
            LayoutInflater inflater = LayoutInflater.from(context);
            JSONArray data = new JSONArray(FileUtils.readString(context, "fexcore_env_vars.json"));
            EnvVars envVars = preset != null ? FEXCorePresetManager.getEnvVars(context, preset.id) : null;

            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                final String name = item.getString("name");
                View child = inflater.inflate(R.layout.box86_64_env_var_list_item, parent, false);
                ((TextView) child.findViewById(R.id.TextView)).setText(name);

                child.findViewById(R.id.BTHelp).setOnClickListener((v) -> {
                    String suffix = name.replace("FEX_", "").toLowerCase(Locale.ENGLISH);
                    String value = StringUtils.getString(context, "fexcore_env_var_help__" + suffix);
                    if (value != null) AppUtils.showHelpBox(context, v, value);
                });

                Spinner spinner = child.findViewById(R.id.Spinner);
                ToggleButton toggleButton = child.findViewById(R.id.ToggleButton);
                EditText editText = child.findViewById(R.id.EditText);

                String[] values = ArrayUtils.toStringArray(item.getJSONArray("values"));
                String value = envVars != null && envVars.has(name) ? envVars.get(name) : item.getString("defaultValue");

                boolean toggleSwitch = item.optBoolean("toggleSwitch", item.optBoolean("toggleswitch", false));
                boolean editTextMode = item.optBoolean("editText", false);

                if (toggleSwitch) {
                    toggleButton.setVisibility(View.VISIBLE);
                    toggleButton.setEnabled(!readonly);
                    toggleButton.setChecked(value.equals("1"));
                    if (readonly) toggleButton.setAlpha(0.5f);
                }
                else if (editTextMode && editText != null) {
                    editText.setVisibility(View.VISIBLE);
                    editText.setEnabled(!readonly);
                    editText.setText(value);
                    if (readonly) editText.setAlpha(0.5f);
                }
                else {
                    spinner.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
                    spinner.setVisibility(View.VISIBLE);
                    spinner.setEnabled(!readonly);
                    spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, values));
                    AppUtils.setSpinnerSelectionFromValue(spinner, value);
                }

                parent.addView(child);
            }
        }
        catch (JSONException ignored) {
        }
    }

    private static void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
        if (isDarkMode) {
            textView.setTextColor(Color.parseColor("#cccccc"));
            textView.setBackgroundResource(R.color.content_dialog_background_dark);
        }
        else {
            textView.setTextColor(Color.parseColor("#424242"));
            textView.setBackgroundResource(R.color.window_background_color);
        }
    }

    private void applyDarkThemeToEditText(EditText editText) {
        if (isDarkMode) {
            editText.setTextColor(Color.WHITE);
            editText.setHintTextColor(Color.GRAY);
            editText.setBackgroundResource(R.drawable.edit_text_dark);
        }
        else {
            editText.setTextColor(Color.BLACK);
            editText.setHintTextColor(Color.GRAY);
            editText.setBackgroundResource(R.drawable.edit_text);
        }
    }
}
