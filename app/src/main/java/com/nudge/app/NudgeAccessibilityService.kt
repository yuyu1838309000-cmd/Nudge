package com.nudge.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class NudgeAccessibilityService : AccessibilityService() {

    companion object {
        var currentPackage: String = ""
        var currentAppName: String = ""
        var isRunning: Boolean = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
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
}
