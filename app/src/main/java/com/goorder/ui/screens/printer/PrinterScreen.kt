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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goorder.R
import com.goorder.data.remote.dto.OrderSessionDto
import com.goorder.data.remote.dto.OutletDto
import com.goorder.data.remote.dto.toPrintText
import com.goorder.domain.model.User
import com.goorder.ui.components.AppSidebar
import com.goorder.ui.components.SidebarScreen
import com.goorder.ui.screens.dashboard.DashboardScreen
import com.goorder.ui.screens.dashboard.OrderViewModel
import com.goorder.ui.screens.settings.UserSettingsScreen
import com.goorder.ui.screens.settings.UserSettingsViewModel
import com.goorder.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterScreen(
    user: User?,
    onLogout: () -> Unit,
    onSyncSession: () -> Unit
) {
    // ── ViewModel: setiap satu ada tanggungjawab tersendiri ──
    val orderViewModel: OrderViewModel = viewModel()
    val printQueueViewModel: PrintQueueViewModel = viewModel()
    val printerViewModel: PrinterViewModel = viewModel()
    val settingsViewModel: UserSettingsViewModel = viewModel()

    var isSettingsOpen by remember { mutableStateOf(false) }
    var isPrinterOpen by remember { mutableStateOf(false) }
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Wire callbacks antara ViewModels ──
    LaunchedEffect(Unit) {
        // Apabila OrderViewModel dapat orders baru dari API, sync ke print queue
        orderViewModel.onNewOrdersFetched = { orders, isFirstLoad ->
            printQueueViewModel.syncWithOrders(orders, isFirstLoad)
        }

        // Apabila WebSocket terima arahan cetak QR, hantar ke print queue
        orderViewModel.onPrintQrReceived = { data ->
            printQueueViewModel.addPrintRequest(
                com.goorder.domain.model.PrintRequest(data = data)
            )
        }

        // Apabila WebSocket terima notifikasi, sync session di AuthViewModel
        orderViewModel.onSyncSession = onSyncSession

        // Mulakan sambungan real-time & muat data
        orderViewModel.connectSocket()
        printQueueViewModel.connectMqtt()
        orderViewModel.fetchCurrentOutletAndOrders()
    }

    // Bind to printer service
    DisposableEffect(Unit) {
        printerViewModel.bindService(context)
        onDispose {
            printerViewModel.unbindService(context)
        }
    }

    // Cleanup bila screen dibuang dari composition (logout / session expired)
    DisposableEffect(Unit) {
        onDispose {
            orderViewModel.cleanup()
            printQueueViewModel.cleanup()
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Handle back button
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && (isSettingsOpen || isPrinterOpen)) {
        if (isSettingsOpen) {
            isSettingsOpen = false
        } else if (isPrinterOpen) {
            isPrinterOpen = false
        }
    }

    // Minimize app if at dashboard
    BackHandler(enabled = !drawerState.isOpen && !isSettingsOpen && !isPrinterOpen) {
        activity?.moveTaskToBack(true)
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Keluar", fontWeight = FontWeight.Bold) },
            text = { Text("Adakah anda pasti mahu log keluar daripada aplikasi?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        orderViewModel.cleanup()
                        printQueueViewModel.cleanup()
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSettingsOpen && !isPrinterOpen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 2.dp,
                modifier = Modifier.width(300.dp)
            ) {
                AppSidebar(
                    user = user,
                    currentOutlet = orderViewModel.currentOutlet,
                    isSocketConnected = orderViewModel.isSocketConnected,
                    isPrinterConnected = printerViewModel.isConnected,
                    ordersCount = orderViewModel.orders.size,
                    currentScreen = when {
                        isSettingsOpen -> SidebarScreen.SETTINGS
                        isPrinterOpen -> SidebarScreen.PRINTER
                        else -> SidebarScreen.ORDERS
                    },
                    onSelectScreen = { screen ->
                        scope.launch { drawerState.close() }
                        when (screen) {
                            SidebarScreen.ORDERS -> {
                                isSettingsOpen = false
                                isPrinterOpen = false
                            }
                            SidebarScreen.PRINTER -> {
                                isPrinterOpen = true
                                isSettingsOpen = false
                            }
                            SidebarScreen.SETTINGS -> {
                                isSettingsOpen = true
                                isPrinterOpen = false
                            }
                        }
                    },
                    onLogoutClick = {
                        scope.launch { drawerState.close() }
                        showLogoutDialog = true
                    },
                    onCloseDrawer = {
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        PrinterContent(
            isSettingsOpen = isSettingsOpen,
            isPrinterOpen = isPrinterOpen,
            isConnected = printerViewModel.isConnected,
            isSocketConnected = orderViewModel.isSocketConnected,
            isRefreshing = orderViewModel.isRefreshingOrders,
            currentOutlet = orderViewModel.currentOutlet,
            orders = orderViewModel.orders,
            printedOrderIds = printQueueViewModel.printedOrderIds,
            onRefreshOrders = { orderViewModel.fetchOrdersFromApi() },
            onToggleSettings = { 
                isSettingsOpen = it
                if (it) isPrinterOpen = false
            },
            onTogglePrinter = { 
                isPrinterOpen = it
                if (it) isSettingsOpen = false
            },
            onStatusChange = { orderId, newStatus ->
                orderViewModel.updateOrderStatus(orderId, newStatus)
            },
            onPrintOrder = { order ->
                if (printerViewModel.isConnected) {
                    scope.launch {
                        val targetUserId = printQueueViewModel.currentUserId ?: user?.id
                        val printData = order.toPrintText(filterUserId = targetUserId)
                        if (printData == null) {
                            android.widget.Toast.makeText(context, "Tiada item pesanan untuk stesen/pencetak ini", android.widget.Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val success = printerViewModel.printText(printData)
                        if (success && order.id != null) {
                            printQueueViewModel.markAsPrinted(order.id.toString())
                            android.widget.Toast.makeText(context, "Mencetak pesanan #${order.id}...", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            android.widget.Toast.makeText(context, "Gagal mencetak pesanan", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    android.widget.Toast.makeText(context, "Pencetak Bluetooth belum bersambung. Sila sambungkan pencetak.", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onOpenSidebar = {
                scope.launch {
                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                }
            },
            onRequestLogout = {
                showLogoutDialog = true
            },
            settingsScreen = {
                UserSettingsScreen(
                    user = user,
                    viewModel = settingsViewModel
                )
            },
            printerScreen = {
                PrinterConnectionScreen(
                    viewModel = printerViewModel,
                    isAutoPrintEnabled = printQueueViewModel.isAutoPrintEnabled,
                    onToggleAutoPrint = { printQueueViewModel.toggleAutoPrint(it) }
                )
            }
        )
    }
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
    printedOrderIds: Set<String> = emptySet(),
    onRefreshOrders: () -> Unit = {},
    onToggleSettings: (Boolean) -> Unit,
    onTogglePrinter: (Boolean) -> Unit,
    onStatusChange: (Long, String) -> Unit,
    onPrintOrder: (OrderSessionDto) -> Unit,
    onOpenSidebar: () -> Unit,
    onRequestLogout: () -> Unit,
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
                    } else {
                        IconButton(
                            onClick = onOpenSidebar,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_panel_left),
                                contentDescription = "Buka Sidebar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
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
                            // Live Badge Pill
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
                    printedOrderIds = printedOrderIds,
                    onStatusChange = onStatusChange,
                    onPrintOrder = onPrintOrder,
                    onRefresh = onRefreshOrders
                )
            }
        }
    }
}
