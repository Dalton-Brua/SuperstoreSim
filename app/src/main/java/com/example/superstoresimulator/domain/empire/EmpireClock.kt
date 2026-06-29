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
    /**
     * Stable identity of the condition behind [pendingDecisionReason] (e.g. "loss:5",
     * "afford:3"). Distinguishes "same store still losing" from "a second store now losing".
     */
    val pendingDecisionSignature: String? = null,
    /**
     * Signatures the player has already seen and resumed past. A condition only auto-pauses
     * while its signature is absent here; it is dropped from this set once the condition
     * clears, so a recovered-then-relapsed condition pauses again.
     */
    val acknowledgedSignatures: Set<String> = emptySet(),
    /** Real-ms accumulator toward the next simulated day. Not a hard contract; driver-owned. */
    val msAccumulator: Long = 0L,
)
