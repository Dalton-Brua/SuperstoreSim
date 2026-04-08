package com.example.superstoresimulator.domain.time

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Unit tests for StoreConfig
 * Tests store opening/closing logic and game speed configuration
 */
class StoreConfigTest {

    private lateinit var config: StoreConfig

    @Before
    fun setUp() {
        config = StoreConfig()
    }


    @Test
    fun testIsOpen() {
        // Before opening
        assertFalse(config.isOpen(GameTime(0)))
        assertFalse(config.isOpen(GameTime(359)))
        
        // At opening
        assertTrue(config.isOpen(GameTime(360)))
        
        // During operating hours
        assertTrue(config.isOpen(GameTime(720)))
        assertTrue(config.isOpen(GameTime(1200)))
        
        // Right before closing
        assertTrue(config.isOpen(GameTime(1259)))
        
        // At closing and after
        assertFalse(config.isOpen(GameTime(1260)))
        assertFalse(config.isOpen(GameTime(1439)))
    }

    @Test
    fun testIsClosing() {
        // Closing window is 1260-1290 (9:00 PM - 9:30 PM)
        
        // Before closing procedures
        assertFalse(config.isClosing(GameTime(1259)))
        
        // During closing procedures
        assertTrue(config.isClosing(GameTime(1260)))
        assertTrue(config.isClosing(GameTime(1275)))
        assertTrue(config.isClosing(GameTime(1289)))
        
        // After closing procedures
        assertFalse(config.isClosing(GameTime(1290)))
        assertFalse(config.isClosing(GameTime(1439)))
    }

    @Test
    fun testIsClosed() {
        // Before opening
        assertTrue(config.isClosed(GameTime(0)))
        assertTrue(config.isClosed(GameTime(359)))
        
        // During operating hours
        assertFalse(config.isClosed(GameTime(360)))
        assertFalse(config.isClosed(GameTime(1200)))
        assertFalse(config.isClosed(GameTime(1259)))
        
        // During closing procedures
        assertFalse(config.isClosed(GameTime(1260)))
        assertFalse(config.isClosed(GameTime(1275)))
        
        // After closing procedures
        assertTrue(config.isClosed(GameTime(1290)))
        assertTrue(config.isClosed(GameTime(1439)))
    }

    @Test
    fun testMutualExclusivityOfStates() {
        // At any given time, exactly one state should be true
        for (minute in 0 until 1440) {
            val time = GameTime(minute.toLong())
            
            val open = config.isOpen(time)
            val closing = config.isClosing(time)
            val closed = config.isClosed(time)
            
            val activeCount = listOf(open, closing, closed).count { it }
            assertEquals("State should be unique at minute $minute", 1, activeCount)
        }
    }

    @Test
    fun testClosingTransitionFlow() {
        // Flow: OPEN -> CLOSING -> CLOSED
        
        // 8:59 PM - Still open
        assertTrue(config.isOpen(GameTime(1259)))
        
        // 9:00 PM - Start closing procedures
        assertFalse(config.isOpen(GameTime(1260)))
        assertTrue(config.isClosing(GameTime(1260)))
        assertFalse(config.isClosed(GameTime(1260)))
        
        // 9:15 PM - Still closing
        assertTrue(config.isClosing(GameTime(1275)))
        
        // 9:30 PM - Fully closed
        assertFalse(config.isClosing(GameTime(1290)))
        assertTrue(config.isClosed(GameTime(1290)))
    }

    @Test
    fun testCustomStoreHours() {
        // Test custom store hours (8 AM - 10 PM)
        val customConfig = StoreConfig(
            openTimeMinutes = 480,    // 8:00 AM
            closeTimeMinutes = 1320,  // 10:00 PM
            closingProcedureDuration = 20
        )
        
        // Before custom opening
        assertFalse(customConfig.isOpen(GameTime(479)))
        assertTrue(customConfig.isOpen(GameTime(480)))
        
        // At custom closing
        assertTrue(customConfig.isOpen(GameTime(1319)))
        assertFalse(customConfig.isOpen(GameTime(1320)))
    }


    @Test
    fun testClosingProcedureDuration() {
        val shortClosing = StoreConfig(
            closingProcedureDuration = 10
        )
        
        val longClosing = StoreConfig(
            closingProcedureDuration = 60
        )
        
        // At 9:00 PM (1260)
        assertTrue(shortClosing.isClosing(GameTime(1260)))
        assertTrue(longClosing.isClosing(GameTime(1260)))
        
        // At 9:15 PM (1275) = 15 minutes after close
        // shortClosing duration is 10, so at 15 min it's closed
        // longClosing duration is 60, so at 15 min it's still closing
        assertTrue(shortClosing.isClosed(GameTime(1275)))
        assertTrue(longClosing.isClosing(GameTime(1275)))
        
        // At 10:00 PM (1320) = 60 minutes after close
        assertTrue(shortClosing.isClosed(GameTime(1320)))
        // longClosing should also be closed now (60 min duration)
        assertTrue(longClosing.isClosed(GameTime(1320)))
    }
}

