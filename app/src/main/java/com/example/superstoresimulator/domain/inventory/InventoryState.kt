package com.example.superstoresimulator.domain.inventory

/**
 * Batch-based inventory tracking for FIFO expiration support.
 * Each delivery creates a new ItemBatch with receivedDay and expirationDay.
 * Oldest batches are consumed first (FIFO). Batches with the same expirationDay
 * are automatically merged to reduce memory overhead.
 */
data class InventoryState(
    val shelfBatches: List<ItemBatch> = emptyList(),
    val backroomBatches: List<ItemBatch> = emptyList(),
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

/**
 * Represents a single batch of items received on a specific day.
 * Batches with the same expirationDay are automatically merged.
 */
data class ItemBatch(
    val receivedDay: Int,       // Day number when ordered
    val quantity: Int,
    val expirationDay: Int,     // receivedDay + item.shelfLifeDays (Int.MAX_VALUE for non-perishables)
)