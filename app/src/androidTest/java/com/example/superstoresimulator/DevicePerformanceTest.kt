package com.example.superstoresimulator

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.FreshAutoOrderConfig
import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.NormalAutoOrderConfig
import com.example.superstoresimulator.domain.StoreManagerConfig
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.expiration.SpoilageManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.progression.ProgressionManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.tick.DayRolloverProcessor
import com.example.superstoresimulator.domain.tick.PlayerTickProcessor
import com.example.superstoresimulator.domain.tick.StaffTickProcessor
import com.example.superstoresimulator.domain.tick.TickOrchestrator
import com.example.superstoresimulator.domain.tick.TrafficProcessor
import com.example.superstoresimulator.domain.tick.UtilizationTracker
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.traffic.TrafficManager
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.helpers.FakeTransactionDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DevicePerformanceTest {

    private val deviceInfo: String by lazy {
        """
        |Device: ${Build.MANUFACTURER} ${Build.MODEL}
        |Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        |CPU ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}
        |Hardware: ${Build.HARDWARE}
        """.trimMargin()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private val perishableShelfLife = mapOf(
        ItemCategory.DAIRY to 5,
        ItemCategory.BAKERY to 3,
        ItemCategory.PRODUCE to 4,
        ItemCategory.MEAT to 5,
    )

    private fun batch(qty: Int, day: Int = 0, expirationDay: Int = Int.MAX_VALUE): List<ItemBatch> =
        if (qty > 0) listOf(ItemBatch(receivedDay = day, quantity = qty, expirationDay = expirationDay))
        else emptyList()

    private fun createSuperstoreItemCatalog(): List<Item> {
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
                name = "Item $index",
                price = MoneyData(cents = (500 + index * 10).toLong()),
                description = "Device test item $index",
                unitCost = MoneyData(cents = (250 + index * 5).toLong()),
                category = category,
                casePack = when (index % 3) {
                    0 -> 6; 1 -> 12; else -> 4
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
        repeat(cashierCount) { registry = registry.hireEntity(EntityDef.CASHIER) }
        repeat(stockerCount) { i ->
            registry = registry.hireEntity(EntityDef.STOCKER)
            if (i < stockingManagerCount) {
                val entityId = registry.hiredEntities.last().id
                registry = forcePromote(registry, entityId, Tier.MANAGER)
            }
        }
        repeat(freshHandlerCount) { registry = registry.hireEntity(EntityDef.FRESH_HANDLER) }
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
        var currentTier = r.getById(entityId).tier
        while (currentTier != targetTier) {
            r = r.promoteEntity(entityId)
            currentTier = r.getById(entityId).tier
        }
        return r
    }

    // ── engine factory (inline — androidTest can't see src/test) ─────────────

    private fun newEngine(items: List<Item>): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }

        val pricingManager = PricingManager(cache)
        val transactionEngine = TransactionEngine(cache = cache, pricingManager = pricingManager)
        val timeManager = TimeManager()
        val trafficManager = TrafficManager()
        val staffManager = StaffManager()
        val dayManager = DayManager()
        val storeController = StoreController()
        val playerActionHandler = PlayerActionHandler()
        val progressionManager = ProgressionManager(cache)
        val truckManager = TruckManager(cache)
        val inventoryManager = InventoryManager(cache, truckManager)
        val spoilageManager = SpoilageManager(cache)
        val registerManager = RegisterManager()
        val dayRolloverProcessor = DayRolloverProcessor(staffManager, dayManager, truckManager, inventoryManager, FakeTransactionDao())
        val trafficProcessor = TrafficProcessor(trafficManager, transactionEngine, registerManager)
        val staffTickProcessor = StaffTickProcessor(
            staffManager, inventoryManager, transactionEngine, registerManager, pricingManager, cache,
        )
        val playerTickProcessor = PlayerTickProcessor(
            playerActionHandler, transactionEngine, inventoryManager, cache,
        )
        val utilizationTracker = UtilizationTracker(staffManager, inventoryManager, cache)
        val tickOrchestrator = TickOrchestrator(
            timeManager, spoilageManager, storeController, pricingManager,
            registerManager, transactionEngine, trafficManager,
            dayRolloverProcessor, trafficProcessor, staffTickProcessor,
            playerTickProcessor, utilizationTracker,
            staffManager, dayManager,
        )
        val engine = GameEngine(
            itemMetadataCache = cache,
            tickOrchestrator = tickOrchestrator,
            transactionEngine = transactionEngine,
            inventoryManager = inventoryManager,
            registerManager = registerManager,
            staffManager = staffManager,
            storeController = storeController,
            playerActionHandler = playerActionHandler,
            progressionManager = progressionManager,
            pricingManager = pricingManager,
            dayManager = dayManager,
            timeManager = timeManager,
            trafficManager = trafficManager,
            truckManager = truckManager,
        )
        engine.seedNewGame()
        return engine
    }

    // ── fields ────────────────────────────────────────────────────────────────

    private lateinit var testItems: List<Item>
    private lateinit var gameEngine: GameEngine

    @Before
    fun setUp() {
        println("\n" + "=".repeat(70))
        println("DEVICE PERFORMANCE TEST")
        println("=".repeat(70))
        println(deviceInfo)
        println("=".repeat(70) + "\n")

        testItems = createSuperstoreItemCatalog()
        gameEngine = newEngine(testItems)
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun testDeviceBaseline() {
        val inventory = (1..10).associate {
            it to InventoryState(
                shelfBatches = batch(10),
                backroomBatches = batch(10),
            )
        }
        gameEngine.state = gameEngine.state.copy(inventory = inventory)

        repeat(100) { gameEngine.tick(16L) }

        val startTime = System.nanoTime()
        repeat(10_000) { gameEngine.tick(16L) }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0

        println("Device Baseline: 10000 ticks in ${elapsedMs}ms (${elapsedMs / 10000}ms per tick)")
        assertTrue("Device baseline too slow: ${elapsedMs}ms. Expected < 500ms.", elapsed < 500_000_000L)
    }

    @Test
    fun testDeviceRealisticLoad() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 10, backroomQty = 10)
        val staffRegistry = createStaffRegistry(
            cashierCount = 5, stockerCount = 5, freshHandlerCount = 2, managerCount = 1,
        )

        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(100_000_00),
            freshAutoOrderConfig = FreshAutoOrderConfig(enabled = true),
            normalAutoOrderConfig = NormalAutoOrderConfig(enabled = true),
        )
        gameEngine.startTransaction()

        repeat(100) { gameEngine.tick(16L) }

        val startTime = System.nanoTime()
        repeat(1000) { gameEngine.tick(16L) }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0

        println("Realistic Load (500 items, 13 staff, perishables, auto-order): " +
            "1000 ticks in ${elapsedMs}ms (${elapsedMs / 1000}ms per tick)")
        assertTrue("Device realistic load too slow: ${elapsedMs}ms. Expected < 1000ms.", elapsed < 1_000_000_000L)
    }

    @Test
    fun testDeviceMaximumLoad() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 50, backroomQty = 50)
        val staffRegistry = createStaffRegistry(
            cashierCount = 25, stockerCount = 20, stockingManagerCount = 3,
            freshHandlerCount = 10, managerCount = 2, storeManagerCount = 1,
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

        repeat(100) { gameEngine.tick(16L) }

        val startTime = System.nanoTime()
        repeat(1000) { gameEngine.tick(16L) }
        val elapsed = System.nanoTime() - startTime
        val elapsedMs = elapsed / 1_000_000.0

        println("Maximum Load (500 items, 60 staff, SUPERSTORE, full configs): " +
            "1000 ticks in ${elapsedMs}ms (${elapsedMs / 1000}ms per tick)")
        assertTrue("Device max load too slow: ${elapsedMs}ms. Expected < 2000ms.", elapsed < 2_000_000_000L)
    }

    @Test
    fun testDeviceOfflineSimulation7Days() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 30, backroomQty = 30)
        val staffRegistry = createStaffRegistry(
            cashierCount = 20, stockerCount = 15, stockingManagerCount = 2,
            freshHandlerCount = 8, managerCount = 2, storeManagerCount = 1,
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
        if (gameEngine.state.showEndOfDayReport) {
            gameEngine.dismissEndOfDayReport()
        }

        val totalElapsed = System.nanoTime() - startTime
        val totalMs = totalElapsed / 1_000_000.0

        println("=== DEVICE: Offline Simulation Benchmark: 7 Game Days ===")
        println("Total: ${totalMs}ms for $totalTicks ticks ($daysCrossed day rollovers)")
        println("Average: ${totalMs / totalTicks}ms per tick")
        dayTimings.forEachIndexed { i, ms -> println("  Day ${i + 1}: ${ms}ms") }

        assertTrue(
            "Device offline 7-day simulation too slow: ${totalMs}ms. Expected < 30000ms.",
            totalElapsed < 30_000_000_000L
        )
    }

    @Test
    fun testDeviceDayRolloverPerformance() {
        val inventory = createInventoryForCatalog(testItems, shelfQty = 20, backroomQty = 20)
        val staffRegistry = createStaffRegistry(
            cashierCount = 15, stockerCount = 12, stockingManagerCount = 2,
            freshHandlerCount = 6, managerCount = 2, storeManagerCount = 1,
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

        val currentDay = gameEngine.state.currentTime.dayNumber
        val targetMinute = (currentDay.toLong() + 1) * 1440L - 10
        gameEngine.state = gameEngine.simulateUntil(gameEngine.state, targetMinute)
        if (gameEngine.state.showEndOfDayReport) gameEngine.dismissEndOfDayReport()

        val rolloverTimings = mutableListOf<Double>()
        val ticksPerRollover = 10

        repeat(14) {
            if (gameEngine.state.showEndOfDayReport) gameEngine.dismissEndOfDayReport()

            val rolloverStart = System.nanoTime()
            repeat(ticksPerRollover) {
                if (gameEngine.state.showEndOfDayReport) gameEngine.dismissEndOfDayReport()
                gameEngine.tick(500L)
            }
            val rolloverMs = (System.nanoTime() - rolloverStart) / 1_000_000.0
            rolloverTimings.add(rolloverMs)

            val day = gameEngine.state.currentTime.dayNumber
            val nextMidnight = (day.toLong() + 1) * 1440L - 10
            if (gameEngine.state.currentTime.totalMinutesElapsed < nextMidnight) {
                gameEngine.state = gameEngine.simulateUntil(gameEngine.state, nextMidnight)
            }
        }

        println("=== DEVICE: Day Rollover Performance (14 consecutive days) ===")
        rolloverTimings.forEachIndexed { i, ms -> println("  Rollover ${i + 1}: ${ms}ms") }
        val maxRollover = rolloverTimings.max()
        val avgRollover = rolloverTimings.average()
        println("Max: ${maxRollover}ms, Avg: ${avgRollover}ms")

        assertTrue("Device day rollover too slow: max ${maxRollover}ms. Expected < 2000ms.", maxRollover < 2000.0)
    }

    @Test
    fun testDeviceMemoryStability() {
        val inventory = createInventoryForCatalog(testItems.take(100), shelfQty = 10, backroomQty = 10)
        val staffRegistry = createStaffRegistry(cashierCount = 10, stockerCount = 10)

        gameEngine.state = gameEngine.state.copy(
            inventory = inventory,
            hiredEntityRegistry = staffRegistry,
            money = Money(100_000_00),
        )

        System.gc()
        Thread.sleep(100)
        val runtime = Runtime.getRuntime()
        val memoryBefore = runtime.totalMemory() - runtime.freeMemory()

        repeat(5000) {
            gameEngine.tick(16L)
            if (it % 100 == 0) gameEngine.startTransaction()
        }

        System.gc()
        Thread.sleep(100)
        val memoryAfter = runtime.totalMemory() - runtime.freeMemory()
        val memoryDelta = (memoryAfter - memoryBefore) / 1024 / 1024

        println("Memory Stability: ${memoryDelta}MB delta after 5000 ticks")
        println("  Before: ${memoryBefore / 1024 / 1024}MB, After: ${memoryAfter / 1024 / 1024}MB")

        assertTrue("Potential memory leak: ${memoryDelta}MB growth. Expected < 50MB.", memoryDelta < 50)
    }

    @Test
    fun testZZ_DevicePerformanceSummary() {
        println("\n" + "=".repeat(70))
        println("DEVICE PERFORMANCE SUMMARY")
        println("=".repeat(70))
        println(deviceInfo)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
            as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        println("Available RAM: ${memInfo.availMem / 1024 / 1024}MB")
        println("Total RAM: ${memInfo.totalMem / 1024 / 1024}MB")
        println("Low Memory: ${if (memInfo.lowMemory) "YES" else "NO"}")
        println("=".repeat(70) + "\n")
        assertTrue(true)
    }
}
