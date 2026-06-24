/*
软件名称：流动文档阅览整理软件V1.0
版本号：V1.0
功能描述：实现ZIP压缩包智能解压、文件夹冲突自动规避、TXT/图片文件唯一命名、压缩包结构分析
所属模块：文件解压模块
开发语言：Java
源码状态：完整未删减
*/
package com.example.outsidebrainkxmt563980;

import android.os.Environment;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 压缩包解压工具类：实现ZIP包智能解压，处理TXT/图片文件重命名、文件夹冲突规避、压缩包结构分析
 */
public class ZipUnzipUtil {
    private static final String TAG = "ZipUnzipUtil";
    private static final String ROOT_FOLDER_NAME = "主页根目录";
    private static final SimpleDateFormat MILLIS_TIMESTAMP_FORMAT =
            new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
    private static final Pattern INCREMENT_TIMESTAMP_PATTERN =
            Pattern.compile("_[A-Za-z0-9]{6}_\\d{13,17}(_\\d{13,17})*");
    private static final Pattern TARGET_TIMESTAMP_PATTERN =
            Pattern.compile("_[A-Za-z0-9]{6}_\\d{13,17}");
    private static final Pattern OLD_TIMESTAMP_PATTERN =
            Pattern.compile("_\\d{13,17}");
    private static final Set<String> IMAGE_SUFFIXES = new HashSet<String>() {{
        add(".png");
        add(".jpg");
        add(".jpeg");
        add(".gif");
        add(".bmp");
    }};

    /**
     * 核心解压方法：解压ZIP到目标目录，自动处理冲突与命名
     */
    public static boolean unzipToCurrentDir(String zipFilePath, String targetDir) {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists()) {
            Log.e(TAG, "压缩文件不存在: " + zipFilePath);
            return false;
        }

        try (ZipFile zf = new ZipFile(zipFile)) {
            RootDirInfo rootDirInfo = analyzeRootDirectory(zf);
            if (rootDirInfo == null) {
                Log.e(TAG, "无法识别压缩包结构");
                return false;
            }

            String targetRootFolder = getNonConflictFolderName(targetDir, rootDirInfo.rootFolderName);
            File rootTargetDir = new File(targetDir, targetRootFolder);
            if (!rootTargetDir.exists() && !rootTargetDir.mkdirs()) {
                Log.e(TAG, "创建根解压目录失败: " + rootTargetDir.getAbsolutePath());
                return false;
            }
            String finalTargetPath = rootTargetDir.getAbsolutePath();
            Set<ZipEntry> dirEntries = new HashSet<>();
            List<ZipEntry> fileEntries = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    dirEntries.add(entry);
                } else {
                    fileEntries.add(entry);
                }
            }
            Collections.sort(fileEntries, new Comparator<ZipEntry>() {
                @Override
                public int compare(ZipEntry o1, ZipEntry o2) {
                    String n1 = o1.getName();
                    String n2 = o2.getName();
                    boolean h1 = hasTimestampInName(n1);
                    boolean h2 = hasTimestampInName(n2);

                    if (!h1 && h2) return -1;
                    if (h1 && !h2) return 1;

                    if (h1 && h2) {
                        long t1 = getTimestampFromName(n1);
                        long t2 = getTimestampFromName(n2);
                        return Long.compare(t1, t2);
                    }
                    return Long.compare(o1.getTime(), o2.getTime());
                }
            });

            for (ZipEntry entry : dirEntries) {
                processDirectoryEntry(zf, entry, rootDirInfo, rootTargetDir);
            }

            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            Set<String> existingCleanNames = new HashSet<>();
            collectAllCleanFileNames(rootDir, existingCleanNames);
            int sequenceNumber = 0;

            for (ZipEntry entry : fileEntries) {
                sequenceNumber = processFileEntry(zf, entry, rootDirInfo, rootTargetDir, existingCleanNames, sequenceNumber);
            }

            Log.d(TAG, "解压成功，目标路径: " + finalTargetPath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "解压失败", e);
            return false;
        }
    }

    /**
     * 单个文件解压处理：保留安全校验，对齐剪切逻辑
     */
    private static int processFileEntry(ZipFile zipFile, ZipEntry entry,
                                        RootDirInfo rootDirInfo, File rootTargetDir,
                                        Set<String> existingCleanNames, int sequenceNumber) throws IOException {
        String entryName = entry.getName().replace("\\", "/");
        entryName = cleanZipEntryName(entryName);
        String relativePath;
        if (rootDirInfo.hasSingleRootFolder) {
            relativePath = entryName.substring(rootDirInfo.rootFolderName.length());
        } else {
            relativePath = entryName;
        }
        File targetFile = new File(rootTargetDir, relativePath);
        try {
            String canonicalTarget = targetFile.getCanonicalPath();
            String canonicalRoot = rootTargetDir.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalRoot + File.separator)) {
                Log.w(TAG, "发现危险文件，已跳过：" + entryName);
                return sequenceNumber;
            }
        } catch (Exception e) {
            Log.w(TAG, "安全校验失败，跳过文件：" + entryName);
            return sequenceNumber;
        }

        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        try (InputStream is = zipFile.getInputStream(entry);
             OutputStream os = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
        String fileName = targetFile.getName().toLowerCase();
        if (fileName.endsWith(".txt") || isImageFile(fileName)) {
            return processNamedFile(targetFile, existingCleanNames, sequenceNumber);
        }
        return sequenceNumber;
    }
    /**
     * 文件重命名核心：完全对齐剪切模块逻辑，序列号放末尾
     */
    private static int processNamedFile(File targetFile,
                                        Set<String> existingCleanNames, int sequenceNumber) {
        if (targetFile == null || !targetFile.exists()) {
            return sequenceNumber;
        }
        try {
            String originalName = targetFile.getName();
            String cleanName = removeTimestamp(originalName);
            String originalExt = getOriginalExtension(originalName);
            int lastDotIndex = cleanName.lastIndexOf(".");
            if (lastDotIndex > 0) {
                cleanName = cleanName.substring(0, lastDotIndex);
            }
            cleanName = getSafeCoreName(cleanName);
            String randomStr = generateRandomString();
            String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
            String seqStr = String.format(Locale.getDefault(), "%05d", sequenceNumber++);
            String finalTs = baseTimestamp.substring(0, baseTimestamp.length() - 5) + seqStr;
            String finalName;
            String[] parts = originalName.split("_");
            if (parts.length <= 2) {
                finalName = cleanName + "_" + randomStr + "_" + finalTs + originalExt;
            } else if (parts.length == 3) {
                finalName = originalName.substring(0, originalName.lastIndexOf('.')) + "_" + finalTs + originalExt;
            } else {
                String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf('.'));
                int lastUnder = nameWithoutExt.lastIndexOf('_');
                String prefix = nameWithoutExt.substring(0, lastUnder);
                finalName = prefix + "_" + finalTs + originalExt;
            }

            File newFile = new File(targetFile.getParentFile(), finalName);
            if (targetFile.renameTo(newFile)) {
                Log.d(TAG, "重命名成功: " + originalName + " → " + finalName);
            } else if (copyFileContent(targetFile, newFile)) {
                targetFile.delete();
            }
            return sequenceNumber;
        } catch (Exception e) {
            Log.e(TAG, "处理文件失败", e);
            return sequenceNumber;
        }
    }
    /**
     * 判断文件名是否包含时间戳：纯字符串判断，稳定兼容
     */
    private static boolean hasTimestampInName(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            fileName = fileName.substring(0, lastDot);
        }
        int lastUnder = fileName.lastIndexOf("_");
        if (lastUnder < 0 || lastUnder >= fileName.length() - 6) {
            return false;
        }
        String part = fileName.substring(lastUnder + 1);
        if (part.length() < 10) return false;
        for (int i = 0; i < part.length(); i++) {
            if (!Character.isDigit(part.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    /**
     * 从文件名提取时间戳数字
     */
    private static long getTimestampFromName(String fileName) {
        try {
            int lastDot = fileName.lastIndexOf(".");
            if (lastDot > 0) {
                fileName = fileName.substring(0, lastDot);
            }
            int lastUnder = fileName.lastIndexOf("_");
            String numStr = fileName.substring(lastUnder + 1);
            return Long.parseLong(numStr);
        } catch (Exception e) {
            return 0;
        }
    }
    /**
     * 创建压缩包内原有目录，不生成多余文件夹
     */
    private static void processDirectoryEntry(ZipFile zipFile, ZipEntry entry,
                                              RootDirInfo rootDirInfo, File rootTargetDir) {
        String entryName = cleanZipEntryName(entry.getName().replace("\\", "/"));
        String relativePath = rootDirInfo.hasSingleRootFolder
                ? entryName.substring(rootDirInfo.rootFolderName.length())
                : entryName;
        if (relativePath.isEmpty() || relativePath.equals("/")) {
            return;
        }
        File targetDir = new File(rootTargetDir, relativePath);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
    }
    /**
     * 清理ZIP内文件名非法字符，不破坏路径结构
     */
    private static String cleanZipEntryName(String name) {
        if (name == null) return "";
        name = name.replaceAll("[\\\\:*?\"<>|]", "");
        name = name.replaceAll("[\\n\\r\\t]", "");
        String[] parts = name.split("/");
        StringBuilder cleanedPath = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim().replaceAll("^\\.+", "").replaceAll("\\.+$", "");
            if (part.isEmpty()) part = "未知文件";
            cleanedPath.append(part);
            if (i < parts.length - 1) cleanedPath.append("/");
        }
        return cleanedPath.length() > 0 ? cleanedPath.toString() : "未知文件";
    }
    /**
     * 判断是否为图片文件
     */
    private static boolean isImageFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String suffix : IMAGE_SUFFIXES) {
            if (lowerFileName.endsWith(suffix)) return true;
        }
        return false;
    }
    /**
     * 生成6位随机字符串，用于唯一命名
     */
    private static String generateRandomString() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(6);
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    /**
     * 移除文件名中的时间戳，获取干净名称
     */
    private static String removeTimestamp(String name) {
        if (name == null) return "";
        name = name.replaceAll("\\.[^.]+$", "");
        name = INCREMENT_TIMESTAMP_PATTERN.matcher(name).replaceAll("");
        name = TARGET_TIMESTAMP_PATTERN.matcher(name).replaceAll("");
        name = OLD_TIMESTAMP_PATTERN.matcher(name).replaceAll("");
        return name.trim().isEmpty() ? "未命名" : name.trim();
    }
    /**
     * 获取文件后缀名
     */
    private static String getOriginalExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }
    /**
     * 获取安全的核心文件名，过滤非法字符
     */
    private static String getSafeCoreName(String name) {
        if (name == null || name.trim().isEmpty()) return "未命名文件";
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "");
    }
    /**
     * 递归收集已存在的干净文件名，防止冲突
     */
    private static void collectAllCleanFileNames(File dir, Set<String> namesSet) {
        if (dir == null || !dir.isDirectory() || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectAllCleanFileNames(file, namesSet);
            } else {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".txt") || isImageFile(fileName)) {
                    String clean = removeTimestamp(file.getName());
                    if (!clean.isEmpty()) namesSet.add(clean);
                }
            }
        }
    }
    /**
     * 文件复制兜底方法，重命名失败时使用
     */
    private static boolean copyFileContent(File source, File dest) throws IOException {
        if (!dest.exists()) dest.createNewFile();
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
        }
        return true;
    }
    /**
     * 分析ZIP根目录结构：判断是否单一根目录
     */
    private static RootDirInfo analyzeRootDirectory(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        Set<String> rootFolders = new HashSet<>();
        boolean hasRootFiles = false;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName().replace("\\", "/");
            if (!entryName.contains("/") || entryName.startsWith("/")) {
                if (!entry.isDirectory()) hasRootFiles = true;
                continue;
            }
            int firstSlashIndex = entryName.indexOf('/');
            String rootFolder = entryName.substring(0, firstSlashIndex + 1);
            rootFolders.add(rootFolder);
        }
        if (!hasRootFiles && rootFolders.size() == 1) {
            String rootFolderName = rootFolders.iterator().next();
            return new RootDirInfo(true, rootFolderName.substring(0, rootFolderName.length() - 1));
        } else {
            String zipFileName = new File(zipFile.getName()).getName();
            int dotIndex = zipFileName.lastIndexOf('.');
            if (dotIndex > 0) zipFileName = zipFileName.substring(0, dotIndex);
            return new RootDirInfo(false, zipFileName);
        }
    }
    /**
     * 生成无冲突文件夹名，自动加中文序号
     */
    public static String getNonConflictFolderName(String targetDir, String originalName) {
        File targetFolder = new File(targetDir, originalName);
        if (!targetFolder.exists()) return originalName;
        int suffix = 1;
        while (true) {
            String newName = originalName + "（" + suffix + "）";
            if (!new File(targetDir, newName).exists()) return newName;
            suffix++;
        }
    }
    /**
     * 压缩包结构信息内部类
     */
    private static class RootDirInfo {
        boolean hasSingleRootFolder;
        String rootFolderName;
        RootDirInfo(boolean hasSingleRootFolder, String rootFolderName) {
            this.hasSingleRootFolder = hasSingleRootFolder;
            this.rootFolderName = rootFolderName;
        }
    }
}