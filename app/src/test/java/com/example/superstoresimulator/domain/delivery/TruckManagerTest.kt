package com.example.superstoresimulator.domain.delivery

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.PendingOrderLine
import com.example.superstoresimulator.domain.ScheduledTruck
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemWithName
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.time.GameTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.example.superstoresimulator.domain.helpers.FakeItemDao

/**
 * Unit tests for [TruckManager].
 *
 * [TruckManager] is a pure class: all methods receive a [GameState] and return a new
 * [GameState]. Tests construct minimal [GameState] objects with only the fields under test.
 *
 * Test catalogue:
 *   Item 1 — GROCERY, casePack 6, non-perishable (shelfLifeDays = null)
 *   Item 2 — PRODUCE, casePack 4, perishable: shelfLifeDays = 3
 *
 * Default TruckConfig: deliveryDays = {0, 3} (Mon=0, Thu=3), regularCap=2000, freshCap=500.
 *
 * Covers:
 *   - [scheduleRegularOrderLines]: scheduled on next configured day of week
 *   - [scheduleRegularOrderLines]: overflow splits across two trucks on consecutive delivery days
 *   - [scheduleFreshOrderLines]: always schedules for currentDay + 1
 *   - [updateConfig]: already-scheduled trucks are untouched; only truckConfig changes
 *   - [processArrivals]: items land in backroom with correct expirationDay, trucks removed
 *   - [cancelPendingOrderLine]: money refunded, empty truck removed from scheduledTrucks
 *   - [requestEarlyTruck]: $100 deducted, arrives currentDay + 1
 *   - [requestEarlyTruck]: subsequent regular orders fill the early truck first
 *   - [requestEarlyTruck]: no-op if early truck already exists for tomorrow
 *   - [requestEarlyTruck]: no-op if insufficient funds
 *   - [findNextDeliveryDay]: returns correct day; handles week-wrap correctly
 */
class TruckManagerTest {

    // ── FakeItemDao ───────────────────────────────────────────────────────────

    // ── Catalogue ─────────────────────────────────────────────────────────────

    private val itemNonPerishable = Item(
        id = "item_001", name = "Canned Beans",
        price = MoneyData(1_000), unitCost = MoneyData(500),
        description = "", category = ItemCategory.GROCERY,
        casePack = 6, tier = "TIER_1", shelfLifeDays = null,
    )

    private val itemPerishable = Item(
        id = "item_002", name = "Tomatoes",
        price = MoneyData(800), unitCost = MoneyData(400),
        description = "", category = ItemCategory.PRODUCE,
        casePack = 4, tier = "TIER_3", shelfLifeDays = 3,
    )

    private lateinit var cache: ItemMetadataCache
    private lateinit var manager: TruckManager

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(listOf(itemNonPerishable, itemPerishable)))
        runBlocking { cache.initialize() }
        manager = TruckManager(cache)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Create a state with the given day (total minutes → full days). Default day 0. */
    private fun stateOnDay(
        day: Int = 0,
        money: Long = 1_000_000L,
        truckConfig: TruckConfig = TruckConfig(deliveryDays = setOf(0, 3)),
        scheduledTrucks: List<ScheduledTruck> = emptyList(),
        nextTruckId: Int = 0,
    ): GameState = GameState(
        currentTime = GameTime(totalMinutesElapsed = day.toLong() * 1440),
        money = Money(money),
        truckConfig = truckConfig,
        scheduledTrucks = scheduledTrucks,
        nextTruckId = nextTruckId,
    )

    private fun regularLine(itemId: Int = 1, casePacks: Int = 1, casePack: Int = 6): PendingOrderLine =
        PendingOrderLine(
            itemId = itemId,
            quantity = casePacks * casePack,
            casePacksCount = casePacks,
            unitCost = Money(500),
            orderedOnDay = 0,
            isFresh = false,
        )

    private fun freshLine(itemId: Int = 2, casePacks: Int = 1, casePack: Int = 4): PendingOrderLine =
        PendingOrderLine(
            itemId = itemId,
            quantity = casePacks * casePack,
            casePacksCount = casePacks,
            unitCost = Money(400),
            orderedOnDay = 0,
            isFresh = true,
        )

    // ── scheduleRegularOrderLines ─────────────────────────────────────────────

    @Test
    fun `scheduleRegularOrderLines creates a truck on the next configured delivery day`() {
        // Day 0 is Monday (0 % 7 = 0). Delivery days = {0, 3} (Mon, Thu).
        // Next delivery day after day 0 should be day 3 (Thursday).
        val state = stateOnDay(day = 0)
        val result = manager.scheduleRegularOrderLines(state, listOf(regularLine(casePacks = 2)), currentDay = 0)

        assertEquals(1, result.scheduledTrucks.size)
        assertEquals(3, result.scheduledTrucks[0].scheduledArrivalDay)
        // Truck should have 2 case-packs loaded
        assertEquals(2, result.scheduledTrucks[0].usedCapacityCasePacks)
    }

    @Test
    fun `scheduleRegularOrderLines places empty list as no-op`() {
        val state = stateOnDay(day = 0)
        val result = manager.scheduleRegularOrderLines(state, emptyList(), currentDay = 0)

        assertTrue(result.scheduledTrucks.isEmpty())
    }

    @Test
    fun `scheduleRegularOrderLines fills existing truck before creating new one`() {
        val existingTruck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 3,
            capacityCasePacks = 2000,
            orders = listOf(regularLine(casePacks = 1)),
        )
        val state = stateOnDay(day = 0, scheduledTrucks = listOf(existingTruck), nextTruckId = 1)
        val result = manager.scheduleRegularOrderLines(state, listOf(regularLine(casePacks = 3)), currentDay = 0)

        // Should still be one truck (existing truck had capacity)
        assertEquals(1, result.scheduledTrucks.size)
        assertEquals(4, result.scheduledTrucks[0].usedCapacityCasePacks) // 1 existing + 3 new
    }

    @Test
    fun `scheduleRegularOrderLines overflow creates second truck on next delivery day`() {
        // Existing truck is nearly full — only 1 case-pack remaining capacity
        val nearlyFullTruck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 3,
            capacityCasePacks = 2,
            orders = listOf(regularLine(casePacks = 1)), // 1 used, 1 remaining
        )
        val state = stateOnDay(day = 0, scheduledTrucks = listOf(nearlyFullTruck), nextTruckId = 1)
        // Try to add 3 case-packs: 1 goes on the existing truck, overflow 2 need a new truck
        val result = manager.scheduleRegularOrderLines(
            state,
            listOf(regularLine(casePacks = 1), regularLine(itemId = 1, casePacks = 2)),
            currentDay = 0
        )

        // Should have 2 trucks: first truck is now full, second truck on next delivery day
        assertEquals(2, result.scheduledTrucks.size)
        val firstTruck = result.scheduledTrucks.first { it.truckId == 0 }
        assertEquals(2, firstTruck.usedCapacityCasePacks) // now full
        val secondTruck = result.scheduledTrucks.first { it.truckId != 0 }
        assertTrue(secondTruck.scheduledArrivalDay > 3) // scheduled after the first truck
    }

    // ── scheduleFreshOrderLines ───────────────────────────────────────────────

    @Test
    fun `scheduleFreshOrderLines always creates truck for currentDay + 1`() {
        val state = stateOnDay(day = 5)
        val result = manager.scheduleFreshOrderLines(state, listOf(freshLine(casePacks = 2)), currentDay = 5)

        assertEquals(1, result.scheduledTrucks.size)
        assertEquals(6, result.scheduledTrucks[0].scheduledArrivalDay) // day 5 + 1
        assertTrue(result.scheduledTrucks[0].isFreshTruck)
    }

    @Test
    fun `scheduleFreshOrderLines appends to existing fresh truck if already scheduled`() {
        val existingFresh = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 6,
            capacityCasePacks = 500,
            isFreshTruck = true,
            orders = listOf(freshLine(casePacks = 5)),
        )
        val state = stateOnDay(day = 5, scheduledTrucks = listOf(existingFresh), nextTruckId = 1)
        val result = manager.scheduleFreshOrderLines(state, listOf(freshLine(casePacks = 3)), currentDay = 5)

        // Should still be just one fresh truck (combined)
        assertEquals(1, result.scheduledTrucks.size)
        assertEquals(8, result.scheduledTrucks[0].usedCapacityCasePacks) // 5 + 3
    }

    @Test
    fun `scheduleFreshOrderLines empty list is no-op`() {
        val state = stateOnDay(day = 2)
        val result = manager.scheduleFreshOrderLines(state, emptyList(), currentDay = 2)
        assertTrue(result.scheduledTrucks.isEmpty())
    }

    // ── updateConfig ──────────────────────────────────────────────────────────

    @Test
    fun `updateConfig changes only truckConfig, leaves scheduled trucks untouched`() {
        val existingTruck = ScheduledTruck(truckId = 0, scheduledArrivalDay = 3, capacityCasePacks = 2000)
        val state = stateOnDay(day = 0, scheduledTrucks = listOf(existingTruck))
        val newConfig = TruckConfig(deliveryDays = setOf(1, 4), regularTruckCapacityCasePacks = 1000)

        val result = manager.updateConfig(state, newConfig)

        assertEquals(newConfig, result.truckConfig)
        // Existing truck is untouched
        assertEquals(1, result.scheduledTrucks.size)
        assertEquals(3, result.scheduledTrucks[0].scheduledArrivalDay)
    }

    @Test
    fun `updateConfig is no-op when deliveryDays would be empty`() {
        val state = stateOnDay(day = 0)
        val emptyConfig = TruckConfig(deliveryDays = emptySet())
        val result = manager.updateConfig(state, emptyConfig)

        // Config unchanged since empty set is rejected
        assertEquals(state.truckConfig, result.truckConfig)
    }

    // ── processArrivals ───────────────────────────────────────────────────────

    @Test
    fun `processArrivals for non-perishable item delivers to backroom with MAX_VALUE expiration`() {
        val truck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 3,
            capacityCasePacks = 2000,
            orders = listOf(regularLine(itemId = 1, casePacks = 2)),
        )
        val state = stateOnDay(day = 3, scheduledTrucks = listOf(truck))
        val result = manager.processArrivals(state, currentDay = 3)

        // Truck removed
        assertTrue(result.scheduledTrucks.isEmpty())

        // Items in backroom
        val inv = result.inventory[1]
        assertNotNull(inv)
        assertEquals(12, inv!!.backroomStock) // 2 case-packs × 6 items = 12

        // expirationDay = Int.MAX_VALUE for non-perishable
        assertEquals(Int.MAX_VALUE, inv.backroomBatches.first().expirationDay)
    }

    @Test
    fun `processArrivals for perishable item sets expirationDay to arrivalDay + shelfLifeDays`() {
        val truck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 5,
            capacityCasePacks = 500,
            isFreshTruck = true,
            orders = listOf(freshLine(itemId = 2, casePacks = 1)),
        )
        val state = stateOnDay(day = 5, scheduledTrucks = listOf(truck))
        val result = manager.processArrivals(state, currentDay = 5)

        val inv = result.inventory[2]
        assertNotNull(inv)
        assertEquals(4, inv!!.backroomStock) // 1 case-pack × 4 items = 4

        // expirationDay = arrivalDay(5) + shelfLifeDays(3) = 8
        assertEquals(8, inv.backroomBatches.first().expirationDay)
    }

    @Test
    fun `processArrivals does not process trucks that have not yet arrived`() {
        val futureTruck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 10,
            capacityCasePacks = 2000,
            orders = listOf(regularLine(casePacks = 1)),
        )
        val state = stateOnDay(day = 5, scheduledTrucks = listOf(futureTruck))
        val result = manager.processArrivals(state, currentDay = 5)

        // Truck still scheduled
        assertEquals(1, result.scheduledTrucks.size)
        // No inventory added
        assertNull(result.inventory[1])
    }

    @Test
    fun `processArrivals records delivery in currentDayMetrics`() {
        val truck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 3,
            capacityCasePacks = 2000,
            orders = listOf(regularLine(itemId = 1, casePacks = 2)),
        )
        val state = stateOnDay(day = 3, scheduledTrucks = listOf(truck))
        val result = manager.processArrivals(state, currentDay = 3)

        assertEquals(1, result.currentDayMetrics.deliveredTrucks.size)
        val record = result.currentDayMetrics.deliveredTrucks[0]
        assertEquals(0, record.truckId)
        assertEquals(3, record.arrivalDay)
        assertEquals(2, record.totalCasePacks)
    }

    // ── cancelPendingOrderLine ────────────────────────────────────────────────

    @Test
    fun `cancelPendingOrderLine refunds money and removes the line`() {
        val line = regularLine(itemId = 1, casePacks = 2) // 2 × 6 × 500¢ = 6000¢ refund
        val truck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 3,
            capacityCasePacks = 2000,
            orders = listOf(line),
        )
        val startMoney = 10_000L
        val state = stateOnDay(day = 0, money = startMoney, scheduledTrucks = listOf(truck))

        val result = manager.cancelPendingOrderLine(state, itemId = 1, truckId = 0, currentDay = 0)

        // Refund = quantity(12) × unitCost(500¢) = 6000¢
        val expectedRefund = Money(6_000L)
        assertEquals(Money(startMoney) + expectedRefund, result.money)
    }

    @Test
    fun `cancelPendingOrderLine removes empty truck from scheduledTrucks`() {
        val line = regularLine(itemId = 1, casePacks = 1)
        val truck = ScheduledTruck(
            truckId = 5,
            scheduledArrivalDay = 3,
            capacityCasePacks = 2000,
            orders = listOf(line),
        )
        val state = stateOnDay(day = 0, scheduledTrucks = listOf(truck))
        val result = manager.cancelPendingOrderLine(state, itemId = 1, truckId = 5, currentDay = 0)

        // Truck was the only order, so it should be removed
        assertTrue(result.scheduledTrucks.isEmpty())
    }

    @Test
    fun `cancelPendingOrderLine is no-op for already-arrived truck`() {
        val line = regularLine(itemId = 1, casePacks = 1)
        val arrivedTruck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 2, // arrived on day 2, current day is 3
            capacityCasePacks = 2000,
            orders = listOf(line),
        )
        val state = stateOnDay(day = 3, money = 5_000L, scheduledTrucks = listOf(arrivedTruck))
        val result = manager.cancelPendingOrderLine(state, itemId = 1, truckId = 0, currentDay = 3)

        // No changes: truck still scheduled, money unchanged
        assertEquals(1, result.scheduledTrucks.size)
        assertEquals(Money(5_000L), result.money)
    }

    // ── requestEarlyTruck ─────────────────────────────────────────────────────

    @Test
    fun `requestEarlyTruck deducts $100 and schedules truck for currentDay + 1`() {
        val state = stateOnDay(day = 4, money = 50_000L)
        val result = manager.requestEarlyTruck(state, currentDay = 4)

        // $100 = 10_000 cents deducted
        assertEquals(Money(40_000L), result.money)
        assertEquals(1, result.scheduledTrucks.size)
        val truck = result.scheduledTrucks[0]
        assertEquals(5, truck.scheduledArrivalDay) // day 4 + 1
        assertTrue(truck.isEarlyTruck)
    }

    @Test
    fun `requestEarlyTruck is no-op when player cannot afford $100`() {
        val state = stateOnDay(day = 4, money = 5_000L) // only $50
        val result = manager.requestEarlyTruck(state, currentDay = 4)

        assertEquals(Money(5_000L), result.money)
        assertTrue(result.scheduledTrucks.isEmpty())
    }

    @Test
    fun `requestEarlyTruck is no-op when early truck already exists for tomorrow`() {
        val existingEarly = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 5,
            capacityCasePacks = 2000,
            isEarlyTruck = true,
        )
        val state = stateOnDay(day = 4, money = 50_000L, scheduledTrucks = listOf(existingEarly))
        val result = manager.requestEarlyTruck(state, currentDay = 4)

        // No extra truck created, money unchanged
        assertEquals(Money(50_000L), result.money)
        assertEquals(1, result.scheduledTrucks.size)
    }

    @Test
    fun `requestEarlyTruck moves existing regular truck orders to arrive tomorrow`() {
        // A regular truck is scheduled for day 7 with 3 case-packs already on it.
        val regularTruck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 7,
            capacityCasePacks = 2000,
            orders = listOf(regularLine(casePacks = 3)),
        )
        val state = stateOnDay(day = 4, money = 50_000L, scheduledTrucks = listOf(regularTruck), nextTruckId = 1)
        val result = manager.requestEarlyTruck(state, currentDay = 4)

        // $100 deducted
        assertEquals(Money(40_000L), result.money)
        // Still only one truck (the regular truck was rescheduled, not duplicated)
        assertEquals(1, result.scheduledTrucks.size)
        val truck = result.scheduledTrucks[0]
        // Arrives tomorrow
        assertEquals(5, truck.scheduledArrivalDay)
        // Flagged as early truck
        assertTrue(truck.isEarlyTruck)
        // All 3 case-packs are present
        assertEquals(3, truck.usedCapacityCasePacks)
        // nextTruckId is unchanged (no new truck was allocated)
        assertEquals(1, result.nextTruckId)
    }

    @Test
    fun `requestEarlyTruck picks the soonest regular truck when multiple are scheduled`() {
        val nearerTruck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 7,
            capacityCasePacks = 2000,
            orders = listOf(regularLine(itemId = 1, casePacks = 2)),
        )
        val furtherTruck = ScheduledTruck(
            truckId = 1,
            scheduledArrivalDay = 10,
            capacityCasePacks = 2000,
            orders = listOf(regularLine(itemId = 1, casePacks = 1)),
        )
        val state = stateOnDay(
            day = 4,
            money = 50_000L,
            scheduledTrucks = listOf(nearerTruck, furtherTruck),
            nextTruckId = 2,
        )
        val result = manager.requestEarlyTruck(state, currentDay = 4)

        // Two trucks remain — only the nearer one was rescheduled
        assertEquals(2, result.scheduledTrucks.size)
        val rescheduled = result.scheduledTrucks.first { it.truckId == 0 }
        assertEquals(5, rescheduled.scheduledArrivalDay)
        assertTrue(rescheduled.isEarlyTruck)
        assertEquals(2, rescheduled.usedCapacityCasePacks)

        // Further truck is untouched
        val unchanged = result.scheduledTrucks.first { it.truckId == 1 }
        assertEquals(10, unchanged.scheduledArrivalDay)
        assertTrue(!unchanged.isEarlyTruck)
    }

    @Test
    fun `scheduleRegularOrderLines fills early truck before regular truck`() {
        val earlyTruck = ScheduledTruck(
            truckId = 0,
            scheduledArrivalDay = 1, // tomorrow (currentDay=0)
            capacityCasePacks = 2000,
            isEarlyTruck = true,
        )
        val state = stateOnDay(day = 0, scheduledTrucks = listOf(earlyTruck), nextTruckId = 1)
        val result = manager.scheduleRegularOrderLines(state, listOf(regularLine(casePacks = 5)), currentDay = 0)

        // The early truck should have been filled (not a new regular truck created)
        val updatedEarly = result.scheduledTrucks.first { it.truckId == 0 }
        assertEquals(5, updatedEarly.usedCapacityCasePacks)
    }

    // ── findNextDeliveryDay ───────────────────────────────────────────────────

    @Test
    fun `findNextDeliveryDay returns next Monday after Sunday`() {
        // Day 6 = Sunday (6 % 7 = 6). Next Monday (0) should be day 7.
        val result = manager.findNextDeliveryDay(currentDay = 6, deliveryDays = setOf(0))
        assertEquals(7, result)
    }

    @Test
    fun `findNextDeliveryDay returns same-week Thursday from Monday`() {
        // Day 0 = Monday. Next Thursday (3) = day 3.
        val result = manager.findNextDeliveryDay(currentDay = 0, deliveryDays = setOf(3))
        assertEquals(3, result)
    }

    @Test
    fun `findNextDeliveryDay skips over currentDay and finds next occurrence`() {
        // Day 3 = Thursday (3 % 7 = 3). Delivery days = {0, 3}.
        // Next day in {0,3} strictly AFTER day 3 is day 7 (next Monday).
        val result = manager.findNextDeliveryDay(currentDay = 3, deliveryDays = setOf(0, 3))
        assertEquals(7, result)
    }

    @Test
    fun `findNextDeliveryDay handles week wrap across multiple weeks`() {
        // Day 6 = Sunday. Delivery on Saturday only (day-of-week = 6).
        // Next Saturday after day 6 is day 13.
        val result = manager.findNextDeliveryDay(currentDay = 6, deliveryDays = setOf(6))
        assertEquals(13, result)
    }

    @Test
    fun `findNextDeliveryDay returns currentDay + 1 when deliveryDays is empty`() {
        val result = manager.findNextDeliveryDay(currentDay = 5, deliveryDays = emptySet())
        assertEquals(6, result)
    }
}
