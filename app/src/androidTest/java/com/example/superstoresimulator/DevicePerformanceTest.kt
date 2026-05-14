package com.example.superstoresimulator

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
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
import org.junit.runner.RunWith

/**
 * Instrumented Performance Tests - Run on Real Android Devices
 * 
 * These tests measure GameEngine performance on actual hardware (e.g., Pixel phones)
 * to understand real-world performance characteristics.
 * 
 * Key Differences from JUnit Unit Tests:
 * - Runs on actual device hardware (CPU, memory, etc.)
 * - More accurate performance measurements
 * - Tests device-specific optimizations (ARM vs x86)
 * - Includes Android runtime overhead
 * 
 * To run on a connected Pixel device:
 * ```
 * ./gradlew connectedAndroidTest
 * ```
 * 
 * Or run specific test:
 * ```
 * adb shell am instrument -w -e class com.example.superstoresimulator.DevicePerformanceTest \
 *   com.example.superstoresimulator.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 * 
 * Results will show device model, Android version, and performance metrics.
 */
@RunWith(AndroidJUnit4::class)
class DevicePerformanceTest {

    // ── Device info for result context ───────────────────────────────────────

    private val deviceInfo: String by lazy {
        """
        |Device: ${Build.MANUFACTURER} ${Build.MODEL}
        |Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        |CPU ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}
        |Hardware: ${Build.HARDWARE}
        """.trimMargin()
    }

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

    // ── test data generators ──────────────────────────────────────────────────

    /**
     * Creates a superstore-scale item catalog (500 items).
     */
    private fun createSuperstoreItemCatalog(): List<Item> {
        return (1..500).map { index ->
            Item(
                id = "item_${String.format("%03d", index)}",
                name = "Item $index",
                price = MoneyData(cents = (500 + index * 10).toLong()),
                description = "Device test item $index",
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
     * Creates a staff registry with specified counts.
     */
    private fun createStaffRegistry(cashierCount: Int, stockerCount: Int): HiredEntityRegistry {
        var registry = HiredEntityRegistry()
        
        repeat(cashierCount) {
            registry = registry.hireEntity(EntityDef.CASHIER, EntityType.CASHIERS)
        }
        
        repeat(stockerCount) {
            registry = registry.hireEntity(EntityDef.STOCKER, EntityType.STOCKERS)
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
        // Print device info at start of tests
        println("\n" + "=".repeat(70))
        println("DEVICE PERFORMANCE TEST")
        println("=".repeat(70))
        println(deviceInfo)
        println("=".repeat(70) + "\n")
        
        testItems = createSuperstoreItemCatalog()
        gameEngine = newEngine(testItems)
    }

    // ── Performance Tests (Device-Specific) ───────────────────────────────────

    /**
     * Baseline: Tests tick() performance with minimal work on the device.
     * 
     * This establishes the device's baseline performance for comparison.
     */
    @Test
    fun testDeviceBaseline() {
        val inventory = (1..10).associate {
            it to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE))
            )
        }
        gameEngine.state = gameEngine.state.copy(inventory = inventory)
        
        // Warm-up: Let JIT compiler optimize
        repeat(100) { gameEngine.tick(16L) }
        
        // Measure
        val startTime = System.nanoTime()
        repeat(10_000) {
            gameEngine.tick(16L)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        println("✓ Device Baseline: 10000 ticks in ${elapsedMs}ms (${elapsedMs / 10000}ms per tick)")
        
        // Assert: Should be very fast (device-dependent, so threshold is generous)
        assertTrue(
            "Device baseline too slow: ${elapsedMs}ms. Expected < 100ms.",
            elapsed < 100_000_000L
        )
    }

    /**
     * Realistic: Tests typical mid-game performance on the device.
     * 
     * Setup:
     * - 500 items (superstore catalog)
     * - 10 staff (5 cashiers + 5 stockers)
     * - Active transaction
     */
    @Test
    fun testDeviceRealisticLoad() {
        val inventory = testItems.associate { item ->
            val itemId = item.id.removePrefix("item_").toInt()
            itemId to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE))
            )
        }
        
        val staffRegistry = createStaffRegistry(cashierCount = 5, stockerCount = 5)
        
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(100_000_00)
        )
        gameEngine.startTransaction()
        
        // Warm-up
        repeat(100) { gameEngine.tick(16L) }
        
        // Measure
        val startTime = System.nanoTime()
        repeat(1000) {
            gameEngine.tick(16L)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        println("✓ Realistic Load (500 items, 10 staff): 1000 ticks in ${elapsedMs}ms (${elapsedMs / 1000}ms per tick)")
        
        // Assert: Should maintain 60 FPS (16.67ms per frame)
        // Each tick should be < 5ms to leave headroom for rendering
        assertTrue(
            "Device realistic load too slow: ${elapsedMs}ms. Expected < 200ms for 60 FPS gameplay.",
            elapsed < 200_000_000L
        )
    }

    /**
     * Maximum: Tests superstore-level performance on the device.
     * 
     * Setup:
     * - 500 items (full stock)
     * - 50 staff (25 cashiers + 25 stockers)
     * - Active transaction
     * 
     * This represents the absolute maximum load in late-game.
     */
    @Test
    fun testDeviceMaximumLoad() {
        val inventory = testItems.associate { item ->
            val itemId = item.id.removePrefix("item_").toInt()
            itemId to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 50, expirationDay = Int.MAX_VALUE)),
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 50, expirationDay = Int.MAX_VALUE))
            )
        }
        
        val staffRegistry = createStaffRegistry(cashierCount = 25, stockerCount = 25)
        
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(10_000_000_00)
        )
        gameEngine.startTransaction()
        
        // Warm-up
        repeat(100) { gameEngine.tick(16L) }
        
        // Measure
        val startTime = System.nanoTime()
        repeat(1000) {
            gameEngine.tick(16L)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        println("✓ Maximum Load (500 items, 50 staff): 1000 ticks in ${elapsedMs}ms (${elapsedMs / 1000}ms per tick)")
        
        // Assert: Should still maintain reasonable performance
        // Even at max load, should complete within 1 second
        assertTrue(
            "Device maximum load too slow: ${elapsedMs}ms. Expected < 1000ms.",
            elapsed < 1_000_000_000L
        )
    }

    /**
     * Transaction: Tests transaction processing speed on the device.
     * 
     * Measures how quickly the device can process 100 complete transactions.
     */
    @Test
    fun testDeviceTransactionProcessing() {
        val inventory = (1..10).associate { 
            it to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 100, expirationDay = Int.MAX_VALUE)),
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 100, expirationDay = Int.MAX_VALUE))
            )
        }
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            money = Money(100_000_00)
        )
        
        // Warm-up
        repeat(10) {
            gameEngine.startTransaction()
            val transaction = gameEngine.currentState().currentTransaction
            transaction.lines.forEach { line ->
                repeat(line.quantity) {
                    gameEngine.ringUpItem(line.itemId)
                }
            }
        }
        
        // Measure
        val startTime = System.nanoTime()
        repeat(100) {
            gameEngine.startTransaction()
            val transaction = gameEngine.currentState().currentTransaction
            transaction.lines.forEach { line ->
                repeat(line.quantity) {
                    gameEngine.ringUpItem(line.itemId)
                }
            }
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0
        
        println("✓ Transaction Processing: 100 transactions in ${elapsedMs}ms (${elapsedMs / 100}ms per transaction)")
        
        // Assert: Should process quickly
        assertTrue(
            "Device transaction processing too slow: ${elapsedMs}ms. Expected < 100ms.",
            elapsed < 100_000_000L
        )
    }

    /**
     * Memory: Tests that the engine doesn't leak memory during sustained operation.
     * 
     * Runs 5000 ticks and measures memory before/after to detect leaks.
     */
    @Test
    fun testDeviceMemoryStability() {
        val inventory = testItems.take(100).associate { item ->
            val itemId = item.id.removePrefix("item_").toInt()
            itemId to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE))
            )
        }
        
        val staffRegistry = createStaffRegistry(cashierCount = 10, stockerCount = 10)
        
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(100_000_00)
        )
        
        // Force GC and measure baseline
        System.gc()
        Thread.sleep(100)
        val runtime = Runtime.getRuntime()
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()
        
        // Run sustained load
        repeat(5000) {
            gameEngine.tick(16L)
            if (it % 100 == 0) {
                gameEngine.startTransaction()
            }
        }
        
        // Force GC and measure after
        System.gc()
        Thread.sleep(100)
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        
        val memoryDelta = (memoryAfter - memoryBefore) / 1024 / 1024 // MB
        
        println("✓ Memory Stability: ${memoryDelta}MB delta after 5000 ticks")
        println("  Before: ${memoryBefore / 1024 / 1024}MB")
        println("  After:  ${memoryAfter / 1024 / 1024}MB")
        
        // Assert: Memory growth should be minimal (< 50MB for 5000 ticks)
        assertTrue(
            "Potential memory leak detected: ${memoryDelta}MB growth. Expected < 50MB.",
            memoryDelta < 50
        )
    }

    /**
     * Summary: Prints a comprehensive performance report for the device.
     * 
     * Run this test last to get a full summary of device capabilities.
     */
    @Test
    fun testZZ_DevicePerformanceSummary() {
        println("\n" + "=".repeat(70))
        println("DEVICE PERFORMANCE SUMMARY")
        println("=".repeat(70))
        println(deviceInfo)
        println("\nRecommendations:")
        
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) 
            as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        println("• Available RAM: ${memInfo.availMem / 1024 / 1024}MB")
        println("• Total RAM: ${memInfo.totalMem / 1024 / 1024}MB")
        println("• Low Memory: ${if (memInfo.lowMemory) "YES (Reduce max items/staff)" else "NO"}")
        println("\nRun individual tests above to see detailed performance metrics.")
        println("=".repeat(70) + "\n")
        
        // This test always passes - it's just for information
        assertTrue(true)
    }
}
