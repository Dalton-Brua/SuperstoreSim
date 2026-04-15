package com.example.superstoresimulator.domain.progression

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ProgressionManager.unlockNextTier].
 *
 * [ProgressionManager] is a pure sub-system: it receives a [GameState] and returns
 * a new [GameState]. Tests construct [GameState] directly with only the fields
 * relevant to progression — no FakeItemDao or GameEngine required.
 *
 * Actual tier thresholds (values are cumulative revenue in cents):
 *   TIER_1 : 0 ¢          — unlock cost: $0
 *   TIER_2 : 500_000 ¢    — unlock cost: 100_000 ¢  ($1,000)
 *   TIER_3 : 2_000_000 ¢  — unlock cost: 400_000 ¢  ($4,000)
 *   TIER_GM: 10_000_000 ¢ — unlock cost: 2_000_000 ¢ ($20,000)
 *
 * Covers:
 *  - No-op when already at the top tier (TIER_GM)
 *  - No-op when revenue gate is not yet met
 *  - No-op when player cannot afford the unlock cost
 *  - Exact boundary: money exactly equal to unlock cost succeeds
 *  - One cent below unlock cost is refused
 *  - Tier advances and unlock cost is deducted on success
 *  - [GameState.totalRevenue] is never modified (unlock is a cash purchase, not revenue)
 *  - All four tier transitions are handled correctly
 */
class ProgressionManagerTest {

    private lateinit var manager: ProgressionManager

    @Before
    fun setUp() {
        manager = ProgressionManager()
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Builds a [GameState] pre-loaded with exactly the fields that matter for
     * progression: [currentTier], [totalRevenue], and [money].
     */
    private fun stateWith(
        tier: ItemUnlockTier,
        revenueCents: Long,
        moneyCents: Long,
    ): GameState = GameState(
        currentTier = tier,
        totalRevenue = Money(revenueCents),
        money = Money(moneyCents),
    )

    // ── No-op guards ─────────────────────────────────────────────────────────

    @Test
    fun `returns same state unchanged when already at top tier TIER_GM`() {
        val state = stateWith(
            tier = ItemUnlockTier.TIER_GM,
            revenueCents = 999_999_999L,
            moneyCents = 999_999_999L,
        )
        val result = manager.unlockNextTier(state)
        assertEquals(ItemUnlockTier.TIER_GM, result.currentTier)
        assertEquals(Money(999_999_999L), result.money)
    }

    @Test
    fun `returns same state unchanged when revenue gate is not met`() {
        // TIER_2 gate is 500_000 ¢; we are one cent short
        val state = stateWith(
            tier = ItemUnlockTier.TIER_1,
            revenueCents = 499_999L,
            moneyCents = 10_000_000L,  // plenty of cash — blocked by revenue, not money
        )
        val result = manager.unlockNextTier(state)
        assertEquals(ItemUnlockTier.TIER_1, result.currentTier)
        assertEquals(Money(10_000_000L), result.money)
    }

    @Test
    fun `returns same state unchanged when revenue gate is exactly zero`() {
        // Fresh engine state — revenue gate for TIER_2 is 500_000 ¢
        val state = stateWith(
            tier = ItemUnlockTier.TIER_1,
            revenueCents = 0L,
            moneyCents = 10_000_000L,
        )
        val result = manager.unlockNextTier(state)
        assertEquals(ItemUnlockTier.TIER_1, result.currentTier)
    }

    @Test
    fun `returns same state unchanged when player cannot afford the unlock cost`() {
        // TIER_2 costs 100_000 ¢; player has one cent less
        val state = stateWith(
            tier = ItemUnlockTier.TIER_1,
            revenueCents = 600_000L,   // revenue gate met
            moneyCents = 99_999L,      // one cent short of the 100_000 ¢ cost
        )
        val result = manager.unlockNextTier(state)
        assertEquals(ItemUnlockTier.TIER_1, result.currentTier)
        assertEquals(Money(99_999L), result.money)
    }

    // ── Boundary: exact cost succeeds ────────────────────────────────────────

    @Test
    fun `succeeds when money equals the unlock cost exactly`() {
        // TIER_2 costs 100_000 ¢; player has exactly that amount
        val state = stateWith(
            tier = ItemUnlockTier.TIER_1,
            revenueCents = 600_000L,
            moneyCents = 100_000L,
        )
        val result = manager.unlockNextTier(state)
        assertEquals(ItemUnlockTier.TIER_2, result.currentTier)
        assertEquals(Money.ZERO, result.money)   // all cash spent on the unlock
    }

    // ── Successful unlock ────────────────────────────────────────────────────

    @Test
    fun `advances tier from TIER_1 to TIER_2 and deducts unlock cost`() {
        val unlockCost = ItemUnlockTier.TIER_2.unlockCost   // 100_000 ¢
        val startMoney = Money(500_000L)
        val state = stateWith(
            tier = ItemUnlockTier.TIER_1,
            revenueCents = 600_000L,
            moneyCents = startMoney.cents,
        )

        val result = manager.unlockNextTier(state)

        assertEquals(ItemUnlockTier.TIER_2, result.currentTier)
        assertEquals(startMoney - unlockCost, result.money)
    }

    @Test
    fun `advances tier from TIER_2 to TIER_3 and deducts unlock cost`() {
        val unlockCost = ItemUnlockTier.TIER_3.unlockCost   // 400_000 ¢
        val startMoney = Money(1_000_000L)
        val state = stateWith(
            tier = ItemUnlockTier.TIER_2,
            revenueCents = 2_500_000L,   // above TIER_3 gate of 2_000_000 ¢
            moneyCents = startMoney.cents,
        )

        val result = manager.unlockNextTier(state)

        assertEquals(ItemUnlockTier.TIER_3, result.currentTier)
        assertEquals(startMoney - unlockCost, result.money)
    }

    @Test
    fun `advances tier from TIER_3 to TIER_GM and deducts unlock cost`() {
        val unlockCost = ItemUnlockTier.TIER_GM.unlockCost  // 2_000_000 ¢
        val startMoney = Money(5_000_000L)
        val state = stateWith(
            tier = ItemUnlockTier.TIER_3,
            revenueCents = 11_000_000L,  // above TIER_GM gate of 10_000_000 ¢
            moneyCents = startMoney.cents,
        )

        val result = manager.unlockNextTier(state)

        assertEquals(ItemUnlockTier.TIER_GM, result.currentTier)
        assertEquals(startMoney - unlockCost, result.money)
    }

    // ── totalRevenue is not affected ─────────────────────────────────────────

    @Test
    fun `totalRevenue is unchanged after a successful tier unlock`() {
        val revenueBeforeUnlock = Money(600_000L)
        val state = stateWith(
            tier = ItemUnlockTier.TIER_1,
            revenueCents = revenueBeforeUnlock.cents,
            moneyCents = 500_000L,
        )

        val result = manager.unlockNextTier(state)

        assertEquals(
            "totalRevenue must not change when purchasing a tier unlock",
            revenueBeforeUnlock,
            result.totalRevenue,
        )
    }

    @Test
    fun `totalRevenue is unchanged after a failed unlock attempt`() {
        val revenueBeforeAttempt = Money(100L)  // below TIER_2 gate
        val state = stateWith(
            tier = ItemUnlockTier.TIER_1,
            revenueCents = revenueBeforeAttempt.cents,
            moneyCents = 10_000_000L,
        )

        val result = manager.unlockNextTier(state)

        assertEquals(revenueBeforeAttempt, result.totalRevenue)
    }

    // ── Only the relevant fields change ──────────────────────────────────────

    @Test
    fun `unrelated GameState fields are not modified on successful unlock`() {
        val state = stateWith(
            tier = ItemUnlockTier.TIER_1,
            revenueCents = 600_000L,
            moneyCents = 500_000L,
        ).copy(storeName = "My Store")

        val result = manager.unlockNextTier(state)

        assertEquals("Unrelated fields must remain identical", "My Store", result.storeName)
    }
}

