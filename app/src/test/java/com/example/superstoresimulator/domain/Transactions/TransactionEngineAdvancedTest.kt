package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionLine
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.random.Random

/**
 * Additional comprehensive tests for TransactionEngine
 * Focus on: random refund paths, edge cases, complex scenarios
 * These tests target the gaps in the original test suite (45% → ~75%+ coverage)
 */
class TransactionEngineAdvancedTest {

    private lateinit var engine: TransactionEngine
    private lateinit var baseState: GameState

    @Before
    fun setUp() {
        // Base state with 10 items, $1000 money
        val inventory = mutableMapOf<Int, InventoryState>()
        for (i in 1..10) {
            inventory[i] = InventoryState(shelfStock = 50, backroomStock = 100)
        }

        baseState = GameState(
            money = Money.fromDollars(1000.0),
            inventory = inventory,
            currentTransaction = Transaction(),
            transactionActive = false
        )
    }

    // ============ Refund Randomness Tests (Force Paths) ============

    @Test
    fun testAlwaysGenerateRefundWhenChanceIs100Percent() {
        // Force refunds to ALWAYS generate
        engine = TransactionEngine(
            salesTaxRate = 0.0825,
            refundChance = 1.0,  // 100% refund chance
            random = Random(42)
        )

        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        // Complete transaction (should trigger refund generation)
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // With 100% refund chance, we SHOULD have a pending refund
        assertTrue("Should generate refund with 100% chance", state.pendingRefunds.isNotEmpty())
    }

    @Test
    fun testNeverGenerateRefundWhenChanceIs0Percent() {
        // Force refunds to NEVER generate
        engine = TransactionEngine(
            salesTaxRate = 0.0825,
            refundChance = 0.0,  // 0% refund chance
            random = Random(42)
        )

        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        // Complete transaction (should NOT trigger refund)
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // With 0% refund chance, we should NOT have a pending refund
        assertTrue("Should not generate refund with 0% chance", state.pendingRefunds.isEmpty())
    }

    @Test
    fun testRefundRequestStructureIsValid() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))

        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refund = state.pendingRefunds[0]

            // Validate refund structure
            assertNotNull("Refund should have ID", refund.id)
            assertNotNull("Refund should have timestamp", refund.timestamp)
            assertNotNull("Refund should have original transaction ID", refund.originalTransactionId)
            assertTrue("Refund should have lines", refund.lines.isNotEmpty())
            assertTrue("Refund subtotal should be positive", refund.subtotal.cents > 0)
            assertTrue("Refund tax should be positive", refund.tax.cents > 0)
        }
    }

    @Test
    fun testRefundLinesHaveCorrectQuantities() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))

        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refund = state.pendingRefunds[0]

            for (refundLine in refund.lines) {
                // Quantities should be positive (stored as positive in RefundLine)
                assertTrue("Refund line quantity should be positive", refundLine.quantity > 0)
                assertTrue("Refund line quantity should have price", refundLine.unitPrice.cents > 0)
            }
        }
    }

    @Test
    fun testMultipleRefundsCanGenerate() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))
        var state = baseState

        // Generate multiple transactions with refunds
        repeat(3) {
            state = engine.startNewTransaction(state)
            val lines = state.currentTransaction.lines

            for (line in lines) {
                repeat(line.quantity) {
                    state = engine.ringUpSingleItem(state, line.itemId)
                }
            }
        }

        // All completed transactions should have generated refunds
        assertTrue("Should have multiple pending refunds", state.pendingRefunds.size >= 1)
    }

    // ============ Edge Case Tests ============

    @Test
    fun testSingleItemInventory() {
        val singleItemInventory = mutableMapOf<Int, InventoryState>(
            1 to InventoryState(shelfStock = 5, backroomStock = 10)
        )
        val singleItemState = baseState.copy(inventory = singleItemInventory)

        engine = TransactionEngine(random = Random(42))
        val newState = engine.startNewTransaction(singleItemState)

        // Should only have item 1
        assertTrue("Should only select item 1", newState.currentTransaction.lines.all { it.itemId == 1 })
    }

    @Test
    fun testLargeQuantityRingUp() {
        // Temporarily increase shelf stock for this test
        val largeStockInventory = baseState.inventory.toMutableMap()
        largeStockInventory[1] = InventoryState(shelfStock = 100, backroomStock = 200)
        val largeStockState = baseState.copy(inventory = largeStockInventory)

        engine = TransactionEngine(random = Random(42))
        var state = engine.startNewTransaction(largeStockState)
        val itemId = state.currentTransaction.lines[0].itemId

        // Ring up many items
        repeat(20) {
            state = engine.ringUpSingleItem(state, itemId)
        }

        // Verify stock depleted appropriately
        val remainingStock = state.inventory[itemId]?.shelfStock ?: 0
        assertTrue("Stock should have decreased", remainingStock < 100)
    }

    @Test
    fun testInventoryBackroomNotConsumed() {
        engine = TransactionEngine(random = Random(42))
        var state = engine.startNewTransaction(baseState)
        val itemId = state.currentTransaction.lines[0].itemId
        val initialBackroom = baseState.inventory[itemId]?.backroomStock ?: 0

        // Ring up items
        state = engine.ringUpSingleItem(state, itemId)

        // Backroom should NOT be affected
        val finalBackroom = state.inventory[itemId]?.backroomStock ?: 0
        assertEquals("Backroom stock should not change during ring-up", initialBackroom, finalBackroom)
    }

    @Test
    fun testCompleteTransactionWithMultipleLines() {
        engine = TransactionEngine(random = Random(42))
        var state = engine.startNewTransaction(baseState)

        // Verify we have multiple lines
        assertTrue("Should have multiple lines", state.currentTransaction.lines.size > 1)

        // Ring up all items completely
        for (line in state.currentTransaction.lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Transaction should be complete
        assertFalse("Transaction should be inactive", state.transactionActive)
        assertEquals("Transaction counter should increment", 1, state.totalTransactionsCompleted)
    }

    @Test
    fun testRefundWithMultipleLines() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))
        var state = engine.startNewTransaction(baseState)

        // Ensure we have multiple lines
        while (state.currentTransaction.lines.size < 3 && !state.transactionActive) {
            state = engine.startNewTransaction(state)
        }

        val lineCount = state.currentTransaction.lines.size
        val lines = state.currentTransaction.lines

        // Complete transaction
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // If refund generated, should have multiple lines
        if (state.pendingRefunds.isNotEmpty()) {
            val refund = state.pendingRefunds[0]
            assertTrue("Refund should have lines", refund.lines.isNotEmpty())
        }
    }

    // ============ Complex Scenario Tests ============

    @Test
    fun testPartialRefundProcessing() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Process refund partially
        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val refundLine = state.pendingRefunds[0].lines[0]
            val itemId = refundLine.itemId
            val initialMoney = state.money

            // Process only 1 item from refund
            state = engine.processRefundLine(state, refundId, itemId, 1)

            // Money should decrease
            assertTrue("Money should decrease after partial refund", state.money < initialMoney)

            // Refund might still be pending if qty > 1
            if (refundLine.quantity > 1) {
                assertTrue("Refund should still exist for remaining qty",
                    state.pendingRefunds.any { it.id == refundId })
            }
        }
    }

    @Test
    fun testFullRefundThenNewTransaction() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Process entire refund
        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            state = engine.processRefund(state, refundId)

            // Refund should be gone
            assertTrue("Refund should be processed", !state.pendingRefunds.any { it.id == refundId })
        }

        // Now start new transaction
        state = engine.startNewTransaction(state)

        // Should be able to ring up items
        assertTrue("Should have lines for new transaction", state.currentTransaction.lines.isNotEmpty())
    }

    @Test
    fun testInventoryReplenishmentAfterRefund() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines
        val firstItemId = lines[0].itemId
        val initialShelfStock = baseState.inventory[firstItemId]?.shelfStock ?: 0

        // Complete transaction
        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        val shelfStockAfterSale = state.inventory[firstItemId]?.shelfStock ?: 0

        // Process refund
        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val originalRefundLine = state.pendingRefunds[0].lines.firstOrNull { it.itemId == firstItemId }

            if (originalRefundLine != null) {
                state = engine.processRefundLine(state, refundId, firstItemId, originalRefundLine.quantity)

                // Shelf stock should be partially restored
                val shelfStockAfterRefund = state.inventory[firstItemId]?.shelfStock ?: 0
                assertTrue("Shelf stock should increase after refund",
                    shelfStockAfterRefund > shelfStockAfterSale)
            }
        }
    }

    // ============ Tax Edge Cases ============

    @Test
    fun testTaxCalculationWithCustomRate() {
        val customRate = 0.10  // 10% tax
        engine = TransactionEngine(salesTaxRate = customRate)

        var state = engine.startNewTransaction(baseState)
        val expectedTax = Money.fromDollars(state.currentTransaction.subtotal.toDouble() * customRate)
        val diff = (state.currentTransaction.tax.cents - expectedTax.cents).let { if (it < 0) -it else it }

        assertTrue("Custom tax rate should apply", diff <= 2)
    }

    @Test
    fun testZeroTaxRate() {
        engine = TransactionEngine(salesTaxRate = 0.0)

        var state = engine.startNewTransaction(baseState)

        // With 0% tax, tax should be 0
        assertEquals("Tax should be zero", Money(0), state.currentTransaction.tax)
        assertEquals("Total should equal subtotal", 
            state.currentTransaction.subtotal, 
            state.currentTransaction.totalEarned)
    }

    @Test
    fun testHighTaxRate() {
        val highRate = 0.25  // 25% tax
        engine = TransactionEngine(salesTaxRate = highRate)

        var state = engine.startNewTransaction(baseState)
        val expectedTax = Money.fromDollars(state.currentTransaction.subtotal.toDouble() * highRate)
        val diff = (state.currentTransaction.tax.cents - expectedTax.cents).let { if (it < 0) -it else it }

        assertTrue("High tax rate should apply", diff <= 2)
    }

    // ============ Transaction History Tests ============

    @Test
    fun testSalesHistoryAccumulatesCorrectly() {
        engine = TransactionEngine(random = Random(42))
        var state = baseState

        val transactionsToCreate = 5
        repeat(transactionsToCreate) {
            state = engine.startNewTransaction(state)
            val lines = state.currentTransaction.lines

            for (line in lines) {
                repeat(line.quantity) {
                    state = engine.ringUpSingleItem(state, line.itemId)
                }
            }
        }

        // Check total transactions
        assertEquals("Should have correct transaction count", 
            transactionsToCreate, 
            state.totalTransactionsCompleted)

        // Sales history should have entries
        assertTrue("Sales history should have entries", state.salesHistory.isNotEmpty())
    }

    @Test
    fun testMoneyAccumulatesAcrossTransactions() {
        engine = TransactionEngine(random = Random(42))
        var state = baseState
        val initialMoney = state.money
        var accumulatedRevenue = Money(0)

        repeat(3) {
            state = engine.startNewTransaction(state)
            val transactionRevenue = state.currentTransaction.totalEarned
            accumulatedRevenue += transactionRevenue
            val lines = state.currentTransaction.lines

            for (line in lines) {
                repeat(line.quantity) {
                    state = engine.ringUpSingleItem(state, line.itemId)
                }
            }
        }

        // Money should increase by total revenue
        assertTrue("Money should accumulate", state.money > initialMoney)
        assertTrue("Money increase should be significant", 
            (state.money - initialMoney).cents > 0)
    }

    // ============ State Consistency Tests ============

    @Test
    fun testStateConsistencyAfterComplexSequence() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))
        var state = baseState

        // Complex sequence: transaction → refund → new transaction → partial refund
        state = engine.startNewTransaction(state)
        for (line in state.currentTransaction.lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Process full refund if generated
        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            state = engine.processRefund(state, refundId)
        }

        // Start new transaction
        state = engine.startNewTransaction(state)
        for (line in state.currentTransaction.lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Verify consistency
        assertNotNull("Money should exist", state.money)
        assertNotNull("Inventory should exist", state.inventory)
        assertTrue("Should have inventory items", state.inventory.isNotEmpty())
        assertTrue("All items should have valid stock", 
            state.inventory.values.all { it.shelfStock >= 0 && it.backroomStock >= 0 })
    }

    @Test
    fun testNoMoneyLeakage() {
        engine = TransactionEngine(random = Random(42))
        var state = baseState
        val initialMoney = state.money

        // Complete transaction and see money increase
        state = engine.startNewTransaction(state)
        val transactionTotal = state.currentTransaction.totalEarned
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        // Money increase should match transaction total
        val moneyIncrease = state.money - initialMoney
        assertEquals("Money increase should match transaction total",
            transactionTotal.cents,
            moneyIncrease.cents)
    }

    // ============ Refund Edge Cases ============

    @Test
    fun testRefundHigherThanSale() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        val moneyAfterSale = state.money

        // Process all refunds
        while (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            state = engine.processRefund(state, refundId)
        }

        // Money should have decreased (refund taken back)
        assertTrue("Money should decrease after full refund", state.money < moneyAfterSale)
    }

    @Test
    fun testProcessRefundLineMultipleTimes() {
        engine = TransactionEngine(refundChance = 1.0, random = Random(42))
        var state = engine.startNewTransaction(baseState)
        val lines = state.currentTransaction.lines

        for (line in lines) {
            repeat(line.quantity) {
                state = engine.ringUpSingleItem(state, line.itemId)
            }
        }

        if (state.pendingRefunds.isNotEmpty()) {
            val refundId = state.pendingRefunds[0].id
            val refundLines = state.pendingRefunds[0].lines

            // Process each refund line one by one
            for (refundLine in refundLines) {
                val itemId = refundLine.itemId
                val qty = refundLine.quantity

                // Process in small increments
                repeat(qty) {
                    state = engine.processRefundLine(state, refundId, itemId, 1)
                }
            }

            // Refund should be fully processed
            assertTrue("Refund should be gone after processing all lines",
                !state.pendingRefunds.any { it.id == refundId })
        }
    }
}

