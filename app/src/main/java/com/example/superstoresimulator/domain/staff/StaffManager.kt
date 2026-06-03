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
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.player.PlayerRole

class StaffManager {

    private val cashierProgressByRegister: MutableMap<Int, Float> = mutableMapOf()
    private var stockerProgress: Float = 0f
    private var freshHandlerProgress: Float = 0f

    // ── Zoning state (Phase 5B) ──────────────────────────────────────────────

    data class StockerZoningState(
        val targetItemId: Int? = null,
        val progress: Float = 0f,
    )

    private val zoningByStockerId: MutableMap<Int, StockerZoningState> = mutableMapOf()

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

    fun advanceCashierProgress(cashierCount: Float, delta: Double, multiplier: Float): Int =
        advanceCashierProgressForRegister(FALLBACK_CASHIER_KEY, cashierCount, delta, multiplier)

    fun advanceStockerProgress(stockerCount: Float, delta: Double, multiplier: Float): Int {
        if (stockerCount <= 0f) return 0
        stockerProgress += STOCKER_ACTIONS_PER_SECOND * stockerCount * delta.toFloat() * multiplier
        val whole = stockerProgress.toInt()
        stockerProgress -= whole
        return whole
    }

    fun advanceFreshHandlerProgress(freshHandlerCount: Float, delta: Double, multiplier: Float): Int {
        if (freshHandlerCount <= 0f) return 0
        freshHandlerProgress += FRESH_HANDLER_ACTIONS_PER_SECOND * freshHandlerCount * delta.toFloat() * multiplier
        val whole = freshHandlerProgress.toInt()
        freshHandlerProgress -= whole
        return whole
    }

    // ── Zoning tick-advance ──────────────────────────────────────────────────

    fun advanceZoningForStocker(
        stockerId: Int,
        stockerWeight: Float,
        delta: Double,
        multiplier: Float,
    ): List<ZoneAction> {
        if (stockerWeight <= 0f) return emptyList()
        val state = zoningByStockerId.getOrPut(stockerId) { StockerZoningState() }
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
        zoningByStockerId[stockerId] = StockerZoningState(targetItemId = itemId, progress = current?.progress ?: 0f)
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
    ) {
        val registry = state.hiredEntityRegistry
        val schedules = state.staffSchedules
        val registers = state.registers
        val assignedIds = registers.mapNotNull { it.assignedCashierId }.toSet()

        if (state.pendingCustomers > peakPendingCustomers) {
            peakPendingCustomers = state.pendingCustomers
        }

        val activities = mutableMapOf<Int, EmployeeActivity>()

        // Cashiers
        for (entity in registry.getByDef(EntityDef.CASHIER)) {
            val shift = schedules.firstOrNull { it.entityId == entity.id }
            if (shift == null || !shift.isOnShift(currentHour)) {
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
            if (shift == null || !shift.isOnShift(currentHour)) {
                activities[entity.id] = EmployeeActivity.OFF_SHIFT
                cleanUpZoningForEntity(entity.id)
                continue
            }
            stockerTotalTicks++
            if (hasActionableBackroom) {
                stockerBusyTicks++
                activities[entity.id] = EmployeeActivity.STOCKING
            } else if (hasUnzonedItems) {
                stockerBusyTicks++
                activities[entity.id] = EmployeeActivity.ZONING
            } else {
                activities[entity.id] = EmployeeActivity.IDLE
            }
        }

        // Fresh handlers
        for (entity in registry.getByDef(EntityDef.FRESH_HANDLER)) {
            val shift = schedules.firstOrNull { it.entityId == entity.id }
            if (shift == null || !shift.isOnShift(currentHour)) {
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
            if (shift == null || !shift.isOnShift(currentHour)) {
                activities[entity.id] = EmployeeActivity.OFF_SHIFT
            } else {
                activities[entity.id] = EmployeeActivity.IDLE
            }
        }

        employeeActivities = activities
    }

    fun currentCashierUtilization(): Float =
        if (cashierTotalTicks > 0) cashierBusyTicks.toFloat() / cashierTotalTicks else 0f

    fun currentStockerUtilization(): Float =
        if (stockerTotalTicks > 0) stockerBusyTicks.toFloat() / stockerTotalTicks else 0f

    fun currentFreshUtilization(): Float =
        if (freshTotalTicks > 0) freshBusyTicks.toFloat() / freshTotalTicks else 0f

    fun snapshotDailyMetrics(): DailyStaffMetrics = DailyStaffMetrics(
        peakPendingCustomers = peakPendingCustomers,
        avgCashierUtilization = if (cashierTotalTicks > 0)
            cashierBusyTicks.toFloat() / cashierTotalTicks else 0f,
        avgStockerUtilization = if (stockerTotalTicks > 0)
            stockerBusyTicks.toFloat() / stockerTotalTicks else 0f,
        avgFreshUtilization = if (freshTotalTicks > 0)
            freshBusyTicks.toFloat() / freshTotalTicks else 0f,
        hasUnstaffedRegisters = hadUnstaffedRegisters,
        freshItemsOutOfStock = 0,
        freshOrdersAttempted = 0,
    )

    // ── Auto-Hire (Phase 5B) ─────────────────────────────────────────────────

    fun evaluateAutoHire(state: GameState): GameState {
        val registry = state.hiredEntityRegistry
        val hasManager = registry.getByDef(EntityDef.MANAGER).isNotEmpty()
        if (!hasManager) return state
        if (state.autoHireBudget <= Money.ZERO) return state

        val hasSeniorManager = registry.getByDef(EntityDef.MANAGER).any { it.tier != Tier.BASE }
        val metrics = snapshotDailyMetrics()

        val freshOosIds = state.currentDayMetrics.outOfStockEvents
            .map { it.itemId }.toSet()
        val freshOrderedIds = (state.currentDayMetrics.autoOrderedFreshItems.map { it.itemId } +
            state.currentDayMetrics.incompleteOrderedFreshItems.map { it.itemId }).toSet()

        var result = state
        val events = mutableListOf<AutoHireEvent>()

        // Bootstrap: if zero employees of a type exist, hire one regardless of metrics
        val cashierCount = registry.getByDef(EntityDef.CASHIER).size
        val stockerCount = registry.getByDef(EntityDef.STOCKER).size
        val freshCount = registry.getByDef(EntityDef.FRESH_HANDLER).size

        if (cashierCount == 0 && result.registers.isNotEmpty()) {
            val before = result.hiredEntityRegistry.totalCount()
            result = tryAutoHire(result, EntityDef.CASHIER)
            if (result.hiredEntityRegistry.totalCount() > before) {
                events += AutoHireEvent(EntityDef.CASHIER.displayName, "No cashiers on staff")
            }
        }
        if (stockerCount == 0) {
            val before = result.hiredEntityRegistry.totalCount()
            result = tryAutoHire(result, EntityDef.STOCKER)
            if (result.hiredEntityRegistry.totalCount() > before) {
                events += AutoHireEvent(EntityDef.STOCKER.displayName, "No stockers on staff")
            }
        }
        if (freshCount == 0 && state.currentTier >= ItemUnlockTier.TIER_3) {
            val before = result.hiredEntityRegistry.totalCount()
            result = tryAutoHire(result, EntityDef.FRESH_HANDLER)
            if (result.hiredEntityRegistry.totalCount() > before) {
                events += AutoHireEvent(EntityDef.FRESH_HANDLER.displayName, "No fresh handlers on staff")
            }
        }

        // Cashier auto-hire
        if (metrics.hasUnstaffedRegisters) {
            // Check if there are coverage gaps (hours with no cashiers at all)
            val cashierIds = result.hiredEntityRegistry.getByDef(EntityDef.CASHIER).map { it.id }.toSet()
            val cashierShifts = result.staffSchedules.filter { it.entityId in cashierIds }
            val hasUncoveredHours = (6..20).any { hour -> cashierShifts.none { it.isOnShift(hour) } }

            if (hasSeniorManager && !hasUncoveredHours && metrics.avgCashierUtilization < 1.0f) {
                events += AutoHireEvent(
                    EntityDef.CASHIER.displayName,
                    "Unstaffed registers detected",
                    blocked = true,
                    blockReason = "Utilization ${(metrics.avgCashierUtilization * 100).toInt()}% — reassign idle cashiers",
                )
            } else {
                val before = result.hiredEntityRegistry.totalCount()
                result = tryAutoHire(result, EntityDef.CASHIER)
                if (result.hiredEntityRegistry.totalCount() > before) {
                    events += AutoHireEvent(EntityDef.CASHIER.displayName,
                        if (hasUncoveredHours) "No cashier coverage for some hours" else "Unstaffed registers detected")
                }
            }
        }

        // Stocker auto-hire: non-fresh backroom not empty or zone score too low
        val hasBackroomStock = result.inventory.any { (_, inv) ->
            inv.backroomBatches.any { it.expirationDay == Int.MAX_VALUE && it.quantity > 0 }
        }
        val avgZone = result.avgZoneScore
        if (hasBackroomStock) {
            val before = result.hiredEntityRegistry.totalCount()
            result = tryAutoHire(result, EntityDef.STOCKER)
            if (result.hiredEntityRegistry.totalCount() > before) {
                events += AutoHireEvent(EntityDef.STOCKER.displayName, "Cases still in backroom")
            }
        } else if (avgZone < 0.8f) {
            val before = result.hiredEntityRegistry.totalCount()
            result = tryAutoHire(result, EntityDef.STOCKER)
            if (result.hiredEntityRegistry.totalCount() > before) {
                events += AutoHireEvent(EntityDef.STOCKER.displayName, "Zone score ${(avgZone * 100).toInt()}% — below 80%")
            }
        }

        // Fresh handler auto-hire: only when OOS items have no pending orders
        val unorderedOosIds = freshOosIds - freshOrderedIds
        if (unorderedOosIds.isNotEmpty()) {
            val freshIds = result.hiredEntityRegistry.getByDef(EntityDef.FRESH_HANDLER).map { it.id }.toSet()
            val freshShifts = result.staffSchedules.filter { it.entityId in freshIds }
            val hasUncoveredFreshHours = (6..20).any { hour -> freshShifts.none { it.isOnShift(hour) } }

            if (hasSeniorManager && !hasUncoveredFreshHours && metrics.avgFreshUtilization < 1.0f) {
                events += AutoHireEvent(
                    EntityDef.FRESH_HANDLER.displayName,
                    "Fresh items out of stock",
                    blocked = true,
                    blockReason = "Utilization ${(metrics.avgFreshUtilization * 100).toInt()}% — reassign idle handlers",
                )
            } else {
                val before = result.hiredEntityRegistry.totalCount()
                result = tryAutoHire(result, EntityDef.FRESH_HANDLER)
                if (result.hiredEntityRegistry.totalCount() > before) {
                    events += AutoHireEvent(EntityDef.FRESH_HANDLER.displayName, "Fresh items out of stock")
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

    // ── Store Manager Auto-Actions ─────────────────────────────────────────

    fun evaluateStoreManagerActions(state: GameState): GameState {
        val hasStoreManager = state.hiredEntityRegistry.getByDef(EntityDef.MANAGER)
            .any { it.tier == Tier.MANAGER }
        if (!hasStoreManager) return state

        var result = state
        val events = mutableListOf<AutoHireEvent>()

        // 1. Buy register if all registers are manned and more cashiers could use one
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
                    ownedRegisterCount = result.ownedRegisterCount + 1,
                    registers = result.registers + RegisterState(registerId = newRegisterId),
                )
                events += AutoHireEvent("Register", "Store Manager purchased register #${result.ownedRegisterCount}")
            }
        }

        // 2. Rebalance shifts to fill coverage gaps
        for (def in listOf(EntityDef.CASHIER, EntityDef.STOCKER, EntityDef.FRESH_HANDLER)) {
            val entityIds = result.hiredEntityRegistry.getByDef(def).map { it.id }.toSet()
            val shifts = result.staffSchedules.filter { it.entityId in entityIds }
            if (shifts.size < 2) continue

            val coverageByHour = (6..20).associateWith { hour ->
                shifts.count { it.isOnShift(hour) }
            }
            val minCoverage = coverageByHour.values.min()
            val maxCoverage = coverageByHour.values.max()
            if (maxCoverage - minCoverage <= 1) continue

            // Find the most overstaffed shift and move one employee to the most understaffed
            val presets = listOf(SHIFT_MORNING, SHIFT_MID, SHIFT_CLOSING)
            val coveragePerShift = presets.associateWith { start ->
                val uncoveredHours = (start until start + 8).count { h -> (coverageByHour[h] ?: 0) == minCoverage }
                uncoveredHours
            }
            val bestTarget = presets.maxByOrNull { coveragePerShift[it] ?: 0 } ?: continue
            val worstSource = presets.minByOrNull { coveragePerShift[it] ?: 0 } ?: continue
            if (bestTarget == worstSource) continue

            val sourceCount = shifts.count { it.startHour == worstSource }
            if (sourceCount <= 1) continue

            val entityToMove = shifts.firstOrNull { it.startHour == worstSource }?.entityId ?: continue
            result = result.copy(
                staffSchedules = result.staffSchedules.map { s ->
                    if (s.entityId == entityToMove) s.copy(startHour = bestTarget) else s
                }
            )
            events += AutoHireEvent(def.displayName, "Store Manager rebalanced shift coverage")
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

    private fun tryAutoHire(state: GameState, def: EntityDef): GameState {
        if (state.money - def.cost < state.autoHireBudget) return state
        return hireEntity(state, def)
    }

    // ── Pure state operations ─────────────────────────────────────────────────

    fun hireEntity(state: GameState, def: EntityDef): GameState {
        if (state.money < def.cost) return state

        val newRegistry = state.hiredEntityRegistry.hireEntity(def)
        val newEntityId = newRegistry.hiredEntities.last().id

        val presets = listOf(SHIFT_MORNING, SHIFT_MID, SHIFT_CLOSING)
        val currentTypeEntityIds = state.hiredEntityRegistry.getByDef(def).map { it.id }.toSet()
        val existingShifts = state.staffSchedules.filter { it.entityId in currentTypeEntityIds }

        // Count coverage per hour from existing employees of this type
        val coverageByHour = (6..20).associateWith { hour ->
            existingShifts.count { it.isOnShift(hour) }
        }

        // Pick shift that covers the most uncovered hours; break ties by least-staffed
        val bestPreset = presets.maxByOrNull { startHour ->
            val shiftHours = startHour until (startHour + 8)
            val uncoveredHours = shiftHours.count { h -> (coverageByHour[h] ?: 0) == 0 }
            val totalGap = shiftHours.sumOf { h -> 1.0 / ((coverageByHour[h] ?: 0) + 1) }
            uncoveredHours * 100 + totalGap
        } ?: SHIFT_MORNING
        val newShift = StaffShift(entityId = newEntityId, startHour = bestPreset)

        return state.copy(
            hiredEntityRegistry = newRegistry,
            money = state.money - def.cost,
            staffSchedules = state.staffSchedules + newShift,
        )
    }

    fun promoteEntity(state: GameState, entityId: Int): GameState {
        val entity = state.hiredEntityRegistry.getById(entityId)
        if (entity.tier == Tier.MANAGER) return state
        // Only one Store Manager allowed
        if (entity.entityDefinition == EntityDef.MANAGER && entity.tier == Tier.FAST) {
            val hasStoreManager = state.hiredEntityRegistry.getByDef(EntityDef.MANAGER).any { it.tier == Tier.MANAGER }
            if (hasStoreManager) return state
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

    fun updateShift(state: GameState, entityId: Int, newStartHour: Int): GameState {
        if (newStartHour !in 6..13) {
            Log.w(TAG, "updateShift: invalid startHour $newStartHour for entityId $entityId — ignoring")
            return state
        }
        val updatedSchedules = if (state.staffSchedules.any { it.entityId == entityId }) {
            state.staffSchedules.map { shift ->
                if (shift.entityId == entityId) shift.copy(startHour = newStartHour) else shift
            }
        } else {
            state.staffSchedules + StaffShift(entityId = entityId, startHour = newStartHour)
        }
        return state.copy(staffSchedules = updatedSchedules)
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        cashierProgressByRegister.clear()
        stockerProgress = 0f
        freshHandlerProgress = 0f
        zoningByStockerId.clear()
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
    }

    // ── Constants + static helpers ────────────────────────────────────────────

    companion object {
        const val CASHIER_ITEMS_PER_SECOND = 1.0f
        const val STOCKER_ACTIONS_PER_SECOND = 0.15f
        const val FRESH_HANDLER_ACTIONS_PER_SECOND = 0.1f
        const val FALLBACK_CASHIER_KEY = -1

        const val SHIFT_MORNING = 6
        const val SHIFT_MID     = 10
        const val SHIFT_CLOSING = 13

        // Zoning constants
        const val ZONE_DECAY_RATE = 0.005f
        const val ZONE_ACTIONS_PER_SECOND = 0.1f
        const val ZONE_PER_ACTION = 0.35f
        const val ZONE_FLOOR = 0.4f

        private const val TAG = "StaffManager"

        fun unassignedOnShiftCashierCount(
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
            registers: List<RegisterState>,
        ): Int {
            val assignedIds = registers.mapNotNull { it.assignedCashierId }.toSet()
            return registry.getByDef(EntityDef.CASHIER).count { entity ->
                entity.id !in assignedIds &&
                    (schedules.firstOrNull { it.entityId == entity.id }?.isOnShift(currentHour) == true)
            }
        }

        fun unassignedOnShiftCashierWeight(
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
            registers: List<RegisterState>,
        ): Float {
            val assignedIds = registers.mapNotNull { it.assignedCashierId }.toSet()
            return registry.getByDef(EntityDef.CASHIER).sumOf { entity ->
                if (entity.id in assignedIds) return@sumOf 0.0
                val shift = schedules.firstOrNull { it.entityId == entity.id }
                if (shift?.isOnShift(currentHour) == true)
                    (entity.throughputWeight * entity.levelMultiplier * entity.trait.throughputMultiplier).toDouble()
                else 0.0
            }.toFloat()
        }

        fun activeWeightedCount(
            def: EntityDef,
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
        ): Float {
            return registry.getByDef(def).sumOf { entity ->
                val shift = schedules.firstOrNull { it.entityId == entity.id }
                if (shift != null && shift.isOnShift(currentHour))
                    (entity.throughputWeight * entity.levelMultiplier * entity.trait.throughputMultiplier).toDouble()
                else 0.0
            }.toFloat()
        }

        fun computeGlobalBonus(
            playerRole: PlayerRole,
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
        ): Float {
            // Manager bonuses are additive and stack across managers on shift
            val onShiftManagers = registry.getByDef(EntityDef.MANAGER).filter { e ->
                schedules.any { s -> s.entityId == e.id && s.isOnShift(currentHour) }
            }
            val managerBonus = onShiftManagers.sumOf { mgr ->
                when (mgr.tier) {
                    Tier.BASE -> 0.15
                    Tier.FAST -> 0.25
                    Tier.MANAGER -> 0.30
                }
            }.toFloat()

            var bonus = 1.0f + managerBonus

            // Player manage bonus is multiplicative
            if (playerRole == PlayerRole.MANAGE) {
                bonus *= 1.10f
            }

            return bonus
        }

        fun deptManagerBonus(
            def: EntityDef,
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
        ): Float {
            val hasOnShiftDeptManager = registry.getByDef(def).any { entity ->
                entity.tier == Tier.MANAGER &&
                    schedules.any { s -> s.entityId == entity.id && s.isOnShift(currentHour) }
            }
            return if (hasOnShiftDeptManager) 1.10f else 1.0f
        }

        fun zonePurchaseMultiplier(zoneScore: Float): Float =
            ZONE_FLOOR + (zoneScore * (1.0f - ZONE_FLOOR))
    }
}
