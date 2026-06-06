package com.example.superstoresimulator.domain.staff

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.metrics.DailyMetricsAccumulator
import com.example.superstoresimulator.domain.metrics.OutOfStockEvent
import com.example.superstoresimulator.domain.store.StoreSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AutoHireTest {

    private lateinit var staffManager: StaffManager

    @Before
    fun setUp() {
        staffManager = StaffManager()
    }

    private fun entity(
        id: Int,
        def: EntityDef,
        tier: Tier = Tier.BASE,
        trait: EntityTrait = EntityTrait.EFFICIENT,
    ) = HiredEntity(id = id, name = "Test", entityDefinition = def, trait = trait, tier = tier)

    private fun buildState(
        entities: List<HiredEntity>,
        money: Long = 500_000L,
        autoHireBudget: Long = 1L,
        registers: List<RegisterState> = listOf(RegisterState(0)),
        oosItemIds: List<Int> = emptyList(),
        storeSize: StoreSize = StoreSize.GROCERY_STORE,
        inventory: Map<Int, InventoryState> = emptyMap(),
        pendingCustomers: Int = 0,
    ): GameState {
        val reg = HiredEntityRegistry(entities = entities, nextEntityId = (entities.maxOfOrNull { it.id } ?: 0) + 1)
        val schedules = entities.map { StaffShift(entityId = it.id, startHour = 6) }
        val oosEvents = oosItemIds.map { OutOfStockEvent(itemId = it, itemName = "Item$it", quantityLost = 1, revenueLost = Money(100)) }
        return GameState(
            money = Money(money),
            hiredEntityRegistry = reg,
            staffSchedules = schedules,
            autoHireBudget = Money(autoHireBudget),
            registers = registers,
            currentStoreSize = storeSize,
            currentDayMetrics = DailyMetricsAccumulator(outOfStockEvents = oosEvents),
            inventory = inventory,
            pendingCustomers = pendingCustomers,
        )
    }

    private fun simulateUnstaffedRegisters() {
        staffManager.updateUtilization(
            state = buildState(
                entities = listOf(entity(10, EntityDef.MANAGER)),
                registers = listOf(RegisterState(0), RegisterState(1)),
            ),
            currentHour = 10,
            hasActionableBackroom = false,
            hasUnzonedItems = false,
            hasFreshWork = false,
        )
    }

    // ── MANAGER basic auto-hire ──────────────────────────────────────────────

    @Test
    fun `manager auto-hires cashier when unstaffed registers detected`() {
        simulateUnstaffedRegisters()

        val state = buildState(
            entities = listOf(entity(10, EntityDef.MANAGER)),
            registers = listOf(RegisterState(0), RegisterState(1)),
        )
        val result = staffManager.evaluateAutoHire(state)
        assertTrue(
            "Should auto-hire a cashier",
            result.hiredEntityRegistry.getByDef(EntityDef.CASHIER).isNotEmpty(),
        )
    }

    @Test
    fun `manager auto-hires stocker when backroom has stock`() {
        val backroomInventory = mapOf(
            1 to InventoryState(backroomBatches = listOf(ItemBatch(receivedDay = 1, quantity = 5, expirationDay = Int.MAX_VALUE))),
        )
        val state = buildState(
            entities = listOf(
                entity(10, EntityDef.MANAGER),
                entity(20, EntityDef.STOCKER),
            ),
            inventory = backroomInventory,
        )
        val beforeCount = state.hiredEntityRegistry.getByDef(EntityDef.STOCKER).size
        val result = staffManager.evaluateAutoHire(state)
        assertTrue(
            "Should auto-hire a stocker",
            result.hiredEntityRegistry.getByDef(EntityDef.STOCKER).size > beforeCount,
        )
    }

    @Test
    fun `manager auto-hires fresh handler when fresh items went OOS without being ordered`() {
        val freshInventory = listOf(100, 101, 102).associateWith {
            InventoryState(shelfBatches = listOf(ItemBatch(receivedDay = 1, quantity = 0, expirationDay = 10)))
        }

        repeat(100) {
            staffManager.updateUtilization(
                state = buildState(
                    entities = listOf(
                        entity(10, EntityDef.MANAGER),
                        entity(30, EntityDef.FRESH_HANDLER),
                    ),
                    inventory = freshInventory,
                ),
                currentHour = 10,
                hasActionableBackroom = false,
                hasUnzonedItems = false,
                hasFreshWork = true,
            )
        }

        val state = buildState(
            entities = listOf(
                entity(10, EntityDef.MANAGER),
                entity(30, EntityDef.FRESH_HANDLER),
            ),
            oosItemIds = listOf(100, 101, 102),
            inventory = freshInventory,
        )
        val result = staffManager.evaluateAutoHire(state)
        assertTrue(
            "Should auto-hire fresh handler",
            result.hiredEntityRegistry.getByDef(EntityDef.FRESH_HANDLER).size > 1,
        )
    }

    // ── No manager = no auto-hire ────────────────────────────────────────────

    @Test
    fun `no auto-hire without manager on staff`() {
        simulateUnstaffedRegisters()

        val state = buildState(
            entities = listOf(entity(20, EntityDef.CASHIER)),
            registers = listOf(RegisterState(0), RegisterState(1)),
        )
        val result = staffManager.evaluateAutoHire(state)
        assertEquals(
            "No new hires without a manager",
            state.hiredEntityRegistry.totalCount(),
            result.hiredEntityRegistry.totalCount(),
        )
    }

    // ── Budget guard ─────────────────────────────────────────────────────────

    @Test
    fun `auto-hire blocked when budget guard would be violated`() {
        simulateUnstaffedRegisters()

        val state = buildState(
            entities = listOf(entity(10, EntityDef.MANAGER)),
            money = 2_000L,
            autoHireBudget = 1_000L,
            registers = listOf(RegisterState(0), RegisterState(1)),
        )
        val result = staffManager.evaluateAutoHire(state)
        assertEquals(
            "Budget guard should prevent hire",
            state.hiredEntityRegistry.totalCount(),
            result.hiredEntityRegistry.totalCount(),
        )
    }

    @Test
    fun `auto-hire disabled when autoHireBudget is ZERO`() {
        simulateUnstaffedRegisters()

        val state = buildState(
            entities = listOf(entity(10, EntityDef.MANAGER)),
            autoHireBudget = 0L,
            registers = listOf(RegisterState(0), RegisterState(1)),
        )
        val result = staffManager.evaluateAutoHire(state)
        assertEquals(
            "autoHireBudget=ZERO disables auto-hire",
            state.hiredEntityRegistry.totalCount(),
            result.hiredEntityRegistry.totalCount(),
        )
    }

    // ── Senior manager blocks hire when utilization < 100% ───────────────────

    @Test
    fun `senior manager blocks cashier hire when utilization below 100 pct`() {
        val entities = listOf(
            entity(10, EntityDef.MANAGER, tier = Tier.FAST),
            entity(20, EntityDef.CASHIER),
            entity(21, EntityDef.CASHIER),
        )
        val reg = HiredEntityRegistry(entities = entities, nextEntityId = 22)
        val shifts = listOf(
            StaffShift(entityId = 10, startHour = 6),
            StaffShift(entityId = 20, startHour = 6),
            StaffShift(entityId = 21, startHour = 13),
        )
        val registers = listOf(
            RegisterState(0, assignedCashierId = 20),
            RegisterState(1),
        )

        repeat(10) {
            staffManager.updateUtilization(
                state = GameState(
                    money = Money(500_000L),
                    hiredEntityRegistry = reg,
                    staffSchedules = shifts,
                    registers = registers,
                    pendingCustomers = 1,
                    currentStoreSize = StoreSize.GROCERY_STORE,
                    autoHireBudget = Money(1L),
                ),
                currentHour = 13,
                hasActionableBackroom = false,
                hasUnzonedItems = false,
                hasFreshWork = false,
            )
        }

        val evalState = GameState(
            money = Money(500_000L),
            hiredEntityRegistry = reg,
            staffSchedules = shifts,
            registers = registers,
            currentStoreSize = StoreSize.GROCERY_STORE,
            autoHireBudget = Money(1L),
        )
        val result = staffManager.evaluateAutoHire(evalState)
        val autoHireEvents = result.currentDayMetrics.autoHireEvents
        assertTrue(
            "Senior manager should block hire with low utilization",
            autoHireEvents.any { it.blocked && it.entityDefName == EntityDef.CASHIER.displayName },
        )
    }
}
