package com.winlator.cmod.contents;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.StringUtils;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public class WrapperManager {
    private static final String TAG = "WrapperManager";
    private static final String OVERRIDE_DIR_NAME = "graphics_driver";
    private static final String META_SUFFIX = ".meta";

    private final Context context;

    public WrapperManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static File getWrapperDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), OVERRIDE_DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<String> driverEntries(Context ctx) {
        List<String> list = new ArrayList<>();
        // bundled
        String[] bundled = ctx.getResources().getStringArray(com.winlator.cmod.R.array.graphics_driver_entries);
        for (String s : bundled) list.add(s);
        // imported
        File dir = getWrapperDir(ctx);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".tzst"));
        if (files != null) {
            for (File f : files) {
                String id = f.getName().replace(".tzst", "");
                // skip bundled overrides if any named wrapper.tzst etc that correspond to bundled?
                // treat any .tzst that is not exactly "wrapper" or "wrapper-leegao" as custom
                if (id.equals("wrapper") || id.equals("wrapper-leegao") || id.equals("extra_libs")) continue;
                // display name from meta if exists
                String display = id;
                File meta = new File(dir, id + META_SUFFIX);
                if (meta.exists()) {
                    try {
                        String content = FileUtils.readString(meta);
                        for (String line : content.split("\n")) {
                            if (line.startsWith("label=")) {
                                display = line.substring(6).trim();
                                if (!display.isEmpty()) break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (!list.contains(display)) list.add(display);
                // also add id if different from display for lookup?
                if (!display.equals(id) && !list.contains(id)) list.add(id);
            }
        }
        return list;
    }

    public static boolean isCustomWrapper(Context ctx, String driver) {
        if (driver == null) return false;
        File dir = getWrapperDir(ctx);
        // check file existence by id or label
        File direct = new File(dir, driver + ".tzst");
        if (direct.exists()) return true;
        // try find by label
        File[] files = dir.listFiles((d, name) -> name.endsWith(".tzst"));
        if (files != null) {
            for (File f : files) {
                String id = f.getName().replace(".tzst", "");
                File meta = new File(dir, id + META_SUFFIX);
                if (meta.exists()) {
                    try {
                        String content = FileUtils.readString(meta);
                        for (String line : content.split("\n")) {
                            if (line.startsWith("label=") && line.substring(6).trim().equals(driver)) return true;
                        }
                    } catch (Exception ignored) {}
                }
                if (id.equalsIgnoreCase(driver)) return true;
            }
        }
        return false;
    }

    public static String resolveWrapperId(Context ctx, String driver) {
        if (driver == null) return null;
        File dir = getWrapperDir(ctx);
        File direct = new File(dir, driver + ".tzst");
        if (direct.exists()) return driver;
        File[] files = dir.listFiles((d, name) -> name.endsWith(".tzst"));
        if (files != null) {
            for (File f : files) {
                String id = f.getName().replace(".tzst", "");
                File meta = new File(dir, id + META_SUFFIX);
                if (meta.exists()) {
                    try {
                        String content = FileUtils.readString(meta);
                        for (String line : content.split("\n")) {
                            if (line.startsWith("label=") && line.substring(6).trim().equals(driver)) return id;
                        }
                    } catch (Exception ignored) {}
                }
                if (id.equalsIgnoreCase(driver)) return id;
            }
        }
        return driver;
    }

    public static File getWrapperFile(Context ctx, String driver) {
        String id = resolveWrapperId(ctx, driver);
        if (id == null) return null;
        File dir = getWrapperDir(ctx);
        File f = new File(dir, id + ".tzst");
        if (f.exists()) return f;
        return null;
    }

    /**
     * Import wrapper from Uri (file). Validates that file exists, copies to wrapper dir.
     * Returns identifier (file name without extension) on success, null on failure.
     */
    public String importWrapper(Uri srcUri, String displayName, String catalogId, int version) {
        if (srcUri == null) return null;
        String label = displayName != null && !displayName.isEmpty() ? displayName : (catalogId != null ? catalogId : "wrapper-custom");
        String identifier = StringUtils.parseIdentifier(label);
        if (identifier == null || identifier.isEmpty()) identifier = "wrapper-custom";
        // sanitize
        identifier = identifier.replaceAll("[^A-Za-z0-9._-]", "_");
        if (identifier.isEmpty()) identifier = "wrapper-custom";

        File dir = getWrapperDir(context);
        File dst = new File(dir, identifier + ".tzst");
        // handle collision: if exists and catalogId same, overwrite; else suffix
        int suffix = 2;
        String baseId = identifier;
        while (dst.exists()) {
            // check if same catalogId -> allow overwrite
            File meta = new File(dir, identifier + META_SUFFIX);
            boolean sameCatalog = false;
            if (meta.exists() && catalogId != null) {
                try {
                    String m = FileUtils.readString(meta);
                    for (String line : m.split("\n")) if (line.startsWith("catalogId=") && line.substring(10).trim().equals(catalogId)) sameCatalog = true;
                } catch (Exception ignored) {}
            }
            if (sameCatalog) break;
            identifier = baseId + "-" + suffix++;
            dst = new File(dir, identifier + ".tzst");
        }

        // copy file
        try (InputStream in = context.getContentResolver().openInputStream(srcUri)) {
            if (in == null) return null;
            File tmp = new File(dir, identifier + ".tzst.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            // basic validation: file should exist and > 1KB
            if (!tmp.exists() || tmp.length() < 1024) {
                tmp.delete();
                return null;
            }
            if (dst.exists()) dst.delete();
            if (!tmp.renameTo(dst)) {
                FileUtils.copy(tmp, dst);
                tmp.delete();
            }
            // write meta
            File meta = new File(dir, identifier + META_SUFFIX);
            StringBuilder sb = new StringBuilder();
            sb.append("label=").append(label).append("\n");
            sb.append("identifier=").append(identifier).append("\n");
            sb.append("catalogId=").append(catalogId != null ? catalogId : "").append("\n");
            sb.append("catalogVersion=").append(version).append("\n");
            sb.append("imported=").append(System.currentTimeMillis()).append("\n");
            FileUtils.writeString(meta, sb.toString());
            Log.i(TAG, "Imported wrapper " + identifier + " label=" + label);
            return identifier;
        } catch (Exception e) {
            Log.w(TAG, "importWrapper failed", e);
            return null;
        }
    }

    public boolean deleteWrapper(String identifier) {
        if (identifier == null) return false;
        File dir = getWrapperDir(context);
        File f = new File(dir, identifier + ".tzst");
        File meta = new File(dir, identifier + META_SUFFIX);
        boolean ok = true;
        if (f.exists()) ok &= f.delete();
        if (meta.exists()) ok &= meta.delete();
        return ok;
    }

    public static List<String> listWrapperIds(Context ctx) {
        List<String> ids = new ArrayList<>();
        File dir = getWrapperDir(ctx);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".tzst"));
        if (files != null) for (File f : files) {
            String id = f.getName().replace(".tzst", "");
            if (id.equals("wrapper") || id.equals("wrapper-leegao") || id.equals("extra_libs")) continue;
            ids.add(id);
        }
        return ids;
    }

    // Compatibility helpers for banner downloader
    public boolean installOverride(String slotFileName, Uri srcUri) {
        // treat slot as identifier without .tzst
        String id = slotFileName.replace(".tzst", "");
        return importWrapper(srcUri, id, id, 1) != null;
    }

    public void recordSlotCatalog(String slotFileName, String catalogId, int version, String name) {
        // no-op for simplified version
    }
}
