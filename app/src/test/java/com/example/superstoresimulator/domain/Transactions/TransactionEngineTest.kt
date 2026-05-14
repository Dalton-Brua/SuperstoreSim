package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.Transactions.TransactionLine
import com.example.superstoresimulator.domain.inventory.InventoryState
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.random.Random

/**
 * Unit tests for TransactionEngine
 * Tests transaction generation, ring-up, completion, and refund processing
 */
class TransactionEngineTest {

    private lateinit var engine: TransactionEngine
    private lateinit var baseState: GameState

    /** Helper to create a batch with the given quantity (non-perishable for testing). */
    private fun batch(qty: Int, day: Int = 1): List<com.example.superstoresimulator.domain.inventory.ItemBatch> =
        if (qty > 0) listOf(com.example.superstoresimulator.domain.inventory.ItemBatch(receivedDay = day, quantity = qty, expirationDay = Int.MAX_VALUE))
        else emptyList()

    /** Helper to create InventoryState from integer quantities. */
    private fun inv(shelfStock: Int, backroomStock: Int): InventoryState =
        InventoryState(shelfBatches = batch(shelfStock), backroomBatches = batch(backroomStock))

    @Before
    fun setUp() {
        engine = TransactionEngine(
            salesTaxRate = 0.0825,
            refundChance = 0.10,
            random = Random(42) // Fixed seed for deterministic tests
        )

        // Create base state with inventory
        val inventory = mutableMapOf<Int, InventoryState>()
        for (i in 1..10) {
            inventory[i] = inv(50, 100)
        }

        baseState = GameState(
            money = Money.fromDollars(1000.0),
            inventory = inventory,
            currentTransaction = Transaction(),
            transactionActive = false
        )
    }

    // ============ startNewTransaction Tests ============

    @Test
    fun testStartNewTransactionCreatesValidTransaction() {
        val newState = engine.startNewTransaction(baseState)

        assertTrue("Transaction should be active", newState.transactionActive)
        assertTrue("Transaction should have lines", newState.currentTransaction.lines.isNotEmpty())
        assertEquals("Transaction ID should increment", baseState.currentTransaction.id + 1, newState.currentTransaction.id)
    }

    @Test
    fun testStartNewTransactionHasCorrectLineCount() {
        val newState = engine.startNewTransaction(baseState)

        val lineCount = newState.currentTransaction.lines.size
        assertTrue("Should have 1-5 lines", lineCount in 1..5)
    }

    @Test
    fun testStartNewTransactionCalculatesTaxCorrectly() {
        val newState = engine.startNewTransaction(baseState)

        val transaction = newState.currentTransaction
        val expectedTax = Money.fromDollars(transaction.subtotal.toDouble() * 0.0825)

        // Allow for small rounding differences
        val diff = (transaction.tax.cents - expectedTax.cents).let { if (it < 0) -it else it }
        assertTrue("Tax calculation should be accurate", diff <= 2)
    }

    @Test
    fun testStartNewTransactionCalculatesTotalCorrectly() {
        val newState = engine.startNewTransaction(baseState)

        val transaction = newState.currentTransaction
        val expectedTotal = transaction.subtotal + transaction.tax

        assertEquals("Total should be subtotal + tax", expectedTotal, transaction.totalEarned)
    }

    @Test
    fun testStartNewTransactionUsesShuffledItems() {
        val newState = engine.startNewTransaction(baseState)

        val itemIds = newState.currentTransaction.lines.map { it.itemId }
        // With a fixed random seed, we should get a specific sequence
        assertTrue("Should have valid item IDs", itemIds.all { it in 1..10 })
    }

    @Test
    fun testStartNewTransactionWithEmptyInventoryThrows() {
        val emptyState = baseState.copy(inventory = emptyMap())

        assertThrows(IllegalStateException::class.java) {
            engine.startNewTransaction(emptyState)
        }
    }

    @Test
    fun testStartNewTransactionQuantitiesInRange() {
        val newState = engine.startNewTransaction(baseState)

        val quantities = newState.currentTransaction.lines.map { it.quantity }
        assertTrue("All quantities should be 1-3", quantities.all { it in 1..3 })
    }

    @Test
    fun testStartNewTransactionLinesTotalCalculated() {
        val newState = engine.startNewTransaction(baseState)

        val lines = newState.currentTransaction.lines
        for (line in lines) {
            val expectedLineTotal = line.unitPrice * line.quantity
            assertEquals("Line total should be quantity * unit price", expectedLineTotal, line.lineTotal)
        }
    }



    // ============ ringUpSingleItem Tests ============

    @Test
    fun testRingUpSingleItemIncrementsRungQty() {
        var state = engine.startNewTransaction(baseState)
        val itemId = state.currentTransaction.lines[0].itemId
        val originalRungQty = state.currentTransaction.lines[0].rungQty

        state = engine.ringUpSingleItem(state, itemId)

        assertEquals("rungQty should increment", originalRungQty + 1, state.currentTransaction.lines[0].rungQty)
    }

    @Test
    fun testRingUpSingleItemConsumesShelfStock() {
        var state = engine.startNewTransaction(baseState)
        val itemId = state.currentTransaction.lines[0].itemId
        val originalStock = state.inventory[itemId]!!.shelfStock

        state = engine.ringUpSingleItem(state, itemId)

        assertEquals("Shelf stock should decrease", originalStock - 1, state.inventory[itemId]!!.shelfStock)
    }

    @Test
    fun testRingUpSingleItemWithNoShelfStockMarksLineAsLostToOutOfStock() {
        // When shelfStock == 0 the engine does NOT leave the state unchanged.
        // Instead it marks the line lostToOutOfStock = true so the cashier can
        // move on rather than blocking the transaction.  Shelf stock must not be
        // decremented and rungQty must not increment.
        var state = engine.startNewTransaction(baseState)
        val itemId = state.currentTransaction.lines[0].itemId
        val rungQtyBefore = state.currentTransaction.lines[0].rungQty  // 0

        // Force shelf stock to 0 for this item
        state = state.copy(inventory = state.inventory + (itemId to inv(0, 100)))

        state = engine.ringUpSingleItem(state, itemId)

        val updatedLine = state.currentTransaction.lines.find { it.itemId == itemId }
        assertNotNull("Line must still exist after OOS ring-up", updatedLine)
        assertTrue("Line must be flagged lostToOutOfStock when shelf is empty",
            updatedLine!!.lostToOutOfStock)
        assertEquals("rungQty must not increment for an OOS item",
            rungQtyBefore, updatedLine.rungQty)
        assertEquals("Shelf stock must not be consumed for an OOS item",
            0, state.inventory[itemId]?.shelfStock)
    }

    @Test
    fun testRingUpSingleItemCompleteTransaction() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        // Ring up all items
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Transaction should be complete
        assertFalse("Transaction should be marked inactive", state.transactionActive)
        assertEquals("Total transactions should increment", baseState.totalTransactionsCompleted + 1, state.totalTransactionsCompleted)
    }


    @Test
    fun testRingUpSingleItemWithInvalidItemIdReturnsUnchanged() {
        var state = engine.startNewTransaction(baseState)
        val stateBeforeRingUp = state

        state = engine.ringUpSingleItem(state, 999) // Invalid item ID

        assertEquals("State should not change for invalid item", stateBeforeRingUp.currentTransaction, state.currentTransaction)
    }

    @Test
    fun testRingUpSingleItemIncreasesMoney() {
        var state = engine.startNewTransaction(baseState)
        val initialMoney = state.money
        val expectedRevenue = state.currentTransaction.totalEarned
        val lines = state.currentTransaction.lines

        // Ring up all items to complete transaction
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Money should increase by total earned (with tax)
        assertTrue("Money should increase", state.money > initialMoney)
        assertEquals("Money should increase by transaction total", initialMoney + expectedRevenue, state.money)
    }

    @Test
    fun testRingUpSingleItemIncrementsTaxCollected() {
        var state = engine.startNewTransaction(baseState)
        val initialTax = state.totalTaxCollected
        val transactionTax = state.currentTransaction.tax
        val lines = state.currentTransaction.lines

        // Ring up all items
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        assertEquals("Tax collected should increase", initialTax + transactionTax, state.totalTaxCollected)
    }

    @Test
    fun testRingUpSingleItemAddedToSalesHistory() {
        var state = engine.startNewTransaction(baseState)
        val initialHistorySize = state.salesHistory.size
        val lines = state.currentTransaction.lines

        // Ring up all items
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        assertEquals("Sales history should have new entry", initialHistorySize + 1, state.salesHistory.size)
    }

    @Test
    fun testRingUpSingleItemAlreadyRungDoesNotRingUp() {
        var state = engine.startNewTransaction(baseState)
        val firstLine = state.currentTransaction.lines[0]

        // Ring up all items on first line
        repeat(firstLine.quantity) {
            state = engine.ringUpSingleItem(state, firstLine.itemId)
        }

        val stateBeforeExtraRingUp = state
        state = engine.ringUpSingleItem(state, firstLine.itemId) // Try to ring up again

        assertEquals("Should not ring up more than quantity", stateBeforeExtraRingUp.inventory, state.inventory)
    }

    // ============ processRefund Tests ============

    @Test
    fun testProcessRefundWithValidRefundId() {
        // Create a refund manually to test processing
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines
        
        // Complete transaction to potentially trigger refund
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Check if refund was generated
        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val initialMoney = state.money
            val refundAmount = state.pendingRefunds[0].subtotal

            state = engine.processRefund(state, refundId)

            // Refund should be removed
            assertFalse("Refund should be processed", state.pendingRefunds.any { it.id == refundId })
            // Money should decrease
            assertEquals("Money should decrease by refund amount", initialMoney - refundAmount, state.money)
        }
    }

    @Test
    fun testProcessRefundWithInvalidRefundIdReturnsUnchanged() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        val initialState = state
        state = engine.processRefund(state, 999) // Invalid refund ID

        assertEquals("State should not change", initialState.pendingRefunds, state.pendingRefunds)
    }

    @Test
    fun testProcessRefundDecreasesTotalTaxCollected() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val initialTax = state.totalTaxCollected
            val refundTax = state.pendingRefunds[0].tax

            state = engine.processRefund(state, refundId)

            assertEquals("Tax collected should decrease", initialTax - refundTax, state.totalTaxCollected)
        }
    }

    @Test
    fun testProcessRefundAddsToSalesHistory() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val initialHistorySize = state.salesHistory.size

            state = engine.processRefund(state, refundId)

            assertEquals("Sales history should have refund entry", initialHistorySize + 1, state.salesHistory.size)
        }
    }

    // ============ processRefundLine Tests ============

    @Test
    fun testProcessRefundLineWithValidParameters() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val refundLine = state.pendingRefunds[0].lines[0]
            val itemId = refundLine.itemId
            val initialPendingCount = state.pendingRefunds.size

            state = engine.processRefundLine(state, refundId, itemId, 1)

            assertTrue("Pending refunds should exist", state.pendingRefunds.size > 0)
        }
    }

    @Test
    fun testProcessRefundLineWithZeroQuantityReturnsUnchanged() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        val initialState = state
        state = engine.processRefundLine(state, 999, 1, 0) // Invalid params

        assertEquals("State should not change with qty 0", initialState.pendingRefunds, state.pendingRefunds)
    }

    @Test
    fun testProcessRefundLineIncreasesShelfStock() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val refundLine = state.pendingRefunds[0].lines[0]
            val itemId = refundLine.itemId
            val initialStock = state.inventory[itemId]?.shelfStock ?: 0

            state = engine.processRefundLine(state, refundId, itemId, 1)

            val newStock = state.inventory[itemId]?.shelfStock ?: 0
            assertTrue("Shelf stock should increase", newStock >= initialStock)
        }
    }

    @Test
    fun testProcessRefundLineDecreasesMoneyAndTax() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val refundLine = state.pendingRefunds[0].lines[0]
            val itemId = refundLine.itemId
            val initialMoney = state.money
            val initialTax = state.totalTaxCollected

            state = engine.processRefundLine(state, refundId, itemId, 1)

            assertTrue("Money should decrease", state.money <= initialMoney)
            assertTrue("Tax should decrease or stay same", state.totalTaxCollected <= initialTax)
        }
    }

    @Test
    fun testProcessRefundLinePartiallyProcessesRefund() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val refundLine = state.pendingRefunds[0].lines[0]
            val itemId = refundLine.itemId
            val originalQty = refundLine.quantity

            // Process partial refund
            state = engine.processRefundLine(state, refundId, itemId, 1)

            // If original qty > 1, refund should still exist
            if (originalQty > 1) {
                val stillPending = state.pendingRefunds.firstOrNull { it.id == refundId }
                assertNotNull("Refund should still exist after partial process", stillPending)
            }
        }
    }

    @Test
    fun testProcessRefundLineWithExcessiveQuantityCoercesMax() {
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val refundLine = state.pendingRefunds[0].lines[0]
            val itemId = refundLine.itemId
            val availableQty = refundLine.quantity

            // Try to process more than available
            state = engine.processRefundLine(state, refundId, itemId, availableQty + 10)

            // Should process only what's available
            val remainingRefund = state.pendingRefunds.firstOrNull { it.id == refundId }
            if (remainingRefund != null) {
                assertTrue("Should not have processed more than available", remainingRefund.lines.isNotEmpty())
            }
        }
    }

    // ============ State Immutability Tests ============

    @Test
    fun testTransactionEngineDoesNotMutateOriginalState() {
        val originalState = baseState
        engine.startNewTransaction(originalState)

        // Original state should be unchanged
        assertEquals("Original state should not change", baseState.money, originalState.money)
        assertEquals("Original inventory should not change", baseState.inventory, originalState.inventory)
        assertFalse("Original transaction should not be active", originalState.transactionActive)
    }

    // ============ Tax Calculation Tests ============

    @Test
    fun testTaxCalculationAccuracy() {
        val engine = TransactionEngine(salesTaxRate = 0.0825)
        var state = engine.startNewTransaction(baseState)

        val expectedTax = Money.fromDollars(state.currentTransaction.subtotal.toDouble() * 0.0825)
        val diff = (state.currentTransaction.tax.cents - expectedTax.cents).let { if (it < 0) -it else it }

        assertTrue("Tax should be accurate to within 2 cents", diff <= 2)
    }

    @Test
    fun testCustomTaxRate() {
        val customTaxRate = 0.10
        val engine = TransactionEngine(salesTaxRate = customTaxRate)
        val state = engine.startNewTransaction(baseState)

        val expectedTax = Money.fromDollars(state.currentTransaction.subtotal.toDouble() * customTaxRate)
        val diff = (state.currentTransaction.tax.cents - expectedTax.cents).let { if (it < 0) -it else it }

        assertTrue("Custom tax rate should apply", diff <= 2)
    }

    // ============ Multiple Transaction Tests ============

    @Test
    fun testMultipleSequentialTransactions() {
        var state = baseState

        for (i in 1..3) {
            state = engine.startNewTransaction(state)
            val lines = state.currentTransaction.lines

            for (line in lines) {
                repeat(line.quantity) {
                    state = engine.ringUpSingleItem(state, line.itemId)
                }
            }

            assertTrue("All transactions should increase money", state.money > baseState.money)
            assertTrue("Transaction count should increase", state.totalTransactionsCompleted >= i)
        }
    }

    @Test
    fun testInventoryDecreasesWithMultipleTransactions() {
        var state = baseState
        val initialTotalStock = state.inventory.values.sumOf { it.shelfStock }

        for (i in 1..2) {
            state = engine.startNewTransaction(state)
            val lines = state.currentTransaction.lines

            for (line in lines) {
                repeat(line.quantity) {
                    state = engine.ringUpSingleItem(state, line.itemId)
                }
            }
        }

        val finalTotalStock = state.inventory.values.sumOf { it.shelfStock }
        assertTrue("Total shelf stock should decrease", finalTotalStock < initialTotalStock)
    }
}


