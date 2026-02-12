package org.fdroid.fdroid.views.repos

import android.content.Intent
import android.content.Intent.EXTRA_TEXT
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import info.guardianproject.netcipher.NetCipher
import kotlinx.coroutines.launch
import org.fdroid.fdroid.FDroidApp
import org.fdroid.fdroid.Preferences
import org.fdroid.fdroid.R
import org.fdroid.fdroid.nearby.SwapService
import org.fdroid.fdroid.ui.theme.FDroidContent
import org.fdroid.fdroid.views.apps.AppListActivity
import org.fdroid.fdroid.views.apps.AppListActivity.EXTRA_REPO_ID
import org.fdroid.fdroid.work.RepoUpdateWorker
import org.fdroid.index.RepoManager
import org.fdroid.repo.AddRepoError
import org.fdroid.repo.AddRepoState
import org.fdroid.repo.Added
import org.fdroid.repo.Adding
import org.fdroid.repo.FetchResult
import org.fdroid.repo.Fetching
import org.fdroid.repo.None
import kotlin.text.RegexOption.IGNORE_CASE
import kotlin.text.RegexOption.MULTILINE
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope

class AddRepoActivity : AppCompatActivity() {

    // Use a getter here, otherwise this tries to access Context too early causing NPE
    private val repoManager: RepoManager get() = FDroidApp.getRepoManager(this)

    private lateinit var etUrl: EditText
    private lateinit var btnFetch: Button
    private lateinit var progress: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_repo)


        // 找到视图
        etUrl    = findViewById<EditText>(R.id.et_repo_url)
        btnFetch = findViewById<Button>(R.id.btn_fetch)
        progress = findViewById<ProgressBar>(R.id.progress)
        tvStatus = findViewById<TextView>(R.id.tv_status)

// 监听添加状态（保持原有逻辑）
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repoManager.addRepoState.collect { state ->
                    Log.d("AddRepoDebug", "State: ${state.javaClass.simpleName} - $state")  // 保持调试日志

                    when (state) {
                        is None -> {
                            progress.visibility = View.GONE
                            tvStatus.text = "请输入仓库地址并点击获取"
                            tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, android.R.color.black))
                        }

                        is Fetching -> {
                            progress.visibility = View.VISIBLE
                            if (state.receivedRepo == null) {
                                // 还在下载/解析索引
                                tvStatus.text = "正在下载仓库索引..."
                                tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, android.R.color.black))
                            } else {
                                // 已获取到仓库预览 → 自动添加（核心改动！）
                                tvStatus.text = "仓库信息已获取，正在自动添加..."
                                tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, android.R.color.black))

                                // 自动调用添加（相当于原版点击“添加仓库”按钮）
                                repoManager.addFetchedRepository()
                                // 注意：添加后状态会很快变为 Adding / Added，不需要额外处理
                            }
                        }

                        is Adding -> {
                            progress.visibility = View.VISIBLE
                            tvStatus.text = "正在添加仓库到客户端..."
                            tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, android.R.color.black))
                        }

                        is Added -> {
                            progress.visibility = View.GONE
                            // update newly added repo
                            RepoUpdateWorker.updateNow(applicationContext, state.repo.repoId)
                            // 跳转到应用列表（带新仓库 ID）
                            val i = Intent(this@AddRepoActivity, AppListActivity::class.java).apply {
                                putExtra(EXTRA_REPO_ID, state.repo.repoId)
                            }
                            startActivity(i)
                            finish()
                        }

                        is AddRepoError -> {
                            progress.visibility = View.GONE
                            val errMsg = state.cause?.message ?: state.toString() ?: "未知错误"
                            tvStatus.text = "添加失败：$errMsg"
                            tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, R.color.error_red))
                        }

                        else -> {
                            progress.visibility = View.GONE
                            tvStatus.text = "未知状态：${state.javaClass.simpleName}"
                            Log.w("AddRepoDebug", "未处理的 AddRepoState: $state")
                        }
                    }
                }
            }
        }
        // Fetch 按钮点击
        btnFetch.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                onFetchRepo(url)
            } else {
                Toast.makeText(this, "请输入仓库地址", Toast.LENGTH_SHORT).show()
            }
        }

        // 处理外部 Intent（保持不变）
        addOnNewIntentListener { intent ->
            when (intent.action) {
                Intent.ACTION_VIEW -> intent.dataString?.let { onFetchRepo(it) }
                Intent.ACTION_SEND -> intent.getStringExtra(EXTRA_TEXT)?.let { fetchIfRepoUri(it) }
                else -> {}
            }
        }

        intent?.let {
            onNewIntent(it)
            it.setData(null)
            it.replaceExtras(Bundle())
        }

    }

    override fun onResume() {
        super.onResume()
        FDroidApp.checkStartTor(this, Preferences.get())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) repoManager.abortAddingRepository()
    }

    private fun onFetchRepo(uriStr: String) {
        val uri = Uri.parse(uriStr.trim())
        if (repoManager.isSwapUri(uri)) {
            val i = Intent(this, SwapService::class.java).apply {
                data = uri
            }
            ContextCompat.startForegroundService(this, i)
        } else {
            repoManager.abortAddingRepository()
            repoManager.fetchRepositoryPreview(uri.toString(), proxy = NetCipher.getProxy())
        }
    }

    private fun fetchIfRepoUri(str: String) {
        // try direct https/fdroidrepos URIs first
        val repoUriMatch = Regex(
            pattern = "^.*((https|fdroidrepos)://.+/repo(\\?fingerprint=[A-F0-9]+)?) ?.*$",
            options = setOf(IGNORE_CASE, MULTILINE),
        ).find(str)?.groups?.get(1)?.value
        if (repoUriMatch != null) {
            Log.d(this::class.simpleName, "Found match: $repoUriMatch")
            onFetchRepo(repoUriMatch)
            return // found, no need to continue
        }
        // now try fdroid.link URIs
        val repoLinkMatch = Regex(
            pattern = "^.*(https://fdroid.link/.+) ?.*$",
            options = setOf(IGNORE_CASE, MULTILINE),
        ).find(str)?.groups?.get(1)?.value
        if (repoLinkMatch != null) {
            Log.d(this::class.simpleName, "Found match: $repoLinkMatch")
            onFetchRepo(repoLinkMatch)
            return // found, no need to continue
        }
        // no URI found
        Toast.makeText(this, R.string.repo_share_not_found, Toast.LENGTH_LONG).show()
        finishAfterTransition()
    }
}
