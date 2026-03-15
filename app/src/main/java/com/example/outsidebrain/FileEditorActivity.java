/*
软件名称：快乐文字
版本号：V1.0
功能描述：实现TXT文件编辑、保存、重命名，自动处理时间戳、命名冲突，提供文件夹压缩、文件分享功能，限制操作范围保障数据安全
所属模块：文件编辑模块
开发语言：Java
*/
package com.example.outsidebrain;
import android.widget.Toast;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.SharedPreferences;
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
        String inputTitle = etFileName.getText().toString();
        String content = etContent.getText().toString();
        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);
        if (rootFolderName == null) rootFolderName = ROOT_FOLDER_NAME;
        File rootDir = new File(getFilesDir(), rootFolderName);
        if (!rootDir.exists() && !rootDir.mkdirs()) {
            Toast.makeText(this, "无法创建根目录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (isPreEdit) {
            boolean isTitleEmpty = TextUtils.isEmpty(inputTitle.trim());
            boolean isContentEmpty = TextUtils.isEmpty(content.trim());
            if (isTitleEmpty && isContentEmpty) {
                Toast.makeText(this, "未输入内容，放弃创建", Toast.LENGTH_SHORT).show();
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
                    finish();
                    return;
                }
                if (targetFile.createNewFile()) {
                    String finalContent = processContentForSaving(
                            content,
                            targetDirectory,
                            rootFolderName,
                            isRootDirectory
                    );
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
        else {
            if (targetFile == null || !targetFile.exists()) {
                Toast.makeText(this, "文件不存在，无法保存", Toast.LENGTH_SHORT).show();
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
                File parsedDirectory = parseFirstLinePath(content, rootDir, isRootDirectory);
                boolean pathMismatch = !parsedDirectory.getAbsolutePath().equals(actualDirectory.getAbsolutePath());
                String finalContent = processContentForSaving(
                        content,
                        pathMismatch ? actualDirectory : parsedDirectory,
                        rootFolderName,
                        isRootDirectory
                );
                writeFileContent(targetFile, finalContent);
                isSaved = true;
                Toast.makeText(this, pathMismatch ? "文件路径标识已纠正并保存" : "文件更新成功", Toast.LENGTH_SHORT).show();
                setResult(RESULT_REFRESH);
            }
        }
        if (!isPreEdit && targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }
        hideSoftInput();
        finish();
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
            return addContentTimestamp(content);
        }
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
            restContent = lines.length > 1 ? lines[1] : "";
        } else {
            restContent = content;
        }
        String contentWithTimestamp = addContentTimestamp(restContent);
        return pathLine + "\n" + contentWithTimestamp;
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
     * 写入内容到文件
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
}