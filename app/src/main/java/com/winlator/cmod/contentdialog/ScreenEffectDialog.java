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
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.effects.ColorEffect;
import com.winlator.cmod.renderer.effects.CRTEffect;
import com.winlator.cmod.renderer.effects.FSR1EasuEffect;
import com.winlator.cmod.renderer.effects.FSR1RcasEffect;
import com.winlator.cmod.renderer.effects.FXAAEffect;
import com.winlator.cmod.renderer.effects.FrameGenerationEffect;
import com.winlator.cmod.renderer.effects.NTSCCombinedEffect;
import com.winlator.cmod.renderer.effects.ToonEffect;
import com.winlator.cmod.renderer.effects.VignetteEffect;
import com.winlator.cmod.renderer.effects.SepiaEffect;
import com.winlator.cmod.renderer.effects.BlurEffect;
import com.winlator.cmod.renderer.effects.PixelateEffect;
import com.winlator.cmod.renderer.effects.GrayscaleEffect;
import com.winlator.cmod.renderer.effects.SharpenEffect;
import com.winlator.cmod.renderer.effects.SmoothEffect;
import com.winlator.cmod.renderer.effects.HDREffect;
import com.winlator.cmod.widget.FrameGenerationView;
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


        GLRenderer renderer = activity.getXServerView().getRenderer();
        if (renderer == null) {
            Log.e(TAG, "Renderer is null in ScreenEffectDialog initialization!");
            return;
        }

        ColorEffect colorEffect = (ColorEffect) renderer.getEffectComposer().getEffect(ColorEffect.class);
        FXAAEffect fxaaEffect = (FXAAEffect) renderer.getEffectComposer().getEffect(FXAAEffect.class);
        CRTEffect crtEffect = (CRTEffect) renderer.getEffectComposer().getEffect(CRTEffect.class);
        ToonEffect toonEffect = (ToonEffect) renderer.getEffectComposer().getEffect(ToonEffect.class);
        NTSCCombinedEffect ntscEffect = (NTSCCombinedEffect) renderer.getEffectComposer().getEffect(NTSCCombinedEffect.class);
        VignetteEffect vignetteEffect = (VignetteEffect) renderer.getEffectComposer().getEffect(VignetteEffect.class);
        SepiaEffect sepiaEffect = (SepiaEffect) renderer.getEffectComposer().getEffect(SepiaEffect.class);
        BlurEffect blurEffect = (BlurEffect) renderer.getEffectComposer().getEffect(BlurEffect.class);
        PixelateEffect pixelateEffect = (PixelateEffect) renderer.getEffectComposer().getEffect(PixelateEffect.class);
        GrayscaleEffect grayscaleEffect = (GrayscaleEffect) renderer.getEffectComposer().getEffect(GrayscaleEffect.class);
        SharpenEffect sharpenEffect = (SharpenEffect) renderer.getEffectComposer().getEffect(SharpenEffect.class);
        SmoothEffect smoothEffect = (SmoothEffect) renderer.getEffectComposer().getEffect(SmoothEffect.class);
        HDREffect hdrEffect = (HDREffect) renderer.getEffectComposer().getEffect(HDREffect.class);
        FSR1EasuEffect fsrEasuEffect = (FSR1EasuEffect) renderer.getEffectComposer().getEffect(FSR1EasuEffect.class);
        FSR1RcasEffect fsrRcasEffect = (FSR1RcasEffect) renderer.getEffectComposer().getEffect(FSR1RcasEffect.class);

        Log.d(TAG, "ScreenEffectDialog initialized");

        if (colorEffect != null) {
            Log.d(TAG, "ColorEffect found");
            sbBrightness.setValue(colorEffect.getBrightness() * 100);
            sbContrast.setValue(colorEffect.getContrast() * 100);
            sbGamma.setValue(colorEffect.getGamma());
        } else {
            Log.d(TAG, "ColorEffect not found, resetting settings");
            resetSettings();
        }

        cbEnableFXAA.setChecked(fxaaEffect != null);
        cbEnableCRTShader.setChecked(crtEffect != null);
        cbEnableToonShader.setChecked(toonEffect != null);
        cbEnableNTSCEffect.setChecked(ntscEffect != null);
        cbEnableVignetteEffect.setChecked(vignetteEffect != null);
        cbEnableSepiaEffect.setChecked(sepiaEffect != null);
        cbEnableBlurEffect.setChecked(blurEffect != null);
        cbEnablePixelateEffect.setChecked(pixelateEffect != null);
        cbEnableGrayscaleEffect.setChecked(grayscaleEffect != null);
        cbEnableSharpenEffect.setChecked(sharpenEffect != null);
        cbEnableSmoothEffect.setChecked(smoothEffect != null);
        cbEnableHDREffect.setChecked(hdrEffect != null);
        cbEnableFSREffect.setChecked(fsrEasuEffect != null || fsrRcasEffect != null);
        if (fsrRcasEffect != null) {
            sbFsrSharpness.setValue(stopsToSliderValue(fsrRcasEffect.getSharpnessStops()));
        } else {
            sbFsrSharpness.setValue(75f);
        }
        if (fsrEasuEffect != null) {
            sFsrQuality.setSelection(fsrEasuEffect.getQuality().ordinal());
            cbFsrAspectFit.setChecked(fsrEasuEffect.isPreserveAspect());
        }

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

            // Directly calling applyEffects to ensure it's triggered
            Log.d(TAG, "Calling applyEffects() directly.");
            applyEffects(colorEffect, renderer, fxaaEffect, crtEffect, toonEffect, ntscEffect, vignetteEffect, sepiaEffect, blurEffect, pixelateEffect, grayscaleEffect, sharpenEffect, smoothEffect, hdrEffect);

            Log.d(TAG, "Effects applied. Dismissing dialog.");
            dismiss(); // Close the dialog
            Log.d(TAG, "Dialog dismissed.");
        });

        findViewById(R.id.ButtonAddGenerationView).setOnClickListener(v -> addFrameGenerationView());



        findViewById(R.id.BTAddProfile).setOnClickListener(v -> promptAddProfile());
        findViewById(R.id.BTRemoveProfile).setOnClickListener(v -> promptDeleteProfile());

        setOnConfirmCallback(() -> {
            Log.d(TAG, "OnConfirm callback triggered. Applying effects.");
            applyEffects(colorEffect, renderer, fxaaEffect, crtEffect, toonEffect, ntscEffect, vignetteEffect, sepiaEffect, blurEffect, pixelateEffect, grayscaleEffect, sharpenEffect, smoothEffect, hdrEffect);
            Log.d(TAG, "Effects applied from callback.");

            // Optionally dismiss after applying effects in callback
            dismiss();
            Log.d(TAG, "Dialog dismissed after callback.");
        });

    }

    private void addFrameGenerationView() {
        if (activity.frameGenerationView == null) {
            GLRenderer currentRenderer = activity.getXServerView().getRenderer();

            final FrameLayout container = activity.findViewById(R.id.FLXServerDisplay);
            activity.frameGenerationView = new FrameGenerationView(activity, currentRenderer);
            activity.frameGenerationView.setFrameGenerationCallback((value) -> {
                FrameGenerationEffect frameGenerationEffect =
                        (FrameGenerationEffect) currentRenderer.getEffectComposer().getEffect(FrameGenerationEffect.class);
                applyFrameGenerationEffect(currentRenderer, frameGenerationEffect, value);
            });

            activity.frameGenerationView.setHideButtonCallback(() -> activity.frameGenerationView.setVisibility(View.GONE));
            container.addView(activity.frameGenerationView);
        } else {
            activity.frameGenerationView.setVisibility(View.VISIBLE);
        }
        dismiss();
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

    public void applyEffects(ColorEffect colorEffect, GLRenderer renderer, FXAAEffect fxaaEffect, CRTEffect crtEffect, ToonEffect toonEffect, NTSCCombinedEffect ntscEffect,
                             VignetteEffect vignetteEffect, SepiaEffect sepiaEffect, BlurEffect blurEffect, PixelateEffect pixelateEffect, GrayscaleEffect grayscaleEffect, SharpenEffect sharpenEffect, SmoothEffect smoothEffect, HDREffect hdrEffect) {
        Log.d(TAG, "applyEffects() called");

        float brightness = sbBrightness.getValue();
        float contrast = sbContrast.getValue();
        float gamma = sbGamma.getValue();
        boolean enableFXAA = cbEnableFXAA.isChecked();
        boolean enableCRTShader = cbEnableCRTShader.isChecked();
        boolean enableToonShader = cbEnableToonShader.isChecked();
        boolean enableNTSCEffect = cbEnableNTSCEffect.isChecked();
        boolean enableVignetteEffect = cbEnableVignetteEffect.isChecked();
        boolean enableSepiaEffect = cbEnableSepiaEffect.isChecked();
        boolean enableBlurEffect = cbEnableBlurEffect.isChecked();
        boolean enablePixelateEffect = cbEnablePixelateEffect.isChecked();
        boolean enableGrayscaleEffect = cbEnableGrayscaleEffect.isChecked();
        boolean enableSharpenEffect = cbEnableSharpenEffect.isChecked();
        boolean enableSmoothEffect = cbEnableSmoothEffect.isChecked();
        boolean enableHDREffect = cbEnableHDREffect.isChecked();

        Log.d(TAG, "Settings - Brightness: " + brightness + ", Contrast: " + contrast + ", Gamma: " + gamma);
        Log.d(TAG, "FXAA Enabled: " + enableFXAA + ", CRT Shader Enabled: " + enableCRTShader + ", HDR Enabled: " + enableHDREffect);

        // Check ColorEffect state
        if (colorEffect == null) {
            Log.d(TAG, "ColorEffect is null, creating new instance.");
            colorEffect = new ColorEffect();
        }

        // Check if renderer and effect composer are non-null
        if (renderer == null) {
            Log.e(TAG, "Renderer is null!");
            return;
        }

        if (renderer.getEffectComposer() == null) {
            Log.e(TAG, "EffectComposer is null!");
            return;
        }

        // Apply or remove ColorEffect
        if (brightness == 0 && contrast == 0 && gamma == 1.0f) {
            Log.d(TAG, "No adjustments are applied. Removing ColorEffect if it exists.");
            renderer.getEffectComposer().removeEffect(colorEffect);
        } else {
            Log.d(TAG, "Applying ColorEffect adjustments.");
            colorEffect.setBrightness(brightness / 100f);
            colorEffect.setContrast(contrast / 100f);
            colorEffect.setGamma(gamma);
            renderer.getEffectComposer().addEffect(colorEffect);
            Log.d(TAG, "ColorEffect added/updated.");
        }
        
        // Apply or remove HDREffect (независимый эффект с контрастом 40)
        if (enableHDREffect) {
            if (hdrEffect == null) {
                Log.d(TAG, "HDREffect is null, creating and adding new instance with contrast 0.4f (40).");
                hdrEffect = new HDREffect();
                hdrEffect.setContrast(0.4f); // Контраст 40 для HDR эффекта
                renderer.getEffectComposer().addEffect(hdrEffect);
            } else {
                Log.d(TAG, "HDREffect is already added.");
            }
        } else if (hdrEffect != null) {
            Log.d(TAG, "HDR Effect is disabled. Removing HDREffect.");
            renderer.getEffectComposer().removeEffect(hdrEffect);
        }

        // Apply or remove FXAAEffect
        if (enableFXAA) {
            if (fxaaEffect == null) {
                Log.d(TAG, "FXAAEffect is null, creating and adding new instance.");
                fxaaEffect = new FXAAEffect();
                renderer.getEffectComposer().addEffect(fxaaEffect);
            } else {
                Log.d(TAG, "FXAAEffect is already added.");
            }
        } else if (fxaaEffect != null) {
            Log.d(TAG, "FXAA is disabled. Removing FXAAEffect.");
            renderer.getEffectComposer().removeEffect(fxaaEffect);
        }

        // Apply or remove CRTEffect
        if (enableCRTShader) {
            if (crtEffect == null) {
                Log.d(TAG, "CRTEffect is null, creating and adding new instance.");
                crtEffect = new CRTEffect();
                renderer.getEffectComposer().addEffect(crtEffect);
            } else {
                Log.d(TAG, "CRTEffect is already added.");
            }
        } else if (crtEffect != null) {
            Log.d(TAG, "CRT Shader is disabled. Removing CRTEffect.");
            renderer.getEffectComposer().removeEffect(crtEffect);
        }


        // Apply or remove ToonEffect
        if (enableToonShader) {
            if (toonEffect == null) {
                Log.d(TAG, "ToonEffect is null, creating and adding new instance.");
                toonEffect = new ToonEffect();
                renderer.getEffectComposer().addEffect(toonEffect);
            } else {
                Log.d(TAG, "ToonEffect is already added.");
            }
        } else if (toonEffect != null) {
            Log.d(TAG, "Toon Shader is disabled. Removing ToonEffect.");
            renderer.getEffectComposer().removeEffect(toonEffect);
        }


        // Apply or remove NTSCCombinedEffect
        if (enableNTSCEffect) {
            if (ntscEffect == null) {
                Log.d(TAG, "NTSCCombinedEffect is null, creating and adding new instance.");
                ntscEffect = new NTSCCombinedEffect();
                renderer.getEffectComposer().addEffect(ntscEffect);
            } else {
                Log.d(TAG, "NTSCCombinedEffect is already added.");
            }
        } else if (ntscEffect != null) {
            Log.d(TAG, "NTSC Effect is disabled. Removing NTSCCombinedEffect.");
            renderer.getEffectComposer().removeEffect(ntscEffect);
        }

        // Apply or remove VignetteEffect
        if (enableVignetteEffect) {
            if (vignetteEffect == null) {
                Log.d(TAG, "VignetteEffect is null, creating and adding new instance.");
                vignetteEffect = new VignetteEffect();
                renderer.getEffectComposer().addEffect(vignetteEffect);
            } else {
                Log.d(TAG, "VignetteEffect is already added.");
            }
        } else if (vignetteEffect != null) {
            Log.d(TAG, "Vignette Effect is disabled. Removing VignetteEffect.");
            renderer.getEffectComposer().removeEffect(vignetteEffect);
        }

        // Apply or remove SepiaEffect
        if (enableSepiaEffect) {
            if (sepiaEffect == null) {
                Log.d(TAG, "SepiaEffect is null, creating and adding new instance.");
                sepiaEffect = new SepiaEffect();
                renderer.getEffectComposer().addEffect(sepiaEffect);
            } else {
                Log.d(TAG, "SepiaEffect is already added.");
            }
        } else if (sepiaEffect != null) {
            Log.d(TAG, "Sepia Effect is disabled. Removing SepiaEffect.");
            renderer.getEffectComposer().removeEffect(sepiaEffect);
        }

        // Apply or remove BlurEffect
        if (enableBlurEffect) {
            if (blurEffect == null) {
                Log.d(TAG, "BlurEffect is null, creating and adding new instance.");
                blurEffect = new BlurEffect();
                renderer.getEffectComposer().addEffect(blurEffect);
            } else {
                Log.d(TAG, "BlurEffect is already added.");
            }
        } else if (blurEffect != null) {
            Log.d(TAG, "Blur Effect is disabled. Removing BlurEffect.");
            renderer.getEffectComposer().removeEffect(blurEffect);
        }

        // Apply or remove PixelateEffect
        if (enablePixelateEffect) {
            if (pixelateEffect == null) {
                Log.d(TAG, "PixelateEffect is null, creating and adding new instance.");
                pixelateEffect = new PixelateEffect();
                renderer.getEffectComposer().addEffect(pixelateEffect);
            } else {
                Log.d(TAG, "PixelateEffect is already added.");
            }
        } else if (pixelateEffect != null) {
            Log.d(TAG, "Pixelate Effect is disabled. Removing PixelateEffect.");
            renderer.getEffectComposer().removeEffect(pixelateEffect);
        }

        // Apply or remove GrayscaleEffect
        if (enableGrayscaleEffect) {
            if (grayscaleEffect == null) {
                Log.d(TAG, "GrayscaleEffect is null, creating and adding new instance.");
                grayscaleEffect = new GrayscaleEffect();
                renderer.getEffectComposer().addEffect(grayscaleEffect);
            } else {
                Log.d(TAG, "GrayscaleEffect is already added.");
            }
        } else if (grayscaleEffect != null) {
            Log.d(TAG, "Grayscale Effect is disabled. Removing GrayscaleEffect.");
            renderer.getEffectComposer().removeEffect(grayscaleEffect);
        }

        // Apply or remove SharpenEffect
        if (enableSharpenEffect) {
            if (sharpenEffect == null) {
                Log.d(TAG, "SharpenEffect is null, creating and adding new instance.");
                sharpenEffect = new SharpenEffect();
                renderer.getEffectComposer().addEffect(sharpenEffect);
            } else {
                Log.d(TAG, "SharpenEffect is already added.");
            }
        } else if (sharpenEffect != null) {
            Log.d(TAG, "Sharpen Effect is disabled. Removing SharpenEffect.");
            renderer.getEffectComposer().removeEffect(sharpenEffect);
        }

        // Apply or remove FSR (EASU + RCAS)
        boolean enableFSREffect = cbEnableFSREffect.isChecked();
        FSR1EasuEffect existingEasu = (FSR1EasuEffect) renderer.getEffectComposer().getEffect(FSR1EasuEffect.class);
        FSR1RcasEffect existingRcas = (FSR1RcasEffect) renderer.getEffectComposer().getEffect(FSR1RcasEffect.class);
        if (enableFSREffect) {
            if (existingEasu == null) {
                existingEasu = new FSR1EasuEffect();
                renderer.getEffectComposer().addEffect(existingEasu);
            }
            if (existingRcas == null) {
                existingRcas = new FSR1RcasEffect();
                renderer.getEffectComposer().addEffect(existingRcas);
            }
            int qIdx = Math.max(0, Math.min(3, sFsrQuality.getSelectedItemPosition()));
            existingEasu.setQuality(FSR1EasuEffect.Quality.values()[qIdx]);
            existingEasu.setPreserveAspect(cbFsrAspectFit.isChecked());
            existingRcas.setSharpnessStops(sliderValueToStops(sbFsrSharpness.getValue()));
        } else {
            if (existingEasu != null) renderer.getEffectComposer().removeEffect(existingEasu);
            if (existingRcas != null) renderer.getEffectComposer().removeEffect(existingRcas);
        }

        // Apply or remove SmoothEffect
        if (enableSmoothEffect) {
            if (smoothEffect == null) {
                Log.d(TAG, "SmoothEffect is null, creating and adding new instance.");
                smoothEffect = new SmoothEffect();
                renderer.getEffectComposer().addEffect(smoothEffect);
            } else {
                Log.d(TAG, "SmoothEffect is already added.");
            }
        } else if (smoothEffect != null) {
            Log.d(TAG, "Smooth Effect is disabled. Removing SmoothEffect.");
            renderer.getEffectComposer().removeEffect(smoothEffect);
        }

        saveProfile(sProfile);
        Log.d(TAG, "Profile saved after applying effects.");
    }

    // Backwards-compatible overload used by older call sites
    public void applyEffects(ColorEffect colorEffect, GLRenderer renderer, FXAAEffect fxaaEffect, CRTEffect crtEffect, ToonEffect toonEffect, NTSCCombinedEffect ntscEffect) {
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

    public void applyFrameGenerationEffect(GLRenderer renderer, FrameGenerationEffect frameGenerationEffect, Boolean enableFrameGenerationEffect) {
        Log.d(TAG, "applyFrameGenerationEffect(): enableFrameGenerationEffect = " + enableFrameGenerationEffect);

        if (renderer == null) {
            Log.e(TAG, "Renderer is null!");
            return;
        }

        if (renderer.getEffectComposer() == null) {
            Log.e(TAG, "EffectComposer is null!");
            return;
        }

        if (enableFrameGenerationEffect) {
            if (frameGenerationEffect == null) {
                Log.d(TAG, "FrameGenerationEffect is null, creating and adding new instance.");
                frameGenerationEffect = new FrameGenerationEffect();
                renderer.getEffectComposer().addEffect(frameGenerationEffect);
                frameGenerationEffect.toggleGeneration();
                frameGenerationEffect.setDisplayRefreshRate(getRefreshRate());
            } else {
                Log.d(TAG, "FrameGenerationEffect is already added.");
            }
        } else if (frameGenerationEffect != null) {
            Log.d(TAG, "FrameGenerationEffect is disabled. Removing FrameGenerationEffect.");
            frameGenerationEffect.toggleGeneration();
            renderer.getEffectComposer().removeEffect(frameGenerationEffect);
        } else {
            Log.d(TAG, "FrameGenerationEffect failed to disable.");
        }
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
