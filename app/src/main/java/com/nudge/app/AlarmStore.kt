package com.nudge.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

object AlarmStore {

    data class AlarmItem(
        val id: Long,
        val type: String,       // "alarm" 定时 | "countdown" 倒计时
        val hour: Int,          // 定时: 小时
        val minute: Int,        // 定时: 分钟
        val message: String,    // 备注
        val enabled: Boolean,   // 开关
        val triggerAt: Long,    // 倒计时: 触发时间戳; 定时: 下次触发时间戳(用于显示)
        val createdAt: Long
    )

    private const val PREFS = "nudge_alarms"
    private const val KEY = "alarms"

    fun load(context: Context): MutableList<AlarmItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        val list = mutableListOf<AlarmItem>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(AlarmItem(
                    id = o.getLong("id"),
                    type = o.optString("type", "alarm"),
                    hour = o.optInt("hour", 0),
                    minute = o.optInt("minute", 0),
                    message = o.optString("message", ""),
                    enabled = o.optBoolean("enabled", true),
                    triggerAt = o.optLong("triggerAt", 0L),
                    createdAt = o.optLong("createdAt", 0L)
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun save(context: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                put("hour", item.hour)
                put("minute", item.minute)
                put("message", item.message)
                put("enabled", item.enabled)
                put("triggerAt", item.triggerAt)
                put("createdAt", item.createdAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** 定时闹钟的下一次触发时间戳 */
    fun nextAlarmTime(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /** 添加并调度，返回新 item */
    fun add(context: Context, type: String, hour: Int, minute: Int, message: String, countdownSeconds: Long): AlarmItem {
        val now = System.currentTimeMillis()
        val id = System.currentTimeMillis() % 1000000
        val triggerAt = if (type == "countdown") now + countdownSeconds * 1000 else nextAlarmTime(hour, minute)
        val item = AlarmItem(id, type, hour, minute, message, true, triggerAt, now)
        val list = load(context)
        list.add(item)
        save(context, list)
        schedule(context, item)
        return item
    }

    /** 开关切换 */
    fun toggle(context: Context, id: Long, enabled: Boolean) {
        val list = load(context)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val item = list[idx].copy(enabled = enabled)
        list[idx] = item
        save(context, list)
        if (enabled) {
            schedule(context, item)
        } else {
            cancel(context, item)
        }
    }

    /** 删除 */
    fun remove(context: Context, id: Long) {
        val list = load(context)
        val item = list.firstOrNull { it.id == id } ?: return
        cancel(context, item)
        list.removeAll { it.id == id }
        save(context, list)
    }

    /** 一次性闹钟响后自动关闭 */
    fun markFired(context: Context, id: Long) {
        val list = load(context)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        list[idx] = list[idx].copy(enabled = false)
        save(context, list)
    }

    private fun pendingIntent(context: Context, item: AlarmItem): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", item.id)
            putExtra("type", item.type)
            putExtra("message", item.message)
        }
        return PendingIntent.getBroadcast(
            context,
            item.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun schedule(context: Context, item: AlarmItem) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, item)
        try {
            if (item.type == "countdown") {
                // 倒计时: 精确闹钟(允许打盹模式)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
                }
            } else {
                // 定时: setAlarmClock 显示在状态栏
                // 注意: showIntent 必须用不同的 requestCode，否则会覆盖真正带 extras 的 pi
                val showIntent = Intent(context, AlarmReceiver::class.java).apply {
                    action = "com.nudge.app.SHOW_ALARM"
                }
                val showPi = PendingIntent.getBroadcast(
                    context,
                    item.id.toInt() + 1000000,
                    showIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val info = AlarmManager.AlarmClockInfo(item.triggerAt, showPi)
                am.setAlarmClock(info, pi)
            }
        } catch (e: Exception) {
            // SCHEDULE_EXACT_ALARM 未授权时退化为 set
            if (item.type == "countdown") {
                am.set(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
            } else {
                am.set(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
            }
        }
    }

    private fun cancel(context: Context, item: AlarmItem) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, item))
    }
}
