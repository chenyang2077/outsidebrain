/*
软件名称：流动文档软件V1.0
版本号：V1.0
功能描述：实现TXT文件编辑、保存、重命名，自动处理时间戳、命名冲突，限制操作范围保障数据安全
所属模块：文件编辑模块
开发语言：Java
源码状态：完整未删减
*/
package com.example.outsidebrainkxmt563980;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.database.Cursor;
import android.provider.OpenableColumns;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 文件编辑页面：实现TXT文件编辑、保存、重命名，自动处理时间戳、命名冲突，
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
    // 历史版本保存（自动备份旧文件）
    private File historyVersionDir;
    private static final int MAX_TITLE_LEN = 31;
    private static final String ROOT_FOLDER_NAME = "主页根目录";
    private String searchKeyword;
    private static final Pattern RANDOM_STR_PATTERN = Pattern.compile("[A-Za-z0-9]{6}");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{17}");
    private static final Pattern SINGLE_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private static final Pattern MULTI_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})+");
    private static final Pattern FULL_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})*$");
    private int cursorPosition = 0;
    private Uri uriFromExternal = null;
    private String originalFileNameForEdit;
    private String savedNewFilePath = "";
    private final Stack<String> undoStack = new Stack<>();
    private final Stack<String> redoStack = new Stack<>();
    private boolean isHistoryChange = false;
    private View btnUndo;
    private View btnRedo;
    private boolean hasContentChanged = false;
    private LinearLayout btnContainer;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);
        etFileName = findViewById(R.id.et_file_name);
        etContent = findViewById(R.id.et_content);
        btnContainer = findViewById(R.id.btn_container);
        String filePath = getIntent().getStringExtra("file_path");
        String currentDirPath = getIntent().getStringExtra("current_dir_path");
        isPreEdit = getIntent().getBooleanExtra("is_pre_edit", false);
        boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);
        SharedPreferences sp = getSharedPreferences("SearchSP", Context.MODE_PRIVATE);
        searchKeyword = sp.getString("current_keyword", "").trim();
        handleExternalFileIntent(getIntent());
        recoverFromCrash();
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
        } else if (filePath != null) {
            targetFile = new File(filePath);
            originalFileNameForEdit = targetFile.getName();
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
                        } else if (TextUtils.isEmpty(fileContent)) {
                            showKeyboard(etContent);
                        }
                    } else if (TextUtils.isEmpty(fileContent)) {
                        showKeyboard(etContent);
                    }
                }, 100);
            }
        }
        if (etContent != null && TextUtils.isEmpty(etContent.getText().toString())) {
            etContent.post(() -> showKeyboard(etContent));
        }
        initUndoHistory();
        setupTextChangeListeners();
        findViewById(R.id.btn_undo).setOnClickListener(v -> doUndo());
        findViewById(R.id.btn_redo).setOnClickListener(v -> doRedo());
        btnUndo = findViewById(R.id.btn_undo);
        btnRedo = findViewById(R.id.btn_redo);
    }
    /**
     * 加载已存在的文件数据到编辑界面
     * 自动处理文件名显示（去除时间戳后缀）
     * @param needHandleTimestamp 是否需要处理时间戳（true=自动去除后缀显示纯标题）
     */
    private void loadExistingFileData(boolean needHandleTimestamp) {
        if (targetFile == null || !targetFile.exists()) {
            Toast.makeText(this, "文件不存在，可能更改未更新", Toast.LENGTH_SHORT).show();
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
     * 设置文本变化监听器，限制标题长度、自动清理时间戳后缀、防止非法输入，记录编辑历史，实现撤销/重做功能
     */
    private void setupTextChangeListeners() {
        etFileName.addTextChangedListener(new android.text.TextWatcher() {
            private final int MAX_ALLOWED_LENGTH = 45;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                String original = s.toString();
                String cleanedTitle = removeAllTimestampFormats(original);

                if (cleanedTitle.length() > MAX_ALLOWED_LENGTH) {
                    String truncated = cleanedTitle.substring(0, MAX_ALLOWED_LENGTH);
                    s.replace(0, s.length(), truncated);
                    etFileName.post(() -> {
                        Toast.makeText(FileEditorActivity.this,
                                "标题最长只能输入 " + MAX_ALLOWED_LENGTH + " 个字符",
                                Toast.LENGTH_SHORT).show();
                    });
                }
                if (!cleanedTitle.equals(original)) {
                    int cursorPos = etFileName.getSelectionStart();
                    s.replace(0, s.length(), cleanedTitle);
                    etFileName.setSelection(Math.min(cursorPos, cleanedTitle.length()));
                }

                isSaved = false;

            }
        });
        etContent.addTextChangedListener(new android.text.TextWatcher() {
            private String oldText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                oldText = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isHistoryChange) {
                    isHistoryChange = false;
                    return;
                }
                String now = s.toString();
                if (!now.equals(oldText)) {
                    undoStack.push(oldText);
                    redoStack.clear();
                    isSaved = false;
                    btnContainer.setVisibility(View.VISIBLE);

                    updateUndoRedoBtnVisibility();
                }
            }
        });
    }
    /**
     *初始化撤销历史
     */
    private void initUndoHistory() {
        String initContent = etContent.getText().toString();
        undoStack.clear();
        redoStack.clear();
        undoStack.push(initContent);
        updateUndoRedoBtnVisibility();
    }
    /**
     * 更新撤销/重做按钮的显示与隐藏状态，根据撤销栈和重做栈的数据量判断按钮是否可用
     */
    private void updateUndoRedoBtnVisibility() {
        if (btnUndo == null || btnRedo == null) return;
        boolean canUndo = undoStack.size() > 1;
        boolean canRedo = !redoStack.isEmpty();
        btnUndo.setVisibility(canUndo ? View.VISIBLE : View.GONE);
        btnRedo.setVisibility(canRedo ? View.VISIBLE : View.GONE);
    }
    /**
     * 撤销
     */
    private void doUndo() {
        if (undoStack.size() <= 1) {
        Toast.makeText(this, "已到最初状态", Toast.LENGTH_SHORT).show();
        return;
        }
        String currentContent = etContent.getText().toString();
        redoStack.push(currentContent);
        String targetContent = undoStack.pop();
        isHistoryChange = true;
        etContent.setText(targetContent);
        setSmartCursorAfterChange(currentContent, targetContent);
        isSaved = false;
        updateUndoRedoBtnVisibility();
    }
    /**
     * 重做
     */
    private void doRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "无可用重做", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentContent = etContent.getText().toString();
        undoStack.push(currentContent);
        String targetContent = redoStack.pop();
        isHistoryChange = true;
        etContent.setText(targetContent);
        setSmartCursorAfterChange(currentContent, targetContent);
        isSaved = false;
        updateUndoRedoBtnVisibility();
    }
    /**
     * 撤销/重做 智能定位光标，撤销：光标放变化段后方， 重做：光标放变化段后方
     */
    private void setSmartCursorAfterChange(String beforeText, String afterText) {
        int sameStart = 0;
        int minLen = Math.min(beforeText.length(), afterText.length());
        while (sameStart < minLen
                && beforeText.charAt(sameStart) == afterText.charAt(sameStart)) {
            sameStart++;
        }
        int sameEnd = 0;
        while (sameEnd < minLen - sameStart
                && beforeText.charAt(beforeText.length() - 1 - sameEnd)
                == afterText.charAt(afterText.length() - 1 - sameEnd)) {
            sameEnd++;
        }
        int targetPos = afterText.length() - sameEnd;
        targetPos = Math.max(0, Math.min(targetPos, afterText.length()));
        etContent.setSelection(targetPos);
    }
    /**
     * 处理来自外部应用的文件打开请求，用于手机自带的文件管理软件的txt文件使用此编辑页面。
     */
    private void handleExternalFileIntent(Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri uri = intent.getData();
        if (uri == null) {
            finish();
            return;
        }
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            br.close();
            is.close();
            String fileName = "未命名文件";
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }
            this.uriFromExternal = uri;
            this.targetFile = null;
            this.isPreEdit = false;
            if (fileName.toLowerCase().endsWith(".txt")) {
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
            }
            etFileName.setText(fileName);
            etContent.setText(content.toString());
        } catch (Exception e) {
            e.printStackTrace();
            finish();
        }
    }
    /**
     * 应用崩溃后自动恢复文件， 修复原子保存的临时文件、备份文件，检测异常保存状态
     */
    private void recoverFromCrash() {
        File rootDir = new File(getFilesDir(), ROOT_FOLDER_NAME);
        if (!rootDir.exists()) return;

        File[] files = rootDir.listFiles((dir, name) ->
                name.contains("_atomic_tmp_") || name.endsWith("_backup")
        );
        if (files == null || files.length == 0) return;

        for (File file : files) {
            String fileName = file.getName();
            if (fileName.contains("_atomic_tmp_")) {
                String originalFileName = fileName.split("_atomic_tmp_")[0];
                File originalFile = new File(rootDir, originalFileName);
                if (originalFile.exists()) {
                    originalFile.delete();
                }
                boolean renamed = file.renameTo(originalFile);
                if (renamed) {
                    Toast.makeText(this, "恢复崩溃未保存的文件：" + originalFileName, Toast.LENGTH_LONG).show();
                }
            } else if (fileName.endsWith("_backup")) {
                String originalFileName = fileName.replace("_backup", "");
                File originalFile = new File(rootDir, originalFileName);
                if (!originalFile.exists()) {
                    boolean renamed = file.renameTo(originalFile);
                    if (renamed) {
                        Toast.makeText(this, "恢复崩溃损坏的文件：" + originalFileName, Toast.LENGTH_LONG).show();
                    }
                } else {
                    file.delete();
                }
            }
        }
        SharedPreferences sp = getSharedPreferences("save_state", Context.MODE_PRIVATE);
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
     * 自动弹出软键盘并让输入框获得焦点
     */
    private void showKeyboard(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }
    /**
     * 移除文件名中所有时间戳后缀，得到干净标题（核心名称），用于显示编辑和保存查重
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
     * 页面销毁时保存编辑状态
     */
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
    /**
     * 后台自动保存
     */
    protected void onPause() {
        super.onPause();
        if (!isSaved) {
            saveContentSync();
        }
    }
    /**
     * 后台自动保存执行方法
     */
    private void saveContentSync() {
        if (uriFromExternal != null) {
            try {
                String content = etContent.getText().toString();
                OutputStream os = getContentResolver().openOutputStream(uriFromExternal, "wt");
                os.write(content.getBytes(StandardCharsets.UTF_8));
                os.close();
                isSaved = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString();
        SharedPreferences sp = getSharedPreferences("save_state", Context.MODE_PRIVATE);
        String fileName = targetFile != null ? targetFile.getName() : inputTitle;
        sp.edit().putBoolean("is_saving_" + fileName, true).commit();
        try {
            String rootFolderName = getIntent().getStringExtra("root_folder_name");
            boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);
            boolean needHandleTimestamp = getIntent().getBooleanExtra("need_handle_timestamp", true);
            if (rootFolderName == null) rootFolderName = ROOT_FOLDER_NAME;
            File rootDir = new File(getFilesDir(), rootFolderName);
            if (!rootDir.exists()) {
                rootDir.mkdirs();
            }
            if (isPreEdit) {
                if (TextUtils.isEmpty(inputTitle) && TextUtils.isEmpty(content)) {
                    isSaved = true;
                    sp.edit().putBoolean("is_saving_" + fileName, false).commit();
                    return;
                }
                String rawTitle = TextUtils.isEmpty(inputTitle) ? getContentSubtitle(content) : inputTitle;
                String cleanedTitle = cleanFileName(rawTitle.trim());
                String timestampSuffix = needHandleTimestamp
                        ? "_" + generateRandomString() + "_" + generateMillisTimestamp()
                        : "";
                File targetDirectory = currentDir != null ? currentDir : rootDir;
                if (!targetDirectory.exists()) {
                    targetDirectory.mkdirs();
                }
                String finalCoreName = getNonConflictCoreNameInFolder(targetDirectory, cleanedTitle);
                String tempFileName = finalCoreName + timestampSuffix + ".txt";
                File uniqueFile = new File(targetDirectory, tempFileName);
                targetFile = uniqueFile;
                String finalContent = content;
                boolean createSuccess = atomicSaveSync(targetFile, finalContent);
                if (createSuccess) {
                    cursorPosition = etContent.getSelectionStart();
                    savedNewFilePath = targetFile.getAbsolutePath();
                    isSaved = true;
                }
            } else {
                if (targetFile == null || !targetFile.exists()) {
                    isSaved = true;
                    sp.edit().putBoolean("is_saving_" + fileName, false).commit();
                    return;
                }
                saveFileHistoryVersion(targetFile);
                String originalContent = readFileContent(targetFile);
                File actualDirectory = targetFile.getParentFile();
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
                boolean isTitleEmpty = TextUtils.isEmpty(cleanedNewTitle);
                boolean isContentEmpty = TextUtils.isEmpty(content);
                if (isTitleEmpty || isContentEmpty) {
                    atomicSaveSync(targetFile, originalContent);
                    String tip = isTitleEmpty && isContentEmpty
                            ? "文件名和内容均为空，放弃修改"
                            : (isTitleEmpty ? "文件名称为空，放弃修改" : "文件内容为空，放弃修改");
                    runOnUiThread(() -> Toast.makeText(this, tip, Toast.LENGTH_SHORT).show());
                    isSaved = true;
                    sp.edit().putBoolean("is_saving_" + fileName, false).commit();
                    return;
                }
                if (TextUtils.isEmpty(cleanedNewTitle)) {
                    cleanedNewTitle = cleanedOriginalTitle;
                }
                boolean needCheckDuplicate = !cleanedNewTitle.equals(cleanedOriginalTitle);
                String newTimestampSuffix = "";

                if (needHandleTimestamp && originalFileName.endsWith(".txt")) {
                    String[] timestampStruct = parseTimestampStructure(originalFileNameWithoutExt);
                    String newRandomStr = "";
                    ArrayList<String> newTimestamps = new ArrayList<>();
                    String newMillisTimestamp = generateMillisTimestamp();
                    boolean hasNoSuffix = !FULL_TIMESTAMP_PATTERN.matcher(originalFileNameWithoutExt).find();
                    if (hasNoSuffix) {
                        newRandomStr = generateRandomString();
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
                    String finalCoreName = getNonConflictCoreNameInFolder(actualDirectory, cleanedNewTitle);
                    newFileName = finalCoreName + newTimestampSuffix + ".txt";
                } else {
                    newFileName = cleanedNewTitle + newTimestampSuffix + ".txt";
                }

                File newFile = new File(targetFile.getParentFile(), newFileName);

                if (!targetFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                    if (!targetFile.renameTo(newFile)) {
                        atomicCopyFileSync(targetFile, newFile);
                        if (!targetFile.delete()) {
                            Log.w("FileEditor", "同步保存时无法删除原文件：" + targetFile.getAbsolutePath());
                        }
                    }
                    targetFile = newFile;
                    Log.d("FileEditor", "同步保存-文件名更新：" + originalFileName + " → " + newFileName);
                }
                String finalContent = content;
                atomicSaveSync(targetFile, finalContent);
                isSaved = true;
            }
            String finalFileName = targetFile != null ? targetFile.getName() : fileName;
            sp.edit().putBoolean("is_saving_" + finalFileName, false).commit();
        } catch (Exception e) {
            e.printStackTrace();
            isSaved = true;
            sp.edit().putBoolean("is_saving_" + fileName, false).commit();
            runOnUiThread(() -> Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
        if (targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastPageType(this, "editor");
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
            Log.d("FileEditor", "同步保存完成，录入新文件名路径：" + targetFile.getAbsolutePath());
        }
    }
    /**
     * 返回键自动保存
     */
    @Override
    public void onBackPressed() {
        autoSave();
        super.onBackPressed();
    }
    /**
     * 保存
     */
    private void autoSave() {
        if (isSaved) return;
        if (uriFromExternal != null) {
            try {
                String content = etContent.getText().toString();
                OutputStream os = getContentResolver().openOutputStream(uriFromExternal, "wt");
                os.write(content.getBytes(StandardCharsets.UTF_8));
                os.close();
                isSaved = true;
                Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
            }
            hideSoftInput();
            finish();
            return;
        }
        SharedPreferences sp = getSharedPreferences("save_state", Context.MODE_PRIVATE);
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
            boolean isContentEmpty = TextUtils.isEmpty(content);
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
                String randomStr = generateRandomString();
                String millisTimestamp = generateMillisTimestamp();
                timestampSuffix = "_" + randomStr + "_" + millisTimestamp;
            }
            File targetDirectory = currentDir;
            String finalCoreName = getNonConflictCoreNameInFolder(targetDirectory, cleanedTitle);
            String tempFileName = finalCoreName + timestampSuffix + ".txt";
            File uniqueFile = new File(targetDirectory, tempFileName);
            targetFile = uniqueFile;
            try {
                if (!targetDirectory.exists() && !targetDirectory.mkdirs()) {
                    Toast.makeText(this, "无法创建目标目录", Toast.LENGTH_SHORT).show();
                    sp.edit().putBoolean("is_saving_" + fileName, false).apply();
                    finish();
                    return;
                }
                String finalContent = content;
                boolean saveSuccess = atomicSave(targetFile, finalContent);
                if (saveSuccess) {
                    isSaved = true;
                    Toast.makeText(this, "文件创建成功", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_REFRESH);
                } else {
                    Toast.makeText(this, "创建文件失败", Toast.LENGTH_SHORT).show();
                }
                sp.edit().putBoolean("is_saving_" + targetFile.getName(), false).apply();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "创建异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                sp.edit().putBoolean("is_saving_" + fileName, false).apply();
            }
        } else {
            if (targetFile == null || !targetFile.exists()) {
                Toast.makeText(this, "文件不存在，无法保存", Toast.LENGTH_SHORT).show();
                sp.edit().putBoolean("is_saving_" + fileName, false).apply();
                finish();
                return;
            }
            saveFileHistoryVersion(targetFile);
            String originalContent = "";
            try {
                originalContent = readFileContent(targetFile);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "读取源文件失败", Toast.LENGTH_SHORT).show();
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

            boolean isTitleEmpty = TextUtils.isEmpty(cleanedNewTitle);
            boolean isContentEmpty = TextUtils.isEmpty(content);
            if (isTitleEmpty || isContentEmpty) {
                try {
                    atomicSave(targetFile, originalContent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String tip = isTitleEmpty && isContentEmpty
                        ? "文件名和内容均为空，放弃修改"
                        : (isTitleEmpty ? "文件名称为空，放弃修改" : "文件内容为空，放弃修改");
                Toast.makeText(this, tip, Toast.LENGTH_SHORT).show();
                sp.edit().putBoolean("is_saving_" + fileName, false).apply();
                finish();
                return;
            }
            if (TextUtils.isEmpty(cleanedNewTitle)) {
                cleanedNewTitle = cleanedOriginalTitle;
            }
            boolean needCheckDuplicate = !cleanedNewTitle.equals(cleanedOriginalTitle);
            String newTimestampSuffix = "";
            if (needHandleTimestamp && originalFileName.endsWith(".txt")) {
                String[] timestampStruct = parseTimestampStructure(originalFileNameWithoutExt);
                String newRandomStr = "";
                ArrayList<String> newTimestamps = new ArrayList<>();
                String newMillisTimestamp = generateMillisTimestamp();
                boolean hasNoSuffix = !FULL_TIMESTAMP_PATTERN.matcher(originalFileNameWithoutExt).find();
                if (hasNoSuffix) {
                    newRandomStr = generateRandomString();
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
                String finalCoreName = getNonConflictCoreNameInFolder(actualDirectory, cleanedNewTitle);
                newFileName = finalCoreName + newTimestampSuffix + ".txt";
            } else {
                newFileName = cleanedNewTitle + newTimestampSuffix + ".txt";
            }
            File newFile = new File(actualDirectory, newFileName);
            boolean fileOperationSuccess = true;
            if (!targetFile.getAbsolutePath().equals(newFile.getAbsolutePath())) {
                if (!targetFile.renameTo(newFile)) {
                    try {
                        atomicSave(newFile, originalContent);
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
                String finalContent = content;
                try {
                    atomicSave(targetFile, finalContent);
                    isSaved = true;
                    Toast.makeText(this, "文件更新成功", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_REFRESH);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
            sp.edit().putBoolean("is_saving_" + targetFile.getName(), false).apply();
        }
        if (!isPreEdit && targetFile != null && targetFile.exists()) {
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }
        hideSoftInput();
        finish();
    }
    /**
     * 原子复制文件到目标路径
     */
    private void atomicCopyFileSync(File sourceFile, File targetFile) throws IOException {
        FileInputStream fis = new FileInputStream(sourceFile);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        fis.close();
        atomicSaveSync(targetFile, new String(bos.toByteArray(), StandardCharsets.UTF_8));
    }
    /**
     * 页面恢复时恢复文件编辑状态
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (!TextUtils.isEmpty(savedNewFilePath) && new File(savedNewFilePath).exists()) {
            isPreEdit = false;
            targetFile = new File(savedNewFilePath);
            loadExistingFileData(getIntent().getBooleanExtra("need_handle_timestamp", true));

            etContent.postDelayed(() -> {
                String realContent = etContent.getText().toString();
                int finalPos = Math.min(cursorPosition, realContent.length());
                etContent.setSelection(finalPos);
                Log.d("FileEditor", "新建文件切回：光标定位到字符数" + finalPos);
            }, 100);

            savedNewFilePath = "";
        }
    }
    /**
     * 原子性安全保存文件（防丢失/损坏）
     */
    private boolean atomicSave(File targetFile, String content) throws IOException {
        if (targetFile == null || targetFile.getParentFile() == null) return false;

        if (!targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

        String tempFileName = targetFile.getName() + "_atomic_tmp_" + System.currentTimeMillis();
        File tempFile = new File(targetFile.getParentFile(), tempFileName);

        FileOutputStream fos = null;
        FileLock lock = null;
        try {
            fos = new FileOutputStream(tempFile);
            lock = fos.getChannel().lock();

            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            fos.write(contentBytes);
            fos.flush();
            fos.getFD().sync();

            if (tempFile.length() != contentBytes.length) {
                throw new IOException("临时文件写入不全，长度不匹配");
            }

            File backupFile = new File(targetFile.getParentFile(), targetFile.getName() + "_backup");
            boolean replaceSuccess = true;
            if (targetFile.exists()) {
                replaceSuccess = targetFile.renameTo(backupFile);
                if (!replaceSuccess) {
                    throw new IOException("原文件备份失败");
                }
            }

            replaceSuccess = tempFile.renameTo(targetFile);
            if (replaceSuccess) {
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                return true;
            } else {
                if (backupFile.exists()) {
                    backupFile.renameTo(targetFile);
                }
                return false;
            }
        } finally {
            if (lock != null) lock.release();
            if (fos != null) fos.close();
            if (tempFile.exists() && !tempFile.equals(targetFile)) {
                tempFile.delete();
            }
        }
    }
    /**
     同步方式原子保存文件
     */
    private boolean atomicSaveSync(File targetFile, String content) throws IOException {
        return atomicSave(targetFile, content);
    }
    /**
     * 清理文件名非法字符
     */
    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "新建文件";
        }
        String cleaned = fileName.trim();
        cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|]", "");
        cleaned = cleaned.replaceAll("[\\n\\r\\t]", "");
        cleaned = cleaned.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        if (cleaned.isEmpty()) {
            return "新建文件";
        }
        int maxLength = 120;
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned;
    }
    /**
     * 生成六位随机字符
     */
    public  String generateRandomString() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(6);
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    /**
     * 生成17位毫秒时间戳
     */
    public static String generateMillisTimestamp() {
        return new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(new Date());
    }
    /**
     * 解析文件名中的时间戳结构，提取随机字符串和所有时间戳并返回数组
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
     * 获取文件夹内无冲突的核心名称
     */
    private String getNonConflictCoreNameInFolder(File targetFolder, String baseCore) {
        if (targetFolder == null || !targetFolder.exists() || baseCore == null) {
            return baseCore;
        }
        if (!isCoreNameExistsInFolder(targetFolder, baseCore)) {
            return baseCore;
        }
        int index = 1;
        while (true) {
            String testName = baseCore + "(" + index + ")";
            if (!isCoreNameExistsInFolder(targetFolder, testName)) {
                return testName;
            }
            index++;
        }
    }
    /**
     * 判断核心名称是否已存在
     */
    private boolean isCoreNameExistsInFolder(File folder, String coreName) {
        if (folder == null || !folder.exists() || coreName == null) return false;
        File[] files = folder.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isFile()) {
                String existCore = extractPureCoreNameForDisplay(f.getName());
                if (coreName.equals(existCore)) {
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * 提取纯核心名称（去除后缀和时间戳）
     */
    private String extractPureCoreNameForDisplay(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            fileName = fileName.substring(0, dot);
        }
        return removeAllTimestampFormats(fileName);
    }

    /**
     * 读取文件文本内容
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
     * 获取内容副标题
     */
    private String getContentSubtitle(String content) {
        if (TextUtils.isEmpty(content.trim())) return "无内容文件";
        String trimmedContent = content.trim();
        return trimmedContent.length() <= MAX_TITLE_LEN
                ? trimmedContent
                : trimmedContent.substring(0, MAX_TITLE_LEN) + "…";
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
    /**
     * 保存修改前的历史版本到「0.修改历史版本」，重名自动加序号
     */
    private void saveFileHistoryVersion(File originalFile) {
        try {
            File recycleBin = new File(getFilesDir(), "回收站");
            historyVersionDir = new File(recycleBin, "0.修改历史版本");
            if (!historyVersionDir.exists()) {
                historyVersionDir.mkdirs();
            }

            String originalFileName = originalFile.getName();
            String baseName = originalFileName.replace(".txt", "");

            // 匹配两种格式：核心_随机_时间戳 / 核心_随机_时间戳_时间戳
            String regex = "^(.+?)(_[^_]{6}_\\d+(?:_\\d+)?)$";
            String coreName = baseName;
            String suffixPart = "";

            if (baseName.matches(regex)) {
                coreName = baseName.replaceAll(regex, "$1");
                suffixPart = baseName.replaceAll(regex, "$2");
            }

            // 去掉核心名里可能带的序号 (1)(2)(3)，保证纯净
            coreName = coreName.replaceAll("\\(\\d+\\)$", "");

            // 文件夹：存在就不创建
            File coreFolder = new File(historyVersionDir, coreName);
            if (!coreFolder.exists()) {
                coreFolder.mkdirs();
            }

            // ==========================================
            // 正确查重：只统计【纯净核心名】数量
            // ==========================================
            int count = 0;
            File[] files = coreFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".txt")) {
                        String fname = f.getName().replace(".txt", "");

                        // 提取这个文件的纯净核心名
                        String fileCore = fname;
                        if (fname.matches(regex)) {
                            fileCore = fname.replaceAll(regex, "$1");
                        }
                        // 去掉序号
                        fileCore = fileCore.replaceAll("\\(\\d+\\)$", "");

                        // 只要和当前核心一样 → 计数+1
                        if (coreName.equals(fileCore)) {
                            count++;
                        }
                    }
                }
            }

            // 生成正确序号
            String finalFileName;
            if (count == 0) {
                finalFileName = coreName + suffixPart + ".txt";
            } else {
                finalFileName = coreName + "(" + count + ")" + suffixPart + ".txt";
            }

            // 保存
            String oldContent = readFileContent(originalFile);
            File historyFile = new File(coreFolder, finalFileName);
            atomicSaveSync(historyFile, oldContent);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}