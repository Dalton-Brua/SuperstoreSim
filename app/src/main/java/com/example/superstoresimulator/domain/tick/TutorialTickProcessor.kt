package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.tutorial.TutorialManager
import com.example.superstoresimulator.domain.tutorial.TutorialStep
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TutorialTickProcessor @Inject constructor(
    private val tutorialManager: TutorialManager,
) {
    private var prevState: GameState? = null

    fun reset() { prevState = null }

    fun process(state: GameState): GameState {
        val tutorial = state.tutorialState
        if (tutorial.tutorialComplete) return state

        val prev = prevState ?: run {
            prevState = state
            return state
        }
        prevState = state

        return when (tutorial.currentStep) {
            TutorialStep.WELCOME -> {
                // Auto-advance on first tick (player sees the welcome)
                tutorialManager.advance(state, TutorialStep.WELCOME)
            }
            TutorialStep.ORDER_FIRST_ITEM -> {
                // Advance when any order is placed (truck is scheduled)
                val prevTruckCount = prev.scheduledTrucks.sumOf { it.orders.size }
                val newTruckCount = state.scheduledTrucks.sumOf { it.orders.size }
                if (newTruckCount > prevTruckCount) {
                    tutorialManager.advance(state, TutorialStep.ORDER_FIRST_ITEM)
                } else state
            }
            TutorialStep.WAIT_FOR_DELIVERY -> {
                // Advance when any item arrives in backroom (truck delivered)
                val prevBackroom = prev.inventory.values.sumOf { it.backroomStock }
                val newBackroom = state.inventory.values.sumOf { it.backroomStock }
                if (newBackroom > prevBackroom) {
                    tutorialManager.advance(state, TutorialStep.WAIT_FOR_DELIVERY)
                } else state
            }
            TutorialStep.STOCK_SHELVES -> {
                // Advance when any item is moved to shelf
                val prevShelf = prev.inventory.values.sumOf { it.shelfStock }
                val newShelf = state.inventory.values.sumOf { it.shelfStock }
                if (newShelf > prevShelf) {
                    tutorialManager.advance(state, TutorialStep.STOCK_SHELVES)
                } else state
            }
            TutorialStep.OPEN_STORE -> {
                // Advance when store opens
                val prevOpen = prev.storeState != com.example.superstoresimulator.domain.store.StoreState.CLOSED
                val nowOpen = state.storeState != com.example.superstoresimulator.domain.store.StoreState.CLOSED
                if (!prevOpen && nowOpen) {
                    tutorialManager.advance(state, TutorialStep.OPEN_STORE)
                } else state
            }
            TutorialStep.FIRST_SALE -> {
                // Advance when a transaction is started
                val prevActive = prev.registers.any { it.transactionActive }
                val nowActive = state.registers.any { it.transactionActive }
                if (!prevActive && nowActive) {
                    tutorialManager.advance(state, TutorialStep.FIRST_SALE)
                } else state
            }
            TutorialStep.COMPLETE_TRANSACTION -> {
                // Advance when a transaction is completed
                if (state.totalTransactionsCompleted > prev.totalTransactionsCompleted) {
                    tutorialManager.advance(state, TutorialStep.COMPLETE_TRANSACTION)
                } else state
            }
            TutorialStep.HIRE_STAFF -> {
                // Advance when any staff is hired
                val prevStaff = prev.hiredEntityRegistry.hiredEntities.size
                val newStaff = state.hiredEntityRegistry.hiredEntities.size
                if (newStaff > prevStaff) {
                    tutorialManager.advance(state, TutorialStep.HIRE_STAFF)
                } else state
            }
            TutorialStep.HIRE_ANALYST -> {
                // Advance when a Market Analyst is hired
                val hadAnalyst = prev.hiredEntityRegistry.hiredEntities.any {
                    it.entityDefinition == EntityDef.MARKET_ANALYST
                }
                val hasAnalyst = state.hiredEntityRegistry.hiredEntities.any {
                    it.entityDefinition == EntityDef.MARKET_ANALYST
                }
                if (!hadAnalyst && hasAnalyst) {
                    tutorialManager.advance(state, TutorialStep.HIRE_ANALYST)
                } else state
            }
            TutorialStep.RESEARCH_FIRST_UNLOCK -> {
                // Advance when any research upgrade is completed.
                val prevUnlocked = prev.researchState.researchedUpgrades.size
                val newUnlocked = state.researchState.researchedUpgrades.size
                if (newUnlocked > prevUnlocked) {
                    tutorialManager.advance(state, TutorialStep.RESEARCH_FIRST_UNLOCK)
                } else state
            }
            TutorialStep.TUTORIAL_COMPLETE -> state
        }
    }
}
