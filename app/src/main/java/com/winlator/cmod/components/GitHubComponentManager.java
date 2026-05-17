package com.winlator.cmod.components;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Менеджер для работы с компонентами из GitHub Releases
 */
public class GitHubComponentManager {
    private static final String TAG = "GitHubComponentManager";
    private static final String GITHUB_API_BASE = "https://api.github.com/repos/";
    
    // Репозиторий с компонентами
    private static final String REPO_OWNER = "REF4IK";
    private static final String REPO_NAME = "Components-Adrenotools-";
    private static final String RELEASE_TAG = "111";

    public interface ComponentsCallback {
        void onComponentsLoaded(List<ComponentInfo> components);
        void onError(String error);
    }

    /**
     * Загрузить список компонентов из GitHub Release
     */
    public static void loadComponents(ComponentsCallback callback) {
        new LoadComponentsTask(callback).execute();
    }

    private static class LoadComponentsTask extends AsyncTask<Void, Void, List<ComponentInfo>> {
        private ComponentsCallback callback;
        private String error;

        LoadComponentsTask(ComponentsCallback callback) {
            this.callback = callback;
        }

        @Override
        protected List<ComponentInfo> doInBackground(Void... voids) {
            List<ComponentInfo> components = new ArrayList<>();
            HttpURLConnection connection = null;
            
            try {
                String urlString = GITHUB_API_BASE + REPO_OWNER + "/" + REPO_NAME + "/releases/tags/" + RELEASE_TAG;
                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // Парсим JSON
                    JSONObject releaseJson = new JSONObject(response.toString());
                    JSONArray assetsArray = releaseJson.getJSONArray("assets");

                    for (int i = 0; i < assetsArray.length(); i++) {
                        JSONObject asset = assetsArray.getJSONObject(i);
                        
                        String fileName = asset.getString("name");
                        String downloadUrl = asset.getString("browser_download_url");
                        long fileSize = asset.getLong("size");
                        
                        // Определяем тип компонента
                        String type = ComponentInfo.detectType(fileName);
                        
                        // Создаем красивое имя и описание
                        String displayName = formatDisplayName(fileName);
                        String description = getDescriptionForType(type, fileName);
                        
                        ComponentInfo component = new ComponentInfo(
                            String.valueOf(i),
                            fileName,
                            displayName,
                            description,
                            fileName,
                            downloadUrl,
                            fileSize,
                            type
                        );
                        
                        components.add(component);
                        Log.d(TAG, "Loaded component: " + displayName + " (" + component.getFormattedSize() + ")");
                    }
                } else {
                    error = "HTTP Error: " + responseCode;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading components", e);
                error = e.getMessage();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            
            return components;
        }

        @Override
        protected void onPostExecute(List<ComponentInfo> components) {
            if (error != null) {
                callback.onError(error);
            } else {
                callback.onComponentsLoaded(components);
            }
        }
    }

    /**
     * Форматировать имя файла в читаемое название
     */
    private static String formatDisplayName(String fileName) {
        // Убираем расширение
        String name = fileName.replaceAll("\\.(exe|msi|tar\\.gz|zip)$", "");
        
        // Заменяем подчеркивания на пробелы
        name = name.replace("_", " ");
        
        // Делаем первую букву заглавной
        if (!name.isEmpty()) {
            name = name.substring(0, 1).toUpperCase() + name.substring(1);
        }
        
        return name;
    }

    /**
     * Получить описание для типа компонента
     */
    private static String getDescriptionForType(String type, String fileName) {
        switch (type) {
            case "physx":
                return "NVIDIA PhysX Engine - physics simulation library";
            case "vcredist":
                if (fileName.contains("2005")) {
                    return "Visual C++ 2005 Redistributable";
                } else if (fileName.contains("2008")) {
                    return "Visual C++ 2008 Redistributable";
                } else if (fileName.contains("2010")) {
                    return "Visual C++ 2010 Redistributable";
                } else if (fileName.contains("2012")) {
                    return "Visual C++ 2012 Redistributable";
                } else if (fileName.contains("2013")) {
                    return "Visual C++ 2013 Redistributable";
                } else if (fileName.contains("2015") || fileName.contains("2017") || 
                          fileName.contains("2019") || fileName.contains("2022")) {
                    return "Visual C++ 2015-2022 Redistributable";
                }
                return "Visual C++ Runtime Libraries";
            case "mono":
                return "Wine Mono - .NET Framework implementation";
            case "gecko":
                return "Wine Gecko - Internet Explorer engine";
            case "fonts":
                return "CJK Fonts - Chinese, Japanese, Korean fonts";
            case "dxvk":
                return "DXVK - DirectX to Vulkan translation layer";
            case "vkd3d":
                return "VKD3D-Proton - Direct3D 12 to Vulkan";
            case "directx":
                return "DirectX Runtime Libraries";
            default:
                return "Component for Wine/Windows";
        }
    }
}
