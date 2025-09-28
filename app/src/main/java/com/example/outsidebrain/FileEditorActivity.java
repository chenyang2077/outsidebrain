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
 * 特点：统一使用UniqueFileNameHandler处理文件名查重和时间戳
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
    private static final String ROOT_FOLDER_NAME = "外置大脑";  // 默认根目录名称

    private static final Pattern RANDOM_STR_PATTERN = Pattern.compile("[A-Za-z0-9]{6}");  // 6位随机字符（字母+数字）
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{17}");  // 17位时间戳（yyyyMMddHHmmssSSS）
    private static final Pattern SINGLE_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");  // 基础时间戳格式
    private static final Pattern MULTI_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})+");  // 增量时间戳格式
    private static final Pattern FULL_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})*$");  // 合并格式（无命名分组）

    // 时间戳格式定义
    private static final SimpleDateFormat FILE_NAME_TIMESTAMP = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
    private static final SimpleDateFormat CONTENT_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // 正则表达式
    private static final Pattern LAST_LINE_TIMESTAMP_PATTERN = Pattern.compile("^\\(\\d{4}-\\d{2}-\\d{2}\\)$");  // 内容末尾时间戳
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");  // 内容首行路径标识


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

    /**
     * 移除文件名中所有时间戳格式（基础+增量）
     */
    private String removeAllTimestampFormats(String input) {
        if (TextUtils.isEmpty(input)) return "";
        // 先处理增量格式，避免被基础格式部分匹配
        Matcher multiMatcher = MULTI_TIMESTAMP_PATTERN.matcher(input);
        String tempResult = multiMatcher.replaceAll("");
        // 再处理基础格式
        Matcher singleMatcher = SINGLE_TIMESTAMP_PATTERN.matcher(tempResult);
        return singleMatcher.replaceAll("");
    }
    /**
     * 加载已有文件数据（显示时隐藏时间戳）
     */
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

    /**
     * 设置文本变化监听（编辑时标记未保存状态）
     */
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
     * 自动保存文件（新建/编辑通用逻辑）
     * 核心：统一调用UniqueFileNameHandler处理文件名查重和格式
     */
    private void autoSave() {
        if (isSaved) return;

        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString();
        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);

        // 初始化根目录（默认"外置大脑"）
        if (rootFolderName == null) rootFolderName = ROOT_FOLDER_NAME;
        File rootDir = new File(Environment.getExternalStorageDirectory(), rootFolderName);

        // 新建文件逻辑（规则1：添加随机字符+1个时间戳）
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
            String timestampSuffix = "";

            // 处理时间戳后缀
            if (needHandleTimestamp) {
                // 新建文件：添加6位随机字符+17位时间戳
                String randomStr = UniqueFileNameHandler.generateRandomString();
                String millisTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();
                timestampSuffix = "_" + randomStr + "_" + millisTimestamp;
            }

            // 生成全局唯一文件名（调用工具类查重）
            String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDir, currentDir, rawTitle, timestampSuffix
            );
            targetFile = new File(currentDir, uniqueFileName);

            try {
                if (targetFile.createNewFile()) {
                    // 构建最终内容（根目录无路径行，子目录添加路径标识）
                    String dirPath = MainActivity.getRelativeDirPath(currentDir, rootFolderName);
                    String finalContent = isRootDirectory
                            ? addContentTimestamp(content)
                            : "【" + dirPath + "】\n" + addContentTimestamp(content);
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
        // 编辑已有文件逻辑（分级处理时间戳）
        else {
            if (targetFile == null || !targetFile.exists()) {
                Toast.makeText(this, "文件不存在，无法保存", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 1. 处理文件名核心数据
            String originalFileName = targetFile.getName();
            // 移除扩展名（仅TXT文件）
            String originalFileNameWithoutExt = originalFileName.endsWith(".txt")
                    ? originalFileName.substring(0, originalFileName.lastIndexOf("."))
                    : originalFileName;
            // 清理原始标题（移除时间戳）
            String cleanedOriginalTitle = needHandleTimestamp
                    ? removeAllTimestampFormats(originalFileNameWithoutExt)
                    : originalFileNameWithoutExt;
            // 清理新标题（移除用户输入的时间戳格式）
            String newTitleInput = etFileName.getText().toString().trim();
            String cleanedNewTitle = needHandleTimestamp
                    ? removeAllTimestampFormats(newTitleInput)
                    : newTitleInput;
            // 空标题处理：使用原始核心标题
            if (TextUtils.isEmpty(cleanedNewTitle)) {
                cleanedNewTitle = cleanedOriginalTitle;
            }

            // 2. 生成新的时间戳后缀（核心分级逻辑）
            String newTimestampSuffix = "";
            if (needHandleTimestamp && originalFileName.endsWith(".txt")) {  // 仅TXT文件处理时间戳
                // 解析原始文件名的时间戳结构
                String[] timestampStruct = parseTimestampStructure(originalFileNameWithoutExt);
                String newRandomStr = "";
                ArrayList<String> newTimestamps = new ArrayList<>();
                String newMillisTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();

                // 情况1：无随机字符+时间戳（规则1：添加随机字符+1个时间戳）
                if (timestampStruct.length == 0) {
                    newRandomStr = UniqueFileNameHandler.generateRandomString();
                    newTimestamps.add(newMillisTimestamp);
                }
                // 情况2：有随机字符+1个时间戳（规则2：追加新时间戳）
                else if (timestampStruct.length == 2) {
                    newRandomStr = timestampStruct[0];  // 保留原随机字符
                    newTimestamps.add(timestampStruct[1]);  // 保留原时间戳
                    newTimestamps.add(newMillisTimestamp);  // 追加新时间戳
                }
                // 情况3：有随机字符+2个及以上时间戳（规则3：更新最后一个时间戳）
                else if (timestampStruct.length >= 3) {
                    newRandomStr = timestampStruct[0];  // 保留原随机字符
                    // 保留前n-1个时间戳
                    for (int i = 1; i < timestampStruct.length - 1; i++) {
                        newTimestamps.add(timestampStruct[i]);
                    }
                    newTimestamps.add(newMillisTimestamp);  // 更新最后一个时间戳
                }

                // 构建时间戳后缀
                if (!TextUtils.isEmpty(newRandomStr) && !newTimestamps.isEmpty()) {
                    StringBuilder suffixBuilder = new StringBuilder("_").append(newRandomStr);
                    for (String ts : newTimestamps) {
                        suffixBuilder.append("_").append(ts);
                    }
                    newTimestampSuffix = suffixBuilder.toString();
                }
            }

            // 3. 生成新文件名
            String newFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDir, targetFile.getParentFile(), cleanedNewTitle, newTimestampSuffix
            );
            File newFile = new File(targetFile.getParentFile(), newFileName);

            // 4. 执行文件重命名（仅当路径变化时）
            if (!targetFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                if (!targetFile.renameTo(newFile)) {
                    Toast.makeText(this, "文件名更新失败，内容已保存", Toast.LENGTH_SHORT).show();
                } else {
                    targetFile = newFile;  // 更新目标文件引用
                    Log.d("FileEditor", "文件名更新：" + originalFileName + " → " + newFileName);
                }
            }

            // 5. 处理文件内容
            String finalContent;
            if (isRootDirectory) {
                finalContent = addContentTimestamp(content);
            } else {
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

            // 6. 写入内容
            writeFileContent(targetFile, finalContent);
            isSaved = true;
            Toast.makeText(this, "文件更新成功", Toast.LENGTH_SHORT).show();
            setResult(RESULT_REFRESH);
        }

        // 保存最后编辑状态
        if (!isPreEdit && targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }

        hideSoftInput();
        finish();
    }

    /**
     * 从内容提取副标题（新建文件无标题时使用）
     */
    private String getContentSubtitle(String content) {
        if (TextUtils.isEmpty(content.trim())) return "无内容文件";
        String trimmedContent = content.trim();
        return trimmedContent.length() <= MAX_TITLE_LEN
                ? trimmedContent
                : trimmedContent.substring(0, MAX_TITLE_LEN) + "…";
    }

    /**
     * 写入文件内容（UTF-8编码）
     */
    private void writeFileContent(File file, String content) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "写入内容失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 为内容添加末尾时间戳
     */
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

    /**
     * 处理压缩和分享意图
     */
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

    /**
     * 压缩文件夹
     */
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

    /**
     * 递归添加文件到压缩包
     */
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

    /**
     * 分享文件
     */
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

    /**
     * 获取文件MIME类型
     */
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

    /**
     * 聚焦并显示软键盘
     */
    private void focusAndShowSoftInput(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            editText.postDelayed(() -> imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT), 200);
        }
    }

    /**
     * 隐藏软键盘
     */
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