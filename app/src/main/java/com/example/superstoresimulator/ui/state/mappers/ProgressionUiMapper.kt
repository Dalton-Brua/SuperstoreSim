package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.state.ProgressionUIState

fun buildProgressionUiState(domain: GameState, old: ProgressionUIState?): ProgressionUIState {
    val nextTier = ItemUnlockTier.nextTier(domain.currentTier)
    val tierStart = domain.currentTier.unlockAmount
    val tierEnd = nextTier?.unlockAmount
    val earned = domain.totalRevenue.cents
    val availableTier = nextTier?.takeIf { earned >= it.unlockAmount }
    val justUnlocked = if (old != null && domain.currentTier != old.currentTier)
        domain.currentTier
    else
        old?.justUnlockedTier
    return ProgressionUIState(
        currentTier = domain.currentTier,
        totalRevenue = domain.totalRevenue,
        nextTier = nextTier,
        revenueToNextTier = tierEnd?.let { Money(it - earned) },
        tierProgressFraction = if (tierEnd != null && tierEnd > tierStart)
            ((earned - tierStart).toFloat() / (tierEnd - tierStart)).coerceIn(0f, 1f)
        else 1f,
        justUnlockedTier = justUnlocked,
        availableTier = availableTier,
    )
}
