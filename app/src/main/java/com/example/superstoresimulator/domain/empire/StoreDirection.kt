package com.example.superstoresimulator.domain.empire

import kotlinx.serialization.Serializable

/** Strategic posture the player (or a regional manager) sets per secondary store. */
@Serializable
enum class StoreDirection(val displayName: String) {
    AGGRESSIVE("Aggressive"),  // low margins, high volume — chase traffic & market share
    BALANCED("Balanced"),      // default — moderate pricing, steady growth
    PASSIVE("Passive"),        // high margins, low volume — milk profit, minimal effort
    DEFENSIVE("Defensive"),    // protect against losses — cut costs, minimize risk
}

/** Multipliers a [StoreDirection] applies to the daily sim. Values come from [EmpireTuning]. */
data class StoreDirectionModifiers(
    val priceMult: Float,
    val trafficMult: Float,
    val costMult: Float,
)
