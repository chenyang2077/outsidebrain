package com.example.outsidebrain;

import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
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
        viewPager2.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager2.setOffscreenPageLimit(1);
        viewPager2.setUserInputEnabled(false);
        viewPager2.setAdapter(new ImageAdapter());
        viewPager2.setCurrentItem(currentPosition, false);
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
            photoView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
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
            // 每次绑定都重新初始化显示
            Glide.with(ImageViewerActivity.this)
                    .load(imageFiles.get(position))
                    .into(holder.photoView);
            holder.photoView.post(() -> holder.photoView.initImageDisplay());
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
    // 完美版：严格按你的规则显示和缩放
    // ================================
    public static class SystemPhotoView extends AppCompatImageView {

        private static final float SLIDE_THRESHOLD = 40; // 滑动灵敏度

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
            // 布局完成后初始化显示
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    initImageDisplay();
                }
            });
        }

        // ========== 核心：初始化显示（严格按你的规则） ==========
        public void initImageDisplay() {
            if (getDrawable() == null) return;

            // 1. 获取尺寸
            int screenWidth = getWidth();
            int screenHeight = getHeight();
            int imageWidth = getDrawable().getIntrinsicWidth();
            int imageHeight = getDrawable().getIntrinsicHeight();

            // 2. 计算基准缩放：图片宽度 = 屏幕宽度
            baseScale = (float) screenWidth / imageWidth;
            currentScale = baseScale;

            // 3. 重置矩阵
            matrix.reset();
            // 4. 缩放：宽度匹配屏幕
            matrix.postScale(baseScale, baseScale);
            // 5. 平移：水平居中，垂直居中
            float translateX = (screenWidth - imageWidth * baseScale) / 2f;
            float translateY = (screenHeight - imageHeight * baseScale) / 2f;
            matrix.postTranslate(translateX, translateY);

            // 6. 应用矩阵
            setImageMatrix(matrix);
        }

        // ========== 触摸事件处理 ==========
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // 单指按下
                    lastTouchPoint.set(event.getX(), event.getY());
                    touchMode = 1;
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    // 双指按下
                    lastFingerDistance = calculateFingerDistance(event);
                    touchMode = 2;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (touchMode == 2) {
                        // 双指缩放
                        handleTwoFingerScale(event);
                    } else if (touchMode == 1) {
                        // 单指操作
                        handleSingleFingerMove(event);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    // 手指抬起：缩放不足则回弹到基准大小
                    touchMode = 0;
                    if (currentScale < baseScale) {
                        resetToBaseScale();
                    }
                    break;
            }
            return true;
        }

        // ========== 双指缩放（限制最小缩放为基准缩放） ==========
        private void handleTwoFingerScale(MotionEvent event) {
            float newDistance = calculateFingerDistance(event);
            if (newDistance > 10f) {
                // 计算缩放比例
                float scaleRatio = newDistance / lastFingerDistance;
                float targetScale = currentScale * scaleRatio;

                // 限制最小缩放为基准缩放（宽度=屏幕宽度）
                targetScale = Math.max(baseScale, targetScale);
                // 限制最大缩放（可根据需要调整）
                targetScale = Math.min(targetScale, baseScale * 4);

                // 计算缩放中心点
                float centerX = (event.getX(0) + event.getX(1)) / 2f;
                float centerY = (event.getY(0) + event.getY(1)) / 2f;

                // 应用缩放
                matrix.postScale(targetScale / currentScale, targetScale / currentScale, centerX, centerY);
                currentScale = targetScale;
                setImageMatrix(matrix);

                lastFingerDistance = newDistance;
            }
        }

        // ========== 单指操作（平移/翻页） ==========
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
                // 边界修正（保证图片宽度至少占满屏幕）
                fixImageBorder();
                setImageMatrix(matrix);
            }

            lastTouchPoint.set(event.getX(), event.getY());
        }

        // ========== 回弹到基准缩放（宽度=屏幕宽度） ==========
        private void resetToBaseScale() {
            // 计算缩放比例
            float scaleRatio = baseScale / currentScale;

            // 计算屏幕中心点作为回弹中心
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;

            // 应用缩放和平移
            matrix.postScale(scaleRatio, scaleRatio, centerX, centerY);
            currentScale = baseScale;

            // 重新居中
            centerImage();
            setImageMatrix(matrix);
        }

        // ========== 边界修正（保证宽度至少占满屏幕） ==========
        private void fixImageBorder() {
            RectF imageRect = new RectF();
            matrix.mapRect(imageRect, new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight()));

            float dx = 0, dy = 0;
            int screenWidth = getWidth();
            int screenHeight = getHeight();

            // 宽度：至少占满屏幕，不足则居中
            if (imageRect.width() < screenWidth) {
                dx = (screenWidth - imageRect.width()) / 2f - imageRect.left;
            } else {
                // 宽度超出：限制左右边界
                if (imageRect.left > 0) dx = -imageRect.left;
                if (imageRect.right < screenWidth) dx = screenWidth - imageRect.right;
            }

            // 高度：不限制，超出则裁剪，不足则留空白
            // （如果需要限制高度边界，可取消下面注释）
            // if (imageRect.height() < screenHeight) {
            //     dy = (screenHeight - imageRect.height()) / 2f - imageRect.top;
            // } else {
            //     if (imageRect.top > 0) dy = -imageRect.top;
            //     if (imageRect.bottom < screenHeight) dy = screenHeight - imageRect.bottom;
            // }

            matrix.postTranslate(dx, dy);
        }

        // ========== 图片居中 ==========
        private void centerImage() {
            if (getDrawable() == null) return;

            RectF imageRect = new RectF();
            matrix.mapRect(imageRect, new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight()));

            float dx = (getWidth() - imageRect.width()) / 2f - imageRect.left;
            float dy = (getHeight() - imageRect.height()) / 2f - imageRect.top;

            matrix.postTranslate(dx, dy);
        }

        // ========== 计算双指距离 ==========
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