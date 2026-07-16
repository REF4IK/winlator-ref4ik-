package com.winlator.cmod.core.gameconfig;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.core.DriverResolver;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class ComponentResolver {
    private static final String TAG = "ComponentResolver";
    private final ContentsManager contentsManager;
    private final AdrenotoolsManager adrenotoolsManager;
    private final Context context;
    private boolean remoteLoaded;

    public ComponentResolver(Context context) {
        this.context = context;
        this.contentsManager = new ContentsManager(context);
        this.adrenotoolsManager = new AdrenotoolsManager(context);
        this.remoteLoaded = false;
    }

    public static class ComponentStatus {
        public final String label;
        public final String version;
        public boolean installed;
        public boolean downloadable;
        public String downloadUrl;
        public boolean isGpuDriver;

        public ComponentStatus(String label, String version, boolean installed) {
            this.label = label;
            this.version = version;
            this.installed = installed;
            this.downloadable = false;
            this.downloadUrl = null;
            this.isGpuDriver = false;
        }
    }

    public interface ResolveCallback {
        void onResult(List<ComponentStatus> statuses);
    }

    public void resolve(JSONObject containerSettings, ResolveCallback callback) {
        ArrayList<ComponentStatus> result = new ArrayList<>();
        if (containerSettings == null) { callback.onResult(result); return; }

        new Thread(() -> {
            try {
                contentsManager.syncContents();
                loadRemoteProfiles();

                checkComponent(result, containerSettings, "dxwrapperConfig", "version",
                    ContentProfile.ContentType.CONTENT_TYPE_DXVK, "DXVK");
                checkComponent(result, containerSettings, "dxwrapperConfig", "vkd3dVersion",
                    ContentProfile.ContentType.CONTENT_TYPE_VKD3D, "VKD3D");
                checkSimple(result, containerSettings, "box64Version",
                    ContentProfile.ContentType.CONTENT_TYPE_BOX64, "Box64");
                checkSimple(result, containerSettings, "fexcoreVersion",
                    ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, "FEXCore");

                String gpuConfig = containerSettings.optString("graphicsDriverConfig", "");
                if (!gpuConfig.isEmpty()) {
                    String gpuVer = parseVersion(gpuConfig, "version");
                    if (gpuVer != null && !gpuVer.isEmpty()) {
                        checkGpuDriver(result, gpuVer);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "resolve error", e);
            }
            callback.onResult(result);
        }).start();
    }

    private void checkComponent(ArrayList<ComponentStatus> result, JSONObject cs,
                                 String configKey, String versionKey,
                                 ContentProfile.ContentType type, String label) {
        if (!cs.has(configKey)) return;
        String configStr = cs.optString(configKey, "");
        if (configStr.isEmpty()) return;
        String version = parseVersion(configStr, versionKey);
        if (version == null || version.isEmpty()) return;
        checkVersion(result, version, type, label);
    }

    private void checkSimple(ArrayList<ComponentStatus> result, JSONObject cs,
                              String key, ContentProfile.ContentType type, String label) {
        if (!cs.has(key)) return;
        String version = cs.optString(key, "");
        if (version.isEmpty()) return;
        checkVersion(result, version, type, label);
    }

    private void checkVersion(ArrayList<ComponentStatus> result, String version,
                               ContentProfile.ContentType type, String label) {
        ComponentStatus status = new ComponentStatus(label, version, false);
        List<ContentProfile> profiles = contentsManager.getProfiles(type);
        if (profiles != null) {
            for (ContentProfile p : profiles) {
                if (p.remoteUrl == null) {
                    status.installed = true;
                    break;
                }
            }
            if (!status.installed) {
                for (ContentProfile p : profiles) {
                    if (p.remoteUrl != null && p.verName != null && matches(p.verName, version)) {
                        status.downloadable = true;
                        status.downloadUrl = p.remoteUrl;
                        break;
                    }
                }
            }
        }
        result.add(status);
    }

    private void checkGpuDriver(ArrayList<ComponentStatus> result, String version) {
        ComponentStatus status = new ComponentStatus("GPU", version, false);
        status.isGpuDriver = true;

        List<String> installedDrivers = adrenotoolsManager.enumarateInstalledDrivers();
        for (String id : installedDrivers) {
            String name = adrenotoolsManager.getDriverName(id);
            String driverVer = adrenotoolsManager.getDriverVersion(id);
            if (name != null && matches(name, version)) {
                status.installed = true;
                break;
            }
            if (driverVer != null && matches(driverVer, version)) {
                status.installed = true;
                break;
            }
        }

        status.downloadable = true;
        result.add(status);
    }

    private boolean matches(String a, String b) {
        String lowerA = a.toLowerCase();
        String lowerB = b.toLowerCase();
        return lowerA.equals(lowerB) || lowerA.contains(lowerB) || lowerB.contains(lowerA);
    }

    public interface InstallCallback {
        void onComplete(boolean success, String message);
    }

    public static void installComponent(ComponentStatus status, Context ctx, InstallCallback callback) {
        new Thread(() -> {
            try {
                if (status.isGpuDriver) {
                    installGpuDriver(status, ctx, callback);
                } else if (status.downloadUrl != null) {
                    installContentComponent(status, ctx, callback);
                } else {
                    callback.onComplete(false, "No download URL available");
                }
            } catch (Exception e) {
                callback.onComplete(false, e.getMessage());
            }
        }).start();
    }

    private static void installContentComponent(ComponentStatus status, Context ctx, InstallCallback callback) throws Exception {
        ContentsManager mgr = new ContentsManager(ctx);
        String url = status.downloadUrl;
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        File cacheDir = new File(ctx.getCacheDir(), "comp_install");
        cacheDir.mkdirs();
        File tempFile = new File(cacheDir, fileName);

        Log.d(TAG, "Downloading " + url + " to " + tempFile);
        boolean ok = Downloader.downloadFile(url, tempFile);
        if (!ok) { callback.onComplete(false, "Download failed"); return; }

        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] success = {false};
        final String[] errorMsg = {null};

        mgr.extraContentFile(Uri.fromFile(tempFile), new ContentsManager.OnInstallFinishedCallback() {
            @Override public void onFailed(ContentsManager.InstallFailedReason reason, Exception e) {
                errorMsg[0] = "Extract failed: " + reason;
                latch.countDown();
            }
            @Override public void onSucceed(ContentProfile profile) {
                mgr.finishInstallContent(profile, new ContentsManager.OnInstallFinishedCallback() {
                    @Override public void onFailed(ContentsManager.InstallFailedReason reason, Exception e) {
                        errorMsg[0] = "Install failed: " + reason;
                        latch.countDown();
                    }
                    @Override public void onSucceed(ContentProfile profile2) {
                        mgr.applyContent(profile2);
                        success[0] = true;
                        latch.countDown();
                    }
                });
            }
        });

        latch.await(120, TimeUnit.SECONDS);
        tempFile.delete();
        callback.onComplete(success[0], errorMsg[0]);
    }

    private static void installGpuDriver(ComponentStatus status, Context ctx, InstallCallback callback) throws Exception {
        DriverResolver resolver = new DriverResolver(ctx);
        AdrenotoolsManager adreno = new AdrenotoolsManager(ctx);

        final CountDownLatch searchLatch = new CountDownLatch(1);
        final AtomicReference<DriverResolver.DriverInfo> foundDriver = new AtomicReference<>(null);

        resolver.searchDrivers(new DriverResolver.DriverSearchCallback() {
            @Override public void onDriversFound(List<DriverResolver.DriverInfo> drivers) {
                String target = status.version.toLowerCase();
                for (DriverResolver.DriverInfo d : drivers) {
                    String dn = d.name != null ? d.name.toLowerCase() : "";
                    String dv = d.version != null ? d.version.toLowerCase() : "";
                    if (dn.contains(target) || target.contains(dn) || dv.contains(target) || target.contains(dv)) {
                        foundDriver.set(d);
                        break;
                    }
                }
                searchLatch.countDown();
            }
            @Override public void onError(String error) {
                searchLatch.countDown();
            }
        });

        searchLatch.await(60, TimeUnit.SECONDS);
        if (foundDriver.get() == null) {
            callback.onComplete(false, "No matching GPU driver found");
            return;
        }

        final CountDownLatch downloadLatch = new CountDownLatch(1);
        final AtomicReference<Uri> downloadedUri = new AtomicReference<>(null);

        resolver.downloadDriver(foundDriver.get(), new DriverResolver.DriverDownloadCallback() {
            @Override public void onProgress(int progress) {}
            @Override public void onComplete(Uri uri) { downloadedUri.set(uri); downloadLatch.countDown(); }
            @Override public void onError(String error) { downloadLatch.countDown(); }
        });

        downloadLatch.await(180, TimeUnit.SECONDS);
        if (downloadedUri.get() == null) {
            callback.onComplete(false, "Download failed");
            return;
        }

        String id = adreno.installDriver(downloadedUri.get());
        boolean success = id != null && !id.isEmpty();
        callback.onComplete(success, success ? null : "Install failed");
    }

    private String parseVersion(String configStr, String key) {
        if (configStr == null || configStr.isBlank()) return null;
        for (String part : configStr.split("[,;]")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(key)) {
                String val = kv[1].trim();
                return val.isEmpty() ? null : val;
            }
        }
        return null;
    }

    private void loadRemoteProfiles() {
        if (remoteLoaded) return;
        try {
            String url = "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json";
            String json = Downloader.downloadString(url);
            if (json != null) {
                contentsManager.setRemoteProfiles(json);
            }
        } catch (Exception ignored) {
        }
        remoteLoaded = true;
    }
}
