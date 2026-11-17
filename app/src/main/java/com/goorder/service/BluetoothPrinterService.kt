package com.goorder.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.goorder.MainActivity
import com.goorder.data.local.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.UUID

class BluetoothPrinterService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val printMutex = Mutex()

    private var bluetoothSocket: BluetoothSocket? = null
    private var isRunning = false
    private var targetDeviceAddress: String? = null
    private var targetDeviceName: String? = null

    private var connectJob: Job? = null
    private var watcherJob: Job? = null
    private var reconnectJob: Job? = null

    private lateinit var sessionManager: SessionManager

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
        instance = this
        sessionManager = SessionManager(applicationContext)
        isRunning = true
        createNotificationChannel()

        val notification = createNotification("Memulakan perkhidmatan pencetak...")
        startForegroundCompat(notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("BluetoothPrinterService", "onStartCommand action: $action")

        when (action) {
            ACTION_STOP -> {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_CONNECT_DEVICE -> {
                val mac = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                if (!mac.isNullOrBlank()) {
                    val adapter = getBluetoothAdapter()
                    try {
                        val device = adapter?.getRemoteDevice(mac)
                        if (device != null) {
                            connectToDevice(device)
                        }
                    } catch (e: Exception) {
                        Log.e("BluetoothPrinterService", "Error getting device for MAC $mac", e)
                    }
                }
            }
            ACTION_START, null -> {
                startForegroundCompat(createNotification("Mencari pencetak Bluetooth..."))
                autoConnectToPrinter()
            }
            ACTION_PRINT_TEXT -> {
                val text = intent.getStringExtra(EXTRA_PRINT_TEXT)
                if (!text.isNullOrBlank()) {
                    serviceScope.launch {
                        printText(text)
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val manager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun autoConnectToPrinter() {
        if (_isConnected.value || _isConnecting.value) {
            Log.d("BluetoothPrinterService", "Abaikan autoConnect kerana isConnected=${_isConnected.value}, isConnecting=${_isConnecting.value}")
            return
        }

        connectJob?.cancel()
        connectJob = serviceScope.launch {
            _isConnecting.value = true
            val adapter = getBluetoothAdapter() ?: run {
                _isConnecting.value = false
                return@launch
            }

            @SuppressLint("MissingPermission")
            val bondedDevices = try {
                adapter.bondedDevices.toList()
            } catch (_: Exception) {
                emptyList()
            }

            if (bondedDevices.isEmpty()) {
                _isConnecting.value = false
                updateNotification("Tiada peranti Bluetooth dipadankan (Paired).")
                return@launch
            }

            val savedMac = sessionManager.getSavedPrinterMac()

            // Tapis keluar peranti yang jelas bukan pencetak (kereta, earphone, dll)
            val nonPrinterKeywords = listOf("car", "perodua", "tws", "buds", "headset", "audio", "speaker")
            val candidates = bondedDevices.filter { device ->
                val name = device.name?.lowercase() ?: ""
                nonPrinterKeywords.none { name.contains(it) }
            }.sortedByDescending { device ->
                val name = device.name?.lowercase() ?: ""
                when {
                    !savedMac.isNullOrBlank() && device.address.equals(savedMac, ignoreCase = true) -> 100
                    name.contains("an581") -> 90
                    name.contains("58") || name.contains("80") || name.contains("pos") || name.contains("print") -> 80
                    name.contains("thermal") || name.contains("receipt") -> 70
                    name.contains("c100") || name.contains("sk59") -> 60
                    else -> 10
                }
            }

            Log.d("BluetoothPrinterService", "Calon pencetak dikesan: ${candidates.map { "${it.name ?: "Unknown"}(${it.address})" }}")

            var connectedAny = false
            for (candidate in candidates) {
                if (_isConnected.value) {
                    connectedAny = true
                    break
                }
                val success = attemptConnection(candidate)
                if (success) {
                    connectedAny = true
                    break
                }
            }

            _isConnecting.value = false

            if (!connectedAny) {
                Log.w("BluetoothPrinterService", "Tiada peranti pencetak yang dapat disambungkan ketika ini.")
                updateNotification("⚪ Pencetak belum bersambung. Menyambung semula...")
                scheduleReconnect()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        connectJob?.cancel()
        connectJob = serviceScope.launch {
            val success = attemptConnection(device)
            if (!success) {
                val name = device.name ?: device.address
                _isConnected.value = false
                _connectedDeviceName.value = null
                _errorMessage.value = "Gagal menyambung ke $name"
                updateNotification("⚪ Gagal menyambung ke $name")
                scheduleReconnect()
            }
        }
    }

    private suspend fun attemptConnection(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        _isConnecting.value = true
        _errorMessage.value = null
        val name = device.name ?: device.address
        targetDeviceAddress = device.address
        targetDeviceName = name

        updateNotification("Menyambung ke $name...")
        Log.d("BluetoothPrinterService", "Memulakan sambungan ke $name (${device.address})...")

        // Batalkan watcher lama terlebih dahulu untuk elak perlumbaan status terputus (race condition)
        watcherJob?.cancel()
        watcherJob = null

        val adapter = getBluetoothAdapter()
        try {
            if (adapter?.isDiscovering == true) {
                adapter.cancelDiscovery()
                delay(300)
            }
        } catch (_: Exception) {}

        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {}
        bluetoothSocket = null

        var socket: BluetoothSocket? = null
        var connected = false

        // Percubaan 1: Insecure SPP UUID (Paling serasi untuk printer thermal POS pada Android)
        try {
            Log.d("BluetoothPrinterService", "Percubaan 1: Insecure SPP RFCOMM ke $name...")
            socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            connected = true
        } catch (e1: Exception) {
            Log.w("BluetoothPrinterService", "Insecure SPP gagal (${e1.message}), mencuba Standard SPP...")
            try { socket?.close() } catch (_: Exception) {}
            socket = null
        }

        // Percubaan 2: Standard SPP UUID
        if (!connected) {
            try {
                Log.d("BluetoothPrinterService", "Percubaan 2: Standard SPP RFCOMM ke $name...")
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                connected = true
            } catch (e2: Exception) {
                Log.w("BluetoothPrinterService", "Standard SPP gagal (${e2.message}), mencuba Insecure Reflection Channel 1...")
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }

        // Percubaan 3: Insecure Reflection ke Channel 1 (Standard printer terma tanpa PIN)
        if (!connected) {
            try {
                Log.d("BluetoothPrinterService", "Percubaan 3: Insecure Reflection createInsecureRfcommSocket(1) ke $name...")
                val m = device.javaClass.getMethod("createInsecureRfcommSocket", Int::class.javaPrimitiveType)
                socket = m.invoke(device, 1) as BluetoothSocket
                socket.connect()
                connected = true
            } catch (e3: Exception) {
                Log.w("BluetoothPrinterService", "Insecure Reflection Channel 1 gagal (${e3.message}), mencuba Reflection Channel 1...")
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }

        // Percubaan 4: Java Reflection createRfcommSocket ke Channel 1
        if (!connected) {
            try {
                Log.d("BluetoothPrinterService", "Percubaan 4: Reflection createRfcommSocket(1) ke $name...")
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                socket = m.invoke(device, 1) as BluetoothSocket
                socket.connect()
                connected = true
            } catch (e4: Exception) {
                Log.e("BluetoothPrinterService", "Semua percubaan sambung ke $name gagal: ${e4.message}")
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }

        if (connected && socket != null) {
            bluetoothSocket = socket
            _isConnected.value = true
            _connectedDeviceName.value = name

            // Batalkan reconnect job kerana sambungan telah berjaya
            reconnectJob?.cancel()
            reconnectJob = null

            // Simpan maklumat pencetak berjaya ke SessionManager
            sessionManager.savePrinter(device.address, name)

            updateNotification("🟢 Bersambung ke $name (Sedia mencetak)")
            Log.i("BluetoothPrinterService", "⚡ BERJAYA BERSAMBUNG ke pencetak Bluetooth: $name! Lampu LED pencetak kini solid.")

            // Mula pantau sambungan socket untuk auto-reconnect bila terputus
            startConnectionWatcher(socket, device)
            true
        } else {
            false
        }
    }

    private fun startConnectionWatcher(socket: BluetoothSocket, device: BluetoothDevice) {
        watcherJob?.cancel()
        watcherJob = serviceScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(256)
            try {
                val stream = socket.inputStream
                while (_isConnected.value && socket.isConnected) {
                    // read() akan menyekat (block) sehingga ada data atau socket terputus
                    val read = stream.read(buffer)
                    if (read == -1) break
                }
            } catch (e: Exception) {
                Log.d("BluetoothPrinterService", "Socket input stream terputus: ${e.message}")
            } finally {
                if (_isConnected.value) {
                    val name = device.name ?: device.address
                    Log.w("BluetoothPrinterService", "Sambungan dengan $name terputus secara tidak dijangka.")
                    _isConnected.value = false
                    _connectedDeviceName.value = null
                    try { socket.close() } catch (_: Exception) {}
                    bluetoothSocket = null

                    updateNotification("⚪ Terputus dari $name. Menyambung semula...")
                    scheduleReconnect()
                }
            }
        }
    }

    private fun scheduleReconnect() {
        if (!isRunning) return
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            delay(5000)
            if (!_isConnected.value && isRunning) {
                Log.d("BluetoothPrinterService", "Mencuba auto-reconnect ke pencetak...")
                autoConnectToPrinter()
            }
        }
    }

    suspend fun printText(text: String): Boolean = withContext(Dispatchers.IO) {
        printMutex.withLock {
            val socket = bluetoothSocket
            if (socket == null || !_isConnected.value) {
                Log.w("BluetoothPrinterService", "Pencetak belum bersambung semasa print dipanggil. Mencuba menyambung...")
                autoConnectToPrinter()
                return@withLock false
            }

            try {
                val outputStream = socket.outputStream
                val trimmed = text.trim()
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    printTsplLabelInternal(outputStream, trimmed)
                } else {
                    Log.d("BluetoothPrinterService", "Menghantar pesanan ESC/POS (${text.length} aksara)...")
                    // 1. Inisialisasi Pencetak: ESC @
                    outputStream.write(byteArrayOf(0x1B, 0x40))

                    // 2. Tetapkan Standard Code Page (PC437 / Lalai): ESC t 0
                    outputStream.write(byteArrayOf(0x1B, 0x74, 0x00))

                    // 3. Pastikan format setiap baris berakhir dengan \r\n (CR LF) yang standard untuk thermal printer
                    val normalizedText = text.lines().joinToString("\r\n")
                    outputStream.write(normalizedText.toByteArray(Charset.forName("GBK")))

                    // 4. Feed kertas 4 baris supaya boleh dicarik dengan mudah (ESC d 4 dan \r\n)
                    outputStream.write("\r\n\r\n".toByteArray(Charset.forName("GBK")))
                    outputStream.write(byteArrayOf(0x1B, 0x64, 0x04))

                    // Nota: JANGAN hantar 'GS V 66 0' kerana printer 58mm (seperti AN581) tiada pemotong automatik (auto-cutter).
                    // Perintah pemotong pada printer tanpa pemotong boleh menyebabkan buffer tersekat (hang).

                    outputStream.flush()
                    Log.i("BluetoothPrinterService", "Berjaya mencetak data ke pencetak Bluetooth!")
                    true
                }
            } catch (e: Exception) {
                Log.e("BluetoothPrinterService", "Ralat semasa mencetak: ${e.message}", e)
                _isConnected.value = false
                _errorMessage.value = "Ralat cetakan: ${e.message}"
                scheduleReconnect()
                false
            }
        }
    }

    fun printTsplLabel(jsonData: String): Boolean {
        return runBlocking {
            printText(jsonData)
        }
    }

    private fun printTsplLabelInternal(outputStream: OutputStream, jsonData: String): Boolean {
        return try {
            val data = JSONObject(jsonData)
            
            val variant = data.optJSONObject("variant")
            val labelName = variant?.optString("code") ?: ""
            val hashId = variant?.optString("hashids") ?: ""
            val typeName = data.optString("type") ?: ""
            val numId = variant?.optString("id") ?: ""
            
            val webUrl = "https://iconfacade.com.my"
            val url = "$webUrl/home/bulk/$hashId"

            val dpmm = 8
            val paperW = 49 * dpmm
            val qrCell = 7
            val qrW = Math.round(28.5 * dpmm).toInt()
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

            outputStream.write(tspl.toString().toByteArray(Charset.forName("GBK")))
            outputStream.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun disconnect() {
        try {
            watcherJob?.cancel()
            reconnectJob?.cancel()
            connectJob?.cancel()
            bluetoothSocket?.close()
        } catch (_: Exception) {}
        bluetoothSocket = null
        _isConnected.value = false
        _connectedDeviceName.value = null
        updateNotification("Pencetak diputuskan sambungan.")
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GooOrder Pencetak Bluetooth",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Perkhidmatan latar belakang untuk menyambung dan mencetak pesanan secara berterusan"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GooOrder Pencetak")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        disconnect()
        serviceScope.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "bluetooth_printer_channel"

        const val ACTION_START = "com.goorder.service.ACTION_START"
        const val ACTION_STOP = "com.goorder.service.ACTION_STOP"
        const val ACTION_CONNECT_DEVICE = "com.goorder.service.ACTION_CONNECT_DEVICE"
        const val ACTION_PRINT_TEXT = "com.goorder.service.ACTION_PRINT_TEXT"
        const val EXTRA_DEVICE_ADDRESS = "device_address"
        const val EXTRA_PRINT_TEXT = "extra_print_text"

        @Volatile
        var instance: BluetoothPrinterService? = null
            private set

        fun print(context: Context, text: String) {
            val s = instance
            if (s != null && s.isConnected.value) {
                s.serviceScope.launch {
                    s.printText(text)
                }
            } else {
                val intent = Intent(context, BluetoothPrinterService::class.java).apply {
                    action = ACTION_PRINT_TEXT
                    putExtra(EXTRA_PRINT_TEXT, text)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, BluetoothPrinterService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BluetoothPrinterService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun connectDevice(context: Context, macAddress: String) {
            val intent = Intent(context, BluetoothPrinterService::class.java).apply {
                action = ACTION_CONNECT_DEVICE
                putExtra(EXTRA_DEVICE_ADDRESS, macAddress)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
