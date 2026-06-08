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
import com.example.superstoresimulator.ui.components.PriceSlider
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.state.TruckOrderLineUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.CriticalRed
import com.example.superstoresimulator.ui.theme.DestructiveDark
import com.example.superstoresimulator.ui.theme.Destructive
import com.example.superstoresimulator.ui.theme.ErrorSurface
import com.example.superstoresimulator.ui.theme.ErrorSurfaceSubtle
import com.example.superstoresimulator.ui.theme.ErrorTextDark
import com.example.superstoresimulator.ui.theme.FreshnessYellow
import com.example.superstoresimulator.ui.theme.InfoSurface
import com.example.superstoresimulator.ui.theme.OrangeAccent
import com.example.superstoresimulator.ui.theme.PlaceholderSurface
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarIndicator
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.Secondary
import com.example.superstoresimulator.ui.theme.Success
import com.example.superstoresimulator.ui.theme.SuccessChipSurface
import com.example.superstoresimulator.ui.theme.SuccessTextDark
import com.example.superstoresimulator.ui.theme.SurfaceSubtle
import com.example.superstoresimulator.ui.theme.CautionDark
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import com.example.superstoresimulator.ui.theme.Violet
import com.example.superstoresimulator.ui.theme.WarningChipSurface
import com.example.superstoresimulator.ui.theme.WarningOrangeSurface
import com.example.superstoresimulator.ui.theme.WarningTextDarker
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
        colors = CardDefaults.cardColors(CardWhite),
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
                color = PrimaryDark
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Category: ${item.category.displayName}",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = InfoSurface
                ) {
                    Text(
                        text = fullItem?.tier ?: currentTier.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            val desc = fullItem?.description
            if (!desc.isNullOrBlank()) {
                Text(
                    text = desc,
                    fontSize = 14.sp,
                    color = ProgressBarIndicator,
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
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Stock Levels", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChipTextDark)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Shelf Stock", fontSize = 12.sp, color = TextSecondary)
                    Text("${item.shelfStock} units", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ChipTextDark)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Backroom Stock", fontSize = 12.sp, color = TextSecondary)
                        if (item.backroomFull) {
                            Text("FULL", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Destructive)
                        }
                    }
                    Text("${item.backroomStock} units", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = ChipTextDark)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlaceholderSurface, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total Stock", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${item.shelfStock + item.backroomStock} units",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark
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
        colors = CardDefaults.cardColors(CardWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Batch Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChipTextDark)
            Text(
                "${allBatches.size} batch${if (allBatches.size != 1) "es" else ""} in stock",
                fontSize = 13.sp, color = TextSecondary
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
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = InfoSurface) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total Value in Stock", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PrimaryDark)
                    Text(Money(totalValueAtRisk).toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryDark)
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

    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = SurfaceSubtle, tonalElevation = 1.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(4.dp), color = if (location == "Shelf") Primary else Violet) {
                        Text(location, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextWhite, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Text("${batch.quantity} units", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ChipTextDark)
                }
                Text("$freshnessPercent%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = freshnessColor)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = expirationLabel(daysUntilExpiration),
                    fontSize = 12.sp,
                    color = if (daysUntilExpiration <= 3) DestructiveDark else TextSecondary,
                    fontWeight = if (daysUntilExpiration <= 3) FontWeight.Bold else FontWeight.Normal
                )
                Text("Received day ${batch.receivedDay}", fontSize = 11.sp, color = TextMuted)
            }
            LinearProgressIndicator(
                progress = { freshnessPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = freshnessColor, trackColor = ProgressBarTrack
            )
            Surface(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp),
                color = when { daysUntilExpiration <= 1 -> ErrorSurface; daysUntilExpiration <= 3 -> WarningOrangeSurface; else -> PlaceholderSurface }
            ) {
                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Value at risk:", fontSize = 11.sp, color = TextSecondary)
                    Text(
                        text = valueAtRisk.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = when { daysUntilExpiration <= 1 -> DestructiveDark; daysUntilExpiration <= 3 -> OrangeAccent; else -> ChipTextDark }
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

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Freshness", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChipTextDark)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expirationLabel(daysUntilExpiration), fontSize = 14.sp,
                    color = if (daysUntilExpiration <= 3) DestructiveDark else TextSecondary,
                    fontWeight = if (daysUntilExpiration <= 3) FontWeight.Bold else FontWeight.Normal
                )
                Text("$freshnessPercent% fresh", fontSize = 14.sp, color = freshnessColor, fontWeight = FontWeight.Bold)
            }
            LinearProgressIndicator(
                progress = { freshnessPercent / 100f },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = freshnessColor, trackColor = ProgressBarTrack
            )
            Text("Shelf life: $shelfLifeDays days", fontSize = 12.sp, color = TextSecondary)
        }
    }
}

// ─── Pricing ─────────────────────────────────────────────────────────────────

/** Card showing pricing info with interactive item price override slider. */
@Composable
fun PricingCard(
    item: InventoryItemUI,
    itemOverridePercent: Int = 0,
    onSetItemOverride: ((Int, Int) -> Unit)? = null,
    onClearMarkdown: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val priceSuffix = if (item.soldByWeight) "/lb" else ""
    val effectiveMargin = item.effectivePrice - item.unitCost
    val effectiveMarginPercent = if (item.unitCost.cents > 0)
        ((effectiveMargin.cents.toDouble() / item.unitCost.cents) * 100).toInt() else 0

    val nearCostThreshold = item.unitCost.cents + (item.unitCost.cents * 0.10).toLong()
    val isAtCost = item.effectivePrice.cents <= item.unitCost.cents
    val isLowMargin = !isAtCost && item.effectivePrice.cents <= nearCostThreshold

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pricing & Profit", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChipTextDark)

            if (item.priceModifierPercent != 0) {
                DetailRow("Base Price", "${item.price}$priceSuffix", TextSecondary)
                val modColor = if (item.priceModifierPercent > 0) DestructiveDark else Secondary
                val modSign = if (item.priceModifierPercent > 0) "+" else ""
                DetailRow("Effective Price", "${item.effectivePrice}$priceSuffix ($modSign${item.priceModifierPercent}%)", modColor)
            } else {
                DetailRow("Retail Price", "${item.effectivePrice}$priceSuffix", PrimaryDark)
            }

            DetailRow("Unit Cost", "${item.unitCost}$priceSuffix", TextSecondary)
            DetailRow(
                "Margin per Unit",
                "$effectiveMargin ($effectiveMarginPercent%)",
                if (effectiveMargin.cents >= 0) Secondary else Destructive
            )

            if (isAtCost) {
                Surface(
                    color = ErrorSurface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("At Cost — No Margin", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = DestructiveDark, modifier = Modifier.padding(8.dp))
                }
            } else if (isLowMargin) {
                Surface(
                    color = WarningChipSurface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Low Margin", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = CautionDark, modifier = Modifier.padding(8.dp))
                }
            }

            if (item.hasActiveMarkdown) {
                Surface(
                    color = SuccessChipSurface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Markdown Active", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Success)
                        if (onClearMarkdown != null) {
                            Text(
                                "Clear",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryDark,
                                modifier = Modifier.clickable { onClearMarkdown(item.id) }
                            )
                        }
                    }
                }
            }

            if (onSetItemOverride != null) {
                Spacer(Modifier.height(4.dp))
                PriceSlider(
                    label = "Item Price Override",
                    currentPercent = itemOverridePercent,
                    range = -75f..200f,
                    onSet = { onSetItemOverride(item.id, it) }
                )
            }
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

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Case Pack Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChipTextDark)
            DetailRow("Units per Case", "${item.casePack} units", ChipTextDark)
            DetailRow("Case Pack Cost", item.casePackCost.toString(), PrimaryDark)
            DetailRow("Case Pack Profit", casePackProfit.toString(), if (casePackProfit.cents >= 0) Secondary else Destructive)
            if (fullItem != null) {
                // Demand is currently always 100% (baseline); TODO: apply event modifiers when implemented
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = SuccessChipSurface) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Customer Demand", fontSize = 12.sp, color = SuccessTextDark)
                        Text("100%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessTextDark)
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
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🚚 Pending Deliveries", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ChipTextDark)
            pendingDeliveries.forEach { line ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${line.casePacks} cases · ${line.quantity} units", fontSize = 13.sp, color = ChipTextDark)
                    if (line.canCancel) {
                        if (line.casePacks > 1) {
                            FilterChip(selected = false, onClick = { onDecrementOrderLine(itemId, line.truckId) }, label = { Text("Cancel 1", fontSize = 11.sp, color = DestructiveDark) })
                        }
                        FilterChip(selected = false, onClick = { onCancelOrderLine(itemId, line.truckId) }, label = { Text("Cancel", fontSize = 11.sp, color = DestructiveDark) })
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

    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(CardWhite), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sales Analysis", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ChipTextDark)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SalesTimeRange.entries.forEach { range ->
                    val isSelected = selectedRange == range
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PrimaryDark else ChipSurface,
                        modifier = Modifier.weight(1f).clickable { selectedRange = range }
                    ) {
                        Text(
                            text = when (range) { SalesTimeRange.DAILY -> "Daily"; SalesTimeRange.WEEKLY -> "Weekly"; SalesTimeRange.MONTHLY -> "Monthly" },
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) TextWhite else ChipTextDark,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (salesStats.totalUnitsSold > 0) {
                DetailRow("Total Units Sold", "${salesStats.totalUnitsSold} units", ChipTextDark)
                DetailRow("Total Revenue", salesStats.totalRevenue.toString(), Secondary)
                DetailRow(
                    "Average per ${getRangeName(selectedRange)}",
                    String.format(Locale.US, "%.1f units", salesStats.averagePerPeriod),
                    TextSecondary
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                    color = when { salesStats.averagePerPeriod >= 10 -> SuccessChipSurface; salesStats.averagePerPeriod >= 5 -> WarningChipSurface; else -> ErrorSurface }
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
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = PlaceholderSurface) {
                    Text(
                        text = "No sales recorded for this ${getRangeName(selectedRange).lowercase()}",
                        fontSize = 13.sp, color = TextSecondary,
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
    valueColor: Color = ChipTextDark
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 14.sp, color = TextSecondary)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

/** Returns a freshness progress color based on [freshnessPercent]. */
fun freshnessColorFor(freshnessPercent: Int): Color = when {
    freshnessPercent <= 10 -> DestructiveDark
    freshnessPercent <= 25 -> OrangeAccent
    freshnessPercent <= 50 -> FreshnessYellow
    else -> Secondary
}

/** Returns a human-readable expiration label for [daysUntilExpiration]. */
fun expirationLabel(daysUntilExpiration: Int): String = when {
    daysUntilExpiration <= 0 -> "⚠️ Expired"
    daysUntilExpiration == 1 -> "⚠️ Expires tomorrow"
    daysUntilExpiration <= 3 -> "⚠️ Expires in $daysUntilExpiration days"
    else -> "Expires in $daysUntilExpiration days"
}

private fun performanceLabelColor(avg: Double): Color = when {
    avg >= 10 -> SuccessTextDark
    avg >= 5 -> WarningTextDarker
    else -> ErrorTextDark
}

