package com.example.outsidebrain;

import android.Manifest;
import android.view.MotionEvent;
import android.graphics.Rect;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
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
    private static final int REQUEST_EDIT_FILE = 101; // 编辑文件的请求码
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private List<File> fileList;          // 原始文件列表
    private List<File> searchResultList;  // 搜索结果列表
    private File currentDirectory;        // 当前目录
    private EditText etSearch;            // 搜索输入框
    private Button btnSearch;             // 搜索按钮
    private boolean isInSearchMode = false; // 是否处于搜索模式
    private ImageButton folderCreateBtn;  // 新建文件夹按钮
    private FloatingActionButton preEditFileBtn; // 预编辑文件按钮

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化控件
        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);
        fileRecyclerView = findViewById(R.id.file_list);
        preEditFileBtn = findViewById(R.id.add_button);
        folderCreateBtn = findViewById(R.id.icon2_btn);

        // 初始化列表数据
        fileList = new ArrayList<>();
        searchResultList = new ArrayList<>();

        // 初始化适配器
        fileAdapter = new FileAdapter();
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileRecyclerView.setAdapter(fileAdapter);

        // 权限检查与初始化
        checkPermission();

        // 搜索按钮点击事件
        btnSearch.setOnClickListener(v -> performSearch());

        // 按钮功能配置
        preEditFileBtn.setOnClickListener(v -> startFilePreEdit());
        folderCreateBtn.setOnClickListener(v -> showFolderCreateDialog());
    }

    // 跳转至文件预编辑页面
    private void startFilePreEdit() {
        if (currentDirectory == null) {
            Toast.makeText(this, "目录未初始化，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent preEditIntent = new Intent(MainActivity.this, FileEditorActivity.class);
        preEditIntent.putExtra("current_dir_path", currentDirectory.getAbsolutePath());
        preEditIntent.putExtra("is_pre_edit", true);
        startActivityForResult(preEditIntent, REQUEST_EDIT_FILE); // 使用带返回结果的启动方式
    }

    // 接收编辑页面返回的结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 如果是从编辑页面返回且需要刷新
        if (requestCode == REQUEST_EDIT_FILE && resultCode == FileEditorActivity.RESULT_REFRESH) {
            loadFileList(); // 刷新文件列表
        }
    }

    // 搜索逻辑
    // 搜索核心逻辑（添加光标控制）
    private void performSearch() {
        String keyword = etSearch.getText().toString().trim();

        // 搜索结束后，清除输入框焦点（光标消失）
        etSearch.clearFocus();

        if (TextUtils.isEmpty(keyword)) {
            isInSearchMode = false;
            fileAdapter.setData(fileList);
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            isInSearchMode = true;
            searchResultList.clear();
            recursiveSearch(currentDirectory, keyword);
            sortSearchResult();

            runOnUiThread(() -> {
                fileAdapter.setData(searchResultList);
                Toast.makeText(MainActivity.this,
                        searchResultList.size() + " 个匹配结果",
                        Toast.LENGTH_SHORT).show();
            });
        }).start();
    }




    // 额外：点击搜索框外部时，清除焦点（可选优化）
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            // 如果当前焦点是搜索框，点击外部则清除焦点
            if (v instanceof EditText && v.getId() == R.id.et_search) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    // 递归搜索
    private void recursiveSearch(File dir, String keyword) {
        if (dir == null || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().toLowerCase().contains(keyword.toLowerCase())) {
                    searchResultList.add(file);
                }
                recursiveSearch(file, keyword);
            } else if (file.isFile() && file.getName().endsWith(".txt")) {
                boolean nameMatch = file.getName().toLowerCase().contains(keyword.toLowerCase());
                boolean contentMatch = isContentContainKeyword(file, keyword);
                if (nameMatch || contentMatch) {
                    searchResultList.add(file);
                }
            }
        }
    }

    // 检查文件内容是否包含关键词
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

    // 排序搜索结果
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

        Collections.sort(folders, Comparator.comparing(File::getName));
        Collections.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        searchResultList.clear();
        searchResultList.addAll(folders);
        searchResultList.addAll(files);
    }

    // 初始化外置大脑文件夹
    private void initExternalBrain() {
        File sdCard = Environment.getExternalStorageDirectory();
        currentDirectory = new File(sdCard, "外置大脑");

        if (!currentDirectory.exists()) {
            if (currentDirectory.mkdirs()) {
                Toast.makeText(this, "已新建根文件夹「外置大脑」", Toast.LENGTH_SHORT).show();
                createTestFile();
            } else {
                Toast.makeText(this, "无法创建根文件夹，请检查存储权限", Toast.LENGTH_SHORT).show();
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

    // 加载文件列表
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

            Collections.sort(folders, Comparator.comparing(File::getName));
            Collections.sort(filesList, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            fileList.addAll(folders);
            fileList.addAll(filesList);
        }

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
            // 退出应用时调用父类方法
            super.onBackPressed();
        }
    }

    // 文件列表适配器
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

        private List<File> mData = new ArrayList<>();

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

            // 设置图标和背景色
            if (file.isDirectory()) {
                holder.ivIcon.setImageResource(R.drawable.ic_folder);
                holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.folderColor));
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.black));
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_file);
                holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.fileColor));
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            }

            // 设置名称（去掉.txt后缀）
            String fileName = file.getName();
            if (file.isFile() && fileName.endsWith(".txt")) {
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
            }
            holder.tvName.setText(fileName);

            // 点击事件
            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    isInSearchMode = false;
                    etSearch.setText("");
                    currentDirectory = file;
                    loadFileList();
                } else {
                    // 打开已有文件，使用带返回结果的启动方式
                    Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
                    editIntent.putExtra("file_path", file.getAbsolutePath());
                    editIntent.putExtra("is_pre_edit", false);
                    startActivityForResult(editIntent, REQUEST_EDIT_FILE);
                }
            });

            // 长按事件
            holder.itemView.setOnLongClickListener(v -> {
                showFileOptions(file);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

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

    // 权限检查
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

    // 新建文件夹对话框
    // 新建文件夹：独立对话框（添加自动弹出输入法）
    private void showFolderCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建文件夹");

        final EditText input = new EditText(this);
        input.setHint("请输入文件夹名称");
        // 设置输入类型为文本，确保输入法正确弹出
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        // 构建对话框并保存引用
        AlertDialog dialog = builder.setPositiveButton("确认", (dialogInterface, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "文件夹名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            createFolder(name);
        }).setNegativeButton("取消", null).create();

        // 显示对话框后，自动聚焦输入框并弹出输入法
        dialog.setOnShowListener(dialogInterface -> {
            input.requestFocus(); // 聚焦输入框
            // 延迟弹出输入法，确保对话框已完全显示
            input.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 200);
        });

        dialog.show();
    }

    // 创建文件夹
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

    // 显示文件操作选项
    private void showFileOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

    // 递归删除文件夹
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

    @Deprecated
    private void openFileEditor(File file) {
        Toast.makeText(this, "打开文件: " + file.getName(), Toast.LENGTH_SHORT).show();
    }
}
