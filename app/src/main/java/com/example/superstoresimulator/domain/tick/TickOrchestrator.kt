package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.GameStateChange
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.expiration.SpoilageManager
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.traffic.TrafficManager
import javax.inject.Inject
import javax.inject.Singleton

data class TickResult(val state: GameState, val changes: List<GameStateChange>)

@Singleton
class TickOrchestrator @Inject constructor(
    private val timeManager: TimeManager,
    private val spoilageManager: SpoilageManager,
    private val storeController: StoreController,
    private val pricingManager: PricingManager,
    private val registerManager: RegisterManager,
    private val transactionEngine: TransactionEngine,
    private val trafficManager: TrafficManager,
    private val dayRolloverProcessor: DayRolloverProcessor,
    private val trafficProcessor: TrafficProcessor,
    private val staffTickProcessor: StaffTickProcessor,
    private val playerTickProcessor: PlayerTickProcessor,
    private val utilizationTracker: UtilizationTracker,
) {
    fun tick(state: GameState, deltaMilliseconds: Long): TickResult {
        if (state.playerPausedTime) return TickResult(state, emptyList())

        var s = advanceTime(state, deltaMilliseconds)
        s = processSpoilage(s)
        s = dayRolloverProcessor.process(s)
        s = updateStoreState(s)
        s = updatePricing(s)

        val delta = deltaMilliseconds / 1000.0
        val speedMultiplier = s.storeConfig.gameSpeedMultiplier
        val currentHour = s.currentTime.hour

        s = registerManager.performShiftCheckAndReassignment(s, currentHour)
        s = trafficProcessor.process(s, delta, currentHour)
        s = staffTickProcessor.process(s, delta, speedMultiplier, currentHour)
        s = playerTickProcessor.process(s, delta, currentHour)
        utilizationTracker.sample(s, currentHour)

        return TickResult(s, emptyList())
    }

    private fun advanceTime(state: GameState, deltaMilliseconds: Long): GameState {
        timeManager.update(deltaMilliseconds)
        return state.copy(currentTime = timeManager.currentTime)
    }

    private fun processSpoilage(state: GameState): GameState {
        val spoilageResult = spoilageManager.processExpiration(state)
        var s = spoilageResult.state
        if (spoilageResult.expiredItemIds.isNotEmpty()) {
            for (itemId in spoilageResult.expiredItemIds) {
                s = transactionEngine.clearStaleExpiryMarkdown(s, itemId)
            }
        }
        return s
    }

    private fun updateStoreState(state: GameState): GameState {
        val newStoreState = timeManager.getStoreState()
        var s = state
        if (newStoreState != s.storeState) {
            s = storeController.handleStoreStateChange(s, newStoreState, trafficManager)
        }
        return s.copy(storeState = newStoreState)
    }

    private fun updatePricing(state: GameState): GameState {
        val currentHour = state.currentTime.hour
        if (currentHour != state.pricingState.lastPriceIndexHour) {
            return pricingManager.updateSmoothedPriceIndex(state)
        }
        return state
    }
}
