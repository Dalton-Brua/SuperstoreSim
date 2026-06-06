package com.example.superstoresimulator.domain.persistence

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.metrics.AutoHireEvent
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.metrics.DailyMetricsAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializerSchedulingTest {

    private fun roundTrip(state: GameState): GameState {
        val json = GameStateSerializer.serialize(state)
        return GameStateSerializer.deserialize(json)!!
    }

    // ── Staff schedules ─────────────────────────────────────────────────────

    @Test
    fun `staff schedules round-trip correctly`() {
        val shifts = listOf(
            StaffShift(entityId = 1, startHour = 6),
            StaffShift(entityId = 2, startHour = 10),
            StaffShift(entityId = 3, startHour = 13),
        )
        val state = GameState(staffSchedules = shifts)
        val restored = roundTrip(state)

        assertEquals(3, restored.staffSchedules.size)
        assertEquals(6, restored.staffSchedules[0].startHour)
        assertEquals(1, restored.staffSchedules[0].entityId)
        assertEquals(10, restored.staffSchedules[1].startHour)
        assertEquals(13, restored.staffSchedules[2].startHour)
    }

    @Test
    fun `empty staff schedules round-trip as empty list`() {
        val state = GameState(staffSchedules = emptyList())
        val restored = roundTrip(state)
        assertTrue(restored.staffSchedules.isEmpty())
    }

    @Test
    fun `shift endHour is derived correctly after deserialization`() {
        val state = GameState(staffSchedules = listOf(StaffShift(entityId = 1, startHour = 8)))
        val restored = roundTrip(state)
        assertEquals(16, restored.staffSchedules[0].endHour)
    }

    // ── Register system ─────────────────────────────────────────────────────

    @Test
    fun `multiple registers with assignments round-trip correctly`() {
        val registers = listOf(
            RegisterState(registerId = 0, assignedCashierId = 1, transactionActive = true),
            RegisterState(registerId = 1, assignedCashierId = null, transactionActive = false),
            RegisterState(registerId = 2, assignedCashierId = 3),
        )
        val state = GameState(
            registers = registers,
            playerAssignedRegisterId = 1,
        )
        val restored = roundTrip(state)

        assertEquals(3, restored.registers.size)
        assertEquals(1, restored.registers[0].assignedCashierId)
        assertTrue(restored.registers[0].transactionActive)
        assertNull(restored.registers[1].assignedCashierId)
        assertEquals(3, restored.registers[2].assignedCashierId)
        assertEquals(3, restored.ownedRegisterCount)
        assertEquals(1, restored.playerAssignedRegisterId)
    }

    @Test
    fun `null playerAssignedRegisterId round-trips as null`() {
        val state = GameState(playerAssignedRegisterId = null)
        val restored = roundTrip(state)
        assertNull(restored.playerAssignedRegisterId)
    }

    @Test
    fun `manuallyUnassignedCashiers round-trips correctly`() {
        val state = GameState(manuallyUnassignedCashiers = setOf(2, 5, 8))
        val restored = roundTrip(state)
        assertEquals(setOf(2, 5, 8), restored.manuallyUnassignedCashiers)
    }

    @Test
    fun `empty manuallyUnassignedCashiers round-trips as empty`() {
        val state = GameState(manuallyUnassignedCashiers = emptySet())
        val restored = roundTrip(state)
        assertTrue(restored.manuallyUnassignedCashiers.isEmpty())
    }

    // ── Hired entity with tiers, XP, and traits ─────────────────────────────

    @Test
    fun `hired entity tier round-trips correctly`() {
        val entity = HiredEntity(
            id = 1, name = "Test", entityDefinition = EntityDef.CASHIER,
            trait = EntityTrait.HARDWORKER, tier = Tier.FAST, xp = 150, level = 2,
        )
        val registry = HiredEntityRegistry(entities = listOf(entity), nextEntityId = 2)
        val state = GameState(hiredEntityRegistry = registry)
        val restored = roundTrip(state)

        val e = restored.hiredEntityRegistry.getById(1)
        assertEquals("Test", e.name)
        assertEquals(Tier.FAST, e.tier)
        assertEquals(EntityTrait.HARDWORKER, e.trait)
        assertEquals(150, e.xp)
        assertEquals(2, e.level)
        assertEquals(EntityDef.CASHIER, e.entityDefinition)
    }

    @Test
    fun `MANAGER tier entity round-trips correctly`() {
        val entity = HiredEntity(
            id = 1, name = "Boss", entityDefinition = EntityDef.CASHIER,
            trait = EntityTrait.EFFICIENT, tier = Tier.MANAGER, xp = 800, level = 4,
        )
        val registry = HiredEntityRegistry(entities = listOf(entity), nextEntityId = 2)
        val state = GameState(hiredEntityRegistry = registry)
        val restored = roundTrip(state)

        val e = restored.hiredEntityRegistry.getById(1)
        assertEquals(Tier.MANAGER, e.tier)
        assertEquals(EntityTrait.EFFICIENT, e.trait)
        assertEquals(800, e.xp)
        assertEquals(4, e.level)
    }

    @Test
    fun `manager entity def round-trips via key lookup`() {
        val entity = HiredEntity(
            id = 1, name = "Mgr", entityDefinition = EntityDef.MANAGER,
            trait = EntityTrait.VETERAN, tier = Tier.BASE,
        )
        val registry = HiredEntityRegistry(entities = listOf(entity), nextEntityId = 2)
        val state = GameState(hiredEntityRegistry = registry)
        val restored = roundTrip(state)

        val e = restored.hiredEntityRegistry.getById(1)
        assertEquals(EntityDef.MANAGER, e.entityDefinition)
        assertEquals("manager", e.entityDefinition.key)
    }

    @Test
    fun `nextEntityId is preserved across round-trip`() {
        val entity = HiredEntity(
            id = 5, name = "Five", entityDefinition = EntityDef.STOCKER,
            trait = EntityTrait.FRIENDLY,
        )
        val registry = HiredEntityRegistry(entities = listOf(entity), nextEntityId = 10)
        val state = GameState(hiredEntityRegistry = registry)
        val restored = roundTrip(state)
        assertEquals(10, restored.hiredEntityRegistry.getNextEntityId())
    }

    // ── AutoHireEvents in DailyMetrics ──────────────────────────────────────

    @Test
    fun `autoHireEvents in completedDayMetrics round-trip correctly`() {
        val events = listOf(
            AutoHireEvent("Cashier", "Unstaffed registers detected"),
            AutoHireEvent("Stocker", "High utilization", blocked = true, blockReason = "Utilization 78%"),
        )
        val day = DailyMetrics(
            dayNumber = 5,
            dayOfWeek = 3,
            autoHireEvents = events,
        )
        val state = GameState(completedDayMetrics = listOf(day))
        val restored = roundTrip(state)

        assertEquals(1, restored.completedDayMetrics.size)
        val restoredEvents = restored.completedDayMetrics[0].autoHireEvents
        assertEquals(2, restoredEvents.size)

        assertEquals("Cashier", restoredEvents[0].entityDefName)
        assertEquals("Unstaffed registers detected", restoredEvents[0].reason)
        assertFalse(restoredEvents[0].blocked)

        assertEquals("Stocker", restoredEvents[1].entityDefName)
        assertTrue(restoredEvents[1].blocked)
        assertEquals("Utilization 78%", restoredEvents[1].blockReason)
    }

    @Test
    fun `autoHireEvents in currentDayMetrics round-trip correctly`() {
        val events = listOf(
            AutoHireEvent("Fresh Handler", "Fresh items out of stock"),
        )
        val acc = DailyMetricsAccumulator(dayNumber = 3, autoHireEvents = events)
        val state = GameState(currentDayMetrics = acc)
        val restored = roundTrip(state)

        val restoredEvents = restored.currentDayMetrics.autoHireEvents
        assertEquals(1, restoredEvents.size)
        assertEquals("Fresh Handler", restoredEvents[0].entityDefName)
        assertEquals("Fresh items out of stock", restoredEvents[0].reason)
        assertFalse(restoredEvents[0].blocked)
    }

    @Test
    fun `empty autoHireEvents round-trip as empty list`() {
        val day = DailyMetrics(dayNumber = 1, dayOfWeek = 0, autoHireEvents = emptyList())
        val state = GameState(completedDayMetrics = listOf(day))
        val restored = roundTrip(state)
        assertTrue(restored.completedDayMetrics[0].autoHireEvents.isEmpty())
    }

    @Test
    fun `missing autoHireEvents in legacy save defaults to empty`() {
        val json = minimalSaveJson()
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertTrue(state!!.currentDayMetrics.autoHireEvents.isEmpty())
        assertTrue(state.completedDayMetrics.isEmpty())
    }

    // ── Full scheduling round-trip ──────────────────────────────────────────

    @Test
    fun `full scheduling state round-trips correctly`() {
        val cashier = HiredEntity(
            id = 1, name = "Alex", entityDefinition = EntityDef.CASHIER,
            trait = EntityTrait.QUICK_LEARNER, tier = Tier.FAST, xp = 350, level = 3,
        )
        val stocker = HiredEntity(
            id = 2, name = "Maya", entityDefinition = EntityDef.STOCKER,
            trait = EntityTrait.EFFICIENT, tier = Tier.BASE, xp = 50, level = 1,
        )
        val mgr = HiredEntity(
            id = 3, name = "Boss", entityDefinition = EntityDef.MANAGER,
            trait = EntityTrait.HARDWORKER, tier = Tier.BASE,
        )
        val registry = HiredEntityRegistry(
            entities = listOf(cashier, stocker, mgr),
            nextEntityId = 4,
        )
        val state = GameState(
            hiredEntityRegistry = registry,
            staffSchedules = listOf(
                StaffShift(entityId = 1, startHour = 6),
                StaffShift(entityId = 2, startHour = 10),
                StaffShift(entityId = 3, startHour = 8),
            ),
            registers = listOf(
                RegisterState(registerId = 0, assignedCashierId = 1),
                RegisterState(registerId = 1),
            ),
            playerAssignedRegisterId = 1,
            autoHireBudget = Money(100_000L),
            manuallyUnassignedCashiers = setOf(5),
        )
        val restored = roundTrip(state)

        assertEquals(3, restored.hiredEntityRegistry.totalCount())
        assertEquals(3, restored.staffSchedules.size)
        assertEquals(2, restored.registers.size)
        assertEquals(2, restored.ownedRegisterCount)
        assertEquals(1, restored.playerAssignedRegisterId)
        assertEquals(Money(100_000L), restored.autoHireBudget)
        assertEquals(setOf(5), restored.manuallyUnassignedCashiers)

        val restoredCashier = restored.hiredEntityRegistry.getById(1)
        assertEquals(Tier.FAST, restoredCashier.tier)
        assertEquals(EntityTrait.QUICK_LEARNER, restoredCashier.trait)
        assertEquals(350, restoredCashier.xp)
        assertEquals(3, restoredCashier.level)

        assertEquals(1, restored.registers[0].assignedCashierId)
        assertNull(restored.registers[1].assignedCashierId)
    }

    // ── Helper ──────────────────────────────────────────────────────────────

    private fun minimalSaveJson(): String {
        val json = org.json.JSONObject()
        json.put("storeName", "TestStore")
        json.put("money", 10000L)
        json.put("transactionActive", false)
        json.put("totalTransactionsCompleted", 0)
        json.put("totalTaxCollected", 0L)
        json.put("nextRefundId", 1)
        json.put("playerPausedTime", false)
        json.put("totalRevenue", 0L)
        json.put("currentTier", "TIER_1")
        json.put("currentStoreSize", "MOM_AND_POP")
        json.put("playerRole", "MANAGE")
        json.put("playerCashierProgress", 0.0)
        json.put("playerStockerProgress", 0.0)
        json.put("pendingCustomers", 0)
        json.put("showEndOfDayReport", false)
        json.put("pausedByEndOfDay", false)
        json.put("objectiveBonusEarned", 0L)
        json.put("currentTime", 0L)
        json.put("storeState", "CLOSED")
        json.put("storeConfig", org.json.JSONObject().apply {
            put("openTimeMinutes", 480)
            put("closeTimeMinutes", 1260)
            put("closingProcedureDuration", 30)
            put("allowTransactionsDuringClosing", true)
            put("gameSpeedMultiplier", 1.0)
            put("backroomCapPerItem", 2)
        })
        json.put("currentTransaction", org.json.JSONObject())
        json.put("salesHistory", org.json.JSONArray())
        json.put("pendingRefunds", org.json.JSONArray())
        json.put("inventory", org.json.JSONObject())
        json.put("hiredEntityRegistry", org.json.JSONObject().apply {
            put("entities", org.json.JSONArray())
            put("nextEntityId", 1)
        })
        json.put("currentDayMetrics", org.json.JSONObject().apply {
            put("dayNumber", 0)
            put("revenue", 0L)
            put("subtotal", 0L)
            put("taxCollected", 0L)
            put("transactionsCompleted", 0)
            put("rentPaid", 0L)
            put("wagesPaid", 0L)
            put("refundsProcessed", 0)
            put("refundAmount", 0L)
            put("customersServed", 0)
            put("itemsSold", 0)
            put("itemsStocked", 0)
            put("itemsOrdered", 0)
            put("lostRevenue", 0L)
            put("itemsLostToOutOfStock", 0)
            put("outOfStockEvents", org.json.JSONArray())
            put("soldItemEvents", org.json.JSONArray())
        })
        return json.toString()
    }
}
