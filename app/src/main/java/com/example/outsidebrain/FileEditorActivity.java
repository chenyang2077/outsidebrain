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

    // 文件名时间戳（-yyyy-MM-dd）
    private static final SimpleDateFormat FILE_NAME_TIMESTAMP = new SimpleDateFormat("-yyyy-MM-dd", Locale.getDefault());
    // 正文时间戳（yyyy-MM-dd）
    private static final SimpleDateFormat CONTENT_TIMESTAMP = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    // 匹配最后一行时间戳（(yyyy-MM-dd)）
    private static final Pattern LAST_LINE_TIMESTAMP_PATTERN = Pattern.compile("\\(\\d{4}-\\d{2}-\\d{2}\\)$");
    // 匹配第一行路径（【路径】）
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【([^】]*)】$");

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
            loadExistingFileData();
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

        String fileName = targetFile.getName();
        if (fileName.endsWith(".txt")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        etFileName.setText(fileName);

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
        if (isSaved) return;

        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString().trim();
        String rootFolderName = getIntent().getStringExtra("root_folder_name");
        boolean isRootDirectory = getIntent().getBooleanExtra("is_root_directory", false);

        if (rootFolderName == null) rootFolderName = "外置大脑";

        String fileTimestamp = FILE_NAME_TIMESTAMP.format(new Date());

        if (isPreEdit) {
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

                    if (isRootDirectory) {
                        finalContent = addContentTimestamp(content);
                    } else {
                        finalContent = "【" + dirPath + "】\n" + addContentTimestamp(content);
                    }

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
        } else {
            if (targetFile == null) return;

            String inputTitleTrimmed = inputTitle.trim();
            String originalTitle = targetFile.getName().replace(".txt", "");

            if (!inputTitleTrimmed.isEmpty() && !inputTitleTrimmed.equals(originalTitle)) {
                String newTitle = removeOldTimestamp(inputTitleTrimmed) + fileTimestamp;
                File newFile = new File(targetFile.getParentFile(), newTitle + ".txt");

                int counter = 1;
                while (newFile.exists()) {
                    newTitle = removeOldTimestamp(inputTitleTrimmed) + fileTimestamp + "(" + counter + ")";
                    newFile = new File(targetFile.getParentFile(), newTitle + ".txt");
                    counter++;
                }

                if (targetFile.renameTo(newFile)) {
                    targetFile = newFile;
                    Toast.makeText(this, "文件重命名成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
            }

            String finalContent;
            if (isRootDirectory) {
                finalContent = addContentTimestamp(content);
            } else {
                // 仅替换第一行的路径标识，其他行的【】不处理
                String contentWithoutFirstPath = content.replaceFirst("^【.*?】\n?", "");
                String dirPath = MainActivity.getRelativeDirPath(targetFile.getParentFile(), rootFolderName);
                finalContent = "【" + dirPath + "】\n" + addContentTimestamp(contentWithoutFirstPath);
            }

            writeFileContent(targetFile, finalContent);
            isSaved = true;
            Toast.makeText(this, "文件更新成功", Toast.LENGTH_SHORT).show();
            setResult(RESULT_REFRESH);
            finish();
        }

        hideSoftInput();
    }

    // 仅最后一行添加时间戳，其他行时间戳不处理
    // 仅最后一行添加时间戳，且检查最后一行时间戳是否为当天
    private String addContentTimestamp(String originalContent) {
        if (TextUtils.isEmpty(originalContent)) {
            return "(" + CONTENT_TIMESTAMP.format(new Date()) + ")";
        }

        String[] lines = originalContent.split("\n");
        ArrayList<String> lineList = new ArrayList<>();
        for (String line : lines) {
            lineList.add(line);
        }

        String lastLine = lineList.isEmpty() ? "" : lineList.get(lineList.size() - 1);
        Matcher timestampMatcher = LAST_LINE_TIMESTAMP_PATTERN.matcher(lastLine);

        // 生成当天时间戳（yyyy-MM-dd）
        String todayTimestamp = CONTENT_TIMESTAMP.format(new Date());
        boolean hasTodayTimestamp = false;

        if (timestampMatcher.find()) {
            // 提取最后一行的时间戳（如：2025-09-20）
            String existingTimestamp = timestampMatcher.group().replace("(", "").replace(")", "");
            // 比较是否为当天时间戳
            hasTodayTimestamp = existingTimestamp.equals(todayTimestamp);
        }

        // 最后一行没有时间戳，或时间戳不是当天 → 追加当天时间戳
        if (!hasTodayTimestamp) {
            lineList.add("(" + todayTimestamp + ")");
        }

        return TextUtils.join("\n", lineList);
    }

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
        if (content.isEmpty()) return "无内容文件";
        return content.length() <= MAX_TITLE_LEN ? content : content.substring(0, MAX_TITLE_LEN) + "…";
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

