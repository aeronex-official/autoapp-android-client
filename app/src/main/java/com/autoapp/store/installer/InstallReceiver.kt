package com.autoapp.store.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast

class InstallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "InstallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val msg    = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val pkgName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        Log.d(TAG, "onReceive: status=$status, pkg=$pkgName, msg=$msg")

        when (status) {

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // 系统要求用户手动确认安装。
                // ⚠️ 关键修复：不能直接 context.startActivity()——
                //   BroadcastReceiver 的 context 是非 Activity Context，
                //   Android 10+ 和车机 ROM 会静默阻止后台 Activity 启动。
                // 解决方案：通过透明的 InstallConfirmActivity 作为中转跳板。
                @Suppress("DEPRECATION")
                val confirmIntent: Intent? = intent.getParcelableExtra(Intent.EXTRA_INTENT)

                if (confirmIntent != null) {
                    Log.d(TAG, "STATUS_PENDING_USER_ACTION — launching via InstallConfirmActivity")
                    try {
                        val launchIntent = InstallConfirmActivity.buildLaunchIntent(context, confirmIntent)
                        context.startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch InstallConfirmActivity: ${e.message}", e)
                        Toast.makeText(context, "请手动点击安装确认", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Log.e(TAG, "STATUS_PENDING_USER_ACTION but confirmIntent is null")
                    Toast.makeText(context, "安装确认失败，请重试", Toast.LENGTH_LONG).show()
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.d(TAG, "Installation SUCCESS: $pkgName")
                Toast.makeText(context, "✅ 安装成功！", Toast.LENGTH_SHORT).show()
            }

            PackageInstaller.STATUS_FAILURE -> {
                Log.e(TAG, "Installation FAILURE: $msg")
                Toast.makeText(context, "❌ 安装失败: $msg", Toast.LENGTH_LONG).show()
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // 用户点击了"取消"
                Log.w(TAG, "Installation ABORTED by user")
                Toast.makeText(context, "已取消安装", Toast.LENGTH_SHORT).show()
            }

            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                Log.e(TAG, "Installation BLOCKED: $msg")
                Toast.makeText(
                    context,
                    "安装被系统阻止，请在「设置→安全」中允许安装未知来源",
                    Toast.LENGTH_LONG
                ).show()
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                Log.e(TAG, "Installation CONFLICT: $msg")
                Toast.makeText(
                    context,
                    "版本冲突，请先卸载旧版本再安装",
                    Toast.LENGTH_LONG
                ).show()
            }

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                Log.e(TAG, "Installation INCOMPATIBLE: $msg")
                Toast.makeText(
                    context,
                    "APK 与此设备不兼容: $msg",
                    Toast.LENGTH_LONG
                ).show()
            }

            PackageInstaller.STATUS_FAILURE_INVALID -> {
                Log.e(TAG, "Installation INVALID APK: $msg")
                Toast.makeText(
                    context,
                    "APK 文件损坏或无效，请重新下载",
                    Toast.LENGTH_LONG
                ).show()
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Log.e(TAG, "Installation STORAGE failure: $msg")
                Toast.makeText(
                    context,
                    "存储空间不足，请清理后重试",
                    Toast.LENGTH_LONG
                ).show()
            }

            else -> {
                Log.e(TAG, "Unknown install status=$status, msg=$msg")
                Toast.makeText(context, "安装状态未知($status): $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
