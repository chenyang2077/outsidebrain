package com.example.outsidebrain;

import android.os.Environment;
import android.util.Log;

import java.io.*;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.Random;

public class ZipUnzipUtil {
    private static final String TAG = "ZipUnzipUtil";
    private static final String ROOT_FOLDER_NAME = "外置大脑";

    /**
     * 解压到当前文件夹，处理所有文件夹重名（包括根目录和内部文件夹）
     * @param zipFilePath 压缩文件路径
     * @param targetDir 当前目标文件夹（解压到这里）
     * @return 解压是否成功
     */
    public static boolean unzipToCurrentDir(String zipFilePath, String targetDir) {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists()) {
            Log.e(TAG, "压缩文件不存在: " + zipFilePath);
            return false;
        }

        try (ZipFile zf = new ZipFile(zipFile)) {
            // 1. 分析压缩包根目录结构
            RootDirInfo rootDirInfo = analyzeRootDirectory(zf);
            if (rootDirInfo == null) {
                Log.e(TAG, "无法识别压缩包结构");
                return false;
            }

            // 2. 处理根目录重名：生成不冲突的目标文件夹名称
            String targetRootFolder = getNonConflictFolderName(targetDir, rootDirInfo.rootFolderName);
            String finalTargetPath = new File(targetDir, targetRootFolder).getAbsolutePath();
            File rootTargetDir = new File(finalTargetPath);

            // 3. 收集所有需要解压的条目，先处理文件夹
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

            // 4. 先创建所有文件夹（处理重名）
            for (ZipEntry entry : dirEntries) {
                processDirectoryEntry(zf, entry, rootDirInfo, rootTargetDir);
            }

            // 5. 再处理所有文件
            for (ZipEntry entry : fileEntries) {
                processFileEntry(zf, entry, rootDirInfo, rootTargetDir);
            }

            Log.d(TAG, "解压成功，目标路径: " + finalTargetPath);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "解压失败", e);
            return false;
        }
    }

    /**
     * 处理文件夹条目，确保名称唯一
     */
    private static void processDirectoryEntry(ZipFile zipFile, ZipEntry entry,
                                              RootDirInfo rootDirInfo, File rootTargetDir) throws IOException {
        String entryName = entry.getName().replace("\\", "/");
        String relativePath;

        // 计算相对路径
        if (rootDirInfo.hasSingleRootFolder) {
            relativePath = entryName.substring(rootDirInfo.rootFolderName.length());
        } else {
            relativePath = entryName;
        }

        // 处理空路径
        if (relativePath.isEmpty() || relativePath.equals("/")) {
            return;
        }

        // 构建目标文件夹路径
        File targetDir = new File(rootTargetDir, relativePath);
        File parentDir = targetDir.getParentFile();

        // 确保父目录存在
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            Log.e(TAG, "创建父文件夹失败: " + parentDir.getAbsolutePath());
            return;
        }

        // 检查并处理当前文件夹重名
        String uniqueDirName = getNonConflictFolderName(parentDir.getAbsolutePath(),
                targetDir.getName());
        File uniqueTargetDir = new File(parentDir, uniqueDirName);

        // 创建唯一文件夹
        if (!uniqueTargetDir.exists() && !uniqueTargetDir.mkdirs()) {
            Log.e(TAG, "创建文件夹失败: " + uniqueTargetDir.getAbsolutePath());
        }
    }



    /**
     * 处理文件条目，使用已创建的唯一文件夹路径
     */
    private static void processFileEntry(ZipFile zipFile, ZipEntry entry,
                                         RootDirInfo rootDirInfo, File rootTargetDir) throws IOException {
        String entryName = entry.getName().replace("\\", "/");
        String relativePath;

        // 计算相对路径
        if (rootDirInfo.hasSingleRootFolder) {
            relativePath = entryName.substring(rootDirInfo.rootFolderName.length());
        } else {
            relativePath = entryName;
        }

        // 构建目标文件路径
        File targetFile = new File(rootTargetDir, relativePath);
        File parentDir = targetFile.getParentFile();

        // 如果父目录不存在，说明是新文件夹，需要检查重名
        if (parentDir != null && !parentDir.exists()) {
            // 获取唯一父目录名
            String uniqueParentName = getNonConflictFolderName(
                    parentDir.getParentFile().getAbsolutePath(),
                    parentDir.getName());
            parentDir = new File(parentDir.getParentFile(), uniqueParentName);

            // 创建父目录
            if (!parentDir.mkdirs()) {
                Log.e(TAG, "创建父文件夹失败: " + parentDir.getAbsolutePath());
                return;
            }

            // 更新目标文件路径
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

        // 处理TXT文件的时间戳和唯一性
        if (targetFile.getName().toLowerCase().endsWith(".txt")) {
            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            processTxtFileTimestamp(targetFile, rootDir);
        }
    }
    /**
     * 新增：生成6位随机字符串（字母+数字组合）
     * 用于TXT文件名的随机字符串部分
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
    // 在解压逻辑中，对TXT文件添加隐藏时间戳
    // 原方法中“生成时间戳”的部分需要修改，完整修改后如下：
    private static void processTxtFileTimestamp(File txtFile, File rootDir) {
        if (txtFile == null || !txtFile.getName().toLowerCase().endsWith(".txt")) {
            return;
        }

        try {
            // 1. 移除旧时间戳（逻辑不变）
            String originalName = txtFile.getName();
            String cleanName = UniqueFileNameHandler.removeTimestamp(originalName);

            // 2. 去除.txt扩展名（逻辑不变）
            if (cleanName.toLowerCase().endsWith(".txt")) {
                cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
            }
            cleanName = cleanName.trim();

            // 3. 关键修改：生成“随机字符串+毫秒时间戳”（格式：_随机字符串_17位时间戳）
            String randomStr = generateRandomString(); // 调用新增的随机字符串方法
            String millisTimestamp = MainActivity.MILLIS_TIMESTAMP_FORMAT.format(new Date());
            String timestampSuffix = "_" + randomStr + "_" + millisTimestamp; // 拼接格式

            // 4. 生成唯一文件名（逻辑不变，参数改为新的timestampSuffix）
            String newFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDir,
                    txtFile.getParentFile(),
                    cleanName,
                    timestampSuffix // 传入带随机字符串的时间戳
            );

            // 5. 重命名文件（逻辑不变，增加异常捕获更稳健）
            File newFile = new File(txtFile.getParentFile(), newFileName);
            if (txtFile.renameTo(newFile)) {
                Log.d(TAG, "TXT文件添加时间戳成功: " + originalName + " → " + newFileName);
            } else {
                Log.w(TAG, "无法为TXT文件添加时间戳: " + originalName);
            }
        } catch (Exception e) {
            Log.e(TAG, "处理TXT文件时间戳失败", e); // 新增异常捕获，避免崩溃
        }
    }

    /**
     * 分析压缩包根目录结构
     * 主要判断：是否所有内容都在一个单一的根文件夹下
     */
    private static RootDirInfo analyzeRootDirectory(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        Set<String> rootFolders = new HashSet<>();
        boolean hasRootFiles = false; // 是否有直接在根目录的文件（非文件夹）

        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();

            // 处理路径分隔符统一为"/"
            entryName = entryName.replace("\\", "/");

            // 判断是否是根目录文件（没有"/"或第一个字符就是"/"）
            if (!entryName.contains("/") || entryName.startsWith("/")) {
                if (!entry.isDirectory()) {
                    hasRootFiles = true; // 根目录存在文件
                }
                continue;
            }

            // 提取根文件夹（第一个"/"之前的部分）
            int firstSlashIndex = entryName.indexOf('/');
            String rootFolder = entryName.substring(0, firstSlashIndex + 1); // 保留末尾"/"
            rootFolders.add(rootFolder);
        }

        // 情况1：所有内容都在同一个根文件夹下（无任何根目录文件，且只有一个根文件夹）
        if (!hasRootFiles && rootFolders.size() == 1) {
            String rootFolderName = rootFolders.iterator().next();
            // 去除末尾的"/"作为文件夹名
            String folderName = rootFolderName.substring(0, rootFolderName.length() - 1);
            return new RootDirInfo(true, folderName);
        }
        // 情况2：使用压缩包文件名作为根文件夹名
        else {
            String zipFileName = new File(zipFile.getName()).getName();
            // 去除扩展名
            int dotIndex = zipFileName.lastIndexOf('.');
            if (dotIndex > 0) {
                zipFileName = zipFileName.substring(0, dotIndex);
            }
            return new RootDirInfo(false, zipFileName);
        }
    }

    /**
     * 生成不冲突的文件夹名称（重名则加序列号）
     * 已修改为public权限，允许外部类访问
     */
    /**
     * 生成不冲突的文件夹名称（重名则加序列号）
     * 已修改为public权限，允许外部类访问
     */
    public static String getNonConflictFolderName(String targetDir, String originalName) {
        String baseName = originalName;
        File targetFolder = new File(targetDir, baseName);

        if (!targetFolder.exists()) {
            return baseName;
        }

        // 重名则添加序列号（1）（2）...
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

    /**
     * 内部类：存储压缩包根目录信息
     */
    private static class RootDirInfo {
        boolean hasSingleRootFolder; // 是否有单一根文件夹
        String rootFolderName;      // 根文件夹名称

        RootDirInfo(boolean hasSingleRootFolder, String rootFolderName) {
            this.hasSingleRootFolder = hasSingleRootFolder;
            this.rootFolderName = rootFolderName;
        }
    }
}
