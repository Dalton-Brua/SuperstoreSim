package com.example.superstoresimulator.domain.inventory

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache

/**
 * Sub-system responsible for all inventory read/write operations.
 *
 * This is a pure sub-system: every method receives a [GameState] and returns a new
 * [GameState]. No reference to the engine's mutable state or change-emission stream
 * is held here — [GameEngine] retains those responsibilities.
 *
 * Change emissions ([GameStateChange.InventoryUpdated], [GameStateChange.MoneyChanged])
 * are the caller's responsibility: compare [GameState.inventory] or [GameState.money]
 * before and after each call and emit accordingly.
 *
 * Responsibilities:
 *  - Move individual items or full case-packs from backroom to shelf
 *  - Pick the lowest-stocked item and restock it (used by stockers and player stocker role)
 *  - Buy single case-packs or multi-pack orders into the backroom, respecting the cap
 *  - Execute a bulk order across an entire category with tiered volume discounts
 */
class InventoryManager(private val cache: ItemMetadataCache) {

    // ── Stocking operations ───────────────────────────────────────────────────

    /**
     * Move one unit of [itemId] from backroom to shelf.
     *
     * Returns the state unchanged when the item is not in inventory or the
     * backroom is already empty.
     */
    fun stockItemFromBackroom(state: GameState, itemId: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        if (dyn.backroomStock <= 0) return state

        val updated = dyn.copy(
            shelfStock = dyn.shelfStock + 1,
            backroomStock = dyn.backroomStock - 1,
        )
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsStocked = state.currentDayMetrics.itemsStocked + 1,
            ),
        )
    }

    /**
     * Find the item with the lowest shelf stock that still has backroom inventory,
     * then stock one full case-pack of it onto the shelf.
     *
     * Returns the state unchanged when every item's backroom is empty.
     */
    fun stockRandomItemFromBackroom(state: GameState): GameState {
        val candidates = state.inventory.filter { (_, dyn) -> dyn.backroomStock > 0 }
        if (candidates.isEmpty()) return state

        val lowestShelf = candidates.minOf { it.value.shelfStock }
        val lowestGroup = candidates.filter { it.value.shelfStock == lowestShelf }
        val targetId = lowestGroup.keys.random()

        return stockCasePackFromBackroom(state, targetId)
    }

    /**
     * Move up to one full case-pack of [itemId] from backroom to shelf.
     * If fewer items than the case-pack size remain in the backroom, all remaining
     * units are stocked.
     */
    private fun stockCasePackFromBackroom(state: GameState, itemId: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        val dbItem = cache.getItem(itemId) ?: return state
        if (dyn.backroomStock <= 0) return state

        val itemsToStock = minOf(dbItem.casePack, dyn.backroomStock)
        val updated = dyn.copy(
            shelfStock = dyn.shelfStock + itemsToStock,
            backroomStock = dyn.backroomStock - itemsToStock,
        )
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsStocked = state.currentDayMetrics.itemsStocked + itemsToStock,
            ),
        )
    }

    // ── Buying operations ─────────────────────────────────────────────────────

    /**
     * Order exactly one case-pack of [itemId] into the backroom.
     *
     * Guards (all return state unchanged on failure):
     *  - Item not in inventory or not in cache
     *  - Adding a full case-pack would exceed [StoreConfig.backroomCapPerItem]
     *  - Player cannot afford the case-pack cost
     */
    fun buyItemToBackroom(state: GameState, itemId: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        val dbItem = cache.getItem(itemId) ?: return state

        val cap = state.storeConfig.backroomCapPerItem
        if (dyn.backroomStock + dbItem.casePack > cap) return state

        val casePackCost = dbItem.getCasePackCostAsMoney()
        if (state.money < casePackCost) return state

        val updated = dyn.copy(backroomStock = dyn.backroomStock + dbItem.casePack)
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            money = state.money - casePackCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + dbItem.casePack,
            ),
        )
    }

    /**
     * Order [numCasePacks] case-packs of [itemId] into the backroom.
     *
     * Delivery is clamped to however many full case-packs fit within the remaining
     * backroom space. The player is charged only for case-packs actually delivered.
     *
     * Guards (all return state unchanged on failure):
     *  - Item not in inventory or cache, or [numCasePacks] ≤ 0
     *  - No room for even one full case-pack
     *  - Player cannot afford the clamped delivery cost
     */
    fun buyItemCasePacks(state: GameState, itemId: Int, numCasePacks: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        val dbItem = cache.getItem(itemId) ?: return state
        if (numCasePacks <= 0) return state

        val cap = state.storeConfig.backroomCapPerItem
        val availableSpace = cap - dyn.backroomStock
        val actualCasePacks = minOf(numCasePacks, availableSpace / dbItem.casePack)
        if (actualCasePacks <= 0) return state

        val totalCost = dbItem.getCasePackCostAsMoney() * actualCasePacks
        if (state.money < totalCost) return state

        val totalItemsAdded = dbItem.casePack * actualCasePacks
        val updated = dyn.copy(backroomStock = dyn.backroomStock + totalItemsAdded)
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            money = state.money - totalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItemsAdded,
            ),
        )
    }

    /**
     * Place a bulk order — buys [casePacksPerItem] case-packs for every eligible item.
     *
     * An item is eligible when ALL of the following hold:
     *  1. Its tier ≤ [GameState.currentTier] (per-item tier gate)
     *  2. Its category matches [categoryFilter] (or filter is null = all categories)
     *  3. Its combined shelf + backroom stock ≤ [maxTotalQuantity]
     *  4. Its backroom has room for at least one full case-pack
     *
     * Per-item delivery is clamped to the remaining backroom space.
     *
     * Volume discount tiers (applied to the total actual case-packs delivered):
     *  ≥ 20 cases  → 10% off
     *  ≥ 50 cases  → 15% off
     *  ≥ 100 cases → 25% off
     *
     * Returns state unchanged if the player cannot afford the discounted total,
     * or if no items qualify.
     */
    fun placeBulkOrder(
        state: GameState,
        maxTotalQuantity: Int,
        casePacksPerItem: Int,
        categoryFilter: ItemCategory?,
    ): GameState {
        if (casePacksPerItem <= 0) return state

        val cap = state.storeConfig.backroomCapPerItem

        val matchingEntries = state.inventory.filter { (itemId, inv) ->
            val meta = cache.get(itemId) ?: return@filter false
            val tierOk = meta.tier.unlockAmount <= state.currentTier.unlockAmount
            val categoryOk = categoryFilter == null || meta.category == categoryFilter
            val qtyOk = inv.shelfStock + inv.backroomStock <= maxTotalQuantity
            val capOk = (cap - inv.backroomStock) >= meta.casePack
            tierOk && categoryOk && qtyOk && capOk
        }
        if (matchingEntries.isEmpty()) return state

        val itemsToAddMap = mutableMapOf<Int, Int>()
        var totalCases = 0
        var baseCost = Money.ZERO

        matchingEntries.forEach { (itemId, inv) ->
            val dbItem = cache.getItem(itemId) ?: return@forEach
            val availableSpace = cap - inv.backroomStock
            val actualCasePacks = minOf(casePacksPerItem, availableSpace / dbItem.casePack)
            if (actualCasePacks <= 0) return@forEach
            itemsToAddMap[itemId] = dbItem.casePack * actualCasePacks
            totalCases += actualCasePacks
            baseCost += dbItem.getCasePackCostAsMoney() * actualCasePacks
        }
        if (itemsToAddMap.isEmpty()) return state

        val discountFraction = when {
            totalCases >= 100 -> 0.25
            totalCases >= 50  -> 0.15
            totalCases >= 20  -> 0.10
            else              -> 0.0
        }
        val finalCost = Money((baseCost.cents * (1.0 - discountFraction)).toLong())
        if (state.money < finalCost) return state

        var newInventory = state.inventory
        var totalItemsAdded = 0
        itemsToAddMap.forEach { (itemId, itemsToAdd) ->
            val inv = newInventory[itemId] ?: return@forEach
            newInventory = newInventory + (itemId to inv.copy(backroomStock = inv.backroomStock + itemsToAdd))
            totalItemsAdded += itemsToAdd
        }

        return state.copy(
            inventory = newInventory,
            money = state.money - finalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItemsAdded,
            ),
        )
    }
}

