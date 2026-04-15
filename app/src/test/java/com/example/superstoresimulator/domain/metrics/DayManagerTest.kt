package com.example.superstoresimulator.domain.metrics

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DayManager].
 *
 * [DayManager] owns two pure functions ([rollOverDay], [dismissEndOfDayReport]) and
 * one piece of internal mutable state ([lastKnownDayNumber]). Tests construct
 * [GameState] directly with named parameters — no FakeItemDao or GameEngine needed.
 *
 * Covers:
 *  [rollOverDay]
 *   - Snapshot is appended to completedDayMetrics
 *   - Fresh accumulator is created for the next day (dayNumber + 1)
 *   - showEndOfDayReport is set to true
 *   - lastEndOfDayReport contains the snapshot
 *   - dayOfWeek is derived as dayNumber % 7
 *   - Auto-pauses time when the player was not already paused (pausedByEndOfDay = true)
 *   - Preserves the manual pause and sets pausedByEndOfDay = false when player was paused
 *   - Appends to an existing list of completed days
 *   - Snapshot revenue matches the accumulator revenue
 *
 *  [dismissEndOfDayReport]
 *   - showEndOfDayReport is cleared
 *   - pausedByEndOfDay is cleared
 *   - Resumes time when the engine auto-paused it (pausedByEndOfDay was true)
 *   - Preserves the player's manual pause when pausedByEndOfDay was false
 *
 *  [advanceDay] / [lastKnownDayNumber]
 *   - Starts at 0
 *   - advanceDay updates the tracked day number
 *   - Multiple advanceDay calls advance correctly
 */
class DayManagerTest {

    private lateinit var dayManager: DayManager

    @Before
    fun setUp() {
        dayManager = DayManager()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /** Returns a [GameState] with only the progression-relevant fields set. */
    private fun stateWith(
        playerPausedTime: Boolean = false,
        pausedByEndOfDay: Boolean = false,
        showEndOfDayReport: Boolean = false,
        revenue: Money = Money.ZERO,
        completedDays: Int = 0,
        dayNumber: Int = 0,
    ): GameState = GameState(
        playerPausedTime = playerPausedTime,
        pausedByEndOfDay = pausedByEndOfDay,
        showEndOfDayReport = showEndOfDayReport,
        currentDayMetrics = DailyMetricsAccumulator(
            dayNumber = dayNumber,
            revenue = revenue,
        ),
        completedDayMetrics = List(completedDays) {
            DailyMetrics(dayNumber = it, dayOfWeek = it % 7)
        },
    )

    // ── rollOverDay — snapshot ────────────────────────────────────────────────

    @Test
    fun `rollOverDay appends a snapshot to completedDayMetrics`() {
        val state = stateWith(completedDays = 0)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertEquals(1, result.completedDayMetrics.size)
    }

    @Test
    fun `rollOverDay appends to an already populated completedDayMetrics list`() {
        val state = stateWith(completedDays = 3)
        val result = dayManager.rollOverDay(state, dayNumber = 3)
        assertEquals(4, result.completedDayMetrics.size)
    }

    @Test
    fun `rollOverDay snapshot dayOfWeek is dayNumber mod 7`() {
        // dayNumber = 8 → dayOfWeek = 8 % 7 = 1 (Tuesday)
        val state = stateWith()
        val result = dayManager.rollOverDay(state, dayNumber = 8)
        assertEquals(1, result.completedDayMetrics.last().dayOfWeek)
    }

    @Test
    fun `rollOverDay snapshot dayOfWeek wraps around correctly at 7`() {
        // dayNumber = 7 → dayOfWeek = 0 (Monday)
        val state = stateWith()
        val result = dayManager.rollOverDay(state, dayNumber = 7)
        assertEquals(0, result.completedDayMetrics.last().dayOfWeek)
    }

    @Test
    fun `rollOverDay snapshot revenue matches the live accumulator revenue`() {
        val revenue = Money(123_456L)
        val state = stateWith(revenue = revenue)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertEquals(revenue, result.completedDayMetrics.last().revenue)
    }

    @Test
    fun `rollOverDay sets lastEndOfDayReport to the new snapshot`() {
        val state = stateWith(revenue = Money(500L))
        val result = dayManager.rollOverDay(state, dayNumber = 2)
        assertNotNull(result.lastEndOfDayReport)
        assertEquals(Money(500L), result.lastEndOfDayReport!!.revenue)
    }

    @Test
    fun `rollOverDay resets currentDayMetrics to a fresh accumulator`() {
        val state = stateWith(revenue = Money(999L), dayNumber = 5)
        val result = dayManager.rollOverDay(state, dayNumber = 5)
        // New accumulator should have zero revenue and dayNumber incremented by 1
        assertEquals(Money.ZERO, result.currentDayMetrics.revenue)
        assertEquals(6, result.currentDayMetrics.dayNumber)
    }

    @Test
    fun `rollOverDay sets showEndOfDayReport to true`() {
        val state = stateWith(showEndOfDayReport = false)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertTrue(result.showEndOfDayReport)
    }

    // ── rollOverDay — pause logic ─────────────────────────────────────────────

    @Test
    fun `rollOverDay auto-pauses time when player was not already paused`() {
        val state = stateWith(playerPausedTime = false)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertTrue("Time should be paused for the end-of-day report", result.playerPausedTime)
    }

    @Test
    fun `rollOverDay sets pausedByEndOfDay true when auto-pausing`() {
        val state = stateWith(playerPausedTime = false)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertTrue(result.pausedByEndOfDay)
    }

    @Test
    fun `rollOverDay preserves existing player pause and sets pausedByEndOfDay false`() {
        // Player had already manually paused — the engine must not claim ownership of the pause
        val state = stateWith(playerPausedTime = true)
        val result = dayManager.rollOverDay(state, dayNumber = 0)
        assertTrue("Player's manual pause must be preserved", result.playerPausedTime)
        assertFalse(
            "pausedByEndOfDay must be false when the player was already paused",
            result.pausedByEndOfDay,
        )
    }

    // ── dismissEndOfDayReport ─────────────────────────────────────────────────

    @Test
    fun `dismissEndOfDayReport clears showEndOfDayReport`() {
        val state = stateWith(showEndOfDayReport = true)
        val result = dayManager.dismissEndOfDayReport(state)
        assertFalse(result.showEndOfDayReport)
    }

    @Test
    fun `dismissEndOfDayReport clears pausedByEndOfDay flag`() {
        val state = GameState(showEndOfDayReport = true, pausedByEndOfDay = true)
        val result = dayManager.dismissEndOfDayReport(state)
        assertFalse(result.pausedByEndOfDay)
    }

    @Test
    fun `dismissEndOfDayReport resumes time when engine auto-paused it`() {
        // Engine auto-paused: pausedByEndOfDay = true, playerPausedTime = true
        val state = GameState(
            showEndOfDayReport = true,
            playerPausedTime = true,
            pausedByEndOfDay = true,
        )
        val result = dayManager.dismissEndOfDayReport(state)
        assertFalse("Time should resume after dismissing an engine-triggered pause", result.playerPausedTime)
    }

    @Test
    fun `dismissEndOfDayReport preserves manual pause when player paused independently`() {
        // Player paused manually before the rollover; pausedByEndOfDay is therefore false
        val state = GameState(
            showEndOfDayReport = true,
            playerPausedTime = true,
            pausedByEndOfDay = false,
        )
        val result = dayManager.dismissEndOfDayReport(state)
        assertTrue("Player's manual pause must survive report dismissal", result.playerPausedTime)
    }

    // ── lastKnownDayNumber / advanceDay ───────────────────────────────────────

    @Test
    fun `lastKnownDayNumber starts at zero`() {
        assertEquals(0, dayManager.lastKnownDayNumber)
    }

    @Test
    fun `advanceDay updates lastKnownDayNumber`() {
        dayManager.advanceDay(3)
        assertEquals(3, dayManager.lastKnownDayNumber)
    }

    @Test
    fun `advanceDay can be called multiple times in sequence`() {
        dayManager.advanceDay(1)
        dayManager.advanceDay(2)
        dayManager.advanceDay(5)
        assertEquals(5, dayManager.lastKnownDayNumber)
    }
}

