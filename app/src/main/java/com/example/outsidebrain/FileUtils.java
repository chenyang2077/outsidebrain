/*
软件名称：流动信息文件管理系统
版本号：V1.0
功能描述：基于Android外部存储实现应用根目录下的文件/文件夹管理，提供唯一命名、创建/复制/移动、文件复制、解压等核心功能，保障数据安全和命名唯一
所属模块：文件操作模块
开发语言：Java
*/
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
/**
 * 文件操作工具类：基于Android外部存储实现应用根目录下的文件/文件夹管理，提供唯一命名、创建/复制/移动、文件复制、解压等核心功能
 */
public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final String APP_ROOT_FOLDER = "流动信息";
    /**
     * 获取应用根目录（外部存储下的"流动信息"文件夹）
     * @return File 应用根目录文件对象
     */
    public static File getAppRootDirectory() {
        return new File(Environment.getExternalStorageDirectory(), APP_ROOT_FOLDER);
    }
    /**
     * 递归收集应用根目录下所有文件夹名称（全局去重）
     */
    private static List<String> getAllGlobalFolderNames() {
        List<String> globalFolderNames = new ArrayList<>();
        File rootDir = getAppRootDirectory();
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
     * 生成全局唯一的文件夹名称（添加数字后缀规避冲突）
     * @return String 无冲突的文件夹名称
     */
    public static String generateUniqueFolderName(File parentDir, String baseName) {
        if (baseName == null || baseName.trim().isEmpty()) {
            baseName = "新建文件夹";
        }
        baseName = baseName.trim();
        List<String> globalExistingNames = getAllGlobalFolderNames();
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
     * 在应用根目录内创建全局唯一的文件夹
     * @return File 新建的文件夹对象，失败返回null
     */
    public static File createUniqueFolder(File parentDir, String baseName) {
        if (!isUnderAppRoot(parentDir)) {
            Log.e(TAG, "父目录不在流动信息根目录下");
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
     * 复制文件夹（含子文件/子文件夹）到目标目录，自动生成唯一名称
     * @return boolean 复制是否成功
     */
    public static boolean copyFolder(File sourceFolder, File targetParentDir) {
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            Log.e(TAG, "源文件夹无效");
            return false;
        }
        if (!isUnderAppRoot(targetParentDir)) {
            Log.e(TAG, "目标目录不在流动信息根目录下");
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
     * 移动文件夹到目标目录（优先重命名，失败则复制后删除源文件）
     * @return boolean 移动是否成功
     */
    public static boolean moveFolder(File sourceFolder, File targetParentDir) {
        if (sourceFolder == null || !sourceFolder.isDirectory()) {
            Log.e(TAG, "源文件夹无效");
            return false;
        }
        if (!isUnderAppRoot(sourceFolder) || !isUnderAppRoot(targetParentDir)) {
            Log.e(TAG, "源目录或目标目录不在流动信息根目录下");
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
            Log.d(TAG, "剪切文件夹成功: " + sourceFolder.getAbsolutePath() + " -> " + targetFolder.getAbsolutePath());
            return true;
        } else {
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
     * 复制单个文件到目标路径
     * @return boolean 复制是否成功
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
     * 递归删除文件夹（含所有子文件/子文件夹）
     * @return boolean 删除是否成功
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
     * 检查目录是否在应用根目录范围内
     * @return boolean 是否在根目录内
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
    /**
     * 解压ZIP文件到目标目录（调用ZipUnzipUtil实现）
     * @return boolean 解压是否成功
     */
    public static boolean unzipFile(File zipFile, File targetDir) {
        return ZipUnzipUtil.unzipToCurrentDir(zipFile.getAbsolutePath(), targetDir.getAbsolutePath());
    }
}