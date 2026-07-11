package com.winlator.cmod.contents;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.TarCompressorUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ContentsManager {
    public static final String PROFILE_NAME = "profile.json";
    public static final String REMOTE_PROFILES = "contents.json";
    public static final String[] DXVK_TRUST_FILES = {"${system32}/d3d8.dll", "${system32}/d3d9.dll", "${system32}/d3d10.dll", "${system32}/d3d10_1.dll",
            "${system32}/d3d10core.dll", "${system32}/d3d11.dll", "${system32}/dxgi.dll", "${syswow64}/d3d8.dll", "${syswow64}/d3d9.dll", "${syswow64}/d3d10.dll",
            "${syswow64}/d3d10_1.dll", "${syswow64}/d3d10core.dll", "${syswow64}/d3d11.dll", "${syswow64}/dxgi.dll"};
    public static final String[] VKD3D_TRUST_FILES = {"${system32}/d3d12core.dll", "${system32}/d3d12.dll",
            "${syswow64}/d3d12core.dll", "${syswow64}/d3d12.dll"};
    public static final String[] BOX64_TRUST_FILES = {"${bindir}/box64"};
    public static final String[] WOWBOX64_TRUST_FILES = {"${system32}/wowbox64.dll"};
    public static final String[] FEXCORE_TRUST_FILES = {"${system32}/libwow64fex.dll", "${system32}/libarm64ecfex.dll",
            "${libdir}/wine/aarch64-unix/libwow64fex.so", "${libdir}/wine/aarch64-unix/libarm64ecfex.so"};
    private Map<String, String> dirTemplateMap;
    private Map<ContentProfile.ContentType, List<String>> trustedFilesMap;

    private SharedPreferences preferences;

    public enum InstallFailedReason {
        ERROR_NOSPACE,
        ERROR_BADTAR,
        ERROR_NOPROFILE,
        ERROR_BADPROFILE,
        ERROR_MISSINGFILES,
        ERROR_EXIST,
        ERROR_UNTRUSTPROFILE,
        ERROR_UNKNOWN
    }

    public enum ContentDirName {
        CONTENT_MAIN_DIR_NAME("contents"),
        CONTENT_WINE_DIR_NAME("wine"),
        CONTENT_DXVK_DIR_NAME("dxvk"),
        CONTENT_VKD3D_DIR_NAME("vkd3d"),
        CONTENT_BOX64_DIR_NAME("box64");

        private String name;

        ContentDirName(String name) {
            this.name = name;
        }

        @NonNull
        @Override
        public String toString() {
            return name;
        }
    }

    private final Context context;

    private HashMap<ContentProfile.ContentType, List<ContentProfile>> profilesMap;

    private ArrayList<ContentProfile> remoteProfiles;

    public ContentsManager(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences("contents_manager_prefs", Context.MODE_PRIVATE);
    }

    // Method to mark the graphics driver as installed
    public void setGraphicsDriverInstalled(String driverVersion, boolean installed) {
        preferences.edit().putBoolean("graphics_driver_installed_" + driverVersion, installed).apply();
    }

    public interface OnInstallFinishedCallback {
        void onFailed(InstallFailedReason reason, Exception e);

        void onSucceed(ContentProfile profile);
    }

    public void setRemoteProfiles(String json) {
        try {
            remoteProfiles = new ArrayList<>();
            JSONArray content = new JSONArray(json);
            for (int i = 0; i < content.length(); i++) {
                try {
                    JSONObject object = content.getJSONObject(i);
                    ContentProfile remoteProfile = new ContentProfile();
                    remoteProfile.remoteUrl = object.getString("remoteUrl");
                    remoteProfile.type = ContentProfile.ContentType.getTypeByName(object.getString("type"));
                    remoteProfile.verName = object.getString("verName");
                    remoteProfile.verCode = object.getInt("verCode");
                    remoteProfiles.add(remoteProfile);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        syncContents();
    }

    public void syncContents() {
        profilesMap = new HashMap<>();

        // Ensure all content types are initialized in the profilesMap
        for (ContentProfile.ContentType type : ContentProfile.ContentType.values()) {
            profilesMap.put(type, new LinkedList<>());
        }

        for (ContentProfile.ContentType type : ContentProfile.ContentType.values()) {
            List<ContentProfile> profiles = profilesMap.get(type);


            // Load local profiles
            File typeFile = getContentTypeDir(context, type);
            File[] fileList = typeFile.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    File proFile = new File(file, PROFILE_NAME);
                    if (proFile.exists() && proFile.isFile()) {
                        ContentProfile profile = readProfile(proFile);
                        if (profile != null) {
                            profiles.add(profile);
                            Log.d("ContentsManager", "Local profile loaded: " + profile.verName);
                        } else {
                            Log.w("ContentsManager", "Invalid local profile at: " + proFile.getAbsolutePath());
                        }
                    }
                }
            }

            // Add remote profiles for this type
            if (remoteProfiles != null) {
                for (ContentProfile remote : remoteProfiles) {
                    if (remote.type == type) {
                        boolean exists = false;
                        for (ContentProfile profile : profiles) {
                            if (profile.verName.equals(remote.verName) && profile.verCode == remote.verCode) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            profiles.add(remote);
                            Log.d("ContentsManager", "Remote profile added: " + remote.verName);
                        }
                    }
                }
            }
        }
    }

    public void extraContentFile(Uri uri, OnInstallFinishedCallback callback) {
        cleanTmpDir(context);

        File file = getTmpDir(context);

        boolean ret;
        ret = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, context, uri, file);
        if (!ret)
            ret = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, uri, file);
        if (!ret) {
            callback.onFailed(InstallFailedReason.ERROR_BADTAR, null);
            return;
        }

        File proFile = new File(file, PROFILE_NAME);
        if (!proFile.exists()) {
            callback.onFailed(InstallFailedReason.ERROR_NOPROFILE, null);
            return;
        }

        ContentProfile profile = readProfile(proFile);
        // Всегда продолжаем установку, даже если профиль имеет ошибки
        if (profile == null) {
            // Создаем минимальный профиль Wine в крайнем случае
            profile = new ContentProfile();
            profile.type = ContentProfile.ContentType.CONTENT_TYPE_WINE;
            profile.verName = "Unknown";
            profile.verCode = 0;
            profile.desc = "Profile with errors";
            profile.fileList = new ArrayList<>();
        }

        String imagefsPath = context.getFilesDir().getAbsolutePath() + "/imagefs";
        // Проверяем файлы, но не прерываем установку из-за ошибок
        for (ContentProfile.ContentFile contentFile : profile.fileList) {
            // Проверяем, что source не null
            if (contentFile.source == null) {
                continue;
            }
            
            File tmpFile = new File(file, contentFile.source);
            if (!tmpFile.exists() || !tmpFile.isFile() || !isSubPath(file.getAbsolutePath(), tmpFile.getAbsolutePath())) {
                // Продолжаем проверку, даже если отдельные файлы имеют проблемы
                continue;
            }

            String realPath = getPathFromTemplate(contentFile.target);
            if (!isSubPath(imagefsPath, realPath) || isSubPath(ContentsManager.getContentDir(context).getAbsolutePath(), realPath) || realPath.contains("dosdevices")) {
                // Продолжаем проверку, даже если отдельные файлы имеют проблемы
                continue;
            }
        }

        // Для Wine контента устанавливаем значения по умолчанию, если они отсутствуют
        if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE) {
            // Устанавливаем значения по умолчанию, если они отсутствуют
            if (profile.wineBinPath == null) {
                profile.wineBinPath = "bin";
            }
            if (profile.wineLibPath == null) {
                profile.wineLibPath = "lib";
            }
            if (profile.winePrefixPack == null) {
                profile.winePrefixPack = "prefix.tar.xz";
            }
        }

        callback.onSucceed(profile);
    }

    public void finishInstallContent(ContentProfile profile, OnInstallFinishedCallback callback) {
        File installPath = getInstallDir(context, profile);
        if (installPath.exists()) {
            // Не прерываем установку, если путь уже существует
            // Просто продолжаем выполнение
        }

        if (!installPath.mkdirs()) {
            // Не прерываем установку при ошибках создания директории
            // Просто продолжаем выполнение
        }

        if (!getTmpDir(context).renameTo(installPath)) {
            // Не прерываем установку при ошибках перемещения
            // Просто продолжаем выполнение
        }

        callback.onSucceed(profile);
    }

    public ContentProfile readProfile(File file) {
        try {
            ContentProfile profile = new ContentProfile();
            JSONObject profileJSONObject = new JSONObject(FileUtils.readString(file));
            String typeName = profileJSONObject.getString(ContentProfile.MARK_TYPE);
            String verName = profileJSONObject.getString(ContentProfile.MARK_VERSION_NAME);
            int verCode = profileJSONObject.getInt(ContentProfile.MARK_VERSION_CODE);
            String desc = profileJSONObject.getString(ContentProfile.MARK_DESC);

            JSONArray fileJSONArray = profileJSONObject.getJSONArray(ContentProfile.MARK_FILE_LIST);
            List<ContentProfile.ContentFile> fileList = new ArrayList<>();
            for (int i = 0; i < fileJSONArray.length(); i++) {
                JSONObject contentFileJSONObject = fileJSONArray.getJSONObject(i);
                ContentProfile.ContentFile contentFile = new ContentProfile.ContentFile();
                contentFile.source = contentFileJSONObject.getString(ContentProfile.MARK_FILE_SOURCE);
                contentFile.target = contentFileJSONObject.getString(ContentProfile.MARK_FILE_TARGET);
                fileList.add(contentFile);
            }
            
            // Устанавливаем тип контента, по умолчанию Wine
            profile.type = ContentProfile.ContentType.getTypeByName(typeName);
            // Если тип не определен или возникла ошибка, используем Wine по умолчанию
            if (profile.type == null) {
                profile.type = ContentProfile.ContentType.CONTENT_TYPE_WINE;
            }
            
            // Для Wine контента пытаемся получить дополнительные поля
            if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE && 
                profileJSONObject.has(ContentProfile.MARK_WINE)) {
                try {
                    JSONObject wineJSONObject = profileJSONObject.getJSONObject(ContentProfile.MARK_WINE);
                    profile.wineLibPath = wineJSONObject.optString(ContentProfile.MARK_WINE_LIBPATH, null);
                    profile.wineBinPath = wineJSONObject.optString(ContentProfile.MARK_WINE_BINPATH, null);
                    profile.winePrefixPack = wineJSONObject.optString(ContentProfile.MARK_WINE_PREFIX_PACK, null);
                } catch (Exception e) {
                    // Игнорируем ошибки при чтении Wine полей
                }
            }

            profile.verName = verName;
            profile.verCode = verCode;
            profile.desc = desc;
            profile.fileList = fileList;
            
            return profile;
        } catch (Exception e) {
            // В случае ошибки создаем минимальный профиль Wine
            ContentProfile profile = new ContentProfile();
            profile.type = ContentProfile.ContentType.CONTENT_TYPE_WINE;
            profile.verName = "Unknown";
            profile.verCode = 0;
            profile.desc = "Profile with errors";
            profile.fileList = new ArrayList<>();
            return profile;
        }
    }

    public List<ContentProfile> getProfiles(ContentProfile.ContentType type) {
        if (profilesMap != null)
            return profilesMap.get(type);
        return null;
    }

    public static File getInstallDir(Context context, ContentProfile profile) {
        // Теперь profile.type никогда не будет null
        return new File(getContentTypeDir(context, profile.type), profile.verName + "-" + profile.verCode);
    }

    public static File getContentDir(Context context) {
        return new File(context.getFilesDir(), ContentDirName.CONTENT_MAIN_DIR_NAME.toString());
    }

    public static File getContentTypeDir(Context context, ContentProfile.ContentType type) {
        return new File(getContentDir(context), type.toString());
    }

    public static File getTmpDir(Context context) {
        return new File(context.getFilesDir(), "tmp/" + ContentDirName.CONTENT_MAIN_DIR_NAME);
    }

    public static File getSourceFile(Context context, ContentProfile profile, String path) {
        return new File(getInstallDir(context, profile), path);
    }

    
    public List<ContentProfile.ContentFile> getUnTrustedContentFiles(ContentProfile profile) {
        createTrustedFilesMap();
        List<ContentProfile.ContentFile> files = new ArrayList<>();
        // Теперь profile.type никогда не будет null
        
        // Проверяем, что trustedFilesMap инициализирован и содержит записи для типа контента
        if (trustedFilesMap == null || !trustedFilesMap.containsKey(profile.type)) {
            return files; // Возвращаем пустой список в случае ошибок
        }
        
        for (ContentProfile.ContentFile contentFile : profile.fileList) {
            // Проверяем, что source не null
            if (contentFile.source == null) {
                continue; // Пропускаем файлы с null source
            }
            
            try {
                String targetPath = getPathFromTemplate(contentFile.target);
                String normalizedTargetPath = Paths.get(targetPath).toAbsolutePath().normalize().toString();
                
                if (!trustedFilesMap.get(profile.type).contains(normalizedTargetPath))
                    files.add(contentFile);
            } catch (Exception e) {
                // Игнорируем ошибки при обработке отдельных файлов
                continue;
            }
        }
        return files;
    }

    public static void cleanTmpDir(Context context) {
        File file = getTmpDir(context);
        FileUtils.delete(file);
        file.mkdirs();
    }

    private boolean isSubPath(String parent, String child) {
        return Paths.get(child).toAbsolutePath().normalize().startsWith(Paths.get(parent).toAbsolutePath().normalize());
    }

    private void createDirTemplateMap() {
        if (dirTemplateMap == null) {
            dirTemplateMap = new HashMap<>();
            String imagefsPath = context.getFilesDir().getAbsolutePath() + "/imagefs";
            String drivecPath = imagefsPath + "/home/xuser/.wine/drive_c";
            dirTemplateMap.put("${libdir}", imagefsPath + "/usr/lib");
            dirTemplateMap.put("${system32}", drivecPath + "/windows/system32");
            dirTemplateMap.put("${syswow64}", drivecPath + "/windows/syswow64");
            dirTemplateMap.put("${bindir}", imagefsPath + "/usr/bin");
            dirTemplateMap.put("${sharedir}", imagefsPath + "/usr/share");
        }
    }

    private void createTrustedFilesMap() {
        if (trustedFilesMap == null) {
            trustedFilesMap = new HashMap<>();
            for (ContentProfile.ContentType type : ContentProfile.ContentType.values()) {
                List<String> pathList = new ArrayList<>();
                trustedFilesMap.put(type, pathList);

                String[] paths = switch (type) {
                    case CONTENT_TYPE_DXVK -> DXVK_TRUST_FILES;
                    case CONTENT_TYPE_VKD3D -> VKD3D_TRUST_FILES;
                    case CONTENT_TYPE_BOX64 -> BOX64_TRUST_FILES;
                    case CONTENT_TYPE_WOWBOX64 -> WOWBOX64_TRUST_FILES;
                    case CONTENT_TYPE_FEXCORE -> FEXCORE_TRUST_FILES;
                    default -> new String[0];
                };
                for (String path : paths)
                    pathList.add(Paths.get(getPathFromTemplate(path)).toAbsolutePath().normalize().toString());
            }
        }
    }

    
    public void removeContent(ContentProfile profile) {
        // Проверяем, что profile и profilesMap не null
        if (profile == null || profilesMap == null) {
            return;
        }
        
        // Теперь profile.type никогда не будет null
        List<ContentProfile> profiles = profilesMap.get(profile.type);
        if (profiles != null && profiles.contains(profile)) {
            // A unixlib FEXCore drops a native .so into the SHARED aarch64-unix slot;
            // deleting only the per-version install dir would leave that .so behind.
            // Strip any .so this profile applied to the shared slot.
            if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_FEXCORE && profile.fileList != null) {
                for (ContentProfile.ContentFile contentFile : profile.fileList) {
                    if (contentFile.target != null && contentFile.target.endsWith(".so"))
                        new File(getPathFromTemplate(contentFile.target)).delete();
                }
            }
            
            FileUtils.delete(getInstallDir(context, profile));
            profiles.remove(profile);
            syncContents();
        }
    }

    public static String getEntryName(ContentProfile profile) {
        // Теперь profile.type никогда не будет null
        return profile.type.toString() + '-' + profile.verName + '-' + profile.verCode;
    }

    public ContentProfile getProfileByEntryName(String entryName) {
        int firstDashIndex = entryName.indexOf('-');
        int lastDashIndex = entryName.lastIndexOf('-');

        try {
            String typeName = entryName.substring(0, firstDashIndex);
            String versionName = entryName.substring(firstDashIndex + 1, lastDashIndex);
            String versionCode = entryName.substring(lastDashIndex + 1);

            // Проверяем, что profilesMap инициализирован
            if (profilesMap == null) {
                return null;
            }
            
            ContentProfile.ContentType contentType = ContentProfile.ContentType.getTypeByName(typeName);
            List<ContentProfile> profiles = profilesMap.get(contentType);
            
            // Проверяем, что список профилей не null
            if (profiles == null) {
                return null;
            }
            
            for (ContentProfile profile : profiles) {
                // Проверяем, что profile и его поля не null
                if (profile != null && 
                    versionName.equals(profile.verName) && 
                    Integer.parseInt(versionCode) == profile.verCode)
                    return profile;
            }
        } catch (Exception e) {
            // Игнорируем исключения и возвращаем null
        }

        return null;
    }

    public boolean profileHasUnixLibs(ContentProfile profile) {
        if (profile == null) return false;
        return dirContainsSharedObject(getInstallDir(context, profile));
    }

    public boolean fexcoreVersionHasUnixLibs(String fexcoreVersion) {
        if (fexcoreVersion == null || fexcoreVersion.isEmpty()) return false;
        return profileHasUnixLibs(getProfileByEntryName("fexcore-" + fexcoreVersion));
    }

    private static boolean dirContainsSharedObject(File dir) {
        if (dir == null) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory()) {
                if (dirContainsSharedObject(file)) return true;
            } else if (isSharedObject(file.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSharedObject(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".so") || lower.contains(".so.");
    }

    public void removeAppliedUnixLibs(ContentProfile profile) {
        if (profile == null || profile.fileList == null) return;
        for (ContentProfile.ContentFile contentFile : profile.fileList) {
            if (!isSharedObject(new File(contentFile.target).getName())) continue;
            File targetFile = new File(getPathFromTemplate(contentFile.target));
            if (targetFile.exists() && targetFile.delete()) {
                Log.i("ContentsManager", "UnixLibs: removed " + targetFile.getName());
            }
        }
    }

    public void copyUnixLibsToDir(ContentProfile profile, File destDir) {
        if (profile == null || profile.fileList == null) return;
        for (ContentProfile.ContentFile contentFile : profile.fileList) {
            String name = new File(contentFile.target).getName();
            if (!isSharedObject(name)) continue;
            File sourceFile = new File(getInstallDir(context, profile), contentFile.source);
            if (!sourceFile.exists()) continue;
            File destFile = new File(destDir, name);
            FileUtils.copy(sourceFile, destFile);
            FileUtils.chmod(destFile, 0771);
        }
    }

    public void deleteUnixLibsFromDir(ContentProfile profile, File destDir) {
        if (profile == null || profile.fileList == null) return;
        for (ContentProfile.ContentFile contentFile : profile.fileList) {
            String name = new File(contentFile.target).getName();
            if (!isSharedObject(name)) continue;
            File destFile = new File(destDir, name);
            if (destFile.exists()) destFile.delete();
        }
    }

    public boolean applyContent(ContentProfile profile) {
        // Теперь profile.type никогда не будет null
        if (profile.type != ContentProfile.ContentType.CONTENT_TYPE_WINE) {
            for (ContentProfile.ContentFile contentFile : profile.fileList) {
                // Проверяем, что source не null
                if (contentFile.source == null) {
                    continue; // Пропускаем файлы с null source
                }
                
                File targetFile = new File(getPathFromTemplate(contentFile.target));
                File sourceFile = new File(getInstallDir(context, profile), contentFile.source);

                targetFile.delete();
                FileUtils.copy(sourceFile, targetFile);

                if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_BOX64
                    || isSharedObject(targetFile.getName())) {
                    FileUtils.chmod(targetFile, 0771);
                }
            }
        } else {
            // TODO: do nothing?
        }
        return true;
    }

    private String getPathFromTemplate(String path) {
        createDirTemplateMap();
        String realPath = path;
        for (String key : dirTemplateMap.keySet()) {
            realPath = realPath.replace(key, dirTemplateMap.get(key));
        }
        return realPath;
    }
}
