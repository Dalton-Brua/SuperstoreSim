package com.example.superstoresimulator.domain.staff

import android.util.Log
import com.example.superstoresimulator.domain.DailyStaffMetrics
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.metrics.AutoHireEvent
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.research.AnalystAssignment
import com.example.superstoresimulator.domain.research.ResearchGates
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.SimAccumulators
import com.example.superstoresimulator.domain.ZoningState
import com.example.superstoresimulator.domain.traffic.TrafficSchedule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StaffManager @Inject constructor() {

    internal val cashierProgressByRegister: MutableMap<Int, Float> = mutableMapOf()
    internal var stockerProgress: Float = 0f
    internal var stockingManagerProgress: Float = 0f
    internal var freshHandlerProgress: Float = 0f

    // Assignment queues: when an action fires, pop an entity ID to credit with XP.
    // Queues are refilled proportional to each employee's speed weight so fast
    // employees are credited more often.
    private val stockerAssignmentQueue = ArrayDeque<Int>()
    private val stockingManagerAssignmentQueue = ArrayDeque<Int>()
    private val freshAssignmentQueue = ArrayDeque<Int>()

    // ── Zoning state (Phase 5B) ──────────────────────────────────────────────

    internal val zoningByStockerId: MutableMap<Int, ZoningState> = mutableMapOf()

    data class ZoneAction(val stockerId: Int, val targetItemId: Int)

    // ── Utilization accumulators (Phase 5B, transient) ───────────────────────

    private var cashierBusyTicks: Int = 0
    private var cashierTotalTicks: Int = 0
    private var stockerBusyTicks: Int = 0
    private var stockerTotalTicks: Int = 0
    private var freshBusyTicks: Int = 0
    private var freshTotalTicks: Int = 0
    private var peakPendingCustomers: Int = 0
    private var hadUnstaffedRegisters: Boolean = false
    private var lastSampledHour: Int = -1
    private val pendingCustomersByHour: MutableMap<Int, Int> = mutableMapOf()

    var employeeActivities: Map<Int, EmployeeActivity> = emptyMap()
        private set

    // ── Tick-advance helpers ──────────────────────────────────────────────────

    fun advanceCashierProgressForRegister(
        registerId: Int,
        cashierWeight: Float,
        delta: Double,
        multiplier: Float,
    ): Int {
        if (cashierWeight <= 0f) return 0
        val current = cashierProgressByRegister.getOrDefault(registerId, 0f)
        val updated = current + CASHIER_ITEMS_PER_SECOND * cashierWeight * delta.toFloat() * multiplier
        val whole = updated.toInt()
        cashierProgressByRegister[registerId] = updated - whole
        return whole
    }

    fun advanceStockerProgress(stockerCount: Float, delta: Double, multiplier: Float): Int {
        if (stockerCount <= 0f) return 0
        stockerProgress += STOCKER_ACTIONS_PER_SECOND * stockerCount * delta.toFloat() * multiplier
        val whole = stockerProgress.toInt()
        stockerProgress -= whole
        return whole
    }

    /**
     * Advance stocker progress and return the entity ID responsible for each completed action.
     * Uses a weighted assignment queue so faster stockers get credited proportionally.
     */
    fun advanceStockerProgressWithAssignment(
        result: ActiveWeightResult,
        delta: Double,
        multiplier: Float,
    ): List<Int> {
        val whole = advanceStockerProgress(result.weight, delta, multiplier)
        if (whole <= 0) return emptyList()
        return assignActions(whole, result, stockerAssignmentQueue)
    }

    fun advanceStockingManagerProgress(managerWeight: Float, delta: Double, multiplier: Float): Int {
        if (managerWeight <= 0f) return 0
        stockingManagerProgress += STOCKER_ACTIONS_PER_SECOND * managerWeight * delta.toFloat() * multiplier
        val whole = stockingManagerProgress.toInt()
        stockingManagerProgress -= whole
        return whole
    }

    fun advanceStockingManagerProgressWithAssignment(
        result: ActiveWeightResult,
        delta: Double,
        multiplier: Float,
    ): List<Int> {
        val whole = advanceStockingManagerProgress(result.weight, delta, multiplier)
        if (whole <= 0) return emptyList()
        return assignActions(whole, result, stockingManagerAssignmentQueue)
    }

    fun advanceFreshHandlerProgress(freshHandlerCount: Float, delta: Double, multiplier: Float): Int {
        if (freshHandlerCount <= 0f) return 0
        freshHandlerProgress += FRESH_HANDLER_ACTIONS_PER_SECOND * freshHandlerCount * delta.toFloat() * multiplier
        val whole = freshHandlerProgress.toInt()
        freshHandlerProgress -= whole
        return whole
    }

    /**
     * Advance fresh handler progress and return the entity ID responsible for each completed action.
     * Uses a weighted assignment queue so faster handlers get credited proportionally.
     */
    fun advanceFreshProgressWithAssignment(
        result: ActiveWeightResult,
        delta: Double,
        multiplier: Float,
    ): List<Int> {
        val whole = advanceFreshHandlerProgress(result.weight, delta, multiplier)
        if (whole <= 0) return emptyList()
        return assignActions(whole, result, freshAssignmentQueue)
    }

    /**
     * Pop [count] entity IDs from [queue], refilling it when empty.
     * Each entity appears in the queue proportional to their speed weight,
     * so faster employees get credited with more actions.
     */
    private fun assignActions(
        count: Int,
        result: ActiveWeightResult,
        queue: ArrayDeque<Int>,
    ): List<Int> {
        val assigned = ArrayList<Int>(count)
        repeat(count) {
            if (queue.isEmpty()) refillQueue(queue, result.onShiftIds, result.perEntityWeights)
            assigned += if (queue.isEmpty()) result.onShiftIds.random() else queue.removeFirst()
        }
        return assigned
    }

    private fun refillQueue(queue: ArrayDeque<Int>, ids: List<Int>, weights: List<Float>) {
        if (ids.isEmpty()) return
        val minWeight = weights.min()
        for (i in ids.indices) {
            // Normalize so the slowest employee gets 1 slot, faster ones get proportionally more
            val slots = (weights[i] / minWeight).toInt().coerceAtLeast(1)
            repeat(slots) { queue.addLast(ids[i]) }
        }
        queue.shuffle()
    }

    // ── Zoning tick-advance ──────────────────────────────────────────────────

    fun advanceZoningForStocker(
        stockerId: Int,
        stockerWeight: Float,
        delta: Double,
        multiplier: Float,
    ): List<ZoneAction> {
        if (stockerWeight <= 0f) return emptyList()
        val state = zoningByStockerId.getOrPut(stockerId) { ZoningState() }
        val targetId = state.targetItemId ?: return emptyList()
        val updated = state.progress + ZONE_ACTIONS_PER_SECOND * stockerWeight * delta.toFloat() * multiplier
        val actions = mutableListOf<ZoneAction>()
        var remaining = updated
        while (remaining >= 1f) {
            remaining -= 1f
            actions += ZoneAction(stockerId, targetId)
        }
        zoningByStockerId[stockerId] = state.copy(progress = remaining)
        return actions
    }

    fun assignZoningTarget(stockerId: Int, itemId: Int) {
        val current = zoningByStockerId[stockerId]
        if (current?.targetItemId == itemId) return
        zoningByStockerId[stockerId] = ZoningState(targetItemId = itemId, progress = current?.progress ?: 0f)
    }

    fun clearZoningTarget(stockerId: Int) {
        val current = zoningByStockerId[stockerId] ?: return
        zoningByStockerId[stockerId] = current.copy(targetItemId = null)
    }

    fun getZoningTarget(stockerId: Int): Int? = zoningByStockerId[stockerId]?.targetItemId

    fun allClaimedZoningTargets(): Set<Int> =
        zoningByStockerId.values.mapNotNull { it.targetItemId }.toSet()

    fun cleanUpZoningForEntity(entityId: Int) {
        zoningByStockerId.remove(entityId)
    }

    // ── Utilization tracking ─────────────────────────────────────────────────

    fun updateUtilization(
        state: GameState,
        currentHour: Int,
        hasActionableBackroom: Boolean,
        hasUnzonedItems: Boolean,
        hasFreshWork: Boolean,
        dayOfWeek: Int = -1,
    ) {
        val registry = state.hiredEntityRegistry
        val schedules = state.staffSchedules
        val registers = state.registers
        val assignedIds = registers.mapNotNull { it.assignedCashierId }.toSet()

        if (state.pendingCustomers > peakPendingCustomers) {
            peakPendingCustomers = state.pendingCustomers
        }

        // Sample pending customers once per hour (keep max seen that hour)
        if (currentHour != lastSampledHour) {
            lastSampledHour = currentHour
            pendingCustomersByHour[currentHour] = state.pendingCustomers
        } else {
            val prev = pendingCustomersByHour[currentHour] ?: 0
            if (state.pendingCustomers > prev) {
                pendingCustomersByHour[currentHour] = state.pendingCustomers
            }
        }

        fun StaffShift?.isWorking(): Boolean =
            if (this == null) false
            else if (dayOfWeek >= 0) isOnShift(currentHour, dayOfWeek)
            else isOnShift(currentHour)

        val activities = mutableMapOf<Int, EmployeeActivity>()

        // Cashiers
        for (entity in registry.getByDef(EntityDef.CASHIER)) {
            val shift = schedules.firstOrNull { it.entityId == entity.id }
            if (!shift.isWorking()) {
                activities[entity.id] = EmployeeActivity.OFF_SHIFT
                continue
            }
            cashierTotalTicks++
            if (entity.id in assignedIds) {
                val reg = registers.firstOrNull { it.assignedCashierId == entity.id }
                if (reg != null && (reg.transactionActive || state.pendingCustomers > 0)) {
                    cashierBusyTicks++
                    activities[entity.id] = if (reg.transactionActive) EmployeeActivity.CASHIERING
                        else EmployeeActivity.WAITING_FOR_CUSTOMER
                } else {
                    activities[entity.id] = EmployeeActivity.WAITING_FOR_CUSTOMER
                }
            } else {
                activities[entity.id] = EmployeeActivity.IDLE
            }
        }

        // Only flag unstaffed registers when customers are actually waiting
        if (state.pendingCustomers > 0) {
            val unmannedRegisters = registers.count { reg ->
                reg.assignedCashierId == null &&
                    state.playerAssignedRegisterId != reg.registerId
            }
            if (unmannedRegisters > 0) hadUnstaffedRegisters = true
        }

        // Stockers
        for (entity in registry.getByDef(EntityDef.STOCKER)) {
            val shift = schedules.firstOrNull { it.entityId == entity.id }
            if (!shift.isWorking()) {
                activities[entity.id] = EmployeeActivity.OFF_SHIFT
                cleanUpZoningForEntity(entity.id)
                continue
            }
            stockerTotalTicks++
            if (hasActionableBackroom) {
                stockerBusyTicks++
                activities[entity.id] = EmployeeActivity.STOCKING
            } else if (hasUnzonedItems) {
                activities[entity.id] = EmployeeActivity.ZONING
            } else {
                activities[entity.id] = EmployeeActivity.IDLE
            }
        }

        // Fresh handlers
        for (entity in registry.getByDef(EntityDef.FRESH_HANDLER)) {
            val shift = schedules.firstOrNull { it.entityId == entity.id }
            if (!shift.isWorking()) {
                activities[entity.id] = EmployeeActivity.OFF_SHIFT
                continue
            }
            freshTotalTicks++
            if (hasFreshWork) {
                freshBusyTicks++
                activities[entity.id] = EmployeeActivity.HANDLING_FRESH
            } else {
                activities[entity.id] = EmployeeActivity.IDLE
            }
        }

        // Managers
        for (entity in registry.getByDef(EntityDef.MANAGER)) {
            val shift = schedules.firstOrNull { it.entityId == entity.id }
            if (!shift.isWorking()) {
                activities[entity.id] = EmployeeActivity.OFF_SHIFT
            } else {
                activities[entity.id] = EmployeeActivity.IDLE
            }
        }

        // Market Analysts
        for (entity in registry.getByDef(EntityDef.MARKET_ANALYST)) {
            val shift = schedules.firstOrNull { it.entityId == entity.id }
            if (!shift.isWorking()) {
                activities[entity.id] = EmployeeActivity.OFF_SHIFT
                continue
            }
            activities[entity.id] = when (state.researchState.analystAssignments[entity.id]) {
                is AnalystAssignment.Research -> EmployeeActivity.RESEARCHING
                AnalystAssignment.Consulting -> EmployeeActivity.CONSULTING
                null -> EmployeeActivity.IDLE
            }
        }

        employeeActivities = activities
    }

    private fun utilization(busy: Int, total: Int): Float =
        if (total > 0) busy.toFloat() / total else 0f

    fun currentCashierUtilization(): Float = utilization(cashierBusyTicks, cashierTotalTicks)

    fun currentStockerUtilization(): Float = utilization(stockerBusyTicks, stockerTotalTicks)

    fun currentFreshUtilization(): Float = utilization(freshBusyTicks, freshTotalTicks)

    fun snapshotDailyMetrics(): DailyStaffMetrics = DailyStaffMetrics(
        peakPendingCustomers = peakPendingCustomers,
        avgHourlyPendingCustomers = if (pendingCustomersByHour.isNotEmpty())
            pendingCustomersByHour.values.sum().toFloat() / pendingCustomersByHour.size else 0f,
        avgCashierUtilization = utilization(cashierBusyTicks, cashierTotalTicks),
        avgStockerUtilization = utilization(stockerBusyTicks, stockerTotalTicks),
        avgFreshUtilization = utilization(freshBusyTicks, freshTotalTicks),
        hasUnstaffedRegisters = hadUnstaffedRegisters,
        freshItemsOutOfStock = 0,
        freshOrdersAttempted = 0,
    )

    // ── Auto-Hire (Phase 5B) ─────────────────────────────────────────────────

    fun evaluateAutoHire(state: GameState): GameState {
        val registry = state.hiredEntityRegistry
        val hasManager = registry.getByDef(EntityDef.MANAGER).isNotEmpty()
        if (!hasManager) return state
        if (!state.storeManagerConfig.autoHireEnabled) return state
        if (state.autoHireBudget <= Money.ZERO) return state

        val hasSeniorManager = registry.getByDef(EntityDef.MANAGER).any { it.tier != Tier.BASE }
        val metrics = snapshotDailyMetrics()

        val freshOosIds = state.currentDayMetrics.outOfStockEvents
            .filter { event ->
                val inv = state.inventory[event.itemId]
                inv != null && (inv.shelfBatches + inv.backroomBatches).any { it.expirationDay != Int.MAX_VALUE }
            }
            .map { it.itemId }.toSet()
        val freshOrderedIds = (state.currentDayMetrics.autoOrderedFreshItems.map { it.itemId } +
            state.currentDayMetrics.incompleteOrderedFreshItems.map { it.itemId }).toSet()

        var result = state
        val events = mutableListOf<AutoHireEvent>()

        // Bootstrap: if zero employees of a type exist, hire one regardless of metrics
        val cashierCount = registry.getByDef(EntityDef.CASHIER).size
        val stockerCount = registry.getByDef(EntityDef.STOCKER).size
        val freshCount = registry.getByDef(EntityDef.FRESH_HANDLER).size

        // Hire one entity of [def]; log an AutoHireEvent only if the hire succeeded.
        fun hireAndLog(def: EntityDef, reason: String, detail: String) {
            val before = result.hiredEntityRegistry.totalCount()
            result = tryAutoHire(result, def)
            if (result.hiredEntityRegistry.totalCount() > before) {
                events += AutoHireEvent(def.displayName, reason, detail = detail)
            }
        }

        fun bootstrapHireIfNeeded(def: EntityDef, reason: String) =
            hireAndLog(def, reason, "Bootstrap hire — zero ${def.displayName.lowercase()}s on staff")

        val config = state.storeManagerConfig

        if (config.autoHireCashiers && cashierCount == 0 && result.registers.isNotEmpty()) bootstrapHireIfNeeded(EntityDef.CASHIER, "No cashiers")
        if (config.autoHireStockers && stockerCount == 0) bootstrapHireIfNeeded(EntityDef.STOCKER, "No stockers")
        val hasFreshItems = ResearchGates.hasFreshSubsystem(state.researchState.researchedUpgrades)
        if (config.autoHireFreshHandlers && freshCount == 0 && hasFreshItems) bootstrapHireIfNeeded(EntityDef.FRESH_HANDLER, "No fresh staff")

        // Skip metric-based hiring if manager terminated someone today — hiring and firing are mutually exclusive
        val firedToday = result.currentDayMetrics.autoHireEvents.any {
            it.action == com.example.superstoresimulator.domain.metrics.AutoHireAction.TERMINATED
        }
        if (firedToday) {
            if (events.isNotEmpty()) {
                result = result.copy(
                    currentDayMetrics = result.currentDayMetrics.copy(
                        autoHireEvents = result.currentDayMetrics.autoHireEvents + events,
                    ),
                )
            }
            return result
        }

        // Cashier auto-hire: avg customers in line vs register count
        val registerCount = result.registers.size
        val avgInLine = metrics.avgHourlyPendingCustomers
        val needMoreCheckout = avgInLine > registerCount

        if (config.autoHireCashiers && (needMoreCheckout || metrics.hasUnstaffedRegisters)) {
            val cashierIds = result.hiredEntityRegistry.getByDef(EntityDef.CASHIER).map { it.id }.toSet()
            val cashierShifts = result.staffSchedules.filter { it.entityId in cashierIds }
            val hasUncoveredHours = (6..20).any { hour -> cashierShifts.none { it.isOnShift(hour) } }
            val allRegistersManned = result.registers.all { reg ->
                reg.assignedCashierId != null || state.playerAssignedRegisterId == reg.registerId
            }

            if (needMoreCheckout && allRegistersManned) {
                // All registers staffed but still too many customers — need more registers (Store Manager handles that)
                // Hire another cashier anyway so the new register has someone to man it
                hireAndLog(EntityDef.CASHIER, "Registers full",
                    "Avg ${avgInLine.toInt()} customers/hr in line, all $registerCount registers manned")
            } else if (hasSeniorManager && !hasUncoveredHours && !needMoreCheckout && metrics.avgCashierUtilization < 1.0f) {
                events += AutoHireEvent(
                    EntityDef.CASHIER.displayName,
                    "Idle cashiers",
                    detail = "Utilization ${(metrics.avgCashierUtilization * 100).toInt()}% — reassign idle cashiers before hiring",
                    blocked = true,
                    blockReason = "Util. ${(metrics.avgCashierUtilization * 100).toInt()}%",
                )
            } else {
                val (reason, detail) = when {
                    hasUncoveredHours -> "Coverage gap" to "Some hours have no cashier on shift"
                    needMoreCheckout -> "Long lines" to "Avg ${avgInLine.toInt()} customers/hr in line (>${registerCount} registers)"
                    else -> "Unmanned register" to "Registers without assigned cashiers during open hours"
                }
                hireAndLog(EntityDef.CASHIER, reason, detail)
            }
        }

        // Stocker auto-hire: non-fresh backroom not empty or zone score too low.
        // Skip when stockers were just bootstrap-hired — backroom items are expected
        // when no one was stocking; the bootstrap hire covers that case.
        if (config.autoHireStockers && stockerCount > 0) {
            val hasBackroomStock = result.inventory.any { (_, inv) ->
                inv.backroomBatches.any { it.expirationDay == Int.MAX_VALUE && it.quantity > 0 }
            }
            val avgZone = result.avgZoneScore
            val zoneThreshold = config.zoneScoreHireThreshold / 100f
            if (config.hireStockerOnBackroomFull && hasBackroomStock) {
                hireAndLog(EntityDef.STOCKER, "Backroom full", "Non-perishable cases still in backroom at end of day")
            } else if (config.hireStockerOnLowZoneScore && avgZone < zoneThreshold) {
                hireAndLog(EntityDef.STOCKER, "Low zone score", "Zone score ${(avgZone * 100).toInt()}% — below ${config.zoneScoreHireThreshold}% target")
            }
        }

        // Fresh handler auto-hire: only when OOS items have no pending orders
        val unorderedOosIds = freshOosIds - freshOrderedIds
        if (config.autoHireFreshHandlers && unorderedOosIds.isNotEmpty()) {
            val freshIds = result.hiredEntityRegistry.getByDef(EntityDef.FRESH_HANDLER).map { it.id }.toSet()
            val freshShifts = result.staffSchedules.filter { it.entityId in freshIds }
            val hasUncoveredFreshHours = (6..20).any { hour -> freshShifts.none { it.isOnShift(hour) } }

            if (hasSeniorManager && !hasUncoveredFreshHours && metrics.avgFreshUtilization < 1.0f) {
                events += AutoHireEvent(
                    EntityDef.FRESH_HANDLER.displayName,
                    "Idle handlers",
                    detail = "Utilization ${(metrics.avgFreshUtilization * 100).toInt()}% — reassign idle handlers before hiring",
                    blocked = true,
                    blockReason = "Util. ${(metrics.avgFreshUtilization * 100).toInt()}%",
                )
            } else {
                hireAndLog(EntityDef.FRESH_HANDLER, "Fresh OOS", "Fresh items out of stock with no pending order")
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

    // ── Store Manager Auto-Actions ─────────────────────────────────────────

    fun evaluateStoreManagerActions(state: GameState): GameState {
        val hasStoreManager = state.hiredEntityRegistry.getByDef(EntityDef.MANAGER)
            .any { it.isStoreManager }
        if (!hasStoreManager) return state

        val config = state.storeManagerConfig
        var result = state
        val events = mutableListOf<AutoHireEvent>()

        // 1. Buy register if all registers are manned and more cashiers could use one
        if (config.autoBuyRegistersEnabled) {
            val maxCashiersPerShift = listOf(SHIFT_MORNING, SHIFT_MID, SHIFT_CLOSING).maxOf { shift ->
                result.hiredEntityRegistry.getByDef(EntityDef.CASHIER).count { e ->
                    result.staffSchedules.any { s -> s.entityId == e.id && s.startHour == shift }
                }
            }
            if (maxCashiersPerShift > result.registers.size &&
                result.ownedRegisterCount < result.currentStoreSize.maxRegisters
            ) {
                val cost = com.example.superstoresimulator.domain.store.StoreSize.nextRegisterCost(result.ownedRegisterCount)
                if (result.money - cost >= result.autoHireBudget) {
                    val newRegisterId = (result.registers.maxOfOrNull { it.registerId } ?: 0) + 1
                    result = result.copy(
                        money = result.money - cost,
                        registers = result.registers + RegisterState(registerId = newRegisterId),
                    )
                    events += AutoHireEvent("Register", "New register", detail = "Store Manager purchased register #${result.ownedRegisterCount}",
                        action = com.example.superstoresimulator.domain.metrics.AutoHireAction.PURCHASED)
                }
            }
        }

        // 2. Auto-promote employees who reached max level
        if (config.autoPromoteEnabled) {
            val promotable = result.hiredEntityRegistry.hiredEntities.filter {
                it.canPromote && it.level >= HiredEntity.MAX_LEVEL
            }
            for (entity in promotable) {
                val cost = entity.upgradeCost
                if (result.money - cost >= result.autoHireBudget) {
                    val promoted = promoteEntity(result, entity.id)
                    if (promoted !== result) {
                        result = promoted
                        events += AutoHireEvent(
                            entity.entityDefinition.displayName,
                            "Auto-promoted",
                            detail = "Store Manager promoted ${entity.name} to ${entity.tier.next()}",
                            action = com.example.superstoresimulator.domain.metrics.AutoHireAction.PROMOTED,
                        )
                    }
                }
            }
        }

        // 3. Rebalance shifts only when some hours have zero coverage
        if (config.autoRebalanceShiftsEnabled) {
            for (def in listOf(EntityDef.CASHIER, EntityDef.STOCKER, EntityDef.FRESH_HANDLER)) {
                val entityIds = result.hiredEntityRegistry.getByDef(def).map { it.id }.toSet()
                val shifts = result.staffSchedules.filter { it.entityId in entityIds }
                if (shifts.size < 2) continue

                val coverageByHour = coverageByHour(shifts)
                val hasZeroCoverage = coverageByHour.values.any { it == 0 }
                if (!hasZeroCoverage) continue

                val zeroHours = (6..20).filter { (coverageByHour[it] ?: 0) == 0 }
                val maxCoverage = coverageByHour.values.max()
                if (maxCoverage <= 1) continue

                val shiftsByStart = shifts.groupBy { it.startHour }
                val worstSource = shiftsByStart.maxByOrNull { it.value.size }?.key ?: continue
                val sourceCount = shiftsByStart[worstSource]?.size ?: 0
                if (sourceCount <= 1) continue

                val presets = listOf(SHIFT_MORNING, SHIFT_MID, SHIFT_CLOSING)
                val bestTarget = presets.maxByOrNull { start ->
                    val shiftHours = start until (start + 8)
                    zeroHours.count { it in shiftHours }
                } ?: continue
                if (bestTarget == worstSource) continue

                val entityToMove = shifts.firstOrNull { it.startHour == worstSource }?.entityId ?: continue
                result = result.copy(
                    staffSchedules = result.staffSchedules.map { s ->
                        if (s.entityId == entityToMove) s.copy(startHour = bestTarget, durationHours = 8) else s
                    }
                )
                events += AutoHireEvent(def.displayName, "Shift rebalanced", detail = "Store Manager moved ${def.displayName} to cover hours with no staff",
                    action = com.example.superstoresimulator.domain.metrics.AutoHireAction.REBALANCED)
            }
        }

        // 4. Auto-terminate excess idle employees (graduated: reduce days first, fire as last resort)
        val hiredToday = result.currentDayMetrics.autoHireEvents.any { it.action == com.example.superstoresimulator.domain.metrics.AutoHireAction.HIRED }
        if (config.autoTerminateEnabled && !hiredToday) {
            val currentDay = result.currentTime.dayNumber
            val recentDays = result.completedDayMetrics.takeLast(TERMINATE_LOOKBACK_DAYS)
            if (recentDays.size >= TERMINATE_LOOKBACK_DAYS) {
                val avgCashierUtil = recentDays.map { it.avgCashierUtilization }.average().toFloat()
                val avgStockerUtil = recentDays.map { it.avgStockerUtilization }.average().toFloat()
                val avgFreshUtil = recentDays.map { it.avgFreshUtilization }.average().toFloat()
                val avgZone = recentDays.map { it.avgZoneScore }.average().toFloat()
                val minDays = config.minDaysPerWeek

                fun canTerminate(def: EntityDef): Boolean {
                    val lastDay = result.lastTerminationDayByType[def.key] ?: return true
                    return currentDay - lastDay >= TERMINATE_COOLDOWN_DAYS
                }

                fun recordTermination(def: EntityDef) {
                    result = result.copy(
                        lastTerminationDayByType = result.lastTerminationDayByType + (def.key to currentDay)
                    )
                }

                val weekdayDemand = TrafficSchedule.WEEKDAY.sumOf { it.baseCustomerRate.toDouble() }.toFloat()
                val weekendDemand = TrafficSchedule.WEEKEND.sumOf { it.baseCustomerRate.toDouble() }.toFloat()

                fun tryReduceDays(victim: HiredEntity, def: EntityDef, reason: String): Boolean {
                    val shift = result.staffSchedules.firstOrNull { it.entityId == victim.id } ?: return false
                    val currentDays = shift.daysPerWeek
                    if (currentDays <= minDays) return false
                    val daysSet = if (shift.workDays.isEmpty()) (0..6).toSet() else shift.workDays
                    val lowestDemandDay = daysSet.minByOrNull { day ->
                        val isWeekend = day in 5..6
                        if (isWeekend) weekendDemand else weekdayDemand
                    } ?: return false
                    result = result.copy(
                        staffSchedules = result.staffSchedules.map { s ->
                            if (s.entityId == victim.id) s.copy(workDays = daysSet - lowestDemandDay) else s
                        }
                    )
                    recordTermination(def)
                    events += AutoHireEvent(
                        def.displayName,
                        "Reduced hours",
                        detail = "Store Manager reduced ${victim.name} to ${currentDays - 1}d/wk — $reason",
                        action = com.example.superstoresimulator.domain.metrics.AutoHireAction.REDUCED_HOURS,
                    )
                    return true
                }

                // Cashiers
                val cashiers = result.hiredEntityRegistry.getByDef(EntityDef.CASHIER)
                if (canTerminate(EntityDef.CASHIER) && cashiers.size > 1 && cashiers.size > result.registers.size && avgCashierUtil < 0.5f) {
                    val assignedIds = result.registers.mapNotNull { it.assignedCashierId }.toSet()
                    val unassigned = cashiers.filter { it.id !in assignedIds }
                    val victim = unassigned.minByOrNull { it.level * 100 + it.xp }
                    if (victim != null) {
                        val reduced = tryReduceDays(victim, EntityDef.CASHIER,
                            "${cashiers.size} cashiers for ${result.registers.size} registers, util ${(avgCashierUtil * 100).toInt()}%")
                        if (!reduced) {
                            result = fireEntity(result, victim.id)
                            recordTermination(EntityDef.CASHIER)
                            events += AutoHireEvent(
                                EntityDef.CASHIER.displayName,
                                "Overstaffed",
                                detail = "Terminated ${victim.name} — already at ${minDays}d/wk min, util ${(avgCashierUtil * 100).toInt()}%",
                                action = com.example.superstoresimulator.domain.metrics.AutoHireAction.TERMINATED,
                            )
                        }
                    }
                }

                // Stockers
                val stockers = result.hiredEntityRegistry.getByDef(EntityDef.STOCKER)
                    .filter { !it.isDeptManager }
                if (canTerminate(EntityDef.STOCKER) && stockers.size > 1 && avgZone >= 0.95f && avgStockerUtil < 0.3f) {
                    val victim = stockers.minByOrNull { it.level * 100 + it.xp }
                    if (victim != null) {
                        val reduced = tryReduceDays(victim, EntityDef.STOCKER,
                            "zone ${(avgZone * 100).toInt()}%, util ${(avgStockerUtil * 100).toInt()}%")
                        if (!reduced) {
                            result = fireEntity(result, victim.id)
                            recordTermination(EntityDef.STOCKER)
                            events += AutoHireEvent(
                                EntityDef.STOCKER.displayName,
                                "Overstaffed",
                                detail = "Terminated ${victim.name} — already at ${minDays}d/wk min, util ${(avgStockerUtil * 100).toInt()}%",
                                action = com.example.superstoresimulator.domain.metrics.AutoHireAction.TERMINATED,
                            )
                        }
                    }
                }

                // Fresh handlers
                val freshHandlers = result.hiredEntityRegistry.getByDef(EntityDef.FRESH_HANDLER)
                    .filter { !it.isDeptManager }
                val recentFreshOos = recentDays.any { day ->
                    day.outOfStockEvents.any { event ->
                        val inv = result.inventory[event.itemId]
                        inv != null && (inv.shelfBatches + inv.backroomBatches).any { it.expirationDay != Int.MAX_VALUE }
                    }
                }
                if (canTerminate(EntityDef.FRESH_HANDLER) && freshHandlers.size > 1 && !recentFreshOos && avgFreshUtil < 0.3f) {
                    val victim = freshHandlers.minByOrNull { it.level * 100 + it.xp }
                    if (victim != null) {
                        val reduced = tryReduceDays(victim, EntityDef.FRESH_HANDLER,
                            "no fresh OOS in ${TERMINATE_LOOKBACK_DAYS} days, util ${(avgFreshUtil * 100).toInt()}%")
                        if (!reduced) {
                            result = fireEntity(result, victim.id)
                            recordTermination(EntityDef.FRESH_HANDLER)
                            events += AutoHireEvent(
                                EntityDef.FRESH_HANDLER.displayName,
                                "Overstaffed",
                                detail = "Terminated ${victim.name} — already at ${minDays}d/wk min, util ${(avgFreshUtil * 100).toInt()}%",
                                action = com.example.superstoresimulator.domain.metrics.AutoHireAction.TERMINATED,
                            )
                        }
                    }
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

    fun optimizeWeeklySchedules(state: GameState): GameState {
        val hasStoreManager = state.hiredEntityRegistry.getByDef(EntityDef.MANAGER)
            .any { it.isStoreManager }
        if (!hasStoreManager) return state

        val config = state.storeManagerConfig
        if (!config.autoOptimizeWeeklySchedule) return state
        if (state.currentTime.dayOfWeek != 0) return state

        val weekdayDemand = TrafficSchedule.WEEKDAY.sumOf { it.baseCustomerRate.toDouble() }.toFloat()
        val weekendDemand = TrafficSchedule.WEEKEND.sumOf { it.baseCustomerRate.toDouble() }.toFloat()
        val deliveryDays = state.truckConfig.deliveryDays

        fun demandScores(role: String): List<Pair<Int, Float>> {
            return (0..6).map { day ->
                val isWeekend = day in 5..6
                val score = when (role) {
                    "cashier" -> if (isWeekend) weekendDemand else weekdayDemand
                    "stocker" -> {
                        val base = if (isWeekend) weekendDemand * 0.5f else weekdayDemand * 0.5f
                        if (day in deliveryDays) base * 2f else base
                    }
                    else -> {
                        val base = 1f
                        if (day in deliveryDays) base * 1.5f else base
                    }
                }
                day to score
            }
        }

        var schedules = state.staffSchedules
        val events = mutableListOf<AutoHireEvent>()

        for (def in listOf(EntityDef.CASHIER, EntityDef.STOCKER, EntityDef.FRESH_HANDLER)) {
            val entities = state.hiredEntityRegistry.getByDef(def)
            if (entities.isEmpty()) continue

            // Days ranked high→low demand; days off come from the low-priority tail.
            val ranked = demandScores(def.key).sortedByDescending { it.second }.map { it.first }
            val maxDays = config.maxDaysPerWeek.coerceIn(config.minDaysPerWeek.coerceIn(1, 5), 5)
            val daysOff = (7 - maxDays).coerceIn(0, 7)

            // Stagger off-days across workers so no single day loses all coverage
            // (otherwise every worker shares the same off-day, e.g. all off Friday).
            entities.forEachIndexed { idx, entity ->
                val assignedDays = if (daysOff == 0) {
                    (0..6).toSet()
                } else {
                    val offDays = (0 until daysOff)
                        .map { ranked[ranked.size - 1 - ((idx + it) % ranked.size)] }
                        .toSet()
                    (0..6).toSet() - offDays
                }

                schedules = schedules.map { s ->
                    if (s.entityId == entity.id) s.copy(workDays = assignedDays) else s
                }
            }

            events += AutoHireEvent(
                def.displayName,
                "Schedule optimized",
                detail = "Store Manager optimized ${entities.size} ${def.displayName}(s) — ${maxDays}d/wk",
                action = com.example.superstoresimulator.domain.metrics.AutoHireAction.SCHEDULE_OPTIMIZED,
            )
        }

        var result = state.copy(staffSchedules = schedules)
        if (events.isNotEmpty()) {
            result = result.copy(
                currentDayMetrics = result.currentDayMetrics.copy(
                    autoHireEvents = result.currentDayMetrics.autoHireEvents + events,
                ),
            )
        }
        return result
    }

    private fun tryAutoHire(state: GameState, def: EntityDef): GameState {
        return hireEntity(state, def)
    }

    private fun coverageByHour(shifts: List<StaffShift>): Map<Int, Int> =
        (6..20).associateWith { hour -> shifts.count { it.isOnShift(hour) } }

    // ── Pure state operations ─────────────────────────────────────────────────

    fun hireEntity(state: GameState, def: EntityDef): GameState {
        val newRegistry = state.hiredEntityRegistry.hireEntity(def)
        val newEntityId = newRegistry.hiredEntities.last().id

        val currentTypeEntityIds = state.hiredEntityRegistry.getByDef(def).map { it.id }.toSet()
        val existingShifts = state.staffSchedules.filter { it.entityId in currentTypeEntityIds }

        val newShift = pickBestShift(newEntityId, coverageByHour(existingShifts))

        return state.copy(
            hiredEntityRegistry = newRegistry,
            staffSchedules = state.staffSchedules + newShift,
        )
    }

    private fun pickBestShift(entityId: Int, coverageByHour: Map<Int, Int>): StaffShift {
        val minCoverage = coverageByHour.values.min()
        val uncoveredHours = (6..20).filter { (coverageByHour[it] ?: 0) == minCoverage }

        // If gap is small (2-4 hours) and other hours already have more coverage, use a short shift
        if (uncoveredHours.size in 2..4) {
            val gapStart = uncoveredHours.first()
            val gapEnd = uncoveredHours.last() + 1
            val gapSpan = gapEnd - gapStart
            // Only short-shift if the gap is contiguous and compact
            if (gapSpan == uncoveredHours.size && gapSpan <= 4) {
                val duration = gapSpan.coerceIn(4, 8)
                val start = gapStart.coerceIn(6, 21 - duration)
                return StaffShift(entityId = entityId, startHour = start, durationHours = duration, workDays = DEFAULT_WORK_DAYS)
            }
        }

        // Otherwise pick best full-length preset
        val presets = listOf(SHIFT_MORNING, SHIFT_MID, SHIFT_CLOSING)
        val bestPreset = presets.maxByOrNull { startHour ->
            val shiftHours = startHour until (startHour + 8)
            val zeroCoverage = shiftHours.count { h -> (coverageByHour[h] ?: 0) == 0 }
            val totalGap = shiftHours.sumOf { h -> 1.0 / ((coverageByHour[h] ?: 0) + 1) }
            zeroCoverage * 100 + totalGap
        } ?: SHIFT_MORNING
        return StaffShift(entityId = entityId, startHour = bestPreset, workDays = DEFAULT_WORK_DAYS)
    }

    fun promoteEntity(state: GameState, entityId: Int): GameState {
        val entity = state.hiredEntityRegistry.getById(entityId)
        if (entity.tier == Tier.MANAGER) return state
        // Only one Store Manager allowed
        if (entity.entityDefinition == EntityDef.MANAGER && entity.tier == Tier.FAST) {
            val hasStoreManager = state.hiredEntityRegistry.getByDef(EntityDef.MANAGER).any { it.isStoreManager }
            if (hasStoreManager) return state
        }
        // Dept Manager cap: 1 per EMPLOYEES_PER_DEPT_MANAGER non-manager employees of same type
        if (entity.entityDefinition != EntityDef.MANAGER && entity.tier == Tier.FAST) {
            val peers = state.hiredEntityRegistry.getByDef(entity.entityDefinition)
            val currentManagers = peers.count { it.isDeptManager }
            val nonManagerCount = peers.count { it.tier != Tier.MANAGER }
            if (currentManagers >= nonManagerCount / EMPLOYEES_PER_DEPT_MANAGER) return state
        }
        val cost = entity.upgradeCost
        if (state.money < cost) return state
        return state.copy(
            hiredEntityRegistry = state.hiredEntityRegistry.promoteEntity(entityId),
            money = state.money - cost,
        )
    }

    fun fireEntity(state: GameState, entityId: Int): GameState {
        cleanUpZoningForEntity(entityId)
        return state.copy(
            hiredEntityRegistry = state.hiredEntityRegistry.fireEntity(entityId),
            staffSchedules = state.staffSchedules.filter { it.entityId != entityId },
        )
    }

    fun updateShift(state: GameState, entityId: Int, newStartHour: Int, newDuration: Int = 8, workDays: Set<Int> = emptySet()): GameState {
        val duration = newDuration.coerceIn(2, 8)
        val start = newStartHour.coerceIn(6, 21 - duration)
        val validDays = workDays.filter { it in 0..6 }.toSet()
        val newShift = StaffShift(entityId = entityId, startHour = start, durationHours = duration, workDays = validDays)
        val updatedSchedules = if (state.staffSchedules.any { it.entityId == entityId }) {
            state.staffSchedules.map { shift ->
                if (shift.entityId == entityId) newShift else shift
            }
        } else {
            state.staffSchedules + newShift
        }
        return state.copy(staffSchedules = updatedSchedules)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        cashierProgressByRegister.clear()
        stockerProgress = 0f
        freshHandlerProgress = 0f
        stockingManagerProgress = 0f
        zoningByStockerId.clear()
        resetDailyMetrics()
    }

    fun restoreAccumulators(acc: SimAccumulators) {
        cashierProgressByRegister.clear()
        cashierProgressByRegister.putAll(acc.cashierProgressByRegister)
        stockerProgress = acc.stockerProgress
        stockingManagerProgress = acc.stockingManagerProgress
        freshHandlerProgress = acc.freshHandlerProgress
        zoningByStockerId.clear()
        zoningByStockerId.putAll(acc.zoningByStockerId)
        resetDailyMetrics()
    }

    fun resetDailyMetrics() {
        cashierBusyTicks = 0
        cashierTotalTicks = 0
        stockerBusyTicks = 0
        stockerTotalTicks = 0
        freshBusyTicks = 0
        freshTotalTicks = 0
        peakPendingCustomers = 0
        hadUnstaffedRegisters = false
        lastSampledHour = -1
        pendingCustomersByHour.clear()
    }

    // ── Constants + static helpers ────────────────────────────────────────────

    companion object {
        const val CASHIER_ITEMS_PER_SECOND = 1.0f
        const val STOCKER_ACTIONS_PER_SECOND = 0.15f
        const val FRESH_HANDLER_ACTIONS_PER_SECOND = 0.1f

        const val SHIFT_MORNING = 6
        const val SHIFT_MID     = 10
        const val SHIFT_CLOSING = 13

        // Default work days for a new shift: Mon–Fri (5 = max days/week). Avoids the
        // emptySet "every day" (7) default which would exceed the 5-day cap.
        val DEFAULT_WORK_DAYS = (0..4).toSet()

        // XP constants
        const val XP_PER_STOCK_ACTION = 1
        const val MANAGER_XP_PER_SUPERVISED_ACTION = 1

        // Zoning constants
        const val ZONE_DECAY_PER_SALE = 0.08f
        const val ZONE_ACTIONS_PER_SECOND = 0.1f
        const val ZONE_PER_ACTION = 0.35f
        const val ZONE_FLOOR = 0.4f

        const val EMPLOYEES_PER_DEPT_MANAGER = 5
        const val TERMINATE_LOOKBACK_DAYS = 3
        const val TERMINATE_COOLDOWN_DAYS = 3

        private const val TAG = "StaffManager"

        data class ActiveWeightResult(
            val weight: Float,
            val onShiftIds: List<Int>,
            val perEntityWeights: List<Float> = emptyList(),
        )

        fun activeWeightedCount(
            def: EntityDef,
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
            dayOfWeek: Int = -1,
        ): Float = activeWeightedCountWithIds(def, currentHour, schedules, registry, dayOfWeek).weight

        fun activeWeightedCountWithIds(
            def: EntityDef,
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
            dayOfWeek: Int = -1,
        ): ActiveWeightResult {
            var weight = 0.0
            val ids = mutableListOf<Int>()
            val weights = mutableListOf<Float>()
            for (entity in registry.getByDef(def)) {
                val shift = schedules.firstOrNull { it.entityId == entity.id }
                val onShift = if (dayOfWeek >= 0)
                    shift != null && shift.isOnShift(currentHour, dayOfWeek)
                else
                    shift != null && shift.isOnShift(currentHour)
                if (onShift) {
                    val w = entity.throughputWeight * entity.levelMultiplier * entity.trait.throughputMultiplier
                    weight += w
                    ids.add(entity.id)
                    weights.add(w)
                }
            }
            return ActiveWeightResult(weight.toFloat(), ids, weights)
        }

        data class TickBonuses(
            val cashierBonus: Float,
            val stockerBonus: Float,
            val freshBonus: Float,
            val onShiftManagerIds: List<Int>,
        )

        fun computeAllBonuses(
            playerRole: PlayerRole,
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
            dayOfWeek: Int = -1,
        ): TickBonuses {
            var managerBonusSum = 0.0
            var hasCashierDeptMgr = false
            var hasStockerDeptMgr = false
            var hasFreshDeptMgr = false
            val onShiftManagerIds = mutableListOf<Int>()

            for (entity in registry.hiredEntities) {
                val isOnShift = if (dayOfWeek >= 0)
                    schedules.any { s -> s.entityId == entity.id && s.isOnShift(currentHour, dayOfWeek) }
                else
                    schedules.any { s -> s.entityId == entity.id && s.isOnShift(currentHour) }
                if (!isOnShift) continue

                if (entity.entityDefinition == EntityDef.MANAGER) {
                    onShiftManagerIds.add(entity.id)
                    managerBonusSum += when (entity.tier) {
                        Tier.BASE -> 0.15
                        Tier.FAST -> 0.25
                        Tier.MANAGER -> 0.30
                    }
                } else if (entity.isDeptManager) {
                    when (entity.entityDefinition) {
                        EntityDef.CASHIER -> hasCashierDeptMgr = true
                        EntityDef.STOCKER -> hasStockerDeptMgr = true
                        EntityDef.FRESH_HANDLER -> hasFreshDeptMgr = true
                        else -> {}
                    }
                }
            }

            var globalBonus = 1.0f + managerBonusSum.toFloat()
            if (playerRole == PlayerRole.MANAGE) {
                globalBonus *= 1.10f
            }

            return TickBonuses(
                cashierBonus = globalBonus * if (hasCashierDeptMgr) 1.10f else 1.0f,
                stockerBonus = globalBonus * if (hasStockerDeptMgr) 1.10f else 1.0f,
                freshBonus = globalBonus * if (hasFreshDeptMgr) 1.10f else 1.0f,
                onShiftManagerIds = onShiftManagerIds,
            )
        }

        fun zonePurchaseMultiplier(zoneScore: Float): Float =
            ZONE_FLOOR + (zoneScore * (1.0f - ZONE_FLOOR))
    }
}
