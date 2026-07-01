package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.TruckConfig

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
        val store = state.secondaryStores.firstOrNull { it.storeId == storeId } ?: return state

        // ponytail: minimal reconstruction; richer seeding can come later
        val op = store.operatingState ?: StoreOperatingState(
            inventory = emptyMap(),
            registers = listOf(RegisterState(registerId = 0)),
            hiredEntityRegistry = HiredEntityRegistry(),
            staffSchedules = emptyList(),
            truckConfig = TruckConfig(),
        )

        return state.copy(
            operatingStoreId = storeId,
            empireClock = state.empireClock.copy(speed = EmpireSpeed.PAUSED),
            playerPausedTime = false, // resume the minute tick for this store
            currentStoreSize = store.storeSize,
            inventory = op.inventory,
            registers = op.registers,
            hiredEntityRegistry = op.hiredEntityRegistry,
            staffSchedules = op.staffSchedules,
            truckConfig = op.truckConfig,
            researchState = op.researchState,
            currentDayMetrics = op.currentDayMetrics,
            completedDayMetrics = op.completedDayMetrics,
            operateSessionStartDay = state.currentTime.dayNumber,
            storeConfig = state.storeConfig.copy(backroomCapPerItem = store.storeSize.backroomCapPerItem),
        )
    }

    fun dropOut(state: GameState): GameState {
        val storeId = state.operatingStoreId ?: return state
        val store = state.secondaryStores.firstOrNull { it.storeId == storeId } ?: return state

        val snapshot = StoreOperatingState(
            inventory = state.inventory,
            registers = state.registers,
            hiredEntityRegistry = state.hiredEntityRegistry,
            staffSchedules = state.staffSchedules,
            truckConfig = state.truckConfig,
            researchState = state.researchState,
            currentDayMetrics = state.currentDayMetrics,
            completedDayMetrics = state.completedDayMetrics,
        )

        // Score realized hands-on profit against the sim baseline, but only once at least one
        // full day has been played — a sub-day visit has no completed day to judge, so leave
        // operatingPerformance untouched. Metrics here are this store's own (loaded on drop-in),
        // so there is no cross-store contamination. ponytail: averaged net over the session's days.
        val startDay = state.operateSessionStartDay
        val daysPlayed = if (startDay != null) (state.currentTime.dayNumber - startDay).coerceAtLeast(0) else 0
        val baseline = SecondaryStoreSimManager.estimateStoreNetProfit(state, store, 1.0f)
        val window = state.completedDayMetrics.takeLast(daysPlayed)
        val perf =
            if (daysPlayed >= 1 && baseline.cents > 0 && window.isNotEmpty()) {
                val avgNet = window.sumOf { it.netRevenue.cents } / window.size
                (avgNet.toFloat() / baseline.cents.toFloat())
                    .coerceIn(EmpireTuning.OPERATING_PERFORMANCE_MIN, EmpireTuning.OPERATING_PERFORMANCE_MAX)
            } else {
                store.operatingPerformance
            }

        val updatedStore = store.copy(
            operatingState = snapshot,
            lastOperatedDay = state.currentTime.dayNumber,
            operatingPerformance = perf,
        )

        return state.copy(
            operatingStoreId = null,
            operateSessionStartDay = null,
            secondaryStores = state.secondaryStores.map { if (it.storeId == storeId) updatedStore else it },
            empireClock = state.empireClock.copy(speed = EmpireSpeed.PAUSED), // player resumes empire clock manually
        )
    }

    /** Decay the operating-performance boost back toward baseline. Called per sim day. */
    fun decayOperatingPerformance(store: SecondaryStore, currentDayIndex: Int): SecondaryStore {
        val last = store.lastOperatedDay ?: return store
        val daysSince = (currentDayIndex - last).coerceAtLeast(0)
        if (daysSince >= EmpireTuning.OPERATING_PERFORMANCE_DECAY_DAYS) {
            return store.copy(operatingPerformance = 1.0f)
        }
        // ponytail: incremental linear decay toward 1.0
        val frac = daysSince / EmpireTuning.OPERATING_PERFORMANCE_DECAY_DAYS.toFloat()
        val newPerf = store.operatingPerformance + (1.0f - store.operatingPerformance) * frac
        return store.copy(operatingPerformance = newPerf)
    }
}
