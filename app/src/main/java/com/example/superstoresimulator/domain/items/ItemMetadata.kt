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
    val purchaseWeight: Float = 1.0f,
    val researchGate: String? = null,
    val affinityGroups: List<String> = emptyList(),
    val substitutionGroup: String? = null,
    val shelfLifeDays: Int? = null,
    val soldByWeight: Boolean = false,
    val vendorId: String? = null,
    val vendorTier: Int = -1,
) {
    val isPerishable: Boolean get() = shelfLifeDays != null
    val isVendorItem: Boolean get() = vendorId != null

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
                researchGate = null,
                shelfLifeDays = null,
            )
        }
    }
}
