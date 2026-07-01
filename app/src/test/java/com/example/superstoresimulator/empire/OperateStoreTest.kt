package com.example.superstoresimulator.empire

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.empire.EmpireClock
import com.example.superstoresimulator.domain.empire.EmpireSpeed
import com.example.superstoresimulator.domain.empire.EmpireTuning
import com.example.superstoresimulator.domain.empire.OperateStoreController
import com.example.superstoresimulator.domain.empire.RegionRegistry
import com.example.superstoresimulator.domain.empire.SecondaryStore
import com.example.superstoresimulator.domain.empire.StoreOperatingState
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [OperateStoreController] (TRACK D — Operate-a-Store).
 * Pure-function driven: GameState built via copy(). No GameEngine, no Mockito.
 */
class OperateStoreTest {

    private fun store(
        id: Int = 1,
        size: StoreSize = StoreSize.GROCERY_STORE,
        operatingState: StoreOperatingState? = null,
        operatingPerformance: Float = 1.0f,
        lastOperatedDay: Int? = null,
    ) = SecondaryStore(
        storeId = id,
        storeName = "Store #$id",
        regionId = RegionRegistry.HOME_REGION_ID,
        storeSize = size,
        operatingState = operatingState,
        operatingPerformance = operatingPerformance,
        lastOperatedDay = lastOperatedDay,
    )

    /** Empire-mode state seeded with regions so the sim baseline is non-zero. */
    private fun empireState(
        stores: List<SecondaryStore>,
        operatingStoreId: Int? = null,
        day: Int = 3,
        sessionStartDay: Int? = null,
    ) = GameState().copy(
        empireModeActive = true,
        regions = RegionRegistry.authored,
        secondaryStores = stores,
        operatingStoreId = operatingStoreId,
        operateSessionStartDay = sessionStartDay,
        currentTime = GameTime(totalMinutesElapsed = day.toLong() * 1440L),
        empireClock = EmpireClock(speed = EmpireSpeed.NORMAL),
        playerPausedTime = true,
    )

    // ── dropIn ────────────────────────────────────────────────────────────────

    @Test
    fun dropIn_setsOperatingStore_pausesEmpireClock_resumesTick_loadsSize() {
        val target = store(id = 7, size = StoreSize.SUPERSTORE)
        val state = empireState(stores = listOf(target))

        val result = OperateStoreController.dropIn(state, 7)

        assertEquals(7, result.operatingStoreId)
        assertEquals(EmpireSpeed.PAUSED, result.empireClock.speed)
        assertFalse("minute tick resumes for the store", result.playerPausedTime)
        assertEquals("loads the store's size", StoreSize.SUPERSTORE, result.currentStoreSize)
        assertEquals(
            "store config backroom cap follows store size",
            StoreSize.SUPERSTORE.backroomCapPerItem,
            result.storeConfig.backroomCapPerItem,
        )
    }

    @Test
    fun dropIn_reconstructsOperableState_whenOperatingStateNull() {
        val target = store(id = 3, operatingState = null)
        val state = empireState(stores = listOf(target))

        val result = OperateStoreController.dropIn(state, 3)

        // Reconstruction yields an operable loop-1 state: empty inventory + one register.
        assertNotNull("operable state reconstructed", result.registers)
        assertTrue("inventory reconstructed empty", result.inventory.isEmpty())
        assertEquals("one register reconstructed", 1, result.registers.size)
        assertEquals(0, result.registers[0].registerId)
    }

    @Test
    fun dropIn_isNoOp_whenStoreNotFound() {
        val state = empireState(stores = listOf(store(id = 1)))
        val result = OperateStoreController.dropIn(state, 99)
        assertSame("unknown store id leaves state untouched", state, result)
    }

    // ── dropOut ───────────────────────────────────────────────────────────────

    @Test
    fun dropOut_clearsOperatingStore_snapshotsState_setsLastDay_clampsPerf() {
        // Huge realized net would push perf well above MAX → expect it clamped.
        val richDay = DailyMetrics(dayNumber = 3, subtotal = Money.fromDollars(1_000_000.0))
        val state = empireState(
            stores = listOf(store(id = 5, size = StoreSize.GROCERY_STORE)),
            operatingStoreId = 5,
            day = 9,
            sessionStartDay = 8, // one full day played → score the session
        ).copy(completedDayMetrics = listOf(richDay))

        val result = OperateStoreController.dropOut(state)

        assertNull("operating store cleared", result.operatingStoreId)
        assertNull("session start cleared", result.operateSessionStartDay)
        assertEquals(EmpireSpeed.PAUSED, result.empireClock.speed)

        val store = result.secondaryStores.first { it.storeId == 5 }
        assertNotNull("operating state snapshotted", store.operatingState)
        assertEquals("last operated day = current day", 9, store.lastOperatedDay)
        assertTrue(
            "performance clamped within [MIN, MAX]",
            store.operatingPerformance >= EmpireTuning.OPERATING_PERFORMANCE_MIN &&
                store.operatingPerformance <= EmpireTuning.OPERATING_PERFORMANCE_MAX,
        )
        assertEquals(
            "huge realized net clamps to MAX",
            EmpireTuning.OPERATING_PERFORMANCE_MAX,
            store.operatingPerformance,
            0.0001f,
        )
    }

    @Test
    fun dropOut_leavesPerfUnchanged_whenNoFullDayPlayed() {
        // Dropped in and out within the same day → no completed day to judge.
        val richDay = DailyMetrics(dayNumber = 3, subtotal = Money.fromDollars(1_000_000.0))
        val state = empireState(
            stores = listOf(store(id = 5, operatingPerformance = 1.1f)),
            operatingStoreId = 5,
            day = 8,
            sessionStartDay = 8, // same day → daysPlayed = 0
        ).copy(completedDayMetrics = listOf(richDay))

        val result = OperateStoreController.dropOut(state)
        val store = result.secondaryStores.first { it.storeId == 5 }
        assertEquals("sub-day session does not move performance", 1.1f, store.operatingPerformance, 0.0001f)
    }

    @Test
    fun dropOut_snapshotsPerStoreMetrics_notGlobalLeftovers() {
        // The operated store's own metrics get snapshotted into its operatingState.
        val day = DailyMetrics(dayNumber = 7, subtotal = Money.fromDollars(500.0))
        val state = empireState(
            stores = listOf(store(id = 5)),
            operatingStoreId = 5,
            day = 7,
        ).copy(completedDayMetrics = listOf(day))

        val snapshot = OperateStoreController.dropOut(state)
            .secondaryStores.first { it.storeId == 5 }.operatingState
        assertNotNull(snapshot)
        assertEquals(listOf(day), snapshot!!.completedDayMetrics)
    }

    @Test
    fun dropOut_isNoOp_whenNotOperating() {
        val state = empireState(stores = listOf(store(id = 1)), operatingStoreId = null)
        val result = OperateStoreController.dropOut(state)
        assertSame(state, result)
    }

    // ── decayOperatingPerformance ───────────────────────────────────────────────

    @Test
    fun decay_movesPerfTowardOne_overDays() {
        val s = store(operatingPerformance = 1.4f, lastOperatedDay = 0)

        // 1 day after operating: should move partway toward 1.0 but not reach it.
        val afterOne = OperateStoreController.decayOperatingPerformance(s, currentDayIndex = 1)
        assertTrue("perf drifts down toward 1.0", afterOne.operatingPerformance < 1.4f)
        assertTrue("but stays above 1.0 mid-window", afterOne.operatingPerformance > 1.0f)
    }

    @Test
    fun decay_reachesExactlyOne_atDecayWindow() {
        val s = store(operatingPerformance = 1.5f, lastOperatedDay = 0)
        val result = OperateStoreController.decayOperatingPerformance(
            s,
            currentDayIndex = EmpireTuning.OPERATING_PERFORMANCE_DECAY_DAYS,
        )
        assertEquals(1.0f, result.operatingPerformance, 0.0f)
    }

    @Test
    fun decay_isNoOp_whenNeverOperated() {
        val s = store(operatingPerformance = 1.3f, lastOperatedDay = null)
        val result = OperateStoreController.decayOperatingPerformance(s, currentDayIndex = 50)
        assertSame("never-operated store is unchanged", s, result)
    }
}
