package com.winlator.cmod.core.gameconfig;

import com.winlator.cmod.BuildConfig;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.DohOkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudConfigRepo {
    private static final String WORKER_URL = BuildConfig.CLOUDFLARE_WORKER_URL;
    private static final String RAW_BASE = "https://raw.githubusercontent.com";
    private static final String INDEX_URL = RAW_BASE + "/REF4IK/winlator-ref4ik-configs/main/index.json";
    private static final String RECENT_URL = RAW_BASE + "/REF4IK/winlator-ref4ik-configs/main/recent.json";

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    public interface UploadCallback {
        void onComplete(boolean success, String message);
    }

    public interface GameConfigsCallback {
        void onComplete(JSONArray configFiles);
    }

    public static void uploadConfig(GameConfig config, UploadCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = config.toJson().toString();
                RequestBody body = RequestBody.create(json, JSON_MEDIA);
                Request request = new Request.Builder()
                        .url(WORKER_URL + "/api/config")
                        .post(body)
                        .build();
                Response response = DohOkHttp.get().newCall(request).execute();
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject result = new JSONObject(responseBody);
                boolean success = result.optBoolean("success", false);
                String message = success ? "Config published!" : result.optString("error", "Upload failed");
                callback.onComplete(success, message);
            } catch (Exception e) {
                callback.onComplete(false, e.getMessage());
            }
        });
    }

    public static void fetchIndex(Callback<JSONArray> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = downloadString(INDEX_URL);
                callback.call(new JSONArray(json));
            } catch (Exception e) {
                callback.call(new JSONArray());
            }
        });
    }

    public static void fetchRecent(Callback<JSONArray> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = downloadString(RECENT_URL);
                callback.call(new JSONArray(json));
            } catch (Exception e) {
                callback.call(new JSONArray());
            }
        });
    }

    public static void fetchConfigsForGame(String gameName, GameConfigsCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String encoded = URLEncoder.encode(gameName, "UTF-8");
                String url = WORKER_URL + "/api/game-configs?game=" + encoded;
                Request request = new Request.Builder().url(url).build();
                Response response = DohOkHttp.get().newCall(request).execute();
                String body = response.body() != null ? response.body().string() : "{}";
                JSONObject result = new JSONObject(body);
                JSONArray configs = result.optJSONArray("configs");
                callback.onComplete(configs != null ? configs : new JSONArray());
            } catch (Exception e) {
                callback.onComplete(new JSONArray());
            }
        });
    }

    public static void fetchConfigFile(String url, Callback<GameConfig> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = downloadString(url);
                GameConfig config = GameConfig.fromJsonString(json);
                callback.call(config);
            } catch (Exception e) {
                callback.call(null);
            }
        });
    }

    private static String downloadString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Winlator.CMOD");
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }
}
