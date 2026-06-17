package com.example.superstoresimulator.domain.metrics

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.store.StoreSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BuildingPurchaseTest {

    private lateinit var dayManager: DayManager

    @Before
    fun setUp() {
        dayManager = DayManager()
    }

    private fun stateWith(
        buildingOwned: Boolean = false,
        storeSize: StoreSize = StoreSize.SUPERCENTER,
        money: Money = Money(500_000_000L),
    ): GameState = GameState(
        buildingOwned = buildingOwned,
        currentStoreSize = storeSize,
        money = money,
        currentDayMetrics = DailyMetrics(),
    )

    // ── Rent elimination ─────────────────────────────────────────────────────

    @Test
    fun `rollOverDay deducts zero rent when building is owned`() {
        val state = stateWith(buildingOwned = true)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertEquals(state.money, result.money)
    }

    @Test
    fun `rollOverDay deducts normal rent when building is not owned`() {
        val state = stateWith(buildingOwned = false)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertEquals(state.money - StoreSize.SUPERCENTER.dailyRent, result.money)
    }

    @Test
    fun `rollOverDay deducts normal rent for each store size when not owned`() {
        for (size in StoreSize.entries) {
            val state = stateWith(buildingOwned = false, storeSize = size)
            val result = dayManager.rollOverDay(state, dayNumber = 0)
            assertEquals(
                "Rent mismatch for $size",
                state.money - size.dailyRent,
                result.money,
            )
        }
    }

    @Test
    fun `rollOverDay deducts zero rent for each store size when owned`() {
        for (size in StoreSize.entries) {
            val state = stateWith(buildingOwned = true, storeSize = size)
            val result = dayManager.rollOverDay(state, dayNumber = 0)
            assertEquals("Money should be unchanged for $size when owned", state.money, result.money)
        }
    }

    // ── Metrics snapshot ─────────────────────────────────────────────────────

    @Test
    fun `metrics snapshot records zero rent when building is owned`() {
        val state = stateWith(buildingOwned = true)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertEquals(Money.ZERO, result.completedDayMetrics.last().rentPaid)
    }

    @Test
    fun `metrics snapshot records actual rent when building is not owned`() {
        val state = stateWith(buildingOwned = false)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertEquals(StoreSize.SUPERCENTER.dailyRent, result.completedDayMetrics.last().rentPaid)
    }

    // ── Default state ────────────────────────────────────────────────────────

    @Test
    fun `buildingOwned defaults to false in GameState`() {
        assertFalse(GameState().buildingOwned)
    }
}
