package com.example.superstoresimulator.domain.pricing

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import kotlin.math.pow
import kotlin.math.roundToLong

data class ResolvedPrice(
    val effectivePrice: Money,
    val basePrice: Money,
    val modifierPercent: Int,
)

@javax.inject.Singleton
class PricingManager @javax.inject.Inject constructor(
    private val cache: ItemMetadataCache,
) {
    val config: PricingConfig = PricingConfig.DEFAULT

    fun resolvePrice(itemId: Int, state: GameState): ResolvedPrice {
        val meta = cache.get(itemId) ?: return ResolvedPrice(Money.ZERO, Money.ZERO, 0)
        val pricing = state.pricingState

        val baseMarkup = pricing.defaultMarkup
        val categoryMarkup = pricing.categoryMarkups[meta.category] ?: 0
        val itemOverride = pricing.itemOverrides[itemId] ?: 0
        val markdown = pricing.activeMarkdowns[itemId]?.percentOff ?: 0

        val combinedPercent = (baseMarkup + categoryMarkup + itemOverride - markdown).coerceAtLeast(-90)
        val combinedMultiplier = 1.0 + combinedPercent / 100.0

        val rawCents = (meta.price.cents.toDouble() * combinedMultiplier).roundToLong()
        val flooredCents = rawCents.coerceAtLeast(meta.unitCost.cents)
        val effectivePrice = Money(flooredCents)

        val modifierPercent = if (meta.price.cents > 0) {
            ((effectivePrice.cents.toDouble() / meta.price.cents.toDouble() - 1.0) * 100).toInt()
        } else 0

        return ResolvedPrice(effectivePrice, meta.price, modifierPercent)
    }

    fun computePriceIndex(state: GameState): Float {
        var weightedSum = 0.0
        var totalWeight = 0.0

        for ((itemId, inv) in state.inventory) {
            if (inv.shelfStock <= 0) continue
            val meta = cache.get(itemId) ?: continue
            if (meta.price.cents <= 0) continue

            val resolved = resolvePrice(itemId, state)
            val ratio = resolved.effectivePrice.cents.toDouble() / meta.price.cents.toDouble()
            val w = meta.purchaseWeight.toDouble().coerceAtLeast(0.01)

            weightedSum += ratio * w
            totalWeight += w
        }

        return if (totalWeight > 0) (weightedSum / totalWeight).toFloat() else 1.0f
    }

    fun updateSmoothedPriceIndex(state: GameState): GameState {
        val rawIndex = computePriceIndex(state)
        val pricing = state.pricingState
        val current = pricing.smoothedPriceIndex

        val smoothed = if (current == 1.0f && pricing.categoryMarkups.isEmpty() &&
            pricing.itemOverrides.isEmpty() && pricing.activeMarkdowns.isEmpty()
        ) {
            rawIndex
        } else {
            config.emaAlpha * rawIndex + (1 - config.emaAlpha) * current
        }

        return state.copy(
            pricingState = pricing.copy(
                smoothedPriceIndex = smoothed,
                lastPriceIndexHour = state.currentTime.hour,
            )
        )
    }

    fun setCategoryMarkup(state: GameState, category: ItemCategory, percent: Int): GameState {
        val clamped = percent.coerceIn(-50, 100)
        val pricing = state.pricingState
        val old = pricing.categoryMarkups[category] ?: 0
        if (old == clamped) return state

        val event = PriceChangeEvent(
            dayNumber = state.currentTime.dayNumber,
            itemId = null,
            category = category,
            oldPercent = old,
            newPercent = clamped,
            source = null,
        )

        return state.copy(
            pricingState = pricing.copy(
                categoryMarkups = pricing.categoryMarkups + (category to clamped),
                priceHistory = appendHistory(pricing.priceHistory, event),
            )
        )
    }

    fun setDefaultMarkup(state: GameState, percent: Int): GameState {
        val clamped = percent.coerceIn(-50, 100)
        return state.copy(
            pricingState = state.pricingState.copy(defaultMarkup = clamped)
        )
    }

    fun setItemOverride(state: GameState, itemId: Int, percent: Int): GameState {
        val clamped = percent.coerceIn(-75, 200)
        val pricing = state.pricingState
        val old = pricing.itemOverrides[itemId] ?: 0
        if (old == clamped) return state

        val event = PriceChangeEvent(
            dayNumber = state.currentTime.dayNumber,
            itemId = itemId,
            category = null,
            oldPercent = old,
            newPercent = clamped,
            source = null,
        )

        return state.copy(
            pricingState = pricing.copy(
                itemOverrides = pricing.itemOverrides + (itemId to clamped),
                priceHistory = appendHistory(pricing.priceHistory, event),
            )
        )
    }

    fun applyExpiryMarkdown(state: GameState, itemId: Int, currentDay: Int): GameState {
        val pricing = state.pricingState
        if (pricing.activeMarkdowns.containsKey(itemId)) return state

        val markdown = Markdown(
            percentOff = config.freshMarkdownPercent,
            reason = MarkdownReason.EXPIRING_SOON,
            appliedOnDay = currentDay,
        )

        val event = PriceChangeEvent(
            dayNumber = currentDay,
            itemId = itemId,
            category = null,
            oldPercent = 0,
            newPercent = -config.freshMarkdownPercent,
            source = MarkdownReason.EXPIRING_SOON,
        )

        return state.copy(
            pricingState = pricing.copy(
                activeMarkdowns = pricing.activeMarkdowns + (itemId to markdown),
                priceHistory = appendHistory(pricing.priceHistory, event),
            )
        )
    }

    fun clearMarkdown(state: GameState, itemId: Int): GameState {
        val pricing = state.pricingState
        if (!pricing.activeMarkdowns.containsKey(itemId)) return state

        return state.copy(
            pricingState = pricing.copy(
                activeMarkdowns = pricing.activeMarkdowns - itemId,
            )
        )
    }

    class PricingData(
        val multipliers: Map<Int, Float>,
        private val resolvedPrices: Map<Int, ResolvedPrice>,
    ) {
        fun resolvePrice(itemId: Int): ResolvedPrice =
            resolvedPrices[itemId] ?: ResolvedPrice(Money.ZERO, Money.ZERO, 0)
    }

    fun computePricingData(state: GameState): PricingData {
        val multipliers = mutableMapOf<Int, Float>()
        val resolved = mutableMapOf<Int, ResolvedPrice>()
        for (itemId in state.inventory.keys) {
            val r = resolvePrice(itemId, state)
            resolved[itemId] = r
            multipliers[itemId] = if (r.basePrice.cents <= 0 || r.effectivePrice.cents <= 0) 1.0f
            else (r.basePrice.cents.toDouble() / r.effectivePrice.cents.toDouble())
                .pow(config.priceElasticity.toDouble()).toFloat()
        }
        return PricingData(multipliers, resolved)
    }

    fun randomWeight(category: ItemCategory, random: kotlin.random.Random = kotlin.random.Random): Float {
        return when (category) {
            ItemCategory.PRODUCE -> random.nextFloat() * (config.produceWeightMax - config.produceWeightMin) + config.produceWeightMin
            ItemCategory.MEAT -> random.nextFloat() * (config.meatWeightMax - config.meatWeightMin) + config.meatWeightMin
            else -> 1.0f
        }
    }

    private fun appendHistory(
        history: List<PriceChangeEvent>,
        event: PriceChangeEvent,
    ): List<PriceChangeEvent> =
        (history + event).takeLast(config.maxPriceHistorySize)
}
