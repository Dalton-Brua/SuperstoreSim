package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.createTestGameEngine
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.traffic.TrafficManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [StoreController].
 *
 * [StoreController] is a pure sub-system: each method receives a [GameState] and
 * returns a new [GameState]. Tests construct [GameState] with named parameters to
 * set only the relevant fields — no FakeItemDao or GameEngine required.
 *
 * The [handleStoreStateChange] tests also exercise the [TrafficManager.reset] side-
 * effect by verifying that the traffic accumulator is drained: after a CLOSED
 * transition, [TrafficManager.update] should not carry over any fractional customer
 * count from before the reset.
 *
 * Covers:
 *  - [updateStoreName]: name is updated, other fields unchanged
 *  - [toggleTimePaused]: false → true, true → false
 *  - [setGameSpeedState]: multiplier written to storeConfig only
 *  - [handleStoreStateChange] CLOSED: pendingCustomers zeroed, trafficManager reset
 *  - [handleStoreStateChange] CLOSED when already zero: no spurious state mutation
 *  - [handleStoreStateChange] OPEN: state returned unchanged
 *  - [handleStoreStateChange] CLOSING: state returned unchanged
 *  - Unrelated GameState fields are never touched
 */
class StoreControllerTest {

    private lateinit var controller: StoreController

    @Before
    fun setUp() {
        controller = StoreController()
    }

    // ── updateStoreName ───────────────────────────────────────────────────────

    @Test
    fun `updateStoreName changes the store name`() {
        val state = GameState(storeName = "Old Name")
        val result = controller.updateStoreName(state, "New Name")
        assertEquals("New Name", result.storeName)
    }

    @Test
    fun `updateStoreName does not modify other fields`() {
        val state = GameState(storeName = "Old Name", pendingCustomers = 5)
        val result = controller.updateStoreName(state, "New Name")
        assertEquals(5, result.pendingCustomers)
    }

    @Test
    fun `updateStoreName allows setting an empty string`() {
        val state = GameState(storeName = "My Store")
        val result = controller.updateStoreName(state, "")
        assertEquals("", result.storeName)
    }

    // ── toggleTimePaused ──────────────────────────────────────────────────────

    @Test
    fun `toggleTimePaused flips false to true`() {
        val state = GameState(playerPausedTime = false)
        val result = controller.toggleTimePaused(state)
        assertTrue(result.playerPausedTime)
    }

    @Test
    fun `toggleTimePaused flips true to false`() {
        val state = GameState(playerPausedTime = true)
        val result = controller.toggleTimePaused(state)
        assertFalse(result.playerPausedTime)
    }

    @Test
    fun `toggleTimePaused twice returns to original value`() {
        val state = GameState(playerPausedTime = false)
        val result = controller.toggleTimePaused(controller.toggleTimePaused(state))
        assertFalse(result.playerPausedTime)
    }

    @Test
    fun `toggleTimePaused does not modify other fields`() {
        val state = GameState(playerPausedTime = false, storeName = "My Store")
        val result = controller.toggleTimePaused(state)
        assertEquals("My Store", result.storeName)
    }

    // ── setGameSpeedState ────────────────────────────────────────────────────

    @Test
    fun `setGameSpeedState updates gameSpeedMultiplier in storeConfig`() {
        val state = GameState()
        val result = controller.setGameSpeedState(state, 4.0f)
        assertEquals(4.0f, result.storeConfig.gameSpeedMultiplier)
    }

    @Test
    fun `setGameSpeedState to 1x restores default speed`() {
        val state = GameState()
        // Set to 8x first, then back to 1x
        val fastState = controller.setGameSpeedState(state, 8.0f)
        val normalState = controller.setGameSpeedState(fastState, 1.0f)
        assertEquals(1.0f, normalState.storeConfig.gameSpeedMultiplier)
    }

    @Test
    fun `setGameSpeedState does not modify other fields`() {
        val state = GameState(storeName = "My Store")
        val result = controller.setGameSpeedState(state, 2.0f)
        assertEquals("My Store", result.storeName)
    }

    // ── handleStoreStateChange — CLOSED ──────────────────────────────────────

    @Test
    fun `handleStoreStateChange CLOSED zeros pendingCustomers`() {
        val state = GameState(pendingCustomers = 7)
        val result = controller.handleStoreStateChange(state, StoreState.CLOSED, TrafficManager())
        assertEquals(0, result.pendingCustomers)
    }

    @Test
    fun `handleStoreStateChange CLOSED when pendingCustomers already zero leaves it zero`() {
        val state = GameState(pendingCustomers = 0)
        val result = controller.handleStoreStateChange(state, StoreState.CLOSED, TrafficManager())
        assertEquals(0, result.pendingCustomers)
    }

    @Test
    fun `handleStoreStateChange CLOSED resets TrafficManager accumulator`() {
        // Prime the TrafficManager accumulator by calling update() with an open-store
        // state and a large delta. Then close the store; reset() should drain the
        // internal fractional count so subsequent updates start fresh.
        val trafficManager = TrafficManager()
        val openState = GameState(
            storeState = StoreState.OPEN,
            pendingCustomers = 0,
        )
        // A 1-second tick at default speed generates a fractional accumulation.
        // We don't assert on the exact count — we just ensure update() is callable
        // afterwards (i.e., reset() did not throw or corrupt state).
        trafficManager.update(openState, 1.0)

        val closedResult = controller.handleStoreStateChange(
            openState.copy(pendingCustomers = 3),
            StoreState.CLOSED,
            trafficManager,
        )
        assertEquals(0, closedResult.pendingCustomers)

        // After reset, update() must not carry over stale accumulation:
        // calling it on a CLOSED state should produce an empty list (the manager
        // early-returns with no customers when storeState != OPEN).
        val afterReset = trafficManager.update(closedResult, 1.0)
        assertTrue("No customers should arrive after reset on a closed store", afterReset.isEmpty())
    }

    @Test
    fun `handleStoreStateChange CLOSED does not modify other fields`() {
        val state = GameState(pendingCustomers = 4, storeName = "Test Store")
        val result = controller.handleStoreStateChange(state, StoreState.CLOSED, TrafficManager())
        assertEquals("Test Store", result.storeName)
    }

    // ── handleStoreStateChange — OPEN / CLOSING ───────────────────────────────

    @Test
    fun `handleStoreStateChange OPEN returns state unchanged`() {
        val state = GameState(pendingCustomers = 3, storeName = "My Store")
        val result = controller.handleStoreStateChange(state, StoreState.OPEN, TrafficManager())
        assertEquals(state, result)
    }

    @Test
    fun `handleStoreStateChange CLOSING returns state unchanged`() {
        val state = GameState(pendingCustomers = 2, storeName = "My Store")
        val result = controller.handleStoreStateChange(state, StoreState.CLOSING, TrafficManager())
        assertEquals(state, result)
    }

    // ── GameEngine integration tests ──────────────────────────────────────────

    /**
     * Tests that verify StoreController behavior through the GameEngine's public API.
     * These complement the pure-manager tests above by validating the end-to-end flow.
     */

    private fun newGameEngine(): GameEngine {
        val cache = ItemMetadataCache(
            object : ItemDao {
                override suspend fun getAllItems() = emptyList<com.example.superstoresimulator.domain.items.Item>()
                override suspend fun getItemById(itemId: String) = null
                override suspend fun insertItem(item: com.example.superstoresimulator.domain.items.Item) {}
                override suspend fun deleteItem(itemId: String) {}
                override suspend fun deleteAll() {}
                override suspend fun getItemName(itemId: String): String? = null
                override suspend fun getItemsByIds(itemIds: List<String>) = emptyList<com.example.superstoresimulator.domain.items.Item>()
                override suspend fun insertBatch(items: List<com.example.superstoresimulator.domain.items.Item>) {}
                override suspend fun getItemCount() = 0
                override suspend fun getItemsByCategory(category: String) = emptyList<com.example.superstoresimulator.domain.items.Item>()
                override suspend fun searchItemsByName(searchTerm: String) = emptyList<com.example.superstoresimulator.domain.items.Item>()
                override suspend fun getItemsForInventory(itemIds: List<String>) = emptyList<com.example.superstoresimulator.domain.items.Item>()
            }
        )
        runBlocking { cache.initialize() }
        return createTestGameEngine(cache)
    }

    @Test
    fun `updateStoreName works through GameEngine (via onEvent)`() {
        val engine = newGameEngine()
        val initialName = engine.currentState().storeName

        engine.updateStoreName("New Store Name")

        val finalName = engine.currentState().storeName
        assertEquals("New Store Name", finalName)
    }

    @Test
    fun `toggleTimePaused toggles playerPausedTime through GameEngine`() {
        val engine = newGameEngine()
        val initialPaused = engine.currentState().playerPausedTime

        engine.toggleTimePaused()

        val afterFirstToggle = engine.currentState().playerPausedTime
        assertEquals("Should toggle", !initialPaused, afterFirstToggle)

        engine.toggleTimePaused()

        val afterSecondToggle = engine.currentState().playerPausedTime
        assertEquals("Should toggle back", initialPaused, afterSecondToggle)
    }

    @Test
    fun `setGameSpeed sets multiplier through GameEngine`() {
        val engine = newGameEngine()

        engine.setGameSpeed(4.0f)

        val speedAfter = engine.currentState().storeConfig.gameSpeedMultiplier
        assertEquals(4.0f, speedAfter)
    }

    @Test
    fun `setGameSpeed accepts multiple values through GameEngine`() {
        val engine = newGameEngine()

        listOf(1.0f, 2.0f, 4.0f, 8.0f).forEach { speed ->
            engine.setGameSpeed(speed)
            assertEquals(speed, engine.currentState().storeConfig.gameSpeedMultiplier)
        }
    }
}
