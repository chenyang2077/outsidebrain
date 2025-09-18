package com.example.outsidebrain;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1001;
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private List<File> fileList;          // 原始文件列表（当前文件夹）
    private List<File> searchResultList;  // 搜索结果列表
    private File currentDirectory;        // 类成员变量，全类可访问
    private EditText etSearch;            // 搜索输入框
    private Button btnSearch;             // 搜索按钮
    private boolean isInSearchMode = false; // 是否处于搜索模式
    // 新增：声明icon2_btn（新建文件夹按钮）
    private ImageButton folderCreateBtn;
    // 声明add_button（新建TXT文件按钮）
    private FloatingActionButton txtCreateBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. 初始化所有控件（确保ID与布局文件一致）
        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);
        fileRecyclerView = findViewById(R.id.file_list);
        // 初始化：新建TXT文件按钮（原add_button）
        txtCreateBtn = findViewById(R.id.add_button);
        // 初始化：新建文件夹按钮（原icon2_btn）
        folderCreateBtn = findViewById(R.id.icon2_btn);

        // 2. 初始化列表数据（避免空指针）
        fileList = new ArrayList<>();
        searchResultList = new ArrayList<>();

        // 3. 初始化适配器（无参构造）
        fileAdapter = new FileAdapter();
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileRecyclerView.setAdapter(fileAdapter);

        // 4. 权限检查与初始化
        checkPermission();

        // 5. 搜索按钮点击事件
        btnSearch.setOnClickListener(v -> performSearch());

        // 6. 按钮功能拆分：
        // 6.1 新建TXT文件按钮（add_button）：仅触发新建TXT逻辑
        txtCreateBtn.setOnClickListener(v -> showTxtCreateDialog());
        // 6.2 新建文件夹按钮（icon2_btn）：仅触发新建文件夹逻辑
        folderCreateBtn.setOnClickListener(v -> showFolderCreateDialog());
    }

    // ---------------------- 搜索核心逻辑 ----------------------
    private void performSearch() {
        String keyword = etSearch.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            // 输入为空：退出搜索模式，恢复原始列表
            isInSearchMode = false;
            fileAdapter.setData(fileList);
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            return;
        }

        // 异步搜索（避免UI卡顿）
        new Thread(() -> {
            isInSearchMode = true;
            searchResultList.clear();

            // 递归搜索当前文件夹及子文件夹
            recursiveSearch(currentDirectory, keyword);
            // 排序搜索结果
            sortSearchResult();

            // 主线程更新UI
            runOnUiThread(() -> {
                fileAdapter.setData(searchResultList);
                Toast.makeText(MainActivity.this,
                        searchResultList.size() + " 个匹配结果",
                        Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // 递归搜索文件夹及子文件夹
    private void recursiveSearch(File dir, String keyword) {
        if (dir == null || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // 文件夹：名称包含关键词则加入结果
                if (file.getName().toLowerCase().contains(keyword.toLowerCase())) {
                    searchResultList.add(file);
                }
                // 递归遍历子文件夹
                recursiveSearch(file, keyword);
            } else if (file.isFile() && file.getName().endsWith(".txt")) {
                // TXT文件：名称或内容包含关键词则加入结果
                boolean nameMatch = file.getName().toLowerCase().contains(keyword.toLowerCase());
                boolean contentMatch = isContentContainKeyword(file, keyword);
                if (nameMatch || contentMatch) {
                    searchResultList.add(file);
                }
            }
        }
    }

    // 检查TXT文件内容是否包含关键词
    private boolean isContentContainKeyword(File file, String keyword) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.toLowerCase().contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    // 排序搜索结果（文件夹在前，文件在后）
    private void sortSearchResult() {
        if (searchResultList.isEmpty()) return;

        List<File> folders = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File f : searchResultList) {
            if (f.isDirectory()) {
                folders.add(f);
            } else {
                files.add(f);
            }
        }

        // 文件夹按名称排序
        Collections.sort(folders, Comparator.comparing(File::getName));
        // 文件按更新时间排序（最新在前）
        Collections.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        // 合并结果
        searchResultList.clear();
        searchResultList.addAll(folders);
        searchResultList.addAll(files);
    }

    // ---------------------- 初始化与列表加载 ----------------------
    // 初始化外置大脑文件夹
    private void initExternalBrain() {
        File sdCard = Environment.getExternalStorageDirectory();
        currentDirectory = new File(sdCard, "外置大脑"); // 初始化类成员变量

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

        loadFileList(); // 调用类内方法
    }

    // 加载当前文件夹的原始文件列表
    private void loadFileList() {
        fileList.clear();

        File[] files = currentDirectory.listFiles(); // 访问类成员变量
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

            Collections.sort(folders, Comparator.comparing(File::getName));
            Collections.sort(filesList, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            fileList.addAll(folders);
            fileList.addAll(filesList);
        }

        // 非搜索模式下，更新为原始列表
        if (!isInSearchMode) {
            fileAdapter.setData(fileList);
        }
    }

    // 返回键逻辑
    @Override
    public void onBackPressed() {
        if (isInSearchMode) {
            // 退出搜索模式
            isInSearchMode = false;
            etSearch.setText("");
            fileAdapter.setData(fileList);
            Toast.makeText(this, "已退出搜索", Toast.LENGTH_SHORT).show();
        } else if (currentDirectory != null && !currentDirectory.getName().equals("外置大脑")) {
            // 返回上一级文件夹
            currentDirectory = currentDirectory.getParentFile();
            loadFileList();
        } else {
            // 退出应用
            super.onBackPressed();
        }
    }

    // ---------------------- 文件列表适配器（内部类） ----------------------
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

        private List<File> mData = new ArrayList<>(); // 适配器内部数据

        // 数据更新方法
        public void setData(List<File> newData) {
            if (newData != null) {
                mData.clear();
                mData.addAll(newData);
                notifyDataSetChanged();
            }
        }

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file, parent, false);
            return new FileViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            File file = mData.get(position);

            // 1. 设置图标和背景色
            if (file.isDirectory()) {
                holder.ivIcon.setImageResource(R.drawable.ic_folder);
                holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.folderColor));
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.black));
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_file);
                holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.fileColor));
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            }

            // 2. 设置名称（去掉.txt后缀）
            String fileName = file.getName();
            if (file.isFile() && fileName.endsWith(".txt")) {
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
            }
            holder.tvName.setText(fileName);

            // 3. 点击事件（进入文件夹/打开文件）
            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    isInSearchMode = false;
                    etSearch.setText("");
                    currentDirectory = file; // 更新类成员变量
                    loadFileList(); // 调用类内方法
                } else {
                    // 打开文件编辑页面
                    Intent intent = new Intent(MainActivity.this, FileEditorActivity.class);
                    intent.putExtra("file_path", file.getAbsolutePath());
                    startActivity(intent);
                }
            });

            // 4. 长按事件（重命名/删除）
            holder.itemView.setOnLongClickListener(v -> {
                showFileOptions(file); // 调用类内方法
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        // ViewHolder（改为非静态内部类，修复语言级别问题）
        class FileViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;

            public FileViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.icon);
                tvName = itemView.findViewById(R.id.name);
            }
        }
    }

    // ---------------------- 辅助方法（按钮功能拆分核心） ----------------------
    // 检查存储权限
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

    // 权限请求回调
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

    // 创建测试文件
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

    // 1. 新建TXT文件：独立对话框（原add_button功能）
    private void showTxtCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建TXT文件");

        final EditText input = new EditText(this);
        input.setHint("请输入文件名（无需添加.txt后缀）");
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            // 自动添加.txt后缀
            if (!name.endsWith(".txt")) {
                name += ".txt";
            }
            createFile(name);
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 2. 新建文件夹：独立对话框（新增，绑定icon2_btn）
    private void showFolderCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建文件夹");

        final EditText input = new EditText(this);
        input.setHint("请输入文件夹名称");
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "文件夹名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            createFolder(name);
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 创建文件夹（逻辑不变，供showFolderCreateDialog调用）
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

    // 创建文件（逻辑不变，供showTxtCreateDialog调用）
    private void createFile(String name) {
        File newFile = new File(currentDirectory, name);
        if (newFile.exists()) {
            Toast.makeText(this, "文件已存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (newFile.createNewFile()) {
                Toast.makeText(this, "TXT文件创建成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            } else {
                Toast.makeText(this, "TXT文件创建失败", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "TXT文件创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 显示文件操作选项（重命名/删除）
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

    // 重命名文件/文件夹
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

    // 删除文件/文件夹
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

    // 递归删除文件夹（包含子文件/子文件夹）
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

    // 旧的打开文件方法（已弃用）
    @Deprecated
    private void openFileEditor(File file) {
        Toast.makeText(this, "打开文件: " + file.getName(), Toast.LENGTH_SHORT).show();
    }
}