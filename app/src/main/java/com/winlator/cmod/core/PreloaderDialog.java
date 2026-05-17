package com.winlator.cmod.core;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import com.winlator.cmod.R;

public class PreloaderDialog {
    private final Activity activity;
    private Dialog dialog;
    private final Handler dotsHandler = new Handler(Looper.getMainLooper());
    private String baseStatus = "";
    private int dotsState = 0;
    private final Runnable dotsRunnable = new Runnable() {
        @Override
        public void run() {
            if (dialog == null || !dialog.isShowing()) return;
            TextView tv = dialog.findViewById(R.id.TextView);
            if (tv != null) {
                StringBuilder sb = new StringBuilder(baseStatus);
                for (int i = 0; i < dotsState; i++) sb.append('.');
                tv.setText(sb.toString());
            }
            dotsState = (dotsState + 1) % 4;
            dotsHandler.postDelayed(this, 400);
        }
    };

    public PreloaderDialog(Activity activity) {
        this.activity = activity;
    }

    private void create() {
        if (dialog != null) return;
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setContentView(R.layout.preloader_dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    public synchronized void show(int textResId) {
        if (isShowing()) return;
        close();
        if (dialog == null) create();
        baseStatus = stripTrailingDots(activity.getString(textResId));
        ((TextView)dialog.findViewById(R.id.TextView)).setText(baseStatus);
        applyPendingTexts();
        dialog.show();
        startFadeIn();
        startDotsAnimation();
    }

    private CharSequence pendingTitle = null;
    private CharSequence pendingSubtitle = null;

    public synchronized void setTitle(CharSequence title) {
        this.pendingTitle = title;
        if (dialog != null) {
            TextView titleView = dialog.findViewById(R.id.TitleTextView);
            if (titleView != null && title != null) titleView.setText(title);
        }
    }

    public synchronized void setSubtitle(CharSequence subtitle) {
        this.pendingSubtitle = subtitle;
        if (dialog != null) {
            TextView subView = dialog.findViewById(R.id.SubtitleTextView);
            if (subView != null) {
                subView.setText(subtitle == null ? "" : subtitle);
                subView.setVisibility(subtitle == null || subtitle.length() == 0 ? View.GONE : View.VISIBLE);
            }
        }
    }

    public void setSubtitleOnUiThread(final CharSequence subtitle) {
        activity.runOnUiThread(() -> setSubtitle(subtitle));
    }

    public void setStageOnUiThread(final CharSequence stage) {
        activity.runOnUiThread(() -> setStage(stage));
    }

    public synchronized void setStage(CharSequence stage) {
        baseStatus = stripTrailingDots(stage == null ? "" : stage.toString());
        dotsState = 0;
        if (dialog != null && isShowing()) {
            TextView tv = dialog.findViewById(R.id.TextView);
            if (tv != null) tv.setText(baseStatus);
        }
    }

    private void applyPendingTexts() {
        if (dialog == null) return;
        if (pendingTitle != null) {
            TextView titleView = dialog.findViewById(R.id.TitleTextView);
            if (titleView != null) titleView.setText(pendingTitle);
        }
        TextView subView = dialog.findViewById(R.id.SubtitleTextView);
        if (subView != null) {
            if (pendingSubtitle != null && pendingSubtitle.length() > 0) {
                subView.setText(pendingSubtitle);
                subView.setVisibility(View.VISIBLE);
            } else {
                subView.setVisibility(View.GONE);
            }
        }
    }

    private void startFadeIn() {
        if (dialog == null) return;
        View root = dialog.findViewById(R.id.PreloaderRoot);
        if (root == null) return;
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(280).setInterpolator(new AccelerateDecelerateInterpolator()).start();
    }

    private void startDotsAnimation() {
        dotsHandler.removeCallbacks(dotsRunnable);
        dotsState = 0;
        dotsHandler.postDelayed(dotsRunnable, 400);
    }

    private static String stripTrailingDots(String s) {
        if (s == null) return "";
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c == '.' || c == '\u2026' || c == ' ') end--;
            else break;
        }
        return s.substring(0, end);
    }

    public void showOnUiThread(final int textResId) {
        activity.runOnUiThread(() -> show(textResId));
    }
    
    /**
     * Обновить текст в уже открытом диалоге
     */
    public synchronized void updateText(String text) {
        setStage(text);
    }
    
    /**
     * Обновить текст в диалоге из UI потока
     */
    public void updateTextOnUiThread(final String text) {
        activity.runOnUiThread(() -> updateText(text));
    }

    public synchronized void close() {
        dotsHandler.removeCallbacks(dotsRunnable);
        if (dialog == null) return;
        final Dialog d = dialog;
        dialog = null;
        try {
            View root = d.findViewById(R.id.PreloaderRoot);
            if (root != null && d.isShowing()) {
                root.animate().alpha(0f).setDuration(260)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> { try { d.dismiss(); } catch (Exception ignored) {} })
                    .start();
            } else {
                d.dismiss();
            }
        } catch (Exception e) {
            try { d.dismiss(); } catch (Exception ignored) {}
        }
    }

    public synchronized void setCoverArt(Bitmap bitmap) {
        if (dialog == null) return;
        ImageView coverView = dialog.findViewById(R.id.CoverArtBackground);
        View scrim = dialog.findViewById(R.id.CoverArtScrim);
        if (coverView == null || scrim == null) return;
        if (bitmap != null) {
            coverView.setImageBitmap(bitmap);
            coverView.setVisibility(View.VISIBLE);
            scrim.setVisibility(View.VISIBLE);
        } else {
            coverView.setVisibility(View.GONE);
            scrim.setVisibility(View.GONE);
        }
    }

    public void setCoverArtOnUiThread(final Bitmap bitmap) {
        activity.runOnUiThread(() -> setCoverArt(bitmap));
    }

    public void closeOnUiThread() {
        activity.runOnUiThread(this::close);
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}
