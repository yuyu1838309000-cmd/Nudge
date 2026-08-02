package com.nudge.app

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class ToolsActivity : ComponentActivity() {

    private lateinit var searchInput: EditText
    private lateinit var toolCountText: TextView
    private lateinit var toolsLeftCol: LinearLayout
    private lateinit var toolsRightCol: LinearLayout
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

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
        setContentView(R.layout.activity_tools)

        searchInput = findViewById(R.id.searchInput)
        toolCountText = findViewById(R.id.toolCountText)
        toolsLeftCol = findViewById(R.id.toolsLeftCol)
        toolsRightCol = findViewById(R.id.toolsRightCol)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                buildToolGrid(s?.toString() ?: "")
            }
        })

        buildToolGrid()
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
                background = ContextCompat.getDrawable(this@ToolsActivity, R.drawable.tool_card_bg)
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
                background = ContextCompat.getDrawable(this@ToolsActivity, R.drawable.detail_bg)
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
                    background = ContextCompat.getDrawable(this@ToolsActivity, R.drawable.btn_outline)
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
}
