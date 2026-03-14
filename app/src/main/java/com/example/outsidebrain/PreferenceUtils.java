package com.example.outsidebrain;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用偏好设置工具类文件PreferenceUtils.java
 * 用途：基于Android SharedPreferences实现应用本地轻量级数据持久化存储，
 * 专门管理用户操作的历史记录（最后访问的文件夹、编辑的文件、查看的图片、访问的页面类型），
 * 用于应用重启后恢复用户上次操作状态，提升使用体验。
 */
public class PreferenceUtils {
    private static final String PREF_NAME = "OutsideBrainPrefs";
    private static final String KEY_LAST_FOLDER_PATH = "last_folder_path";
    private static final String KEY_LAST_EDITED_FILE = "last_edited_file";
    private static final String KEY_LAST_VIEWED_IMAGE = "last_viewed_image";
    private static final String KEY_LAST_PAGE_TYPE = "last_page_type";

    /**
     * 获取应用共享偏好设置实例
     */
    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 保存最后访问的文件夹路径
     */
    public static void saveLastFolderPath(Context context, String path) {
        getPreferences(context).edit().putString(KEY_LAST_FOLDER_PATH, path).apply();
    }

    /**
     * 获取最后访问的文件夹路径
     * @return String 文件夹路径，无则返回null
     */
    public static String getLastFolderPath(Context context) {
        return getPreferences(context).getString(KEY_LAST_FOLDER_PATH, null);
    }

    /**
     * 保存最后编辑的文件路径
     */
    public static void saveLastEditedFile(Context context, String path) {
        getPreferences(context).edit().putString(KEY_LAST_EDITED_FILE, path).apply();
    }

    /**
     * 获取最后编辑的文件路径
     * @return String 文件路径，无则返回null
     */
    public static String getLastEditedFile(Context context) {
        return getPreferences(context).getString(KEY_LAST_EDITED_FILE, null);
    }

    /**
     * 清空最后编辑的文件路径记录
     */
    public static void clearLastEditedFile(Context context) {
        getPreferences(context).edit().remove(KEY_LAST_EDITED_FILE).apply();
    }

    /**
     * 保存最后查看的图片路径
     */
    public static void saveLastViewedImage(Context context, String path) {
        getPreferences(context).edit().putString(KEY_LAST_VIEWED_IMAGE, path).apply();
    }

    /**
     * 获取最后查看的图片路径
     * @return String 图片路径，无则返回null
     */
    public static String getLastViewedImage(Context context) {
        return getPreferences(context).getString(KEY_LAST_VIEWED_IMAGE, null);
    }

    /**
     * 清空最后查看的图片路径记录
     */
    public static void clearLastViewedImage(Context context) {
        getPreferences(context).edit().remove(KEY_LAST_VIEWED_IMAGE).apply();
    }

    /**
     * 保存最后访问的页面类型
     */
    public static void saveLastPageType(Context context, String type) {
        getPreferences(context).edit().putString(KEY_LAST_PAGE_TYPE, type).apply();
    }

    /**
     * 获取最后访问的页面类型
     * @return String 页面类型，默认返回"main"
     */
    public static String getLastPageType(Context context) {
        return getPreferences(context).getString(KEY_LAST_PAGE_TYPE, "main");
    }
}