package com.goorder.ui.screens.printer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goorder.data.remote.dto.OrderSessionDto
import com.goorder.data.remote.dto.OutletDto
import com.goorder.data.remote.dto.toPrintText
import com.goorder.domain.model.User
import com.goorder.ui.screens.dashboard.DashboardScreen
import com.goorder.ui.screens.login.AuthViewModel
import com.goorder.ui.screens.settings.UserSettingsScreen
import com.goorder.ui.screens.settings.UserSettingsViewModel
import com.goorder.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterScreen(
    user: User?,
    authViewModel: AuthViewModel,
    onLogout: () -> Unit
) {
    val settingsViewModel: UserSettingsViewModel = viewModel()
    val printerViewModel: PrinterViewModel = viewModel()
    var isSettingsOpen by remember { mutableStateOf(false) }
    var isPrinterOpen by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Bind to printer service globally in PrinterScreen
    DisposableEffect(Unit) {
        printerViewModel.bindService(context)
        onDispose {
            printerViewModel.unbindService(context)
        }
    }

    // Handle back button
    BackHandler(enabled = isSettingsOpen || isPrinterOpen) {
        if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (isPrinterOpen) {
            isPrinterOpen = false
        }
    }

    // Minimize app if at dashboard
    BackHandler(enabled = !isSettingsOpen && !isPrinterOpen) {
        activity?.moveTaskToBack(true)
    }

    // Auto-print logic
    LaunchedEffect(authViewModel.printRequests, authViewModel.isAutoPrintEnabled, printerViewModel.isConnected) {
        if (authViewModel.isAutoPrintEnabled && printerViewModel.isConnected) {
            authViewModel.printRequests.forEach { request ->
                if (!request.isPrinted) {
                    val success = printerViewModel.printText(request.data)
                    if (success) {
                        authViewModel.markAsPrinted(request.id)
                    }
                }
            }
        }
    }

    PrinterContent(
        isSettingsOpen = isSettingsOpen,
        isPrinterOpen = isPrinterOpen,
        isConnected = printerViewModel.isConnected,
        isSocketConnected = authViewModel.isSocketConnected,
        isRefreshing = authViewModel.isRefreshingOrders,
        currentOutlet = authViewModel.currentOutlet,
        orders = authViewModel.orders,
        onRefreshOrders = { authViewModel.fetchOrdersFromApi() },
        onToggleSettings = { 
            isSettingsOpen = it
            if (it) isPrinterOpen = false
        },
        onTogglePrinter = { 
            isPrinterOpen = it
            if (it) isSettingsOpen = false
        },
        onStatusChange = { orderId, newStatus ->
            authViewModel.updateOrderStatus(orderId, newStatus)
        },
        onPrintOrder = { order ->
            if (printerViewModel.isConnected) {
                scope.launch {
                    val printData = order.toPrintText()
                    val success = printerViewModel.printText(printData)
                    if (success && order.id != null) {
                        authViewModel.markAsPrinted(order.id.toString())
                        android.widget.Toast.makeText(context, "Mencetak pesanan #${order.id}...", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Gagal mencetak pesanan", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                android.widget.Toast.makeText(context, "Pencetak Bluetooth belum bersambung. Sila sambungkan pencetak.", android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onLogout = onLogout,
        settingsScreen = {
            UserSettingsScreen(
                user = user,
                viewModel = settingsViewModel
            )
        },
        printerScreen = {
            PrinterConnectionScreen(
                viewModel = printerViewModel,
                isAutoPrintEnabled = authViewModel.isAutoPrintEnabled,
                onToggleAutoPrint = { authViewModel.toggleAutoPrint(it) }
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterContent(
    isSettingsOpen: Boolean,
    isPrinterOpen: Boolean,
    isConnected: Boolean,
    isSocketConnected: Boolean,
    isRefreshing: Boolean = false,
    currentOutlet: OutletDto?,
    orders: List<OrderSessionDto>,
    onRefreshOrders: () -> Unit = {},
    onToggleSettings: (Boolean) -> Unit,
    onTogglePrinter: (Boolean) -> Unit,
    onStatusChange: (Long, String) -> Unit,
    onPrintOrder: (OrderSessionDto) -> Unit,
    onLogout: () -> Unit,
    settingsScreen: @Composable () -> Unit,
    printerScreen: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    var isMenuExpanded by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Keluar", fontWeight = FontWeight.Bold) },
            text = { Text("Adakah anda pasti mahu log keluar daripada aplikasi?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSettingsOpen) {
                        Text("Tetapan Pengguna", fontWeight = FontWeight.Bold)
                    } else if (isPrinterOpen) {
                        Text("Sambungan Pencetak", fontWeight = FontWeight.Bold)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(start = 2.dp)) {
                                Text(
                                    text = "Pesanan",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = currentOutlet?.name ?: "Goorder Live Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
                navigationIcon = {
                    if (isSettingsOpen || isPrinterOpen) {
                        IconButton(onClick = { 
                            onToggleSettings(false)
                            onTogglePrinter(false)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali"
                            )
                        }
                    }
                },
                actions = {
                    if (!isSettingsOpen && !isPrinterOpen) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Live Badge Pill matching goreact
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSocketConnected) SuccessGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (isSocketConnected) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = if (isSocketConnected) "Live" else "Jeda",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSocketConnected) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = onRefreshOrders,
                                enabled = !isRefreshing
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Segar Pesanan",
                                    tint = if (isRefreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = if (isRefreshing) Modifier.rotate(spinAngle) else Modifier
                                )
                            }

                            Box {
                                IconButton(onClick = { isMenuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "User Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = isMenuExpanded,
                                    onDismissRequest = { isMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Tetapan Pengguna") },
                                        onClick = {
                                            isMenuExpanded = false
                                            onToggleSettings(true)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tetapan Pencetak") },
                                        onClick = {
                                            isMenuExpanded = false
                                            onTogglePrinter(true)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Print,
                                                contentDescription = null,
                                                tint = if (isConnected) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Log Keluar",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            isMenuExpanded = false
                                            showLogoutDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.padding(paddingValues)
        ) {
            if (isSettingsOpen) {
                settingsScreen()
            } else if (isPrinterOpen) {
                printerScreen()
            } else {
                DashboardScreen(
                    orders = orders,
                    isConnected = isConnected,
                    onStatusChange = onStatusChange,
                    onPrintOrder = onPrintOrder,
                    onRefresh = onRefreshOrders
                )
            }
        }
    }
}
