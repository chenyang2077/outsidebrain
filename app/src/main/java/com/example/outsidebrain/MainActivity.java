/*
软件名称：快乐文字
版本号：V1.0
功能描述：1. 基础文件管理：支持TXT文件/文件夹整理、ZIP压缩解压、文件/文件夹复制与移动；
        2. 文件浏览编辑：支持TXT文件/图片浏览、TXT文件编辑；
        3. 辅助功能：文件分享至第三方APP、按文件名/TXT内容检索文件。
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
import android.util.DisplayMetrics;
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
import androidx.core.content.FileProvider;
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
import android.icu.text.Transliterator;
import java.io.OutputStreamWriter;
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
    private static final String ROOT_FOLDER_NAME = "主页根目录";
    private File transferStationDirectory;
    private static final int REQUEST_TRANSFER_PERMISSION = 101;
    private boolean isInTransferStation = false;
    private File recycleBinDirectory;
    private boolean isInRecycleBin = false;
    public static final SimpleDateFormat MILLIS_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
    public static final Pattern FILE_MILLIS_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    public static final Pattern TARGET_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    public static final Pattern INCREMENT_TIMESTAMP_PATTERN = Pattern.compile("(_[A-Za-z0-9]{6}_\\d{17})(_\\d{17})+$");
    public static final Pattern OLD_TIMESTAMP_PATTERN = Pattern.compile("_(\\d{14}|\\d{17})$");
    private View pasteButton;
    private Toast mPathToast; // 路径提示Toast
    private View mTouchOverlay; // 全屏触摸覆盖层（用于监听触摸消失）
    // 自定义路径提示（替代Toast，无自动消失）
    private View mCustomTipView;
    private boolean mIsTipShowing = false;
    // ========== 新增全局变量：解决卡顿核心 ==========
    private View mReusableTipView; // 复用唯一的提示视图，避免重复inflate
    private long lastTipUpdateTime = 0; // 防抖时间戳，避免高频触发
    private String lastTipText = ""; // 缓存上一次提示文本，避免重复更新
    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
    /**
     * 页面创建初始化：
     private static final Pattern FIRST_LINE_PATH_PATTERN = Pattern.compile("^【[^】]*】$");
     * 1. 绑定布局控件，初始化视图组件；2. 设置夜间模式、文件列表适配器；3. 初始化回收站/中转站目录； 4. 设置搜索框/菜单按钮/新增按钮点击事件； 5. 检查存储权限，初始化根目录。
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
        recoverFromCrash();
        isInSearchMode = false;
        initRecycleBin();
        transferStationDirectory = new File(Environment.getExternalStorageDirectory(), "中转站");
        if (!transferStationDirectory.exists()) {
            transferStationDirectory.mkdirs();
        }
        String openFolder = getIntent().getStringExtra("open_folder");
        if (openFolder != null) {
            File dir = new File(openFolder);
            if (dir.exists()) {
                currentDirectory = dir;
                loadFileList();
                updateLevelHint();
            }
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
        // ===== 新增：初始化全屏触摸覆盖层 =====
        mTouchOverlay = new View(this);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        mTouchOverlay.setLayoutParams(params);
        // 设置透明背景，不遮挡界面
        mTouchOverlay.setBackgroundColor(Color.TRANSPARENT);
        // 添加到屏幕最顶层
        ((ViewGroup) getWindow().getDecorView()).addView(mTouchOverlay);
        // 默认隐藏
        mTouchOverlay.setVisibility(View.GONE);

        // 触摸覆盖层的监听：触摸任意位置关闭Toast并隐藏自身
        mTouchOverlay.setOnTouchListener((v, event) -> {
            if (mPathToast != null) {
                mPathToast.cancel(); // 关闭Toast
                mPathToast = null;
            }
            mTouchOverlay.setVisibility(View.GONE); // 隐藏覆盖层
            return false;
        });


    }
    /**
     * 显示自定义路径提示（TXT文件所在文件夹的实际展示序号层级 + 文件夹路径+文件名分行显示）
     */
    /**
     * 显示可点击的路径提示，点击跳转到对应文件夹
     */
    private void showCustomPathTip(String fullRelativePath) {
        // ========== 修复1：防抖：50ms内不重复执行，避免高频创建视图 ==========
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTipUpdateTime < 50) {
            return;
        }
        lastTipUpdateTime = currentTime;

        // ========== 修复2：先隐藏旧视图（保留原有逻辑） ==========
        hideCustomPathTip();

        // ========== 原有路径解析逻辑：完全保留 ==========
        String folderPath;
        String fileName;
        int lastSepIndex = fullRelativePath.lastIndexOf(File.separator);
        if (lastSepIndex == -1) {
            folderPath = "";       // 根目录下
            fileName = fullRelativePath;
        } else {
            folderPath = fullRelativePath.substring(0, lastSepIndex);
            fileName = fullRelativePath.substring(lastSepIndex + 1);
        }

        String tipText;
        if (TextUtils.isEmpty(folderPath)) {
            tipText = "根目录" + "\n" + fileName;
        } else {
            tipText = folderPath + "\n" + fileName;
        }

        // ========== 修复3：文本未变化则直接返回，避免无效操作 ==========
        if (tipText.equals(lastTipText)) {
            return;
        }
        lastTipText = tipText;

        // ========== 修复4：复用视图，避免重复inflate ==========
        if (mReusableTipView == null) {
            // 仅第一次调用时加载布局，后续复用
            mReusableTipView = LayoutInflater.from(this).inflate(R.layout.layout_custom_tip, null);
        }
        mCustomTipView = mReusableTipView; // 替换原有mCustomTipView为复用视图

        // ========== 原有文本设置+点击事件：完全保留 ==========
        TextView tvTip = mCustomTipView.findViewById(R.id.tv_custom_tip);
        tvTip.setText(tipText);

        View tipContainer = mCustomTipView.findViewById(R.id.tip_container);
        tipContainer.setOnClickListener(v -> {
            try {
                File targetFolder;
                if (TextUtils.isEmpty(folderPath)) {
                    targetFolder = rootDirectory;
                } else {
                    targetFolder = new File(rootDirectory, folderPath);
                }

                if (targetFolder.exists() && targetFolder.isDirectory()) {
                    isInSearchMode = false;
                    etSearch.setText("");
                    currentDirectory = targetFolder;
                    loadFileList();

                    PreferenceUtils.saveLastPageType(MainActivity.this, "main");
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, targetFolder.getAbsolutePath());
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, null);
                    PreferenceUtils.saveLastViewedImage(MainActivity.this, null);
                }
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "跳转文件夹失败", Toast.LENGTH_SHORT).show();
            }
            hideCustomPathTip();
        });

        // ========== 修复5：提前计算位置，避免post异步调整导致的闪屏 ==========
        ViewGroup rootView = (ViewGroup) getWindow().getDecorView();

        // 先计算位置，再添加视图（核心：避免视图先显示在默认位置）
        int screenWidth = rootView.getWidth();
        int bottomMargin = dp2px(80);
        // 提前测量视图尺寸（避免post获取宽高）
        mCustomTipView.measure(
                View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int tipWidth = mCustomTipView.getMeasuredWidth();
        int tipHeight = mCustomTipView.getMeasuredHeight();
        int left = (screenWidth - tipWidth) / 2;
        int top = rootView.getHeight() - bottomMargin - tipHeight;

        // 设置位置后再添加视图，避免闪屏
        mCustomTipView.setX(left);
        mCustomTipView.setY(top);

        // ========== 修复6：添加视图前先移除旧引用（避免重复添加） ==========
        if (mCustomTipView.getParent() != null) {
            ((ViewGroup) mCustomTipView.getParent()).removeView(mCustomTipView);
        }
        rootView.addView(mCustomTipView);

        // 原有布局参数设置：保留
        ViewGroup.LayoutParams params = mCustomTipView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mCustomTipView.setLayoutParams(params);

        mIsTipShowing = true;

        // 外部点击消失：保留
        rootView.setOnTouchListener((view, event) -> {
            if (mIsTipShowing && event.getAction() == MotionEvent.ACTION_DOWN) {
                hideCustomPathTip();
                rootView.setOnTouchListener(null);
            }
            return false;
        });
    }
    private void hideCustomPathTip() {
        if (mIsTipShowing && mCustomTipView != null && mCustomTipView.getParent() != null) {
            ((ViewGroup) mCustomTipView.getParent()).removeView(mCustomTipView);
            // ========== 核心修改：注释掉mCustomTipView = null，保留复用视图 ==========
            // mCustomTipView = null; // 不要置空！保留视图引用以便复用
            mIsTipShowing = false;
        }
    }

    // 工具方法：dp转px（如果已有可忽略）
    private int dp2px(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
    /**
     * 文件列表RecyclerView适配器，处理不同文件类型的显示逻辑
     */
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {
        private List<File> mData = new ArrayList<>();
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
            String displayFileName = file.getName();

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
                displayFileName = formatFileNameForDisplay(file.getName());
            } else if (isImageFile(file)) {
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                loadImageThumbnail(file, holder.ivIcon);
                displayFileName = formatImageFileName(file.getName());
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_other_file);
                holder.itemView.setBackgroundResource(R.drawable.item_txt_rounded_bg);
                holder.tvName.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.white));
                displayFileName = formatFileNameForDisplay(file.getName());
            }

            // 统一设置处理后的文件名
            holder.tvName.setText(displayFileName);

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

                    // ========== 搜索模式显示自定义路径提示（永不自动消失）==========
                    if (isInSearchMode) {
                        // 只显示根目录后面的路径
                        String full = file.getAbsolutePath();
                        String root = rootDirectory.getAbsolutePath();
                        String showPath = full.replace(root, "");
                        if (showPath.startsWith(File.separator)) {
                            showPath = showPath.substring(1);
                        }
                        // 显示自定义提示（替代Toast）
                        MainActivity.this.showCustomPathTip(showPath);
                    }

                    new Thread(() -> {
                        boolean corrected = file.getName().toLowerCase().endsWith(".txt");
                        runOnUiThread(() -> {
                            if (corrected) {
                                Intent editIntent = new Intent(MainActivity.this, FileEditorActivity.class);
                                editIntent.putExtra("file_path", file.getAbsolutePath());
                                editIntent.putExtra("is_pre_edit", false);
                                editIntent.putExtra("root_folder_name", ROOT_FOLDER_NAME);
                                editIntent.putExtra("is_root_directory", file.getParentFile().equals(rootDirectory));
                                startActivityForResult(editIntent, REQUEST_EDIT_FILE);
                            } else {
                                Toast.makeText(MainActivity.this, "仅支持编辑TXT文件", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                    PreferenceUtils.saveLastPageType(MainActivity.this, "editor");
                }else if (file.getName().toLowerCase().endsWith(".zip")) {
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
        private String formatImageFileName(String originalFileName) {
            String extension = "";
            String nameWithoutExt = originalFileName;
            int lastDotIndex = originalFileName.lastIndexOf(".");
            if (lastDotIndex != -1) {
                extension = originalFileName.substring(lastDotIndex);
                nameWithoutExt = originalFileName.substring(0, lastDotIndex);
            }
            String timestampRegex = "_\\d{8,14}";
            String randomStrRegex = "_[a-zA-Z0-9]{6,16}";
            String cleanName = nameWithoutExt.replaceAll(timestampRegex, "")
                    .replaceAll(randomStrRegex, "");
            return cleanName + extension;
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
    /**
     * 导航至根目录：1. 退出搜索模式，清空搜索框； 2. 重置回收站/中转站标识； 3. 加载根目录文件列表，更新层级提示； 4. 保存页面状态，延迟修正TXT文件路径。
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
                    runOnUiThread(() -> loadFileList());
                }).start();
            }, 1000);
        } else {
            Toast.makeText(this, "根目录不存在", Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 初始化回收站： 1. 创建应用内部存储的回收站目录； 2. 打印创建结果日志，失败时提示权限问题。
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
     * TXT文件时间戳比较器：1. 优先按文件名中的17位时间戳降序排序； 2. 无时间戳时按文件最后修改时间降序排序。
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
     * 文件夹排序（支持多级小数序号+汉字拼音首字母排序）：
     * 1. 提取文件夹名称开头的多级数字序号（如1.25.5.25）；
     * 2. 按层级逐位比较数字大小（1.25.5.25 < 1.25.22.12）；
     * 3. 无数字序号则按「完整名称拼音（去声调）+ 原名字母序」排序（兼容中英文/汉字）。
     *
     * @param folders 待排序的文件夹列表
     */
    private void sortFoldersWithDecimalSupport(List<File> folders) {
        final Transliterator transliterator;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Transliterator temp = null;
            try {
                temp = Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower");
            } catch (Exception e) {
                temp = null;
            }
            transliterator = temp;
        } else {
            transliterator = null;
        }
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
                String pinyin1 = convertToPinyin(name1, transliterator);
                String pinyin2 = convertToPinyin(name2, transliterator);
                int pinyinCompare = pinyin1.compareTo(pinyin2);
                if (pinyinCompare != 0) {
                    return pinyinCompare;
                }
                return name1.compareTo(name2);
            }
        });
    }
    /**
     * 转换拼音方法（参数直接用Transliterator，无需强转）
     */
    private String convertToPinyin(String name, Transliterator transliterator) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        try {
            String pinyin = name;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && transliterator != null) {
                pinyin = transliterator.transliterate(name);
            }
            pinyin = pinyin.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            return TextUtils.isEmpty(pinyin) ? name.toLowerCase() : pinyin;
        } catch (Exception e) {
            return name.toLowerCase();
        }
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
    /**
     * 检查文件内容中是否包含指定关键词（忽略大小写）
     * 过滤规则：不检查ZIP压缩包、图片文件、其他非文本文件
     * @param file 待检查的文件
     * @param keyword 要匹配的关键词（空字符串返回false）
     * @return 内容是否包含关键词
     */
    private boolean isContentContainKeyword(File file, String keyword) {
        // 1. 空关键词直接返回false
        if (TextUtils.isEmpty(keyword)) {
            return false;
        }

        // 2. 过滤非文本文件：ZIP压缩包、图片、其他非文本文件不检查
        if (file.getName().toLowerCase().endsWith(".zip") || isImageFile(file) || isOtherFile(file)) {
            return false;
        }

        // 3. 读取文件内容，逐行检查关键词（忽略大小写）
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            String lowerKeyword = keyword.toLowerCase(); // 预转换关键词为小写，提升性能

            while ((line = br.readLine()) != null) {
                // 移除原逻辑中第一行路径标识的过滤，直接检查整行内容
                if (line.toLowerCase().contains(lowerKeyword)) {
                    return true; // 找到关键词，立即返回true
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // 文件读取失败时，返回false
        }

        // 4. 未找到关键词或文件读取失败
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

        // 🔥 搜索开始：弹出提示框（文件多需要时间）
        ProgressDialog searchDialog = new ProgressDialog(this);
        searchDialog.setMessage("太慢可跳过末尾带#的文件夹...");
        searchDialog.setCanceledOnTouchOutside(false); // 点击外部不消失
        searchDialog.setCancelable(false); // 按返回键不消失
        searchDialog.show();

        new Thread(() -> {
            isInSearchMode = true;
            searchResultList.clear();
            recursiveSearch(currentDirectory, keyword);
            sortSearchResult();

            runOnUiThread(() -> {
                // 🔥 搜索结束：关闭提示框
                if (searchDialog.isShowing()) {
                    searchDialog.dismiss();
                }

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
     * 2. 文件夹：名称匹配则加入结果，递归搜索子目录，跳过末尾带#的文件夹；
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
                // ======================
                // 🔥 只跳过【子文件夹】末尾带 # 的，不跳过当前目录
                // ======================
                if (file.getName().endsWith("#")) {
                    continue;
                }

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
     * 1. 按类型分组（文件夹→ZIP→TXT/图片→其他）；
     * 2. 文件夹按名称排序，TXT/图片按时间戳排序，其余按修改时间排序。
     */
    private void sortSearchResult() {
        if (searchResultList.isEmpty()) return;
        List<File> folders = new ArrayList<>();
        List<File> zipFiles = new ArrayList<>();
        List<File> txtAndImageFiles = new ArrayList<>();
        List<File> otherFiles = new ArrayList<>();
        for (File f : searchResultList) {
            if (f.isDirectory()) {
                folders.add(f);
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                zipFiles.add(f);
            } else if (f.getName().toLowerCase().endsWith(".txt") || isImageFile(f)) {
                txtAndImageFiles.add(f);
            } else {
                otherFiles.add(f);
            }
        }
        Collections.sort(folders, (file1, file2) -> file1.getName().compareTo(file2.getName()));
        Collections.sort(txtAndImageFiles, new TxtTimestampComparator()); // 复用你的比较器
        Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        Collections.sort(otherFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        searchResultList.clear();
        searchResultList.addAll(folders);
        searchResultList.addAll(zipFiles);
        searchResultList.addAll(txtAndImageFiles); // 合并后的TXT+图片组
        searchResultList.addAll(otherFiles);
    }


    /**
     * 初始化外部存储（根目录）：
     * 1. 创建应用私有存储的根目录（主页根目录）；
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
     * 创建测试文件：
     * 1. 生成使用说明TXT文件；
     * 2. 写入软件使用说明内容，保存到当前目录。
     */
    private void createTestFile() {
        // 修复核心：使用全局的currentDirectory（主页根目录），而非直接getFilesDir()
        if (currentDirectory == null || !currentDirectory.canWrite()) {
            Toast.makeText(this, "创建测试文件失败：目标目录不可写", Toast.LENGTH_SHORT).show();
            return;
        }

        // 简化：固定文件名，移除随机字符和时间戳
        File testFile = new File(currentDirectory, "使用说明与注意事项.txt");

        try {
            // 检查文件是否已存在，避免重复创建
            if (testFile.exists()) {
                Toast.makeText(this, "测试文件已存在，无需重复创建", Toast.LENGTH_SHORT).show();
                return;
            }

            // 创建文件并写入内容
            if (testFile.createNewFile()) {
                String content = getResources().getString(R.string.app_usage_instructions);
                // 确保字符编码为UTF-8，避免乱码
                try (BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(
                                new FileOutputStream(testFile),
                                StandardCharsets.UTF_8)
                )) {
                    writer.write(content);
                }
            } else {
                Toast.makeText(this, "测试文件创建失败：无法新建文件", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "创建测试文件失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
     * 1. 按类型分组（文件夹→ZIP→TXT/图片→其他）；
     * 2. 文件夹按小数序号排序，TXT/图片按数字/时间戳排序；
     * 3. 更新适配器数据，刷新UI。
     */
    private void loadFileList() {
        fileList.clear();
        File[] files = currentDirectory.listFiles();
        if (files != null) {
            List<File> folders = new ArrayList<>();
            List<File> zipFiles = new ArrayList<>();
            List<File> txtAndImageFiles = new ArrayList<>();
            List<File> otherFiles = new ArrayList<>();
            for (File file : files) {
                if (file.isDirectory()) {
                    folders.add(file);
                } else if (file.getName().toLowerCase().endsWith(".zip")) {
                    zipFiles.add(file);
                } else if (file.getName().toLowerCase().endsWith(".txt") || isImageFile(file)) {
                    txtAndImageFiles.add(file);
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
            Collections.sort(txtAndImageFiles, txtImageComparator); // 合并后的列表排序
            Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            Collections.sort(otherFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            fileList.addAll(folders);
            fileList.addAll(zipFiles);
            fileList.addAll(txtAndImageFiles);
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

                    // 🔥 解压开始：显示加载提示
                    ProgressDialog extractDialog = new ProgressDialog(this);
                    extractDialog.setMessage("正在解压中，请稍候...");
                    extractDialog.setCanceledOnTouchOutside(false);
                    extractDialog.setCancelable(false);
                    extractDialog.show();

                    new Thread(() -> {
                        boolean result = FileUtils.unzipFile(zipFile, currentDirectory);
                        runOnUiThread(() -> {
                            // 🔥 解压结束：关闭提示
                            if (extractDialog.isShowing()) {
                                extractDialog.dismiss();
                            }

                            if (result) {
                                Toast.makeText(this, "解压成功", Toast.LENGTH_SHORT).show();
                                loadFileList();
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
                            Toast.makeText(this, "删除操作失败", Toast.LENGTH_SHORT).show();
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

        // 加载提示（固定final，不会报错）
        final ProgressDialog recycleDialog = new ProgressDialog(this);
        recycleDialog.setMessage("正在移动到回收站，请稍候...");
        recycleDialog.setCanceledOnTouchOutside(false);
        recycleDialog.setCancelable(false);
        runOnUiThread(recycleDialog::show);

        try {
            if (!recycleBinDirectory.exists()) {
                recycleBinDirectory.mkdirs();
            }

            // 文件夹
            if (file.isDirectory()) {
                File targetFolder = new File(recycleBinDirectory, file.getName());
                File safeFolder = getNonConflictFile(targetFolder);
                final boolean result = moveFolderToRecycleBinLikeCut(file, safeFolder);

                runOnUiThread(() -> {
                    if (recycleDialog.isShowing()) {
                        recycleDialog.dismiss();
                        // 成功不提示，只失败提示
                        if (!result) {
                            Toast.makeText(this, "移动失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                return result;
            }

            // 文件处理
            boolean isTxt = file.getName().toLowerCase().endsWith(".txt");
            boolean isImg = isImageFile(file);
            boolean finalSuccess = false;

            if (isTxt || isImg) {
                String originalName = file.getName();
                String originalExt = getOriginalExtension(originalName);
                String[] parsed = parseFileName(originalName);
                String pureCoreName = parsed[0];

                pureCoreName = getSafeCoreName(pureCoreName);
                String finalCoreName = getNonConflictCoreNameInFolder(recycleBinDirectory, pureCoreName);
                String timestampSuffix = generateNewTimestampSuffix(parsed);
                String finalFileName = finalCoreName + timestampSuffix + originalExt;

                File targetFile = new File(recycleBinDirectory, finalFileName);

                boolean success = file.renameTo(targetFile);
                if (!success) {
                    if (copyFileContent(file, targetFile)) {
                        file.delete();
                        finalSuccess = true;
                    }
                } else {
                    finalSuccess = true;
                }

            } else {
                // 普通文件
                File targetFile = new File(recycleBinDirectory, file.getName());
                File safeTarget = getNonConflictFile(targetFile);
                boolean success = file.renameTo(safeTarget);
                if (!success) {
                    if (copyFileContent(file, safeTarget)) {
                        file.delete();
                        finalSuccess = true;
                    }
                } else {
                    finalSuccess = true;
                }
            }

            // 最终提示：成功不提示，失败才提示
            boolean finalResult = finalSuccess;
            runOnUiThread(() -> {
                if (recycleDialog.isShowing()) {
                    recycleDialog.dismiss();
                    if (!finalResult) {
                        Toast.makeText(this, "移动失败", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            return finalSuccess;

        } catch (Exception e) {
            e.printStackTrace();

            runOnUiThread(() -> {
                if (recycleDialog.isShowing()) {
                    recycleDialog.dismiss();
                    Toast.makeText(this, "移动失败：异常", Toast.LENGTH_SHORT).show();
                }
            });

            return false;
        }
    }
    private boolean moveFolderToRecycleBinLikeCut(File sourceFolder, File targetFolder) {
        if (sourceFolder == null || !sourceFolder.exists() || targetFolder == null) {
            return false;
        }

        try {
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }

            File[] files = sourceFolder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        String safeFolderName = getSafeFileNameForRecycleBin(file.getName());
                        File subTarget = new File(targetFolder, safeFolderName);
                        moveFolderToRecycleBinLikeCut(file, subTarget);
                    } else {
                        boolean isTxt = file.getName().toLowerCase().endsWith(".txt");
                        boolean isImg = isImageFile(file);

                        if (isTxt || isImg) {
                            String originalName = file.getName();
                            String originalExt = getOriginalExtension(originalName);
                            String[] parsed = parseFileName(originalName);
                            String pureCoreName = parsed[0];
                            pureCoreName = getSafeCoreName(pureCoreName);

                            String timestampSuffix = generateNewTimestampSuffix(parsed);

                            // ==========================
                            // 🔥 【关键修复】只在当前目标文件夹查重主体名
                            // ==========================
                            String finalCoreName = getNonConflictCoreNameInFolder(targetFolder, pureCoreName);
                            String uniqueFileName = finalCoreName + timestampSuffix;

                            String finalFileName = removeAllExtensions(uniqueFileName) + originalExt;
                            File targetFile = new File(targetFolder, finalFileName);

                            if (!file.renameTo(targetFile)) {
                                if (copyFileContent(file, targetFile)) {
                                    file.delete();
                                }
                            }
                        } else {
                            File target = new File(targetFolder, file.getName());
                            File safeTarget = getNonConflictFile(target);
                            if (!file.renameTo(safeTarget)) {
                                copyFileContent(file, safeTarget);
                                file.delete();
                            }
                        }
                    }
                }
            }

            deleteFolderTreeInternal(sourceFolder);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 内部删除，永不冲突
    private void deleteFolderTreeInternal(File fileOrFolder) {
        if (fileOrFolder == null || !fileOrFolder.exists()) return;
        if (fileOrFolder.isDirectory()) {
            File[] children = fileOrFolder.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFolderTreeInternal(child);
                }
            }
        }
        fileOrFolder.delete();
    }
    // 🔥 回收站专用：裁剪主体名称，保留时间戳、后缀、格式
    private String getSafeFileNameForRecycleBin(String fileName) {
        final int MAX_ALLOW = 120; // 安全长度
        if (fileName.length() <= MAX_ALLOW) {
            return fileName;
        }

        // 分离主体 + 合法后缀
        int lastDot = fileName.lastIndexOf(".");
        String ext = "";
        String main = fileName;

        if (lastDot > 0 && (fileName.length() - lastDot - 1) <= 4) {
            String afterDot = fileName.substring(lastDot + 1).toLowerCase();
            if (afterDot.equals("txt") || afterDot.equals("png") || afterDot.equals("jpg")
                    || afterDot.equals("jpeg") || afterDot.equals("gif") || afterDot.equals("bmp")) {
                ext = fileName.substring(lastDot);
                main = fileName.substring(0, lastDot);
            }
        }

        // 只裁剪主体，从尾部裁
        if (main.length() > MAX_ALLOW) {
            main = main.substring(0, MAX_ALLOW);
        }

        return main + ext;
    }
    private boolean moveFolderToRecycleBinWithSafeName(File sourceFolder, File targetFolder) {
        try {
            if (!targetFolder.exists()) {
                targetFolder.mkdirs();
            }

            File[] files = sourceFolder.listFiles();
            if (files == null) {
                return true;
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    String safeFolderName = getSafeFileNameForRecycleBin(file.getName());
                    File subTarget = new File(targetFolder, safeFolderName);
                    moveFolderToRecycleBinWithSafeName(file, subTarget);
                } else {
                    String safeFileName = getSafeFileNameForRecycleBin(file.getName());
                    File targetFile = new File(targetFolder, safeFileName);
                    File finalTarget = getNonConflictFile(targetFile);

                    if (file.getName().toLowerCase().endsWith(".txt")) {
                        moveFileWithTimestampUpdate(file, finalTarget);
                    } else {
                        file.renameTo(finalTarget);
                    }
                }
            }

            sourceFolder.delete();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    // ====================== 回收站安全文件名：裁剪主体，保留后缀、时间戳、随机串 ======================

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

        // ======================
        // 🔥 永久显示分享：所有文件夹都显示分享
        // ======================
        boolean shouldHideShare = false;

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
     * 分享文件，
     */
    private void shareFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    file
            );
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(getMimeType(file.getName()));
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, "分享文件");
            if (shareIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(chooser);
            } else {
                Toast.makeText(this, "未找到可分享的应用", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private String getMimeType(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "application/octet-stream";
        }
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        switch (extension) {
            case "txt":
                return "text/plain";
            case "zip":
                return "application/zip";
            default:
                return "application/octet-stream";
        }
    }
    /**
     * 显示文件/文件夹重命名弹窗
     * 核心：
     * 1. 编辑框隐藏「随机字符+单/双时间戳」，只显示纯核心名
     * 2. 时间戳生成（含双时间戳）完全复用原逻辑，规则不变
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
        final boolean isImageFileFlag;
        final boolean isFolder;
        final String originalCoreName;
        final String imgExt;
        isFolder = file.isDirectory();
        isTxtFile = !isFolder && originalFileName.toLowerCase().endsWith(".txt");
        isImageFileFlag = !isFolder && isImageFile(file);
        boolean isSpecialFile = isTxtFile || isImageFileFlag;
        originalCoreName = extractPureCoreNameForDisplay(originalFileName);
        if (isImageFileFlag) {
            imgExt = getPureImageExtension(originalFileName);
        } else {
            imgExt = "";
        }
        if (!isSpecialFile && !isFolder) {
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
        if (isSpecialFile) {
            displayName = originalCoreName;
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

            if (isFolder) {
                File newFolder = new File(file.getParentFile(), newName);
                if (newFolder.exists()) {
                    Toast.makeText(this, "此文件夹名称已存在", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (newName.equals(originalFileName)) {
                    Toast.makeText(this, "名称未更改", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (file.renameTo(newFolder)) {
                    Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (isSpecialFile) {
                String newCoreName = newName;
                newCoreName = removeAllExtensions(newCoreName);

                int maxCoreLength = 45;
                if (newCoreName.length() > maxCoreLength) {
                    newCoreName = newCoreName.substring(0, maxCoreLength);
                }

                if (newCoreName.equals(originalCoreName)) {
                    Toast.makeText(this, "名称未更改", Toast.LENGTH_SHORT).show();
                    return;
                }

                // ==============================
                // 🔥 【和 FileEditor 完全一致】
                // 第一步：主体名查重 → 自动加 (1)(2)
                // ==============================
                File parentDir = file.getParentFile();
                newCoreName = getNonConflictCoreNameInDir(parentDir, newCoreName);

                // -------- 你原来的时间戳逻辑 --------
                String nameWithoutExt = originalFileName;
                int lastDot = originalFileName.lastIndexOf(".");
                if (lastDot > 0) {
                    nameWithoutExt = originalFileName.substring(0, lastDot);
                }
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

                String tempFileName = newCoreName + timestampSuffix.toString();
                if (isTxtFile) {
                    tempFileName += ".txt";
                } else if (isImageFileFlag) {
                    tempFileName += imgExt;
                }
                tempFileName = purifyFileName(tempFileName, isTxtFile, isImageFileFlag, originalFileName);

                // ==============================
                // 🔥 第二步：完整文件名保险查重
                // ==============================
                File finalFile = getNonConflictFile(new File(parentDir, tempFileName));

                if (file.renameTo(finalFile)) {
                    Toast.makeText(this, "重命名成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                }

            } else {
                final String finalNewName = (!isFolder && !fileExtension.isEmpty()) ? newName + fileExtension : newName;
                if (finalNewName.equals(originalFileName)) {
                    Toast.makeText(this, "名称未更改", Toast.LENGTH_SHORT).show();
                    return;
                }
                File newFile = new File(file.getParentFile(), finalNewName);
                if (newFile.exists()) {
                    Toast.makeText(this, "文件名称已存在", Toast.LENGTH_SHORT).show();
                    return;
                }
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

    // ==============================
// 🔥 主体名查重（和 FileEditor 一模一样）
// ==============================
    private String getNonConflictCoreNameInDir(File folder, String baseCore) {
        if (folder == null || !folder.exists() || TextUtils.isEmpty(baseCore)) {
            return baseCore;
        }

        if (!isCoreNameExists(folder, baseCore)) {
            return baseCore;
        }

        int index = 1;
        while (true) {
            String testName = baseCore + "(" + index + ")";
            if (!isCoreNameExists(folder, testName)) {
                return testName;
            }
            index++;
        }
    }

    private boolean isCoreNameExists(File folder, String coreName) {
        File[] files = folder.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isFile()) {
                String existCore = extractPureCoreNameForDisplay(f.getName());
                if (coreName.equals(existCore)) {
                    return true;
                }
            }
        }
        return false;
    }

    // 当前文件夹查重，自动加 (1)(2)
    private String getUniqueFileNameInCurrentDir(File folder, String baseName) {
        if (folder == null || !folder.exists()) return baseName;
        File test = new File(folder, baseName);
        if (!test.exists()) return baseName;

        int dot = baseName.lastIndexOf(".");
        String name = dot == -1 ? baseName : baseName.substring(0, dot);
        String ext = dot == -1 ? "" : baseName.substring(dot);

        int num = 1;
        while (new File(folder, name + "(" + num + ")" + ext).exists()) {
            num++;
        }
        return name + "(" + num + ")" + ext;
    }



    /**
     * 提取纯核心名（专门兼容双时间戳，仅用于编辑显示，不影响生成逻辑）
     * 支持格式：
     * - 核心名_随机串_时间戳.后缀
     * - 核心名_随机串_时间戳1_时间戳2.后缀
     * @param fileName 原始文件名
     * @return 纯核心名（无随机字符、无单/双时间戳、无后缀）
     */
    private String extractPureCoreNameForDisplay(String fileName) {
        // 步骤1：先去掉「真正的文件后缀」（保留文件名中的小数点）
        String nameWithoutExt = removeAllExtensions(fileName);

        // 步骤2：移除时间戳片段（保留合法小数点）
        String timestampPattern = "_[a-zA-Z0-9]+_(\\d+)(_\\d+)*";
        Pattern pattern = Pattern.compile(timestampPattern);
        Matcher matcher = pattern.matcher(nameWithoutExt);
        while (matcher.find()) {
            nameWithoutExt = nameWithoutExt.replace(matcher.group(), "");
        }

        // 步骤3：清理多余下划线（保留小数点）
        nameWithoutExt = nameWithoutExt.replaceAll("^_+|_+$", "");

        return nameWithoutExt;
    }

    // 提取图片真实后缀（移除非图片后缀）
    private String getPureImageExtension(String fileName) {
        String ext = "";
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            ext = fileName.substring(lastDot).toLowerCase();
            // 仅保留图片后缀，其他后缀清空
            if (!ext.equals(".png") && !ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".gif") && !ext.equals(".bmp")) {
                ext = "";
            }
        }
        return ext;
    }

    // 净化文件名（移除错误后缀，保留正确后缀）
    private String purifyFileName(String fileName, boolean isTxtFile, boolean isImageFileFlag, String originalFileName) {
        // 步骤1：移除所有后缀，得到纯核心名+时间戳
        String pureName = removeAllExtensions(fileName);
        // 步骤2：拼接正确后缀
        if (isTxtFile) {
            return pureName + ".txt";
        } else if (isImageFileFlag) {
            return pureName + getPureImageExtension(originalFileName);
        }
        return fileName;
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
     * 复制文件夹并处理其中TXT/图片文件的全局唯一命名规则，维护序号避免重复
     */
    private boolean copyFolderWithTxtGlobalCheck(File sourceFolder, File targetParent) throws IOException {
        // 复制文件夹：保持原有逻辑（文件夹名/内部子文件夹/文件 全域查重）
        String baseName = sourceFolder.getName();
        String uniqueFolderName = getUniqueFolderName(targetParent, baseName); // 全域查重文件夹名
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
            }
            else if (sourceFile.getName().toLowerCase().endsWith(".txt") || isImageFile(sourceFile)) {
                String originalName = sourceFile.getName();
                String cleanName = UniqueFileNameHandler.removeTimestamp(originalName);
                String originalExt = getOriginalExtension(originalName);

                int lastDotIndex = cleanName.lastIndexOf(".");
                if (lastDotIndex > 0) {
                    cleanName = cleanName.substring(0, lastDotIndex);
                }

                // 自动裁剪超长文件名
                cleanName = getSafeCoreName(cleanName);

                String randomStr = UniqueFileNameHandler.generateRandomString();
                String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

                if (baseTimestamp.length() >= 12) {
                    String datePart = baseTimestamp.substring(0, 8);
                    String timeRemaining = baseTimestamp.substring(12);
                    String sequenceStr = String.format(Locale.getDefault(), "%04d", sequenceNumber % 10000);
                    String newTimestamp = datePart + sequenceStr + timeRemaining;
                    String timestampSuffix = "_" + randomStr + "_" + newTimestamp;

                    // ==============================
                    // 🔥 【正确】只在目标文件夹查重主体名
                    // ==============================
                    String finalCoreName = getNonConflictCoreNameInFolder(targetFolder, cleanName);
                    String uniqueFileName = finalCoreName + timestampSuffix;

                    String finalFileName = removeAllExtensions(uniqueFileName) + originalExt;

                    File targetFile = new File(targetFolder, finalFileName);
                    if (!copyFileContent(sourceFile, targetFile)) {
                        return false;
                    }
                    sequenceNumber++;
                } else {
                    Log.w("CopyFolder", "时间戳格式异常，使用默认命名");
                    String timestampSuffix = "_" + randomStr + "_" + baseTimestamp;

                    // ==============================
                    // 🔥 【正确】只在目标文件夹查重主体名
                    // ==============================
                    String finalCoreName = getNonConflictCoreNameInFolder(targetFolder, cleanName);
                    String uniqueFileName = finalCoreName + timestampSuffix;

                    String finalFileName = removeAllExtensions(uniqueFileName) + originalExt;

                    File targetFile = new File(targetFolder, finalFileName);
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
     * 移除名称中所有后缀，仅保留纯核心名（防重复加.txt）
     */
    private String removeAllExtensions(String name) {
        int lastDot = name.lastIndexOf(".");
        if (lastDot > 0 && (name.length() - lastDot - 1) <= 4 && name.substring(lastDot + 1).indexOf(".") == -1) {
            String afterDot = name.substring(lastDot + 1).toLowerCase();
            if (afterDot.equals("txt") || afterDot.equals("png") || afterDot.equals("jpg") ||
                    afterDot.equals("jpeg") || afterDot.equals("gif") || afterDot.equals("bmp")) {
                return name.substring(0, lastDot);
            }
        }
        return name;
    }

    /**
     * 提取文件原始后缀（小写，仅保留1个）
     */
    private String getOriginalExtension(String fileName) {
        String ext = "";
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            ext = fileName.substring(lastDot).toLowerCase(); // 小写统一格式，避免大小写问题
        }
        return ext;
    }


    // ==============================
// 【核心统一工具】获取不重复的主体名（只在目标文件夹查重）
// ==============================
    private String getNonConflictCoreNameInFolder(File targetFolder, String baseCore) {
        if (targetFolder == null || !targetFolder.exists() || baseCore == null) {
            return baseCore;
        }

        // 不重复直接返回
        if (!isCoreNameExistsInFolder(targetFolder, baseCore)) {
            return baseCore;
        }

        // 重复自动加 (1)(2)...
        int index = 1;
        while (true) {
            String testName = baseCore + "(" + index + ")";
            if (!isCoreNameExistsInFolder(targetFolder, testName)) {
                return testName;
            }
            index++;
        }
    }

    // ==============================
// 判断：主体名 是否在 目标文件夹 已存在
// ==============================
    private boolean isCoreNameExistsInFolder(File folder, String coreName) {
        if (folder == null || !folder.exists() || coreName == null) {
            return false;
        }

        File[] files = folder.listFiles();
        if (files == null) return false;

        for (File f : files) {
            if (f.isFile()) {
                String existCore = extractPureCoreNameForDisplay(f.getName());
                if (coreName.equals(existCore)) {
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * 解析文件名中的随机串+时间戳结构
     * 返回数组：[0]纯核心名, [1]随机串, [2]时间戳1, [3]时间戳2
     * 示例：风景_abc_123456_789012.png → ["风景", "abc", "123456", "789012"]
     * 示例：笔记_xyz_987654.txt → ["笔记", "xyz", "987654", null]
     * 示例：文档.png → ["文档", null, null, null]
     */
    private String[] parseFileName(String fileName) {
        // 步骤1：去掉后缀
        String nameWithoutExt = removeAllExtensions(fileName);
        String[] parts = new String[4];
        parts[0] = nameWithoutExt;
        parts[1] = null;
        parts[2] = null;
        parts[3] = null;
        Pattern pattern = Pattern.compile("_([a-zA-Z0-9]+)_(\\d+)(_?(\\d+))?$");
        Matcher matcher = pattern.matcher(nameWithoutExt);
        if (matcher.find()) {
            parts[0] = nameWithoutExt.substring(0, matcher.start());
            parts[1] = matcher.group(1);
            parts[2] = matcher.group(2);
            if (matcher.group(4) != null) {
                parts[3] = matcher.group(4);
            }
        }
        return parts;
    }


    /**
     * 生成新的时间戳后缀（按剪切规则）
     */
    private String generateNewTimestampSuffix(String[] parsedParts) {
        String randomStr = parsedParts[1];
        String ts1 = parsedParts[2];
        String ts2 = parsedParts[3];
        String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

        if (randomStr == null || ts1 == null) {
            randomStr = UniqueFileNameHandler.generateRandomString();
            return "_" + randomStr + "_" + newTimestamp;
        } else if (ts2 == null) {
            return "_" + randomStr + "_" + ts1 + "_" + newTimestamp;
        } else {
            return "_" + randomStr + "_" + ts1 + "_" + newTimestamp;
        }
    }
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
        String targetPath = currentDirectory.getAbsolutePath();
        if (isCutOperation && copiedFile.isDirectory() &&
                targetPath.startsWith(copiedFile.getAbsolutePath() + File.separator)) {
            Toast.makeText(this, "无法剪切到子目录，避免循环嵌套", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            return;
        }

        // 🔥 粘贴提示框（统一风格，不可取消）
        final ProgressDialog pasteDialog = new ProgressDialog(this);
        pasteDialog.setMessage("正在处理中，请稍候...");
        pasteDialog.setCanceledOnTouchOutside(false);
        pasteDialog.setCancelable(false);
        runOnUiThread(pasteDialog::show);

        new Thread(() -> {
            boolean success = false;
            try {
                if (copiedFile.isDirectory()) {
                    if (isCutOperation) {
                        success = moveFolderWithTxtUpdate(copiedFile, currentDirectory, false);
                    } else {
                        success = copyFolderWithTxtGlobalCheck(copiedFile, currentDirectory);
                    }
                } else {
                    File sourceFile = copiedFile;
                    boolean isTxt = sourceFile.getName().toLowerCase().endsWith(".txt");
                    boolean isImg = isImageFile(sourceFile);

                    if (isTxt || isImg) {
                        String originalName = sourceFile.getName();
                        String originalExt = getOriginalExtension(originalName);
                        String cleanName = removeAllExtensions(originalName);

                        String randomStr = UniqueFileNameHandler.generateRandomString();
                        String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
                        String timestampSuffix;

                        if (isCutOperation) {
                            String[] parsed = parseFileName(originalName);
                            String pureCoreName = parsed[0];
                            pureCoreName = getSafeCoreName(pureCoreName);
                            timestampSuffix = generateNewTimestampSuffix(parsed);
                            cleanName = pureCoreName;
                        } else {
                            cleanName = UniqueFileNameHandler.removeTimestamp(cleanName);
                            cleanName = getSafeCoreName(cleanName);

                            if (baseTimestamp.length() >= 12) {
                                String datePart = baseTimestamp.substring(0, 8);
                                String timeRemaining = baseTimestamp.substring(12);
                                String sequenceStr = "0000";
                                String newTimestamp = datePart + sequenceStr + timeRemaining;
                                timestampSuffix = "_" + randomStr + "_" + newTimestamp;
                            } else {
                                timestampSuffix = "_" + randomStr + "_" + baseTimestamp;
                            }
                        }

                        // 主体名查重
                        String finalCoreName = getNonConflictCoreNameInFolder(currentDirectory, cleanName);
                        String uniqueFileName = finalCoreName + timestampSuffix;
                        String finalFileName = removeAllExtensions(uniqueFileName) + originalExt;
                        File targetFile = new File(currentDirectory, finalFileName);

                        if (isCutOperation) {
                            success = sourceFile.renameTo(targetFile);
                            if (!success) {
                                if (copyFileContent(sourceFile, targetFile)) {
                                    sourceFile.delete();
                                    success = true;
                                }
                            }
                        } else {
                            success = copyFileContent(sourceFile, targetFile);
                        }
                    } else {
                        File targetFile = new File(currentDirectory, copiedFile.getName());
                        File uniqueTargetFile = getNonConflictFile(targetFile);
                        if (isCutOperation) {
                            success = copiedFile.renameTo(uniqueTargetFile);
                            if (!success) {
                                if (copyFileContent(copiedFile, uniqueTargetFile)) {
                                    copiedFile.delete();
                                    success = true;
                                }
                            }
                        } else {
                            success = copyFileContent(copiedFile, uniqueTargetFile);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }

            // 结束后关闭提示
            boolean finalSuccess = success;
            runOnUiThread(() -> {
                if (pasteDialog.isShowing()) {
                    pasteDialog.dismiss();
                }
                if (finalSuccess) {
                    Toast.makeText(this, (isCutOperation ? "剪切" : "复制") + "成功", Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "操作失败", Toast.LENGTH_SHORT).show();
                }
                hidePasteButton();
            });
        }).start();
    }
    private boolean moveFolderWithTxtUpdate(File sourceDir, File targetParentDir, boolean isInnerMove) {
        if (sourceDir == null || !sourceDir.exists() || targetParentDir == null) {
            return false;
        }

        try {
            // 文件夹名称安全裁剪
            String folderName = getSafeCoreName(sourceDir.getName());
            File targetDir = new File(targetParentDir, folderName);

            // 重名处理
            if (targetDir.exists()) {
                String uniqueName = getUniqueFolderName(targetParentDir, targetDir.getName());
                targetDir = new File(targetParentDir, uniqueName);
            }

            if (!targetDir.mkdirs()) {
                return false;
            }

            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // 递归处理子文件夹
                        moveFolderWithTxtUpdate(file, targetDir, true);
                    } else {
                        boolean isTxt = file.getName().toLowerCase().endsWith(".txt");
                        boolean isImg = isImageFile(file);

                        if (isTxt || isImg) {
                            String originalName = file.getName();
                            String originalExt = getOriginalExtension(originalName);
                            String[] parsed = parseFileName(originalName);
                            String pureCoreName = parsed[0];

                            // 超长文件名自动裁剪
                            pureCoreName = getSafeCoreName(pureCoreName);

                            // 你自己的时间戳规则
                            String timestampSuffix = generateNewTimestampSuffix(parsed);
                            String cleanName = pureCoreName;

                            // ==============================
// 🔥 【正确逻辑】只在目标文件夹查重主体名
// ==============================
                            String finalCoreName = getNonConflictCoreNameInFolder(targetDir, cleanName);
                            String uniqueFileName = finalCoreName + timestampSuffix;

                            String finalFileName = removeAllExtensions(uniqueFileName) + originalExt;
                            File targetFile = new File(targetDir, finalFileName);

                            // 执行剪切
                            if (!file.renameTo(targetFile)) {
                                if (copyFileContent(file, targetFile)) {
                                    file.delete();
                                }
                            }
                        } else {
                            // 普通文件
                            File target = new File(targetDir, file.getName());
                            File safeTarget = getNonConflictFile(target);
                            if (!file.renameTo(safeTarget)) {
                                copyFileContent(file, safeTarget);
                                file.delete();
                            }
                        }
                    }
                }
            }

            // ====================== 【修复点：递归删除所有原文件夹】 ======================
            // 不管是不是内层，全部递归删除 → 彻底清空源目录
            deleteFolderTree(sourceDir);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ====================== 【新增：递归删除文件夹工具方法】 ======================
    private void deleteFolderTree(File fileOrFolder) {
        if (fileOrFolder.isDirectory()) {
            File[] children = fileOrFolder.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFolderTree(child);
                }
            }
        }
        fileOrFolder.delete();
    }
    // 剪切文件夹：复制成功后删除原文件夹
    private void deleteFolderAfterCopy(File folder) {
        if (folder == null || !folder.exists()) return;
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteFolderAfterCopy(file);
            }
        }
        folder.delete();
    }
    // 统一规则：裁剪主体名称，保留后缀、随机串、时间戳
    private String getSafeCoreName(String coreName) {
        final int MAX_LENGTH = 45;
        if (coreName.length() > MAX_LENGTH) {
            return coreName.substring(0, MAX_LENGTH);
        }
        return coreName;
    }
    /**
     * 仅在当前目录查重生成唯一文件夹名（剪切专用）
     * 规则：仅当目标目录已存在同名文件夹时，加(1)/(2)序号，否则返回原名
     */
    private String getUniqueFolderNameInCurrentDir(File targetParent, String folderName) {
        File tempFolder = new File(targetParent, folderName);
        if (!tempFolder.exists()) {
            return folderName;
        }
        int counter = 1;
        String newFolderName;
        do {
            newFolderName = folderName + "(" + counter + ")";
            tempFolder = new File(targetParent, newFolderName);
            counter++;
        } while (tempFolder.exists());
        return newFolderName;
    }
    /**
     * 移动文件夹并更新其中TXT/图片文件的时间戳，处理重名和复制失败的降级逻辑
     */
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

                // 超长文件名自动裁剪
                cleanName = getSafeCoreName(cleanName);

                String randomStr = UniqueFileNameHandler.generateRandomString();
                String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

                if (baseTimestamp.length() >= 12) {
                    String datePart = baseTimestamp.substring(0, 8);
                    String timeRemaining = baseTimestamp.substring(12);
                    String sequenceStr = String.format(Locale.getDefault(), "%04d", currentSequence % 10000);
                    String newTimestamp = datePart + sequenceStr + timeRemaining;
                    String timestampSuffix = "_" + randomStr + "_" + newTimestamp;

                    // ==============================
                    // 🔥 【正确】只在目标文件夹查重主体名
                    // ==============================
                    String finalCoreName = getNonConflictCoreNameInFolder(targetFolder, cleanName);
                    String uniqueFileName = finalCoreName + timestampSuffix + ".txt";

                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (copyFileContent(sourceFile, targetFile)) {
                        currentSequence++;
                    }
                } else {
                    Log.w("CopySubFolder", "时间戳格式异常，使用默认命名");
                    String timestampSuffix = "_" + randomStr + "_" + baseTimestamp;

                    // ==============================
                    // 🔥 【正确】只在目标文件夹查重主体名
                    // ==============================
                    String finalCoreName = getNonConflictCoreNameInFolder(targetFolder, cleanName);
                    String uniqueFileName = finalCoreName + timestampSuffix + ".txt";

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
     * 修复：确保方法是 private（Activity 内可访问），参数为 File，返回 String
     * 剪切专用：当前目录生成唯一文件名（不加时间戳）
     */
    private String getUniqueFileNameInDir(File targetFile) {
        String fileName = targetFile.getName();
        // 修复：调用正确的后缀处理方法
        String coreName = removeAllExtensions(fileName);
        String ext = getOriginalExtension(fileName);
        int count = 1;
        String newFileName = coreName + "(" + count + ")" + ext;
        File newFile = new File(targetFile.getParentFile(), newFileName);

        while (newFile.exists()) {
            count++;
            newFileName = coreName + "(" + count + ")" + ext;
            newFile = new File(targetFile.getParentFile(), newFileName);
        }
        return newFileName;
    }
    /**
     * 删除空目录（仅当目录为空时执行删除）
     */
    private boolean deleteEmptyDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null && files.length == 0) {
                return dir.delete();
            }
        }
        return false;
    }
    /**
     * 统一更新文件名的时间戳（TXT/图片通用，避免乱码）
     * @param originalFile 原文件
     * @return 带新时间戳的文件名（核心名+随机串+时间戳+原后缀）
     */
    private String updateFileNameWithTimestamp(File originalFile) {
        String originalName = originalFile.getName();
        boolean isTxtFile = originalName.toLowerCase().endsWith(".txt");
        boolean isImageFile = isImageFile(originalFile);
        String coreName = originalName;
        String originalExt = "";
        int lastDot = originalName.lastIndexOf(".");
        if (lastDot > 0) {
            coreName = originalName.substring(0, lastDot);
            originalExt = originalName.substring(lastDot);
        }
        Matcher incrementMatcher = INCREMENT_TIMESTAMP_PATTERN.matcher(coreName);
        if (incrementMatcher.find()) {
            coreName = coreName.replace(incrementMatcher.group(), "");
        }
        Matcher targetMatcher = TARGET_TIMESTAMP_PATTERN.matcher(coreName);
        if (targetMatcher.find()) {
            coreName = coreName.replace(targetMatcher.group(), "");
        }
        coreName = coreName.replaceAll("_+$", "");
        String randomStr = generateRandomString();
        String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
        String timestampSuffix = "_" + randomStr + "_" + newTimestamp;
        return coreName + timestampSuffix + originalExt;
    }

    /**
     * 统一生成唯一文件名（避免重名，通用）
     * @param targetDir 目标目录
     * @param fileName 初始文件名
     * @return 唯一文件名
     */
    private String getUniqueFileName统一(File targetDir, String fileName) {
        String uniqueName = fileName;
        int count = 1;
        while (new File(targetDir, uniqueName).exists()) {
            String core = uniqueName;
            String ext = "";
            int lastDot = uniqueName.lastIndexOf(".");
            if (lastDot > 0) {
                core = uniqueName.substring(0, lastDot);
                ext = uniqueName.substring(lastDot);
            }
            uniqueName = core + "(" + count + ")" + ext;
            count++;
        }
        return uniqueName;
    }
    /**
     * 处理文件名加时间戳（区分TXT/图片，避免图片加.txt）
     * @param originalName 原文件名
     * @param isImageFile 是否为图片文件（true=图片，false=TXT）
     * @return 带时间戳的新文件名（图片不加.txt，TXT加.txt）
     */
    private String processTxtForCutOperation(String originalName, boolean isImageFile) {
        String coreName = originalName;
        int lastDot = originalName.lastIndexOf(".");
        if (lastDot > 0) {
            coreName = originalName.substring(0, lastDot);
        }
        String randomStr = UniqueFileNameHandler.generateRandomString();
        String newTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
        String timestampSuffix = "_" + randomStr + "_" + newTimestamp;
        if (isImageFile) {
            return coreName + timestampSuffix;
        } else {
            return coreName + timestampSuffix + ".txt";
        }
    }
    private String processTxtForCutOperation(String originalName) {
        return processTxtForCutOperation(originalName, false);
    }
    // 通过文件名判断是否为图片
    private boolean isImageFileByName(String fileName) {
        String lowerFileName = fileName.toLowerCase();
        for (String ext : IMAGE_EXTENSIONS) {
            if (lowerFileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
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
     * 移动文件并更新TXT/图片文件的时间戳，重命名失败时降级为复制+删除
     */
    private boolean moveFileWithTimestampUpdate(File sourceFile, File targetFile) throws IOException {
        // 统一生成带时间戳的文件名
        String newFileName = updateFileNameWithTimestamp(sourceFile);
        File finalTargetFile = new File(targetFile.getParentFile(), newFileName);
        // 处理重名
        if (finalTargetFile.exists()) {
            newFileName = getUniqueFileName统一(finalTargetFile.getParentFile(), newFileName);
            finalTargetFile = new File(targetFile.getParentFile(), newFileName);
        }
        // 执行移动
        if (sourceFile.renameTo(finalTargetFile)) {
            return true;
        } else {
            // 降级复制
            boolean copySuccess = copyFileContent(sourceFile, finalTargetFile);
            if (copySuccess) {
                sourceFile.delete();
            }
            return copySuccess;
        }
    }
    /**
     * 复制文件并生成唯一名称，处理TXT文件的特殊命名规则

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
     */



    /**
     * 使用Glide加载图片缩略图，优化加载性能
     */
    private void loadImageThumbnail(File imageFile, ImageView imageView) {
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
     * 返回键处理（流畅滑动+底部精准定位版）：
     * 1. 快速平滑滑动（从顶部往下滑），解决卡顿问题；
     * 2. 最后几个文件夹（≤6个到末尾）直接滑到底部；
     * 3. 中间文件夹精准定位到第6位，无卡顿/漂移；
     * 4. 新增：滑动时长固定0.5秒 + 前排显示不滑动
     */
    @Override
    public void onBackPressed() {
        hideCustomPathTip(); // 新增：返回时隐藏提示
        final File targetFileForScroll = currentDirectory;
        boolean needScroll = true;
        final int TARGET_VISUAL_POS = 5;

        // =========================
        // 🔥 核心修复：搜索模式下，直接回到【搜索前的文件夹】，而不是当前目录
        // =========================
        if (isInSearchMode) {
            isInSearchMode = false;
            etSearch.setText("");
            etSearch.clearFocus();
            fileAdapter.setData(fileList);
            clearSearchKeyword();
            Toast.makeText(this, "已退出搜索", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            PreferenceUtils.saveLastPageType(this, "main");

            // 🔥 退出搜索 → 恢复搜索前的文件夹（所有位置都生效）
            if (currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }

            // 退出搜索后不再执行后续逻辑
            needScroll = false;

            // 直接刷新列表，回到搜索前所在的文件夹
            loadFileList();
            updateLevelHint();
            return; // 关键：直接return，不往下走
        }

        if (isInTransferStation) {
            if (currentDirectory != null && !currentDirectory.equals(transferStationDirectory)) {
                currentDirectory = currentDirectory.getParentFile();
                loadFileList();
                updateLevelHint();
            } else {
                exitTransferStationToHome();
                needScroll = false;
            }
        } else if (isInRecycleBin) {
            if (currentDirectory != null && !currentDirectory.equals(recycleBinDirectory)) {
                currentDirectory = currentDirectory.getParentFile();
                loadFileList();
                updateLevelHint();
            } else {
                exitRecycleBinToHome();
                needScroll = false;
            }
        } else if (currentDirectory != null && !currentDirectory.getName().equals(ROOT_FOLDER_NAME)) {
            final File childFolder = currentDirectory;
            currentDirectory = currentDirectory.getParentFile();
            etSearch.clearFocus();
            loadFileList();
            fileAdapter.notifyDataSetChanged();
            PreferenceUtils.saveLastPageType(this, "main");
            if (currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }
            fileRecyclerView.postDelayed(() -> smoothScrollToTarget(childFolder, TARGET_VISUAL_POS), 200);
            needScroll = false;
        } else {
            super.onBackPressed();
            needScroll = false;
        }
        if (needScroll && targetFileForScroll != null) {
            fileRecyclerView.postDelayed(() -> smoothScrollToTarget(targetFileForScroll, TARGET_VISUAL_POS), 200);
        }
    }
    /**
     * 流畅滑动定位（核心优化：固定0.1秒滑动+前排显示不滑动+底部精准定位）
     * @param targetFile 目标文件夹
     * @param targetVisualPos 视觉第6位（索引5）
     */
    private void smoothScrollToTarget(final File targetFile, final int targetVisualPos) {
        if (targetFile == null || fileRecyclerView == null || fileAdapter == null || isInSearchMode) {
            return;
        }
        final List<File> currentFileList = fileList;
        if (currentFileList == null || currentFileList.isEmpty()) {
            return;
        }
        int targetAbsPos = -1;
        for (int i = 0; i < currentFileList.size(); i++) {
            if (currentFileList.get(i).getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                targetAbsPos = i;
                break;
            }
        }
        if (targetAbsPos == -1) return;
        final LinearLayoutManager layoutManager = (LinearLayoutManager) fileRecyclerView.getLayoutManager();
        if (layoutManager == null) return;
        final int totalItemCount = currentFileList.size();
        final int finalTargetAbsPos = targetAbsPos;
        if (isItemFullyVisible(layoutManager, finalTargetAbsPos)) {
            return;
        }
        if (finalTargetAbsPos <= targetVisualPos) {
            return;
        }
        if (finalTargetAbsPos >= totalItemCount - (targetVisualPos + 1)) {
            scrollToBottom(layoutManager, totalItemCount);
            return;
        }
        final int scrollToAbsPos = finalTargetAbsPos - targetVisualPos;
        FixedDurationSmoothScroller smoothScroller = new FixedDurationSmoothScroller(this);
        smoothScroller.setTargetPosition(scrollToAbsPos);
        layoutManager.startSmoothScroll(smoothScroller);
        fileRecyclerView.setItemViewCacheSize(10);
        fileRecyclerView.postDelayed(() -> {
            fileRecyclerView.setItemViewCacheSize(20);
        }, 300);
    }
    /**
     * 判断Item是否完全显示在屏幕内
     */
    private boolean isItemFullyVisible(LinearLayoutManager layoutManager, int position) {
        int visiblePos = layoutManager.findFirstCompletelyVisibleItemPosition();
        int lastVisiblePos = layoutManager.findLastCompletelyVisibleItemPosition();
        return position >= visiblePos && position <= lastVisiblePos;
    }
    private void scrollToBottom(final LinearLayoutManager layoutManager, final int totalItemCount) {
        FixedDurationSmoothScroller bottomScroller = new FixedDurationSmoothScroller(this);
        bottomScroller.setTargetPosition(totalItemCount - 1);
        layoutManager.startSmoothScroll(bottomScroller);
    }
    /**
     * 自定义固定时长SmoothScroller（强制0.1秒滑动到位）
     */
    private static class FixedDurationSmoothScroller extends androidx.recyclerview.widget.LinearSmoothScroller {
        private static final int FIXED_SCROLL_DURATION = 100;

        public FixedDurationSmoothScroller(Context context) {
            super(context);
        }
        @Override
        protected int calculateTimeForScrolling(int dx) {
            return FIXED_SCROLL_DURATION;
        }
        @Override
        protected int getVerticalSnapPreference() {
            return SNAP_TO_START;
        }
        @Override
        protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
            return 50f / displayMetrics.densityDpi;
        }
    }


    // 打开时恢复崩溃的文件
    private void recoverFromCrash() {
        File rootDir = new File(getFilesDir(), "主页根目录");
        if (!rootDir.exists()) return;
        // 扫描并恢复备份/临时文件（逻辑和FileEditorActivity里的recoverFromCrash()一致）
        File[] files = rootDir.listFiles((dir, name) ->
                name.contains("_atomic_tmp_") || name.endsWith("_backup")
        );
        if (files == null || files.length == 0) return;
        for (File file : files) {
            String fileName = file.getName();
            if (fileName.contains("_atomic_tmp_")) {
                String originalFileName = fileName.split("_atomic_tmp_")[0];
                File originalFile = new File(rootDir, originalFileName);
                if (originalFile.exists()) originalFile.delete();
                file.renameTo(originalFile);
                Toast.makeText(this, "恢复未保存的文件：" + originalFileName, Toast.LENGTH_LONG).show();
            } else if (fileName.endsWith("_backup")) {
                String originalFileName = fileName.replace("_backup", "");
                File originalFile = new File(rootDir, originalFileName);
                if (!originalFile.exists()) {
                    file.renameTo(originalFile);
                    Toast.makeText(this, "恢复损坏的文件：" + originalFileName, Toast.LENGTH_LONG).show();
                } else {
                    file.delete();
                }
            }
        }
    }

}