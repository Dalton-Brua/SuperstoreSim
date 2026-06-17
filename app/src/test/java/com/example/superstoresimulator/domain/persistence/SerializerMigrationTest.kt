package com.example.superstoresimulator.domain.persistence

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.store.StoreSize
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializerMigrationTest {

    private fun minimalSaveJson(overrides: Map<String, Any> = emptyMap()): String {
        val json = JSONObject()
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
        json.put("storeConfig", JSONObject().apply {
            put("openTimeMinutes", 480)
            put("closeTimeMinutes", 1260)
            put("closingProcedureDuration", 30)
            put("allowTransactionsDuringClosing", true)
            put("gameSpeedMultiplier", 1.0)
            put("backroomCapPerItem", 2)
        })
        json.put("currentTransaction", JSONObject())
        json.put("salesHistory", org.json.JSONArray())
        json.put("pendingRefunds", org.json.JSONArray())
        json.put("inventory", JSONObject())
        json.put("hiredEntityRegistry", JSONObject().apply {
            put("entities", org.json.JSONArray())
            put("nextEntityId", 1)
        })

        for ((key, value) in overrides) {
            json.put(key, value)
        }
        return json.toString()
    }

    // ── PlayerRole.NONE → MANAGE migration ───────────────────────────────────

    @Test
    fun `legacy playerRole NONE deserializes as MANAGE`() {
        val json = minimalSaveJson(mapOf("playerRole" to "NONE"))
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(PlayerRole.MANAGE, state!!.playerRole)
    }

    @Test
    fun `current playerRole MANAGE round-trips correctly`() {
        val json = minimalSaveJson(mapOf("playerRole" to "MANAGE"))
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(PlayerRole.MANAGE, state!!.playerRole)
    }

    @Test
    fun `playerRole CASHIER round-trips correctly`() {
        val json = minimalSaveJson(mapOf("playerRole" to "CASHIER"))
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(PlayerRole.CASHIER, state!!.playerRole)
    }

    // ── autoHireBudget defaults ──────────────────────────────────────────────

    @Test
    fun `missing autoHireBudget defaults to daily rent`() {
        val json = minimalSaveJson()
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(StoreSize.MOM_AND_POP.dailyRent, state!!.autoHireBudget)
    }

    @Test
    fun `autoHireBudget round-trips correctly`() {
        val json = minimalSaveJson(mapOf("autoHireBudget" to 50000L))
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(Money(50000L), state!!.autoHireBudget)
    }

    // ── zoneScore defaults ───────────────────────────────────────────────────

    @Test
    fun `missing zoneScore defaults to 1_0`() {
        val invJson = JSONObject().apply {
            put("1", JSONObject().apply {
                put("shelfBatches", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("receivedDay", 0)
                        put("quantity", 10)
                        put("expirationDay", Int.MAX_VALUE)
                    })
                })
                put("backroomBatches", org.json.JSONArray())
            })
        }
        val json = minimalSaveJson(mapOf("inventory" to invJson))
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(1.0f, state!!.inventory[1]!!.zoneScore, 0.001f)
    }

    @Test
    fun `zoneScore round-trips correctly`() {
        val invJson = JSONObject().apply {
            put("1", JSONObject().apply {
                put("shelfBatches", org.json.JSONArray())
                put("backroomBatches", org.json.JSONArray())
                put("zoneScore", 0.42)
            })
        }
        val json = minimalSaveJson(mapOf("inventory" to invJson))
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(0.42f, state!!.inventory[1]!!.zoneScore, 0.001f)
    }

    // ── Register system migration ────────────────────────────────────────────

    @Test
    fun `missing registers field creates single register from legacy fields`() {
        val json = minimalSaveJson()
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(1, state!!.registers.size)
        assertEquals(0, state.registers[0].registerId)
    }

    // ── Full round-trip ──────────────────────────────────────────────────────

    @Test
    fun `serialize then deserialize preserves key fields`() {
        val original = GameState(
            storeName = "RoundTrip",
            money = Money(12345L),
            playerRole = PlayerRole.MANAGE,
            autoHireBudget = Money(5000L),
        )
        val serialized = GameStateSerializer.serialize(original)
        val restored = GameStateSerializer.deserialize(serialized)
        assertNotNull(restored)
        assertEquals("RoundTrip", restored!!.storeName)
        assertEquals(Money(12345L), restored.money)
        assertEquals(PlayerRole.MANAGE, restored.playerRole)
        assertEquals(Money(5000L), restored.autoHireBudget)
    }

    // ── Building purchase serialization ─────────────────────────────────────

    @Test
    fun `GameState with buildingOwned true round-trips through JSON`() {
        val original = GameState(buildingOwned = true)
        val serialized = GameStateSerializer.serialize(original)
        val restored = GameStateSerializer.deserialize(serialized)
        assertNotNull(restored)
        assertTrue(restored!!.buildingOwned)
    }

    @Test
    fun `old save without buildingOwned deserializes with default false`() {
        val json = minimalSaveJson()
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertFalse(state!!.buildingOwned)
    }

    // ── Truck capacity tier serialization ────────────────────────────────────

    @Test
    fun `GameState with truckCapacityTier 2 round-trips through JSON`() {
        val original = GameState(
            truckConfig = TruckConfig(truckCapacityTier = 2),
        )
        val serialized = GameStateSerializer.serialize(original)
        val restored = GameStateSerializer.deserialize(serialized)
        assertNotNull(restored)
        assertEquals(2, restored!!.truckConfig.truckCapacityTier)
    }

    @Test
    fun `old save without truckCapacityTier deserializes with default 0`() {
        val json = minimalSaveJson()
        val state = GameStateSerializer.deserialize(json)
        assertNotNull(state)
        assertEquals(0, state!!.truckConfig.truckCapacityTier)
    }
}
