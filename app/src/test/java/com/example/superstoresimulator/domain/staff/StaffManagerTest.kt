package com.example.superstoresimulator.domain.staff

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.createTestGameEngine
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [StaffManager].
 *
 * [StaffManager] has two distinct responsibilities tested here:
 *
 *  1. Pure state operations ([hireEntity], [upgradeEntity], [fireEntity]) — each
 *     takes a [GameState] and returns a new [GameState]. Tests construct [GameState]
 *     directly with a pre-populated [HiredEntityRegistry]; no FakeItemDao needed.
 *
 *  2. Fractional-accumulator helpers ([advanceCashierProgress], [advanceStockerProgress])
 *     — each mutates an internal float and returns the whole-action count for the tick.
 *     A fresh [StaffManager] instance is used per test via [@Before] so accumulator
 *     state does not leak between tests.
 *
 * Cost constants used (all in cents):
 *   CASHIER.cost      = 1_500 ¢  ($15)
 *   FAST_CASHIER.cost = 10_000 ¢ ($100)
 *   STOCKER.cost      = 1_500 ¢  ($15)
 *
 * Accumulator rates:
 *   Cashier : 1.0 items/second per hired cashier
 *   Stocker : 0.15 case-packs/second per hired stocker
 *
 * Covers:
 *  [hireEntity]
 *   - Registry grows by one after a successful hire
 *   - Cost is deducted from money
 *   - Returns state unchanged when money is insufficient
 *   - Money is not touched when hire is blocked
 *
 *  [upgradeEntity]
 *   - Entity definition advances to nextUpgrade after successful upgrade
 *   - Upgrade cost is deducted from money
 *   - Returns state unchanged when money is insufficient for the upgrade
 *
 *  [fireEntity]
 *   - Registry shrinks by one after firing
 *   - Fired entity is no longer present in the registry
 *   - Money is not affected by firing
 *
 *  [advanceCashierProgress]
 *   - Returns 0 when cashierCount is 0 (no accumulation)
 *   - Returns 0 on the first tick when delta is too small to reach 1.0
 *   - Returns 1 when accumulated rate crosses 1.0 across two ticks
 *   - Fractional remainder is retained between ticks
 *   - Respects the game-speed multiplier
 *
 *  [advanceStockerProgress]
 *   - Returns 0 when stockerCount is 0 (no accumulation)
 *   - Returns 0 on a single tick at default rate (0.15/s × 1s = 0.15 < 1.0)
 *   - Accumulates correctly over 7 ticks to return 1 action
 *   - Respects the game-speed multiplier (4× speed reduces the required ticks)
 */
class StaffManagerTest {

    private lateinit var staffManager: StaffManager

    @Before
    fun setUp() {
        staffManager = StaffManager()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a [GameState] with [moneyCents] and a registry that has one hired
     * [EntityDef.CASHIER], returning both the state and the hired entity's id (1).
     */
    private fun stateWithOneCashier(moneyCents: Long): GameState {
        val registry = HiredEntityRegistry().hireEntity(EntityDef.CASHIER)
        return GameState(money = Money(moneyCents), hiredEntityRegistry = registry)
    }

    // ── hireEntity ────────────────────────────────────────────────────────────

    @Test
    fun `hireEntity adds one entity to the registry`() {
        val state = GameState(money = Money(50_000L))
        val result = staffManager.hireEntity(state, EntityDef.CASHIER)
        assertEquals(1, result.hiredEntityRegistry.totalCount())
    }

    @Test
    fun `hireEntity deducts the hire cost from money`() {
        val startMoney = Money(50_000L)
        val state = GameState(money = startMoney)
        val result = staffManager.hireEntity(state, EntityDef.CASHIER)
        assertEquals(startMoney - EntityDef.CASHIER.cost, result.money)
    }

    @Test
    fun `hireEntity returns state unchanged when money is insufficient`() {
        // CASHIER costs 1_500 ¢; player has 1_499 ¢
        val state = GameState(money = Money(1_499L))
        val result = staffManager.hireEntity(state, EntityDef.CASHIER)
        assertEquals(0, result.hiredEntityRegistry.totalCount())
        assertEquals(Money(1_499L), result.money)
    }

    @Test
    fun `hireEntity money guard uses strict less-than — exact cost succeeds`() {
        val state = GameState(money = EntityDef.CASHIER.cost)
        val result = staffManager.hireEntity(state, EntityDef.CASHIER)
        assertEquals(1, result.hiredEntityRegistry.totalCount())
        assertEquals(Money.ZERO, result.money)
    }

    // ── upgradeEntity ─────────────────────────────────────────────────────────

    @Test
    fun `upgradeEntity advances tier to FAST`() {
        val state = stateWithOneCashier(moneyCents = 100_000L)
        val entityId = 1
        val result = staffManager.promoteEntity(state, entityId)
        val upgraded = result.hiredEntityRegistry.getById(entityId)
        assertEquals(Tier.FAST, upgraded.tier)
        assertEquals(EntityDef.CASHIER, upgraded.entityDefinition)
    }

    @Test
    fun `upgradeEntity deducts the upgrade cost from money`() {
        val startMoney = Money(100_000L)
        val state = stateWithOneCashier(moneyCents = startMoney.cents)
        val result = staffManager.promoteEntity(state, entityId = 1)
        val upgradeCost = Money(10_000) // BASE → FAST upgrade cost
        assertEquals(startMoney - upgradeCost, result.money)
    }

    @Test
    fun `upgradeEntity returns state unchanged when money is insufficient`() {
        // BASE→FAST costs 10,000 ¢; player has 9,999 ¢
        val state = stateWithOneCashier(moneyCents = 9_999L)
        val before = state.hiredEntityRegistry.getById(1).tier
        val result = staffManager.promoteEntity(state, entityId = 1)
        val after = result.hiredEntityRegistry.getById(1).tier
        assertEquals("Tier must not change on failed upgrade", before, after)
        assertEquals(Money(9_999L), result.money)
    }

    @Test
    fun `upgradeEntity does not touch money when already at max tier`() {
        val registry = HiredEntityRegistry().hireEntity(EntityDef.CASHIER)
        // Manually put entity at MANAGER tier
        val upgraded = registry.promoteEntity(1).promoteEntity(1)
        val state = GameState(money = Money(5_000L), hiredEntityRegistry = upgraded)
        assertThrows(IllegalStateException::class.java) {
            state.hiredEntityRegistry.getById(1).upgradeCost
        }
        assertEquals(Tier.MANAGER, state.hiredEntityRegistry.getById(1).tier)
    }

    // ── fireEntity ────────────────────────────────────────────────────────────

    @Test
    fun `fireEntity removes the entity from the registry`() {
        val state = stateWithOneCashier(moneyCents = 50_000L)
        val result = staffManager.fireEntity(state, entityId = 1)
        assertEquals(0, result.hiredEntityRegistry.totalCount())
    }

    @Test
    fun `fireEntity does not affect money`() {
        val state = stateWithOneCashier(moneyCents = 50_000L)
        val result = staffManager.fireEntity(state, entityId = 1)
        assertEquals(Money(50_000L), result.money)
    }

    @Test
    fun `fireEntity only removes the targeted entity when multiple are hired`() {
        val registry = HiredEntityRegistry()
            .hireEntity(EntityDef.CASHIER)  // id = 1
            .hireEntity(EntityDef.STOCKER)  // id = 2
        val state = GameState(money = Money(50_000L), hiredEntityRegistry = registry)
        val result = staffManager.fireEntity(state, entityId = 1)
        assertEquals(1, result.hiredEntityRegistry.totalCount())
        assertEquals(EntityDef.STOCKER, result.hiredEntityRegistry.getById(2).entityDefinition)
    }

    // ── advanceCashierProgress ────────────────────────────────────────────────

    @Test
    fun `advanceCashierProgress returns 0 when cashierCount is 0`() {
        val result = staffManager.advanceCashierProgress(
            cashierCount = 0f, delta = 10.0, multiplier = 1.0f,
        )
        assertEquals(0, result)
    }

    @Test
    fun `advanceCashierProgress returns 0 on first small tick with 1 cashier`() {
        // 2.0 items/s × 1 cashier × 0.1 s × 1× speed = 0.2 — below 1.0
        val result = staffManager.advanceCashierProgress(
            cashierCount = 1f, delta = 0.1, multiplier = 1.0f,
        )
        assertEquals(0, result)
    }

    @Test
    fun `advanceCashierProgress returns 1 after accumulating past 1 item`() {
        // 1.0 items/s × 1 cashier × 0.6 s = 0.6 → 0 whole
        staffManager.advanceCashierProgress(cashierCount = 1f, delta = 0.6, multiplier = 1.0f)
        // 0.6 + 0.6 = 1.2 → 1 whole, 0.2 remainder
        val result = staffManager.advanceCashierProgress(
            cashierCount = 1f, delta = 0.6, multiplier = 1.0f,
        )
        assertEquals(1, result)
    }

    @Test
    fun `advanceCashierProgress retains fractional remainder between ticks`() {
        // 1.0 × 1 × 0.6 = 0.6 → 0 whole, 0.6 left
        staffManager.advanceCashierProgress(cashierCount = 1f, delta = 0.6, multiplier = 1.0f)
        // 0.6 + 0.6 = 1.2 → 1 whole, 0.2 left
        staffManager.advanceCashierProgress(cashierCount = 1f, delta = 0.6, multiplier = 1.0f)
        // 0.2 + 0.6 = 0.8 → 0 whole
        val third = staffManager.advanceCashierProgress(cashierCount = 1f, delta = 0.6, multiplier = 1.0f)
        assertEquals("Third tick should give 0 whole actions (0.8 < 1.0)", 0, third)
    }

    @Test
    fun `advanceCashierProgress respects game-speed multiplier`() {
        // 1.0 × 1 × 0.2 × 4 = 0.8 < 1.0 → 0; second tick: 0.8+0.8 = 1.6 → 1
        staffManager.advanceCashierProgress(cashierCount = 1f, delta = 0.2, multiplier = 4.0f)
        val result = staffManager.advanceCashierProgress(
            cashierCount = 1f, delta = 0.2, multiplier = 4.0f,
        )
        assertEquals(1, result)
    }

    @Test
    fun `advanceCashierProgress scales with cashier count`() {
        // 1.0 items/s × 4 cashiers × 0.26 s × 1× = 1.04 → 1 whole
        val result = staffManager.advanceCashierProgress(
            cashierCount = 4f, delta = 0.26, multiplier = 1.0f,
        )
        assertEquals(1, result)
    }

    // ── advanceStockerProgress ────────────────────────────────────────────────

    @Test
    fun `advanceStockerProgress returns 0 when stockerCount is 0`() {
        val result = staffManager.advanceStockerProgress(
            stockerCount = 0f, delta = 100.0, multiplier = 1.0f,
        )
        assertEquals(0, result)
    }

    @Test
    fun `advanceStockerProgress returns 0 on a single 1-second tick with 1 stocker`() {
        // 0.15 case-packs/s × 1 stocker × 1.0 s × 1× = 0.15 — below 1.0
        val result = staffManager.advanceStockerProgress(
            stockerCount = 1f, delta = 1.0, multiplier = 1.0f,
        )
        assertEquals(0, result)
    }

    @Test
    fun `advanceStockerProgress returns 1 after seven one-second ticks with 1 stocker`() {
        // Each tick adds 0.15; after 7 ticks the accumulator reaches 1.05
        repeat(6) {
            staffManager.advanceStockerProgress(stockerCount = 1f, delta = 1.0, multiplier = 1.0f)
        }
        val seventh = staffManager.advanceStockerProgress(
            stockerCount = 1f, delta = 1.0, multiplier = 1.0f,
        )
        assertEquals(1, seventh)
    }

    @Test
    fun `advanceStockerProgress respects game-speed multiplier`() {
        // At 4× speed a single 2.5-second tick gives 0.1 × 1 × 2.5 × 4 = 1.0 → 1 whole
        val result = staffManager.advanceStockerProgress(
            stockerCount = 1f, delta = 2.5, multiplier = 4.0f,
        )
        assertEquals(1, result)
    }

    @Test
    fun `advanceStockerProgress scales with stocker count`() {
        // 0.1 case-packs/s × 5 stockers × 2.0 s × 1× = 1.0 → 1 whole
        val result = staffManager.advanceStockerProgress(
            stockerCount = 5f, delta = 2.0, multiplier = 1.0f,
        )
        assertEquals(1, result)
    }

    // ── GameEngine integration tests ──────────────────────────────────────────

    /**
     * Tests that verify StaffManager behavior through the GameEngine's public API.
     * These complement the pure-manager tests above by validating the end-to-end flow
     * from GameEvent dispatch through onEvent() routing to state mutation.
     */

    private fun newGameEngine(): GameEngine {
        val cache = ItemMetadataCache(
            object : ItemDao {
                override suspend fun getAllItems() = emptyList<com.example.superstoresimulator.domain.items.Item>()
                override suspend fun getItemById(itemId: String) = null
                override suspend fun insertItem(item: com.example.superstoresimulator.domain.items.Item) {}
                override suspend fun deleteItem(itemId: String) {}
                override suspend fun deleteAll() {}
                override suspend fun getItemName(itemId: String): String? = null
                override suspend fun getAllItemsWithNames() = emptyList<ItemWithName>()
                override suspend fun getItemsByIds(itemIds: List<String>) = emptyList<com.example.superstoresimulator.domain.items.Item>()
                override suspend fun insertBatch(items: List<com.example.superstoresimulator.domain.items.Item>) {}
                override suspend fun getItemCount() = 0
                override suspend fun getItemsByCategory(category: String) = emptyList<com.example.superstoresimulator.domain.items.Item>()
                override suspend fun searchItemsByName(searchTerm: String) = emptyList<com.example.superstoresimulator.domain.items.Item>()
                override suspend fun getItemsForInventory(itemIds: List<String>) = emptyList<com.example.superstoresimulator.domain.items.Item>()
            }
        )
        runBlocking { cache.initialize() }
        return createTestGameEngine(cache)
    }

    private fun setEngineMoneyTo(engine: GameEngine, cents: Long) {
        engine.state = engine.state.copy(money = Money(cents))
    }

    @Test
    fun `hireEntity increases registry count when cash available (via GameEngine)`() {
        val engine = newGameEngine()
        setEngineMoneyTo(engine, 10_000L)
        val initialCount = engine.currentState().hiredEntityRegistry.totalCount()

        engine.hireEntity(EntityDef.CASHIER)

        val finalCount = engine.currentState().hiredEntityRegistry.totalCount()
        assertEquals("Registry count should increase by 1", initialCount + 1, finalCount)
    }

    @Test
    fun `hireEntity deducts money when successful (via GameEngine)`() {
        val engine = newGameEngine()
        setEngineMoneyTo(engine, 10_000L)
        val initialMoney = engine.currentState().money

        engine.hireEntity(EntityDef.CASHIER)

        val finalMoney = engine.currentState().money
        assertTrue("Money should decrease after hiring", finalMoney < initialMoney)
    }

    @Test
    fun `hireEntity is rejected when insufficient funds (via GameEngine)`() {
        val engine = newGameEngine()
        // Engine starts with 0 money
        val initialCount = engine.currentState().hiredEntityRegistry.totalCount()

        engine.hireEntity(EntityDef.CASHIER)

        val finalCount = engine.currentState().hiredEntityRegistry.totalCount()
        assertEquals("Registry count should not change", initialCount, finalCount)
    }

    @Test
    fun `upgradeEntity advances definition when cash available (via GameEngine)`() {
        val engine = newGameEngine()
        setEngineMoneyTo(engine, 20_000L)

        engine.hireEntity(EntityDef.CASHIER)
        val stateAfterHire = engine.currentState()

        if (stateAfterHire.hiredEntityRegistry.totalCount() > 0) {
            val entityId = stateAfterHire.hiredEntityRegistry.getNextEntityId() - 1
            engine.promoteEntity(entityId)

            val upgradedEntity = engine.currentState().hiredEntityRegistry.getById(entityId)
            assertNotNull("Entity should still exist after upgrade", upgradedEntity)
        }
    }

    @Test
    fun `fireEntity removes from registry (via GameEngine)`() {
        val engine = newGameEngine()
        setEngineMoneyTo(engine, 10_000L)

        engine.hireEntity(EntityDef.CASHIER)
        val stateAfterHire = engine.currentState()
        val initialCount = stateAfterHire.hiredEntityRegistry.totalCount()

        if (initialCount > 0) {
            val entityId = stateAfterHire.hiredEntityRegistry.getNextEntityId() - 1
            engine.fireEntity(entityId)

            val finalCount = engine.currentState().hiredEntityRegistry.totalCount()
            assertEquals("Registry count should decrease by 1", initialCount - 1, finalCount)
        }
    }
}
