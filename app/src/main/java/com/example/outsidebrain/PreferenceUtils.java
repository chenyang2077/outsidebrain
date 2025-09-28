package com.example.outsidebrain;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceUtils {
    private static final String PREF_NAME = "OutsideBrainPrefs";
    private static final String KEY_LAST_FOLDER_PATH = "last_folder_path";
    private static final String KEY_LAST_EDITED_FILE = "last_edited_file";

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // 保存最后访问的文件夹路径
    public static void saveLastFolderPath(Context context, String path) {
        getPreferences(context).edit().putString(KEY_LAST_FOLDER_PATH, path).apply();
    }

    // 获取最后访问的文件夹路径
    public static String getLastFolderPath(Context context) {
        return getPreferences(context).getString(KEY_LAST_FOLDER_PATH, null);
    }

    // 保存最后编辑的文件路径
    public static void saveLastEditedFile(Context context, String path) {
        getPreferences(context).edit().putString(KEY_LAST_EDITED_FILE, path).apply();
    }

    // 获取最后编辑的文件路径
    public static String getLastEditedFile(Context context) {
        return getPreferences(context).getString(KEY_LAST_EDITED_FILE, null);
    }

    // 清除最后编辑的文件记录
    public static void clearLastEditedFile(Context context) {
        getPreferences(context).edit().remove(KEY_LAST_EDITED_FILE).apply();
    }
}
