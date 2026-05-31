package com.winlator.cmod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ShortcutSettingsDialog;

import java.io.File;

/**
 * Full-screen replacement for the old {@link ShortcutSettingsDialog} modal.
 *
 * Strategy: we still construct the existing dialog (so all 2k+ lines of binding /
 * preset / validation logic keep working untouched) but never call show() on it.
 * Instead we steal the dialog's already-inflated content (the user UI from
 * {@code R.layout.shortcut_settings_dialog}) and re-parent it into the activity.
 * Save/Cancel from the activity's bottom bar drive the dialog's existing
 * onConfirmCallback / onCancelCallback.
 *
 * Only the regular "Shortcuts" entry-point uses this activity. The Steam /
 * BigPicture flows continue to use the dialog directly.
 */
public class ShortcutSettingsActivity extends AppCompatActivity {
    public static final String EXTRA_CONTAINER_ID = "container_id";
    public static final String EXTRA_SHORTCUT_PATH = "shortcut_path";

    private ShortcutSettingsDialog hostedDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Respect the user's chosen app theme so the embedded dialog content
        // (which inflates with this Activity's theme) matches the rest of the app.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isDarkMode = prefs.getBoolean("dark_mode", false);
        setTheme(isDarkMode ? R.style.AppTheme_Dark : R.style.AppTheme);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shortcut_settings);

        Intent intent = getIntent();
        int containerId = intent.getIntExtra(EXTRA_CONTAINER_ID, -1);
        String shortcutPath = intent.getStringExtra(EXTRA_SHORTCUT_PATH);

        if (containerId < 0 || shortcutPath == null) {
            Toast.makeText(this, "Invalid shortcut", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ContainerManager manager = new ContainerManager(this);
        Container container = manager.getContainerById(containerId);
        File shortcutFile = new File(shortcutPath);
        if (container == null || !shortcutFile.exists()) {
            Toast.makeText(this, "Shortcut not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Shortcut shortcut = new Shortcut(container, shortcutFile);

        MaterialToolbar toolbar = findViewById(R.id.Toolbar);
        toolbar.setTitle(shortcut.name);
        toolbar.setNavigationOnClickListener(v -> handleCancel());

        // Construct the dialog. This runs createContentView() which binds every
        // field, sets up listeners, loads spinners, etc. -- exactly as the modal
        // would do. We never call show() so no window is attached.
        try {
            hostedDialog = new ShortcutSettingsDialog(this, shortcut);
        } catch (Throwable t) {
            android.util.Log.e("ShortcutSettings", "Failed to construct dialog", t);
            Toast.makeText(this, "Failed to load settings", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        embedDialogContent();

        findViewById(R.id.BTCancel).setOnClickListener(v -> handleCancel());
        findViewById(R.id.BTSave).setOnClickListener(v -> handleSave());
    }

    /**
     * Embed the dialog's WHOLE inflated wrapper into the Activity, then hide its
     * own title bar and bottom OK/Cancel bar (we provide our own toolbar + buttons).
     *
     * We keep the wrapper intact rather than ripping out just the user subtree,
     * because dialog code (e.g. {@code AppUtils.setupTabLayout}) holds a reference
     * to the wrapper and calls {@code findViewById} on it later -- detaching the
     * user content breaks that lookup and crashes on tab switch.
     */
    private void embedDialogContent() {
        View dialogRoot = hostedDialog.getContentView();
        if (dialogRoot == null) return;

        // Detach from the dialog window if already attached there.
        if (dialogRoot.getParent() instanceof ViewGroup) {
            ((ViewGroup) dialogRoot.getParent()).removeView(dialogRoot);
        }

        // Hide the dialog's own chrome -- the Activity supplies these.
        View titleBar = dialogRoot.findViewById(R.id.LLTitleBar);
        if (titleBar != null) titleBar.setVisibility(View.GONE);
        View bottomBar = dialogRoot.findViewById(R.id.LLBottomBar);
        if (bottomBar != null) bottomBar.setVisibility(View.GONE);

        // content_dialog.xml uses wrap_content on the root LinearLayout, its
        // inner ScrollView and the FrameLayout slot. Combined with the dialog
        // forcing a fixed modal width on LLContent, that makes the whole tree
        // only fill ~half the screen in landscape. Force every container in the
        // chain to MATCH_PARENT and call setLayoutParams() to trigger relayout.
        forceMatchParentWidth(dialogRoot.findViewById(R.id.LLContent));

        FrameLayout container = findViewById(R.id.FLContainer);
        container.addView(dialogRoot,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Walk from {@code leaf} up to (but not including) the FLContainer, forcing
     *  every ViewGroup in the chain to MATCH_PARENT width. */
    private void forceMatchParentWidth(View leaf) {
        View v = leaf;
        while (v != null) {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.width != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
                v.setLayoutParams(lp);
            }
            android.view.ViewParent p = v.getParent();
            if (!(p instanceof View)) break;
            v = (View) p;
        }
    }

    private void handleSave() {
        if (hostedDialog != null) {
            try {
                hostedDialog.triggerConfirmAction();
            } catch (Throwable t) {
                android.util.Log.e("ShortcutSettings", "Save failed", t);
                Toast.makeText(this, "Save failed: " + t.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        }
        setResult(RESULT_OK);
        finish();
    }

    private void handleCancel() {
        if (hostedDialog != null) {
            try { hostedDialog.triggerCancelAction(); } catch (Throwable ignored) {}
        }
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (hostedDialog != null) hostedDialog.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        handleCancel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hostedDialog = null;
    }
}
