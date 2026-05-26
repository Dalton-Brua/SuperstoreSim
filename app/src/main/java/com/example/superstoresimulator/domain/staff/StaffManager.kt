package com.example.superstoresimulator.domain.staff

import android.util.Log
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.StaffShift

/**
 * Sub-system responsible for hired-staff management and staff work-tick accounting.
 *
 * Pure state operations ([hireEntity], [upgradeEntity], [fireEntity]) each receive a
 * [GameState] and return a new [GameState]. They never hold a reference to the
 * engine's mutable state or its change-emission stream — [GameEngine] retains those.
 *
 * The two fractional-accumulator fields ([cashierProgress], [stockerProgress]) live
 * outside [GameState] for the same reason as [DayManager.lastKnownDayNumber]: they
 * are internal engine counters, not player-visible data. The tick-advance helpers
 * ([advanceCashierProgress], [advanceStockerProgress]) mutate them and return the
 * whole-action count for the tick; [GameEngine.tick] then executes those actions.
 *
 * Responsibilities:
 *  - Hire / upgrade / fire hired entities, deducting cost from [GameState.money]
 *  - Advance the cashier ring-up accumulator each tick and return ready-action count
 *  - Advance the stocker stocking accumulator each tick and return ready-action count
 */
class StaffManager {

    /**
     * Fractional cashier ring-up accumulator.
     * Accrues partial item counts each tick; whole items are returned to the caller.
     */
    private var cashierProgress: Float = 0f

    /**
     * Fractional stocker stocking accumulator.
     * Accrues partial case-pack counts each tick; whole case-packs are returned to the caller.
     */
    private var stockerProgress: Float = 0f

    /**
     * Fractional fresh handler stocking accumulator.
     * Accrues partial case-pack counts each tick; whole case-packs are returned to the caller.
     */
    private var freshHandlerProgress: Float = 0f

    // ── Tick-advance helpers ──────────────────────────────────────────────────

    /**
     * Advance the cashier ring-up accumulator by one tick.
     *
     * Rate: 0.5 items/second per weighted active cashier.
     * [cashierCount] is a weighted Float (from [activeWeightedCount]) rather than a raw head count
     * so that FAST_CASHIER (weight 2.0) is counted correctly.
     *
     * Returns the number of whole ring-up actions [GameEngine.tick] should perform
     * this frame via `repeat(result) { ringUpItem() }`.  Returns 0 immediately when
     * [cashierCount] is zero so no accumulation occurs.
     */
    fun advanceCashierProgress(cashierCount: Float, delta: Double, multiplier: Float): Int {
        if (cashierCount <= 0f) return 0
        cashierProgress += CASHIER_ITEMS_PER_SECOND * cashierCount * delta.toFloat() * multiplier
        val whole = cashierProgress.toInt()
        cashierProgress -= whole
        return whole
    }

    /**
     * Advance the stocker stocking accumulator by one tick.
     *
     * Rate: 0.1 case-packs/second per weighted active stocker.
     * [stockerCount] is a weighted Float (from [activeWeightedCount]) so FAST_STOCKER
     * contributes 2× the work per tick.
     *
     * Returns the number of whole stocking actions [GameEngine.tick] should perform
     * this frame via `repeat(result) { stockRandomItemFromBackroom() }`.  Returns 0
     * immediately when [stockerCount] is zero so no accumulation occurs.
     */
    fun advanceStockerProgress(stockerCount: Float, delta: Double, multiplier: Float): Int {
        if (stockerCount <= 0f) return 0
        stockerProgress += STOCKER_ACTIONS_PER_SECOND * stockerCount * delta.toFloat() * multiplier
        val whole = stockerProgress.toInt()
        stockerProgress -= whole
        return whole
    }

    /**
     * Advance the fresh handler stocking accumulator by one tick.
     *
     * Rate: 0.1 case-packs/second per weighted active fresh handler.
     * [freshHandlerCount] is a weighted Float (from [activeWeightedCount]) so
     * FAST_FRESH_HANDLER contributes 2× the work per tick.
     *
     * Returns the number of whole stocking actions [GameEngine.tick] should perform
     * this frame via `repeat(result) { stockRandomFreshItemFromBackroom() }`.  Returns 0
     * immediately when [freshHandlerCount] is zero so no accumulation occurs.
     */
    fun advanceFreshHandlerProgress(freshHandlerCount: Float, delta: Double, multiplier: Float): Int {
        if (freshHandlerCount <= 0f) return 0
        freshHandlerProgress += FRESH_HANDLER_ACTIONS_PER_SECOND * freshHandlerCount * delta.toFloat() * multiplier
        val whole = freshHandlerProgress.toInt()
        freshHandlerProgress -= whole
        return whole
    }

    // ── Pure state operations ─────────────────────────────────────────────────

    /**
     * Hire a new entity of the given [def] and [type], deducting [EntityDef.cost]
     * from [GameState.money], and automatically assigning a default shift.
     *
     * Shift presets (startHour): Morning = 6, Mid = 10, Closing = 13.
     * The preset with the fewest existing assignments for this [EntityType] is chosen.
     * Tie-break: Morning (6).
     *
     * Returns the state unchanged if the player cannot afford the hire cost.
     */
    fun hireEntity(state: GameState, def: EntityDef, type: EntityType): GameState {
        if (state.money < def.cost) return state

        val newRegistry = state.hiredEntityRegistry.hireEntity(def, type)
        val newEntityId = newRegistry.hiredEntities.last().id

        // Pick the least-populated shift preset for this EntityType
        val presets = listOf(SHIFT_MORNING, SHIFT_MID, SHIFT_CLOSING)
        val currentTypeEntityIds = state.hiredEntityRegistry.getByType(type).map { it.id }.toSet()
        val countsPerPreset = presets.associateWith { preset ->
            state.staffSchedules.count { it.entityId in currentTypeEntityIds && it.startHour == preset }
        }
        // minByOrNull iterates in list order → tie-break is Morning (6)
        val bestPreset = presets.minByOrNull { countsPerPreset[it] ?: 0 } ?: SHIFT_MORNING
        val newShift = StaffShift(entityId = newEntityId, startHour = bestPreset)

        return state.copy(
            hiredEntityRegistry = newRegistry,
            money = state.money - def.cost,
            staffSchedules = state.staffSchedules + newShift,
        )
    }

    /**
     * Upgrade an existing entity identified by [entityId] to its next definition tier,
     * deducting [EntityDef.nextUpgrade.cost] from [GameState.money].
     *
     * Returns the state unchanged if the player cannot afford the upgrade cost.
     */
    fun upgradeEntity(state: GameState, entityId: Int): GameState {
        val cost = state.hiredEntityRegistry
            .getById(entityId)
            .entityDefinition
            .nextUpgrade?.cost ?: Money(0)
        if (state.money < cost) return state
        return state.copy(
            hiredEntityRegistry = state.hiredEntityRegistry.upgradeEntity(entityId),
            money = state.money - cost,
        )
    }

    /**
     * Remove the entity identified by [entityId] from the registry and remove
     * their shift from [GameState.staffSchedules].
     *
     * This operation never fails and does not affect [GameState.money].
     */
    fun fireEntity(state: GameState, entityId: Int): GameState =
        state.copy(
            hiredEntityRegistry = state.hiredEntityRegistry.fireEntity(entityId),
            staffSchedules = state.staffSchedules.filter { it.entityId != entityId },
        )

    /**
     * Update the shift start hour for employee [entityId].
     *
     * Safe policy: if [newStartHour] is outside 6..13,
     * a warning is logged and the state is returned unchanged (no throw).
     * If the employee has no existing shift, a new one is created.
     */
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

    // ── Reset and constants ─────────────────────────────────────────────────────────────

    /**
     * Reset the internal fractional accumulators.
     * Used when loading saved games to start with a clean slate.
     */
    fun reset() {
        cashierProgress = 0f
        stockerProgress = 0f
        freshHandlerProgress = 0f
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        const val CASHIER_ITEMS_PER_SECOND = 0.5f
        const val STOCKER_ACTIONS_PER_SECOND = 0.1f
        const val FRESH_HANDLER_ACTIONS_PER_SECOND = 0.1f

        /** Shift preset start hours (inclusive, 24-hour clock). */
        const val SHIFT_MORNING = 6
        const val SHIFT_MID     = 10
        const val SHIFT_CLOSING = 13

        private const val TAG = "StaffManager"

        /**
         * Returns the sum of throughput weights for all employees of [type] who are
         * currently on shift at [currentHour].
         *
         * Each entity's weight is determined by [EntityDef.baseThroughputWeight]:
         *  - Base staff (CASHIER, STOCKER, FRESH_HANDLER) → 1.0
         *  - Fast-tier (FAST_CASHIER, FAST_STOCKER, FAST_FRESH_HANDLER) → 2.0
         *  - Department managers (FRONT_END_MANAGER, STOCKING_MANAGER) → 3.0
         *
         * An employee with no shift entry is treated as off-shift (contributes 0).
         *
         * Example: 1 CASHIER on shift + 1 FAST_CASHIER on shift = 3.0
         */
        fun activeWeightedCount(
            type: EntityType,
            currentHour: Int,
            schedules: List<StaffShift>,
            registry: HiredEntityRegistry,
        ): Float {
            return registry.getByType(type).sumOf { entity ->
                val shift = schedules.firstOrNull { it.entityId == entity.id }
                if (shift != null && shift.isOnShift(currentHour)) {
                    EntityDef.baseThroughputWeight(entity.entityDefinition).toDouble()
                } else {
                    0.0
                }
            }.toFloat()
        }
    }
}
