package com.example.superstoresimulator.domain.research

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface AnalystAssignment {
    @Serializable
    @SerialName("research")
    data class Research(val upgradeId: String) : AnalystAssignment

    @Serializable
    @SerialName("consulting")
    data object Consulting : AnalystAssignment
}

@Serializable
data class ResearchState(
    val researchProgress: Map<String, Float> = emptyMap(),
    val totalPointsEarned: Float = 0f,
    val researchedUpgrades: Set<String> = emptySet(),
    val analystAssignments: Map<Int, AnalystAssignment> = emptyMap(),
)
