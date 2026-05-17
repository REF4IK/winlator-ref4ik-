package com.winlator.cmod.components;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Диалог для выбора и установки компонентов из GitHub
 */
public class ComponentsDialog extends Dialog {
    private ProgressBar loadingProgress;
    private TextView loadingText;
    private RecyclerView recyclerView;
    private Button btnCancel;
    private Button btnInstall;
    
    private ComponentsAdapter adapter;
    private List<ComponentInfo> components = new ArrayList<>();
    private ComponentInstallListener listener;

    public interface ComponentInstallListener {
        void onInstall(List<ComponentInfo> selectedComponents);
    }

    public ComponentsDialog(@NonNull Context context, ComponentInstallListener listener) {
        super(context);
        this.listener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.components_dialog);

        // Настройка размера диалога
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        initViews();
        loadComponents();
    }

    private void initViews() {
        loadingProgress = findViewById(R.id.loadingProgress);
        loadingText = findViewById(R.id.loadingText);
        recyclerView = findViewById(R.id.componentsRecyclerView);
        btnCancel = findViewById(R.id.btnCancel);
        btnInstall = findViewById(R.id.btnInstall);

        // Настройка RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ComponentsAdapter();
        recyclerView.setAdapter(adapter);

        // Обработчики кнопок
        btnCancel.setOnClickListener(v -> dismiss());
        btnInstall.setOnClickListener(v -> installSelected());
    }

    private void loadComponents() {
        showLoading(true);
        
        GitHubComponentManager.loadComponents(new GitHubComponentManager.ComponentsCallback() {
            @Override
            public void onComponentsLoaded(List<ComponentInfo> loadedComponents) {
                components = loadedComponents;
                showLoading(false);
                adapter.setComponents(components);
                updateInstallButton();
            }

            @Override
            public void onError(String error) {
                showLoading(false);
                loadingText.setText("Error: " + error);
                loadingText.setVisibility(View.VISIBLE);
                Toast.makeText(getContext(), "Failed to load components: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoading(boolean show) {
        loadingProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        loadingText.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void updateInstallButton() {
        int selectedCount = 0;
        for (ComponentInfo component : components) {
            if (component.isSelected()) {
                selectedCount++;
            }
        }
        
        btnInstall.setEnabled(selectedCount > 0);
        if (selectedCount > 0) {
            btnInstall.setText("Install Selected (" + selectedCount + ")");
        } else {
            btnInstall.setText("Install Selected");
        }
    }

    private void installSelected() {
        List<ComponentInfo> selectedComponents = new ArrayList<>();
        for (ComponentInfo component : components) {
            if (component.isSelected()) {
                selectedComponents.add(component);
            }
        }
        
        if (!selectedComponents.isEmpty() && listener != null) {
            listener.onInstall(selectedComponents);
            dismiss();
        }
    }

    /**
     * Адаптер для списка компонентов
     */
    private class ComponentsAdapter extends RecyclerView.Adapter<ComponentsAdapter.ViewHolder> {
        private List<ComponentInfo> items = new ArrayList<>();

        void setComponents(List<ComponentInfo> components) {
            this.items = components;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.component_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ComponentInfo component = items.get(position);
            holder.bind(component);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private ImageView icon;
            private TextView name;
            private TextView description;
            private TextView size;
            private CheckBox checkbox;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.componentIcon);
                name = itemView.findViewById(R.id.componentName);
                description = itemView.findViewById(R.id.componentDescription);
                size = itemView.findViewById(R.id.componentSize);
                checkbox = itemView.findViewById(R.id.componentCheckbox);
            }

            void bind(ComponentInfo component) {
                name.setText(component.getDisplayName());
                description.setText(component.getDescription());
                size.setText("Size: " + component.getFormattedSize());
                checkbox.setChecked(component.isSelected());
                
                // Установка иконки по типу
                int iconRes = ComponentInfo.getIconForType(component.getType());
                icon.setImageResource(iconRes);

                // Клик по всему элементу переключает checkbox
                itemView.setOnClickListener(v -> {
                    component.setSelected(!component.isSelected());
                    checkbox.setChecked(component.isSelected());
                    updateInstallButton();
                });
            }
        }
    }
}
