package com.goorder.ui.screens.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goorder.service.BluetoothPrinterService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PrinterViewModel : ViewModel() {

    @SuppressLint("StaticFieldLeak")
    private var bluetoothService: BluetoothPrinterService? = null
    private var isBound = false

    var isConnected by mutableStateOf(false)
        private set

    var connectedDeviceName by mutableStateOf<String?>(null)
        private set

    var isConnecting by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        // Pantau singleton instance jika servis sudah berjalan di latar belakang
        observeServiceInstance(BluetoothPrinterService.instance)
    }

    private fun observeServiceInstance(service: BluetoothPrinterService?) {
        service ?: return
        bluetoothService = service
        viewModelScope.launch {
            service.isConnected.collectLatest { isConnected = it }
        }
        viewModelScope.launch {
            service.connectedDeviceName.collectLatest { connectedDeviceName = it }
        }
        viewModelScope.launch {
            service.isConnecting.collectLatest { isConnecting = it }
        }
        viewModelScope.launch {
            service.errorMessage.collectLatest { errorMessage = it }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothPrinterService.LocalBinder
            val s = binder.getService()
            bluetoothService = s
            isBound = true
            observeServiceInstance(s)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }

    fun bindService(context: Context) {
        BluetoothPrinterService.startService(context)
        val intent = Intent(context, BluetoothPrinterService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (_: Exception) {}
            isBound = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        bluetoothService?.connectToDevice(device) ?: BluetoothPrinterService.instance?.connectToDevice(device)
    }

    fun autoConnect() {
        bluetoothService?.autoConnectToPrinter() ?: BluetoothPrinterService.instance?.autoConnectToPrinter()
    }

    fun disconnect() {
        bluetoothService?.disconnect() ?: BluetoothPrinterService.instance?.disconnect()
    }

    suspend fun printText(text: String): Boolean {
        val s = bluetoothService ?: BluetoothPrinterService.instance ?: return false
        return s.printText(text)
    }

    fun testPrint(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val testText = StringBuilder().apply {
                append("================================\r\n")
                append("     GOORDER UJIAN CETAK        \r\n")
                append("================================\r\n")
                append("Pencetak: ${connectedDeviceName ?: "Thermal"}\r\n")
                append("Tarikh  : ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\r\n")
                append("Status  : Sambungan Berjaya!\r\n")
                append("--------------------------------\r\n")
                append("1x Ujian Resit         RM 10.00\r\n")
                append("1x Minuman Ujian       RM  5.00\r\n")
                append("--------------------------------\r\n")
                append("Jumlah                 RM 15.00\r\n")
                append("================================\r\n")
                append("  Terima Kasih - Sedia Digunakan\r\n")
            }.toString()

            val success = printText(testText)
            if (success) {
                onResult(true, "Ujian cetakan berjaya dihantar ke pencetak.")
            } else {
                onResult(false, "Gagal mencetak. Sila pastikan pencetak disambungkan.")
            }
        }
    }
}
