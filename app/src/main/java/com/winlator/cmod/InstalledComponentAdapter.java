package com.winlator.cmod;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class InstalledComponentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_SECTION_HEADER = 0;
    private static final int TYPE_COMPONENT_ITEM = 1;

    private final List<InstalledComponent> items;
    private final OnDeleteClickListener deleteClickListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(InstalledComponent component);
    }

    public InstalledComponentAdapter(List<InstalledComponent> items, OnDeleteClickListener deleteClickListener) {
        this.items = items;
        this.deleteClickListener = deleteClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isSectionHeader() ? TYPE_SECTION_HEADER : TYPE_COMPONENT_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SECTION_HEADER) {
            View view = inflater.inflate(R.layout.installed_component_section_header, parent, false);
            return new SectionHeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.installed_component_list_item, parent, false);
            return new ComponentItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        InstalledComponent item = items.get(position);
        if (holder instanceof SectionHeaderViewHolder) {
            SectionHeaderViewHolder headerHolder = (SectionHeaderViewHolder) holder;
            headerHolder.tvSectionTitle.setText(item.type);
        } else if (holder instanceof ComponentItemViewHolder) {
            ComponentItemViewHolder itemHolder = (ComponentItemViewHolder) holder;
            itemHolder.tvComponentName.setText(item.name);
            if (item.version != null && !item.version.isEmpty()) {
                itemHolder.tvComponentVersion.setVisibility(View.VISIBLE);
                itemHolder.tvComponentVersion.setText(item.version);
            } else {
                itemHolder.tvComponentVersion.setVisibility(View.GONE);
            }
            itemHolder.tvComponentSize.setText(formatFileSize(item.sizeBytes));
            itemHolder.ivIcon.setBackgroundResource(item.iconResId);
            itemHolder.btDelete.setOnClickListener(v -> {
                if (deleteClickListener != null) {
                    deleteClickListener.onDeleteClick(item);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ComponentItemViewHolder) {
            ((ComponentItemViewHolder) holder).btDelete.setOnClickListener(null);
        }
        super.onViewRecycled(holder);
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

    static class SectionHeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvSectionTitle;

        SectionHeaderViewHolder(@NonNull View view) {
            super(view);
            tvSectionTitle = view.findViewById(R.id.TVSectionTitle);
        }
    }

    static class ComponentItemViewHolder extends RecyclerView.ViewHolder {
        ImageView ivIcon;
        TextView tvComponentName;
        TextView tvComponentVersion;
        TextView tvComponentSize;
        ImageButton btDelete;

        ComponentItemViewHolder(@NonNull View view) {
            super(view);
            ivIcon = view.findViewById(R.id.IVIcon);
            tvComponentName = view.findViewById(R.id.TVComponentName);
            tvComponentVersion = view.findViewById(R.id.TVComponentVersion);
            tvComponentSize = view.findViewById(R.id.TVComponentSize);
            btDelete = view.findViewById(R.id.BTDelete);
        }
    }
}
