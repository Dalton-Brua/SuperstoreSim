package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.research.ResearchManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResearchTickProcessor @Inject constructor(
    private val researchManager: ResearchManager,
) {
    private var prevState: GameState? = null

    fun process(state: GameState): GameState {
        val prev = prevState ?: run {
            prevState = state
            return state
        }

        var totalPoints = 0f

        // Count transactions completed since last tick
        val txDelta = state.totalTransactionsCompleted - prev.totalTransactionsCompleted
        if (txDelta > 0) totalPoints += txDelta * ResearchManager.TRANSACTION_INSIGHT

        // Count items stocked
        val stockedDelta = state.currentDayMetrics.itemsStocked - prev.currentDayMetrics.itemsStocked
        if (stockedDelta > 0) totalPoints += stockedDelta * ResearchManager.STOCK_INSIGHT

        // Count lost customers (pending customers that dropped — traffic proxy)
        val lostDelta = state.currentDayMetrics.itemsLostToOutOfStock -
            prev.currentDayMetrics.itemsLostToOutOfStock
        if (lostDelta > 0) totalPoints += lostDelta * ResearchManager.LOST_CUSTOMER_INSIGHT

        prevState = state

        if (totalPoints <= 0f) return state

        var s = researchManager.distributeInsightPoints(state, totalPoints)
        s = researchManager.checkCompletions(s)
        return s
    }
}
