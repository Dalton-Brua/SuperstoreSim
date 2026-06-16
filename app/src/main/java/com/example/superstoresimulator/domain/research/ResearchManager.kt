package com.example.superstoresimulator.domain.research

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreSize
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class ResearchManager @Inject constructor(
    private val itemMetadataCache: ItemMetadataCache,
) {
    companion object {
        const val TRANSACTION_INSIGHT = 0.3f
        const val STOCK_INSIGHT = 0.05f
        const val LOST_CUSTOMER_INSIGHT = 0.15f
        val CONSULTING_CASH_PER_POINT = Money(500L) // $5.00

        /** Starter units placed on the shelf and in the backroom when a research-gated item unlocks. */
        const val SEED_QTY = 10
    }

    fun distributeInsightPoints(state: GameState, baseEventPoints: Float): GameState {
        val currentHour = state.currentTime.hour

        // Canonical staff weighting (throughputWeight * levelMultiplier * trait.throughputMultiplier)
        // lives in StaffManager — reuse it so analyst traits scale insight/consulting like every other role.
        val activeWeights = StaffManager.activeWeightedCountWithIds(
            EntityDef.MARKET_ANALYST,
            currentHour,
            state.staffSchedules,
            state.hiredEntityRegistry,
        )
        if (activeWeights.onShiftIds.isEmpty()) return state

        val weightByAnalystId = activeWeights.onShiftIds.zip(activeWeights.perEntityWeights)

        // Group by assignment
        val researchGroups = mutableMapOf<String, MutableList<Float>>()
        var cashEarned = Money.ZERO

        for ((analystId, effectiveWeight) in weightByAnalystId) {
            val assignment = state.researchState.analystAssignments[analystId]
            when (assignment) {
                is AnalystAssignment.Research -> {
                    researchGroups.getOrPut(assignment.upgradeId) { mutableListOf() }
                        .add(effectiveWeight)
                }
                is AnalystAssignment.Consulting -> {
                    cashEarned += Money((baseEventPoints * effectiveWeight * CONSULTING_CASH_PER_POINT.cents).toLong())
                }
                null -> { /* idle — no contribution */ }
            }
        }

        var researchState = state.researchState
        var money = state.money

        if (cashEarned > Money.ZERO) {
            money += cashEarned
        }

        for ((upgradeId, weights) in researchGroups) {
            val effectiveCount = sqrt(weights.size.toFloat())
            val avgWeight = weights.average().toFloat()
            val topicPoints = baseEventPoints * effectiveCount * avgWeight
            val currentProgress = researchState.researchProgress[upgradeId] ?: 0f
            researchState = researchState.copy(
                researchProgress = researchState.researchProgress + (upgradeId to currentProgress + topicPoints),
                totalPointsEarned = researchState.totalPointsEarned + topicPoints,
            )
        }

        return state.copy(
            researchState = researchState,
            money = money,
        )
    }

    fun checkCompletions(state: GameState): GameState {
        var s = state
        var researchState = s.researchState
        val newlyCompleted = mutableListOf<String>()

        for ((upgradeId, progress) in researchState.researchProgress) {
            if (upgradeId in researchState.researchedUpgrades) continue
            val upgrade = ResearchUpgradeRegistry.allUpgrades[upgradeId] ?: continue
            if (progress >= upgrade.researchCost) {
                newlyCompleted.add(upgradeId)
            }
        }

        if (newlyCompleted.isEmpty()) return s

        val updatedCompleted = researchState.researchedUpgrades + newlyCompleted
        var updatedAssignments = researchState.analystAssignments

        // Idle analysts that were assigned to now-completed topics
        for (completedId in newlyCompleted) {
            updatedAssignments = updatedAssignments.filterValues { assignment ->
                !(assignment is AnalystAssignment.Research && assignment.upgradeId == completedId)
            }
        }

        researchState = researchState.copy(
            researchedUpgrades = updatedCompleted,
            analystAssignments = updatedAssignments,
        )
        s = s.copy(researchState = researchState)

        // Add newly accessible items to inventory, seeded with starter stock
        // (SEED_QTY on the shelf + SEED_QTY in the backroom) so a freshly unlocked
        // category is immediately sellable instead of starting empty.
        val currentDay = s.simAccumulators.lastKnownDayNumber
        for (completedId in newlyCompleted) {
            val newItems = itemMetadataCache.getAllItems().filter { (_, item) ->
                item.researchGate == completedId && !s.inventory.containsKey(
                    item.id.removePrefix("item_").toIntOrNull() ?: -1
                )
            }
            if (newItems.isNotEmpty()) {
                val updatedInventory = s.inventory.toMutableMap()
                for ((_, item) in newItems) {
                    val itemId = item.id.removePrefix("item_").toIntOrNull() ?: continue
                    val expirationDay = item.shelfLifeDays?.let { currentDay + it } ?: Int.MAX_VALUE
                    val seedBatch = listOf(
                        ItemBatch(receivedDay = currentDay, quantity = SEED_QTY, expirationDay = expirationDay)
                    )
                    updatedInventory[itemId] = InventoryState(
                        shelfBatches = seedBatch,
                        backroomBatches = seedBatch,
                    )
                }
                s = s.copy(inventory = updatedInventory)
            }
        }

        return s
    }

    fun assignAnalyst(state: GameState, entityId: Int, assignment: AnalystAssignment): GameState {
        val entity = state.hiredEntityRegistry.hiredEntities.find { it.id == entityId }
            ?: return state
        if (entity.entityDefinition != EntityDef.MARKET_ANALYST) return state

        if (assignment is AnalystAssignment.Research) {
            val upgradeId = assignment.upgradeId
            if (upgradeId in state.researchState.researchedUpgrades) return state
            if (!isResearchVisible(state, upgradeId)) return state
        }

        return state.copy(
            researchState = state.researchState.copy(
                analystAssignments = state.researchState.analystAssignments + (entityId to assignment),
            )
        )
    }

    fun removeAnalystAssignment(state: GameState, entityId: Int): GameState {
        return state.copy(
            researchState = state.researchState.copy(
                analystAssignments = state.researchState.analystAssignments - entityId,
            )
        )
    }

    fun isResearchVisible(state: GameState, upgradeId: String): Boolean {
        val upgrade = ResearchUpgradeRegistry.allUpgrades[upgradeId] ?: return false
        if (upgradeId in state.researchState.researchedUpgrades) return false

        // Check store size requirement
        val requiredSize = upgrade.requiredStoreSize
        if (requiredSize != null && state.currentStoreSize.ordinal < requiredSize.ordinal) return false

        // Check prerequisites
        if (upgrade.prerequisites.any { it !in state.researchState.researchedUpgrades }) return false

        // Check gate condition
        if (upgrade.gateCheck != null && !upgrade.gateCheck.invoke(state)) return false

        return true
    }

    fun isUpgradeResearched(state: GameState, upgradeId: String): Boolean =
        upgradeId in state.researchState.researchedUpgrades

    fun getAvailableResearch(state: GameState): List<ResearchableUpgrade> =
        ResearchUpgradeRegistry.allUpgrades.values.filter { isResearchVisible(state, it.id) }
}
