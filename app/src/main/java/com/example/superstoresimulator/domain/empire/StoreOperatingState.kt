package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.metrics.DailyMetrics
import com.example.superstoresimulator.domain.research.ResearchState
import kotlinx.serialization.Serializable

/**
 * Preserved loop-1 detail for a secondary store, populated only after the player has
 * operated it by hand. Null for stores never visited (reconstructed on entry from
 * size/upgrades/direction). This is the heavier blob most stores never carry.
 *
 * [researchState] is per-store: while operating a store its research replaces the global
 * one (loaded on drop-in, snapshotted on drop-out). The sim itself ignores research, so
 * outside a hands-on visit this value is dormant.
 */
@Serializable
data class StoreOperatingState(
    val inventory: Map<Int, InventoryState> = emptyMap(),
    val registers: List<RegisterState> = listOf(RegisterState(registerId = 0)),
    val hiredEntityRegistry: HiredEntityRegistry = HiredEntityRegistry(),
    val staffSchedules: List<StaffShift> = emptyList(),
    val truckConfig: TruckConfig = TruckConfig(),
    val researchState: ResearchState = ResearchState(),
    /** Per-store loop-1 daily metrics, kept isolated so performance scoring can't read another store's days. */
    val currentDayMetrics: DailyMetrics = DailyMetrics(),
    val completedDayMetrics: List<DailyMetrics> = emptyList(),
)
