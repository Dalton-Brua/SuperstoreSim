package com.example.superstoresimulator.domain.inventory

import kotlinx.serialization.Serializable

@Serializable
data class InventoryState(
    val shelfBatches: List<ItemBatch> = emptyList(),
    val backroomBatches: List<ItemBatch> = emptyList(),
    val zoneScore: Float = 1.0f,
) {
    val shelfStock: Int get() = shelfBatches.sumOf { it.quantity }
    val backroomStock: Int get() = backroomBatches.sumOf { it.quantity }
    
    fun oldestShelfBatch(): ItemBatch? = shelfBatches.minByOrNull { it.receivedDay }
    fun oldestBackroomBatch(): ItemBatch? = backroomBatches.minByOrNull { it.receivedDay }
    
    /**
     * Merges batches with the same expirationDay to reduce memory overhead.
     * Called automatically when adding new batches.
     */
    fun mergeBatches(batches: List<ItemBatch>): List<ItemBatch> {
        return batches
            .groupBy { it.expirationDay }
            .map { (expirationDay, batchGroup) ->
                ItemBatch(
                    receivedDay = batchGroup.minOf { it.receivedDay },
                    quantity = batchGroup.sumOf { it.quantity },
                    expirationDay = expirationDay
                )
            }
            .sortedBy { it.receivedDay }
    }
}

@Serializable
data class ItemBatch(
    val receivedDay: Int,       // Day number when ordered
    val quantity: Int,
    val expirationDay: Int,     // receivedDay + item.shelfLifeDays (Int.MAX_VALUE for non-perishables)
)