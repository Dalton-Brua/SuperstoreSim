package com.example.superstoresimulator.domain.items

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.superstoresimulator.domain.Money

@Entity(
    tableName = "items",
    indices = [
        Index("category"),  // ✅ Fast category filtering
        Index("name"),      // ✅ Fast name searches
    ]
)
data class Item(
    @PrimaryKey val id: String,
    val name: String,
    @Embedded(prefix = "price_")
    val price: MoneyData,
    val description: String,
    @Embedded(prefix = "unitCost_")
    val unitCost: MoneyData,
    val category: ItemCategory = ItemCategory.GROCERY,
    val casePack: Int = 1,
    /** Relative likelihood that a customer picks this item. Higher = chosen more often. */
    val purchaseWeight: Float = 1.0f,
    /** Minimum tier required to see/buy this item in the store. Stored as the enum name string. */
    val tier: String = "TIER_1",
    /** Number of days until item expires. Null = non-perishable (never expires). */
    val shelfLifeDays: Int? = null,
    val soldByWeight: Boolean = false,
    val vendorId: String? = null,
    val vendorTier: Int = -1,
) {
    /**
     * Get price as Money object
     */
    fun getPriceAsMoney(): Money = price.toMoney()
    
    /**
     * Get unit cost as Money object
     */
    fun getUnitCostAsMoney(): Money = unitCost.toMoney()
    
    /**
     * Get total cost for ordering one case pack
     */
    fun getCasePackCostAsMoney(): Money = unitCost.toMoney() * casePack
}
