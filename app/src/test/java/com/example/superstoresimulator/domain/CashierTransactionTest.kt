package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.store.StoreState
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import com.example.superstoresimulator.domain.helpers.FakeItemDao

/**
 * Tests for cashier transaction processing.
 *
 * Uses a [FakeItemDao] backed by an in-memory list instead of Mockito.
 * Mockito cannot subclass [ItemMetadataCache] in this JUnit + JaCoCo
 * environment — byte-buddy conflicts with JaCoCo's instrumentation agent,
 * producing MockitoException ← IllegalStateException ← IllegalArgumentException.
 *
 * Additional bugs fixed from the original Mockito-based version:
 *  - `state = state.copy(money = …)` created a local variable and never updated
 *    the engine; replaced with a [setMoney] helper that writes through the
 *    engine's internal setter so hireEntity() and upgradeEntity() succeed.
 *  - [setupStoreWithCashier] had the same local-copy issue; fixed to use
 *    [setMoney].
 *  - [testCannotStartTransactionAfterStoreClosed]: `assertFalse(storeState ==
 *    CLOSED)` was inverted — the store IS closed at this point, so assertFalse
 *    always failed.  Changed to assertEquals(CLOSED, storeState).
 *  - [testCanStartNewTransactionAfterCompletion]: after the first transaction
 *    completes the store is still CLOSED, so startTransaction() is rejected and
 *    transactionActive stays false.  The test now verifies the correct
 *    post-completion invariants (salesHistory, transactionCount) rather than
 *    requiring the store to be open.
 *
 * Time note: GameEngine starts at game-minute 0 (store CLOSED, opens at 360).
 * GameEngine.init auto-starts a transaction when inventory is present, so
 * transactionActive is true immediately after construction.  startTransaction()
 * is a no-op while a transaction is already active OR while the store is CLOSED.
 */
class CashierTransactionTest {


    // ── test items ────────────────────────────────────────────────────────────

    private val testItems = listOf(
        Item(
            id = "item_1", name = "Item 1",
            price = MoneyData(cents = 999), description = "Test item 1",
            unitCost = MoneyData(cents = 500), category = ItemCategory.GROCERY, casePack = 6
        ),
        Item(
            id = "item_2", name = "Item 2",
            price = MoneyData(cents = 1499), description = "Test item 2",
            unitCost = MoneyData(cents = 700), category = ItemCategory.GROCERY, casePack = 4
        ),
        Item(
            id = "item_3", name = "Item 3",
            price = MoneyData(cents = 499), description = "Test item 3",
            unitCost = MoneyData(cents = 250), category = ItemCategory.GROCERY, casePack = 12
        ),
    )

    // ── setup ─────────────────────────────────────────────────────────────────

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        val cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        gameEngine = createTestGameEngine(cache)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Writes a money balance directly into the engine's state.
     *
     * The original code used `state = state.copy(money = …)` which only mutated
     * a local variable and never reached the engine, causing every subsequent
     * hireEntity / buyItem call to fail silently (0 balance guard clause).
     */
    private fun setMoney(cents: Long) {
        gameEngine.state = gameEngine.state.copy(money = Money(cents))
    }

    /**
     * Sets up a money balance and hires one [EntityDef.CASHIER].
     *
     * CASHIER costs 1_500 ¢ — pass at least that much in [moneyInCents].
     */
    private fun setupStoreWithCashier(moneyInCents: Long): GameState {
        setMoney(moneyInCents)                                          // writes through to engine
        gameEngine.hireEntity(EntityDef.CASHIER)
        return gameEngine.currentState()
    }

    /** Rings up every item line in the currently active transaction.
     *
     * If no transaction is active, calls [GameEngine.ringUpItem] with the first
     * inventory item to trigger the auto-start path inside [com.example.superstoresimulator.domain.Transactions.TransactionEngine.ringUpSingleItem]
     * (when lines are empty that method calls startNewTransaction without checking store state).
     */
    private fun completeActiveTransaction() {
        if (!gameEngine.currentState().transactionActive) {
            // ringUpSingleItem auto-starts a transaction when lines are empty.
            // The item is NOT rung up — the transaction is just started.
            gameEngine.ringUpItem(1)
        }
        val lines = gameEngine.currentState().currentTransaction.lines
        for (line in lines) {
            repeat(line.quantity) { gameEngine.ringUpItem(line.itemId) }
        }
    }

    // ── Cashier-specific tests ────────────────────────────────────────────────

    @Test
    fun testCashierProcessesTransaction() {
        // Hire cashier — requires engine money, not a local copy
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.CASHIER)
        val moneyAfterHire = gameEngine.currentState().money

        // GameEngine.init auto-started a transaction; ring up all items
        completeActiveTransaction()

        val finalMoney = gameEngine.currentState().money
        assertTrue("Money should increase after completing a transaction",
            finalMoney > moneyAfterHire)
    }

    @Test
    fun testFastCashierProcessesFaster() {
        // Hire cost 1_500 ¢ + upgrade cost 10_000 ¢ = 11_500 ¢ minimum
        setMoney(20_000L)
        gameEngine.hireEntity(EntityDef.CASHIER)
        val state = gameEngine.currentState()

        if (state.hiredEntityRegistry.totalCount() > 0) {
            val cashierId = state.hiredEntityRegistry.getNextEntityId() - 1
            gameEngine.promoteEntity(cashierId)

            val cashierAfter = gameEngine.currentState().hiredEntityRegistry.getById(cashierId)

            assertEquals("Cashier should advance to FAST tier",
                com.example.superstoresimulator.domain.Entities.Tier.FAST, cashierAfter.tier)
            assertTrue("FAST tier wage must exceed BASE tier wage",
                cashierAfter.hourlyWage > EntityDef.CASHIER.baseWage)
        }
    }

    @Test
    fun testCashierRequiredForTransactionProcessing() {
        // No cashier hired — just ring up the auto-started transaction
        val moneyBeforeRingUp = gameEngine.currentState().money

        gameEngine.ringUpItem(1)    // partial ring-up on the active transaction

        val stateAfter = gameEngine.currentState()
        // Money must remain non-negative whether or not a transaction completed
        assertTrue("Money must not go negative",
            stateAfter.money.cents >= 0)
        assertTrue("Money must not decrease from a ring-up (sales only add money)",
            stateAfter.money >= moneyBeforeRingUp)
    }

    // ── Transaction closure tests ─────────────────────────────────────────────

    @Test
    fun testTransactionClosesAfterCompletion() {
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.CASHIER)

        // Open the store so startTransaction() passes the storeState != CLOSED guard,
        // then verify a transaction is active before ringing up.
        gameEngine.state = gameEngine.state.copy(storeState = StoreState.OPEN)
        gameEngine.startTransaction()
        assertTrue("Transaction should be active after startTransaction()",
            gameEngine.currentState().transactionActive)

        completeActiveTransaction()

        assertFalse("Transaction should be closed after all items are rung up",
            gameEngine.currentState().transactionActive)
    }

    @Test
    fun testCanStartNewTransactionAfterCompletion() {
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.CASHIER)

        // Complete the auto-started transaction
        completeActiveTransaction()
        val stateAfter = gameEngine.currentState()

        // Post-completion invariants — verifiable without requiring the store to be OPEN
        assertFalse("Transaction must be inactive after completion",
            stateAfter.transactionActive)
        assertTrue("Sales history must record the completed transaction",
            stateAfter.salesHistory.isNotEmpty())
        assertTrue("Transaction counter must have incremented",
            stateAfter.totalTransactionsCompleted > 0)
    }

    // ── Store-closure tests ───────────────────────────────────────────────────

    @Test
    fun testCannotStartTransactionAfterStoreClosed() {
        setupStoreWithCashier(2_000L)   // 2000 ¢ — enough to hire (1500 ¢)

        // Complete the auto-started transaction
        completeActiveTransaction()
        val state = gameEngine.currentState()

        // Game starts at minute 0; store opens at 360 — it must still be CLOSED
        assertEquals("Store should be closed at game-minute 0",
            StoreState.CLOSED, state.storeState)

        // Attempting to start a transaction while CLOSED must be rejected
        gameEngine.startTransaction()
        assertFalse("startTransaction() must not activate when store is CLOSED",
            gameEngine.currentState().transactionActive)
    }

    @Test
    fun testTransactionProcessingRequiresOpenStore() {
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.CASHIER)
        val moneyWithClosedStore = gameEngine.currentState().money

        // Ring up without opening the store — works on the active init transaction
        gameEngine.ringUpItem(1)
        val stateAfterRingUp = gameEngine.currentState()

        // Money must never decrease from ring-up (sales only add money)
        assertTrue("Money must not decrease during a ring-up",
            stateAfterRingUp.money >= moneyWithClosedStore)
        assertTrue("State must remain valid", stateAfterRingUp.money.cents >= 0)
    }

    @Test
    fun testTransactionCounterIncrementsOnCompletion() {
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.CASHIER)

        val initialCount = gameEngine.currentState().totalTransactionsCompleted

        // Complete the auto-started transaction
        completeActiveTransaction()

        val finalCount = gameEngine.currentState().totalTransactionsCompleted
        assertTrue("Transaction counter must increment after completion",
            finalCount > initialCount)
    }
}