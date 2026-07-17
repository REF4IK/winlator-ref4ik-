package com.winlator.cmod.core.gameconfig;

import android.content.Context;

import com.winlator.cmod.contentdialog.DXVKConfigDialog;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.DefaultVersion;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GameConfigBundler {
    private final Context context;
    private final ContentsManager contentsManager;
    private final AdrenotoolsManager adrenotoolsManager;

    public GameConfigBundler(Context context) {
        this.context = context;
        this.contentsManager = new ContentsManager(context);
        this.adrenotoolsManager = new AdrenotoolsManager(context);
    }

    public static class BundleResult {
        public final File zipFile;
        public final int componentCount;

        public BundleResult(File zipFile, int componentCount) {
            this.zipFile = zipFile;
            this.componentCount = componentCount;
        }
    }

    public BundleResult buildBundle(JSONObject settings, File outputDir) throws Exception {
        outputDir.mkdirs();
        File zipFile = File.createTempFile("bundle_", ".zip", outputDir);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            if (settings != null) {
                int count = addBundledComponentsToArchive(zos, settings);
                return new BundleResult(zipFile, count);
            }
        }
        return new BundleResult(zipFile, 0);
    }

    private int addBundledComponentsToArchive(ZipOutputStream zos, JSONObject settings) throws Exception {
        contentsManager.syncContents();
        int bundledCount = 0;

        String dxWrapper = settings.optString("dxwrapper", "");
        String dxConfigStr = settings.optString("dxwrapperConfig", Container.DEFAULT_DXWRAPPERCONFIG);
        com.winlator.cmod.core.KeyValueSet dxConfig = DXVKConfigDialog.parseConfig(dxConfigStr);

        if ("dxvk".equalsIgnoreCase(dxWrapper) || "d8vk".equalsIgnoreCase(dxWrapper)) {
            String version = dxConfig.get("version", "");
            bundledCount += addInstalledContentVersionToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_DXVK, version);
        } else if ("vkd3d".equalsIgnoreCase(dxWrapper)) {
            String version = dxConfig.get("vkd3dVersion", "");
            bundledCount += addInstalledContentVersionToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_VKD3D, version);
        }

        ContentProfile.ContentType boxType = getBoxContentTypeFromSettings(settings);
        String boxVersion = settings.optString("box64Version", "");
        bundledCount += addInstalledContentVersionToArchive(zos, boxType, boxVersion);

        String fexcoreVersion = settings.optString("fexcoreVersion", "");
        bundledCount += addInstalledContentVersionToArchive(zos, ContentProfile.ContentType.CONTENT_TYPE_FEXCORE, fexcoreVersion);

        String gpuConfig = settings.optString("graphicsDriverConfig", Container.DEFAULT_GRAPHICSDRIVERCONFIG);
        String graphicsDriverVersion = GraphicsDriverConfigDialog.getVersion(gpuConfig);
        bundledCount += addGraphicsDriverToArchive(zos, graphicsDriverVersion);

        return bundledCount;
    }

    private int addInstalledContentVersionToArchive(ZipOutputStream zos, ContentProfile.ContentType type, String version) throws Exception {
        if (version == null || version.isEmpty()) return 0;
        List<ContentProfile> profiles = contentsManager.getProfiles(type);
        if (profiles != null) {
            for (ContentProfile profile : profiles) {
                if (matchesContentVersion(profile, version) && ContentsManager.getInstallDir(context, profile).exists()) {
                    return addContentProfileToArchive(zos, profile);
                }
            }
        }
        File contentDir = new File(context.getFilesDir(), "imagefs/contents/" + type);
        if (contentDir.isDirectory()) {
            File[] dirs = contentDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir.getName().toLowerCase().contains(version.toLowerCase()) || version.toLowerCase().contains(dir.getName().toLowerCase())) {
                        zipDirectory(zos, dir, "contents/" + type + "/" + dir.getName());
                        return 1;
                    }
                }
            }
        }
        return 0;
    }

    private int addContentProfileToArchive(ZipOutputStream zos, ContentProfile profile) throws Exception {
        if (profile == null) return 0;
        File installDir = ContentsManager.getInstallDir(context, profile);
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
        return 0;
    }

    private File resolveInstalledGraphicsDriverDir(String selectedVersion) {
        if (selectedVersion == null || selectedVersion.isEmpty()) return null;

        for (String driverId : adrenotoolsManager.enumarateInstalledDrivers()) {
            if (selectedVersion.equalsIgnoreCase(driverId)
                    || selectedVersion.equalsIgnoreCase(adrenotoolsManager.getDriverName(driverId))
                    || selectedVersion.equalsIgnoreCase(adrenotoolsManager.getDriverVersion(driverId))
                    || selectedVersion.contains(adrenotoolsManager.getDriverName(driverId))) {
                return new File(context.getFilesDir(), "imagefs/contents/adrenotools/" + driverId);
            }
        }

        File directDir = new File(context.getFilesDir(), "imagefs/contents/adrenotools/" + selectedVersion);
        return directDir.exists() ? directDir : null;
    }

    private boolean matchesContentVersion(ContentProfile profile, String selectedVersion) {
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

    private String getVersionSpinnerValue(ContentProfile profile) {
        String entryName = ContentsManager.getEntryName(profile);
        int firstDashIndex = entryName.indexOf('-');
        return firstDashIndex >= 0 ? entryName.substring(firstDashIndex + 1) : entryName;
    }

    private ContentProfile.ContentType getBoxContentTypeFromSettings(JSONObject settings) {
        String storedType = settings.optString("box64ContentType", "");
        if (ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64.toString().equalsIgnoreCase(storedType)) {
            return ContentProfile.ContentType.CONTENT_TYPE_WOWBOX64;
        }
        if (ContentProfile.ContentType.CONTENT_TYPE_BOX64.toString().equalsIgnoreCase(storedType)) {
            return ContentProfile.ContentType.CONTENT_TYPE_BOX64;
        }
        return ContentProfile.ContentType.CONTENT_TYPE_BOX64;
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

}
