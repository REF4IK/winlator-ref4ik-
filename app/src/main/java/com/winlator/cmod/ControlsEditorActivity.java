package com.winlator.cmod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.Binding;
import com.winlator.cmod.inputcontrols.ControlElement;
import com.winlator.cmod.inputcontrols.ControlsProfile;
import com.winlator.cmod.inputcontrols.CustomIconManager;
import com.winlator.cmod.inputcontrols.IconPackManager;
import com.winlator.cmod.inputcontrols.IconPickerDialog;
import com.winlator.cmod.inputcontrols.InputControlsManager;
import com.winlator.cmod.math.Mathf;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.widget.InputControlsView;
import com.winlator.cmod.widget.NumberPicker;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public class ControlsEditorActivity extends AppCompatActivity implements View.OnClickListener {
    private InputControlsView inputControlsView;
    private ControlsProfile profile;
    private CustomIconManager customIconManager;
    private IconPackManager iconPackManager;
    
    private ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Uri imageUri = result.getData().getData();
                if (imageUri != null) {
                    importCustomIcon(imageUri);
                }
            }
        }
    );

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AppUtils.hideSystemUI(this);
        setContentView(R.layout.controls_editor_activity);
        
        // Initialize custom icon manager
        customIconManager = new CustomIconManager(this);
        iconPackManager = new IconPackManager(this);

        inputControlsView = new InputControlsView(this);
        inputControlsView.setEditMode(true);
        inputControlsView.setOverlayOpacity(0.6f);

        profile = InputControlsManager.loadProfile(this, ControlsProfile.getProfileFile(this, getIntent().getIntExtra("profile_id", 0)));
        ((TextView)findViewById(R.id.TVProfileName)).setText(profile.getName());
        inputControlsView.setProfile(profile);

        FrameLayout container = findViewById(R.id.FLContainer);
        container.addView(inputControlsView, 0);

        container.findViewById(R.id.BTAddElement).setOnClickListener(this);
        container.findViewById(R.id.BTUndo).setOnClickListener(this);
        container.findViewById(R.id.BTRedo).setOnClickListener(this);
        container.findViewById(R.id.BTRemoveElement).setOnClickListener(this);
        container.findViewById(R.id.BTElementSettings).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.BTAddElement:
                showQuickAddControlDialog();
                break;
            case R.id.BTUndo:
                if (!inputControlsView.undoLastEdit()) AppUtils.showToast(this, R.string.no_changes_to_undo);
                break;
            case R.id.BTRedo:
                if (!inputControlsView.redoLastEdit()) AppUtils.showToast(this, R.string.no_changes_to_redo);
                break;
            case R.id.BTRemoveElement:
                if (!inputControlsView.removeElement()) {
                    AppUtils.showToast(this, R.string.no_control_element_selected);
                }
                break;
            case R.id.BTElementSettings:
                ControlElement selectedElement = inputControlsView.getSelectedElement();
                if (selectedElement != null) {
                    showControlElementSettings(v);
                }
                else AppUtils.showToast(this, R.string.no_control_element_selected);
                break;
        }
    }

    private void showQuickAddControlDialog() {
        final Binding[] bindings = {
            Binding.KEY_W, Binding.KEY_A, Binding.KEY_S, Binding.KEY_D,
            Binding.KEY_SPACE, Binding.KEY_ENTER, Binding.KEY_ESC,
            Binding.MOUSE_LEFT_BUTTON, Binding.MOUSE_RIGHT_BUTTON,
            Binding.GAMEPAD_BUTTON_A, Binding.GAMEPAD_BUTTON_B, Binding.GAMEPAD_BUTTON_X, Binding.GAMEPAD_BUTTON_Y
        };
        String[] items = new String[bindings.length + 5];
        items[0] = "Button";
        items[1] = "Combo Button";
        items[2] = "D-Pad";
        items[3] = "Stick";
        items[4] = "Trackpad";
        for (int i = 0; i < bindings.length; i++) items[i + 5] = bindings[i].toString();

        new AlertDialog.Builder(this)
            .setTitle(R.string.quick_add_control)
            .setItems(items, (dialog, which) -> {
                boolean added;
                if (which == 0) added = inputControlsView.addElement(ControlElement.Type.BUTTON, Binding.NONE);
                else if (which == 1) added = inputControlsView.addElement(ControlElement.Type.COMBO_BUTTON, Binding.KEY_CTRL_L);
                else if (which == 2) added = inputControlsView.addElement(ControlElement.Type.D_PAD, Binding.NONE);
                else if (which == 3) added = inputControlsView.addElement(ControlElement.Type.STICK, Binding.NONE);
                else if (which == 4) added = inputControlsView.addElement(ControlElement.Type.TRACKPAD, Binding.NONE);
                else added = inputControlsView.addElement(ControlElement.Type.BUTTON, bindings[which - 5]);

                if (!added) AppUtils.showToast(this, R.string.no_profile_selected);
            })
            .show();
    }

    private void showControlElementSettings(View anchorView) {
        final ControlElement element = inputControlsView.getSelectedElement();
        View view = LayoutInflater.from(this).inflate(R.layout.control_element_settings, null);

        final Runnable updateLayout = () -> {
            ControlElement.Type type = element.getType();
            view.findViewById(R.id.LLShape).setVisibility(View.GONE);
            view.findViewById(R.id.CBToggleSwitch).setVisibility(View.GONE);
            view.findViewById(R.id.LLCustomTextIcon).setVisibility(View.GONE);
            view.findViewById(R.id.LLRangeOptions).setVisibility(View.GONE);

            if (type == ControlElement.Type.BUTTON || type == ControlElement.Type.COMBO_BUTTON) {
                view.findViewById(R.id.LLShape).setVisibility(View.VISIBLE);
                view.findViewById(R.id.CBToggleSwitch).setVisibility(View.VISIBLE);
                view.findViewById(R.id.LLCustomTextIcon).setVisibility(View.VISIBLE);
            }
            else if (type == ControlElement.Type.RANGE_BUTTON) {
                view.findViewById(R.id.LLRangeOptions).setVisibility(View.VISIBLE);
            }

            loadBindingSpinners(element, view);
        };

        loadTypeSpinner(element, view.findViewById(R.id.SType), updateLayout);
        loadShapeSpinner(element, view.findViewById(R.id.SShape));
        loadRangeSpinner(element, view.findViewById(R.id.SRange));

        RadioGroup rgOrientation = view.findViewById(R.id.RGOrientation);
        rgOrientation.check(element.getOrientation() == 1 ? R.id.RBVertical : R.id.RBHorizontal);
        rgOrientation.setOnCheckedChangeListener((group, checkedId) -> {
            inputControlsView.saveSelectedElementStateForUndo();
            element.setOrientation((byte)(checkedId == R.id.RBVertical ? 1 : 0));
            profile.save();
            inputControlsView.invalidate();
        });

        NumberPicker npColumns = view.findViewById(R.id.NPColumns);
        npColumns.setValue(element.getBindingCount());
        npColumns.setOnValueChangeListener((numberPicker, value) -> {
            inputControlsView.saveSelectedElementStateForUndo();
            element.setBindingCount(value);
            profile.save();
            inputControlsView.invalidate();
        });

        final TextView tvScale = view.findViewById(R.id.TVScale);
        SeekBar sbScale = view.findViewById(R.id.SBScale);
        sbScale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvScale.setText(progress+"%");
                if (fromUser) {
                    progress = (int)Mathf.roundTo(progress, 5);
                    seekBar.setProgress(progress);
                    element.setScale(progress / 100.0f);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                inputControlsView.saveSelectedElementStateForUndo();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbScale.setProgress((int)(element.getScale() * 100));

        // Setup icon size slider
        final TextView tvIconSize = view.findViewById(R.id.TVIconSize);
        SeekBar sbIconSize = view.findViewById(R.id.SBIconSize);
        sbIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvIconSize.setText(progress+"%");
                if (fromUser) {
                    progress = (int)Mathf.roundTo(progress, 5);
                    seekBar.setProgress(progress);
                    // Устанавливаем размер иконки отдельно от размера кнопки
                    element.setIconScale(progress / 100.0f);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                inputControlsView.saveSelectedElementStateForUndo();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        // Set initial progress based on element's icon scale
        sbIconSize.setProgress((int)(element.getIconScale() * 100));
        
        CheckBox cbToggleSwitch = view.findViewById(R.id.CBToggleSwitch);
        cbToggleSwitch.setChecked(element.isToggleSwitch());
        cbToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            inputControlsView.saveSelectedElementStateForUndo();
            element.setToggleSwitch(isChecked);
            profile.save();
        });

        CheckBox cbHideBorder = view.findViewById(R.id.CBHideBorder);
        cbHideBorder.setChecked(element.isHideBorder());
        cbHideBorder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            inputControlsView.saveSelectedElementStateForUndo();
            element.setHideBorder(isChecked);
            profile.save();
            inputControlsView.invalidate();
        });

        // Opacity slider
        final TextView tvOpacity = view.findViewById(R.id.TVOpacity);
        final SeekBar sbOpacity = view.findViewById(R.id.SBOpacity);
        tvOpacity.setText((int)(element.getOpacity() * 100) + "%");
        sbOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvOpacity.setText(progress + "%");
                    element.setOpacity(progress / 100.0f);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                inputControlsView.saveSelectedElementStateForUndo();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbOpacity.setProgress((int)(element.getOpacity() * 100));

        final TextView tvFillOpacity = view.findViewById(R.id.TVFillOpacity);
        final SeekBar sbFillOpacity = view.findViewById(R.id.SBFillOpacity);
        tvFillOpacity.setText((int)(element.getFillOpacity() * 100) + "%");
        sbFillOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvFillOpacity.setText(progress + "%");
                if (fromUser) {
                    element.setFillOpacity(progress / 100.0f);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                inputControlsView.saveSelectedElementStateForUndo();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbFillOpacity.setProgress((int)(element.getFillOpacity() * 100));

        final TextView tvRotation = view.findViewById(R.id.TVRotation);
        final SeekBar sbRotation = view.findViewById(R.id.SBRotation);
        tvRotation.setText((int)element.getRotation() + " deg");
        sbRotation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvRotation.setText(progress + " deg");
                if (fromUser) {
                    element.setRotation(progress);
                    profile.save();
                    inputControlsView.invalidate();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                inputControlsView.saveSelectedElementStateForUndo();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sbRotation.setProgress((int)element.getRotation());

        loadColorPalette(view.findViewById(R.id.LLColorPalette), element);

        final EditText etCustomText = view.findViewById(R.id.ETCustomText);
        etCustomText.setText(element.getText());
        final LinearLayout llIconList = view.findViewById(R.id.LLIconList);

        // Show currently selected icon (or "+" if none)
        refreshIconList(llIconList, element);

        // "Select icon" button - opens picker
        view.findViewById(R.id.BTAddCustomIcon).setOnClickListener((v) -> {
            IconPickerDialog dialog = new IconPickerDialog(this, customIconManager, iconPackManager,
                    element.getIconId(), (iconId) -> {
                        element.setIconId(iconId);
                        refreshIconList(llIconList, element);
                    });
            dialog.show();
        });

        updateLayout.run();

        PopupWindow popupWindow = AppUtils.showPopupWindow(anchorView, view, 340, 0);
        popupWindow.setOnDismissListener(() -> {
            String text = etCustomText.getText().toString().trim();
            // iconId was already set live as user picks; just save current value
            element.setText(text);
            profile.save();
            inputControlsView.invalidate();
        });
    }

    private void refreshIconList(LinearLayout llIconList, ControlElement element) {
        llIconList.removeAllViews();
        int currentIconId = element.getIconId();
        int size = (int) UnitUtils.dpToPx(40);
        int margin = (int) UnitUtils.dpToPx(2);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, margin, margin, margin);

        if (currentIconId > 0) {
            ImageView iv = new ImageView(this);
            iv.setLayoutParams(params);
            iv.setBackgroundResource(R.drawable.icon_background);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(4, 4, 4, 4);
            // Load icon from custom icons or pack icons
            android.graphics.Bitmap bm = null;
            if (IconPackManager.isPackIcon(currentIconId)) {
                bm = iconPackManager.loadIconByGlobalId(currentIconId);
            } else if (CustomIconManager.isCustomIcon(currentIconId)) {
                bm = customIconManager.loadIcon(currentIconId);
            }
            if (bm != null) iv.setImageBitmap(bm);
            llIconList.addView(iv);
        } else {
            // "+ Add icon" button
            TextView addTv = new TextView(this);
            addTv.setText("+ " + getString(R.string.add_single_icon));
            addTv.setTextColor(0xffaaaaaa);
            addTv.setTextSize(13);
            addTv.setGravity(android.view.Gravity.CENTER);
            addTv.setPadding((int) UnitUtils.dpToPx(12), (int) UnitUtils.dpToPx(8),
                    (int) UnitUtils.dpToPx(12), (int) UnitUtils.dpToPx(8));
            addTv.setBackgroundColor(0xff2a2a2a);
            final ControlElement el = element;
            addTv.setOnClickListener((v) -> {
                IconPickerDialog dialog = new IconPickerDialog(this, customIconManager, iconPackManager,
                        el.getIconId(), (iconId) -> {
                            el.setIconId(iconId);
                            refreshIconList(llIconList, el);
                        });
                dialog.show();
            });
            llIconList.addView(addTv);
        }
    }

    private void loadTypeSpinner(final ControlElement element, Spinner spinner, Runnable callback) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Type.names()));
        spinner.setSelection(element.getType().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                inputControlsView.saveSelectedElementStateForUndo();
                element.setType(ControlElement.Type.values()[position]);
                profile.save();
                callback.run();
                inputControlsView.invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadShapeSpinner(final ControlElement element, Spinner spinner) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Shape.names()));
        spinner.setSelection(element.getShape().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                inputControlsView.saveSelectedElementStateForUndo();
                element.setShape(ControlElement.Shape.values()[position]);
                profile.save();
                inputControlsView.invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadBindingSpinners(ControlElement element, View view) {
        LinearLayout container = view.findViewById(R.id.LLBindings);
        container.removeAllViews();

        ControlElement.Type type = element.getType();
        if (type == ControlElement.Type.BUTTON) {
            loadBindingSpinner(element, container, 0, R.string.binding);
        }
        else if (type == ControlElement.Type.COMBO_BUTTON) {
            for (int i = 0; i < element.getBindingCount(); i++) loadBindingSpinner(element, container, i, R.string.binding);
        }
        else if (type == ControlElement.Type.D_PAD || type == ControlElement.Type.STICK || type == ControlElement.Type.TRACKPAD) {
            loadBindingSpinner(element, container, 0, R.string.binding_up);
            loadBindingSpinner(element, container, 1, R.string.binding_right);
            loadBindingSpinner(element, container, 2, R.string.binding_down);
            loadBindingSpinner(element, container, 3, R.string.binding_left);
        }
        else if (type == ControlElement.Type.STEERING_WHEEL) {
            loadBindingSpinner(element, container, 1, R.string.binding_right);
            loadBindingSpinner(element, container, 3, R.string.binding_left);
        }
    }

    private void loadBindingSpinner(final ControlElement element, LinearLayout container, final int index, int titleResId) {
        View view = LayoutInflater.from(this).inflate(R.layout.binding_field, container, false);
        ((TextView)view.findViewById(R.id.TVTitle)).setText(titleResId);
        view.findViewById(R.id.SBindingType).setVisibility(View.GONE);
        view.findViewById(R.id.SBinding).setVisibility(View.GONE);

        LinearLayout row = (LinearLayout)((ViewGroup)view).getChildAt(1);
        Button bindingButton = createPickerButton(element.getBindingAt(index).toString(), false);
        bindingButton.setOnClickListener(v -> showBindingPicker(element, index, bindingButton));
        row.addView(bindingButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        container.addView(view);
    }

    private void showBindingPicker(final ControlElement element, final int index, final TextView targetView) {
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)UnitUtils.dpToPx(8);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xff1f1f1f);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView backButton = new TextView(this);
        backButton.setText("←");
        backButton.setTextColor(Color.WHITE);
        backButton.setTextSize(22);
        backButton.setGravity(Gravity.CENTER);
        backButton.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
        });
        header.addView(backButton, new LinearLayout.LayoutParams((int)UnitUtils.dpToPx(34), (int)UnitUtils.dpToPx(36)));

        TextView title = new TextView(this);
        title.setText(R.string.select_key);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button noneButton = createPickerButton(getString(R.string.none), false);
        noneButton.setTextColor(0xffff6666);
        noneButton.setTextSize(13);
        noneButton.setOnClickListener(v -> applyBinding(element, index, Binding.NONE, targetView, dialogRef[0]));
        LinearLayout.LayoutParams noneParams = new LinearLayout.LayoutParams((int)UnitUtils.dpToPx(90), (int)UnitUtils.dpToPx(34));
        header.addView(noneButton, noneParams);
        root.addView(header);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(0, (int)UnitUtils.dpToPx(4), 0, 0);
        root.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)UnitUtils.dpToPx(58)));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, (int)UnitUtils.dpToPx(4), 0, 0);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)UnitUtils.dpToPx(255)));

        TextView keyboardTab = createCategoryTab("⌨", getString(R.string.main_keyboard), true);
        TextView mouseTab = createCategoryTab("◖", getString(R.string.mouse_buttons), false);
        TextView gamepadTab = createCategoryTab("✚", getString(R.string.gamepad), false);
        tabs.addView(keyboardTab, categoryTabParams());
        tabs.addView(mouseTab, categoryTabParams());
        tabs.addView(gamepadTab, categoryTabParams());

        keyboardTab.setOnClickListener(v -> {
            setCategoryTabAccent(keyboardTab, mouseTab, gamepadTab);
            populateKeyboardLayout(content, element, index, targetView, dialogRef);
        });
        mouseTab.setOnClickListener(v -> {
            setCategoryTabAccent(mouseTab, keyboardTab, gamepadTab);
            populateMouseLayout(content, element, index, targetView, dialogRef);
        });
        gamepadTab.setOnClickListener(v -> {
            setCategoryTabAccent(gamepadTab, keyboardTab, mouseTab);
            populateGamepadLayout(content, element, index, targetView, dialogRef);
        });

        AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();
        dialogRef[0] = dialog;
        populateKeyboardLayout(content, element, index, targetView, dialogRef);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void applyBinding(ControlElement element, int index, Binding binding, TextView targetView, AlertDialog dialog) {
        if (binding != element.getBindingAt(index)) {
            inputControlsView.saveSelectedElementStateForUndo();
            element.setBindingAt(index, binding);
            targetView.setText(binding.toString());
            profile.save();
            inputControlsView.invalidate();
        }
        if (dialog != null) dialog.dismiss();
    }

    private void populateKeyboardLayout(LinearLayout content, ControlElement element, int index, TextView targetView, AlertDialog[] dialogRef) {
        content.removeAllViews();
        addBindingRow(content, element, index, targetView, dialogRef, Binding.KEY_ESC, Binding.KEY_F1, Binding.KEY_F2, Binding.KEY_F3, Binding.KEY_F4, Binding.KEY_F5, Binding.KEY_F6, Binding.KEY_F7, Binding.KEY_F8, Binding.KEY_F9, Binding.KEY_F10, Binding.KEY_F11, Binding.KEY_F12);
        addBindingRow(content, element, index, targetView, dialogRef, Binding.KEY_GRAVE, Binding.KEY_1, Binding.KEY_2, Binding.KEY_3, Binding.KEY_4, Binding.KEY_5, Binding.KEY_6, Binding.KEY_7, Binding.KEY_8, Binding.KEY_9, Binding.KEY_0, Binding.KEY_MINUS, Binding.KEY_BKSP);
        addBindingRow(content, element, index, targetView, dialogRef, Binding.KEY_TAB, Binding.KEY_Q, Binding.KEY_W, Binding.KEY_E, Binding.KEY_R, Binding.KEY_T, Binding.KEY_Y, Binding.KEY_U, Binding.KEY_I, Binding.KEY_O, Binding.KEY_P, Binding.KEY_BRACKET_LEFT, Binding.KEY_BRACKET_RIGHT, Binding.KEY_BACKSLASH);
        addBindingRow(content, element, index, targetView, dialogRef, Binding.KEY_CAPS_LOCK, Binding.KEY_A, Binding.KEY_S, Binding.KEY_D, Binding.KEY_F, Binding.KEY_G, Binding.KEY_H, Binding.KEY_J, Binding.KEY_K, Binding.KEY_L, Binding.KEY_SEMICOLON, Binding.KEY_APOSTROPHE, Binding.KEY_ENTER);
        addBindingRow(content, element, index, targetView, dialogRef, Binding.KEY_SHIFT_L, Binding.KEY_Z, Binding.KEY_X, Binding.KEY_C, Binding.KEY_V, Binding.KEY_B, Binding.KEY_N, Binding.KEY_M, Binding.KEY_COMMA, Binding.KEY_PERIOD, Binding.KEY_SLASH, Binding.KEY_SHIFT_R);
        addBindingRow(content, element, index, targetView, dialogRef, Binding.KEY_CTRL_L, Binding.KEY_ALT_L, Binding.KEY_SPACE, Binding.KEY_ALT_R, Binding.KEY_CTRL_R, Binding.KEY_INSERT, Binding.KEY_HOME, Binding.KEY_PG_UP, Binding.KEY_DEL, Binding.KEY_END, Binding.KEY_PG_DOWN, Binding.KEY_UP, Binding.KEY_LEFT, Binding.KEY_DOWN, Binding.KEY_RIGHT);
    }

    private void populateMouseLayout(LinearLayout content, ControlElement element, int index, TextView targetView, AlertDialog[] dialogRef) {
        content.removeAllViews();
        addBindingRow(content, element, index, targetView, dialogRef, Binding.MOUSE_LEFT_BUTTON, Binding.MOUSE_RIGHT_BUTTON, Binding.MOUSE_MIDDLE_BUTTON, Binding.MOUSE_SCROLL_UP);
        addBindingRow(content, element, index, targetView, dialogRef, Binding.MOUSE_SCROLL_DOWN, Binding.MOUSE_MOVE_UP, Binding.MOUSE_MOVE_DOWN, Binding.MOUSE_MOVE_LEFT);
        addBindingRow(content, element, index, targetView, dialogRef, Binding.MOUSE_MOVE_RIGHT);
    }

    private void populateGamepadLayout(LinearLayout content, ControlElement element, int index, TextView targetView, AlertDialog[] dialogRef) {
        content.removeAllViews();
        FrameLayout pad = new FrameLayout(this);
        content.addView(pad, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int)UnitUtils.dpToPx(250)));

        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_L2, 11, 7, 7, 4);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_L1, 20, 7, 7, 4);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_R1, 73, 7, 7, 4);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_R2, 82, 7, 7, 4);

        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_DPAD_UP, 19, 72, 7, 7);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_DPAD_LEFT, 15, 116, 7, 7);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_DPAD_RIGHT, 23, 116, 7, 7);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_DPAD_DOWN, 19, 160, 7, 7);

        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_SELECT, 42, 94, 8, 4);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_START, 51, 94, 8, 4);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_L3, 43, 150, 6, 6);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_R3, 53, 150, 6, 6);

        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_Y, 78, 62, 7, 7);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_X, 72, 116, 7, 7);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_B, 84, 116, 7, 7);
        addPadButton(pad, element, index, targetView, dialogRef, Binding.GAMEPAD_BUTTON_A, 78, 170, 7, 7);
    }

    private void addPadButton(FrameLayout pad, ControlElement element, int index, TextView targetView, AlertDialog[] dialogRef, Binding binding, int leftPercent, int topDp, int widthPercent, int heightPercent) {
        Button button = createBindingButton(binding, binding == element.getBindingAt(index));
        button.setOnClickListener(v -> applyBinding(element, index, binding, targetView, dialogRef[0]));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(0, 0);
        params.leftMargin = leftPercent;
        params.topMargin = (int)UnitUtils.dpToPx(topDp);
        params.width = widthPercent;
        params.height = heightPercent;
        button.setLayoutParams(params);
        pad.addView(button);

        pad.post(() -> {
            ViewGroup.LayoutParams lp = button.getLayoutParams();
            if (lp instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams)lp;
                flp.leftMargin = pad.getWidth() * leftPercent / 100;
                flp.width = Math.max((int)UnitUtils.dpToPx(52), pad.getWidth() * widthPercent / 100);
                flp.height = Math.max((int)UnitUtils.dpToPx(34), pad.getHeight() * heightPercent / 100);
                button.setLayoutParams(flp);
            }
        });
    }

    private void addBindingRow(LinearLayout content, ControlElement element, int index, TextView targetView, AlertDialog[] dialogRef, Binding... bindings) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, (int)UnitUtils.dpToPx(3));
        for (Binding binding : bindings) {
            Button button = createBindingButton(binding, binding == element.getBindingAt(index));
            button.setOnClickListener(v -> applyBinding(element, index, binding, targetView, dialogRef[0]));
            row.addView(button, keyParams(binding));
        }
        content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout.LayoutParams categoryTabParams() {
        int margin = (int)UnitUtils.dpToPx(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(margin, 0, margin, 0);
        return params;
    }

    private LinearLayout.LayoutParams keyParams(Binding binding) {
        float weight = 1.0f;
        if (binding == Binding.KEY_SPACE) weight = 4.0f;
        else if (binding == Binding.KEY_SHIFT_L || binding == Binding.KEY_SHIFT_R) weight = 1.8f;
        else if (binding == Binding.KEY_ENTER || binding == Binding.KEY_BKSP || binding == Binding.KEY_CAPS_LOCK || binding == Binding.KEY_TAB) weight = 1.55f;
        else if (binding.isMouse()) weight = 2.3f;
        else if (binding.isGamepad()) weight = 1.2f;

        int margin = (int)UnitUtils.dpToPx(1);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, (int)UnitUtils.dpToPx(29), weight);
        params.setMargins(margin, 0, margin, 0);
        return params;
    }

    private void setCategoryTabAccent(TextView active, TextView inactive1, TextView inactive2) {
        active.setBackground(makeCategoryBackground(true));
        inactive1.setBackground(makeCategoryBackground(false));
        inactive2.setBackground(makeCategoryBackground(false));
    }

    private Button createPickerButton(String text, boolean accent) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(10);
        button.setGravity(Gravity.CENTER);
        button.setBackground(makePickerBackground(accent));
        return button;
    }

    private Button createKeyButton(String text, boolean accent) {
        Button button = createPickerButton(text, accent);
        button.setTextSize(10);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(1, 0, 1, 0);
        return button;
    }

    private Button createBindingButton(Binding binding, boolean accent) {
        Button button = createKeyButton(bindingLabel(binding), accent);
        if (binding.isMouse()) {
            button.setTextSize(10);
        }
        else if (binding.isGamepad()) {
            button.setTextSize(11);
            button.setBackground(makeGamepadBackground(binding, accent));
            if (binding == Binding.GAMEPAD_BUTTON_A || binding == Binding.GAMEPAD_BUTTON_B ||
                binding == Binding.GAMEPAD_BUTTON_X || binding == Binding.GAMEPAD_BUTTON_Y) {
                button.setTextColor(Color.BLACK);
                button.setTypeface(null, android.graphics.Typeface.BOLD);
            }
        }
        return button;
    }

    private String bindingLabel(Binding binding) {
        switch (binding) {
            case MOUSE_LEFT_BUTTON: return "(L)\n" + getString(R.string.mouse_left_button_short);
            case MOUSE_RIGHT_BUTTON: return "(R)\n" + getString(R.string.mouse_right_button_short);
            case MOUSE_MIDDLE_BUTTON: return "(M)\n" + getString(R.string.mouse_middle_button_short);
            case MOUSE_SCROLL_UP: return "^\n" + getString(R.string.mouse_scroll_up_short);
            case MOUSE_SCROLL_DOWN: return "v\n" + getString(R.string.mouse_scroll_down_short);
            case MOUSE_MOVE_UP: return "^\n" + getString(R.string.move_up_short);
            case MOUSE_MOVE_DOWN: return "v\n" + getString(R.string.move_down_short);
            case MOUSE_MOVE_LEFT: return "<\n" + getString(R.string.move_left_short);
            case MOUSE_MOVE_RIGHT: return ">\n" + getString(R.string.move_right_short);
            case KEY_BKSP: return "Bksp";
            case KEY_TAB: return "Tab";
            case KEY_CAPS_LOCK: return "Caps";
            case KEY_ENTER: return "Enter";
            case KEY_SHIFT_L: return "L Shift";
            case KEY_SHIFT_R: return "R Shift";
            case KEY_CTRL_L: return "L Ctrl";
            case KEY_CTRL_R: return "R Ctrl";
            case KEY_ALT_L: return "L Alt";
            case KEY_ALT_R: return "R Alt";
            case KEY_SPACE: return "Space";
            case KEY_INSERT: return "Ins";
            case KEY_PG_UP: return "PgUp";
            case KEY_PG_DOWN: return "PgDn";
            case KEY_DEL: return "Del";
            case KEY_UP: return "^";
            case KEY_LEFT: return "<";
            case KEY_DOWN: return "v";
            case KEY_RIGHT: return ">";
            case GAMEPAD_BUTTON_L2: return "LT";
            case GAMEPAD_BUTTON_L1: return "LB";
            case GAMEPAD_BUTTON_R1: return "RB";
            case GAMEPAD_BUTTON_R2: return "RT";
            case GAMEPAD_BUTTON_SELECT: return "SELECT";
            case GAMEPAD_BUTTON_START: return "START";
            case GAMEPAD_BUTTON_A: return "A";
            case GAMEPAD_BUTTON_B: return "B";
            case GAMEPAD_BUTTON_X: return "X";
            case GAMEPAD_BUTTON_Y: return "Y";
            case GAMEPAD_BUTTON_L3: return "L3";
            case GAMEPAD_BUTTON_R3: return "R3";
            case GAMEPAD_DPAD_UP: return "D^";
            case GAMEPAD_DPAD_LEFT: return "D<";
            case GAMEPAD_DPAD_RIGHT: return "D>";
            case GAMEPAD_DPAD_DOWN: return "Dv";
            case GAMEPAD_LEFT_THUMB_UP: return "L^";
            case GAMEPAD_LEFT_THUMB_RIGHT: return "L>";
            case GAMEPAD_LEFT_THUMB_DOWN: return "Lv";
            case GAMEPAD_LEFT_THUMB_LEFT: return "L<";
            case GAMEPAD_RIGHT_THUMB_UP: return "R^";
            case GAMEPAD_RIGHT_THUMB_RIGHT: return "R>";
            case GAMEPAD_RIGHT_THUMB_DOWN: return "Rv";
            case GAMEPAD_RIGHT_THUMB_LEFT: return "R<";
            default: return binding.toString();
        }
    }

    private TextView createCategoryTab(String icon, String title, boolean accent) {
        TextView tab = new TextView(this);
        tab.setText(icon + "\n" + title);
        tab.setTextColor(Color.WHITE);
        tab.setTextSize(11);
        tab.setGravity(Gravity.CENTER);
        tab.setTypeface(null, android.graphics.Typeface.BOLD);
        tab.setBackground(makeCategoryBackground(accent));
        return tab;
    }

    private GradientDrawable makePickerBackground(boolean accent) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(accent ? 0xff11a9ef : 0xff353535);
        drawable.setCornerRadius(UnitUtils.dpToPx(5));
        drawable.setStroke((int)UnitUtils.dpToPx(1), accent ? 0xff5bdcff : 0xff444444);
        return drawable;
    }

    private GradientDrawable makeGamepadBackground(Binding binding, boolean accent) {
        int color = 0xff353535;
        if (binding == Binding.GAMEPAD_BUTTON_A || binding == Binding.GAMEPAD_BUTTON_Y) color = 0xffffe000;
        else if (binding == Binding.GAMEPAD_BUTTON_B) color = 0xffff2b2b;
        else if (binding == Binding.GAMEPAD_BUTTON_X) color = 0xff03a9f4;
        else if (binding == Binding.GAMEPAD_BUTTON_SELECT || binding == Binding.GAMEPAD_BUTTON_START) color = 0xff4a4a4a;

        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(UnitUtils.dpToPx(binding == Binding.GAMEPAD_BUTTON_A ||
            binding == Binding.GAMEPAD_BUTTON_B || binding == Binding.GAMEPAD_BUTTON_X ||
            binding == Binding.GAMEPAD_BUTTON_Y ? 18 : 6));
        drawable.setStroke((int)UnitUtils.dpToPx(accent ? 2 : 1), accent ? 0xff5bdcff : 0xff444444);
        return drawable;
    }

    private GradientDrawable makeCategoryBackground(boolean accent) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(accent ? 0x1600aaff : Color.TRANSPARENT);
        drawable.setStroke((int)UnitUtils.dpToPx(1), accent ? 0xffa8c9ff : Color.TRANSPARENT);
        return drawable;
    }

    private Binding[] keyboardBindings() {
        return new Binding[] {
            Binding.KEY_ESC, Binding.KEY_F1, Binding.KEY_F2, Binding.KEY_F3, Binding.KEY_F4, Binding.KEY_F5, Binding.KEY_F6, Binding.KEY_F7, Binding.KEY_F8, Binding.KEY_F9, Binding.KEY_F10, Binding.KEY_F11, Binding.KEY_F12,
            Binding.KEY_GRAVE, Binding.KEY_1, Binding.KEY_2, Binding.KEY_3, Binding.KEY_4, Binding.KEY_5, Binding.KEY_6, Binding.KEY_7, Binding.KEY_8, Binding.KEY_9, Binding.KEY_0, Binding.KEY_MINUS, Binding.KEY_BKSP,
            Binding.KEY_TAB, Binding.KEY_Q, Binding.KEY_W, Binding.KEY_E, Binding.KEY_R, Binding.KEY_T, Binding.KEY_Y, Binding.KEY_U, Binding.KEY_I, Binding.KEY_O, Binding.KEY_P, Binding.KEY_BRACKET_LEFT, Binding.KEY_BRACKET_RIGHT, Binding.KEY_BACKSLASH,
            Binding.KEY_CAPS_LOCK, Binding.KEY_A, Binding.KEY_S, Binding.KEY_D, Binding.KEY_F, Binding.KEY_G, Binding.KEY_H, Binding.KEY_J, Binding.KEY_K, Binding.KEY_L, Binding.KEY_SEMICOLON, Binding.KEY_APOSTROPHE, Binding.KEY_ENTER,
            Binding.KEY_SHIFT_L, Binding.KEY_Z, Binding.KEY_X, Binding.KEY_C, Binding.KEY_V, Binding.KEY_B, Binding.KEY_N, Binding.KEY_M, Binding.KEY_COMMA, Binding.KEY_PERIOD, Binding.KEY_SLASH, Binding.KEY_SHIFT_R,
            Binding.KEY_CTRL_L, Binding.KEY_ALT_L, Binding.KEY_SPACE, Binding.KEY_ALT_R, Binding.KEY_CTRL_R, Binding.KEY_INSERT, Binding.KEY_HOME, Binding.KEY_PG_UP, Binding.KEY_DEL, Binding.KEY_END, Binding.KEY_PG_DOWN, Binding.KEY_UP, Binding.KEY_LEFT, Binding.KEY_DOWN, Binding.KEY_RIGHT
        };
    }

    private Binding[] mouseBindings() {
        return new Binding[] {
            Binding.MOUSE_LEFT_BUTTON, Binding.MOUSE_RIGHT_BUTTON, Binding.MOUSE_MIDDLE_BUTTON,
            Binding.MOUSE_SCROLL_UP, Binding.MOUSE_SCROLL_DOWN,
            Binding.MOUSE_MOVE_UP, Binding.MOUSE_MOVE_RIGHT, Binding.MOUSE_MOVE_DOWN, Binding.MOUSE_MOVE_LEFT
        };
    }

    private Binding[] gamepadBindings() {
        return new Binding[] {
            Binding.GAMEPAD_BUTTON_L2, Binding.GAMEPAD_BUTTON_L1, Binding.GAMEPAD_BUTTON_R1, Binding.GAMEPAD_BUTTON_R2,
            Binding.GAMEPAD_DPAD_UP, Binding.GAMEPAD_DPAD_LEFT, Binding.GAMEPAD_DPAD_RIGHT, Binding.GAMEPAD_DPAD_DOWN,
            Binding.GAMEPAD_BUTTON_SELECT, Binding.GAMEPAD_BUTTON_START,
            Binding.GAMEPAD_BUTTON_A, Binding.GAMEPAD_BUTTON_B, Binding.GAMEPAD_BUTTON_X, Binding.GAMEPAD_BUTTON_Y,
            Binding.GAMEPAD_BUTTON_L3, Binding.GAMEPAD_BUTTON_R3,
            Binding.GAMEPAD_LEFT_THUMB_UP, Binding.GAMEPAD_LEFT_THUMB_RIGHT, Binding.GAMEPAD_LEFT_THUMB_DOWN, Binding.GAMEPAD_LEFT_THUMB_LEFT,
            Binding.GAMEPAD_RIGHT_THUMB_UP, Binding.GAMEPAD_RIGHT_THUMB_RIGHT, Binding.GAMEPAD_RIGHT_THUMB_DOWN, Binding.GAMEPAD_RIGHT_THUMB_LEFT
        };
    }

    private void loadRangeSpinner(final ControlElement element, Spinner spinner) {
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ControlElement.Range.names()));
        spinner.setSelection(element.getRange().ordinal(), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                inputControlsView.saveSelectedElementStateForUndo();
                element.setRange(ControlElement.Range.values()[position]);
                profile.save();
                inputControlsView.invalidate();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadColorPalette(LinearLayout parent, ControlElement element) {
        parent.removeAllViews();
        int[] colors = {
            Color.TRANSPARENT, Color.WHITE, 0xff00e5ff, 0xff00e676,
            0xffffea00, 0xffff9100, 0xffff1744, 0xff7c4dff
        };

        int size = (int)UnitUtils.dpToPx(34);
        int margin = (int)UnitUtils.dpToPx(3);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, margin, margin, margin);

        for (int color : colors) {
            View swatch = new View(this);
            swatch.setLayoutParams(params);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setCornerRadius(UnitUtils.dpToPx(6));
            drawable.setColor(color == Color.TRANSPARENT ? 0x22ffffff : color);
            drawable.setStroke((int)UnitUtils.dpToPx(2), color == Color.TRANSPARENT ? 0xffffffff : 0x66000000);
            swatch.setBackground(drawable);
            swatch.setOnClickListener(v -> {
                inputControlsView.saveSelectedElementStateForUndo();
                element.setBorderColor(color);
                element.setTextColor(color);
                element.setFillColor(color);
                profile.save();
                inputControlsView.invalidate();
            });
            parent.addView(swatch);
        }
    }

    private void loadIcons(final LinearLayout parent, int selectedId) {
        parent.removeAllViews();
        
        // Load only custom icons (removed built-in icons)
        int[] customIconIds = customIconManager.getCustomIconIds();
        
        // Use only custom icon IDs
        int[] allIconIds = customIconIds;
        
        Arrays.sort(allIconIds);

        int size = (int)UnitUtils.dpToPx(40);
        int margin = (int)UnitUtils.dpToPx(2);
        int padding = (int)UnitUtils.dpToPx(4);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(margin, 0, margin, 0);

        for (final int id : allIconIds) {
            ImageView imageView = new ImageView(this);
            imageView.setLayoutParams(params);
            imageView.setPadding(padding, padding, padding, padding);
            imageView.setBackgroundResource(R.drawable.icon_background);
            imageView.setTag(id);
            imageView.setSelected(id == selectedId);
            
            // Set up click listeners for both selection and deletion
            imageView.setOnClickListener((v) -> {
                for (int i = 0; i < parent.getChildCount(); i++) parent.getChildAt(i).setSelected(false);
                imageView.setSelected(true);
            });
            
            // Add long click listener for custom icon deletion
            if (CustomIconManager.isCustomIcon(id)) {
                imageView.setOnLongClickListener((v) -> {
                    showDeleteCustomIconDialog(id, parent);
                    return true;
                });
            }

            // Load the icon image - now only custom icons
            if (CustomIconManager.isCustomIcon(id)) {
                // Load custom icon
                android.graphics.Bitmap customIcon = customIconManager.loadIcon(id);
                if (customIcon != null) {
                    imageView.setImageBitmap(customIcon);
                }
            }

            parent.addView(imageView);
        }
    }
    
    private void importCustomIcon(Uri imageUri) {
        int iconId = customIconManager.importIcon(imageUri);
        if (iconId != -1) {
            AppUtils.showToast(this, R.string.icon_imported_successfully);
            // Refresh icon list if currently showing settings
            ControlElement selectedElement = inputControlsView.getSelectedElement();
            if (selectedElement != null) {
                // Find and refresh the icon list view if the settings popup is open
                refreshIconListIfVisible();
            }
        } else {
            AppUtils.showToast(this, R.string.failed_to_import_icon);
        }
    }
    
    private void refreshIconListIfVisible() {
        // This will be called to refresh the icon list after importing
        // The actual implementation would need access to the current popup window
        // For now, we'll just show a message that the user should reopen settings
        AppUtils.showToast(this, "Please reopen element settings to see the new icon");
    }
    
    private void showDeleteCustomIconDialog(int iconId, LinearLayout iconListParent) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.delete_custom_icon);
        builder.setMessage(R.string.confirm_delete_custom_icon);
        
        builder.setPositiveButton(R.string.ok, (dialog, which) -> {
            boolean deleted = customIconManager.deleteIcon(iconId);
            if (deleted) {
                // Remove the icon from the current view
                for (int i = 0; i < iconListParent.getChildCount(); i++) {
                    ImageView iconView = (ImageView) iconListParent.getChildAt(i);
                    if ((Integer) iconView.getTag() == iconId) {
                        iconListParent.removeViewAt(i);
                        break;
                    }
                }
                
                // Check if any element is using this deleted icon and reset it
                ControlElement selectedElement = inputControlsView.getSelectedElement();
                if (selectedElement != null && selectedElement.getIconId() == iconId) {
                    selectedElement.setIconId(0); // Reset to default
                    profile.save();
                    inputControlsView.invalidate();
                }
                
                // Check all elements in the profile and reset any using the deleted icon
                checkAndResetDeletedIcons(iconId);
                
                AppUtils.showToast(this, "Custom icon deleted successfully");
            } else {
                AppUtils.showToast(this, "Failed to delete custom icon");
            }
        });
        
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }
    
    private void checkAndResetDeletedIcons(int deletedIconId) {
        // Ensure the profile is loaded
        if (profile != null) {
            boolean profileModified = false;
            for (ControlElement element : profile.getElements()) {
                if (element.getIconId() == deletedIconId) {
                    element.setIconId(0); // Reset to default
                    profileModified = true;
                }
            }
            if (profileModified) {
                profile.save();
                inputControlsView.invalidate();
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_up);  // Custom slide animations for exiting
    }

}
