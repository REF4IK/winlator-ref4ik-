package com.catfixture.inputbridge.core.iconmanager;

import java.io.Serializable;
import java.util.List;

public class IconPack implements Serializable {
    private static final long serialVersionUID = 0x5b92L; // 23442L
    public String name;
    public String author;
    public List<Icon> icons;
    public boolean isEnabled;
    public long packSize;
}
