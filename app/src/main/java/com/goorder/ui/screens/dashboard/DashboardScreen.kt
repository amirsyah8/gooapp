package com.goorder.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.goorder.data.remote.dto.*
import java.util.Locale

enum class OrderTab(val label: String) {
    ACTIVE("Aktif Dapur"),
    PENDING("Menunggu"),
    COOKING("Dimasak"),
    WAITING_SERVE("Siap Kaunter"),
    SERVED("Dihidang"),
    ALL("Semua")
}

@Composable
fun DashboardScreen(
    orders: List<OrderSessionDto>,
    isConnected: Boolean,
    printedOrderIds: Set<String> = emptySet(),
    currentUserId: String? = null,
    onStatusChange: (Long, String) -> Unit,
    onPrintOrder: (OrderSessionDto) -> Unit,
    onRefresh: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(OrderTab.ACTIVE) }
    var selectedOrderForDetail by remember { mutableStateOf<OrderSessionDto?>(null) }

    // Statistics calculation matching goreact
    val pendingCount = remember(orders) {
        orders.count { it.status.equals("PENDING", ignoreCase = true) }
    }
    val cookingCount = remember(orders) {
        orders.count { it.status.equals("COOKING", ignoreCase = true) }
    }
    val waitingServeCount = remember(orders) {
        orders.count {
            it.status.equals("WAITING_SERVE", ignoreCase = true) ||
            it.status.equals("READY_TO_SERVE", ignoreCase = true)
        }
    }
    val servedCount = remember(orders) {
        orders.count { it.status.equals("SERVED", ignoreCase = true) }
    }
    val activeCount = remember(pendingCount, cookingCount, waitingServeCount) {
        pendingCount + cookingCount + waitingServeCount
    }
    val totalCount = orders.size

    // Filter & search logic
    val filteredOrders = remember(orders, selectedTab, searchQuery) {
        orders.filter { order ->
            val status = order.status?.uppercase() ?: "PENDING"
            val matchesTab = when (selectedTab) {
                OrderTab.ACTIVE -> status in listOf("PENDING", "COOKING", "WAITING_SERVE", "READY_TO_SERVE")
                OrderTab.PENDING -> status == "PENDING"
                OrderTab.COOKING -> status == "COOKING"
                OrderTab.WAITING_SERVE -> status in listOf("WAITING_SERVE", "READY_TO_SERVE")
                OrderTab.SERVED -> status == "SERVED"
                OrderTab.ALL -> true
            }

            if (!matchesTab) return@filter false

            if (searchQuery.isBlank()) return@filter true

            val q = searchQuery.trim().lowercase()
            val tableName = order.getDisplayTableName().lowercase()
            val waiterName = order.user?.name?.lowercase() ?: ""
            val hashid = order.hashid?.lowercase() ?: ""
            val orderId = order.id?.toString() ?: ""
            val itemMatch = order.items?.any {
                (it.menu?.name ?: it.name ?: "").lowercase().contains(q)
            } == true

            tableName.contains(q) ||
            waiterName.contains(q) ||
            hashid.contains(q) ||
            orderId.contains(q) ||
            itemMatch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ── Quick Stat Cards (Full Width Row) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StatFilterChip(
                modifier = Modifier.weight(1f),
                title = "",
                count = activeCount,
                icon = Icons.Default.LocalFireDepartment,
                accentColor = Color(0xFFF97316),
                isSelected = selectedTab == OrderTab.ACTIVE,
                onClick = { selectedTab = OrderTab.ACTIVE }
            )
            StatFilterChip(
                modifier = Modifier.weight(1f),
                title = "",
                count = pendingCount,
                icon = Icons.Default.Schedule,
                accentColor = Color(0xFFF59E0B),
                isSelected = selectedTab == OrderTab.PENDING,
                onClick = { selectedTab = OrderTab.PENDING }
            )
            StatFilterChip(
                modifier = Modifier.weight(1f),
                title = "",
                count = cookingCount,
                icon = Icons.Default.RestaurantMenu,
                accentColor = Color(0xFF3B82F6),
                isSelected = selectedTab == OrderTab.COOKING,
                onClick = { selectedTab = OrderTab.COOKING }
            )
            StatFilterChip(
                modifier = Modifier.weight(1f),
                title = "",
                count = waitingServeCount,
                icon = Icons.Default.NotificationsActive,
                accentColor = Color(0xFF06B6D4),
                isSelected = selectedTab == OrderTab.WAITING_SERVE,
                onClick = { selectedTab = OrderTab.WAITING_SERVE }
            )
            StatFilterChip(
                modifier = Modifier.weight(1f),
                title = "",
                count = servedCount,
                icon = Icons.Default.CheckCircle,
                accentColor = Color(0xFF10B981),
                isSelected = selectedTab == OrderTab.SERVED,
                onClick = { selectedTab = OrderTab.SERVED }
            )
            StatFilterChip(
                modifier = Modifier.weight(1f),
                title = "",
                count = totalCount,
                icon = Icons.Default.Layers,
                accentColor = MaterialTheme.colorScheme.primary,
                isSelected = selectedTab == OrderTab.ALL,
                onClick = { selectedTab = OrderTab.ALL }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Search Input ──
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Cari meja, kod pesanan, menu...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Cari",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kosongkan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ── Header Summary Bar ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedTab.label} (${filteredOrders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Jumlah Pesanan: ${orders.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Main Orders List / Empty State ──
        if (filteredOrders.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredOrders, key = { it.id ?: 0L }) { order ->
                    val isPrinted = order.isOrderPrinted(printedOrderIds, filterUserId = currentUserId)
                    OrderCard(
                        order = order,
                        isConnected = isConnected,
                        isPrinted = isPrinted,
                        printedOrderIds = printedOrderIds,
                        onCardClick = { selectedOrderForDetail = order },
                        onPrint = { onPrintOrder(order) },
                        onStatusChange = onStatusChange
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Tiada Pesanan Dijumpai",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (searchQuery.isNotEmpty())
                                "Tiada pesanan yang sepadan dengan carian \"$searchQuery\"."
                            else if (selectedTab == OrderTab.ACTIVE)
                                "Semua pesanan aktif telah siap atau tiada pesanan menunggu buat masa ini."
                            else
                                "Tiada rekod pesanan untuk status \"${selectedTab.label}\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { searchQuery = "" },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Kosongkan Carian", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Order Detail Modal Dialog ──
    selectedOrderForDetail?.let { order ->
        OrderDetailDialog(
            order = order,
            isPrinted = order.isOrderPrinted(printedOrderIds, filterUserId = currentUserId),
            printedOrderIds = printedOrderIds,
            onDismiss = { selectedOrderForDetail = null },
            onPrint = { onPrintOrder(order) },
            onStatusChange = { orderId, newStatus ->
                onStatusChange(orderId, newStatus)
                selectedOrderForDetail = null
            }
        )
    }
}

@Composable
fun StatFilterChip(
    modifier: Modifier = Modifier,
    title: String = "",
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = modifier.height(38.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title.ifBlank { null },
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            if (title.isNotBlank()) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(3.dp))
            Surface(
                shape = CircleShape,
                color = if (isSelected) accentColor else accentColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else accentColor,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun OrderCard(
    order: OrderSessionDto,
    isConnected: Boolean,
    isPrinted: Boolean = false,
    printedOrderIds: Set<String> = emptySet(),
    onCardClick: () -> Unit,
    onPrint: () -> Unit,
    onStatusChange: (Long, String) -> Unit
) {
    val status = order.status?.uppercase() ?: "PENDING"
    val accentColor = when (status) {
        "PENDING" -> Color(0xFFF59E0B)
        "COOKING" -> Color(0xFF3B82F6)
        "WAITING_SERVE", "READY_TO_SERVE" -> Color(0xFF06B6D4)
        "SERVED" -> Color(0xFF10B981)
        "COMPLETED" -> Color(0xFF8B5CF6)
        "CANCELLED" -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.primary
    }

    val isTakeaway = order.orderType.equals("takeaway", ignoreCase = true)
    val tableName = order.getDisplayTableName()
    val waiterName = order.user?.name ?: "Staf #${order.userId ?: "-"}"
    val items = order.items ?: emptyList()
    val previewItems = items.take(3)
    val remainingCount = items.size - previewItems.size
    val totalPrice = order.calculateTotal()
    val totalCount = order.totalItemCount()

    // Next Action logic matching goreact
    val nextAction: Pair<String, String>? = when (status) {
        "PENDING" -> Pair("Mula Masak", "COOKING")
        "COOKING" -> Pair("Siap Masak", "WAITING_SERVE")
        "WAITING_SERVE", "READY_TO_SERVE" -> Pair("Hidang ke Meja", "SERVED")
        "SERVED" -> Pair("Selesai", "COMPLETED")
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left colored stripe
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // ── Top Header Row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isTakeaway) Icons.Default.ShoppingBag else Icons.Default.Restaurant,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = tableName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = "• #${order.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if ((order.roundNumber ?: 1) > 1) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.padding(start = 2.dp)
                                    ) {
                                        Text(
                                            text = "P${order.roundNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = order.formatTime(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Print Button & Status Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Print Button with Printed Status Indicator
                        Surface(
                            onClick = onPrint,
                            shape = RoundedCornerShape(8.dp),
                            color = when {
                                isPrinted -> Color(0xFF10B981).copy(alpha = 0.12f)
                                isConnected -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = when {
                                    isPrinted -> Color(0xFF10B981).copy(alpha = 0.4f)
                                    isConnected -> Color(0xFFF59E0B).copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                }
                            ),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = if (isPrinted) "Sudah Dicetak" else "Belum Dicetak",
                                    tint = when {
                                        isPrinted -> Color(0xFF10B981)
                                        isConnected -> Color(0xFFF59E0B)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    },
                                    modifier = Modifier.size(17.dp)
                                )

                                // Indicator dot (Green = Dicetak, Amber = Belum Cetak)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isPrinted) Color(0xFF10B981) else Color(0xFFF59E0B)
                                        )
                                )
                            }
                        }

                        OrderStatusBadge(status = status, color = accentColor)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // ── Items List Preview ──
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    previewItems.forEach { item ->
                        val subtotal = item.calculateSubtotal()
                        val itemName = item.menu?.name ?: item.name ?: "Menu #${item.menuId}"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.padding(top = 1.dp)
                                ) {
                                    Text(
                                        text = "${item.quantity}×",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }

                                val isItemPrinted = item.isItemPrinted(printedOrderIds)
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = if (isItemPrinted) "Sudah dicetak" else "Belum dicetak",
                                    tint = if (isItemPrinted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .size(13.dp)
                                        .padding(top = 2.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itemName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Addons
                                    item.addOns?.forEach { addon ->
                                        val addonName = addon.addonMenu?.addOnName ?: "Pilihan #${addon.addonMenuId}"
                                        Text(
                                            text = "- $addonName${if (addon.quantity > 1) " (${addon.quantity}x)" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Notes
                                    if (!item.notes.isNullOrBlank()) {
                                        Text(
                                            text = "Nota: ${item.notes}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontStyle = FontStyle.Italic,
                                            color = Color(0xFFD97706),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "RM ${String.format(Locale.US, "%.2f", subtotal)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (remainingCount > 0) {
                        Text(
                            text = "+$remainingCount lagi menu...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                // ── Footer: Waiter + Total + Action Button ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = waiterName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "RM ${String.format(Locale.US, "%.2f", totalPrice)} ($totalCount item)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (nextAction != null && order.id != null) {
                            Button(
                                onClick = { onStatusChange(order.id, nextAction.second) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Text(
                                    text = nextAction.first,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Lihat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrderStatusBadge(status: String, color: Color) {
    val displayLabel = when (status) {
        "PENDING" -> "Menunggu"
        "COOKING" -> "Dimasak"
        "WAITING_SERVE", "READY_TO_SERVE" -> "Siap"
        "SERVED" -> "Dihidang"
        "COMPLETED" -> "Selesai"
        "CANCELLED" -> "Batal"
        else -> status
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun OrderDetailDialog(
    order: OrderSessionDto,
    isPrinted: Boolean = false,
    printedOrderIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onStatusChange: (Long, String) -> Unit
) {
    val status = order.status?.uppercase() ?: "PENDING"
    val isTakeaway = order.orderType.equals("takeaway", ignoreCase = true)
    val tableName = order.getDisplayTableName()
    val waiterName = order.user?.name ?: "Staf #${order.userId ?: "-"}"
    val items = order.items ?: emptyList()
    val totalPrice = order.calculateTotal()
    val totalCount = order.totalItemCount()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = tableName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Pesanan #${order.id} • ${order.formatTime()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isPrinted) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isPrinted) "Sudah Cetak" else "Belum Cetak",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPrinted) Color(0xFF10B981) else Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Pelayan: $waiterName",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Items list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id ?: 0L }) { item ->
                        val subtotal = item.calculateSubtotal()
                        val itemName = item.menu?.name ?: item.name ?: "Menu #${item.menuId}"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val isItemPrinted = item.isItemPrinted(printedOrderIds)
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = if (isItemPrinted) "Sudah dicetak" else "Belum dicetak",
                                        tint = if (isItemPrinted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "${item.quantity}× $itemName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "RM ${String.format(Locale.US, "%.2f", subtotal)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            item.addOns?.forEach { addon ->
                                val addonName = addon.addonMenu?.addOnName ?: "Pilihan #${addon.addonMenuId}"
                                val addonPrice = (addon.unitPrice ?: addon.addonMenu?.addOnPrice ?: 0.0) * addon.quantity
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "+ ${addon.quantity}× $addonName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "RM ${String.format(Locale.US, "%.2f", addonPrice)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (!item.notes.isNullOrBlank()) {
                                Text(
                                    text = "Nota: ${item.notes}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFFD97706),
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Summary Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Jumlah ($totalCount item):",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Text(
                        text = "RM ${String.format(Locale.US, "%.2f", totalPrice)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPrint,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPrinted) Color(0xFF10B981) else Color(0xFFF59E0B)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPrinted) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFF59E0B).copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPrinted) "Cetak Semula" else "Cetak Dapur")
                    }

                    if (order.id != null) {
                        val nextStatus = when (status) {
                            "PENDING" -> "COOKING"
                            "COOKING" -> "WAITING_SERVE"
                            "WAITING_SERVE", "READY_TO_SERVE" -> "SERVED"
                            "SERVED" -> "COMPLETED"
                            else -> null
                        }
                        if (nextStatus != null) {
                            Button(
                                onClick = { onStatusChange(order.id, nextStatus) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    when (nextStatus) {
                                        "COOKING" -> "Mula Masak"
                                        "WAITING_SERVE" -> "Siap Masak"
                                        "SERVED" -> "Hidang"
                                        "COMPLETED" -> "Selesai"
                                        else -> nextStatus
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
