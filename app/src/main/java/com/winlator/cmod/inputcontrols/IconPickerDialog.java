package com.winlator.cmod.inputcontrols;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.winlator.cmod.R;
import com.winlator.cmod.core.UnitUtils;
import com.winlator.cmod.inputcontrols.IconPackManager.IconPack;

import java.io.File;
import java.util.List;

/**
 * Dialog that lets the user pick an icon from icon packs and custom (individual) icons.
 * Packs are shown first, then individual custom icons.
 * Background is gray (0xff2a2a2a) instead of black.
 */
public class IconPickerDialog {
    public interface OnIconPickedListener {
        void onIconPicked(int iconId);
    }

    private final Activity activity;
    private final CustomIconManager customIconManager;
    private final IconPackManager iconPackManager;
    private final int selectedId;
    private final OnIconPickedListener listener;
    private AlertDialog dialog;
    private int pickedId = 0;

    public IconPickerDialog(Activity activity, CustomIconManager customIconManager,
                            IconPackManager iconPackManager,
                            int selectedId, OnIconPickedListener listener) {
        this.activity = activity;
        this.customIconManager = customIconManager;
        this.iconPackManager = iconPackManager;
        this.selectedId = selectedId;
        this.listener = listener;
    }

    public void show() {
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) UnitUtils.dpToPx(8);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xff3a3a3a); // gray

        // Header
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, (int) UnitUtils.dpToPx(8));

        TextView title = new TextView(activity);
        title.setText(R.string.select_icon);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        header.addView(title, titleParams);

        // "None" button to clear icon
        TextView noneBtn = new TextView(activity);
        noneBtn.setText(R.string.none);
        noneBtn.setTextColor(0xffff6666);
        noneBtn.setTextSize(13);
        noneBtn.setGravity(Gravity.CENTER);
        noneBtn.setPadding((int) UnitUtils.dpToPx(12), (int) UnitUtils.dpToPx(6),
                (int) UnitUtils.dpToPx(12), (int) UnitUtils.dpToPx(6));
        noneBtn.setOnClickListener(v -> {
            pickedId = 0;
            if (listener != null) listener.onIconPicked(pickedId);
            if (dialog != null) dialog.dismiss();
        });
        header.addView(noneBtn);

        root.addView(header);

        // Container with vertical scroll
        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        scrollView.setFillViewport(true);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) UnitUtils.dpToPx(400)));

        // Section 1: Icon packs
        List<IconPack> packs = iconPackManager.getIconPacks();
        if (!packs.isEmpty()) {
            addSectionTitle(content, R.string.packs);
            for (IconPack pack : packs) {
                addPackRow(content, pack);
            }
        }

        // Section 2: Individual custom icons
        int[] customIds = customIconManager.getCustomIconIds();
        if (customIds.length > 0) {
            addSectionTitle(content, R.string.my_icons);
            addIconsGrid(content, customIds);
        }

        // Empty state
        if (packs.isEmpty() && customIds.length == 0) {
            TextView empty = new TextView(activity);
            empty.setText(R.string.no_items_to_display);
            empty.setTextColor(0xff888888);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, (int) UnitUtils.dpToPx(32), 0, 0);
            content.addView(empty);
        }

        dialog = new AlertDialog.Builder(activity).setView(root).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void addSectionTitle(LinearLayout parent, int stringRes) {
        TextView section = new TextView(activity);
        section.setText(stringRes);
        section.setTextColor(0xffa8c9ff);
        section.setTextSize(14);
        section.setTypeface(null, android.graphics.Typeface.BOLD);
        section.setPadding(0, (int) UnitUtils.dpToPx(8), 0, (int) UnitUtils.dpToPx(4));
        parent.addView(section);
    }

    private void addPackRow(LinearLayout parent, IconPack pack) {
        LinearLayout packRow = new LinearLayout(activity);
        packRow.setOrientation(LinearLayout.HORIZONTAL);
        packRow.setGravity(Gravity.CENTER_VERTICAL);
        packRow.setBackgroundColor(0xff3a3a3a);
        int pad = (int) UnitUtils.dpToPx(8);
        packRow.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, (int) UnitUtils.dpToPx(4));
        parent.addView(packRow, rowParams);

        // Preview icon
        ImageView ivPreview = new ImageView(activity);
        int size = (int) UnitUtils.dpToPx(40);
        LinearLayout.LayoutParams ivParams = new LinearLayout.LayoutParams(size, size);
        ivPreview.setLayoutParams(ivParams);
        ivPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivPreview.setBackgroundColor(0xff2a2a2a);
        if (pack.preview != null) ivPreview.setImageBitmap(pack.preview);
        packRow.addView(ivPreview);

        // Name + count
        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding((int) UnitUtils.dpToPx(8), 0, 0, 0);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        packRow.addView(textCol, colParams);

        TextView tvName = new TextView(activity);
        tvName.setText(pack.name);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(14);
        tvName.setTypeface(null, android.graphics.Typeface.BOLD);
        textCol.addView(tvName);

        TextView tvCount = new TextView(activity);
        tvCount.setText(activity.getString(R.string.icon_count, pack.iconCount));
        tvCount.setTextColor(0xffaaaaaa);
        tvCount.setTextSize(12);
        textCol.addView(tvCount);

        // Arrow
        TextView arrow = new TextView(activity);
        arrow.setText("›");
        arrow.setTextColor(0xffaaaaaa);
        arrow.setTextSize(24);
        arrow.setGravity(Gravity.CENTER);
        packRow.addView(arrow);

        packRow.setOnClickListener(v -> showPackIcons(pack));
    }

    private void showPackIcons(IconPack pack) {
        List<File> iconFiles = pack.getIconFiles();
        if (iconFiles.isEmpty()) return;

        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) UnitUtils.dpToPx(8);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xff2a2a2a);

        // Header
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, (int) UnitUtils.dpToPx(8));

        TextView back = new TextView(activity);
        back.setText("←");
        back.setTextColor(Color.WHITE);
        back.setTextSize(22);
        back.setGravity(Gravity.CENTER);
        back.setPadding((int) UnitUtils.dpToPx(8), 0, (int) UnitUtils.dpToPx(8), 0);
        header.addView(back);

        TextView title = new TextView(activity);
        title.setText(pack.name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins((int) UnitUtils.dpToPx(8), 0, 0, 0);
        header.addView(title, titleParams);
        root.addView(header);

        // Icons grid - larger to fill the screen
        int iconSize = (int) (activity.getResources().getDisplayMetrics().density * 90);
        int margin = (int) UnitUtils.dpToPx(4);
        int columns = Math.max(3, (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.85f / iconSize));

        android.widget.ScrollView scrollView = new android.widget.ScrollView(activity);
        LinearLayout gridOuter = new LinearLayout(activity);
        gridOuter.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(gridOuter);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout row = null;
        int selectedGlobalId = selectedId;
        final AlertDialog[] packDialogHolder = new AlertDialog[1];
        for (int i = 0; i < iconFiles.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER);
                gridOuter.addView(row);
            }
            File f = iconFiles.get(i);
            final int globalId = IconPackManager.getPackIconId(pack.id, i);

            final LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            cellParams.setMargins(margin, margin, margin, margin);
            cell.setLayoutParams(cellParams);
            cell.setBackgroundColor(0xff2a2a2a);
            if (globalId == selectedGlobalId) {
                cell.setBackgroundColor(0xff11a9ef);
            }

            ImageView iv = new ImageView(activity);
            iv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding((int) UnitUtils.dpToPx(8), (int) UnitUtils.dpToPx(8),
                    (int) UnitUtils.dpToPx(8), (int) UnitUtils.dpToPx(8));
            Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bm != null) iv.setImageBitmap(bm);
            cell.addView(iv);

            final int pickedIndex = i;
            cell.setOnClickListener(v -> {
                // Use pack icon ID directly without copying to custom icons
                pickedId = IconPackManager.getPackIconId(pack.id, pickedIndex);
                if (listener != null) listener.onIconPicked(pickedId);
                packDialogHolder[0].dismiss();
                if (dialog != null) dialog.dismiss();
            });

            row.addView(cell);
        }

        AlertDialog packDialog = new AlertDialog.Builder(activity).setView(root).create();
        packDialogHolder[0] = packDialog;
        back.setOnClickListener(v -> packDialog.dismiss());
        packDialog.show();
        if (packDialog.getWindow() != null) {
            packDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            // Use full screen height for the pack detail dialog
            android.view.Window window = packDialog.getWindow();
            window.setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.95f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.85f));
        }
    }

    private void addIconsGrid(LinearLayout parent, int[] iconIds) {
        // Use a wrapping grid layout so icons wrap to next line
        android.widget.GridLayout grid = new android.widget.GridLayout(activity);
        grid.setColumnCount(4);
        grid.setBackgroundColor(0xff3a3a3a);
        int pad = (int) UnitUtils.dpToPx(8);
        grid.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.setMargins(0, 0, 0, (int) UnitUtils.dpToPx(4));
        parent.addView(grid, gridParams);

        int size = (int) (activity.getResources().getDisplayMetrics().density * 70);
        int margin = (int) UnitUtils.dpToPx(4);
        for (int id : iconIds) {
            final LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setBackgroundColor(id == selectedId ? 0xff11a9ef : 0xff2a2a2a);

            ImageView iv = new ImageView(activity);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding((int) UnitUtils.dpToPx(4), (int) UnitUtils.dpToPx(4),
                    (int) UnitUtils.dpToPx(4), (int) UnitUtils.dpToPx(4));

            Bitmap bm = customIconManager.loadIcon(id);
            if (bm != null) iv.setImageBitmap(bm);

            cell.addView(iv);

            android.widget.GridLayout.LayoutParams cellParams = new android.widget.GridLayout.LayoutParams();
            cellParams.width = size;
            cellParams.height = size;
            cellParams.setMargins(margin, margin, margin, margin);
            cell.setLayoutParams(cellParams);

            cell.setOnClickListener(v -> {
                pickedId = id;
                if (listener != null) listener.onIconPicked(pickedId);
                if (dialog != null) dialog.dismiss();
            });
            grid.addView(cell);
        }
    }
}
