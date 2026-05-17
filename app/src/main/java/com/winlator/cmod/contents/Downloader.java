package com.winlator.cmod.contents;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

public class Downloader {

    public interface DownloadProgressListener {
        void onProgress(long downloaded, long total);
    }

    public static boolean downloadFile(String address, File file) {
        return downloadFile(address, file, null);
    }

    public static boolean downloadFile(String address, File file, DownloadProgressListener listener) {
        try {
            URL url = new URL(address);
            URLConnection connection = url.openConnection();
            connection.connect();

            // Get file size
            long fileSize = connection.getContentLengthLong();

            // download the file
            InputStream input = url.openStream();

            // Output stream
            OutputStream output = new FileOutputStream(file.getAbsolutePath());

            byte[] data = new byte[8192];
            long downloaded = 0;

            int count;
            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
                downloaded += count;
                
                if (listener != null) {
                    listener.onProgress(downloaded, fileSize);
                }
            }

            // flushing output
            output.flush();

            // closing streams
            output.close();
            input.close();
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
            connection.connect();

            InputStream input = url.openStream();
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