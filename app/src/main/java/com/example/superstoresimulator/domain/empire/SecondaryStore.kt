package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.GameTime
import kotlinx.serialization.Serializable

/**
 * A lightweight, simulated store in empire mode. Not micro-managed — driven by its
 * size, upgrades, direction, region, and global state. Outputs are closed-form sim
 * results, not full loop-1 machinery (that only spins up during a hands-on visit).
 */
@Serializable
data class SecondaryStore(
    val storeId: Int,
    val storeName: String,
    val regionId: Int,
    val storeSize: StoreSize,
    val upgrades: Set<StoreUpgrade> = emptySet(),
    val direction: StoreDirection = StoreDirection.BALANCED,
    val managedByRegionalManager: Boolean = false,
    val openedAtTime: GameTime = GameTime(0),
    /** Multiplier on sim profit from last hands-on visit (1.0 = sim baseline). */
    val operatingPerformance: Float = 1.0f,
    /** dayIndex of last hands-on session, for decay (null = never operated). */
    val lastOperatedDay: Int? = null,
    /** Preserved loop-1 detail; null until first hands-on visit. */
    val operatingState: StoreOperatingState? = null,
    val currentDayMetrics: SimStoreMetrics = SimStoreMetrics(),
    val completedDayMetrics: List<SimStoreMetrics> = emptyList(),
)
