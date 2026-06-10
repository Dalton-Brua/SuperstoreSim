package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.items.requiredTierForSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.superstoresimulator.domain.helpers.FakeItemDao

/**
 * Unit tests for the revenue-tier progression system.
 *
 * Uses a [FakeItemDao] backed by an in-memory list instead of Mockito.
 * Mockito cannot subclass [ItemMetadataCache] in this JUnit + JaCoCo
 * environment — byte-buddy conflicts with JaCoCo's instrumentation agent,
 * producing MockitoException ← IllegalStateException ← IllegalArgumentException.
 *
 * Actual [ItemUnlockTier] thresholds (all values are cumulative revenue in cents):
 *   TIER_1 :       0 ¢  ($0)
 *   TIER_2 : 500_000 ¢  ($5,000)
 *   TIER_3 : 2_000_000 ¢  ($20,000)
 *   TIER_GM: 10_000_000 ¢  ($100,000)
 *
 * Covers:
 *  - Correct tier returned by [ItemUnlockTier.fromTotalEarned]
 *  - [GameState.currentTier] initialises at TIER_1
 *  - [GameState.totalRevenue] increases after a completed transaction
 *  - Tier advances when totalRevenue crosses a threshold
 *  - totalRevenue never decreases when money is spent (buying items)
 *  - Tier category boundaries are correct
 *  - [requiredTierForSection] matches tier definitions
 */
class ProgressionTest {


    // ── helpers ───────────────────────────────────────────────────────────────

    /** Creates a test [Item] with an explicit price in cents. */
    private fun makeItem(
        id: Int,
        priceCents: Long,
        category: ItemCategory = ItemCategory.GROCERY
    ): Item = Item(
        id = "item_${String.format("%03d", id)}",
        name = "Item $id",
        price = MoneyData(cents = priceCents),
        description = "Test item $id",
        unitCost = MoneyData(cents = priceCents / 2),
        category = category,
        casePack = 6
    )

    /**
     * Builds a [GameEngine] backed by the given items.
     * Passing an empty list creates a no-inventory engine suitable for testing
     * pure state queries (tier, revenue fields, etc.).
     */
    private fun newEngine(items: List<Item> = emptyList()): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        val engine = createTestGameEngine(cache)
        // Reset to TIER_1 / zero revenue so progression tests start from a clean baseline.
        engine.state = engine.state.copy(
            currentTier = ItemUnlockTier.TIER_1,
            totalRevenue = Money(0),
        )
        return engine
    }

    /** Convenience overload — single item engine. */
    private fun engineWithItem(
        id: Int,
        priceCents: Long,
        category: ItemCategory = ItemCategory.GROCERY
    ): GameEngine = newEngine(listOf(makeItem(id, priceCents, category)))

    /** Rings up every line in the current transaction to completion.
     *
     * If no transaction is active, calls [GameEngine.ringUpItem] with the first
     * inventory item to trigger the auto-start path inside [com.example.superstoresimulator.domain.Transactions.TransactionEngine.ringUpSingleItem]
     * (when lines are empty that method calls startNewTransaction without checking store state).
     */
    private fun completeSingleTransaction(engine: GameEngine) {
        if (!engine.currentState().transactionActive) {
            val firstItemId = engine.currentState().inventory.keys.first()
            engine.ringUpItem(firstItemId)
        }
        val lines = engine.currentState().currentTransaction.lines
        for (line in lines) {
            repeat(line.quantity) { engine.ringUpItem(line.itemId) }
        }
    }

    // ── ItemUnlockTier.fromTotalEarned ────────────────────────────────────────
    //
    // All threshold values below are in CENTS and match ItemUnlockTier exactly:
    //   TIER_2 unlocks at   500_000 ¢ ($5,000)
    //   TIER_3 unlocks at 2_000_000 ¢ ($20,000)
    //   TIER_GM unlocks at 10_000_000 ¢ ($100,000)

    @Test
    fun `fromTotalEarned returns TIER_1 at zero`() {
        assertEquals(ItemUnlockTier.TIER_1, ItemUnlockTier.fromTotalEarned(0L))
    }

    @Test
    fun `fromTotalEarned returns TIER_1 just below TIER_2 threshold`() {
        // One cent below the 500_000 ¢ TIER_2 threshold
        assertEquals(ItemUnlockTier.TIER_1, ItemUnlockTier.fromTotalEarned(499_999L))
    }

    @Test
    fun `fromTotalEarned returns TIER_2 exactly at its threshold`() {
        assertEquals(ItemUnlockTier.TIER_2, ItemUnlockTier.fromTotalEarned(500_000L))
    }

    @Test
    fun `fromTotalEarned returns TIER_2 just below TIER_3 threshold`() {
        // One cent below the 2_000_000 ¢ TIER_3 threshold
        assertEquals(ItemUnlockTier.TIER_2, ItemUnlockTier.fromTotalEarned(1_999_999L))
    }

    @Test
    fun `fromTotalEarned returns TIER_3 exactly at its threshold`() {
        assertEquals(ItemUnlockTier.TIER_3, ItemUnlockTier.fromTotalEarned(2_000_000L))
    }

    @Test
    fun `fromTotalEarned returns TIER_GM exactly at its threshold`() {
        assertEquals(ItemUnlockTier.TIER_GM, ItemUnlockTier.fromTotalEarned(10_000_000L))
    }

    @Test
    fun `fromTotalEarned returns TIER_GM well above max threshold`() {
        assertEquals(ItemUnlockTier.TIER_GM, ItemUnlockTier.fromTotalEarned(999_999_999L))
    }

    // ── initial GameState ─────────────────────────────────────────────────────

    @Test
    fun `initial GameState starts at TIER_1`() {
        val engine = newEngine()
        assertEquals(ItemUnlockTier.TIER_1, engine.currentState().currentTier)
    }

    @Test
    fun `initial GameState has zero totalRevenue`() {
        val engine = newEngine()
        assertEquals(Money.ZERO, engine.currentState().totalRevenue)
    }

    // ── totalRevenue accumulation ─────────────────────────────────────────────

    @Test
    fun `totalRevenue increases after completing a transaction`() {
        val engine = engineWithItem(id = 1, priceCents = 999)
        val revenueBefore = engine.currentState().totalRevenue

        completeSingleTransaction(engine)

        val revenueAfter = engine.currentState().totalRevenue
        assertTrue(
            "totalRevenue should increase after a transaction (was $revenueBefore, now $revenueAfter)",
            revenueAfter > revenueBefore
        )
    }

    @Test
    fun `totalRevenue does not decrease when spending money on inventory`() {
        val engine = engineWithItem(id = 1, priceCents = 999)

        // Complete the auto-started transaction to earn some revenue and money
        completeSingleTransaction(engine)
        val revenueBefore = engine.currentState().totalRevenue

        // Buying inventory deducts from money but must NOT touch totalRevenue
        engine.buyItemToBackroom(1)

        val revenueAfter = engine.currentState().totalRevenue
        assertEquals("Buying inventory must NOT change totalRevenue", revenueBefore, revenueAfter)
    }

    // ── tier advancement ──────────────────────────────────────────────────────

    /**
     * TIER_2 threshold is 500_000 ¢ ($5,000).  An item priced at 600_000 ¢ ($6,000)
     * generates > $6,000 in a single transaction (qty ≥ 1 + 8.25 % tax), which crosses
     * the revenue gate on the very first completed transaction.
     *
     * Since April 2026 the tier is NOT auto-unlocked — the player must call
     * [GameEngine.unlockNextTier] and pay [ItemUnlockTier.TIER_2.unlockCost] ($1,000).
     */
    @Test
    fun `tier advances to TIER_2 after crossing 5000 dollar threshold`() {
        val engine = engineWithItem(id = 1, priceCents = 600_000)

        assertEquals("Should start at TIER_1", ItemUnlockTier.TIER_1, engine.currentState().currentTier)

        completeSingleTransaction(engine)

        val stateAfterTx = engine.currentState()
        assertTrue("totalRevenue must exceed $5,000", stateAfterTx.totalRevenue.cents >= 500_000L)

        // Revenue gate met but tier must NOT auto-advance — still TIER_1
        assertEquals(
            "currentTier must remain TIER_1 until the player explicitly pays",
            ItemUnlockTier.TIER_1,
            stateAfterTx.currentTier
        )

        // Player pays the $1,000 unlock cost — tier advances
        engine.unlockNextTier()

        val stateAfterUnlock = engine.currentState()
        assertEquals("currentTier must advance to TIER_2 after paying", ItemUnlockTier.TIER_2, stateAfterUnlock.currentTier)

        // Unlock cost ($1,000 = 100_000 ¢) must have been deducted from money
        val expectedMoneyDeducted = ItemUnlockTier.TIER_2.unlockCost
        assertTrue(
            "money should have decreased by at least the unlock cost",
            stateAfterTx.money - stateAfterUnlock.money >= expectedMoneyDeducted
        )
    }

    @Test
    fun `unlockNextTier does nothing when revenue gate is not yet met`() {
        // Item at 10 ¢ — one transaction earns well under the 500_000 ¢ TIER_2 threshold
        val engine = engineWithItem(id = 1, priceCents = 10)

        completeSingleTransaction(engine)

        val stateBefore = engine.currentState()
        engine.unlockNextTier()  // should be a no-op
        val stateAfter = engine.currentState()

        assertEquals(
            "currentTier must remain TIER_1 when revenue gate is not met",
            ItemUnlockTier.TIER_1,
            stateAfter.currentTier
        )
        assertEquals("money must not change on a no-op unlock attempt", stateBefore.money, stateAfter.money)
    }

    // ── category boundary tests ───────────────────────────────────────────────

    @Test
    fun `TIER_1 contains GROCERY SNACKS and DRINKS only`() {
        val sections = ItemUnlockTier.TIER_1.unlockedSections
        assertTrue(ItemCategory.GROCERY in sections)
        assertTrue(ItemCategory.SNACKS in sections)
        assertTrue(ItemCategory.DRINKS in sections)
        assertFalse("DAIRY should not be in TIER_1",   ItemCategory.DAIRY   in sections)
        assertFalse("PRODUCE should not be in TIER_1", ItemCategory.PRODUCE in sections)
        assertFalse("FROZEN should not be in TIER_1",  ItemCategory.FROZEN  in sections)
        assertFalse("BAKERY should not be in TIER_1",  ItemCategory.BAKERY  in sections)
        assertFalse("MEAT should not be in TIER_1",    ItemCategory.MEAT    in sections)
        assertFalse("HEALTH should not be in TIER_1",  ItemCategory.HEALTH  in sections)
    }

    @Test
    fun `TIER_2 adds DAIRY to TIER_1 sections`() {
        val sections = ItemUnlockTier.TIER_2.unlockedSections
        assertTrue(ItemCategory.DAIRY   in sections)
        assertTrue(ItemCategory.GROCERY in sections)
        assertTrue(ItemCategory.SNACKS  in sections)
        assertTrue(ItemCategory.DRINKS  in sections)
        assertFalse("PRODUCE should not be in TIER_2", ItemCategory.PRODUCE in sections)
        assertFalse("FROZEN should not be in TIER_2",  ItemCategory.FROZEN  in sections)
    }

    @Test
    fun `TIER_3 adds PRODUCE FROZEN BAKERY to TIER_2 sections`() {
        val sections = ItemUnlockTier.TIER_3.unlockedSections
        assertTrue(ItemCategory.PRODUCE in sections)
        assertTrue(ItemCategory.FROZEN  in sections)
        assertTrue(ItemCategory.BAKERY  in sections)
        assertTrue(ItemCategory.DAIRY   in sections)
        assertFalse("MEAT should not be in TIER_3",      ItemCategory.MEAT      in sections)
        assertFalse("HEALTH should not be in TIER_3",    ItemCategory.HEALTH    in sections)
        assertFalse("HOUSEHOLD should not be in TIER_3", ItemCategory.HOUSEHOLD in sections)
    }

    @Test
    fun `TIER_GM contains every ItemCategory`() {
        val sections = ItemUnlockTier.TIER_GM.unlockedSections
        ItemCategory.entries.forEach { category ->
            assertTrue("TIER_GM must include $category", category in sections)
        }
    }

    // ── requiredTierForSection ────────────────────────────────────────────────

    @Test
    fun `requiredTierForSection maps GROCERY SNACKS DRINKS to TIER_1`() {
        assertEquals(ItemUnlockTier.TIER_1, requiredTierForSection(ItemCategory.GROCERY))
        assertEquals(ItemUnlockTier.TIER_1, requiredTierForSection(ItemCategory.SNACKS))
        assertEquals(ItemUnlockTier.TIER_1, requiredTierForSection(ItemCategory.DRINKS))
    }

    @Test
    fun `requiredTierForSection maps DAIRY to TIER_2`() {
        assertEquals(ItemUnlockTier.TIER_2, requiredTierForSection(ItemCategory.DAIRY))
    }

    @Test
    fun `requiredTierForSection maps PRODUCE FROZEN BAKERY to TIER_3`() {
        assertEquals(ItemUnlockTier.TIER_3, requiredTierForSection(ItemCategory.PRODUCE))
        assertEquals(ItemUnlockTier.TIER_3, requiredTierForSection(ItemCategory.FROZEN))
        assertEquals(ItemUnlockTier.TIER_3, requiredTierForSection(ItemCategory.BAKERY))
    }

    @Test
    fun `requiredTierForSection maps specialty categories to TIER_GM`() {
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.MEAT))
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.HEALTH))
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.HOUSEHOLD))
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.PHARMACY))
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.ELECTRONICS))
    }

    // ── nextTier companion ────────────────────────────────────────────────────

    @Test
    fun `nextTier returns correct successor tiers`() {
        assertEquals(ItemUnlockTier.TIER_2, ItemUnlockTier.nextTier(ItemUnlockTier.TIER_1))
        assertEquals(ItemUnlockTier.TIER_3, ItemUnlockTier.nextTier(ItemUnlockTier.TIER_2))
        assertEquals(ItemUnlockTier.TIER_GM, ItemUnlockTier.nextTier(ItemUnlockTier.TIER_3))
        assertNull("nextTier of TIER_GM should be null", ItemUnlockTier.nextTier(ItemUnlockTier.TIER_GM))
    }
}