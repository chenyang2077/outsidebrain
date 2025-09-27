package com.example.outsidebrain;

import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    // 获取指定目录下的所有子文件/文件夹名称
    private static List<String> getAllChildNames(File parentDir) {
        List<String> names = new ArrayList<>();
        if (parentDir.exists() && parentDir.isDirectory()) {
            File[] children = parentDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    names.add(child.getName());
                }
            }
        }
        return names;
    }

    // 生成不重复的文件夹名称
    public static String generateUniqueFolderName(File parentDir, String baseName) {
        // 获取父目录下所有已有名称
        List<String> existingNames = getAllChildNames(parentDir);

        // 检查基础名称是否已存在
        if (!existingNames.contains(baseName)) {
            return baseName;
        }

        // 如果存在，添加序列号查找可用名称
        int counter = 1;
        while (true) {
            String newName = baseName + "(" + counter + ")";
            if (!existingNames.contains(newName)) {
                return newName;
            }
            counter++;
        }
    }

    // 新建文件夹（确保名称唯一）
    public static File createUniqueFolder(File parentDir, String baseName) {
        String uniqueName = generateUniqueFolderName(parentDir, baseName);
        File newFolder = new File(parentDir, uniqueName);
        if (newFolder.mkdirs()) {
            return newFolder;
        }
        return null;
    }

    // 复制文件夹（处理重名）
    public static boolean copyFolder(File sourceFolder, File targetParentDir) {
        if (!sourceFolder.isDirectory()) {
            return false;
        }

        // 生成目标文件夹的唯一名称
        String targetName = generateUniqueFolderName(targetParentDir, sourceFolder.getName());
        File targetFolder = new File(targetParentDir, targetName);

        // 创建目标文件夹
        if (!targetFolder.mkdirs()) {
            return false;
        }

        // 复制文件夹内容（这里仅为框架，需实现具体复制逻辑）
        File[] files = sourceFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 递归复制子文件夹
                    copyFolder(file, targetFolder);
                } else {
                    // 复制文件（需实现具体文件复制代码）
                }
            }
        }
        return true;
    }

    // 解压ZIP文件（处理文件夹重名）
    public static boolean unzipFile(File zipFile, File targetDir) {
        // 这里使用ZIP解压库（如ZipInputStream）
        // 伪代码逻辑：
        /*
        1. 打开ZIP文件
        2. 遍历ZIP中的每个条目
        3. 对于文件夹条目：
           a. 获取原始文件夹名
           b. 生成唯一名称：generateUniqueFolderName(targetDir, originalName)
           c. 创建新文件夹
        4. 对于文件条目：
           a. 确定目标路径（使用上面生成的唯一文件夹名）
           b. 解压到目标路径
        5. 关闭ZIP文件
        */
        return true; // 实际实现中返回真实结果
    }
}
    