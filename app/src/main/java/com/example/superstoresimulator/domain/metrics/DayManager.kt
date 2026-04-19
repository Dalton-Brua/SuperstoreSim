package com.example.superstoresimulator.domain.metrics

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.store.StaffWageCalculator

/**
 * Sub-system responsible for day-boundary management and the end-of-day report lifecycle.
 *
 * This is a pure sub-system: [rollOverDay] and [dismissEndOfDayReport] each receive a
 * [GameState] and return a new [GameState]. They never hold a reference to the engine's
 * mutable state or its change-emission stream — [GameEngine] retains those responsibilities.
 *
 * The one piece of mutable state owned here is [lastKnownDayNumber], an accumulator
 * that [GameEngine.tick] uses to detect when the game clock has crossed midnight.
 * It is intentionally kept outside [GameState] (like the cashier/stocker progress
 * accumulators) because it is an engine-internal counter, not player-visible data.
 *
 * Responsibilities:
 *  - Tracking the last processed day number to detect midnight rollovers
 *  - Snapshotting the live [DailyMetricsAccumulator] into an immutable [DailyMetrics]
 *  - Appending the snapshot to [GameState.completedDayMetrics]
 *  - Auto-pausing time for the end-of-day report (while respecting a player's manual pause)
 *  - Clearing the report flag and restoring the pause state when the player dismisses it
 */
class DayManager {

    /**
     * The last [GameState.currentTime.dayNumber] that [GameEngine.tick] has fully
     * processed. When the current day number differs from this value, a midnight
     * rollover has occurred and [rollOverDay] must be called.
     *
     * Starts at 0 to match [GameState.currentTime.dayNumber] at game start
     * ([GameTime(totalMinutesElapsed = 0)] → dayNumber = 0).
     */
    var lastKnownDayNumber: Int = 0
        private set

    /**
     * Called by [GameEngine.tick] immediately after [rollOverDay] to advance the
     * tracked day so the same rollover is not triggered again next tick.
     */
    fun advanceDay(newDayNumber: Int) {
        lastKnownDayNumber = newDayNumber
    }

    /**
     * Sync the internal day counter to match a loaded game state.
     * Used when restoring saved games to prevent double-triggering day rollover.
     */
    fun syncDay(dayNumber: Int) {
        lastKnownDayNumber = dayNumber
    }

    /**
     * Snapshot today's [DailyMetricsAccumulator] into an immutable [DailyMetrics]
     * record, append it to [GameState.completedDayMetrics], start a fresh accumulator
     * for the new day, and surface the end-of-day report.
     *
     * Auto-pauses time unless the player already has time paused — in which case
     * [GameState.pausedByEndOfDay] is set to `false` so [dismissEndOfDayReport] will
     * not accidentally unpause what the player explicitly paused.
     *
     * @param dayNumber The day number that just *finished* (i.e., [lastKnownDayNumber]
     *                  at the moment the rollover was detected).
     */
    fun rollOverDay(state: GameState, dayNumber: Int): GameState {
        // Calculate operating costs
        val rentCost = state.currentStoreSize.dailyRent
        val wagesCost = StaffWageCalculator.calculateTotalWages(state.hiredEntityRegistry)

        // Update metrics with operating costs
        val metricsWithCosts = state.currentDayMetrics.copy(
            rentPaid = rentCost,
            wagesPaid = wagesCost,
        )

        // Snapshot metrics
        val snapshot = metricsWithCosts.toSnapshot(dayOfWeek = dayNumber % 7)

        // Deduct operating costs
        val newCash = state.money - rentCost - wagesCost

        val wasAlreadyPaused = state.playerPausedTime
        return state.copy(
            money = newCash,
            completedDayMetrics = state.completedDayMetrics + snapshot,
            currentDayMetrics = DailyMetricsAccumulator(dayNumber = dayNumber + 1),
            showEndOfDayReport = true,
            lastEndOfDayReport = snapshot,
            playerPausedTime = true,
            pausedByEndOfDay = !wasAlreadyPaused,
        )
    }

    /**
     * Clear the end-of-day report flag and restore the correct time-running state.
     *
     * Only resumes time if the engine auto-paused it ([GameState.pausedByEndOfDay] is
     * `true`). If the player had already paused time before the rollover, their pause
     * is preserved.
     */
    fun dismissEndOfDayReport(state: GameState): GameState = state.copy(
        showEndOfDayReport = false,
        playerPausedTime = if (state.pausedByEndOfDay) false else state.playerPausedTime,
        pausedByEndOfDay = false,
    )
}

