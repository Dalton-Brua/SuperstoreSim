package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.MoneyData
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for stockRandomItemFromBackroom() functionality.
 *
 * Uses a [FakeItemDao] backed by an in-memory list instead of Mockito.
 * Mockito cannot subclass [ItemMetadataCache] in this JUnit + JaCoCo
 * environment — byte-buddy conflicts with JaCoCo's instrumentation agent,
 * producing MockitoException ← IllegalStateException ← IllegalArgumentException.
 *
 * Time note: the engine starts at game-minute 0 (pre-dawn).  With
 * baseSpeed=120 and default 1× multiplier each 1 000 ms tick advances the
 * clock by 2 game-minutes.  The store opens at minute 360 (6 AM), so all
 * tests that use ≤ 50 ticks never reach opening time — the store stays CLOSED
 * throughout.  Stockers run regardless of store state (they are NOT gated on
 * StoreState.OPEN), so stocking is tested in isolation from customer traffic.
 *
 * Previous tests called toggleTimePaused() before ticking.  That set
 * playerPausedTime = true, which disabled the entire tick body (stockers
 * included) — every tick was a no-op.  Those calls have been removed.
 */
class StockRandomItemFromBackroomTest {

    private lateinit var gameEngine: GameEngine

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

    // ── test items ────────────────────────────────────────────────────────────

    private val testItems = listOf(
        Item(
            id = "item_1", name = "Item 1",
            price = MoneyData(cents = 999), description = "Test item 1",
            unitCost = MoneyData(cents = 500), category = ItemCategory.GROCERY, casePack = 6
        ),
        Item(
            id = "item_2", name = "Item 2",
            price = MoneyData(cents = 1499), description = "Test item 2",
            unitCost = MoneyData(cents = 700), category = ItemCategory.GROCERY, casePack = 4
        ),
        Item(
            id = "item_3", name = "Item 3",
            price = MoneyData(cents = 499), description = "Test item 3",
            unitCost = MoneyData(cents = 250), category = ItemCategory.GROCERY, casePack = 12
        ),
    )

    // ── setUp ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        val cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        gameEngine = GameEngine(cache)
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private fun setMoney(cents: Long) {
        gameEngine.state = gameEngine.state.copy(money = Money(cents))
    }

    // ════════════════════════════════════════════════════════════════════════
    // stockItemFromBackroom() — direct calls
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testStockItemFromBackroomIncreasesShelf() {
        val initialShelf    = gameEngine.currentState().inventory[1]?.shelfStock    ?: 0
        val initialBackroom = gameEngine.currentState().inventory[1]?.backroomStock ?: 0

        gameEngine.stockItemFromBackroom(1)

        assertEquals("Shelf should increase by 1",
            initialShelf + 1,    gameEngine.currentState().inventory[1]?.shelfStock)
        assertEquals("Backroom should decrease by 1",
            initialBackroom - 1, gameEngine.currentState().inventory[1]?.backroomStock)
    }

    @Test
    fun testStockItemFromBackroomWithNoBackroomStock() {
        // Drain backroom for item 1 (initial stock = 10, each call moves 1 unit)
        repeat(20) { gameEngine.stockItemFromBackroom(1) }

        val backroomAfterDrain = gameEngine.currentState().inventory[1]?.backroomStock ?: 0
        val shelfAfterDrain    = gameEngine.currentState().inventory[1]?.shelfStock    ?: 0
        assertEquals("Backroom should be 0 after draining", 0, backroomAfterDrain)

        // One more stock attempt on empty backroom must be a no-op
        gameEngine.stockItemFromBackroom(1)

        assertEquals("Shelf must not change when backroom is empty",
            shelfAfterDrain, gameEngine.currentState().inventory[1]?.shelfStock)
    }

    @Test
    fun testStockItemFromBackroomMultipleItems() {
        val initial1Shelf = gameEngine.currentState().inventory[1]?.shelfStock ?: 0
        val initial2Shelf = gameEngine.currentState().inventory[2]?.shelfStock ?: 0

        gameEngine.stockItemFromBackroom(1)
        gameEngine.stockItemFromBackroom(2)

        assertEquals("Item 1 shelf should increase by 1",
            initial1Shelf + 1, gameEngine.currentState().inventory[1]?.shelfStock)
        assertEquals("Item 2 shelf should increase by 1",
            initial2Shelf + 1, gameEngine.currentState().inventory[2]?.shelfStock)
    }

    @Test
    fun testStockItemFromBackroomInventoryRemains() {
        val totalBefore = gameEngine.currentState().inventory.values
            .sumOf { it.shelfStock + it.backroomStock }

        gameEngine.stockItemFromBackroom(1)

        val totalAfter = gameEngine.currentState().inventory.values
            .sumOf { it.shelfStock + it.backroomStock }

        assertEquals("Total inventory must not change — items only move shelf ↔ backroom",
            totalBefore, totalAfter)
    }

    // ════════════════════════════════════════════════════════════════════════
    // Stocker-driven stocking via tick()
    //
    // Stocking rate: 0.1 case-packs / sec / stocker  (with 1× speed)
    // Each tick = 1 000 ms = 1 real second, so a single stocker stocks
    // exactly 0.1 case-packs per tick and fires a full stock every 10 ticks.
    //
    // playerPausedTime stays false (default) so the tick body executes normally.
    // The store remains CLOSED for all tests (< 180 ticks needed to open),
    // but stocker logic is not gated on store state — stockers work 24/7.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testTickWithStockerTriggersStocking() {
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.STOCKER, EntityType.STOCKERS)

        val shelfsBefore    = gameEngine.currentState().inventory.mapValues { (_, v) -> v.shelfStock }
        val backroomsBefore = gameEngine.currentState().inventory.mapValues { (_, v) -> v.backroomStock }

        // 20 ticks × 0.1 stocks/tick = 2 case-pack stocks expected
        repeat(20) { gameEngine.tick(1_000) }

        var shelfIncreased   = false
        var backroomDecreased = false
        gameEngine.currentState().inventory.forEach { (id, inv) ->
            if (inv.shelfStock    > (shelfsBefore[id]    ?: 0)) shelfIncreased    = true
            if (inv.backroomStock < (backroomsBefore[id] ?: 0)) backroomDecreased = true
        }

        assertTrue("Shelf stock should increase when stocker is working",    shelfIncreased)
        assertTrue("Backroom stock should decrease when stocker is working", backroomDecreased)
    }

    @Test
    fun testMultipleStockersStockFaster() {
        setMoney(100_000L)

        // ── 1 stocker, 10 ticks → 10 × 0.1 = 1 stock ──────────────────────
        gameEngine.hireEntity(EntityDef.STOCKER, EntityType.STOCKERS)
        val backroomBefore1 = gameEngine.currentState().inventory.values.sumOf { it.backroomStock }

        repeat(10) { gameEngine.tick(1_000) }

        val stockedWith1 = backroomBefore1 -
            gameEngine.currentState().inventory.values.sumOf { it.backroomStock }

        // ── add 2nd stocker, 10 more ticks → 10 × 0.2 = 2 stocks ──────────
        gameEngine.hireEntity(EntityDef.STOCKER, EntityType.STOCKERS)
        val backroomBefore2 = gameEngine.currentState().inventory.values.sumOf { it.backroomStock }

        repeat(10) { gameEngine.tick(1_000) }

        val stockedWith2 = backroomBefore2 -
            gameEngine.currentState().inventory.values.sumOf { it.backroomStock }

        assertTrue("2 stockers should move at least as many items as 1 stocker in the same ticks",
            stockedWith2 >= stockedWith1)
    }

    @Test
    fun testStockerPrefersItemsWithLowestShelf() {
        // Raise item 3's shelf by manually stocking it 5 times (1 unit each).
        // item 3 casePack = 12; stockItemFromBackroom adds 1 unit, not a case pack.
        // Result: shelf3 = 15, shelf1 = shelf2 = 10.
        repeat(5) { gameEngine.stockItemFromBackroom(3) }

        val shelf1 = gameEngine.currentState().inventory[1]?.shelfStock ?: 0
        val shelf2 = gameEngine.currentState().inventory[2]?.shelfStock ?: 0
        val shelf3 = gameEngine.currentState().inventory[3]?.shelfStock ?: 0

        assertTrue("Setup: item 1 must have the lowest (or tied-lowest) shelf before test",
            shelf1 <= shelf2 && shelf1 <= shelf3)

        // Hire stocker and run 50 ticks → 50 × 0.1 = 5 case-pack stocks
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.STOCKER, EntityType.STOCKERS)
        repeat(50) { gameEngine.tick(1_000) }

        val newShelf1 = gameEngine.currentState().inventory[1]?.shelfStock ?: 0
        val newShelf3 = gameEngine.currentState().inventory[3]?.shelfStock ?: 0

        val increase1 = newShelf1 - shelf1
        val increase3 = newShelf3 - shelf3

        // The stocker always targets the item(s) with the lowest shelf first.
        // Item 1 and item 2 start lower (shelf = 10) than item 3 (shelf = 15),
        // so item 3 cannot be stocked until items 1 and 2 have risen above 15.
        // Therefore item 1's net shelf increase must be >= item 3's.
        assertTrue("Item with lowest shelf (item 1) should receive at least as much stock as the highest-shelf item (item 3)",
            increase1 >= increase3)
    }

    @Test
    fun testStockerStopsWhenNoBackroomStock() {
        // Drain all backrooms via the 1-unit-at-a-time call (initial backroom = 10 each)
        repeat(50) {
            gameEngine.stockItemFromBackroom(1)
            gameEngine.stockItemFromBackroom(2)
            gameEngine.stockItemFromBackroom(3)
        }

        assertTrue("Setup: all backrooms must be empty before test",
            gameEngine.currentState().inventory.values.all { it.backroomStock == 0 })

        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.STOCKER, EntityType.STOCKERS)
        val shelvesBefore = gameEngine.currentState().inventory.mapValues { (_, v) -> v.shelfStock }

        // Stocker fires stock actions but finds nothing in the backroom — shelves unchanged
        repeat(30) { gameEngine.tick(1_000) }

        gameEngine.currentState().inventory.forEach { (id, inv) ->
            assertEquals("Shelf must not change when every backroom is empty",
                shelvesBefore[id] ?: 0, inv.shelfStock)
        }
    }

    @Test
    fun testNoStockingWithoutStocker() {
        // No stocker hired — backroom must not decrease during ticks
        val backroomBefore = gameEngine.currentState().inventory.mapValues { (_, v) -> v.backroomStock }

        repeat(50) { gameEngine.tick(1_000) }

        gameEngine.currentState().inventory.forEach { (id, inv) ->
            assertEquals("Backroom must not change without a stocker",
                backroomBefore[id] ?: 0, inv.backroomStock)
        }
    }

    @Test
    fun testStockingWithInventoryConsistency() {
        setMoney(10_000L)
        gameEngine.hireEntity(EntityDef.STOCKER, EntityType.STOCKERS)

        val totalBefore = gameEngine.currentState().inventory.values
            .sumOf { it.shelfStock + it.backroomStock }

        // Store is CLOSED throughout (30 ticks × 2 min/tick = 60 game-minutes < 360 min open).
        // No sales occur, so items only move between shelf and backroom — total is invariant.
        repeat(30) { gameEngine.tick(1_000) }

        val totalAfter = gameEngine.currentState().inventory.values
            .sumOf { it.shelfStock + it.backroomStock }

        assertEquals("Total inventory (shelf + backroom) must remain constant",
            totalBefore, totalAfter)
    }
}

