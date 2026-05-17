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
    public static final short CUSTOM_ICON_ID_OFFSET = 100;
    public static final short MAX_CUSTOM_ICONS = 1000; // Максимум кастомных иконок
    
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
    public short importIcon(Uri imageUri) {
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
            short iconId = getNextAvailableId();
            if (iconId == -1) {
                Log.e(TAG, "No available ID for new custom icon");
                resizedBitmap.recycle();
                return (short) -1;
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
            return (short) -1;
        }
    }
    
    /**
     * Loads a custom icon by its ID
     * @param iconId The ID of the icon to load
     * @return The loaded bitmap, or null if not found
     */
    public Bitmap loadIcon(short iconId) {
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
    public boolean deleteIcon(short iconId) {
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
    public short[] getCustomIconIds() {
        if (!customIconsDir.exists()) {
            return new short[0];
        }
        
        File[] iconFiles = customIconsDir.listFiles((dir, name) -> 
            name.startsWith(ICON_PREFIX) && name.endsWith(ICON_EXTENSION)
        );
        
        if (iconFiles == null || iconFiles.length == 0) {
            return new short[0];
        }
        
        List<Short> iconIds = new ArrayList<>();
        for (File iconFile : iconFiles) {
            try {
                String fileName = iconFile.getName();
                String idStr = fileName.substring(ICON_PREFIX.length(), fileName.lastIndexOf('.'));
                short iconId = Short.parseShort(idStr);
                iconIds.add(iconId);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Invalid custom icon filename: " + iconFile.getName());
            }
        }
        
        Collections.sort(iconIds);
        short[] result = new short[iconIds.size()];
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
    public static boolean isCustomIcon(short iconId) {
        return iconId >= CUSTOM_ICON_ID_OFFSET;
    }
    
    /**
     * Gets the number of custom icons currently stored
     * @return Number of custom icons
     */
    public int getCustomIconCount() {
        return getCustomIconIds().length;
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
    private short getNextAvailableId() {
        short[] existingIds = getCustomIconIds();
        Arrays.sort(existingIds);
        
        short maxId = (short) (CUSTOM_ICON_ID_OFFSET + MAX_CUSTOM_ICONS);
        for (short id = CUSTOM_ICON_ID_OFFSET; id < maxId; id++) {
            if (Arrays.binarySearch(existingIds, id) < 0) {
                return id;
            }
        }
        
        return (short) -1; // No available ID
    }
}