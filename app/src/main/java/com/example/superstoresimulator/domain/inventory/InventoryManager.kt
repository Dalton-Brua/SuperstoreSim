package com.example.superstoresimulator.domain.inventory

import com.example.superstoresimulator.domain.FreshAutoOrderConfig
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.GameStateChange
import com.example.superstoresimulator.domain.IncompleteOrderRequest
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.PendingOrderLine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.FreshOrderLineItem
import com.example.superstoresimulator.domain.metrics.IncompleteOrderLineItem

/**
 * Result of a buy operation: the updated [GameState] (money deducted, NO backroom change)
 * plus the [orderLines] to be handed to [TruckManager] for scheduling.
 */
data class BuyResult(
    val state: GameState,
    val orderLines: List<PendingOrderLine>,
)

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
class InventoryManager(
    private val cache: ItemMetadataCache,
    private val truckManager: TruckManager? = null,
) {

    companion object {
        /**
         * Maximum number of future truck delivery slots that can be pre-committed per item.
         * With cap=2 this allows up to 4 case packs committed total (backroom + transit).
         * Used only by [buyItemCasePacks] for multi-truck pre-ordering; [buyItemToBackroom]
         * is strictly capped at [GameState.storeConfig.backroomCapPerItem].
         */
        private const val MAX_TRUCKS_AHEAD = 2
    }

    private fun pendingCasePacksFor(state: GameState, itemId: Int): Int =
        state.scheduledTrucks.flatMap { it.orders }.filter { it.itemId == itemId }.sumOf { it.casePacksCount }

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
            backroomBatches = updatedBackroomBatches,
            zoneScore = 1.0f,
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
            backroomBatches = updatedBackroomBatches,
            zoneScore = 1.0f,
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
     * Order exactly one case-pack of [itemId].
     * Deducts money immediately. Does NOT add to backroom — caller passes returned [BuyResult.orderLines]
     * to TruckManager for scheduling.
     *
     * Uses a strict committed-stock check: counts both backroom stock AND in-transit
     * case packs toward the cap. This prevents indefinite ordering while items are in transit.
     */
    fun buyItemToBackroom(
        state: GameState,
        itemId: Int,
        precomputedPendingCasePacks: Map<Int, Int>? = null,
    ): BuyResult {
        val inv = state.inventory[itemId] ?: return BuyResult(state, emptyList())
        val dbItem = cache.getItem(itemId) ?: return BuyResult(state, emptyList())
        val metadata = cache.get(itemId) ?: return BuyResult(state, emptyList())

        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val currentCasePacksInBackroom = inv.backroomStock / dbItem.casePack
        val pendingCasePacks = precomputedPendingCasePacks?.get(itemId)
            ?: pendingCasePacksFor(state, itemId)
        val totalCommitted = currentCasePacksInBackroom + pendingCasePacks
        if (totalCommitted + 1 > capInCasePacks) return BuyResult(state, emptyList())

        val casePackCost = dbItem.getCasePackCostAsMoney()
        if (state.money < casePackCost) return BuyResult(state, emptyList())

        val currentDay = state.currentTime.dayNumber
        val line = PendingOrderLine(
            itemId = itemId,
            quantity = dbItem.casePack,
            casePacksCount = 1,
            unitCost = dbItem.unitCost.toMoney(),
            orderedOnDay = currentDay,
            isFresh = metadata.isPerishable,
        )

        val newState = state.copy(
            money = state.money - casePackCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + dbItem.casePack,
            ),
        )
        return BuyResult(newState, listOf(line))
    }

    /**
     * Order [numCasePacks] case-packs of [itemId].
     * Deducts money immediately. Does NOT add to backroom.
     *
     * Supports **multi-truck pre-ordering**: orders up to [MAX_TRUCKS_AHEAD] × cap case packs
     * total (counting in-transit). When the requested amount exceeds the per-truck backroom cap,
     * the order is automatically split into lines of at most [cap] case packs each so that
     * TruckManager distributes them across separate trucks, respecting the per-item-per-truck cap.
     */
    fun buyItemCasePacks(
        state: GameState,
        itemId: Int,
        numCasePacks: Int,
        precomputedPendingCasePacks: Map<Int, Int>? = null,
    ): BuyResult {
        val inv = state.inventory[itemId] ?: return BuyResult(state, emptyList())
        val dbItem = cache.getItem(itemId) ?: return BuyResult(state, emptyList())
        val metadata = cache.get(itemId) ?: return BuyResult(state, emptyList())
        if (numCasePacks <= 0) return BuyResult(state, emptyList())

        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val currentCasePacksInBackroom = inv.backroomStock / dbItem.casePack
        val pendingCasePacks = precomputedPendingCasePacks?.get(itemId)
            ?: pendingCasePacksFor(state, itemId)
        val totalCommitted = currentCasePacksInBackroom + pendingCasePacks

        // Allow pre-ordering up to MAX_TRUCKS_AHEAD × cap case packs total (backroom + transit).
        val maxPreOrder = (capInCasePacks * MAX_TRUCKS_AHEAD).coerceAtLeast(capInCasePacks)
        val remainingAllowable = (maxPreOrder - totalCommitted).coerceAtLeast(0)
        val actualCasePacks = minOf(numCasePacks, remainingAllowable)
        if (actualCasePacks <= 0) return BuyResult(state, emptyList())

        val totalCost = dbItem.getCasePackCostAsMoney() * actualCasePacks
        if (state.money < totalCost) return BuyResult(state, emptyList())

        val currentDay = state.currentTime.dayNumber
        val totalItems = dbItem.casePack * actualCasePacks

        // Split order into lines of at most capInCasePacks each so TruckManager can distribute
        // them across separate trucks (enforcing per-item backroom cap per delivery).
        val lines = mutableListOf<PendingOrderLine>()
        var remaining = actualCasePacks
        while (remaining > 0) {
            val batch = minOf(capInCasePacks, remaining)
            lines.add(
                PendingOrderLine(
                    itemId = itemId,
                    quantity = dbItem.casePack * batch,
                    casePacksCount = batch,
                    unitCost = dbItem.unitCost.toMoney(),
                    orderedOnDay = currentDay,
                    isFresh = metadata.isPerishable,
                )
            )
            remaining -= batch
        }

        val newState = state.copy(
            money = state.money - totalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItems,
            ),
        )
        return BuyResult(newState, lines)
    }

    /**
     * Place a bulk order — deducts money, does NOT add to backroom.
     * Returns [BuyResult] with order lines to be scheduled by TruckManager.
     *
     * An item is eligible when ALL of the following hold:
     *  1. Its tier ≤ [GameState.currentTier]
     *  2. Its category matches [categoryFilter] (or filter is null)
     *  3. Its combined shelf + backroom stock ≤ [maxTotalQuantity]
     *  4. Its committed stock (backroom + in-transit) < [backroomCapPerItem]
     *  5. It is NOT a fresh/perishable item
     *
     * Volume discount tiers (applied to the total actual case-packs):
     *  ≥ 20 cases → 10% off
     *  ≥ 50 cases → 15% off
     *  ≥ 100 cases → 25% off
     */
    fun placeBulkOrder(
        state: GameState,
        maxTotalQuantity: Int,
        casePacksPerItem: Int,
        categoryFilter: ItemCategory?,
    ): BuyResult {
        if (casePacksPerItem <= 0) return BuyResult(state, emptyList())

        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val currentDay = state.currentTime.dayNumber

        // Precompute pending (in-transit) case packs per item once, outside the filter loop.
        val pendingCasePacksMap = mutableMapOf<Int, Int>()
        for (truck in state.scheduledTrucks) {
            for (line in truck.orders) {
                pendingCasePacksMap[line.itemId] =
                    (pendingCasePacksMap[line.itemId] ?: 0) + line.casePacksCount
            }
        }

        val matchingEntries = state.inventory.filter { (itemId, inv) ->
            val meta = cache.get(itemId) ?: return@filter false
            val tierOk = meta.tier.unlockAmount <= state.currentTier.unlockAmount
            val categoryOk = categoryFilter == null || meta.category == categoryFilter
            val qtyOk = inv.shelfStock + inv.backroomStock <= maxTotalQuantity
            val currentCasePacksInBackroom = inv.backroomStock / meta.casePack
            val pendingCasePacks = pendingCasePacksMap[itemId] ?: 0
            val totalCommitted = currentCasePacksInBackroom + pendingCasePacks
            val capOk = totalCommitted < capInCasePacks
            val notFresh = !isFreshItem(itemId)
            tierOk && categoryOk && qtyOk && capOk && notFresh
        }
        if (matchingEntries.isEmpty()) return BuyResult(state, emptyList())

        data class OrderInfo(val itemsToAdd: Int, val actualCasePacks: Int)
        val itemsToAddMap = mutableMapOf<Int, OrderInfo>()
        var totalCases = 0
        var baseCost = Money.ZERO

        matchingEntries.forEach { (itemId, inv) ->
            val dbItem = cache.getItem(itemId) ?: return@forEach
            val currentCasePacksInBackroom = inv.backroomStock / dbItem.casePack
            val pendingCasePacks = pendingCasePacksMap[itemId] ?: 0
            val totalCommitted = currentCasePacksInBackroom + pendingCasePacks
            val availableCasePacks = capInCasePacks - totalCommitted
            val actualCasePacks = minOf(casePacksPerItem, availableCasePacks)
            if (actualCasePacks <= 0) return@forEach

            itemsToAddMap[itemId] = OrderInfo(
                itemsToAdd = dbItem.casePack * actualCasePacks,
                actualCasePacks = actualCasePacks,
            )
            totalCases += actualCasePacks
            baseCost += dbItem.getCasePackCostAsMoney() * actualCasePacks
        }
        if (itemsToAddMap.isEmpty()) return BuyResult(state, emptyList())

        val discountFraction = when {
            totalCases >= 100 -> 0.25
            totalCases >= 50  -> 0.15
            totalCases >= 20  -> 0.10
            else              -> 0.0
        }
        val finalCost = Money((baseCost.cents * (1.0 - discountFraction)).toLong())
        if (state.money < finalCost) return BuyResult(state, emptyList())

        val lines = mutableListOf<PendingOrderLine>()
        var totalItemsAdded = 0
        itemsToAddMap.forEach { (itemId, orderInfo) ->
            val dbItem = cache.getItem(itemId) ?: return@forEach
            lines.add(
                PendingOrderLine(
                    itemId = itemId,
                    quantity = orderInfo.itemsToAdd,
                    casePacksCount = orderInfo.actualCasePacks,
                    unitCost = dbItem.unitCost.toMoney(),
                    orderedOnDay = currentDay,
                    isFresh = false,
                )
            )
            totalItemsAdded += orderInfo.itemsToAdd
        }

        val newState = state.copy(
            money = state.money - finalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItemsAdded,
            ),
        )
        return BuyResult(newState, lines)
    }

    private data class PrivateOrderInfo(
        val itemsToAdd: Int,
        val actualCasePacks: Int,
    )

    /**
     * Place a fresh bulk order — deducts money, does NOT add to backroom.
     * Returns [BuyResult] with order lines (all fresh) to be scheduled on the fresh truck.
     */
    fun placeFreshBulkOrder(
        state: GameState,
        maxTotalQuantity: Int,
        casePacksPerItem: Int,
        currentTier: com.example.superstoresimulator.domain.items.ItemUnlockTier
    ): BuyResult {
        val currentDay = state.currentTime.dayNumber
        val itemsToAddMap = mutableMapOf<Int, PrivateOrderInfo>()
        var totalCases = 0
        var baseCost = Money(0)

        for ((itemId, inv) in state.inventory) {
            val dbItem = cache.getItem(itemId) ?: continue
            val metadata = cache.get(itemId) ?: continue

            if (!isFreshItem(itemId)) continue
            if (metadata.tier.unlockAmount > currentTier.unlockAmount) continue

            val currentTotal = inv.shelfStock + inv.backroomStock
            if (currentTotal >= maxTotalQuantity) continue

            val actualCasePacks = casePacksPerItem
            itemsToAddMap[itemId] = PrivateOrderInfo(
                itemsToAdd = dbItem.casePack * actualCasePacks,
                actualCasePacks = actualCasePacks,
            )
            totalCases += actualCasePacks
            baseCost += dbItem.getCasePackCostAsMoney() * actualCasePacks
        }
        if (itemsToAddMap.isEmpty()) return BuyResult(state, emptyList())

        val discountFraction = when {
            totalCases >= 50  -> 0.15
            totalCases >= 30  -> 0.10
            totalCases >= 15  -> 0.05
            else              -> 0.0
        }
        val finalCost = Money((baseCost.cents * (1.0 - discountFraction)).toLong())
        if (state.money < finalCost) return BuyResult(state, emptyList())

        val lines = mutableListOf<PendingOrderLine>()
        var totalItemsAdded = 0
        itemsToAddMap.forEach { (itemId, orderInfo) ->
            val dbItem = cache.getItem(itemId) ?: return@forEach
            lines.add(
                PendingOrderLine(
                    itemId = itemId,
                    quantity = orderInfo.itemsToAdd,
                    casePacksCount = orderInfo.actualCasePacks,
                    unitCost = dbItem.unitCost.toMoney(),
                    orderedOnDay = currentDay,
                    isFresh = true,
                )
            )
            totalItemsAdded += orderInfo.itemsToAdd
        }

        val newState = state.copy(
            money = state.money - finalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItemsAdded,
            ),
        )
        return BuyResult(newState, lines)
    }


    // ── Fresh item auto-ordering operations ───────────────────────────────────

    /**
     * Check if an item should trigger auto-ordering based on current stock and config.
     */
    fun shouldAutoOrderFreshItem(
        state: GameState,
        itemId: Int,
        config: FreshAutoOrderConfig
    ): Boolean {
        if (!config.enabled) return false
        if (!isFreshItem(itemId)) return false

        val inv = state.inventory[itemId] ?: return false
        val totalStock = inv.shelfStock + inv.backroomStock

        return totalStock < config.minStockThreshold
    }

    // ── Order scheduling (moved from GameEngine) ─────────────────────────────

    data class OrderResult(val state: GameState, val changes: List<GameStateChange>)

    fun scheduleAndEmitOrder(state: GameState, result: BuyResult, moneyBefore: Money): OrderResult {
        val tm = truckManager ?: return OrderResult(result.state, emptyList())
        var s = result.state
        val changes = mutableListOf<GameStateChange>()
        if (result.orderLines.isNotEmpty()) {
            val currentDay = s.currentTime.dayNumber
            val fresh = result.orderLines.filter { it.isFresh }
            val regular = result.orderLines.filter { !it.isFresh }
            if (regular.isNotEmpty()) s = tm.scheduleRegularOrderLines(s, regular, currentDay)
            if (fresh.isNotEmpty()) s = tm.scheduleFreshOrderLines(s, fresh, currentDay)
            val arrivalDay = s.scheduledTrucks
                .filter { t -> result.orderLines.any { l -> t.orders.any { o -> o.itemId == l.itemId } } }
                .minOfOrNull { it.scheduledArrivalDay } ?: (currentDay + 1)
            changes.add(GameStateChange.OrderScheduled(arrivalDay))
        }
        if (s.money != moneyBefore) changes.add(GameStateChange.MoneyChanged(s.money))
        return OrderResult(s, changes)
    }

    fun orderIncompleteItem(state: GameState, itemId: Int, casePacksRequested: Int): OrderResult {
        val tm = truckManager ?: return OrderResult(state, emptyList())
        val item = cache.getItem(itemId) ?: return OrderResult(state, emptyList())
        val totalCost = item.getCasePackCostAsMoney() * casePacksRequested

        if (state.money < totalCost) return OrderResult(state, emptyList())

        val result = buyItemCasePacks(state, itemId, casePacksRequested)
        var s = result.state
        val changes = mutableListOf<GameStateChange>()
        if (result.orderLines.isNotEmpty()) {
            val currentDay = s.currentTime.dayNumber
            s = tm.scheduleFreshOrderLines(s, result.orderLines, currentDay)
            changes.add(GameStateChange.OrderScheduled(currentDay + 1))
        }
        s = s.copy(
            incompleteFreshOrders = s.incompleteFreshOrders.filter { it.itemId != itemId }
        )
        changes.add(GameStateChange.MoneyChanged(s.money))
        return OrderResult(s, changes)
    }

    fun attemptFreshHandlerAutoOrder(state: GameState): GameState {
        val tm = truckManager ?: return state
        if (!state.freshAutoOrderConfig.enabled) return state

        val alreadyHandled = (state.currentDayMetrics.autoOrderedFreshItems.map { it.itemId } +
            state.currentDayMetrics.incompleteOrderedFreshItems.map { it.itemId }).toSet()

        val pendingCasePacksByItem = mutableMapOf<Int, Int>()
        for (truck in state.scheduledTrucks) {
            for (line in truck.orders) {
                pendingCasePacksByItem[line.itemId] = (pendingCasePacksByItem[line.itemId] ?: 0) + line.casePacksCount
            }
        }

        var s = state
        for ((itemId, _) in s.inventory) {
            if (itemId in alreadyHandled) continue
            if (!shouldAutoOrderFreshItem(s, itemId, s.freshAutoOrderConfig)) continue

            val item = cache.getItem(itemId) ?: continue
            val casePacks = s.freshAutoOrderConfig.casePacksPerItem
            val totalCost = item.getCasePackCostAsMoney() * casePacks

            if (s.money >= totalCost) {
                val result = buyItemCasePacks(s, itemId, casePacks, pendingCasePacksByItem)
                s = result.state
                if (result.orderLines.isNotEmpty()) {
                    val currentDay = s.currentTime.dayNumber
                    s = tm.scheduleFreshOrderLines(s, result.orderLines, currentDay)
                }
                s = s.copy(
                    currentDayMetrics = s.currentDayMetrics.copy(
                        autoOrderedFreshItems = s.currentDayMetrics.autoOrderedFreshItems +
                            FreshOrderLineItem(
                                itemId = itemId,
                                itemName = item.name,
                                casePacksOrdered = casePacks,
                                costPerCasePack = item.getCasePackCostAsMoney(),
                                totalCost = totalCost,
                            )
                    )
                )
            } else {
                s = s.copy(
                    currentDayMetrics = s.currentDayMetrics.copy(
                        incompleteOrderedFreshItems = s.currentDayMetrics.incompleteOrderedFreshItems +
                            IncompleteOrderLineItem(
                                itemId = itemId,
                                itemName = item.name,
                                casePacksRequested = casePacks,
                                costPerCasePack = item.getCasePackCostAsMoney(),
                                totalCost = totalCost,
                                reason = "Insufficient funds",
                            )
                    ),
                    incompleteFreshOrders = s.incompleteFreshOrders.filter { it.itemId != itemId } +
                        IncompleteOrderRequest(
                            itemId = itemId,
                            casePacksRequested = casePacks,
                            requestedOnDay = s.currentTime.dayNumber,
                            reason = "Insufficient funds",
                        )
                )
            }
        }
        return s
    }
}
