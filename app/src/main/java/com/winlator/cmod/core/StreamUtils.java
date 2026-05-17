package com.winlator.cmod.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtils {
    // Increased buffer size for faster I/O operations
    public static final int BUFFER_SIZE = 128 * 1024; // Increased from 64KB to 128KB

    public static int skip(InputStream inStream, int bytesToSkip) {
        try {
            int bytesSkipped = (int) inStream.skip(bytesToSkip);
            if (bytesSkipped > 0 && bytesSkipped != bytesToSkip) {
                byte[] skipBuffer = new byte[1024];
                while (bytesSkipped != bytesToSkip) {
                    int bytesRead = inStream.read(skipBuffer, 0, Math.min(skipBuffer.length, bytesToSkip - bytesSkipped));
                    if (bytesRead == -1) break;
                    bytesSkipped += bytesRead;
                }
            }
            return bytesSkipped;
        } catch (IOException e) {
            return 0;
        }
    }

    public static byte[] copyToByteArray(InputStream inStream) {
        if (inStream == null) return new byte[0];

        ByteArrayOutputStream outStream = new ByteArrayOutputStream(BUFFER_SIZE);
        copy(inStream, outStream);
        return outStream.toByteArray();
    }

    public static boolean copy(InputStream inStream, OutputStream outStream) {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int amountRead;
            while ((amountRead = inStream.read(buffer)) != -1) {
                outStream.write(buffer, 0, amountRead);
            }
            outStream.flush();
            return true;
        }
        catch (IOException e) {
            return false;
        }
    }
}