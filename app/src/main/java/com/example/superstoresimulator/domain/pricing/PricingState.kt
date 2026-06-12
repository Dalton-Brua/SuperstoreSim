package com.example.superstoresimulator.domain.pricing

import com.example.superstoresimulator.domain.items.ItemCategory
import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
data class PricingState(
    val categoryMarkups: Map<ItemCategory, Int> = emptyMap(),
    val itemOverrides: Map<Int, Int> = emptyMap(),
    val activeMarkdowns: Map<Int, Markdown> = emptyMap(),
    val defaultMarkup: Int = 10,
    val smoothedPriceIndex: Float = 1.0f,
    val priceHistory: List<PriceChangeEvent> = emptyList(),
    val lastPriceIndexHour: Int = -1,
) {
    val priceTrafficMultiplier: Float get() =
        if (smoothedPriceIndex <= 0f) 1.0f
        else (1.0 / smoothedPriceIndex).pow(PricingConfig.DEFAULT.trafficElasticity.toDouble()).toFloat()

    val basketSizeMultiplier: Float get() =
        if (smoothedPriceIndex <= 1.0f) 1.0f
        else (1.0 / smoothedPriceIndex).pow(PricingConfig.DEFAULT.basketElasticity.toDouble()).toFloat()
}

@Serializable
data class Markdown(
    val percentOff: Int,
    val reason: MarkdownReason,
    val appliedOnDay: Int,
)

@Serializable
enum class MarkdownReason {
    EXPIRING_SOON,
    PLAYER_SALE,
}

@Serializable
data class PriceChangeEvent(
    val dayNumber: Int,
    val itemId: Int?,
    val category: ItemCategory?,
    val oldPercent: Int,
    val newPercent: Int,
    val source: MarkdownReason?,
)
