package com.example.superstoresimulator.domain.reputation

import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.store.StoreSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD tests for [ReputationManager]. Written before the implementation exists.
 * Covers: sub-score math, composite weighting, daily nudge smoothing, GOOB sale
 * eligibility, target ratchet, multiplier curves, feature gating, and streak tracking.
 *
 * All float comparisons use delta=0.01f unless a range check is more appropriate.
 * No Mockito. No trivial tests. Pure-function driven — no GameEngine required.
 */
class ReputationManagerTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun stateWithTarget(targetCents: Long): ReputationState =
        ReputationState(currentRevenueTarget = Money(targetCents))

    private fun snapshot(
        subtotalCents: Long = 0L,
        itemsSold: Int = 100,
        itemsLostToOutOfStock: Int = 0,
        avgZoneScore: Float = 1.0f,
    ) = DailyMetrics(
        subtotal = Money(subtotalCents),
        itemsSold = itemsSold,
        itemsLostToOutOfStock = itemsLostToOutOfStock,
        avgZoneScore = avgZoneScore,
    )

    // ── Sub-score: Revenue ────────────────────────────────────────────────────

    @Test
    fun `computeRevenueScore at target returns 100`() {
        val target = Money(100_000)
        assertEquals(100f, ReputationManager.computeRevenueScore(target, target), 0.01f)
    }

    @Test
    fun `computeRevenueScore double target returns 200`() {
        val target = Money(100_000)
        assertEquals(200f, ReputationManager.computeRevenueScore(Money(200_000), target), 0.01f)
    }

    @Test
    fun `computeRevenueScore zero revenue returns 0`() {
        val target = Money(100_000)
        assertEquals(0f, ReputationManager.computeRevenueScore(Money.ZERO, target), 0.01f)
    }

    @Test
    fun `computeRevenueScore half target returns 50`() {
        val target = Money(100_000)
        assertEquals(50f, ReputationManager.computeRevenueScore(Money(50_000), target), 0.01f)
    }

    // ── Sub-score: Stock ──────────────────────────────────────────────────────

    @Test
    fun `computeStockScore 0 percent OOS returns 200`() {
        assertEquals(200f, ReputationManager.computeStockScore(itemsSold = 100, itemsLostToOOS = 0), 0.01f)
    }

    @Test
    fun `computeStockScore 25 percent OOS returns 100`() {
        assertEquals(100f, ReputationManager.computeStockScore(itemsSold = 75, itemsLostToOOS = 25), 0.01f)
    }

    @Test
    fun `computeStockScore 50 percent OOS returns 0`() {
        assertEquals(0f, ReputationManager.computeStockScore(itemsSold = 50, itemsLostToOOS = 50), 0.01f)
    }

    @Test
    fun `computeStockScore zero total returns 100 neutral`() {
        assertEquals(100f, ReputationManager.computeStockScore(itemsSold = 0, itemsLostToOOS = 0), 0.01f)
    }

    // ── Sub-score: Appearance ─────────────────────────────────────────────────

    @Test
    fun `computeAppearanceScore zone 1 returns 200`() {
        assertEquals(200f, ReputationManager.computeAppearanceScore(1.0f), 0.01f)
    }

    @Test
    fun `computeAppearanceScore zone 0 point 5 returns 100`() {
        assertEquals(100f, ReputationManager.computeAppearanceScore(0.5f), 0.01f)
    }

    @Test
    fun `computeAppearanceScore zone 0 returns 0`() {
        assertEquals(0f, ReputationManager.computeAppearanceScore(0.0f), 0.01f)
    }

    // ── Composite ────────────────────────────────────────────────────────────

    @Test
    fun `weights sum to 1 point 0`() {
        val sum = ReputationManager.WEIGHT_REVENUE + ReputationManager.WEIGHT_STOCK + ReputationManager.WEIGHT_APPEARANCE
        assertEquals(1.0f, sum, 0.01f)
    }

    @Test
    fun `composite of all max subscores is 200 and clamped`() {
        // revenue=2x target (200), OOS=0% (200), zone=1.0 (200) → weighted sum = 200
        val state = stateWithTarget(100_000)
        val result = ReputationManager.updateReputation(
            state,
            snapshot(subtotalCents = 200_000, itemsSold = 100, itemsLostToOutOfStock = 0, avgZoneScore = 1.0f),
            StoreSize.GROCERY_STORE,
        )
        assertEquals(200f, result.lastDailyComposite, 1.0f)
    }

    @Test
    fun `composite of all zero subscores is 0 and clamped`() {
        // revenue=0 (0), OOS=100% (0), zone=0.0 (0) → weighted sum = 0
        val state = stateWithTarget(100_000)
        val result = ReputationManager.updateReputation(
            state,
            snapshot(subtotalCents = 0, itemsSold = 0, itemsLostToOutOfStock = 100, avgZoneScore = 0.0f),
            StoreSize.GROCERY_STORE,
        )
        assertEquals(0f, result.lastDailyComposite, 1.0f)
    }

    // ── Nudge smoothing ───────────────────────────────────────────────────────

    @Test
    fun `smoothReputation daysTracked 0 returns 100 regardless of composite`() {
        assertEquals(100f, ReputationManager.smoothReputation(50f, 200f, daysTracked = 0), 0.01f)
        assertEquals(100f, ReputationManager.smoothReputation(150f, 0f, daysTracked = 0), 0.01f)
    }

    @Test
    fun `smoothReputation perfect day at rep 100 returns 103`() {
        // delta=100 >= 75 threshold, rising → +3
        assertEquals(103f, ReputationManager.smoothReputation(100f, 200f, daysTracked = 1), 0.01f)
    }

    @Test
    fun `smoothReputation slightly bad day at rep 100 returns 99`() {
        // delta=-20, absDelta < 25 → nudge=1, falling → -1
        assertEquals(99f, ReputationManager.smoothReputation(100f, 80f, daysTracked = 1), 0.01f)
    }

    @Test
    fun `smoothReputation horrific day at rep 100 returns 98`() {
        // delta=-100 >= 75 threshold, falling → capped at -2
        assertEquals(98f, ReputationManager.smoothReputation(100f, 0f, daysTracked = 1), 0.01f)
    }

    @Test
    fun `smoothReputation max rise is 3 per day`() {
        // Verify asymmetric cap: rising max = +3
        val result = ReputationManager.smoothReputation(100f, 200f, daysTracked = 1)
        assertEquals(103f, result, 0.01f)
    }

    @Test
    fun `smoothReputation max fall is 2 per day`() {
        // Verify asymmetric cap: falling max = -2, not -3
        val result = ReputationManager.smoothReputation(100f, 0f, daysTracked = 1)
        assertEquals(98f, result, 0.01f)
    }

    @Test
    fun `30 perfect days from rep 100 builds substantial but not maxed reputation`() {
        // Perfect composite (200) every day — should make good progress but never trivially
        // hit 200 in 30 days. Demonstrates the "hard to max" design goal.
        var rep = 100f
        repeat(30) { i ->
            rep = ReputationManager.smoothReputation(rep, 200f, daysTracked = i + 1)
        }
        assertTrue("Expected meaningful progress above 150 after 30 perfect days, got $rep", rep > 150f)
        assertTrue("Expected rep < 200 after 30 perfect days (hard to max), got $rep", rep < 200f)
    }

    @Test
    fun `single horrific day at rep 150 falls to 148`() {
        // Fall capped at 2 regardless of how bad the composite is
        assertEquals(148f, ReputationManager.smoothReputation(150f, 0f, daysTracked = 1), 0.01f)
    }

    // ── GOOB Sale ─────────────────────────────────────────────────────────────

    @Test
    fun `checkGoobSaleEligible returns true when below threshold and cooldown elapsed`() {
        val state = ReputationState(reputationScore = 25f, lastSaleEventDay = 0)
        assertTrue(ReputationManager.checkGoobSaleEligible(state, currentDay = 10))
    }

    @Test
    fun `checkGoobSaleEligible returns false when cooldown not elapsed`() {
        // 10 - 5 = 5 days elapsed, need >= 7
        val state = ReputationState(reputationScore = 25f, lastSaleEventDay = 5)
        assertFalse(ReputationManager.checkGoobSaleEligible(state, currentDay = 10))
    }

    @Test
    fun `checkGoobSaleEligible returns false when reputation above threshold`() {
        val state = ReputationState(reputationScore = 50f, lastSaleEventDay = 0)
        assertFalse(ReputationManager.checkGoobSaleEligible(state, currentDay = 100))
    }

    @Test
    fun `applyGoobSale sets goobSaleActiveToday true and records lastSaleEventDay`() {
        val state = ReputationState(reputationScore = 25f, goobSaleActiveToday = false, lastSaleEventDay = 0)
        val result = ReputationManager.applyGoobSale(state, currentDay = 10)
        assertTrue(result.goobSaleActiveToday)
        assertEquals(10, result.lastSaleEventDay)
    }

    // ── Target ratchet ────────────────────────────────────────────────────────

    @Test
    fun `adjustTarget hit with zero overshoot grows by 1 percent`() {
        val target = Money(100_000)
        // revenue == target → overshoot=0 → growthRate=0.01 → newTarget=101_000
        val result = ReputationManager.adjustTarget(target, todayRevenue = target, baseTarget = Money(80_000))
        assertEquals(101_000L, result.cents)
    }

    @Test
    fun `adjustTarget hit with full overshoot grows by 2 percent`() {
        val target = Money(100_000)
        // revenue = 2x target → overshoot=1.0 → growthRate=0.02 → newTarget=102_000
        val result = ReputationManager.adjustTarget(target, todayRevenue = Money(200_000), baseTarget = Money(80_000))
        assertEquals(102_000L, result.cents)
    }

    @Test
    fun `adjustTarget miss with near-zero undershoot shrinks by approximately 0 point 5 percent`() {
        val target = Money(100_000)
        // revenue barely below target → undershoot≈0 → decayRate≈0.005 → newTarget≈99_500
        val result = ReputationManager.adjustTarget(target, todayRevenue = Money(99_999), baseTarget = Money(50_000))
        assertTrue("Expected ~99_500 (0.5% decay), got ${result.cents}", result.cents in 99_400L..99_600L)
    }

    @Test
    fun `adjustTarget miss with full undershoot shrinks by 1 point 5 percent`() {
        val target = Money(100_000)
        // revenue=0 → undershoot=1.0 → decayRate=0.015 → newTarget=98_500
        val result = ReputationManager.adjustTarget(target, todayRevenue = Money.ZERO, baseTarget = Money(50_000))
        assertEquals(98_500L, result.cents)
    }

    @Test
    fun `adjustTarget repeated misses never drop below baseTarget`() {
        val baseTarget = Money(80_000)
        var target = baseTarget
        repeat(10) {
            target = ReputationManager.adjustTarget(target, todayRevenue = Money.ZERO, baseTarget = baseTarget)
        }
        assertTrue("Target must not fall below baseTarget, got ${target.cents}", target.cents >= baseTarget.cents)
    }

    // ── Multiplier curves ─────────────────────────────────────────────────────

    @Test
    fun `deriveTrafficMultiplier rep 0 returns 0 point 5`() {
        assertEquals(0.5f, ReputationManager.deriveTrafficMultiplier(0f), 0.01f)
    }

    @Test
    fun `deriveTrafficMultiplier rep 100 returns 1 point 0`() {
        assertEquals(1.0f, ReputationManager.deriveTrafficMultiplier(100f), 0.01f)
    }

    @Test
    fun `deriveTrafficMultiplier rep 200 returns 2 point 0`() {
        assertEquals(2.0f, ReputationManager.deriveTrafficMultiplier(200f), 0.01f)
    }

    @Test
    fun `derivePriceToleranceMultiplier rep 0 returns 0 point 7`() {
        assertEquals(0.7f, ReputationManager.derivePriceToleranceMultiplier(0f), 0.01f)
    }

    @Test
    fun `derivePriceToleranceMultiplier rep 100 returns 1 point 0`() {
        assertEquals(1.0f, ReputationManager.derivePriceToleranceMultiplier(100f), 0.01f)
    }

    @Test
    fun `derivePriceToleranceMultiplier rep 200 returns 1 point 4`() {
        assertEquals(1.4f, ReputationManager.derivePriceToleranceMultiplier(200f), 0.01f)
    }

    @Test
    fun `deriveSupplierBonus rep 100 returns 0`() {
        assertEquals(0f, ReputationManager.deriveSupplierBonus(100f), 0.01f)
    }

    @Test
    fun `deriveSupplierBonus rep 200 returns 0 point 10`() {
        assertEquals(0.10f, ReputationManager.deriveSupplierBonus(200f), 0.01f)
    }

    @Test
    fun `deriveSupplierBonus rep 50 returns 0`() {
        assertEquals(0f, ReputationManager.deriveSupplierBonus(50f), 0.01f)
    }

    // ── Feature gating ────────────────────────────────────────────────────────

    @Test
    fun `updateReputation with null baseRevenueTarget returns state unchanged`() {
        val state = ReputationState()
        val result = ReputationManager.updateReputation(
            state,
            snapshot(subtotalCents = 100_000),
            StoreSize.MOM_AND_POP,
        )
        assertEquals(state, result)
    }

    // ── Streak tracking ───────────────────────────────────────────────────────

    @Test
    fun `two consecutive revenue hits gives consecutiveTargetHits 2 and misses 0`() {
        val initial = stateWithTarget(100_000)
        // Use revenue well above target so the second call also hits after target grows
        val hitSnapshot = snapshot(subtotalCents = 200_000)
        val after1 = ReputationManager.updateReputation(initial, hitSnapshot, StoreSize.GROCERY_STORE)
        val after2 = ReputationManager.updateReputation(after1, hitSnapshot, StoreSize.GROCERY_STORE)
        assertEquals(2, after2.consecutiveTargetHits)
        assertEquals(0, after2.consecutiveTargetMisses)
    }

    @Test
    fun `miss after two hits resets consecutiveTargetHits to 0 and increments misses to 1`() {
        val initial = stateWithTarget(100_000)
        val hitSnapshot = snapshot(subtotalCents = 200_000)
        val missSnapshot = snapshot(subtotalCents = 0)
        val after1 = ReputationManager.updateReputation(initial, hitSnapshot, StoreSize.GROCERY_STORE)
        val after2 = ReputationManager.updateReputation(after1, hitSnapshot, StoreSize.GROCERY_STORE)
        val after3 = ReputationManager.updateReputation(after2, missSnapshot, StoreSize.GROCERY_STORE)
        assertEquals(0, after3.consecutiveTargetHits)
        assertEquals(1, after3.consecutiveTargetMisses)
    }

    // ── Integration gap coverage ──────────────────────────────────────────────

    // TrafficManager reads GOOB_SALE_TRAFFIC_BOOST directly from the companion — pin the value.
    @Test
    fun `GOOB_SALE_TRAFFIC_BOOST constant is 1 point 5`() {
        assertEquals(1.5f, ReputationManager.GOOB_SALE_TRAFFIC_BOOST, 0.01f)
    }

    // DayRolloverProcessor clears goobSaleActiveToday in the else-branch when not eligible.
    // Verify the flag is false on a state where GOOB was active but rep recovered above threshold.
    @Test
    fun `checkGoobSaleEligible false after rep recovers so DayRollover else-branch fires correctly`() {
        val state = ReputationState(
            reputationScore = 50f,       // above GOOB_SALE_THRESHOLD (30)
            goobSaleActiveToday = true,  // was active yesterday
            lastSaleEventDay = 0,
        )
        // checkGoobSaleEligible must return false — DayRollover then does copy(goobSaleActiveToday=false)
        assertFalse(ReputationManager.checkGoobSaleEligible(state, currentDay = 10))
    }

    // Boundary: rep == GOOB_SALE_THRESHOLD (30f) is NOT < 30f → not eligible.
    @Test
    fun `checkGoobSaleEligible returns false when reputation equals threshold exactly`() {
        val state = ReputationState(reputationScore = 30f, lastSaleEventDay = 0)
        assertFalse(ReputationManager.checkGoobSaleEligible(state, currentDay = 10))
    }

    // Boundary: cooldown of exactly 7 days elapsed → eligible (>= 7).
    @Test
    fun `checkGoobSaleEligible returns true when exactly 7 days have elapsed since last sale`() {
        val state = ReputationState(reputationScore = 25f, lastSaleEventDay = 3)
        assertTrue(ReputationManager.checkGoobSaleEligible(state, currentDay = 10)) // 10-3=7
    }

    // updateReputation must initialize currentRevenueTarget from storeSize.baseRevenueTarget
    // when the state carries the default Money.ZERO (first day at a new store size).
    @Test
    fun `updateReputation initializes currentRevenueTarget from storeSize when Money ZERO`() {
        val state = ReputationState() // currentRevenueTarget == Money.ZERO
        val result = ReputationManager.updateReputation(
            state,
            snapshot(subtotalCents = 100_000),
            StoreSize.GROCERY_STORE,
        )
        assertTrue(
            "Target must be initialized from GROCERY_STORE base (80_000), got ${result.currentRevenueTarget}",
            result.currentRevenueTarget != Money.ZERO,
        )
    }
}
