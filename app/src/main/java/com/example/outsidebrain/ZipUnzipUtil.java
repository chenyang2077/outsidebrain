package com.example.outsidebrain;

import android.util.Log;
import java.io.*;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipUnzipUtil {
    private static final String TAG = "ZipUnzipUtil";

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
            // 1. 分析压缩包根目录结构（关键修正点）
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
                }
            }
            Log.d(TAG, "解压成功，目标路径: " + finalTargetPath);
            return true;

        } catch (IOException e) {
            Log.e(TAG, "解压失败", e);
            return false;
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
