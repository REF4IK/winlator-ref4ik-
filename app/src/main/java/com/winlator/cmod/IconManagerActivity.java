package com.winlator.cmod;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.inputcontrols.CustomIconManager;
import com.winlator.cmod.inputcontrols.IconPackManager;
import com.winlator.cmod.inputcontrols.IconPackManager.IconPack;
import com.winlator.cmod.widget.IconPackAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IconManagerActivity extends AppCompatActivity {
    public static final String EXTRA_TAB = "tab";

    private TabLayout tabLayout;
    private RecyclerView recyclerMy;
    private FrameLayout frameContent;
    private TextView tvEmpty;
    private ImageButton btAddIcon;

    private CustomIconManager customIconManager;
    private IconPackManager iconPackManager;

    private int currentTab = 0;
    private ListView listViewPacks;
    private View llContentMy;
    private View llContentPacks;

    private final List<IconPack> userPacks = new ArrayList<>();

    // Multi-select
    private boolean multiSelectMode = false;
    private Set<Integer> selectedIconIds = new HashSet<>();
    private View llBottomActions;
    private MaterialButton btSelectAll;

    // My Icons adapter
    private MyIconRecyclerAdapter myAdapter;
    private final List<Integer> myIconIdList = new ArrayList<>();

    private ActivityResultLauncher<Intent> filePickerLauncher;
    private Callback<Uri> importCallback;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.icon_manager_activity);

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
        llBottomActions = findViewById(R.id.LLBottomActions);
        btSelectAll = findViewById(R.id.BTSelectAll);

        setupTabs();
        setupContent();
        setupAddButton();
        setupBottomActions();
        setupFilePicker();

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
                exitMultiSelectMode();
                updateContent();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupContent() {
        llContentMy = createMyTabView();
        frameContent.addView(llContentMy);
        llContentPacks = createPacksTabView();
        frameContent.addView(llContentPacks);
    }

    private View createMyTabView() {
        LinearLayout root = new LinearLayout(this);
        root.setId(View.generateViewId());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff2a2a2a);

        recyclerMy = new RecyclerView(this);
        recyclerMy.setId(View.generateViewId());
        recyclerMy.setBackgroundColor(0xff2a2a2a);
        recyclerMy.setPadding(12, 12, 12, 12);
        recyclerMy.setClipToPadding(false);
        recyclerMy.setLayoutManager(new GridLayoutManager(this, 4));
        recyclerMy.setHasFixedSize(true);

        // Drag & drop via ItemTouchHelper
        ItemTouchHelper.SimpleCallback dragCb = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder from,
                                  @NonNull RecyclerView.ViewHolder to) {
                int fromPos = from.getAdapterPosition();
                int toPos = to.getAdapterPosition();
                if (fromPos >= 0 && toPos >= 0 && fromPos < myIconIdList.size() && toPos < myIconIdList.size()) {
                    Integer moved = myIconIdList.remove(fromPos);
                    myIconIdList.add(toPos, moved);
                    myAdapter.notifyItemMoved(fromPos, toPos);
                    customIconManager.saveIconOrder(myIconIdList);
                    return true;
                }
                return false;
            }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
        };
        ItemTouchHelper ith = new ItemTouchHelper(dragCb);
        ith.attachToRecyclerView(recyclerMy);

        // Enable drag handle on long-press
        recyclerMy.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            private final android.os.Handler h = new android.os.Handler();
            private boolean dragging = false;
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN && !multiSelectMode) {
                    View child = rv.findChildViewUnder(e.getX(), e.getY());
                    if (child != null) {
                        int pos = rv.getChildAdapterPosition(child);
                        if (pos >= 0) {
                            h.postDelayed(() -> {
                                if (!multiSelectMode) {
                                    dragging = true;
                                    ith.startDrag(rv.getChildViewHolder(child));
                                }
                            }, 400);
                        }
                    }
                }
                if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    h.removeCallbacksAndMessages(null);
                    dragging = false;
                }
                return false;
            }
            @Override public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {}
            @Override public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
        });

        root.addView(recyclerMy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private View createPacksTabView() {
        listViewPacks = new ListView(this);
        listViewPacks.setId(View.generateViewId());
        listViewPacks.setBackgroundColor(0xff2a2a2a);
        listViewPacks.setDivider(null);
        listViewPacks.setDividerHeight(0);
        listViewPacks.setPadding(0, 8, 0, 8);
        return listViewPacks;
    }

    private void setupBottomActions() {
        btSelectAll.setOnClickListener(v -> {
            if (selectedIconIds.size() == myIconIdList.size()) {
                selectedIconIds.clear();
                btSelectAll.setText(R.string.select_all);
            } else {
                selectedIconIds.addAll(myIconIdList);
                btSelectAll.setText(R.string.deselect_all);
            }
            myAdapter.notifyDataSetChanged();
        });

        findViewById(R.id.BTExitSelectMode).setOnClickListener(v -> exitMultiSelectMode());
    }

    private void setupAddButton() {
        btAddIcon.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.inflate(R.menu.icon_manager_add_menu);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.add_icon_image) {
                    openFilePicker("image/*");
                    importCallback = this::importIconFromUri;
                    return true;
                } else if (item.getItemId() == R.id.add_icon_pack_zip) {
                    openFilePicker("application/zip");
                    importCallback = this::importPackFromZip;
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
        intent.setType(type != null ? type : "*/*");
        filePickerLauncher.launch(Intent.createChooser(intent, getString(R.string.add_custom_icon)));
    }

    private void importIconFromUri(Uri uri) {
        int iconId = customIconManager.importIcon(uri);
        if (iconId >= 0) {
            AppUtils.showToast(this, R.string.icon_imported_successfully);
            tabLayout.getTabAt(0).select();
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
        if (currentTab != 0) exitMultiSelectMode();
        btAddIcon.setVisibility(View.VISIBLE);
        switch (currentTab) {
            case 0: loadMyTab(); break;
            case 1: loadPacksTab(); break;
        }
    }

    // ─── My Icons ───

    private void loadMyTab() {
        int[] ids = customIconManager.getCustomIconIds();
        int[] ordered = customIconManager.getIconIdsInOrder(ids);
        myIconIdList.clear();
        for (int id : ordered) myIconIdList.add(id);
        myAdapter = new MyIconRecyclerAdapter();
        recyclerMy.setAdapter(myAdapter);
        tvEmpty.setVisibility(myIconIdList.isEmpty() && !multiSelectMode ? View.VISIBLE : View.GONE);
    }

    private void showPreviewDialog(int iconId) {
        Bitmap bm = customIconManager.loadIcon(iconId);
        if (bm == null) return;
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bm);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setPadding(32, 32, 32, 32);
        iv.setBackgroundColor(0xff2a2a2a);
        new AlertDialog.Builder(this).setView(iv).setPositiveButton(R.string.close, null).show();
    }

    private void showIconEditDialog(final int iconId) {
        Bitmap bm = customIconManager.loadIcon(iconId);
        if (bm == null) return;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 16);
        root.setBackgroundColor(0xff2a2a2a);

        final ImageView iv = new ImageView(this);
        iv.setImageBitmap(bm);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = (int)(getResources().getDisplayMetrics().density * 140);
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        iv.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        iv.setPadding(8, 8, 8, 8);
        iv.setBackgroundColor(0xff1a1a1a);
        root.addView(iv);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 16, 0, 0);

        MaterialButton btRotate = new MaterialButton(this);
        btRotate.setText(R.string.rotate);
        btRotate.setOnClickListener(v -> {
            customIconManager.rotateIcon(iconId);
            Bitmap newBm = customIconManager.loadIcon(iconId);
            if (newBm != null) iv.setImageBitmap(newBm);
            if (myAdapter != null) myAdapter.notifyDataSetChanged();
            AppUtils.showToast(this, R.string.saved);
        });
        btnRow.addView(btRotate);

        MaterialButton btEdit = new MaterialButton(this);
        btEdit.setText(R.string.edit_external);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.setMargins(16, 0, 0, 0);
        btEdit.setLayoutParams(ep);
        btEdit.setOnClickListener(v -> {
            File iconFile = customIconManager.getIconFile(iconId);
            if (iconFile != null) {
                Intent editIntent = new Intent(Intent.ACTION_EDIT);
                editIntent.setDataAndType(Uri.fromFile(iconFile), "image/png");
                editIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(editIntent, getString(R.string.edit_external)));
                } catch (Exception e) {
                    AppUtils.showToast(this, R.string.no_app_found);
                }
            }
        });
        btnRow.addView(btEdit);

        root.addView(btnRow);
        new AlertDialog.Builder(this).setTitle(R.string.edit_icon)
            .setView(root).setPositiveButton(R.string.close, null).show();
    }

    // ─── Multi-select ───

    private void enterMultiSelectMode() {
        multiSelectMode = true;
        selectedIconIds.clear();
        llBottomActions.setVisibility(View.VISIBLE);
        btSelectAll.setText(R.string.select_all);
        if (myAdapter != null) myAdapter.notifyDataSetChanged();
    }

    private void exitMultiSelectMode() {
        multiSelectMode = false;
        selectedIconIds.clear();
        llBottomActions.setVisibility(View.GONE);
        if (myAdapter != null) myAdapter.notifyDataSetChanged();
    }

    private void deleteSelectedIcons() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_custom_icon)
            .setMessage(R.string.confirm_delete_custom_icon)
            .setPositiveButton(R.string.ok, (d, w) -> {
                for (int iconId : selectedIconIds) {
                    customIconManager.deleteIcon(iconId);
                }
                exitMultiSelectMode();
                updateContent();
            })
            .setNegativeButton(R.string.cancel, null).show();
    }

    // ─── Packs ───

    private void loadPacksTab() {
        userPacks.clear();
        userPacks.addAll(iconPackManager.getIconPacks());
        listViewPacks.setAdapter(new IconPackAdapter(this, userPacks));
        listViewPacks.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, IconPackDetailActivity.class);
            intent.putExtra(IconPackDetailActivity.EXTRA_PACK_NAME, userPacks.get(position).name);
            startActivity(intent);
        });
        listViewPacks.setOnItemLongClickListener((parent, view, position, id) -> {
            showPackOptionsPopup(view, userPacks.get(position));
            return true;
        });
        tvEmpty.setVisibility(userPacks.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPackOptionsPopup(View anchor, IconPack pack) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.icon_pack_options_menu);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.rename_pack:
                    showRenameDialog(pack);
                    return true;
                case R.id.delete_pack:
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.delete_custom_icon)
                        .setMessage(R.string.confirm_delete_custom_icon)
                        .setPositiveButton(R.string.ok, (d, dw) -> {
                            iconPackManager.deleteIconPack(pack);
                            updateContent();
                        }).setNegativeButton(R.string.cancel, null).show();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void showRenameDialog(IconPack pack) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setText(pack.name);
        new AlertDialog.Builder(this).setTitle(R.string.profile_name)
            .setView(input)
            .setPositiveButton(R.string.ok, (d, w) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty()) {
                    iconPackManager.renameIconPack(pack, newName);
                    updateContent();
                }
            }).setNegativeButton(R.string.cancel, null).show();
    }

    // ─── RecyclerView Adapter ───

    private class MyIconRecyclerAdapter extends RecyclerView.Adapter<MyIconRecyclerAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final ImageView iv;
            final View checkOverlay;
            VH(@NonNull View itemView) {
                super(itemView);
                iv = itemView.findViewById(android.R.id.icon);
                checkOverlay = itemView.findViewById(android.R.id.background);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(IconManagerActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            int size = (int)(getResources().getDisplayMetrics().density * 86);
            RecyclerView.LayoutParams rp = new RecyclerView.LayoutParams(size, size);
            int m = (int)(getResources().getDisplayMetrics().density * 4);
            rp.setMargins(m, m, m, m);
            root.setLayoutParams(rp);
            root.setGravity(Gravity.CENTER);

            View check = new View(IconManagerActivity.this);
            check.setId(android.R.id.background);
            check.setBackgroundColor(0x8800aaff);
            check.setVisibility(View.GONE);
            root.addView(check, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            ImageView iv = new ImageView(IconManagerActivity.this);
            iv.setId(android.R.id.icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setPadding(6, 6, 6, 6);
            iv.setBackgroundResource(R.drawable.icon_background);
            root.addView(iv, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            return new VH(root);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            int iconId = myIconIdList.get(pos);
            Bitmap bm = customIconManager.loadIcon(iconId);
            holder.iv.setImageBitmap(bm);
            boolean sel = selectedIconIds.contains(iconId);
            holder.checkOverlay.setVisibility(sel ? View.VISIBLE : View.GONE);

            holder.itemView.setOnClickListener(v -> {
                if (multiSelectMode) toggleSel(iconId);
                else showPreviewDialog(iconId);
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (multiSelectMode) toggleSel(iconId);
                else showMyIconPopup(v, iconId);
                return true;
            });
        }

        private void toggleSel(int iconId) {
            if (selectedIconIds.contains(iconId)) selectedIconIds.remove(iconId);
            else selectedIconIds.add(iconId);
            btSelectAll.setText(selectedIconIds.size() == myIconIdList.size() ? R.string.deselect_all : R.string.select_all);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() { return myIconIdList.size(); }
    }

    private void showMyIconPopup(View anchor, final int iconId) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.preview);
        popup.getMenu().add(0, 2, 0, R.string.edit_icon);
        popup.getMenu().add(0, 3, 0, R.string.delete_custom_icon);
        popup.getMenu().add(0, 4, 0, R.string.delete_all_custom_icons);
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showPreviewDialog(iconId); return true;
                case 2: showIconEditDialog(iconId); return true;
                case 3: showDeleteCustomIconDialog(iconId); return true;
                case 4:
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.delete_all_custom_icons)
                        .setMessage(R.string.confirm_delete_all_custom_icons)
                        .setPositiveButton(R.string.ok, (d, w) -> {
                            customIconManager.deleteAllIcons();
                            exitMultiSelectMode();
                            updateContent();
                        }).setNegativeButton(R.string.cancel, null).show();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void showDeleteCustomIconDialog(int iconId) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_custom_icon)
            .setMessage(R.string.confirm_delete_custom_icon)
            .setPositiveButton(R.string.ok, (d, w) -> {
                customIconManager.deleteIcon(iconId);
                updateContent();
            }).setNegativeButton(R.string.cancel, null).show();
    }
}
