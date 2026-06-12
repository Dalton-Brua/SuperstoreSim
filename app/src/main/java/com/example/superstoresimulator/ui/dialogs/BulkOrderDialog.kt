package com.example.superstoresimulator.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.theme.*
import kotlin.math.roundToInt

// ── Discount tier definitions ──────────────────────────────────────────────────
private data class DiscountTier(val minCases: Int, val fraction: Double, val label: String)

private val BULK_DISCOUNT_TIERS = listOf(
    DiscountTier(100, 0.25, "25% off"),
    DiscountTier(50,  0.15, "15% off"),
    DiscountTier(20,  0.10, "10% off"),
)

private fun discountFractionForCases(totalCases: Int): Double =
    BULK_DISCOUNT_TIERS.firstOrNull { totalCases >= it.minCases }?.fraction ?: 0.0


private fun nextDiscountHint(totalCases: Int): String? {
    val next = BULK_DISCOUNT_TIERS.reversed().firstOrNull { it.minCases > totalCases }
        ?: return null
    val needed = next.minCases - totalCases
    return "Add $needed more case(s) to unlock ${next.label}"
}

/**
 * Dialog for placing a bulk inventory order with customizable criteria and volume discounts.
 *
 * Criteria:
 *  - [maxTotalQuantity] slider: include items whose shelf + backroom stock ≤ this value
 *  - Category chip filter: limit to a specific category or all accessible categories
 *  - Cases-per-item slider: how many case packs to order for each matching item
 *
 * Discount tiers (applied to the full order total):
 *  - 20–49  total cases: 10% off
 *  - 50–99  total cases: 15% off
 *  - 100+   total cases: 25% off
 */
@Composable
fun BulkOrderDialog(
    allItems: List<InventoryItemUI>,
    money: Money,
    onConfirm: (maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    // ── State ──────────────────────────────────────────────────────────────────
    var maxTotalQuantity by remember { mutableStateOf(20) }
    var casePacksPerItem by remember { mutableStateOf(2) }
    var selectedCategory by remember { mutableStateOf<ItemCategory?>(null) }

    // Categories that have at least one item in the current (tier-filtered) list
    val availableCategories = remember(allItems) {
        allItems.map { it.category }.distinct().sortedBy { it.ordinal }
    }

    // ── Computed order summary ─────────────────────────────────────────────────
    val matchingItems = remember(allItems, maxTotalQuantity, selectedCategory) {
        allItems.filter { item ->
            val totalQty = item.shelfStock + item.backroomStock
            val categoryOk = selectedCategory == null || item.category == selectedCategory
            val notVendor = item.vendorName == null
            totalQty <= maxTotalQuantity && categoryOk && notVendor
        }
    }

    val totalCases = matchingItems.size * casePacksPerItem
    val baseCost = remember(matchingItems, casePacksPerItem) {
        matchingItems.fold(Money.ZERO) { acc, item -> acc + item.casePackCost * casePacksPerItem }
    }
    val discountFraction = discountFractionForCases(totalCases)
    val finalCost = Money((baseCost.cents * (1.0 - discountFraction)).toLong())
    val savings = baseCost - finalCost
    val canAfford = money >= finalCost
    val hasItems = matchingItems.isNotEmpty()

    // ── Dialog ────────────────────────────────────────────────────────────────
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
                    .padding(20.dp)
            ) {
                // ── Header ─────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryDark, RoundedCornerShape(10.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Bulk Order",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Section: Order Criteria ────────────────────────────────────
                SectionLabel("Order Criteria")
                Spacer(Modifier.height(10.dp))

                // Category filter chips
                Text("Category Filter", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BulkChip(
                        label = "All",
                        isSelected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    availableCategories.forEach { cat ->
                        BulkChip(
                            label = cat.displayName,
                            isSelected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Max quantity threshold slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Stock threshold", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "≤ $maxTotalQuantity units",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryDark
                    )
                }
                Slider(
                    value = maxTotalQuantity.toFloat(),
                    onValueChange = { maxTotalQuantity = it.roundToInt() },
                    valueRange = 1f..100f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryDark,
                        activeTrackColor = Primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 unit", fontSize = 10.sp, color = TextSecondary)
                    Text("100 units", fontSize = 10.sp, color = TextSecondary)
                }
                Text(
                    "Orders items with shelf + backroom stock at or below this value",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(Modifier.height(14.dp))

                // Cases per item slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cases per item", fontSize = 12.sp, color = TextSecondary)
                    Text(
                        "$casePacksPerItem case(s)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryDark
                    )
                }
                Slider(
                    value = casePacksPerItem.toFloat(),
                    onValueChange = { casePacksPerItem = it.roundToInt() },
                    valueRange = 1f..20f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryDark,
                        activeTrackColor = Primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 case", fontSize = 10.sp, color = TextSecondary)
                    Text("20 cases", fontSize = 10.sp, color = TextSecondary)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                // ── Section: Discount Tiers ────────────────────────────────────
                SectionLabel("Bulk Discount Tiers")
                Spacer(Modifier.height(8.dp))

                BULK_DISCOUNT_TIERS.reversed().forEach { tier ->
                    val isActive = totalCases >= tier.minCases
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "≥ ${tier.minCases} cases",
                            fontSize = 12.sp,
                            color = if (isActive) TextDark else TextSecondary,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isActive) Secondary.copy(alpha = 0.15f)
                                    else PlaceholderSurface,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                tier.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) Secondary else TextSecondary
                            )
                        }
                    }
                }

                // Next discount hint
                val hint = nextDiscountHint(totalCases)
                if (hint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "💡 $hint",
                        fontSize = 11.sp,
                        color = Primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                } else if (totalCases >= 100) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "🎉 Maximum bulk discount applied!",
                        fontSize = 11.sp,
                        color = Secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                // ── Section: Order Summary ─────────────────────────────────────
                SectionLabel("Order Summary")
                Spacer(Modifier.height(10.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryStat("${matchingItems.size}", "items")
                    SummaryStat("$totalCases", "total cases")
                    SummaryStat(
                        if (discountFraction > 0.0) "${(discountFraction * 100).toInt()}% OFF" else "—",
                        "discount",
                        valueColor = if (discountFraction > 0.0) Secondary else TextSecondary
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Cost breakdown
                if (discountFraction > 0.0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Base cost:", fontSize = 13.sp, color = TextSecondary)
                        Text(
                            baseCost.toString(),
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "You save:",
                            fontSize = 13.sp,
                            color = Secondary
                        )
                        Text(
                            savings.toString(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Secondary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Total:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryDark
                    )
                    Text(
                        finalCost.toString(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !hasItems   -> TextSecondary
                            !canAfford  -> Destructive
                            else        -> if (discountFraction > 0.0) Secondary else PrimaryDark
                        }
                    )
                }

                if (!canAfford && hasItems) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Insufficient funds — you have $money",
                        fontSize = 12.sp,
                        color = Destructive
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                // ── Section: Matching Items Preview ────────────────────────────
                SectionLabel("Matching Items (${matchingItems.size})")
                Spacer(Modifier.height(8.dp))

                if (matchingItems.isNotEmpty()) {
                    matchingItems.take(6).forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Medium)
                                Text(
                                    "${item.category.displayName} · ${item.casePack}/case · ${item.casePackCost}/case",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${item.shelfStock + item.backroomStock} in stock",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    "+${item.casePack * casePacksPerItem} units",
                                    fontSize = 11.sp,
                                    color = Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (matchingItems.size > 6) {
                        Text(
                            "…and ${matchingItems.size - 6} more item(s)",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PlaceholderSurface, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No items match the current criteria.\nTry increasing the stock threshold.",
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Action buttons ─────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = PrimaryDark, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { onConfirm(maxTotalQuantity, casePacksPerItem, selectedCategory) },
                        enabled = hasItems && canAfford,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Place Order",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ── Private helpers ────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = PrimaryDark
    )
}

@Composable
private fun SummaryStat(
    value: String,
    label: String,
    valueColor: Color = PrimaryDark,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

@Composable
private fun BulkChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val bg = if (isSelected) PrimaryDark else ChipSurface
    val fg = if (isSelected) TextWhite else ChipTextDark
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

