package com.example.superstoresimulator.domain.player

import com.example.superstoresimulator.domain.GameState

/**
 * Result of a single-tick player work calculation.
 *
 * @param actionsToTake Maximum number of actions the player should perform this tick.
 *                      For cashier work this is a ceiling — [GameEngine] still checks
 *                      [GameState.transactionActive] before each ring-up so the actual
 *                      number performed may be less if the transaction completes mid-tick.
 * @param newProgress   The fractional accumulator value after deducting [actionsToTake]
 *                      whole units. The caller is responsible for discarding this and
 *                      storing 0f when a transaction/backroom event terminates the work
 *                      cycle early.
 */
data class PlayerWorkResult(
    val actionsToTake: Int,
    val newProgress: Float,
)

/**
 * Sub-system responsible for player-role management and per-tick player work calculations.
 *
 * This is a pure sub-system: [setPlayerRole] receives a [GameState] and returns a new
 * [GameState]. [calculateCashierWork] and [calculateStockerWork] are also pure — they
 * read from [GameState] and return a [PlayerWorkResult] without performing any actions.
 *
 * The actual ring-up and stocking calls remain in [GameEngine]'s private helpers because
 * they must mutate live state between iterations (checking [GameState.transactionActive]
 * after every ring-up, and [GameState.inventory] after every stocking action).
 *
 * Responsibilities:
 *  - Toggle the player's active role (dispatching the active role again returns to NONE)
 *  - Calculate how many cashier ring-up actions and the new progress fraction for a tick
 *  - Calculate how many stocker stocking actions and the new progress fraction for a tick
 */
class PlayerActionHandler {

    // ── Role management ───────────────────────────────────────────────────────

    /**
     * Set the player's active work role.
     *
     * Passing the currently-active role toggles it OFF (returns to [PlayerRole.NONE]).
     * Both progress accumulators are always zeroed when the role changes so that
     * leftover fractions from the previous role do not bleed into the next one.
     */
    fun setPlayerRole(state: GameState, role: PlayerRole): GameState {
        val newRole = if (state.playerRole == role) PlayerRole.NONE else role

        val newAssignedRegisterId = when (newRole) {
            PlayerRole.CASHIER -> state.registers
                .firstOrNull { it.assignedCashierId == null }
                ?.registerId
            else -> null
        }

        return state.copy(
            playerRole = newRole,
            playerCashierProgress = 0f,
            playerStockerProgress = 0f,
            playerAssignedRegisterId = newAssignedRegisterId,
        )
    }

    // ── Tick work calculations ────────────────────────────────────────────────

    /**
     * Calculate the cashier work the player should perform this tick.
     *
     * Rate: [CASHIER_ITEMS_PER_SECOND] (0.5 items/second).
     *
     * The returned [PlayerWorkResult.actionsToTake] is a **maximum**. The caller
     * ([GameEngine]) must still guard each ring-up with a [GameState.transactionActive]
     * check, because the transaction may complete before all actions are consumed.
     * When that happens the caller should store 0f for the new progress rather than
     * [PlayerWorkResult.newProgress].
     */
    fun calculateCashierWork(state: GameState, deltaSeconds: Double): PlayerWorkResult {
        val multiplier = state.storeConfig.gameSpeedMultiplier
        var progress = state.playerCashierProgress +
                (CASHIER_ITEMS_PER_SECOND * deltaSeconds.toFloat() * multiplier)
        val whole = progress.toInt()
        progress -= whole
        return PlayerWorkResult(
            actionsToTake = whole,
            newProgress = progress.coerceIn(0f, 1f),
        )
    }

    /**
     * Calculate the stocker work the player should perform this tick.
     *
     * Rate: [STOCKER_ACTIONS_PER_SECOND] (0.3 case-packs/second — faster than a
     * hired stocker's 0.1/second because the player is hands-on).
     *
     * Unlike cashier work there is no early-exit mid-loop, but the caller
     * ([GameEngine]) still checks whether the backroom is now empty after all
     * actions are performed and may override [PlayerWorkResult.newProgress] with 0f
     * while also returning the player to [PlayerRole.NONE].
     */
    fun calculateStockerWork(state: GameState, deltaSeconds: Double): PlayerWorkResult {
        val multiplier = state.storeConfig.gameSpeedMultiplier
        var progress = state.playerStockerProgress +
                (STOCKER_ACTIONS_PER_SECOND * deltaSeconds.toFloat() * multiplier)
        val whole = progress.toInt()
        progress -= whole
        return PlayerWorkResult(
            actionsToTake = whole,
            newProgress = progress.coerceIn(0f, 1f),
        )
    }

    companion object {
        const val CASHIER_ITEMS_PER_SECOND = 0.5f
        const val STOCKER_ACTIONS_PER_SECOND = 0.3f
    }
}

