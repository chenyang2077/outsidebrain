package com.example.outsidebrainkxmt563980;

import android.os.Environment;
import android.util.Log;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.Date;
import java.util.regex.Pattern;

public class ZipUnzipUtil {
    private static final String TAG = "ZipUnzipUtil";
    private static final String ROOT_FOLDER_NAME = "主页根目录";
    private static final int SAMPLE_SIZE = 16 * 1024; // 采样前16KB

    // ===================== 新增乱码弹窗回调 =====================
    public interface EncodeWarningCallback {
        // 携带乱码txt文件路径列表
        void showGbkWarning(String zipPath, List<String> garbledTxtPaths);
    }
    static EncodeWarningCallback mWarningCallback;

    public static void setEncodeWarningCallback(EncodeWarningCallback callback) {
        mWarningCallback = callback;
    }

    /**
     * 递归扫描，收集所有疑似GBK乱码TXT绝对路径
     * @param rootDir 扫描根目录
     * @param outList 输出乱码文件列表
     */
    public static void collectAllGarbledTxtPath(File rootDir, List<String> outList) {
        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory()) {
            return;
        }
        File[] files = rootDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                collectAllGarbledTxtPath(file, outList);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".txt")) {
                    if (isTextFileGbkEncoding(file)) {
                        outList.add(file.getAbsolutePath());
                    }
                }
            }
        }
    }

    /**
     * 递归扫描指定文件夹，存在GBK编码TXT返回true
     */
    public static boolean scanAllTxtHasGarbledSymbol(File rootDir) {
        List<String> tempList = new ArrayList<>();
        collectAllGarbledTxtPath(rootDir, tempList);
        return !tempList.isEmpty();
    }

    /**
     * 核心新版编码检测：判断文本文件是否本质为GBK编码（UTF8查看乱码）
     * 原理：校验UTF-8合法多字节结构 + GBK编码字节区间统计，概率判定
     */
    private static boolean isTextFileGbkEncoding(File txtFile) {
        byte[] sample;
        try (FileInputStream fis = new FileInputStream(txtFile)) {
            byte[] buf = new byte[SAMPLE_SIZE];
            int read = fis.read(buf);
            if (read <= 0) return false;
            sample = new byte[read];
            System.arraycopy(buf, 0, sample, 0, read);
        } catch (Exception e) {
            Log.e(TAG, "读取文件采样失败", e);
            return false;
        }

        // 1. 先判断是否符合UTF-8合法字节规则
        boolean isLikelyUtf8 = isValidUtf8Bytes(sample);
        if (isLikelyUtf8) {
            return false;
        }

        // 2. UTF8结构不合法，再统计是否匹配GBK字节区间特征
        int gbkMatchCount = countGbkCodePoint(sample);
        float ratio = (float) gbkMatchCount / sample.length;
        // 匹配GBK字节占比超过阈值判定为GBK文件
        return ratio > 0.15f;
    }

    /**
     * 判断字节数组是否是合法 UTF-8 编码序列
     */
    private static boolean isValidUtf8Bytes(byte[] data) {
        int i = 0;
        int len = data.length;
        while (i < len) {
            int b = data[i] & 0xFF;
            if ((b & 0x80) == 0) {
                // 单字节ASCII
                i++;
            } else if ((b & 0xE0) == 0xC0) {
                // 2字节
                if (i + 1 >= len) return false;
                int b2 = data[i+1] & 0xFF;
                if ((b2 & 0xC0) != 0x80) return false;
                i += 2;
            } else if ((b & 0xF0) == 0xE0) {
                // 3字节（中文常用）
                if (i + 2 >= len) return false;
                int b2 = data[i+1] & 0xFF;
                int b3 = data[i+2] & 0xFF;
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) return false;
                i += 3;
            } else if ((b & 0xF8) == 0xF0) {
                // 4字节
                if (i + 3 >= len) return false;
                int b2 = data[i+1] & 0xFF;
                int b3 = data[i+2] & 0xFF;
                int b4 = data[i+3] & 0xFF;
                if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80 || (b4 & 0xC0) != 0x80) return false;
                i += 4;
            } else {
                // 非法UTF8起始字节
                return false;
            }
        }
        return true;
    }

    /**
     * 统计符合GBK编码区间的字节对数量
     * GBK首字节：0x81~0xFE，第二个字节：0x40~0x7E、0x80~0xFE
     */
    private static int countGbkCodePoint(byte[] data) {
        int count = 0;
        int len = data.length;
        for (int i = 0; i < len - 1; i++) {
            int b1 = data[i] & 0xFF;
            int b2 = data[i+1] & 0xFF;
            if (b1 >= 0x81 && b1 <= 0xFE) {
                if ((b2 >= 0x40 && b2 <= 0x7E) || (b2 >= 0x80 && b2 <= 0xFE)) {
                    count++;
                }
            }
        }
        return count;
    }

    // ============ 原有废弃乱码检测方法 ============
    /*
    private static boolean isFileRawGbkCode(File txtFile) { ... }
    private static boolean isTxtContainReplacementChar(File txtFile) { ... }
    */

    private static SimpleDateFormat getTsFormatter() {
        return new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
    }

    private static final Pattern INCREMENT_TIMESTAMP_PATTERN =
            Pattern.compile("_[A-Za-z0-9]{6}_\\d{13,17}(_\\d{13,17})*");
    private static final Pattern TARGET_TIMESTAMP_PATTERN =
            Pattern.compile("_[A-Za-z0-9]{6}_\\d{13,17}");
    private static final Pattern OLD_TIMESTAMP_PATTERN =
            Pattern.compile("_\\d{13,17}");
    private static final Set<String> IMAGE_SUFFIXES = new HashSet<>() {{
        add(".png");
        add(".jpg");
        add(".jpeg");
        add(".gif");
        add(".bmp");
    }};

    public static boolean unzipToCurrentDir(String zipFilePath, String targetDir) {
        File zipFile = new File(zipFilePath);
        if (!zipFile.exists()) {
            Log.e(TAG, "压缩文件不存在: " + zipFilePath);
            return false;
        }

        // 先一次性读取全部条目用于根目录分析
        List<ZipArchiveEntry> allEntries = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipArchiveInputStream zin = new ZipArchiveInputStream(fis, StandardCharsets.UTF_8.name())) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextZipEntry()) != null) {
                allEntries.add(entry);
            }
        } catch (Exception e) {
            Log.e(TAG, "读取压缩包条目失败", e);
            return false;
        }

        RootDirInfo rootDirInfo = analyzeRootDirectory(allEntries, zipFile.getName());
        String targetRootFolder = getNonConflictFolderName(targetDir, rootDirInfo.rootFolderName);
        File rootTargetDir = new File(targetDir, targetRootFolder);
        if (!rootTargetDir.exists()) {
            boolean mkdirOk = rootTargetDir.mkdirs();
            if (!mkdirOk) {
                Log.e(TAG, "创建根解压目录失败: " + rootTargetDir.getAbsolutePath());
                return false;
            }
        }
        String finalTargetPath = rootTargetDir.getAbsolutePath();

        Set<ZipArchiveEntry> dirEntries = new HashSet<>();
        List<ZipArchiveEntry> fileEntries = new ArrayList<>();
        for (ZipArchiveEntry e : allEntries) {
            if (e.isDirectory()) dirEntries.add(e);
            else fileEntries.add(e);
        }

        // 排序逻辑不变
        fileEntries.sort((o1, o2) -> {
            String n1 = o1.getName();
            String n2 = o2.getName();
            boolean h1 = hasTimestampInName(n1);
            boolean h2 = hasTimestampInName(n2);

            if (!h1 && h2) return -1;
            if (h1 && !h2) return 1;

            if (h1 && h2) {
                long t1 = getTimestampFromName(n1);
                long t2 = getTimestampFromName(n2);
                return Long.compare(t1, t2);
            }
            return Long.compare(o1.getTime(), o2.getTime());
        });

        // 第二次流遍历执行解压
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipArchiveInputStream zin = new ZipArchiveInputStream(fis, StandardCharsets.UTF_8.name())) {

            File rootDir = new File(Environment.getExternalStorageDirectory(), ROOT_FOLDER_NAME);
            Set<String> existingCleanNames = new HashSet<>();
            collectAllCleanFileNames(rootDir, existingCleanNames);
            int sequenceNumber = 0;

            ZipArchiveEntry entry;
            while ((entry = zin.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    processDirectoryEntry(entry, rootDirInfo, rootTargetDir);
                } else {
                    InputStream entryIs = zin;
                    sequenceNumber = processFileEntry(entryIs, entry, rootDirInfo, rootTargetDir, existingCleanNames, sequenceNumber);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "解压IO异常（损坏压缩包/权限不足）", e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "解压未知异常", e);
            return false;
        }

        Log.d(TAG, "解压成功，目标路径: " + finalTargetPath);

        // 解压完成扫描乱码文件，回调传入文件列表
        if (mWarningCallback != null) {
            List<String> garbledList = new ArrayList<>();
            collectAllGarbledTxtPath(rootTargetDir, garbledList);
            if (!garbledList.isEmpty()) {
                mWarningCallback.showGbkWarning(zipFilePath, garbledList);
            }
        }

        return true;
    }

    private static int processFileEntry(InputStream entryIn, ZipArchiveEntry entry,
                                        RootDirInfo rootDirInfo, File rootTargetDir,
                                        Set<String> existingCleanNames, int sequenceNumber) throws IOException {
        String entryName = entry.getName().replace("\\", "/");
        entryName = cleanZipEntryName(entryName);
        String relativePath;
        String rootName = rootDirInfo.rootFolderName;
        if (rootDirInfo.hasSingleRootFolder && entryName.length() >= rootName.length()) {
            relativePath = entryName.substring(rootName.length());
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
            parentDir.mkdirs();
        }

        try (OutputStream os = new FileOutputStream(targetFile)) {
            IOUtils.copy(entryIn, os);
        }

        String fileName = targetFile.getName().toLowerCase();
        if (fileName.endsWith(".txt") || isImageFile(fileName)) {
            return processNamedFile(targetFile, existingCleanNames, sequenceNumber);
        }
        return sequenceNumber;
    }

    private static int processNamedFile(File targetFile,
                                        Set<String> existingCleanNames, int sequenceNumber) {
        if (targetFile == null || !targetFile.exists()) {
            return sequenceNumber;
        }
        try {
            String originalName = targetFile.getName();
            String cleanName = removeTimestamp(originalName);
            String originalExt = getOriginalExtension(originalName);
            int lastDotIndex = cleanName.lastIndexOf(".");
            if (lastDotIndex > 0) {
                cleanName = cleanName.substring(0, lastDotIndex);
            }
            cleanName = getSafeCoreName(cleanName);
            String randomStr = generateRandomString();
            String baseTimestamp = getTsFormatter().format(new Date());
            String seqStr = String.format(Locale.getDefault(), "%05d", sequenceNumber++);
            String finalTs = baseTimestamp.substring(0, baseTimestamp.length() - 5) + seqStr;
            String finalName;
            String[] parts = originalName.split("_");
            if (parts.length <= 2) {
                finalName = cleanName + "_" + randomStr + "_" + finalTs + originalExt;
            } else if (parts.length == 3) {
                finalName = originalName.substring(0, originalName.lastIndexOf('.')) + "_" + finalTs + originalExt;
            } else {
                String nameWithoutExt = originalName.substring(0, originalName.lastIndexOf('.'));
                int lastUnder = nameWithoutExt.lastIndexOf('_');
                String prefix = nameWithoutExt.substring(0, lastUnder);
                finalName = prefix + "_" + finalTs + originalExt;
            }

            File newFile = new File(targetFile.getParentFile(), finalName);
            if (targetFile.renameTo(newFile)) {
                Log.d(TAG, "重命名成功: " + originalName + " → " + finalName);
            } else if (copyFileContent(targetFile, newFile)) {
                //noinspection ResultOfMethodCallIgnored
                targetFile.delete();
            }
            return sequenceNumber;
        } catch (Exception e) {
            Log.e(TAG, "处理文件失败", e);
            return sequenceNumber;
        }
    }

    private static boolean hasTimestampInName(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            fileName = fileName.substring(0, lastDot);
        }
        int lastUnder = fileName.lastIndexOf("_");
        if (lastUnder < 0 || lastUnder >= fileName.length() - 6) {
            return false;
        }
        String part = fileName.substring(lastUnder + 1);
        if (part.length() < 10) return false;
        for (int i = 0; i < part.length(); i++) {
            if (!Character.isDigit(part.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static long getTimestampFromName(String fileName) {
        try {
            int lastDot = fileName.lastIndexOf(".");
            if (lastDot > 0) {
                fileName = fileName.substring(0, lastDot);
            }
            int lastUnder = fileName.lastIndexOf("_");
            String numStr = fileName.substring(lastUnder + 1);
            return Long.parseLong(numStr);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void processDirectoryEntry(ZipArchiveEntry entry,
                                              RootDirInfo rootDirInfo, File rootTargetDir) {
        String entryName = entry.getName().replace("\\", "/");
        entryName = cleanZipEntryName(entryName);
        String relativePath;
        String rootName = rootDirInfo.rootFolderName;
        if (rootDirInfo.hasSingleRootFolder && entryName.length() >= rootName.length()) {
            relativePath = entryName.substring(rootName.length());
        } else {
            relativePath = entryName;
        }
        if (relativePath.isEmpty() || relativePath.equals("/")) {
            return;
        }
        File targetDir = new File(rootTargetDir, relativePath);
        if (!targetDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            targetDir.mkdirs();
        }
    }

    private static String cleanZipEntryName(String name) {
        if (name == null) return "";
        name = name.replaceAll("[\\\\:*?\"<>|]", "");
        name = name.replaceAll("[\\n\\r\\t]", "");
        String[] parts = name.split("/");
        StringBuilder cleanedPath = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim().replaceAll("^\\.+", "").replaceAll("\\.+$", "");
            if (part.isEmpty()) part = "未知文件";
            cleanedPath.append(part);
            if (i < parts.length - 1) cleanedPath.append("/");
        }
        return cleanedPath.length() > 0 ? cleanedPath.toString() : "未知文件";
    }

    private static boolean isImageFile(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String suffix : IMAGE_SUFFIXES) {
            if (lowerFileName.endsWith(suffix)) return true;
        }
        return false;
    }

    private static String generateRandomString() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(6);
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String removeTimestamp(String name) {
        if (name == null) return "";
        name = name.replaceAll("\\.[^.]+$", "");
        name = INCREMENT_TIMESTAMP_PATTERN.matcher(name).replaceAll("");
        name = TARGET_TIMESTAMP_PATTERN.matcher(name).replaceAll("");
        name = OLD_TIMESTAMP_PATTERN.matcher(name).replaceAll("");
        return name.trim().isEmpty() ? "未命名" : name.trim();
    }

    private static String getOriginalExtension(String fileName) {
        int lastDot = fileName.lastIndexOf(".");
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }

    private static String getSafeCoreName(String name) {
        if (name == null || name.trim().isEmpty()) return "未命名文件";
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "");
    }

    private static void collectAllCleanFileNames(File dir, Set<String> namesSet) {
        if (dir == null || !dir.isDirectory() || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                collectAllCleanFileNames(file, namesSet);
            } else {
                String fileName = file.getName().toLowerCase();
                if (fileName.endsWith(".txt") || isImageFile(fileName)) {
                    String clean = removeTimestamp(file.getName());
                    if (!clean.isEmpty()) namesSet.add(clean);
                }
            }
        }
    }

    private static boolean copyFileContent(File source, File dest) throws IOException {
        if (!dest.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dest.createNewFile();
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
        }
        return true;
    }

    private static RootDirInfo analyzeRootDirectory(List<ZipArchiveEntry> entries, String zipFileName) {
        Set<String> rootFolders = new HashSet<>();
        boolean hasRootFiles = false;
        for (ZipArchiveEntry entry : entries) {
            String entryName = entry.getName().replace("\\", "/");
            if (!entryName.contains("/") || entryName.startsWith("/")) {
                if (!entry.isDirectory()) hasRootFiles = true;
                continue;
            }
            int firstSlashIndex = entryName.indexOf('/');
            String rootFolder = entryName.substring(0, firstSlashIndex + 1);
            rootFolders.add(rootFolder);
        }
        if (!hasRootFiles && rootFolders.size() == 1) {
            String rootFolderName = rootFolders.iterator().next();
            return new RootDirInfo(true, rootFolderName.substring(0, rootFolderName.length() - 1));
        } else {
            int dotIndex = zipFileName.lastIndexOf('.');
            if (dotIndex > 0) zipFileName = zipFileName.substring(0, dotIndex);
            return new RootDirInfo(false, zipFileName);
        }
    }

    public static String getNonConflictFolderName(String targetDir, String originalName) {
        File targetFolder = new File(targetDir, originalName);
        if (!targetFolder.exists()) return originalName;
        int suffix = 1;
        while (true) {
            String newName = originalName + "（" + suffix + "）";
            if (!new File(targetDir, newName).exists()) return newName;
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

    public static String getUnzipRootFolderName(String zipFilePath, String targetDir) {
        File zipFile = new File(zipFilePath);
        List<ZipArchiveEntry> tempEntries = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipArchiveInputStream zin = new ZipArchiveInputStream(fis, StandardCharsets.UTF_8.name())) {
            ZipArchiveEntry entry;
            while ((entry = zin.getNextZipEntry()) != null) {
                tempEntries.add(entry);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return zipFile.getName().replace(".zip","");
        }
        RootDirInfo info = analyzeRootDirectory(tempEntries, zipFile.getName());
        return getNonConflictFolderName(targetDir, info.rootFolderName);
    }
}