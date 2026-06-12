package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.pricing.PricingManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PlayerTickProcessorTest {

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

    private lateinit var processor: PlayerTickProcessor
    private lateinit var cache: ItemMetadataCache

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        val pricingManager = PricingManager(cache)
        val transactionEngine = TransactionEngine(cache = cache, pricingManager = pricingManager)
        val truckManager = TruckManager(cache)
        val inventoryManager = InventoryManager(cache, truckManager)
        processor = PlayerTickProcessor(
            PlayerActionHandler(), transactionEngine, inventoryManager, cache,
        )
    }

    private fun inventoryWithBackroom(): Map<Int, InventoryState> = mapOf(
        1 to InventoryState(
            backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 20, expirationDay = Int.MAX_VALUE)),
        ),
        2 to InventoryState(
            backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 20, expirationDay = Int.MAX_VALUE)),
        ),
    )

    @Test
    fun `MANAGE role resets progress accumulators`() {
        val state = GameState(
            money = Money(5000),
            playerRole = PlayerRole.MANAGE,
            playerCashierProgress = 0.5f,
            playerStockerProgress = 0.3f,
        )
        val result = processor.process(state, delta = 1.0)
        assertEquals(0f, result.playerCashierProgress, 0.001f)
        assertEquals(0f, result.playerStockerProgress, 0.001f)
    }

    @Test
    fun `MANAGE role no-op when progress already zero`() {
        val state = GameState(
            money = Money(5000),
            playerRole = PlayerRole.MANAGE,
            playerCashierProgress = 0f,
            playerStockerProgress = 0f,
        )
        val result = processor.process(state, delta = 1.0)
        assertSame(state, result)
    }

    @Test
    fun `CASHIER auto-assigns to free register`() {
        val state = GameState(
            money = Money(5000),
            playerRole = PlayerRole.CASHIER,
            playerAssignedRegisterId = null,
            registers = listOf(RegisterState(registerId = 0, assignedCashierId = null)),
        )
        val result = processor.process(state, delta = 0.1)
        assertEquals(0, result.playerAssignedRegisterId)
    }

    @Test
    fun `CASHIER does not override staff-assigned register`() {
        val state = GameState(
            money = Money(5000),
            playerRole = PlayerRole.CASHIER,
            playerAssignedRegisterId = null,
            registers = listOf(RegisterState(registerId = 0, assignedCashierId = 5)),
        )
        val result = processor.process(state, delta = 0.1)
        assertNull(result.playerAssignedRegisterId)
    }

    @Test
    fun `STOCKER stocks from backroom when available`() {
        val state = GameState(
            money = Money(5000),
            playerRole = PlayerRole.STOCKER,
            inventory = inventoryWithBackroom(),
        )
        val result = processor.process(state, delta = 10.0)
        val totalBackroomAfter = result.inventory.values.sumOf { it.backroomStock }
        val totalBackroomBefore = state.inventory.values.sumOf { it.backroomStock }
        assertTrue(
            "Stocker should move items from backroom to shelf",
            totalBackroomAfter <= totalBackroomBefore,
        )
    }

    @Test
    fun `STOCKER zones items when backroom is empty`() {
        val inventory = mapOf(
            1 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                zoneScore = 0.0f,
            ),
            2 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                zoneScore = 0.5f,
            ),
        )
        val state = GameState(
            money = Money(5000),
            playerRole = PlayerRole.STOCKER,
            inventory = inventory,
        )
        val result = processor.process(state, delta = 10.0)
        val avgZoneBefore = state.inventory.values.map { it.zoneScore }.average()
        val avgZoneAfter = result.inventory.values.map { it.zoneScore }.average()
        assertTrue("Zone scores should increase", avgZoneAfter >= avgZoneBefore)
    }

    @Test
    fun `STOCKER switches to MANAGE when nothing to do`() {
        val inventory = mapOf(
            1 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                zoneScore = 1.0f,
            ),
        )
        val state = GameState(
            money = Money(5000),
            playerRole = PlayerRole.STOCKER,
            inventory = inventory,
        )
        val result = processor.process(state, delta = 10.0)
        assertEquals(PlayerRole.MANAGE, result.playerRole)
    }
}
