package com.winlator.cmod;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.contents.ContentProfile;
import com.winlator.cmod.contents.ContentsManager;
import com.winlator.cmod.midi.MidiManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class InstalledComponentsFragment extends Fragment {
    private RecyclerView recyclerView;
    private View emptyText;
    private TextView tvTotalSize;
    private ContentsManager contentsManager;
    private AdrenotoolsManager adrenotoolsManager;
    private InstalledComponentAdapter adapter;
    private List<InstalledComponent> components = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        contentsManager = new ContentsManager(getContext());
        contentsManager.syncContents();
        adrenotoolsManager = new AdrenotoolsManager(getContext());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.installed_components);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup layout = (ViewGroup) inflater.inflate(R.layout.installed_components_fragment, container, false);

        emptyText = layout.findViewById(R.id.TVEmptyText);
        tvTotalSize = layout.findViewById(R.id.TVTotalSize);
        recyclerView = layout.findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));

        loadInstalledComponents();

        return layout;
    }

    @Override
    public void onResume() {
        super.onResume();
        contentsManager.syncContents();
        loadInstalledComponents();
    }

    @SuppressLint("StringFormatInvalid")
    private void loadInstalledComponents() {
        components.clear();

        loadContentComponents();
        loadAdrenotoolsComponents();
        loadSoundFontComponents();

        if (components.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            tvTotalSize.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            tvTotalSize.setVisibility(View.VISIBLE);

            adapter = new InstalledComponentAdapter(components, this::onDeleteClick);
            recyclerView.setAdapter(adapter);

            long totalSize = 0;
            for (InstalledComponent c : components) {
                if (!c.isSectionHeader()) {
                    totalSize += c.sizeBytes;
                }
            }
            tvTotalSize.setText(getString(R.string.total_size, formatFileSize(totalSize)));
        }
    }

    private void loadContentComponents() {
        for (ContentProfile.ContentType type : ContentProfile.ContentType.values()) {
            List<ContentProfile> profiles = contentsManager.getProfiles(type);
            if (profiles == null) continue;

            List<InstalledComponent> typeComponents = new ArrayList<>();
            for (ContentProfile profile : profiles) {
                if (profile.remoteUrl != null) continue;

                File installDir = ContentsManager.getInstallDir(getContext(), profile);
                long size = installDir.exists() ? getDirSize(installDir) : 0;

                int iconId = type == ContentProfile.ContentType.CONTENT_TYPE_WINE
                        ? R.drawable.icon_wine
                        : R.drawable.icon_settings;

                String version = getString(R.string.version) + ": " + profile.verName + " (" + profile.verCode + ")";
                typeComponents.add(new InstalledComponent(
                        profile.verName,
                        version,
                        type.toString(),
                        size,
                        iconId,
                        InstalledComponent.ComponentCategory.CONTENT,
                        profile
                ));
            }

            if (!typeComponents.isEmpty()) {
                components.add(new SectionHeader(type.toString()));
                components.addAll(typeComponents);
            }
        }
    }

    private void loadAdrenotoolsComponents() {
        ArrayList<String> drivers = adrenotoolsManager.enumarateInstalledDrivers();
        if (drivers.isEmpty()) return;

        components.add(new SectionHeader(getString(R.string.component_type_gpu_driver)));

        for (String driverId : drivers) {
            String name = adrenotoolsManager.getDriverName(driverId);
            String driverVersion = adrenotoolsManager.getDriverVersion(driverId);
            File driverPath = new File(getContext().getFilesDir(), "imagefs/contents/adrenotools/" + driverId);
            long size = driverPath.exists() ? getDirSize(driverPath) : 0;

            String version = "";
            if (driverVersion != null && !driverVersion.isEmpty()) {
                version = getString(R.string.version) + ": " + driverVersion;
            }

            components.add(new InstalledComponent(
                    name != null && !name.isEmpty() ? name : driverId,
                    version,
                    getString(R.string.component_type_gpu_driver),
                    size,
                    R.drawable.icon_snapdragon,
                    InstalledComponent.ComponentCategory.ADRENOTOOLS,
                    driverId
            ));
        }
    }

    private void loadSoundFontComponents() {
        List<File> sf2Files = MidiManager.getSF2Files(getContext());
        if (sf2Files == null || sf2Files.isEmpty()) return;

        List<InstalledComponent> sfComponents = new ArrayList<>();
        for (File file : sf2Files) {
            if (file.getName().equals(MidiManager.DEFAULT_SF2_FILE)) continue;

            long size = file.length();
            sfComponents.add(new InstalledComponent(
                    file.getName(),
                    formatFileSize(size),
                    getString(R.string.component_type_soundfont),
                    size,
                    R.drawable.icon_audio_settings,
                    InstalledComponent.ComponentCategory.SOUNDFONT,
                    file.getName()
            ));
        }

        if (!sfComponents.isEmpty()) {
            components.add(new SectionHeader(getString(R.string.component_type_soundfont)));
            components.addAll(sfComponents);
        }
    }

    private void onDeleteClick(InstalledComponent component) {
        ContentDialog.confirm(getContext(), getString(R.string.confirm_delete_component, component.name), () -> {
            if (component.category == InstalledComponent.ComponentCategory.CONTENT) {
                ContentProfile profile = (ContentProfile) component.identifier;
                if (profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE) {
                    ContainerManager containerManager = new ContainerManager(getContext());
                    for (Container container : containerManager.getContainers()) {
                        if (container.getWineVersion().equals(ContentsManager.getEntryName(profile))) {
                            ContentDialog.alert(getContext(), String.format(getString(R.string.unable_to_remove_content_since_container_using), container.getName()), null);
                            return;
                        }
                    }
                }
                contentsManager.removeContent(profile);
            } else if (component.category == InstalledComponent.ComponentCategory.ADRENOTOOLS) {
                String driverId = (String) component.identifier;
                adrenotoolsManager.removeDriver(driverId);
            } else if (component.category == InstalledComponent.ComponentCategory.SOUNDFONT) {
                String fileName = (String) component.identifier;
                MidiManager.removeSF2File(getContext(), fileName);
            }
            loadInstalledComponents();
        });
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
}
