package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.GameState

/**
 * TRACK A (Sim Engine) — fill the bodies. Wave-0 stub: no-ops that compile.
 *
 * Regional manager hire/fire + the saturation-aware day-rollover assignment algorithm.
 *
 * [assignDirections] (runs at day rollover, before the sim, only if a manager is hired):
 *  1. Tentatively set EVERY managed store to manager.personality.preferred.
 *  2. Estimate empire net profit (sum [SecondaryStoreSimManager.estimateStoreNetProfit]
 *       per store, accounting for each region's saturationFactor at the tentative weights,
 *       minus manager salary).
 *  3. While empire net profit < [EmpireTuning.MANAGER_PROFIT_FLOOR]: sort stores by how
 *       much they hurt the bottom line on the preferred direction (worst first — an
 *       AGGRESSIVE store crowding its region shows up here), flip the worst to a
 *       profit-preserving direction (PASSIVE/DEFENSIVE) one at a time, re-estimating.
 *  4. Leave the max possible stores on the preferred direction; mark all managed stores
 *       managedByRegionalManager = true with the assigned direction.
 */
object RegionalManagerManager {

    /** Hire a manager of [personality] (gated by store count + affordability). */
    fun hire(state: GameState, personality: ManagerPersonality): GameState {
        // TODO(Track A): gate on MANAGER_HIRE_MIN_STORES, set regionalManager, flag stores.
        return state
    }

    /** Fire the manager; return direction control to the player. */
    fun fire(state: GameState): GameState {
        // TODO(Track A): clear regionalManager, set managedByRegionalManager = false on all.
        return state
    }

    /** Reassign directions across managed stores (called by the sim each day). */
    fun assignDirections(state: GameState): GameState {
        // TODO(Track A): profit-floor + max-preferred, saturation-aware (see class doc).
        return state
    }
}
