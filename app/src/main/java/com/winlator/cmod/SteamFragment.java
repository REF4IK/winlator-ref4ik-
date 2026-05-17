package com.winlator.cmod;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.container.Container;
import com.winlator.cmod.container.ContainerManager;
import com.winlator.cmod.contents.Downloader;
import com.winlator.cmod.steam.SteamShortcutHelper;

import java.io.File;
import java.util.ArrayList;

public class SteamFragment extends Fragment {
    private Spinner containerSpinner;
    private Button btInstallSteam;
    private Button btRunSteam;
    private Button btImportSteamGames;
    private ProgressBar progressBar;
    private TextView statusView;
    private ArrayList<Container> containers = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.steam_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.steam);
        }

        containerSpinner = view.findViewById(R.id.SpinnerSteamContainer);
        btInstallSteam = view.findViewById(R.id.BTInstallSteam);
        btRunSteam = view.findViewById(R.id.BTRunSteam);
        btImportSteamGames = view.findViewById(R.id.BTImportSteamGames);
        progressBar = view.findViewById(R.id.ProgressSteam);
        statusView = view.findViewById(R.id.TVSteamStatus);

        loadContainers();

        containerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view1, int position, long id) {
                refreshStatus();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                refreshStatus();
            }
        });

        btInstallSteam.setOnClickListener(v -> installOrUpdateSteam());
        btRunSteam.setOnClickListener(v -> runSteamClient());
        btImportSteamGames.setOnClickListener(v -> importSteamGames());
    }

    private void loadContainers() {
        ContainerManager containerManager = new ContainerManager(requireContext());
        containers = containerManager.getContainers();

        ArrayList<String> names = new ArrayList<>();
        for (Container container : containers) names.add(container.getName());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        containerSpinner.setAdapter(adapter);

        boolean hasContainers = !containers.isEmpty();
        containerSpinner.setEnabled(hasContainers);
        btInstallSteam.setEnabled(hasContainers);
        btRunSteam.setEnabled(hasContainers);
        btImportSteamGames.setEnabled(hasContainers);

        if (!hasContainers) {
            statusView.setText(R.string.steam_no_containers);
        } else {
            refreshStatus();
        }
    }

    @Nullable
    private Container getSelectedContainer() {
        int position = containerSpinner.getSelectedItemPosition();
        if (position < 0 || position >= containers.size()) return null;
        return containers.get(position);
    }

    private void refreshStatus() {
        Container container = getSelectedContainer();
        if (container == null) {
            statusView.setText(R.string.steam_no_containers);
            return;
        }

        if (SteamShortcutHelper.isSteamInstalled(container)) {
            statusView.setText(getString(R.string.steam_status_installed, container.getName()));
            btRunSteam.setEnabled(true);
            btImportSteamGames.setEnabled(true);
        } else {
            statusView.setText(getString(R.string.steam_status_not_installed, container.getName()));
            btRunSteam.setEnabled(false);
            btImportSteamGames.setEnabled(false);
        }
    }

    private void setBusy(boolean busy) {
        progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        containerSpinner.setEnabled(!busy && !containers.isEmpty());
        btInstallSteam.setEnabled(!busy && !containers.isEmpty());
        Container selected = getSelectedContainer();
        boolean installed = selected != null && SteamShortcutHelper.isSteamInstalled(selected);
        btRunSteam.setEnabled(!busy && installed);
        btImportSteamGames.setEnabled(!busy && installed);
    }

    private void installOrUpdateSteam() {
        Container container = getSelectedContainer();
        if (container == null) {
            Toast.makeText(getContext(), R.string.steam_no_containers, Toast.LENGTH_SHORT).show();
            return;
        }

        if (SteamShortcutHelper.isSteamInstalled(container)) {
            runSteamClient();
            return;
        }

        setBusy(true);
        statusView.setText(R.string.steam_downloading_installer);

        new Thread(() -> {
            File installer = SteamShortcutHelper.getSteamInstallerTarget(container);
            boolean downloaded = installer.isFile() || Downloader.downloadFile(SteamShortcutHelper.STEAM_INSTALLER_URL, installer);

            requireActivity().runOnUiThread(() -> {
                setBusy(false);
                if (!downloaded) {
                    statusView.setText(R.string.steam_download_failed);
                    Toast.makeText(getContext(), R.string.steam_download_failed, Toast.LENGTH_LONG).show();
                    return;
                }

                File shortcutFile = SteamShortcutHelper.createInstallerShortcut(container, installer);
                launchShortcut(container, shortcutFile);
                statusView.setText(getString(R.string.steam_install_launched, container.getName()));
                Toast.makeText(getContext(), R.string.steam_install_launched_short, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void runSteamClient() {
        Container container = getSelectedContainer();
        if (container == null) {
            Toast.makeText(getContext(), R.string.steam_no_containers, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!SteamShortcutHelper.isSteamInstalled(container)) {
            Toast.makeText(getContext(), R.string.steam_client_not_found, Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }

        File shortcutFile = SteamShortcutHelper.ensureSteamClientShortcut(container);
        launchShortcut(container, shortcutFile);
    }

    private void importSteamGames() {
        Container container = getSelectedContainer();
        if (container == null) {
            Toast.makeText(getContext(), R.string.steam_no_containers, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!SteamShortcutHelper.isSteamInstalled(container)) {
            Toast.makeText(getContext(), R.string.steam_client_not_found, Toast.LENGTH_LONG).show();
            refreshStatus();
            return;
        }

        setBusy(true);
        statusView.setText(R.string.steam_importing_games);

        new Thread(() -> {
            SteamShortcutHelper.ImportResult result = SteamShortcutHelper.importInstalledGames(requireContext(), container);
            requireActivity().runOnUiThread(() -> {
                setBusy(false);
                if (result.found == 0) {
                    statusView.setText(R.string.steam_no_games_found);
                    Toast.makeText(getContext(), R.string.steam_no_games_found, Toast.LENGTH_LONG).show();
                } else {
                    statusView.setText(getString(R.string.steam_import_complete, result.imported, container.getName()));
                    Toast.makeText(getContext(), getString(R.string.steam_import_complete, result.imported, container.getName()), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void launchShortcut(@NonNull Container container, @NonNull File shortcutFile) {
        Activity activity = getActivity();
        if (activity == null) return;

        if (!XrActivity.isEnabled(getContext())) {
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            intent.putExtra("shortcut_path", shortcutFile.getPath());
            activity.startActivity(intent);
        } else {
            XrActivity.openIntent(activity, container.id, shortcutFile.getPath());
        }
    }
}
