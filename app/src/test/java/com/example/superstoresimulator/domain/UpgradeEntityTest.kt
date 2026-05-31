package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.Tier
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
 * Tier system: BASE → FAST (10,000¢) → MANAGER (50,000¢) → error (max tier)
 * entityDefinition does NOT change on upgrade; only tier changes.
 *
 * Cost constants (all in cents):
 *   CASHIER.cost (hire)   = 1,500¢
 *   STOCKER.cost (hire)   = 1,500¢
 *   BASE→FAST upgrade     = 10,000¢
 *   FAST→MANAGER upgrade  = 50,000¢
 */
class UpgradeEntityTest {

    private lateinit var gameEngine: GameEngine

    private class FakeItemDao(private val items: List<Item>) : ItemDao {
        override suspend fun getItemById(itemId: String): Item? = items.firstOrNull { it.id == itemId }
        override suspend fun getAllItems(): List<Item> = items
        override suspend fun insertItem(item: Item) {}
        override suspend fun deleteItem(itemId: String) {}
        override suspend fun deleteAll() {}
        override suspend fun getItemName(itemId: String): String? = items.firstOrNull { it.id == itemId }?.name
        override suspend fun getAllItemsWithNames(): List<ItemWithName> = items.map { ItemWithName(it.id, it.name) }
        override suspend fun getItemsByIds(itemIds: List<String>): List<Item> = items.filter { it.id in itemIds }
        override suspend fun insertBatch(items: List<Item>) {}
        override suspend fun getItemCount(): Int = items.size
        override suspend fun getItemsByCategory(category: String): List<Item> = items.filter { it.category.name == category }
        override suspend fun searchItemsByName(searchTerm: String): List<Item> = items.filter { it.name.contains(searchTerm, ignoreCase = true) }
        override suspend fun getItemsForInventory(itemIds: List<String>): List<Item> = items.filter { it.id in itemIds }
    }

    private val testItems = listOf(
        Item(
            id = "item_1", name = "Item 1", price = MoneyData(cents = 999),
            description = "Test item 1", unitCost = MoneyData(cents = 500),
            category = ItemCategory.GROCERY, casePack = 6
        )
    )

    @Before
    fun setUp() {
        val cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        gameEngine = GameEngine(cache)
    }

    private fun setMoney(cents: Long) {
        gameEngine.state = gameEngine.state.copy(money = Money(cents))
    }

    private fun hire(def: EntityDef): Int {
        val assignedId = gameEngine.currentState().hiredEntityRegistry.getNextEntityId()
        gameEngine.hireEntity(def)
        return assignedId
    }

    private fun tierOf(id: Int): Tier =
        gameEngine.currentState().hiredEntityRegistry.getById(id).tier

    // ── 1. BASIC UPGRADE PATHS ────────────────────────────────────────────────

    @Test
    fun testCashierUpgradesToFastTier() {
        // 20_000 → hire −1_500 → 18_500 → upgrade −10_000 → 8_500
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER)
        assertEquals(Tier.BASE, tierOf(id))
        assertEquals(Money(18_500), gameEngine.currentState().money)

        gameEngine.promoteEntity(id)

        assertEquals(Tier.FAST, tierOf(id))
        assertEquals(EntityDef.CASHIER, gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals(Money(8_500), gameEngine.currentState().money)
    }

    @Test
    fun testStockerUpgradesToFastTier() {
        setMoney(20_000L)
        val id = hire(EntityDef.STOCKER)
        assertEquals(Tier.BASE, tierOf(id))

        gameEngine.promoteEntity(id)

        assertEquals(Tier.FAST, tierOf(id))
        assertEquals(EntityDef.STOCKER, gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
        assertEquals(Money(8_500), gameEngine.currentState().money)
    }

    @Test
    fun testFastTierUpgradesToManagerTier() {
        // 80_000 → hire −1_500 → 78_500 → BASE→FAST −10_000 → 68_500 → FAST→MANAGER −50_000 → 18_500
        setMoney(80_000L)
        val id = hire(EntityDef.CASHIER)
        gameEngine.promoteEntity(id) // BASE → FAST
        assertEquals(Tier.FAST, tierOf(id))
        assertEquals(Money(68_500), gameEngine.currentState().money)

        gameEngine.promoteEntity(id) // FAST → MANAGER
        assertEquals(Tier.MANAGER, tierOf(id))
        assertEquals(Money(18_500), gameEngine.currentState().money)
    }

    // ── 2. EXACT COST DEDUCTION ───────────────────────────────────────────────

    @Test
    fun testUpgradeDeductsExactBaseToFastCost() {
        val start = 20_000L
        setMoney(start)
        val id = hire(EntityDef.CASHIER)
        val afterHire = start - EntityDef.CASHIER.cost.cents // 18_500
        assertEquals(Money(afterHire), gameEngine.currentState().money)

        gameEngine.promoteEntity(id)
        assertEquals(Money(afterHire - 10_000), gameEngine.currentState().money)
    }

    // ── 3. INSUFFICIENT FUNDS — UPGRADE BLOCKED ───────────────────────────────

    @Test
    fun testUpgradeBlockedWhenBalanceIsZero() {
        setMoney(EntityDef.CASHIER.cost.cents) // exactly 1_500¢
        val id = hire(EntityDef.CASHIER)
        assertEquals(Money(0), gameEngine.currentState().money)

        gameEngine.promoteEntity(id)

        assertEquals("Tier must stay BASE — upgrade unaffordable", Tier.BASE, tierOf(id))
        assertEquals(Money(0), gameEngine.currentState().money)
    }

    @Test
    fun testUpgradeBlockedWhenOneCentShort() {
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER)
        gameEngine.state = gameEngine.state.copy(money = Money(10_000 - 1)) // 1¢ short

        gameEngine.promoteEntity(id)

        assertEquals(Tier.BASE, tierOf(id))
        assertEquals(Money(9_999), gameEngine.currentState().money)
    }

    @Test
    fun testUpgradeSucceedsWithExactlyEnoughMoney() {
        val exactStart = EntityDef.CASHIER.cost.cents + 10_000L // 11_500¢
        setMoney(exactStart)
        val id = hire(EntityDef.CASHIER)
        assertEquals(Money(10_000), gameEngine.currentState().money)

        gameEngine.promoteEntity(id)

        assertEquals(Tier.FAST, tierOf(id))
        assertEquals(Money(0), gameEngine.currentState().money)
    }

    // ── 4. MAX-TIER UPGRADE IS NO-OP ──────────────────────────────────────────

    @Test
    fun testUpgradingMaxTierIsNoOp() {
        // 100_000 → hire −1_500 → BASE→FAST −10_000 → FAST→MANAGER −50_000 → 38_500 → no-op
        setMoney(100_000L)
        val id = hire(EntityDef.CASHIER)
        gameEngine.promoteEntity(id) // BASE → FAST
        gameEngine.promoteEntity(id) // FAST → MANAGER
        assertEquals(Tier.MANAGER, tierOf(id))
        assertEquals(Money(38_500), gameEngine.currentState().money)

        gameEngine.promoteEntity(id) // should be no-op (error in upgradeCost, so upgradeEntity guard must fire)

        assertEquals(Tier.MANAGER, tierOf(id))
        assertEquals(Money(38_500), gameEngine.currentState().money)
    }

    // ── 5. ENTITY PROPERTIES PRESERVED THROUGH UPGRADE ────────────────────────

    @Test
    fun testUpgradePreservesEntityName() {
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER)
        val nameBefore = gameEngine.currentState().hiredEntityRegistry.getById(id).name
        assertFalse(nameBefore.isBlank())

        gameEngine.promoteEntity(id)

        assertEquals(nameBefore, gameEngine.currentState().hiredEntityRegistry.getById(id).name)
    }

    @Test
    fun testUpgradePreservesEntityTrait() {
        setMoney(20_000L)
        val id = hire(EntityDef.STOCKER)
        val traitBefore = gameEngine.currentState().hiredEntityRegistry.getById(id).trait
        assertTrue(EntityTrait.entries.contains(traitBefore))

        gameEngine.promoteEntity(id)

        assertEquals(traitBefore, gameEngine.currentState().hiredEntityRegistry.getById(id).trait)
    }

    @Test
    fun testUpgradePreservesEntityId() {
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER)
        gameEngine.promoteEntity(id)
        assertEquals(id, gameEngine.currentState().hiredEntityRegistry.getById(id).id)
    }

    @Test
    fun testUpgradePreservesEntityDef() {
        setMoney(20_000L)
        val id = hire(EntityDef.CASHIER)
        gameEngine.promoteEntity(id)
        assertEquals(EntityDef.CASHIER, gameEngine.currentState().hiredEntityRegistry.getById(id).entityDefinition)
    }

    // ── 6. REGISTRY INTEGRITY ─────────────────────────────────────────────────

    @Test
    fun testUpgradeDoesNotChangeRegistryCount() {
        setMoney(50_000L)
        val id1 = hire(EntityDef.CASHIER)
        hire(EntityDef.CASHIER)
        assertEquals(2, gameEngine.currentState().hiredEntityRegistry.totalCount())

        gameEngine.promoteEntity(id1)

        assertEquals(2, gameEngine.currentState().hiredEntityRegistry.totalCount())
    }

    @Test
    fun testUpgradeOnlyAffectsTargetedEntity() {
        setMoney(100_000L)
        val id1 = hire(EntityDef.CASHIER)
        val id2 = hire(EntityDef.CASHIER)
        val id3 = hire(EntityDef.STOCKER)

        gameEngine.promoteEntity(id2)

        assertEquals(Tier.BASE, tierOf(id1))
        assertEquals(Tier.FAST, tierOf(id2))
        assertEquals(Tier.BASE, tierOf(id3))
    }

    @Test
    fun testUpgradingOneEntityDeductsBalanceForThatEntityOnly() {
        // 100_000 − 1_500(id1) − 1_500(id2) − 1_500(id3) = 95_500 → upgrade id2 −10_000 = 85_500
        setMoney(100_000L)
        hire(EntityDef.CASHIER)
        val id2 = hire(EntityDef.CASHIER)
        hire(EntityDef.STOCKER)
        assertEquals(Money(95_500), gameEngine.currentState().money)

        gameEngine.promoteEntity(id2)
        assertEquals(Money(85_500), gameEngine.currentState().money)
    }

    // ── 7. INVALID / EDGE CASES ───────────────────────────────────────────────

    @Test(expected = NoSuchElementException::class)
    fun testUpgradeNonExistentEntityThrows() {
        gameEngine.promoteEntity(9999)
    }

    @Test(expected = NoSuchElementException::class)
    fun testUpgradeFiredEntityThrows() {
        setMoney(10_000L)
        val id = hire(EntityDef.CASHIER)
        gameEngine.fireEntity(id)
        gameEngine.promoteEntity(id)
    }

    // ── 8. FULL UPGRADE SEQUENCES ─────────────────────────────────────────────

    @Test
    fun testFullCashierUpgradeSequence() {
        // 100_000 → hire −1_500 → 98_500 → BASE→FAST −10_000 → 88_500 → FAST→MANAGER −50_000 → 38_500
        setMoney(100_000L)
        val id = hire(EntityDef.CASHIER)
        assertEquals(Tier.BASE, tierOf(id))
        assertEquals(Money(98_500), gameEngine.currentState().money)

        gameEngine.promoteEntity(id)
        assertEquals(Tier.FAST, tierOf(id))
        assertEquals(Money(88_500), gameEngine.currentState().money)

        gameEngine.promoteEntity(id)
        assertEquals(Tier.MANAGER, tierOf(id))
        assertEquals(Money(38_500), gameEngine.currentState().money)

        // no-op at max
        gameEngine.promoteEntity(id)
        assertEquals(Tier.MANAGER, tierOf(id))
        assertEquals(Money(38_500), gameEngine.currentState().money)
    }

    @Test
    fun testFullStockerUpgradeSequence() {
        setMoney(100_000L)
        val id = hire(EntityDef.STOCKER)
        assertEquals(Tier.BASE, tierOf(id))

        gameEngine.promoteEntity(id)
        assertEquals(Tier.FAST, tierOf(id))
        assertEquals(Money(88_500), gameEngine.currentState().money)

        gameEngine.promoteEntity(id)
        assertEquals(Tier.MANAGER, tierOf(id))
        assertEquals(Money(38_500), gameEngine.currentState().money)

        gameEngine.promoteEntity(id)
        assertEquals(Tier.MANAGER, tierOf(id))
        assertEquals(Money(38_500), gameEngine.currentState().money)
    }
}
