package com.example.superstoresimulator.domain.pricing

import com.example.superstoresimulator.domain.GameState
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
import kotlin.math.abs

class PricingManagerTest {

    private class FakeItemDao(private val items: List<Item>) : ItemDao {
        override suspend fun getAllItems(): List<Item> = items
        override suspend fun getItemById(itemId: String): Item? = items.firstOrNull { it.id == itemId }
        override suspend fun insertItem(item: Item) {}
        override suspend fun deleteItem(itemId: String) {}
        override suspend fun deleteAll() {}
        override suspend fun getItemName(itemId: String): String? = items.firstOrNull { it.id == itemId }?.name
        override suspend fun getAllItemsWithNames(): List<ItemWithName> = items.map { ItemWithName(it.id, it.name) }
        override suspend fun getItemsByIds(itemIds: List<String>): List<Item> = items.filter { it.id in itemIds }
        override suspend fun insertBatch(items: List<Item>) {}
        override suspend fun getItemCount(): Int = items.size
        override suspend fun getItemsByCategory(category: String): List<Item> = items.filter { it.category.name == category }
        override suspend fun searchItemsByName(searchTerm: String): List<Item> = items.filter { it.name.contains(searchTerm, ignoreCase = true) }
        override suspend fun getItemsForInventory(itemIds: List<String>): List<Item> = items.filter { it.id in itemIds }
    }

    private fun item(
        id: Int, name: String, priceCents: Long, costCents: Long,
        category: ItemCategory = ItemCategory.GROCERY,
        purchaseWeight: Float = 1.0f,
        soldByWeight: Boolean = false,
    ) = Item(
        id = "item_$id", name = name,
        price = MoneyData(cents = priceCents), description = name,
        unitCost = MoneyData(cents = costCents), category = category,
        casePack = 6, purchaseWeight = purchaseWeight, soldByWeight = soldByWeight,
    )

    private fun batch(qty: Int, expirationDay: Int = Int.MAX_VALUE, receivedDay: Int = 1) =
        ItemBatch(receivedDay = receivedDay, quantity = qty, expirationDay = expirationDay)

    private fun inv(shelfQty: Int, expirationDay: Int = Int.MAX_VALUE) = InventoryState(
        shelfBatches = if (shelfQty > 0) listOf(batch(shelfQty, expirationDay)) else emptyList(),
    )

    private val groceryItem = item(1, "Cereal", 500, 300)
    private val dairyItem = item(2, "Milk", 300, 150, ItemCategory.DAIRY)
    private val bananas = item(3, "Bananas", 69, 29, ItemCategory.PRODUCE, purchaseWeight = 10.0f, soldByWeight = true)
    private val chicken = item(4, "Chicken", 449, 299, ItemCategory.MEAT, purchaseWeight = 7.0f, soldByWeight = true)
    private val expensiveItem = item(5, "Ribeye", 1599, 1149, ItemCategory.MEAT)
    private val cheapItem = item(6, "Gum", 59, 30)
    private val largeItem = item(7, "TV", 19999, 14999, ItemCategory.ELECTRONICS)

    private lateinit var cache: ItemMetadataCache
    private lateinit var mgr: PricingManager

    private fun baseState(vararg inventoryPairs: Pair<Int, InventoryState>): GameState =
        GameState(
            money = Money(100_000),
            inventory = inventoryPairs.toMap(),
        )

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(listOf(
            groceryItem, dairyItem, bananas, chicken, expensiveItem, cheapItem, largeItem,
        )))
        runBlocking { cache.initialize() }
        mgr = PricingManager(cache)
    }

    // ── 1. Price Resolution ──────────────────────────────────────────────────

    @Test
    fun `no modifiers returns base price`() {
        val state = baseState()
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(500), resolved.effectivePrice)
        assertEquals(Money(500), resolved.basePrice)
        assertEquals(0, resolved.modifierPercent)
    }

    @Test
    fun `category markup only`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 10))
        )
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(550), resolved.effectivePrice)
    }

    @Test
    fun `item override only`() {
        val state = baseState().copy(
            pricingState = PricingState(itemOverrides = mapOf(1 to 20))
        )
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(600), resolved.effectivePrice)
    }

    @Test
    fun `markdown only`() {
        val state = baseState().copy(
            pricingState = PricingState(
                activeMarkdowns = mapOf(1 to Markdown(30, MarkdownReason.EXPIRING_SOON, 1))
            )
        )
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(350), resolved.effectivePrice)
    }

    @Test
    fun `all three stack multiplicatively`() {
        val state = baseState().copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.DAIRY to 10),
                itemOverrides = mapOf(2 to 20),
            )
        )
        // $3.00 * 1.10 * 1.20 = $3.96
        val resolved = mgr.resolvePrice(2, state)
        assertEquals(Money(396), resolved.effectivePrice)
    }

    @Test
    fun `markup plus markdown stacking`() {
        val state = baseState().copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.GROCERY to 10),
                activeMarkdowns = mapOf(1 to Markdown(30, MarkdownReason.PLAYER_SALE, 1)),
            )
        )
        // $5.00 * 1.10 * 0.70 = $3.85
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(385), resolved.effectivePrice)
    }

    @Test
    fun `negative category markup`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to -25))
        )
        // $5.00 * 0.75 = $3.75
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(375), resolved.effectivePrice)
    }

    @Test
    fun `max item override 200 percent`() {
        val state = baseState().copy(
            pricingState = PricingState(itemOverrides = mapOf(2 to 200))
        )
        // $3.00 * 3.0 = $9.00
        val resolved = mgr.resolvePrice(2, state)
        assertEquals(Money(900), resolved.effectivePrice)
    }

    @Test
    fun `max category markup 100 percent`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 100))
        )
        // $5.00 * 2.0 = $10.00
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(1000), resolved.effectivePrice)
    }

    // ── 2. Sell-Below-Cost Floor ─────────────────────────────────────────────

    @Test
    fun `floor clamps at unitCost`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to -50))
        )
        // $5.00 * 0.50 = $2.50 → clamped to unitCost $3.00
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(300), resolved.effectivePrice)
    }

    @Test
    fun `stacked modifiers hit floor`() {
        val state = baseState().copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.DAIRY to -30),
                itemOverrides = mapOf(2 to -50),
            )
        )
        // $3.00 * 0.70 * 0.50 = $1.05 → clamped to unitCost $1.50
        val resolved = mgr.resolvePrice(2, state)
        assertEquals(Money(150), resolved.effectivePrice)
    }

    @Test
    fun `markdown alone hits floor`() {
        val state = baseState().copy(
            pricingState = PricingState(
                activeMarkdowns = mapOf(2 to Markdown(60, MarkdownReason.PLAYER_SALE, 1)),
            )
        )
        // $3.00 * 0.40 = $1.20 → clamped to unitCost $1.50
        val resolved = mgr.resolvePrice(2, state)
        assertEquals(Money(150), resolved.effectivePrice)
    }

    @Test
    fun `exact cost is allowed`() {
        // item 1: price 500, cost 300 — no modifiers → $5.00 (above cost, no floor)
        val state = baseState()
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(500), resolved.effectivePrice)
    }

    @Test
    fun `markup above cost not affected by floor`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 10))
        )
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(550), resolved.effectivePrice)
    }

    // ── 3. Cents-Based Arithmetic Precision ──────────────────────────────────

    @Test
    fun `sub-dollar price with markup - bananas`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.PRODUCE to 10))
        )
        // $0.69 * 1.10 = $0.759 → rounds to $0.76
        val resolved = mgr.resolvePrice(3, state)
        assertEquals(Money(76), resolved.effectivePrice)
    }

    @Test
    fun `fractional cent rounds correctly`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 15))
        )
        // $0.59 * 1.15 = 67.85 → rounds to 68 cents
        val resolved = mgr.resolvePrice(6, state)
        assertEquals(Money(68), resolved.effectivePrice)
    }

    @Test
    fun `chained small percentages - single rounding`() {
        val state = baseState().copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.DAIRY to 3),
                itemOverrides = mapOf(2 to 7),
            )
        )
        // $3.00 * 1.03 * 1.07 = 3.3063 → rounds to 331 cents
        val resolved = mgr.resolvePrice(2, state)
        assertEquals(Money(331), resolved.effectivePrice)
    }

    @Test
    fun `large price no overflow`() {
        val state = baseState().copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.ELECTRONICS to 100),
                itemOverrides = mapOf(7 to 200),
            )
        )
        // $199.99 * 2.0 * 3.0 = $1199.94
        val resolved = mgr.resolvePrice(7, state)
        assertEquals(Money(119994), resolved.effectivePrice)
    }

    // ── 4. Purchase Probability Multiplier ───────────────────────────────────

    @Test
    fun `no markup gives multiplier near 1`() {
        val state = baseState()
        val mult = mgr.purchaseProbabilityMultiplier(1, state)
        assertTrue("Expected ~1.0, got $mult", abs(mult - 1.0f) < 0.01f)
    }

    @Test
    fun `markup reduces probability`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 20))
        )
        val mult = mgr.purchaseProbabilityMultiplier(1, state)
        // (5.0/6.0)^1.5 ≈ 0.76
        assertTrue("Expected <1.0, got $mult", mult < 1.0f)
        assertTrue("Expected ~0.76, got $mult", abs(mult - 0.76f) < 0.05f)
    }

    @Test
    fun `markdown increases probability`() {
        val state = baseState().copy(
            pricingState = PricingState(
                activeMarkdowns = mapOf(1 to Markdown(20, MarkdownReason.PLAYER_SALE, 1)),
            )
        )
        val mult = mgr.purchaseProbabilityMultiplier(1, state)
        // (5.0/4.0)^1.5 ≈ 1.40
        assertTrue("Expected >1.0, got $mult", mult > 1.0f)
    }

    @Test
    fun `large markup severely penalizes`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 100))
        )
        val mult = mgr.purchaseProbabilityMultiplier(1, state)
        // (5.0/10.0)^1.5 ≈ 0.354
        assertTrue("Expected <0.5, got $mult", mult < 0.5f)
    }

    // ── 5. Store Price Index ─────────────────────────────────────────────────

    @Test
    fun `all items at base gives index 1`() {
        val state = baseState(1 to inv(10), 2 to inv(10))
        val idx = mgr.computePriceIndex(state)
        assertTrue("Expected ~1.0, got $idx", abs(idx - 1.0f) < 0.01f)
    }

    @Test
    fun `uniform 10 percent markup gives index near 1_1`() {
        val state = baseState(1 to inv(10), 2 to inv(10)).copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.GROCERY to 10, ItemCategory.DAIRY to 10)
            )
        )
        val idx = mgr.computePriceIndex(state)
        assertTrue("Expected ~1.1, got $idx", abs(idx - 1.1f) < 0.02f)
    }

    @Test
    fun `mixed markups weighted by purchaseWeight`() {
        // Item 1: weight 1.0, +20%; Item 3 (bananas): weight 10.0, -50% but clamped to cost
        // Bananas: price 69, cost 29. With -50% produce → 69*0.5=35 cents (above cost 29, not clamped) → ratio 35/69 ≈ 0.507
        // Item 1: weight 1.0, +20% → ratio 600/500 = 1.20
        // Weighted avg = (1.0*1.20 + 10.0*0.507) / 11.0 ≈ 0.570
        val state = baseState(1 to inv(10), 3 to inv(10)).copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.GROCERY to 20, ItemCategory.PRODUCE to -50),
            )
        )
        val idx = mgr.computePriceIndex(state)
        assertTrue("Index should be pulled toward bananas' low ratio due to high weight, got $idx", idx < 0.8f)
    }

    @Test
    fun `items not on shelf excluded from index`() {
        val state = baseState(1 to inv(10), 2 to inv(0)).copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 10))
        )
        // Only item 1 on shelf, 10% markup → index ≈ 1.1
        val idx = mgr.computePriceIndex(state)
        assertTrue("Expected ~1.1, got $idx", abs(idx - 1.1f) < 0.02f)
    }

    @Test
    fun `empty shelf gives neutral index`() {
        val state = baseState()
        val idx = mgr.computePriceIndex(state)
        assertEquals(1.0f, idx, 0.001f)
    }

    @Test
    fun `EMA converges from neutral`() {
        var state = baseState(1 to inv(10)).copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.GROCERY to 30),
                smoothedPriceIndex = 1.0f,
            )
        )
        // Raw index = 1.30. After 1 EMA update: 0.3*1.3 + 0.7*1.0 = 1.09
        state = mgr.updateSmoothedPriceIndex(state)
        assertEquals(1.09f, state.pricingState.smoothedPriceIndex, 0.02f)

        // After 2nd update: 0.3*1.3 + 0.7*1.09 = 1.153
        state = mgr.updateSmoothedPriceIndex(state)
        assertEquals(1.153f, state.pricingState.smoothedPriceIndex, 0.02f)
    }

    @Test
    fun `EMA first load initializes to raw`() {
        // Fresh state with no history → smoothed should equal raw on first call
        val state = baseState(1 to inv(10))
        val updated = mgr.updateSmoothedPriceIndex(state)
        val raw = mgr.computePriceIndex(state)
        assertEquals(raw, updated.pricingState.smoothedPriceIndex, 0.001f)
    }

    // ── 6. Traffic Multiplier ────────────────────────────────────────────────

    @Test
    fun `neutral price index gives traffic multiplier 1`() {
        assertEquals(1.0f, mgr.computeTrafficMultiplier(1.0f), 0.001f)
    }

    @Test
    fun `cheap store boosts traffic`() {
        val mult = mgr.computeTrafficMultiplier(0.70f)
        assertTrue("Expected >1.0, got $mult", mult > 1.0f)
        assertTrue("Expected ~1.20, got $mult", abs(mult - 1.195f) < 0.05f)
    }

    @Test
    fun `expensive store reduces traffic`() {
        val mult = mgr.computeTrafficMultiplier(1.30f)
        assertTrue("Expected <1.0, got $mult", mult < 1.0f)
    }

    @Test
    fun `extreme cheap does not overflow`() {
        val mult = mgr.computeTrafficMultiplier(0.50f)
        assertTrue("Expected ~1.41, got $mult", mult > 1.0f && mult < 2.0f)
    }

    @Test
    fun `extreme expensive does not zero out traffic`() {
        val mult = mgr.computeTrafficMultiplier(2.0f)
        assertTrue("Expected positive, got $mult", mult > 0.0f)
        assertTrue("Expected ~0.71, got $mult", mult < 1.0f)
    }

    // ── 7. Basket Size Multiplier ────────────────────────────────────────────

    @Test
    fun `at or below 1 gives no reduction`() {
        assertEquals(1.0f, mgr.computeBasketMultiplier(1.0f), 0.001f)
    }

    @Test
    fun `discount does not increase basket`() {
        assertEquals(1.0f, mgr.computeBasketMultiplier(0.7f), 0.001f)
    }

    @Test
    fun `10 percent markup reduces basket to about 91 percent`() {
        val mult = mgr.computeBasketMultiplier(1.10f)
        assertTrue("Expected ~0.91, got $mult", abs(mult - 0.909f) < 0.02f)
    }

    @Test
    fun `30 percent markup reduces basket to about 77 percent`() {
        val mult = mgr.computeBasketMultiplier(1.30f)
        assertTrue("Expected ~0.77, got $mult", abs(mult - 0.769f) < 0.02f)
    }

    @Test
    fun `100 percent markup halves basket`() {
        val mult = mgr.computeBasketMultiplier(2.0f)
        assertTrue("Expected ~0.50, got $mult", abs(mult - 0.50f) < 0.02f)
    }

    // ── 8. Weighted Items ────────────────────────────────────────────────────

    @Test
    fun `produce weight in valid range`() {
        val random = kotlin.random.Random(42)
        repeat(100) {
            val w = mgr.randomWeight(ItemCategory.PRODUCE, random)
            assertTrue("Weight $w out of produce range", w >= 0.5f && w <= 3.0f)
        }
    }

    @Test
    fun `meat weight in valid range`() {
        val random = kotlin.random.Random(42)
        repeat(100) {
            val w = mgr.randomWeight(ItemCategory.MEAT, random)
            assertTrue("Weight $w out of meat range", w >= 0.75f && w <= 2.5f)
        }
    }

    @Test
    fun `non-produce non-meat returns 1`() {
        assertEquals(1.0f, mgr.randomWeight(ItemCategory.GROCERY), 0.001f)
    }

    // ── 9. Player Controls ───────────────────────────────────────────────────

    @Test
    fun `setCategoryMarkup persists`() {
        val state = mgr.setCategoryMarkup(baseState(), ItemCategory.GROCERY, 15)
        assertEquals(15, state.pricingState.categoryMarkups[ItemCategory.GROCERY])
    }

    @Test
    fun `setCategoryMarkup clamps high`() {
        val state = mgr.setCategoryMarkup(baseState(), ItemCategory.DAIRY, 150)
        assertEquals(100, state.pricingState.categoryMarkups[ItemCategory.DAIRY])
    }

    @Test
    fun `setCategoryMarkup clamps low`() {
        val state = mgr.setCategoryMarkup(baseState(), ItemCategory.DAIRY, -80)
        assertEquals(-50, state.pricingState.categoryMarkups[ItemCategory.DAIRY])
    }

    @Test
    fun `setItemOverride persists`() {
        val state = mgr.setItemOverride(baseState(), 42, -15)
        assertEquals(-15, state.pricingState.itemOverrides[42])
    }

    @Test
    fun `setItemOverride clamps high`() {
        val state = mgr.setItemOverride(baseState(), 42, 300)
        assertEquals(200, state.pricingState.itemOverrides[42])
    }

    @Test
    fun `clearItemMarkdown removes active`() {
        val state = baseState().copy(
            pricingState = PricingState(
                activeMarkdowns = mapOf(5 to Markdown(30, MarkdownReason.EXPIRING_SOON, 1))
            )
        )
        val cleared = mgr.clearMarkdown(state, 5)
        assertNull(cleared.pricingState.activeMarkdowns[5])
    }

    // ── 10. Fresh Handler Markdowns ──────────────────────────────────────────

    @Test
    fun `applyExpiryMarkdown creates markdown`() {
        val state = mgr.applyExpiryMarkdown(baseState(), 1, 4)
        val md = state.pricingState.activeMarkdowns[1]
        assertNotNull(md)
        assertEquals(30, md!!.percentOff)
        assertEquals(MarkdownReason.EXPIRING_SOON, md.reason)
        assertEquals(4, md.appliedOnDay)
    }

    @Test
    fun `applyExpiryMarkdown skips already marked down item`() {
        val initial = baseState().copy(
            pricingState = PricingState(
                activeMarkdowns = mapOf(1 to Markdown(30, MarkdownReason.EXPIRING_SOON, 3))
            )
        )
        val after = mgr.applyExpiryMarkdown(initial, 1, 4)
        // Should still have original markdown from day 3
        assertEquals(3, after.pricingState.activeMarkdowns[1]!!.appliedOnDay)
    }

    // ── 11. Price History ────────────────────────────────────────────────────

    @Test
    fun `setCategoryMarkup records history event`() {
        val state = mgr.setCategoryMarkup(baseState(), ItemCategory.GROCERY, 15)
        assertEquals(1, state.pricingState.priceHistory.size)
        val evt = state.pricingState.priceHistory[0]
        assertEquals(ItemCategory.GROCERY, evt.category)
        assertNull(evt.itemId)
        assertEquals(0, evt.oldPercent)
        assertEquals(15, evt.newPercent)
    }

    @Test
    fun `price history caps at 200`() {
        var state = baseState()
        repeat(250) { i ->
            state = mgr.setCategoryMarkup(state, ItemCategory.GROCERY, i % 100)
        }
        assertEquals(PricingManager.MAX_PRICE_HISTORY_SIZE, state.pricingState.priceHistory.size)
    }

    // ── 13. Edge Cases ───────────────────────────────────────────────────────

    @Test
    fun `item with no category markup entry uses zero`() {
        val state = baseState().copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.DAIRY to 50))
        )
        // Item 1 is GROCERY, no entry → uses 0%
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(500), resolved.effectivePrice)
    }

    @Test
    fun `item with no override entry uses zero`() {
        val state = baseState().copy(
            pricingState = PricingState(itemOverrides = mapOf(99 to 50))
        )
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(500), resolved.effectivePrice)
    }

    @Test
    fun `concurrent markup and markdown shows net effect`() {
        val state = baseState().copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.GROCERY to 20),
                activeMarkdowns = mapOf(1 to Markdown(30, MarkdownReason.EXPIRING_SOON, 1)),
            )
        )
        // $5.00 * 1.20 * 0.70 = $4.20
        val resolved = mgr.resolvePrice(1, state)
        assertEquals(Money(420), resolved.effectivePrice)
        // Net modifier should be negative (markdown > markup)
        assertTrue(resolved.modifierPercent < 0)
    }

    @Test
    fun `all items at cost floor reflects floor prices in index`() {
        val state = baseState(1 to inv(10), 2 to inv(10)).copy(
            pricingState = PricingState(
                categoryMarkups = mapOf(ItemCategory.GROCERY to -50, ItemCategory.DAIRY to -50),
                itemOverrides = mapOf(1 to -75, 2 to -75),
            )
        )
        // All items should be floored at unitCost
        val r1 = mgr.resolvePrice(1, state)
        val r2 = mgr.resolvePrice(2, state)
        assertEquals(Money(300), r1.effectivePrice) // unitCost of item 1
        assertEquals(Money(150), r2.effectivePrice) // unitCost of item 2

        val idx = mgr.computePriceIndex(state)
        assertTrue("Index should be below 1.0 at cost floor, got $idx", idx < 1.0f)
    }

    @Test
    fun `updateSmoothedPriceIndex also updates traffic and basket multipliers`() {
        val state = baseState(1 to inv(10)).copy(
            pricingState = PricingState(categoryMarkups = mapOf(ItemCategory.GROCERY to 30))
        )
        val updated = mgr.updateSmoothedPriceIndex(state)
        // Traffic should be below 1.0 (expensive store)
        assertTrue(updated.pricingState.priceTrafficMultiplier < 1.0f)
        // Basket should be below 1.0 (price index > 1.0)
        assertTrue(updated.pricingState.basketSizeMultiplier < 1.0f)
    }
}
