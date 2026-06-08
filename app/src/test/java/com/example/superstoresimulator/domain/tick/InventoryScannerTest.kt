package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class InventoryScannerTest {

    private lateinit var cache: ItemMetadataCache

    @Before
    fun setUp() {
        val items = listOf(
            Item(
                id = "item_1", name = "Apple",
                price = MoneyData(cents = 200), description = "Fresh",
                unitCost = MoneyData(cents = 100), category = ItemCategory.PRODUCE, casePack = 6,
                shelfLifeDays = 3,
            ),
            Item(
                id = "item_2", name = "Canned Beans",
                price = MoneyData(cents = 150), description = "Dry",
                unitCost = MoneyData(cents = 75), category = ItemCategory.GROCERY, casePack = 12,
            ),
        )
        cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
    }

    @Test
    fun `empty inventory returns all false`() {
        val result = scanInventory(emptyMap(), cache)
        assertFalse(result.hasActionableBackroom)
        assertFalse(result.hasUnzonedItems)
        assertFalse(result.hasFreshBackroomStock)
    }

    @Test
    fun `non-perishable backroom detected as actionable`() {
        val inventory = mapOf(
            2 to InventoryState(
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 5, expirationDay = Int.MAX_VALUE)),
            ),
        )
        val result = scanInventory(inventory, cache)
        assertTrue(result.hasActionableBackroom)
        assertFalse(result.hasFreshBackroomStock)
    }

    @Test
    fun `perishable backroom detected as fresh stock`() {
        val inventory = mapOf(
            1 to InventoryState(
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 5, expirationDay = 3)),
            ),
        )
        val result = scanInventory(inventory, cache)
        assertFalse(result.hasActionableBackroom)
        assertTrue(result.hasFreshBackroomStock)
    }

    @Test
    fun `unzoned shelf items detected`() {
        val inventory = mapOf(
            2 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                zoneScore = 0.5f,
            ),
        )
        val result = scanInventory(inventory, cache)
        assertTrue(result.hasUnzonedItems)
    }

    @Test
    fun `fully zoned items not flagged`() {
        val inventory = mapOf(
            2 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
                zoneScore = 1.0f,
            ),
        )
        val result = scanInventory(inventory, cache)
        assertFalse(result.hasUnzonedItems)
    }

    @Test
    fun `mixed inventory detects all flags`() {
        val inventory = mapOf(
            1 to InventoryState(
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 5, expirationDay = 3)),
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 5, expirationDay = 3)),
                zoneScore = 0.3f,
            ),
            2 to InventoryState(
                backroomBatches = listOf(ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)),
            ),
        )
        val result = scanInventory(inventory, cache)
        assertTrue(result.hasActionableBackroom)
        assertTrue(result.hasUnzonedItems)
        assertTrue(result.hasFreshBackroomStock)
    }

    @Test
    fun `empty shelf items not counted as unzoned`() {
        val inventory = mapOf(
            2 to InventoryState(zoneScore = 0.0f),
        )
        val result = scanInventory(inventory, cache)
        assertFalse(result.hasUnzonedItems)
    }
}
