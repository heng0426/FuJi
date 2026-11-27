package com.xiaofu.daily.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.xiaofu.daily.MainActivity
import com.xiaofu.daily.R

/** 版本更新通知：独立通道，可在系统设置里单独关闭 */
object UpdateNotifier {

    const val EXTRA_OPEN_UPDATE = "open_update"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                UpdateConfig.CHANNEL_ID,
                "版本更新",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "发现新版本时提醒，可在应用内关闭"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    fun notifyAvailable(context: Context, manifest: UpdateManifest) {
        val summary = manifest.changelog.firstOrNull() ?: "发现新版本，可在「我的 - 关于」中更新"
        post(
            context = context,
            id = UpdateConfig.NOTIFICATION_ID,
            title = "小福日常 ${manifest.versionName} 已发布",
            text = summary,
        )
    }

    fun notifyInstallFailed(context: Context, message: String) {
        post(
            context = context,
            id = UpdateConfig.FAILED_NOTIFICATION_ID,
            title = "更新未完成",
            text = message,
        )
    }

    fun cancel(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(UpdateConfig.NOTIFICATION_ID) }
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_UPDATE, true)
        }
        val pending = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, UpdateConfig.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ms_pets)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(id, notification) }
    }
}
