package com.example.outsidebrain;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
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
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    private int cursorPosition = 0;
    private Uri uriFromExternal = null;
    private String originalFileNameForEdit;
    private String savedNewFilePath = "";

    // 撤销 / 重做
    private java.util.Stack<String> undoStack = new java.util.Stack<>();
    private java.util.Stack<String> redoStack = new java.util.Stack<>();
    private String lastContent = "";
    private static final int UNDO_DELAY = 500;
    private android.os.Handler undoHandler = new android.os.Handler();
    private Runnable undoRunnable;
    private String fileUniqueId = "";
    private SharedPreferences undoSP;

    // 键盘控制按钮
    private LinearLayout btnContainer;
    private View rootView;
    private boolean isKeyboardShown = false;

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
        }

        if (etContent != null && TextUtils.isEmpty(etContent.getText().toString())) {
            etContent.post(() -> showKeyboard(etContent));
        }

        setupTextChangeListeners();

        // 撤销初始化
        undoSP = getSharedPreferences("file_undo_cache", Context.MODE_PRIVATE);
        initUndoWithUniqueId();
        setupUndoTextWatcher();

        // 绑定按钮
        findViewById(R.id.btn_undo).setOnClickListener(v -> doUndo());
        findViewById(R.id.btn_redo).setOnClickListener(v -> doRedo());

        // 键盘监听
        rootView = getWindow().getDecorView().findViewById(android.R.id.content);
        setupKeyboardListenerForBtn();
    }

    // ==============================
    // 键盘弹出显示按钮
    // ==============================
    private void setupKeyboardListenerForBtn() {
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect r = new android.graphics.Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int heightDiff = screenHeight - (r.bottom - r.top);
            boolean isOpen = heightDiff > 200;

            if (isOpen != isKeyboardShown) {
                isKeyboardShown = isOpen;
                btnContainer.setVisibility(isOpen ? View.VISIBLE : View.GONE);
            }
        });
    }

    // ==============================
    // 撤销 / 重做 核心
    // ==============================
    private void initUndoWithUniqueId() {
        String fileName = targetFile != null ? targetFile.getName() : "";
        if (TextUtils.isEmpty(fileName)) {
            fileUniqueId = "temp_" + System.currentTimeMillis();
            undoStack.clear();
            redoStack.clear();
            lastContent = etContent.getText().toString();
            return;
        }

        String fileNameWithoutExt = fileName.endsWith(".txt")
                ? fileName.substring(0, fileName.lastIndexOf("."))
                : fileName;
        String[] struct = parseTimestampStructure(fileNameWithoutExt);

        if (struct != null && struct.length >= 2) {
            fileUniqueId = struct[0] + "_" + struct[1];
        } else {
            fileUniqueId = "temp_" + System.currentTimeMillis();
        }

        long saveTime = undoSP.getLong(fileUniqueId + "_save_time", 0);
        long now = System.currentTimeMillis();
        long twoDays = 2L * 24 * 60 * 60 * 1000;

        if (saveTime == 0 || now - saveTime > twoDays) {
            undoSP.edit().remove(fileUniqueId + "_history").apply();
            undoSP.edit().remove(fileUniqueId + "_save_time").apply();
        }

        loadUndoHistory();

    }

    private void loadUndoHistory() {
        undoStack.clear();
        redoStack.clear();
        lastContent = etContent.getText().toString();

        String history = undoSP.getString(fileUniqueId + "_history", "");
        if (!TextUtils.isEmpty(history)) {
            String[] array = history.split("\n=====\n");
            for (String item : array) {
                if (!TextUtils.isEmpty(item)) {
                    undoStack.push(item);
                }
            }
        }
    }

    private void saveUndoHistory() {
        if (TextUtils.isEmpty(fileUniqueId)) return;
        StringBuilder sb = new StringBuilder();
        for (String s : undoStack) {
            sb.append(s).append("\n=====\n");
        }
        undoSP.edit().putString(fileUniqueId + "_history", sb.toString()).apply();
        undoSP.edit().putLong(fileUniqueId + "_save_time", System.currentTimeMillis()).apply();
    }

    private void doUndo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "无可用撤销", Toast.LENGTH_SHORT).show();
            return;
        }
        redoStack.push(lastContent);
        String prev = undoStack.pop();
        etContent.setText(prev);
        etContent.setSelection(prev.length());
        lastContent = prev;
        isSaved = false;
        saveUndoHistory();
    }

    private void doRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "无可用重做", Toast.LENGTH_SHORT).show();
            return;
        }
        undoStack.push(lastContent);
        String next = redoStack.pop();
        etContent.setText(next);
        etContent.setSelection(next.length());
        lastContent = next;
        isSaved = false;
        saveUndoHistory();
    }

    private void setupUndoTextWatcher() {
        etContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isSaved = false;
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {
                String now = s.toString();
                if (now.equals(lastContent)) return;

                if (undoRunnable != null) undoHandler.removeCallbacks(undoRunnable);
                undoRunnable = () -> {
                    if (!lastContent.isEmpty()) {
                        undoStack.push(lastContent);
                        redoStack.clear();
                        saveUndoHistory();
                    }
                    lastContent = now;
                };
                undoHandler.postDelayed(undoRunnable, UNDO_DELAY);
            }
        });
    }

    // ==============================
    // 保存功能（完全正常）
    // ==============================
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
            boolean isContentEmpty = TextUtils.isEmpty(content);
            if (isTitleEmpty && isContentEmpty) {
                Toast.makeText(this, "未输入内容，放弃创建", Toast.LENGTH_SHORT).show();
                sp.edit().putBoolean("is_saving_" + fileName, false).apply();
                finish();
                return;
            }
            String rawTitle = isTitleEmpty ? getContentSubtitle(content) : inputTitle;
            String titleWithoutEdgeSpace = rawTitle.trim();
            String cleanedTitle = cleanFileName(titleWithoutEdgeSpace);
            String timestampSuffix = "";
            if (needHandleTimestamp) {
                String randomStr = UniqueFileNameHandler.generateRandomString();
                String millisTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();
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
                boolean saveSuccess = atomicSave(targetFile, content);
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
                String tip = isTitleEmpty && isContentEmpty ? "文件名和内容均为空，放弃修改" : (isTitleEmpty ? "文件名称为空，放弃修改" : "文件内容为空，放弃修改");
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
            }
            if (fileOperationSuccess) {
                try {
                    atomicSave(targetFile, content);
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

    @Override
    public void onBackPressed() {
        autoSave();
        super.onBackPressed();
    }

    // ==============================
    // 工具方法
    // ==============================
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

        SharedPreferences sp = getSharedPreferences("save_state", MODE_PRIVATE);
        Set<String> keys = sp.getAll().keySet();
        for (String key : keys) {
            if (key.startsWith("is_saving_") && sp.getBoolean(key, false)) {
                String fileName = key.replace("is_saving_", "");
                Toast.makeText(this, "检测到「" + fileName + "」上次保存可能未完成，请检查文件内容", Toast.LENGTH_LONG).show();
                sp.edit().remove(key).apply();
            }
        }
    }

    private void showKeyboard(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

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

    private void loadExistingFileData(boolean needHandleTimestamp) {
        if (targetFile == null || !targetFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String fileName = targetFile.getName();
        String fileNameWithoutExt = fileName.endsWith(".txt") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
        String displayName = needHandleTimestamp ? removeAllTimestampFormats(fileNameWithoutExt) : fileNameWithoutExt;
        etFileName.setText(displayName);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            String finalContent = content.toString().endsWith("\n") ? content.toString().substring(0, content.length()-1) : content.toString();
            etContent.setText(finalContent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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
                    s.replace(0, s.length(), cleanedTitle.substring(0, MAX_ALLOWED_LENGTH));
                }
                isSaved = false;
            }
        });
    }

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

    private String extractPureCoreNameForDisplay(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            fileName = fileName.substring(0, dot);
        }
        return removeAllTimestampFormats(fileName);
    }

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

    private boolean atomicSaveSync(File targetFile, String content) throws IOException {
        return atomicSave(targetFile, content);
    }

    private String cleanFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "");
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

    private void hideSoftInput() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etContent.getWindowToken(), 0);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isSaved) {
            saveContentSync();
        }
    }

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

        SharedPreferences sp = getSharedPreferences("save_state", MODE_PRIVATE);
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
                        ? "_" + UniqueFileNameHandler.generateRandomString() + "_" + UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp()
                        : "";
                File targetDirectory = currentDir != null ? currentDir : rootDir;
                if (!targetDirectory.exists()) {
                    targetDirectory.mkdirs();
                }

                String finalCoreName = getNonConflictCoreNameInFolder(targetDirectory, cleanedTitle);
                String tempFileName = finalCoreName + timestampSuffix + ".txt";
                File uniqueFile = new File(targetDirectory, tempFileName);
                targetFile = uniqueFile;

                boolean createSuccess = atomicSaveSync(targetFile, content);
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
                    String tip = isTitleEmpty && isContentEmpty ? "文件名和内容均为空，放弃修改" : (isTitleEmpty ? "文件名称为空，放弃修改" : "文件内容为空，放弃修改");
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
                }

                atomicSaveSync(targetFile, content);
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
            PreferenceUtils.saveLastEditedFile(this, targetFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, targetFile.getParentFile().getAbsolutePath());
        }
    }

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
            }, 100);

            savedNewFilePath = "";
        }
    }
}