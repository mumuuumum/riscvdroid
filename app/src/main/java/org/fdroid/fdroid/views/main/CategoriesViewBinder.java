package org.fdroid.fdroid.views.main;

import android.content.Intent;
import android.util.ArraySet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.os.LocaleListCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.fdroid.database.AppOverviewItem;
import org.fdroid.database.Category;
import org.fdroid.database.FDroidDatabase;
import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.DBHelper;
import org.fdroid.fdroid.panic.HidingManager;
import org.fdroid.fdroid.views.AppDetailsActivity;
import org.fdroid.fdroid.views.apps.AppListActivity;
import org.fdroid.fdroid.views.categories.CategoryAppListAdapter;
import org.fdroid.fdroid.views.categories.CategoryItem;
import org.fdroid.fdroid.views.categories.CategorySidebarAdapter;
import org.fdroid.fdroid.work.RepoUpdateWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 双栏布局分类页面的控制器
 * 负责管理左侧分类侧边栏和右侧应用列表的联动逻辑
 * 已重构为使用双栏联动布局替代原来的垂直滚动布局
 */
class CategoriesViewBinder implements Observer<List<Category>> {

    public static final String TAG = "CategoriesViewBinder";

    private final AppCompatActivity activity;
    private final FDroidDatabase db;
    private final String[] defaultCategories;
    private final CategorySidebarAdapter categoryAdapter;
    private final CategoryAppListAdapter appAdapter;
    private final View emptyState;
    private final RecyclerView categorySidebar;
    private final RecyclerView appsList;
    @Nullable
    private Disposable disposable;

    // 缓存分类对应的应用数据，避免重复查询
    private final HashMap<String, LiveData<List<AppOverviewItem>>> categoryAppLiveData = new HashMap<>();
    private String currentCategoryId = null;

    CategoriesViewBinder(final AppCompatActivity activity, FrameLayout parent) {
        this.activity = activity;
        db = DBHelper.getDb(activity);
        Transformations.distinctUntilChanged(db.getRepositoryDao().getLiveCategories()).observe(activity, this);
        defaultCategories = activity.getResources().getStringArray(R.array.defaultCategories);

        // 使用新的双栏布局
        View categoriesView = activity.getLayoutInflater().inflate(R.layout.main_tab_categories_dual_pane, parent, true);

        // 初始化适配器
        categoryAdapter = new CategorySidebarAdapter(activity);
        appAdapter = new CategoryAppListAdapter(activity);

        // 设置分类侧边栏
        emptyState = categoriesView.findViewById(R.id.empty_state_container);
        categorySidebar = categoriesView.findViewById(R.id.category_sidebar);
        categorySidebar.setHasFixedSize(true);
        categorySidebar.setLayoutManager(new LinearLayoutManager(activity));
        categorySidebar.setAdapter(categoryAdapter);

        // 设置应用列表
        appsList = categoriesView.findViewById(R.id.apps_list);
        appsList.setHasFixedSize(true);
        appsList.setLayoutManager(new LinearLayoutManager(activity));
        appsList.setAdapter(appAdapter);

        // 设置下拉刷新
        final SwipeRefreshLayout swipeToRefresh = categoriesView.findViewById(R.id.swipe_to_refresh);
        Utils.applySwipeLayoutColors(swipeToRefresh);
        swipeToRefresh.setOnRefreshListener(() -> {
            swipeToRefresh.setRefreshing(false);
            RepoUpdateWorker.updateNow(activity);
        });

        // 设置搜索按钮
        FloatingActionButton searchFab = categoriesView.findViewById(R.id.fab_search);
        searchFab.setOnClickListener(v -> activity.startActivity(new Intent(activity, AppListActivity.class)));
        searchFab.setOnLongClickListener(view -> {
            if (Preferences.get().hideOnLongPressSearch()) {
                HidingManager.showHideDialog(activity);
                return true;
            } else {
                return false;
            }
        });

        // 设置分类点击监听器
        categoryAdapter.setOnCategoryClickListener(this::onCategorySelected);

        // 设置应用点击监听器
        appAdapter.setOnAppClickListener(new CategoryAppListAdapter.OnAppClickListener() {
            @Override
            public void onAppClick(AppOverviewItem app) {
                Intent intent = new Intent(activity, AppDetailsActivity.class);
                intent.putExtra(AppDetailsActivity.EXTRA_APPID, app.getPackageName());
                activity.startActivity(intent);
            }

            @Override
            public void onAppInstall(AppOverviewItem app) {
                // 这里处理应用的安装逻辑
                // 可以调用现有的安装功能
                Intent intent = new Intent(activity, AppDetailsActivity.class);
                intent.putExtra(AppDetailsActivity.EXTRA_APPID, app.getPackageName());
                activity.startActivity(intent);
            }
        });
    }

    /**
     * 当分类数据变化时被调用
     */
    @Override
    public void onChanged(List<Category> categories) {
        if (disposable != null) disposable.dispose();
        disposable = Single.fromCallable(() -> loadCategoryItems(categories))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onCategoriesLoaded);
    }

    /**
     * 处理分类选择事件
     */
    private void onCategorySelected(CategoryItem category, int position) {
        String categoryId = category.category.getId();
        Log.d(TAG, "Category selected: " + categoryId);

        if (currentCategoryId != null && currentCategoryId.equals(categoryId)) {
            // 如果是同一个分类，不需要重新加载
            return;
        }

        currentCategoryId = categoryId;

        // 加载选中分类的应用
        loadAppsForCategory(categoryId);
    }

    /**
     * 加载指定分类的应用
     */
    private void loadAppsForCategory(String categoryId) {
        // 每次都重新获取数据，避免复杂的缓存逻辑
        LiveData<List<AppOverviewItem>> liveData = db.getAppDao().getAppOverviewItems(categoryId, Integer.MAX_VALUE);

        liveData.observe(activity, new Observer<List<AppOverviewItem>>() {
            @Override
            public void onChanged(List<AppOverviewItem> apps) {
                if (categoryId.equals(currentCategoryId)) {
                    // 确保这个观察者仍然对应当前选中的分类
                    appAdapter.setApps(apps);

                    // 如果有应用，隐藏空状态；否则显示空状态
                    if (apps.isEmpty()) {
                        showEmptyState();
                    } else {
                        hideEmptyState();
                    }
                }
            }
        });
    }

    /**
     * 分类数据加载完成后的处理
     */
    private void onCategoriesLoaded(List<CategoryItem> items) {
        if (items.size() == 0) {
            showEmptyState();
        } else {
            hideEmptyState();
            categoryAdapter.setCategories(items);

            // 默认选中第一个分类
            if (!items.isEmpty()) {
                categoryAdapter.setSelectedPosition(0);
                onCategorySelected(items.get(0), 0);
            }
        }
    }

    /**
     * 显示空状态
     */
    private void showEmptyState() {
        emptyState.setVisibility(View.VISIBLE);
        appsList.setVisibility(View.GONE);
    }

    /**
     * 隐藏空状态
     */
    private void hideEmptyState() {
        emptyState.setVisibility(View.GONE);
        appsList.setVisibility(View.VISIBLE);
    }

    /**
     * 从数据库加载分类数据，包括默认分类
     */
    @WorkerThread
    private List<CategoryItem> loadCategoryItems(List<Category> categories) {
        ArrayList<CategoryItem> items = new ArrayList<>();
        ArraySet<String> ids = new ArraySet<>(categories.size());

        // 加载数据库中的分类
        for (Category c : categories) {
            int numApps = db.getAppDao().getNumberOfAppsInCategory(c.getId());
            if (numApps > 0) {
                ids.add(c.getId());
                CategoryItem item = new CategoryItem(c, numApps);
                items.add(item);
            } else {
                Log.d(TAG, "Not adding " + c.getId() + " because it has no apps.");
            }
        }

        // 添加默认分类
        for (String id : defaultCategories) {
            if (!ids.contains(id)) {
                int numApps = db.getAppDao().getNumberOfAppsInCategory(id);
                if (numApps > 0) {
                    // 名称和图标会在CategorySidebarAdapter中设置，这里不设置
                    Category c = new Category(2L, id, Collections.emptyMap(), Collections.emptyMap(),
                            Collections.emptyMap());
                    CategoryItem item = new CategoryItem(c, numApps);
                    items.add(item);
                } else {
                    Log.d(TAG, "Not adding default " + id + " because it has no apps.");
                }
            }
        }

        return items;
    }
}