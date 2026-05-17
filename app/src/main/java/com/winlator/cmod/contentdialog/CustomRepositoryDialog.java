package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.winlator.cmod.R;

public class CustomRepositoryDialog extends ContentDialog {
    private EditText etRepoUrl;
    private EditText etRepoName;
    private Button btnAdd;
    private Button btnCancel;
    private OnRepositoryAddedListener listener;
    
    public interface OnRepositoryAddedListener {
        void onRepositoryAdded(String repoUrl, String repoName);
    }
    
    public CustomRepositoryDialog(Context context, OnRepositoryAddedListener listener) {
        super(context, R.layout.dialog_custom_repository);
        this.listener = listener;
        setupDialog();
    }
    
    private void setupDialog() {
        setTitle(R.string.add_custom_repository);
        setIcon(R.drawable.ic_add);
        
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_custom_repository, null);
        setContentView(view);
        
        etRepoUrl = view.findViewById(R.id.etRepoUrl);
        etRepoName = view.findViewById(R.id.etRepoName);
        btnAdd = view.findViewById(R.id.btnAdd);
        btnCancel = view.findViewById(R.id.btnCancel);
        
        btnAdd.setOnClickListener(v -> onAddClicked());
        btnCancel.setOnClickListener(v -> dismiss());
        
        // Автозаполнение имени репозитория при вводе URL
        etRepoUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus && etRepoName.getText().toString().trim().isEmpty()) {
                String url = etRepoUrl.getText().toString().trim();
                String autoName = extractRepoNameFromUrl(url);
                if (!autoName.isEmpty()) {
                    etRepoName.setText(autoName);
                }
            }
        });
    }
    
    private void onAddClicked() {
        String repoUrl = etRepoUrl.getText().toString().trim();
        String repoName = etRepoName.getText().toString().trim();
        
        if (repoUrl.isEmpty()) {
            Toast.makeText(getContext(), R.string.repo_url_required, Toast.LENGTH_SHORT).show();
            etRepoUrl.requestFocus();
            return;
        }
        
        // Валидация формата GitHub репозитория
        if (!isValidGitHubRepo(repoUrl)) {
            Toast.makeText(getContext(), R.string.invalid_repo_format, Toast.LENGTH_LONG).show();
            etRepoUrl.requestFocus();
            return;
        }
        
        // Если имя не указано, извлекаем из URL
        if (repoName.isEmpty()) {
            repoName = extractRepoNameFromUrl(repoUrl);
        }
        
        if (listener != null) {
            listener.onRepositoryAdded(repoUrl, repoName);
        }
        
        dismiss();
    }
    
    private boolean isValidGitHubRepo(String url) {
        // Поддерживаем форматы:
        // 1. username/repo
        // 2. https://github.com/username/repo
        // 3. github.com/username/repo
        
        if (url.matches("^[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$")) {
            return true;
        }
        
        if (url.matches("^(https?://)?(www\\.)?github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+/?$")) {
            return true;
        }
        
        return false;
    }
    
    private String extractRepoNameFromUrl(String url) {
        // Извлекаем имя репозитория из URL
        String normalized = url.replaceAll("^(https?://)?(www\\.)?github\\.com/", "");
        normalized = normalized.replaceAll("/$", "");
        
        String[] parts = normalized.split("/");
        if (parts.length >= 2) {
            return parts[1]; // Возвращаем название репозитория
        }
        
        return "";
    }
}