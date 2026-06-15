package com.example.superstoresimulator.domain.tutorial

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.GameState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TutorialManager @Inject constructor() {

    fun advance(state: GameState, completedStep: TutorialStep): GameState {
        val tutorial = state.tutorialState
        if (tutorial.tutorialComplete) return state
        if (tutorial.currentStep != completedStep) return state

        val nextStep = TutorialStep.entries.getOrNull(completedStep.ordinal + 1)
            ?: return markComplete(state)

        val updatedTutorial = tutorial.copy(
            currentStep = nextStep,
            completedSteps = tutorial.completedSteps + completedStep,
        )

        return if (nextStep == TutorialStep.TUTORIAL_COMPLETE) {
            markComplete(state.copy(tutorialState = updatedTutorial))
        } else {
            state.copy(tutorialState = updatedTutorial)
        }
    }

    fun skip(state: GameState): GameState = markComplete(state)

    fun dismissHint(state: GameState, hintKey: String): GameState {
        return state.copy(
            tutorialState = state.tutorialState.copy(
                dismissedHints = state.tutorialState.dismissedHints + hintKey,
            )
        )
    }

    private fun markComplete(state: GameState): GameState {
        return state.copy(
            tutorialState = state.tutorialState.copy(
                tutorialComplete = true,
                completedSteps = TutorialStep.entries.toSet(),
            )
        )
    }

    /**
     * Whether a gated UI feature should be shown. Early features unlock as the
     * tutorial progresses; the rest unlock once the tutorial is complete and the
     * relevant research has been finished. Unknown keys default to visible.
     */
    fun isFeatureVisible(state: GameState, feature: String): Boolean {
        val ts = state.tutorialState
        val researched = state.researchState.researchedUpgrades
        fun stepDone(step: TutorialStep) = ts.tutorialComplete || step in ts.completedSteps
        return when (feature) {
            FEATURE_ORDER_ITEMS -> stepDone(TutorialStep.WELCOME)
            FEATURE_STORE_TOGGLE -> stepDone(TutorialStep.WELCOME)
            // Visible right after unpausing so the player can cashier/stock and earn
            // revenue from starter stock before the first truck arrives.
            FEATURE_PLAYER_ROLES -> stepDone(TutorialStep.WELCOME)
            FEATURE_HIRE_STAFF -> stepDone(TutorialStep.COMPLETE_TRANSACTION)
            FEATURE_HIRE_ANALYST -> stepDone(TutorialStep.HIRE_STAFF)
            FEATURE_RESEARCH_TAB -> ts.tutorialComplete
            FEATURE_PRICING_UI -> ts.tutorialComplete && "category_pricing" in researched
            FEATURE_DELIVERY_SETTINGS -> ts.tutorialComplete && "extra_truck_slots" in researched
            FEATURE_STAFF_SCHEDULING -> ts.tutorialComplete && "staff_scheduling" in researched
            FEATURE_REGISTERS -> ts.tutorialComplete && "register_expansion" in researched
            FEATURE_BULK_ORDERING -> ts.tutorialComplete && "bulk_ordering" in researched
            FEATURE_MANAGER_HIRING -> ts.tutorialComplete && "manager_hiring" in researched
            else -> true
        }
    }

    /** Precompute visibility for every gated feature, for the UI layer. */
    fun featureVisibility(state: GameState): Map<String, Boolean> =
        ALL_FEATURES.associateWith { isFeatureVisible(state, it) }

    companion object {
        const val FEATURE_ORDER_ITEMS = "order_items"
        const val FEATURE_STORE_TOGGLE = "store_toggle"
        const val FEATURE_PLAYER_ROLES = "player_roles"
        const val FEATURE_HIRE_STAFF = "hire_staff"
        const val FEATURE_HIRE_ANALYST = "hire_analyst"
        const val FEATURE_RESEARCH_TAB = "research_tab"
        const val FEATURE_PRICING_UI = "pricing_ui"
        const val FEATURE_DELIVERY_SETTINGS = "delivery_settings"
        const val FEATURE_STAFF_SCHEDULING = "staff_scheduling"
        const val FEATURE_REGISTERS = "registers"
        const val FEATURE_BULK_ORDERING = "bulk_ordering"
        const val FEATURE_MANAGER_HIRING = "manager_hiring"

        val ALL_FEATURES = listOf(
            FEATURE_ORDER_ITEMS, FEATURE_STORE_TOGGLE, FEATURE_PLAYER_ROLES,
            FEATURE_HIRE_STAFF, FEATURE_HIRE_ANALYST, FEATURE_RESEARCH_TAB,
            FEATURE_PRICING_UI, FEATURE_DELIVERY_SETTINGS, FEATURE_STAFF_SCHEDULING,
            FEATURE_REGISTERS, FEATURE_BULK_ORDERING, FEATURE_MANAGER_HIRING,
        )
    }
}
