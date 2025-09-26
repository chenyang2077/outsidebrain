package com.example.outsidebrain;

import android.Manifest;
import java.text.ParseException;

// 如果还有 SimpleDateFormat 错误也需要这个
import java.text.SimpleDateFormat;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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
    private File rootDirectory;
    private EditText etSearch;
    private Button btnSearch;
    private boolean isInSearchMode = false;
    private ImageButton folderCreateBtn;
    private FloatingActionButton preEditFileBtn;
    private static final String ROOT_FOLDER_NAME = "外置大脑";
    // 匹配文件名中的时间戳（格式：-yyyy-MM-dd）
    // 文件名中隐藏的秒级时间戳正则（格式：_yyyyMMddHHmmss，使用下划线避免视觉干扰）
    // 替换原有的FILE_TIMESTAMP_PATTERN为新的秒级时间戳正则
// 原定义：public static final Pattern FILE_TIMESTAMP_PATTERN = Pattern.compile("-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
    public static final Pattern FILE_TIMESTAMP_PATTERN = Pattern.compile("_\\d{14}");  // 新：下划线+14位数字（yyyyMMddHHmmss）

    // 同时更新时间戳格式化工具
    public static final SimpleDateFormat SECOND_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
    public static final Pattern FILE_SECOND_TIMESTAMP_PATTERN = Pattern.compile("_\\d{14}");
    // 秒级时间戳生成器（精确到秒，格式：yyyyMMddHHmmss）

    // 仅匹配整行的路径标识（严格第一行使用）
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");
    // 匹配内容中的时间戳（格式：(yyyy-MM-dd)）
    private static final Pattern CONTENT_TIMESTAMP_PATTERN = Pattern.compile("\\(\\d{4}-\\d{2}-\\d{2}\\)");

    private File copiedFile;
    private boolean isCutOperation;
    private View pasteButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);
        fileRecyclerView = findViewById(R.id.file_list);
        preEditFileBtn = findViewById(R.id.add_button);
        folderCreateBtn = findViewById(R.id.icon2_btn);

        fileList = new ArrayList<>();
        searchResultList = new ArrayList<>();

        fileAdapter = new FileAdapter();
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileRecyclerView.setAdapter(fileAdapter);

        checkPermission();

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // 当点击软键盘的搜索图标时触发
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch();  // 调用原有的搜索逻辑
                    // 隐藏软键盘
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });


        btnSearch.setOnClickListener(v -> {
            hidePasteButton();
            performSearch();
        });

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

        etSearch.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hidePasteButton();
            }
            return false;
        });

        preEditFileBtn.setOnClickListener(v -> {
            hidePasteButton();
            startFilePreEdit();
        });
        folderCreateBtn.setOnClickListener(v -> {
            hidePasteButton();
            showFolderCreateDialog();
        });
    }

    // 修改TXT文件比较器，使用文件名中的秒级时间戳
    private class TxtTimestampComparator implements Comparator<File> {
        @Override
        public int compare(File f1, File f2) {
            long time1 = getSecondTimestampFromFileName(f1.getName());
            long time2 = getSecondTimestampFromFileName(f2.getName());

            // 优先按文件名中的秒级时间戳排序（新的在前）
            if (time1 != 0 && time2 != 0) {
                return Long.compare(time2, time1);
            }
            // 时间戳缺失时用文件修改时间兜底
            return Long.compare(f2.lastModified(), f1.lastModified());
        }
    }

    // 从文件名提取秒级时间戳
    private long getSecondTimestampFromFileName(String fileName) {
        if (!fileName.endsWith(".txt")) return 0;

        Matcher matcher = FILE_SECOND_TIMESTAMP_PATTERN.matcher(fileName);
        if (matcher.find()) {
            String timestampStr = matcher.group().replaceFirst("_", "");
            try {
                Date date = SECOND_TIMESTAMP_FORMAT.parse(timestampStr);
                return date != null ? date.getTime() : 0;
            } catch (ParseException e) {
                return 0;
            }
        }
        return 0;
    }

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
                Collections.sort(fileList, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        // 按文件名排序
                        return file1.getName().compareTo(file2.getName());
                    }
                });

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

    private void startFilePreEdit() {
        if (currentDirectory == null) {
            Toast.makeText(this, "目录未初始化，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent preEditIntent = new Intent(MainActivity.this, FileEditorActivity.class);
        preEditIntent.putExtra("current_dir_path", currentDirectory.getAbsolutePath());
        preEditIntent.putExtra("is_pre_edit", true);
        preEditIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
        preEditIntent.putExtra("is_root_directory", currentDirectory.equals(rootDirectory));
        startActivityForResult(preEditIntent, REQUEST_EDIT_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_FILE && resultCode == FileEditorActivity.RESULT_REFRESH) {
            loadFileList();
            hidePasteButton();
        }
    }

    private boolean isContentContainKeyword(File file, String keyword) {
        if (file.getName().toLowerCase().endsWith(".zip")) return false;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                String filteredLine = line;
                // 仅移除第一行的路径标识，中间行【】保留
                if (isFirstLine) {
                    filteredLine = FIRST_LINE_PATH_PATTERN.matcher(line).replaceAll("");
                    isFirstLine = false;
                }
                if (filteredLine.toLowerCase().contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

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
            } else if (isSupportedFile(file)) {
                boolean nameMatch = getDisplayName(file).toLowerCase().contains(keyword.toLowerCase());
                boolean contentMatch = isContentContainKeyword(file, keyword);
                if (nameMatch || contentMatch) {
                    searchResultList.add(file);
                }
            }
        }
    }

    private boolean isSupportedFile(File file) {
        String fileName = file.getName().toLowerCase();
        return fileName.endsWith(".txt") || fileName.endsWith(".zip");
    }

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

        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                // 按文件名排序
                return file1.getName().compareTo(file2.getName());
            }
        });
        Collections.sort(txtFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        searchResultList.clear();
        searchResultList.addAll(folders);
        searchResultList.addAll(zipFiles);
        searchResultList.addAll(txtFiles);
    }

    private void initExternalBrain() {
        File sdCard = Environment.getExternalStorageDirectory();
        rootDirectory = new File(sdCard, ROOT_FOLDER_NAME);
        currentDirectory = rootDirectory;

        if (!currentDirectory.exists()) {
            if (currentDirectory.mkdirs()) {
                Toast.makeText(this, "感谢世界有你", Toast.LENGTH_SHORT).show();
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

        batchCorrectTxtFilepaths(currentDirectory);
        loadFileList();
        updateLevelHint();
    }

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

            Collections.sort(fileList, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    // 按文件名排序
                    return file1.getName().compareTo(file2.getName());
                }
            });
            Collections.sort(txtFiles, new TxtTimestampComparator());
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

        // 【新增】切换目录后自动校验：若当前目录是被复制/剪切的文件夹，隐藏粘贴按钮
        if (copiedFile != null && currentDirectory.equals(copiedFile)) {
            hidePasteButton();
        }
    }

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

    // 新建测试文件时添加时间戳（仅新建时）
    private void createTestFile() {
        // 使用秒级时间戳格式
        String timestamp = "_" + SECOND_TIMESTAMP_FORMAT.format(new Date());
        File testFile = new File(currentDirectory, "使用说明与注意事项" + timestamp + ".txt");
        try {
            if (testFile.createNewFile()) {
                String content = "此文件编辑软件会自动增加每次修改的时间戳和文件路径，文件传播过程中可能会暴露此类信息。\n\n从屏幕左边缘向右划返回或退出。\n\n左上角添加新文件夹，可文件夹内创建文件夹。\n\n搜索功能只能搜索到当前文件夹里的内容。\n\n右下角加号可以新增TXT文件。\n\n长按文件和文件夹模块可以更名，分享发送给微信QQ好友，以及压缩文件夹。\n\n单击压缩文件解压文件，单击TXT文件打开。返回或关闭软件自动保存。\n\n此软件为清洁的不联网工具软件，查询更新功能，或者有增加功能的意见，直接找开发者。\n\n开发者各自媒体网名：“陈阳2077”邮箱必回：“137903874@qq.com”";

                FileOutputStream fos = new FileOutputStream(testFile);
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "创建测试文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 列表显示时移除时间戳（仅显示用，不修改实际文件名）
    private String getDisplayName(File file) {
        if (file.isDirectory()) return file.getName();

        String fileName = file.getName();
        if (fileName.endsWith(".txt")) {
            fileName = FILE_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
            return fileName.substring(0, fileName.lastIndexOf("."));
        } else if (fileName.endsWith(".zip")) {
            return fileName;
        }
        return fileName;
    }

    public static String getRelativeDirPath(File dir, String rootName) {
        List<String> pathSegments = new ArrayList<>();
        File current = dir;
        while (current != null && !current.getName().equals(rootName)) {
            pathSegments.add(current.getName());
            current = current.getParentFile();
        }
        Collections.reverse(pathSegments);
        return String.join("/", pathSegments);
    }

    private void showZipExtractDialog(File zipFile) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("解压文件")
                .setMessage("是否将「" + zipFile.getName() + "」解压到当前文件夹？")
                .setPositiveButton("确定", (dialog, which) -> {
                    new Thread(() -> {
                        boolean result = ZipUnzipUtil.unzipToCurrentDir(
                                zipFile.getAbsolutePath(),
                                currentDirectory.getAbsolutePath()
                        );
                        runOnUiThread(() -> {
                            if (result) {
                                Toast.makeText(this, "解压成功，正在修正文件路径...", Toast.LENGTH_SHORT).show();
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

    private void showFileOptions(File file) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] options = {"重命名", "删除", "分享", "复制", "剪切"};
        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    renameFile(file); // 允许用户手动重命名
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

    private void zipFolder(File folder) {
        Intent intent = new Intent(this, FileEditorActivity.class);
        intent.putExtra("ACTION_ZIP_FOLDER", true);
        intent.putExtra("FOLDER_PATH", folder.getAbsolutePath());
        startActivityForResult(intent, REQUEST_EDIT_FILE);
    }

    private void shareFile(File file) {
        Intent intent = new Intent(this, FileEditorActivity.class);
        intent.putExtra("ACTION_SHARE_FILE", true);
        intent.putExtra("FILE_PATH", file.getAbsolutePath());
        startActivityForResult(intent, REQUEST_EDIT_FILE);
    }

    // 【核心】用户手动重命名TXT文件（完全按用户输入，不自动添加时间戳）
    private void renameFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名");

        final EditText input = new EditText(this);
        // 显示无时间戳的名称（供用户编辑）
        input.setText(getDisplayName(file));
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 对于TXT文件，需要检查全局唯一性
            if (file.isFile() && file.getName().toLowerCase().endsWith(".txt")) {
                // 去除可能的扩展名
                String baseName = newName.toLowerCase().endsWith(".txt")
                        ? newName.substring(0, newName.lastIndexOf("."))
                        : newName;

                // 生成新的时间戳
                String timestamp = "_" + MainActivity.SECOND_TIMESTAMP_FORMAT.format(new Date());

                // 获取全局唯一文件名
                String uniqueName = UniqueFileNameHandler.getGlobalUniqueFileName(
                        rootDirectory,
                        file.getParentFile(),
                        baseName,
                        timestamp
                );

                File newFile = new File(file.getParentFile(), uniqueName);
                if (newFile.exists()) {
                    Toast.makeText(this, "名称已存在", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 执行重命名
                if (file.renameTo(newFile)) {
                    Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                // 非TXT文件直接使用用户输入的名称
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
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

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

    // 修改copyFileOrFolder方法，明确区分复制和剪切
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

    private void hidePasteButton() {
        if (pasteButton != null && pasteButton.getParent() != null) {
            ((ViewGroup) pasteButton.getParent()).removeView(pasteButton);
        }
        copiedFile = null;
        isCutOperation = false;
        pasteButton = null;
    }

    // 修改performPaste方法，处理剪切和复制的不同逻辑
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

        // 禁止在“被复制/剪切的文件夹”内部粘贴
        if (copiedFile.isDirectory() && currentDirectory.equals(copiedFile)) {
            Toast.makeText(this, "无法在当前复制/剪切的文件夹内粘贴，避免循环嵌套", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            return;
        }

        final File baseTargetFile = new File(currentDirectory, copiedFile.getName());

        new Thread(() -> {
            boolean threadSuccess = false;
            try {
                if (copiedFile.isDirectory()) {
                    // 文件夹操作：复制或剪切整个文件夹
                    threadSuccess = copyDirectory(copiedFile, baseTargetFile, isCutOperation);
                } else {
                    // 文件操作：根据是复制还是剪切采取不同策略
                    if (isCutOperation) {
                        // 剪切操作：直接移动，不改变文件名，只更新时间戳
                        threadSuccess = moveFileWithTimestampUpdate(copiedFile, baseTargetFile);
                    } else {
                        // 复制操作：创建副本，确保文件名唯一
                        threadSuccess = copyFileWithUniqueName(copiedFile, baseTargetFile);
                    }
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
                    String operation = isCutOperation ? "移动" : "复制";
                    Toast.makeText(this, operation + "成功，正在刷新列表...", Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        batchCorrectTxtFilepaths(currentDirectory);
                        runOnUiThread(this::loadFileList);
                    }).start();
                } else {
                    Toast.makeText(this, "操作失败，请重试", Toast.LENGTH_SHORT).show();
                    loadFileList();
                }
                hidePasteButton(); // 操作完成后自动隐藏按钮
            });
        }).start();
    }
    private boolean moveFileWithTimestampUpdate(File source, File target) throws IOException {
        if (!source.exists()) return false;

        // 对于TXT文件，仅更新时间戳，不改变文件名
        File finalTarget = target;
        if (source.getName().toLowerCase().endsWith(".txt")) {
            String fileName = source.getName();
            String cleanName = UniqueFileNameHandler.removeTimestamp(fileName);
            if (cleanName.toLowerCase().endsWith(".txt")) {
                cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
            }

            // 生成新的时间戳
            String newTimestamp = "_" + MainActivity.SECOND_TIMESTAMP_FORMAT.format(new Date());

            // 构建新文件名（保留原有序列号）
            String newFileName = cleanName + newTimestamp + ".txt";
            finalTarget = new File(target.getParentFile(), newFileName);
        }

        // 执行移动操作
        return source.renameTo(finalTarget);
    }

    // 复制文件并确保唯一文件名（复制操作）
    private boolean copyFileWithUniqueName(File source, File target) throws IOException {
        if (!source.exists()) return false;

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        // 对于TXT文件，确保全局唯一
        File finalTarget = target;
        if (source.getName().toLowerCase().endsWith(".txt")) {
            String cleanName = UniqueFileNameHandler.removeTimestamp(source.getName());
            if (cleanName.toLowerCase().endsWith(".txt")) {
                cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
            }

            // 生成新的时间戳
            String newTimestamp = "_" + MainActivity.SECOND_TIMESTAMP_FORMAT.format(new Date());

            // 获取全局唯一文件名
            String uniqueName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDirectory,
                    parent,
                    cleanName,
                    newTimestamp
            );

            finalTarget = new File(parent, uniqueName);
        }

        // 执行复制操作
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(finalTarget))) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        }
    }
    // 修改copySingleFile方法，处理TXT文件的重命名
    private boolean copySingleFile(File source, File target) throws IOException {
        if (!source.exists()) return false;

        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }

        // 对于TXT文件，确保全局唯一
        File finalTarget = target;
        if (source.getName().toLowerCase().endsWith(".txt")) {
            String cleanName = UniqueFileNameHandler.removeTimestamp(source.getName());
            if (cleanName.toLowerCase().endsWith(".txt")) {
                cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
            }

            String timestamp = UniqueFileNameHandler.removeTimestamp(
                    MainActivity.SECOND_TIMESTAMP_FORMAT.format(new Date())
            );

            String uniqueName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDirectory,
                    parent,
                    cleanName,
                    "_" + timestamp
            );

            finalTarget = new File(parent, uniqueName);
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(finalTarget))) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        }
    }


    private boolean copyDirectory(File sourceDir, File targetDir, boolean isCut) throws IOException {
        if (!sourceDir.isDirectory()) return false;
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return false;
        }

        File[] files = sourceDir.listFiles();
        if (files == null) return false;

        for (File file : files) {
            File targetFile = new File(targetDir, file.getName());
            if (file.isDirectory()) {
                if (!copyDirectory(file, targetFile, isCut)) {
                    return false;
                }
            } else {
                if (isCut) {
                    // 剪切：移动文件并更新时间戳
                    if (!moveFileWithTimestampUpdate(file, targetFile)) {
                        return false;
                    }
                } else {
                    // 复制：创建副本并确保唯一
                    if (!copyFileWithUniqueName(file, targetFile)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // 路径修正：仅处理第一行，不干扰中间内容
    private String extractFirstLinePath(File file) {
        if (!file.getName().toLowerCase().endsWith(".txt")) return null;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String firstLine = br.readLine();
            if (firstLine == null) return null;

            Matcher matcher = FIRST_LINE_PATH_PATTERN.matcher(firstLine);
            if (matcher.matches()) {
                return matcher.group().replace("【", "").replace("】", "").trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean correctFilepathInTxt(File file) {
        if (!file.getName().toLowerCase().endsWith(".txt")) return false;

        boolean isInRootDir = file.getParentFile().equals(rootDirectory);
        List<String> allLines = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                allLines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        StringBuilder newContent = new StringBuilder();
        String actualPath = getRelativeDirPath(file.getParentFile(), ROOT_FOLDER_NAME);
        boolean pathProcessed = false;

        for (int i = 0; i < allLines.size(); i++) {
            String line = allLines.get(i);
            if (i == 0 && !isInRootDir) {
                if (FIRST_LINE_PATH_PATTERN.matcher(line).matches()) {
                    newContent.append("【").append(actualPath).append("】\n");
                } else {
                    newContent.append("【").append(actualPath).append("】\n").append(line).append("\n");
                }
                pathProcessed = true;
            } else if (i == 0 && isInRootDir) {
                String processedLine = FIRST_LINE_PATH_PATTERN.matcher(line).replaceAll("");
                newContent.append(processedLine).append("\n");
                pathProcessed = true;
            } else {
                newContent.append(line).append("\n");
            }
        }

        if (!isInRootDir && allLines.isEmpty()) {
            newContent.append("【").append(actualPath).append("】\n");
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            String finalContent = newContent.toString().endsWith("\n")
                    ? newContent.toString().substring(0, newContent.length() - 1)
                    : newContent.toString();
            fos.write(finalContent.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

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

            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    if (copiedFile != null && file.equals(copiedFile)) {
                        hidePasteButton(); // 隐藏粘贴按钮
                        Toast.makeText(MainActivity.this, "禁止在当前复制/剪切的文件夹内粘贴，已自动隐藏粘贴功能", Toast.LENGTH_SHORT).show();
                    }
                    isInSearchMode = false;
                    etSearch.setText("");
                    currentDirectory = file;
                    loadFileList();
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    hidePasteButton();
                    new Thread(() -> {
                        boolean corrected = correctFilepathInTxt(file);
                        runOnUiThread(() -> {
                            if (corrected) {
                                Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
                                editIntent.putExtra("file_path", file.getAbsolutePath());
                                editIntent.putExtra("is_pre_edit", false);
                                editIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
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