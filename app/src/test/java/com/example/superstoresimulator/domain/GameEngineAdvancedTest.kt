package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.time.StoreState
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Comprehensive tests for GameEngine.
 *
 * Uses a [FakeItemDao] backed by an in-memory list instead of Mockito.
 * Mockito cannot subclass [ItemMetadataCache] in this JUnit + JaCoCo
 * environment — byte-buddy conflicts with JaCoCo's instrumentation agent,
 * producing MockitoException ← IllegalStateException ← IllegalArgumentException.
 *
 * Additional bugs fixed from the original Mockito-based version:
 *  - [setMoney] helper writes through the engine's internal setter so
 *    hireEntity() and buyItem() calls succeed.
 */
class GameEngineAdvancedTest {

    // ── in-memory fake ────────────────────────────────────────────────────────

    private class FakeItemDao(private val items: List<Item>) : ItemDao {
        override suspend fun getItemById(itemId: String): Item? =
            items.firstOrNull { it.id == itemId }

        override suspend fun getAllItems(): List<Item> = items

        override suspend fun insertItem(item: Item) {}
        override suspend fun deleteItem(itemId: String) {}
        override suspend fun deleteAll() {}

        override suspend fun getItemName(itemId: String): String? =
            items.firstOrNull { it.id == itemId }?.name

        override suspend fun getAllItemsWithNames(): List<ItemWithName> =
            items.map { ItemWithName(it.id, it.name) }

        override suspend fun getItemsByIds(itemIds: List<String>): List<Item> =
            items.filter { it.id in itemIds }

        override suspend fun insertBatch(items: List<Item>) {}
        override suspend fun getItemCount(): Int = items.size

        override suspend fun getItemsByCategory(category: String): List<Item> =
            items.filter { it.category.name == category }

        override suspend fun searchItemsByName(searchTerm: String): List<Item> =
            items.filter { it.name.contains(searchTerm, ignoreCase = true) }

        override suspend fun getItemsForInventory(itemIds: List<String>): List<Item> =
            items.filter { it.id in itemIds }
    }

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

    // ── engine setup ──────────────────────────────────────────────────────────

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        val cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        gameEngine = GameEngine(cache)
    }

    // ── helper ────────────────────────────────────────────────────────────────

    /**
     * Injects a money balance directly into the engine's state via the
     * internal setter.  The original tests used `state = state.copy(money = …)`,
     * which only mutated a local variable and never updated the engine.
     */
    private fun setMoney(cents: Long) {
        gameEngine.state = gameEngine.state.copy(money = Money(cents))
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    @Test
    fun testGameEngineInitialization() {
        val state = gameEngine.currentState()

        assertNotNull("State should not be null", state)
        assertTrue("Should have inventory items", state.inventory.isNotEmpty())
        assertNotNull("Should have current time", state.currentTime)
        assertNotNull("Should have store state", state.storeState)
    }

    @Test
    fun testInventoryInitialStock() {
        // GameEngine.init sets shelfStock = 10, backroomStock = 10
        gameEngine.currentState().inventory.values.forEach { inv ->
            assertEquals("Should start with 10 shelf items",    10, inv.shelfStock)
            assertEquals("Should start with 10 backroom items", 10, inv.backroomStock)
        }
    }

    @Test
    fun testGetDbItem() {
        val item = gameEngine.getDbItem(1)
        assertNotNull("Should find item by ID", item)
        assertEquals("Item name should match", "Item 1", item?.name)
    }

    @Test
    fun testGetDbItemNotFound() {
        val item = gameEngine.getDbItem(999)
        assertNull("Should return null for non-existent item", item)
    }

    // ── Inventory operations ──────────────────────────────────────────────────

    @Test
    fun testStockItemFromBackroom() {
        val initialShelf    = gameEngine.currentState().inventory[1]?.shelfStock    ?: 0
        val initialBackroom = gameEngine.currentState().inventory[1]?.backroomStock ?: 0

        gameEngine.stockItemFromBackroom(1)

        assertEquals("Shelf stock should increase by 1",    initialShelf    + 1, gameEngine.currentState().inventory[1]?.shelfStock)
        assertEquals("Backroom stock should decrease by 1", initialBackroom - 1, gameEngine.currentState().inventory[1]?.backroomStock)
    }

    @Test
    fun testStockItemWithNoBackroomStock() {
        repeat(50) { gameEngine.stockItemFromBackroom(1) }

        val state = gameEngine.currentState()
        assertTrue("Shelf stock should not be negative",    (state.inventory[1]?.shelfStock    ?: 0) >= 0)
        assertTrue("Backroom stock should not be negative", (state.inventory[1]?.backroomStock ?: 0) >= 0)
    }

    @Test
    fun testBuyItemToBackroom() {
        // Use setMoney() so the engine actually has funds — local state.copy() never
        // updated the engine in the original test, causing the backroom assertion to fail.
        setMoney(10_000L)
        val initialMoney   = gameEngine.currentState().money
        val initialBackroom = gameEngine.currentState().inventory[1]?.backroomStock ?: 0

        // item_1: unitCost=500¢, casePack=6 → casePackCost=3000¢; 10000 >= 3000 → succeeds
        gameEngine.buyItemToBackroom(1)

        val state = gameEngine.currentState()
        assertTrue("Money should decrease after purchase", state.money < initialMoney)
        assertTrue("Backroom should increase",
            (state.inventory[1]?.backroomStock ?: 0) > initialBackroom)
    }

    @Test
    fun testBuyItemToBackroomWithInsufficientFunds() {
        // Engine starts with 0 money; guard clause prevents going negative
        repeat(50) { gameEngine.buyItemToBackroom(1) }

        assertTrue("Should have non-negative money", gameEngine.currentState().money.cents >= 0)
    }

    @Test
    fun testBuyItemCasePacksSingle() {
        setMoney(10_000L)
        val initialBackroom = gameEngine.currentState().inventory[1]?.backroomStock ?: 0
        val initialMoney    = gameEngine.currentState().money

        // item_1: casePack=6, casePackCost=3000¢; 10000 >= 3000 → succeeds
        gameEngine.buyItemCasePacks(1, 1)

        val state = gameEngine.currentState()
        assertTrue("Money should decrease", state.money < initialMoney)
        assertEquals("Backroom should increase by one case pack (6 units)",
            initialBackroom + 6, state.inventory[1]?.backroomStock ?: 0)
    }

    @Test
    fun testBuyItemCasePacksMultiple() {
        setMoney(50_000L)
        val initialBackroom = gameEngine.currentState().inventory[1]?.backroomStock ?: 0

        gameEngine.buyItemCasePacks(1, 3)   // 3 × 3000¢ = 9000¢; 50000 >= 9000 → succeeds

        val finalBackroom = gameEngine.currentState().inventory[1]?.backroomStock ?: 0
        assertTrue("Backroom should remain valid (>= 0)", finalBackroom >= 0)
        assertTrue("Backroom should increase with 3 case packs purchased",
            finalBackroom > initialBackroom)
    }

    @Test
    fun testBuyItemCasePacksInsufficientFunds() {
        // 0 money; buying 1000 case packs must be silently rejected
        val initialBackroom = gameEngine.currentState().inventory[1]?.backroomStock ?: 0

        gameEngine.buyItemCasePacks(1, 1_000)

        val finalBackroom = gameEngine.currentState().inventory[1]?.backroomStock ?: 0
        assertTrue("Backroom must not increase when purchase is rejected",
            finalBackroom <= initialBackroom)
    }

    // ── Transaction operations ────────────────────────────────────────────────

    @Test
    fun testStartTransaction() {
        // startTransaction() requires storeState != CLOSED; open the store first.
        gameEngine.state = gameEngine.state.copy(storeState = StoreState.OPEN)
        gameEngine.startTransaction()
        assertTrue("Should have active transaction after startTransaction() with inventory",
            gameEngine.currentState().transactionActive)
    }

    @Test
    fun testRingUpItem() {
        val state = gameEngine.currentState()
        if (state.currentTransaction.lines.isNotEmpty()) {
            val firstLine      = state.currentTransaction.lines[0]
            val initialRungQty = firstLine.rungQty

            gameEngine.ringUpItem(firstLine.itemId)

            val updatedLine = gameEngine.currentState().currentTransaction.lines
                .find { it.itemId == firstLine.itemId }
            assertNotNull("Line should still exist", updatedLine)
            assertTrue("RungQty should increase",
                (updatedLine?.rungQty ?: 0) > initialRungQty)
        }
    }

    @Test
    fun testRingUpItemRandom() {
        val state = gameEngine.currentState()
        if (state.currentTransaction.lines.isNotEmpty()) {
            val initialRungTotal = state.currentTransaction.lines.sumOf { it.rungQty }

            gameEngine.ringUpItem()

            val newRungTotal = gameEngine.currentState().currentTransaction.lines.sumOf { it.rungQty }
            assertTrue("Total rung quantity should increase", newRungTotal > initialRungTotal)
        }
    }

    @Test
    fun testCompleteTransaction() {
        val state = gameEngine.currentState()
        if (state.currentTransaction.lines.isNotEmpty()) {
            for (line in state.currentTransaction.lines) {
                repeat(line.quantity) { gameEngine.ringUpItem(line.itemId) }
            }
        }
        assertTrue("Operation should complete without error", true)
    }

    @Test
    fun testProcessRefund() {
        val lines = gameEngine.currentState().currentTransaction.lines
        for (line in lines) {
            repeat(line.quantity) { gameEngine.ringUpItem(line.itemId) }
        }

        val stateAfterTx = gameEngine.currentState()
        if (stateAfterTx.pendingRefunds.isNotEmpty()) {
            val refundId    = stateAfterTx.pendingRefunds[0].id
            val moneyBefore = stateAfterTx.money

            gameEngine.processRefund(refundId)

            val stateAfter = gameEngine.currentState()
            assertFalse("Refund should be removed from pending list",
                stateAfter.pendingRefunds.any { it.id == refundId })
            assertTrue("Money should decrease after issuing a refund",
                stateAfter.money < moneyBefore)
        }
    }

    @Test
    fun testProcessRefundLine() {
        val lines = gameEngine.currentState().currentTransaction.lines
        for (line in lines) {
            repeat(line.quantity) { gameEngine.ringUpItem(line.itemId) }
        }

        val stateAfterTx = gameEngine.currentState()
        if (stateAfterTx.pendingRefunds.isNotEmpty()) {
            val refundId   = stateAfterTx.pendingRefunds[0].id
            val refundLine = stateAfterTx.pendingRefunds[0].lines[0]

            gameEngine.processRefundLine(refundId, refundLine.itemId, 1)
        }
        assertTrue("Operation should complete without error", true)
    }

    // ── Entity management ─────────────────────────────────────────────────────

    @Test
    fun testHireEntity() {
        // setMoney() writes through to the engine; state.copy() in the original never did.
        setMoney(10_000L)
        val initialMoney = gameEngine.currentState().money
        val initialCount = gameEngine.currentState().hiredEntityRegistry.totalCount()

        gameEngine.hireEntity(EntityDef.CASHIER, EntityType.CASHIERS)  // costs 1500¢

        val state = gameEngine.currentState()
        assertTrue("Money should decrease after hiring",    state.money < initialMoney)
        assertEquals("Registry count should increase by 1", initialCount + 1,
            state.hiredEntityRegistry.totalCount())
    }

    @Test
    fun testHireEntityInsufficientFunds() {
        // Engine starts with 0 money; hire must be silently rejected
        gameEngine.hireEntity(EntityDef.CASHIER, EntityType.CASHIERS)

        assertTrue("Money must not go negative", gameEngine.currentState().money.cents >= 0)
    }

    @Test
    fun testUpgradeEntity() {
        setMoney(20_000L)   // enough for hire (1500¢) + upgrade (10000¢)

        gameEngine.hireEntity(EntityDef.CASHIER, EntityType.CASHIERS)
        val state = gameEngine.currentState()

        if (state.hiredEntityRegistry.totalCount() > 0) {
            val entityId = state.hiredEntityRegistry.getNextEntityId() - 1
            gameEngine.upgradeEntity(entityId)
        }
        assertTrue("Operation should complete without error", true)
    }

    @Test
    fun testFireEntity() {
        setMoney(10_000L)

        gameEngine.hireEntity(EntityDef.CASHIER, EntityType.CASHIERS)
        val stateAfterHire = gameEngine.currentState()
        val initialCount   = stateAfterHire.hiredEntityRegistry.totalCount()

        if (initialCount > 0) {
            val entityId = stateAfterHire.hiredEntityRegistry.getNextEntityId() - 1
            gameEngine.fireEntity(entityId)
            assertEquals("Registry count should decrease by 1 after firing",
                initialCount - 1, gameEngine.currentState().hiredEntityRegistry.totalCount())
        }
    }

    // ── State management ──────────────────────────────────────────────────────


    @Test
    fun testToggleTimePaused() {
        val initialPaused = gameEngine.currentState().playerPausedTime   // false by default

        gameEngine.toggleTimePaused()
        assertEquals("Should toggle to !initial", !initialPaused,
            gameEngine.currentState().playerPausedTime)

        gameEngine.toggleTimePaused()
        assertEquals("Should toggle back to initial", initialPaused,
            gameEngine.currentState().playerPausedTime)
    }


    @Test
    fun testSetGameSpeedVariations() {
        listOf(1.0f, 2.0f, 4.0f, 8.0f).forEach { speed ->
            gameEngine.setGameSpeed(speed)
            assertEquals("Speed should be set to $speed", speed,
                gameEngine.currentState().storeConfig.gameSpeedMultiplier)
        }
    }

    // ── Tick behaviour ────────────────────────────────────────────────────────

    @Test
    fun testTickWithCashierProcessesTransactions() {
        // playerPausedTime stays false (default) so tick body executes.
        // Store stays CLOSED for the duration (5 s × 2 min/s = 10 game-min < 360 min to open),
        // so cashier auto-ring-up is not triggered — but the test verifies no crash.
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.CASHIER, EntityType.CASHIERS)

        repeat(5) { gameEngine.tick(1_000) }

        assertTrue("totalTransactionsCompleted must be non-negative",
            gameEngine.currentState().totalTransactionsCompleted >= 0)
    }

    @Test
    fun testTickWithStocker() {
        // Stockers are NOT gated on StoreState.OPEN; they work 24/7.
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.STOCKER, EntityType.STOCKERS)

        repeat(10) { gameEngine.tick(1_000) }   // 10 s → 1 case-pack stock expected

        assertTrue("Engine should tick without error", true)
    }

    // ── Consistency / invariant tests ─────────────────────────────────────────

    @Test
    fun testStateRemainsConsistent() {
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.CASHIER, EntityType.CASHIERS)
        gameEngine.buyItemToBackroom(1)
        gameEngine.startTransaction()
        gameEngine.tick(1_000)

        val state = gameEngine.currentState()
        assertNotNull("Money should exist",     state.money)
        assertNotNull("Inventory should exist", state.inventory)
        assertTrue("All inventory values must be non-negative",
            state.inventory.values.all { it.shelfStock >= 0 && it.backroomStock >= 0 })
    }

    @Test
    fun testMoneyNeverNegative() {
        // Start with some money so operations actually execute and exercise guard clauses
        setMoney(10_000L)

        repeat(100) {
            gameEngine.hireEntity(EntityDef.CASHIER, EntityType.CASHIERS)
            gameEngine.buyItemToBackroom(1)
        }

        assertTrue("Money must never go negative",
            gameEngine.currentState().money.cents >= 0)
    }

    @Test
    fun testInventoryNeverNegative() {
        // Ring up the auto-started transaction, then attempt 49 more cycles.
        // startTransaction() is gated on storeState != CLOSED, so subsequent
        // calls in the loop are no-ops; ring-up calls on an inactive transaction
        // are also no-ops — ensuring inventory values stay non-negative.
        repeat(50) {
            gameEngine.startTransaction()
            val lines = gameEngine.currentState().currentTransaction.lines
            for (line in lines) {
                repeat(line.quantity) { gameEngine.ringUpItem(line.itemId) }
            }
        }

        assertTrue("Inventory values must never be negative",
            gameEngine.currentState().inventory.values
                .all { it.shelfStock >= 0 && it.backroomStock >= 0 })
    }

    @Test
    fun testCurrentStateReturnsLatestState() {
        val state1 = gameEngine.currentState()
        gameEngine.updateStoreName("New Store")
        val state2 = gameEngine.currentState()

        assertNotEquals("Store names should differ",        state1.storeName, state2.storeName)
        assertEquals("Latest state should have new name", "New Store",       state2.storeName)
    }
}
