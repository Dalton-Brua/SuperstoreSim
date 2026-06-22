package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.GameState

/**
 * TRACK C (Empire Clock) — fill the bodies. Wave-0 stub. The minute tick is suspended
 * when empireModeActive; this drives day-stepped time instead.
 *
 * [tick] is called from the ViewModel tick loop with the real-ms delta when empire mode
 * is active AND no store is being operated by hand. It accumulates ms in
 * [EmpireClock.msAccumulator] and, each time enough real-ms elapse for one sim day at the
 * current speed ([EmpireTuning.MS_PER_DAY_NORMAL] / MS_PER_DAY_FAST), calls
 * [advanceOneDay]. PAUSED → no advancement. After each day, evaluate decision-pause
 * conditions; if any fires, set [EmpireClock.pendingDecisionReason] and flip speed to PAUSED.
 *
 * Decision-pause conditions: a store turns unprofitable, a region tips into saturation,
 * a cash milestone is hit (can afford next location/upgrade), the manager quits/underperforms.
 */
object EmpireClockController {

    /** Set empire speed (PAUSED/NORMAL/FAST); clears any pending decision reason on resume. */
    fun setSpeed(state: GameState, speed: EmpireSpeed): GameState {
        // TODO(Track C)
        return state.copy(empireClock = state.empireClock.copy(speed = speed, pendingDecisionReason = null))
    }

    /** Real-ms driver. Advances whole sim days as elapsed time allows. */
    fun tick(state: GameState, deltaMs: Long): GameState {
        // TODO(Track C)
        return state
    }

    /** Advance exactly one simulated day: bump GameTime by 1440 min, run the sim, check pauses. */
    fun advanceOneDay(state: GameState): GameState {
        // TODO(Track C): currentTime += 1440 min; SecondaryStoreSimManager.simulateDay; decision-pause.
        return state
    }

    /** Skip-ahead: advance days until a decision-pause condition fires (bounded). */
    fun advanceToNextDecision(state: GameState): GameState {
        // TODO(Track C)
        return state
    }

    /**
     * Build a weekly/monthly digest from secondary-store completedDayMetrics for the
     * batched-reporting UI. Returns a value object Track E renders (define as needed).
     */
    fun buildDigest(state: GameState): EmpireDigest {
        // TODO(Track C)
        return EmpireDigest()
    }
}

/** Batched empire reporting summary (weekly/monthly). Track C populates, Track E renders. */
data class EmpireDigest(
    val periodLabel: String = "",
    val totalRevenueCents: Long = 0L,
    val totalNetProfitCents: Long = 0L,
    val bestStoreName: String? = null,
    val worstStoreName: String? = null,
)
