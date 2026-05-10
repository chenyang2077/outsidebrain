/*
软件名称：快乐文字
版本号：V1.0
功能描述：基于Android应用私有存储实现文件/文件夹管理，提供唯一命名、创建/复制/移动、解压等核心功能
所属模块：文件操作模块
开发语言：Java
*/
package com.example.outsidebrainkxmt563980;

import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件操作工具类：仅用于应用内部目录安全管理
 */
public class FileUtils {
    private static final String TAG = "FileUtils";

    /**
     * 递归收集目录下所有文件夹名称（全局去重）
     */
    private static List<String> getAllGlobalFolderNames(File rootDir) {
        List<String> globalFolderNames = new ArrayList<>();
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            Log.e(TAG, "根目录不存在或非目录: " + rootDir.getAbsolutePath());
            return globalFolderNames;
        }
        collectFolderNamesRecursive(rootDir, globalFolderNames);
        return globalFolderNames;
    }

    /**
     * 递归遍历目录，收集所有子文件夹名称
     */
    private static void collectFolderNamesRecursive(File currentDir, List<String> folderNames) {
        File[] files = currentDir.listFiles();
        if (files == null) {
            Log.w(TAG, "无法读取目录: " + currentDir.getAbsolutePath());
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                String folderName = file.getName();
                if (!folderNames.contains(folderName)) {
                    folderNames.add(folderName);
                }
                collectFolderNamesRecursive(file, folderNames);
            }
        }
    }

    /**
     * 生成全局唯一的文件夹名称
     */
    public static String generateUniqueFolderName(File parentDir, String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "新建文件夹";
        }
        baseName = baseName.trim();
        List<String> globalExistingNames = getAllGlobalFolderNames(parentDir);
        if (!globalExistingNames.contains(baseName)) {
            return baseName;
        }
        int counter = 1;
        while (true) {
            String newName = baseName + "(" + counter + ")";
            if (!globalExistingNames.contains(newName)) {
                return newName;
            }
            counter++;
        }
    }

    /**
     * 创建全局唯一的文件夹
     */
    public static File createUniqueFolder(File parentDir, String baseName) {
        String uniqueName = generateUniqueFolderName(parentDir, baseName);
        File newFolder = new File(parentDir, uniqueName);
        if (newFolder.mkdirs()) {
            Log.d(TAG, "创建唯一文件夹成功: " + newFolder.getAbsolutePath());
            return newFolder;
        } else {
            Log.e(TAG, "创建文件夹失败: " + newFolder.getAbsolutePath());
            return null;
        }
    }

    /**
     * 复制文件夹
     */
    public static boolean copyFolder(File sourceFolder, File targetParentDir) {
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            Log.e(TAG, "源文件夹无效");
            return false;
        }
        String targetUniqueName = generateUniqueFolderName(targetParentDir, sourceFolder.getName());
        File targetFolder = new File(targetParentDir, targetUniqueName);
        if (!targetFolder.mkdirs()) {
            Log.e(TAG, "无法创建目标文件夹: " + targetFolder.getAbsolutePath());
            return false;
        }
        File[] files = sourceFolder.listFiles();
        if (files == null) {
            Log.w(TAG, "源文件夹为空: " + sourceFolder.getAbsolutePath());
            return true;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if (!copyFolder(file, targetFolder)) {
                    Log.e(TAG, "复制子文件夹失败: " + file.getAbsolutePath());
                    return false;
                }
            } else {
                if (!copyFile(file, new File(targetFolder, file.getName()))) {
                    Log.e(TAG, "复制文件失败: " + file.getAbsolutePath());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 移动文件夹
     */
    public static boolean moveFolder(File sourceFolder, File targetParentDir) {
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            Log.e(TAG, "源文件夹无效");
            return false;
        }
        File targetFolder = new File(targetParentDir, sourceFolder.getName());
        if (targetFolder.exists()) {
            if (!deleteFolder(targetFolder)) {
                Log.e(TAG, "目标位置已存在同名文件夹且无法删除: " + targetFolder.getAbsolutePath());
                return false;
            }
        }
        if (sourceFolder.renameTo(targetFolder)) {
            Log.d(TAG, "移动文件夹成功: " + sourceFolder.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
            return true;
        } else {
            Log.w(TAG, "直接移动失败，尝试复制后删除");
            if (copyFolder(sourceFolder, targetParentDir)) {
                if (deleteFolder(sourceFolder)) {
                    Log.d(TAG, "通过复制删除完成移动");
                    return true;
                } else {
                    Log.e(TAG, "复制成功但删除源文件夹失败");
                    return false;
                }
            } else {
                Log.e(TAG, "移动文件夹失败");
                return false;
            }
        }
    }

    /**
     * 复制单个文件
     */
    private static boolean copyFile(File sourceFile, File targetFile) {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.isFile()) {
            Log.e(TAG, "源文件无效: " + (sourceFile != null ? sourceFile.getAbsolutePath() : "null"));
            return false;
        }
        File targetParent = targetFile.getParentFile();
        if (targetParent != null && !targetParent.exists() && !targetParent.mkdirs()) {
            Log.e(TAG, "无法创建目标文件父目录: " + targetParent.getAbsolutePath());
            return false;
        }
        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "复制文件失败", e);
            return false;
        }
    }

    /**
     * 递归删除文件夹
     */
    public static boolean deleteFolder(File folder) {
        if (folder == null || !folder.exists()) {
            return true;
        }
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }
        return folder.delete();
    }

    /**
     * 解压ZIP文件
     */
    public static boolean unzipFile(File zipFile, File targetDir) {
        return ZipUnzipUtil.unzipToCurrentDir(zipFile.getAbsolutePath(), targetDir.getAbsolutePath());
    }
}