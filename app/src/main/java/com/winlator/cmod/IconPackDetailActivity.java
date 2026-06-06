package com.winlator.cmod;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.winlator.cmod.inputcontrols.IconPackManager;
import com.winlator.cmod.inputcontrols.IconPackManager.IconPack;

import java.io.File;
import java.util.List;

/**
 * Activity that displays all icons in a single icon pack as a grid.
 */
public class IconPackDetailActivity extends AppCompatActivity {
    public static final String EXTRA_PACK_NAME = "pack_name";

    private GridView gridIcons;
    private TextView tvCount;
    private TextView tvEmpty;
    private String packName;
    private IconPack pack;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.icon_pack_detail_activity);

        packName = getIntent().getStringExtra(EXTRA_PACK_NAME);

        Toolbar toolbar = findViewById(R.id.Toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        if (packName == null) {
            finish();
            return;
        }

        gridIcons = findViewById(R.id.GVIcons);
        tvCount = findViewById(R.id.TVCount);
        tvEmpty = findViewById(R.id.TVEmpty);

        IconPackManager mgr = new IconPackManager(this);
        pack = mgr.getIconPack(packName);
        toolbar.setTitle(packName);

        if (pack == null) {
            finish();
            return;
        }

        tvCount.setText(getString(R.string.icon_count, pack.iconCount));

        final List<File> iconFiles = pack.getIconFiles();
        if (iconFiles.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            gridIcons.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            gridIcons.setVisibility(View.VISIBLE);
            gridIcons.setAdapter(new IconGridAdapter(iconFiles));

            // When tapped, apply the icon directly (using pack icon ID) and return result
            gridIcons.setOnItemClickListener((parent, view, position, id) -> {
                // Use pack icon ID directly - no copying to custom icons
                int packIconId = IconPackManager.getPackIconId(pack.id, position);
                Intent resultIntent = new Intent();
                resultIntent.putExtra("icon_id", packIconId);
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        }
    }

    /**
     * Adapter that displays all icons in the pack as a grid
     */
    private class IconGridAdapter extends BaseAdapter {
        private final List<File> files;

        IconGridAdapter(List<File> files) {
            this.files = files;
        }

        @Override
        public int getCount() {
            return files.size();
        }

        @Override
        public Object getItem(int position) {
            return files.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView imageView;
            if (convertView instanceof ImageView) {
                imageView = (ImageView) convertView;
            } else {
                imageView = new ImageView(IconPackDetailActivity.this);
                imageView.setLayoutParams(new GridView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) (getResources().getDisplayMetrics().density * 80)
                ));
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setPadding(8, 8, 8, 8);
                imageView.setBackgroundResource(R.drawable.icon_background);
            }
            Bitmap bm = android.graphics.BitmapFactory.decodeFile(files.get(position).getAbsolutePath());
            if (bm != null) {
                imageView.setImageBitmap(bm);
            }
            return imageView;
        }
    }
}
