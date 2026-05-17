package com.winlator.cmod.update;

import com.google.gson.annotations.SerializedName;

public class ReleaseAsset {
    @SerializedName("name")
    private String name;

    @SerializedName("browser_download_url")
    private String browserDownloadUrl;

    @SerializedName("size")
    private long size;

    @SerializedName("content_type")
    private String contentType;

    public String getName() {
        return name;
    }

    public String getBrowserDownloadUrl() {
        return browserDownloadUrl;
    }

    public long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }

    public boolean isApk() {
        return name != null && name.toLowerCase().endsWith(".apk");
    }

    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }
}
