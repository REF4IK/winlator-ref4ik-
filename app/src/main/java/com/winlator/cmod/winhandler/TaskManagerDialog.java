package com.winlator.cmod.winhandler;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridLayout;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.CPUStatus;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.widget.CPUListView;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.io.File;
import java.util.Locale;

public class TaskManagerDialog extends ContentDialog implements OnGetProcessInfoListener {
    private final XServerDisplayActivity activity;
    private final LayoutInflater inflater;
    private final boolean isDarkMode;
    private final Object lock = new Object();
    private final Handler updateHandler = new Handler(Looper.getMainLooper());
    private final Runnable periodicUpdate = new Runnable() {
        @Override
        public void run() {
            if (!isShowing()) return;
            update();
            updateHandler.postDelayed(this, 1000);
        }
    };
    private int batteryLevel = -1;
    private float batteryTemperature = -1.0f;
    private final BroadcastReceiver batteryReceiver;

    {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                batteryLevel = intent.getIntExtra("level", -1); // Уровень заряда в процентах
                int temperature = intent.getIntExtra("temperature", -1);
                batteryTemperature = temperature != -1 ? temperature / 10.0f : -1.0f; // Температура в °C
            }
        };
    }

    public TaskManagerDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.task_manager_dialog);
        this.activity = activity;
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        isDarkMode = preferences.getBoolean("dark_mode", false);
        setCancelable(false);
        // setTitle(R.string.task_manager); // Убрано для экономии места
        // setIcon(R.drawable.icon_task_manager); // Убрано для экономии места

        Button cancelButton = findViewById(R.id.BTCancel);
        cancelButton.setText(R.string.new_task);
        cancelButton.setOnClickListener((v) -> {
            dismiss();
            ContentDialog.prompt(activity, R.string.new_task, "taskmgr.exe", (command) -> activity.getWinHandler().exec(command));
        });

        setOnDismissListener((dialog) -> {
            updateHandler.removeCallbacks(periodicUpdate);
            activity.getWinHandler().setOnGetProcessInfoListener(null);
            if (batteryReceiver != null) {
                try {
                    activity.unregisterReceiver(batteryReceiver); // Отмена регистрации BroadcastReceiver
                } catch (IllegalArgumentException e) {
                    // Игнорируем, если receiver не был зарегистрирован
                }
            }
        });

        FileUtils.clear(getIconDir(activity));
        inflater = LayoutInflater.from(activity);
        applyThemeOverrides();
    }

    private void applyThemeOverrides() {
        if (!isDarkMode) return;

        if (getWindow() != null) {
            getWindow().setBackgroundDrawableResource(R.drawable.content_dialog_background_dark);
        }

        View root = findViewById(R.id.TaskManagerRoot);
        View statsPanel = findViewById(R.id.LLStatsPanel);
        View cpuCard = findViewById(R.id.LLCPUCard);
        View memoryCard = findViewById(R.id.LLMemoryCard);
        TextView emptyText = findViewById(R.id.TVEmptyText);
        TextView cpuIcon = findViewById(R.id.TVCPUIcon);
        TextView memoryIcon = findViewById(R.id.TVMemoryIcon);

        int dialogColor = ContextCompat.getColor(activity, R.color.content_dialog_background_dark);
        if (root != null) root.setBackgroundColor(dialogColor);
        if (statsPanel != null) statsPanel.setBackgroundColor(dialogColor);

        int darkCardColor = ContextCompat.getColor(activity, R.color.content_dialog_background_dark);
        int lightTextColor = ContextCompat.getColor(activity, R.color.white);

        if (cpuCard != null) cpuCard.setBackgroundResource(R.drawable.bordered_panel_surface_dark);
        if (memoryCard != null) memoryCard.setBackgroundResource(R.drawable.bordered_panel_surface_dark);
        if (emptyText != null) emptyText.setTextColor(lightTextColor);
        if (cpuIcon != null) cpuIcon.setTextColor(lightTextColor);
        if (memoryIcon != null) memoryIcon.setTextColor(lightTextColor);
    }

    private void update() {
        synchronized (lock) {
            activity.getWinHandler().listProcesses();

            final LinearLayout container = findViewById(R.id.LLProcessList);
            if (container.getChildCount() == 0) findViewById(R.id.TVEmptyText).setVisibility(View.VISIBLE);
        }

        updateCPUInfoView();
        updateMemoryInfoView();
    }

    private void showListItemMenu(final View anchorView, final ProcessInfo processInfo) {
        PopupMenu listItemMenu = new PopupMenu(activity, anchorView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

        listItemMenu.inflate(R.menu.process_popup_menu);
        com.winlator.cmod.widget.ProfilingSession session = com.winlator.cmod.widget.ProfilingSession.getInstance();
        android.view.MenuItem profileItem = listItemMenu.getMenu().findItem(R.id.process_profile);
        if (profileItem != null) {
            boolean activeForThis = session.isActive()
                    && processInfo.name != null
                    && processInfo.name.equals(session.getTargetProcessName());
            profileItem.setTitle(activeForThis ? R.string.profile_process_stop : R.string.profile_process_start);
            profileItem.setEnabled(!session.isActive() || activeForThis);
        }
        listItemMenu.setOnMenuItemClickListener((menuItem) -> {
            int itemId = menuItem.getItemId();
            final WinHandler winHandler = activity.getWinHandler();
            if (itemId == R.id.process_affinity) {
                showProcessorAffinityDialog(processInfo);
            }
            else if (itemId == R.id.bring_to_front) {
                winHandler.bringToFront(processInfo.name);
                dismiss();
            }
            else if (itemId == R.id.process_end) {
                ContentDialog.confirm(activity, R.string.do_you_want_to_end_this_process, () -> {
                    winHandler.killProcess(processInfo.name);
                });
            }
            else if (itemId == R.id.process_profile) {
                toggleProfiling(processInfo);
            }
            return true;
        });
        listItemMenu.show();
    }

    private void toggleProfiling(ProcessInfo processInfo) {
        com.winlator.cmod.widget.ProfilingSession session = com.winlator.cmod.widget.ProfilingSession.getInstance();
        if (session.isActive()) {
            com.winlator.cmod.widget.ProfilingSession.Result result = session.stop();
            if (result != null) showProfilingResultDialog(result);
        } else {
            // Bootstrap the (hidden) FrameRating so we can collect frame/sensor samples
            // without forcing the user to enable the FPS overlay.
            activity.enableProfilingHook();
            session.start(processInfo.name, processInfo.pid);
            android.widget.Toast.makeText(activity,
                    activity.getString(R.string.profile_started, processInfo.name),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void showProfilingResultDialog(com.winlator.cmod.widget.ProfilingSession.Result result) {
        ContentDialog dialog = new ContentDialog(activity, R.layout.profiling_result_dialog);
        dialog.setTitle(activity.getString(R.string.profile_result_title));
        com.winlator.cmod.widget.ProfilingChartView chart = dialog.findViewById(R.id.ProfilingChart);
        TextView summary = dialog.findViewById(R.id.TVProfilingSummary);
        if (chart != null) chart.setData(result);
        if (summary != null) {
            summary.setText(result.format(activity));
            if (isDarkMode) summary.setTextColor(ContextCompat.getColor(activity, R.color.white));
        }
        View cancelBtn = dialog.findViewById(R.id.BTCancel);
        if (cancelBtn != null) cancelBtn.setVisibility(View.GONE);
        dialog.show();
        // Make dialog wider — default ContentDialog size is too narrow for the chart.
        if (dialog.getWindow() != null) {
            android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int width = (int) (Math.min(dm.widthPixels, dm.heightPixels) * 1.4f);
            width = Math.min(width, (int) (Math.max(dm.widthPixels, dm.heightPixels) * 0.85f));
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showProcessorAffinityDialog(final ProcessInfo processInfo) {
        ContentDialog dialog = new ContentDialog(activity, R.layout.cpu_list_dialog);
        dialog.setTitle(processInfo.name);
        dialog.setIcon(R.drawable.icon_cpu);
        final CPUListView cpuListView = dialog.findViewById(R.id.CPUListView);
        cpuListView.setCheckedCPUList(processInfo.getCPUList());
        dialog.setOnConfirmCallback(() -> {
            WinHandler winHandler = activity.getWinHandler();
            winHandler.setProcessAffinity(processInfo.pid, ProcessHelper.getAffinityMask(cpuListView.getCheckedCPUList()));
            update();
        });
        dialog.show();
    }

    private int resolveThemeColor(int attr, int fallbackColor) {
        TypedValue outValue = new TypedValue();
        boolean resolved = activity.getTheme().resolveAttribute(attr, outValue, true);
        if (!resolved) return fallbackColor;
        if (outValue.resourceId != 0) {
            return ContextCompat.getColor(activity, outValue.resourceId);
        }
        return outValue.data;
    }

    public static File getIconDir(Context context) {
        File iconDir = new File(ImageFs.find(context).getRootDir(), "home/xuser/.local/share/icons/taskmgr");
        if (!iconDir.isDirectory()) iconDir.mkdirs();
        return iconDir;
    }

    @Override
    public void show() {
        updateHandler.removeCallbacks(periodicUpdate);
        update();
        activity.getWinHandler().setOnGetProcessInfoListener(this);

        // Регистрация BroadcastReceiver для батареи
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        activity.registerReceiver(batteryReceiver, filter);

        super.show();
        android.view.Window window = getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        View contentView = getContentView();
        if (contentView != null) {
            contentView.setPadding(dp(12), dp(12), dp(12), dp(12));
        }
        FrameLayout frameLayout = findViewById(R.id.FrameLayout);
        if (frameLayout != null) {
            frameLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        }
        updateHandler.postDelayed(periodicUpdate, 1000);
    }

    @Override
    public void onGetProcessInfo(int index, int numProcesses, ProcessInfo processInfo) {
        activity.runOnUiThread(() -> {
            synchronized (lock) {
                final LinearLayout container = findViewById(R.id.LLProcessList);
                setBottomBarText(activity.getString(R.string.processes)+": " + numProcesses);

                if (numProcesses == 0) {
                    container.removeAllViews();
                    findViewById(R.id.TVEmptyText).setVisibility(View.VISIBLE);
                    return;
                }

                findViewById(R.id.TVEmptyText).setVisibility(View.GONE);

                int childCount = container.getChildCount();
                View itemView = index < childCount ? container.getChildAt(index) : inflater.inflate(R.layout.process_info_list_item, container, false);
                int rowTextColor = isDarkMode
                        ? ContextCompat.getColor(activity, R.color.white)
                        : resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, 0xFFE6E0E9);
                TextView tvName = itemView.findViewById(R.id.TVName);
                TextView tvPid = itemView.findViewById(R.id.TVPID);
                TextView tvMemoryUsage = itemView.findViewById(R.id.TVMemoryUsage);
                tvName.setText(processInfo.name+(processInfo.wow64Process ? " *32" : ""));
                tvPid.setText(String.valueOf(processInfo.pid));
                tvMemoryUsage.setText(processInfo.getFormattedMemoryUsage());
                tvName.setTextColor(rowTextColor);
                tvPid.setTextColor(rowTextColor);
                tvMemoryUsage.setTextColor(rowTextColor);
                itemView.findViewById(R.id.BTMenu).setOnClickListener((v) -> showListItemMenu(v, processInfo));

                XServer xServer = activity.getXServer();
                Window window;

                try (XLock xlock = xServer.lock(XServer.Lockable.WINDOW_MANAGER)) {
                    window = xServer.windowManager.findWindowWithProcessId(processInfo.pid);
                }

                TextView tvStatus = itemView.findViewById(R.id.TVStatus);
                if (tvStatus != null) {
                    boolean hasWindow = window != null;
                    tvStatus.setText(hasWindow ? "Running" : "Bg");
                    tvStatus.setTextColor(hasWindow ? 0xFF4CAF50 : 0xFF888888);
                }

                ImageView ivIcon = itemView.findViewById(R.id.IVIcon);
                ivIcon.setImageResource(R.drawable.taskmgr_process);
                if (window != null) {
                    Bitmap icon = xServer.pixmapManager.getWindowIcon(window);
                    if (icon != null) ivIcon.setImageBitmap(icon);
                }

                if (index >= childCount) container.addView(itemView);

                if (index == numProcesses-1 && childCount > numProcesses) {
                    for (int i = childCount-1; i >= numProcesses; i--) container.removeViewAt(i);
                }
            }
        });
    }

    private void updateCPUInfoView() {
        GridLayout llCPUInfo = findViewById(R.id.LLCPUInfo);
        llCPUInfo.removeAllViews();
        int infoTextColor = isDarkMode
                ? ContextCompat.getColor(activity, R.color.white)
                : resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, 0xFFE6E0E9);
        int accentColor = resolveThemeColor(com.google.android.material.R.attr.colorPrimary, ContextCompat.getColor(activity, R.color.colorPrimary));
        short[] clockSpeeds = CPUStatus.getCurrentClockSpeeds();
        int totalClockSpeed = 0;
        short maxClockSpeed = 0;

        for (int i = 0; i < clockSpeeds.length; i++) {
            short clockSpeed = CPUStatus.getMaxClockSpeed(i);
            LinearLayout coreTile = new LinearLayout(activity);
            coreTile.setOrientation(LinearLayout.VERTICAL);
            coreTile.setMinimumHeight(dp(34));
            coreTile.setPadding(dp(5), dp(2), dp(5), dp(2));
            coreTile.setBackgroundResource(isDarkMode ? R.drawable.bordered_panel_surface_dark : R.drawable.bordered_panel_surface);

            TextView coreLabel = new TextView(activity);
            coreLabel.setIncludeFontPadding(false);
            coreLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
            coreLabel.setTextColor(infoTextColor);
            coreLabel.setSingleLine(true);
            coreLabel.setText("Core " + (i + 1));
            coreTile.addView(coreLabel);

            TextView coreValue = new TextView(activity);
            coreValue.setIncludeFontPadding(false);
            coreValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            coreValue.setTextColor(accentColor);
            coreValue.setSingleLine(true);
            coreValue.setText(String.format(Locale.ENGLISH, "%.1f GHz", clockSpeeds[i] / 1000.0f));
            coreTile.addView(coreValue);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(36);
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(2), dp(2), dp(2), dp(3));
            llCPUInfo.addView(coreTile, params);
            totalClockSpeed += clockSpeeds[i];
            maxClockSpeed = (short)Math.max(maxClockSpeed, clockSpeed);
        }

        int avgClockSpeed = clockSpeeds.length > 0 ? totalClockSpeed / clockSpeeds.length : 0;
        TextView tvCPUTitle = findViewById(R.id.TVCPUTitle);
        int cpuUsagePercent = maxClockSpeed > 0 ? Math.min(100, Math.round(((float)avgClockSpeed / maxClockSpeed) * 100.0f)) : 0;
        tvCPUTitle.setText("CPU Usage");
        TextView tvCPUPercent = findViewById(R.id.TVCPUPercent);
        if (tvCPUPercent != null) tvCPUPercent.setText(cpuUsagePercent + "%");
        ProgressBar pbCPUUsage = findViewById(R.id.PBCPUUsage);
        if (pbCPUUsage != null) pbCPUUsage.setProgress(cpuUsagePercent);
    }

    private final com.winlator.cmod.core.SensorReader sensorReader = new com.winlator.cmod.core.SensorReader();

    private String getCPUTemperature() {
        return sensorReader.getCpuTemperature();
    }

    private int dp(int value) {
        return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics());
    }

    private void updateMemoryInfoView() {
        LinearLayout llMemoryInfo = findViewById(R.id.LLMemoryInfo);
        if (llMemoryInfo != null) {
            llMemoryInfo.removeAllViews();
        }

        ActivityManager activityManager = (ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        long usedMem = memoryInfo.totalMem - memoryInfo.availMem;
        byte memUsagePercent = (byte)(((double)usedMem / memoryInfo.totalMem) * 100.0f);

        TextView tvMemoryTitle = findViewById(R.id.TVMemoryTitle);
        if (tvMemoryTitle != null) {
            tvMemoryTitle.setText(activity.getString(R.string.memory)+" ("+memUsagePercent+"%)");
        }
        TextView tvMemoryPercent = findViewById(R.id.TVMemoryPercent);
        if (tvMemoryPercent != null) tvMemoryPercent.setText(memUsagePercent + "%");
        ProgressBar pbMemoryUsage = findViewById(R.id.PBMemoryUsage);
        if (pbMemoryUsage != null) pbMemoryUsage.setProgress(memUsagePercent);

        if (llMemoryInfo != null) {
            int infoTextColor = isDarkMode
                    ? ContextCompat.getColor(activity, R.color.white)
                    : resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, 0xFFE6E0E9);
            // Добавление TextView для RAM
            TextView tvMemoryInfo = new TextView(activity);
            tvMemoryInfo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tvMemoryInfo.setTextColor(infoTextColor);
            tvMemoryInfo.setText(StringUtils.formatBytes(usedMem, false)+"/"+StringUtils.formatBytes(memoryInfo.totalMem));
            llMemoryInfo.addView(tvMemoryInfo);

            // Добавление TextView для температуры процессора
            TextView tvCPUTemp = new TextView(activity);
            tvCPUTemp.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tvCPUTemp.setTextColor(infoTextColor);
            tvCPUTemp.setText("CPU Temp: " + getCPUTemperature());
            llMemoryInfo.addView(tvCPUTemp);

            // Добавление TextView для температуры батареи
            TextView tvBatteryTemp = new TextView(activity);
            tvBatteryTemp.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tvBatteryTemp.setTextColor(infoTextColor);
            tvBatteryTemp.setText("Battery Temp: " + (batteryTemperature != -1.0f ? String.format(Locale.ENGLISH, "%.1f C", batteryTemperature) : "N/A"));
            llMemoryInfo.addView(tvBatteryTemp);

            // Добавление TextView для уровня заряда батареи
            TextView tvBatteryLevel = new TextView(activity);
            tvBatteryLevel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            tvBatteryLevel.setTextColor(infoTextColor);
            tvBatteryLevel.setText("Battery Level: " + (batteryLevel != -1 ? batteryLevel + "%" : "N/A"));
            llMemoryInfo.addView(tvBatteryLevel);
        }
    }
}


