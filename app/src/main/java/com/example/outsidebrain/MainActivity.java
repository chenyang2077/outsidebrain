package com.example.outsidebrain;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
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
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1001;
    private static final int REQUEST_EDIT_FILE = 101;
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private List<File> fileList;
    private List<File> searchResultList;
    private File currentDirectory;
    private EditText etSearch;
    private Button btnSearch;
    private boolean isInSearchMode = false;
    private ImageButton folderCreateBtn;
    private FloatingActionButton preEditFileBtn;
    private static final String ROOT_FOLDER_NAME = "外置大脑"; // 根文件夹名称
    // 正则：匹配文件名中的时间戳（格式：-yy-MM-dd）
    public static final Pattern FILE_TIMESTAMP_PATTERN = Pattern.compile("-\\d{2}-\\d{2}-\\d{2}");

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

        // 搜索框文本变化监听
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(s)) {
                    updateLevelHint();
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // 按钮功能配置
        preEditFileBtn.setOnClickListener(v -> startFilePreEdit());
        folderCreateBtn.setOnClickListener(v -> showFolderCreateDialog());
    }

    // 更新层级作为搜索框hint
    private void updateLevelHint() {
        if (currentDirectory == null) return;

        List<Integer> levelPath = getLevelPath(currentDirectory);
        StringBuilder levelStr = new StringBuilder("Lv-");
        for (int i = 0; i < levelPath.size(); i++) {
            levelStr.append(levelPath.get(i));
            if (i < levelPath.size() - 1) {
                levelStr.append("-");
            }
        }
        etSearch.setHint(levelStr.toString());
    }

    // 计算文件夹层级路径
    private List<Integer> getLevelPath(File file) {
        List<Integer> levelPath = new ArrayList<>();
        File current = file;

        if (current.getName().equals(ROOT_FOLDER_NAME)) {
            levelPath.add(1);
            return levelPath;
        }

        while (current != null) {
            String fileName = current.getName();
            if (fileName.equals(ROOT_FOLDER_NAME)) {
                levelPath.add(1);
                break;
            }

            File parent = current.getParentFile();
            if (parent == null) break;

            File[] siblings = parent.listFiles(File::isDirectory);
            if (siblings != null) {
                List<File> sortedSiblings = new ArrayList<>();
                Collections.addAll(sortedSiblings, siblings);
                Collections.sort(sortedSiblings, Comparator.comparing(File::getName));

                for (int i = 0; i < sortedSiblings.size(); i++) {
                    if (sortedSiblings.get(i).getName().equals(fileName)) {
                        levelPath.add(i + 1);
                        break;
                    }
                }
            }

            current = parent;
        }

        Collections.reverse(levelPath);
        return levelPath;
    }

    // 跳转至文件预编辑页面（传递根文件夹名称，用于计算相对路径）
    private void startFilePreEdit() {
        if (currentDirectory == null) {
            Toast.makeText(this, "目录未初始化，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent preEditIntent = new Intent(MainActivity.this, FileEditorActivity.class);
        preEditIntent.putExtra("current_dir_path", currentDirectory.getAbsolutePath());
        preEditIntent.putExtra("is_pre_edit", true);
        preEditIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME); // 传递根文件夹名称
        startActivityForResult(preEditIntent, REQUEST_EDIT_FILE);
    }

    // 接收编辑页面返回结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_FILE && resultCode == FileEditorActivity.RESULT_REFRESH) {
            loadFileList(); // 压缩/分享/创建后刷新列表
        }
    }

    // 搜索逻辑（支持TXT和ZIP文件）
    private void performSearch() {
        String keyword = etSearch.getText().toString().trim();
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

    // 递归搜索（包含ZIP文件）
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
            } else if (isSupportedFile(file)) { // 支持TXT和ZIP
                boolean nameMatch = getDisplayName(file).toLowerCase().contains(keyword.toLowerCase());
                boolean contentMatch = isContentContainKeyword(file, keyword);
                if (nameMatch || contentMatch) {
                    searchResultList.add(file);
                }
            }
        }
    }

    // 检查是否为支持的文件类型（TXT或ZIP）
    private boolean isSupportedFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".txt") || fileName.endsWith(".zip");
    }

    // 检查文件内容是否包含关键词（仅TXT文件，ZIP不检查）
    private boolean isContentContainKeyword(File file, String keyword) {
        if (file.getName().toLowerCase().endsWith(".zip")) return false;

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

    // 排序搜索结果（文件夹→TXT→ZIP）
    private void sortSearchResult() {
        if (searchResultList.isEmpty()) return;

        List<File> folders = new ArrayList<>();
        List<File> txtFiles = new ArrayList<>();
        List<File> zipFiles = new ArrayList<>();

        for (File f : searchResultList) {
            if (f.isDirectory()) {
                folders.add(f);
            } else if (f.getName().toLowerCase().endsWith(".txt")) {
                txtFiles.add(f);
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                zipFiles.add(f);
            }
        }

        Collections.sort(folders, Comparator.comparing(File::getName));
        Collections.sort(txtFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        searchResultList.clear();
        searchResultList.addAll(folders);
        searchResultList.addAll(txtFiles);
        searchResultList.addAll(zipFiles);
    }

    // 初始化根文件夹
    private void initExternalBrain() {
        File sdCard = Environment.getExternalStorageDirectory();
        currentDirectory = new File(sdCard, ROOT_FOLDER_NAME);

        if (!currentDirectory.exists()) {
            if (currentDirectory.mkdirs()) {
                Toast.makeText(this, "已新建根文件夹「" + ROOT_FOLDER_NAME + "」", Toast.LENGTH_SHORT).show();
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
        updateLevelHint();
    }

    // 加载文件列表（显示TXT和ZIP文件）
    private void loadFileList() {
        fileList.clear();

        File[] files = currentDirectory.listFiles();
        if (files != null) {
            List<File> folders = new ArrayList<>();
            List<File> txtFiles = new ArrayList<>();
            List<File> zipFiles = new ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) {
                    folders.add(file);
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    txtFiles.add(file);
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    zipFiles.add(file);
                }
            }

            Collections.sort(folders, Comparator.comparing(File::getName));
            Collections.sort(txtFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            fileList.addAll(folders);
            fileList.addAll(txtFiles);
            fileList.addAll(zipFiles);
        }

        if (!isInSearchMode) {
            fileAdapter.setData(fileList);
        }

        if (TextUtils.isEmpty(etSearch.getText().toString().trim())) {
            updateLevelHint();
        }
    }

    // 返回键逻辑
    @Override
    public void onBackPressed() {
        if (isInSearchMode) {
            isInSearchMode = false;
            etSearch.setText("");
            etSearch.clearFocus();
            fileAdapter.setData(fileList);
            Toast.makeText(this, "已退出搜索", Toast.LENGTH_SHORT).show();
        } else if (currentDirectory != null && !currentDirectory.getName().equals(ROOT_FOLDER_NAME)) {
            currentDirectory = currentDirectory.getParentFile();
            etSearch.clearFocus();
            loadFileList();
        } else {
            super.onBackPressed();
        }
    }

    // 新建文件夹对话框
    private void showFolderCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建文件夹");

        final EditText input = new EditText(this);
        input.setHint("请输入文件夹名称");
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        AlertDialog dialog = builder.setPositiveButton("确认", (dialogInterface, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "文件夹名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            createFolder(name);
        }).setNegativeButton("取消", null).create();

        dialog.setOnShowListener(dialogInterface -> {
            input.requestFocus();
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

    // 创建测试TXT文件（带目录标识）
    private void createTestFile() {
        File testFile = new File(currentDirectory, "测试文件.txt");
        try {
            if (testFile.createNewFile()) {
                // 生成相对根目录的路径（根目录下为“根目录”）
                String dirPath = getRelativeDirPath(currentDirectory, ROOT_FOLDER_NAME);
                dirPath = TextUtils.isEmpty(dirPath) ? "根目录" : dirPath;
                // 拼接内容（标识+测试内容）
                String content = "{" + dirPath + "}\n这是一个测试文件\n支持多行编辑哦～";
                FileOutputStream fos = new FileOutputStream(testFile);
                fos.write(content.getBytes());
                fos.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "创建测试文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 核心1：获取文件显示名称（TXT隐藏时间戳和后缀，ZIP显示完整名称含.zip）
    private String getDisplayName(File file) {
        if (file.isDirectory()) return file.getName();

        String fileName = file.getName();
        if (fileName.endsWith(".txt")) {
            // TXT文件：先去除时间戳，再去除后缀
            fileName = FILE_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
            return fileName.substring(0, fileName.lastIndexOf("."));
        } else if (fileName.endsWith(".zip")) {
            // ZIP文件：显示完整名称（含后缀，保留时间戳）
            return fileName;
        }
        return fileName;
    }

    // 核心2：计算当前目录相对于根目录的路径（静态方法，供FileEditorActivity调用）
    public static String getRelativeDirPath(File dir, String rootName) {
        List<String> pathSegments = new ArrayList<>();
        File current = dir;
        // 从当前目录向上追溯，直到根目录“外置大脑”
        while (current != null && !current.getName().equals(rootName)) {
            pathSegments.add(current.getName());
            current = current.getParentFile();
        }
        // 反转列表，生成从根到当前的路径（如：文件夹2 → 文件夹2.2 → 路径为“文件夹2/文件夹2.2”）
        Collections.reverse(pathSegments);
        return String.join("/", pathSegments);
    }

    // 文件夹长按选项（重命名/删除/压缩为ZIP）
    private void showFolderOptions(File folder) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] options = {"重命名", "删除", "压缩为ZIP文件"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    renameFile(folder);
                    break;
                case 1:
                    deleteFile(folder);
                    break;
                case 2:
                    zipFolder(folder); // 调用压缩逻辑
                    break;
            }
        });
        builder.show();
    }

    // 文件/ZIP长按选项（重命名/删除/分享）
    private void showFileOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] options = {"重命名", "删除", "分享"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    renameFile(file);
                    break;
                case 1:
                    deleteFile(file);
                    break;
                case 2:
                    shareFile(file); // 调用分享逻辑
                    break;
            }
        });
        builder.show();
    }

    // 压缩文件夹（跳转至FileEditorActivity处理）
    private void zipFolder(File folder) {
        Intent intent = new Intent(this, FileEditorActivity.class);
        intent.putExtra("ACTION_ZIP_FOLDER", true);
        intent.putExtra("FOLDER_PATH", folder.getAbsolutePath());
        startActivityForResult(intent, REQUEST_EDIT_FILE);
    }

    // 分享文件（跳转至FileEditorActivity处理）
    private void shareFile(File file) {
        Intent intent = new Intent(this, FileEditorActivity.class);
        intent.putExtra("ACTION_SHARE_FILE", true);
        intent.putExtra("FILE_PATH", file.getAbsolutePath());
        startActivityForResult(intent, REQUEST_EDIT_FILE);
    }

    // 重命名文件/文件夹
    private void renameFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名");

        final EditText input = new EditText(this);
        input.setText(getDisplayName(file));
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 保留文件后缀
            if (file.isFile()) {
                if (file.getName().endsWith(".txt") && !newName.endsWith(".txt")) {
                    newName += ".txt";
                } else if (file.getName().endsWith(".zip") && !newName.endsWith(".zip")) {
                    newName += ".zip";
                }
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
                .setMessage("确定要删除 " + getDisplayName(file) + " 吗？")
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

    // 递归删除文件夹（包含子文件）
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

    // 权限申请结果
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

    // 点击外部清除搜索框焦点
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
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

    // 文件列表适配器（显示TXT和ZIP，区分长按逻辑）
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

            // 设置图标和背景色（ZIP复用文件图标，区分颜色）
            if (file.isDirectory()) {
                holder.ivIcon.setImageResource(R.drawable.ic_folder);
                holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.folderColor));
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.black));
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_file); // ZIP复用TXT图标
                if (file.getName().toLowerCase().endsWith(".zip")) {
                    holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.zipColor));
                } else {
                    holder.itemView.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.fileColor));
                }
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            }

            // 显示文件名（TXT隐藏时间戳和后缀，ZIP显示完整名称含.zip）
            holder.tvName.setText(getDisplayName(file));

            // 点击事件
            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    isInSearchMode = false;
                    etSearch.setText("");
                    currentDirectory = file;
                    loadFileList();
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    // 打开TXT编辑（传递根文件夹名称）
                    Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
                    editIntent.putExtra("file_path", file.getAbsolutePath());
                    editIntent.putExtra("is_pre_edit", false);
                    editIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
                    startActivityForResult(editIntent, REQUEST_EDIT_FILE);
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    // ZIP文件点击：显示操作选项
                    showFileOptions(file);
                }
            });

            // 长按事件：区分文件夹和文件
            holder.itemView.setOnLongClickListener(v -> {
                if (file.isDirectory()) {
                    showFolderOptions(file);
                } else {
                    showFileOptions(file);
                }
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
}