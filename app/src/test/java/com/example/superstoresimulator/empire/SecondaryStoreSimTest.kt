package com.example.superstoresimulator.empire

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.empire.EmpireTuning
import com.example.superstoresimulator.domain.empire.ManagerPersonality
import com.example.superstoresimulator.domain.empire.RegionRegistry
import com.example.superstoresimulator.domain.empire.RegionalManagerManager
import com.example.superstoresimulator.domain.empire.SecondaryStore
import com.example.superstoresimulator.domain.empire.SecondaryStoreSimManager
import com.example.superstoresimulator.domain.empire.StoreDirection
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TRACK A sim-engine tests. Plain JUnit4, no Mockito.
 *
 * Covers direction traffic ordering, region saturation, cannibalization, money folding,
 * metric rollover, manager hire gating, and the manager profit-floor assignment.
 */
class SecondaryStoreSimTest {

    private val HOME = RegionRegistry.HOME_REGION_ID // capacity 20, neutral demand profile
    private val RURAL = 1                            // capacity 8

    private fun store(
        id: Int,
        region: Int = HOME,
        size: StoreSize = StoreSize.GROCERY_STORE,
        direction: StoreDirection = StoreDirection.BALANCED,
    ) = SecondaryStore(
        storeId = id,
        storeName = "Store $id",
        regionId = region,
        storeSize = size,
        direction = direction,
    )

    private fun empire(vararg stores: SecondaryStore): GameState =
        GameState().copy(
            empireModeActive = true,
            regions = RegionRegistry.authored,
            secondaryStores = stores.toList(),
        )

    // (1) AGGRESSIVE traffic > BALANCED > PASSIVE for the same store.
    @Test
    fun directionTrafficOrdering() {
        // satFactor 1f: single grocery store (weight 4) well under home capacity (20).
        // Read customers off the produced metrics by simulating one day per direction.
        fun customers(dir: StoreDirection): Int =
            SecondaryStoreSimManager.simulateDay(empire(store(1, direction = dir)))
                .secondaryStores.first().currentDayMetrics.customers
        val aggressive = customers(StoreDirection.AGGRESSIVE)
        val balanced = customers(StoreDirection.BALANCED)
        val passive = customers(StoreDirection.PASSIVE)
        assertTrue("AGGRESSIVE ($aggressive) > BALANCED ($balanced)", aggressive > balanced)
        assertTrue("BALANCED ($balanced) > PASSIVE ($passive)", balanced > passive)
    }

    // (2) Saturation: 1f under capacity, < 1f over, decreasing as weight rises.
    @Test
    fun saturationFactorBehavior() {
        // Rural capacity is 8.
        val under = SecondaryStoreSimManager.saturationFactor(totalWeight = 6f, capacity = 8f)
        assertEquals(1f, under, 0.0001f)

        val atCap = SecondaryStoreSimManager.saturationFactor(totalWeight = 8f, capacity = 8f)
        assertEquals(1f, atCap, 0.0001f)

        val over1 = SecondaryStoreSimManager.saturationFactor(totalWeight = 12f, capacity = 8f)
        val over2 = SecondaryStoreSimManager.saturationFactor(totalWeight = 20f, capacity = 8f)
        assertTrue("over capacity < 1f", over1 < 1f)
        assertTrue("heavier load saturates harder", over2 < over1)
    }

    // (3) Cannibalization: adding a store to a crowded region lowers an EXISTING store's
    //     traffic/profit.
    @Test
    fun addingStoreCannibalizesExisting() {
        // Two GROCERY_STOREs (weight 4 each = 8) fit rural exactly; a third pushes it over.
        val before = empire(
            store(1, region = RURAL),
            store(2, region = RURAL),
        )
        val after = empire(
            store(1, region = RURAL),
            store(2, region = RURAL),
            store(3, region = RURAL),
        )
        val store1Before = SecondaryStoreSimManager.simulateDay(before)
            .secondaryStores.first { it.storeId == 1 }.currentDayMetrics
        val store1After = SecondaryStoreSimManager.simulateDay(after)
            .secondaryStores.first { it.storeId == 1 }.currentDayMetrics

        assertTrue(
            "more stores → fewer customers for existing store",
            store1After.customers < store1Before.customers,
        )
        assertTrue(
            "more stores → lower profit for existing store",
            store1After.netProfit < store1Before.netProfit,
        )
    }

    // (4) Net profit folds into money after simulateDay.
    @Test
    fun netProfitFoldsIntoMoney() {
        val state = empire(store(1), store(2))
        val after = SecondaryStoreSimManager.simulateDay(state)
        val summed = after.secondaryStores.sumOf { it.currentDayMetrics.netProfit.cents }
        assertEquals(state.money.cents + summed, after.money.cents)
        // No manager → no salary deducted, and these profitable stores grow money.
        assertTrue(after.money.cents > state.money.cents)
    }

    // (5) currentDayMetrics rolls into completedDayMetrics across two simulateDay calls.
    @Test
    fun metricsRollover() {
        val day0 = empire(store(1))
        val afterDay0 = SecondaryStoreSimManager.simulateDay(day0)
        val s0 = afterDay0.secondaryStores.first()
        assertEquals(0, s0.currentDayMetrics.dayIndex)
        // First run should NOT push the seed metrics (same dayIndex) into completed.
        assertTrue("no dup on first run", s0.completedDayMetrics.isEmpty())

        // Advance the clock by one day and run again.
        val day1 = afterDay0.copy(currentTime = GameTime(totalMinutesElapsed = 1440))
        val afterDay1 = SecondaryStoreSimManager.simulateDay(day1)
        val s1 = afterDay1.secondaryStores.first()
        assertEquals(1, s1.currentDayMetrics.dayIndex)
        assertEquals(1, s1.completedDayMetrics.size)
        assertEquals(0, s1.completedDayMetrics.first().dayIndex)
    }

    // (6) Manager hire gated by store count (hire with < 3 stores is a no-op).
    @Test
    fun managerHireGatedByStoreCount() {
        val tooFew = empire(store(1), store(2))
        val notHired = RegionalManagerManager.hire(tooFew, ManagerPersonality.AGGRESSIVE)
        assertTrue("hire with < 3 stores is a no-op", notHired.regionalManager == null)

        val enough = empire(store(1), store(2), store(3))
        val hired = RegionalManagerManager.hire(enough, ManagerPersonality.AGGRESSIVE)
        assertTrue("hire with >= 3 stores succeeds", hired.regionalManager != null)
        assertTrue("managed stores flagged", hired.secondaryStores.all { it.managedByRegionalManager })
    }

    // (7) Manager assignment keeps empire net >= floor while maximizing preferred-direction
    //     stores. Under the authored tuning these stores stay profitable even when crowded,
    //     so the manager should keep them ALL on the preferred direction (no needless flips).
    @Test
    fun managerKeepsNetAboveFloorWhileMaximizingPreferred() {
        val state = empire(
            store(1, region = RURAL),
            store(2, region = RURAL),
            store(3, region = RURAL),
            store(4, region = RURAL),
        )
        val hired = RegionalManagerManager.hire(state, ManagerPersonality.AGGRESSIVE)
        assertTrue(hired.regionalManager != null)

        val stores = hired.secondaryStores

        // Empire net at the assigned directions must clear the floor.
        val satFactor = run {
            val region = hired.regions.first { it.regionId == RURAL }
            val weight = stores.filter { it.regionId == RURAL }
                .map { SecondaryStoreSimManager.storeWeight(it) }.sum()
            SecondaryStoreSimManager.saturationFactor(weight, region.capacity)
        }
        var net = Money.ZERO
        for (s in stores) {
            net += SecondaryStoreSimManager.estimateStoreNetProfit(hired, s, satFactor)
        }
        net -= EmpireTuning.MANAGER_SALARY_PER_DAY
        assertTrue("empire net ($net) >= floor", net >= EmpireTuning.MANAGER_PROFIT_FLOOR)

        // Profitable preferred direction → every managed store stays on it (max preferred).
        assertTrue(
            "all stores kept on preferred AGGRESSIVE direction",
            stores.all { it.direction == StoreDirection.AGGRESSIVE },
        )
        assertTrue("all stores flagged managed", stores.all { it.managedByRegionalManager })
    }
}
