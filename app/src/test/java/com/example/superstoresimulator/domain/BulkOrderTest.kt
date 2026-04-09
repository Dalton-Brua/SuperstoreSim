package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.MoneyData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [GameEngine.placeBulkOrder].
 *
 * The bulk-order function accepts:
 *   [maxTotalQuantity]  — include an item only when its shelf + backroom stock ≤ this value
 *   [casePacksPerItem]  — number of case packs to order for each matching item
 *   [categoryFilter]    — optional category restriction (null = all categories)
 *
 * Volume discount tiers (applied to the entire order's base cost):
 *   < 20 total cases  →  0% discount
 *   ≥ 20 total cases  → 10% discount
 *   ≥ 50 total cases  → 15% discount
 *   ≥ 100 total cases → 25% discount
 *
 * Per-item tier gate:  an item is eligible only when
 *   itemMetadata.tier.unlockAmount <= state.currentTier.unlockAmount
 *
 * Test item catalogue (all defined inline, casePack × unitCost = casePackCost):
 *   Item 1 — GROCERY,  casePack=6, unitCost=500 ¢  → casePackCost=3_000 ¢, tier=TIER_1
 *   Item 2 — SNACKS,   casePack=4, unitCost=400 ¢  → casePackCost=1_600 ¢, tier=TIER_1
 *   Item 3 — DAIRY,    casePack=6, unitCost=600 ¢  → casePackCost=3_600 ¢, tier=TIER_2
 *
 * GameEngine.init gives every item shelfStock=10, backroomStock=10 (total=20) by default.
 *
 * Uses [FakeItemDao] in-memory instead of Mockito (byte-buddy/JaCoCo incompatibility).
 */
class BulkOrderTest {

    // ── in-memory fake ────────────────────────────────────────────────────────

    private class FakeItemDao(private val items: List<Item>) : ItemDao {
        override suspend fun getItemById(itemId: String): Item? =
            items.firstOrNull { it.id == itemId }
        override suspend fun getAllItems(): List<Item> = items
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

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Creates a test [Item].  [unitCostCents] is the per-unit cost; the selling price
     * is set to double that value (ensures a healthy margin without affecting bulk-order
     * logic, which uses unitCost for casePackCost).
     */
    private fun makeItem(
        id: Int,
        category: ItemCategory,
        casePack: Int,
        unitCostCents: Long,
        tier: String = "TIER_1",
    ): Item = Item(
        id = "item_${String.format("%03d", id)}",
        name = "Item $id",
        price = MoneyData(cents = unitCostCents * 2),
        description = "Test item $id",
        unitCost = MoneyData(cents = unitCostCents),
        category = category,
        casePack = casePack,
        tier = tier
    )

    private fun newEngine(items: List<Item>): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        return GameEngine(cache)
    }

    /** Injects a money balance directly into the engine state. */
    private fun setMoney(engine: GameEngine, cents: Long) {
        engine.state = engine.state.copy(money = Money(cents))
    }

    // ── Basic order (no discount) ─────────────────────────────────────────────

    @Test
    fun `placeBulkOrder increases backroom stock and deducts exact cost when no discount applies`() {
        // 2 items × 2 cases = 4 total cases  (<20 → 0% discount)
        // baseCost = casePackCost_1×2 + casePackCost_2×2
        //          = 3_000×2 + 1_600×2 = 6_000 + 3_200 = 9_200 ¢
        // money: 100_000 - 9_200 = 90_800
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
            makeItem(id = 2, category = ItemCategory.SNACKS,  casePack = 4, unitCostCents = 400),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 2, categoryFilter = null)

        val state = engine.currentState()
        assertEquals("Money after order (no discount)", Money(90_800L), state.money)
        // Item 1: 10 + 6×2 = 22 units in backroom
        assertEquals("Item 1 backroom after 2 case packs", 22, state.inventory[1]?.backroomStock)
        // Item 2: 10 + 4×2 = 18 units in backroom
        assertEquals("Item 2 backroom after 2 case packs", 18, state.inventory[2]?.backroomStock)
    }

    // ── Category filter ───────────────────────────────────────────────────────

    @Test
    fun `placeBulkOrder with category filter only orders items in that category`() {
        // GROCERY filter — Item 2 (SNACKS) must be skipped
        // 1 item × 2 cases = 2 total cases (<20 → no discount)
        // baseCost = 3_000×2 = 6_000 ¢;  money: 100_000 - 6_000 = 94_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
            makeItem(id = 2, category = ItemCategory.SNACKS,  casePack = 4, unitCostCents = 400),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 2, categoryFilter = ItemCategory.GROCERY)

        val state = engine.currentState()
        assertEquals("Money after GROCERY-only order", Money(94_000L), state.money)
        assertEquals("Item 1 (GROCERY) backroom increases by 2 case packs", 22, state.inventory[1]?.backroomStock)
        assertEquals("Item 2 (SNACKS) backroom must be unchanged", 10, state.inventory[2]?.backroomStock)
    }

    @Test
    fun `placeBulkOrder with null category filter orders all eligible items`() {
        // null → both items ordered (same arithmetic as basic-order test above)
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
            makeItem(id = 2, category = ItemCategory.SNACKS,  casePack = 4, unitCostCents = 400),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 2, categoryFilter = null)

        val state = engine.currentState()
        assertEquals("Both items ordered: money", Money(90_800L), state.money)
        assertEquals("Item 1 backroom increases", 22, state.inventory[1]?.backroomStock)
        assertEquals("Item 2 backroom increases", 18, state.inventory[2]?.backroomStock)
    }

    // ── Stock threshold filter ─────────────────────────────────────────────────

    @Test
    fun `placeBulkOrder excludes items whose combined stock exceeds the threshold`() {
        // Default total stock = 20; threshold = 15 → item 1 does NOT qualify (20 > 15)
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 15, casePacksPerItem = 1, categoryFilter = null)

        val state = engine.currentState()
        assertEquals("Money must be unchanged when no items qualify", Money(100_000L), state.money)
        assertEquals("Backroom must be unchanged when item is above threshold", 10, state.inventory[1]?.backroomStock)
    }

    @Test
    fun `placeBulkOrder includes an item whose combined stock equals exactly the threshold`() {
        // Default total stock = 20; threshold = 20 → qualifies (20 ≤ 20, not strictly <)
        // 1 item × 1 case = 1 total case (no discount)
        // baseCost = 3_000×1 = 3_000 ¢;  money: 100_000 - 3_000 = 97_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 1, categoryFilter = null)

        val state = engine.currentState()
        assertEquals("Money must decrease by exactly one case-pack cost", Money(97_000L), state.money)
        // 10 + 6×1 = 16
        assertEquals("Backroom must increase by one case pack", 16, state.inventory[1]?.backroomStock)
    }

    // ── Per-item tier gate ─────────────────────────────────────────────────────

    @Test
    fun `placeBulkOrder excludes items whose tier is above the current tier`() {
        // Item 1 is TIER_1 (qualifies); Item 3 is TIER_2 (excluded at TIER_1)
        // 1 item × 1 case = 1 total case (<20 → no discount)
        // baseCost = 3_000×1 = 3_000 ¢;  money: 100_000 - 3_000 = 97_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500, tier = "TIER_1"),
            makeItem(id = 3, category = ItemCategory.DAIRY,   casePack = 6, unitCostCents = 600, tier = "TIER_2"),
        )
        val engine = newEngine(items)  // currentTier defaults to TIER_1
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 1, categoryFilter = null)

        val state = engine.currentState()
        assertEquals("Money reflects only TIER_1 item", Money(97_000L), state.money)
        assertEquals("Item 1 (TIER_1) backroom increases", 16, state.inventory[1]?.backroomStock)
        assertEquals("Item 3 (TIER_2) backroom must be unchanged at TIER_1", 10, state.inventory[3]?.backroomStock)
    }

    @Test
    fun `placeBulkOrder includes TIER_2 items once the player has advanced to TIER_2`() {
        // Same item set as above, but currentTier is advanced to TIER_2 via state injection
        // Both items qualify; 2 items × 1 case = 2 total cases (<20 → no discount)
        // baseCost = 3_000×1 + 3_600×1 = 6_600 ¢;  money: 100_000 - 6_600 = 93_400
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500, tier = "TIER_1"),
            makeItem(id = 3, category = ItemCategory.DAIRY,   casePack = 6, unitCostCents = 600, tier = "TIER_2"),
        )
        val engine = newEngine(items)
        engine.state = engine.state.copy(currentTier = ItemUnlockTier.TIER_2)
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 1, categoryFilter = null)

        val state = engine.currentState()
        assertEquals("Both items ordered: money after order", Money(93_400L), state.money)
        assertEquals("Item 1 (TIER_1) backroom increases", 16, state.inventory[1]?.backroomStock)
        assertEquals("Item 3 (TIER_2) backroom increases at TIER_2", 16, state.inventory[3]?.backroomStock)
    }

    // ── Discount boundary: 0% → 10% at 20 cases ──────────────────────────────

    @Test
    fun `placeBulkOrder charges full base cost with no discount at 19 total cases`() {
        // 1 item × 19 cases = 19 total cases  (<20 → 0% discount)
        // baseCost = 3_000×19 = 57_000 ¢;  finalCost = 57_000 ¢
        // money: 1_000_000 - 57_000 = 943_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 19, categoryFilter = null)

        assertEquals("No discount at 19 cases: money", Money(943_000L), engine.currentState().money)
        // backroom: 10 + 6×19 = 124
        assertEquals("Item 1 backroom at 19 cases", 124, engine.currentState().inventory[1]?.backroomStock)
    }

    @Test
    fun `placeBulkOrder applies 10% discount at exactly 20 total cases`() {
        // 1 item × 20 cases = 20 total cases  (≥20 → 10% off)
        // baseCost = 3_000×20 = 60_000 ¢
        // finalCost = (60_000 × 0.90).toLong() = 54_000 ¢
        // money: 1_000_000 - 54_000 = 946_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 20, categoryFilter = null)

        assertEquals("10% discount at exactly 20 cases: money", Money(946_000L), engine.currentState().money)
        // backroom: 10 + 6×20 = 130
        assertEquals("Item 1 backroom at 20 cases", 130, engine.currentState().inventory[1]?.backroomStock)
    }

    // ── Discount boundary: 10% → 15% at 50 cases ─────────────────────────────

    @Test
    fun `placeBulkOrder applies 10% discount at 49 total cases`() {
        // 1 item × 49 cases = 49 total cases  (≥20 but <50 → 10% off)
        // baseCost = 3_000×49 = 147_000 ¢
        // finalCost = (147_000 × 0.90).toLong() = 132_300 ¢
        // money: 1_000_000 - 132_300 = 867_700
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 49, categoryFilter = null)

        assertEquals("10% discount at 49 cases: money", Money(867_700L), engine.currentState().money)
        // backroom: 10 + 6×49 = 304
        assertEquals("Item 1 backroom at 49 cases", 304, engine.currentState().inventory[1]?.backroomStock)
    }

    @Test
    fun `placeBulkOrder applies 15% discount at exactly 50 total cases`() {
        // 1 item × 50 cases = 50 total cases  (≥50 → 15% off)
        // baseCost = 3_000×50 = 150_000 ¢
        // finalCost = (150_000 × 0.85).toLong() = 127_500 ¢
        // money: 1_000_000 - 127_500 = 872_500
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 50, categoryFilter = null)

        assertEquals("15% discount at exactly 50 cases: money", Money(872_500L), engine.currentState().money)
        // backroom: 10 + 6×50 = 310
        assertEquals("Item 1 backroom at 50 cases", 310, engine.currentState().inventory[1]?.backroomStock)
    }

    // ── Discount boundary: 15% → 25% at 100 cases ────────────────────────────

    @Test
    fun `placeBulkOrder applies 15% discount at 99 total cases`() {
        // 1 item × 99 cases = 99 total cases  (≥50 but <100 → 15% off)
        // baseCost = 3_000×99 = 297_000 ¢
        // finalCost = (297_000 × 0.85).toLong() = 252_450 ¢
        // money: 1_000_000 - 252_450 = 747_550
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 99, categoryFilter = null)

        assertEquals("15% discount at 99 cases: money", Money(747_550L), engine.currentState().money)
        // backroom: 10 + 6×99 = 604
        assertEquals("Item 1 backroom at 99 cases", 604, engine.currentState().inventory[1]?.backroomStock)
    }

    @Test
    fun `placeBulkOrder applies 25% discount at exactly 100 total cases`() {
        // 1 item × 100 cases = 100 total cases  (≥100 → 25% off)
        // baseCost = 3_000×100 = 300_000 ¢
        // finalCost = (300_000 × 0.75).toLong() = 225_000 ¢
        // money: 1_000_000 - 225_000 = 775_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 100, categoryFilter = null)

        assertEquals("25% discount at exactly 100 cases: money", Money(775_000L), engine.currentState().money)
        // backroom: 10 + 6×100 = 610
        assertEquals("Item 1 backroom at 100 cases", 610, engine.currentState().inventory[1]?.backroomStock)
    }

    // ── Guard: insufficient funds ─────────────────────────────────────────────

    @Test
    fun `placeBulkOrder is a no-op when the player cannot afford the order`() {
        // 1 case of item 1 costs 3_000 ¢; player has 0 ¢
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 0L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 1, categoryFilter = null)

        assertEquals("Money must remain 0 when insufficient funds", Money(0L), engine.currentState().money)
        assertEquals("Backroom must be unchanged when order is rejected", 10, engine.currentState().inventory[1]?.backroomStock)
    }

    // ── Guard: zero cases per item ────────────────────────────────────────────

    @Test
    fun `placeBulkOrder with zero cases per item is a no-op`() {
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 0, categoryFilter = null)

        assertEquals("Money must be unchanged with 0 cases per item", Money(100_000L), engine.currentState().money)
        assertEquals("Backroom must be unchanged with 0 cases per item", 10, engine.currentState().inventory[1]?.backroomStock)
    }

    // ── Guard: no matching items ──────────────────────────────────────────────

    @Test
    fun `placeBulkOrder is a no-op when no items satisfy the stock threshold`() {
        // Default total stock = 20; threshold = 15 → no items qualify
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)

        engine.placeBulkOrder(maxTotalQuantity = 15, casePacksPerItem = 2, categoryFilter = null)

        assertEquals("Money must be unchanged when no items match threshold", Money(100_000L), engine.currentState().money)
        assertEquals("Backroom must be unchanged when no items match threshold", 10, engine.currentState().inventory[1]?.backroomStock)
    }

    // ── Metrics ───────────────────────────────────────────────────────────────

    @Test
    fun `placeBulkOrder increments itemsOrdered metric by the exact number of units added to backroom`() {
        // Item 1: casePack=6, 2 cases → 12 units;  Item 2: casePack=4, 2 cases → 8 units
        // Total units added = 20
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
            makeItem(id = 2, category = ItemCategory.SNACKS,  casePack = 4, unitCostCents = 400),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)

        val orderedBefore = engine.currentState().currentDayMetrics.itemsOrdered
        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 2, categoryFilter = null)
        val orderedAfter = engine.currentState().currentDayMetrics.itemsOrdered

        assertEquals(
            "itemsOrdered metric must increase by exactly 20 units (12 + 8)",
            orderedBefore + 20,
            orderedAfter
        )
    }
}

