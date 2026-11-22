package team.wd.atnestapp.ui.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 演示模式验证码通知：验证码由后端随响应返回，这里只负责在系统通知栏展示。
 * 生产环境不应把这种方式当作手机号归属验证。
 */
object LoginCodeNotification {
    private const val CHANNEL_ID = "login_code"
    private const val NOTIFICATION_ID = 7301

    fun show(context: Context, phone: String, code: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "登录验证码",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "宠栖登录验证码"
            }
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launchIntent != null) {
            val pending = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentIntent(pending)
        }

        val masked = if (phone.length >= 7) {
            phone.substring(0, 3) + "****" + phone.takeLast(4)
        } else {
            phone
        }
        val title = "宠栖登录验证码"
        val text = "$code（5 分钟内有效）"

        builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("$masked：$text")
            .setStyle(Notification.BigTextStyle().bigText("$masked 的登录验证码：$text"))
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setTimeoutAfter(5 * 60 * 1000L)

        manager.notify(NOTIFICATION_ID, builder.build())
        return true
    }
}
