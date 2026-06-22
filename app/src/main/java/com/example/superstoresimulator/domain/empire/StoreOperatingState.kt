package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.Entities.HiredEntityRegistry
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.StaffShift
import com.example.superstoresimulator.domain.TruckConfig
import com.example.superstoresimulator.domain.inventory.InventoryState
import kotlinx.serialization.Serializable

/**
 * Preserved loop-1 detail for a secondary store, populated only after the player has
 * operated it by hand. Null for stores never visited (reconstructed on entry from
 * size/upgrades/direction). This is the heavier blob most stores never carry.
 */
@Serializable
data class StoreOperatingState(
    val inventory: Map<Int, InventoryState> = emptyMap(),
    val registers: List<RegisterState> = listOf(RegisterState(registerId = 0)),
    val hiredEntityRegistry: HiredEntityRegistry = HiredEntityRegistry(),
    val staffSchedules: List<StaffShift> = emptyList(),
    val truckConfig: TruckConfig = TruckConfig(),
)
