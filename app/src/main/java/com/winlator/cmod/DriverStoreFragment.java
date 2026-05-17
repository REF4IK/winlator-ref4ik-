package com.winlator.cmod;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import com.winlator.cmod.contentdialog.CustomRepositoryDialog;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.DriverResolver;
import com.winlator.cmod.core.GPUInformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DriverStoreFragment extends Fragment {
    private static final String TAG = "DriverStoreFragment";
    
    private RecyclerView recyclerView;
    private MaterialButton btnSettings;
    private ProgressBar loadingIndicator;
    private TextView emptyStateText;
    private TextView gpuInfoText;
    private Button refreshButton;
    
    private DriverResolver driverResolver;
    private AdrenotoolsManager adrenotoolsManager;
    private DriverGroupAdapter driverGroupAdapter;
    
    // Карта рекомендаций драйверов по модели GPU
    private static final Map<String, String> GPU_DRIVER_RECOMMENDATIONS = new HashMap<String, String>() {{
        put("Adreno 610", "KIMCHI Turnip");
        put("Adreno 620", "KIMCHI Turnip");
        put("Adreno 630", "Mr. Purple Turnip");
        put("Adreno 640", "Mr. Purple Turnip");
        put("Adreno 650", "Mr. Purple Turnip");
        put("Adreno 660", "Mr. Purple Turnip");
        put("Adreno 730", "GameHub Adreno 8xx");
        put("Adreno 740", "GameHub Adreno 8xx");
        put("Adreno 750", "GameHub Adreno 8xx");
    }};
    
    /**
     * Извлекает модель GPU из строки рендерера
     * @param renderer строка рендерера от GPUInformation.getRenderer()
     * @return модель GPU (например "Adreno 640") или null если не найдена
     */
    private String extractGpuModel(String renderer) {
        if (renderer == null || renderer.isEmpty()) {
            return null;
        }
        
        // Приводим к нижнему регистру для поиска
        String lowerRenderer = renderer.toLowerCase(Locale.ENGLISH);
        
        // Ищем Adreno GPU с использованием regex
        if (lowerRenderer.contains("adreno")) {
            // Извлекаем номер модели Adreno (например 610, 640, 730)
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("adreno[^0-9]*([0-9]{3})");
            java.util.regex.Matcher matcher = pattern.matcher(lowerRenderer);
            if (matcher.find()) {
                return "Adreno " + matcher.group(1);
            }
        }
        
        // Возвращаем оригинальную строку если не смогли извлечь модель
        return renderer;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        driverResolver = new DriverResolver(requireContext());
        adrenotoolsManager = new AdrenotoolsManager(requireContext());
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_driver_store, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Настройка ActionBar
        if (getActivity() instanceof AppCompatActivity) {
            ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.driver_store_title);
        }
        
        initViews(view);
        setupRecyclerView();
        updateGpuInfo();
        hideRecommendedDriver();
        
        // Загружаем драйверы при первом запуске
        loadDrivers();
    }
    
    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewDrivers);
        btnSettings = view.findViewById(R.id.btnSettings);
        loadingIndicator = view.findViewById(R.id.loadingIndicator);
        emptyStateText = view.findViewById(R.id.emptyStateText);
        gpuInfoText = view.findViewById(R.id.gpuInfoText);
        refreshButton = view.findViewById(R.id.refreshButton);
        
        refreshButton.setOnClickListener(v -> loadDrivers());
        btnSettings.setOnClickListener(v -> showRepositoryManagementDialog());
    }
    
    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        driverGroupAdapter = new DriverGroupAdapter(requireContext(), new DriverGroupAdapter.DriverDownloadListener() {
            @Override
            public void onDownloadDriver(DriverResolver.DriverInfo driverInfo) {
                downloadAndInstallDriver(driverInfo);
            }
        });
        recyclerView.setAdapter(driverGroupAdapter);
    }
    

    
    private void updateGpuInfo() {
        String gpuModel = extractGpuModel(GPUInformation.getRenderer());
        if (gpuModel != null && !gpuModel.isEmpty()) {
            gpuInfoText.setText(getString(R.string.gpu_model_format, gpuModel));
            gpuInfoText.setVisibility(View.VISIBLE);
        } else {
            gpuInfoText.setVisibility(View.GONE);
        }
    }
    
    private void hideRecommendedDriver() {
        View recommendedDriverText = requireView().findViewById(R.id.recommendedDriverText);
        if (recommendedDriverText != null) {
            recommendedDriverText.setVisibility(View.GONE);
        }
    }
    
    private void loadDrivers() {
        showLoading(true);
        
        driverResolver.searchDrivers(new DriverResolver.DriverSearchCallback() {
            @Override
            public void onDriversFound(List<DriverResolver.DriverInfo> drivers) {
                requireActivity().runOnUiThread(() -> {
                    showLoading(false);
                    
                    if (drivers.isEmpty()) {
                        showEmptyState(true);
                    } else {
                        showEmptyState(false);
                        groupAndDisplayDrivers(drivers);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    showLoading(false);
                    showEmptyState(true);
                    emptyStateText.setText(getString(R.string.driver_load_error, error));
                    Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Ошибка загрузки драйверов: " + error);
                });
            }
        });
    }
    
    private void groupAndDisplayDrivers(List<DriverResolver.DriverInfo> drivers) {
        // Группируем драйверы по репозиториям
        Map<String, List<DriverResolver.DriverInfo>> groupedDrivers = new HashMap<>();
        
        for (DriverResolver.DriverInfo driver : drivers) {
            String repoName = driver.repoName;
            if (!groupedDrivers.containsKey(repoName)) {
                groupedDrivers.put(repoName, new ArrayList<>());
            }
            groupedDrivers.get(repoName).add(driver);
        }
        
        driverGroupAdapter.setDriverGroups(groupedDrivers);
    }
    
    private void downloadAndInstallDriver(DriverResolver.DriverInfo driverInfo) {
        ProgressDialog progressDialog = new ProgressDialog(getContext());
        progressDialog.setTitle(R.string.downloading_driver);
        progressDialog.setMessage(getString(R.string.downloading_driver_message, driverInfo.name));
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(100);
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        driverResolver.downloadDriver(driverInfo, new DriverResolver.DriverDownloadCallback() {
            @Override
            public void onProgress(int progress) {
                requireActivity().runOnUiThread(() -> {
                    progressDialog.setProgress(progress);
                });
            }
            
            @Override
            public void onComplete(Uri driverUri) {
                requireActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    installDriver(driverUri, driverInfo.name);
                });
            }
            
            @Override
            public void onError(String error) {
                requireActivity().runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(getContext(), 
                        getString(R.string.download_error, error), 
                        Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Ошибка загрузки драйвера: " + error);
                });
            }
        });
    }
    
    private void installDriver(Uri driverUri, String driverName) {
        try {
            String installedDriverId = adrenotoolsManager.installDriver(driverUri);
            
            if (!installedDriverId.isEmpty()) {
                Toast.makeText(getContext(), 
                    getString(R.string.driver_installed_successfully, driverName), 
                    Toast.LENGTH_LONG).show();
                
                // Возвращаемся к фрагменту AdrenoTools
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                Toast.makeText(getContext(), 
                    R.string.driver_installation_failed, 
                    Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка установки драйвера", e);
            Toast.makeText(getContext(), 
                getString(R.string.installation_error, e.getMessage()), 
                Toast.LENGTH_LONG).show();
        }
    }
    
    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showEmptyState(boolean show) {
        emptyStateText.setVisibility(show ? View.VISIBLE : View.GONE);
        refreshButton.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showRepositoryManagementDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.manage_repositories);
        builder.setIcon(android.R.drawable.ic_menu_manage);
        
        String[] options = {
            getString(R.string.add_custom_repository),
            getString(R.string.view_custom_repositories)
        };
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showAddRepositoryDialog();
            } else if (which == 1) {
                showCustomRepositoriesList();
            }
        });
        
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
    
    private void showAddRepositoryDialog() {
        CustomRepositoryDialog dialog = new CustomRepositoryDialog(requireContext(), 
            (repoUrl, repoName) -> {
                driverResolver.addCustomRepository(repoUrl, repoName);
                Toast.makeText(getContext(), 
                    getString(R.string.repository_added_successfully, repoName), 
                    Toast.LENGTH_SHORT).show();
                
                // Перезагружаем драйверы
                loadDrivers();
            });
        dialog.show();
    }
    
    private void showCustomRepositoriesList() {
        java.util.List<String> customRepos = driverResolver.getCustomRepositories();
        
        if (customRepos.isEmpty()) {
            Toast.makeText(getContext(), R.string.no_custom_repositories, Toast.LENGTH_SHORT).show();
            return;
        }
        
        String[] repoNames = new String[customRepos.size()];
        for (int i = 0; i < customRepos.size(); i++) {
            String repo = customRepos.get(i);
            String name = driverResolver.getCustomRepositoryName(repo);
            repoNames[i] = (name != null && !name.isEmpty()) ? name : repo;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(R.string.custom_repositories);
        builder.setIcon(android.R.drawable.ic_menu_view);
        
        builder.setItems(repoNames, (dialog, which) -> {
            String selectedRepo = customRepos.get(which);
            showRepositoryOptionsDialog(selectedRepo, repoNames[which]);
        });
        
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
    
    private void showRepositoryOptionsDialog(String repoUrl, String repoName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(repoName);
        builder.setMessage(getString(R.string.repository_url_format, repoUrl));
        
        builder.setPositiveButton(R.string.confirm_deletion, (dialog, which) -> {
            new AlertDialog.Builder(requireContext())
                .setTitle(R.string.confirm_deletion)
                .setMessage(getString(R.string.confirm_delete_repository, repoName))
                .setPositiveButton(android.R.string.yes, (d, w) -> {
                    driverResolver.removeCustomRepository(repoUrl);
                    Toast.makeText(getContext(), 
                        getString(R.string.repository_deleted, repoName), 
                        Toast.LENGTH_SHORT).show();
                    loadDrivers();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
        });
        
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (driverResolver != null) {
            driverResolver.shutdown();
        }
    }
}
