package com.example.superstoresimulator.ui.screens.metrics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.ui.dialogs.EndOfDayReportDialog
import com.example.superstoresimulator.ui.dialogs.OutOfStockReportDialog
import com.example.superstoresimulator.ui.dialogs.SoldItemsReportDialog
import com.example.superstoresimulator.ui.state.MetricsUIState
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.theme.*

/**
 * Searchable list of daily store reports.
 * Each card shows headline metrics; tapping opens the full EndOfDayReportDialog for that day.
 *
 * Search works by day number (1-based display) or day-of-week name fragment.
 */
@Composable
fun MetricsScreen(
    state: MetricsUIState,
    modifier: Modifier = Modifier,
    /** Called when the player taps an item name in a report; navigates to Inventory + focuses the item. */
    onFocusInventoryItem: (itemId: Int) -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf<DailyMetrics?>(null) }
    var outOfStockDay by remember { mutableStateOf<DailyMetrics?>(null) }
    var soldItemsDay by remember { mutableStateOf<DailyMetrics?>(null) }
    val focusManager = LocalFocusManager.current

    val filtered = remember(state.completedDays, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) state.completedDays
        else state.completedDays.filter { day ->
            // Search by display day number ("Day 3" or just "3") or day-of-week name
            "day ${day.dayNumber + 1}".contains(q) ||
            (day.dayNumber + 1).toString() == q ||
            day.dayOfWeekName.lowercase().contains(q)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        
        ScreenHeader(
            title = "Metrics",
            subtitle = "Daily store performance reports"
        )
        
        // ── Search bar ────────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by day or weekday…", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = ""; focusManager.clearFocus() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = CardBlue,
                focusedBorderColor = Primary,
                focusedTextColor = PrimaryDark,  // Dark blue for text
                unfocusedTextColor = PrimaryDark,  // Dark blue for text
                cursorColor = Primary  // Blue cursor
            )
        )

        // ── Day list ──────────────────────────────────────────────────────
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.BarChart,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (state.completedDays.isEmpty())
                            "No completed days yet.\nCome back after midnight!"
                        else
                            "No days match \"$searchQuery\"",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                    items(filtered, key = { it.dayNumber }) { day ->
                        DayReportCard(
                            day = day,
                            onClick = { selectedDay = day },
                            onOutOfStockClick = { outOfStockDay = day },
                            onSoldItemsClick = { soldItemsDay = day },
                        )
                    }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Full detail dialog when a card is tapped
    selectedDay?.let { day ->
        EndOfDayReportDialog(
            report = day,
            onDismiss = { selectedDay = null },
            isAutoShown = false,
        )
    }

    // Out-of-stock drill-down dialog
    outOfStockDay?.let { day ->
        OutOfStockReportDialog(
            report = day,
            onDismiss = { outOfStockDay = null },
            onItemClick = { itemId ->
                outOfStockDay = null
                onFocusInventoryItem(itemId)
            },
        )
    }

    // Items sold drill-down dialog
    soldItemsDay?.let { day ->
        SoldItemsReportDialog(
            report = day,
            onDismiss = { soldItemsDay = null },
            onItemClick = { itemId ->
                soldItemsDay = null
                onFocusInventoryItem(itemId)
            },
        )
    }
}

@Composable
private fun DayReportCard(
    day: DailyMetrics,
    onClick: () -> Unit,
    onOutOfStockClick: () -> Unit,
    onSoldItemsClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Title row ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Day ${day.dayNumber + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryDark
                    )
                    Text(
                        text = day.dayOfWeekName,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                // Revenue pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(CardBlue)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = day.revenue.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = PrimaryDark
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = LightBackground, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))

            // ── Headline stats grid ───────────────────────────────────────
            Row(modifier = Modifier.fillMaxWidth()) {
                HeadlineStat(
                    icon = Icons.Default.People,
                    label = "Customers",
                    value = day.customersServed.toString(),
                    modifier = Modifier.weight(1f)
                )
                HeadlineStat(
                    icon = Icons.Default.ShoppingCart,
                    label = "Transactions",
                    value = day.transactionsCompleted.toString(),
                    modifier = Modifier.weight(1f)
                )
                HeadlineStat(
                    icon = Icons.Default.Inventory2,
                    label = "Items Sold",
                    value = day.itemsSold.toString(),
                    modifier = Modifier.weight(1f)
                )
                HeadlineStat(
                    icon = Icons.Default.MoveToInbox,
                    label = "Stocked",
                    value = day.itemsStocked.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            // Tap hint
            Spacer(Modifier.height(8.dp))

            // ── Items sold button ─────────────────────────────────────────
            if (day.soldItemEvents.isNotEmpty()) {
                OutlinedButton(
                    onClick = onSoldItemsClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Primary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${day.itemsSold} units sold · ${day.subtotal}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── Out-of-stock button (only when there were OOS events) ─────
            if (day.outOfStockEvents.isNotEmpty()) {
                OutlinedButton(
                    onClick = onOutOfStockClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFB91C1C)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB91C1C)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${day.itemsLostToOutOfStock} units lost to OOS · ${day.lostRevenue}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Text(
                text = "Tap for full report",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun HeadlineStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        Text(text = value, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDark)
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}
