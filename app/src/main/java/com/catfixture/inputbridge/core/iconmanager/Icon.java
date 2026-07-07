package com.catfixture.inputbridge.core.iconmanager;

import java.io.Serializable;

public class Icon implements Serializable {
    private static final long serialVersionUID = 0x5f2aL; // 24362L
    public String name;
    public String path;
    public int scaleType;
    public BitmapData bmpData;
}
