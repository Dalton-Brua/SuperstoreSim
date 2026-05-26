package com.example.superstoresimulator.ui.screens.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.ui.components.cards.InventoryItemCard
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.dialogs.BulkOrderDialog
import com.example.superstoresimulator.ui.state.InventoryUIState
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
        
        // Exclude fresh items (those with shelfLifeDays) - they only appear in Fresh tab
        filtered = filtered.filter { item -> item.shelfLifeDays == null }
        
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            ScreenHeader(
                title = "Inventory",
                money = money,
                modifier = Modifier.weight(1f)
            )

            // Only show Bulk Order button if TIER_2 or above is unlocked
            if (currentTier.unlockAmount >= ItemUnlockTier.TIER_2.unlockAmount) {
                Button(
                    onClick = onShowBulkOrderDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
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
                    canAffordBuy = money >= item.casePackCost,
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
    // Filter out fresh-related categories (only show in Fresh tab)
    val freshCategories = setOf(ItemCategory.DAIRY, ItemCategory.BAKERY, ItemCategory.PRODUCE, ItemCategory.MEAT, ItemCategory.FROZEN)
    val inventoryCategories = categories.filter { it !in freshCategories }
    
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

        items(inventoryCategories) { category ->
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
    currentDay: Int,
    metricsData: List<DailyMetrics>,
    shelfBatches: List<ItemBatch> = emptyList(),
    backroomBatches: List<ItemBatch> = emptyList(),
    pendingDeliveries: List<com.example.superstoresimulator.ui.state.TruckOrderLineUI> = emptyList(),
    onCancelOrderLine: (itemId: Int, truckId: Int) -> Unit = { _, _ -> },
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit = { _, _ -> },
    onBuyItem: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fullItem = remember(item.id) { itemMetadataCache.getItem(item.id) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F8FF))
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Money display header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = money.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF)
            )
        }

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item { ItemHeaderCard(item = item, fullItem = fullItem, currentTier = currentTier) }

            item { StockLevelsCard(item = item) }

            // Batch details (perishable with full batch data)
            if (item.shelfLifeDays != null && (shelfBatches.isNotEmpty() || backroomBatches.isNotEmpty())) {
                item {
                    BatchDetailsCard(
                        item = item,
                        shelfBatches = shelfBatches,
                        backroomBatches = backroomBatches,
                        currentDay = currentDay
                    )
                }
            } else if (item.shelfLifeDays != null && item.closestExpirationDay != null) {
                // Fallback freshness card
                item { FreshnessCard(item = item, currentDay = currentDay) }
            }

            item { PricingCard(item = item) }

            item { CasePackDetailsCard(item = item, fullItem = fullItem) }

            item { SalesAnalysisCard(itemId = item.id, metricsData = metricsData) }

            item {
                PendingDeliveriesCard(
                    itemId = item.id,
                    pendingDeliveries = pendingDeliveries,
                    onCancelOrderLine = onCancelOrderLine,
                    onDecrementOrderLine = onDecrementOrderLine
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
                        text = when {
                            item.backroomFull -> "Backroom Full"
                            money < item.casePackCost -> "Insufficient Funds"
                            else -> "Order Case Pack (${item.casePackCost})"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (money >= item.casePackCost && !item.backroomFull) Color.White else Color(0xFF94A3B8)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF1E40AF),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Back", fontWeight = FontWeight.Bold)
        }
    }
}



/**
 * Wrapper screen that adds tabs to Inventory screen (Inventory / Fresh).
 * Fresh tab only appears when perishable items are unlocked (TIER_2+).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InventoryAndFreshScreen(
    state: InventoryUIState,
    money: Money,
    itemMetadataCache: ItemMetadataCache,
    currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1,
    currentDay: Int,
    metricsData: List<DailyMetrics> = emptyList(),
    resetTrigger: Int = 0,
    initialTab: Int = 0,
    incompleteFreshOrdersCount: Int = 0,
    deliveries: com.example.superstoresimulator.ui.state.DeliveryUIState = com.example.superstoresimulator.ui.state.DeliveryUIState(),
    onTabChanged: (Int) -> Unit = {},
    onBuyItem: (Int) -> Unit,
    onSelectCategory: (ItemCategory?) -> Unit,
    onBulkOrder: (maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) -> Unit = { _, _, _ -> },
    onFreshBulkOrder: () -> Unit = {},
    onViewIncompleteOrders: () -> Unit = {},
    onCancelOrderLine: (itemId: Int, truckId: Int) -> Unit = { _, _ -> },
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit = { _, _ -> },
    onRequestEarlyTruck: () -> Unit = {},
    onItemClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Only show Fresh tab if TIER_2 or above (first perishables unlock)
    val showFreshTab = currentTier.unlockAmount >= ItemUnlockTier.TIER_2.unlockAmount

    // Deliveries tab is always visible (truck system replaces instant delivery for all tiers)
    val tabs = if (showFreshTab) listOf("Inventory", "Fresh", "Deliveries") else listOf("Inventory", "Deliveries")

    // Pager state for tab navigation
    val pagerState = rememberPagerState(
        pageCount = { tabs.size },
        initialPage = initialTab.coerceIn(0, tabs.size - 1)
    )
    val coroutineScope = rememberCoroutineScope()

    // Track selected tab
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, tabs.size - 1)) }

    // Update selectedTab when initialTab changes from outside
    LaunchedEffect(initialTab) {
        val clamped = initialTab.coerceIn(0, tabs.size - 1)
        if (selectedTab != clamped) {
            selectedTab = clamped
            if (pagerState.currentPage != clamped) {
                pagerState.animateScrollToPage(clamped)
            }
        }
    }

    // Update selectedTab when user swipes to a different page
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            if (selectedTab != pagerState.currentPage) {
                selectedTab = pagerState.currentPage
                onTabChanged(pagerState.currentPage)
            }
        }
    }

    Column(modifier = modifier
        .fillMaxSize()
        .background(LightBackground)
        .statusBarsPadding()
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = PrimaryDark,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        selectedTab = index
                        onTabChanged(index)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) },
                    selectedContentColor = Primary,
                    unselectedContentColor = Color(0xFF64748B)
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            // Tab layout depends on whether Fresh tab is visible:
            //   showFreshTab=true  → 0=Inventory, 1=Fresh, 2=Deliveries
            //   showFreshTab=false → 0=Inventory, 1=Deliveries
            when {
                page == 0 -> InventoryScreen(
                    state = state,
                    money = money,
                    itemMetadataCache = itemMetadataCache,
                    currentTier = currentTier,
                    metricsData = metricsData,
                    resetTrigger = resetTrigger,
                    onBuyItem = onBuyItem,
                    onSelectCategory = onSelectCategory,
                    onBulkOrder = onBulkOrder,
                    onItemClick = onItemClick
                )
                showFreshTab && page == 1 -> FreshScreen(
                    items = state.items,
                    money = money,
                    currentDay = currentDay,
                    incompleteFreshOrdersCount = incompleteFreshOrdersCount,
                    onItemClick = onItemClick,
                    onFreshBulkOrder = onFreshBulkOrder,
                    onViewIncompleteOrders = onViewIncompleteOrders
                )
                else -> DeliveriesScreen(
                    deliveries = deliveries,
                    money = money,
                    currentDay = currentDay,
                    onCancelOrderLine = onCancelOrderLine,
                    onDecrementOrderLine = onDecrementOrderLine,
                    onRequestEarlyTruck = onRequestEarlyTruck,
                )
            }
        }
    }
}
