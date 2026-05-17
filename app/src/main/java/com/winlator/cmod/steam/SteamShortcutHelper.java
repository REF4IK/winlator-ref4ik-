package com.winlator.cmod.steam;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.winlator.cmod.MainActivity;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SteamShortcutHelper {
    public static final String STEAM_INSTALLER_URL = "https://cdn.cloudflare.steamstatic.com/client/installer/SteamSetup.exe";
    private static final String STEAM_EXE_WIN_PATH = "C:/Program Files (x86)/Steam/steam.exe";
    private static final String STEAM_DIR_WIN_PATH = "C:\\Program Files (x86)\\Steam";
    private static final String STEAM_DESKTOP_DIR = "/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.wine/dosdevices/c:/Program Files (x86)/Steam";
    private static final String WINE_PREFIX = "/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.wine/dosdevices/z:/home/xuser/.wine";
    private static final Pattern SIMPLE_VDF_VALUE = Pattern.compile("\"([^\"]+)\"\\s+\"([^\"]*)\"");
    private static final String[] STEAM_IMAGE_HOSTS = {
            "cdn.cloudflare.steamstatic.com",
            "cdn.akamai.steamstatic.com",
            "steamcdn-a.akamaihd.net"
    };

    private SteamShortcutHelper() {}

    public static final class ImportResult {
        public final int found;
        public final int imported;

        public ImportResult(int found, int imported) {
            this.found = found;
            this.imported = imported;
        }
    }

    private static final class SteamAppInfo {
        final String appId;
        final String name;

        SteamAppInfo(String appId, String name) {
            this.appId = appId;
            this.name = name;
        }
    }

    public static boolean isSteamInstalled(@NonNull Container container) {
        return getSteamExe(container).isFile();
    }

    @NonNull
    public static File getSteamExe(@NonNull Container container) {
        return new File(container.getRootDir(), ".wine/drive_c/Program Files (x86)/Steam/steam.exe");
    }

    @NonNull
    public static File getSteamDir(@NonNull Container container) {
        return getSteamExe(container).getParentFile();
    }

    @NonNull
    public static File getSteamInstallerTarget(@NonNull Container container) {
        File downloadsDir = new File(container.getRootDir(), ".wine/drive_c/users/" + ImageFs.USER + "/Downloads");
        if (!downloadsDir.exists()) downloadsDir.mkdirs();
        return new File(downloadsDir, "SteamSetup.exe");
    }

    @NonNull
    public static File ensureSteamClientShortcut(@NonNull Container container) {
        File shortcutFile = new File(container.getDesktopDir(), "steam-client.desktop");
        writeShortcutFile(shortcutFile, "Steam", STEAM_EXE_WIN_PATH, "", "steam.exe");
        return shortcutFile;
    }

    @NonNull
    public static File createInstallerShortcut(@NonNull Container container, @NonNull File installerFile) {
        String relativeWindowsPath = "C:/users/" + ImageFs.USER + "/Downloads/" + installerFile.getName();
        File shortcutFile = new File(container.getDesktopDir(), "steam-setup.desktop");
        writeShortcutFile(shortcutFile, "Steam Setup", relativeWindowsPath, "", "steamsetup.exe");
        return shortcutFile;
    }

    @NonNull
    public static ImportResult importInstalledGames(@NonNull Context context, @NonNull Container container) {
        File desktopDir = container.getDesktopDir();
        if (!desktopDir.exists()) desktopDir.mkdirs();

        List<File> manifestFiles = getSteamManifestFiles(container);
        int found = 0;
        int imported = 0;

        for (File manifest : manifestFiles) {
            SteamAppInfo appInfo = parseAppManifest(manifest);
            if (appInfo == null) continue;

            found++;
            File shortcutFile = new File(desktopDir, "steam-app-" + appInfo.appId + ".desktop");
            String execArgs = "-silent -vgui -tcp -nobigpicture -nofriendsui -nochatui -nointro -applaunch " + appInfo.appId;
            writeShortcutFile(shortcutFile, appInfo.name, STEAM_EXE_WIN_PATH, execArgs, "steam.exe");

            Shortcut shortcut = new Shortcut(container, shortcutFile);
            shortcut.putExtra("steamAppId", appInfo.appId);
            shortcut.putExtra("gameSource", "STEAM");
            shortcut.saveData();

            maybeDownloadCoverArt(container, shortcut, appInfo.appId);
            imported++;
        }

        return new ImportResult(found, imported);
    }

    private static void writeShortcutFile(@NonNull File shortcutFile, @NonNull String name,
                                          @NonNull String executableWinPath, @NonNull String execArgs,
                                          @NonNull String wmClass) {
        if (!shortcutFile.getParentFile().exists()) shortcutFile.getParentFile().mkdirs();

        StringBuilder content = new StringBuilder();
        content.append("[Desktop Entry]\n");
        content.append("Name=").append(name).append("\n");
        content.append("Exec=env WINEPREFIX=\"").append(WINE_PREFIX).append("\" wine ").append(executableWinPath).append("\n");
        content.append("Type=Application\n");
        content.append("StartupNotify=true\n");
        content.append("Path=").append(STEAM_DESKTOP_DIR).append("\n");
        content.append("StartupWMClass=").append(wmClass).append("\n");

        if (!execArgs.isEmpty()) {
            content.append("\n[Extra Data]\n");
            content.append("execArgs=").append(execArgs).append("\n");
        }

        FileUtils.writeString(shortcutFile, content.toString());
    }

    @NonNull
    private static List<File> getSteamManifestFiles(@NonNull Container container) {
        Set<String> manifestPaths = new LinkedHashSet<>();
        File steamAppsDir = new File(getSteamDir(container), "steamapps");
        if (steamAppsDir.isDirectory()) manifestPaths.add(steamAppsDir.getAbsolutePath());

        File libraryFolders = new File(steamAppsDir, "libraryfolders.vdf");
        if (libraryFolders.isFile()) {
            for (String path : extractLibraryPaths(libraryFolders)) {
                File resolved = resolveWindowsPath(container, path);
                if (resolved == null) continue;
                File candidate = new File(resolved, "steamapps");
                if (candidate.isDirectory()) manifestPaths.add(candidate.getAbsolutePath());
            }
        }

        List<File> manifests = new ArrayList<>();
        for (String path : manifestPaths) {
            File[] files = new File(path).listFiles((dir, name) -> name.startsWith("appmanifest_") && name.endsWith(".acf"));
            if (files == null) continue;
            for (File file : files) manifests.add(file);
        }
        return manifests;
    }

    @NonNull
    private static List<String> extractLibraryPaths(@NonNull File libraryFolders) {
        List<String> paths = new ArrayList<>();
        for (String line : FileUtils.readLines(libraryFolders)) {
            Matcher matcher = SIMPLE_VDF_VALUE.matcher(line.trim());
            if (!matcher.find()) continue;
            if (!"path".equals(matcher.group(1))) continue;
            paths.add(matcher.group(2).replace("\\\\", "\\"));
        }
        return paths;
    }

    @Nullable
    private static SteamAppInfo parseAppManifest(@NonNull File manifest) {
        String appId = null;
        String name = null;

        for (String line : FileUtils.readLines(manifest)) {
            Matcher matcher = SIMPLE_VDF_VALUE.matcher(line.trim());
            if (!matcher.find()) continue;

            String key = matcher.group(1);
            String value = matcher.group(2);
            if ("appid".equals(key)) appId = value;
            if ("name".equals(key)) name = value;
        }

        if (appId == null || appId.isEmpty() || name == null || name.isEmpty()) return null;
        return new SteamAppInfo(appId, name);
    }

    @Nullable
    private static File resolveWindowsPath(@NonNull Container container, @NonNull String windowsPath) {
        String normalized = windowsPath.replace("\\\\", "\\").replace("/", "\\");
        if (normalized.length() < 2 || normalized.charAt(1) != ':') return null;

        char driveLetter = Character.toUpperCase(normalized.charAt(0));
        String relativePath = normalized.substring(2).replace("\\", "/");
        while (relativePath.startsWith("/")) relativePath = relativePath.substring(1);

        if (driveLetter == 'C') {
            return relativePath.isEmpty()
                    ? new File(container.getRootDir(), ".wine/drive_c")
                    : new File(container.getRootDir(), ".wine/drive_c/" + relativePath);
        }

        for (String[] mapping : container.drivesIterator()) {
            if (mapping[0] == null || mapping[0].isEmpty()) continue;
            if (Character.toUpperCase(mapping[0].charAt(0)) != driveLetter) continue;
            return relativePath.isEmpty() ? new File(mapping[1]) : new File(mapping[1], relativePath);
        }
        return null;
    }

    private static void maybeDownloadCoverArt(@NonNull Container container, @NonNull Shortcut shortcut, @NonNull String appId) {
        File coverDir = new File(container.getRootDir(), "app_data/cover_arts");
        if (!coverDir.exists()) coverDir.mkdirs();

        File coverFile = new File(coverDir, "steam_" + appId + ".jpg");
        if (!coverFile.isFile()) {
            for (String url : buildCoverArtUrls(appId)) {
                if (Downloader.downloadFile(url, coverFile)) break;
                if (coverFile.exists()) coverFile.delete();
            }
        }

        if (coverFile.isFile()) shortcut.setCustomCoverArtPath(coverFile.getPath());
    }

    @NonNull
    private static List<String> buildCoverArtUrls(@NonNull String appId) {
        List<String> urls = new ArrayList<>();
        for (String host : STEAM_IMAGE_HOSTS) {
            urls.add(String.format(Locale.ENGLISH, "https://%s/steam/apps/%s/library_600x900_2x.jpg", host, appId));
            urls.add(String.format(Locale.ENGLISH, "https://%s/steam/apps/%s/library_600x900.jpg", host, appId));
            urls.add(String.format(Locale.ENGLISH, "https://%s/steam/apps/%s/header.jpg", host, appId));
        }
        return urls;
    }
}
