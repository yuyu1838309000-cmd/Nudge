package com.nudge.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "闹钟时间到"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "nudge_alarm"
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Nudge 闹钟", NotificationManager.IMPORTANCE_HIGH)
        )
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle("⏰ Nudge 闹钟")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        nm.notify((System.currentTimeMillis() % 100000).toInt(), notif)
    }
}
