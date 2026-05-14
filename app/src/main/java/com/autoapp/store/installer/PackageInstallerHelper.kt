package com.autoapp.store.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

/**
 * PackageInstallerHelper — 兼容车机（Android Automotive）的 APK 安装器。
 *
 * 安装策略（按优先级依次尝试）：
 *   1. PackageInstaller Session API（Android 5.0+，推荐）
 *   2. Intent ACTION_VIEW fallback（兼容老设备 / 车机限制场景）
 *
 * 车机注意事项：
 *   - REQUEST_INSTALL_PACKAGES 权限在车机上可能为 signature 级别，需用户在设置中手动授权
 *   - Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES 在部分车机 ROM 中不存在，已加 try-catch
 *   - 安装确认弹窗由系统触发（STATUS_PENDING_USER_ACTION），InstallReceiver 负责转发
 */
object PackageInstallerHelper {

    private const val TAG = "PackageInstallerHelper"

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
            Toast.makeText(context, "安装失败：APK 文件不存在", Toast.LENGTH_LONG).show()
            return
        }
        Log.d(TAG, "Installing APK: ${apkFile.absolutePath} (${apkFile.length()} bytes)")

        // 优先使用 PackageInstaller Session API
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            installViaSessionApi(context, apkFile)
        } else {
            installViaIntent(context, apkFile)
        }
    }

    // ----------------------------------------------------------------
    // 方式 1：PackageInstaller Session API（现代推荐方式）
    // ----------------------------------------------------------------
    private fun installViaSessionApi(context: Context, apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller

        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            // 车机上设置安装来源标识，便于系统日志追踪
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        try {
            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // 将 APK 写入 Session
            FileInputStream(apkFile).use { input ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                    session.fsync(output)
                }
            }

            // 构建安装回调 Intent，由 InstallReceiver 处理结果
            val intent = Intent(context, InstallReceiver::class.java).apply {
                action = "com.autoapp.store.ACTION_INSTALL_COMPLETE"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            session.commit(pendingIntent.intentSender)
            session.close()

            Log.d(TAG, "Session committed, waiting for system install dialog (sessionId=$sessionId)")
            Toast.makeText(context, "安装中，请在弹窗中确认...", Toast.LENGTH_LONG).show()

        } catch (e: SecurityException) {
            // REQUEST_INSTALL_PACKAGES 权限被拒绝 — 回退到 Intent 方式
            Log.w(TAG, "Session API denied (SecurityException), falling back to Intent: ${e.message}")
            Toast.makeText(context, "请在系统设置中允许安装未知来源应用", Toast.LENGTH_LONG).show()
            installViaIntent(context, apkFile)

        } catch (e: Exception) {
            Log.e(TAG, "Session API failed: ${e.message}", e)
            Toast.makeText(context, "安装失败，尝试备用方式...", Toast.LENGTH_SHORT).show()
            installViaIntent(context, apkFile)
        }
    }

    // ----------------------------------------------------------------
    // 方式 2：Intent ACTION_VIEW fallback（兼容老设备/限制 ROM）
    // ----------------------------------------------------------------
    private fun installViaIntent(context: Context, apkFile: File) {
        try {
            val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } else {
                @Suppress("DEPRECATION")
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            // 检查是否有 Activity 能处理此 Intent（车机上可能不存在安装界面）
            if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
                context.startActivity(intent)
                Log.d(TAG, "Install Intent launched via ACTION_VIEW")
            } else {
                Log.e(TAG, "No Activity found to handle install intent on this device")
                Toast.makeText(
                    context,
                    "此设备不支持直接安装，请联系管理员开启安装权限",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Intent install also failed: ${e.message}", e)
            Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
