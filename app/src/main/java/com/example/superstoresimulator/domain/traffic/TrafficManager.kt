package com.example.superstoresimulator.domain.traffic

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.time.StoreState
import kotlin.random.Random

/**
 * Manages autonomous customer generation based on time-of-day traffic patterns.
 *
 * Uses fractional accumulation (same technique as TimeManager and stocker progress)
 * so customer arrivals are smooth and rate-correct regardless of tick granularity.
 *
 * Integrates with GameEngine.tick() — called once per frame when the store is open.
 */
class TrafficManager {

    /** Accumulated fractional customers from previous ticks. */
    private var accumulatedCustomers = 0.0

    /**
     * Update customer traffic for this tick.
     *
     * @param state        Current game state (provides time, storeState, gameSpeedMultiplier)
     * @param deltaSeconds Real seconds elapsed since the last tick
     * @return             List of transaction requests — one per customer that fully "arrived"
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
        // Divide by 60 to get per-game-second, then scale by game speed so
        // higher speed multipliers produce proportionally more customers.
        val customerRatePerSecond =
            (pattern.baseCustomerRate / 60.0) * state.storeConfig.gameSpeedMultiplier

        accumulatedCustomers += customerRatePerSecond * deltaSeconds

        // Convert whole accumulated customers into transaction requests
        while (accumulatedCustomers >= 1.0) {
            val basketSize = (pattern.averageBasketSize - 1 + Random.nextInt(3)).coerceIn(1, 7)
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

