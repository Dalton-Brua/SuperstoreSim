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
     * Move one unit of [itemId] from backroom to shelf using FIFO (oldest batch first).
     *
     * Returns the state unchanged when the item is not in inventory or the
     * backroom is already empty.
     */
    fun stockItemFromBackroom(state: GameState, itemId: Int): GameState {
        val inv = state.inventory[itemId] ?: return state
        if (inv.backroomStock <= 0) return state

        // Take from oldest backroom batch (FIFO)
        val oldestBatch = inv.oldestBackroomBatch() ?: return state
        
        // Remove 1 item from oldest batch
        val updatedBackroomBatches = inv.backroomBatches.mapNotNull { batch ->
            if (batch.receivedDay == oldestBatch.receivedDay && batch.expirationDay == oldestBatch.expirationDay) {
                if (batch.quantity > 1) batch.copy(quantity = batch.quantity - 1) else null
            } else {
                batch
            }
        }
        
        // Add 1 item to shelf (same batch, or create new if batch doesn't exist on shelf)
        val existingShelfBatch = inv.shelfBatches.find { 
            it.receivedDay == oldestBatch.receivedDay && it.expirationDay == oldestBatch.expirationDay 
        }
        
        val updatedShelfBatches = if (existingShelfBatch != null) {
            inv.shelfBatches.map { batch ->
                if (batch.receivedDay == oldestBatch.receivedDay && batch.expirationDay == oldestBatch.expirationDay) {
                    batch.copy(quantity = batch.quantity + 1)
                } else {
                    batch
                }
            }
        } else {
            inv.shelfBatches + ItemBatch(
                receivedDay = oldestBatch.receivedDay,
                quantity = 1,
                expirationDay = oldestBatch.expirationDay
            )
        }
        
        val updated = inv.copy(
            shelfBatches = updatedShelfBatches,
            backroomBatches = updatedBackroomBatches
        )
        
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsStocked = state.currentDayMetrics.itemsStocked + 1,
            ),
        )
    }

    /**
     * Check if an item is perishable (fresh).
     */
    private fun isFreshItem(itemId: Int): Boolean {
        val item = cache.getItem(itemId) ?: return false
        return item.shelfLifeDays != null
    }

    /**
     * Find the item with the lowest shelf stock that still has backroom inventory,
     * then stock one full case-pack of it onto the shelf.
     * Excludes fresh/perishable items (those with shelfLifeDays).
     *
     * Returns the state unchanged when every non-fresh item's backroom is empty.
     */
    fun stockRandomItemFromBackroom(state: GameState): GameState {
        val candidates = state.inventory.filter { (itemId, dyn) -> 
            dyn.backroomStock > 0 && !isFreshItem(itemId)
        }
        if (candidates.isEmpty()) return state

        val lowestShelf = candidates.minOf { it.value.shelfStock }
        val lowestGroup = candidates.filter { it.value.shelfStock == lowestShelf }
        val targetId = lowestGroup.keys.random()

        return stockCasePackFromBackroom(state, targetId)
    }

    /**
     * Find the fresh/perishable item with the lowest shelf stock that still has backroom inventory,
     * then stock one full case-pack of it onto the shelf.
     *
     * Returns the state unchanged when every fresh item's backroom is empty.
     */
    fun stockRandomFreshItemFromBackroom(state: GameState): GameState {
        val candidates = state.inventory.filter { (itemId, dyn) -> 
            dyn.backroomStock > 0 && isFreshItem(itemId)
        }
        if (candidates.isEmpty()) return state

        val lowestShelf = candidates.minOf { it.value.shelfStock }
        val lowestGroup = candidates.filter { it.value.shelfStock == lowestShelf }
        val targetId = lowestGroup.keys.random()

        return stockCasePackFromBackroom(state, targetId)
    }

    /**
     * Move up to one full case-pack of [itemId] from backroom to shelf using FIFO.
     * If fewer items than the case-pack size remain in the backroom, all remaining
     * units are stocked.
     */
    private fun stockCasePackFromBackroom(state: GameState, itemId: Int): GameState {
        val inv = state.inventory[itemId] ?: return state
        val dbItem = cache.getItem(itemId) ?: return state
        if (inv.backroomStock <= 0) return state

        val itemsToStock = minOf(dbItem.casePack, inv.backroomStock)
        var remaining = itemsToStock
        var updatedBackroomBatches = inv.backroomBatches
        val batchesToMove = mutableListOf<ItemBatch>()
        
        // Take from oldest batches first (FIFO) until we have itemsToStock
        while (remaining > 0 && updatedBackroomBatches.isNotEmpty()) {
            val oldestBatch = updatedBackroomBatches.minByOrNull { it.receivedDay } ?: break
            val takeQty = minOf(remaining, oldestBatch.quantity)
            
            batchesToMove.add(ItemBatch(
                receivedDay = oldestBatch.receivedDay,
                quantity = takeQty,
                expirationDay = oldestBatch.expirationDay
            ))
            
            updatedBackroomBatches = updatedBackroomBatches.mapNotNull { batch ->
                if (batch.receivedDay == oldestBatch.receivedDay && batch.expirationDay == oldestBatch.expirationDay) {
                    if (batch.quantity > takeQty) batch.copy(quantity = batch.quantity - takeQty) else null
                } else {
                    batch
                }
            }
            
            remaining -= takeQty
        }
        
        // Add batches to shelf (merge with existing batches if same expirationDay)
        val updatedShelfBatches = inv.mergeBatches(inv.shelfBatches + batchesToMove)
        
        val updated = inv.copy(
            shelfBatches = updatedShelfBatches,
            backroomBatches = updatedBackroomBatches
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
     * Creates a new batch with receivedDay = currentDay and expirationDay based on shelfLifeDays.
     *
     * Guards (all return state unchanged on failure):
     *  - Item not in inventory or not in cache
     *  - Adding a full case-pack would exceed [StoreConfig.backroomCapPerItem] (in case packs)
     *  - Player cannot afford the case-pack cost
     */
    fun buyItemToBackroom(state: GameState, itemId: Int): GameState {
        val inv = state.inventory[itemId] ?: return state
        val dbItem = cache.getItem(itemId) ?: return state
        val metadata = cache.get(itemId) ?: return state

        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val currentCasePacksInBackroom = inv.backroomStock / dbItem.casePack
        if (currentCasePacksInBackroom + 1 > capInCasePacks) return state

        val casePackCost = dbItem.getCasePackCostAsMoney()
        if (state.money < casePackCost) return state

        // Create new batch
        val currentDay = state.currentTime.dayNumber
        val expirationDay = if (metadata.isPerishable) {
            currentDay + (metadata.shelfLifeDays ?: 0)
        } else {
            Int.MAX_VALUE  // Never expires
        }
        
        val newBatch = ItemBatch(
            receivedDay = currentDay,
            quantity = dbItem.casePack,
            expirationDay = expirationDay
        )
        
        // Merge with existing batches that have the same expirationDay
        val updatedBackroomBatches = inv.mergeBatches(inv.backroomBatches + newBatch)
        
        val updated = inv.copy(backroomBatches = updatedBackroomBatches)
        
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
     * Creates new batches with receivedDay = currentDay and expirationDay based on shelfLifeDays.
     *
     * Delivery is clamped to however many full case-packs fit within the remaining
     * backroom space (measured in case packs). The player is charged only for case-packs actually delivered.
     *
     * Guards (all return state unchanged on failure):
     *  - Item not in inventory or cache, or [numCasePacks] ≤ 0
     *  - No room for even one full case-pack
     *  - Player cannot afford the clamped delivery cost
     */
    fun buyItemCasePacks(state: GameState, itemId: Int, numCasePacks: Int): GameState {
        val inv = state.inventory[itemId] ?: return state
        val dbItem = cache.getItem(itemId) ?: return state
        val metadata = cache.get(itemId) ?: return state
        if (numCasePacks <= 0) return state

        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val currentCasePacksInBackroom = inv.backroomStock / dbItem.casePack
        val availableCasePacks = capInCasePacks - currentCasePacksInBackroom
        val actualCasePacks = minOf(numCasePacks, availableCasePacks)
        if (actualCasePacks <= 0) return state

        val totalCost = dbItem.getCasePackCostAsMoney() * actualCasePacks
        if (state.money < totalCost) return state

        // Create new batch
        val currentDay = state.currentTime.dayNumber
        val expirationDay = if (metadata.isPerishable) {
            currentDay + (metadata.shelfLifeDays ?: 0)
        } else {
            Int.MAX_VALUE  // Never expires
        }
        
        val totalItemsAdded = dbItem.casePack * actualCasePacks
        val newBatch = ItemBatch(
            receivedDay = currentDay,
            quantity = totalItemsAdded,
            expirationDay = expirationDay
        )
        
        // Merge with existing batches that have the same expirationDay
        val updatedBackroomBatches = inv.mergeBatches(inv.backroomBatches + newBatch)
        
        val updated = inv.copy(backroomBatches = updatedBackroomBatches)
        
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
     *  4. Its backroom has room for at least one full case-pack (measured in case packs)
     *
     * Per-item delivery is clamped to the remaining backroom space (in case packs).
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

        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val currentDay = state.currentTime.dayNumber

        val matchingEntries = state.inventory.filter { (itemId, inv) ->
            val meta = cache.get(itemId) ?: return@filter false
            val tierOk = meta.tier.unlockAmount <= state.currentTier.unlockAmount
            val categoryOk = categoryFilter == null || meta.category == categoryFilter
            val qtyOk = inv.shelfStock + inv.backroomStock <= maxTotalQuantity
            val currentCasePacksInBackroom = inv.backroomStock / meta.casePack
            val capOk = currentCasePacksInBackroom < capInCasePacks
            // Exclude fresh items from regular bulk order
            val notFresh = !isFreshItem(itemId)
            tierOk && categoryOk && qtyOk && capOk && notFresh
        }
        if (matchingEntries.isEmpty()) return state

        data class OrderInfo(val itemsToAdd: Int, val expirationDay: Int)
        val itemsToAddMap = mutableMapOf<Int, OrderInfo>()
        var totalCases = 0
        var baseCost = Money.ZERO

        matchingEntries.forEach { (itemId, inv) ->
            val dbItem = cache.getItem(itemId) ?: return@forEach
            val metadata = cache.get(itemId) ?: return@forEach
            val currentCasePacksInBackroom = inv.backroomStock / dbItem.casePack
            val availableCasePacks = capInCasePacks - currentCasePacksInBackroom
            val actualCasePacks = minOf(casePacksPerItem, availableCasePacks)
            if (actualCasePacks <= 0) return@forEach
            
            val expirationDay = if (metadata.isPerishable) {
                currentDay + (metadata.shelfLifeDays ?: 0)
            } else {
                Int.MAX_VALUE
            }
            
            itemsToAddMap[itemId] = OrderInfo(
                itemsToAdd = dbItem.casePack * actualCasePacks,
                expirationDay = expirationDay
            )
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
        itemsToAddMap.forEach { (itemId, orderInfo) ->
            val inv = newInventory[itemId] ?: return@forEach
            
            val newBatch = ItemBatch(
                receivedDay = currentDay,
                quantity = orderInfo.itemsToAdd,
                expirationDay = orderInfo.expirationDay
            )
            
            val updatedBackroomBatches = inv.mergeBatches(inv.backroomBatches + newBatch)
            
            newInventory = newInventory + (itemId to inv.copy(backroomBatches = updatedBackroomBatches))
            totalItemsAdded += orderInfo.itemsToAdd
        }

        return state.copy(
            inventory = newInventory,
            money = state.money - finalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItemsAdded,
            ),
        )
    }

    // ── Fresh item auto-ordering operations ───────────────────────────────────

    /**
     * Private helper data class for ordering operations.
     */
    private data class OrderInfo(
        val itemsToAdd: Int,
        val expirationDay: Int,
    )


    /**
     * Check if an item should trigger auto-ordering based on current stock and config.
     */
    fun shouldAutoOrderFreshItem(
        state: GameState,
        itemId: Int,
        config: com.example.superstoresimulator.domain.FreshAutoOrderConfig
    ): Boolean {
        if (!config.enabled) return false
        if (!isFreshItem(itemId)) return false
        
        val inv = state.inventory[itemId] ?: return false
        val totalStock = inv.shelfStock + inv.backroomStock
        
        return totalStock < config.minStockThreshold
    }


    /**
     * Create a fresh bulk order with lower discount tiers than regular items.
     * Only includes fresh (perishable) items.
     */
    fun placeFreshBulkOrder(
        state: GameState,
        maxTotalQuantity: Int,
        casePacksPerItem: Int,
        currentTier: com.example.superstoresimulator.domain.items.ItemUnlockTier
    ): GameState {
        val currentDay = state.currentTime.dayNumber
        val itemsToAddMap = mutableMapOf<Int, OrderInfo>()
        var totalCases = 0
        var baseCost = Money(0)

        for ((itemId, inv) in state.inventory) {
            val dbItem = cache.getItem(itemId) ?: continue
            val metadata = cache.get(itemId) ?: continue
            
            // Only fresh items
            if (!isFreshItem(itemId)) continue
            
            // Check tier gate
            if (metadata.tier.unlockAmount > currentTier.unlockAmount) continue
            
            val currentTotal = inv.shelfStock + inv.backroomStock
            
            // Only if below max total
            if (currentTotal >= maxTotalQuantity) continue
            
            val expirationDay = currentDay + (dbItem.shelfLifeDays ?: 0)
            val actualCasePacks = casePacksPerItem
            
            itemsToAddMap[itemId] = OrderInfo(
                itemsToAdd = dbItem.casePack * actualCasePacks,
                expirationDay = expirationDay
            )
            totalCases += actualCasePacks
            baseCost += dbItem.getCasePackCostAsMoney() * actualCasePacks
        }
        if (itemsToAddMap.isEmpty()) return state

        // Fresh discount tiers (lower than regular)
        val discountFraction = when {
            totalCases >= 50  -> 0.15
            totalCases >= 30  -> 0.10
            totalCases >= 15  -> 0.05
            else              -> 0.0
        }
        val finalCost = Money((baseCost.cents * (1.0 - discountFraction)).toLong())
        if (state.money < finalCost) return state

        var newInventory = state.inventory
        var totalItemsAdded = 0
        itemsToAddMap.forEach { (itemId, orderInfo) ->
            val inv = newInventory[itemId] ?: return@forEach
            
            val newBatch = ItemBatch(
                receivedDay = currentDay,
                quantity = orderInfo.itemsToAdd,
                expirationDay = orderInfo.expirationDay
            )
            
            val updatedBackroomBatches = inv.mergeBatches(inv.backroomBatches + newBatch)
            
            newInventory = newInventory + (itemId to inv.copy(backroomBatches = updatedBackroomBatches))
            totalItemsAdded += orderInfo.itemsToAdd
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
