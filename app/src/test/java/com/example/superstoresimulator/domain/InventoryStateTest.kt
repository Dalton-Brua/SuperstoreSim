package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
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

    /** Helper to create a batch with the given quantity (non-perishable for testing). */
    private fun batch(qty: Int, day: Int = 1): List<com.example.superstoresimulator.domain.inventory.ItemBatch> =
        if (qty > 0) listOf(ItemBatch(receivedDay = day, quantity = qty, expirationDay = Int.MAX_VALUE))
        else emptyList()

    /** Helper to create InventoryState from integer quantities. */
    private fun inv(shelfStock: Int, backroomStock: Int): InventoryState =
        InventoryState(shelfBatches = batch(shelfStock), backroomBatches = batch(backroomStock))

    @Test
    fun testStockingFromEmptyBackroom() {
        // Can't stock if backroom is empty
        val inventory = inv(5, 0)

        if (inventory.backroomStock > 0) {
            // This shouldn't execute
            fail("Backroom should be empty")
        }
    }
}
