/*
软件名称：流动信息文件管理系统
版本号：V1.0
功能描述：生成无冲突文件名、清理文件名中的时间戳、解析文件名结构，保障文件命名唯一性
所属模块：文件命名模块
开发语言：Java
*/
package com.example.outsidebrain;
import android.text.TextUtils;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 唯一文件名处理工具类：生成无冲突文件名、清理时间戳、解析文件名结构
 */
public class UniqueFileNameHandler {
    private static final String TAG = "UniqueFileNameHandler";
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(.*?)\\((\\d+)\\)$");
    private static final Pattern SINGLE_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private static final Pattern MULTI_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})+");
    /**
     * 生成6位随机字符串（包含大小写字母和数字）
     * @return String 6位随机字符串
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
    /**
     * 生成全局唯一的TXT文件名，自动规避命名冲突
     * @return String 无冲突的唯一文件名
     */
    public static String getGlobalUniqueFileName(File rootDir, File targetDir, String baseName, String timestamp) {
        String cleanedBaseName = cleanTitle(baseName);
        NameParts nameParts = parseNameParts(cleanedBaseName);
        String coreName = nameParts.coreName;
        List<File> conflictFiles = findConflictingFiles(rootDir, coreName);
        List<Integer> usedSuffixes = new ArrayList<>();
        for (File file : conflictFiles) {
            String fileName = cleanTitle(file.getName());
            fileName = fileName.replace(".txt", "").trim();
            NameParts parts = parseNameParts(fileName);
            if (parts.coreName.equals(coreName)) {
                usedSuffixes.add(parts.suffix);
            }
        }
        int suffix = 0;
        while (usedSuffixes.contains(suffix)) {
            suffix++;
        }
        return buildFileName(coreName, suffix, timestamp);
    }
    /**
     * 清理文件名中的各类时间戳，返回纯文本标题
     * @return String 清理后的文件名
     */
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
    /**
     * 解析文件名结构，提取核心名称和数字后缀
     * @return NameParts 文件名解析结果
     */
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
    /**
     * 构建最终文件名，拼接核心名称、后缀和时间戳
     * @return String 完整文件名
     */
    private static String buildFileName(String coreName, int suffix, String timestamp) {
        if (suffix <= 0) {
            return coreName + timestamp + ".txt";
        } else {
            return coreName + "(" + suffix + ")" + timestamp + ".txt";
        }
    }
    /**
     * 移除文件名中的所有时间戳（含旧版格式）
     * @return String 去时间戳后的文件名
     */
    public static String removeTimestamp(String fileName) {
        String cleaned = cleanTitle(fileName);
        if (MainActivity.FILE_MILLIS_TIMESTAMP_PATTERN != null) {
            cleaned = MainActivity.FILE_MILLIS_TIMESTAMP_PATTERN.matcher(cleaned).replaceAll("");
        }
        return cleaned;
    }
    /**
     * 查找指定根目录下所有与核心名称冲突的TXT文件
     * @return List<File> 冲突文件列表
     */
    private static List<File> findConflictingFiles(File rootDir, String coreName) {
        List<File> result = new ArrayList<>();
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            return result;
        }
        searchFiles(rootDir, coreName, result);
        return result;
    }
    /**
     * 递归搜索目录下的冲突TXT文件
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
                String fileName = cleanTitle(file.getName());
                fileName = fileName.replace(".txt", "");
                NameParts parts = parseNameParts(fileName);
                if (parts.coreName.equals(coreName)) {
                    result.add(file);
                }
            }
        }
    }
    /**
     * 内部数据类：存储文件名解析结果（核心名称、数字后缀）
     */
    private static class NameParts {
        String coreName;
        int suffix;

        NameParts(String coreName, int suffix) {
            this.coreName = coreName;
            this.suffix = suffix;
        }
    }
    /**
     * 时间戳处理内部类：生成时间戳、处理TXT文件名时间戳更新
     */
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