package com.example.superstoresimulator.domain.delivery

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.ScheduledTruck
import com.example.superstoresimulator.domain.StoreManagerConfig
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.metrics.OutOfStockEvent
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoreManagerTruckActionsTest {

    private lateinit var cache: ItemMetadataCache
    private lateinit var manager: TruckManager

    private val testItem = Item(
        id = "item_001", name = "Canned Beans",
        price = MoneyData(1_000), unitCost = MoneyData(500),
        description = "", category = ItemCategory.GROCERY,
        casePack = 6, tier = "TIER_1", shelfLifeDays = null,
    )

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(listOf(testItem)))
        runBlocking { cache.initialize() }
        manager = TruckManager(cache)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun storeManagerEntity(id: Int = 100): HiredEntity = HiredEntity(
        id = id, name = "Test Manager", entityDefinition = EntityDef.MANAGER,
        trait = EntityTrait.EFFICIENT, tier = Tier.MANAGER,
    )

    private fun registryWithStoreManager(): HiredEntityRegistry = HiredEntityRegistry(
        entities = listOf(storeManagerEntity()),
        nextEntityId = 101,
    )

    private fun oosEvents(count: Int): List<OutOfStockEvent> = (1..count).map {
        OutOfStockEvent(itemId = it, itemName = "Item $it", quantityLost = 5, revenueLost = Money(1_000))
    }

    private fun inventoryOf(count: Int): Map<Int, InventoryState> =
        (1..count).associateWith { InventoryState() }

    private fun baseState(
        day: Int = 7,
        money: Long = 1_000_000L,
        oosCount: Int = 0,
        deliveryDays: Set<Int> = setOf(0, 3),
        storeSize: StoreSize = StoreSize.SMALL_GROCERY,
        scheduledTrucks: List<ScheduledTruck> = emptyList(),
        extraSlots: Int = 0,
        hasStoreManager: Boolean = true,
        totalItems: Int = 100,
    ): GameState = GameState(
        currentTime = GameTime(totalMinutesElapsed = day.toLong() * 1440),
        money = Money(money),
        truckConfig = TruckConfig(
            deliveryDays = deliveryDays,
            extraTruckSlotsUnlocked = extraSlots,
        ),
        currentDayMetrics = DailyMetrics(outOfStockEvents = oosEvents(oosCount)),
        hiredEntityRegistry = if (hasStoreManager) registryWithStoreManager() else HiredEntityRegistry(),
        currentStoreSize = storeSize,
        scheduledTrucks = scheduledTrucks,
        autoHireBudget = Money(10_000),
        inventory = inventoryOf(totalItems),
    )

    // ── No Store Manager → no-op ─────────────────────────────────────────────

    @Test
    fun `no store manager means no truck actions taken`() {
        val state = baseState(oosCount = 15, hasStoreManager = false, storeSize = StoreSize.GROCERY_STORE)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertEquals(state, result)
    }

    // ── Fill Free Delivery-Day Slots ─────────────────────────────────────────

    @Test
    fun `fills free delivery-day slots after store upgrade`() {
        // SMALL_GROCERY: maxDays = 2 + 1 + 0 = 3. Currently using 2 (Mon,Thu).
        // Should add 1 day to fill the free slot.
        val state = baseState(oosCount = 0)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertEquals(3, result.truckConfig.deliveryDays.size)
    }

    @Test
    fun `fills all free slots not just one`() {
        // GROCERY_STORE: maxDays = 2 + 2 + 0 = 4. Currently using 2.
        // Should add 2 days.
        val state = baseState(oosCount = 0, storeSize = StoreSize.GROCERY_STORE)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertEquals(4, result.truckConfig.deliveryDays.size)
    }

    @Test
    fun `no days added when already at max`() {
        // MOM_AND_POP: maxDays = 2 + 0 + 0 = 2. Already using 2.
        val state = baseState(oosCount = 0, storeSize = StoreSize.MOM_AND_POP)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertEquals(2, result.truckConfig.deliveryDays.size)
    }

    @Test
    fun `filling slots is free — money unchanged`() {
        val state = baseState(oosCount = 0)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertEquals(state.money, result.money)
    }

    @Test
    fun `added days maximize gap coverage`() {
        // Mon(0),Thu(3) + 1 free slot. Best gap day should be on the far side (Sun=6 or Sat=5).
        val state = baseState(oosCount = 0)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        val addedDay = (result.truckConfig.deliveryDays - state.truckConfig.deliveryDays).single()
        // Circular distance from Mon(0): 6→1, from Thu(3): 6→3. Min=1.
        // Sat(5): from Mon: 5→2, from Thu: 5→2. Min=2.
        // Sun(6): from Mon: 6→1, from Thu: 6→3. Min=1.
        // Sat has higher min-distance, so Sat should be picked.
        // Actually let me recalculate: Fri(4): from 0→4 or 3, from 3→1 or 6. Min=1.
        // Sat(5): from 0→5 or 2, from 3→2 or 5. Min=2.
        // Sun(6): from 0→6 or 1, from 3→3 or 4. Min=1.
        assertTrue("Added day should be Sat(5) for max gap", addedDay == 5)
    }

    // ── Early Truck ──────────────────────────────────────────────────────────

    @Test
    fun `requests early truck when OOS threshold met and next truck far away`() {
        // Day 7 is Monday (7%7=0). Delivery days Mon,Thu,Sat (after free slot fill).
        // Next delivery after day 7: findNextDeliveryDay(7, {0,3,5}) → day 10 (Thu, 10%7=3).
        // 10 - 7 = 3 days away (>2), and 5 OOS items → should request early truck
        val state = baseState(day = 7, oosCount = 5)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)

        val earlyTruck = result.scheduledTrucks.find { it.isEarlyTruck }
        assertTrue("Early truck should be scheduled", earlyTruck != null)
        assertEquals(8, earlyTruck!!.scheduledArrivalDay)
    }

    @Test
    fun `no early truck when next truck is within 2 days`() {
        // Day 9 is Wednesday (9%7=2). After slot fill: Mon,Thu,Sat.
        // findNextDeliveryDay(9, {0,3,5}) → day 10 (Thu). 10-9=1 ≤2 → no early truck.
        val state = baseState(day = 9, oosCount = 10)
        val result = manager.evaluateStoreManagerTruckActions(state, 9)
        assertTrue(result.scheduledTrucks.none { it.isEarlyTruck })
    }

    @Test
    fun `no early truck when OOS below threshold`() {
        // 2 OOS out of 100 items = 2%, below default 3% threshold
        val state = baseState(day = 7, oosCount = 2)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertTrue(result.scheduledTrucks.none { it.isEarlyTruck })
    }

    @Test
    fun `no early truck when not enough money above budget floor`() {
        val state = baseState(day = 7, oosCount = 5, money = 15_000)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertTrue(result.scheduledTrucks.none { it.isEarlyTruck })
    }

    @Test
    fun `no early truck when one already exists for tomorrow`() {
        val earlyTruck = ScheduledTruck(
            truckId = 99, scheduledArrivalDay = 8, capacityCasePacks = 2000, isEarlyTruck = true,
        )
        val state = baseState(day = 7, oosCount = 5, scheduledTrucks = listOf(earlyTruck))
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertEquals(1, result.scheduledTrucks.count { it.isEarlyTruck })
        assertEquals(state.money, result.money)
    }

    // ── Purchase Extra Slot ──────────────────────────────────────────────────

    @Test
    fun `purchases extra slot and adds day when maxed out and OOS very high`() {
        // MOM_AND_POP: maxDays = 2 + 0 + 0 = 2. Already using 2 days. OOS = 10.
        val state = baseState(day = 9, oosCount = 10, storeSize = StoreSize.MOM_AND_POP)
        val result = manager.evaluateStoreManagerTruckActions(state, 9)

        assertEquals(1, result.truckConfig.extraTruckSlotsUnlocked)
        assertEquals(3, result.truckConfig.deliveryDays.size)
        assertTrue(result.money < state.money)
    }

    @Test
    fun `no extra slot when money would drop below budget`() {
        // autoHireBudget = $100 (10_000 cents). Slot costs $1,000 (100_000 cents).
        // Need money < 100_000 + 10_000 = 110_000 to fail.
        val state = baseState(day = 9, oosCount = 10, storeSize = StoreSize.MOM_AND_POP, money = 105_000)
        val result = manager.evaluateStoreManagerTruckActions(state, 9)
        assertEquals(0, result.truckConfig.extraTruckSlotsUnlocked)
    }

    @Test
    fun `no extra slot when free delivery days available`() {
        // SMALL_GROCERY: maxDays=3, using 2. Free slots exist → fill those, don't buy slot.
        val state = baseState(day = 9, oosCount = 10)
        val result = manager.evaluateStoreManagerTruckActions(state, 9)
        assertEquals(0, result.truckConfig.extraTruckSlotsUnlocked)
        assertEquals(3, result.truckConfig.deliveryDays.size)
    }

    // ── Events Logged ────────────────────────────────────────────────────────

    @Test
    fun `slot fill actions are logged as AutoHireEvents`() {
        val state = baseState(day = 7, oosCount = 0)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertTrue(result.currentDayMetrics.autoHireEvents.any {
            it.entityDefName == "Truck" && it.reason == "Delivery day added"
        })
    }

    @Test
    fun `early truck action is logged`() {
        val state = baseState(day = 7, oosCount = 5)
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertTrue(result.currentDayMetrics.autoHireEvents.any {
            it.reason == "Early truck ordered"
        })
    }

    // ── findBestGapDay ───────────────────────────────────────────────────────

    @Test
    fun `findBestGapDay picks day maximizing minimum distance to existing days`() {
        val result = manager.findBestGapDay(setOf(0, 3))
        assertEquals(5, result) // Sat — min distance 2 from both Mon and Thu
    }

    @Test
    fun `findBestGapDay returns null when all days taken`() {
        assertEquals(null, manager.findBestGapDay(setOf(0, 1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun `findBestGapDay with single existing day prioritizes weekend`() {
        val result = manager.findBestGapDay(setOf(0))
        // No weekend in {Mon} → picks best weekend. Sat(5): dist 2 from Mon. Sun(6): dist 1.
        assertEquals(5, result)
    }

    @Test
    fun `findBestGapDay uses gap logic when weekend already covered`() {
        val result = manager.findBestGapDay(setOf(0, 5))
        // Weekend exists (Sat). Best gap: Wed(2) or Tue(2) — both have min-dist 2.
        assertTrue(result == 2 || result == 3)
    }

    // ── Config-disabled guards ──────────────────────────────────────────────

    @Test
    fun `fill slots disabled via config — no days added`() {
        val state = baseState(oosCount = 0).copy(
            storeManagerConfig = StoreManagerConfig(autoFillDeliverySlots = false)
        )
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertEquals(2, result.truckConfig.deliveryDays.size)
    }

    @Test
    fun `early truck disabled via config — no early truck even with high OOS`() {
        val state = baseState(day = 7, oosCount = 15).copy(
            storeManagerConfig = StoreManagerConfig(autoEarlyTruckEnabled = false)
        )
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertTrue(result.scheduledTrucks.none { it.isEarlyTruck })
    }

    @Test
    fun `buy slot disabled via config — no extra slot purchased`() {
        val state = baseState(day = 9, oosCount = 15, storeSize = StoreSize.MOM_AND_POP).copy(
            storeManagerConfig = StoreManagerConfig(autoBuyTruckSlotEnabled = false)
        )
        val result = manager.evaluateStoreManagerTruckActions(state, 9)
        assertEquals(0, result.truckConfig.extraTruckSlotsUnlocked)
    }

    @Test
    fun `custom early truck percent threshold respected`() {
        // 5 OOS out of 100 = 5%, set threshold to 10% → should NOT trigger
        val state = baseState(day = 7, oosCount = 5).copy(
            storeManagerConfig = StoreManagerConfig(earlyTruckOosPercent = 10)
        )
        val result = manager.evaluateStoreManagerTruckActions(state, 7)
        assertTrue(result.scheduledTrucks.none { it.isEarlyTruck })
    }

    @Test
    fun `custom buy slot threshold respected`() {
        // Default threshold is 10, set to 20. 10 OOS should NOT trigger.
        val state = baseState(day = 9, oosCount = 10, storeSize = StoreSize.MOM_AND_POP).copy(
            storeManagerConfig = StoreManagerConfig(buySlotOosThreshold = 20)
        )
        val result = manager.evaluateStoreManagerTruckActions(state, 9)
        assertEquals(0, result.truckConfig.extraTruckSlotsUnlocked)
    }
}
