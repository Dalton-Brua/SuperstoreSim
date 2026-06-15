package com.example.superstoresimulator.domain.pricing

import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.createTestGameEngine
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import com.example.superstoresimulator.domain.helpers.FakeItemDao

class PricingIntegrationTest {

    private val testItems = listOf(
        Item("item_1", "Cereal", MoneyData(500), "desc", MoneyData(300), ItemCategory.GROCERY, 6, 1.0f),
        Item("item_2", "Milk", MoneyData(300), "desc", MoneyData(150), ItemCategory.DAIRY, 6, 1.0f),
        Item("item_3", "Frozen Pizza", MoneyData(799), "desc", MoneyData(450), ItemCategory.FROZEN, 6, 1.0f),
        Item("item_4", "Steak", MoneyData(1599), "desc", MoneyData(1149), ItemCategory.MEAT, 6, 1.0f),
    )

    private lateinit var cache: ItemMetadataCache
    private lateinit var engine: GameEngine

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        engine = createTestGameEngine(cache)
    }

    private fun setMoney(amount: Long) {
        engine.state = engine.state.copy(money = Money(amount))
    }

    // ── Phase 5: Metrics Accumulation ────────────────────────────────────────

    @Test
    fun `markup extra revenue accumulates when items sell above base price`() {
        engine.state = engine.state.copy(
            inventory = mapOf(1 to InventoryState(
                shelfBatches = listOf(ItemBatch(1, 50, Int.MAX_VALUE))
            )),
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 20)),
        )
        setMoney(100_000)

        engine.startTransaction()
        val tx = engine.currentState().currentTransaction
        val line = tx.lines.firstOrNull { it.itemId == 1 }
        if (line != null) {
            repeat(line.quantity) { engine.ringUpItem(line.itemId) }
        }

        val acc = engine.currentState().currentDayMetrics
        if (line != null && !line.lostToOutOfStock) {
            assertTrue("Markup extra revenue should be positive", acc.markupExtraRevenue.cents > 0)
        }
    }

    @Test
    fun `markdown saved accumulates when marked-down items sell`() {
        engine.state = engine.state.copy(
            inventory = mapOf(1 to InventoryState(
                shelfBatches = listOf(ItemBatch(1, 50, 2))
            )),
            pricingState = PricingState(
                activeMarkdowns = mapOf(1 to Markdown(30, MarkdownReason.EXPIRING_SOON, 1))
            ),
        )
        setMoney(100_000)

        engine.startTransaction()
        val tx = engine.currentState().currentTransaction
        val line = tx.lines.firstOrNull { it.itemId == 1 }
        if (line != null) {
            repeat(line.quantity) { engine.ringUpItem(line.itemId) }
        }

        val acc = engine.currentState().currentDayMetrics
        if (line != null && !line.lostToOutOfStock) {
            assertTrue("Markdown saved should be positive", acc.markdownsSaved.cents > 0)
        }
    }

    @Test
    fun `no pricing modifiers yields zero pricing metrics`() {
        engine.state = engine.state.copy(
            inventory = mapOf(1 to InventoryState(
                shelfBatches = listOf(ItemBatch(1, 50, Int.MAX_VALUE))
            )),
        )
        setMoney(100_000)

        engine.startTransaction()
        val tx = engine.currentState().currentTransaction
        val line = tx.lines.firstOrNull { it.itemId == 1 }
        if (line != null) {
            repeat(line.quantity) { engine.ringUpItem(line.itemId) }
        }

        val acc = engine.currentState().currentDayMetrics
        assertEquals(Money.ZERO, acc.markupExtraRevenue)
        assertEquals(Money.ZERO, acc.markdownsSaved)
    }

    // ── Phase 5: Serialization of new DailyMetrics fields ────────────────────

    @Test
    fun `pricing metrics fields survive serialization round-trip`() {
        val state = GameState(money = Money(10_000)).copy(
            currentDayMetrics = com.example.superstoresimulator.domain.metrics.DailyMetrics(
                dayNumber = 5,
                markdownsSaved = Money(1500),
                markupExtraRevenue = Money(3200),
                itemsMarkedDown = 4,
            )
        )
        val json = com.example.superstoresimulator.domain.persistence.GameStateSerializer.serialize(state)
        val restored = com.example.superstoresimulator.domain.persistence.GameStateSerializer.deserialize(json)!!

        assertEquals(Money(1500), restored.currentDayMetrics.markdownsSaved)
        assertEquals(Money(3200), restored.currentDayMetrics.markupExtraRevenue)
        assertEquals(4, restored.currentDayMetrics.itemsMarkedDown)
    }

    @Test
    fun `old saves without pricing metrics fields deserialize with defaults`() {
        val state = GameState(money = Money(10_000))
        val json = com.example.superstoresimulator.domain.persistence.GameStateSerializer.serialize(state)
        val jsonObj = org.json.JSONObject(json)
        val accJson = jsonObj.getJSONObject("currentDayMetrics")
        accJson.remove("markdownsSaved")
        accJson.remove("markupExtraRevenue")
        accJson.remove("itemsMarkedDown")
        val restored = com.example.superstoresimulator.domain.persistence.GameStateSerializer.deserialize(jsonObj.toString())!!

        assertEquals(Money.ZERO, restored.currentDayMetrics.markdownsSaved)
        assertEquals(Money.ZERO, restored.currentDayMetrics.markupExtraRevenue)
        assertEquals(0, restored.currentDayMetrics.itemsMarkedDown)
    }
}