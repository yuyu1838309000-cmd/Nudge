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
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class MainActivity : ComponentActivity() {

    private lateinit var statusDot: ImageView
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var testResult: TextView
    private lateinit var headerDot: ImageView
    private lateinit var permUsageText: TextView
    private lateinit var permUsageBtn: Button
    private lateinit var permAccessText: TextView
    private lateinit var permAccessBtn: Button
    private lateinit var permNotifText: TextView
    private lateinit var permNotifBtn: Button
    private lateinit var permProgress: ProgressBar
    private lateinit var permProgressText: TextView
    private lateinit var apiKeyInput: EditText
    private lateinit var modelInput: EditText
    private lateinit var urlInput: EditText
    private lateinit var promptInput: EditText
    private lateinit var apiSaveBtn: Button
    private lateinit var apiStatus: TextView
    private lateinit var versionText: TextView
    private lateinit var searchInput: EditText
    private lateinit var toolCountText: TextView
    private lateinit var toolsLeftCol: LinearLayout
    private lateinit var toolsRightCol: LinearLayout
    private lateinit var tabOverview: TextView
    private lateinit var tabTools: TextView
    private lateinit var tabSettings: TextView
    private lateinit var pageOverview: View
    private lateinit var pageTools: View
    private lateinit var pageSettings: View
    private val handler = Handler(Looper.getMainLooper())

    private data class ToolDef(
        val name: String,
        val desc: String,
        val status: String,
        val params: String = "",
        val testable: Boolean = false
    )

    private val tools = listOf(
        ToolDef("ping", "测试连通性", "ok", testable = true),
        ToolDef("wake_up", "亮屏唤醒", "ok", testable = true),
        ToolDef("get_foreground_app", "前台应用包名/名称/界面/停留时长", "ok", testable = true),
        ToolDef("screenshot_analyze", "截屏并用AI分析内容", "ok", "无参，耗时约10-30秒", true),
        ToolDef("sensor_data", "加速度/光线/陀螺仪等传感器", "ok", testable = true),
        ToolDef("device_status", "锁屏/电量/充电/网络状态", "ok", testable = true),
        ToolDef("get_location", "GPS定位(经纬度+地址)", "ok", "需要定位权限", true),
        ToolDef("get_notifications", "最近通知列表", "ok", "需要通知监听权限", true),
        ToolDef("get_steps", "今日步数", "ok", testable = true),
        ToolDef("calendar_query", "查询日历事件", "ok", "days: 查询天数，默认7", true),
        ToolDef("set_alarm", "设置系统闹钟", "ok", "hour, minute 必填；message 备注可选"),
        ToolDef("lock_screen", "强制锁屏", "ok", testable = true),
        ToolDef("media_play_pause", "媒体播放/暂停", "ok", testable = true),
        ToolDef("media_next", "媒体下一首", "ok", testable = true),
        ToolDef("media_previous", "媒体上一首", "ok", testable = true),
        ToolDef("press_back", "返回键", "ok", testable = true),
        ToolDef("press_home", "回桌面", "ok", testable = true),
        ToolDef("open_app", "打开指定应用", "ok", "package: 应用包名必填"),
        ToolDef("read_screen", "读取当前界面所有文字", "ok", testable = true),
        ToolDef("switch_to_rikkahub", "切回RikkaHub对话", "ok", testable = true),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        testResult = findViewById(R.id.testResult)
        headerDot = findViewById(R.id.headerDot)
        permUsageText = findViewById(R.id.permUsageText)
        permUsageBtn = findViewById(R.id.permUsageBtn)
        permAccessText = findViewById(R.id.permAccessText)
        permAccessBtn = findViewById(R.id.permAccessBtn)
        permNotifText = findViewById(R.id.permNotifText)
        permNotifBtn = findViewById(R.id.permNotifBtn)
        permProgress = findViewById(R.id.permProgress)
        permProgressText = findViewById(R.id.permProgressText)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        modelInput = findViewById(R.id.modelInput)
        urlInput = findViewById(R.id.urlInput)
        promptInput = findViewById(R.id.promptInput)
        apiSaveBtn = findViewById(R.id.apiSaveBtn)
        apiStatus = findViewById(R.id.apiStatus)
        versionText = findViewById(R.id.versionText)
        searchInput = findViewById(R.id.searchInput)
        toolCountText = findViewById(R.id.toolCountText)
        toolsLeftCol = findViewById(R.id.toolsLeftCol)
        toolsRightCol = findViewById(R.id.toolsRightCol)
        tabOverview = findViewById(R.id.tabOverview)
        tabTools = findViewById(R.id.tabTools)
        tabSettings = findViewById(R.id.tabSettings)
        pageOverview = findViewById(R.id.pageOverview)
        pageTools = findViewById(R.id.pageTools)
        pageSettings = findViewById(R.id.pageSettings)

        try {
            val info = packageManager.getPackageInfo(packageName, 0)
            versionText.text = "v${info.versionName}"
        } catch (_: Exception) {}

        val prefs = getSharedPreferences("nudge", MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("api_key", ""))
        modelInput.setText(prefs.getString("model", "Qwen/Qwen2.5-VL-32B-Instruct"))
        urlInput.setText(prefs.getString("api_url", "https://api.siliconflow.cn/v1/chat/completions"))
        promptInput.setText(prefs.getString("prompt", "如实描述这个手机屏幕截图的内容，语气自然口语化，简洁直接。看到啥说啥，不加多余评价。不超过100字。"))

        tabOverview.setOnClickListener { switchTab(0) }
        tabTools.setOnClickListener { switchTab(1) }
        tabSettings.setOnClickListener { switchTab(2) }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                buildToolGrid(s?.toString() ?: "")
            }
        })

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

        switchTab(0)
        buildToolGrid()
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

    private fun switchTab(tab: Int) {
        pageOverview.visibility = if (tab == 0) View.VISIBLE else View.GONE
        pageTools.visibility = if (tab == 1) View.VISIBLE else View.GONE
        pageSettings.visibility = if (tab == 2) View.VISIBLE else View.GONE
        tabOverview.isSelected = tab == 0
        tabTools.isSelected = tab == 1
        tabSettings.isSelected = tab == 2
        tabOverview.setTextColor(Color.parseColor(if (tab == 0) "#8B9EFF" else "#6B7280"))
        tabTools.setTextColor(Color.parseColor(if (tab == 1) "#8B9EFF" else "#6B7280"))
        tabSettings.setTextColor(Color.parseColor(if (tab == 2) "#8B9EFF" else "#6B7280"))
    }

    private fun buildToolGrid(filter: String = "") {
        toolsLeftCol.removeAllViews()
        toolsRightCol.removeAllViews()
        val okCount = tools.count { it.status == "ok" }
        val wipCount = tools.count { it.status == "wip" }
        toolCountText.text = "${tools.size} 个工具 | $okCount 可用 · $wipCount 调试中"

        val kw = filter.trim().lowercase()
        val filtered = if (kw.isEmpty()) tools else tools.filter {
            it.name.lowercase().contains(kw) || it.desc.lowercase().contains(kw)
        }

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = "没有匹配的工具"
                textSize = 13f
                setTextColor(Color.parseColor("#6B7280"))
                gravity = Gravity.CENTER
                setPadding(0, 48, 0, 0)
            }
            toolsLeftCol.addView(empty, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            return
        }

        filtered.forEachIndexed { index, tool ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.tool_card_bg)
                setPadding(14, 14, 14, 14)
                isClickable = true
                isFocusable = true
            }
            val col = if (index % 2 == 0) toolsLeftCol else toolsRightCol
            col.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 })

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val dot = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(8, 8).apply { setMargins(0, 0, 8, 0) }
                setImageResource(if (tool.status == "ok") R.drawable.dot_green else R.drawable.dot_orange)
            }
            topRow.addView(dot)
            val nameTv = TextView(this).apply {
                text = tool.name
                textSize = 13f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            topRow.addView(nameTv)
            card.addView(topRow)

            val descTv = TextView(this).apply {
                text = tool.desc
                textSize = 11f
                setTextColor(Color.parseColor("#9CA3AF"))
                maxLines = 2
                setPadding(0, 4, 0, 0)
            }
            card.addView(descTv)

            val detail = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = ContextCompat.getDrawable(this@MainActivity, R.drawable.detail_bg)
                setPadding(12, 10, 12, 10)
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10 }
            }
            if (tool.params.isNotEmpty()) {
                val paramsTv = TextView(this).apply {
                    text = "参数: ${tool.params}"
                    textSize = 11f
                    setTextColor(Color.parseColor("#6B7280"))
                }
                detail.addView(paramsTv)
            }
            if (tool.testable) {
                val testBtn = TextView(this).apply {
                    text = "测试"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    background = ContextCompat.getDrawable(this@MainActivity, R.drawable.btn_outline)
                    gravity = Gravity.CENTER
                    minHeight = 0
                    isClickable = true
                    isFocusable = true
                    setPadding(0, 10, 0, 10)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 8 }
                }
                val resultTv = TextView(this).apply {
                    textSize = 11f
                    setTextColor(Color.parseColor("#9CA3AF"))
                    setPadding(0, 8, 0, 0)
                }
                testBtn.setOnClickListener {
                    resultTv.text = "调用中..."
                    callMCP(tool.name, resultTv)
                }
                detail.addView(testBtn)
                detail.addView(resultTv)
            }
            card.addView(detail)

            card.setOnClickListener {
                detail.visibility = if (detail.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
    }

    private fun callMCP(toolName: String, resultView: TextView) {
        Thread {
            try {
                val s = Socket("127.0.0.1", 8809)
                s.soTimeout = 30000
                val payload = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", 1)
                    put("method", "tools/call")
                    put("params", JSONObject().apply {
                        put("name", toolName)
                        put("arguments", JSONObject())
                    })
                }.toString()
                val bodyBytes = payload.toByteArray()
                val req = "POST / HTTP/1.1\r\nHost: 127.0.0.1:8809\r\nContent-Type: application/json\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\n\r\n$payload"
                val out = s.getOutputStream()
                out.write(req.toByteArray())
                out.flush()
                val inp = BufferedReader(InputStreamReader(s.getInputStream()))
                var line: String? = ""
                val sb = StringBuilder()
                while (inp.readLine().also { line = it } != null) {
                    sb.append(line).append("\n")
                }
                s.close()
                val resp = sb.toString()
                val body = when {
                    resp.contains("\r\n\r\n") -> resp.substringAfter("\r\n\r\n")
                    resp.contains("\n\n") -> resp.substringAfter("\n\n")
                    else -> resp
                }
                val result = try {
                    val json = JSONObject(body)
                    val content = json.optJSONArray("content")
                    if (content != null && content.length() > 0) {
                        content.getJSONObject(0).optString("text", "")
                    } else json.toString()
                } catch (_: Exception) {
                    body
                }
                handler.post { resultView.text = result.take(600) }
            } catch (e: Exception) {
                handler.post { resultView.text = "调用失败: ${e.message}" }
            }
        }.start()
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
        val usage = hasUsageAccess()
        val access = isAccessibilityEnabled()
        val notif = NudgeNotificationService.isRunning
        val done = listOf(usage, access, notif).count { it }
        permProgress.progress = done
        permProgressText.text = "$done/3"

        if (usage) {
            permUsageText.text = "✓ 使用情况访问"
            permUsageText.setTextColor(Color.parseColor("#4ADE80"))
            permUsageBtn.visibility = View.GONE
        } else {
            permUsageText.text = "✗ 使用情况访问"
            permUsageText.setTextColor(Color.parseColor("#F87171"))
            permUsageBtn.text = "去授权"
            permUsageBtn.visibility = View.VISIBLE
        }
        if (access) {
            permAccessText.text = "✓ 无障碍服务"
            permAccessText.setTextColor(Color.parseColor("#4ADE80"))
            permAccessBtn.visibility = View.GONE
        } else {
            permAccessText.text = "✗ 无障碍服务"
            permAccessText.setTextColor(Color.parseColor("#F87171"))
            permAccessBtn.text = "去开启"
            permAccessBtn.visibility = View.VISIBLE
        }
        if (notif) {
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
            headerDot.setImageResource(R.drawable.dot_green)
            statusText.text = "MCP 运行中  :8809"
            toggleButton.text = "停止 MCP"
            toggleButton.setBackgroundResource(R.drawable.btn_danger)
        } else {
            statusDot.setImageResource(R.drawable.dot_gray)
            headerDot.setImageResource(R.drawable.dot_gray)
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
