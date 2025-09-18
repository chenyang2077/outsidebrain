package com.example.outsidebrain;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class FileEditorActivity extends AppCompatActivity {

    private EditText etFileContent;  // 文本编辑框
    private File currentFile;        // 当前编辑的文件
    private String originalContent; // 存储文件打开时的原始内容

    // 新增：标记是否已保存，避免重复保存（可选，增强安全性）
    private boolean hasSaved = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_editor);

        // 初始化控件
        etFileContent = findViewById(R.id.et_file_content);
        TextView tvFileName = findViewById(R.id.tv_file_name);

        // 获取从MainActivity传递的文件路径
        Intent intent = getIntent();
        String filePath = intent.getStringExtra("file_path");
        if (filePath != null) {
            currentFile = new File(filePath);
            // 显示文件名（去掉.txt后缀）
            readFileContent(currentFile);
            // 新增：保存原始内容，用于判断是否修改
            originalContent = etFileContent.getText().toString();
            String fileName = currentFile.getName();
            if (fileName.endsWith(".txt")) {
                fileName = fileName.substring(0, fileName.lastIndexOf("."));
            }
            tvFileName.setText(fileName);
            // 读取文件内容到编辑框
            readFileContent(currentFile);
        } else {
            Toast.makeText(this, "文件路径无效", Toast.LENGTH_SHORT).show();
            finish(); // 异常情况直接关闭页面
        }
    }

    // 读取文件内容
    private void readFileContent(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
            BufferedReader br = new BufferedReader(isr);

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n"); // 保留换行符
            }

            // 显示内容（去掉最后一个多余的换行符）
            if (content.length() > 0) {
                content.deleteCharAt(content.length() - 1);
            }
            etFileContent.setText(content.toString());

            // 关闭流
            br.close();
            isr.close();
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "读取文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 保存文件内容
    private void saveFileContent() {
        if (currentFile == null || hasSaved) return; // 避免重复保存

        try {
            FileOutputStream fos = new FileOutputStream(currentFile);
            // 获取编辑框内容，转成字节数组（UTF-8编码避免乱码）
            String content = etFileContent.getText().toString();
            fos.write(content.getBytes("UTF-8"));
            fos.flush();
            fos.close();

            Toast.makeText(this, "文件已保存", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "保存文件失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 监听「返回键」：返回前自动保存
    @Override
    public void onBackPressed() {
        saveFileContent(); // 返回前保存
        super.onBackPressed(); // 继续执行返回操作
    }

    // 监听「页面销毁」：退出应用时自动保存
    @Override
    protected void onDestroy() {
        String currentContent = etFileContent.getText().toString();
        // 判断：内容有变化 + 未保存过
        if (!currentContent.equals(originalContent) && !hasSaved) {
            saveFileContent();
        }
        super.onDestroy();
    }
}