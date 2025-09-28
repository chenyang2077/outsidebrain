package com.example.outsidebrain;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UniqueFileNameHandler {
    private static final String TAG = "UniqueFileNameHandler";
    // 匹配带序列号的文件名模式，如"文件(1)"
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(.*?)\\((\\d+)\\)$");

    /**
     * 检查并获取全局唯一的文件名（在整个"外置大脑"目录下）
     * @param rootDir 外置大脑根目录
     * @param targetDir 目标保存目录
     * @param baseName 基础文件名（不含时间戳和扩展名）
     * @param timestamp 要添加的时间戳
     * @return 唯一的文件名
     */
    public static String getGlobalUniqueFileName(File rootDir, File targetDir, String baseName, String timestamp) {
        // 解析基础名称，提取核心名称和现有序列号（如“笔记（2）”→核心名“笔记”，序列号2）
        NameParts nameParts = parseNameParts(baseName);
        String coreName = nameParts.coreName;
        int existingSuffix = nameParts.suffix;

        // 收集所有目录中与核心名称相同的TXT文件（全局查重）
        List<File> conflictFiles = findConflictingFiles(rootDir, coreName);

        // 如果没有冲突，直接使用基础名称+时间戳构建文件名
        if (conflictFiles.isEmpty()) {
            return buildFileName(coreName, existingSuffix, timestamp);
        }

        // 找到已存在文件中的最大序列号（用于生成新序列号）
        int maxSuffix = existingSuffix;
        for (File file : conflictFiles) {
            // 移除文件名中的“随机字符串+时间戳”，提取纯名称
            String fileName = removeTimestamp(file.getName());
            fileName = fileName.replace(".txt", "").trim();

            // 解析该文件的核心名称和序列号
            NameParts parts = parseNameParts(fileName);
            // 仅更新相同核心名称的最大序列号
            if (parts.coreName.equals(coreName) && parts.suffix > maxSuffix) {
                maxSuffix = parts.suffix;
            }
        }

        // 使用“最大序列号+1”作为新序列号，确保文件名唯一
        return buildFileName(coreName, maxSuffix + 1, timestamp);
    }


    /**
     * 解析文件名，提取核心名称和序列号
     */
    private static NameParts parseNameParts(String fileName) {
        Matcher matcher = SUFFIX_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            String core = matcher.group(1).trim();
            try {
                int suffix = Integer.parseInt(matcher.group(2));
                return new NameParts(core, suffix);
            } catch (NumberFormatException e) {
                // 序列号不是数字，视为无序列号
                return new NameParts(fileName, 0);
            }
        }
        return new NameParts(fileName, 0);
    }

    /**
     * 构建文件名
     */
    private static String buildFileName(String coreName, int suffix, String timestamp) {
        if (suffix <= 0) {
            return coreName + timestamp + ".txt";
        } else {
            return coreName + "(" + suffix + ")" + timestamp + ".txt";
        }
    }

    /**
     * 从文件名中移除时间戳
     */
    // 1. 修改removeTimestamp方法，使用毫秒级时间戳模式
    public static String removeTimestamp(String fileName) {
        // 匹配格式：_随机字符串_毫秒时间戳（6位字母数字 + 17位时间戳）
        Matcher matcher = MainActivity.FILE_MILLIS_TIMESTAMP_PATTERN.matcher(fileName);
        return matcher.replaceAll("");
    }


    /**
     * 在整个根目录下查找与核心名称冲突的文件
     */
    private static List<File> findConflictingFiles(File rootDir, String coreName) {
        List<File> result = new ArrayList<>();
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return result;
        }

        // 递归搜索所有子目录
        searchFiles(rootDir, coreName, result);
        return result;
    }

    /**
     * 递归搜索文件
     */
    private static void searchFiles(File dir, String coreName, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                searchFiles(file, coreName, result);
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                String fileName = removeTimestamp(file.getName());
                fileName = fileName.replace(".txt", "");

                // 检查是否与核心名称相同（忽略可能的序列号）
                NameParts parts = parseNameParts(fileName);
                if (parts.coreName.equals(coreName)) {
                    result.add(file);
                }
            }
        }
    }

    /**
     * 内部类：存储文件名解析结果
     */
    private static class NameParts {
        String coreName;
        int suffix;

        NameParts(String coreName, int suffix) {
            this.coreName = coreName;
            this.suffix = suffix;
        }
    }
}
