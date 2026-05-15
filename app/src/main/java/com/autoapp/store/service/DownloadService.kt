package com.autoapp.store.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autoapp.store.installer.PackageInstallerHelper
import com.autoapp.store.data.remote.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "autoapp_downloads"
        private const val NOTIF_ID = 1001

        const val EXTRA_URL     = "download_url"
        const val EXTRA_APP_ID  = "app_id"
        const val EXTRA_VERSION = "version"

        // ── LocalBroadcast Actions & Extras ──────────────────────────
        /** 下载进度更新 */
        const val ACTION_PROGRESS = "com.autoapp.store.DOWNLOAD_PROGRESS"
        /** 下载/安装完成（成功） */
        const val ACTION_SUCCESS  = "com.autoapp.store.DOWNLOAD_SUCCESS"
        /** 下载/安装失败 */
        const val ACTION_ERROR    = "com.autoapp.store.DOWNLOAD_ERROR"

        const val EXTRA_PROGRESS_APP_ID    = "progress_app_id"
        const val EXTRA_PROGRESS_PERCENT   = "progress_percent"    // 0-100, -1=indeterminate
        const val EXTRA_PROGRESS_DOWNLOADED = "progress_downloaded" // bytes
        const val EXTRA_PROGRESS_TOTAL     = "progress_total"      // bytes, -1=unknown
        const val EXTRA_PROGRESS_LABEL     = "progress_label"      // 显示文字
        const val EXTRA_ERROR_MESSAGE      = "error_message"

        fun start(context: Context, url: String, appId: String, version: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_APP_ID, appId)
                putExtra(EXTRA_VERSION, version)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationManager: NotificationManager
    private lateinit var lbm: LocalBroadcastManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        lbm = LocalBroadcastManager.getInstance(this)
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("准备下载...", -1))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url     = intent?.getStringExtra(EXTRA_URL)    ?: return START_NOT_STICKY
        val appId   = intent.getStringExtra(EXTRA_APP_ID)  ?: ""
        val version = intent.getStringExtra(EXTRA_VERSION) ?: "unknown"

        serviceScope.launch {
            try {
                downloadAndInstall(url, appId, version)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                broadcastError(appId, e.message ?: "未知错误")
                withContext(Dispatchers.Main) { showErrorNotification("下载失败: ${e.message}") }
                // 上报失败状态到后端
                reportStatusToBackend(appId, "failed")
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    // ────────────────────────────────────────────────────────────────
    // 核心下载逻辑
    // ────────────────────────────────────────────────────────────────
    private suspend fun downloadAndInstall(url: String, appId: String, version: String) {
        Log.d(TAG, "Starting OkHttp download: $url")

        val destFile = File(
            getExternalFilesDir("apk_downloads") ?: filesDir,
            "${appId}_${version}.apk"
        )
        if (destFile.exists()) destFile.delete()

        // 广播：开始下载
        broadcastProgress(appId, -1, 0, -1, "连接中...")

        withContext(Dispatchers.IO) {
            val client  = RetrofitClient.okHttpClient
            val request = okhttp3.Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body       = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                var lastBroadcastTime = 0L
                var lastNotifTime = 0L

                FileOutputStream(destFile).use { fos ->
                    val buffer = ByteArray(16 * 1024)
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()

                            // 每 200ms 广播一次进度（UI 更新）
                            if (now - lastBroadcastTime > 200) {
                                lastBroadcastTime = now
                                val pct = if (totalBytes > 0)
                                    ((downloaded * 100) / totalBytes).toInt() else -1
                                val label = if (totalBytes > 0)
                                    "${formatSize(downloaded)} / ${formatSize(totalBytes)}  ($pct%)"
                                else
                                    "${formatSize(downloaded)} 已下载"
                                broadcastProgress(appId, pct, downloaded, totalBytes, label)
                            }

                            // 每 500ms 更新一次通知
                            if (now - lastNotifTime > 500) {
                                lastNotifTime = now
                                val pct = if (totalBytes > 0)
                                    ((downloaded * 100) / totalBytes).toInt() else -1
                                val label = if (totalBytes > 0)
                                    "${formatSize(downloaded)} / ${formatSize(totalBytes)}  ($pct%)"
                                else "${formatSize(downloaded)} 已下载"
                                withContext(Dispatchers.Main) {
                                    notificationManager.notify(
                                        NOTIF_ID, buildNotification(label, pct)
                                    )
                                }
                            }
                        }
                        fos.flush()
                    }
                }
                Log.d(TAG, "File saved: ${destFile.absolutePath} (${destFile.length()} bytes)")
            }
        }

        // 基础完整性检查
        if (!destFile.exists() || destFile.length() < 1024) {
            throw Exception("APK 文件无效 (${destFile.length()} bytes)")
        }

        // 广播：下载完成，开始安装
        broadcastProgress(appId, 100, destFile.length(), destFile.length(), "安装中...")
        withContext(Dispatchers.Main) {
            notificationManager.notify(NOTIF_ID, buildNotification("安装中...", 100))
        }

        // 上报下载成功到后端
        reportStatusToBackend(appId, "success")

        // 触发系统安装
        withContext(Dispatchers.Main) {
            PackageInstallerHelper.installApk(this@DownloadService, destFile)
        }

        // 广播：安装触发完毕
        broadcastSuccess(appId)
    }

    // ────────────────────────────────────────────────────────────────
    // LocalBroadcast 广播
    // ────────────────────────────────────────────────────────────────
    private fun broadcastProgress(
        appId: String, percent: Int, downloaded: Long, total: Long, label: String
    ) {
        val intent = Intent(ACTION_PROGRESS).apply {
            putExtra(EXTRA_PROGRESS_APP_ID, appId)
            putExtra(EXTRA_PROGRESS_PERCENT, percent)
            putExtra(EXTRA_PROGRESS_DOWNLOADED, downloaded)
            putExtra(EXTRA_PROGRESS_TOTAL, total)
            putExtra(EXTRA_PROGRESS_LABEL, label)
        }
        lbm.sendBroadcast(intent)
    }

    private fun broadcastSuccess(appId: String) {
        val intent = Intent(ACTION_SUCCESS).apply {
            putExtra(EXTRA_PROGRESS_APP_ID, appId)
        }
        lbm.sendBroadcast(intent)
    }

    private fun broadcastError(appId: String, message: String) {
        val intent = Intent(ACTION_ERROR).apply {
            putExtra(EXTRA_PROGRESS_APP_ID, appId)
            putExtra(EXTRA_ERROR_MESSAGE, message)
        }
        lbm.sendBroadcast(intent)
    }

    // ────────────────────────────────────────────────────────────────
    // 上报下载状态到后端（新增 /api/apps/{id}/download-status 接口）
    // ────────────────────────────────────────────────────────────────
    private suspend fun reportStatusToBackend(appId: String, status: String) {
        try {
            RetrofitClient.apiService.reportDownloadStatus(appId, mapOf("status" to status))
            Log.d(TAG, "Reported status=$status for appId=$appId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to report status: ${e.message}")
            // 上报失败不影响主流程
        }
    }

    // ────────────────────────────────────────────────────────────────
    // 通知管理
    // ────────────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "AutoApp 下载", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "应用下载进度通知"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, percent: Int): android.app.Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoApp Store")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (percent < 0) {
            builder.setProgress(0, 0, true)   // indeterminate
        } else {
            builder.setProgress(100, percent, false)
        }
        return builder.build()
    }

    private fun showErrorNotification(message: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("下载失败")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIF_ID + 1, notif)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024        -> "%.1f KB".format(bytes / 1024.0)
        else                 -> "$bytes B"
    }
}
