package com.nudge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class NudgeAccessibilityService : AccessibilityService() {

    companion object {
        var currentPackage: String = ""
        var currentAppName: String = ""
        var isRunning: Boolean = false
        var instance: NudgeAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
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
}
