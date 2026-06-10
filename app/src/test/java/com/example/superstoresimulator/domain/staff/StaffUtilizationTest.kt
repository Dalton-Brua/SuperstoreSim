package com.example.superstoresimulator.domain.staff

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class StaffUtilizationTest {

    private lateinit var staffManager: StaffManager

    @Before
    fun setUp() {
        staffManager = StaffManager()
    }

    private fun entity(id: Int, def: EntityDef) =
        HiredEntity(id = id, name = "Test", entityDefinition = def, trait = EntityTrait.EFFICIENT)

    private fun buildState(
        entities: List<HiredEntity>,
        schedules: List<StaffShift> = entities.map { StaffShift(entityId = it.id, startHour = 6) },
        registers: List<RegisterState> = listOf(RegisterState(0)),
        pendingCustomers: Int = 0,
    ): GameState {
        val reg = HiredEntityRegistry(
            entities = entities,
            nextEntityId = (entities.maxOfOrNull { it.id } ?: 0) + 1,
        )
        return GameState(
            money = Money(100_000L),
            hiredEntityRegistry = reg,
            staffSchedules = schedules,
            registers = registers,
            pendingCustomers = pendingCustomers,
        )
    }

    // ── Cashier utilization ──────────────────────────────────────────────────

    @Test
    fun `cashier assigned to register with active transaction counts as busy`() {
        val cashier = entity(1, EntityDef.CASHIER)
        val state = buildState(
            entities = listOf(cashier),
            registers = listOf(RegisterState(0, assignedCashierId = 1, transactionActive = true)),
        )
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = false)
        }
        assertEquals(1.0f, staffManager.currentCashierUtilization(), 0.001f)
    }

    @Test
    fun `cashier assigned to register with pending customers counts as busy`() {
        val cashier = entity(1, EntityDef.CASHIER)
        val state = buildState(
            entities = listOf(cashier),
            registers = listOf(RegisterState(0, assignedCashierId = 1, transactionActive = false)),
            pendingCustomers = 5,
        )
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = false)
        }
        assertEquals(1.0f, staffManager.currentCashierUtilization(), 0.001f)
    }

    @Test
    fun `unassigned on-shift cashier counts as idle`() {
        val cashier = entity(1, EntityDef.CASHIER)
        val state = buildState(
            entities = listOf(cashier),
            registers = listOf(RegisterState(0)),
        )
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = false)
        }
        assertEquals(0.0f, staffManager.currentCashierUtilization(), 0.001f)
    }

    @Test
    fun `off-shift cashier not counted in utilization`() {
        val cashier = entity(1, EntityDef.CASHIER)
        val state = buildState(
            entities = listOf(cashier),
            schedules = listOf(StaffShift(entityId = 1, startHour = 13)),
        )
        staffManager.updateUtilization(state, currentHour = 6,
            hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = false)
        assertEquals(0.0f, staffManager.currentCashierUtilization(), 0.001f)
    }

    // ── Stocker utilization ──────────────────────────────────────────────────

    @Test
    fun `stocker busy when actionable backroom items exist`() {
        val stocker = entity(1, EntityDef.STOCKER)
        val state = buildState(entities = listOf(stocker))
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = true, hasUnzonedItems = false, hasFreshWork = false)
        }
        assertEquals(1.0f, staffManager.currentStockerUtilization(), 0.001f)
    }

    @Test
    fun `stocker not counted as busy when only zoning`() {
        val stocker = entity(1, EntityDef.STOCKER)
        val state = buildState(entities = listOf(stocker))
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = false, hasUnzonedItems = true, hasFreshWork = false)
        }
        assertEquals(0.0f, staffManager.currentStockerUtilization(), 0.001f)
    }

    @Test
    fun `stocker idle when no backroom work and no zoning needed`() {
        val stocker = entity(1, EntityDef.STOCKER)
        val state = buildState(entities = listOf(stocker))
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = false)
        }
        assertEquals(0.0f, staffManager.currentStockerUtilization(), 0.001f)
    }

    // ── Fresh handler utilization ────────────────────────────────────────────

    @Test
    fun `fresh handler busy when fresh work available`() {
        val handler = entity(1, EntityDef.FRESH_HANDLER)
        val state = buildState(entities = listOf(handler))
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = true)
        }
        assertEquals(1.0f, staffManager.currentFreshUtilization(), 0.001f)
    }

    @Test
    fun `fresh handler idle when no fresh work`() {
        val handler = entity(1, EntityDef.FRESH_HANDLER)
        val state = buildState(entities = listOf(handler))
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = false)
        }
        assertEquals(0.0f, staffManager.currentFreshUtilization(), 0.001f)
    }

    // ── Daily metrics reset ──────────────────────────────────────────────────

    @Test
    fun `resetDailyMetrics clears utilization accumulators`() {
        val cashier = entity(1, EntityDef.CASHIER)
        val state = buildState(
            entities = listOf(cashier),
            registers = listOf(RegisterState(0, assignedCashierId = 1, transactionActive = true)),
        )
        repeat(10) {
            staffManager.updateUtilization(state, currentHour = 10,
                hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = false)
        }
        assertEquals(1.0f, staffManager.currentCashierUtilization(), 0.001f)

        staffManager.resetDailyMetrics()
        assertEquals(0.0f, staffManager.currentCashierUtilization(), 0.001f)
    }

    // ── Employee activity tracking ───────────────────────────────────────────

    @Test
    fun `employee activities correctly assigned per state`() {
        val cashier = entity(1, EntityDef.CASHIER)
        val stocker = entity(2, EntityDef.STOCKER)
        val state = buildState(
            entities = listOf(cashier, stocker),
            registers = listOf(RegisterState(0, assignedCashierId = 1, transactionActive = true)),
        )
        staffManager.updateUtilization(state, currentHour = 10,
            hasActionableBackroom = true, hasUnzonedItems = false, hasFreshWork = false)

        assertEquals(EmployeeActivity.CASHIERING, staffManager.employeeActivities[1])
        assertEquals(EmployeeActivity.STOCKING, staffManager.employeeActivities[2])
    }

    @Test
    fun `off-shift employee gets OFF_SHIFT activity`() {
        val cashier = entity(1, EntityDef.CASHIER)
        val state = buildState(
            entities = listOf(cashier),
            schedules = listOf(StaffShift(entityId = 1, startHour = 13)),
        )
        staffManager.updateUtilization(state, currentHour = 6,
            hasActionableBackroom = false, hasUnzonedItems = false, hasFreshWork = false)
        assertEquals(EmployeeActivity.OFF_SHIFT, staffManager.employeeActivities[1])
    }
}
