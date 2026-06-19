package com.example.superstoresimulator.domain.reputation

import com.example.superstoresimulator.domain.Money
import kotlinx.serialization.Serializable

@Serializable
data class ReputationState(
    val reputationScore: Float = 100f,
    val currentRevenueTarget: Money = Money.ZERO,
    val daysTracked: Int = 0,

    // Cached multipliers (recomputed from score at day rollover)
    val trafficMultiplier: Float = 1.0f,
    val priceToleranceMultiplier: Float = 1.0f,
    val supplierDiscountBonus: Float = 0f,

    // Streak counters (UI display)
    val consecutiveTargetHits: Int = 0,
    val consecutiveTargetMisses: Int = 0,

    // Going Out of Business Sale
    val lastSaleEventDay: Int = -7,
    val goobSaleActiveToday: Boolean = false,

    // Last day's breakdown (for End-of-Day report)
    val lastRevenueScore: Float = 0f,
    val lastStockScore: Float = 0f,
    val lastAppearanceScore: Float = 0f,
    val lastDailyComposite: Float = 0f,
)
