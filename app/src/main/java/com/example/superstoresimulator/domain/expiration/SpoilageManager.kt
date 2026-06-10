package com.example.superstoresimulator.domain.expiration

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.ExpiredItemEvent

data class SpoilageResult(
    val state: GameState,
    val expiredItemIds: Set<Int>,
)

/**
 * Manages item expiration logic.
 * 
 * Core responsibilities:
 * - Check for expired batches (expirationDay <= currentDay)
 * - Remove expired batches from inventory
 * - Track expiration events in daily metrics
 * - Calculate waste costs (at unit cost, not sale price)
 * 
 * Called once per tick by GameEngine. Expired items are removed immediately
 * and accumulate in DailyMetrics. The full accounting appears
 * in the end-of-day report's "Shrinkage" section.
 * 
 * Architecture: Pure function — takes GameState, returns new GameState.
 * No side effects, no mutable state. All expiration data flows through
 * the daily metrics system.
 * 
 * NOTE: Free waste disposal is the only method implemented. Future enhancements
 * can add paid disposal ($50/day) and composting ($5k one-time, +$10/day revenue).
 * See EXTREME_FEATURES_PROPOSAL.md for complete waste disposal system design.
 */
class SpoilageManager(
    private val itemMetadataCache: ItemMetadataCache
) {
    /**
     * Check all inventory for expired batches and remove them.
     * Updates daily metrics with expiration events.
     * 
     * @param state Current game state
     * @return New game state with expired batches removed and metrics updated
     */
    fun processExpiration(state: GameState): SpoilageResult {
        val currentDay = state.currentTime.dayNumber

        val hasAnyExpired = state.inventory.any { (itemId, inv) ->
            val meta = itemMetadataCache.get(itemId) ?: return@any false
            meta.isPerishable && (inv.shelfBatches + inv.backroomBatches).any { it.expirationDay <= currentDay }
        }
        if (!hasAnyExpired) return SpoilageResult(state, emptySet())

        var totalExpired = 0
        var totalWasteCost = Money.ZERO
        val expirationEvents = mutableListOf<ExpiredItemEvent>()
        val expiredItemIds = mutableSetOf<Int>()

        val updatedInventory = state.inventory.mapValues { (itemId, inv) ->
            val metadata = itemMetadataCache.get(itemId)
            if (metadata == null || !metadata.isPerishable) {
                return@mapValues inv
            }

            val (validShelfBatches, expiredShelfBatches) = inv.shelfBatches.partition { batch ->
                batch.expirationDay > currentDay
            }

            val (validBackroomBatches, expiredBackroomBatches) = inv.backroomBatches.partition { batch ->
                batch.expirationDay > currentDay
            }

            val totalExpiredForItem = expiredShelfBatches.sumOf { it.quantity } +
                                     expiredBackroomBatches.sumOf { it.quantity }

            if (totalExpiredForItem > 0) {
                val wasteCost = metadata.unitCost * totalExpiredForItem
                totalExpired += totalExpiredForItem
                totalWasteCost += wasteCost
                expiredItemIds.add(itemId)

                expirationEvents.add(
                    ExpiredItemEvent(
                        itemId = itemId,
                        itemName = metadata.name,
                        quantity = totalExpiredForItem,
                        valueLost = wasteCost
                    )
                )
            }

            inv.copy(
                shelfBatches = validShelfBatches,
                backroomBatches = validBackroomBatches
            )
        }

        val newState = state.copy(
            inventory = updatedInventory,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsExpired = state.currentDayMetrics.itemsExpired + totalExpired,
                expiredWasteCost = state.currentDayMetrics.expiredWasteCost + totalWasteCost,
                expiredItemEvents = state.currentDayMetrics.expiredItemEvents + expirationEvents
            )
        )
        return SpoilageResult(newState, expiredItemIds)
    }
}

