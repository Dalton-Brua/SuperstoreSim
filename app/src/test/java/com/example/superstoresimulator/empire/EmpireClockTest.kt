package com.example.superstoresimulator.empire

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.empire.DemandProfile
import com.example.superstoresimulator.domain.empire.EmpireClock
import com.example.superstoresimulator.domain.empire.EmpireClockController
import com.example.superstoresimulator.domain.empire.EmpireSpeed
import com.example.superstoresimulator.domain.empire.Region
import com.example.superstoresimulator.domain.empire.SecondaryStore
import com.example.superstoresimulator.domain.empire.SimStoreMetrics
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [EmpireClockController]. SecondaryStoreSimManager.simulateDay is currently a
 * stub returning state unchanged, so these exercise only logic fully owned by Track C:
 * the ms accumulator / day stepping, the no-op guards, the loss decision-pause, and
 * setSpeed clearing a pending decision.
 */
class EmpireClockTest {

    /** Empire-active state with no stores and money low enough that no decision-pause fires. */
    private fun activeState(speed: EmpireSpeed = EmpireSpeed.NORMAL): GameState =
        GameState().copy(
            empireModeActive = true,
            money = Money.ZERO,
            currentTime = GameTime(0),
            empireClock = EmpireClock(speed = speed),
            secondaryStores = emptyList(),
            regions = emptyList(),
        )

    @Test
    fun tickWhenPausedDoesNothing() {
        val state = activeState(EmpireSpeed.PAUSED)
        val result = EmpireClockController.tick(state, 10_000L)
        assertEquals(state, result)
        assertEquals(0, result.currentTime.dayNumber)
    }

    @Test
    fun tickAccumulatesAndAdvancesDayAtNormal() {
        val state = activeState(EmpireSpeed.NORMAL)
        // 3000 ms = 1 day at NORMAL.
        val result = EmpireClockController.tick(state, 3_000L)
        assertEquals(1, result.currentTime.dayNumber)
        assertEquals(0L, result.empireClock.msAccumulator)
    }

    @Test
    fun tickPartialMsAccumulatesWithoutAdvancing() {
        val state = activeState(EmpireSpeed.NORMAL)
        val result = EmpireClockController.tick(state, 1_500L)
        assertEquals(0, result.currentTime.dayNumber)
        assertEquals(1_500L, result.empireClock.msAccumulator)
    }

    @Test
    fun fastAdvancesMoreDaysThanNormalForSameElapsedMs() {
        val elapsed = 3_000L
        val normalResult = EmpireClockController.tick(activeState(EmpireSpeed.NORMAL), elapsed)
        val fastResult = EmpireClockController.tick(activeState(EmpireSpeed.FAST), elapsed)
        // NORMAL: 3000/3000 = 1 day. FAST: 3000/600 = 5 days.
        assertEquals(1, normalResult.currentTime.dayNumber)
        assertEquals(5, fastResult.currentTime.dayNumber)
        assertTrue(fastResult.currentTime.dayNumber > normalResult.currentTime.dayNumber)
    }

    @Test
    fun tickNoOpWhenOperatingStore() {
        val state = activeState(EmpireSpeed.NORMAL).copy(operatingStoreId = 3)
        val result = EmpireClockController.tick(state, 10_000L)
        assertEquals(state, result)
        assertEquals(0, result.currentTime.dayNumber)
    }

    @Test
    fun tickNoOpWhenEmpireModeInactive() {
        val state = activeState(EmpireSpeed.NORMAL).copy(empireModeActive = false)
        val result = EmpireClockController.tick(state, 10_000L)
        assertEquals(state, result)
        assertEquals(0, result.currentTime.dayNumber)
    }

    @Test
    fun losingStoreAdvanceOneDayPausesWithReason() {
        // A region with zero base traffic → 0 customers → 0 revenue, but a positive
        // operating cost, so the real sim produces a guaranteed daily loss.
        val deadRegion = Region(
            regionId = 0,
            name = "Ghost Town",
            demandProfile = DemandProfile(baseSpendingPower = 1.0f, baseTraffic = 0.0f, growthRate = 0.0f),
            capacity = 10f,
            unlockCost = Money.ZERO,
            unlocked = true,
        )
        val losingStore = SecondaryStore(
            storeId = 1,
            storeName = "Red Ink Mart",
            regionId = 0,
            storeSize = StoreSize.MOM_AND_POP,
        )
        val state = GameState().copy(
            empireModeActive = true,
            money = Money.ZERO,
            empireClock = EmpireClock(speed = EmpireSpeed.NORMAL),
            secondaryStores = listOf(losingStore),
            regions = listOf(deadRegion),
        )

        val result = EmpireClockController.advanceOneDay(state)

        assertEquals(EmpireSpeed.PAUSED, result.empireClock.speed)
        val reason = result.empireClock.pendingDecisionReason
        assertNotNull(reason)
        assertTrue(reason!!.contains("loss", ignoreCase = true))
    }

    @Test
    fun setSpeedClearsPendingDecisionReason() {
        val paused = GameState().copy(
            empireModeActive = true,
            empireClock = EmpireClock(
                speed = EmpireSpeed.PAUSED,
                pendingDecisionReason = "A store is operating at a loss.",
            ),
        )
        val resumed = EmpireClockController.setSpeed(paused, EmpireSpeed.NORMAL)
        assertEquals(EmpireSpeed.NORMAL, resumed.empireClock.speed)
        assertNull(resumed.empireClock.pendingDecisionReason)
    }
}
