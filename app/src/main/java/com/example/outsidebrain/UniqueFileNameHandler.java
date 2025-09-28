package com.example.outsidebrain;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UniqueFileNameHandler {
    private static final String TAG = "UniqueFileNameHandler";
    // 匹配带序列号的文件名模式，如"文件(1)"
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(.*?)\\((\\d+)\\)$");

    // 两种时间戳格式的正则（私有，仅内部使用）
    private static final Pattern SINGLE_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private static final Pattern MULTI_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})+");

    // -------------------------- 公开方法：生成6位随机字符串 --------------------------
    /**
     * 生成6位随机字符串（字母+数字）
     */
    public static String generateRandomString() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(6);
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // -------------------------- 公开方法：提取随机字符串 --------------------------
    /**
     * 从文件名中提取6位随机字符串（支持基础/增量格式）
     * @param fileName 不含扩展名的原始文件名（如"笔记_abc123_20250928153022123"）
     * @return 提取的随机字符串，提取失败则生成新的
     */
    public static String extractRandomString(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return generateRandomString(); // 空文件名时生成新随机串
        }

        // 1. 先匹配基础格式（_abc123_20250928153022123）
        Matcher singleMatcher = SINGLE_TIMESTAMP_PATTERN.matcher(fileName);
        if (singleMatcher.find()) {
            String[] parts = singleMatcher.group().split("_"); // 分割为 ["", "abc123", "20250928153022123"]
            if (parts.length >= 2) {
                return parts[1]; // 返回第2个元素（6位随机串）
            }
        }

        // 2. 再匹配增量格式（_abc123_20250928153022123_20250928164033444）
        Matcher multiMatcher = MULTI_TIMESTAMP_PATTERN.matcher(fileName);
        if (multiMatcher.find()) {
            String[] parts = multiMatcher.group().split("_"); // 分割为 ["", "abc123", "20250928153022123", "20250928164033444"]
            if (parts.length >= 2) {
                return parts[1]; // 返回第2个元素（6位随机串）
            }
        }

        // 3. 两种格式都匹配失败，生成新的随机串
        return generateRandomString();
    }

    // -------------------------- 其他已有方法保持不变 --------------------------
    public static String getGlobalUniqueFileName(File rootDir, File targetDir, String baseName, String timestamp) {
        String cleanedBaseName = cleanTitle(baseName);
        NameParts nameParts = parseNameParts(cleanedBaseName);
        String coreName = nameParts.coreName;
        int existingSuffix = nameParts.suffix;
        List<File> conflictFiles = findConflictingFiles(rootDir, coreName);
        if (conflictFiles.isEmpty()) {
            return buildFileName(coreName, existingSuffix, timestamp);
        }
        int maxSuffix = existingSuffix;
        for (File file : conflictFiles) {
            String fileName = cleanTitle(file.getName());
            fileName = fileName.replace(".txt", "").trim();
            NameParts parts = parseNameParts(fileName);
            if (parts.coreName.equals(coreName) && parts.suffix > maxSuffix) {
                maxSuffix = parts.suffix;
            }
        }
        return buildFileName(coreName, maxSuffix + 1, timestamp);
    }

    public static String cleanTitle(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        String cleaned = MULTI_TIMESTAMP_PATTERN.matcher(input).replaceAll("");
        cleaned = SINGLE_TIMESTAMP_PATTERN.matcher(cleaned).replaceAll("");
        if (MainActivity.OLD_TIMESTAMP_PATTERN != null) {
            cleaned = MainActivity.OLD_TIMESTAMP_PATTERN.matcher(cleaned).replaceAll("");
        }
        return cleaned.trim();
    }

    private static NameParts parseNameParts(String fileName) {
        Matcher matcher = SUFFIX_PATTERN.matcher(fileName);
        if (matcher.matches()) {
            String core = matcher.group(1).trim();
            try {
                int suffix = Integer.parseInt(matcher.group(2));
                return new NameParts(core, suffix);
            } catch (NumberFormatException e) {
                return new NameParts(fileName, 0);
            }
        }
        return new NameParts(fileName, 0);
    }

    private static String buildFileName(String coreName, int suffix, String timestamp) {
        if (suffix <= 0) {
            return coreName + timestamp + ".txt";
        } else {
            return coreName + "(" + suffix + ")" + timestamp + ".txt";
        }
    }

    public static String removeTimestamp(String fileName) {
        String cleaned = cleanTitle(fileName);
        if (MainActivity.FILE_MILLIS_TIMESTAMP_PATTERN != null) {
            cleaned = MainActivity.FILE_MILLIS_TIMESTAMP_PATTERN.matcher(cleaned).replaceAll("");
        }
        return cleaned;
    }

    private static List<File> findConflictingFiles(File rootDir, String coreName) {
        List<File> result = new ArrayList<>();
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return result;
        }
        searchFiles(rootDir, coreName, result);
        return result;
    }

    private static void searchFiles(File dir, String coreName, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                searchFiles(file, coreName, result);
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                String fileName = cleanTitle(file.getName());
                fileName = fileName.replace(".txt", "");
                NameParts parts = parseNameParts(fileName);
                if (parts.coreName.equals(coreName)) {
                    result.add(file);
                }
            }
        }
    }

    private static class NameParts {
        String coreName;
        int suffix;
        NameParts(String coreName, int suffix) {
            this.coreName = coreName;
            this.suffix = suffix;
        }
    }

    // 时间戳处理内部类
    public static class TimestampHandler {
        public static String generateRandomString() {
            return UniqueFileNameHandler.generateRandomString(); // 复用外部方法
        }

        public static String generateMillisTimestamp() {
            return new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(new Date());
        }

        public static String processTxtFileName(String originalFileName) {
            if (!originalFileName.toLowerCase().endsWith(".txt")) {
                return originalFileName;
            }
            String fileNameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf("."));
            String ext = originalFileName.substring(originalFileName.lastIndexOf("."));

            Matcher targetMatcher = MainActivity.TARGET_TIMESTAMP_PATTERN.matcher(fileNameWithoutExt);
            Matcher incrementMatcher = MainActivity.INCREMENT_TIMESTAMP_PATTERN.matcher(fileNameWithoutExt);
            Matcher oldMatcher = MainActivity.OLD_TIMESTAMP_PATTERN.matcher(fileNameWithoutExt);

            String newFileNameWithoutExt;
            String newTimestamp = generateMillisTimestamp();

            if (incrementMatcher.find()) {
                String targetPart = incrementMatcher.group(1);
                newFileNameWithoutExt = fileNameWithoutExt.replaceAll("(" + MainActivity.TARGET_TIMESTAMP_PATTERN.pattern() + ")(_\\d{17})+$",
                        targetPart + "_" + newTimestamp);
            } else if (targetMatcher.find()) {
                newFileNameWithoutExt = fileNameWithoutExt + "_" + newTimestamp;
            } else {
                String cleanName = oldMatcher.replaceAll("");
                String randomStr = generateRandomString();
                newFileNameWithoutExt = cleanName + "_" + randomStr + "_" + newTimestamp;
            }

            return newFileNameWithoutExt + ext;
        }
    }
}