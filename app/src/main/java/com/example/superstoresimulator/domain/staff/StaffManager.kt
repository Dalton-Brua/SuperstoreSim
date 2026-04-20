package com.example.superstoresimulator.domain.staff

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money

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
     * Rate: 0.5 items/second per hired cashier.
     *
     * Returns the number of whole ring-up actions [GameEngine.tick] should perform
     * this frame via `repeat(result) { ringUpItem() }`.  Returns 0 immediately when
     * [cashierCount] is zero so no accumulation occurs.
     */
    fun advanceCashierProgress(cashierCount: Int, delta: Double, multiplier: Float): Int {
        if (cashierCount <= 0) return 0
        cashierProgress += CASHIER_ITEMS_PER_SECOND * cashierCount * delta.toFloat() * multiplier
        val whole = cashierProgress.toInt()
        cashierProgress -= whole
        return whole
    }

    /**
     * Advance the stocker stocking accumulator by one tick.
     *
     * Rate: 0.1 case-packs/second per hired stocker.
     *
     * Returns the number of whole stocking actions [GameEngine.tick] should perform
     * this frame via `repeat(result) { stockRandomItemFromBackroom() }`.  Returns 0
     * immediately when [stockerCount] is zero so no accumulation occurs.
     */
    fun advanceStockerProgress(stockerCount: Int, delta: Double, multiplier: Float): Int {
        if (stockerCount <= 0) return 0
        stockerProgress += STOCKER_ACTIONS_PER_SECOND * stockerCount * delta.toFloat() * multiplier
        val whole = stockerProgress.toInt()
        stockerProgress -= whole
        return whole
    }

    /**
     * Advance the fresh handler stocking accumulator by one tick.
     *
     * Rate: 0.1 case-packs/second per hired fresh handler.
     *
     * Returns the number of whole stocking actions [GameEngine.tick] should perform
     * this frame via `repeat(result) { stockRandomFreshItemFromBackroom() }`.  Returns 0
     * immediately when [freshHandlerCount] is zero so no accumulation occurs.
     */
    fun advanceFreshHandlerProgress(freshHandlerCount: Int, delta: Double, multiplier: Float): Int {
        if (freshHandlerCount <= 0) return 0
        freshHandlerProgress += FRESH_HANDLER_ACTIONS_PER_SECOND * freshHandlerCount * delta.toFloat() * multiplier
        val whole = freshHandlerProgress.toInt()
        freshHandlerProgress -= whole
        return whole
    }

    // ── Pure state operations ─────────────────────────────────────────────────

    /**
     * Hire a new entity of the given [def] and [type], deducting [EntityDef.cost]
     * from [GameState.money].
     *
     * Returns the state unchanged if the player cannot afford the hire cost.
     */
    fun hireEntity(state: GameState, def: EntityDef, type: EntityType): GameState {
        if (state.money < def.cost) return state
        return state.copy(
            hiredEntityRegistry = state.hiredEntityRegistry.hireEntity(def, type),
            money = state.money - def.cost,
        )
    }

    /**
     * Upgrade an existing entity identified by [entityId] to its next definition tier,
     * deducting [EntityDef.nextUpgrade.cost] from [GameState.money].
     *
     * Returns the state unchanged if the player cannot afford the upgrade cost.
     * When the entity is already at its maximum tier, [EntityDef.nextUpgrade] is null
     * and the effective cost is [Money.ZERO]; the guard therefore passes and the
     * registry call is a no-op (the entity keeps its current definition).
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
     * Remove the entity identified by [entityId] from the registry.
     * This operation never fails and does not affect [GameState.money].
     */
    fun fireEntity(state: GameState, entityId: Int): GameState =
        state.copy(hiredEntityRegistry = state.hiredEntityRegistry.fireEntity(entityId))

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
    }
}

