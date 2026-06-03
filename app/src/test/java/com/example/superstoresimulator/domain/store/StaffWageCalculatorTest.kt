package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class StaffWageCalculatorTest {

    private fun registryOf(vararg entities: HiredEntity): HiredEntityRegistry =
        HiredEntityRegistry(entities = entities.toList(), nextEntityId = (entities.maxOfOrNull { it.id } ?: 0) + 1)

    private fun cashier(id: Int, trait: EntityTrait = EntityTrait.HARDWORKER, tier: Tier = Tier.BASE) =
        HiredEntity(id = id, name = "Cashier $id", entityDefinition = EntityDef.CASHIER, trait = trait, tier = tier)

    private fun stocker(id: Int, trait: EntityTrait = EntityTrait.HARDWORKER, tier: Tier = Tier.BASE) =
        HiredEntity(id = id, name = "Stocker $id", entityDefinition = EntityDef.STOCKER, trait = trait, tier = tier)

    private fun freshHandler(id: Int, trait: EntityTrait = EntityTrait.HARDWORKER, tier: Tier = Tier.BASE) =
        HiredEntity(id = id, name = "Fresh $id", entityDefinition = EntityDef.FRESH_HANDLER, trait = trait, tier = tier)

    private fun manager(id: Int, trait: EntityTrait = EntityTrait.HARDWORKER, tier: Tier = Tier.BASE) =
        HiredEntity(id = id, name = "Manager $id", entityDefinition = EntityDef.MANAGER, trait = trait, tier = tier)

    // ── Empty registry ──────────────────────────────────────────────────────

    @Test
    fun `empty registry returns zero wages`() {
        assertEquals(Money.ZERO, StaffWageCalculator.calculateTotalWages(HiredEntityRegistry()))
    }

    // ── Single BASE cashier, non-EFFICIENT ──────────────────────────────────

    @Test
    fun `single BASE cashier daily wage is hourlyWage times 8`() {
        val registry = registryOf(cashier(1))
        val expected = EntityDef.CASHIER.baseWage * 8
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    // ── EFFICIENT trait gives 10% discount ──────────────────────────────────

    @Test
    fun `EFFICIENT trait applies 10 percent discount`() {
        val registry = registryOf(cashier(1, trait = EntityTrait.EFFICIENT))
        val fullWage = EntityDef.CASHIER.baseWage * 8
        val discounted = fullWage * 0.9
        assertEquals(discounted, StaffWageCalculator.calculateTotalWages(registry))
    }

    // ── Non-EFFICIENT traits pay full rate ───────────────────────────────────

    @Test
    fun `FRIENDLY trait pays full rate`() {
        val registry = registryOf(cashier(1, trait = EntityTrait.FRIENDLY))
        val expected = EntityDef.CASHIER.baseWage * 8
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    @Test
    fun `QUICK_LEARNER trait pays full rate`() {
        val registry = registryOf(cashier(1, trait = EntityTrait.QUICK_LEARNER))
        val expected = EntityDef.CASHIER.baseWage * 8
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    @Test
    fun `VETERAN trait pays full rate`() {
        val registry = registryOf(cashier(1, trait = EntityTrait.VETERAN))
        val expected = EntityDef.CASHIER.baseWage * 8
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    // ── Tier-based wage scaling ─────────────────────────────────────────────

    @Test
    fun `FAST tier cashier pays double hourly wage`() {
        val registry = registryOf(cashier(1, tier = Tier.FAST))
        val hourlyWage = EntityDef.CASHIER.baseWage * 2.0
        val expected = hourlyWage * 8
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    @Test
    fun `MANAGER tier cashier pays triple hourly wage`() {
        val registry = registryOf(cashier(1, tier = Tier.MANAGER))
        val hourlyWage = EntityDef.CASHIER.baseWage * 3.0
        val expected = hourlyWage * 8
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    // ── Manager entity has higher base wage ─────────────────────────────────

    @Test
    fun `manager entity uses its own baseWage`() {
        val registry = registryOf(manager(1))
        val expected = EntityDef.MANAGER.baseWage * 8
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    @Test
    fun `FAST tier manager pays double its baseWage`() {
        val registry = registryOf(manager(1, tier = Tier.FAST))
        val hourlyWage = EntityDef.MANAGER.baseWage * 2.0
        val expected = hourlyWage * 8
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    // ── EFFICIENT + promoted tier stacks ─────────────────────────────────────

    @Test
    fun `EFFICIENT FAST cashier gets both tier and trait multipliers`() {
        val registry = registryOf(cashier(1, trait = EntityTrait.EFFICIENT, tier = Tier.FAST))
        val hourlyWage = EntityDef.CASHIER.baseWage * 2.0
        val expected = hourlyWage * 8 * 0.9
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    // ── Multiple employees aggregate ────────────────────────────────────────

    @Test
    fun `multiple employees sum correctly`() {
        val registry = registryOf(
            cashier(1),
            stocker(2),
            freshHandler(3),
        )
        val perDay = EntityDef.CASHIER.baseWage * 8
        val expected = perDay + perDay + perDay
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    @Test
    fun `mixed traits and tiers aggregate correctly`() {
        val c = cashier(1, trait = EntityTrait.HARDWORKER, tier = Tier.BASE)
        val s = stocker(2, trait = EntityTrait.EFFICIENT, tier = Tier.FAST)
        val registry = registryOf(c, s)

        val cashierDaily = EntityDef.CASHIER.baseWage * 8
        val stockerHourly = EntityDef.STOCKER.baseWage * 2.0
        val stockerDaily = stockerHourly * 8 * 0.9

        val expected = cashierDaily + stockerDaily
        assertEquals(expected, StaffWageCalculator.calculateTotalWages(registry))
    }

    // ── All entity types at BASE tier have same base wage ────────────────────

    @Test
    fun `cashier stocker and fresh handler share default baseWage`() {
        assertEquals(EntityDef.CASHIER.baseWage, EntityDef.STOCKER.baseWage)
        assertEquals(EntityDef.CASHIER.baseWage, EntityDef.FRESH_HANDLER.baseWage)
    }

    @Test
    fun `manager has higher baseWage than cashier`() {
        assert(EntityDef.MANAGER.baseWage > EntityDef.CASHIER.baseWage)
    }
}
