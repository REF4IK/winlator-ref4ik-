package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.winlator.cmod.DriverGroupAdapter;
import com.winlator.cmod.R;
import com.winlator.cmod.contents.AdrenotoolsManager;
import com.winlator.cmod.core.DriverResolver;
import com.winlator.cmod.core.AppUtils;
import com.winlator.cmod.core.UnitUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DriverDownloadDialog extends ContentDialog {

    private static final String TAG = "DriverDownloadDialog";
    
    public interface OnDriverDownloadedListener {
        void onDriverDownloaded(String installedDriverId);
    }

    private OnDriverDownloadedListener downloadListener;
    private DriverResolver driverResolver;
    private AdrenotoolsManager adrenotoolsManager;
    private ProgressBar loadingIndicator;
    private TextView emptyStateText;
    private RecyclerView recyclerView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DriverDownloadDialog(Context context, OnDriverDownloadedListener listener) {
        super(context, R.layout.driver_download_dialog);
        this.downloadListener = listener;
        this.driverResolver = new DriverResolver(context);
        this.adrenotoolsManager = new AdrenotoolsManager(context);
        initializeDialog(context);
    }

    private void initializeDialog(Context context) {
        setIcon(R.drawable.icon_settings);
        setTitle(context.getString(R.string.download_graphics_driver));

        recyclerView = findViewById(R.id.driversRecyclerView);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        emptyStateText = findViewById(R.id.emptyStateText);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        View contentFrame = findViewById(R.id.FrameLayout);
        if (contentFrame != null) {
            ViewGroup.LayoutParams lp = contentFrame.getLayoutParams();
            if (lp != null) lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            ViewParent parent = contentFrame.getParent();
            if (parent instanceof View) {
                ViewGroup.LayoutParams parentLp = ((View) parent).getLayoutParams();
                if (parentLp != null) parentLp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            }
        }

        // Загружаем драйверы из GitHub
        loadDriversFromGitHub(context);

        setOnConfirmCallback(() -> {
            // Закрыть диалог
            if (driverResolver != null) {
                driverResolver.shutdown();
            }
        });
    }

    @Override
    public void show() {
        super.show();
        Window window = getWindow();
        if (window != null) {
            int preferred = AppUtils.getPreferredDialogWidth(getContext()); // ~80% портрет/50% альбом
            int minDp360 = (int) UnitUtils.dpToPx(360); // чтобы не становился слишком узким
            int width = Math.max(preferred, minDp360);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
    
    private void loadDriversFromGitHub(Context context) {
        showLoading(true);
        
        Log.d(TAG, "Начинаем загрузку списка драйверов из GitHub...");
        
        driverResolver.searchDrivers(new DriverResolver.DriverSearchCallback() {
            @Override
            public void onDriversFound(List<DriverResolver.DriverInfo> drivers) {
                Log.d(TAG, "Получено драйверов: " + drivers.size());
                
                // Обновляем UI в главном потоке
                mainHandler.post(() -> {
                    showLoading(false);
                    
                    if (drivers.isEmpty()) {
                        Log.w(TAG, "Список драйверов пуст");
                        showEmptyState(true, context.getString(R.string.no_driver_versions_available));
                    } else {
                        showEmptyState(false, null);
                        displayDrivers(context, drivers);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Ошибка загрузки списка драйверов: " + error);
                
                // Обновляем UI в главном потоке
                mainHandler.post(() -> {
                    showLoading(false);
                    showEmptyState(true, context.getString(R.string.download_error, error));
                    Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void displayDrivers(Context context, List<DriverResolver.DriverInfo> resolverDrivers) {
        Log.d(TAG, "Отображение драйверов: " + resolverDrivers.size());
        
        // Группируем драйверы по репозиториям
        Map<String, List<DriverResolver.DriverInfo>> groupedDrivers = new HashMap<>();
        
        for (DriverResolver.DriverInfo driver : resolverDrivers) {
            Log.d(TAG, "Драйвер: " + driver.name + " v" + driver.version + " из " + driver.repoName);
            
            String repoName = driver.repoName;
            if (!groupedDrivers.containsKey(repoName)) {
                groupedDrivers.put(repoName, new ArrayList<>());
            }
            groupedDrivers.get(repoName).add(driver);
        }
        
        Log.d(TAG, "Создание группового адаптера с " + groupedDrivers.size() + " группами");
        
        DriverGroupAdapter adapter = new DriverGroupAdapter(context, driverInfo -> {
            // Обработка нажатия на кнопку скачивания
            downloadAndInstallDriver(context, driverInfo);
        });
        
        adapter.setDriverGroups(groupedDrivers);
        recyclerView.setAdapter(adapter);
        Log.d(TAG, "Адаптер установлен в RecyclerView");
    }
    
    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showEmptyState(boolean show, String message) {
        emptyStateText.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        
        if (show && message != null) {
            emptyStateText.setText(message);
        }
    }

    private void downloadAndInstallDriver(Context context, DriverResolver.DriverInfo driverInfo) {
        Log.d(TAG, "Начинаем скачивание драйвера: " + driverInfo.name);
        
        // Создаем современный Material Design диалог прогресса
        View progressView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null);
        TextView downloadMessage = progressView.findViewById(R.id.downloadMessage);
        com.google.android.material.progressindicator.LinearProgressIndicator progressBar = 
            progressView.findViewById(R.id.downloadProgressBar);
        TextView progressText = progressView.findViewById(R.id.downloadProgressText);
        
        downloadMessage.setText(context.getString(R.string.downloading_driver_message, driverInfo.name));
        
        AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
            .setView(progressView)
            .setCancelable(false)
            .create();
        
        progressDialog.show();
        Window window = progressDialog.getWindow();
        if (window != null) {
            int preferred = AppUtils.getPreferredDialogWidth(context);
            int minDp360 = (int) UnitUtils.dpToPx(360);
            int width = Math.max(preferred, minDp360);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        
        // Запускаем скачивание в фоновом потоке
        driverResolver.downloadDriver(driverInfo, new DriverResolver.DriverDownloadCallback() {
            @Override
            public void onProgress(int progress) {
                // Обновляем прогресс в главном потоке
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressBar.setProgress(progress);
                        progressText.setText(progress + "%");
                    }
                });
            }
            
            @Override
            public void onComplete(Uri driverUri) {
                Log.d(TAG, "Скачивание завершено: " + driverUri);
                // Закрываем прогресс и устанавливаем драйвер в главном потоке
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    installDriver(context, driverUri, driverInfo);
                });
            }
            
            @Override
            public void onError(String error) {
                Log.e(TAG, "Ошибка загрузки драйвера: " + error);
                // Показываем ошибку в главном потоке
                mainHandler.post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(context, 
                        context.getString(R.string.download_error, error), 
                        Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void installDriver(Context context, Uri driverUri, DriverResolver.DriverInfo driverInfo) {
        String driverName = driverInfo.name;
        Log.d(TAG, "Начинаем установку драйвера: " + driverName);
        
        try {
            if (driverUri == null) {
                Log.e(TAG, "Driver URI is null");
                mainHandler.post(() -> {
                    Toast.makeText(context, R.string.driver_installation_failed, Toast.LENGTH_LONG).show();
                });
                return;
            }
            
            String installedDriverId = adrenotoolsManager.installDriver(driverUri);
            
            if (!installedDriverId.isEmpty()) {
                Log.d(TAG, "Драйвер успешно установлен: " + installedDriverId);
                adrenotoolsManager.writeStoreInfo(installedDriverId, driverInfo.name, driverInfo.version, driverInfo.downloadUrl);
                
                mainHandler.post(() -> {
                    Toast.makeText(context, 
                        context.getString(R.string.driver_installed_successfully, driverName), 
                        Toast.LENGTH_LONG).show();
                    
                    // Закрываем диалог и вызываем callback для обновления списка
                    dismiss();
                    if (downloadListener != null) {
                        downloadListener.onDriverDownloaded(installedDriverId);
                    }
                });
            } else {
                Log.w(TAG, "Установка драйвера вернула пустой ID");
                
                mainHandler.post(() -> {
                    Toast.makeText(context, 
                        R.string.driver_installation_failed, 
                        Toast.LENGTH_LONG).show();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Ошибка установки драйвера: " + e.getMessage(), e);
            
            mainHandler.post(() -> {
                Toast.makeText(context, 
                    context.getString(R.string.installation_error, e.getMessage()), 
                    Toast.LENGTH_LONG).show();
            });
        }
    }
}
