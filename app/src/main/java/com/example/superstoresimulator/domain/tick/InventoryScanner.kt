package com.example.superstoresimulator.domain.tick

import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.ItemMetadataCache

data class InventoryScanResult(
    val hasActionableBackroom: Boolean,
    val hasUnzonedItems: Boolean,
    val hasFreshBackroomStock: Boolean,
)

fun scanInventory(
    inventory: Map<Int, InventoryState>,
    cache: ItemMetadataCache,
): InventoryScanResult {
    var hasActionableBackroom = false
    var hasUnzonedItems = false
    var hasFreshBackroomStock = false
    for ((itemId, inv) in inventory) {
        if (!hasActionableBackroom && inv.backroomStock > 0 && cache.get(itemId)?.isPerishable != true) {
            hasActionableBackroom = true
        }
        if (!hasUnzonedItems && inv.shelfStock > 0 && inv.zoneScore < 1.0f) {
            hasUnzonedItems = true
        }
        if (!hasFreshBackroomStock && inv.backroomStock > 0 && cache.get(itemId)?.isPerishable == true) {
            hasFreshBackroomStock = true
        }
        if (hasActionableBackroom && hasUnzonedItems && hasFreshBackroomStock) break
    }
    return InventoryScanResult(hasActionableBackroom, hasUnzonedItems, hasFreshBackroomStock)
}
