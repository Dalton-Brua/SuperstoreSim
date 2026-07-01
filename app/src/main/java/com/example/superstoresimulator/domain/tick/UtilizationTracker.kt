package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.staff.StaffManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UtilizationTracker @Inject constructor(
    private val staffManager: StaffManager,
    private val inventoryManager: InventoryManager,
    private val itemMetadataCache: ItemMetadataCache,
) {
    fun sample(state: GameState, currentHour: Int, providedScan: InventoryScanResult? = null) {
        if (state.hiredEntityRegistry.hiredEntities.isEmpty()) return
        val scan = providedScan ?: scanInventory(state.inventory, itemMetadataCache)
        val hasFreshWork = scan.hasFreshBackroomStock ||
            (state.freshAutoOrderConfig.enabled && state.inventory.any { (itemId, _) ->
                inventoryManager.shouldAutoOrderFreshItem(state, itemId, state.freshAutoOrderConfig)
            })
        staffManager.updateUtilization(state, currentHour, scan.hasActionableBackroom, scan.hasUnzonedItems, hasFreshWork, state.currentTime.dayOfWeek)
    }
}
