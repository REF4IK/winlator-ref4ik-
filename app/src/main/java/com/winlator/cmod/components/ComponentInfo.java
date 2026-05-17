package com.winlator.cmod.components;

/**
 * Информация о компоненте для установки
 */
public class ComponentInfo {
    private String id;
    private String name;
    private String displayName;
    private String description;
    private String fileName;
    private String downloadUrl;
    private long fileSize;
    private String type; // physx, vcredist, mono, gecko, fonts
    private boolean isInstalled;
    private boolean isSelected;

    public ComponentInfo(String id, String name, String displayName, String description, 
                        String fileName, String downloadUrl, long fileSize, String type) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.fileSize = fileSize;
        this.type = type;
        this.isInstalled = false;
        this.isSelected = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getType() {
        return type;
    }

    public boolean isInstalled() {
        return isInstalled;
    }

    public void setInstalled(boolean installed) {
        isInstalled = installed;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    /**
     * Получить читаемый размер файла
     */
    public String getFormattedSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        }
    }

    /**
     * Определить тип компонента по имени файла
     */
    public static String detectType(String fileName) {
        String lowerName = fileName.toLowerCase();
        
        if (lowerName.contains("physx")) {
            return "physx";
        } else if (lowerName.contains("vcredist") || lowerName.contains("vc_redist")) {
            return "vcredist";
        } else if (lowerName.contains("mono")) {
            return "mono";
        } else if (lowerName.contains("gecko")) {
            return "gecko";
        } else if (lowerName.contains("font") || lowerName.contains("cjk")) {
            return "fonts";
        } else if (lowerName.contains("dxvk")) {
            return "dxvk";
        } else if (lowerName.contains("vkd3d")) {
            return "vkd3d";
        } else if (lowerName.contains("directx") || lowerName.contains("d3dx")) {
            return "directx";
        }
        
        return "other";
    }

    /**
     * Получить иконку для типа компонента
     */
    public static int getIconForType(String type) {
        switch (type) {
            case "physx":
                return android.R.drawable.ic_menu_compass;
            case "vcredist":
                return android.R.drawable.ic_menu_info_details;
            case "mono":
            case "gecko":
                return android.R.drawable.ic_menu_manage;
            case "fonts":
                return android.R.drawable.ic_menu_sort_alphabetically;
            case "dxvk":
            case "vkd3d":
                return android.R.drawable.ic_menu_view;
            default:
                return android.R.drawable.ic_menu_save;
        }
    }
}
