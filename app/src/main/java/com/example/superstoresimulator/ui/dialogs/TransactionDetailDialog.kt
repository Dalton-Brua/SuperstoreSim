package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionLine
import com.example.superstoresimulator.domain.items.ItemDao
import com.yourapp.ui.theme.GameButtonStyles
import java.util.Locale
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.ProgressBarIndicator
import com.example.superstoresimulator.ui.theme.CardWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailDialog(
    transactionLines: List<TransactionLine>,
    itemDao: ItemDao,
    onDismiss: () -> Unit,
    onViewItem: (Int) -> Unit,
) {
    // State to hold item name mappings
    val itemNames = remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    
    // Fetch item names when dialog opens
    LaunchedEffect(transactionLines) {
        try {
            val names = mutableMapOf<Int, String>()
            for (line in transactionLines) {
                // Convert integer itemId to database format "item_XXX"
                val dbItemId = "item_" + String.format(Locale.US, "%03d", line.itemId)
                val name = itemDao.getItemName(dbItemId)
                names[line.itemId] = name ?: "Item ${line.itemId}"
            }
            itemNames.value = names
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDismiss() }
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column {
                    // Header with background
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LightBackground)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transaction Details",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDark
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary
                            )
                        }
                    }

                    // Scrollable list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        // Incomplete first
                        items(
                            items = transactionLines
                                .filter { it.rungQty < it.quantity }
                                .sortedBy { it.itemId },
                            key = { it.itemId }
                        ) { line ->
                            TransactionLineCard(
                                line = line,
                                itemName = itemNames.value[line.itemId] ?: "Item ${line.itemId}",
                                onViewItem = { onViewItem(line.itemId) },
                            )

                        }

                        // Completed last
                        items(
                            items = transactionLines
                                .filter { it.rungQty >= it.quantity }
                                .sortedBy { it.itemId },
                            key = { it.itemId }
                        ) { line ->
                            TransactionLineCard(
                                line = line,
                                itemName = itemNames.value[line.itemId] ?: "Item ${line.itemId}",
                                onViewItem = { onViewItem(line.itemId) },
                            )
                        }
                    }

                    // Progress summary
                    HorizontalDivider(color = ProgressBarTrack)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(CardWhite),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val totalRequired = transactionLines.sumOf { it.quantity }
                        val totalRung = transactionLines.sumOf { it.rungQty }
                        Text("$totalRung / $totalRequired complete", color = PrimaryDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Overload to show completed/archived sales transaction details (read-only)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailDialog(
    transaction: Transaction,
    itemDao: ItemDao,
    onDismiss: () -> Unit,
) {
    // State to hold item name mappings
    val itemNames = remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    
    // Fetch item names when dialog opens
    LaunchedEffect(transaction.lines) {
        try {
            val names = mutableMapOf<Int, String>()
            for (line in transaction.lines) {
                // Convert integer itemId to database format "item_XXX"
                val dbItemId = "item_" + String.format(Locale.US, "%03d", line.itemId)
                val name = itemDao.getItemName(dbItemId)
                names[line.itemId] = name ?: "Item ${line.itemId}"
            }
            itemNames.value = names
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

            // Transparent, clickable scrim that dismisses the dialog when tapped
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onDismiss() }
            )

            // Scrollable bubble (read-only) — increase size to match primary dialog
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = LightBackground),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column {
                    // Header with dismiss
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LightBackground)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transaction Details",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryDark
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary
                            )
                        }
                    }

                    // Scrollable sold line list
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(transaction.lines) { line: TransactionLine ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = LightBackground)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = itemNames.value[line.itemId] ?: "Item ${line.itemId}",
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        color = PrimaryDark
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${line.quantity} × ${line.unitPrice}",
                                            color = PrimaryDark
                                        )
                                        Text("Line total: ${line.lineTotal}", color = TextSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Summary
                    HorizontalDivider(color = ProgressBarTrack)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardWhite)
                            .padding(16.dp)
                    ) {
                        Text("Subtotal: ${transaction.subtotal}", color = PrimaryDark)
                        Text("Tax: ${transaction.tax}", color = PrimaryDark)
                        Text("Total: ${transaction.totalEarned}", fontWeight = FontWeight.Bold, color = PrimaryDark)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionLineCard(
    line: TransactionLine,
    itemName: String,
    onViewItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    val remaining = line.quantity - line.rungQty

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = LightBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp)) {

            // Top row: Item name + rung/total + remaining
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = itemName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    color = PrimaryDark
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${line.rungQty}/${line.quantity}",
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )

                    Text(
                        text = "$remaining left",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (remaining > 0) Destructive else Secondary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            val progress = if (line.quantity == 0) 0f else line.rungQty.toFloat() / line.quantity
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = ProgressBarIndicator,
                trackColor = ProgressBarTrack
            )

            Spacer(Modifier.height(12.dp))

            // Action button
            OutlinedButton(
                onClick = { onViewItem() },
                modifier = Modifier.fillMaxWidth(),
                colors = GameButtonStyles.outlinedPrimaryColors(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("View Item", fontWeight = FontWeight.Medium)
            }
        }
    }
}
