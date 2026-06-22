package com.example.superstoresimulator.domain.empire

import kotlinx.serialization.Serializable

/**
 * Per-store purchasable upgrade flags that tune the sim. Store SIZE is NOT here —
 * it lives on [SecondaryStore.storeSize] and is bumped via a dedicated expand purchase.
 */
@Serializable
enum class StoreUpgrade(val displayName: String, val description: String) {
    EXTRA_REGISTERS("Extra Registers", "Higher traffic ceiling (+throughput)"),
    LOGISTICS("Logistics", "Lower operating cost"),
    MARKETING("Marketing", "More customer traffic (and more region weight)"),
}
