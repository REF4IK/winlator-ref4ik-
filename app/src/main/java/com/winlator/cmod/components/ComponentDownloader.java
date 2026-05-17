package com.winlator.cmod.components;

import android.os.AsyncTask;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * Загрузчик компонентов с GitHub с поддержкой прогресса
 */
public class ComponentDownloader {
    private static final String TAG = "ComponentDownloader";
    
    /**
     * Класс для хранения информации о прогрессе загрузки
     */
    public static class DownloadProgress {
        public final int progressPercent;
        public final long downloadedBytes;
        public final long totalBytes;
        public final String componentName;
        
        public DownloadProgress(int progressPercent, long downloadedBytes, long totalBytes, String componentName) {
            this.progressPercent = progressPercent;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.componentName = componentName;
        }
        
        public String getDownloadedMB() {
            return String.format("%.2f", downloadedBytes / (1024.0 * 1024.0));
        }
        
        public String getTotalMB() {
            return String.format("%.2f", totalBytes / (1024.0 * 1024.0));
        }
        
        public String getProgressText() {
            return getDownloadedMB() + " MB / " + getTotalMB() + " MB (" + progressPercent + "%)";
        }
    }

    public interface DownloadListener {
        void onProgress(DownloadProgress progress);
        void onComplete(File file, ComponentInfo component);
        void onError(String error, ComponentInfo component);
    }

    /**
     * Загрузить компонент
     */
    public static void download(ComponentInfo component, File destinationDir, DownloadListener listener) {
        new DownloadTask(component, destinationDir, listener).execute();
    }

    private static class DownloadTask extends AsyncTask<Void, DownloadProgress, File> {
        private ComponentInfo component;
        private File destinationDir;
        private DownloadListener listener;
        private String error;
        private long fileLength;

        DownloadTask(ComponentInfo component, File destinationDir, DownloadListener listener) {
            this.component = component;
            this.destinationDir = destinationDir;
            this.listener = listener;
        }

        @Override
        protected File doInBackground(Void... voids) {
            HttpURLConnection connection = null;
            InputStream input = null;
            FileOutputStream output = null;

            try {
                // Создаем директорию если не существует
                if (!destinationDir.exists()) {
                    destinationDir.mkdirs();
                }

                // Файл назначения
                File destinationFile = new File(destinationDir, component.getFileName());
                
                // Если файл уже существует и имеет правильный размер, возвращаем его
                if (destinationFile.exists() && destinationFile.length() == component.getFileSize()) {
                    Log.d(TAG, "File already exists and has correct size: " + destinationFile.getName());
                    return destinationFile;
                }

                Log.d(TAG, "Downloading: " + component.getDisplayName() + " from " + component.getDownloadUrl());

                URL url = new URL(component.getDownloadUrl());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                connection.setRequestMethod("GET");
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    error = "HTTP error code: " + responseCode;
                    return null;
                }

                fileLength = connection.getContentLength();
                if (fileLength == -1) {
                    fileLength = component.getFileSize();
                }

                input = connection.getInputStream();
                output = new FileOutputStream(destinationFile);

                byte[] buffer = new byte[8192];
                long total = 0;
                int count;
                int lastProgress = 0;

                while ((count = input.read(buffer)) != -1) {
                    if (isCancelled()) {
                        return null;
                    }

                    total += count;
                    output.write(buffer, 0, count);

                    // Публикуем прогресс с информацией о размерах
                    if (fileLength > 0) {
                        int progress = (int) ((total * 100) / fileLength);
                        if (progress != lastProgress) {
                            DownloadProgress downloadProgress = new DownloadProgress(
                                progress, total, fileLength, component.getDisplayName()
                            );
                            publishProgress(downloadProgress);
                            lastProgress = progress;
                        }
                    }
                }

                output.flush();
                Log.d(TAG, "Download complete: " + destinationFile.getName());
                return destinationFile;

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                error = e.getMessage();
                return null;
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing streams", e);
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        @Override
        protected void onProgressUpdate(DownloadProgress... values) {
            if (listener != null && values.length > 0) {
                listener.onProgress(values[0]);
            }
        }

        @Override
        protected void onPostExecute(File file) {
            if (listener != null) {
                if (error != null) {
                    listener.onError(error, component);
                } else if (file != null) {
                    listener.onComplete(file, component);
                } else {
                    listener.onError("Download failed", component);
                }
            }
        }
    }

    /**
     * Загрузить несколько компонентов последовательно
     */
    public static void downloadMultiple(List<ComponentInfo> components, File destinationDir, MultiDownloadListener listener) {
        new MultiDownloadTask(components, destinationDir, listener).execute();
    }

    public interface MultiDownloadListener {
        void onComponentProgress(ComponentInfo component, int progress);
        void onComponentComplete(ComponentInfo component, File file);
        void onComponentError(ComponentInfo component, String error);
        void onAllComplete();
    }

    private static class MultiDownloadTask extends AsyncTask<Void, DownloadUpdate, Void> {
        private List<ComponentInfo> components;
        private File destinationDir;
        private MultiDownloadListener listener;

        MultiDownloadTask(List<ComponentInfo> components, File destinationDir, MultiDownloadListener listener) {
            this.components = components;
            this.destinationDir = destinationDir;
            this.listener = listener;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (ComponentInfo component : components) {
                if (isCancelled()) {
                    break;
                }

                publishProgress(new DownloadUpdate(component, 0, DownloadUpdate.Type.START));

                // Загружаем компонент
                download(component, destinationDir, new DownloadListener() {
                    @Override
                    public void onProgress(DownloadProgress progress) {
                        publishProgress(new DownloadUpdate(component, progress.progressPercent, DownloadUpdate.Type.PROGRESS));
                    }

                    @Override
                    public void onComplete(File file, ComponentInfo comp) {
                        publishProgress(new DownloadUpdate(component, 100, DownloadUpdate.Type.COMPLETE, file));
                    }

                    @Override
                    public void onError(String error, ComponentInfo comp) {
                        publishProgress(new DownloadUpdate(component, 0, DownloadUpdate.Type.ERROR, null, error));
                    }
                });

                // Ждем завершения загрузки текущего компонента
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(DownloadUpdate... values) {
            if (listener != null && values.length > 0) {
                DownloadUpdate update = values[0];
                switch (update.type) {
                    case PROGRESS:
                        listener.onComponentProgress(update.component, update.progress);
                        break;
                    case COMPLETE:
                        listener.onComponentComplete(update.component, update.file);
                        break;
                    case ERROR:
                        listener.onComponentError(update.component, update.error);
                        break;
                }
            }
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (listener != null) {
                listener.onAllComplete();
            }
        }
    }

    private static class DownloadUpdate {
        enum Type { START, PROGRESS, COMPLETE, ERROR }
        
        ComponentInfo component;
        int progress;
        Type type;
        File file;
        String error;

        DownloadUpdate(ComponentInfo component, int progress, Type type) {
            this(component, progress, type, null, null);
        }

        DownloadUpdate(ComponentInfo component, int progress, Type type, File file) {
            this(component, progress, type, file, null);
        }

        DownloadUpdate(ComponentInfo component, int progress, Type type, File file, String error) {
            this.component = component;
            this.progress = progress;
            this.type = type;
            this.file = file;
            this.error = error;
        }
    }
}
