package com.example.superstoresimulator.domain.pricing

import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.items.ItemWithName
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
        engine = GameEngine(cache)
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

    // ── Phase 6: Tier Unlock Default Markup Inheritance ──────────────────────

    @Test
    fun `tier unlock does not add redundant categoryMarkup entries — defaultMarkup already applies globally`() {
        engine.state = engine.state.copy(
            currentTier = ItemUnlockTier.TIER_1,
            totalRevenue = Money(600_000),
            money = Money(200_000),
            pricingState = PricingState(defaultMarkup = 10),
        )

        engine.unlockNextTier()

        assertEquals(ItemUnlockTier.TIER_2, engine.currentState().currentTier)
        val markups = engine.currentState().pricingState.categoryMarkups
        // defaultMarkup applies to ALL items via resolvePrice baseMarkup — no per-category entry needed
        assertFalse(markups.containsKey(ItemCategory.DAIRY))
    }

    @Test
    fun `tier unlock does not overwrite existing category markup`() {
        engine.state = engine.state.copy(
            currentTier = ItemUnlockTier.TIER_1,
            totalRevenue = Money(600_000),
            money = Money(200_000),
            pricingState = PricingState(
                defaultMarkup = 10,
                categoryMarkups = mapOf(ItemCategory.DAIRY to 25),
            ),
        )

        engine.unlockNextTier()

        val markups = engine.currentState().pricingState.categoryMarkups
        assertEquals(25, markups[ItemCategory.DAIRY])
    }

    @Test
    fun `tier unlock with zero defaultMarkup does not add entries`() {
        engine.state = engine.state.copy(
            currentTier = ItemUnlockTier.TIER_1,
            totalRevenue = Money(600_000),
            money = Money(200_000),
            pricingState = PricingState(defaultMarkup = 0),
        )

        engine.unlockNextTier()

        val markups = engine.currentState().pricingState.categoryMarkups
        assertFalse(markups.containsKey(ItemCategory.DAIRY))
    }

    @Test
    fun `tier 3 unlock does not add redundant categoryMarkup entries for new categories`() {
        engine.state = engine.state.copy(
            currentTier = ItemUnlockTier.TIER_2,
            totalRevenue = Money(2_500_000),
            money = Money(500_000),
            pricingState = PricingState(defaultMarkup = 15),
        )

        engine.unlockNextTier()

        assertEquals(ItemUnlockTier.TIER_3, engine.currentState().currentTier)
        val markups = engine.currentState().pricingState.categoryMarkups
        // defaultMarkup applies globally — no per-category duplication
        assertFalse(markups.containsKey(ItemCategory.FROZEN))
        assertFalse(markups.containsKey(ItemCategory.BAKERY))
        assertFalse(markups.containsKey(ItemCategory.PRODUCE))
    }

    // ── Phase 5: Serialization of new DailyMetrics fields ────────────────────

    @Test
    fun `pricing metrics fields survive serialization round-trip`() {
        val state = GameState(money = Money(10_000)).copy(
            currentDayMetrics = com.example.superstoresimulator.domain.metrics.DailyMetricsAccumulator(
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