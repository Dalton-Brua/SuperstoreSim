package com.example.superstoresimulator.domain.delivery

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.TRUCK_CAPACITY_TIERS
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.research.ResearchState
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.PendingOrderLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TruckUpgradeTest {

    private val testItem = Item(
        id = "item_001", name = "Canned Beans",
        price = MoneyData(1_000), unitCost = MoneyData(500),
        description = "", category = ItemCategory.GROCERY,
        casePack = 6, tier = "TIER_1", shelfLifeDays = null,
    )

    private lateinit var cache: ItemMetadataCache
    private lateinit var manager: TruckManager

    @Before
    fun setUp() {
        cache = ItemMetadataCache(FakeItemDao(listOf(testItem)))
        runBlocking { cache.initialize() }
        manager = TruckManager(cache)
    }

    private fun readyForTier1(money: Money = Money(50_000_000L)): GameState = GameState(
        money = money,
        truckConfig = TruckConfig(truckCapacityTier = 0, deliveryDays = setOf(0, 3)),
        researchState = ResearchState(
            researchedUpgrades = setOf("extra_truck_slots", "truck_upgrade_enhanced"),
        ),
    )

    private fun readyForTier2(): GameState = GameState(
        money = Money(50_000_000L),
        truckConfig = TruckConfig(truckCapacityTier = 1, deliveryDays = setOf(0, 3)),
        researchState = ResearchState(
            researchedUpgrades = setOf("extra_truck_slots", "truck_upgrade_enhanced", "truck_upgrade_heavy"),
        ),
    )

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `purchaseTruckUpgrade advances from tier 0 to tier 1`() {
        val state = readyForTier1()
        val result = manager.purchaseTruckUpgrade(state)
        assertEquals(1, result.truckConfig.truckCapacityTier)
        assertEquals(state.money - TRUCK_CAPACITY_TIERS[1].upgradeCost!!, result.money)
    }

    @Test
    fun `purchaseTruckUpgrade advances from tier 1 to tier 2`() {
        val state = readyForTier2()
        val result = manager.purchaseTruckUpgrade(state)
        assertEquals(2, result.truckConfig.truckCapacityTier)
        assertEquals(state.money - TRUCK_CAPACITY_TIERS[2].upgradeCost!!, result.money)
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    @Test
    fun `purchaseTruckUpgrade no-op at max tier`() {
        val state = readyForTier2().copy(
            truckConfig = TruckConfig(truckCapacityTier = 2, deliveryDays = setOf(0, 3)),
        )
        val result = manager.purchaseTruckUpgrade(state)
        assertEquals(state, result)
    }

    @Test
    fun `purchaseTruckUpgrade no-op when insufficient funds`() {
        val state = readyForTier1(money = Money(100_000L)) // well below $100K cost
        val result = manager.purchaseTruckUpgrade(state)
        assertEquals(0, result.truckConfig.truckCapacityTier)
        assertEquals(state.money, result.money)
    }

    @Test
    fun `purchaseTruckUpgrade no-op when research not complete`() {
        val state = readyForTier1().copy(
            researchState = ResearchState(researchedUpgrades = setOf("extra_truck_slots")),
        )
        val result = manager.purchaseTruckUpgrade(state)
        assertEquals(0, result.truckConfig.truckCapacityTier)
    }

    // ── Capacity from tier ───────────────────────────────────────────────────

    private fun stateOnDay(
        day: Int = 0,
        truckCapacityTier: Int = 0,
    ): GameState = GameState(
        currentTime = GameTime(totalMinutesElapsed = day.toLong() * 1440),
        money = Money(1_000_000L),
        truckConfig = TruckConfig(
            deliveryDays = setOf(0, 3),
            truckCapacityTier = truckCapacityTier,
        ),
    )

    private fun regularLine(): PendingOrderLine = PendingOrderLine(
        itemId = 1, quantity = 6, casePacksCount = 1,
        unitCost = Money(500), orderedOnDay = 0, isFresh = false,
    )

    private fun freshLine(): PendingOrderLine = PendingOrderLine(
        itemId = 1, quantity = 4, casePacksCount = 1,
        unitCost = Money(400), orderedOnDay = 0, isFresh = true,
    )

    @Test
    fun `scheduleRegularOrderLines creates truck with tier 0 capacity 2000`() {
        val state = stateOnDay(day = 0, truckCapacityTier = 0)
        val result = manager.scheduleRegularOrderLines(state, listOf(regularLine()), 0)
        assertEquals(2000, result.scheduledTrucks.first().capacityCasePacks)
    }

    @Test
    fun `scheduleRegularOrderLines creates truck with tier 1 capacity 3000`() {
        val state = stateOnDay(day = 0, truckCapacityTier = 1)
        val result = manager.scheduleRegularOrderLines(state, listOf(regularLine()), 0)
        assertEquals(3000, result.scheduledTrucks.first().capacityCasePacks)
    }

    @Test
    fun `scheduleRegularOrderLines creates truck with tier 2 capacity 5000`() {
        val state = stateOnDay(day = 0, truckCapacityTier = 2)
        val result = manager.scheduleRegularOrderLines(state, listOf(regularLine()), 0)
        assertEquals(5000, result.scheduledTrucks.first().capacityCasePacks)
    }

    @Test
    fun `scheduleFreshOrderLines creates fresh truck with tier-scaled capacity`() {
        for ((tier, expected) in listOf(0 to 500, 1 to 750, 2 to 1250)) {
            val state = stateOnDay(day = 0, truckCapacityTier = tier)
            val result = manager.scheduleFreshOrderLines(state, listOf(freshLine()), 0)
            val freshTruck = result.scheduledTrucks.first { it.isFreshTruck }
            assertEquals("Tier $tier fresh capacity", expected, freshTruck.capacityCasePacks)
        }
    }

    @Test
    fun `requestEarlyTruck creates truck with current tier capacity`() {
        val state = stateOnDay(day = 0, truckCapacityTier = 1)
        val result = manager.requestEarlyTruck(state, 0)
        val earlyTruck = result.scheduledTrucks.first { it.isEarlyTruck }
        assertEquals(3000, earlyTruck.capacityCasePacks)
    }

    @Test
    fun `upgrading tier does not change capacity of already-scheduled trucks`() {
        val state = stateOnDay(day = 0, truckCapacityTier = 0)
        val withTruck = manager.scheduleRegularOrderLines(state, listOf(regularLine()), 0)
        val oldTruck = withTruck.scheduledTrucks.first()
        assertEquals(2000, oldTruck.capacityCasePacks)

        val upgraded = withTruck.copy(
            truckConfig = withTruck.truckConfig.copy(truckCapacityTier = 1),
        )
        val withNewTruck = manager.scheduleRegularOrderLines(
            upgraded.copy(scheduledTrucks = upgraded.scheduledTrucks.map {
                it.copy(orders = it.orders + List(2000) { regularLine() })
            }),
            listOf(regularLine()),
            0,
        )
        val originalTruck = withNewTruck.scheduledTrucks.first()
        assertEquals("Original truck capacity unchanged", 2000, originalTruck.capacityCasePacks)
        val newTruck = withNewTruck.scheduledTrucks.last()
        assertEquals("New truck uses tier 1 capacity", 3000, newTruck.capacityCasePacks)
    }
}
