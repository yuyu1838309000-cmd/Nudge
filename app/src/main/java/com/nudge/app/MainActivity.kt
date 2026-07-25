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
import android.widget.EditText
import android.widget.ImageView
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

    private lateinit var statusDot: ImageView
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var testResult: TextView
    private lateinit var permUsageText: TextView
    private lateinit var permUsageBtn: Button
    private lateinit var permAccessText: TextView
    private lateinit var permAccessBtn: Button
    private lateinit var permNotifText: TextView
    private lateinit var permNotifBtn: Button
    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var promptInput: EditText
    private lateinit var apiSaveBtn: Button
    private lateinit var apiStatus: TextView
    private lateinit var versionText: TextView
    private lateinit var toolCountText: TextView
    private lateinit var toolsContainer: LinearLayout
    private val handler = Handler(Looper.getMainLooper())

    private data class ToolDef(val name: String, val desc: String, val status: String)

    private val tools = listOf(
        ToolDef("ping", "测试连通性", "ok"),
        ToolDef("wake_up", "亮屏唤醒", "ok"),
        ToolDef("get_foreground_app", "获取前台应用包名和名称", "ok"),
        ToolDef("screenshot_analyze", "截屏并用AI分析内容", "ok"),
        ToolDef("sensor_data", "加速度/光线/陀螺仪等传感器", "ok"),
        ToolDef("device_status", "锁屏/电量/充电/网络状态", "ok"),
        ToolDef("get_location", "GPS定位经纬度", "ok"),
        ToolDef("get_notifications", "最近通知列表", "ok"),
        ToolDef("get_steps", "今日步数", "ok"),
        ToolDef("calendar_query", "查询日历事件", "ok"),
        ToolDef("set_alarm", "设置系统闹钟", "ok"),
        ToolDef("lock_screen", "强制锁屏", "ok"),
        ToolDef("media_play_pause", "媒体播放/暂停", "ok"),
        ToolDef("media_next", "媒体下一首", "ok"),
        ToolDef("media_previous", "媒体上一首", "ok"),
        ToolDef("press_back", "返回键", "ok"),
        ToolDef("press_home", "回桌面", "ok"),
        ToolDef("open_app", "打开指定应用", "ok"),
        ToolDef("tap", "点击屏幕指定坐标", "ok"),
        ToolDef("swipe", "滑动屏幕", "ok"),
        ToolDef("read_screen", "读取当前界面所有文字", "ok"),
        ToolDef("switch_to_rikkahub", "切回RikkaHub对话", "ok"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        testResult = findViewById(R.id.testResult)
        permUsageText = findViewById(R.id.permUsageText)
        permUsageBtn = findViewById(R.id.permUsageBtn)
        permAccessText = findViewById(R.id.permAccessText)
        permAccessBtn = findViewById(R.id.permAccessBtn)
        permNotifText = findViewById(R.id.permNotifText)
        permNotifBtn = findViewById(R.id.permNotifBtn)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        modelInput = findViewById(R.id.modelInput)
        urlInput = findViewById(R.id.urlInput)
        promptInput = findViewById(R.id.promptInput)
        apiSaveBtn = findViewById(R.id.apiSaveBtn)
        apiStatus = findViewById(R.id.apiStatus)
        versionText = findViewById(R.id.versionText)
        toolCountText = findViewById(R.id.toolCountText)
        toolsContainer = findViewById(R.id.toolsContainer)

        try {
            val info = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "v${info.versionName}"
        } catch (_: Exception) {}

        val prefs = getSharedPreferences("nudge", MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("api_key", ""))
        modelInput.setText(prefs.getString("model", "Qwen/Qwen3.6-35B-A3B"))
        urlInput.setText(prefs.getString("api_url", "https://api.siliconflow.cn/v1/chat/completions"))
        promptInput.setText(prefs.getString("prompt", "如实描述这个手机屏幕截图的内容，语气自然口语化，简洁直接。看到啥说啥，不加多余评价。不超过100字。"))

        apiSaveBtn.setOnClickListener {
            prefs.edit()
                .putString("api_key", apiKeyInput.text.toString().trim())
                .putString("model", modelInput.text.toString().trim())
                .putString("api_url", urlInput.text.toString().trim())
                .putString("prompt", promptInput.text.toString().trim())
                .apply()
            apiStatus.text = "已保存"
            apiStatus.postDelayed({ apiStatus.text = "" }, 2000)
        }

        toggleButton.setOnClickListener { toggleService() }
        permUsageBtn.setOnClickListener { openUsageAccessSettings() }
        permAccessBtn.setOnClickListener { openAccessibilitySettings() }
        permNotifBtn.setOnClickListener { openNotificationSettings() }

        updateUI()
        runSelfTest()
        updatePermissionUI()
        buildToolList()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        runSelfTest()
        updatePermissionUI()
    }

    private fun buildToolList() {
        val okCount = tools.count { it.status == "ok" }
        val wipCount = tools.count { it.status == "wip" }
        toolCountText.text = "${tools.size} 个工具 | $okCount 可用 · $wipCount 调试中"

        val sections = listOf(
            "已就绪" to tools.filter { it.status == "ok" },
            "调试中" to tools.filter { it.status == "wip" },
        )

        for ((label, items) in sections) {
            if (items.isEmpty()) continue

            val sectionLabel = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(Color.parseColor("#6B7280"))
                setPadding(0, 8, 0, 6)
            }
            toolsContainer.addView(sectionLabel)

            for (tool in items) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 6, 0, 6)
                }

                val dotId = when (tool.status) {
                    "ok" -> R.drawable.dot_green
                    "wip" -> R.drawable.dot_orange
                    else -> R.drawable.dot_gray
                }
                val dot = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(10, 10).apply { setMargins(0, 0, 10, 0) }
                    setImageResource(dotId)
                }
                row.addView(dot)

                val nameTv = TextView(this).apply {
                    text = tool.name
                    textSize = 13f
                    setTextColor(Color.parseColor("#9CA3AF"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(nameTv)

                val descTv = TextView(this).apply {
                    text = tool.desc
                    textSize = 12f
                    setTextColor(Color.parseColor("#6B7280"))
                    maxLines = 1
                }
                row.addView(descTv)

                toolsContainer.addView(row)
            }
        }
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
            permUsageText.setTextColor(Color.parseColor("#4ADE80"))
            permUsageBtn.visibility = View.GONE
        } else {
            permUsageText.text = "✗ 使用情况访问"
            permUsageText.setTextColor(Color.parseColor("#F87171"))
            permUsageBtn.text = "去授权"
            permUsageBtn.visibility = View.VISIBLE
        }
        if (isAccessibilityEnabled()) {
            permAccessText.text = "✓ 无障碍服务"
            permAccessText.setTextColor(Color.parseColor("#4ADE80"))
            permAccessBtn.visibility = View.GONE
        } else {
            permAccessText.text = "✗ 无障碍服务"
            permAccessText.setTextColor(Color.parseColor("#F87171"))
            permAccessBtn.text = "去开启"
            permAccessBtn.visibility = View.VISIBLE
        }
        if (NudgeNotificationService.isRunning) {
            permNotifText.text = "✓ 通知监听"
            permNotifText.setTextColor(Color.parseColor("#4ADE80"))
            permNotifBtn.visibility = View.GONE
        } else {
            permNotifText.text = "✗ 通知监听"
            permNotifText.setTextColor(Color.parseColor("#F87171"))
            permNotifBtn.text = "去开启"
            permNotifBtn.visibility = View.VISIBLE
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        if (NudgeAccessibilityService.isRunning) return true
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("NudgeAccessibilityService") ||
               enabled.contains("nudge") ||
               enabled.contains("Nudge")
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openUsageAccessSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun updateUI() {
        val running = isServiceRunning()
        if (running) {
            statusDot.setImageResource(R.drawable.dot_green)
            statusText.text = "MCP 运行中  :8809"
            toggleButton.text = "停止 MCP"
            toggleButton.setBackgroundResource(R.drawable.btn_danger)
        } else {
            statusDot.setImageResource(R.drawable.dot_gray)
            statusText.text = "MCP 已停止"
            toggleButton.text = "启动 MCP"
            toggleButton.setBackgroundResource(R.drawable.btn_primary)
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
                    handler.post { testResult.text = "✓ 自检通过" }
                } else {
                    handler.post { testResult.text = "✗ 响应异常" }
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
