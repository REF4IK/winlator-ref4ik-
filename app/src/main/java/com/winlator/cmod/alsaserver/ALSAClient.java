package com.winlator.cmod.alsaserver;

import android.util.Log;
import com.winlator.cmod.sysvshm.SysVSharedMemory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ALSAClient {
    public enum DataType {
        U8(1), S16LE(2), S16BE(2), FLOATLE(4), FLOATBE(4);
        public final byte byteCount;

        DataType(int byteCount) {
            this.byteCount = (byte)byteCount;
        }
    }
    private DataType dataType = DataType.U8;
    private byte channelCount = 2;
    private int sampleRate = 0;
    private int position;
    private int bufferSize;
    private int frameBytes;
    private ByteBuffer sharedBuffer;
    private boolean playing = false;
    private long streamPtr = 0;
    private long mirrorStreamPtr = 0;
    private boolean reflectorMode = false;
    private static final String TAG = "ALSAClient";

    static {
        System.loadLibrary("winlator");
    }

    public void release() {
        if (sharedBuffer != null) {
            SysVSharedMemory.unmapSHMSegment(sharedBuffer, sharedBuffer.capacity());
            sharedBuffer = null;
        }

        stop(streamPtr);
        close(streamPtr);
        
        if (reflectorMode && mirrorStreamPtr != 0) {
            stop(mirrorStreamPtr);
            close(mirrorStreamPtr);
            mirrorStreamPtr = 0;
        }
        
        playing = false;
        streamPtr = 0;
    }

    public void prepare() {
        position = 0;
        frameBytes = channelCount * dataType.byteCount;
        release();

        if (!isValidBufferSize()) return;

        if (reflectorMode) {
            Log.d(TAG, "Creating ALSA-Reflector streams: pacer + mirror");
            // Create virtual pacer stream (silent, for timing)
            streamPtr = createVirtualStream(dataType.ordinal(), channelCount, sampleRate, bufferSize);
            // Create real hardware mirror stream
            mirrorStreamPtr = create(dataType.ordinal(), channelCount, sampleRate, bufferSize);
            
            if (streamPtr > 0 && mirrorStreamPtr > 0) {
                Log.d(TAG, "ALSA-Reflector: Both streams created successfully");
                start();
            } else {
                Log.e(TAG, "ALSA-Reflector: Failed to create streams");
            }
        } else {
            streamPtr = create(dataType.ordinal(), channelCount, sampleRate, bufferSize);
            if (streamPtr > 0) start();
        }
    }

    public void start() {
        if (streamPtr > 0 && !playing) {
            start(streamPtr);
            
            if (reflectorMode && mirrorStreamPtr > 0) {
                start(mirrorStreamPtr);
                Log.d(TAG, "ALSA-Reflector: Started both pacer and mirror streams");
            }
            
            playing = true;
        }
    }

    public void stop() {
        if (streamPtr > 0 && playing) {
            stop(streamPtr);
            
            if (reflectorMode && mirrorStreamPtr > 0) {
                stop(mirrorStreamPtr);
                Log.d(TAG, "ALSA-Reflector: Stopped both streams");
            }
            
            playing = false;
        }
    }

    public void pause() {
        if (streamPtr > 0) {
            pause(streamPtr);
            
            if (reflectorMode && mirrorStreamPtr > 0) {
                pause(mirrorStreamPtr);
            }
            
            playing = false;
        }
    }

    public void drain() {
        if (streamPtr > 0) {
            flush(streamPtr);
            
            if (reflectorMode && mirrorStreamPtr > 0) {
                flush(mirrorStreamPtr);
            }
        }
    }

    public void writeDataToStream(ByteBuffer data) {
        if (dataType == DataType.S16LE || dataType == DataType.FLOATLE) {
            data.order(ByteOrder.LITTLE_ENDIAN);
        }
        else if (dataType == DataType.S16BE || dataType == DataType.FLOATBE) {
            data.order(ByteOrder.BIG_ENDIAN);
        }

        if (playing) {
            int numFrames = data.limit() / frameBytes;
            
            if (reflectorMode) {
                // Write to virtual pacer stream (for timing)
                int framesWritten = writeVirtual(streamPtr, data, numFrames);
                
                // Mirror to real hardware stream
                if (mirrorStreamPtr > 0) {
                    data.rewind();
                    write(mirrorStreamPtr, data, numFrames);
                }
                
                if (framesWritten > 0) position += framesWritten;
            } else {
                int framesWritten = write(streamPtr, data, numFrames);
                if (framesWritten > 0) position += framesWritten;
            }
            
            data.rewind();
        }
    }

    public int pointer() {
        return position;
    }

    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public void setChannelCount(int channelCount) {
        this.channelCount = (byte)channelCount;
    }

    public void setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public ByteBuffer getSharedBuffer() {
        return sharedBuffer;
    }

    public void setSharedBuffer(ByteBuffer sharedBuffer) {
        this.sharedBuffer = sharedBuffer;
    }

    public DataType getDataType() {
        return dataType;
    }

    public byte getChannelCount() {
        return channelCount;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getBufferSizeInBytes() {
        return bufferSize * frameBytes;
    }

    private boolean isValidBufferSize() {
        return (getBufferSizeInBytes() % frameBytes == 0) && bufferSize > 0;
    }

    public int computeLatencyMillis() {
        return (int)(((float)bufferSize / sampleRate) * 1000);
    }

    public void setReflectorMode(boolean reflectorMode) {
        this.reflectorMode = reflectorMode;
        Log.d(TAG, "ALSA-Reflector mode: " + (reflectorMode ? "enabled" : "disabled"));
    }

    public boolean isReflectorMode() {
        return reflectorMode;
    }

    public void recoverStream() {
        if (reflectorMode && mirrorStreamPtr > 0) {
            Log.d(TAG, "ALSA-Reflector: Attempting stream recovery");
            stop(mirrorStreamPtr);
            close(mirrorStreamPtr);
            
            // Recreate mirror stream
            mirrorStreamPtr = create(dataType.ordinal(), channelCount, sampleRate, bufferSize);
            if (mirrorStreamPtr > 0 && playing) {
                start(mirrorStreamPtr);
                Log.d(TAG, "ALSA-Reflector: Stream recovery successful");
            } else {
                Log.e(TAG, "ALSA-Reflector: Stream recovery failed");
            }
        }
    }

    private native long create(int format, byte channelCount, int sampleRate, int bufferSize);

    private native long createVirtualStream(int format, byte channelCount, int sampleRate, int bufferSize);

    private native int write(long streamPtr, ByteBuffer buffer, int numFrames);

    private native int writeVirtual(long streamPtr, ByteBuffer buffer, int numFrames);

    private native void start(long streamPtr);

    private native void stop(long streamPtr);

    private native void pause(long streamPtr);

    private native void flush(long streamPtr);

    private native void close(long streamPtr);
}
