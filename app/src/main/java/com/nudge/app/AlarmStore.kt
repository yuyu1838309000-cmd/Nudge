package com.nudge.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object AlarmStore {

    data class AlarmItem(
        val id: Long,
        val type: String,          // "alarm" 定时 | "countdown" 倒计时
        val hour: Int,             // 定时: 小时
        val minute: Int,           // 定时: 分钟
        val title: String,         // 标题
        val note: String,          // 备注
        val enabled: Boolean,      // 开关
        val triggerAt: Long,       // 下次触发时间戳
        val repeat: String,        // "once" 响一次 | "daily" 每天 | "weekly" 按周几
        val weekdays: List<Int>,   // weekly: 1=周一 ... 7=周日
        val durationSec: Long,     // countdown: 倒计时时长(秒)，用于重开
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
                val wdArr = o.optJSONArray("weekdays")
                val weekdays = mutableListOf<Int>()
                if (wdArr != null) {
                    for (j in 0 until wdArr.length()) weekdays.add(wdArr.getInt(j))
                }
                list.add(AlarmItem(
                    id = o.getLong("id"),
                    type = o.optString("type", "alarm"),
                    hour = o.optInt("hour", 0),
                    minute = o.optInt("minute", 0),
                    title = o.optString("title", o.optString("message", "闹钟")),
                    note = o.optString("note", ""),
                    enabled = o.optBoolean("enabled", true),
                    triggerAt = o.optLong("triggerAt", 0L),
                    repeat = o.optString("repeat", "once"),
                    weekdays = weekdays,
                    durationSec = o.optLong("durationSec", 0L),
                    createdAt = o.optLong("createdAt", 0L)
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    private fun save(context: Context, list: List<AlarmItem>) {
        val arr = JSONArray()
        for (item in list) {
            val wdArr = JSONArray()
            for (d in item.weekdays) wdArr.put(d)
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("type", item.type)
                put("hour", item.hour)
                put("minute", item.minute)
                put("title", item.title)
                put("note", item.note)
                put("enabled", item.enabled)
                put("triggerAt", item.triggerAt)
                put("repeat", item.repeat)
                put("weekdays", wdArr)
                put("durationSec", item.durationSec)
                put("createdAt", item.createdAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** 定时闹钟下一次触发时间戳(once/daily 用) */
    fun nextAlarmTime(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }

    /** ISO 周几: 1=周一 ... 7=周日 */
    private fun isoWeekday(cal: Calendar): Int {
        val dow = cal.get(Calendar.DAY_OF_WEEK) // SUNDAY=1 ... SATURDAY=7
        return if (dow == Calendar.SUNDAY) 7 else dow - 1
    }

    /** weekly: 下一个选中周几的时间戳 */
    fun nextWeeklyTime(weekdays: List<Int>, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        for (offset in 0..7) {
            val tmp = cal.clone() as Calendar
            tmp.add(Calendar.DAY_OF_MONTH, offset)
            if (weekdays.contains(isoWeekday(tmp))) {
                if (offset > 0 || tmp.timeInMillis > System.currentTimeMillis()) {
                    return tmp.timeInMillis
                }
            }
        }
        // 兜底: 明天同一时间
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    /** 添加并调度 */
    fun add(context: Context, type: String, hour: Int, minute: Int, title: String, note: String,
            repeat: String, weekdays: List<Int>, countdownSeconds: Long): AlarmItem {
        val now = System.currentTimeMillis()
        val id = (System.currentTimeMillis() % 1000000) + (Math.random() * 1000).toLong()
        val triggerAt = when {
            type == "countdown" -> now + countdownSeconds * 1000
            repeat == "weekly" -> nextWeeklyTime(weekdays, hour, minute)
            else -> nextAlarmTime(hour, minute)
        }
        val item = AlarmItem(id, type, hour, minute, title, note, true, triggerAt,
            repeat, weekdays, countdownSeconds, now)
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
        var item = list[idx]
        if (enabled) {
            // 重新打开: 倒计时按原时长重新计时; 定时按规则重算下次触发
            item = when {
                item.type == "countdown" -> {
                    val dur = if (item.durationSec > 0) item.durationSec else 60L
                    item.copy(enabled = true, triggerAt = System.currentTimeMillis() + dur * 1000)
                }
                item.repeat == "weekly" ->
                    item.copy(enabled = true, triggerAt = nextWeeklyTime(item.weekdays, item.hour, item.minute))
                else ->
                    item.copy(enabled = true, triggerAt = nextAlarmTime(item.hour, item.minute))
            }
        } else {
            item = item.copy(enabled = false)
        }
        list[idx] = item
        save(context, list)
        if (item.enabled) schedule(context, item) else cancel(context, item)
    }

    /** 删除 */
    fun remove(context: Context, id: Long) {
        val list = load(context)
        val item = list.firstOrNull { it.id == id } ?: return
        cancel(context, item)
        list.removeAll { it.id == id }
        save(context, list)
    }

    /** 闹钟触发后处理: once/countdown 自动关闭; daily/weekly 保持开启并重排 */
    fun onFired(context: Context, id: Long) {
        val list = load(context)
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val item = list[idx]
        if (item.repeat == "once" || item.type == "countdown") {
            list[idx] = item.copy(enabled = false)
            save(context, list)
        } else {
            // daily / weekly: 重排下一次
            val next = if (item.repeat == "weekly")
                nextWeeklyTime(item.weekdays, item.hour, item.minute)
            else nextAlarmTime(item.hour, item.minute)
            list[idx] = item.copy(triggerAt = next)
            save(context, list)
            schedule(context, list[idx])
        }
    }

    private fun pendingIntent(context: Context, item: AlarmItem): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("id", item.id)
            putExtra("type", item.type)
            putExtra("title", item.title)
            putExtra("note", item.note)
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
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
                }
            } else {
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
            am.set(AlarmManager.RTC_WAKEUP, item.triggerAt, pi)
        }
    }

    private fun cancel(context: Context, item: AlarmItem) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, item))
    }
}
