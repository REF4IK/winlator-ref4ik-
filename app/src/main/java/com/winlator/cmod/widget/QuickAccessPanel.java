package com.winlator.cmod.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;

public class QuickAccessPanel extends LinearLayout {
    private final PointF startPoint = new PointF();
    private boolean isDragging = false;
    private SharedPreferences preferences;

    public interface QuickAccessListener {
        void onTaskManagerClick();
        void onFpsCounterClick();
        void onInputControlsClick();
    }

    private QuickAccessListener listener;

    public QuickAccessPanel(Context context) {
        this(context, null);
    }

    public QuickAccessPanel(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public QuickAccessPanel(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setOrientation(VERTICAL);

        preferences = new com.winlator.cmod.core.MmkvPreferences();

        View contentView = LayoutInflater.from(getContext())
                .inflate(R.layout.quick_access_panel, this, false);

        View moveHandle = contentView.findViewById(R.id.btnQuickAccessMove);
        if (moveHandle != null) {
            moveHandle.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startPoint.set(event.getX(), event.getY());
                        isDragging = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isDragging) {
                            float newX = getX() + (event.getX() - startPoint.x);
                            float newY = getY() + (event.getY() - startPoint.y);
                            setX(newX);
                            setY(newY);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isDragging = false;
                        savePosition();
                        break;
                }
                return true;
            });
        }

        contentView.findViewById(R.id.btnQuickTaskManager).setOnClickListener(v -> {
            if (listener != null) listener.onTaskManagerClick();
        });

        contentView.findViewById(R.id.btnQuickFpsCounter).setOnClickListener(v -> {
            if (listener != null) listener.onFpsCounterClick();
        });

        contentView.findViewById(R.id.btnQuickInputControls).setOnClickListener(v -> {
            if (listener != null) listener.onInputControlsClick();
        });

        addView(contentView);
        restorePosition();
    }

    public void setQuickAccessListener(QuickAccessListener listener) {
        this.listener = listener;
    }

    private void savePosition() {
        preferences.edit()
                .putFloat("quick_access_x", getX())
                .putFloat("quick_access_y", getY())
                .apply();
    }

    private void restorePosition() {
        float x = preferences.getFloat("quick_access_x", 50f);
        float y = preferences.getFloat("quick_access_y", 50f);
        setX(x);
        setY(y);
    }
}
