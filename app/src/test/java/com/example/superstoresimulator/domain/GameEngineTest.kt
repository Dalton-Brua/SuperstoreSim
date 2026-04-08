package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.time.StoreState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GameEngine.
 *
 * Uses a [FakeItemDao] backed by an in-memory list instead of Mockito.
 * Mockito cannot subclass [ItemMetadataCache] in this JUnit + JaCoCo
 * environment — byte-buddy conflicts with JaCoCo's instrumentation agent,
 * producing MockitoException ← IllegalStateException ← IllegalArgumentException.
 */
class GameEngineTest {

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

    // ── engine factory ────────────────────────────────────────────────────────

    /**
     * Creates a [GameEngine] with an empty item catalogue.
     * All tests in this class use no items so inventory-dependent paths are
     * not exercised — this matches the original intent of the Mockito setup
     * that returned `emptyMap()` for [ItemMetadataCache.getAllItems].
     */
    private fun newEngine(): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(emptyList()))
        runBlocking { cache.initialize() }
        return GameEngine(cache)
    }

    // ── fields ────────────────────────────────────────────────────────────────

    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        gameEngine = newEngine()
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun testGameEngineInitialization() {
        val state = gameEngine.currentState()

        assertNotNull(state)
        assertEquals("Grocery Store", state.storeName)
        assertEquals(Money(0), state.money)
        assertEquals(GameTime(0), state.currentTime)
        assertEquals(StoreState.CLOSED, state.storeState)
    }

    @Test
    fun testGameEngineStateImmutable() {
        val initialState = gameEngine.currentState()

        gameEngine.updateStoreName("My Store")

        val newState = gameEngine.currentState()

        // Snapshot taken before the update must be unchanged
        assertEquals("Grocery Store", initialState.storeName)
        // Engine state reflects the update
        assertEquals("My Store", newState.storeName)
    }


    @Test
    fun testGameTickProgressesTime() {
        val initialTime = gameEngine.currentState().currentTime.totalMinutesElapsed

        gameEngine.tick(1_000) // 1 real second; playerPausedTime=false (default) so time advances

        val newTime = gameEngine.currentState().currentTime.totalMinutesElapsed

        assertTrue("Time should advance after a 1-second tick", newTime > initialTime)
    }

    @Test
    fun testToggleTimePaused() {
        // GameState.playerPausedTime defaults to FALSE (time runs by default)
        assertFalse("Engine should start with time running (playerPausedTime = false)",
            gameEngine.currentState().playerPausedTime)

        gameEngine.toggleTimePaused()
        assertTrue("After first toggle, time should be paused",
            gameEngine.currentState().playerPausedTime)

        gameEngine.toggleTimePaused()
        assertFalse("After second toggle, time should be running again",
            gameEngine.currentState().playerPausedTime)
    }


    @Test
    fun testGameSpeedMultiplier() {
        // Measure advancement at 1×
        gameEngine.setGameSpeed(1.0f)
        val time1xBefore = gameEngine.currentState().currentTime.totalMinutesElapsed
        gameEngine.tick(1_000)
        val advance1x = gameEngine.currentState().currentTime.totalMinutesElapsed - time1xBefore

        // Fresh engine at 4× — use newEngine() instead of the removed mock field
        gameEngine = newEngine()
        gameEngine.setGameSpeed(4.0f)
        gameEngine.tick(1_000)
        val advance4x = gameEngine.currentState().currentTime.totalMinutesElapsed

        // 4× speed must advance at least twice as far as 1× in the same tick duration
        assertTrue("4× speed should advance more than 1× speed", advance4x > advance1x * 2)
    }

    @Test
    fun testStoreStateTransitions() {
        assertEquals(StoreState.CLOSED, gameEngine.currentState().storeState)

        // At game-minute 360 (6 AM) the store is open
        val openTime = GameTime(360)
        val state = gameEngine.currentState().copy(currentTime = openTime)

        assertTrue("Store config must report OPEN at 6 AM", state.storeConfig.isOpen(openTime))
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

        assertEquals(originalConfig.openTimeMinutes,  newState.storeConfig.openTimeMinutes)
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
    fun testGameStateStructure() {
        val state = gameEngine.currentState()

        assertNotNull(state.storeName)
        assertNotNull(state.money)
        assertNotNull(state.currentTransaction)
        assertNotNull(state.hiredEntityRegistry)
        assertNotNull(state.inventory)
        assertNotNull(state.currentTime)
        assertNotNull(state.storeConfig)
        assertNotNull(state.storeState)
    }
}
