package com.example.superstoresimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.Screen
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.viewmodels.GameViewModel
import com.example.superstoresimulator.ui.screens.home.StoreHomeScreen
import com.example.superstoresimulator.ui.screens.inventory.InventoryAndFreshScreen
import com.example.superstoresimulator.ui.screens.inventory.InventoryItemDetailScreen
import com.example.superstoresimulator.ui.screens.metrics.MetricsScreen
import com.example.superstoresimulator.ui.screens.sales.SalesHistoryScreen
import com.example.superstoresimulator.ui.screens.staff.StaffAndUnlocksScreen
import com.example.superstoresimulator.ui.screens.staff.EntityTypeDetailScreen
import com.example.superstoresimulator.ui.dialogs.EndOfDayReportDialog
import com.example.superstoresimulator.ui.dialogs.FreshBulkOrderDialog
import com.example.superstoresimulator.ui.dialogs.IncompleteOrdersDialog
import com.example.superstoresimulator.ui.theme.NavBarBackground
import com.example.superstoresimulator.ui.theme.NavBarSelectedIcon
import com.example.superstoresimulator.ui.theme.NavBarSelectedText
import com.example.superstoresimulator.ui.theme.NavBarIndicator
import com.example.superstoresimulator.ui.theme.NavBarUnselectedIcon
import com.example.superstoresimulator.ui.theme.NavBarUnselectedText
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var itemDao: ItemDao
    
    // Keep reference to ViewModel to save on lifecycle events
    private var gameViewModel: GameViewModel? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SuperstoreSimulatorTheme(
                dynamicColor = false  // Disable dynamic theming for consistent colors across all devices
            ) {
                // Instantiate ViewModel (Hilt handles dependency injection automatically)
                val viewModel: GameViewModel = viewModel()
                gameViewModel = viewModel // Keep reference for lifecycle callbacks
                var currentScreen by remember { mutableStateOf(Screen.GAME) }
                var selectedStaffTab by remember { mutableStateOf(0) }
                var inventoryResetTrigger by remember { mutableStateOf(0) }
                var selectedInventoryItemId by remember { mutableStateOf<Int?>(null) }
                
                // Fresh auto-order dialog states
                var showFreshBulkOrderDialog by remember { mutableStateOf(false) }
                var showIncompleteOrdersDialog by remember { mutableStateOf(false) }

                // Snackbar for truck order confirmations
                val snackbarHostState = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    viewModel.snackbarMessage.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                // Collect UI state from ViewModel's StateFlow
                val uiState by viewModel.uiState.collectAsState()

                // Show loading indicator while uiState is null
                if (uiState == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(ComposeColor(0xFFF5F8FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading items...", fontSize = 18.sp, color = ComposeColor(0xFF1E40AF))
                    }
                    return@SuperstoreSimulatorTheme
                }

                // Extract non-null uiState to avoid smart cast issues with delegated properties
                val state = uiState!!

                // List of main navigation screens (excluding detail screens)
                val mainScreens = remember {
                    listOf(Screen.GAME, Screen.INVENTORY, Screen.STAFF, Screen.HISTORY, Screen.METRICS)
                }
                
                // Pager state for main screen navigation
                val pagerState = rememberPagerState(pageCount = { mainScreens.size })
                val coroutineScope = rememberCoroutineScope()
                
                // Flag to prevent sync loops during programmatic navigation
                var isNavigatingProgrammatically by remember { mutableStateOf(false) }

                // Sync currentScreen with pager (only when user swipes, not when nav button clicked)
                LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
                    if (!isNavigatingProgrammatically && !pagerState.isScrollInProgress) {
                        // User finished swiping to a new page
                        currentScreen = mainScreens[pagerState.currentPage]
                    }
                }

                Box(modifier = Modifier.statusBarsPadding()) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            BottomNavBar(
                                current = currentScreen,
                                onSelect = { screen ->
                                    // Disable navigation when overlay screens are open
                                    if (currentScreen == Screen.STAFF_ENTITY_LIST || selectedInventoryItemId != null) {
                                        return@BottomNavBar
                                    }
                                    
                                    // If clicking the current screen button, reset its state
                                    if (currentScreen == screen) {
                                        when (screen) {
                                            Screen.INVENTORY -> {
                                                // Reset inventory filters and trigger local state reset
                                                viewModel.onEvent(GameEvent.SelectItemCategory(null))
                                                viewModel.onEvent(GameEvent.FocusInventoryItem(null))
                                                inventoryResetTrigger++  // Trigger reset of search bar and detail view
                                                selectedInventoryItemId = null  // Close detail screen if open
                                            }
                                            Screen.STAFF -> {
                                                // Reset to Staff tab (tab 0)
                                                selectedStaffTab = 0
                                            }
                                            else -> {
                                                // Other screens don't need reset yet
                                            }
                                        }
                                    } else {
                                        // Navigate to different screen
                                        val targetIndex = mainScreens.indexOf(screen)
                                        if (targetIndex != -1) {
                                            isNavigatingProgrammatically = true
                                            currentScreen = screen
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(targetIndex)
                                                // Clear flag after animation completes
                                                isNavigatingProgrammatically = false
                                            }
                                        }
                                    }
                                },
                                pendingRefundsCount = state.app.pendingRefunds
                            )
                        }
                    ) { paddingValues ->
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = currentScreen !in listOf(Screen.STAFF_ENTITY_LIST) && selectedInventoryItemId == null
                        ) { page ->
                            when (mainScreens[page]) {
                            Screen.GAME -> StoreHomeScreen(
                                state = state,
                                onStoreNameChange = { viewModel.onEvent(GameEvent.ChangeStoreName(it)) },
                                onProcessRefundLine = { refundId: Int, itemId: Int, qty: Int -> viewModel.onEvent(GameEvent.ProcessRefundLine(refundId, itemId, qty)) },
                                onViewItem = { itemId: Int -> viewModel.onEvent(GameEvent.FocusInventoryItem(itemId)) },
                                itemDao = itemDao,
                                onNavigateToInventory = { 
                                    val targetIndex = mainScreens.indexOf(Screen.INVENTORY)
                                    if (targetIndex != -1) {
                                        isNavigatingProgrammatically = true
                                        currentScreen = Screen.INVENTORY
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(targetIndex)
                                            isNavigatingProgrammatically = false
                                        }
                                    }
                                },
                                modifier = Modifier.padding(paddingValues),
                                onSpeedChanged = { multiplier: Float -> viewModel.onEvent(GameEvent.SetGameSpeed(multiplier)) },
                                onOpenStore = { viewModel.onEvent(GameEvent.ToggleStore) },
                                onSetPlayerRole = { role: PlayerRole -> viewModel.onEvent(GameEvent.SetPlayerRole(role)) },
                                onSkipDay = { viewModel.onEvent(GameEvent.SkipDay) },
                                onUpgradeStore = { viewModel.onEvent(GameEvent.UpgradeStoreSize) },
                                onUnlockNextTier = { viewModel.onEvent(GameEvent.UnlockNextTier) },
                                onNavigateToUnlocks = { 
                                    val targetIndex = mainScreens.indexOf(Screen.STAFF)
                                    if (targetIndex != -1) {
                                        isNavigatingProgrammatically = true
                                        selectedStaffTab = 1  // 1 = Unlocks tab
                                        currentScreen = Screen.STAFF
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(targetIndex)
                                            isNavigatingProgrammatically = false
                                        }
                                    }
                                },
                                onSave = { viewModel.onEvent(GameEvent.SaveGame) },
                                onReset = { viewModel.onEvent(GameEvent.ResetGame) },
                                freshAutoOrderEnabled = viewModel.currentState().freshAutoOrderConfig.enabled,
                                freshMinStockThreshold = viewModel.currentState().freshAutoOrderConfig.minStockThreshold,
                                freshCasePacksPerItem = viewModel.currentState().freshAutoOrderConfig.casePacksPerItem,
                                onFreshAutoOrderConfigChanged = { enabled, threshold, packs ->
                                     viewModel.onEvent(GameEvent.UpdateFreshAutoOrderConfig(enabled, threshold, packs))
                                },
                                truckConfig = viewModel.currentState().truckConfig,
                                onTruckConfigChanged = { days, regularCap, freshCap ->
                                    viewModel.onEvent(GameEvent.UpdateTruckConfig(days, regularCap, freshCap))
                                },
                            )

                            Screen.INVENTORY -> InventoryAndFreshScreen(
                                state = state.inventory,
                                money = state.app.money,
                                itemMetadataCache = viewModel.itemMetadataCache,
                                currentTier = state.progression.currentTier,
                                currentDay = state.time?.currentTime?.dayNumber ?: 0,
                                metricsData = state.metrics.completedDays,
                                resetTrigger = inventoryResetTrigger,
                                incompleteFreshOrdersCount = viewModel.currentState().incompleteFreshOrders.size,
                                deliveries = state.delivery,
                                onBuyItem = { itemId -> viewModel.onEvent(GameEvent.BuyItem(itemId)) },
                                onSelectCategory = { category -> viewModel.onEvent(GameEvent.SelectItemCategory(category)) },
                                onBulkOrder = { maxQty, casePacks, category ->
                                    viewModel.onEvent(GameEvent.BulkOrder(maxQty, casePacks, category))
                                },
                                onFreshBulkOrder = { showFreshBulkOrderDialog = true },
                                onViewIncompleteOrders = { showIncompleteOrdersDialog = true },
                                onCancelOrderLine = { itemId, truckId ->
                                    viewModel.onEvent(GameEvent.CancelPendingOrderLine(itemId, truckId))
                                },
                                onDecrementOrderLine = { itemId, truckId ->
                                    viewModel.onEvent(GameEvent.DecrementOrderLine(itemId, truckId))
                                },
                                onRequestEarlyTruck = {
                                    viewModel.onEvent(GameEvent.RequestEarlyTruck)
                                },
                                onItemClick = { itemId -> selectedInventoryItemId = itemId },
                                modifier = Modifier.padding(paddingValues)
                            )

                            Screen.STAFF -> StaffAndUnlocksScreen(
                                staffState = state.staff,
                                progression = state.progression,
                                money = state.app.money,
                                initialTab = selectedStaffTab,
                                onTabChanged = { tab -> selectedStaffTab = tab },
                                onSelectStaffType = { type ->
                                    viewModel.onEvent(GameEvent.SelectStaffType(type))
                                    currentScreen = Screen.STAFF_ENTITY_LIST
                                },
                                onUnlockNextTier = { viewModel.onEvent(GameEvent.UnlockNextTier) },
                                modifier = Modifier.padding(paddingValues)
                            )
                                
                            Screen.HISTORY -> SalesHistoryScreen(
                                state = state.history,
                                itemDao = itemDao,
                                modifier = Modifier.padding(paddingValues)
                            )

                            Screen.METRICS -> MetricsScreen(
                                state = state.metrics,
                                modifier = Modifier.padding(paddingValues),
                                onFocusInventoryItem = { itemId ->
                                    viewModel.onEvent(GameEvent.FocusInventoryItem(itemId))
                                    val targetIndex = mainScreens.indexOf(Screen.INVENTORY)
                                    if (targetIndex != -1) {
                                        isNavigatingProgrammatically = true
                                        currentScreen = Screen.INVENTORY
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(targetIndex)
                                            isNavigatingProgrammatically = false
                                        }
                                    }
                                }
                            )
                            
                            else -> {} // Should not reach here for main screens
                        }
                    }
                }
                
                    // Staff Entity Detail Screen as overlay (not part of pager)
                    if (currentScreen == Screen.STAFF_ENTITY_LIST) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    enabled = true,
                                    onClick = { /* Consume all clicks to prevent interaction with underlying content */ },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                        ) {
                            EntityTypeDetailScreen(
                                state = state.staff,
                                money = state.app.money,
                                type = state.staff.selectedType,
                                currentTier = state.progression.currentTier,
                                onHire = { def ->
                                    viewModel.onEvent(GameEvent.HireStaff(def, state.staff.selectedType )) },
                                onFire = { id -> viewModel.onEvent(GameEvent.FireStaff(id)) },
                                onUpgrade = { id -> viewModel.onEvent(GameEvent.UpgradeStaff(id)) },
                                onBack = {
                                    viewModel.onEvent(GameEvent.SelectStaffType(EntityType.NONE))
                                    currentScreen = Screen.STAFF
                                }
                            )
                        }
                    }
                    
                    // Inventory Item Detail Screen as overlay (not part of pager)
                    selectedInventoryItemId?.let { selectedId ->
                        val selectedItem = state.inventory.items.find { it.id == selectedId }
                        if (selectedItem != null) {
                            // Get batch data through ViewModel read-only helper
                            val inventoryState = viewModel.getInventoryStateForItem(selectedId)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        enabled = true,
                                        onClick = { /* Consume all clicks to prevent interaction with underlying content */ },
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    )
                            ) {
                                InventoryItemDetailScreen(
                                    item = selectedItem,
                                    money = state.app.money,
                                    itemMetadataCache = viewModel.itemMetadataCache,
                                    currentTier = state.progression.currentTier,
                                    currentDay = state.time?.currentTime?.dayNumber ?: 0,
                                    metricsData = state.metrics.completedDays,
                                    shelfBatches = inventoryState?.shelfBatches ?: emptyList(),
                                    backroomBatches = inventoryState?.backroomBatches ?: emptyList(),
                                    pendingDeliveries = (state.delivery.regularTrucks + listOfNotNull(state.delivery.freshTruck))
                                        .flatMap { truck -> truck.orderLines }
                                        .filter { line -> line.itemId == selectedId },
                                    onCancelOrderLine = { itemId, truckId ->
                                        viewModel.onEvent(GameEvent.CancelPendingOrderLine(itemId, truckId))
                                    },
                                    onDecrementOrderLine = { itemId, truckId ->
                                        viewModel.onEvent(GameEvent.DecrementOrderLine(itemId, truckId))
                                    },
                                    onBuyItem = { itemId -> viewModel.onEvent(GameEvent.BuyItem(itemId)) },
                                    onBack = { selectedInventoryItemId = null }
                                )
                            }
                        }
                    }

                    // Fresh Bulk Order Dialog
                    if (showFreshBulkOrderDialog) {
                        FreshBulkOrderDialog(
                            allItems = state.inventory.items,
                            money = state.app.money,
                            onConfirm = { maxQty, packsPer ->
                                viewModel.onEvent(GameEvent.FreshBulkOrder(maxQty, packsPer))
                            },
                            onDismiss = { showFreshBulkOrderDialog = false }
                        )
                    }

                    // Incomplete Orders Dialog
                    if (showIncompleteOrdersDialog) {
                        val gameState = viewModel.currentState()
                        // Build item names and costs maps
                        val itemNames = gameState.incompleteFreshOrders.associate { order ->
                            val item = viewModel.itemMetadataCache.getItem(order.itemId)
                            order.itemId to (item?.name ?: "Item ${order.itemId}")
                        }
                        val itemCosts = gameState.incompleteFreshOrders.associate { order ->
                            val item = viewModel.itemMetadataCache.getItem(order.itemId)
                            order.itemId to (item?.getCasePackCostAsMoney() ?: Money.ZERO)
                        }
                        
                        IncompleteOrdersDialog(
                            incompleteOrders = gameState.incompleteFreshOrders,
                            itemNames = itemNames,
                            itemCosts = itemCosts,
                            money = state.app.money,
                            onOrderItem = { itemId: Int, packs: Int ->
                                viewModel.onEvent(GameEvent.OrderIncompleteItem(itemId, packs))
                            },
                            onOrderAll = {
                                // Order all affordable items
                                val currentState = viewModel.currentState()
                                currentState.incompleteFreshOrders.forEach { order ->
                                    val item = viewModel.itemMetadataCache.getItem(order.itemId)
                                    val totalCost = (item?.getCasePackCostAsMoney() ?: Money.ZERO) * order.casePacksRequested
                                    if (currentState.money >= totalCost) {
                                        viewModel.onEvent(GameEvent.OrderIncompleteItem(order.itemId, order.casePacksRequested))
                                    }
                                }
                            },
                            onDismiss = { showIncompleteOrdersDialog = false }
                        )
                    }

                    // End-of-day report dialog — shown automatically at midnight
                    if (state.metrics.showEndOfDayReport && state.metrics.lastReport != null) {
                        EndOfDayReportDialog(
                            report = state.metrics.lastReport,
                            onDismiss = { viewModel.onEvent(GameEvent.DismissEndOfDayReport) }
                        )
                    }
                }
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Save game when app goes to background
        gameViewModel?.saveGameState()
    }
    
    override fun onStop() {
        super.onStop()
        // Additional save on stop as a safety measure
        gameViewModel?.saveGameState()
    }
}

// Re-add BottomNavBar composable used above
@Composable
fun BottomNavBar(
    current: Screen,
    onSelect: (Screen) -> Unit,
    pendingRefundsCount: Int = 0
) {
    NavigationBar(containerColor = NavBarBackground) {
        NavigationBarItem(
            selected = current == Screen.GAME,
            onClick = { onSelect(Screen.GAME) },
            icon = {
                // Icon with optional small badge showing pending refunds count
                Box {
                    Icon(
                        Icons.Default.AddShoppingCart,
                        contentDescription = "Transactions",
                        tint = Color.Unspecified
                    )
                    if (pendingRefundsCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-6).dp)
                                .background(color = Color(0xFFEF4444), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pendingRefundsCount.coerceAtMost(99).toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(1.dp)
                            )
                        }
                    }
                }
            },
            label = { Text("Store") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NavBarSelectedIcon,
                selectedTextColor = NavBarSelectedText,
                indicatorColor = NavBarIndicator,
                unselectedIconColor = NavBarUnselectedIcon,
                unselectedTextColor = NavBarUnselectedText
            )
        )

        NavigationBarItem(
            selected = current == Screen.INVENTORY,
            onClick = { onSelect(Screen.INVENTORY) },
            icon = { Icon(Icons.Default.Inbox, contentDescription = "Inventory") },
            label = { Text("Inventory") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NavBarSelectedIcon,
                selectedTextColor = NavBarSelectedText,
                indicatorColor = NavBarIndicator,
                unselectedIconColor = NavBarUnselectedIcon,
                unselectedTextColor = NavBarUnselectedText
            )
        )

        NavigationBarItem(
            selected = current == Screen.STAFF,
            onClick = { onSelect(Screen.STAFF) },
            icon = { Icon(Icons.Default.People, contentDescription = "Manage") },
            label = { Text("Manage") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NavBarSelectedIcon,
                selectedTextColor = NavBarSelectedText,
                indicatorColor = NavBarIndicator,
                unselectedIconColor = NavBarUnselectedIcon,
                unselectedTextColor = NavBarUnselectedText
            )
        )

        NavigationBarItem(
            selected = current == Screen.HISTORY,
            onClick = { onSelect(Screen.HISTORY) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("History") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NavBarSelectedIcon,
                selectedTextColor = NavBarSelectedText,
                indicatorColor = NavBarIndicator,
                unselectedIconColor = NavBarUnselectedIcon,
                unselectedTextColor = NavBarUnselectedText
            )
        )

        NavigationBarItem(
            selected = current == Screen.METRICS,
            onClick = { onSelect(Screen.METRICS) },
            icon = { Icon(Icons.Default.BarChart, contentDescription = "Metrics") },
            label = { Text("Metrics") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NavBarSelectedIcon,
                selectedTextColor = NavBarSelectedText,
                indicatorColor = NavBarIndicator,
                unselectedIconColor = NavBarUnselectedIcon,
                unselectedTextColor = NavBarUnselectedText
            )
        )
    }
}
