package com.autoapp.store.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream

object PackageInstallerHelper {

    fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )

            try {
                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                FileInputStream(apkFile).use { input ->
                    session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val intent = Intent(context, InstallReceiver::class.java)
                intent.action = "com.autoapp.store.ACTION_INSTALL_COMPLETE"
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                session.commit(pendingIntent.intentSender)
                session.close()

                Toast.makeText(context, "Installing... Please confirm on system dialog", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Install failed: ${e.message}", Toast.LENGTH_LONG).show()
                installViaIntent(context, apkFile)
            }
        } else {
            installViaIntent(context, apkFile)
        }
    }

    private fun installViaIntent(context: Context, apkFile: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
