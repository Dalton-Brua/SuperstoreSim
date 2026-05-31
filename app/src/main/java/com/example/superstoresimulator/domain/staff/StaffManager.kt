package com.example.superstoresimulator.domain.staff

import android.util.Log
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift

class StaffManager {

    private val cashierProgressByRegister: MutableMap<Int, Float> = mutableMapOf()
    private var stockerProgress: Float = 0f
    private var freshHandlerProgress: Float = 0f

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

    // ── Pure state operations ─────────────────────────────────────────────────

    fun hireEntity(state: GameState, def: EntityDef): GameState {
        if (state.money < def.cost) return state

        val newRegistry = state.hiredEntityRegistry.hireEntity(def)
        val newEntityId = newRegistry.hiredEntities.last().id

        val presets = listOf(SHIFT_MORNING, SHIFT_MID, SHIFT_CLOSING)
        val currentTypeEntityIds = state.hiredEntityRegistry.getByDef(def).map { it.id }.toSet()
        val countsPerPreset = presets.associateWith { preset ->
            state.staffSchedules.count { it.entityId in currentTypeEntityIds && it.startHour == preset }
        }
        val bestPreset = presets.minByOrNull { countsPerPreset[it] ?: 0 } ?: SHIFT_MORNING
        val newShift = StaffShift(entityId = newEntityId, startHour = bestPreset)

        return state.copy(
            hiredEntityRegistry = newRegistry,
            money = state.money - def.cost,
            staffSchedules = state.staffSchedules + newShift,
        )
    }

    fun promoteEntity(state: GameState, entityId: Int): GameState {
        val entity = state.hiredEntityRegistry.getById(entityId)
        if (entity.tier == com.example.superstoresimulator.domain.Entities.Tier.MANAGER) return state
        val cost = entity.upgradeCost
        if (state.money < cost) return state
        return state.copy(
            hiredEntityRegistry = state.hiredEntityRegistry.promoteEntity(entityId),
            money = state.money - cost,
        )
    }

    fun fireEntity(state: GameState, entityId: Int): GameState =
        state.copy(
            hiredEntityRegistry = state.hiredEntityRegistry.fireEntity(entityId),
            staffSchedules = state.staffSchedules.filter { it.entityId != entityId },
        )

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
    }

    // ── Constants + static helpers ────────────────────────────────────────────

    companion object {
        const val CASHIER_ITEMS_PER_SECOND = 2.0f
        const val STOCKER_ACTIONS_PER_SECOND = 0.1f
        const val FRESH_HANDLER_ACTIONS_PER_SECOND = 0.1f
        const val FALLBACK_CASHIER_KEY = -1

        const val SHIFT_MORNING = 6
        const val SHIFT_MID     = 10
        const val SHIFT_CLOSING = 13

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
                    entity.throughputWeight.toDouble()
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
    }
}