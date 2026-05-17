package com.winlator.cmod.xenvironment.components;

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.util.Log;
import com.winlator.cmod.alsaserver.ALSAClientConnectionHandler;
import com.winlator.cmod.alsaserver.ALSARequestHandler;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xconnector.XConnectorEpoll;
import com.winlator.cmod.xenvironment.EnvironmentComponent;

public class ALSAServerComponent extends EnvironmentComponent {
    private XConnectorEpoll connector;
    private final UnixSocketConfig socketConfig;
    private final boolean reflectorMode;
    private ALSAClientConnectionHandler connectionHandler;
    private AudioDeviceCallback audioDeviceCallback;
    private static final String TAG = "ALSAServerComponent";
    
    public ALSAServerComponent(UnixSocketConfig socketConfig) {
        this(socketConfig, false);
    }
    
    public ALSAServerComponent(UnixSocketConfig socketConfig, boolean reflectorMode) {
        this.socketConfig = socketConfig;
        this.reflectorMode = reflectorMode;
        Log.d(TAG, "Created ALSA server component, reflector mode: " + reflectorMode);
    }

    @Override
    public void start() {
        if (connector != null) return;
        
        connectionHandler = new ALSAClientConnectionHandler(reflectorMode);
        connector = new XConnectorEpoll(socketConfig, connectionHandler, new ALSARequestHandler());
        connector.setMultithreadedClients(true);
        connector.start();
        
        Log.d(TAG, "ALSA server started with reflector mode: " + reflectorMode);
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }
        
        Log.d(TAG, "ALSA server stopped");
    }
    
    public void setAudioDeviceCallback(AudioDeviceCallback callback) {
        this.audioDeviceCallback = callback;
        Log.d(TAG, "Audio device callback set for reflector mode");
    }
    
    public void onAudioDeviceChange() {
        if (reflectorMode && connector != null) {
            Log.d(TAG, "Audio device change detected in reflector mode - triggering stream recovery");
            // Signal all connected clients to recover their streams
            // This will be handled by the individual ALSA clients
        }
    }
    
    public boolean isReflectorMode() {
        return reflectorMode;
    }
}
