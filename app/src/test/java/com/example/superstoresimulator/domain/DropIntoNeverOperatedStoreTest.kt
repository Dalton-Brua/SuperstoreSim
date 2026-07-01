package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.empire.EmpireClock
import com.example.superstoresimulator.domain.empire.EmpireSpeed
import com.example.superstoresimulator.domain.empire.RegionRegistry
import com.example.superstoresimulator.domain.empire.SecondaryStore
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.example.superstoresimulator.domain.helpers.FakeItemDao

/**
 * Regression test for the crash where [GameEngine.dropIntoStore]'s reconstruction path built
 * an out-of-bounds [StaffShift] (durationHours=14 vs the constructor's require(in 2..8)),
 * throwing on the first hands-on drop-in to any never-operated secondary store. Existing
 * empire tests only exercise [com.example.superstoresimulator.domain.empire.OperateStoreController.dropIn]
 * directly, which bypasses [GameEngine]'s reconstruction entirely — this drives the real
 * public entry point so the invariant is enforced end-to-end.
 */
class DropIntoNeverOperatedStoreTest {

    private val testItems = listOf(
        Item(
            id = "item_1", name = "Item 1",
            price = MoneyData(cents = 999), description = "Test item 1",
            unitCost = MoneyData(cents = 500), category = ItemCategory.GROCERY, casePack = 6
        ),
    )

    private fun newEngine(): GameEngine {
        val cache = ItemMetadataCache(FakeItemDao(testItems))
        runBlocking { cache.initialize() }
        return createTestGameEngine(cache)
    }

    @Test
    fun dropIntoStore_onNeverOperatedStore_doesNotCrash_forEverySize() {
        for (size in StoreSize.entries) {
            val engine = newEngine()
            val store = SecondaryStore(
                storeId = 1,
                storeName = "Never Operated",
                regionId = RegionRegistry.HOME_REGION_ID,
                storeSize = size,
                operatingState = null,
            )
            engine.state = engine.state.copy(
                empireModeActive = true,
                regions = RegionRegistry.authored,
                secondaryStores = listOf(store),
                currentTime = GameTime(totalMinutesElapsed = 3L * 1440L),
                empireClock = EmpireClock(speed = EmpireSpeed.NORMAL),
            )

            engine.dropIntoStore(1)

            val operatingState = engine.state.secondaryStores.first { it.storeId == 1 }.operatingState
            assertNotNull("$size: operating state reconstructed", operatingState)
            assertTrue("$size: at least one shift", operatingState!!.staffSchedules.isNotEmpty())
            operatingState.staffSchedules.forEach { shift ->
                assertTrue(
                    "$size: shift duration ${shift.durationHours}h must respect StaffShift's 2..8 bound",
                    shift.durationHours in 2..8,
                )
            }
        }
    }
}
