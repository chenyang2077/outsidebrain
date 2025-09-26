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

public class ZipUnzipUtil {
    private static final String TAG = "ZipUnzipUtil";
    private static final String ROOT_FOLDER_NAME = "外置大脑";

    /**
     * 解压到当前文件夹，处理根目录文件夹重名（整体添加序列号）
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

            // 3. 执行解压（保持内部结构）
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // 处理路径分隔符统一为"/"
                entryName = entryName.replace("\\", "/");

                // 构建目标路径：根目录替换为带序列号的文件夹，保留内部结构
                String relativePath;
                if (rootDirInfo.hasSingleRootFolder) {
                    // 情况1：有单一根文件夹（如"123/"），去掉根文件夹后取相对路径
                    relativePath = entryName.substring(rootDirInfo.rootFolderName.length());
                } else {
                    // 情况2：无单一根文件夹，直接使用entryName作为相对路径
                    relativePath = entryName;
                }

                // 拼接最终目标路径
                File targetFile = new File(new File(targetDir, targetRootFolder), relativePath);

                if (entry.isDirectory()) {
                    if (!targetFile.exists() && !targetFile.mkdirs()) {
                        Log.e(TAG, "创建文件夹失败: " + targetFile.getAbsolutePath());
                        return false;
                    }
                } else {
                    File parentFile = targetFile.getParentFile();
                    if (parentFile != null && !parentFile.exists() && !parentFile.mkdirs()) {
                        Log.e(TAG, "创建父文件夹失败: " + parentFile.getAbsolutePath());
                        return false;
                    }

                    // 写入文件内容
                    try (InputStream is = zf.getInputStream(entry);
                         OutputStream os = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[1024 * 4];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                        }
                    }

                    // 处理TXT文件的时间戳和唯一性
                    if (targetFile.getName().toLowerCase().endsWith(".txt")) {
                        // 传入外置大脑根目录
                        File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
                        processTxtFileTimestamp(targetFile, rootDir);
                    }
                }
            }
            Log.d(TAG, "解压成功，目标路径: " + finalTargetPath);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "解压失败", e);
            return false;
        }
    }

    // 在解压逻辑中，对TXT文件添加隐藏时间戳
    private static void processTxtFileTimestamp(File txtFile, File rootDir) {
        if (txtFile == null || !txtFile.getName().toLowerCase().endsWith(".txt")) {
            return;
        }

        // 1. 移除文件名中可能存在的旧时间戳
        String originalName = txtFile.getName();
        String cleanName = UniqueFileNameHandler.removeTimestamp(originalName);

        // 2. 去除原有扩展名
        if (cleanName.endsWith(".txt")) {
            cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
        }

        // 3. 添加新的秒级时间戳（放在末尾隐藏）
        String newTimestamp = "_" + MainActivity.SECOND_TIMESTAMP_FORMAT.format(new Date());

        // 4. 获取全局唯一文件名
        String newFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                rootDir,
                txtFile.getParentFile(),
                cleanName,
                newTimestamp
        );

        File newFile = new File(txtFile.getParentFile(), newFileName);

        // 5. 执行重命名
        if (!txtFile.renameTo(newFile)) {
            Log.w(TAG, "无法为TXT文件添加时间戳: " + originalName);
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
     */
    private static String getNonConflictFolderName(String targetDir, String originalName) {
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
