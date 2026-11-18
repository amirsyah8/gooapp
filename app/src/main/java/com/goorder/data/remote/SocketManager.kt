package com.goorder.data.remote

import android.util.Log
import com.goorder.BuildConfig
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

class SocketManager {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var isConnected: Boolean = false
        private set

    // ── Auto-Reconnect State ──
    private var shouldReconnect = false
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null

    // Simpan parameter untuk reconnect
    private var savedToken: String? = null
    private var savedOnNotification: (() -> Unit)? = null
    private var savedOnPrintQr: ((String) -> Unit)? = null
    private var savedOnOrderUpdated: (() -> Unit)? = null
    private var savedOnConnectionChange: ((Boolean) -> Unit)? = null

    fun connect(
        token: String,
        onNotificationReceived: () -> Unit = {},
        onPrintQrReceived: (String) -> Unit = {},
        onOrderUpdated: () -> Unit = {},
        onConnectionChange: (Boolean) -> Unit = {}
    ) {
        if (webSocket != null) return

        // Simpan callbacks untuk auto-reconnect
        shouldReconnect = true
        reconnectAttempt = 0
        savedToken = token
        savedOnNotification = onNotificationReceived
        savedOnPrintQr = onPrintQrReceived
        savedOnOrderUpdated = onOrderUpdated
        savedOnConnectionChange = onConnectionChange

        connectInternal(token, onNotificationReceived, onPrintQrReceived, onOrderUpdated, onConnectionChange)
    }

    private fun connectInternal(
        token: String,
        onNotificationReceived: () -> Unit,
        onPrintQrReceived: (String) -> Unit,
        onOrderUpdated: () -> Unit,
        onConnectionChange: (Boolean) -> Unit
    ) {
        // Pastikan tiada socket lama yang masih aktif
        webSocket?.close(1000, "Reconnecting")
        webSocket = null

        val baseUrl = BuildConfig.BASE_API
        val url = if (baseUrl.contains("?")) {
            "${baseUrl}&token=$token"
        } else {
            "${baseUrl}?token=$token"
        }

        Log.d("SocketManager", "Connecting to WebSocket: $url (attempt ${reconnectAttempt + 1})")

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SocketManager", "✅ WebSocket Connected Successfully!")
                isConnected = true
                reconnectAttempt = 0  // Reset counter bila berjaya sambung
                onConnectionChange(true)
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
                        "ORDER_UPDATED" -> {
                            Log.d("SocketManager", "⚡ Pesanan dikemaskini dari WebSocket")
                            onOrderUpdated()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SocketManager", "Error parsing WS message", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SocketManager", "WebSocket Failure: ${t.message}, Response Code: ${response?.code}")
                this@SocketManager.webSocket = null
                isConnected = false
                onConnectionChange(false)
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SocketManager", "WebSocket Closed: $code / $reason")
                this@SocketManager.webSocket = null
                isConnected = false
                onConnectionChange(false)
                // Hanya reconnect jika bukan penutupan sengaja (code 1000 = normal close)
                if (code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }

    /**
     * Auto-reconnect dengan Exponential Backoff.
     * Delay: 5s → 10s → 20s → 40s → 60s (max)
     */
    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        val token = savedToken ?: return
        val onNotification = savedOnNotification ?: return
        val onPrintQr = savedOnPrintQr ?: return
        val onOrderUpdated = savedOnOrderUpdated ?: return
        val onConnectionChange = savedOnConnectionChange ?: return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = min(5000L * (1L shl min(reconnectAttempt, 4)), 60_000L)
            reconnectAttempt++
            Log.d("SocketManager", "🔄 WebSocket reconnect dalam ${delayMs / 1000}s (percubaan #$reconnectAttempt)...")
            delay(delayMs)

            if (shouldReconnect && !isConnected) {
                connectInternal(token, onNotification, onPrintQr, onOrderUpdated, onConnectionChange)
            }
        }
    }

    fun disconnect() {
        Log.d("SocketManager", "Disconnecting WebSocket")
        shouldReconnect = false
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "User logged out")
        webSocket = null
        isConnected = false

        // Bersihkan saved callbacks
        savedToken = null
        savedOnNotification = null
        savedOnPrintQr = null
        savedOnOrderUpdated = null
        savedOnConnectionChange = null
    }
}
