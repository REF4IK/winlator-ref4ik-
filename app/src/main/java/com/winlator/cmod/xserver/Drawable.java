package com.winlator.cmod.xserver;

import android.graphics.Bitmap;

import com.winlator.cmod.core.Callback;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.renderer.GPUImage;
import com.winlator.cmod.renderer.NativeTexture;
import com.winlator.cmod.renderer.Texture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Drawable extends XResource {
    private static boolean DRAWABLE_FOR_ASR = false;
    public final short width;
    public final short height;
    public final Visual visual;
    private Texture texture = new Texture();
    private ByteBuffer data;
    private Runnable onDrawListener;
    private Callback<Drawable> onDestroyListener;
    public final Object renderLock = new Object();
    private boolean directScanout = false;

    // Dirty region tracking for partial texture upload
    private short dirtyX;
    private short dirtyY;
    private short dirtyWidth;
    private short dirtyHeight;
    private boolean hasDirtyRegion = false;

    public static void DRAWABLE_ASR_MODE(boolean value) {
        DRAWABLE_FOR_ASR = value;
    }

    public static boolean IS_ASR() {
        return DRAWABLE_FOR_ASR;
    }

    static {
        System.loadLibrary("winlator");
    }

    public Drawable(int id, int width, int height, Visual visual) {
        super(id);
        this.width = (short)width;
        this.height = (short)height;
        this.visual = visual;
        this.data = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
        if (this.data == null) {
            throw new IllegalStateException("Drawable.data initialized as null!");
        }
    }

    public static Drawable fromBitmap(Bitmap bitmap) {
        Drawable drawable = new Drawable(0, bitmap.getWidth(), bitmap.getHeight(), null);
        fromBitmap(bitmap, drawable.data);
        return drawable;
    }

    public Texture getTexture() {
        return texture;
    }

    public void setTexture(Texture texture) {
        if (texture instanceof GPUImage) {
            ByteBuffer vd = ((GPUImage)texture).getVirtualData();
            if (vd != null) data = vd;
        } else if (texture instanceof NativeTexture) {
            ByteBuffer vd = ((NativeTexture)texture).getVirtualData();
            if (vd != null) data = vd;
        }
        this.texture = texture;
    }

    public ByteBuffer getData() {
        return data;
    }

    public void setData(ByteBuffer data) {
        if (data == null) {
            throw new IllegalArgumentException("Attempting to set Drawable.data to null!");
        }
        this.data = data;
    }

    public void setDirectScanout(boolean value) {
        this.directScanout = value;
    }

    public boolean isDirectScanout() {
        return directScanout;
    }

    public ByteBuffer getBuffer() {
        return data;
    }

    private short getStride() {
        if (texture instanceof GPUImage) return ((GPUImage)texture).getStride();
        if (texture instanceof NativeTexture) return ((NativeTexture)texture).getStride();
        return width;
    }

    public Runnable getOnDrawListener() {
        return onDrawListener;
    }

    public void setOnDrawListener(Runnable onDrawListener) {
        this.onDrawListener = onDrawListener;
    }

    public Callback<Drawable> getOnDestroyListener() {
        return onDestroyListener;
    }

    public void setOnDestroyListener(Callback<Drawable> onDestroyListener) {
        this.onDestroyListener = onDestroyListener;
    }

    public void drawImage(short srcX, short srcY, short dstX, short dstY, short width, short height, byte depth, ByteBuffer data, short totalWidth, short totalHeight) {
        if (depth == 1) {
            drawBitmap(width, height, data, this.data);
        }
        else if (depth == 24 || depth == 32) {
            dstX = (short)Mathf.clamp(dstX, 0, this.width-1);
            dstY = (short)Mathf.clamp(dstY, 0, this.height-1);
            if ((dstX + width) > this.width) width = (short)((this.width - dstX));
            if ((dstY + height) > this.height) height = (short)((this.height - dstY));

            copyArea(srcX, srcY, dstX, dstY, width, height, totalWidth, this.getStride(), data, this.data);
        }

        this.data.rewind();
        data.rewind();

        texture.setNeedsUpdate(true);
        markDirty(dstX, dstY, width, height);
        if (onDrawListener != null) onDrawListener.run();
    }

    public ByteBuffer getImage(short x, short y, short width, short height) {
        ByteBuffer dstData = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);

        x = (short)Mathf.clamp(x, 0, this.width-1);
        y = (short)Mathf.clamp(y, 0, this.height-1);
        if ((x + width) > this.width) width = (short)(this.width - x);
        if ((y + height) > this.height) height = (short)(this.height - y);

        copyArea(x, y, (short)0, (short)0, width, height, this.getStride(), width, this.data, dstData);

        this.data.rewind();
        dstData.rewind();
        return dstData;
    }

    public void copyArea(short srcX, short srcY, short dstX, short dstY, short width, short height, Drawable drawable) {
        copyArea(srcX, srcY, dstX, dstY, width, height, drawable, GraphicsContext.Function.COPY);
    }

    private void ensureDataLocked(Drawable d) {
        Texture t = d.texture;
        if (t instanceof GPUImage) {
            GPUImage g = (GPUImage) t;
            ByteBuffer vd = g.getVirtualData();
            if (vd == null) {
                // Buffer was unlocked for scanout (presentScanout) - re-lock to make it valid
                g.lock();
                vd = g.getVirtualData();
            }
            if (vd != null && vd != d.data) d.data = vd;
            // If still null, fallback to keep old data but it will be stale -> caller will skip copy
        } else if (t instanceof NativeTexture) {
            ByteBuffer vd = ((NativeTexture) t).getVirtualData();
            if (vd != null && vd != d.data) d.data = vd;
        }
    }

    public void copyArea(short srcX, short srcY, short dstX, short dstY, short width, short height, Drawable drawable, GraphicsContext.Function gcFunction) {
        if (width <= 0 || height <= 0) return;
        if (drawable == null || this.data == null || drawable.data == null) return;

        // Lock ordering to avoid deadlock, synchronize on both renderLocks during the whole copy
        // This prevents race with VulkanRenderer/GLRenderer.presentScanout which does unlock/lock under same lock
        Drawable src = drawable;
        Drawable dst = this;
        if (src == dst) {
            synchronized (dst.renderLock) {
                copyAreaLocked(srcX, srcY, dstX, dstY, width, height, src, gcFunction);
            }
        } else {
            Object lock1 = src.renderLock;
            Object lock2 = dst.renderLock;
            Object first = System.identityHashCode(lock1) < System.identityHashCode(lock2) ? lock1 : lock2;
            Object second = first == lock1 ? lock2 : lock1;
            synchronized (first) {
                synchronized (second) {
                    copyAreaLocked(srcX, srcY, dstX, dstY, width, height, src, gcFunction);
                }
            }
        }
    }

    private void copyAreaLocked(short srcX, short srcY, short dstX, short dstY, short width, short height, Drawable drawable, GraphicsContext.Function gcFunction) {
        // Ensure GPUImage buffers are locked and data references are up-to-date (fixes 0x7c18c00000 fault)
        ensureDataLocked(drawable);
        ensureDataLocked(this);
        if (this.data == null || drawable.data == null) return;
        // If either buffer is still null after lock attempt (e.g. failed lock), skip
        if (drawable.getTexture() instanceof GPUImage && ((GPUImage)drawable.getTexture()).getVirtualData() == null) return;
        if (this.getTexture() instanceof GPUImage && ((GPUImage)this.getTexture()).getVirtualData() == null) return;

        // --- Clip src against drawable (source) bounds, adjust dst accordingly ---
        if (srcX < 0) {
            int d = -srcX;
            if (d >= width) return;
            width = (short)(width - d);
            dstX = (short)(dstX + d);
            srcX = 0;
        }
        if (srcY < 0) {
            int d = -srcY;
            if (d >= height) return;
            height = (short)(height - d);
            dstY = (short)(dstY + d);
            srcY = 0;
        }
        if (srcX + width > drawable.width) {
            if (srcX >= drawable.width) return;
            width = (short)(drawable.width - srcX);
        }
        if (srcY + height > drawable.height) {
            if (srcY >= drawable.height) return;
            height = (short)(drawable.height - srcY);
        }
        short srcStride = drawable.getStride();
        if (srcX + width > srcStride) {
            if (srcX >= srcStride) return;
            width = (short)(srcStride - srcX);
        }

        // --- Clip dst against this bounds, adjust src accordingly ---
        if (dstX < 0) {
            int d = -dstX;
            if (d >= width) return;
            width = (short)(width - d);
            srcX = (short)(srcX + d);
            dstX = 0;
        }
        if (dstY < 0) {
            int d = -dstY;
            if (d >= height) return;
            height = (short)(height - d);
            srcY = (short)(srcY + d);
            dstY = 0;
        }
        dstX = (short)Mathf.clamp(dstX, 0, this.width-1);
        dstY = (short)Mathf.clamp(dstY, 0, this.height-1);
        if ((dstX + width) > this.width) width = (short)((this.width - dstX));
        if ((dstY + height) > this.height) height = (short)((this.height - dstY));
        short dstStride = this.getStride();
        if (dstX + width > dstStride) {
            if (dstX >= dstStride) return;
            width = (short)(dstStride - dstX);
        }

        if (width <= 0 || height <= 0) return;

        int srcCap = drawable.data.capacity();
        int dstCap = this.data.capacity();
        if (srcCap <= 0 || dstCap <= 0) return;
        long srcEnd = ((long)srcX + (long)(srcY + height - 1) * srcStride + width) * 4L;
        long dstEnd = ((long)dstX + (long)(dstY + height - 1) * dstStride + width) * 4L;
        if (srcEnd > srcCap || dstEnd > dstCap) return;
        if (srcEnd <= 0 || dstEnd <= 0) return;

        // Final null check for DirectByteBuffer address validity
        if (!drawable.data.isDirect() || !this.data.isDirect()) return;

        try {
            if (gcFunction == GraphicsContext.Function.COPY) {
                copyArea(srcX, srcY, dstX, dstY, width, height, srcStride, dstStride, drawable.data, this.data);
            } else {
                copyAreaOp(srcX, srcY, dstX, dstY, width, height, srcStride, dstStride, drawable.data, this.data, gcFunction.ordinal());
            }
        } catch (Exception e) {
            // Catch any JNI exception to avoid crashing X thread
            android.util.Log.e("Drawable", "copyArea failed: " + e);
            return;
        }

        try { this.data.rewind(); } catch (Exception ignored) {}
        try { drawable.data.rewind(); } catch (Exception ignored) {}

        texture.setNeedsUpdate(true);
        markDirty(dstX, dstY, width, height);
        if (onDrawListener != null) onDrawListener.run();
    }

    public void fillColor(int color) {
        fillRect(0, 0, width, height, color);
    }

    public void fillRect(int x, int y, int width, int height, int color) {
        x = (short)Mathf.clamp(x, 0, this.width-1);
        y = (short)Mathf.clamp(y, 0, this.height-1);
        if ((x + width) > this.width) width = (short)((this.width - x));
        if ((y + height) > this.height) height = (short)((this.height - y));

        fillRect((short)x, (short)y, (short)width, (short)height, color, this.getStride(), this.data);
        this.data.rewind();

        texture.setNeedsUpdate(true);
        markDirty((short)x, (short)y, (short)width, (short)height);
        if (onDrawListener != null) onDrawListener.run();
    }

    public void drawLines(int color, int lineWidth, short... points) {
        for (int i = 2; i < points.length; i += 2) {
            drawLine(points[i-2], points[i-1], points[i+0], points[i+1], color, (short)lineWidth);
        }
    }

    public void drawLine(int x0, int y0, int x1, int y1, int color, int lineWidth) {
        x0 = Mathf.clamp(x0, 0, width-lineWidth);
        y0 = Mathf.clamp(y0, 0, height-lineWidth);
        x1 = Mathf.clamp(x1, 0, width-lineWidth);
        y1 = Mathf.clamp(y1, 0, height-lineWidth);

        drawLine((short)x0, (short)y0, (short)x1, (short)y1, color, (short)lineWidth, this.getStride(), this.data);

        this.data.rewind();

        texture.setNeedsUpdate(true);
        short lx = (short)Math.min(x0, x1);
        short ly = (short)Math.min(y0, y1);
        short lw = (short)(Math.abs(x1 - x0) + lineWidth);
        short lh = (short)(Math.abs(y1 - y0) + lineWidth);
        markDirty(lx, ly, lw, lh);
        if (onDrawListener != null) onDrawListener.run();
    }

    public void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue, byte backRed, byte backGreen, byte backBlue, Drawable srcDrawable, Drawable maskDrawable) {
        drawAlphaMaskedBitmap(foreRed, foreGreen, foreBlue, backRed, backGreen, backBlue, srcDrawable.data, maskDrawable.data, this.data);
        this.data.rewind();

        texture.setNeedsUpdate(true);
        markDirty((short)0, (short)0, width, height);
        if (onDrawListener != null) onDrawListener.run();
    }

    public void markDirty(short x, short y, short w, short h) {
        if (!hasDirtyRegion) {
            dirtyX = x;
            dirtyY = y;
            dirtyWidth = w;
            dirtyHeight = h;
            hasDirtyRegion = true;
        } else {
            short x2 = (short)Math.min(dirtyX, x);
            short y2 = (short)Math.min(dirtyY, y);
            short right = (short)Math.max(dirtyX + dirtyWidth, x + w);
            short bottom = (short)Math.max(dirtyY + dirtyHeight, y + h);
            dirtyX = x2;
            dirtyY = y2;
            dirtyWidth = (short)(right - x2);
            dirtyHeight = (short)(bottom - y2);
        }
    }

    public boolean hasDirtyRegion() {
        return hasDirtyRegion;
    }

    public short getDirtyX() { return dirtyX; }
    public short getDirtyY() { return dirtyY; }
    public short getDirtyWidth() { return dirtyWidth; }
    public short getDirtyHeight() { return dirtyHeight; }

    public void clearDirtyRegion() {
        hasDirtyRegion = false;
    }

    private static native void drawBitmap(short width, short height, ByteBuffer srcData, ByteBuffer dstData);

    private static native void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue, byte backRed, byte backGreen, byte backBlue, ByteBuffer srcData, ByteBuffer maskData, ByteBuffer dstData);

    private static native void copyArea(short srcX, short srcY, short dstX, short dstY, short width, short height, short srcStride, short dstStride, ByteBuffer srcData, ByteBuffer dstData);

    private static native void copyAreaOp(short srcX, short srcY, short dstX, short dstY, short width, short height, short srcStride, short dstStride, ByteBuffer srcData, ByteBuffer dstData, int gcFunction);

    private static native void fillRect(short x, short y, short width, short height, int color, short stride, ByteBuffer data);

    private static native void drawLine(short x0, short y0, short x1, short y1, int color, short lineWidth, short stride, ByteBuffer data);

    private static native void fromBitmap(Bitmap bitmap, ByteBuffer data);
}

//package com.winlator.cmod.xserver;
//
//import android.graphics.Bitmap;
//import com.winlator.cmod.core.Callback;
//import com.winlator.cmod.math.Mathf;
//import com.winlator.cmod.renderer.GPUImage;
//import com.winlator.cmod.renderer.Texture;
//
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//
///**
// * Merged Drawable class based on original + new Smali changes:
// * - Adds a 'blank' field (default true).
// * - Adds a 'useSharedData' field (default false).
// * - Adds a new method 'forceUpdate()' that sets the texture to need an update,
// *   sets blank = false, and triggers onDrawListener if present.
// * - Adds isBlank(), isUseSharedData(), setUseSharedData(...) from Smali.
// */
//public class Drawable extends XResource {
//    public final short width;
//    public final short height;
//    public final Visual visual;
//
//    // The texture bound to this Drawable.
//    private Texture texture = new Texture();
//
//    // Pixel data for this Drawable (e.g., RGBA).
//    private ByteBuffer data;
//
//    // Optional callback if something needs to be run after a draw.
//    private Runnable onDrawListener;
//
//    // Optional callback if something needs to be run on destroy.
//    private Callback<Drawable> onDestroyListener;
//
//    /**
//     * Locks concurrency for rendering. In many places, code calls synchronized(renderLock).
//     */
//    public final Object renderLock = new Object();
//
//    /**
//     * Whether this Drawable is "blank" (unused).
//     * Smali indicates it starts true, then set to false once we write or draw to it.
//     */
//    private boolean blank = true;
//
//    /**
//     * Whether this Drawable is using shared data externally, introduced in new Smali.
//     */
//    private boolean useSharedData = false;
//
//    static {
//        System.loadLibrary("winlator");
//    }
//
//    /**
//     * Main constructor. Allocates a ByteBuffer for the image data sized width*height*4.
//     */
//    public Drawable(int id, int width, int height, Visual visual) {
//        super(id);
//        this.width = (short) width;
//        this.height = (short) height;
//        this.visual = visual;
//
//        // Allocate local buffer (4 bytes per pixel)
//        this.data = ByteBuffer
//                .allocateDirect(width * height * 4)
//                .order(ByteOrder.LITTLE_ENDIAN);
//    }
//
//    /**
//     * Creates a Drawable from a Bitmap by copying its data into a newly allocated buffer.
//     * Also sets blank = false in the new Smali code.
//     */
//    public static Drawable fromBitmap(Bitmap bitmap) {
//        Drawable drawable = new Drawable(
//                0,
//                bitmap.getWidth(),
//                bitmap.getHeight(),
//                null
//        );
//        fromBitmap(bitmap, drawable.data);
//        drawable.blank = false; // Smali sets blank to false once we fill data
//        return drawable;
//    }
//
//    /**
//     * Force the Texture to be updated on the next render pass (based on new Smali).
//     * Also sets blank = false and calls onDrawListener if not null.
//     */
//    public void forceUpdate() {
//        texture.setNeedsUpdate(true);
//        blank = false;
//        if (onDrawListener != null) {
//            onDrawListener.run();
//        }
//    }
//
//    // -------------------------------------------------------------------------
//    // Getters & Setters
//    // -------------------------------------------------------------------------
//
//    public Texture getTexture() {
//        return texture;
//    }
//
//    /**
//     * If the Texture is a GPUImage, we also sync its ByteBuffer with this Drawable’s data.
//     */
//    public void setTexture(Texture texture) {
//        if (texture instanceof GPUImage) {
//            this.data = ((GPUImage) texture).getVirtualData();
//        }
//        this.texture = texture;
//    }
//
//    public ByteBuffer getData() {
//        return data;
//    }
//
//    public void setData(ByteBuffer data) {
//        this.data = data;
//        this.blank = false; // If we manually set data, it's no longer blank
//    }
//
//    /**
//     * New from Smali: isBlank() returns whether this Drawable has never been drawn to.
//     */
//    public boolean isBlank() {
//        return blank;
//    }
//
//    /**
//     * New from Smali: track whether we share data externally. Default is false.
//     */
//    public boolean isUseSharedData() {
//        return useSharedData;
//    }
//
//    public void setUseSharedData(boolean useSharedData) {
//        this.useSharedData = useSharedData;
//    }
//
//    public Runnable getOnDrawListener() {
//        return onDrawListener;
//    }
//
//    public void setOnDrawListener(Runnable onDrawListener) {
//        this.onDrawListener = onDrawListener;
//    }
//
//    public Callback<Drawable> getOnDestroyListener() {
//        return onDestroyListener;
//    }
//
//    public void setOnDestroyListener(Callback<Drawable> onDestroyListener) {
//        this.onDestroyListener = onDestroyListener;
//    }
//
//    // -------------------------------------------------------------------------
//    // Drawing & Copy Methods
//    // -------------------------------------------------------------------------
//
//    /**
//     * Draw an image into this Drawable.
//     * Depth 1 => drawBitmap(), Depth 24/32 => copy color data, etc.
//     */
//    public void drawImage(short srcX, short srcY, short dstX, short dstY,
//                          short width, short height, byte depth,
//                          ByteBuffer data, short totalWidth, short totalHeight) {
//        if (depth == 1) {
//            drawBitmap(width, height, data, this.data);
//        } else if (depth == 24 || depth == 32) {
//            dstX = (short) Mathf.clamp(dstX, 0, this.width - 1);
//            dstY = (short) Mathf.clamp(dstY, 0, this.height - 1);
//            if ((dstX + width) > this.width) width = (short) (this.width - dstX);
//            if ((dstY + height) > this.height) height = (short) (this.height - dstY);
//
//            copyArea(srcX, srcY, dstX, dstY, width, height,
//                    totalWidth, getStride(), data, this.data);
//        }
//
//        this.data.rewind();
//        data.rewind();
//
//        forceUpdate(); // Replaces older setNeedsUpdate + onDrawListener run
//    }
//
//    /**
//     * Extract an image from this Drawable.
//     */
//    public ByteBuffer getImage(short x, short y, short width, short height) {
//        ByteBuffer dstData = ByteBuffer
//                .allocateDirect(width * height * 4)
//                .order(ByteOrder.LITTLE_ENDIAN);
//
//        x = (short) Mathf.clamp(x, 0, this.width - 1);
//        y = (short) Mathf.clamp(y, 0, this.height - 1);
//        if ((x + width) > this.width) width = (short) (this.width - x);
//        if ((y + height) > this.height) height = (short) (this.height - y);
//
//        copyArea(x, y, (short) 0, (short) 0, width, height,
//                getStride(), width, this.data, dstData);
//
//        this.data.rewind();
//        dstData.rewind();
//        return dstData;
//    }
//
//    /**
//     * Copy from another Drawable into this Drawable, possibly applying a GC function.
//     */
//    public void copyArea(short srcX, short srcY, short dstX, short dstY,
//                         short width, short height, Drawable drawable) {
//        copyArea(srcX, srcY, dstX, dstY, width, height, drawable, GraphicsContext.Function.COPY);
//    }
//
//    public void copyArea(short srcX, short srcY, short dstX, short dstY,
//                         short width, short height, Drawable drawable,
//                         GraphicsContext.Function gcFunction) {
//        dstX = (short) Mathf.clamp(dstX, 0, this.width - 1);
//        dstY = (short) Mathf.clamp(dstY, 0, this.height - 1);
//        if ((dstX + width) > this.width) width = (short) (this.width - dstX);
//        if ((dstY + height) > this.height) height = (short) (this.height - dstY);
//
//        if (gcFunction == GraphicsContext.Function.COPY) {
//            copyArea(srcX, srcY, dstX, dstY, width, height,
//                    drawable.getStride(), getStride(),
//                    drawable.data, this.data);
//        } else {
//            copyAreaOp(srcX, srcY, dstX, dstY, width, height,
//                    drawable.getStride(), getStride(),
//                    drawable.data, this.data,
//                    gcFunction.ordinal());
//        }
//
//        this.data.rewind();
//        drawable.data.rewind();
//
//        forceUpdate();
//    }
//
//    public void fillColor(int color) {
//        fillRect(0, 0, width, height, color);
//    }
//
//    public void fillRect(int x, int y, int width, int height, int color) {
//        x = (short) Mathf.clamp(x, 0, this.width - 1);
//        y = (short) Mathf.clamp(y, 0, this.height - 1);
//        if ((x + width) > this.width) width = (short) (this.width - x);
//        if ((y + height) > this.height) height = (short) (this.height - y);
//
//        fillRect((short) x, (short) y, (short) width, (short) height,
//                color, getStride(), this.data);
//        this.data.rewind();
//
//        forceUpdate();
//    }
//
//    public void drawLines(int color, int lineWidth, short... points) {
//        for (int i = 2; i < points.length; i += 2) {
//            drawLine(points[i - 2], points[i - 1], points[i], points[i + 1], color, lineWidth);
//        }
//    }
//
//    public void drawLine(int x0, int y0, int x1, int y1, int color, int lineWidth) {
//        x0 = Mathf.clamp(x0, 0, width - lineWidth);
//        y0 = Mathf.clamp(y0, 0, height - lineWidth);
//        x1 = Mathf.clamp(x1, 0, width - lineWidth);
//        y1 = Mathf.clamp(y1, 0, height - lineWidth);
//
//        drawLine((short) x0, (short) y0, (short) x1, (short) y1,
//                color, (short) lineWidth, getStride(), this.data);
//
//        this.data.rewind();
//
//        forceUpdate();
//    }
//
//    public void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue,
//                                      byte backRed, byte backGreen, byte backBlue,
//                                      Drawable srcDrawable, Drawable maskDrawable) {
//        drawAlphaMaskedBitmap(foreRed, foreGreen, foreBlue,
//                backRed, backGreen, backBlue,
//                srcDrawable.data, maskDrawable.data, this.data);
//        this.data.rewind();
//
//        forceUpdate();
//    }
//
//    // -------------------------------------------------------------------------
//    // Private / Native Helpers
//    // -------------------------------------------------------------------------
//
//    private short getStride() {
//        return (texture instanceof GPUImage)
//                ? ((GPUImage) texture).getStride()
//                : width;
//    }
//
//    private static native void drawBitmap(short width, short height,
//                                          ByteBuffer srcData, ByteBuffer dstData);
//
//    private static native void drawAlphaMaskedBitmap(byte foreRed, byte foreGreen, byte foreBlue,
//                                                     byte backRed, byte backGreen, byte backBlue,
//                                                     ByteBuffer srcData, ByteBuffer maskData,
//                                                     ByteBuffer dstData);
//
//    private static native void copyArea(short srcX, short srcY, short dstX, short dstY,
//                                        short width, short height,
//                                        short srcStride, short dstStride,
//                                        ByteBuffer srcData, ByteBuffer dstData);
//
//    private static native void copyAreaOp(short srcX, short srcY, short dstX, short dstY,
//                                          short width, short height,
//                                          short srcStride, short dstStride,
//                                          ByteBuffer srcData, ByteBuffer dstData,
//                                          int gcFunction);
//
//    private static native void fillRect(short x, short y, short width, short height,
//                                        int color, short stride, ByteBuffer data);
//
//    private static native void drawLine(short x0, short y0, short x1, short y1,
//                                        int color, short lineWidth,
//                                        short stride, ByteBuffer data);
//
//    private static native void fromBitmap(Bitmap bitmap, ByteBuffer data);
//}
