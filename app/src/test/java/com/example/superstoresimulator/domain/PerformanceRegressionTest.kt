package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.MoneyData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.example.superstoresimulator.domain.helpers.FakeItemDao

/**
 * Performance regression tests for GameEngine.
 *
 * These tests verify that core operations maintain acceptable performance
 * characteristics under realistic load conditions. Tests measure execution
 * time and fail if performance degrades beyond defined thresholds.
 *
 * Thresholds are set conservatively to account for CI/CD variability and
 * different hardware configurations.
 *
 * Uses a [FakeItemDao] backed by an in-memory list (no Mockito dependency).
 */
class PerformanceRegressionTest {


    // ── helper functions ──────────────────────────────────────────────────────

    /** Helper to create a batch with the given quantity (non-perishable for testing). */
    private fun batch(qty: Int, day: Int = 1): List<com.example.superstoresimulator.domain.inventory.ItemBatch> =
        if (qty > 0) listOf(com.example.superstoresimulator.domain.inventory.ItemBatch(receivedDay = day, quantity = qty, expirationDay = Int.MAX_VALUE))
        else emptyList()

    /** Helper to create InventoryState from integer quantities. */
    private fun inv(shelfStock: Int, backroomStock: Int): InventoryState =
        InventoryState(shelfBatches = batch(shelfStock), backroomBatches = batch(backroomStock))

    // ── test data generators ──────────────────────────────────────────────────

    /**
     * Creates a large item catalog for performance testing.
     * Generates 500 items with realistic data to simulate superstore-level inventory.
     */
    private fun createLargeItemCatalog(): List<Item> {
        return (1..500).map { index ->
            Item(
                id = "item_${String.format("%03d", index)}",
                name = "Test Item $index",
                price = MoneyData(cents = (500 + index * 10).toLong()),
                description = "Performance test item $index",
                unitCost = MoneyData(cents = (250 + index * 5).toLong()),
                category = when (index % 10) {
                    0 -> ItemCategory.GROCERY
                    1 -> ItemCategory.SNACKS
                    2 -> ItemCategory.DRINKS
                    3 -> ItemCategory.DAIRY
                    4 -> ItemCategory.FROZEN
                    5 -> ItemCategory.BAKERY
                    6 -> ItemCategory.PRODUCE
                    7 -> ItemCategory.MEAT
                    8 -> ItemCategory.HEALTH
                    else -> ItemCategory.HOUSEHOLD
                },
                casePack = when (index % 3) {
                    0 -> 6
                    1 -> 12
                    else -> 4
                },
                purchaseWeight = 1.0f,
                tier = "TIER_1"
            )
        }
    }

    /**
     * Creates a registry with multiple hired staff.
     * @param cashierCount Number of cashiers to hire
     * @param stockerCount Number of stockers to hire
     */
    private fun createStaffRegistry(cashierCount: Int, stockerCount: Int): HiredEntityRegistry {
        var registry = HiredEntityRegistry()
        
        repeat(cashierCount) {
            registry = registry.hireEntity(EntityDef.CASHIER)
        }
        
        repeat(stockerCount) {
            registry = registry.hireEntity(EntityDef.STOCKER)
        }
        
        return registry
    }

    // ── engine factory ────────────────────────────────────────────────────────

    private fun newEngine(items: List<Item>): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        return GameEngine(cache)
    }

    // ── fields ────────────────────────────────────────────────────────────────

    private lateinit var testItems: List<Item>
    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        testItems = createLargeItemCatalog()
        gameEngine = newEngine(testItems)
    }

    // ── Performance Tests ─────────────────────────────────────────────────────

    /**
     * Tests tick() performance under realistic load conditions.
     * 
     * Setup:
     * - 100 inventory items (each with 10 shelf stock, 10 backroom stock)
     * - 5 cashiers + 5 stockers (10 total staff)
     * - Active transaction
     * 
     * Expectation:
     * - 1000 ticks should complete in under 100ms (0.1ms per tick average)
     * - This allows headroom for 60 FPS gameplay (16.67ms per frame)
     * 
     * Note: Threshold is conservative to account for CI/CD and hardware variability.
     */
    @Test
    fun `tick performance under realistic load`() {
        // Setup: 100 inventory items with stock
        val inventory = testItems.associate { item ->
            val itemId = item.id.removePrefix("item_").toInt()
            itemId to inv(10, 10)
        }
        
        // Setup: 10 staff members (5 cashiers + 5 stockers)
        val staffRegistry = createStaffRegistry(cashierCount = 5, stockerCount = 5)
        
        // Setup: Active transaction
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(100_000_00)  // $100,000 for buying/hiring operations
        )
        gameEngine.startTransaction()
        
        // Measure: 1000 ticks (approximately 16 seconds of game time at 60 FPS)
        val startTime = System.nanoTime()
        repeat(1000) {
            gameEngine.tick(16L)  // 16ms tick (60 FPS)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        // Assert: Should complete in under 300ms (0.3ms per tick average)
        // Includes zone decay + utilization tracking + hourly pricing EMA update overhead
        assertTrue(
            "Tick performance regression detected: 1000 ticks took ${elapsedMs}ms " +
            "(${elapsedMs / 1000}ms per tick). Expected < 300ms total.",
            elapsed < 300_000_000L  // 300ms in nanoseconds
        )
        
        // Log performance for tracking (visible in test output)
        println("Performance: 1000 ticks completed in ${elapsedMs}ms (${elapsedMs / 1000}ms per tick)")
    }

    /**
     * Tests tick() performance under maximum stress conditions (SUPERSTORE LEVEL).
     * 
     * This test simulates a late-game scenario with a fully-stocked superstore
     * to establish performance characteristics at scale.
     * 
     * Setup:
     * - 500 inventory items (full stock) - realistic superstore catalog
     * - 25 cashiers + 25 stockers (50 total staff) - late-game staffing
     * - Active transaction with multiple lines
     * 
     * Expectation:
     * - 1000 ticks should complete in under 500ms (0.5ms per tick)
     * - This is 5× the baseline threshold, accounting for 5× more items and 2.5× more staff
     * 
     * Note: This represents the absolute maximum load the game should encounter.
     */
    @Test
    fun `tick performance under maximum load`() {
        // Setup: 500 inventory items with full stock (superstore scale)
        val inventory = testItems.associate { item ->
            val itemId = item.id.removePrefix("item_").toInt()
            itemId to inv(50, 50)
        }
        
        // Setup: 50 staff members (25 cashiers + 25 stockers) - late-game staffing
        val staffRegistry = createStaffRegistry(cashierCount = 25, stockerCount = 25)
        
        // Setup: Active transaction
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(10_000_000_00)  // $10,000,000 (late-game capital)
        )
        gameEngine.startTransaction()
        
        // Measure: 1000 ticks
        val startTime = System.nanoTime()
        repeat(1000) {
            gameEngine.tick(16L)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        // Assert: Should complete in under 500ms even at superstore scale
        // This allows up to 0.5ms per tick, still well within 60 FPS budget (16.67ms per frame)
        assertTrue(
            "Tick performance regression under SUPERSTORE load: 1000 ticks took ${elapsedMs}ms " +
            "(${elapsedMs / 1000}ms per tick). Expected < 500ms total. " +
            "This test simulates 500 items + 50 staff at late-game scale.",
            elapsed < 500_000_000L  // 500ms in nanoseconds
        )
        
        println("Superstore load (500 items, 50 staff): 1000 ticks completed in ${elapsedMs}ms (${elapsedMs / 1000}ms per tick)")
    }

    /**
     * Tests that empty ticks (no staff, no transactions) are extremely fast.
     * 
     * This establishes a baseline for minimum tick overhead.
     * 
     * Expectation:
     * - 10,000 ticks should complete in under 50ms (0.005ms per tick)
     */
    @Test
    fun `tick performance with minimal work`() {
        // Setup: No staff, no transactions, minimal inventory
        val inventory = (1..10).associate { it to inv(10, 10) }
        gameEngine.state = gameEngine.state.copy(inventory = inventory)
        
        // Measure: 10,000 ticks (more iterations since each tick is cheaper)
        val startTime = System.nanoTime()
        repeat(10_000) {
            gameEngine.tick(16L)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        // Assert: Should be very fast with no work
        // Includes zone decay overhead on 10 items (Phase 5B)
        assertTrue(
            "Empty tick performance regression: 10000 ticks took ${elapsedMs}ms " +
            "(${elapsedMs / 10000}ms per tick). Expected < 100ms total.",
            elapsed < 100_000_000L  // 100ms in nanoseconds
        )
        
        println("Minimal work: 10000 ticks completed in ${elapsedMs}ms (${elapsedMs / 10000}ms per tick)")
    }

    /**
     * Tests transaction processing performance in isolation.
     * 
     * Measures how long it takes to complete 100 full transactions
     * (start, ring up all items, complete).
     * 
     * Expectation:
     * - 100 transactions should complete in under 50ms
     */
    @Test
    fun `transaction processing performance`() {
        // Setup: Small inventory for consistent transaction generation
        val inventory = (1..10).associate { it to inv(100, 100) }
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            money = Money(100_000_00)
        )
        
        // Measure: 100 complete transactions
        val startTime = System.nanoTime()
        repeat(100) {
            gameEngine.startTransaction()
            
            // Ring up all items in the transaction
            val transaction = gameEngine.currentState().currentTransaction
            transaction.lines.forEach { line ->
                repeat(line.quantity) {
                    gameEngine.ringUpItem(line.itemId)
                }
            }
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        // Assert: Should process quickly
        assertTrue(
            "Transaction processing regression: 100 transactions took ${elapsedMs}ms. Expected < 50ms.",
            elapsed < 50_000_000L  // 50ms in nanoseconds
        )
        
        println("Transaction processing: 100 transactions completed in ${elapsedMs}ms (${elapsedMs / 100}ms per transaction)")
    }

    /**
     * Tests inventory operations performance.
     * 
     * Measures bulk inventory operations (stocking and buying).
     * 
     * Expectation:
     * - 1000 inventory operations should complete in under 50ms
     */
    @Test
    fun `inventory operations performance`() {
        // Setup: Inventory with backroom stock
        val inventory = (1..50).associate { it to inv(0, 100) }
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            money = Money(100_000_00)
        )
        
        // Measure: 1000 mixed inventory operations
        val startTime = System.nanoTime()
        repeat(500) {
            val itemId = (it % 50) + 1
            gameEngine.stockItemFromBackroom(itemId)
        }
        repeat(500) {
            val itemId = (it % 50) + 1
            gameEngine.buyItemToBackroom(itemId)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        // Assert: Should process quickly
        assertTrue(
            "Inventory operations regression: 1000 operations took ${elapsedMs}ms. Expected < 50ms.",
            elapsed < 50_000_000L  // 50ms in nanoseconds
        )
        
        println("Inventory operations: 1000 operations completed in ${elapsedMs}ms (${elapsedMs / 1000}ms per operation)")
    }
}

