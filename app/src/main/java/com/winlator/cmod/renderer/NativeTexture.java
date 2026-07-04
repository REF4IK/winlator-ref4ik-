package com.winlator.cmod.renderer;

import java.nio.ByteBuffer;

public abstract class NativeTexture extends Texture {
    public abstract short getStride();
    public abstract ByteBuffer getVirtualData();
}
