package com.nudge.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

class NudgeNotificationService : NotificationListenerService() {

    companion object {
        var notifMap: java.util.concurrent.ConcurrentHashMap<String, String> = java.util.concurrent.ConcurrentHashMap()
        var isRunning: Boolean = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        val appName = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) { pkg }
        val entry = JSONObject().apply {
            put("app", appName)
            put("package", pkg)
            put("title", title)
            put("text", text)
            put("time", System.currentTimeMillis())
        }.toString()
        notifMap[sbn.key] = entry
        if (notifMap.size > 100) {
            val oldest = notifMap.entries.minByOrNull { entry ->
                runCatching { JSONObject(entry.value).optLong("time", Long.MAX_VALUE) }.getOrDefault(Long.MAX_VALUE)
            }
            if (oldest != null) notifMap.remove(oldest.key)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        notifMap.remove(sbn.key)
    }
}
