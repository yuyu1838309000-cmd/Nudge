package com.nudge.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1L)
        val type = intent.getStringExtra("type") ?: "alarm"
        val message = intent.getStringExtra("message") ?: "时间到啦"
        if (id > 0) {
            AlarmStore.markFired(context, id)
        }

        // Android 13+ 通知权限检查
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 清理旧渠道(声音锁死 + 重复), 统一用新的单渠道
        nm.deleteNotificationChannel("nudge_alarm")
        nm.deleteNotificationChannel("nudge_alarm_v2")

        val channelId = "nudge_alarm"
        // 内置铃声: res/raw/alarm.wav
        val soundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.alarm}")
        val channel = NotificationChannel(channelId, "Nudge 闹钟", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            setSound(soundUri, null)
        }
        nm.createNotificationChannel(channel)

        val title = if (type == "countdown") "⏱️ 倒计时结束" else "⏰ Nudge 闹钟"

        val fullIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_alarm", true)
        }
        val fullPi = PendingIntent.getActivity(
            context,
            (id % 50000).toInt(),
            fullIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(fullPi)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 600, 400, 600, 400, 600))

        if (Build.VERSION.SDK_INT >= 29) {
            builder.setFullScreenIntent(fullPi, true)
        }

        try {
            nm.notify((id % 100000).toInt(), builder.build())
        } catch (_: Exception) {
            nm.notify(1, builder.build())
        }
    }
}
