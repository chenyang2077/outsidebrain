package com.example.outsidebrain;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

public class FileEditorActivity extends AppCompatActivity {

    public static final int RESULT_REFRESH = 100; // 用于通知刷新的结果码
    private EditText etFileName;      // 标题编辑区（文件名）
    private EditText etContent;       // 内容编辑区
    private boolean isPreEdit;        // 是否为“预编辑”状态
    private File currentDir;         // 预编辑文件保存目录
    private File targetFile;          // 已有文件（非预编辑时使用）
    private boolean isSaved = true;   // 是否已保存
    private static final int MAX_TITLE_LEN = 15; // 内容截取最大长度

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

        // 3. 初始化状态
        if (isPreEdit) {
            // 预编辑：初始化保存目录，聚焦内容区
            currentDir = new File(currentDirPath);
            etFileName.setHint("自动生成标题（内容前15字）");
            focusAndShowSoftInput(etContent);
        } else {
            // 已有文件：加载标题和内容
            targetFile = new File(filePath);
            loadExistingFileData();
        }

        // 4. 监听文本变化，标记未保存
        setupTextChangeListeners();
    }

    // 加载已有文件的标题和内容
    private void loadExistingFileData() {
        if (targetFile == null || !targetFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 加载标题（去除.txt后缀和旧时间戳）
        String fileName = targetFile.getName();
        if (fileName.endsWith(".txt")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        etFileName.setText(removeOldTimestamp(fileName));

        // 加载文件内容
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

    // 监听文本变化
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

    // 自动保存逻辑
    private void autoSave() {
        if (isSaved) return;

        String inputTitle = etFileName.getText().toString().trim();
        String content = etContent.getText().toString().trim();

        // 预编辑状态
        if (isPreEdit) {
            // 标题+内容都空 → 放弃创建
            if (inputTitle.isEmpty() && content.isEmpty()) {
                Toast.makeText(this, "未输入内容，放弃创建", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            // 生成最终标题
            String finalTitle = inputTitle.isEmpty() ? getContentSubtitle(content) : inputTitle;
            finalTitle = removeOldTimestamp(finalTitle);

            // 添加时间戳（格式：-yy-MM-dd）
            String timestamp = new SimpleDateFormat("-yy-MM-dd", Locale.getDefault()).format(new Date());

            // 处理重名（在日期前添加"+"）
            String baseFileName = finalTitle + timestamp + ".txt";
            targetFile = getUniqueFile(currentDir, finalTitle, timestamp);

            // 创建文件并写入内容
            try {
                if (targetFile.createNewFile()) {
                    writeFileContent(targetFile, content);
                    isSaved = true;
                    Toast.makeText(this, "文件创建成功：" + targetFile.getName(), Toast.LENGTH_SHORT).show();
                    setResult(RESULT_REFRESH); // 设置结果码，通知需要刷新
                    finish();
                } else {
                    Toast.makeText(this, "创建文件失败", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "创建异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
        // 已有文件
        else {
            if (targetFile == null) return;

            // 标题修改：处理重命名
            String inputTitleTrimmed = inputTitle.trim();
            String originalTitle = targetFile.getName().replace(".txt", "");
            originalTitle = removeOldTimestamp(originalTitle);

            if (!inputTitleTrimmed.isEmpty() && !inputTitleTrimmed.equals(originalTitle)) {
                String timestamp = new SimpleDateFormat("-yy-MM-dd", Locale.getDefault()).format(new Date());
                File newFile = getUniqueFile(targetFile.getParentFile(), inputTitleTrimmed, timestamp);

                // 执行重命名
                if (targetFile.renameTo(newFile)) {
                    targetFile = newFile;
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
            }

            // 更新内容
            writeFileContent(targetFile, content);
            isSaved = true;
            Toast.makeText(this, "文件更新成功", Toast.LENGTH_SHORT).show();
            setResult(RESULT_REFRESH); // 设置结果码，通知需要刷新
            finish();
        }

        hideSoftInput();
    }

    // 核心修改：处理重名，在日期前添加"+"
    private File getUniqueFile(File parentDir, String baseTitle, String timestamp) {
        // 基础文件名：标题+时间戳
        String baseFileName = baseTitle + timestamp + ".txt";
        File file = new File(parentDir, baseFileName);
        int suffixCount = 0;

        // 如果文件已存在，在标题和时间戳之间添加"+"
        while (file.exists()) {
            suffixCount++;
            String suffix = "+".repeat(suffixCount);
            // 重名格式：标题+"+"+时间戳.txt
            String uniqueFileName = baseTitle + suffix + timestamp + ".txt";
            file = new File(parentDir, uniqueFileName);
        }
        return file;
    }

    // 从内容截取前15字生成标题
    private String getContentSubtitle(String content) {
        if (content.isEmpty()) return "无内容文件";
        return content.length() <= MAX_TITLE_LEN ? content : content.substring(0, MAX_TITLE_LEN) + "…";
    }

    // 移除旧时间戳
    private String removeOldTimestamp(String fileName) {
        // 正则表达式：匹配末尾的-数字-数字-数字格式
        return fileName.replaceAll("\\+*-[0-9]{2}-[0-9]{2}-[0-9]{2}$", "");
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
        super.onBackPressed(); // 添加调用父类方法
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
