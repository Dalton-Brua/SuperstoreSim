package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.metrics.AutoHireAction
import com.example.superstoresimulator.domain.metrics.AutoHireEvent
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.ui.state.ReputationUIState
import com.example.superstoresimulator.ui.theme.*
import java.util.Locale

/**
 * Modal dialog shown automatically at midnight when a game day rolls over.
 * Displays a summary of the day's performance across sales, customers, and inventory.
 */
@Composable
fun EndOfDayReportDialog(
    report: DailyMetrics,
    onDismiss: () -> Unit,
    /** True when the dialog is shown automatically at midnight (shows "Start Day" + pauses time).
     *  False when opened manually from the Metrics screen (shows plain "Close"). */
    isAutoShown: Boolean = true,
    itemNames: Map<Int, String> = emptyMap(),
    reputationUiState: ReputationUIState? = null,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Header ────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryDark, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "End of Day Report",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Day ${report.dayNumber + 1} — ${report.dayOfWeekName}",
                            color = TextWhite.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Sales section ─────────────────────────────────────────
                SectionHeader("💰 Sales")
                StatRow(Icons.Default.AttachMoney, "Revenue",           report.revenue.toString(),     highlight = true)
                StatRow(Icons.Default.Receipt,      "Subtotal",          report.subtotal.toString())
                StatRow(Icons.Default.AccountBalance,"Tax Collected",    report.taxCollected.toString())
                StatRow(Icons.Default.ShoppingCart, "Transactions",      report.transactionsCompleted.toString())
                StatRow(Icons.AutoMirrored.Filled.TrendingUp, "Avg. Sale Value", report.averageTransactionValue.toString())

                if (report.lostRevenue.cents > 0) {
                    Spacer(Modifier.height(4.dp))
                    StatRow(
                        Icons.Default.RemoveShoppingCart,
                        "Lost Revenue (OOS)",
                        "${report.itemsLostToOutOfStock} units · ${report.lostRevenue}",
                        tint = CriticalRed
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Operating Costs section ──────────────────────────────
                SectionHeader("💸 Operating Costs")
                StatRow(Icons.Default.Home, "Daily Rent", "-${report.rentPaid}", tint = ClosedRed)
                StatRow(Icons.Default.People, "Staff Wages", "-${report.wagesPaid}", tint = ClosedRed)

                // Net revenue calculation
                val netAfterCosts = report.netRevenue
                StatRow(
                    Icons.Default.AccountBalance,
                    "Net Revenue",
                    netAfterCosts.toString(),
                    highlight = true,
                    tint = if (netAfterCosts.cents >= 0) PositiveGreen else ClosedRed
                )

                Spacer(Modifier.height(12.dp))
                
                // ── Shrinkage section ─────────────────────────────────────
                if (report.itemsExpired > 0) {
                    SectionHeader("🗑️ Shrinkage (Expiration)")
                    StatRow(
                        Icons.Default.Delete,
                        "Items Expired",
                        "${report.itemsExpired} units",
                        tint = ClosedRed
                    )
                    StatRow(
                        Icons.Default.AttachMoney,
                        "Waste Cost",
                        "-${report.expiredWasteCost}",
                        tint = ClosedRed
                    )
                    
                    Spacer(Modifier.height(12.dp))
                }

                // ── Customers section ─────────────────────────────────────
                SectionHeader("👥 Customers")
                StatRow(Icons.Default.People,       "Customers Served",  report.customersServed.toString())
                StatRow(Icons.Default.Inventory2,   "Items Sold",        "${report.itemsSold} units")
                StatRow(Icons.Default.BarChart,     "Avg. Basket Size",
                    String.format(Locale.US, "%.1f items", report.averageBasketSize))

                Spacer(Modifier.height(12.dp))

                // ── Inventory section ─────────────────────────────────────
                SectionHeader("📦 Inventory")
                StatRow(Icons.Default.MoveToInbox,  "Cases Stocked",     "${report.itemsStocked}")
                StatRow(Icons.Default.AddShoppingCart, "Items Ordered",  "${report.itemsOrdered} units")

                Spacer(Modifier.height(20.dp))

                // ── Fresh Auto-Orders section ──────────────────────────────
                if (report.autoOrderedFreshItems.isNotEmpty() || report.incompleteOrderedFreshItems.isNotEmpty()) {
                    SectionHeader("🥬 Fresh Auto-Orders")
                    
                    if (report.autoOrderedFreshItems.isNotEmpty()) {
                        // Aggregate orders by item ID to show total packs ordered per item for the entire day
                        val aggregatedOrders = report.autoOrderedFreshItems
                            .groupBy { it.itemId }
                            .map { (_, orders) ->
                                val first = orders.first()
                                val totalPacks = orders.sumOf { it.casePacksOrdered }
                                val totalCost = orders.fold(Money.ZERO) { acc, order -> acc + order.totalCost }
                                first.copy(
                                    casePacksOrdered = totalPacks,
                                    totalCost = totalCost
                                )
                            }
                            .sortedByDescending { it.casePacksOrdered }
                        
                        var successfulExpanded by remember { mutableStateOf(false) }
                        val totalSuccessfulCost = aggregatedOrders.fold(Money.ZERO) { acc, order -> acc + order.totalCost }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SuccessSurface, RoundedCornerShape(8.dp))
                                .clickable { successfulExpanded = !successfulExpanded }
                                .padding(12.dp)
                        ) {
                            // Summary row - always visible
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Successfully Ordered",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SuccessAccent
                                    )
                                    Text(
                                        "${aggregatedOrders.size} items • ${aggregatedOrders.sumOf { it.casePacksOrdered }} packs total",
                                        fontSize = 11.sp,
                                        color = SuccessAccent.copy(alpha = 0.8f)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        totalSuccessfulCost.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessAccent
                                    )
                                    Icon(
                                        imageVector = if (successfulExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (successfulExpanded) "Collapse" else "Expand",
                                        tint = SuccessAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            // Details - shown when expanded
                            if (successfulExpanded) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = SuccessAccent.copy(alpha = 0.2f), thickness = 1.dp)
                                Spacer(Modifier.height(8.dp))
                                
                                aggregatedOrders.forEach { order ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${itemNames[order.itemId] ?: order.itemName.ifEmpty { "Item ${order.itemId}" }}: ${order.casePacksOrdered} packs",
                                            fontSize = 11.sp,
                                            color = TextNeutralDark
                                        )
                                        Text(
                                            order.totalCost.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = SuccessAccent
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    if (report.incompleteOrderedFreshItems.isNotEmpty()) {
                        // Aggregate incomplete orders by item ID
                        val aggregatedIncomplete = report.incompleteOrderedFreshItems
                            .groupBy { it.itemId }
                            .map { (_, orders) ->
                                val first = orders.first()
                                val totalPacks = orders.sumOf { it.casePacksRequested }
                                val totalCost = orders.fold(Money.ZERO) { acc, order -> acc + order.totalCost }
                                first.copy(
                                    casePacksRequested = totalPacks,
                                    totalCost = totalCost
                                )
                            }
                            .sortedByDescending { it.casePacksRequested }
                        
                        var incompleteExpanded by remember { mutableStateOf(false) }
                        val totalIncompleteCost = aggregatedIncomplete.fold(Money.ZERO) { acc, order -> acc + order.totalCost }
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ErrorSurfaceSubtle, RoundedCornerShape(8.dp))
                                .clickable { incompleteExpanded = !incompleteExpanded }
                                .padding(12.dp)
                        ) {
                            // Summary row - always visible
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Incomplete (Insufficient Funds)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DestructiveDark
                                    )
                                    Text(
                                        "${aggregatedIncomplete.size} items • ${aggregatedIncomplete.sumOf { it.casePacksRequested }} packs needed",
                                        fontSize = 11.sp,
                                        color = DestructiveDark.copy(alpha = 0.8f)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        totalIncompleteCost.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DestructiveDark
                                    )
                                    Icon(
                                        imageVector = if (incompleteExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = if (incompleteExpanded) "Collapse" else "Expand",
                                        tint = DestructiveDark,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            
                            // Details - shown when expanded
                            if (incompleteExpanded) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = DestructiveDark.copy(alpha = 0.2f), thickness = 1.dp)
                                Spacer(Modifier.height(8.dp))
                                
                                aggregatedIncomplete.forEach { order ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "${itemNames[order.itemId] ?: order.itemName.ifEmpty { "Item ${order.itemId}" }}: ${order.casePacksRequested} packs",
                                                fontSize = 11.sp,
                                                color = TextNeutralDark
                                            )
                                            Text(
                                                order.reason,
                                                fontSize = 10.sp,
                                                color = DestructiveDark
                                            )
                                        }
                                        Text(
                                            order.totalCost.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DestructiveDark
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "These can be manually ordered from the Fresh screen.",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }

                // ── Auto-Hire section ──────────────────────────────────────
                if (report.autoHireEvents.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionHeader("👔 Manager Auto-Hire")
                        report.autoHireEvents.forEach { event ->
                            AutoHireRow(event)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Deliveries Received section ───────────────────────────
                if (report.deliveredTrucks.isNotEmpty()) {
                    var deliveriesExpanded by remember { mutableStateOf(false) }
                    val totalCasePacks = report.deliveredTrucks.sumOf { it.totalCasePacks }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SuccessSurface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deliveriesExpanded = !deliveriesExpanded }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "🚚 Deliveries Received",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = SuccessTextDark
                                )
                                Text(
                                    "${report.deliveredTrucks.size} truck(s) · $totalCasePacks cases ${if (deliveriesExpanded) "▲" else "▼"}",
                                    fontSize = 11.sp,
                                    color = SuccessTextDark
                                )
                            }
                            if (deliveriesExpanded) {
                                Spacer(Modifier.height(8.dp))
                                HorizontalDivider(color = SuccessTextDark.copy(alpha = 0.2f), thickness = 1.dp)
                                Spacer(Modifier.height(8.dp))
                                report.deliveredTrucks.forEach { truck ->
                                    val label = when {
                                        truck.isEarlyTruck -> "Early Truck"
                                        truck.isFreshTruck -> "Fresh Truck"
                                        else -> "Regular Truck"
                                    }
                                    Text(
                                        "$label — Day ${truck.arrivalDay + 1} — ${truck.totalCasePacks} cases",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextNeutralDark,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                    truck.lines.forEach { line ->
                                        val lineName = itemNames[line.itemId] ?: line.itemName.ifEmpty { "Item ${line.itemId}" }
                                        if (line.casePacks > 0) {
                                            Text(
                                                "  $lineName: ${line.casePacks} packs (${line.quantity} units)",
                                                fontSize = 11.sp,
                                                color = TextNeutralMedium
                                            )
                                        }
                                        if (line.deferredCasePacks > 0) {
                                            Text(
                                                "  $lineName: ${line.deferredCasePacks} packs deferred — backroom full",
                                                fontSize = 11.sp,
                                                color = Amber
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }

                // ── Store Reputation section ──────────────────────────────
                if (reputationUiState?.isActive == true && reputationUiState.lastDailyComposite > 0f) {
                    Spacer(Modifier.height(12.dp))
                    SectionHeader("⭐ Store Reputation")

                    val repPct = reputationUiState.reputationScore.toInt()
                    val repColor = when {
                        reputationUiState.reputationScore >= 125f -> PositiveGreen
                        reputationUiState.reputationScore >= 75f  -> Amber
                        else                                      -> ClosedRed
                    }
                    StatRow(Icons.Default.Star, "Reputation", "$repPct%", highlight = true, tint = repColor)

                    val revScore = reputationUiState.lastRevenueScore.toInt()
                    val stockScore = reputationUiState.lastStockScore.toInt()
                    val appScore = reputationUiState.lastAppearanceScore.toInt()
                    if (reputationUiState.currentRevenueTarget.cents > 0) {
                        StatRow(Icons.Default.AttachMoney, "Revenue Score", "$revScore% (target ${reputationUiState.currentRevenueTarget})")
                    }
                    StatRow(Icons.Default.Inventory2, "Stock Score", "$stockScore%")
                    StatRow(Icons.Default.CleaningServices, "Appearance Score", "$appScore%")

                    val trafficPct = ((reputationUiState.trafficMultiplier - 1f) * 100).toInt()
                    val tolPct = ((reputationUiState.priceToleranceMultiplier - 1f) * 100).toInt()
                    val supPct = (reputationUiState.supplierDiscountBonus * 100 * 10).toInt() / 10f
                    val trafficSign = if (trafficPct >= 0) "+" else ""
                    val tolSign = if (tolPct >= 0) "+" else ""
                    StatRow(
                        Icons.Default.People, "Active Bonuses",
                        "Traffic ${trafficSign}${trafficPct}% · Tolerance ${tolSign}${tolPct}% · Supplier +${supPct}%"
                    )

                    if (reputationUiState.consecutiveTargetHits > 1) {
                        StatRow(Icons.Default.Star, "Streak", "${reputationUiState.consecutiveTargetHits}-day target streak", tint = PositiveGreen)
                    } else if (reputationUiState.consecutiveTargetMisses > 0) {
                        StatRow(Icons.Default.Warning, "Streak",
                            "Target missed (${reputationUiState.consecutiveTargetMisses} day${if (reputationUiState.consecutiveTargetMisses > 1) "s" else ""})",
                            tint = Amber)
                    }

                    Spacer(Modifier.height(12.dp))
                }

                // ── Research Completed section ────────────────────────────
                if (report.completedResearch.isNotEmpty()) {
                    SectionHeader("🔬 Research Completed")
                    report.completedResearch.forEach { research ->
                        StatRow(
                            Icons.Default.Science,
                            research.displayName,
                            if (research.unlockedItemCount > 0)
                                "+${research.unlockedItemCount} item${if (research.unlockedItemCount > 1) "s" else ""}"
                            else "Unlocked",
                            tint = Secondary
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ── Dismiss button ────────────────────────────────────────
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isAutoShown) "Start Day ${report.dayNumber + 2}" else "Close",
                        color = TextWhite,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = PrimaryDark,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
    HorizontalDivider(color = CardBlue, thickness = 1.dp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun StatRow(
    icon: ImageVector,
    label: String,
    value: String,
    highlight: Boolean = false,
    tint: Color = TextSecondary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) PrimaryDark else TextDark,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AutoHireRow(event: AutoHireEvent) {
    var showTooltip by remember { mutableStateOf(false) }
    val (icon, tint, prefix) = when (event.action) {
        AutoHireAction.HIRED -> Triple(Icons.Default.PersonAdd, Secondary, "Hired")
        AutoHireAction.SKIPPED -> Triple(Icons.Default.Block, Amber, "Skipped")
        AutoHireAction.REBALANCED -> Triple(Icons.Default.SwapHoriz, Primary, "Rebalanced")
        AutoHireAction.PURCHASED -> Triple(Icons.Default.ShoppingCart, Violet, "Purchased")
        AutoHireAction.PROMOTED -> Triple(Icons.AutoMirrored.Filled.TrendingUp, Secondary, "Promoted")
        AutoHireAction.TERMINATED -> Triple(Icons.Default.PersonOff, Destructive, "Terminated")
        AutoHireAction.REDUCED_HOURS -> Triple(Icons.Default.RemoveCircleOutline, Amber, "Reduced")
        AutoHireAction.SCHEDULE_OPTIMIZED -> Triple(Icons.Default.CalendarMonth, Primary, "Scheduled")
    }
    val displayReason = if (event.blocked) event.blockReason else event.reason
    val detail = event.detail

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$prefix ${event.entityDefName}",
                fontSize = 13.sp,
                color = TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = displayReason,
                fontSize = 13.sp,
                color = TextDark,
                textAlign = TextAlign.End,
            )
            if (detail.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Details",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { showTooltip = !showTooltip }
                )
            }
        }
        if (showTooltip && detail.isNotEmpty()) {
            Text(
                text = detail,
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(start = 24.dp, bottom = 4.dp)
            )
        }
    }
}

