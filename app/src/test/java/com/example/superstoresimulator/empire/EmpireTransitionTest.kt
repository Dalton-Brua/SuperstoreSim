package com.example.superstoresimulator.empire

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.empire.EmpireTransitionActions
import com.example.superstoresimulator.domain.empire.EmpireTuning
import com.example.superstoresimulator.domain.empire.RegionRegistry
import com.example.superstoresimulator.domain.empire.StoreDirection
import com.example.superstoresimulator.domain.empire.StoreUpgrade
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [EmpireTransitionActions] (TRACK B — Opening & Transition).
 * Pure-function driven: GameState built via copy(). No GameEngine, no Mockito.
 */
class EmpireTransitionTest {

    private val firstLocationFee = EmpireTuning.locationCost(2) // 2nd-location entry fee ($1M)

    /** A loop-1 home store rich enough to convert: Supercenter, plenty of cash. */
    private fun homeState(
        moneyDollars: Double = 5_000_000.0,
        size: StoreSize = StoreSize.SUPERCENTER,
        day: Int = 3,
    ): GameState = GameState().copy(
        storeName = "Dalton's Mart",
        money = Money.fromDollars(moneyDollars),
        currentStoreSize = size,
        currentTime = GameTime(totalMinutesElapsed = day.toLong() * 1440L),
    )

    /** A state already in empire mode with cash, for region/location/store-edit tests. */
    private fun empireState(moneyDollars: Double = 10_000_000.0): GameState =
        EmpireTransitionActions.enterEmpireMode(homeState(moneyDollars = moneyDollars))

    // ── enterEmpireMode ─────────────────────────────────────────────────────────

    @Test
    fun enterEmpireMode_flipsFlag_createsTwoStores_seedsRegions_deductsFee() {
        val start = homeState(moneyDollars = 5_000_000.0, size = StoreSize.SUPERCENTER, day = 3)
        val result = EmpireTransitionActions.enterEmpireMode(start)

        assertTrue("empire mode should be active", result.empireModeActive)
        assertEquals("should create exactly two stores", 2, result.secondaryStores.size)

        val converted = result.secondaryStores[0]
        assertEquals("Dalton's Mart", converted.storeName)
        assertEquals(RegionRegistry.HOME_REGION_ID, converted.regionId)
        assertEquals("converted home keeps loop-1 size", StoreSize.SUPERCENTER, converted.storeSize)
        assertNotNull("converted home carries operatingState", converted.operatingState)
        assertEquals("metrics seeded from current day", 3, converted.currentDayMetrics.dayIndex)

        val second = result.secondaryStores[1]
        assertEquals("Store #2", second.storeName)
        assertEquals(StoreSize.MOM_AND_POP, second.storeSize)
        assertNull("brand-new store has no operatingState", second.operatingState)

        assertEquals("regions seeded from registry", RegionRegistry.authored.size, result.regions.size)
        assertEquals("nextSecondaryStoreId advanced by 2", start.nextSecondaryStoreId + 2, result.nextSecondaryStoreId)
        assertEquals(
            "fee deducted",
            start.money - firstLocationFee,
            result.money,
        )
    }

    @Test
    fun enterEmpireMode_isIrreversibleNoOp_whenCalledAgain() {
        val once = EmpireTransitionActions.enterEmpireMode(homeState())
        val twice = EmpireTransitionActions.enterEmpireMode(once)
        assertSame("second entry is a no-op returning the same state", once, twice)
        assertEquals("no extra stores", 2, twice.secondaryStores.size)
    }

    @Test
    fun enterEmpireMode_isNoOp_whenCannotAfford() {
        // locationCost(2) is $1M; give less than that.
        val poor = homeState(moneyDollars = 500_000.0)
        val result = EmpireTransitionActions.enterEmpireMode(poor)
        assertSame("insufficient funds leaves state untouched", poor, result)
        assertFalse(result.empireModeActive)
    }

    // ── unlockRegion ────────────────────────────────────────────────────────────

    @Test
    fun unlockRegion_deductsCost_andFlipsUnlocked() {
        val state = empireState()
        // Region 1 (Rural County) costs $200K and starts locked.
        val target = RegionRegistry.byId(1)!!
        val result = EmpireTransitionActions.unlockRegion(state, 1)

        val region = result.regions.first { it.regionId == 1 }
        assertTrue("region should be unlocked", region.unlocked)
        assertEquals(state.money - target.unlockCost, result.money)
    }

    @Test
    fun unlockRegion_isNoOp_whenInsufficientFunds() {
        // Metro Heights (region 2) costs $1.5M; enter empire, then leave just under.
        val state = empireState().copy(money = Money.fromDollars(1_000_000.0))
        val result = EmpireTransitionActions.unlockRegion(state, 2)
        assertSame(state, result)
        assertFalse(result.regions.first { it.regionId == 2 }.unlocked)
    }

    @Test
    fun unlockRegion_isNoOp_whenAlreadyUnlocked() {
        // Home region (0) is unlocked on entry.
        val state = empireState()
        val result = EmpireTransitionActions.unlockRegion(state, RegionRegistry.HOME_REGION_ID)
        assertSame(state, result)
    }

    // ── openLocation ────────────────────────────────────────────────────────────

    @Test
    fun openLocation_addsStore_andChargesScalingCost() {
        val state = empireState()
        // 2 stores already exist → opening the 3rd location costs locationCost(3).
        val expectedCost = EmpireTuning.locationCost(state.secondaryStores.size + 1)
        val result = EmpireTransitionActions.openLocation(state, RegionRegistry.HOME_REGION_ID)

        assertEquals("one store added", state.secondaryStores.size + 1, result.secondaryStores.size)
        val added = result.secondaryStores.last()
        assertEquals("Store #3", added.storeName)
        assertEquals(StoreSize.MOM_AND_POP, added.storeSize)
        assertEquals(StoreDirection.BALANCED, added.direction)
        assertEquals(state.nextSecondaryStoreId + 1, result.nextSecondaryStoreId)
        assertEquals(state.money - expectedCost, result.money)
    }

    @Test
    fun openLocation_isNoOp_inLockedRegion() {
        val state = empireState()
        // Region 1 is unlocked-only after paying; here it is still locked.
        val result = EmpireTransitionActions.openLocation(state, 1)
        assertSame(state, result)
    }

    // ── buyStoreUpgrade ─────────────────────────────────────────────────────────

    @Test
    fun buyStoreUpgrade_addsFlag_andDeducts_duplicateIsNoOp() {
        val state = empireState()
        val storeId = state.secondaryStores[1].storeId
        val cost = EmpireTuning.upgradeCost(StoreUpgrade.MARKETING)

        val bought = EmpireTransitionActions.buyStoreUpgrade(state, storeId, StoreUpgrade.MARKETING)
        val store = bought.secondaryStores.first { it.storeId == storeId }
        assertTrue("upgrade flag set", StoreUpgrade.MARKETING in store.upgrades)
        assertEquals(state.money - cost, bought.money)

        val again = EmpireTransitionActions.buyStoreUpgrade(bought, storeId, StoreUpgrade.MARKETING)
        assertSame("buying a duplicate upgrade is a no-op", bought, again)
    }

    // ── expandStoreSize ─────────────────────────────────────────────────────────

    @Test
    fun expandStoreSize_bumpsTier_andDeducts() {
        val state = empireState()
        val storeId = state.secondaryStores[1].storeId // MOM_AND_POP
        val next = StoreSize.nextSize(StoreSize.MOM_AND_POP)!!
        val cost = next.upgradeCost!!

        val result = EmpireTransitionActions.expandStoreSize(state, storeId)
        val store = result.secondaryStores.first { it.storeId == storeId }
        assertEquals(next, store.storeSize)
        assertEquals(state.money - cost, result.money)
    }

    @Test
    fun expandStoreSize_isNoOp_atMaxSize() {
        val state = empireState()
        val storeId = state.secondaryStores[0].storeId // converted home is SUPERCENTER (max)
        val result = EmpireTransitionActions.expandStoreSize(state, storeId)
        assertSame("no size above Supercenter", state, result)
    }

    // ── setStoreDirection ───────────────────────────────────────────────────────

    @Test
    fun setStoreDirection_changesDirection_whenPlayerManaged() {
        val state = empireState()
        val storeId = state.secondaryStores[1].storeId
        val result = EmpireTransitionActions.setStoreDirection(state, storeId, StoreDirection.AGGRESSIVE)
        val store = result.secondaryStores.first { it.storeId == storeId }
        assertEquals(StoreDirection.AGGRESSIVE, store.direction)
    }

    @Test
    fun setStoreDirection_isNoOp_whenManagedByRegionalManager() {
        val state = empireState()
        val storeId = state.secondaryStores[1].storeId
        // Mark the store as manager-owned.
        val managed = state.copy(
            secondaryStores = state.secondaryStores.map {
                if (it.storeId == storeId) it.copy(managedByRegionalManager = true) else it
            },
        )
        val result = EmpireTransitionActions.setStoreDirection(managed, storeId, StoreDirection.PASSIVE)
        assertSame("manager owns direction — player change ignored", managed, result)
    }
}
