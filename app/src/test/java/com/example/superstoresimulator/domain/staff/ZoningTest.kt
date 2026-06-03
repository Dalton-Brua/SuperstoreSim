package com.example.superstoresimulator.domain.staff

import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ZoningTest {

    private lateinit var staffManager: StaffManager

    @Before
    fun setUp() {
        staffManager = StaffManager()
    }

    // ── Zone score clamping ──────────────────────────────────────────────────

    @Test
    fun `zone score defaults to 1_0 for new inventory`() {
        val inv = InventoryState()
        assertEquals(1.0f, inv.zoneScore, 0.001f)
    }

    @Test
    fun `zone score can be set to 0_0`() {
        val inv = InventoryState(zoneScore = 0.0f)
        assertEquals(0.0f, inv.zoneScore, 0.001f)
    }

    // ── Zone purchase multiplier ─────────────────────────────────────────────

    @Test
    fun `zonePurchaseMultiplier at score 1_0 returns 1_0`() {
        assertEquals(1.0f, StaffManager.zonePurchaseMultiplier(1.0f), 0.001f)
    }

    @Test
    fun `zonePurchaseMultiplier at score 0_0 returns ZONE_FLOOR`() {
        assertEquals(StaffManager.ZONE_FLOOR, StaffManager.zonePurchaseMultiplier(0.0f), 0.001f)
    }

    @Test
    fun `zonePurchaseMultiplier at score 0_5 is between ZONE_FLOOR and 1_0`() {
        val mult = StaffManager.zonePurchaseMultiplier(0.5f)
        assertTrue(mult > StaffManager.ZONE_FLOOR)
        assertTrue(mult < 1.0f)
        val expected = StaffManager.ZONE_FLOOR + (0.5f * (1.0f - StaffManager.ZONE_FLOOR))
        assertEquals(expected, mult, 0.001f)
    }

    // ── Zoning progress advance ──────────────────────────────────────────────

    @Test
    fun `advanceZoningForStocker returns no actions without target`() {
        val actions = staffManager.advanceZoningForStocker(
            stockerId = 1, stockerWeight = 1.0f, delta = 100.0, multiplier = 1.0f,
        )
        assertTrue("No actions without a target", actions.isEmpty())
    }

    @Test
    fun `advanceZoningForStocker produces actions when target is assigned`() {
        staffManager.assignZoningTarget(stockerId = 1, itemId = 42)
        val actions = staffManager.advanceZoningForStocker(
            stockerId = 1, stockerWeight = 1.0f, delta = 20.0, multiplier = 1.0f,
        )
        assertTrue("Should produce zone actions over 20 game seconds", actions.isNotEmpty())
        assertTrue(actions.all { it.targetItemId == 42 })
    }

    @Test
    fun `advanceZoningForStocker returns 0 actions when weight is 0`() {
        staffManager.assignZoningTarget(stockerId = 1, itemId = 42)
        val actions = staffManager.advanceZoningForStocker(
            stockerId = 1, stockerWeight = 0f, delta = 100.0, multiplier = 1.0f,
        )
        assertTrue(actions.isEmpty())
    }

    // ── Zoning target management ─────────────────────────────────────────────

    @Test
    fun `assignZoningTarget sets target for stocker`() {
        staffManager.assignZoningTarget(stockerId = 1, itemId = 10)
        assertEquals(10, staffManager.getZoningTarget(1))
    }

    @Test
    fun `clearZoningTarget removes target`() {
        staffManager.assignZoningTarget(stockerId = 1, itemId = 10)
        staffManager.clearZoningTarget(1)
        assertEquals(null, staffManager.getZoningTarget(1))
    }

    @Test
    fun `allClaimedZoningTargets returns set of claimed item ids`() {
        staffManager.assignZoningTarget(stockerId = 1, itemId = 10)
        staffManager.assignZoningTarget(stockerId = 2, itemId = 20)
        assertEquals(setOf(10, 20), staffManager.allClaimedZoningTargets())
    }

    @Test
    fun `cleanUpZoningForEntity releases claimed target`() {
        staffManager.assignZoningTarget(stockerId = 1, itemId = 10)
        staffManager.cleanUpZoningForEntity(1)
        assertTrue(staffManager.allClaimedZoningTargets().isEmpty())
    }

    // ── Zone score on InventoryState ─────────────────────────────────────────

    @Test
    fun `avgZoneScore computed from items with shelf stock`() {
        val inv = mapOf(
            1 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 5, expirationDay = Int.MAX_VALUE)),
                zoneScore = 0.5f,
            ),
            2 to InventoryState(
                shelfBatches = listOf(ItemBatch(receivedDay = 0, quantity = 5, expirationDay = Int.MAX_VALUE)),
                zoneScore = 1.0f,
            ),
            3 to InventoryState(zoneScore = 0.0f),
        )
        val state = com.example.superstoresimulator.domain.GameState(inventory = inv)
        assertEquals(0.75f, state.avgZoneScore, 0.001f)
    }

    @Test
    fun `avgZoneScore returns 1_0 when no items have shelf stock`() {
        val inv = mapOf(
            1 to InventoryState(zoneScore = 0.0f),
        )
        val state = com.example.superstoresimulator.domain.GameState(inventory = inv)
        assertEquals(1.0f, state.avgZoneScore, 0.001f)
    }

    // ── Reset clears zoning state ────────────────────────────────────────────

    @Test
    fun `reset clears all zoning state`() {
        staffManager.assignZoningTarget(stockerId = 1, itemId = 10)
        staffManager.assignZoningTarget(stockerId = 2, itemId = 20)
        staffManager.reset()
        assertTrue(staffManager.allClaimedZoningTargets().isEmpty())
    }
}
