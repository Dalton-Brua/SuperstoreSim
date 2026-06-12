package com.example.superstoresimulator.ui.root

import androidx.compose.runtime.Composable
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.ui.dialogs.EndOfDayReportDialog
import com.example.superstoresimulator.ui.dialogs.FreshBulkOrderDialog
import com.example.superstoresimulator.ui.dialogs.IncompleteOrdersDialog
import com.example.superstoresimulator.ui.state.GameUiState

/**
 * Hosts the top-level dialogs: fresh bulk order, incomplete fresh/normal orders,
 * and the automatic end-of-day report.
 */
@Composable
fun GameDialogs(
    state: GameUiState,
    showFreshBulkOrderDialog: Boolean,
    onDismissFreshBulkOrder: () -> Unit,
    showIncompleteOrdersDialog: Boolean,
    onDismissIncompleteOrders: () -> Unit,
    showIncompleteNormalOrdersDialog: Boolean,
    onDismissIncompleteNormalOrders: () -> Unit,
    onEvent: (GameEvent) -> Unit,
) {
    // Fresh Bulk Order Dialog
    if (showFreshBulkOrderDialog) {
        FreshBulkOrderDialog(
            allItems = state.inventory.items,
            money = state.app.money,
            onConfirm = { maxQty, packsPer ->
                onEvent(GameEvent.FreshBulkOrder(maxQty, packsPer))
            },
            onDismiss = onDismissFreshBulkOrder
        )
    }

    // Incomplete Orders Dialog
    if (showIncompleteOrdersDialog) {
        IncompleteOrdersDialog(
            incompleteOrders = state.inventory.incompleteFreshOrders,
            itemNames = state.inventory.incompleteFreshItemNames,
            itemCosts = state.inventory.incompleteFreshItemCosts,
            money = state.app.money,
            onOrderItem = { itemId: Int, packs: Int ->
                onEvent(GameEvent.OrderIncompleteItem(itemId, packs))
            },
            onOrderAll = {
                state.inventory.incompleteFreshOrders.forEach { order ->
                    val totalCost = (state.inventory.incompleteFreshItemCosts[order.itemId]
                        ?: Money.ZERO) * order.casePacksRequested
                    if (state.app.money >= totalCost) {
                        onEvent(GameEvent.OrderIncompleteItem(order.itemId, order.casePacksRequested))
                    }
                }
            },
            onDismiss = onDismissIncompleteOrders
        )
    }

    // Incomplete Normal Orders Dialog
    if (showIncompleteNormalOrdersDialog) {
        IncompleteOrdersDialog(
            incompleteOrders = state.inventory.incompleteNormalOrders,
            itemNames = state.inventory.incompleteNormalItemNames,
            itemCosts = state.inventory.incompleteNormalItemCosts,
            money = state.app.money,
            onOrderItem = { itemId: Int, packs: Int ->
                onEvent(GameEvent.OrderIncompleteNormalItem(itemId, packs))
            },
            onOrderAll = {
                state.inventory.incompleteNormalOrders.forEach { order ->
                    val totalCost = (state.inventory.incompleteNormalItemCosts[order.itemId]
                        ?: Money.ZERO) * order.casePacksRequested
                    if (state.app.money >= totalCost) {
                        onEvent(GameEvent.OrderIncompleteNormalItem(order.itemId, order.casePacksRequested))
                    }
                }
            },
            onDismiss = onDismissIncompleteNormalOrders
        )
    }

    // End-of-day report dialog — shown automatically at midnight
    if (state.metrics.showEndOfDayReport && state.metrics.lastReport != null) {
        EndOfDayReportDialog(
            report = state.metrics.lastReport,
            onDismiss = { onEvent(GameEvent.DismissEndOfDayReport) }
        )
    }
}
