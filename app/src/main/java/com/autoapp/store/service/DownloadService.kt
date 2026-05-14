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
import com.autoapp.store.installer.PackageInstallerHelper
import com.autoapp.store.data.remote.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * DownloadService — 使用 OkHttp 手动下载，完全绕过 DownloadManager。
 *
 * 原因：比亚迪方程豹等车机 ROM 常常禁用或阉割系统 DownloadManager，
 * 导致 enqueue() 静默失败，ACTION_DOWNLOAD_COMPLETE 广播永远不到来。
 * 改用 OkHttp（项目已依赖 okhttp3:logging-interceptor）直接流式写文件。
 */
class DownloadService : Service() {

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "autoapp_downloads"
        private const val NOTIF_ID = 1001

        const val EXTRA_URL = "download_url"
        const val EXTRA_APP_ID = "app_id"
        const val EXTRA_VERSION = "version"

        fun start(context: Context, url: String, appId: String, version: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_APP_ID, appId)
                putExtra(EXTRA_VERSION, version)
            }
            // Android 8+ 后台服务必须用 startForegroundService，否则 5 秒后被系统杀死
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        // 必须在 onCreate 或 onStartCommand 5 秒内调用，防止 ANR
        startForeground(NOTIF_ID, buildNotification("准备下载...", 0, 0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url     = intent?.getStringExtra(EXTRA_URL)     ?: return START_NOT_STICKY
        val appId   = intent.getStringExtra(EXTRA_APP_ID)   ?: ""
        val version = intent.getStringExtra(EXTRA_VERSION)  ?: "unknown"

        serviceScope.launch {
            try {
                downloadAndInstall(url, appId, version)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.message}", e)
                showErrorNotification("下载失败: ${e.message}")
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    // ----------------------------------------------------------------
    // 核心下载逻辑：OkHttp 流式写文件，支持进度通知
    // ----------------------------------------------------------------
    private suspend fun downloadAndInstall(url: String, appId: String, version: String) {
        Log.d(TAG, "Starting OkHttp download: $url")

        // 下载目标文件：应用私有目录，不需要 WRITE_EXTERNAL_STORAGE 权限
        val destFile = File(getExternalFilesDir("apk_downloads") ?: filesDir, "${appId}_${version}.apk")
        if (destFile.exists()) destFile.delete()

        withContext(Dispatchers.IO) {
            // 复用项目已有的 OkHttpClient（RetrofitClient 暴露的 client）
            val client = RetrofitClient.okHttpClient
            val request = okhttp3.Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw Exception("Empty response body")
                val totalBytes = body.contentLength()   // -1 if unknown
                var downloadedBytes = 0L
                var lastNotifTime = 0L

                FileOutputStream(destFile).use { fos ->
                    val buffer = ByteArray(8 * 1024)    // 8 KB buffer
                    body.byteStream().use { input ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                            downloadedBytes += read

                            // 每 500ms 更新一次通知，避免频繁刷新
                            val now = System.currentTimeMillis()
                            if (now - lastNotifTime > 500) {
                                lastNotifTime = now
                                val progress = if (totalBytes > 0)
                                    ((downloadedBytes * 100) / totalBytes).toInt() else -1
                                withContext(Dispatchers.Main) {
                                    updateProgress(downloadedBytes, totalBytes, progress)
                                }
                            }
                        }
                        fos.flush()
                    }
                }

                Log.d(TAG, "Download complete: ${destFile.absolutePath} (${destFile.length()} bytes)")
            }
        }

        // 验证文件完整性（基础检查）
        if (!destFile.exists() || destFile.length() < 1024) {
            throw Exception("Downloaded file is invalid or too small (${destFile.length()} bytes)")
        }

        // 更新通知：安装中
        withContext(Dispatchers.Main) {
            notificationManager.notify(
                NOTIF_ID,
                buildNotification("安装中...", 100, 100)
            )
        }

        // 触发安装
        withContext(Dispatchers.Main) {
            PackageInstallerHelper.installApk(this@DownloadService, destFile)
        }
    }

    // ----------------------------------------------------------------
    // 通知管理
    // ----------------------------------------------------------------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AutoApp 下载",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "应用下载进度通知"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, progress: Int, max: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoApp Store")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (max > 0) {
                    setProgress(max, progress, false)
                } else {
                    setProgress(0, 0, true)  // indeterminate
                }
            }
            .build()

    private fun updateProgress(downloaded: Long, total: Long, percent: Int) {
        val text = if (total > 0) {
            "${formatSize(downloaded)} / ${formatSize(total)}  ($percent%)"
        } else {
            "${formatSize(downloaded)} 已下载"
        }
        notificationManager.notify(NOTIF_ID, buildNotification(text, percent.coerceAtLeast(0), 100))
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

    override fun onDestroy() {
        super.onDestroy()
        // serviceScope 的 Job 会随着 Service 销毁自动取消协程
    }
}
