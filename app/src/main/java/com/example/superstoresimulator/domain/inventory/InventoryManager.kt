package com.example.superstoresimulator.domain.inventory

import com.example.superstoresimulator.domain.FreshAutoOrderConfig
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.IncompleteOrderRequest
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.NormalAutoOrderConfig
import com.example.superstoresimulator.domain.PendingOrderLine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.AutoOrderLineItem
import com.example.superstoresimulator.domain.metrics.IncompleteAutoOrderLineItem

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
 * [GameState]. No reference to the engine's mutable state is held here —
 * [GameEngine] retains those responsibilities.
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
     * Find the [freshOnly]-matching item with the lowest shelf stock that still has
     * backroom inventory, then stock one full case-pack of it onto the shelf.
     * Returns the state unchanged when no matching item has backroom stock.
     */
    fun stockRandomFromBackroom(state: GameState, freshOnly: Boolean = false): GameState {
        val candidates = state.inventory.filter { (itemId, dyn) ->
            dyn.backroomStock > 0 && isFreshItem(itemId) == freshOnly
        }
        if (candidates.isEmpty()) return state

        val lowestShelf = candidates.minOf { it.value.shelfStock }
        val targetId = candidates.filter { it.value.shelfStock == lowestShelf }.keys.random()
        return stockCasePackFromBackroom(state, targetId)
    }

    /**
     * Batch-stock multiple case packs in one map copy. Used during skip/offline simulation
     * to avoid per-action map copies.
     */
    fun stockMultipleFromBackroom(state: GameState, count: Int, freshOnly: Boolean = false): GameState {
        if (count <= 0) return state
        val mutableInv = state.inventory.toMutableMap()
        var itemsStocked = 0
        repeat(count) {
            val candidates = mutableInv.filter { (id, inv) ->
                inv.backroomStock > 0 && if (freshOnly) isFreshItem(id) else !isFreshItem(id)
            }
            if (candidates.isEmpty()) return@repeat
            val lowestShelf = candidates.minOf { it.value.shelfStock }
            val lowestGroup = candidates.filter { it.value.shelfStock == lowestShelf }
            val targetId = lowestGroup.keys.random()
            itemsStocked += stockCasePackInPlace(mutableInv, targetId)
        }
        if (itemsStocked == 0) return state
        return state.copy(
            inventory = mutableInv,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsStocked = state.currentDayMetrics.itemsStocked + itemsStocked
            ),
        )
    }

    /**
     * Drain up to [count] units from the oldest backroom batches (FIFO).
     * Returns the moved batches plus the remaining backroom batches.
     */
    private fun drainBackroomFifo(
        backroomBatches: List<ItemBatch>,
        count: Int,
    ): Pair<List<ItemBatch>, List<ItemBatch>> {
        var remaining = count
        var remainingBatches = backroomBatches
        val moved = mutableListOf<ItemBatch>()
        while (remaining > 0 && remainingBatches.isNotEmpty()) {
            val oldest = remainingBatches.minByOrNull { it.receivedDay } ?: break
            val takeQty = minOf(remaining, oldest.quantity)
            moved.add(ItemBatch(oldest.receivedDay, takeQty, oldest.expirationDay))
            remainingBatches = remainingBatches.mapNotNull { batch ->
                if (batch.receivedDay == oldest.receivedDay && batch.expirationDay == oldest.expirationDay) {
                    if (batch.quantity > takeQty) batch.copy(quantity = batch.quantity - takeQty) else null
                } else batch
            }
            remaining -= takeQty
        }
        return moved to remainingBatches
    }

    private fun stockCasePackInPlace(map: MutableMap<Int, InventoryState>, itemId: Int): Int {
        val inv = map[itemId] ?: return 0
        val dbItem = cache.getItem(itemId) ?: return 0
        if (inv.backroomStock <= 0) return 0

        val itemsToStock = minOf(dbItem.casePack, inv.backroomStock)
        val (moved, remainingBackroom) = drainBackroomFifo(inv.backroomBatches, itemsToStock)
        map[itemId] = inv.copy(
            shelfBatches = inv.mergeBatches(inv.shelfBatches + moved),
            backroomBatches = remainingBackroom,
            zoneScore = 1.0f,
        )
        return itemsToStock
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
        val (moved, remainingBackroom) = drainBackroomFifo(inv.backroomBatches, itemsToStock)
        val updated = inv.copy(
            shelfBatches = inv.mergeBatches(inv.shelfBatches + moved),
            backroomBatches = remainingBackroom,
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
    ): BuyResult =
        // ponytail: ordering one case pack == buyItemCasePacks(.., 1). Assumes
        // backroomCapPerItem >= 1 (always true); cap == 0 would mean no backroom anyway.
        buyItemCasePacks(state, itemId, 1, precomputedPendingCasePacks)

    /**
     * Order up to [numCasePacks] case-packs of [itemId], capped so that committed stock
     * (backroom + in-transit case-packs) never exceeds [backroomCapPerItem].
     * Deducts money immediately only for the case-packs that will actually fit at delivery.
     * Does NOT add to backroom — caller schedules the returned lines via TruckManager.
     *
     * [precomputedPendingCasePacks] supplies in-transit case-packs per item (from
     * [buildPendingCasePacksMap]); when null it is computed on demand.
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
        if (metadata.isVendorItem) return BuyResult(state, emptyList())
        if (numCasePacks <= 0) return BuyResult(state, emptyList())

        // Cap to remaining room: backroomCapPerItem minus what is already committed
        // (current backroom case-packs + in-transit case-packs). Mirrors the clamp in
        // TruckManager.processArrivals so we never charge for stock that can't land.
        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val inTransit = (precomputedPendingCasePacks ?: buildPendingCasePacksMap(state))[itemId] ?: 0
        val committed = inv.backroomStock / dbItem.casePack + inTransit
        val remainingCap = (capInCasePacks - committed).coerceAtLeast(0)
        val actualCasePacks = minOf(numCasePacks, remainingCap)
        if (actualCasePacks <= 0) return BuyResult(state, emptyList())

        val totalCost = dbItem.getCasePackCostAsMoney() * actualCasePacks
        if (state.money < totalCost) return BuyResult(state, emptyList())

        val currentDay = state.currentTime.dayNumber
        val totalItems = dbItem.casePack * actualCasePacks

        // actualCasePacks <= capInCasePacks, so a single line never exceeds the per-delivery cap.
        val line = PendingOrderLine(
            itemId = itemId,
            quantity = totalItems,
            casePacksCount = actualCasePacks,
            unitCost = dbItem.unitCost.toMoney(),
            orderedOnDay = currentDay,
            isFresh = metadata.isPerishable,
        )

        val newState = state.copy(
            money = state.money - totalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItems,
            ),
        )
        return BuyResult(newState, listOf(line))
    }

    /**
     * Place a bulk order — deducts money, does NOT add to backroom.
     * Returns [BuyResult] with order lines to be scheduled by TruckManager.
     *
     * An item is eligible when ALL of the following hold:
     *  1. It is accessible (research gate unlocked or no gate)
     *  2. Its category matches [categoryFilter] (or filter is null)
     *  3. Its combined shelf + backroom stock ≤ [maxTotalQuantity]
     *  4. It is NOT a fresh/perishable item and NOT a vendor item
     *
     * Per item the order is capped so committed stock (backroom + in-transit) stays within
     * [backroomCapPerItem]. Volume discount on total case-packs: ≥20→10%, ≥50→15%, ≥100→25%.
     */
    fun placeBulkOrder(
        state: GameState,
        maxTotalQuantity: Int,
        casePacksPerItem: Int,
        categoryFilter: ItemCategory?,
    ): BuyResult {
        val researched = state.researchState.researchedUpgrades
        return placeBulkOrderInternal(
            state, casePacksPerItem, isFresh = false,
            discountFor = { cases ->
                when {
                    cases >= 100 -> 0.25
                    cases >= 50  -> 0.15
                    cases >= 20  -> 0.10
                    else         -> 0.0
                }
            },
            eligible = { itemId, inv ->
                val meta = cache.get(itemId)
                meta != null &&
                    (meta.researchGate == null || meta.researchGate in researched) &&
                    (categoryFilter == null || meta.category == categoryFilter) &&
                    inv.shelfStock + inv.backroomStock <= maxTotalQuantity &&
                    !isFreshItem(itemId) &&
                    !meta.isVendorItem
            },
        )
    }

    /**
     * Place a fresh bulk order — deducts money, does NOT add to backroom.
     * Returns [BuyResult] with order lines (all fresh) to be scheduled on the fresh truck.
     * Eligibility: accessible, fresh/perishable, and combined shelf + backroom < [maxTotalQuantity].
     */
    fun placeFreshBulkOrder(
        state: GameState,
        maxTotalQuantity: Int,
        casePacksPerItem: Int,
    ): BuyResult {
        val researched = state.researchState.researchedUpgrades
        return placeBulkOrderInternal(
            state, casePacksPerItem, isFresh = true,
            discountFor = { cases ->
                when {
                    cases >= 50 -> 0.15
                    cases >= 30 -> 0.10
                    cases >= 15 -> 0.05
                    else        -> 0.0
                }
            },
            eligible = { itemId, inv ->
                val meta = cache.get(itemId)
                meta != null &&
                    isFreshItem(itemId) &&
                    (meta.researchGate == null || meta.researchGate in researched) &&
                    inv.shelfStock + inv.backroomStock < maxTotalQuantity
            },
        )
    }

    /**
     * Shared body for [placeBulkOrder]/[placeFreshBulkOrder]. Per eligible item, orders
     * [casePacksPerItem] case-packs capped to remaining room (cap − backroom − in-transit),
     * applies the volume + reputation discount, and deducts money for what will actually fit.
     */
    private fun placeBulkOrderInternal(
        state: GameState,
        casePacksPerItem: Int,
        isFresh: Boolean,
        discountFor: (totalCases: Int) -> Double,
        eligible: (itemId: Int, inv: InventoryState) -> Boolean,
    ): BuyResult {
        if (casePacksPerItem <= 0) return BuyResult(state, emptyList())

        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val currentDay = state.currentTime.dayNumber
        val pending = buildPendingCasePacksMap(state)

        val lines = mutableListOf<PendingOrderLine>()
        var totalCases = 0
        var baseCost = Money.ZERO
        var totalItemsAdded = 0

        for ((itemId, inv) in state.inventory) {
            if (!eligible(itemId, inv)) continue
            val dbItem = cache.getItem(itemId) ?: continue
            val committed = inv.backroomStock / dbItem.casePack + (pending[itemId] ?: 0)
            val remainingCap = (capInCasePacks - committed).coerceAtLeast(0)
            val actualCasePacks = minOf(casePacksPerItem, remainingCap)
            if (actualCasePacks <= 0) continue

            val itemsToAdd = dbItem.casePack * actualCasePacks
            lines.add(
                PendingOrderLine(
                    itemId = itemId,
                    quantity = itemsToAdd,
                    casePacksCount = actualCasePacks,
                    unitCost = dbItem.unitCost.toMoney(),
                    orderedOnDay = currentDay,
                    isFresh = isFresh,
                )
            )
            totalCases += actualCasePacks
            baseCost += dbItem.getCasePackCostAsMoney() * actualCasePacks
            totalItemsAdded += itemsToAdd
        }
        if (lines.isEmpty()) return BuyResult(state, emptyList())

        val repBonus = state.reputationState.supplierDiscountBonus.toDouble()
        val totalDiscount = (discountFor(totalCases) + repBonus).coerceAtMost(0.99)
        val finalCost = Money((baseCost.cents * (1.0 - totalDiscount)).toLong())
        if (state.money < finalCost) return BuyResult(state, emptyList())

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

    data class OrderResult(val state: GameState, val orderArrivalDay: Int? = null)

    fun scheduleAndEmitOrder(result: BuyResult): OrderResult {
        val tm = truckManager ?: return OrderResult(result.state)
        var s = result.state
        var arrivalDay: Int? = null
        if (result.orderLines.isNotEmpty()) {
            val currentDay = s.currentTime.dayNumber
            val fresh = result.orderLines.filter { it.isFresh }
            val regular = result.orderLines.filter { !it.isFresh }
            if (regular.isNotEmpty()) s = tm.scheduleRegularOrderLines(s, regular, currentDay)
            if (fresh.isNotEmpty()) s = tm.scheduleFreshOrderLines(s, fresh, currentDay)
            arrivalDay = s.scheduledTrucks
                .filter { t -> result.orderLines.any { l -> t.orders.any { o -> o.itemId == l.itemId } } }
                .minOfOrNull { it.scheduledArrivalDay } ?: (currentDay + 1)
        }
        return OrderResult(s, arrivalDay)
    }

    fun orderIncompleteItem(state: GameState, itemId: Int, casePacksRequested: Int): OrderResult =
        fulfillIncompleteOrder(state, itemId, casePacksRequested, isFresh = true)

    fun orderIncompleteNormalItem(state: GameState, itemId: Int, casePacksRequested: Int): OrderResult =
        fulfillIncompleteOrder(state, itemId, casePacksRequested, isFresh = false)

    private fun fulfillIncompleteOrder(
        state: GameState, itemId: Int, casePacksRequested: Int, isFresh: Boolean,
    ): OrderResult {
        val tm = truckManager ?: return OrderResult(state)
        val item = cache.getItem(itemId) ?: return OrderResult(state)
        val totalCost = item.getCasePackCostAsMoney() * casePacksRequested
        if (state.money < totalCost) return OrderResult(state)

        val result = buyItemCasePacks(state, itemId, casePacksRequested)
        var s = result.state
        var arrivalDay: Int? = null
        if (result.orderLines.isNotEmpty()) {
            val currentDay = s.currentTime.dayNumber
            if (isFresh) {
                s = tm.scheduleFreshOrderLines(s, result.orderLines, currentDay)
                arrivalDay = currentDay + 1
            } else {
                s = tm.scheduleRegularOrderLines(s, result.orderLines, currentDay)
                arrivalDay = s.scheduledTrucks
                    .filter { t -> result.orderLines.any { l -> t.orders.any { o -> o.itemId == l.itemId } } }
                    .minOfOrNull { it.scheduledArrivalDay } ?: (currentDay + 1)
            }
        }
        s = if (isFresh) {
            s.copy(incompleteFreshOrders = s.incompleteFreshOrders.filter { it.itemId != itemId })
        } else {
            s.copy(incompleteNormalOrders = s.incompleteNormalOrders.filter { it.itemId != itemId })
        }
        return OrderResult(s, arrivalDay)
    }

    // ── Normal item auto-ordering operations (Stocking Manager) ────────────

    fun shouldAutoOrderNormalItem(
        state: GameState,
        itemId: Int,
        config: NormalAutoOrderConfig
    ): Boolean {
        if (!config.enabled) return false
        if (isFreshItem(itemId)) return false
        val meta = cache.get(itemId) ?: return false
        if (meta.isVendorItem) return false
        if (meta.researchGate != null && meta.researchGate !in state.researchState.researchedUpgrades) return false
        val inv = state.inventory[itemId] ?: return false
        val totalStock = inv.shelfStock + inv.backroomStock
        return totalStock < config.minStockThreshold
    }

    /**
     * Total units of [itemId] sold over the last 7 completed days (or fewer if
     * not enough history exists yet). Used by the stocking manager to size orders
     * based on actual demand rather than a fixed case-pack count.
     */
    private fun weeklySalesUnits(state: GameState, itemId: Int): Int {
        val history = state.completedDayMetrics.takeLast(7)
        if (history.isEmpty()) return 0
        return history.sumOf { day ->
            day.soldItemEvents.filter { it.itemId == itemId }.sumOf { it.quantitySold }
        }
    }

    /**
     * Compute how many case packs to order for [itemId] based on weekly sales,
     * capped at the backroom capacity. Returns at least 1 when weekly sales > 0.
     */
    private fun casePacksForWeeklySales(state: GameState, itemId: Int): Int {
        val weeklyUnits = weeklySalesUnits(state, itemId)
        if (weeklyUnits <= 0) return 0
        val casePack = cache.getItem(itemId)?.casePack ?: return 0
        val needed = (weeklyUnits + casePack - 1) / casePack // ceil division
        return minOf(needed, state.storeConfig.backroomCapPerItem)
    }

    data class AutoOrderResult(val state: GameState, val itemsOrdered: Int)

    // Sum of in-transit case-packs per item across all scheduled trucks. Threaded into
    // buyItemCasePacks (via precomputedPendingCasePacks) and the bulk-order path so the
    // committed-stock cap counts in-transit cases and a re-order can't blow past
    // backroomCapPerItem while deliveries are pending.
    private fun buildPendingCasePacksMap(state: GameState): MutableMap<Int, Int> {
        val map = mutableMapOf<Int, Int>()
        for (truck in state.scheduledTrucks) {
            for (line in truck.orders) {
                map[line.itemId] = (map[line.itemId] ?: 0) + line.casePacksCount
            }
        }
        return map
    }

    private fun processAutoOrderItem(
        s: GameState,
        tm: TruckManager,
        itemId: Int,
        casePacks: Int,
        isFresh: Boolean,
        pendingCasePacksByItem: MutableMap<Int, Int>,
    ): GameState {
        val item = cache.getItem(itemId) ?: return s
        val totalCost = item.getCasePackCostAsMoney() * casePacks

        if (s.money >= totalCost) {
            val result = buyItemCasePacks(s, itemId, casePacks, pendingCasePacksByItem)
            var updated = result.state
            if (result.orderLines.isNotEmpty()) {
                val day = updated.currentTime.dayNumber
                updated = if (isFresh) tm.scheduleFreshOrderLines(updated, result.orderLines, day)
                else tm.scheduleRegularOrderLines(updated, result.orderLines, day)
            }
            val lineItem = AutoOrderLineItem(
                itemId = itemId,
                casePacksOrdered = casePacks,
                costPerCasePack = item.getCasePackCostAsMoney(), totalCost = totalCost,
            )
            val m = updated.currentDayMetrics
            return updated.copy(
                currentDayMetrics = if (isFresh)
                    m.copy(autoOrderedFreshItems = m.autoOrderedFreshItems + lineItem)
                else
                    m.copy(autoOrderedNormalItems = m.autoOrderedNormalItems + lineItem)
            )
        } else {
            val incomplete = IncompleteAutoOrderLineItem(
                itemId = itemId,
                casePacksRequested = casePacks,
                costPerCasePack = item.getCasePackCostAsMoney(), totalCost = totalCost,
                reason = "Insufficient funds",
            )
            val request = IncompleteOrderRequest(
                itemId = itemId, casePacksRequested = casePacks,
                requestedOnDay = s.currentTime.dayNumber, reason = "Insufficient funds",
            )
            val m = s.currentDayMetrics
            return if (isFresh) {
                s.copy(
                    currentDayMetrics = m.copy(incompleteOrderedFreshItems = m.incompleteOrderedFreshItems + incomplete),
                    incompleteFreshOrders = s.incompleteFreshOrders.filter { it.itemId != itemId } + request,
                )
            } else {
                s.copy(
                    currentDayMetrics = m.copy(incompleteOrderedNormalItems = m.incompleteOrderedNormalItems + incomplete),
                    incompleteNormalOrders = s.incompleteNormalOrders.filter { it.itemId != itemId } + request,
                )
            }
        }
    }

    fun attemptStockingManagerAutoOrder(state: GameState, maxActions: Int): AutoOrderResult {
        val tm = truckManager ?: return AutoOrderResult(state, 0)
        if (!state.normalAutoOrderConfig.enabled) return AutoOrderResult(state, 0)

        val alreadyHandled = (state.currentDayMetrics.autoOrderedNormalItems.map { it.itemId } +
            state.currentDayMetrics.incompleteOrderedNormalItems.map { it.itemId }).toSet()
        val pending = buildPendingCasePacksMap(state)

        var s = state
        var actionsUsed = 0
        for ((itemId, _) in s.inventory) {
            if (actionsUsed >= maxActions) break
            if (itemId in alreadyHandled) continue
            if (!shouldAutoOrderNormalItem(s, itemId, s.normalAutoOrderConfig)) continue
            val casePacks = casePacksForWeeklySales(s, itemId)
            if (casePacks <= 0) continue
            s = processAutoOrderItem(s, tm, itemId, casePacks, false, pending)
            actionsUsed++
        }
        return AutoOrderResult(s, actionsUsed)
    }

    fun attemptDayRolloverAutoOrder(state: GameState): GameState {
        val tm = truckManager ?: return state
        if (!state.normalAutoOrderConfig.enabled) return state

        val alreadyHandled = (state.currentDayMetrics.autoOrderedNormalItems.map { it.itemId } +
            state.currentDayMetrics.incompleteOrderedNormalItems.map { it.itemId }).toSet()
        val pending = buildPendingCasePacksMap(state)

        var s = state
        for ((itemId, inv) in s.inventory) {
            if (itemId in alreadyHandled) continue
            if (isFreshItem(itemId)) continue
            val meta = cache.get(itemId) ?: continue
            if (meta.isVendorItem) continue
            if (meta.researchGate != null && meta.researchGate !in s.researchState.researchedUpgrades) continue
            if (inv.shelfStock + inv.backroomStock > 0) continue
            if ((pending[itemId] ?: 0) > 0) continue
            val casePacks = casePacksForWeeklySales(s, itemId).coerceAtLeast(1)
            s = processAutoOrderItem(s, tm, itemId, casePacks, false, pending)
        }
        return s
    }

    fun attemptFreshHandlerAutoOrder(state: GameState): GameState {
        val tm = truckManager ?: return state
        if (!state.freshAutoOrderConfig.enabled) return state

        val alreadyHandled = (state.currentDayMetrics.autoOrderedFreshItems.map { it.itemId } +
            state.currentDayMetrics.incompleteOrderedFreshItems.map { it.itemId }).toSet()
        val pending = buildPendingCasePacksMap(state)

        var s = state
        for ((itemId, _) in s.inventory) {
            if (itemId in alreadyHandled) continue
            if (!shouldAutoOrderFreshItem(s, itemId, s.freshAutoOrderConfig)) continue
            s = processAutoOrderItem(s, tm, itemId, s.freshAutoOrderConfig.casePacksPerItem, true, pending)
        }
        return s
    }
}
