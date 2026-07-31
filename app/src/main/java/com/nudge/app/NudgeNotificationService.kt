package com.nudge.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NudgeNotificationService : NotificationListenerService() {

    companion object {
        var notifMap: MutableMap<String, String> = mutableMapOf()
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
        val entry = "{\"app\":\"$appName\",\"package\":\"$pkg\",\"title\":\"${title.replace("\"","\\\"")}\",\"text\":\"${text.replace("\"","\\\"")}\",\"time\":${System.currentTimeMillis()}}"
        notifMap[sbn.key] = entry
        if (notifMap.size > 100) {
            val oldest = notifMap.keys.firstOrNull()
            if (oldest != null) notifMap.remove(oldest)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        notifMap.remove(sbn.key)
    }
}
