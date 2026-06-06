package com.winlator.cmod.inputcontrols;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Manager class for handling icon packs.
 * Icon packs are folders containing PNG icons.
 * Each pack can be loaded as a collection of icons.
 */
public class IconPackManager {
    private static final String TAG = "IconPackManager";
    private static final String PACKS_DIR = "icon_packs";
    private static final String PACK_INFO_FILE = "pack.json";
    private static final String ICON_EXTENSION = ".png";

    // Pack ID offset to avoid conflicts with built-in icons (similar to custom icons)
    public static final int PACK_ICON_ID_OFFSET = 1000;
    public static final int MAX_PACK_ICONS = 10000;

    private final Context context;
    private final File packsDir;

    public IconPackManager(Context context) {
        this.context = context;
        this.packsDir = new File(context.getFilesDir(), PACKS_DIR);
        if (!packsDir.exists()) {
            packsDir.mkdirs();
        }
    }

    /**
     * Gets the root directory for icon packs
     */
    public File getPacksDir() {
        return packsDir;
    }

    /**
     * Lists all icon packs available
     */
    public List<IconPack> getIconPacks() {
        List<IconPack> packs = new ArrayList<>();
        if (!packsDir.exists()) return packs;

        File[] packFolders = packsDir.listFiles(File::isDirectory);
        if (packFolders == null) return packs;

        Arrays.sort(packFolders, (a, b) -> a.getName().compareTo(b.getName()));

        for (File packFolder : packFolders) {
            IconPack pack = loadPackInfo(packFolder);
            if (pack != null) {
                packs.add(pack);
            }
        }
        return packs;
    }

    /**
     * Gets a specific icon pack by name
     */
    public IconPack getIconPack(String name) {
        File packFolder = new File(packsDir, name);
        if (!packFolder.exists() || !packFolder.isDirectory()) return null;
        return loadPackInfo(packFolder);
    }

    private IconPack loadPackInfo(File packFolder) {
        String name = packFolder.getName();
        File[] iconFiles = packFolder.listFiles((dir, n) -> n.toLowerCase().endsWith(ICON_EXTENSION));
        int iconCount = iconFiles != null ? iconFiles.length : 0;

        // Generate ID based on folder name hash
        int id = name.hashCode();

        // Get preview icon (first icon)
        Bitmap preview = null;
        if (iconFiles != null && iconFiles.length > 0) {
            File firstIcon = iconFiles[0];
            preview = loadIconFromFile(firstIcon);
        }

        return new IconPack(id, name, packFolder, iconCount, preview);
    }

    /**
     * Loads a Bitmap from a file
     */
    public Bitmap loadIconFromFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return BitmapFactory.decodeStream(fis);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load icon from file: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    /**
     * Imports an icon pack from a ZIP archive.
     * The archive can contain:
     * - A folder with PNG files (and optional pack.json)
     * - PNG files directly at root (will be wrapped in a folder)
     */
    public IconPack importIconPackFromZip(Uri zipUri) {
        try {
            String packName = generateUniquePackName();
            File packFolder = new File(packsDir, packName);
            packFolder.mkdirs();

            InputStream inputStream = context.getContentResolver().openInputStream(zipUri);
            if (inputStream == null) return null;

            try (ZipInputStream zis = new ZipInputStream(inputStream)) {
                ZipEntry entry;
                boolean hasFiles = false;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    String entryName = entry.getName();
                    if (!entryName.toLowerCase().endsWith(ICON_EXTENSION)) continue;

                    // Strip folder prefix from entry name
                    String fileName = entryName;
                    if (fileName.contains("/")) {
                        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
                    }

                    File outFile = new File(packFolder, fileName);
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    hasFiles = true;
                }

                if (!hasFiles) {
                    packFolder.delete();
                    return null;
                }
            }

            return loadPackInfo(packFolder);
        } catch (IOException e) {
            Log.e(TAG, "Failed to import icon pack from zip", e);
            return null;
        }
    }

    /**
     * Imports an icon pack from a folder of files (used for already-extracted packs)
     */
    public IconPack importIconPackFromFolder(File sourceFolder) {
        if (!sourceFolder.isDirectory()) return null;

        String packName = generateUniquePackName();
        File packFolder = new File(packsDir, packName);
        packFolder.mkdirs();

        File[] files = sourceFolder.listFiles((dir, n) -> n.toLowerCase().endsWith(ICON_EXTENSION));
        if (files == null || files.length == 0) {
            packFolder.delete();
            return null;
        }

        for (File f : files) {
            try (FileInputStream fis = new FileInputStream(f);
                 FileOutputStream fos = new FileOutputStream(new File(packFolder, f.getName()))) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy icon: " + f.getName(), e);
            }
        }

        return loadPackInfo(packFolder);
    }

    /**
     * Imports a single icon as a new pack
     */
    public IconPack importIconAsPack(Uri iconUri) {
        try {
            String packName = generateUniquePackName();
            File packFolder = new File(packsDir, packName);
            packFolder.mkdirs();

            InputStream inputStream = context.getContentResolver().openInputStream(iconUri);
            if (inputStream == null) return null;

            File outFile = new File(packFolder, "icon" + ICON_EXTENSION);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
            }
            inputStream.close();

            return loadPackInfo(packFolder);
        } catch (IOException e) {
            Log.e(TAG, "Failed to import icon as pack", e);
            return null;
        }
    }

    /**
     * Deletes an icon pack
     */
    public boolean deleteIconPack(IconPack pack) {
        return deleteRecursive(pack.folder);
    }

    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    /**
     * Renames an icon pack
     */
    public boolean renameIconPack(IconPack pack, String newName) {
        File newFolder = new File(packsDir, newName);
        if (newFolder.exists()) return false;
        return pack.folder.renameTo(newFolder);
    }

    private String generateUniquePackName() {
        long timestamp = System.currentTimeMillis();
        return "pack_" + timestamp;
    }

    /**
     * Computes the global icon ID for a pack icon (uses pack ID + index)
     * Used so icons from packs can be referenced via ControlElement.setIconId()
     */
    public static int getPackIconId(int packId, int index) {
        return PACK_ICON_ID_OFFSET + (Math.abs(packId) % 1000) * 100 + index;
    }

    /**
     * Loads an icon by its global ID (>= PACK_ICON_ID_OFFSET)
     * Returns the Bitmap for the icon at the given position in the matching pack,
     * or null if no pack matches the ID.
     */
    public Bitmap loadIconByGlobalId(int globalId) {
        if (globalId < PACK_ICON_ID_OFFSET) return null;
        int relId = globalId - PACK_ICON_ID_OFFSET;
        int index = relId % 100;
        int packHash = relId / 100;
        // Try all packs and find the one whose name.hashCode() mod 1000 matches
        List<IconPack> all = getIconPacks();
        for (IconPack p : all) {
            if (Math.abs(p.id) % 1000 == packHash) {
                if (index < p.iconCount) {
                    return p.loadIcon(index);
                }
            }
        }
        return null;
    }

    /**
     * Checks if a given icon ID is a pack icon (ID >= PACK_ICON_ID_OFFSET)
     */
    public static boolean isPackIcon(int iconId) {
        return iconId >= PACK_ICON_ID_OFFSET;
    }

    /**
     * Represents a single icon pack
     */
    public static class IconPack {
        public final int id;
        public final String name;
        public final File folder;
        public final int iconCount;
        public final Bitmap preview;

        public IconPack(int id, String name, File folder, int iconCount, Bitmap preview) {
            this.id = id;
            this.name = name;
            this.folder = folder;
            this.iconCount = iconCount;
            this.preview = preview;
        }

        /**
         * Gets the list of icon files in this pack
         */
        public List<File> getIconFiles() {
            File[] files = folder.listFiles((dir, n) -> n.toLowerCase().endsWith(ICON_EXTENSION));
            if (files == null) return Collections.emptyList();
            List<File> result = new ArrayList<>(Arrays.asList(files));
            Collections.sort(result, (a, b) -> a.getName().compareTo(b.getName()));
            return result;
        }

        public File getIconFile(int index) {
            List<File> files = getIconFiles();
            if (index < 0 || index >= files.size()) return null;
            return files.get(index);
        }

        public Bitmap loadIcon(int index) {
            File f = getIconFile(index);
            if (f == null) return null;
            try (FileInputStream fis = new FileInputStream(f)) {
                return BitmapFactory.decodeStream(fis);
            } catch (IOException e) {
                Log.e(TAG, "Failed to load icon at index " + index, e);
                return null;
            }
        }
    }
}
