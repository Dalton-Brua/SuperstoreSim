package com.example.superstoresimulator.domain.delivery

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.PendingOrderLine
import com.example.superstoresimulator.domain.ScheduledTruck
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.DeliveredItemLine
import com.example.superstoresimulator.domain.metrics.DeliveredTruckRecord

/**
 * Pure manager for the truck-based delivery system.
 *
 * All public methods receive a [GameState] and return a new [GameState].
 * No mutable state is stored here — only [ItemMetadataCache] for item lookups.
 *
 * Responsibilities:
 *  - Schedule regular order lines onto the next available regular truck
 *  - Schedule fresh order lines onto the daily fresh truck (arrives currentDay + 1)
 *  - Process truck arrivals at the start of each new game day
 *  - Update truck configuration (delivery days, capacities)
 *  - Cancel pending order lines and refund costs
 *  - Book an on-demand early truck for $100
 */
class TruckManager(private val cache: ItemMetadataCache) {

    // ── Scheduling ────────────────────────────────────────────────────────────

    /**
     * Assign [lines] to the next regular truck with remaining capacity.
     * If no suitable truck exists, one is created on the next configured delivery day.
     * If lines overflow the first truck, the overflow is placed on a subsequent truck.
     */
    fun scheduleRegularOrderLines(
        state: GameState,
        lines: List<PendingOrderLine>,
        currentDay: Int,
    ): GameState {
        if (lines.isEmpty()) return state
        return assignLinesToNextRegularTruck(state, lines, currentDay)
    }

    private fun assignLinesToNextRegularTruck(
        state: GameState,
        lines: List<PendingOrderLine>,
        currentDay: Int,
    ): GameState {
        val trucks = state.scheduledTrucks.toMutableList()
        var nextId = state.nextTruckId
        var remainingLines = lines.toMutableList()

        // Look for early truck first (if one exists for tomorrow)
        val earlyTruck = trucks.find {
            it.isEarlyTruck && it.scheduledArrivalDay == currentDay + 1
        }
        if (earlyTruck != null) {
            val (updatedEarly, leftover) = fillTruck(earlyTruck, remainingLines)
            val idx = trucks.indexOf(earlyTruck)
            trucks[idx] = updatedEarly
            if (leftover.isEmpty()) {
                return state.copy(scheduledTrucks = trucks, nextTruckId = nextId)
            }
            remainingLines = leftover.toMutableList()
        }

        // Place remaining lines on regular trucks, creating new trucks as needed.
        // Splitting is based purely on truck capacity — the per-item cap enforcement
        // happens at order time in InventoryManager, not here.
        while (remainingLines.isNotEmpty()) {
            val candidate = trucks
                .filter { !it.isFreshTruck && !it.isEarlyTruck }
                .firstOrNull { it.remainingCapacityCasePacks > 0 }

            if (candidate != null) {
                val (updated, leftover) = fillTruck(candidate, remainingLines)
                val idx = trucks.indexOf(candidate)
                trucks[idx] = updated
                when {
                    leftover.isEmpty() -> break
                    leftover.size < remainingLines.size -> {
                        // Progress made — continue with the remaining lines
                        remainingLines = leftover.toMutableList()
                    }
                    else -> {
                        // No progress despite candidate having capacity: every remaining line
                        // individually exceeds the truck's remaining space. Safety break to
                        // prevent an infinite loop; these lines cannot be placed.
                        break
                    }
                }
            } else {
                // No eligible truck with capacity — create a new regular truck on the next delivery day.
                val lastRegularDay = trucks
                    .filter { !it.isFreshTruck && !it.isEarlyTruck }
                    .maxOfOrNull { it.scheduledArrivalDay }
                val baseDay = if (lastRegularDay != null && lastRegularDay > currentDay) lastRegularDay else currentDay
                val arrivalDay = findNextDeliveryDay(baseDay, state.truckConfig.deliveryDays)
                val newTruck = ScheduledTruck(
                    truckId = nextId++,
                    scheduledArrivalDay = arrivalDay,
                    capacityCasePacks = state.truckConfig.regularTruckCapacityCasePacks,
                )
                trucks.add(newTruck)
            }
        }

        return state.copy(scheduledTrucks = trucks, nextTruckId = nextId)
    }

    /**
     * Assign [lines] to the daily fresh truck arriving at [currentDay] + 1.
     * Creates the truck if it does not already exist for that day.
     */
    fun scheduleFreshOrderLines(
        state: GameState,
        lines: List<PendingOrderLine>,
        currentDay: Int,
    ): GameState {
        if (lines.isEmpty()) return state
        val arrivalDay = currentDay + 1
        val trucks = state.scheduledTrucks.toMutableList()
        var nextId = state.nextTruckId

        val existing = trucks.find { it.isFreshTruck && it.scheduledArrivalDay == arrivalDay }
        val freshCap = state.truckConfig.freshTruckCapacityCasePacks

        if (existing != null) {
            val (updated, _) = fillTruck(existing, lines)
            val idx = trucks.indexOf(existing)
            trucks[idx] = updated
        } else {
            val newTruck = ScheduledTruck(
                truckId = nextId++,
                scheduledArrivalDay = arrivalDay,
                capacityCasePacks = freshCap,
                isFreshTruck = true,
            )
            val (filled, _) = fillTruck(newTruck, lines)
            trucks.add(filled)
        }

        return state.copy(scheduledTrucks = trucks, nextTruckId = nextId)
    }

    // ── Arrivals ──────────────────────────────────────────────────────────────

    /**
     * Process all trucks whose [ScheduledTruck.scheduledArrivalDay] ≤ [currentDay].
     * For each arriving truck, delivers items as [ItemBatch]s to the backroom and
     * appends a [DeliveredTruckRecord] to [GameState.currentDayMetrics].
     * Delivered trucks are removed from [GameState.scheduledTrucks].
     */
    fun processArrivals(state: GameState, currentDay: Int): GameState {
        val arriving = state.scheduledTrucks.filter { it.scheduledArrivalDay <= currentDay }
        if (arriving.isEmpty()) return state

        var newInventory = state.inventory
        var newMetrics = state.currentDayMetrics
        val remainingTrucks = state.scheduledTrucks.filter { it.scheduledArrivalDay > currentDay }

        for (truck in arriving) {
            val deliveredLines = mutableListOf<DeliveredItemLine>()

            for (line in truck.orders) {
                val inv = newInventory[line.itemId] ?: InventoryState()
                val meta = cache.getItem(line.itemId)
                val expirationDay = if (meta?.shelfLifeDays != null) {
                    truck.scheduledArrivalDay + meta.shelfLifeDays
                } else {
                    Int.MAX_VALUE
                }
                val batch = ItemBatch(
                    receivedDay = truck.scheduledArrivalDay,
                    quantity = line.quantity,
                    expirationDay = expirationDay,
                )
                val updatedBackroom = inv.mergeBatches(inv.backroomBatches + batch)
                newInventory = newInventory + (line.itemId to inv.copy(backroomBatches = updatedBackroom))

                val itemName = cache.get(line.itemId)?.name ?: "Item ${line.itemId}"
                deliveredLines.add(
                    DeliveredItemLine(
                        itemId = line.itemId,
                        itemName = itemName,
                        casePacks = line.casePacksCount,
                        quantity = line.quantity,
                    )
                )
            }

            if (truck.orders.isNotEmpty()) {
                val record = DeliveredTruckRecord(
                    truckId = truck.truckId,
                    arrivalDay = truck.scheduledArrivalDay,
                    isFreshTruck = truck.isFreshTruck,
                    isEarlyTruck = truck.isEarlyTruck,
                    totalCasePacks = truck.orders.sumOf { it.casePacksCount },
                    lines = deliveredLines,
                )
                newMetrics = newMetrics.copy(deliveredTrucks = newMetrics.deliveredTrucks + record)
            }
        }

        return state.copy(
            inventory = newInventory,
            scheduledTrucks = remainingTrucks,
            currentDayMetrics = newMetrics,
        )
    }

    // ── Configuration ─────────────────────────────────────────────────────────

    /**
     * Return the maximum number of weekly delivery days allowed for [state].
     * Formula: [TruckConfig.BASE_FREE_SLOTS] + storeSize.ordinal + extraSlotsUnlocked.
     */
    fun maxDeliveryDaysAllowed(state: GameState): Int =
        TruckConfig.BASE_FREE_SLOTS +
            state.currentStoreSize.ordinal +
            state.truckConfig.extraTruckSlotsUnlocked

    /**
     * Update truck configuration. Already-scheduled trucks are untouched.
     * No-op if [newConfig] would leave [TruckConfig.deliveryDays] empty.
     * Silently clamps [newConfig.deliveryDays] to [maxDeliveryDaysAllowed] if exceeded.
     * Always preserves [TruckConfig.extraTruckSlotsUnlocked] from [state].
     */
    fun updateConfig(state: GameState, newConfig: TruckConfig): GameState {
        if (newConfig.deliveryDays.isEmpty()) return state
        val existingSlots = state.truckConfig.extraTruckSlotsUnlocked
        val maxAllowed = TruckConfig.BASE_FREE_SLOTS + state.currentStoreSize.ordinal + existingSlots
        // Clamp delivery days to the allowed maximum (preserve sorted/insertion order)
        val effectiveDays = newConfig.deliveryDays.take(maxAllowed).toSet()
        if (effectiveDays.isEmpty()) return state
        return state.copy(
            truckConfig = newConfig.copy(
                deliveryDays = effectiveDays,
                extraTruckSlotsUnlocked = existingSlots,  // never changed here
            )
        )
    }

    /**
     * Unlock one extra weekly delivery-day slot for [TruckConfig.EXTRA_SLOT_COST].
     *
     * Guard: player must have ≥ [TruckConfig.EXTRA_SLOT_COST].
     * The slot is not automatically tied to a day — the player must then set the desired
     * delivery day via [updateConfig].
     */
    fun purchaseExtraTruckSlot(state: GameState): GameState {
        val cost = TruckConfig.EXTRA_SLOT_COST
        if (state.money < cost) return state
        return state.copy(
            money = state.money - cost,
            truckConfig = state.truckConfig.copy(
                extraTruckSlotsUnlocked = state.truckConfig.extraTruckSlotsUnlocked + 1,
            )
        )
    }

    // ── Cancellation ──────────────────────────────────────────────────────────

    /**
     * Cancel the order line for [itemId] on truck [truckId].
     *
     * Guard: truck must have [ScheduledTruck.scheduledArrivalDay] > [currentDay].
     * Refunds `unitCost × quantity` to [GameState.money].
     * Removes the truck from [GameState.scheduledTrucks] if it becomes empty.
     */
    fun cancelPendingOrderLine(
        state: GameState,
        itemId: Int,
        truckId: Int,
        currentDay: Int,
    ): GameState {
        val truck = state.scheduledTrucks.find { it.truckId == truckId } ?: return state
        if (truck.scheduledArrivalDay <= currentDay) return state // already arrived

        // Collect ALL lines for this item (UI shows them merged; cancel all of them)
        val linesToCancel = truck.orders.filter { it.itemId == itemId }
        if (linesToCancel.isEmpty()) return state
        val refund = linesToCancel.fold(com.example.superstoresimulator.domain.Money.ZERO) { acc, line ->
            acc + line.unitCost * line.quantity
        }

        val updatedOrders = truck.orders.filter { it.itemId != itemId }
        val updatedTruck = truck.copy(orders = updatedOrders)

        val updatedTrucks = if (updatedOrders.isEmpty()) {
            state.scheduledTrucks.filter { it.truckId != truckId }
        } else {
            state.scheduledTrucks.map { if (it.truckId == truckId) updatedTruck else it }
        }

        return state.copy(
            scheduledTrucks = updatedTrucks,
            money = state.money + refund,
        )
    }

    /**
     * Removes exactly one case pack for [itemId] from the truck with [truckId].
     * Refunds `unitCost × unitsPerCasePack` to [GameState.money].
     * Guard: truck must have [ScheduledTruck.scheduledArrivalDay] > [currentDay].
     * Guard: total case packs for that item must be > 1 (use [cancelPendingOrderLine] for the last pack).
     */
    fun decrementOrderLine(
        state: GameState,
        itemId: Int,
        truckId: Int,
        currentDay: Int,
    ): GameState {
        val truck = state.scheduledTrucks.find { it.truckId == truckId } ?: return state
        if (truck.scheduledArrivalDay <= currentDay) return state

        val lines = truck.orders.filter { it.itemId == itemId }
        val totalCasePacks = lines.sumOf { it.casePacksCount }
        if (totalCasePacks <= 1) return state // nothing to decrement (would empty the item)

        // Take 1 case pack off the last line for this item
        val targetLine = lines.last()
        val unitsPerCasePack = if (targetLine.casePacksCount > 0) targetLine.quantity / targetLine.casePacksCount else 0
        val refund = targetLine.unitCost * unitsPerCasePack

        val updatedOrders = if (targetLine.casePacksCount == 1) {
            // This specific line drops to zero — remove it entirely
            truck.orders.filter { it !== targetLine }
        } else {
            truck.orders.map { line ->
                if (line === targetLine) line.copy(
                    casePacksCount = line.casePacksCount - 1,
                    quantity = line.quantity - unitsPerCasePack,
                ) else line
            }
        }

        val updatedTruck = truck.copy(orders = updatedOrders)
        val updatedTrucks = state.scheduledTrucks.map { if (it.truckId == truckId) updatedTruck else it }

        return state.copy(
            scheduledTrucks = updatedTrucks,
            money = state.money + refund,
        )
    }

    // ── Early Truck ───────────────────────────────────────────────────────────

    /**
     * Book an on-demand early truck arriving [currentDay] + 1 for $100.
     *
     * If a regular truck is already scheduled, its arrival day is moved to [currentDay] + 1
     * and it is flagged as the early truck — carrying all its existing orders.
     * If no regular truck exists yet, a new empty early truck is created instead.
     *
     * Guard: player must have ≥ $100.
     * Guard: only one early truck per day (no-op if one already exists for tomorrow).
     */
    fun requestEarlyTruck(state: GameState, currentDay: Int): GameState {
        val cost = Money(10_000L) // $100.00
        if (state.money < cost) return state

        val arrivalDay = currentDay + 1
        val alreadyExists = state.scheduledTrucks.any { it.isEarlyTruck && it.scheduledArrivalDay == arrivalDay }
        if (alreadyExists) return state

        // Find the next scheduled regular truck (not fresh, not already early).
        val nextRegularTruck = state.scheduledTrucks
            .filter { !it.isFreshTruck && !it.isEarlyTruck && it.scheduledArrivalDay > currentDay }
            .minByOrNull { it.scheduledArrivalDay }

        val updatedTrucks: List<ScheduledTruck>
        val nextTruckId: Int

        if (nextRegularTruck != null) {
            // Reschedule the regular truck to arrive tomorrow and mark it as the early truck.
            // All existing orders remain on the truck — they are now arriving a day early.
            val earlyTruck = nextRegularTruck.copy(
                scheduledArrivalDay = arrivalDay,
                isEarlyTruck = true,
            )
            updatedTrucks = state.scheduledTrucks.map {
                if (it.truckId == nextRegularTruck.truckId) earlyTruck else it
            }
            nextTruckId = state.nextTruckId
        } else {
            // No regular truck yet — create a new empty early truck.
            val newTruck = ScheduledTruck(
                truckId = state.nextTruckId,
                scheduledArrivalDay = arrivalDay,
                capacityCasePacks = state.truckConfig.regularTruckCapacityCasePacks,
                isEarlyTruck = true,
            )
            updatedTrucks = state.scheduledTrucks + newTruck
            nextTruckId = state.nextTruckId + 1
        }

        return state.copy(
            scheduledTrucks = updatedTrucks,
            nextTruckId = nextTruckId,
            money = state.money - cost,
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fill [truck] with as many lines as truck capacity allows.
     * When [perItemCapCasePacks] is set, each item is also limited to that many case packs
     * per truck — overflow lines are returned as leftover so the caller can place them on
     * a subsequent truck (enabling multi-truck splitting for large per-item orders).
     *
     * Fresh truck calls omit [perItemCapCasePacks] (default = no per-item limit).
     */
    private fun fillTruck(
        truck: ScheduledTruck,
        lines: List<PendingOrderLine>,
        perItemCapCasePacks: Int = Int.MAX_VALUE,
    ): Pair<ScheduledTruck, List<PendingOrderLine>> {
        var remaining = truck.remainingCapacityCasePacks
        val placed = mutableListOf<PendingOrderLine>()
        val leftover = mutableListOf<PendingOrderLine>()

        // Track per-item case packs already committed to this truck (existing + newly placed).
        val itemCapUsed = truck.orders
            .groupBy { it.itemId }
            .mapValues { (_, ls) -> ls.sumOf { it.casePacksCount } }
            .toMutableMap()

        for (line in lines) {
            val alreadyForItem = itemCapUsed.getOrDefault(line.itemId, 0)
            val fitsInTruck = line.casePacksCount <= remaining
            val fitsPerItem = alreadyForItem + line.casePacksCount <= perItemCapCasePacks
            if (fitsInTruck && fitsPerItem) {
                placed.add(line)
                remaining -= line.casePacksCount
                itemCapUsed[line.itemId] = alreadyForItem + line.casePacksCount
            } else {
                leftover.add(line)
            }
        }

        return truck.copy(orders = truck.orders + placed) to leftover
    }

    /**
     * Given [currentDay] (absolute day number), return the next absolute day whose
     * [dayOfWeek] (`day % 7`) is in [deliveryDays].
     * Always returns a day strictly after [currentDay].
     */
    internal fun findNextDeliveryDay(currentDay: Int, deliveryDays: Set<Int>): Int {
        if (deliveryDays.isEmpty()) return currentDay + 1
        var candidate = currentDay + 1
        repeat(14) { // safeguard: at most 14 iterations to find a valid day in the week
            if (candidate % 7 in deliveryDays) return candidate
            candidate++
        }
        return candidate
    }
}

