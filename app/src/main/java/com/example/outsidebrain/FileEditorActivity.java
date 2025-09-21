package com.example.outsidebrain;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileEditorActivity extends AppCompatActivity {

    public static final int RESULT_REFRESH = 100; // 通知刷新列表的结果码
    private EditText etFileName;      // 标题编辑区（文件名）
    private EditText etContent;       // 内容编辑区
    private boolean isPreEdit;        // 是否为“预编辑”状态（首次创建）
    private File currentDir;         // 预编辑文件保存目录
    private File targetFile;          // 已有文件（非预编辑时使用）
    private boolean isSaved = true;   // 是否已保存
    private static final int MAX_TITLE_LEN = 31; // 内容截取最大长度

    // 1. 【修改】文件名时间戳：年份改为完整4位（yyyy）
    private static final SimpleDateFormat FILE_NAME_TIMESTAMP = new SimpleDateFormat("-yyyy-MM-dd", Locale.getDefault());
    // 2. 【修改】正文时间戳：年份改为完整4位（yyyy），格式保持 (yyyy-MM-dd)
    private static final SimpleDateFormat CONTENT_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    // 3. 【修改】时间戳匹配正则：适配4位年份（\\d{4} 表示4位数字）
    private static final Pattern CONTENT_TIMESTAMP_PATTERN = Pattern.compile("\\(\\d{4}-\\d{2}-\\d{2}\\)");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        // 1. 初始化控件
        etFileName = findViewById(R.id.et_file_name);
        etContent = findViewById(R.id.et_content);

        // 2. 获取传递参数
        String filePath = getIntent().getStringExtra("file_path");
        String currentDirPath = getIntent().getStringExtra("current_dir_path");
        isPreEdit = getIntent().getBooleanExtra("is_pre_edit", false);

        // 3. 优先处理压缩和分享意图（不加载编辑界面）
        handleZipAndShareIntent();

        // 4. 初始化编辑状态（仅当不是压缩/分享意图时执行）
        if (isPreEdit) {
            currentDir = new File(currentDirPath);
            etFileName.setHint(":标题");
            focusAndShowSoftInput(etContent);
        } else if (filePath != null) {
            targetFile = new File(filePath);
            loadExistingFileData(); // 加载TXT时显示全名
        }

        // 5. 监听文本变化，标记未保存
        setupTextChangeListeners();
    }

    // 处理压缩文件夹和分享文件的意图
    private void handleZipAndShareIntent() {
        Intent intent = getIntent();
        // 压缩文件夹
        if (intent.hasExtra("ACTION_ZIP_FOLDER")) {
            String folderPath = intent.getStringExtra("FOLDER_PATH");
            zipFolder(new File(folderPath));
            finish(); // 处理完成后关闭页面
        }
        // 分享文件（TXT或ZIP）
        else if (intent.hasExtra("ACTION_SHARE_FILE")) {
            String filePath = intent.getStringExtra("FILE_PATH");
            shareFile(new File(filePath));
            finish(); // 处理完成后关闭页面
        }
    }

    // 压缩文件夹为ZIP
    private void zipFolder(File folder) {
        if (!folder.exists() || !folder.isDirectory()) {
            Toast.makeText(this, "文件夹不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String zipFileName = folder.getName() + ".zip";
            File zipFile = new File(folder.getParentFile(), zipFileName);

            int counter = 1;
            while (zipFile.exists()) {
                zipFileName = folder.getName() + "(" + counter + ").zip";
                zipFile = new File(folder.getParentFile(), zipFileName);
                counter++;
            }

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                addFolderToZip(folder, folder.getName(), zos);
                Toast.makeText(this, "压缩成功：" + zipFile.getName(), Toast.LENGTH_SHORT).show();
                setResult(RESULT_REFRESH);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "压缩失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 递归将文件夹内容添加到ZIP
    private void addFolderToZip(File folder, String parentEntryName, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String newEntryName = parentEntryName + "/" + file.getName();
                addFolderToZip(file, newEntryName, zos);
            } else {
                ZipEntry zipEntry = new ZipEntry(parentEntryName + "/" + file.getName());
                zos.putNextEntry(zipEntry);

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        zos.write(buffer, 0, length);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    // 分享文件
    private void shareFile(File file) {
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(getMimeType(file.getName()));
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "分享文件");
            if (shareIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
                setResult(RESULT_REFRESH);
            } else {
                Toast.makeText(this, "未找到可分享的应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 根据文件名获取MIME类型
    private String getMimeType(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "application/octet-stream";

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.getDefault());
        switch (extension) {
            case "txt":
                return "text/plain";
            case "zip":
                return "application/zip";
            default:
                return "application/octet-stream";
        }
    }

    // 加载已有文件数据
    private void loadExistingFileData() {
        if (targetFile == null || !targetFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ZIP文件不支持编辑，直接返回
        if (targetFile.getName().toLowerCase().endsWith(".zip")) {
            Toast.makeText(this, "ZIP文件不支持编辑", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 加载标题：仅去除.txt后缀，保留时间戳
        String fileName = targetFile.getName();
        if (fileName.endsWith(".txt")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        etFileName.setText(fileName);

        // 加载文件内容（保留所有历史时间戳）
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            etContent.setText(content.toString().trim());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载内容失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 监听文本变化，标记未保存
    private void setupTextChangeListeners() {
        etFileName.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isSaved = false;
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        etContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isSaved = false;
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // 自动保存TXT文件逻辑
    private void autoSave() {
        if (isSaved) return;

        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString().trim();
        // 获取根文件夹名称
        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        if (rootFolderName == null) rootFolderName = "外置大脑";

        // 生成文件名时间戳
        String fileTimestamp = FILE_NAME_TIMESTAMP.format(new Date());

        if (isPreEdit) {
            // 首次创建文件：无时间戳
            if (inputTitle.isEmpty() && content.isEmpty()) {
                Toast.makeText(this, "未输入内容，放弃创建", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String finalTitle = inputTitle.isEmpty() ? getContentSubtitle(content) : inputTitle;
            finalTitle = removeOldTimestamp(finalTitle);

            targetFile = getUniqueFile(currentDir, finalTitle, fileTimestamp);

            try {
                if (targetFile.createNewFile()) {
                    String dirPath = MainActivity.getRelativeDirPath(currentDir, rootFolderName);
                    String finalContent;

                    if (TextUtils.isEmpty(dirPath)) {
                        finalContent = content;
                    } else {
                        finalContent = "【" + dirPath + "】\n" + content;
                    }

                    writeFileContent(targetFile, finalContent);
                    isSaved = true;
                    Toast.makeText(this, "文件创建成功：" + targetFile.getName(), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_REFRESH);
                    finish();
                } else {
                    Toast.makeText(this, "创建文件失败", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "创建异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            // 已有文件：追加时间戳（不修改原有）
            if (targetFile == null) return;

            // 处理标题重命名
            String inputTitleTrimmed = inputTitle.trim();
            String originalTitle = targetFile.getName().replace(".txt", "");

            if (!inputTitleTrimmed.isEmpty() && !inputTitleTrimmed.equals(originalTitle)) {
                String newTitle = removeOldTimestamp(inputTitleTrimmed) + fileTimestamp;
                File newFile = new File(targetFile.getParentFile(), newTitle + ".txt");
                if (targetFile.renameTo(newFile)) {
                    targetFile = newFile;
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
            }

            // 核心修改：追加时间戳（不修改原有，同一天不重复）
            String finalContent = addContentTimestamp(content);

            writeFileContent(targetFile, finalContent);
            isSaved = true;
            Toast.makeText(this, "文件更新成功", Toast.LENGTH_SHORT).show();
            setResult(RESULT_REFRESH);
            finish();
        }

        hideSoftInput();
    }

    // 4. 【核心修改】时间戳从内容前换到内容后，保留“同一天只加一次”逻辑
    private String addContentTimestamp(String originalContent) {
        // 生成当前完整年份时间戳 (yyyy-MM-dd)
        String currentTimeStamp = "(" + CONTENT_TIMESTAMP.format(new Date()) + ")";

        // 检查内容中是否已存在当天时间戳（任意位置）
        Matcher matcher = CONTENT_TIMESTAMP_PATTERN.matcher(originalContent);
        boolean hasSameTimestamp = false;

        while (matcher.find()) {
            String existing = matcher.group();
            if (existing.equals(currentTimeStamp)) {
                // 已存在当天时间戳，不添加
                hasSameTimestamp = true;
                break;
            }
        }

        if (hasSameTimestamp) {
            return originalContent;
        } else {
            // 5. 【修改】时间戳追加到内容末尾（空内容时直接加时间戳，非空时加换行分隔）
            if (TextUtils.isEmpty(originalContent)) {
                return currentTimeStamp;
            } else {
                return originalContent + "\n" + currentTimeStamp;
            }
        }
    }

    // 处理TXT文件重名
    private File getUniqueFile(File parentDir, String baseTitle, String timestamp) {
        String baseFileName = baseTitle + timestamp + ".txt";
        File file = new File(parentDir, baseFileName);
        int suffixCount = 0;

        while (file.exists()) {
            suffixCount++;
            String suffix = "+".repeat(suffixCount);
            String uniqueFileName = baseTitle + suffix + timestamp + ".txt";
            file = new File(parentDir, uniqueFileName);
        }
        return file;
    }

    // 从内容截取标题
    private String getContentSubtitle(String content) {
        if (content.isEmpty()) return "无内容文件";
        return content.length() <= MAX_TITLE_LEN ? content : content.substring(0, MAX_TITLE_LEN) + "…";
    }

    // 移除旧时间戳
    private String removeOldTimestamp(String fileName) {
        return MainActivity.FILE_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
    }

    // 写入文件内容
    private void writeFileContent(File file, String content) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "写入内容失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 聚焦并显示输入法
    private void focusAndShowSoftInput(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            editText.postDelayed(() -> imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT), 200);
        }
    }

    // 隐藏输入法
    private void hideSoftInput() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etContent.getWindowToken(), 0);
        }
    }

    // 返回键触发保存
    @Override
    public void onBackPressed() {
        autoSave();
        super.onBackPressed();
    }

    // 应用后台触发保存
    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing() && !isSaved) {
            autoSave();
        }
    }
}