/*
软件名称：快乐文字
版本号：V1.0
功能描述：实现ZIP压缩包智能解压、文件夹冲突自动规避、TXT/图片文件唯一命名、压缩包结构分析
所属模块：文件解压模块
开发语言：Java
*/
package com.example.outsidebrain;
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
    private static final SimpleDateFormat BASE_TIMESTAMP_FORMAT =
            new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
    private static final Pattern INCREMENT_TIMESTAMP_PATTERN =
            Pattern.compile("_[A-Za-z0-9]{6}_\\d{13,17}(_\\d{13,17})*");
    private static final Pattern TARGET_TIMESTAMP_PATTERN =
            Pattern.compile("_[A-Za-z0-9]{6}_\\d{13,17}");
    private static final Pattern OLD_TIMESTAMP_PATTERN =
            Pattern.compile("_\\d{13,17}");

    // 新增：定义需要处理的图片后缀（小写）
    private static final Set<String> IMAGE_SUFFIXES = new HashSet<String>() {{
        add(".png");
        add(".jpg");
        add(".jpeg");
        add(".gif");
        add(".bmp");
    }};

    /**
     * 核心解压方法：将ZIP文件解压到目标目录，自动处理文件夹冲突和TXT/图片文件重命名
     * @param zipFilePath ZIP压缩包完整路径
     * @param targetDir 解压目标根目录
     * @return boolean 解压是否成功
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

            for (ZipEntry entry : dirEntries) {
                processDirectoryEntry(zf, entry, rootDirInfo, rootTargetDir);
            }

            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            // 扩展：收集所有TXT和图片文件的清洁名称
            Set<String> existingCleanNames = new HashSet<>();
            collectAllCleanFileNames(rootDir, existingCleanNames);

            int sequenceNumber = 0;
            for (ZipEntry entry : fileEntries) {
                sequenceNumber = processFileEntry(zf, entry, rootDirInfo, rootTargetDir,
                        existingCleanNames, sequenceNumber);
            }

            Log.d(TAG, "解压成功，目标路径: " + finalTargetPath);
            return true;
        } catch (IOException e) {
            Log.e(TAG, "解压失败", e);
            return false;
        }
    }

    /**
     * 处理压缩包目录项，创建解压目录（规避文件夹名称冲突）
     */
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

    /**
     * 处理压缩包文件项，解压文件并对TXT/图片文件进行智能重命名
     * @return int 更新后的序列号
     */
    private static int processFileEntry(ZipFile zipFile, ZipEntry entry,
                                        RootDirInfo rootDirInfo, File rootTargetDir,
                                        Set<String> existingCleanNames, int sequenceNumber) throws IOException {
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

        // 扩展：判断是否是TXT或图片文件，统一处理重命名
        String fileName = targetFile.getName().toLowerCase();
        if (fileName.endsWith(".txt") || isImageFile(fileName)) {
            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            return processNamedFile(targetFile, rootDir, existingCleanNames, sequenceNumber);
        }
        return sequenceNumber;
    }

    /**
     * 统一处理TXT/图片文件重命名，生成唯一的带时间戳和随机字符的文件名
     * @return int 更新后的序列号
     */
    private static int processNamedFile(File targetFile, File rootDir,
                                        Set<String> existingCleanNames, int sequenceNumber) {
        if (targetFile == null || !targetFile.exists()) {
            return sequenceNumber;
        }
        try {
            String originalName = targetFile.getName();
            // 获取文件后缀（带点）
            String suffix = originalName.substring(originalName.lastIndexOf("."));
            String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf("."));

            // 清理旧时间戳（和原TXT逻辑一致）
            String cleanName = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt).replaceAll("");
            cleanName = TARGET_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");
            cleanName = OLD_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");

            if (cleanName.trim().isEmpty()) {
                // 区分空名称的默认值
                if (suffix.equalsIgnoreCase(".txt")) {
                    cleanName = "未命名文件";
                } else {
                    cleanName = "未命名图片";
                }
            }

            // 查找唯一基础名
            String uniqueBaseName = findUniqueBaseName(cleanName, existingCleanNames);
            existingCleanNames.add(uniqueBaseName);

            // 生成随机串和时间戳（和原TXT逻辑一致）
            String randomStr = generateRandomString();
            String datePart = BASE_TIMESTAMP_FORMAT.format(new Date());
            String sequenceStr = String.format("%04d", sequenceNumber % 10000);
            SimpleDateFormat timeSuffixFormat = new SimpleDateFormat("ssSSS", Locale.getDefault());
            String timeSuffix = timeSuffixFormat.format(new Date());
            String newTimestamp = datePart + sequenceStr + timeSuffix;

            // 拼接新文件名（兼容不同后缀）
            String newFileName = uniqueBaseName + "_" + randomStr + "_" + newTimestamp + suffix;
            File newFile = new File(targetFile.getParentFile(), newFileName);

            // 重命名/复制重命名（和原TXT逻辑一致）
            if (targetFile.renameTo(newFile)) {
                Log.d(TAG, "文件处理成功: " + originalName + " → " + newFileName);
                return (sequenceNumber + 1) % 10000;
            }
            if (copyFileContent(targetFile, newFile)) {
                targetFile.delete();
                Log.d(TAG, "文件复制并重命名成功: " + originalName + " → " + newFileName);
                return (sequenceNumber + 1) % 10000;
            } else {
                Log.w(TAG, "文件重命名失败: " + originalName);
                return sequenceNumber;
            }
        } catch (Exception e) {
            Log.e(TAG, "处理文件失败", e);
            return sequenceNumber;
        }
    }

    /**
     * 判断是否是需要处理的图片文件（通过后缀）
     */
    private static boolean isImageFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String suffix : IMAGE_SUFFIXES) {
            if (lowerFileName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成6位随机字符串（包含大小写字母和数字），用于文件命名唯一标识
     * @return String 6位随机字符串
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
     * 查找无冲突的基础文件名，添加数字后缀规避冲突
     * @return String 无冲突的基础名称
     */
    private static String findUniqueBaseName(String baseName, Set<String> existingNames) {
        if (!existingNames.contains(baseName)) {
            return baseName;
        }

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
     * 递归收集指定目录下所有TXT/图片文件的清洁名称（去时间戳）
     */
    private static void collectAllCleanFileNames(File dir, Set<String> namesSet) {
        if (dir == null || !dir.isDirectory() || !dir.exists()) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                collectAllCleanFileNames(file, namesSet);
            } else {
                String fileName = file.getName().toLowerCase();
                // 收集TXT和图片文件的清洁名称
                if (fileName.endsWith(".txt") || isImageFile(fileName)) {
                    String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));

                    String cleanName = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt).replaceAll("");
                    cleanName = TARGET_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");
                    cleanName = OLD_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");

                    if (!cleanName.trim().isEmpty()) {
                        namesSet.add(cleanName);
                    }
                }
            }
        }
    }

    /**
     * 复制文件内容，用于文件重命名失败时的兜底方案
     * @return boolean 复制是否成功
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

    /**
     * 分析压缩包根目录结构，判断是否有单一根文件夹
     * @return RootDirInfo 根目录分析结果
     */
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

    /**
     * 获取无冲突的文件夹名称，添加中文括号后缀规避冲突
     * @return String 无冲突的文件夹名称
     */
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

    /**
     * 内部数据类：存储压缩包根目录分析结果，用于传递根目录结构信息
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