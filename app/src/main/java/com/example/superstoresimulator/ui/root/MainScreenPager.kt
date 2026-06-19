package com.example.superstoresimulator.ui.root

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.superstoresimulator.domain.Screen
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.navigation.GameNavigationState
import com.example.superstoresimulator.ui.screens.home.StoreHomeScreen
import com.example.superstoresimulator.ui.screens.inventory.InventoryAndFreshScreen
import com.example.superstoresimulator.ui.screens.metrics.MetricsScreen
import com.example.superstoresimulator.ui.screens.sales.SalesHistoryScreen
import com.example.superstoresimulator.ui.screens.staff.StaffAndUnlocksScreen
import com.example.superstoresimulator.ui.state.GameUiState

/**
 * The horizontally-swipeable pager hosting the five main screens.
 * Swiping is disabled while a full-screen overlay is open.
 */
@Composable
fun MainScreenPager(
    state: GameUiState,
    navState: GameNavigationState,
    itemDao: ItemDao,
    paddingValues: PaddingValues,
    onEvent: (GameEvent) -> Unit,
    onFreshBulkOrder: () -> Unit,
    onViewIncompleteOrders: () -> Unit,
    onViewIncompleteNormalOrders: () -> Unit,
) {
    HorizontalPager(
        state = navState.pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !navState.isOverlayOpen
    ) { page ->
        when (navState.mainScreens[page]) {
            Screen.GAME -> StoreHomeScreen(
                state = state,
                onStoreNameChange = { onEvent(GameEvent.ChangeStoreName(it)) },
                onProcessRefundLine = { refundId: Int, itemId: Int, qty: Int -> onEvent(GameEvent.ProcessRefundLine(refundId, itemId, qty)) },
                onViewItem = { itemId: Int -> onEvent(GameEvent.FocusInventoryItem(itemId)) },
                itemDao = itemDao,
                onNavigateToInventory = { navState.navigateTo(Screen.INVENTORY) },
                modifier = Modifier.padding(paddingValues),
                contentPadding = paddingValues,
                onSpeedChanged = { multiplier: Float -> onEvent(GameEvent.SetGameSpeed(multiplier)) },
                onOpenStore = { onEvent(GameEvent.ToggleStore) },
                onSetPlayerRole = { role: PlayerRole -> onEvent(GameEvent.SetPlayerRole(role)) },
                onSkipDay = { onEvent(GameEvent.SkipDay) },
                onSkipWeek = { onEvent(GameEvent.SkipWeek) },
                onUpgradeStore = { onEvent(GameEvent.UpgradeStoreSize) },
                onPurchaseBuilding = { onEvent(GameEvent.PurchaseBuilding) },
                onSave = { onEvent(GameEvent.SaveGame) },
                onReset = { onEvent(GameEvent.ResetGame) },
                onPurchaseRegister = {
                    onEvent(GameEvent.PurchaseRegister)
                },
                onAssignPlayerToRegister = { registerId ->
                    onEvent(GameEvent.AssignPlayerToRegister(registerId))
                },
                onAssignCashierToRegister = { cashierId, registerId ->
                    onEvent(GameEvent.AssignCashierToRegister(cashierId, registerId))
                },
                onSetDefaultMarkup = { percent ->
                    onEvent(GameEvent.SetDefaultMarkup(percent))
                },
                onSetCategoryMarkup = { category, percent ->
                    onEvent(GameEvent.SetCategoryMarkup(category, percent))
                },
            )

            Screen.INVENTORY -> InventoryAndFreshScreen(
                state = state.inventory,
                money = state.app.money,
                researchedUpgrades = state.research.researchedUpgrades,
                currentDay = state.time?.currentTime?.dayNumber ?: 0,
                metricsData = state.metrics.completedDays,
                resetTrigger = navState.inventoryResetTrigger,
                incompleteFreshOrdersCount = state.inventory.incompleteFreshOrders.size,
                incompleteNormalOrdersCount = state.inventory.incompleteNormalOrders.size,
                hasFastStocker = state.inventory.hasFastStocker,
                hasStockingManager = state.inventory.hasStockingManager,
                hasFreshHandler = state.inventory.hasFreshHandler,
                freshAutoOrderConfig = state.inventory.freshAutoOrderConfig,
                normalAutoOrderConfig = state.inventory.normalAutoOrderConfig,
                deliveries = state.delivery,
                onBuyItem = { itemId -> onEvent(GameEvent.BuyItem(itemId)) },
                onSelectCategory = { category -> onEvent(GameEvent.SelectItemCategory(category)) },
                onBulkOrder = { maxQty, casePacks, category ->
                    onEvent(GameEvent.BulkOrder(maxQty, casePacks, category))
                },
                onFreshBulkOrder = onFreshBulkOrder,
                onViewIncompleteOrders = onViewIncompleteOrders,
                onViewIncompleteNormalOrders = onViewIncompleteNormalOrders,
                onUpdateFreshAutoOrderConfig = { enabled, threshold, packs ->
                    onEvent(GameEvent.UpdateFreshAutoOrderConfig(enabled, threshold, packs))
                },
                onUpdateNormalAutoOrderConfig = { enabled, threshold, packs ->
                    onEvent(GameEvent.UpdateNormalAutoOrderConfig(enabled, threshold, packs))
                },
                onCancelOrderLine = { itemId, truckId ->
                    onEvent(GameEvent.CancelPendingOrderLine(itemId, truckId))
                },
                onDecrementOrderLine = { itemId, truckId ->
                    onEvent(GameEvent.DecrementOrderLine(itemId, truckId))
                },
                onRequestEarlyTruck = {
                    onEvent(GameEvent.RequestEarlyTruck)
                },
                onTruckConfigChanged = { days, regularCap, freshCap ->
                    onEvent(GameEvent.UpdateTruckConfig(days, regularCap, freshCap))
                },
                onPurchaseExtraTruckSlot = {
                    onEvent(GameEvent.PurchaseExtraTruckSlot)
                },
                onPurchaseTruckUpgrade = {
                    onEvent(GameEvent.PurchaseTruckUpgrade)
                },
                onItemClick = { itemId -> navState.selectedInventoryItemId = itemId },
                modifier = Modifier.padding(paddingValues)
            )

            Screen.STAFF -> StaffAndUnlocksScreen(
                staffState = state.staff,
                research = state.research,
                vendorState = state.vendors,
                money = state.app.money,
                initialTab = navState.selectedStaffTab,
                onTabChanged = { tab -> navState.selectedStaffTab = tab },
                currentStoreSize = state.time?.currentStoreSize ?: StoreSize.MOM_AND_POP,
                onHireStaff = { def -> onEvent(GameEvent.HireStaff(def)) },
                onFireStaff = { id -> onEvent(GameEvent.FireStaff(id)) },
                onPromoteStaff = { id -> onEvent(GameEvent.PromoteStaff(id)) },
                onUnlockNextVendorTier = { onEvent(GameEvent.UnlockNextVendorTier) },
                onInvestInVendor = { vendorId -> onEvent(GameEvent.InvestInVendor(vendorId)) },
                onUpdateShift = { entityId, newStartHour, newDuration, workDays ->
                    onEvent(GameEvent.UpdateShift(entityId, newStartHour, newDuration, workDays))
                },
                onUpdateStoreManagerConfig = { config ->
                    onEvent(GameEvent.UpdateStoreManagerConfig(config))
                },
                onAssignAnalyst = { entityId, assignment ->
                    onEvent(GameEvent.AssignAnalyst(entityId, assignment))
                },
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
                    onEvent(GameEvent.FocusInventoryItem(itemId))
                    navState.navigateTo(Screen.INVENTORY)
                },
                onLoadArchivedDay = { dayNumber ->
                    onEvent(GameEvent.LoadArchivedDayDetail(dayNumber))
                },
                onClearLoadedArchivedDay = {
                    onEvent(GameEvent.ClearLoadedArchivedDay)
                },
            )
        }
    }
}
