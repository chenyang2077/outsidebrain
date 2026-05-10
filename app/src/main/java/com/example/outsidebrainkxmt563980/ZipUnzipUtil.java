/*
软件名称：红玉文档软件V1.0
版本号：V1.0
功能描述：实现ZIP压缩包智能解压、文件夹冲突自动规避、TXT/图片文件唯一命名、压缩包结构分析
所属模块：文件解压模块
开发语言：Java
*/
package com.example.outsidebrainkxmt563980;
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
 * 修复：解决解压出多余空文件夹（如文件夹（1））的问题
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

    // 定义需要处理的图片后缀
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
            File rootTargetDir = new File(targetDir, targetRootFolder);
            if (!rootTargetDir.exists() && !rootTargetDir.mkdirs()) {
                Log.e(TAG, "创建根解压目录失败: " + rootTargetDir.getAbsolutePath());
                return false;
            }
            String finalTargetPath = rootTargetDir.getAbsolutePath();
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
     * 处理压缩包目录项：仅创建原压缩包内的空文件夹，不生成带序号的冲突文件夹
     */
    private static void processDirectoryEntry(ZipFile zipFile, ZipEntry entry,
                                              RootDirInfo rootDirInfo, File rootTargetDir) throws IOException {
        String entryName = entry.getName().replace("\\", "/");
        entryName = cleanZipEntryName(entryName);
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
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            Log.w(TAG, "创建压缩包内空文件夹失败: " + targetDir.getAbsolutePath());
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
        entryName = cleanZipEntryName(entryName);
        String relativePath;
        if (rootDirInfo.hasSingleRootFolder) {
            relativePath = entryName.substring(rootDirInfo.rootFolderName.length());
        } else {
            relativePath = entryName;
        }
        File targetFile = new File(rootTargetDir, relativePath);
        try {
            String canonicalTarget = targetFile.getCanonicalPath();
            String canonicalRoot = rootTargetDir.getCanonicalPath();
            if (!canonicalTarget.startsWith(canonicalRoot + File.separator)) {
                Log.w(TAG, "发现危险文件，已跳过：" + entryName);
                return sequenceNumber;
            }
        } catch (Exception e) {
            Log.w(TAG, "安全校验失败，跳过文件：" + entryName);
            return sequenceNumber;
        }
        File parentDir = targetFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                Log.e(TAG, "创建文件父文件夹失败: " + parentDir.getAbsolutePath());
                return sequenceNumber;
            }
        }
        try (InputStream is = zipFile.getInputStream(entry);
             OutputStream os = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }
        String fileName = targetFile.getName().toLowerCase();
        if (fileName.endsWith(".txt") || isImageFile(fileName)) {
            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            return processNamedFile(targetFile, rootDir, existingCleanNames, sequenceNumber);
        }
        return sequenceNumber;
    }
    // 】解压专用：清理文件名/目录名非法字符
    // 只清理文件名非法字符，不破坏路径
    private static String cleanZipEntryName(String name) {
        if (name == null) return "";
        name = name.replaceAll("[\\\\:*?\"<>|]", "");
        name = name.replaceAll("[\\n\\r\\t]", "");
        String[] parts = name.split("/");
        StringBuilder cleanedPath = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            part = part.trim().replaceAll("^\\.+", "").replaceAll("\\.+$", "");
            if (part.isEmpty()) {
                part = "未知文件";
            }
            cleanedPath.append(part);
            if (i < parts.length - 1) {
                cleanedPath.append("/");
            }
        }
        return cleanedPath.length() > 0 ? cleanedPath.toString() : "未知文件";
    }
    /**
     * 统一处理TXT/图片文件重命名，生成唯一的带时间戳和随机字符的文件名
     * @return int 更新后的序列号
     */
    /**
     * 统一处理TXT/图片文件重命名：不清理旧时间戳，只追加/更新最后一个时间戳
     * @return int 更新后的序列号
     */
    private static int processNamedFile(File targetFile, File rootDir,
                                        Set<String> existingCleanNames, int sequenceNumber) {
        if (targetFile == null || !targetFile.exists()) {
            return sequenceNumber;
        }
        try {
            String originalName = targetFile.getName();
            int lastDot = originalName.lastIndexOf(".");
            String suffix = lastDot > 0 ? originalName.substring(lastDot) : "";
            String nameWithoutExt = lastDot > 0 ? originalName.substring(0, lastDot) : originalName;
            String newFileName;
            String newTs = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault()).format(new Date());
            Pattern hasRandomAndTs = Pattern.compile("^.+_[A-Za-z0-9]{6}(_\\d{13,17}){1,2}$");
            Pattern hasOneTs = Pattern.compile("^.+_[A-Za-z0-9]{6}_\\d{13,17}$");
            Pattern hasTwoTs = Pattern.compile("^.+_[A-Za-z0-9]{6}_\\d{13,17}_\\d{13,17}$");
            if (hasTwoTs.matcher(nameWithoutExt).matches()) {
                String base = nameWithoutExt.replaceAll("_\\d{13,17}$", "");
                newFileName = base + "_" + newTs + suffix;
            } else if (hasOneTs.matcher(nameWithoutExt).matches()) {
                newFileName = nameWithoutExt + "_" + newTs + suffix;
            } else if (hasRandomAndTs.matcher(nameWithoutExt).matches()) {
                newFileName = nameWithoutExt + "_" + newTs + suffix;
            } else {
                String cleanName = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt).replaceAll("");
                cleanName = TARGET_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");
                cleanName = OLD_TIMESTAMP_PATTERN.matcher(cleanName).replaceAll("");
                if (cleanName.trim().isEmpty()) {
                    cleanName = suffix.equalsIgnoreCase(".txt") ? "未命名文件" : "未命名图片";
                }
                String uniqueBase = findUniqueBaseName(cleanName, existingCleanNames);
                existingCleanNames.add(uniqueBase);
                String randomStr = generateRandomString();
                newFileName = uniqueBase + "_" + randomStr + "_" + newTs + suffix;
            }
            File newFile = new File(targetFile.getParentFile(), newFileName);
            if (targetFile.renameTo(newFile)) {
                Log.d(TAG, "重命名成功: " + originalName + " → " + newFileName);
            } else if (copyFileContent(targetFile, newFile)) {
                targetFile.delete();
                Log.d(TAG, "复制重命名成功: " + originalName + " → " + newFileName);
            }
            return (sequenceNumber + 1) % 10000;
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
     * 获取无冲突的文件夹名称，添加中文括号后缀规避冲突（仅根文件夹使用）
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