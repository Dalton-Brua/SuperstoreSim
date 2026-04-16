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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.ui.components.cards.InventoryItemCard
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.dialogs.BulkOrderDialog
import com.example.superstoresimulator.ui.state.InventoryUIState
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun InventoryScreen(
    state: InventoryUIState,
    money: Money,
    itemMetadataCache: ItemMetadataCache,
    currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1,
    metricsData: List<DailyMetrics> = emptyList(),
    resetTrigger: Int = 0,  // Increment this to trigger a reset
    onBuyItem: (Int) -> Unit,
    onSelectCategory: (ItemCategory?) -> Unit,
    onBulkOrder: (maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) -> Unit = { _, _, _ -> },
    onItemClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // State to hold item names from cache
    val itemNames = remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    
    // State to hold search query (preserved across navigation)
    val searchQuery = remember { mutableStateOf("") }
    
    // State for debounced search query
    val debouncedSearchQuery = remember { mutableStateOf("") }
    
    // Dialog state
    var showBulkOrderDialog by remember { mutableStateOf(false) }
    
    // Debounce search query with 200ms delay
    LaunchedEffect(searchQuery.value) {
        if (searchQuery.value.isEmpty()) {
            debouncedSearchQuery.value = ""
        } else {
            delay(200)  // Wait 200ms before applying search
            debouncedSearchQuery.value = searchQuery.value
        }
    }
    
    // Load item names from cache instead of database query
    LaunchedEffect(Unit) {
        try {
            // Get cached item names (no database access)
            itemNames.value = itemMetadataCache.getAllItemNames()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Reset local state when resetTrigger changes (triggered by clicking nav button on current screen)
    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {  // Only reset if trigger is positive (not initial value)
            searchQuery.value = ""
        }
    }

    val listState = rememberLazyListState()
    
    // Recompute filtered items whenever category, focused item, or debounced search query changes
    val filteredItems = remember(state.items, state.selectedCategory, state.focusedItemId, debouncedSearchQuery.value, itemNames.value) {
        var filtered = if (state.focusedItemId != null) {
            // If an item is focused, show only that item
            state.items.filter { item -> item.id == state.focusedItemId }
        } else {
            // Otherwise filter by category
            state.items.filter { item ->
                state.selectedCategory == null || item.category == state.selectedCategory
            }
        }
        
        // Apply search filter if debounced search query is not empty
        if (debouncedSearchQuery.value.isNotEmpty()) {
            filtered = filtered.filter { item ->
                val itemName = itemNames.value[item.id] ?: item.name
                itemName.contains(debouncedSearchQuery.value, ignoreCase = true)
            }
        }
        
        filtered
    }

    // Scroll to focused item when it becomes available
    LaunchedEffect(state.focusedItemId, filteredItems) {
        val targetId = state.focusedItemId ?: return@LaunchedEffect
        val index = filteredItems.indexOfFirst { it.id == targetId }
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
    }

    // Show bulk order dialog when requested
    if (showBulkOrderDialog) {
        BulkOrderDialog(
            allItems = state.items,
            money = money,
            onConfirm = { maxQty, casePacks, category ->
                onBulkOrder(maxQty, casePacks, category)
                showBulkOrderDialog = false
            },
            onDismiss = { showBulkOrderDialog = false }
        )
    }

    // Always show list view (detail screen is handled at MainActivity level as overlay)
    InventoryListScreen(
        state = state,
        money = money,
        currentTier = currentTier,
        searchQuery = searchQuery,
        debouncedSearchQuery = debouncedSearchQuery,
        itemNames = itemNames,
        filteredItems = filteredItems,
        listState = listState,
        onBuyItem = onBuyItem,
        onSelectCategory = onSelectCategory,
        onItemClick = onItemClick,
        onShowBulkOrderDialog = { showBulkOrderDialog = true },
        modifier = modifier
    )
}

@Composable
private fun InventoryListScreen(
    state: InventoryUIState,
    money: Money,
    currentTier: ItemUnlockTier,
    searchQuery: MutableState<String>,
    debouncedSearchQuery: MutableState<String>,
    itemNames: MutableState<Map<Int, String>>,
    filteredItems: List<InventoryItemUI>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onBuyItem: (Int) -> Unit,
    onSelectCategory: (ItemCategory?) -> Unit,
    onItemClick: (Int) -> Unit,
    onShowBulkOrderDialog: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FF))
            .padding(24.dp),
    ) {
        // ── Header row: title + bulk order button ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            ScreenHeader(
                title = "Inventory",
                money = money
            )

            // Only show Bulk Order button if TIER_2 or above is unlocked
            if (currentTier.unlockAmount >= ItemUnlockTier.TIER_2.unlockAmount) {
                Button(
                    onClick = onShowBulkOrderDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = "Bulk Order",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Bulk Order",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        InventoryCategoryBar(
            categories = ItemCategory.entries,
            selected = state.selectedCategory,
            onSelect = onSelectCategory
        )

        Spacer(Modifier.height(16.dp))

        // Search bar
        TextField(
            value = searchQuery.value,
            onValueChange = { searchQuery.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            placeholder = { Text("Search items...") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = PrimaryDark,  // Dark slate for text
                unfocusedTextColor = PrimaryDark,  // Dark slate for text
                cursorColor = Primary  // Blue cursor
            )
        )

        Spacer(Modifier.height(16.dp))

        // Item List
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredItems) { item ->
                InventoryItemCard(
                    item = item.copy(name = itemNames.value[item.id] ?: item.name),
                    canAffordBuy = money >= item.unitCost,
                    onBuy = { onBuyItem(item.id) },
                    onClick = { onItemClick(item.id) }
                )

            }
        }
    }
}

@Composable
fun InventoryCategoryBar(
    categories: List<ItemCategory>,
    selected: ItemCategory?,
    onSelect: (ItemCategory?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {

        item {
            CategoryChip(
                label = "All",
                isSelected = selected == null,
                onClick = { onSelect(null) }
            )
        }

        items(categories) { category ->
            CategoryChip(
                label = category.displayName,
                isSelected = selected == category,
                onClick = { onSelect(category) }
            )
        }
    }
}

@Composable
fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) Color(0xFF1E40AF) else Color(0xFFE2E8F0)
    val fg = if (isSelected) Color.White else Color(0xFF1E293B)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        tonalElevation = if (isSelected) 4.dp else 0.dp,
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = fg,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
internal fun InventoryItemDetailScreen(
    item: InventoryItemUI,
    money: Money,
    itemMetadataCache: ItemMetadataCache,
    currentTier: ItemUnlockTier,
    metricsData: List<DailyMetrics>,
    onBuyItem: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Get full item metadata from cache for additional details
    val fullItem = remember(item.id) { itemMetadataCache.getItem(item.id) }
    
    // Calculate margins and profit info
    val margin = item.price - item.unitCost
    val marginPercent = if (item.unitCost.cents > 0) {
        ((margin.cents.toDouble() / item.unitCost.cents) * 100).toInt()
    } else {
        0
    }
    
    val casePackProfit = margin * item.casePack
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FF))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Header with money display
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            // Money display
            Text(
                text = money.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF)
            )
        }

        Spacer(Modifier.height(24.dp))

        // Main content
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Item name and category
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            
                            // Tier badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFDEEBFF)
                            ) {
                                Text(
                                    text = "${fullItem?.tier ?: currentTier}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E40AF),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        // Description (if available from full item)
                        fullItem?.description?.let { desc ->
                            if (desc.isNotBlank()) {
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
            }
            
            // Stock Information
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Stock Levels",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Shelf Stock", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(
                                    text = "${item.shelfStock} units",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            
                            Column(horizontalAlignment = Alignment.End) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Backroom Stock", fontSize = 12.sp, color = Color(0xFF64748B))
                                    if (item.backroomFull) {
                                        Text(
                                            "FULL",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEF4444)
                                        )
                                    }
                                }
                                Text(
                                    text = "${item.backroomStock} units",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                        }
                        
                        // Total stock
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
            
            // Pricing Information
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Pricing & Profit",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        
                        DetailRow("Retail Price", item.price.toString(), Color(0xFF1E40AF))
                        DetailRow("Unit Cost", item.unitCost.toString(), Color(0xFF64748B))
                        DetailRow(
                            "Margin per Unit",
                            "$margin ($marginPercent%)",
                            if (margin.cents >= 0) Color(0xFF22C55E) else Color(0xFFEF4444)
                        )
                    }
                }
            }
            
            // Case Pack Information
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Case Pack Details",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        
                        DetailRow("Units per Case", "${item.casePack} units", Color(0xFF1E293B))
                        DetailRow("Case Pack Cost", item.casePackCost.toString(), Color(0xFF1E40AF))
                        DetailRow(
                            "Case Pack Profit",
                            casePackProfit.toString(),
                            if (casePackProfit.cents >= 0) Color(0xFF22C55E) else Color(0xFFEF4444)
                        )
                        
                        // Purchase weight (if available)
                        fullItem?.purchaseWeight?.let { baselineWeight ->
                            // Calculate percentage relative to item's baseline weight
                            // When events modify demand, they would change currentWeight
                            val currentWeight = baselineWeight  // TODO: Apply event modifiers when implemented
                            val demandPercentage = ((currentWeight / baselineWeight) * 100).toInt()
                            
                            // Green for normal/high demand (≥100%), yellow for low demand (<100%)
                            val backgroundColor = if (demandPercentage >= 100) {
                                Color(0xFFDCFCE7)  // Light green
                            } else {
                                Color(0xFFFEF3C7)  // Amber/yellow
                            }
                            val textColor = if (demandPercentage >= 100) {
                                Color(0xFF166534)  // Dark green
                            } else {
                                Color(0xFF78350F)  // Dark amber
                            }
                            
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = backgroundColor
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Customer Demand",
                                        fontSize = 12.sp,
                                        color = textColor
                                    )
                                    Text(
                                        "$demandPercentage%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Sales Analysis Card
            item {
                SalesAnalysisCard(
                    itemId = item.id,
                    metricsData = metricsData
                )
            }
            
            // Order button
            item {
                Button(
                    onClick = { onBuyItem(item.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E40AF),
                        disabledContainerColor = Color(0xFFE2E8F0)
                    ),
                    enabled = money >= item.casePackCost && !item.backroomFull,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (item.backroomFull) {
                            "Backroom Full"
                        } else if (money < item.casePackCost) {
                            "Insufficient Funds"
                        } else {
                            "Order Case Pack (${item.casePackCost})"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (money >= item.casePackCost && !item.backroomFull) Color.White else Color(0xFF94A3B8)
                    )
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Back button at bottom
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E40AF),  // Primary blue
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Back", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFF1E293B)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF64748B)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

enum class SalesTimeRange {
    DAILY, WEEKLY, MONTHLY
}

@Composable
private fun SalesAnalysisCard(
    itemId: Int,
    metricsData: List<DailyMetrics>
) {
    var selectedRange by remember { mutableStateOf(SalesTimeRange.DAILY) }
    
    // Calculate sales stats based on selected range
    val salesStats = remember(itemId, metricsData, selectedRange) {
        calculateSalesStats(itemId, metricsData, selectedRange)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sales Analysis",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            
            // Time range filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SalesTimeRange.entries.forEach { range ->
                    val isSelected = selectedRange == range
                    val bg = if (isSelected) Color(0xFF1E40AF) else Color(0xFFE2E8F0)
                    val fg = if (isSelected) Color.White else Color(0xFF1E293B)
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = bg,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRange = range }
                    ) {
                        Text(
                            text = when (range) {
                                SalesTimeRange.DAILY -> "Daily"
                                SalesTimeRange.WEEKLY -> "Weekly"
                                SalesTimeRange.MONTHLY -> "Monthly"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = fg,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            // Sales metrics
            if (salesStats.totalUnitsSold > 0) {
                DetailRow(
                    "Total Units Sold",
                    "${salesStats.totalUnitsSold} units",
                    Color(0xFF1E293B)
                )
                DetailRow(
                    "Total Revenue",
                    salesStats.totalRevenue.toString(),
                    Color(0xFF22C55E)
                )
                DetailRow(
                    "Average per ${getRangeName(selectedRange)}",
                    String.format(Locale.US, "%.1f units", salesStats.averagePerPeriod),
                    Color(0xFF64748B)
                )
                
                // Performance indicator
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        salesStats.averagePerPeriod >= 10 -> Color(0xFFDCFCE7) // High sales - light green
                        salesStats.averagePerPeriod >= 5 -> Color(0xFFFEF3C7)  // Medium - amber
                        else -> Color(0xFFFEE2E2)  // Low - light red
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Performance",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                salesStats.averagePerPeriod >= 10 -> Color(0xFF166534)
                                salesStats.averagePerPeriod >= 5 -> Color(0xFF78350F)
                                else -> Color(0xFF991B1B)
                            }
                        )
                        Text(
                            when {
                                salesStats.averagePerPeriod >= 10 -> "High Demand"
                                salesStats.averagePerPeriod >= 5 -> "Moderate"
                                else -> "Low Sales"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                salesStats.averagePerPeriod >= 10 -> Color(0xFF166534)
                                salesStats.averagePerPeriod >= 5 -> Color(0xFF78350F)
                                else -> Color(0xFF991B1B)
                            }
                        )
                    }
                }
            } else {
                // No sales data
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "No sales recorded for this ${getRangeName(selectedRange).lowercase()}",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun getRangeName(range: SalesTimeRange): String {
    return when (range) {
        SalesTimeRange.DAILY -> "Day"
        SalesTimeRange.WEEKLY -> "Week"
        SalesTimeRange.MONTHLY -> "Month"
    }
}

data class SalesStats(
    val totalUnitsSold: Int,
    val totalRevenue: Money,
    val averagePerPeriod: Double,
    val daysAnalyzed: Int
)

private fun calculateSalesStats(
    itemId: Int,
    metricsData: List<DailyMetrics>,
    range: SalesTimeRange
): SalesStats {
    if (metricsData.isEmpty()) {
        return SalesStats(0, Money.ZERO, 0.0, 0)
    }
    
    // Determine how many days to look back
    val daysToAnalyze = when (range) {
        SalesTimeRange.DAILY -> 1  // Just today/last completed day
        SalesTimeRange.WEEKLY -> 7
        SalesTimeRange.MONTHLY -> 30
    }
    
    // Take the most recent N days
    val relevantDays = metricsData.take(daysToAnalyze)
    
    var totalUnits = 0
    var totalRevenue = Money.ZERO
    
    relevantDays.forEach { dayMetrics ->
        // Find sales events for this specific item
        val itemSales = dayMetrics.soldItemEvents.filter { it.itemId == itemId }
        itemSales.forEach { event ->
            totalUnits += event.quantitySold
            totalRevenue += event.revenue
        }
    }
    
    val actualDaysAnalyzed = relevantDays.size
    val averagePerDay = if (actualDaysAnalyzed > 0) {
        totalUnits.toDouble() / actualDaysAnalyzed
    } else {
        0.0
    }
    
    return SalesStats(
        totalUnitsSold = totalUnits,
        totalRevenue = totalRevenue,
        averagePerPeriod = averagePerDay,
        daysAnalyzed = actualDaysAnalyzed
    )
}

