import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private List<File> imageFiles; // 同级目录所有图片文件
    private int currentPosition;   // 当前打开的图片位置

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        // 1. 获取传递的参数：当前图片路径 + 同级目录所有图片
        String currentImagePath = getIntent().getStringExtra("IMAGE_PATH");
        String folderPath = getIntent().getStringExtra("FOLDER_PATH");

        // 2. 加载同级目录所有图片文件
        imageFiles = getImageFilesInFolder(new File(folderPath));

        // 3. 找到当前图片的位置
        currentPosition = findImagePosition(currentImagePath);

        // 4. 初始化ViewPager2（设置为垂直滑动，也可改为水平）
        viewPager = findViewById(R.id.view_pager);
        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL); // 垂直滑动（上下）
        viewPager.setAdapter(new ImagePagerAdapter());
        viewPager.setCurrentItem(currentPosition, false); // 定位到当前图片
    }

    /**
     * 获取指定文件夹下的所有图片文件
     */
    private List<File> getImageFilesInFolder(File folder) {
        List<File> imageList = new ArrayList<>();
        if (!folder.isDirectory()) return imageList;

        File[] files = folder.listFiles();
        if (files == null) return imageList;

        // 筛选出所有图片文件
        for (File file : files) {
            if (isImageFile(file)) {
                imageList.add(file);
            }
        }
        return imageList;
    }

    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(File file) {
        if (file.isDirectory()) return false;
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".gif") ||
                name.endsWith(".bmp") || name.endsWith(".webp");
    }

    /**
     * 找到当前图片在列表中的位置
     */
    private int findImagePosition(String imagePath) {
        for (int i = 0; i < imageFiles.size(); i++) {
            if (imageFiles.get(i).getAbsolutePath().equals(imagePath)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * ViewPager2适配器（加载图片）
     */
    private class ImagePagerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder> {

        @Override
        public ImageViewHolder onCreateViewHolder(androidx.recyclerview.widget.ViewGroup parent, int viewType) {
            ImageView imageView = new ImageView(parent.getContext());
            imageView.setLayoutParams(new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                    androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT,
                    androidx.recyclerview.widget.RecyclerView.LayoutParams.MATCH_PARENT
            ));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new ImageViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(ImageViewHolder holder, int position) {
            File imageFile = imageFiles.get(position);
            // 用Glide加载图片（支持私有存储，无权限问题）
            Glide.with(ImageViewerActivity.this)
                    .load(imageFile)
                    .fitCenter()
                    .into(holder.imageView);

            // 点击图片可退出查看器（可选，增强体验）
            holder.imageView.setOnClickListener(v -> finish());
        }

        @Override
        public int getItemCount() {
            return imageFiles.size();
        }

        class ImageViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView imageView;

            public ImageViewHolder(ImageView itemView) {
                super(itemView);
                imageView = itemView;
            }
        }
    }

    /**
     * 重写返回键逻辑：返回上一级文件夹（你的文件列表页面）
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // 这里会自动返回你的主文件列表页面（因为启动方式是startActivity）
        // 无需额外处理，系统默认逻辑就是返回上一级Activity
    }
}