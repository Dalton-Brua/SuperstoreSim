package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.store.StoreSize

/**
 * TRACK B (Opening & Transition) — fill the bodies. Wave-0 stub: no-ops that compile.
 *
 * Owns the empire-mode flip, home-store conversion, region unlock, location scaling
 * cost, opening, per-store upgrade + size-expansion purchases. All paid from the single
 * shared [GameState.money]. Additive — never touches loop-1 home-store fields except to
 * preserve them.
 */
object EmpireTransitionActions {

    /**
     * IRREVERSIBLE. Only call on explicit player confirm (the "Go corporate?" dialog).
     *  1. Derive a SecondaryStore from the home store: home region (id 0, auto-unlocked),
     *     storeSize = currentStoreSize, direction = BALANCED, carry mappable upgrades,
     *     seed currentDayMetrics from recent home performance (or zero), preserve loop-1
     *     detail into its operatingState.
     *  2. Append to secondaryStores; set empireModeActive = true; unlock home region;
     *     seed [GameState.regions] from [RegionRegistry.authored].
     *  3. Pay the 2nd-location scaling cost ([EmpireTuning.locationCost] with locationNumber=2).
     */
    fun enterEmpireMode(state: GameState): GameState {
        if (state.empireModeActive) return state
        val cost = EmpireTuning.locationCost(2)
        if (state.money < cost) return state

        val convertedHome = SecondaryStore(
            storeId = state.nextSecondaryStoreId,
            storeName = state.storeName,
            regionId = RegionRegistry.HOME_REGION_ID,
            storeSize = state.currentStoreSize,
            direction = StoreDirection.BALANCED,
            openedAtTime = state.currentTime,
            currentDayMetrics = SimStoreMetrics(dayIndex = state.currentTime.dayNumber),
            operatingState = StoreOperatingState(
                inventory = state.inventory,
                registers = state.registers,
                hiredEntityRegistry = state.hiredEntityRegistry,
                staffSchedules = state.staffSchedules,
                truckConfig = state.truckConfig,
            ),
        )

        val newSecond = SecondaryStore(
            storeId = state.nextSecondaryStoreId + 1,
            storeName = "Store #2",
            regionId = RegionRegistry.HOME_REGION_ID,
            storeSize = StoreSize.MOM_AND_POP,
            direction = StoreDirection.BALANCED,
            openedAtTime = state.currentTime,
            currentDayMetrics = SimStoreMetrics(dayIndex = state.currentTime.dayNumber),
            operatingState = null,
        )

        return state.copy(
            empireModeActive = true,
            regions = RegionRegistry.authored,
            secondaryStores = listOf(convertedHome, newSecond),
            nextSecondaryStoreId = state.nextSecondaryStoreId + 2,
            money = state.money - cost,
            empireClock = state.empireClock.copy(speed = EmpireSpeed.PAUSED),
        )
    }

    /** Unlock [regionId], paying its [Region.unlockCost] from money (no-op if already unlocked). */
    fun unlockRegion(state: GameState, regionId: Int): GameState {
        val region = state.regions.firstOrNull { it.regionId == regionId } ?: return state
        if (region.unlocked) return state
        val cost = region.unlockCost
        if (state.money < cost) return state
        return state.copy(
            money = state.money - cost,
            regions = state.regions.map {
                if (it.regionId == regionId) it.copy(unlocked = true) else it
            },
        )
    }

    /**
     * Open a new location in [regionId] (must be unlocked). Pays the next location scaling
     * cost (locationNumber = secondaryStores.size + 1). New store: base size, BALANCED.
     */
    fun openLocation(state: GameState, regionId: Int): GameState {
        if (!state.empireModeActive) return state
        val region = state.regions.firstOrNull { it.regionId == regionId } ?: return state
        if (!region.unlocked) return state
        val locationNumber = state.secondaryStores.size + 1
        val cost = EmpireTuning.locationCost(locationNumber)
        if (state.money < cost) return state

        val newStore = SecondaryStore(
            storeId = state.nextSecondaryStoreId,
            storeName = "Store #${state.secondaryStores.size + 1}",
            regionId = regionId,
            storeSize = StoreSize.MOM_AND_POP,
            direction = StoreDirection.BALANCED,
            openedAtTime = state.currentTime,
            currentDayMetrics = SimStoreMetrics(dayIndex = state.currentTime.dayNumber),
        )

        return state.copy(
            secondaryStores = state.secondaryStores + newStore,
            nextSecondaryStoreId = state.nextSecondaryStoreId + 1,
            money = state.money - cost,
        )
    }

    /**
     * Close (sell off) a store. Removes its weight from the region, which lowers regional
     * saturation and lifts traffic for the remaining stores. Refused while the store is being
     * operated hands-on. No salvage refund.
     */
    // ponytail: no salvage payout; add a size-based refund here if closing should return cash.
    fun closeStore(state: GameState, storeId: Int): GameState {
        if (!state.empireModeActive) return state
        if (state.operatingStoreId == storeId) return state
        if (state.secondaryStores.none { it.storeId == storeId }) return state
        return state.copy(secondaryStores = state.secondaryStores.filterNot { it.storeId == storeId })
    }

    /**
     * Turn ongoing region investment on or off. While on, the daily sim compounds the region's
     * capacity and demand (see [EmpireTuning.REGION_INVEST_DAILY_GROWTH]) and charges a daily
     * cost that scales with the region's growing population — easing saturation and raising sales
     * over months, not days. No upfront lump; the spend is recurring.
     */
    fun setRegionInvesting(state: GameState, regionId: Int, investing: Boolean): GameState {
        val region = state.regions.firstOrNull { it.regionId == regionId } ?: return state
        if (!region.unlocked || region.investing == investing) return state
        return state.copy(
            regions = state.regions.map { if (it.regionId == regionId) it.copy(investing = investing) else it },
        )
    }

    /** Player sets a store's direction manually (ignored if managedByRegionalManager). */
    fun setStoreDirection(state: GameState, storeId: Int, direction: StoreDirection): GameState {
        val store = state.secondaryStores.firstOrNull { it.storeId == storeId } ?: return state
        if (store.managedByRegionalManager) return state
        return state.copy(
            secondaryStores = mapStore(state, storeId) { it.copy(direction = direction) },
        )
    }

    /** Buy a [StoreUpgrade] flag for a store, paying [EmpireTuning.upgradeCost]. */
    fun buyStoreUpgrade(state: GameState, storeId: Int, upgrade: StoreUpgrade): GameState {
        val store = state.secondaryStores.firstOrNull { it.storeId == storeId } ?: return state
        if (upgrade in store.upgrades) return state
        val cost = EmpireTuning.upgradeCost(upgrade)
        if (state.money < cost) return state
        return state.copy(
            money = state.money - cost,
            secondaryStores = mapStore(state, storeId) { it.copy(upgrades = it.upgrades + upgrade) },
        )
    }

    /** Bump a store's StoreSize to the next tier, paying StoreSize.upgradeCost. */
    fun expandStoreSize(state: GameState, storeId: Int): GameState {
        val store = state.secondaryStores.firstOrNull { it.storeId == storeId } ?: return state
        val next = StoreSize.nextSize(store.storeSize) ?: return state
        val cost = next.upgradeCost ?: return state
        if (state.money < cost) return state
        return state.copy(
            money = state.money - cost,
            secondaryStores = mapStore(state, storeId) { it.copy(storeSize = next) },
        )
    }

    /** Replace the single store matching [storeId] in secondaryStores via [transform]. */
    private inline fun mapStore(
        state: GameState,
        storeId: Int,
        transform: (SecondaryStore) -> SecondaryStore,
    ): List<SecondaryStore> =
        state.secondaryStores.map { if (it.storeId == storeId) transform(it) else it }
}
