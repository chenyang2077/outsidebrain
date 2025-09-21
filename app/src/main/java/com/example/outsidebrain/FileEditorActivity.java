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
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileEditorActivity extends AppCompatActivity {

    public static final int RESULT_REFRESH = 100;
    private EditText etFileName;
    private EditText etContent;
    private boolean isPreEdit;
    private File currentDir;
    private File targetFile;
    private boolean isSaved = true;
    private static final int MAX_TITLE_LEN = 31;

    // 文件名时间戳（仅新建文件时使用）
    private static final SimpleDateFormat FILE_NAME_TIMESTAMP = new SimpleDateFormat("-yyyy-MM-dd", Locale.getDefault());
    // 正文时间戳（yyyy-MM-dd）
    private static final SimpleDateFormat CONTENT_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    // 匹配最后一行有效时间戳（整行：(yyyy-MM-dd)）
    private static final Pattern LAST_LINE_TIMESTAMP_PATTERN = Pattern.compile("^\\(\\d{4}-\\d{2}-\\d{2}\\)$");
    // 匹配第一行有效路径标识（整行：【路径】）
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        etFileName = findViewById(R.id.et_file_name);
        etContent = findViewById(R.id.et_content);

        String filePath = getIntent().getStringExtra("file_path");
        String currentDirPath = getIntent().getStringExtra("current_dir_path");
        isPreEdit = getIntent().getBooleanExtra("is_pre_edit", false);

        handleZipAndShareIntent();

        if (isPreEdit) {
            currentDir = new File(currentDirPath);
            etFileName.setHint(":标题");
            focusAndShowSoftInput(etContent);
        } else if (filePath != null) {
            targetFile = new File(filePath);
            loadExistingFileData(); // 加载已有文件的标题和内容
        }

        setupTextChangeListeners();
    }

    // 压缩和分享相关方法（完全保留原逻辑）
    private void handleZipAndShareIntent() {
        Intent intent = getIntent();
        if (intent.hasExtra("ACTION_ZIP_FOLDER")) {
            String folderPath = intent.getStringExtra("FOLDER_PATH");
            zipFolder(new File(folderPath));
            finish();
        } else if (intent.hasExtra("ACTION_SHARE_FILE")) {
            String filePath = intent.getStringExtra("FILE_PATH");
            shareFile(new File(filePath));
            finish();
        }
    }

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

    // 加载文件数据（保留原格式，标题显示去除后缀的文件名）
    private void loadExistingFileData() {
        if (targetFile == null || !targetFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (targetFile.getName().toLowerCase().endsWith(".zip")) {
            Toast.makeText(this, "ZIP文件不支持编辑", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 加载标题：去除 .txt 后缀（保留用户之前设置的完整标题，包括手动添加的时间戳）
        String fileName = targetFile.getName();
        if (fileName.endsWith(".txt")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        etFileName.setText(fileName); // 用户可在此输入框修改标题

        // 加载内容：完全保留原格式（含空行、中间【】等）
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            // 去除末尾多余换行符
            String finalContent = content.toString().endsWith("\n")
                    ? content.toString().substring(0, content.length() - 1)
                    : content.toString();
            etContent.setText(finalContent);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载内容失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupTextChangeListeners() {
        // 标题变化时标记为未保存
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

        // 内容变化时标记为未保存
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

    // 【核心修复】自动保存逻辑：新增“编辑已有文件时的标题修改”处理
    private void autoSave() {
        if (isSaved) return;

        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString();
        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);

        if (rootFolderName == null) rootFolderName = "外置大脑";
        String fileTimestamp = FILE_NAME_TIMESTAMP.format(new Date());

        // 1. 新建文件逻辑（保留自动加时间戳）
        if (isPreEdit) {
            if (TextUtils.isEmpty(inputTitle) && TextUtils.isEmpty(content.trim())) {
                Toast.makeText(this, "未输入内容，放弃创建", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            String finalTitle = TextUtils.isEmpty(inputTitle) ? getContentSubtitle(content) : inputTitle;
            finalTitle = removeOldTimestamp(finalTitle);
            targetFile = getUniqueFile(currentDir, finalTitle, fileTimestamp); // 自动加时间戳

            try {
                if (targetFile.createNewFile()) {
                    String dirPath = MainActivity.getRelativeDirPath(currentDir, rootFolderName);
                    String finalContent = isRootDirectory
                            ? addContentTimestamp(content)
                            : "【" + dirPath + "】\n" + addContentTimestamp(content);
                    writeFileContent(targetFile, finalContent);
                    isSaved = true;
                    Toast.makeText(this, "文件创建成功", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_REFRESH);
                    finish();
                } else {
                    Toast.makeText(this, "创建文件失败", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "创建异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        // 2. 编辑已有文件逻辑（新增标题修改处理）
        else {
            if (targetFile == null) return;

            // 【新增】处理用户手动修改的标题（文件名）
            String originalFileName = targetFile.getName(); // 原始完整文件名（含.txt）
            String originalTitle = originalFileName.endsWith(".txt")
                    ? originalFileName.substring(0, originalFileName.lastIndexOf("."))
                    : originalFileName;

            // 若用户修改了标题（输入框内容与原标题不同）
            if (!TextUtils.isEmpty(inputTitle) && !inputTitle.equals(originalTitle)) {
                // 新文件名 = 用户输入标题 + .txt（保留后缀，不自动加时间戳）
                String newFileName = inputTitle + ".txt";
                File newFile = new File(targetFile.getParentFile(), newFileName);

                // 处理重名：若新文件名已存在，添加序号（如“笔记(1).txt”）
                newFile = getUniqueEditFile(newFile);

                // 执行重命名
                if (targetFile.renameTo(newFile)) {
                    targetFile = newFile; // 更新目标文件为新文件
                    Toast.makeText(this, "标题修改成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "标题修改失败（可能被其他应用占用）", Toast.LENGTH_SHORT).show();
                }
            }

            // 处理文件内容（路径标识、时间戳，逻辑不变）
            String finalContent;
            if (isRootDirectory) {
                finalContent = addContentTimestamp(content);
            } else {
                String[] allLines = content.split("\n", -1);
                ArrayList<String> userLines = new ArrayList<>();
                for (int i = 0; i < allLines.length; i++) {
                    String line = allLines[i];
                    if (i == 0 && FIRST_LINE_PATH_PATTERN.matcher(line).matches()) {
                        continue;
                    }
                    userLines.add(line);
                }
                String userContent = TextUtils.join("\n", userLines);
                String contentWithTimestamp = addContentTimestamp(userContent);
                String dirPath = MainActivity.getRelativeDirPath(targetFile.getParentFile(), rootFolderName);
                finalContent = "【" + dirPath + "】\n" + contentWithTimestamp;
            }

            writeFileContent(targetFile, finalContent);
            isSaved = true;
            Toast.makeText(this, "文件更新成功", Toast.LENGTH_SHORT).show();
            setResult(RESULT_REFRESH);
            finish();
        }

        hideSoftInput();
    }

    // 【新增】处理编辑已有文件时的重名问题（避免覆盖已有文件）
    private File getUniqueEditFile(File targetFile) {
        if (!targetFile.exists()) return targetFile;

        File parentDir = targetFile.getParentFile();
        String fileName = targetFile.getName();
        String extension = "";
        int dotIndex = fileName.lastIndexOf(".");

        if (dotIndex != -1) {
            extension = fileName.substring(dotIndex); // .txt
            fileName = fileName.substring(0, dotIndex); // 去除后缀的标题
        }

        int counter = 1;
        File uniqueFile;
        do {
            String uniqueFileName = fileName + "(" + counter + ")" + extension;
            uniqueFile = new File(parentDir, uniqueFileName);
            counter++;
        } while (uniqueFile.exists());

        return uniqueFile;
    }

    // 时间戳处理（保留所有空行，仅最后一行有效）
    private String addContentTimestamp(String originalContent) {
        String[] allLines = originalContent.split("\n", -1);
        ArrayList<String> lineList = new ArrayList<>();
        for (String line : allLines) {
            lineList.add(line);
        }

        String todayTimestamp = CONTENT_TIMESTAMP.format(new Date());
        boolean needAddTimestamp = true;

        // 找到最后一个非空行
        int lastValidLineIndex = -1;
        for (int i = lineList.size() - 1; i >= 0; i--) {
            if (!TextUtils.isEmpty(lineList.get(i).trim())) {
                lastValidLineIndex = i;
                break;
            }
        }

        // 检查最后一个有效行是否为当天时间戳
        if (lastValidLineIndex != -1) {
            String lastValidLine = lineList.get(lastValidLineIndex);
            if (LAST_LINE_TIMESTAMP_PATTERN.matcher(lastValidLine).matches()) {
                String existingDate = lastValidLine.replace("(", "").replace(")", "");
                if (existingDate.equals(todayTimestamp)) {
                    needAddTimestamp = false;
                }
            }
        }

        if (needAddTimestamp) {
            lineList.add("(" + todayTimestamp + ")");
        }

        return TextUtils.join("\n", lineList);
    }

    // 以下方法保持不变
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

    private String getContentSubtitle(String content) {
        if (TextUtils.isEmpty(content.trim())) return "无内容文件";
        String trimmedContent = content.trim();
        return trimmedContent.length() <= MAX_TITLE_LEN
                ? trimmedContent
                : trimmedContent.substring(0, MAX_TITLE_LEN) + "…";
    }

    private String removeOldTimestamp(String fileName) {
        return MainActivity.FILE_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
    }

    private void writeFileContent(File file, String content) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "写入内容失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void focusAndShowSoftInput(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            editText.postDelayed(() -> imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT), 200);
        }
    }

    private void hideSoftInput() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etContent.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        autoSave();
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing() && !isSaved) {
            autoSave();
        }
    }
}