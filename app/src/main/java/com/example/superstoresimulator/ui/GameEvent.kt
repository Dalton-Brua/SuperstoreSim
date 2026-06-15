package com.example.superstoresimulator.ui

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.StoreManagerConfig
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.research.AnalystAssignment

sealed interface GameEvent {
    data object Tick : GameEvent
    data object RingUp : GameEvent
    data class RingUpItem(val itemId: Int) : GameEvent
    data object StartTransaction : GameEvent
    data class StockItem(val itemId: Int) : GameEvent
    data class BuyItem(val itemId: Int) : GameEvent
    data class SelectItemCategory(val category: ItemCategory?) : GameEvent
    data class FocusInventoryItem(val itemId: Int?) : GameEvent
    data class HireStaff(val entityDef: EntityDef) : GameEvent
    data class PromoteStaff(val entityId: Int) : GameEvent
    data class FireStaff(val entityId: Int) : GameEvent
    data class ChangeStoreName(val name: String) : GameEvent
    data class ProcessRefund(val refundId: Int) : GameEvent
    data class ProcessRefundLine(val refundId: Int, val itemId: Int, val quantity: Int) : GameEvent

    data class SetGameSpeed(val multiplier: Float) : GameEvent
    data object ToggleStore : GameEvent

    // Phase 1: Store sizing events
    data object UpgradeStoreSize : GameEvent

    // Phase 2: Player role events
    data class SetPlayerRole(val role: PlayerRole) : GameEvent

    // Phase 3: Metrics events
    data object DismissEndOfDayReport : GameEvent

    // Research system
    data class AssignAnalyst(val entityId: Int, val assignment: AnalystAssignment?) : GameEvent

    // Tutorial system
    data object SkipTutorial : GameEvent

    // Skip Day: simulate the rest of the current day and show the end-of-day report
    data object SkipDay : GameEvent

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

    // Normal Auto-Order Config: update the auto-ordering settings for stocking manager
    data class UpdateNormalAutoOrderConfig(
        val enabled: Boolean,
        val minStockThreshold: Int,
        val casePacksPerItem: Int,
    ) : GameEvent

    // Fresh Auto-Order: manually order an incomplete fresh item from the dialog
    data class OrderIncompleteItem(
        val itemId: Int,
        val casePacksRequested: Int,
    ) : GameEvent

    // Normal Auto-Order: manually order an incomplete normal item from the dialog
    data class OrderIncompleteNormalItem(
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

    /**
     * Purchase one extra weekly delivery-day slot beyond the store-size-based free limit.
     * Costs $100 (one-time). Does not assign to a specific day — the player configures that
     * separately in the Delivery Schedule settings.
     */
    data object PurchaseExtraTruckSlot : GameEvent

    // Staff Scheduling (Phase 1)
    /**
     * Update the shift start hour for a specific employee.
     * [newStartHour] is clamped to 6..13 in the UI before dispatch; the domain
     * layer also validates the range and silently ignores invalid values.
     */
    data class UpdateShift(val entityId: Int, val newStartHour: Int, val newDuration: Int = 8) : GameEvent

    // Register System (Phase 3)
    /** Purchase one additional register (subject to store-size cap and affordability). */
    data object PurchaseRegister : GameEvent

    /**
     * Assign (or unassign) a hired cashier to/from a register.
     * Pass [cashierId] = null to clear the assignment.
     */
    data class AssignCashierToRegister(val cashierId: Int?, val registerId: Int) : GameEvent

    /**
     * Assign (or unassign) the player as cashier on a specific register.
     * Pass [registerId] = null to unassign.
     * Guard: cannot assign to a register that already has a hired cashier.
     */
    data class AssignPlayerToRegister(val registerId: Int?) : GameEvent

    // Auto-Hire Budget (Phase 5B)
    data class SetAutoHireBudget(val budget: Money) : GameEvent

    // Pricing System
    data class SetCategoryMarkup(val category: ItemCategory, val percent: Int) : GameEvent
    data class SetDefaultMarkup(val percent: Int) : GameEvent
    data class SetItemPriceOverride(val itemId: Int, val percent: Int) : GameEvent
    data class ClearItemMarkdown(val itemId: Int) : GameEvent

    // Store Manager Config
    data class UpdateStoreManagerConfig(val config: StoreManagerConfig) : GameEvent

    // Vendor System
    data object UnlockNextVendorTier : GameEvent
    data class InvestInVendor(val vendorId: String) : GameEvent
}