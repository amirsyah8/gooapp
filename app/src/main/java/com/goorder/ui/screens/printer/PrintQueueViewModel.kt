package com.goorder.ui.screens.printer

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goorder.data.local.SessionManager
import com.goorder.data.remote.MqttManager
import com.goorder.data.remote.dto.OrderSessionDto
import com.goorder.data.remote.dto.toPrintText
import com.goorder.domain.model.PrintRequest
import com.goorder.service.BluetoothPrinterService
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * PrintQueueViewModel — Tanggungjawab baris gilir cetakan & auto-print.
 *
 * Skop: print requests, auto-print toggle, MQTT connection, cetak automatik.
 * BUKAN: auth (AuthViewModel), orders/outlet (OrderViewModel).
 */
class PrintQueueViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val mqttManager = MqttManager()

    // ── State ──

    var printRequests by mutableStateOf<List<PrintRequest>>(emptyList())
        private set

    var isAutoPrintEnabled by mutableStateOf(false)
        private set

    var savedPrintedIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val printedOrderIds: Set<String>
        get() = savedPrintedIds

    private var isMqttInitialized = false

    init {
        viewModelScope.launch {
            sessionManager.autoPrintData.collect {
                isAutoPrintEnabled = it
            }
        }
        viewModelScope.launch {
            sessionManager.printedOrdersData.collect {
                savedPrintedIds = it
            }
        }
    }

    // ── MQTT Connection ──

    fun connectMqtt() {
        if (isMqttInitialized) return
        isMqttInitialized = true

        mqttManager.connect(
            onPrintOrderReceived = { payload ->
                val newRequest = PrintRequest(data = payload)
                addPrintRequest(newRequest)
            }
        )
    }

    // ── Print Request Management ──

    /** Tambah print request baru (dari MQTT atau WebSocket). */
    fun addPrintRequest(request: PrintRequest) {
        printRequests = printRequests + request
        checkAndAutoPrint(listOf(request))
    }

    /**
     * Sinkronkan print queue dengan senarai pesanan dari API.
     * Merge logik: kekalkan status 'isPrinted' yang sedia ada,
     * hanya auto-print order baru yang belum pernah dilihat.
     */
    fun syncWithOrders(orderDtos: List<OrderSessionDto>, isFirstLoad: Boolean) {
        val apiRequests = orderDtos.map { dto ->
            val orderIdStr = dto.id?.toString() ?: UUID.randomUUID().toString()
            val alreadyPrinted = savedPrintedIds.contains(orderIdStr) ||
                    dto.status?.equals("SERVED", ignoreCase = true) == true ||
                    dto.status?.equals("COMPLETED", ignoreCase = true) == true

            PrintRequest(
                id = orderIdStr,
                data = dto.toPrintText(),
                isPrinted = alreadyPrinted
            )
        }

        val existingMap = printRequests.associateBy { it.id }

        // Gabungkan: kekalkan state sedia ada jika request sudah wujud
        val mergedRequests = apiRequests.map { apiReq ->
            existingMap[apiReq.id] ?: apiReq
        }

        // Kekalkan local-only requests (cth. dari MQTT/WS yang tiada dalam API)
        val newUniqueLocal = printRequests.filter { localReq ->
            mergedRequests.none { it.id == localReq.id }
        }

        // Kenal pasti order baru yang belum pernah dilihat
        val newlyArrivedUnprinted = if (isFirstLoad) {
            emptyList()
        } else {
            apiRequests.filter { !it.isPrinted && existingMap[it.id] == null }
        }

        printRequests = mergedRequests + newUniqueLocal

        // Auto-print HANYA order baru yang masuk semasa app sedang berjalan
        if (newlyArrivedUnprinted.isNotEmpty()) {
            checkAndAutoPrint(newlyArrivedUnprinted)
        }
    }

    fun clearPrintRequests() {
        // Hanya padam yang sudah diprint
        printRequests = printRequests.filter { !it.isPrinted }
    }

    fun deletePrintRequest(id: String) {
        printRequests = printRequests.filter { it.id != id }
    }

    fun markAsPrinted(requestId: String) {
        printRequests = printRequests.map {
            if (it.id == requestId) it.copy(isPrinted = true) else it
        }
        savedPrintedIds = savedPrintedIds + requestId
        viewModelScope.launch {
            sessionManager.savePrintedOrderId(requestId)
        }
    }

    fun toggleAutoPrint(enabled: Boolean) {
        isAutoPrintEnabled = enabled
        viewModelScope.launch {
            sessionManager.saveAutoPrint(enabled)
        }
    }

    // ── Auto-Print Logic ──

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

    // ── Cleanup ──

    fun cleanup() {
        mqttManager.disconnect()
        printRequests = emptyList()
        isMqttInitialized = false
    }
}
