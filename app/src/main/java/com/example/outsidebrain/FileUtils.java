package com.example.outsidebrain;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    private static final String TAG = "FileUtils";
    // 应用根目录名称
    private static final String APP_ROOT_FOLDER = "外置大脑";

    /**
     * 获取"外置大脑"根目录
     */
    public static File getAppRootDirectory() {
        return new File(Environment.getExternalStorageDirectory(), APP_ROOT_FOLDER);
    }

    /**
     * 递归收集"外置大脑"根目录下所有文件夹名称（包括所有子文件夹）
     * 用于全局查重，确保根目录及所有子目录中没有重名文件夹
     */
    private static List<String> getAllGlobalFolderNames() {
        List<String> globalFolderNames = new ArrayList<>();
        File rootDir = getAppRootDirectory();
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            Log.e(TAG, "根目录不存在或非目录: " + rootDir.getAbsolutePath());
            return globalFolderNames;
        }
        // 递归收集所有文件夹名称
        collectFolderNamesRecursive(rootDir, globalFolderNames);
        return globalFolderNames;
    }

    /**
     * 递归收集指定目录下所有子文件夹名称
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
                // 递归处理子目录
                collectFolderNamesRecursive(file, folderNames);
            }
        }
    }

    /**
     * 生成全局唯一的文件夹名称（检查"外置大脑"下所有文件夹，包括子文件夹）
     */
    public static String generateUniqueFolderName(File parentDir, String baseName) {
        // 处理空名称
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "新建文件夹";
        }
        baseName = baseName.trim();

        // 获取全局所有已存在的文件夹名称
        List<String> globalExistingNames = getAllGlobalFolderNames();

        // 检查基础名称是否可用
        if (!globalExistingNames.contains(baseName)) {
            return baseName;
        }

        // 生成带序号的唯一名称
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
     * 新建全局唯一的文件夹
     */
    public static File createUniqueFolder(File parentDir, String baseName) {
        // 校验父目录是否在"外置大脑"根目录下
        if (!isUnderAppRoot(parentDir)) {
            Log.e(TAG, "父目录不在外置大脑根目录下");
            return null;
        }

        String uniqueName = generateUniqueFolderName(parentDir, baseName);
        File newFolder = new File(parentDir, uniqueName);
        if (newFolder.mkdirs()) {
            Log.d(TAG, "创建全局唯一文件夹成功: " + newFolder.getAbsolutePath());
            return newFolder;
        } else {
            Log.e(TAG, "创建文件夹失败: " + newFolder.getAbsolutePath());
            return null;
        }
    }

    /**
     * 复制文件夹（需要全局查重）
     * 复制的文件夹及其子文件夹都会生成全局唯一名称
     */
    public static boolean copyFolder(File sourceFolder, File targetParentDir) {
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            Log.e(TAG, "源文件夹无效");
            return false;
        }

        // 校验目标目录是否在"外置大脑"根目录下
        if (!isUnderAppRoot(targetParentDir)) {
            Log.e(TAG, "目标目录不在外置大脑根目录下");
            return false;
        }

        // 生成目标文件夹的全局唯一名称
        String targetUniqueName = generateUniqueFolderName(targetParentDir, sourceFolder.getName());
        File targetFolder = new File(targetParentDir, targetUniqueName);

        // 创建目标文件夹
        if (!targetFolder.mkdirs()) {
            Log.e(TAG, "无法创建目标文件夹: " + targetFolder.getAbsolutePath());
            return false;
        }

        // 复制子文件和子文件夹
        File[] files = sourceFolder.listFiles();
        if (files == null) {
            Log.w(TAG, "源文件夹为空: " + sourceFolder.getAbsolutePath());
            return true;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归复制子文件夹（会自动处理子文件夹的重名问题）
                if (!copyFolder(file, targetFolder)) {
                    Log.e(TAG, "复制子文件夹失败: " + file.getAbsolutePath());
                    return false;
                }
            } else {
                // 复制文件
                if (!copyFile(file, new File(targetFolder, file.getName()))) {
                    Log.e(TAG, "复制文件失败: " + file.getAbsolutePath());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 剪切文件夹（不需要查重，直接移动）
     * 剪切操作保留原名称，因为只是位置移动，不会产生新的全局重名
     */
    public static boolean moveFolder(File sourceFolder, File targetParentDir) {
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            Log.e(TAG, "源文件夹无效");
            return false;
        }

        // 校验源目录和目标目录是否都在"外置大脑"根目录下
        if (!isUnderAppRoot(sourceFolder) || !isUnderAppRoot(targetParentDir)) {
            Log.e(TAG, "源目录或目标目录不在外置大脑根目录下");
            return false;
        }

        // 目标路径（使用原名称，不查重）
        File targetFolder = new File(targetParentDir, sourceFolder.getName());

        // 如果目标位置已存在同名文件夹，删除目标文件夹（或根据需求处理）
        if (targetFolder.exists()) {
            if (!deleteFolder(targetFolder)) {
                Log.e(TAG, "目标位置已存在同名文件夹且无法删除: " + targetFolder.getAbsolutePath());
                return false;
            }
        }

        // 执行移动操作
        if (sourceFolder.renameTo(targetFolder)) {
            Log.d(TAG, "剪切文件夹成功: " + sourceFolder.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
            return true;
        } else {
            // 移动失败时尝试复制后删除源文件（应对跨分区移动）
            Log.w(TAG, "直接移动失败，尝试复制后删除源文件");
            if (copyFolder(sourceFolder, targetParentDir)) {
                if (deleteFolder(sourceFolder)) {
                    Log.d(TAG, "通过复制删除方式完成剪切");
                    return true;
                } else {
                    Log.e(TAG, "复制成功但删除源文件夹失败");
                    return false;
                }
            } else {
                Log.e(TAG, "剪切文件夹失败");
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

        // 确保目标目录存在
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
     * 删除文件夹（递归删除所有内容）
     */
    private static boolean deleteFolder(File folder) {
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
     * 检查目录是否在"外置大脑"根目录下
     */
    private static boolean isUnderAppRoot(File dir) {
        if (dir == null) {
            return false;
        }
        File rootDir = getAppRootDirectory();
        try {
            String dirPath = dir.getCanonicalPath();
            String rootPath = rootDir.getCanonicalPath();
            return dirPath.startsWith(rootPath);
        } catch (IOException e) {
            Log.e(TAG, "检查目录是否在根目录下失败", e);
            return false;
        }
    }

    // 解压文件方法保持不变（如果需要使用）
    public static boolean unzipFile(File zipFile, File targetDir) {
        // 实际实现中使用ZipUnzipUtil的解压逻辑
        return ZipUnzipUtil.unzipToCurrentDir(zipFile.getAbsolutePath(), targetDir.getAbsolutePath());
    }
}

    