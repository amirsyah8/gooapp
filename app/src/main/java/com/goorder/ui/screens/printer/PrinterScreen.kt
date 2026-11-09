package com.goorder.ui.screens.printer

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goorder.domain.model.PrintRequest
import com.goorder.domain.model.User
import com.goorder.ui.screens.dashboard.DashboardScreen
import com.goorder.ui.screens.login.AuthViewModel
import com.goorder.ui.screens.settings.UserSettingsScreen
import com.goorder.ui.screens.settings.UserSettingsViewModel
import com.goorder.ui.theme.LoginTheme
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

    // Optional: minimize app if at dashboard
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
        isAutoPrintEnabled = authViewModel.isAutoPrintEnabled,
        onToggleAutoPrint = { authViewModel.toggleAutoPrint(it) },
        onToggleSettings = { 
            isSettingsOpen = it
            if (it) isPrinterOpen = false
        },
        onTogglePrinter = { 
            isPrinterOpen = it
            if (it) isSettingsOpen = false
        },
        printRequests = authViewModel.printRequests,
        onClearRequests = { authViewModel.clearPrintRequests() },
        onDeleteRequest = { authViewModel.deletePrintRequest(it) },
        onManualPrint = { request ->
            if (printerViewModel.isConnected) {
                scope.launch {
                    val success = printerViewModel.printText(request.data)
                    if (success) {
                        authViewModel.markAsPrinted(request.id)
                    }
                }
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
            PrinterConnectionScreen(viewModel = printerViewModel)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterContent(
    isSettingsOpen: Boolean,
    isPrinterOpen: Boolean,
    isConnected: Boolean,
    isAutoPrintEnabled: Boolean,
    onToggleAutoPrint: (Boolean) -> Unit,
    onToggleSettings: (Boolean) -> Unit,
    onTogglePrinter: (Boolean) -> Unit,
    printRequests: List<PrintRequest>,
    onClearRequests: () -> Unit,
    onDeleteRequest: (String) -> Unit,
    onManualPrint: (PrintRequest) -> Unit,
    onLogout: () -> Unit,
    settingsScreen: @Composable () -> Unit,
    printerScreen: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSettingsOpen) {
                        Text("User Settings")
                    } else if (isPrinterOpen) {
                        Text("Printer Bluetooth")
                    } else {
                        Text(
                            "ICONFACADE",
                            color = Color(0xFF1877F2),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
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
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (!isSettingsOpen && !isPrinterOpen) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = isAutoPrintEnabled,
                                onCheckedChange = onToggleAutoPrint,
                                modifier = Modifier.scale(0.7f) // Kecilkan sedikit toggle
                            )
                            IconButton(onClick = { onTogglePrinter(true) }) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = "Printer Settings",
                                    tint = if (isConnected) SuccessGreen else LocalContentColor.current
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (!isSettingsOpen && !isPrinterOpen) {
                FloatingActionButton(
                    onClick = { onToggleSettings(true) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "User Settings"
                    )
                }
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .padding(paddingValues)
        ) {
            if (isSettingsOpen) {
                settingsScreen()
            } else if (isPrinterOpen) {
                printerScreen()
            } else {
                DashboardScreen(
                    printRequests = printRequests,
                    onClearRequests = onClearRequests,
                    onDeleteRequest = onDeleteRequest,
                    onManualPrint = onManualPrint,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PrinterScreenPreview() {
    LoginTheme {
        PrinterContent(
            isSettingsOpen = false,
            isPrinterOpen = false,
            isConnected = false,
            isAutoPrintEnabled = false,
            onToggleAutoPrint = {},
            onToggleSettings = {},
            onTogglePrinter = {},
            printRequests = listOf(PrintRequest(data = "Request 1")),
            onClearRequests = {},
            onDeleteRequest = {},
            onManualPrint = {},
            onLogout = {},
            settingsScreen = {},
            printerScreen = {}
        )
    }
}


@Preview(showBackground = true)
@Composable
fun PrinterScreenPrinterPreview() {
    LoginTheme {
        PrinterContent(
            isSettingsOpen = false,
            isPrinterOpen = true,
            isConnected = true,
            isAutoPrintEnabled = false,
            onToggleAutoPrint = {},
            onToggleSettings = {},
            onTogglePrinter = {},
            printRequests = listOf(PrintRequest(data = "Request 1")),
            onClearRequests = {},
            onDeleteRequest = {},
            onManualPrint = {},
            onLogout = {},
            settingsScreen = {},
            printerScreen = {
                PrinterConnectionScreen()
            }
        )
    }
}
