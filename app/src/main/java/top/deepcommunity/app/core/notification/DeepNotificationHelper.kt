package top.deepcommunity.app.core.notification

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
import top.deepcommunity.app.MainActivity
import top.deepcommunity.app.R

/**
 * 社区原生通知管理工具
 * 适配 Android 8.0+ 渠道管理与 Android 13+ 运行时通知权限
 */
object DeepNotificationHelper {

    const val CHANNEL_ID = "deep_community_notifications"
    const val CHANNEL_NAME = "社区动态与聊天提醒"
    const val EXTRA_TARGET_ID = "extra_target_id"

    /**
     * 在 App 启动时注册原生通知渠道
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "接收点赞、回复、@我以及私聊群聊实时消息"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * 弹出原生系统通知
     */
    fun showNotification(
        context: Context,
        title: String,
        content: String,
        targetId: String? = null,
        notificationId: Int = (System.currentTimeMillis() % 100000).toInt()
    ) {
        // Android 13+ 检查 POST_NOTIFICATIONS 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!targetId.isNullOrBlank()) {
                putExtra(EXTRA_TARGET_ID, targetId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // 权限未授予或被拒绝时静默处理
        }
    }
}
