package com.example.superstoresimulator.domain.expiration

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.ExpiredItemEvent

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
 * and accumulate in DailyMetricsAccumulator. The full accounting appears
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
    fun processExpiration(state: GameState): GameState {
        val currentDay = state.currentTime.dayNumber
        var totalExpired = 0
        var totalWasteCost = Money.ZERO
        val expirationEvents = mutableListOf<ExpiredItemEvent>()
        
        // Process each item in inventory
        val updatedInventory = state.inventory.mapValues { (itemId, inv) ->
            val metadata = itemMetadataCache.get(itemId)
            if (metadata == null || !metadata.isPerishable) {
                return@mapValues inv  // Skip non-perishables
            }
            
            // Remove expired batches from shelf
            val (validShelfBatches, expiredShelfBatches) = inv.shelfBatches.partition { batch ->
                batch.expirationDay > currentDay
            }
            
            // Remove expired batches from backroom
            val (validBackroomBatches, expiredBackroomBatches) = inv.backroomBatches.partition { batch ->
                batch.expirationDay > currentDay
            }
            
            // Track expiration events
            val totalExpiredForItem = expiredShelfBatches.sumOf { it.quantity } + 
                                     expiredBackroomBatches.sumOf { it.quantity }
            
            if (totalExpiredForItem > 0) {
                val wasteCost = metadata.unitCost * totalExpiredForItem
                totalExpired += totalExpiredForItem
                totalWasteCost += wasteCost
                
                expirationEvents.add(
                    ExpiredItemEvent(
                        itemId = itemId,
                        itemName = metadata.name,
                        quantity = totalExpiredForItem,
                        valueLost = wasteCost
                    )
                )
            }
            
            // Return updated inventory with expired batches removed
            inv.copy(
                shelfBatches = validShelfBatches,
                backroomBatches = validBackroomBatches
            )
        }
        
        // Update game state with new inventory and metrics
        return if (totalExpired > 0) {
            state.copy(
                inventory = updatedInventory,
                currentDayMetrics = state.currentDayMetrics.copy(
                    itemsExpired = state.currentDayMetrics.itemsExpired + totalExpired,
                    expiredWasteCost = state.currentDayMetrics.expiredWasteCost + totalWasteCost,
                    expiredItemEvents = state.currentDayMetrics.expiredItemEvents + expirationEvents
                )
            )
        } else {
            // No expiration, return state with updated inventory (in case batches were reorganized)
            state.copy(inventory = updatedInventory)
        }
    }
}

