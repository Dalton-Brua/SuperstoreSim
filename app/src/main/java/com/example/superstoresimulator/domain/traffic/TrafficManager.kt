package com.example.superstoresimulator.domain.traffic

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.reputation.ReputationManager
import com.example.superstoresimulator.domain.store.StoreState
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class TrafficManager @Inject constructor() {

    /** Accumulated fractional customers from previous ticks. */
    internal var accumulatedCustomers = 0.0

    /**
     * Update customer traffic for this tick.
     *
     * @param state        Current game state (provides time, storeState, gameSpeedMultiplier, currentStoreSize)
     * @param deltaSeconds Real seconds elapsed since the last tick
     * @return             List of transaction requests — one per customer that fully "arrived"
     *
     * Traffic rate is affected by:
     *  - Time-of-day pattern (baseCustomerRate)
     *  - Game speed multiplier (faster time = more customers)
     *  - Store size multiplier (larger stores attract more customers)
     *
     * Note: If a transaction is already active, the list may be empty even if customers
     * accumulated, because [generateRandomTransaction] is a no-op when active.
     * Customers that cannot be served are lost to queue overflow — tune traffic
     * rates and cashier efficiency to keep up with peak demand.
     */
    fun update(state: GameState, deltaSeconds: Double): List<TransactionRequest> {
        val results = mutableListOf<TransactionRequest>()

        // Only generate customers when the store is open
        if (state.storeState != StoreState.OPEN) {
            accumulatedCustomers = 0.0
            return results
        }

        val pattern = TrafficSchedule.getPatternForTime(state.currentTime)

        // baseCustomerRate = customers per game-minute
        // Divide by 60 to get per-game-second, then scale by:
        //  - game speed (faster time = more customers proportionally)
        //  - store size (larger stores = more foot traffic)
        val goobBoost = if (state.reputationState.goobSaleActiveToday) ReputationManager.GOOB_SALE_TRAFFIC_BOOST.toDouble() else 1.0
        val customerRatePerSecond =
            (pattern.baseCustomerRate / 60.0) *
            state.storeConfig.gameSpeedMultiplier *
            state.currentStoreSize.trafficMultiplier *
            state.pricingState.priceTrafficMultiplier *
            state.reputationState.trafficMultiplier *
            goobBoost

        accumulatedCustomers += customerRatePerSecond * deltaSeconds

        // Convert whole accumulated customers into transaction requests
        while (accumulatedCustomers >= 1.0) {
            val baseBasket = pattern.averageBasketSize - 1 + Random.nextInt(3)
            val basketSize = (baseBasket * state.currentStoreSize.basketSizeMultiplier).toInt().coerceAtLeast(1)
            results.add(
                TransactionRequest(
                    itemCount = basketSize,
                    customerPattern = pattern
                )
            )
            accumulatedCustomers -= 1.0
        }

        return results
    }

    /** Reset accumulator — call when the store transitions to CLOSED. */
    fun reset() {
        accumulatedCustomers = 0.0
    }
}

/**
 * A request to generate a new transaction for an arriving customer.
 *
 * @param itemCount       Pre-determined basket size for this customer
 * @param customerPattern The traffic pattern active when the customer arrived
 */
data class TransactionRequest(
    val itemCount: Int,
    val customerPattern: TrafficPattern
)

