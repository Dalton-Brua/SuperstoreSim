package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.inventory.InventoryState
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
 * Unit tests for [GameEngine.placeBulkOrder], [GameEngine.buyItemToBackroom],
 * and [GameEngine.buyItemCasePacks] with respect to the backroom cap.
 *
 * The bulk-order function accepts:
 *   [maxTotalQuantity]  — include an item only when its shelf + backroom stock ≤ this value
 *   [casePacksPerItem]  — number of case packs to order for each matching item
 *   [categoryFilter]    — optional category restriction (null = all categories)
 *
 * Volume discount tiers (applied to the actual cases delivered, post-cap clamping):
 *   < 20 total cases  →  0% discount
 *   ≥ 20 total cases  → 10% discount
 *   ≥ 50 total cases  → 15% discount
 *   ≥ 100 total cases → 25% discount
 *
 * Backroom cap (default 50 units/item via StoreConfig.backroomCapPerItem):
 *   - buyItemToBackroom:  refuses if a full case pack would exceed the cap
 *   - buyItemCasePacks:   clamps delivery to however many full case packs fit; charges accordingly
 *   - placeBulkOrder:     excludes fully-capped items; clamps per-item delivery;
 *                         discount is based on actual (clamped) total cases
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

    /** Helper to create a batch with the given quantity (non-perishable for testing). */
    private fun batch(qty: Int, day: Int = 1): List<com.example.superstoresimulator.domain.inventory.ItemBatch> =
        if (qty > 0) listOf(com.example.superstoresimulator.domain.inventory.ItemBatch(receivedDay = day, quantity = qty, expirationDay = Int.MAX_VALUE))
        else emptyList()

    /** Helper to create InventoryState from integer quantities. */
    private fun inv(shelfStock: Int, backroomStock: Int): InventoryState =
        InventoryState(shelfBatches = batch(shelfStock), backroomBatches = batch(backroomStock))

    private fun newEngine(items: List<Item>): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        val engine = GameEngine(cache)
        // Keep non-cap tests focused on pricing/filtering, not store-size cap limits.
        engine.state = engine.state.copy(
            storeConfig = engine.state.storeConfig.copy(backroomCapPerItem = 10_000)
        )
        return engine
    }

    /** Injects a money balance directly into the engine state. */
    private fun setMoney(engine: GameEngine, cents: Long) {
        engine.state = engine.state.copy(money = Money(cents))
    }

    /**
     * Overrides the backroom cap for tests that need to isolate discount-tier logic
     * from cap clamping (e.g. ordering 100 cases of a single item).
     */
    private fun setBackroomCap(engine: GameEngine, cap: Int) {
        engine.state = engine.state.copy(
            storeConfig = engine.state.storeConfig.copy(backroomCapPerItem = cap)
        )
    }

    /** With the truck system orders go to scheduledTrucks, not directly to backroom. */
    private fun scheduledQtyForItem(engine: GameEngine, itemId: Int): Int =
        engine.currentState().scheduledTrucks
            .flatMap { it.orders }
            .filter { it.itemId == itemId }
            .sumOf { it.quantity }

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
        // Item 1: 2 case packs × 6 items = 12 units scheduled for delivery
        assertEquals("Item 1 scheduled 12 units", 12, scheduledQtyForItem(engine, 1))
        // Item 2: 2 case packs × 4 items = 8 units scheduled for delivery
        assertEquals("Item 2 scheduled 8 units", 8, scheduledQtyForItem(engine, 2))
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
        assertEquals("Item 1 (GROCERY) scheduled 12 units", 12, scheduledQtyForItem(engine, 1))
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
        assertEquals("Item 1 scheduled 12 units", 12, scheduledQtyForItem(engine, 1))
        assertEquals("Item 2 scheduled 8 units", 8, scheduledQtyForItem(engine, 2))
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
        // 1 case pack × 6 items = 6 units scheduled for delivery
        assertEquals("6 units scheduled for delivery", 6, scheduledQtyForItem(engine, 1))
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
        assertEquals("Item 1 (TIER_1) scheduled 6 units", 6, scheduledQtyForItem(engine, 1))
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
        assertEquals("Item 1 (TIER_1) scheduled 6 units", 6, scheduledQtyForItem(engine, 1))
        assertEquals("Item 3 (TIER_2) scheduled 6 units", 6, scheduledQtyForItem(engine, 3))
    }

    // ── Discount boundary: 0% → 10% at 20 cases ──────────────────────────────

    @Test
    fun `placeBulkOrder charges full base cost with zero discount at 19 total cases`() {
        // 1 item × 19 cases = 19 total cases  (<20 → 0% discount)
        // baseCost = 3_000×19 = 57_000 ¢;  finalCost = 57_000 ¢
        // money: 1_000_000 - 57_000 = 943_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)
        // Use a high cap so the discount-tier logic can be tested in isolation
        setBackroomCap(engine, 10_000)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 19, categoryFilter = null)

        assertEquals("No discount at 19 cases: money", Money(943_000L), engine.currentState().money)
        // 19 case packs × 6 items = 114 units scheduled for delivery
        assertEquals("Item 1 scheduled 114 units", 114, scheduledQtyForItem(engine, 1))
    }

    @Test
    fun `placeBulkOrder applies 10pct discount at exactly 20 total cases`() {
        // 1 item × 20 cases = 20 total cases  (≥20 → 10% off)
        // baseCost = 3_000×20 = 60_000 ¢
        // finalCost = (60_000 × 0.90).toLong() = 54_000 ¢
        // money: 1_000_000 - 54_000 = 946_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)
        setBackroomCap(engine, 10_000)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 20, categoryFilter = null)

        assertEquals("10% discount at exactly 20 cases: money", Money(946_000L), engine.currentState().money)
        // 20 case packs × 6 items = 120 units scheduled
        assertEquals("Item 1 scheduled 120 units", 120, scheduledQtyForItem(engine, 1))
    }

    // ── Discount boundary: 10% → 15% at 50 cases ─────────────────────────────

    @Test
    fun `placeBulkOrder applies 10pct discount at 49 total cases`() {
        // 1 item × 49 cases = 49 total cases  (≥20 but <50 → 10% off)
        // baseCost = 3_000×49 = 147_000 ¢
        // finalCost = (147_000 × 0.90).toLong() = 132_300 ¢
        // money: 1_000_000 - 132_300 = 867_700
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)
        setBackroomCap(engine, 10_000)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 49, categoryFilter = null)

        assertEquals("10% discount at 49 cases: money", Money(867_700L), engine.currentState().money)
        // 49 case packs × 6 items = 294 units scheduled
        assertEquals("Item 1 scheduled 294 units", 294, scheduledQtyForItem(engine, 1))
    }

    @Test
    fun `placeBulkOrder applies 15pct discount at exactly 50 total cases`() {
        // 1 item × 50 cases = 50 total cases  (≥50 → 15% off)
        // baseCost = 3_000×50 = 150_000 ¢
        // finalCost = (150_000 × 0.85).toLong() = 127_500 ¢
        // money: 1_000_000 - 127_500 = 872_500
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)
        setBackroomCap(engine, 10_000)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 50, categoryFilter = null)

        assertEquals("15% discount at exactly 50 cases: money", Money(872_500L), engine.currentState().money)
        // 50 case packs × 6 items = 300 units scheduled
        assertEquals("Item 1 scheduled 300 units", 300, scheduledQtyForItem(engine, 1))
    }

    // ── Discount boundary: 15% → 25% at 100 cases ────────────────────────────

    @Test
    fun `placeBulkOrder applies 15pct discount at 99 total cases`() {
        // 1 item × 99 cases = 99 total cases  (≥50 but <100 → 15% off)
        // baseCost = 3_000×99 = 297_000 ¢
        // finalCost = (297_000 × 0.85).toLong() = 252_450 ¢
        // money: 1_000_000 - 252_450 = 747_550
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)
        setBackroomCap(engine, 10_000)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 99, categoryFilter = null)

        assertEquals("15% discount at 99 cases: money", Money(747_550L), engine.currentState().money)
        // 99 case packs × 6 items = 594 units scheduled
        assertEquals("Item 1 scheduled 594 units", 594, scheduledQtyForItem(engine, 1))
    }

    @Test
    fun `placeBulkOrder applies 25pct discount at exactly 100 total cases`() {
        // 1 item × 100 cases = 100 total cases  (≥100 → 25% off)
        // baseCost = 3_000×100 = 300_000 ¢
        // finalCost = (300_000 × 0.75).toLong() = 225_000 ¢
        // money: 1_000_000 - 225_000 = 775_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 1_000_000L)
        setBackroomCap(engine, 10_000)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 100, categoryFilter = null)

        assertEquals("25% discount at exactly 100 cases: money", Money(775_000L), engine.currentState().money)
        // 100 case packs × 6 items = 600 units scheduled
        assertEquals("Item 1 scheduled 600 units", 600, scheduledQtyForItem(engine, 1))
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

    // ── Backroom cap ───────────────────────────────────────────────────────────

    @Test
    fun `buyItemToBackroom is rejected when a full case pack would exceed the backroom cap`() {
        // Item 1: casePack=6, backroomStock starts at 10 (engine init).
        // Set cap = 14 → backroom (10) + casePack (6) = 16 > 14 → rejected.
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)
        setBackroomCap(engine, 1)

        engine.buyItemToBackroom(1)

        assertEquals("Backroom must be unchanged when cap prevents order", 10, engine.currentState().inventory[1]?.backroomStock)
        assertEquals("Money must be unchanged when cap prevents order", Money(100_000L), engine.currentState().money)
    }

    @Test
    fun `buyItemToBackroom succeeds when a full case pack exactly fits within the cap`() {
        // backroomStock=10, casePack=6, cap=16 → 10+6=16 ≤ 16 → allowed.
        // cost = 3_000 ¢;  money: 100_000 - 3_000 = 97_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)
        setBackroomCap(engine, 16)

        engine.buyItemToBackroom(1)

        assertEquals("One case pack (6 units) scheduled for delivery", 6, scheduledQtyForItem(engine, 1))
        assertEquals("Backroom unchanged — items are in transit", 10, engine.currentState().inventory[1]?.backroomStock)
        assertEquals("Money must decrease by one case pack cost", Money(97_000L), engine.currentState().money)
    }

    @Test
    fun `buyItemCasePacks splits across trucks when order exceeds per-truck cap`() {
        // backroomStock=10, casePack=6 → 1 case pack in backroom. cap=4.
        // totalCommitted=1, maxPreOrder=MAX_TRUCKS_AHEAD(2)×4=8, remainingAllowable=7.
        // Requested 10, but only 7 delivered (split as [4,3] across 2 trucks).
        // cost = 3_000×7 = 21_000 ¢; money: 100_000 - 21_000 = 79_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)
        setBackroomCap(engine, 4)

        engine.buyItemCasePacks(1, 10)

        assertEquals("7 case packs (42 units) scheduled for delivery", 42, scheduledQtyForItem(engine, 1))
        assertEquals("Backroom unchanged — items are in transit", 10, engine.currentState().inventory[1]?.backroomStock)
        assertEquals("Money must decrease by cost of 7 case packs", Money(79_000L), engine.currentState().money)
    }

    @Test
    fun `buyItemCasePacks pre-orders when backroom is at cap but transit is empty`() {
        // backroomStock=10, casePack=6, cap=1 → 1 case pack in backroom (= cap).
        // totalCommitted=1, maxPreOrder=MAX_TRUCKS_AHEAD(2)×1=2, remainingAllowable=1.
        // Can order 1 more case pack (goes on next truck).
        // cost = 3_000×1 = 3_000 ¢; money: 100_000 - 3_000 = 97_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)
        setBackroomCap(engine, 1)

        engine.buyItemCasePacks(1, 5)

        assertEquals("1 case pack (6 units) scheduled for delivery", 6, scheduledQtyForItem(engine, 1))
        assertEquals("Backroom unchanged — items are in transit", 10, engine.currentState().inventory[1]?.backroomStock)
        assertEquals("Money must decrease by 1 case pack cost", Money(97_000L), engine.currentState().money)
    }

    @Test
    fun `placeBulkOrder excludes items whose backroom is already full`() {
        // Item 1: backroom=10, casePack=6, cap=10 → no room → excluded entirely.
        // Item 2: backroom=10, casePack=4, cap=50 → room for 10 cases → included.
        // Order: 1 item × 2 cases (item 2 only), no discount.
        // cost = 1_600×2 = 3_200 ¢;  money: 100_000 - 3_200 = 96_800
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
            makeItem(id = 2, category = ItemCategory.SNACKS,  casePack = 4, unitCostCents = 400),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)
        // Set different caps per-item by manually pinning item 1's backroom to cap
        setBackroomCap(engine, 8)
        engine.state = engine.state.copy(
            inventory = engine.state.inventory + (1 to inv(10, 50))
        )

        engine.placeBulkOrder(maxTotalQuantity = 100, casePacksPerItem = 2, categoryFilter = null)

        assertEquals("Item 1 backroom must be unchanged (at cap)", 50, engine.currentState().inventory[1]?.backroomStock)
        assertEquals("Item 2: 2 case packs (8 units) scheduled for delivery", 8, scheduledQtyForItem(engine, 2))
        assertEquals("Item 2 backroom unchanged — delivery in transit", 10, engine.currentState().inventory[2]?.backroomStock)
        assertEquals("Money must reflect only item 2's order", Money(96_800L), engine.currentState().money)
    }

    @Test
    fun `placeBulkOrder clamps per-item case packs to available space and charges accordingly`() {
        // Item 1: backroom=10, casePack=6, cap=28, available=18 → floor(18/6)=3 cases fit.
        // Requested 5 cases, but only 3 delivered.
        // baseCost = 3_000×3 = 9_000 ¢;  totalCases=3 → 0% discount.
        // money: 100_000 - 9_000 = 91_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)
        setBackroomCap(engine, 4)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 5, categoryFilter = null)

        assertEquals("3 case packs (18 units) scheduled for delivery", 18, scheduledQtyForItem(engine, 1))
        assertEquals("Backroom unchanged — delivery in transit", 10, engine.currentState().inventory[1]?.backroomStock)
        assertEquals("Money must reflect only the 3 delivered case packs", Money(91_000L), engine.currentState().money)
    }

    @Test
    fun `placeBulkOrder discount tier is based on actual clamped cases not requested cases`() {
        // 4 items, each with backroom=10, casePack=6, cap=28 → 3 cases each fit.
        // Requested 20 cases each → actual 3 cases each → total=12 cases → 0% discount
        // (would be 25% discount if cap were absent and 80 total cases were delivered)
        // baseCost per item = 3_000×3 = 9_000 ¢;  total = 9_000×4 = 36_000 ¢ (no discount)
        // money: 100_000 - 36_000 = 64_000
        val items = listOf(
            makeItem(id = 1, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
            makeItem(id = 2, category = ItemCategory.SNACKS,  casePack = 6, unitCostCents = 500),
            makeItem(id = 3, category = ItemCategory.DRINKS,  casePack = 6, unitCostCents = 500),
            makeItem(id = 4, category = ItemCategory.GROCERY, casePack = 6, unitCostCents = 500),
        )
        val engine = newEngine(items)
        setMoney(engine, 100_000L)
        setBackroomCap(engine, 4)

        engine.placeBulkOrder(maxTotalQuantity = 20, casePacksPerItem = 20, categoryFilter = null)

        // Each item: 3 case packs (18 units) scheduled for delivery
        assertEquals("Item 1: 3 case packs (18 units) scheduled for delivery", 18, scheduledQtyForItem(engine, 1))
        assertEquals("Item 4: 3 case packs (18 units) scheduled for delivery", 18, scheduledQtyForItem(engine, 4))
        assertEquals("No discount applied — only 12 actual cases delivered (< 20 threshold)", Money(64_000L), engine.currentState().money)
    }
}
