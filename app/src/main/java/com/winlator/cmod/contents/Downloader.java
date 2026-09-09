package com.winlator.cmod.contents;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class Downloader {

    public interface DownloadProgressListener {
        void onProgress(long downloaded, long total);
    }

    /** Banner-совместимый лисенер: fraction 0..1, -1 когда размер неизвестен. */
    public interface ProgressListener {
        void onProgress(float fraction);
    }

    public static boolean downloadFile(String address, File file) {
        return downloadFile(address, file, false, (DownloadProgressListener) null);
    }

    public static boolean downloadFile(String address, File file, DownloadProgressListener listener) {
        return downloadFile(address, file, false, listener);
    }

    public static boolean downloadFile(String address, File file, ProgressListener listener) {
        return downloadFile(address, file, false, listener);
    }

    public static boolean downloadFile(String address, File file, boolean resume, DownloadProgressListener listener) {
        return downloadCore(address, file, resume, listener, null);
    }

    public static boolean downloadFile(String address, File file, boolean resume, ProgressListener listener) {
        return downloadCore(address, file, resume, null, listener);
    }

    private static boolean downloadCore(String address, File file, boolean resume,
                                        DownloadProgressListener byteListener, ProgressListener fractionListener) {
        try {
            long existing = (resume && file.exists()) ? file.length() : 0;

            URL url = new URL(address);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            if (existing > 0) connection.setRequestProperty("Range", "bytes=" + existing + "-");

            boolean append = false;
            long readTotal = 0;
            long total;

            if (connection instanceof HttpURLConnection) {
                HttpURLConnection http = (HttpURLConnection) connection;
                int code = http.getResponseCode();
                if (existing > 0 && code == HttpURLConnection.HTTP_PARTIAL) {
                    append = true;
                    readTotal = existing;
                    total = existing + connection.getContentLengthLong();
                } else if (existing > 0 && code == 416) {
                    if (byteListener != null) byteListener.onProgress(existing, existing);
                    if (fractionListener != null) fractionListener.onProgress(1f);
                    return true;
                } else {
                    total = connection.getContentLengthLong();
                }
            } else {
                total = connection.getContentLengthLong();
            }

            InputStream input = connection.getInputStream();
            OutputStream output = new FileOutputStream(file.getAbsolutePath(), append);

            byte[] data = new byte[8192];
            long downloaded = readTotal;
            float lastReported = -2f;

            int count;
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
                downloaded += count;

                if (byteListener != null) byteListener.onProgress(downloaded, total);
                if (fractionListener != null) {
                    float fraction = total > 0 ? (float) downloaded / (float) total : -1f;
                    if (fraction < 0f || fraction - lastReported >= 0.01f || fraction >= 1f) {
                        lastReported = fraction;
                        fractionListener.onProgress(fraction);
                    }
                }
            }

            output.flush();
            output.close();
            input.close();
            if (fractionListener != null) fractionListener.onProgress(1f);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static long getFileSize(String address) {
        try {
            URL url = new URL(address);
            URLConnection connection = url.openConnection();
            connection.connect();
            return connection.getContentLengthLong();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    public static String downloadString(String address) {
        try {
            URL url = new URL(address);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);

            InputStream input = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}