package com.goorder.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class OrderSessionDto(
    val id: Long? = null,
    val hashid: String? = null,
    @SerialName("table_session_id") val tableSessionId: Long? = null,
    @SerialName("table_session") val tableSession: TableSessionDto? = null,
    @SerialName("user_id") val userId: Long? = null,
    val user: OrderUserDto? = null,
    @SerialName("order_type") val orderType: String? = null,
    val items: List<OrderMenuDto>? = emptyList(),
    @SerialName("round_number") val roundNumber: Int? = null,
    val status: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class OrderUserDto(
    val id: Long? = null,
    val hashid: String? = null,
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class TableSessionDto(
    val id: Long? = null,
    val hashid: String? = null,
    @SerialName("table_meja_id") val tableMejaId: Long? = null,
    @SerialName("table_meja") val tableMeja: TableMejaDto? = null,
    @SerialName("user_id") val userId: Long? = null,
    @SerialName("total_amount") val totalAmount: Double? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    val status: String? = null
)

@Serializable
data class TableMejaDto(
    val id: Long? = null,
    val hashid: String? = null,
    @SerialName("outlet_id") val outletId: Long? = null,
    val name: String? = null,
    val desc: String? = null
)

@Serializable
data class OrderMenuDto(
    val id: Long? = null,
    val hashid: String? = null,
    @SerialName("cart_id") val cartId: String? = null,
    @SerialName("order_session_id") val orderSessionId: Long? = null,
    @SerialName("menu_id") val menuId: Long? = null,
    val name: String? = null,
    val menu: MenuDto? = null,
    val quantity: Int = 1,
    @SerialName("unit_price") val unitPrice: Double? = null,
    @SerialName("total_price") val totalPrice: Double? = null,
    val notes: String? = null,
    @SerialName("note_pay") val notePay: Boolean? = false,
    val status: String? = null,
    @SerialName("add_ons") val addOns: List<OrderMenuAddonDto>? = emptyList(),
    val printer: List<StaffOutletResponse>? = emptyList()
)

@Serializable
data class MenuDto(
    val id: Long? = null,
    val hashid: String? = null,
    val name: String? = null,
    val type: String? = null,
    val desc: String? = null,
    val price: Double? = null
)

@Serializable
data class OrderMenuAddonDto(
    val id: Long? = null,
    val hashid: String? = null,
    @SerialName("order_menu_id") val orderMenuId: Long? = null,
    @SerialName("addon_menu_id") val addonMenuId: Long? = null,
    @SerialName("addon_menu") val addonMenu: AddonMenuDto? = null,
    val quantity: Int = 1,
    @SerialName("unit_price") val unitPrice: Double? = null,
    val status: Boolean = true
)

@Serializable
data class AddonMenuDto(
    val id: Long? = null,
    val hashid: String? = null,
    @SerialName("add_on_name") val addOnName: String? = null,
    @SerialName("add_on_desc") val addOnDesc: String? = null,
    @SerialName("add_on_price") val addOnPrice: Double? = null,
    val status: Boolean = true
)

@Serializable
data class StaffOutletResponse(
    val id: Long? = null,
    val hashid: String? = null,
    @SerialName("outlet_id") val outletId: Long? = null,
    val outlet: OutletDto? = null,
    @SerialName("user_id") val userId: Long? = null,
    val user: OrderUserDto? = null,
    val main: Boolean = false,
    @SerialName("on_duty") val onDuty: Boolean = false
)

@Serializable
data class OutletDto(
    val id: Long? = null,
    val hashid: String? = null,
    val name: String? = null,
    val domain: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val status: Boolean = true
)

@Serializable
data class UpdateOrderStatusRequest(
    @SerialName("order_id") val orderId: Long,
    val status: String
)

fun OrderSessionDto.formatTime(): String {
    if (createdAt.isNullOrBlank()) return "-"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        inputFormat.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputFormat.parse(createdAt.take(19)) ?: return "-"
        val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        outputFormat.format(date)
    } catch (_: Exception) {
        createdAt.takeLast(8)
    }
}

fun OrderMenuDto.calculateSubtotal(): Double {
    if (totalPrice != null && totalPrice > 0) return totalPrice
    val base = (unitPrice ?: menu?.price ?: 0.0) * quantity
    val addons = addOns?.sumOf { (it.unitPrice ?: it.addonMenu?.addOnPrice ?: 0.0) * it.quantity } ?: 0.0
    return base + addons
}

fun OrderSessionDto.calculateTotal(): Double {
    return items?.sumOf { it.calculateSubtotal() } ?: 0.0
}

fun OrderSessionDto.totalItemCount(): Int {
    return items?.sumOf { it.quantity } ?: 0
}

fun OrderSessionDto.getDisplayTableName(): String {
    val rawTableName = tableSession?.tableMeja?.name
    val isTakeaway = orderType.equals("takeaway", ignoreCase = true)
    val hasValidTable = !rawTableName.isNullOrBlank() && !rawTableName.equals("TAKEAWAY", ignoreCase = true)

    return when {
        isTakeaway && hasValidTable -> "TAKEAWAY ($rawTableName)"
        isTakeaway -> "TAKEAWAY"
        else -> rawTableName ?: "Pesanan Meja"
    }
}

fun OrderMenuDto.matchesPrinterUser(targetUserId: String?): Boolean {
    if (targetUserId.isNullOrBlank()) return true
    val printers = printer ?: return false
    if (printers.isEmpty()) return false
    return printers.any { staff ->
        val sUserId = staff.userId?.toString()?.takeIf { it.isNotBlank() && it != "0" }
            ?: staff.user?.id?.toString()
        val sHashId = staff.user?.hashid
        (sUserId != null && sUserId == targetUserId) || (sHashId != null && sHashId == targetUserId)
    }
}

fun OrderSessionDto.isOrderPrinted(printedIds: Set<String>, filterUserId: String? = null): Boolean {
    val targetItems = if (!filterUserId.isNullOrBlank()) {
        val matched = items?.filter { it.matchesPrinterUser(filterUserId) } ?: emptyList()
        if (matched.isNotEmpty()) matched else (items ?: emptyList())
    } else {
        items ?: emptyList()
    }
    val cartIds = targetItems.mapNotNull { it.cartId }.filter { it.isNotBlank() }
    if (cartIds.isNotEmpty() && cartIds.all { printedIds.contains(it) }) {
        return true
    }
    return id != null && printedIds.contains(id.toString())
}

fun OrderSessionDto.toPrintText(filterUserId: String? = null): String? {
    val targetItems = if (!filterUserId.isNullOrBlank()) {
        items?.filter { it.matchesPrinterUser(filterUserId) } ?: emptyList()
    } else {
        items ?: emptyList()
    }

    if (targetItems.isEmpty()) {
        return null
    }

    val tableName = getDisplayTableName()
    val waiterName = user?.name ?: "Staf #${userId ?: "-"}"
    val sb = StringBuilder()
    sb.append("================================\r\n")
    sb.append("       GOORDER PESANAN          \r\n")
    sb.append("================================\r\n")
    sb.append("Meja    : $tableName\r\n")
    sb.append("No. Pes : #${id ?: "-"}\r\n")
    sb.append("Masa    : ${formatTime()}\r\n")
    sb.append("Pelayan : $waiterName\r\n")
    sb.append("--------------------------------\r\n")
    targetItems.forEach { item ->
        val itemName = item.menu?.name ?: item.name ?: "Menu #${item.menuId}"
        sb.append("${item.quantity}x $itemName\r\n")
        item.addOns?.forEach { addon ->
            val addonName = addon.addonMenu?.addOnName ?: "Pilihan #${addon.addonMenuId}"
            sb.append("   + ${addon.quantity}x $addonName\r\n")
        }
        if (!item.notes.isNullOrBlank()) {
            sb.append("   Nota: ${item.notes}\r\n")
        }
        val subtotal = item.calculateSubtotal()
//        sb.append("   Subtotal: RM ${String.format(Locale.US, "%.2f", subtotal)}\r\n")
    }
    sb.append("--------------------------------\r\n")
    val total = targetItems.sumOf { it.calculateSubtotal() }
    val totalCount = targetItems.sumOf { it.quantity }
//    sb.append("Jumlah  : RM ${String.format(Locale.US, "%.2f", total)} ($totalCount item)\r\n")
    sb.append("Status  : ${status ?: "PENDING"}\r\n")
    sb.append("================================\r\n\r\n\r\n")
    return sb.toString()
}

/**
 * Memformat payload JSON daripada MQTT (sama ada OrderSession atau OrderMenu)
 * dan menapis item-item yang hanya dikonfigurasikan untuk pencetak staf yang sedang login.
 * Jika tiada item yang sepadan, memulangkan null (tidak perlu cetak pada peranti ini).
 */
fun formatMqttOrderToReceipt(payload: String, currentUserId: String?): String? {
    if (currentUserId.isNullOrBlank()) return null
    val root = try {
        JSONObject(payload.trim())
    } catch (_: Exception) {
        return null
    }

    // Helper untuk semak sama ada staf dalam senarai printer sepadan dengan currentUserId
    fun isStaffMatched(printerArray: JSONArray?): Boolean {
        if (printerArray == null) return false
        for (i in 0 until printerArray.length()) {
            val staff = printerArray.optJSONObject(i) ?: continue
            val staffUserId = staff.optString("user_id").takeIf { it.isNotBlank() && it != "0" }
                ?: staff.optJSONObject("user")?.optString("id")
            val staffUserHashId = staff.optJSONObject("user")?.optString("hashid")

            if (staffUserId != null && staffUserId == currentUserId) {
                return true
            }
            if (staffUserHashId != null && staffUserHashId == currentUserId) {
                return true
            }
        }
        return false
    }

    // Helper untuk mendapatkan format nama meja
    fun extractTableName(tableSessionObj: JSONObject?, orderType: String?): String {
        val rawTableName = tableSessionObj?.optJSONObject("table_meja")?.optString("name")
        val isTakeaway = orderType.equals("takeaway", ignoreCase = true)
        val hasValidTable = !rawTableName.isNullOrBlank() && !rawTableName.equals("TAKEAWAY", ignoreCase = true)

        return when {
            isTakeaway && hasValidTable -> "TAKEAWAY ($rawTableName)"
            isTakeaway -> "TAKEAWAY"
            !rawTableName.isNullOrBlank() -> rawTableName
            else -> "Pesanan Meja"
        }
    }

    fun formatTimeString(createdAt: String?): String {
        if (createdAt.isNullOrBlank()) return "-"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(createdAt.take(19)) ?: return "-"
            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            outputFormat.format(date)
        } catch (_: Exception) {
            createdAt.takeLast(8)
        }
    }

    // ── KES 1: Payload adalah OrderSession penuh (mempunyai array "items") ──
    if (root.has("items")) {
        val itemsArray = root.optJSONArray("items") ?: return null
        val matchedItems = mutableListOf<JSONObject>()
        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.optJSONObject(i) ?: continue
            val printers = item.optJSONArray("printer")
            if (isStaffMatched(printers)) {
                matchedItems.add(item)
            }
        }

        if (matchedItems.isEmpty()) {
            // Tiada item yang ditugaskan ke pencetak staf ini
            return null
        }

        val orderId = root.optString("id", "-")
        val orderType = root.optString("order_type", "")
        val tableSession = root.optJSONObject("table_session")
        val tableName = extractTableName(tableSession, orderType)
        val timeStr = formatTimeString(root.optString("created_at"))
        val waiterName = root.optJSONObject("user")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: root.optString("user_id").takeIf { it.isNotBlank() && it != "0" }?.let { "Staf #$it" }
            ?: "Staf"

        val sb = StringBuilder()
        sb.append("================================\r\n")
        sb.append("         PESANAN DAPUR          \r\n")
        sb.append("================================\r\n")
        sb.append("Meja    : $tableName\r\n")
        sb.append("No. Pes : #$orderId\r\n")
        if (timeStr != "-") {
            sb.append("Masa    : $timeStr\r\n")
        }
        sb.append("Pelayan : $waiterName\r\n")
        sb.append("--------------------------------\r\n")

        matchedItems.forEach { item ->
            val itemName = item.optJSONObject("menu")?.optString("name")
                ?: item.optString("name").takeIf { it.isNotBlank() }
                ?: "Menu #${item.optString("menu_id", "-")}"
            val qty = item.optInt("quantity", 1)
            val notes = item.optString("notes", "")

            sb.append("${qty}x $itemName\r\n")

            val addOns = item.optJSONArray("add_ons")
            if (addOns != null) {
                for (j in 0 until addOns.length()) {
                    val addon = addOns.optJSONObject(j) ?: continue
                    val addonName = addon.optJSONObject("addon_menu")?.optString("add_on_name")
                        ?: "Pilihan #${addon.optString("addon_menu_id")}"
                    val addonQty = addon.optInt("quantity", 1)
                    sb.append("   + ${addonQty}x $addonName\r\n")
                }
            }

            if (notes.isNotBlank()) {
                sb.append("   Nota: $notes\r\n")
            }
        }

        sb.append("================================\r\n\r\n\r\n")
        return sb.toString()
    }

    // ── KES 2: Payload adalah OrderMenu tunggal ──
    val printers = root.optJSONArray("printer")
    if (isStaffMatched(printers)) {
        val itemName = root.optJSONObject("menu")?.optString("name")
            ?: root.optString("name").takeIf { it.isNotBlank() }
            ?: "Menu #${root.optString("menu_id", "-")}"
        val qty = root.optInt("quantity", 1)
        val notes = root.optString("notes", "")

        val orderSession = root.optJSONObject("order_session")
        val orderId = orderSession?.optString("id") ?: root.optString("order_session_id", "-")
        val orderType = orderSession?.optString("order_type", "")
        val tableSession = orderSession?.optJSONObject("table_session")
        val tableName = extractTableName(tableSession, orderType)
        val timeStr = formatTimeString(orderSession?.optString("created_at") ?: root.optString("created_at"))
        val waiterName = orderSession?.optJSONObject("user")?.optString("name")?.takeIf { it.isNotBlank() }
            ?: orderSession?.optString("user_id")?.takeIf { it.isNotBlank() && it != "0" }?.let { "Staf #$it" }

        val sb = StringBuilder()
        sb.append("================================\r\n")
        sb.append("      PESANAN DAPUR (ITEM)      \r\n")
        sb.append("================================\r\n")
        if (tableName != "Pesanan Meja" || orderId != "-") {
            sb.append("Meja    : $tableName\r\n")
            sb.append("No. Pes : #$orderId\r\n")
            if (timeStr != "-") {
                sb.append("Masa    : $timeStr\r\n")
            }
            if (waiterName != null) {
                sb.append("Pelayan : $waiterName\r\n")
            }
            sb.append("--------------------------------\r\n")
        }
        sb.append("${qty}x $itemName\r\n")

        val addOns = root.optJSONArray("add_ons")
        if (addOns != null) {
            for (j in 0 until addOns.length()) {
                val addon = addOns.optJSONObject(j) ?: continue
                val addonName = addon.optJSONObject("addon_menu")?.optString("add_on_name")
                    ?: "Pilihan #${addon.optString("addon_menu_id")}"
                val addonQty = addon.optInt("quantity", 1)
                sb.append("   + ${addonQty}x $addonName\r\n")
            }
        }

        if (notes.isNotBlank()) {
            sb.append("   Nota: $notes\r\n")
        }

        sb.append("================================\r\n\r\n\r\n")
        return sb.toString()
    }

    return null
}

/**
 * Mengekstrak senarai cart_id daripada payload MQTT bagi item-item yang
 * sepadan dengan staf pencetak yang sedang login.
 */
fun extractCartIdsFromMqttPayload(payload: String, currentUserId: String?): List<String> {
    if (currentUserId.isNullOrBlank()) return emptyList()
    val root = try {
        JSONObject(payload.trim())
    } catch (_: Exception) {
        return emptyList()
    }

    fun isStaffMatched(printerArray: JSONArray?): Boolean {
        if (printerArray == null) return false
        for (i in 0 until printerArray.length()) {
            val staff = printerArray.optJSONObject(i) ?: continue
            val staffUserId = staff.optString("user_id").takeIf { it.isNotBlank() && it != "0" }
                ?: staff.optJSONObject("user")?.optString("id")
            val staffUserHashId = staff.optJSONObject("user")?.optString("hashid")

            if ((staffUserId != null && staffUserId == currentUserId) ||
                (staffUserHashId != null && staffUserHashId == currentUserId)) {
                return true
            }
        }
        return false
    }

    val cartIds = mutableListOf<String>()

    if (root.has("items")) {
        val itemsArray = root.optJSONArray("items")
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.optJSONObject(i) ?: continue
                val printers = item.optJSONArray("printer")
                if (isStaffMatched(printers)) {
                    val cid = item.optString("cart_id").takeIf { it.isNotBlank() }
                    if (cid != null) cartIds.add(cid)
                }
            }
        }
    } else {
        val printers = root.optJSONArray("printer")
        if (isStaffMatched(printers)) {
            val cid = root.optString("cart_id").takeIf { it.isNotBlank() }
            if (cid != null) cartIds.add(cid)
        }
    }

    return cartIds
}
