package com.example.outsidebrain;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1001;
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private List<File> fileList;
    private File currentDirectory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fileRecyclerView = findViewById(R.id.file_list);
        FloatingActionButton addButton = findViewById(R.id.add_button);

        fileList = new ArrayList<>();
        fileAdapter = new FileAdapter(fileList);
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileRecyclerView.setAdapter(fileAdapter);

        // 检查权限
        checkPermission();

        // 添加按钮点击事件
        addButton.setOnClickListener(v -> showCreateDialog());
    }

    // 检查存储权限（不变）
    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION);
            } else {
                initExternalBrain();
            }
        } else {
            initExternalBrain();
        }
    }

    // 权限回调（不变）
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initExternalBrain();
            } else {
                Toast.makeText(this, "需要存储权限才能使用应用", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // 初始化外置大脑文件夹（不变）
    private void initExternalBrain() {
        File sdCard = Environment.getExternalStorageDirectory();
        currentDirectory = new File(sdCard, "外置大脑");

        if (!currentDirectory.exists()) {
            if (currentDirectory.mkdirs()) {
                Toast.makeText(this, "已新建文件夹", Toast.LENGTH_SHORT).show();
                createTestFile();
            } else {
                Toast.makeText(this, "无法创建文件夹", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            File[] files = currentDirectory.listFiles();
            if (files == null || files.length == 0) {
                createTestFile();
            }
        }

        loadFileList();
    }

    // 创建测试文件（不变）
    private void createTestFile() {
        File testFile = new File(currentDirectory, "测试文件.txt");
        try {
            if (testFile.createNewFile()) {
                FileOutputStream fos = new FileOutputStream(testFile);
                fos.write("这是一个测试文件\n支持多行编辑哦～".getBytes());
                fos.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "创建测试文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 加载文件列表（不变）
    private void loadFileList() {
        fileList.clear();

        File[] files = currentDirectory.listFiles();
        if (files != null) {
            List<File> folders = new ArrayList<>();
            List<File> filesList = new ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) {
                    folders.add(file);
                } else if (file.isFile() && file.getName().endsWith(".txt")) {
                    filesList.add(file);
                }
            }

            // 文件夹按首字母排序
            Collections.sort(folders, Comparator.comparing(File::getName));
            // 文件按更新时间排序（最新在前）
            Collections.sort(filesList, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            fileList.addAll(folders);
            fileList.addAll(filesList);
        }

        fileAdapter.notifyDataSetChanged();
    }

    // 显示创建文件/文件夹对话框（不变）
    private void showCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("创建");
        String[] options = {"创建文件夹", "创建文件"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showInputDialog("新建文件夹", true);
            } else {
                showInputDialog("新建文件", false);
            }
        });
        builder.show();
    }

    // 显示输入对话框（不变）
    private void showInputDialog(String title, boolean isFolder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isFolder) {
                createFolder(name);
            } else {
                if (!name.endsWith(".txt")) {
                    name += ".txt";
                }
                createFile(name);
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 创建文件夹（不变）
    private void createFolder(String name) {
        File newFolder = new File(currentDirectory, name);
        if (newFolder.exists()) {
            Toast.makeText(this, "文件夹已存在", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newFolder.mkdirs()) {
            Toast.makeText(this, "文件夹创建成功", Toast.LENGTH_SHORT).show();
            loadFileList();
        } else {
            Toast.makeText(this, "文件夹创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 创建文件（不变）
    private void createFile(String name) {
        File newFile = new File(currentDirectory, name);
        if (newFile.exists()) {
            Toast.makeText(this, "文件已存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (newFile.createNewFile()) {
                Toast.makeText(this, "文件创建成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            } else {
                Toast.makeText(this, "文件创建失败", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "文件创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 文件适配器（核心修改：适配新样式和跳转逻辑）
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

        private List<File> files;

        public FileAdapter(List<File> files) {
            this.files = files;
        }

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file, parent, false);
            return new FileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            File file = files.get(position);

            // 变更1：设置图标和背景色（引用新的颜色资源）
            if (file.isDirectory()) {
                holder.icon.setImageResource(R.drawable.ic_folder);
                // 文件夹背景：浅棕色
                holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.folderColor));
                // 文件夹文字颜色：黑色（与浅棕色对比）
                holder.name.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.black));
            } else {
                holder.icon.setImageResource(R.drawable.ic_file);
                // 文件背景：深灰色
                holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.fileColor));
                // 文件文字颜色：白色（与深灰色对比）
                holder.name.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            }

            // 显示名称（去掉.txt后缀，不变）
            String fileName = file.getName();
            if (file.isFile() && fileName.endsWith(".txt")) {
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
            }
            holder.name.setText(fileName);

            // 变更2：点击文件跳转到编辑页面（替换原Toast逻辑）
            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    currentDirectory = file;
                    loadFileList();
                } else {
                    // 跳转到FileEditorActivity，传递文件路径
                    Intent intent = new Intent(MainActivity.this, FileEditorActivity.class);
                    intent.putExtra("file_path", file.getAbsolutePath());
                    startActivity(intent);
                }
            });

            // 长按事件（不变）
            holder.itemView.setOnLongClickListener(v -> {
                showFileOptions(file);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return files.size();
        }

        public class FileViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView name;

            public FileViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.icon);
                name = itemView.findViewById(R.id.name);
            }
        }
    }

    // 显示文件操作选项（不变）
    private void showFileOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("操作");
        String[] options = {"重命名", "删除"};
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                renameFile(file);
            } else {
                deleteFile(file);
            }
        });
        builder.show();
    }

    // 重命名文件/文件夹（不变）
    private void renameFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名");

        final EditText input = new EditText(this);
        String originalName = file.getName();
        if (file.isFile() && originalName.endsWith(".txt")) {
            originalName = originalName.substring(0, originalName.lastIndexOf("."));
        }
        input.setText(originalName);
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            if (file.isFile() && !newName.endsWith(".txt")) {
                newName += ".txt";
            }

            File newFile = new File(file.getParentFile(), newName);
            if (newFile.exists()) {
                Toast.makeText(this, "名称已存在", Toast.LENGTH_SHORT).show();
                return;
            }

            if (file.renameTo(newFile)) {
                Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            } else {
                Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 删除文件/文件夹（不变）
    private void deleteFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除")
                .setMessage("确定要删除 " + file.getName() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (deleteRecursive(file)) {
                        Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
                        loadFileList();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 递归删除文件夹（不变）
    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return file.delete();
    }

    // 旧的打开文件方法（已弃用，被适配器中的跳转逻辑替代）
    @Deprecated
    private void openFileEditor(File file) {
        Toast.makeText(this, "打开文件: " + file.getName(), Toast.LENGTH_SHORT).show();
    }

    // 返回键逻辑（不变）
    @Override
    public void onBackPressed() {
        if (currentDirectory != null && !currentDirectory.getName().equals("外置大脑")) {
            currentDirectory = currentDirectory.getParentFile();
            loadFileList();
        } else {
            super.onBackPressed();
        }
    }
}