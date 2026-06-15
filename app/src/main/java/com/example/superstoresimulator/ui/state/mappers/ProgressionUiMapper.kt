package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Screen
import com.example.superstoresimulator.domain.research.ResearchUpgradeRegistry
import com.example.superstoresimulator.domain.tutorial.TutorialManager
import com.example.superstoresimulator.domain.tutorial.TutorialStep
import com.example.superstoresimulator.ui.state.AnalystUiInfo
import com.example.superstoresimulator.ui.state.ResearchUIState
import com.example.superstoresimulator.ui.state.TutorialUIState

fun buildResearchUiState(domain: GameState): ResearchUIState {
    val rs = domain.researchState
    val visible = ResearchUpgradeRegistry.allUpgrades.values.filter { upgrade ->
        val researched = upgrade.id in rs.researchedUpgrades
        val inProgress = rs.researchProgress.containsKey(upgrade.id)
        val prereqsMet = upgrade.prerequisites.all { it in rs.researchedUpgrades }
        val sizeMet = upgrade.requiredStoreSize?.let {
            domain.currentStoreSize.ordinal >= it.ordinal
        } ?: true
        val gateMet = upgrade.gateCheck?.invoke(domain) ?: true
        // Show researched/in-progress always; otherwise hide until every gate is met.
        researched || inProgress || (prereqsMet && sizeMet && gateMet)
    }
    val analysts = domain.hiredEntityRegistry.hiredEntities
        .filter { it.entityDefinition == EntityDef.MARKET_ANALYST }
        .map { AnalystUiInfo(id = it.id, name = it.name, assignment = rs.analystAssignments[it.id]) }

    return ResearchUIState(
        totalRevenue = domain.totalRevenue,
        researchedUpgrades = rs.researchedUpgrades,
        researchProgress = rs.researchProgress,
        totalPointsEarned = rs.totalPointsEarned,
        analystAssignments = rs.analystAssignments,
        visibleUpgrades = visible,
        analysts = analysts,
    )
}

fun buildTutorialUiState(domain: GameState, tutorialManager: TutorialManager): TutorialUIState {
    val ts = domain.tutorialState
    val step = ts.currentStep
    val hintScreen = if (ts.tutorialComplete) null else when (step) {
        TutorialStep.ORDER_FIRST_ITEM -> Screen.INVENTORY
        TutorialStep.HIRE_STAFF, TutorialStep.HIRE_ANALYST,
        TutorialStep.RESEARCH_FIRST_UNLOCK -> Screen.STAFF
        else -> Screen.GAME
    }
    return TutorialUIState(
        tutorialComplete = ts.tutorialComplete,
        currentStep = step,
        displayTitle = step.displayTitle,
        instruction = step.instruction,
        hintText = step.hintText,
        featureVisibility = tutorialManager.featureVisibility(domain),
        hintScreen = hintScreen,
    )
}
