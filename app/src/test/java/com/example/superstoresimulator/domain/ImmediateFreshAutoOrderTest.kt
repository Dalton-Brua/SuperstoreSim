package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.MoneyData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the immediate fresh auto-order processing added in the
 * "Immediate Fresh Handler Order Processing" plan.
 *
 * Key behaviours tested:
 *  1. When a fresh handler is idle and a fresh item is below threshold AND funds are
 *     sufficient, `attemptFreshHandlerAutoOrder()` (triggered via tick) buys inventory
 *     immediately: money is deducted and backroom stock increases before midnight.
 *  2. The accumulator's `autoOrderedFreshItems` is populated in the same tick.
 *  3. On the failure path (insufficient funds) `incompleteOrderedFreshItems` in the
 *     accumulator is populated and `incompleteFreshOrders` in GameState is updated.
 *  4. The same item is NOT re-attempted on subsequent ticks within the same day
 *     (once it appears in autoOrderedFreshItems or incompleteOrderedFreshItems).
 *
 * All tests use FakeItemDao and drive fresh-handler idle ticks by resetting backroom
 * stock to zero so `stockRandomFreshItemFromBackroom` returns immediately and
 * `attemptFreshHandlerAutoOrder` fires.
 */
class ImmediateFreshAutoOrderTest {

    // ── In-memory fake ─────────────────────────────────────────────────────────

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

    // ── Item factories ─────────────────────────────────────────────────────────

    /**
     * A perishable item with [shelfLifeDays] set.
     * casePack = 6, unitCost = 100¢ → casePackCost = 600¢ = $6.00
     */
    private fun makeFreshItem(id: Int): Item = Item(
        id = "item_${String.format("%03d", id)}",
        name = "Fresh Item $id",
        price = MoneyData(cents = 200L),
        description = "Fresh test item $id",
        unitCost = MoneyData(cents = 100L),
        category = ItemCategory.PRODUCE,
        casePack = 6,
        tier = "TIER_1",
        shelfLifeDays = 3,
    )

    /**
     * A non-perishable item (shelfLifeDays = null). Used to confirm non-fresh items
     * are ignored by the fresh auto-order logic.
     */
    private fun makeNonFreshItem(id: Int): Item = Item(
        id = "item_${String.format("%03d", id)}",
        name = "Dry Item $id",
        price = MoneyData(cents = 200L),
        description = "Dry test item $id",
        unitCost = MoneyData(cents = 100L),
        category = ItemCategory.GROCERY,
        casePack = 6,
        tier = "TIER_1",
        shelfLifeDays = null,
    )

    // ── Engine factory ──────────────────────────────────────────────────────────

    private fun newEngine(items: List<Item>): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        val engine = GameEngine(cache)
        // Generous backroom cap and money to avoid unrelated guards firing.
        engine.state = engine.state.copy(
            storeConfig = engine.state.storeConfig.copy(backroomCapPerItem = 100),
        )
        return engine
    }

    /**
     * Advance the engine's game time to 6:30 AM (GameTime(390)) so that staff on
     * the default Morning shift (startHour=6, covers 6–14) are on-shift.
     *
     * Phase 1 made staff work schedule-aware: hired staff only accumulate progress
     * during their shift hours.  The engine starts at GameTime(0) (midnight, hour 0),
     * which is before any legal shift start (minimum 6).  Without this call, all
     * tick-driven staff work is a no-op in tests.
     *
     * [loadState] is used because it syncs both [GameState.currentTime] and
     * the internal [TimeManager] in one call, preventing the TimeManager from
     * overwriting our time offset on the very next tick.
     */
    private fun advanceToShiftHour(engine: GameEngine) {
        engine.loadState(engine.state.copy(currentTime = com.example.superstoresimulator.domain.time.GameTime(390)))
    }

    /**
     * Force a fresh-handler idle tick without actually stocking:
     * clear backroom of all fresh items so stockRandomFreshItemFromBackroom is a
     * no-op, then tick long enough for the fresh-handler accumulator to produce ≥1
     * action (at 1× speed one fresh handler accumulates ~0.1 actions/sec, so 1000 ms
     * at 10× speed = 1 second scaled → 1 action is guaranteed after ~200ms scaled).
     *
     * We drive the tick at 1× speed and call it with 10_000 ms (10 real seconds
     * wall-clock = 10 game-seconds at 1× → 1 full fresh-handler action).
     */
    private fun triggerFreshHandlerIdleTick(engine: GameEngine) {
        engine.tick(10_000L)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Returns total units scheduled across all trucks for the given item. */
    private fun scheduledQtyForItem(engine: GameEngine, itemId: Int): Int =
        engine.currentState().scheduledTrucks
            .flatMap { it.orders }
            .filter { it.itemId == itemId }
            .sumOf { it.quantity }

    // ── Tests ───────────────────────────────────────────────────────────────────

    @Test
    fun `successful auto-order deducts money and schedules delivery`() {
        val freshItem = makeFreshItem(id = 1)
        val engine = newEngine(listOf(freshItem))

        // Hire first (costs 1_500¢), then reset money to just enough for one case-pack (600¢)
        engine.state = engine.state.copy(money = Money(10_000L))
        engine.hireEntity(EntityDef.FRESH_HANDLER)
        // Advance to morning shift so the fresh handler is on-shift when ticks run.
        advanceToShiftHour(engine)
        engine.state = engine.state.copy(money = Money(600L))

        // Empty the backroom so the handler has nothing to stock → idle → auto-order fires
        engine.state = engine.state.copy(
            inventory = engine.state.inventory.mapValues { (_, inv) ->
                inv.copy(backroomBatches = emptyList())
            }
        )

        val moneyBefore = engine.state.money

        // Enable auto-ordering with threshold above current stock
        engine.state = engine.state.copy(
            freshAutoOrderConfig = FreshAutoOrderConfig(
                enabled = true,
                minStockThreshold = 100, // well above current stock (shelfStock=10)
                casePacksPerItem = 1,
            )
        )

        triggerFreshHandlerIdleTick(engine)

        val stateAfter = engine.currentState()

        // Money should be deducted by casePackCost = 600¢ (deducted immediately at order time)
        assertTrue(
            "Money should be deducted after successful auto-order",
            stateAfter.money < moneyBefore
        )
        assertEquals(
            "Exactly one case-pack cost deducted",
            Money(moneyBefore.cents - 600L),
            stateAfter.money
        )

        // With the truck delivery system items are scheduled, not immediately in backroom
        assertEquals(
            "One case-pack (6 units) scheduled for delivery",
            6,
            scheduledQtyForItem(engine, 1)
        )
        // Backroom is unchanged — items are in transit
        assertEquals(
            "Backroom stock unchanged (items are in transit via truck)",
            0,
            stateAfter.inventory[1]?.backroomStock ?: 0
        )
    }

    @Test
    fun `successful auto-order populates autoOrderedFreshItems in current day metrics`() {
        val freshItem = makeFreshItem(id = 1)
        val engine = newEngine(listOf(freshItem))

        // Hire first (costs 1_500¢) then proceed — remaining money covers the 600¢ order
        engine.state = engine.state.copy(money = Money(10_000L))
        engine.hireEntity(EntityDef.FRESH_HANDLER)
        // Advance to morning shift so the fresh handler is on-shift when ticks run.
        advanceToShiftHour(engine)
        engine.state = engine.state.copy(
            inventory = engine.state.inventory.mapValues { (_, inv) ->
                inv.copy(backroomBatches = emptyList())
            },
            freshAutoOrderConfig = FreshAutoOrderConfig(
                enabled = true,
                minStockThreshold = 100,
                casePacksPerItem = 1,
            )
        )

        triggerFreshHandlerIdleTick(engine)

        val metrics = engine.currentState().currentDayMetrics
        assertEquals(
            "One entry in autoOrderedFreshItems",
            1,
            metrics.autoOrderedFreshItems.size
        )
        val line = metrics.autoOrderedFreshItems.first()
        assertEquals("Correct itemId on auto-order line", 1, line.itemId)
        assertEquals("Correct case packs ordered", 1, line.casePacksOrdered)
        assertEquals("Correct total cost", Money(600L), line.totalCost)
    }

    @Test
    fun `failed auto-order when insufficient funds populates incompleteOrderedFreshItems in metrics`() {
        val freshItem = makeFreshItem(id = 1)
        val engine = newEngine(listOf(freshItem))

        // Hire first (costs 1_500¢), then drop money below the 600¢ case-pack cost
        engine.state = engine.state.copy(money = Money(10_000L))
        engine.hireEntity(EntityDef.FRESH_HANDLER)
        // Advance to morning shift so the fresh handler is on-shift when ticks run.
        advanceToShiftHour(engine)
        engine.state = engine.state.copy(
            money = Money(100L),  // not enough for the 600¢ case-pack
            inventory = engine.state.inventory.mapValues { (_, inv) ->
                inv.copy(backroomBatches = emptyList())
            },
            freshAutoOrderConfig = FreshAutoOrderConfig(
                enabled = true,
                minStockThreshold = 100,
                casePacksPerItem = 1,
            )
        )

        triggerFreshHandlerIdleTick(engine)

        val state = engine.currentState()

        // Money should NOT change (order failed)
        assertEquals("Money unchanged on failed auto-order", Money(100L), state.money)

        // Metrics should record the failure
        val metrics = state.currentDayMetrics
        assertEquals(
            "autoOrderedFreshItems empty on failure",
            0,
            metrics.autoOrderedFreshItems.size
        )
        assertEquals(
            "One entry in incompleteOrderedFreshItems",
            1,
            metrics.incompleteOrderedFreshItems.size
        )
        val line = metrics.incompleteOrderedFreshItems.first()
        assertEquals("Correct itemId on incomplete line", 1, line.itemId)
        assertEquals("Correct reason", "Insufficient funds", line.reason)

        // incompleteFreshOrders (persistent) should also be populated
        assertEquals(
            "incompleteFreshOrders has one entry",
            1,
            state.incompleteFreshOrders.size
        )
        assertEquals("incompleteFreshOrders itemId correct", 1, state.incompleteFreshOrders.first().itemId)
    }

    @Test
    fun `item is not re-attempted on subsequent ticks within the same day`() {
        val freshItem = makeFreshItem(id = 1)
        val engine = newEngine(listOf(freshItem))

        // Hire first (costs 1_500¢), then set money to cover exactly one order (600¢)
        engine.state = engine.state.copy(money = Money(10_000L))
        engine.hireEntity(EntityDef.FRESH_HANDLER)
        // Advance to morning shift so the fresh handler is on-shift when ticks run.
        advanceToShiftHour(engine)
        engine.state = engine.state.copy(
            money = Money(600L),
            inventory = engine.state.inventory.mapValues { (_, inv) ->
                inv.copy(backroomBatches = emptyList())
            },
            freshAutoOrderConfig = FreshAutoOrderConfig(
                enabled = true,
                minStockThreshold = 100,
                casePacksPerItem = 1,
            )
        )

        // First tick: order fires, money deducted
        triggerFreshHandlerIdleTick(engine)
        val moneyAfterFirstTick = engine.currentState().money

        // Second tick: item is already in autoOrderedFreshItems → should NOT be ordered again
        triggerFreshHandlerIdleTick(engine)
        val moneyAfterSecondTick = engine.currentState().money

        assertEquals(
            "Money should not change on second tick (item already handled today)",
            moneyAfterFirstTick,
            moneyAfterSecondTick
        )
        assertEquals(
            "Still only one entry in autoOrderedFreshItems after second tick",
            1,
            engine.currentState().currentDayMetrics.autoOrderedFreshItems.size
        )
    }

    @Test
    fun `non-fresh items are never auto-ordered`() {
        val dryItem = makeNonFreshItem(id = 1)
        val engine = newEngine(listOf(dryItem))

        engine.state = engine.state.copy(money = Money(10_000L))
        engine.hireEntity(EntityDef.FRESH_HANDLER)
        engine.state = engine.state.copy(
            inventory = engine.state.inventory.mapValues { (_, inv) ->
                inv.copy(backroomBatches = emptyList())
            },
            freshAutoOrderConfig = FreshAutoOrderConfig(
                enabled = true,
                minStockThreshold = 100,
                casePacksPerItem = 1,
            )
        )

        val moneyBefore = engine.state.money
        triggerFreshHandlerIdleTick(engine)

        assertEquals("Money unchanged — dry items must not be auto-ordered", moneyBefore, engine.currentState().money)
        assertEquals(
            "autoOrderedFreshItems empty for non-fresh catalog",
            0,
            engine.currentState().currentDayMetrics.autoOrderedFreshItems.size
        )
    }

    @Test
    fun `auto-ordering is skipped when config is disabled`() {
        val freshItem = makeFreshItem(id = 1)
        val engine = newEngine(listOf(freshItem))

        engine.state = engine.state.copy(money = Money(10_000L))
        engine.hireEntity(EntityDef.FRESH_HANDLER)
        engine.state = engine.state.copy(
            inventory = engine.state.inventory.mapValues { (_, inv) ->
                inv.copy(backroomBatches = emptyList())
            },
            freshAutoOrderConfig = FreshAutoOrderConfig(
                enabled = false,   // ← disabled
                minStockThreshold = 100,
                casePacksPerItem = 1,
            )
        )

        val moneyBefore = engine.state.money
        triggerFreshHandlerIdleTick(engine)

        assertEquals("Money unchanged when auto-order is disabled", moneyBefore, engine.currentState().money)
    }
}

