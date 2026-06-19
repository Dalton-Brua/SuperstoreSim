package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.store.StoreSize
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.example.superstoresimulator.domain.helpers.FakeItemDao

/**
 * Performance regression tests for GameEngine.
 *
 * Uses a [FakeItemDao] backed by an in-memory list (no Mockito dependency).
 */
class PerformanceRegressionTest {

    // ── helper functions ──────────────────────────────────────────────────────

    private fun batch(qty: Int, day: Int = 1, expirationDay: Int = Int.MAX_VALUE): List<ItemBatch> =
        if (qty > 0) listOf(ItemBatch(receivedDay = day, quantity = qty, expirationDay = expirationDay))
        else emptyList()

    private fun inv(shelfStock: Int, backroomStock: Int): InventoryState =
        InventoryState(shelfBatches = batch(shelfStock), backroomBatches = batch(backroomStock))

    // ── test data generators ──────────────────────────────────────────────────

    private val perishableShelfLife = mapOf(
        ItemCategory.DAIRY to 5,
        ItemCategory.BAKERY to 3,
        ItemCategory.PRODUCE to 4,
        ItemCategory.MEAT to 5,
    )

    private fun createLargeItemCatalog(): List<Item> {
        return (1..500).map { index ->
            val category = when (index % 10) {
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
            }
            Item(
                id = "item_${String.format("%03d", index)}",
                name = "Test Item $index",
                price = MoneyData(cents = (500 + index * 10).toLong()),
                description = "Performance test item $index",
                unitCost = MoneyData(cents = (250 + index * 5).toLong()),
                category = category,
                casePack = when (index % 3) {
                    0 -> 6
                    1 -> 12
                    else -> 4
                },
                purchaseWeight = 1.0f,
                tier = "TIER_1",
                shelfLifeDays = perishableShelfLife[category],
            )
        }
    }

    private fun createInventoryForCatalog(
        items: List<Item>,
        shelfQty: Int,
        backroomQty: Int,
        currentDay: Int = 0,
    ): Map<Int, InventoryState> {
        return items.associate { item ->
            val itemId = item.id.removePrefix("item_").toInt()
            val expirationDay = if (item.shelfLifeDays != null) {
                currentDay + item.shelfLifeDays
            } else Int.MAX_VALUE
            itemId to InventoryState(
                shelfBatches = batch(shelfQty, day = currentDay, expirationDay = expirationDay),
                backroomBatches = batch(backroomQty, day = currentDay, expirationDay = expirationDay),
            )
        }
    }

    private fun createStaffRegistry(
        cashierCount: Int = 0,
        stockerCount: Int = 0,
        freshHandlerCount: Int = 0,
        managerCount: Int = 0,
        stockingManagerCount: Int = 0,
        storeManagerCount: Int = 0,
    ): HiredEntityRegistry {
        var registry = HiredEntityRegistry()

        repeat(cashierCount) {
            registry = registry.hireEntity(EntityDef.CASHIER)
        }

        // Hire stockers: first N become stocking managers (BASE→FAST→MANAGER)
        repeat(stockerCount) { i ->
            registry = registry.hireEntity(EntityDef.STOCKER)
            if (i < stockingManagerCount) {
                val entityId = registry.hiredEntities.last().id
                registry = forcePromote(registry, entityId, Tier.MANAGER)
            }
        }

        repeat(freshHandlerCount) {
            registry = registry.hireEntity(EntityDef.FRESH_HANDLER)
        }

        // Hire managers: first N become store managers (BASE→FAST→MANAGER)
        repeat(managerCount) { i ->
            registry = registry.hireEntity(EntityDef.MANAGER)
            if (i < storeManagerCount) {
                val entityId = registry.hiredEntities.last().id
                registry = forcePromote(registry, entityId, Tier.MANAGER)
            }
        }

        return registry
    }

    private fun forcePromote(registry: HiredEntityRegistry, entityId: Int, targetTier: Tier): HiredEntityRegistry {
        var r = registry
        val entity = r.getById(entityId)
        var currentTier = entity.tier
        while (currentTier != targetTier) {
            r = r.promoteEntity(entityId)
            currentTier = r.getById(entityId).tier
        }
        return r
    }

    // ── engine factory ────────────────────────────────────────────────────────

    private fun newEngine(items: List<Item>): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        return createTestGameEngine(cache)
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

    @Test
    fun `simulateWeek benchmark — skip week user path`() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 30, backroomQty = 30)
        val staffRegistry = createStaffRegistry(
            cashierCount = 20,
            stockerCount = 15,
            stockingManagerCount = 2,
            freshHandlerCount = 8,
            managerCount = 2,
            storeManagerCount = 1,
        )

        gameEngine.setGameSpeed(1f)
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(10_000_000_00),
            currentStoreSize = StoreSize.SUPERSTORE,
            freshAutoOrderConfig = FreshAutoOrderConfig(enabled = true),
            normalAutoOrderConfig = NormalAutoOrderConfig(enabled = true),
            storeManagerConfig = StoreManagerConfig(),
            truckConfig = TruckConfig(deliveryDays = setOf(0, 2, 4)),
            autoHireBudget = Money(500_000),
        )

        // Warm up JIT
        gameEngine.simulateWeek()
        gameEngine.state = gameEngine.state.copy(
            showEndOfWeekReport = false,
            playerPausedTime = false,
        )

        // Actual measurement
        val startTime = System.nanoTime()
        gameEngine.simulateWeek()
        val elapsed = (System.nanoTime() - startTime) / 1_000_000.0

        println("=== simulateWeek() Benchmark (Superstore, 500 items, 48 staff) ===")
        println("Total: ${elapsed}ms")
        println("Final day: ${gameEngine.state.currentTime.dayNumber}")

        assertTrue(
            "simulateWeek too slow: ${elapsed}ms. Expected < 2000ms.",
            elapsed < 2000.0
        )
    }

    @Test
    fun `tick performance under realistic load`() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 10, backroomQty = 10)
        val staffRegistry = createStaffRegistry(
            cashierCount = 5,
            stockerCount = 5,
            freshHandlerCount = 2,
            managerCount = 1,
        )

        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(100_000_00),
            freshAutoOrderConfig = FreshAutoOrderConfig(enabled = true),
            normalAutoOrderConfig = NormalAutoOrderConfig(enabled = true),
        )
        gameEngine.startTransaction()

        val startTime = System.nanoTime()
        repeat(1000) {
            gameEngine.tick(16L)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0

        assertTrue(
            "Tick performance regression detected: 1000 ticks took ${elapsedMs}ms " +
            "(${elapsedMs / 1000}ms per tick). Expected < 500ms total.",
            elapsed < 500_000_000L
        )

        println("Realistic load (500 items, 13 staff, perishables, auto-order): " +
            "1000 ticks completed in ${elapsedMs}ms (${elapsedMs / 1000}ms per tick)")
    }

    @Test
    fun `tick performance under maximum load`() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 50, backroomQty = 50)
        val staffRegistry = createStaffRegistry(
            cashierCount = 25,
            stockerCount = 20,
            stockingManagerCount = 3,
            freshHandlerCount = 10,
            managerCount = 2,
            storeManagerCount = 1,
        )

        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(10_000_000_00),
            currentStoreSize = StoreSize.SUPERSTORE,
            freshAutoOrderConfig = FreshAutoOrderConfig(enabled = true),
            normalAutoOrderConfig = NormalAutoOrderConfig(enabled = true),
            storeManagerConfig = StoreManagerConfig(),
            truckConfig = TruckConfig(deliveryDays = setOf(0, 2, 4)),
        )
        gameEngine.startTransaction()

        val startTime = System.nanoTime()
        repeat(1000) {
            gameEngine.tick(16L)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0

        assertTrue(
            "Tick performance regression under SUPERSTORE load: 1000 ticks took ${elapsedMs}ms " +
            "(${elapsedMs / 1000}ms per tick). Expected < 800ms total.",
            elapsed < 800_000_000L
        )

        println("Superstore load (500 items, 60 staff, full configs): " +
            "1000 ticks completed in ${elapsedMs}ms (${elapsedMs / 1000}ms per tick)")
    }

    @Test
    fun `tick performance with minimal work`() {
        val inventory = (1..10).associate { it to inv(10, 10) }
        gameEngine.state = gameEngine.state.copy(inventory = inventory)

        val startTime = System.nanoTime()
        repeat(10_000) {
            gameEngine.tick(16L)
        }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0

        assertTrue(
            "Empty tick performance regression: 10000 ticks took ${elapsedMs}ms " +
            "(${elapsedMs / 10000}ms per tick). Expected < 100ms total.",
            elapsed < 100_000_000L
        )

        println("Minimal work: 10000 ticks completed in ${elapsedMs}ms (${elapsedMs / 10000}ms per tick)")
    }

    @Test
    fun `transaction processing performance`() {
        val inventory = (1..10).associate { it to inv(100, 100) }
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            money = Money(100_000_00)
        )

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

        assertTrue(
            "Transaction processing regression: 100 transactions took ${elapsedMs}ms. Expected < 50ms.",
            elapsed < 50_000_000L
        )

        println("Transaction processing: 100 transactions completed in ${elapsedMs}ms (${elapsedMs / 100}ms per transaction)")
    }

    @Test
    fun `inventory operations performance`() {
        val inventory = (1..50).associate { it to inv(0, 100) }
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            money = Money(100_000_00)
        )

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

        assertTrue(
            "Inventory operations regression: 1000 operations took ${elapsedMs}ms. Expected < 50ms.",
            elapsed < 50_000_000L
        )

        println("Inventory operations: 1000 operations completed in ${elapsedMs}ms (${elapsedMs / 1000}ms per operation)")
    }

    // ── Offline Play Benchmarks ───────────────────────────────────────────────

    @Test
    fun `offline simulation benchmark — 7 game days`() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 30, backroomQty = 30)
        val staffRegistry = createStaffRegistry(
            cashierCount = 20,
            stockerCount = 15,
            stockingManagerCount = 2,
            freshHandlerCount = 8,
            managerCount = 2,
            storeManagerCount = 1,
        )

        gameEngine.setGameSpeed(1f)
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(10_000_000_00),
            currentStoreSize = StoreSize.SUPERSTORE,
            freshAutoOrderConfig = FreshAutoOrderConfig(enabled = true),
            normalAutoOrderConfig = NormalAutoOrderConfig(enabled = true),
            storeManagerConfig = StoreManagerConfig(),
            truckConfig = TruckConfig(deliveryDays = setOf(0, 2, 4)),
            autoHireBudget = Money(500_000),
        )

        val totalTicks = 10_080
        val simulationDeltaMs = 500L
        var daysCrossed = 0
        val dayTimings = mutableListOf<Double>()
        var dayStart = System.nanoTime()
        var lastDay = gameEngine.state.currentTime.dayNumber

        val startTime = System.nanoTime()
        for (tick in 1..totalTicks) {
            if (gameEngine.state.showEndOfDayReport) {
                gameEngine.dismissEndOfDayReport()
            }
            gameEngine.tick(simulationDeltaMs)

            val currentDay = gameEngine.state.currentTime.dayNumber
            if (currentDay != lastDay) {
                daysCrossed++
                val dayElapsed = (System.nanoTime() - dayStart) / 1_000_000.0
                dayTimings.add(dayElapsed)
                dayStart = System.nanoTime()
                lastDay = currentDay
            }
        }
        // Dismiss final report if pending
        if (gameEngine.state.showEndOfDayReport) {
            gameEngine.dismissEndOfDayReport()
        }

        val totalElapsed = System.nanoTime() - startTime
        val totalMs = totalElapsed / 1_000_000.0

        println("=== Offline Simulation Benchmark: 7 Game Days ===")
        println("Total: ${totalMs}ms for $totalTicks ticks ($daysCrossed day rollovers)")
        println("Average: ${totalMs / totalTicks}ms per tick")
        dayTimings.forEachIndexed { i, ms ->
            println("  Day ${i + 1}: ${ms}ms")
        }

        assertTrue(
            "Offline 7-day simulation too slow: ${totalMs}ms. Expected < 5000ms. " +
            "This is the offline catch-up budget — if desktop exceeds 5s, device will be 30s+.",
            totalElapsed < 5_000_000_000L
        )
    }

    @Test
    fun `day rollover performance`() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 20, backroomQty = 20)
        val staffRegistry = createStaffRegistry(
            cashierCount = 15,
            stockerCount = 12,
            stockingManagerCount = 2,
            freshHandlerCount = 6,
            managerCount = 2,
            storeManagerCount = 1,
        )

        gameEngine.setGameSpeed(1f)
        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(10_000_000_00),
            currentStoreSize = StoreSize.SUPERSTORE,
            freshAutoOrderConfig = FreshAutoOrderConfig(enabled = true),
            normalAutoOrderConfig = NormalAutoOrderConfig(enabled = true),
            storeManagerConfig = StoreManagerConfig(),
            truckConfig = TruckConfig(deliveryDays = setOf(0, 1, 2, 3, 4, 5, 6)),
            autoHireBudget = Money(500_000),
        )

        // Fast-forward to near midnight of current day (minute 1430 = 23:50)
        val currentDay = gameEngine.state.currentTime.dayNumber
        val targetMinute = (currentDay.toLong() + 1) * 1440L - 10
        gameEngine.state = gameEngine.simulateUntil(gameEngine.state, targetMinute)
        if (gameEngine.state.showEndOfDayReport) {
            gameEngine.dismissEndOfDayReport()
        }

        val rolloverTimings = mutableListOf<Double>()
        val ticksPerRollover = 10

        repeat(14) {
            if (gameEngine.state.showEndOfDayReport) {
                gameEngine.dismissEndOfDayReport()
            }

            val rolloverStart = System.nanoTime()
            // Simulate ~10 minutes around midnight boundary
            repeat(ticksPerRollover) {
                if (gameEngine.state.showEndOfDayReport) {
                    gameEngine.dismissEndOfDayReport()
                }
                gameEngine.tick(500L)
            }
            val rolloverMs = (System.nanoTime() - rolloverStart) / 1_000_000.0
            rolloverTimings.add(rolloverMs)

            // Fast-forward to next near-midnight
            val day = gameEngine.state.currentTime.dayNumber
            val nextMidnight = (day.toLong() + 1) * 1440L - 10
            if (gameEngine.state.currentTime.totalMinutesElapsed < nextMidnight) {
                gameEngine.state = gameEngine.simulateUntil(gameEngine.state, nextMidnight)
            }
        }

        println("=== Day Rollover Performance (14 consecutive days) ===")
        rolloverTimings.forEachIndexed { i, ms ->
            println("  Rollover ${i + 1}: ${ms}ms")
        }
        val maxRollover = rolloverTimings.max()
        val avgRollover = rolloverTimings.average()
        println("Max: ${maxRollover}ms, Avg: ${avgRollover}ms")

        assertTrue(
            "Day rollover too slow: max ${maxRollover}ms ($ticksPerRollover ticks). Expected < 500ms.",
            maxRollover < 500.0
        )
    }
}
