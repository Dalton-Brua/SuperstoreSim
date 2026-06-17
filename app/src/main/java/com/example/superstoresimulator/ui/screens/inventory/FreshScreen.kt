package com.example.superstoresimulator.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.DestructiveDark
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextPrimary
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import kotlin.math.max

/**
 * Screen showing perishable items grouped by category with expiration tracking.
 * Shows category cards, and when clicked, displays items with expiration progress bars.
 */
@Composable
fun FreshScreen(
    items: List<InventoryItemUI>,
    money: Money,
    currentDay: Int,
    incompleteFreshOrdersCount: Int = 0,
    hasFreshHandler: Boolean = false,
    freshAutoOrderConfig: com.example.superstoresimulator.domain.FreshAutoOrderConfig = com.example.superstoresimulator.domain.FreshAutoOrderConfig(),
    freshAutoOrderResearched: Boolean = false,
    onItemClick: (Int) -> Unit = {},
    onFreshBulkOrder: () -> Unit = {},
    onViewIncompleteOrders: () -> Unit = {},
    onUpdateFreshAutoOrderConfig: (enabled: Boolean, threshold: Int, casePacks: Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    // Filter to only perishable items
    val perishableItems = remember(items) {
        items.filter { it.shelfLifeDays != null }
    }
    
    // Group by category
    val itemsByCategory = remember(perishableItems) {
        perishableItems.groupBy { it.category }
    }
    
    // Track selected category
    var selectedCategory by remember { mutableStateOf<ItemCategory?>(null) }
    var showAutoOrderConfigDialog by remember { mutableStateOf(false) }

    if (showAutoOrderConfigDialog) {
        com.example.superstoresimulator.ui.dialogs.AutoOrderConfigDialog(
            title = "Fresh Auto-Order Settings",
            subtitle = "Fresh handlers auto-order when idle and stock is low",
            enabled = freshAutoOrderConfig.enabled,
            minStockThreshold = freshAutoOrderConfig.minStockThreshold,
            casePacksPerItem = freshAutoOrderConfig.casePacksPerItem,
            onConfigChanged = onUpdateFreshAutoOrderConfig,
            onDismiss = { showAutoOrderConfigDialog = false }
        )
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
            .padding(24.dp)
    ) {
        if (selectedCategory == null) {
            // Show category overview
            ScreenHeader(
                title = "Fresh Goods",
                money = money
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = "Track expiration dates for perishable items",
                fontSize = 14.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onFreshBulkOrder,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        modifier = Modifier
                            .height(16.dp)
                            .width(16.dp),
                        tint = TextWhite
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Fresh Bulk Order", fontSize = 12.sp, color = TextWhite)
                }

                if (freshAutoOrderResearched) {
                    Button(
                        onClick = { showAutoOrderConfigDialog = true },
                        enabled = hasFreshHandler,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            disabledContainerColor = ProgressBarTrack,
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Auto-Order",
                            fontSize = 12.sp,
                            color = if (hasFreshHandler) TextWhite else TextMuted
                        )
                    }
                }
            }

            if (incompleteFreshOrdersCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                ) {
                    Button(
                        onClick = onViewIncompleteOrders,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DestructiveDark
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Incomplete Fresh Orders ($incompleteFreshOrdersCount)",
                            fontSize = 12.sp,
                            color = TextWhite
                        )
                    }
                }
            }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(itemsByCategory.entries.sortedBy { it.key.name }) { (category, categoryItems) ->
                    CategoryCard(
                        category = category,
                        items = categoryItems,
                        currentDay = currentDay,
                        onClick = { selectedCategory = category }
                    )
                }
            }
        } else {
            // Show items in selected category
            val categoryItems = itemsByCategory[selectedCategory] ?: emptyList()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedCategory = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PrimaryDark)
                }
                
                Text(
                    text = selectedCategory!!.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryItems.sortedBy { item ->
                    // Sort by closest expiration
                    item.closestExpirationDay ?: Int.MAX_VALUE
                }) { item ->
                    ExpiringItemCard(
                        item = item,
                        currentDay = currentDay,
                        onClick = { onItemClick(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: ItemCategory,
    items: List<InventoryItemUI>,
    currentDay: Int,
    onClick: () -> Unit
) {
    // Find the item closest to expiring in this category
    val closestExpiration = items.mapNotNull { it.closestExpirationDay }.minOrNull()
    val daysUntilExpiration = closestExpiration?.let { it - currentDay }
    
    // Calculate freshness percentage (0-100)
    val freshnessPercent = if (closestExpiration != null) {
        val oldestItem = items.firstOrNull { it.closestExpirationDay == closestExpiration }
        oldestItem?.let {
            val shelfLife = it.shelfLifeDays ?: return@let 100
            val daysRemaining = max(0, closestExpiration - currentDay)
            ((daysRemaining.toFloat() / shelfLife) * 100).toInt().coerceIn(0, 100)
        } ?: 100
    } else 100
    
    val freshnessColor = freshnessColorFor(freshnessPercent)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryDark
                )
                
                Text(
                    text = "${items.size} items",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
            
            if (daysUntilExpiration != null) {
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            daysUntilExpiration <= 0 -> "Expired"
                            daysUntilExpiration == 1 -> "Expires in 1 day"
                            else -> "Expires in $daysUntilExpiration days"
                        },
                        fontSize = 14.sp,
                        color = if (daysUntilExpiration <= 1) DestructiveDark else TextSecondary,
                        fontWeight = if (daysUntilExpiration <= 1) FontWeight.Bold else FontWeight.Normal
                    )

                    Text(
                        text = "$freshnessPercent% fresh",
                        fontSize = 14.sp,
                        color = freshnessColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Freshness progress bar
                LinearProgressIndicator(
                    progress = { freshnessPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = freshnessColor,
                    trackColor = ProgressBarTrack,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No items in stock",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ExpiringItemCard(
    item: InventoryItemUI,
    currentDay: Int,
    onClick: () -> Unit = {}
) {
    val closestExpiration = item.closestExpirationDay
    val daysUntilExpiration = closestExpiration?.let { it - currentDay }
    
    // Calculate freshness percentage
    val freshnessPercent = if (closestExpiration != null && item.shelfLifeDays != null) {
        val daysRemaining = max(0, closestExpiration - currentDay)
        ((daysRemaining.toFloat() / item.shelfLifeDays) * 100).toInt().coerceIn(0, 100)
    } else 100
    
    val freshnessColor = freshnessColorFor(freshnessPercent)

    val totalStock = item.shelfStock + item.backroomStock

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryDark
                    )
                    
                    Spacer(Modifier.height(4.dp))
                    
                    Text(
                        text = "Shelf: ${item.shelfStock} • Backroom: ${item.backroomStock}",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
                
                Text(
                    text = item.price.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
            }
            
            if (totalStock > 0 && closestExpiration != null && daysUntilExpiration != null) {
                Spacer(Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            daysUntilExpiration <= 0 -> "⚠️ Expired"
                            daysUntilExpiration == 1 -> "⚠️ Expires tomorrow"
                            daysUntilExpiration <= 3 -> "⚠️ Expires in $daysUntilExpiration days"
                            else -> "Expires in $daysUntilExpiration days"
                        },
                        fontSize = 13.sp,
                        color = if (daysUntilExpiration <= 3) DestructiveDark else TextSecondary,
                        fontWeight = if (daysUntilExpiration <= 3) FontWeight.Bold else FontWeight.Normal
                    )

                    Text(
                        text = "$freshnessPercent%",
                        fontSize = 13.sp,
                        color = freshnessColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { freshnessPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = freshnessColor,
                    trackColor = ProgressBarTrack,
                )
            } else if (totalStock == 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Out of stock",
                    fontSize = 13.sp,
                    color = TextMuted
                )
            }
        }
    }
}

