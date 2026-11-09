package com.goorder.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID

class BluetoothPrinterService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    private var bluetoothSocket: BluetoothSocket? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName = _connectedDeviceName.asStateFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothPrinterService = this@BluetoothPrinterService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        serviceScope.launch {
            _isConnecting.value = true
            _errorMessage.value = null
            
            try {
                bluetoothSocket?.close()
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                
                // Better to cancel discovery before connecting
                val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = bluetoothManager.adapter
                if (adapter?.isDiscovering == true) {
                    adapter.cancelDiscovery()
                }
                
                bluetoothSocket?.connect()
                
                _isConnected.value = true
                val name = device.name ?: device.address
                _connectedDeviceName.value = name
                
                startForeground(NOTIFICATION_ID, createNotification("Connected to $name"))
            } catch (e: IOException) {
                _isConnected.value = false
                _connectedDeviceName.value = null
                _errorMessage.value = "Connection failed: ${e.message}"
                stopForeground(STOP_FOREGROUND_REMOVE)
            } finally {
                _isConnecting.value = false
            }
        }
    }

    fun printTsplLabel(jsonData: String): Boolean {
        return try {
            val outputStream = bluetoothSocket?.outputStream ?: throw IOException("Printer not connected")
            val data = JSONObject(jsonData)
            
            // Extract data (Same logic as your Node.js)
            val variant = data.optJSONObject("variant")
            val labelName = variant?.optString("code") ?: ""
            val hashId = variant?.optString("hashids") ?: ""
            val typeName = data.optString("type") ?: ""
            val numId = variant?.optString("id") ?: ""
            
            val webUrl = "https://iconfacade.com.my"
            val url = "$webUrl/home/bulk/$hashId"

            // 203 DPI = 8 dots/mm
            val dpmm = 8
            val paperW = 49 * dpmm // 392 dots
            val qrCell = 7
            val qrW = Math.round(28.5 * dpmm).toInt() // 228 dots
            val qrX = (paperW - qrW) / 2

            val fontMul = 1
            val fontCharW = 16 * fontMul
            val fontH = 24 * fontMul
            val qrY = Math.round(4.5 * dpmm).toInt()
            val nameY = qrY + qrW + dpmm
            val hashY = nameY + fontH + dpmm
            
            val nameText = labelName
            val hashText = "$numId.$hashId"
            
            val nameTextX = Math.max(0, (paperW - nameText.length * fontCharW) / 2)
            val hashTextX = Math.max(0, (paperW - hashText.length * fontCharW) / 2)

            // Construct TSPL commands
            val tspl = StringBuilder()
            tspl.append("SIZE 49 mm,50 mm\r\n")
            tspl.append("GAP 2 mm,0 mm\r\n")
            tspl.append("DENSITY 8\r\n")
            tspl.append("DIRECTION 0\r\n")
            tspl.append("CLS\r\n")
            tspl.append("QRCODE $qrX,$qrY,L,$qrCell,A,0,\"$url\"\r\n")
            tspl.append("TEXT $nameTextX,$nameY,\"3\",0,$fontMul,$fontMul,\"$nameText\"\r\n")
            tspl.append("TEXT $hashTextX,$hashY,\"3\",0,$fontMul,$fontMul,\"$hashText\"\r\n")
            tspl.append("TEXT 360,10,\"3\",90,$fontMul,$fontMul,\"$typeName\"\r\n")
            tspl.append("PRINT 1,1\r\n")

            // Send to printer using GBK or Windows-1252 encoding (standard for TSPL)
            outputStream.write(tspl.toString().toByteArray(Charset.forName("GBK")))
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            // Mark as disconnected if writing fails
            _isConnected.value = false
            _errorMessage.value = "Printing failed: ${e.message}"
            false
        }
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
            _isConnected.value = false
            _connectedDeviceName.value = null
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Bluetooth Printer Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Printer Bluetooth")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnect()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "bluetooth_printer_channel"
    }
}
