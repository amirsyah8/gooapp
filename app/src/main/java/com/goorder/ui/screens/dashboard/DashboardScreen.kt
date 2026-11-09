package com.goorder.ui.screens.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.goorder.domain.model.PrintRequest
import com.goorder.ui.theme.LoginTheme
import com.goorder.ui.theme.SuccessGreen

@Composable
fun DashboardScreen(
    printRequests: List<PrintRequest>,
    onClearRequests: () -> Unit,
    onManualPrint: (PrintRequest) -> Unit,
    onDeleteRequest: (String) -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (printRequests.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val printedCount = printRequests.count { it.isPrinted }
                Text(text = "Permintaan Cetakan (${printRequests.size})", fontWeight = FontWeight.Bold)
                if (printedCount > 0) {
                    TextButton(onClick = onClearRequests) {
                        Text("Padam Yang Selesai")
                    }
                }
            }
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(printRequests.reversed()) { request ->
                    PrintRequestCard(
                        request = request,
                        onPrintClick = { onManualPrint(request) },
                        onDeleteClick = { onDeleteRequest(request.id) }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Text(text = "Tiada permintaan cetakan.", color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Logout")
        }
    }
}

@Composable
fun PrintRequestCard(
    request: PrintRequest,
    onPrintClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (request.isPrinted) Icons.Default.CheckCircle else Icons.Default.Print,
                contentDescription = null,
                tint = if (request.isPrinted) SuccessGreen else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (request.isPrinted) "Sudah Dicetak" else "Belum Dicetak",
                    fontWeight = FontWeight.Bold,
                    color = if (request.isPrinted) SuccessGreen else MaterialTheme.colorScheme.primary
                )
                Text(text = request.data, style = MaterialTheme.typography.bodySmall)
            }
            if (!request.isPrinted) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onPrintClick) {
                        Icon(imageVector = Icons.Default.Print, contentDescription = "Cetak")
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete, 
                            contentDescription = "Padam",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    LoginTheme {
        DashboardScreen(
            printRequests = listOf(
                PrintRequest(data = "Item 1", isPrinted = true),
                PrintRequest(data = "Item 2", isPrinted = false)
            ),
            onClearRequests = {},
            onManualPrint = {},
            onDeleteRequest = {},
            onLogout = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PrintRequestCardPreview() {
    LoginTheme {
        PrintRequestCard(
            request = PrintRequest(data = "Contoh Data Cetakan", isPrinted = false),
            onPrintClick = {},
            onDeleteClick = {}
        )
    }
}

