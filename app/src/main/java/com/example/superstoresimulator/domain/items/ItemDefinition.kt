package com.example.superstoresimulator.domain.items

import com.example.superstoresimulator.domain.Money

data class ItemDefinition(
    val id: Int,
    val name: String,
    val basePrice: Money,
    val unitCost: Money,
    val category: ItemCategory,
    val tier: ItemUnlockTier = ItemUnlockTier.TIER_1,
    val casePack: Int = 1,  // Number of items per case/box
)