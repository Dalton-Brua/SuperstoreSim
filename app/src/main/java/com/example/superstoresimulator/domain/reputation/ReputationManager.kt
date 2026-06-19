package com.example.superstoresimulator.domain.reputation

import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.store.StoreSize
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

object ReputationManager {

    const val WEIGHT_REVENUE = 0.50f
    const val WEIGHT_STOCK = 0.30f
    const val WEIGHT_APPEARANCE = 0.20f
    const val NUDGE_SMALL = 1f
    const val NUDGE_MEDIUM = 2f
    const val NUDGE_RISE_LARGE = 3f
    const val NUDGE_FALL_MAX = 2f
    const val NUDGE_THRESHOLD_SMALL = 25f
    const val NUDGE_THRESHOLD_LARGE = 75f
    const val GOOB_SALE_THRESHOLD = 30f
    const val GOOB_SALE_COOLDOWN_DAYS = 7
    const val GOOB_SALE_TRAFFIC_BOOST = 1.5f
    const val MIN_REPUTATION = 0f
    const val MAX_REPUTATION = 200f
    const val NEUTRAL_REPUTATION = 100f

    fun updateReputation(
        reputationState: ReputationState,
        snapshot: DailyMetrics,
        storeSize: StoreSize,
    ): ReputationState {
        val baseTarget = storeSize.baseRevenueTarget ?: return reputationState

        val target = if (reputationState.currentRevenueTarget == Money.ZERO || reputationState.daysTracked == 0)
            baseTarget
        else
            reputationState.currentRevenueTarget

        val todayRevenue = snapshot.subtotal

        val revenueScore = computeRevenueScore(todayRevenue, target)
        val stockScore = computeStockScore(snapshot.itemsSold, snapshot.itemsLostToOutOfStock)
        val appearanceScore = computeAppearanceScore(snapshot.avgZoneScore)

        val composite = (revenueScore * WEIGHT_REVENUE + stockScore * WEIGHT_STOCK + appearanceScore * WEIGHT_APPEARANCE)
            .coerceIn(MIN_REPUTATION, MAX_REPUTATION)

        val newScore = smoothReputation(reputationState.reputationScore, composite, reputationState.daysTracked)
        val newTarget = adjustTarget(target, todayRevenue, baseTarget)

        val hitTarget = todayRevenue >= target
        val newHits = if (hitTarget) reputationState.consecutiveTargetHits + 1 else 0
        val newMisses = if (!hitTarget) reputationState.consecutiveTargetMisses + 1 else 0

        return reputationState.copy(
            reputationScore = newScore,
            currentRevenueTarget = newTarget,
            daysTracked = reputationState.daysTracked + 1,
            trafficMultiplier = deriveTrafficMultiplier(newScore),
            priceToleranceMultiplier = derivePriceToleranceMultiplier(newScore),
            supplierDiscountBonus = deriveSupplierBonus(newScore),
            consecutiveTargetHits = newHits,
            consecutiveTargetMisses = newMisses,
            lastRevenueScore = revenueScore,
            lastStockScore = stockScore,
            lastAppearanceScore = appearanceScore,
            lastDailyComposite = composite,
        )
    }

    fun computeRevenueScore(todayRevenue: Money, currentTarget: Money): Float {
        if (currentTarget.cents <= 0L) return NEUTRAL_REPUTATION
        return (todayRevenue.cents.toDouble() / currentTarget.cents * 100.0)
            .toFloat().coerceIn(MIN_REPUTATION, MAX_REPUTATION)
    }

    fun computeStockScore(itemsSold: Int, itemsLostToOOS: Int): Float {
        val total = itemsSold + itemsLostToOOS
        if (total == 0) return NEUTRAL_REPUTATION
        val oosRate = itemsLostToOOS.toDouble() / total
        return (100.0 + (100.0 - oosRate * 400.0)).toFloat().coerceIn(MIN_REPUTATION, MAX_REPUTATION)
    }

    fun computeAppearanceScore(avgZoneScore: Float): Float =
        (avgZoneScore * 200f).coerceIn(MIN_REPUTATION, MAX_REPUTATION)

    fun smoothReputation(currentRep: Float, dailyComposite: Float, daysTracked: Int): Float {
        if (daysTracked == 0) return NEUTRAL_REPUTATION
        val delta = dailyComposite - currentRep
        val absDelta = abs(delta)
        val isRising = delta > 0
        val nudgeMagnitude = when {
            absDelta < NUDGE_THRESHOLD_SMALL -> NUDGE_SMALL
            absDelta < NUDGE_THRESHOLD_LARGE -> NUDGE_MEDIUM
            else -> if (isRising) NUDGE_RISE_LARGE else NUDGE_FALL_MAX
        }
        val nudge = sign(delta) * nudgeMagnitude
        return (currentRep + nudge).coerceIn(MIN_REPUTATION, MAX_REPUTATION)
    }

    fun adjustTarget(currentTarget: Money, todayRevenue: Money, baseTarget: Money): Money {
        val newTarget = if (todayRevenue >= currentTarget) {
            val overshoot = (todayRevenue.cents.toDouble() / currentTarget.cents - 1.0).coerceIn(0.0, 1.0)
            val growthRate = 0.01 + 0.01 * overshoot
            Money((currentTarget.cents * (1.0 + growthRate)).toLong())
        } else {
            val undershoot = (1.0 - todayRevenue.cents.toDouble() / currentTarget.cents).coerceIn(0.0, 1.0)
            val decayRate = 0.005 + 0.01 * undershoot
            Money((currentTarget.cents * (1.0 - decayRate)).toLong())
        }
        return if (newTarget > baseTarget) newTarget else baseTarget
    }

    fun deriveTrafficMultiplier(rep: Float): Float {
        val n = rep / 100f
        return if (n >= 1f) {
            val excess = n - 1f
            1f + excess.toDouble().pow(1.5).toFloat()
        } else {
            val deficit = 1f - n
            1f - 0.5f * deficit.toDouble().pow(1.5).toFloat()
        }
    }

    fun derivePriceToleranceMultiplier(rep: Float): Float {
        val n = rep / 100f
        return if (n >= 1f) {
            val excess = n - 1f
            1f + 0.4f * excess.toDouble().pow(1.5).toFloat()
        } else {
            val deficit = 1f - n
            1f - 0.3f * deficit.toDouble().pow(1.5).toFloat()
        }
    }

    fun deriveSupplierBonus(rep: Float): Float {
        if (rep <= 100f) return 0f
        val excess = (rep - 100f) / 100f
        return 0.10f * excess.toDouble().pow(1.5).toFloat()
    }

    fun initializeTarget(storeSize: StoreSize): Money =
        storeSize.baseRevenueTarget ?: Money.ZERO

    fun checkGoobSaleEligible(reputationState: ReputationState, currentDay: Int): Boolean =
        reputationState.reputationScore < GOOB_SALE_THRESHOLD &&
            (currentDay - reputationState.lastSaleEventDay) >= GOOB_SALE_COOLDOWN_DAYS

    fun applyGoobSale(reputationState: ReputationState, currentDay: Int): ReputationState =
        reputationState.copy(goobSaleActiveToday = true, lastSaleEventDay = currentDay)
}
