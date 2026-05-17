package com.winlator.cmod.alsaserver;

import android.util.Log;
import com.winlator.cmod.xconnector.Client;
import com.winlator.cmod.xconnector.ConnectionHandler;

public class ALSAClientConnectionHandler implements ConnectionHandler {
    private boolean reflectorMode = false;
    private static final String TAG = "ALSAConnectionHandler";
    
    public ALSAClientConnectionHandler() {
        this(false);
    }
    
    public ALSAClientConnectionHandler(boolean reflectorMode) {
        this.reflectorMode = reflectorMode;
        Log.d(TAG, "Created ALSA connection handler, reflector mode: " + reflectorMode);
    }
    @Override
    public void handleNewConnection(Client client) {
        client.createIOStreams();
        ALSAClient alsaClient = new ALSAClient();
        alsaClient.setReflectorMode(reflectorMode);
        client.setTag(alsaClient);
        
        Log.d(TAG, "New ALSA client connection established, reflector mode: " + reflectorMode);
    }

    @Override
    public void handleConnectionShutdown(Client client) {
        ALSAClient alsaClient = (ALSAClient)client.getTag();
        if (alsaClient != null) {
            alsaClient.release();
            Log.d(TAG, "ALSA client connection shutdown, reflector mode was: " + alsaClient.isReflectorMode());
        }
    }
    
    public void setReflectorMode(boolean reflectorMode) {
        this.reflectorMode = reflectorMode;
        Log.d(TAG, "Updated reflector mode: " + reflectorMode);
    }
    
    public boolean isReflectorMode() {
        return reflectorMode;
    }
}
