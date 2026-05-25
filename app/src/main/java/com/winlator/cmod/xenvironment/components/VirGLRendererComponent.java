package com.winlator.cmod.xenvironment.components;

import android.opengl.EGL14;
import android.util.Log;

import androidx.annotation.Keep;

import com.winlator.cmod.renderer.Texture;
import com.winlator.cmod.xconnector.Client;
import com.winlator.cmod.xconnector.ConnectionHandler;
import com.winlator.cmod.xconnector.RequestHandler;
import com.winlator.cmod.xconnector.UnixSocketConfig;
import com.winlator.cmod.xconnector.XConnectorEpoll;
import com.winlator.cmod.xenvironment.EnvironmentComponent;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.XServer;

import java.io.IOException;

public class VirGLRendererComponent extends EnvironmentComponent implements ConnectionHandler, RequestHandler {
    private final XServer xServer;
    private final UnixSocketConfig socketConfig;
    private XConnectorEpoll connector;
    private long sharedEGLContextPtr;
    private android.opengl.EGLDisplay eglDisplay;
    private android.opengl.EGLContext eglContext;
    private android.opengl.EGLSurface eglSurface;

    static {
        System.loadLibrary("virglrenderer");
    }

    public VirGLRendererComponent(XServer xServer, UnixSocketConfig socketConfig) {
        this.xServer = xServer;
        this.socketConfig = socketConfig;
    }

    @Override
    public void start() {
        if (connector != null) return;
        connector = new XConnectorEpoll(socketConfig, this, this);
        connector.start();
    }

    @Override
    public void stop() {
        if (connector != null) {
            connector.stop();
            connector = null;
        }
    }

    @Keep
    private void killConnection(int fd) {
        connector.killConnection(connector.getClient(fd));
    }

    @Keep
    private long getSharedEGLContext() {
        if (sharedEGLContextPtr != 0) return sharedEGLContextPtr;
        // VulkanRenderer doesn't use EGL, so create a standalone EGL context
        // on a dedicated thread (similar to how GLRenderer.queueEvent worked).
        // VirGL native code needs a valid EGL context as shared context.
        final Thread callerThread = Thread.currentThread();
        final Object lock = new Object();
        Thread eglThread = new Thread(() -> {
            try {
                eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
                if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                    Log.e("VirGL", "No EGL display");
                    synchronized (lock) { lock.notify(); }
                    return;
                }
                int[] version = new int[2];
                if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                    Log.e("VirGL", "EGL init failed");
                    synchronized (lock) { lock.notify(); }
                    return;
                }

                int[] configAttribs = {
                    EGL14.EGL_RENDERABLE_TYPE, 0x40,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_NONE
                };
                android.opengl.EGLConfig[] configs = new android.opengl.EGLConfig[1];
                int[] numConfigs = new int[1];
                if (!EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
                    Log.e("VirGL", "EGL choose config failed");
                    synchronized (lock) { lock.notify(); }
                    return;
                }

                int[] contextAttribs = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE };
                eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
                if (eglContext == EGL14.EGL_NO_CONTEXT) {
                    Log.e("VirGL", "EGL create context failed");
                    synchronized (lock) { lock.notify(); }
                    return;
                }

                int[] pbufferAttribs = { EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE };
                eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, configs[0], pbufferAttribs, 0);
                if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    Log.e("VirGL", "eglMakeCurrent failed");
                    synchronized (lock) { lock.notify(); }
                    return;
                }

                sharedEGLContextPtr = getCurrentEGLContextPtr();
                Log.d("VirGL", "Shared EGL context ptr = " + sharedEGLContextPtr);
            } catch (Exception e) {
                Log.e("VirGL", "getSharedEGLContext failed: " + e.getMessage());
            } finally {
                synchronized (lock) { lock.notify(); }
            }
        }, "EGL-Init-Thread");

        eglThread.start();
        try {
            synchronized (lock) { lock.wait(5000); }
        } catch (InterruptedException e) {
            Log.e("VirGL", "Interrupted while waiting for EGL init");
        }

        return sharedEGLContextPtr;
    }

    @Override
    public void handleConnectionShutdown(Client client) {
        long clientPtr = (long)client.getTag();
        destroyClient(clientPtr);
    }

    @Override
    public void handleNewConnection(Client client) {
        getSharedEGLContext();
        long clientPtr = handleNewConnection(client.clientSocket.fd);
        client.setTag(clientPtr);
    }

    @Override
    public boolean handleRequest(Client client) throws IOException {
        long clientPtr = (long)client.getTag();
        handleRequest(clientPtr);
        return true;
    }

    @Keep
    private void flushFrontbuffer(int drawableId, int framebuffer) {
        Drawable drawable = xServer.drawableManager.getDrawable(drawableId);
        if (drawable == null) {
            Log.e("VirGLRendererComponent", "Drawable not found for drawableId=" + drawableId);
            return;
        }

        synchronized (drawable.renderLock) {
            if (framebuffer == 0) {
                Log.e("VirGLRendererComponent", "Framebuffer is invalid for drawableId=" + drawableId);
                return;
            }

            Texture texture = drawable.getTexture();
            if (texture == null) {
                Log.e("VirGLRendererComponent", "Texture is null for drawableId=" + drawableId);
                return;
            }

            // Ensure existing data is valid before resetting
            try {
                texture.copyFromFramebuffer(framebuffer, drawable.width, drawable.height);
            } catch (Exception e) {
                Log.e("VirGLRendererComponent", "Error during framebuffer copy: " + e.getMessage(), e);
                return;
            }
        }

        Runnable onDrawListener = drawable.getOnDrawListener();
        if (onDrawListener != null) {
            onDrawListener.run();
        }
    }


    private native long handleNewConnection(int fd);

    private native void handleRequest(long clientPtr);

    private native long getCurrentEGLContextPtr();

    private native void destroyClient(long clientPtr);

    private native void destroyRenderer(long clientPtr);
}
