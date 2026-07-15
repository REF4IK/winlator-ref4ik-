package com.winlator.cmod.core.gameconfig;

import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.Executors;

public class ConfigIndex {
    private static final String CACHE_FILE = "games_canonical.json";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    private JSONObject cachedIndex;
    private long lastFetchTime;

    public ConfigIndex() {
        this.cachedIndex = null;
        this.lastFetchTime = 0;
    }

    public void getIndex(File cacheDir, Callback<JSONObject> callback) {
        fetchIndex(cacheDir, callback, false);
    }

    public void refreshIndex(File cacheDir, Callback<JSONObject> callback) {
        clearCache(cacheDir);
        fetchIndex(cacheDir, callback, true);
    }

    private void fetchIndex(File cacheDir, Callback<JSONObject> callback, boolean force) {
        long now = System.currentTimeMillis();

        if (!force && cachedIndex != null && (now - lastFetchTime) < CACHE_TTL_MS) {
            callback.call(cachedIndex);
            return;
        }

        File cacheFile = new File(cacheDir, CACHE_FILE);

        if (!force && cacheFile.exists() && (now - cacheFile.lastModified()) < CACHE_TTL_MS) {
            try {
                String content = FileUtils.readString(cacheFile);
                cachedIndex = new JSONObject(content);
                lastFetchTime = now;
                callback.call(cachedIndex);
                return;
            } catch (Exception ignored) {}
        }

        CloudConfigRepoV2.fetchIndex(index -> {
            if (index != null && index.length() > 0) {
                cachedIndex = index;
                lastFetchTime = now;
                try {
                    FileUtils.writeString(cacheFile, index.toString());
                } catch (Exception ignored) {}
            }
            callback.call(index != null ? index : new JSONObject());
        });
    }

    public void clearCache(File cacheDir) {
        cachedIndex = null;
        lastFetchTime = 0;
        File cacheFile = new File(cacheDir, CACHE_FILE);
        if (cacheFile.exists()) cacheFile.delete();
    }
}
