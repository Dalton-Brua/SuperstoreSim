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

    /**
     * Set empire speed (PAUSED/NORMAL/FAST). Acknowledges the current pending decision so the
     * same situation does not immediately re-pause; a genuinely new one still will.
     */
    fun setSpeed(state: GameState, speed: EmpireSpeed): GameState {
        val clock = state.empireClock
        val acked = clock.pendingDecisionSignature
            ?.let { clock.acknowledgedSignatures + it }
            ?: clock.acknowledgedSignatures
        return state.copy(
            empireClock = clock.copy(
                speed = speed,
                pendingDecisionReason = null,
                pendingDecisionSignature = null,
                acknowledgedSignatures = acked,
            ),
        )
    }

    /** Real-ms driver. Advances whole sim days as elapsed time allows. */
    fun tick(state: GameState, deltaMs: Long): GameState {
        val clock = state.empireClock
        if (!state.empireModeActive ||
            state.operatingStoreId != null ||
            clock.speed == EmpireSpeed.PAUSED ||
            clock.pendingDecisionReason != null
        ) {
            return state
        }

        val msPerDay =
            if (clock.speed == EmpireSpeed.FAST) EmpireTuning.MS_PER_DAY_FAST else EmpireTuning.MS_PER_DAY_NORMAL

        var acc = clock.msAccumulator + deltaMs
        var s = state
        while (acc >= msPerDay) {
            acc -= msPerDay
            s = advanceOneDay(s)
            if (s.empireClock.pendingDecisionReason != null) break
        }
        return s.copy(empireClock = s.empireClock.copy(msAccumulator = acc))
    }

    /** Advance exactly one simulated day: bump GameTime by 1440 min, run the sim, check pauses. */
    fun advanceOneDay(state: GameState): GameState {
        val newTime = state.currentTime.addMinutes(1440)
        var s = state.copy(currentTime = newTime)
        s = SecondaryStoreSimManager.simulateDay(s)
        return applyDecisionPause(s)
    }

    /**
     * Auto-pause for the first active decision the player hasn't already acknowledged. Stale
     * acknowledgements (conditions that have since cleared) are pruned so a recurrence re-fires.
     */
    private fun applyDecisionPause(state: GameState): GameState {
        val active = activeDecisions(state)
        val activeKeys = active.map { it.signature }.toSet()
        // Keep only acks whose condition still holds; the rest re-arm.
        val acked = state.empireClock.acknowledgedSignatures intersect activeKeys
        val fresh = active.firstOrNull { it.signature !in acked }

        return if (fresh == null) {
            state.copy(empireClock = state.empireClock.copy(acknowledgedSignatures = acked))
        } else {
            state.copy(
                empireClock = state.empireClock.copy(
                    speed = EmpireSpeed.PAUSED,
                    pendingDecisionReason = fresh.reason,
                    pendingDecisionSignature = fresh.signature,
                    acknowledgedSignatures = acked,
                ),
            )
        }
    }

    /** A fired decision condition: a stable [signature] and the player-facing [reason]. */
    private data class Decision(val signature: String, val reason: String)

    /**
     * Every decision condition currently firing, in priority order (losses, then saturation,
     * then affordability). Signatures are per-store / per-region / per-location-number so each
     * distinct situation is tracked independently.
     */
    private fun activeDecisions(state: GameState): List<Decision> {
        val out = mutableListOf<Decision>()

        state.secondaryStores
            .filter { it.currentDayMetrics.netProfit.cents < 0 }
            .forEach { out += Decision("loss:${it.storeId}", "${it.storeName} is operating at a loss.") }

        state.regions.forEach { region ->
            val weight = state.secondaryStores
                .filter { it.regionId == region.regionId }
                .sumOf { SecondaryStoreSimManager.storeWeight(it).toDouble() }
            if (region.capacity > 0f && weight / region.capacity > 1.0f) {
                out += Decision("sat:${region.regionId}", "${region.name} has tipped into saturation.")
            }
        }

        val nextLocation = state.secondaryStores.size + 1
        if (state.money >= EmpireTuning.locationCost(nextLocation)) {
            out += Decision("afford:$nextLocation", "You can afford a new location.")
        }

        return out
    }

    /** Skip-ahead: advance days until a *new* (unacknowledged) decision fires (bounded). */
    fun advanceToNextDecision(state: GameState): GameState {
        if (!state.empireModeActive) return state
        val clock = state.empireClock
        // Acknowledge the current pause so the skip doesn't stop on it again immediately.
        val acked = clock.pendingDecisionSignature
            ?.let { clock.acknowledgedSignatures + it }
            ?: clock.acknowledgedSignatures
        var s = state.copy(
            empireClock = clock.copy(
                pendingDecisionReason = null,
                pendingDecisionSignature = null,
                acknowledgedSignatures = acked,
            ),
        )
        repeat(365) {
            s = advanceOneDay(s)
            if (s.empireClock.pendingDecisionReason != null) return s
        }
        return s
    }

    /**
     * Build a weekly/monthly digest from secondary-store completedDayMetrics for the
     * batched-reporting UI. Returns a value object Track E renders (define as needed).
     */
    fun buildDigest(state: GameState): EmpireDigest {
        val stores = state.secondaryStores
        if (stores.isEmpty()) return EmpireDigest(periodLabel = "Last 7 days")

        var totalRevenue = 0L
        var totalNetProfit = 0L
        var bestStoreName: String? = null
        var worstStoreName: String? = null
        var bestNet = Long.MIN_VALUE
        var worstNet = Long.MAX_VALUE

        for (store in stores) {
            val window = store.completedDayMetrics.takeLast(7)
            val storeNet = window.sumOf { it.netProfit.cents }
            totalRevenue += window.sumOf { it.revenue.cents }
            totalNetProfit += storeNet

            if (storeNet > bestNet) {
                bestNet = storeNet
                bestStoreName = store.storeName
            }
            if (storeNet < worstNet) {
                worstNet = storeNet
                worstStoreName = store.storeName
            }
        }

        return EmpireDigest(
            periodLabel = "Last 7 days",
            totalRevenueCents = totalRevenue,
            totalNetProfitCents = totalNetProfit,
            bestStoreName = bestStoreName,
            worstStoreName = worstStoreName,
        )
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
