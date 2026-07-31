package com.winlator.cmod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;
import com.winlator.cmod.R;
import com.winlator.cmod.box86_64.Box86_64EditPresetDialog;
import com.winlator.cmod.box86_64.Box86_64Preset;
import com.winlator.cmod.box86_64.Box86_64PresetManager;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.GPUPerformanceDialog;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.ArrayUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUPerformanceManager;
import com.winlator.cmod.core.LsfgVkManager;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineUtils;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.inputcontrols.ExternalController;
import com.winlator.cmod.inputcontrols.PreferenceKeys;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.restore.RestoreActivity;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xenvironment.ImageFsInstaller;

import org.json.JSONArray;
import org.json.JSONException;
import org.tukaani.xz.check.Check;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsFragment extends Fragment {
    public static final String DEFAULT_WINE_DEBUG_CHANNELS = "warn,err,fixme";
    private Callback<Uri> installSoundFontCallback;
    private PreloaderDialog preloaderDialog;
    private static String defaultExportPath() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        String downloadPath = downloads != null ? downloads.getPath() : "/storage/emulated/0/Download";
        return downloadPath + "/Winlator/Frontend";
    }
    public static final String DEFAULT_EXPORT_PATH = defaultExportPath();
    private SharedPreferences preferences;

	// Disable or enable True Mouse Control
	private CheckBox cbCursorLock;
    // Disable or enable Xinput Processing
    private CheckBox cbXinputToggle;

    private CheckBox cbGyroEnabled;
    private SeekBar sbGyroXSensitivity;
    private SeekBar sbGyroYSensitivity;
    private SeekBar sbGyroSmoothing;
    private SeekBar sbGyroDeadzone;
    private CheckBox cbInvertGyroX;
    private CheckBox cbInvertGyroY;

    private boolean isRestoreAction = false;

    private boolean enableLegacyInputMode = false;


    private CheckBox cbDarkMode;
    boolean isDarkMode;

    private static final int REQUEST_CODE_FRONTEND_EXPORT_PATH = 1002;
    private static final int REQUEST_CODE_INSTALL_SOUNDFONT = 1001;
    private static final int REQUEST_CODE_IMPORT_GLOBAL_LOSSLESS_DLL = 1003;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Apply dynamic styles to all labels
        applyDynamicStylesRecursively(view);

        Button btnConfigureGyro = view.findViewById(R.id.BTConfigureGyro);
        btnConfigureGyro.setOnClickListener(v -> showGyroConfigDialog());

        // Initialize GPU Performance button
        Button btnGPUPerformance = view.findViewById(R.id.BTGPUPerformance);
        TextView tvGPUInfoDisplay = view.findViewById(R.id.TVGPUInfoDisplay);
        
        btnGPUPerformance.setOnClickListener(v -> {
            GPUPerformanceDialog dialog = new GPUPerformanceDialog(getContext());
            dialog.show();
        });
        
        // Initialize Adreno Turbo Mode checkbox
        CheckBox cbAdrenoTurboMode = view.findViewById(R.id.CBAdrenoTurboMode);
        boolean isAdrenoGPU = com.winlator.cmod.core.GPUInformation.isAdrenoGPU();
        
        if (isAdrenoGPU) {
            boolean turboEnabled = preferences.getBoolean("adreno_turbo_mode", false);
            cbAdrenoTurboMode.setChecked(turboEnabled);
            
            // Apply saved state
            if (turboEnabled) {
                com.winlator.cmod.core.GPUInformation.setTurboMode(true);
            }
            
            cbAdrenoTurboMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Apply turbo mode
                boolean success = com.winlator.cmod.core.GPUInformation.setTurboMode(isChecked);
                
                if (success) {
                    // Save preference only if successful
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean("adreno_turbo_mode", isChecked);
                    editor.apply();
                    
                    String message = isChecked ? 
                        getString(R.string.adreno_turbo_enabled) : 
                        getString(R.string.adreno_turbo_disabled);
                    android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    // Revert checkbox if failed
                    cbAdrenoTurboMode.setChecked(!isChecked);
                    
                    // Show root warning only once
                    boolean hasShownRootWarning = preferences.getBoolean("adreno_turbo_root_warning_shown", false);
                    
                    if (!hasShownRootWarning) {
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
                        builder.setTitle("Р СћРЎР‚Р ВµР В±РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ root Р Т‘Р С•РЎРѓРЎвЂљРЎС“Р С—");
                        builder.setMessage(getString(R.string.adreno_turbo_no_root) + 
                                         "\n\nР вЂќР В»РЎРЏ РЎР‚Р В°Р В±Р С•РЎвЂљРЎвЂ№ РЎРЊРЎвЂљР С•Р в„– РЎвЂћРЎС“Р Р…Р С”РЎвЂ Р С‘Р С‘ Р Р…Р ВµР С•Р В±РЎвЂ¦Р С•Р Т‘Р С‘Р С root Р Т‘Р С•РЎРѓРЎвЂљРЎС“Р С— Р Р…Р В° РЎС“РЎРѓРЎвЂљРЎР‚Р С•Р в„–РЎРѓРЎвЂљР Р†Р Вµ.");
                        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            // Mark as shown
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.putBoolean("adreno_turbo_root_warning_shown", true);
                            editor.apply();
                        });
                        builder.show();
                    } else {
                        // Just show a simple toast after first warning
                        android.widget.Toast.makeText(getContext(), 
                            "Turbo Mode Р Р…Р ВµР Т‘Р С•РЎРѓРЎвЂљРЎС“Р С—Р ВµР Р… (Р Р…Р ВµРЎвЂљ root)", 
                            android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } else {
            // Hide checkbox if not Adreno GPU
            cbAdrenoTurboMode.setVisibility(View.GONE);
        }
        
        // Update GPU info display
        updateGPUInfoDisplay(tvGPUInfoDisplay);

        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.settings);
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.settings_fragment, container, false);
        final Context context = getContext();
        preferences = new com.winlator.cmod.core.MmkvPreferences();

        // Check for Dark Mode preference
        isDarkMode = preferences.getBoolean("dark_mode", false);
        // Apply dynamic styles
        applyDynamicStyles(view, isDarkMode);

        // Initialize the Dark Mode checkbox
        cbDarkMode = view.findViewById(R.id.CBDarkMode);
        cbDarkMode.setChecked(preferences.getBoolean("dark_mode", false));

        cbDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save dark mode preference
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean("dark_mode", isChecked);
            editor.apply();

            // Update the UI or activity theme if necessary
            updateTheme(isChecked);
        });

        // Initialize App Language Spinner
        Spinner spAppLanguage = view.findViewById(R.id.SPAppLanguage);
        String[] languageValues = getResources().getStringArray(R.array.app_languages_values);
        String savedLanguage = preferences.getString("app_language", "system");
        
        int languageIndex = 0;
        for (int i = 0; i < languageValues.length; i++) {
            if (languageValues[i].equals(savedLanguage)) {
                languageIndex = i;
                break;
            }
        }
        spAppLanguage.setSelection(languageIndex);
        
        spAppLanguage.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String selectedLanguage = languageValues[position];
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("app_language", selectedLanguage);
                editor.apply();
                applyLanguage(selectedLanguage);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        // Initialize Transition Animation Spinner
        Spinner spTransitionAnimation = view.findViewById(R.id.SPTransitionAnimation);
        String[] animationValues = getResources().getStringArray(R.array.transition_animations_values);
        String savedAnimation = preferences.getString("transition_animation", "none");
        
        int animationIndex = 0;
        for (int i = 0; i < animationValues.length; i++) {
            if (animationValues[i].equals(savedAnimation)) {
                animationIndex = i;
                break;
            }
        }
        spTransitionAnimation.setSelection(animationIndex);
        
        spTransitionAnimation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                String selectedAnimation = animationValues[position];
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString("transition_animation", selectedAnimation);
                editor.apply();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        // Initialize the cursor lock checkbox
        cbCursorLock = view.findViewById(R.id.CBCursorLock);
        cbCursorLock.setChecked(preferences.getBoolean("cursor_lock", true));

        // Initialize the xinput toggle checkbox
        cbXinputToggle = view.findViewById(R.id.CBXinputToggle);
        cbXinputToggle.setChecked(preferences.getBoolean("xinput_toggle", false));

        // Inside onCreateView in SettingsFragment.java
        CheckBox cbLegacyInputMode = view.findViewById(R.id.CBLegacyInputMode);
        enableLegacyInputMode = preferences.getBoolean("legacy_mode_enabled", false);
        cbLegacyInputMode.setChecked(enableLegacyInputMode);

        // Listen to changes and update the temporary state
        cbLegacyInputMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            enableLegacyInputMode = isChecked;
            preferences.edit().putBoolean("legacy_mode_enabled", isChecked).apply();
        });


        // Initialize gyro enable checkbox
        cbGyroEnabled = view.findViewById(R.id.CBGyroEnabled);
        cbGyroEnabled.setChecked(preferences.getBoolean("gyro_enabled", false));

//        CheckBox cbProcessGyroWithLeftTrigger = view.findViewById(R.id.CBProcessGyroWithLeftTrigger);
//        cbProcessGyroWithLeftTrigger.setChecked(preferences.getBoolean("process_gyro_with_left_trigger", false));

        Spinner sbGyroTriggerButton = view.findViewById(R.id.SBGyroTriggerButton);
        RadioGroup rgGyroMode = view.findViewById(R.id.RGyroMode);


        int selectedMode = preferences.getInt("gyro_mode", 0); // 0 for hold, 1 for toggle

        TypedArray keycodeArray = getResources().obtainTypedArray(R.array.button_keycodes);
        int[] keycodes = new int[keycodeArray.length()];

// Log the keycodes for debugging purposes
        Log.d("SettingsFragment", "Populating keycodes array:");

        for (int i = 0; i < keycodeArray.length(); i++) {
            keycodes[i] = keycodeArray.getResourceId(i, -1); // Get the resource ID
            if (keycodes[i] != -1) {
                keycodes[i] = getResources().getInteger(keycodes[i]); // Fetch the actual integer value
                Log.d("SettingsFragment", "Keycode[" + i + "] = " + keycodes[i]); // Log the populated keycode
            } else {
                Log.e("SettingsFragment", "Invalid keycode resource at index " + i);
            }
        }
        keycodeArray.recycle(); // Always recycle TypedArray to free up resources

// Now get the currently selected button from SharedPreferences
        int selectedButton = preferences.getInt("gyro_trigger_button", KeyEvent.KEYCODE_BUTTON_L1);
        Log.d("SettingsFragment", "Selected button keycode: " + selectedButton);

// Find the index of the selectedButton in the keycodes array
        int selectedIndex = -1;
        for (int i = 0; i < keycodes.length; i++) {
            if (keycodes[i] == selectedButton) {
                selectedIndex = i;
                break;
            }
        }

// Ensure a valid index is found, otherwise, handle fallback
        if (selectedIndex != -1) {
            Log.d("SettingsFragment", "Selected button found at index: " + selectedIndex);
            sbGyroTriggerButton.setSelection(selectedIndex);
        } else {
            Log.e("SettingsFragment", "Selected button not found in keycodes array!");
            // If needed, handle the case where the button is not found (you can choose to throw an exception or show an error)
        }


        rgGyroMode.check(selectedMode == 0 ? R.id.RBHoldMode : R.id.RBToggleMode);

        // Initialize the "Configure Analog Sticks" button
        Button btConfigureAnalogSticks = view.findViewById(R.id.BTConfigureAnalogSticks);
        btConfigureAnalogSticks.setOnClickListener(v -> showAnalogStickConfigDialog());

        // Configure Frontend Export Path button

        Button btnChooseFrontendExportPath = view.findViewById(R.id.BTChooseFrontendExportPath);
        TextView tvFrontendExportPath = view.findViewById(R.id.TVFrontendExportPath);

        // Get the saved export path from SharedPreferences or use the default
        String savedUriString = preferences.getString("frontend_export_uri", null);
        if (savedUriString == null) {
            // No saved path, set default path
            tvFrontendExportPath.setText(DEFAULT_EXPORT_PATH);
        } else {
            // Parse and display the saved URI path
            Uri savedUri = Uri.parse(savedUriString);
            String displayPath = FileUtils.getFilePathFromUri(getContext(), savedUri);
            tvFrontendExportPath.setText(displayPath != null ? displayPath : savedUriString);
        }

        // Set the click listener for the "Choose Frontend Export Path" button
        btnChooseFrontendExportPath.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); // Launch File Picker for directory selection
            startActivityForResult(intent, REQUEST_CODE_FRONTEND_EXPORT_PATH);
        });

        setupGlobalLosslessDllImport(view);

        final Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);

        sMIDISoundFont.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        final View btInstallSF = view.findViewById(R.id.BTInstallSF);
        final View btRemoveSF = view.findViewById(R.id.BTRemoveSF);

        MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
        btInstallSF.setOnClickListener(v -> {
            installSoundFontCallback = uri -> {
                PreloaderDialog dialog = new PreloaderDialog(requireActivity());
                dialog.showOnUiThread(R.string.installing_content);
                MidiManager.installSF2File(context, uri, new MidiManager.OnSoundFontInstalledCallback() {
                    @Override
                    public void onSuccess() {
                        dialog.closeOnUiThread();
                        requireActivity().runOnUiThread(() -> {
                            ContentDialog.alert(context, R.string.sound_font_installed_success, null);
                            MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
                        });
                    }

                    @Override
                    public void onFailed(int reason) {
                        dialog.closeOnUiThread();
                        int resId = switch (reason) {
                            case MidiManager.ERROR_BADFORMAT -> R.string.sound_font_bad_format;
                            case MidiManager.ERROR_EXIST -> R.string.sound_font_already_exist;
                            default -> R.string.sound_font_installed_failed;
                        };
                        requireActivity().runOnUiThread(() -> ContentDialog.alert(context, resId, null));
                    }
                });
            };

            // Open the file picker with the request code for SoundFont installation
            openFile(REQUEST_CODE_INSTALL_SOUNDFONT);
        });

        btRemoveSF.setOnClickListener(v -> {
            if (sMIDISoundFont.getSelectedItemPosition() != 0) {
                ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_sound_font, () -> {
                    if (MidiManager.removeSF2File(context, sMIDISoundFont.getSelectedItem().toString())) {
                        AppUtils.showToast(context, R.string.sound_font_removed_success);
                        MidiManager.loadSFSpinnerWithoutDisabled(sMIDISoundFont);
                    } else
                        AppUtils.showToast(context, R.string.sound_font_removed_failed);
                });
            } else
                AppUtils.showToast(context, R.string.cannot_remove_default_sound_font);
        });

        final CheckBox cbUseDRI3 = view.findViewById(R.id.CBUseDRI3);
        cbUseDRI3.setChecked(preferences.getBoolean("use_dri3", true));

        final CheckBox cbUseXR = view.findViewById(R.id.CBUseXR);
        cbUseXR.setChecked(preferences.getBoolean("use_xr", true));
        if (!XrActivity.isSupported()) {
            cbUseXR.setVisibility(View.GONE);
        }

        final CheckBox cbEnableWineDebug = view.findViewById(R.id.CBEnableWineDebug);
        cbEnableWineDebug.setChecked(preferences.getBoolean("enable_wine_debug", false));

        final ArrayList<String> wineDebugChannels = new ArrayList<>(Arrays.asList(preferences.getString("wine_debug_channels", DEFAULT_WINE_DEBUG_CHANNELS).split(",")));
        loadWineDebugChannels(view, wineDebugChannels);

        final CheckBox cbEnableBox86_64Logs = view.findViewById(R.id.CBEnableBox86_64Logs);
        cbEnableBox86_64Logs.setChecked(preferences.getBoolean("enable_box86_64_logs", false));

        final TextView tvCursorSpeed = view.findViewById(R.id.TVCursorSpeed);
        final SeekBar sbCursorSpeed = view.findViewById(R.id.SBCursorSpeed);
        sbCursorSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCursorSpeed.setText(progress+"%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbCursorSpeed.setProgress((int)(preferences.getFloat("cursor_speed", 1.0f) * 100));

        final RadioGroup rgTriggerType = view.findViewById(R.id.RGTriggerType);
        final View btHelpTriggerMode = view.findViewById(R.id.BTHelpTriggerMode);
        List<Integer> triggerRbIds = List.of(R.id.RBTriggerIsButton, R.id.RBTriggerIsAxis, R.id.RBTriggerIsMixed);
        int triggerType = preferences.getInt("trigger_type", ExternalController.TRIGGER_IS_AXIS);

        if (triggerType >= 0 && triggerType < triggerRbIds.size()) {
            ((RadioButton) (rgTriggerType.findViewById(triggerRbIds.get(triggerType)))).setChecked(true);
        }
        btHelpTriggerMode.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_trigger_mode));

        final CheckBox cbEnableFileProvider = view.findViewById(R.id.CBEnableFileProvider);
        final View btHelpFileProvider = view.findViewById(R.id.BTHelpFileProvider);

        cbEnableFileProvider.setChecked(preferences.getBoolean("enable_file_provider", true));
        cbEnableFileProvider.setOnClickListener(v -> AppUtils.showToast(context, R.string.take_effect_next_startup));
        btHelpFileProvider.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_file_provider));

        final CheckBox cbOpenInBrowser = view.findViewById(R.id.CBOpenWithAndroidBrowser);

final CheckBox cbShareClipboard = view.findViewById(R.id.CBShareAndroidClipboard);
cbShareClipboard.setChecked(preferences.getBoolean("share_android_clipboard", false));

view.findViewById(R.id.BTReInstallImagefs).setOnClickListener(v -> {
    ContentDialog.confirm(context, R.string.do_you_want_to_reinstall_imagefs, () -> ImageFsInstaller.installFromAssets((MainActivity) getActivity()));
});

// Add backup button
Button btnBackupData = view.findViewById(R.id.BTBackupData);
btnBackupData.setOnClickListener(v -> {
    showBackupConfirmationDialog();
});

// Add restore button
Button btnRestoreData = view.findViewById(R.id.BTRestoreData);
btnRestoreData.setOnClickListener(v -> {
    selectBackupFileForRestore();
});

int finalSelectedIndex = selectedIndex;
view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
    SharedPreferences.Editor editor = preferences.edit();

    // Save Dark Mode setting
    editor.putBoolean("dark_mode", cbDarkMode.isChecked());
    editor.putBoolean("use_dri3", cbUseDRI3.isChecked());
    editor.putBoolean("use_xr", cbUseXR.isChecked());
    editor.putFloat("cursor_speed", sbCursorSpeed.getProgress() / 100.0f);
    editor.putBoolean("enable_wine_debug", cbEnableWineDebug.isChecked());
    editor.putBoolean("enable_box86_64_logs", cbEnableBox86_64Logs.isChecked());
    editor.putInt("trigger_type", triggerRbIds.indexOf(rgTriggerType.getCheckedRadioButtonId()));
    editor.putBoolean("cursor_lock", cbCursorLock.isChecked()); // Save cursor lock state
    editor.putBoolean("xinput_toggle", cbXinputToggle.isChecked()); // Save xinput toggle state
    editor.putBoolean("enable_file_provider", cbEnableFileProvider.isChecked());
    editor.putBoolean("open_with_android_browser", cbOpenInBrowser.isChecked());
    editor.putBoolean("share_android_clipboard", cbShareClipboard.isChecked());

    // Save gyro settings
    editor.putBoolean("gyro_enabled", cbGyroEnabled.isChecked());
//            editor.putBoolean("process_gyro_with_left_trigger", cbProcessGyroWithLeftTrigger.isChecked());

            int selectedKeycode = keycodes[sbGyroTriggerButton.getSelectedItemPosition()];

// Save the selected keycode to preferences
            editor.putInt("gyro_trigger_button", selectedKeycode);

            editor.putInt("gyro_mode", rgGyroMode.getCheckedRadioButtonId() == R.id.RBHoldMode ? 0 : 1);



            if (!wineDebugChannels.isEmpty()) {
                editor.putString("wine_debug_channels", String.join(",", wineDebugChannels));
            } else if (preferences.contains("wine_debug_channels")) {
                editor.remove("wine_debug_channels");
            }
            else if (preferences.contains("wine_debug_channels")) editor.remove("wine_debug_channels");

            editor.putBoolean("legacy_mode_enabled", enableLegacyInputMode); // Save the 7.1.2 legacy mode state

            if (editor.commit()) {
                NavigationView navigationView = getActivity().findViewById(R.id.NavigationView);
                navigationView.setCheckedItem(R.id.main_menu_containers);
                FragmentManager fragmentManager = getParentFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(R.id.FLFragmentContainer, new ContainersFragment())
                        .commit();
            }
        });



        return view;
    }

    private void updateTheme(boolean isDarkMode) {
        if (isDarkMode) {
            getActivity().setTheme(R.style.AppTheme_Dark);
        } else {
            getActivity().setTheme(R.style.AppTheme);
        }

        // Recreate the activity to apply the new theme
        getActivity().recreate();
    }


    private void applyDynamicStyles(View view, boolean isDarkMode) {

    }

    private void applyDynamicStylesRecursively(View view) {
        TextView soundLabel = view.findViewById(R.id.TVSound);
        applyFieldSetLabelStyle(soundLabel, isDarkMode);

        TextView themeLabel = view.findViewById(R.id.TVTheme);
        applyFieldSetLabelStyle(themeLabel, isDarkMode);

        TextView shortcutSettingsLabel = view.findViewById(R.id.TVShortcutSettings);
        applyFieldSetLabelStyle(shortcutSettingsLabel, isDarkMode);

        TextView frameGenerationSettingsLabel = view.findViewById(R.id.TVFrameGenerationSettings);
        applyFieldSetLabelStyle(frameGenerationSettingsLabel, isDarkMode);

//        TextView shortcutSettingsLabel = view.findViewById(R.id.TVShortcutSettings);
//        applyFieldSetLabelStyle(shortcutSettingsLabel, isDarkMode);

        // Inputs tab labels
        TextView xServerLabel = view.findViewById(R.id.TVXServer);
        applyFieldSetLabelStyle(xServerLabel, isDarkMode);

        TextView gyroSettingsLabel = view.findViewById(R.id.TVGyroSettings);
        applyFieldSetLabelStyle(gyroSettingsLabel, isDarkMode);

        TextView gameControllerLabel = view.findViewById(R.id.TVGameControllerLabel);
        applyFieldSetLabelStyle(gameControllerLabel, isDarkMode);

        // Advanced tab labels
        TextView logsLabel = view.findViewById(R.id.TVLogs);
        applyFieldSetLabelStyle(logsLabel, isDarkMode);

        TextView experimentalLabel = view.findViewById(R.id.TVExperimental);
        applyFieldSetLabelStyle(experimentalLabel, isDarkMode);

        TextView ImageFsLabel = view.findViewById(R.id.TVImageFs);
        applyFieldSetLabelStyle(ImageFsLabel, isDarkMode);

    }

    private void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
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

    private void setupGlobalLosslessDllImport(View view) {
        if (view == null) return;

        View importButton = view.findViewById(R.id.BTImportGlobalLosslessDll);
        if (importButton != null) {
            importButton.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                intent.setType("*/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_GLOBAL_LOSSLESS_DLL);
            });
        }

        updateGlobalLosslessDllStatus(view);
    }

    private void updateGlobalLosslessDllStatus(View view) {
        if (view == null) return;
        TextView statusView = view.findViewById(R.id.TVGlobalLosslessDllStatus);
        if (statusView == null) return;

        String dllPath = LsfgVkManager.globalDllPath(requireContext());
        if (dllPath != null) {
            statusView.setText(getString(R.string.lsfg_global_lossless_dll_installed, dllPath));
        }
        else if (LsfgVkManager.isBundledDllAvailable(requireContext())) {
            statusView.setText(R.string.lsfg_global_lossless_dll_bundled);
        }
        else {
            statusView.setText(R.string.lsfg_global_lossless_dll_missing);
        }
    }



    private void openFile(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");

        // Start activity for result based on the provided request code
        getActivity().startActivityFromFragment(this, intent, requestCode);
    }


    private void loadWineDebugChannels(final View view, final ArrayList<String> debugChannels) {
        final Context context = getContext();
        LinearLayout container = view.findViewById(R.id.LLWineDebugChannels);
        container.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(context);
        View itemView = inflater.inflate(R.layout.wine_debug_channel_list_item, container, false);
        itemView.findViewById(R.id.TextView).setVisibility(View.GONE);
        itemView.findViewById(R.id.BTRemove).setVisibility(View.GONE);

        View addButton = itemView.findViewById(R.id.BTAdd);
        addButton.setVisibility(View.VISIBLE);
        addButton.setOnClickListener((v) -> {
            JSONArray jsonArray = null;
            try {
                jsonArray = new JSONArray(FileUtils.readString(context, "wine_debug_channels.json"));
            }
            catch (JSONException e) {
                Log.e("SettingsFragment", "Failed to parse wine_debug_channels.json", e);
            }

            final String[] items = ArrayUtils.toStringArray(jsonArray);
            ContentDialog.showMultipleChoiceList(context, R.string.wine_debug_channel, items, (selectedPositions) -> {
                for (int selectedPosition : selectedPositions) if (!debugChannels.contains(items[selectedPosition])) debugChannels.add(items[selectedPosition]);
                loadWineDebugChannels(view, debugChannels);
            });
        });

        View resetButton = itemView.findViewById(R.id.BTReset);
        resetButton.setVisibility(View.VISIBLE);
        resetButton.setOnClickListener((v) -> {
            debugChannels.clear();
            debugChannels.addAll(Arrays.asList(DEFAULT_WINE_DEBUG_CHANNELS.split(",")));
            loadWineDebugChannels(view, debugChannels);
        });
        container.addView(itemView);

        for (int i = 0; i < debugChannels.size(); i++) {
            itemView = inflater.inflate(R.layout.wine_debug_channel_list_item, container, false);
            TextView textView = itemView.findViewById(R.id.TextView);
            textView.setText(debugChannels.get(i));
            final int index = i;
            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                debugChannels.remove(index);
                loadWineDebugChannels(view, debugChannels);
            });
            container.addView(itemView);
        }
    }

    public static void resetEmulatorsVersion(Context context) {
        SharedPreferences preferences = new com.winlator.cmod.core.MmkvPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.remove("current_box64_version");
        editor.remove("current_wowbox64_version");
        editor.remove("current_fexcore_version");
        editor.apply();
    }

    private void showGyroConfigDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.gyro_config_dialog, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        builder.setTitle("Gyroscope Configuration");

        // Initialize InputControlsView and configure it for displaying the stick
        InputControlsView inputControlsView = new InputControlsView(getContext(), true);
        inputControlsView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        inputControlsView.setEditMode(false);  // Disable edit mode

        // Initialize the stick element and set its type to STICK
        inputControlsView.initializeStickElement(600, 250, 2.0f);
        inputControlsView.getStickElement().setType(ControlElement.Type.STICK); // Set the type to STICK


        // Add the InputControlsView to the placeholder in your dialog layout
        FrameLayout placeholder = dialogView.findViewById(R.id.stick_placeholder);
        placeholder.addView(inputControlsView);

        // Redraw the stick in InputControlsView
        inputControlsView.invalidate();

        // Initialize the "Reset Center" button
        Button btnResetCenter = dialogView.findViewById(R.id.btnResetCenter);
        btnResetCenter.setOnClickListener(v -> {
            // Reset the stick element's position to the center
            inputControlsView.resetStickPosition();
            inputControlsView.invalidate();  // Redraw the stick
        });

        // Initialize the UI elements in the dialog
        SeekBar sbGyroXSensitivity = dialogView.findViewById(R.id.SBGyroXSensitivity);
        SeekBar sbGyroYSensitivity = dialogView.findViewById(R.id.SBGyroYSensitivity);
        SeekBar sbGyroSmoothing = dialogView.findViewById(R.id.SBGyroSmoothing);
        SeekBar sbGyroDeadzone = dialogView.findViewById(R.id.SBGyroDeadzone);
        CheckBox cbInvertGyroX = dialogView.findViewById(R.id.CBInvertGyroX);
        CheckBox cbInvertGyroY = dialogView.findViewById(R.id.CBInvertGyroY);
        TextView tvGyroXSensitivity = dialogView.findViewById(R.id.TVGyroXSensitivity);
        TextView tvGyroYSensitivity = dialogView.findViewById(R.id.TVGyroYSensitivity);
        TextView tvGyroSmoothing = dialogView.findViewById(R.id.TVGyroSmoothing);
        TextView tvGyroDeadzone = dialogView.findViewById(R.id.TVGyroDeadzone);


        // Load current preferences
        sbGyroXSensitivity.setProgress((int) (preferences.getFloat("gyro_x_sensitivity", 1.0f) * 100));
        sbGyroYSensitivity.setProgress((int) (preferences.getFloat("gyro_y_sensitivity", 1.0f) * 100));
        sbGyroSmoothing.setProgress((int) (preferences.getFloat("gyro_smoothing", 0.9f) * 100));
        sbGyroDeadzone.setProgress((int) (preferences.getFloat("gyro_deadzone", 0.05f) * 100));
        cbInvertGyroX.setChecked(preferences.getBoolean("invert_gyro_x", false));
        cbInvertGyroY.setChecked(preferences.getBoolean("invert_gyro_y", false));

        // Update text views for SeekBars
        tvGyroXSensitivity.setText("X Sensitivity: " + sbGyroXSensitivity.getProgress() + "%");
        tvGyroYSensitivity.setText("Y Sensitivity: " + sbGyroYSensitivity.getProgress() + "%");
        tvGyroSmoothing.setText("Smoothing: " + sbGyroSmoothing.getProgress() + "%");
        tvGyroDeadzone.setText("Deadzone: " + sbGyroDeadzone.getProgress() + "%");

        // Listeners for SeekBars
        sbGyroXSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvGyroXSensitivity.setText("X Sensitivity: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbGyroYSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvGyroYSensitivity.setText("Y Sensitivity: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbGyroSmoothing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvGyroSmoothing.setText("Smoothing: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbGyroDeadzone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvGyroDeadzone.setText("Deadzone: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // SensorManager to handle gyroscope input and affect only the thumbstick position within a fixed radius
        SensorManager sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        Sensor gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

// Define variables for smoothing and deadzone
        final float[] smoothGyroX = {0};
        final float[] smoothGyroY = {0};
        float smoothingFactor = preferences.getFloat("gyro_smoothing", 0.9f);  // User-defined smoothing factor
        float gyroDeadzone = preferences.getFloat("gyro_deadzone", 0.05f);      // User-defined deadzone
        boolean invertGyroX = preferences.getBoolean("invert_gyro_x", false);   // User-defined inversion for X axis
        boolean invertGyroY = preferences.getBoolean("invert_gyro_y", false);   // User-defined inversion for Y axis
        float gyroSensitivityX = preferences.getFloat("gyro_x_sensitivity", 1.0f); // User-defined sensitivity for X axis
        float gyroSensitivityY = preferences.getFloat("gyro_y_sensitivity", 1.0f); // User-defined sensitivity for Y axis

        SensorEventListener gyroListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float rawGyroX = event.values[0];  // Gyroscope X axis value
                float rawGyroY = event.values[1];  // Gyroscope Y axis value

                // Apply deadzone
                if (Math.abs(rawGyroX) < gyroDeadzone) rawGyroX = 0;
                if (Math.abs(rawGyroY) < gyroDeadzone) rawGyroY = 0;

                // Apply inversion
                if (invertGyroX) rawGyroX = -rawGyroX;
                if (invertGyroY) rawGyroY = -rawGyroY;

                // Apply sensitivity
                rawGyroX *= gyroSensitivityX;
                rawGyroY *= gyroSensitivityY;

                // Apply smoothing (exponential smoothing)
                smoothGyroX[0] = smoothGyroX[0] * smoothingFactor + rawGyroX * (1 - smoothingFactor);
                smoothGyroY[0] = smoothGyroY[0] * smoothingFactor + rawGyroY * (1 - smoothingFactor);

                // Define the outer stick's center as a fixed point (outer circle center)
                int stickCenterX = inputControlsView.getStickElement().getX(); // Base stick X (center of outer circle)
                int stickCenterY = inputControlsView.getStickElement().getY(); // Base stick Y (center of outer circle)
                int stickRadius = 100;  // Example radius (adjust as needed)

                // Calculate the new thumbstick (inner circle) position based on the smoothed gyro data
                float newX = inputControlsView.getStickElement().getCurrentPosition().x + smoothGyroX[0];
                float newY = inputControlsView.getStickElement().getCurrentPosition().y + smoothGyroY[0];

                // Calculate the distance between the new thumbstick position and the outer circle center
                float deltaX = newX - stickCenterX;
                float deltaY = newY - stickCenterY;
                float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                // Constrain the inner circle within the outer circle's radius
                if (distance > stickRadius) {
                    float scaleFactor = stickRadius / distance;
                    newX = stickCenterX + deltaX * scaleFactor;
                    newY = stickCenterY + deltaY * scaleFactor;
                }

                // Update the thumbstick (inner circle) position, but keep the outer circle fixed
                inputControlsView.updateStickPosition(newX, newY);

                // Redraw InputControlsView to reflect the new thumbstick position
                inputControlsView.invalidate();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(gyroListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);

        // Set up the dialog buttons
        builder.setPositiveButton("OK", (dialog, which) -> {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putFloat("gyro_x_sensitivity", sbGyroXSensitivity.getProgress() / 100.0f);
            editor.putFloat("gyro_y_sensitivity", sbGyroYSensitivity.getProgress() / 100.0f);
            editor.putFloat("gyro_smoothing", sbGyroSmoothing.getProgress() / 100.0f);
            editor.putFloat("gyro_deadzone", sbGyroDeadzone.getProgress() / 100.0f);
            editor.putBoolean("invert_gyro_x", cbInvertGyroX.isChecked());
            editor.putBoolean("invert_gyro_y", cbInvertGyroY.isChecked());
            editor.apply();
        });

        builder.setNegativeButton("Cancel", null);

        // Show the dialog
        builder.create().show();
    }



    private void showBackupConfirmationDialog() {
        new AlertDialog.Builder(getContext())
                .setTitle("Backup Data")
                .setMessage("Do you want to create a backup of the app's data directory?")
                .setPositiveButton("Yes", (dialog, which) -> backupAppData())
                .setNegativeButton("No", null)
                .show();
    }

    private void backupAppData() {
        File dataDir = getContext().getFilesDir().getParentFile(); // App's data directory
        File backupFile = new File(Environment.getExternalStorageDirectory(), "app_data_backup.tar");

        preloaderDialog.showOnUiThread(R.string.backing_up_data);

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        ExecutorService compressionExecutor = Executors.newFixedThreadPool(availableProcessors);

        compressionExecutor.execute(() -> {
            try {
                TarCompressorUtils.archive(new File[]{dataDir}, backupFile, file -> {
                    // Exclude the problematic directory
                    String excludePath = "imagefs/tmp/.sysvshm";
                    return !file.getAbsolutePath().contains(excludePath);
                });
                getActivity().runOnUiThread(() -> {
                    preloaderDialog.closeOnUiThread();
                    AppUtils.showToast(getContext(), "Backup completed: " + backupFile.getPath());
                });
            } catch (Exception e) {
                getActivity().runOnUiThread(() -> {
                    preloaderDialog.closeOnUiThread();
                    AppUtils.showToast(getContext(), "Backup failed.");
                });
            }
        });
    }



    private void selectBackupFileForRestore() {
        isRestoreAction = true; // Set the flag to indicate a restore operation
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();

            if (uri != null) {
                switch (requestCode) {
                    // Case for File Picker to restore data
                    case MainActivity.OPEN_FILE_REQUEST_CODE:
                        if (isRestoreAction) {
                            restoreAppData(uri);
                            isRestoreAction = false;  // Reset the flag
                        }
                        break;

                    // Case for FilePicker to select frontend export path
                    case REQUEST_CODE_FRONTEND_EXPORT_PATH:
                        // Save the selected URI as a string in SharedPreferences
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putString("frontend_export_uri", uri.toString());
                        editor.apply();

                        // Take persistable URI permission
                        try {
                            // Take persistable URI permission with explicit flags
                            requireContext().getContentResolver().takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );
                        } catch (SecurityException e) {
                            AppUtils.showToast(getContext(), "Unable to take persistable permissions: " + e.getMessage());
                        }

                        // Convert the URI to an absolute path and display it
                        String fullPath = FileUtils.getFilePathFromUri(getContext(), uri);

                        // Update the TextView with the absolute path or URI string if conversion fails
                        TextView tvFrontendExportPath = getView().findViewById(R.id.TVFrontendExportPath);
                        tvFrontendExportPath.setText(fullPath != null ? fullPath : uri.toString());
                        break;

                    // Case for installing a SoundFont
                    case REQUEST_CODE_INSTALL_SOUNDFONT:
                        if (installSoundFontCallback != null) {
                            try {
                                installSoundFontCallback.call(uri);
                            } catch (Exception e) {
                                AppUtils.showToast(getContext(), R.string.unable_to_install_soundfont);
                            } finally {
                                installSoundFontCallback = null;
                            }
                        }
                        break;

                    case REQUEST_CODE_IMPORT_GLOBAL_LOSSLESS_DLL:
                        try {
                            requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        catch (Throwable ignored) {
                        }

                        boolean imported = LsfgVkManager.importGlobalLosslessDll(requireContext(), uri);
                        AppUtils.showToast(getContext(), imported ? R.string.lsfg_lossless_dll_imported : R.string.lsfg_lossless_dll_import_failed);
                        updateGlobalLosslessDllStatus(getView());
                        break;

                    // Add future cases here for other request codes...
                    default:
                        break;
                }
            }
        }
    }






    private void restoreAppData(Uri backupUri) {
        if (getActivity() != null) {  // Ensure the activity is not null
            Intent intent = new Intent(getActivity(), RestoreActivity.class);
            intent.setData(backupUri);
            startActivity(intent);
            getActivity().finish(); // Close the main activity
        }
    }


    private void moveFiles(File sourceDir, File targetDir) throws IOException {
        File[] files = sourceDir.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(targetDir, file.getName());
                if (file.isDirectory()) {
                    if (!targetFile.exists()) {
                        targetFile.mkdirs();
                    }
                    moveFiles(file, targetFile); // Recursively move directory contents
                } else {
                    if (!file.renameTo(targetFile)) {
                        throw new IOException("Failed to move file: " + file.getAbsolutePath());
                    }
                }
            }
        }
        // Clear the temporary directory after moving
        FileUtils.clear(sourceDir);
    }


    private void onRestoreSuccess() {
        getActivity().runOnUiThread(() -> {
            preloaderDialog.closeOnUiThread();
            AppUtils.showToast(getContext(), "Data restored successfully.");
            AppUtils.restartApplication(getActivity());  // Restart the app to apply changes
        });
    }

    private void onRestoreFailed() {
        getActivity().runOnUiThread(() -> {
            preloaderDialog.closeOnUiThread();
            AppUtils.showToast(getContext(), "Data restore failed.");
        });
    }

    /**
     * Р С›Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµРЎвЂљ Р С•РЎвЂљР С•Р В±РЎР‚Р В°Р В¶Р ВµР Р…Р С‘Р Вµ Р С‘Р Р…РЎвЂћР С•РЎР‚Р СР В°РЎвЂ Р С‘Р С‘ Р С• GPU
     */
    private void updateGPUInfoDisplay(TextView tvGPUInfoDisplay) {
        try {
            GPUPerformanceManager gpuManager = new GPUPerformanceManager(getContext());
            GPUPerformanceManager.GPUStatus status = gpuManager.getGPUStatus();
            
            String gpuRenderer = com.winlator.cmod.core.GPUInformation.getRenderer();
            String info = String.format("GPU: %s\nР В Р ВµР С–РЎС“Р В»РЎРЏРЎвЂљР С•РЎР‚: %s", 
                gpuRenderer, 
                status.governor != null ? status.governor : "Р СњР ВµР С‘Р В·Р Р†Р ВµРЎРѓРЎвЂљР Р…Р С•");
            
            if (status.currentFreq > 0) {
                info += String.format("\nР В§Р В°РЎРѓРЎвЂљР С•РЎвЂљР В°: %s (%d%%)", 
                    status.getFrequencyMHz(), status.getUsagePercent());
            }
            
            tvGPUInfoDisplay.setText(info);
        } catch (Exception e) {
            tvGPUInfoDisplay.setText("GPU: Р ВР Р…РЎвЂћР С•РЎР‚Р СР В°РЎвЂ Р С‘РЎРЏ Р Р…Р ВµР Т‘Р С•РЎРѓРЎвЂљРЎС“Р С—Р Р…Р В°");
        }
    }

    private void showAnalogStickConfigDialog() {
        // Inflate the dialog layout
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View dialogView = inflater.inflate(R.layout.analog_stick_config_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setView(dialogView);
        builder.setTitle("Configure Analog Sticks");
        builder.setCancelable(false);

        // Initialize UI elements
        SeekBar sbLeftDeadzone = dialogView.findViewById(R.id.SBLeftDeadzone);
        TextView tvLeftDeadzone = dialogView.findViewById(R.id.TVLeftDeadzone);

        SeekBar sbLeftSensitivity = dialogView.findViewById(R.id.SBLeftSensitivity);
        TextView tvLeftSensitivity = dialogView.findViewById(R.id.TVLeftSensitivity);

        SeekBar sbRightDeadzone = dialogView.findViewById(R.id.SBRightDeadzone);
        TextView tvRightDeadzone = dialogView.findViewById(R.id.TVRightDeadzone);

        SeekBar sbRightSensitivity = dialogView.findViewById(R.id.SBRightSensitivity);
        TextView tvRightSensitivity = dialogView.findViewById(R.id.TVRightSensitivity);

        CheckBox cbInvertLeftX = dialogView.findViewById(R.id.CBInvertLeftStickX);
        CheckBox cbInvertLeftY = dialogView.findViewById(R.id.CBInvertLeftStickY);
        CheckBox cbInvertRightX = dialogView.findViewById(R.id.CBInvertRightStickX);
        CheckBox cbInvertRightY = dialogView.findViewById(R.id.CBInvertRightStickY);

        // New checkbox for square deadzone
        CheckBox cbLeftStickSquareDeadzone = dialogView.findViewById(R.id.CBLeftStickSquareDeadzone);

        // Load current preferences
        float currentDeadzoneLeft = preferences.getFloat(PreferenceKeys.DEADZONE_LEFT, 0.1f) * 100; // Convert to percentage
        float currentDeadzoneRight = preferences.getFloat(PreferenceKeys.DEADZONE_RIGHT, 0.1f) * 100;
        float currentSensitivityLeft = preferences.getFloat(PreferenceKeys.SENSITIVITY_LEFT, 1.0f) * 100; // Convert to percentage
        float currentSensitivityRight = preferences.getFloat(PreferenceKeys.SENSITIVITY_RIGHT, 1.0f) * 100;
        boolean squareDeadzoneLeft = preferences.getBoolean(PreferenceKeys.SQUARE_DEADZONE_LEFT, false);

        boolean invertLeftX = preferences.getBoolean(PreferenceKeys.INVERT_LEFT_X, false);
        boolean invertLeftY = preferences.getBoolean(PreferenceKeys.INVERT_LEFT_Y, false);
        boolean invertRightX = preferences.getBoolean(PreferenceKeys.INVERT_RIGHT_X, false);
        boolean invertRightY = preferences.getBoolean(PreferenceKeys.INVERT_RIGHT_Y, false);

        // Set initial values
        sbLeftDeadzone.setProgress((int) currentDeadzoneLeft);
        tvLeftDeadzone.setText("Deadzone: " + sbLeftDeadzone.getProgress() + "%");

        sbLeftSensitivity.setProgress((int) currentSensitivityLeft);
        tvLeftSensitivity.setText("Sensitivity: " + sbLeftSensitivity.getProgress() + "%");

        sbRightDeadzone.setProgress((int) currentDeadzoneRight);
        tvRightDeadzone.setText("Deadzone: " + sbRightDeadzone.getProgress() + "%");

        sbRightSensitivity.setProgress((int) currentSensitivityRight);
        tvRightSensitivity.setText("Sensitivity: " + sbRightSensitivity.getProgress() + "%");

        cbInvertLeftX.setChecked(invertLeftX);
        cbInvertLeftY.setChecked(invertLeftY);
        cbInvertRightX.setChecked(invertRightX);
        cbInvertRightY.setChecked(invertRightY);

        cbLeftStickSquareDeadzone.setChecked(squareDeadzoneLeft);

        // Set listeners to update TextViews as SeekBars change
        sbLeftDeadzone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLeftDeadzone.setText("Deadzone: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbLeftSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvLeftSensitivity.setText("Sensitivity: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbRightDeadzone.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvRightDeadzone.setText("Deadzone: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        sbRightSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvRightSensitivity.setText("Sensitivity: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Set up the dialog buttons
        builder.setPositiveButton("Save", (dialog, which) -> {
            // Retrieve and save the updated settings
            float newDeadzoneLeft = sbLeftDeadzone.getProgress() / 100.0f;
            float newDeadzoneRight = sbRightDeadzone.getProgress() / 100.0f;
            float newSensitivityLeft = sbLeftSensitivity.getProgress() / 100.0f;
            float newSensitivityRight = sbRightSensitivity.getProgress() / 100.0f;

            boolean newInvertLeftX = cbInvertLeftX.isChecked();
            boolean newInvertLeftY = cbInvertLeftY.isChecked();
            boolean newInvertRightX = cbInvertRightX.isChecked();
            boolean newInvertRightY = cbInvertRightY.isChecked();

            // Save to SharedPreferences
            SharedPreferences.Editor editor = preferences.edit();
            editor.putFloat(PreferenceKeys.DEADZONE_LEFT, newDeadzoneLeft);
            editor.putFloat(PreferenceKeys.DEADZONE_RIGHT, newDeadzoneRight);
            editor.putFloat(PreferenceKeys.SENSITIVITY_LEFT, newSensitivityLeft);
            editor.putFloat(PreferenceKeys.SENSITIVITY_RIGHT, newSensitivityRight);
            editor.putBoolean(PreferenceKeys.INVERT_LEFT_X, newInvertLeftX);
            editor.putBoolean(PreferenceKeys.INVERT_LEFT_Y, newInvertLeftY);
            editor.putBoolean(PreferenceKeys.INVERT_RIGHT_X, newInvertRightX);
            editor.putBoolean(PreferenceKeys.INVERT_RIGHT_Y, newInvertRightY);
            editor.putBoolean(PreferenceKeys.SQUARE_DEADZONE_LEFT, cbLeftStickSquareDeadzone.isChecked());
            editor.apply();

            // Optionally, notify ExternalController instances to reload preferences
            // If you have a central manager or singleton, you can call a method here
            // For example:
            // ExternalControllerManager.getInstance().reloadPreferences();

            // For this example, we'll assume ExternalController instances listen to preference changes
        });

        builder.setNegativeButton("Cancel", null);

        // Create and show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void applyLanguage(String languageCode) {
        if ("system".equals(languageCode)) {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList());
        } else if ("en".equals(languageCode)) {
            androidx.core.os.LocaleListCompat localeList = androidx.core.os.LocaleListCompat.forLanguageTags("en");
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList);
        } else if ("ru".equals(languageCode)) {
            androidx.core.os.LocaleListCompat localeList = androidx.core.os.LocaleListCompat.forLanguageTags("ru");
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList);
        } else if ("zh".equals(languageCode)) {
            androidx.core.os.LocaleListCompat localeList = androidx.core.os.LocaleListCompat.forLanguageTags("zh");
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList);
        } else if ("pt".equals(languageCode)) {
            androidx.core.os.LocaleListCompat localeList = androidx.core.os.LocaleListCompat.forLanguageTags("pt");
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList);
        } else if ("pt-rBR".equals(languageCode)) {
            androidx.core.os.LocaleListCompat localeList = androidx.core.os.LocaleListCompat.forLanguageTags("pt-BR");
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList);
        }
    }

}

