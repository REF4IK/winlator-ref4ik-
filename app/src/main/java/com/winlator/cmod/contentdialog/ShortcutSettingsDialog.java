package com.winlator.cmod.contentdialog;



import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.winlator.cmod.ContainerDetailFragment;
import com.winlator.cmod.R;
import com.winlator.cmod.ShortcutsFragment;
import com.winlator.cmod.box86_64.Box86_64EditPresetDialog;
import com.winlator.cmod.box86_64.Box86_64PresetManager;
import com.winlator.cmod.box86_64.rc.RCManager;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.fexcore.FEXCoreEditPresetDialog;
import com.winlator.cmod.fexcore.FEXCoreManager;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.widget.CPUListView;
import com.winlator.cmod.widget.EnvVarsView;
import com.winlator.cmod.winhandler.WinHandler;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kotlin.random.Random;

public class ShortcutSettingsDialog extends ContentDialog {
    private static final int STEAM_COLOR_PRIMARY = 0xFF18C5BE;
    private static final int STEAM_COLOR_PRIMARY_ALT = 0xFF18C5F3;
    private static final int STEAM_COLOR_BACKGROUND = 0xFF09090D;
    private static final int STEAM_COLOR_SURFACE = 0xFF11141C;
    private static final int STEAM_COLOR_SURFACE_VARIANT = 0xFF171C26;
    private static final int STEAM_COLOR_SURFACE_ELEVATED = 0xFF1B2130;
    private static final int STEAM_COLOR_CARD = 0xFF161D29;
    private static final int STEAM_COLOR_OUTLINE = 0xFF263348;
    private static final int STEAM_COLOR_TEXT = 0xFFF3F7FF;
    private static final int STEAM_COLOR_SUBTEXT = 0xFF95A6BF;
    private static final int STEAM_COLOR_SELECTED = 0xFF0E4E83;
    private static final int STEAM_COLOR_GRADIENT_END = 0xFF7E2DFF;
    private final ShortcutsFragment fragment;
    private final Shortcut shortcut;
    private final boolean steamStyledMode;
    private final boolean steamComposeHostMode;
    private InputControlsManager inputControlsManager;
    private TextView tvGraphicsDriverVersion;
    private String box64Version;
    private final Context context;
    private final ContentsManager contentsManager;
    private final ArrayList<View> steamHostSections = new ArrayList<>();
    private String[] steamHostSectionTitles = new String[0];
    private LinearLayout generalSection;
    public static final int REQUEST_CODE_SELECT_CUSTOM_ICON = 10001;


    public ShortcutSettingsDialog(ShortcutsFragment fragment, Shortcut shortcut) {
        this(fragment.getContext(), fragment, shortcut, false, false);
    }
    
    // Constructor for use from BigPictureActivity or other Activities
    public ShortcutSettingsDialog(Context context, Shortcut shortcut) {
        this(context, null, shortcut, false, false);
    }

    public ShortcutSettingsDialog(Context context, Shortcut shortcut, boolean steamStyledMode) {
        this(context, null, shortcut, steamStyledMode, false);
    }

    public ShortcutSettingsDialog(Context context, Shortcut shortcut, boolean steamStyledMode, boolean steamComposeHostMode) {
        this(context, null, shortcut, steamStyledMode, steamComposeHostMode);
    }

    private ShortcutSettingsDialog(Context context, ShortcutsFragment fragment, Shortcut shortcut, boolean steamStyledMode, boolean steamComposeHostMode) {
        super(context, R.layout.shortcut_settings_dialog, steamStyledMode || steamComposeHostMode);
        this.fragment = fragment;
        this.context = (steamStyledMode || steamComposeHostMode) ? getContext() : context;
        this.shortcut = shortcut;
        this.steamStyledMode = steamStyledMode;
        this.steamComposeHostMode = steamComposeHostMode;
        this.contentsManager = new ContentsManager(this.context);
        setTitle(shortcut.name);
        setIcon(R.drawable.icon_settings_shortcut);

        // Initialize the ContentsManager
        ContainerManager containerManager = shortcut.container.getManager();

//        if (containerManager != null) {
//            this.contentsManager = new ContentsManager(containerManager.getContext());
//            this.contentsManager.syncTurnipContents();
//        } else {
//            Toast.makeText(fragment.getContext(), "Failed to initialize container manager. Please try again.", Toast.LENGTH_SHORT).show();
//            return;
//        }

        createContentView();
    }

    private void createContentView() {
        final Context ctx = (fragment != null) ? fragment.getContext() : context;
        inputControlsManager = new InputControlsManager(context);
        LinearLayout llContent = findViewById(R.id.LLContent);
        ViewGroup.LayoutParams contentLayoutParams = llContent.getLayoutParams();
        if (steamStyledMode || steamComposeHostMode) {
            contentLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            int preferredWidth = AppUtils.getPreferredDialogWidth(context);
            int screenWidth = ctx.getResources().getDisplayMetrics().widthPixels;
            int dialogSideMargin = (int) UnitUtils.dpToPx(24);
            int minDialogWidth = (int) UnitUtils.dpToPx(260);
            contentLayoutParams.width = Math.max(minDialogWidth, Math.min(preferredWidth, screenWidth - dialogSideMargin));
        }
        llContent.setLayoutParams(contentLayoutParams);

        SharedPreferences prefs = new com.winlator.cmod.core.MmkvPreferences();
        boolean isDarkMode = prefs.getBoolean("dark_mode", false) || steamStyledMode || steamComposeHostMode;

        applyDynamicStyles(findViewById(R.id.LLContent), isDarkMode);
        if (steamStyledMode) {
            applySteamDialogChrome();
        }

        // Initialize the turnip version TextView
        tvGraphicsDriverVersion = findViewById(R.id.TVGraphicsDriverVersion);

        // Get the shared preferences and check the legacy mode status
        SharedPreferences preferences = new com.winlator.cmod.core.MmkvPreferences();
        boolean isLegacyModeEnabled = preferences.getBoolean("legacy_mode_enabled", false);

        final EditText etName = findViewById(R.id.ETName);
        etName.setText(shortcut.name);

        final ImageView ivCustomIcon = findViewById(R.id.IVCustomIcon);
        final Button btSelectCustomIcon = findViewById(R.id.BTSelectCustomIcon);
        final Button btRemoveCustomIcon = findViewById(R.id.BTRemoveCustomIcon);
        Bitmap displayIcon = shortcut.getDisplayIcon();
        if (displayIcon != null) ivCustomIcon.setImageBitmap(displayIcon);
        else ivCustomIcon.setImageResource(R.drawable.icon_shortcut);
        btRemoveCustomIcon.setVisibility(shortcut.getCustomIcon() != null ? View.VISIBLE : View.GONE);
        btSelectCustomIcon.setOnClickListener((v) -> {
            if (context instanceof Activity) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                ((Activity)context).startActivityForResult(intent, REQUEST_CODE_SELECT_CUSTOM_ICON);
            }
        });
        btRemoveCustomIcon.setOnClickListener((v) -> {
            shortcut.removeCustomIcon();
            Bitmap fallbackIcon = shortcut.getDisplayIcon();
            if (fallbackIcon != null) ivCustomIcon.setImageBitmap(fallbackIcon);
            else ivCustomIcon.setImageResource(R.drawable.icon_shortcut);
            btRemoveCustomIcon.setVisibility(View.GONE);
            ShortcutsFragment.updateShortcutOnScreen(context, shortcut);
        });

        final EditText etExecArgs = findViewById(R.id.ETExecArgs);
        etExecArgs.setText(shortcut.getExtra("execArgs"));

        ContainerDetailFragment containerDetailFragment = new ContainerDetailFragment(shortcut.container.id);
//        containerDetailFragment.loadScreenSizeSpinner(getContentView(), shortcut.getExtra("screenSize", shortcut.container.getScreenSize()));

        loadScreenSizeSpinner(getContentView(), shortcut.getExtra("screenSize", shortcut.container.getScreenSize()), isDarkMode);


        final Spinner sGraphicsDriver = findViewById(R.id.SGraphicsDriver);
        
        final Spinner sDXWrapper = findViewById(R.id.SDXWrapper);

        final Spinner sDDrawrapper = findViewById(R.id.SDDrawrapper);

        final Spinner sBox64Version = findViewById(R.id.SBox64Version);
        
        contentsManager.syncContents();

        final View vGraphicsDriverConfig = findViewById(R.id.BTGraphicsDriverConfig);
        vGraphicsDriverConfig.setTag(shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()));
        
        final View vDXWrapperConfig = findViewById(R.id.BTDXWrapperConfig);
        vDXWrapperConfig.setTag(shortcut.getExtra("dxwrapperConfig", shortcut.container.getDXWrapperConfig()));

        ContainerDetailFragment.setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig);
        ContainerDetailFragment.setupDDrawSpinner(sDDrawrapper, shortcut.getExtra("ddrawrapper", shortcut.container.getDDrawWrapper()));
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig, shortcut.getExtra("graphicsDriver", shortcut.container.getGraphicsDriver()),
            shortcut.getExtra("dxwrapper", shortcut.container.getDXWrapper()));

        findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.dxwrapper_help_content));

        final Spinner sAudioDriver = findViewById(R.id.SAudioDriver);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, shortcut.getExtra("audioDriver", shortcut.container.getAudioDriver()));
        final Spinner sEmulator = findViewById(R.id.SEmulator);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, shortcut.getExtra("emulator", shortcut.container.getEmulator()));
        final Spinner sEmulator64 = findViewById(R.id.SEmulator64);
        sEmulator64.setEnabled(false);
        final Spinner sMIDISoundFont = findViewById(R.id.SMIDISoundFont);
        MidiManager.loadSFSpinner(sMIDISoundFont);
        AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, shortcut.getExtra("midiSoundFont", shortcut.container.getMIDISoundFont()));

        FrameLayout fexcoreFL = findViewById(R.id.fexcoreFrame);
        FrameLayout box86box64FL = findViewById(R.id.box86box64Frame);
        String wineVersion = shortcut.container.getWineVersion();
        WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
        final boolean shortcutWineIsArm64EC = wineInfo.isArm64EC();
        if (shortcutWineIsArm64EC) {
            fexcoreFL.setVisibility(View.VISIBLE);
            sEmulator.setEnabled(true);
            sEmulator64.setSelection(0);
        }
        else {
            fexcoreFL.setVisibility(View.GONE);
            sEmulator.setEnabled(false);
            sEmulator.setSelection(1);
            sEmulator64.setSelection(1);
        }

        if (box86box64FL != null) {
            box86box64FL.setVisibility(View.VISIBLE);
        }

        AdapterView.OnItemSelectedListener emulatorListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateEmulatorConfigVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateEmulatorConfigVisibility();
            }
        };
        sEmulator.setOnItemSelectedListener(emulatorListener);
        if (sEmulator64 != null) {
            sEmulator64.setOnItemSelectedListener(emulatorListener);
        }
        updateEmulatorConfigVisibility();

        loadShortcutBox64VersionSpinner(sBox64Version, shortcutWineIsArm64EC);

        findViewById(R.id.BTDownloadBox64Version).setOnClickListener((v) -> {
            ContentProfile.ContentType contentType = shortcutWineIsArm64EC
                    ? ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                    : ContentProfile.ContentType.CONTENT_TYPE_BOX64;
            String displayName = shortcutWineIsArm64EC ? "WoWBox64" : "Box64";
            showShortcutVersionDownloadDialog(contentType, displayName, sBox64Version,
                    () -> loadShortcutBox64VersionSpinner(sBox64Version, shortcutWineIsArm64EC));
        });

        // Add this part to set the initial spinner selection based on the shortcut
        String currentBox64Version = shortcut.getExtra("box64Version", shortcut.container.getBox64Version());
        if (currentBox64Version != null) {
            AppUtils.setSpinnerSelectionFromValue(sBox64Version, currentBox64Version);
        } else {
            // Default selection or use a preferred default version
            AppUtils.setSpinnerSelectionFromValue(sBox64Version, DefaultVersion.BOX64);
        }

        // Set OnItemSelectedListener for the Box64 version spinner
        sBox64Version.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedVersion = parent.getItemAtPosition(position).toString();
                box64Version = selectedVersion;  // Update the class-level variable
                // Update the shortcut extra immediately, or wait until saveData() is called
                shortcut.putExtra("box64Version", selectedVersion);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // This method must be implemented, even if it's empty.
                // Optional: You can handle the case where no item is selected, if needed.
            }
        });

        final CheckBox cbUseSecondaryExec = findViewById(R.id.CBUseSecondaryExec);
        final LinearLayout llSecondaryExecOptions = findViewById(R.id.LLSecondaryExecOptions);
        final EditText etSecondaryExec = findViewById(R.id.ETSecondaryExec);
        final EditText etExecDelay = findViewById(R.id.ETExecDelay);

        boolean useSecondaryExec = !shortcut.getExtra("secondaryExec", "").isEmpty();
        cbUseSecondaryExec.setChecked(useSecondaryExec);
        llSecondaryExecOptions.setVisibility(useSecondaryExec ? View.VISIBLE : View.GONE);
        etSecondaryExec.setText(shortcut.getExtra("secondaryExec"));
        etExecDelay.setText(shortcut.getExtra("execDelay", "0"));

        cbUseSecondaryExec.setOnCheckedChangeListener((buttonView, isChecked) -> {
            llSecondaryExecOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // Initialize the TextView for the legacy mode message
        TextView tvLegacyInputMessage = findViewById(R.id.TVLegacyInputMessage);

        final CheckBox cbFullscreenStretched =  findViewById(R.id.CBFullscreenStretched);
        boolean fullscreenStretched = shortcut.getExtra("fullscreenStretched", "0").equals("1");
        cbFullscreenStretched.setChecked(fullscreenStretched);


        final Runnable showInputWarning = () -> ContentDialog.alert(context, R.string.enable_xinput_and_dinput_same_time, null);
        final CheckBox cbEnableXInput = findViewById(R.id.CBEnableXInput);
        final CheckBox cbEnableDInput = findViewById(R.id.CBEnableDInput);
        final View llDInputType = findViewById(R.id.LLDinputMapperType);
        final View btHelpXInput = findViewById(R.id.BTXInputHelp);
        final View btHelpDInput = findViewById(R.id.BTDInputHelp);
        Spinner SDInputType = findViewById(R.id.SDInputType);
        int inputType = Integer.parseInt(shortcut.getExtra("inputType", String.valueOf(shortcut.container.getInputType())));

        if (isLegacyModeEnabled) {
            // Display legacy mode message and hide input controls
            tvLegacyInputMessage.setText("You are in 7.1.2 legacy input mode. Advanced input settings are not available.");
            tvLegacyInputMessage.setVisibility(View.VISIBLE);
            // In legacy mode, hide all input-related UI elements
            cbEnableXInput.setVisibility(View.GONE);
            cbEnableDInput.setVisibility(View.GONE);
            llDInputType.setVisibility(View.GONE);
            btHelpXInput.setVisibility(View.GONE);
            btHelpDInput.setVisibility(View.GONE);
            SDInputType.setVisibility(View.GONE);
        } else {
            cbEnableXInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT);
            cbEnableDInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT);
            cbEnableDInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
                llDInputType.setVisibility(isChecked?View.VISIBLE:View.GONE);
                if (isChecked && cbEnableXInput.isChecked())
                    showInputWarning.run();
            });
            cbEnableXInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && cbEnableDInput.isChecked())
                    showInputWarning.run();
            });
            btHelpXInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_xinput));
            btHelpDInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_dinput));
            SDInputType.setSelection(((inputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? 0 : 1);
            llDInputType.setVisibility(cbEnableDInput.isChecked()?View.VISIBLE:View.GONE);

            // Always show input-related UI elements when not in legacy mode
            cbEnableXInput.setVisibility(View.VISIBLE);
            cbEnableDInput.setVisibility(View.VISIBLE);
            llDInputType.setVisibility(View.VISIBLE);
            btHelpXInput.setVisibility(View.VISIBLE);
            btHelpDInput.setVisibility(View.VISIBLE);
            SDInputType.setVisibility(View.VISIBLE);


        }

        final CheckBox cbForceFullscreen = findViewById(R.id.CBForceFullscreen);
        cbForceFullscreen.setChecked(shortcut.getExtra("forceFullscreen", "0").equals("1"));


        final Spinner sBox64Preset = findViewById(R.id.SBox64Preset);
        Box86_64PresetManager.loadSpinner("box64", sBox64Preset, shortcut.getExtra("box64Preset", shortcut.container.getBox64Preset()));

        findViewById(R.id.BTAddBox64Preset).setOnClickListener((v) -> {
            Box86_64EditPresetDialog dialog = new Box86_64EditPresetDialog(context, "box64", null);
            dialog.setOnConfirmCallback(() -> Box86_64PresetManager.loadSpinner("box64", sBox64Preset, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset)));
            dialog.show();
        });

        findViewById(R.id.BTEditBox64Preset).setOnClickListener((v) -> {
            Box86_64EditPresetDialog dialog = new Box86_64EditPresetDialog(context, "box64", Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
            dialog.setOnConfirmCallback(() -> Box86_64PresetManager.loadSpinner("box64", sBox64Preset, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset)));
            dialog.show();
        });

        findViewById(R.id.BTDuplicateBox64Preset).setOnClickListener((v) -> {
            ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
                Box86_64PresetManager.duplicatePreset("box64", context, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
                Box86_64PresetManager.loadSpinner("box64", sBox64Preset, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
                sBox64Preset.setSelection(sBox64Preset.getCount() - 1);
            });
        });

        findViewById(R.id.BTRemoveBox64Preset).setOnClickListener((v) -> {
            final String presetId = Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset);
            if (!presetId.startsWith(com.winlator.cmod.box86_64.Box86_64Preset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                Box86_64PresetManager.removePreset("box64", context, presetId);
                Box86_64PresetManager.loadSpinner("box64", sBox64Preset, shortcut.getExtra("box64Preset", shortcut.container.getBox64Preset()));
            });
        });

        findViewById(R.id.BTExportBox64Preset).setOnClickListener((v) -> {
            Box86_64PresetManager.exportPreset("box64", context, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
        });

        findViewById(R.id.BTImportBox64Preset).setOnClickListener((v) -> {
            if (fragment != null) {
                fragment.requestImportBox64Preset((Uri uri) -> {
                    if (uri == null) return;
                    try {
                        InputStream is = context.getContentResolver().openInputStream(uri);
                        if (is == null) return;
                        Box86_64PresetManager.importPreset("box64", context, is);
                        is.close();
                        Box86_64PresetManager.loadSpinner("box64", sBox64Preset, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
                        sBox64Preset.setSelection(sBox64Preset.getCount() - 1);
                    }
                    catch (Exception ignored) {
                    }
                });
            }
            else {
                File[] files = Box86_64PresetManager.listExportedPresets(context);
                if (files.length == 0) {
                    AppUtils.showToast(context, "No presets found");
                    return;
                }
                String[] names = new String[files.length];
                for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
                ContentDialog.showSingleChoiceList(context, R.string.import_profile, names, (index) -> {
                    if (index < 0 || index >= files.length) return;
                    Box86_64PresetManager.importPreset("box64", context, files[index]);
                    Box86_64PresetManager.loadSpinner("box64", sBox64Preset, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
                    sBox64Preset.setSelection(sBox64Preset.getCount() - 1);
                });
            }
        });

        final Spinner sFEXCoreVersion = findViewById(R.id.SFEXCoreVersion);
        FEXCoreManager.loadFEXCoreVersion(context, contentsManager, sFEXCoreVersion, shortcut);

        findViewById(R.id.BTDownloadFEXCoreVersion).setOnClickListener((v) ->
                showShortcutVersionDownloadDialog(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, "FEXCore", sFEXCoreVersion,
                        () -> FEXCoreManager.loadFEXCoreVersion(context, contentsManager, sFEXCoreVersion, shortcut)));

        final Spinner sFEXCorePreset = findViewById(R.id.SFEXCorePreset);
        FEXCorePresetManager.loadSpinner(sFEXCorePreset, shortcut.getExtra("fexcorePreset", shortcut.container.getFEXCorePreset()));

        findViewById(R.id.BTAddFEXCorePreset).setOnClickListener((v) -> {
            FEXCoreEditPresetDialog dialog = new FEXCoreEditPresetDialog(context, null);
            dialog.setOnConfirmCallback(() -> FEXCorePresetManager.loadSpinner(sFEXCorePreset, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset)));
            dialog.show();
        });

        findViewById(R.id.BTEditFEXCorePreset).setOnClickListener((v) -> {
            FEXCoreEditPresetDialog dialog = new FEXCoreEditPresetDialog(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
            dialog.setOnConfirmCallback(() -> FEXCorePresetManager.loadSpinner(sFEXCorePreset, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset)));
            dialog.show();
        });

        findViewById(R.id.BTDuplicateFEXCorePreset).setOnClickListener((v) -> {
            ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
                FEXCorePresetManager.duplicatePreset(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
                FEXCorePresetManager.loadSpinner(sFEXCorePreset, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
                sFEXCorePreset.setSelection(sFEXCorePreset.getCount() - 1);
            });
        });

        findViewById(R.id.BTRemoveFEXCorePreset).setOnClickListener((v) -> {
            final String presetId = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
            if (!presetId.startsWith(FEXCorePreset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                FEXCorePresetManager.removePreset(context, presetId);
                FEXCorePresetManager.loadSpinner(sFEXCorePreset, shortcut.getExtra("fexcorePreset", shortcut.container.getFEXCorePreset()));
            });
        });

        findViewById(R.id.BTExportFEXCorePreset).setOnClickListener((v) -> {
            FEXCorePresetManager.exportPreset(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
        });

        findViewById(R.id.BTImportFEXCorePreset).setOnClickListener((v) -> {
            if (fragment != null) {
                fragment.requestImportFexcorePreset((Uri uri) -> {
                    if (uri == null) return;
                    try {
                        InputStream is = context.getContentResolver().openInputStream(uri);
                        if (is == null) return;
                        FEXCorePresetManager.importPreset(context, is);
                        is.close();
                        FEXCorePresetManager.loadSpinner(sFEXCorePreset, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
                        sFEXCorePreset.setSelection(sFEXCorePreset.getCount() - 1);
                    }
                    catch (Exception ignored) {
                    }
                });
            }
            else {
                File[] files = FEXCorePresetManager.listExportedPresets(context);
                if (files.length == 0) {
                    AppUtils.showToast(context, "No presets found");
                    return;
                }
                String[] names = new String[files.length];
                for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
                ContentDialog.showSingleChoiceList(context, R.string.import_profile, names, (index) -> {
                    if (index < 0 || index >= files.length) return;
                    FEXCorePresetManager.importPreset(context, files[index]);
                    FEXCorePresetManager.loadSpinner(sFEXCorePreset, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
                    sFEXCorePreset.setSelection(sFEXCorePreset.getCount() - 1);
                });
            }
        });

        final Spinner sRCFile = findViewById(R.id.SRCFile);
        final int[] rcfileIds = {0};
        RCManager manager = new RCManager(context);
        String rcfileId = shortcut.getExtra("rcfileId", String.valueOf(shortcut.container.getRCFileId()));
        RCManager.loadRCFileSpinner(manager, Integer.parseInt(rcfileId), sRCFile, id -> {
            rcfileIds[0] = id;
        });

        final Spinner sControlsProfile = findViewById(R.id.SControlsProfile);
        loadControlsProfileSpinner(sControlsProfile, shortcut.getExtra("controlsProfile", "0"));

        final CheckBox cbDisabledXInput = findViewById(R.id.CBDisabledXInput);
        // Set the initial value based on the shortcut extras
        boolean isXInputDisabled = shortcut.getExtra("disableXinput", "0").equals("1");
        cbDisabledXInput.setChecked(isXInputDisabled);

        final CheckBox cbSimTouchScreen = findViewById(R.id.CBTouchscreenMode);
        String isTouchScreenMode = shortcut.getExtra("simTouchScreen");
        cbSimTouchScreen.setChecked(isTouchScreenMode.equals("1") ? true : false);

        ContainerDetailFragment.createWinComponentsTabFromShortcut(this, getContentView(),
                shortcut.getExtra("wincomponents", shortcut.container.getWinComponents()), isDarkMode);

        final EnvVarsView envVarsView = createEnvVarsTab(isDarkMode);

        TabLayout tabLayout = findViewById(R.id.TabLayout);
        LinearLayout llTabWinComponents = findViewById(R.id.LLTabWinComponents);
        LinearLayout llTabEnvVars = findViewById(R.id.LLTabEnvVars);
        LinearLayout llTabAdvanced = findViewById(R.id.LLTabAdvanced);
        if (steamStyledMode) {
            buildSteamSplitLayout();
        } else if (steamComposeHostMode) {
            prepareSteamComposeHostContent(llContent, tabLayout, llTabWinComponents, llTabEnvVars, llTabAdvanced);
        } else {
            AppUtils.setupTabLayout(getContentView(), R.id.TabLayout, R.id.LLTabWinComponents, R.id.LLTabEnvVars, R.id.LLTabAdvanced);
            if (isDarkMode) {
                tabLayout.setBackgroundResource(R.drawable.tab_layout_background_dark);
            } else {
                tabLayout.setBackgroundResource(R.drawable.tab_layout_background);
            }
        }

        findViewById(R.id.BTExtraArgsMenu).setOnClickListener((v) -> {
            PopupMenu popupMenu = new PopupMenu(context, v);
            popupMenu.inflate(R.menu.extra_args_popup_menu);
            popupMenu.setOnMenuItemClickListener((menuItem) -> {
                String value = String.valueOf(menuItem.getTitle());
                String execArgs = etExecArgs.getText().toString();
                if (!execArgs.contains(value)) etExecArgs.setText(!execArgs.isEmpty() ? execArgs + " " + value : value);
                return true;
            });
            popupMenu.show();
        });

        String selectedDriver = sGraphicsDriver.getSelectedItem().toString();
        List<String> sGraphicsItemsList = new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.graphics_driver_entries)));
        sGraphicsDriver.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, sGraphicsItemsList));
        AppUtils.setSpinnerSelectionFromValue(sGraphicsDriver, selectedDriver);

        final Spinner sStartupSelection = findViewById(R.id.SStartupSelection);
        sStartupSelection.setSelection(Integer.parseInt(shortcut.getExtra("startupSelection", String.valueOf(shortcut.container.getStartupSelection()))));

        final Spinner sSharpnessEffect = findViewById(R.id.SSharpnessEffect);
        final SeekBar sbSharpnessLevel = findViewById(R.id.SBSharpnessLevel);
        final SeekBar sbSharpnessDenoise = findViewById(R.id.SBSharpnessDenoise);
        final TextView tvSharpnessLevel = findViewById(R.id.TVSharpnessLevel);
        final TextView tvSharpnessDenoise = findViewById(R.id.TVSharpnessDenoise);

        AppUtils.setSpinnerSelectionFromValue(sSharpnessEffect, shortcut.getExtra("sharpnessEffect", "None"));

        sbSharpnessLevel.setProgress(Integer.parseInt(shortcut.getExtra("sharpnessLevel", "100")));
        tvSharpnessLevel.setText(shortcut.getExtra("sharpnessLevel", "100") + "%");
        sbSharpnessLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSharpnessLevel.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        sbSharpnessDenoise.setProgress(Integer.parseInt(shortcut.getExtra("sharpnessDenoise", "100")));
        tvSharpnessDenoise.setText(shortcut.getExtra("sharpnessDenoise", "100") + "%");
        sbSharpnessDenoise.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSharpnessDenoise.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        final CPUListView cpuListView = findViewById(R.id.CPUListView);
        cpuListView.setCheckedCPUList(shortcut.getExtra("cpuList", shortcut.container.getCPUList(true)));
        final CPUListView cpuListViewWoW64 = findViewById(R.id.CPUListViewWoW64);
        cpuListViewWoW64.setCheckedCPUList(shortcut.getExtra("cpuListWoW64", shortcut.container.getCPUListWoW64(true)));

        setOnConfirmCallback(() -> {
            String name = etName.getText().toString().trim();
            boolean nameChanged = !shortcut.name.equals(name) && !name.isEmpty();

            // First, handle renaming if the name has changed
            if (nameChanged) {
                renameShortcut(name);
            }


            // Determine if renaming is needed
            boolean renamingSuccess = !nameChanged || new File(shortcut.file.getParent(), name + ".desktop").exists();

            if (renamingSuccess) {
                String graphicsDriver = StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem());
                String graphicsDriverConfig = vGraphicsDriverConfig.getTag().toString();
                String dxwrapper = StringUtils.parseIdentifier(sDXWrapper.getSelectedItem());
                String ddrawrapper = StringUtils.parseIdentifier(sDDrawrapper.getSelectedItem());
                String dxwrapperConfig = vDXWrapperConfig.getTag().toString();
                String audioDriver = StringUtils.parseIdentifier(sAudioDriver.getSelectedItem());
                String emulator = StringUtils.parseIdentifier(sEmulator.getSelectedItem());
                String midiSoundFont = sMIDISoundFont.getSelectedItemPosition() == 0 ? "" : sMIDISoundFont.getSelectedItem().toString();
                String screenSize = containerDetailFragment.getScreenSize(getContentView());

                int finalInputType = 0;
                finalInputType |= cbEnableXInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_XINPUT : 0;
                finalInputType |= cbEnableDInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_DINPUT : 0;
                finalInputType |= SDInputType.getSelectedItemPosition() == 0 ?  WinHandler.FLAG_DINPUT_MAPPER_STANDARD : WinHandler.FLAG_DINPUT_MAPPER_XINPUT;


                shortcut.putExtra("inputType", String.valueOf(finalInputType));

                boolean disabledXInput = cbDisabledXInput.isChecked();
                shortcut.putExtra("disableXinput", disabledXInput ? "1" : null);

                boolean touchscreenMode = cbSimTouchScreen.isChecked();
                shortcut.putExtra("simTouchScreen", touchscreenMode ? "1" : "0");

                String execArgs = etExecArgs.getText().toString();
                shortcut.putExtra("execArgs", !execArgs.isEmpty() ? execArgs : null);
                shortcut.putExtra("screenSize", !screenSize.equals(shortcut.container.getScreenSize()) ? screenSize : null);
                shortcut.putExtra("graphicsDriver", !graphicsDriver.equals(shortcut.container.getGraphicsDriver()) ? graphicsDriver : null);
                shortcut.putExtra("graphicsDriverConfig", !graphicsDriverConfig.equals(shortcut.container.getGraphicsDriverConfig()) ? graphicsDriverConfig : null);
                shortcut.putExtra("wrapperGraphicsDriverVersion", GraphicsDriverConfigDialog.getVersion(graphicsDriverConfig));
                shortcut.putExtra("dxwrapper", !dxwrapper.equals(shortcut.container.getDXWrapper()) ? dxwrapper : null);
                shortcut.putExtra("ddrawrapper", !ddrawrapper.equals(shortcut.container.getDDrawWrapper()) ? ddrawrapper : null);
                shortcut.putExtra("dxwrapperConfig", !dxwrapperConfig.equals(shortcut.container.getDXWrapperConfig()) ? dxwrapperConfig : null);
                shortcut.putExtra("audioDriver", !audioDriver.equals(shortcut.container.getAudioDriver()) ? audioDriver : null);
                shortcut.putExtra("emulator", !emulator.equals(shortcut.container.getEmulator()) ? emulator : null);
                shortcut.putExtra("midiSoundFont", !midiSoundFont.equals(shortcut.container.getMIDISoundFont()) ? midiSoundFont : null);
                shortcut.putExtra("forceFullscreen", cbForceFullscreen.isChecked() ? "1" : null);

                if (cbUseSecondaryExec.isChecked()) {
                    String secondaryExec = etSecondaryExec.getText().toString().trim();
                    String execDelay = etExecDelay.getText().toString().trim();
                    shortcut.putExtra("secondaryExec", !secondaryExec.isEmpty() ? secondaryExec : null);
                    shortcut.putExtra("execDelay", !execDelay.isEmpty() ? execDelay : null);
                } else {
                    shortcut.putExtra("secondaryExec", null);
                    shortcut.putExtra("execDelay", null);
                }

                shortcut.putExtra("fullscreenStretched", cbFullscreenStretched.isChecked() ? "1" : null);

                String wincomponents = containerDetailFragment.getWinComponents(getContentView());
                shortcut.putExtra("wincomponents", !wincomponents.equals(shortcut.container.getWinComponents()) ? wincomponents : null);

                String envVars = envVarsView.getEnvVars();
                shortcut.putExtra("envVars", !envVars.isEmpty() ? envVars : null);

                String box64Preset = Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset);
                shortcut.putExtra("box64Preset", !box64Preset.equals(shortcut.container.getBox64Preset()) ? box64Preset : null);

                shortcut.putExtra("rcfileId", rcfileIds[0] != shortcut.container.getRCFileId() ? Integer.toString(rcfileIds[0]) : null);

                Object selectedItem = sFEXCoreVersion.getSelectedItem();
                if (selectedItem != null) {
                    String fexcoreVersion = selectedItem.toString();
                    shortcut.putExtra("fexcoreVersion", !fexcoreVersion.equals(shortcut.container.getFEXCoreVersion()) ? fexcoreVersion : null);
                }

                String fexcorePreset = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
                shortcut.putExtra("fexcorePreset", !fexcorePreset.equals(shortcut.container.getFEXCorePreset()) ? fexcorePreset : null);

                byte startupSelection = (byte)sStartupSelection.getSelectedItemPosition();
                shortcut.putExtra("startupSelection", (startupSelection != shortcut.container.getStartupSelection()) ? String.valueOf(startupSelection) : null);

                String sharpeningEffect = sSharpnessEffect.getSelectedItem().toString();
                String sharpeningLevel = String.valueOf(sbSharpnessLevel.getProgress());
                String sharpeningDenoise = String.valueOf(sbSharpnessDenoise.getProgress());
                shortcut.putExtra("sharpnessEffect", sharpeningEffect);
                shortcut.putExtra("sharpnessLevel", sharpeningLevel);
                shortcut.putExtra("sharpnessDenoise", sharpeningDenoise);

                ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
                int controlsProfile = sControlsProfile.getSelectedItemPosition() > 0 ? profiles.get(sControlsProfile.getSelectedItemPosition() - 1).id : 0;
                shortcut.putExtra("controlsProfile", controlsProfile > 0 ? String.valueOf(controlsProfile) : null);

                String cpuList = cpuListView.getCheckedCPUListAsString();
                shortcut.putExtra("cpuList", !cpuList.equals(shortcut.container.getCPUList(true)) ? cpuList : null);

                String cpuListWoW64 = cpuListViewWoW64.getCheckedCPUListAsString();
                shortcut.putExtra("cpuListWoW64", !cpuListWoW64.equals(shortcut.container.getCPUListWoW64(true)) ? cpuListWoW64 : null);

                // Save all changes to the shortcut
                shortcut.saveData();
//
                // FEXCoreManager.saveFEXCoreSpinners(shortcut.container, sFEXCoreTSOPreset, sFEXCoreMultiBlock, sFEXCoreX87ReducedPrecision); 
            }
        });
    }

    @Override
    public void show() {
        super.show();
        if (steamStyledMode) {
            applySteamWindowLayout();
        }
    }

    private void applySteamDialogChrome() {
        View dialogView = getContentView();
        dialogView.setBackground(createSteamRootBackground());
        dialogView.setPadding(steamDp(8), steamDp(4), steamDp(8), steamDp(4));
        ViewGroup.LayoutParams rootLayoutParams = dialogView.getLayoutParams();
        if (rootLayoutParams != null) {
            rootLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            rootLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialogView.setLayoutParams(rootLayoutParams);
        }

        TextView titleView = findViewById(R.id.TVTitle);
        if (titleView != null) {
            titleView.setVisibility(View.GONE);
        }

        ImageView iconView = findViewById(R.id.IVIcon);
        if (iconView != null) {
            iconView.setVisibility(View.GONE);
        }

        LinearLayout titleBar = findViewById(R.id.LLTitleBar);
        if (titleBar != null) {
            titleBar.setVisibility(View.VISIBLE);
            titleBar.setPadding(0, 0, 0, steamDp(4));
            View divider = titleBar.getChildCount() > 1 ? titleBar.getChildAt(1) : null;
            if (divider != null) {
                divider.setBackgroundColor(withAlpha(STEAM_COLOR_PRIMARY, 180));
                ViewGroup.LayoutParams dividerLayoutParams = divider.getLayoutParams();
                dividerLayoutParams.height = steamDp(1);
                divider.setLayoutParams(dividerLayoutParams);
            }

            if (titleBar.getChildCount() > 0 && titleBar.getChildAt(0) instanceof LinearLayout headerRow) {
                headerRow.removeAllViews();
                headerRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                headerRow.setPadding(steamDp(2), 0, steamDp(2), steamDp(2));
                addSteamCloseButton(headerRow);
            }
        }

        Button cancelButton = findViewById(R.id.BTCancel);
        Button confirmButton = findViewById(R.id.BTConfirm);
        styleSteamActionButton(cancelButton, STEAM_COLOR_SURFACE_VARIANT, STEAM_COLOR_TEXT);
        styleSteamActionButton(confirmButton, STEAM_COLOR_PRIMARY, Color.WHITE);

        TextView bottomBarText = findViewById(R.id.TVBottomBarText);
        if (bottomBarText != null) {
            bottomBarText.setTextColor(STEAM_COLOR_SUBTEXT);
        }

        LinearLayout bottomBar = findViewById(R.id.LLBottomBar);
        if (bottomBar != null && bottomBar.getChildCount() > 0) {
            bottomBar.setPadding(0, steamDp(4), 0, 0);
            View divider = bottomBar.getChildAt(0);
            divider.setBackgroundColor(withAlpha(STEAM_COLOR_PRIMARY, 180));
        }
    }

    private void styleSteamActionButton(Button button, int fillColor, int textColor) {
        if (button == null) return;

        Drawable background = fillColor == STEAM_COLOR_PRIMARY
                ? createSteamGradientBackground(18f)
                : createSteamPanelBackground(fillColor, STEAM_COLOR_OUTLINE, 18f);
        button.setAllCaps(false);
        button.setMinHeight(steamDp(44));
        button.setPadding(steamDp(22), steamDp(10), steamDp(22), steamDp(10));
        button.setBackground(background);
        button.setTextColor(textColor);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
    }

    private void addSteamCloseButton(LinearLayout headerRow) {
        if (headerRow.findViewWithTag("steam_close_button") != null) {
            return;
        }

        ImageView closeButton = new ImageView(context);
        closeButton.setTag("steam_close_button");
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setColorFilter(STEAM_COLOR_TEXT);
        closeButton.setBackground(createSteamPanelBackground(STEAM_COLOR_SURFACE_VARIANT, STEAM_COLOR_OUTLINE, 20f));
        closeButton.setPadding(steamDp(10), steamDp(10), steamDp(10), steamDp(10));
        closeButton.setClickable(true);
        closeButton.setFocusable(true);
        closeButton.setOnClickListener(v -> dismiss());

        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(steamDp(42), steamDp(42));
        headerRow.addView(closeButton, closeParams);
    }

    private void detachSteamFrameFromOuterScroll(FrameLayout dialogFrame) {
        if (!(dialogFrame.getParent() instanceof ScrollView scrollView)) {
            return;
        }
        if (!(scrollView.getParent() instanceof LinearLayout dialogRoot)) {
            return;
        }

        int scrollIndex = dialogRoot.indexOfChild(scrollView);
        scrollView.removeView(dialogFrame);
        dialogRoot.removeViewAt(scrollIndex);
        dialogRoot.addView(dialogFrame, scrollIndex, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
    }

    private LinearLayout extractGeneralSection(LinearLayout llContent, TabLayout tabLayout) {
        if (generalSection != null) {
            return generalSection;
        }

        generalSection = new LinearLayout(context);
        generalSection.setOrientation(LinearLayout.VERTICAL);
        generalSection.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        while (llContent.getChildCount() > 0 && llContent.getChildAt(0) != tabLayout) {
            View child = llContent.getChildAt(0);
            llContent.removeViewAt(0);
            generalSection.addView(child);
        }

        if (tabLayout.getParent() == llContent) {
            llContent.removeView(tabLayout);
        }

        return generalSection;
    }

    private void prepareSteamComposeHostContent(
            LinearLayout llContent,
            TabLayout tabLayout,
            LinearLayout llTabWinComponents,
            LinearLayout llTabEnvVars,
            LinearLayout llTabAdvanced
    ) {
        LinearLayout extractedGeneralSection = extractGeneralSection(llContent, tabLayout);
        steamHostSectionTitles = new String[]{
                context.getString(R.string.general),
                context.getString(R.string.win_components),
                context.getString(R.string.environment_variables),
                context.getString(R.string.advanced)
        };
        steamHostSections.clear();
        steamHostSections.add(extractedGeneralSection);
        steamHostSections.add(llTabWinComponents);
        steamHostSections.add(llTabEnvVars);
        steamHostSections.add(llTabAdvanced);

        View titleBar = findViewById(R.id.LLTitleBar);
        if (titleBar != null) {
            titleBar.setVisibility(View.GONE);
        }
        View bottomBar = findViewById(R.id.LLBottomBar);
        if (bottomBar != null) {
            bottomBar.setVisibility(View.GONE);
        }
        if (llContent != null) {
            llContent.setVisibility(View.GONE);
        }

        for (View section : steamHostSections) {
            if (section == null) {
                continue;
            }
            section.setVisibility(View.VISIBLE);
            applySteamContentStyles(section);
            applySteamFieldsetStyles(section);
        }
    }

    private void buildSteamSplitLayout() {
        FrameLayout dialogFrame = getContentView().findViewById(R.id.FrameLayout);
        LinearLayout llContent = findViewById(R.id.LLContent);
        TabLayout tabLayout = findViewById(R.id.TabLayout);
        LinearLayout llTabWinComponents = findViewById(R.id.LLTabWinComponents);
        LinearLayout llTabEnvVars = findViewById(R.id.LLTabEnvVars);
        LinearLayout llTabAdvanced = findViewById(R.id.LLTabAdvanced);

        if (dialogFrame == null || llContent == null || tabLayout == null ||
                llTabWinComponents == null || llTabEnvVars == null || llTabAdvanced == null) {
            return;
        }

        ViewGroup.LayoutParams dialogFrameLayoutParams = dialogFrame.getLayoutParams();
        dialogFrameLayoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        dialogFrameLayoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        dialogFrame.setLayoutParams(dialogFrameLayoutParams);
        detachSteamFrameFromOuterScroll(dialogFrame);

        LinearLayout generalSection = extractGeneralSection(llContent, tabLayout);
        llContent.addView(generalSection, 0);
        llContent.setPadding(0, 0, 0, 0);

        ViewGroup currentParent = (ViewGroup) llContent.getParent();
        if (currentParent != null) {
            currentParent.removeView(llContent);
        }

        dialogFrame.removeAllViews();

        LinearLayout steamRoot = new LinearLayout(context);
        steamRoot.setOrientation(LinearLayout.HORIZONTAL);
        steamRoot.setPadding(steamDp(2), 0, steamDp(2), 0);
        steamRoot.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout sidebar = new LinearLayout(context);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(steamDp(18), steamDp(18), steamDp(18), steamDp(18));
        sidebar.setBackground(createSteamPanelBackground(STEAM_COLOR_SURFACE, STEAM_COLOR_OUTLINE, 24f));
        LinearLayout.LayoutParams sidebarParams = new LinearLayout.LayoutParams(steamDp(228), ViewGroup.LayoutParams.MATCH_PARENT);
        sidebarParams.rightMargin = steamDp(12);
        steamRoot.addView(sidebar, sidebarParams);

        TextView steamLabel = new TextView(context);
        steamLabel.setText(R.string.steam_library_tab_steam);
        steamLabel.setTextColor(STEAM_COLOR_PRIMARY);
        steamLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        steamLabel.setLetterSpacing(0.16f);
        sidebar.addView(steamLabel);

        TextView settingsLabel = new TextView(context);
        settingsLabel.setText(R.string.settings);
        settingsLabel.setTextColor(STEAM_COLOR_TEXT);
        settingsLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        settingsLabel.setPadding(0, steamDp(4), 0, 0);
        sidebar.addView(settingsLabel);

        View sidebarDivider = new View(context);
        sidebarDivider.setBackgroundColor(withAlpha(STEAM_COLOR_OUTLINE, 230));
        LinearLayout.LayoutParams sidebarDividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                steamDp(1)
        );
        sidebarDividerParams.topMargin = steamDp(14);
        sidebarDividerParams.bottomMargin = steamDp(14);
        sidebar.addView(sidebarDivider, sidebarDividerParams);

        LinearLayout navContainer = new LinearLayout(context);
        navContainer.setOrientation(LinearLayout.VERTICAL);
        sidebar.addView(navContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout rightPane = new LinearLayout(context);
        rightPane.setOrientation(LinearLayout.VERTICAL);
        rightPane.setPadding(steamDp(18), steamDp(18), steamDp(18), steamDp(12));
        rightPane.setBackground(createSteamPanelBackground(STEAM_COLOR_SURFACE_VARIANT, STEAM_COLOR_OUTLINE, 24f));
        steamRoot.addView(rightPane, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
        ));

        TextView rightCaption = new TextView(context);
        rightCaption.setText(R.string.steam_library_settings);
        rightCaption.setTextColor(STEAM_COLOR_PRIMARY);
        rightCaption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        rightCaption.setLetterSpacing(0.14f);
        rightPane.addView(rightCaption);

        TextView sectionTitleView = new TextView(context);
        sectionTitleView.setTextColor(STEAM_COLOR_TEXT);
        sectionTitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        sectionTitleView.setPadding(0, steamDp(4), 0, 0);
        rightPane.addView(sectionTitleView);

        TextView sectionSubtitleView = new TextView(context);
        sectionSubtitleView.setTextColor(STEAM_COLOR_SUBTEXT);
        sectionSubtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        sectionSubtitleView.setPadding(0, steamDp(6), 0, steamDp(12));
        sectionSubtitleView.setVisibility(View.GONE);
        rightPane.addView(sectionSubtitleView);

        View divider = new View(context);
        divider.setBackgroundColor(withAlpha(STEAM_COLOR_OUTLINE, 230));
        rightPane.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                steamDp(1)
        ));

        ScrollView contentScroll = new ScrollView(context);
        contentScroll.setFillViewport(true);
        contentScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout.LayoutParams contentScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        contentScrollParams.topMargin = steamDp(10);
        contentScroll.setPadding(0, 0, steamDp(4), 0);
        rightPane.addView(contentScroll, contentScrollParams);
        contentScroll.addView(llContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        String[] sectionTitles = new String[]{
                context.getString(R.string.general),
                context.getString(R.string.win_components),
                context.getString(R.string.environment_variables),
                context.getString(R.string.advanced)
        };

        List<View> sections = Arrays.asList(generalSection, llTabWinComponents, llTabEnvVars, llTabAdvanced);
        List<MaterialButton> navButtons = new ArrayList<>();

        for (int i = 0; i < sectionTitles.length; i++) {
            MaterialButton navButton = createSteamNavButton(sectionTitles[i]);
            LinearLayout.LayoutParams navButtonParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                navButtonParams.topMargin = steamDp(8);
            }
            final int sectionIndex = i;
            navButton.setOnClickListener(v -> selectSteamSection(sectionIndex, sectionTitles, sections, navButtons, sectionTitleView, sectionSubtitleView));
            navButtons.add(navButton);
            navContainer.addView(navButton, navButtonParams);
        }

        dialogFrame.addView(steamRoot);
        selectSteamSection(0, sectionTitles, sections, navButtons, sectionTitleView, sectionSubtitleView);
        applySteamContentStyles(llContent);
    }

    private MaterialButton createSteamNavButton(String label) {
        MaterialButton button = new MaterialButton(context);
        button.setTag("steam_nav_button");
        button.setText(label);
        button.setAllCaps(false);
        button.setCheckable(true);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(steamDp(72));
        button.setPadding(steamDp(18), steamDp(15), steamDp(18), steamDp(15));
        button.setCornerRadius(18);
        button.setStrokeWidth(steamDp(1));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        button.setLineSpacing(0f, 1.08f);
        button.setRippleColor(ColorStateList.valueOf(withAlpha(STEAM_COLOR_PRIMARY_ALT, 70)));
        return button;
    }

    private void selectSteamSection(
            int selectedIndex,
            String[] sectionTitles,
            List<View> sections,
            List<MaterialButton> navButtons,
            TextView sectionTitleView,
            TextView sectionSubtitleView
    ) {
        for (int i = 0; i < sections.size(); i++) {
            boolean selected = i == selectedIndex;
            View section = sections.get(i);
            MaterialButton navButton = navButtons.get(i);

            section.setVisibility(selected ? View.VISIBLE : View.GONE);
            navButton.setChecked(selected);
            navButton.setBackgroundTintList(ColorStateList.valueOf(
                    selected ? STEAM_COLOR_SELECTED : STEAM_COLOR_CARD
            ));
            navButton.setStrokeColor(ColorStateList.valueOf(
                    selected ? STEAM_COLOR_PRIMARY_ALT : STEAM_COLOR_OUTLINE
            ));
            navButton.setTextColor(selected ? STEAM_COLOR_TEXT : STEAM_COLOR_SUBTEXT);
            navButton.setElevation(selected ? steamDp(3) : 0f);
        }

        sectionTitleView.setText(sectionTitles[selectedIndex]);
        sectionSubtitleView.setVisibility(View.GONE);
    }

    private void applySteamWindowLayout() {
        Window window = getWindow();
        if (window == null) return;

        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.getDecorView().setPadding(0, 0, 0, 0);
        View root = getContentView();
        root.post(() -> {
            ViewGroup.LayoutParams params = root.getLayoutParams();
            if (params != null) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                root.setLayoutParams(params);
            }
        });
    }

    private GradientDrawable createSteamRootBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF111116, STEAM_COLOR_BACKGROUND, 0xFF050507}
        );
    }

    private GradientDrawable createSteamPanelBackground(int fillColor, int strokeColor, float cornerRadiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setColor(fillColor);
        background.setStroke(steamDp(1), strokeColor);
        background.setCornerRadius(UnitUtils.dpToPx(cornerRadiusDp));
        return background;
    }

    private GradientDrawable createSteamGradientBackground(float cornerRadiusDp) {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{STEAM_COLOR_PRIMARY_ALT, STEAM_COLOR_GRADIENT_END}
        );
        background.setCornerRadius(UnitUtils.dpToPx(cornerRadiusDp));
        return background;
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int steamDp(int dp) {
        return Math.round(UnitUtils.dpToPx(dp));
    }

    public int getSteamHostSectionCount() {
        return steamHostSections.size();
    }

    public String[] getSteamHostSectionTitles() {
        return steamHostSectionTitles.clone();
    }

    public View obtainSteamHostSection(int index) {
        if (index < 0 || index >= steamHostSections.size()) {
            return null;
        }

        View section = steamHostSections.get(index);
        if (section.getParent() instanceof ViewGroup parent) {
            parent.removeView(section);
        }
        section.setVisibility(View.VISIBLE);
        return section;
    }

    private void applySteamContentStyles(View root) {
        if (root == null) return;

        if (root instanceof TextInputLayout) {
            styleSteamTextInputLayout((TextInputLayout) root);
        } else if (root instanceof Spinner) {
            styleSteamSpinner((Spinner) root);
        } else if (root instanceof CheckBox) {
            styleSteamCheckBox((CheckBox) root);
        } else if (root instanceof MaterialButton) {
            styleSteamMaterialButton((MaterialButton) root);
        } else if (root instanceof EditText) {
            styleSteamEditText((EditText) root);
        } else if (root instanceof SeekBar) {
            styleSteamSeekBar((SeekBar) root);
        } else if (root instanceof ImageView) {
            styleSteamImageView((ImageView) root);
        } else if (root instanceof TextView && !(root instanceof Button)) {
            styleSteamTextView((TextView) root);
        }

        if (root instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                applySteamContentStyles(viewGroup.getChildAt(i));
            }
        }
    }

    private void applySteamFieldsetStyles(View root) {
        if (!(root instanceof ViewGroup group)) {
            return;
        }

        if (group instanceof FrameLayout && group.getChildCount() >= 2) {
            View panel = group.getChildAt(0);
            View label = group.getChildAt(1);
            if (panel instanceof LinearLayout panelLayout) {
                panelLayout.setBackground(createSteamPanelBackground(STEAM_COLOR_CARD, STEAM_COLOR_OUTLINE, 18f));
                panelLayout.setPadding(steamDp(12), steamDp(18), steamDp(12), steamDp(12));
                if (label instanceof TextView labelView) {
                    String headerText = labelView.getText() != null ? labelView.getText().toString() : "";
                    if (!headerText.isEmpty() && panelLayout.findViewWithTag("steam_section_header") == null) {
                        TextView sectionHeader = new TextView(context);
                        sectionHeader.setTag("steam_section_header");
                        sectionHeader.setText(headerText);
                        sectionHeader.setTextColor(STEAM_COLOR_PRIMARY);
                        sectionHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                        sectionHeader.setLetterSpacing(0.12f);
                        sectionHeader.setAllCaps(true);
                        sectionHeader.setPadding(0, 0, 0, steamDp(10));
                        panelLayout.addView(sectionHeader, 0);
                    }
                }
            }
            if (label instanceof TextView labelView) {
                labelView.setVisibility(View.GONE);
            }
        }

        for (int i = 0; i < group.getChildCount(); i++) {
            applySteamFieldsetStyles(group.getChildAt(i));
        }
    }

    private void styleSteamTextInputLayout(TextInputLayout layout) {
        ColorStateList hintColor = ColorStateList.valueOf(STEAM_COLOR_SUBTEXT);
        layout.setHintTextColor(hintColor);
        layout.setDefaultHintTextColor(hintColor);
        layout.setBoxBackgroundColor(STEAM_COLOR_SURFACE);
        layout.setBoxStrokeColor(STEAM_COLOR_OUTLINE);
    }

    private void styleSteamSpinner(Spinner spinner) {
        spinner.setBackground(createSteamPanelBackground(STEAM_COLOR_SURFACE, STEAM_COLOR_OUTLINE, 16f));
        spinner.setPopupBackgroundDrawable(createSteamPanelBackground(STEAM_COLOR_SURFACE_VARIANT, STEAM_COLOR_OUTLINE, 14f));
        spinner.setMinimumHeight(steamDp(50));
        spinner.setPadding(steamDp(14), 0, steamDp(14), 0);
        spinner.post(() -> tintSpinnerSelectedText(spinner));
        spinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP
                    && shouldUseSteamSpinnerDialog((Spinner) v)) {
                showSteamSpinnerDialog((Spinner) v);
                return true;
            }
            v.post(() -> tintSpinnerSelectedText((Spinner) v));
            return false;
        });
    }

    private void tintSpinnerSelectedText(Spinner spinner) {
        View selectedView = spinner.getSelectedView();
        if (selectedView instanceof TextView textView) {
            textView.setTextColor(STEAM_COLOR_TEXT);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        }
    }

    private boolean shouldUseSteamSpinnerDialog(Spinner spinner) {
        if (spinner.getId() == R.id.SGraphicsDriverAvailableExtensions) {
            return false;
        }
        SpinnerAdapter adapter = spinner.getAdapter();
        return adapter != null && adapter.getCount() > 0;
    }

    private void showSteamSpinnerDialog(Spinner spinner) {
        SpinnerAdapter adapter = spinner.getAdapter();
        if (adapter == null) {
            return;
        }

        ArrayList<String> items = new ArrayList<>();
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            items.add(item != null ? item.toString() : "");
        }

        ContentDialog dialog = new ContentDialog(context, 0, true);
        dialog.setTitle(findSteamSpinnerTitle(spinner));
        dialog.findViewById(R.id.BTConfirm).setVisibility(View.GONE);
        styleSteamActionButton(dialog.findViewById(R.id.BTCancel), STEAM_COLOR_CARD, STEAM_COLOR_TEXT);

        ListView listView = dialog.findViewById(R.id.ListView);
        listView.setVisibility(View.VISIBLE);
        listView.getLayoutParams().width = Math.max(steamDp(320), context.getResources().getDisplayMetrics().widthPixels - steamDp(64));
        listView.setSelector(new ColorDrawable(Color.TRANSPARENT));
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setAdapter(new ArrayAdapter<String>(dialog.getContext(), android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                boolean selected = position == spinner.getSelectedItemPosition();
                view.setTextColor(selected ? STEAM_COLOR_TEXT : STEAM_COLOR_SUBTEXT);
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
                view.setPadding(steamDp(18), steamDp(16), steamDp(18), steamDp(16));
                view.setBackground(createSteamPanelBackground(
                        selected ? STEAM_COLOR_SELECTED : STEAM_COLOR_SURFACE_VARIANT,
                        selected ? STEAM_COLOR_PRIMARY_ALT : STEAM_COLOR_OUTLINE,
                        14f
                ));
                AbsListView.LayoutParams params = new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                view.setLayoutParams(params);
                return view;
            }
        });
        listView.setDividerHeight(steamDp(8));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            spinner.setSelection(position);
            tintSpinnerSelectedText(spinner);
            dialog.dismiss();
        });

        dialog.show();

        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            dialogWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialogWindow.getDecorView().setPadding(0, 0, 0, 0);
            dialogWindow.setLayout(
                    Math.max(steamDp(340), context.getResources().getDisplayMetrics().widthPixels - steamDp(56)),
                    Math.min(context.getResources().getDisplayMetrics().heightPixels - steamDp(84), steamDp(560))
            );
        }

        View dialogView = dialog.getContentView();
        dialogView.setBackground(createSteamRootBackground());
        dialogView.setPadding(steamDp(10), steamDp(10), steamDp(10), steamDp(10));
        TextView dialogTitle = dialog.findViewById(R.id.TVTitle);
        if (dialogTitle != null) {
            dialogTitle.setTextColor(STEAM_COLOR_TEXT);
            dialogTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        }
        ImageView dialogIcon = dialog.findViewById(R.id.IVIcon);
        if (dialogIcon != null) {
            dialogIcon.setVisibility(View.GONE);
        }
    }

    private String findSteamSpinnerTitle(Spinner spinner) {
        CharSequence prompt = spinner.getPrompt();
        if (prompt != null && !prompt.toString().trim().isEmpty()) {
            return prompt.toString();
        }

        if (spinner.getParent() instanceof ViewGroup parent) {
            int spinnerIndex = parent.indexOfChild(spinner);
            for (int i = spinnerIndex - 1; i >= 0; i--) {
                String text = extractSteamLabel(parent.getChildAt(i));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }

        return context.getString(R.string.settings);
    }

    private String extractSteamLabel(View view) {
        if (view instanceof TextView textView) {
            String text = textView.getText() != null ? textView.getText().toString().trim() : "";
            if (!text.isEmpty()) {
                return text;
            }
        }
        if (view instanceof ViewGroup group) {
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                String text = extractSteamLabel(group.getChildAt(i));
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return "";
    }

    private void styleSteamCheckBox(CheckBox checkBox) {
        checkBox.setTextColor(STEAM_COLOR_TEXT);
        checkBox.setButtonTintList(ColorStateList.valueOf(STEAM_COLOR_PRIMARY));
    }

    private void styleSteamMaterialButton(MaterialButton button) {
        Object tag = button.getTag();
        if ("steam_nav_button".equals(tag)) {
            return;
        }

        button.setTextColor(STEAM_COLOR_TEXT);
        button.setStrokeWidth(steamDp(1));
        button.setCornerRadius(16);
        button.setElevation(0f);
        if (button.getText() != null && button.getText().length() > 0) {
            button.setBackgroundTintList(ColorStateList.valueOf(STEAM_COLOR_CARD));
            button.setStrokeColor(ColorStateList.valueOf(STEAM_COLOR_OUTLINE));
            button.setRippleColor(ColorStateList.valueOf(withAlpha(STEAM_COLOR_PRIMARY_ALT, 70)));
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(STEAM_COLOR_CARD));
            button.setStrokeColor(ColorStateList.valueOf(STEAM_COLOR_OUTLINE));
            button.setIconTint(ColorStateList.valueOf(STEAM_COLOR_TEXT));
            button.setRippleColor(ColorStateList.valueOf(withAlpha(STEAM_COLOR_PRIMARY_ALT, 70)));
        }
    }

    private void styleSteamEditText(EditText editText) {
        editText.setBackground(createSteamPanelBackground(STEAM_COLOR_SURFACE, STEAM_COLOR_OUTLINE, 16f));
        editText.setTextColor(STEAM_COLOR_TEXT);
        editText.setHintTextColor(STEAM_COLOR_SUBTEXT);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
        editText.setPadding(steamDp(14), steamDp(10), steamDp(14), steamDp(10));
    }

    private void styleSteamSeekBar(SeekBar seekBar) {
        ColorStateList tint = ColorStateList.valueOf(STEAM_COLOR_PRIMARY);
        seekBar.setProgressTintList(tint);
        seekBar.setThumbTintList(tint);
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(STEAM_COLOR_OUTLINE));
    }

    private void styleSteamImageView(ImageView imageView) {
        if (imageView.getId() == R.id.IVIcon) {
            imageView.setColorFilter(STEAM_COLOR_PRIMARY);
            return;
        }
        imageView.setColorFilter(STEAM_COLOR_TEXT);
    }

    private void styleSteamTextView(TextView textView) {
        if (textView.getId() == R.id.TVGraphicsDriverVersion) {
            textView.setTextColor(STEAM_COLOR_PRIMARY_ALT);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            return;
        }

        if (textView.getId() == R.id.TVSharpnessLevel || textView.getId() == R.id.TVSharpnessDenoise) {
            textView.setTextColor(STEAM_COLOR_TEXT);
            return;
        }

        if (textView.getParent() instanceof FrameLayout) {
            textView.setTextColor(STEAM_COLOR_TEXT);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            return;
        }

        float currentSp = textView.getTextSize() / context.getResources().getDisplayMetrics().scaledDensity;
        if (currentSp >= 20f) {
            textView.setTextColor(STEAM_COLOR_TEXT);
        } else if (currentSp >= 16f) {
            textView.setTextColor(withAlpha(STEAM_COLOR_TEXT, 235));
        } else {
            textView.setTextColor(STEAM_COLOR_SUBTEXT);
        }
    }

    // Utility method to apply styles to dynamically added TextViews based on their content
    private void applyFieldSetLabelStylesDynamically(ViewGroup rootView, boolean isDarkMode) {
        for (int i = 0; i < rootView.getChildCount(); i++) {
            View child = rootView.getChildAt(i);
            if (child instanceof ViewGroup) {
                applyFieldSetLabelStylesDynamically((ViewGroup) child, isDarkMode); // Recursive call for nested ViewGroups
            } else if (child instanceof TextView) {
                TextView textView = (TextView) child;
                // Apply the style based on the content of the TextView
                if (isFieldSetLabel(textView.getText().toString())) {
                    applyFieldSetLabelStyle(textView, isDarkMode);
                }
            }
        }
    }

    // Method to check if the text content matches any fieldset label
    private boolean isFieldSetLabel(String text) {
        return text.equalsIgnoreCase("DirectX") ||
                text.equalsIgnoreCase("General") ||
                text.equalsIgnoreCase("Box86/Box64") ||
                text.equalsIgnoreCase("Input Controls") ||
                text.equalsIgnoreCase("Game Controller") ||
                text.equalsIgnoreCase("System");
    }

    public void onWinComponentsViewsAdded(boolean isDarkMode) {
        // Apply styles to all dynamically added TextViews
        ViewGroup llContent = findViewById(R.id.LLContent);
        applyFieldSetLabelStylesDynamically(llContent, isDarkMode);
    }


    public static void loadScreenSizeSpinner(View view, String selectedValue, boolean isDarkMode) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);

        applyDarkThemeToEditText(view.findViewById(R.id.ETScreenWidth), isDarkMode);
        applyDarkThemeToEditText(view.findViewById(R.id.ETScreenHeight), isDarkMode);


        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = sScreenSize.getItemAtPosition(position).toString();
                llCustomScreenSize.setVisibility(value.equalsIgnoreCase("custom") ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean found = AppUtils.setSpinnerSelectionFromIdentifier(sScreenSize, selectedValue);
        if (!found) {
            AppUtils.setSpinnerSelectionFromValue(sScreenSize, "custom");
            String[] screenSize = selectedValue.split("x");
            ((EditText)view.findViewById(R.id.ETScreenWidth)).setText(screenSize[0]);
            ((EditText)view.findViewById(R.id.ETScreenHeight)).setText(screenSize[1]);
        }
    }

    private void applyDynamicStyles(View view, boolean isDarkMode) {

        // Update edit text
        EditText etName = view.findViewById(R.id.ETName);
        applyDarkThemeToEditText(etName, isDarkMode);

        // Update Spinners
        Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
        Spinner sDDrawrapper = view.findViewById(R.id.SDDrawrapper);
        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        Spinner sEmulatorSpinner = view.findViewById(R.id.SEmulator);
        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        Spinner sControlsProfile = view.findViewById(R.id.SControlsProfile);
        Spinner sRCFile = view.findViewById(R.id.SRCFile);
        Spinner sDInputType = view.findViewById(R.id.SDInputType);
        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        Spinner sStartupSelection = findViewById(R.id.SStartupSelection);
        MaterialButton btGraphicsDriverConfig = findViewById(R.id.BTGraphicsDriverConfig);
        MaterialButton btDXWrapperConfig = findViewById(R.id.BTDXWrapperConfig);
        

        // Set dark or light mode background for spinners
        sGraphicsDriver.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sDXWrapper.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sDDrawrapper.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sAudioDriver.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sEmulatorSpinner.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sBox64Preset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sControlsProfile.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sRCFile.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sDInputType.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sMIDISoundFont.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sBox64Version.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sFEXCorePreset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sFEXCoreVersion.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        sStartupSelection.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        int gearIconColor = isDarkMode ? Color.WHITE : Color.parseColor("#424242");
        btGraphicsDriverConfig.setIconTint(ColorStateList.valueOf(gearIconColor));
        btDXWrapperConfig.setIconTint(ColorStateList.valueOf(gearIconColor));

//        EditText etLC_ALL = view.findViewById(R.id.ETlcall);
        EditText etExecArgs = view.findViewById(R.id.ETExecArgs);

//        applyDarkThemeToEditText(etLC_ALL, isDarkMode);
        applyDarkThemeToEditText(etExecArgs, isDarkMode);
        styleCheckBoxTextColors(view, isDarkMode ? Color.WHITE : Color.BLACK);

    }

    private void styleCheckBoxTextColors(View root, int textColor) {
        if (root instanceof CheckBox) {
            ((CheckBox) root).setTextColor(textColor);
            return;
        }

        if (!(root instanceof ViewGroup)) {
            return;
        }

        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            styleCheckBoxTextColors(group.getChildAt(i), textColor);
        }
    }

    private void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
        if (isDarkMode) {
            // Apply dark mode-specific attributes
            textView.setTextColor(Color.parseColor("#cccccc")); // Set text color to #cccccc
            textView.setBackgroundColor(Color.parseColor("#424242")); // Set dark background color
        } else {
            // Apply light mode-specific attributes
            textView.setTextColor(Color.parseColor("#424242")); // Set text color to #bdbdbd
            textView.setBackgroundResource(R.color.window_background_color); // Set light background color
        }
    }

    private static void applyDarkThemeToEditText(EditText editText, boolean isDarkMode) {
        if (isDarkMode) {
            editText.setTextColor(Color.WHITE);
            editText.setHintTextColor(Color.GRAY);
            editText.setBackgroundResource(R.drawable.edit_text_dark);
        } else {
            editText.setTextColor(Color.BLACK);
            editText.setHintTextColor(Color.GRAY);
            editText.setBackgroundResource(R.drawable.edit_text);
        }
    }

    private void updateExtra(String extraName, String containerValue, String newValue) {
        String extraValue = shortcut.getExtra(extraName);
        if (extraValue.isEmpty() && containerValue.equals(newValue))
            return;
        shortcut.putExtra(extraName, newValue);
    }

    private void renameShortcut(String newName) {
        File parent = shortcut.file.getParentFile();
        File oldDesktopFile = shortcut.file; // Reference to the old file
        File newDesktopFile = new File(parent, newName + ".desktop");

        // Rename the desktop file if the new one doesn't exist
        if (!newDesktopFile.isFile() && oldDesktopFile.renameTo(newDesktopFile)) {
            // Successfully renamed, update the shortcut's file reference
            updateShortcutFileReference(newDesktopFile); // New helper method

            // As a precaution, delete any remaining old file
            deleteOldFileIfExists(oldDesktopFile);
        }

        // Rename link file if applicable
        File linkFile = new File(parent, shortcut.name + ".lnk");
        if (linkFile.isFile()) {
            File newLinkFile = new File(parent, newName + ".lnk");
            if (!newLinkFile.isFile()) linkFile.renameTo(newLinkFile);
        }

        if (fragment != null) {
            fragment.loadShortcutsList();
            fragment.updateShortcutOnScreen(newName, newName, shortcut.container.id, newDesktopFile.getAbsolutePath(),
                    ShortcutsFragment.createShortcutIcon(context, shortcut), shortcut.getExtra("uuid"));
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE_SELECT_CUSTOM_ICON || resultCode != Activity.RESULT_OK || data == null) return;
        Uri selectedImageUri = data.getData();
        if (selectedImageUri == null) return;
        try (InputStream inputStream = context.getContentResolver().openInputStream(selectedImageUri)) {
            Bitmap customIcon = BitmapFactory.decodeStream(inputStream);
            if (customIcon == null) return;
            shortcut.saveCustomIcon(customIcon);
            ImageView ivCustomIcon = findViewById(R.id.IVCustomIcon);
            Button btRemoveCustomIcon = findViewById(R.id.BTRemoveCustomIcon);
            if (ivCustomIcon != null) ivCustomIcon.setImageBitmap(customIcon);
            if (btRemoveCustomIcon != null) btRemoveCustomIcon.setVisibility(View.VISIBLE);
            ShortcutsFragment.updateShortcutOnScreen(context, shortcut);
        } catch (Exception e) {
            Log.e("ShortcutSettingsDialog", "Failed to load custom icon", e);
        }
    }

    // Method to ensure no old file remains
    private void deleteOldFileIfExists(File oldFile) {
        if (oldFile.exists()) {
            if (!oldFile.delete()) {
                Log.e("ShortcutSettingsDialog", "Failed to delete old file: " + oldFile.getPath());
            }
        }
    }

    // Update the shortcut's file reference to ensure saveData() writes to the correct file
    private void updateShortcutFileReference(File newFile) {
        try {
            Field fileField = Shortcut.class.getDeclaredField("file");
            fileField.setAccessible(true);
            fileField.set(shortcut, newFile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Log.e("ShortcutSettingsDialog", "Error updating shortcut file reference", e);
        }
    }


    private EnvVarsView createEnvVarsTab(boolean isDarkMode) {
        final View view = getContentView();
        final Context context = view.getContext();

        // Retrieve the existing EnvVarsView
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);

        envVarsView.setDarkMode(isDarkMode);

        // Set the environment variables in the existing EnvVarsView
        envVarsView.setEnvVars(new EnvVars(shortcut.getExtra("envVars")));

        // Set the click listener for adding new environment variables
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) ->
                new AddEnvVarDialog(context, envVarsView).show()
        );

        return envVarsView;
    }

    private void updateEmulatorConfigVisibility() {
        FrameLayout fexcoreFL = findViewById(R.id.fexcoreFrame);
        FrameLayout box86box64FL = findViewById(R.id.box86box64Frame);
        Spinner sEmulator = findViewById(R.id.SEmulator);
        Spinner sEmulator64 = findViewById(R.id.SEmulator64);

        String emulatorValue = null;
        if (sEmulator != null && sEmulator.isEnabled() && sEmulator.getSelectedItem() != null) {
            emulatorValue = sEmulator.getSelectedItem().toString();
        } else if (sEmulator64 != null && sEmulator64.getSelectedItem() != null) {
            emulatorValue = sEmulator64.getSelectedItem().toString();
        }

        boolean useFex = emulatorValue != null && emulatorValue.equalsIgnoreCase("FEXCore");
        if (fexcoreFL != null) {
            fexcoreFL.setVisibility(useFex ? View.VISIBLE : View.GONE);
        }
        if (box86box64FL != null) {
            box86box64FL.setVisibility(useFex ? View.GONE : View.VISIBLE);
        }
    }

    private void loadControlsProfileSpinner(Spinner spinner, String selectedValue) {
        final Context ctx = this.context;
        final ArrayList<ControlsProfile> profiles = inputControlsManager.getProfiles(true);
        ArrayList<String> values = new ArrayList<>();
        values.add(ctx.getString(R.string.none));

        int selectedPosition = 0;
        int selectedId = Integer.parseInt(selectedValue);
        for (int i = 0; i < profiles.size(); i++) {
            ControlsProfile profile = profiles.get(i);
            if (profile.id == selectedId) selectedPosition = i + 1;
            values.add(profile.getName());
        }

        spinner.setAdapter(new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(selectedPosition, false);
    }

    private void showInputWarning() {
        final Context ctx = this.context;
        ContentDialog.alert(ctx, R.string.enable_xinput_and_dinput_same_time, null);
    }

    private Activity getHostActivity() {
        if (fragment != null) return fragment.getActivity();
        return context instanceof Activity ? (Activity) context : null;
    }

    private void loadShortcutBox64VersionSpinner(Spinner spinner, boolean isArm64EC) {
        ContainerDetailFragment.loadBox64VersionSpinner(context, shortcut.container, contentsManager, spinner, isArm64EC);
        AppUtils.setSpinnerSelectionFromValue(spinner, shortcut.getExtra("box64Version", shortcut.container.getBox64Version()));
    }

    private void loadRemoteProfiles(Runnable onLoaded) {
        Activity hostActivity = getHostActivity();
        if (hostActivity == null) return;

        PreloaderDialog dialog = new PreloaderDialog(hostActivity);
        dialog.showOnUiThread(R.string.loading_component_versions);

        new Thread(() -> {
            SharedPreferences sp = new com.winlator.cmod.core.MmkvPreferences();
            String contentsURL = sp.getString("downloadable_contents_url", "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json");
            String json = Downloader.downloadString(contentsURL);
            dialog.closeOnUiThread();

            Activity currentActivity = getHostActivity();
            if (currentActivity == null) return;
            currentActivity.runOnUiThread(() -> {
                if (json == null) {
                    AppUtils.showToast(context, R.string.failed_to_load_remote_contents);
                    return;
                }
                contentsManager.setRemoteProfiles(json);
                onLoaded.run();
            });
        }).start();
    }

    public void showVersionDownloadDialog(com.winlator.cmod.contents.ContentProfile.ContentType type, String displayName, final com.winlator.cmod.core.Callback<String> onInstalled) {
        loadRemoteProfiles(() -> {
            List<com.winlator.cmod.contents.ContentProfile> downloadableProfiles = new ArrayList<>();
            for (com.winlator.cmod.contents.ContentProfile profile : contentsManager.getProfiles(type)) {
                if (profile.remoteUrl == null || profile.remoteUrl.isEmpty()) continue;
                if (com.winlator.cmod.contents.ContentsManager.getInstallDir(context, profile).exists()) continue;
                downloadableProfiles.add(profile);
            }

            if (downloadableProfiles.isEmpty()) {
                com.winlator.cmod.core.AppUtils.showToast(context, context.getString(R.string.all_component_versions_installed, displayName));
                return;
            }

            String[] items = new String[downloadableProfiles.size()];
            for (int i = 0; i < downloadableProfiles.size(); i++) {
                items[i] = getVersionSpinnerValue(downloadableProfiles.get(i));
            }

            new android.app.AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.download_component_title, displayName, downloadableProfiles.size()))
                    .setItems(items, (dialog, which) -> {
                        if (which < 0 || which >= downloadableProfiles.size()) return;
                        com.winlator.cmod.contents.ContentProfile selectedProfile = downloadableProfiles.get(which);
                        String selectedValue = getVersionSpinnerValue(selectedProfile);
                        downloadAndInstallShortcutContent(selectedProfile, () -> {
                            contentsManager.syncContents();
                            onInstalled.call(selectedValue);
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void showShortcutVersionDownloadDialog(ContentProfile.ContentType type, String displayName, Spinner spinner, Runnable refreshSpinner) {
        loadRemoteProfiles(() -> {
            List<ContentProfile> downloadableProfiles = new ArrayList<>();
            for (ContentProfile profile : contentsManager.getProfiles(type)) {
                if (profile.remoteUrl == null || profile.remoteUrl.isEmpty()) continue;
                if (ContentsManager.getInstallDir(context, profile).exists()) continue;
                downloadableProfiles.add(profile);
            }

            if (downloadableProfiles.isEmpty()) {
                AppUtils.showToast(context, context.getString(R.string.all_component_versions_installed, displayName));
                return;
            }

            String[] items = new String[downloadableProfiles.size()];
            for (int i = 0; i < downloadableProfiles.size(); i++) {
                items[i] = getVersionSpinnerValue(downloadableProfiles.get(i));
            }

            new AlertDialog.Builder(context)
                    .setTitle(context.getString(R.string.download_component_title, displayName, downloadableProfiles.size()))
                    .setItems(items, (dialog, which) -> {
                        if (which < 0 || which >= downloadableProfiles.size()) return;
                        ContentProfile selectedProfile = downloadableProfiles.get(which);
                        String selectedValue = getVersionSpinnerValue(selectedProfile);
                        downloadAndInstallShortcutContent(selectedProfile, () -> {
                            contentsManager.syncContents();
                            refreshSpinner.run();
                            AppUtils.setSpinnerSelectionFromValue(spinner, selectedValue);
                        });
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    private void downloadAndInstallShortcutContent(ContentProfile profile, Runnable onInstalled) {
        Activity hostActivity = getHostActivity();
        if (profile.remoteUrl == null || hostActivity == null) return;

        PreloaderDialog downloadDialog = new PreloaderDialog(hostActivity);
        downloadDialog.showOnUiThread(R.string.downloading_file);

        new Thread(() -> {
            File output = new File(context.getCacheDir(), "shortcut_content_" + System.currentTimeMillis());
            if (!Downloader.downloadFile(profile.remoteUrl, output)) {
                downloadDialog.closeOnUiThread();
                Activity currentActivity = getHostActivity();
                if (currentActivity == null) return;
                currentActivity.runOnUiThread(() -> AppUtils.showToast(context, R.string.unable_to_install_content));
                return;
            }

            downloadDialog.closeOnUiThread();
            installShortcutContentFromUri(Uri.fromFile(output), profile, () -> {
                if (output.exists()) output.delete();
                onInstalled.run();
            }, () -> {
                if (output.exists()) output.delete();
            });
        }).start();
    }

    private void installShortcutContentFromUri(Uri uri, ContentProfile expectedProfile, Runnable onInstalled, Runnable onFailed) {
        Activity hostActivity = getHostActivity();
        if (hostActivity == null) return;

        PreloaderDialog dialog = new PreloaderDialog(hostActivity);
        dialog.showOnUiThread(R.string.installing_content);

        ContentsManager.OnInstallFinishedCallback callback = new ContentsManager.OnInstallFinishedCallback() {
            private boolean isExtracting = true;

            @Override
            public void onFailed(ContentsManager.InstallFailedReason reason, Exception e) {
                int msgId = switch (reason) {
                    case ERROR_BADTAR -> R.string.file_cannot_be_recognied;
                    case ERROR_NOPROFILE -> R.string.profile_not_found_in_content;
                    case ERROR_BADPROFILE -> R.string.profile_cannot_be_recognized;
                    case ERROR_EXIST -> R.string.content_already_exist;
                    case ERROR_MISSINGFILES -> R.string.content_is_incomplete;
                    case ERROR_UNTRUSTPROFILE -> R.string.content_cannot_be_trusted;
                    default -> R.string.unable_to_install_content;
                };

                Activity currentActivity = getHostActivity();
                if (currentActivity == null) return;
                currentActivity.runOnUiThread(() -> ContentDialog.alert(context,
                        context.getString(R.string.install_failed) + ": " + context.getString(msgId), () -> {
                            dialog.closeOnUiThread();
                            onFailed.run();
                        }));
            }

            @Override
            public void onSucceed(ContentProfile installedProfile) {
                installedProfile.type = expectedProfile.type;
                installedProfile.verName = expectedProfile.verName;
                installedProfile.verCode = expectedProfile.verCode;

                if (isExtracting) {
                    ContentsManager.OnInstallFinishedCallback callback1 = this;
                    Activity currentActivity = getHostActivity();
                    if (currentActivity == null) return;
                    currentActivity.runOnUiThread(() -> {
                        ContentInfoDialog infoDialog = new ContentInfoDialog(context, installedProfile);
                        ((TextView) infoDialog.findViewById(R.id.BTConfirm)).setText(R.string._continue);
                        infoDialog.setOnConfirmCallback(() -> {
                            isExtracting = false;
                            List<ContentProfile.ContentFile> untrustedFiles = contentsManager.getUnTrustedContentFiles(installedProfile);
                            if (!untrustedFiles.isEmpty()) {
                                ContentUntrustedDialog untrustedDialog = new ContentUntrustedDialog(context, untrustedFiles);
                                untrustedDialog.setOnCancelCallback(() -> {
                                    dialog.closeOnUiThread();
                                    onFailed.run();
                                });
                                untrustedDialog.setOnConfirmCallback(() -> contentsManager.finishInstallContent(installedProfile, callback1));
                                untrustedDialog.show();
                            } else {
                                contentsManager.finishInstallContent(installedProfile, callback1);
                            }
                        });
                        infoDialog.setOnCancelCallback(() -> {
                            dialog.closeOnUiThread();
                            onFailed.run();
                        });
                        infoDialog.show();
                    });
                } else {
                    dialog.closeOnUiThread();
                    Activity currentActivity = getHostActivity();
                    if (currentActivity == null) return;
                    currentActivity.runOnUiThread(() -> {
                        ContentDialog.alert(context, R.string.content_installed_success, null);
                        contentsManager.syncContents();
                        onInstalled.run();
                    });
                }
            }
        };

        new Thread(() -> contentsManager.extraContentFile(uri, callback)).start();
    }

    private String getVersionSpinnerValue(ContentProfile profile) {
        String entryName = ContentsManager.getEntryName(profile);
        int firstDashIndex = entryName.indexOf('-');
        return firstDashIndex >= 0 ? entryName.substring(firstDashIndex + 1) : entryName;
    }

    public static void loadBox64VersionSpinner(Context context, ContentsManager contentsManager, Spinner spinner, String selectedVersion) {
        String[] originalItems = context.getResources().getStringArray(R.array.wowbox64_version_entries);
        List<String> itemList = new ArrayList<>(Arrays.asList(originalItems));
        for (ContentProfile profile : contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64)) {
            if (!(profile.remoteUrl == null || ContentsManager.getInstallDir(context, profile).exists())) continue;
            String entryName = ContentsManager.getEntryName(profile);
            int firstDashIndex = entryName.indexOf('-');
            itemList.add(entryName.substring(firstDashIndex + 1));
        }
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, itemList));
        AppUtils.setSpinnerSelectionFromValue(spinner, selectedVersion);
    }
    
    public void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, final View vGraphicsDriverConfig, String selectedGraphicsDriver, String selectedDXWrapper) {
        final Context context = sGraphicsDriver.getContext();
        
        ContainerDetailFragment.updateGraphicsDriverSpinner(context, sGraphicsDriver);
        
        final String[] dxwrapperEntries = context.getResources().getStringArray(R.array.dxwrapper_entries);
        
        Runnable update = () -> {
            String graphicsDriver = StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem());
            String graphicsDriverConfig = vGraphicsDriverConfig.getTag().toString();

            tvGraphicsDriverVersion.setText(GraphicsDriverConfigDialog.getVersion(graphicsDriverConfig));

            vGraphicsDriverConfig.setOnClickListener((v) -> {
                new GraphicsDriverConfigDialog(vGraphicsDriverConfig, graphicsDriver, tvGraphicsDriverVersion).show();
            });

            ArrayList<String> items = new ArrayList<>();
            for (String value : dxwrapperEntries) {
                    items.add(value);
            }
            sDXWrapper.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, items.toArray(new String[0])));
            AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, selectedDXWrapper);
        };

        sGraphicsDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, selectedGraphicsDriver);
        update.run();
    }
}
