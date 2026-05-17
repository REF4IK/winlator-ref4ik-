package com.winlator.cmod.contentdialog;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.KeyValueSet;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DXVKConfigDialog extends ContentDialog {
    private static final int HELP_POPUP_WIDTH_DP = 340;
    public static final String DEFAULT_CONFIG = "version=" + DefaultVersion.DXVK +
            ",framerate=0,maxDeviceMemory=0,async=0,asyncCache=0" +
            ",tearFree=Auto,maxFrameLatency=0,tilerMode=Auto,deferSurfaceCreation=0,disableMsaa=0,maxTessFactor=0" +
            ",syncInterval=-1,maxFeatureLevel=12_1,forceRefreshRate=0,shaderModel=3,floatEmulation=Auto,samplerAnisotropy=-1" +
            ",enableMemoryDefrag=Auto,lowerSinCos=Auto,clampNegativeLodBias=Auto,samplerLodBias=0.0" +
            ",numCompilerThreads=0,enableGraphicsPipelineLibrary=Auto,relaxedBarriers=Auto";
    public static final int DXVK_TYPE_NONE = 0;
    public static final int DXVK_TYPE_ASYNC = 1;
    public static final int DXVK_TYPE_GPLASYNC = 2;
    private final ToggleButton swAsync;
    private final ToggleButton swAsyncCache;
    private final View llAsync;
    private final View llAsyncCache;
    private final Context context;
    private List<String> dxvkVersions;

    public DXVKConfigDialog(View anchor) {
        super(anchor.getContext(), R.layout.dxvk_config_dialog, ContentDialog.shouldUseDarkDialog(anchor.getContext()));
        context = anchor.getContext();
        setIcon(R.drawable.icon_settings);
        setTitle("DXVK "+context.getString(R.string.configuration));

        final Spinner sVersion = findViewById(R.id.SVersion);
        final Spinner sFramerate = findViewById(R.id.SFramerate);
        final Spinner sMaxDeviceMemory = findViewById(R.id.SMaxDeviceMemory);
        final Spinner sTearFree = findViewById(R.id.STearFree);
        final Spinner sMaxFrameLatency = findViewById(R.id.SMaxFrameLatency);
        final Spinner sTilerMode = findViewById(R.id.STilerMode);
        final Spinner sMaxTessFactor = findViewById(R.id.SMaxTessFactor);
        final Spinner sSyncInterval = findViewById(R.id.SSyncInterval);
        final Spinner sMaxFeatureLevel = findViewById(R.id.SMaxFeatureLevel);
        final Spinner sForceRefreshRate = findViewById(R.id.SForceRefreshRate);
        final Spinner sShaderModel = findViewById(R.id.SShaderModel);
        final Spinner sFloatEmulation = findViewById(R.id.SFloatEmulation);
        final Spinner sSamplerAnisotropy = findViewById(R.id.SSamplerAnisotropy);
        final Spinner sEnableMemoryDefrag = findViewById(R.id.SEnableMemoryDefrag);
        final Spinner sLowerSinCos = findViewById(R.id.SLowerSinCos);
        final Spinner sClampNegativeLodBias = findViewById(R.id.SClampNegativeLodBias);
        final Spinner sSamplerLodBias = findViewById(R.id.SSamplerLodBias);
        final Spinner sNumCompilerThreads = findViewById(R.id.SNumCompilerThreads);
        final Spinner sEnableGraphicsPipelineLibrary = findViewById(R.id.SEnableGraphicsPipelineLibrary);
        final Spinner sRelaxedBarriers = findViewById(R.id.SRelaxedBarriers);
        swAsync = findViewById(R.id.SWAsync);
        swAsyncCache = findViewById(R.id.SWAsyncCache);
        final ToggleButton swDeferSurfaceCreation = findViewById(R.id.SWDeferSurfaceCreation);
        final ToggleButton swDisableMsaa = findViewById(R.id.SWDisableMsaa);
        llAsync = findViewById(R.id.LLAsync);
        llAsyncCache = findViewById(R.id.LLAsyncCache);

        ContentsManager contentsManager = new ContentsManager(context);
        contentsManager.syncContents();
        loadDxvkVersionSpinner(contentsManager,sVersion);

        KeyValueSet config = parseConfig(anchor.getTag());
        AppUtils.setSpinnerSelectionFromIdentifier(sVersion, config.get("version"));
        AppUtils.setSpinnerSelectionFromIdentifier(sFramerate, config.get("framerate"));
        AppUtils.setSpinnerSelectionFromNumber(sMaxDeviceMemory, config.get("maxDeviceMemory"));
        AppUtils.setSpinnerSelectionFromValue(sTearFree, config.get("tearFree", "Auto"));
        AppUtils.setSpinnerSelectionFromNumber(sMaxFrameLatency, config.get("maxFrameLatency", "0"));
        AppUtils.setSpinnerSelectionFromValue(sTilerMode, config.get("tilerMode", "Auto"));
        AppUtils.setSpinnerSelectionFromNumber(sMaxTessFactor, config.get("maxTessFactor", "0"));
        AppUtils.setSpinnerSelectionFromIdentifier(sSyncInterval, config.get("syncInterval", "-1"));
        AppUtils.setSpinnerSelectionFromValue(sMaxFeatureLevel, config.get("maxFeatureLevel", "12_1"));
        AppUtils.setSpinnerSelectionFromNumber(sForceRefreshRate, config.get("forceRefreshRate", "0"));
        AppUtils.setSpinnerSelectionFromNumber(sShaderModel, config.get("shaderModel", "3"));
        AppUtils.setSpinnerSelectionFromValue(sFloatEmulation, config.get("floatEmulation", "Auto"));
        AppUtils.setSpinnerSelectionFromIdentifier(sSamplerAnisotropy, config.get("samplerAnisotropy", "-1"));
        AppUtils.setSpinnerSelectionFromValue(sEnableMemoryDefrag, config.get("enableMemoryDefrag", "Auto"));
        AppUtils.setSpinnerSelectionFromValue(sLowerSinCos, config.get("lowerSinCos", "Auto"));
        AppUtils.setSpinnerSelectionFromValue(sClampNegativeLodBias, config.get("clampNegativeLodBias", "Auto"));
        AppUtils.setSpinnerSelectionFromValue(sSamplerLodBias, config.get("samplerLodBias", "0.0"));
        AppUtils.setSpinnerSelectionFromNumber(sNumCompilerThreads, config.get("numCompilerThreads", "0"));
        AppUtils.setSpinnerSelectionFromValue(sEnableGraphicsPipelineLibrary, config.get("enableGraphicsPipelineLibrary", "Auto"));
        AppUtils.setSpinnerSelectionFromValue(sRelaxedBarriers, config.get("relaxedBarriers", "Auto"));
        swAsync.setChecked(config.get("async").equals("1"));
        swAsyncCache.setChecked(config.get("asyncCache").equals("1"));
        swDeferSurfaceCreation.setChecked(config.getBoolean("deferSurfaceCreation", false));
        swDisableMsaa.setChecked(config.getBoolean("disableMsaa", false));

        updateConfigVisibility(getDXVKType(sVersion.getSelectedItemPosition()));

        setupHelpButton(R.id.BTHelpTearFree, R.string.dxvk_tear_free_help);
        setupHelpButton(R.id.BTHelpMaxFrameLatency, R.string.dxvk_max_frame_latency_help);
        setupHelpButton(R.id.BTHelpTilerMode, R.string.dxvk_tiler_mode_help);
        setupHelpButton(R.id.BTHelpDeferSurfaceCreation, R.string.dxvk_defer_surface_creation_help);
        setupHelpButton(R.id.BTHelpDisableMsaa, R.string.dxvk_disable_msaa_help);
        setupHelpButton(R.id.BTHelpMaxTessFactor, R.string.dxvk_max_tess_factor_help);
        setupHelpButton(R.id.BTHelpSyncInterval, R.string.dxvk_sync_interval_help);
        setupHelpButton(R.id.BTHelpMaxFeatureLevel, R.string.dxvk_max_feature_level_help);
        setupHelpButton(R.id.BTHelpForceRefreshRate, R.string.dxvk_force_refresh_rate_help);
        setupHelpButton(R.id.BTHelpShaderModel, R.string.dxvk_shader_model_help);
        setupHelpButton(R.id.BTHelpFloatEmulation, R.string.dxvk_float_emulation_help);
        setupHelpButton(R.id.BTHelpSamplerAnisotropy, R.string.dxvk_sampler_anisotropy_help);
        setupHelpButton(R.id.BTHelpEnableMemoryDefrag, R.string.dxvk_enable_memory_defrag_help);
        setupHelpButton(R.id.BTHelpLowerSinCos, R.string.dxvk_lower_sin_cos_help);
        setupHelpButton(R.id.BTHelpClampNegativeLodBias, R.string.dxvk_clamp_negative_lod_bias_help);
        setupHelpButton(R.id.BTHelpSamplerLodBias, R.string.dxvk_sampler_lod_bias_help);
        setupHelpButton(R.id.BTHelpNumCompilerThreads, R.string.dxvk_num_compiler_threads_help);
        setupHelpButton(R.id.BTHelpEnableGraphicsPipelineLibrary, R.string.dxvk_enable_graphics_pipeline_library_help);
        setupHelpButton(R.id.BTHelpRelaxedBarriers, R.string.dxvk_relaxed_barriers_help);

        sVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateConfigVisibility(getDXVKType(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        Button btnDownloadDXVK = findViewById(R.id.BTDownloadDXVK);
        btnDownloadDXVK.setOnClickListener(v -> showDownloadDialog(contentsManager, sVersion));

        setOnConfirmCallback(() -> {
            config.put("version", sVersion.getSelectedItem().toString());
            config.put("framerate", StringUtils.parseNumber(sFramerate.getSelectedItem()));
            config.put("maxDeviceMemory", StringUtils.parseNumber(sMaxDeviceMemory.getSelectedItem()));
            config.put("async", ((swAsync.isChecked())&&(llAsync.getVisibility()==View.VISIBLE))?"1":"0");
            config.put("asyncCache", ((swAsyncCache.isChecked())&&(llAsyncCache.getVisibility()==View.VISIBLE))?"1":"0");
            config.put("tearFree", sTearFree.getSelectedItem().toString());
            config.put("maxFrameLatency", StringUtils.parseNumber(sMaxFrameLatency.getSelectedItem()));
            config.put("tilerMode", sTilerMode.getSelectedItem().toString());
            config.put("deferSurfaceCreation", swDeferSurfaceCreation.isChecked() ? "1" : "0");
            config.put("disableMsaa", swDisableMsaa.isChecked() ? "1" : "0");
            config.put("maxTessFactor", StringUtils.parseNumber(sMaxTessFactor.getSelectedItem()));
            config.put("syncInterval", StringUtils.parseIdentifier(sSyncInterval.getSelectedItem()));
            config.put("maxFeatureLevel", sMaxFeatureLevel.getSelectedItem().toString());
            config.put("forceRefreshRate", StringUtils.parseNumber(sForceRefreshRate.getSelectedItem()));
            config.put("shaderModel", StringUtils.parseNumber(sShaderModel.getSelectedItem()));
            config.put("floatEmulation", sFloatEmulation.getSelectedItem().toString());
            config.put("samplerAnisotropy", StringUtils.parseIdentifier(sSamplerAnisotropy.getSelectedItem()));
            config.put("enableMemoryDefrag", sEnableMemoryDefrag.getSelectedItem().toString());
            config.put("lowerSinCos", sLowerSinCos.getSelectedItem().toString());
            config.put("clampNegativeLodBias", sClampNegativeLodBias.getSelectedItem().toString());
            config.put("samplerLodBias", sSamplerLodBias.getSelectedItem().toString());
            config.put("numCompilerThreads", StringUtils.parseNumber(sNumCompilerThreads.getSelectedItem()));
            config.put("enableGraphicsPipelineLibrary", sEnableGraphicsPipelineLibrary.getSelectedItem().toString());
            config.put("relaxedBarriers", sRelaxedBarriers.getSelectedItem().toString());
            anchor.setTag(config.toString());
        });
    }

    private void setupHelpButton(int buttonId, int textResId) {
        findViewById(buttonId).setOnClickListener(v -> AppUtils.showHelpBox(context, v, textResId, HELP_POPUP_WIDTH_DP));
    }

    private void updateConfigVisibility(int dxvkType) {
        if (dxvkType == DXVK_TYPE_ASYNC) {
            llAsync.setVisibility(View.VISIBLE);
            llAsyncCache.setVisibility(View.GONE);
        } else if (dxvkType == DXVK_TYPE_GPLASYNC) {
            llAsync.setVisibility(View.VISIBLE);
            llAsyncCache.setVisibility(View.VISIBLE);
        } else {
            llAsync.setVisibility(View.GONE);
            llAsyncCache.setVisibility(View.GONE);
        }
    }

    private int getDXVKType(int pos) {
        final String v = dxvkVersions.get(pos);
        int dxvkType = DXVK_TYPE_NONE;
        if (v.contains("gplasync"))
            dxvkType = DXVK_TYPE_GPLASYNC;
        else if (v.contains("async"))
            dxvkType = DXVK_TYPE_ASYNC;
        return dxvkType;
    }

    public static KeyValueSet parseConfig(Object config) {
        String data = config != null && !config.toString().isEmpty() ? config.toString() : DEFAULT_CONFIG;
        return new KeyValueSet(data);
    }

    public static void setEnvVars(Context context, KeyValueSet config, EnvVars envVars) {
        envVars.put("DXVK_STATE_CACHE_PATH", context.getFilesDir() + "/imagefs/" + ImageFs.CACHE_PATH);
        envVars.put("DXVK_LOG_LEVEL", "none");

        File rootDir = ImageFs.find(context).getRootDir();
        File dxvkConfigFile = new File(rootDir, ImageFs.CONFIG_PATH+"/dxvk.conf");

        StringBuilder content = new StringBuilder("\"");
        String maxDeviceMemory = config.get("maxDeviceMemory");
        if (!maxDeviceMemory.isEmpty() && !maxDeviceMemory.equals("0")) {
            appendConfigEntry(content, "dxgi.maxDeviceMemory", maxDeviceMemory);
            appendConfigEntry(content, "dxgi.maxSharedMemory", maxDeviceMemory);
        }

        String framerate = config.get("framerate");
        if (!framerate.isEmpty() && !framerate.equals("0")) {
//            content += "dxgi.maxFrameRate = "+framerate+';';
//            content += "d3d9.maxFrameRate = "+framerate+';';
            envVars.put("DXVK_FRAME_RATE", framerate);
        }

        String async = config.get("async");
        if (!async.isEmpty() && !async.equals("0"))
//            content += "dxvk.enableAsync = True;";
            envVars.put("DXVK_ASYNC", "1");

        String asyncCache = config.get("asyncCache");
        if (!asyncCache.isEmpty() && !asyncCache.equals("0"))
//            content += "dxvk.gplAsyncCache = True;";
            envVars.put("DXVK_GPLASYNCCACHE", "1");

        String tearFree = config.get("tearFree", "Auto");
        if (!tearFree.isEmpty() && !tearFree.equals("Auto")) {
            appendConfigEntry(content, "dxvk.tearFree", tearFree);
        }

        String maxFrameLatency = config.get("maxFrameLatency", "0");
        if (!maxFrameLatency.isEmpty() && !maxFrameLatency.equals("0")) {
            appendConfigEntry(content, "dxgi.maxFrameLatency", maxFrameLatency);
            appendConfigEntry(content, "d3d9.maxFrameLatency", maxFrameLatency);
        }

        String tilerMode = config.get("tilerMode", "Auto");
        if (!tilerMode.isEmpty() && !tilerMode.equals("Auto")) {
            appendConfigEntry(content, "dxvk.tilerMode", tilerMode);
        }

        if (config.getBoolean("deferSurfaceCreation", false)) {
            appendConfigEntry(content, "dxgi.deferSurfaceCreation", "True");
            appendConfigEntry(content, "d3d9.deferSurfaceCreation", "True");
        }

        if (config.getBoolean("disableMsaa", false)) {
            appendConfigEntry(content, "d3d11.disableMsaa", "True");
        }

        String maxTessFactor = config.get("maxTessFactor", "0");
        if (!maxTessFactor.isEmpty() && !maxTessFactor.equals("0")) {
            appendConfigEntry(content, "d3d11.maxTessFactor", maxTessFactor);
        }

        String syncInterval = config.get("syncInterval", "-1");
        if (!syncInterval.isEmpty() && !syncInterval.equals("-1")) {
            appendConfigEntry(content, "dxgi.syncInterval", syncInterval);
            appendConfigEntry(content, "d3d9.presentInterval", syncInterval);
        }

        String maxFeatureLevel = config.get("maxFeatureLevel", "12_1");
        if (!maxFeatureLevel.isEmpty() && !maxFeatureLevel.equals("12_1")) {
            appendConfigEntry(content, "d3d11.maxFeatureLevel", maxFeatureLevel);
        }

        String forceRefreshRate = config.get("forceRefreshRate", "0");
        if (!forceRefreshRate.isEmpty() && !forceRefreshRate.equals("0")) {
            appendConfigEntry(content, "dxgi.forceRefreshRate", forceRefreshRate);
            appendConfigEntry(content, "d3d9.forceRefreshRate", forceRefreshRate);
        }

        String shaderModel = config.get("shaderModel", "3");
        if (!shaderModel.isEmpty() && !shaderModel.equals("3")) {
            appendConfigEntry(content, "d3d9.shaderModel", shaderModel);
        }

        String floatEmulation = config.get("floatEmulation", "Auto");
        if (!floatEmulation.isEmpty() && !floatEmulation.equals("Auto")) {
            appendConfigEntry(content, "d3d9.floatEmulation", floatEmulation);
        }

        String samplerAnisotropy = config.get("samplerAnisotropy", "-1");
        if (!samplerAnisotropy.isEmpty() && !samplerAnisotropy.equals("-1")) {
            appendConfigEntry(content, "d3d11.samplerAnisotropy", samplerAnisotropy);
            appendConfigEntry(content, "d3d9.samplerAnisotropy", samplerAnisotropy);
        }

        String enableMemoryDefrag = config.get("enableMemoryDefrag", "Auto");
        if (!enableMemoryDefrag.isEmpty() && !enableMemoryDefrag.equals("Auto")) {
            appendConfigEntry(content, "dxvk.enableMemoryDefrag", enableMemoryDefrag);
        }

        String lowerSinCos = config.get("lowerSinCos", "Auto");
        if (!lowerSinCos.isEmpty() && !lowerSinCos.equals("Auto")) {
            appendConfigEntry(content, "dxvk.lowerSinCos", lowerSinCos);
        }

        String clampNegativeLodBias = config.get("clampNegativeLodBias", "Auto");
        if (!clampNegativeLodBias.isEmpty() && !clampNegativeLodBias.equals("Auto")) {
            appendConfigEntry(content, "d3d11.clampNegativeLodBias", clampNegativeLodBias);
            appendConfigEntry(content, "d3d9.clampNegativeLodBias", clampNegativeLodBias);
        }

        String samplerLodBias = config.get("samplerLodBias", "0.0");
        if (!samplerLodBias.isEmpty() && !samplerLodBias.equals("0.0")) {
            appendConfigEntry(content, "d3d11.samplerLodBias", samplerLodBias);
            appendConfigEntry(content, "d3d9.samplerLodBias", samplerLodBias);
        }

        String numCompilerThreads = config.get("numCompilerThreads", "0");
        if (!numCompilerThreads.isEmpty() && !numCompilerThreads.equals("0")) {
            appendConfigEntry(content, "dxvk.numCompilerThreads", numCompilerThreads);
        }

        String enableGraphicsPipelineLibrary = config.get("enableGraphicsPipelineLibrary", "Auto");
        if (!enableGraphicsPipelineLibrary.isEmpty() && !enableGraphicsPipelineLibrary.equals("Auto")) {
            appendConfigEntry(content, "dxvk.enableGraphicsPipelineLibrary", enableGraphicsPipelineLibrary);
        }

        String relaxedBarriers = config.get("relaxedBarriers", "Auto");
        if (!relaxedBarriers.isEmpty() && !relaxedBarriers.equals("Auto")) {
            appendConfigEntry(content, "d3d11.relaxedBarriers", relaxedBarriers);
        }

        content.append('\"');

//        FileUtils.delete(dxvkConfigFile);
//        if (!content.isEmpty() && FileUtils.writeString(dxvkConfigFile, content)) {
//            envVars.put("DXVK_CONFIG_FILE", rootDir + ImageFs.CONFIG_PATH+"/dxvk.conf");
//        }
        envVars.put("DXVK_CONFIG_FILE", rootDir + ImageFs.CONFIG_PATH+"/dxvk.conf");
        envVars.put("DXVK_CONFIG", content.toString());
    }

    private static void appendConfigEntry(StringBuilder content, String key, String value) {
        content.append(key).append(" = ").append(value).append(';');
    }

    private void loadDxvkVersionSpinner(ContentsManager manager, Spinner spinner) {
        String[] originalItems = context.getResources().getStringArray(R.array.dxvk_version_entries);
        List<String> itemList = new ArrayList<>(Arrays.asList(originalItems));

        // Добавляем только УСТАНОВЛЕННЫЕ версии (у которых remoteUrl == null)
        for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK)) {
            // Фильтруем: если есть remoteUrl, значит версия еще не установлена
            if (profile.remoteUrl == null || profile.remoteUrl.isEmpty()) {
                String entryName = ContentsManager.getEntryName(profile);
                int firstDashIndex = entryName.indexOf('-');
                itemList.add(entryName.substring(firstDashIndex + 1));
            }
        }

        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, itemList));
        dxvkVersions = itemList;
    }
    
    private void showDownloadDialog(ContentsManager manager, Spinner sVersion) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(context.getString(R.string.loading_dxvk_versions));
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        Handler mainHandler = new Handler(Looper.getMainLooper());
        
        new Thread(() -> {
            try {
                // Загружаем contents.json
                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                String contentsURL = sp.getString("downloadable_contents_url", 
                    "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json");
                String json = Downloader.downloadString(contentsURL);
                
                if (json == null) {
                    mainHandler.post(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(context, R.string.failed_to_load_remote_contents, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                
                // Обновляем remote profiles
                manager.setRemoteProfiles(json);
                
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    showDXVKListDialog(manager, sVersion);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(context, context.getString(R.string.install_failed) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
                Log.e("DXVKConfigDialog", "Error loading contents", e);
            }
        }).start();
    }
    
    private void showDXVKListDialog(ContentsManager manager, Spinner sVersion) {
        List<ContentProfile> allProfiles = manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_DXVK);
        if (allProfiles == null || allProfiles.isEmpty()) {
            Toast.makeText(context, R.string.no_dxvk_versions_available, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Создаем список установленных версий для проверки
        List<String> installedVersions = new ArrayList<>();
        for (ContentProfile profile : allProfiles) {
            if (profile.remoteUrl == null || profile.remoteUrl.isEmpty()) {
                installedVersions.add(profile.verName + "_v" + profile.verCode);
            }
        }
        
        // Показываем ВСЕ remote профили (отмечая установленные)
        List<ContentProfile> downloadableProfiles = new ArrayList<>();
        
        for (ContentProfile profile : allProfiles) {
            if (profile.remoteUrl != null && !profile.remoteUrl.isEmpty()) {
                downloadableProfiles.add(profile);
            }
        }
        
        if (downloadableProfiles.isEmpty()) {
            Toast.makeText(context, R.string.all_dxvk_installed, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Создаем кастомный адаптер для красивого отображения
        DXVKDownloadAdapter adapter = new DXVKDownloadAdapter(context, downloadableProfiles, installedVersions);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.download_dxvk_title, downloadableProfiles.size()));
        builder.setIcon(android.R.drawable.stat_sys_download);
        builder.setAdapter(adapter, (dialog, which) -> {
            ContentProfile selectedProfile = downloadableProfiles.get(which);
            
            // Проверяем, не установлена ли уже эта версия
            String versionKey = selectedProfile.verName + "_v" + selectedProfile.verCode;
            if (installedVersions.contains(versionKey)) {
                Toast.makeText(context, R.string.content_already_exist, Toast.LENGTH_SHORT).show();
                return;
            }
            
            downloadAndInstallDXVK(selectedProfile, manager, sVersion);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    
    // Кастомный адаптер для красивого отображения списка DXVK версий
    private static class DXVKDownloadAdapter extends ArrayAdapter<ContentProfile> {
        private final List<ContentProfile> profiles;
        private final List<String> installedVersions;
        private final LayoutInflater inflater;
        private final Context context;
        
        public DXVKDownloadAdapter(Context context, List<ContentProfile> profiles, List<String> installedVersions) {
            super(context, R.layout.dxvk_download_item, profiles);
            this.context = context;
            this.profiles = profiles;
            this.installedVersions = installedVersions;
            this.inflater = LayoutInflater.from(context);
        }
        
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.dxvk_download_item, parent, false);
                holder = new ViewHolder();
                holder.tvVersionName = convertView.findViewById(R.id.TVVersionName);
                holder.tvVersionCode = convertView.findViewById(R.id.TVVersionCode);
                holder.tvDescription = convertView.findViewById(R.id.TVDescription);
                holder.ivIcon = convertView.findViewById(R.id.IVIcon);
                holder.ivStatus = convertView.findViewById(R.id.IVStatus);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            ContentProfile profile = profiles.get(position);
            String versionKey = profile.verName + "_v" + profile.verCode;
            boolean isInstalled = installedVersions.contains(versionKey);

            holder.ivIcon.setImageResource(R.drawable.ic_dxvkk);
            holder.ivIcon.clearColorFilter();
            
            holder.tvVersionName.setText(profile.verName);
            holder.tvVersionCode.setText(context.getString(R.string.version_code) + ": " + profile.verCode);
            
            if (profile.desc != null && !profile.desc.isEmpty()) {
                holder.tvDescription.setText(profile.desc);
                holder.tvDescription.setVisibility(View.VISIBLE);
            } else {
                holder.tvDescription.setVisibility(View.GONE);
            }
            
            // Красивое отображение для установленных версий
            if (isInstalled) {
                // Зелёная галочка для установленных
                holder.ivStatus.setImageResource(android.R.drawable.checkbox_on_background);
                holder.ivStatus.setColorFilter(0xFF4CAF50); // Material Green
                
                // Добавляем текстовый индикатор
                holder.tvVersionName.setText(profile.verName + " (" + context.getString(R.string.installed) + ")");
                holder.tvVersionName.setTextColor(0xFF4CAF50);
            } else {
                // Обычная иконка загрузки для доступных версий
                holder.ivStatus.setImageResource(android.R.drawable.stat_sys_download);
                holder.ivStatus.clearColorFilter();
                
                holder.tvVersionName.setTextColor(context.getResources().getColor(android.R.color.primary_text_light));
            }
            
            return convertView;
        }
        
        private static class ViewHolder {
            TextView tvVersionName;
            TextView tvVersionCode;
            TextView tvDescription;
            ImageView ivIcon;
            ImageView ivStatus;
        }
    }
    
    private void downloadAndInstallDXVK(ContentProfile profile, ContentsManager manager, Spinner sVersion) {
        ProgressDialog downloadDialog = new ProgressDialog(context);
        downloadDialog.setMessage(context.getString(R.string.downloading, profile.verName));
        downloadDialog.setCancelable(false);
        downloadDialog.show();
        
        Handler mainHandler = new Handler(Looper.getMainLooper());
        
        new Thread(() -> {
            try {
                // Скачиваем файл во временную папку
                long timestamp = System.currentTimeMillis();
                File tempFile = new File(context.getCacheDir(), "dxvk_temp_" + timestamp + ".tar.xz");
                
                boolean downloaded = Downloader.downloadFile(profile.remoteUrl, tempFile);
                
                if (!downloaded || !tempFile.exists()) {
                    mainHandler.post(() -> {
                        downloadDialog.dismiss();
                        Toast.makeText(context, context.getString(R.string.failed_to_download, profile.verName), Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                
                // Устанавливаем скачанный файл
                mainHandler.post(() -> {
                    downloadDialog.setMessage(context.getString(R.string.installing, profile.verName));
                });
                
                installDownloadedDXVK(tempFile, manager, sVersion, downloadDialog, mainHandler);
                
            } catch (Exception e) {
                mainHandler.post(() -> {
                    downloadDialog.dismiss();
                    Toast.makeText(context, context.getString(R.string.install_failed) + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                Log.e("DXVKConfigDialog", "Error downloading DXVK", e);
            }
        }).start();
    }
    
    private void installDownloadedDXVK(File tempFile, ContentsManager manager, Spinner sVersion, 
                                      ProgressDialog downloadDialog, Handler mainHandler) {
        ContentsManager.OnInstallFinishedCallback callback = new ContentsManager.OnInstallFinishedCallback() {
            private boolean isExtracting = true;
            
            @Override
            public void onFailed(ContentsManager.InstallFailedReason reason, Exception e) {
                mainHandler.post(() -> {
                    downloadDialog.dismiss();
                    int errorMsgResId = switch (reason) {
                        case ERROR_BADTAR -> R.string.file_cannot_be_recognized;
                        case ERROR_NOPROFILE -> R.string.profile_not_found_in_content;
                        case ERROR_BADPROFILE -> R.string.profile_cannot_be_recognized;
                        case ERROR_EXIST -> R.string.content_already_exist;
                        case ERROR_MISSINGFILES -> R.string.content_is_incomplete;
                        case ERROR_UNTRUSTPROFILE -> R.string.content_cannot_be_trusted;
                        default -> R.string.unable_to_install_content;
                    };
                    String errorMsg = context.getString(errorMsgResId);
                    Toast.makeText(context, context.getString(R.string.install_failed) + ": " + errorMsg, Toast.LENGTH_LONG).show();
                });
                
                // Удаляем временный файл
                if (tempFile.exists()) tempFile.delete();
            }
            
            @Override
            public void onSucceed(ContentProfile profile) {
                if (isExtracting) {
                    isExtracting = false;
                    // Автоматически продолжаем установку без показа диалогов
                    manager.finishInstallContent(profile, this);
                } else {
                    mainHandler.post(() -> {
                        downloadDialog.dismiss();
                        Toast.makeText(context, context.getString(R.string.installed_successfully, profile.verName), Toast.LENGTH_LONG).show();
                        
                        // Обновляем список версий
                        manager.syncContents();
                        loadDxvkVersionSpinner(manager, sVersion);

                        // Автоматически выбираем только что установленную версию в спиннере
                        String versionEntry = profile.verName + "-" + profile.verCode;
                        AppUtils.setSpinnerSelectionFromValue(sVersion, versionEntry);
                    });
                    
                    // Удаляем временный файл
                    if (tempFile.exists()) tempFile.delete();
                }
            }
        };
        
        // Запускаем установку
        manager.extraContentFile(Uri.fromFile(tempFile), callback);
    }
}
