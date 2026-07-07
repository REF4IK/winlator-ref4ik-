package com.winlator.cmod.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DriverResolver {
    private static final String TAG = "DriverResolver";
    private static final String GITHUB_API_BASE = "https://api.github.com/repos/";
    private static final int RELEASES_PER_PAGE = 100;
    private static final int MAX_RELEASE_PAGES = 5;
    
    // Альтернативные зеркала для обхода блокировок
    private static final String[] GITHUB_MIRRORS = {
        "https://api.github.com/repos/",
        "https://gh.api.99988866.xyz/",
        "https://ghproxy.com/https://api.github.com/repos/",
        "https://mirror.ghproxy.com/https://api.github.com/repos/"
    };
    
    private static final String[] DEFAULT_DRIVER_REPOSITORIES = {
        "MrPurple666/purple-turnip",
        "crueter/GameHub-8Elite-Drivers", 
        "K11MCH1/AdrenoToolsDrivers",
        "Weab-chan/freedreno_turnip-CI",
        "StevenMXZ/Adreno-Tools-Drivers"
    };
    
    private static final String PREFS_NAME = "DriverResolverPrefs";
    private static final String PREFS_CUSTOM_REPOS = "custom_repositories";
    private static final String PREFS_CUSTOM_REPO_NAMES = "custom_repository_names";
    
    private final ExecutorService executor;
    private final Context context;
    private final SharedPreferences prefs;
    
    public static class DriverInfo {
        public String name;
        public String version;
        public String downloadUrl;
        public String repoName;
        public String description;
        public long size;
        public String publishedAt;
        
        public DriverInfo(String name, String version, String downloadUrl, String repoName) {
            this.name = name;
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.repoName = repoName;
        }
    }
    
    public interface DriverSearchCallback {
        void onDriversFound(List<DriverInfo> drivers);
        void onError(String error);
    }
    
    public interface DriverDownloadCallback {
        void onProgress(int progress);
        void onComplete(Uri driverUri);
        void onError(String error);
    }
    
    public DriverResolver(Context context) {
        this.context = context;
        this.executor = Executors.newCachedThreadPool();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public void searchDrivers(DriverSearchCallback callback) {
        executor.execute(() -> {
            try {
                if (!isNetworkAvailable()) {
                    callback.onError("Нет доступа к интернету");
                    return;
                }
                
                List<DriverInfo> allDrivers = new ArrayList<>();
                
                // Загружаем из стандартных репозиториев
                for (String repo : DEFAULT_DRIVER_REPOSITORIES) {
                    try {
                        List<DriverInfo> repoDrivers = fetchDriversFromRepo(repo);
                        allDrivers.addAll(repoDrivers);
                        Log.d(TAG, "Загружено " + repoDrivers.size() + " драйверов из " + repo);
                    } catch (Exception e) {
                        Log.w(TAG, "Ошибка при загрузке из репозитория " + repo + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                // Загружаем из пользовательских репозиториев
                List<String> customRepos = getCustomRepositories();
                for (String repo : customRepos) {
                    try {
                        List<DriverInfo> repoDrivers = fetchDriversFromRepo(repo);
                        allDrivers.addAll(repoDrivers);
                        Log.d(TAG, "Загружено " + repoDrivers.size() + " драйверов из пользовательского репозитория " + repo);
                    } catch (Exception e) {
                        Log.w(TAG, "Ошибка при загрузке из пользовательского репозитория " + repo + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }
                
                Log.d(TAG, "Всего загружено драйверов: " + allDrivers.size());
                
                if (allDrivers.isEmpty()) {
                    callback.onError("Не удалось загрузить драйверы. Проверьте подключение к интернету.");
                } else {
                    callback.onDriversFound(allDrivers);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Ошибка поиска драйверов", e);
                callback.onError("Ошибка поиска драйверов: " + e.getMessage());
            }
        });
    }
    
    private List<DriverInfo> fetchDriversFromRepo(String repo) throws IOException, JSONException {
        List<DriverInfo> drivers = new ArrayList<>();
        Exception lastException = null;
        
        for (String mirror : GITHUB_MIRRORS) {
            try {
                List<DriverInfo> pagedDrivers = new ArrayList<>();
                for (int page = 1; page <= MAX_RELEASE_PAGES; page++) {
                    String url = mirror + repo + "/releases?per_page=" + RELEASES_PER_PAGE + "&page=" + page;
                    Log.d(TAG, "Попытка загрузки из: " + url);

                    List<DriverInfo> pageDrivers = tryFetchFromUrl(url, repo);
                    pagedDrivers.addAll(pageDrivers);

                    if (pageDrivers.isEmpty()) {
                        break;
                    }
                }

                drivers = pagedDrivers;
                
                if (!drivers.isEmpty()) {
                    Log.d(TAG, "Успешно загружено из " + mirror);
                    return drivers;
                }
            } catch (Exception e) {
                Log.w(TAG, "Не удалось загрузить из " + mirror + ": " + e.getMessage());
                lastException = e;
            }
        }
        
        if (lastException != null) {
            if (lastException instanceof IOException) {
                throw (IOException) lastException;
            } else if (lastException instanceof JSONException) {
                throw (JSONException) lastException;
            }
        }
        
        return drivers;
    }
    
    private List<DriverInfo> tryFetchFromUrl(String url, String repo) throws IOException, JSONException {
        List<DriverInfo> drivers = new ArrayList<>();
        
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent", "Winlator-App");
        
        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            
            InputStream inputStream = connection.getInputStream();
            String response = new Scanner(inputStream, "UTF-8").useDelimiter("\\A").next();
            inputStream.close();
            
            JSONArray releases = new JSONArray(response);
            
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.getJSONObject(i);
                
                if (release.getBoolean("draft") || release.getBoolean("prerelease")) {
                    continue;
                }
                
                String tagName = release.getString("tag_name");
                String name = release.optString("name", tagName);
                String description = release.optString("body", "");
                String publishedAt = release.optString("published_at", "");
                
                JSONArray assets = release.getJSONArray("assets");
                for (int j = 0; j < assets.length(); j++) {
                    JSONObject asset = assets.getJSONObject(j);
                    String fileName = asset.getString("name");
                    
                    if (fileName.toLowerCase().endsWith(".zip")) {
                        String downloadUrl = asset.getString("browser_download_url");
                        long size = asset.getLong("size");
                        
                        String driverName = parseDriverNameFromFile(fileName, name);
                        
                        DriverInfo driver = new DriverInfo(driverName, tagName, downloadUrl, getRepoDisplayName(repo));
                        driver.description = description;
                        driver.size = size;
                        driver.publishedAt = publishedAt;
                        
                        drivers.add(driver);
                        Log.d(TAG, "Найден драйвер: " + driverName + " (" + fileName + ")");
                    }
                }
            }
            
        } finally {
            connection.disconnect();
        }
        
        return drivers;
    }
    
    public void downloadDriver(DriverInfo driverInfo, DriverDownloadCallback callback) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Начинается загрузка драйвера: " + driverInfo.name);
                
                String[] downloadMirrors = {
                    driverInfo.downloadUrl,
                    "https://ghproxy.com/" + driverInfo.downloadUrl,
                    "https://mirror.ghproxy.com/" + driverInfo.downloadUrl
                };
                
                Exception lastException = null;
                for (String downloadUrl : downloadMirrors) {
                    try {
                        Log.d(TAG, "Попытка скачивания с: " + downloadUrl);
                        
                        URL url = new URL(downloadUrl);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setRequestMethod("GET");
                        connection.setConnectTimeout(30000);
                        connection.setReadTimeout(30000);
                        connection.setInstanceFollowRedirects(true);
                        
                        int responseCode = connection.getResponseCode();
                        if (responseCode != 200 && responseCode != 302) {
                            Log.w(TAG, "HTTP error code: " + responseCode + " для " + downloadUrl);
                            connection.disconnect();
                            continue;
                        }
                        
                        int contentLength = connection.getContentLength();
                        InputStream inputStream = connection.getInputStream();
                        
                        File tempDir = new File(context.getCacheDir(), "driver_downloads");
                        if (!tempDir.exists()) {
                            tempDir.mkdirs();
                        }
                        
                        String fileName = driverInfo.name.replaceAll("[^a-zA-Z0-9.-]", "_") + ".zip";
                        File tempFile = new File(tempDir, fileName);
                        
                        FileOutputStream outputStream = new FileOutputStream(tempFile);
                        
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        int totalBytesRead = 0;
                        
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                            totalBytesRead += bytesRead;
                            
                            if (contentLength > 0) {
                                int progress = (int) ((totalBytesRead * 100L) / contentLength);
                                callback.onProgress(progress);
                            }
                        }
                        
                        outputStream.close();
                        inputStream.close();
                        connection.disconnect();
                        
                        Log.d(TAG, "Драйвер успешно загружен с: " + downloadUrl);
                        callback.onComplete(Uri.fromFile(tempFile));
                        return;
                        
                    } catch (Exception e) {
                        Log.w(TAG, "Ошибка при скачивании с " + downloadUrl + ": " + e.getMessage());
                        lastException = e;
                    }
                }
                
                Log.e(TAG, "Не удалось скачать драйвер ни с одного зеркала");
                callback.onError("Не удалось скачать драйвер. Попробуйте позже.");
                
            } catch (Exception e) {
                Log.e(TAG, "Критическая ошибка загрузки", e);
                callback.onError("Ошибка загрузки: " + e.getMessage());
            }
        });
    }
    
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = 
            (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager == null) return false;
        
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) return false;
        
        NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        return networkCapabilities != null && 
               networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    
    public String getRepoDisplayName(String repo) {
        // Проверяем пользовательские имена
        String customName = getCustomRepositoryName(repo);
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        
        // Стандартные имена
        switch (repo) {
            case "MrPurple666/purple-turnip":
                return "Mr. Purple Turnip";
            case "crueter/GameHub-8Elite-Drivers":
                return "GameHub Adreno 8xx";
            case "K11MCH1/AdrenoToolsDrivers":
                return "KIMCHI Turnip";
            case "Weab-chan/freedreno_turnip-CI":
                return "Weab-Chan Freedreno";
            case "StevenMXZ/Adreno-Tools-Drivers":
                return "StevenMXZ Adreno Drivers";
            default:
                return repo;
        }
    }
    
    // Методы для работы с пользовательскими репозиториями
    
    public void addCustomRepository(String repoUrl, String repoName) {
        // Нормализуем URL репозитория
        String normalizedUrl = normalizeRepoUrl(repoUrl);
        
        Set<String> repos = prefs.getStringSet(PREFS_CUSTOM_REPOS, new HashSet<>());
        Set<String> reposCopy = new HashSet<>(repos);
        reposCopy.add(normalizedUrl);
        
        prefs.edit()
            .putStringSet(PREFS_CUSTOM_REPOS, reposCopy)
            .putString(PREFS_CUSTOM_REPO_NAMES + "_" + normalizedUrl, repoName)
            .apply();
        
        Log.d(TAG, "Добавлен пользовательский репозиторий: " + normalizedUrl + " (" + repoName + ")");
    }
    
    public void removeCustomRepository(String repoUrl) {
        String normalizedUrl = normalizeRepoUrl(repoUrl);
        
        Set<String> repos = prefs.getStringSet(PREFS_CUSTOM_REPOS, new HashSet<>());
        Set<String> reposCopy = new HashSet<>(repos);
        reposCopy.remove(normalizedUrl);
        
        prefs.edit()
            .putStringSet(PREFS_CUSTOM_REPOS, reposCopy)
            .remove(PREFS_CUSTOM_REPO_NAMES + "_" + normalizedUrl)
            .apply();
        
        Log.d(TAG, "Удален пользовательский репозиторий: " + normalizedUrl);
    }
    
    public List<String> getCustomRepositories() {
        Set<String> repos = prefs.getStringSet(PREFS_CUSTOM_REPOS, new HashSet<>());
        return new ArrayList<>(repos);
    }
    
    public String getCustomRepositoryName(String repoUrl) {
        String normalizedUrl = normalizeRepoUrl(repoUrl);
        return prefs.getString(PREFS_CUSTOM_REPO_NAMES + "_" + normalizedUrl, null);
    }
    
    private String normalizeRepoUrl(String url) {
        // Убираем протокол и www
        String normalized = url.replaceAll("^(https?://)?(www\\.)?github\\.com/", "");
        // Убираем завершающий слэш
        normalized = normalized.replaceAll("/$", "");
        return normalized;
    }
    
    private String parseDriverNameFromFile(String fileName, String baseName) {
        String nameWithoutExt = fileName.replaceAll("\\.zip$", "");
        String[] parts = nameWithoutExt.split("_");
        
        if (parts.length > 1) {
            String lastPart = parts[parts.length - 1];
            
            if (lastPart.matches("(?i)(Gmem|Sysmem|Freedreno|Mesa|Zink).*")) {
                return baseName + " (" + lastPart + ")";
            }
        }
        
        return baseName;
    }
    
    public void shutdown() {
        executor.shutdown();
    }
}
