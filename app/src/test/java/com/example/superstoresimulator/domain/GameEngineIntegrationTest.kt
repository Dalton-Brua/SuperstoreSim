package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.time.StoreState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for GameEngine core functionality.
 *
 * These tests verify GameEngine's role as a facade orchestrator:
 *  - Initialization and state structure
 *  - State immutability (copy semantics)
 *  - Consistency invariants across all domains
 *  - Proper delegation to managers
 *
 * Tests focus on cross-manager concerns rather than individual manager behavior
 * (which is tested in manager-specific files: StaffManagerTest, InventoryManagerTest, etc.)
 *
 * Uses a [FakeItemDao] backed by an in-memory list instead of Mockito.
 * Mockito cannot subclass [ItemMetadataCache] in this JUnit + JaCoCo
 * environment — byte-buddy conflicts with JaCoCo's instrumentation agent.
 */
class GameEngineIntegrationTest {

    // ── in-memory fake ────────────────────────────────────────────────────────

    private class FakeItemDao(private val items: List<Item>) : ItemDao {
        override suspend fun getAllItems(): List<Item> = items
        override suspend fun getItemById(itemId: String): Item? =
            items.firstOrNull { it.id == itemId }
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

    // ── engine factory ────────────────────────────────────────────────────────

    private fun newEngine(items: List<Item> = testItems): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        return GameEngine(cache)
    }

    // ── fields ────────────────────────────────────────────────────────────────

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        gameEngine = newEngine()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Injects a money balance directly into the engine's state via the
     * internal setter. This ensures the engine's mutable state is updated,
     * not just a local copy.
     */
    private fun setMoney(cents: Long) {
        gameEngine.state = gameEngine.state.copy(money = Money(cents))
    }

    // ── Core initialization ───────────────────────────────────────────────────

    @Test
    fun testGameEngineInitialization() {
        val state = gameEngine.currentState()

        assertNotNull("State should not be null", state)
        assertEquals("Grocery Store", state.storeName)
        assertEquals(Money(0), state.money)
        assertEquals(GameTime(0), state.currentTime)
        assertEquals(StoreState.CLOSED, state.storeState)
        assertTrue("Should have inventory items initialized", state.inventory.isNotEmpty())
    }

    @Test
    fun testGameStateStructure() {
        val state = gameEngine.currentState()

        assertNotNull("storeName should exist", state.storeName)
        assertNotNull("money should exist", state.money)
        assertNotNull("currentTransaction should exist", state.currentTransaction)
        assertNotNull("hiredEntityRegistry should exist", state.hiredEntityRegistry)
        assertNotNull("inventory should exist", state.inventory)
        assertNotNull("currentTime should exist", state.currentTime)
        assertNotNull("storeConfig should exist", state.storeConfig)
        assertNotNull("storeState should exist", state.storeState)
        assertNotNull("playerRole should exist", state.playerRole)
        assertNotNull("currentDayMetrics should exist", state.currentDayMetrics)
        assertNotNull("completedDayMetrics should exist", state.completedDayMetrics)
    }

    // ── State immutability ────────────────────────────────────────────────────

    @Test
    fun testGameStateImmutable() {
        val initialState = gameEngine.currentState()

        gameEngine.updateStoreName("My Store")

        val newState = gameEngine.currentState()

        // Snapshot taken before the update must be unchanged
        assertEquals("Grocery Store", initialState.storeName)
        // Engine state reflects the update
        assertEquals("My Store", newState.storeName)
    }

    @Test
    fun testCurrentStateReturnsLatestState() {
        val state1 = gameEngine.currentState()
        gameEngine.updateStoreName("New Store")
        val state2 = gameEngine.currentState()

        assertNotEquals("Store names should differ", state1.storeName, state2.storeName)
        assertEquals("Latest state should have new name", "New Store", state2.storeName)
    }

    // ── Consistency invariants ────────────────────────────────────────────────

    @Test
    fun testMoneyNeverNegative() {
        setMoney(10_000L)

        repeat(100) {
            gameEngine.buyItemToBackroom(1)
        }

        assertTrue("Money must never go negative",
            gameEngine.currentState().money.cents >= 0)
    }

    @Test
    fun testInventoryNeverNegative() {
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
    fun testStateRemainsConsistent() {
        setMoney(10_000L)
        gameEngine.hireEntity(
            com.example.superstoresimulator.domain.Entities.EntityDef.CASHIER,
            com.example.superstoresimulator.domain.Entities.EntityType.CASHIERS
        )
        gameEngine.buyItemToBackroom(1)
        gameEngine.startTransaction()
        gameEngine.tick(1_000)

        val state = gameEngine.currentState()
        assertNotNull("Money should exist", state.money)
        assertNotNull("Inventory should exist", state.inventory)
        assertTrue("All inventory values must be non-negative",
            state.inventory.values.all { it.shelfStock >= 0 && it.backroomStock >= 0 })
    }

    @Test
    fun testGameStateConsistency() {
        gameEngine.tick(16)

        val state = gameEngine.currentState()

        assertNotNull(state.currentTime)
        assertNotNull(state.storeState)
        assertNotNull(state.inventory)
        assertNotNull(state.money)
    }

    @Test
    fun testMultipleTickCalls() {
        val time0 = gameEngine.currentState().currentTime.totalMinutesElapsed

        gameEngine.tick(100)
        val time1 = gameEngine.currentState().currentTime.totalMinutesElapsed

        gameEngine.tick(100)
        val time2 = gameEngine.currentState().currentTime.totalMinutesElapsed

        // Time progression must be monotonically non-decreasing
        assertTrue(time1 >= time0)
        assertTrue(time2 >= time1)
    }

    @Test
    fun testStoreConfigPreservation() {
        val originalConfig = gameEngine.currentState().storeConfig

        gameEngine.tick(100)

        val newState = gameEngine.currentState()

        assertEquals(originalConfig.openTimeMinutes, newState.storeConfig.openTimeMinutes)
        assertEquals(originalConfig.closeTimeMinutes, newState.storeConfig.closeTimeMinutes)
    }

    @Test
    fun testCurrentStateMethod() {
        val state1 = gameEngine.currentState()
        val time1 = state1.currentTime.totalMinutesElapsed

        gameEngine.tick(100L)

        val state2 = gameEngine.currentState()

        assertNotNull(state2)
        assertNotNull(state2.currentTime)
        // Time must not go backwards
        assertTrue(state2.currentTime.totalMinutesElapsed >= time1)
    }

    @Test
    fun testStoreStateTransitions() {
        assertEquals(StoreState.CLOSED, gameEngine.currentState().storeState)

        // At game-minute 360 (6 AM) the store is open
        val openTime = GameTime(360)
        val state = gameEngine.currentState().copy(currentTime = openTime)

        assertTrue("Store config must report OPEN at 6 AM", state.storeConfig.isOpen(openTime))
    }

    // ── Delegation to managers (smoke tests) ──────────────────────────────────

    /**
     * Smoke test: Verify that cross-manager state updates work together.
     * Hire a cashier, add inventory, ring up items — all should work without crashes
     * and maintain consistency invariants.
     */
    @Test
    fun testCrossDomainOperations() {
        setMoney(10_000L)

        // Hire a cashier (tests StaffManager)
        gameEngine.hireEntity(
            com.example.superstoresimulator.domain.Entities.EntityDef.CASHIER,
            com.example.superstoresimulator.domain.Entities.EntityType.CASHIERS
        )

        // Stock inventory (tests InventoryManager)
        gameEngine.buyItemToBackroom(1)
        gameEngine.stockItemFromBackroom(1)

        // Open store and start a transaction (tests StoreController & TransactionEngine)
        gameEngine.state = gameEngine.state.copy(storeState = StoreState.OPEN)
        gameEngine.startTransaction()

        // Process transaction (tests TransactionEngine)
        gameEngine.tick(100)

        // All invariants must hold
        assertTrue("Money should not be negative", gameEngine.currentState().money.cents >= 0)
        assertTrue("Inventory should be valid",
            gameEngine.currentState().inventory.values
                .all { it.shelfStock >= 0 && it.backroomStock >= 0 })
    }
}

