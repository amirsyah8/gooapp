package com.goorder.data.remote

import android.util.Log
import com.goorder.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SocketManager {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    fun connect(
        token: String,
        onNotificationReceived: () -> Unit,
        onPrintQrReceived: (String) -> Unit
    ) {
        if (webSocket != null) return

        val baseUrl = BuildConfig.BASE_API
        val url = if (baseUrl.contains("?")) {
            "${baseUrl}&token=$token"
        } else {
            "${baseUrl}?token=$token"
        }

        Log.d("SocketManager", "Connecting to WebSocket: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SocketManager", "WebSocket Connected Successfully!")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("SocketManager", "Received WS Message: $text")
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")
                    when (type) {
                        "notification" -> onNotificationReceived()
                        "printqr" -> {
                            val payload = json.opt("payload")?.toString() ?: text
                            onPrintQrReceived(payload)
                        }
                        "user:status" -> {
                            onNotificationReceived()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SocketManager", "Error parsing WS message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SocketManager", "WebSocket Failure: ${t.message}, Response Code: ${response?.code}")
                this@SocketManager.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SocketManager", "WebSocket Closed: $code / $reason")
                this@SocketManager.webSocket = null
            }
        })
    }

    fun disconnect() {
        Log.d("SocketManager", "Disconnecting WebSocket")
        webSocket?.close(1000, "User logged out")
        webSocket = null
    }
}
