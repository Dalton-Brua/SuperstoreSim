package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.ScheduledTruck
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.ItemMetadata
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.pricing.PricingState
import com.example.superstoresimulator.domain.pricing.ResolvedPrice
import com.example.superstoresimulator.domain.vendor.VendorConfig
import com.example.superstoresimulator.ui.state.InventoryItemUI
import com.example.superstoresimulator.ui.state.InventoryUIState

/**
 * Caches InventoryItemUI objects and only remaps items that have actually changed.
 * Avoids creating new InventoryItemUI objects every frame when nothing changed.
 */
class MemoizedInventoryMapper(
    private val metadataCache: ItemMetadataCache
) {
    private val vendorNameLookup: Map<String, String> =
        VendorConfig.VENDORS.associate { it.vendorId to it.vendorName }

    private var lastDomainInventory: Map<Int, InventoryState>? = null
    private var lastMappedItems: List<InventoryItemUI>? = null
    private var lastResearchedUpgrades: Set<String>? = null
    private var lastBackroomCap: Int? = null
    private var lastScheduledTrucks: List<ScheduledTruck>? = null
    private var lastPricingState: PricingState? = null
    private var lastVendorTier: Int? = null
    private val itemCache: MutableMap<Int, InventoryItemUI> = mutableMapOf()

    fun map(
        currentInventory: Map<Int, InventoryState>,
        researchedUpgrades: Set<String>,
        backroomCap: Int,
        scheduledTrucks: List<ScheduledTruck> = emptyList(),
        priceResolver: ((Int) -> ResolvedPrice)? = null,
        gameState: GameState? = null,
    ): InventoryUIState {
        val domainInventory = currentInventory
        val currentPricing = gameState?.pricingState
        val currentVendorTier = gameState?.vendorSystem?.currentVendorTier

        if (researchedUpgrades != lastResearchedUpgrades || backroomCap != lastBackroomCap ||
            scheduledTrucks != lastScheduledTrucks || currentPricing != lastPricingState ||
            currentVendorTier != lastVendorTier) {
            lastDomainInventory = null
            lastMappedItems = null
            lastResearchedUpgrades = researchedUpgrades
            lastBackroomCap = backroomCap
            lastScheduledTrucks = scheduledTrucks
            lastPricingState = currentPricing
            lastVendorTier = currentVendorTier
        }

        if (domainInventory == lastDomainInventory && lastMappedItems != null) {
            return InventoryUIState(items = lastMappedItems!!)
        }

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

        val changedItemIds = mutableSetOf<Int>()
        lastDomainInventory?.keys?.forEach { oldId ->
            if (oldId !in domainInventory) {
                changedItemIds.add(oldId)
                itemCache.remove(oldId)
            }
        }
        domainInventory.forEach { (itemId, dyn) ->
            val oldDyn = lastDomainInventory?.get(itemId)
            if (oldDyn != dyn) changedItemIds.add(itemId)
        }
        pendingDeliveryMap.forEach { (itemId, _) -> changedItemIds.add(itemId) }
        lastScheduledTrucks?.forEach { truck ->
            truck.orders.forEach { line -> changedItemIds.add(line.itemId) }
        }

        changedItemIds.forEach { itemId ->
            val dyn = domainInventory[itemId] ?: return@forEach
            val meta = metadataCache.get(itemId) ?: ItemMetadata.default(itemId)
            val pending = pendingDeliveryMap[itemId]
            val pendingCasePacks = pending?.first ?: 0
            val currentCasePacksInBackroom = dyn.backroomStock / meta.casePack
            val backroomFull = (currentCasePacksInBackroom + pendingCasePacks + 1) > backroomCap
            val allBatches = dyn.shelfBatches + dyn.backroomBatches
            val closestExpiration = allBatches.minOfOrNull { it.expirationDay }
            val resolved = priceResolver?.invoke(itemId)

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
                effectivePrice = resolved?.effectivePrice ?: meta.price,
                priceModifierPercent = resolved?.modifierPercent ?: 0,
                hasActiveMarkdown = currentPricing?.activeMarkdowns?.containsKey(itemId) == true,
                soldByWeight = meta.soldByWeight,
                description = metadataCache.getItem(itemId)?.description ?: "",
                tierLabel = meta.researchGate ?: "",
                vendorName = meta.vendorId?.let { vendorNameLookup[it] },
            )
        }

        val vendorTier = currentVendorTier ?: 0
        val items = domainInventory.keys.mapNotNull { itemId ->
            val ui = itemCache[itemId] ?: return@mapNotNull null
            val meta = metadataCache.get(itemId)
            if (!metadataCache.isItemAccessible(itemId, researchedUpgrades)) return@mapNotNull null
            if (meta?.isVendorItem == true && meta.vendorTier > vendorTier) return@mapNotNull null
            ui
        }

        lastDomainInventory = domainInventory
        lastMappedItems = items

        return InventoryUIState(items = items)
    }
}
