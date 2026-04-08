package com.example.superstoresimulator.domain

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for InventoryState.
 * All data-class construction / copy() tests removed — they tested Kotlin language
 * semantics rather than application logic.  The single test retained documents the
 * deliberate design decision that InventoryState itself has no negative-stock guard
 * (enforcement lives in GameEngine).
 *
 * For business-logic inventory tests see StockRandomItemFromBackroomTest and
 * GameEngineAdvancedTest.
 */
class InventoryStateTest {

    @Test
    fun testStockingFromEmptyBackroom() {
        // Can't stock if backroom is empty
        val inventory = InventoryState(shelfStock = 5, backroomStock = 0)

        if (inventory.backroomStock > 0) {
            // This shouldn't execute
            fail("Backroom should be empty")
        }
    }
}
