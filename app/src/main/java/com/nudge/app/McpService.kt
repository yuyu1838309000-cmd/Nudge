package com.nudge.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.*

class McpService : Service() {

    private lateinit var server: HttpServer

    override fun onCreate() {
        super.onCreate()
        server = HttpServer(8809)
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

class HttpServer(private val port: Int) {

    private var running = false
    private lateinit var serverSocket: java.net.ServerSocket

    fun start() {
        running = true
        Thread {
            try {
                serverSocket = java.net.ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1"))
                Log.i("Nudge", "MCP HTTP Server started on 127.0.0.1:$port")
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
                if (line.startsWith("Content-Length:")) {
                    contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            var body = ""
            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                input.read(buf, 0, contentLength)
                body = String(buf)
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
        } catch (_: Exception) {}
    }

    private fun route(method: String, path: String, body: String): Pair<String, String> {
        return when {
            path == "/ping" -> "200 OK" to "{\"result\":\"pong\"}"
            path == "/tools/list" -> "200 OK" to "{\"tools\":[{\"name\":\"ping\",\"description\":\"test\"}]}"
            else -> "200 OK" to "{\"result\":\"Nudge MCP v0.1.0\"}"
        }
    }
}
