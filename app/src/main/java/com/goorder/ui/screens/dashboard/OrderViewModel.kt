package com.goorder.ui.screens.dashboard

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goorder.data.local.SessionManager
import com.goorder.data.remote.AuthRemoteDataSource
import com.goorder.data.remote.SocketManager
import com.goorder.data.remote.dto.OrderSessionDto
import com.goorder.data.remote.dto.OutletDto
import kotlinx.coroutines.launch

/**
 * OrderViewModel — Tanggungjawab pengurusan pesanan & sambungan real-time.
 *
 * Skop: orders, outlet, status updates, WebSocket connection.
 * BUKAN: auth (AuthViewModel), print queue (PrintQueueViewModel).
 */
class OrderViewModel(application: Application) : AndroidViewModel(application) {

    private val remoteDataSource = AuthRemoteDataSource(application)
    private val sessionManager = SessionManager(application)
    private val socketManager = SocketManager()

    // ── State ──

    var orders by mutableStateOf<List<OrderSessionDto>>(emptyList())
        private set

    var currentOutlet by mutableStateOf<OutletDto?>(null)
        private set

    var isRefreshingOrders by mutableStateOf(false)
        private set

    var isSocketConnected by mutableStateOf(false)
        private set

    var isInitialOrdersLoaded = false
        private set

    // ── Callbacks (diwire-kan dari PrinterScreen) ──

    /** Dipanggil apabila orders baru diambil dari API. */
    var onNewOrdersFetched: ((orders: List<OrderSessionDto>, isFirstLoad: Boolean) -> Unit)? = null

    /** Dipanggil apabila WebSocket menerima arahan cetak QR. */
    var onPrintQrReceived: ((data: String) -> Unit)? = null

    /** Dipanggil apabila WebSocket menerima notifikasi (untuk sync session). */
    var onSyncSession: (() -> Unit)? = null

    private var isSocketInitialized = false

    // ── WebSocket Connection ──

    fun connectSocket() {
        if (isSocketInitialized) return
        isSocketInitialized = true

        viewModelScope.launch {
            val token = sessionManager.getToken()
            if (!token.isNullOrBlank()) {
                socketManager.connect(
                    token = token,
                    onNotificationReceived = { onSyncSession?.invoke() },
                    onPrintQrReceived = { data -> onPrintQrReceived?.invoke(data) },
                    onOrderUpdated = { fetchOrdersFromApi() },
                    onConnectionChange = { connected -> isSocketConnected = connected }
                )
            }
        }
    }

    // ── Outlet & Orders ──

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
                        fallbackToSavedOutlet()
                    }
                }
                .onFailure { error ->
                    Log.e("OrderViewModel", "Failed to fetch user outlets", error)
                    fallbackToSavedOutlet()
                }
            isRefreshingOrders = false
        }
    }

    private suspend fun fallbackToSavedOutlet() {
        val savedId = sessionManager.getSavedOutletId()
        val savedName = sessionManager.getSavedOutletName()
        if (savedId != null) {
            currentOutlet = OutletDto(id = savedId, name = savedName)
            fetchOrdersFromApi(savedId)
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

                    val isFirstLoad = !isInitialOrdersLoaded
                    isInitialOrdersLoaded = true

                    // Beritahu PrintQueueViewModel tentang orders baru
                    onNewOrdersFetched?.invoke(orderDtos, isFirstLoad)
                }
                .onFailure { error ->
                    Log.e("OrderViewModel", "Failed to fetch orders by outlet ID: $outletId", error)
                }

            isRefreshingOrders = false
        }
    }

    fun updateOrderStatus(orderId: Long, nextStatus: String) {
        viewModelScope.launch {
            val oldOrders = orders
            // Optimistic update
            orders = orders.map { if (it.id == orderId) it.copy(status = nextStatus) else it }

            remoteDataSource.updateOrderStatus(orderId, nextStatus)
                .onSuccess { fetchOrdersFromApi() }
                .onFailure { error ->
                    Log.e("OrderViewModel", "Failed to update order status", error)
                    orders = oldOrders  // Rollback
                }
        }
    }

    // ── Cleanup ──

    fun cleanup() {
        socketManager.disconnect()
        orders = emptyList()
        currentOutlet = null
        isSocketConnected = false
        isInitialOrdersLoaded = false
        isSocketInitialized = false
    }
}
