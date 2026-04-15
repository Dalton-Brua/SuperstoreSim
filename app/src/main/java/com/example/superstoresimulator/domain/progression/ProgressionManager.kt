package com.example.superstoresimulator.domain.progression

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.items.ItemUnlockTier

/**
 * Sub-system responsible for tier progression logic.
 *
 * This is a pure sub-system: it receives a [GameState] and returns a new [GameState].
 * It never holds a reference to the engine's mutable state or the change-emission
 * stream — [GameEngine] retains those responsibilities.
 *
 * Responsibilities:
 *  - Validating the revenue gate and cash balance before a tier purchase
 *  - Advancing [GameState.currentTier] and deducting [ItemUnlockTier.unlockCost]
 */
class ProgressionManager {

    /**
     * Purchase the next [ItemUnlockTier] with the player's cash balance.
     *
     * Guards:
     *  - Returns the same state unchanged if already at the top tier.
     *  - Returns the same state unchanged if [GameState.totalRevenue] has not yet
     *    met the next tier's revenue gate ([ItemUnlockTier.unlockAmount]).
     *  - Returns the same state unchanged if [GameState.money] is less than the
     *    tier's [ItemUnlockTier.unlockCost].
     *
     * On success: deducts [ItemUnlockTier.unlockCost] from [GameState.money] and
     * advances [GameState.currentTier] to the next tier.
     *
     * Change emission ([GameStateChange.TierUnlocked]) is the caller's responsibility
     * — compare [GameState.currentTier] before and after this call.
     */
    fun unlockNextTier(state: GameState): GameState {
        val nextTier = ItemUnlockTier.nextTier(state.currentTier) ?: return state
        if (state.totalRevenue.cents < nextTier.unlockAmount) return state
        if (state.money < nextTier.unlockCost) return state

        return state.copy(
            currentTier = nextTier,
            money = state.money - nextTier.unlockCost,
        )
    }
}

