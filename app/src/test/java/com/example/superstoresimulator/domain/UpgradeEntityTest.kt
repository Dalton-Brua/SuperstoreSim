package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.MoneyData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for upgradeEntity() functionality.
 *
 * Uses a [FakeItemDao] backed by an in-memory list instead of Mockito so the
 * tests are not affected by JDK / byte-buddy compatibility issues.
 *
 * Cost constants (all amounts in cents):
 *   CASHIER.cost              = 1_500 ¢  ($15.00)
 *   FAST_CASHIER.cost         = 10_000 ¢ ($100.00)
 *   STOCKER.cost              = 1_500 ¢  ($15.00)
 *   FAST_STOCKER.cost         = 10_000 ¢ ($100.00)
 *   FRESH_HANDLER.cost        = 1_500 ¢  ($15.00)
 *   FAST_FRESH_HANDLER.cost   = 10_000 ¢ ($100.00)
 *   (CUSTOMER_SERVICE_REP was removed)
 *
 * NON-VACUOUSNESS PRINCIPLE:
 * Every money assertion uses a concrete expected value derived by independent
 * arithmetic from the starting balance — never just comparing a value to itself.
 * This means: if the implementation accidentally deducts any wrong amount (even
 * during a "no-op" path), the assertion will fail.
 */
class UpgradeEntityTest {

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
            id = "item_1",
            name = "Item 1",
            price = MoneyData(cents = 999),
            description = "Test item 1",
            unitCost = MoneyData(cents = 500),
            category = ItemCategory.GROCERY,
            casePack = 6
        )
    )

    // ── setUp ─────────────────────────────────────────────────────────────────

    @Before
    fun setUp() {
        val cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        gameEngine = GameEngine(cache)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Directly sets the engine's money balance via the internal setter. */
    private fun setMoney(cents: Long) {
        gameEngine.state = gameEngine.state.copy(money = Money(cents))
    }

    /**
     * Hires one entity and returns its stable registry ID.
     *
     * The ID is read from [HiredEntityRegistry.getNextEntityId] BEFORE the hire
     * call — the entity is assigned exactly that ID when created — so no
     * arithmetic on a post-hire state is needed.
     */
    private fun hire(def: EntityDef, type: EntityType): Int {
        val assignedId = gameEngine.currentState().hiredEntityRegistry.getNextEntityId()
        gameEngine.hireEntity(def, type)
        return assignedId
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. BASIC UPGRADE PATHS
    //    Verifies the happy path for each entity type that has an upgrade.
    //    Money assertions use concrete expected values traced step-by-step so a
    //    wrong deduction amount (too little or too much) will fail the test.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testCashierUpgradesToFastCashier() {
        // Balance trace: 20_000 → hire CASHIER −1_500 → 18_500 → upgrade −10_000 → 8_500
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)

        assertEquals("Setup: entity must start as CASHIER",
            EntityDef.CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Setup: balance after hire must be exactly 18_500¢",
            Money(18_500), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id)

        assertEquals("Entity must be FAST_CASHIER after upgrade",
            EntityDef.FAST_CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Balance must be exactly 8_500¢ after deducting FAST_CASHIER.cost (10_000¢)",
            Money(8_500), gameEngine.currentState().money)
    }

    @Test
    fun testStockerUpgradesToFastStocker() {
        // Balance trace: 20_000 → hire STOCKER −1_500 → 18_500 → upgrade −10_000 → 8_500
        setMoney(20_000L)
        val id = hire(EntityDef.STOCKER, EntityType.STOCKERS)

        assertEquals("Setup: entity must start as STOCKER",
            EntityDef.STOCKER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Setup: balance after hire must be exactly 18_500¢",
            Money(18_500), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id)

        assertEquals("Entity must be FAST_STOCKER after upgrade",
            EntityDef.FAST_STOCKER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Balance must be exactly 8_500¢ after deducting FAST_STOCKER.cost (10_000¢)",
            Money(8_500), gameEngine.currentState().money)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. EXACT COST DEDUCTION
    //    Independently compute the expected balance so any deduction error fails.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testUpgradeDeductsExactCashierCost() {
        // Expected balance = start − CASHIER.cost − FAST_CASHIER.cost
        //                  = 20_000 − 1_500 − 10_000 = 8_500¢
        val start = 20_000L
        setMoney(start)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)

        val expectedAfterHire = start - EntityDef.CASHIER.cost.cents         // 18_500
        assertEquals("Balance after hire", Money(expectedAfterHire), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id)

        val expectedAfterUpgrade = expectedAfterHire - EntityDef.FAST_CASHIER.cost.cents  // 8_500
        assertEquals(
            "Balance after upgrade must equal start − CASHIER.cost − FAST_CASHIER.cost",
            Money(expectedAfterUpgrade), gameEngine.currentState().money
        )
    }

    @Test
    fun testUpgradeDeductsExactStockerCost() {
        // Expected balance = 20_000 − 1_500 − 10_000 = 8_500¢
        val start = 20_000L
        setMoney(start)
        val id = hire(EntityDef.STOCKER, EntityType.STOCKERS)

        val expectedAfterHire = start - EntityDef.STOCKER.cost.cents         // 18_500
        gameEngine.upgradeEntity(id)

        val expectedAfterUpgrade = expectedAfterHire - EntityDef.FAST_STOCKER.cost.cents  // 8_500
        assertEquals(
            "Balance after upgrade must equal start − STOCKER.cost − FAST_STOCKER.cost",
            Money(expectedAfterUpgrade), gameEngine.currentState().money
        )
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. INSUFFICIENT FUNDS — UPGRADE BLOCKED
    //    Guard clause: if balance < upgrade cost, state must be unchanged.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testUpgradeBlockedWhenBalanceIsZero() {
        // Give exactly enough to hire but nothing left for the upgrade.
        // CASHIER.cost = 1_500¢; FAST_CASHIER.cost = 10_000¢ — cannot afford.
        setMoney(EntityDef.CASHIER.cost.cents)      // exactly 1_500¢
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)

        assertEquals("Setup: balance must be exactly 0 after hire",
            Money(0), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id)

        assertEquals("Entity must remain CASHIER — upgrade unaffordable",
            EntityDef.CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Balance must remain exactly 0 — no deduction on rejected upgrade",
            Money(0), gameEngine.currentState().money)
    }

    @Test
    fun testUpgradeBlockedWhenOneCentShort() {
        // Set balance to exactly FAST_CASHIER.cost − 1 = 9_999¢ so we are one
        // cent short.  Upgrade must be rejected; entity and balance unchanged.
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)
        gameEngine.state = gameEngine.state.copy(money = Money(EntityDef.FAST_CASHIER.cost.cents - 1))

        gameEngine.upgradeEntity(id)

        assertEquals("Entity must remain CASHIER when 1 cent short of upgrade cost",
            EntityDef.CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Balance must remain exactly 9_999¢ — nothing deducted on rejection",
            Money(EntityDef.FAST_CASHIER.cost.cents - 1),
            gameEngine.currentState().money)
    }

    @Test
    fun testUpgradeSucceedsWithExactlyEnoughMoney() {
        // Give start = CASHIER.cost + FAST_CASHIER.cost = 11_500¢.
        // After hire: 10_000¢ remain — precisely the upgrade cost.
        // After upgrade: 0¢.  Any under- or over-deduction will fail.
        val exactStart = EntityDef.CASHIER.cost.cents + EntityDef.FAST_CASHIER.cost.cents  // 11_500
        setMoney(exactStart)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)

        assertEquals("Setup: balance must be exactly 10_000¢ after hire",
            Money(10_000), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id)

        assertEquals("Entity must be FAST_CASHIER when balance was exactly enough",
            EntityDef.FAST_CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Balance must be exactly 0¢ — spent every last cent on the upgrade",
            Money(0), gameEngine.currentState().money)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. MAX-LEVEL / NO FURTHER UPGRADE
    //    Upgrading an already-max-level entity must be a true no-op.
    //
    //    WHY THESE TESTS ARE NON-VACUOUS:
    //    The original approach compared `moneyBefore == moneyAfter`, which is
    //    always true because the engine computes `cost = Money(0)` and then
    //    executes `money - Money(0) = money`.  That comparison can never fail
    //    regardless of what the code does.
    //
    //    Here we instead assert against a CONCRETE expected value (18_500¢)
    //    derived independently.  If the implementation accidentally deducts any
    //    amount — even 1¢ — from the balance during a "no-op" upgrade, the
    //    assertion will catch it.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testUpgradingMaxLevelCashierIsNoOp() {
        // Balance trace: 30_000 → hire −1_500 → 28_500 → upgrade −10_000 → 18_500
        //                → 2nd upgrade attempt → must remain 18_500 exactly.
        setMoney(30_000L)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)
        gameEngine.upgradeEntity(id)    // first upgrade: CASHIER → FAST_CASHIER

        assertEquals("Setup: entity must be FAST_CASHIER after first upgrade",
            EntityDef.FAST_CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Setup: balance must be exactly 18_500¢ after first upgrade",
            Money(18_500), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id)    // second attempt — max level, should be no-op

        assertEquals("Max-level FAST_CASHIER must not change entity definition",
            EntityDef.FAST_CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Balance must remain exactly 18_500¢ — no amount subtracted at max level",
            Money(18_500), gameEngine.currentState().money)
    }

    @Test
    fun testUpgradingMaxLevelStockerIsNoOp() {
        // Balance trace: 30_000 → hire −1_500 → 28_500 → upgrade −10_000 → 18_500
        //                → 2nd upgrade attempt → must remain 18_500 exactly.
        setMoney(30_000L)
        val id = hire(EntityDef.STOCKER, EntityType.STOCKERS)
        gameEngine.upgradeEntity(id)    // STOCKER → FAST_STOCKER

        assertEquals("Setup: entity must be FAST_STOCKER after first upgrade",
            EntityDef.FAST_STOCKER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Setup: balance must be exactly 18_500¢ after first upgrade",
            Money(18_500), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id)    // second attempt — max level

        assertEquals("Max-level FAST_STOCKER must not change entity definition",
            EntityDef.FAST_STOCKER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Balance must remain exactly 18_500¢ — no amount subtracted at max level",
            Money(18_500), gameEngine.currentState().money)
    }

    /*
     * Test disabled: CUSTOMER_SERVICE_REP entity has been removed from the game.
     * This test is preserved for reference but commented out.
     *
    @Test
    fun testCustomerServiceRepHasNoUpgradePath() {
        // CUSTOMER_SERVICE_REP has nextUpgrade = null from the moment it is hired
        // (unlike CASHIER which gains null only after being promoted to FAST_CASHIER).
        // Balance trace: 10_000 → hire CSR −5_000 → 5_000 → upgrade attempt → 5_000 exactly.
        setMoney(10_000L)
        val id = hire(EntityDef.CUSTOMER_SERVICE_REP, EntityType.CUSTOMER_SERVICE_REPRESENTATIVES)

        assertEquals("Setup: entity must be CUSTOMER_SERVICE_REP",
            EntityDef.CUSTOMER_SERVICE_REP,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Setup: balance must be exactly 5_000¢ after hire",
            Money(5_000), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id)    // no upgrade path exists from the start

        assertEquals("CSR entity definition must be unchanged after upgrade attempt",
            EntityDef.CUSTOMER_SERVICE_REP,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Balance must be exactly 5_000¢ — upgrade of CSR must not deduct money",
            Money(5_000), gameEngine.currentState().money)
    }
    */

    // ════════════════════════════════════════════════════════════════════════
    // 5. ENTITY PROPERTIES PRESERVED THROUGH UPGRADE
    //    Only entityDefinition should change; id, name, and trait must survive.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testUpgradePreservesEntityName() {
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)
        val nameBeforeUpgrade = gameEngine.currentState().hiredEntityRegistry.getById(id).name

        assertFalse("Setup: hired entity must have a non-blank name", nameBeforeUpgrade.isBlank())

        gameEngine.upgradeEntity(id)

        assertEquals("Entity name must be identical before and after upgrade",
            nameBeforeUpgrade,
            gameEngine.currentState().hiredEntityRegistry.getById(id).name)
    }

    @Test
    fun testUpgradePreservesEntityTrait() {
        setMoney(20_000L)
        val id = hire(EntityDef.STOCKER, EntityType.STOCKERS)
        val traitBeforeUpgrade = gameEngine.currentState().hiredEntityRegistry.getById(id).trait

        assertTrue("Setup: trait must be a valid EntityTrait value",
            EntityTrait.entries.contains(traitBeforeUpgrade))

        gameEngine.upgradeEntity(id)

        assertEquals("Entity trait must be identical before and after upgrade",
            traitBeforeUpgrade,
            gameEngine.currentState().hiredEntityRegistry.getById(id).trait)
    }

    @Test
    fun testUpgradePreservesEntityId() {
        // Confirm the entity can still be looked up by the same ID after upgrade,
        // and that the ID field on the returned object is unchanged.
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)

        assertEquals("Setup: entity ID must match the assigned hire ID",
            id,
            gameEngine.currentState().hiredEntityRegistry.getById(id).id)

        gameEngine.upgradeEntity(id)

        val upgradedEntity = gameEngine.currentState().hiredEntityRegistry.getById(id)
        assertEquals("Entity ID must not change after upgrade",
            id, upgradedEntity.id)
        assertEquals("getById(id) must return the upgraded entity definition",
            EntityDef.FAST_CASHIER, upgradedEntity.entityDefinition)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. REGISTRY INTEGRITY — ONLY THE TARGETED ENTITY IS AFFECTED
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testUpgradeDoesNotChangeRegistryCount() {
        setMoney(50_000L)
        val id1 = hire(EntityDef.CASHIER, EntityType.CASHIERS)
        hire(EntityDef.CASHIER, EntityType.CASHIERS)

        assertEquals("Setup: registry must contain exactly 2 entities",
            2, gameEngine.currentState().hiredEntityRegistry.totalCount())

        gameEngine.upgradeEntity(id1)

        assertEquals("Total entity count must be unchanged after upgrade",
            2, gameEngine.currentState().hiredEntityRegistry.totalCount())
    }

    @Test
    fun testUpgradeOnlyAffectsTargetedEntity() {
        // Hire two cashiers and a stocker; upgrade only the second cashier.
        // The first cashier and the stocker must remain at their original definitions.
        setMoney(100_000L)
        val id1 = hire(EntityDef.CASHIER, EntityType.CASHIERS)
        val id2 = hire(EntityDef.CASHIER, EntityType.CASHIERS)
        val id3 = hire(EntityDef.STOCKER, EntityType.STOCKERS)

        gameEngine.upgradeEntity(id2)   // upgrade only id2

        assertEquals("id1 (non-upgraded cashier) must remain CASHIER",
            EntityDef.CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id1).entityDefinition)
        assertEquals("id2 (upgraded cashier) must be FAST_CASHIER",
            EntityDef.FAST_CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id2).entityDefinition)
        assertEquals("id3 (stocker) must remain STOCKER — upgrade must not bleed across entities",
            EntityDef.STOCKER,
            gameEngine.currentState().hiredEntityRegistry.getById(id3).entityDefinition)
    }

    @Test
    fun testUpgradingOneEntityDeductsExactBalanceForThatEntityOnly() {
        // Hire three entities; upgrade only id2.
        // Balance trace: 100_000 − 1_500(id1) − 1_500(id2) − 1_500(id3) = 95_500
        //                → upgrade id2: 95_500 − 10_000 = 85_500
        // Any extra deduction (for id1 or id3) would move balance away from 85_500.
        setMoney(100_000L)
        hire(EntityDef.CASHIER, EntityType.CASHIERS)
        val id2 = hire(EntityDef.CASHIER, EntityType.CASHIERS)
        hire(EntityDef.STOCKER, EntityType.STOCKERS)

        assertEquals("Setup: balance after 3 hires must be exactly 95_500¢",
            Money(95_500), gameEngine.currentState().money)

        gameEngine.upgradeEntity(id2)

        assertEquals("Balance must be exactly 85_500¢ — only FAST_CASHIER.cost (10_000¢) deducted",
            Money(85_500), gameEngine.currentState().money)
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. INVALID / EDGE CASES
    // ════════════════════════════════════════════════════════════════════════

    @Test(expected = NoSuchElementException::class)
    fun testUpgradeNonExistentEntityThrows() {
        // Empty registry — getById(9999) must throw NoSuchElementException.
        gameEngine.upgradeEntity(9999)
    }

    @Test(expected = NoSuchElementException::class)
    fun testUpgradeFiredEntityThrows() {
        // Hire then immediately fire the entity; the ID is no longer in the registry.
        // Attempting to upgrade it must throw because getById can no longer find it.
        setMoney(10_000L)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)
        gameEngine.fireEntity(id)

        gameEngine.upgradeEntity(id)    // must throw NoSuchElementException
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. FULL UPGRADE SEQUENCES
    //    Each step asserts BOTH entity definition AND exact concrete balance.
    // ════════════════════════════════════════════════════════════════════════

    @Test
    fun testFullCashierUpgradeSequence() {
        // Balance trace:
        //   30_000 → hire −1_500 → 28_500 → upgrade −10_000 → 18_500 → no-op → 18_500
        setMoney(30_000L)
        val id = hire(EntityDef.CASHIER, EntityType.CASHIERS)

        // Step 0: baseline after hire
        assertEquals("Step 0: entity must be CASHIER",
            EntityDef.CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Step 0: balance must be exactly 28_500¢",
            Money(28_500), gameEngine.currentState().money)

        // Step 1: first upgrade — CASHIER → FAST_CASHIER, deducts 10_000¢
        gameEngine.upgradeEntity(id)
        assertEquals("Step 1: entity must be FAST_CASHIER",
            EntityDef.FAST_CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Step 1: balance must be exactly 18_500¢",
            Money(18_500), gameEngine.currentState().money)

        // Step 2: second upgrade attempt on max-level entity — must be a no-op
        gameEngine.upgradeEntity(id)
        assertEquals("Step 2: entity must remain FAST_CASHIER",
            EntityDef.FAST_CASHIER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Step 2: balance must remain exactly 18_500¢ — no deduction at max level",
            Money(18_500), gameEngine.currentState().money)
    }

    @Test
    fun testFullStockerUpgradeSequence() {
        // Balance trace:
        //   30_000 → hire −1_500 → 28_500 → upgrade −10_000 → 18_500 → no-op → 18_500
        setMoney(30_000L)
        val id = hire(EntityDef.STOCKER, EntityType.STOCKERS)

        // Step 0: baseline after hire
        assertEquals("Step 0: entity must be STOCKER",
            EntityDef.STOCKER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Step 0: balance must be exactly 28_500¢",
            Money(28_500), gameEngine.currentState().money)

        // Step 1: first upgrade — STOCKER → FAST_STOCKER, deducts 10_000¢
        gameEngine.upgradeEntity(id)
        assertEquals("Step 1: entity must be FAST_STOCKER",
            EntityDef.FAST_STOCKER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Step 1: balance must be exactly 18_500¢",
            Money(18_500), gameEngine.currentState().money)

        // Step 2: second upgrade attempt on max-level entity — must be a no-op
        gameEngine.upgradeEntity(id)
        assertEquals("Step 2: entity must remain FAST_STOCKER",
            EntityDef.FAST_STOCKER,
            gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals("Step 2: balance must remain exactly 18_500¢ — no deduction at max level",
            Money(18_500), gameEngine.currentState().money)
    }
}
