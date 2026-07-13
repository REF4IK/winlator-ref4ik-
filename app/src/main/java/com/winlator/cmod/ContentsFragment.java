package com.winlator.cmod;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.R;
import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contentdialog.ContentInfoDialog;
import com.winlator.cmod.contentdialog.ContentUntrustedDialog;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.PreloaderDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class ContentsFragment extends Fragment {
    private RecyclerView recyclerView;
    private View emptyText;
    private ContentsManager manager;
    SharedPreferences sp;
    private SharedPreferences fileSizeCache;
    private ContentProfile.ContentType currentContentType = ContentProfile.ContentType.CONTENT_TYPE_WINE;
    private Spinner sContentType;

    private boolean isDarkMode;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        manager = new ContentsManager(getContext());
        manager.syncContents();
        sp = new com.winlator.cmod.core.MmkvPreferences();
        
        // РРЅРёС†РёР°Р»РёР·Р°С†РёСЏ РєСЌС€Р° СЂР°Р·РјРµСЂРѕРІ С„Р°Р№Р»РѕРІ
        fileSizeCache = new com.winlator.cmod.core.MmkvPreferences("file_size_cache");

        // Initialize isDarkMode based on shared preferences or theme
        isDarkMode = sp.getBoolean("dark_mode", false);
    }

    @Override
    public void onDestroy() {
        FileUtils.clear(getContext().getCacheDir());
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        new Thread(() -> {
            String contentsURL = sp.getString("downloadable_contents_url", "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json");
            String json = Downloader.downloadString(contentsURL);
            if (json == null)
                return;
            getActivity().runOnUiThread(() -> {
                manager.setRemoteProfiles(json);
                loadContentList();
            });
        }).start();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.contents);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.contents_fragment, container, false);

        sContentType = layout.findViewById(R.id.SContentType);
        updateContentTypeSpinner(sContentType);
        sContentType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentContentType = ContentProfile.ContentType.values()[position];
                loadContentList();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        emptyText = layout.findViewById(R.id.TVEmptyText);

        layout.findViewById(R.id.BTContentsSettings).setOnClickListener(v -> showContentsUrlDialog());

        View btInstallContent = layout.findViewById(R.id.BTInstallContent);
        btInstallContent.setOnClickListener(v -> {
            ContentDialog.confirm(getContext(), getString(R.string.do_you_want_to_install_content) + " " + getString(R.string.pls_make_sure_content_trustworthy) + " "
                    + getString(R.string.content_suffix_is_wcp_packed_xz_zst) + '\n' + getString(R.string.get_more_contents_form_github), () -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                getActivity().startActivityFromFragment(this, intent, MainActivity.OPEN_FILE_REQUEST_CODE);
            });
        });

        layout.findViewById(R.id.BTInstalledComponents).setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                .replace(R.id.FLFragmentContainer, new InstalledComponentsFragment())
                .addToBackStack(null)
                .commit();
        });

        recyclerView = layout.findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));
        loadContentList();

        return layout;
    }

    private void updateContentTypeSpinner(Spinner spinner) {
        List<String> typeList = new ArrayList<>();
        for (ContentProfile.ContentType type : ContentProfile.ContentType.values())
            typeList.add(type.toString());
        spinner.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, typeList));

        // Set the popup background based on the theme
        spinner.setPopupBackgroundResource(isDarkMode ? R.drawable.content_dialog_background_dark : R.drawable.content_dialog_background);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentContentType = ContentProfile.ContentType.values()[position];
                updateContentsListView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private static final String URL_REF4IK = "https://github.com/REF4IK/Components-Adrenotools-/releases/download/1/contents.json";
    private static final String URL_THE412BANNER = "https://raw.githubusercontent.com/The412Banner/winlator-contents/main/contents.json";

    private void showContentsUrlDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(R.string.contents_source);

        String currentUrl = sp.getString("downloadable_contents_url", URL_REF4IK);

        final String[] presets = {"REF4IK", "The412Banner", getString(R.string.custom_profile)};
        final String[] urls = {URL_REF4IK, URL_THE412BANNER, null};

        int checkedItem = 2;
        if (currentUrl.equals(URL_REF4IK)) checkedItem = 0;
        else if (currentUrl.equals(URL_THE412BANNER)) checkedItem = 1;

        builder.setSingleChoiceItems(presets, checkedItem, (dialog, which) -> {
            if (which < 2) {
                sp.edit().putString("downloadable_contents_url", urls[which]).apply();
                dialog.dismiss();
                onResume();
            } else {
                dialog.dismiss();
                showCustomUrlDialog();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showCustomUrlDialog() {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(R.string.custom_profile);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(getContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad / 2);

        final EditText inputName = new EditText(getContext());
        inputName.setHint(R.string.profile_name_hint);
        inputName.setSingleLine(true);
        inputName.setText(sp.getString("custom_contents_name", ""));
        layout.addView(inputName);

        final EditText inputUrl = new EditText(getContext());
        inputUrl.setHint(R.string.profile_url_hint);
        inputUrl.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        inputUrl.setSingleLine(true);
        String savedCustomUrl = sp.getString("custom_contents_url", "");
        inputUrl.setText(savedCustomUrl);
        layout.addView(inputUrl);

        builder.setView(layout);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String name = inputName.getText().toString().trim();
            String url = inputUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                sp.edit()
                    .putString("custom_contents_name", name)
                    .putString("custom_contents_url", url)
                    .putString("downloadable_contents_url", url)
                    .apply();
                onResume();
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void updateContentsListView() {
        List<ContentProfile> profiles = manager.getProfiles(currentContentType);
        if (profiles.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyText.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            PreloaderDialog preloaderDialog = new PreloaderDialog(getActivity());
            preloaderDialog.showOnUiThread(R.string.installing_content);
            try {
                ContentsManager.OnInstallFinishedCallback callback = new ContentsManager.OnInstallFinishedCallback() {
                    private boolean isExtracting = true;

                    @Override
                    public void onFailed(ContentsManager.InstallFailedReason reason, Exception e) {
                        int msgId = switch (reason) {
                            case ERROR_BADTAR -> R.string.file_cannot_be_recognied;
                            case ERROR_NOPROFILE -> R.string.profile_not_found_in_content;
                            case ERROR_BADPROFILE -> R.string.profile_cannot_be_recognized;
                            case ERROR_EXIST -> R.string.content_already_exist;
                            case ERROR_MISSINGFILES -> R.string.content_is_incomplete;
                            case ERROR_UNTRUSTPROFILE -> R.string.content_cannot_be_trusted;
                            default -> R.string.unable_to_install_content;
                        };
                        requireActivity().runOnUiThread(() -> ContentDialog.alert(getContext(), getString(R.string.install_failed) + ": " + getString(msgId), preloaderDialog::closeOnUiThread));
                    }

                    @Override
                    public void onSucceed(ContentProfile profile) {
                        if (isExtracting) {
                            ContentsManager.OnInstallFinishedCallback callback1 = this;
                            requireActivity().runOnUiThread(() -> {
                                ContentInfoDialog dialog = new ContentInfoDialog(getContext(), profile);
                                ((TextView) dialog.findViewById(R.id.BTConfirm)).setText(R.string._continue);
                                dialog.setOnConfirmCallback(() -> {
                                    isExtracting = false;
                                    List<ContentProfile.ContentFile> untrustedFiles = manager.getUnTrustedContentFiles(profile);
                                    if (!untrustedFiles.isEmpty()) {
                                        ContentUntrustedDialog untrustedDialog = new ContentUntrustedDialog(getContext(), untrustedFiles);
                                        untrustedDialog.setOnCancelCallback(preloaderDialog::closeOnUiThread);
                                        untrustedDialog.setOnConfirmCallback(() -> manager.finishInstallContent(profile, callback1));
                                        untrustedDialog.show();
                                    } else manager.finishInstallContent(profile, callback1);
                                });
                                dialog.setOnCancelCallback(preloaderDialog::closeOnUiThread);
                                dialog.show();
                            });

                        } else {
                            preloaderDialog.closeOnUiThread();
                            requireActivity().runOnUiThread(() -> {
                                ContentDialog.alert(getContext(), R.string.content_installed_success, null);
                                manager.syncContents();
                                // РўРµРїРµСЂСЊ profile.type РЅРёРєРѕРіРґР° РЅРµ Р±СѓРґРµС‚ null
                                boolean flashAfter = currentContentType == profile.type;
                                currentContentType = profile.type;
                                AppUtils.setSpinnerSelectionFromValue(sContentType, currentContentType.toString());
                                if (flashAfter) loadContentList();
                            });
                        }
                    }
                };
                Executors.newSingleThreadExecutor().execute(() -> {
                    manager.extraContentFile(data.getData(), callback);
                });
            } catch (Exception e) {
                preloaderDialog.closeOnUiThread();
                AppUtils.showToast(getContext(), R.string.unable_to_import_profile);
            }
        }
    }

    private void loadContentList() {
        List<ContentProfile> profiles = manager.getProfiles(currentContentType);
        if (profiles.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            ContentItemAdapter adapter = new ContentItemAdapter(profiles);
            recyclerView.setAdapter(adapter);
            
            // РџСЂРµРґР·Р°РіСЂСѓР¶Р°РµРј СЂР°Р·РјРµСЂС‹ РІСЃРµС… СѓРґР°Р»РµРЅРЅС‹С… С„Р°Р№Р»РѕРІ
            new Thread(() -> {
                boolean hasUpdates = false;
                for (ContentProfile profile : profiles) {
                    if (profile.remoteUrl != null) {
                        // РџСЂРѕРІРµСЂСЏРµРј РїРѕСЃС‚РѕСЏРЅРЅС‹Р№ РєСЌС€
                        if (!fileSizeCache.contains(profile.remoteUrl)) {
                            long fileSize = Downloader.getFileSize(profile.remoteUrl);
                            
                            // РЎРѕС…СЂР°РЅСЏРµРј РІ РїРѕСЃС‚РѕСЏРЅРЅС‹Р№ РєСЌС€
                            fileSizeCache.edit().putLong(profile.remoteUrl, fileSize).apply();
                            
                            // РћР±РЅРѕРІР»СЏРµРј РІСЂРµРјРµРЅРЅС‹Р№ РєСЌС€ Р°РґР°РїС‚РµСЂР°
                            adapter.fileSizeCache.put(profile.remoteUrl, fileSize);
                            hasUpdates = true;
                        } else {
                            // Р—Р°РіСЂСѓР¶Р°РµРј РёР· РїРѕСЃС‚РѕСЏРЅРЅРѕРіРѕ РєСЌС€Р°
                            long cachedSize = fileSizeCache.getLong(profile.remoteUrl, -1);
                            adapter.fileSizeCache.put(profile.remoteUrl, cachedSize);
                        }
                    }
                }
                
                // РћР±РЅРѕРІР»СЏРµРј UI РµСЃР»Рё Р±С‹Р»Рё РёР·РјРµРЅРµРЅРёСЏ
                if (hasUpdates && isAdded() && getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                    });
                }
            }).start();
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    private long getDirSize(File dir) {
        long size = 0;
        if (dir.isFile()) {
            return dir.length();
        }
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    size += file.length();
                } else {
                    size += getDirSize(file);
                }
            }
        }
        return size;
    }

    private class ContentItemAdapter extends RecyclerView.Adapter<ContentItemAdapter.ViewHolder> {
        private final List<ContentProfile> data;
        final Map<String, Long> fileSizeCache = new HashMap<>();

        private static class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView ivIcon;
            private final TextView tvVersionName;
            private final TextView tvVersionCode;
            private final TextView tvFileSize;
            private final ImageButton ibMenu;
            private final ImageButton ibDownload;
            private final ProgressBar progressBar;

            public ViewHolder(@NonNull View view) {
                super(view);

                ivIcon = view.findViewById(R.id.IVIcon);
                tvVersionName = view.findViewById(R.id.TVVersionName);
                tvVersionCode = view.findViewById(R.id.TVVersionCode);
                tvFileSize = view.findViewById(R.id.TVFileSize);
                ibMenu = view.findViewById(R.id.BTMenu);
                ibDownload = view.findViewById(R.id.BTDownload);
                progressBar = view.findViewById(R.id.Progress);
            }
        }

        public ContentItemAdapter(List<ContentProfile> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ContentItemAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.content_list_item, parent, false));
        }

        @Override
        public void onViewRecycled(@NonNull ViewHolder holder) {
            holder.ibMenu.setOnClickListener(null);
            super.onViewRecycled(holder);
        }

        @SuppressLint("StringFormatInvalid")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final ContentProfile profile = data.get(position);

            // РўРµРїРµСЂСЊ profile.type РЅРёРєРѕРіРґР° РЅРµ Р±СѓРґРµС‚ null
            int iconId = switch (profile.type) {
                case CONTENT_TYPE_WINE -> R.drawable.icon_wine;
                default -> R.drawable.icon_settings;
            };
            holder.ivIcon.setBackground(getContext().getDrawable(iconId));

            holder.tvVersionName.setText(getContext().getString(R.string.version) + ": " + profile.verName);
            holder.tvVersionCode.setText(getContext().getString(R.string.version_code) + ": " + profile.verCode);
            
            // РћС‚РѕР±СЂР°Р¶РµРЅРёРµ СЂР°Р·РјРµСЂР° С„Р°Р№Р»Р°
            if (profile.remoteUrl != null) {
                String cacheKey = profile.remoteUrl;
                
                // РЎРЅР°С‡Р°Р»Р° РїСЂРѕРІРµСЂСЏРµРј РїРѕСЃС‚РѕСЏРЅРЅС‹Р№ РєСЌС€
                if (ContentsFragment.this.fileSizeCache.contains(cacheKey)) {
                    long cachedSize = ContentsFragment.this.fileSizeCache.getLong(cacheKey, -1);
                    holder.tvFileSize.setVisibility(View.VISIBLE);
                    if (cachedSize > 0) {
                        String sizeText = formatFileSize(cachedSize);
                        holder.tvFileSize.setText(getContext().getString(R.string.file_size) + ": " + sizeText);
                    } else {
                        holder.tvFileSize.setText(getContext().getString(R.string.file_size) + ": " + getContext().getString(R.string.unknown_size));
                    }
                    // РўР°РєР¶Рµ РѕР±РЅРѕРІР»СЏРµРј РІСЂРµРјРµРЅРЅС‹Р№ РєСЌС€ Р°РґР°РїС‚РµСЂР°
                    fileSizeCache.put(cacheKey, cachedSize);
                } else if (fileSizeCache.containsKey(cacheKey)) {
                    // РџСЂРѕРІРµСЂСЏРµРј РІСЂРµРјРµРЅРЅС‹Р№ РєСЌС€ Р°РґР°РїС‚РµСЂР°
                    long cachedSize = fileSizeCache.get(cacheKey);
                    holder.tvFileSize.setVisibility(View.VISIBLE);
                    if (cachedSize > 0) {
                        String sizeText = formatFileSize(cachedSize);
                        holder.tvFileSize.setText(getContext().getString(R.string.file_size) + ": " + sizeText);
                    } else {
                        holder.tvFileSize.setText(getContext().getString(R.string.file_size) + ": " + getContext().getString(R.string.unknown_size));
                    }
                } else {
                    // Р Р°Р·РјРµСЂ РµС‰Рµ РЅРµ Р·Р°РіСЂСѓР¶РµРЅ
                    holder.tvFileSize.setVisibility(View.VISIBLE);
                    holder.tvFileSize.setText(getContext().getString(R.string.loading_size));
                    
                    // Р—Р°РіСЂСѓР¶Р°РµРј СЂР°Р·РјРµСЂ С„Р°Р№Р»Р° РІ С„РѕРЅРѕРІРѕРј РїРѕС‚РѕРєРµ
                    new Thread(() -> {
                        long fileSize = Downloader.getFileSize(profile.remoteUrl);
                        
                        // РЎРѕС…СЂР°РЅСЏРµРј РІ РїРѕСЃС‚РѕСЏРЅРЅС‹Р№ РєСЌС€
                        ContentsFragment.this.fileSizeCache.edit().putLong(cacheKey, fileSize).apply();
                        
                        // РўР°РєР¶Рµ РѕР±РЅРѕРІР»СЏРµРј РІСЂРµРјРµРЅРЅС‹Р№ РєСЌС€ Р°РґР°РїС‚РµСЂР°
                        fileSizeCache.put(cacheKey, fileSize);
                        
                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                // РџСЂРѕРІРµСЂСЏРµРј, С‡С‚Рѕ ViewHolder РІСЃРµ РµС‰Рµ РїРѕРєР°Р·С‹РІР°РµС‚ С‚РѕС‚ Р¶Рµ СЌР»РµРјРµРЅС‚
                                if (holder.getBindingAdapterPosition() == position) {
                                    if (fileSize > 0) {
                                        String sizeText = formatFileSize(fileSize);
                                        holder.tvFileSize.setText(getContext().getString(R.string.file_size) + ": " + sizeText);
                                    } else {
                                        holder.tvFileSize.setText(getContext().getString(R.string.file_size) + ": " + getContext().getString(R.string.unknown_size));
                                    }
                                }
                            });
                        }
                    }).start();
                }
            } else {
                // Р”Р»СЏ Р»РѕРєР°Р»СЊРЅС‹С… С„Р°Р№Р»РѕРІ РїРѕРєР°Р·С‹РІР°РµРј СЂР°Р·РјРµСЂ СѓСЃС‚Р°РЅРѕРІР»РµРЅРЅРѕРіРѕ РєРѕРЅС‚РµРЅС‚Р°
                File installDir = ContentsManager.getInstallDir(getContext(), profile);
                if (installDir.exists()) {
                    long dirSize = getDirSize(installDir);
                    String sizeText = formatFileSize(dirSize);
                    holder.tvFileSize.setVisibility(View.VISIBLE);
                    holder.tvFileSize.setText(getContext().getString(R.string.installed_size) + ": " + sizeText);
                } else {
                    holder.tvFileSize.setVisibility(View.GONE);
                }
            }
            
            holder.ibMenu.setVisibility(profile.remoteUrl == null ? View.VISIBLE : View.GONE);
            holder.ibMenu.setOnClickListener(v -> {
                PopupMenu selectionMenu = new PopupMenu(getContext(), holder.ibMenu);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    selectionMenu.setForceShowIcon(true);
                selectionMenu.inflate(R.menu.content_popup_menu);
                selectionMenu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.content_info) {
                        new ContentInfoDialog(getContext(), profile).show();
                    } else if (itemId == R.id.remove_content) {
                        ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_content, () -> {
                            // РўРµРїРµСЂСЊ profile.type РЅРёРєРѕРіРґР° РЅРµ Р±СѓРґРµС‚ null
                            if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE) {
                                ContainerManager containerManager = new ContainerManager(getContext());
                                for (Container container : containerManager.getContainers()) {
                                    if (container.getWineVersion().equals(ContentsManager.getEntryName(profile))) {
                                        ContentDialog.alert(getContext(), String.format(getString(R.string.unable_to_remove_content_since_container_using), container.getName()), null);
                                        return;
                                    }
                                }
                            }
                            manager.removeContent(profile);
                            loadContentList();
                        });
                    }
                    return true;
                });
                selectionMenu.show();
            });
            holder.ibDownload.setVisibility((profile.remoteUrl != null) && (holder.progressBar.getVisibility() == View.GONE) ? View.VISIBLE : View.GONE);
            holder.ibDownload.setOnClickListener(v -> {
                holder.ibDownload.setVisibility(View.GONE);
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.tvFileSize.setVisibility(View.VISIBLE);

                Intent intent = new Intent();
                intent.setData(Uri.parse(profile.remoteUrl));
                new Thread(() -> {
                    long timestamp = System.currentTimeMillis();
                    File output = new File(getContext().getCacheDir(), "temp_" + timestamp);
                    
                    boolean success = Downloader.downloadFile(profile.remoteUrl, output, (downloaded, total) -> {
                        // РћР±РЅРѕРІР»СЏРµРј РїСЂРѕРіСЂРµСЃСЃ СЃРєР°С‡РёРІР°РЅРёСЏ, РїСЂРѕРІРµСЂСЏСЏ С‡С‚Рѕ Fragment РІСЃРµ РµС‰Рµ РїСЂРёСЃРѕРµРґРёРЅРµРЅ
                        if (isAdded() && getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                if (total > 0) {
                                    // Р Р°Р·РјРµСЂ РёР·РІРµСЃС‚РµРЅ - РїРѕРєР°Р·С‹РІР°РµРј РїСЂРѕРіСЂРµСЃСЃ СЃ РїСЂРѕС†РµРЅС‚Р°РјРё
                                    String progressText = formatFileSize(downloaded) + " / " + formatFileSize(total);
                                    int percent = (int) ((downloaded * 100) / total);
                                    holder.tvFileSize.setText(getContext().getString(R.string.download_progress) + ": " + progressText + " (" + percent + "%)");
                                } else {
                                    // Р Р°Р·РјРµСЂ РЅРµРёР·РІРµСЃС‚РµРЅ - РїРѕРєР°Р·С‹РІР°РµРј С‚РѕР»СЊРєРѕ СЃРєР°С‡Р°РЅРЅС‹Р№ РѕР±СЉРµРј
                                    String progressText = formatFileSize(downloaded);
                                    holder.tvFileSize.setText(getContext().getString(R.string.download_progress) + ": " + progressText);
                                }
                            });
                        }
                    });
                    
                    if (success) {
                        intent.setData(Uri.parse(output.getAbsolutePath()));
                    }
                    
                    // РџСЂРѕРІРµСЂСЏРµРј С‡С‚Рѕ Fragment РІСЃРµ РµС‰Рµ РїСЂРёСЃРѕРµРґРёРЅРµРЅ РїРµСЂРµРґ РѕР±РЅРѕРІР»РµРЅРёРµРј UI
                    if (isAdded() && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            holder.progressBar.setVisibility(View.GONE);
                            holder.ibDownload.setVisibility(View.VISIBLE);
                            if (success) {
                                onActivityResult(MainActivity.OPEN_FILE_REQUEST_CODE, Activity.RESULT_OK, intent);
                            } else {
                                holder.tvFileSize.setText(getContext().getString(R.string.download_failed));
                            }
                        });
                    }
                }).start();
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
