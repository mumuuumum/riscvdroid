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

// 监听状态变化（严格对应 AddRepoIntroScreen 逻辑）
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repoManager.addRepoState.collect { state ->
                    Log.d("AddRepoDebug", "State: ${state.javaClass.simpleName} → $state")

                    btnConfirmAdd.visibility = View.GONE  // 默认隐藏按钮

                    when (state) {
                        is None -> {
                            progress.visibility = View.GONE
                            btnFetch.visibility = View.VISIBLE
                            tvStatus.text = "请输入仓库地址并点击“获取仓库信息”"
                            tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, android.R.color.black))
                        }

                        is Fetching -> {
                            progress.visibility = View.VISIBLE
                            if (state.receivedRepo == null) {
                                // 对应 RepoProgressScreen
                                tvStatus.text = "正在获取仓库信息...\n已解析 ${state.apps} 个应用"
                                tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, android.R.color.black))
                            } else {
                                // 对应 RepoPreviewScreen：显示预览，按钮可见
                                progress.visibility = View.GONE
                                btnConfirmAdd.visibility = View.VISIBLE
                                val repoName = state.receivedRepo.name ?: "未知仓库"
                                val appCount = state.apps
                                val url = state.fetchUrl
                                val fingerprint = state.receivedRepo.fingerprint?.take(16) ?: "无指纹"
                                tvStatus.text = """
                                    仓库预览就绪：
                                    名称：$repoName
                                    应用数量：$appCount
                                    地址：$url
                                    指纹：$fingerprint...
                                    
                                    确认无误后点击下方按钮添加
                                """.trimIndent()
                                tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, android.R.color.black))
                            }
                        }

                        is Adding -> {
                            progress.visibility = View.VISIBLE
                            btnConfirmAdd.visibility = View.GONE
                            tvStatus.text = "正在添加仓库到客户端..."
                            tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, android.R.color.black))
                        }

                        is Added -> {
                            progress.visibility = View.GONE
                            btnConfirmAdd.visibility = View.GONE
                            RepoUpdateWorker.updateNow(applicationContext, state.repo.repoId)
                            val intent = Intent(this@AddRepoActivity, AppListActivity::class.java).apply {
                                putExtra(EXTRA_REPO_ID, state.repo.repoId)
                            }
                            startActivity(intent)
                            finish()
                        }

                        is AddRepoError -> {
                            progress.visibility = View.GONE
                            btnConfirmAdd.visibility = View.GONE
                            val errMsg = state.cause?.message ?: state.toString() ?: "未知错误"
                            tvStatus.text = "添加失败：$errMsg\n请检查地址、指纹或网络"
                            tvStatus.setTextColor(ContextCompat.getColor(this@AddRepoActivity, R.color.error_red))
                        }

                        else -> {
                            progress.visibility = View.GONE
                            tvStatus.text = "未知状态：${state.javaClass.simpleName}"
                            Log.w("AddRepoDebug", "未处理状态: $state")
                        }
                    }
                }
            }
        }
// 获取按钮
        btnFetch.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "请输入仓库地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onFetchRepo(url)
        }

        // 确认添加按钮（手动触发添加）
        btnConfirmAdd.setOnClickListener {
            try {
                repoManager.addFetchedRepository()
            } catch (e: Exception) {
                tvStatus.text = "添加失败：${e.message ?: "状态异常，请重试"}"
                Log.e("AddRepo", "手动添加异常", e)
            }
        }

// 外部 Intent 处理
        addOnNewIntentListener { intent ->
            when (intent.action) {
                Intent.ACTION_VIEW -> intent.dataString?.let { onFetchRepo(it) }
                Intent.ACTION_SEND -> intent.getStringExtra(EXTRA_TEXT)?.let { fetchIfRepoUri(it) }
                else -> {}
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
        progress.visibility = View.VISIBLE
        tvStatus.text = "正在解析地址并开始获取..."
        btnConfirmAdd.visibility = View.GONE

        val uri = Uri.parse(uriStr.trim())
        if (repoManager.isSwapUri(uri)) {
            val swapIntent = Intent(this, SwapService::class.java).apply { data = uri }
            ContextCompat.startForegroundService(this, swapIntent)
        } else {
            repoManager.abortAddingRepository()  // 清空旧状态，非常重要！
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
