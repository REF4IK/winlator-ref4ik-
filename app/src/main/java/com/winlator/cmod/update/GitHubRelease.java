package com.winlator.cmod.update;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GitHubRelease {
    @SerializedName("tag_name")
    private String tagName;

    @SerializedName("name")
    private String name;

    @SerializedName("body")
    private String body;

    @SerializedName("prerelease")
    private boolean prerelease;

    @SerializedName("published_at")
    private String publishedAt;

    @SerializedName("assets")
    private List<ReleaseAsset> assets;

    @SerializedName("html_url")
    private String htmlUrl;

    public String getTagName() {
        return tagName;
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body != null ? body : "";
    }

    public boolean isPrerelease() {
        return prerelease;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public List<ReleaseAsset> getAssets() {
        return assets;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public ReleaseAsset getFirstApkAsset() {
        if (assets != null) {
            for (ReleaseAsset asset : assets) {
                if (asset.isApk()) {
                    return asset;
                }
            }
        }
        return null;
    }

    public boolean hasApkAsset() {
        return getFirstApkAsset() != null;
    }

    public String getVersionName() {
        // Remove 'v' prefix if present
        if (tagName != null && tagName.startsWith("v")) {
            return tagName.substring(1);
        }
        return tagName;
    }
}
