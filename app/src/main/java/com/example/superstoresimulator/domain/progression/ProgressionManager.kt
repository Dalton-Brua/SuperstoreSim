package com.example.superstoresimulator.domain.progression

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressionManager @Inject constructor(
    private val itemMetadataCache: ItemMetadataCache,
) {

    fun unlockNextTier(state: GameState): GameState {
        val nextTier = ItemUnlockTier.nextTier(state.currentTier) ?: return state
        if (state.totalRevenue.cents < nextTier.unlockAmount) return state
        if (state.money < nextTier.unlockCost) return state

        val previousTier = state.currentTier
        var s = state.copy(
            currentTier = nextTier,
            money = state.money - nextTier.unlockCost,
        )

        val newItems = itemMetadataCache.getAllItems().filter { (itemId, _) ->
            val meta = itemMetadataCache.get(itemId) ?: return@filter false
            meta.tier.unlockAmount > previousTier.unlockAmount &&
                meta.tier.unlockAmount <= nextTier.unlockAmount &&
                itemId !in s.inventory
        }
        if (newItems.isNotEmpty()) {
            val updatedInventory = s.inventory.toMutableMap()
            for ((itemId, _) in newItems) {
                updatedInventory[itemId] = InventoryState()
            }
            s = s.copy(inventory = updatedInventory)
        }

        return s
    }
}

