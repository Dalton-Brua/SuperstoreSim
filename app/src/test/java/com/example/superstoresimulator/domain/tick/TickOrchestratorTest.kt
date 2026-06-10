package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.createTestGameEngine
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.helpers.FakeTransactionDao
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.expiration.SpoilageManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.traffic.TrafficManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TickOrchestratorTest {

    private val testItems = listOf(
        Item(
            id = "item_1", name = "Apple",
            price = MoneyData(cents = 100), description = "Test",
            unitCost = MoneyData(cents = 50), category = ItemCategory.GROCERY, casePack = 6
        ),
    )

    private lateinit var cache: ItemMetadataCache
    private lateinit var orchestrator: TickOrchestrator
    private lateinit var timeManager: TimeManager

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        val pricingManager = PricingManager(cache)
        val transactionEngine = TransactionEngine(cache = cache, pricingManager = pricingManager)
        timeManager = TimeManager()
        val trafficManager = TrafficManager()
        val staffManager = StaffManager()
        val dayManager = DayManager()
        val storeController = StoreController()
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
            PlayerActionHandler(), transactionEngine, inventoryManager, cache,
        )
        val utilizationTracker = UtilizationTracker(staffManager, inventoryManager, cache)
        orchestrator = TickOrchestrator(
            timeManager, spoilageManager, storeController, pricingManager,
            registerManager, transactionEngine, trafficManager,
            dayRolloverProcessor, trafficProcessor, staffTickProcessor,
            playerTickProcessor, utilizationTracker,
        )
    }

    @Test
    fun `tick returns unchanged state when player paused`() {
        val state = GameState(playerPausedTime = true, money = Money(1000))
        val result = orchestrator.tick(state, 16L)
        assertEquals(state, result.state)
        assertTrue(result.changes.isEmpty())
    }

    @Test
    fun `tick advances time`() {
        val state = GameState(money = Money(1000))
        val result = orchestrator.tick(state, 1000L)
        assertTrue(result.state.currentTime.totalMinutesElapsed > state.currentTime.totalMinutesElapsed)
    }

    @Test
    fun `tick preserves money when no transactions active`() {
        val state = GameState(money = Money(5000))
        val result = orchestrator.tick(state, 16L)
        assertEquals(Money(5000), result.state.money)
    }

    @Test
    fun `tick detects store state transition`() {
        timeManager.syncTime(GameTime(360))
        val state = GameState(
            money = Money(5000),
            currentTime = GameTime(360),
            storeState = StoreState.CLOSED,
        )
        val result = orchestrator.tick(state, 16L)
        assertEquals(StoreState.OPEN, result.state.storeState)
    }

    @Test
    fun `multiple ticks produce monotonically increasing time`() {
        var state = GameState(money = Money(5000))
        var lastMinutes = state.currentTime.totalMinutesElapsed
        repeat(10) {
            val result = orchestrator.tick(state, 100L)
            state = result.state
            assertTrue(state.currentTime.totalMinutesElapsed >= lastMinutes)
            lastMinutes = state.currentTime.totalMinutesElapsed
        }
    }
}
