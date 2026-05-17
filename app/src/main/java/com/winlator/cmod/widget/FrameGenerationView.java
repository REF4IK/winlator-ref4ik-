package com.winlator.cmod.widget;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PointF;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.renderer.EffectComposer;
import com.winlator.cmod.renderer.GLRenderer;
import com.winlator.cmod.renderer.effects.FrameGenerationEffect;

public class FrameGenerationView extends FrameLayout {
    private final SharedPreferences preferences;
    private boolean restoreSavedPosition = true;
    private short lastX = 0;
    private short lastY = 0;
    private Callback<Boolean> frameGenerationCallback;
    private Runnable hideButtonCallback;
    private final GLRenderer renderer;

    private final Spinner modeSpinner;
    private final Spinner fpsSpinner;
    private final TextView currentFpsTextView;
    private final RadioGroup multiplierGroup;

    private static final String[] MODE_OPTIONS = {
            "Fast", "Balanced", "Quality"
    };
    private static final int[] MODE_VALUES = {
            FrameGenerationEffect.MODE_FAST,
            FrameGenerationEffect.MODE_BALANCED,
            FrameGenerationEffect.MODE_QUALITY
    };

    private static final String[] FPS_OPTIONS = {
            "Auto", "15->30 FPS", "20->40 FPS", "25->50 FPS", "30->60 FPS", "45->90 FPS", "60->120 FPS"
    };
    private static final int[] FPS_VALUES = {
            FrameGenerationEffect.FPS_AUTO,
            FrameGenerationEffect.FPS_15,
            FrameGenerationEffect.FPS_20,
            FrameGenerationEffect.FPS_25,
            FrameGenerationEffect.FPS_30,
            FrameGenerationEffect.FPS_45,
            FrameGenerationEffect.FPS_60
    };

    private int targetFPS;
    private int generationMode;
    private int generationMultiplier = 2;

    public FrameGenerationView(Context context, GLRenderer renderer) {
        this(context, null, renderer);
    }

    public FrameGenerationView(Context context, @Nullable AttributeSet attrs, GLRenderer renderer) {
        this(context, attrs, 0, renderer);
    }

    public FrameGenerationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, GLRenderer renderer) {
        this(context, attrs, defStyleAttr, 0, renderer);
    }

    public FrameGenerationView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes, GLRenderer renderer) {
        super(context, attrs, defStyleAttr, defStyleRes);

        this.renderer = renderer;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View contentView = LayoutInflater.from(context).inflate(R.layout.frame_generation_layout, this, false);

        final PointF startPoint = new PointF();
        final boolean[] isActionDown = {false};
        contentView.findViewById(R.id.BTMove).setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startPoint.x = event.getX();
                    startPoint.y = event.getY();
                    isActionDown[0] = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isActionDown[0]) {
                        float newX = getX() + (event.getX() - startPoint.x);
                        float newY = getY() + (event.getY() - startPoint.y);
                        movePanel(newX, newY);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (isActionDown[0] && lastX > 0 && lastY > 0) {
                        preferences.edit().putString("frame_generation_layout", lastX + "|" + lastY).apply();
                    }
                    lastX = 0;
                    lastY = 0;
                    isActionDown[0] = false;
                    break;
            }
            return true;
        });

        contentView.findViewById(R.id.BTHide).setOnClickListener((v) -> {
            if (hideButtonCallback != null) hideButtonCallback.run();
        });

        fpsSpinner = contentView.findViewById(R.id.fps_spinner);
        modeSpinner = contentView.findViewById(R.id.generation_mode_spinner);
        currentFpsTextView = contentView.findViewById(R.id.current_fps_text);
        multiplierGroup = contentView.findViewById(R.id.multiplier_group);

        ArrayAdapter<String> fpsAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, FPS_OPTIONS);
        fpsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fpsSpinner.setAdapter(fpsAdapter);

        ArrayAdapter<String> modeAdapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item, MODE_OPTIONS);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);

        loadSettings();

        final ToggleButton toggleButton = contentView.findViewById(R.id.ToggleButton);
        toggleButton.setVisibility(VISIBLE);
        toggleButton.setOnClickListener((v) -> {
            if (frameGenerationCallback != null) {
                frameGenerationCallback.call(toggleButton.isChecked());
            }
            if (renderer != null && renderer.getEffectComposer() != null) {
                renderer.getEffectComposer().configureFrameGeneration(targetFPS, generationMode);
            }
        });

        fpsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedFPS = FPS_VALUES[position];
                boolean isAuto = (selectedFPS == FrameGenerationEffect.FPS_AUTO);
                applyFrameGenerationSettings(selectedFPS);
                if (isAuto) updateCurrentFPSDisplay();
                saveSettings(selectedFPS, position, generationMode, modeSpinner.getSelectedItemPosition());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedMode = MODE_VALUES[position];
                generationMode = selectedMode;
                if (renderer != null && renderer.getEffectComposer() != null) {
                    renderer.getEffectComposer().setGenerationMode(selectedMode);
                }
                saveSettings(targetFPS, fpsSpinner.getSelectedItemPosition(), selectedMode, position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        multiplierGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.multiplier_2x) generationMultiplier = 2;
            else if (checkedId == R.id.multiplier_3x) generationMultiplier = 3;
            else if (checkedId == R.id.multiplier_4x) generationMultiplier = 4;
            if (renderer != null && renderer.getEffectComposer() != null) {
                renderer.getEffectComposer().setGenerationMultiplier(generationMultiplier);
            }
            saveSettings(targetFPS, fpsSpinner.getSelectedItemPosition(), generationMode,
                    modeSpinner.getSelectedItemPosition(), generationMultiplier);
        });

        startFPSUpdateTimer();
        addView(contentView);
    }

    private void applyFrameGenerationSettings(int targetFPS) {
        this.targetFPS = targetFPS;
        if (renderer != null && renderer.getEffectComposer() != null) {
            renderer.getEffectComposer().configureFrameGeneration(targetFPS, generationMode);
        }
    }

    private void updateCurrentFPSDisplay() {
        if (renderer == null || renderer.getEffectComposer() == null) return;

        EffectComposer.FrameGenerationSettings settings = renderer.getEffectComposer().getFrameGenerationSettings();
        if (settings == null) return;

        int realFPS = settings.realInterval > 0 ? (int) (1000 / settings.realInterval) : 0;
        int targetFPS = settings.targetInterval > 0 ? (int) (1000 / settings.targetInterval) : 0;

        String text = String.format("Current: %d FPS → %d FPS", realFPS, targetFPS);
        currentFpsTextView.setText(text);

        if (settings.autoDetect) updateFpsSpinnerForAutoMode(realFPS);
    }

    private void updateFpsSpinnerForAutoMode(int detectedFPS) {
        int closestFPS = FrameGenerationEffect.FPS_30;
        int minDiff = Integer.MAX_VALUE;

        for (int fps : FPS_VALUES) {
            if (fps != FrameGenerationEffect.FPS_AUTO) {
                int diff = Math.abs(fps - detectedFPS);
                if (diff < minDiff) {
                    minDiff = diff;
                    closestFPS = fps;
                }
            }
        }

        for (int i = 0; i < FPS_VALUES.length; i++) {
            if (FPS_VALUES[i] == closestFPS) {
                fpsSpinner.setSelection(i, false);
                View selected = fpsSpinner.getSelectedView();
                if (selected instanceof TextView) {
                    ((TextView) selected).setText(String.format("Auto (≈%d FPS)", detectedFPS));
                }
                break;
            }
        }
    }

    private void startFPSUpdateTimer() {
        Handler handler = new Handler();
        Runnable updateTask = new Runnable() {
            @Override
            public void run() {
                updateCurrentFPSDisplay();
                handler.postDelayed(this, 1000);
            }
        };
        handler.postDelayed(updateTask, 1000);
    }

    private void saveSettings(int fps, int fpsSpinnerPosition, int mode, int modeSpinnerPosition) {
        saveSettings(fps, fpsSpinnerPosition, mode, modeSpinnerPosition, generationMultiplier);
    }

    private void saveSettings(int fps, int fpsSpinnerPosition, int mode, int modeSpinnerPosition, int multiplier) {
        SharedPreferences prefs = getContext().getSharedPreferences("frame_generation", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("fps", fps)
                .putInt("fps_spinner_position", fpsSpinnerPosition)
                .putInt("mode", mode)
                .putInt("mode_spinner_position", modeSpinnerPosition)
                .putInt("multiplier", multiplier)
                .apply();
    }

    private void loadSettings() {
        SharedPreferences prefs = getContext().getSharedPreferences("frame_generation", Context.MODE_PRIVATE);
        targetFPS = prefs.getInt("fps", FrameGenerationEffect.FPS_30);
        int fpsSpinnerPosition = prefs.getInt("fps_spinner_position", 4);
        generationMode = prefs.getInt("mode", FrameGenerationEffect.MODE_BALANCED);
        int modeSpinnerPosition = prefs.getInt("mode_spinner_position", 1); // default = Balanced

        generationMultiplier = prefs.getInt("multiplier", 2);
        fpsSpinner.setSelection(Mathf.clamp(fpsSpinnerPosition, 0, FPS_VALUES.length - 1));
        modeSpinner.setSelection(Mathf.clamp(modeSpinnerPosition, 0, MODE_VALUES.length - 1));
        if (generationMultiplier == 3) multiplierGroup.check(R.id.multiplier_3x);
        else if (generationMultiplier == 4) multiplierGroup.check(R.id.multiplier_4x);
        else multiplierGroup.check(R.id.multiplier_2x);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (restoreSavedPosition) {
            float x = 1e6f;
            float y = 1e6f;

            String config = preferences.getString("frame_generation_layout", null);
            if (config != null) {
                try {
                    String[] parts = config.split("\\|");
                    x = Short.parseShort(parts[0]);
                    y = Short.parseShort(parts[1]);
                } catch (NumberFormatException ignored) {}
            }

            movePanel(x, y);
            restoreSavedPosition = false;
        }
    }

    private void movePanel(float x, float y) {
        final int padding = (int) UnitUtils.dpToPx(8);
        ViewGroup parent = (ViewGroup) getParent();
        int width = getWidth();
        int height = getHeight();
        int parentWidth = parent.getWidth();
        int parentHeight = parent.getHeight();
        x = Mathf.clamp(x, padding, parentWidth - padding - width);
        y = Mathf.clamp(y, padding, parentHeight - padding - height);
        setX(x);
        setY(y);
        lastX = (short) x;
        lastY = (short) y;
    }

    public Callback<Boolean> getFrameGenerationCallback() {
        return frameGenerationCallback;
    }

    public void setFrameGenerationCallback(Callback<Boolean> frameGenerationCallback) {
        this.frameGenerationCallback = frameGenerationCallback;
    }

    public Runnable getHideButtonCallback() {
        return hideButtonCallback;
    }

    public void setHideButtonCallback(Runnable hideButtonCallback) {
        this.hideButtonCallback = hideButtonCallback;
    }
}

