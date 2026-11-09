package com.goorder.data.remote

import android.util.Log
import com.goorder.BuildConfig
import io.socket.client.IO
import io.socket.client.Socket
import java.net.URISyntaxException

class SocketManager {
    private var mSocket: Socket? = null

    fun connect(
        onNotificationReceived: () -> Unit,
        onPrintQrReceived: (String) -> Unit
    ) {
        if (mSocket?.connected() == true) return

        try {
            val opts = IO.Options().apply {
                forceNew = true
                reconnection = true
            }
            
            mSocket = IO.socket(BuildConfig.BASE_API, opts)
            
            mSocket?.on(Socket.EVENT_CONNECT) {
                Log.d("SocketManager", "Socket Connected")
            }

            mSocket?.on(Socket.EVENT_DISCONNECT) {
                Log.d("SocketManager", "Socket Disconnected")
            }

            mSocket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e("SocketManager", "Socket Connect Error: ${args.getOrNull(0)}")
            }

            mSocket?.on("notification") {
                Log.d("SocketManager", "Notification received")
                onNotificationReceived()
            }

            mSocket?.on("printqr") { args ->
                val data = args.getOrNull(0)?.toString() ?: ""
                Log.d("SocketManager", "Print QR received: $data")
                onPrintQrReceived(data)
            }

            mSocket?.connect()
        } catch (e: URISyntaxException) {
            Log.e("SocketManager", "Socket URL error", e)
        }
    }

    fun join(userId: Int?) {
        userId?.let {
            Log.d("SocketManager", "Emitting join for user: $it")
            mSocket?.emit("join", it)
        }
    }

    fun disconnect() {
        Log.d("SocketManager", "Disconnecting socket")
        mSocket?.disconnect()
        mSocket?.off()
        mSocket = null
    }
}
