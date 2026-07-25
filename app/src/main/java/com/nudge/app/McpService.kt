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
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            var contentLength = 0
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:") || line.startsWith("content-length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            var body = ""
            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var total = 0
                while (total < contentLength) {
                    val n = input.read(buf, total, contentLength - total)
                    if (n <= 0) break
                    total += n
                }
                body = String(buf, 0, total)
            } else if (method.uppercase() == "POST") {
                val sb = StringBuilder()
                while (input.ready()) {
                    val c = input.read()
                    if (c == -1) break
                    sb.append(c.toChar())
                }
                body = sb.toString()
            }

            val (code, resp) = route(method, path, body)
            val respBytes = resp.toByteArray(Charsets.UTF_8)
            output.write("HTTP/1.1 $code\r\n")
            output.write("Content-Type: application/json; charset=utf-8\r\n")
            output.write("Content-Length: ${respBytes.size}\r\n")
            output.write("Connection: close\r\n")
            output.write("\r\n")
            output.write(resp)
            output.flush()
            socket.close()
        } catch (e: Exception) {
            Log.e("Nudge", "handle error: ${e.message}", e)
        }
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

        return "200 OK" to "{\"result\":\"Nudge MCP v0.1.0\"}"
    }

    private fun handleJsonRpc(method: String, params: JSONObject): JSONObject {
        return when (method) {
            "initialize" -> {
                val caps = JSONObject()
                caps.put("tools", JSONObject())
                val serverInfo = JSONObject()
                serverInfo.put("name", "Nudge")
                serverInfo.put("version", "0.1.0")
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
                    put("description", "测试连通性，返回pong")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_foreground_app")
                    put("description", "获取当前前台应用的包名和应用名称")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "screenshot_analyze")
                    put("description", "截取当前屏幕并用AI分析内容，返回文字描述")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "sensor_data")
                    put("description", "获取手机传感器实时数据（加速度、光线、陀螺仪等）")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "device_status")
                    put("description", "获取设备状态：锁屏状态、电量、充电状态、网络类型")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_location")
                    put("description", "获取当前GPS定位（经纬度）")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_notifications")
                    put("description", "获取最近收到的通知列表（需开启通知监听权限）")
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
                    put("description", "获取今日步数")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "calendar_query")
                    put("description", "查询日历事件（需授权日历权限）")
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
                    put("description", "设置系统闹钟")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("hour", JSONObject().apply { put("type", "integer"); put("description", "小时（0-23）") })
                            put("minute", JSONObject().apply { put("type", "integer"); put("description", "分钟（0-59）") })
                            put("message", JSONObject().apply { put("type", "string"); put("description", "闹钟备注（可选）") })
                        })
                        put("required", JSONArray().put("hour").put("minute"))
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "lock_screen")
                    put("description", "锁屏")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "media_play_pause")
                    put("description", "媒体播放/暂停")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "media_next")
                    put("description", "媒体下一首")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "media_previous")
                    put("description", "媒体上一首")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "press_back")
                    put("description", "返回键")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "press_home")
                    put("description", "回桌面")
                    put("inputSchema", JSONObject().apply { put("type", "object"); put("properties", JSONObject()) })
                })
                tools.put(JSONObject().apply {
                    put("name", "open_app")
                    put("description", "打开指定应用")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("package", JSONObject().apply { put("type", "string"); put("description", "应用包名") })
                        })
                        put("required", JSONArray().put("package"))
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "tap")
                    put("description", "点击屏幕指定坐标")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("x", JSONObject().apply { put("type", "number"); put("description", "X坐标") })
                            put("y", JSONObject().apply { put("type", "number"); put("description", "Y坐标") })
                        })
                        put("required", JSONArray().put("x").put("y"))
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "swipe")
                    put("description", "滑动屏幕")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject().apply {
                            put("x1", JSONObject().apply { put("type", "number") })
                            put("y1", JSONObject().apply { put("type", "number") })
                            put("x2", JSONObject().apply { put("type", "number") })
                            put("y2", JSONObject().apply { put("type", "number") })
                        })
                        put("required", JSONArray().put("x1").put("y1").put("x2").put("y2"))
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "wake_up")
                    put("description", "亮屏唤醒（不解锁，仅点亮屏幕）")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "read_screen")
                    put("description", "读取当前界面所有可见文字，返回结构化列表")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_heart_rate")
                    put("description", "从Health Connect获取心率数据（需小米运动/Zepp Life同步）")
                    put("inputSchema", JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    })
                })
                tools.put(JSONObject().apply {
                    put("name", "get_sleep")
                    put("description", "从Health Connect获取睡眠数据（需小米运动/Zepp Life同步）")
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
                        val info = setAlarm(hour, minute, message)
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "lock_screen" -> {
                        val info = performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "锁屏")
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
                        } else if (svc.launchAppDirectly(pkg)) {
                            content.put(JSONObject().apply { put("type", "text"); put("text", "{\"success\":true,\"package\":\"$pkg\",\"method\":\"direct\"}") })
                        } else {
                            // Fallback: find on desktop
                            val appName = try {
                                val ai = context.packageManager.getApplicationInfo(pkg, 0)
                                context.packageManager.getApplicationLabel(ai).toString()
                            } catch (_: Exception) { "" }
                            if (appName.isEmpty()) {
                                content.put(JSONObject().apply { put("type", "text"); put("text", "{\"error\":\"未找到: $pkg\"}") })
                            } else {
                                val ok = svc.findAndClickApp(appName)
                                val info = if (ok) "{\"success\":true,\"package\":\"$pkg\",\"app_name\":\"$appName\",\"method\":\"desktop\"}"
                                          else "{\"error\":\"无法打开: $appName\"}"
                                content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                            }
                        }
                    }
                    "tap" -> {
                        val x = args.optDouble("x", 0.0).toFloat()
                        val y = args.optDouble("y", 0.0).toFloat()
                        val info = doTap(x, y)
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "swipe" -> {
                        val x1 = args.optDouble("x1", 0.0).toFloat()
                        val y1 = args.optDouble("y1", 0.0).toFloat()
                        val x2 = args.optDouble("x2", 0.0).toFloat()
                        val y2 = args.optDouble("y2", 0.0).toFloat()
                        val info = doSwipe(x1, y1, x2, y2)
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "wake_up" -> {
                        val info = wakeUp()
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "read_screen" -> {
                        val info = readScreen()
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "get_heart_rate" -> {
                        val info = getHeartRate()
                        content.put(JSONObject().apply { put("type", "text"); put("text", info) })
                    }
                    "get_sleep" -> {
                        val info = getSleepData()
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
        return "{\"package\":\"$pkg\",\"app_name\":\"$name\"}"
    }

    private fun screenshotAndAnalyze(): String {
        val latch = java.util.concurrent.CountDownLatch(1)
        var result = ""
        try {
            // 需要从主线程获取NudgeAccessibilityService实例
            val service = NudgeAccessibilityService.instance ?: return "{\"error\":\"无障碍服务未运行\"}"
            service.takeScreenshotAndAnalyze { base64 ->
                if (base64.startsWith("{\"error\"")) {
                    result = base64
                } else {
                    result = analyzeWithAI(base64)
                }
                latch.countDown()
            }
            latch.await(15, java.util.concurrent.TimeUnit.SECONDS)
            return result.ifEmpty { "{\"error\":\"超时\"}" }
        } catch (e: Exception) {
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

            JSONObject().apply {
                put("screen_locked", locked)
                put("battery", battery)
                put("charging", charging)
                put("network", network)
            }.toString()
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun getSteps(): String {
        val s = NudgeAccessibilityService.steps
        return if (s > 0) "{\"steps\":$s}" else "{\"steps\":0,\"note\":\"传感器未激活，请走几步后再试\"}"
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

    private fun doTap(x: Float, y: Float): String {
        val service = NudgeAccessibilityService.instance ?: return "{\"error\":\"无障碍服务未运行\"}"
        val latch = java.util.concurrent.CountDownLatch(1)
        var result = false
        service.doTap(x, y) { ok -> result = ok; latch.countDown() }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return if (result) "{\"success\":true,\"x\":$x,\"y\":$y}" else "{\"error\":\"点击失败\"}"
    }

    private fun doSwipe(x1: Float, y1: Float, x2: Float, y2: Float): String {
        val service = NudgeAccessibilityService.instance ?: return "{\"error\":\"无障碍服务未运行\"}"
        val latch = java.util.concurrent.CountDownLatch(1)
        var result = false
        service.doSwipe(x1, y1, x2, y2, 300) { ok -> result = ok; latch.countDown() }
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
        return if (result) "{\"success\":true}" else "{\"error\":\"滑动失败\"}"
    }

    private fun openApp(pkg: String): String {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent == null) return "{\"error\":\"未找到应用: $pkg\"}"
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "{\"success\":true,\"package\":\"$pkg\"}"
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun setAlarm(hour: Int, minute: Int, message: String): String {
        return try {
            val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
            cal.set(java.util.Calendar.MINUTE, minute)
            cal.set(java.util.Calendar.SECOND, 0)
            if (cal.timeInMillis <= System.currentTimeMillis()) {
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            val intent = android.content.Intent(context, McpService::class.java)
            val pi = android.app.PendingIntent.getBroadcast(context, 0, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
            val info = android.app.AlarmManager.AlarmClockInfo(cal.timeInMillis, pi)
            am.setAlarmClock(info, pi)
            "{\"success\":true,\"time\":\"${hour}:${String.format("%02d", minute)}\",\"message\":\"${message}\"}"
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }

    private fun getNotifications(count: Int): String {
        if (!NudgeNotificationService.isRunning) {
            return "{\"error\":\"通知监听服务未开启，请在系统设置→通知使用权中开启Nudge\"}"
        }
        val list = NudgeNotificationService.lastNotifications.take(count)
        val arr = JSONArray()
        for (item in list) {
            try { arr.put(JSONObject(item)) } catch (_: Exception) {}
        }
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
                JSONObject().apply {
                    put("latitude", best.latitude)
                    put("longitude", best.longitude)
                    put("accuracy", best.accuracy.toDouble())
                    put("provider", best.provider ?: "unknown")
                }.toString()
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
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "nudge:wake"
            )
            wl.acquire(500)
            wl.release()
            "{\"success\":true,\"action\":\"唤醒屏幕\"}"
        } catch (e: Exception) {
            "{\"error\":\"\${e.message}\"}"
        }
    }

    private fun readScreen(): String {
        val service = NudgeAccessibilityService.instance ?: return "{\"error\":\"无障碍服务未运行\"}"
        return try {
            service.readScreen()
        } catch (e: Exception) {
            "{\"error\":\"\${e.message}\"}"
        }
    }

    private fun getHeartRate(): String {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return "{\"error\":\"Health Connect 需要 Android 14+\"}"
        }
        return try {
            val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
            val now = java.time.Instant.now()
            val start = now.minus(java.time.Duration.ofDays(1))
            val response = kotlinx.coroutines.runBlocking {
                client.readRecords(
                    androidx.health.connect.client.request.ReadRecordsRequest(
                        recordType = androidx.health.connect.client.records.HeartRateRecord::class,
                    timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(start, now),
                    pageSize = 200
                )
            )
            }
            val records = response.records
            if (records.isEmpty()) return "{\"error\":\"未找到心率数据，请确认小米运动已同步\"}"
            
            val samples = org.json.JSONArray()
            var min = Long.MAX_VALUE; var max = 0L; var sum = 0L
            for (r in records) {
                for (s in r.samples) {
                    val bpm = s.beatsPerMinute
                    samples.put(org.json.JSONObject().apply {
                        put("bpm", bpm)
                        put("time", s.time.toString())
                    })
                    if (bpm < min) min = bpm
                    if (bpm > max) max = bpm.toLong()
                    sum += bpm
                }
            }
            org.json.JSONObject().apply {
                put("avg", if (samples.length() > 0) sum / samples.length() else 0)
                put("min", if (min != Long.MAX_VALUE) min else 0L)
                put("max", max)
                put("count", samples.length())
                put("latest", if (samples.length() > 0) samples.getJSONObject(samples.length() - 1).optInt("bpm") else 0)
                put("samples", samples)
            }.toString()
        } catch (e: Exception) {
            if (e is SecurityException || e.message?.contains("permission") == true) {
                "{\"error\":\"未授权Health Connect。请在 设置→隐私→运动健康 中授权Nudge\"}"
            } else {
                "{\"error\":\"Health Connect不可用(OS3限制): \${e.message}\"}"
            }
        }
    }

    private fun getSleepData(): String {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return "{\"error\":\"Health Connect 需要 Android 14+\"}"
        }
        return try {
            val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
            val now = java.time.Instant.now()
            val start = now.minus(java.time.Duration.ofDays(2))
            val response = kotlinx.coroutines.runBlocking {
                client.readRecords(
                    androidx.health.connect.client.request.ReadRecordsRequest(
                        recordType = androidx.health.connect.client.records.SleepSessionRecord::class,
                    timeRangeFilter = androidx.health.connect.client.time.TimeRangeFilter.between(start, now),
                    pageSize = 10
                )
            )
            }
            val records = response.records
            if (records.isEmpty()) return "{\"error\":\"未找到睡眠数据，请确认小米运动已同步\"}"
            
            val sessions = org.json.JSONArray()
            for (r in records) {
                val dur = java.time.Duration.between(r.startTime, r.endTime)
                sessions.put(org.json.JSONObject().apply {
                    put("start", r.startTime.toString())
                    put("end", r.endTime.toString())
                    put("duration_min", dur.toMinutes())
                    put("duration_hours", String.format("%.1f", dur.toMinutes() / 60.0))
                    if (r.endZoneOffset != null) put("timezone", r.endZoneOffset.toString())
                })
            }
            org.json.JSONObject().apply {
                put("sessions", sessions)
                put("count", sessions.length())
                if (sessions.length() > 0) {
                    val latest = sessions.getJSONObject(sessions.length() - 1)
                    put("latest_start", latest.optString("start"))
                    put("latest_end", latest.optString("end"))
                    put("latest_duration_hours", latest.optString("duration_hours"))
                }
            }.toString()
        } catch (e: Exception) {
            if (e is SecurityException || e.message?.contains("permission") == true) {
                "{\"error\":\"未授权Health Connect。请在 设置→隐私→运动健康 中授权Nudge\"}"
            } else {
                "{\"error\":\"Health Connect不可用(OS3限制): \${e.message}\"}"
            }
        }
    }

    private fun analyzeWithAI(base64: String): String {
        try {
            val prefs = context.getSharedPreferences("nudge", android.content.Context.MODE_PRIVATE)
            val apiKey = prefs.getString("api_key", "") ?: ""
            val model = prefs.getString("model", "Qwen/Qwen3.6-35B-A3B") ?: "Qwen/Qwen3.6-35B-A3B"
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
                return "{\"description\":\"${text.replace("\"", "\\\"").replace("\n", " ")}\"}"
            }
            return "{\"error\":\"AI返回异常: $respBody\"}"
        } catch (e: Exception) {
            return "{\"error\":\"AI分析失败: ${e.message}\"}"
        }
    }
}
