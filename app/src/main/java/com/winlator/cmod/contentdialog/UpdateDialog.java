package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.update.GitHubRelease;
import com.winlator.cmod.update.ReleaseAsset;
import com.winlator.cmod.update.UpdateManager;

import java.io.File;

public class UpdateDialog extends ContentDialog {
    private final GitHubRelease release;
    private final UpdateManager updateManager;
    private final Handler mainHandler;
    
    private TextView tvVersionInfo;
    private TextView tvReleaseNotes;
    private TextView tvFileSize;
    private ProgressBar progressBar;
    private TextView tvProgress;
    private Button btDownload;
    private Button btLater;
    private Button btSkip;
    
    private boolean isDownloading = false;
    
    public UpdateDialog(Context context, GitHubRelease release) {
        super(context, R.layout.update_dialog);
        this.release = release;
        this.updateManager = new UpdateManager(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        setTitle(R.string.update_available);
        setIcon(R.drawable.icon_settings);
        
        setupViews();
        setupButtons();
        displayReleaseInfo();
    }
    
    private void setupViews() {
        tvVersionInfo = findViewById(R.id.TVVersionInfo);
        tvReleaseNotes = findViewById(R.id.TVReleaseNotes);
        tvFileSize = findViewById(R.id.TVFileSize);
        progressBar = findViewById(R.id.ProgressBar);
        tvProgress = findViewById(R.id.TVProgress);
        btDownload = findViewById(R.id.BTDownload);
        btLater = findViewById(R.id.BTLater);
        btSkip = findViewById(R.id.BTSkip);
    }
    
    private void setupButtons() {
        btDownload.setOnClickListener(v -> startDownload());
        btLater.setOnClickListener(v -> dismiss());
        btSkip.setOnClickListener(v -> {
            updateManager.skipVersion(release.getTagName());
            dismiss();
        });
    }
    
    private void displayReleaseInfo() {
        String versionText = getContext().getString(R.string.new_version_available, release.getTagName());
        if (release.isPrerelease()) {
            versionText += " (Beta)";
        }
        tvVersionInfo.setText(versionText);
        
        String releaseNotes = release.getBody();
        if (releaseNotes.isEmpty()) {
            releaseNotes = getContext().getString(R.string.no_release_notes);
        }
        tvReleaseNotes.setText(releaseNotes);
        
        ReleaseAsset apkAsset = release.getFirstApkAsset();
        if (apkAsset != null) {
            String sizeText = getContext().getString(R.string.file_size, apkAsset.getFormattedSize());
            tvFileSize.setText(sizeText);
        } else {
            tvFileSize.setText(R.string.no_apk_found);
            btDownload.setEnabled(false);
        }
    }
    
    private void startDownload() {
        if (isDownloading) return;
        
        ReleaseAsset apkAsset = release.getFirstApkAsset();
        if (apkAsset == null) {
            showError(getContext().getString(R.string.no_apk_found));
            return;
        }
        
        isDownloading = true;
        btDownload.setEnabled(false);
        btLater.setEnabled(false);
        btSkip.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvProgress.setVisibility(View.VISIBLE);
        
        updateManager.downloadAndInstall(apkAsset.getBrowserDownloadUrl(), new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(int progress, long downloaded, long total) {
                mainHandler.post(() -> {
                    progressBar.setProgress(progress);
                    String progressText = String.format("%d%% (%s / %s)", 
                        progress,
                        formatBytes(downloaded),
                        formatBytes(total));
                    tvProgress.setText(progressText);
                });
            }
            
            @Override
            public void onComplete(File file) {
                mainHandler.post(() -> {
                    boolean installStarted = updateManager.installApk(file);
                    if (installStarted) {
                        dismiss();
                    } else {
                        showError("APK downloaded, but failed to open the installer.");
                        resetDownloadState();
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                mainHandler.post(() -> {
                    showError(error);
                    resetDownloadState();
                });
            }
        });
    }
    
    private void resetDownloadState() {
        isDownloading = false;
        btDownload.setEnabled(true);
        btLater.setEnabled(true);
        btSkip.setEnabled(true);
        progressBar.setVisibility(View.GONE);
        tvProgress.setVisibility(View.GONE);
    }
    
    private void showError(String message) {
        ContentDialog.alert(getContext(), message, null);
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    @Override
    public void setCanceledOnTouchOutside(boolean cancel) {
        super.setCanceledOnTouchOutside(!isDownloading && cancel);
    }
}
