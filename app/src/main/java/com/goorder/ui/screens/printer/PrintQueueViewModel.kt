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
import com.goorder.data.remote.dto.extractCartIdsFromMqttPayload
import com.goorder.data.remote.dto.formatMqttOrderToReceipt
import com.goorder.data.remote.dto.matchesPrinterUser
import com.goorder.data.remote.dto.toPrintText
import com.goorder.domain.model.PrintRequest
import com.goorder.service.BluetoothPrinterService
import kotlinx.coroutines.flow.firstOrNull
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

    var currentUserId by mutableStateOf<String?>(null)
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
        viewModelScope.launch {
            sessionManager.userData.collect {
                currentUserId = it?.id
            }
        }
    }

    // ── MQTT Connection ──

    fun connectMqtt() {
        if (isMqttInitialized) return
        isMqttInitialized = true

        mqttManager.connect(
            onPrintOrderReceived = { payload ->
                viewModelScope.launch {
                    val userId = currentUserId ?: sessionManager.userData.firstOrNull()?.id
                    Log.d("PrintQueueViewModel", "Mesej MQTT diterima. Memeriksa printer untuk user ID: $userId")
                    val formattedReceipt = formatMqttOrderToReceipt(payload, userId)
                    if (formattedReceipt != null) {
                        val cartIds = extractCartIdsFromMqttPayload(payload, userId)
                        val alreadyPrinted = cartIds.isNotEmpty() && cartIds.all { savedPrintedIds.contains(it) }
                        if (!alreadyPrinted) {
                            Log.d("PrintQueueViewModel", "✅ MQTT order sepadan dengan printer staf ID $userId! Menambah ke baris gilir cetak.")
                            val requestId = cartIds.firstOrNull() ?: UUID.randomUUID().toString()
                            val newRequest = PrintRequest(
                                id = requestId,
                                data = formattedReceipt,
                                cartIds = cartIds
                            )
                            addPrintRequest(newRequest)
                        } else {
                            Log.d("PrintQueueViewModel", "ℹ️ MQTT order diabaikan: semua cart_id sudah dicetak.")
                        }
                    } else {
                        Log.d("PrintQueueViewModel", "ℹ️ MQTT order diabaikan: tiada item untuk printer staf ID $userId.")
                    }
                }
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
        viewModelScope.launch {
            val userId = currentUserId ?: sessionManager.userData.firstOrNull()?.id
            val apiRequests = orderDtos.mapNotNull { dto ->
                val printData = dto.toPrintText(filterUserId = userId) ?: return@mapNotNull null
                val targetItems = if (!userId.isNullOrBlank()) {
                    dto.items?.filter { it.matchesPrinterUser(userId) } ?: emptyList()
                } else {
                    dto.items ?: emptyList()
                }
                val cartIds = targetItems.mapNotNull { it.cartId }.filter { it.isNotBlank() }
                val orderIdStr = dto.id?.toString()

                val alreadyPrinted = (cartIds.isNotEmpty() && cartIds.all { savedPrintedIds.contains(it) }) ||
                        (orderIdStr != null && savedPrintedIds.contains(orderIdStr)) ||
                        dto.status?.equals("SERVED", ignoreCase = true) == true ||
                        dto.status?.equals("COMPLETED", ignoreCase = true) == true

                val requestId = cartIds.firstOrNull() ?: orderIdStr ?: UUID.randomUUID().toString()

                PrintRequest(
                    id = requestId,
                    data = printData,
                    isPrinted = alreadyPrinted,
                    cartIds = cartIds
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
    }

    fun clearPrintRequests() {
        // Hanya padam yang sudah diprint
        printRequests = printRequests.filter { !it.isPrinted }
    }

    fun deletePrintRequest(id: String) {
        printRequests = printRequests.filter { it.id != id }
    }

    fun markAsPrinted(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        printRequests = printRequests.map { req ->
            if (idSet.contains(req.id) || req.cartIds.any { idSet.contains(it) }) {
                req.copy(isPrinted = true)
            } else {
                req
            }
        }
        savedPrintedIds = savedPrintedIds + idSet
        viewModelScope.launch {
            sessionManager.savePrintedOrderIds(idSet)
        }
    }

    fun markAsPrinted(requestId: String) {
        markAsPrinted(listOf(requestId))
    }

    fun markOrderAsPrinted(order: OrderSessionDto, targetUserId: String? = null) {
        val targetItems = if (!targetUserId.isNullOrBlank()) {
            val matched = order.items?.filter { it.matchesPrinterUser(targetUserId) } ?: emptyList()
            if (matched.isNotEmpty()) matched else (order.items ?: emptyList())
        } else {
            order.items ?: emptyList()
        }
        val cartIds = targetItems.mapNotNull { it.cartId }.filter { it.isNotBlank() }
        val idsToSave = (cartIds + listOfNotNull(order.id?.toString())).filter { it.isNotBlank() }
        markAsPrinted(idsToSave)
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
                            val idsToMark = (req.cartIds + req.id).filter { it.isNotBlank() }
                            markAsPrinted(idsToMark)
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
