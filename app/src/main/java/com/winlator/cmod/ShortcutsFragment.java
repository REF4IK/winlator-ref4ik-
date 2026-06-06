package com.winlator.cmod;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.container.Shortcut;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.ShortcutSettingsDialog;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.Callback;
import com.winlator.cmod.core.DohOkHttp;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.win32.PEParser;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.bumptech.glide.Glide;
import com.winlator.cmod.steamgrid.SteamGridDBApi;
import com.winlator.cmod.steamgrid.SteamGridGridsResponse;
import com.winlator.cmod.steamgrid.SteamGridGridsResponseDeserializer;
import com.winlator.cmod.steamgrid.SteamGridSearchResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ShortcutsFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private String searchText;
    private Container shortcutContainer;
    private boolean isGridView = false;
    private android.view.MenuItem toggleViewMenuItem;

    private static final int REQUEST_CODE_SELECT_EXE_FILE = 7777;
    private static final int REQUEST_CODE_IMPORT_BOX64_PRESET = 7878;
    private static final int REQUEST_CODE_IMPORT_FEXCORE_PRESET = 7879;
    private static final int REQUEST_CODE_IMPORT_GAME = 7880;
    private static final int REQUEST_CODE_CHANGE_EXE_PATH = 7883;
    private static final int REQUEST_CODE_SHORTCUT_SETTINGS = 7881;

    private static final String STEAMGRID_BASE_URL = "https://www.steamgriddb.com/api/v2/";
    private static final String STEAMGRID_API_KEY = "4765cce5e92f8406ab0f5346c3b5e3ba";

    private static final java.util.concurrent.ExecutorService COVER_ART_EXECUTOR =
            java.util.concurrent.Executors.newFixedThreadPool(5);

    private static final okhttp3.OkHttpClient COVER_ART_HTTP = DohOkHttp.get().newBuilder()
            // Keep downloads responsive; don't let a couple of bad requests block the whole pool.
            .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private static final okhttp3.OkHttpClient STEAMGRID_HTTP = DohOkHttp.get().newBuilder()
            .callTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private static final long STEAMGRID_NEGATIVE_TTL_MS = 10 * 60 * 1000L;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> STEAMGRID_LAST_FAIL =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, Integer> STEAM_APPID_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final String[] STEAM_ASSET_HOSTS = new String[] {
            // Often more reachable in RU networks (as seen in GameNative)
            "cdn.cloudflare.steamstatic.com",
            // Common Steam CDN host
            "cdn.akamai.steamstatic.com",
            // Some regions still work via this
            "steamcdn-a.akamaihd.net"
    };

    private Callback<Uri> importBox64PresetCallback;
    private Callback<Uri> importFexcorePresetCallback;

    public void requestImportBox64Preset(Callback<Uri> callback) {
        importBox64PresetCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_BOX64_PRESET);
    }

    public void requestImportFexcorePreset(Callback<Uri> callback) {
        importFexcorePresetCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_IMPORT_FEXCORE_PRESET);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        
        // Load saved view mode preference
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        isGridView = prefs.getBoolean("shortcuts_grid_view", true);
        
        // Setup RecyclerView after loading preferences
        setupRecyclerView();
        
        loadShortcutsList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.shortcuts);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull android.view.Menu menu, @NonNull android.view.MenuInflater inflater) {
        inflater.inflate(R.menu.shortcuts_menu, menu);
        toggleViewMenuItem = menu.findItem(R.id.toggle_view_mode);
        updateViewModeIcon();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        int itemId = item.getItemId();
        
        if (itemId == R.id.search_shortcut) {
            final EditText taskEditText = new EditText(getContext());
            taskEditText.setText(searchText);
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setTitle("Search shortcuts by Name")
                    .setMessage("Type Name of shortcut in field")
                    .setView(taskEditText)
                    .setPositiveButton("SEARCH", (dialog1, which) -> {
                        searchText = String.valueOf(taskEditText.getText());
                        loadShortcutsList(6);
                    })
                    .setNegativeButton("Cancel", null)
                    .create();
            dialog.show();
            taskEditText.setSelection(0);
            return true;
        }
        else if (itemId == R.id.toggle_view_mode) {
            isGridView = !isGridView;
            
            // Save view mode preference
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.edit().putBoolean("shortcuts_grid_view", isGridView).apply();
            
            updateViewModeIcon();
            setupRecyclerView();
            loadShortcutsList();
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout = (FrameLayout)inflater.inflate(R.layout.shortcuts_fragment, container, false);
        recyclerView = frameLayout.findViewById(R.id.RecyclerView);
        emptyTextView = frameLayout.findViewById(R.id.TVEmptyText);
        
        // Setup FAB
        FloatingActionButton fabAddShortcut = frameLayout.findViewById(R.id.FABAddShortcut);
        fabAddShortcut.setOnClickListener(v -> {
            ContainerManager containerManager = new ContainerManager(getContext());
            ArrayList<Container> containers = containerManager.getContainers();

            showContainerSelectionDialog(containers, selectedContainer -> {
                shortcutContainer = selectedContainer;

                // Launch the full-screen "Import game" picker styled like the rest of the app
                File internalStorage = Environment.getExternalStorageDirectory(); // /storage/emulated/0
                Intent intent = new Intent(getContext(), GameImportPickerActivity.class);
                intent.putExtra(GameImportPickerActivity.EXTRA_INITIAL_DIR, internalStorage.getAbsolutePath());
                startActivityForResult(intent, REQUEST_CODE_IMPORT_GAME);
            });
        });
        
        return frameLayout;
    }
    
    private void setupRecyclerView() {
        recyclerView.clearOnScrollListeners();
        
        if (isGridView) {
            int spanCount = isLandscapeOrientation() ? 4 : 2;
            recyclerView.setLayoutManager(new GridLayoutManager(recyclerView.getContext(), spanCount));
            // Remove divider for grid view
            while (recyclerView.getItemDecorationCount() > 0) {
                recyclerView.removeItemDecorationAt(0);
            }
        } else {
            recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
            // Add divider for list view
            while (recyclerView.getItemDecorationCount() > 0) {
                recyclerView.removeItemDecorationAt(0);
            }
            recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
        }
    }
    
    private void updateViewModeIcon() {
        if (toggleViewMenuItem != null) {
            if (isGridView) {
                toggleViewMenuItem.setIcon(R.drawable.ic_view_grid);
            } else {
                toggleViewMenuItem.setIcon(R.drawable.ic_view_list);
            }
        }
    }

    public void loadShortcutsList() {
        loadShortcutsList(0);
    }

    public void loadShortcutsList(int sortType) {
        ArrayList<Shortcut> shortcuts = manager.loadShortcuts();

        // Apply sorting
        switch (sortType) {
            case 6 -> { // Search by name
                if (searchText != null && !searchText.isEmpty()) {
                    shortcuts.sort((s1, s2) -> {
                        String name1 = s1.name.toLowerCase();
                        String name2 = s2.name.toLowerCase();
                        String search = searchText.toLowerCase();

                        int idx1 = name1.indexOf(search);
                        int idx2 = name2.indexOf(search);

                        if (idx1 == -1 && idx2 != -1) return 1;
                        if (idx1 != -1 && idx2 == -1) return -1;
                        if (idx1 == -1 && idx2 == -1) return name1.compareTo(name2);

                        if (idx1 != idx2) return Integer.compare(idx1, idx2);

                        return name1.compareTo(name2);
                    });
                }
            }
        }

        // Validate and remove corrupted shortcuts and temporary component shortcuts
        shortcuts.removeIf(shortcut -> shortcut == null || 
                                      shortcut.file == null || 
                                      shortcut.file.getName().isEmpty() ||
                                      shortcut.isTemporary() ||
                                      isSteamManagedShortcut(shortcut)); // РЎРєСЂС‹РІР°РµРј РІСЂРµРјРµРЅРЅС‹Рµ СЏСЂР»С‹РєРё РєРѕРјРїРѕРЅРµРЅС‚РѕРІ

        recyclerView.setAdapter(new ShortcutsAdapter(shortcuts));
        if (shortcuts.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
        else emptyTextView.setVisibility(View.GONE);
    }

    private boolean isSteamManagedShortcut(@Nullable Shortcut shortcut) {
        if (shortcut == null) return false;

        String source = shortcut.getExtra("game_source");
        if (source == null || source.isEmpty()) {
            source = shortcut.getExtra("gameSource");
        }

        return source != null && source.equalsIgnoreCase("STEAM");
    }
    private class ShortcutsAdapter extends RecyclerView.Adapter<ShortcutsAdapter.ViewHolder> {
        private final List<Shortcut> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final View menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView subtitle;
            private final TextView wineVersion;
            private final View cardView;
            private String boundKey;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.subtitle = view.findViewById(R.id.TVSubtitle);
                this.wineVersion = view.findViewById(R.id.TVWineVersion);
                this.menuButton = view.findViewById(R.id.BTMenu);
                this.cardView = view; // The entire MaterialCardView is clickable
            }
        }

        public ShortcutsAdapter(List<Shortcut> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = isGridView ? R.layout.shortcut_grid_item : R.layout.shortcut_list_item;
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false));
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            holder.menuButton.setOnClickListener(null);
            holder.cardView.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Shortcut item = data.get(position);
            holder.boundKey = item.name;

            if (isGridView) {
                bindGridCoverArt(holder, item);
                // Adjust cover art height for landscape (4 cols) vs portrait (2 cols)
                ViewGroup.LayoutParams lp = holder.imageView.getLayoutParams();
                if (isLandscapeOrientation()) {
                    lp.height = (int)(getResources().getDisplayMetrics().widthPixels / 4 * 1.3f);
                } else {
                    lp.height = (int)(228 * getResources().getDisplayMetrics().density);
                }
                holder.imageView.setLayoutParams(lp);
            } else {
                Bitmap displayIcon = item.getDisplayIcon();
                if (displayIcon != null) holder.imageView.setImageBitmap(displayIcon);
                else holder.imageView.setImageResource(R.drawable.icon_shortcut);
            }

            holder.title.setText(item.name);
            holder.subtitle.setText(item.container.getName());
            if (holder.wineVersion != null) {
                String wineVersionText = item.container != null ? item.container.getWineVersion() : null;
                if (wineVersionText != null && !wineVersionText.isEmpty()) {
                    holder.wineVersion.setText(wineVersionText);
                    holder.wineVersion.setVisibility(View.VISIBLE);
                } else {
                    holder.wineVersion.setText("");
                    holder.wineVersion.setVisibility(View.GONE);
                }
            }
            holder.menuButton.setOnClickListener((v) -> ShortcutsFragment.this.showListItemMenu(v, item));
            holder.cardView.setOnClickListener((v) -> ShortcutsFragment.this.runFromShortcut(item));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    private boolean isLandscapeOrientation() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isGridView) {
            setupRecyclerView();
            loadShortcutsList();
        }
    }

    private void bindGridCoverArt(@NonNull ShortcutsAdapter.ViewHolder holder, @NonNull Shortcut shortcut) {
        final boolean landscape = false;

        if (shortcut.getCustomCoverArtPath() != null && !shortcut.getCustomCoverArtPath().isEmpty()) {
            File customFile = new File(shortcut.getCustomCoverArtPath());
            if (customFile.exists()) {
                String ext = urlToExtension(customFile.getName());
                if (isAnimatedFile(customFile, ext)) {
                    loadAnimatedCover(holder.imageView, customFile);
                    return;
                }
                Bitmap coverArt = BitmapFactory.decodeFile(customFile.getAbsolutePath());
                if (coverArt != null) {
                    holder.imageView.setImageBitmap(coverArt);
                    return;
                }
            }
        }

        File cachedFile = getCachedCoverFile(shortcut.name, landscape);
        if (cachedFile != null) {
            if (isCachedCoverAnimated(shortcut.name, landscape)) {
                loadAnimatedCover(holder.imageView, cachedFile);
                return;
            }
            Bitmap coverArt = BitmapFactory.decodeFile(cachedFile.getAbsolutePath());
            if (coverArt != null) {
                holder.imageView.setImageBitmap(coverArt);
                fetchSteamGridCoverArt(holder, shortcut, landscape);
                return;
            }
        }

        if (shortcut.icon != null) {
            holder.imageView.setImageBitmap(shortcut.icon);
        } else {
            holder.imageView.setImageResource(R.drawable.cover_art_placeholder);
        }
        fetchSteamGridCoverArt(holder, shortcut, landscape);
    }

    private static boolean isAnimatedFile(File file, String ext) {
        if (file == null || !file.exists()) return false;
        if (".gif".equalsIgnoreCase(ext)) return true;
        if (".apng".equalsIgnoreCase(ext)) return true;
        if (".webp".equalsIgnoreCase(ext)) return true;
        return false;
    }

    private void fetchSteamCoverArt(@NonNull ShortcutsAdapter.ViewHolder holder, @NonNull Shortcut shortcut, boolean landscape) {
        final String expectedKey = shortcut.name;
        COVER_ART_EXECUTOR.execute(() -> {
            try {
                Integer appId = resolveSteamAppIdByStoreSearch(shortcut.name);
                if (appId == null || appId <= 0) {
                    Log.w("CoverArt", "Steam CDN: appid not found name=" + shortcut.name + ", falling back to SteamGridDB");
                    fetchSteamGridCoverArt(holder, shortcut, landscape);
                    return;
                }

                for (String url : buildSteamCoverUrls(appId, landscape)) {
                    if (!isAdded()) return;
                    if (shortcut.getCustomCoverArtPath() != null && !shortcut.getCustomCoverArtPath().isEmpty()) return;
                    if (loadCachedCoverArt(shortcut.name, landscape) != null) return;

                    Bitmap bmp = downloadBitmap(url);
                    if (bmp != null) {
                        cacheCoverArt(bmp, shortcut.name, landscape);

                        if (!isAdded()) return;
                        final Bitmap finalBmp = bmp;
                        requireActivity().runOnUiThread(() -> {
                            if (!expectedKey.equals(holder.boundKey)) return;
                            holder.imageView.setImageBitmap(finalBmp);
                        });
                        Log.i("CoverArt", "Steam CDN cover art downloaded name=" + shortcut.name + " appId=" + appId + " landscape=" + landscape);
                        return;
                    }
                }

                Log.w("CoverArt", "Steam CDN: all urls failed name=" + shortcut.name + " appId=" + appId + ", falling back to SteamGridDB");
                fetchSteamGridCoverArt(holder, shortcut, landscape);
            }
            catch (Exception e) {
                Log.w("CoverArt", "Steam CDN cover art failed name=" + shortcut.name, e);
                fetchSteamGridCoverArt(holder, shortcut, landscape);
            }
        });
    }

    private List<String> buildSteamCoverUrls(int appId, boolean landscape) {
        List<String> urls = new ArrayList<>();
        String primaryHost = "https://shared.steamstatic.com/store_item_assets/steam/apps";

        if (landscape) {
            // Wide art: prefer hero / header / wide capsule for landscape tiles
            urls.add(primaryHost + "/" + appId + "/library_hero.jpg");
            urls.add(primaryHost + "/" + appId + "/header.jpg");
            urls.add(primaryHost + "/" + appId + "/capsule_616x353.jpg");
            urls.add(primaryHost + "/" + appId + "/capsule_231x87.jpg");

            for (String host : STEAM_ASSET_HOSTS) {
                urls.add("https://" + host + "/steam/apps/" + appId + "/library_hero.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/header.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/capsule_616x353.jpg");
            }
        } else {
            // Portrait: Steam library capsule 600x900
            urls.add(primaryHost + "/" + appId + "/library_600x900_2x.jpg");
            urls.add(primaryHost + "/" + appId + "/library_600x900.jpg");
            urls.add(primaryHost + "/" + appId + "/capsule_616x353.jpg");
            urls.add(primaryHost + "/" + appId + "/header.jpg");

            for (String host : STEAM_ASSET_HOSTS) {
                urls.add("https://" + host + "/steam/apps/" + appId + "/library_600x900_2x.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/library_600x900.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/header.jpg");
            }
        }

        return urls;
    }

    private void fetchSteamGridCoverArt(@NonNull ShortcutsAdapter.ViewHolder holder, @NonNull Shortcut shortcut, boolean landscape) {
        if (shouldSkipSteamGrid(shortcut.name)) {
            fetchSteamHeaderFallback(holder, shortcut, landscape);
            return;
        }

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(STEAMGRID_BASE_URL)
                .client(DohOkHttp.get())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        SteamGridDBApi api = retrofit.create(SteamGridDBApi.class);
        String searchName = normalizeSteamGridSearchName(shortcut.name);
        Call<SteamGridSearchResponse> call = api.searchGame("Bearer " + STEAMGRID_API_KEY, searchName);

        call.enqueue(new retrofit2.Callback<SteamGridSearchResponse>() {
            @Override
            public void onResponse(Call<SteamGridSearchResponse> call, Response<SteamGridSearchResponse> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful()) {
                    Log.w("CoverArt", "SteamGrid search failed name=" + shortcut.name + " code=" + response.code());
                    markSteamGridFailed(shortcut.name);
                    fetchSteamHeaderFallback(holder, shortcut, landscape);
                    return;
                }
                if (response.body() == null || response.body().data == null || response.body().data.isEmpty()) {
                    Log.w("CoverArt", "SteamGrid search empty name=" + shortcut.name);
                    markSteamGridFailed(shortcut.name);
                    fetchSteamHeaderFallback(holder, shortcut, landscape);
                    return;
                }

                fetchGridsForGame(holder, response.body().data.get(0).id, shortcut, landscape);
            }

            @Override
            public void onFailure(Call<SteamGridSearchResponse> call, Throwable t) {
                Log.w("CoverArt", "SteamGrid search error name=" + shortcut.name, t);
                markSteamGridFailed(shortcut.name);
                fetchSteamHeaderFallback(holder, shortcut, landscape);
            }
        });
    }

    private static String normalizeSteamGridSearchName(@NonNull String name) {
        String normalized = name.trim();
        String lower = normalized.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").trim();
        if (lower.equals("gta 4") || lower.equals("gta iv")) return "Grand Theft Auto IV";
        if (lower.equals("gta 5") || lower.equals("gta v")) return "Grand Theft Auto V";
        if (lower.equals("gta sa")) return "Grand Theft Auto San Andreas";
        if (lower.equals("gta vc")) return "Grand Theft Auto Vice City";
        return normalized;
    }

    private void fetchGridsForGame(@NonNull ShortcutsAdapter.ViewHolder holder, int gameId, @NonNull Shortcut shortcut, boolean landscape) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(SteamGridGridsResponse.class, new SteamGridGridsResponseDeserializer())
                .setPrettyPrinting()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(STEAMGRID_BASE_URL)
                .client(DohOkHttp.get())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        SteamGridDBApi api = retrofit.create(SteamGridDBApi.class);

        final String dimensions = landscape ? "920x430,460x215" : "600x900";
        final String auth = "Bearer " + STEAMGRID_API_KEY;

        Call<SteamGridGridsResponse> animatedCall = api.getAnimatedGridsByGameId(
                auth, gameId, "alternate,blurred,white_logo,material,no_logo", dimensions, "animated", "image/webp", "false", "false");

        animatedCall.enqueue(new retrofit2.Callback<SteamGridGridsResponse>() {
            @Override
            public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().data != null && !response.body().data.isEmpty()) {
                    SteamGridGridsResponse.Grid best = null;
                    SteamGridGridsResponse.Grid apngFallback = null;
                    for (SteamGridGridsResponse.Grid g : response.body().data) {
                        String mime = g.mime != null ? g.mime.toLowerCase(Locale.US) : "";
                        String ext = g.url != null ? urlToExtension(g.url) : "";
                        if (mime.contains("webp") || ".webp".equals(ext) || mime.contains("gif") || ".gif".equals(ext)) {
                            best = g;
                            break;
                        }
                        if (apngFallback == null && (mime.contains("png") || ".png".equals(ext))) apngFallback = g;
                    }
                    if (best == null) best = apngFallback;
                    if (best != null && best.url != null && !best.url.isEmpty()) {
                        Log.i("CoverArt", "Animated cover found name=" + shortcut.name + " mime=" + best.mime + " url=" + best.url);
                        downloadCoverArt(holder, best.url, shortcut, landscape, true, best.mime);
                        return;
                    }
                }
                Log.i("CoverArt", "No WebP animated cover for name=" + shortcut.name + ", trying any animated cover");
                fetchAnyAnimatedGridsFallback(holder, gameId, shortcut, landscape, api, dimensions, auth);
            }

            @Override
            public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                if (!isAdded()) return;
                fetchAnyAnimatedGridsFallback(holder, gameId, shortcut, landscape, api, dimensions, auth);
            }
        });
    }

    private void fetchAnyAnimatedGridsFallback(@NonNull ShortcutsAdapter.ViewHolder holder, int gameId,
                                               @NonNull Shortcut shortcut, boolean landscape,
                                               SteamGridDBApi api, String dimensions, String auth) {
        Call<SteamGridGridsResponse> anyAnimatedCall = api.getAnimatedGridsByGameId(
                auth, gameId, "alternate,blurred,white_logo,material,no_logo", dimensions, "animated", null, "false", "false");

        anyAnimatedCall.enqueue(new retrofit2.Callback<SteamGridGridsResponse>() {
            @Override
            public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().data != null && !response.body().data.isEmpty()) {
                    SteamGridGridsResponse.Grid grid = response.body().data.get(0);
                    if (grid.url != null && !grid.url.isEmpty()) {
                        downloadCoverArt(holder, grid.url, shortcut, landscape, true, grid.mime);
                        return;
                    }
                }
                fetchStaticGridsFallback(holder, gameId, shortcut, landscape, api, dimensions, auth);
            }

            @Override
            public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                if (!isAdded()) return;
                fetchStaticGridsFallback(holder, gameId, shortcut, landscape, api, dimensions, auth);
            }
        });
    }

    private void fetchStaticGridsFallback(@NonNull ShortcutsAdapter.ViewHolder holder, int gameId,
                                           @NonNull Shortcut shortcut, boolean landscape,
                                           SteamGridDBApi api, String dimensions, String auth) {
        Call<SteamGridGridsResponse> gridsCall = api.getGridsByGameId(
                auth,
                gameId,
                "alternate",
                dimensions,
                "static");

        gridsCall.enqueue(new retrofit2.Callback<SteamGridGridsResponse>() {
            @Override
            public void onResponse(Call<SteamGridGridsResponse> call, Response<SteamGridGridsResponse> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful()) {
                    Log.w("CoverArt", "SteamGrid grids failed name=" + shortcut.name + " code=" + response.code());
                    markSteamGridFailed(shortcut.name);
                    fetchSteamHeaderFallback(holder, shortcut, landscape);
                    return;
                }
                if (response.body() == null || response.body().data == null || response.body().data.isEmpty()) {
                    Log.w("CoverArt", "SteamGrid grids empty name=" + shortcut.name);
                    markSteamGridFailed(shortcut.name);
                    fetchSteamHeaderFallback(holder, shortcut, landscape);
                    return;
                }

                String url = response.body().data.get(0).url;
                if (url == null || url.isEmpty()) {
                    Log.w("CoverArt", "SteamGrid grid url empty name=" + shortcut.name);
                    markSteamGridFailed(shortcut.name);
                    fetchSteamHeaderFallback(holder, shortcut, landscape);
                    return;
                }
                downloadCoverArt(holder, url, shortcut, landscape);
            }

            @Override
            public void onFailure(Call<SteamGridGridsResponse> call, Throwable t) {
                Log.w("CoverArt", "SteamGrid grids error name=" + shortcut.name, t);
                markSteamGridFailed(shortcut.name);
                fetchSteamHeaderFallback(holder, shortcut, landscape);
            }
        });
    }

    private boolean shouldSkipSteamGrid(@NonNull String shortcutName) {
        Long lastFail = STEAMGRID_LAST_FAIL.get(shortcutName);
        if (lastFail == null) return false;
        return (System.currentTimeMillis() - lastFail) < STEAMGRID_NEGATIVE_TTL_MS;
    }

    private void markSteamGridFailed(@NonNull String shortcutName) {
        STEAMGRID_LAST_FAIL.put(shortcutName, System.currentTimeMillis());
    }

    private void fetchSteamHeaderFallback(@NonNull ShortcutsAdapter.ViewHolder holder, @NonNull Shortcut shortcut, boolean landscape) {
        final String expectedKey = shortcut.name;
        COVER_ART_EXECUTOR.execute(() -> {
            try {
                Integer appId = resolveSteamAppIdByStoreSearch(shortcut.name);
                if (appId == null || appId <= 0) {
                    Log.w("CoverArt", "Steam fallback: appid not found name=" + shortcut.name);
                    return;
                }

                for (String url : buildSteamHeaderUrls(appId, landscape)) {
                    Bitmap bmp = downloadBitmap(url);
                    if (bmp == null) continue;

                    cacheCoverArt(bmp, shortcut.name, landscape);

                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        if (!expectedKey.equals(holder.boundKey)) return;
                        holder.imageView.setImageBitmap(bmp);
                    });
                    return;
                }

                Log.w("CoverArt", "Steam fallback: all header urls failed name=" + shortcut.name + " appid=" + appId);
            }
            catch (Exception e) {
                Log.w("CoverArt", "Steam fallback failed name=" + shortcut.name, e);
            }
        });
    }

    private List<String> buildSteamHeaderUrls(int appId, boolean landscape) {
        List<String> urls = new ArrayList<>();

        for (String host : STEAM_ASSET_HOSTS) {
            if (landscape) {
                // Wide assets first
                urls.add("https://" + host + "/steam/apps/" + appId + "/library_hero.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/header.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/capsule_616x353.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/capsule_231x87.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/capsule_184x69.jpg");
            } else {
                // Portrait poster first
                urls.add("https://" + host + "/steam/apps/" + appId + "/library_600x900_2x.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/library_600x900.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/capsule_616x353.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/capsule_184x69.jpg");
                urls.add("https://" + host + "/steam/apps/" + appId + "/header.jpg");
            }
        }

        return urls;
    }

    private Integer resolveSteamAppIdByStoreSearch(@NonNull String queryName) {
        try {
            String q = queryName.trim();
            if (q.length() < 4) {
                Log.w("CoverArt", "Steam storesearch skipped (query too short) name=" + queryName);
                return null;
            }

            Integer cached = STEAM_APPID_CACHE.get(queryName);
            if (cached != null && cached > 0) {
                return cached;
            }

            String encoded = URLEncoder.encode(queryName, StandardCharsets.UTF_8.name());
            String url = "https://store.steampowered.com/api/storesearch/?term=" + encoded + "&l=english&cc=us";

            Request request = new Request.Builder().url(url).build();
            try (okhttp3.Response response = DohOkHttp.get().newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    Log.w("CoverArt", "Steam storesearch failed code=" + response.code() + " name=" + queryName);
                    return null;
                }

                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                JSONArray items = json.optJSONArray("items");
                if (items == null || items.length() == 0) return null;

                String qNorm = q.toLowerCase(Locale.US);

                String[] qTokens = qNorm.split("[^a-z0-9]+", -1);
                int bestScore = -1;
                int bestId = 0;
                String bestName = null;

                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.optJSONObject(i);
                    if (it == null) continue;
                    String name = it.optString("name", "").trim();
                    if (name.isEmpty()) continue;
                    int id = it.optInt("id", 0);
                    if (id <= 0) continue;

                    String nNorm = name.toLowerCase(Locale.US);
                    int score = 0;

                    if (nNorm.equals(qNorm)) {
                        score += 100;
                    } else if (nNorm.contains(qNorm)) {
                        score += 20;
                    }

                    String[] nTokens = nNorm.split("[^a-z0-9]+", -1);
                    int matched = 0;
                    int qTokenCount = 0;

                    for (String qt : qTokens) {
                        if (qt == null || qt.isEmpty()) continue;
                        qTokenCount++;

                        boolean found = false;
                        for (String nt : nTokens) {
                            if (nt == null || nt.isEmpty()) continue;
                            if (nt.equals(qt)) {
                                found = true;
                                break;
                            }
                        }

                        if (found) matched++;
                    }

                    if (qTokenCount > 0) {
                        score += matched * 10;
                        if (matched == qTokenCount) score += 60;
                    }

                    // Avoid generic one-token queries matching a longer title (e.g. Minecraft -> Minecraft Dungeons)
                    if (qTokenCount == 1 && !nNorm.equals(qNorm)) {
                        int nTokenCount = 0;
                        for (String nt : nTokens) {
                            if (nt != null && !nt.isEmpty()) nTokenCount++;
                        }
                        if (nTokenCount > 1) score -= 50;
                    }

                    // Penalize obviously wrong matches (e.g. "Fallout 1" -> "Fallout 1st")
                    if (qTokenCount > 0 && matched == 0) score -= 20;

                    if (score > bestScore) {
                        bestScore = score;
                        bestId = id;
                        bestName = name;
                    }
                }

                if (bestId > 0 && bestScore >= 60) {
                    Log.i("CoverArt", "Steam storesearch best match name=" + queryName + " -> appid=" + bestId + " (" + bestName + ") score=" + bestScore);
                    STEAM_APPID_CACHE.put(queryName, bestId);
                    return bestId;
                }

                Log.w("CoverArt", "Steam storesearch: no good match found name=" + queryName + " items=" + items.length() + " bestScore=" + bestScore + " bestName=" + bestName);
                return null;
            }
        }
        catch (Exception e) {
            Log.w("CoverArt", "Steam storesearch error name=" + queryName, e);
            return null;
        }
    }

    private Bitmap downloadBitmap(@NonNull String url) {
        try {
            Request request = new Request.Builder().url(url).build();
            try (okhttp3.Response response = COVER_ART_HTTP.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w("CoverArt", "Steam header HTTP failed code=" + response.code() + " url=" + url);
                    return null;
                }
                ResponseBody body = response.body();
                if (body == null) return null;
                byte[] bytes = body.bytes();
                if (bytes.length == 0) return null;
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
        }
        catch (Exception e) {
            Log.w("CoverArt", "Steam header download failed url=" + url, e);
            return null;
        }
    }

    private void downloadCoverArt(@NonNull ShortcutsAdapter.ViewHolder holder, @NonNull String url, @NonNull Shortcut shortcut, boolean landscape) {
        downloadCoverArt(holder, url, shortcut, landscape, false, null);
    }

    private void downloadCoverArt(@NonNull ShortcutsAdapter.ViewHolder holder, @NonNull String url, @NonNull Shortcut shortcut, boolean landscape, boolean isAnimated) {
        downloadCoverArt(holder, url, shortcut, landscape, isAnimated, null);
    }

    private void downloadCoverArt(@NonNull ShortcutsAdapter.ViewHolder holder, @NonNull String url, @NonNull Shortcut shortcut, boolean landscape, boolean isAnimated, String mime) {
        final String expectedKey = shortcut.name;
        COVER_ART_EXECUTOR.execute(() -> {
            try {
                Log.d("CoverArt", "Downloading cover name=" + shortcut.name + " url=" + url + " landscape=" + landscape + " animated=" + isAnimated);
                final boolean isSteamGridCdn = url.contains("steamgriddb.com");
                Request request = new Request.Builder()
                        .url(url)
                        .build();

                okhttp3.OkHttpClient http = isSteamGridCdn ? STEAMGRID_HTTP : COVER_ART_HTTP;

                try (okhttp3.Response response = http.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Log.w("CoverArt", "HTTP failed name=" + shortcut.name + " code=" + response.code() + " url=" + url);
                        if (isSteamGridCdn) {
                            markSteamGridFailed(shortcut.name);
                            fetchSteamHeaderFallback(holder, shortcut, landscape);
                        }
                        return;
                    }

                    ResponseBody body = response.body();
                    if (body == null) {
                        Log.w("CoverArt", "HTTP empty body name=" + shortcut.name + " url=" + url);
                        if (isSteamGridCdn) {
                            markSteamGridFailed(shortcut.name);
                            fetchSteamHeaderFallback(holder, shortcut, landscape);
                        }
                        return;
                    }

                    byte[] bytes = body.bytes();
                    if (bytes.length == 0) {
                        Log.w("CoverArt", "HTTP empty bytes name=" + shortcut.name + " url=" + url);
                        if (isSteamGridCdn) {
                            markSteamGridFailed(shortcut.name);
                            fetchSteamHeaderFallback(holder, shortcut, landscape);
                        }
                        return;
                    }

                    String ext = urlToExtension(url);
                    if (mime != null && mime.contains("webp")) ext = ".webp";
                    boolean storeAsFile = isAnimated || isAnimatedBytes(bytes, ext);

                    if (storeAsFile) {
                        File cacheDir = new File(requireContext().getFilesDir(), "coverArtCache");
                        if (!cacheDir.exists()) cacheDir.mkdirs();
                        String animExt = detectAnimExtension(bytes, url, mime);
                        String animFileName = landscape ? (shortcut.name + "_l" + animExt) : (shortcut.name + animExt);
                        File animFile = new File(cacheDir, animFileName);
                        try (FileOutputStream fos = new FileOutputStream(animFile)) {
                            fos.write(bytes);
                            fos.flush();
                        }

                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!expectedKey.equals(holder.boundKey)) return;
                            loadAnimatedCover(holder.imageView, animFile);
                        });
                    } else {
                        Bitmap coverArt = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        if (coverArt == null) {
                            if (isSteamGridCdn) {
                                markSteamGridFailed(shortcut.name);
                                fetchSteamHeaderFallback(holder, shortcut, landscape);
                            }
                            return;
                        }

                        cacheCoverArt(coverArt, shortcut.name, landscape);

                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            if (!expectedKey.equals(holder.boundKey)) return;
                            holder.imageView.setImageBitmap(coverArt);
                        });
                    }
                }
            } catch (Exception e) {
                Log.w("CoverArt", "Download failed name=" + shortcut.name + " url=" + url, e);
                if (url.contains("steamgriddb.com")) {
                    markSteamGridFailed(shortcut.name);
                    fetchSteamHeaderFallback(holder, shortcut, landscape);
                }
            }
        });
    }

    private static String coverCacheFileName(@NonNull String shortcutName, boolean landscape) {
        return landscape ? (shortcutName + "_l.png") : (shortcutName + ".png");
    }

    private static final String[] ANIMATED_EXTENSIONS = {".gif", ".webp", ".apng"};

    private File getCachedCoverFile(@NonNull String shortcutName, boolean landscape) {
        File cacheDir = new File(requireContext().getFilesDir(), "coverArtCache");
        for (String ext : ANIMATED_EXTENSIONS) {
            String name = landscape ? (shortcutName + "_l" + ext) : (shortcutName + ext);
            File f = new File(cacheDir, name);
            if (f.exists()) return f;
        }
        File staticFile = new File(cacheDir, coverCacheFileName(shortcutName, landscape));
        if (staticFile.exists()) return staticFile;
        return null;
    }

    private boolean isCachedCoverAnimated(@NonNull String shortcutName, boolean landscape) {
        File cacheDir = new File(requireContext().getFilesDir(), "coverArtCache");
        for (String ext : ANIMATED_EXTENSIONS) {
            String name = landscape ? (shortcutName + "_l" + ext) : (shortcutName + ext);
            if (new File(cacheDir, name).exists()) return true;
        }
        return false;
    }

    private void loadCoverWithGlide(@NonNull ImageView imageView, @NonNull File file) {
        Glide.with(this)
                .load(file)
                .centerCrop()
                .into(imageView);
    }

    private void loadAnimatedCover(@NonNull ImageView imageView, @NonNull File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(file);
                Drawable drawable = ImageDecoder.decodeDrawable(source, (decoder, info, src) -> decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
                imageView.setImageDrawable(drawable);
                if (drawable instanceof AnimatedImageDrawable) {
                    ((AnimatedImageDrawable)drawable).start();
                    Log.i("CoverArt", "Animated drawable started file=" + file.getName());
                    return;
                }
                Log.w("CoverArt", "Decoded animated cover is static drawable file=" + file.getName());
            } catch (Exception e) {
                Log.w("CoverArt", "ImageDecoder failed for animated cover file=" + file.getName(), e);
            }
        }
        loadCoverWithGlide(imageView, file);
    }

    private static boolean isAnimatedBytes(byte[] bytes, String ext) {
        if (".gif".equalsIgnoreCase(ext)) return true;
        if (".png".equalsIgnoreCase(ext)) {
            if (bytes.length > 8) {
                int offset = 8;
                while (offset + 4 + 4 <= bytes.length) {
                    int chunkLen = ((bytes[offset] & 0xFF) << 24) | ((bytes[offset + 1] & 0xFF) << 16)
                            | ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
                    String chunkType = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
                    if ("acTL".equals(chunkType)) return true;
                    if ("IDAT".equals(chunkType)) break;
                    offset += 4 + 4 + chunkLen + 4;
                }
            }
        }
        if (".webp".equalsIgnoreCase(ext)) {
            if (bytes.length >= 12) {
                int riffSize = ((bytes[7] & 0xFF) << 24) | ((bytes[6] & 0xFF) << 16)
                        | ((bytes[5] & 0xFF) << 8) | (bytes[4] & 0xFF);
                int offset = 12;
                while (offset + 8 <= bytes.length) {
                    String chunk = new String(bytes, offset, 4);
                    int chunkSize = ((bytes[offset + 7] & 0xFF) << 24) | ((bytes[offset + 6] & 0xFF) << 16)
                            | ((bytes[offset + 5] & 0xFF) << 8) | (bytes[offset + 4] & 0xFF);
                    if ("ANIM".equals(chunk)) return true;
                    offset += 8 + chunkSize + (chunkSize & 1);
                    if (offset > riffSize + 8) break;
                }
            }
        }
        return false;
    }

    private static String urlToExtension(String url) {
        String path = url.split("[?#]", 2)[0];
        int dot = path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            String ext = path.substring(dot).toLowerCase();
            if (ext.equals(".gif") || ext.equals(".webp") || ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg"))
                return ext;
        }
        return ".png";
    }

    private static String detectAnimExtension(byte[] bytes, String url, String mime) {
        if (mime != null && mime.contains("webp")) return ".webp";
        String urlExt = urlToExtension(url);
        if (".gif".equals(urlExt)) return ".gif";
        if (".webp".equals(urlExt) && isAnimatedBytes(bytes, urlExt)) return ".webp";
        if (".png".equals(urlExt) && isAnimatedBytes(bytes, urlExt)) return ".png";
        if (isAnimatedBytes(bytes, urlExt)) return ".gif";
        return ".png";
    }

    private void cacheCoverArt(@NonNull Bitmap coverArt, @NonNull String shortcutName, boolean landscape) {
        try {
            File cacheDir = new File(requireContext().getFilesDir(), "coverArtCache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File coverFile = new File(cacheDir, coverCacheFileName(shortcutName, landscape));
            try (FileOutputStream outputStream = new FileOutputStream(coverFile)) {
                coverArt.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                outputStream.flush();
            }
        } catch (IOException ignored) {
        }
    }

    private Bitmap loadCachedCoverArt(@NonNull String shortcutName, boolean landscape) {
        try {
            File cacheDir = new File(requireContext().getFilesDir(), "coverArtCache");
            File coverFile = new File(cacheDir, coverCacheFileName(shortcutName, landscape));
            if (coverFile.exists()) {
                return BitmapFactory.decodeFile(coverFile.getAbsolutePath());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void runFromShortcut(Shortcut shortcut) {
        Activity activity = getActivity();
        if (activity == null) return;

        if (!XrActivity.isEnabled(getContext())) {
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", shortcut.container.id);
            intent.putExtra("shortcut_path", shortcut.file.getPath());
            intent.putExtra("shortcut_name", shortcut.name);
            String disableXinputValue = shortcut.getExtra("disableXinput", "0");
            intent.putExtra("disableXinput", disableXinputValue);
            activity.startActivity(intent);
        }
        else {
            XrActivity.openIntent(activity, shortcut.container.id, shortcut.file.getPath());
        }
    }

    private void showListItemMenu(View anchorView, final Shortcut shortcut) {
        final Context context = getContext();
        if (context == null) return;

        PopupMenu listItemMenu = new PopupMenu(context, anchorView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

        listItemMenu.inflate(R.menu.shortcut_popup_menu);
        listItemMenu.setOnMenuItemClickListener((menuItem) -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.shortcut_settings) {
                Intent settingsIntent = new Intent(getContext(), ShortcutSettingsActivity.class);
                settingsIntent.putExtra(ShortcutSettingsActivity.EXTRA_CONTAINER_ID, shortcut.container.id);
                settingsIntent.putExtra(ShortcutSettingsActivity.EXTRA_SHORTCUT_PATH, shortcut.file.getAbsolutePath());
                startActivityForResult(settingsIntent, REQUEST_CODE_SHORTCUT_SETTINGS);
            }
            else if (itemId == R.id.shortcut_remove) {
                ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_shortcut, () -> {
                    boolean fileDeleted = shortcut.file.delete();
                    if (fileDeleted) {
                        disableShortcutOnScreen(requireContext(), shortcut);
                        loadShortcutsList();
                        Toast.makeText(context, "Shortcut removed successfully.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "Failed to remove the shortcut. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            else if (itemId == R.id.shortcut_clone_to_container) {
                ContainerManager containerManager = new ContainerManager(context);
                ArrayList<Container> containers = containerManager.getContainers();

                showContainerSelectionDialog(containers, selectedContainer -> {
                    if (shortcut.cloneToContainer(selectedContainer)) {
                        Toast.makeText(context, "Shortcut cloned successfully.", Toast.LENGTH_SHORT).show();
                        loadShortcutsList();
                    } else {
                        Toast.makeText(context, "Failed to clone shortcut.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            else if (itemId == R.id.shortcut_add_to_home_screen) {
                if (shortcut.getExtra("uuid").equals("")) shortcut.genUUID();
                addShortcutToScreen(shortcut);
            }
            else if (itemId == R.id.shortcut_export_to_frontend) {
                exportShortcutToFrontend(shortcut);
            }
            else if (itemId == R.id.shortcut_properties) {
                showShortcutProperties(shortcut);
            }
            return true;
        });
        listItemMenu.show();
    }

    private interface OnContainerSelectedListener {
        void onContainerSelected(Container container);
    }

    private void showContainerSelectionDialog(ArrayList<Container> containers, OnContainerSelectedListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.select_container);

        String[] containerNames = new String[containers.size()];
        for (int i = 0; i < containers.size(); i++) {
            containerNames[i] = containers.get(i).getName();
        }

        builder.setItems(containerNames, (dialog, which) -> listener.onContainerSelected(containers.get(which)));
        builder.show();
    }

    private void exportShortcutToFrontend(Shortcut shortcut) {
        Context context = getContext();
        if (context == null) return;

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String uriString = sharedPreferences.getString("frontend_export_uri", null);

        File frontendDir;

        if (uriString != null) {
            Uri folderUri = Uri.parse(uriString);
            DocumentFile pickedDir = DocumentFile.fromTreeUri(context, folderUri);
            if (pickedDir == null || !pickedDir.canWrite()) {
                Toast.makeText(context, "Cannot write to the selected folder", Toast.LENGTH_SHORT).show();
                return;
            }
            frontendDir = new File(FileUtils.getFilePathFromUri(context, folderUri));
        } else {
            frontendDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Winlator/Frontend");
            if (!frontendDir.exists() && !frontendDir.mkdirs()) {
                Toast.makeText(context, "Failed to create default directory", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        File instructionsFile = new File(frontendDir, "FRONTEND_INSTRUCTIONS.txt");
        if (!instructionsFile.exists()) {
            try (FileWriter writer = new FileWriter(instructionsFile, false)) {
                writer.write("Instructions for adding Winlator shortcuts to Frontends (WIP):\n\n");
                writer.write("Daijisho:\n\n");
                writer.write("1. Open Daijisho\n");
                writer.write("2. Navigate to the Settings tab.\n");
                writer.write("3. Navigate to Settings\\Library\n");
                writer.write("4. Select, Import from Pegasus\n");
                writer.write("5. Add the metadata.pegasus.txt file located in this directory (Downloads\\Winlator\\Frontend)\n");
                writer.write("6. Set the Sync path to Downloads\\Winlator\\Frontend\n");
                writer.write("7. Start your game!\n\n");
                writer.write("Beacon:\n\n");
                writer.write("1. Navigate to Settings\n");
                writer.write("2. Click the + Icon\n");
                writer.write("3. Set the following values:\n\n");
                writer.write("Platform Type: Custom\n");
                writer.write("Name: Windows (or Winlator, whatever you prefer)\n");
                writer.write("Short name: windows\n");
                writer.write("Player app: Select Winlator.CMOD\n");
                writer.write("ROMs folder: Use Android FilePicker to select the Downloads\\Winlator\\Frontend directory\n");
                writer.write("Expand Advanced:\n");
                writer.write("Use custom launch: True\n");
                writer.write("am start command: am start -n " + context.getPackageName() + "/com.winlator.cmod.XServerDisplayActivity -e shortcut_path {file_path}\n\n");
                writer.flush();
            } catch (IOException e) {
                Log.e("ShortcutsFragment", "Failed to create FRONTEND_INSTRUCTIONS.txt", e);
            }
        }

        File metadataFile = new File(frontendDir, "metadata.pegasus.txt");
        try (FileWriter writer = new FileWriter(metadataFile, false)) {
            writer.write("collection: Windows\n");
            writer.write("shortname: windows\n");
            writer.write("extensions: desktop\n");
            writer.write("launch: am start\n");
            writer.write("  -n " + context.getPackageName() + "/.XServerDisplayActivity\n");
            writer.write("  -e shortcut_path {file.path}\n");
            writer.write("  --activity-clear-task\n");
            writer.write("  --activity-clear-top\n");
            writer.write("  --activity-no-history\n");
            writer.flush();
        } catch (IOException e) {
            Log.e("ShortcutsFragment", "Failed to create or update metadata.pegasus.txt", e);
        }

        File exportFile = new File(frontendDir, shortcut.file.getName());
        boolean fileExists = exportFile.exists();
        boolean containerIdFound = false;

        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(shortcut.file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("container_id:")) {
                        lines.add("container_id:" + shortcut.container.id);
                        containerIdFound = true;
                    } else {
                        lines.add(line);
                    }
                }
            }

            if (!containerIdFound) {
                lines.add("container_id:" + shortcut.container.id);
            }

            try (FileWriter writer = new FileWriter(exportFile, false)) {
                for (String line : lines) {
                    writer.write(line + "\n");
                }
                writer.flush();
            }

            String message = fileExists ?
                    "Frontend Shortcut Updated at " + exportFile.getPath() :
                    "Frontend Shortcut Exported to " + exportFile.getPath();
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e("ShortcutsFragment", "Failed to export shortcut", e);
            Toast.makeText(context, "Failed to export shortcut", Toast.LENGTH_LONG).show();
        }
    }

    private void showShortcutProperties(Shortcut shortcut) {
        Context context = getContext();
        if (context == null) return;

        SharedPreferences playtimePrefs = context.getSharedPreferences("playtime_stats", Context.MODE_PRIVATE);

        String playtimeKey = shortcut.name + "_playtime";
        String playCountKey = shortcut.name + "_play_count";

        long totalPlaytime = playtimePrefs.getLong(playtimeKey, 0);
        int playCount = playtimePrefs.getInt(playCountKey, 0);

        long seconds = (totalPlaytime / 1000) % 60;
        long minutes = (totalPlaytime / (1000 * 60)) % 60;
        long hours = (totalPlaytime / (1000 * 60 * 60)) % 24;
        long days = (totalPlaytime / (1000 * 60 * 60 * 24));
        String playtimeFormatted = String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);

        ContentDialog dialog = new ContentDialog(context, R.layout.shortcut_properties_dialog);
        dialog.setTitle("Properties");

        TextView playCountTextView = dialog.findViewById(R.id.play_count);
        TextView playtimeTextView = dialog.findViewById(R.id.playtime);

        playCountTextView.setText("Number of times played: " + playCount);
        playtimeTextView.setText("Playtime: " + playtimeFormatted);

        Button resetPropertiesButton = dialog.findViewById(R.id.reset_properties);
        resetPropertiesButton.setOnClickListener(v -> {
            playtimePrefs.edit().remove(playtimeKey).remove(playCountKey).apply();
            Toast.makeText(context, "Properties reset successfully.", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private ShortcutInfo buildScreenShortCut(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        Intent intent = new Intent(getActivity(), XServerDisplayActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra("container_id", containerId);
        intent.putExtra("shortcut_path", shortcutPath);

        return new ShortcutInfo.Builder(getActivity(), uuid)
                .setShortLabel(shortLabel)
                .setLongLabel(longLabel)
                .setIcon(icon)
                .setIntent(intent)
                .build();
    }

    private void addShortcutToScreen(Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(requireContext(), ShortcutManager.class);
        if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported())
            shortcutManager.requestPinShortcut(buildScreenShortCut(shortcut.name, shortcut.name, shortcut.container.id,
                    shortcut.file.getPath(), createShortcutIcon(requireContext(), shortcut), shortcut.getExtra("uuid")), null);
    }

    public static Icon createShortcutIcon(Context context, Shortcut shortcut) {
        Bitmap displayIcon = shortcut.getDisplayIcon();
        if (displayIcon == null) displayIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.icon_shortcut);
        return Icon.createWithBitmap(displayIcon);
    }

    public static void disableShortcutOnScreen(Context context, Shortcut shortcut) {
        ShortcutManager shortcutManager = getSystemService(context, ShortcutManager.class);
        try {
            shortcutManager.disableShortcuts(Collections.singletonList(shortcut.getExtra("uuid")),
                    context.getString(R.string.shortcut_not_available));
        } catch (Exception e) {}
    }

    public void updateShortcutOnScreen(String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        updateShortcutOnScreen(requireContext(), shortLabel, longLabel, containerId, shortcutPath, icon, uuid);
    }

    public static void updateShortcutOnScreen(Context context, Shortcut shortcut) {
        updateShortcutOnScreen(context, shortcut.name, shortcut.name, shortcut.container.id, shortcut.file.getPath(),
                createShortcutIcon(context, shortcut), shortcut.getExtra("uuid"));
    }

    private static void updateShortcutOnScreen(Context context, String shortLabel, String longLabel, int containerId, String shortcutPath, Icon icon, String uuid) {
        try {
            ShortcutManager shortcutManager = getSystemService(context, ShortcutManager.class);
            if (shortcutManager == null || uuid == null || uuid.isEmpty()) return;
            for (ShortcutInfo shortcutInfo : shortcutManager.getPinnedShortcuts()) {
                if (shortcutInfo.getId().equals(uuid)) {
                    Intent intent = new Intent(context, XServerDisplayActivity.class);
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.putExtra("container_id", containerId);
                    intent.putExtra("shortcut_path", shortcutPath);
                    ShortcutInfo updatedShortcut = new ShortcutInfo.Builder(context, uuid)
                            .setShortLabel(shortLabel)
                            .setLongLabel(longLabel)
                            .setIcon(icon)
                            .setIntent(intent)
                            .build();
                    shortcutManager.updateShortcuts(Collections.singletonList(updatedShortcut));
                    break;
                }
            }
        } catch (Exception e) {}
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT_GAME && resultCode == Activity.RESULT_OK && data != null) {
            String path = data.getStringExtra(GameImportPickerActivity.EXTRA_RESULT_PATH);
            if (path != null && shortcutContainer != null) {
                onFileSelectedForShortcut(new File(path), shortcutContainer);
            }
            return;
        }

        if (requestCode == REQUEST_CODE_CHANGE_EXE_PATH && resultCode == Activity.RESULT_OK && data != null) {
            String filePath = data.getStringExtra(GameImportConfirmActivity.EXTRA_FILE_PATH);
            String finalName = data.getStringExtra(GameImportConfirmActivity.EXTRA_RESULT_NAME);
            int containerId = data.getIntExtra(GameImportConfirmActivity.EXTRA_CONTAINER_ID, -1);
            if (filePath != null && finalName != null && containerId >= 0) {
                ContainerManager cm = new ContainerManager(getContext());
                Container targetContainer = cm.getContainerById(containerId);
                if (targetContainer != null) {
                    File exeFile = new File(filePath);
                    Bitmap exeIcon = null;
                    try {
                        exeIcon = PEParser.extractIcon(exeFile);
                    } catch (Exception ignored) {}
                    String driveLetter = detectDriveLetter(exeFile.getAbsolutePath());
                    if (driveLetter != null) {
                        String exePath = buildExePath(exeFile.getAbsolutePath(), driveLetter);
                        createShortcutFromImport(exeFile, targetContainer, driveLetter, exePath, finalName, exeIcon);
                    } else {
                        Toast.makeText(getContext(), "Wrong path! Can't detect drive!", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            return;
        }

        if (requestCode == REQUEST_CODE_SHORTCUT_SETTINGS) {
            // Settings may have renamed/edited the shortcut -- refresh the list either way.
            loadShortcutsList();
            return;
        }

        if (requestCode == REQUEST_CODE_IMPORT_BOX64_PRESET && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedFile = data.getData();
            if (importBox64PresetCallback != null) {
                importBox64PresetCallback.call(selectedFile);
                importBox64PresetCallback = null;
            }
            return;
        }

        if (requestCode == REQUEST_CODE_IMPORT_FEXCORE_PRESET && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedFile = data.getData();
            if (importFexcorePresetCallback != null) {
                importFexcorePresetCallback.call(selectedFile);
                importFexcorePresetCallback = null;
            }
            return;
        }

        if (requestCode == REQUEST_CODE_SELECT_EXE_FILE && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedFile = data.getData();
            String selectedFilePath = selectedFile.getPath().toLowerCase();
            
            if (selectedFilePath.endsWith(".exe") && selectedFilePath.contains("/document/")) {
                if (shortcutContainer != null) {
                    String driveLetter = null;

                    if (selectedFilePath.contains("primary:"))
                        driveLetter = "D:";

                    if (selectedFilePath.contains("/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/"))
                        driveLetter = "Z:";

                    if (driveLetter == null) {
                        Toast.makeText(getContext(), "Wrong path! Can't detect drive!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String fileName = queryName(getContext().getContentResolver(), selectedFile);
                    String fileNameOutExe = fileName.substring(0, fileName.length() - 4); // -.exe
                    String pathWOutDocument = selectedFilePath;
                    
                    Log.d("ShortcutsFragment", "selectedFilePath: " + selectedFilePath);
                    Log.d("ShortcutsFragment", "fileName: " + fileName);
                    Log.d("ShortcutsFragment", "driveLetter: " + driveLetter);

                    if (pathWOutDocument.startsWith("/document/primary:")) {
                        pathWOutDocument = pathWOutDocument.replaceFirst("/document/primary:", "");
                    } else if (pathWOutDocument.startsWith("/document/")) {
                        pathWOutDocument = pathWOutDocument.replaceFirst("/document/", "");
                    }

                    if (driveLetter.equals("Z:"))
                        pathWOutDocument = pathWOutDocument.replaceFirst("/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/", "");

                    if (driveLetter.equals("D:"))
                        pathWOutDocument = pathWOutDocument.replaceFirst("download/", "");
                    
                    Log.d("ShortcutsFragment", "pathWOutDocument after replaceFirst: " + pathWOutDocument);

                    // For Exec line: use Unix path format (forward slashes)
                    String execPath = pathWOutDocument;
                    
                    // For Path line: remove the filename to get directory only
                    int lastSlash = pathWOutDocument.lastIndexOf("/");
                    String pathDir = (lastSlash > 0) ? pathWOutDocument.substring(0, lastSlash) : "";
                    
                    // Use filename without extension as icon name
                    String iconName = fileNameOutExe.toLowerCase();
                    
                    String shortcutDesktop =
                            "[Desktop Entry]\n" +
                            "Name=" + fileNameOutExe + "\n" +
                            "Exec=env WINEPREFIX=\"/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.wine/dosdevices/z:/home/xuser/.wine\" wine " + driveLetter + "/" + execPath + "\n" +
                            "Type=Application\n" +
                            "StartupNotify=true\n" +
                            "Path=/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.wine/dosdevices/" + driveLetter.toLowerCase() + "/" + pathDir + "\n" +
                            "Icon=" + iconName + "\n" +
                            "StartupWMClass=" + fileName;

                    File desktopFile = new File(shortcutContainer.getDesktopDir(), fileNameOutExe + ".desktop");
                    
                    Log.d("ShortcutsFragment", "Desktop file path: " + desktopFile.getAbsolutePath());
                    Log.d("ShortcutsFragment", "Desktop file content:\n" + shortcutDesktop);

                    try (FileWriter writer = new FileWriter(desktopFile)) {
                        writer.write(shortcutDesktop);
                    } catch (IOException e) {
                        Log.e("ShortcutsFragment", e.toString());
                        Toast.makeText(getContext(), "Error occurred while adding shortcut!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    loadShortcutsList();
                    Toast.makeText(getContext(), "Shortcut created for Container: " + shortcutContainer.getName(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Wrong file type! U need choose .exe file!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void onFileSelectedForShortcut(File file, Container container) {
        // Open the full-screen confirmation activity (styled like the picker)
        Intent intent = new Intent(getContext(), GameImportConfirmActivity.class);
        intent.putExtra(GameImportConfirmActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        intent.putExtra(GameImportConfirmActivity.EXTRA_CONTAINER_ID, container.id);
        startActivityForResult(intent, REQUEST_CODE_CHANGE_EXE_PATH);
    }

    private void showImportDialog(File file, Container container, Bitmap exeIcon) {
        Context context = getContext();
        if (context == null) return;

        String absolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String fileNameWithoutExt = fileName.substring(0, fileName.length() - 4);

        // Determine drive letter
        String driveLetter = detectDriveLetter(absolutePath);
        if (driveLetter == null) {
            Toast.makeText(context, "Wrong path! Can't detect drive!", Toast.LENGTH_SHORT).show();
            return;
        }

        String exePath = buildExePath(absolutePath, driveLetter);

        // Build the dialog
        ContentDialog dialog = new ContentDialog(context, R.layout.import_game_dialog);
        dialog.setTitle(context.getString(R.string.game_import_title));

        ImageView iconView = (ImageView) dialog.findViewById(R.id.IVImportIcon);
        if (exeIcon != null) {
            iconView.setImageBitmap(exeIcon);
        } else {
            iconView.setImageResource(R.drawable.icon_shortcut);
        }

        TextInputEditText nameEdit = (TextInputEditText) dialog.findViewById(R.id.ETImportName);
        nameEdit.setText(fileNameWithoutExt);

        TextInputEditText descEdit = (TextInputEditText) dialog.findViewById(R.id.ETImportDescription);

        TextView pathView = (TextView) dialog.findViewById(R.id.TVImportExePath);
        pathView.setText(exePath);

        MaterialButton changePathBtn = (MaterialButton) dialog.findViewById(R.id.BTChangeExePath);
        // The "Change" button re-opens the GameImportPickerActivity (custom file browser)
        changePathBtn.setOnClickListener(v -> {
            dialog.dismiss();
            shortcutContainer = container;
            File currentDir = file.getParentFile();
            Intent intent = new Intent(getContext(), GameImportPickerActivity.class);
            intent.putExtra(GameImportPickerActivity.EXTRA_INITIAL_DIR, currentDir.getAbsolutePath());
            startActivityForResult(intent, REQUEST_CODE_CHANGE_EXE_PATH);
        });

        dialog.onConfirmCallback = () -> {
            String finalName = nameEdit.getText().toString().trim();
            if (finalName.isEmpty()) finalName = fileNameWithoutExt;
            createShortcutFromImport(file, container, driveLetter, exePath, finalName, exeIcon);
        };

        dialog.show();
    }

    private String detectDriveLetter(String absolutePath) {
        String relativePath = absolutePath.toLowerCase();
        String externalStoragePath = Environment.getExternalStorageDirectory().getAbsolutePath().toLowerCase();

        if (relativePath.contains(externalStoragePath)) {
            return "D:";
        } else if (relativePath.contains("/imagefs/")) {
            return "Z:";
        } else if (relativePath.contains("/.wine/drive_c/")) {
            return "C:";
        }
        return null;
    }

    private String buildExePath(String absolutePath, String driveLetter) {
        String pathWOutPrefix = absolutePath;

        if (driveLetter.equals("D:")) {
            pathWOutPrefix = pathWOutPrefix.replaceFirst(Environment.getExternalStorageDirectory().getAbsolutePath() + "/", "");
            if (pathWOutPrefix.toLowerCase().startsWith("download/")) {
                pathWOutPrefix = pathWOutPrefix.substring(9);
            }
        } else if (driveLetter.equals("Z:")) {
            int imagefsIndex = pathWOutPrefix.indexOf("/imagefs/");
            if (imagefsIndex != -1) {
                pathWOutPrefix = pathWOutPrefix.substring(imagefsIndex + 9);
            }
        } else if (driveLetter.equals("C:")) {
            int driveCIndex = pathWOutPrefix.indexOf("/.wine/drive_c/");
            if (driveCIndex != -1) {
                pathWOutPrefix = pathWOutPrefix.substring(driveCIndex + 15);
            }
        }
        return pathWOutPrefix;
    }

    private void createShortcutFromImport(File file, Container container, String driveLetter, String exePath, String finalName, Bitmap exeIcon) {
        try {
            // For Path line: remove the filename to get directory only
            int lastSlash = exePath.lastIndexOf("/");
            String pathDir = (lastSlash > 0) ? exePath.substring(0, lastSlash) : "";

            // Generate icon name in Wine format: number_filename.0
            int randomNum = (int)(Math.random() * 10000);
            String iconName = randomNum + "_" + finalName + ".0";

            // Save icon
            if (exeIcon != null) {
                File iconDir = container.getIconsDir(64);
                if (!iconDir.exists()) iconDir.mkdirs();
                File iconFile = new File(iconDir, iconName + ".png");
                FileUtils.saveBitmapToFile(exeIcon, iconFile);
                Log.d("ShortcutsFragment", "Icon saved: " + iconFile.getAbsolutePath());
            }

            String shortcutDesktop =
                "[Desktop Entry]\n" +
                "Name=" + finalName + "\n" +
                "Exec=env WINEPREFIX=\"/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.wine/dosdevices/z:/home/xuser/.wine\" wine " + driveLetter + "/" + exePath + "\n" +
                "Type=Application\n" +
                "StartupNotify=true\n" +
                "Path=/data/user/0/" + MainActivity.PACKAGE_NAME + "/files/imagefs/home/xuser/.wine/dosdevices/" + driveLetter.toLowerCase() + "/" + pathDir + "\n" +
                "Icon=" + iconName + "\n" +
                "StartupWMClass=" + finalName.toLowerCase();

            File desktopFile = new File(container.getDesktopDir(), finalName + ".desktop");

            Log.d("ShortcutsFragment", "Desktop file path: " + desktopFile.getAbsolutePath());
            Log.d("ShortcutsFragment", "Desktop file content:\n" + shortcutDesktop);

            try (FileWriter writer = new FileWriter(desktopFile)) {
                writer.write(shortcutDesktop);
            }

            loadShortcutsList();
            Toast.makeText(getContext(), "Shortcut created for Container: " + container.getName(), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e("ShortcutsFragment", "Error creating shortcut", e);
            Toast.makeText(getContext(), "Error occurred while adding shortcut!", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String queryName(ContentResolver resolver, Uri uri) {
        String[] projection = new String[] { OpenableColumns.DISPLAY_NAME };
        Cursor returnCursor = resolver.query(uri, projection, null, null, null);
        assert returnCursor != null;
        returnCursor.moveToFirst();
        String name = returnCursor.getString(0);
        returnCursor.close();
        return name;
    }
}
