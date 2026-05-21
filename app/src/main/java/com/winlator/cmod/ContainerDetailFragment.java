package com.winlator.cmod;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.tabs.TabLayout;
import com.winlator.cmod.R;
import com.winlator.cmod.box86_64.Box86_64EditPresetDialog;
import com.winlator.cmod.box86_64.Box86_64Preset;
import com.winlator.cmod.box86_64.Box86_64PresetManager;
import com.winlator.cmod.box86_64.rc.RCManager;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contentdialog.AddEnvVarDialog;
import com.winlator.cmod.contentdialog.AudioDriverConfigDialog;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.ContentInfoDialog;
import com.winlator.cmod.contentdialog.ContentUntrustedDialog;
import com.winlator.cmod.contentdialog.DXVKConfigDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.contentdialog.ShortcutSettingsDialog;
import com.winlator.cmod.contentdialog.VKD3DConfigDialog;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.PreloaderDialog;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;
import com.winlator.cmod.core.WineRegistryEditor;
import com.winlator.cmod.core.WineThemeManager;
import com.winlator.cmod.fexcore.FEXCoreEditPresetDialog;
import com.winlator.cmod.fexcore.FEXCoreManager;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.midi.MidiManager;
import com.winlator.cmod.widget.CPUListView;
import com.winlator.cmod.widget.ColorPickerView;
import com.winlator.cmod.widget.EnvVarsView;
import com.winlator.cmod.widget.ImagePickerView;
import com.winlator.cmod.winhandler.WinHandler;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xserver.XKeycode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ContainerDetailFragment extends Fragment {

    private static final String TAG = "FileUtils";
    private static final String CONTAINER_PROFILE_TYPE = "container_settings_profile";
    private static final int CONTAINER_PROFILE_SCHEMA_VERSION = 2;
    private static final String CONTAINER_PROFILE_ARCHIVE_EXTENSION = ".wprofile.zip";

    private ContainerManager manager;
    private ContentsManager contentsManager;
    private final int containerId;
    private static Container container;
    private PreloaderDialog preloaderDialog;
    private JSONArray gpuCards;
    private Callback<String> openDirectoryCallback;

    private static boolean isDarkMode;

    private ImageFs imageFs;

    private ActivityResultLauncher<String[]> importBox64PresetLauncher;
    private ActivityResultLauncher<String[]> importFexcorePresetLauncher;
    private ActivityResultLauncher<String[]> importContainerProfileLauncher;
    private ActivityResultLauncher<String> exportContainerProfileLauncher;
    private Spinner box64PresetSpinner;
    private Spinner fexcorePresetSpinner;
    private EnvVarsView envVarsView;
    private int selectedRcFileId;
    private String pendingContainerProfileJson;

    private View rootView;
    private Spinner wineVersionSpinner;
    private Spinner box64VersionSpinner;
    private Spinner fexcoreVersionSpinner;

    public ContainerDetailFragment() {
        this(0);
    }

    public ContainerDetailFragment(int containerId) {
        this.containerId = containerId;
    }

    private static final String[] SDL2_ENV_VARS = {
            "SDL_JOYSTICK_WGI=0",
            "SDL_XINPUT_ENABLED=1",
            "SDL_JOYSTICK_RAWINPUT=0",
            "SDL_JOYSTICK_HIDAPI=1",
            "SDL_DIRECTINPUT_ENABLED=0",
            "SDL_JOYSTICK_ALLOW_BACKGROUND_EVENTS=1",
            "SDL_HINT_FORCE_RAISEWINDOW=0",
            "SDL_ALLOW_TOPMOST=0",
            "SDL_MOUSE_FOCUS_CLICKTHROUGH=1"
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        preloaderDialog = new PreloaderDialog(getActivity());

        importBox64PresetLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), (uri) -> {
            if (uri == null) return;
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                if (is == null) return;
                Box86_64PresetManager.importPreset("box64", requireContext(), is);
                is.close();
                if (box64PresetSpinner != null) {
                    Box86_64PresetManager.loadSpinner("box64", box64PresetSpinner, Box86_64PresetManager.getSpinnerSelectedId(box64PresetSpinner));
                    box64PresetSpinner.setSelection(box64PresetSpinner.getCount() - 1);
                }
            }
            catch (Exception ignored) {
            }
        });

        importFexcorePresetLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), (uri) -> {
            if (uri == null) return;
            try {
                InputStream is = requireContext().getContentResolver().openInputStream(uri);
                if (is == null) return;
                FEXCorePresetManager.importPreset(requireContext(), is);
                is.close();
                if (fexcorePresetSpinner != null) {
                    FEXCorePresetManager.loadSpinner(fexcorePresetSpinner, FEXCorePresetManager.getSpinnerSelectedId(fexcorePresetSpinner));
                    fexcorePresetSpinner.setSelection(fexcorePresetSpinner.getCount() - 1);
                }
            }
            catch (Exception ignored) {
            }
        });

        importContainerProfileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), (uri) -> {
            if (uri == null) return;
            importContainerProfile(uri);
        });

        exportContainerProfileLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), (uri) -> {
            if (uri == null || pendingContainerProfileJson == null) return;
            try (OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri, "w")) {
                if (outputStream == null) {
                    AppUtils.showToast(requireContext(), R.string.failed_to_export_container_profile);
                    return;
                }
                writeContainerProfileArchive(outputStream, new JSONObject(pendingContainerProfileJson));
                AppUtils.showToast(requireContext(), R.string.container_profile_exported);
            }
            catch (Exception e) {
                Log.e(TAG, "Failed to export container profile", e);
                AppUtils.showToast(requireContext(), R.string.failed_to_export_container_profile);
            }
            finally {
                pendingContainerProfileJson = null;
            }
        });

        try {
            gpuCards = new JSONArray(FileUtils.readString(getContext(), "gpu_cards.json"));
        }
        catch (JSONException e) {}
    }

    private static void applyFieldSetLabelStyle(TextView textView, boolean isDarkMode) {
        if (isDarkMode) {
            // Apply dark mode-specific attributes
            textView.setTextColor(Color.parseColor("#cccccc"));
            textView.setBackgroundResource(R.color.window_background_color_dark);
        } else {
            // Apply light mode-specific attributes
            textView.setTextColor(Color.parseColor("#424242"));
            textView.setBackgroundResource(R.color.window_background_color);
        }
    }


    private void applyDynamicStyles(View view, boolean isDarkMode) {


        // Update Spinners
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        sScreenSize.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sWineVersion = view.findViewById(R.id.SWineVersion);
        sWineVersion.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        sGraphicsDriver.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
        sDXWrapper.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sDDrawrapper = view.findViewById(R.id.SDDrawrapper);
        sDDrawrapper.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        sAudioDriver.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        sEmulator64.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sEmulator = view.findViewById(R.id.SEmulator);
        sEmulator.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        sMIDISoundFont.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        // Update Wine Configuration Tab Spinner styles
        // Desktop
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        sDesktopTheme.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        sDesktopBackgroundType.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        // Registry Keys
        Spinner SCSMT = view.findViewById(R.id.SCSMT);
        SCSMT.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner SGPUName = view.findViewById(R.id.SGPUName);
        SGPUName.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sOffscreenRenderingMode = view.findViewById(R.id.SOffscreenRenderingMode);
        sOffscreenRenderingMode.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sStrictShaderMath = view.findViewById(R.id.SStrictShaderMath);
        sStrictShaderMath.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sVideoMemorySize = view.findViewById(R.id.SVideoMemorySize);
        sVideoMemorySize.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
        sMouseWarpOverride.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        // Win Components
        // Handled in createWinComponentsTab

        // Update Advanced Tab Spinner styles
        Spinner SDInputType = view.findViewById(R.id.SDInputType);
        SDInputType.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        sBox64Preset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        sBox64Version.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        sFEXCoreVersion.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        if (sFEXCorePreset != null) {
            sFEXCorePreset.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);
        }
        

        Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        sStartupSelection.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        Spinner sRCFile = view.findViewById(R.id.SRCFile);
        sRCFile.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

    }

    private void applyDynamicStylesRecursively(View view, boolean isDarkMode) {
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                applyDynamicStylesRecursively(child, isDarkMode);
            }
        } else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if ("desktop".equals(textView.getText().toString())) { // Check for specific text if needed
                textView.setTextAppearance(getContext(), isDarkMode ? R.style.FieldSetLabel_Dark : R.style.FieldSetLabel);
            }
        }
    }

    private static int resolveThemeColor(@NonNull Context context, int attr, int fallbackColor) {
        TypedValue outValue = new TypedValue();
        boolean resolved = context.getTheme().resolveAttribute(attr, outValue, true);
        if (!resolved) return fallbackColor;
        if (outValue.resourceId != 0) {
            try {
                return context.getResources().getColor(outValue.resourceId);
            } catch (Exception ignored) {
                return fallbackColor;
            }
        }
        return outValue.data;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                Uri uri = data.getData();
                Log.d(TAG, "URI obtained in onActivityResult: " + uri.toString());
                String path = FileUtils.getFilePathFromUri(getContext(), uri);
                Log.d(TAG, "File path in onActivityResult: " + path);
                if (path != null) {
                    if (openDirectoryCallback != null) {
                        openDirectoryCallback.call(path);
                    }
                } else {
                    Toast.makeText(getContext(), "Invalid directory selected", Toast.LENGTH_SHORT).show();
                }
            }
            openDirectoryCallback = null;
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(isEditMode() ? R.string.edit_container : R.string.new_container);

        // Find TextViews by ID and apply dynamic styles
        TextView desktopLabel = view.findViewById(R.id.TVDesktop);
        applyFieldSetLabelStyle(desktopLabel, isDarkMode);  // Apply the dark or light mode styles

        TextView registryKeysLabel = view.findViewById(R.id.TVRegistryKeys);
        applyFieldSetLabelStyle(registryKeysLabel, isDarkMode);  // Apply the dark or light mode styles

        // Win Components TextViews
        TextView directXLabel = view.findViewById(R.id.TVDirectX);
        applyFieldSetLabelStyle(directXLabel, isDarkMode);  // Apply the dark or light mode styles

        TextView generalLabel = view.findViewById(R.id.TVGeneral);
        applyFieldSetLabelStyle(generalLabel, isDarkMode);  // Apply the dark or light mode styles

        // Advanced Tab TextViews
        TextView box86box64Label = view.findViewById(R.id.TVBox86Box64);
        applyFieldSetLabelStyle(box86box64Label, isDarkMode);  // Apply the dark or light mode styles
        
        TextView fexCoreLabel = view.findViewById(R.id.TVFEXCore);
        applyFieldSetLabelStyle(fexCoreLabel, isDarkMode);

        TextView systemLabel = view.findViewById(R.id.TVSystem);
        applyFieldSetLabelStyle(systemLabel, isDarkMode);  // Apply the dark or light mode styles

        TextView gameControllerLabel = view.findViewById(R.id.TVGameController);
        applyFieldSetLabelStyle(gameControllerLabel, isDarkMode);  // Apply the dark or light mode styles

    }

    public boolean isEditMode() {
        return container != null;
    }

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup root, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final View view = inflater.inflate(R.layout.container_detail_fragment, root, false);
        rootView = view;

        // Determine if dark mode is enabled
        isDarkMode = preferences.getBoolean("dark_mode", false); // false = светлая тема по умолчанию

        // Apply dynamic styles
        applyDynamicStyles(view, isDarkMode);

        // Apply dynamic styles recursively
//        applyDynamicStylesRecursively(view, isDarkMode);

        manager = new ContainerManager(context);
        container = containerId > 0 ? manager.getContainerById(containerId) : null;
        contentsManager = new ContentsManager(context);
        contentsManager.syncContents();



        boolean isLegacyModeEnabled = preferences.getBoolean("legacy_mode_enabled", false);


        final EditText etName = view.findViewById(R.id.ETName);

        final Spinner sWineVersion = view.findViewById(R.id.SWineVersion);
        wineVersionSpinner = sWineVersion;



        // Ensure the Wine version layout is visible
        final LinearLayout llWineVersion = view.findViewById(R.id.LLWineVersion);
        llWineVersion.setVisibility(View.VISIBLE);

        // Set container name and graphics driver version based on mode
        if (isEditMode()) {
            etName.setText(container.getName());
        } else {
            etName.setText(getString(R.string.container) + "-" + manager.getNextContainerId());
        }

        final Spinner sBox64Version = view.findViewById(R.id.SBox64Version);
        box64VersionSpinner = sBox64Version;

        loadWineVersionSpinner(view, sWineVersion, sBox64Version);

        loadScreenSizeSpinner(view, isEditMode() ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE);

        final Spinner sGraphicsDriver = view.findViewById(R.id.SGraphicsDriver);
        
        final Spinner sDXWrapper = view.findViewById(R.id.SDXWrapper);
        final Spinner sDDrawrapper = view.findViewById(R.id.SDDrawrapper);

        final View vDXWrapperConfig = view.findViewById(R.id.BTDXWrapperConfig);
        vDXWrapperConfig.setTag(isEditMode() ? container.getDXWrapperConfig() : Container.DEFAULT_DXWRAPPERCONFIG);

        final View vGraphicsDriverConfig = view.findViewById(R.id.BTGraphicsDriverConfig);
        vGraphicsDriverConfig.setTag(isEditMode() ? container.getGraphicsDriverConfig() : Container.DEFAULT_GRAPHICSDRIVERCONFIG);

        setupDXWrapperSpinner(sDXWrapper, vDXWrapperConfig);
        setupDDrawSpinner(sDDrawrapper, isEditMode() ? container.getDDrawWrapper() : Container.DEFAULT_DDRAWRAPPER);
        loadGraphicsDriverSpinner(sGraphicsDriver, sDXWrapper, vGraphicsDriverConfig,
                isEditMode() ? container.getGraphicsDriver() : Container.DEFAULT_GRAPHICS_DRIVER,
                isEditMode() ? container.getDXWrapper() : Container.DEFAULT_DXWRAPPER);

        view.findViewById(R.id.BTHelpDXWrapper).setOnClickListener((v) -> AppUtils.showHelpBox(context, v, R.string.dxwrapper_help_content));
        Spinner sAudioDriver = view.findViewById(R.id.SAudioDriver);
        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, isEditMode() ? container.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER);

        final View vAudioDriverConfig = view.findViewById(R.id.BTAudioDriverConfig);
        vAudioDriverConfig.setTag(isEditMode() ? container.getAudioDriverConfig() : "performanceMode=1,volume=1.0,latencyMillis=20");
        vAudioDriverConfig.setOnClickListener((v) -> new AudioDriverConfigDialog(v).show());

        Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
        Spinner sEmulator = view.findViewById(R.id.SEmulator);
        AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, isEditMode() ? container.getEmulator() : Container.DEFAULT_EMULATOR);

        AdapterView.OnItemSelectedListener emulatorListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                updateEmulatorConfigVisibility(view);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateEmulatorConfigVisibility(view);
            }
        };
        sEmulator.setOnItemSelectedListener(emulatorListener);
        if (sEmulator64 != null) {
            sEmulator64.setOnItemSelectedListener(emulatorListener);
        }
        updateEmulatorConfigVisibility(view);

        Spinner sMIDISoundFont = view.findViewById(R.id.SMIDISoundFont);
        MidiManager.loadSFSpinner(sMIDISoundFont);
        AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, isEditMode() ? container.getMIDISoundFont() : "");



        final CheckBox cbFullscreenStretched = view.findViewById(R.id.CBFullscreenStretched);
        cbFullscreenStretched.setChecked(isEditMode() && container.isFullscreenStretched());

        // Existing declarations of UI components and variables
        final Runnable showInputWarning = () -> ContentDialog.alert(context, R.string.enable_xinput_and_dinput_same_time, null);
        final CheckBox cbEnableXInput = view.findViewById(R.id.CBEnableXInput);
        final CheckBox cbEnableDInput = view.findViewById(R.id.CBEnableDInput);
        final View llDInputType = view.findViewById(R.id.LLDinputMapperType);
        final View btHelpXInput = view.findViewById(R.id.BTXInputHelp);
        final View btHelpDInput = view.findViewById(R.id.BTDInputHelp);
        final Spinner SDInputType = view.findViewById(R.id.SDInputType);

        // Check if we are in edit mode to set input type accordingly
        int inputType = isEditMode() ? container.getInputType() : WinHandler.DEFAULT_INPUT_TYPE;

        // Initialize the TextView for the legacy mode message
        TextView tvLegacyInputMessage = view.findViewById(R.id.TVLegacyInputMessage);

        if (!isLegacyModeEnabled) {

            // Set visibility of legacy mode message
            tvLegacyInputMessage.setVisibility(View.GONE); // Hide message when not in legacy mode

            // New logic for enabling XInput and DInput
            cbEnableXInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT);
            cbEnableDInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT);

            cbEnableDInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
                llDInputType.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (isChecked && cbEnableXInput.isChecked())
                    showInputWarning.run();
            });

            cbEnableXInput.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && cbEnableDInput.isChecked())
                    showInputWarning.run();
            });

            SDInputType.setSelection(((inputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD) ? 0 : 1);
            llDInputType.setVisibility(cbEnableDInput.isChecked() ? View.VISIBLE : View.GONE);

            btHelpXInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_xinput));
            btHelpDInput.setOnClickListener(v -> AppUtils.showHelpBox(context, v, R.string.help_dinput));
        } else {
            // Legacy mode handling: disable or hide input-related UI elements
            cbEnableXInput.setVisibility(View.GONE);
            cbEnableDInput.setVisibility(View.GONE);
            llDInputType.setVisibility(View.GONE);
            btHelpXInput.setVisibility(View.GONE);
            btHelpDInput.setVisibility(View.GONE);
            SDInputType.setVisibility(View.GONE);

            // Show the legacy input mode message
            tvLegacyInputMessage.setVisibility(View.VISIBLE);

            // Set inputType to default or legacy-compatible setting
            inputType = WinHandler.DEFAULT_INPUT_TYPE;
        }

        final CheckBox cbSdl2Toggle = view.findViewById(R.id.CBSdl2Toggle);
        cbSdl2Toggle.setChecked(isEditMode() && container.getEnvVars().contains("SDL_XINPUT_ENABLED=1"));


        final EditText etLC_ALL = view.findViewById(R.id.ETlcall);
        Locale systemLocal = Locale.getDefault();
        
        // Загружаем массивы для преобразования
        String[] lcCodes = getResources().getStringArray(R.array.some_lc_all);
        String[] lcNames = getResources().getStringArray(R.array.some_lc_all_names);
        
        // Преобразуем технический код в красивое название при загрузке
        String initialLocale = isEditMode() ? container.getLC_ALL() : systemLocal.getLanguage() + '_' + systemLocal.getCountry() + ".UTF-8";
        String initialLocaleName = initialLocale;
        
        // Ищем красивое название для текущей локали
        String codeWithoutUtf = initialLocale.replace(".UTF-8", "").replace(".utf-8", "").replace(".utf8", "");
        for (int i = 0; i < lcCodes.length; i++) {
            if (lcCodes[i].equals(codeWithoutUtf)) {
                initialLocaleName = lcNames[i];
                break;
            }
        }
        
        etLC_ALL.setText(initialLocaleName); // Показываем красивое название
        etLC_ALL.setTag(initialLocale); // Сохраняем технический код в tag

        final View btShowLCALL = view.findViewById(R.id.BTShowLCALL);
        btShowLCALL.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(context, v);
            
            // Отображаем красивые названия в меню
            for (int i = 0; i < lcNames.length; i++)
                popupMenu.getMenu().add(Menu.NONE, i, Menu.NONE, lcNames[i]);
            
            // При выборе показываем красивое название, но в tag сохраняем технический код
            popupMenu.setOnMenuItemClickListener(item -> {
                int index = item.getItemId();
                etLC_ALL.setText(lcNames[index]); // Показываем красивое название
                etLC_ALL.setTag(lcCodes[index] + ".UTF-8"); // Сохраняем технический код
                return true;
            });
            popupMenu.show();
        });

        final CheckBox cbWoW64Mode = view.findViewById(R.id.CBWoW64Mode);
        cbWoW64Mode.setChecked(!isEditMode() || container.isWoW64Mode());

        final Spinner sStartupSelection = view.findViewById(R.id.SStartupSelection);
        byte previousStartupSelection = isEditMode() ? container.getStartupSelection() : -1;
        sStartupSelection.setSelection(previousStartupSelection != -1 ? previousStartupSelection : Container.STARTUP_SELECTION_ESSENTIAL);

        final Spinner sBox64Preset = view.findViewById(R.id.SBox64Preset);
        box64PresetSpinner = sBox64Preset;
        Box86_64PresetManager.loadSpinner("box64", sBox64Preset, isEditMode() ? container.getBox64Preset() : preferences.getString("box64_preset", Box86_64Preset.COMPATIBILITY));

        view.findViewById(R.id.BTDownloadBox64Version).setOnClickListener((v) -> {
            boolean isArm64EC = isCurrentWineArm64EC();
            ContentProfile.ContentType contentType = isArm64EC
                    ? ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                    : ContentProfile.ContentType.CONTENT_TYPE_BOX64;
            String displayName = isArm64EC ? "WoWBox64" : "Box64";
            showVersionDownloadDialog(contentType, displayName, sBox64Version, this::refreshBox64VersionSpinner, true);
        });

        // Box64 Preset management buttons
        view.findViewById(R.id.BTAddBox64Preset).setOnClickListener((v) -> {
            Box86_64EditPresetDialog dialog = new Box86_64EditPresetDialog(context, "box64", null);
            dialog.setOnConfirmCallback(() -> Box86_64PresetManager.loadSpinner("box64", sBox64Preset, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset)));
            dialog.show();
        });

        view.findViewById(R.id.BTEditBox64Preset).setOnClickListener((v) -> {
            Box86_64EditPresetDialog dialog = new Box86_64EditPresetDialog(context, "box64", Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
            dialog.setOnConfirmCallback(() -> Box86_64PresetManager.loadSpinner("box64", sBox64Preset, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset)));
            dialog.show();
        });

        view.findViewById(R.id.BTDuplicateBox64Preset).setOnClickListener((v) -> {
            ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
                Box86_64PresetManager.duplicatePreset("box64", context, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
                Box86_64PresetManager.loadSpinner("box64", sBox64Preset, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
                sBox64Preset.setSelection(sBox64Preset.getCount()-1);
            });
        });

        view.findViewById(R.id.BTRemoveBox64Preset).setOnClickListener((v) -> {
            final String presetId = Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset);
            if (!presetId.startsWith(Box86_64Preset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                Box86_64PresetManager.removePreset("box64", context, presetId);
                Box86_64PresetManager.loadSpinner("box64", sBox64Preset, preferences.getString("box64_preset", Box86_64Preset.COMPATIBILITY));
            });
        });

        view.findViewById(R.id.BTExportBox64Preset).setOnClickListener((v) -> {
            Box86_64PresetManager.exportPreset("box64", context, Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
        });

        view.findViewById(R.id.BTImportBox64Preset).setOnClickListener((v) -> {
            if (importBox64PresetLauncher != null) importBox64PresetLauncher.launch(new String[]{"*/*"});
        });

        final Spinner sFEXCoreVersion = view.findViewById(R.id.SFEXCoreVersion);
        fexcoreVersionSpinner = sFEXCoreVersion;
        FEXCoreManager.loadFEXCoreVersion(context, contentsManager, sFEXCoreVersion, container);

        view.findViewById(R.id.BTDownloadFEXCoreVersion).setOnClickListener((v) ->
                showVersionDownloadDialog(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, "FEXCore", sFEXCoreVersion, this::refreshFEXCoreVersionSpinner, true));

        final Spinner sFEXCorePreset = view.findViewById(R.id.SFEXCorePreset);
        fexcorePresetSpinner = sFEXCorePreset;
        FEXCorePresetManager.loadSpinner(sFEXCorePreset, isEditMode() ? container.getFEXCorePreset() : FEXCorePreset.INTERMEDIATE);

        view.findViewById(R.id.BTAddFEXCorePreset).setOnClickListener((v) -> {
            FEXCoreEditPresetDialog dialog = new FEXCoreEditPresetDialog(context, null);
            dialog.setOnConfirmCallback(() -> FEXCorePresetManager.loadSpinner(sFEXCorePreset, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset)));
            dialog.show();
        });

        view.findViewById(R.id.BTEditFEXCorePreset).setOnClickListener((v) -> {
            FEXCoreEditPresetDialog dialog = new FEXCoreEditPresetDialog(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
            dialog.setOnConfirmCallback(() -> FEXCorePresetManager.loadSpinner(sFEXCorePreset, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset)));
            dialog.show();
        });

        view.findViewById(R.id.BTDuplicateFEXCorePreset).setOnClickListener((v) -> {
            ContentDialog.confirm(context, R.string.do_you_want_to_duplicate_this_preset, () -> {
                FEXCorePresetManager.duplicatePreset(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
                FEXCorePresetManager.loadSpinner(sFEXCorePreset, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
                sFEXCorePreset.setSelection(sFEXCorePreset.getCount() - 1);
            });
        });

        view.findViewById(R.id.BTRemoveFEXCorePreset).setOnClickListener((v) -> {
            final String presetId = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
            if (!presetId.startsWith(FEXCorePreset.CUSTOM)) {
                AppUtils.showToast(context, R.string.you_cannot_remove_this_preset);
                return;
            }
            ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_preset, () -> {
                FEXCorePresetManager.removePreset(context, presetId);
                FEXCorePresetManager.loadSpinner(sFEXCorePreset, isEditMode() ? container.getFEXCorePreset() : FEXCorePreset.INTERMEDIATE);
            });
        });

        view.findViewById(R.id.BTExportFEXCorePreset).setOnClickListener((v) -> {
            FEXCorePresetManager.exportPreset(context, FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
        });

        view.findViewById(R.id.BTImportFEXCorePreset).setOnClickListener((v) -> {
            if (importFexcorePresetLauncher != null) importFexcorePresetLauncher.launch(new String[]{"*/*"});
        });

        String selectedDriver = sGraphicsDriver.getSelectedItem().toString();
        List<String> sGraphicsItemsList = new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.graphics_driver_entries)));
        sGraphicsDriver.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, sGraphicsItemsList));
        AppUtils.setSpinnerSelectionFromValue(sGraphicsDriver, selectedDriver);

        final Spinner sRCFile = view.findViewById(R.id.SRCFile);
        RCManager rcManager = new RCManager(context);
        RCManager.loadRCFileSpinner(rcManager, container == null ? 0 : container.getRCFileId(), sRCFile, id -> selectedRcFileId = id);

        final CPUListView cpuListView = view.findViewById(R.id.CPUListView);
        final CPUListView cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);

        cpuListView.setCheckedCPUList(isEditMode() ? container.getCPUList(true) : Container.getFallbackCPUList());
        cpuListViewWoW64.setCheckedCPUList(isEditMode() ? container.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64());

        final Spinner sPrimaryController = view.findViewById(R.id.SPrimaryController);
        sPrimaryController.setSelection(isEditMode() ? container.getPrimaryController() : 1);
        setControllerMapping(view.findViewById(R.id.SButtonA), Container.XrControllerMapping.BUTTON_A, XKeycode.KEY_A.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonB), Container.XrControllerMapping.BUTTON_B, XKeycode.KEY_B.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonX), Container.XrControllerMapping.BUTTON_X, XKeycode.KEY_X.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonY), Container.XrControllerMapping.BUTTON_Y, XKeycode.KEY_Y.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonGrip), Container.XrControllerMapping.BUTTON_GRIP, XKeycode.KEY_SPACE.ordinal());
        setControllerMapping(view.findViewById(R.id.SButtonTrigger), Container.XrControllerMapping.BUTTON_TRIGGER, XKeycode.KEY_ENTER.ordinal());
        setControllerMapping(view.findViewById(R.id.SThumbstickUp), Container.XrControllerMapping.THUMBSTICK_UP, XKeycode.KEY_UP.ordinal());
        setControllerMapping(view.findViewById(R.id.SThumbstickDown), Container.XrControllerMapping.THUMBSTICK_DOWN, XKeycode.KEY_DOWN.ordinal());
        setControllerMapping(view.findViewById(R.id.SThumbstickLeft), Container.XrControllerMapping.THUMBSTICK_LEFT, XKeycode.KEY_LEFT.ordinal());
        setControllerMapping(view.findViewById(R.id.SThumbstickRight), Container.XrControllerMapping.THUMBSTICK_RIGHT, XKeycode.KEY_RIGHT.ordinal());

        createWineConfigurationTab(view);
        envVarsView = createEnvVarsTab(view);
        createWinComponentsTab(view, isEditMode() ? container.getWinComponents() : Container.DEFAULT_WINCOMPONENTS);
        createDrivesTab(view);
        view.findViewById(R.id.BTExportContainerProfile).setOnClickListener((v) -> exportCurrentContainerProfile());
        view.findViewById(R.id.BTImportContainerProfile).setOnClickListener((v) -> {
            if (importContainerProfileLauncher != null) {
                importContainerProfileLauncher.launch(new String[]{"application/json", "*/*"});
            }
        });

        AppUtils.setupTabLayout(view, R.id.TabLayout, R.id.LLTabWineConfiguration, R.id.LLTabWinComponents, R.id.LLTabEnvVars, R.id.LLTabDrives, R.id.LLTabAdvanced, R.id.LLTabXR);

        TabLayout tabLayout = view.findViewById(R.id.TabLayout);

        if (isDarkMode) {
            tabLayout.setBackgroundResource(R.drawable.tab_layout_background_dark);
        } else {
            tabLayout.setBackgroundResource(R.drawable.tab_layout_background);
        }

        // Set up confirm button
        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            try {
                // Capture and set container properties based on UI inputs
                String name = etName.getText().toString();
                String screenSize = getScreenSize(view);
                String envVars = envVarsView.getEnvVars();
                String graphicsDriver = StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem());
                String graphicsDriverConfig = vGraphicsDriverConfig.getTag().toString();
                String dxwrapper = StringUtils.parseIdentifier(sDXWrapper.getSelectedItem());
                String ddrawrapper = StringUtils.parseIdentifier(sDDrawrapper.getSelectedItem());
                String dxwrapperConfig = vDXWrapperConfig.getTag().toString();
                String audioDriver = StringUtils.parseIdentifier(sAudioDriver.getSelectedItem());
                String audioDriverConfig = vAudioDriverConfig.getTag().toString();
                Log.d("ContainerDetailFragment", "Конфигурация аудио драйвера при сохранении: " + audioDriverConfig);
                String emulator = StringUtils.parseIdentifier(sEmulator.getSelectedItem());
                String wincomponents = getWinComponents(view);
                String drives = getDrives(view);

                boolean fullscreenStretched = cbFullscreenStretched.isChecked();
                String cpuList = cpuListView.getCheckedCPUListAsString();
                String cpuListWoW64 = cpuListViewWoW64.getCheckedCPUListAsString();
                boolean wow64Mode = cbWoW64Mode.isChecked();
                byte startupSelection = (byte) sStartupSelection.getSelectedItemPosition();
                String box64Version = sBox64Version.getSelectedItem().toString();
                String box64Preset = Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset);
                String desktopTheme = getDesktopTheme(view);
                int rcfileId = selectedRcFileId;
                String fexcoreVersion = sFEXCoreVersion.getSelectedItem().toString();
                String fexcorePreset = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
                // Capture missing properties
                String midiSoundFont = sMIDISoundFont.getSelectedItemPosition() == 0 ? "" : sMIDISoundFont.getSelectedItem().toString();
                // Берем технический код локали из tag (где хранится ru_RU.UTF-8), а не красивое название из поля
                String lc_all = etLC_ALL.getTag() != null ? etLC_ALL.getTag().toString() : etLC_ALL.getText().toString();
                int primaryController = sPrimaryController.getSelectedItemPosition();
                String controllerMapping = getControllerMapping(view);

                // Define final input type
                int finalInputType = 0;
                finalInputType |= cbEnableXInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_XINPUT : 0;
                finalInputType |= cbEnableDInput.isChecked() ? WinHandler.FLAG_INPUT_TYPE_DINPUT : 0;
                finalInputType |= SDInputType.getSelectedItemPosition() == 0 ? WinHandler.FLAG_DINPUT_MAPPER_STANDARD : WinHandler.FLAG_DINPUT_MAPPER_XINPUT;

                // Handle SDL2 environment variables based on the toggle state
                if (cbSdl2Toggle.isChecked()) {
                    // Add SDL2 environment variables if the toggle is enabled
                    for (String envVar : SDL2_ENV_VARS) {
                        if (!envVars.contains(envVar)) {
                            envVars += (envVars.isEmpty() ? "" : " ") + envVar;
                        }
                    }
                } else {
                    // Remove SDL2 environment variables if the toggle is disabled
                    for (String envVar : SDL2_ENV_VARS) {
                        envVars = envVars.replace(envVar, "").replaceAll("\\s{2,}", " ").trim();
                    }
                }



                if (isEditMode()) {
                    // Update existing container properties
                    container.setName(name);
                    container.setScreenSize(screenSize);
                    container.setEnvVars(envVars);
                    container.setCPUList(cpuList);
                    container.setCPUListWoW64(cpuListWoW64);
                    container.setGraphicsDriver(graphicsDriver);
                    container.setGraphicsDriverConfig(graphicsDriverConfig);
                    container.setDXWrapper(dxwrapper);
                    container.setDDrawWrapper(ddrawrapper);
                    container.setDXWrapperConfig(dxwrapperConfig);
                    container.setAudioDriver(audioDriver);
                    container.setAudioDriverConfig(audioDriverConfig);
                    container.setEmulator(emulator);
                    container.setWinComponents(wincomponents);
                    container.setDrives(drives);

                    container.setFullscreenStretched(fullscreenStretched);
                    container.setInputType(finalInputType);
                    container.setWoW64Mode(wow64Mode);
                    container.setStartupSelection(startupSelection);
                    container.setBox64Version(box64Version);
                    container.setBox64Preset(box64Preset);
                    container.setFEXCoreVersion(fexcoreVersion);
                    container.setFEXCorePreset(fexcorePreset);
                    container.setDesktopTheme(desktopTheme);
                    container.setRcfileId(rcfileId);
                    container.setMidiSoundFont(midiSoundFont);
                    container.setLC_ALL(lc_all);
                    container.setPrimaryController(primaryController);
                    container.setControllerMapping(controllerMapping);
                    container.saveData();
                    saveWineRegistryKeys(view);
                    getActivity().onBackPressed();
                } else {
                    // Create new container with specified properties
                    JSONObject data = new JSONObject();
                    data.put("name", name);
                    data.put("screenSize", screenSize);
                    data.put("envVars", envVars);
                    data.put("cpuList", cpuList);
                    data.put("cpuListWoW64", cpuListWoW64);
                    data.put("graphicsDriver", graphicsDriver);
                    data.put("graphicsDriverConfig", graphicsDriverConfig);
                    data.put("dxwrapper", dxwrapper);
                    data.put("ddrawrapper", ddrawrapper);
                    data.put("dxwrapperConfig", dxwrapperConfig);
                    data.put("audioDriver", audioDriver);
                    data.put("audioDriverConfig", audioDriverConfig);
                    data.put("emulator", emulator);
                    data.put("wincomponents", wincomponents);
                    data.put("drives", drives);

                    data.put("fullscreenStretched", fullscreenStretched);
                    data.put("inputType", finalInputType);
                    data.put("wow64Mode", wow64Mode);
                    data.put("startupSelection", startupSelection);
                    data.put("box64Version", box64Version);
                    data.put("box64Preset", box64Preset);
                    data.put("fexcoreVersion", fexcoreVersion);
                    data.put("fexcorePreset", fexcorePreset);
                    data.put("desktopTheme", desktopTheme);
                    data.put("rcfileId", rcfileId);
                    data.put("wineVersion", sWineVersion.getSelectedItem().toString());
                    data.put("midiSoundFont", midiSoundFont);
                    data.put("lc_all", lc_all);
                    data.put("primaryController", primaryController);
                    data.put("controllerMapping", controllerMapping);
                    appendWineConfigurationToProfileData(data, view);

                    preloaderDialog.show(R.string.creating_container);

                    // Initialize ImageFs
                    File imageFsRoot = new File(context.getFilesDir(), "imagefs");
                    imageFs = ImageFs.find(imageFsRoot);


                    manager.createContainerAsync(data, contentsManager, (container) -> {
                        if (container != null) {
                            this.container = container;
                            saveWineRegistryKeys(view);
                        }
                        preloaderDialog.close();
                        getActivity().onBackPressed();
                    });
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });

        // FAB для установки компонентов
        view.findViewById(R.id.fabInstallComponents).setOnClickListener((v) -> {
            if (isEditMode()) {
                openComponentsDialogForContainer(container);
            } else {
                Toast.makeText(context, "Please save the container first", Toast.LENGTH_SHORT).show();
            }
        });

        return view;
    }

    /**
     * Открыть диалог установки компонентов для текущего контейнера
     */
    private void openComponentsDialogForContainer(Container container) {
        // Импортируем необходимые классы
        com.winlator.cmod.components.ComponentsDialog dialog = 
            new com.winlator.cmod.components.ComponentsDialog(getContext(), selectedComponents -> {
            if (selectedComponents.isEmpty()) {
                Toast.makeText(getContext(), "No components selected", Toast.LENGTH_SHORT).show();
                return;
            }

            preloaderDialog.show(R.string.downloading_file);

            // Скачиваем в папку Download/Winlator - используем правильный путь
            java.io.File downloadDir = new java.io.File(Environment.getExternalStorageDirectory(), "Download/Winlator");

            installComponentsToContainer(selectedComponents, container, downloadDir);
        });

        dialog.show();
    }

    /**
     * Загрузить и установить компоненты в контейнер через ярлыки
     */
    private void installComponentsToContainer(java.util.List<com.winlator.cmod.components.ComponentInfo> components, 
                                             Container container, java.io.File downloadDir) {
        final int[] currentIndex = {0};
        final int totalCount = components.size();

        installNextComponentViaShortcut(components, container, downloadDir, currentIndex, totalCount);
    }

    private void installNextComponentViaShortcut(java.util.List<com.winlator.cmod.components.ComponentInfo> components,
                                                 Container container, java.io.File downloadDir,
                                                 int[] currentIndex, int totalCount) {
        if (currentIndex[0] >= totalCount) {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    preloaderDialog.close();
                });
            }
            return;
        }

        com.winlator.cmod.components.ComponentInfo component = components.get(currentIndex[0]);

        // Загружаем компонент
        com.winlator.cmod.components.ComponentDownloader.download(component, downloadDir, 
            new com.winlator.cmod.components.ComponentDownloader.DownloadListener() {
            @Override
            public void onProgress(com.winlator.cmod.components.ComponentDownloader.DownloadProgress progress) {
                // Показываем прогресс в диалоге загрузки
                if (getActivity() != null) {
                    String message = "Downloading " + (currentIndex[0] + 1) + "/" + totalCount + "\n" + 
                                   progress.componentName + "\n" + 
                                   progress.getProgressText();
                    preloaderDialog.updateTextOnUiThread(message);
                }
            }

            @Override
            public void onComplete(java.io.File file, com.winlator.cmod.components.ComponentInfo comp) {
                // После загрузки создаем ярлык и запускаем его
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Обновляем диалог
                        String message = "Installing " + (currentIndex[0] + 1) + "/" + totalCount + "\n" + 
                                       comp.getDisplayName() + "\nPlease complete installation...";
                        preloaderDialog.updateText(message);
                        
                        java.io.File shortcutFile = createTemporaryShortcut(container, file, comp);
                        
                        // Автоматически запускаем установку
                        if (shortcutFile != null) {
                            launchShortcutInstallation(container, shortcutFile, comp);
                        }
                        
                        // Переходим к следующему компоненту
                        currentIndex[0]++;
                        installNextComponentViaShortcut(components, container, downloadDir, currentIndex, totalCount);
                    });
                }
            }

            @Override
            public void onError(String error, com.winlator.cmod.components.ComponentInfo comp) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        // Показываем ошибку в диалоге
                        String message = "Error downloading " + (currentIndex[0] + 1) + "/" + totalCount + "\n" + 
                                       comp.getDisplayName() + "\n" + error;
                        preloaderDialog.updateText(message);
                        
                        // Ждем 2 секунды перед продолжением
                        new android.os.Handler().postDelayed(() -> {
                            // Продолжаем со следующим
                            currentIndex[0]++;
                            installNextComponentViaShortcut(components, container, downloadDir, currentIndex, totalCount);
                        }, 2000);
                    });
                }
            }
        });
    }

    /**
     * Создать временный ярлык для автоматической установки компонента
     */
    private java.io.File createTemporaryShortcut(Container container, java.io.File componentFile, 
                                        com.winlator.cmod.components.ComponentInfo component) {
        try {
            // Определяем буквы дисков и пути как в ShortcutsFragment
            String driveLetter = "D:"; // /storage/emulated/0/Download -> D:
            // Важно: компонент скачивается в /storage/emulated/0/Download/Winlator,
            // а D: в Wine мапится на /storage/emulated/0/Download.
            // Поэтому нужен путь вида D:/Winlator/<file>, иначе Wine не найдёт файл.
            String execPath = convertToWindowsPath(componentFile.getAbsolutePath(), container);
            
            // WINEPREFIX как в ShortcutsFragment
            String winePrefix = container.getRootDir().getAbsolutePath() + 
                              "/.wine/dosdevices/z:" + 
                              container.getRootDir().getAbsolutePath().replace(
                                  "/data/user/0/" + com.winlator.cmod.MainActivity.PACKAGE_NAME + "/files/imagefs", 
                                  "") + "/.wine";
            
            // Path должен заканчиваться на d:/ без поддиректорий!
            String pathLine = container.getRootDir().getAbsolutePath() + 
                            "/.wine/dosdevices/" + driveLetter.toLowerCase() + "/";
            
            // Создаем .desktop файл
            java.io.File shortcutsDir = container.getDesktopDir();
            if (!shortcutsDir.exists()) {
                shortcutsDir.mkdirs();
            }
            
            String shortcutName = "_winlator_component_" + component.getFileName().replace(".exe", "");
            java.io.File shortcutFile = new java.io.File(shortcutsDir, shortcutName + ".desktop");
            
            // Создаем содержимое .desktop файла как в ShortcutsFragment
            StringBuilder content = new StringBuilder();
            content.append("[Desktop Entry]\n");
            content.append("Name=").append(component.getDisplayName()).append("\n");
            content.append("Exec=env WINEPREFIX=\"").append(winePrefix).append("\" wine ")
                   .append(execPath).append("\n");
            content.append("Type=Application\n");
            content.append("StartupNotify=true\n");
            content.append("Path=").append(pathLine).append("\n");
            content.append("StartupWMClass=").append(component.getFileName().toLowerCase()).append("\n");
            content.append("\n[Extra Data]\n");
            content.append("inputType=6\n");
            content.append("simTouchScreen=0\n");
            content.append("execArgs=/passive /norestart\n");
            content.append("fexConfig=\n");
            content.append("sharpnessEffect=None\n");
            content.append("sharpnessLevel=100\n");
            content.append("sharpnessDenoise=100\n");
            
            com.winlator.cmod.core.FileUtils.writeString(shortcutFile, content.toString());
            
            return shortcutFile;
            
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to create shortcut: " + e.getMessage(), 
                         Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * Запустить установку компонента через ярлык
     */
    private void launchShortcutInstallation(Container container, java.io.File shortcutFile,
                                           com.winlator.cmod.components.ComponentInfo component) {
        try {
            android.content.Intent intent = new android.content.Intent(getActivity(), 
                com.winlator.cmod.XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            intent.putExtra("shortcut_path", shortcutFile.getPath());
            intent.putExtra("shortcut_name", component.getDisplayName());
            
            Toast.makeText(getContext(), 
                "Installing " + component.getDisplayName() + "...", 
                Toast.LENGTH_SHORT).show();
            
            startActivity(intent);
            
        } catch (Exception e) {
            Toast.makeText(getContext(), "Failed to launch: " + e.getMessage(), 
                         Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Конвертировать Linux путь в Windows путь для Wine
     */
    private String convertToWindowsPath(String linuxPath, Container container) {
        // Получаем правильный путь к внешнему хранилищу
        String externalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String downloadPath = externalStoragePath + "/Download";
        
        // /storage/emulated/0/Download -> D:/Download (так как D: это внешнее хранилище)
        if (linuxPath.startsWith(downloadPath)) {
            String relativePath = linuxPath.substring(downloadPath.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            // Используем прямые слеши для Wine
            return "D:/" + relativePath.replace("/", "/");
        }
        
        // /storage/emulated/0 -> D:/ (внешнее хранилище)
        if (linuxPath.startsWith(externalStoragePath)) {
            String relativePath = linuxPath.substring(externalStoragePath.length());
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            // Используем прямые слеши для Wine
            return "D:/" + relativePath.replace("/", "/");
        }
        
        // /data/user/0/package/files/imagefs -> Z:/
        String imagefsPath = "/data/user/0/" + com.winlator.cmod.MainActivity.PACKAGE_NAME + "/files/imagefs";
        if (linuxPath.startsWith(imagefsPath)) {
            return "Z:/" + linuxPath.substring(imagefsPath.length() + 1).replace("/", "/");
        }
        
        // По умолчанию Z:
        return "Z:/" + linuxPath.replace("/", "/");
    }

    private void saveWineRegistryKeys(View view) {
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            Spinner sCSMT = view.findViewById(R.id.SCSMT);
            registryEditor.setDwordValue("Software\\Wine\\Direct3D", "csmt", sCSMT.getSelectedItemPosition() != 0 ? 3 : 0);

            Spinner sGPUName = view.findViewById(R.id.SGPUName);
            try {
                JSONObject gpuName = gpuCards.getJSONObject(sGPUName.getSelectedItemPosition());
                registryEditor.setDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", gpuName.getInt("deviceID"));
                registryEditor.setDwordValue("Software\\Wine\\Direct3D", "VideoPciVendorID", gpuName.getInt("vendorID"));
            }
            catch (JSONException e) {}

            Spinner sOffscreenRenderingMode = view.findViewById(R.id.SOffscreenRenderingMode);
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", sOffscreenRenderingMode.getSelectedItem().toString().toLowerCase(Locale.ENGLISH));

            Spinner sStrictShaderMath = view.findViewById(R.id.SStrictShaderMath);
            registryEditor.setDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", sStrictShaderMath.getSelectedItemPosition());

            Spinner sVideoMemorySize = view.findViewById(R.id.SVideoMemorySize);
            String videoMemorySize = StringUtils.parseNumber(sVideoMemorySize.getSelectedItem());
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", videoMemorySize);

            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
            registryEditor.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", sMouseWarpOverride.getSelectedItem().toString().toLowerCase(Locale.ENGLISH));

            registryEditor.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl");
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled");
        }
    }

    private void createWineConfigurationTab(View view) {
        Context context = getContext();

        WineThemeManager.ThemeInfo desktopTheme = new WineThemeManager.ThemeInfo(isEditMode() ? container.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME);
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        sDesktopTheme.setSelection(desktopTheme.theme.ordinal());
        final ImagePickerView ipvDesktopBackgroundImage = view.findViewById(R.id.IPVDesktopBackgroundImage);
        final ColorPickerView cpvDesktopBackgroundColor = view.findViewById(R.id.CPVDesktopBackgroundColor);
        cpvDesktopBackgroundColor.setColor(desktopTheme.backgroundColor);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        sDesktopBackgroundType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[position];
                ipvDesktopBackgroundImage.setVisibility(View.GONE);
                cpvDesktopBackgroundColor.setVisibility(View.GONE);

                if (type == WineThemeManager.BackgroundType.IMAGE) {
                    ipvDesktopBackgroundImage.setVisibility(View.VISIBLE);
                }
                else if (type == WineThemeManager.BackgroundType.COLOR) {
                    cpvDesktopBackgroundColor.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        sDesktopBackgroundType.setSelection(desktopTheme.backgroundType.ordinal());

        File containerDir = isEditMode() ? container.getRootDir() : null;
        File userRegFile = new File(containerDir, ".wine/user.reg");

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            List<String> stateList = Arrays.asList(context.getString(R.string.disable), context.getString(R.string.enable));
            Spinner sCSMT = view.findViewById(R.id.SCSMT);
            sCSMT.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, stateList));
            sCSMT.setSelection(registryEditor.getDwordValue("Software\\Wine\\Direct3D", "csmt", 3) != 0 ? 1 : 0);

            Spinner sGPUName = view.findViewById(R.id.SGPUName);
            loadGPUNameSpinner(sGPUName, registryEditor.getDwordValue("Software\\Wine\\Direct3D", "VideoPciDeviceID", 1728));

            List<String> offscreenRenderingModeList = Arrays.asList("Backbuffer", "FBO");
            Spinner sOffscreenRenderingMode = view.findViewById(R.id.SOffscreenRenderingMode);
            sOffscreenRenderingMode.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, offscreenRenderingModeList));
            AppUtils.setSpinnerSelectionFromValue(sOffscreenRenderingMode, registryEditor.getStringValue("Software\\Wine\\Direct3D", "OffScreenRenderingMode", "fbo"));

            Spinner sStrictShaderMath = view.findViewById(R.id.SStrictShaderMath);
            sStrictShaderMath.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, stateList));
            sStrictShaderMath.setSelection(Math.min(registryEditor.getDwordValue("Software\\Wine\\Direct3D", "strict_shader_math", 1), 1));

            Spinner sVideoMemorySize = view.findViewById(R.id.SVideoMemorySize);
            String videoMemorySize = registryEditor.getStringValue("Software\\Wine\\Direct3D", "VideoMemorySize", "2048");
            AppUtils.setSpinnerSelectionFromNumber(sVideoMemorySize, videoMemorySize);

            List<String> mouseWarpOverrideList = Arrays.asList(context.getString(R.string.disable), context.getString(R.string.enable), context.getString(R.string.force));
            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
            sMouseWarpOverride.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, mouseWarpOverrideList));
            AppUtils.setSpinnerSelectionFromValue(sMouseWarpOverride, registryEditor.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", "disable"));
        }
    }

    private void loadGPUNameSpinner(Spinner spinner, int selectedDeviceID) {
        List<String> values = new ArrayList<>();
        int selectedPosition = 0;

        try {
            for (int i = 0; i < gpuCards.length(); i++) {
                JSONObject item = gpuCards.getJSONObject(i);
                if (item.getInt("deviceID") == selectedDeviceID) selectedPosition = i;
                values.add(item.getString("name"));
            }
        }
        catch (JSONException e) {}

        spinner.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, values));
        spinner.setSelection(selectedPosition);
    }

    public static String getScreenSize(View view) {
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        String value = sScreenSize.getSelectedItem().toString();
        if (value.equalsIgnoreCase("custom")) {
            value = Container.DEFAULT_SCREEN_SIZE;
            String strWidth = ((EditText)view.findViewById(R.id.ETScreenWidth)).getText().toString().trim();
            String strHeight = ((EditText)view.findViewById(R.id.ETScreenHeight)).getText().toString().trim();
            if (strWidth.matches("[0-9]+") && strHeight.matches("[0-9]+")) {
                int width = Integer.parseInt(strWidth);
                int height = Integer.parseInt(strHeight);
                if ((width % 2) == 0 && (height % 2) == 0) return width+"x"+height;
            }
        }
        return StringUtils.parseIdentifier(value);
    }

    private String getDesktopTheme(View view) {
        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[sDesktopBackgroundType.getSelectedItemPosition()];
        Spinner sDesktopTheme = view.findViewById(R.id.SDesktopTheme);
        ColorPickerView cpvDesktopBackground = view.findViewById(R.id.CPVDesktopBackgroundColor);
        WineThemeManager.Theme theme = WineThemeManager.Theme.values()[sDesktopTheme.getSelectedItemPosition()];

        String desktopTheme = theme+","+type+","+cpvDesktopBackground.getColorAsString();
        if (type == WineThemeManager.BackgroundType.IMAGE) {
            File userWallpaperFile = WineThemeManager.getUserWallpaperFile(getContext());
            desktopTheme += ","+(userWallpaperFile.isFile() ? userWallpaperFile.lastModified() : "0");
        }
        return desktopTheme;
    }

    public static void loadScreenSizeSpinner(View view, String selectedValue) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);
        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String value = sScreenSize.getItemAtPosition(position).toString();
                llCustomScreenSize.setVisibility(value.equalsIgnoreCase("custom") ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        boolean found = AppUtils.setSpinnerSelectionFromIdentifier(sScreenSize, selectedValue);
        if (!found) {
            AppUtils.setSpinnerSelectionFromValue(sScreenSize, "custom");
            String[] screenSize = selectedValue.split("x");
            ((EditText) view.findViewById(R.id.ETScreenWidth)).setText(screenSize[0]);
            ((EditText) view.findViewById(R.id.ETScreenHeight)).setText(screenSize[1]);
        }
    }

    // New method: Adds support for the GraphicsDriverConfigDialog
    public void loadGraphicsDriverSpinner(final Spinner sGraphicsDriver, final Spinner sDXWrapper, final View vGraphicsDriverConfig, String selectedGraphicsDriver, String selectedDXWrapper) {
        final Context context = sGraphicsDriver.getContext();

        // Update the spinner with the available graphics driver options
        updateGraphicsDriverSpinner(context, sGraphicsDriver);

        Runnable update = () -> {
            String graphicsDriver = StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem());

            // Update the DXWrapper spinner
            ArrayList<String> items = new ArrayList<>();
            for (String value : context.getResources().getStringArray(R.array.dxwrapper_entries)) {
                items.add(value);
            }
            sDXWrapper.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, items.toArray()));
            AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, selectedDXWrapper);

            vGraphicsDriverConfig.setOnClickListener((v) -> {
                new GraphicsDriverConfigDialog(vGraphicsDriverConfig, graphicsDriver, null).show();
            });
            vGraphicsDriverConfig.setVisibility(View.VISIBLE);
        };

        sGraphicsDriver.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                update.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Set the spinner's initial selection
        AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, selectedGraphicsDriver);
        update.run();
    }

    public static void setupDXWrapperSpinner(final Spinner sDXWrapper, final View vDXWrapperConfig) {
        sDXWrapper.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String dxwrapper = StringUtils.parseIdentifier(sDXWrapper.getSelectedItem());
                if (dxwrapper.equals("dxvk")) {
                    vDXWrapperConfig.setOnClickListener((v) -> (new DXVKConfigDialog(vDXWrapperConfig)).show());
                    vDXWrapperConfig.setVisibility(View.VISIBLE);
                }
                else if (dxwrapper.equals("vkd3d")) {
                    vDXWrapperConfig.setOnClickListener((v) -> (new VKD3DConfigDialog(vDXWrapperConfig)).show());
                    vDXWrapperConfig.setVisibility(View.VISIBLE);
                } else vDXWrapperConfig.setVisibility(View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    public static void setupDDrawSpinner(final Spinner sDDrawspinner, String selectedDDrawrapper) {
        final Context context = sDDrawspinner.getContext();
        ArrayList<String> items = new ArrayList<>();
        for (String value : context.getResources().getStringArray(R.array.ddrawrapper_entries)) {
            items.add(value);
        }
        sDDrawspinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, items.toArray(new String[0])));
        AppUtils.setSpinnerSelectionFromIdentifier(sDDrawspinner, selectedDDrawrapper);
    }


    public static String getWinComponents(View view) {
        ViewGroup parent = view.findViewById(R.id.LLTabWinComponents);
        ArrayList<View> views = new ArrayList<>();
        AppUtils.findViewsWithClass(parent, Spinner.class, views);
        String[] wincomponents = new String[views.size()];

        for (int i = 0; i < views.size(); i++) {
            Spinner spinner = (Spinner)views.get(i);
            wincomponents[i] = spinner.getTag()+"="+spinner.getSelectedItemPosition();
        }
        return String.join(",", wincomponents);
    }

    public static void createWinComponentsTab(View view, String wincomponents) {
        Context context = view.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
        ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);
        directxSectionView.removeAllViews();
        generalSectionView.removeAllViews();

        for (String[] wincomponent : new KeyValueSet(wincomponents)) {
            ViewGroup parent = wincomponent[0].startsWith("direct") ? directxSectionView : generalSectionView;
            View itemView = inflater.inflate(R.layout.wincomponent_list_item, parent, false);
            ((TextView)itemView.findViewById(R.id.TextView)).setText(StringUtils.getString(context, wincomponent[0]));
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setSelection(Integer.parseInt(wincomponent[1]), false);
            spinner.setTag(wincomponent[0]);

            // Set the background color of the spinners dynamically based on the current theme
            spinner.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark: R.drawable.content_dialog_background);

            parent.addView(itemView);

        }
    }

    public static void createWinComponentsTabFromShortcut(ShortcutSettingsDialog dialog, View view, String wincomponents, boolean isDarkMode) {
        Context context = dialog.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
        ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);

        for (String[] wincomponent : new KeyValueSet(wincomponents)) {
            ViewGroup parent = wincomponent[0].startsWith("direct") ? directxSectionView : generalSectionView;
            View itemView = inflater.inflate(R.layout.wincomponent_list_item, parent, false);
            ((TextView) itemView.findViewById(R.id.TextView)).setText(StringUtils.getString(context, wincomponent[0]));
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setSelection(Integer.parseInt(wincomponent[1]), false);
            spinner.setTag(wincomponent[0]);

            // Set the background color of the spinners dynamically based on the current theme
            spinner.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

            parent.addView(itemView);
        }

        // Notify that the views are ready
        dialog.onWinComponentsViewsAdded(isDarkMode);
    }

    private EnvVarsView createEnvVarsTab(final View view) {
        final Context context = view.getContext();
        final EnvVarsView envVarsView = view.findViewById(R.id.EnvVarsView);

        // Apply dark mode setting to the existing instance
        envVarsView.setDarkMode(isDarkMode); // New setter method

        envVarsView.setEnvVars(new EnvVars(isEditMode() ? container.getEnvVars() : Container.DEFAULT_ENV_VARS));
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) -> (new AddEnvVarDialog(context, envVarsView)).show());
        return envVarsView;
    }

    private String getDrives(View view) {
        LinearLayout parent = view.findViewById(R.id.LLDrives);
        String drives = "";

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Spinner spinner = child.findViewById(R.id.Spinner);
            EditText editText = child.findViewById(R.id.EditText);
            String path = editText.getText().toString().trim();
            if (!path.isEmpty()) drives += spinner.getSelectedItem()+path;
        }
        return drives;
    }

    private void createDrivesTab(View view) {
        final String drives = isEditMode() ? container.getDrives() : Container.DEFAULT_DRIVES;
        createDrivesTab(view, drives);
    }

    private void createDrivesTab(View view, String drives) {
        final Context context = getContext();

        final LinearLayout parent = view.findViewById(R.id.LLDrives);
        final View emptyTextView = view.findViewById(R.id.TVDrivesEmptyText);
        parent.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        final String[] driveLetters = new String[Container.MAX_DRIVE_LETTERS];
        for (int i = 0; i < driveLetters.length; i++) driveLetters[i] = ((char)(i + 68))+":";

        Callback<String[]> addItem = (drive) -> {
            final View itemView = inflater.inflate(R.layout.drive_list_item, parent, false);
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, driveLetters));
            AppUtils.setSpinnerSelectionFromValue(spinner, drive[0]+":");

            // Apply dark theme to the spinner popup background
            spinner.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

            final EditText editText = itemView.findViewById(R.id.EditText);
            editText.setText(drive[1]);

            // Apply dark theme to EditText if necessary
            applyDarkThemeToEditText(editText);

            // Apply dark theme to the search button if necessary
            View btSearch = itemView.findViewById(R.id.BTSearch);
            applyDarkThemeToButton(btSearch);

            itemView.findViewById(R.id.BTSearch).setOnClickListener((v) -> {
                openDirectoryCallback = (path) -> {
                    drive[1] = path;
                    editText.setText(path);
                };
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.fromFile(Environment.getExternalStorageDirectory()));
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_DIRECTORY_REQUEST_CODE);
            });

            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                parent.removeView(itemView);
                if (parent.getChildCount() == 0) emptyTextView.setVisibility(View.VISIBLE);
            });
            parent.addView(itemView);

            // Hide empty text view if there are items
            emptyTextView.setVisibility(View.GONE);
        };
        for (String[] drive : Container.drivesIterator(drives)) addItem.call(drive);

        view.findViewById(R.id.BTAddDrive).setOnClickListener((v) -> {
            if (parent.getChildCount() >= Container.MAX_DRIVE_LETTERS) return;
            final String nextDriveLetter = String.valueOf(driveLetters[parent.getChildCount()].charAt(0));
            addItem.call(new String[]{nextDriveLetter, ""});
        });

        if (drives.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    private void exportCurrentContainerProfile() {
        if (rootView == null || envVarsView == null || exportContainerProfileLauncher == null) return;

        EditText input = new EditText(requireContext());
        applyDarkThemeToEditText(input);

        String containerName = ((EditText) rootView.findViewById(R.id.ETName)).getText().toString().trim();
        String defaultName = containerName.isEmpty() ? "container-profile" : containerName;
        input.setText(defaultName);
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.enter_profile_name)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    try {
                        String profileName = input.getText().toString().trim();
                        if (profileName.isEmpty()) profileName = defaultName;
                        JSONObject profile = buildContainerProfileJson(profileName);
                        pendingContainerProfileJson = profile.toString(2);
                        exportContainerProfileLauncher.launch(sanitizeProfileFileName(profileName) + CONTAINER_PROFILE_ARCHIVE_EXTENSION);
                    }
                    catch (Exception e) {
                        Log.e(TAG, "Failed to build container profile", e);
                        AppUtils.showToast(requireContext(), R.string.failed_to_export_container_profile);
                    }
                })
                .show();
    }

    private JSONObject buildContainerProfileJson(String profileName) throws JSONException {
        JSONObject profile = new JSONObject();
        profile.put("profileType", CONTAINER_PROFILE_TYPE);
        profile.put("schemaVersion", CONTAINER_PROFILE_SCHEMA_VERSION);
        profile.put("profileName", profileName);
        profile.put("sourceContainerName", ((EditText) rootView.findViewById(R.id.ETName)).getText().toString().trim());
        profile.put("exportedAt", System.currentTimeMillis());
        profile.put("settings", collectCurrentProfileSettings());
        return profile;
    }

    private JSONObject collectCurrentProfileSettings() throws JSONException {
        JSONObject data = new JSONObject();

        Spinner sGraphicsDriver = rootView.findViewById(R.id.SGraphicsDriver);
        Spinner sDXWrapper = rootView.findViewById(R.id.SDXWrapper);
        Spinner sDDrawrapper = rootView.findViewById(R.id.SDDrawrapper);
        Spinner sAudioDriver = rootView.findViewById(R.id.SAudioDriver);
        Spinner sEmulator = rootView.findViewById(R.id.SEmulator);
        Spinner sBox64Version = rootView.findViewById(R.id.SBox64Version);
        Spinner sBox64Preset = rootView.findViewById(R.id.SBox64Preset);
        Spinner sFEXCoreVersion = rootView.findViewById(R.id.SFEXCoreVersion);
        Spinner sFEXCorePreset = rootView.findViewById(R.id.SFEXCorePreset);
        Spinner sMIDISoundFont = rootView.findViewById(R.id.SMIDISoundFont);
        Spinner sStartupSelection = rootView.findViewById(R.id.SStartupSelection);
        Spinner sPrimaryController = rootView.findViewById(R.id.SPrimaryController);
        View vGraphicsDriverConfig = rootView.findViewById(R.id.BTGraphicsDriverConfig);
        View vDXWrapperConfig = rootView.findViewById(R.id.BTDXWrapperConfig);
        View vAudioDriverConfig = rootView.findViewById(R.id.BTAudioDriverConfig);
        CPUListView cpuListView = rootView.findViewById(R.id.CPUListView);
        CPUListView cpuListViewWoW64 = rootView.findViewById(R.id.CPUListViewWoW64);

        data.put("screenSize", getScreenSize(rootView));
        data.put("envVars", envVarsView.getEnvVars());
        data.put("cpuList", cpuListView.getCheckedCPUListAsString());
        data.put("cpuListWoW64", cpuListViewWoW64.getCheckedCPUListAsString());
        data.put("graphicsDriver", StringUtils.parseIdentifier(sGraphicsDriver.getSelectedItem()));
        data.put("graphicsDriverConfig", String.valueOf(vGraphicsDriverConfig.getTag()));
        data.put("dxwrapper", StringUtils.parseIdentifier(sDXWrapper.getSelectedItem()));
        data.put("ddrawrapper", StringUtils.parseIdentifier(sDDrawrapper.getSelectedItem()));
        data.put("dxwrapperConfig", String.valueOf(vDXWrapperConfig.getTag()));
        data.put("audioDriver", StringUtils.parseIdentifier(sAudioDriver.getSelectedItem()));
        data.put("audioDriverConfig", String.valueOf(vAudioDriverConfig.getTag()));
        data.put("emulator", StringUtils.parseIdentifier(sEmulator.getSelectedItem()));
        data.put("wincomponents", getWinComponents(rootView));
        data.put("drives", getDrives(rootView));
        data.put("fullscreenStretched", ((CheckBox) rootView.findViewById(R.id.CBFullscreenStretched)).isChecked());
        data.put("wow64Mode", ((CheckBox) rootView.findViewById(R.id.CBWoW64Mode)).isChecked());
        data.put("startupSelection", sStartupSelection.getSelectedItemPosition());
        data.put("box64Version", safeSpinnerValue(sBox64Version, DefaultVersion.BOX64));
        data.put("box64Preset", Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset));
        data.put("fexcoreVersion", safeSpinnerValue(sFEXCoreVersion, DefaultVersion.FEXCORE));
        data.put("fexcorePreset", FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset));
        data.put("desktopTheme", getDesktopTheme(rootView));
        data.put("rcfileId", selectedRcFileId);
        data.put("box64ContentType", getSelectedBoxContentType().toString());
        data.put("midiSoundFont", sMIDISoundFont.getSelectedItemPosition() == 0 ? "" : safeSpinnerValue(sMIDISoundFont, ""));
        data.put("lc_all", getLocaleValue());
        data.put("primaryController", sPrimaryController.getSelectedItemPosition());
        data.put("controllerMapping", getControllerMapping(rootView));
        appendPresetProfilesToSettings(data, sBox64Preset, sFEXCorePreset);

        int finalInputType = 0;
        finalInputType |= ((CheckBox) rootView.findViewById(R.id.CBEnableXInput)).isChecked() ? WinHandler.FLAG_INPUT_TYPE_XINPUT : 0;
        finalInputType |= ((CheckBox) rootView.findViewById(R.id.CBEnableDInput)).isChecked() ? WinHandler.FLAG_INPUT_TYPE_DINPUT : 0;
        finalInputType |= ((Spinner) rootView.findViewById(R.id.SDInputType)).getSelectedItemPosition() == 0
                ? WinHandler.FLAG_DINPUT_MAPPER_STANDARD
                : WinHandler.FLAG_DINPUT_MAPPER_XINPUT;
        data.put("inputType", finalInputType);

        appendWineConfigurationToProfileData(data, rootView);
        return data;
    }

    private void appendWineConfigurationToProfileData(JSONObject data, View view) throws JSONException {
        Spinner sCSMT = view.findViewById(R.id.SCSMT);
        Spinner sGPUName = view.findViewById(R.id.SGPUName);
        Spinner sOffscreenRenderingMode = view.findViewById(R.id.SOffscreenRenderingMode);
        Spinner sStrictShaderMath = view.findViewById(R.id.SStrictShaderMath);
        Spinner sVideoMemorySize = view.findViewById(R.id.SVideoMemorySize);
        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);

        data.put("csmtEnabled", sCSMT.getSelectedItemPosition() != 0);
        data.put("gpuDeviceId", getSelectedGpuDeviceId(sGPUName));
        data.put("offscreenRenderingMode", safeSpinnerValue(sOffscreenRenderingMode, "FBO"));
        data.put("strictShaderMath", sStrictShaderMath.getSelectedItemPosition());
        data.put("videoMemorySize", StringUtils.parseNumber(sVideoMemorySize.getSelectedItem()));
        data.put("mouseWarpOverride", safeSpinnerValue(sMouseWarpOverride, getString(R.string.disable)).toLowerCase(Locale.ENGLISH));
    }

    private void appendPresetProfilesToSettings(JSONObject data, Spinner sBox64Preset, Spinner sFEXCorePreset) throws JSONException {
        String box64PresetId = Box86_64PresetManager.getSpinnerSelectedId(sBox64Preset);
        Box86_64Preset box64Preset = Box86_64PresetManager.getPreset("box64", requireContext(), box64PresetId);
        if (box64Preset != null) {
            JSONObject presetData = new JSONObject();
            presetData.put("id", box64Preset.id);
            presetData.put("name", box64Preset.name);
            presetData.put("envVars", Box86_64PresetManager.getEnvVars("box64", requireContext(), box64Preset.id).toString());
            data.put("box64PresetProfile", presetData);
        }

        String fexcorePresetId = FEXCorePresetManager.getSpinnerSelectedId(sFEXCorePreset);
        FEXCorePreset fexcorePreset = FEXCorePresetManager.getPreset(requireContext(), fexcorePresetId);
        if (fexcorePreset != null) {
            JSONObject presetData = new JSONObject();
            presetData.put("id", fexcorePreset.id);
            presetData.put("name", fexcorePreset.name);
            presetData.put("envVars", FEXCorePresetManager.getEnvVars(requireContext(), fexcorePreset.id).toString());
            data.put("fexcorePresetProfile", presetData);
        }
    }

    private void importContainerProfile(Uri uri) {
        try {
            JSONObject profile = readContainerProfile(uri);
            if (!CONTAINER_PROFILE_TYPE.equals(profile.optString("profileType"))) {
                AppUtils.showToast(requireContext(), R.string.invalid_container_profile);
                return;
            }
            JSONObject settings = profile.optJSONObject("settings");
            if (settings == null) {
                AppUtils.showToast(requireContext(), R.string.invalid_container_profile);
                return;
            }
            showContainerProfilePreview(profile, settings, uri);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to import container profile", e);
            AppUtils.showToast(requireContext(), R.string.failed_to_import_container_profile);
        }
    }

    private void showContainerProfilePreview(JSONObject profile, JSONObject settings, @Nullable Uri sourceUri) {
        if (getContext() == null) return;

        ScrollView scrollView = new ScrollView(getContext());
        int padding = (int)(16 * getResources().getDisplayMetrics().density);
        scrollView.setPadding(padding, padding, padding, padding);

        TextView previewView = new TextView(getContext());
        previewView.setText(buildContainerProfilePreview(profile, settings));
        previewView.setTextIsSelectable(true);
        previewView.setTypeface(Typeface.MONOSPACE);
        previewView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        scrollView.addView(previewView);

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.container_profile_preview)
                .setView(scrollView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.apply_profile, (dialog, which) -> applyContainerProfileSettings(settings, sourceUri))
                .show();
    }

    private CharSequence buildContainerProfilePreview(JSONObject profile, JSONObject settings) {
        ArrayList<String> lines = new ArrayList<>();
        KeyValueSet dxConfig = DXVKConfigDialog.parseConfig(settings.optString("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG));
        String dxWrapper = settings.optString("dxwrapper", "-");
        String dxVersion = "vkd3d".equalsIgnoreCase(dxWrapper)
                ? dxConfig.get("vkd3dVersion", "-")
                : dxConfig.get("version", "-");
        String graphicsDriverVersion = GraphicsDriverConfigDialog.getVersion(settings.optString("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG));
        String profileName = profile.optString("profileName", "");
        if (!profileName.isEmpty()) lines.add(getString(R.string.profile_name_label) + ": " + profileName);
        lines.add(getString(R.string.profile_source_container) + ": " + profile.optString("sourceContainerName", "-"));
        lines.add(getString(R.string.screen_size) + ": " + settings.optString("screenSize", "-"));
        lines.add(getString(R.string.graphics_driver) + ": " + settings.optString("graphicsDriver", "-") + " / " + graphicsDriverVersion);
        lines.add("DX Wrapper: " + settings.optString("dxwrapper", "-") + " / " + dxVersion);
        lines.add("DDraw/Glide: " + settings.optString("ddrawrapper", "-"));
        lines.add(getString(R.string.audio_driver) + ": " + settings.optString("audioDriver", "-"));
        lines.add("Emulator: " + settings.optString("emulator", "-"));
        lines.add("Box64: " + settings.optString("box64Version", "-") + " / " + settings.optString("box64Preset", "-"));
        lines.add("FEXCore: " + settings.optString("fexcoreVersion", "-") + " / " + settings.optString("fexcorePreset", "-"));
        lines.add("Box64 preset config: " + (settings.has("box64PresetProfile") ? getString(R.string.included) : getString(R.string.not_set)));
        lines.add("FEXCore preset config: " + (settings.has("fexcorePresetProfile") ? getString(R.string.included) : getString(R.string.not_set)));
        lines.add(getString(R.string.startup_selection) + ": " + settings.optInt("startupSelection", 0));
        lines.add(getString(R.string.audio_driver) + " config: " + (settings.has("audioDriverConfig") ? getString(R.string.included) : getString(R.string.not_set)));
        lines.add(getString(R.string.graphics_driver) + " config: " + (settings.has("graphicsDriverConfig") ? getString(R.string.included) : getString(R.string.not_set)));
        lines.add("DX config: " + (settings.has("dxwrapperConfig") ? getString(R.string.included) : getString(R.string.not_set)));
        lines.add("Win components: " + settings.optString("wincomponents", "-"));
        lines.add("Drives: " + settings.optString("drives", "-"));
        lines.add("Env Vars: " + settings.optString("envVars", "-"));
        lines.add("MIDI: " + settings.optString("midiSoundFont", getString(R.string.disabled)));
        lines.add("LC_ALL: " + settings.optString("lc_all", "-"));
        lines.add("CSMT: " + booleanLabel(settings.optBoolean("csmtEnabled", true)));
        lines.add("GPU device ID: " + settings.optInt("gpuDeviceId", 0));
        lines.add("Offscreen: " + settings.optString("offscreenRenderingMode", "-"));
        lines.add("Video memory: " + settings.optString("videoMemorySize", "-"));
        lines.add("Mouse warp: " + settings.optString("mouseWarpOverride", "-"));
        lines.add("Fullscreen stretched: " + booleanLabel(settings.optBoolean("fullscreenStretched", false)));
        lines.add("WoW64 mode: " + booleanLabel(settings.optBoolean("wow64Mode", true)));
        lines.add("Input flags: " + settings.optInt("inputType", 0));
        lines.add("RC file id: " + settings.optInt("rcfileId", 0));
        lines.add("XR primary controller: " + settings.optInt("primaryController", 1));
        lines.add("XR mapping: " + (settings.has("controllerMapping") ? getString(R.string.included) : getString(R.string.not_set)));
        lines.add(getString(R.string.bundled_components) + ": " + profile.optInt("bundledComponentsCount", 0));
        return String.join("\n", lines);
    }

    private void applyContainerProfileSettings(JSONObject settings, @Nullable Uri sourceUri) {
        if (rootView == null) return;

        preloaderDialog.show(R.string.please_wait);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (sourceUri != null && isZipUri(sourceUri)) {
                    importBundledComponentsFromArchive(sourceUri);
                }
                if (contentsManager != null) {
                    contentsManager.syncContents();
                }
                requireActivity().runOnUiThread(() -> {
                    try {
                        if (settings.has("screenSize")) {
                            loadScreenSizeSpinner(rootView, settings.optString("screenSize", Container.DEFAULT_SCREEN_SIZE));
                        }
                        rootView.post(() -> {
                            try {
                                applyContainerProfileSettingsDeferred(settings);
                                AppUtils.showToast(requireContext(), R.string.container_profile_applied);
                            }
                            catch (Exception e) {
                                Log.e(TAG, "Failed to apply container profile", e);
                                AppUtils.showToast(requireContext(), R.string.failed_to_import_container_profile);
                            }
                            finally {
                                preloaderDialog.close();
                            }
                        });
                    }
                    catch (Exception e) {
                        preloaderDialog.close();
                        Log.e(TAG, "Failed to prepare imported profile", e);
                        AppUtils.showToast(requireContext(), R.string.failed_to_import_container_profile);
                    }
                });
            }
            catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    preloaderDialog.close();
                    Log.e(TAG, "Failed to import bundled profile components", e);
                    AppUtils.showToast(requireContext(), R.string.failed_to_import_container_profile);
                });
            }
        });
    }

    private void applyContainerProfileSettingsDeferred(JSONObject settings) {
        Spinner sGraphicsDriver = rootView.findViewById(R.id.SGraphicsDriver);
        Spinner sDXWrapper = rootView.findViewById(R.id.SDXWrapper);
        Spinner sDDrawrapper = rootView.findViewById(R.id.SDDrawrapper);
        Spinner sAudioDriver = rootView.findViewById(R.id.SAudioDriver);
        Spinner sEmulator = rootView.findViewById(R.id.SEmulator);
        Spinner sBox64Version = rootView.findViewById(R.id.SBox64Version);
        Spinner sBox64Preset = rootView.findViewById(R.id.SBox64Preset);
        Spinner sFEXCoreVersion = rootView.findViewById(R.id.SFEXCoreVersion);
        Spinner sFEXCorePreset = rootView.findViewById(R.id.SFEXCorePreset);
        Spinner sMIDISoundFont = rootView.findViewById(R.id.SMIDISoundFont);
        Spinner sStartupSelection = rootView.findViewById(R.id.SStartupSelection);
        Spinner sRCFile = rootView.findViewById(R.id.SRCFile);
        Spinner sPrimaryController = rootView.findViewById(R.id.SPrimaryController);
        Spinner sCSMT = rootView.findViewById(R.id.SCSMT);
        Spinner sOffscreenRenderingMode = rootView.findViewById(R.id.SOffscreenRenderingMode);
        Spinner sStrictShaderMath = rootView.findViewById(R.id.SStrictShaderMath);
        Spinner sVideoMemorySize = rootView.findViewById(R.id.SVideoMemorySize);
        Spinner sMouseWarpOverride = rootView.findViewById(R.id.SMouseWarpOverride);
        View vGraphicsDriverConfig = rootView.findViewById(R.id.BTGraphicsDriverConfig);
        View vDXWrapperConfig = rootView.findViewById(R.id.BTDXWrapperConfig);
        View vAudioDriverConfig = rootView.findViewById(R.id.BTAudioDriverConfig);
        CheckBox cbFullscreenStretched = rootView.findViewById(R.id.CBFullscreenStretched);
        CheckBox cbWoW64Mode = rootView.findViewById(R.id.CBWoW64Mode);
        CheckBox cbEnableXInput = rootView.findViewById(R.id.CBEnableXInput);
        CheckBox cbEnableDInput = rootView.findViewById(R.id.CBEnableDInput);
        CheckBox cbSdl2Toggle = rootView.findViewById(R.id.CBSdl2Toggle);
        Spinner sDInputType = rootView.findViewById(R.id.SDInputType);
        CPUListView cpuListView = rootView.findViewById(R.id.CPUListView);
        CPUListView cpuListViewWoW64 = rootView.findViewById(R.id.CPUListViewWoW64);
        EditText etLC_ALL = rootView.findViewById(R.id.ETlcall);

        if (settings.has("envVars") && envVarsView != null) {
            String envVars = settings.optString("envVars", "");
            envVarsView.setEnvVars(new EnvVars(envVars));
            cbSdl2Toggle.setChecked(envVars.contains("SDL_XINPUT_ENABLED=1"));
        }
        if (settings.has("cpuList")) cpuListView.setCheckedCPUList(settings.optString("cpuList", ""));
        if (settings.has("cpuListWoW64")) cpuListViewWoW64.setCheckedCPUList(settings.optString("cpuListWoW64", ""));
        if (settings.has("graphicsDriver")) AppUtils.setSpinnerSelectionFromIdentifier(sGraphicsDriver, settings.optString("graphicsDriver", Container.DEFAULT_GRAPHICS_DRIVER));
        if (settings.has("graphicsDriverConfig")) vGraphicsDriverConfig.setTag(settings.optString("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG));
        if (settings.has("dxwrapper")) AppUtils.setSpinnerSelectionFromIdentifier(sDXWrapper, settings.optString("dxwrapper", Container.DEFAULT_DXWRAPPER));
        if (settings.has("ddrawrapper")) AppUtils.setSpinnerSelectionFromIdentifier(sDDrawrapper, settings.optString("ddrawrapper", Container.DEFAULT_DDRAWRAPPER));
        if (settings.has("dxwrapperConfig")) vDXWrapperConfig.setTag(normalizeImportedDxWrapperConfig(settings.optString("dxwrapper", ""), settings.optString("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG)));
        if (settings.has("audioDriver")) AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, settings.optString("audioDriver", Container.DEFAULT_AUDIO_DRIVER));
        if (settings.has("audioDriverConfig")) vAudioDriverConfig.setTag(settings.optString("audioDriverConfig", "performanceMode=1,volume=1.0,latencyMillis=20"));
        if (settings.has("emulator")) AppUtils.setSpinnerSelectionFromIdentifier(sEmulator, settings.optString("emulator", Container.DEFAULT_EMULATOR));
        if (settings.has("wincomponents")) createWinComponentsTab(rootView, settings.optString("wincomponents", Container.DEFAULT_WINCOMPONENTS));
        if (settings.has("drives")) createDrivesTab(rootView, settings.optString("drives", Container.DEFAULT_DRIVES));
        cbFullscreenStretched.setChecked(settings.optBoolean("fullscreenStretched", false));
        cbWoW64Mode.setChecked(settings.optBoolean("wow64Mode", true));
        sStartupSelection.setSelection(settings.optInt("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL));
        if (settings.has("box64Version")) {
            String normalizedBox64Version = normalizeImportedContentSelection(getBoxContentTypeFromSettings(settings), settings.optString("box64Version", DefaultVersion.BOX64));
            AppUtils.setSpinnerSelectionFromValue(sBox64Version, normalizedBox64Version);
        }
        if (settings.has("box64Preset")) Box86_64PresetManager.loadSpinner("box64", sBox64Preset, resolveImportedBox64PresetId(settings));
        if (settings.has("fexcoreVersion")) {
            String normalizedFexcoreVersion = normalizeImportedContentSelection(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, settings.optString("fexcoreVersion", DefaultVersion.FEXCORE));
            AppUtils.setSpinnerSelectionFromValue(sFEXCoreVersion, normalizedFexcoreVersion);
        }
        if (settings.has("fexcorePreset")) FEXCorePresetManager.loadSpinner(sFEXCorePreset, resolveImportedFEXCorePresetId(settings));
        applyDesktopThemeSettings(settings.optString("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME));
        if (settings.has("rcfileId")) {
            RCManager.loadRCFileSpinner(new RCManager(requireContext()), settings.optInt("rcfileId", 0), sRCFile, id -> selectedRcFileId = id);
        }
        if (settings.has("midiSoundFont")) AppUtils.setSpinnerSelectionFromValue(sMIDISoundFont, settings.optString("midiSoundFont", ""));
        if (settings.has("lc_all")) {
            String locale = settings.optString("lc_all", "");
            etLC_ALL.setTag(locale);
            etLC_ALL.setText(locale);
        }
        sPrimaryController.setSelection(settings.optInt("primaryController", 1));
        if (settings.has("controllerMapping")) applyControllerMapping(rootView, settings.optString("controllerMapping", ""));

        int inputType = settings.optInt("inputType", WinHandler.DEFAULT_INPUT_TYPE);
        cbEnableXInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_XINPUT) == WinHandler.FLAG_INPUT_TYPE_XINPUT);
        cbEnableDInput.setChecked((inputType & WinHandler.FLAG_INPUT_TYPE_DINPUT) == WinHandler.FLAG_INPUT_TYPE_DINPUT);
        sDInputType.setSelection((inputType & WinHandler.FLAG_DINPUT_MAPPER_STANDARD) == WinHandler.FLAG_DINPUT_MAPPER_STANDARD ? 0 : 1);

        sCSMT.setSelection(settings.optBoolean("csmtEnabled", true) ? 1 : 0);
        if (settings.has("gpuDeviceId")) loadGPUNameSpinner((Spinner) rootView.findViewById(R.id.SGPUName), settings.optInt("gpuDeviceId", 1728));
        if (settings.has("offscreenRenderingMode")) AppUtils.setSpinnerSelectionFromValue(sOffscreenRenderingMode, settings.optString("offscreenRenderingMode", "fbo"));
        sStrictShaderMath.setSelection(Math.min(settings.optInt("strictShaderMath", 1), 1));
        if (settings.has("videoMemorySize")) AppUtils.setSpinnerSelectionFromNumber(sVideoMemorySize, settings.optString("videoMemorySize", "2048"));
        if (settings.has("mouseWarpOverride")) AppUtils.setSpinnerSelectionFromValue(sMouseWarpOverride, settings.optString("mouseWarpOverride", "disable"));

        updateEmulatorConfigVisibility(rootView);
    }

    private void applyDesktopThemeSettings(String desktopThemeValue) {
        Spinner sDesktopTheme = rootView.findViewById(R.id.SDesktopTheme);
        Spinner sDesktopBackgroundType = rootView.findViewById(R.id.SDesktopBackgroundType);
        ColorPickerView cpvDesktopBackgroundColor = rootView.findViewById(R.id.CPVDesktopBackgroundColor);

        WineThemeManager.ThemeInfo themeInfo = new WineThemeManager.ThemeInfo(desktopThemeValue);
        sDesktopTheme.setSelection(themeInfo.theme.ordinal());
        sDesktopBackgroundType.setSelection(themeInfo.backgroundType.ordinal());
        cpvDesktopBackgroundColor.setColor(themeInfo.backgroundColor);
    }

    private void applyControllerMapping(View view, String controllerMapping) {
        if (controllerMapping == null || controllerMapping.isEmpty()) return;
        int[] ids = {
                R.id.SButtonA, R.id.SButtonB, R.id.SButtonX, R.id.SButtonY, R.id.SButtonGrip, R.id.SButtonTrigger,
                R.id.SThumbstickUp, R.id.SThumbstickDown, R.id.SThumbstickLeft, R.id.SThumbstickRight
        };
        XKeycode[] values = XKeycode.values();
        for (int i = 0; i < Math.min(ids.length, controllerMapping.length()); i++) {
            Spinner spinner = view.findViewById(ids[i]);
            byte keycode = (byte)controllerMapping.charAt(i);
            for (int j = 0; j < values.length; j++) {
                if (values[j].id == keycode) {
                    spinner.setSelection(j);
                    break;
                }
            }
        }
    }

    private int getSelectedGpuDeviceId(Spinner spinner) {
        try {
            JSONObject gpuName = gpuCards.getJSONObject(spinner.getSelectedItemPosition());
            return gpuName.getInt("deviceID");
        }
        catch (Exception e) {
            return 1728;
        }
    }

    private String getLocaleValue() {
        EditText etLC_ALL = rootView.findViewById(R.id.ETlcall);
        return etLC_ALL.getTag() != null ? etLC_ALL.getTag().toString() : etLC_ALL.getText().toString();
    }

    private String readTextFromUri(Uri uri) throws Exception {
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) throw new IllegalStateException("Input stream is null");
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString("UTF-8");
        }
    }

    private JSONObject readContainerProfile(Uri uri) throws Exception {
        if (isZipUri(uri)) {
            return readContainerProfileFromArchive(uri);
        }
        return new JSONObject(readTextFromUri(uri));
    }

    private boolean isZipUri(Uri uri) {
        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType != null && mimeType.toLowerCase(Locale.ENGLISH).contains("zip")) {
            return true;
        }
        android.database.Cursor cursor = null;
        try {
            cursor = requireContext().getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (index != -1) {
                    String name = cursor.getString(index);
                    if (name != null && name.toLowerCase(Locale.ENGLISH).endsWith(".zip")) {
                        return true;
                    }
                }
            }
        }
        catch (Exception ignored) {
        }
        finally {
            if (cursor != null) cursor.close();
        }
        String path = uri.toString().toLowerCase(Locale.ENGLISH);
        return path.endsWith(".zip");
    }

    private JSONObject readContainerProfileFromArchive(Uri uri) throws Exception {
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             ZipInputStream zis = inputStream != null ? new ZipInputStream(inputStream) : null) {
            if (zis == null) throw new IllegalStateException("Input stream is null");
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("profile.json".equals(entry.getName())) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                    }
                    return new JSONObject(outputStream.toString("UTF-8"));
                }
            }
        }
        throw new IllegalStateException("profile.json not found in archive");
    }

    private void writeContainerProfileArchive(OutputStream outputStream, JSONObject profile) throws Exception {
        profile.put("bundledComponentsCount", countBundledComponents(profile.optJSONObject("settings")));
        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            addStringToZip(zos, "profile.json", profile.toString(2));
            addBundledComponentsToArchive(zos, profile.optJSONObject("settings"));
            zos.finish();
        }
    }

    private int countBundledComponents(@Nullable JSONObject settings) throws Exception {
        if (settings == null || contentsManager == null) return 0;
        contentsManager.syncContents();
        int bundledCount = 0;

        KeyValueSet dxConfig = DXVKConfigDialog.parseConfig(settings.optString("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG));
        String dxWrapper = settings.optString("dxwrapper", "");
        if ("dxvk".equalsIgnoreCase(dxWrapper) || "d8vk".equalsIgnoreCase(dxWrapper)) {
            String version = dxConfig.get("version", "");
            if (hasInstalledContentVersion(ContentProfile.ContentType.CONTENT_TYPE_DXVK, version)
                    || hasBundledAssetDxWrapper(ContentProfile.ContentType.CONTENT_TYPE_DXVK, version)) bundledCount++;
        }
        else if ("vkd3d".equalsIgnoreCase(dxWrapper)) {
            String version = dxConfig.get("vkd3dVersion", "");
            if (hasInstalledContentVersion(ContentProfile.ContentType.CONTENT_TYPE_VKD3D, version)
                    || hasBundledAssetDxWrapper(ContentProfile.ContentType.CONTENT_TYPE_VKD3D, version)) bundledCount++;
        }

        ContentProfile.ContentType boxType = getBoxContentTypeFromSettings(settings);
        String boxVersion = settings.optString("box64Version", "");
        if (hasInstalledContentVersion(boxType, boxVersion) || hasBundledAssetContent(boxType, boxVersion)) bundledCount++;
        String fexcoreVersion = settings.optString("fexcoreVersion", "");
        if (hasInstalledContentVersion(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, fexcoreVersion)
                || hasBundledAssetContent(ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, fexcoreVersion)) bundledCount++;

        String graphicsDriverVersion = GraphicsDriverConfigDialog.getVersion(settings.optString("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG));
        if (graphicsDriverVersion != null && !graphicsDriverVersion.isEmpty() && !DefaultVersion.WRAPPER.equalsIgnoreCase(graphicsDriverVersion)) {
            File driverDir = resolveInstalledGraphicsDriverDir(graphicsDriverVersion);
            if ((driverDir != null && driverDir.exists()) || hasBundledAssetGraphicsDriver(graphicsDriverVersion)) bundledCount++;
        }

        return bundledCount;
    }

    private int addBundledComponentsToArchive(ZipOutputStream zos, @Nullable JSONObject settings) throws Exception {
        if (settings == null || contentsManager == null) return 0;
        contentsManager.syncContents();
        int bundledCount = 0;

        KeyValueSet dxConfig = DXVKConfigDialog.parseConfig(settings.optString("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG));
        String dxWrapper = settings.optString("dxwrapper", "");
        if ("dxvk".equalsIgnoreCase(dxWrapper) || "d8vk".equalsIgnoreCase(dxWrapper)) {
            String version = dxConfig.get("version", "");
            bundledCount += addInstalledContentVersionToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_DXVK, version);
            if (bundledCount == 0) bundledCount += addBundledAssetDxWrapperToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_DXVK, version);
        } else if ("vkd3d".equalsIgnoreCase(dxWrapper)) {
            String version = dxConfig.get("vkd3dVersion", "");
            bundledCount += addInstalledContentVersionToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_VKD3D, version);
            if (bundledCount == 0) bundledCount += addBundledAssetDxWrapperToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_VKD3D, version);
        }

        ContentProfile.ContentType boxType = getBoxContentTypeFromSettings(settings);
        String boxVersion = settings.optString("box64Version", "");
        int boxBundled = addInstalledContentVersionToArchive(zos, boxType, boxVersion);
        if (boxBundled == 0) boxBundled = addBundledAssetContentToArchive(zos, boxType, boxVersion);
        bundledCount += boxBundled;

        String fexcoreVersion = settings.optString("fexcoreVersion", "");
        int fexBundled = addInstalledContentVersionToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, fexcoreVersion);
        if (fexBundled == 0) fexBundled = addBundledAssetContentToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, fexcoreVersion);
        bundledCount += fexBundled;

        String graphicsDriverVersion = GraphicsDriverConfigDialog.getVersion(settings.optString("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG));
        bundledCount += addGraphicsDriverToArchive(zos, graphicsDriverVersion);
        return bundledCount;
    }

    private int addInstalledContentVersionToArchive(ZipOutputStream zos, ContentProfile.ContentType type, String version) throws Exception {
        if (version == null || version.isEmpty() || contentsManager == null) return 0;
        List<ContentProfile> profiles = contentsManager.getProfiles(type);
        if (profiles != null) {
            for (ContentProfile profile : profiles) {
                if (matchesContentVersion(profile, version) && ContentsManager.getInstallDir(requireContext(), profile).exists()) {
                    return addContentProfileToArchive(zos, profile);
                }
            }
        }
        File directInstallDir = resolveInstalledContentDir(type, version);
        if (directInstallDir != null && directInstallDir.exists()) {
            zipDirectory(zos, directInstallDir, "contents/" + type + "/" + directInstallDir.getName());
            return 1;
        }
        return 0;
    }

    private boolean hasInstalledContentVersion(ContentProfile.ContentType type, String version) {
        if (version == null || version.isEmpty() || contentsManager == null) return false;
        List<ContentProfile> profiles = contentsManager.getProfiles(type);
        if (profiles != null) {
            for (ContentProfile profile : profiles) {
                if (matchesContentVersion(profile, version) && ContentsManager.getInstallDir(requireContext(), profile).exists()) {
                    return true;
                }
            }
        }
        File directInstallDir = resolveInstalledContentDir(type, version);
        return directInstallDir != null && directInstallDir.exists();
    }

    private int addContentProfileToArchive(ZipOutputStream zos, @Nullable ContentProfile profile) throws Exception {
        if (profile == null) return 0;
        File installDir = ContentsManager.getInstallDir(requireContext(), profile);
        if (!installDir.exists()) return 0;
        zipDirectory(zos, installDir, "contents/" + profile.type + "/" + installDir.getName());
        return 1;
    }

    private int addGraphicsDriverToArchive(ZipOutputStream zos, String graphicsDriverVersion) throws Exception {
        if (graphicsDriverVersion == null || graphicsDriverVersion.isEmpty()) return 0;
        if (DefaultVersion.WRAPPER.equalsIgnoreCase(graphicsDriverVersion)) return 0;
        File driverDir = resolveInstalledGraphicsDriverDir(graphicsDriverVersion);
        if (driverDir != null && driverDir.exists()) {
            zipDirectory(zos, driverDir, "adrenotools/" + driverDir.getName());
            return 1;
        }
        return addBundledAssetGraphicsDriverToArchive(zos, graphicsDriverVersion);
    }

    private void zipDirectory(ZipOutputStream zos, File dir, String basePath) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String entryName = basePath + "/" + file.getName();
            if (file.isDirectory()) {
                zipDirectory(zos, file, entryName);
            } else {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private void addStringToZip(ZipOutputStream zos, String entryName, String content) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes("UTF-8"));
        zos.closeEntry();
    }

    private void importBundledComponentsFromArchive(Uri uri) throws Exception {
        File contentsRoot = ContentsManager.getContentDir(requireContext());
        File adrenotoolsRoot = new File(requireContext().getFilesDir(), "imagefs/contents/adrenotools");
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
             ZipInputStream zis = inputStream != null ? new ZipInputStream(inputStream) : null) {
            if (zis == null) throw new IllegalStateException("Input stream is null");
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || "profile.json".equals(entry.getName())) continue;
                if (entry.getName().startsWith("contents/")) {
                    writeArchiveEntry(zis, entry.getName().substring("contents/".length()), contentsRoot);
                } else if (entry.getName().startsWith("adrenotools/")) {
                    writeArchiveEntry(zis, entry.getName().substring("adrenotools/".length()), adrenotoolsRoot);
                }
            }
        }
    }

    private void writeArchiveEntry(ZipInputStream zis, String relativePath, File baseDir) throws Exception {
        File outFile = new File(baseDir, relativePath);
        String canonicalBase = baseDir.getCanonicalPath() + File.separator;
        String canonicalOut = outFile.getCanonicalPath();
        if (!canonicalOut.startsWith(canonicalBase)) {
            throw new SecurityException("Invalid archive entry path");
        }
        File parent = outFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStream os = new java.io.FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = zis.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
    }

    private String sanitizeProfileFileName(String name) {
        String safe = name == null ? "container-profile" : name.trim();
        if (safe.isEmpty()) safe = "container-profile";
        safe = safe.replaceAll("[^a-zA-Z0-9._-]+", "_");
        return safe.toLowerCase(Locale.ENGLISH);
    }

    private boolean matchesContentVersion(@Nullable ContentProfile profile, @Nullable String selectedVersion) {
        if (profile == null || selectedVersion == null || selectedVersion.isEmpty()) return false;

        String normalizedSelected = selectedVersion.trim();
        String versionName = profile.verName == null ? "" : profile.verName;
        String versionWithCode = versionName + "-" + profile.verCode;
        String versionWithUnderscoreCode = versionName + "_v" + profile.verCode;
        String entryName = ContentsManager.getEntryName(profile);
        String spinnerValue = getVersionSpinnerValue(profile);

        return normalizedSelected.equalsIgnoreCase(versionName)
                || normalizedSelected.equalsIgnoreCase(versionWithCode)
                || normalizedSelected.equalsIgnoreCase(versionWithUnderscoreCode)
                || normalizedSelected.equalsIgnoreCase(entryName)
                || normalizedSelected.equalsIgnoreCase(spinnerValue)
                || spinnerValue.equalsIgnoreCase(versionWithCode);
    }

    private ContentProfile.ContentType getSelectedBoxContentType() {
        return isCurrentWineArm64EC()
                ? ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64
                : ContentProfile.ContentType.CONTENT_TYPE_BOX64;
    }

    private ContentProfile.ContentType getBoxContentTypeFromSettings(@NonNull JSONObject settings) {
        String storedType = settings.optString("box64ContentType", "");
        if (ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64.toString().equalsIgnoreCase(storedType)) {
            return ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64;
        }
        if (ContentProfile.ContentType.CONTENT_TYPE_BOX64.toString().equalsIgnoreCase(storedType)) {
            return ContentProfile.ContentType.CONTENT_TYPE_BOX64;
        }
        return getSelectedBoxContentType();
    }

    @NonNull
    private String normalizeImportedDxWrapperConfig(@Nullable String dxWrapper, @Nullable String configValue) {
        KeyValueSet config = DXVKConfigDialog.parseConfig(configValue);
        if ("dxvk".equalsIgnoreCase(dxWrapper) || "d8vk".equalsIgnoreCase(dxWrapper)) {
            String normalizedVersion = normalizeImportedContentSelection(ContentProfile.ContentType.CONTENT_TYPE_DXVK, config.get("version", ""));
            if (normalizedVersion != null && !normalizedVersion.isEmpty()) config.put("version", normalizedVersion);
        }
        else if ("vkd3d".equalsIgnoreCase(dxWrapper)) {
            String normalizedVersion = normalizeImportedContentSelection(ContentProfile.ContentType.CONTENT_TYPE_VKD3D, config.get("vkd3dVersion", ""));
            if (normalizedVersion != null && !normalizedVersion.isEmpty()) config.put("vkd3dVersion", normalizedVersion);
        }
        return config.toString();
    }

    @Nullable
    private String resolveInstalledContentVersionValue(@NonNull ContentProfile.ContentType type, @Nullable String version) {
        if (version == null || version.isEmpty() || contentsManager == null) return null;
        List<ContentProfile> profiles = contentsManager.getProfiles(type);
        if (profiles == null) return null;
        for (ContentProfile profile : profiles) {
            if (matchesContentVersion(profile, version) && ContentsManager.getInstallDir(requireContext(), profile).exists()) {
                return getVersionSpinnerValue(profile);
            }
        }
        return null;
    }

    @NonNull
    private String normalizeImportedContentSelection(@NonNull ContentProfile.ContentType type, @Nullable String version) {
        if (version == null || version.isEmpty()) return "";
        String normalizedVersion = resolveInstalledContentVersionValue(type, version);
        return normalizedVersion != null && !normalizedVersion.isEmpty() ? normalizedVersion : version;
    }

    private boolean hasBundledAssetDxWrapper(@NonNull ContentProfile.ContentType type, @Nullable String version) {
        String assetPath = getDxWrapperAssetPath(type, version);
        if (assetPath == null) return false;
        try (InputStream ignored = requireContext().getAssets().open(assetPath)) {
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private int addBundledAssetDxWrapperToArchive(ZipOutputStream zos, @NonNull ContentProfile.ContentType type, @Nullable String version) throws Exception {
        String assetPath = getBundledAssetContentPath(type, version);
        if (assetPath == null) return 0;

        File tempRoot = new File(requireContext().getCacheDir(), "profile_export_assets/" + type + "/" + sanitizeProfileFileName(version));
        FileUtils.delete(tempRoot);
        tempRoot.mkdirs();

        try {
            if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, requireContext(), assetPath, tempRoot)) {
                return 0;
            }
            JSONObject profileJson = buildSyntheticDxWrapperProfile(type, version, tempRoot);
            FileUtils.writeString(new File(tempRoot, ContentsManager.PROFILE_NAME), profileJson.toString());
            String syntheticDirName = sanitizeProfileFileName(version) + "-0";
            zipDirectory(zos, tempRoot, "contents/" + type + "/" + syntheticDirName);
            return 1;
        }
        finally {
            FileUtils.delete(tempRoot);
        }
    }

    private JSONObject buildSyntheticDxWrapperProfile(@NonNull ContentProfile.ContentType type, @Nullable String version, @NonNull File rootDir) throws JSONException {
        JSONArray files = new JSONArray();
        appendSyntheticDxWrapperFiles(files, rootDir, rootDir);

        JSONObject profile = new JSONObject();
        profile.put(ContentProfile.MARK_TYPE, type.toString());
        profile.put(ContentProfile.MARK_VERSION_NAME, version != null ? version : "bundled");
        profile.put(ContentProfile.MARK_VERSION_CODE, 0);
        profile.put(ContentProfile.MARK_DESC, "Bundled from app assets");
        profile.put(ContentProfile.MARK_FILE_LIST, files);
        return profile;
    }

    private void appendSyntheticDxWrapperFiles(@NonNull JSONArray files, @NonNull File rootDir, @NonNull File currentDir) throws JSONException {
        File[] children = currentDir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                appendSyntheticDxWrapperFiles(files, rootDir, child);
                continue;
            }

            String relativePath = rootDir.toURI().relativize(child.toURI()).getPath();
            String target = toSyntheticContentTarget(relativePath);
            if (target == null) continue;

            JSONObject fileJson = new JSONObject();
            fileJson.put(ContentProfile.MARK_FILE_SOURCE, relativePath);
            fileJson.put(ContentProfile.MARK_FILE_TARGET, target);
            files.put(fileJson);
        }
    }

    @Nullable
    private String toSyntheticContentTarget(@Nullable String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return null;
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("usr/bin/")) {
            return "${bindir}/" + normalized.substring("usr/bin/".length());
        }
        if (normalized.startsWith("usr/local/bin/")) {
            return "${bindir}/" + normalized.substring("usr/local/bin/".length());
        }
        if (normalized.startsWith("usr/lib/")) {
            return "${libdir}/" + normalized.substring("usr/lib/".length());
        }
        if (normalized.startsWith("usr/share/")) {
            return "${sharedir}/" + normalized.substring("usr/share/".length());
        }
        if (normalized.startsWith("system32/")) {
            return "${system32}/" + normalized.substring("system32/".length());
        }
        if (normalized.startsWith("syswow64/")) {
            return "${syswow64}/" + normalized.substring("syswow64/".length());
        }
        if (normalized.startsWith("windows/system32/")) {
            return "${system32}/" + normalized.substring("windows/system32/".length());
        }
        if (normalized.startsWith("windows/syswow64/")) {
            return "${syswow64}/" + normalized.substring("windows/syswow64/".length());
        }
        return null;
    }

    @Nullable
    private String getDxWrapperAssetPath(@NonNull ContentProfile.ContentType type, @Nullable String version) {
        return getBundledAssetContentPath(type, version);
    }

    private boolean hasBundledAssetGraphicsDriver(@Nullable String version) {
        String assetPath = getBundledGraphicsDriverAssetPath(version);
        if (assetPath == null) return false;
        try (InputStream ignored = requireContext().getAssets().open(assetPath)) {
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private int addBundledAssetGraphicsDriverToArchive(ZipOutputStream zos, @Nullable String version) throws Exception {
        String assetPath = getBundledGraphicsDriverAssetPath(version);
        if (assetPath == null) return 0;

        File tempRoot = new File(requireContext().getCacheDir(), "profile_export_assets/driver/" + sanitizeProfileFileName(version));
        FileUtils.delete(tempRoot);
        tempRoot.mkdirs();

        try {
            if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, requireContext(), assetPath, tempRoot)) {
                return 0;
            }
            zipDirectory(zos, tempRoot, "adrenotools/" + tempRoot.getName());
            return 1;
        }
        finally {
            FileUtils.delete(tempRoot);
        }
    }

    @Nullable
    private String getBundledGraphicsDriverAssetPath(@Nullable String version) {
        if (version == null || version.isEmpty()) return null;
        if ("v762".equalsIgnoreCase(version) || "v805".equalsIgnoreCase(version)) {
            return "graphics_driver/adrenotools-" + version.toLowerCase(Locale.ENGLISH) + ".tzst";
        }
        return null;
    }

    @Nullable
    private File resolveInstalledContentDir(@NonNull ContentProfile.ContentType type, @Nullable String selectedVersion) {
        if (selectedVersion == null || selectedVersion.isEmpty()) return null;

        File typeDir = ContentsManager.getContentTypeDir(requireContext(), type);
        File[] installDirs = typeDir.listFiles();
        if (installDirs == null) return null;

        String normalizedSelected = selectedVersion.trim();
        for (File installDir : installDirs) {
            if (!installDir.isDirectory()) continue;
            String dirName = installDir.getName();
            if (dirName.equalsIgnoreCase(normalizedSelected)) {
                return installDir;
            }
        }

        for (File installDir : installDirs) {
            if (!installDir.isDirectory()) continue;
            String dirVersionName = extractVersionNameFromInstallDir(installDir.getName());
            if (dirVersionName != null && dirVersionName.equalsIgnoreCase(normalizedSelected)) {
                return installDir;
            }
        }
        return null;
    }

    @Nullable
    private String extractVersionNameFromInstallDir(@Nullable String dirName) {
        if (dirName == null || dirName.isEmpty()) return null;
        int lastDash = dirName.lastIndexOf('-');
        if (lastDash <= 0 || lastDash >= dirName.length() - 1) return null;
        String suffix = dirName.substring(lastDash + 1);
        if (!suffix.matches("\\d+")) return null;
        return dirName.substring(0, lastDash);
    }

    private boolean hasBundledAssetContent(@NonNull ContentProfile.ContentType type, @Nullable String version) {
        String assetPath = getBundledAssetContentPath(type, version);
        if (assetPath == null) return false;
        try (InputStream ignored = requireContext().getAssets().open(assetPath)) {
            return true;
        }
        catch (Exception ignored) {
            return false;
        }
    }

    private int addBundledAssetContentToArchive(ZipOutputStream zos, @NonNull ContentProfile.ContentType type, @Nullable String version) throws Exception {
        String assetPath = getBundledAssetContentPath(type, version);
        if (assetPath == null) return 0;

        File tempRoot = new File(requireContext().getCacheDir(), "profile_export_assets/" + type + "/" + sanitizeProfileFileName(version));
        FileUtils.delete(tempRoot);
        tempRoot.mkdirs();

        try {
            if (!TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, requireContext(), assetPath, tempRoot)) {
                return 0;
            }
            JSONObject profileJson = buildSyntheticDxWrapperProfile(type, version, tempRoot);
            FileUtils.writeString(new File(tempRoot, ContentsManager.PROFILE_NAME), profileJson.toString());
            String syntheticDirName = sanitizeProfileFileName(version) + "-0";
            zipDirectory(zos, tempRoot, "contents/" + type + "/" + syntheticDirName);
            return 1;
        }
        finally {
            FileUtils.delete(tempRoot);
        }
    }

    @Nullable
    private String getBundledAssetContentPath(@NonNull ContentProfile.ContentType type, @Nullable String version) {
        if (version == null || version.isEmpty()) return null;
        if (type == ContentProfile.ContentType.CONTENT_TYPE_DXVK) {
            return "dxwrapper/dxvk-" + version + ".tzst";
        }
        if (type == ContentProfile.ContentType.CONTENT_TYPE_VKD3D) {
            return "dxwrapper/vkd3d-" + version + "-0.tzst";
        }
        if (type == ContentProfile.ContentType.CONTENT_TYPE_BOX64) {
            return "box86_64/box64-" + version + ".tzst";
        }
        if (type == ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64) {
            return "wowbox64/wowbox64-" + version + ".tzst";
        }
        if (type == ContentProfile.ContentType.CONTENT_TYPE_FEXCORE) {
            return "fexcore/fexcore-" + version + ".tzst";
        }
        return null;
    }

    @NonNull
    private String resolveImportedBox64PresetId(@NonNull JSONObject settings) {
        String originalId = settings.optString("box64Preset", Box86_64Preset.COMPATIBILITY);
        JSONObject presetProfile = settings.optJSONObject("box64PresetProfile");
        if (presetProfile == null) return originalId;

        Box86_64Preset existingPreset = Box86_64PresetManager.getPreset("box64", requireContext(), originalId);
        if (existingPreset != null && !originalId.startsWith(Box86_64Preset.CUSTOM)) {
            return originalId;
        }

        String name = presetProfile.optString("name", "Imported Box64");
        String envVars = presetProfile.optString("envVars", "");
        int nextId = Box86_64PresetManager.getNextPresetId(requireContext(), "box64");
        String importedId = Box86_64Preset.CUSTOM + "-" + nextId;
        Box86_64PresetManager.importPreset("box64", requireContext(), new ByteArrayInputStream(
                ("Type:box64\nName:" + name + "\nEnvVars:" + envVars + "\n").getBytes()
        ));
        return importedId;
    }

    @NonNull
    private String resolveImportedFEXCorePresetId(@NonNull JSONObject settings) {
        String originalId = settings.optString("fexcorePreset", FEXCorePreset.INTERMEDIATE);
        JSONObject presetProfile = settings.optJSONObject("fexcorePresetProfile");
        if (presetProfile == null) return originalId;

        FEXCorePreset existingPreset = FEXCorePresetManager.getPreset(requireContext(), originalId);
        if (existingPreset != null && !originalId.startsWith(FEXCorePreset.CUSTOM)) {
            return originalId;
        }

        String name = presetProfile.optString("name", "Imported FEXCore");
        String envVars = presetProfile.optString("envVars", "");
        int nextId = FEXCorePresetManager.getNextPresetId(requireContext());
        String importedId = FEXCorePreset.CUSTOM + "-" + nextId;
        FEXCorePresetManager.importPreset(requireContext(), new ByteArrayInputStream(
                ("ID:" + importedId + "\nName:" + name + "\nEnvVars:" + envVars + "\n").getBytes()
        ));
        return importedId;
    }

    @Nullable
    private File resolveInstalledGraphicsDriverDir(@Nullable String selectedVersion) {
        if (selectedVersion == null || selectedVersion.isEmpty()) return null;

        AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(requireContext());
        for (String driverId : adrenotoolsManager.enumarateInstalledDrivers()) {
            if (selectedVersion.equalsIgnoreCase(driverId)
                    || selectedVersion.equalsIgnoreCase(adrenotoolsManager.getDriverName(driverId))
                    || selectedVersion.equalsIgnoreCase(adrenotoolsManager.getDriverVersion(driverId))
                    || selectedVersion.contains(adrenotoolsManager.getDriverName(driverId))) {
                return new File(requireContext().getFilesDir(), "imagefs/contents/adrenotools/" + driverId);
            }
        }

        File directDir = new File(requireContext().getFilesDir(), "imagefs/contents/adrenotools/" + selectedVersion);
        return directDir.exists() ? directDir : null;
    }

    private String safeSpinnerValue(Spinner spinner, String fallback) {
        Object selected = spinner.getSelectedItem();
        return selected != null ? selected.toString() : fallback;
    }

    private String booleanLabel(boolean value) {
        return getString(value ? R.string.yes : R.string.no);
    }

    // Helper method to apply dark theme to EditText
    private static void applyDarkThemeToEditText(EditText editText) {
        if (isDarkMode) {
            editText.setTextColor(Color.WHITE); // Set text color to white for dark theme
            editText.setHintTextColor(Color.GRAY); // Set hint color to gray
            editText.setBackgroundResource(R.drawable.edit_text_dark); // Custom dark background drawable
        } else {
            editText.setTextColor(Color.BLACK); // Default text color
            editText.setHintTextColor(Color.GRAY); // Default hint color
            editText.setBackgroundResource(R.drawable.edit_text); // Custom light background drawable
        }
    }

    // Helper method to apply dark theme to buttons or other clickable views
    private void applyDarkThemeToButton(View button) {

    }

    private void loadWineVersionSpinner(final View view, Spinner sWineVersion, Spinner sBox64Version) {
        final Context context = getContext();
        sWineVersion.setEnabled(!isEditMode());
//
        sWineVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                CheckBox cbWoW64Mode = view.findViewById(R.id.CBWoW64Mode);
                FrameLayout fexcoreFL = view.findViewById(R.id.fexcoreFrame);
                FrameLayout box86box64FL = view.findViewById(R.id.box86box64Frame);
                Spinner sEmulator = view.findViewById(R.id.SEmulator);
                Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
                sEmulator64.setEnabled(false);
                String wineVersion = sWineVersion.getSelectedItem().toString();
                WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
                if (wineInfo.isArm64EC()) {
                    fexcoreFL.setVisibility(View.VISIBLE);
                    sEmulator.setEnabled(true);
                    sEmulator64.setSelection(0);
                    if (!isEditMode()) sEmulator.setSelection(0);
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
                updateEmulatorConfigVisibility(view);
                loadBox64VersionSpinner(context, container, contentsManager, sBox64Version, wineInfo.isArm64EC());
                cbWoW64Mode.setEnabled(true); // Always allow user to toggle WoW64 mode
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                FrameLayout fexcoreFL = view.findViewById(R.id.fexcoreFrame);
                FrameLayout box86box64FL = view.findViewById(R.id.box86box64Frame);
                Spinner sEmulator = view.findViewById(R.id.SEmulator);
                Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);
                sEmulator64.setEnabled(false);
                String wineVersion = sWineVersion.getSelectedItem().toString();
                WineInfo wineInfo = WineInfo.fromIdentifier(context, contentsManager, wineVersion);
                if (wineInfo.isArm64EC()) {
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
                updateEmulatorConfigVisibility(view);
                loadBox64VersionSpinner(context, container, contentsManager, sBox64Version, wineInfo.isArm64EC());
            }
        });


        view.findViewById(R.id.LLWineVersion).setVisibility(View.VISIBLE);
        ArrayList<WineSpinnerItem> items = new ArrayList<>();
        String[] versions = getResources().getStringArray(R.array.wine_entries);
        for (String version : versions) {
            items.add(WineSpinnerItem.builtin(version));
        }
        List<ContentProfile> profiles = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE);
        if (profiles != null) {
            java.util.LinkedHashMap<String, WineSpinnerItem> deduped = new java.util.LinkedHashMap<>();
            for (ContentProfile profile : profiles) {
                WineSpinnerItem item = WineSpinnerItem.fromProfile(context, profile);
                if (!item.isInstalled()) continue;
                WineSpinnerItem existing = deduped.get(item.toString());
                if (existing == null || item.isInstalled()) {
                    deduped.put(item.toString(), item);
                }
            }
            items.addAll(deduped.values());
        }

        final WineDownloadHandler handler = new WineDownloadHandler() {
            @Override
            public void onDownloadRequested(ContentProfile profile, Runnable onStateChanged) {
                downloadAndInstallContent(profile, onStateChanged, () -> {
                    contentsManager.syncContents();
                    ArrayList<WineSpinnerItem> refreshed = new ArrayList<>();
                    for (String version : versions) {
                        refreshed.add(WineSpinnerItem.builtin(version));
                    }
                    List<ContentProfile> refreshedProfiles = contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE);
                    if (refreshedProfiles != null) {
                        java.util.LinkedHashMap<String, WineSpinnerItem> deduped = new java.util.LinkedHashMap<>();
                        for (ContentProfile p : refreshedProfiles) {
                            WineSpinnerItem item = WineSpinnerItem.fromProfile(context, p);
                            if (!item.isInstalled()) continue;
                            WineSpinnerItem existing = deduped.get(item.toString());
                            if (existing == null || item.isInstalled()) {
                                deduped.put(item.toString(), item);
                            }
                        }
                        refreshed.addAll(deduped.values());
                    }

                    WineSpinnerAdapter newAdapter = new WineSpinnerAdapter(context, refreshed, this);
                    sWineVersion.setAdapter(newAdapter);
                    AppUtils.setSpinnerSelectionFromValue(sWineVersion, ContentsManager.getEntryName(profile));
                });
            }
        };

        WineSpinnerAdapter adapter = new WineSpinnerAdapter(context, items, handler);
        sWineVersion.setAdapter(adapter);
        if (isEditMode()) AppUtils.setSpinnerSelectionFromValue(sWineVersion, container.getWineVersion());
    }

    private static final class WineSpinnerItem {
        private final String id;
        @Nullable
        private final ContentProfile profile;
        private final boolean builtin;
        private final boolean installed;

        private WineSpinnerItem(String id, @Nullable ContentProfile profile, boolean builtin, boolean installed) {
            this.id = id;
            this.profile = profile;
            this.builtin = builtin;
            this.installed = installed;
        }

        static WineSpinnerItem builtin(String id) {
            return new WineSpinnerItem(id, null, true, true);
        }

        static WineSpinnerItem fromProfile(@NonNull Context context, @NonNull ContentProfile profile) {
            boolean installed = profile.remoteUrl == null || ContentsManager.getInstallDir(context, profile).exists();
            return new WineSpinnerItem(ContentsManager.getEntryName(profile), profile, false, installed);
        }

        boolean isDownloadable() {
            return !builtin && profile != null && profile.remoteUrl != null && !installed;
        }

        boolean isInstalled() {
            return installed;
        }

        @NonNull
        @Override
        public String toString() {
            return id;
        }
    }

    private interface WineDownloadHandler {
        void onDownloadRequested(ContentProfile profile, Runnable onStateChanged);
    }

    private final class WineSpinnerAdapter extends ArrayAdapter<WineSpinnerItem> {
        private final LayoutInflater inflater;
        private final WineDownloadHandler downloadHandler;

        public WineSpinnerAdapter(@NonNull Context context, @NonNull List<WineSpinnerItem> objects, @NonNull WineDownloadHandler downloadHandler) {
            super(context, 0, objects);
            this.inflater = LayoutInflater.from(context);
            this.downloadHandler = downloadHandler;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = convertView;
            if (view == null) view = inflater.inflate(R.layout.wine_item, parent, false);
            bind(view, getItem(position), false);
            return view;
        }

        @NonNull
        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View view = convertView;
            if (view == null) view = inflater.inflate(R.layout.wine_item, parent, false);
            bind(view, getItem(position), true);
            return view;
        }

        private void bind(@NonNull View view, @Nullable WineSpinnerItem item, boolean isDropdown) {
            TextView tvTitle = view.findViewById(R.id.TVTitle);
            ImageButton btDownload = view.findViewById(R.id.BTDownload);
            ProgressBar progress = view.findViewById(R.id.Progress);

            if (item == null) return;

            tvTitle.setText(item.toString());
            int normalTextColor = resolveThemeColor(view.getContext(), android.R.attr.textColorPrimary, Color.BLACK);
            tvTitle.setTextColor(item.isInstalled() ? normalTextColor : Color.GRAY);

            progress.setVisibility(View.GONE);

            if (isDropdown && item.isDownloadable() && item.profile != null) {
                btDownload.setVisibility(View.VISIBLE);
                btDownload.setOnClickListener(v -> {
                    btDownload.setVisibility(View.GONE);
                    progress.setVisibility(View.VISIBLE);
                    downloadHandler.onDownloadRequested(item.profile, () -> {
                        progress.setVisibility(View.GONE);
                        btDownload.setVisibility(View.VISIBLE);
                    });
                });
            } else {
                btDownload.setOnClickListener(null);
                btDownload.setVisibility(View.GONE);
            }
        }
    }

    private void downloadAndInstallContent(@NonNull ContentProfile profile, @NonNull Runnable onStateChanged, @NonNull Runnable onInstalled) {
        downloadAndInstallContent(profile, onStateChanged, onInstalled, null);
    }

    private void downloadAndInstallContent(@NonNull ContentProfile profile, @NonNull Runnable onStateChanged,
                                           @NonNull Runnable onInstalled, Runnable onFailed) {
        if (profile.remoteUrl == null) return;

        PreloaderDialog downloadDialog = new PreloaderDialog(getActivity());
        downloadDialog.showOnUiThread(R.string.downloading_file);

        new Thread(() -> {
            long timestamp = System.currentTimeMillis();
            File output = new File(requireContext().getCacheDir(), "temp_" + timestamp);
            if (!Downloader.downloadFile(profile.remoteUrl, output)) {
                downloadDialog.closeOnUiThread();
                requireActivity().runOnUiThread(() -> {
                    onStateChanged.run();
                    if (onFailed != null) onFailed.run();
                    AppUtils.showToast(getContext(), R.string.unable_to_install_content);
                });
                return;
            }

            downloadDialog.closeOnUiThread();
            Uri uri = Uri.fromFile(output);
            installContentFromUri(uri, profile, () -> {
                if (output.exists()) output.delete();
                onStateChanged.run();
                onInstalled.run();
            }, () -> {
                if (output.exists()) output.delete();
                requireActivity().runOnUiThread(() -> {
                    onStateChanged.run();
                    if (onFailed != null) onFailed.run();
                });
            });
        }).start();
    }

    private void loadRemoteProfiles(@NonNull Runnable onLoaded) {
        final Context context = getContext();
        if (context == null || getActivity() == null) return;

        PreloaderDialog dialog = new PreloaderDialog(getActivity());
        dialog.showOnUiThread(R.string.loading_component_versions);

        new Thread(() -> {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
            String contentsURL = sp.getString("downloadable_contents_url", "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json");
            String json = Downloader.downloadString(contentsURL);
            dialog.closeOnUiThread();

            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (json == null) {
                    AppUtils.showToast(getContext(), R.string.failed_to_load_remote_contents);
                    return;
                }
                contentsManager.setRemoteProfiles(json);
                onLoaded.run();
            });
        }).start();
    }

    private void showVersionDownloadDialog(@NonNull ContentProfile.ContentType type, @NonNull String displayName,
                                           @NonNull Spinner spinner, @NonNull Runnable reloadSpinner,
                                           boolean keepDialogOpenAfterInstall) {
        loadRemoteProfiles(() -> {
            Context context = getContext();
            if (context == null) return;

            List<ContentProfile> downloadableProfiles = new ArrayList<>();
            for (ContentProfile profile : contentsManager.getProfiles(type)) {
                if (profile.remoteUrl == null || profile.remoteUrl.isEmpty()) continue;
                if (ContentsManager.getInstallDir(context, profile).exists()) continue;
                downloadableProfiles.add(profile);
            }

            if (downloadableProfiles.isEmpty()) {
                Toast.makeText(context, getString(R.string.all_component_versions_installed, displayName), Toast.LENGTH_SHORT).show();
                return;
            }

            ArrayList<String> items = new ArrayList<>();
            for (int i = 0; i < downloadableProfiles.size(); i++) {
                items.add(getVersionSpinnerValue(downloadableProfiles.get(i)));
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, items);
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(getString(R.string.download_component_title, displayName, items.size()))
                    .setAdapter(adapter, null)
                    .setNegativeButton(R.string.cancel, null)
                    .create();

            boolean[] isInstalling = {false};
            dialog.setOnShowListener((d) -> dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
                if (isInstalling[0] || position < 0 || position >= downloadableProfiles.size()) return;

                ContentProfile selectedProfile = downloadableProfiles.get(position);
                String selectedValue = items.get(position);

                isInstalling[0] = true;
                dialog.getListView().setEnabled(false);

                downloadAndInstallContent(selectedProfile, () -> {}, () -> {
                    contentsManager.syncContents();
                    reloadSpinner.run();
                    AppUtils.setSpinnerSelectionFromValue(spinner, selectedValue);

                    if (keepDialogOpenAfterInstall && dialog.isShowing()) {
                        downloadableProfiles.remove(position);
                        items.remove(position);
                        adapter.notifyDataSetChanged();
                        isInstalling[0] = false;

                        if (items.isEmpty()) {
                            dialog.dismiss();
                        } else {
                            dialog.setTitle(getString(R.string.download_component_title, displayName, items.size()));
                            dialog.getListView().setEnabled(true);
                        }
                    } else if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }, () -> {
                    isInstalling[0] = false;
                    if (dialog.isShowing()) {
                        dialog.getListView().setEnabled(true);
                    }
                });
            }));
            dialog.show();
        });
    }

    private String getVersionSpinnerValue(@NonNull ContentProfile profile) {
        String entryName = ContentsManager.getEntryName(profile);
        int firstDashIndex = entryName.indexOf('-');
        return firstDashIndex >= 0 ? entryName.substring(firstDashIndex + 1) : entryName;
    }

    private boolean isCurrentWineArm64EC() {
        Context context = getContext();
        if (context == null || wineVersionSpinner == null || wineVersionSpinner.getSelectedItem() == null) return false;
        try {
            return WineInfo.fromIdentifier(context, contentsManager, wineVersionSpinner.getSelectedItem().toString()).isArm64EC();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void refreshBox64VersionSpinner() {
        Context context = getContext();
        if (context == null || box64VersionSpinner == null) return;
        loadBox64VersionSpinner(context, container, contentsManager, box64VersionSpinner, isCurrentWineArm64EC());
    }

    private void refreshFEXCoreVersionSpinner() {
        Context context = getContext();
        if (context == null || fexcoreVersionSpinner == null) return;
        FEXCoreManager.loadFEXCoreVersion(context, contentsManager, fexcoreVersionSpinner, container);
    }

    private void installContentFromUri(@NonNull Uri uri, @NonNull ContentProfile expectedProfile,
                                       @NonNull Runnable onInstalled, @NonNull Runnable onFailed) {
        PreloaderDialog dialog = new PreloaderDialog(getActivity());
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
                requireActivity().runOnUiThread(() -> ContentDialog.alert(getContext(), getString(R.string.install_failed) + ": " + getString(msgId), () -> {
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
                    requireActivity().runOnUiThread(() -> {
                        ContentInfoDialog infoDialog = new ContentInfoDialog(getContext(), installedProfile);
                        ((TextView) infoDialog.findViewById(R.id.BTConfirm)).setText(R.string._continue);
                        infoDialog.setOnConfirmCallback(() -> {
                            isExtracting = false;
                            List<ContentProfile.ContentFile> untrustedFiles = contentsManager.getUnTrustedContentFiles(installedProfile);
                            if (!untrustedFiles.isEmpty()) {
                                ContentUntrustedDialog untrustedDialog = new ContentUntrustedDialog(getContext(), untrustedFiles);
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
                    requireActivity().runOnUiThread(() -> {
                        ContentDialog.alert(getContext(), R.string.content_installed_success, null);
                        contentsManager.syncContents();
                        onInstalled.run();
                    });
                }
            }
        };

        Executors.newSingleThreadExecutor().execute(() -> contentsManager.extraContentFile(uri, callback));
    }

    private void updateEmulatorConfigVisibility(View view) {
        FrameLayout fexcoreFL = view.findViewById(R.id.fexcoreFrame);
        FrameLayout box86box64FL = view.findViewById(R.id.box86box64Frame);
        Spinner sEmulator = view.findViewById(R.id.SEmulator);
        Spinner sEmulator64 = view.findViewById(R.id.SEmulator64);

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

    public String getControllerMapping(View view) {
        //The order has to be the same like Container.XrControllerMapping
        int[] ids = {
                R.id.SButtonA, R.id.SButtonB, R.id.SButtonX, R.id.SButtonY, R.id.SButtonGrip, R.id.SButtonTrigger,
                R.id.SThumbstickUp, R.id.SThumbstickDown, R.id.SThumbstickLeft, R.id.SThumbstickRight
        };
        byte[] controllerMapping = new byte[ids.length];
        for (int i = 0; i < ids.length; i++) {
            int index =  ((Spinner)view.findViewById(ids[i])).getSelectedItemPosition();
            byte value = XKeycode.values()[index].id;
            controllerMapping[i] = value;
        }
        return new String(controllerMapping);
    }

    public void setControllerMapping(Spinner spinner, Container.XrControllerMapping mapping, int defaultValue) {
        XKeycode[] values = XKeycode.values();
        ArrayList<String> array = new ArrayList<>();
        for (XKeycode value : values) {
            array.add(value.name());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(spinner.getContext(), android.R.layout.simple_spinner_dropdown_item, array);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        byte keycode = isEditMode() ? container.getControllerMapping(mapping) : (byte) defaultValue;
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].id == keycode) {
                index = i;
                break;
            }
        }
        spinner.setSelection(isEditMode() && (index != 0) ? index : defaultValue);
    }

    public static void updateGraphicsDriverSpinner(Context context, Spinner spinner) {
        String[] originalItems = context.getResources().getStringArray(R.array.graphics_driver_entries);
        List<String> itemList = new ArrayList<>(Arrays.asList(originalItems));
        
        // Set the adapter with the combined list
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, itemList));
    }

    public static void loadBox64VersionSpinner(Context context, Container container, ContentsManager manager, Spinner spinner, boolean isArm64EC) {
        List<String> itemList;
        if (isArm64EC) {
            String[] originalItems = context.getResources().getStringArray(R.array.wowbox64_version_entries);
            itemList = new ArrayList<>(Arrays.asList(originalItems));
        }
        else {
            String[] originalItems = context.getResources().getStringArray(R.array.box64_version_entries);
            itemList = new ArrayList<>(Arrays.asList(originalItems));
        }
        if (!isArm64EC) {
            for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_BOX64)) {
                if (!(profile.remoteUrl == null || ContentsManager.getInstallDir(context, profile).exists())) continue;
                String entryName = ContentsManager.getEntryName(profile);
                int firstDashIndex = entryName.indexOf('-');
                itemList.add(entryName.substring(firstDashIndex + 1));
            }
        } else {
            for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64)) {
                if (!(profile.remoteUrl == null || ContentsManager.getInstallDir(context, profile).exists())) continue;
                String entryName = ContentsManager.getEntryName(profile);
                int firstDashIndex = entryName.indexOf('-');
                itemList.add(entryName.substring(firstDashIndex + 1));
            }
        }
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, itemList));
        if (container != null)
            AppUtils.setSpinnerSelectionFromValue(spinner, container.getBox64Version());
        else
            AppUtils.setSpinnerSelectionFromValue(spinner, DefaultVersion.BOX64);
    }
    
    /**
     * Очистка временных файлов компонентов после установки
     */
    private void cleanupTemporaryComponentFiles(Container container) {
        try {
            // Удаляем временные ярлыки
            java.io.File desktopDir = container.getDesktopDir();
            if (desktopDir.exists()) {
                java.io.File[] shortcuts = desktopDir.listFiles();
                if (shortcuts != null) {
                    for (java.io.File shortcutFile : shortcuts) {
                        if (shortcutFile.getName().startsWith("_winlator_component_")) {
                            // Удаляем только временный ярлык
                            // Файл компонента оставляем в папке для повторного использования
                            shortcutFile.delete();
                        }
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ComponentCleanup", "Error cleaning up temporary files", e);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Очищаем временные файлы компонентов после возврата из установки
        if (container != null) {
            cleanupTemporaryComponentFiles(container);
        }

        final Context context = getContext();
        if (context == null) return;

        // Подгружаем remote profiles (contents.json) как в ContentsFragment, чтобы в списке Wine
        // отображались версии из ContentsManager (в т.ч. нескачанные).
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        new Thread(() -> {
            String contentsURL = sp.getString("downloadable_contents_url", "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json");
            String json = Downloader.downloadString(contentsURL);
            if (json == null) return;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (getContext() == null || wineVersionSpinner == null || box64VersionSpinner == null || rootView == null) return;
                contentsManager.setRemoteProfiles(json);
                // setRemoteProfiles already calls syncContents(); we just refresh spinner to show new items
                loadWineVersionSpinner(rootView, wineVersionSpinner, box64VersionSpinner);
                refreshBox64VersionSpinner();
                refreshFEXCoreVersionSpinner();
            });
        }).start();
    }

}
