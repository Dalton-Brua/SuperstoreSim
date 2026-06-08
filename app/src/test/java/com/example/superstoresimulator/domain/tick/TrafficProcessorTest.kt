package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.traffic.TrafficManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrafficProcessorTest {

    private val testItems = listOf(
        Item(
            id = "item_1", name = "Apple",
            price = MoneyData(cents = 200), description = "Test",
            unitCost = MoneyData(cents = 100), category = ItemCategory.GROCERY, casePack = 6,
        ),
        Item(
            id = "item_2", name = "Bread",
            price = MoneyData(cents = 300), description = "Test",
            unitCost = MoneyData(cents = 150), category = ItemCategory.GROCERY, casePack = 4,
        ),
    )

    private lateinit var processor: TrafficProcessor
    private lateinit var cache: ItemMetadataCache

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        val pricingManager = PricingManager(cache)
        val transactionEngine = TransactionEngine(cache = cache, pricingManager = pricingManager)
        val trafficManager = TrafficManager()
        val registerManager = RegisterManager()
        processor = TrafficProcessor(trafficManager, transactionEngine, registerManager)
    }

    private fun stateWithInventory(
        storeState: StoreState = StoreState.OPEN,
        pendingCustomers: Int = 0,
        registers: List<RegisterState> = listOf(RegisterState(0, assignedCashierId = 1)),
    ): GameState {
        val inventory = mapOf(
            1 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 50, expirationDay = Int.MAX_VALUE)),
            ),
            2 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 50, expirationDay = Int.MAX_VALUE)),
            ),
        )
        var reg = HiredEntityRegistry()
        reg = reg.hireEntity(EntityDef.CASHIER)
        return GameState(
            money = Money(100_000),
            storeState = storeState,
            pendingCustomers = pendingCustomers,
            inventory = inventory,
            registers = registers,
            hiredEntityRegistry = reg,
            staffSchedules = listOf(StaffShift(entityId = 1, startHour = 6)),
        )
    }

    @Test
    fun `no traffic generated when store is closed`() {
        val state = stateWithInventory(storeState = StoreState.CLOSED)
        val result = processor.process(state, delta = 1.0, currentHour = 10)
        assertEquals(0, result.pendingCustomers)
    }

    @Test
    fun `pending customers served when register is manned and free`() {
        val state = stateWithInventory(pendingCustomers = 3)
        val result = processor.process(state, delta = 0.0, currentHour = 10)
        assertTrue(
            "Pending customers should decrease when register serves them",
            result.pendingCustomers < 3 || result.registers.first().transactionActive
        )
    }

    @Test
    fun `no customers served when register already active`() {
        val state = stateWithInventory(
            pendingCustomers = 5,
            registers = listOf(RegisterState(0, assignedCashierId = 1, transactionActive = true)),
        )
        val result = processor.process(state, delta = 0.0, currentHour = 10)
        assertEquals(5, result.pendingCustomers)
    }

    @Test
    fun `no customers served when no cashiers hired`() {
        val inventory = mapOf(
            1 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 50, expirationDay = Int.MAX_VALUE)),
            ),
        )
        val state = GameState(
            money = Money(100_000),
            storeState = StoreState.OPEN,
            pendingCustomers = 5,
            inventory = inventory,
            registers = listOf(RegisterState(0, assignedCashierId = null)),
            hiredEntityRegistry = HiredEntityRegistry(),
            staffSchedules = emptyList(),
        )
        val result = processor.process(state, delta = 0.0, currentHour = 10)
        assertEquals(5, result.pendingCustomers)
    }

    @Test
    fun `pending customers never go negative`() {
        val state = stateWithInventory(pendingCustomers = 1)
        val result = processor.process(state, delta = 0.0, currentHour = 10)
        assertTrue(result.pendingCustomers >= 0)
    }
}
