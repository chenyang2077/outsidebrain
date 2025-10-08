package com.example.outsidebrain;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * V1版本 - 文件编辑活动
 * 功能：创建新文件、编辑已有文件、自动保存、文件压缩、文件分享
 * 特点：统一使用UniqueFileNameHandler处理文件名查重和时间戳，存储在应用私有目录
 */
public class FileEditorActivity extends AppCompatActivity {

    public static final int RESULT_REFRESH = 100;
    private EditText etFileName;
    private EditText etContent;
    private boolean isPreEdit;       // 是否为新建文件模式
    private File currentDir;         // 当前目录（新建文件时使用）
    private File targetFile;         // 目标文件（编辑时使用）
    private boolean isSaved = true;  // 是否已保存
    private static final int MAX_TITLE_LEN = 31;  // 标题最大长度
    private static final String ROOT_FOLDER_NAME = "流动信息";  // 根目录名称（私有存储中）

    // 正则表达式模式（保持不变）
    private static final Pattern RANDOM_STR_PATTERN = Pattern.compile("[A-Za-z0-9]{6}");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{17}");
    private static final Pattern SINGLE_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private static final Pattern MULTI_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})+");
    private static final Pattern FULL_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})*$");

    // 时间戳格式定义（保持不变）
    private static final SimpleDateFormat FILE_NAME_TIMESTAMP = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
    private static final SimpleDateFormat CONTENT_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // 正则表达式（保持不变）
    private static final Pattern LAST_LINE_TIMESTAMP_PATTERN = Pattern.compile("^\\(\\d{4}-\\d{2}-\\d{2}\\)$");
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        // 初始化视图
        etFileName = findViewById(R.id.et_file_name);
        etContent = findViewById(R.id.et_content);

        // 获取意图参数
        String filePath = getIntent().getStringExtra("file_path");
        String currentDirPath = getIntent().getStringExtra("current_dir_path");
        isPreEdit = getIntent().getBooleanExtra("is_pre_edit", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);

        // 处理压缩和分享意图
        handleZipAndShareIntent();

        // 记录编辑状态（仅针对已有文件）
        if (!isPreEdit && filePath != null) {
            targetFile = new File(filePath);
            if (targetFile.exists()) {
                PreferenceUtils.saveLastPageType(this, "editor");
                PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
                PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
            }
        }

        // 新建文件模式初始化
        if (isPreEdit) {
            currentDir = new File(currentDirPath);
            etFileName.setHint(":标题");
            focusAndShowSoftInput(etContent);  // 聚焦内容输入框

            // 标题输入过滤（自动清理时间戳格式）
            if (needHandleTimestamp) {
                etFileName.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(android.text.Editable s) {
                        String cleanedTitle = removeAllTimestampFormats(s.toString());
                        if (!cleanedTitle.equals(s.toString())) {
                            s.replace(0, s.length(), cleanedTitle);
                        }
                    }
                });
            }
        }
        // 编辑已有文件模式初始化
        else if (filePath != null) {
            targetFile = new File(filePath);
            loadExistingFileData(needHandleTimestamp);
        }

        // 设置文本变化监听（标记未保存状态）
        setupTextChangeListeners();
    }

    // 以下方法保持不变：parseTimestampStructure、removeAllTimestampFormats、loadExistingFileData、setupTextChangeListeners
    private String[] parseTimestampStructure(String fileNameWithoutExt) {
        if (TextUtils.isEmpty(fileNameWithoutExt)) return new String[0];

        Matcher fullMatcher = FULL_TIMESTAMP_PATTERN.matcher(fileNameWithoutExt);
        if (!fullMatcher.find()) {
            return new String[0];  // 无符合规则的时间戳结构
        }

        // 提取完整匹配的字符串
        String fullMatch = fullMatcher.group();
        // 按下划线分割（结果为["", "随机字符", "时间戳1", "时间戳2"...]）
        String[] parts = fullMatch.split("_");

        // 验证结构有效性（至少需要随机字符和一个时间戳）
        if (parts.length < 3) {
            return new String[0];
        }

        // 提取并验证随机字符（6位字母+数字）
        String randomStr = parts[1];
        if (randomStr == null || !RANDOM_STR_PATTERN.matcher(randomStr).matches()) {
            return new String[0];  // 随机字符格式无效
        }

        // 提取所有时间戳（17位数字）
        ArrayList<String> timestamps = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            String ts = parts[i];
            if (ts != null && TIMESTAMP_PATTERN.matcher(ts).matches()) {
                timestamps.add(ts);
            }
        }

        // 构建结果数组：[随机字符, 时间戳1, 时间戳2, ...]
        if (timestamps.isEmpty()) {
            return new String[0];  // 无有效时间戳
        }
        String[] result = new String[timestamps.size() + 1];
        result[0] = randomStr;
        for (int i = 0; i < timestamps.size(); i++) {
            result[i + 1] = timestamps.get(i);
        }
        return result;
    }

    private String removeAllTimestampFormats(String input) {
        if (TextUtils.isEmpty(input)) return "";
        String result = input;

        // 先移除增量时间戳格式
        Matcher multiMatcher = MULTI_TIMESTAMP_PATTERN.matcher(result);
        result = multiMatcher.replaceAll("");

        // 再移除基础时间戳格式
        Matcher singleMatcher = SINGLE_TIMESTAMP_PATTERN.matcher(result);
        result = singleMatcher.replaceAll("");

        // 清理可能的残留下划线
        result = result.replaceAll("_+$", "");

        return result.trim();
    }

    private void loadExistingFileData(boolean needHandleTimestamp) {
        if (targetFile == null || !targetFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ZIP文件不支持编辑
        if (targetFile.getName().toLowerCase().endsWith(".zip")) {
            Toast.makeText(this, "ZIP文件不支持编辑", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 处理文件名：移除扩展名和时间戳（仅TXT文件需处理时间戳）
        String fileName = targetFile.getName();
        String fileNameWithoutExt = fileName;
        if (fileName.endsWith(".txt")) {
            fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        }
        // 清理时间戳，显示纯净标题
        String displayName = needHandleTimestamp
                ? removeAllTimestampFormats(fileNameWithoutExt)
                : fileNameWithoutExt;
        etFileName.setText(displayName);

        // 加载文件内容（UTF-8编码）
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            // 移除末尾多余换行
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
            public void afterTextChanged(android.text.Editable s) {
                String cleanedTitle = removeAllTimestampFormats(s.toString());
                if (!cleanedTitle.equals(s.toString())) {
                    s.replace(0, s.length(), cleanedTitle);
                }
            }
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

    /**
     * 自动保存文件（核心修改：使用应用私有存储）
     */
    private void autoSave() {
        if (isSaved) return;

        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString();
        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);

        // 初始化根目录（修改为应用私有存储中的"流动信息"）
        if (rootFolderName == null) rootFolderName = ROOT_FOLDER_NAME;
        // 关键修改：使用应用私有存储目录(getFilesDir())而非外部存储
        File rootDir = new File(getFilesDir(), rootFolderName);

        // 确保根目录存在
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            Toast.makeText(this, "无法创建根目录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 新建文件逻辑
        if (isPreEdit) {
            // 空内容+空标题：放弃创建
            if (TextUtils.isEmpty(inputTitle) && TextUtils.isEmpty(content.trim())) {
                Toast.makeText(this, "未输入内容，放弃创建", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 生成原始标题（空标题时用内容前31字符）
            String rawTitle = TextUtils.isEmpty(inputTitle)
                    ? getContentSubtitle(content)
                    : inputTitle;
            // 清理标题中的特殊字符
            rawTitle = cleanFileName(rawTitle);
            String timestampSuffix = "";

            // 处理时间戳后缀
            if (needHandleTimestamp) {
                // 新建文件：添加6位随机字符+17位时间戳
                String randomStr = UniqueFileNameHandler.generateRandomString();
                String millisTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();
                timestampSuffix = "_" + randomStr + "_" + millisTimestamp;
            }

            // 新文件始终保存在当前文件夹
            File targetDirectory = currentDir;

            // 生成全局唯一文件名
            String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDir, targetDirectory, rawTitle, timestampSuffix
            );
            targetFile = new File(targetDirectory, uniqueFileName);

            try {
                // 确保目标目录存在
                if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                    Toast.makeText(this, "无法创建目标目录", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                if (targetFile.createNewFile()) {
                    // 构建最终内容（始终使用当前文件夹路径）
                    String finalContent = processContentForSaving(
                            content,
                            targetDirectory,  // 强制使用当前目录
                            rootFolderName,
                            isRootDirectory
                    );

                    // 写入内容
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
        // 编辑已有文件逻辑
        else {
            if (targetFile == null || !targetFile.exists()) {
                Toast.makeText(this, "文件不存在，无法保存", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 文件实际存储的目录（不可通过内容修改）
            File actualDirectory = targetFile.getParentFile();

            // 1. 处理文件名核心数据（不依赖内容中的路径）
            String originalFileName = targetFile.getName();
            String originalFileNameWithoutExt = originalFileName.endsWith(".txt")
                    ? originalFileName.substring(0, originalFileName.lastIndexOf("."))
                    : originalFileName;
            String cleanedOriginalTitle = needHandleTimestamp
                    ? removeAllTimestampFormats(originalFileNameWithoutExt)
                    : originalFileNameWithoutExt;

            String newTitleInput = etFileName.getText().toString().trim();
            newTitleInput = cleanFileName(newTitleInput);
            String cleanedNewTitle = needHandleTimestamp
                    ? removeAllTimestampFormats(newTitleInput)
                    : newTitleInput;

            if (TextUtils.isEmpty(cleanedNewTitle)) {
                cleanedNewTitle = cleanedOriginalTitle;
            }

            // 2. 判断是否需要查重
            boolean needCheckDuplicate = !cleanedNewTitle.equals(cleanedOriginalTitle);

            // 3. 生成新的时间戳后缀
            String newTimestampSuffix = "";
            if (needHandleTimestamp && originalFileName.endsWith(".txt")) {
                String[] timestampStruct = parseTimestampStructure(originalFileNameWithoutExt);
                String newRandomStr = "";
                ArrayList<String> newTimestamps = new ArrayList<>();
                String newMillisTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();

                if (timestampStruct.length == 0) {
                    newRandomStr = UniqueFileNameHandler.generateRandomString();
                    newTimestamps.add(newMillisTimestamp);
                }
                else if (timestampStruct.length == 2) {
                    newRandomStr = timestampStruct[0];
                    newTimestamps.add(timestampStruct[1]);
                    newTimestamps.add(newMillisTimestamp);
                }
                else if (timestampStruct.length >= 3) {
                    newRandomStr = timestampStruct[0];
                    for (int i = 1; i < timestampStruct.length - 1; i++) {
                        newTimestamps.add(timestampStruct[i]);
                    }
                    newTimestamps.add(newMillisTimestamp);
                }

                if (!TextUtils.isEmpty(newRandomStr) && !newTimestamps.isEmpty()) {
                    StringBuilder suffixBuilder = new StringBuilder("_").append(newRandomStr);
                    for (String ts : newTimestamps) {
                        suffixBuilder.append("_").append(ts);
                    }
                    newTimestampSuffix = suffixBuilder.toString();
                }
            }

            // 4. 生成新文件名（始终在实际存储目录下）
            String newFileName;
            if (needCheckDuplicate) {
                newFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                        rootDir, actualDirectory, cleanedNewTitle, newTimestampSuffix
                );
            } else {
                newFileName = cleanedNewTitle + newTimestampSuffix + ".txt";
            }

            File newFile = new File(actualDirectory, newFileName);

            // 5. 执行文件重命名（仅文件名变化，目录不变）
            boolean fileOperationSuccess = true;
            if (!targetFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                if (!targetFile.renameTo(newFile)) {
                    try {
                        writeFileContent(newFile, readFileContent(targetFile));
                        if (!targetFile.delete()) {
                            Log.w("FileEditor", "无法删除原文件");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        fileOperationSuccess = false;
                        Toast.makeText(this, "文件名更新失败，内容已保存", Toast.LENGTH_SHORT).show();
                    }
                }
                targetFile = newFile;
                Log.d("FileEditor", "文件名更新：" + originalFileName + " → " + newFileName);
            }

            if (fileOperationSuccess) {
                // 6. 检查内容中的路径标识是否与实际存储目录一致
                File parsedDirectory = parseFirstLinePath(content, rootDir, isRootDirectory);
                boolean pathMismatch = !parsedDirectory.getAbsolutePath().equals(actualDirectory.getAbsolutePath());

                // 7. 处理文件内容（路径不匹配时自动纠正为实际路径）
                String finalContent = processContentForSaving(
                        content,
                        pathMismatch ? actualDirectory : parsedDirectory,  // 不匹配则纠正
                        rootFolderName,
                        isRootDirectory
                );

                // 8. 写入内容
                writeFileContent(targetFile, finalContent);
                isSaved = true;
                Toast.makeText(this, pathMismatch ? "文件路径标识已纠正并保存" : "文件更新成功", Toast.LENGTH_SHORT).show();
                setResult(RESULT_REFRESH);
            }
        }

        // 保存最后编辑状态
        if (!isPreEdit && targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }

        hideSoftInput();
        finish();
    }

    // 以下方法保持不变：cleanFileName、parseFirstLinePath、processContentForSaving、readFileContent
    // getContentSubtitle、writeFileContent、addContentTimestamp、handleZipAndShareIntent
    // zipFolder、addFolderToZip、shareFile、getMimeType、focusAndShowSoftInput、hideSoftInput
    private String cleanFileName(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private File parseFirstLinePath(String content, File rootDir, boolean isRootDirectory) {
        if (isRootDirectory || TextUtils.isEmpty(content)) {
            return rootDir;
        }

        String[] lines = content.split("\n", 2);
        String firstLine = lines[0];

        if (FIRST_LINE_PATH_PATTERN.matcher(firstLine).matches()) {
            try {
                String pathContent = firstLine.substring(1, firstLine.length() - 1);
                String decodedPath = pathContent.replace("_slash_", "/");
                return new File(rootDir, decodedPath);
            } catch (Exception e) {
                Log.w("FileEditor", "解析第一行路径失败，使用根目录", e);
                return rootDir;
            }
        }

        return rootDir;
    }

    private String processContentForSaving(String content, File targetDir, String rootFolderName, boolean isRootDirectory) {
        if (isRootDirectory) {
            return addContentTimestamp(content);
        }

        String dirPath = MainActivity.getRelativeDirPath(targetDir, rootFolderName);
        String safeDirPath = dirPath.replace("/", "_slash_");
        String pathLine = "【" + safeDirPath + "】";

        // 检查第一行是否已有路径标识
        boolean hasPathIdentifier = false;
        String[] lines = content.split("\n", 2);
        if (lines.length > 0 && !TextUtils.isEmpty(lines[0])) {
            hasPathIdentifier = FIRST_LINE_PATH_PATTERN.matcher(lines[0]).matches();
        }

        // 处理内容
        String restContent;
        if (hasPathIdentifier) {
            restContent = lines.length > 1 ? lines[1] : "";
        } else {
            restContent = content;
        }

        String contentWithTimestamp = addContentTimestamp(restContent);
        return pathLine + "\n" + contentWithTimestamp;
    }

    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }

    private String getContentSubtitle(String content) {
        if (TextUtils.isEmpty(content.trim())) return "无内容文件";
        String trimmedContent = content.trim();
        return trimmedContent.length() <= MAX_TITLE_LEN
                ? trimmedContent
                : trimmedContent.substring(0, MAX_TITLE_LEN) + "…";
    }

    private void writeFileContent(File file, String content) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "写入内容失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String addContentTimestamp(String originalContent) {
        String[] allLines = originalContent.split("\n", -1);
        ArrayList<String> lineList = new ArrayList<>();
        for (String line : allLines) {
            lineList.add(line);
        }

        String todayTimestamp = CONTENT_TIMESTAMP.format(new Date());
        boolean needAddTimestamp = true;

        // 查找最后一行有效内容
        int lastValidLineIndex = -1;
        for (int i = lineList.size() - 1; i >= 0; i--) {
            if (!TextUtils.isEmpty(lineList.get(i).trim())) {
                lastValidLineIndex = i;
                break;
            }
        }

        // 检查最后一行是否已有今日时间戳
        if (lastValidLineIndex != -1) {
            String lastValidLine = lineList.get(lastValidLineIndex);
            if (LAST_LINE_TIMESTAMP_PATTERN.matcher(lastValidLine).matches()) {
                String existingDate = lastValidLine.replace("(", "").replace(")", "");
                if (existingDate.equals(todayTimestamp)) {
                    needAddTimestamp = false;
                }
            }
        }

        // 需要则添加今日时间戳
        if (needAddTimestamp) {
            lineList.add("(" + todayTimestamp + ")");
        }

        return TextUtils.join("\n", lineList);
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

            // 避免压缩文件重名
            int counter = 1;
            while (zipFile.exists()) {
                zipFileName = folder.getName() + "(" + counter + ").zip";
                zipFile = new File(folder.getParentFile(), zipFileName);
                counter++;
            }

            // 执行压缩
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
        if (!isPreEdit && targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastPageType(this, "editor");
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }
        if (!isFinishing() && !isSaved) {
            autoSave();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isChangingConfigurations()) {
            if (!isPreEdit && targetFile != null && targetFile.exists()) {
                PreferenceUtils.saveLastPageType(this, "editor");
                PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            }
        }
    }
}
