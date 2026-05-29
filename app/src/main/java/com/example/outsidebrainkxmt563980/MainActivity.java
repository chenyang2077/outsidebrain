/*
软件名称：流动文档软件V1.0
版本号：V1.0
功能描述：1. 基础文件管理：支持TXT文件/文件夹整理、ZIP压缩解压、文件/文件夹复制与移动；
        2. 文件浏览编辑：支持TXT文件/图片浏览、TXT文件编辑；
        3. 辅助功能：文件分享至第三方APP、按文件名/TXT内容检索文件。
所属模块：主界面模块
开发语言：Java
源码状态：完整未删减
*/
package com.example.outsidebrainkxmt563980;
import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import android.provider.Settings;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
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
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
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
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;
import android.icu.text.Transliterator;
import java.util.HashMap;
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
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("^(.*?)\\((\\d+)\\)$");
    private static final Pattern SINGLE_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private static final Pattern MULTI_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}(_\\d{17})+");
    private View pasteButton;
    private Toast mPathToast;
    private View mTouchOverlay;
    private View mCustomTipView;
    private boolean mIsTipShowing = false;
    private boolean isJumping = false;
    private View mReusableTipView;
    private long lastTipUpdateTime = 0;
    private String lastTipText = "";
    private boolean isZipCompressing = false;
    private FrameLayout zipContainer;
    private View btnZip;
    private ProgressDialog mLoadingDialog;
    private static final int FILE_COUNT_LIMIT = 100;
    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp"};
    /**1·页面创建初始化：
     * 1. 绑定布局控件，初始化视图组件；2. 设置夜间模式、文件列表适配器；3. 初始化回收站/中转站目录； 4. 设置搜索框/菜单按钮/新增按钮点击事件； 5. 检查存储权限，初始化根目录。
     **/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);
        fileRecyclerView = findViewById(R.id.file_list);
        preEditFileBtn = findViewById(R.id.add_button);
        ImageButton menuButton = findViewById(R.id.menu_btn);
        menuButton.setOnClickListener(v -> showPopupMenu(v));
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
        zipContainer = findViewById(R.id.zip_container);
        btnZip = getLayoutInflater().inflate(R.layout.btn_zip, zipContainer, false);
        zipContainer.addView(btnZip);
        btnZip.setVisibility(View.GONE);
        btnZip.setOnClickListener(v -> {
            if (searchResultList == null || searchResultList.isEmpty()) {
                Toast.makeText(this, "没有可压缩的文件", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("压缩确认")
                    .setMessage("确定要将搜索到的文件压缩成 zip 吗？")
                    .setPositiveButton("确定", (dialog, which) -> {
                        dialog.dismiss();
                        zipSearchResultsToCurrentDir(etSearch.getText().toString().trim(), searchResultList);
                    })
                    .setNegativeButton("取消", (dialog, which) -> {
                        dialog.dismiss();
                    })
                    .show();
        });
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
        mTouchOverlay = new View(this);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        mTouchOverlay.setLayoutParams(params);
        mTouchOverlay.setBackgroundColor(Color.TRANSPARENT);
        ((ViewGroup) getWindow().getDecorView()).addView(mTouchOverlay);
        mTouchOverlay.setVisibility(View.GONE);
        mTouchOverlay.setOnTouchListener((v, event) -> {
            if (mPathToast != null) {
                mPathToast.cancel();
                mPathToast = null;
            }
            mTouchOverlay.setVisibility(View.GONE);
            return false;
        });
    }

    /**
     * 2·文件列表RecyclerView适配器，处理不同文件类型的显示逻辑
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
            int defaultIconSize = dp2px(holder.itemView.getContext(), 24);
            ViewGroup.LayoutParams params = holder.ivIcon.getLayoutParams();
            params.width = defaultIconSize;
            params.height = defaultIconSize;
            holder.ivIcon.setLayoutParams(params);
            if (file.isDirectory()) {
                holder.itemView.setBackgroundResource(R.drawable.item_folder_rounded_bg);
                int iconSize = dp2px(holder.itemView.getContext(), 36);
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
            holder.tvName.setText(displayFileName);
            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    if (copiedFile != null && file.equals(copiedFile)) {
                        hidePasteButton();
                        Toast.makeText(MainActivity.this, "禁止粘贴在所选文件夹内部", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (isInSearchMode) {
                        isInSearchMode = false;
                        etSearch.setText("");
                        if (btnZip != null) btnZip.setVisibility(View.GONE);
                        etSearch.clearFocus();
                        clearSearchKeyword();
                        hideCustomPathTip();
                        hidePasteButton();
                        currentDirectory = file;
                        loadFileList();
                        updateLevelHint();
                        PreferenceUtils.saveLastPageType(MainActivity.this, "main");
                        return;
                    }
                    File[] tempFiles = file.listFiles();
                    int totalCount = (tempFiles != null) ? tempFiles.length : 0;
                    if (totalCount <= FILE_COUNT_LIMIT) {
                        currentDirectory = file;
                        loadFileList();
                        updateLevelHint();
                    } else {
                        currentDirectory = file;
                        fileList.clear();
                        fileAdapter.setData(fileList);
                        updateLevelHint();
                        mLoadingDialog = new ProgressDialog(MainActivity.this);
                        mLoadingDialog.setMessage("正在加载文件...");
                        mLoadingDialog.setCancelable(false);
                        mLoadingDialog.show();
                        new Thread(() -> loadFileList()).start();
                    }
                    PreferenceUtils.saveLastPageType(MainActivity.this, "main");
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, file.getAbsolutePath());
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, "");
                    PreferenceUtils.saveLastViewedImage(MainActivity.this, "");
                } else if (file.getName().toLowerCase().endsWith(".txt") && isTextFile(file)) {
                    hidePasteButton();
                    PreferenceUtils.saveLastEditedFile(MainActivity.this, file.getAbsolutePath());
                    PreferenceUtils.saveLastFolderPath(MainActivity.this, file.getParentFile().getAbsolutePath());
                    if (isInSearchMode) {
                        String showPath;
                        String fullPath = file.getAbsolutePath();
                        if (isInTransferStation) {
                            String basePath = transferStationDirectory.getAbsolutePath();
                            showPath = fullPath.replace(basePath, "");
                        } else if (isInRecycleBin) {
                            String basePath = getFilesDir().getAbsolutePath();
                            showPath = fullPath.replace(basePath, "");
                        } else {
                            String basePath = rootDirectory.getAbsolutePath();
                            showPath = fullPath.replace(basePath, "");
                        }
                        if (showPath.startsWith(File.separator)) {
                            showPath = showPath.substring(1);
                        }
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
                }else if (isZipFile(file)) {
                    showZipExtractDialog(file);
                } else if (isImageFile(file)) {
                    hidePasteButton();
                    if (isInSearchMode) {
                        String showPath;
                        String fullPath = file.getAbsolutePath();
                        if (isInTransferStation) {
                            String basePath = transferStationDirectory.getAbsolutePath();
                            showPath = fullPath.replace(basePath, "");
                        } else if (isInRecycleBin) {
                            String basePath = getFilesDir().getAbsolutePath();
                            showPath = fullPath.replace(basePath, "");
                        } else {
                            String basePath = rootDirectory.getAbsolutePath();
                            showPath = fullPath.replace(basePath, "");
                        }
                        if (showPath.startsWith(File.separator)) {
                            showPath = showPath.substring(1);
                        }
                        MainActivity.this.showCustomPathTip(showPath);
                    }
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
        class FileViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            public FileViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.icon);
                tvName = itemView.findViewById(R.id.name);
            }
        }
        private int dp2px(Context context, float dp) {
            return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
        }
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
     * 显示可点击的路径提示，点击跳转到对应文件夹
     */
    private void showCustomPathTip(String fullRelativePath) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastTipUpdateTime < 50) {
            return;
        }
        lastTipUpdateTime = currentTime;
        hideCustomPathTip();
        String folderPath;
        String fileName;
        int lastSepIndex = fullRelativePath.lastIndexOf(File.separator);
        if (lastSepIndex == -1) {
            folderPath = "";
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
        if (tipText.equals(lastTipText)) {
            return;
        }
        lastTipText = tipText;
        if (mReusableTipView == null) {
            mReusableTipView = LayoutInflater.from(this).inflate(R.layout.layout_custom_tip, null);
        }
        mCustomTipView = mReusableTipView;
        TextView tvTip = mCustomTipView.findViewById(R.id.tv_custom_tip);
        tvTip.setText(tipText);
        View tipContainer = mCustomTipView.findViewById(R.id.tip_container);
        tipContainer.setOnClickListener(v -> {
            if (isJumping) {
                return;
            }
            isJumping = true;
            ProgressDialog jumpDialog = new ProgressDialog(MainActivity.this);
            jumpDialog.setMessage("正在跳转...");
            jumpDialog.setCancelable(false);
            jumpDialog.show();
            hideCustomPathTip();
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    File baseDir;
                    if (isInTransferStation) {
                        baseDir = transferStationDirectory;
                    } else if (isInRecycleBin) {
                        baseDir = getFilesDir();
                    } else {
                        baseDir = rootDirectory;
                    }
                    File targetFile = new File(baseDir, fullRelativePath);
                    File targetFolder = targetFile.getParentFile();

                    if (targetFolder != null && targetFolder.exists() && targetFolder.isDirectory()) {
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
                    Toast.makeText(MainActivity.this, "跳转失败", Toast.LENGTH_SHORT).show();
                } finally {
                    isJumping = false;
                    if (jumpDialog.isShowing()) {
                        jumpDialog.dismiss();
                    }
                }
            });
        });
        ViewGroup rootView = (ViewGroup) getWindow().getDecorView();
        int screenWidth = rootView.getWidth();
        int bottomMargin = dp2px(80);
        int tipWidth = screenWidth * 2 / 3;
        int tipHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
        int left = screenWidth - tipWidth;
        int top = rootView.getHeight() - bottomMargin - mCustomTipView.getMeasuredHeight();

        mCustomTipView.setX(left);
        mCustomTipView.setY(top);

        if (mCustomTipView.getParent() != null) {
            ((ViewGroup) mCustomTipView.getParent()).removeView(mCustomTipView);
        }
        rootView.addView(mCustomTipView);
        ViewGroup.LayoutParams params = mCustomTipView.getLayoutParams();
        params.width = tipWidth;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        mCustomTipView.setLayoutParams(params);

        mIsTipShowing = true;
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
            mIsTipShowing = false;
        }
    }
    private int dp2px(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 导航至根目录：1. 退出搜索模式，清空搜索框； 2. 重置回收站/中转站标识； 3. 加载根目录文件列表，更新层级提示； 4. 保存页面状态，延迟修正TXT文件路径。
     */
    private void navigateToRootDirectory() {
        if (rootDirectory != null && rootDirectory.exists() && rootDirectory.isDirectory()) {
            if (isInSearchMode) {
                isInSearchMode = false;
                etSearch.setText("");
                if (btnZip != null) btnZip.setVisibility(View.GONE);
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
        File historyDir = new File(recycleBinDirectory, "0.修改历史版本");
        if (!historyDir.exists()) {
            historyDir.mkdirs();
            Log.d("RecycleBin", "自动重建修改历史版本文件夹");
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
     * 提取文件名开头的多级数字序号（支持任意层级小数点）
     * @param fileName 文件名
     * @return 数字列表（如"1.25.5.25文件夹" → [1,25,5,25]，无数字则返回空列表）
     */
    private List<Long> extractMultiLevelNumberFromName(String fileName) {
        List<Long> numList = new ArrayList<>();
        if (fileName == null || fileName.isEmpty()) {
            return numList;
        }
        Pattern pattern = Pattern.compile("^([0-9]+([.、][0-9]*)*)");
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            String numStr = matcher.group(1);
            boolean hasValidSymbol = numStr.contains(".") || numStr.contains("、");
            if (!hasValidSymbol) {
                return numList;
            }
            numStr = numStr.replace('、', '.');
            String[] numParts = numStr.split("\\.");
            for (String part : numParts) {
                try {
                    if (!part.isEmpty()) {
                        numList.add(Long.parseLong(part));
                    }
                } catch (NumberFormatException e) {
                    break;
                }
            }
        }
        return numList;
    }
    /**
     * 中文数字映射 & 解析
     */
    private final Map<String, Integer> CN_NUM_MAP = new HashMap<>();
    {
        CN_NUM_MAP.put("零", 0);
        CN_NUM_MAP.put("一", 1);
        CN_NUM_MAP.put("二", 2);
        CN_NUM_MAP.put("三", 3);
        CN_NUM_MAP.put("四", 4);
        CN_NUM_MAP.put("五", 5);
        CN_NUM_MAP.put("六", 6);
        CN_NUM_MAP.put("七", 7);
        CN_NUM_MAP.put("八", 8);
        CN_NUM_MAP.put("九", 9);
        CN_NUM_MAP.put("十", 10);
        CN_NUM_MAP.put("百", 100);
        CN_NUM_MAP.put("千", 1000);
        CN_NUM_MAP.put("万", 10000);
    }
    // 提取名称中连续中文数字
    private String parseChineseNumber(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;
        int len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            String charStr = String.valueOf(c);
            if (CN_NUM_MAP.containsKey(charStr) && i + 1 < len) {
                char next = input.charAt(i + 1);
                if (next == '、' || next == '.') {
                    int j = i;
                    while (j < len && CN_NUM_MAP.containsKey(String.valueOf(input.charAt(j)))) {
                        j++;
                    }
                    String cnNum = input.substring(i, j);
                    long num = convertChineseToNumber(cnNum);
                    result.append(num);
                    i = j;
                } else {
                    result.append(c);
                    i++;
                }
            } else {
                result.append(c);
                i++;
            }
        }
        return result.toString();
    }
    private long convertChineseToNumber(String cn) {
        try {
            long total = 0;
            long temp = 0;
            for (int i = 0; i < cn.length(); i++) {
                String c = String.valueOf(cn.charAt(i));
                int v = CN_NUM_MAP.getOrDefault(c, 0);

                if (v == 10 || v == 100 || v == 1000 || v == 10000) {
                    temp = (temp == 0) ? 1 : temp;
                    temp *= v;
                    total += temp;
                    temp = 0;
                } else {
                    temp = v;
                }
            }
            total += temp;
            return total;
        } catch (Exception e) {
            return 0;
        }
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
                name1 = parseChineseNumber(name1);
                name2 = parseChineseNumber(name2);
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
     * 压缩根文件夹：
     * 1. 弹出确认对话框，确认压缩操作；
     * 2. 生成无冲突的压缩包名称（主页压缩包.zip/主页压缩包(1).zip）；
     * 3. 执行异步压缩任务。
     */
    private void compressRootFolder() {
        new AlertDialog.Builder(this)
                .setTitle("确认压缩")
                .setMessage("确定要将整个主页内容压缩到当前目录吗？\n\n搜索“@20080202”这个格式可以将这个日期以后的txt文件进行压缩,区间也行“@20080202#20290202”\n\n压缩后的文件将以“主页压缩包.zip”命名。")
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
            List<String> menuList = new ArrayList<>();
            if (isInRecycleBin) {
                menuList.add("返回主页");
                menuList.add("清空回收站");
                menuList.add("中转站");
            } else if (isInTransferStation) {
                menuList.add("返回主页");
                menuList.add("新建文件夹");
                menuList.add("压缩主页文件");
                menuList.add("定稿");
                menuList.add("回收站");
            } else {
                menuList.add("返回主页");
                menuList.add("新建文件夹");
                menuList.add("定稿");
                menuList.add("回收站");
                menuList.add("中转站");
            }
            ListView listView = new ListView(this);
            listView.setBackgroundResource(R.drawable.popup_menu_bg);
            listView.setDivider(null);
            listView.setDividerHeight(1);
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 0, menuList) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null) {
                        convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
                    }
                    TextView tv = convertView.findViewById(android.R.id.text1);
                    tv.setText(menuList.get(position));
                    tv.setTextColor(0xFFFFFFFF);
                    tv.setTextSize(18);
                    tv.setPadding(20, 16, 20, 16);
                    convertView.setBackgroundResource(R.drawable.menu_item_border);
                    convertView.setClickable(false);
                    convertView.setFocusable(false);

                    return convertView;
                }
            };
            listView.setAdapter(adapter);
            final PopupWindow popupWindow = new PopupWindow(
                    listView,
                    dp2px(140),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );
            popupWindow.setBackgroundDrawable(new ColorDrawable(0xFF000000));
            popupWindow.setOutsideTouchable(true);
            popupWindow.setFocusable(true);
            listView.setOnItemClickListener((parent, v, position, id) -> {
                String text = menuList.get(position);
                switch (text) {
                    case "返回主页":
                        if (isInRecycleBin) exitRecycleBin();
                        else navigateToRootDirectory();
                        break;
                    case "新建文件夹":
                        showFolderCreateDialog();
                        break;
                    case "压缩主页文件":
                        compressRootFolder();
                        break;
                    case "回收站":
                        openRecycleBin();
                        break;
                    case "清空回收站":
                        confirmClearRecycleBin();
                        break;
                    case "中转站":
                        if (checkTransferPermission()) openTransferStation();
                        break;
                    case "定稿":
                        generateAllTxtHashTask();
                        break;
                }
                popupWindow.dismiss();
            });

            popupWindow.showAsDropDown(view, 0, 0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    /**
     * 一键生成 TXT 哈希总表
     */
    private void generateAllTxtHashTask() {
        // 先弹出确认框
        new AlertDialog.Builder(this)
                .setTitle("生成防篡改文件")
                .setMessage("此操作要为当前文件夹所有TXT文件生成哈希值，\n\n并合并计算总哈希值，存入一个新的TXT文件。")
                .setPositiveButton("确认生成", (dialog, which) -> {
                    dialog.dismiss();
                    startRealHashGenerate(); // 真正开始生成
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }
    /**
     * 计算当前文件夹哈希值
     */
    private void startRealHashGenerate() {
        if (currentDirectory == null || !currentDirectory.exists()) {
            Toast.makeText(this, "当前目录无效", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage("正在扫描计算哈希...");
        dialog.setCancelable(false);
        dialog.show();
        new AsyncTask<Void, Void, String>() {
            private List<FileItem> validFiles = new ArrayList<>();
            private List<String> skipped = new ArrayList<>();
            private String totalHash;
            @Override
            protected String doInBackground(Void... voids) {
                scanAllTxt(currentDirectory);
                Collections.sort(validFiles, (a, b) -> a.fileHash.compareTo(b.fileHash));
                StringBuilder allContent = new StringBuilder();
                for (FileItem item : validFiles) {
                    allContent.append(item.fileHash);
                }
                totalHash = getSHA256(allContent.toString());

                StringBuilder res = new StringBuilder();
                res.append("==========================\n");
                res.append("📌 所有文件合并后的总哈希：\n").append(totalHash).append("\n");
                res.append("==========================\n\n");
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH时mm分ss秒 SSS毫秒", Locale.getDefault());
                String nowTime = sdf.format(new Date());
                res.append("⏰ 生成时间：").append(nowTime).append("\n");
                res.append("===========================\n\n");
                res.append("===== 所有 TXT 按文件哈希排序 =====\n");
                res.append("有效文件：").append(validFiles.size()).append(" 个\n");
                res.append("跳过文件：").append(skipped.size()).append(" 个\n\n");
                if (!skipped.isEmpty()) {
                    res.append("---------- 被跳过的文件 ----------\n");
                    for (String s : skipped) res.append(s).append("\n");
                    res.append("----------------------------------------\n\n");
                }
                for (FileItem item : validFiles) {
                    res.append("文件哈希：").append(item.fileHash).append("\n");
                    res.append("路径：").append(item.relPath).append("\n");
                    res.append("----------------------------------------\n");
                }
                res.append("\n==========================\n");
                res.append("📄 生成原理说明：\n");
                res.append("1. 递归扫描当前目录及所有子文件夹内TXT文件；\n");
                res.append("2. 自动跳过文件名包含「_所有文件总哈希值」的文件；\n");
                res.append("3. 读取内容后彻底清除：所有 Unicode 空白符、换行相关符号；\n");
                res.append("4. 中文逗号「，」自动转为英文逗号「,」统一格式；\n");
                res.append("5. 对清洗后的纯文本计算单个文件SHA‑256哈希；\n");
                res.append("6. 按文件哈希从小到大排序；\n");
                res.append("7. 按顺序拼接所有文件哈希值，计算最终总SHA‑256哈希值；\n");
                res.append("8. 高精度时间戳用于防篡改校验。\n");
                res.append("=============================\n");
                res.append("本软件由开发者陈阳2077开发维护，软件名“流动文档”\n");
                res.append("=============================\n");
                return res.toString();
            }
            @Override
            protected void onPostExecute(String result) {
                dialog.dismiss();
                try {
                    String folderName = currentDirectory.getName();
                    String baseName = folderName + "_所有文件总哈希值.txt";
                    File outFile = getNoDuplicateFile(currentDirectory, baseName);
                    FileOutputStream fos = new FileOutputStream(outFile);
                    fos.write(result.getBytes(StandardCharsets.UTF_8));
                    fos.close();
                    Toast.makeText(MainActivity.this, "完成：" + outFile.getName(), Toast.LENGTH_LONG).show();
                    loadFileList();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                }
            }
            private void scanAllTxt(File dir) {
                File[] files = dir.listFiles();
                if (files == null) return;
                for (File f : files) {
                    if (f.isDirectory()) {
                        scanAllTxt(f);
                        continue;
                    }
                    String name = f.getName();
                    if (!name.toLowerCase().endsWith(".txt")) continue;
                    if (name.contains("_所有文件总哈希值")) {
                        skipped.add(getRelativePath(f));
                        continue;
                    }
                    try {
                        String content = readTxt(f);
                        String hash = getSHA256(content);
                        validFiles.add(new FileItem(f, getRelativePath(f), content, hash));
                    } catch (Exception e) {}
                }
            }
            private String readTxt(File f) throws Exception {
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String content = sb.toString();
                content = content.replaceAll("\\p{Space}+", "");
                content = content.replaceAll("[\\n\\r\\f\\u000B]", "");
                content = content.replace("，", ",");

                return content;
            }
            private String getRelativePath(File f) {
                String root = currentDirectory.getAbsolutePath();
                String path = f.getAbsolutePath();
                if (path.startsWith(root)) return path.substring(root.length() + 1);
                return f.getName();
            }
            private File getNoDuplicateFile(File dir, String baseName) {
                String corePrefix = baseName.replace(".txt", "");
                String randomStr = generateRandomString();
                String timestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
                boolean coreExists = false;
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        String fn = f.getName();
                        if (fn.matches("^" + Pattern.quote(corePrefix) + "(\\(\\d+\\))?_.+")) {
                            coreExists = true;
                            break;
                        }
                    }
                }
                int serial = 0;
                if (coreExists) {
                    int maxNum = 0;
                    for (File f : files) {
                        String fn = f.getName();
                        if (fn.startsWith(corePrefix)) {
                            String part = fn.substring(corePrefix.length());
                            if (part.startsWith("(")) {
                                int end = part.indexOf(")");
                                if (end > 1) {
                                    try {
                                        int num = Integer.parseInt(part.substring(1, end));
                                        if (num > maxNum) maxNum = num;
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    }
                    serial = maxNum + 1;
                }
                StringBuilder sb = new StringBuilder(corePrefix);
                if (serial > 0) {
                    sb.append("(").append(serial).append(")");
                }
                sb.append("_").append(randomStr).append("_").append(timestamp);
                return new File(dir, sb + ".txt");
            }
            private String getSHA256(String content) {
                try {
                    java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                    byte[] bytes = md.digest(content.getBytes(StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    for (byte b : bytes) {
                        String hex = Integer.toHexString(0xff & b);
                        if (hex.length() == 1) sb.append("0");
                        sb.append(hex);
                    }
                    return sb.toString();
                } catch (Exception e) {
                    return "";
                }
            }
            class FileItem {
                File file;
                String relPath;
                String content;
                String fileHash;
                public FileItem(File file, String relPath, String content, String fileHash) {
                    this.file = file;
                    this.relPath = relPath;
                    this.content = content;
                    this.fileHash = fileHash;
                }
            }
        }.execute();
    }
    /**
     * 检查中转站权限：
     * 1. Android 11+ 检查MANAGE_EXTERNAL_STORAGE权限；
     * 2. 低版本检查读写外部存储权限；
     * 3. 校验中转站目录可写性。
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
            if (btnZip != null) btnZip.setVisibility(View.GONE);
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
            if (btnZip != null) btnZip.setVisibility(View.GONE);
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
     * 检查文件内容中是否包含指定关键词（忽略大小写）
     * 过滤规则：不检查ZIP压缩包、图片文件、其他非文本文件
     * @param file 待检查的文件
     * @param keyword 要匹配的关键词（空字符串返回false）
     * @return 内容是否包含关键词
     */
    private boolean isContentContainKeyword(File file, String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return false;
        }
        if (file.getName().toLowerCase().endsWith(".zip") || isImageFile(file) || isOtherFile(file)) {
            return false;
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            String lowerKeyword = keyword.toLowerCase();
            while ((line = br.readLine()) != null) {
                if (line.toLowerCase().contains(lowerKeyword)) {
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
            if (btnZip != null) btnZip.setVisibility(View.GONE);
            return;
        }
        saveSearchKeyword(keyword);
        ProgressDialog searchDialog = new ProgressDialog(this);
        searchDialog.setMessage("太慢可跳过末尾带#的文件夹...");
        searchDialog.setCanceledOnTouchOutside(false);
        searchDialog.setCancelable(false);
        searchDialog.show();
        new Thread(() -> {
            isInSearchMode = true;
            searchResultList.clear();
            if (keyword.startsWith("@")) {
                String timeStr = keyword.substring(1).trim();
                if (timeStr.matches("\\d{8}[#]\\d{8}")) {
                    String[] split = timeStr.split("[#]");
                    String startDay = split[0];
                    String endDay = split[1];
                    scanTimeRangeNoSkip(currentDirectory, startDay, endDay);
                }
                else if (timeStr.length() == 8) {
                    scanTimeSingleNoSkip(currentDirectory, timeStr);
                }
            }
            recursiveSearch(currentDirectory, keyword);
            sortSearchResult();

            runOnUiThread(() -> {
                if (searchDialog.isShowing()) searchDialog.dismiss();
                fileAdapter.setData(searchResultList);
                btnZip.setVisibility(searchResultList.isEmpty() ? View.GONE : View.VISIBLE);
                Toast.makeText(MainActivity.this, searchResultList.size() + " 个匹配结果", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }
    /**
     * 内部工具：单日期到今日搜索
     */
    private void scanTimeSingleNoSkip(File dir, String timeStr) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanTimeSingleNoSkip(file, timeStr);
            } else {
                String name = file.getName();
                String timeStamp = null;
                int len = name.length();
                for (int i = 0; i <= len - 8; i++) {
                    String sub = name.substring(i, i + 8);
                    if (sub.matches("\\d{8}")) {
                        timeStamp = sub;
                        break;
                    }
                }
                if (timeStamp != null && timeStamp.equals(timeStr) && !searchResultList.contains(file)) {
                    searchResultList.add(file);
                }
            }
        }
    }
    /**
     * 内部工具：日期区间搜索
     */
    private void scanTimeRangeNoSkip(File dir, String startDay, String endDay) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanTimeRangeNoSkip(file, startDay, endDay);
            } else {
                String name = file.getName();
                String timeStamp = null;
                int len = name.length();
                for (int i = 0; i <= len - 8; i++) {
                    String sub = name.substring(i, i + 8);
                    if (sub.matches("\\d{8}")) {
                        timeStamp = sub;
                        break;
                    }
                }
                if (timeStamp != null) {
                    if (timeStamp.compareTo(startDay) >= 0 && timeStamp.compareTo(endDay) <= 0) {
                        if (!searchResultList.contains(file)) {
                            searchResultList.add(file);
                        }
                    }
                }
            }
        }
    }
    /**
     * 递归：按文件名最后一个时间戳筛选（晚于指定时间）
     */
    private void recursiveSearchByLastTimestamp(File dir, String targetTime) {
        if (dir == null || !dir.exists() || dir.listFiles() == null) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                recursiveSearchByLastTimestamp(f, targetTime);
            } else {
                String name = f.getName();
                if (!name.toLowerCase().endsWith(".txt")) continue;

                ArrayList<String> timestamps = extractAllTimestamps(name);
                if (timestamps.isEmpty()) continue;
                String lastTs = timestamps.get(timestamps.size() - 1);
                if (lastTs.length() < 8) continue;

                String fileDate = lastTs.substring(0, 8);
                if (fileDate.compareTo(targetTime) >= 0) {
                    if (!searchResultList.contains(f)) {
                        searchResultList.add(f);
                    }
                }
            }
        }
    }

    /**
     * 提取文件名中所有 17位时间戳 yyyyMMddHHmmssSSS
     */
    private ArrayList<String> extractAllTimestamps(String fileName) {
        ArrayList<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\d{17}");
        Matcher matcher = pattern.matcher(fileName);
        while (matcher.find()) {
            list.add(matcher.group());
        }
        return list;
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
     * @param dir     搜索目录
     * @param keyword 搜索关键词
     */
    private void recursiveSearch(File dir, String keyword) {
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
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
        if (file == null || file.isDirectory() || !file.exists()) {
            return false;
        }
        String fileName = file.getName().toLowerCase();
        boolean extMatch = false;
        for (String ext : IMAGE_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                extMatch = true;
                break;
            }
        }
        if (!extMatch) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[10];
            int readLen = fis.read(header);
            if (readLen < 4) {
                return false;
            }
            if (header[0] == (byte)0xFF && header[1] == (byte)0xD8 && header[2] == (byte)0xFF) {
                return true;
            }
            // PNG
            if (header[0] == (byte)0x89 && header[1] == (byte)0x50 && header[2] == (byte)0x4E && header[3] == (byte)0x47) {
                return true;
            }
            // GIF
            if (header[0] == (byte)0x47 && header[1] == (byte)0x49 && header[2] == (byte)0x46) {
                return true;
            }
            // BMP
            if (header[0] == (byte)0x42 && header[1] == (byte)0x4D) {
                return true;
            }
            // WebP
            if (header[0] == (byte)0x52 && header[1] == (byte)0x49 && header[2] == (byte)0x46 && header[3] == (byte)0x46) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    private boolean isTextFile(File file) {
        if (file == null || file.isDirectory() || !file.exists()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".txt")) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] head = new byte[64];
            int len = fis.read(head);
            if (len <= 0) return true;
            for (int i = 0; i < len; i++) {
                byte b = head[i];
                if (b == 0x00 || b == 0x7F) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }
    private boolean isZipFile(File file) {
        if (file == null || file.isDirectory() || !file.exists()) {
            return false;
        }
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".zip")) {
            return false;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] head = new byte[4];
            if (fis.read(head) != 4) return false;

            return head[0] == 0x50 && head[1] == 0x4B &&
                    (head[2] == 0x03 || head[2] == 0x05 || head[2] == 0x07) &&
                    (head[3] == 0x04 || head[3] == 0x06 || head[3] == 0x08);

        } catch (Exception e) {
            return false;
        }
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
        searchResultList.addAll(txtAndImageFiles);
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
        if (currentDirectory == null || !currentDirectory.canWrite()) {
            Toast.makeText(this, "创建测试文件失败：目标目录不可写", Toast.LENGTH_SHORT).show();
            return;
        }
        File testFile = new File(currentDirectory, "使用说明与注意事项.txt");
        if (testFile.exists()) {
            Toast.makeText(this, "测试文件已存在，无需重复创建", Toast.LENGTH_SHORT).show();
            return;
        }
        try (InputStream in = getAssets().open("instructions.txt");
             OutputStream out = new FileOutputStream(testFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            Toast.makeText(this, "文件复制成功！", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "复制失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        final File loadDirectory = currentDirectory;
        new Thread(new Runnable() {
            @Override
            public void run() {
                File dir = loadDirectory;
                File[] files = dir.listFiles();
                List<File> folders = new ArrayList<>();
                List<File> zipFiles = new ArrayList<>();
                List<File> txtAndImageFiles = new ArrayList<>();
                List<File> otherFiles = new ArrayList<>();
                if (files != null) {
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
                }
                sortFoldersWithDecimalSupport(folders);
                Comparator<File> txtImageComparator = new Comparator<File>() {
                    @Override
                    public int compare(File file1, File file2) {
                        String name1 = file1.getName();
                        String name2 = file2.getName();
                        name1 = parseChineseNumber(name1);
                        name2 = parseChineseNumber(name2);
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
                Collections.sort(txtAndImageFiles, txtImageComparator);
                Collections.sort(zipFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                Collections.sort(otherFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
                List<File> fullySortedList = new ArrayList<>();
                fullySortedList.addAll(folders);
                fullySortedList.addAll(zipFiles);
                fullySortedList.addAll(txtAndImageFiles);
                fullySortedList.addAll(otherFiles);
                runOnUiThread(() -> {
                    if (!loadDirectory.equals(currentDirectory)) {
                        return;
                    }
                    fileList.clear();
                    fileList.addAll(fullySortedList);
                    if (!isInSearchMode) {
                        fileAdapter.setData(fileList);
                    }
                    if (mLoadingDialog != null && mLoadingDialog.isShowing()) {
                        mLoadingDialog.dismiss();
                        mLoadingDialog = null;
                    }

                    if (TextUtils.isEmpty(etSearch.getText().toString().trim())) {
                        updateLevelHint();
                    }
                    if (copiedFile != null && dir.equals(copiedFile)) {
                        hidePasteButton();
                    }
                });
            }
        }).start();
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
                        boolean result = ZipUnzipUtil.unzipToCurrentDir(zipFile.getAbsolutePath(), currentDirectory.getAbsolutePath());
                        runOnUiThread(() -> {
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
                        androidx.appcompat.app.AlertDialog loadingDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setMessage("正在删除，请稍候...")
                                .setCancelable(false)
                                .create();
                        loadingDialog.show();
                        new android.os.AsyncTask<Void, Void, Boolean>() {
                            @Override
                            protected Boolean doInBackground(Void... voids) {
                                return performRecursiveDeletion(file);
                            }
                            @Override
                            protected void onPostExecute(Boolean success) {
                                super.onPostExecute(success);
                                if (loadingDialog.isShowing()) {
                                    loadingDialog.dismiss();
                                }
                                if (success) {
                                    Toast.makeText(MainActivity.this, "已永久删除", Toast.LENGTH_SHORT).show();
                                    loadFileList();
                                } else {
                                    Toast.makeText(MainActivity.this, "删除失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }.execute();

                    });
        } else {
            builder.setTitle("确认删除")
                    .setMessage("确定要将 " + getDisplayName(file) + " 移至回收站吗？")
                    .setPositiveButton("删除", (dialog, which) -> {
                        androidx.appcompat.app.AlertDialog loadingDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setMessage("正在移至回收站...")
                                .setCancelable(false)
                                .create();
                        loadingDialog.show();
                        new android.os.AsyncTask<Void, Void, Boolean>() {
                            @Override
                            protected Boolean doInBackground(Void... voids) {
                                return moveToRecycleBin(file);
                            }
                            @Override
                            protected void onPostExecute(Boolean success) {
                                super.onPostExecute(success);
                                if (loadingDialog.isShowing()) {
                                    loadingDialog.dismiss();
                                }
                                if (success) {
                                    Toast.makeText(MainActivity.this, "已移至回收站", Toast.LENGTH_SHORT).show();
                                    loadFileList();
                                } else {
                                    Toast.makeText(MainActivity.this, "删除操作失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }.execute();

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
            if (file.isDirectory()) {
                File targetFolder = new File(recycleBinDirectory, file.getName());
                File safeFolder = getNonConflictFile(targetFolder);
                final boolean result = moveFolderToRecycleBinLikeCut(file, safeFolder);
                return result;
            }
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
            boolean finalResult = finalSuccess;
            return finalResult;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * 模拟剪切效果将文件夹移入回收站，递归移动文件并自动重命名、处理重名
     */
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
    /**
     * 内部递归删除文件/文件夹，清空目录后删除自身
     */
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
    /**
     * 回收站安全文件名：超长文件名自动截取，保留合法后缀
     */
    private String getSafeFileNameForRecycleBin(String fileName) {
        final int MAX_ALLOW = 120;
        if (fileName.length() <= MAX_ALLOW) {
            return fileName;
        }
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
        if (main.length() > MAX_ALLOW) {
            main = main.substring(0, MAX_ALLOW);
        }
        return main + ext;
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
     * 直接压缩文件夹，
     */
    private void zipFolder(File folder) {
        if (isZipCompressing) {
            Toast.makeText(this, "正在压缩中，请稍候...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!folder.exists() || !folder.isDirectory()) {
            Toast.makeText(this, "文件夹不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        String zipFileName = folder.getName() + ".zip";
        File zipFile = new File(folder.getParentFile(), zipFileName);
        int counter = 1;
        while (zipFile.exists()) {
            zipFileName = folder.getName() + "(" + counter + ").zip";
            zipFile = new File(folder.getParentFile(), zipFileName);
            counter++;
        }
        final File finalZipFile = zipFile;
        final ProgressDialog progressDialog = new ProgressDialog(this);
        final boolean[] finalSuccess = {true};
        progressDialog.setMessage("正在压缩...");
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
        isZipCompressing = true;
        new Thread(() -> {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(finalZipFile))) {
                zos.setLevel(9);
                addFolderToZip(folder, folder.getName(), zos);
            } catch (IOException e) {
                finalSuccess[0] = false;
                e.printStackTrace();
            }
            runOnUiThread(() -> {
                progressDialog.dismiss();
                isZipCompressing = false;
                if (finalSuccess[0]) {
                    Toast.makeText(this, "压缩成功：" + finalZipFile.getName(), Toast.LENGTH_SHORT).show();
                    loadFileList();
                } else {
                    Toast.makeText(this, "压缩失败", Toast.LENGTH_SHORT).show();
                    if (finalZipFile.exists()) {
                        finalZipFile.delete();
                    }
                }
            });
        }).start();
    }
    /**
     * 递归压缩文件夹
     */
    private void addFolderToZip(File folder, String parentPath, ZipOutputStream zos) throws IOException {
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            ZipEntry zipEntry = new ZipEntry(parentPath + "/");
            zos.putNextEntry(zipEntry);
            zos.closeEntry();
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                addFolderToZip(file, parentPath + "/" + file.getName(), zos);
            } else {
                try (FileInputStream fis = new FileInputStream(file)) {
                    ZipEntry zipEntry = new ZipEntry(parentPath + "/" + file.getName());
                    zos.putNextEntry(zipEntry);
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
        }
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
            Uri fileUri;
            if (isImageFile(file)) {
                String path = MediaStore.Images.Media.insertImage(
                        getContentResolver(),
                        file.getAbsolutePath(),
                        file.getName(),
                        "Shared Image"
                );
                fileUri = Uri.parse(path);
            } else {
                fileUri = FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".fileprovider",
                        file
                );
            }
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (isImageFile(file)) {
                shareIntent.setType("image/*");
                shareIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"});
            } else {
                shareIntent.setType(getMimeType(file.getName()));
            }
            Intent chooser = Intent.createChooser(shareIntent, "分享文件");
            startActivity(chooser);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    /**
     * 获取文件MIME类型，支持txt、zip，其余返回通用类型，用于文件分享
     */
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
            newName = cleanFileName(newName);

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
                File parentDir = file.getParentFile();
                newCoreName = getNonConflictCoreNameInDir(parentDir, newCoreName);
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
    /**
     * 去除名称非法字符
     */
    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return "新建文件";
        }
        String cleaned = fileName.trim();
        cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|]", "");
        cleaned = cleaned.replaceAll("[\\n\\r\\t]", "");
        cleaned = cleaned.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        if (cleaned.isEmpty()) {
            return "新建文件";
        }
        int maxLength = 120;
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned;
    }
    /**
     * 文件名主体部分查重
     */
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
    /**
     * 判断目录中是否已存在相同核心文件名
     */
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
    /**
     * 提取纯核心名（专门兼容双时间戳，仅用于编辑显示，不影响生成逻辑）
     * 支持格式：
     * - 核心名_随机串_时间戳.后缀
     * - 核心名_随机串_时间戳1_时间戳2.后缀
     * @param fileName 原始文件名
     * @return 纯核心名（无随机字符、无单/双时间戳、无后缀）
     */
    private String extractPureCoreNameForDisplay(String fileName) {
        String nameWithoutExt = removeAllExtensions(fileName);
        String timestampPattern = "_[a-zA-Z0-9]+_(\\d+)(_\\d+)*";
        Pattern pattern = Pattern.compile(timestampPattern);
        Matcher matcher = pattern.matcher(nameWithoutExt);
        while (matcher.find()) {
            nameWithoutExt = nameWithoutExt.replace(matcher.group(), "");
        }
        nameWithoutExt = nameWithoutExt.replaceAll("^_+|_+$", "");

        return nameWithoutExt;
    }
    /**
     * 获取纯净的图片后缀，仅保留合法图片格式
     */
    private String getPureImageExtension(String fileName) {
        String ext = "";
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            ext = fileName.substring(lastDot).toLowerCase();
            if (!ext.equals(".png") && !ext.equals(".jpg") && !ext.equals(".jpeg") && !ext.equals(".gif") && !ext.equals(".bmp")) {
                ext = "";
            }
        }
        return ext;
    }
    /**
     * 净化文件名：
     * 1. 移除文件名中所有后缀；
     * 2. 根据文件类型重新补全标准后缀；
     * 3. 图片文件使用原始文件的纯净后缀，保证格式正确。
     */
    private String purifyFileName(String fileName, boolean isTxtFile, boolean isImageFileFlag, String originalFileName) {
        String pureName = removeAllExtensions(fileName);
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
            }
            else if (sourceFile.getName().toLowerCase().endsWith(".txt") || isImageFile(sourceFile)) {
                String originalName = sourceFile.getName();
                String cleanName = removeTimestamp(originalName);
                String originalExt = getOriginalExtension(originalName);
                int lastDotIndex = cleanName.lastIndexOf(".");
                if (lastDotIndex > 0) {
                    cleanName = cleanName.substring(0, lastDotIndex);
                }
                cleanName = getSafeCoreName(cleanName);
                String randomStr = generateRandomString();
                String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
                if (baseTimestamp.length() >= 12) {
                    String datePart = baseTimestamp.substring(0, 8);
                    String timeRemaining = baseTimestamp.substring(12);
                    String sequenceStr = String.format(Locale.getDefault(), "%04d", sequenceNumber % 10000);
                    String newTimestamp = datePart + sequenceStr + timeRemaining;
                    String timestampSuffix = "_" + randomStr + "_" + newTimestamp;
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
            ext = fileName.substring(lastDot).toLowerCase();
        }
        return ext;
    }
    /**
     *   【核心统一工具】获取不重复的主体名（只在目标文件夹查重）
     */

    private String getNonConflictCoreNameInFolder(File targetFolder, String baseCore) {
        if (targetFolder == null || !targetFolder.exists() || baseCore == null) {
            return baseCore;
        }
        if (!isCoreNameExistsInFolder(targetFolder, baseCore)) {
            return baseCore;
        }
        int index = 1;
        while (true) {
            String testName = baseCore + "(" + index + ")";
            if (!isCoreNameExistsInFolder(targetFolder, testName)) {
                return testName;
            }
            index++;
        }
    }
    /**
     *  判断：主体名 是否在 目标文件夹 已存在
     */

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
            randomStr = generateRandomString();
            return "_" + randomStr + "_" + newTimestamp;
        } else if (ts2 == null) {
            return "_" + randomStr + "_" + ts1 + "_" + newTimestamp;
        } else {
            return "_" + randomStr + "_" + ts1 + "_" + newTimestamp;
        }
    }
    /**
     * 执行粘贴操作：
     * 1. 校验粘贴内容与当前目录有效性，禁止非法循环剪切；
     * 2. 显示进度弹窗，后台异步处理复制/剪切逻辑；
     * 3. 区分文件夹与文件，对TXT/图片执行特殊重命名与路径更新；
     * 4. 操作完成后关闭弹窗、提示结果并刷新文件列表。
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
        String targetPath = currentDirectory.getAbsolutePath();
        if (isCutOperation && copiedFile.isDirectory() &&
                targetPath.startsWith(copiedFile.getAbsolutePath() + File.separator)) {
            Toast.makeText(this, "无法剪切到子目录，避免循环嵌套", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            return;
        }
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
                        String randomStr = generateRandomString();
                        String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());
                        String timestampSuffix;
                        if (isCutOperation) {
                            String[] parsed = parseFileName(originalName);
                            String pureCoreName = parsed[0];
                            pureCoreName = getSafeCoreName(pureCoreName);
                            timestampSuffix = generateNewTimestampSuffix(parsed);
                            cleanName = pureCoreName;
                        } else {
                            cleanName = removeTimestamp(cleanName);
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
    /**
     * 移动文件夹并同步处理TXT/图片文件，自动重命名、处理冲突、递归移动
     */
    private boolean moveFolderWithTxtUpdate(File sourceDir, File targetParentDir, boolean isInnerMove) {
        if (sourceDir == null || !sourceDir.exists() || targetParentDir == null) {
            return false;
        }
        try {
            String folderName = getSafeCoreName(sourceDir.getName());
            File targetDir = new File(targetParentDir, folderName);
            if (targetDir.exists()) {
                String uniqueName = getUniqueFolderName(targetParentDir, targetDir.getName());
                targetDir = new File(targetParentDir, uniqueName);
            }
            if (!targetDir.mkdirs()) {
                return false;
            }
            File[] files = sourceDir.listFiles();
            if (files != null) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                for (File file : files) {
                    if (file.isDirectory()) {
                        moveFolderWithTxtUpdate(file, targetDir, true);
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
                            String cleanName = pureCoreName;
                            String finalCoreName = getNonConflictCoreNameInFolder(targetDir, cleanName);
                            String uniqueFileName = finalCoreName + timestampSuffix;
                            String finalFileName = removeAllExtensions(uniqueFileName) + originalExt;
                            File targetFile = new File(targetDir, finalFileName);
                            if (!file.renameTo(targetFile)) {
                                if (copyFileContent(file, targetFile)) {
                                    file.delete();
                                }
                            }
                        } else {
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
            deleteFolderTree(sourceDir);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     *  递归删除文件夹工具方法
     */
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
    /**
     *  统一规则：裁剪主体名称，保留后缀、随机串、时间戳
     */
    private String getSafeCoreName(String coreName) {
        final int MAX_LENGTH = 45;
        if (coreName.length() > MAX_LENGTH) {
            return coreName.substring(0, MAX_LENGTH);
        }
        return coreName;
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
                String cleanName = removeTimestamp(originalName);
                if (cleanName.toLowerCase().endsWith(".txt")) {
                    cleanName = cleanName.substring(0, cleanName.lastIndexOf("."));
                }
                cleanName = getSafeCoreName(cleanName);

                String randomStr = generateRandomString();
                String baseTimestamp = MILLIS_TIMESTAMP_FORMAT.format(new Date());

                if (baseTimestamp.length() >= 12) {
                    String datePart = baseTimestamp.substring(0, 8);
                    String timeRemaining = baseTimestamp.substring(12);
                    String sequenceStr = String.format(Locale.getDefault(), "%04d", currentSequence % 10000);
                    String newTimestamp = datePart + sequenceStr + timeRemaining;
                    String timestampSuffix = "_" + randomStr + "_" + newTimestamp;
                    String finalCoreName = getNonConflictCoreNameInFolder(targetFolder, cleanName);
                    String uniqueFileName = finalCoreName + timestampSuffix + ".txt";
                    File targetFile = new File(targetFolder, uniqueFileName);
                    if (copyFileContent(sourceFile, targetFile)) {
                        currentSequence++;
                    }
                } else {
                    Log.w("CopySubFolder", "时间戳格式异常，使用默认命名");
                    String timestampSuffix = "_" + randomStr + "_" + baseTimestamp;
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
     * 清理文件名中的各类时间戳，返回纯文本标题
     * @return String 清理后的文件名
     */
    public  String cleanTitle(String input) {
        if (TextUtils.isEmpty(input)) {
            return "";
        }
        String cleaned = MULTI_TIMESTAMP_PATTERN.matcher(input).replaceAll("");
        cleaned = SINGLE_TIMESTAMP_PATTERN.matcher(cleaned).replaceAll("");
        if (MainActivity.OLD_TIMESTAMP_PATTERN != null) {
            cleaned = MainActivity.OLD_TIMESTAMP_PATTERN.matcher(cleaned).replaceAll("");
        }
        return cleaned.trim();
    }

    /**
     * 移除文件名中的所有时间戳（含旧版格式）
     * @return String 去时间戳后的文件名
     */
    public  String removeTimestamp(String fileName) {
        String cleaned = cleanTitle(fileName);
        if (MainActivity.FILE_MILLIS_TIMESTAMP_PATTERN != null) {
            cleaned = MainActivity.FILE_MILLIS_TIMESTAMP_PATTERN.matcher(cleaned).replaceAll("");
        }
        return cleaned;
    }
    /**
     * 修复：确保方法是 private（Activity 内可访问），参数为 File，返回 String
     * 剪切专用：当前目录生成唯一文件名（不加时间戳）
     */
    private String getUniqueFileNameInDir(File targetFile) {
        String fileName = targetFile.getName();
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
            PreferenceUtils.saveLastPageType(this, "image");
            PreferenceUtils.saveLastViewedImage(this, imageFile.getAbsolutePath());
            PreferenceUtils.saveLastFolderPath(this, imageFile.getParentFile().getAbsolutePath());
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
        hideCustomPathTip();
        final File targetFileForScroll = currentDirectory;
        boolean needScroll = true;
        final int TARGET_VISUAL_POS = 5;
        if (isInSearchMode) {
            isInSearchMode = false;
            etSearch.setText("");
            if (btnZip != null) btnZip.setVisibility(View.GONE);
            etSearch.clearFocus();
            fileAdapter.setData(fileList);
            clearSearchKeyword();
            Toast.makeText(this, "已退出搜索", Toast.LENGTH_SHORT).show();
            hidePasteButton();
            PreferenceUtils.saveLastPageType(this, "main");
            if (currentDirectory != null && currentDirectory.exists()) {
                PreferenceUtils.saveLastFolderPath(this, currentDirectory.getAbsolutePath());
            }
            needScroll = false;
            loadFileList();
            updateLevelHint();
            return;
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
    /**
     * 打开时恢复崩溃的文件
     */
    private void recoverFromCrash() {
        File rootDir = new File(getFilesDir(), "主页根目录");
        if (!rootDir.exists()) return;
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
    /**
     * 压缩搜索结果到当前目录，重名自动加序号，文件名使用搜索关键词
     */
    private void zipSearchResultsToCurrentDir(final String keyword, final List<File> fileList) {
        if (currentDirectory == null || !currentDirectory.exists()) {
            Toast.makeText(this, "目录不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        final ProgressDialog zipDialog = new ProgressDialog(this);
        zipDialog.setMessage("正在压缩...");
        zipDialog.setCancelable(false);
        zipDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String zipName;
                    if (keyword.startsWith("@")) {
                        if (keyword.matches("@\\d{8}")) {
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
                            String today = sdf.format(new Date());
                            zipName = keyword + "#" + today + ".zip";
                        }
                        else {
                            zipName = keyword + ".zip";
                        }
                    } else {
                        zipName = keyword + ".zip";
                    }
                    File zipFile = new File(currentDirectory, zipName);
                    int index = 1;
                    while (zipFile.exists()) {
                        zipName = zipName.replace(".zip", "") + "(" + index + ").zip";
                        zipFile = new File(currentDirectory, zipName);
                        index++;
                    }
                    final String zipFileName = zipFile.getName();
                    FileOutputStream fos = new FileOutputStream(zipFile);
                    ZipOutputStream zos = new ZipOutputStream(fos);
                    zos.setLevel(5);
                    for (File file : fileList) {
                        if (file.isDirectory()) continue;
                        addFileToZip(zos, file);
                    }
                    zos.finish();
                    zos.close();
                    fos.close();
                    runOnUiThread(() -> {
                        zipDialog.dismiss();
                        Toast.makeText(MainActivity.this, "压缩完成：" + zipFileName, Toast.LENGTH_LONG).show();
                        performSearch();
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        zipDialog.dismiss();
                        Toast.makeText(MainActivity.this, "压缩失败", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }
    /**
     * 递归添加文件到 ZIP
     */
    private void addFileToZip(ZipOutputStream zos, File file) throws IOException {
        if (file.isDirectory()) {
            return;
        }
        String basePath = rootDirectory.getAbsolutePath();
        String fullPath = file.getAbsolutePath();
        String relativePath = fullPath.replace(basePath, "");
        if (relativePath.startsWith(File.separator)) {
            relativePath = relativePath.substring(1);
        }
        String zipEntryName = relativePath.replace(File.separator, "}");
        ZipEntry entry = new ZipEntry(zipEntryName);
        zos.putNextEntry(entry);
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            zos.write(buffer, 0, len);
        }
        fis.close();
        zos.closeEntry();
    }
}