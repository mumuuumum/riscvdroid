package org.fdroid.fdroid.views.categories;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.os.LocaleListCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.fdroid.database.Category;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.views.categories.CategoryItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 左侧分类侧边栏的RecyclerView适配器
 * 负责显示分类列表，处理选中状态和点击事件
 */
public class CategorySidebarAdapter extends ListAdapter<CategoryItem, CategorySidebarAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<CategoryItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CategoryItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull CategoryItem oldItem, @NonNull CategoryItem newItem) {
                    return oldItem.category.equals(newItem.category);
                }

                @Override
                public boolean areContentsTheSame(@NonNull CategoryItem oldItem, @NonNull CategoryItem newItem) {
                    return oldItem.numApps == newItem.numApps &&
                            oldItem.category.getName(LocaleListCompat.getDefault())
                                    .equals(newItem.category.getName(LocaleListCompat.getDefault()));
                }
            };

    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryItem category, int position);
    }

    private final Context context;
    private OnCategoryClickListener listener;
    private int selectedPosition = 0; // 默认选中第一个分类

    public CategorySidebarAdapter(Context context) {
        super(DIFF_CALLBACK);
        this.context = context;
    }

    public void setOnCategoryClickListener(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setSelectedPosition(int position) {
        if (position >= 0 && position < getItemCount()) {
            int previousPosition = selectedPosition;
            selectedPosition = position;

            // 更新前一个选中的item
            if (previousPosition >= 0 && previousPosition < getItemCount()) {
                notifyItemChanged(previousPosition);
            }

            // 更新当前选中的item
            notifyItemChanged(selectedPosition);
        }
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public CategoryItem getSelectedCategory() {
        if (selectedPosition >= 0 && selectedPosition < getItemCount()) {
            return getItem(selectedPosition);
        }
        return null;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setCategories(@NonNull List<CategoryItem> items) {
        // 排序分类项目
        List<CategoryItem> sortedItems = new ArrayList<>(items);
        Collections.sort(sortedItems, (o1, o2) -> {
            String name1 = o1.category.getName(LocaleListCompat.getDefault());
            if (name1 == null) name1 = o1.category.getId();
            String name2 = o2.category.getName(LocaleListCompat.getDefault());
            if (name2 == null) name2 = o2.category.getId();
            return name1.compareToIgnoreCase(name2);
        });

        submitList(sortedItems);
        // 重置选中位置为第一个
        selectedPosition = 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.category_sidebar_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryItem item = getItem(position);
        holder.bind(item, position == selectedPosition);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final View itemRoot;
        private final TextView nameText;

        ViewHolder(View itemView) {
            super(itemView);
            itemRoot = itemView.findViewById(R.id.category_item_root);
            nameText = itemView.findViewById(R.id.category_name);
        }

        void bind(CategoryItem item, boolean isSelected) {
            // 设置分类名称
            String categoryName = item.category.getName(LocaleListCompat.getDefault());
            if (categoryName == null) {
                categoryName = translateCategory(context, item.category.getId());
            }
            nameText.setText(categoryName);

            // 获取当前位置
            int position = getAbsoluteAdapterPosition();

            // 优先级 1: 选中状态 - 保持纯白色背景，加粗黑色字体
            if (isSelected) {
                itemRoot.setBackgroundColor(context.getColor(android.R.color.white));
                // 设置文字为加粗黑色
                nameText.setTypeface(null, android.graphics.Typeface.BOLD);
                nameText.setTextColor(context.getColor(android.R.color.black));
            } else {
                // 优先级 2: 未选中状态 - 实现交替背景色 (Zebra Striping)
                if (position % 2 == 0) {
                    // 偶数行：浅紫色背景 (#F2EFFD)
                    itemRoot.setBackgroundColor(context.getColor(R.color.sidebar_bg_even));
                    nameText.setTextColor(context.getColor(R.color.sidebar_text_normal_dark));
                } else {
                    // 奇数行：浅灰色背景 (#F5F5F5)
                    itemRoot.setBackgroundColor(context.getColor(R.color.sidebar_bg_odd));
                    nameText.setTextColor(context.getColor(R.color.sidebar_text_normal));
                }
                // 恢复文字为正常
                nameText.setTypeface(null, android.graphics.Typeface.NORMAL);
            }

            // 检查文本长度，必要时调整显示效果
            if (categoryName != null && categoryName.length() > 20) {
                // 对于很长的文本，允许显示两行
                nameText.setMaxLines(2);
                nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            } else {
                // 对于较短的文本，限制为单行以保持一致性
                nameText.setMaxLines(1);
                nameText.setEllipsize(android.text.TextUtils.TruncateAt.END);
            }

            // 设置点击事件
            itemRoot.setOnClickListener(v -> {
                if (listener != null) {
                    int currentPosition = getAbsoluteAdapterPosition();
                    if (currentPosition != RecyclerView.NO_POSITION) {
                        setSelectedPosition(currentPosition);
                        listener.onCategoryClick(item, currentPosition);
                    }
                }
            });

            // 设置无障碍描述
            itemView.setContentDescription("Category " + categoryName);
        }

        
        private String translateCategory(Context context, String categoryName) {
            int categoryNameId = getCategoryResource(context, categoryName);
            return categoryNameId == 0 ? categoryName : context.getString(categoryNameId);
        }

        private int getCategoryResource(Context context, @NonNull String categoryName) {
            String suffix = categoryName.replace(" & ", "_").replace(" ", "_").replace("'", "");
            return context.getResources().getIdentifier("category_" + suffix, "string", context.getPackageName());
        }
    }
}