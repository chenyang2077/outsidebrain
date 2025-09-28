package com.example.outsidebrain;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceUtils {
    private static final String PREF_NAME = "OutsideBrainPrefs";
    private static final String KEY_LAST_FOLDER_PATH = "last_folder_path";
    private static final String KEY_LAST_EDITED_FILE = "last_edited_file";
    private static final String KEY_LAST_VIEWED_IMAGE = "last_viewed_image"; // 图片相关键名
    private static final String KEY_LAST_PAGE_TYPE = "last_page_type"; // main, editor, image

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // 文件夹相关方法
    public static void saveLastFolderPath(Context context, String path) {
        getPreferences(context).edit().putString(KEY_LAST_FOLDER_PATH, path).apply();
    }

    public static String getLastFolderPath(Context context) {
        return getPreferences(context).getString(KEY_LAST_FOLDER_PATH, null);
    }

    // 编辑文件相关方法
    public static void saveLastEditedFile(Context context, String path) {
        getPreferences(context).edit().putString(KEY_LAST_EDITED_FILE, path).apply();
    }

    public static String getLastEditedFile(Context context) {
        return getPreferences(context).getString(KEY_LAST_EDITED_FILE, null);
    }

    public static void clearLastEditedFile(Context context) {
        getPreferences(context).edit().remove(KEY_LAST_EDITED_FILE).apply();
    }

    // 图片查看相关方法 - 统一使用viewed命名
    public static void saveLastViewedImage(Context context, String path) {
        getPreferences(context).edit().putString(KEY_LAST_VIEWED_IMAGE, path).apply();
    }

    public static String getLastViewedImage(Context context) {
        return getPreferences(context).getString(KEY_LAST_VIEWED_IMAGE, null);
    }

    public static void clearLastViewedImage(Context context) {
        getPreferences(context).edit().remove(KEY_LAST_VIEWED_IMAGE).apply();
    }

    // 页面类型相关方法
    public static void saveLastPageType(Context context, String type) {
        getPreferences(context).edit().putString(KEY_LAST_PAGE_TYPE, type).apply();
    }

    public static String getLastPageType(Context context) {
        return getPreferences(context).getString(KEY_LAST_PAGE_TYPE, "main");
    }
}
