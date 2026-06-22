package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.store.StoreSize

/**
 * Frozen tuning constants + per-direction modifier table for empire mode (loop 2).
 *
 * Wave-0 placeholder values; Wave-2 tuning may adjust the numbers but NOT the names.
 * Every Wave-1 track references the names here so the values live in one place.
 */
object EmpireTuning {

    // ── Direction modifiers ────────────────────────────────────────────────────
    /** Price/traffic/cost multipliers applied to the daily sim per [StoreDirection]. */
    fun modifiersFor(direction: StoreDirection): StoreDirectionModifiers = when (direction) {
        StoreDirection.AGGRESSIVE -> StoreDirectionModifiers(priceMult = 0.90f, trafficMult = 1.30f, costMult = 1.20f)
        StoreDirection.BALANCED   -> StoreDirectionModifiers(priceMult = 1.00f, trafficMult = 1.00f, costMult = 1.00f)
        StoreDirection.PASSIVE    -> StoreDirectionModifiers(priceMult = 1.15f, trafficMult = 0.75f, costMult = 0.85f)
        StoreDirection.DEFENSIVE  -> StoreDirectionModifiers(priceMult = 1.00f, trafficMult = 0.65f, costMult = 0.65f)
    }

    // ── Base sim economics ─────────────────────────────────────────────────────
    /** Base daily customers for the smallest store at BALANCED before any multipliers. */
    const val BASE_DAILY_CUSTOMERS = 80
    /** Base average basket value (dollars) for a BALANCED store before multipliers. */
    val BASE_AVG_BASKET = Money(2_500L) // $25.00
    /** Base daily fixed operating cost (dollars) for the smallest store. */
    val BASE_OPERATING_COST = Money(40_000L) // $400/day

    // ── Saturation ─────────────────────────────────────────────────────────────
    /** Steepness of the per-region saturation falloff above capacity. Key tuning knob. */
    const val SATURATION_STEEPNESS = 0.5f

    // ── Store weight (region crowding) ─────────────────────────────────────────
    /** Weight a store of [size] contributes to its region. */
    fun sizeWeight(size: StoreSize): Float = when (size) {
        StoreSize.MOM_AND_POP    -> 1.0f
        StoreSize.SMALL_GROCERY  -> 2.0f
        StoreSize.GROCERY_STORE  -> 4.0f
        StoreSize.SUPERSTORE     -> 8.0f
        StoreSize.SUPERCENTER    -> 16.0f
    }
    /** Extra weight fraction added by the MARKETING upgrade. */
    const val MARKETING_WEIGHT_BONUS = 0.5f

    // ── Upgrade effects ────────────────────────────────────────────────────────
    const val EXTRA_REGISTERS_TRAFFIC_BONUS = 0.15f  // +15% traffic ceiling
    const val LOGISTICS_COST_REDUCTION = 0.20f       // −20% operating cost
    const val MARKETING_TRAFFIC_BONUS = 0.25f        // +25% traffic

    /** Cost of each [StoreUpgrade] flag (size expansion uses StoreSize.upgradeCost instead). */
    fun upgradeCost(upgrade: StoreUpgrade): Money = when (upgrade) {
        StoreUpgrade.EXTRA_REGISTERS -> Money(5_000_000L)  // $50,000
        StoreUpgrade.LOGISTICS       -> Money(7_500_000L)  // $75,000
        StoreUpgrade.MARKETING       -> Money(5_000_000L)  // $50,000
    }

    // ── Location scaling cost ──────────────────────────────────────────────────
    /**
     * Cost to open the [locationNumber]-th location (2 = first secondary store).
     * 2nd = $1M, 3rd = $2.5M, nth = $1M × 2.5^(n-2).
     */
    fun locationCost(locationNumber: Int): Money {
        val n = (locationNumber - 2).coerceAtLeast(0)
        val dollars = 1_000_000.0 * Math.pow(2.5, n.toDouble())
        return Money.fromDollars(dollars)
    }

    // ── Regional manager ───────────────────────────────────────────────────────
    val MANAGER_SALARY_PER_DAY = Money(500_00L) // $500/day
    /** Minimum secondary stores owned before a regional manager can be hired. */
    const val MANAGER_HIRE_MIN_STORES = 3
    /** Empire net-profit floor (per day) the manager keeps the empire at or above. */
    val MANAGER_PROFIT_FLOOR = Money.ZERO

    // ── Operating performance (hands-on visits) ────────────────────────────────
    const val OPERATING_PERFORMANCE_MIN = 0.7f
    const val OPERATING_PERFORMANCE_MAX = 1.5f
    /** Days over which operatingPerformance drifts back to 1.0 baseline. */
    const val OPERATING_PERFORMANCE_DECAY_DAYS = 14

    // ── Empire clock ───────────────────────────────────────────────────────────
    /** Real milliseconds per simulated day at each speed. */
    const val MS_PER_DAY_NORMAL = 3_000L
    const val MS_PER_DAY_FAST = 600L
}
