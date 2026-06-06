package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.store.StoreSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegisterTest {

    // ── ID-based lookup ──────────────────────────────────────────────────────

    @Test
    fun `findRegisterById returns matching register`() {
        val registers = listOf(
            RegisterState(registerId = 0),
            RegisterState(registerId = 1),
            RegisterState(registerId = 2),
        )
        val found = registers.findRegisterById(1)
        assertEquals(1, found?.registerId)
    }

    @Test
    fun `findRegisterById returns null for missing id`() {
        val registers = listOf(RegisterState(registerId = 0))
        assertNull(registers.findRegisterById(99))
    }

    // ── updateRegister ───────────────────────────────────────────────────────

    @Test
    fun `updateRegister replaces matching register and preserves others`() {
        val registers = listOf(
            RegisterState(registerId = 0, transactionActive = false),
            RegisterState(registerId = 1, transactionActive = false),
        )
        val updated = registers.updateRegister(
            RegisterState(registerId = 1, transactionActive = true)
        )
        assertEquals(false, updated[0].transactionActive)
        assertEquals(true, updated[1].transactionActive)
    }

    // ── Register cost ladder ─────────────────────────────────────────────────

    @Test
    fun `nextRegisterCost follows documented cost ladder`() {
        assertEquals(Money(20_000L), StoreSize.nextRegisterCost(1))
        assertEquals(Money(50_000L), StoreSize.nextRegisterCost(2))
        assertEquals(Money(100_000L), StoreSize.nextRegisterCost(3))
        assertEquals(Money(200_000L), StoreSize.nextRegisterCost(4))
        assertEquals(Money(200_000L), StoreSize.nextRegisterCost(5))
    }

    // ── Max registers per store size ─────────────────────────────────────────

    @Test
    fun `maxRegisters matches documented values per store size`() {
        assertEquals(1, StoreSize.MOM_AND_POP.maxRegisters)
        assertEquals(2, StoreSize.SMALL_GROCERY.maxRegisters)
        assertEquals(3, StoreSize.GROCERY_STORE.maxRegisters)
        assertEquals(5, StoreSize.SUPERSTORE.maxRegisters)
        assertEquals(8, StoreSize.SUPERCENTER.maxRegisters)
    }

    // ── purchaseRegister ─────────────────────────────────────────────────────

    @Test
    fun `purchaseRegister adds register and deducts cost`() {
        val controller = StoreController()
        val state = GameState(
            money = Money(50_000L),
            currentStoreSize = StoreSize.SMALL_GROCERY,
        )
        val result = controller.purchaseRegister(state)
        assertEquals(2, result.registers.size)
        assertEquals(2, result.ownedRegisterCount)
        assertEquals(Money(50_000L) - StoreSize.nextRegisterCost(1), result.money)
    }

    @Test
    fun `purchaseRegister blocked when at max for store size`() {
        val controller = StoreController()
        val state = GameState(
            money = Money(500_000L),
            currentStoreSize = StoreSize.MOM_AND_POP,
        )
        val result = controller.purchaseRegister(state)
        assertEquals("Should not add register at MOM_AND_POP max", 1, result.registers.size)
        assertEquals(Money(500_000L), result.money)
    }

    @Test
    fun `purchaseRegister blocked when insufficient funds`() {
        val controller = StoreController()
        val state = GameState(
            money = Money(100L),
            currentStoreSize = StoreSize.SMALL_GROCERY,
        )
        val result = controller.purchaseRegister(state)
        assertEquals(1, result.registers.size)
        assertEquals(Money(100L), result.money)
    }

    @Test
    fun `purchased register gets unique incrementing id`() {
        val controller = StoreController()
        var state = GameState(
            money = Money(500_000L),
            currentStoreSize = StoreSize.GROCERY_STORE,
        )
        state = controller.purchaseRegister(state)
        state = controller.purchaseRegister(state)
        val ids = state.registers.map { it.registerId }
        assertEquals(3, ids.size)
        assertEquals(ids.toSet().size, ids.size)
    }
}
