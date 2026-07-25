package com.nudge.app

import android.Manifest
import android.app.ActivityManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class MainActivity : ComponentActivity() {

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var testResult: TextView
    private lateinit var permUsageText: TextView
    private lateinit var permUsageBtn: Button
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
            setPadding(0, 36, 0, 12)
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

        testResult = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }
        layout.addView(testResult)

        // 权限状态区域
        val permLabel = TextView(this).apply {
            text = "权限状态"
            textSize = 14f
            setTextColor(Color.parseColor("#aaaaaa"))
            gravity = Gravity.CENTER
            setPadding(0, 32, 0, 12)
        }
        layout.addView(permLabel)

        permUsageText = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
        }
        layout.addView(permUsageText)

        permUsageBtn = Button(this).apply {
            textSize = 14f
            setPadding(32, 10, 32, 10)
        }
        layout.addView(permUsageBtn)

        setContentView(layout)

        toggleButton.setOnClickListener { toggleService() }
        permUsageBtn.setOnClickListener { openUsageAccessSettings() }
        updateUI()
        runSelfTest()
        updatePermissionUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        runSelfTest()
        updatePermissionUI()
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

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun updatePermissionUI() {
        if (hasUsageAccess()) {
            permUsageText.text = "✓ 使用情况访问"
            permUsageText.setTextColor(Color.parseColor("#4CAF50"))
            permUsageBtn.visibility = View.GONE
        } else {
            permUsageText.text = "✗ 使用情况访问"
            permUsageText.setTextColor(Color.parseColor("#ff6b6b"))
            permUsageBtn.text = "去授权"
            permUsageBtn.visibility = View.VISIBLE
        }
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
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

    private fun runSelfTest() {
        testResult.text = "检测中..."
        Thread {
            try {
                val s = Socket("127.0.0.1", 8809)
                val out = OutputStreamWriter(s.getOutputStream())
                out.write("GET /ping HTTP/1.0\r\nHost: 127.0.0.1\r\n\r\n")
                out.flush()
                val inp = BufferedReader(InputStreamReader(s.getInputStream()))
                var line: String? = ""
                val sb = StringBuilder()
                while (inp.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                s.close()
                val body = sb.toString()
                if (body.contains("pong")) {
                    handler.post { testResult.text = "✓ MCP 自检通过" }
                } else {
                    handler.post { testResult.text = "✗ 响应异常: $body" }
                }
            } catch (e: Exception) {
                handler.post { testResult.text = "✗ 连接失败: ${e.message}" }
            }
        }.start()
    }

    private fun toggleService() {
        if (isServiceRunning()) {
            stopService(Intent(this, McpService::class.java))
            handler.postDelayed({ updateUI(); runSelfTest() }, 500)
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
            handler.postDelayed({ updateUI(); runSelfTest() }, 500)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startForegroundService(Intent(this, McpService::class.java))
        }
        handler.postDelayed({ updateUI(); runSelfTest() }, 500)
    }
}
