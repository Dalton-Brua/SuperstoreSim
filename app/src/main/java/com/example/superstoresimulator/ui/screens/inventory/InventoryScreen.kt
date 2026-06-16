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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.research.ResearchGates
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.ui.components.cards.InventoryItemCard
import com.example.superstoresimulator.ui.components.common.ScreenHeader
import com.example.superstoresimulator.ui.dialogs.BulkOrderDialog
import com.example.superstoresimulator.ui.state.InventoryUIState
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.theme.CardWhite
import com.example.superstoresimulator.ui.theme.ChipSurface
import com.example.superstoresimulator.ui.theme.ChipTextDark
import com.example.superstoresimulator.ui.theme.LightBackground
import com.example.superstoresimulator.ui.theme.DestructiveDark
import com.example.superstoresimulator.ui.theme.Primary
import com.example.superstoresimulator.ui.theme.PrimaryDark
import com.example.superstoresimulator.ui.theme.ProgressBarTrack
import com.example.superstoresimulator.ui.theme.TextMuted
import com.example.superstoresimulator.ui.theme.TextSecondary
import com.example.superstoresimulator.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InventoryScreen(
    state: InventoryUIState,
    money: Money,
    researchedUpgrades: Set<String> = emptySet(),
    metricsData: List<DailyMetrics> = emptyList(),
    resetTrigger: Int = 0,
    hasFastStocker: Boolean = false,
    hasStockingManager: Boolean = false,
    incompleteNormalOrdersCount: Int = 0,
    normalAutoOrderConfig: com.example.superstoresimulator.domain.NormalAutoOrderConfig = com.example.superstoresimulator.domain.NormalAutoOrderConfig(),
    onBuyItem: (Int) -> Unit,
    onSelectCategory: (ItemCategory?) -> Unit,
    onBulkOrder: (maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) -> Unit = { _, _, _ -> },
    onUpdateNormalAutoOrderConfig: (enabled: Boolean, threshold: Int, casePacks: Int) -> Unit = { _, _, _ -> },
    onViewIncompleteNormalOrders: () -> Unit = {},
    onItemClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Item names derived from UIState items (no cache access needed)
    val itemNames = remember(state.items) { state.items.associate { it.id to it.name } }

    // State to hold search query (preserved across navigation)
    val searchQuery = remember { mutableStateOf("") }

    // State for debounced search query
    val debouncedSearchQuery = remember { mutableStateOf("") }

    // Dialog state
    var showBulkOrderDialog by remember { mutableStateOf(false) }
    var showAutoOrderConfigDialog by remember { mutableStateOf(false) }

    // Debounce search query with 200ms delay
    LaunchedEffect(searchQuery.value) {
        if (searchQuery.value.isEmpty()) {
            debouncedSearchQuery.value = ""
        } else {
            delay(200)
            debouncedSearchQuery.value = searchQuery.value
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
    val filteredItems = remember(state.items, state.selectedCategory, state.focusedItemId, debouncedSearchQuery.value) {
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
                item.name.contains(debouncedSearchQuery.value, ignoreCase = true)
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

    if (showAutoOrderConfigDialog) {
        if (hasStockingManager) {
            com.example.superstoresimulator.ui.dialogs.AutoOrderConfigDialog(
                title = "Auto-Order Settings",
                subtitle = "Stocking Manager proactively orders items below threshold each tick",
                enabled = normalAutoOrderConfig.enabled,
                minStockThreshold = normalAutoOrderConfig.minStockThreshold,
                casePacksPerItem = normalAutoOrderConfig.casePacksPerItem,
                onConfigChanged = onUpdateNormalAutoOrderConfig,
                onDismiss = { showAutoOrderConfigDialog = false }
            )
        } else {
            com.example.superstoresimulator.ui.dialogs.BasicAutoOrderInfoDialog(
                enabled = normalAutoOrderConfig.enabled,
                onToggle = { enabled -> onUpdateNormalAutoOrderConfig(enabled, normalAutoOrderConfig.minStockThreshold, normalAutoOrderConfig.casePacksPerItem) },
                onDismiss = { showAutoOrderConfigDialog = false }
            )
        }
    }

    // Always show list view (detail screen is handled at MainActivity level as overlay)
    InventoryListScreen(
        state = state,
        money = money,
        researchedUpgrades = researchedUpgrades,
        searchQuery = searchQuery,
        debouncedSearchQuery = debouncedSearchQuery,
        itemNames = itemNames,
        filteredItems = filteredItems,
        listState = listState,
        hasFastStocker = hasFastStocker,
        hasStockingManager = hasStockingManager,
        incompleteNormalOrdersCount = incompleteNormalOrdersCount,
        onBuyItem = onBuyItem,
        onSelectCategory = onSelectCategory,
        onItemClick = onItemClick,
        onShowBulkOrderDialog = { showBulkOrderDialog = true },
        onShowAutoOrderConfig = { showAutoOrderConfigDialog = true },
        onViewIncompleteNormalOrders = onViewIncompleteNormalOrders,
        modifier = modifier
    )
}

@Composable
private fun InventoryListScreen(
    state: InventoryUIState,
    money: Money,
    researchedUpgrades: Set<String>,
    searchQuery: MutableState<String>,
    debouncedSearchQuery: MutableState<String>,
    itemNames: Map<Int, String>,
    filteredItems: List<InventoryItemUI>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    hasFastStocker: Boolean = false,
    hasStockingManager: Boolean = false,
    incompleteNormalOrdersCount: Int = 0,
    onBuyItem: (Int) -> Unit,
    onSelectCategory: (ItemCategory?) -> Unit,
    onItemClick: (Int) -> Unit,
    onShowBulkOrderDialog: () -> Unit,
    onShowAutoOrderConfig: () -> Unit = {},
    onViewIncompleteNormalOrders: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (ResearchGates.BULK_ORDERING in researchedUpgrades) {
                    Button(
                        onClick = onShowBulkOrderDialog,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.LocalShipping,
                            contentDescription = "Bulk Order",
                            modifier = Modifier.size(16.dp),
                            tint = TextWhite
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Bulk Order",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextWhite
                        )
                    }
                    }

                    Button(
                        onClick = onShowAutoOrderConfig,
                        enabled = hasFastStocker,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasStockingManager) Primary else PrimaryDark.copy(alpha = 0.7f),
                            disabledContainerColor = ProgressBarTrack,
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            if (hasStockingManager) Icons.Default.Settings else Icons.Default.LocalShipping,
                            contentDescription = "Auto-Order",
                            modifier = Modifier.size(16.dp),
                            tint = if (hasFastStocker) TextWhite else TextMuted
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            when {
                                hasStockingManager -> "Auto-Order"
                                hasFastStocker -> "Auto-Order"
                                else -> "Auto-Order (T2)"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (hasFastStocker) TextWhite else TextMuted
                        )
                    }
                }
            }
        // Incomplete normal orders banner
        if (incompleteNormalOrdersCount > 0) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onViewIncompleteNormalOrders,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DestructiveDark),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "Incomplete Auto-Orders ($incompleteNormalOrdersCount)",
                    fontSize = 12.sp,
                    color = TextWhite
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Only show chips for categories that have at least one accessible item.
        // state.items is already research-gated (see MemoizedInventoryMapper), so a
        // research-locked category simply has no items here and its chip is hidden.
        val availableCategories = remember(state.items) {
            ItemCategory.entries.filter { cat -> state.items.any { it.category == cat } }
        }
        InventoryCategoryBar(
            categories = availableCategories,
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
                focusedContainerColor = CardWhite,
                unfocusedContainerColor = CardWhite,
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
                    item = item.copy(name = itemNames[item.id] ?: item.name),
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
    val bg = if (isSelected) PrimaryDark else ChipSurface
    val fg = if (isSelected) TextWhite else ChipTextDark

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
    currentDay: Int,
    metricsData: List<DailyMetrics>,
    shelfBatches: List<ItemBatch> = emptyList(),
    backroomBatches: List<ItemBatch> = emptyList(),
    pendingDeliveries: List<com.example.superstoresimulator.ui.state.TruckOrderLineUI> = emptyList(),
    onCancelOrderLine: (itemId: Int, truckId: Int) -> Unit = { _, _ -> },
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit = { _, _ -> },
    onBuyItem: (Int) -> Unit,
    onSetItemOverride: (Int, Int) -> Unit = { _, _ -> },
    onClearMarkdown: (Int) -> Unit = { _ -> },
    itemOverridePercent: Int = 0,
    itemPricingUnlocked: Boolean = false,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBackground)
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
                color = PrimaryDark
            )
        }

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            item { ItemHeaderCard(item = item) }

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

            item {
                PricingCard(
                    item = item,
                    itemOverridePercent = itemOverridePercent,
                    onSetItemOverride = if (itemPricingUnlocked) onSetItemOverride else null,
                    onClearMarkdown = onClearMarkdown,
                )
            }

            item { CasePackDetailsCard(item = item) }

            item { SalesAnalysisCard(itemId = item.id, metricsData = metricsData) }

            item {
                PendingDeliveriesCard(
                    itemId = item.id,
                    pendingDeliveries = pendingDeliveries,
                    onCancelOrderLine = onCancelOrderLine,
                    onDecrementOrderLine = onDecrementOrderLine
                )
            }

            // Order button (hidden for vendor-stocked items)
            if (item.vendorName == null) {
                item {
                    Button(
                        onClick = { onBuyItem(item.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryDark,
                            disabledContainerColor = ChipSurface
                        ),
                        enabled = money >= item.casePackCost,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when {
                                money < item.casePackCost -> "Insufficient Funds"
                                else -> "Order Case Pack (${item.casePackCost})"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (money >= item.casePackCost) TextWhite else TextMuted
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = PrimaryDark,
                contentColor = TextWhite
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
    researchedUpgrades: Set<String> = emptySet(),
    currentDay: Int,
    metricsData: List<DailyMetrics> = emptyList(),
    resetTrigger: Int = 0,
    initialTab: Int = 0,
    incompleteFreshOrdersCount: Int = 0,
    incompleteNormalOrdersCount: Int = 0,
    hasFastStocker: Boolean = false,
    hasStockingManager: Boolean = false,
    hasFreshHandler: Boolean = false,
    freshAutoOrderConfig: com.example.superstoresimulator.domain.FreshAutoOrderConfig = com.example.superstoresimulator.domain.FreshAutoOrderConfig(),
    normalAutoOrderConfig: com.example.superstoresimulator.domain.NormalAutoOrderConfig = com.example.superstoresimulator.domain.NormalAutoOrderConfig(),
    deliveries: com.example.superstoresimulator.ui.state.DeliveryUIState = com.example.superstoresimulator.ui.state.DeliveryUIState(),
    onTabChanged: (Int) -> Unit = {},
    onBuyItem: (Int) -> Unit,
    onSelectCategory: (ItemCategory?) -> Unit,
    onBulkOrder: (maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) -> Unit = { _, _, _ -> },
    onFreshBulkOrder: () -> Unit = {},
    onViewIncompleteOrders: () -> Unit = {},
    onViewIncompleteNormalOrders: () -> Unit = {},
    onUpdateFreshAutoOrderConfig: (enabled: Boolean, threshold: Int, casePacks: Int) -> Unit = { _, _, _ -> },
    onUpdateNormalAutoOrderConfig: (enabled: Boolean, threshold: Int, casePacks: Int) -> Unit = { _, _, _ -> },
    onCancelOrderLine: (itemId: Int, truckId: Int) -> Unit = { _, _ -> },
    onDecrementOrderLine: (itemId: Int, truckId: Int) -> Unit = { _, _ -> },
    onRequestEarlyTruck: () -> Unit = {},
    onItemClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val showFreshTab = ResearchGates.hasFreshSubsystem(researchedUpgrades)

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
            containerColor = CardWhite,
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
                    unselectedContentColor = TextSecondary
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
                    researchedUpgrades = researchedUpgrades,
                    metricsData = metricsData,
                    resetTrigger = resetTrigger,
                    hasFastStocker = hasFastStocker,
                    hasStockingManager = hasStockingManager,
                    incompleteNormalOrdersCount = incompleteNormalOrdersCount,
                    normalAutoOrderConfig = normalAutoOrderConfig,
                    onBuyItem = onBuyItem,
                    onSelectCategory = onSelectCategory,
                    onBulkOrder = onBulkOrder,
                    onUpdateNormalAutoOrderConfig = onUpdateNormalAutoOrderConfig,
                    onViewIncompleteNormalOrders = onViewIncompleteNormalOrders,
                    onItemClick = onItemClick
                )
                showFreshTab && page == 1 -> FreshScreen(
                    items = state.items,
                    money = money,
                    currentDay = currentDay,
                    incompleteFreshOrdersCount = incompleteFreshOrdersCount,
                    hasFreshHandler = hasFreshHandler,
                    freshAutoOrderConfig = freshAutoOrderConfig,
                    onItemClick = onItemClick,
                    onFreshBulkOrder = onFreshBulkOrder,
                    onViewIncompleteOrders = onViewIncompleteOrders,
                    onUpdateFreshAutoOrderConfig = onUpdateFreshAutoOrderConfig,
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
