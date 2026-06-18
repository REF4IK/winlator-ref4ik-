package com.winlator.cmod.inputcontrols;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Manager class for handling custom user icons in the input controls editor.
 * Manages saving, loading, and deletion of user-uploaded icon images.
 */
public class CustomIconManager {
    private static final String TAG = "CustomIconManager";
    private static final String CUSTOM_ICONS_DIR = "custom_icons";
    private static final String ICON_PREFIX = "custom_";
    private static final String ICON_EXTENSION = ".png";
    private static final int MAX_ICON_SIZE = 128; // Maximum dimension in pixels
    private static final int QUALITY = 90; // PNG compression quality
    
    // Offset for custom icon IDs to avoid conflicts with built-in icons
    public static final int CUSTOM_ICON_ID_OFFSET = 100;
    public static final int PACK_ICON_ID_OFFSET = 1000;
    public static final int MAX_CUSTOM_ICONS = 1000; // Максимум кастомных иконок
    
    private final Context context;
    private final File customIconsDir;
    
    public CustomIconManager(Context context) {
        this.context = context;
        this.customIconsDir = new File(context.getFilesDir(), CUSTOM_ICONS_DIR);
        if (!customIconsDir.exists()) {
            customIconsDir.mkdirs();
        }
    }
    
    /**
     * Imports an icon from a URI and saves it to internal storage
     * @param imageUri URI of the image to import
     * @return The ID of the imported icon, or -1 if import failed
     */
    public int importIcon(Uri imageUri) {
        try {
            // Load and resize the original bitmap
            Bitmap originalBitmap = BitmapFactory.decodeStream(
                context.getContentResolver().openInputStream(imageUri)
            );
            
            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode image from URI: " + imageUri);
                return -1;
            }
            
            // Resize to max size while maintaining aspect ratio
            Bitmap resizedBitmap = resizeBitmap(originalBitmap, MAX_ICON_SIZE);
            originalBitmap.recycle(); // Free memory
            
            // Generate a unique ID for this icon
            int iconId = getNextAvailableId();
            if (iconId == -1) {
                Log.e(TAG, "No available ID for new custom icon");
                resizedBitmap.recycle();
                return -1;
            }
            
            // Save the bitmap to internal storage
            File iconFile = new File(customIconsDir, ICON_PREFIX + iconId + ICON_EXTENSION);
            try (FileOutputStream fos = new FileOutputStream(iconFile)) {
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, fos);
                fos.flush();
            }
            
            resizedBitmap.recycle();
            Log.i(TAG, "Successfully imported custom icon with ID: " + iconId);
            return iconId;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to import icon", e);
            return -1;
        }
    }
    
    /**
     * Loads a custom icon by its ID
     * @param iconId The ID of the icon to load
     * @return The loaded bitmap, or null if not found
     */
    public Bitmap loadIcon(int iconId) {
        if (!isCustomIcon(iconId)) {
            return null;
        }
        
        File iconFile = new File(customIconsDir, ICON_PREFIX + iconId + ICON_EXTENSION);
        if (!iconFile.exists()) {
            return null;
        }
        
        try (FileInputStream fis = new FileInputStream(iconFile)) {
            return BitmapFactory.decodeStream(fis);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load custom icon: " + iconId, e);
            return null;
        }
    }
    
    /**
     * Deletes a custom icon by its ID
     * @param iconId The ID of the icon to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteIcon(int iconId) {
        if (!isCustomIcon(iconId)) {
            return false;
        }
        
        File iconFile = new File(customIconsDir, ICON_PREFIX + iconId + ICON_EXTENSION);
        if (iconFile.exists()) {
            boolean deleted = iconFile.delete();
            if (deleted) {
                Log.i(TAG, "Successfully deleted custom icon: " + iconId);
            } else {
                Log.e(TAG, "Failed to delete custom icon: " + iconId);
            }
            return deleted;
        }
        return true; // Already deleted
    }
    
    /**
     * Gets a list of all available custom icon IDs
     * @return Array of custom icon IDs
     */
    public int[] getCustomIconIds() {
        if (!customIconsDir.exists()) {
            return new int[0];
        }
        
        File[] iconFiles = customIconsDir.listFiles((dir, name) -> 
            name.startsWith(ICON_PREFIX) && name.endsWith(ICON_EXTENSION)
        );
        
        if (iconFiles == null || iconFiles.length == 0) {
            return new int[0];
        }
        
        List<Integer> iconIds = new ArrayList<>();
        for (File iconFile : iconFiles) {
            try {
                String fileName = iconFile.getName();
                String idStr = fileName.substring(ICON_PREFIX.length(), fileName.lastIndexOf('.'));
                int iconId = Integer.parseInt(idStr);
                iconIds.add(iconId);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid custom icon filename: " + iconFile.getName());
            }
        }
        
        Collections.sort(iconIds);
        int[] result = new int[iconIds.size()];
        for (int i = 0; i < iconIds.size(); i++) {
            result[i] = iconIds.get(i);
        }
        
        return result;
    }
    
    /**
     * Checks if the given ID represents a custom icon
     * @param iconId The icon ID to check
     * @return true if it's a custom icon ID, false otherwise
     */
    public static boolean isCustomIcon(int iconId) {
        return iconId >= CUSTOM_ICON_ID_OFFSET && iconId < PACK_ICON_ID_OFFSET;
    }
    
    /**
     * Gets the number of custom icons currently stored
     * @return Number of custom icons
     */
    public int getCustomIconCount() {
        return getCustomIconIds().length;
    }
    
    /**
     * Imports an icon from a PNG file (e.g., from an icon pack) into custom icons
     * @param iconFile The PNG file to import
     * @return The ID of the imported icon, or -1 if import failed
     */
    public int importIconFromFile(File iconFile) {
        if (!iconFile.exists() || !iconFile.getName().toLowerCase().endsWith(".png")) {
            Log.e(TAG, "Invalid icon file: " + (iconFile != null ? iconFile.getAbsolutePath() : "null"));
            return -1;
        }

        try {
            // Load and resize the bitmap
            Bitmap originalBitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
            if (originalBitmap == null) {
                Log.e(TAG, "Failed to decode image from file: " + iconFile.getAbsolutePath());
                return -1;
            }

            Bitmap resizedBitmap = resizeBitmap(originalBitmap, MAX_ICON_SIZE);
            originalBitmap.recycle();

            // Generate a unique ID for this icon
            int iconId = getNextAvailableId();
            if (iconId == -1) {
                Log.e(TAG, "No available ID for new custom icon");
                resizedBitmap.recycle();
                return -1;
            }

            // Save the bitmap to internal storage
            File outFile = new File(customIconsDir, ICON_PREFIX + iconId + ICON_EXTENSION);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                resizedBitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, fos);
                fos.flush();
            }

            resizedBitmap.recycle();
            Log.i(TAG, "Successfully imported custom icon from file with ID: " + iconId);
            return iconId;

        } catch (IOException e) {
            Log.e(TAG, "Failed to import icon from file", e);
            return -1;
        }
    }

    /**
     * Resizes a bitmap to fit within maxSize while maintaining aspect ratio
     */
    private Bitmap resizeBitmap(Bitmap original, int maxSize) {
        int width = original.getWidth();
        int height = original.getHeight();
        
        if (width <= maxSize && height <= maxSize) {
            return original; // No resizing needed
        }
        
        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);
        int newWidth = Math.round(width * ratio);
        int newHeight = Math.round(height * ratio);
        
        return Bitmap.createScaledBitmap(original, newWidth, newHeight, true);
    }
    
    /**
     * Finds the next available ID for a custom icon
     */
    private int getNextAvailableId() {
        int[] existingIds = getCustomIconIds();
        Arrays.sort(existingIds);
        
        int maxId = CUSTOM_ICON_ID_OFFSET + MAX_CUSTOM_ICONS;
        for (int id = CUSTOM_ICON_ID_OFFSET; id < maxId; id++) {
            if (Arrays.binarySearch(existingIds, id) < 0) {
                return id;
            }
        }
        
        return -1; // No available ID
    }
    
    /**
     * Deletes all custom icons
     */
    public void deleteAllIcons() {
        int[] ids = getCustomIconIds();
        for (int id : ids) {
            deleteIcon(id);
        }
        Log.i(TAG, "Deleted all " + ids.length + " custom icons");
    }

    /**
     * Gets the File for a custom icon by its ID
     */
    public File getIconFile(int iconId) {
        if (!isCustomIcon(iconId)) return null;
        File f = new File(customIconsDir, ICON_PREFIX + iconId + ICON_EXTENSION);
        return f.exists() ? f : null;
    }

    /** Rotates a custom icon 90 degrees clockwise */
    public void rotateIcon(int iconId) {
        Bitmap bm = loadIcon(iconId);
        if (bm == null) return;
        int w = bm.getWidth(), h = bm.getHeight();
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(bm, 0, 0, w, h, m, true);
        bm.recycle();
        File f = new File(customIconsDir, ICON_PREFIX + iconId + ICON_EXTENSION);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            rotated.compress(Bitmap.CompressFormat.PNG, QUALITY, fos);
        } catch (IOException e) {
            Log.e(TAG, "rotateIcon failed", e);
        }
        rotated.recycle();
    }

    // ─── Icon ordering ───

    private static final String ORDER_FILE = "custom_icons_order.txt";

    /** Saves the current display order of icon IDs to a file */
    public void saveIconOrder(List<Integer> order) {
        StringBuilder sb = new StringBuilder();
        for (int id : order) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        FileUtils.writeString(new File(customIconsDir, ORDER_FILE), sb.toString());
    }

    /**
     * Reads saved order and merges with actual icon IDs.
     * Icons not in the saved order get appended at the end.
     */
    public int[] getIconIdsInOrder(int[] actualIds) {
        File orderFile = new File(customIconsDir, ORDER_FILE);
        if (!orderFile.exists()) return actualIds;

        String content = null;
        try {
            content = new String(java.nio.file.Files.readAllBytes(orderFile.toPath()));
        } catch (IOException e) {
            return actualIds;
        }
        if (content == null || content.isEmpty()) return actualIds;

        String[] parts = content.split(",");
        java.util.LinkedHashSet<Integer> orderedSet = new java.util.LinkedHashSet<>();
        for (String p : parts) {
            try { orderedSet.add(Integer.parseInt(p.trim())); } catch (NumberFormatException ignored) {}
        }
        // Add actual IDs that aren't in the saved order
        for (int id : actualIds) orderedSet.add(id);

        int[] result = new int[orderedSet.size()];
        int i = 0;
        for (int id : orderedSet) result[i++] = id;
        return result;
    }
}