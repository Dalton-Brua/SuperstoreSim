package com.example.superstoresimulator.domain.empire

import kotlinx.serialization.Serializable

/** Empire-mode time speed. Day is the atom; minutes are not rendered in loop 2. */
@Serializable
enum class EmpireSpeed { PAUSED, NORMAL, FAST }

/**
 * Decision-driven empire clock. When [GameState.empireModeActive] the minute tick is
 * suspended and the day is advanced at [speed]. [pendingDecisionReason] is set when the
 * clock auto-pauses because the player's input matters (store unprofitable, region
 * saturated, cash milestone, manager quit).
 */
@Serializable
data class EmpireClock(
    val speed: EmpireSpeed = EmpireSpeed.PAUSED,
    val pendingDecisionReason: String? = null,
    /** Real-ms accumulator toward the next simulated day. Not a hard contract; driver-owned. */
    val msAccumulator: Long = 0L,
)
