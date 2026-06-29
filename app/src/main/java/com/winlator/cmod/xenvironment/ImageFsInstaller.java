package com.winlator.cmod.xenvironment;

import android.content.Context;
import com.winlator.cmod.R;
import com.winlator.cmod.SettingsFragment;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.core.WineInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public abstract class ImageFsInstaller {
    public static final byte LATEST_VERSION = 21;

    public interface OnProgressListener {
        void onProgress(int progress);
        void onFinished(boolean success);
    }

    private static void resetContainerImgVersions(Context context) {
        ContainerManager manager = new ContainerManager(context);
        for (Container container : manager.getContainers()) {
            String imgVersion = container.getExtra("imgVersion");
            String wineVersion = container.getWineVersion();
            if (!imgVersion.isEmpty() && WineInfo.isMainWineVersion(wineVersion) && Short.parseShort(imgVersion) <= 5) {
                container.putExtra("wineprefixNeedsUpdate", "t");
            }

            container.putExtra("imgVersion", null);
            container.saveData();
        }
    }

    public static void installWineFromAssets(final Context context) {
        String[] versions = context.getResources().getStringArray(R.array.wine_entries);
        File rootDir = ImageFs.find(context).getRootDir();
        
        // Use parallel processing for faster extraction
        int numThreads = Math.min(versions.length, Runtime.getRuntime().availableProcessors());
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CompletionService<Boolean> completionService = new ExecutorCompletionService<>(executor);
        ArrayList<Future<Boolean>> futures = new ArrayList<>();
        
        for (String version : versions) {
            futures.add(completionService.submit(() -> {
                File outFile = new File(rootDir, "/opt/" + version);
                outFile.mkdirs();
                return TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, context, version + ".txz", outFile);
            }));
        }
        
        // Wait for all extractions to complete
        for (Future<Boolean> future : futures) {
            try {
                future.get(); // This will block until the task completes
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        executor.shutdown();
    }

    public static void installFromAssets(final Context context) {
        installFromAssets(context, null);
    }

    public static void installFromAssets(final Context context, final OnProgressListener listener) {
        ImageFs imageFs = ImageFs.find(context);
        File rootDir = imageFs.getRootDir();

        SettingsFragment.resetEmulatorsVersion(context);
        
        // Use parallel processing for faster extraction
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            clearRootDir(rootDir);
            final byte compressionRatio = 22;
            final long contentLength = (long)(FileUtils.getSize(context, "imagefs.txz") * (100.0f / compressionRatio));
            AtomicLong totalSizeRef = new AtomicLong();

            boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.XZ, context, "imagefs.txz", rootDir, (file, size) -> {
                if (size > 0) {
                    long totalSize = totalSizeRef.addAndGet(size);
                    final int progress = (int)(((float)totalSize / contentLength) * 100);
                    if (listener != null) listener.onProgress(progress);
                }
                return file;
            });

            if (success) {
                installWineFromAssets(context);
                installGuestLibraries(rootDir);
                imageFs.createImgVersionFile(LATEST_VERSION);
                resetContainerImgVersions(context);
            }

            if (listener != null) listener.onFinished(success);
            executor.shutdown();
        });
    }

    private static void clearRootDir(File rootDir) {
        if (rootDir.isDirectory()) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String name = file.getName();
                        if (name.equals("home")) {
                            continue;
                        }
                    }
                    FileUtils.delete(file);
                }
            }
        }
        else rootDir.mkdirs();
    }
    
    private static void installGuestLibraries(File rootDir) {
        // Install guest libraries for ALSA-Reflector functionality
        File guestLibDir = new File(rootDir, "/usr/lib/winlator/guest");
        guestLibDir.mkdirs();
        
        File guestLib = new File(guestLibDir, "libevshim_guest.so");
        if (!guestLib.exists()) {
            try {
                guestLib.createNewFile();
                android.util.Log.d("ImageFsInstaller", "Guest library placeholder created: " + guestLib.getPath());
            } catch (Exception e) {
                android.util.Log.e("ImageFsInstaller", "Failed to create guest library: " + e.getMessage());
            }
        }
    }
}