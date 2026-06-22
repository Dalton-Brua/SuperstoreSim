package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.Money
import kotlinx.serialization.Serializable

/** One day of simulated output for a secondary store — what the dashboard shows. */
@Serializable
data class SimStoreMetrics(
    val dayIndex: Int = 0,
    val customers: Int = 0,
    val revenue: Money = Money.ZERO,
    val operatingCost: Money = Money.ZERO,
    val netProfit: Money = Money.ZERO,
    val avgPriceLevel: Float = 1.0f,   // relative to baseline (1.0 = normal)
    val trafficVsGoal: Float = 1.0f,   // actual / goal
)
