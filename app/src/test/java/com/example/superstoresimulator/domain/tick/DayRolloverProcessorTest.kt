package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.helpers.FakeItemDao
import com.example.superstoresimulator.domain.helpers.FakeTransactionDao
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.MoneyData
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.vendor.VendorManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DayRolloverProcessorTest {

    private lateinit var staffManager: StaffManager
    private lateinit var dayManager: DayManager
    private lateinit var processor: DayRolloverProcessor

    @Before
    fun setUp() {
        staffManager = StaffManager()
        dayManager = DayManager()
        val items = listOf(
            Item(
                id = "item_1", name = "Apple",
                price = MoneyData(cents = 100), description = "Test",
                unitCost = MoneyData(cents = 50), category = ItemCategory.GROCERY, casePack = 6,
            )
        )
        val cache = ItemMetadataCache(FakeItemDao(items))
        runBlocking { cache.initialize() }
        val truckManager = TruckManager(cache)
        val inventoryManager = InventoryManager(cache, truckManager)
        processor = DayRolloverProcessor(staffManager, dayManager, truckManager, inventoryManager, FakeTransactionDao(), VendorManager(cache))
    }

    @Test
    fun `no-op when day has not changed`() {
        val state = GameState(currentTime = GameTime(100), money = Money(5000))
        val result = processor.process(state)
        assertSame(state, result)
    }

    @Test
    fun `triggers rollover when day advances`() {
        val state = GameState(
            currentTime = GameTime(1440),
            money = Money(50_000),
            registers = listOf(
                RegisterState(
                    registerId = 0,
                    transactionActive = true,
                    dailyTransactions = 5,
                    dailyRevenue = Money(1000),
                    currentTransaction = Transaction(),
                )
            ),
        )
        val result = processor.process(state)
        val reg = result.registers.first()
        assertFalse(reg.transactionActive)
        assertEquals(0, reg.dailyTransactions)
        assertEquals(Money.ZERO, reg.dailyRevenue)
    }

    @Test
    fun `clears manuallyUnassignedCashiers on rollover`() {
        val state = GameState(
            currentTime = GameTime(1440),
            money = Money(50_000),
            manuallyUnassignedCashiers = setOf(1, 2, 3),
        )
        val result = processor.process(state)
        assertTrue(result.manuallyUnassignedCashiers.isEmpty())
    }

    @Test
    fun `dayManager advances to new day after rollover`() {
        assertEquals(0, dayManager.lastKnownDayNumber)
        val state = GameState(
            currentTime = GameTime(1440),
            money = Money(50_000),
        )
        processor.process(state)
        assertEquals(1, dayManager.lastKnownDayNumber)
    }
}
