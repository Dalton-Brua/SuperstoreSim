package com.example.superstoresimulator.domain.pricing

import com.example.superstoresimulator.domain.items.ItemCategory

data class PricingState(
    val categoryMarkups: Map<ItemCategory, Int> = emptyMap(),
    val itemOverrides: Map<Int, Int> = emptyMap(),
    val activeMarkdowns: Map<Int, Markdown> = emptyMap(),
    val defaultMarkup: Int = 0,
    val smoothedPriceIndex: Float = 1.0f,
    val priceTrafficMultiplier: Float = 1.0f,
    val basketSizeMultiplier: Float = 1.0f,
    val priceHistory: List<PriceChangeEvent> = emptyList(),
)

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
