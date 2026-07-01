package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.time.GameTime
import kotlinx.serialization.Serializable

/** Automates direction-setting across all managed stores per its personality. */
@Serializable
data class RegionalManager(
    val name: String,
    val personality: ManagerPersonality,
    val salaryPerDay: Money = EmpireTuning.MANAGER_SALARY_PER_DAY,
    val hiredAtTime: GameTime = GameTime(0),
)

@Serializable
enum class ManagerPersonality(val preferred: StoreDirection, val displayName: String) {
    AGGRESSIVE(StoreDirection.AGGRESSIVE, "Growth Hawk"),
    PASSIVE(StoreDirection.PASSIVE, "Margin Maximizer"),
    DEFENSIVE(StoreDirection.DEFENSIVE, "Risk Manager"),
}
