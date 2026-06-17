package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.research.ResearchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildingPurchaseFlowTest {

    private val controller = StoreController()

    private fun readyToBuy(money: Money = Money(100_000_000L)): GameState = GameState(
        currentStoreSize = StoreSize.SUPERCENTER,
        money = money,
        buildingOwned = false,
        researchState = ResearchState(
            researchedUpgrades = setOf("store_supercenter", "building_purchase"),
        ),
    )

    // ── Happy path ───────────────────────────────────────────────────────────

    @Test
    fun `purchaseBuilding deducts cost and sets buildingOwned true`() {
        val state = readyToBuy()
        val result = controller.purchaseBuilding(state)
        assertTrue(result.buildingOwned)
        assertEquals(state.money - StoreController.BUILDING_PURCHASE_COST, result.money)
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    @Test
    fun `purchaseBuilding no-op when already owned`() {
        val state = readyToBuy().copy(buildingOwned = true)
        val result = controller.purchaseBuilding(state)
        assertEquals(state, result)
    }

    @Test
    fun `purchaseBuilding no-op when insufficient funds`() {
        val state = readyToBuy(money = Money(10_000_000L)) // $100K, below $500K
        val result = controller.purchaseBuilding(state)
        assertFalse(result.buildingOwned)
        assertEquals(state.money, result.money)
    }

    @Test
    fun `purchaseBuilding no-op when research not complete`() {
        val state = readyToBuy().copy(
            researchState = ResearchState(researchedUpgrades = setOf("store_supercenter")),
        )
        val result = controller.purchaseBuilding(state)
        assertFalse(result.buildingOwned)
        assertEquals(state.money, result.money)
    }
}
