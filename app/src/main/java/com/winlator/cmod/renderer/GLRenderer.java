package com.winlator.cmod.renderer;



import android.content.Context;

import android.graphics.Bitmap;

import android.graphics.BitmapFactory;

import android.opengl.GLES20;

import android.opengl.GLSurfaceView;

import android.util.Log;



import com.winlator.cmod.R;

import com.winlator.cmod.XrActivity;

import com.winlator.cmod.core.HDRDisplayManager;

import com.winlator.cmod.core.HDRConfiguration;

import com.winlator.cmod.core.HDRSurfaceConfiguration;

import com.winlator.cmod.core.HDR10MetadataHandler;

import com.winlator.cmod.core.HDRColorUtils;

import com.winlator.cmod.math.Mathf;

import com.winlator.cmod.math.XForm;

import com.winlator.cmod.core.ImageUtils;

import com.winlator.cmod.renderer.material.CursorMaterial;

import com.winlator.cmod.renderer.material.ScreenMaterial;

import com.winlator.cmod.renderer.material.ShaderMaterial;

import com.winlator.cmod.renderer.material.WindowMaterial;

import com.winlator.cmod.widget.XServerView;

import com.winlator.cmod.xserver.Bitmask;

import com.winlator.cmod.xserver.Cursor;

import com.winlator.cmod.xserver.Drawable;

import com.winlator.cmod.xserver.Pointer;

import com.winlator.cmod.xserver.Window;

import com.winlator.cmod.xserver.WindowAttributes;

import com.winlator.cmod.xserver.WindowManager;

import com.winlator.cmod.xserver.XLock;

import com.winlator.cmod.xserver.XServer;



import java.util.ArrayList;



import javax.microedition.khronos.egl.EGLConfig;

import javax.microedition.khronos.opengles.GL10;



public class GLRenderer implements GLSurfaceView.Renderer, WindowManager.OnWindowModificationListener, Pointer.OnPointerMotionListener {

    private static final long GL_ERROR_CHECK_INTERVAL_MS = 2000L;

    public final XServerView xServerView;

    private final XServer xServer;

    public final VertexAttribute quadVertices = new VertexAttribute("position", 2);

    private final float[] tmpXForm1 = XForm.getInstance();

    private final float[] tmpXForm2 = XForm.getInstance();

    private final CursorMaterial cursorMaterial = new CursorMaterial();

    private final WindowMaterial windowMaterial = new WindowMaterial();

    public final ViewTransformation viewTransformation = new ViewTransformation();

    private final Drawable rootCursorDrawable;

    private final ArrayList<RenderableWindow> renderableWindows = new ArrayList<>();

    private String forceFullscreenWMClass = null;

    private boolean fullscreen = false;

    private boolean toggleFullscreen = false;

    public boolean viewportNeedsUpdate = true;

    private boolean cursorVisible = true;

    private boolean rootWindowDownsized = false;

    private boolean screenOffsetYRelativeToCursor = false;

    private String[] unviewableWMClasses = null;

    private float magnifierZoom = 1.0f;

    private boolean magnifierEnabled = true;

    public int surfaceWidth;

    public int surfaceHeight;

    private final EffectComposer effectComposer;

    

    // HDR rendering components

    private HDRDisplayManager hdrDisplayManager;

    private HDRConfiguration hdrConfiguration;

    private HDRSurfaceConfiguration hdrSurfaceConfiguration;

    private HDR10MetadataHandler hdr10MetadataHandler;

    private boolean hdrRenderingEnabled = false;

    private int hdrFramebuffer = 0;

    private int hdrColorTexture = 0;

    private boolean hdrInitialized = false;

    private long lastGlErrorCheckTime = 0L;



    public GLRenderer(XServerView xServerView, XServer xServer) {

        this.xServerView = xServerView;

        this.xServer = xServer;

        this.effectComposer = new EffectComposer(this);

        rootCursorDrawable = createRootCursorDrawable();



        quadVertices.put(new float[]{

            0.0f, 0.0f,

            0.0f, 1.0f,

            1.0f, 0.0f,

            1.0f, 1.0f

        });



        xServer.windowManager.addOnWindowModificationListener(this);

        xServer.pointer.addOnPointerMotionListener(this);

        

        // Initialize HDR components

        initializeHDR();

    }

    

    /**

     * Initialize HDR rendering components

     */

    private void initializeHDR() {

        try {

            hdrDisplayManager = xServerView.getHdrDisplayManager();

            hdrConfiguration = xServerView.getHdrConfiguration();

            hdrSurfaceConfiguration = xServerView.getHdrSurfaceConfiguration();



            if (hdrDisplayManager != null && hdrConfiguration != null && hdrSurfaceConfiguration != null) {

                Log.d("GLRenderer", "HDR10 display detected, initializing HDR components");



                // Create metadata handler

                hdr10MetadataHandler = new HDR10MetadataHandler(

                    hdrDisplayManager.getPrimaryDisplayInfo()

                );

                

                // Enable HDR rendering if available

                hdrRenderingEnabled = hdrSurfaceConfiguration.isHDRRenderingAvailable();

                

                Log.d("GLRenderer", "HDR initialization complete. Enabled: " + hdrRenderingEnabled);

                Log.d("GLRenderer", hdrSurfaceConfiguration.getCapabilitySummary());

                Log.d("GLRenderer", hdr10MetadataHandler.getMetadataSummary());

            } else {

                Log.d("GLRenderer", "HDR10 not supported on this display");

            }

        } catch (Exception e) {

            Log.e("GLRenderer", "Failed to initialize HDR components", e);

            hdrRenderingEnabled = false;

        }

    }



    @Override

    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        GPUImage.checkIsSupported();



        GLES20.glFrontFace(GLES20.GL_CCW);

        GLES20.glDisable(GLES20.GL_CULL_FACE);



        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        GLES20.glDepthMask(false);



        GLES20.glEnable(GLES20.GL_BLEND);

        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

    }



    @Override

    public void onSurfaceChanged(GL10 gl, int width, int height) {

        if (XrActivity.isEnabled(null)) {

            XrActivity activity = XrActivity.getInstance();

            activity.init();

            width = activity.getWidth();

            height = activity.getHeight();

            magnifierEnabled = false;

        }



        GLES20.glViewport(0, 0, width, height);



        surfaceWidth = width;

        surfaceHeight = height;

        viewTransformation.update(width, height, xServer.screenInfo.width, xServer.screenInfo.height);

        viewportNeedsUpdate = true;

        

        // Initialize HDR framebuffer if needed

        if (hdrRenderingEnabled && !hdrInitialized) {

            initializeHDRFramebuffer(width, height);

        }

    }



    @Override

    public void onDrawFrame(GL10 gl) {

        xServerView.onFrameStarted();

        if (toggleFullscreen) {

            fullscreen = !fullscreen;

            toggleFullscreen = false;

            viewportNeedsUpdate = true;

        }



        drawFrame();

    }



    public void drawFrame() {

        boolean xrFrame = false;

        boolean xrImmersive = false;

        if (XrActivity.isEnabled(null)) {

            xrImmersive = XrActivity.getImmersive();

            xrFrame = XrActivity.getInstance().beginFrame(xrImmersive, XrActivity.getSBS());

        }



        if (effectComposer.hasEffects()) {

            effectComposer.render(xrImmersive);

        } else {

            renderScene(xrImmersive);

        }

        

        // Apply HDR tone mapping if enabled

        if (hdrRenderingEnabled && hdrInitialized) {

            applyHDRProcessing();

        }



        // Finalize XR frame if supported

        if (xrFrame) {

            XrActivity.getInstance().endFrame();

            XrActivity.updateControllers();

            xServerView.requestRender();

        }

    }



    void renderScene(boolean forceFullscreen) {

        if (viewportNeedsUpdate && magnifierEnabled) {

            if (fullscreen) {

                GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);

            }

            else {

                GLES20.glViewport(viewTransformation.viewOffsetX, viewTransformation.viewOffsetY, viewTransformation.viewWidth, viewTransformation.viewHeight);

            }

            viewportNeedsUpdate = false;

        }



        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);



        if (magnifierEnabled) {

            float pointerX = 0;

            float pointerY = 0;

            float magnifierZoom = !screenOffsetYRelativeToCursor ? this.magnifierZoom : 1.0f;



            if (magnifierZoom != 1.0f) {

                pointerX = Mathf.clamp(xServer.pointer.getX() * magnifierZoom - xServer.screenInfo.width * 0.5f, 0, xServer.screenInfo.width * Math.abs(1.0f - magnifierZoom));

            }



            if (screenOffsetYRelativeToCursor || magnifierZoom != 1.0f) {

                float scaleY = magnifierZoom != 1.0f ? Math.abs(1.0f - magnifierZoom) : 0.5f;

                float offsetY = xServer.screenInfo.height * (screenOffsetYRelativeToCursor ? 0.25f : 0.5f);

                pointerY = Mathf.clamp(xServer.pointer.getY() * magnifierZoom - offsetY, 0, xServer.screenInfo.height * scaleY);

            }



            XForm.makeTransform(tmpXForm2, -pointerX, -pointerY, magnifierZoom, magnifierZoom, 0);

        } else {

            if (!fullscreen) {

                int pointerY = 0;

                if (screenOffsetYRelativeToCursor) {

                    short halfScreenHeight = (short)(xServer.screenInfo.height / 2);

                    pointerY = Mathf.clamp(xServer.pointer.getY() - halfScreenHeight / 2, 0, halfScreenHeight);

                }



                XForm.makeTransform(tmpXForm2, viewTransformation.sceneOffsetX, viewTransformation.sceneOffsetY - pointerY, viewTransformation.sceneScaleX, viewTransformation.sceneScaleY, 0);



                GLES20.glEnable(GLES20.GL_SCISSOR_TEST);

                GLES20.glScissor(viewTransformation.viewOffsetX, viewTransformation.viewOffsetY, viewTransformation.viewWidth, viewTransformation.viewHeight);

            } else {

                XForm.identity(tmpXForm2);

            }

        }



        renderWindows(forceFullscreen);



        if (cursorVisible && !rootWindowDownsized) renderCursor();



        if (!magnifierEnabled && !fullscreen) {

            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        }

    }





    @Override

    public void onMapWindow(Window window) {

        xServerView.queueEvent(this::updateScene);

        xServerView.requestRender();

    }



    @Override

    public void onUnmapWindow(Window window) {

        xServerView.queueEvent(this::updateScene);

        xServerView.requestRender();

    }



    @Override

    public void onChangeWindowZOrder(Window window) {

        xServerView.queueEvent(this::updateScene);

        xServerView.requestRender();

    }



    @Override

    public void onUpdateWindowContent(Window window) {

        xServerView.requestRender();

    }



    @Override

    public void onUpdateWindowGeometry(final Window window, boolean resized) {

        if (resized) {

            xServerView.queueEvent(this::updateScene);

        }

        else xServerView.queueEvent(() -> updateWindowPosition(window));

        xServerView.requestRender();

    }



    @Override

    public void onUpdateWindowAttributes(Window window, Bitmask mask) {

        if (mask.isSet(WindowAttributes.FLAG_CURSOR)) xServerView.requestRender();

    }



    @Override

    public void onPointerMove(short x, short y) {

        xServerView.requestRender();

    }



    private void renderDrawable(Drawable drawable, int x, int y, ShaderMaterial material) {

        renderDrawable(drawable, x, y, material, false);

    }



    private void renderDrawable(Drawable drawable, int x, int y, ShaderMaterial material, boolean forceFullscreen) {

        if (drawable == null) return;

        Texture texture;
        boolean isGPUImage;
        short dw, dh;

        // Hold lock only during texture data upload — release before GL draw commands
        synchronized (drawable.renderLock) {

            texture = drawable.getTexture();
            dw = drawable.width;
            dh = drawable.height;

            // Unlock AHardwareBuffer before GPU read to avoid data race
            isGPUImage = texture instanceof GPUImage;
            if (isGPUImage) ((GPUImage)texture).unlockForRender();

            texture.updateFromDrawable(drawable);

        }
        // Lock released — X clients can now write new pixel data while GPU renders

        if (forceFullscreen) {

            short newHeight = (short)Math.min(xServer.screenInfo.height, ((float)xServer.screenInfo.width / dw) * dh);

            short newWidth = (short)(((float)newHeight / dh) * dw);

            XForm.set(tmpXForm1, (xServer.screenInfo.width - newWidth) * 0.5f, (xServer.screenInfo.height - newHeight) * 0.5f, newWidth, newHeight);

        }

        else XForm.set(tmpXForm1, x, y, dw, dh);



        XForm.multiply(tmpXForm1, tmpXForm1, tmpXForm2);



        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());

        GLES20.glUniform1i(material.getUniformLocation("texture"), 0);

        GLES20.glUniform1fv(material.getUniformLocation("xform"), tmpXForm1.length, tmpXForm1, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        // Re-lock AHardwareBuffer for CPU write after GPU is done reading
        if (isGPUImage) ((GPUImage)texture).relockForWrite();

    }



    private void renderWindows(boolean forceFullscreen) {

        windowMaterial.use();

        GLES20.glUniform2f(windowMaterial.getUniformLocation("viewSize"), xServer.screenInfo.width, xServer.screenInfo.height);

        quadVertices.bind(windowMaterial.programId);



        boolean singleWindow = forceFullscreen;

        try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {

            rootWindowDownsized = false;

            if (fullscreen && !renderableWindows.isEmpty()) {

                RenderableWindow root = renderableWindows.get(0);

                if ((root.content.width < xServer.screenInfo.width) || (root.content.height < xServer.screenInfo.height)) {

                    rootWindowDownsized = true;

                    singleWindow = true;

                }

            }

            if (singleWindow && !renderableWindows.isEmpty()) {

                RenderableWindow window = renderableWindows.get(renderableWindows.size() - 1);

                renderDrawable(window.content, window.rootX, window.rootY, windowMaterial, true);

            } else {

                for (RenderableWindow window : renderableWindows) {

                    renderDrawable(window.content, window.rootX, window.rootY, windowMaterial, window.forceFullscreen);

                }

            }

        }



        quadVertices.disable();



        if (Log.isLoggable("GLRenderer", Log.VERBOSE)) {

            long now = android.os.SystemClock.elapsedRealtime();

            if (now - lastGlErrorCheckTime >= GL_ERROR_CHECK_INTERVAL_MS) {

                lastGlErrorCheckTime = now;

                int error = GLES20.glGetError();

                if (error != GLES20.GL_NO_ERROR) {

                    Log.e("GLRenderer", "OpenGL Error: " + error);

                }

            }

        }



    }



    private void renderCursor() {

        cursorMaterial.use();

        GLES20.glUniform2f(cursorMaterial.getUniformLocation("viewSize"), xServer.screenInfo.width, xServer.screenInfo.height);

        quadVertices.bind(cursorMaterial.programId);



        try (XLock lock = xServer.lock(XServer.Lockable.DRAWABLE_MANAGER)) {

            Window pointWindow = xServer.inputDeviceManager.getPointWindow();

            Cursor cursor = pointWindow != null ? pointWindow.attributes.getCursor() : null;

            short x = xServer.pointer.getClampedX();

            short y = xServer.pointer.getClampedY();



            if (cursor != null) {

                if (cursor.isVisible()) renderDrawable(cursor.cursorImage, x - cursor.hotSpotX, y - cursor.hotSpotY, cursorMaterial);

            }

            else renderDrawable(rootCursorDrawable, x, y, cursorMaterial);

        }



        quadVertices.disable();

    }



    public void toggleFullscreen() {

        toggleFullscreen = true;

        xServerView.requestRender();

    }



    private Drawable createRootCursorDrawable() {

        Context context = xServerView.getContext();

        BitmapFactory.Options options = new BitmapFactory.Options();

        options.inScaled = false;

        Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.cursor, options);

        return Drawable.fromBitmap(bitmap);

    }



    private void updateScene() {

        try (XLock lock = xServer.lock(XServer.Lockable.WINDOW_MANAGER, XServer.Lockable.DRAWABLE_MANAGER)) {

            renderableWindows.clear();

            collectRenderableWindows(xServer.windowManager.rootWindow, xServer.windowManager.rootWindow.getX(), xServer.windowManager.rootWindow.getY());

        }

    }



    private void collectRenderableWindows(Window window, int x, int y) {

        if (!window.attributes.isMapped()) return;

        if (window != xServer.windowManager.rootWindow) {

            boolean viewable = true;



            if (unviewableWMClasses != null) {

                String wmClass = window.getClassName();

                for (String unviewableWMClass : unviewableWMClasses) {

                    if (wmClass.contains(unviewableWMClass)) {

                        if (window.attributes.isEnabled()) window.disableAllDescendants();

                        viewable = false;

                        break;

                    }

                }

            }



            if (viewable) {

                if (forceFullscreenWMClass != null) {

                    short width = window.getWidth();

                    short height = window.getHeight();

                    boolean forceFullscreen= false;



                    if (width >= 320 && height >= 200 && width < xServer.screenInfo.width && height < xServer.screenInfo.height) {

                        Window parent = window.getParent();

                        boolean parentHasWMClass = parent.getClassName().contains(forceFullscreenWMClass);

                        boolean hasWMClass = window.getClassName().contains(forceFullscreenWMClass);

                        if (hasWMClass) {

                            forceFullscreen = !parentHasWMClass && window.getChildCount() == 0;

                        }

                        else {

                            short borderX = (short)(parent.getWidth() - width);

                            short borderY = (short)(parent.getHeight() - height);

                            if (parent.getChildCount() == 1 && borderX > 0 && borderY > 0 && borderX <= 12) {

                                forceFullscreen = true;

                                removeRenderableWindow(parent);

                            }

                        }

                    }



                    renderableWindows.add(new RenderableWindow(window.getContent(), x, y, forceFullscreen));

                }

                else renderableWindows.add(new RenderableWindow(window.getContent(), x, y));

            }

        }



        for (Window child : window.getChildren()) {

            collectRenderableWindows(child, child.getX() + x, child.getY() + y);

        }

    }



    private void removeRenderableWindow(Window window) {

        for (int i = 0; i < renderableWindows.size(); i++) {

            if (renderableWindows.get(i).content == window.getContent()) {

                renderableWindows.remove(i);

                break;

            }

        }

    }



    private void updateWindowPosition(Window window) {

        for (RenderableWindow renderableWindow : renderableWindows) {

            if (renderableWindow.content == window.getContent()) {

                renderableWindow.rootX = window.getRootX();

                renderableWindow.rootY = window.getRootY();

                break;

            }

        }

    }



    public void setCursorVisible(boolean cursorVisible) {

        this.cursorVisible = cursorVisible;

        xServerView.requestRender();

    }



    public boolean isCursorVisible() {

        return cursorVisible;

    }



    public boolean isScreenOffsetYRelativeToCursor() {

        return screenOffsetYRelativeToCursor;

    }



    public void setScreenOffsetYRelativeToCursor(boolean screenOffsetYRelativeToCursor) {

        this.screenOffsetYRelativeToCursor = screenOffsetYRelativeToCursor;

        xServerView.requestRender();

    }



    public String getForceFullscreenWMClass() {

        return forceFullscreenWMClass;

    }



    public void setForceFullscreenWMClass(String forceFullscreenWMClass) {

        this.forceFullscreenWMClass = forceFullscreenWMClass;

    }



    public String[] getUnviewableWMClasses() {

        return unviewableWMClasses;

    }



    public void setUnviewableWMClasses(String... unviewableWMNames) {

        this.unviewableWMClasses = unviewableWMNames;

    }



    public boolean isFullscreen() {

        return fullscreen;

    }



    public float getMagnifierZoom() {

        return magnifierZoom;

    }



    public void setMagnifierZoom(float magnifierZoom) {

        this.magnifierZoom = magnifierZoom;

        xServerView.requestRender();

    }



    public int getSurfaceWidth() {

        return surfaceWidth;

    }



    public int getXServerWidth() {

        return xServer.screenInfo.width;

    }



    public int getXServerHeight() {

        return xServer.screenInfo.height;

    }



    public int getSurfaceHeight() {

        return surfaceHeight;

    }



    public boolean isViewportNeedsUpdate() {

        return viewportNeedsUpdate;

    }



    public void setViewportNeedsUpdate(boolean viewportNeedsUpdate) {

        this.viewportNeedsUpdate = viewportNeedsUpdate;

    }



    public VertexAttribute getQuadVertices() {

        return quadVertices;

    }



    public EffectComposer getEffectComposer (){

        return effectComposer;

    }



    private void renderWindowEffect(Drawable drawable, int x, int y, ShaderMaterial material) {

        // Implement the rendering effect logic here

        synchronized (drawable.renderLock) {

            Texture texture = drawable.getTexture();

            texture.updateFromDrawable(drawable);



            XForm.set(tmpXForm1, x, y, drawable.width, drawable.height);

            XForm.multiply(tmpXForm1, tmpXForm1, tmpXForm2);



            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());

            if (GLES20.glIsTexture(texture.getTextureId()) == false) {

                Log.e("GLRenderer", "Invalid texture binding!");

            }



            GLES20.glUniform1i(material.getUniformLocation("texture"), 0);

            GLES20.glUniform1fv(material.getUniformLocation("xform"), tmpXForm1.length, tmpXForm1, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        }

    }



    /**

     * Take a screenshot of a window

     * @param content The drawable content of the window

     * @param callback Callback to receive the screenshot bitmap

     */

    public void takeWindowScreenshot(final Drawable content, final ScreenshotCallback callback) {

        xServerView.queueEvent(() -> {

            synchronized (content.renderLock) {

                try {

                    Texture texture = content.getTexture();

                    texture.updateFromDrawable(content);

                    

                    // Scale to max 256px height for screenshot

                    int[] framebufferSize = ImageUtils.getScaledSize(content.width, content.height, 0, 256);

                    

                    RenderTarget renderTarget = new RenderTarget();

                    renderTarget.allocateFramebuffer(framebufferSize[0], framebufferSize[1]);

                    

                    // Bind framebuffer and setup viewport

                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, renderTarget.getFramebuffer());

                    GLES20.glViewport(0, 0, framebufferSize[0], framebufferSize[1]);

                    viewportNeedsUpdate = true;

                    

                    // Clear and render texture to framebuffer

                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

                    

                    ScreenMaterial material = new ScreenMaterial();

                    material.use();

                    quadVertices.bind(material.programId);

                    

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture.getTextureId());

                    material.setUniformInt("screenTexture", 0);

                    

                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, quadVertices.count());

                    

                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

                    quadVertices.disable();

                    

                    // Read pixels from framebuffer

                    int[] colors = getPixelsARGB(0, 0, framebufferSize[0], framebufferSize[1], true);

                    Bitmap bitmap = Bitmap.createBitmap(colors, framebufferSize[0], framebufferSize[1], Bitmap.Config.ARGB_8888);

                    

                    // Cleanup

                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

                    renderTarget.destroy();

                    material.destroy();

                    

                    // Return bitmap through callback

                    if (callback != null) {

                        callback.onScreenshotTaken(bitmap);

                    }

                } catch (Exception e) {

                    e.printStackTrace();

                    if (callback != null) {

                        callback.onScreenshotTaken(null);

                    }

                }

            }

        });

        xServerView.requestRender();

    }

    

    /**

     * Read pixels from current framebuffer in ARGB format

     */

    private int[] getPixelsARGB(int x, int y, int width, int height, boolean flipY) {

        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(width * height * 4);

        buffer.order(java.nio.ByteOrder.nativeOrder());

        

        GLES20.glReadPixels(x, y, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

        

        int[] pixels = new int[width * height];

        buffer.rewind();

        

        for (int i = 0; i < pixels.length; i++) {

            int r = buffer.get() & 0xFF;

            int g = buffer.get() & 0xFF;

            int b = buffer.get() & 0xFF;

            int a = buffer.get() & 0xFF;

            

            pixels[i] = (a << 24) | (r << 16) | (g << 8) | b;

        }

        

        // Flip vertically if needed (OpenGL is bottom-up)

        if (!flipY) {

            int[] flipped = new int[pixels.length];

            for (int row = 0; row < height; row++) {

                System.arraycopy(pixels, row * width, flipped, (height - 1 - row) * width, width);

            }

            return flipped;

        }

        

        return pixels;

    }



    /**

     * Callback interface for window screenshots

     */

    @FunctionalInterface

    public interface ScreenshotCallback {

        void onScreenshotTaken(Bitmap bitmap);

    }

    

    /**

     * Initialize HDR framebuffer for HDR10 rendering

     */

    private void initializeHDRFramebuffer(int width, int height) {

        if (hdrSurfaceConfiguration == null) {

            return;

        }

        

        // Generate HDR framebuffer

        int[] framebuffers = new int[1];

        GLES20.glGenFramebuffers(1, framebuffers, 0);

        hdrFramebuffer = framebuffers[0];

        

        // Generate HDR color texture

        int[] textures = new int[1];

        GLES20.glGenTextures(1, textures, 0);

        hdrColorTexture = textures[0];

        

        // Configure HDR texture

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, hdrColorTexture);

        

        // Set texture parameters

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        

        // Allocate HDR texture storage

        int internalFormat = hdrSurfaceConfiguration.getHDRFramebufferFormat();

        int format = hdrSurfaceConfiguration.getHDRTextureFormat();

        int type = hdrSurfaceConfiguration.getHDRTextureType();

        

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);

        

        // Attach texture to framebuffer

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, hdrFramebuffer);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, hdrColorTexture, 0);

        

        // Check framebuffer completeness

        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);

        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {

            Log.e("GLRenderer", "HDR framebuffer not complete: " + status);

            cleanupHDRFramebuffer();

            hdrRenderingEnabled = false;

            return;

        }

        

        // Restore default framebuffer

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        

        hdrInitialized = true;

        Log.d("GLRenderer", "HDR framebuffer initialized: " + width + "x" + height + ", format=" + internalFormat);

    }

    

    /**

     * Apply HDR processing pipeline

     */

    private void applyHDRProcessing() {

        if (!hdrInitialized || hdrConfiguration == null) {

            return;

        }

        

        // Apply tone mapping based on configuration

        switch (hdrConfiguration.getToneMapping()) {

            case HDRConfiguration.TONE_MAPPING_REINHARD:

                applyReinhardToneMapping();

                break;

            case HDRConfiguration.TONE_MAPPING_HABLE:

                applyHableToneMapping();

                break;

            case HDRConfiguration.TONE_MAPPING_ACES:

                applyACESToneMapping();

                break;

            case HDRConfiguration.TONE_MAPPING_AUTO:

                applyAutoToneMapping();

                break;

            default:

                // No tone mapping

                break;

        }

        

        // Apply color space conversion if needed

        if (hdrConfiguration.getColorSpace() != HDRDisplayManager.COLOR_SPACE_SRGB) {

            applyColorSpaceConversion();

        }

        

        // Apply HDR10 metadata

        if (hdr10MetadataHandler != null) {

            updateHDR10Metadata();

        }

    }

    

    /**

     * Apply Reinhard tone mapping

     */

    private void applyReinhardToneMapping() {

        // This would typically be done with a shader

        // For now, we'll just log that it's being applied

        Log.v("GLRenderer", "Applying Reinhard tone mapping");

    }

    

    /**

     * Apply Hable (Uncharted) tone mapping

     */

    private void applyHableToneMapping() {

        Log.v("GLRenderer", "Applying Hable tone mapping");

    }

    

    /**

     * Apply ACES tone mapping

     */

    private void applyACESToneMapping() {

        Log.v("GLRenderer", "Applying ACES tone mapping");

    }

    

    /**

     * Apply automatic tone mapping based on content

     */

    private void applyAutoToneMapping() {

        // Analyze current frame luminance and choose appropriate tone mapping

        Log.v("GLRenderer", "Applying automatic tone mapping");

    }

    

    /**

     * Apply color space conversion

     */

    private void applyColorSpaceConversion() {

        int targetColorSpace = hdrConfiguration.getColorSpace();

        Log.v("GLRenderer", "Converting to color space: " + targetColorSpace);

        

        // This would involve applying color transformation matrices

        // Implementation would require custom shaders

    }

    

    /**

     * Update HDR10 metadata for current frame

     */

    private void updateHDR10Metadata() {

        if (hdr10MetadataHandler == null) {

            return;

        }

        

        // In a real implementation, you would analyze the current frame

        // to determine maximum and average luminance values

        float maxLuminance = estimateFrameMaxLuminance();

        float avgLuminance = estimateFrameAverageLuminance();

        

        hdr10MetadataHandler.updateMetadataForContent(maxLuminance, avgLuminance);

    }

    

    /**

     * Estimate maximum luminance in current frame

     */

    private float estimateFrameMaxLuminance() {

        // Placeholder implementation

        // In practice, this would analyze the rendered frame

        return hdrConfiguration.getMaxLuminance() * 0.8f;

    }

    

    /**

     * Estimate average luminance in current frame

     */

    private float estimateFrameAverageLuminance() {

        // Placeholder implementation

        return hdrConfiguration.getMaxLuminance() * 0.4f;

    }

    

    /**

     * Cleanup HDR framebuffer resources

     */

    private void cleanupHDRFramebuffer() {

        if (hdrFramebuffer != 0) {

            GLES20.glDeleteFramebuffers(1, new int[]{hdrFramebuffer}, 0);

            hdrFramebuffer = 0;

        }

        

        if (hdrColorTexture != 0) {

            GLES20.glDeleteTextures(1, new int[]{hdrColorTexture}, 0);

            hdrColorTexture = 0;

        }

        

        hdrInitialized = false;

    }

    

    /**

     * Enable or disable HDR rendering

     */

    public void setHDRRenderingEnabled(boolean enabled) {

        if (enabled && hdrDisplayManager != null && hdrDisplayManager.isHDR10Supported()) {

            hdrRenderingEnabled = true;

            if (!hdrInitialized && surfaceWidth > 0 && surfaceHeight > 0) {

                initializeHDRFramebuffer(surfaceWidth, surfaceHeight);

            }

        } else {

            hdrRenderingEnabled = false;

            cleanupHDRFramebuffer();

        }

        

        Log.d("GLRenderer", "HDR rendering enabled: " + hdrRenderingEnabled);

    }

    

    /**

     * Check if HDR rendering is enabled

     */

    public boolean isHDRRenderingEnabled() {

        return hdrRenderingEnabled;

    }

    

    /**

     * Get HDR display manager

     */

    public HDRDisplayManager getHDRDisplayManager() {

        return hdrDisplayManager;

    }

    

    /**

     * Get HDR configuration

     */

    public HDRConfiguration getHDRConfiguration() {

        return hdrConfiguration;

    }

    

    /**

     * Update HDR configuration

     */

    public void updateHDRConfiguration(HDRConfiguration newConfiguration) {

        this.hdrConfiguration = newConfiguration;

        

        if (hdrSurfaceConfiguration != null) {

            hdrSurfaceConfiguration = new HDRSurfaceConfiguration(

                hdrConfiguration, 

                hdrDisplayManager.getPrimaryDisplayInfo()

            );

        }

        

        // Reinitialize framebuffer with new configuration if needed

        if (hdrRenderingEnabled && hdrInitialized) {

            cleanupHDRFramebuffer();

            initializeHDRFramebuffer(surfaceWidth, surfaceHeight);

        }

        

        Log.d("GLRenderer", "HDR configuration updated: " + newConfiguration.toString());

    }

}

