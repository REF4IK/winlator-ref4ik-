package com.winlator.cmod.core.gameconfig;

import com.winlator.cmod.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BundleRepoClient {
    static final String WORKER_URL = BuildConfig.CLOUDFLARE_WORKER_URL;
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    public interface BundleUploadCallback {
        void onComplete(boolean success, String sha, String bundleUrl, String error);
    }

    public interface BundleListCallback {
        void onComplete(JSONArray bundles);
    }

    public interface BundleDeleteCallback {
        void onComplete(boolean success, String error);
    }

    public static void uploadBundle(File zipFile, String configJson, String gameName, String description, BundleUploadCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                byte[] zipBytes = new byte[(int) zipFile.length()];
                try (FileInputStream fis = new FileInputStream(zipFile)) {
                    int offset = 0;
                    while (offset < zipBytes.length) {
                        int read = fis.read(zipBytes, offset, zipBytes.length - offset);
                        if (read == -1) break;
                        offset += read;
                    }
                }
                String base64 = android.util.Base64.encodeToString(zipBytes, android.util.Base64.NO_WRAP);

                JSONObject body = new JSONObject();
                body.put("base64", base64);
                body.put("configJson", configJson);
                body.put("gameName", gameName);
                body.put("description", description != null ? description : "");

                RequestBody reqBody = RequestBody.create(body.toString(), JSON_MEDIA);
                Request request = new Request.Builder()
                        .url(WORKER_URL + "/api/bundles")
                        .post(reqBody)
                        .build();
                Response response = okhttpClient().newCall(request).execute();
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject result = new JSONObject(responseBody);
                boolean success = result.optBoolean("success", false);
                callback.onComplete(success,
                    result.optString("sha", ""),
                    result.optString("bundleUrl", ""),
                    success ? null : result.optString("error", "Upload failed"));
            } catch (Exception e) {
                callback.onComplete(false, "", "", e.getMessage() != null ? e.getMessage() : "Unknown error");
            }
        });
    }

    public static void fetchBundles(String gameName, BundleListCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String encoded = URLEncoder.encode(gameName, "UTF-8");
                String url = WORKER_URL + "/api/bundles?game=" + encoded;
                Request request = new Request.Builder().url(url).build();
                Response response = okhttpClient().newCall(request).execute();
                String body = response.body() != null ? response.body().string() : "[]";
                callback.onComplete(new JSONArray(body));
            } catch (Exception e) {
                callback.onComplete(new JSONArray());
            }
        });
    }

    public static void deleteBundle(String sha, String uploadToken, BundleDeleteCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sha", sha);
                body.put("upload_token", uploadToken);
                String url = WORKER_URL + "/api/bundles";

                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("DELETE");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "Winlator.CMOD-Configs");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes("UTF-8"));
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    JSONObject result = new JSONObject(reader.readLine());
                    callback.onComplete(result.optBoolean("success", false), result.optString("error", null));
                }
            } catch (Exception e) {
                callback.onComplete(false, e.getMessage());
            }
        });
    }

    private static okhttp3.OkHttpClient okhttpClient() {
        return com.winlator.cmod.core.DohOkHttp.get();
    }
}
