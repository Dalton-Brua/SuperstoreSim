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
        if (metadata.isVendorItem) return BuyResult(state, emptyList())

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
     * Order [numCasePacks] case-packs of [itemId], capped at [backroomCapPerItem].
     * Deducts money immediately. Does NOT add to backroom — overflow is handled at delivery time.
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

        val capInCasePacks = state.storeConfig.backroomCapPerItem
        val actualCasePacks = minOf(numCasePacks, capInCasePacks)
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
     *  1. It is accessible (research gate unlocked or no gate)
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

        val researchedUpgrades = state.researchState.researchedUpgrades
        val matchingEntries = state.inventory.filter { (itemId, inv) ->
            val meta = cache.get(itemId) ?: return@filter false
            val accessible = meta.researchGate == null || meta.researchGate in researchedUpgrades
            val categoryOk = categoryFilter == null || meta.category == categoryFilter
            val qtyOk = inv.shelfStock + inv.backroomStock <= maxTotalQuantity
            val notFresh = !isFreshItem(itemId)
            val notVendor = !meta.isVendorItem
            accessible && categoryOk && qtyOk && notFresh && notVendor
        }
        if (matchingEntries.isEmpty()) return BuyResult(state, emptyList())

        val itemsToAddMap = mutableMapOf<Int, OrderInfo>()
        var totalCases = 0
        var baseCost = Money.ZERO

        matchingEntries.forEach { (itemId, _) ->
            val dbItem = cache.getItem(itemId) ?: return@forEach
            val actualCasePacks = minOf(casePacksPerItem, capInCasePacks)
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

    private data class OrderInfo(
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
    ): BuyResult {
        val currentDay = state.currentTime.dayNumber
        val researchedUpgrades = state.researchState.researchedUpgrades
        val itemsToAddMap = mutableMapOf<Int, OrderInfo>()
        var totalCases = 0
        var baseCost = Money(0)

        for ((itemId, inv) in state.inventory) {
            val dbItem = cache.getItem(itemId) ?: continue
            val metadata = cache.get(itemId) ?: continue

            if (!isFreshItem(itemId)) continue
            if (metadata.researchGate != null && metadata.researchGate !in researchedUpgrades) continue

            val currentTotal = inv.shelfStock + inv.backroomStock
            if (currentTotal >= maxTotalQuantity) continue

            val actualCasePacks = casePacksPerItem
            itemsToAddMap[itemId] = OrderInfo(
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

    data class AutoOrderResult(val state: GameState, val itemsOrdered: Int)

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
                itemId = itemId, itemName = item.name,
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
                itemId = itemId, itemName = item.name,
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
            s = processAutoOrderItem(s, tm, itemId, s.normalAutoOrderConfig.casePacksPerItem, false, pending)
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
            s = processAutoOrderItem(s, tm, itemId, 1, false, pending)
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
