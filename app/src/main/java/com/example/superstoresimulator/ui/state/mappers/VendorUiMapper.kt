package com.example.superstoresimulator.ui.state.mappers

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.vendor.VendorConfig
import com.example.superstoresimulator.ui.state.VendorCardUI
import com.example.superstoresimulator.ui.state.VendorItemInfo
import com.example.superstoresimulator.ui.state.VendorUIState

fun buildVendorUiState(domain: GameState, metadataCache: ItemMetadataCache): VendorUIState {
    val system = domain.vendorSystem
    val cards = VendorConfig.VENDORS.map { def ->
        val vendor = system.vendors[def.vendorId]
        val rep = vendor?.reputation ?: 0
        val tier = VendorConfig.getCommissionTier(rep)
        val investCost = VendorConfig.getInvestmentCost(rep)
        val atMax = rep >= VendorConfig.MAX_REPUTATION

        val vendorItems = metadataCache.getVendorItems(def.vendorId)
            .sortedWith(compareBy({ it.vendorTier }, { it.name }))
            .map { meta ->
                VendorItemInfo(
                    name = meta.name,
                    price = meta.price,
                    vendorTier = meta.vendorTier,
                )
            }

        VendorCardUI(
            vendorId = def.vendorId,
            vendorName = def.vendorName,
            reputation = rep,
            maxReputation = VendorConfig.MAX_REPUTATION,
            tierName = tier.tierName,
            commissionPercent = tier.commissionPercent,
            restockIntervalDays = tier.restockIntervalDays,
            investCost = investCost,
            canInvest = !atMax && domain.money >= investCost,
            atMaxRep = atMax,
            items = vendorItems,
        )
    }

    val nextTierIndex = system.currentVendorTier + 1
    val maxTier = nextTierIndex >= VendorConfig.VENDOR_TIER_UNLOCK_COSTS.size
    val nextCost = if (!maxTier) VendorConfig.VENDOR_TIER_UNLOCK_COSTS[nextTierIndex] else null

    return VendorUIState(
        vendors = cards,
        currentVendorTier = system.currentVendorTier,
        nextTierCost = nextCost,
        canUnlockNextTier = nextCost != null && domain.money >= nextCost,
        maxTierReached = maxTier,
    )
}
