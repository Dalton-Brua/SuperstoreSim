package com.example.superstoresimulator.domain.vendor

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Singleton
class VendorManager @Inject constructor(private val cache: ItemMetadataCache) {

    fun getVendorForItem(itemId: Int): String? {
        val meta = cache.get(itemId) ?: return null
        return meta.vendorId
    }

    fun isVendorItemUnlocked(itemId: Int, vendorTier: Int): Boolean {
        val meta = cache.get(itemId) ?: return false
        return meta.vendorId != null && meta.vendorTier in 0..vendorTier
    }

    fun initializeStartingVendors(state: GameState): GameState {
        val vendors = mutableMapOf<String, VendorState>()
        for (def in VendorConfig.VENDORS) {
            vendors[def.vendorId] = VendorState(
                vendorId = def.vendorId,
                unlocked = true,
                lastRestockDay = state.currentTime.dayNumber,
            )
        }

        var newState = state.copy(
            vendorSystem = VendorSystemState(
                vendors = vendors,
                currentVendorTier = 0,
            ),
        )

        newState = stockVendorItems(newState, state.currentTime.dayNumber, isInitial = true)
        return newState
    }

    fun processVendorArrivals(state: GameState, currentDay: Int): GameState {
        var newState = state
        val updatedVendors = state.vendorSystem.vendors.toMutableMap()

        for ((vendorId, vendor) in state.vendorSystem.vendors) {
            if (!vendor.unlocked) continue
            val interval = VendorConfig.getRestockInterval(vendor.reputation)
            if (currentDay - vendor.lastRestockDay < interval) continue

            var newRep = vendor.reputation
            if (vendor.unitsDeliveredLastRestock > 0) {
                val sellThrough = vendor.unitsSoldSinceLastRestock.toFloat() / vendor.unitsDeliveredLastRestock
                val repGain = when {
                    sellThrough >= 0.9f -> 3
                    sellThrough >= 0.7f -> 2
                    sellThrough >= 0.5f -> 1
                    else -> 0
                }
                newRep = min(newRep + repGain, VendorConfig.MAX_REPUTATION)
            }

            updatedVendors[vendorId] = vendor.copy(
                reputation = newRep,
                lastRestockDay = currentDay,
                unitsSoldSinceLastRestock = 0,
                unitsDeliveredLastRestock = 0,
            )
        }

        newState = newState.copy(
            vendorSystem = newState.vendorSystem.copy(vendors = updatedVendors),
        )

        newState = stockVendorItems(newState, currentDay, isInitial = false)
        return newState
    }

    private fun stockVendorItems(state: GameState, currentDay: Int, isInitial: Boolean): GameState {
        val inventory = state.inventory.toMutableMap()
        val updatedVendors = state.vendorSystem.vendors.toMutableMap()
        val vendorTier = state.vendorSystem.currentVendorTier

        for ((vendorId, vendor) in state.vendorSystem.vendors) {
            if (!vendor.unlocked) continue
            if (!isInitial) {
                val interval = VendorConfig.getRestockInterval(vendor.reputation)
                if (currentDay - vendor.lastRestockDay != 0 && currentDay - vendor.lastRestockDay < interval) continue
            }

            val items = cache.getVendorItemsByTier(vendorId, vendorTier)
            var totalDelivered = 0
            val maxCasePacks = state.storeConfig.backroomCapPerItem

            for (item in items) {
                val quantity = item.casePack * maxCasePacks
                val expirationDay = if (item.shelfLifeDays != null) {
                    currentDay + item.shelfLifeDays
                } else Int.MAX_VALUE

                val batch = ItemBatch(
                    receivedDay = currentDay,
                    quantity = quantity,
                    expirationDay = expirationDay,
                )

                val existing = inventory[item.id] ?: InventoryState()
                inventory[item.id] = existing.copy(
                    shelfBatches = existing.shelfBatches + batch,
                )
                totalDelivered += quantity
            }

            updatedVendors[vendorId] = (updatedVendors[vendorId] ?: vendor).copy(
                unitsDeliveredLastRestock = totalDelivered,
            )
        }

        return state.copy(
            inventory = inventory,
            vendorSystem = state.vendorSystem.copy(vendors = updatedVendors),
        )
    }

    fun deductVendorCommission(
        state: GameState,
        itemId: Int,
        saleRevenue: Money,
        qtySold: Int,
    ): GameState {
        val meta = cache.get(itemId) ?: return state
        val vendorId = meta.vendorId ?: return state
        val vendor = state.vendorSystem.vendors[vendorId] ?: return state

        val commissionRate = VendorConfig.getCommissionRate(vendor.reputation)
        val commission = Money(saleRevenue.cents * commissionRate / 100)

        val updatedVendor = vendor.copy(
            unitsSoldSinceLastRestock = vendor.unitsSoldSinceLastRestock + qtySold,
        )

        val acc = state.currentDayMetrics
        return state.copy(
            money = state.money - commission,
            vendorSystem = state.vendorSystem.copy(
                vendors = state.vendorSystem.vendors + (vendorId to updatedVendor),
            ),
            currentDayMetrics = acc.copy(
                vendorCommissionPaid = acc.vendorCommissionPaid + commission,
                vendorItemsSold = acc.vendorItemsSold + qtySold,
                vendorRevenue = acc.vendorRevenue + saleRevenue,
            ),
        )
    }

    fun unlockNextVendorTier(state: GameState): GameState {
        val nextTier = state.vendorSystem.currentVendorTier + 1
        if (nextTier >= VendorConfig.VENDOR_TIER_UNLOCK_COSTS.size) return state

        val cost = VendorConfig.VENDOR_TIER_UNLOCK_COSTS[nextTier]
        if (state.money < cost) return state

        var newState = state.copy(
            money = state.money - cost,
            vendorSystem = state.vendorSystem.copy(currentVendorTier = nextTier),
        )

        val inventory = newState.inventory.toMutableMap()
        for (def in VendorConfig.VENDORS) {
            val items = cache.getVendorItemsByTier(def.vendorId, nextTier)
            for (item in items) {
                if (item.vendorTier != nextTier) continue
                if (inventory.containsKey(item.id)) continue
                inventory[item.id] = InventoryState()
            }
        }

        return newState.copy(inventory = inventory)
    }

    fun investInVendor(state: GameState, vendorId: String): GameState {
        val vendor = state.vendorSystem.vendors[vendorId] ?: return state
        if (!vendor.unlocked) return state
        if (vendor.reputation >= VendorConfig.MAX_REPUTATION) return state

        val cost = VendorConfig.getInvestmentCost(vendor.reputation)
        if (state.money < cost) return state

        val newRep = min(vendor.reputation + VendorConfig.INVESTMENT_REP_GAIN, VendorConfig.MAX_REPUTATION)
        return state.copy(
            money = state.money - cost,
            vendorSystem = state.vendorSystem.copy(
                vendors = state.vendorSystem.vendors + (vendorId to vendor.copy(reputation = newRep)),
            ),
        )
    }
}
