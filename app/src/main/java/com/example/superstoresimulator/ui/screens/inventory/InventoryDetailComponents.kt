package com.example.superstoresimulator.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.state.TruckOrderLineUI
import java.util.Locale

// ─── Sales time range ─────────────────────────────────────────────────────────

enum class SalesTimeRange { DAILY, WEEKLY, MONTHLY }

data class SalesStats(
    val totalUnitsSold: Int,
    val totalRevenue: Money,
    val averagePerPeriod: Double,
    val daysAnalyzed: Int
)

fun getRangeName(range: SalesTimeRange): String = when (range) {
    SalesTimeRange.DAILY -> "Day"
    SalesTimeRange.WEEKLY -> "Week"
    SalesTimeRange.MONTHLY -> "Month"
}

fun calculateSalesStats(
    itemId: Int,
    metricsData: List<DailyMetrics>,
    range: SalesTimeRange
): SalesStats {
    if (metricsData.isEmpty()) return SalesStats(0, Money.ZERO, 0.0, 0)
    val daysToAnalyze = when (range) {
        SalesTimeRange.DAILY -> 1
        SalesTimeRange.WEEKLY -> 7
        SalesTimeRange.MONTHLY -> 30
    }
    val relevantDays = metricsData.take(daysToAnalyze)
    var totalUnits = 0
    var totalRevenue = Money.ZERO
    relevantDays.forEach { dayMetrics ->
        dayMetrics.soldItemEvents.filter { it.itemId == itemId }.forEach { event ->
            totalUnits += event.quantitySold
            totalRevenue += event.revenue
        }
    }
    val actualDays = relevantDays.size
    return SalesStats(
        totalUnitsSold = totalUnits,
        totalRevenue = totalRevenue,
        averagePerPeriod = if (actualDays > 0) totalUnits.toDouble() / actualDays else 0.0,
        daysAnalyzed = actualDays
    )
}

// ─── Item Header ─────────────────────────────────────────────────────────────

/**
 * Top card showing item name, category, tier badge, and optional description.
 * [fullItem] is the full Room [Item] entity (provides description and tier string).
 */
@Composable
fun ItemHeaderCard(
    item: InventoryItemUI,
    fullItem: Item?,
    currentTier: ItemUnlockTier,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = item.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Category: ${item.category.displayName}",
                    fontSize = 14.sp,
                    color = Color(0xFF64748B)
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFDEEBFF)
                ) {
                    Text(
                        text = fullItem?.tier ?: currentTier.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            val desc = fullItem?.description
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    fontSize = 14.sp,
                    color = Color(0xFF334155),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ─── Stock Levels ─────────────────────────────────────────────────────────────

/** Card showing shelf stock, backroom stock, and combined total. */
@Composable
fun StockLevelsCard(
    item: InventoryItemUI,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Stock Levels", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Shelf Stock", fontSize = 12.sp, color = Color(0xFF64748B))
                    Text("${item.shelfStock} units", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Backroom Stock", fontSize = 12.sp, color = Color(0xFF64748B))
                        if (item.backroomFull) {
                            Text("FULL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                        }
                    }
                    Text("${item.backroomStock} units", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Stock", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${item.shelfStock + item.backroomStock} units",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E40AF)
                )
            }
        }
    }
}

// ─── Batch Details ────────────────────────────────────────────────────────────

/** Card showing per-batch expiration info for perishable items when full batch data is available. */
@Composable
fun BatchDetailsCard(
    item: InventoryItemUI,
    shelfBatches: List<ItemBatch>,
    backroomBatches: List<ItemBatch>,
    currentDay: Int,
    modifier: Modifier = Modifier
) {
    val allBatches = (shelfBatches.map { it to "Shelf" } + backroomBatches.map { it to "Backroom" })
        .sortedBy { it.first.expirationDay }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Batch Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Text(
                "${allBatches.size} batch${if (allBatches.size != 1) "es" else ""} in stock",
                fontSize = 13.sp, color = Color(0xFF64748B)
            )
            allBatches.forEach { (batch, location) ->
                BatchRow(
                    batch = batch,
                    location = location,
                    shelfLifeDays = item.shelfLifeDays ?: 1,
                    unitCost = item.unitCost,
                    currentDay = currentDay
                )
            }
            val totalValueAtRisk = allBatches.sumOf { (batch, _) -> (item.unitCost * batch.quantity).cents }
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFDEEBFF)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Value in Stock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E40AF))
                    Text(Money(totalValueAtRisk).toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                }
            }
        }
    }
}

@Composable
private fun BatchRow(
    batch: ItemBatch,
    location: String,
    shelfLifeDays: Int,
    unitCost: Money,
    currentDay: Int
) {
    val daysUntilExpiration = batch.expirationDay - currentDay
    val freshnessPercent = if (shelfLifeDays > 0) {
        ((kotlin.math.max(0, daysUntilExpiration).toFloat() / shelfLifeDays) * 100).toInt().coerceIn(0, 100)
    } else 100
    val freshnessColor = freshnessColorFor(freshnessPercent)
    val valueAtRisk = unitCost * batch.quantity

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFF8FAFC), tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(4.dp), color = if (location == "Shelf") Color(0xFF3B82F6) else Color(0xFF8B5CF6)) {
                        Text(location, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Text("${batch.quantity} units", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                }
                Text("$freshnessPercent%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = freshnessColor)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = expirationLabel(daysUntilExpiration),
                    fontSize = 12.sp,
                    color = if (daysUntilExpiration <= 3) Color(0xFFDC2626) else Color(0xFF64748B),
                    fontWeight = if (daysUntilExpiration <= 3) FontWeight.Bold else FontWeight.Normal
                )
                Text("Received day ${batch.receivedDay}", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
            LinearProgressIndicator(
                progress = { freshnessPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = freshnessColor, trackColor = Color(0xFFE2E8F0)
            )
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp),
                color = when { daysUntilExpiration <= 1 -> Color(0xFFFEE2E2); daysUntilExpiration <= 3 -> Color(0xFFFED7AA); else -> Color(0xFFF1F5F9) }
            ) {
                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Value at risk:", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        text = valueAtRisk.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = when { daysUntilExpiration <= 1 -> Color(0xFFDC2626); daysUntilExpiration <= 3 -> Color(0xFFEA580C); else -> Color(0xFF1E293B) }
                    )
                }
            }
        }
    }
}

// ─── Simple Freshness ─────────────────────────────────────────────────────────

/** Fallback freshness card for perishable items without full batch data. */
@Composable
fun FreshnessCard(
    item: InventoryItemUI,
    currentDay: Int,
    modifier: Modifier = Modifier
) {
    val closestExpDay = item.closestExpirationDay ?: return
    val shelfLifeDays = item.shelfLifeDays ?: return
    val daysUntilExpiration = closestExpDay - currentDay
    val freshnessPercent = if (shelfLifeDays > 0) {
        ((kotlin.math.max(0, daysUntilExpiration).toFloat() / shelfLifeDays) * 100).toInt().coerceIn(0, 100)
    } else 100
    val freshnessColor = freshnessColorFor(freshnessPercent)

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Freshness", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expirationLabel(daysUntilExpiration), fontSize = 14.sp,
                    color = if (daysUntilExpiration <= 3) Color(0xFFDC2626) else Color(0xFF64748B),
                    fontWeight = if (daysUntilExpiration <= 3) FontWeight.Bold else FontWeight.Normal
                )
                Text("$freshnessPercent% fresh", fontSize = 14.sp, color = freshnessColor, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { freshnessPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = freshnessColor, trackColor = Color(0xFFE2E8F0)
            )
            Text("Shelf life: $shelfLifeDays days", fontSize = 12.sp, color = Color(0xFF64748B))
        }
    }
}

// ─── Pricing ─────────────────────────────────────────────────────────────────

/** Card showing retail price, unit cost, and per-unit margin. */
@Composable
fun PricingCard(
    item: InventoryItemUI,
    modifier: Modifier = Modifier
) {
    val margin = item.price - item.unitCost
    val marginPercent = if (item.unitCost.cents > 0) ((margin.cents.toDouble() / item.unitCost.cents) * 100).toInt() else 0

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pricing & Profit", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            DetailRow("Retail Price", item.price.toString(), Color(0xFF1E40AF))
            DetailRow("Unit Cost", item.unitCost.toString(), Color(0xFF64748B))
            DetailRow("Margin per Unit", "$margin ($marginPercent%)", if (margin.cents >= 0) Color(0xFF22C55E) else Color(0xFFEF4444))
        }
    }
}

// ─── Case Pack Details ────────────────────────────────────────────────────────

/**
 * Card showing case pack size, cost, profit, and customer demand indicator.
 * [fullItem] is the full Room [Item] entity (provides purchaseWeight).
 */
@Composable
fun CasePackDetailsCard(
    item: InventoryItemUI,
    fullItem: Item?,
    modifier: Modifier = Modifier
) {
    val margin = item.price - item.unitCost
    val casePackProfit = margin * item.casePack

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Case Pack Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            DetailRow("Units per Case", "${item.casePack} units", Color(0xFF1E293B))
            DetailRow("Case Pack Cost", item.casePackCost.toString(), Color(0xFF1E40AF))
            DetailRow("Case Pack Profit", casePackProfit.toString(), if (casePackProfit.cents >= 0) Color(0xFF22C55E) else Color(0xFFEF4444))
            if (fullItem != null) {
                // Demand is currently always 100% (baseline); TODO: apply event modifiers when implemented
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFDCFCE7)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Customer Demand", fontSize = 12.sp, color = Color(0xFF166534))
                        Text("100%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                    }
                }
            }
        }
    }
}

// ─── Pending Deliveries ───────────────────────────────────────────────────────

/**
 * Card showing in-transit truck order lines for this item with cancel controls.
 * Only rendered when [pendingDeliveries] is non-empty.
 */
@Composable
fun PendingDeliveriesCard(
    itemId: Int,
    pendingDeliveries: List<TruckOrderLineUI>,
    onCancelOrderLine: (itemId: Int, truckId: Int) -> Unit,
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pendingDeliveries.isEmpty()) return
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🚚 Pending Deliveries", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            pendingDeliveries.forEach { line ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${line.casePacks} cases · ${line.quantity} units", fontSize = 13.sp, color = Color(0xFF1E293B))
                    if (line.canCancel) {
                        if (line.casePacks > 1) {
                            FilterChip(selected = false, onClick = { onDecrementOrderLine(itemId, line.truckId) }, label = { Text("Cancel 1", fontSize = 11.sp) })
                        }
                        FilterChip(selected = false, onClick = { onCancelOrderLine(itemId, line.truckId) }, label = { Text("Cancel", fontSize = 11.sp) })
                    }
                }
            }
        }
    }
}

// ─── Sales Analysis ───────────────────────────────────────────────────────────

/** Card showing sales stats for an item over a selectable time range. */
@Composable
fun SalesAnalysisCard(
    itemId: Int,
    metricsData: List<DailyMetrics>,
    modifier: Modifier = Modifier
) {
    var selectedRange by remember { mutableStateOf(SalesTimeRange.DAILY) }
    val salesStats = remember(itemId, metricsData, selectedRange) {
        calculateSalesStats(itemId, metricsData, selectedRange)
    }

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sales Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SalesTimeRange.entries.forEach { range ->
                    val isSelected = selectedRange == range
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1E40AF) else Color(0xFFE2E8F0),
                        modifier = Modifier.weight(1f).clickable { selectedRange = range }
                    ) {
                        Text(
                            text = when (range) { SalesTimeRange.DAILY -> "Daily"; SalesTimeRange.WEEKLY -> "Weekly"; SalesTimeRange.MONTHLY -> "Monthly" },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) Color.White else Color(0xFF1E293B),
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (salesStats.totalUnitsSold > 0) {
                DetailRow("Total Units Sold", "${salesStats.totalUnitsSold} units", Color(0xFF1E293B))
                DetailRow("Total Revenue", salesStats.totalRevenue.toString(), Color(0xFF22C55E))
                DetailRow(
                    "Average per ${getRangeName(selectedRange)}",
                    String.format(Locale.US, "%.1f units", salesStats.averagePerPeriod),
                    Color(0xFF64748B)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                    color = when { salesStats.averagePerPeriod >= 10 -> Color(0xFFDCFCE7); salesStats.averagePerPeriod >= 5 -> Color(0xFFFEF3C7); else -> Color(0xFFFEE2E2) }
                ) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Performance", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = performanceLabelColor(salesStats.averagePerPeriod))
                        Text(
                            text = when { salesStats.averagePerPeriod >= 10 -> "High Demand"; salesStats.averagePerPeriod >= 5 -> "Moderate"; else -> "Low Sales" },
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = performanceLabelColor(salesStats.averagePerPeriod)
                        )
                    }
                }
            } else {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = Color(0xFFF1F5F9)) {
                    Text(
                        text = "No sales recorded for this ${getRangeName(selectedRange).lowercase()}",
                        fontSize = 13.sp, color = Color(0xFF64748B),
                        modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

/** A two-column label/value row used across detail cards. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFF1E293B)
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 14.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/** Returns a freshness progress color based on [freshnessPercent]. */
fun freshnessColorFor(freshnessPercent: Int): Color = when {
    freshnessPercent <= 10 -> Color(0xFFDC2626)
    freshnessPercent <= 25 -> Color(0xFFEA580C)
    freshnessPercent <= 50 -> Color(0xFFFBBF24)
    else -> Color(0xFF22C55E)
}

/** Returns a human-readable expiration label for [daysUntilExpiration]. */
fun expirationLabel(daysUntilExpiration: Int): String = when {
    daysUntilExpiration <= 0 -> "⚠️ Expired"
    daysUntilExpiration == 1 -> "⚠️ Expires tomorrow"
    daysUntilExpiration <= 3 -> "⚠️ Expires in $daysUntilExpiration days"
    else -> "Expires in $daysUntilExpiration days"
}

private fun performanceLabelColor(avg: Double): Color = when {
    avg >= 10 -> Color(0xFF166534)
    avg >= 5 -> Color(0xFF78350F)
    else -> Color(0xFF991B1B)
}

