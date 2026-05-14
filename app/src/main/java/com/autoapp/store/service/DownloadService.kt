package com.autoapp.store.service

import android.app.DownloadManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.autoapp.store.installer.PackageInstallerHelper
import java.io.File

class DownloadService : Service() {

    companion object {
        private const val EXTRA_URL = "download_url"
        private const val EXTRA_APP_ID = "app_id"
        private const val EXTRA_VERSION = "version"

        fun start(context: Context, url: String, appId: String, version: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_APP_ID, appId)
                putExtra(EXTRA_VERSION, version)
            }
            context.startService(intent)
        }
    }

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: return
            if (downloadId == currentDownloadId) {
                handleDownloadComplete(downloadId)
            }
        }
    }

    private var currentDownloadId: Long = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadCompleteReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: ""
        val version = intent.getStringExtra(EXTRA_VERSION) ?: ""

        startForeground(
            1,
            NotificationCompat.Builder(this, getChannelId())
                .setContentTitle("Downloading App")
                .setContentText("Starting download...")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
        )

        enqueueDownload(url, appId, version)
        return START_NOT_STICKY
    }

    private fun enqueueDownload(url: String, appId: String, version: String) {
        val fileName = "${appId}_$version.apk"
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Downloading App")
            setDescription("AutoApp Store")
            setDestinationInExternalFilesDir(this@DownloadService, Environment.DIRECTORY_DOWNLOADS, fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        }

        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        currentDownloadId = dm.enqueue(request)
    }

    private fun handleDownloadComplete(downloadId: Long) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = dm.query(query)
        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val localUri = cursor.getString(uriIndex)
                localUri?.let {
                    val file = File(Uri.parse(it).path ?: return@let)
                    PackageInstallerHelper.installApk(this, file)
                }
            }
        }
        cursor.close()
        stopSelf()
    }

    private fun getChannelId(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "autoapp_downloads"
            val channel = android.app.NotificationChannel(
                channelId, "Downloads", android.app.NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)
            channelId
        } else {
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(downloadCompleteReceiver)
    }
}
