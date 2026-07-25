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
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val beginTime = endTime - 60000
            val events = usageStatsManager.queryEvents(beginTime, endTime)
            var lastPkg = ""
            var lastTime = 0L
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (event.timeStamp > lastTime) {
                        lastTime = event.timeStamp
                        lastPkg = event.packageName
                    }
                }
            }
            if (lastPkg.isEmpty()) {
                return "最近没有检测到前台切换事件，请切换一下应用后再试"
            }
            val pm = context.packageManager
            val appName = try {
                pm.getApplicationLabel(pm.getApplicationInfo(lastPkg, 0)).toString()
            } catch (_: Exception) {
                lastPkg
            }
            "{\"package\":\"$lastPkg\",\"app_name\":\"$appName\",\"last_foreground_time\":$lastTime}"
        } catch (e: Exception) {
            "{\"error\":\"${e.message}\"}"
        }
    }
}
