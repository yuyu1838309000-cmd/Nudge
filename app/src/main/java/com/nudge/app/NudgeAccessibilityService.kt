package com.nudge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class NudgeAccessibilityService : AccessibilityService() {

    companion object {
        val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.miui.home",
            "com.sohu.inputmethod.sogou",
            "com.baidu.input",
            "com.iflytek.inputmethod",
            "com.google.android.inputmethod.latin",
            "com.miui.notes",
            "com.android.settings",
        )
        var currentPackage: String = ""
        var currentAppName: String = ""
        var currentActivity: String = ""
        var currentSince: Long = 0L
        var isRunning: Boolean = false
        var instance: NudgeAccessibilityService? = null
        @Volatile var steps: Long = 0
        private val labelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        fun appLabel(pkg: String): String {
            labelCache[pkg]?.let { return it }
            val label = try {
                val inst = instance ?: return pkg.substringAfterLast('.')
                val ai = inst.packageManager.getApplicationInfo(pkg, 0)
                val l = ai.loadLabel(inst.packageManager).toString()
                if (l.isNotBlank()) l else null
            } catch (_: Exception) { null }
            val result = label ?: pkg.substringAfterLast('.')
            labelCache[pkg] = result
            return result
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this
        startStepCounter()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        stepSensorManager?.unregisterListener(stepListener)
    }

    private var stepSensorManager: android.hardware.SensorManager? = null
    private var stepListener: android.hardware.SensorEventListener? = null

    private fun startStepCounter() {
        stepSensorManager = getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
        val sensor = stepSensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER) ?: return
        stepListener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (event != null && event.values.isNotEmpty()) {
                    steps = event.values[0].toLong()
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        stepSensorManager?.registerListener(stepListener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg in Companion.IGNORED_PACKAGES) return
            currentPackage = pkg
            currentAppName = appLabel(pkg)
            currentActivity = event.className?.toString() ?: ""
            currentSince = System.currentTimeMillis()
        }
    }

    override fun onInterrupt() {}

    fun takeScreenshotAndAnalyze(callback: (String) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            callback("{\"error\":\"需要Android 14+\"}")
            return
        }
        try {
            takeScreenshot(
                0,
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        try {
                            val hb = result.hardwareBuffer
                            if (hb == null) {
                                callback("{\"error\":\"hardwareBuffer为null\"}")
                                return
                            }
                            val cs = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                            val bmp = android.graphics.Bitmap.wrapHardwareBuffer(hb, cs)
                            if (bmp == null) {
                                hb.close()
                                callback("{\"error\":\"wrapHardwareBuffer返回null\"}")
                                return
                            }
                            val stream = java.io.ByteArrayOutputStream()
                            val maxWidth = 1080
                            val outBmp = if (bmp.width > maxWidth) {
                                val nh = bmp.height * maxWidth / bmp.width
                                android.graphics.Bitmap.createScaledBitmap(bmp, maxWidth, nh, true)
                            } else {
                                bmp
                            }
                            outBmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 55, stream)
                            if (outBmp !== bmp) outBmp.recycle()
                            val bytes = stream.toByteArray()
                            if (bytes.isEmpty()) {
                                callback("{\"error\":\"压缩后数据为空\"}")
                                return
                            }
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                            callback(base64)
                            hb.close()
                        } catch (e: Exception) {
                            callback("{\"error\":\"${e.message}\"}")
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        callback("{\"error\":\"截屏失败，错误码: $errorCode\"}")
                    }
                }
            )
        } catch (e: Exception) {
            callback("{\"error\":\"${e.message}\"}")
        }
    }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return """{"error":"无法获取屏幕内容，请确认无障碍服务已开启"}"""
        val texts = mutableListOf<String>()
        try {
            collectTexts(root, texts)
        } finally {
            root.recycle()
        }
        val arr = org.json.JSONArray()
        for (t in texts) arr.put(t)
        return org.json.JSONObject().apply {
            put("elements", arr)
            put("count", arr.length())
        }.toString()
    }

    private fun collectTexts(node: android.view.accessibility.AccessibilityNodeInfo, collector: MutableList<String>) {
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        if (!text.isNullOrEmpty()) collector.add(text)
        if (!desc.isNullOrEmpty() && desc != text) collector.add(desc)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTexts(child, collector)
            child.recycle()
        }
    }


    fun findAndClickApp(appName: String): Boolean {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Thread.sleep(700)
        // Try up to 3 pages
        for (page in 0 until 3) {
            val root = rootInActiveWindow ?: continue
            try {
                val nodes = root.findAccessibilityNodeInfosByText(appName)
                for (node in nodes) {
                    try {
                        if (node.isClickable) {
                            node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                            return true
                        }
                        var p = node.parent
                        var depth = 0
                        while (p != null && depth < 5) {
                            if (p.isClickable) {
                                p.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                                p.recycle()
                                return true
                            }
                            val next = p.parent
                            p.recycle()
                            p = next
                            depth++
                        }
                    } finally {
                        try { node.recycle() } catch (_: Exception) {}
                    }
                }
            } finally {
                try { root.recycle() } catch (_: Exception) {}
            }
            // Swipe left to next page
            val path = android.graphics.Path()
            path.moveTo(900f, 1000f)
            path.lineTo(200f, 1000f)
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 300)
            val builder = android.accessibilityservice.GestureDescription.Builder()
            builder.addStroke(stroke)
            val latch = java.util.concurrent.CountDownLatch(1)
            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gd: android.accessibilityservice.GestureDescription?) { latch.countDown() }
                override fun onCancelled(gd: android.accessibilityservice.GestureDescription?) { latch.countDown() }
            }, null)
            latch.await(300, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(400)
        }
        return false
    }


    fun openApp(pkg: String): String {
        // 1. Try direct launch via accessibility context
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return "{\"success\":true,\"package\":\"$pkg\",\"method\":\"direct\"}"
            }
        } catch (_: Exception) {}

        // 2. Get app name - use getInstalledApplications for better coverage
        var appName = ""
        try {
            val apps = packageManager.getInstalledApplications(0)
            for (app in apps) {
                if (app.packageName == pkg) {
                    appName = packageManager.getApplicationLabel(app).toString()
                    break
                }
            }
        } catch (_: Exception) {}
        if (appName.isEmpty()) return "{\"error\":\"未找到应用\"}"

        // 3. Desktop click
        if (findAndClickApp(appName)) {
            return "{\"success\":true,\"package\":\"$pkg\",\"app_name\":\"$appName\",\"method\":\"desktop\"}"
        }
        return "{\"error\":\"桌面未找到: $appName\"}"
    }



    fun switchToRikkaHub(index: Int = 1): Boolean {
        // 1. Try direct intent with REORDER_TO_FRONT
        var launched = false
        try {
            val intent = packageManager.getLaunchIntentForPackage("me.rerere.rikkahub")
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                launched = true
            }
        } catch (_: Exception) {}
        // 2. If launched, wait and auto-click chooser option (双开分身选择框)
        if (launched) {
            Thread.sleep(1000)
            try {
                val root = rootInActiveWindow ?: return true
                try {
                    val rootPkg = root.packageName?.toString() ?: ""
                    if (rootPkg != "me.rerere.rikkahub") {
                        // 还在选择框/其他界面，找 RikkaHub 选项点击
                        val candidates = mutableListOf<android.util.Pair<android.view.accessibility.AccessibilityNodeInfo, Int>>()
                        try {
                            val nodes = root.findAccessibilityNodeInfosByText("RikkaHub")
                            val seen = HashSet<String>()
                            for (node in nodes) {
                                try {
                                    var n: android.view.accessibility.AccessibilityNodeInfo? = node
                                    var depth = 0
                                    while (n != null && depth < 6) {
                                        if (n.isClickable) {
                                            val bounds = android.graphics.Rect()
                                            n.getBoundsInScreen(bounds)
                                            val key = "${n.className}|${bounds.top}|${bounds.left}"
                                            if (seen.add(key)) {
                                                candidates.add(android.util.Pair(n, bounds.top))
                                            }
                                            break
                                        }
                                        val parent = n.parent
                                        n = if (parent != null && parent !== n) parent else null
                                        depth++
                                    }
                                } catch (_: Exception) {}
                            }
                            candidates.sortBy { it.second }
                            val clickables = candidates.map { it.first }
                            if (clickables.isNotEmpty()) {
                                val target = clickables.getOrNull(index) ?: clickables[0]
                                target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                                return true
                            }
                        } catch (_: Exception) {}
                    }
                } finally {
                    try { root.recycle() } catch (_: Exception) {}
                }
                return true
            } catch (_: Exception) {
                return true
            }
        }
        // 3. Fallback to recents
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        Thread.sleep(800)
        val root = rootInActiveWindow ?: return false
        try {
            val nodes = root.findAccessibilityNodeInfosByText("RikkaHub")
            for (node in nodes) {
                try {
                    if (node.isClickable) {
                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    var p = node.parent
                    var depth = 0
                    while (p != null && depth < 5) {
                        if (p.isClickable) {
                            p.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                            p.recycle()
                            return true
                        }
                        val next = p.parent
                        p.recycle()
                        p = next
                        depth++
                    }
                } finally {
                    try { node.recycle() } catch (_: Exception) {}
                }
            }
            return false
        } finally {
            try { root.recycle() } catch (_: Exception) {}
        }
    }

}
