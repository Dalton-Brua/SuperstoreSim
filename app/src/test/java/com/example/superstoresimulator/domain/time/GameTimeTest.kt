package com.example.superstoresimulator.domain.time

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for GameTime value class
 * Tests basic time representation, formatting, and day/week calculations
 */
class GameTimeTest {


    @Test
    fun testHourCalculation() {
        // 0 minutes = 0:00 AM
        assertEquals(0, GameTime(0).hour)
        
        // 60 minutes = 1:00 AM
        assertEquals(1, GameTime(60).hour)
        
        // 360 minutes = 6:00 AM
        assertEquals(6, GameTime(360).hour)
        
        // 1260 minutes = 21:00 (9:00 PM)
        assertEquals(21, GameTime(1260).hour)
        
        // Wrap around: 1500 minutes = 1:00 AM next day
        assertEquals(1, GameTime(1500).hour)
    }

    @Test
    fun testMinuteCalculation() {
        // 0 minutes = 0 min
        assertEquals(0, GameTime(0).minute)
        
        // 30 minutes = 30 min
        assertEquals(30, GameTime(30).minute)
        
        // 90 minutes = 1:30 AM = 30 min
        assertEquals(30, GameTime(90).minute)
        
        // 1265 minutes = 9:05 PM
        assertEquals(5, GameTime(1265).minute)
    }

    @Test
    fun testDayNumberCalculation() {
        // Day 0: 0-1439 minutes
        assertEquals(0, GameTime(0).dayNumber)
        assertEquals(0, GameTime(720).dayNumber)
        assertEquals(0, GameTime(1439).dayNumber)
        
        // Day 1: 1440+ minutes
        assertEquals(1, GameTime(1440).dayNumber)
        assertEquals(1, GameTime(2000).dayNumber)
        
        // Day 7: Week boundary
        assertEquals(7, GameTime(1440 * 7).dayNumber)
    }

    @Test
    fun testDayOfWeekCalculation() {
        // Day 0 = Monday (0)
        assertEquals(0, GameTime(0).dayOfWeek)
        
        // Day 1 = Tuesday (1)
        assertEquals(1, GameTime(1440).dayOfWeek)
        
        // Day 4 = Friday (4)
        assertEquals(4, GameTime(1440 * 4).dayOfWeek)
        
        // Day 6 = Sunday (6)
        assertEquals(6, GameTime(1440 * 6).dayOfWeek)
        
        // Day 7 = Monday again (wraps to 0)
        assertEquals(0, GameTime(1440 * 7).dayOfWeek)
    }

    @Test
    fun testWeekNumberCalculation() {
        // Week 0: Days 0-6
        assertEquals(0, GameTime(0).weekNumber)
        assertEquals(0, GameTime(1440 * 6).weekNumber)
        
        // Week 1: Days 7-13
        assertEquals(1, GameTime(1440 * 7).weekNumber)
        assertEquals(1, GameTime(1440 * 13).weekNumber)
        
        // Week 4
        assertEquals(4, GameTime(1440 * 28).weekNumber)
    }

    @Test
    fun testGetTotalMinutesOfDay() {
        // Start of day
        assertEquals(0, GameTime(0).getTotalMinutesOfDay())
        
        // 6 AM = 360 minutes into the day
        assertEquals(360, GameTime(360).getTotalMinutesOfDay())
        
        // End of day (11:59 PM)
        assertEquals(1439, GameTime(1439).getTotalMinutesOfDay())
        
        // Next day (wraps around)
        assertEquals(0, GameTime(1440).getTotalMinutesOfDay())
        assertEquals(300, GameTime(1440 + 300).getTotalMinutesOfDay())
    }

    @Test
    fun testIsOpen() {
        val storeOpenTime = 360   // 6:00 AM
        val storeCloseTime = 1260 // 9:00 PM
        
        // Before opening
        assertFalse(GameTime(0).isOpen(storeOpenTime, storeCloseTime))
        assertFalse(GameTime(359).isOpen(storeOpenTime, storeCloseTime))
        
        // During open hours
        assertTrue(GameTime(360).isOpen(storeOpenTime, storeCloseTime))
        assertTrue(GameTime(720).isOpen(storeOpenTime, storeCloseTime))
        assertTrue(GameTime(1259).isOpen(storeOpenTime, storeCloseTime))
        
        // After closing
        assertFalse(GameTime(1260).isOpen(storeOpenTime, storeCloseTime))
        assertFalse(GameTime(1439).isOpen(storeOpenTime, storeCloseTime))
    }

    @Test
    fun testAddMinutes() {
        val time = GameTime(0)
        
        // Add 60 minutes
        val oneHourLater = time.addMinutes(60)
        assertEquals(60, oneHourLater.totalMinutesElapsed)
        assertEquals(1, oneHourLater.hour)
        
        // Add multiple hours
        val sixHoursLater = time.addMinutes(360)
        assertEquals(360, sixHoursLater.totalMinutesElapsed)
        assertEquals(6, sixHoursLater.hour)
        
        // Add across day boundary
        val nextDay = time.addMinutes(1440)
        assertEquals(1440, nextDay.totalMinutesElapsed)
        assertEquals(1, nextDay.dayNumber)
        assertEquals(0, nextDay.hour)
    }

    @Test
    fun testAddSeconds() {
        val time = GameTime(0)
        
        // Add 60 seconds = 1 minute
        val oneLaterMinute = time.addSeconds(60)
        assertEquals(1, oneLaterMinute.totalMinutesElapsed)
        
        // Add 3600 seconds = 60 minutes = 1 hour
        val oneHourLater = time.addSeconds(3600)
        assertEquals(60, oneHourLater.totalMinutesElapsed)
    }

    @Test
    fun testGetFormattedTime() {
        // Monday 00:00
        assertEquals("Mon 00:00", GameTime(0).getFormattedTime())
        
        // Monday 06:00
        assertEquals("Mon 06:00", GameTime(360).getFormattedTime())
        
        // Tuesday 09:05
        assertEquals("Tue 09:05", GameTime(1440 + 545).getFormattedTime())
        
        // Friday 21:30
        assertEquals("Fri 21:30", GameTime(1440 * 4 + 1290).getFormattedTime())
    }

    @Test
    fun testGetDayOfWeekName() {
        assertEquals("Monday", GameTime(0).getDayOfWeekName())
        assertEquals("Tuesday", GameTime(1440).getDayOfWeekName())
        assertEquals("Wednesday", GameTime(1440 * 2).getDayOfWeekName())
        assertEquals("Thursday", GameTime(1440 * 3).getDayOfWeekName())
        assertEquals("Friday", GameTime(1440 * 4).getDayOfWeekName())
        assertEquals("Saturday", GameTime(1440 * 5).getDayOfWeekName())
        assertEquals("Sunday", GameTime(1440 * 6).getDayOfWeekName())
    }

    @Test
    fun testDateProgression() {
        // Test progression through multiple days
        var time = GameTime(0)
        
        for (day in 0..6) {
            assertEquals(day, time.dayNumber)
            assertEquals(day, time.dayOfWeek)
            time = time.addMinutes(1440)
        }
        
        // After 7 days, we should be at day 7, week 1, and dayOfWeek should wrap to 0 (Monday again)
        time = GameTime(1440 * 7)
        assertEquals(7, time.dayNumber)
        assertEquals(1, time.weekNumber)
        assertEquals(0, time.dayOfWeek) // Monday again
    }

    @Test
    fun testRealisticGameDayFlow() {
        // Simulate a store day from 6 AM to 9 PM
        val storeOpenTime = 360   // 6:00 AM
        val storeCloseTime = 1260 // 9:00 PM
        
        var time = GameTime((storeOpenTime - 60).toLong())
        assertFalse(time.isOpen(storeOpenTime, storeCloseTime)) // 5:00 AM - closed
        
        time = time.addMinutes(60)
        assertTrue(time.isOpen(storeOpenTime, storeCloseTime)) // 6:00 AM - open
        
        time = time.addMinutes(360) // Noon
        assertTrue(time.isOpen(storeOpenTime, storeCloseTime))
        assertEquals(12, time.hour)
        
        time = time.addMinutes(660) // 9:00 PM
        assertFalse(time.isOpen(storeOpenTime, storeCloseTime)) // closed
    }
}

