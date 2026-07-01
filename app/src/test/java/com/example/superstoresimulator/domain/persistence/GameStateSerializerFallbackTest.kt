package com.example.superstoresimulator.domain.persistence

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.StaffShift
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression coverage for #6/#7 from the engine code review: a single field that fails a
 * constructor invariant (or references an unknown registry key) used to throw during
 * [GameStateSerializer.deserialize], returning null and losing the entire save. The fix is
 * [TolerantListSerializer] on the two fragile lists (GameState.staffSchedules,
 * HiredEntityRegistry's entities) — it drops the one bad element instead of failing the
 * whole array (and by extension the whole decode).
 */
class GameStateSerializerFallbackTest {

    @Test
    fun deserialize_dropsOneInvalidShift_insteadOfLosingTheWholeSave() {
        val state = GameState().copy(
            money = Money(12345),
            storeName = "My Store",
            staffSchedules = listOf(
                StaffShift(entityId = 1, startHour = 6, durationHours = 8),
                StaffShift(entityId = 2, startHour = 13, durationHours = 8),
            ),
        )
        val raw = GameStateSerializer.serialize(state)

        // Corrupt one persisted shift the way a bad migration or hand-edited save could:
        // durationHours=14 violates StaffShift.init's require(durationHours in 2..8), so
        // decoding that element directly would throw.
        val corrupted = JSONObject(raw).apply {
            getJSONArray("staffSchedules").getJSONObject(0).put("durationHours", 14)
        }.toString()

        val result = GameStateSerializer.deserialize(corrupted)

        assertNotNull("save recovers instead of returning null", result)
        assertEquals("core fields survive", Money(12345), result!!.money)
        assertEquals("My Store", result.storeName)
        assertEquals(
            "the one invalid shift is dropped, the valid one survives",
            listOf(StaffShift(entityId = 2, startHour = 13, durationHours = 8)),
            result.staffSchedules,
        )
    }

    @Test
    fun deserialize_dropsOneUnresolvableHire_insteadOfLosingTheWholeSave() {
        var registry = GameState().hiredEntityRegistry
        registry = registry.hireEntity(EntityDef.CASHIER)
        registry = registry.hireEntity(EntityDef.CASHIER)
        val state = GameState().copy(money = Money(500), hiredEntityRegistry = registry)
        val raw = GameStateSerializer.serialize(state)

        // Simulate a renamed/removed EntityDef.key: HiredEntitySerializer errors on decode
        // when it can't resolve the key against EntityDef.allEntities.
        val corrupted = JSONObject(raw).apply {
            val entities = getJSONObject("hiredEntityRegistry").getJSONArray("entities")
            entities.getJSONObject(0).put("entityDefKey", "no_longer_exists")
        }.toString()

        val result = GameStateSerializer.deserialize(corrupted)

        assertNotNull("save recovers instead of returning null", result)
        assertEquals("core fields survive", Money(500), result!!.money)
        assertEquals(
            "the one unresolvable hire is dropped, the other survives",
            1,
            result.hiredEntityRegistry.hiredEntities.size,
        )
    }

    @Test
    fun deserialize_returnsNull_whenRawIsNotJson() {
        assertEquals(null, GameStateSerializer.deserialize("not json at all"))
    }
}
