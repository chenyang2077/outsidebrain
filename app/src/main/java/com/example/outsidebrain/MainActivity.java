package com.example.outsidebrain;

import android.Manifest;
import android.content.DialogInterface;
import android.net.Uri;
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
import android.widget.FrameLayout;
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1001;
    private static final int REQUEST_EDIT_FILE = 101;
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private List<File> fileList;
    private List<File> searchResultList;
    private File currentDirectory;
    private File rootDirectory; // 新增：根目录引用
    private EditText etSearch;
    private Button btnSearch;
    private boolean isInSearchMode = false;
    private ImageButton folderCreateBtn;
    private FloatingActionButton preEditFileBtn;
    private static final String ROOT_FOLDER_NAME = "外置大脑"; // 根文件夹名称
    // 正则：匹配文件名中的时间戳（格式：-yy-MM-dd）
    public static final Pattern FILE_TIMESTAMP_PATTERN = Pattern.compile("-\\d{2}-\\d{2}-\\d{2}");

    // 匹配 TXT 中【】包裹的路径（如【文件夹1/文件夹2】）
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile("【([^】]*)】");

    // 新增：匹配内容中的时间戳（格式：(yyyy-MM-dd)）
    private static final Pattern CONTENT_TIMESTAMP_PATTERN = Pattern.compile("\\(\\d{4}-\\d{2}-\\d{2}\\)");

    // 复制粘贴核心变量
    private File copiedFile;
    private boolean isCutOperation;
    private View pasteButton;


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
        btnSearch.setOnClickListener(v -> {
            hidePasteButton();
            performSearch();
        });

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

        // 搜索框点击事件
        etSearch.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hidePasteButton();
            }
            return false;
        });

        // 按钮功能配置
        preEditFileBtn.setOnClickListener(v -> {
            hidePasteButton();
            startFilePreEdit();
        });
        folderCreateBtn.setOnClickListener(v -> {
            hidePasteButton();
            showFolderCreateDialog();
        });
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

    // 跳转至文件预编辑页面
    private void startFilePreEdit() {
        if (currentDirectory == null) {
            Toast.makeText(this, "目录未初始化，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent preEditIntent = new Intent(MainActivity.this, FileEditorActivity.class);
        preEditIntent.putExtra("current_dir_path", currentDirectory.getAbsolutePath());
        preEditIntent.putExtra("is_pre_edit", true); // 标记为新建文件
        preEditIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
        // 新增：传递是否为根目录
        preEditIntent.putExtra("is_root_directory", currentDirectory.equals(rootDirectory));
        startActivityForResult(preEditIntent, REQUEST_EDIT_FILE);
    }

    // 接收编辑页面返回结果
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_FILE && resultCode == FileEditorActivity.RESULT_REFRESH) {
            loadFileList(); // 压缩/分享/创建/解压后刷新列表
            hidePasteButton();
        }
    }

    // 搜索内容匹配逻辑
    private boolean isContentContainKeyword(File file, String keyword) {
        if (file.getName().toLowerCase().endsWith(".zip")) return false;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                // 过滤【】及其中间内容后再判断
                String filteredLine = line.replaceAll("【.*?】", "");
                if (filteredLine.toLowerCase().contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    // 执行搜索
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
            } else if (isSupportedFile(file)) { // 支持TXT和ZIP
                boolean nameMatch = getDisplayName(file).toLowerCase().contains(keyword.toLowerCase());
                boolean contentMatch = isContentContainKeyword(file, keyword);
                if (nameMatch || contentMatch) {
                    searchResultList.add(file);
                }
            }
        }
    }

    // 检查是否为支持的文件类型
    private boolean isSupportedFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".txt") || fileName.endsWith(".zip");
    }

    // 搜索结果排序（ZIP 在 TXT 前面）
    private void sortSearchResult() {
        if (searchResultList.isEmpty()) return;

        List<File> folders = new ArrayList<>();
        List<File> zipFiles = new ArrayList<>();
        List<File> txtFiles = new ArrayList<>();

        for (File f : searchResultList) {
            if (f.isDirectory()) {
                folders.add(f);
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                zipFiles.add(f);
            } else if (f.getName().toLowerCase().endsWith(".txt")) {
                txtFiles.add(f);
            }
        }

        Collections.sort(folders, Comparator.comparing(File::getName));
        Collections.sort(txtFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        searchResultList.clear();
        searchResultList.addAll(folders);
        searchResultList.addAll(zipFiles);
        searchResultList.addAll(txtFiles);
    }

    // 初始化根文件夹
    private void initExternalBrain() {
        File sdCard = Environment.getExternalStorageDirectory();
        rootDirectory = new File(sdCard, ROOT_FOLDER_NAME); // 初始化根目录引用
        currentDirectory = rootDirectory;

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

        // 新增：修正根目录中已存在的文件（移除路径和时间戳）
        batchCorrectTxtFilepaths(currentDirectory);

        loadFileList();
        updateLevelHint();
    }

    // 加载文件列表（ZIP 在 TXT 前面）
    private void loadFileList() {
        fileList.clear();

        File[] files = currentDirectory.listFiles();
        if (files != null) {
            List<File> folders = new ArrayList<>();
            List<File> zipFiles = new ArrayList<>();
            List<File> txtFiles = new ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) {
                    folders.add(file);
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    zipFiles.add(file);
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    txtFiles.add(file);
                }
            }

            Collections.sort(folders, Comparator.comparing(File::getName));
            Collections.sort(txtFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            fileList.addAll(folders);
            fileList.addAll(zipFiles);
            fileList.addAll(txtFiles);
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
            hidePasteButton();
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

    // 创建测试TXT文件
    private void createTestFile() {
        File testFile = new File(currentDirectory, "使用说明与注意事项-25-09-20.txt");
        try {
            if (testFile.createNewFile()) {
                // 生成内容（根目录不添加路径标识）
                String content = "此文件编辑软件会自动增加文件路径和每次修改的时间戳，文件传播过程中可能会暴露此类信息。\n\n从屏幕左边缘向右划返回或退出。\n\n左上角添加新文件夹，可文件夹内创建文件夹。\n\n搜索功能只能搜索到当前文件夹里的内容。\n\n右下角加号可以新增TXT文件。\n\n长按文件和文件夹模块可以更名，分享发送给微信QQ好友，以及压缩文件夹。\n\n单击压缩文件解压文件，单击TXT文件打开。返回或关闭软件自动保存。\n\n此软件为清洁的不联网工具软件，查询更新功能，或者有增加功能的意见，直接找开发者。\n\n开发者各自媒体网名：“陈阳2077”邮箱必回：“137903874@qq.com”\n";

                FileOutputStream fos = new FileOutputStream(testFile);
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "创建测试文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 获取文件显示名称
    private String getDisplayName(File file) {
        if (file.isDirectory()) return file.getName();

        String fileName = file.getName();
        if (fileName.endsWith(".txt")) {
            // TXT列表显示：去除时间戳和后缀
            fileName = FILE_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
            return fileName.substring(0, fileName.lastIndexOf("."));
        } else if (fileName.endsWith(".zip")) {
            // ZIP列表显示：完整名称（含后缀）
            return fileName;
        }
        return fileName;
    }

    // 计算当前目录相对于根目录的路径
    public static String getRelativeDirPath(File dir, String rootName) {
        List<String> pathSegments = new ArrayList<>();
        File current = dir;
        // 从当前目录向上追溯，直到根目录“外置大脑”
        while (current != null && !current.getName().equals(rootName)) {
            pathSegments.add(current.getName());
            current = current.getParentFile();
        }
        // 反转列表，生成从根到当前的路径
        Collections.reverse(pathSegments);
        return String.join("/", pathSegments);
    }

    // ZIP文件单击事件 - 显示解压对话框（解压后修正路径）
    private void showZipExtractDialog(File zipFile) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("解压文件")
                .setMessage("是否将「" + zipFile.getName() + "」解压到当前文件夹？")
                .setPositiveButton("确定", (dialog, which) -> {
                    new Thread(() -> {
                        // 解压文件
                        boolean result = ZipUnzipUtil.unzipToCurrentDir(
                                zipFile.getAbsolutePath(),
                                currentDirectory.getAbsolutePath()
                        );
                        runOnUiThread(() -> {
                            if (result) {
                                Toast.makeText(this, "解压成功，正在修正文件路径...", Toast.LENGTH_SHORT).show();
                                // 解压成功后批量修正路径
                                new Thread(() -> {
                                    batchCorrectTxtFilepaths(currentDirectory);
                                    runOnUiThread(this::loadFileList);
                                }).start();
                            } else {
                                Toast.makeText(this, "解压失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ZIP解压逻辑
    private boolean extractZip(File zipFile, File targetDir) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[1024 * 4];

            while ((entry = zis.getNextEntry()) != null) {
                File entryFile = new File(targetDir, entry.getName());
                entryFile = getUniqueExtractFile(entryFile);

                if (entry.isDirectory()) {
                    if (!entryFile.mkdirs()) {
                        return false;
                    }
                } else {
                    File parentDir = entryFile.getParentFile();
                    if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                        return false;
                    }

                    try (FileOutputStream fos = new FileOutputStream(entryFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 处理解压重名
    private File getUniqueExtractFile(File targetFile) {
        if (!targetFile.exists()) return targetFile;

        File parentDir = targetFile.getParentFile();
        String name = targetFile.getName();
        String extension = "";
        int dotIndex = name.lastIndexOf(".");

        if (dotIndex != -1) {
            extension = name.substring(dotIndex);
            name = name.substring(0, dotIndex);
        }

        int counter = 1;
        File uniqueFile;
        do {
            String uniqueName = name + "(" + counter + ")" + extension;
            uniqueFile = new File(parentDir, uniqueName);
            counter++;
        } while (uniqueFile.exists());

        return uniqueFile;
    }

    // 文件夹长按选项
    private void showFolderOptions(File folder) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] options = {"重命名", "删除", "压缩为ZIP文件", "复制", "剪切"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    renameFile(folder);
                    break;
                case 1:
                    deleteFile(folder);
                    break;
                case 2:
                    zipFolder(folder);
                    break;
                case 3:
                    copyFileOrFolder(folder, false);
                    break;
                case 4:
                    copyFileOrFolder(folder, true);
                    break;
            }
        });
        builder.show();
    }

    // 文件长按选项
    private void showFileOptions(File file) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] options = {"重命名", "删除", "分享", "复制", "剪切"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    renameFile(file);
                    break;
                case 1:
                    deleteFile(file);
                    break;
                case 2:
                    shareFile(file);
                    break;
                case 3:
                    copyFileOrFolder(file, false);
                    break;
                case 4:
                    copyFileOrFolder(file, true);
                    break;
            }
        });
        builder.show();
    }

    // 压缩文件夹
    private void zipFolder(File folder) {
        Intent intent = new Intent(this, FileEditorActivity.class);
        intent.putExtra("ACTION_ZIP_FOLDER", true);
        intent.putExtra("FOLDER_PATH", folder.getAbsolutePath());
        startActivityForResult(intent, REQUEST_EDIT_FILE);
    }

    // 分享文件
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

    // 复制或剪切文件/文件夹
    private void copyFileOrFolder(File target, boolean isCut) {
        if (target == null || !target.exists()) {
            Toast.makeText(this, "文件不存在，无法操作", Toast.LENGTH_SHORT).show();
            return;
        }
        copiedFile = target;
        isCutOperation = isCut;
        showPasteButton();
        String tip = isCut ? "已剪切：" : "已复制：";
        Toast.makeText(this, tip + getDisplayName(target), Toast.LENGTH_SHORT).show();
    }

    // 显示粘贴按钮
    private void showPasteButton() {
        if (pasteButton != null && pasteButton.getParent() != null) {
            ((ViewGroup) pasteButton.getParent()).removeView(pasteButton);
        }

        pasteButton = LayoutInflater.from(this).inflate(R.layout.paste_button, null);
        Button btnPaste = pasteButton.findViewById(R.id.btn_paste);
        btnPaste.setOnClickListener(v -> performPaste());

        FrameLayout pasteContainer = findViewById(R.id.paste_container);
        pasteContainer.removeAllViews();
        pasteContainer.addView(pasteButton);
    }

    // 隐藏粘贴按钮
    private void hidePasteButton() {
        if (pasteButton != null && pasteButton.getParent() != null) {
            ((ViewGroup) pasteButton.getParent()).removeView(pasteButton);
        }
        copiedFile = null;
        isCutOperation = false;
        pasteButton = null;
    }

    // 执行粘贴操作（粘贴后修正路径）
    private void performPaste() {
        if (copiedFile == null || !copiedFile.exists()) {
            Toast.makeText(this, "粘贴内容已失效", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            return;
        }
        if (currentDirectory == null) {
            Toast.makeText(this, "当前目录不可用", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            return;
        }

        final File baseTargetFile = new File(currentDirectory, copiedFile.getName());
        final File finalTargetFile = getUniqueExtractFile(baseTargetFile);

        new Thread(() -> {
            boolean threadSuccess = false;
            try {
                if (copiedFile.isDirectory()) {
                    threadSuccess = copyDirectory(copiedFile, finalTargetFile);
                } else {
                    threadSuccess = copySingleFile(copiedFile, finalTargetFile);
                }

                if (threadSuccess && isCutOperation) {
                    deleteRecursive(copiedFile);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean successResult = threadSuccess;
            runOnUiThread(() -> {
                if (successResult) {
                    Toast.makeText(this, "粘贴成功，正在修正文件路径...", Toast.LENGTH_SHORT).show();
                    // 粘贴完成后批量修正路径
                    new Thread(() -> {
                        batchCorrectTxtFilepaths(finalTargetFile);
                        runOnUiThread(this::loadFileList);
                    }).start();
                } else {
                    Toast.makeText(this, "粘贴失败，请重试", Toast.LENGTH_SHORT).show();
                    loadFileList();
                }
                hidePasteButton();
            });
        }).start();
    }

    // 复制单个文件
    private boolean copySingleFile(File source, File target) throws IOException {
        if (!source.exists()) return false;
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        }
    }

    // 递归复制文件夹
    private boolean copyDirectory(File sourceDir, File targetDir) throws IOException {
        if (!sourceDir.isDirectory()) return false;
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return false;
        }
        File[] files = sourceDir.listFiles();
        if (files == null) return false;
        for (File file : files) {
            File targetFile = new File(targetDir, file.getName());
            if (file.isDirectory()) {
                if (!copyDirectory(file, targetFile)) {
                    return false;
                }
            } else {
                if (!copySingleFile(file, targetFile)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------- 路径修正核心方法（更新） ----------------------
    /**
     * 提取TXT文件中【】包裹的现有路径
     */
    private String extractExistingPathInFile(File file) {
        if (!file.getName().toLowerCase().endsWith(".txt")) return null;

        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        Matcher matcher = FILE_PATH_PATTERN.matcher(content.toString());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 修正单个TXT文件的路径和时间戳
     * 根目录文件：移除【】及路径，删除时间戳
     * 子目录文件：保留路径，正常处理
     */
    private boolean correctFilepathInTxt(File file) {
        if (!file.getName().toLowerCase().endsWith(".txt")) return false;

        // 检查文件是否在根目录
        boolean isInRootDir = file.getParentFile().equals(rootDirectory);

        StringBuilder newContent = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean pathReplaced = false;

            while ((line = br.readLine()) != null) {
                // 根目录文件处理
                if (isInRootDir) {
                    // 移除【】及其中的内容
                    line = FILE_PATH_PATTERN.matcher(line).replaceAll("");
                    // 移除时间戳
                    line = CONTENT_TIMESTAMP_PATTERN.matcher(line).replaceAll("");
                }
                // 子目录文件处理
                else if (!pathReplaced) {
                    String actualPath = getRelativeDirPath(file.getParentFile(), ROOT_FOLDER_NAME);
                    String existingPath = extractExistingPathInFile(file);

                    Matcher matcher = FILE_PATH_PATTERN.matcher(line);
                    if (matcher.find()) {
                        // 替换路径
                        if (!actualPath.equals(existingPath)) {
                            line = matcher.replaceFirst("【" + actualPath + "】");
                        }
                        pathReplaced = true;
                    } else if (TextUtils.isEmpty(existingPath) && line.trim().isEmpty()) {
                        // 添加路径标识
                        line = "【" + actualPath + "】";
                        pathReplaced = true;
                    }
                }

                newContent.append(line).append("\n");
            }

            // 子目录文件且未找到路径标识，在开头添加
            if (!isInRootDir && !pathReplaced) {
                String actualPath = getRelativeDirPath(file.getParentFile(), ROOT_FOLDER_NAME);
                newContent.insert(0, "【" + actualPath + "】\n\n");
            }

            // 写入修正后的内容
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(newContent.toString().getBytes(StandardCharsets.UTF_8));
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 批量修正文件夹内所有TXT文件的路径和时间戳
     */
    private void batchCorrectTxtFilepaths(File folder) {
        if (!folder.isDirectory()) return;

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                batchCorrectTxtFilepaths(file);
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                correctFilepathInTxt(file);
            }
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

            if (file.isDirectory()) {
                holder.itemView.setBackgroundResource(R.drawable.item_folder_rounded_bg);
                holder.ivIcon.setImageResource(R.drawable.ic_folder);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.black));
            } else {
                if (file.getName().toLowerCase().endsWith(".zip")) {
                    holder.ivIcon.setImageResource(R.drawable.ic_folder2);
                    holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                    holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.folderColor));
                } else {
                    holder.ivIcon.setImageResource(R.drawable.ic_file);
                    holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                    holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                }
            }

            holder.tvName.setText(getDisplayName(file));

            // 点击事件（TXT打开前先修正路径）
            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    isInSearchMode = false;
                    etSearch.setText("");
                    currentDirectory = file;
                    loadFileList();
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    hidePasteButton();
                    // 打开前修正路径
                    new Thread(() -> {
                        boolean corrected = correctFilepathInTxt(file);
                        runOnUiThread(() -> {
                            if (corrected) {
                                Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
                                editIntent.putExtra("file_path", file.getAbsolutePath());
                                editIntent.putExtra("is_pre_edit", false);
                                editIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
                                // 新增：传递是否为根目录
                                editIntent.putExtra("is_root_directory", file.getParentFile().equals(rootDirectory));
                                startActivityForResult(editIntent, REQUEST_EDIT_FILE);
                            } else {
                                Toast.makeText(MainActivity.this, "文件路径修正失败，无法打开", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    showZipExtractDialog(file);
                }
            });

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
