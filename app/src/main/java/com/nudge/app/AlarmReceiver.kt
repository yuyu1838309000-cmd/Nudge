package com.nudge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP = "com.nudge.app.ALARM_STOP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 停止铃声广播
        if (intent.action == ACTION_STOP) {
            context.stopService(Intent(context, AlarmSoundService::class.java))
            return
        }

        val id = intent.getLongExtra("id", -1L)
        val type = intent.getStringExtra("type") ?: "alarm"
        val title = intent.getStringExtra("title") ?: "闹钟"
        val note = intent.getStringExtra("note") ?: ""
        if (id > 0) {
            // 处理重复/关闭逻辑
            AlarmStore.onFired(context, id)
        }
        // 前台服务直接播放铃声+震动(不依赖通知渠道声音)
        AlarmSoundService.start(context, id, title, if (type == "countdown") "倒计时结束 $note" else note)
    }
}
