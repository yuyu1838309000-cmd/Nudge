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
                JSONObject().apply { put("tools", tools) }
            }
            "tools/call" -> {
                val toolName = params.optString("name", "")
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
