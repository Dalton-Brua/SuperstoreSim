package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.ui.state.PricingUIState

fun buildPricingUiState(domain: GameState): PricingUIState {
    val pricing = domain.pricingState
    val reputationLabel = when {
        pricing.smoothedPriceIndex < 0.9f -> "Budget"
        pricing.smoothedPriceIndex > 1.1f -> "Premium"
        else -> "Standard"
    }
    return PricingUIState(
        pricingState = pricing,
        priceIndex = pricing.smoothedPriceIndex,
        trafficMultiplier = pricing.priceTrafficMultiplier,
        basketMultiplier = pricing.basketSizeMultiplier,
        reputationLabel = reputationLabel,
        itemsMarkedDown = pricing.activeMarkdowns.size,
    )
}
