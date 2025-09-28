package com.example.outsidebrain;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
    private static final String ROOT_FOLDER_NAME = "外置大脑";

    // 新秒级时间戳格式（下划线+14位数字：_yyyyMMddHHmmss）
    private static final SimpleDateFormat FILE_NAME_TIMESTAMP = new SimpleDateFormat("_yyyyMMddHHmmss", Locale.getDefault());
    // 正文时间戳（保持原有格式不变）
    private static final SimpleDateFormat CONTENT_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    // 匹配最后一行有效时间戳
    private static final Pattern LAST_LINE_TIMESTAMP_PATTERN = Pattern.compile("^\\(\\d{4}-\\d{2}-\\d{2}\\)$");
    // 匹配第一行有效路径标识
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");
    // 新秒级时间戳正则（用于隐藏和重名判断）
    private static final Pattern NEW_SECOND_TIMESTAMP_PATTERN = Pattern.compile("_\\d{14}");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        etFileName = findViewById(R.id.et_file_name);
        etContent = findViewById(R.id.et_content);

        // 获取意图参数
        String filePath = getIntent().getStringExtra("file_path");
        String currentDirPath = getIntent().getStringExtra("current_dir_path");
        isPreEdit = getIntent().getBooleanExtra("is_pre_edit", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);

        handleZipAndShareIntent();

        // 记录编辑状态（无论是否新建）
        if (!isPreEdit && targetFile != null) {
            PreferenceUtils.saveLastPageType(this, "editor");
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }

        if (isPreEdit) {
            currentDir = new File(currentDirPath);
            etFileName.setHint(":标题");
            focusAndShowSoftInput(etContent);

            if (needHandleTimestamp) {
                etFileName.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        // 实时移除用户输入中可能包含的时间戳格式
                        String cleaned = NEW_SECOND_TIMESTAMP_PATTERN.matcher(s.toString()).replaceAll("");
                        if (!cleaned.equals(s.toString())) {
                            s.replace(0, s.length(), cleaned);
                        }
                    }
                });
            }
        } else if (filePath != null) {
            targetFile = new File(filePath);
            loadExistingFileData(needHandleTimestamp);
        }

        setupTextChangeListeners();
    }

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

    private void loadExistingFileData(boolean needHandleTimestamp) {
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

        String fileName = targetFile.getName();
        if (fileName.endsWith(".txt")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }

        if (needHandleTimestamp) {
            Matcher timestampMatcher = NEW_SECOND_TIMESTAMP_PATTERN.matcher(fileName);
            if (timestampMatcher.find()) {
                fileName = timestampMatcher.replaceAll("");
            }
        }
        etFileName.setText(fileName);

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
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

    private void autoSave() {
        if (isSaved) return; // 无改动则不执行保存

        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString();
        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);

        // 初始化根文件夹名称和时间戳（保持原有逻辑）
        if (rootFolderName == null) rootFolderName = "外置大脑";
        String newTimestamp = needHandleTimestamp ? FILE_NAME_TIMESTAMP.format(new Date()) : "";
        File rootDir = new File(Environment.getExternalStorageDirectory(), rootFolderName);

        // -------------------------- 1. 新建文件逻辑（isPreEdit=true）：保持不变 --------------------------
        if (isPreEdit) {
            if (TextUtils.isEmpty(inputTitle) && TextUtils.isEmpty(content.trim())) {
                Toast.makeText(this, "未输入内容，放弃创建", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 无标题时用内容片段生成标题，确保无时间戳
            String finalTitle = TextUtils.isEmpty(inputTitle) ? getContentSubtitle(content) : inputTitle;
            if (needHandleTimestamp) {
                finalTitle = UniqueFileNameHandler.removeTimestamp(finalTitle);
            }

            // 全局查重生成唯一文件（含新时间戳）
            targetFile = getUniqueFile(currentDir, finalTitle, needHandleTimestamp);

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
                } else {
                    Toast.makeText(this, "创建文件失败", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "创建异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        // -------------------------- 2. 编辑已有文件逻辑（isPreEdit=false）：核心修改 --------------------------
        else {
            if (targetFile == null || !targetFile.exists()) {
                Toast.makeText(this, "文件不存在，无法保存", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 2.1 提取原文件的「纯净标题」（去除原时间戳和扩展名）
            String originalFileName = targetFile.getName();
            String originalTitleWithExt = originalFileName.endsWith(".txt")
                    ? originalFileName
                    : originalFileName + ".txt"; // 确保含扩展名，避免截取错误
            String originalTitleWithoutExt = originalTitleWithExt.substring(0, originalTitleWithExt.lastIndexOf("."));
            String originalPureTitle = needHandleTimestamp
                    ? UniqueFileNameHandler.removeTimestamp(originalTitleWithoutExt)
                    : originalTitleWithoutExt;

            // 2.2 处理输入标题（空标题时用原纯净标题，避免重命名为空）
            String finalInputTitle = TextUtils.isEmpty(inputTitle) ? originalPureTitle : inputTitle;

            // 2.3 判断标题是否变更
            boolean titleChanged = !finalInputTitle.equals(originalPureTitle);

            // 2.4 生成新文件名（仅在标题变更时才进行全局查重）
            String newFileName;
            if (needHandleTimestamp) {
                if (titleChanged) {
                    // 标题变更：全局查重确保唯一性
                    newFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDir,                  // 全局范围（外置大脑根目录）
                            targetFile.getParentFile(),// 当前文件父目录
                            finalInputTitle,           // 最终标题（输入标题/原纯净标题）
                            newTimestamp               // 新时间戳（强制更新）
                    );
                } else {
                    // 标题未变更：仅更新时间戳，不查重，不增加序列号
                    String baseName = finalInputTitle;
                    // 保留文件扩展名
                    String extension = originalFileName.contains(".") ?
                            originalFileName.substring(originalFileName.lastIndexOf(".")) : ".txt";
                    newFileName = baseName + newTimestamp + extension;
                }
            } else {
                if (titleChanged) {
                    // 无需时间戳但标题变更：全局查重
                    newFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDir,
                            targetFile.getParentFile(),
                            finalInputTitle,
                            "" // 空时间戳
                    );
                } else {
                    // 无需时间戳且标题未变更：直接使用原文件名
                    newFileName = originalFileName;
                }
            }

            // 2.5 执行重命名（标题未改则仅更新时间戳，不提示；标题已改则静默重命名）
            File newFile = new File(targetFile.getParentFile(), newFileName);
            if (!targetFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                // 重命名成功后更新目标文件引用
                if (targetFile.renameTo(newFile)) {
                    targetFile = newFile;
                    // 标题已改时可加提示（可选，需求未要求则注释）
                    // Toast.makeText(this, "文件名已更新", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "文件名更新失败，内容已保存", Toast.LENGTH_SHORT).show();
                }
            }
            // 标题未改/重命名路径相同：仅更新时间戳，不提示

            // 2.6 处理文件内容（保留原路径修正和时间戳追加逻辑）
            String finalContent;
            if (isRootDirectory) {
                finalContent = addContentTimestamp(content);
            } else {
                // 移除原第一行路径标识，重新生成当前路径
                String[] allLines = content.split("\n", -1);
                ArrayList<String> userLines = new ArrayList<>();
                for (int i = 0; i < allLines.length; i++) {
                    String line = allLines[i];
                    if (i == 0 && FIRST_LINE_PATH_PATTERN.matcher(line).matches()) {
                        continue; // 跳过原路径行
                    }
                    userLines.add(line);
                }
                String userContent = TextUtils.join("\n", userLines);
                String contentWithTimestamp = addContentTimestamp(userContent);
                String currentDirPath = MainActivity.getRelativeDirPath(targetFile.getParentFile(), rootFolderName);
                finalContent = "【" + currentDirPath + "】\n" + contentWithTimestamp;
            }

            // 2.7 写入内容并完成保存
            writeFileContent(targetFile, finalContent);
            isSaved = true;
            // 仅提示内容更新，标题未改时不额外提示
            Toast.makeText(this, "文件更新成功", Toast.LENGTH_SHORT).show();
            setResult(RESULT_REFRESH);
        }
        // 新增：如果是编辑现有文件，保存文件路径
        if (!isPreEdit && targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            // 同时保存文件所在的文件夹
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }

        hideSoftInput();
        finish(); // 保存后关闭编辑页，返回列表页
    }

    // 修改onBackPressed，确保返回主页面时状态更新
    @Override
    public void onBackPressed() {
        autoSave();
        super.onBackPressed();
    }

    // 重写onDestroy，处理异常关闭情况
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 如果是被系统销毁，保存最后状态
        if (isChangingConfigurations()) {
            if (!isPreEdit && targetFile != null && targetFile.exists()) {
                PreferenceUtils.saveLastPageType(this, "editor");
                PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            }
        }
    }

    private File getUniqueEditFile(File targetFile, boolean needHandleTimestamp) {
        if (!targetFile.exists()) return targetFile;

        File parentDir = targetFile.getParentFile();
        String fileName = targetFile.getName();
        String extension = "";
        int dotIndex = fileName.lastIndexOf(".");

        if (dotIndex != -1) {
            extension = fileName.substring(dotIndex);
            fileName = fileName.substring(0, dotIndex);
        }

        String coreTitle = fileName;
        if (needHandleTimestamp) {
            coreTitle = UniqueFileNameHandler.removeTimestamp(fileName);
        }

        String timestamp = needHandleTimestamp ? FILE_NAME_TIMESTAMP.format(new Date()) : "";

        // 使用全局唯一性检查
        String newFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME),
                parentDir,
                coreTitle,
                timestamp
        );

        return new File(parentDir, newFileName);
    }

    private String addContentTimestamp(String originalContent) {
        String[] allLines = originalContent.split("\n", -1);
        ArrayList<String> lineList = new ArrayList<>();
        for (String line : allLines) {
            lineList.add(line);
        }

        String todayTimestamp = CONTENT_TIMESTAMP.format(new Date());
        boolean needAddTimestamp = true;

        int lastValidLineIndex = -1;
        for (int i = lineList.size() - 1; i >= 0; i--) {
            if (!TextUtils.isEmpty(lineList.get(i).trim())) {
                lastValidLineIndex = i;
                break;
            }
        }

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

    private File getUniqueFile(File parentDir, String baseTitle, boolean needHandleTimestamp) {
        String cleanTitle = needHandleTimestamp ? UniqueFileNameHandler.removeTimestamp(baseTitle) : baseTitle;
        String timestamp = needHandleTimestamp ? FILE_NAME_TIMESTAMP.format(new Date()) : "";

        // 使用全局唯一性检查
        String fileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME),
                parentDir,
                cleanTitle,
                timestamp
        );

        return new File(parentDir, fileName);
    }

    private String getContentSubtitle(String content) {
        if (TextUtils.isEmpty(content.trim())) return "无内容文件";
        String trimmedContent = content.trim();
        return trimmedContent.length() <= MAX_TITLE_LEN
                ? trimmedContent
                : trimmedContent.substring(0, MAX_TITLE_LEN) + "…";
    }

    private String removeOldTimestamp(String fileName) {
        return NEW_SECOND_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
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



    // 修改onPause，确保状态正确记录
    @Override
    protected void onPause() {
        super.onPause();

        // 只有编辑已有文件时才记录状态
        if (!isPreEdit && targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastPageType(this, "editor");
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }

        if (!isFinishing() && !isSaved) {
            autoSave();
        }
    }
}
