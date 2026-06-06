package com.example.superstoresimulator.domain.pricing

import com.example.superstoresimulator.domain.items.ItemCategory
import kotlin.math.pow

data class PricingState(
    val categoryMarkups: Map<ItemCategory, Int> = emptyMap(),
    val itemOverrides: Map<Int, Int> = emptyMap(),
    val activeMarkdowns: Map<Int, Markdown> = emptyMap(),
    val defaultMarkup: Int = 0,
    val smoothedPriceIndex: Float = 1.0f,
    val priceHistory: List<PriceChangeEvent> = emptyList(),
) {
    val priceTrafficMultiplier: Float get() =
        if (smoothedPriceIndex <= 0f) 1.0f
        else (1.0 / smoothedPriceIndex).pow(PricingManager.TRAFFIC_ELASTICITY.toDouble()).toFloat()

    val basketSizeMultiplier: Float get() =
        if (smoothedPriceIndex <= 1.0f) 1.0f
        else (1.0 / smoothedPriceIndex).pow(PricingManager.BASKET_ELASTICITY.toDouble()).toFloat()
}

data class Markdown(
    val percentOff: Int,
    val reason: MarkdownReason,
    val appliedOnDay: Int,
)

enum class MarkdownReason {
    EXPIRING_SOON,
    PLAYER_SALE,
}

data class PriceChangeEvent(
    val dayNumber: Int,
    val itemId: Int?,
    val category: ItemCategory?,
    val oldPercent: Int,
    val newPercent: Int,
    val source: MarkdownReason?,
)
