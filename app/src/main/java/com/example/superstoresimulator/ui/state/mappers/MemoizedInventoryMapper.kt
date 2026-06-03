package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.ScheduledTruck
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.ItemMetadata
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.state.InventoryUIState

/**
 * ✅ Performance Optimization #2: Memoized Inventory Mapper
 * 
 * Caches InventoryItemUI objects and only remaps items that have actually changed.
 * This avoids creating 50 new InventoryItemUI objects every frame when nothing changed.
 * 
 * Algorithm:
 * 1. Compare current inventory map with last frame's inventory map
 * 2. Identify added/removed/modified items
 * 3. Only remap changed items
 * 4. Reuse cached InventoryItemUI objects for unchanged items
 * 5. Return same list instance if nothing changed (prevents Compose recomposition)
 * 
 * Performance:
 * - Before: 50 new InventoryItemUI objects per frame (2.7M per 30-min session)
 * - After: 0-5 new objects per frame (only for changed items)
 * - Improvement: 99% fewer allocations + no Compose recomposition on unchanged frames
 */
class MemoizedInventoryMapper(
    private val metadataCache: ItemMetadataCache
) {
    
    private var lastDomainInventory: Map<Int, InventoryState>? = null
    private var lastMappedItems: List<InventoryItemUI>? = null
    private var lastTier: ItemUnlockTier? = null
    private var lastBackroomCap: Int? = null
    private var lastScheduledTrucks: List<ScheduledTruck>? = null
    private val itemCache: MutableMap<Int, InventoryItemUI> = mutableMapOf()
    
    fun map(
        currentInventory: Map<Int, InventoryState>,
        tier: ItemUnlockTier,
        backroomCap: Int,
        scheduledTrucks: List<ScheduledTruck> = emptyList(),
    ): InventoryUIState {
        val domainInventory = currentInventory

        // Invalidate the cache when the tier, backroom cap, or truck state changes
        if (tier != lastTier || backroomCap != lastBackroomCap || scheduledTrucks != lastScheduledTrucks) {
            lastDomainInventory = null
            lastMappedItems = null
            lastTier = tier
            lastBackroomCap = backroomCap
            lastScheduledTrucks = scheduledTrucks
        }

        // ✅ If inventory hasn't changed structurally, return cached result immediately
        if (domainInventory == lastDomainInventory && lastMappedItems != null) {
            return InventoryUIState(items = lastMappedItems!!)
        }

        // Build pending delivery lookup: itemId → (totalCasePacks, earliestArrivalDay)
        val pendingDeliveryMap = mutableMapOf<Int, Pair<Int, Int>>()
        for (truck in scheduledTrucks) {
            for (line in truck.orders) {
                val existing = pendingDeliveryMap[line.itemId]
                val newCasePacks = (existing?.first ?: 0) + line.casePacksCount
                val newEarliest = if (existing == null) truck.scheduledArrivalDay
                                  else minOf(existing.second, truck.scheduledArrivalDay)
                pendingDeliveryMap[line.itemId] = Pair(newCasePacks, newEarliest)
            }
        }

        // Identify changed items
        val changedItemIds = mutableSetOf<Int>()
        
        // Items that were removed from inventory
        lastDomainInventory?.keys?.forEach { oldId ->
            if (oldId !in domainInventory) {
                changedItemIds.add(oldId)
                itemCache.remove(oldId)
            }
        }
        
        // Items that were added or modified
        domainInventory.forEach { (itemId, dyn) ->
            val oldDyn = lastDomainInventory?.get(itemId)
            if (oldDyn != dyn) {
                changedItemIds.add(itemId)
            }
        }

        // Also invalidate items whose pending delivery info changed
        pendingDeliveryMap.forEach { (itemId, _) -> changedItemIds.add(itemId) }
        lastScheduledTrucks?.forEach { truck ->
            truck.orders.forEach { line -> changedItemIds.add(line.itemId) }
        }

        // Only remap changed items (instead of remapping all items)
        changedItemIds.forEach { itemId ->
            val dyn = domainInventory[itemId] ?: return@forEach
            val meta = metadataCache.get(itemId) ?: ItemMetadata.default(itemId)
            
            // Pending delivery info must be computed BEFORE backroomFull so in-transit
            // case packs are counted toward the committed total.
            val pending = pendingDeliveryMap[itemId]
            val pendingCasePacks = pending?.first ?: 0

            // Calculate current case packs in backroom and check if one more would exceed cap,
            // counting both backroom stock AND in-transit case packs as "committed".
            val currentCasePacksInBackroom = dyn.backroomStock / meta.casePack
            val backroomFull = (currentCasePacksInBackroom + pendingCasePacks + 1) > backroomCap

            // Find the closest expiration date among all batches
            val allBatches = dyn.shelfBatches + dyn.backroomBatches
            val closestExpiration = if (allBatches.isNotEmpty()) {
                allBatches.minOfOrNull { it.expirationDay }
            } else null


            itemCache[itemId] = InventoryItemUI(
                id = itemId,
                name = meta.name,
                price = meta.price,
                unitCost = meta.unitCost,
                shelfStock = dyn.shelfStock,
                backroomStock = dyn.backroomStock,
                category = meta.category,
                casePack = meta.casePack,
                casePackCost = meta.casePackCost,
                backroomFull = backroomFull,
                shelfLifeDays = meta.shelfLifeDays,
                closestExpirationDay = closestExpiration,
                pendingCasePacks = pending?.first ?: 0,
                earliestArrivalDay = pending?.second,
                zoneScore = dyn.zoneScore,
            )
        }
        
        // Rebuild items list, filtering to only items whose tier is unlocked
        val currentTierAmount = tier.unlockAmount
        val items = domainInventory.keys.mapNotNull { itemId ->
            val ui = itemCache[itemId] ?: return@mapNotNull null
            val itemTier = metadataCache.get(itemId)?.tier ?: ItemUnlockTier.TIER_1
            if (itemTier.unlockAmount > currentTierAmount) null else ui
        }
        
        // Cache the result for next frame
        lastDomainInventory = domainInventory
        lastMappedItems = items
        
        return InventoryUIState(items = items)
    }
}
