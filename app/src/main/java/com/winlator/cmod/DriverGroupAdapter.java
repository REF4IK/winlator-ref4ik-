package com.winlator.cmod;

import android.animation.ValueAnimator;
import android.content.Context;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.core.DriverResolver;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import com.winlator.cmod.contents.AdrenotoolsManager;

public class DriverGroupAdapter extends RecyclerView.Adapter<DriverGroupAdapter.GroupViewHolder> {
    private static final String TAG = "DriverGroupAdapter";
    
    private final Context context;
    private final List<DriverGroup> driverGroups;
    private final DriverDownloadListener downloadListener;
    private final AdrenotoolsManager adrenotoolsManager;
    private final Set<String> installedDriverNames = new HashSet<>();
    private final Set<String> installedDriverUrls = new HashSet<>();
    
    public interface DriverDownloadListener {
        void onDownloadDriver(DriverResolver.DriverInfo driverInfo);
    }
    
    public static class DriverGroup {
        public String groupName;
        public List<DriverResolver.DriverInfo> drivers;
        public boolean isExpanded;
        
        public DriverGroup(String groupName, List<DriverResolver.DriverInfo> drivers) {
            this.groupName = groupName;
            this.drivers = drivers;
            this.isExpanded = false; // По умолчанию группа свернута
        }
    }
    
    public DriverGroupAdapter(Context context, DriverDownloadListener downloadListener) {
        this.context = context;
        this.downloadListener = downloadListener;
        this.driverGroups = new ArrayList<>();
        this.adrenotoolsManager = new AdrenotoolsManager(context);
    }

    private void refreshInstalledDrivers() {
        installedDriverNames.clear();
        installedDriverUrls.clear();
        try {
            for (String id : adrenotoolsManager.enumarateInstalledDrivers()) {
                installedDriverNames.add(id);
                String n = adrenotoolsManager.getDriverName(id);
                if (n != null && !n.isEmpty()) installedDriverNames.add(n);

                org.json.JSONObject storeInfo = adrenotoolsManager.getStoreInfo(id);
                if (storeInfo != null) {
                    String url = storeInfo.optString("downloadUrl");
                    if (url != null && !url.isEmpty()) installedDriverUrls.add(url);
                    String sName = storeInfo.optString("storeName");
                    if (sName != null && !sName.isEmpty()) installedDriverNames.add(sName);
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private boolean isAnyDriverMatching(String storeName) {
        String cleanStore = cleanString(storeName);
        if (cleanStore.isEmpty()) return false;

        String cleanStoreNoV = cleanStore.replace("v", "");

        for (String inst : installedDriverNames) {
            String cleanInst = cleanString(inst);
            if (cleanInst.isEmpty()) continue;

            if (cleanInst.equals(cleanStore) || cleanStore.contains(cleanInst) || cleanInst.contains(cleanStore)) {
                return true;
            }

            String cleanInstNoV = cleanInst.replace("v", "");
            if (cleanInstNoV.contains(cleanStoreNoV) || cleanStoreNoV.contains(cleanInstNoV)) {
                return true;
            }
        }
        return false;
    }

    private String cleanString(String s) {
        return s.toLowerCase(Locale.ENGLISH)
                .replace("gmem", "")
                .replace("sysmem", "")
                .replaceAll("[^a-z0-9]", "");
    }
    
    public void setDriverGroups(Map<String, List<DriverResolver.DriverInfo>> groupedDrivers) {
        refreshInstalledDrivers();
        driverGroups.clear();
        
        for (Map.Entry<String, List<DriverResolver.DriverInfo>> entry : groupedDrivers.entrySet()) {
            driverGroups.add(new DriverGroup(entry.getKey(), entry.getValue()));
        }
        
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.driver_group_item, parent, false);
        return new GroupViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        DriverGroup group = driverGroups.get(position);
        holder.bind(group);
    }
    
    @Override
    public int getItemCount() {
        return driverGroups.size();
    }
    
    public class GroupViewHolder extends RecyclerView.ViewHolder {
        private final View groupHeaderLayout;
        private final TextView groupNameText;
        private final TextView groupDescriptionText;
        private final View driversContainer;
        private final ImageView expandArrow;
        private final RecyclerView driversRecyclerView;
        private final DriverItemAdapter driverItemAdapter;
        
        public GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            groupHeaderLayout = itemView.findViewById(R.id.groupHeaderLayout);
            groupNameText = itemView.findViewById(R.id.groupNameText);
            groupDescriptionText = itemView.findViewById(R.id.groupDescriptionText);
            driversContainer = itemView.findViewById(R.id.driversContainer);
            expandArrow = itemView.findViewById(R.id.expandArrow);
            driversRecyclerView = itemView.findViewById(R.id.driversRecyclerView);
            
            driverItemAdapter = new DriverItemAdapter();
            driversRecyclerView.setLayoutManager(new LinearLayoutManager(context));
            driversRecyclerView.setNestedScrollingEnabled(true);

            driversRecyclerView.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
                @Override
                public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull android.view.MotionEvent e) {
                    ViewParent parent = rv.getParent();
                    while (parent != null && !(parent instanceof RecyclerView)) {
                        parent = parent.getParent();
                    }

                    if (parent != null) {
                        int action = e.getActionMasked();
                        if (action == android.view.MotionEvent.ACTION_DOWN || action == android.view.MotionEvent.ACTION_MOVE) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                            parent.requestDisallowInterceptTouchEvent(false);
                        }
                    }
                    return false;
                }
            });
            driversRecyclerView.setAdapter(driverItemAdapter);
        }
        
        public void bind(DriverGroup group) {
            groupNameText.setText(group.groupName);
            
            // Устанавливаем описание группы
            String description = getGroupDescription(group.groupName);
            if (description != null) {
                groupDescriptionText.setText(description);
                groupDescriptionText.setVisibility(View.VISIBLE);
            } else {
                groupDescriptionText.setVisibility(View.GONE);
            }
            
            // Устанавливаем драйверы
            driverItemAdapter.setDrivers(group.drivers);
            
            // Обновляем видимость контейнера драйверов и стрелки
            updateExpandedState(group.isExpanded);
            
            // Обработчик клика на заголовок группы
            groupHeaderLayout.setOnClickListener(v -> {
                group.isExpanded = !group.isExpanded;
                updateExpandedState(group.isExpanded);
            });
        }
        
        private void updateExpandedState(boolean isExpanded) {
            // Анимация поворота стрелки
            float targetRotation = isExpanded ? 180f : 0f;
            expandArrow.animate()
                    .rotation(targetRotation)
                    .setDuration(300)
                    .start();
            
            if (isExpanded) {
                // Плавное появление контейнера
                driversContainer.setVisibility(View.VISIBLE);
                driversContainer.setAlpha(0f);
                driversContainer.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
            } else {
                // Плавное скрытие контейнера
                driversContainer.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction(() -> driversContainer.setVisibility(View.GONE))
                        .start();
            }
        }
        
        private String getGroupDescription(String groupName) {
            switch (groupName) {
                case "Mr. Purple Turnip":
                    return context.getString(R.string.group_description_mr_purple);
                case "GameHub Adreno 8xx":
                    return context.getString(R.string.group_description_gamehub);
                case "KIMCHI Turnip":
                    return context.getString(R.string.group_description_kimchi);
                case "Weab-Chan Freedreno":
                    return context.getString(R.string.group_description_weabchan);
                default:
                    return null;
            }
        }
    }
    
    private class DriverItemAdapter extends RecyclerView.Adapter<DriverItemAdapter.DriverViewHolder> {
        private List<DriverResolver.DriverInfo> drivers = new ArrayList<>();
        
        public void setDrivers(List<DriverResolver.DriverInfo> drivers) {
            this.drivers = drivers;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public DriverViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.driver_item, parent, false);
            return new DriverViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull DriverViewHolder holder, int position) {
            holder.bind(drivers.get(position));
        }
        
        @Override
        public int getItemCount() {
            return drivers.size();
        }
        
        public class DriverViewHolder extends RecyclerView.ViewHolder {
            private final TextView driverNameText;
            private final TextView driverVersionText;
            private final TextView driverSizeText;
            private final TextView driverDateText;
            private final TextView driverDescriptionText;
            private final Button downloadButton;
            
            public DriverViewHolder(@NonNull View itemView) {
                super(itemView);
                driverNameText = itemView.findViewById(R.id.driverNameText);
                driverVersionText = itemView.findViewById(R.id.driverVersionText);
                driverSizeText = itemView.findViewById(R.id.driverSizeText);
                driverDateText = itemView.findViewById(R.id.driverDateText);
                driverDescriptionText = itemView.findViewById(R.id.driverDescriptionText);
                downloadButton = itemView.findViewById(R.id.downloadButton);
            }
            
            public void bind(DriverResolver.DriverInfo driverInfo) {
                driverNameText.setText(driverInfo.name);
                driverVersionText.setText(context.getString(R.string.version_format, driverInfo.version));
                
                // Форматируем размер файла
                if (driverInfo.size > 0) {
                    String formattedSize = Formatter.formatFileSize(context, driverInfo.size);
                    driverSizeText.setText(context.getString(R.string.size_format, formattedSize));
                    driverSizeText.setVisibility(View.VISIBLE);
                } else {
                    driverSizeText.setVisibility(View.GONE);
                }
                
                // Форматируем дату публикации
                if (driverInfo.publishedAt != null && !driverInfo.publishedAt.isEmpty()) {
                    String formattedDate = formatDate(driverInfo.publishedAt);
                    if (formattedDate != null) {
                        driverDateText.setText(context.getString(R.string.published_format, formattedDate));
                        driverDateText.setVisibility(View.VISIBLE);
                    } else {
                        driverDateText.setVisibility(View.GONE);
                    }
                } else {
                    driverDateText.setVisibility(View.GONE);
                }
                
                // Описание скрыто для экономии места
                driverDescriptionText.setVisibility(View.GONE);
                
                boolean isInstalled = installedDriverUrls.contains(driverInfo.downloadUrl) || 
                                      isAnyDriverMatching(driverInfo.name);

                if (isInstalled) {
                    downloadButton.setText(R.string.installed);
                    downloadButton.setEnabled(false);
                    downloadButton.setOnClickListener(null);
                } else {
                    downloadButton.setText(R.string.download);
                    downloadButton.setEnabled(true);
                    downloadButton.setOnClickListener(v -> {
                        if (downloadListener != null) {
                            downloadListener.onDownloadDriver(driverInfo);
                        }
                    });
                }
            }
            
            private String formatDate(String isoDate) {
                try {
                    SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    SimpleDateFormat outputFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
                    Date date = inputFormat.parse(isoDate);
                    return outputFormat.format(date);
                } catch (ParseException e) {
                    return null;
                }
            }
        }
    }
}