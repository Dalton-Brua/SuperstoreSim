package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.traffic.TrafficManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreController @Inject constructor() {

    /**
     * Update the store's display name.
     */
    fun updateStoreName(state: GameState, newName: String): GameState =
        state.copy(storeName = newName)

    /**
     * Upgrade the store to the next size, if possible.
     *
     * Guards:
     *  - Returns the same state unchanged if already at max size (XL).
     *  - Returns the same state unchanged if player doesn't have enough cash.
     *
     * On success: deducts [StoreSize.upgradeCost] and advances to next size.
     */
    fun upgradeStoreSize(state: GameState): GameState {
        val nextSize = StoreSize.nextSize(state.currentStoreSize) ?: return state
        val cost = nextSize.upgradeCost ?: return state
        
        if (state.money < cost) return state

        return state.copy(
            currentStoreSize = nextSize,
            money = state.money - cost,
            storeConfig = state.storeConfig.copy(backroomCapPerItem = nextSize.backroomCapPerItem)
        )
    }

    /**
     * Toggle whether the player has manually paused the passage of time.
     */
    fun toggleTimePaused(state: GameState): GameState =
        state.copy(playerPausedTime = !state.playerPausedTime)

    /**
     * Synchronise [GameState.storeConfig.gameSpeedMultiplier] with the new [multiplier].
     *
     * Note: callers are responsible for also calling [TimeManager.setSpeedMultiplier]
     * to keep the [TimeManager]'s internal config in sync — that call is a side-effect
     * on the [TimeManager] object and is therefore NOT performed here.
     */
    fun setGameSpeedState(state: GameState, multiplier: Float): GameState =
        state.copy(storeConfig = state.storeConfig.copy(gameSpeedMultiplier = multiplier))

    /**
     * Apply any side-effects triggered by a store-state transition and return the
     * updated [GameState].
     *
     * On [StoreState.CLOSED]:
     *  - Calls [TrafficManager.reset] to drain the fractional customer accumulator.
     *  - Zeros [GameState.pendingCustomers] so no phantom customers remain in the queue.
     *
     * All other transitions are currently no-ops but are handled explicitly to make
     * future additions safe.
     *
     * Note: the caller ([GameEngine.tick]) is responsible for setting
     * [GameState.storeState] = [newStoreState] after this call returns.
     */
    fun handleStoreStateChange(
        state: GameState,
        newStoreState: StoreState,
        trafficManager: TrafficManager,
    ): GameState = when (newStoreState) {
        StoreState.OPEN -> state   // opening procedures — nothing to do yet
        StoreState.CLOSING -> state  // closing procedures — nothing to do yet
        StoreState.CLOSED -> {
            // Reset traffic accumulator so stale fractions don't carry to the next day.
            trafficManager.reset()
            state.copy(pendingCustomers = 0)
        }
        else -> state
    }
}

