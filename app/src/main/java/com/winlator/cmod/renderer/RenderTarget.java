package com.winlator.cmod.renderer;

import android.opengl.GLES30;

// This class extends the Texture class and manages a framebuffer object (FBO).
public class RenderTarget extends Texture {
    // Field to store the OpenGL framebuffer ID.
    private int framebuffer;
    protected int unpackAlignment = 4;
    protected int format = GLES30.GL_RGBA;

    // Constructor
    public RenderTarget() {
        // Call the superclass constructor to initialize the texture.
        super();
    }

    // Generates a new framebuffer and stores its ID in the framebuffer field.
    private void generateFramebuffer() {
        // Create a new array to hold the framebuffer ID.
        int[] framebuffers = new int[1];
        // Generate a framebuffer and store its ID in the array.
        GLES30.glGenFramebuffers(1, framebuffers, 0);
        // Set the framebuffer field to the generated framebuffer ID.
        framebuffer = framebuffers[0];
    }

    protected void generateTextureId() {
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);
        textureId = textures[0];
    }

    protected void setTextureParameters() {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
    }

    // Allocates and initializes the framebuffer and texture with the specified width and height.
    public void allocateFramebuffer(int width, int height) {
        // Check if the framebuffer is already allocated.
        if (framebuffer != 0) {
            return; // If the framebuffer is already allocated, return.
        }

        // Generate the framebuffer if not already done.
        generateFramebuffer();

        // Generate a texture ID using the superclass method.
        generateTextureId();

        // Bind the framebuffer.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer);

        // Activate texture unit 0.
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);

        // Set pixel storage mode for unpacking pixel data from memory.
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, unpackAlignment);

        // Bind the texture to the 2D texture target.
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId);

        // Allocate memory for the texture image with the specified width, height, and format.
        GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, format, width, height, 0,
                format, GLES30.GL_UNSIGNED_BYTE, null
        );

        // Set texture parameters (like filtering and wrapping modes).
        setTextureParameters();

        // Attach the texture to the framebuffer as a color attachment.
        GLES30.glFramebufferTexture2D(
                GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, textureId, 0
        );

        // Unbind the texture.
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);

        // Unbind the framebuffer.
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    // Returns the framebuffer ID.
    public int getFramebuffer() {
        return framebuffer;
    }

    @Override
    public void copyFromFramebuffer(int srcFramebuffer, short width, short height) {
        if (framebuffer == 0) allocateFramebuffer(width, height);
        if (textureId == 0) return;

        // Blit from source framebuffer to our framebuffer/texture
        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, srcFramebuffer);
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, framebuffer);
        GLES30.glBlitFramebuffer(
            0, 0, width, height,
            0, 0, width, height,
            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST
        );
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        needsUpdate = true;
    }
}
