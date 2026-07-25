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
        // 确保在主线程调用takeScreenshot
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
        try {
            takeScreenshot(
                0, // displayId
                java.util.concurrent.Executors.newSingleThreadExecutor(),
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        try {
                            val bitmap = android.graphics.BitmapFactory.decodeStream(
                                java.io.ByteArrayInputStream(result.getHardwareBuffer()?.let {
                                    // fallback: use pixel copy
                                    null
                                })
                            )
                            // convert hardware buffer to bitmap
                            val hb = result.hardwareBuffer
                            if (hb != null) {
                                val colorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
                                val bitmap2 = android.graphics.Bitmap.wrapHardwareBuffer(hb, colorSpace)
                                if (bitmap2 != null) {
                                    val stream = java.io.ByteArrayOutputStream()
                                    bitmap2.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, stream)
                                    val base64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
                                    result.hardwareBuffer.close()
                                    callback(base64)
                                    return
                                }
                                hb.close()
                            }
                            callback("{\"error\":\"截屏转换失败\"}")
                        } catch (e: Exception) {
                            callback("{\"error\":\"${e.message}\"}")
                        } finally {
                            result.hardwareBuffer?.close()
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
        } // mainHandler.post
    }
}
