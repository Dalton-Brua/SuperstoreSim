package com.example.superstoresimulator.domain.time

import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreState
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Unit tests for TimeManager
 * Tests time progression, store state transitions, and game speed
 */
class TimeManagerTest {

    private lateinit var timeManager: TimeManager

    @Before
    fun setUp() {
        timeManager = TimeManager()
    }


    @Test
    fun testResetTime() {
        // Advance time
        timeManager.update(5000)
        assertTrue(timeManager.currentTime.totalMinutesElapsed > 0)
        
        // Reset
        timeManager.reset()
        assertEquals(0L, timeManager.currentTime.totalMinutesElapsed)
        assertEquals(StoreState.CLOSED, timeManager.getStoreState())
    }

    @Test
    fun testJumpToTime() {
        // Jump to 6 AM (360 minutes)
        val result = timeManager.jumpToTime(6, 0)
        assertEquals(360, result.totalMinutesElapsed)
        assertEquals(6, result.hour)
        assertEquals(0, result.minute)
    }

    @Test
    fun testJumpToTimeWithMinutes() {
        // Jump to 9:30 AM
        val result = timeManager.jumpToTime(9, 30)
        assertEquals(570, result.totalMinutesElapsed) // 9*60 + 30
        assertEquals(9, result.hour)
        assertEquals(30, result.minute)
    }

    @Test
    fun testJumpToTimePastCurrentTime() {
        // Start at 6 AM
        timeManager.jumpToTime(6, 0)
        assertEquals(360, timeManager.currentTime.totalMinutesElapsed)
        
        // Try to jump to 5 AM (earlier) - should jump to next day
        timeManager.jumpToTime(5, 0)
        
        // Should be at 5 AM next day = 360 (today) + (19 hours) = 360 + 1140 - 60 + 300 = 1740
        // Actually: 24*60 - 60 + 300 = 1440 - 60 + 300 = 1680
        // Calculation: remaining time in day (24-6)*60 + target = 18*60 + 300 = 1380
        val expected = 24 * 60 + 5 * 60 // 1740 = next day at 5 AM
        assertEquals(expected, timeManager.currentTime.totalMinutesElapsed.toInt())
    }

    @Test
    fun testStoreStateClosed() {
        // At midnight (0:00)
        val time = GameTime(0)
        val storeState = timeManager.config.let { config ->
            when {
                config.isClosed(time) -> StoreState.CLOSED
                config.isClosing(time) -> StoreState.CLOSING
                config.isOpen(time) -> StoreState.OPEN
                else -> StoreState.CLOSED
            }
        }
        assertEquals(StoreState.CLOSED, storeState)
    }

    @Test
    fun testStoreStateOpen() {
        // At 6 AM (360 minutes)
        var time = GameTime(360)
        var storeState = timeManager.config.let { config ->
            when {
                config.isClosed(time) -> StoreState.CLOSED
                config.isClosing(time) -> StoreState.CLOSING
                config.isOpen(time) -> StoreState.OPEN
                else -> StoreState.CLOSED
            }
        }
        assertEquals(StoreState.OPEN, storeState)
        
        // At noon
        time = GameTime(720)
        storeState = timeManager.config.let { config ->
            when {
                config.isClosed(time) -> StoreState.CLOSED
                config.isClosing(time) -> StoreState.CLOSING
                config.isOpen(time) -> StoreState.OPEN
                else -> StoreState.CLOSED
            }
        }
        assertEquals(StoreState.OPEN, storeState)
        
        // At 8:59 PM (1259 minutes)
        time = GameTime(1259)
        storeState = timeManager.config.let { config ->
            when {
                config.isClosed(time) -> StoreState.CLOSED
                config.isClosing(time) -> StoreState.CLOSING
                config.isOpen(time) -> StoreState.OPEN
                else -> StoreState.CLOSED
            }
        }
        assertEquals(StoreState.OPEN, storeState)
    }

    @Test
    fun testStoreStateClosing() {
        // At 9:00 PM (1260 minutes) - start of closing procedures
        var time = GameTime(1260)
        var storeState = timeManager.config.let { config ->
            when {
                config.isClosed(time) -> StoreState.CLOSED
                config.isClosing(time) -> StoreState.CLOSING
                config.isOpen(time) -> StoreState.OPEN
                else -> StoreState.CLOSED
            }
        }
        assertEquals(StoreState.CLOSING, storeState)
        
        // During closing (9:15 PM)
        time = GameTime(1275)
        storeState = timeManager.config.let { config ->
            when {
                config.isClosed(time) -> StoreState.CLOSED
                config.isClosing(time) -> StoreState.CLOSING
                config.isOpen(time) -> StoreState.OPEN
                else -> StoreState.CLOSED
            }
        }
        assertEquals(StoreState.CLOSING, storeState)
    }

    @Test
    fun testStoreStateTransition() {
        // Progress from closed to open to closing to closed
        val testTimes = listOf(
            0L to StoreState.CLOSED,      // Midnight
            360L to StoreState.OPEN,      // 6 AM
            720L to StoreState.OPEN,      // Noon
            1260L to StoreState.CLOSING,  // 9 PM
            1290L to StoreState.CLOSED    // 9:30 PM
        )
        
        for ((minutes, expectedState) in testTimes) {
            val time = GameTime(minutes)
            val state = timeManager.config.let { config ->
                when {
                    config.isClosed(time) -> StoreState.CLOSED
                    config.isClosing(time) -> StoreState.CLOSING
                    config.isOpen(time) -> StoreState.OPEN
                    else -> StoreState.CLOSED
                }
            }
            assertEquals("Failed at $minutes minutes", expectedState, state)
        }
    }

    @Test
    fun testAccumulatedTimeRounding() {
        // Test that fractional time is accumulated properly
        timeManager = TimeManager()
        
        // Many small updates should accumulate correctly
        repeat(100) {
            timeManager.update(16L) // Typical 16ms frame
        }
        
        // 100 frames * 16ms = 1600ms = 1.6 seconds
        // At base speed 120: 1.6 * 120 * 1.0 = 192 game minutes
        val expectedMinutes = (1600 * 120.0 / 60000).toLong()
        
        // Allow small rounding error
        val diff = (timeManager.currentTime.totalMinutesElapsed - expectedMinutes).let {
            if (it < 0) -it else it
        }
        assertTrue("Expected around $expectedMinutes, got ${timeManager.currentTime.totalMinutesElapsed}",
            diff <= 1)
    }

    @Test
    fun testConfigCanBeChanged() {
        val newConfig = StoreConfig(
            openTimeMinutes = 480,    // 8 AM
            closeTimeMinutes = 1320   // 10 PM
        )
        
        timeManager.config = newConfig
        
        // Test with new config
        var time = GameTime(479)
        var state = if (newConfig.isOpen(time)) StoreState.OPEN else StoreState.CLOSED
        assertEquals(StoreState.CLOSED, state)
        
        time = GameTime(480)
        state = if (newConfig.isOpen(time)) StoreState.OPEN else StoreState.CLOSED
        assertEquals(StoreState.OPEN, state)
    }

    @Test
    fun testSpeedMultiplierAffectsTimeProgression() {
        timeManager.setSpeedMultiplier(1.0f)
        val startTime = timeManager.currentTime.totalMinutesElapsed
        timeManager.update(1000L)
        val advanceAt1x = timeManager.currentTime.totalMinutesElapsed - startTime
        
        timeManager.reset()
        timeManager.setSpeedMultiplier(4.0f)
        timeManager.update(1000L)
        val advanceAt4x = timeManager.currentTime.totalMinutesElapsed
        
        // At 4x speed, should advance 4 times as much
        assertEquals(advanceAt1x * 4, advanceAt4x)
    }

    @Test
    fun testDayTransition() {
        // Start at 11 PM (1380 minutes)
        var time = GameTime(1380)
        assertEquals(0, time.dayNumber)
        
        // Jump past midnight (1440 minutes = 1 day)
        time = GameTime(1440)
        assertEquals(1, time.dayNumber)
    }

    @Test
    fun testLongGameSession() {
        // Test progression over multiple days
        var time = GameTime(0)
        
        for (day in 0..6) {
            assertEquals(day, time.dayNumber)
            time = time.addMinutes(1440)
        }
        
        // After 7 days
        assertEquals(1, time.weekNumber)
        assertEquals(0, time.dayOfWeek) // Monday again
    }

    @Test
    fun testTimerDoesNotRegress() {
        // Time should never go backwards
        val startTime = timeManager.currentTime.totalMinutesElapsed
        
        for (i in 0..100) {
            timeManager.update(16L)
            assertTrue("Time regressed!",
                timeManager.currentTime.totalMinutesElapsed >= startTime)
        }
    }
}


