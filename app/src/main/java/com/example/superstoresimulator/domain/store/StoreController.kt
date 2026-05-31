package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.traffic.TrafficManager

/**
 * Sub-system responsible for store administrative operations.
 *
 * This is a pure sub-system: each method receives a [GameState] and returns a new
 * [GameState]. It never holds a reference to the engine's mutable state or the
 * change-emission stream — [GameEngine] retains those responsibilities.
 *
 * The exception is [handleStoreStateChange], which accepts a [TrafficManager] as a
 * method parameter so it can call [TrafficManager.reset] on the CLOSED transition.
 * That side-effect is intentional: the traffic accumulator must be drained whenever
 * the store closes so stale fractional customer counts do not carry over to the next
 * business day.
 *
 * Responsibilities:
 *  - Store name changes
 *  - Player pause / resume toggle
 *  - Game-speed state synchronisation
 *  - Store open/closing/closed transition side-effects
 */
class StoreController {

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
     * Purchase one additional register.
     *
     * Guards:
     *  - Returns unchanged if already at the max for the current store size.
     *  - Returns unchanged if player doesn't have enough cash.
     *
     * On success: deducts [StoreSize.nextRegisterCost] and appends a new [RegisterState]
     * with the next available register id.
     */
    fun purchaseRegister(state: GameState): GameState {
        if (state.ownedRegisterCount >= state.currentStoreSize.maxRegisters) return state
        val cost = StoreSize.nextRegisterCost(state.ownedRegisterCount)
        if (state.money < cost) return state
        val newRegisterId = (state.registers.maxOfOrNull { it.registerId } ?: 0) + 1
        return state.copy(
            money = state.money - cost,
            ownedRegisterCount = state.ownedRegisterCount + 1,
            registers = state.registers + RegisterState(registerId = newRegisterId),
        )
    }

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

