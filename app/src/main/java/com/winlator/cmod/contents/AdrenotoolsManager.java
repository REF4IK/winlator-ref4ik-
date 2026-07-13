package com.winlator.cmod.contents;

import android.content.res.AssetManager;
import android.net.Uri;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.os.Build;
import android.provider.MediaStore;

import android.content.Context;
import android.util.Log;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.GPUInformation;
import com.winlator.cmod.contentdialog.GraphicsDriverConfigDialog;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.xenvironment.ImageFs;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.json.JSONException;
import org.json.JSONObject;

public class AdrenotoolsManager {
    
    private File adrenotoolsContentDir;
    private Context mContext;
    
    public AdrenotoolsManager(Context context) {
        this.mContext = context;
        this.adrenotoolsContentDir = new File(mContext.getFilesDir(), "imagefs/contents/adrenotools");
        if (!adrenotoolsContentDir.exists())
            adrenotoolsContentDir.mkdirs();
    }
        
    public String getLibraryName(String adrenoToolsDriverId) {
        String libraryName = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            libraryName = jsonObject.getString("libraryName");
        }
        catch (JSONException e) {
        }
        return libraryName;
    }
    
    public String getDriverName(String adrenoToolsDriverId) {
        String driverName = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            driverName = jsonObject.getString("name");
        }
        catch (JSONException e) {
        }
        return driverName;
    }

    public String getDriverVersion(String adrenoToolsDriverId) {
        String driverVersion = "";
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        try {
            File metaProfile = new File(driverPath, "meta.json");
            JSONObject jsonObject = new JSONObject(FileUtils.readString(metaProfile));
            driverVersion = jsonObject.getString("driverVersion");
        }
        catch (JSONException e) {
        }
        return driverVersion;
    }

    private void reloadContainers(String adrenoToolsDriverId) {
        ContainerManager containerManager = new ContainerManager(mContext);
        for (Container container : containerManager.getContainers()) {
            HashMap<String, String> config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(container.getGraphicsDriverConfig());
            Log.d("AdrenotoolsManager", "Checking if container driver version " + config.get("version") + " matches " + getDriverName(adrenoToolsDriverId));
            if (config.get("version").contains(getDriverName(adrenoToolsDriverId))) {
                Log.d("AdrenotoolsManager", "Found a match for container " + container.getName());
                config.put("version", DefaultVersion.WRAPPER);
                container.setGraphicsDriverConfig(GraphicsDriverConfigDialog.toGraphicsDriverConfig(config));
                container.saveData();
            }     
        }
        for (Shortcut shortcut : containerManager.loadShortcuts()) {
            HashMap<String, String> config = GraphicsDriverConfigDialog.parseGraphicsDriverConfig(shortcut.getExtra("graphicsDriverConfig", shortcut.container.getGraphicsDriverConfig()));
            Log.d("AdrenotoolsManager", "Checking if shortcut driver version " + config.get("version") + " matches " + getDriverName(adrenoToolsDriverId));
            if (config.get("version").contains(getDriverName(adrenoToolsDriverId))) {
                Log.d("AdrenotoolsManager", "Found a match for shortcut " + shortcut.name);
                config.put("version", DefaultVersion.WRAPPER);
                shortcut.putExtra("graphicsDriverConfig", GraphicsDriverConfigDialog.toGraphicsDriverConfig(config));
                shortcut.saveData();
            }
        }
    }
    
    public void removeDriver(String adrenoToolsDriverId) {
        Log.d("AdrenotoolsManager", "Removing driver " + adrenoToolsDriverId);
        File driverPath = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        reloadContainers(adrenoToolsDriverId);
        FileUtils.delete(driverPath);
    }

    public ArrayList<String> enumarateInstalledDrivers() {
        ArrayList<String> driversList = new ArrayList<>();
        
        for (File f : adrenotoolsContentDir.listFiles()) {
            boolean fromResources = isFromResources("graphics_driver/adrenotools-" + f.getName() + ".tzst");
            if (!fromResources && new File(f, "meta.json").exists())
                driversList.add(f.getName());
        }
        return driversList;
    }
    
    private boolean isFromResources(String driver) {
        AssetManager am = mContext.getResources().getAssets();
        InputStream is = null;
        boolean isFromResources = true;
        
        try {
            is = am.open(driver);
            is.close();
        }
        catch (IOException e) {
            isFromResources = false;
        }
        
        return isFromResources;
    }
        
    private boolean extractDriverFromResources(String adrenotoolsDriverId) {
        String src = "graphics_driver/adrenotools-" + adrenotoolsDriverId + ".tzst";
        boolean hasExtracted;

        File dst = new File(adrenotoolsContentDir, adrenotoolsDriverId);
        if (dst.exists())
            return true;

        dst.mkdirs();
        Log.d("AdrenotoolsManager", "Extracting " + src + " to " + dst.getAbsolutePath());
        hasExtracted = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, mContext, src, dst);

        if (!hasExtracted)
            dst.delete();

        return hasExtracted;
    }
    
    public String installDriver(Uri driverUri) {
        File tmpDir = new File(adrenotoolsContentDir, "tmp");
        if (tmpDir.exists()) FileUtils.delete(tmpDir);
        tmpDir.mkdirs();
        ZipInputStream zis;
        InputStream is;
        String name = "";
        
        try {
            is = mContext.getContentResolver().openInputStream(driverUri);
            zis = new ZipInputStream(is);
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                File dstFile = new File(tmpDir, entry.getName());
                // Создаём родительские папки для файлов в подпапках архива
                File parentDir = dstFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) parentDir.mkdirs();
                if (!entry.isDirectory()) {
                    Files.copy(zis, dstFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                entry = zis.getNextEntry();
            }
            zis.close();
            // Ищем meta.json — либо в корне tmpDir, либо в первой подпапке
            File metaFile = new File(tmpDir, "meta.json");
            File driverRoot = tmpDir;
            if (!metaFile.exists()) {
                // Архив с подпапкой: ищем meta.json в подпапках
                File[] subDirs = tmpDir.listFiles();
                if (subDirs != null) {
                    for (File f : subDirs) {
                        if (f.isDirectory() && new File(f, "meta.json").exists()) {
                            driverRoot = f;
                            metaFile = new File(f, "meta.json");
                            break;
                        }
                    }
                }
            }
            if (metaFile.exists()) {
                // Читаем имя драйвера из meta.json
                try {
                    JSONObject jsonObject = new JSONObject(FileUtils.readString(metaFile));
                    name = jsonObject.getString("name");
                } catch (JSONException e) {
                    name = driverRoot.getName();
                }
                File dst = new File(adrenotoolsContentDir, name);
                if (!dst.exists() && !name.equals("")) {
                    // Перемещаем папку драйвера в итоговое место
                    if (driverRoot != tmpDir) {
                        driverRoot.renameTo(dst);
                    } else {
                        tmpDir.renameTo(dst);
                    }
                } else {
                    name = "";
                    FileUtils.delete(tmpDir);
                }
            }
            else {
                Log.d("AdrenotoolsManager", "Failed to install driver, a valid driver has not been selected");
                FileUtils.delete(tmpDir);
            }
        }
        catch (IOException e) {
            Log.d("AdrenotoolsManager", "Failed to install driver, a valid driver has not been selected", e);
            FileUtils.delete(tmpDir);
        }
        
        return name;
    }
    
    public void setDriverById(EnvVars envVars, ImageFs imagefs, String adrenotoolsDriverId) {
        if (extractDriverFromResources(adrenotoolsDriverId) || enumarateInstalledDrivers().contains(adrenotoolsDriverId)) {
            String driverPath = adrenotoolsContentDir.getAbsolutePath() + "/" + adrenotoolsDriverId + "/";
            if (!getLibraryName(adrenotoolsDriverId).equals("")) {
                envVars.put("ADRENOTOOLS_DRIVER_PATH", driverPath);
                envVars.put("ADRENOTOOLS_HOOKS_PATH", imagefs.getLibDir());
                envVars.put("ADRENOTOOLS_DRIVER_NAME", getLibraryName(adrenotoolsDriverId));
                if (adrenotoolsDriverId.contains("v762") && GPUInformation.getVersion().contains("512.530")) {
                    Log.d("AdrenotoolsManager", "Patching v762 driver for stock v530");
                    FileUtils.writeToBinaryFile(driverPath + "notadreno_utils.so", 0x2680, 3);
                } else if (adrenotoolsDriverId.contains("v762") && GPUInformation.getVersion().contains("512.502")) {
                    Log.d("AdrenotoolsManager", "Patching v762 driver for stock v502");
                    FileUtils.writeToBinaryFile(driverPath + "notadreno_utils.so", 0x2680, 2);
                }
            }
        }
    }

    public boolean exportDriverToDownloads(String adrenoToolsDriverId) {
        File driverDir = new File(adrenotoolsContentDir, adrenoToolsDriverId);
        if (!driverDir.exists() || !driverDir.isDirectory()) {
            return false;
        }

        String fileName = adrenoToolsDriverId + ".zip";

        ContentResolver resolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");

        Uri collection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/");
            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
        }

        Uri itemUri = resolver.insert(collection, values);
        if (itemUri == null) {
            return false;
        }

        try (OutputStream os = resolver.openOutputStream(itemUri);
             ZipOutputStream zos = os != null ? new ZipOutputStream(os) : null) {

            if (zos == null) {
                return false;
            }

            zipDirectory(driverDir, driverDir.getName(), zos);
            zos.finish();
            return true;

        } catch (Exception e) {
            try {
                resolver.delete(itemUri, null, null);
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    private void zipDirectory(File dir, String basePath, ZipOutputStream zos) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String entryName = basePath + "/" + file.getName();
            if (file.isDirectory()) {
                zipDirectory(file, entryName, zos);
            } else {
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                try (InputStream is = Files.newInputStream(file.toPath())) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    public void writeStoreInfo(String driverId, String storeName, String version, String downloadUrl) {
        File driverPath = new File(adrenotoolsContentDir, driverId);
        if (driverPath.exists() && driverPath.isDirectory()) {
            try {
                JSONObject json = new JSONObject();
                json.put("storeName", storeName);
                json.put("version", version);
                json.put("downloadUrl", downloadUrl);
                FileUtils.writeString(new File(driverPath, "store_info.json"), json.toString());
            } catch (Exception e) {
                Log.e("AdrenotoolsManager", "Failed to write store_info.json", e);
            }
        }
    }

    public JSONObject getStoreInfo(String driverId) {
        File driverPath = new File(adrenotoolsContentDir, driverId);
        File storeInfoFile = new File(driverPath, "store_info.json");
        if (storeInfoFile.exists() && storeInfoFile.isFile()) {
            try {
                return new JSONObject(FileUtils.readString(storeInfoFile));
            } catch (Exception e) {
                Log.e("AdrenotoolsManager", "Failed to read store_info.json", e);
            }
        }
        return null;
    }
}
