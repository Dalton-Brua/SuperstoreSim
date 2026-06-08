package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.Transactions.TransactionLine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.staff.StaffManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StaffTickProcessorTest {

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

    private lateinit var processor: StaffTickProcessor
    private lateinit var staffManager: StaffManager
    private lateinit var cache: ItemMetadataCache

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        staffManager = StaffManager()
        val pricingManager = PricingManager(cache)
        val transactionEngine = TransactionEngine(cache = cache, pricingManager = pricingManager)
        val truckManager = TruckManager(cache)
        val inventoryManager = InventoryManager(cache, truckManager)
        val registerManager = RegisterManager()
        processor = StaffTickProcessor(
            staffManager, inventoryManager, transactionEngine, registerManager, pricingManager, cache,
        )
    }

    private fun hireRegistry(vararg defs: EntityDef): HiredEntityRegistry {
        var reg = HiredEntityRegistry()
        for (def in defs) {
            reg = reg.hireEntity(def)
        }
        return reg
    }

    @Test
    fun `no-op when no staff hired`() {
        val state = GameState(money = Money(5000))
        val result = processor.process(state, delta = 1.0, speedMultiplier = 1f, currentHour = 10)
        assertEquals(state, result)
    }

    @Test
    fun `cashier processes transaction items over time`() {
        val registry = hireRegistry(EntityDef.CASHIER)
        val cashierId = registry.hiredEntities.first().id
        val state = GameState(
            money = Money(50_000),
            hiredEntityRegistry = registry,
            staffSchedules = listOf(StaffShift(entityId = cashierId, startHour = 6)),
            registers = listOf(
                RegisterState(
                    registerId = 0,
                    assignedCashierId = cashierId,
                    transactionActive = true,
                    currentTransaction = Transaction(
                        id = 1,
                        lines = listOf(TransactionLine(itemId = 1, quantity = 3, rungQty = 0, unitPrice = Money(200), lineTotal = Money(600))),
                        subtotal = Money(600),
                        tax = Money(0),
                        totalEarned = Money(0),
                    ),
                )
            ),
            inventory = mapOf(
                1 to InventoryState(
                    shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 50, expirationDay = Int.MAX_VALUE)),
                ),
            ),
        )

        var s = state
        repeat(50) {
            s = processor.process(s, delta = 1.0, speedMultiplier = 1f, currentHour = 10)
        }
        val remaining = s.registers.first().currentTransaction.lines.sumOf { it.quantity }
        assertTrue("Cashier should ring up items over time", remaining < 3 || !s.registers.first().transactionActive)
    }

    @Test
    fun `stocker moves items from backroom to shelf`() {
        val registry = hireRegistry(EntityDef.STOCKER)
        val stockerId = registry.hiredEntities.first().id
        val state = GameState(
            money = Money(50_000),
            hiredEntityRegistry = registry,
            staffSchedules = listOf(StaffShift(entityId = stockerId, startHour = 6)),
            inventory = mapOf(
                1 to InventoryState(
                    backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 20, expirationDay = Int.MAX_VALUE)),
                ),
            ),
        )

        var s = state
        repeat(50) {
            s = processor.process(s, delta = 1.0, speedMultiplier = 1f, currentHour = 10)
        }
        val inv = s.inventory[1]!!
        assertTrue("Backroom stock should decrease", inv.backroomStock < 20)
        assertTrue("Shelf stock should increase", inv.shelfStock > 0)
    }

    @Test
    fun `stocker zones items when backroom is empty`() {
        val registry = hireRegistry(EntityDef.STOCKER)
        val stockerId = registry.hiredEntities.first().id
        val state = GameState(
            money = Money(50_000),
            hiredEntityRegistry = registry,
            staffSchedules = listOf(StaffShift(entityId = stockerId, startHour = 6)),
            inventory = mapOf(
                1 to InventoryState(
                    shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                    zoneScore = 0.0f,
                ),
            ),
        )

        var s = state
        repeat(50) {
            s = processor.process(s, delta = 1.0, speedMultiplier = 1f, currentHour = 10)
        }
        assertTrue("Zone score should improve", s.inventory[1]!!.zoneScore > 0.0f)
    }

    @Test
    fun `off-shift staff does no work`() {
        val registry = hireRegistry(EntityDef.STOCKER)
        val stockerId = registry.hiredEntities.first().id
        val state = GameState(
            money = Money(50_000),
            hiredEntityRegistry = registry,
            staffSchedules = listOf(StaffShift(entityId = stockerId, startHour = 13)),
            inventory = mapOf(
                1 to InventoryState(
                    backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 20, expirationDay = Int.MAX_VALUE)),
                ),
            ),
        )

        var s = state
        repeat(10) {
            s = processor.process(s, delta = 1.0, speedMultiplier = 1f, currentHour = 6)
        }
        assertEquals(20, s.inventory[1]!!.backroomStock)
    }

    @Test
    fun `manager XP granted when supervised actions occur`() {
        val registry = hireRegistry(EntityDef.MANAGER, EntityDef.STOCKER)
        val mgrId = registry.getByDef(EntityDef.MANAGER).first().id
        val stockerId = registry.getByDef(EntityDef.STOCKER).first().id
        val state = GameState(
            money = Money(50_000),
            hiredEntityRegistry = registry,
            staffSchedules = listOf(
                StaffShift(entityId = mgrId, startHour = 6),
                StaffShift(entityId = stockerId, startHour = 6),
            ),
            inventory = mapOf(
                1 to InventoryState(
                    backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 100, expirationDay = Int.MAX_VALUE)),
                ),
            ),
        )

        var s = state
        repeat(50) {
            s = processor.process(s, delta = 1.0, speedMultiplier = 1f, currentHour = 10)
        }
        val mgrAfter = s.hiredEntityRegistry.getById(mgrId)
        assertTrue("Manager should gain XP from supervised work", mgrAfter.xp > 0)
    }
}
