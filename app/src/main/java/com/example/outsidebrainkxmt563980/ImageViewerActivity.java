/**
软件名称：流动文档软件V1.0
版本号：V1.0
功能描述：加载指定文件夹下的图片并按规则排序，支持图片缩放、回弹居中、上下滑动翻页，首屏加载完成后开启预加载优化体验
所属模块：图片浏览模块
开发语言：Java
*/
package com.example.outsidebrainkxmt563980;
import android.content.Context;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * 图片浏览页：加载指定文件夹下的图片并按规则排序，支持图片缩放、回弹居中、上下滑动翻页，优化翻页体验
 */
public class ImageViewerActivity extends AppCompatActivity {
    private ViewPager2 viewPager2;
    private List<File> imageFiles;
    private int currentPosition;
    private ImageAdapter imageAdapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPreloadInited = false;
    private final Pattern INCREMENT_TIMESTAMP_PATTERN = Pattern.compile("(_[A-Za-z0-9]{6}_\\d{17})(_\\d{17})+$");
    private final Pattern TARGET_TIMESTAMP_PATTERN = Pattern.compile("_[A-Za-z0-9]{6}_\\d{17}");
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
        viewPager2.setUserInputEnabled(false);
        imageAdapter = new ImageAdapter();
        viewPager2.setAdapter(imageAdapter);
        viewPager2.setCurrentItem(currentPosition, false);
        viewPager2.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                viewPager2.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                handler.postDelayed(() -> {
                    if (!isPreloadInited && viewPager2 != null && imageFiles.size() > 1) {
                        viewPager2.setOffscreenPageLimit(1);
                        isPreloadInited = true;
                        if (currentPosition + 1 < imageFiles.size()) {
                            imageAdapter.notifyItemChanged(currentPosition + 1);
                        }
                    }
                }, 1000);
            }
        });
        viewPager2.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPosition = position;
                handler.postDelayed(() -> {
                    RecyclerView recyclerView = (RecyclerView) viewPager2.getChildAt(0);
                    if (recyclerView != null) {
                        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
                        if (holder instanceof ImageAdapter.Holder) {
                            SystemPhotoView photoView = ((ImageAdapter.Holder) holder).photoView;
                            photoView.forceInitDisplay();
                            photoView.forceCenterImage();
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
    /**
     * 加载指定文件夹下的图片文件并按规则排序
     * @param folder 目标文件夹
     * @return 排序后的图片文件列表
     */
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
                List<Long> l1 = extractMultiLevelNumberFromName(n1);
                List<Long> l2 = extractMultiLevelNumberFromName(n2);
                if (!l1.isEmpty() && !l2.isEmpty()) {
                    int minSize = Math.min(l1.size(), l2.size());
                    for (int i = 0; i < minSize; i++) {
                        long num1 = l1.get(i);
                        long num2 = l2.get(i);
                        if (num1 != num2) {
                            return Long.compare(num1, num2);
                        }
                    }
                    return Integer.compare(l1.size(), l2.size());
                } else if (!l1.isEmpty()) {
                    return -1;
                } else if (!l2.isEmpty()) {
                    return 1;
                }
                long t1 = extractTimestampFromName(n1);
                long t2 = extractTimestampFromName(n2);
                if (t1 != t2) {
                    return Long.compare(t2, t1);
                }
                return n1.compareTo(n2);
            }
        });
        return list;
    }
    /**
     * 提取文件名开头的多级小数点数字（去除扩展名后匹配）
     * @param fileName 文件名
     * @return 数字列表（如"1.25.jpg"→[1,25]，无数字返回空列表）
     */
    private List<Long> extractMultiLevelNumberFromName(String fileName) {
        List<Long> numList = new ArrayList<>();
        if (fileName == null || fileName.isEmpty()) {
            return numList;
        }
        String nameWithoutExt = fileName;
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
        }
        Pattern pattern = Pattern.compile("^([0-9]+(\\.[0-9]+)*)");
        Matcher matcher = pattern.matcher(nameWithoutExt);
        if (matcher.find()) {
            String[] numParts = matcher.group(1).split("\\.");
            for (String part : numParts) {
                try {
                    numList.add(Long.parseLong(part));
                } catch (NumberFormatException ignored) {
                    break;
                }
            }
        }
        return numList;
    }
    /**
     * 从文件名中提取时间戳（去除扩展名后匹配）
     * @param fileName 文件名
     * @return 解析后的时间戳（失败返回0）
     */
    private long extractTimestampFromName(String fileName) {
        if (fileName == null) return 0;
        String nameWithoutExt = fileName;
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex > 0) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
        }
        Matcher m = INCREMENT_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (m.find()) {
            String[] arr = m.group().split("_");
            return parseLongSafely(arr[arr.length - 1]);
        }
        m = TARGET_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (m.find()) {
            String g = m.group();
            return parseLongSafely(g.substring(g.lastIndexOf("_") + 1));
        }
        m = OLD_TIMESTAMP_PATTERN.matcher(nameWithoutExt);
        if (m.find()) {
            return parseLongSafely(m.group(1));
        }
        return 0;
    }
    /**
     * 安全解析Long类型数字，避免格式异常
     * @param s 待解析字符串
     * @return 解析结果（失败返回0）
     */
    private long parseLongSafely(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    /**
     * 判断文件是否为图片格式（jpg/jpeg/png/webp）
     * @param f 目标文件
     * @return true=是图片，false=否
     */
    private boolean isImage(File f) {
        if (f.isDirectory()) return false;
        String n = f.getName().toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }
    /**
     * 查找指定路径图片在列表中的位置
     * @param path 图片绝对路径
     * @return 位置索引（未找到返回0）
     */
    private int findImagePosition(String path) {
        for (int i = 0; i < imageFiles.size(); i++) {
            if (imageFiles.get(i).getAbsolutePath().equals(path)) return i;
        }
        return 0;
    }
    /**
     * 图片列表适配器（ViewPager2专用）：加载图片、初始化缩放/居中逻辑、处理滑动翻页事件
     */
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
            Glide.with(ImageViewerActivity.this)
                    .load(imageFiles.get(position))
                    .apply(new RequestOptions()
                            .dontTransform()
                            .skipMemoryCache(true)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE))
                    .into(holder.photoView);
            holder.photoView.post(() -> {
                holder.photoView.forceInitDisplay();
                holder.photoView.forceCenterImage();
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
    /**
     * 自定义图片显示View：支持双指缩放、回弹居中、滑动翻页触发
     */
    public static class SystemPhotoView extends AppCompatImageView {
        private static final float SLIDE_THRESHOLD = 40;
        private final Matrix matrix = new Matrix();
        private float baseScale = 1.0f;
        private float currentScale = 1.0f;
        private final PointF lastTouchPoint = new PointF();
        private float lastFingerDistance;
        private int touchMode = 0;
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
            addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left > 0 && bottom - top > 0) {
                    forceInitDisplay();
                    forceCenterImage();
                }
            });
        }
        /**
         * 强制初始化图片显示（宽度适配屏幕）
         */
        public void forceInitDisplay() {
            if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) {
                return;
            }
            matrix.reset();
            currentScale = 1.0f;
            int screenWidth = getWidth();
            int screenHeight = getHeight();
            int imageWidth = getDrawable().getIntrinsicWidth();
            int imageHeight = getDrawable().getIntrinsicHeight();
            baseScale = (float) screenWidth / imageWidth;
            currentScale = baseScale;
            matrix.postScale(baseScale, baseScale);
            forceCenterImage();
            setImageMatrix(matrix);
            invalidate();
        }
        /**
         * 强制图片上下左右完全居中
         */
        public void forceCenterImage() {
            if (getDrawable() == null || getWidth() == 0 || getHeight() == 0) {
                return;
            }
            RectF imageRect = new RectF();
            matrix.mapRect(imageRect, new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight()));
            float dx = (getWidth() - imageRect.width()) / 2f - imageRect.left;
            float dy = (getHeight() - imageRect.height()) / 2f - imageRect.top;
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
                    if (currentScale <= baseScale + 0.01f) {
                        currentScale = baseScale;
                        resetToBaseScale();
                        forceCenterImage();
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
                targetScale = Math.max(baseScale, targetScale);
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
                if (dy < -SLIDE_THRESHOLD && slideListener != null) {
                    slideListener.onSlideNext();
                    touchMode = 0;
                } else if (dy > SLIDE_THRESHOLD && slideListener != null) {
                    slideListener.onSlidePrev();
                    touchMode = 0;
                }
            } else {
                matrix.postTranslate(dx, dy);
                fixImageBorder();
                setImageMatrix(matrix);
            }
            lastTouchPoint.set(event.getX(), event.getY());
        }
        private void resetToBaseScale() {
            float scaleRatio = baseScale / currentScale;
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            matrix.postScale(scaleRatio, scaleRatio, centerX, centerY);
            currentScale = baseScale;
            setImageMatrix(matrix);
        }
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