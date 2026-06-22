package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.GameState

/**
 * TRACK D (Operate-a-Store) — fill the bodies. Wave-0 stub.
 *
 * Lets the player drop into any secondary store and run it with full loop-1 machinery,
 * then return to empire mode. The two time models never run at once.
 *
 * [dropIn]:
 *  1. Pause the empire clock (EmpireSpeed.PAUSED).
 *  2. Set [GameState.operatingStoreId] = storeId.
 *  3. Load the store's loop-1 fields into GameState (inventory, registers, staff, trucks):
 *     resume from store.operatingState if present, else RECONSTRUCT plausible state from
 *     size + upgrades + direction so any store is operable.
 *  4. Resume the minute tick scoped to this one store (loop 1 plays as today).
 *  Record session baseline (sim profit rate for this store's size+direction) for scoring.
 *
 * [dropOut]:
 *  1. Measure realized performance vs the store's sim baseline → operatingPerformance,
 *     clamped to [EmpireTuning.OPERATING_PERFORMANCE_MIN..MAX].
 *  2. Set lastOperatedDay = current day index.
 *  3. Snapshot GameState loop-1 fields back into store.operatingState.
 *  4. Clear operatingStoreId; resume the empire clock.
 *
 * [decayOperatingPerformance]: drift each store's operatingPerformance toward 1.0 over
 * [EmpireTuning.OPERATING_PERFORMANCE_DECAY_DAYS] since lastOperatedDay (called by the sim).
 */
object OperateStoreController {

    fun dropIn(state: GameState, storeId: Int): GameState {
        // TODO(Track D)
        return state
    }

    fun dropOut(state: GameState): GameState {
        // TODO(Track D)
        return state
    }

    /** Decay the operating-performance boost back toward baseline. Called per sim day. */
    fun decayOperatingPerformance(store: SecondaryStore, currentDayIndex: Int): SecondaryStore {
        // TODO(Track D)
        return store
    }
}
