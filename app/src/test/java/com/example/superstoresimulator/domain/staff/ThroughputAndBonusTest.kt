package com.example.superstoresimulator.domain.staff

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.player.PlayerRole
import org.junit.Assert.assertEquals
import org.junit.Test

class ThroughputAndBonusTest {

    private fun entity(
        id: Int,
        def: EntityDef = EntityDef.CASHIER,
        tier: Tier = Tier.BASE,
        trait: EntityTrait = EntityTrait.EFFICIENT,
        level: Int = 1,
    ) = HiredEntity(id = id, name = "Test", entityDefinition = def, trait = trait, tier = tier, level = level)

    private fun registry(vararg entities: HiredEntity): HiredEntityRegistry {
        var reg = HiredEntityRegistry()
        for (e in entities) {
            reg = HiredEntityRegistry(
                entities = reg.hiredEntities + e,
                nextEntityId = (reg.hiredEntities + e).maxOf { it.id } + 1,
            )
        }
        return reg
    }

    private fun shift(entityId: Int, startHour: Int = 6) = StaffShift(entityId = entityId, startHour = startHour)

    // ── Tier throughput weights ──────────────────────────────────────────────

    @Test
    fun `BASE tier throughputWeight is 1_0`() {
        assertEquals(1.0f, entity(1, tier = Tier.BASE).throughputWeight)
    }

    @Test
    fun `FAST tier throughputWeight is 1_5`() {
        assertEquals(1.5f, entity(1, tier = Tier.FAST).throughputWeight)
    }

    @Test
    fun `MANAGER tier throughputWeight is 2_0`() {
        assertEquals(2.0f, entity(1, tier = Tier.MANAGER).throughputWeight)
    }

    // ── activeWeightedCount ──────────────────────────────────────────────────

    @Test
    fun `activeWeightedCount returns 0 when no employees on shift`() {
        val reg = registry(entity(1, def = EntityDef.CASHIER))
        val schedules = listOf(shift(1, startHour = 13))
        val result = StaffManager.activeWeightedCount(EntityDef.CASHIER, currentHour = 6, schedules, reg)
        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `activeWeightedCount sums weighted on-shift employees`() {
        val e1 = entity(1, def = EntityDef.CASHIER, tier = Tier.BASE)
        val e2 = entity(2, def = EntityDef.CASHIER, tier = Tier.FAST)
        val reg = registry(e1, e2)
        val schedules = listOf(shift(1, startHour = 6), shift(2, startHour = 6))
        val result = StaffManager.activeWeightedCount(EntityDef.CASHIER, currentHour = 8, schedules, reg)
        assertEquals(2.5f, result, 0.001f)
    }

    @Test
    fun `activeWeightedCount applies levelMultiplier`() {
        val e = entity(1, def = EntityDef.CASHIER, tier = Tier.BASE, level = 3)
        val reg = registry(e)
        val schedules = listOf(shift(1, startHour = 6))
        val result = StaffManager.activeWeightedCount(EntityDef.CASHIER, currentHour = 8, schedules, reg)
        assertEquals(1.0f * 1.2f * 1.0f, result, 0.001f)
    }

    @Test
    fun `activeWeightedCount applies HARDWORKER throughputMultiplier`() {
        val e = entity(1, def = EntityDef.CASHIER, trait = EntityTrait.HARDWORKER)
        val reg = registry(e)
        val schedules = listOf(shift(1, startHour = 6))
        val result = StaffManager.activeWeightedCount(EntityDef.CASHIER, currentHour = 8, schedules, reg)
        assertEquals(1.0f * 1.0f * 1.10f, result, 0.001f)
    }

    // ── computeGlobalBonus ───────────────────────────────────────────────────

    @Test
    fun `MANAGE player role gives 1_10 bonus`() {
        val reg = HiredEntityRegistry()
        val bonus = StaffManager.computeGlobalBonus(PlayerRole.MANAGE, 10, emptyList(), reg)
        assertEquals(1.10f, bonus, 0.001f)
    }

    @Test
    fun `CASHIER player role gives no bonus`() {
        val reg = HiredEntityRegistry()
        val bonus = StaffManager.computeGlobalBonus(PlayerRole.CASHIER, 10, emptyList(), reg)
        assertEquals(1.0f, bonus, 0.001f)
    }

    @Test
    fun `base MANAGER entity gives 1_15 bonus`() {
        val mgr = entity(1, def = EntityDef.MANAGER, tier = Tier.BASE)
        val reg = registry(mgr)
        val schedules = listOf(shift(1, startHour = 6))
        val bonus = StaffManager.computeGlobalBonus(PlayerRole.CASHIER, 10, schedules, reg)
        assertEquals(1.15f, bonus, 0.001f)
    }

    @Test
    fun `promoted MANAGER (senior) gives 1_25 bonus`() {
        val mgr = entity(1, def = EntityDef.MANAGER, tier = Tier.FAST)
        val reg = registry(mgr)
        val schedules = listOf(shift(1, startHour = 6))
        val bonus = StaffManager.computeGlobalBonus(PlayerRole.CASHIER, 10, schedules, reg)
        assertEquals(1.25f, bonus, 0.001f)
    }

    @Test
    fun `player MANAGE stacks multiplicatively with senior manager`() {
        val mgr = entity(1, def = EntityDef.MANAGER, tier = Tier.FAST)
        val reg = registry(mgr)
        val schedules = listOf(shift(1, startHour = 6))
        val bonus = StaffManager.computeGlobalBonus(PlayerRole.MANAGE, 10, schedules, reg)
        assertEquals(1.10f * 1.25f, bonus, 0.001f)
    }

    @Test
    fun `off-shift manager gives no bonus`() {
        val mgr = entity(1, def = EntityDef.MANAGER, tier = Tier.BASE)
        val reg = registry(mgr)
        val schedules = listOf(shift(1, startHour = 13))
        val bonus = StaffManager.computeGlobalBonus(PlayerRole.CASHIER, 6, schedules, reg)
        assertEquals(1.0f, bonus, 0.001f)
    }

    // ── deptManagerBonus ─────────────────────────────────────────────────────

    @Test
    fun `dept manager gives 1_10 to same type`() {
        val deptMgr = entity(1, def = EntityDef.CASHIER, tier = Tier.MANAGER)
        val reg = registry(deptMgr)
        val schedules = listOf(shift(1, startHour = 6))
        val bonus = StaffManager.deptManagerBonus(EntityDef.CASHIER, 10, schedules, reg)
        assertEquals(1.10f, bonus, 0.001f)
    }

    @Test
    fun `dept manager does NOT boost other types`() {
        val deptMgr = entity(1, def = EntityDef.CASHIER, tier = Tier.MANAGER)
        val reg = registry(deptMgr)
        val schedules = listOf(shift(1, startHour = 6))
        val bonus = StaffManager.deptManagerBonus(EntityDef.STOCKER, 10, schedules, reg)
        assertEquals(1.0f, bonus, 0.001f)
    }

    @Test
    fun `multiple dept managers of same type do NOT double-stack`() {
        val dm1 = entity(1, def = EntityDef.CASHIER, tier = Tier.MANAGER)
        val dm2 = entity(2, def = EntityDef.CASHIER, tier = Tier.MANAGER)
        val reg = registry(dm1, dm2)
        val schedules = listOf(shift(1, startHour = 6), shift(2, startHour = 6))
        val bonus = StaffManager.deptManagerBonus(EntityDef.CASHIER, 10, schedules, reg)
        assertEquals(1.10f, bonus, 0.001f)
    }

    @Test
    fun `off-shift dept manager gives no bonus`() {
        val deptMgr = entity(1, def = EntityDef.CASHIER, tier = Tier.MANAGER)
        val reg = registry(deptMgr)
        val schedules = listOf(shift(1, startHour = 13))
        val bonus = StaffManager.deptManagerBonus(EntityDef.CASHIER, 6, schedules, reg)
        assertEquals(1.0f, bonus, 0.001f)
    }

    // ── Full stacking example ────────────────────────────────────────────────

    @Test
    fun `global and dept bonuses stack multiplicatively`() {
        val mgr = entity(1, def = EntityDef.MANAGER, tier = Tier.FAST)
        val deptMgr = entity(2, def = EntityDef.CASHIER, tier = Tier.MANAGER)
        val reg = registry(mgr, deptMgr)
        val schedules = listOf(shift(1, startHour = 6), shift(2, startHour = 6))

        val global = StaffManager.computeGlobalBonus(PlayerRole.MANAGE, 10, schedules, reg)
        val dept = StaffManager.deptManagerBonus(EntityDef.CASHIER, 10, schedules, reg)
        val total = global * dept
        assertEquals(1.10f * 1.25f * 1.10f, total, 0.01f)
    }
}
