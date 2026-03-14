/*
软件名称：流动信息文件管理系统
版本号：V1.0
功能描述：实现文件管理器核心功能，支持文件/文件夹管理、TXT文件智能命名、ZIP压缩解压、文件分享、回收站、图片预览、搜索及状态恢复
所属模块：主界面模块
开发语言：Java
*/
package com.example.outsidebrain;
import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import java.util.Random;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import android.content.Context;
import android.view.ContextThemeWrapper;
import androidx.appcompat.app.AppCompatDelegate;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.content.Context;
import android.os.Environment;
import android.widget.Toast;
/**
 * 主界面：实现文件管理器核心功能，支持文件/文件夹管理、TXT文件智能命名、ZIP压缩解压、文件分享、回收站、图片预览、搜索及状态恢复
 */
public class MainActivity extends AppCompatActivity {
    private File copiedFile;
    private boolean isCutOperation;
    private static final int REQUEST_EDIT_FILE = 101;
    private RecyclerView fileRecyclerView;
    private FileAdapter fileAdapter;
    private List<File> fileList;
    private List<File> searchResultList;
    private File currentDirectory;
    private File rootDirectory;
    private EditText etSearch;
    private Button btnSearch;
    private boolean isInSearchMode = false;
    private FloatingActionButton preEditFileBtn;
    private CompressTask compressTask;
    private static final String ROOT_FOLDER_NAME = "流动信息";
    private File transferStationDirectory;
    private static final int REQUEST_TRANSFER_PERMISSION = 101;
    private boolean isInTransferStation = false;
    private File recycleBinDirectory;
    private boolean isInRecycleBin = false;
    public static final SimpleDateFormat MILLIS_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
    public static final Pattern FILE_MILLIS_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");
    public static final Pattern TARGET_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    public static final Pattern INCREMENT_TIMESTAMP_PATTERN = Pattern.compile("(_[A-Za-z0-9]{6}_\\d{17})(_\\d{17})+$");
    public static final Pattern OLD_TIMESTAMP_PATTERN = Pattern.compile("_(\\d{14}|\\d{17})$");
    private View pasteButton;
    private static final String[] IMAGE_EXTENSIONS = {
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"
    };
    /**
     * 页面创建初始化：
     * 1. 绑定布局控件，初始化视图组件；
     * 2. 设置夜间模式、文件列表适配器；
     * 3. 初始化回收站/中转站目录；
     * 4. 设置搜索框/菜单按钮/新增按钮点击事件；
     * 5. 检查存储权限，初始化根目录。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);
        fileRecyclerView = findViewById(R.id.file_list);
        preEditFileBtn = findViewById(R.id.add_button);
        ImageButton menuButton = findViewById(R.id.menu_btn);
        menuButton.setOnClickListener(v -> showPopupMenu(v));
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        fileList = new ArrayList<>();
        searchResultList = new ArrayList<>();
        fileAdapter = new FileAdapter();
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        fileRecyclerView.setAdapter(fileAdapter);
        checkPermission();
        clearSearchKeyword();
        isInSearchMode = false;
        initRecycleBin();
        transferStationDirectory = new File(Environment.getExternalStorageDirectory(), "中转站");
        if (!transferStationDirectory.exists()) {
            transferStationDirectory.mkdirs();
        }
        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    performSearch();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });
        btnSearch.setOnClickListener(v -> {
            hidePasteButton();
            performSearch();
        });
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(s)) {
                    updateLevelHint();
                }
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        etSearch.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hidePasteButton();
            }
            return false;
        });
        preEditFileBtn.setOnClickListener(v -> {
            hidePasteButton();
            startFilePreEdit();
        });
    }

    /**
     * 导航至根目录：
     * 1. 退出搜索模式，清空搜索框；
     * 2. 重置回收站/中转站标识；
     * 3. 加载根目录文件列表，更新层级提示；
     * 4. 保存页面状态，延迟修正TXT文件路径。
     */
    private void navigateToRootDirectory() {
        if (rootDirectory != null && rootDirectory.exists() && rootDirectory.isDirectory()) {
            if (isInSearchMode) {
                isInSearchMode = false;
                etSearch.setText("");
            }
            isInRecycleBin = false;
            isInTransferStation = false;
            currentDirectory = rootDirectory;
            loadFileList();
            updateLevelHint();
            PreferenceUtils.saveLastPageType(this, "main");
            PreferenceUtils.saveLastFolderPath(this, rootDirectory.getAbsolutePath());
            invalidateOptionsMenu();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                new Thread(() -> {
                    batchCorrectTxtFilepaths(rootDirectory);
                    runOnUiThread(() -> loadFileList());
                }).start();
            }, 1000);
        } else {
            Toast.makeText(this, "根目录不存在", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 初始化回收站：
     * 1. 创建应用内部存储的回收站目录；
     * 2. 打印创建结果日志，失败时提示权限问题。
     */
    private void initRecycleBin() {
        recycleBinDirectory = new File(getFilesDir(), "回收站");
        if (!recycleBinDirectory.exists()) {
            boolean created = recycleBinDirectory.mkdirs();
            if (created) {
                Log.d("RecycleBin", "回收站创建成功: " + recycleBinDirectory.getAbsolutePath());
            } else {
                Log.e("RecycleBin", "回收站创建失败");
                Toast.makeText(this, "无法创建回收站，请检查存储权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * TXT文件时间戳比较器：
     * 1. 优先按文件名中的17位时间戳降序排序；
     * 2. 无时间戳时按文件最后修改时间降序排序。
     */
    private class TxtTimestampComparator implements Comparator<File> {
        @Override
        public int compare(File f1, File f2) {
            long time1 = getMillisTimestampFromFileName(f1.getName());
            long time2 = getMillisTimestampFromFileName(f2.getName());
            if (time1 != 0 && time2 != 0) {
                return Long.compare(time2, time1);
            }
            return Long.compare(f2.lastModified(), f1.lastModified());
        }
    }

    /**
     * 从文件名提取毫秒时间戳：
     * 1. 仅处理TXT文件；
     * 2. 匹配FILE_MILLIS_TIMESTAMP_PATTERN正则，解析17位数字为时间戳；
     * 3. 解析失败返回0。
     *
     * @param fileName 文件名
     * @return 解析后的时间戳（毫秒），失败返回0
     */
    private long getMillisTimestampFromFileName(String fileName) {
        if (!fileName.endsWith(".txt")) return 0;
        Matcher matcher = FILE_MILLIS_TIMESTAMP_PATTERN.matcher(fileName);
        if (matcher.find()) {
            String timestampStr = matcher.group().replaceFirst("_", "");
            try {
                Date date = MILLIS_TIMESTAMP_FORMAT.parse(timestampStr);
                return date != null ? date.getTime() : 0;
            } catch (ParseException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 更新搜索框层级提示：
     * 1. 回收站/中转站目录显示对应提示；
     * 2. 普通目录显示层级路径（如Lv-1-2-3）。
     */
    private void updateLevelHint() {
        if (isInRecycleBin) {
            etSearch.setHint("回收站");
            return;
        }
        if (isInTransferStation) {
            etSearch.setHint("中转站");
            return;
        }
        if (currentDirectory == null) return;
        List<Integer> levelPath = getLevelPath(currentDirectory);
        StringBuilder levelStr = new StringBuilder();
        if (!levelPath.isEmpty()) {
            levelStr.append("Lv-");
            for (int i = 0; i < levelPath.size(); i++) {
                levelStr.append(levelPath.get(i));
                if (i < levelPath.size() - 1) {
                    levelStr.append("-");
                }
            }
        }
        etSearch.setHint(levelStr.toString());
    }

    /**
     * 文件夹排序（支持多级小数序号）：
     * 1. 提取文件夹名称开头的多级数字序号（如1.25.5.25）；
     * 2. 按层级逐位比较数字大小（1.25.5.25 < 1.25.22.12）；
     * 3. 无数字序号则按名称字母序排序。
     *
     * @param folders 待排序的文件夹列表
     */
    private void sortFoldersWithDecimalSupport(List<File> folders) {
        Collections.sort(folders, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                String name1 = file1.getName();
                String name2 = file2.getName();
                List<Long> numList1 = extractMultiLevelNumberFromName(name1);
                List<Long> numList2 = extractMultiLevelNumberFromName(name2);
                if (!numList1.isEmpty() && !numList2.isEmpty()) {
                    int minSize = Math.min(numList1.size(), numList2.size());
                    for (int i = 0; i < minSize; i++) {
                        long num1 = numList1.get(i);
                        long num2 = numList2.get(i);
                        if (num1 != num2) {
                            return Long.compare(num1, num2);
                        }
                    }
                    return Integer.compare(numList1.size(), numList2.size());
                } else if (!numList1.isEmpty()) {
                    return -1;
                } else if (!numList2.isEmpty()) {
                    return 1;
                }
                return name1.compareTo(name2);
            }

        });
    }
    /**
     * 提取文件名开头的多级数字序号（支持任意层级小数点）
     * @param fileName 文件名
     * @return 数字列表（如"1.25.5.25文件夹" → [1,25,5,25]，无数字则返回空列表）
     */
    private List<Long> extractMultiLevelNumberFromName(String fileName) {
        List<Long> numList = new ArrayList<>();
        if (fileName == null || fileName.isEmpty()) {
            return numList;
        }
        Pattern pattern = Pattern.compile("^([0-9]+(\\.[0-9]+)*)");
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            String numStr = matcher.group(1);
            String[] numParts = numStr.split("\\.");
            for (String part : numParts) {
                try {
                    numList.add(Long.parseLong(part));
                } catch (NumberFormatException e) {
                    break;
                }
            }
        }
        return numList;
    }

    /**
     * 压缩根文件夹：
     * 1. 弹出确认对话框，确认压缩操作；
     * 2. 生成无冲突的压缩包名称（主页压缩包.zip/主页压缩包(1).zip）；
     * 3. 执行异步压缩任务。
     */
    private void compressRootFolder() {
        new AlertDialog.Builder(this)
                .setTitle("确认压缩")
                .setMessage("确定要将整个主页内容压缩到当前目录吗？\n\n压缩后的文件将以“主页压缩包.zip”命名。")
                .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();

                        if (rootDirectory == null || !rootDirectory.exists() || !rootDirectory.isDirectory()) {
                            Toast.makeText(MainActivity.this, "根目录不存在，无法压缩", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (currentDirectory == null || !currentDirectory.exists() || !currentDirectory.isDirectory()) {
                            Toast.makeText(MainActivity.this, "当前目录不存在，无法保存压缩包", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String baseFileName = "主页压缩包";
                        String extension = ".zip";
                        File zipFile = new File(currentDirectory, baseFileName + extension);
                        int counter = 1;
                        while (zipFile.exists()) {
                            zipFile = new File(currentDirectory, baseFileName + " (" + counter + ")" + extension);
                            counter++;
                        }
                        compressTask = new CompressTask();
                        compressTask.execute(rootDirectory, zipFile);
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(true)
                .show();
    }
    /**
     * 异步压缩任务：
     * 1. 后台执行文件夹压缩，显示进度对话框；
     * 2. 递归处理文件夹/文件，生成ZIP压缩包；
     * 3. 压缩完成后更新UI，提示结果。
     */
    private class CompressTask extends AsyncTask<File, Integer, Boolean> {
        private File sourceDir;
        private File destZipFile;
        private String errorMessage;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            showProgressDialog("正在压缩...");
        }
        @Override
        protected Boolean doInBackground(File... params) {
            sourceDir = params[0];
            destZipFile = params[1];
            if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
                errorMessage = "源目录不存在或不是文件夹";
                return false;
            }
            if (!sourceDir.canRead()) {
                errorMessage = "没有读取源目录的权限";
                return false;
            }
            if (!destZipFile.getParentFile().canWrite()) {
                errorMessage = "没有写入压缩包的权限";
                return false;
            }
            ZipOutputStream zos = null;
            try {
                zos = new ZipOutputStream(new FileOutputStream(destZipFile));
                File[] allItems = sourceDir.listFiles();
                if (allItems == null) {
                    ZipEntry rootEntry = new ZipEntry(sourceDir.getName() + "/");
                    zos.putNextEntry(rootEntry);
                    zos.closeEntry();
                    return true;
                }

                for (File item : allItems) {
                    addToZip(item, sourceDir, zos);
                }
                return true;
            } catch (Exception e) {
                errorMessage = "压缩失败：" + e.getMessage();
                e.printStackTrace();
                return false;
            } finally {
                if (zos != null) {
                    try {
                        zos.flush();
                        zos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            updateProgressDialog(values[0]);
        }
        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            dismissProgressDialog();
            if (result) {
                Toast.makeText(MainActivity.this, "压缩成功：" + destZipFile.getName(), Toast.LENGTH_LONG).show();
                loadFileList();
            } else {
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                if (destZipFile.exists()) {
                    destZipFile.delete();
                }
            }
            compressTask = null;
        }
        private void addToZip(File file, File rootDir, ZipOutputStream zos) throws IOException {
            Log.d("CompressDebug", "处理条目：" + file.getAbsolutePath()
                    + " | 是否文件夹：" + file.isDirectory()
                    + " | 是否为空：" + (file.listFiles() == null || file.listFiles().length == 0));
            String relativePath = getRelativePath(rootDir, file);
            if (relativePath.isEmpty()) {
                relativePath = file.getName();
            }
            if (file.isDirectory()) {
                if (!relativePath.endsWith("/")) {
                    relativePath += "/";
                }
                ZipEntry dirEntry = new ZipEntry(relativePath);
                dirEntry.setSize(0);
                dirEntry.setTime(file.lastModified());
                zos.putNextEntry(dirEntry);
                zos.closeEntry();
                Log.d("CompressDebug", "创建文件夹条目：" + relativePath);
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        addToZip(child, rootDir, zos);
                    }
                }
            } else {
                ZipEntry fileEntry = new ZipEntry(relativePath);
                fileEntry.setTime(file.lastModified());
                zos.putNextEntry(fileEntry);

                FileInputStream fis = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    zos.write(buffer, 0, len);
                }

                fis.close();
                zos.closeEntry();
                publishProgress(0);
            }
        }
        private String getRelativePath(File rootDir, File file) throws IOException {
            String rootPath = rootDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.startsWith(rootPath)) {
                String rel = filePath.substring(rootPath.length());
                return rel.startsWith(File.separator) ? rel.substring(1) : rel;
            }
            return file.getName();
        }
    }

    // 进度对话框
    private ProgressDialog progressDialog;

    /**
     * 显示进度对话框：
     * 1. 创建水平进度条对话框，设置不可取消；
     * 2. 设置提示信息，初始化进度为0并显示。
     *
     * @param message 提示文本
     */
    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false); // 不可取消
        }
        progressDialog.setMessage(message);
        progressDialog.setProgress(0);
        progressDialog.show();
    }

    /**
     * 更新进度对话框：设置当前进度值
     *
     * @param progress 进度值（0-100）
     */
    private void updateProgressDialog(int progress) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.setProgress(progress);
        }
    }

    /**
     * 关闭进度对话框：隐藏并释放对话框资源
     */
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    /**
     * 页面销毁处理：
     * 1. 取消未完成的压缩任务，避免内存泄漏。
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (compressTask != null && !compressTask.isCancelled()) {
            compressTask.cancel(true);
        }
    }

    /**
     * 启动文件预编辑：
     * 1. 校验当前目录有效性；
     * 2. 跳转到FileEditorActivity，传递目录路径等参数。
     */
    private void startFilePreEdit() {
        if (currentDirectory == null) {
            Toast.makeText(this, "目录未初始化，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent preEditIntent = new Intent(MainActivity.this, FileEditorActivity.class);
        preEditIntent.putExtra("current_dir_path", currentDirectory.getAbsolutePath());
        preEditIntent.putExtra("is_pre_edit", true);
        preEditIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
        preEditIntent.putExtra("is_root_directory", currentDirectory.equals(rootDirectory));
        startActivityForResult(preEditIntent, REQUEST_EDIT_FILE);
    }

    /**
     * 显示弹出菜单：
     * 1. 创建主题化PopupMenu，加载菜单布局；
     * 2. 根据当前目录（回收站/中转站/普通目录）调整菜单项显示；
     * 3. 设置菜单项点击事件，处理主页/回收站/新建文件夹等操作。
     *
     * @param view 菜单按钮视图
     */
    private void showPopupMenu(View view) {
        try {
            ContextThemeWrapper themeWrapper = new ContextThemeWrapper(this, R.style.CustomPopupMenu);
            PopupMenu popupMenu = new PopupMenu(themeWrapper, view);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                popupMenu.setGravity(Gravity.TOP | Gravity.START);
            }
            MenuInflater inflater = popupMenu.getMenuInflater();
            inflater.inflate(R.menu.menu_popup, popupMenu.getMenu());
            if (isInRecycleBin) {
                popupMenu.getMenu().findItem(R.id.action_home).setTitle("返回主页");
                popupMenu.getMenu().findItem(R.id.action_recycle_bin).setVisible(false);
                popupMenu.getMenu().findItem(R.id.action_clear_recycle_bin).setVisible(true);
                popupMenu.getMenu().findItem(R.id.action_new_folder).setVisible(false);
                popupMenu.getMenu().findItem(R.id.action_transfer_station).setVisible(true);
                popupMenu.getMenu().findItem(R.id.yasuo).setVisible(false);
            } else if (isInTransferStation) {
                popupMenu.getMenu().findItem(R.id.action_home).setTitle("返回主页");
                popupMenu.getMenu().findItem(R.id.action_recycle_bin).setVisible(true);
                popupMenu.getMenu().findItem(R.id.action_transfer_station).setVisible(false);
                popupMenu.getMenu().findItem(R.id.action_clear_recycle_bin).setVisible(false);
                popupMenu.getMenu().findItem(R.id.action_new_folder).setVisible(true);
                popupMenu.getMenu().findItem(R.id.yasuo).setVisible(true); // 中转站显示压缩
            } else {
                popupMenu.getMenu().findItem(R.id.action_home).setTitle("返回主页");
                popupMenu.getMenu().findItem(R.id.action_recycle_bin).setVisible(true);
                popupMenu.getMenu().findItem(R.id.action_clear_recycle_bin).setVisible(false);
                popupMenu.getMenu().findItem(R.id.action_new_folder).setVisible(true);
                boolean isHomeOrHomeSubFolders = currentDirectory.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath());
                popupMenu.getMenu().findItem(R.id.action_transfer_station).setVisible(isHomeOrHomeSubFolders);
                popupMenu.getMenu().findItem(R.id.yasuo).setVisible(false);
            }
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_home) {
                    if (isInRecycleBin) {
                        exitRecycleBin();
                    } else {
                        navigateToRootDirectory();
                    }
                    return true;
                } else if (itemId == R.id.action_new_folder) {
                    showFolderCreateDialog();
                    return true;
                } else if (itemId == R.id.action_recycle_bin) {
                    openRecycleBin();
                    return true;
                } else if (itemId == R.id.action_clear_recycle_bin) {
                    confirmClearRecycleBin();
                    return true;
                } else if (itemId == R.id.action_transfer_station) {
                    if (checkTransferPermission()) {
                        openTransferStation();
                    }
                    return true;
                } else if (itemId == R.id.yasuo) {
                    compressRootFolder();
                    return true;
                }
                return false;
            });
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                popupMenu.setOnDismissListener(menu -> {
                });
            }
            popupMenu.show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "菜单加载失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查中转站权限：
     * 1. Android 11+ 检查MANAGE_EXTERNAL_STORAGE权限；
     * 2. 低版本检查读写外部存储权限；
     * 3. 校验中转站目录可写性。
     *
     * @return 权限是否已授予
     */
    private boolean checkTransferPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_TRANSFER_PERMISSION);
                return false;
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_TRANSFER_PERMISSION);
                return false;
            }
        }
        if (transferStationDirectory != null && !transferStationDirectory.canWrite()) {
            Toast.makeText(this, "中转站目录不可写", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * 打开中转站目录：
     * 1. 设置中转站标识，切换当前目录为中转站；
     * 2. 加载文件列表，更新层级提示。
     */
    private void openTransferStation() {
        isInTransferStation = true;
        isInRecycleBin = false;
        currentDirectory = transferStationDirectory;
        loadFileList();
        updateLevelHint();
    }

    /**
     * 确认清空回收站：
     * 1. 弹出确认对话框，提示永久删除风险；
     * 2. 确认后执行清空操作，提示结果。
     */
    private void confirmClearRecycleBin() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("确认清空回收站")
                .setMessage("此操作将永久删除回收站中所有文件，不可恢复，是否继续？")
                .setPositiveButton("清空", (dialog, which) -> {
                    if (clearRecycleBin()) {
                        Toast.makeText(this, "回收站已清空", Toast.LENGTH_SHORT).show();
                        loadFileList();
                    } else {
                        Toast.makeText(this, "清空失败，请重试", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 清空回收站：
     * 1. 递归删除回收站目录下所有文件/文件夹；
     * 2. 返回删除结果。
     *
     * @return 是否清空成功
     */
    private boolean clearRecycleBin() {
        if (recycleBinDirectory == null || !recycleBinDirectory.exists()) {
            return true;
        }

        File[] files = recycleBinDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!deleteFileOrDirectory(file)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 递归删除文件/文件夹：
     * 1. 处理文件夹：递归删除子项后删除自身；
     * 2. 处理文件：直接删除；
     * 3. 返回删除结果。
     *
     * @param file 待删除的文件/文件夹
     * @return 是否删除成功
     */
    private boolean deleteFileOrDirectory(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteFileOrDirectory(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    /**
     * 打开回收站目录：
     * 1. 校验回收站目录有效性；
     * 2. 退出搜索模式，切换当前目录为回收站；
     * 3. 加载文件列表，更新层级提示，保存页面状态。
     */
    private void openRecycleBin() {
        if (recycleBinDirectory == null || !recycleBinDirectory.exists()) {
            Toast.makeText(this, "回收站不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isInSearchMode) {
            isInSearchMode = false;
            etSearch.setText("");
        }
        isInRecycleBin = true;
        isInTransferStation = false;
        currentDirectory = recycleBinDirectory;
        loadFileList();
        updateLevelHint();
        PreferenceUtils.saveLastPageType(this, "recycle_bin");
        PreferenceUtils.saveLastFolderPath(this, recycleBinDirectory.getAbsolutePath());
    }

    /**
     * 退出回收站：
     * 1. 退出搜索模式，切换当前目录为根目录；
     * 2. 加载文件列表，更新层级提示，保存页面状态。
     */
    private void exitRecycleBin() {
        if (isInSearchMode) {
            isInSearchMode = false;
            etSearch.setText("");
        }
        if (rootDirectory == null || !rootDirectory.exists() || !rootDirectory.isDirectory()) {
            Toast.makeText(this, "根目录不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        isInRecycleBin = false;
        currentDirectory = rootDirectory;
        loadFileList();
        updateLevelHint();
        PreferenceUtils.saveLastPageType(this, "main");
        PreferenceUtils.saveLastFolderPath(this, rootDirectory.getAbsolutePath());
    }

    /**
     * 检查文件内容是否包含关键词：
     * 1. 跳过ZIP/图片/其他非文本文件；
     * 2. 读取文件内容，跳过首行路径标识后匹配关键词；
     * 3. 返回匹配结果。
     *
     * @param file    待检查文件
     * @param keyword 搜索关键词
     * @return 是否包含关键词
     */
    private boolean isContentContainKeyword(File file, String keyword) {
        if (file.getName().toLowerCase().endsWith(".zip") || isImageFile(file) || isOtherFile(file)) {
            return false;
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                String filteredLine = line;
                if (isFirstLine) {
                    filteredLine = FIRST_LINE_PATH_PATTERN.matcher(line).replaceAll("");
                    isFirstLine = false;
                }
                if (filteredLine.toLowerCase().contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 执行文件搜索：
     * 1. 获取搜索关键词，校验非空；
     * 2. 后台递归搜索当前目录下匹配的文件（名称/内容）；
     * 3. 排序搜索结果，更新UI显示。
     */
    private void performSearch() {
        String keyword = etSearch.getText().toString().trim();
        etSearch.clearFocus();

        if (TextUtils.isEmpty(keyword)) {
            isInSearchMode = false;
            fileAdapter.setData(fileList);
            clearSearchKeyword();
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show();
            return;
        }
        saveSearchKeyword(keyword);
        new Thread(() -> {
            isInSearchMode = true;
            searchResultList.clear();
            recursiveSearch(currentDirectory, keyword);
            sortSearchResult();
            runOnUiThread(() -> {
                fileAdapter.setData(searchResultList);
                Toast.makeText(MainActivity.this,
                        searchResultList.size() + " 个匹配结果",
                        Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    /**
     * 保存搜索关键词：存储到SharedPreferences，用于后续恢复
     *
     * @param keyword 搜索关键词
     */
    private void saveSearchKeyword(String keyword) {
        SharedPreferences sp = getSharedPreferences("SearchSP", Context.MODE_PRIVATE);
        sp.edit().putString("current_keyword", keyword).apply(); // 异步保存，不阻塞
    }

    /**
     * 清空搜索关键词：从SharedPreferences移除当前关键词
     */
    private void clearSearchKeyword() {
        SharedPreferences sp = getSharedPreferences("SearchSP", Context.MODE_PRIVATE);
        sp.edit().remove("current_keyword").apply();
    }

    /**
     * 递归搜索文件：
     * 1. 遍历当前目录下所有文件/文件夹；
     * 2. 文件夹：名称匹配则加入结果，递归搜索子目录；
     * 3. 文件：名称/内容匹配则加入结果。
     *
     * @param dir     搜索目录
     * @param keyword 搜索关键词
     */
    private void recursiveSearch(File dir, String keyword) {
        if (dir == null || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().toLowerCase().contains(keyword.toLowerCase())) {
                    searchResultList.add(file);
                }
                recursiveSearch(file, keyword);
            } else if (isSupportedFile(file)) {
                boolean nameMatch = getDisplayName(file).toLowerCase().contains(keyword.toLowerCase());
                boolean contentMatch = isContentContainKeyword(file, keyword);
                if (nameMatch || contentMatch) {
                    searchResultList.add(file);
                }
            }
        }
    }

    /**
     * 判断是否为图片文件：
     * 1. 检查文件扩展名是否在IMAGE_EXTENSIONS数组中；
     * 2. 返回判断结果。
     *
     * @param file 待判断文件
     * @return 是否为图片文件
     */
    private boolean isImageFile(File file) {
        if (file.isDirectory()) return false;

        String fileName = file.getName().toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为其他非文本/非压缩/非图片文件：
     * 1. 排除TXT/ZIP/图片文件，返回其他文件类型；
     *
     * @param file 待判断文件
     * @return 是否为其他文件
     */
    private boolean isOtherFile(File file) {
        if (file.isDirectory()) return false;
        String fileName = file.getName().toLowerCase();
        return !(fileName.endsWith(".txt") || fileName.endsWith(".zip") || isImageFile(file));
    }

    /**
     * 判断是否为支持的文件类型：
     * 1. 支持文件夹/TXT/ZIP/图片/其他文件；
     *
     * @param file 待判断文件
     * @return 是否支持
     */
    private boolean isSupportedFile(File file) {
        return file.isDirectory() ||
                file.getName().toLowerCase().endsWith(".txt") ||
                file.getName().toLowerCase().endsWith(".zip") ||
                isImageFile(file) ||
                isOtherFile(file);
    }

    /**
     * 排序搜索结果：
     * 1. 按类型分组（文件夹→ZIP→TXT→图片→其他）；
     * 2. 文件夹按名称排序，TXT/图片按时间戳排序，其余按修改时间排序。
     */
    private void sortSearchResult() {
        if (searchResultList.isEmpty()) return;

        List<File> folders = new ArrayList<>();
        List<File> zipFiles = new ArrayList<>();
        List<File> txtFiles = new ArrayList<>();
        List<File> imageFiles = new ArrayList<>();
        List<File> otherFiles = new ArrayList<>();

        for (File f : searchResultList) {
            if (f.isDirectory()) {
                folders.add(f);
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                zipFiles.add(f);
            } else if (f.getName().toLowerCase().endsWith(".txt")) {
                txtFiles.add(f);
            } else if (isImageFile(f)) {
                imageFiles.add(f);
            } else {
                otherFiles.add(f);
            }
        }

        // 1. 文件夹：按名称排序
        Collections.sort(folders, (file1, file2) -> file1.getName().compareTo(file2.getName()));
        // 2. TXT/图片：按时间戳排序（共用同一个比较器）
        Collections.sort(txtFiles, new TxtTimestampComparator());
        Collections.sort(imageFiles, new TxtTimestampComparator());
        // 3. ZIP/其他文件：按修改时间倒序
        Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        Collections.sort(otherFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

        // 重新组装排序后的列表
        searchResultList.clear();
        searchResultList.addAll(folders);
        searchResultList.addAll(zipFiles);
        searchResultList.addAll(txtFiles);
        searchResultList.addAll(imageFiles);
        searchResultList.addAll(otherFiles);
    }

    /**
     * 初始化外部存储（根目录）：
     * 1. 创建应用私有存储的根目录（流动信息）；
     * 2. 目录创建失败时使用兼容模式；
     * 3. 创建测试文件，加载文件列表，恢复上次状态。
     */
    private void initExternalBrain() {
        File privateStorageDir = getFilesDir();
        if (!privateStorageDir.exists()) {
            Toast.makeText(this, "存储不可用，无法初始化应用", Toast.LENGTH_SHORT).show();
            return;
        }
        rootDirectory = new File(privateStorageDir, ROOT_FOLDER_NAME);
        currentDirectory = rootDirectory;
        if (!currentDirectory.exists()) {
            boolean created = currentDirectory.mkdirs();
            if (!created) {
                File createdDir = FileUtils.createUniqueFolder(
                        privateStorageDir,
                        ROOT_FOLDER_NAME
                );
                if (createdDir != null) {
                    currentDirectory = createdDir;
                    rootDirectory = createdDir;
                    Toast.makeText(this, "在内部存储中创建根文件夹: " + createdDir.getName(), Toast.LENGTH_SHORT).show();
                    createTestFile();
                } else {
                    File fallbackDir = new File(getFilesDir(), ROOT_FOLDER_NAME);
                    if (fallbackDir.mkdirs()) {
                        currentDirectory = fallbackDir;
                        rootDirectory = fallbackDir;
                        Toast.makeText(this, "已使用兼容模式创建文件夹", Toast.LENGTH_SHORT).show();
                        createTestFile();
                    } else {
                        Toast.makeText(this, "无法创建根文件夹，请检查存储权限", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } else {
                Toast.makeText(this, "感谢世界有你", Toast.LENGTH_SHORT).show();
                createTestFile();
            }
        } else {
            File[] files = currentDirectory.listFiles();
            if (files == null || files.length == 0) {
                createTestFile();
            }
        }
        loadFileList();
        updateLevelHint();
        restoreLastState();
    }

    /**
     * 恢复上次页面状态：
     * 1. 从SharedPreferences读取上次页面类型/文件夹路径；
     * 2. 恢复回收站/图片/编辑页面状态，否则返回根目录。
     */
    private void restoreLastState() {
        String lastPageType = PreferenceUtils.getLastPageType(this);
        String lastFolderPath = PreferenceUtils.getLastFolderPath(this);
        if (lastFolderPath != null) {
            File lastFolder = new File(lastFolderPath);
            if (!lastFolder.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath())) {
                navigateToRootDirectory();
                return;
            }
        }
        if ("recycle_bin".equals(lastPageType)) {
            navigateToRootDirectory();
            return;
        }
        if ("image".equals(lastPageType)) {
            String lastImagePath = PreferenceUtils.getLastViewedImage(this);
            if (lastImagePath != null) {
                File imageFile = new File(lastImagePath);
                if (imageFile.exists() && isImageFile(imageFile) &&
                        imageFile.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath())) {
                    currentDirectory = imageFile.getParentFile();
                    loadFileList();
                    openImageFile(imageFile);
                    return;
                }
            }
        }
        if ("editor".equals(lastPageType)) {
            String lastEditedFile = PreferenceUtils.getLastEditedFile(this);
            if (lastEditedFile != null) {
                File file = new File(lastEditedFile);
                if (file.exists() && file.getName().toLowerCase().endsWith(".txt") &&
                        file.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath())) {
                    currentDirectory = file.getParentFile();
                    loadFileList();
                    openFileEditor(file);
                    return;
                }
            }
        }
        if (lastFolderPath != null) {
            File lastFolder = new File(lastFolderPath);
            if (lastFolder.exists() && lastFolder.isDirectory() &&
                    lastFolder.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath())) {
                currentDirectory = lastFolder;
                loadFileList();
                return;
            }
        }
        navigateToRootDirectory();
    }

    /**
     * 打开文件编辑器：
     * 1. 跳转到FileEditorActivity，传递文件路径等参数；
     * 2. 用于编辑已存在的TXT文件。
     *
     * @param file 待编辑的TXT文件
     */
    private void openFileEditor(File file) {
        Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
        editIntent.putExtra("file_path", file.getAbsolutePath());
        editIntent.putExtra("is_pre_edit", false);
        editIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
        editIntent.putExtra("is_root_directory", currentDirectory.equals(rootDirectory));
        startActivityForResult(editIntent, REQUEST_EDIT_FILE);
    }

    /**
     * 页面暂停处理：
     * 1. 保存当前页面类型/文件夹路径到SharedPreferences；
     * 2. 排除编辑/图片页面，仅保存主页面/回收站状态。
     */
    @Override
    protected void onPause() {
        super.onPause();
        String currentPageType = PreferenceUtils.getLastPageType(this);
        if (!"editor".equals(currentPageType) && !"image".equals(currentPageType)) {
            PreferenceUtils.saveLastPageType(this, isInRecycleBin ? "recycle_bin" : "main");
            if (!isInSearchMode && currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }
        }
    }

    /**
     * 活动结果处理：
     * 1. 处理文件编辑返回结果，刷新文件列表；
     * 2. 保存主页面状态。
     *
     * @param requestCode 请求码
     * @param resultCode  结果码
     * @param data        返回数据
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_EDIT_FILE) {
            PreferenceUtils.saveLastPageType(this, "main");
            loadFileList();
        }
    }

    /**
     * 加载文件列表：
     * 1. 按类型分组（文件夹→ZIP→TXT→图片→其他）；
     * 2. 文件夹按小数序号排序，TXT/图片按数字/时间戳排序；
     * 3. 更新适配器数据，刷新UI。
     */
    private void loadFileList() {
        fileList.clear();

        File[] files = currentDirectory.listFiles();
        if (files != null) {
            List<File> folders = new ArrayList<>();
            List<File> zipFiles = new ArrayList<>();
            List<File> txtFiles = new ArrayList<>();
            List<File> imageFiles = new ArrayList<>();
            List<File> otherFiles = new ArrayList<>();
            for (File file : files) {
                if (file.isDirectory()) {
                    folders.add(file);
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    zipFiles.add(file);
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    txtFiles.add(file);
                } else if (isImageFile(file)) {
                    imageFiles.add(file);
                } else {
                    otherFiles.add(file);
                }
            }
            sortFoldersWithDecimalSupport(folders);
            Comparator<File> txtImageComparator = new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    String name1 = file1.getName();
                    String name2 = file2.getName();
                    List<Long> numList1 = extractMultiLevelNumberFromName(name1);
                    List<Long> numList2 = extractMultiLevelNumberFromName(name2);
                    if (!numList1.isEmpty() && !numList2.isEmpty()) {
                        int minSize = Math.min(numList1.size(), numList2.size());
                        for (int i = 0; i < minSize; i++) {
                            long num1 = numList1.get(i);
                            long num2 = numList2.get(i);
                            if (num1 != num2) {
                                return Long.compare(num1, num2);
                            }
                        }
                        return Integer.compare(numList1.size(), numList2.size());
                    } else if (!numList1.isEmpty()) {
                        return -1;
                    } else if (!numList2.isEmpty()) {
                        return 1;
                    }
                    long time1 = extractTimestampIgnoreLast4(name1);
                    long time2 = extractTimestampIgnoreLast4(name2);
                    if (time1 != time2) {
                        return Long.compare(time2, time1);
                    }
                    return name1.compareTo(name2);
                }
                private long extractTimestampIgnoreLast4(String fileName) {
                    if (fileName == null || fileName.length() <= 4) {
                        return 0;
                    }
                    String nameWithoutExtension = fileName;
                    int lastDotIndex = fileName.lastIndexOf(".");
                    if (lastDotIndex > 0) {
                        nameWithoutExtension = fileName.substring(0, lastDotIndex);
                    }

                    Pattern pattern = Pattern.compile("(\\d{17})$");
                    Matcher matcher = pattern.matcher(nameWithoutExtension);
                    if (matcher.find()) {
                        try {
                            return Long.parseLong(matcher.group(1));
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }
                    return 0;
                }
            };
            Collections.sort(txtFiles, txtImageComparator);
            Collections.sort(imageFiles, txtImageComparator);
            Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Collections.sort(otherFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));

            fileList.addAll(folders);
            fileList.addAll(zipFiles);
            fileList.addAll(txtFiles);
            fileList.addAll(imageFiles);
            fileList.addAll(otherFiles);
        }
        if (!isInSearchMode) {
            fileAdapter.setData(fileList);
        }
        if (TextUtils.isEmpty(etSearch.getText().toString().trim())) {
            updateLevelHint();
        }
        if (copiedFile != null && currentDirectory.equals(copiedFile)) {

            hidePasteButton();
        }
    }

    /**
     * 获取目录层级路径：
     * 1. 从当前目录向上遍历至根目录，记录每个层级的序号；
     * 2. 反转层级列表，返回标准化层级路径（如[1,2,3]）。
     *
     * @param targetDir 目标目录
     * @return 层级序号列表
     */
    private List<Integer> getLevelPath(File targetDir) {
        List<Integer> levelPath = new ArrayList<>();
        File currentDir = targetDir;
        if (targetDir.equals(rootDirectory)) {
            return levelPath;
        }
        while (currentDir != null && !currentDir.equals(rootDirectory)) {
            File parentDir = currentDir.getParentFile();
            if (parentDir == null || !parentDir.exists()) break;
            File[] parentFiles = parentDir.listFiles();
            List<File> parentFolders = new ArrayList<>();
            if (parentFiles != null) {
                for (File f : parentFiles) {
                    if (f.isDirectory()) {
                        parentFolders.add(f);
                    }
                }
            }
            sortFoldersWithDecimalSupport(parentFolders);
            int serialNumber = 0;
            for (int i = 0; i < parentFolders.size(); i++) {
                if (parentFolders.get(i).getAbsolutePath().equals(currentDir.getAbsolutePath())) {
                    serialNumber = i + 1;
                    break;
                }
            }
            if (serialNumber > 0) {
                levelPath.add(serialNumber);
            }
            currentDir = parentDir;
        }
        Collections.reverse(levelPath);
        if (levelPath.isEmpty()) {
            levelPath.add(1);
        }
        return levelPath;
    }

    /**
     * 提取文件名开头的数字（支持小数）：
     * 1. 匹配文件名开头的数字（如1.2、3）；
     * 2. 返回解析后的Double值，失败返回null。
     *
     * @param fileName 文件名
     * @return 开头数字，无则返回null
     */
    private Double extractLeadingNumberFromName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile("^\\d+\\.?\\d*");
        Matcher matcher = pattern.matcher(fileName);

        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 格式化文件名用于显示：
     * 1. TXT文件去除所有时间戳后缀，显示核心名称；
     * 2. 其他文件直接返回原名称。
     *
     * @param originalFileName 原始文件名
     * @return 格式化后的显示名称
     */
    private String formatFileNameForDisplay(String originalFileName) {
        if (!originalFileName.toLowerCase().endsWith(".txt")) {
            return originalFileName;
        }
        String nameWithoutExt = originalFileName;
        if (originalFileName.contains(".")) {
            int extIndex = originalFileName.lastIndexOf(".");
            nameWithoutExt = originalFileName.substring(0, extIndex);
        }
        String processedName = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt).replaceAll("");
        processedName = TARGET_TIMESTAMP_PATTERN.matcher(processedName).replaceAll("");
        processedName = OLD_TIMESTAMP_PATTERN.matcher(processedName).replaceAll("");
        return processedName;
    }

    /**
     * 返回键处理：
     * 1. 中转站/回收站：返回上级目录或主页；
     * 2. 搜索模式：退出搜索，恢复文件列表；
     * 3. 普通目录：返回上级目录，根目录则退出应用。
     */
    @Override
    public void onBackPressed() {
        if (isInTransferStation) {
            if (currentDirectory != null && !currentDirectory.equals(transferStationDirectory)) {
                currentDirectory = currentDirectory.getParentFile();
                loadFileList();
                updateLevelHint();
            } else {
                exitTransferStationToHome();
            }
            return;
        }
        if (isInRecycleBin) {
            if (currentDirectory != null && !currentDirectory.equals(recycleBinDirectory)) {
                currentDirectory = currentDirectory.getParentFile();
                loadFileList();
                updateLevelHint();
            } else {
                exitRecycleBinToHome();
            }
            return;
        }
        if (isInSearchMode) {
            isInSearchMode = false;
            etSearch.setText("");
            etSearch.clearFocus();
            fileAdapter.setData(fileList);
            clearSearchKeyword();
            Toast.makeText(this, "已退出搜索", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            PreferenceUtils.saveLastPageType(this, "main");
            if (currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }
        } else if (currentDirectory != null && !currentDirectory.getName().equals(ROOT_FOLDER_NAME)) {
            currentDirectory = currentDirectory.getParentFile();
            etSearch.clearFocus();
            loadFileList();
            PreferenceUtils.saveLastPageType(this, "main");
            if (currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }
        } else {
            super.onBackPressed();
        }

    }

    /**
     * 退出中转站返回主页：
     * 1. 重置中转站标识，切换到根目录；
     * 2. 加载文件列表，更新层级提示，保存状态。
     */
    private void exitTransferStationToHome() {
        isInTransferStation = false;
        currentDirectory = rootDirectory;
        loadFileList();
        updateLevelHint();
        PreferenceUtils.saveLastPageType(this, "main");
        PreferenceUtils.saveLastFolderPath(this, rootDirectory.getAbsolutePath());
        Toast.makeText(this, "已返回主页", Toast.LENGTH_SHORT).show();
    }

    /**
     * 退出回收站返回主页：
     * 1. 重置回收站标识，切换到根目录；
     * 2. 加载文件列表，更新层级提示，保存状态。
     */
    private void exitRecycleBinToHome() {
        isInRecycleBin = false;
        currentDirectory = rootDirectory;
        loadFileList();
        updateLevelHint();
        PreferenceUtils.saveLastPageType(this, "main");
        PreferenceUtils.saveLastFolderPath(this, rootDirectory.getAbsolutePath());
        Toast.makeText(this, "已返回主页", Toast.LENGTH_SHORT).show();
    }

    /**
     * 准备选项菜单：
     * 1. 根据当前目录（回收站/中转站/普通目录）调整菜单项显示；
     * 2. 控制中转站/回收站/清空回收站/新建文件夹/压缩按钮的可见性。
     *
     * @param menu 菜单对象
     * @return 是否准备成功
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu != null) {
            boolean isHome = !isInRecycleBin && !isInTransferStation &&
                    currentDirectory.equals(rootDirectory);
            menu.findItem(R.id.action_transfer_station).setVisible(!isInTransferStation);
            menu.findItem(R.id.action_recycle_bin).setVisible(!isInRecycleBin);
            menu.findItem(R.id.action_clear_recycle_bin).setVisible(isInRecycleBin);
            menu.findItem(R.id.action_new_folder).setVisible(!isInRecycleBin);
            menu.findItem(R.id.yasuo).setVisible(isInTransferStation);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * 显示新建文件夹对话框：
     * 1. 输入文件夹名称，校验非空/已存在；
     * 2. 中转站创建需检查权限，创建成功后刷新文件列表。
     */
    private void showFolderCreateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建文件夹");
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setPositiveButton("创建", (dialog, which) -> {
            String folderName = input.getText().toString().trim();
            if (folderName.isEmpty()) {
                Toast.makeText(this, "文件夹名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            File newFolder = new File(currentDirectory, folderName);
            if (isInTransferStation) {
                isCreatingFolderInTransfer = true;
                if (!checkTransferPermission()) {
                    return;
                }
            }
            if (newFolder.exists()) {
                Toast.makeText(this, "文件夹已存在", Toast.LENGTH_SHORT).show();
                isCreatingFolderInTransfer = false; // 重置状态
                return;
            }
            if (newFolder.mkdirs()) {
                Toast.makeText(this, "文件夹创建成功", Toast.LENGTH_SHORT).show();
                loadFileList();
            } else {
                Toast.makeText(this, "创建失败，请检查权限", Toast.LENGTH_SHORT).show();
            }
            isCreatingFolderInTransfer = false; // 重置状态
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isCreatingFolderInTransfer = false; // 取消时重置状态
                dialog.dismiss();
            }
        });
        AlertDialog dialog = builder.show();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        input.postDelayed(() -> {
            input.requestFocus();
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    /**
     * 创建测试文件：
     * 1. 生成带随机字符/时间戳的使用说明TXT文件；
     * 2. 写入软件使用说明内容，保存到当前目录。
     */
    private void createTestFile() {
        String randomStr = generateRandomString();
        DateFormat MILLIS_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        String timestamp = "_" + MILLIS_TIMESTAMP_FORMAT.format(new Date());
        File currentDirectory = getFilesDir();
        File testFile = new File(currentDirectory, "使用说明与注意事项_" + randomStr + timestamp + ".txt");
        try {
            if (testFile.createNewFile()) {
                String content = getResources().getString(R.string.app_usage_instructions);
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(testFile),
                                StandardCharsets.UTF_8)
                )) {
                    writer.write(content);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "创建测试文件失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 获取文件显示名称：
     * 1. 文件夹：直接返回名称；
     * 2. TXT文件：去除时间戳后缀；
     * 3. ZIP/图片：去除扩展名；
     * 4. 其他文件：去除扩展名。
     *
     * @param file 目标文件
     * @return 显示名称
     */
    private String getDisplayName(File file) {
        String fileName = file.getName();
        if (file.isDirectory()) {
            return fileName;
        }
        if (fileName.endsWith(".txt")) {
            fileName = MainActivity.INCREMENT_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
            fileName = MainActivity.TARGET_TIMESTAMP_PATTERN.matcher(fileName).replaceAll("");
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1) {
                fileName = fileName.substring(0, dotIndex);
            }
            return fileName;
        }
        if (fileName.endsWith(".zip")) {
            return fileName.substring(0, fileName.lastIndexOf("."));
        }
        if (isImageFile(file)) {
            int dotIndex = fileName.lastIndexOf(".");
            if (dotIndex != -1) {
                return fileName.substring(0, dotIndex);
            }
            return fileName;
        }
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex != -1) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    /**
     * 获取目录相对路径（相对于根目录）：
     * 1. 从当前目录向上遍历至根目录，收集路径段；
     * 2. 反转路径段，拼接为/分隔的相对路径。
     *
     * @param dir      目标目录
     * @param rootName 根目录名称
     * @return 相对路径
     */
    public static String getRelativeDirPath(File dir, String rootName) {
        List<String> pathSegments = new ArrayList<>();
        File current = dir;
        while (current != null && !current.getName().equals(rootName)) {
            pathSegments.add(current.getName());
            current = current.getParentFile();
        }
        Collections.reverse(pathSegments);
        return String.join("/", pathSegments);
    }

    /**
     * 显示ZIP解压对话框：
     * 1. 确认解压操作，后台执行解压；
     * 2. 解压成功后批量修正TXT文件路径，刷新文件列表。
     *
     * @param zipFile 待解压的ZIP文件
     */
    private void showZipExtractDialog(File zipFile) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("解压文件")
                .setMessage("是否将「" + zipFile.getName() + "」解压到当前文件夹？")
                .setPositiveButton("确定", (dialog, which) -> {
                    new Thread(() -> {
                        boolean result = FileUtils.unzipFile(zipFile, currentDirectory);
                        runOnUiThread(() -> {
                            if (result) {
                                Toast.makeText(this, "解压成功，正在修正文件路径...", Toast.LENGTH_SHORT).show();
                                new Thread(() -> {
                                    batchCorrectTxtFilepaths(currentDirectory);
                                    runOnUiThread(this::loadFileList);
                                }).start();
                            } else {
                                Toast.makeText(this, "解压失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示文件夹操作选项弹窗（重命名、删除、压缩、复制、剪切）
     */
    private void showFolderOptions(File folder) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.FileOptionsDialogStyle);
        String folderName = folder.getName();
        SpannableString whiteTitle = new SpannableString(folderName);
        whiteTitle.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.white)),
                0,
                folderName.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
        );
        builder.setTitle(whiteTitle);
        String[] allOptions = {"重命名", "删除", "压缩为ZIP文件", "复制", "剪切"};
        int[] allIcons = new int[]{
                R.drawable.ic_rename,
                R.drawable.ic_delete,
                R.drawable.ic_image_error,
                R.drawable.ic_copy,
                R.drawable.ic_cut
        };
        List<String> optionsList = new ArrayList<>();
        List<Integer> iconsList = new ArrayList<>();
        for (int i = 0; i < allOptions.length; i++) {
            optionsList.add(allOptions[i]);
            iconsList.add(allIcons[i]);
        }
        String[] options = optionsList.toArray(new String[0]);
        int[] icons = new int[iconsList.size()];
        for (int i = 0; i < iconsList.size(); i++) {
            icons[i] = iconsList.get(i);
        }
        ListAdapter adapter = new ArrayAdapter<String>(this, R.layout.file_option_item, options) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.file_option_item, parent, false);
                }
                TextView textView = convertView.findViewById(R.id.option_text);
                ImageView imageView = convertView.findViewById(R.id.option_icon);
                int iconResId = icons[position];
                if (iconResId != 0) {
                    imageView.setImageResource(iconResId);
                } else {
                    imageView.setImageResource(R.drawable.ic_folder);
                }
                textView.setTextColor(ContextCompat.getColor(MainActivity.this, android.R.color.white));
                textView.setText(options[position]);
                return convertView;
            }
        };
        builder.setAdapter(adapter, (dialog, which) -> {
            switch (which) {
                case 0:
                    renameFile(folder);
                    break;
                case 1:
                    confirmFileDeletion(folder);
                    break;
                case 2:
                    zipFolder(folder);
                    break;
                case 3:
                    copyFileOrFolder(folder, false);
                    break;
                case 4:
                    copyFileOrFolder(folder, true);
                    break;
            }
        });
        builder.show();
    }

    /**
     * 显示文件删除确认弹窗，区分回收站（移至）和非回收站（永久删除）逻辑
     */
    private void confirmFileDeletion(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        if (isInRecycleBin) {
            builder.setTitle("确认彻底删除")
                    .setMessage("确定要永久删除 " + getDisplayName(file) + " 吗？此操作不可恢复。")
                    .setPositiveButton("删除", (dialog, which) -> {
                        if (performRecursiveDeletion(file)) {
                            Toast.makeText(this, "已永久删除", Toast.LENGTH_SHORT).show();
                            loadFileList();
                        } else {
                            Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                        }
                    });
        } else {
            builder.setTitle("确认删除")
                    .setMessage("确定要将 " + getDisplayName(file) + " 移至回收站吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        if (moveToRecycleBin(file)) {
                            Toast.makeText(this, "已移至回收站", Toast.LENGTH_SHORT).show();
                            loadFileList();
                        } else {
                            Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        builder.setNegativeButton("取消", null)
                .show();
    }

    /**
     * 将文件/文件夹移动到回收站目录，处理TXT文件和文件夹的特殊逻辑
     */
    private boolean moveToRecycleBin(File file) {
        if (file == null || !file.exists()) {
            return false;
        }

        try {
            if (!recycleBinDirectory.exists()) {
                recycleBinDirectory.mkdirs();
            }
            File initialTargetFile = new File(recycleBinDirectory, file.getName());
            File targetFile = getNonConflictFile(initialTargetFile);
            if (file.getName().toLowerCase().endsWith(".txt")) {
                return moveFileWithTimestampUpdate(file, targetFile);
            } else if (file.isDirectory()) {
                return moveFolderToRecycleBin(file, targetFile);
            } else {
                return file.renameTo(targetFile);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 递归移动文件夹到回收站，处理其中TXT文件的时间戳更新
     */
    private boolean moveFolderToRecycleBin(File sourceFolder, File targetFolder) throws IOException {
        if (!targetFolder.mkdirs()) {
            Log.e("MoveToRecycle", "创建目标文件夹失败: " + targetFolder.getAbsolutePath());
            return false;
        }
        File[] files = sourceFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    File subTargetFolder = new File(targetFolder, file.getName());
                    if (!moveFolderToRecycleBin(file, subTargetFolder)) {
                        return false;
                    }
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    String originalName = file.getName();
                    String newFileName = processTxtForCutOperation(originalName);
                    File targetFile = new File(targetFolder, newFileName);
                    if (!file.renameTo(targetFile)) {
                        if (copyFileContent(file, targetFile)) {
                            file.delete();
                        } else {
                            Log.e("MoveToRecycle", "处理TXT文件失败: " + originalName);
                            return false;
                        }
                    }
                } else {
                    File targetFile = new File(targetFolder, file.getName());
                    if (!file.renameTo(targetFile)) {
                        if (copyFileContent(file, targetFile)) {
                            file.delete();
                        } else {
                            Log.e("MoveToRecycle", "处理文件失败: " + file.getName());
                            return false;
                        }
                    }
                }
            }
        }
        return deleteEmptyDirectory(sourceFolder);
    }

    /**
     * 生成不重复的文件名，处理重名时添加序号后缀（如：文件(1).txt）
     */
    private String getUniqueFileName(File parentDir, String baseName) {
        if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory()) {
            return baseName;
        }
        File baseFile = new File(parentDir, baseName);
        if (!baseFile.exists()) {
            return baseName;
        }
        String nameWithoutExt = baseName;
        String extension = "";
        int dotIndex = baseName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = baseName.substring(0, dotIndex);
            extension = baseName.substring(dotIndex);
        }
        int maxSerial = 0;
        Pattern pattern = Pattern.compile("^" + Pattern.quote(nameWithoutExt) + "\\((\\d+)\\)" + Pattern.quote(extension) + "$");
        File[] files = parentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.matches()) {
                    try {
                        int serial = Integer.parseInt(matcher.group(1));
                        if (serial > maxSerial) {
                            maxSerial = serial;
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return nameWithoutExt + "(" + (maxSerial + 1) + ")" + extension;
    }

    /**
     * 递归删除文件/文件夹（包括所有子文件和子文件夹）
     */
    private boolean performRecursiveDeletion(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!performRecursiveDeletion(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    /**
     * 显示文件操作选项弹窗（重命名、删除、分享、复制、剪切），根据目录隐藏分享选项
     */
    private void showFileOptions(File file) {
        hidePasteButton();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.FileOptionsDialogStyle);
        String fileName = file.getName();
        SpannableString whiteTitle = new SpannableString(fileName);
        whiteTitle.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, android.R.color.white)),
                0,
                fileName.length(),
                Spannable.SPAN_INCLUSIVE_INCLUSIVE
        );
        builder.setTitle(whiteTitle);
        boolean isHomeOrHomeSubFolder = currentDirectory.getAbsolutePath().startsWith(rootDirectory.getAbsolutePath());
        boolean isRecycleOrRecycleSubFolder = currentDirectory.getAbsolutePath().startsWith(recycleBinDirectory.getAbsolutePath());
        boolean shouldHideShare = isHomeOrHomeSubFolder || isRecycleOrRecycleSubFolder;
        String[] allOptions = {"重命名", "删除", "分享", "复制", "剪切"};
        int[] allIcons = {
                R.drawable.ic_rename,
                R.drawable.ic_delete,
                R.drawable.ic_share,
                R.drawable.ic_copy,
                R.drawable.ic_cut
        };
        List<String> optionsList = new ArrayList<>();
        List<Integer> iconsList = new ArrayList<>();

        for (int i = 0; i < allOptions.length; i++) {
            if (i == 2 && shouldHideShare) {
                continue;
            }
            optionsList.add(allOptions[i]);
            iconsList.add(allIcons[i]);
        }
        String[] options = optionsList.toArray(new String[0]);
        int[] icons = new int[iconsList.size()];
        for (int i = 0; i < iconsList.size(); i++) {
            icons[i] = iconsList.get(i);
        }
        ListAdapter adapter = new ArrayAdapter<String>(this, R.layout.file_option_item, options) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.file_option_item, parent, false);
                }
                TextView textView = convertView.findViewById(R.id.option_text);
                ImageView imageView = convertView.findViewById(R.id.option_icon);
                int iconResId = icons[position];
                if (iconResId != 0) {
                    imageView.setImageResource(iconResId);
                } else {
                    imageView.setImageResource(R.drawable.ic_file);
                }
                textView.setTextColor(ContextCompat.getColor(MainActivity.this, android.R.color.white));
                textView.setText(options[position]);
                return convertView;
            }
        };
        builder.setAdapter(adapter, (dialog, which) -> {
            int originalWhich = shouldHideShare && which >= 2 ? which + 1 : which;
            switch (originalWhich) {
                case 0:
                    renameFile(file);
                    break;
                case 1:
                    confirmFileDeletion(file);
                    break;
                case 2:
                    shareFile(file);
                    break;
                case 3:
                    copyFileOrFolder(file, false);
                    break;
                case 4:
                    copyFileOrFolder(file, true);
                    break;
            }
        });
        builder.show();
    }

    /**
     * 跳转到FileEditorActivity执行文件夹压缩为ZIP的操作
     */
    private void zipFolder(File folder) {
        Intent intent = new Intent(this, FileEditorActivity.class);
        intent.putExtra("ACTION_ZIP_FOLDER", true);
        intent.putExtra("FOLDER_PATH", folder.getAbsolutePath());
        startActivityForResult(intent, REQUEST_EDIT_FILE);
    }

    /**
     * 跳转到FileEditorActivity执行文件分享操作
     */
    private void shareFile(File file) {
        Intent intent = new Intent(this, FileEditorActivity.class);
        intent.putExtra("ACTION_SHARE_FILE", true);
        intent.putExtra("FILE_PATH", file.getAbsolutePath());
        startActivityForResult(intent, REQUEST_EDIT_FILE);
    }

    /**
     * 显示文件/文件夹重命名弹窗，处理TXT文件的特殊命名规则（保留时间戳）
     */
    private void renameFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名");
        final EditText input = new EditText(this);
        input.requestFocus();
        final String originalFileName = file.getName();
        String displayName = originalFileName;
        final String fileExtension;
        final boolean isTxtFile;
        final boolean isFolder;
        final String originalTxtCoreName;
        isFolder = file.isDirectory();
        isTxtFile = !isFolder && originalFileName.toLowerCase().endsWith(".txt");
        originalTxtCoreName = isTxtFile ? extractCoreName(originalFileName) : "";
        if (!isTxtFile && !isFolder) {
            int dotIndex = originalFileName.lastIndexOf(".");
            if (dotIndex != -1 && dotIndex < originalFileName.length() - 1) {
                displayName = originalFileName.substring(0, dotIndex);
                fileExtension = originalFileName.substring(dotIndex);
            } else {
                fileExtension = "";
            }
        } else {
            fileExtension = "";
        }
        if (isTxtFile) {
            displayName = originalTxtCoreName;
        }
        input.setText(displayName);
        input.setSelection(0, displayName.length());
        builder.setView(input);

        builder.setPositiveButton("确认", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isTxtFile) {
                String newCoreName = newName.toLowerCase().endsWith(".txt")
                        ? newName.substring(0, newName.lastIndexOf("."))
                        : newName;
                if (newCoreName.equals(originalTxtCoreName)) {
                    Toast.makeText(this, "名称未更改", Toast.LENGTH_SHORT).show();
                    return;
                }
                String nameWithoutExt = originalFileName.substring(0, originalFileName.lastIndexOf("."));
                String randomStr = "";
                List<String> timestamps = new ArrayList<>();
                Matcher targetMatcher = TARGET_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
                Matcher incrementMatcher = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
                if (incrementMatcher.find()) {
                    String[] parts = incrementMatcher.group().split("_");
                    if (parts.length >= 3) {
                        randomStr = parts[1];
                        for (int i = 2; i < parts.length; i++) {
                            timestamps.add(parts[i]);
                        }
                    }
                } else if (targetMatcher.find()) {
                    String[] parts = targetMatcher.group().split("_");
                    if (parts.length >= 3) {
                        randomStr = parts[1];
                        timestamps.add(parts[2]);
                    }
                }
                String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
                StringBuilder timestampSuffix = new StringBuilder();
                if (timestamps.isEmpty()) {
                    randomStr = generateRandomString();
                    timestampSuffix.append("_").append(randomStr).append("_").append(newTimestamp);
                } else if (timestamps.size() == 1) {
                    timestampSuffix.append("_").append(randomStr)
                            .append("_").append(timestamps.get(0))
                            .append("_").append(newTimestamp);
                } else {
                    timestampSuffix.append("_").append(randomStr);
                    for (int i = 0; i < timestamps.size() - 1; i++) {
                        timestampSuffix.append("_").append(timestamps.get(i));
                    }
                    timestampSuffix.append("_").append(newTimestamp);
                }
                String uniqueName = UniqueFileNameHandler.getGlobalUniqueFileName(
                        rootDirectory,
                        file.getParentFile(),
                        newCoreName,
                        timestampSuffix.toString()
                );
                File newFile = new File(file.getParentFile(), uniqueName);
                if (newFile.exists()) {
                    Toast.makeText(this, "名称已存在", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (file.renameTo(newFile)) {
                    Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                final String finalNewName = (!isFolder && !fileExtension.isEmpty())
                        ? newName + fileExtension
                        : newName;
                if (finalNewName.equals(originalFileName)) {
                    Toast.makeText(this, "名称未更改", Toast.LENGTH_SHORT).show();
                    return;
                }
                String uniqueName = FileUtils.generateUniqueFolderName(
                        file.getParentFile(),
                        finalNewName
                );
                File newFile = new File(file.getParentFile(), uniqueName);
                if (file.renameTo(newFile)) {
                    Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        input.postDelayed(() -> imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT), 100);
    }

    /**
     * 提取TXT文件的核心名称（去除随机字符串和时间戳后缀）
     */
    private String extractCoreName(String fileName) {
        // 先去除文件扩展名
        String nameWithoutExt = fileName;
        if (fileName.toLowerCase().endsWith(".txt")) {
            nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        }
        Pattern pattern = Pattern.compile("_[A-Za-z0-9]{6}");
        Matcher matcher = pattern.matcher(nameWithoutExt);
        if (matcher.find()) {
            String coreName = nameWithoutExt.substring(0, matcher.start());
            return coreName.trim().isEmpty() ? "未命名文件" : coreName;
        } else {
            return nameWithoutExt.trim().isEmpty() ? "未命名文件" : nameWithoutExt;
        }
    }

    /**
     * 检查应用权限，初始化外部存储相关逻辑
     */
    private void checkPermission() {
        initExternalBrain();
    }

    /**
     * 处理存储权限请求结果，根据Android版本判断权限是否授予成功
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_TRANSFER_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    handleTransferPermissionGranted();
                } else {
                    Toast.makeText(this, "需要存储权限才能使用中转站", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    handleTransferPermissionGranted();
                } else {
                    Toast.makeText(this, "需要存储权限才能使用中转站", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * 处理存储权限授予成功后的逻辑，恢复之前的操作（创建文件夹/打开中转站）
     */
    private void handleTransferPermissionGranted() {
        if (isCreatingFolderInTransfer) {
            showFolderCreateDialog();
        } else {
            openTransferStation();
        }
        isCreatingFolderInTransfer = false;
    }

    private boolean isCreatingFolderInTransfer = false;

    /**
     * 分发触摸事件，点击搜索框外部时隐藏软键盘并清除焦点
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText && v.getId() == R.id.et_search) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 复制/剪切文件/文件夹，保存操作状态并显示粘贴按钮
     *
     * @param target 目标文件/文件夹
     * @param isCut  true=剪切，false=复制
     */
    private void copyFileOrFolder(File target, boolean isCut) {
        if (target == null || !target.exists()) {
            Toast.makeText(this, "文件不存在，无法操作", Toast.LENGTH_SHORT).show();
            return;
        }
        copiedFile = target;
        isCutOperation = isCut;
        showPasteButton();
        String tip = isCut ? "已剪切：" : "已复制：";
        Toast.makeText(this, tip + getDisplayName(target), Toast.LENGTH_SHORT).show();
    }

    /**
     * 显示粘贴按钮，动态添加到界面并设置点击事件
     */
    private void showPasteButton() {
        if (pasteButton != null && pasteButton.getParent() != null) {
            ((ViewGroup) pasteButton.getParent()).removeView(pasteButton);
        }
        pasteButton = LayoutInflater.from(this).inflate(R.layout.paste_button, null);
        Button btnPaste = pasteButton.findViewById(R.id.btn_paste);
        btnPaste.setOnClickListener(v -> performPaste());
        FrameLayout pasteContainer = findViewById(R.id.paste_container);
        pasteContainer.removeAllViews();
        pasteContainer.addView(pasteButton);
    }

    /**
     * 隐藏粘贴按钮并重置复制/剪切状态
     */
    private void hidePasteButton() {
        if (pasteButton != null && pasteButton.getParent() != null) {
            ((ViewGroup) pasteButton.getParent()).removeView(pasteButton);
        }
        copiedFile = null;
        isCutOperation = false;
        pasteButton = null;
    }

    /**
     * 生成不重复的文件夹名称，处理重名时添加序号后缀（如：文件夹(1)）
     */
    private String getUniqueFolderName(File parentDir, String baseName) {
        if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory()) {
            return baseName;
        }
        File baseFile = new File(parentDir, baseName);
        if (!baseFile.exists()) {
            return baseName;
        }
        int maxSerial = 0;
        Pattern pattern = Pattern.compile("^" + Pattern.quote(baseName) + "\\((\\d+)\\)$");
        File[] files = parentDir.listFiles(File::isDirectory);
        if (files != null) {
            for (File file : files) {
                Matcher matcher = pattern.matcher(file.getName());
                if (matcher.matches()) {
                    try {
                        int serial = Integer.parseInt(matcher.group(1));
                        if (serial > maxSerial) {
                            maxSerial = serial;
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return baseName + "(" + (maxSerial + 1) + ")";
    }

    /**
     * 复制文件夹并处理其中TXT文件的全局唯一命名规则，维护序号避免重复
     */
    private boolean copyFolderWithTxtGlobalCheck(File sourceFolder, File targetParent) throws IOException {
        String baseName = sourceFolder.getName();
        String uniqueFolderName = getUniqueFolderName(targetParent, baseName);
        File targetFolder = new File(targetParent, uniqueFolderName);
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            Log.e("CopyFolder", "创建目标文件夹失败: " + targetFolder.getAbsolutePath());
            return false;
        }
        int sequenceNumber = 0;
        File[] files = sourceFolder.listFiles();
        if (files == null) {
            return true;
        }
        for (File sourceFile : files) {
            if (sourceFile.isDirectory()) {
                sequenceNumber = copySubFolderWithTxtCheck(sourceFile, targetFolder, sequenceNumber);
            } else if (sourceFile.getName().toLowerCase().endsWith(".txt")) {
                String originalName = sourceFile.getName();
                String cleanName = UniqueFileNameHandler.removeTimestamp(originalName);
                if (cleanName.toLowerCase().endsWith(".txt")) {
                    cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
                }
                String randomStr = UniqueFileNameHandler.generateRandomString();
                String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
                if (baseTimestamp.length() >= 12) {
                    String datePart = baseTimestamp.substring(0, 8);
                    String timeRemaining = baseTimestamp.substring(12);
                    String sequenceStr = String.format(Locale.getDefault(), "%04d", sequenceNumber % 10000);
                    String newTimestamp = datePart + sequenceStr + timeRemaining;
                    String timestampSuffix = "_" + randomStr + "_" + newTimestamp;
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            timestampSuffix
                    );
                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (!copyFileContent(sourceFile, targetFile)) {
                        return false;
                    }
                    sequenceNumber++;
                } else {
                    Log.w("CopyFolder", "时间戳格式异常，使用默认命名");
                    String timestampSuffix = "_" + randomStr + "_" + baseTimestamp;
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            timestampSuffix
                    );
                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (!copyFileContent(sourceFile, targetFile)) {
                        return false;
                    }
                }
            } else {
                File targetFile = new File(targetFolder, sourceFile.getName());
                File uniqueTargetFile = getNonConflictFile(targetFile);
                if (!copyFileContent(sourceFile, uniqueTargetFile)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 递归复制子文件夹，处理其中TXT文件并维护序号
     */
    private int copySubFolderWithTxtCheck(File sourceFolder, File targetParent, int startSequence) throws IOException {
        int currentSequence = startSequence;
        String uniqueFolderName = getUniqueFolderName(targetParent, sourceFolder.getName());
        File targetFolder = new File(targetParent, uniqueFolderName);
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            Log.e("CopySubFolder", "创建子文件夹失败: " + targetFolder.getAbsolutePath());
            return currentSequence;
        }
        File[] files = sourceFolder.listFiles();
        if (files == null) {
            return currentSequence;
        }
        for (File sourceFile : files) {
            if (sourceFile.isDirectory()) {
                currentSequence = copySubFolderWithTxtCheck(sourceFile, targetFolder, currentSequence);
            } else if (sourceFile.getName().toLowerCase().endsWith(".txt")) {
                String originalName = sourceFile.getName();
                String cleanName = UniqueFileNameHandler.removeTimestamp(originalName);
                if (cleanName.toLowerCase().endsWith(".txt")) {
                    cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
                }
                String randomStr = UniqueFileNameHandler.generateRandomString();
                String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
                if (baseTimestamp.length() >= 12) {
                    String datePart = baseTimestamp.substring(0, 8);
                    String timeRemaining = baseTimestamp.substring(12);
                    String sequenceStr = String.format(Locale.getDefault(), "%04d", currentSequence % 10000);
                    String newTimestamp = datePart + sequenceStr + timeRemaining;
                    String timestampSuffix = "_" + randomStr + "_" + newTimestamp;
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            timestampSuffix
                    );
                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (copyFileContent(sourceFile, targetFile)) {
                        currentSequence++;
                    }
                } else {
                    Log.w("CopySubFolder", "时间戳格式异常，使用默认命名");
                    String timestampSuffix = "_" + randomStr + "_" + baseTimestamp;
                    String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                            rootDirectory,
                            targetFolder,
                            cleanName,
                            timestampSuffix
                    );
                    File targetFile = new File(targetFolder, uniqueFileName);
                    copyFileContent(sourceFile, targetFile);
                }
            } else {
                File targetFile = new File(targetFolder, sourceFile.getName());
                File uniqueTargetFile = getNonConflictFile(targetFile);
                copyFileContent(sourceFile, uniqueTargetFile);
            }
        }
        return currentSequence;
    }

    /**
     * 执行粘贴操作，在子线程处理文件/文件夹的复制/剪切，避免ANR
     */
    private void performPaste() {
        if (copiedFile == null || !copiedFile.exists()) {
            Toast.makeText(this, "粘贴内容已失效", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            return;
        }
        if (currentDirectory == null) {
            Toast.makeText(this, "当前目录不可用", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            return;
        }
        String originalParentPath = copiedFile.getParentFile().getAbsolutePath();
        String targetPath = currentDirectory.getAbsolutePath();
        if (isCutOperation && copiedFile.isDirectory() &&
                targetPath.startsWith(copiedFile.getAbsolutePath() + File.separator)) {
            Toast.makeText(this, "无法剪切到子目录，避免循环嵌套", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            return;
        }
        new Thread(() -> {
            boolean threadSuccess = false;
            try {
                if (copiedFile.isDirectory()) {
                    if (isCutOperation) {
                        threadSuccess = moveFolderWithTxtUpdate(copiedFile, currentDirectory);
                    } else {
                        threadSuccess = copyFolderWithTxtGlobalCheck(copiedFile, currentDirectory);
                    }
                } else {
                    String targetFileName = copiedFile.getName();
                    if (isCutOperation) {
                        File targetFile = new File(currentDirectory, targetFileName);
                        if (targetFile.exists()) {
                            targetFileName = getUniqueFileName(currentDirectory, targetFileName);
                        }
                    }
                    File targetFile = new File(currentDirectory, targetFileName);

                    if (isCutOperation) {
                        threadSuccess = moveFileWithTimestampUpdate(copiedFile, targetFile);
                    } else {
                        threadSuccess = copyFileWithUniqueName(copiedFile, targetFile);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            final boolean successResult = threadSuccess;
            runOnUiThread(() -> {
                if (successResult) {
                    String operation = isCutOperation ? "移动" : "复制";
                    Toast.makeText(this, operation + "成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "操作失败，请重试", Toast.LENGTH_SHORT).show();
                }
                hidePasteButton();
            });
        }).start();
    }

    /**
     * 移动文件夹并更新其中TXT文件的时间戳，处理重名和复制失败的降级逻辑
     */
    private boolean moveFolderWithTxtUpdate(File sourceFolder, File targetParent) throws IOException {
        String baseName = sourceFolder.getName();
        String uniqueFolderName = getUniqueFolderName(targetParent, baseName);
        File targetFolder = new File(targetParent, uniqueFolderName);
        if (!targetFolder.exists() && !targetFolder.mkdirs()) {
            Log.e("MoveFolder", "创建目标文件夹失败: " + targetFolder.getAbsolutePath());
            return false;
        }
        File[] files = sourceFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (!moveFolderWithTxtUpdate(file, targetFolder)) {
                        return false;
                    }
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    String originalName = file.getName();
                    String newFileName = processTxtForCutOperation(originalName);
                    File targetFile = new File(targetFolder, newFileName);
                    if (!file.renameTo(targetFile)) {
                        if (copyFileContent(file, targetFile)) {
                            file.delete();
                        } else {
                            Log.e("MoveFolder", "处理TXT文件失败: " + originalName);
                            return false;
                        }
                    }
                } else {
                    File targetFile = new File(targetFolder, file.getName());
                    if (!file.renameTo(targetFile)) {
                        if (copyFileContent(file, targetFile)) {
                            file.delete();
                        } else {
                            Log.e("MoveFolder", "处理文件失败: " + file.getName());
                            return false;
                        }
                    }
                }
            }
        }
        return deleteEmptyDirectory(sourceFolder);
    }

    /**
     * 删除空目录（仅当目录为空时执行删除）
     */
    private boolean deleteEmptyDirectory(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return false;
        }
        File[] files = dir.listFiles();
        if (files != null && files.length > 0) {
            return false;
        }

        return dir.delete();
    }

    /**
     * 处理剪切操作中的TXT文件命名，更新时间戳后缀
     */
    private String processTxtForCutOperation(String fileName) {
        if (!fileName.toLowerCase().endsWith(".txt")) {
            return fileName;
        }
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf("."));
        String ext = fileName.substring(fileName.lastIndexOf("."));
        String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
        Matcher targetMatcher = TARGET_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (!targetMatcher.find()) {
            String randomStr = UniqueFileNameHandler.generateRandomString();
            return nameWithoutExt + "_" + randomStr + "_" + newTimestamp + ext;
        }
        Matcher incrementMatcher = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (incrementMatcher.find()) {
            return incrementMatcher.replaceAll("$1_" + newTimestamp) + ext;
        }
        return nameWithoutExt + "_" + newTimestamp + ext;
    }

    /**
     * 生成6位随机字符串（字母+数字组合）
     */
    private String generateRandomString() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int index = new Random().nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 复制文件内容，使用缓冲区提高读写效率
     */
    private boolean copyFileContent(File source, File target) throws IOException {
        try (InputStream in = new BufferedInputStream(new FileInputStream(source));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            byte[] buffer = new byte[1024 * 4];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        }
    }

    /**
     * 获取无冲突的文件路径，重名时添加序号后缀
     */
    private File getNonConflictFile(File targetFile) {
        if (!targetFile.exists()) {
            return targetFile;
        }
        String baseName = targetFile.getName();
        String extension = "";
        int dotIndex = baseName.lastIndexOf(".");
        if (dotIndex != -1) {
            baseName = baseName.substring(0, dotIndex);
            extension = targetFile.getName().substring(dotIndex);
        }
        int counter = 1;
        while (true) {
            String newName = baseName + "(" + counter + ")" + extension;
            File newFile = new File(targetFile.getParentFile(), newName);
            if (!newFile.exists()) {
                return newFile;
            }
            counter++;
        }
    }

    /**
     * 打开图片文件（使用自建查看器，支持上下滑动切换）
     */
    private void openImageFile(File imageFile) {
        try {
            // 原有记录保存逻辑
            PreferenceUtils.saveLastPageType(this, "image");
            PreferenceUtils.saveLastViewedImage(this, imageFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, imageFile.getParentFile().getAbsolutePath());

            // 跳转到自建图片查看器
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra("IMAGE_PATH", imageFile.getAbsolutePath());
            intent.putExtra("FOLDER_PATH", imageFile.getParentFile().getAbsolutePath());
            startActivity(intent);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "打开图片失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 显示不支持的文件类型提示
     */
    private void showUnsupportedFileMessage() {
        Toast.makeText(this, "暂不支持此文件类型", Toast.LENGTH_SHORT).show();
    }

    /**
     * 移动文件并更新TXT文件的时间戳，重命名失败时降级为复制+删除
     */
    private boolean moveFileWithTimestampUpdate(File source, File target) throws IOException {
        if (!source.exists()) return false;
        if (source.getName().toLowerCase().endsWith(".txt")) {
            String originalName = source.getName();
            String newFileName = processTxtForCutOperation(originalName);
            File finalTarget = new File(target.getParentFile(), newFileName);
            if (source.renameTo(finalTarget)) {
                return true;
            } else {
                if (copyFileContent(source, finalTarget)) {
                    return source.delete();
                }
                return false;
            }
        }
        return source.renameTo(target);
    }

    /**
     * 复制文件并生成唯一名称，处理TXT文件的特殊命名规则
     */
    private boolean copyFileWithUniqueName(File source, File target) throws IOException {
        // 检查源文件是否存在
        if (!source.exists()) {
            Log.e("FileCopy", "源文件不存在: " + source.getAbsolutePath());
            return false;
        }
        File parentDir = target.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            Log.e("FileCopy", "无法创建目标目录: " + parentDir.getAbsolutePath());
            return false;
        }
        File finalTargetFile = target;
        String sourceFileName = source.getName();
        if (sourceFileName.toLowerCase().endsWith(".txt")) {
            String processedFileName = UniqueFileNameHandler.TimestampHandler.processTxtFileName(sourceFileName);
            String coreTitle = processedFileName;
            if (coreTitle.toLowerCase().endsWith(".txt")) {
                coreTitle = coreTitle.substring(0, coreTitle.lastIndexOf("."));
            }
            coreTitle = MainActivity.INCREMENT_TIMESTAMP_PATTERN.matcher(coreTitle).replaceAll("");
            coreTitle = MainActivity.TARGET_TIMESTAMP_PATTERN.matcher(coreTitle).replaceAll("");
            String randomStr = UniqueFileNameHandler.TimestampHandler.generateRandomString();
            String newTimestamp = UniqueFileNameHandler.TimestampHandler.generateMillisTimestamp();
            String timestampSuffix = "_" + randomStr + "_" + newTimestamp;
            String uniqueFileName = UniqueFileNameHandler.getGlobalUniqueFileName(
                    rootDirectory,
                    parentDir,
                    coreTitle,
                    timestampSuffix
            );
            finalTargetFile = new File(parentDir, uniqueFileName);
            Log.d("FileCopy", "TXT文件复制 - 原名称: " + sourceFileName + " → 新名称: " + uniqueFileName);
        } else {
            finalTargetFile = getNonConflictFile(target);
            Log.d("FileCopy", "非TXT文件复制 - 原名称: " + sourceFileName + " → 新名称: " + finalTargetFile.getName());
        }
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(finalTargetFile))) {
            byte[] buffer = new byte[1024 * 4];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            Log.e("FileCopy", "复制文件失败: " + e.getMessage(), e);
            // 复制失败时删除可能创建的空文件
            if (finalTargetFile.exists() && finalTargetFile.length() == 0) {
                finalTargetFile.delete();
            }
            throw e;
        }
    }

    /**
     * 递归升级目录下所有TXT文件的命名格式（更新时间戳规则）
     */
    private void recursiveUpgradeTxtFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                recursiveUpgradeTxtFiles(file);
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                String oldName = file.getName();
                String newName = UniqueFileNameHandler.TimestampHandler.processTxtFileName(oldName);
                if (!oldName.equals(newName)) {
                    File newFile = new File(file.getParentFile(), newName);
                    if (file.renameTo(newFile)) {
                        Log.d("MainActivity", "升级文件：" + oldName + "→" + newName);
                    } else {
                        Log.w("MainActivity", "升级失败：" + oldName);
                    }
                }
            }
        }
    }

    /**
     * 提取TXT文件第一行中的路径信息（格式：【路径】）
     */
    private String extractFirstLinePath(File file) {
        if (!file.getName().toLowerCase().endsWith(".txt")) return null;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String firstLine = br.readLine();
            if (firstLine == null) return null;

            Matcher matcher = FIRST_LINE_PATH_PATTERN.matcher(firstLine);
            if (matcher.matches()) {
                return matcher.group().replace("【", "").replace("】", "").trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 修正TXT文件第一行的路径信息，根据文件所在目录更新路径
     */
    private boolean correctFilepathInTxt(File file) {
        if (!file.getName().toLowerCase().endsWith(".txt")) return false;
        File parentDir = file.getParentFile();
        if (parentDir == null) {
            return false;
        }
        boolean isInRootDir = parentDir.equals(rootDirectory);
        boolean isInTransferStationRoot = parentDir.equals(transferStationDirectory);
        boolean isInRecycleBinRoot = parentDir.equals(recycleBinDirectory);
        boolean isInNoPathRequiredDir = isInRootDir || isInTransferStationRoot || isInRecycleBinRoot;
        boolean isInTransferStationSubDir = false;
        boolean isInRecycleBinSubDir = false;
        String actualPath = "";
        try {
            String parentPath = parentDir.getCanonicalPath() + File.separator;
            String transferPath = transferStationDirectory.getCanonicalPath() + File.separator;
            isInTransferStationSubDir = parentPath.startsWith(transferPath) && !isInTransferStationRoot;
            String recyclePath = recycleBinDirectory.getCanonicalPath() + File.separator;
            isInRecycleBinSubDir = parentPath.startsWith(recyclePath) && !isInRecycleBinRoot;
            if (isInTransferStationSubDir) {
                actualPath = parentPath.substring(transferPath.length())
                        .replace(File.separator, "/")
                        .replaceAll("/$", "");
            } else if (isInRecycleBinSubDir) {
                actualPath = parentPath.substring(recyclePath.length())
                        .replace(File.separator, "/")
                        .replaceAll("/$", "");
            } else if (!isInNoPathRequiredDir) {
                String rootPath = rootDirectory.getCanonicalPath() + File.separator;
                actualPath = parentPath.substring(rootPath.length())
                        .replace(File.separator, "/")
                        .replaceAll("/$", "");
            }
        } catch (IOException e) {
            e.printStackTrace();
            actualPath = parentDir.getName();
        }
        List<String> allLines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                allLines.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        StringBuilder newContent = new StringBuilder();
        boolean pathProcessed = false;
        for (int i = 0; i < allLines.size(); i++) {
            String line = allLines.get(i);
            if (i == 0 && !isInNoPathRequiredDir) {
                if (FIRST_LINE_PATH_PATTERN.matcher(line).matches()) {
                    newContent.append("【").append(actualPath).append("】\n");
                } else {
                    newContent.append("【").append(actualPath).append("】\n").append(line).append("\n");
                }
                pathProcessed = true;
            } else if (i == 0 && isInNoPathRequiredDir) {
                String processedLine = FIRST_LINE_PATH_PATTERN.matcher(line).replaceAll("");
                newContent.append(processedLine).append("\n");
                pathProcessed = true;
            } else {
                newContent.append(line).append("\n");
            }
        }
        if (!isInNoPathRequiredDir && allLines.isEmpty()) {
            newContent.append("【").append(actualPath).append("】\n");
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            String finalContent = newContent.toString().endsWith("\n")
                    ? newContent.toString().substring(0, newContent.length() - 1)
                    : newContent.toString();
            fos.write(finalContent.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 批量修正目录下所有TXT文件的路径信息
     */
    private void batchCorrectTxtFilepaths(File folder) {
        if (!folder.isDirectory()) return;

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                batchCorrectTxtFilepaths(file);
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                correctFilepathInTxt(file);
            }
        }
    }

    /**
     * 使用Glide加载图片缩略图，优化加载性能
     */
    private void loadImageThumbnail(File imageFile, ImageView imageView) {
        // 使用Glide库加载缩略图
        Glide.with(MainActivity.this)
                .load(imageFile)
                .thumbnail(0.1f)
                .centerCrop()
                .error(R.drawable.ic_image)
                .into(imageView);
    }

    /**
     * Activity恢复时更新目录状态，刷新菜单
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (currentDirectory.equals(transferStationDirectory) && !isInTransferStation) {
            isInTransferStation = true;
        } else if (currentDirectory.equals(rootDirectory) && (isInTransferStation || isInRecycleBin)) {
            isInTransferStation = false;
            isInRecycleBin = false;
        }
        invalidateOptionsMenu();
    }

    /**
     * 文件列表RecyclerView适配器，处理不同文件类型的显示逻辑
     */
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
        private List<File> mData = new ArrayList<>();

        /**
         * 更新适配器数据并刷新列表
         */
        public void setData(List<File> newData) {
            if (newData != null) {
                mData.clear();
                mData.addAll(newData);
                notifyDataSetChanged();
            }
        }

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_file, parent, false);
            return new FileViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            File file = mData.get(position);
            if (file.isDirectory()) {
                holder.itemView.setBackgroundResource(R.drawable.item_folder_rounded_bg);
                int iconSize = dp2px(holder.itemView.getContext(), 36);
                ViewGroup.LayoutParams params = holder.ivIcon.getLayoutParams();
                params.width = iconSize;
                params.height = iconSize;
                holder.ivIcon.setLayoutParams(params);
                drawFolderIconWithNumber(holder.ivIcon, position + 1);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.black));
            } else if (file.getName().toLowerCase().endsWith(".zip")) {
                holder.ivIcon.setImageResource(R.drawable.ic_image_error);
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.folderColor));
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                holder.ivIcon.setImageResource(R.drawable.ic_file);
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            } else if (isImageFile(file)) {
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                loadImageThumbnail(file, holder.ivIcon);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_other_file);
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
            }
            holder.tvName.setText(formatFileNameForDisplay(file.getName()));
            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    if (copiedFile != null && file.equals(copiedFile)) {
                        hidePasteButton(); // 隐藏粘贴按钮
                        Toast.makeText(MainActivity.this, "禁止在当前复制/剪切的文件夹内粘贴，已自动隐藏粘贴功能", Toast.LENGTH_SHORT).show();
                    }
                    isInSearchMode = false;
                    etSearch.setText("");
                    currentDirectory = file;
                    loadFileList();
                    PreferenceUtils.saveLastPageType(MainActivity.this, "main");
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, file.getAbsolutePath());
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, null);
                    PreferenceUtils.saveLastViewedImage(MainActivity.this, null);
                } else if (file.getName().toLowerCase().endsWith(".txt")) {
                    hidePasteButton();
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, file.getAbsolutePath());
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, file.getParentFile().getAbsolutePath());
                    new Thread(() -> {
                        boolean corrected = correctFilepathInTxt(file);
                        runOnUiThread(() -> {
                            if (corrected) {
                                Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
                                editIntent.putExtra("file_path", file.getAbsolutePath());
                                editIntent.putExtra("is_pre_edit", false);
                                editIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
                                editIntent.putExtra("is_root_directory", file.getParentFile().equals(rootDirectory));
                                startActivityForResult(editIntent, REQUEST_EDIT_FILE);
                            } else {
                                Toast.makeText(MainActivity.this, "文件路径修正失败，无法打开", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                    PreferenceUtils.saveLastPageType(MainActivity.this, "editor");
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    showZipExtractDialog(file);
                } else if (isImageFile(file)) {
                    hidePasteButton();
                    openImageFile(file);
                    PreferenceUtils.saveLastPageType(MainActivity.this, "image");
                    PreferenceUtils.saveLastViewedImage(MainActivity.this, file.getAbsolutePath());
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, file.getParentFile().getAbsolutePath());
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, null);
                } else {
                    hidePasteButton();
                    showUnsupportedFileMessage();
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (file.isDirectory()) {
                    showFolderOptions(file);
                } else {
                    showFileOptions(file);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        /**
         * 文件列表项ViewHolder，绑定视图控件
         */
        class FileViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;

            public FileViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.icon);
                tvName = itemView.findViewById(R.id.name);
            }
        }

        /**
         * dp转px，适配不同屏幕密度
         */
        private int dp2px(Context context, float dp) {
            return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
        }

        /**
         * 绘制带序号的文件夹图标
         */
        private void drawFolderIconWithNumber(ImageView imageView, int number) {
            Drawable folderDrawable = ContextCompat.getDrawable(imageView.getContext(), R.drawable.ic_folder);
            if (folderDrawable == null) {
                imageView.setImageResource(R.drawable.ic_folder);
                return;
            }
            int drawableWidth = folderDrawable.getIntrinsicWidth();
            int drawableHeight = folderDrawable.getIntrinsicHeight();
            Bitmap bitmap = Bitmap.createBitmap(drawableWidth, drawableHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            folderDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            folderDrawable.draw(canvas);
            Paint paint = new Paint();
            paint.setColor(Color.parseColor("#FFB85C"));
            paint.setTextSize(dp2px(imageView.getContext(), 12));
            paint.setTypeface(Typeface.DEFAULT_BOLD); // 加粗
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setAntiAlias(true);
            String numberText = String.valueOf(number);
            float x = canvas.getWidth() / 2f;
            float textHeight = paint.descent() - paint.ascent();
            float y = canvas.getHeight() / 2f - (paint.descent() + paint.ascent()) / 2 + textHeight / 6;
            canvas.drawText(numberText, x, y, paint);
            imageView.setImageBitmap(bitmap);
        }
    }
}