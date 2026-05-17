package com.winlator.cmod.update;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateManager {
    private static final String TAG = "UpdateManager";
    private static final String GITHUB_API_BASE_URL = "https://api.github.com/";
    private static final String REPO_OWNER = "REF4IK";
    private static final String REPO_NAME = "update-url-mod-";
    
    private static final String PREF_RECEIVE_BETA = "receive_beta_updates";
    private static final String PREF_SKIPPED_VERSION = "skipped_version";
    
    private final Context context;
    private final SharedPreferences preferences;
    private final UpdateService updateService;
    
    public interface UpdateCheckCallback {
        void onUpdateAvailable(GitHubRelease release);
        void onNoUpdateAvailable();
        void onError(String error);
    }
    
    public interface DownloadCallback {
        void onProgress(int progress, long downloaded, long total);
        void onComplete(File file);
        void onError(String error);
    }
    
    public UpdateManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        
        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
        
        this.updateService = retrofit.create(UpdateService.class);
    }
    
    public void checkForUpdates(UpdateCheckCallback callback) {
        boolean includeBeta = preferences.getBoolean(PREF_RECEIVE_BETA, false);
        String skippedVersion = preferences.getString(PREF_SKIPPED_VERSION, null);
        
        updateService.getLatestRelease(REPO_OWNER, REPO_NAME).enqueue(new Callback<GitHubRelease>() {
            @Override
            public void onResponse(Call<GitHubRelease> call, retrofit2.Response<GitHubRelease> response) {
                if (response.isSuccessful() && response.body() != null) {
                    GitHubRelease release = response.body();
                    
                    // Skip if it's a prerelease and beta is disabled
                    if (release.isPrerelease() && !includeBeta) {
                        callback.onNoUpdateAvailable();
                        return;
                    }
                    
                    // Skip if user skipped this version
                    if (skippedVersion != null && skippedVersion.equals(release.getTagName())) {
                        callback.onNoUpdateAvailable();
                        return;
                    }
                    
                    // Check if update is available
                    if (isUpdateAvailable(release)) {
                        callback.onUpdateAvailable(release);
                    } else {
                        callback.onNoUpdateAvailable();
                    }
                } else {
                    callback.onError("Failed to fetch release: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<GitHubRelease> call, Throwable t) {
                Log.e(TAG, "Error checking for updates", t);
                callback.onError(t.getMessage());
            }
        });
    }
    
    private boolean isUpdateAvailable(GitHubRelease release) {
        String currentVersion = getCurrentVersion();
        String releaseVersion = release.getVersionName();
        
        Log.d(TAG, "Current version: " + currentVersion + ", Release version: " + releaseVersion);
        
        if (currentVersion == null || releaseVersion == null) {
            return false;
        }
        
        int cmp = compareVersions(releaseVersion, currentVersion);
        Log.d(TAG, "Version comparison result: " + cmp + " (>0 means update available)");
        return cmp > 0;
    }
    
    private String getCurrentVersion() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Error getting current version", e);
            return null;
        }
    }
    
    private int compareVersions(String version1, String version2) {
        // Remove 'v' prefix if present
        version1 = version1.replaceAll("^v", "");
        version2 = version2.replaceAll("^v", "");
        
        // Remove beta/alpha suffixes for comparison
        version1 = version1.split("-")[0];
        version2 = version2.split("-")[0];
        
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int v1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int v2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            
            if (v1 != v2) {
                return Integer.compare(v1, v2);
            }
        }
        return 0;
    }
    
    private int parseVersionPart(String part) {
        try {
            String numeric = part.replaceAll("[^0-9].*", "");
            if (numeric.isEmpty()) return 0;
            return Integer.parseInt(numeric);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    public void downloadAndInstall(String downloadUrl, DownloadCallback callback) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder().url(downloadUrl).build();
                
                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) {
                    callback.onError("Download failed: " + response.code());
                    return;
                }
                
                File downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (downloadDir == null) {
                    callback.onError("Cannot access download directory");
                    return;
                }
                
                File apkFile = new File(downloadDir, "update.apk");
                
                long totalBytes = response.body().contentLength();
                long downloadedBytes = 0;
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                java.io.InputStream inputStream = response.body().byteStream();
                java.io.FileOutputStream outputStream = new java.io.FileOutputStream(apkFile);
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;
                    
                    int progress = (int) ((downloadedBytes * 100) / totalBytes);
                    long finalDownloadedBytes = downloadedBytes;
                    callback.onProgress(progress, finalDownloadedBytes, totalBytes);
                }
                
                outputStream.close();
                inputStream.close();
                
                callback.onComplete(apkFile);
                
            } catch (IOException e) {
                Log.e(TAG, "Error downloading update", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }
    
    public boolean installApk(File apkFile) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".tileprovider",
                apkFile
            );

            Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            installIntent.setData(apkUri);
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);

            if (installIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(installIntent);
                return true;
            }

            Intent fallbackIntent = new Intent(Intent.ACTION_VIEW);
            fallbackIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            fallbackIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (fallbackIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(fallbackIntent);
                return true;
            }

            Log.e(TAG, "No package installer activity found for APK installation");
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start APK installation", e);
            return false;
        }
    }
    
    public void skipVersion(String version) {
        preferences.edit().putString(PREF_SKIPPED_VERSION, version).apply();
    }
    
    public void clearSkippedVersion() {
        preferences.edit().remove(PREF_SKIPPED_VERSION).apply();
    }
    
    public boolean isBetaEnabled() {
        return preferences.getBoolean(PREF_RECEIVE_BETA, false);
    }
    
    public void setBetaEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_RECEIVE_BETA, enabled).apply();
    }
}
