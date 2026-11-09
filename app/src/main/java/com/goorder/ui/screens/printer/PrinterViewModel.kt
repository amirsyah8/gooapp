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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothPrinterService.LocalBinder
            bluetoothService = binder.getService()
            isBound = true
            
            // Observe states from service
            viewModelScope.launch {
                bluetoothService?.isConnected?.collectLatest { isConnected = it }
            }
            viewModelScope.launch {
                bluetoothService?.connectedDeviceName?.collectLatest { connectedDeviceName = it }
            }
            viewModelScope.launch {
                bluetoothService?.isConnecting?.collectLatest { isConnecting = it }
            }
            viewModelScope.launch {
                bluetoothService?.errorMessage?.collectLatest { errorMessage = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothService = null
            isBound = false
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, BluetoothPrinterService::class.java)
        context.startService(intent) // Start it first to keep it alive
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        bluetoothService?.connectToDevice(device)
    }

    fun disconnect() {
        bluetoothService?.disconnect()
    }

    suspend fun printText(text: String): Boolean {
        return withContext(Dispatchers.IO) {
            bluetoothService?.printTsplLabel(text) ?: false
        }
    }
}
