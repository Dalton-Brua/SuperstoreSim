package com.example.superstoresimulator.domain.vendor

import com.example.superstoresimulator.domain.Money
import kotlinx.serialization.Serializable

@Serializable
data class VendorState(
    val vendorId: String,
    val reputation: Int = 0,
    val lastRestockDay: Int = 0,
    val unlocked: Boolean = false,
    val unitsDeliveredLastRestock: Int = 0,
    val unitsSoldSinceLastRestock: Int = 0,
)

@Serializable
data class VendorSystemState(
    val vendors: Map<String, VendorState> = emptyMap(),
    val currentVendorTier: Int = 0,
)
