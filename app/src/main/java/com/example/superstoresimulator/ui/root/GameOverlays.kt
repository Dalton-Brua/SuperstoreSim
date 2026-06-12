package com.example.superstoresimulator.ui.root

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.superstoresimulator.domain.Screen
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.navigation.GameNavigationState
import com.example.superstoresimulator.ui.screens.inventory.InventoryItemDetailScreen
import com.example.superstoresimulator.ui.screens.staff.EntityTypeDetailScreen
import com.example.superstoresimulator.ui.state.GameUiState

/**
 * Full-screen overlay container that consumes all clicks so the underlying
 * pager content can't be interacted with while a detail screen is open.
 */
@Composable
private fun OverlayScrim(content: @Composable () -> Unit) {
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
        content()
    }
}

/** Staff Entity Detail Screen as overlay (not part of pager). */
@Composable
fun StaffEntityOverlay(
    state: GameUiState,
    navState: GameNavigationState,
    onEvent: (GameEvent) -> Unit,
) {
    if (navState.currentScreen != Screen.STAFF_ENTITY_LIST) return

    OverlayScrim {
        EntityTypeDetailScreen(
            state = state.staff,
            money = state.app.money,
            def = state.staff.selectedDef,
            currentTier = state.progression.currentTier,
            currentStoreSize = state.time?.currentStoreSize ?: StoreSize.MOM_AND_POP,
            onHire = { def -> onEvent(GameEvent.HireStaff(def)) },
            onFire = { id -> onEvent(GameEvent.FireStaff(id)) },
            onUpgrade = { id -> onEvent(GameEvent.PromoteStaff(id)) },
            onBack = {
                onEvent(GameEvent.SelectStaffDef(null))
                navState.currentScreen = Screen.STAFF
            }
        )
    }
}

/** Inventory Item Detail Screen as overlay (not part of pager). */
@Composable
fun InventoryItemDetailOverlay(
    state: GameUiState,
    navState: GameNavigationState,
    onEvent: (GameEvent) -> Unit,
    getInventoryStateForItem: (Int) -> InventoryState?,
) {
    val selectedId = navState.selectedInventoryItemId ?: return
    val selectedItem = state.inventory.items.find { it.id == selectedId } ?: return

    // Get batch data through ViewModel read-only helper
    val inventoryState = getInventoryStateForItem(selectedId)
    OverlayScrim {
        InventoryItemDetailScreen(
            item = selectedItem,
            money = state.app.money,
            currentTier = state.progression.currentTier,
            currentDay = state.time?.currentTime?.dayNumber ?: 0,
            metricsData = state.metrics.completedDays,
            shelfBatches = inventoryState?.shelfBatches ?: emptyList(),
            backroomBatches = inventoryState?.backroomBatches ?: emptyList(),
            pendingDeliveries = (state.delivery.regularTrucks + listOfNotNull(state.delivery.freshTruck))
                .flatMap { truck -> truck.orderLines }
                .filter { line -> line.itemId == selectedId },
            onCancelOrderLine = { itemId, truckId ->
                onEvent(GameEvent.CancelPendingOrderLine(itemId, truckId))
            },
            onDecrementOrderLine = { itemId, truckId ->
                onEvent(GameEvent.DecrementOrderLine(itemId, truckId))
            },
            onBuyItem = { itemId -> onEvent(GameEvent.BuyItem(itemId)) },
            onSetItemOverride = { itemId, percent ->
                onEvent(GameEvent.SetItemPriceOverride(itemId, percent))
            },
            onClearMarkdown = { itemId ->
                onEvent(GameEvent.ClearItemMarkdown(itemId))
            },
            itemOverridePercent = state.pricing.pricingState.itemOverrides[selectedId] ?: 0,
            onBack = { navState.selectedInventoryItemId = null }
        )
    }
}
