package com.example.superstoresimulator.ui.screens.metrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
    onLoadArchivedDay: (dayNumber: Int) -> Unit = {},
    onClearLoadedArchivedDay: () -> Unit = {},
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedDay by remember { mutableStateOf<DailyMetrics?>(null) }
    var outOfStockDay by remember { mutableStateOf<DailyMetrics?>(null) }
    var soldItemsDay by remember { mutableStateOf<DailyMetrics?>(null) }
    // When true, the corresponding dialog reads state.activeDay (live-updating)
    var showActiveReport by remember { mutableStateOf(false) }
    var showActiveOutOfStock by remember { mutableStateOf(false) }
    var showActiveSoldItems by remember { mutableStateOf(false) }
    // Tracks which archived report type we're waiting to show
    var pendingArchivedReport by remember { mutableStateOf<String?>(null) } // "sold", "oos", "full"
    val focusManager = LocalFocusManager.current

    // When loaded archived day arrives, show the pending dialog
    LaunchedEffect(state.loadedArchivedDay) {
        val loaded = state.loadedArchivedDay ?: return@LaunchedEffect
        when (pendingArchivedReport) {
            "sold" -> soldItemsDay = loaded
            "oos" -> outOfStockDay = loaded
            "full" -> selectedDay = loaded
        }
        pendingArchivedReport = null
    }

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
        if (filtered.isEmpty() && state.activeDay == null) {
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
                // ── Active day (live) ────────────────────────────────────
                state.activeDay?.let { active ->
                    item(key = "active_day") {
                        ActiveDayReportCard(
                            day = active,
                            onClick = { showActiveReport = true },
                            onOutOfStockClick = { showActiveOutOfStock = true },
                            onSoldItemsClick = { showActiveSoldItems = true },
                        )
                    }
                }

                // ── Completed days ───────────────────────────────────────
                items(filtered, key = { "day_${it.dayNumber}" }) { day ->
                    val isArchived = day.dayNumber in state.archivedDayNumbers
                    DayReportCard(
                        day = day,
                        isArchived = isArchived,
                        onClick = {
                            if (isArchived) {
                                pendingArchivedReport = "full"
                                onLoadArchivedDay(day.dayNumber)
                            } else {
                                selectedDay = day
                            }
                        },
                        onOutOfStockClick = {
                            if (isArchived) {
                                pendingArchivedReport = "oos"
                                onLoadArchivedDay(day.dayNumber)
                            } else {
                                outOfStockDay = day
                            }
                        },
                        onSoldItemsClick = {
                            if (isArchived) {
                                pendingArchivedReport = "sold"
                                onLoadArchivedDay(day.dayNumber)
                            } else {
                                soldItemsDay = day
                            }
                        },
                    )
                }
                item(key = "bottom_spacer") { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Full detail dialog — completed day (static snapshot)
    selectedDay?.let { day ->
        EndOfDayReportDialog(
            report = day,
            onDismiss = { selectedDay = null; onClearLoadedArchivedDay() },
            isAutoShown = false,
            itemNames = state.itemNames,
        )
    }

    // Full detail dialog — active day (live-updating via state.activeDay)
    if (showActiveReport) {
        state.activeDay?.let { day ->
            EndOfDayReportDialog(
                report = day,
                onDismiss = { showActiveReport = false },
                isAutoShown = false,
                itemNames = state.itemNames,
            )
        } ?: run { showActiveReport = false }
    }

    // Out-of-stock drill-down — completed day
    outOfStockDay?.let { day ->
        OutOfStockReportDialog(
            report = day,
            onDismiss = { outOfStockDay = null; onClearLoadedArchivedDay() },
            onItemClick = { itemId ->
                outOfStockDay = null
                onClearLoadedArchivedDay()
                onFocusInventoryItem(itemId)
            },
            itemNames = state.itemNames,
        )
    }

    // Out-of-stock drill-down — active day (live-updating)
    if (showActiveOutOfStock) {
        state.activeDay?.let { day ->
            OutOfStockReportDialog(
                report = day,
                onDismiss = { showActiveOutOfStock = false },
                onItemClick = { itemId ->
                    showActiveOutOfStock = false
                    onFocusInventoryItem(itemId)
                },
                itemNames = state.itemNames,
            )
        } ?: run { showActiveOutOfStock = false }
    }

    // Items sold drill-down — completed day
    soldItemsDay?.let { day ->
        SoldItemsReportDialog(
            report = day,
            onDismiss = { soldItemsDay = null; onClearLoadedArchivedDay() },
            onItemClick = { itemId ->
                soldItemsDay = null
                onClearLoadedArchivedDay()
                onFocusInventoryItem(itemId)
            },
            itemNames = state.itemNames,
        )
    }

    // Items sold drill-down — active day (live-updating)
    if (showActiveSoldItems) {
        state.activeDay?.let { day ->
            SoldItemsReportDialog(
                report = day,
                onDismiss = { showActiveSoldItems = false },
                onItemClick = { itemId ->
                    showActiveSoldItems = false
                    onFocusInventoryItem(itemId)
                },
                itemNames = state.itemNames,
            )
        } ?: run { showActiveSoldItems = false }
    }
}

@Composable
private fun ActiveDayReportCard(
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
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(3.dp),
        border = BorderStroke(1.5.dp, Secondary),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Title row with live badge ─────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.width(8.dp))
                    // Live indicator
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SuccessChipSurface)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Secondary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Live",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessTextDark
                        )
                    }
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

            DayReportCardContent(
                day = day,
                onSoldItemsClick = onSoldItemsClick,
                onOutOfStockClick = onOutOfStockClick,
            )

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
private fun DayReportCard(
    day: DailyMetrics,
    isArchived: Boolean = false,
    onClick: () -> Unit,
    onOutOfStockClick: () -> Unit,
    onSoldItemsClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
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

            DayReportCardContent(
                day = day,
                isArchived = isArchived,
                onSoldItemsClick = onSoldItemsClick,
                onOutOfStockClick = onOutOfStockClick,
            )

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
private fun DayReportCardContent(
    day: DailyMetrics,
    isArchived: Boolean = false,
    onSoldItemsClick: () -> Unit,
    onOutOfStockClick: () -> Unit,
) {
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

    Spacer(Modifier.height(8.dp))

    // ── Items sold button ─────────────────────────────────────────
    val showSoldButton = if (isArchived) day.itemsSold > 0 else day.soldItemEvents.isNotEmpty()
    if (showSoldButton) {
        OutlinedButton(
            onClick = onSoldItemsClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Primary
            ),
            border = BorderStroke(1.dp, Primary),
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
    val showOosButton = if (isArchived) day.itemsLostToOutOfStock > 0 else day.outOfStockEvents.isNotEmpty()
    if (showOosButton) {
        OutlinedButton(
            onClick = onOutOfStockClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = CriticalRed
            ),
            border = BorderStroke(1.dp, CriticalRed),
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
