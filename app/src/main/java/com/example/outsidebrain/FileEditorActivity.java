/*
软件名称：快乐文字
版本号：V1.0
功能描述：实现TXT文件编辑、保存、重命名，自动处理时间戳、命名冲突，提供文件夹压缩、文件分享功能，限制操作范围保障数据安全
所属模块：文件编辑模块
开发语言：Java
*/
package com.example.outsidebrain;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件编辑页面：实现TXT文件编辑、保存、重命名，自动处理时间戳、命名冲突，提供文件夹压缩、文件分享功能
 */
public class FileEditorActivity extends AppCompatActivity {
    private static final int BUFFER_SIZE = 8192;
    private static final int RESULT_REFRESH = 1002;
    private EditText etFileName;
    private EditText etContent;
    private boolean isPreEdit;
    private File currentDir;
    private File targetFile;
    private boolean isSaved = true;
    private static final int MAX_TITLE_LEN = 31;
    private static final String ROOT_FOLDER_NAME = "主页根目录";
    private String searchKeyword;
    private static final Pattern RANDOM_STR_PATTERN = Pattern.compile("[A-Za-z0-9]{6}");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{17}");
    private static final Pattern SINGLE_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private static final Pattern MULTI_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})+");
    private static final Pattern FULL_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})*$");
    private static final SimpleDateFormat CONTENT_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private static final Pattern LAST_LINE_TIMESTAMP_PATTERN = Pattern.compile("^\\(\\d{4}-\\d{2}-\\d{2}\\)$");
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");
    private int cursorPosition = 0;
    private String savedNewFilePath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);
        etFileName = findViewById(R.id.et_file_name);
        etContent = findViewById(R.id.et_content);
        String filePath = getIntent().getStringExtra("file_path");
        String currentDirPath = getIntent().getStringExtra("current_dir_path");
        isPreEdit = getIntent().getBooleanExtra("is_pre_edit", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);
        SharedPreferences sp = getSharedPreferences("SearchSP", Context.MODE_PRIVATE);
        searchKeyword = sp.getString("current_keyword", "").trim();

        // 新增：启动时恢复崩溃未完成的保存
        recoverFromCrash();

        handleZipAndShareIntent();
        if (!isPreEdit && filePath != null) {
            targetFile = new File(filePath);
            if (targetFile.exists()) {
                PreferenceUtils.saveLastPageType(this, "editor");
                PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
                PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
            }
        }
        if (isPreEdit) {
            currentDir = new File(currentDirPath);
            etFileName.setHint(":标题");
        }
        else if (filePath != null) {
            targetFile = new File(filePath);
            loadExistingFileData(needHandleTimestamp);
            if (etContent != null) {
                etContent.postDelayed(() -> {
                    String fileContent = etContent.getText().toString();
                    if (!TextUtils.isEmpty(searchKeyword) && !TextUtils.isEmpty(fileContent)) {
                        String lowerFileContent = fileContent.toLowerCase();
                        String lowerKeyword = searchKeyword.toLowerCase();
                        int keywordLength = lowerKeyword.length();
                        int firstMatchIndex = lowerFileContent.indexOf(lowerKeyword);
                        if (firstMatchIndex != -1) {
                            int secondMatchIndex = lowerFileContent.indexOf(lowerKeyword, firstMatchIndex + keywordLength);
                            int targetMatchIndex = (secondMatchIndex != -1) ? secondMatchIndex : firstMatchIndex;
                            etContent.requestFocus();
                            int cursorPosition = targetMatchIndex + searchKeyword.length();
                            etContent.setSelection(cursorPosition);
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.showSoftInput(etContent, InputMethodManager.SHOW_FORCED);
                            }
                        }
                        else if (TextUtils.isEmpty(fileContent)) {
                            showKeyboard(etContent);
                        }
                    }
                    else if (TextUtils.isEmpty(fileContent)) {
                        showKeyboard(etContent);
                    }
                }, 100);
            }
        }
        if (etContent != null && TextUtils.isEmpty(etContent.getText().toString())) {
            etContent.post(() -> showKeyboard(etContent));
        }
        setupTextChangeListeners();
    }

    /**
     * 进程被杀后的数据恢复（兜底：恢复临时文件/备份文件）
     */
    private void recoverFromCrash() {
        File rootDir = new File(getFilesDir(), ROOT_FOLDER_NAME);
        if (!rootDir.exists()) return;

        // 1. 扫描所有临时文件（_atomic_tmp_）和备份文件（_backup）
        File[] files = rootDir.listFiles((dir, name) ->
                name.contains("_atomic_tmp_") || name.endsWith("_backup")
        );
        if (files == null || files.length == 0) return;

        // 2. 恢复数据
        for (File file : files) {
            String fileName = file.getName();
            // 处理临时文件（未完成替换的新内容）
            if (fileName.contains("_atomic_tmp_")) {
                String originalFileName = fileName.split("_atomic_tmp_")[0];
                File originalFile = new File(rootDir, originalFileName);
                // 临时文件内容是完整的，直接替换原文件
                if (originalFile.exists()) {
                    originalFile.delete();
                }
                boolean renamed = file.renameTo(originalFile);
                if (renamed) {
                    Toast.makeText(this, "恢复崩溃未保存的文件：" + originalFileName, Toast.LENGTH_LONG).show();
                }
            }
            // 处理备份文件（原文件的完整备份）
            else if (fileName.endsWith("_backup")) {
                String originalFileName = fileName.replace("_backup", "");
                File originalFile = new File(rootDir, originalFileName);
                // 若原文件不存在，恢复备份
                if (!originalFile.exists()) {
                    boolean renamed = file.renameTo(originalFile);
                    if (renamed) {
                        Toast.makeText(this, "恢复崩溃损坏的文件：" + originalFileName, Toast.LENGTH_LONG).show();
                    }
                } else {
                    file.delete(); // 原文件存在，删除备份
                }
            }
        }

        // 3. 清理保存状态标记
        SharedPreferences sp = getSharedPreferences("save_state", MODE_PRIVATE);
        Map<String, ?> allEntries = sp.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("is_saving_") && (Boolean) entry.getValue()) {
                String fileName = key.replace("is_saving_", "");
                Toast.makeText(this, "检测到「" + fileName + "」上次保存可能未完成，请检查文件内容", Toast.LENGTH_LONG).show();
                sp.edit().remove(key).apply();
            }
        }
    }

    /**
     * 弹出软键盘并聚焦指定输入框
     */
    private void showKeyboard(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * 解析文件名中的时间戳结构，提取随机字符串和时间戳列表
     * @return String[] 索引0为随机字符串，后续为时间戳
     */
    private String[] parseTimestampStructure(String fileNameWithoutExt) {
        if (TextUtils.isEmpty(fileNameWithoutExt)) return new String[0];
        Matcher fullMatcher = FULL_TIMESTAMP_PATTERN.matcher(fileNameWithoutExt);
        if (!fullMatcher.find()) {
            return new String[0];
        }
        String fullMatch = fullMatcher.group();
        String[] parts = fullMatch.split("_");
        if (parts.length < 3) {
            return new String[0];
        }
        String randomStr = parts[1];
        if (randomStr == null || !RANDOM_STR_PATTERN.matcher(randomStr).matches()) {
            return new String[0];
        }
        ArrayList<String> timestamps = new ArrayList<>();
        for (int i = 2; i < parts.length; i++) {
            String ts = parts[i];
            if (ts != null && TIMESTAMP_PATTERN.matcher(ts).matches()) {
                timestamps.add(ts);
            }
        }
        if (timestamps.isEmpty()) {
            return new String[0];
        }
        String[] result = new String[timestamps.size() + 1];
        result[0] = randomStr;
        for (int i = 0; i < timestamps.size(); i++) {
            result[i + 1] = timestamps.get(i);
        }
        return result;
    }

    /**
     * 移除文件名中的所有时间戳格式字符
     * @return String 清理后的文件名
     */
    private String removeAllTimestampFormats(String input) {
        if (TextUtils.isEmpty(input)) return "";
        String result = input;
        Matcher multiMatcher = MULTI_TIMESTAMP_PATTERN.matcher(result);
        result = multiMatcher.replaceAll("");
        Matcher singleMatcher = SINGLE_TIMESTAMP_PATTERN.matcher(result);
        result = singleMatcher.replaceAll("");
        result = result.replaceAll("_+$", "");
        return result;
    }

    /**
     * 加载已有文件的名称和内容到编辑框
     */
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
        String fileNameWithoutExt = fileName;
        if (fileName.endsWith(".txt")) {
            fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        }
        String displayName = needHandleTimestamp
                ? removeAllTimestampFormats(fileNameWithoutExt)
                : fileNameWithoutExt;
        etFileName.setText(displayName);
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

    /**
     * 设置文本变化监听，标记未保存状态并清理文件名中的时间戳
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
                String original = s.toString();
                String cleanedTitle = removeAllTimestampFormats(original);
                if (!cleanedTitle.equals(original)) {
                    int cursorPos = etFileName.getSelectionStart();
                    s.replace(0, s.length(), cleanedTitle);
                    etFileName.setSelection(Math.min(cursorPos, cleanedTitle.length()));
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
     * 自动保存编辑内容，处理新建/编辑文件的命名、路径、时间戳逻辑
     */
    private void autoSave() {
        if (isSaved) return;

        // 新增：记录保存状态（防进程被杀）
        SharedPreferences sp = getSharedPreferences("save_state", MODE_PRIVATE);
        String fileName = targetFile != null ? targetFile.getName() : etFileName.getText().toString().trim();
        sp.edit().putBoolean("is_saving_" + fileName, true).apply();

        String inputTitle = etFileName.getText().toString();
        String content = etContent.getText().toString();
        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);
        if (rootFolderName == null) rootFolderName = ROOT_FOLDER_NAME;
        File rootDir = new File(getFilesDir(), rootFolderName);
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            Toast.makeText(this, "无法创建根目录", Toast.LENGTH_SHORT).show();
            sp.edit().putBoolean("is_saving_" + fileName, false).apply();
            finish();
            return;
        }
        if (isPreEdit) {
            boolean isTitleEmpty = TextUtils.isEmpty(inputTitle.trim());
            boolean isContentEmpty = TextUtils.isEmpty(content.trim());
            if (isTitleEmpty && isContentEmpty) {
                Toast.makeText(this, "未输入内容，放弃创建", Toast.LENGTH_SHORT).show();
                sp.edit().putBoolean("is_saving_" + fileName, false).apply();
                finish();
                return;
            }
            String rawTitle = isTitleEmpty
                    ? getContentSubtitle(content)
                    : inputTitle;
            String titleWithoutEdgeSpace = rawTitle.trim();
            String cleanedTitle = cleanFileName(titleWithoutEdgeSpace);
            String timestampSuffix = "";
            if (needHandleTimestamp) {
                String randomStr = UniqueFileNameHandler.generateRandomString();
                String millisTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();
                timestampSuffix = "_" + randomStr + "_" + millisTimestamp;
            }
            File targetDirectory = currentDir;
            String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDir, targetDirectory, cleanedTitle, timestampSuffix
            );
            targetFile = new File(targetDirectory, uniqueFileName);
            try {
                if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                    Toast.makeText(this, "无法创建目标目录", Toast.LENGTH_SHORT).show();
                    sp.edit().putBoolean("is_saving_" + fileName, false).apply();
                    finish();
                    return;
                }

                String finalContent = processContentForSaving(
                        content,
                        targetDirectory,
                        rootFolderName,
                        isRootDirectory
                );

                // ========== 增强版原子保存：新建文件 ==========
                boolean saveSuccess = atomicSave(targetFile, finalContent);
                if (saveSuccess) {
                    isSaved = true;
                    Toast.makeText(this, "文件创建成功", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_REFRESH);
                } else {
                    Toast.makeText(this, "创建文件失败", Toast.LENGTH_SHORT).show();
                }
                // 清除保存状态
                sp.edit().putBoolean("is_saving_" + targetFile.getName(), false).apply();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "创建异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                sp.edit().putBoolean("is_saving_" + fileName, false).apply();
            }
        }
        else {
            if (targetFile == null || !targetFile.exists()) {
                Toast.makeText(this, "文件不存在，无法保存", Toast.LENGTH_SHORT).show();
                sp.edit().putBoolean("is_saving_" + fileName, false).apply();
                finish();
                return;
            }
            File actualDirectory = targetFile.getParentFile();
            String originalFileName = targetFile.getName();
            String originalFileNameWithoutExt = originalFileName.endsWith(".txt")
                    ? originalFileName.substring(0, originalFileName.lastIndexOf("."))
                    : originalFileName;
            String cleanedOriginalTitle = needHandleTimestamp
                    ? removeAllTimestampFormats(originalFileNameWithoutExt)
                    : originalFileNameWithoutExt;
            String newTitleInput = etFileName.getText().toString();
            String newTitleWithoutEdgeSpace = newTitleInput.trim();
            newTitleWithoutEdgeSpace = cleanFileName(newTitleWithoutEdgeSpace);
            String cleanedNewTitle = needHandleTimestamp
                    ? removeAllTimestampFormats(newTitleWithoutEdgeSpace)
                    : newTitleWithoutEdgeSpace;
            if (TextUtils.isEmpty(cleanedNewTitle)) {
                cleanedNewTitle = cleanedOriginalTitle;
            }
            boolean needCheckDuplicate = !cleanedNewTitle.equals(cleanedOriginalTitle);
            String newTimestampSuffix = "";
            if (needHandleTimestamp && originalFileName.endsWith(".txt")) {
                String[] timestampStruct = parseTimestampStructure(originalFileNameWithoutExt);
                String newRandomStr = "";
                ArrayList<String> newTimestamps = new ArrayList<>();
                String newMillisTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();
                if (timestampStruct.length == 0) {
                    newRandomStr = UniqueFileNameHandler.generateRandomString();
                    newTimestamps.add(newMillisTimestamp);
                } else if (timestampStruct.length == 2) {
                    newRandomStr = timestampStruct[0];
                    newTimestamps.add(timestampStruct[1]);
                    newTimestamps.add(newMillisTimestamp);
                } else if (timestampStruct.length >= 3) {
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
            String newFileName;
            if (needCheckDuplicate) {
                newFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                        rootDir, actualDirectory, cleanedNewTitle, newTimestampSuffix
                );
            } else {
                newFileName = cleanedNewTitle + newTimestampSuffix + ".txt";
            }
            File newFile = new File(actualDirectory, newFileName);
            boolean fileOperationSuccess = true;
            if (!targetFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                if (!targetFile.renameTo(newFile)) {
                    try {
                        // 复制原文件内容到新文件（原子方式）
                        String oldContent = readFileContent(targetFile);
                        atomicSave(newFile, oldContent);

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
                File parsedDirectory = parseFirstLinePath(content, rootDir, isRootDirectory);
                boolean pathMismatch = !parsedDirectory.getAbsolutePath().equals(actualDirectory.getAbsolutePath());
                String finalContent = processContentForSaving(
                        content,
                        pathMismatch ? actualDirectory : parsedDirectory,
                        rootFolderName,
                        isRootDirectory
                );

                // ========== 增强版原子保存：覆盖更新文件 ==========
                try {
                    atomicSave(targetFile, finalContent);
                    isSaved = true;
                    Toast.makeText(this, pathMismatch ? "文件路径标识已纠正并保存" : "文件更新成功", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_REFRESH);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            // 清除保存状态
            sp.edit().putBoolean("is_saving_" + targetFile.getName(), false).apply();
        }
        if (!isPreEdit && targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }
        hideSoftInput();
        finish();
    }

    // ========== 增强版原子保存核心方法（防进程被杀+数据兜底） ==========
    private boolean atomicSave(File targetFile, String content) throws IOException {
        if (targetFile == null || targetFile.getParentFile() == null) return false;

        // 1. 确保目录存在
        if (!targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

        // 2. 创建临时文件（带时间戳，避免冲突）
        String tempFileName = targetFile.getName() + "_atomic_tmp_" + System.currentTimeMillis();
        File tempFile = new File(targetFile.getParentFile(), tempFileName);

        FileOutputStream fos = null;
        FileLock lock = null;
        try {
            // 3. 打开临时文件并加独占锁
            fos = new FileOutputStream(tempFile);
            lock = fos.getChannel().lock();

            // 4. 写入内容并强制刷盘
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            fos.write(contentBytes);
            fos.flush();
            fos.getFD().sync(); // 强制同步到硬件，进程被杀也能保证内容落地

            // 5. 校验写入完整性
            if (tempFile.length() != contentBytes.length) {
                throw new IOException("临时文件写入不全，长度不匹配");
            }

            // 6. 备份原文件（兜底）
            File backupFile = new File(targetFile.getParentFile(), targetFile.getName() + "_backup");
            boolean replaceSuccess = true;
            if (targetFile.exists()) {
                replaceSuccess = targetFile.renameTo(backupFile);
                if (!replaceSuccess) {
                    throw new IOException("原文件备份失败");
                }
            }

            // 7. 原子替换原文件
            replaceSuccess = tempFile.renameTo(targetFile);
            if (replaceSuccess) {
                // 替换成功：删除备份
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                return true;
            } else {
                // 替换失败：恢复备份
                if (backupFile.exists()) {
                    backupFile.renameTo(targetFile);
                }
                return false;
            }
        } finally {
            // 8. 兜底清理：释放资源+临时文件
            if (lock != null) lock.release();
            if (fos != null) fos.close();
            if (tempFile.exists() && !tempFile.equals(targetFile)) {
                tempFile.delete();
            }
        }
    }

    // ========== 增强版原子保存（同步版） ==========
    private boolean atomicSaveSync(File targetFile, String content) throws IOException {
        // 复用增强版原子保存逻辑，适配同步场景
        return atomicSave(targetFile, content);
    }

    /**
     * 清理文件名中的非法字符（\ / : * ? " < > |）
     * @return String 合法的文件名
     */
    private String cleanFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "");
    }

    /**
     * 解析文件内容第一行的路径标识，返回对应的目录
     * @return File 解析后的目录，失败返回根目录
     */
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

    /**
     * 处理保存内容，添加路径标识和时间戳
     * @return String 处理后的文件内容
     */
    private String processContentForSaving(String content, File targetDir, String rootFolderName, boolean isRootDirectory) {
        if (isRootDirectory) {
            // 根目录：直接返回原始内容（不加时间戳）
            return content;
        }
        // 非根目录：仅添加路径标识，不处理时间戳
        String dirPath = MainActivity.getRelativeDirPath(targetDir, rootFolderName);
        String safeDirPath = dirPath.replace("/", "_slash_");
        String pathLine = "【" + safeDirPath + "】";

        boolean hasPathIdentifier = false;
        String[] lines = content.split("\n", 2);
        if (lines.length > 0 && !TextUtils.isEmpty(lines[0])) {
            hasPathIdentifier = FIRST_LINE_PATH_PATTERN.matcher(lines[0]).matches();
        }

        String restContent;
        if (hasPathIdentifier) {
            // 已有路径标识：保留除第一行外的原始内容（不加时间戳）
            restContent = lines.length > 1 ? lines[1] : "";
        } else {
            // 无路径标识：保留全部原始内容（不加时间戳）
            restContent = content;
        }

        // 核心修改：直接返回「路径行 + 换行 + 原始内容」，不再调用addContentTimestamp
        return pathLine + "\n" + restContent;
    }

    /**
     * 读取文件内容
     * @return String 文件内容
     * @throws IOException 文件读取异常
     */
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

    /**
     * 从内容中提取副标题（用于无标题文件命名）
     * @return String 内容副标题
     */
    private String getContentSubtitle(String content) {
        if (TextUtils.isEmpty(content.trim())) return "无内容文件";
        String trimmedContent = content.trim();
        return trimmedContent.length() <= MAX_TITLE_LEN
                ? trimmedContent
                : trimmedContent.substring(0, MAX_TITLE_LEN) + "…";
    }

    /**
     * 写入内容到文件（仅用于兼容旧逻辑，实际已被原子保存替代）
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
     * 为文件内容添加日期时间戳（最后一行）
     * @return String 添加时间戳后的内容
     */
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

    /**
     * 处理压缩文件夹和分享文件的意图
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
     * 压缩文件夹为ZIP文件（自动处理命名冲突）
     */
    private void zipFolder(File folder) {
        if (!folder.exists() || !folder.isDirectory()) {
            Toast.makeText(this, "文件夹不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        String zipFileName = folder.getName() + ".zip";
        File zipFile = new File(folder.getParentFile(), zipFileName);
        int counter = 1;
        while (zipFile.exists()) {
            zipFileName = folder.getName() + "(" + counter + ").zip";
            zipFile = new File(folder.getParentFile(), zipFileName);
            counter++;
        }
        final File finalZipFile = zipFile;
        new Thread(() -> {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(finalZipFile))) {
                zos.setLevel(9);
                addFolderToZip(folder, folder.getName(), zos);
                runOnUiThread(() -> {
                    Toast.makeText(this, "压缩成功：" + finalZipFile.getName(), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_REFRESH);
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    Toast.makeText(this, "压缩失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    if (finalZipFile.exists()) {
                        finalZipFile.delete();
                    }
                });
            }
        }).start();
    }

    /**
     * 递归添加文件夹内容到ZIP输出流
     * @throws IOException ZIP写入异常
     */
    private void addFolderToZip(File folder, String parentEntryName, ZipOutputStream zos) throws IOException {
        ZipEntry dirEntry = new ZipEntry(parentEntryName + "/");
        dirEntry.setTime(folder.lastModified());
        zos.putNextEntry(dirEntry);
        zos.closeEntry();
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
                    byte[] buffer = new byte[BUFFER_SIZE];
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
     * 分享文件到其他应用（基于FileProvider）
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
     * 根据文件名获取MIME类型
     * @return String MIME类型
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
    protected void onDestroy() {
        super.onDestroy();
        if (isChangingConfigurations()) {
            if (!isPreEdit && targetFile != null && targetFile.exists()) {
                PreferenceUtils.saveLastPageType(this, "editor");
                PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 滑动关闭APP时，强制同步保存（阻塞直到保存完成）
        if (!isSaved) {
            saveContentSync(); // 同步保存，不使用线程，确保执行完再退出
        }
    }

    /**
     * 同步保存内容（无异步、无线程，确保滑动关闭时必执行完）
     * 核心：放弃所有异步操作，直接写入文件，哪怕卡顿100ms，也要保证内容保存
     */
    private void saveContentSync() {
        // 空内容直接标记为已保存
        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString().trim();
        if (TextUtils.isEmpty(inputTitle) && TextUtils.isEmpty(content)) {
            isSaved = true;
            return;
        }

        // 新增：记录保存状态
        SharedPreferences sp = getSharedPreferences("save_state", MODE_PRIVATE);
        String fileName = targetFile != null ? targetFile.getName() : inputTitle;
        sp.edit().putBoolean("is_saving_" + fileName, true).commit();

        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);
        if (rootFolderName == null) rootFolderName = ROOT_FOLDER_NAME;
        File rootDir = new File(getFilesDir(), rootFolderName);

        // 同步创建目录（阻塞直到创建完成）
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }

        try {
            // 1. 新建文件场景
            if (isPreEdit) {
                String rawTitle = TextUtils.isEmpty(inputTitle) ? getContentSubtitle(content) : inputTitle;
                String cleanedTitle = cleanFileName(rawTitle.trim());
                String timestampSuffix = needHandleTimestamp
                        ? "_" + UniqueFileNameHandler.generateRandomString() + "_" + UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp()
                        : "";
                File targetDirectory = currentDir != null ? currentDir : rootDir;
                if (!targetDirectory.exists()) {
                    targetDirectory.mkdirs(); // 同步创建目录
                }
                String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                        rootDir, targetDirectory, cleanedTitle, timestampSuffix
                );
                targetFile = new File(targetDirectory, uniqueFileName);

                // ========== 增强版原子保存：新建文件 ==========
                String finalContent = processContentForSaving(content, targetDirectory, rootFolderName, isRootDirectory);
                boolean createSuccess = atomicSaveSync(targetFile, finalContent);
                if (createSuccess) {
                    // 记录光标位置和保存路径
                    cursorPosition = etContent.getSelectionStart();
                    savedNewFilePath = targetFile.getAbsolutePath();
                    isSaved = true;
                }

                // 2. 编辑已有文件场景
            } else {
                if (targetFile == null || !targetFile.exists()) {
                    isSaved = true;
                    sp.edit().putBoolean("is_saving_" + fileName, false).commit();
                    return;
                }
                // 处理文件名变更（同步逻辑）
                String originalFileName = targetFile.getName();
                String originalFileNameWithoutExt = originalFileName.endsWith(".txt")
                        ? originalFileName.substring(0, originalFileName.lastIndexOf("."))
                        : originalFileName;
                String cleanedOriginalTitle = needHandleTimestamp
                        ? removeAllTimestampFormats(originalFileNameWithoutExt)
                        : originalFileNameWithoutExt;
                String cleanedNewTitle = TextUtils.isEmpty(inputTitle.trim())
                        ? cleanedOriginalTitle
                        : removeAllTimestampFormats(cleanFileName(inputTitle.trim()));
                boolean needCheckDuplicate = !cleanedNewTitle.equals(cleanedOriginalTitle);
                String newTimestampSuffix = "";

                if (needHandleTimestamp && originalFileName.endsWith(".txt")) {
                    String[] timestampStruct = parseTimestampStructure(originalFileNameWithoutExt);
                    String newRandomStr = timestampStruct.length > 0 ? timestampStruct[0] : UniqueFileNameHandler.generateRandomString();
                    ArrayList<String> newTimestamps = new ArrayList<>();
                    String newMillisTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();
                    if (timestampStruct.length >= 2) {
                        for (int i = 1; i < timestampStruct.length; i++) {
                            newTimestamps.add(timestampStruct[i]);
                        }
                    }
                    newTimestamps.add(newMillisTimestamp);
                    StringBuilder suffixBuilder = new StringBuilder("_").append(newRandomStr);
                    for (String ts : newTimestamps) {
                        suffixBuilder.append("_").append(ts);
                    }
                    newTimestampSuffix = suffixBuilder.toString();
                }

                String newFileName = needCheckDuplicate
                        ? UniqueFileNameHandler.getGlobalUniqueFileName(rootDir, targetFile.getParentFile(), cleanedNewTitle, newTimestampSuffix)
                        : cleanedNewTitle + newTimestampSuffix + ".txt";
                File newFile = new File(targetFile.getParentFile(), newFileName);

                // 同步重命名/复制（阻塞直到完成）
                if (!targetFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                    if (!targetFile.renameTo(newFile)) {
                        // ========== 原子复制文件 ==========
                        atomicCopyFileSync(targetFile, newFile);
                        targetFile.delete(); // 同步删除原文件
                    }
                    targetFile = newFile; // 更新为新文件名
                }

                // ========== 增强版原子保存：同步写入最终内容 ==========
                File parsedDirectory = parseFirstLinePath(content, rootDir, isRootDirectory);
                boolean pathMismatch = !parsedDirectory.getAbsolutePath().equals(targetFile.getParentFile().getAbsolutePath());
                String finalContent = processContentForSaving(
                        content, pathMismatch ? targetFile.getParentFile() : parsedDirectory, rootFolderName, isRootDirectory
                );
                atomicSaveSync(targetFile, finalContent);
                isSaved = true;
            }
            // 清除保存状态
            sp.edit().putBoolean("is_saving_" + (targetFile != null ? targetFile.getName() : fileName), false).commit();
        } catch (Exception e) {
            e.printStackTrace();
            // 即使异常，也标记为已保存（避免重复尝试）
            isSaved = true;
            sp.edit().putBoolean("is_saving_" + fileName, false).commit();
        }

        // 保存完成后，录入新文件名路径
        if (targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastPageType(this, "editor");
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
            Log.d("FileEditor", "保存完成，录入新文件名路径：" + targetFile.getAbsolutePath());
        }
    }

    // ========== 原子复制文件（同步版） ==========
    private void atomicCopyFileSync(File sourceFile, File targetFile) throws IOException {
        // 先原子写入到临时文件，再替换目标文件
        FileInputStream fis = new FileInputStream(sourceFile);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        fis.close();
        // 调用增强版原子保存方法
        atomicSaveSync(targetFile, new String(bos.toByteArray(), StandardCharsets.UTF_8));
    }

    // ===== onResume方法（保留原有逻辑）=====
    @Override
    protected void onResume() {
        super.onResume();
        // 仅处理「新建文件后台保存后重新打开」的特例场景
        if (!TextUtils.isEmpty(savedNewFilePath) && new File(savedNewFilePath).exists()) {
            isPreEdit = false; // 转为编辑已有文件模式
            targetFile = new File(savedNewFilePath);

            // 调用原有加载方法
            loadExistingFileData(getIntent().getBooleanExtra("need_handle_timestamp", true));

            // 替换_slash_为/ + 定位光标
            etContent.postDelayed(() -> {
                String originalContent = etContent.getText().toString();
                String realContent = replaceSlashSymbol(originalContent);
                etContent.setText(realContent);

                int firstEndSymbolPos = realContent.indexOf("】");
                if (firstEndSymbolPos != -1) {
                    int nextNewLinePos = realContent.indexOf("\n", firstEndSymbolPos);
                    int targetCursorPos = nextNewLinePos != -1 ? nextNewLinePos + 1 : firstEndSymbolPos + 1;
                    targetCursorPos = Math.min(targetCursorPos, realContent.length());
                    etContent.setSelection(targetCursorPos);
                    Log.d("FileEditor", "新建文件切回：光标定位到字符数" + targetCursorPos);
                } else {
                    int finalPos = Math.min(cursorPosition, realContent.length());
                    etContent.setSelection(finalPos);
                }
            }, 100);

            // 清空标记
            savedNewFilePath = "";
        }
    }

    private String replaceSlashSymbol(String content) {
        if (TextUtils.isEmpty(content)) {
            return "";
        }
        // 仅在新建文件切回前台时，临时替换 _slash_ 为 /
        return content.replace("_slash_", "/");
    }
}