package com.example.superstoresimulator.domain.items

import com.example.superstoresimulator.domain.Money

/**
 * Lightweight metadata for items, cached to avoid repeated database lookups.
 * Used by inventory UI mapping to avoid O(n) database access per frame.
 */
data class ItemMetadata(
    val id: Int,
    val name: String,
    val price: Money,
    val unitCost: Money,
    val category: ItemCategory,
    val casePack: Int,
    val casePackCost: Money,
    /** Relative likelihood that a customer picks this item. Higher = chosen more often. */
    val purchaseWeight: Float = 1.0f,
    /** Minimum tier required to see/buy this item in the store. */
    val tier: ItemUnlockTier = ItemUnlockTier.TIER_1,
    /** Number of days until item expires. Null = non-perishable (never expires). */
    val shelfLifeDays: Int? = null,
    val soldByWeight: Boolean = false,
) {
    /** Returns true if this item is perishable (has an expiration date). */
    val isPerishable: Boolean get() = shelfLifeDays != null
    
    companion object {
        fun default(itemId: Int): ItemMetadata {
            return ItemMetadata(
                id = itemId,
                name = "Item $itemId",
                price = Money.fromDollars(9.99),
                unitCost = Money.fromDollars(5.00),
                category = ItemCategory.GROCERY,
                casePack = 1,
                casePackCost = Money.fromDollars(5.00),
                purchaseWeight = 1.0f,
                tier = ItemUnlockTier.TIER_1,
                shelfLifeDays = null,
            )
        }
    }
}

