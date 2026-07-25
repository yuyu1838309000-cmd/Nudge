package com.nudge.app

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }

        layout.addView(TextView(this).apply {
            text = "Nudge"
            textSize = 32f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })

        layout.addView(TextView(this).apply {
            text = "v0.1.0"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 0)
        })

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 36)
        }

        statusDot = View(this).apply {
            val size = 14
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, 0, 12, 0)
            }
        }
        statusRow.addView(statusDot)

        statusText = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
        }
        statusRow.addView(statusText)
        layout.addView(statusRow)

        toggleButton = Button(this).apply {
            textSize = 16f
            setPadding(48, 16, 48, 16)
        }
        layout.addView(toggleButton)

        setContentView(layout)

        toggleButton.setOnClickListener { toggleService() }
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (McpService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun updateUI() {
        val running = isServiceRunning()
        if (running) {
            statusDot.setBackgroundColor(Color.parseColor("#4CAF50"))
            statusText.text = "MCP 运行中  :8809"
            toggleButton.text = "停止 MCP"
        } else {
            statusDot.setBackgroundColor(Color.parseColor("#555555"))
            statusText.text = "MCP 已停止"
            toggleButton.text = "启动 MCP"
        }
    }

    private fun toggleService() {
        if (isServiceRunning()) {
            stopService(Intent(this, McpService::class.java))
            handler.postDelayed({ updateUI() }, 300)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return
                }
            }
            startForegroundService(Intent(this, McpService::class.java))
            handler.postDelayed({ updateUI() }, 300)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startForegroundService(Intent(this, McpService::class.java))
        }
        handler.postDelayed({ updateUI() }, 300)
    }
}
