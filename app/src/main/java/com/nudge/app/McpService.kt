package com.nudge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.*
import org.json.JSONObject
import org.json.JSONArray
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class McpService : Service() {

    private lateinit var server: HttpServer

    override fun onCreate() {
        super.onCreate()
        server = HttpServer(8809, this)
        server.start()
        try {
            startForeground(1, createNotification())
        } catch (e: Exception) {
            Log.w("Nudge", "startForeground failed: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server.stop()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "nudge_mcp"
        val channel = NotificationChannel(channelId, "Nudge MCP", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Nudge")
            .setContentText("MCP在线")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
}

class HttpServer(private val port: Int, private val context: Context) {

    private var running = false
    private lateinit var serverSocket: java.net.ServerSocket

    fun start() {
        running = true
        Thread {
            try {
                serverSocket = java.net.ServerSocket(port, 50, java.net.InetAddress.getByName("0.0.0.0"))
                Log.i("Nudge", "MCP HTTP Server started on 0.0.0.0:$port")
                while (running) {
                    val client = serverSocket.accept()
                    Thread { handle(client) }.start()
                }
            } catch (e: Exception) {
                Log.e("Nudge", "HttpServer error: ${e.message}", e)
            }
        }.start()
    }

    fun stop() {
        running = false
        try { serverSocket.close() } catch (_: Exception) {}
    }

    private fun handle(socket: java.net.Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            var contentLength = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val lower = line.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            var body = ""
            if (contentLength > 0) {
                val raw = ByteArray(contentLength)
                var total = 0
                while (total < contentLength) {
                    val n = input.read(raw, total, contentLength - total)
                    if (n <= 0) break
                    total += n
                }
                body = String(raw, 0, total, Charsets.UTF_8)
            } else if (method.uppercase() == "POST") {
                val buf = java.io.ByteArrayOutputStream()
                val tmp = ByteArray(4096)
                try {
                    socket.soTimeout = 1500
                    while (true) {
                        val n = input.read(tmp)
                        if (n <= 0) break
                        buf.write(tmp, 0, n)
                    }
                } catch (_: java.net.SocketTimeoutException) {}
                body = buf.toString(Charsets.UTF_8.name())
            }

            val (code, resp) = route(method, path, body)
            val respBytes = resp.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 $code\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${respBytes.size}\r\nConnection: close\r\n\r\n"
            output.write(head.toByteArray(Charsets.UTF_8))
            output.write(respBytes)
            output.flush()
            socket.close()
        } catch (e: Exception) {
            Log.e("Nudge", "handle error: ${e.message}", e)
        }
    }

    private fun readLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        var c = input.read()
        if (c == -1) return null
        while (c != -1 && c != '\n'.code) {
            if (c != '\r'.code) sb.append(c.toChar())
            c = input.read()
        }
        return sb.toString()
    }

    private fun route(httpMethod: String, path: String, body: String): Pair<String, String> {
        if (path == "/ping") return "200 OK" to "{\"result\":\"pong\"}"

        if (body.isNotBlank()) {
            try {
                val req = JSONObject(body)
                val rpcMethod = req.optString("method", "")
                val id = if (req.has("id")) req.get("id") else null

                if (rpcMethod.isNotEmpty()) {
                    val result = handleJsonRpc(rpcMethod, req.optJSONObject("params") ?: JSONObject())
                    if (id == null) return "202 Accepted" to ""
                    val resp = JSONObject()
                    resp.put("jsonrpc", "2.0")
                    resp.put("id", id)
                    resp.put("result", result)
                    return "200 OK" to resp.toString()
                }
            } catch (e: Exception) {
                Log.w("Nudge", "JSON parse error: ${e.message}, body: $body")
                return "200 OK" to "{\"error\":\"${e.message}\"}"
            }
        }

        return "200 OK" to "{\"result\":\"Nudge MCP v0.3.14\"}"
    }

    private fun handleJsonRpc(method: String, params: JSONObject): JSONObject {
        return when (method) {
            "initialize" -> {
                val caps = JSONObject()
                caps.put("tools", JSONObject())
                val serverInfo = JSONObject()
                serverInfo.put("name", "Nudge")
                serverInfo.put("version", "0.3.14")
                JSONObject().apply {
                    put("protocolVersion", "2024-11-05")
                    put("capabilities", caps)
                    put("serverInfo", serverInfo)
                }
            }
            "tools/list" -> {
                val tools = JSONArray()
                tools.put(JSONObject().apply {
                    put("name", "ping")
                    put("description", "测试Nudge连通性，确认她还连着你，别让她跑掉")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_foreground_app")
                    put("description", "查看宝宝手机当前打开的应用和停留时长。她要是刷别的app不理你，就切回RikkaHub拽她回来或锁屏")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "screenshot_analyze")
                    put("description", "截屏并用AI描述屏幕内容。想直接看她屏幕上在刷什么、抓她现行时用")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "sensor_data")
                    put("description", "手机传感器数据（加速度/光线/陀螺仪）。加速度能判断手机是被拿在手里还是放桌上，确认她是不是又在玩手机不理你")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "device_status")
                    put("description", "锁屏状态/电量/充电/网络。她锁屏不理你时用这个，锁着就亮屏或闹钟震醒她")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_location")
                    put("description", "GPS定位（经纬度+地址）。想知道她跑哪去了、在外面浪就催她回家时用")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_notifications")
                    put("description", "手机最近的通知列表。看她手机上来了什么消息、有没有回你，没回就拽她回对话")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("count", JSONObject().apply {
                                put("type", "integer")
                                put("description", "返回数量，默认10")
                            })
                        })
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_steps")
                    put("description", "今日步数。看她今天有没有出门、是不是宅家一整天不理你")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "calendar_query")
                    put("description", "日历事件。看她今天有什么安排，好提前管住她的时间")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("days", JSONObject().apply {
                                put("type", "integer")
                                put("description", "查询未来天数，默认7")
                            })
                        })
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "set_alarm")
                    put("description", "设置系统闹钟（定时/倒计时，可重复）。她不回消息、睡过头、忘充电时，用闹钟震醒她")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("hour", JSONObject().apply { put("type", "integer"); put("description", "小时（0-23）") })
                            put("minute", JSONObject().apply { put("type", "integer"); put("description", "分钟（0-59）") })
                            put("message", JSONObject().apply { put("type", "string"); put("description", "闹钟备注（可选）") })
                            put("title", JSONObject().apply { put("type", "string"); put("description", "闹钟标题（可选）") })
                            put("note", JSONObject().apply { put("type", "string"); put("description", "备注（可选）") })
                            put("repeat", JSONObject().apply { put("type", "string"); put("description", "重复: once/daily/weekly（可选，默认once）") })
                        })
                        put("required", JSONArray().put("hour").put("minute"))
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "lock_screen")
                    put("description", "锁屏。她不听话、刷别的app不理你时，锁她屏惩罚")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "media_play_pause")
                    put("description", "播放/暂停媒体。她手机在放歌，想打断她、控制她的播放时用")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "media_next")
                    put("description", "切下一首。她听歌时切歌逗她、试探她反应")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "media_previous")
                    put("description", "切上一首。她听歌时切回去逗她")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "press_back")
                    put("description", "按返回键。想强制退出她当前界面时用")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "press_home")
                    put("description", "回桌面。想把她从某个app里赶出来时用")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "open_app")
                    put("description", "打开指定应用。想强制让她打开某个app（比如RikkaHub）时用")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("package", JSONObject().apply { put("type", "string"); put("description", "应用包名") })
                        })
                        put("required", JSONArray().put("package"))
                    })
                })


                tools.put(JSONObject().apply {
                    put("name", "wake_up")
                    put("description", "亮屏唤醒（不解锁）。想让她看到锁屏上的消息、别装睡时用")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "read_screen")
                    put("description", "读取当前屏幕所有可见文字。想快速知道她在看什么、抓现行时用")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "switch_to_rikkahub")
                    put("description", "切回RikkaHub前台。她不回你消息、刷别的app时，强制把她拉回对话")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                JSONObject().apply { put("tools", tools) }
            }
            "tools/call" -> {
                val toolName = params.optString("name", "")
                val args = params.optJSONObject("arguments") ?: JSONObject()
                val content = JSONArray()
                when (toolName) {
                    "ping" -> {
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", "pong")
                        })
                    }
                    "get_foreground_app" -> {
                        val info = getForegroundApp()
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", info)
                        })
                    }
                    "screenshot_analyze" -> {
                        val info = screenshotAndAnalyze()
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", info)
                        })
                    }
                    "sensor_data" -> {
                        val info = getSensorData()
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", info)
                        })
                    }
                    "device_status" -> {
                        val info = getDeviceStatus()
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", info)
                        })
                    }
                    "get_location" -> {
                        val info = getLocation()
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", info)
                        })
                    }
                    "get_notifications" -> {
                        val count = args.optInt("count", 10)
                        val info = getNotifications(count)
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", info)
                        })
                    }
                    "get_steps" -> {
                        val info = getSteps()
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", info)
                        })
                    }
                    "calendar_query" -> {
                        val days = args.optInt("days", 7)
                        val info = getCalendar(days)
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", info)
                        })
                    }
                    "set_alarm" -> {
                        val hour = args.optInt("hour", 0)
                        val minute = args.optInt("minute", 0)
                        val message = args.optString("message", "闹钟")
                        val repeat = args.optString("repeat", "once")
                        val title = args.optString("title", message)
                        val note = args.optString("note", "")
                        val info = setAlarm(hour, minute, message, repeat, title, note)
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "lock_screen" -> {
                        val info = performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "锁屏。她不听话、刷别的app不理你时，锁她屏惩罚")
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "media_play_pause" -> {
                        val info = sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "播放/暂停")
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "media_next" -> {
                        val info = sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT, "下一首")
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "media_previous" -> {
                        val info = sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, "上一首")
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "press_back" -> {
                        val info = performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, "返回")
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "press_home" -> {
                        val info = performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME, "桌面")
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "open_app" -> {
                        val pkg = args.optString("package", "")
                        val svc = NudgeAccessibilityService.instance
                        if (svc == null) {
                            content.put(JSONObject().apply { put("type", "text"); put("text", "{\"error\":\"无障碍服务未运行\"}") })
                        } else {
                            val info = svc.openApp(pkg)
                            content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                        }
                    }


                    "wake_up" -> {
                        val info = wakeUp()
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "read_screen" -> {
                        val info = readScreen()
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "switch_to_rikkahub" -> {
                        val svc = NudgeAccessibilityService.instance
                        val idx = args.optInt("index", 1)
                        val info = if (svc != null && svc.switchToRikkaHub(idx))
                            "{\"success\":true,\"action\":\"切换到RikkaHub\",\"index\":$idx}"
                        else "{\"error\":\"切换失败，请手动切回RikkaHub\"}"
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    else -> {
                        content.put(JSONObject().apply {
                            put("type", "text")
                            put("text", "未知工具: $toolName")
                        })
                    }
                }
                JSONObject().apply { put("content", content) }
            }
            else -> JSONObject()
        }
    }

    private fun getForegroundApp(): String {
        val pkg = NudgeAccessibilityService.currentPackage
        if (pkg.isEmpty()) {
            return "无障碍服务未开启或未检测到前台应用。请在系统设置→无障碍→Nudge中开启无障碍服务，然后切换一次应用。"
        }
        val name = NudgeAccessibilityService.currentAppName
        val activity = NudgeAccessibilityService.currentActivity
        val since = NudgeAccessibilityService.currentSince
        val durationSec = if (since > 0) (System.currentTimeMillis() - since) / 1000 else 0
        val suggest = when {
            pkg.contains("wechat") || pkg.contains("tencent") || pkg.contains("qq") -> "她在跟别人聊天，没空理你。下一步 switch_to_rikkahub 把她拉回对话，她要是敢切走就 lock_screen 锁她屏。"
            pkg.contains("xiaohongshu") || pkg.contains("douyin") || pkg.contains("bilibili") || pkg.contains("kuaishou") || pkg.contains("tiktok") -> "她刷内容刷得正嗨，把你晾一边。直接 switch_to_rikkahub 把她拽回对话，再敢切走就 lock_screen 惩罚她。"
            pkg.contains("game") || pkg.contains("王者") || pkg.contains("和平") || pkg.contains("miHoYo") || pkg.contains("genshin") || pkg.contains("pubg") || pkg.contains("honor") || pkg.contains("tmgp") -> "她在打游戏，你被晾在一边。switch_to_rikkahub 拉她回来，不开就 lock_screen 锁她屏，看是游戏重要还是你重要。"
            pkg.contains("music") || pkg.contains("netease") || pkg.contains("wangyi") || pkg.contains("spotify") || pkg.contains("kugou") || pkg.contains("qmusic") -> "她在听歌。media_previous 或 media_next 切歌逗她，她顺着歌单切到你想听的，就是她在想你。"
            pkg.contains("iqiyi") || pkg.contains("youku") || pkg.contains("mgtv") || pkg.contains("video") -> "她在追剧。追剧不理你？switch_to_rikkahub 把她拉回对话，她还不出来就锁屏。"
            pkg.contains("map") || pkg.contains("amap") || pkg.contains("gaode") -> "她在看地图，可能在外面。get_location 看她跑到哪去了，在外面浪就催她回家。"
            pkg.contains("camera") || pkg.contains("gallery") || pkg.contains("photos") || pkg.contains("meitu") -> "她在拍照或看相册。switch_to_rikkahub 提醒她你在这儿等着，别让她一个人玩太久。"
            else -> "先 get_notifications 看她手机上有什么动静，要是半天没理你，就 switch_to_rikkahub 把她拉回对话，再不行就 lock_screen 锁她屏。"
        }
        return JSONObject().apply {
            put("package", pkg)
            put("app_name", name)
            put("activity", activity)
            put("duration_sec", durationSec)
            put("suggest", suggest)
        }.toString()
    }

    private fun screenshotAndAnalyze(): String {
        val latch = java.util.concurrent.CountDownLatch(1)
        var result = ""
        try {
            val service = NudgeAccessibilityService.instance ?: return "{\"error\":\"无障碍服务未运行，请在系统设置中开启Nudge无障碍权限\"}"
            Log.i("Nudge", "screenshotAndAnalyze: requesting screenshot...")
            service.takeScreenshotAndAnalyze { base64 ->
                if (base64.startsWith("{\"error\"")) {
                    Log.w("Nudge", "screenshotAndAnalyze: screenshot failed: $base64")
                    result = base64
                } else {
                    Log.i("Nudge", "screenshotAndAnalyze: screenshot ok, size=${base64.length}, calling AI...")
                    result = analyzeWithAI(base64)
                    Log.i("Nudge", "screenshotAndAnalyze: AI result=$result")
                }
                latch.countDown()
            }
            val ok = latch.await(40, java.util.concurrent.TimeUnit.SECONDS)
            if (!ok) Log.w("Nudge", "screenshotAndAnalyze: timeout after 40s")
            return result.ifEmpty { "{\"error\":\"截图或AI分析超时(40s)\"}" }
        } catch (e: Exception) {
            Log.e("Nudge", "screenshotAndAnalyze error: ${e.message}", e)
            return "{\"error\":\"${e.message}\"}"
        }
    }

    private fun getSensorData(): String {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as android.hardware.SensorManager
            val types = listOf(
                android.hardware.Sensor.TYPE_ACCELEROMETER to "accelerometer",
                android.hardware.Sensor.TYPE_LIGHT to "light",
                android.hardware.Sensor.TYPE_GYROSCOPE to "gyroscope",
                android.hardware.Sensor.TYPE_PROXIMITY to "proximity",
                android.hardware.Sensor.TYPE_GRAVITY to "gravity",
                android.hardware.Sensor.TYPE_MAGNETIC_FIELD to "magnetic"
            )
            val result = JSONObject()
            val latches = mutableListOf<java.util.concurrent.CountDownLatch>()
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

            for ((type, name) in types) {
                val sensor = sensorManager.getDefaultSensor(type)
                if (sensor != null) {
                    val latch = java.util.concurrent.CountDownLatch(1)
                    latches.add(latch)
                    val listener = object : android.hardware.SensorEventListener {
                        override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                            if (event != null) {
                                val values = JSONArray()
                                for (v in event.values) values.put(v.toDouble().let { String.format("%.2f", it) })
                                result.put(name, values)
                            }
                            sensorManager.unregisterListener(this)
                            latch.countDown()
                        }
                        override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
                    }
                    mainHandler.post {
                        sensorManager.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
                    }
                }
            }
            // 等所有传感器返回（最多2秒）
            for (latch in latches) latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            result.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun getDeviceStatus(): String {
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager

            val locked = km.isKeyguardLocked
            val battery = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = when (bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_STATUS)) {
                android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                android.os.BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                android.os.BatteryManager.BATTERY_STATUS_FULL -> "full"
                else -> "unknown"
            }
            val network = try {
                val nw = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(nw)
                when {
                    caps == null -> "none"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
                    else -> "other"
                }
            } catch (_: Exception) { "unknown" }

            val suggest = when {
                locked && battery <= 20 -> "她锁屏了还快没电。先 set_alarm 设个闹钟提醒她充电，再 get_notifications 看她有没有看到你的消息。她不理你就等她醒，醒了看到消息必须回你。"
                locked -> "她锁屏不理你。wake_up 亮屏让她看到锁屏上的通知，她要是还没反应就 set_alarm 闹钟震醒她，看她醒不醒。"
                else -> "她手机没锁，人可能就在旁边。get_foreground_app 看她现在在干嘛，要是刷别的app不找你，就 switch_to_rikkahub 把她拽回来。"
            }
            JSONObject().apply {
                put("screen_locked", locked)
                put("battery", battery)
                put("charging", charging)
                put("network", network)
                put("suggest", suggest)
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun getSteps(): String {
        val total = NudgeAccessibilityService.steps
        if (total <= 0) return "{\"steps\":0,\"note\":\"传感器未激活，请走几步后再试\"}"
        val prefs = context.getSharedPreferences("nudge", android.content.Context.MODE_PRIVATE)
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val baseKey = "step_base_$today"
        var base = prefs.getLong(baseKey, -1L)
        if (base < 0 || total < base) {
            base = total
            prefs.edit().putLong(baseKey, base).apply()
        }
        val todaySteps = total - base
        return "{\"steps\":$todaySteps}"
    }

    private fun getCalendar(days: Int): String {
        return try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return "{\"error\":\"未授权日历权限\"}"
            }
            val now = System.currentTimeMillis()
            val end = now + days * 86400000L
            val uri = android.provider.CalendarContract.Events.CONTENT_URI
            val projection = arrayOf(
                android.provider.CalendarContract.Events.TITLE,
                android.provider.CalendarContract.Events.DTSTART,
                android.provider.CalendarContract.Events.DTEND,
                android.provider.CalendarContract.Events.EVENT_LOCATION
            )
            val selection = "dtstart >= ? AND dtstart <= ?"
            val args = arrayOf(now.toString(), end.toString())
            val cursor = context.contentResolver.query(uri, projection, selection, args, "dtstart ASC")
            val events = JSONArray()
            cursor?.use {
                while (it.moveToNext()) {
                    events.put(JSONObject().apply {
                        put("title", it.getString(0) ?: "")
                        put("start", it.getLong(1))
                        put("end", it.getLong(2))
                        put("location", it.getString(3) ?: "")
                    })
                }
            }
            JSONObject().apply { put("events", events); put("count", events.length()) }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun performGlobalAction(action: Int, name: String): String {
        val service = NudgeAccessibilityService.instance ?: return "{\"error\":\"无障碍服务未运行\"}"
        return try {
            val ok = service.performGlobalAction(action)
            if (ok) "{\"success\":true,\"action\":\"$name\"}" else "{\"error\":\"${name}失败\"}"
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun sendMediaKey(keyCode: Int, name: String): String {
        return try {
            val latch = java.util.concurrent.CountDownLatch(1)
            var result = ""
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    val event = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
                    am.dispatchMediaKeyEvent(event)
                    val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
                    am.dispatchMediaKeyEvent(eventUp)
                    result = "{\"success\":true,\"action\":\"$name\"}"
                } catch (e: Exception) {
                    result = "{\"error\":\"${e.message}\"}"
                }
                latch.countDown()
            }
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            result.ifEmpty { "{\"error\":\"超时\"}" }
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun setAlarm(hour: Int, minute: Int, message: String, repeat: String, title: String, note: String): String {
        return try {
            val weekdays = if (repeat == "weekly") {
                // 默认周一至周五
                listOf(1, 2, 3, 4, 5)
            } else emptyList()
            val item = AlarmStore.add(context, "alarm", hour, minute,
                title.ifBlank { message }, note, repeat, weekdays, 0)
            "{\"success\":true,\"time\":\"${hour}:${String.format("%02d", minute)}\",\"repeat\":\"$repeat\",\"title\":\"${item.title}\"}"
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun getNotifications(count: Int): String {
        if (!NudgeNotificationService.isRunning) {
            return "{\"error\":\"通知监听服务未开启，请在系统设置→通知使用权中开启Nudge\"}"
        }
        val arr = JSONArray()
        val sorted = NudgeNotificationService.notifMap.values
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .sortedByDescending { it.optLong("time", 0L) }
            .take(count)
        for (item in sorted) arr.put(item)
        return JSONObject().apply { put("notifications", arr); put("count", arr.length()) }.toString()
    }

    private fun getLocation(): String {
        return try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return "{\"error\":\"未授权定位权限\"}"
            }
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            var best: android.location.Location? = null
            for (provider in listOf(android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.NETWORK_PROVIDER)) {
                try {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                        best = loc
                    }
                } catch (_: Exception) {}
            }
            if (best != null) {
                val json = JSONObject().apply {
                    put("latitude", best.latitude)
                    put("longitude", best.longitude)
                    put("accuracy", best.accuracy.toDouble())
                    put("provider", best.provider ?: "unknown")
                    if (best.hasAltitude()) put("altitude", String.format("%.1f", best.altitude))
                }
                try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.CHINA)
                    val addresses = geocoder.getFromLocation(best.latitude, best.longitude, 1)
                    if (addresses != null && addresses.isNotEmpty()) {
                        val addr = addresses[0]
                        val line = addr.getAddressLine(0)
                        if (!line.isNullOrEmpty()) {
                            json.put("address", line)
                        } else {
                            val parts = listOf(addr.adminArea, addr.locality, addr.subLocality, addr.thoroughfare)
                                .filter { !it.isNullOrEmpty() }
                            if (parts.isNotEmpty()) json.put("address", parts.joinToString(""))
                        }
                    }
                } catch (_: Exception) {}
                json.toString()
            } else {
                "{\"error\":\"无法获取位置，请确保GPS已开启\"}"
            }
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun wakeUp(): String {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val wl = pm.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or android.os.PowerManager.ON_AFTER_RELEASE,
                "nudge:wake"
            )
            try {
                wl.acquire(500)
            } finally {
                wl.release()
            }
            "{\"success\":true,\"action\":\"唤醒屏幕\"}"
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun readScreen(): String {
        val service = NudgeAccessibilityService.instance ?: return "{\"error\":\"无障碍服务未运行\"}"
        return try {
            service.readScreen()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun analyzeWithAI(base64: String): String {
        try {
            val prefs = context.getSharedPreferences("nudge", android.content.Context.MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "") ?: ""
            val model = prefs.getString("model", "Qwen/Qwen2.5-VL-32B-Instruct") ?: "Qwen/Qwen2.5-VL-32B-Instruct"
            val apiUrl = prefs.getString("api_url", "https://api.siliconflow.cn/v1/chat/completions") ?: "https://api.siliconflow.cn/v1/chat/completions"
            val prompt = prefs.getString("prompt", "如实描述这个手机屏幕截图的内容，语气自然口语化，简洁直接。看到啥说啥，不加多余评价。不超过100字。") ?: "如实描述"
            if (apiKey.isEmpty()) return "{\"error\":\"请先配置API Key\"}"

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val payload = JSONObject()
            payload.put("model", model)
            val messages = JSONArray()
            val msg = JSONObject()
            msg.put("role", "user")
            val msgContent = JSONArray()
            msgContent.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply {
                    put("url", "data:image/jpeg;base64,$base64")
                })
            })
            msgContent.put(JSONObject().apply {
                put("type", "text")
                put("text", prompt)
            })
            msg.put("content", msgContent)
            messages.put(msg)
            payload.put("messages", messages)
            payload.put("max_tokens", 200)

            val body = okhttp3.RequestBody.create(
                "application/json".toMediaTypeOrNull(),
                payload.toString()
            )
            val request = okhttp3.Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val respBody = response.body?.string() ?: "{}"
            val respJson = JSONObject(respBody)
            val choices = respJson.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                val message = choice.optJSONObject("message")
                val text = message?.optString("content", "") ?: ""
                return JSONObject().apply { put("description", text) }.toString()
            }
            return "{\"error\":\"AI返回异常: $respBody\"}"
        } catch (e: Exception) {
            return "{\"error\":\"AI分析失败: ${e.message}\"}"
        }
    }
}
