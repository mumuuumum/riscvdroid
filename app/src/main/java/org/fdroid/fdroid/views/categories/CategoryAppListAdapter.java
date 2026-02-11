package org.fdroid.fdroid.views.categories;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import org.fdroid.database.AppOverviewItem;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.views.AppDetailsActivity;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 右侧应用列表的RecyclerView适配器
 * 负责显示选中分类下的所有应用，每个应用一行显示
 */
public class CategoryAppListAdapter extends ListAdapter<AppOverviewItem, CategoryAppListAdapter.ViewHolder> {

    private static final DiffUtil.ItemCallback<AppOverviewItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<AppOverviewItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull AppOverviewItem oldItem, @NonNull AppOverviewItem newItem) {
                    return oldItem.getPackageName().equals(newItem.getPackageName());
                }

                @Override
                public boolean areContentsTheSame(@NonNull AppOverviewItem oldItem, @NonNull AppOverviewItem newItem) {
                    return oldItem.getLastUpdated() == newItem.getLastUpdated();
                }
            };

    /**
     * After this many days, don't consider showing the "New" tag next to an app.
     */
    private static final int DAYS_TO_CONSIDER_NEW = 14;

    public interface OnAppClickListener {
        void onAppClick(AppOverviewItem app);
        void onAppInstall(AppOverviewItem app);
    }

    private final AppCompatActivity activity;
    private OnAppClickListener listener;

    public CategoryAppListAdapter(AppCompatActivity activity) {
        super(DIFF_CALLBACK);
        this.activity = activity;
    }

    public void setOnAppClickListener(OnAppClickListener listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setApps(@NonNull List<AppOverviewItem> apps) {
        submitList(new ArrayList<>(apps));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(activity)
                .inflate(R.layout.category_app_list_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppOverviewItem app = getItem(position);
        holder.bind(app);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView nameText;
        private final TextView summaryText;
        private final TextView metadataText;
        private final MaterialButton installButton;
        private final View newTag;

        ViewHolder(View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.icon);
            nameText = itemView.findViewById(R.id.name);
            summaryText = itemView.findViewById(R.id.summary);
            metadataText = itemView.findViewById(R.id.metadata);
            installButton = itemView.findViewById(R.id.install_button);
            newTag = itemView.findViewById(R.id.new_tag);
        }

        void bind(AppOverviewItem app) {
            // 设置应用图标 - 使用Utils.setIconFromRepoOrPM方法
            Utils.setIconFromRepoOrPM(app, iconView, iconView.getContext());

            // 设置应用名称
            nameText.setText(app.getName());

            // 设置应用描述
            summaryText.setText(app.getSummary());

            // 设置元数据（简化版）
            String metadata = buildMetadataString(app);
            metadataText.setText(metadata);

            // 设置新标签
            if (isConsideredNew(app)) {
                newTag.setVisibility(View.VISIBLE);
            } else {
                newTag.setVisibility(View.GONE);
            }

            // 设置安装按钮状态和文本（简化版）
            installButton.setText(R.string.menu_install);

            // 设置点击事件
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAppClick(app);
                }
            });

            installButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAppInstall(app);
                }
            });

            // 设置无障碍描述
            String contentDescription = app.getName() + ", " + app.getSummary();
            itemView.setContentDescription(contentDescription);

            // 设置安装按钮无障碍描述
            installButton.setContentDescription("Install " + app.getName());
        }

        private String buildMetadataString(AppOverviewItem app) {
            StringBuilder metadata = new StringBuilder();

            // 简化版元数据，只显示应用名称
            // 实际的版本大小、评分、下载量信息需要从数据库获取，这里暂时不显示

            return metadata.toString();
        }

        private boolean isConsideredNew(@NonNull AppOverviewItem app) {
            return app.getAdded() == app.getLastUpdated() &&
                    Utils.daysSince(app.getAdded()) <= DAYS_TO_CONSIDER_NEW;
        }
    }
}