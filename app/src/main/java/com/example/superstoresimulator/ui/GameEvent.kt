package com.example.superstoresimulator.ui

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.player.PlayerRole

sealed interface GameEvent {
    data object Tick : GameEvent
    data object RingUp : GameEvent
    data class RingUpItem(val itemId: Int) : GameEvent
    data object StartTransaction : GameEvent
    data class StockItem(val itemId: Int) : GameEvent
    data class BuyItem(val itemId: Int) : GameEvent
    data class SelectItemCategory(val category: ItemCategory?) : GameEvent
    data class FocusInventoryItem(val itemId: Int?) : GameEvent
    data class HireStaff(val entityDef: EntityDef, val entityType: EntityType) : GameEvent
    data class UpgradeStaff(val entityId: Int) : GameEvent
    data class FireStaff(val entityId: Int) : GameEvent
    data class ChangeStoreName(val name: String) : GameEvent
    data class ProcessRefund(val refundId: Int) : GameEvent
    data class ProcessRefundLine(val refundId: Int, val itemId: Int, val quantity: Int) : GameEvent
    data class SelectStaffType(val staffType: EntityType) : GameEvent

    data class SetGameSpeed(val multiplier: Float) : GameEvent
    data object ToggleStore : GameEvent

    // Phase 1: Store sizing events
    data object UpgradeStoreSize : GameEvent

    // Phase 2: Player role events
    data class SetPlayerRole(val role: PlayerRole) : GameEvent

    // Phase 3: Metrics events
    data object DismissEndOfDayReport : GameEvent

    // Progression: tier unlock notification dismiss
    data object DismissTierUnlock : GameEvent

    // Skip Day: simulate the rest of the current day and show the end-of-day report
    data object SkipDay : GameEvent

    // Progression: player purchases the next store tier (costs money, requires revenue gate)
    data object UnlockNextTier : GameEvent

    // Bulk Order: purchase case packs for all items meeting a criteria, with volume discounts
    data class BulkOrder(
        val maxTotalQuantity: Int,      // include items with shelfStock + backroomStock <= this
        val casePacksPerItem: Int,      // number of case packs to order per matching item
        val categoryFilter: ItemCategory? = null,  // null = all accessible categories
    ) : GameEvent

    // Fresh Bulk Order: purchase case packs for fresh items only with lower discounts
    data class FreshBulkOrder(
        val maxTotalQuantity: Int,
        val casePacksPerItem: Int,
    ) : GameEvent

    // Fresh Auto-Order Config: update the auto-ordering settings
    data class UpdateFreshAutoOrderConfig(
        val enabled: Boolean,
        val minStockThreshold: Int,
        val casePacksPerItem: Int,
    ) : GameEvent

    // Fresh Auto-Order: manually order an incomplete fresh item from the dialog
    data class OrderIncompleteItem(
        val itemId: Int,
        val casePacksRequested: Int,
    ) : GameEvent

    // Save System: manually save the game state
    data object SaveGame : GameEvent

    // Reset System: reset the game to initial state
    data object ResetGame : GameEvent

    // Truck Delivery System
    data class UpdateTruckConfig(
        val deliveryDays: Set<Int>,
        val regularCapacityCasePacks: Int,
        val freshCapacityCasePacks: Int,
    ) : GameEvent

    data class CancelPendingOrderLine(
        val itemId: Int,
        val truckId: Int,
    ) : GameEvent

    data class DecrementOrderLine(
        val itemId: Int,
        val truckId: Int,
    ) : GameEvent

    data object RequestEarlyTruck : GameEvent
}