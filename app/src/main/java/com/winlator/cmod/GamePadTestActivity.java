package com.winlator.cmod;

import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.widget.JoystickView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GamePadTestActivity extends AppCompatActivity {
    private TextView tvDeviceInfo;
    private TextView tvLeftStickX;
    private TextView tvLeftStickY;
    private TextView tvRightStickX;
    private TextView tvRightStickY;
    private TextView tvLeftTrigger;
    private TextView tvRightTrigger;
    private TextView tvButtonsPressed;
    private TextView tvVibrationStatus;
    private Button btTestVibration;
    private JoystickView leftStickView;
    private JoystickView rightStickView;
    
    private List<String> pressedButtons = new ArrayList<>();
    private InputDevice currentDevice;
    private Vibrator vibrator;
    private boolean isVibrating = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gamepad_test_activity);

        // Initialize views
        ImageButton btBack = findViewById(R.id.BTBack);
        tvDeviceInfo = findViewById(R.id.TVDeviceInfo);
        tvLeftStickX = findViewById(R.id.TVLeftStickX);
        tvLeftStickY = findViewById(R.id.TVLeftStickY);
        tvRightStickX = findViewById(R.id.TVRightStickX);
        tvRightStickY = findViewById(R.id.TVRightStickY);
        tvLeftTrigger = findViewById(R.id.TVLeftTrigger);
        tvRightTrigger = findViewById(R.id.TVRightTrigger);
        tvButtonsPressed = findViewById(R.id.TVButtonsPressed);
        tvVibrationStatus = findViewById(R.id.TVVibrationStatus);
        btTestVibration = findViewById(R.id.BTTestVibration);
        leftStickView = findViewById(R.id.LeftStickView);
        rightStickView = findViewById(R.id.RightStickView);

        // Set colors for stick views
        leftStickView.setDotColor(0xFF4CAF50);
        rightStickView.setDotColor(0xFF2196F3);

        btBack.setOnClickListener(v -> finish());

        // Detect connected gamepads
        detectGamepads();

        // Initialize vibrator from gamepad
        initializeVibrator();

        // Setup vibration test button
        setupVibrationButton();
    }

    private void detectGamepads() {
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && isGamepad(device)) {
                currentDevice = device;
                tvDeviceInfo.setText(String.format(Locale.US, 
                    "%s\nVendor: %04X Product: %04X",
                    device.getName(),
                    device.getVendorId(),
                    device.getProductId()));
                return;
            }
        }
        tvDeviceInfo.setText("No gamepad detected\nConnect a gamepad and restart");
    }

    private boolean isGamepad(InputDevice device) {
        int sources = device.getSources();
        return ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
               ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            event.getAction() == MotionEvent.ACTION_MOVE) {
            
            // Left stick
            float leftX = getCenteredAxis(event, MotionEvent.AXIS_X);
            float leftY = getCenteredAxis(event, MotionEvent.AXIS_Y);
            tvLeftStickX.setText(String.format(Locale.US, "%.2f", leftX));
            tvLeftStickY.setText(String.format(Locale.US, "%.2f", leftY));
            leftStickView.setPosition(leftX, leftY);

            // Right stick
            float rightX = getCenteredAxis(event, MotionEvent.AXIS_Z);
            float rightY = getCenteredAxis(event, MotionEvent.AXIS_RZ);
            tvRightStickX.setText(String.format(Locale.US, "%.2f", rightX));
            tvRightStickY.setText(String.format(Locale.US, "%.2f", rightY));
            rightStickView.setPosition(rightX, rightY);

            // Triggers
            float leftTrigger = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
            float rightTrigger = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
            tvLeftTrigger.setText(String.format(Locale.US, "%.2f", leftTrigger));
            tvRightTrigger.setText(String.format(Locale.US, "%.2f", rightTrigger));

            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private float getCenteredAxis(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        if (device != null) {
            InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
            if (range != null) {
                float flat = range.getFlat();
                float value = event.getAxisValue(axis);
                if (Math.abs(value) > flat) {
                    return value;
                }
            }
        }
        return 0;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isGamepadButton(keyCode)) {
            String buttonName = getButtonName(keyCode);
            if (!pressedButtons.contains(buttonName)) {
                pressedButtons.add(buttonName);
                updateButtonsDisplay();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isGamepadButton(keyCode)) {
            String buttonName = getButtonName(keyCode);
            pressedButtons.remove(buttonName);
            updateButtonsDisplay();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean isGamepadButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_L2:
            case KeyEvent.KEYCODE_BUTTON_R2:
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
            case KeyEvent.KEYCODE_BUTTON_MODE:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    private String getButtonName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "LB";
            case KeyEvent.KEYCODE_BUTTON_R1: return "RB";
            case KeyEvent.KEYCODE_BUTTON_L2: return "LT";
            case KeyEvent.KEYCODE_BUTTON_R2: return "RT";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "L3";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "R3";
            case KeyEvent.KEYCODE_BUTTON_START: return "START";
            case KeyEvent.KEYCODE_BUTTON_SELECT: return "SELECT";
            case KeyEvent.KEYCODE_BUTTON_MODE: return "HOME";
            case KeyEvent.KEYCODE_DPAD_UP: return "D-UP";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "D-DOWN";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "D-LEFT";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "D-RIGHT";
            default: return "UNKNOWN";
        }
    }

    private void updateButtonsDisplay() {
        if (pressedButtons.isEmpty()) {
            tvButtonsPressed.setText("None");
        } else {
            tvButtonsPressed.setText(String.join(", ", pressedButtons));
        }
    }

    private void initializeVibrator() {
        // Get vibrator from the gamepad device, not from phone
        if (currentDevice != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager = currentDevice.getVibratorManager();
                if (vibratorManager != null) {
                    vibrator = vibratorManager.getDefaultVibrator();
                }
            } else {
                vibrator = currentDevice.getVibrator();
            }
        }
        
        // If gamepad doesn't have vibrator, show message
        if (vibrator == null || !vibrator.hasVibrator()) {
            tvVibrationStatus.setText("Vibration: Not Supported on Gamepad");
            tvVibrationStatus.setTextColor(0xFFFF5722);
            btTestVibration.setEnabled(false);
        }
    }

    private void setupVibrationButton() {
        btTestVibration.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startVibration();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    stopVibration();
                    return true;
            }
            return false;
        });
    }

    private void startVibration() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect effect = VibrationEffect.createWaveform(
                    new long[]{0, 100, 100},
                    new int[]{0, 128, 0},
                    0  // Repeat indefinitely
                );
                vibrator.vibrate(effect);
            } else {
                vibrator.vibrate(new long[]{0, 100, 100}, 0);
            }
            isVibrating = true;
            tvVibrationStatus.setText("Vibration: ON");
            tvVibrationStatus.setTextColor(0xFF4CAF50);
            btTestVibration.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50));
        } else {
            tvVibrationStatus.setText("Vibration: Not Supported");
            tvVibrationStatus.setTextColor(0xFFFF5722);
        }
    }

    private void stopVibration() {
        if (vibrator != null && isVibrating) {
            vibrator.cancel();
            isVibrating = false;
            tvVibrationStatus.setText("Vibration: OFF");
            tvVibrationStatus.setTextColor(0xFFAAAAAA);
            btTestVibration.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF673AB7));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopVibration();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVibration();
    }
}
