package com.nudge.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
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
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 注意: 旧 channel "nudge_alarm" 已在旧版本用默认声音创建，Android 8+ channel 声音不可改，
        // 所以必须换全新 channel ID，否则永远响的是通知声音
        val channelId = "nudge_alarm_v2"
        val channel = NotificationChannel(channelId, "Nudge 闹钟", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
            // 用系统闹钟默认铃声
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), null)
        }
        nm.createNotificationChannel(channel)

        val title = if (type == "countdown") "⏱️ 倒计时结束" else "⏰ Nudge 闹钟"

        // 全屏意图: 锁屏/亮屏时直接全屏显示，确保能看到
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
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
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
