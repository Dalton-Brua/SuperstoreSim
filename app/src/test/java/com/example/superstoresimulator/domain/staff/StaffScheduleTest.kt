package com.example.superstoresimulator.domain.staff

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StaffScheduleTest {

    private lateinit var staffManager: StaffManager

    @Before
    fun setUp() {
        staffManager = StaffManager()
    }

    private fun stateWithMoney(cents: Long = 100_000L): GameState =
        GameState(money = Money(cents))

    // ── StaffShift validation ────────────────────────────────────────────────

    @Test
    fun `StaffShift rejects startHour below 6`() {
        assertThrows(IllegalArgumentException::class.java) {
            StaffShift(entityId = 1, startHour = 5)
        }
    }

    @Test
    fun `StaffShift rejects startHour above 13`() {
        assertThrows(IllegalArgumentException::class.java) {
            StaffShift(entityId = 1, startHour = 14)
        }
    }

    @Test
    fun `StaffShift accepts boundary startHour 6`() {
        val shift = StaffShift(entityId = 1, startHour = 6)
        assertEquals(6, shift.startHour)
        assertEquals(14, shift.endHour)
    }

    @Test
    fun `StaffShift accepts boundary startHour 13`() {
        val shift = StaffShift(entityId = 1, startHour = 13)
        assertEquals(13, shift.startHour)
        assertEquals(21, shift.endHour)
    }

    // ── isOnShift ────────────────────────────────────────────────────────────

    @Test
    fun `isOnShift returns true for hours within window`() {
        val shift = StaffShift(entityId = 1, startHour = 8)
        assertTrue(shift.isOnShift(8))
        assertTrue(shift.isOnShift(12))
        assertTrue(shift.isOnShift(15))
    }

    @Test
    fun `isOnShift returns false at endHour (exclusive)`() {
        val shift = StaffShift(entityId = 1, startHour = 8)
        assertFalse(shift.isOnShift(16))
    }

    @Test
    fun `isOnShift returns false before startHour`() {
        val shift = StaffShift(entityId = 1, startHour = 10)
        assertFalse(shift.isOnShift(9))
    }

    // ── Auto-shift on hire ───────────────────────────────────────────────────

    @Test
    fun `hireEntity auto-assigns shift to new employee`() {
        val state = stateWithMoney()
        val result = staffManager.hireEntity(state, EntityDef.CASHIER)
        val entityId = result.hiredEntityRegistry.hiredEntities.last().id
        val shift = result.staffSchedules.firstOrNull { it.entityId == entityId }
        assertTrue("Hired employee should have a shift assigned", shift != null)
        assertTrue("Shift startHour must be in 6..13", shift!!.startHour in 6..13)
    }

    @Test
    fun `hireEntity distributes shifts across presets`() {
        var state = stateWithMoney(500_000L)
        repeat(3) {
            state = staffManager.hireEntity(state, EntityDef.CASHIER)
        }
        val startHours = state.staffSchedules.map { it.startHour }.toSet()
        assertTrue(
            "Three hires should use at least two distinct shift presets but got $startHours",
            startHours.size >= 2,
        )
        assertTrue(
            "All shift start hours should be valid presets",
            startHours.all { it in setOf(StaffManager.SHIFT_MORNING, StaffManager.SHIFT_MID, StaffManager.SHIFT_CLOSING) },
        )
    }

    // ── Shift removal on fire ────────────────────────────────────────────────

    @Test
    fun `fireEntity removes shift for fired employee`() {
        var state = stateWithMoney()
        state = staffManager.hireEntity(state, EntityDef.CASHIER)
        val entityId = state.hiredEntityRegistry.hiredEntities.last().id
        assertTrue(state.staffSchedules.any { it.entityId == entityId })

        state = staffManager.fireEntity(state, entityId)
        assertFalse(
            "Fired employee's shift should be removed",
            state.staffSchedules.any { it.entityId == entityId },
        )
    }

    @Test
    fun `fireEntity does not affect other employees shifts`() {
        var state = stateWithMoney(500_000L)
        state = staffManager.hireEntity(state, EntityDef.CASHIER)
        state = staffManager.hireEntity(state, EntityDef.STOCKER)
        val cashierId = state.hiredEntityRegistry.getByDef(EntityDef.CASHIER).first().id
        val stockerId = state.hiredEntityRegistry.getByDef(EntityDef.STOCKER).first().id

        state = staffManager.fireEntity(state, cashierId)
        assertTrue(
            "Stocker shift should remain after firing cashier",
            state.staffSchedules.any { it.entityId == stockerId },
        )
    }

    // ── updateShift ──────────────────────────────────────────────────────────

    @Test
    fun `updateShift changes startHour for existing shift`() {
        var state = stateWithMoney()
        state = staffManager.hireEntity(state, EntityDef.CASHIER)
        val entityId = state.hiredEntityRegistry.hiredEntities.last().id

        state = staffManager.updateShift(state, entityId, newStartHour = 10)
        val shift = state.staffSchedules.first { it.entityId == entityId }
        assertEquals(10, shift.startHour)
    }

    @Test
    fun `updateShift rejects invalid startHour and returns state unchanged`() {
        var state = stateWithMoney()
        state = staffManager.hireEntity(state, EntityDef.CASHIER)
        val entityId = state.hiredEntityRegistry.hiredEntities.last().id
        val originalHour = state.staffSchedules.first { it.entityId == entityId }.startHour

        val result = staffManager.updateShift(state, entityId, newStartHour = 5)
        val shiftAfter = result.staffSchedules.first { it.entityId == entityId }
        assertEquals("Invalid startHour should leave shift unchanged", originalHour, shiftAfter.startHour)
    }

    @Test
    fun `updateShift creates shift if entity has none`() {
        val registry = HiredEntityRegistry().hireEntity(EntityDef.CASHIER)
        val entityId = registry.hiredEntities.last().id
        val state = GameState(money = Money(100_000L), hiredEntityRegistry = registry)

        val result = staffManager.updateShift(state, entityId, newStartHour = 8)
        val shift = result.staffSchedules.firstOrNull { it.entityId == entityId }
        assertTrue("Should create shift for entity without one", shift != null)
        assertEquals(8, shift!!.startHour)
    }
}
