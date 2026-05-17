package com.winlator.cmod.contentdialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.cmod.R;
import com.winlator.cmod.win32.PEParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FilePickerDialog extends ContentDialog {
    private File currentDirectory;
    private TextView currentPathTextView;
    private RecyclerView recyclerView;
    private FileAdapter adapter;
    private OnFileSelectedListener listener;
    private ExecutorService iconExecutor;
    private Handler mainHandler;

    public interface OnFileSelectedListener {
        void onFileSelected(File file);
    }

    public FilePickerDialog(Context context, File initialDirectory, OnFileSelectedListener listener) {
        super(context, R.layout.file_picker_dialog);
        this.currentDirectory = initialDirectory;
        this.listener = listener;
        this.iconExecutor = Executors.newFixedThreadPool(2);
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        setTitle("Select File");
        setIcon(R.drawable.icon_open);
        
        View view = getContentView();
        currentPathTextView = view.findViewById(R.id.TVCurrentPath);
        recyclerView = view.findViewById(R.id.RecyclerViewFiles);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);
        
        loadFiles();
        
        // Set fixed dialog size
        setFixedDialogSize(context);
    }
    
    private void setFixedDialogSize(Context context) {
        if (getWindow() != null) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.getDefaultDisplay().getMetrics(displayMetrics);
                
                // Set width to 85% of screen width
                int width = (int) (displayMetrics.widthPixels * 0.85);
                
                // Set fixed height
                int height = ViewGroup.LayoutParams.WRAP_CONTENT;
                
                getWindow().setLayout(width, height);
            }
        }
    }
    
    @Override
    public void dismiss() {
        super.dismiss();
        if (iconExecutor != null) {
            iconExecutor.shutdown();
        }
    }

    private void loadFiles() {
        currentPathTextView.setText(currentDirectory.getAbsolutePath());
        
        List<File> files = new ArrayList<>();
        
        // Add parent directory if not root
        if (currentDirectory.getParentFile() != null) {
            files.add(new File(currentDirectory, ".."));
        }
        
        // Add files and directories - filter to show only .exe files and directories
        File[] fileArray = currentDirectory.listFiles();
        if (fileArray != null) {
            for (File file : fileArray) {
                // Add directories or .exe files only
                if (file.isDirectory() || file.getName().toLowerCase().endsWith(".exe")) {
                    files.add(file);
                }
            }
            
            // Sort: directories first, then files, alphabetically
            Collections.sort(files, (f1, f2) -> {
                if (f1.getName().equals("..")) return -1;
                if (f2.getName().equals("..")) return 1;
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });
        }
        
        adapter.setFiles(files);
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        private List<File> files = new ArrayList<>();

        void setFiles(List<File> files) {
            this.files = files;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.file_picker_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = files.get(position);
            holder.currentFile = file;
            holder.name.setText(file.getName());
            
            // Set default icons
            if (file.getName().equals("..")) {
                // Custom back arrow icon
                holder.icon.setImageResource(R.drawable.ic_arrow_back);
            } else if (file.isDirectory()) {
                // Custom folder icon
                holder.icon.setImageResource(R.drawable.icon_back);
            } else {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".exe")) {
                    // Default exe icon first, then load real icon
                    holder.icon.setImageResource(android.R.drawable.ic_menu_preferences);
                    
                    // Load real icon from .exe file in background
                    iconExecutor.execute(() -> {
                        Bitmap exeIcon = PEParser.extractIcon(file);
                        
                        mainHandler.post(() -> {
                            // Check if this ViewHolder still displays the same file
                            if (holder.currentFile != null && holder.currentFile.equals(file) && exeIcon != null) {
                                holder.icon.setImageBitmap(exeIcon);
                            }
                        });
                    });
                } else {
                    // Generic file icon
                    holder.icon.setImageResource(android.R.drawable.ic_menu_info_details);
                }
            }
            
            holder.itemView.setOnClickListener(v -> {
                if (file.getName().equals("..")) {
                    currentDirectory = currentDirectory.getParentFile();
                    loadFiles();
                } else if (file.isDirectory()) {
                    currentDirectory = file;
                    loadFiles();
                } else if (file.getName().toLowerCase().endsWith(".exe")) {
                    listener.onFileSelected(file);
                    dismiss();
                }
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;
            File currentFile;

            ViewHolder(View view) {
                super(view);
                icon = view.findViewById(R.id.IVFileIcon);
                name = view.findViewById(R.id.TVFileName);
            }
        }
    }
}
