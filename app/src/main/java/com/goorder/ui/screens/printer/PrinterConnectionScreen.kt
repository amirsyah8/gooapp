package com.goorder.ui.screens.printer

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.util.Log
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goorder.ui.theme.SuccessGreen

@Composable
fun PrinterConnectionScreen(
    viewModel: PrinterViewModel = viewModel()
) {
    val context = LocalContext.current
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }

    // Bind to service
    DisposableEffect(Unit) {
        viewModel.bindService(context)
        onDispose {
            viewModel.unbindService(context)
        }
    }
    
    // Launcher untuk kebenaran (Permission)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.BLUETOOTH_CONNECT] == true) {
            pairedDevices = getPairedBluetoothDevices(context)
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = if (viewModel.isConnected) SuccessGreen else MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (viewModel.isConnected) "Tersambung ke ${viewModel.connectedDeviceName}" else "Sambung ke Printer Bluetooth",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sila pastikan printer thermal anda telah dihidupkan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        if (viewModel.isConnected) {
            Button(
                onClick = { viewModel.disconnect() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Putuskan Sambungan")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                val permissions = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                } else {
                    permissions.add(Manifest.permission.BLUETOOTH)
                    permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                
                val allGranted = permissions.all { 
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
                }
                
                if (allGranted) {
                    pairedDevices = getPairedBluetoothDevices(context)
                } else {
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Imbas Printer Paired")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (pairedDevices.isNotEmpty()) {
            Text(
                text = "Senarai Peranti Paired:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pairedDevices) { device ->
                    val isSelected = viewModel.connectedDeviceName == (device.name ?: device.address)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                if (!viewModel.isConnecting) {
                                    viewModel.connectToDevice(device)
                                }
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected && viewModel.isConnected) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = device.name ?: "Tiada Nama", fontWeight = FontWeight.Bold)
                                Text(text = device.address, style = MaterialTheme.typography.bodySmall)
                            }
                            if (viewModel.isConnecting && viewModel.connectedDeviceName == (device.name ?: device.address)) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else if (isSelected && viewModel.isConnected) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = "Connected",
                                    tint = SuccessGreen
                                )
                            }
                        }
                    }
                }
            }
        }
        
        if (viewModel.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = viewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Mendapatkan senarai peranti Bluetooth yang telah dipasangkan (Paired Devices).
 */
private fun getPairedBluetoothDevices(context: Context): List<BluetoothDevice> {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter = bluetoothManager.adapter

    if (bluetoothAdapter == null) return emptyList()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
    }
    
    return try {
        val devices = bluetoothAdapter.bondedDevices.toList()
        Log.d("Bluetooth", "Jumlah peranti paired: ${devices.size}")
        devices.forEach { device ->
            Log.d("Bluetooth", "Peranti: ${device.name ?: "Tiada Nama"}, Alamat: ${device.address}")
        }
        devices
    } catch (e: Exception) {
        Log.e("Bluetooth", "Ralat mendapatkan peranti: ${e.message}")
        emptyList()
    }
}
