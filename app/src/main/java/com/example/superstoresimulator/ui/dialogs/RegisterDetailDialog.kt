package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.ui.state.GameUiState
import com.example.superstoresimulator.ui.state.RegisterUIState
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.TextMuted
import com.yourapp.ui.theme.GameButtonStyles
import java.util.Locale

/**
 * Bottom sheet modal displaying register details: assigned cashier, active transaction,
 * and previous transaction summary.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterDetailDialog(
    register: RegisterUIState,
    state: GameUiState,
    itemDao: ItemDao,
    onDismiss: () -> Unit,
    onAssignCashier: (cashierId: Int?, registerId: Int) -> Unit = { _, _ -> },
    onAssignPlayer: (registerId: Int?) -> Unit = {},
) {
    // Always read the most current register snapshot from state so the status dot,
    // header label, and cashier info update live while the dialog is open.
    val liveRegister = state.registers.registers.find { it.registerId == register.registerId } ?: register

    // skipPartiallyExpanded prevents the sheet from snapping back to a half-height
    // position when the transaction section appears or disappears mid-session.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var assignMenuExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrimColor = Color.Black.copy(alpha = 0.32f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CardWhite)
                .padding(bottom = 32.dp)
        ) {
            // ── Header ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Status dot
                    val statusColor = when {
                        liveRegister.transactionActive -> Secondary
                        liveRegister.isManned -> Primary
                        else -> Color(0xFFCBD5E1)
                    }
                    Surface(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape),
                        color = statusColor,
                    ) {}

                    Column {
                        Text(
                            text = "Register ${register.registerId + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PrimaryDark,
                        )
                        Text(
                            text = when {
                                liveRegister.transactionActive -> "Transaction in progress"
                                liveRegister.isManned -> "Staffed"
                                else -> "Unassigned"
                            },
                            fontSize = 12.sp,
                            color = TextMuted,
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

            // ── Cashier Assignment ────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Assigned Cashier",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = PrimaryDark,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val cashierName = liveRegister.assignedCashierName ?: "None"
                    val cashierStatus = when {
                        liveRegister.assignedCashierName == null -> "No cashier assigned"
                        liveRegister.cashierOnShift -> "(on shift)"
                        else -> "(off shift)"
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = cashierName,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = PrimaryDark,
                        )
                        if (liveRegister.assignedCashierName != null) {
                            Text(
                                text = cashierStatus,
                                fontSize = 11.sp,
                                color = TextMuted,
                            )
                        }
                    }

                    Box {
                        OutlinedButton(
                            onClick = { assignMenuExpanded = true },
                            shape = GameButtonStyles.Shape,
                            colors = GameButtonStyles.outlinedPrimaryColors(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                        ) {
                            Text("Change", fontSize = 11.sp)
                        }

                        DropdownMenu(
                            expanded = assignMenuExpanded,
                            onDismissRequest = { assignMenuExpanded = false },
                        ) {
                            // Unassign option
                            if (liveRegister.assignedCashierId != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove assignment", color = Color(0xFFEF4444)) },
                                    onClick = {
                                        onAssignCashier(null, liveRegister.registerId)
                                        assignMenuExpanded = false
                                    },
                                )
                                HorizontalDivider()
                            }

                            // Available cashiers
                            val cashierEntries = state.staff.scheduleEntries.filter {
                                it.entityTypeName.lowercase().contains("cashier")
                            }
                            val available = cashierEntries.filter { c ->
                                c.assignedRegisterId == null || c.assignedRegisterId == liveRegister.registerId
                            }

                            if (available.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No cashiers available", color = TextMuted) },
                                    onClick = { assignMenuExpanded = false },
                                    enabled = false,
                                )
                            } else {
                                available.forEach { cashier ->
                                    val isHere = cashier.assignedRegisterId == liveRegister.registerId
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = cashier.entityName + if (isHere) " ✓" else "",
                                                    fontWeight = if (isHere)
                                                        FontWeight.SemiBold else FontWeight.Normal,
                                                )
                                                Text(
                                                    text = cashier.entityTypeName,
                                                    fontSize = 11.sp,
                                                    color = TextMuted,
                                                )
                                            }
                                        },
                                        onClick = {
                                            onAssignCashier(cashier.entityId, liveRegister.registerId)
                                            assignMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

            // ── Transaction Details ────────────────────────────────────────
            val prevTx = state.transactions.previous ?: state.history.salesHistory.lastOrNull()
            if (liveRegister.transactionActive || prevTx != null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (liveRegister.transactionActive) {
                        Text(
                            text = "Current Transaction",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = PrimaryDark,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )

                        val txLines = state.transactions.current.lines
                        val rungSubtotal = txLines
                            .filter { !it.lostToOutOfStock }
                            .fold(Money.ZERO) { acc, line -> acc + line.unitPrice * line.rungQty }
                        val rungTax = rungSubtotal * 0.0825
                        TransactionDetails(
                            transactionLines = txLines,
                            subtotal = rungSubtotal,
                            tax = rungTax,
                            total = rungSubtotal + rungTax,
                            itemDao = itemDao,
                        )

                        if (prevTx != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Previous Transaction",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = PrimaryDark,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp)),
                                color = Color(0xFFFAFAFA),
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = "Transaction #${prevTx.id}",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = PrimaryDark,
                                        )
                                        Text(
                                            text = prevTx.totalEarned.toString(),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = PrimaryDark,
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${prevTx.lines.size} items",
                                        fontSize = 12.sp,
                                        color = TextMuted,
                                    )
                                }
                            }
                        }
                    } else if (prevTx != null) {
                        Text(
                            text = "Last Transaction",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = PrimaryDark,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        TransactionDetails(
                            transactionLines = prevTx.lines,
                            subtotal = prevTx.subtotal,
                            tax = prevTx.tax,
                            total = prevTx.totalEarned,
                            itemDao = itemDao,
                            showProgress = false,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Close Button ───────────────────────────────────────────────
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = GameButtonStyles.primaryBlueColor(),
                shape = GameButtonStyles.Shape,
            ) {
                Text("Close")
            }
        }
    }
}

/**
 * Displays transaction line items, subtotal, tax, and total.
 */
@Composable
private fun TransactionDetails(
    transactionLines: List<com.example.superstoresimulator.domain.Transactions.TransactionLine>,
    subtotal: Money,
    tax: Money,
    total: Money,
    itemDao: ItemDao,
    showProgress: Boolean = true,
) {

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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        color = Color(0xFFFAFAFA),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val fulfillableLines = transactionLines.filter { !it.lostToOutOfStock }
            val totalRung = fulfillableLines.sumOf { it.rungQty }
            val totalRequired = fulfillableLines.sumOf { it.quantity }

            // Line items
            transactionLines.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = itemNames.value[line.itemId] ?: "Item ${line.itemId}",
                            fontSize = 12.sp,
                            color = if (line.lostToOutOfStock) TextMuted else PrimaryDark,
                        )
                        if (line.lostToOutOfStock) {
                            Text(
                                text = "Out of stock",
                                fontSize = 10.sp,
                                color = Color(0xFFEF4444),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (line.lostToOutOfStock)
                            "${line.quantity}⨯ ✗"
                        else
                            "${line.rungQty}/${line.quantity}",
                        fontSize = 11.sp,
                        color = if (line.lostToOutOfStock) Color(0xFFEF4444) else TextMuted,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

            // Subtotal, Tax, Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Subtotal", fontSize = 11.sp, color = TextMuted)
                Text(subtotal.toString(), fontSize = 11.sp, color = TextMuted)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Tax", fontSize = 11.sp, color = TextMuted)
                Text(tax.toString(), fontSize = 11.sp, color = TextMuted)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = PrimaryDark)
                Text(
                    total.toString(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = PrimaryDark
                )
            }

            if (showProgress) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Progress: $totalRung/$totalRequired items rung",
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

