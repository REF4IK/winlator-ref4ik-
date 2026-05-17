package com.winlator.cmod;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.win32.PEParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen file picker for "Import Game" flow.
 * Returns the selected .exe absolute path as {@link #EXTRA_RESULT_PATH}.
 *
 * Styled to match the app's dark UI: gradient background, 3-column grid,
 * yellow folder icons, breadcrumb path.
 */
public class GameImportPickerActivity extends AppCompatActivity {
    public static final String EXTRA_INITIAL_DIR = "initial_dir";
    public static final String EXTRA_RESULT_PATH = "result_path";

    private static final int GRID_SPAN = 3;

    private File rootDirectory;
    private File currentDirectory;
    private TextView emptyView;
    private LinearLayout breadcrumb;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private File selectedFile;

    private ExecutorService iconExecutor;
    private Handler mainHandler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_import_picker);

        // Match system bars to the gradient background instead of theme green / white
        android.view.Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(0xFF0E1F26);      // top of gradient
            window.setNavigationBarColor(0xFF1A0B22);  // bottom of gradient
            android.view.View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            // Light icons on dark bars
            flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                flags &= ~android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }

        iconExecutor = Executors.newFixedThreadPool(2);
        mainHandler = new Handler(Looper.getMainLooper());

        rootDirectory = Environment.getExternalStorageDirectory();
        String initial = getIntent().getStringExtra(EXTRA_INITIAL_DIR);
        currentDirectory = (initial != null) ? new File(initial) : rootDirectory;
        if (!currentDirectory.exists() || !currentDirectory.isDirectory()) {
            currentDirectory = rootDirectory;
        }

        emptyView = findViewById(R.id.TVEmpty);
        breadcrumb = findViewById(R.id.LLBreadcrumb);
        recyclerView = findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, GRID_SPAN));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        findViewById(R.id.BTBack).setOnClickListener(v -> onBackPressed());

        loadFiles();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (iconExecutor != null) iconExecutor.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (currentDirectory != null
                && !currentDirectory.getAbsolutePath().equals(rootDirectory.getAbsolutePath())
                && currentDirectory.getParentFile() != null) {
            currentDirectory = currentDirectory.getParentFile();
            selectedFile = null;
            loadFiles();
        } else {
            super.onBackPressed();
        }
    }

    private void loadFiles() {
        updateBreadcrumb();

        List<File> items = new ArrayList<>();
        File[] children = currentDirectory.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory() || f.getName().toLowerCase().endsWith(".exe")) items.add(f);
            }
            Collections.sort(items, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
        }

        adapter.setItems(items);
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateBreadcrumb() {
        breadcrumb.removeAllViews();
        Context ctx = this;

        // Build path chain from root -> current
        List<File> chain = new ArrayList<>();
        File f = currentDirectory;
        while (f != null) {
            chain.add(0, f);
            if (f.getAbsolutePath().equals(rootDirectory.getAbsolutePath())) break;
            f = f.getParentFile();
        }

        int dp = (int) (getResources().getDisplayMetrics().density);
        for (int i = 0; i < chain.size(); i++) {
            File node = chain.get(i);
            boolean isLast = (i == chain.size() - 1);

            TextView tv = new TextView(ctx);
            tv.setText(i == 0 ? getString(R.string.game_import_internal_storage) : node.getName());
            tv.setTextSize(13f);
            tv.setSingleLine(true);
            tv.setPadding(4 * dp, 2 * dp, 4 * dp, 2 * dp);
            tv.setTextColor(isLast ? 0xFFFFFFFF : 0x99FFFFFF);
            if (isLast) tv.setTypeface(Typeface.DEFAULT_BOLD);
            if (!isLast) {
                final File target = node;
                tv.setOnClickListener(v -> {
                    currentDirectory = target;
                    selectedFile = null;
                    loadFiles();
                });
            }
            breadcrumb.addView(tv);

            if (!isLast) {
                TextView sep = new TextView(ctx);
                sep.setText(" > ");
                sep.setTextSize(13f);
                sep.setTextColor(0x66FFFFFF);
                breadcrumb.addView(sep);
            }
        }
    }

    private void onTileClicked(File file, int position) {
        if (file.isDirectory()) {
            currentDirectory = file;
            selectedFile = null;
            loadFiles();
            return;
        }

        if (file.equals(selectedFile)) {
            // Second tap on selected .exe = confirm
            Intent data = new Intent();
            data.putExtra(EXTRA_RESULT_PATH, file.getAbsolutePath());
            setResult(Activity.RESULT_OK, data);
            finish();
            return;
        }

        // First tap on .exe = highlight
        File previous = selectedFile;
        selectedFile = file;
        int prevIdx = previous != null ? adapter.indexOf(previous) : -1;
        if (prevIdx >= 0) adapter.notifyItemChanged(prevIdx);
        adapter.notifyItemChanged(position);
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        private final List<File> items = new ArrayList<>();

        void setItems(List<File> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        int indexOf(File f) {
            for (int i = 0; i < items.size(); i++) if (items.get(i).equals(f)) return i;
            return -1;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.game_import_tile, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            File file = items.get(position);
            h.currentFile = file;
            h.name.setText(file.getName());
            h.itemView.setActivated(file.equals(selectedFile));

            if (file.isDirectory()) {
                h.icon.setImageResource(R.drawable.ic_folder_yellow);
                File[] kids = file.listFiles();
                int count = (kids == null) ? 0 : kids.length;
                h.subtitle.setVisibility(View.VISIBLE);
                h.subtitle.setText(getResources().getQuantityString(
                        R.plurals.game_import_files_count, count, count));
            } else {
                h.subtitle.setVisibility(View.GONE);
                h.icon.setImageResource(R.drawable.ic_exe_placeholder);
                final File peFile = file;
                iconExecutor.execute(() -> {
                    Bitmap bmp = null;
                    try { bmp = PEParser.extractIcon(peFile); } catch (Throwable ignored) {}
                    final Bitmap result = bmp;
                    mainHandler.post(() -> {
                        if (h.currentFile != null && h.currentFile.equals(peFile) && result != null) {
                            h.icon.setImageBitmap(result);
                        }
                    });
                });
            }

            h.itemView.setOnClickListener(v -> onTileClicked(file, h.getBindingAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView subtitle;
            File currentFile;

            VH(View v) {
                super(v);
                icon = v.findViewById(R.id.IVIcon);
                name = v.findViewById(R.id.TVName);
                subtitle = v.findViewById(R.id.TVSubtitle);
            }
        }
    }
}
