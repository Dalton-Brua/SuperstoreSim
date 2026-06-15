package com.example.superstoresimulator.domain.tutorial

import kotlinx.serialization.Serializable

@Serializable
data class TutorialState(
    val currentStep: TutorialStep = TutorialStep.WELCOME,
    val completedSteps: Set<TutorialStep> = emptySet(),
    val tutorialComplete: Boolean = false,
    val dismissedHints: Set<String> = emptySet(),
)
