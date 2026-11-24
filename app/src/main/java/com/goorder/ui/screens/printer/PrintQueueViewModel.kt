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
import com.goorder.domain.model.User
import com.goorder.service.BluetoothPrinterService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONObject
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

    fun setCurrentUser(user: User?) {
        val newId = user?.id?.takeIf { it.isNotBlank() }
        currentUserId = newId
        Log.d("PrintQueueViewModel", "setCurrentUser: currentUserId=$currentUserId (${user?.name})")
    }

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
            sessionManager.userData.collect { user ->
                if (user?.id != null && user.id.isNotBlank()) {
                    currentUserId = user.id
                    Log.d("PrintQueueViewModel", "SessionManager userData collect: currentUserId=$currentUserId")
                }
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
                    handleOrderPrintFromMqtt(
                        payload = payload,
                        bypassAutoPrint = false,
                        forcePrint = false,
                        sourceTopic = "pos/order/print"
                    )
                }
            },
            onPrintManualReceived = { payload ->
                viewModelScope.launch {
                    handleOrderPrintFromMqtt(
                        payload = payload,
                        bypassAutoPrint = true,
                        forcePrint = false,
                        sourceTopic = "pos/order/printmanual"
                    )
                }
            },
            onPrintForceReceived = { payload ->
                viewModelScope.launch {
                    handleOrderPrintFromMqtt(
                        payload = payload,
                        bypassAutoPrint = true,
                        forcePrint = true,
                        sourceTopic = "pos/order/printforce"
                    )
                }
            },
            onTestPrintReceived = { payload ->
                viewModelScope.launch {
                    handleTestPrintFromMqtt(payload)
                }
            }
        )
    }

    /**
     * Memproses mesej cetak pesanan dari topik MQTT (pos/order/print, pos/order/printmanual, pos/order/printforce).
     *
     * @param payload JSON string pesanan
     * @param bypassAutoPrint Jika true, terus hantar cetakan ke pencetak walaupun suis auto-print dimatikan.
     * @param forcePrint Jika true, terus cetak walaupun semua cart_id sudah pernah dicetak sebelumnya.
     */
    private suspend fun handleOrderPrintFromMqtt(
        payload: String,
        bypassAutoPrint: Boolean,
        forcePrint: Boolean,
        sourceTopic: String
    ) {
        val userId = currentUserId ?: sessionManager.userData.firstOrNull()?.id
        Log.d("PrintQueueViewModel", "Mesej MQTT [$sourceTopic] diterima. Memeriksa printer untuk user ID: $userId")

        val formattedReceipt = formatMqttOrderToReceipt(payload, userId)
        if (formattedReceipt == null) {
            Log.d("PrintQueueViewModel", "ℹ️ MQTT [$sourceTopic] diabaikan: tiada item untuk printer staf ID $userId.")
            return
        }

        val cartIds = extractCartIdsFromMqttPayload(payload, userId)
        val alreadyPrinted = cartIds.isNotEmpty() && cartIds.all { savedPrintedIds.contains(it) }

        if (alreadyPrinted && !forcePrint) {
            Log.d("PrintQueueViewModel", "ℹ️ MQTT [$sourceTopic] diabaikan: semua cart_id sudah dicetak (forcePrint=false).")
            return
        }

        val requestId = cartIds.firstOrNull() ?: UUID.randomUUID().toString()

        if (bypassAutoPrint || forcePrint) {
            Log.d("PrintQueueViewModel", "⚡ MQTT [$sourceTopic] cetak terus (bypassAutoPrint=$bypassAutoPrint, forcePrint=$forcePrint)...")
            val service = BluetoothPrinterService.instance
            var printedSuccess = false

            if (service != null && service.isConnected.value) {
                printedSuccess = service.printText(formattedReceipt)
                Log.d("PrintQueueViewModel", "Hasil cetakan MQTT [$sourceTopic]: $printedSuccess")
                if (printedSuccess && cartIds.isNotEmpty()) {
                    markAsPrinted(cartIds)
                    mqttManager.publishPrintedCartIds(cartIds)
                }
            } else {
                Log.w("PrintQueueViewModel", "⚠️ Pencetak belum bersambung semasa [$sourceTopic] diterima. Menambah ke baris gilir cetak.")
                service?.autoConnectToPrinter()
            }

            val newRequest = PrintRequest(
                id = requestId,
                data = formattedReceipt,
                isPrinted = printedSuccess,
                cartIds = cartIds
            )
            addPrintRequest(newRequest)
        } else {
            Log.d("PrintQueueViewModel", "✅ MQTT order sepadan dengan printer staf ID $userId! Menambah ke baris gilir cetak.")
            val newRequest = PrintRequest(
                id = requestId,
                data = formattedReceipt,
                cartIds = cartIds
            )
            addPrintRequest(newRequest)
        }
    }

    private suspend fun handleTestPrintFromMqtt(payload: String) {
        val sessionUser = sessionManager.userData.firstOrNull()
        val activeUserId = currentUserId ?: sessionUser?.id

        Log.d("PrintQueueViewModel", "MQTT [pos/printer/test] diterima. activeUserId=$activeUserId, payload=$payload")

        if (activeUserId.isNullOrBlank()) {
            Log.w("PrintQueueViewModel", "⚠️ Ujian cetak MQTT dibatalkan: Tiada ID pengguna pencetak yang log masuk.")
            return
        }

        if (payload.isBlank()) {
            Log.w("PrintQueueViewModel", "⚠️ Ujian cetak MQTT diabaikan: Payload kosong.")
            return
        }

        val trimmed = payload.trim()
        var targetUserId: String? = null

        try {
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val json = JSONObject(trimmed)

                // 1. Semak dalam "user" -> { "id": 7 } atau "hashid"
                if (json.has("user") && json.optJSONObject("user") != null) {
                    val userObj = json.getJSONObject("user")
                    targetUserId = userObj.optString("id").takeIf { it.isNotBlank() && it != "0" }
                        ?: userObj.optString("hashid").takeIf { it.isNotBlank() }
                }

                // 2. Semak fallback "user_id": 7
                if (targetUserId.isNullOrBlank() && json.has("user_id")) {
                    targetUserId = json.optString("user_id").takeIf { it.isNotBlank() && it != "0" }
                }

                // 3. Semak fallback "target_user_id": 7
                if (targetUserId.isNullOrBlank() && json.has("target_user_id")) {
                    targetUserId = json.optString("target_user_id").takeIf { it.isNotBlank() && it != "0" }
                }

                // 4. Semak fallback "id": 7
                if (targetUserId.isNullOrBlank() && json.has("id")) {
                    targetUserId = json.optString("id").takeIf { it.isNotBlank() && it != "0" }
                }
            } else if (trimmed.toLongOrNull() != null) {
                // Jika payload terus dihantar nombor ID sahaja (cth: "7")
                targetUserId = trimmed
            }
        } catch (e: Exception) {
            Log.e("PrintQueueViewModel", "Ralat membaca JSON test print payload: ${e.message}", e)
            return
        }

        // MESTILAH mempunyai id printer/staf dalam payload
        if (targetUserId.isNullOrBlank()) {
            Log.w("PrintQueueViewModel", "ℹ️ MQTT test print diabaikan: Tiada ID printer/user dalam payload.")
            return
        }

        // Padankan sama ada nombor (cth: 7 == 7) atau string (cth: "7" == "7")
        val targetLong = targetUserId.toLongOrNull()
        val activeLong = activeUserId.toLongOrNull()

        val isMatched = if (targetLong != null && activeLong != null) {
            targetLong == activeLong
        } else {
            targetUserId.trim().equals(activeUserId.trim(), ignoreCase = true)
        }

        Log.d(
            "PrintQueueViewModel",
            "Semakan padanan test print: targetUserId=$targetUserId vs activeUserId=$activeUserId -> isMatched=$isMatched"
        )

        if (!isMatched) {
            Log.i(
                "PrintQueueViewModel",
                "⛔ MQTT test print DIABAIKAN: Sasaran user_id=$targetUserId tidak sepadan dengan user_id app semasa ($activeUserId)."
            )
            return
        }

        Log.d("PrintQueueViewModel", "✅ MQTT test print sepadan dengan printer user ID $activeUserId! Memulakan cetakan...")

        val staffName = sessionUser?.name ?: sessionUser?.fname ?: "Staf"
        val staffRole = sessionUser?.roleStr ?: sessionUser?.type ?: "Admin"
        val outletName = sessionManager.getSavedOutletName()
        val printerName = BluetoothPrinterService.instance?.connectedDeviceName?.value
            ?: sessionManager.getSavedPrinterName()
            ?: "Thermal"

        val testText = StringBuilder().apply {
            append("================================\r\n")
            append("     GOORDER UJIAN CETAK        \r\n")
            append("   (DITERIMA MELALUI MQTT)      \r\n")
            append("================================\r\n")
            if (!outletName.isNullOrBlank()) {
                append("Outlet  : $outletName\r\n")
            }
            append("Pengguna: $staffName ($staffRole)\r\n")
            append("ID Staf : $activeUserId\r\n")
            append("Pencetak: $printerName\r\n")
            append("Tarikh  : ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\r\n")
            append("Sumber  : Client Browser (MQTT)\r\n")
            append("Status  : Sambungan Berjaya!\r\n")
            append("--------------------------------\r\n")
            append("1x Ujian Resit         RM 10.00\r\n")
            append("1x Minuman Ujian       RM  5.00\r\n")
            append("--------------------------------\r\n")
            append("Jumlah                 RM 15.00\r\n")
            append("================================\r\n")
            append("  Terima Kasih - Sedia Digunakan\r\n")
        }.toString()

        val service = BluetoothPrinterService.instance
        var printedSuccess = false
        if (service != null && service.isConnected.value) {
            printedSuccess = service.printText(testText)
            Log.d("PrintQueueViewModel", "Hasil cetakan ujian MQTT: $printedSuccess")
        } else {
            Log.w("PrintQueueViewModel", "Pencetak belum bersambung semasa ujian cetakan MQTT diterima.")
        }

        addPrintRequest(
            PrintRequest(
                id = UUID.randomUUID().toString(),
                data = testText,
                isPrinted = printedSuccess
            )
        )
    }

    // ── Print Request Management ──

    /** Tambah print request baru (dari MQTT atau WebSocket). */
    fun addPrintRequest(request: PrintRequest) {
        printRequests = printRequests.filter { it.id != request.id } + request
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

                val alreadyPrinted = (cartIds.isNotEmpty() && targetItems.all { it.printed || savedPrintedIds.contains(it.cartId) }) ||
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
        markAsPrinted(cartIds)

        // Hantar cart_id ke MQTT pos/order/printed supaya backend Go kemas kini printed = true dalam DB
        if (cartIds.isNotEmpty()) {
            mqttManager.publishPrintedCartIds(cartIds)
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
                            if (req.cartIds.isNotEmpty()) {
                                markAsPrinted(req.cartIds)
                                mqttManager.publishPrintedCartIds(req.cartIds)
                            }
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
        currentUserId = null
        savedPrintedIds = emptySet()
        isMqttInitialized = false
    }
}
