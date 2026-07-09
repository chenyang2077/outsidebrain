package com.example.outsidebrainkxmt563980;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    private static final String SP_NAME = "privacy_sp";
    private static final String KEY_AGREE = "has_agree_privacy";
    private AlertDialog privacyDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        SharedPreferences sp = getSharedPreferences(SP_NAME, MODE_PRIVATE);
        boolean isAgree = sp.getBoolean(KEY_AGREE, false);

        if (!isAgree) {
            showPrivacyDialog(sp);
        } else {
            jumpMain();
        }
    }

    // 展示隐私弹窗
    private void showPrivacyDialog(SharedPreferences sp) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_privacy, null);
        TextView tvText = dialogView.findViewById(R.id.tv_privacy_content);
        Button btnAgree = dialogView.findViewById(R.id.btn_agree);
        Button btnDisagree = dialogView.findViewById(R.id.btn_disagree);

        String content = "开发者：陈阳\n" +
                "生效日期：2026年07月09日\n\n" +
                "1. 软件运行说明\n" +
                "本软件为纯单机本地工具，全程不会发起任何网络请求，不会上传、同步、外传您手机内的任何本地文档、个人信息，所有文件阅览、整理操作仅在您设备本地完成，无任何云端数据交互。\n\n" +
                "2. 应用权限说明\n" +
                "本软件仅申请读取存储卡照片、媒体内容和文件、修改或删除存储卡照片、媒体内容和文件两项存储权限，对应用途与访问范围如下：\n" +
                "1. 读取存储卡照片、媒体内容和文件权限：软件会在设备公共存储目录自动创建专属「中转站」文件夹，本权限仅用于读取该文件夹内用户手动存入的文档、素材，实现文件阅览、预览检索功能；\n" +
                "2. 修改或删除存储卡照片、媒体内容和文件权限：仅支持对「中转站」文件夹内文件执行重命名、移动、删除、导出保存等整理操作；\n" +
                "访问限制：软件对文件的读取、修改、删除操作仅限「中转站」专属文件夹，不会主动扫描、访问、修改设备内其他目录的私人文件；所有文件操作均需用户手动触发，无后台自动读写行为。\n" +
                "所有文件仅保存在您手机本地存储空间，开发者无任何渠道获取、查看、上传您的文档数据。\n\n" +
                "3. 账号相关说明\n" +
                "本软件无注册、登录账号体系，不存在用户个人账号，无账号注册、解绑、注销相关流程，不采集任何账号类信息。\n\n" +
                "4. 个人信息收集说明\n" +
                "本软件不会采集设备ID、通讯录、地理位置、相册原始数据、短信等无关个人隐私信息；无用户行为统计、无广告推送、无第三方SDK、无任何数据共享行为。\n\n" +
                "5. 用户数据安全说明\n" +
                "您的本地文档、文件资料仅永久保存在本机「中转站」文件夹内，仅用户本人有权访问；软件不会将任何本地内容传输至开发者、第三方机构或外部服务器。您可随时在手机系统设置中关闭存储权限，关闭后文件阅览、整理功能将无法使用。\n\n" +
                "6. 用户权限自主管理说明\n" +
                "您可随时进入手机系统设置，开启或关闭本应用存储权限；若关闭存储权限，软件无法读取、编辑「中转站」内文件，核心文件整理功能将全部失效。\n\n" +
                "7. 政策效力说明\n" +
                "您打开应用弹窗点击「同意」按钮，即代表已完整阅读、充分理解并自愿认可本隐私政策全部条款；若您选择「不同意」，将无法使用本软件所有功能，应用会直接退出。\n\n" +
                "8. 联系方式\n" +
                "如有隐私相关疑问、意见或反馈，可发送邮件联系开发者：2740745045@qq.com";
        tvText.setText(content);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        builder.setCancelable(false);
        privacyDialog = builder.create();

        // 不同意：退出应用
        btnDisagree.setOnClickListener(v -> {
            privacyDialog.dismiss();
            finishAffinity();
            System.exit(0);
        });

        // 同意：存储标记，跳转主界面
        btnAgree.setOnClickListener(v -> {
            sp.edit().putBoolean(KEY_AGREE, true).apply();
            privacyDialog.dismiss();
            jumpMain();
        });

        privacyDialog.show();
    }

    // 跳转主页面
    private void jumpMain() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}