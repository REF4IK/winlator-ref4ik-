package com.winlator.cmod.components;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.winlator.cmod.core.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Установщик компонентов Wine
 * Копирует .exe/.msi файлы в директорию Temp контейнера для ручной установки
 */
public class ComponentInstaller {
    private static final String TAG = "ComponentInstaller";

    public interface InstallListener {
        void onProgress(String message);
        void onComplete();
        void onError(String error);
    }

    /**
     * Установить компонент в контейнер
     */
    public static void install(Context context, ComponentInfo component, File componentFile, 
                              String containerPath, InstallListener listener) {
        new InstallTask(context, component, componentFile, containerPath, listener).execute();
    }

    private static class InstallTask extends AsyncTask<Void, String, Boolean> {
        private Context context;
        private ComponentInfo component;
        private File componentFile;
        private String containerPath;
        private InstallListener listener;
        private String error;

        InstallTask(Context context, ComponentInfo component, File componentFile, 
                   String containerPath, InstallListener listener) {
            this.context = context;
            this.component = component;
            this.componentFile = componentFile;
            this.containerPath = containerPath;
            this.listener = listener;
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            try {
                publishProgress("Installing " + component.getDisplayName() + "...");
                
                // Создаем директорию для компонентов в контейнере
                File containerDir = new File(containerPath).getParentFile();
                File componentsDir = new File(containerDir, "drive_c/temp/winlator_components");
                if (!componentsDir.exists()) {
                    componentsDir.mkdirs();
                }
                
                // Копируем файл компонента
                File destinationFile = new File(componentsDir, componentFile.getName());
                
                publishProgress("Copying " + component.getDisplayName() + " to container...");
                
                if (copyFile(componentFile, destinationFile)) {
                    publishProgress("Component ready for installation!");
                    publishProgress("Location: C:\\temp\\winlator_components\\" + componentFile.getName());
                    
                    // Создаем README файл с инструкциями
                    createReadmeFile(componentsDir);
                    
                    return true;
                } else {
                    error = "Failed to copy component file";
                    return false;
                }

            } catch (Exception e) {
                Log.e(TAG, "Installation error", e);
                error = e.getMessage();
                return false;
            }
        }

        private boolean copyFile(File source, File destination) {
            try {
                InputStream in = new FileInputStream(source);
                OutputStream out = new FileOutputStream(destination);
                
                byte[] buffer = new byte[8192];
                int length;
                long total = 0;
                long fileSize = source.length();
                
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                    total += length;
                    
                    if (fileSize > 0) {
                        int progress = (int) ((total * 100) / fileSize);
                        publishProgress("Copying: " + progress + "%");
                    }
                }
                
                in.close();
                out.close();
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Error copying file", e);
                error = e.getMessage();
                return false;
            }
        }

        private void createReadmeFile(File dir) {
            try {
                File readme = new File(dir, "README.txt");
                if (!readme.exists()) {
                    FileOutputStream fos = new FileOutputStream(readme);
                    String content = "Winlator Components\n" +
                                   "===================\n\n" +
                                   "Components downloaded from GitHub have been placed here.\n\n" +
                                   "To install a component:\n" +
                                   "1. Open File Manager in Wine\n" +
                                   "2. Navigate to C:\\temp\\winlator_components\\\n" +
                                   "3. Double-click the installer (.exe or .msi)\n" +
                                   "4. Follow the installation wizard\n\n" +
                                   "Components:\n" +
                                   "- VCRedist: Visual C++ Runtime Libraries\n" +
                                   "- PhysX: NVIDIA Physics Engine\n" +
                                   "- Mono: .NET Framework\n" +
                                   "- Gecko: Internet Explorer Engine\n";
                    fos.write(content.getBytes());
                    fos.close();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error creating README", e);
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (listener != null && values.length > 0) {
                listener.onProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (listener != null) {
                if (success) {
                    listener.onComplete();
                } else {
                    listener.onError(error != null ? error : "Installation failed");
                }
            }
        }
    }
}
