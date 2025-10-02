package com.example.outsidebrain;

import android.Manifest;
import android.content.DialogInterface;
import android.util.Log;
import java.util.Random;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
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
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // 匹配文件名中的时间戳（格式：_yyyyMMddHHmmss）
    // 新正则（匹配“_随机字符串_时间戳”，其中随机字符串是6位字母数字）
    public static final Pattern FILE_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    // 定义时间戳正则（17位数字：yyyyMMddHHmmssSSS）
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{17}");
    public static final SimpleDateFormat MILLIS_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()); // 17位格式
    public static final Pattern FILE_MILLIS_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");

    // 仅匹配整行的路径标识（严格第一行使用）
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");
    // 匹配内容中的时间戳（格式：(yyyy-MM-dd)）
    private static final Pattern CONTENT_TIMESTAMP_PATTERN = Pattern.compile("\\(\\d{4}-\\d{2}-\\d{2}\\)");

    // 1. 目标格式正则：_随机字符(6位)_时间戳(17位) → 例：_abc123_20250928153022123
    public static final Pattern TARGET_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");

    // 2. 增量格式正则：目标格式 + _时间戳(17位) → 例：_abc123_20250928153022123_20250928153567890
    public static final Pattern INCREMENT_TIMESTAMP_PATTERN = Pattern.compile("(_[A-Za-z0-9]{6}_\\d{17})(_\\d{17})+$");

    // 3. 旧格式正则（兼容历史文件：可能是纯时间戳，如_20250928153022）
    public static final Pattern OLD_TIMESTAMP_PATTERN = Pattern.compile("_(\\d{14}|\\d{17})$"); // 14位秒级/17位毫秒级旧格式

    private File copiedFile;
    private boolean isCutOperation;
    private View pasteButton;

    // 图片文件扩展名
    private static final String[] IMAGE_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);
        fileRecyclerView = findViewById(R.id.file_list);
        preEditFileBtn = findViewById(R.id.add_button);

        // 在onCreate方法中添加
        ImageButton menuButton = findViewById(R.id.menu_btn);
        menuButton.setOnClickListener(v -> showPopupMenu(v));

        fileList = new ArrayList<>();
        searchResultList = new ArrayList<>();

        fileAdapter = new FileAdapter();
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileRecyclerView.setAdapter(fileAdapter);

        checkPermission();

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch();
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

    }

    // 在MainActivity类中添加返回根目录的方法
    private void navigateToRootDirectory() {
        // 检查根目录是否存在
        if (rootDirectory != null && rootDirectory.exists() && rootDirectory.isDirectory()) {
            // 退出搜索模式（如果处于搜索中）
            if (isInSearchMode) {
                isInSearchMode = false;
                etSearch.setText("");
            }

            // 切换到根目录
            currentDirectory = rootDirectory;
            loadFileList(); // 刷新文件列表
            updateLevelHint(); // 更新路径提示

            // 保存状态
            PreferenceUtils.saveLastPageType(this, "main");
            PreferenceUtils.saveLastFolderPath(this, rootDirectory.getAbsolutePath());

            Toast.makeText(this, "已返回根目录", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "根目录不存在", Toast.LENGTH_SHORT).show();
        }
    }

    private long getLastTimestampFromFileName(String fileName) {
        // 移除文件后缀
        String nameWithoutExt = fileName;
        if (fileName.toLowerCase().endsWith(".txt")) {
            nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        }

        // 匹配所有时间戳（17位数字）
        Matcher matcher = TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        long lastTimestamp = 0;

        // 找到最后一个匹配的时间戳
        while (matcher.find()) {
            try {
                long timestamp = Long.parseLong(matcher.group());
                if (timestamp > lastTimestamp) {
                    lastTimestamp = timestamp;
                }
            } catch (NumberFormatException e) {
                // 忽略无效的数字格式
            }
        }

        return lastTimestamp;
    }
    // 毫秒级TXT排序比较器
    private class TxtTimestampComparator implements Comparator<File> {
        @Override
        public int compare(File f1, File f2) {
            long time1 = getMillisTimestampFromFileName(f1.getName());
            long time2 = getMillisTimestampFromFileName(f2.getName());
            if (time1 != 0 && time2 != 0) {
                return Long.compare(time2, time1);
            }
            return Long.compare(f2.lastModified(), f1.lastModified());
        }
    }

    // 毫秒级时间戳提取
    private long getMillisTimestampFromFileName(String fileName) {
        if (!fileName.endsWith(".txt")) return 0;
        Matcher matcher = FILE_MILLIS_TIMESTAMP_PATTERN.matcher(fileName);
        if (matcher.find()) {
            String timestampStr = matcher.group().replaceFirst("_", "");
            try {
                Date date = MILLIS_TIMESTAMP_FORMAT.parse(timestampStr);
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
                // 修复排序对象错误：原代码用fileList排序，改为用siblings排序
                Collections.sort(sortedSiblings, new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
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
    // 弹出菜单方法
    private void showPopupMenu(View view) {
        // 创建弹出菜单，指定在左上角显示
        PopupMenu popupMenu = new PopupMenu(this, view, Gravity.TOP | Gravity.START);
        // 加载菜单资源
        popupMenu.getMenuInflater().inflate(R.menu.menu_popup, popupMenu.getMenu());

        // 修改showPopupMenu()方法中的菜单点击监听：
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_home) {
                // 调用返回根目录方法
                navigateToRootDirectory();
                return true;
            } else if (itemId == R.id.action_new_folder) {
                showFolderCreateDialog();
                return true;
            }
            return false;
        });

        // 显示菜单
        popupMenu.show();
    }


    private boolean isContentContainKeyword(File file, String keyword) {
        if (file.getName().toLowerCase().endsWith(".zip") || isImageFile(file) || isOtherFile(file)) {
            return false;
        }

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

    // 判断是否为图片文件
    private boolean isImageFile(File file) {
        if (file.isDirectory()) return false;

        String fileName = file.getName().toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    // 判断是否为其他文件（非文件夹、非ZIP、非TXT、非图片）
    private boolean isOtherFile(File file) {
        if (file.isDirectory()) return false;

        String fileName = file.getName().toLowerCase();
        // 明确排除已知类型
        return !(fileName.endsWith(".txt") || fileName.endsWith(".zip") || isImageFile(file));
    }

    private boolean isSupportedFile(File file) {
        return file.isDirectory() ||
                file.getName().toLowerCase().endsWith(".txt") ||
                file.getName().toLowerCase().endsWith(".zip") ||
                isImageFile(file) ||
                isOtherFile(file);
    }

    private void sortSearchResult() {
        if (searchResultList.isEmpty()) return;

        List<File> folders = new ArrayList<>();
        List<File> zipFiles = new ArrayList<>();
        List<File> txtFiles = new ArrayList<>();
        List<File> imageFiles = new ArrayList<>();
        List<File> otherFiles = new ArrayList<>();

        for (File f : searchResultList) {
            if (f.isDirectory()) {
                folders.add(f);
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                zipFiles.add(f);
            } else if (f.getName().toLowerCase().endsWith(".txt")) {
                txtFiles.add(f);
            } else if (isImageFile(f)) {
                imageFiles.add(f);
            } else {
                otherFiles.add(f);
            }
        }

        Collections.sort(folders, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                return file1.getName().compareTo(file2.getName());
            }
        });
        Collections.sort(txtFiles, new TxtTimestampComparator());
        Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        Collections.sort(imageFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        Collections.sort(otherFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        searchResultList.clear();
        searchResultList.addAll(folders);
        searchResultList.addAll(zipFiles);
        searchResultList.addAll(txtFiles);
        searchResultList.addAll(imageFiles);
        searchResultList.addAll(otherFiles);
    }

    private void initExternalBrain() {
        // 明确指定SD卡根目录作为基础路径
        File sdCardRoot = Environment.getExternalStorageDirectory();
        if (sdCardRoot == null || !sdCardRoot.exists()) {
            Toast.makeText(this, "未找到SD卡存储", Toast.LENGTH_SHORT).show();
            return;
        }

        // 构建根文件夹路径
        rootDirectory = new File(sdCardRoot, ROOT_FOLDER_NAME);
        currentDirectory = rootDirectory;

        // 检查文件夹是否存在
        if (!currentDirectory.exists()) {
            // 尝试直接创建文件夹
            boolean created = currentDirectory.mkdirs();

            // 如果创建失败，尝试使用兼容模式创建唯一文件夹
            if (!created) {
                File createdDir = FileUtils.createUniqueFolder(
                        sdCardRoot,  // 直接使用SD卡根目录作为父目录
                        ROOT_FOLDER_NAME
                );

                if (createdDir != null) {
                    currentDirectory = createdDir;
                    rootDirectory = createdDir;
                    Toast.makeText(this, "在SD卡中创建根文件夹: " + createdDir.getName(), Toast.LENGTH_SHORT).show();
                    createTestFile();
                } else {
                    // 最后尝试使用应用专属目录作为备选方案
                    File fallbackDir = new File(getExternalFilesDir(null), ROOT_FOLDER_NAME);
                    if (fallbackDir.mkdirs()) {
                        currentDirectory = fallbackDir;
                        rootDirectory = fallbackDir;
                        Toast.makeText(this, "已使用兼容模式创建文件夹", Toast.LENGTH_SHORT).show();
                        createTestFile();
                    } else {
                        Toast.makeText(this, "无法创建根文件夹，请检查存储权限", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } else {
                Toast.makeText(this, "感谢世界有你", Toast.LENGTH_SHORT).show();
                createTestFile();
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
        // 恢复最后状态
        restoreLastState();
    }

    // 恢复最后状态
    // 新增：按优先级恢复最后状态
    private void restoreLastState() {
        String lastPageType = PreferenceUtils.getLastPageType(this);

        // 1. 优先恢复图片查看状态 - 修复getLastImageFile方法名
        if ("image".equals(lastPageType)) {
            // 将getLastImageFile改为getLastViewedImage
            String lastImagePath = PreferenceUtils.getLastViewedImage(this);
            if (lastImagePath != null) {
                File imageFile = new File(lastImagePath);
                if (imageFile.exists() && isImageFile(imageFile)) {
                    currentDirectory = imageFile.getParentFile();
                    loadFileList();
                    openImageFile(imageFile);
                    return;
                }
            }
        }

        // 2. 恢复TXT编辑状态
        if ("editor".equals(lastPageType)) {
            String lastEditedFile = PreferenceUtils.getLastEditedFile(this);
            if (lastEditedFile != null) {
                File file = new File(lastEditedFile);
                if (file.exists() && file.getName().toLowerCase().endsWith(".txt")) {
                    currentDirectory = file.getParentFile();
                    loadFileList();
                    openFileEditor(file);
                    return;
                }
            }
        }

        // 3. 恢复文件夹浏览状态
        String lastFolderPath = PreferenceUtils.getLastFolderPath(this);
        if (lastFolderPath != null) {
            File lastFolder = new File(lastFolderPath);
            if (lastFolder.exists() && lastFolder.isDirectory() &&
                    lastFolder.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath())) {
                currentDirectory = lastFolder;
                loadFileList();
            }
        }
    }

    // 新增：打开文件编辑器的封装方法
    private void openFileEditor(File file) {
        Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
        editIntent.putExtra("file_path", file.getAbsolutePath());
        editIntent.putExtra("is_pre_edit", false);
        editIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
        editIntent.putExtra("is_root_directory", currentDirectory.equals(rootDirectory));
        startActivityForResult(editIntent, REQUEST_EDIT_FILE);
    }
    // 新增方法：恢复最后访问的位置
    // 在restoreLastLocation方法中正确使用变量和方法
    // 2. 在恢复状态时正确获取图片路径
    // 2. 状态恢复方法（完全替换原方法）
    private void restoreLastLocation() {
        String lastPageType = PreferenceUtils.getLastPageType(this);

        // 优先恢复图片查看状态
        if ("image".equals(lastPageType)) {
            // 使用正确的方法名获取图片路径
            String lastImagePath = PreferenceUtils.getLastViewedImage(this); // 正确方法
            if (lastImagePath != null && !lastImagePath.isEmpty()) {
                File imageFile = new File(lastImagePath);
                if (imageFile.exists() && isImageFile(imageFile)) {
                    currentDirectory = imageFile.getParentFile();
                    loadFileList();
                    openImageFile(imageFile);
                    return;
                } else {
                    PreferenceUtils.clearLastViewedImage(this);
                }
            }
        }

        // 恢复编辑文件状态
        if ("editor".equals(lastPageType)) {
            String lastEditedFile = PreferenceUtils.getLastEditedFile(this);
            if (lastEditedFile != null && !lastEditedFile.isEmpty()) {
                File file = new File(lastEditedFile);
                if (file.exists() && file.isFile() && file.getName().toLowerCase().endsWith(".txt")) {
                    currentDirectory = file.getParentFile();
                    loadFileList();

                    Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
                    editIntent.putExtra("file_path", file.getAbsolutePath());
                    editIntent.putExtra("is_pre_edit", false);
                    editIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
                    editIntent.putExtra("is_root_directory", currentDirectory.equals(rootDirectory));
                    startActivityForResult(editIntent, REQUEST_EDIT_FILE);

                    return;
                } else {
                    PreferenceUtils.clearLastEditedFile(this);
                }
            }
        }

        // 最后恢复文件夹浏览状态
        String lastFolderPath = PreferenceUtils.getLastFolderPath(this);
        if (lastFolderPath != null && !lastFolderPath.isEmpty()) {
            File lastFolder = new File(lastFolderPath);
            if (lastFolder.exists() && lastFolder.isDirectory() &&
                    lastFolder.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath())) {
                currentDirectory = lastFolder;
                loadFileList();
            }
        }
    }


    // 重写onPause方法，保存当前文件夹位置
    @Override
    protected void onPause() {
        super.onPause();
        // 当在主页面暂停时，更新状态类型为main
        String currentPageType = PreferenceUtils.getLastPageType(this);
        if (!"editor".equals(currentPageType) && !"image".equals(currentPageType)) {
            PreferenceUtils.saveLastPageType(this, "main");
            if (!isInSearchMode && currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }
        }
    }

    // 修改onActivityResult方法，处理从编辑页面返回的情况
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EDIT_FILE) {
            // 从TXT编辑页面返回，更新状态为main
            PreferenceUtils.saveLastPageType(this, "main");
            // 刷新文件列表
            loadFileList();
        }
    }


    private void loadFileList() {
        fileList.clear();

        File[] files = currentDirectory.listFiles();
        if (files != null) {
            List<File> folders = new ArrayList<>();
            List<File> zipFiles = new ArrayList<>();
            List<File> txtFiles = new ArrayList<>();
            List<File> imageFiles = new ArrayList<>();
            List<File> otherFiles = new ArrayList<>();

            for (File file : files) {
                if (file.isDirectory()) {
                    folders.add(file);
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    zipFiles.add(file);
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    txtFiles.add(file);
                } else if (isImageFile(file)) {
                    imageFiles.add(file);
                } else {
                    otherFiles.add(file);
                }
            }

            // 文件夹按名称排序
            Collections.sort(folders, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    return file1.getName().compareTo(file2.getName());
                }
            });

            // TXT文件按最后一个时间戳倒序排序（最新的在前）
            Collections.sort(txtFiles, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    long time1 = getLastTimestampFromFileName(file1.getName());
                    long time2 = getLastTimestampFromFileName(file2.getName());
                    // 时间戳大的排在前面（最新的文件优先）
                    return Long.compare(time2, time1);
                }
            });

            // 其他文件类型保持原排序（按修改时间倒序）
            Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Collections.sort(imageFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Collections.sort(otherFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            // 合并所有文件列表
            fileList.addAll(folders);
            fileList.addAll(zipFiles);
            fileList.addAll(txtFiles);
            fileList.addAll(imageFiles);
            fileList.addAll(otherFiles);
        }

        if (!isInSearchMode) {
            // 直接使用原File列表，通过getDisplayName方法处理显示名称
            fileAdapter.setData(fileList);
        }

        if (TextUtils.isEmpty(etSearch.getText().toString().trim())) {
            updateLevelHint();
        }

        // 切换目录后自动校验：若当前目录是被复制/剪切的文件夹，隐藏粘贴按钮
        if (copiedFile != null && currentDirectory.equals(copiedFile)) {
            hidePasteButton();
        }
    }
    /**
     * 格式化TXT文件名用于显示（隐藏随机字符和所有时间戳）
     * 处理格式：
     * - 基础名_随机字符_时间戳.txt → 基础名.txt
     * - 基础名_随机字符_时间戳1_时间戳2.txt → 基础名.txt
     */

    private void showRenameDialog(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名");

        String originalName = file.getName();
        String displayName = formatFileNameForDisplay(originalName);
        String editTextContent = displayName;

        EditText input = new EditText(this);
        input.setText(editTextContent);
        builder.setView(input);

        builder.setPositiveButton("确定", (dialog, which) -> {
            String newBaseName = input.getText().toString().trim();
            if (newBaseName.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            if (originalName.toLowerCase().endsWith(".txt")) {
                handleTxtRename(file, newBaseName);
            } else {
                // 正确调用：仅传递文件名参数
                renameFile(file, newBaseName + getFileExtension(originalName));
            }
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }
    private void renameFile(File file, String newFileName) {
        File parentDir = file.getParentFile();
        File newFile = new File(parentDir, newFileName);

        if (newFile.exists()) {
            Toast.makeText(this, "文件名已存在", Toast.LENGTH_SHORT).show();
            return;
        }

        if (file.renameTo(newFile)) {
            Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
            loadFileList();
        } else {
            Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }
    private void handleTxtRename(File file, String newBaseName) {
        String originalName = file.getName();
        String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf("."));
        String newTimestamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String newFileName;

        // 匹配随机字符和时间戳结构
        Pattern pattern = Pattern.compile("(_[A-Za-z0-9]+)(_\\d+)+$");
        Matcher matcher = pattern.matcher(nameWithoutExt);

        if (!matcher.find()) {
            // 无随机字符和时间戳：添加随机字符+时间戳+.txt后缀
            String randomStr = generateRandomString();
            newFileName = newBaseName + "_" + randomStr + "_" + newTimestamp + ".txt";
        } else {
            String timestampPart = matcher.group();
            String[] parts = timestampPart.split("_");

            if (parts.length == 3) {
                // 随机字符+1个时间戳：增加新时间戳+.txt后缀
                newFileName = newBaseName + timestampPart + "_" + newTimestamp + ".txt";
            } else {
                // 随机字符+2个以上时间戳：刷新最后一个时间戳+.txt后缀
                String prefix = timestampPart.substring(0, timestampPart.lastIndexOf("_"));
                newFileName = newBaseName + prefix + "_" + newTimestamp + ".txt";
            }
        }

        renameFile(file, newFileName);
    }

    /**
     * 格式化TXT文件名用于显示（隐藏随机字符和所有时间戳）
     * 处理格式：
     * - 基础名_随机字符_时间戳.txt → 基础名
     * - 基础名_随机字符_时间戳1_时间戳2.txt → 基础名
     */
    private String formatFileNameForDisplay(String originalFileName) {
        // 只处理TXT文件
        if (!originalFileName.toLowerCase().endsWith(".txt")) {
            return originalFileName;
        }

        // 分离文件名和扩展名
        String nameWithoutExt = originalFileName;
        if (originalFileName.contains(".")) {
            int extIndex = originalFileName.lastIndexOf(".");
            nameWithoutExt = originalFileName.substring(0, extIndex);
        }

        // 关键修复：使用已定义的正则常量，确保匹配所有时间戳格式
        // 先移除增量格式（随机字符+多个时间戳）
        String processedName = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt).replaceAll("");
        // 再移除基础格式（随机字符+单个时间戳）
        processedName = TARGET_TIMESTAMP_PATTERN.matcher(processedName).replaceAll("");
        // 最后处理旧格式（仅时间戳）
        processedName = OLD_TIMESTAMP_PATTERN.matcher(processedName).replaceAll("");

        return processedName;
    }


    /**
     * 内部类：用于存储文件及其显示名称
     */
    private class FileDisplayItem {
        private File file;
        private String displayName;

        public FileDisplayItem(File file, String displayName) {
            this.file = file;
            this.displayName = displayName;
        }

        public File getFile() {
            return file;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    @Override
    public void onBackPressed() {
        if (isInSearchMode) {
            // 原有：退出搜索模式逻辑
            isInSearchMode = false;
            etSearch.setText("");
            etSearch.clearFocus();
            fileAdapter.setData(fileList);
            Toast.makeText(this, "已退出搜索", Toast.LENGTH_SHORT).show();
            hidePasteButton();

            // 新增：退出搜索后，记录当前文件夹状态（确保状态正确）
            PreferenceUtils.saveLastPageType(this, "main");
            if (currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }

        } else if (currentDirectory != null && !currentDirectory.getName().equals(ROOT_FOLDER_NAME)) {
            // 原有：返回上一级文件夹逻辑
            currentDirectory = currentDirectory.getParentFile();
            etSearch.clearFocus();
            loadFileList();

            // 新增：返回上一级文件夹后，记录当前文件夹状态
            PreferenceUtils.saveLastPageType(this, "main");
            if (currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }

        } else {
            // 原有：根目录下按返回键，执行系统默认退出逻辑
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
        // 使用FileUtils的全局查重方法生成唯一文件夹
        File newFolder = FileUtils.createUniqueFolder(currentDirectory, name);
        if (newFolder != null) {
            Toast.makeText(this, "文件夹创建成功", Toast.LENGTH_SHORT).show();
            loadFileList();
        } else {
            Toast.makeText(this, "文件夹创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 新建测试文件时添加时间戳（仅新建时）
    // 新建测试文件（毫秒级时间戳）
    private void createTestFile() {
        String randomStr = generateRandomString(); // 生成随机字符串
        String timestamp = "_" + MILLIS_TIMESTAMP_FORMAT.format(new Date());
        // 文件名格式：标题_随机字符串_时间戳.txt
        File testFile = new File(currentDirectory, "使用说明与注意事项_" + randomStr + timestamp + ".txt");
        try {
            if (testFile.createNewFile()) {
                String content = "此软件主要提供TXT文件的整理、搜索、压缩、发送，文字图片阅览等。\n\n此文件编辑软件会自动增加每次修改的时间戳和文件路径，文件传播过程中可能会暴露此类信息。\n\n从屏幕左边缘向右划返回或退出。\n\n左上角添加新文件夹，可文件夹内创建文件夹。\n\n搜索功能只能搜索到当前文件夹里的内容。\n\n右下角加号可以新增TXT文件。\n\n长按文件和文件夹模块可以更名，分享发送给微信QQ好友，以及压缩文件夹。\n\n单击压缩文件解压文件，单击TXT文件打开。返回或关闭软件自动保存。\n\n此软件为清洁的不联网工具软件，查询更新功能，或者有增加功能的意见，直接找开发者。\n\n开发者各自媒体网名：“陈阳2077”邮箱必回：“137903874@qq.com”";

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
        String fileName = file.getName();

        // 目录直接返回名称（无后缀）
        if (file.isDirectory()) {
            return fileName;
        }

        // 处理TXT文件：先移除时间戳，再去除后缀
        if (fileName.endsWith(".txt")) {
            // 移除所有时间戳格式（目标格式+增量格式）
            fileName = MainActivity.INCREMENT_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
            fileName = MainActivity.TARGET_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
            // 去除.txt后缀
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1) {
                fileName = fileName.substring(0, dotIndex);
            }
            return fileName;
        }

        // 处理ZIP文件：只去除.zip后缀
        if (fileName.endsWith(".zip")) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        }

        // 处理图片文件：去除图片后缀（如.jpg/.png等）
        if (isImageFile(file)) {
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1) {
                return fileName.substring(0, dotIndex);
            }
            return fileName;
        }

        // 其他文件类型：统一去除后缀
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex != -1) {
            return fileName.substring(0, dotIndex);
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
                        // 使用FileUtils调用解压，保持逻辑统一
                        boolean result = FileUtils.unzipFile(zipFile, currentDirectory);
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
    // 重命名方法，避免与系统方法冲突
    private void deleteSelectedFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除")
                .setMessage("确定要删除 " + getDisplayName(file) + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (deleteRecursive(file)) {  // 使用本地递归删除方法
                        Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
                        loadFileList();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 递归删除实现
    private boolean deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
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
                    confirmFileDeletion(folder); // 同样使用新的删除确认方法
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
    // 3. 全新的删除确认方法（确保只定义一次）
    private void confirmFileDeletion(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认删除")
                .setMessage("确定要删除 " + getDisplayName(file) + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    if (performRecursiveDeletion(file)) { // 使用全新的执行方法名
                        Toast.makeText(this, "删除成功", Toast.LENGTH_SHORT).show();
                        loadFileList();
                    } else {
                        Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private boolean performRecursiveDeletion(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!performRecursiveDeletion(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private void showFileOptions(File file) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] options = new String[]{"重命名", "删除", "分享", "复制", "剪切"};

        builder.setItems(options, (dialog, which) -> {
            switch (which) {
                case 0:
                    renameFile(file);
                    break;
                case 1:
                    // 调用唯一的删除确认方法
                    confirmFileDeletion(file); // 使用全新的方法名，彻底避免冲突
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



    // 重命名的删除方法，避免与系统的 deleteFile(String) 冲突


    // 重命名删除方法，避免与系统的deleteFile(String)冲突


    // 递归删除文件/文件夹的实现


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

    // 【核心】用户手动重命名文件
    private void renameFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名");

        final EditText input = new EditText(this);
        final String originalFileName = file.getName();
        String displayName = originalFileName;
        final String fileExtension;
        final boolean isTxtFile;
        final boolean isFolder;
        final String originalTxtCoreName;

        isFolder = file.isDirectory();
        isTxtFile = !isFolder && originalFileName.toLowerCase().endsWith(".txt");

        // 关键改进：准确提取TXT文件的核心名称
        originalTxtCoreName = isTxtFile ? extractCoreName(originalFileName) : "";

        // 处理非TXT文件的扩展名
        if (!isTxtFile && !isFolder) {
            int dotIndex = originalFileName.lastIndexOf(".");
            if (dotIndex != -1 && dotIndex < originalFileName.length() - 1) {
                displayName = originalFileName.substring(0, dotIndex);
                fileExtension = originalFileName.substring(dotIndex);
            } else {
                fileExtension = "";
            }
        } else {
            fileExtension = "";
        }

        // 设置显示名称（TXT文件显示核心名称）
        if (isTxtFile) {
            displayName = originalTxtCoreName;
        }

        input.setText(displayName);
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 处理TXT文件重命名逻辑
            if (isTxtFile) {
                // 提取新名称的核心部分（去除可能的.txt后缀）
                String newCoreName = newName.toLowerCase().endsWith(".txt")
                        ? newName.substring(0, newName.lastIndexOf("."))
                        : newName;

                // 检查核心名称是否变化
                if (newCoreName.equals(originalTxtCoreName)) {
                    Toast.makeText(this, "名称未更改", Toast.LENGTH_SHORT).show();
                    return;
                }

                // 解析原始文件名中的时间戳结构
                String nameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf("."));
                String randomStr = "";
                List<String> timestamps = new ArrayList<>();
                Matcher targetMatcher = TARGET_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
                Matcher incrementMatcher = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt);

                // 提取现有随机字符和时间戳
                if (incrementMatcher.find()) {
                    // 匹配到：_随机字符_时间戳1_时间戳2...
                    String[] parts = incrementMatcher.group().split("_");
                    if (parts.length >= 3) {
                        randomStr = parts[1]; // 提取随机字符
                        for (int i = 2; i < parts.length; i++) {
                            timestamps.add(parts[i]); // 提取所有时间戳
                        }
                    }
                } else if (targetMatcher.find()) {
                    // 匹配到：_随机字符_时间戳
                    String[] parts = targetMatcher.group().split("_");
                    if (parts.length >= 3) {
                        randomStr = parts[1]; // 提取随机字符
                        timestamps.add(parts[2]); // 提取时间戳
                    }
                }

                // 生成新时间戳
                String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
                StringBuilder timestampSuffix = new StringBuilder();

                // 根据时间戳数量处理不同逻辑
                if (timestamps.isEmpty()) {
                    // 无时间戳：添加随机字符+新时间戳
                    randomStr = generateRandomString();
                    timestampSuffix.append("_").append(randomStr).append("_").append(newTimestamp);
                } else if (timestamps.size() == 1) {
                    // 有1个时间戳：保留原随机字符和时间戳，添加新时间戳
                    timestampSuffix.append("_").append(randomStr)
                            .append("_").append(timestamps.get(0))
                            .append("_").append(newTimestamp);
                } else {
                    // 有2个及以上时间戳：保留原随机字符和前n-1个时间戳，更新最后一个时间戳
                    timestampSuffix.append("_").append(randomStr);
                    for (int i = 0; i < timestamps.size() - 1; i++) {
                        timestampSuffix.append("_").append(timestamps.get(i));
                    }
                    timestampSuffix.append("_").append(newTimestamp);
                }

                // 生成全局唯一文件名
                String uniqueName = UniqueFileNameHandler.getGlobalUniqueFileName(
                        rootDirectory,
                        file.getParentFile(),
                        newCoreName,
                        timestampSuffix.toString()
                );

                // 执行重命名
                File newFile = new File(file.getParentFile(), uniqueName);
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
            } else {
                // 非TXT文件处理逻辑
                final String finalNewName = (!isFolder && !fileExtension.isEmpty())
                        ? newName + fileExtension
                        : newName;

                if (finalNewName.equals(originalFileName)) {
                    Toast.makeText(this, "名称未更改", Toast.LENGTH_SHORT).show();
                    return;
                }

                String uniqueName = FileUtils.generateUniqueFolderName(
                        file.getParentFile(),
                        finalNewName
                );
                File newFile = new File(file.getParentFile(), uniqueName);

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
    /**
     * 提取TXT文件的核心名称
     * 规则：以下横线+随机字符为界，之前的部分是核心名称
     * 随机字符定义为6位字母数字组合
     */
    private String extractCoreName(String fileName) {
        // 先去除文件扩展名
        String nameWithoutExt = fileName;
        if (fileName.toLowerCase().endsWith(".txt")) {
            nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        }

        // 定义下划线+随机字符的模式（6位字母数字）
        Pattern pattern = Pattern.compile("_[A-Za-z0-9]{6}");
        Matcher matcher = pattern.matcher(nameWithoutExt);

        // 查找第一个匹配的下划线+随机字符模式
        if (matcher.find()) {
            // 提取模式之前的部分作为核心名称
            String coreName = nameWithoutExt.substring(0, matcher.start());
            // 处理可能的空值
            return coreName.trim().isEmpty() ? "未命名文件" : coreName;
        } else {
            // 如果没有找到模式，整个名称都是核心名称
            return nameWithoutExt.trim().isEmpty() ? "未命名文件" : nameWithoutExt;
        }
    }


    // 在 FileUtils 类中
    public static boolean deleteFolder(File file) {  // 将 private 改为 public
        // 方法实现保持不变
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteFolder(child)) {
                        return false;
                    }
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

    // 新增：带TXT全局查重和时间戳更新的文件夹复制方法
    /**
     * 复制文件夹（递归处理内部TXT文件，确保全域唯一）
     * 规则：复制的TXT文件必须包含"_随机字符_时间戳"格式
     */
    private String getUniqueFolderName(File parentDir, String baseName) {
        if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory()) {
            return baseName;
        }

        // 检查基础名称是否已存在
        File baseFile = new File(parentDir, baseName);
        if (!baseFile.exists()) {
            return baseName;
        }

        // 查找最大序列号
        int maxSerial = 0;
        Pattern pattern = Pattern.compile("^" + Pattern.quote(baseName) + "\\((\\d+)\\)$");
        File[] files = parentDir.listFiles(File::isDirectory);

        if (files != null) {
            for (File file : files) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.matches()) {
                    try {
                        int serial = Integer.parseInt(matcher.group(1));
                        if (serial > maxSerial) {
                            maxSerial = serial;
                        }
                    } catch (NumberFormatException e) {
                        // 忽略非数字序列号的文件夹
                    }
                }
            }
        }

        // 返回下一个序列号的名称
        return baseName + "(" + (maxSerial + 1) + ")";
    }
    private boolean copyFolderWithTxtGlobalCheck(File sourceFolder, File targetParent) throws IOException {
        // 生成带序列号的唯一文件夹名
        String baseName = sourceFolder.getName();
        String uniqueFolderName = getUniqueFolderName(targetParent, baseName);
        File targetFolder = new File(targetParent, uniqueFolderName);

        // 创建目标文件夹
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            Log.e("CopyFolder", "创建目标文件夹失败: " + targetFolder.getAbsolutePath());
            return false;
        }

        // 初始化本次复制的序列号（每次复制操作从0000开始）
        int sequenceNumber = 0;

        File[] files = sourceFolder.listFiles();
        if (files == null) {
            return true; // 空文件夹复制成功
        }

        for (File sourceFile : files) {
            if (sourceFile.isDirectory()) {
                // 递归复制子文件夹，传递当前序列号并接收更新后的值
                sequenceNumber = copySubFolderWithTxtCheck(sourceFile, targetFolder, sequenceNumber);
            } else if (sourceFile.getName().toLowerCase().endsWith(".txt")) {
                // 处理TXT文件：使用序列号替代时间戳中的小时和分钟
                String originalName = sourceFile.getName();
                String cleanName = UniqueFileNameHandler.removeTimestamp(originalName);
                if (cleanName.toLowerCase().endsWith(".txt")) {
                    cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
                }

                // 生成随机字符（6位）
                String randomStr = UniqueFileNameHandler.generateRandomString();

                // 生成基础时间戳（yyyyMMddHHmmssSSS）
                String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

                // 替换时间戳中的小时和分钟部分（第9-13位：HHmm）为4位序列号
                // 格式说明：yyyyMMddHHmmssSSS → 前8位是日期，9-12位是小时分钟，后续是秒和毫秒
                if (baseTimestamp.length() >= 12) {
                    // 保留前8位（日期）+ 替换9-12位为序列号 + 保留剩余部分（秒和毫秒）
                    String datePart = baseTimestamp.substring(0, 8);
                    String timeRemaining = baseTimestamp.substring(12);
                    // 格式化序列号为4位数字（0000-9999循环）
                    String sequenceStr = String.format(Locale.getDefault(), "%04d", sequenceNumber % 10000);
                    // 拼接新时间戳
                    String newTimestamp = datePart + sequenceStr + timeRemaining;

                    // 生成时间戳后缀
                    String timestampSuffix = "_" + randomStr + "_" + newTimestamp;

                    // 生成全局唯一文件名
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            timestampSuffix
                    );

                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (!copyFileContent(sourceFile, targetFile)) {
                        return false;
                    }

                    // 序列号自增（超过9999自动循环）
                    sequenceNumber++;
                } else {
                    // 时间戳格式异常时使用默认逻辑
                    Log.w("CopyFolder", "时间戳格式异常，使用默认命名");
                    String timestampSuffix = "_" + randomStr + "_" + baseTimestamp;
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            timestampSuffix
                    );
                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (!copyFileContent(sourceFile, targetFile)) {
                        return false;
                    }
                }
            } else {
                // 非TXT文件：当前目录内查重
                File targetFile = new File(targetFolder, sourceFile.getName());
                File uniqueTargetFile = getNonConflictFile(targetFile);
                if (!copyFileContent(sourceFile, uniqueTargetFile)) {
                    return false;
                }
            }
        }
        return true;
    }
    /**
     * 递归复制子文件夹，保持序列号连续
     * @param sourceFolder 源文件夹
     * @param targetParent 目标父文件夹
     * @param startSequence 起始序列号
     * @return 更新后的序列号
     */
    private int copySubFolderWithTxtCheck(File sourceFolder, File targetParent, int startSequence) throws IOException {
        int currentSequence = startSequence;

        // 创建子文件夹
        String uniqueFolderName = getUniqueFolderName(targetParent, sourceFolder.getName());
        File targetFolder = new File(targetParent, uniqueFolderName);
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            Log.e("CopySubFolder", "创建子文件夹失败: " + targetFolder.getAbsolutePath());
            return currentSequence;
        }

        File[] files = sourceFolder.listFiles();
        if (files == null) {
            return currentSequence;
        }

        for (File sourceFile : files) {
            if (sourceFile.isDirectory()) {
                // 递归处理子文件夹，更新序列号
                currentSequence = copySubFolderWithTxtCheck(sourceFile, targetFolder, currentSequence);
            } else if (sourceFile.getName().toLowerCase().endsWith(".txt")) {
                // 处理TXT文件，逻辑与父文件夹一致
                String originalName = sourceFile.getName();
                String cleanName = UniqueFileNameHandler.removeTimestamp(originalName);
                if (cleanName.toLowerCase().endsWith(".txt")) {
                    cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
                }

                String randomStr = UniqueFileNameHandler.generateRandomString();
                String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

                if (baseTimestamp.length() >= 12) {
                    String datePart = baseTimestamp.substring(0, 8);
                    String timeRemaining = baseTimestamp.substring(12);
                    String sequenceStr = String.format(Locale.getDefault(), "%04d", currentSequence % 10000);
                    String newTimestamp = datePart + sequenceStr + timeRemaining;

                    String timestampSuffix = "_" + randomStr + "_" + newTimestamp;
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            timestampSuffix
                    );

                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (copyFileContent(sourceFile, targetFile)) {
                        currentSequence++; // 仅在复制成功时自增序列号
                    }
                } else {
                    Log.w("CopySubFolder", "时间戳格式异常，使用默认命名");
                    String timestampSuffix = "_" + randomStr + "_" + baseTimestamp;
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            timestampSuffix
                    );
                    File targetFile = new File(targetFolder, uniqueFileName);
                    copyFileContent(sourceFile, targetFile);
                }
            } else {
                // 处理非TXT文件
                File targetFile = new File(targetFolder, sourceFile.getName());
                File uniqueTargetFile = getNonConflictFile(targetFile);
                copyFileContent(sourceFile, uniqueTargetFile);
            }
        }

        return currentSequence;
    }


    // 修改performPaste方法，处理剪切和复制的不同逻辑
    // 修改performPaste方法中的文件操作逻辑
    /**
     * 执行粘贴操作（处理复制/剪切逻辑）
     */
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

        // 路径校验
        String originalParentPath = copiedFile.getParentFile().getAbsolutePath();
        String targetPath = currentDirectory.getAbsolutePath();

        // 剪切操作的子目录校验
        if (isCutOperation) {
            if (originalParentPath.equals(targetPath)) {
                Toast.makeText(this, "剪切目标与原位置相同，无需操作", Toast.LENGTH_SHORT).show();
                hidePasteButton();
                return;
            }
            if (copiedFile.isDirectory() && targetPath.startsWith(copiedFile.getAbsolutePath() + File.separator)) {
                Toast.makeText(this, "无法剪切到子目录，避免循环嵌套", Toast.LENGTH_SHORT).show();
                hidePasteButton();
                return;
            }
        } else {
            // 复制操作的子目录校验
            if (copiedFile.isDirectory() && targetPath.startsWith(copiedFile.getAbsolutePath() + File.separator)) {
                Toast.makeText(this, "无法复制到子目录，避免循环嵌套", Toast.LENGTH_SHORT).show();
                hidePasteButton();
                return;
            }
        }

        // 执行实际操作
        new Thread(() -> {
            boolean threadSuccess = false;
            try {
                if (copiedFile.isDirectory()) {
                    if (isCutOperation) {
                        // 剪切文件夹：使用剪切专用的TXT处理逻辑
                        threadSuccess = moveFolderWithTxtUpdate(copiedFile, currentDirectory);
                    } else {
                        // 复制文件夹：使用复制专用的TXT处理逻辑
                        threadSuccess = copyFolderWithTxtGlobalCheck(copiedFile, currentDirectory);
                    }
                } else {
                    // 单个文件操作
                    if (isCutOperation) {
                        // 单个TXT文件剪切
                        threadSuccess = moveFileWithTimestampUpdate(copiedFile, new File(currentDirectory, copiedFile.getName()));
                    } else {
                        // 单个TXT文件复制（保持原有逻辑）
                        threadSuccess = copyFileWithUniqueName(copiedFile, new File(currentDirectory, copiedFile.getName()));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean successResult = threadSuccess;
            runOnUiThread(() -> {
                if (successResult) {
                    String operation = isCutOperation ? "移动" : "复制";
                    String targetName = getUniqueFolderName(currentDirectory, copiedFile.getName());
                    Toast.makeText(this, operation + "成功：" + targetName, Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "操作失败，请重试", Toast.LENGTH_SHORT).show();
                }
                hidePasteButton();
            });
        }).start();
    }
    /**
     * 移动文件夹并更新内部TXT文件的时间戳
     * 规则：
     * 1. 有随机字符+1个时间戳 → 增加一个时间戳
     * 2. 有随机字符+2个以上时间戳 → 刷新最后一个时间戳
     */
    private boolean moveFolderWithTxtUpdate(File sourceFolder, File targetParent) throws IOException {
        // 生成带序列号的唯一文件夹名
        String baseName = sourceFolder.getName();
        String uniqueFolderName = getUniqueFolderName(targetParent, baseName);
        File targetFolder = new File(targetParent, uniqueFolderName);

        // 确保目标文件夹存在
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            Log.e("MoveFolder", "创建目标文件夹失败: " + targetFolder.getAbsolutePath());
            return false;
        }

        // 处理内部文件
        File[] files = sourceFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 递归处理子文件夹
                    if (!moveFolderWithTxtUpdate(file, new File(targetFolder, file.getName()))) {
                        return false;
                    }
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    // 剪切操作：按规则更新TXT文件名
                    String originalName = file.getName();
                    String newFileName = processTxtForCutOperation(originalName);

                    // 执行重命名
                    File targetFile = new File(targetFolder, newFileName);
                    if (!file.renameTo(targetFile)) {
                        // 重命名失败时尝试复制后删除原文件
                        if (copyFileContent(file, targetFile)) {
                            file.delete();
                        } else {
                            Log.e("MoveFolder", "处理TXT文件失败: " + originalName);
                            return false;
                        }
                    }
                } else {
                    // 非TXT文件直接移动
                    File targetFile = new File(targetFolder, file.getName());
                    if (!file.renameTo(targetFile)) {
                        if (copyFileContent(file, targetFile)) {
                            file.delete();
                        } else {
                            Log.e("MoveFolder", "处理文件失败: " + file.getName());
                            return false;
                        }
                    }
                }
            }
        }

        // 删除原文件夹（确保为空）
        return deleteEmptyDirectory(sourceFolder);
    }

    private boolean deleteEmptyDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }

        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            return false; // 目录不为空
        }

        return dir.delete();
    }
    /**
     * 剪切操作专用：处理TXT文件名的随机字符和时间戳
     */
    private String processTxtForCutOperation(String fileName) {
        if (!fileName.toLowerCase().endsWith(".txt")) {
            return fileName;
        }

        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        String ext = fileName.substring(fileName.lastIndexOf("."));
        String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

        // 1. 检查是否有随机字符+时间戳结构
        Matcher targetMatcher = TARGET_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (!targetMatcher.find()) {
            // 无随机字符和时间戳：添加完整结构
            String randomStr = UniqueFileNameHandler.generateRandomString();
            return nameWithoutExt + "_" + randomStr + "_" + newTimestamp + ext;
        }

        // 2. 检查是否有多个时间戳（随机字符+2个以上时间戳）
        Matcher incrementMatcher = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (incrementMatcher.find()) {
            // 刷新最后一个时间戳
            return incrementMatcher.replaceAll("$1_" + newTimestamp) + ext;
        }

        // 3. 只有随机字符+1个时间戳：增加一个新时间戳
        return nameWithoutExt + "_" + newTimestamp + ext;
    }
    /**
     * 根据规则更新TXT文件名的时间戳
     */
    private String updateTxtTimestamp(String fileName) {
        if (!fileName.toLowerCase().endsWith(".txt")) {
            return fileName;
        }

        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        String ext = fileName.substring(fileName.lastIndexOf("."));
        String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

        // 匹配增量格式（随机字符+多个时间戳）
        Matcher incrementMatcher = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (incrementMatcher.find()) {
            // 刷新最后一个时间戳
            return incrementMatcher.replaceAll("$1_" + newTimestamp) + ext;
        }

        // 匹配目标格式（随机字符+1个时间戳）
        Matcher targetMatcher = TARGET_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (targetMatcher.find()) {
            // 增加一个时间戳
            return nameWithoutExt + "_" + newTimestamp + ext;
        }

        // 不匹配任何格式：添加完整时间戳（随机字符+时间戳）
        String randomStr = UniqueFileNameHandler.generateRandomString();
        return nameWithoutExt + "_" + randomStr + "_" + newTimestamp + ext;
    }





    /**
     * 复制文件夹（递归处理内部TXT文件，确保全域唯一）
     * 复用解压/TXT文件复制的查重逻辑，保持一致性
     */
    private boolean copyFolderWithTxtCheck(File sourceFolder, File targetParent) throws IOException {
        // 文件夹自身查重逻辑保持不变（当前目录内查重，生成唯一文件夹名）
        String targetFolderName = FileUtils.generateUniqueFolderName(
                targetParent,
                sourceFolder.getName()
        );
        File targetFolder = new File(targetParent, targetFolderName);
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            Log.e("MainActivity", "创建目标文件夹失败: " + targetFolder.getAbsolutePath());
            return false;
        }

        File[] files = sourceFolder.listFiles();
        if (files == null) {
            return true; // 空文件夹复制成功
        }

        for (File sourceFile : files) {
            if (sourceFile.isDirectory()) {
                // 子文件夹查重逻辑完全不变，递归调用保持原有规则
                if (!copyFolderWithTxtCheck(sourceFile, targetFolder)) {
                    return false;
                }
            } else {
                // 仅修改TXT文件的处理逻辑
                if (sourceFile.getName().toLowerCase().endsWith(".txt")) {
                    // TXT文件使用全局查重（与单独复制TXT逻辑一致）
                    String originalName = sourceFile.getName();
                    String cleanName = UniqueFileNameHandler.removeTimestamp(originalName);
                    if (cleanName.toLowerCase().endsWith(".txt")) {
                        cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
                    }

                    String randomStr = generateRandomString(); // 生成随机字符串
                    String newTimestamp = "_" + MILLIS_TIMESTAMP_FORMAT.format(new Date());
                    // 文件名格式：标题_随机字符串_时间戳.txt
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            "_" + randomStr + newTimestamp
                    );

                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (!copyFileContent(sourceFile, targetFile)) {
                        return false;
                    }
                } else {
                    // 非TXT文件保持原有查重逻辑不变
                    File targetFile = new File(targetFolder, sourceFile.getName());
                    File uniqueTargetFile = getNonConflictFile(targetFile);
                    if (!copyFileContent(sourceFile, uniqueTargetFile)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
    // 生成固定长度的随机字符串（6位字母数字组合）
    private String generateRandomString() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int index = new Random().nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 辅助方法：复制文件内容（复用，避免重复代码）
     */
    private boolean copyFileContent(File source, File target) throws IOException {
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

    /**
     * 辅助方法：非TXT文件的当前文件夹内查重（复用已有逻辑，确保一致性）
     */
    private File getNonConflictFile(File targetFile) {
        if (!targetFile.exists()) {
            return targetFile;
        }

        String baseName = targetFile.getName();
        String extension = "";
        int dotIndex = baseName.lastIndexOf(".");
        if (dotIndex != -1) {
            baseName = baseName.substring(0, dotIndex);
            extension = targetFile.getName().substring(dotIndex);
        }

        int counter = 1;
        while (true) {
            String newName = baseName + "(" + counter + ")" + extension;
            File newFile = new File(targetFile.getParentFile(), newName);
            if (!newFile.exists()) {
                return newFile;
            }
            counter++;
        }
    }
    // 打开图片文件
    // 在打开图片时正确记录状态
    // 1. 在打开图片时记录状态
    private void openImageFile(File imageFile) {
        try {
            // 记录图片查看状态 - 使用正确的方法名
            PreferenceUtils.saveLastPageType(this, "image");
            PreferenceUtils.saveLastViewedImage(this, imageFile.getAbsolutePath()); // 正确方法
            PreferenceUtils.saveLastFolderPath(this, imageFile.getParentFile().getAbsolutePath());

            // 原有打开图片的代码
            Uri imageUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    imageFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(imageUri, "image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "没有找到可以打开图片的应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "打开图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 提示不支持的文件类型
    private void showUnsupportedFileMessage() {
        Toast.makeText(this, "暂不支持此文件类型", Toast.LENGTH_SHORT).show();
    }

    private boolean moveFileWithTimestampUpdate(File source, File target) throws IOException {
        if (!source.exists()) return false;

        // 对于TXT文件，按剪切规则处理
        if (source.getName().toLowerCase().endsWith(".txt")) {
            String originalName = source.getName();
            String newFileName = processTxtForCutOperation(originalName);
            File finalTarget = new File(target.getParentFile(), newFileName);

            // 执行移动
            if (source.renameTo(finalTarget)) {
                return true;
            } else {
                // 移动失败时尝试复制后删除
                if (copyFileContent(source, finalTarget)) {
                    return source.delete();
                }
                return false;
            }
        }

        // 非TXT文件直接移动
        return source.renameTo(target);
    }

    // 复制文件并确保唯一文件名（复制操作）
    // 复制文件并确保唯一文件名（复制操作）- 核心修改
    private boolean copyFileWithUniqueName(File source, File target) throws IOException {
        // 检查源文件是否存在
        if (!source.exists()) {
            Log.e("FileCopy", "源文件不存在: " + source.getAbsolutePath());
            return false;
        }

        // 确保目标目录存在
        File parentDir = target.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            Log.e("FileCopy", "无法创建目标目录: " + parentDir.getAbsolutePath());
            return false;
        }

        File finalTargetFile = target;
        String sourceFileName = source.getName();

        // 仅处理TXT文件的时间戳格式和唯一性
        if (sourceFileName.toLowerCase().endsWith(".txt")) {
            // 1. 按规则处理源文件名（升级旧格式/更新时间戳）
            String processedFileName = UniqueFileNameHandler.TimestampHandler.processTxtFileName(sourceFileName);

            // 2. 提取核心标题（移除所有时间戳相关部分）
            String coreTitle = processedFileName;
            if (coreTitle.toLowerCase().endsWith(".txt")) {
                coreTitle = coreTitle.substring(0, coreTitle.lastIndexOf("."));
            }
            // 移除目标格式和增量时间戳
            coreTitle = MainActivity.INCREMENT_TIMESTAMP_PATTERN.matcher(coreTitle).replaceAll("");
            coreTitle = MainActivity.TARGET_TIMESTAMP_PATTERN.matcher(coreTitle).replaceAll("");

            // 3. 生成符合规则的时间戳后缀（_随机字符串_时间戳）
            String randomStr = UniqueFileNameHandler.TimestampHandler.generateRandomString();
            String newTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();
            String timestampSuffix = "_" + randomStr + "_" + newTimestamp;

            // 4. 全局查重生成唯一文件名
            String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDirectory,  // 全局根目录（外置大脑）
                    parentDir,      // 目标父目录
                    coreTitle,      // 核心标题（无时间戳）
                    timestampSuffix // 时间戳后缀（含随机字符串）
            );
            finalTargetFile = new File(parentDir, uniqueFileName);
            Log.d("FileCopy", "TXT文件复制 - 原名称: " + sourceFileName + " → 新名称: " + uniqueFileName);
        } else {
            // 非TXT文件：仅在当前目录查重（不处理时间戳）
            finalTargetFile = getNonConflictFile(target);
            Log.d("FileCopy", "非TXT文件复制 - 原名称: " + sourceFileName + " → 新名称: " + finalTargetFile.getName());
        }

        // 执行文件复制操作
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(finalTargetFile))) {

            byte[] buffer = new byte[1024 * 4]; // 4KB缓冲区
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush(); // 确保所有数据写入磁盘
            return true;
        } catch (IOException e) {
            Log.e("FileCopy", "复制文件失败: " + e.getMessage(), e);
            // 复制失败时删除可能创建的空文件
            if (finalTargetFile.exists() && finalTargetFile.length() == 0) {
                finalTargetFile.delete();
            }
            throw e; // 向上传递异常，让调用者处理
        }
    }

    // 辅助方法：非TXT文件的当前目录查重



    // 在MainActivity中添加批量升级方法（可在onCreate或按钮点击时调用）
    public void batchUpgradeOldTxtFiles() {
        File rootDir = new File(Environment.getExternalStorageDirectory(), "外置大脑");
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            Log.d("MainActivity", "根目录不存在，无需升级");
            return;
        }

        // 递归遍历所有子目录
        recursiveUpgradeTxtFiles(rootDir);
        Toast.makeText(this, "历史TXT文件格式升级完成", Toast.LENGTH_SHORT).show();
    }

    // 递归处理每个TXT文件
    private void recursiveUpgradeTxtFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                recursiveUpgradeTxtFiles(file); // 递归子目录
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                // 调用处理方法升级格式
                String oldName = file.getName();
                String newName = UniqueFileNameHandler.TimestampHandler.processTxtFileName(oldName);
                if (!oldName.equals(newName)) {
                    File newFile = new File(file.getParentFile(), newName);
                    if (file.renameTo(newFile)) {
                        Log.d("MainActivity", "升级文件：" + oldName + "→" + newName);
                    } else {
                        Log.w("MainActivity", "升级失败：" + oldName);
                    }
                }
            }
        }
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

    private void loadImageThumbnail(File imageFile, ImageView imageView) {
        // 使用Glide库加载缩略图
        Glide.with(MainActivity.this)
                .load(imageFile)
                .thumbnail(0.1f) // 加载原图的1/10作为缩略图
                .centerCrop()
                .error(R.drawable.ic_image) // 加载失败时显示默认图片图标
                .into(imageView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String lastPageType = PreferenceUtils.getLastPageType(this);
        // 从任何子页面返回都更新为main状态
        if ("image".equals(lastPageType) || "editor".equals(lastPageType)) {
            PreferenceUtils.saveLastPageType(this, "main");
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

            // 严格按类型设置样式（保持原有逻辑）
            if (file.isDirectory()) {
                holder.itemView.setBackgroundResource(R.drawable.item_folder_rounded_bg);
                holder.ivIcon.setImageResource(R.drawable.ic_folder);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.black));
            } else if (file.getName().toLowerCase().endsWith(".zip")) {
                holder.ivIcon.setImageResource(R.drawable.ic_folder2);
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.folderColor));
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                holder.ivIcon.setImageResource(R.drawable.ic_file);
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            } else if (isImageFile(file)) {
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                loadImageThumbnail(file, holder.ivIcon);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_other_file);
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            }

            // 关键修改：使用处理后的显示名称
            holder.tvName.setText(formatFileNameForDisplay(file.getName()));

            // 保持原有的点击事件逻辑
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
                    // 记录文件夹浏览状态
                    PreferenceUtils.saveLastPageType(MainActivity.this, "main");
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, file.getAbsolutePath());
                    // 清除其他类型状态
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, null);
                    PreferenceUtils.saveLastViewedImage(MainActivity.this, null);
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    hidePasteButton();
                    // 记录文件路径
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, file.getAbsolutePath());
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, file.getParentFile().getAbsolutePath());

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

                    // 记录TXT编辑状态（仅在打开编辑页面时）
                    PreferenceUtils.saveLastPageType(MainActivity.this, "editor");
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    showZipExtractDialog(file);
                } else if (isImageFile(file)) {
                    hidePasteButton();
                    openImageFile(file);
                    // 记录图片查看状态
                    PreferenceUtils.saveLastPageType(MainActivity.this, "image");
                    PreferenceUtils.saveLastViewedImage(MainActivity.this, file.getAbsolutePath());
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, file.getParentFile().getAbsolutePath());
                    // 清除其他类型状态
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, null);
                } else {
                    hidePasteButton();
                    showUnsupportedFileMessage();
                }
            });

            // 保持原有的长按事件逻辑
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
