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

class PricingManager(private val cache: ItemMetadataCache) {

    companion object {
        const val PRICE_ELASTICITY = 1.5f
        const val TRAFFIC_ELASTICITY = 0.5f
        const val BASKET_ELASTICITY = 1.0f
        const val FRESH_MARKDOWN_PERCENT = 30
        const val EXPIRY_THRESHOLD_DAYS = 1
        const val EMA_ALPHA = 0.3f
        const val MAX_PRICE_HISTORY_SIZE = 200
        const val PRODUCE_WEIGHT_MIN = 0.5f
        const val PRODUCE_WEIGHT_MAX = 3.0f
        const val MEAT_WEIGHT_MIN = 0.75f
        const val MEAT_WEIGHT_MAX = 2.5f
    }

    fun resolvePrice(itemId: Int, state: GameState): ResolvedPrice {
        val meta = cache.get(itemId) ?: return ResolvedPrice(Money.ZERO, Money.ZERO, 0)
        val pricing = state.pricingState

        val categoryMarkup = pricing.categoryMarkups[meta.category] ?: 0
        val itemOverride = pricing.itemOverrides[itemId] ?: 0
        val markdown = pricing.activeMarkdowns[itemId]?.percentOff ?: 0

        val combinedMultiplier = (1.0 + categoryMarkup / 100.0) *
                (1.0 + itemOverride / 100.0) *
                (1.0 - markdown / 100.0)

        val rawCents = (meta.price.cents.toDouble() * combinedMultiplier).roundToLong()
        val flooredCents = rawCents.coerceAtLeast(meta.unitCost.cents)
        val effectivePrice = Money(flooredCents)

        val modifierPercent = if (meta.price.cents > 0) {
            ((effectivePrice.cents.toDouble() / meta.price.cents.toDouble() - 1.0) * 100).toInt()
        } else 0

        return ResolvedPrice(effectivePrice, meta.price, modifierPercent)
    }

    fun purchaseProbabilityMultiplier(itemId: Int, state: GameState): Float {
        val resolved = resolvePrice(itemId, state)
        if (resolved.basePrice.cents <= 0 || resolved.effectivePrice.cents <= 0) return 1.0f
        val ratio = resolved.basePrice.cents.toDouble() / resolved.effectivePrice.cents.toDouble()
        return ratio.pow(PRICE_ELASTICITY.toDouble()).toFloat()
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
            EMA_ALPHA * rawIndex + (1 - EMA_ALPHA) * current
        }

        val trafficMult = computeTrafficMultiplier(smoothed)
        val basketMult = computeBasketMultiplier(smoothed)

        return state.copy(
            pricingState = pricing.copy(
                smoothedPriceIndex = smoothed,
                priceTrafficMultiplier = trafficMult,
                basketSizeMultiplier = basketMult,
            )
        )
    }

    fun computeTrafficMultiplier(priceIndex: Float): Float {
        if (priceIndex <= 0f) return 1.0f
        return (1.0 / priceIndex).pow(TRAFFIC_ELASTICITY.toDouble()).toFloat()
    }

    fun computeBasketMultiplier(priceIndex: Float): Float {
        if (priceIndex <= 1.0f) return 1.0f
        return (1.0 / priceIndex).pow(BASKET_ELASTICITY.toDouble()).toFloat()
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
            percentOff = FRESH_MARKDOWN_PERCENT,
            reason = MarkdownReason.EXPIRING_SOON,
            appliedOnDay = currentDay,
        )

        val event = PriceChangeEvent(
            dayNumber = currentDay,
            itemId = itemId,
            category = null,
            oldPercent = 0,
            newPercent = -FRESH_MARKDOWN_PERCENT,
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

    fun computePricingMultipliers(state: GameState): Map<Int, Float> {
        val result = mutableMapOf<Int, Float>()
        for (itemId in state.inventory.keys) {
            result[itemId] = purchaseProbabilityMultiplier(itemId, state)
        }
        return result
    }

    fun randomWeight(category: ItemCategory, random: kotlin.random.Random = kotlin.random.Random): Float {
        return when (category) {
            ItemCategory.PRODUCE -> random.nextFloat() * (PRODUCE_WEIGHT_MAX - PRODUCE_WEIGHT_MIN) + PRODUCE_WEIGHT_MIN
            ItemCategory.MEAT -> random.nextFloat() * (MEAT_WEIGHT_MAX - MEAT_WEIGHT_MIN) + MEAT_WEIGHT_MIN
            else -> 1.0f
        }
    }

    private fun appendHistory(
        history: List<PriceChangeEvent>,
        event: PriceChangeEvent,
    ): List<PriceChangeEvent> {
        val updated = history + event
        return if (updated.size > MAX_PRICE_HISTORY_SIZE) {
            updated.drop(updated.size - MAX_PRICE_HISTORY_SIZE)
        } else updated
    }
}
