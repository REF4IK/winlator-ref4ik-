package com.winlator.cmod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.tabs.TabLayout;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.inputcontrols.CustomIconManager;
import com.winlator.cmod.inputcontrols.IconPackManager;
import com.winlator.cmod.inputcontrols.IconPackManager.IconPack;
import com.winlator.cmod.widget.IconPackAdapter;

import java.util.ArrayList;
import java.util.List;

public class IconManagerActivity extends AppCompatActivity {
    public static final String EXTRA_TAB = "tab";

    private TabLayout tabLayout;
    private FrameLayout frameContent;
    private TextView tvEmpty;
    private ImageButton btAddIcon;

    private CustomIconManager customIconManager;
    private IconPackManager iconPackManager;
    private boolean isDarkMode;

    // Tabs: 0=My, 1=Packs
    private int currentTab = 0;
    private ListView listViewPacks;
    private GridView gridViewMy;
    private View llContentMy;
    private View llContentPacks;

    private final List<IconPack> userPacks = new ArrayList<>();

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private Callback<Uri> importCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.icon_manager_activity);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isDarkMode = prefs.getBoolean("dark_mode", false);

        customIconManager = new CustomIconManager(this);
        iconPackManager = new IconPackManager(this);

        Toolbar toolbar = findViewById(R.id.Toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.icon_manager);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        tabLayout = findViewById(R.id.TabLayout);
        frameContent = findViewById(R.id.FLContent);
        tvEmpty = findViewById(R.id.TVEmpty);
        btAddIcon = findViewById(R.id.BTAddIcon);

        setupTabs();
        setupContent();
        setupAddButton();
        setupFilePicker();

        // Restore tab from intent
        int requestedTab = getIntent().getIntExtra(EXTRA_TAB, 0);
        if (requestedTab >= 0 && requestedTab < tabLayout.getTabCount()) {
            tabLayout.getTabAt(requestedTab).select();
        }
    }

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText(R.string.my_icons));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.packs));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab = tab.getPosition();
                updateContent();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupContent() {
        // Inflate two content views, but only show the selected one
        LayoutInflater inflater = LayoutInflater.from(this);

        // My tab - grid of personal custom icons
        llContentMy = createMyTabView();
        frameContent.addView(llContentMy);

        // Packs tab - list of user icon packs
        llContentPacks = createPacksTabView();
        frameContent.addView(llContentPacks);
    }

    private View createMyTabView() {
        GridView grid = new GridView(this);
        grid.setId(View.generateViewId());
        grid.setNumColumns(4);
        grid.setBackgroundColor(Color.BLACK);
        grid.setPadding(16, 16, 16, 16);
        grid.setVerticalSpacing(12);
        grid.setHorizontalSpacing(12);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setGravity(android.view.Gravity.CENTER);
        gridViewMy = grid;
        return grid;
    }

    private View createPacksTabView() {
        ListView listView = new ListView(this);
        listView.setId(View.generateViewId());
        listView.setBackgroundColor(Color.BLACK);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setPadding(0, 8, 0, 8);
        listViewPacks = listView;
        return listView;
    }

    private void setupAddButton() {
        btAddIcon.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.inflate(R.menu.icon_manager_add_menu);
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.add_icon_image) {
                    openFilePicker("image/*");
                    importCallback = uri -> importIconFromUri(uri);
                    return true;
                } else if (id == R.id.add_icon_pack_zip) {
                    openFilePicker("application/zip");
                    importCallback = uri -> importPackFromZip(uri);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    private void setupFilePicker() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null && importCallback != null) {
                        importCallback.call(uri);
                        importCallback = null;
                    }
                }
            }
        );
    }

    private void openFilePicker(String type) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (type != null) {
            intent.setType(type);
        } else {
            intent.setType("*/*");
        }
        filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.add_custom_icon)));
    }

    private void importIconFromUri(Uri uri) {
        // Save as a single-icon pack
        IconPack pack = iconPackManager.importIconAsPack(uri);
        if (pack != null) {
            AppUtils.showToast(this, R.string.icon_imported_successfully);
            updateContent();
        } else {
            AppUtils.showToast(this, R.string.failed_to_import_icon);
        }
    }

    private void importPackFromZip(Uri uri) {
        IconPack pack = iconPackManager.importIconPackFromZip(uri);
        if (pack != null) {
            AppUtils.showToast(this, R.string.icon_imported_successfully);
            updateContent();
        } else {
            AppUtils.showToast(this, R.string.failed_to_import_icon);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateContent();
    }

    private void updateContent() {
        llContentMy.setVisibility(currentTab == 0 ? View.VISIBLE : View.GONE);
        llContentPacks.setVisibility(currentTab == 1 ? View.VISIBLE : View.GONE);

        // Show "+" on all tabs
        btAddIcon.setVisibility(View.VISIBLE);

        switch (currentTab) {
            case 0: loadMyTab(); break;
            case 1: loadPacksTab(); break;
        }
    }

    private void loadMyTab() {
        // Show user's imported icons in a grid
        int[] iconIds = customIconManager.getCustomIconIds();
        MyIconAdapter adapter = new MyIconAdapter(this, iconIds, customIconManager);
        gridViewMy.setAdapter(adapter);
        gridViewMy.setOnItemClickListener((parent, view, position, id) -> {
            int iconId = iconIds[position];
            if (CustomIconManager.isCustomIcon(iconId)) {
                showDeleteCustomIconDialog(iconId);
            }
        });
    }

    private void loadPacksTab() {
        // Show user icon packs
        userPacks.clear();
        userPacks.addAll(iconPackManager.getIconPacks());
        IconPackAdapter adapter = new IconPackAdapter(this, userPacks);
        listViewPacks.setAdapter(adapter);
        listViewPacks.setOnItemClickListener((parent, view, position, id) -> {
            IconPack pack = userPacks.get(position);
            openPackDetail(pack);
        });
        listViewPacks.setOnItemLongClickListener((parent, view, position, id) -> {
            IconPack pack = userPacks.get(position);
            showPackOptionsDialog(pack);
            return true;
        });
        tvEmpty.setVisibility(userPacks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openPackDetail(IconPack pack) {
        Intent intent = new Intent(this, IconPackDetailActivity.class);
        intent.putExtra(IconPackDetailActivity.EXTRA_PACK_NAME, pack.name);
        startActivity(intent);
    }

    private void showPackOptionsDialog(IconPack pack) {
        PopupMenu popup = new PopupMenu(this, findViewById(android.R.id.content));
        popup.inflate(R.menu.icon_pack_options_menu);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.rename_pack) {
                showRenameDialog(pack);
                return true;
            } else if (id == R.id.delete_pack) {
                new AlertDialog.Builder(this)
                    .setTitle(R.string.delete_custom_icon)
                    .setMessage(R.string.confirm_delete_custom_icon)
                    .setPositiveButton(R.string.ok, (d, w) -> {
                        iconPackManager.deleteIconPack(pack);
                        updateContent();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void showRenameDialog(IconPack pack) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.profile_name);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(pack.name);
        builder.setView(input);
        builder.setPositiveButton(R.string.ok, (d, w) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                iconPackManager.renameIconPack(pack, newName);
                updateContent();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showDeleteCustomIconDialog(int iconId) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_custom_icon)
            .setMessage(R.string.confirm_delete_custom_icon)
            .setPositiveButton(R.string.ok, (d, w) -> {
                customIconManager.deleteIcon(iconId);
                updateContent();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Adapter for the My icons grid
     */
    private static class MyIconAdapter extends BaseAdapter {
        private final android.content.Context context;
        private final int[] iconIds;
        private final CustomIconManager customIconManager;

        MyIconAdapter(android.content.Context context, int[] iconIds, CustomIconManager manager) {
            this.context = context;
            this.iconIds = iconIds;
            this.customIconManager = manager;
        }

        @Override
        public int getCount() { return iconIds.length; }

        @Override
        public Object getItem(int position) { return iconIds[position]; }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView instanceof ImageView) {
                imageView = (ImageView) convertView;
            } else {
                imageView = new ImageView(context);
                imageView.setLayoutParams(new GridView.LayoutParams(120, 120));
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setPadding(12, 12, 12, 12);
                imageView.setBackgroundResource(R.drawable.icon_background);
            }
            android.graphics.Bitmap icon = customIconManager.loadIcon(iconIds[position]);
            if (icon != null) {
                imageView.setImageBitmap(icon);
            } else {
                imageView.setImageResource(R.drawable.icon_background);
            }
            return imageView;
        }
    }
}
