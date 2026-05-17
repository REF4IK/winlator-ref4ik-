package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import com.winlator.cmod.R;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.core.HDRDisplayManager;
import com.winlator.cmod.core.HDRConfiguration;
import com.winlator.cmod.core.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class GraphicsDriverConfigDialog extends ContentDialog {

    private static final String TAG = "GraphicsDriverConfigDialog"; // Tag for logging
    HashMap<String, Boolean> extensionsState = new HashMap<>();
    private Spinner sVersion;
    private Spinner sAvailableExtensions;
    private Spinner sMaxDeviceMemory;
    private Spinner sFrameSynchronization;
    private Spinner sPresentMode;
    private Spinner sResourceType;
    private CheckBox cbAdrenotoolsTurnip;
    private CheckBox cbEnableBlit;
    
    // HDR Controls (hidden)
    // private CheckBox cbEnableHDR;
    // private Spinner sHDRMode;
    // private Spinner sHDRColorSpace;
    // private Spinner sHDRToneMapping;
    // private CheckBox cbEnable10Bit;
    // private CheckBox cbEnableWideColorGamut;
    // private HDRDisplayManager hdrDisplayManager;
    private static String selectedVersion;
    private static String blacklistedExtensions = "";
    private static String selectedDeviceMemory;
    private static String isAdrenotoolsTurnip = "1";
    private static String frameSynchronization;
    private static String selectedPresentMode;
    private static String selectedResourceType;
    private static String enableBlit;
    
    // HDR configuration static variables (hidden)
    // private static boolean enableHDR = false;
    // private static String hdrMode = "0"; // Disabled by default
    // private static String hdrColorSpace = "0"; // sRGB by default
    // private static String hdrToneMapping = "0"; // Disabled by default
    // private static boolean enable10Bit = false;
    // private static boolean enableWideColorGamut = false;

    protected class ExtensionAdapter extends ArrayAdapter<String> {
        ArrayList<String> extensions;

        public ExtensionAdapter(Context context, List<String> list) {
            super(context, 0, list);
            this.extensions = new ArrayList<>(list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return initSpinnerElement(position, convertView, parent);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return initDropDownView(position, convertView, parent);
        }

        private View initSpinnerElement(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = (View)new TextView(getContext());
            }
            ((TextView)convertView).setText(extensions.size() + " System Extensions");
            return convertView;
        }

        private View initDropDownView(int position, View convertView, ViewGroup parent) {
            boolean isDarkMode = ContentDialog.shouldUseDarkDialog(getContext());
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.checkbox_spinner, parent, false);
            }
            CheckBox cb = convertView.findViewById(R.id.checkbox);
            cb.setTextAppearance(isDarkMode ? R.style.CheckBox_Dark : R.style.CheckBox);
            cb.setText(extensions.get(position));
            cb.setOnCheckedChangeListener(null);
            cb.setChecked(extensionsState.getOrDefault(extensions.get(position), true));
            cb.setOnCheckedChangeListener((buttonView, isChecked) ->  {
                extensionsState.put(extensions.get(position), isChecked);
            });
            return convertView;
        }
    }

    public static HashMap<String, String> parseGraphicsDriverConfig(String graphicsDriverConfig) {
        HashMap<String, String> mappedConfig = new HashMap<>();
        String[] configElements = graphicsDriverConfig.split(";");
        for (String element : configElements) {
            String key;
            String value;
            String[] splittedElement = element.split("=");
            key = splittedElement[0];
            if (splittedElement.length > 1)
                value = element.split("=")[1];
            else
                value = "";
            mappedConfig.put(key, value);
        }
        return mappedConfig;
    }

    public static String toGraphicsDriverConfig(HashMap<String, String> config) {
        String graphicsDriverConfig = "";
        for (Map.Entry<String, String> entry : config.entrySet()) {
            graphicsDriverConfig += entry.getKey() + "=" + entry.getValue() + ";";
        }
        return graphicsDriverConfig.substring(0, graphicsDriverConfig.length() - 1);
    }

    public static String getVersion(String graphicsDriverConfig) {
        HashMap<String, String> config = parseGraphicsDriverConfig(graphicsDriverConfig);
        return config.get("version");
    }

    public static String getExtensionsBlacklist(String graphicsDriverConfig) {
        HashMap<String, String> config = parseGraphicsDriverConfig(graphicsDriverConfig);
        return config.get("blacklistedExtensions");
    }

    private static String normalizeFrameSynchronization(String frameSync) {
        if ("Never".equals(frameSync) || "Always".equals(frameSync)) {
            return frameSync;
        }
        return "Normal";
    }

    private static String getRecommendedPresentMode(String frameSync) {
        String normalizedFrameSync = normalizeFrameSynchronization(frameSync);
        if ("Never".equals(normalizedFrameSync)) {
            return "immediate";
        }
        if ("Always".equals(normalizedFrameSync)) {
            return "fifo";
        }
        return "mailbox";
    }

    private static String resolveConfiguredPresentMode(String frameSync, String presentMode) {
        if (presentMode == null || presentMode.trim().isEmpty()) {
            return getRecommendedPresentMode(frameSync);
        }
        String normalizedPresentMode = presentMode.trim().toLowerCase();
        if ("Normal".equals(normalizeFrameSynchronization(frameSync)) && "relaxed".equals(normalizedPresentMode)) {
            return "mailbox";
        }
        return normalizedPresentMode;
    }

    public static String writeGraphicsDriverConfig() {
        String normalizedFrameSync = normalizeFrameSynchronization(frameSynchronization);
        String effectivePresentMode = resolveConfiguredPresentMode(normalizedFrameSync, selectedPresentMode);
        String graphicsDriverConfig = "version=" + selectedVersion + ";" + "blacklistedExtensions=" + blacklistedExtensions + ";" + "maxDeviceMemory=" + StringUtils.parseNumber(selectedDeviceMemory) + ";" + "adrenotoolsTurnip=" + isAdrenotoolsTurnip + ";" + "frameSync=" + normalizedFrameSync + ";" + "presentMode=" + effectivePresentMode + ";" + "resourceType=" + selectedResourceType + ";" + "blit=" + enableBlit + ";" + "enableHDR=0;hdrMode=0;hdrColorSpace=0;hdrToneMapping=0;enable10Bit=0;enableWideColorGamut=0";
        Log.i(TAG, "Written config " + graphicsDriverConfig);
        return graphicsDriverConfig;
    }
  
    public GraphicsDriverConfigDialog(View anchor, String graphicsDriver, TextView graphicsDriverVersionView) {
        super(anchor.getContext(), R.layout.graphics_driver_config_dialog, ContentDialog.shouldUseDarkDialog(anchor.getContext()));
        initializeDialog(anchor, graphicsDriver, graphicsDriverVersionView);
    }

    private void initializeDialog(View anchor, String graphicsDriver, TextView graphicsDriverVersionView) {
        setIcon(R.drawable.icon_settings);
        setTitle(anchor.getContext().getString(R.string.graphics_driver_configuration));

        String graphicsDriverConfig = anchor.getTag().toString();

        sVersion = findViewById(R.id.SGraphicsDriverVersion);
        sAvailableExtensions = findViewById(R.id.SGraphicsDriverAvailableExtensions);
        sFrameSynchronization = findViewById(R.id.SGraphicsDriverFrameSync);
        sMaxDeviceMemory = findViewById(R.id.SGraphicsDriverMaxDeviceMemory);
        sPresentMode = findViewById(R.id.SGraphicsDriverPresentMode);
        sResourceType = findViewById(R.id.SGraphicsDriverResourceType);
        cbAdrenotoolsTurnip = findViewById(R.id.CBAdrenotoolsTurnip);
        cbEnableBlit = findViewById(R.id.CBEnableBlit);
        
        // Initialize HDR controls (hidden)
        // cbEnableHDR = findViewById(R.id.CBEnableHDR);
        // sHDRMode = findViewById(R.id.SHDRMode);
        // sHDRColorSpace = findViewById(R.id.SHDRColorSpace);
        // sHDRToneMapping = findViewById(R.id.SHDRToneMapping);
        // cbEnable10Bit = findViewById(R.id.CBEnable10Bit);
        // cbEnableWideColorGamut = findViewById(R.id.CBEnableWideColorGamut);
        
        // Initialize HDR display manager
        // hdrDisplayManager = new HDRDisplayManager(anchor.getContext());

        HashMap<String, String> config = parseGraphicsDriverConfig(graphicsDriverConfig);

        String initialVersion = config.get("version");
        String blExtensions = config.get("blacklistedExtensions");
        String maxDeviceMemory = config.get("maxDeviceMemory");
        String adrenotoolsTurnip = config.get("adrenotoolsTurnip");
        String frameSync = normalizeFrameSynchronization(config.get("frameSync"));
        String presentMode = resolveConfiguredPresentMode(frameSync, config.get("presentMode"));
        String resourceType = config.get("resourceType");
        String blit = config.get("blit");

        frameSynchronization = frameSync;
        selectedPresentMode = presentMode;
        selectedResourceType = resourceType != null && !resourceType.isEmpty() ? resourceType : "auto";
        selectedDeviceMemory = maxDeviceMemory != null && !maxDeviceMemory.isEmpty() ? maxDeviceMemory : "0";
        isAdrenotoolsTurnip = "0".equals(adrenotoolsTurnip) ? "0" : "1";
        
        // Parse HDR configuration (hidden)
        // String enableHDRStr = config.get("enableHDR");
        // String hdrModeStr = config.get("hdrMode");
        // String hdrColorSpaceStr = config.get("hdrColorSpace");
        // String hdrToneMappingStr = config.get("hdrToneMapping");
        // String enable10BitStr = config.get("enable10Bit");
        // String enableWideColorGamutStr = config.get("enableWideColorGamut");

        // Update the selectedVersion whenever the user selects a different version
        sVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVersion = sVersion.getSelectedItem().toString();
                Log.d(TAG, "User selected version: " + selectedVersion);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedVersion = sVersion.getSelectedItem().toString();
                Log.d(TAG, "User selected version: " + selectedVersion);
            }
        });

        sMaxDeviceMemory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedDeviceMemory = sMaxDeviceMemory.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        sFrameSynchronization.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                frameSynchronization = sFrameSynchronization.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        sPresentMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedPresentMode = sPresentMode.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        sResourceType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selectedResourceType = sResourceType.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        cbAdrenotoolsTurnip.setOnCheckedChangeListener(null);
        cbAdrenotoolsTurnip.setChecked(adrenotoolsTurnip.equals("1") ? true : false);
        cbAdrenotoolsTurnip.setOnCheckedChangeListener((buttonView, isChecked) ->  {
            isAdrenotoolsTurnip = isChecked ? "1" : "0";
        });

        enableBlit = blit != null ? blit : "0";
        cbEnableBlit.setChecked(enableBlit.equals("1") ? true : false);
        cbEnableBlit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            enableBlit = isChecked ? "1" : "0";
        });
        
        // Setup HDR controls (hidden)
        // setupHDRControls(enableHDRStr, hdrModeStr, hdrColorSpaceStr, hdrToneMappingStr, enable10BitStr, enableWideColorGamutStr);

        // Ensure ContentsManager syncContents is called
        ContentsManager contentsManager = new ContentsManager(anchor.getContext());
        contentsManager.syncContents();
        
        // Populate the spinner with available versions from ContentsManager and pre-select the initial version
        populateGraphicsDriverVersions(anchor.getContext(), contentsManager, initialVersion, blExtensions, maxDeviceMemory, frameSync, presentMode, resourceType, graphicsDriver);
        
        // Check HDR support and update UI accordingly (hidden)
        // updateHDRAvailability();

        // Обработчик кнопки скачивания драйверов
        Button btnDownloadGraphicsDriver = findViewById(R.id.BTDownloadGraphicsDriver);
        btnDownloadGraphicsDriver.setOnClickListener(v -> {
            DriverDownloadDialog downloadDialog = new DriverDownloadDialog(
                getContext(),
                installedDriverId -> {
                    // После успешной установки: синхронизируем и автовыбираем установленный драйвер
                    contentsManager.syncContents();
                    selectedVersion = installedDriverId;
                    populateGraphicsDriverVersions(getContext(), contentsManager,
                        installedDriverId, blacklistedExtensions, selectedDeviceMemory,
                        frameSynchronization, selectedPresentMode, selectedResourceType,
                        graphicsDriver);
                }
            );
            downloadDialog.show();
        });

        setOnConfirmCallback(() -> {
            for (HashMap.Entry<String, Boolean> entry : extensionsState.entrySet()) {
                if(!entry.getKey().isEmpty() && !entry.getValue()) {
                    blacklistedExtensions += entry.getKey() + ",";
                }
            }

            if (!blacklistedExtensions.isEmpty())
                blacklistedExtensions = blacklistedExtensions.substring(0, blacklistedExtensions.length() - 1);

            if (graphicsDriverVersionView != null)
                graphicsDriverVersionView.setText(selectedVersion);

            anchor.setTag(writeGraphicsDriverConfig());
        });
    }

    private void populateGraphicsDriverVersions(Context context, ContentsManager contentsManager, @Nullable String initialVersion, @Nullable String blExtensions, String maxDeviceMemory, String frameSync, String presentMode, String resourceType, String graphicsDriver) {
        List<String> wrapperVersions = new ArrayList<>();
        ArrayList<String> availableExtensions;

        String[] wrapperDefaultVersions = context.getResources().getStringArray(R.array.wrapper_graphics_driver_version_entries);

        wrapperVersions.addAll(Arrays.asList(wrapperDefaultVersions));
        
        // Add installed versions from AdrenotoolsManager
        AdrenotoolsManager adrenotoolsManager = new AdrenotoolsManager(context);
        wrapperVersions.addAll(adrenotoolsManager.enumarateInstalledDrivers());


        availableExtensions = new ArrayList<>(Arrays.asList(GPUInformation.enumerateExtensions()));

        // Remove essential and wrapper disabled extensions
        String[] essentialExtensions = {"VK_EXT_hdr_metadata", "VK_GOOGLE_display_timing", "VK_KHR_shader_float_controls", "VK_KHR_shader_presentable_image", "VK_EXT_image_compression_control_swapchain"};
        for (String extension : essentialExtensions) {
            availableExtensions.remove(extension);
        }

        // Set the adapter and select the initial version
        ArrayAdapter<String> wrapperAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wrapperVersions);
        ExtensionAdapter extensionsAdapter = new ExtensionAdapter(context, availableExtensions);

        String[] bl = blExtensions.split("\\,");

        for (String extension : bl) {
            if (!extension.isEmpty()) {
                Log.d("GraphicsDriverConfigDialog", "Getting initial blacklisted extension: " + extension);
                extensionsState.put(extension, false);
            }
        }
        
        sVersion.setAdapter(wrapperAdapter);
        sAvailableExtensions.setAdapter(extensionsAdapter);
        
        // We can start logging selected graphics driver and initial version
        Log.d(TAG, "Graphics driver: " + graphicsDriver);
        Log.d(TAG, "Initial version: " + initialVersion);

        // Use the custom selection logic
        setSpinnerSelectionWithFallback(sVersion, initialVersion, graphicsDriver);
        AppUtils.setSpinnerSelectionFromNumber(sMaxDeviceMemory, maxDeviceMemory);
        AppUtils.setSpinnerSelectionFromValue(sFrameSynchronization, normalizeFrameSynchronization(frameSync));
        AppUtils.setSpinnerSelectionFromValue(sPresentMode, resolveConfiguredPresentMode(frameSync, presentMode));
        AppUtils.setSpinnerSelectionFromValue(sResourceType, resourceType != null ? resourceType : "auto");

        // We can log the spinner values now
        Log.d(TAG, "Spinner selected position: " + sVersion.getSelectedItemPosition());
        Log.d(TAG, "Spinner selected value: " + sVersion.getSelectedItem());
    }

    private void setSpinnerSelectionWithFallback(Spinner spinner, String version, String graphicsDriver) {
        // First, attempt to find an exact match (case-insensitive)
        for (int i = 0; i < spinner.getCount(); i++) {
            String item = spinner.getItemAtPosition(i).toString();
            if (item.equalsIgnoreCase(version)) {
                spinner.setSelection(i);
                return;
            }
        }

        AppUtils.setSpinnerSelectionFromValue(spinner, DefaultVersion.WRAPPER);
    }
    
    /*
     * Setup HDR controls with initial values and listeners (HIDDEN)
     */
    /*
    private void setupHDRControls(String enableHDRStr, String hdrModeStr, String hdrColorSpaceStr, 
                                 String hdrToneMappingStr, String enable10BitStr, String enableWideColorGamutStr) {
        // Parse initial values with defaults
        enableHDR = "1".equals(enableHDRStr);
        hdrMode = hdrModeStr != null ? hdrModeStr : "0";
        hdrColorSpace = hdrColorSpaceStr != null ? hdrColorSpaceStr : "0";
        hdrToneMapping = hdrToneMappingStr != null ? hdrToneMappingStr : "0";
        enable10Bit = "1".equals(enable10BitStr);
        enableWideColorGamut = "1".equals(enableWideColorGamutStr);
        
        // Set initial checkbox states
        cbEnableHDR.setChecked(enableHDR);
        cbEnable10Bit.setChecked(enable10Bit);
        cbEnableWideColorGamut.setChecked(enableWideColorGamut);
        
        // Set initial spinner selections
        AppUtils.setSpinnerSelectionFromNumber(sHDRMode, hdrMode);
        AppUtils.setSpinnerSelectionFromNumber(sHDRColorSpace, hdrColorSpace);
        AppUtils.setSpinnerSelectionFromNumber(sHDRToneMapping, hdrToneMapping);
        
        // Setup listeners
        cbEnableHDR.setOnCheckedChangeListener((buttonView, isChecked) -> {
            enableHDR = isChecked;
            updateHDRControlsState();
        });
        
        sHDRMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                hdrMode = String.valueOf(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        sHDRColorSpace.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                hdrColorSpace = String.valueOf(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        sHDRToneMapping.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                hdrToneMapping = String.valueOf(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        cbEnable10Bit.setOnCheckedChangeListener((buttonView, isChecked) -> {
            enable10Bit = isChecked;
        });
        
        cbEnableWideColorGamut.setOnCheckedChangeListener((buttonView, isChecked) -> {
            enableWideColorGamut = isChecked;
        });
        
        // Initial state update
        updateHDRControlsState();
    }
    */
    
    /*
     * Update HDR availability based on display capabilities (HIDDEN)
     */
    /*
    private void updateHDRAvailability() {
        if (hdrDisplayManager != null) {
            boolean hdrSupported = hdrDisplayManager.isHDR10Supported();
            boolean wideColorSupported = hdrDisplayManager.isWideColorGamutSupported();
            
            // Log HDR support status
            Log.d(TAG, "HDR Support: " + hdrSupported);
            Log.d(TAG, "Wide Color Gamut Support: " + wideColorSupported);
            Log.d(TAG, hdrDisplayManager.getHDRSupportSummary());
            
            if (!hdrSupported) {
                // Disable HDR controls if not supported
                cbEnableHDR.setEnabled(false);
                cbEnableHDR.setText(getContext().getString(R.string.hdr_not_supported));
            } else {
                cbEnableHDR.setEnabled(true);
                cbEnableHDR.setText(getContext().getString(R.string.enable_hdr10));
            }
            
            if (!wideColorSupported) {
                cbEnableWideColorGamut.setEnabled(false);
            }
        }
    }
    */
    
    /*
     * Update HDR controls enabled state based on main HDR checkbox (HIDDEN)
     */
    /*
    private void updateHDRControlsState() {
        boolean enabled = enableHDR && cbEnableHDR.isEnabled();
        
        sHDRMode.setEnabled(enabled);
        sHDRColorSpace.setEnabled(enabled);
        sHDRToneMapping.setEnabled(enabled);
        cbEnable10Bit.setEnabled(enabled);
        
        // Wide color gamut depends on both HDR enabled and display support
        boolean wideColorEnabled = enabled && hdrDisplayManager != null && 
                                  hdrDisplayManager.isWideColorGamutSupported();
        cbEnableWideColorGamut.setEnabled(wideColorEnabled);
    }
    */

}
