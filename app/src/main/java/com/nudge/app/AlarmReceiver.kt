package com.nudge.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        val type = intent.getStringExtra("type") ?: "alarm"
        val message = intent.getStringExtra("message") ?: "时间到啦"
        if (id > 0) {
            // 一次性闹钟自动关闭开关
            AlarmStore.markFired(context, id)
        }

        // Android 13+ 通知权限检查
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nudge_alarm"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Nudge 闹钟", NotificationManager.IMPORTANCE_HIGH)
        )
        val title = if (type == "countdown") "⏱️ 倒计时结束" else "⏰ Nudge 闹钟"
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify((id % 100000).toInt(), notif)
        } catch (_: Exception) {
            nm.notify(1, notif)
        }
    }
}
