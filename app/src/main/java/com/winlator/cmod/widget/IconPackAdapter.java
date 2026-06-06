package com.winlator.cmod.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.inputcontrols.IconPackManager.IconPack;

import java.util.List;

/**
 * Adapter for displaying icon packs in a list.
 * Each item shows: category, pack name, icon count, and preview.
 */
public class IconPackAdapter extends BaseAdapter {
    private final Context context;
    private final List<IconPack> packs;

    public IconPackAdapter(Context context, List<IconPack> packs) {
        this.context = context;
        this.packs = packs;
    }

    @Override
    public int getCount() {
        return packs.size();
    }

    @Override
    public Object getItem(int position) {
        return packs.get(position);
    }

    @Override
    public long getItemId(int position) {
        return packs.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.icon_pack_list_item, parent, false);
        }

        IconPack pack = packs.get(position);
        TextView tvCategory = view.findViewById(R.id.TVCategory);
        ImageView ivPreview = view.findViewById(R.id.IVPreview);
        TextView tvPackName = view.findViewById(R.id.TVPackName);
        TextView tvIconCount = view.findViewById(R.id.TVIconCount);

        // Category: use pack name
        tvCategory.setText(context.getString(R.string.packs));

        tvPackName.setText(pack.name);
        tvIconCount.setText(context.getString(R.string.icon_count, pack.iconCount));

        if (pack.preview != null) {
            ivPreview.setImageBitmap(pack.preview);
        } else {
            ivPreview.setImageResource(R.drawable.icon_background);
        }

        return view;
    }
}
