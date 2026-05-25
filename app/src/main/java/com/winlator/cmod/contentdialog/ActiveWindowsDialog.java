package com.winlator.cmod.contentdialog;

import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.view.LayoutInflater;
import android.view.View;
import android.content.Context;
import android.graphics.Bitmap;

import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.R;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.core.ImageUtils;
import com.winlator.cmod.renderer.VulkanRenderer;
import com.winlator.cmod.xserver.Drawable;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XServer;
import com.winlator.cmod.xserver.PixmapManager;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer.Lockable;
import com.winlator.cmod.winhandler.WinHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ActiveWindowsDialog extends ContentDialog {
    private final XServerDisplayActivity activity;

    public ActiveWindowsDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.active_windows_dialog);
        this.activity = activity;
        setCancelable(true);
        setTitle(R.string.active_windows);
        setIcon(R.drawable.icon_window_list);

        ArrayList<Window> windows = collectActiveWindows();
        loadWindowViews(windows);
    }

    private ArrayList<Window> collectActiveWindows() {
        XServer xServer = activity.getXServer();
        ArrayList<Window> result = new ArrayList<>();
        Set<Long> seenHandles = new HashSet<>();

        XLock lock = xServer.lock(Lockable.WINDOW_MANAGER, Lockable.DRAWABLE_MANAGER);
        try {
            collectActiveWindows(xServer.windowManager.rootWindow, result, seenHandles);
        } finally {
            if (lock != null) lock.close();
        }

        return result;
    }

    private void collectActiveWindows(Window window, ArrayList<Window> result, Set<Long> seenHandles) {
        // Add window if it's renderable and not the root or desktop window
        if (window.isRenderable() &&
            window != activity.getXServer().windowManager.rootWindow &&
            !window.isDesktopWindow()) {
            
            String className = window.getClassName();
            long handle = window.getHandle();
            Drawable content = window.getContent();
            
            // Исключаем explorer.exe, дубликаты по handle и окна без содержимого
            if ((className == null || !className.equalsIgnoreCase("explorer.exe")) &&
                !seenHandles.contains(handle) &&
                content != null) {
                seenHandles.add(handle);
                result.add(window);
            }
        }

        // Recursively collect children
        List<Window> children = window.getChildren();
        for (Window child : children) {
            collectActiveWindows(child, result, seenHandles);
        }
    }

    private void loadWindowViews(ArrayList<Window> windows) {
        if (windows.isEmpty()) {
            findViewById(R.id.no_windows_text).setVisibility(View.VISIBLE);
            return;
        }

        XServer xServer = activity.getXServer();
        LinearLayout llWindowList = findViewById(R.id.window_list_layout);
        llWindowList.removeAllViews();

        VulkanRenderer renderer = xServer.getRenderer();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        int previewWidth = (int) UnitUtils.dpToPx(240.0f);
        int previewHeight = (int) UnitUtils.dpToPx(160.0f);

        // Создаем строки по 2 карточки
        LinearLayout currentRow = null;

        for (int i = windows.size() - 1; i >= 0; i--) {
            // Создаём новую строку для каждых 2 окон
            if ((windows.size() - 1 - i) % 2 == 0) {
                currentRow = new LinearLayout(getContext());
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                currentRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ));
                llWindowList.addView(currentRow);
            }
            final Window window = windows.get(i);
            Window parent = window.getParent();

            View itemView = inflater.inflate(R.layout.active_window_item, currentRow, false);
            ImageView ivIcon = itemView.findViewById(R.id.window_icon);
            final ImageView ivWindow = itemView.findViewById(R.id.window_preview);
            TextView tvName = itemView.findViewById(R.id.window_name);
            TextView tvProcess = itemView.findViewById(R.id.process_name);

            String title = window.getName();
            if (title.isEmpty()) {
                title = parent.getName();
            }
            tvName.setText(title);

            // Добавляем имя процесса
            String className = window.getClassName();
            if (className != null && !className.isEmpty()) {
                tvProcess.setText(className);
                tvProcess.setVisibility(View.VISIBLE);
            } else {
                tvProcess.setVisibility(View.GONE);
            }

            // Устанавливаем иконку окна
            PixmapManager pixmapManager = xServer.pixmapManager;
            Bitmap icon = pixmapManager.getWindowIcon(window);
            if (icon == null) {
                icon = pixmapManager.getWindowIcon(parent);
            }

            ivIcon.setImageResource(R.drawable.icon_window_default);
            if (icon != null) {
                ivIcon.setImageBitmap(icon);
            }

            if (!window.isIconic()) {
                // Окно не свернуто - показываем превью фиксированного размера
                Drawable content = window.getContent();
                if (content != null) {
                    // Устанавливаем фиксированный размер превью
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(previewWidth, previewHeight);
                    ivWindow.setLayoutParams(params);
                    ivWindow.setScaleType(ImageView.ScaleType.CENTER_CROP);

                    // Делаем скриншот окна из данных Drawable
                    try (XLock dlock = xServer.lock(Lockable.DRAWABLE_MANAGER)) {
                        synchronized (content.renderLock) {
                            java.nio.ByteBuffer buf = content.getData();
                            if (buf != null) {
                                int[] pixels = new int[content.width * content.height];
                                buf.rewind();
                                for (int j = 0; j < pixels.length; j++) {
                                    int r = buf.get() & 0xFF;
                                    int g = buf.get() & 0xFF;
                                    int b = buf.get() & 0xFF;
                                    int a = buf.get() & 0xFF;
                                    pixels[j] = (a << 24) | (r << 16) | (g << 8) | b;
                                }
                                buf.rewind();
                                final Bitmap bitmap = Bitmap.createBitmap(pixels, content.width, content.height, Bitmap.Config.ARGB_8888);
                                ivWindow.post(() -> ivWindow.setImageBitmap(bitmap));
                            }
                        }
                    }
                }
            } else {
                // Окно свернуто - показываем заглушку
                itemView.findViewById(R.id.iconic_overlay).setVisibility(View.VISIBLE);
                itemView.findViewById(R.id.iconic_icon).setVisibility(View.VISIBLE);

                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(previewWidth, previewHeight);
                ivWindow.setLayoutParams(params);
            }

            // Клик по карточке переключает на окно
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    WinHandler winHandler = activity.getWinHandler();
                    winHandler.bringToFront(window.getClassName(), window.getHandle());
                    dismiss();
                }
            });

            currentRow.addView(itemView);
        }
    }
}