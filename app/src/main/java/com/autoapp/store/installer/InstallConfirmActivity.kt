package com.autoapp.store.installer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

/**
 * InstallConfirmActivity — 透明 Activity，专门用于弹出系统安装确认对话框。
 *
 * 问题根因：
 *   PackageInstaller.STATUS_PENDING_USER_ACTION 需要调用 startActivity(confirmIntent)。
 *   但 InstallReceiver 是 BroadcastReceiver，其 Context 是非 Activity Context（来自 Service）。
 *   在 Android 10+ 和车机定制 ROM 上，从非 Activity Context 直接 startActivity() 会被系统
 *   静默阻止（BackgroundActivityStartRestriction），导致安装弹窗永远不出现。
 *
 * 解决方案：
 *   通过一个透明的 Activity（InstallConfirmActivity）作为中转站：
 *   InstallReceiver → 启动 InstallConfirmActivity（allowBackgroundActivityStart=true 因为
 *   它是通过 PendingIntent 回调的）→ InstallConfirmActivity.onCreate() 弹出系统安装弹窗。
 *
 * 使用方式：
 *   在 InstallReceiver.onReceive() 的 STATUS_PENDING_USER_ACTION 分支中，
 *   不再直接 startActivity(confirmIntent)，而是把 confirmIntent 包在这个 Activity 里启动。
 */
class InstallConfirmActivity : Activity() {

    companion object {
        private const val TAG = "InstallConfirmActivity"
        const val EXTRA_CONFIRM_INTENT = "confirm_intent"

        /**
         * 构建启动此 Activity 的 Intent，把系统下发的 confirmIntent 包裹进来。
         */
        fun buildLaunchIntent(context: android.content.Context, confirmIntent: Intent): Intent {
            return Intent(context, InstallConfirmActivity::class.java).apply {
                putExtra(EXTRA_CONFIRM_INTENT, confirmIntent)
                // 从非 Activity Context 启动 Activity 必须加 FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // 避免重复创建实例，防止多次点击导致多个安装弹窗叠加
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不设置 setContentView —— Activity 使用 Theme.Translucent，完全透明

        @Suppress("DEPRECATION")
        val confirmIntent: Intent? = intent?.getParcelableExtra(EXTRA_CONFIRM_INTENT)

        if (confirmIntent == null) {
            Log.e(TAG, "confirmIntent is null, finishing immediately")
            finish()
            return
        }

        // confirmIntent 是系统 PackageInstaller 下发的，必须在 Activity 中启动
        // 此时我们处于 Activity 上下文，不受后台启动限制
        try {
            Log.d(TAG, "Starting system install confirmation dialog")
            startActivity(confirmIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start confirmIntent: ${e.message}", e)
        }

        // 立即 finish，这个透明 Activity 只作为跳板，不显示任何 UI
        finish()
    }
}
