package com.winlator.cmod.xenvironment.components;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

import androidx.preference.PreferenceManager;

import com.winlator.cmod.box86_64.Box86_64Preset;
import com.winlator.cmod.box86_64.Box86_64PresetManager;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.DefaultVersion;
import com.winlator.cmod.core.EnvVars;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.TarCompressorUtils;
import com.winlator.cmod.fexcore.FEXCorePreset;
import com.winlator.cmod.fexcore.FEXCorePresetManager;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xenvironment.EnvironmentComponent;
import com.winlator.cmod.xenvironment.ImageFs;

import java.io.File;

public class GuestProgramLauncherComponent extends EnvironmentComponent {
    private String guestExecutable;
    private static int pid = -1;
    private String[] bindingPaths;
    private EnvVars envVars;
    private String box86Preset = Box86_64Preset.COMPATIBILITY;
    private String box64Preset = Box86_64Preset.COMPATIBILITY;
    protected String fexcorePreset = FEXCorePreset.INTERMEDIATE;
    private Callback<Integer> terminationCallback;
    private static final Object lock = new Object();
    private boolean wow64Mode = true;

    private static String appendFirstExistingPreload(String ldPreload, File[] candidates) {
        for (File candidate : candidates) {
            if (candidate.exists()) {
                if (!ldPreload.isEmpty()) ldPreload += ":";
                ldPreload += candidate.getAbsolutePath();
                return ldPreload;
            }
        }
        return ldPreload;
    }

    protected boolean isProotLaunch() {
        return true;
    }

    protected static void setActivePid(int newPid) {
        synchronized (lock) {
            pid = newPid;
        }
    }

    @Override
    public void start() {
        synchronized (lock) {
            stop();
            extractBox86_64Files();
            pid = execGuestProgram();
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            if (pid != -1) {
                ProcessHelper.terminateProcess(pid);
                try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                Process.killProcess(pid);
                pid = -1;
            }
        }
    }

    public Callback<Integer> getTerminationCallback() {
        return terminationCallback;
    }

    public void setTerminationCallback(Callback<Integer> terminationCallback) {
        this.terminationCallback = terminationCallback;
    }

    public String getGuestExecutable() {
        return guestExecutable;
    }

    public void setGuestExecutable(String guestExecutable) {
        this.guestExecutable = guestExecutable;
    }

    public boolean isWoW64Mode() {
        return wow64Mode;
    }

    public void setWoW64Mode(boolean wow64Mode) {
        this.wow64Mode = wow64Mode;
    }

    public String[] getBindingPaths() {
        return bindingPaths;
    }

    public void setBindingPaths(String[] bindingPaths) {
        this.bindingPaths = bindingPaths;
    }

    public EnvVars getEnvVars() {
        return envVars;
    }

    public void setEnvVars(EnvVars envVars) {
        this.envVars = envVars;
    }

    public String getBox86Preset() {
        return box86Preset;
    }

    public void setBox86Preset(String box86Preset) {
        this.box86Preset = box86Preset;
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    public String getFEXCorePreset() {
        return fexcorePreset;
    }

    public void setFEXCorePreset(String fexcorePreset) {
        this.fexcorePreset = fexcorePreset;
    }

    private int execGuestProgram() {
        Context context = environment.getContext();
        ImageFs imageFs = environment.getImageFs();
        File rootDir = imageFs.getRootDir();
        File tmpDir = environment.getTmpDir();
        String nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;

        SharedPreferences preferences = new com.winlator.cmod.core.MmkvPreferences();
        boolean enableBox86_64Logs = preferences.getBoolean("enable_box86_64_logs", false);

        EnvVars envVars = new EnvVars();
        if (!wow64Mode) addBox86EnvVars(envVars, enableBox86_64Logs);
        addBox64EnvVars(envVars, enableBox86_64Logs);
        envVars.putAll(FEXCorePresetManager.getEnvVars(context, fexcorePreset));
        envVars.put("HOME", ImageFs.HOME_PATH);
        envVars.put("USER", ImageFs.USER);
        envVars.put("TMPDIR", "/tmp");
        if (!envVars.has("WRAPPER_MAX_IMAGE_COUNT")) envVars.put("WRAPPER_MAX_IMAGE_COUNT", "0");
        envVars.put("LC_ALL", "en_US.utf8");
        envVars.put("DISPLAY", ":0");
        envVars.put("PATH", imageFs.getWinePath()+"/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
        envVars.put("LD_LIBRARY_PATH", "/usr/lib/aarch64-linux-gnu:/usr/lib/arm-linux-gnueabihf");
        envVars.put("ANDROID_SYSVSHM_SERVER", UnixSocketConfig.SYSVSHM_SERVER_PATH);

        if ((new File(imageFs.getLibDir(), "libandroid-sysvshm.so")).exists()) 
            envVars.put("LD_PRELOAD", "libandroid-sysvshm.so");
        
        String ldPreload = envVars.get("LD_PRELOAD") != null ? envVars.get("LD_PRELOAD") : "";
        File[] jpegCandidates = new File[] {
            new File("/system/lib64/libjpeg.so"),
            new File("/system_ext/lib64/libjpeg.so"),
        };
        ldPreload = appendFirstExistingPreload(ldPreload, jpegCandidates);

        File[] cryptoCandidates = new File[] {
            new File("/system/lib64/libcrypto.so"),
            new File("/system_ext/lib64/libcrypto.so"),
            new File(imageFs.getLibDir(), "libcrypto.so.3"),
        };
        ldPreload = appendFirstExistingPreload(ldPreload, cryptoCandidates);

        if (!ldPreload.isEmpty()) envVars.put("LD_PRELOAD", ldPreload);
        
        // Добавляем пользовательские переменные окружения (могут перезаписать любые из вышеуказанных)
        if (this.envVars != null) envVars.putAll(this.envVars);

        boolean bindSHM = envVars.get("WINEESYNC").equals("1");

        String command = nativeLibraryDir+"/libproot.so";
        command += " --kill-on-exit";
        command += " --rootfs="+rootDir;
        command += " --cwd="+ImageFs.HOME_PATH;
        command += " --bind=/dev";

        if (bindSHM) {
            File shmDir = new File(rootDir, "/tmp/shm");
            shmDir.mkdirs();
            command += " --bind="+shmDir.getAbsolutePath()+":/dev/shm";
        }

        command += " --bind=/proc";
        command += " --bind=/sys";

        if (bindingPaths != null) {
            for (String path : bindingPaths) command += " --bind="+(new File(path)).getAbsolutePath();
        }

        command += " /usr/bin/env "+envVars.toEscapedString()+" box64 "+guestExecutable;

        envVars.clear();
        envVars.put("PROOT_TMP_DIR", tmpDir);
        envVars.put("PROOT_LOADER", nativeLibraryDir+"/libproot-loader.so");
        if (!wow64Mode) envVars.put("PROOT_LOADER_32", nativeLibraryDir+"/libproot-loader32.so");

        return ProcessHelper.exec(command, envVars.toStringArray(), rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }
            if (terminationCallback != null) terminationCallback.call(status);
        });
    }

    private void extractBox86_64Files() {
        ImageFs imageFs = environment.getImageFs();
        Context context = environment.getContext();
        SharedPreferences preferences = new com.winlator.cmod.core.MmkvPreferences();
        String box86Version = preferences.getString("box86_version", DefaultVersion.BOX86);
        String box64Version = preferences.getString("box64_version", DefaultVersion.BOX64);
        String currentBox86Version = preferences.getString("current_box86_version", "");
        String currentBox64Version = preferences.getString("current_box64_version", "");
        File rootDir = imageFs.getRootDir();

        if (wow64Mode) {
            File box86File = new File(rootDir, "/usr/local/bin/box86");
            if (box86File.isFile()) {
                box86File.delete();
                preferences.edit().putString("current_box86_version", "").apply();
            }
        }
        else if (!box86Version.equals(currentBox86Version)) {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "box86_64/box86-"+box86Version+".tzst", rootDir);
            preferences.edit().putString("current_box86_version", box86Version).apply();
        }

        if (!box64Version.equals(currentBox64Version)) {
            TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, "box86_64/box64-"+box64Version+".tzst", rootDir);
            preferences.edit().putString("current_box64_version", box64Version).apply();
        }
    }

    private void addBox86EnvVars(EnvVars envVars, boolean enableLogs) {
        envVars.put("BOX86_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX86_DYNAREC", "1");

        if (enableLogs) {
            envVars.put("BOX86_LOG", "1");
            envVars.put("BOX86_DYNAREC_MISSING", "1");
        }

        envVars.putAll(Box86_64PresetManager.getEnvVars("box86", environment.getContext(), box86Preset));
        envVars.put("BOX86_X11GLX", "1");
        envVars.put("BOX86_NORCFILES", "1");
    }

    private void addBox64EnvVars(EnvVars envVars, boolean enableLogs) {
        envVars.put("BOX64_NOBANNER", ProcessHelper.PRINT_DEBUG && enableLogs ? "0" : "1");
        envVars.put("BOX64_DYNAREC", "1");

        if (enableLogs) {
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");
        } else {
            envVars.put("BOX64_LOG", "0");
        }

        envVars.putAll(Box86_64PresetManager.getEnvVars("box64", environment.getContext(), box64Preset));
        envVars.put("BOX64_X11GLX", "1");
    }

    public void suspendProcess() {
        synchronized (lock) {
            if (pid != -1) ProcessHelper.suspendProcess(pid);
        }
    }

    public void resumeProcess() {
        synchronized (lock) {
            if (pid != -1) ProcessHelper.resumeProcess(pid);
        }
    }

    public String execShellCommand(String command) {
        throw new UnsupportedOperationException("execShellCommand not implemented in base class.");
    }

}
