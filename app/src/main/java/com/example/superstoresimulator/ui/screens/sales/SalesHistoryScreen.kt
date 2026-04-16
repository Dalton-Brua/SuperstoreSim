package com.example.superstoresimulator.ui.screens.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.dialogs.TransactionDetailDialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.superstoresimulator.ui.viewmodels.ItemViewModel
import com.example.superstoresimulator.ui.state.HistoryUIState
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.PrimaryDark
import java.util.Locale

@Composable
fun SalesHistoryScreen(
    state: HistoryUIState,
    modifier: Modifier = Modifier,
    itemViewModel: ItemViewModel = viewModel(),
    itemDao: ItemDao
) {
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        PaginatedHistoryList(
            salesHistory = state.salesHistory,
            totalTaxCollected = state.totalTaxCollected,
            onTransactionSelect = { selectedTransaction = it },
            itemViewModel = itemViewModel
        )
        selectedTransaction?.let { tx ->
            TransactionDetailDialog(transaction = tx, itemDao = itemDao, onDismiss = { selectedTransaction = null })
        }
    }
}

@Composable
private fun PaginatedHistoryList(
    salesHistory: List<Transaction>,
    totalTaxCollected: Money,
    onTransactionSelect: (Transaction) -> Unit,
    itemViewModel: ItemViewModel
) {
    val pageSize = 10
    val sortedSalesHistory = remember(salesHistory) { salesHistory.sortedByDescending { it.completedAt } }
    var itemsToShow by remember { mutableStateOf(pageSize.coerceAtMost(sortedSalesHistory.size)) }
    val listState = rememberLazyListState()

    LaunchedEffect(sortedSalesHistory.size) { itemsToShow = pageSize.coerceAtMost(sortedSalesHistory.size) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisible ->
                if (lastVisible != null && lastVisible >= itemsToShow - 3 && itemsToShow < sortedSalesHistory.size) {
                    itemsToShow = (itemsToShow + pageSize).coerceAtMost(sortedSalesHistory.size)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        stickyHeader {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightBackground)
                    .padding(bottom = 8.dp)
            ) {
                ScreenHeader(title = "Sales History")
                
                // Tax badge
                Card(colors = CardDefaults.cardColors(containerColor = Secondary)) {
                    Text(
                        "Total Tax: $totalTaxCollected",
                        color = TextWhite,
                        modifier = Modifier.padding(8.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (sortedSalesHistory.isEmpty()) {
            item {
                Text(
                    text = "No transactions yet.",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(sortedSalesHistory.take(itemsToShow), key = { it.id }) { tx ->
                if (tx.subtotal < Money.ZERO) {
                    RefundCard(transaction = tx, onClick = { onTransactionSelect(tx) }, itemViewModel = itemViewModel)
                } else {
                    TransactionCard(transaction = tx, onClick = { onTransactionSelect(tx) }, itemViewModel = itemViewModel)
                }
            }
        }
    }

}

@Composable
private fun TransactionCard(transaction: Transaction, onClick: () -> Unit, itemViewModel: ItemViewModel) {
    val items = remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(transaction.lines) {
        val fetchedItems = transaction.lines.map { line ->
            // Convert integer itemId to database format "item_XXX"
            val dbItemId = "item_" + String.format(Locale.US, "%03d", line.itemId)
            itemViewModel.getItemById(dbItemId)?.name ?: "Unknown Item"
        }
        items.value = fetchedItems
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Transaction #${transaction.id}",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = PrimaryDark
            )

            Text(
                text = "Total: ${transaction.totalEarned}",
                color = Color(0xFF1E40AF),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(4.dp))

            items.value.forEachIndexed { index, itemName ->
                val line = transaction.lines[index]
                Text(
                    text = "$itemName: ${line.quantity} × ${line.unitPrice} = ${line.lineTotal}",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }

            Text(
                text = "Tax: ${transaction.tax}",
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun RefundCard(transaction: Transaction, onClick: () -> Unit, itemViewModel: ItemViewModel) {
    val items = remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(transaction.lines) {
        val fetchedItems = transaction.lines.map { line ->
            // Convert integer itemId to database format "item_XXX"
            val dbItemId = "item_" + String.format(Locale.US, "%03d", line.itemId)
            itemViewModel.getItemById(dbItemId)?.name ?: "Unknown Item"
        }
        items.value = fetchedItems
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3F2)),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "Refund #${transaction.id}",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Color(0xFFB91C1C)
            )

            Text(
                text = "Total refunded: ${-transaction.subtotal}",
                color = Color(0xFFB91C1C),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(4.dp))

            items.value.forEachIndexed { index, itemName ->
                val line = transaction.lines[index]
                Text(
                    text = "$itemName: ${line.quantity} × ${line.unitPrice} = ${line.lineTotal}",
                    fontSize = 13.sp,
                    color = Color(0xFF64748B)
                )
            }

            Text(
                text = "Tax refunded: ${-transaction.tax}",
                fontSize = 13.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

