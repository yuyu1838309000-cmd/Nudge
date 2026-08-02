package com.nudge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat

/**
 * 闹钟铃声播放服务。
 * 用 MediaPlayer 直接播内置铃声(USAGE_ALARM 走闹钟音量) + 震动，
 * 不依赖通知渠道声音，避免被渠道静音/替换。
 */
class AlarmSoundService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var ringId: Long = -1L

    companion object {
        private const val ACTION_STOP = "com.nudge.app.ALARM_STOP"
        private const val RING_DURATION_MS = 60_000L // 最长响60秒

        fun start(context: Context, id: Long, title: String, note: String) {
            val intent = Intent(context, AlarmSoundService::class.java).apply {
                putExtra("id", id)
                putExtra("title", title)
                putExtra("note", note)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmSoundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRinging()
            return START_NOT_STICKY
        }
        ringId = intent?.getLongExtra("id", -1L) ?: -1L
        val title = intent?.getStringExtra("title") ?: "闹钟"
        val note = intent?.getStringExtra("note") ?: ""

        startForeground(notifyId(), buildNotification(title, note))
        startRinging()

        // 最长响60秒自动停
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopRinging() }, RING_DURATION_MS)
        return START_NOT_STICKY
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun notifyId(): Int {
        val base = (ringId % 90000).toInt()
        return if (base < 0) 1000 else base + 1000
    }

    private fun buildNotification(title: String, note: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel("nudge_alarm")
        nm.deleteNotificationChannel("nudge_alarm_v2")
        val channelId = "nudge_alarm_v3"
        // 声音由服务播放, 渠道本身静音避免双响
        val channel = NotificationChannel(channelId, "Nudge 闹钟", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)

        val contentIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this, notifyId(), contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 停止按钮
        val stopIntent = Intent(this, AlarmSoundService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(
            this, notifyId() + 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (note.isNotBlank()) note else "时间到啦"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("⏰ $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(contentPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPi)
            .build()
    }

    private fun startRinging() {
        try {
            val p = MediaPlayer.create(this, R.raw.alarm)
            p.isLooping = true
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            p.start()
            player = p
        } catch (e: Exception) {
            // 播放失败: 退回系统闹钟提示音
            try {
                val p2 = MediaPlayer()
                p2.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                p2.setDataSource(this, Uri.parse("android.resource://${packageName}/${R.raw.alarm}"))
                p2.isLooping = true
                p2.prepare()
                p2.start()
                player = p2
            } catch (_: Exception) {}
        }

        try {
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 600, 400, 600, 400, 600), 0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 600, 400, 600, 400, 600), 0)
                }
            }
        } catch (_: Exception) {}
    }

    private fun stopRinging() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
