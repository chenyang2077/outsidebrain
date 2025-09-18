package com.example.outsidebrain;

import android.os.Bundle;
import android.view.View;
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

public class FileEditorActivity extends AppCompatActivity {

    private EditText etContent;       // 内容编辑区
    private EditText etFileName;      // 文件名编辑区（原tv_file_name）
    private File currentFile;         // 当前操作的文件
    private boolean isSaved;          // 标记是否已保存（避免重复保存）
    private String originalFileName;  // 原始文件名（用于对比是否修改）

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        // 1. 初始化控件（绑定文件名编辑区和内容编辑区）
        etFileName = findViewById(R.id.et_file_name);
        etContent = findViewById(R.id.et_content);

        // 2. 获取从MainActivity传递的文件路径
        String filePath = getIntent().getStringExtra("file_path");
        if (filePath != null) {
            currentFile = new File(filePath);
            originalFileName = currentFile.getName();  // 保存原始文件名
            loadFileInfo();  // 加载文件名和文件内容
        }

        // 3. 监听文本变化（内容或文件名修改时，标记为未保存）
        setupTextChangeListeners();

        // 4. 初始状态：让文件名编辑区获取焦点（方便直接修改文件名）
        etFileName.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etFileName, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    // 加载文件名和文件内容
    private void loadFileInfo() {
        if (currentFile == null || !currentFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 加载文件名（去除.txt后缀，让编辑更直观）
        String fileName = currentFile.getName();
        if (fileName.endsWith(".txt")) {
            fileName = fileName.substring(0, fileName.lastIndexOf("."));
        }
        etFileName.setText(fileName);

        // 加载文件内容
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(currentFile), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            etContent.setText(content.toString().trim());  // 去除末尾空行
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载文件内容失败", Toast.LENGTH_SHORT).show();
        }

        isSaved = true;  // 初始加载完成后标记为“已保存”
    }

    // 监听文件名和内容的变化，标记未保存状态
    private void setupTextChangeListeners() {
        // 监听文件名变化
        etFileName.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isSaved = false;  // 文件名修改，标记为未保存
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // 监听内容变化（原有逻辑）
        etContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isSaved = false;  // 内容修改，标记为未保存
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // 核心：保存文件（同时处理文件名修改和内容保存）
    private void saveFile() {
        if (currentFile == null || isSaved) return;  // 已保存或文件为空，直接返回

        // 1. 处理文件名（确保合法性）
        String newFileName = etFileName.getText().toString().trim();
        if (newFileName.isEmpty()) {
            Toast.makeText(this, "文件名不能为空", Toast.LENGTH_SHORT).show();
            etFileName.requestFocus();  // 让焦点回到文件名编辑区
            return;
        }

        // 自动添加.txt后缀（避免用户忘记）
        if (!newFileName.endsWith(".txt")) {
            newFileName += ".txt";
        }

        // 2. 处理文件重命名（如果文件名有修改）
        File parentDir = currentFile.getParentFile();  // 获取当前文件的父目录
        File newFile = new File(parentDir, newFileName);  // 新文件名对应的文件对象

        // 检查新文件名是否已存在（避免覆盖其他文件）
        if (!newFileName.equals(originalFileName)) {
            if (newFile.exists()) {
                Toast.makeText(this, "文件名已存在，请修改", Toast.LENGTH_SHORT).show();
                etFileName.requestFocus();
                return;
            }

            // 执行重命名（将原文件改名为新文件名）
            if (!currentFile.renameTo(newFile)) {
                Toast.makeText(this, "修改文件名失败", Toast.LENGTH_SHORT).show();
                return;
            }

            // 更新当前文件引用和原始文件名（后续操作基于新文件）
            currentFile = newFile;
            originalFileName = newFileName;
        }

        // 3. 保存文件内容
        String content = etContent.getText().toString().trim();
        try (FileOutputStream fos = new FileOutputStream(currentFile)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));  // 用UTF-8编码保存，避免中文乱码
            isSaved = true;  // 标记为已保存
            Toast.makeText(this, "文件保存成功", Toast.LENGTH_SHORT).show();

            // 保存后隐藏输入法（提升体验）
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etContent.getWindowToken(), 0);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 手动保存按钮点击事件（布局中按钮需绑定此方法）
    public void onSaveClick(View view) {
        saveFile();
    }

    // 返回键逻辑：先保存再退出
    @Override
    public void onBackPressed() {
        saveFile();  // 退出前自动保存
        super.onBackPressed();
    }

    // 应用进入后台时自动保存（防止用户忘记手动保存）
    @Override
    protected void onPause() {
        super.onPause();
        if (!isFinishing() && !isSaved) {  // 未销毁且未保存时，自动保存
            saveFile();
        }
    }
}