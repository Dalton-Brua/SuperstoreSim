package com.example.superstoresimulator.domain.metrics

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.store.StaffWageCalculator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayManager @Inject constructor() {

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
     * Snapshot today's [DailyMetrics] into a completed record, append it to
     * [GameState.completedDayMetrics], start a fresh accumulator for the new day,
     * and surface the end-of-day report.
     *
     * Auto-pauses time unless the player already has time paused — in which case
     * [GameState.pausedByEndOfDay] is set to `false` so [dismissEndOfDayReport] will
     * not accidentally unpause what the player explicitly paused.
     *
     * @param dayNumber The day number that just *finished* (i.e., [lastKnownDayNumber]
     *                  at the moment the rollover was detected).
     */
    fun rollOverDay(state: GameState, dayNumber: Int): GameState {
        val processedState = state

        // Calculate operating costs
        val rentCost = processedState.currentStoreSize.dailyRent
        val wagesCost = StaffWageCalculator.calculateTotalWages(processedState.hiredEntityRegistry, processedState.staffSchedules)

        // Update metrics with operating costs and pricing snapshot
        val metricsWithCosts = processedState.currentDayMetrics.copy(
            rentPaid = rentCost,
            wagesPaid = wagesCost,
            itemsMarkedDown = processedState.pricingState.activeMarkdowns.size,
        )

        // Snapshot metrics
        val snapshot = metricsWithCosts.copy(dayOfWeek = dayNumber % 7)

        // Deduct operating costs
        val newCash = processedState.money - rentCost - wagesCost

        val wasAlreadyPaused = processedState.playerPausedTime
        return processedState.copy(
            money = newCash,
            completedDayMetrics = processedState.completedDayMetrics + snapshot,
            currentDayMetrics = DailyMetrics(dayNumber = dayNumber + 1),
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

