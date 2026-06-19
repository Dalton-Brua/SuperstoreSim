package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.research.ResearchGates
import com.example.superstoresimulator.ui.state.ReputationUIState

fun buildReputationUiState(domain: GameState): ReputationUIState {
    val rep = domain.reputationState
    val isActive = domain.currentStoreSize.baseRevenueTarget != null
        && domain.researchState.researchedUpgrades.contains(ResearchGates.REVENUE_REPUTATION)
        && rep.daysTracked > 0
    return ReputationUIState(
        isActive = isActive,
        reputationScore = rep.reputationScore,
        trafficMultiplier = rep.trafficMultiplier,
        priceToleranceMultiplier = rep.priceToleranceMultiplier,
        supplierDiscountBonus = rep.supplierDiscountBonus,
        currentRevenueTarget = rep.currentRevenueTarget,
        lastRevenueScore = rep.lastRevenueScore,
        lastStockScore = rep.lastStockScore,
        lastAppearanceScore = rep.lastAppearanceScore,
        lastDailyComposite = rep.lastDailyComposite,
        consecutiveTargetHits = rep.consecutiveTargetHits,
        consecutiveTargetMisses = rep.consecutiveTargetMisses,
        goobSaleActiveToday = rep.goobSaleActiveToday,
    )
}
