package com.example.superstoresimulator.domain.staff

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityTrait
import com.example.superstoresimulator.domain.Entities.HiredEntity
import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.Entities.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffLevelingTest {

    private fun registryWithEntity(
        trait: EntityTrait = EntityTrait.EFFICIENT,
        level: Int = 1,
        xp: Int = 0,
    ): HiredEntityRegistry {
        val entity = HiredEntity(
            id = 1, name = "Test", entityDefinition = EntityDef.CASHIER,
            trait = trait, level = level, xp = xp,
        )
        return HiredEntityRegistry(entities = listOf(entity), nextEntityId = 2)
    }

    // ── grantXp accumulation ─────────────────────────────────────────────────

    @Test
    fun `grantXp adds raw XP to entity`() {
        val reg = registryWithEntity(xp = 0)
        val updated = reg.grantXp(1, 50)
        assertEquals(50, updated.getById(1).xp)
    }

    @Test
    fun `grantXp accumulates across multiple calls`() {
        var reg = registryWithEntity(xp = 0)
        reg = reg.grantXp(1, 40)
        reg = reg.grantXp(1, 40)
        reg = reg.grantXp(1, 40)
        assertEquals(120, reg.getById(1).xp)
    }

    // ── Level thresholds ─────────────────────────────────────────────────────

    @Test
    fun `entity starts at level 1 with 0 XP`() {
        val reg = registryWithEntity(xp = 0)
        assertEquals(1, reg.getById(1).level)
    }

    @Test
    fun `entity reaches level 2 at 100 XP`() {
        val reg = registryWithEntity(xp = 0)
        val updated = reg.grantXp(1, 100)
        assertEquals(2, updated.getById(1).level)
    }

    @Test
    fun `entity reaches level 3 at 300 XP`() {
        val reg = registryWithEntity(xp = 0)
        val updated = reg.grantXp(1, 300)
        assertEquals(3, updated.getById(1).level)
    }

    @Test
    fun `entity reaches level 5 at 1500 XP`() {
        val reg = registryWithEntity(xp = 0)
        val updated = reg.grantXp(1, 1500)
        assertEquals(HiredEntity.MAX_LEVEL, updated.getById(1).level)
    }

    @Test
    fun `level does not exceed MAX_LEVEL with excess XP`() {
        val reg = registryWithEntity(xp = 0)
        val updated = reg.grantXp(1, 99999)
        assertEquals(HiredEntity.MAX_LEVEL, updated.getById(1).level)
    }

    @Test
    fun `99 XP stays at level 1`() {
        val reg = registryWithEntity(xp = 0)
        val updated = reg.grantXp(1, 99)
        assertEquals(1, updated.getById(1).level)
    }

    // ── QUICK_LEARNER trait ──────────────────────────────────────────────────

    @Test
    fun `QUICK_LEARNER gets 1_5x XP`() {
        val reg = registryWithEntity(trait = EntityTrait.QUICK_LEARNER, xp = 0)
        val updated = reg.grantXp(1, 100)
        assertEquals(150, updated.getById(1).xp)
    }

    @Test
    fun `non-QUICK_LEARNER gets 1x XP`() {
        val reg = registryWithEntity(trait = EntityTrait.HARDWORKER, xp = 0)
        val updated = reg.grantXp(1, 100)
        assertEquals(100, updated.getById(1).xp)
    }

    // ── VETERAN trait ────────────────────────────────────────────────────────

    @Test
    fun `VETERAN starts at level 3`() {
        val registry = HiredEntityRegistry()
        var hired: HiredEntityRegistry? = null
        repeat(50) {
            val candidate = registry.hireEntity(EntityDef.CASHIER)
            val entity = candidate.hiredEntities.last()
            if (entity.trait == EntityTrait.VETERAN) {
                hired = candidate
                return@repeat
            }
        }
        if (hired != null) {
            val veteran = hired!!.hiredEntities.last()
            assertEquals(3, veteran.level)
            assertTrue("VETERAN should start with enough XP for level 3", veteran.xp >= EntityDef.DEFAULT_XP_THRESHOLDS[1])
        }
    }

    // ── canPromote ───────────────────────────────────────────────────────────

    @Test
    fun `canPromote is false at level 2`() {
        val entity = HiredEntity(
            id = 1, name = "Test", entityDefinition = EntityDef.CASHIER,
            trait = EntityTrait.EFFICIENT, level = 2,
        )
        assertFalse(entity.canPromote)
    }

    @Test
    fun `canPromote is true at level 3`() {
        val entity = HiredEntity(
            id = 1, name = "Test", entityDefinition = EntityDef.CASHIER,
            trait = EntityTrait.EFFICIENT, level = 3,
        )
        assertTrue(entity.canPromote)
    }

    @Test
    fun `canPromote is false at MANAGER tier regardless of level`() {
        val entity = HiredEntity(
            id = 1, name = "Test", entityDefinition = EntityDef.CASHIER,
            trait = EntityTrait.EFFICIENT, tier = Tier.MANAGER, level = 5,
        )
        assertFalse(entity.canPromote)
    }

    // ── levelMultiplier ──────────────────────────────────────────────────────

    @Test
    fun `levelMultiplier at level 1 is 1_0`() {
        val entity = HiredEntity(
            id = 1, name = "Test", entityDefinition = EntityDef.CASHIER,
            trait = EntityTrait.EFFICIENT, level = 1,
        )
        assertEquals(1.0f, entity.levelMultiplier, 0.001f)
    }

    @Test
    fun `levelMultiplier at level 5 is 1_4`() {
        val entity = HiredEntity(
            id = 1, name = "Test", entityDefinition = EntityDef.CASHIER,
            trait = EntityTrait.EFFICIENT, level = 5,
        )
        assertEquals(1.4f, entity.levelMultiplier, 0.001f)
    }

    // ── grantXpToAll ─────────────────────────────────────────────────────────

    @Test
    fun `grantXpDistributed grants XP to listed entities`() {
        val e1 = HiredEntity(id = 1, name = "A", entityDefinition = EntityDef.CASHIER, trait = EntityTrait.EFFICIENT)
        val e2 = HiredEntity(id = 2, name = "B", entityDefinition = EntityDef.CASHIER, trait = EntityTrait.EFFICIENT)
        val e3 = HiredEntity(id = 3, name = "C", entityDefinition = EntityDef.STOCKER, trait = EntityTrait.EFFICIENT)
        val reg = HiredEntityRegistry(entities = listOf(e1, e2, e3), nextEntityId = 4)

        val updated = reg.grantXpDistributed(listOf(1, 2), 100)
        assertEquals(50, updated.getById(1).xp)
        assertEquals(50, updated.getById(2).xp)
        assertEquals(0, updated.getById(3).xp)
    }
}
