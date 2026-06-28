package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import kotlin.random.Random

/**
 * Additional comprehensive tests for TransactionEngine
 * Focus on: edge cases, tax handling, and complex scenarios
 */
class TransactionEngineAdvancedTest {

    private lateinit var engine: TransactionEngine
    private lateinit var baseState: GameState
    private lateinit var cache: ItemMetadataCache

    /** Helper to create a batch with the given quantity (non-perishable for testing). */
    private fun batch(qty: Int, day: Int = 1): List<com.example.superstoresimulator.domain.inventory.ItemBatch> =
        if (qty > 0) listOf(com.example.superstoresimulator.domain.inventory.ItemBatch(receivedDay = day, quantity = qty, expirationDay = Int.MAX_VALUE))
        else emptyList()

    /** Helper to create InventoryState from integer quantities. */
    private fun inv(shelfStock: Int, backroomStock: Int): InventoryState =
        InventoryState(shelfBatches = batch(shelfStock), backroomBatches = batch(backroomStock))

    /** Ten starter items (ids 1..10) so basket generation has accessible items to pick. */
    private fun makeCatalog(): List<Item> = (1..10).map { id ->
        Item(
            id = "item_${String.format("%03d", id)}",
            name = "Item $id",
            price = MoneyData(cents = 1_000),
            description = "Test item $id",
            unitCost = MoneyData(cents = 500),
            category = ItemCategory.GROCERY,
            casePack = 6,
        )
    }

    @Before
    fun setUp() {
        // TransactionEngine builds baskets from cached, research-accessible items, so a
        // populated cache is required — without it startNewTransaction returns no lines.
        cache = ItemMetadataCache(FakeItemDao(makeCatalog()))
        runBlocking { cache.initialize() }

        // Base state with 10 items, $1000 money
        val inventory = mutableMapOf<Int, InventoryState>()
        for (i in 1..10) {
            inventory[i] = inv(50, 100)
        }

        baseState = GameState(
            money = Money.fromDollars(1000.0),
            inventory = inventory
        )
    }

    // ============ Edge Case Tests ============

    @Test
    fun testSingleItemInventory() {
        val singleItemInventory = mutableMapOf<Int, InventoryState>(
            1 to inv(5, 10)
        )
        val singleItemState = baseState.copy(inventory = singleItemInventory)

        engine = TransactionEngine(random = Random(42), cache = cache)
        val newState = engine.startNewTransaction(singleItemState)

        // Should only have item 1
        assertTrue("Should only select item 1", newState.currentTransaction.lines.all { it.itemId == 1 })
    }

    @Test
    fun testLargeQuantityRingUp() {
        // Temporarily increase shelf stock for this test
        val largeStockInventory = baseState.inventory.toMutableMap()
        largeStockInventory[1] = inv(100, 200)
        val largeStockState = baseState.copy(inventory = largeStockInventory)

        engine = TransactionEngine(random = Random(42), cache = cache)
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
        engine = TransactionEngine(random = Random(42), cache = cache)
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
        engine = TransactionEngine(random = Random(42), cache = cache)
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

    // ============ Tax Edge Cases ============

    @Test
    fun testTaxCalculationWithCustomRate() {
        val customRate = 0.10  // 10% tax
        engine = TransactionEngine(salesTaxRate = customRate, cache = cache)

        var state = engine.startNewTransaction(baseState)
        val expectedTax = Money.fromDollars(state.currentTransaction.subtotal.toDouble() * customRate)
        val diff = (state.currentTransaction.tax.cents - expectedTax.cents).let { if (it < 0) -it else it }

        assertTrue("Custom tax rate should apply", diff <= 2)
    }

    @Test
    fun testZeroTaxRate() {
        engine = TransactionEngine(salesTaxRate = 0.0, cache = cache)

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
        engine = TransactionEngine(salesTaxRate = highRate, cache = cache)

        var state = engine.startNewTransaction(baseState)
        val expectedTax = Money.fromDollars(state.currentTransaction.subtotal.toDouble() * highRate)
        val diff = (state.currentTransaction.tax.cents - expectedTax.cents).let { if (it < 0) -it else it }

        assertTrue("High tax rate should apply", diff <= 2)
    }

    // ============ Transaction History Tests ============

    @Test
    fun testSalesHistoryAccumulatesCorrectly() {
        engine = TransactionEngine(random = Random(42), cache = cache)
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
        engine = TransactionEngine(random = Random(42), cache = cache)
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

    @Test
    fun testNoMoneyLeakage() {
        engine = TransactionEngine(random = Random(42), cache = cache)
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
}
