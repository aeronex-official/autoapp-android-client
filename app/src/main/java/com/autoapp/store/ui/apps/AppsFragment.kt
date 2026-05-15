package com.autoapp.store.ui.apps

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.autoapp.store.data.local.PrefsManager
import com.autoapp.store.databinding.FragmentAppsBinding
import com.autoapp.store.service.DownloadService
import com.autoapp.store.ui.viewmodel.AppsViewModel
import com.autoapp.store.ui.viewmodel.SubscriptionViewModel

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AppsViewModel
    private lateinit var subViewModel: SubscriptionViewModel
    private lateinit var adapter: AppAdapter

    // 记录用户点击的 appId，等权限/订阅检查通过后再触发下载
    private var pendingDownloadAppId: String? = null

    // Android 13+ 通知权限请求
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 无论结果如何都继续，通知只是辅助 */ }

    // ── LocalBroadcast receiver for download progress ──────────────
    private val downloadProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val appId = intent.getStringExtra(DownloadService.EXTRA_PROGRESS_APP_ID) ?: return
            when (intent.action) {
                DownloadService.ACTION_PROGRESS -> {
                    val percent = intent.getIntExtra(DownloadService.EXTRA_PROGRESS_PERCENT, -1)
                    val label   = intent.getStringExtra(DownloadService.EXTRA_PROGRESS_LABEL) ?: ""
                    adapter.setDownloading(appId, percent, label)
                }
                DownloadService.ACTION_SUCCESS -> {
                    adapter.setSuccess(appId)
                }
                DownloadService.ACTION_ERROR -> {
                    val msg = intent.getStringExtra(DownloadService.EXTRA_ERROR_MESSAGE) ?: "下载失败"
                    adapter.setError(appId, msg)
                }
            }
        }
    }

    private lateinit var lbm: LocalBroadcastManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel    = ViewModelProvider(this)[AppsViewModel::class.java]
        subViewModel = ViewModelProvider(this)[SubscriptionViewModel::class.java]

        adapter = AppAdapter { app -> onAppClicked(app.id) }

        binding.recyclerView.layoutManager = GridLayoutManager(context, 2)
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadApps() }

        // ── 注册 LocalBroadcast 接收下载进度 ─────────────────────────
        lbm = LocalBroadcastManager.getInstance(requireContext())
        val filter = IntentFilter().apply {
            addAction(DownloadService.ACTION_PROGRESS)
            addAction(DownloadService.ACTION_SUCCESS)
            addAction(DownloadService.ACTION_ERROR)
        }
        lbm.registerReceiver(downloadProgressReceiver, filter)

        // ---- Observers ----

        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            adapter.submitList(apps)
            binding.swipeRefresh.isRefreshing = false
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        }

        // 下载 URL 拿到后启动 DownloadService
        viewModel.downloadUrl.observe(viewLifecycleOwner) { info ->
            info ?: return@observe
            viewModel.clearDownloadUrl()

            // 申请通知权限（Android 13+），不影响下载本身
            requestNotifPermissionIfNeeded()

            Toast.makeText(context, "开始下载：${info.version}", Toast.LENGTH_SHORT).show()
            DownloadService.start(requireContext(), info.downloadUrl, info.appId, info.version)
        }

        // 订阅状态实时同步后，若有 pending 下载则继续
        subViewModel.subscription.observe(viewLifecycleOwner) { sub ->
            val waiting = pendingDownloadAppId ?: return@observe
            if (sub != null && sub.status == "active") {
                pendingDownloadAppId = null
                proceedDownload(waiting)
            } else if (sub != null) {
                // 服务器已回复，但确实没有订阅
                pendingDownloadAppId = null
                Toast.makeText(context, "需要订阅才能下载，请前往「账户」页面开通", Toast.LENGTH_LONG).show()
            }
            // sub == null 时服务器还在请求中，继续等待
        }

        viewModel.loadApps()
    }

    // ----------------------------------------------------------------
    // 点击 App 卡片
    // ----------------------------------------------------------------
    private fun onAppClicked(appId: String) {
        // 1. 检查登录
        if (!PrefsManager.isLoggedIn()) {
            Toast.makeText(context, "请先登录", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. 检查本地缓存的订阅状态
        if (PrefsManager.hasActiveSubscription()) {
            // 已有有效订阅，直接下载
            proceedDownload(appId)
            return
        }

        // 3. 本地无缓存（刚登录 / 缓存过期）→ 实时向服务器查询
        //    查询结果通过 subViewModel.subscription observer 异步处理
        val token = PrefsManager.token ?: return
        pendingDownloadAppId = appId
        Toast.makeText(context, "正在验证订阅状态...", Toast.LENGTH_SHORT).show()
        subViewModel.checkSubscription(token)
    }

    // ----------------------------------------------------------------
    // 真正触发下载（订阅验证通过后调用）
    // ----------------------------------------------------------------
    private fun proceedDownload(appId: String) {
        // 检查"安装未知来源"权限（Android 8+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = requireContext().packageManager.canRequestPackageInstalls()
            if (!canInstall) {
                // 尝试打开设置页；车机如果不支持此 Settings 页会 catch
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${requireContext().packageName}")
                        )
                    )
                    Toast.makeText(
                        context,
                        "请在设置中允许「AutoApp Store」安装未知来源应用，然后重试",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                } catch (e: Exception) {
                    // 车机不支持此设置页 → 直接尝试下载，安装时系统会再次鉴权
                }
            }
        }

        val token = PrefsManager.token ?: return
        viewModel.requestDownload(appId, token)
    }

    // ----------------------------------------------------------------
    // 通知权限（Android 13+，可选）
    // ----------------------------------------------------------------
    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(requireContext(), perm)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(perm)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 注销广播接收器，防止内存泄漏
        lbm.unregisterReceiver(downloadProgressReceiver)
        _binding = null
    }
}
