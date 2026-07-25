package com.nudge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class NudgeAccessibilityService : AccessibilityService() {

    companion object {
        var currentPackage: String = ""
        var currentAppName: String = ""
        var isRunning: Boolean = false
        var instance: NudgeAccessibilityService? = null
        var steps: Long = 0
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
            currentPackage = pkg
            currentAppName = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (_: Exception) {
                pkg
            }
        }
    }

    override fun onInterrupt() {}

    fun takeScreenshotAndAnalyze(callback: (String) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            callback("{\"error\":\"需要Android 14+\"}")
            return
        }
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
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
                                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
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
    }

    fun doTap(x: Float, y: Float, callback: (Boolean) -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            val path = android.graphics.Path()
            path.moveTo(x, y)
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 100)
            val builder = android.accessibilityservice.GestureDescription.Builder()
            builder.addStroke(stroke)
            val cb = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gd: android.accessibilityservice.GestureDescription?) { callback(true) }
                override fun onCancelled(gd: android.accessibilityservice.GestureDescription?) { callback(false) }
            }
            dispatchGesture(builder.build(), cb, null)
        }
    }

    fun doSwipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long, callback: (Boolean) -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            val path = android.graphics.Path()
            path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, dur)
            val builder = android.accessibilityservice.GestureDescription.Builder()
            builder.addStroke(stroke)
            val cb = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gd: android.accessibilityservice.GestureDescription?) { callback(true) }
                override fun onCancelled(gd: android.accessibilityservice.GestureDescription?) { callback(false) }
            }
            dispatchGesture(builder.build(), cb, null)
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

}
