package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.empire.EmpireTuning
import com.example.superstoresimulator.domain.empire.RegionRegistry
import com.example.superstoresimulator.domain.empire.SecondaryStore
import com.example.superstoresimulator.domain.empire.SecondaryStoreSimManager
import com.example.superstoresimulator.domain.research.ResearchGates
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.ui.state.EmpireClockUI
import com.example.superstoresimulator.ui.state.EmpireUIState
import com.example.superstoresimulator.ui.state.ManagerUI
import com.example.superstoresimulator.ui.state.RegionUI
import com.example.superstoresimulator.ui.state.StoreUI

/**
 * Builds the read-only empire UI contract from [GameState]. Pure read — no logic the
 * sim owns. Saturation load uses [SecondaryStoreSimManager.storeWeight] so it tracks
 * whatever Track A implements.
 */
fun buildEmpireUiState(domain: GameState): EmpireUIState {
    val currentDay = domain.currentTime.dayNumber
    val storesByRegion = domain.secondaryStores.groupBy { it.regionId }

    val regions = domain.regions
        .sortedBy { it.regionId }
        .map { region ->
            val stores = storesByRegion[region.regionId].orEmpty()
            val totalWeight = stores.sumOf { SecondaryStoreSimManager.storeWeight(it).toDouble() }.toFloat()
            val load = if (region.capacity > 0f) totalWeight / region.capacity else 0f
            RegionUI(
                regionId = region.regionId,
                name = region.name,
                unlocked = region.unlocked,
                unlockCost = region.unlockCost,
                load = load,
                overCapacity = load > 1.0f,
                baseSpendingPower = region.demandProfile.baseSpendingPower,
                baseTraffic = region.demandProfile.baseTraffic,
                stores = stores.map { it.toStoreUI(currentDay) },
            )
        }

    val mgr = domain.regionalManager?.let { m ->
        val onPreferred = domain.secondaryStores.count { it.direction == m.personality.preferred }
        ManagerUI(
            name = m.name,
            personality = m.personality,
            salaryPerDay = m.salaryPerDay,
            storesOnPreferred = onPreferred,
            storesDeviating = domain.secondaryStores.size - onPreferred,
        )
    }

    return EmpireUIState(
        active = domain.empireModeActive,
        money = domain.money,
        canEnterEmpire = !domain.empireModeActive &&
            ResearchGates.isResearched(domain.researchState.researchedUpgrades, ResearchGates.SECOND_LOCATION),
        regions = regions,
        manager = mgr,
        canHireManager = domain.empireModeActive && domain.regionalManager == null &&
            domain.secondaryStores.size >= EmpireTuning.MANAGER_HIRE_MIN_STORES,
        clock = EmpireClockUI(
            speed = domain.empireClock.speed,
            pendingDecisionReason = domain.empireClock.pendingDecisionReason,
            currentDayIndex = currentDay,
        ),
        operatingStoreId = domain.operatingStoreId,
    )
}

private fun SecondaryStore.toStoreUI(currentDay: Int): StoreUI = StoreUI(
    storeId = storeId,
    name = storeName,
    size = storeSize,
    direction = direction,
    managedByManager = managedByRegionalManager,
    upgrades = upgrades,
    today = currentDayMetrics,
    history = completedDayMetrics,
    operatingPerformance = operatingPerformance,
    daysSinceOperated = lastOperatedDay?.let { currentDay - it },
    unprofitable = currentDayMetrics.netProfit.cents < 0L,
    expandCost = StoreSize.nextSize(storeSize)?.upgradeCost,
)
