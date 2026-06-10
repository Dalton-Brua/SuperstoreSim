package com.example.superstoresimulator.domain.inventory

import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.createTestGameEngine
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.store.StoreConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.superstoresimulator.domain.helpers.FakeItemDao

/**
 * Unit tests for [InventoryManager].
 *
 * [InventoryManager] is a pure sub-system: every method receives a [GameState]
 * and returns a new [GameState]. Tests build [GameState] with explicit inventory
 * maps — no [GameEngine] is needed.
 *
 * Test-item catalogue (all casePack = 6 unless stated):
 *   Item 1 — GROCERY, casePack 6, unitCost 500 ¢, tier TIER_1
 *   Item 2 — SNACKS,  casePack 4, unitCost 400 ¢, tier TIER_1
 *   Item 3 — DAIRY,   casePack 6, unitCost 600 ¢, tier TIER_2
 *
 * Default StoreConfig.backroomCapPerItem = 50 units.
 *
 * Covers:
 *  [stockItemFromBackroom]
 *   - Moves one unit shelf +1, backroom -1
 *   - Increments itemsStocked metric by 1
 *   - No-op when backroom is empty
 *   - No-op when itemId not in inventory
 *
 *  [stockRandomItemFromBackroom]
 *   - No-op when all backrooms empty
 *   - Picks the item with the lowest shelf stock
 *   - Stocks a full case-pack (not just 1 unit)
 *   - Increments itemsStocked by casePack amount
 *
 *  [buyItemToBackroom]
 *   - Adds casePack units to backroom, deducts casePackCost from money
 *   - Increments itemsOrdered metric
 *   - No-op when adding a full case-pack would exceed the backroom cap
 *   - No-op when money is insufficient
 *
 *  [buyItemCasePacks]
 *   - Adds correct units for the requested number of case-packs
 *   - Deducts the correct total cost
 *   - Clamps delivery to what fits within the remaining backroom space
 *   - No-op when numCasePacks ≤ 0
 *   - No-op when backroom is full (zero available space)
 *   - No-op when money is insufficient
 *
 *  [placeBulkOrder]
 *   - No-op when casePacksPerItem ≤ 0
 *   - Orders all qualifying items below the stock threshold
 *   - Skips items whose combined stock exceeds maxTotalQuantity
 *   - Skips tier-gated items above the player's current tier
 *   - Applies category filter
 *   - Applies 10% discount when ≥ 20 case-packs are ordered
 *   - No-op when the player cannot afford the discounted total
 */
class InventoryManagerTest {

    // ── FakeItemDao ───────────────────────────────────────────────────────────

    // ── Catalogue ─────────────────────────────────────────────────────────────

    private val item1 = Item(
        id = "item_001", name = "Item 1",
        price = MoneyData(1_000), unitCost = MoneyData(500),
        description = "", category = ItemCategory.GROCERY,
        casePack = 6, tier = "TIER_1",
    )
    private val item2 = Item(
        id = "item_002", name = "Item 2",
        price = MoneyData(800), unitCost = MoneyData(400),
        description = "", category = ItemCategory.SNACKS,
        casePack = 4, tier = "TIER_1",
    )
    private val item3 = Item(
        id = "item_003", name = "Item 3",
        price = MoneyData(1_200), unitCost = MoneyData(600),
        description = "", category = ItemCategory.DAIRY,
        casePack = 6, tier = "TIER_2",
    )

    // Item 1 case-pack cost = 6 × 500 = 3_000 ¢
    private val item1CasePackCost = Money(3_000)
    // Item 2 case-pack cost = 4 × 400 = 1_600 ¢
    private val item2CasePackCost = Money(1_600)

    // Grouped test items for engine integration tests
    private val testItems = listOf(item1, item2, item3)

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private lateinit var manager: InventoryManager

    @Before
    fun setUp() {
        val cache = ItemMetadataCache(FakeItemDao(listOf(item1, item2, item3)))
        runBlocking { cache.initialize() }
        manager = InventoryManager(cache)
    }

    /** Helper to create a batch with the given quantity (non-perishable for testing). */
    private fun batch(qty: Int, day: Int = 1): List<ItemBatch> =
        if (qty > 0) listOf(ItemBatch(receivedDay = day, quantity = qty, expirationDay = Int.MAX_VALUE))
        else emptyList()

    /** Helper to create InventoryState from integer quantities. */
    private fun inv(shelfStock: Int, backroomStock: Int): InventoryState =
        InventoryState(shelfBatches = batch(shelfStock), backroomBatches = batch(backroomStock))

    /** Builds a [GameState] with explicit per-item [InventoryState] values. */
    private fun stateWith(
        vararg items: Pair<Int, InventoryState>,
        money: Money = Money(100_000L),
        currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1,
        backroomCap: Int = 50,
    ): GameState = GameState(
        inventory = mapOf(*items),
        money = money,
        currentTier = currentTier,
        storeConfig = StoreConfig(backroomCapPerItem = backroomCap),
    )

    // ── stockItemFromBackroom ─────────────────────────────────────────────────

    @Test
    fun stockItemFromBackroomMovesOneUnitFromShelfToBackroom() {
        val state = stateWith(1 to inv(5, 8))
        val result = manager.stockItemFromBackroom(state, itemId = 1)
        assertEquals(6, result.inventory[1]!!.shelfStock)
        assertEquals(7, result.inventory[1]!!.backroomStock)
    }

    @Test
    fun `stockItemFromBackroom increments itemsStocked metric by 1`() {
        val state = stateWith(1 to inv(0, 5))
        val result = manager.stockItemFromBackroom(state, itemId = 1)
        assertEquals(1, result.currentDayMetrics.itemsStocked)
    }

    @Test
    fun `stockItemFromBackroom is no-op when backroom is empty`() {
        val state = stateWith(1 to inv(10, 0))
        val result = manager.stockItemFromBackroom(state, itemId = 1)
        assertEquals(state, result)
    }

    @Test
    fun `stockItemFromBackroom is no-op when itemId not in inventory`() {
        val state = stateWith(1 to inv(5, 5))
        val result = manager.stockItemFromBackroom(state, itemId = 99)
        assertEquals(state, result)
    }

    // ── stockRandomItemFromBackroom ───────────────────────────────────────────

    @Test
    fun `stockRandomItemFromBackroom is no-op when all backrooms are empty`() {
        val state = stateWith(
            1 to inv(10, 0),
            2 to inv(5, 0),
        )
        val result = manager.stockRandomItemFromBackroom(state)
        assertEquals(state, result)
    }

    @Test
    fun `stockRandomItemFromBackroom picks the item with the lowest shelf stock`() {
        // Item 1 has lower shelf stock → must be chosen
        val state = stateWith(
            1 to inv(2, 10),
            2 to inv(10, 10),
        )
        val result = manager.stockRandomItemFromBackroom(state)
        // Item 1 shelf should increase; item 2 shelf should be unchanged
        assertTrue(result.inventory[1]!!.shelfStock > state.inventory[1]!!.shelfStock)
        assertEquals(state.inventory[2]!!.shelfStock, result.inventory[2]!!.shelfStock)
    }

    @Test
    fun `stockRandomItemFromBackroom stocks a full case-pack not just one unit`() {
        // Item 1 casePack = 6; backroom has 10 → should move 6 to shelf
        val state = stateWith(1 to inv(0, 10))
        val result = manager.stockRandomItemFromBackroom(state)
        assertEquals(6, result.inventory[1]!!.shelfStock)
        assertEquals(4, result.inventory[1]!!.backroomStock)
    }

    @Test
    fun `stockRandomItemFromBackroom increments itemsStocked by casePack amount`() {
        val state = stateWith(1 to inv(0, 10))
        val result = manager.stockRandomItemFromBackroom(state)
        assertEquals(6, result.currentDayMetrics.itemsStocked) // casePack = 6
    }

    // ── buyItemToBackroom ─────────────────────────────────────────────────────
    // NOTE: buyItemToBackroom now returns BuyResult (money deducted, orderLines produced).
    // Items are no longer added directly to backroom — check orderLines instead.

    @Test
    fun `buyItemToBackroom produces one order line and deducts cost`() {
        val state = stateWith(1 to inv(0, 0))
        val result = manager.buyItemToBackroom(state, itemId = 1)
        assertEquals(1, result.orderLines.size)
        assertEquals(6, result.orderLines[0].quantity) // casePack = 6
        assertEquals(1, result.orderLines[0].casePacksCount)
        assertEquals(state.money - item1CasePackCost, result.state.money)
    }

    @Test
    fun `buyItemToBackroom increments itemsOrdered metric`() {
        val state = stateWith(1 to inv(0, 0))
        val result = manager.buyItemToBackroom(state, itemId = 1)
        assertEquals(6, result.state.currentDayMetrics.itemsOrdered)
    }

    @Test
    fun `buyItemToBackroom is no-op when adding a case-pack would exceed cap`() {
        // Cap is measured in case-packs. backroom=12 with casePack=6 => 2 case-packs already.
        // With cap=2, adding one more case-pack is rejected.
        val state = stateWith(
            1 to inv(0, 12),
            backroomCap = 2,
        )
        val result = manager.buyItemToBackroom(state, itemId = 1)
        assertTrue(result.orderLines.isEmpty())
        assertEquals(state, result.state)
    }

    @Test
    fun `buyItemToBackroom is no-op when money is insufficient`() {
        // item1CasePackCost = 3_000 ¢; give player only 2_999 ¢
        val state = stateWith(
            1 to inv(0, 0),
            money = Money(2_999L),
        )
        val result = manager.buyItemToBackroom(state, itemId = 1)
        assertTrue(result.orderLines.isEmpty())
        assertEquals(state, result.state)
    }

    // ── buyItemCasePacks ──────────────────────────────────────────────────────
    // NOTE: buyItemCasePacks now returns BuyResult (money deducted, orderLines produced).

    @Test
    fun `buyItemCasePacks produces order line with correct units and deducts cost`() {
        // 2 case-packs × casePack 6 = 12 units
        val state = stateWith(1 to inv(0, 0))
        val result = manager.buyItemCasePacks(state, itemId = 1, numCasePacks = 2)
        assertEquals(1, result.orderLines.size)
        assertEquals(12, result.orderLines[0].quantity)
        assertEquals(2, result.orderLines[0].casePacksCount)
        assertEquals(state.money - item1CasePackCost * 2, result.state.money)
    }

    @Test
    fun `buyItemCasePacks splits across trucks when order exceeds per-truck cap`() {
        // cap=2, backroom 10 units (=1 case-pack of 6), transit=0, request 3.
        // totalCommitted=1, maxPreOrder=MAX_TRUCKS_AHEAD(2)×2=4, remainingAllowable=3 → order all 3.
        // Split: [cap=2, 1] → 2 lines for 2 separate trucks.
        val state = stateWith(
            1 to inv(0, 10),
            backroomCap = 2,
        )
        val result = manager.buyItemCasePacks(state, itemId = 1, numCasePacks = 3)
        assertEquals(2, result.orderLines.size)  // split into 2 lines for 2 trucks
        assertEquals(3, result.orderLines.sumOf { it.casePacksCount })  // 3 total case packs
        assertEquals(state.money - item1CasePackCost * 3, result.state.money)
    }

    @Test
    fun `buyItemCasePacks is no-op when numCasePacks is zero`() {
        val state = stateWith(1 to inv(0, 0))
        val result = manager.buyItemCasePacks(state, itemId = 1, numCasePacks = 0)
        assertTrue(result.orderLines.isEmpty())
        assertEquals(state, result.state)
    }

    @Test
    fun `buyItemCasePacks can pre-order when backroom is full if truck slots available`() {
        // backroom at cap (12 units = 2 case packs, cap=2) but transit empty.
        // Multi-truck: totalCommitted=2, maxPreOrder=4, remaining=2 → order 1 succeeds.
        val state = stateWith(
            1 to inv(0, 12),
            backroomCap = 2,
        )
        val result = manager.buyItemCasePacks(state, itemId = 1, numCasePacks = 1)
        assertEquals(1, result.orderLines.size)
        assertEquals(1, result.orderLines[0].casePacksCount)
        assertEquals(state.money - item1CasePackCost, result.state.money)
    }

    @Test
    fun `buyItemCasePacks is no-op when money is insufficient`() {
        // 3 case-packs cost 9_000 ¢; give player only 8_999 ¢
        val state = stateWith(
            1 to inv(0, 0),
            money = Money(8_999L),
        )
        val result = manager.buyItemCasePacks(state, itemId = 1, numCasePacks = 3)
        assertTrue(result.orderLines.isEmpty())
        assertEquals(state, result.state)
    }

    // ── placeBulkOrder ────────────────────────────────────────────────────────
    // NOTE: placeBulkOrder now returns BuyResult. Items are scheduled for delivery,
    // not added directly to backroom. Check orderLines instead of inventory changes.

    @Test
    fun `placeBulkOrder is no-op when casePacksPerItem is zero`() {
        val state = stateWith(1 to inv(0, 0))
        val result = manager.placeBulkOrder(state, maxTotalQuantity = 100, casePacksPerItem = 0, categoryFilter = null)
        assertTrue(result.orderLines.isEmpty())
        assertEquals(state, result.state)
    }

    @Test
    fun `placeBulkOrder orders all items below the stock threshold`() {
        // Both items have total stock = 0; threshold = 100 → both qualify
        val state = stateWith(
            1 to inv(0, 0),
            2 to inv(0, 0),
        )
        val result = manager.placeBulkOrder(state, maxTotalQuantity = 100, casePacksPerItem = 1, categoryFilter = null)
        assertTrue(result.orderLines.any { it.itemId == 1 })
        assertTrue(result.orderLines.any { it.itemId == 2 })
    }

    @Test
    fun `placeBulkOrder skips items whose combined stock exceeds maxTotalQuantity`() {
        // Item 1 total = 20 (above threshold = 15); item 2 total = 0 (below)
        val state = stateWith(
            1 to inv(10, 10),
            2 to inv(0, 0),
        )
        val result = manager.placeBulkOrder(state, maxTotalQuantity = 15, casePacksPerItem = 1, categoryFilter = null)
        // Item 1 skipped; item 2 ordered
        assertTrue(result.orderLines.none { it.itemId == 1 })
        assertTrue(result.orderLines.any { it.itemId == 2 })
    }

    @Test
    fun `placeBulkOrder skips tier-gated items above the current tier`() {
        // Item 3 requires TIER_2; player is at TIER_1
        val state = stateWith(
            1 to inv(0, 0),
            3 to inv(0, 0),
            currentTier = ItemUnlockTier.TIER_1,
        )
        val result = manager.placeBulkOrder(state, maxTotalQuantity = 100, casePacksPerItem = 1, categoryFilter = null)
        assertTrue(result.orderLines.none { it.itemId == 3 })   // tier-blocked
        assertTrue(result.orderLines.any { it.itemId == 1 })    // allowed
    }

    @Test
    fun `placeBulkOrder applies category filter`() {
        val state = stateWith(
            1 to inv(0, 0), // GROCERY
            2 to inv(0, 0), // SNACKS
        )
        // Only GROCERY should be ordered
        val result = manager.placeBulkOrder(
            state, maxTotalQuantity = 100, casePacksPerItem = 1, categoryFilter = ItemCategory.GROCERY,
        )
        assertTrue(result.orderLines.any { it.itemId == 1 })          // GROCERY — ordered
        assertTrue(result.orderLines.none { it.itemId == 2 })         // SNACKS — filtered out
    }

    @Test
    fun `placeBulkOrder applies 10 percent discount when 20 or more case-packs are ordered`() {
        // Build 20 TIER_1 GROCERY items (id 1..20, each casePack=6, unitCost=500 ¢)
        val manyItems = (1..20).map { id ->
            Item(
                id = "item_${String.format("%03d", id)}", name = "Item $id",
                price = MoneyData(1_000), unitCost = MoneyData(500),
                description = "", category = ItemCategory.GROCERY,
                casePack = 6, tier = "TIER_1",
            )
        }
        val cache = ItemMetadataCache(FakeItemDao(manyItems))
        runBlocking { cache.initialize() }
        val mgr = InventoryManager(cache)

        val inventory = (1..20).associate { id -> id to inv(0, 0) }
        val startMoney = Money(500_000L)
        val state = GameState(inventory = inventory, money = startMoney, currentTier = ItemUnlockTier.TIER_1)

        // 20 items × 1 case-pack each = 20 total cases → 10% discount
        val result = mgr.placeBulkOrder(state, maxTotalQuantity = 100, casePacksPerItem = 1, categoryFilter = null)

        val baseCost = Money(3_000L * 20)            // 20 × 3_000 ¢
        val discountedCost = Money((baseCost.cents * 0.9).toLong())
        assertEquals(startMoney - discountedCost, result.state.money)
    }

    @Test
    fun `placeBulkOrder is no-op when player cannot afford the discounted total`() {
        val state = stateWith(
            1 to inv(0, 0),
            money = Money(1L),   // essentially broke
        )
        val result = manager.placeBulkOrder(state, maxTotalQuantity = 100, casePacksPerItem = 1, categoryFilter = null)
        assertTrue(result.orderLines.isEmpty())
        assertEquals(state, result.state)
    }

    // ── GameEngine integration tests ──────────────────────────────────────────

    /**
     * Tests that verify InventoryManager behavior through the GameEngine's public API.
     * These complement the pure-manager tests above by validating the end-to-end flow.
     */

    private fun newGameEngineWithItems(): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        val engine = createTestGameEngine(cache)
        engine.state = engine.state.copy(
            storeConfig = engine.state.storeConfig.copy(backroomCapPerItem = 100)
        )
        return engine
    }

    private fun setEngineMoneyTo(engine: GameEngine, cents: Long) {
        engine.state = engine.state.copy(money = Money(cents))
    }

    @Test
    fun `stockItemFromBackroom moves unit shelf+1 backroom-1 (via GameEngine)`() {
        val engine = newGameEngineWithItems()
        val initialShelf = engine.currentState().inventory[1]?.shelfStock ?: 0
        val initialBackroom = engine.currentState().inventory[1]?.backroomStock ?: 0

        engine.stockItemFromBackroom(1)

        val finalShelf = engine.currentState().inventory[1]?.shelfStock ?: 0
        val finalBackroom = engine.currentState().inventory[1]?.backroomStock ?: 0
        assertEquals("Shelf should increase by 1", initialShelf + 1, finalShelf)
        assertEquals("Backroom should decrease by 1", initialBackroom - 1, finalBackroom)
    }

    @Test
    fun `stockItemFromBackroom is no-op when backroom empty (via GameEngine)`() {
        val engine = newGameEngineWithItems()
        // Deplete backroom
        repeat(50) { engine.stockItemFromBackroom(1) }
        val stateAfterDepletion = engine.currentState()
        val shelfAfter = stateAfterDepletion.inventory[1]?.shelfStock ?: 0
        val backroomAfter = stateAfterDepletion.inventory[1]?.backroomStock ?: 0

        // Another stock should be no-op
        engine.stockItemFromBackroom(1)

        val finalState = engine.currentState()
        assertEquals("Shelf should not change", shelfAfter, finalState.inventory[1]?.shelfStock)
        assertEquals("Backroom should not change", backroomAfter, finalState.inventory[1]?.backroomStock)
    }

    @Test
    fun `buyItemToBackroom adds casePack and deducts money (via GameEngine)`() {
        val engine = newGameEngineWithItems()
        setEngineMoneyTo(engine, 10_000L)
        val initialMoney = engine.currentState().money

        engine.buyItemToBackroom(1)

        // With truck system: money deducted immediately, item scheduled for delivery
        assertTrue("Money should decrease", engine.currentState().money < initialMoney)
        assertTrue("Order should be scheduled", engine.currentState().scheduledTrucks.isNotEmpty())
    }

    @Test
    fun `buyItemToBackroom is no-op when insufficient funds (via GameEngine)`() {
        val engine = newGameEngineWithItems()
        setEngineMoneyTo(engine, 0L)
        val initialMoney = engine.currentState().money

        engine.buyItemToBackroom(1)

        val finalMoney = engine.currentState().money
        assertEquals("Money should not change", initialMoney, finalMoney)
        assertTrue("No truck should be scheduled", engine.currentState().scheduledTrucks.isEmpty())
    }

    @Test
    fun `buyItemCasePacks adds multiple case packs (via GameEngine)`() {
        val engine = newGameEngineWithItems()
        setEngineMoneyTo(engine, 50_000L)

        engine.buyItemCasePacks(1, 3)

        // With truck system: check scheduled quantity, not backroom directly
        val scheduledQty = engine.currentState().scheduledTrucks
            .flatMap { it.orders }
            .filter { it.itemId == 1 }
            .sumOf { it.quantity }
        // item_1 has casePack=6, so 3 case packs = 18 units scheduled
        assertEquals("Should schedule 18 units for delivery", 18, scheduledQty)
    }

    @Test
    fun `buyItemCasePacks is no-op when insufficient funds (via GameEngine)`() {
        val engine = newGameEngineWithItems()
        // Engine starts with 0 money
        val initialBackroom = engine.currentState().inventory[1]?.backroomStock ?: 0

        engine.buyItemCasePacks(1, 1_000)

        val finalBackroom = engine.currentState().inventory[1]?.backroomStock ?: 0
        assertEquals("Backroom should not change", initialBackroom, finalBackroom)
    }
}
