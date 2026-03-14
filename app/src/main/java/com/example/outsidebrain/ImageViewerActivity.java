package com.example.outsidebrain;

import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageViewerActivity extends AppCompatActivity {

    private ViewPager2 viewPager2;
    private List<File> imageFiles;
    private int currentPosition;
    private ImageAdapter imageAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPreloadInited = false; // 标记预加载是否已初始化

    private final SimpleDateFormat MILLIS_TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.getDefault());
    private final Pattern FILE_MILLIS_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private final Pattern TARGET_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
    private final Pattern INCREMENT_TIMESTAMP_PATTERN = Pattern.compile("(_[A-Za-z0-9]{6}_\\d{17})(_\\d{17})+$");
    private final Pattern OLD_TIMESTAMP_PATTERN = Pattern.compile("_(\\d{14}|\\d{17})$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        String currentImagePath = getIntent().getStringExtra("IMAGE_PATH");
        String folderPath = getIntent().getStringExtra("FOLDER_PATH");

        imageFiles = getImageFilesInFolder(new File(folderPath));
        currentPosition = findImagePosition(currentImagePath);

        viewPager2 = findViewById(R.id.view_pager);
        // 初始不设置预加载，使用默认值（等效于0）
        viewPager2.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager2.setUserInputEnabled(false);

        imageAdapter = new ImageAdapter();
        viewPager2.setAdapter(imageAdapter);
        viewPager2.setCurrentItem(currentPosition, false);

        // 监听首屏布局完成，延迟开启预加载
        viewPager2.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                viewPager2.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                // 首屏布局完成后，延迟1秒开启预加载（确保首屏完全稳定）
                handler.postDelayed(() -> {
                    if (!isPreloadInited && viewPager2 != null && imageFiles.size() > 1) {
                        viewPager2.setOffscreenPageLimit(1); // 开启预加载（符合规则）
                        isPreloadInited = true;
                        // 刷新下一页预加载内容
                        if (currentPosition + 1 < imageFiles.size()) {
                            imageAdapter.notifyItemChanged(currentPosition + 1);
                        }
                    }
                }, 1000);
            }
        });

        // 页面切换监听：确保每次切换都正确初始化+居中
        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                // 延迟初始化当前页并强制居中
                handler.postDelayed(() -> {
                    RecyclerView recyclerView = (RecyclerView) viewPager2.getChildAt(0);
                    if (recyclerView != null) {
                        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                        if (holder instanceof ImageAdapter.Holder) {
                            SystemPhotoView photoView = ((ImageAdapter.Holder) holder).photoView;
                            photoView.forceInitDisplay();
                            photoView.forceCenterImage(); // 强制居中
                        }
                    }
                }, 50);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private List<File> getImageFilesInFolder(File folder) {
        List<File> list = new ArrayList<>();
        if (folder == null || !folder.isDirectory()) return list;

        File[] files = folder.listFiles();
        if (files == null) return list;

        for (File f : files) {
            if (isImage(f)) list.add(f);
        }

        Collections.sort(list, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                String n1 = f1.getName();
                String n2 = f2.getName();

                List<Long> l1 = extractMultiNumber(n1);
                List<Long> l2 = extractMultiNumber(n2);
                if (!l1.isEmpty() && !l2.isEmpty()) {
                    int min = Math.min(l1.size(), l2.size());
                    for (int i = 0; i < min; i++) {
                        if (!l1.get(i).equals(l2.get(i))) {
                            return Long.compare(l1.get(i), l2.get(i));
                        }
                    }
                    return Integer.compare(l1.size(), l2.size());
                } else if (!l1.isEmpty()) {
                    return -1;
                } else if (!l2.isEmpty()) {
                    return 1;
                }

                long t1 = extractTime(n1);
                long t2 = extractTime(n2);
                if (t1 != t2) return Long.compare(t2, t1);
                return n1.compareTo(n2);
            }
        });
        return list;
    }

    private long extractTime(String name) {
        if (name == null) return 0;
        String s = name.contains(".") ? name.substring(0, name.lastIndexOf(".")) : name;

        Matcher m = INCREMENT_TIMESTAMP_PATTERN.matcher(s);
        if (m.find()) {
            String[] arr = m.group().split("_");
            return parse(arr[arr.length - 1]);
        }
        m = TARGET_TIMESTAMP_PATTERN.matcher(s);
        if (m.find()) {
            String g = m.group();
            return parse(g.substring(g.lastIndexOf("_") + 1));
        }
        m = OLD_TIMESTAMP_PATTERN.matcher(s);
        if (m.find()) return parse(m.group(1));
        return 0;
    }

    private long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private List<Long> extractMultiNumber(String name) {
        List<Long> list = new ArrayList<>();
        if (name == null) return list;
        String s = name.contains(".") ? name.substring(0, name.lastIndexOf(".")) : name;
        Matcher m = Pattern.compile("^([0-9]+(\\.[0-9]+)*)").matcher(s);
        if (m.find()) {
            String[] arr = m.group(1).split("\\.");
            for (String num : arr) {
                try {
                    list.add(Long.parseLong(num));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private boolean isImage(File f) {
        if (f.isDirectory()) return false;
        String n = f.getName().toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    private int findImagePosition(String path) {
        for (int i = 0; i < imageFiles.size(); i++) {
            if (imageFiles.get(i).getAbsolutePath().equals(path)) return i;
        }
        return 0;
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            SystemPhotoView photoView = new SystemPhotoView(parent.getContext());
            photoView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            photoView.setScaleType(ImageView.ScaleType.MATRIX);

            photoView.setOnPageSlideListener(new SystemPhotoView.OnPageSlideListener() {
                @Override
                public void onSlideNext() {
                    int cur = viewPager2.getCurrentItem();
                    int total = getItemCount();
                    if (total <= 1) return;

                    int next = (cur + 1) % total;
                    viewPager2.setCurrentItem(next, true);
                }

                @Override
                public void onSlidePrev() {
                    int cur = viewPager2.getCurrentItem();
                    int total = getItemCount();
                    if (total <= 1) return;

                    int prev = (cur - 1 + total) % total;
                    viewPager2.setCurrentItem(prev, true);
                }
            });

            return new Holder(photoView);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            // 禁用Glide变换和缓存，保证尺寸准确
            Glide.with(ImageViewerActivity.this)
                    .load(imageFiles.get(position))
                    .apply(new RequestOptions()
                            .dontTransform()
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
                    .into(holder.photoView);

            // 初始化并强制居中
            holder.photoView.post(() -> {
                holder.photoView.forceInitDisplay();
                holder.photoView.forceCenterImage();
                // 二次确认居中
                handler.postDelayed(() -> holder.photoView.forceCenterImage(), 100);
            });
        }

        @Override
        public int getItemCount() {
            return imageFiles.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            SystemPhotoView photoView;
            public Holder(@NonNull View itemView) {
                super(itemView);
                photoView = (SystemPhotoView) itemView;
            }
        }
    }

    // ================================
    // 核心：缩放回弹自动居中 + 首屏预加载优化
    // ================================
    public static class SystemPhotoView extends AppCompatImageView {

        private static final float SLIDE_THRESHOLD = 40;

        private final Matrix matrix = new Matrix();
        private float baseScale = 1.0f; // 基准缩放：宽度=屏幕宽度
        private float currentScale = 1.0f;

        private final PointF lastTouchPoint = new PointF();
        private float lastFingerDistance;
        private int touchMode = 0; // 0=无 1=单指 2=双指

        public interface OnPageSlideListener {
            void onSlideNext();
            void onSlidePrev();
        }

        private OnPageSlideListener slideListener;

        public void setOnPageSlideListener(OnPageSlideListener listener) {
            this.slideListener = listener;
        }

        public SystemPhotoView(Context context) {
            super(context);
            init();
        }

        public SystemPhotoView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private void init() {
            setScaleType(ScaleType.MATRIX);
            // 布局变化时强制初始化+居中
            addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left > 0 && bottom - top > 0) {
                    forceInitDisplay();
                    forceCenterImage();
                }
            });
        }

        // ========== 强制初始化显示（宽度=屏幕宽度） ==========
        public void forceInitDisplay() {
            if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) {
                return;
            }

            // 重置矩阵和缩放状态
            matrix.reset();
            currentScale = 1.0f;

            // 计算基准缩放
            int screenWidth = getWidth();
            int screenHeight = getHeight();
            int imageWidth = getDrawable().getIntrinsicWidth();
            int imageHeight = getDrawable().getIntrinsicHeight();

            baseScale = (float) screenWidth / imageWidth;
            currentScale = baseScale;

            // 应用缩放
            matrix.postScale(baseScale, baseScale);

            // 先居中，再应用矩阵
            forceCenterImage();
            setImageMatrix(matrix);
            invalidate();
        }

        // ========== 核心新增：强制图片上下左右完全居中 ==========
        public void forceCenterImage() {
            if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) {
                return;
            }

            RectF imageRect = new RectF();
            matrix.mapRect(imageRect, new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight()));

            // 计算完全居中的偏移量
            float dx = (getWidth() - imageRect.width()) / 2f - imageRect.left;
            float dy = (getHeight() - imageRect.height()) / 2f - imageRect.top;

            // 应用居中平移
            matrix.postTranslate(dx, dy);
            setImageMatrix(matrix);
            invalidate();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchPoint.set(event.getX(), event.getY());
                    touchMode = 1;
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    lastFingerDistance = calculateFingerDistance(event);
                    touchMode = 2;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (touchMode == 2) {
                        handleTwoFingerScale(event);
                    } else if (touchMode == 1) {
                        handleSingleFingerMove(event);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    touchMode = 0;
                    // 缩放到基准宽度时，强制居中
                    if (currentScale <= baseScale + 0.01f) { // 允许微小误差
                        currentScale = baseScale;
                        resetToBaseScale();
                        forceCenterImage(); // 缩放回弹后自动居中
                    }
                    break;
            }
            return true;
        }

        private void handleTwoFingerScale(MotionEvent event) {
            float newDistance = calculateFingerDistance(event);
            if (newDistance > 10f) {
                float scaleRatio = newDistance / lastFingerDistance;
                float targetScale = currentScale * scaleRatio;
                // 最小缩放为基准缩放（宽度=屏幕宽度）
                targetScale = Math.max(baseScale, targetScale);
                // 最大缩放为基准缩放的4倍
                targetScale = Math.min(targetScale, baseScale * 4);

                float centerX = (event.getX(0) + event.getX(1)) / 2f;
                float centerY = (event.getY(0) + event.getY(1)) / 2f;

                matrix.postScale(targetScale / currentScale, targetScale / currentScale, centerX, centerY);
                currentScale = targetScale;
                setImageMatrix(matrix);
                lastFingerDistance = newDistance;
            }
        }

        private void handleSingleFingerMove(MotionEvent event) {
            float dx = event.getX() - lastTouchPoint.x;
            float dy = event.getY() - lastTouchPoint.y;

            if (Math.abs(currentScale - baseScale) < 0.01f) {
                // 基准大小：滑动翻页
                if (dy < -SLIDE_THRESHOLD && slideListener != null) {
                    slideListener.onSlideNext();
                    touchMode = 0;
                } else if (dy > SLIDE_THRESHOLD && slideListener != null) {
                    slideListener.onSlidePrev();
                    touchMode = 0;
                }
            } else {
                // 放大状态：平移图片
                matrix.postTranslate(dx, dy);
                fixImageBorder(); // 边界修正
                setImageMatrix(matrix);
            }

            lastTouchPoint.set(event.getX(), event.getY());
        }

        // 回弹到基准缩放并自动居中
        private void resetToBaseScale() {
            float scaleRatio = baseScale / currentScale;
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;

            matrix.postScale(scaleRatio, scaleRatio, centerX, centerY);
            currentScale = baseScale;

            setImageMatrix(matrix);
        }

        // 边界修正（保证宽度至少占满屏幕）
        private void fixImageBorder() {
            RectF imageRect = new RectF();
            matrix.mapRect(imageRect, new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight()));

            float dx = 0;
            int screenWidth = getWidth();

            if (imageRect.width() < screenWidth) {
                dx = (screenWidth - imageRect.width()) / 2f - imageRect.left;
            } else {
                if (imageRect.left > 0) dx = -imageRect.left;
                if (imageRect.right < screenWidth) dx = screenWidth - imageRect.right;
            }

            matrix.postTranslate(dx, 0);
        }

        // 计算双指距离
        private float calculateFingerDistance(MotionEvent event) {
            float x = event.getX(0) - event.getX(1);
            float y = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(x * x + y * y);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }
}