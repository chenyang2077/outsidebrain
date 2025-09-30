package com.example.outsidebrain;

import android.os.Environment;
import android.util.Log;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipUnzipUtil {
    private static final String TAG = "ZipUnzipUtil";
    private static final String ROOT_FOLDER_NAME = "外置大脑";
    private static final SimpleDateFormat MILLIS_TIMESTAMP_FORMAT =
            new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());

    // 正则表达式模式定义
    // 匹配：_随机字符_时间戳1_时间戳2... (随机字符为6位字母数字，时间戳为13-17位数字)
    private static final Pattern INCREMENT_TIMESTAMP_PATTERN =
            Pattern.compile("_[A-Za-z0-9]{6}_\\d{13,17}(_\\d{13,17})*");
    // 匹配：_随机字符_时间戳 (随机字符为6位字母数字，时间戳为13-17位数字)
    private static final Pattern TARGET_TIMESTAMP_PATTERN =
            Pattern.compile("_[A-Za-z0-9]{6}_\\d{13,17}");
    // 匹配：_时间戳 (仅时间戳，13-17位数字)
    private static final Pattern OLD_TIMESTAMP_PATTERN =
            Pattern.compile("_\\d{13,17}");

    /**
     * 解压到当前文件夹，处理所有文件夹重名
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
            String finalTargetPath = new File(targetDir, targetRootFolder).getAbsolutePath();
            File rootTargetDir = new File(finalTargetPath);

            Set<ZipEntry> dirEntries = new HashSet<>();
            Set<ZipEntry> fileEntries = new HashSet<>();

            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    dirEntries.add(entry);
                } else {
                    fileEntries.add(entry);
                }
            }

            // 先创建目录
            for (ZipEntry entry : dirEntries) {
                processDirectoryEntry(zf, entry, rootDirInfo, rootTargetDir);
            }

            // 收集所有已存在的TXT文件的"清洁名称"用于查重
            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            Set<String> existingTxtCleanNames = new HashSet<>();
            collectAllCleanTxtNames(rootDir, existingTxtCleanNames);

            // 处理文件
            for (ZipEntry entry : fileEntries) {
                processFileEntry(zf, entry, rootDirInfo, rootTargetDir, existingTxtCleanNames);
            }

            Log.d(TAG, "解压成功，目标路径: " + finalTargetPath);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "解压失败", e);
            return false;
        }
    }

    private static void processDirectoryEntry(ZipFile zipFile, ZipEntry entry,
                                              RootDirInfo rootDirInfo, File rootTargetDir) throws IOException {
        String entryName = entry.getName().replace("\\", "/");
        String relativePath;

        if (rootDirInfo.hasSingleRootFolder) {
            relativePath = entryName.substring(rootDirInfo.rootFolderName.length());
        } else {
            relativePath = entryName;
        }

        if (relativePath.isEmpty() || relativePath.equals("/")) {
            return;
        }

        File targetDir = new File(rootTargetDir, relativePath);
        File parentDir = targetDir.getParentFile();

        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            Log.e(TAG, "创建父文件夹失败: " + parentDir.getAbsolutePath());
            return;
        }

        String uniqueDirName = getNonConflictFolderName(parentDir.getAbsolutePath(),
                targetDir.getName());
        File uniqueTargetDir = new File(parentDir, uniqueDirName);

        if (!uniqueTargetDir.exists() && !uniqueTargetDir.mkdirs()) {
            Log.e(TAG, "创建文件夹失败: " + uniqueTargetDir.getAbsolutePath());
        }
    }

    private static void processFileEntry(ZipFile zipFile, ZipEntry entry,
                                         RootDirInfo rootDirInfo, File rootTargetDir,
                                         Set<String> existingTxtCleanNames) throws IOException {
        String entryName = entry.getName().replace("\\", "/");
        String relativePath;

        if (rootDirInfo.hasSingleRootFolder) {
            relativePath = entryName.substring(rootDirInfo.rootFolderName.length());
        } else {
            relativePath = entryName;
        }

        File targetFile = new File(rootTargetDir, relativePath);
        File parentDir = targetFile.getParentFile();

        if (parentDir != null && !parentDir.exists()) {
            String uniqueParentName = getNonConflictFolderName(
                    parentDir.getParentFile().getAbsolutePath(),
                    parentDir.getName());
            parentDir = new File(parentDir.getParentFile(), uniqueParentName);

            if (!parentDir.mkdirs()) {
                Log.e(TAG, "创建父文件夹失败: " + parentDir.getAbsolutePath());
                return;
            }

            targetFile = new File(parentDir, targetFile.getName());
        }

        // 写入文件内容
        try (InputStream is = zipFile.getInputStream(entry);
             OutputStream os = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }

        // 处理TXT文件
        if (targetFile.getName().toLowerCase().endsWith(".txt")) {
            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            processTxtFile(targetFile, rootDir, existingTxtCleanNames);
        }
    }

    /**
     * 处理TXT文件：移除旧随机字符和时间戳，查重，添加新的随机字符和时间戳
     */
    private static void processTxtFile(File txtFile, File rootDir, Set<String> existingTxtCleanNames) {
        if (txtFile == null || !txtFile.exists() || !txtFile.getName().toLowerCase().endsWith(".txt")) {
            return;
        }

        try {
            // 1. 移除原有随机字符和时间戳
            String originalName = txtFile.getName();
            String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf("."));

            // 先移除增量格式（随机字符+多个时间戳）
            String cleanName = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt).replaceAll("");
            // 再移除基础格式（随机字符+单个时间戳）
            cleanName = TARGET_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");
            // 最后处理旧格式（仅时间戳）
            cleanName = OLD_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");

            // 确保清理后的名称不为空
            if (cleanName.trim().isEmpty()) {
                cleanName = "未命名文件";
            }

            // 2. 进行全域查重（使用已收集的现有名称）
            String uniqueBaseName = findUniqueBaseName(cleanName, existingTxtCleanNames);

            // 将新名称添加到集合中，防止后续文件重名
            existingTxtCleanNames.add(uniqueBaseName);

            // 3. 生成新的随机字符和时间戳
            String randomStr = generateRandomString();
            String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
            String newFileName = uniqueBaseName + "_" + randomStr + "_" + newTimestamp + ".txt";

            // 4. 重命名文件
            File targetFile = new File(txtFile.getParentFile(), newFileName);
            if (txtFile.renameTo(targetFile)) {
                Log.d(TAG, "TXT文件处理成功: " + originalName + " → " + newFileName);
                return;
            }

            // 如果重命名失败，尝试复制后删除原文件
            if (copyFileContent(txtFile, targetFile)) {
                txtFile.delete();
                Log.d(TAG, "TXT文件复制并重命名成功: " + originalName + " → " + newFileName);
            } else {
                Log.w(TAG, "TXT文件重命名失败: " + originalName);
            }
        } catch (Exception e) {
            Log.e(TAG, "处理TXT文件失败", e);
        }
    }

    /**
     * 生成6位随机字符串
     */
    private static String generateRandomString() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(6);
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 查找唯一的基础名称，如果已存在则添加序列号
     */
    private static String findUniqueBaseName(String baseName, Set<String> existingNames) {
        if (!existingNames.contains(baseName)) {
            return baseName;
        }

        // 如果已存在，添加序列号
        int counter = 1;
        while (true) {
            String candidate = baseName + "(" + counter + ")";
            if (!existingNames.contains(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    /**
     * 收集所有TXT文件去除随机字符和时间戳后的名称
     */
    private static void collectAllCleanTxtNames(File dir, Set<String> namesSet) {
        if (dir == null || !dir.isDirectory() || !dir.exists()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectAllCleanTxtNames(file, namesSet); // 递归处理子目录
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                String fileName = file.getName();
                String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));

                // 移除所有时间戳格式
                String cleanName = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt).replaceAll("");
                cleanName = TARGET_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");
                cleanName = OLD_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");

                // 确保清理后的名称不为空
                if (!cleanName.trim().isEmpty()) {
                    namesSet.add(cleanName);
                }
            }
        }
    }

    /**
     * 复制文件内容
     */
    private static boolean copyFileContent(File source, File dest) throws IOException {
        if (!dest.exists() && !dest.createNewFile()) {
            return false;
        }

        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
        return true;
    }

    private static RootDirInfo analyzeRootDirectory(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        Set<String> rootFolders = new HashSet<>();
        boolean hasRootFiles = false;

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName().replace("\\", "/");

            if (!entryName.contains("/") || entryName.startsWith("/")) {
                if (!entry.isDirectory()) {
                    hasRootFiles = true;
                }
                continue;
            }

            int firstSlashIndex = entryName.indexOf('/');
            String rootFolder = entryName.substring(0, firstSlashIndex + 1);
            rootFolders.add(rootFolder);
        }

        if (!hasRootFiles && rootFolders.size() == 1) {
            String rootFolderName = rootFolders.iterator().next();
            String folderName = rootFolderName.substring(0, rootFolderName.length() - 1);
            return new RootDirInfo(true, folderName);
        } else {
            String zipFileName = new File(zipFile.getName()).getName();
            int dotIndex = zipFileName.lastIndexOf('.');
            if (dotIndex > 0) {
                zipFileName = zipFileName.substring(0, dotIndex);
            }
            return new RootDirInfo(false, zipFileName);
        }
    }

    public static String getNonConflictFolderName(String targetDir, String originalName) {
        String baseName = originalName;
        File targetFolder = new File(targetDir, baseName);

        if (!targetFolder.exists()) {
            return baseName;
        }

        int suffix = 1;
        while (true) {
            String newName = baseName + "（" + suffix + "）";
            File newFolder = new File(targetDir, newName);
            if (!newFolder.exists()) {
                return newName;
            }
            suffix++;
        }
    }

    private static class RootDirInfo {
        boolean hasSingleRootFolder;
        String rootFolderName;

        RootDirInfo(boolean hasSingleRootFolder, String rootFolderName) {
            this.hasSingleRootFolder = hasSingleRootFolder;
            this.rootFolderName = rootFolderName;
        }
    }
}
