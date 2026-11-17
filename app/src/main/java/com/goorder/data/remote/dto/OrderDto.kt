package com.goorder.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    @SerialName("add_ons") val addOns: List<OrderMenuAddonDto>? = emptyList()
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

fun OrderSessionDto.toPrintText(): String {
    val tableName = tableSession?.tableMeja?.name
        ?: if (orderType.equals("takeaway", ignoreCase = true)) "Bungkus / Takeaway" else "Pesanan Meja"
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
    items?.forEach { item ->
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
        sb.append("   Subtotal: RM ${String.format(Locale.US, "%.2f", subtotal)}\r\n")
    }
    sb.append("--------------------------------\r\n")
    val total = calculateTotal()
    val totalCount = totalItemCount()
    sb.append("Jumlah  : RM ${String.format(Locale.US, "%.2f", total)} ($totalCount item)\r\n")
    sb.append("Status  : ${status ?: "PENDING"}\r\n")
    sb.append("================================\r\n")
    return sb.toString()
}
