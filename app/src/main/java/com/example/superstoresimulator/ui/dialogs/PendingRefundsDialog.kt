package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RefundRequest
import com.example.superstoresimulator.domain.items.ItemDao
import java.util.Locale
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.TextSecondary

@Composable
fun PendingRefundsDialog(
    pending: List<RefundRequest>,
    itemDao: ItemDao,
    onDismiss: () -> Unit,
    onProcessLine: (refundId: Int, itemId: Int, qty: Int) -> Unit
) {
    // State to hold item name mappings
    val itemNames = remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    
    // Fetch all item names when dialog opens
    LaunchedEffect(pending) {
        try {
            val names = mutableMapOf<Int, String>()
            val uniqueItemIds = pending.flatMap { r -> r.lines.map { it.itemId } }.distinct()
            
            for (itemId in uniqueItemIds) {
                val dbItemId = "item_" + String.format(Locale.US, "%03d", itemId)
                val name = itemDao.getItemName(dbItemId)
                names[itemId] = name ?: "Item $itemId"
            }
            itemNames.value = names
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pending Refunds", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = onDismiss) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                        }
                    }

                    HorizontalDivider()

                    val refundsByTx = pending.groupBy { it.originalTransactionId }
                    if (refundsByTx.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No pending refunds")
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            refundsByTx.forEach { (txId, refunds) ->
                                item {
                                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = CardWhite)) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("Transaction #$txId", fontWeight = FontWeight.Bold)
                                            Text("${ Money(refunds.sumOf { it.subtotal.cents }) } total pending refund")
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = {
                                                    // Caller should handle processing all refunds for this transaction.
                                                    // We expose refund ids for the caller to process.
                                                    refunds.forEach { r ->
                                                        r.lines.forEach { line ->
                                                            onProcessLine(r.id, line.itemId, line.quantity)
                                                        }
                                                    }
                                                }) {
                                                    Text("Process All", color = Primary)
                                                }
                                            }
                                            refunds.forEach { r ->
                                                r.lines.forEach { line ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 6.dp)
                                                            .clickable { onProcessLine(r.id, line.itemId, 1) },
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            "${line.quantity} × ${itemNames.value[line.itemId] ?: "Item ${line.itemId}"} — ${line.unitPrice}"
                                                        )
                                                        Text(
                                                            "Process 1",
                                                            color = Primary,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
