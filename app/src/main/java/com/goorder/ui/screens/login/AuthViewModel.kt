package com.goorder.ui.screens.login

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goorder.data.local.SessionManager
import com.goorder.data.remote.AuthRemoteDataSource
import com.goorder.data.remote.MqttManager
import com.goorder.data.remote.SocketManager
import com.goorder.data.remote.dto.*
import com.goorder.data.repository.AuthRepositoryImpl
import com.goorder.domain.model.PrintRequest
import com.goorder.domain.model.User
import com.goorder.domain.usecase.CheckSessionUseCase
import com.goorder.domain.usecase.LoginUseCase
import com.goorder.domain.usecase.LogoutUseCase
import com.goorder.service.BluetoothPrinterService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data object Unauthenticated : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    
    // Manual DI - In a real app, use Hilt or Koin
    private val remoteDataSource = AuthRemoteDataSource(application)
    private val sessionManager = SessionManager(application)
    private val repository = AuthRepositoryImpl(remoteDataSource, sessionManager)
    private val socketManager = SocketManager()
    private val mqttManager = MqttManager()
    
    private val loginUseCase = LoginUseCase(repository)
    private val logoutUseCase = LogoutUseCase(repository)
    private val checkSessionUseCase = CheckSessionUseCase(repository)
    
    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set

    var currentOutlet by mutableStateOf<OutletDto?>(null)
        private set

    var orders by mutableStateOf<List<OrderSessionDto>>(emptyList())
        private set

    var printRequests by mutableStateOf<List<PrintRequest>>(emptyList())
        private set
        
    var isAutoPrintEnabled by mutableStateOf(false)
        private set

    var isRefreshingOrders by mutableStateOf(false)
        private set

    var isSocketConnected by mutableStateOf(false)
        private set

    init {
        observeSession()
        syncSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            checkSessionUseCase.getSavedUser().collectLatest { user ->
                if (user != null) {
                    authState = AuthState.Authenticated(user)

                    // Muatkan status auto-print yang disimpan
                    isAutoPrintEnabled = sessionManager.getAutoPrintEnabled()

                    // MULAKAN PERKHIDMATAN PENCETAK BLUETOOTH DI LATAR BELAKANG (FOREGROUND SERVICE)
                    // Servis ini akan sentiasa bersambung ke pencetak walaupun aplikasi ditutup/berjalan di background
                    try {
                        BluetoothPrinterService.startService(getApplication())
                    } catch (e: Exception) {
                        Log.e("AuthViewModel", "Gagal memulakan BluetoothPrinterService", e)
                    }

                    val token = sessionManager.getToken()
                    if (!token.isNullOrBlank()) {
                        socketManager.connect(
                            token = token,
                            onNotificationReceived = {
                                syncSession()
                            },
                            onPrintQrReceived = { data ->
                                val newRequest = PrintRequest(data = data)
                                printRequests = printRequests + newRequest
                                checkAndAutoPrint(listOf(newRequest))
                            },
                            onOrderUpdated = {
                                fetchOrdersFromApi()
                            },
                            onConnectionChange = { connected ->
                                isSocketConnected = connected
                            }
                        )
                    }

                    // Sambungkan ke MQTT Broker untuk mendengar pesanan dari Go backend pada topik pos/order/print
                    mqttManager.connect(
                        onPrintOrderReceived = { payload ->
                            val newRequest = PrintRequest(data = payload)
                            printRequests = printRequests + newRequest
                            checkAndAutoPrint(listOf(newRequest))
                        }
                    )

                    // Muat turun cawangan pengguna & seterusnya pesanan dari outlet
                    fetchCurrentOutletAndOrders()
                } else {
                    authState = AuthState.Unauthenticated
                    // Hentikan perkhidmatan pencetak Bluetooth latar belakang bila log keluar
                    BluetoothPrinterService.stopService(getApplication())

                    // Disconnect socket & MQTT when not authenticated
                    socketManager.disconnect()
                    mqttManager.disconnect()
                    orders = emptyList()
                    printRequests = emptyList()
                    currentOutlet = null
                    isSocketConnected = false
                }
            }
        }
    }

    fun clearPrintRequests() {
        // Hanya padam yang sudah diprint
        printRequests = printRequests.filter { !it.isPrinted }
    }

    fun deletePrintRequest(id: String) {
        printRequests = printRequests.filter { it.id != id }
    }

    fun toggleAutoPrint(enabled: Boolean) {
        isAutoPrintEnabled = enabled
        viewModelScope.launch {
            sessionManager.saveAutoPrint(enabled)
        }
    }

    fun markAsPrinted(requestId: String) {
        printRequests = printRequests.map {
            if (it.id == requestId) it.copy(isPrinted = true) else it
        }
    }

    private fun checkAndAutoPrint(requests: List<PrintRequest>) {
        if (!isAutoPrintEnabled) return
        viewModelScope.launch {
            val service = BluetoothPrinterService.instance
            if (service != null && service.isConnected.value) {
                requests.forEach { req ->
                    if (!req.isPrinted) {
                        val success = service.printText(req.data)
                        if (success) {
                            markAsPrinted(req.id)
                        }
                    }
                }
            }
        }
    }

    fun fetchCurrentOutletAndOrders() {
        viewModelScope.launch {
            isRefreshingOrders = true
            remoteDataSource.getMyOutlets()
                .onSuccess { staffList ->
                    val mainStaff = staffList.find { it.main } ?: staffList.firstOrNull()
                    val outlet = mainStaff?.outlet
                    if (outlet?.id != null) {
                        currentOutlet = outlet
                        sessionManager.saveCurrentOutlet(outlet.id, outlet.name ?: "")
                        fetchOrdersFromApi(outlet.id)
                    } else {
                        val savedId = sessionManager.getSavedOutletId()
                        val savedName = sessionManager.getSavedOutletName()
                        if (savedId != null) {
                            currentOutlet = OutletDto(id = savedId, name = savedName)
                            fetchOrdersFromApi(savedId)
                        }
                    }
                }
                .onFailure { error ->
                    Log.e("AuthViewModel", "Failed to fetch user outlets", error)
                    val savedId = sessionManager.getSavedOutletId()
                    val savedName = sessionManager.getSavedOutletName()
                    if (savedId != null) {
                        currentOutlet = OutletDto(id = savedId, name = savedName)
                        fetchOrdersFromApi(savedId)
                    }
                }
            isRefreshingOrders = false
        }
    }

    fun fetchOrdersFromApi(targetOutletId: Long? = null) {
        viewModelScope.launch {
            val outletId = targetOutletId
                ?: currentOutlet?.id
                ?: sessionManager.getSavedOutletId()

            if (outletId == null) {
                fetchCurrentOutletAndOrders()
                return@launch
            }

            isRefreshingOrders = true

            remoteDataSource.getOrdersByOutletId(outletId)
                .onSuccess { orderDtos ->
                    orders = orderDtos

                    val apiRequests = orderDtos.map { dto ->
                        PrintRequest(
                            id = dto.id?.toString() ?: UUID.randomUUID().toString(),
                            data = dto.toPrintText(),
                            isPrinted = dto.status?.equals("SERVED", ignoreCase = true) == true ||
                                        dto.status?.equals("COMPLETED", ignoreCase = true) == true
                        )
                    }

                    val existingMap = printRequests.associateBy { it.id }
                    val mergedRequests = apiRequests.map { apiReq ->
                        existingMap[apiReq.id] ?: apiReq
                    }
                    val newUniqueLocal = printRequests.filter { localReq ->
                        mergedRequests.none { it.id == localReq.id }
                    }

                    val newlyArrivedUnprinted = apiRequests.filter { !it.isPrinted && existingMap[it.id] == null }

                    printRequests = mergedRequests + newUniqueLocal

                    // Auto-print order baru yang belum pernah dicetak jika suis auto-print aktif
                    if (newlyArrivedUnprinted.isNotEmpty()) {
                        checkAndAutoPrint(newlyArrivedUnprinted)
                    }
                }
                .onFailure { error ->
                    Log.e("AuthViewModel", "Failed to fetch orders by outlet ID: $outletId", error)
                }

            isRefreshingOrders = false
        }
    }

    fun updateOrderStatus(orderId: Long, nextStatus: String) {
        viewModelScope.launch {
            val oldOrders = orders
            orders = orders.map { if (it.id == orderId) it.copy(status = nextStatus) else it }

            remoteDataSource.updateOrderStatus(orderId, nextStatus)
                .onSuccess {
                    fetchOrdersFromApi()
                }
                .onFailure { error ->
                    Log.e("AuthViewModel", "Failed to update order status", error)
                    orders = oldOrders
                }
        }
    }

    private fun syncSession() {
        viewModelScope.launch {
            checkSessionUseCase.syncUser()
                .onFailure {
                    // Optional: handle background sync failure
                }
        }
    }

    fun login(email: String, pass: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            loginUseCase(email, pass)
                .onSuccess {
                    onResult(null)
                }
                .onFailure { error ->
                    onResult(error.message ?: "Login gagal")
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            BluetoothPrinterService.stopService(getApplication())
            logoutUseCase()
        }
    }
}
