package com.example.superstoresimulator.domain.delivery

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.PendingOrderLine
import com.example.superstoresimulator.domain.ScheduledTruck
import com.example.superstoresimulator.domain.TRUCK_CAPACITY_TIERS
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.AutoHireAction
import com.example.superstoresimulator.domain.metrics.AutoHireEvent
import com.example.superstoresimulator.domain.metrics.DeliveredItemLine
import com.example.superstoresimulator.domain.metrics.DeliveredTruckRecord
import com.example.superstoresimulator.domain.research.ResearchGates
import com.example.superstoresimulator.domain.time.GameTime

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
@javax.inject.Singleton
class TruckManager @javax.inject.Inject constructor(private val cache: ItemMetadataCache) {

    companion object {
        const val OOS_BUY_SLOT_THRESHOLD = 10
    }

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
                    capacityCasePacks = state.truckConfig.effectiveRegularCapacity,
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
        val freshCap = state.truckConfig.effectiveFreshCapacity

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
        val remainingTrucks = state.scheduledTrucks.filter { it.scheduledArrivalDay > currentDay }.toMutableList()
        val overflowLines = mutableListOf<PendingOrderLine>()
        val capInCasePacks = state.storeConfig.backroomCapPerItem

        for (truck in arriving) {
            val deliveredLines = mutableListOf<DeliveredItemLine>()

            for (line in truck.orders) {
                val inv = newInventory[line.itemId] ?: InventoryState()
                val meta = cache.getItem(line.itemId)
                val casePack = meta?.casePack ?: 1
                val currentBackroomCasePacks = inv.backroomStock / casePack
                val roomLeft = (capInCasePacks - currentBackroomCasePacks).coerceAtLeast(0)
                val deliverableCasePacks = minOf(line.casePacksCount, roomLeft)
                val deferredCasePacks = line.casePacksCount - deliverableCasePacks

                if (deliverableCasePacks > 0) {
                    val deliverableQuantity = casePack * deliverableCasePacks
                    val expirationDay = if (meta?.shelfLifeDays != null) {
                        truck.scheduledArrivalDay + meta.shelfLifeDays
                    } else {
                        Int.MAX_VALUE
                    }
                    val batch = ItemBatch(
                        receivedDay = truck.scheduledArrivalDay,
                        quantity = deliverableQuantity,
                        expirationDay = expirationDay,
                    )
                    val updatedBackroom = inv.mergeBatches(inv.backroomBatches + batch)
                    newInventory = newInventory + (line.itemId to inv.copy(backroomBatches = updatedBackroom))
                }

                if (deferredCasePacks > 0) {
                    overflowLines.add(
                        line.copy(
                            casePacksCount = deferredCasePacks,
                            quantity = casePack * deferredCasePacks,
                        )
                    )
                }

                deliveredLines.add(
                    DeliveredItemLine(
                        itemId = line.itemId,
                        casePacks = deliverableCasePacks,
                        quantity = casePack * deliverableCasePacks,
                        deferredCasePacks = deferredCasePacks,
                        deferredQuantity = casePack * deferredCasePacks,
                    )
                )
            }

            if (truck.orders.isNotEmpty()) {
                val record = DeliveredTruckRecord(
                    truckId = truck.truckId,
                    arrivalDay = truck.scheduledArrivalDay,
                    isFreshTruck = truck.isFreshTruck,
                    isEarlyTruck = truck.isEarlyTruck,
                    totalCasePacks = deliveredLines.sumOf { it.casePacks },
                    lines = deliveredLines,
                )
                newMetrics = newMetrics.copy(deliveredTrucks = newMetrics.deliveredTrucks + record)
            }
        }

        var resultState = state.copy(
            inventory = newInventory,
            scheduledTrucks = remainingTrucks,
            currentDayMetrics = newMetrics,
        )

        if (overflowLines.isNotEmpty()) {
            val freshOverflow = overflowLines.filter { it.isFresh }
            val regularOverflow = overflowLines.filter { !it.isFresh }
            if (regularOverflow.isNotEmpty()) {
                resultState = scheduleRegularOrderLines(resultState, regularOverflow, currentDay)
            }
            if (freshOverflow.isNotEmpty()) {
                resultState = scheduleFreshOrderLines(resultState, freshOverflow, currentDay)
            }
        }

        return resultState
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
                truckCapacityTier = state.truckConfig.truckCapacityTier,  // never changed here
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
        if (maxDeliveryDaysAllowed(state) >= 7) return state
        val cost = TruckConfig.EXTRA_SLOT_COST
        if (state.money < cost) return state
        return state.copy(
            money = state.money - cost,
            truckConfig = state.truckConfig.copy(
                extraTruckSlotsUnlocked = state.truckConfig.extraTruckSlotsUnlocked + 1,
            )
        )
    }

    /**
     * Upgrade the truck fleet to the next capacity tier.
     *
     * Tier 0 → 1 (Enhanced Fleet): $100K, requires "truck_upgrade_enhanced" research.
     * Tier 1 → 2 (Heavy Fleet):    $200K, requires "truck_upgrade_heavy" research.
     *
     * Already-scheduled trucks keep their original capacity.
     */
    fun purchaseTruckUpgrade(state: GameState): GameState {
        val currentTier = state.truckConfig.truckCapacityTier
        val nextTierIndex = currentTier + 1
        val nextTier = TRUCK_CAPACITY_TIERS.getOrNull(nextTierIndex) ?: return state
        val cost = nextTier.upgradeCost ?: return state

        val requiredResearch = when (nextTierIndex) {
            1 -> ResearchGates.TRUCK_UPGRADE_ENHANCED
            2 -> ResearchGates.TRUCK_UPGRADE_HEAVY
            else -> return state
        }
        if (!ResearchGates.isResearched(state.researchState.researchedUpgrades, requiredResearch)) return state
        if (state.money < cost) return state

        return state.copy(
            money = state.money - cost,
            truckConfig = state.truckConfig.copy(truckCapacityTier = nextTierIndex),
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
                capacityCasePacks = state.truckConfig.effectiveRegularCapacity,
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

    // ── Store Manager Auto-Actions ───────────────────────────────────────────

    /**
     * Autonomous truck management decisions made by the Store Manager at day rollover.
     *
     * Actions (in order):
     *  1. **Fill free delivery-day slots** — when the store size grants more slots than are
     *     currently configured (e.g. after an upgrade), assign days that best fill gaps in
     *     the existing schedule. Free — no monetary cost.
     *  2. **Early truck** — if OOS percentage exceeds the configured threshold and the
     *     next regular truck is more than 2 days away, request an early truck for tomorrow.
     *  3. **Purchase extra slot + add day** — if ≥ [OOS_BUY_SLOT_THRESHOLD] unique items
     *     went OOS and all delivery-day slots are already used, buy one more slot and assign
     *     the best gap-filling day.
     *
     * Monetary actions respect [GameState.autoHireBudget] — money will not drop below that floor.
     */
    fun evaluateStoreManagerTruckActions(state: GameState, currentDay: Int): GameState {
        val hasStoreManager = state.hiredEntityRegistry.getByDef(EntityDef.MANAGER)
            .any { it.isStoreManager }
        if (!hasStoreManager) return state

        val config = state.storeManagerConfig
        var result = state
        val oosCount = state.currentDayMetrics.outOfStockEvents.size
        val slotsWereMaxedBeforeFill = state.truckConfig.deliveryDays.size >= maxDeliveryDaysAllowed(state)
        val events = mutableListOf<AutoHireEvent>()

        // 1. Fill any unused delivery-day slots (free — happens after store upgrade)
        if (config.autoFillDeliverySlots) {
            val maxDays = maxDeliveryDaysAllowed(result)
            while (result.truckConfig.deliveryDays.size < maxDays) {
                val bestDay = findBestGapDay(result.truckConfig.deliveryDays) ?: break
                val newDays = result.truckConfig.deliveryDays + bestDay
                result = updateConfig(result, result.truckConfig.copy(deliveryDays = newDays))
                events += AutoHireEvent(
                    "Truck", "Delivery day added",
                    detail = "Store Manager added ${GameTime.shortDayName(bestDay)} delivery — filling available slot",
                    action = AutoHireAction.PURCHASED,
                )
            }
        }

        // 2. Request early truck when OOS % exceeds threshold and next truck is far away
        val totalItems = result.inventory.size.coerceAtLeast(1)
        val oosPercent = (oosCount * 100) / totalItems
        if (config.autoEarlyTruckEnabled && oosPercent >= config.earlyTruckOosPercent) {
            val nextScheduledTruck = result.scheduledTrucks
                .filter { !it.isFreshTruck && !it.isEarlyTruck && it.scheduledArrivalDay > currentDay }
                .minByOrNull { it.scheduledArrivalDay }
            val nextConfiguredDay = findNextDeliveryDay(currentDay, result.truckConfig.deliveryDays)
            val nextArrivalDay = if (nextScheduledTruck != null)
                minOf(nextScheduledTruck.scheduledArrivalDay, nextConfiguredDay) else nextConfiguredDay
            val daysUntilNext = nextArrivalDay - currentDay

            if (daysUntilNext > 2) {
                val cost = Money(10_000L)
                val alreadyExists = result.scheduledTrucks.any {
                    it.isEarlyTruck && it.scheduledArrivalDay == currentDay + 1
                }
                if (!alreadyExists && result.money - cost >= result.autoHireBudget) {
                    val before = result.money
                    result = requestEarlyTruck(result, currentDay)
                    if (result.money != before) {
                        events += AutoHireEvent(
                            "Truck", "Early truck ordered",
                            detail = "Store Manager ordered early truck — $oosPercent% items OOS ($oosCount/$totalItems), next truck in $daysUntilNext days",
                            action = AutoHireAction.PURCHASED,
                        )
                    }
                }
            }
        }

        // 3. Purchase an extra truck slot and add a day if slots were already maxed before fill
        // Manager limited to 1 extra purchased slot; player can buy more manually
        if (config.autoBuyTruckSlotEnabled && oosCount >= config.buySlotOosThreshold && slotsWereMaxedBeforeFill &&
            result.truckConfig.extraTruckSlotsUnlocked < 1 &&
            result.truckConfig.deliveryDays.size < 7
        ) {
            val cost = TruckConfig.EXTRA_SLOT_COST
            if (result.money - cost >= result.autoHireBudget) {
                val slotsBefore = result.truckConfig.extraTruckSlotsUnlocked
                result = purchaseExtraTruckSlot(result)
                if (result.truckConfig.extraTruckSlotsUnlocked > slotsBefore) {
                    val bestDay = findBestGapDay(result.truckConfig.deliveryDays)
                    if (bestDay != null) {
                        val newDays = result.truckConfig.deliveryDays + bestDay
                        result = updateConfig(result, result.truckConfig.copy(deliveryDays = newDays))
                    }
                    events += AutoHireEvent(
                        "Truck", "Extra slot purchased",
                        detail = "Store Manager bought extra truck slot${if (bestDay != null) " and added ${GameTime.shortDayName(bestDay!!)}" else ""} — $oosCount items OOS",
                        action = AutoHireAction.PURCHASED,
                    )
                }
            }
        }

        if (events.isNotEmpty()) {
            result = result.copy(
                currentDayMetrics = result.currentDayMetrics.copy(
                    autoHireEvents = result.currentDayMetrics.autoHireEvents + events,
                ),
            )
        }

        return result
    }

    /**
     * Find the day-of-week (0–6) to add as a delivery day.
     * Prioritizes weekends (Sat=5, Sun=6) when none exist, then falls back
     * to maximizing the minimum circular distance to existing days.
     */
    internal fun findBestGapDay(existingDays: Set<Int>): Int? {
        val available = (0..6).filter { it !in existingDays }
        if (available.isEmpty()) return null

        val hasWeekend = existingDays.any { it == 5 || it == 6 }
        if (!hasWeekend) {
            val bestWeekend = available.filter { it == 5 || it == 6 }
                .maxByOrNull { candidate ->
                    existingDays.minOf { existing ->
                        val dist = (candidate - existing + 7) % 7
                        minOf(dist, 7 - dist)
                    }
                }
            if (bestWeekend != null) return bestWeekend
        }

        return available.maxByOrNull { candidate ->
            existingDays.minOf { existing ->
                val dist = (candidate - existing + 7) % 7
                minOf(dist, 7 - dist)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fill [truck] with as many lines as truck capacity allows.
     * Overflow lines are returned as leftover so the caller can place them on a
     * subsequent truck. Per-item ordering caps are enforced upstream in InventoryManager.
     */
    private fun fillTruck(
        truck: ScheduledTruck,
        lines: List<PendingOrderLine>,
    ): Pair<ScheduledTruck, List<PendingOrderLine>> {
        var remaining = truck.remainingCapacityCasePacks
        val placed = mutableListOf<PendingOrderLine>()
        val leftover = mutableListOf<PendingOrderLine>()

        for (line in lines) {
            if (line.casePacksCount <= remaining) {
                placed.add(line)
                remaining -= line.casePacksCount
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

