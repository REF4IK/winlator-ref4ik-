package com.winlator.cmod.core.gameconfig;

import com.winlator.cmod.BuildConfig;
import com.winlator.cmod.core.Callback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CloudConfigRepoV2 {
    static final String WORKER_URL = BuildConfig.CLOUDFLARE_WORKER_URL;
    private static final String RAW_BASE = "https://raw.githubusercontent.com";
    private static final String INDEX_URL = WORKER_URL + "/api/games";

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    public interface UploadCallback {
        void onComplete(boolean success, String sha, String uploadToken, String error);
    }

    public interface GameListCallback {
        void onComplete(JSONArray configs);
    }

    public interface VoteCallback {
        void onResult(boolean success, int votesUp, int votesDown, String error);
    }

    public interface CommentCallback {
        void onResult(boolean success, String error);
    }

    public interface DeleteCallback {
        void onResult(boolean success, String error);
    }

    public static void fetchIndex(Callback<JSONObject> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = downloadString(INDEX_URL);
                callback.call(new JSONObject(json));
            } catch (Exception e) {
                callback.call(new JSONObject());
            }
        });
    }

    public static void fetchConfigsForGame(String gameName, GameListCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String encoded = URLEncoder.encode(gameName, "UTF-8");
                String url = WORKER_URL + "/api/configs?game=" + encoded;
                Request request = new Request.Builder().url(url).build();
                Response response = okhttpClient().newCall(request).execute();
                String body = response.body() != null ? response.body().string() : "[]";
                callback.onComplete(new JSONArray(body));
            } catch (Exception e) {
                callback.onComplete(new JSONArray());
            }
        });
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
                Response response = okhttpClient().newCall(request).execute();
                String responseBody = response.body() != null ? response.body().string() : "";
                JSONObject result = new JSONObject(responseBody);
                boolean success = result.optBoolean("success", false);
                callback.onComplete(success,
                    result.optString("sha", ""),
                    result.optString("upload_token", ""),
                    success ? null : result.optString("error", "Upload failed"));
            } catch (Exception e) {
                callback.onComplete(false, "", "", e.getMessage());
            }
        });
    }

    public static void vote(String sha, boolean upvote, VoteCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sha", sha);
                body.put("direction", upvote ? "up" : "down");
                String url = WORKER_URL + "/api/vote";
                String response = postJson(url, body.toString());
                JSONObject result = new JSONObject(response);
                boolean success = result.optBoolean("success", false);
                callback.onResult(success,
                    result.optInt("votes_up", 0),
                    result.optInt("votes_down", 0),
                    success ? null : result.optString("error", "Vote failed"));
            } catch (Exception e) {
                callback.onResult(false, 0, 0, e.getMessage());
            }
        });
    }

    public static void postComment(String sha, String text, String nickname, CommentCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sha", sha);
                body.put("text", text);
                body.put("nickname", nickname != null ? nickname : "Anonymous");
                String url = WORKER_URL + "/api/comment";
                String response = postJson(url, body.toString());
                JSONObject result = new JSONObject(response);
                callback.onResult(result.optBoolean("success", false), result.optString("error", null));
            } catch (Exception e) {
                callback.onResult(false, e.getMessage());
            }
        });
    }

    public static void fetchComments(String sha, Callback<JSONArray> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String url = WORKER_URL + "/api/comments?sha=" + URLEncoder.encode(sha, "UTF-8");
                String response = downloadString(url);
                callback.call(new JSONArray(response));
            } catch (Exception e) {
                callback.call(new JSONArray());
            }
        });
    }

    public static void deleteConfig(String sha, String game, String filename, String uploadToken, DeleteCallback callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sha", sha);
                body.put("game", game);
                body.put("filename", filename);
                body.put("upload_token", uploadToken);
                String url = WORKER_URL + "/api/config";
                String response = httpDelete(url, body.toString());
                JSONObject result = new JSONObject(response);
                callback.onResult(result.optBoolean("success", false), result.optString("error", null));
            } catch (Exception e) {
                callback.onResult(false, e.getMessage());
            }
        });
    }

    public static void fetchConfigFile(String url, Callback<GameConfig> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String json = downloadString(url);
                callback.call(GameConfig.fromJsonString(json));
            } catch (Exception e) {
                callback.call(null);
            }
        });
    }

    public static void searchGames(String query, Callback<JSONArray> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String url = WORKER_URL + "/api/search?q=" + URLEncoder.encode(query, "UTF-8");
                String response = downloadString(url);
                callback.call(new JSONArray(response));
            } catch (Exception e) {
                callback.call(new JSONArray());
            }
        });
    }

    // ── Internal HTTP methods ──

    private static okhttp3.OkHttpClient okhttpClient() {
        return com.winlator.cmod.core.DohOkHttp.get();
    }

    static String postJson(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "Winlator.CMOD-Configs");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(jsonBody);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return reader.readLine();
        }
    }

    private static String httpDelete(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "Winlator.CMOD-Configs");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(jsonBody);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return reader.readLine();
        }
    }

    private static String downloadString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent", "Winlator.CMOD-Configs");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        }
    }

    static String post(String urlStr, String jsonBody) throws Exception {
        return postJson(urlStr, jsonBody);
    }

    String get(String urlStr) throws Exception {
        return downloadString(urlStr);
    }
}
