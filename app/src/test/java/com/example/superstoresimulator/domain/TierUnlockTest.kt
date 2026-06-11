package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.store.StoreState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.superstoresimulator.domain.helpers.FakeItemDao

/**
 * Unit tests for [GameEngine.unlockNextTier] and the revenue/tier progression system.
 *
 * Tiers and their thresholds (all values in CENTS):
 *   TIER_1 : 0 ¢  — starting tier, no unlock cost
 *   TIER_2 : revenue gate = 500_000 ¢ ($5,000)  | unlock cost = 100_000 ¢ ($1,000)
 *   TIER_3 : revenue gate = 2_000_000 ¢ ($20,000) | unlock cost = 400_000 ¢ ($4,000)
 *   TIER_GM: revenue gate = 10_000_000 ¢ ($100,000) | unlock cost = 2_000_000 ¢ ($20,000)
 *
 * Key rules under test:
 *   - Tier advances ONLY when both gates pass: totalRevenue ≥ unlockAmount AND money ≥ unlockCost
 *   - unlockNextTier() is a no-op at max tier TIER_GM
 *   - Tier does NOT auto-advance — the player must call unlockNextTier() explicitly
 *   - unlockCost is deducted from [GameState.money]; totalRevenue is never affected
 *   - On success [GameState.currentTier] advances to the next tier
 *
 * Uses [FakeItemDao] in-memory instead of Mockito (byte-buddy/JaCoCo incompatibility).
 * All pre-conditions are injected via [engine.state] assignment helpers so that tests
 * are deterministic and independent of the transaction / traffic system.
 */
class TierUnlockTest {


    // ── helpers ───────────────────────────────────────────────────────────────

    private fun makeItem(id: Int, priceCents: Long, category: ItemCategory = ItemCategory.GROCERY): Item =
        Item(
            id = "item_${String.format("%03d", id)}",
            name = "Item $id",
            price = MoneyData(cents = priceCents),
            description = "Test item $id",
            unitCost = MoneyData(cents = priceCents / 2),
            category = category,
            casePack = 6,
            tier = "TIER_1"
        )

    private fun newEngine(items: List<Item> = emptyList()): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        return createTestGameEngine(cache)
    }

    /** Injects money directly into the engine state (bypasses the purchase path). */
    private fun setMoney(engine: GameEngine, cents: Long) {
        engine.state = engine.state.copy(money = Money(cents))
    }

    /** Injects cumulative revenue directly — simulates revenue already earned. */
    private fun setRevenue(engine: GameEngine, cents: Long) {
        engine.state = engine.state.copy(totalRevenue = Money(cents))
    }

    // ── Revenue gate guard ────────────────────────────────────────────────────

    @Test
    fun `unlockNextTier is no-op when total revenue is zero`() {
        val engine = newEngine()
        setMoney(engine, 10_000_000L)
        // totalRevenue starts at 0 — well below the 500_000 ¢ TIER_2 gate

        engine.unlockNextTier()

        assertEquals(
            "Tier must remain TIER_1 when revenue gate is not met",
            ItemUnlockTier.TIER_1,
            engine.currentState().currentTier
        )
        assertEquals(
            "Money must not be deducted on a rejected unlock",
            Money(10_000_000L),
            engine.currentState().money
        )
    }

    @Test
    fun `unlockNextTier is no-op when total revenue is one cent below TIER_2 gate`() {
        // Off-by-one boundary: 499_999 ¢ is NOT enough; 500_000 ¢ is the gate
        val engine = newEngine()
        setMoney(engine, 10_000_000L)
        setRevenue(engine, 499_999L)

        engine.unlockNextTier()

        assertEquals(
            "Tier must remain TIER_1 one cent below the gate",
            ItemUnlockTier.TIER_1,
            engine.currentState().currentTier
        )
        assertEquals(
            "Money must not change on rejected unlock",
            Money(10_000_000L),
            engine.currentState().money
        )
    }

    // ── Funds guard ───────────────────────────────────────────────────────────

    @Test
    fun `unlockNextTier is no-op when money is one cent short of TIER_2 unlock cost`() {
        // Revenue gate is met but player is one cent short of the 100_000 ¢ unlock cost
        val engine = newEngine()
        setRevenue(engine, 500_000L)
        setMoney(engine, 99_999L)  // TIER_2.unlockCost = 100_000 ¢; 99_999 is one short

        engine.unlockNextTier()

        assertEquals(
            "Tier must remain TIER_1 when one cent short of unlock cost",
            ItemUnlockTier.TIER_1,
            engine.currentState().currentTier
        )
        assertEquals(
            "Money must not change on rejected unlock",
            Money(99_999L),
            engine.currentState().money
        )
    }

    // ── Successful unlock ─────────────────────────────────────────────────────

    @Test
    fun `unlockNextTier advances to TIER_2 and deducts exact unlock cost`() {
        // TIER_2.unlockCost = 100_000 ¢ → money: 500_000 - 100_000 = 400_000
        val engine = newEngine()
        setRevenue(engine, 500_000L)
        setMoney(engine, 500_000L)

        engine.unlockNextTier()

        assertEquals(
            "Tier must advance to TIER_2",
            ItemUnlockTier.TIER_2,
            engine.currentState().currentTier
        )
        assertEquals(
            "Money must decrease by exactly the TIER_2 unlock cost (100_000 ¢)",
            Money(400_000L),
            engine.currentState().money
        )
    }

    @Test
    fun `unlockNextTier succeeds when player has exactly the unlock cost and drains money to zero`() {
        // Boundary: player has exactly 100_000 ¢ — enough for TIER_2 but nothing left over
        val engine = newEngine()
        setRevenue(engine, 500_000L)
        setMoney(engine, 100_000L)  // exactly TIER_2.unlockCost

        engine.unlockNextTier()

        assertEquals(
            "Tier must advance to TIER_2 when money equals the exact unlock cost",
            ItemUnlockTier.TIER_2,
            engine.currentState().currentTier
        )
        assertEquals(
            "Money must be drained to exactly zero",
            Money(0L),
            engine.currentState().money
        )
    }

    // ── totalRevenue must never be affected ───────────────────────────────────

    @Test
    fun `unlockNextTier does not modify totalRevenue`() {
        val engine = newEngine()
        setRevenue(engine, 500_000L)
        setMoney(engine, 500_000L)

        engine.unlockNextTier()

        assertEquals(
            "totalRevenue must be unchanged after paying unlock cost",
            Money(500_000L),
            engine.currentState().totalRevenue
        )
    }

    // ── Max-tier guard ────────────────────────────────────────────────────────

    @Test
    fun `unlockNextTier is a no-op at max tier TIER_GM and makes no money deduction`() {
        val engine = newEngine()
        engine.state = engine.state.copy(currentTier = ItemUnlockTier.TIER_GM)
        setRevenue(engine, 99_000_000L)
        setMoney(engine, 99_000_000L)

        engine.unlockNextTier()

        assertEquals(
            "Tier must remain TIER_GM at max tier",
            ItemUnlockTier.TIER_GM,
            engine.currentState().currentTier
        )
        assertEquals(
            "Money must not be deducted when already at max tier",
            Money(99_000_000L),
            engine.currentState().money
        )
    }

    // ── Sequential unlock balance trace ───────────────────────────────────────

    @Test
    fun `sequential unlocks from TIER_1 to TIER_GM deduct correct cumulative costs`() {
        // Unlock costs:  TIER_2 = 100_000 ¢  |  TIER_3 = 400_000 ¢  |  TIER_GM = 2_000_000 ¢
        // Revenue must meet TIER_GM's gate (10_000_000 ¢) so all three calls succeed
        val engine = newEngine()
        setRevenue(engine, 10_000_000L)
        setMoney(engine, 50_000_000L)

        // ── TIER_1 → TIER_2 ──────────────────────────────────────────────────
        engine.unlockNextTier()
        assertEquals("After TIER_2 unlock", ItemUnlockTier.TIER_2, engine.currentState().currentTier)
        // 50_000_000 - 100_000 = 49_900_000
        assertEquals("Money after TIER_2 unlock", Money(49_900_000L), engine.currentState().money)

        // ── TIER_2 → TIER_3 ──────────────────────────────────────────────────
        engine.unlockNextTier()
        assertEquals("After TIER_3 unlock", ItemUnlockTier.TIER_3, engine.currentState().currentTier)
        // 49_900_000 - 400_000 = 49_500_000
        assertEquals("Money after TIER_3 unlock", Money(49_500_000L), engine.currentState().money)

        // ── TIER_3 → TIER_GM ─────────────────────────────────────────────────
        engine.unlockNextTier()
        assertEquals("After TIER_GM unlock", ItemUnlockTier.TIER_GM, engine.currentState().currentTier)
        // 49_500_000 - 2_000_000 = 47_500_000
        assertEquals("Money after TIER_GM unlock", Money(47_500_000L), engine.currentState().money)
    }

    // ── Revenue gate boundary (exact value) ──────────────────────────────────

    @Test
    fun `unlockNextTier succeeds at the exact TIER_2 revenue gate boundary of 500_000 cents`() {
        val engine = newEngine()
        setRevenue(engine, 500_000L)  // exactly the gate — should pass
        setMoney(engine, 10_000_000L)

        engine.unlockNextTier()

        assertEquals(
            "Tier must advance at the exact revenue gate boundary",
            ItemUnlockTier.TIER_2,
            engine.currentState().currentTier
        )
    }

    // ── Revenue accumulation via a real completed transaction ─────────────────

    @Test
    fun `totalRevenue increases by exactly the transaction totalEarned when a transaction completes`() {
        // Single-item engine so startTransaction() always produces a predictable basket
        val engine = newEngine(listOf(makeItem(id = 1, priceCents = 999)))

        // Open the store — startTransaction() requires storeState != CLOSED
        engine.state = engine.state.copy(storeState = StoreState.OPEN)
        engine.startTransaction()
        assertTrue("Transaction must be active after startTransaction()", engine.currentState().transactionActive)

        // Capture the expected revenue before ringing up
        val txEarned = engine.currentState().currentTransaction.totalEarned

        // Ring up every line to completion
        val lines = engine.currentState().currentTransaction.lines
        for (line in lines) {
            repeat(line.quantity) { engine.ringUpItem(line.itemId) }
        }

        // totalRevenue must equal the transaction's totalEarned exactly (no other revenue source)
        assertEquals(
            "totalRevenue must equal totalEarned from the first completed transaction",
            txEarned,
            engine.currentState().totalRevenue
        )
    }

    // ── totalRevenue must not be affected by spending ─────────────────────────

    @Test
    fun `totalRevenue is not decreased by buyItemToBackroom`() {
        val engine = newEngine(listOf(makeItem(id = 1, priceCents = 999)))
        setRevenue(engine, 600_000L)
        setMoney(engine, 500_000L)  // enough to cover the case-pack cost

        engine.buyItemToBackroom(1)

        assertEquals(
            "totalRevenue must not change when money is spent on inventory",
            Money(600_000L),
            engine.currentState().totalRevenue
        )
    }

    // ── No auto-advance ───────────────────────────────────────────────────────

    @Test
    fun `tier does not auto-advance when revenue gate is met without calling unlockNextTier`() {
        val engine = newEngine()
        // Revenue is above the TIER_2 gate — tier must remain TIER_1 until explicitly purchased
        setRevenue(engine, 600_000L)

        // No call to unlockNextTier()
        assertEquals(
            "Tier must stay TIER_1 until the player explicitly calls unlockNextTier()",
            ItemUnlockTier.TIER_1,
            engine.currentState().currentTier
        )
    }

    // ── Second call is idempotent when next gate is not yet met ───────────────

    @Test
    fun `second unlockNextTier call does not double-deduct when TIER_3 revenue gate is not met`() {
        val engine = newEngine()
        // Revenue meets TIER_2 gate (500_000) but NOT TIER_3 gate (2_000_000)
        setRevenue(engine, 500_000L)
        setMoney(engine, 500_000L)

        // First call: TIER_1 → TIER_2 (costs 100_000 ¢)
        engine.unlockNextTier()
        assertEquals("Tier after first unlock", ItemUnlockTier.TIER_2, engine.currentState().currentTier)
        assertEquals("Money after first unlock", Money(400_000L), engine.currentState().money)

        // Second call: revenue (500_000) < TIER_3 gate (2_000_000) → no-op
        engine.unlockNextTier()
        assertEquals(
            "Tier must remain TIER_2 when TIER_3 revenue gate is not met",
            ItemUnlockTier.TIER_2,
            engine.currentState().currentTier
        )
        assertEquals(
            "Money must not be further deducted on the rejected second call",
            Money(400_000L),
            engine.currentState().money
        )
    }
}
