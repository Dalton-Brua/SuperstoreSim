package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.GameState

/**
 * TRACK B (Opening & Transition) — fill the bodies. Wave-0 stub: no-ops that compile.
 *
 * Owns the empire-mode flip, home-store conversion, region unlock, location scaling
 * cost, opening, per-store upgrade + size-expansion purchases. All paid from the single
 * shared [GameState.money]. Additive — never touches loop-1 home-store fields except to
 * preserve them.
 */
object EmpireTransitionActions {

    /**
     * IRREVERSIBLE. Only call on explicit player confirm (the "Go corporate?" dialog).
     *  1. Derive a SecondaryStore from the home store: home region (id 0, auto-unlocked),
     *     storeSize = currentStoreSize, direction = BALANCED, carry mappable upgrades,
     *     seed currentDayMetrics from recent home performance (or zero), preserve loop-1
     *     detail into its operatingState.
     *  2. Append to secondaryStores; set empireModeActive = true; unlock home region;
     *     seed [GameState.regions] from [RegionRegistry.authored].
     *  3. Pay the 2nd-location scaling cost ([EmpireTuning.locationCost] with locationNumber=2).
     */
    fun enterEmpireMode(state: GameState): GameState {
        // TODO(Track B)
        return state
    }

    /** Unlock [regionId], paying its [Region.unlockCost] from money (no-op if already unlocked). */
    fun unlockRegion(state: GameState, regionId: Int): GameState {
        // TODO(Track B)
        return state
    }

    /**
     * Open a new location in [regionId] (must be unlocked). Pays the next location scaling
     * cost (locationNumber = secondaryStores.size + 1). New store: base size, BALANCED.
     */
    fun openLocation(state: GameState, regionId: Int): GameState {
        // TODO(Track B)
        return state
    }

    /** Player sets a store's direction manually (ignored if managedByRegionalManager). */
    fun setStoreDirection(state: GameState, storeId: Int, direction: StoreDirection): GameState {
        // TODO(Track B)
        return state
    }

    /** Buy a [StoreUpgrade] flag for a store, paying [EmpireTuning.upgradeCost]. */
    fun buyStoreUpgrade(state: GameState, storeId: Int, upgrade: StoreUpgrade): GameState {
        // TODO(Track B)
        return state
    }

    /** Bump a store's StoreSize to the next tier, paying StoreSize.upgradeCost. */
    fun expandStoreSize(state: GameState, storeId: Int): GameState {
        // TODO(Track B)
        return state
    }
}
