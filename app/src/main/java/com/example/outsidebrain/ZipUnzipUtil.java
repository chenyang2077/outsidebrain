package com.example.outsidebrain; // 添加包声明

import android.os.Environment;
import android.util.Log;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// 假设的时间戳格式常量，避免直接依赖MainActivity
public class ZipUnzipUtil {
    private static final String TAG = "ZipUnzipUtil";
    private static final String ROOT_FOLDER_NAME = "外置大脑";
    // 定义自己的时间戳格式，不依赖MainActivity
    private static final SimpleDateFormat MILLIS_TIMESTAMP_FORMAT =
            new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());

    /**
     * 解压到当前文件夹，处理所有文件夹重名
     */
    public static boolean unzipToCurrentDir(String zipFilePath, String targetDir) {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists()) {
            Log.e(TAG, "压缩文件不存在: " + zipFilePath);
            return false;
        }

        // 初始化本次解压的序列号（每次解压从0000开始）
        int sequenceNumber = 0;

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

            for (ZipEntry entry : dirEntries) {
                processDirectoryEntry(zf, entry, rootDirInfo, rootTargetDir);
            }

            for (ZipEntry entry : fileEntries) {
                sequenceNumber = processFileEntry(zf, entry, rootDirInfo, rootTargetDir, sequenceNumber);
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

    private static int processFileEntry(ZipFile zipFile, ZipEntry entry,
                                        RootDirInfo rootDirInfo, File rootTargetDir, int sequenceNumber) throws IOException {
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
                return sequenceNumber;
            }

            targetFile = new File(parentDir, targetFile.getName());
        }

        try (InputStream is = zipFile.getInputStream(entry);
             OutputStream os = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }

        if (targetFile.getName().toLowerCase().endsWith(".txt")) {
            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            return processTxtFileTimestamp(targetFile, rootDir, sequenceNumber);
        }

        return sequenceNumber;
    }

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
     * 处理TXT文件时间戳，使用序列号替代小时和分钟部分
     * 不依赖外部的UniqueFileNameHandler，内置基本实现
     */
    private static int processTxtFileTimestamp(File txtFile, File rootDir, int sequenceNumber) {
        if (txtFile == null || !txtFile.getName().toLowerCase().endsWith(".txt")) {
            return sequenceNumber;
        }

        try {
            // 1. 移除旧时间戳（内置简单实现）
            String originalName = txtFile.getName();
            String cleanName = removeTimestamp(originalName);

            // 2. 去除.txt扩展名
            if (cleanName.toLowerCase().endsWith(".txt")) {
                cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
            }
            cleanName = cleanName.trim();

            // 3. 生成随机字符串和基础时间戳
            String randomStr = generateRandomString();
            String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

            // 4. 替换时间戳中的小时和分钟部分为4位序列号
            String modifiedTimestamp = baseTimestamp;
            if (baseTimestamp.length() >= 12) {
                String datePart = baseTimestamp.substring(0, 8); // yyyyMMdd
                String timeRemaining = baseTimestamp.substring(12); // ssSSS
                String sequenceStr = String.format("%04d", sequenceNumber % 10000);
                modifiedTimestamp = datePart + sequenceStr + timeRemaining;
            }

            // 5. 生成时间戳后缀
            String timestampSuffix = "_" + randomStr + "_" + modifiedTimestamp;

            // 6. 生成唯一文件名（内置简单实现）
            String newFileName = getGlobalUniqueFileName(
                    rootDir,
                    txtFile.getParentFile(),
                    cleanName,
                    timestampSuffix
            );

            // 7. 重命名文件
            File newFile = new File(txtFile.getParentFile(), newFileName);
            if (txtFile.renameTo(newFile)) {
                Log.d(TAG, "TXT文件添加时间戳成功: " + originalName + " → " + newFileName);
                return sequenceNumber + 1;
            } else {
                Log.w(TAG, "无法为TXT文件添加时间戳: " + originalName);
                return sequenceNumber;
            }
        } catch (Exception e) {
            Log.e(TAG, "处理TXT文件时间戳失败", e);
            return sequenceNumber;
        }
    }

    /**
     * 内置的移除时间戳方法，不依赖外部类
     */
    private static String removeTimestamp(String fileName) {
        // 简单实现：移除最后一个下划线开始的时间戳部分
        int lastUnderlineIndex = fileName.lastIndexOf("_");
        if (lastUnderlineIndex > 0) {
            String potentialTimestamp = fileName.substring(lastUnderlineIndex + 1);
            // 判断是否是时间戳格式
            if (potentialTimestamp.matches("\\d{13,17}")) {
                return fileName.substring(0, lastUnderlineIndex);
            }
        }
        return fileName;
    }

    /**
     * 内置的生成唯一文件名方法，不依赖外部类
     */
    private static String getGlobalUniqueFileName(File rootDir, File parentDir,
                                                  String baseName, String suffix) {
        String fileName = baseName + suffix + ".txt";
        File testFile = new File(parentDir, fileName);

        // 如果文件名已存在，添加序号
        int counter = 1;
        while (testFile.exists()) {
            fileName = baseName + suffix + "(" + counter + ").txt";
            testFile = new File(parentDir, fileName);
            counter++;
        }
        return fileName;
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
    