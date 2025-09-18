package com.example.outsidebrain;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class FileEditorActivity extends AppCompatActivity {

    private EditText etContent;
    private File currentFile;
    private boolean isSaved = false; // 标记是否已保存，避免重复提示

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor); // 关联布局文件

        // 初始化控件 (现在可以找到et_content了)
        etContent = findViewById(R.id.et_content);

        // 获取从MainActivity传递的文件路径
        String filePath = getIntent().getStringExtra("file_path");
        if (filePath != null) {
            currentFile = new File(filePath);
            loadFileContent(); // 加载文件内容
        }

        // 监听文本变化，重置保存状态
        etContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isSaved = false; // 内容修改时，重置保存标记
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // 加载文件内容
    private void loadFileContent() {
        if (currentFile == null || !currentFile.exists()) return;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(currentFile), "UTF-8"))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
            etContent.setText(content.toString().trim()); // 去除末尾空行
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "加载文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 保存文件（统一入口，避免重复调用）
    private void saveFile() {
        if (currentFile == null || isSaved) return; // 已保存则跳过

        String content = etContent.getText().toString().trim();
        try (FileOutputStream fos = new FileOutputStream(currentFile)) {
            fos.write(content.getBytes("UTF-8"));
            isSaved = true; // 标记为已保存
            Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 修复：重写返回键方法时调用super.onBackPressed()
    @Override
    public void onBackPressed() {
        saveFile(); // 保存文件
        super.onBackPressed(); // 调用父类方法，修复警告
    }

    // 后台清理时自动保存
    @Override
    protected void onPause() {
        super.onPause();
        // 当应用进入后台（如被清理），自动保存文件
        if (!isFinishing() && !isSaved) { // 未销毁且未保存时才保存
            saveFile();
        }
    }

    // 手动保存按钮点击事件
    public void onSaveClick(View view) {
        saveFile();
    }
}
