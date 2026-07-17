package com.winlator.cmod.core.gameconfig;

import android.content.Context;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.core.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BundledConfigApplier {
    private final Context context;

    public BundledConfigApplier(Context context) {
        this.context = context;
    }

    public interface ApplyCallback {
        void onProgress(String status);
        void onComplete(boolean success, String message, GameConfig config);
    }

    public void applyFromUrl(final String bundleUrl, final Container container, final Shortcut shortcut, final ApplyCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("Downloading bundle...");

                File cacheDir = new File(context.getCacheDir(), "bundle_apply");
                cacheDir.mkdirs();
                File tempZip = new File(cacheDir, "bundle_" + System.currentTimeMillis() + ".zip");

                boolean downloaded = com.winlator.cmod.contents.Downloader.downloadFile(bundleUrl, tempZip);
                if (!downloaded) {
                    callback.onComplete(false, "Download failed", null);
                    return;
                }

                callback.onProgress("Extracting bundle...");
                File extractDir = new File(cacheDir, "extract_" + System.currentTimeMillis());
                extractDir.mkdirs();

                unzip(tempZip, extractDir);

                callback.onProgress("Installing components...");

                File contentsRoot = ContentsManager.getContentDir(context);
                File adrenotoolsRoot = new File(context.getFilesDir(), "imagefs/contents/adrenotools");

                File contentsExtracted = new File(extractDir, "contents");
                if (contentsExtracted.exists()) {
                    FileUtils.copy(contentsExtracted, contentsRoot);
                }

                File adrenotoolsExtracted = new File(extractDir, "adrenotools");
                if (adrenotoolsExtracted.exists()) {
                    adrenotoolsRoot.mkdirs();
                    FileUtils.copy(adrenotoolsExtracted, adrenotoolsRoot);
                }

                ContentsManager cm = new ContentsManager(context);
                cm.syncContents();

                callback.onProgress("Applying config...");

                File configFile = new File(extractDir, "config.json");
                if (configFile.exists()) {
                    String json = new String(java.nio.file.Files.readAllBytes(configFile.toPath()));
                    GameConfig config = GameConfig.fromJsonString(json);
                    tempZip.delete();
                    FileUtils.delete(extractDir);
                    if (config != null) {
                        callback.onComplete(true, "Components installed", config);
                    } else {
                        callback.onComplete(false, "Invalid config.json", null);
                    }
                    return;
                }

                tempZip.delete();
                FileUtils.delete(extractDir);
                callback.onComplete(false, "Invalid bundle: config.json missing", null);

            } catch (Exception e) {
                callback.onComplete(false, "Error: " + e.getMessage(), null);
            }
        }).start();
    }

    private void unzip(File zipFile, File destDir) throws Exception {
        try (InputStream is = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                File outFile = new File(destDir, entry.getName());
                String canonicalDest = destDir.getCanonicalPath() + File.separator;
                String canonicalOut = outFile.getCanonicalPath();
                if (!canonicalOut.startsWith(canonicalDest)) {
                    throw new SecurityException("Invalid zip entry path: " + entry.getName());
                }
                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (OutputStream os = new FileOutputStream(outFile)) {
                    int len;
                    while ((len = zis.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                }
            }
        }
    }
}
