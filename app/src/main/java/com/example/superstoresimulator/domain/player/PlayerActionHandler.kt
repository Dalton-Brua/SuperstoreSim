package com.example.superstoresimulator.domain.player

import com.example.superstoresimulator.domain.GameState
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerWorkResult(
    val actionsToTake: Int,
    val newProgress: Float,
)

@Singleton
class PlayerActionHandler @Inject constructor() {

    // ── Role management ───────────────────────────────────────────────────────

    /**
     * Set the player's active work role.
     *
     * Passing the currently-active role toggles it OFF (returns to [PlayerRole.MANAGE]).
     * Both progress accumulators are always zeroed when the role changes so that
     * leftover fractions from the previous role do not bleed into the next one.
     */
    fun setPlayerRole(state: GameState, role: PlayerRole): GameState {
        val newRole = if (state.playerRole == role) PlayerRole.MANAGE else role

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
     * while also returning the player to [PlayerRole.MANAGE].
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

