package com.nudge.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject

class NudgeNotificationService : NotificationListenerService() {

    companion object {
        var notifMap: java.util.concurrent.ConcurrentHashMap<String, String> = java.util.concurrent.ConcurrentHashMap()
        var isRunning: Boolean = false
        var instance: NudgeNotificationService? = null

        // 从系统实时读取当前活跃通知（过滤黑名单），不依赖内存缓存
        fun getActiveNotificationsJson(count: Int): List<JSONObject> {
            val inst = instance ?: return emptyList()
            return try {
                inst.activeNotifications
                    .filter { !isBlocked(it.packageName) }
                    .map { sbn ->
                        val extras = sbn.notification.extras
                        JSONObject().apply {
                            put("app", try {
                                inst.packageManager.getApplicationLabel(inst.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
                            } catch (_: Exception) { sbn.packageName })
                            put("package", sbn.packageName)
                            put("title", extras.getString("android.title") ?: "")
                            put("text", extras.getString("android.text") ?: "")
                            put("time", sbn.postTime)
                        }
                    }
                    .sortedByDescending { it.optLong("time", 0L) }
                    .take(count)
            } catch (e: Exception) {
                emptyList()
            }
        }

        // 小米系统服务/常驻垃圾通知，不存进 map（右页那些）
        private val BLOCKED_PACKAGES = setOf(
            "com.android.systemui",       // 系统界面
            "com.miui.securitycenter",    // 安全服务
            "com.milink.service",         // 设备互联
            "com.xiaomi.aicr",            // 小米澎湃AI引擎
            "com.miui.misound",           // 音质音效
            "com.miui.contentextension",  // 传送门
            "com.miui.translationservice", // 传送门-翻译
            "com.xiaomi.finddevice",       // 查找设备
            "com.xiaomi.smarthome",        // 米家
            "com.miui.tsmclient",          // 小米智能卡/钱包
            "com.xiaomi.mi_connect_service", // 小米互联通信服务
            "com.miui.voicetrigger",       // 语音唤醒
            "com.xiaomi.mirror"            // 跨屏协同服务
        )

        fun isBlocked(pkg: String): Boolean = pkg in BLOCKED_PACKAGES
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName
        if (isBlocked(pkg)) return
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
