package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.renderer.VulkanRenderer;
import com.winlator.cmod.widget.SeekBar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class ScreenEffectDialog extends ContentDialog {

    private final XServerDisplayActivity activity;
    private final CheckBox cbEnableCRTShader;
    private final CheckBox cbEnableFXAA;
    private final CheckBox cbEnableToonShader;
    private final CheckBox cbEnableNTSCEffect;
    private final CheckBox cbEnableVignetteEffect;
    private final CheckBox cbEnableSepiaEffect;
    private final CheckBox cbEnableBlurEffect;
    private final CheckBox cbEnablePixelateEffect;
    private final CheckBox cbEnableGrayscaleEffect;
    private final CheckBox cbEnableSharpenEffect;
    private final CheckBox cbEnableSmoothEffect;
    private final CheckBox cbEnableHDREffect;
    private final CheckBox cbEnableFSREffect;
    private final CheckBox cbFsrAspectFit;
    private final Spinner sFsrQuality;
    private final SharedPreferences preferences;
    private final boolean isDarkMode;
    private final Spinner sProfile;
    private final SeekBar sbBrightness;
    private final SeekBar sbContrast;
    private final SeekBar sbGamma;
    private final SeekBar sbFsrSharpness;

    private static final String TAG = "ScreenEffectDialog";


    public ScreenEffectDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.screen_effect_dialog);
        this.activity = activity;

        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        isDarkMode = preferences.getBoolean("dark_mode", false);

        TextView lblColorAdjustment = findViewById(R.id.LBLColorAdjustment);
        applyFieldSetLabelStyle(lblColorAdjustment, isDarkMode);

        sProfile = findViewById(R.id.SProfile);
        sbBrightness = findViewById(R.id.SBBrightness);
        sbContrast = findViewById(R.id.SBContrast);
        sbGamma = findViewById(R.id.SBGamma);
        sbFsrSharpness = findViewById(R.id.SBFsrSharpness);
        cbEnableFXAA = findViewById(R.id.CBEnableFXAA);
        cbEnableCRTShader = findViewById(R.id.CBEnableCRTShader);

        cbEnableToonShader = findViewById(R.id.CBEnableToonShader);
        cbEnableNTSCEffect = findViewById(R.id.CBEnableNTSCEffect);
        cbEnableVignetteEffect = findViewById(R.id.CBEnableVignetteEffect);
        cbEnableSepiaEffect = findViewById(R.id.CBEnableSepiaEffect);
        cbEnableBlurEffect = findViewById(R.id.CBEnableBlurEffect);
        cbEnablePixelateEffect = findViewById(R.id.CBEnablePixelateEffect);
        cbEnableGrayscaleEffect = findViewById(R.id.CBEnableGrayscaleEffect);
        cbEnableSharpenEffect = findViewById(R.id.CBEnableSharpenEffect);
        cbEnableSmoothEffect = findViewById(R.id.CBEnableSmoothEffect);
        cbEnableHDREffect = findViewById(R.id.CBEnableHDREffect);
        cbEnableFSREffect = findViewById(R.id.CBEnableFSREffect);
        cbFsrAspectFit = findViewById(R.id.CBFsrAspectFit);
        sFsrQuality = findViewById(R.id.SFsrQuality);
        {
            ArrayAdapter<String> qAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item, new String[]{
                    activity.getString(R.string.fsr_quality_ultra),
                    activity.getString(R.string.fsr_quality_quality),
                    activity.getString(R.string.fsr_quality_balanced),
                    activity.getString(R.string.fsr_quality_performance)
            });
            qAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            sFsrQuality.setAdapter(qAdapter);
            sFsrQuality.setSelection(1);
        }

        applyDialogThemeOverrides();


        VulkanRenderer renderer = (VulkanRenderer) activity.getXServerView().getRenderer();
        if (renderer == null) {
            Log.e(TAG, "Renderer is null in ScreenEffectDialog initialization!");
            return;
        }

        Log.d(TAG, "ScreenEffectDialog initialized");

        // Load saved effect settings from SharedPreferences
        loadEffectSettingsFromPrefs();

        loadProfileSpinner(sProfile, activity.getScreenEffectProfile());

        sProfile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    loadProfile(sProfile.getSelectedItem().toString());
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        Button resetButton = findViewById(R.id.BTReset);
        resetButton.setVisibility(View.VISIBLE);
        resetButton.setOnClickListener(v -> resetSettings());

        findViewById(R.id.BTConfirm).setOnClickListener(v -> {
            Log.d(TAG, "BTConfirm clicked. Preparing to save profile and apply effects.");
            saveProfile(sProfile);
            Log.d(TAG, "Profile saved.");

            Log.d(TAG, "Calling applyVulkanEffects() directly.");
            applyVulkanEffects(renderer);

            Log.d(TAG, "Effects applied. Dismissing dialog.");
            dismiss();
        });

        findViewById(R.id.BTAddProfile).setOnClickListener(v -> promptAddProfile());
        findViewById(R.id.BTRemoveProfile).setOnClickListener(v -> promptDeleteProfile());

        setOnConfirmCallback(() -> {
            Log.d(TAG, "OnConfirm callback triggered. Applying effects.");
            applyVulkanEffects(renderer);
            Log.d(TAG, "Effects applied from callback.");
            dismiss();
        });

    }

    private static void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
//        Context context = textView.getContext();

        if (isDarkMode) {
            // Apply dark mode-specific attributes
            textView.setTextColor(Color.parseColor("#cccccc")); // Set text color to #cccccc
            textView.setBackgroundResource(R.color.window_background_color_dark); // Set dark background color
        } else {
            // Apply light mode-specific attributes (original FieldSetLabel)
            textView.setTextColor(Color.parseColor("#424242")); // Set text color to #bdbdbd
            textView.setBackgroundResource(R.color.window_background_color); // Set light background color
        }
    }

    private void applyDialogThemeOverrides() {
        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(isDarkMode
                    ? R.drawable.content_dialog_background_dark
                    : R.drawable.content_dialog_background);
        }

        View colorFieldSet = findViewById(R.id.LLColorFieldSet);
        View divider = findViewById(R.id.VScreenEffectsDivider);
        Spinner profileSpinner = findViewById(R.id.SProfile);

        if (colorFieldSet != null) {
            colorFieldSet.setBackgroundResource(isDarkMode
                    ? R.drawable.bordered_panel_dark
                    : R.drawable.bordered_panel);
        }
        if (divider != null) {
            divider.setBackgroundColor(isDarkMode
                    ? Color.parseColor("#707070")
                    : Color.parseColor("#9E9E9E"));
        }
        if (profileSpinner != null) {
            profileSpinner.setBackgroundResource(isDarkMode
                    ? R.drawable.combo_box_dark
                    : R.drawable.combo_box);
            profileSpinner.setPopupBackgroundResource(isDarkMode
                    ? R.drawable.content_dialog_background_dark
                    : R.drawable.content_dialog_background);
        }

        int textColor = ContextCompat.getColor(activity, isDarkMode ? R.color.white : R.color.black);
        View content = getContentView();
        if (content instanceof ViewGroup) {
            setTextColorRecursive((ViewGroup) content, textColor);
        }
        styleCheckBoxes(textColor);
    }

    private void setTextColorRecursive(ViewGroup viewGroup, int color) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof ViewGroup) {
                setTextColorRecursive((ViewGroup) child, color);
            } else if (child instanceof TextView && !(child instanceof Button)) {
                ((TextView) child).setTextColor(color);
            }
        }
    }

    private void styleCheckBoxes(int textColor) {
        CheckBox[] checkBoxes = new CheckBox[]{
                cbEnableFXAA,
                cbEnableCRTShader,
                cbEnableToonShader,
                cbEnableNTSCEffect,
                cbEnableVignetteEffect,
                cbEnableSepiaEffect,
                cbEnableBlurEffect,
                cbEnablePixelateEffect,
                cbEnableGrayscaleEffect,
                cbEnableSharpenEffect,
                cbEnableSmoothEffect,
                cbEnableHDREffect,
                cbEnableFSREffect,
                cbFsrAspectFit
        };

        for (CheckBox checkBox : checkBoxes) {
            if (checkBox != null) {
                checkBox.setTextColor(textColor);
            }
        }
    }

    private void promptAddProfile() {
        ContentDialog.prompt(activity, R.string.do_you_want_to_add_a_new_profile, null, name -> addProfile(name, sProfile));
    }

    private void promptDeleteProfile() {
        if (sProfile.getSelectedItemPosition() > 0) {
            String selectedProfile = sProfile.getSelectedItem().toString();
            ContentDialog.confirm(activity, R.string.do_you_want_to_remove_this_profile, () -> removeProfile(selectedProfile, sProfile));
        } else {
            AppUtils.showToast(activity, R.string.no_profile_selected);
        }
    }

    private void addProfile(String newName, Spinner sProfile) {
        Set<String> profiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
        for (String profile : profiles) {
            String[] parts = profile.split(":");
            if (parts[0].equals(newName)) {
                return;
            }
        }
        profiles.add(newName + ":");
        preferences.edit().putStringSet("screen_effect_profiles", profiles).apply();
        loadProfileSpinner(sProfile, newName);
    }

    private void loadProfileSpinner(Spinner sProfile, String selectedName) {
        Set<String> profiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
        ArrayList<String> items = new ArrayList<>();
        items.add("-- " + activity.getString(R.string.default_profile) + " --");
        int selectedPosition = 0, position = 1;
        for (String profile : profiles) {
            String[] parts = profile.split(":");
            items.add(parts[0]);
            if (parts[0].equals(selectedName)) {
                selectedPosition = position;
            }
            position++;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(isDarkMode
                            ? ContextCompat.getColor(activity, R.color.white)
                            : ContextCompat.getColor(activity, R.color.black));
                }
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(isDarkMode
                            ? ContextCompat.getColor(activity, R.color.white)
                            : ContextCompat.getColor(activity, R.color.black));
                    if (isDarkMode) {
                        view.setBackgroundColor(ContextCompat.getColor(activity, R.color.content_dialog_background_dark));
                    }
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sProfile.setAdapter(adapter);
        sProfile.setSelection(selectedPosition);
    }

    private void loadProfile(String name) {
        Set<String> profiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
        for (String profile : profiles) {
            String[] parts = profile.split(":");
            if (parts[0].equals(name) && parts.length > 1 && !parts[1].isEmpty()) {
                KeyValueSet settings = new KeyValueSet(parts[1]);
                sbBrightness.setValue(settings.getFloat("brightness", 0));
                sbContrast.setValue(settings.getFloat("contrast", 1.0f));
                sbGamma.setValue(settings.getFloat("gamma", 1.0f));
                cbEnableFXAA.setChecked(settings.getBoolean("fxaa", false));
                cbEnableCRTShader.setChecked(settings.getBoolean("crt_shader", false));
                cbEnableToonShader.setChecked(settings.getBoolean("toon_shader", false));
                cbEnableNTSCEffect.setChecked(settings.getBoolean("ntsc_effect", false));
                cbEnableVignetteEffect.setChecked(settings.getBoolean("vignette_effect", false));
                cbEnableSepiaEffect.setChecked(settings.getBoolean("sepia_effect", false));
                cbEnableBlurEffect.setChecked(settings.getBoolean("blur_effect", false));
                cbEnablePixelateEffect.setChecked(settings.getBoolean("pixelate_effect", false));
                cbEnableGrayscaleEffect.setChecked(settings.getBoolean("grayscale_effect", false));
                cbEnableSharpenEffect.setChecked(settings.getBoolean("sharpen_effect", false));
                cbEnableSmoothEffect.setChecked(settings.getBoolean("smooth_effect", false));
                cbEnableHDREffect.setChecked(settings.getBoolean("hdr_effect", false));
                cbEnableFSREffect.setChecked(settings.getBoolean("fsr_effect", false));
                sbFsrSharpness.setValue(settings.getFloat("fsr_sharpness", 75f));
                sFsrQuality.setSelection(Math.max(0, Math.min(3, (int) settings.getFloat("fsr_quality", 1f))));
                cbFsrAspectFit.setChecked(settings.getBoolean("fsr_aspect_fit", false));
                return;
            }
        }
    }

    private void removeProfile(String targetName, Spinner sProfile) {
        Set<String> profiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
        profiles.removeIf(profile -> profile.split(":")[0].equals(targetName));
        preferences.edit().putStringSet("screen_effect_profiles", profiles).apply();
        loadProfileSpinner(sProfile, null);
        resetSettings();
    }

    private void resetSettings() {
        sbBrightness.setValue(0);
        sbContrast.setValue(0);
        sbGamma.setValue(1.0f);
        cbEnableFXAA.setChecked(false);
        cbEnableCRTShader.setChecked(false);
        cbEnableToonShader.setChecked(false);
        cbEnableNTSCEffect.setChecked(false);
        cbEnableVignetteEffect.setChecked(false);
        cbEnableSepiaEffect.setChecked(false);
        cbEnableBlurEffect.setChecked(false);
        cbEnablePixelateEffect.setChecked(false);
        cbEnableGrayscaleEffect.setChecked(false);
        cbEnableSharpenEffect.setChecked(false);
        cbEnableSmoothEffect.setChecked(false);
        cbEnableHDREffect.setChecked(false);
        cbEnableFSREffect.setChecked(false);
        sbFsrSharpness.setValue(75f);
        sFsrQuality.setSelection(1);
        cbFsrAspectFit.setChecked(false);
    }

    private void saveProfile(Spinner sProfile) {
        if (sProfile.getSelectedItemPosition() > 0) {
            String selectedProfile = sProfile.getSelectedItem().toString();
            Set<String> oldProfiles = new LinkedHashSet<>(preferences.getStringSet("screen_effect_profiles", new LinkedHashSet<>()));
            Set<String> newProfiles = new LinkedHashSet<>();
            KeyValueSet settings = new KeyValueSet();
            settings.put("brightness", sbBrightness.getValue());
            settings.put("contrast", sbContrast.getValue());
            settings.put("gamma", sbGamma.getValue());
            settings.put("fxaa", cbEnableFXAA.isChecked());
            settings.put("crt_shader", cbEnableCRTShader.isChecked());
            settings.put("toon_shader", cbEnableToonShader.isChecked());
            settings.put("ntsc_effect", cbEnableNTSCEffect.isChecked());
            settings.put("vignette_effect", cbEnableVignetteEffect.isChecked());
            settings.put("sepia_effect", cbEnableSepiaEffect.isChecked());
            settings.put("blur_effect", cbEnableBlurEffect.isChecked());
            settings.put("pixelate_effect", cbEnablePixelateEffect.isChecked());
            settings.put("grayscale_effect", cbEnableGrayscaleEffect.isChecked());
            settings.put("sharpen_effect", cbEnableSharpenEffect.isChecked());
            settings.put("smooth_effect", cbEnableSmoothEffect.isChecked());
            settings.put("hdr_effect", cbEnableHDREffect.isChecked());
            settings.put("fsr_effect", cbEnableFSREffect.isChecked());
            settings.put("fsr_sharpness", sbFsrSharpness.getValue());
            settings.put("fsr_quality", (float) sFsrQuality.getSelectedItemPosition());
            settings.put("fsr_aspect_fit", cbFsrAspectFit.isChecked());

            for (String profile : oldProfiles) {
                String[] parts = profile.split(":");
                if (parts[0].equals(selectedProfile)) {
                    newProfiles.add(selectedProfile + ":" + settings.toString());
                } else {
                    newProfiles.add(profile);
                }
            }
            preferences.edit().putStringSet("screen_effect_profiles", newProfiles).apply();
            activity.setScreenEffectProfile(selectedProfile);
        }
    }

    private void loadEffectSettingsFromPrefs() {
        // Load effect settings from SharedPreferences (saved by profile or defaults)
        float brightness = preferences.getFloat("effect_brightness", 0f);
        float contrast = preferences.getFloat("effect_contrast", 0f);
        float gamma = preferences.getFloat("effect_gamma", 1.0f);
        sbBrightness.setValue(brightness);
        sbContrast.setValue(contrast);
        sbGamma.setValue(gamma);

        cbEnableFXAA.setChecked(preferences.getBoolean("effect_fxaa", false));
        cbEnableCRTShader.setChecked(preferences.getBoolean("effect_crt", false));
        cbEnableToonShader.setChecked(preferences.getBoolean("effect_toon", false));
        cbEnableNTSCEffect.setChecked(preferences.getBoolean("effect_ntsc", false));
        cbEnableVignetteEffect.setChecked(preferences.getBoolean("effect_vignette", false));
        cbEnableSepiaEffect.setChecked(preferences.getBoolean("effect_sepia", false));
        cbEnableBlurEffect.setChecked(preferences.getBoolean("effect_blur", false));
        cbEnablePixelateEffect.setChecked(preferences.getBoolean("effect_pixelate", false));
        cbEnableGrayscaleEffect.setChecked(preferences.getBoolean("effect_grayscale", false));
        cbEnableSharpenEffect.setChecked(preferences.getBoolean("effect_sharpen", false));
        cbEnableSmoothEffect.setChecked(preferences.getBoolean("effect_smooth", false));
        cbEnableHDREffect.setChecked(preferences.getBoolean("effect_hdr", false));
        cbEnableFSREffect.setChecked(preferences.getBoolean("effect_fsr", false));
        sbFsrSharpness.setValue(preferences.getFloat("effect_fsr_sharpness", 75f));
        sFsrQuality.setSelection(Math.max(0, Math.min(3, (int)preferences.getFloat("effect_fsr_quality", 1f))));
        cbFsrAspectFit.setChecked(preferences.getBoolean("effect_fsr_aspect_fit", false));
    }

    private void saveEffectSettingsToPrefs() {
        preferences.edit()
            .putFloat("effect_brightness", sbBrightness.getValue())
            .putFloat("effect_contrast", sbContrast.getValue())
            .putFloat("effect_gamma", sbGamma.getValue())
            .putBoolean("effect_fxaa", cbEnableFXAA.isChecked())
            .putBoolean("effect_crt", cbEnableCRTShader.isChecked())
            .putBoolean("effect_toon", cbEnableToonShader.isChecked())
            .putBoolean("effect_ntsc", cbEnableNTSCEffect.isChecked())
            .putBoolean("effect_vignette", cbEnableVignetteEffect.isChecked())
            .putBoolean("effect_sepia", cbEnableSepiaEffect.isChecked())
            .putBoolean("effect_blur", cbEnableBlurEffect.isChecked())
            .putBoolean("effect_pixelate", cbEnablePixelateEffect.isChecked())
            .putBoolean("effect_grayscale", cbEnableGrayscaleEffect.isChecked())
            .putBoolean("effect_sharpen", cbEnableSharpenEffect.isChecked())
            .putBoolean("effect_smooth", cbEnableSmoothEffect.isChecked())
            .putBoolean("effect_hdr", cbEnableHDREffect.isChecked())
            .putBoolean("effect_fsr", cbEnableFSREffect.isChecked())
            .putFloat("effect_fsr_sharpness", sbFsrSharpness.getValue())
            .putFloat("effect_fsr_quality", (float) sFsrQuality.getSelectedItemPosition())
            .putBoolean("effect_fsr_aspect_fit", cbFsrAspectFit.isChecked())
            .apply();
    }

    public void applyVulkanEffects(VulkanRenderer renderer) {
        Log.d(TAG, "applyVulkanEffects() called");

        float brightness = sbBrightness.getValue();
        float contrast = sbContrast.getValue();
        float gamma = sbGamma.getValue();
        boolean enableFXAA = cbEnableFXAA.isChecked();
        boolean enableCRT = cbEnableCRTShader.isChecked();
        boolean enableToon = cbEnableToonShader.isChecked();
        boolean enableNTSC = cbEnableNTSCEffect.isChecked();
        boolean enableVignette = cbEnableVignetteEffect.isChecked();
        boolean enableSepia = cbEnableSepiaEffect.isChecked();
        boolean enableBlur = cbEnableBlurEffect.isChecked();
        boolean enablePixelate = cbEnablePixelateEffect.isChecked();
        boolean enableGrayscale = cbEnableGrayscaleEffect.isChecked();
        boolean enableSharpen = cbEnableSharpenEffect.isChecked();
        boolean enableSmooth = cbEnableSmoothEffect.isChecked();
        boolean enableHDR = cbEnableHDREffect.isChecked();
        boolean enableFSR = cbEnableFSREffect.isChecked();

        // Save current settings
        saveEffectSettingsToPrefs();

        // Build effect list for VulkanRenderer
        ArrayList<Integer> types = new ArrayList<>();
        ArrayList<float[]> paramsList = new ArrayList<>();

        // ColorEffect: brightness, contrast, gamma (only if non-default)
        if (brightness != 0 || contrast != 0 || gamma != 1.0f) {
            types.add(VulkanRenderer.EFFECT_COLOR);
            paramsList.add(new float[]{brightness / 100f, contrast / 100f, gamma, 0, 0, 0, 0, 0});
        }

        if (enableHDR) {
            types.add(VulkanRenderer.EFFECT_HDR);
            paramsList.add(new float[]{0.4f, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableFXAA) {
            types.add(VulkanRenderer.EFFECT_FXAA);
            paramsList.add(new float[]{0, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableCRT) {
            types.add(VulkanRenderer.EFFECT_CRT);
            paramsList.add(new float[]{0, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableToon) {
            types.add(VulkanRenderer.EFFECT_TOON);
            paramsList.add(new float[]{0, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableNTSC) {
            types.add(VulkanRenderer.EFFECT_NTSC);
            // params: frameCount, textureSizeX, textureSizeY
            int screenW = renderer.getSurfaceWidth();
            int screenH = renderer.getSurfaceHeight();
            paramsList.add(new float[]{0, (float)screenW, (float)screenH, 0, 0, 0, 0, 0});
        }

        if (enableVignette) {
            types.add(VulkanRenderer.EFFECT_VIGNETTE);
            paramsList.add(new float[]{0.5f, 0.5f, 0, 0, 0, 0, 0, 0});
        }

        if (enableSepia) {
            types.add(VulkanRenderer.EFFECT_SEPIA);
            paramsList.add(new float[]{1.0f, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableBlur) {
            types.add(VulkanRenderer.EFFECT_BLUR);
            paramsList.add(new float[]{2.0f, 5.0f, 0, 0, 0, 0, 0, 0});
        }

        if (enablePixelate) {
            types.add(VulkanRenderer.EFFECT_PIXELATE);
            paramsList.add(new float[]{4.0f, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableGrayscale) {
            types.add(VulkanRenderer.EFFECT_GRAYSCALE);
            paramsList.add(new float[]{1.0f, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableSharpen) {
            types.add(VulkanRenderer.EFFECT_SHARPEN);
            paramsList.add(new float[]{1.0f, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableSmooth) {
            types.add(VulkanRenderer.EFFECT_SMOOTH);
            paramsList.add(new float[]{1.0f, 0, 0, 0, 0, 0, 0, 0});
        }

        if (enableFSR) {
            // FSR EASU + RCAS
            int screenW = renderer.getSurfaceWidth();
            int screenH = renderer.getSurfaceHeight();
            // Quality modes: ultra=77%, quality=67%, balanced=59%, performance=50%
            float[] qualityScales = {0.77f, 0.67f, 0.59f, 0.50f};
            int qIdx = Math.max(0, Math.min(3, sFsrQuality.getSelectedItemPosition()));
            float scale = qualityScales[qIdx];
            float inputW = screenW * scale;
            float inputH = screenH * scale;
            float preserveAspect = cbFsrAspectFit.isChecked() ? 1.0f : 0.0f;

            types.add(VulkanRenderer.EFFECT_FSR1_EASU);
            paramsList.add(new float[]{inputW, inputH, (float)screenW, (float)screenH, preserveAspect, 0, 0, 0});

            types.add(VulkanRenderer.EFFECT_FSR1_RCAS);
            float sharpnessStops = sliderValueToStops(sbFsrSharpness.getValue());
            paramsList.add(new float[]{sharpnessStops, 0, 0, 0, 0, 0, 0, 0});
        }

        if (types.isEmpty()) {
            // РќРµС‚ СЌС„С„РµРєС‚РѕРІ вЂ” РїСЂРѕСЃС‚Рѕ РѕС‡РёС‰Р°РµРј, scanout РІРѕСЃСЃС‚Р°РЅРѕРІРёС‚СЃСЏ СЃР°Рј
            renderer.clearEffects();
            Log.d(TAG, "No effects enabled, cleared all effects.");
        } else {
            // BUG FIX: disableScanoutForEffects() РІС‹Р·С‹РІР°РµРј РўРћР›Р¬РљРћ РєРѕРіРґР° СЂРµР°Р»СЊРЅРѕ
            // РІРєР»СЋС‡Р°РµРј СЌС„С„РµРєС‚С‹, РёРЅР°С‡Рµ РїСЂРё СЃР±СЂРѕСЃРµ РЅР°СЃС‚СЂРѕРµРє scanout РЅРµ РІРѕСЃСЃС‚Р°РЅР°РІР»РёРІР°Р»СЃСЏ.
            renderer.disableScanoutForEffects();
            int[] typeArr = new int[types.size()];
            float[][] paramsArr = new float[types.size()][];
            for (int i = 0; i < types.size(); i++) {
                typeArr[i] = types.get(i);
                paramsArr[i] = paramsList.get(i);
            }
            renderer.setEffects(typeArr, paramsArr);
            Log.d(TAG, "Applied " + types.size() + " effects to VulkanRenderer.");
        }

        saveProfile(sProfile);
        Log.d(TAG, "Profile saved after applying effects.");
    }

    // Backwards-compatible overload - no longer used but kept for compatibility
    public void applyEffects(Object colorEffect, Object renderer, Object fxaaEffect, Object crtEffect, Object toonEffect, Object ntscEffect,
                             Object vignetteEffect, Object sepiaEffect, Object blurEffect, Object pixelateEffect, Object grayscaleEffect, Object sharpenEffect, Object smoothEffect, Object hdrEffect) {
        if (renderer instanceof VulkanRenderer) {
            applyVulkanEffects((VulkanRenderer) renderer);
        }
    }

    public void applyEffects(Object colorEffect, Object renderer, Object fxaaEffect, Object crtEffect, Object toonEffect, Object ntscEffect) {
        applyEffects(colorEffect, renderer, fxaaEffect, crtEffect, toonEffect, ntscEffect,
                null, null, null, null, null, null, null, null);
    }

    /** Slider value 0..100 -> RCAS sharpnessStops 2.0..0.0 (higher slider = sharper). */
    private static float sliderValueToStops(float sliderValue) {
        float v = Math.max(0f, Math.min(100f, sliderValue));
        return 2.0f * (1.0f - v / 100f);
    }

    private static float stopsToSliderValue(float stops) {
        float s = Math.max(0f, Math.min(2f, stops));
        return (1.0f - s / 2.0f) * 100f;
    }

    public void setOnConfirmCallback(Runnable confirmCallback) {
        Log.d(TAG, "Setting OnConfirm callback.");
        this.onConfirmCallback = confirmCallback;
    }

    public int getRefreshRate() {
        int refreshRate = 60;

        try {
            WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                Display display = windowManager.getDefaultDisplay();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    refreshRate = (int) display.getRefreshRate();
                    Log.d(TAG, "Using Android R+ method, refresh rate: " + refreshRate + "Hz");
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Display.Mode mode = display.getMode();
                    refreshRate = (int) mode.getRefreshRate();
                    Log.d(TAG, "Using Android M+ method, refresh rate: " + refreshRate + "Hz");
                } else {
                    DisplayMetrics metrics = new DisplayMetrics();
                    display.getMetrics(metrics);
                    refreshRate = 60;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error detecting refresh rate: " + e.getMessage());
            refreshRate = 60;
        }

        refreshRate = roundToStandardRefreshRate(refreshRate);
        Log.i(TAG, "Final refresh rate: " + refreshRate + "Hz");
        return refreshRate;
    }

    private static int roundToStandardRefreshRate(int rate) {
        int[] standardRates = {30, 45, 48, 50, 60, 72, 75, 90, 96, 100, 120, 144, 165, 240, 360};

        int closest = 60;
        int minDiff = Integer.MAX_VALUE;

        for (int standard : standardRates) {
            int diff = Math.abs(rate - standard);
            if (diff < minDiff) {
                minDiff = diff;
                closest = standard;
            }
        }

        if (rate < 30) {
            return 60;
        }

        return closest;
    }

}
