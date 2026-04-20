package com.example.superstoresimulator.domain.Transactions

import android.annotation.SuppressLint
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RefundLine
import com.example.superstoresimulator.domain.RefundRequest
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import java.time.Instant
import kotlin.random.Random

class TransactionEngine(
    private val salesTaxRate: Double = 0.0825,
    private val refundChance: Double = 0.00,
    private val random: Random = Random.Default,
    private val cache: ItemMetadataCache? = null,
) {

    fun startNewTransaction(state: GameState): GameState {
        val currentTierAmount = state.currentTier.unlockAmount
        val availableItemIds = state.inventory.keys.filter { itemId ->
            val itemTier = cache?.get(itemId)?.tier ?: ItemUnlockTier.TIER_1
            itemTier.unlockAmount <= currentTierAmount
        }
        if (availableItemIds.isEmpty()) {
            throw IllegalStateException("No items in inventory to create transactions")
        }

        val numLines = (1..availableItemIds.size.coerceAtMost(5)).random(random)
        val lines = mutableListOf<TransactionLine>()

        val chosen = weightedSample(availableItemIds, numLines)

        repeat(numLines) { index ->
            val itemId = chosen[index]

            val qty = (1..3).random(random)
            val unitPrice = cache?.get(itemId)?.price ?: Money.Companion.fromDollars(9.99)

            lines += TransactionLine(
                itemId = itemId,
                quantity = qty,
                rungQty = 0,
                unitPrice = unitPrice,
                lineTotal = unitPrice * qty
            )
        }

        val subtotal = lines.fold(Money(0)) { acc, line -> acc + line.lineTotal }
        val tax = Money.Companion.fromDollars(subtotal.toDouble() * salesTaxRate)
        val totalEarned = subtotal + tax

        return state.copy(
            currentTransaction = Transaction(
                id = state.currentTransaction.id + 1,
                lines = lines,
                subtotal = subtotal,
                tax = tax,
                totalEarned = totalEarned
            ),
            transactionActive = true
        )
    }

    /**
     * Generate a transaction for an autonomously arriving customer (Phase 2 / TrafficManager).
     *
     * Unlike [startNewTransaction], this method:
     * - Accepts a pre-determined [itemCount] from the customer's basket size
     * - Is a **no-op** when a transaction is already active — the arriving customer
     *   is held off until the current one completes
     * - Returns [state] unchanged if inventory is empty
     */
    fun generateRandomTransaction(state: GameState, itemCount: Int): GameState {
        // Don't interrupt an active transaction
        if (state.transactionActive) return state

        val currentTierAmount = state.currentTier.unlockAmount
        val availableItemIds = state.inventory.keys.filter { itemId ->
            val itemTier = cache?.get(itemId)?.tier ?: ItemUnlockTier.TIER_1
            itemTier.unlockAmount <= currentTierAmount
        }
        if (availableItemIds.isEmpty()) return state

        val numLines = itemCount.coerceIn(1, availableItemIds.size.coerceAtMost(7))
        val chosen = weightedSample(availableItemIds, numLines)
        val lines = mutableListOf<TransactionLine>()

        for (itemId in chosen) {
            val qty = (1..3).random(random)
            val unitPrice = cache?.get(itemId)?.price ?: Money.Companion.fromDollars(9.99)
            lines += TransactionLine(
                itemId = itemId,
                quantity = qty,
                rungQty = 0,
                unitPrice = unitPrice,
                lineTotal = unitPrice * qty
            )
        }

        val subtotal = lines.fold(Money(0)) { acc, line -> acc + line.lineTotal }
        val tax = Money.Companion.fromDollars(subtotal.toDouble() * salesTaxRate)
        val totalEarned = subtotal + tax

        return state.copy(
            currentTransaction = Transaction(
                id = state.currentTransaction.id + 1,
                lines = lines,
                subtotal = subtotal,
                tax = tax,
                totalEarned = totalEarned
            ),
            transactionActive = true
        )
    }

    /**
     * Ring up exactly one item (one unit on one line).
     */
    fun ringUpSingleItem(state: GameState, itemId: Int): GameState {
        val prev = state

        val lines = prev.currentTransaction.lines.toMutableList()
        if (lines.isEmpty()) {
            return startNewTransaction(prev)
        }

        // Find the specific line for this item
        val lineIndex = lines.indexOfFirst { it.itemId == itemId }
        if (lineIndex == -1) return prev

        val line = lines[lineIndex]

        // Skip lines already fully rung or already marked lost to OOS
        if (line.rungQty >= line.quantity || line.lostToOutOfStock) return prev

        // If shelf stock is zero, mark the remaining quantity as lost (out-of-stock)
        // rather than blocking the transaction — the cashier moves on to the next item.
        if ((state.inventory[line.itemId]?.shelfStock ?: 0) == 0) {
            lines[lineIndex] = line.copy(lostToOutOfStock = true)
            return if (isTransactionComplete(lines)) {
                completeTransaction(prev, lines, prev.inventory)
            } else {
                updatePartialTransaction(prev, lines, prev.inventory)
            }
        }

        val updatedInventory = consumeShelfStock(prev.inventory, itemId)

        // Increment rungQty for this specific line
        val updatedLines = incrementRungQty(lines, lineIndex)

        // Finish or continue the transaction
        return if (isTransactionComplete(updatedLines)) {
            completeTransaction(prev, updatedLines, updatedInventory)
        } else {
            updatePartialTransaction(prev, updatedLines, updatedInventory)
        }
    }

    private fun consumeShelfStock(
        inventory: Map<Int, InventoryState>,
        itemId: Int
    ): Map<Int, InventoryState> {
        val inv = inventory[itemId] ?: return inventory
        
        // Consume from oldest shelf batch (FIFO)
        val oldestBatch = inv.oldestShelfBatch() ?: return inventory
        
        val updatedShelfBatches = inv.shelfBatches.mapNotNull { batch ->
            if (batch.receivedDay == oldestBatch.receivedDay && batch.expirationDay == oldestBatch.expirationDay) {
                if (batch.quantity > 1) batch.copy(quantity = batch.quantity - 1) else null
            } else {
                batch
            }
        }
        
        val updated = inv.copy(shelfBatches = updatedShelfBatches)
        return inventory + (itemId to updated)
    }


    private fun incrementRungQty(
        lines: MutableList<TransactionLine>,
        lineIndex: Int
    ): List<TransactionLine> {
        val line = lines[lineIndex]
        lines[lineIndex] = line.copy(rungQty = line.rungQty + 1)
        return lines
    }


    private fun isTransactionComplete(lines: List<TransactionLine>): Boolean {
        return lines.all { it.rungQty >= it.quantity || it.lostToOutOfStock }
    }

    private fun completeTransaction(
        prev: GameState,
        lines: List<TransactionLine>,
        inventory: Map<Int, InventoryState>
    ): GameState {
        val historyEntry = buildTransaction(
            id = prev.currentTransaction.id,
            transactionLines = lines
        )

        var newState = prev.copy(
            totalTransactionsCompleted = prev.totalTransactionsCompleted + 1,
            money = prev.money + historyEntry.totalEarned,
            totalRevenue = prev.totalRevenue + historyEntry.totalEarned,
            currentTransaction = prev.currentTransaction.copy(lines = lines),
            inventory = inventory,
            salesHistory = prev.salesHistory + historyEntry,
            totalTaxCollected = prev.totalTaxCollected + historyEntry.tax,
            transactionActive = false
        )

        newState = maybeGenerateRefund(newState, lines, inventory)
        return newState
    }


    private fun updatePartialTransaction(
        prev: GameState,
        lines: List<TransactionLine>,
        inventory: Map<Int, InventoryState>
    ): GameState {
        return prev.copy(
            currentTransaction = prev.currentTransaction.copy(lines = lines),
            inventory = inventory
        )
    }

    /**
     * Select [count] distinct item IDs from [pool] using weighted random sampling
     * without replacement. An item's [ItemMetadata.purchaseWeight] determines how
     * likely it is to be chosen relative to the rest of the pool.
     *
     * Falls back to a uniform shuffle when the metadata cache is unavailable
     * (e.g., in unit tests that do not supply a cache).
     */
    private fun weightedSample(pool: List<Int>, count: Int): List<Int> {
        if (cache == null) return pool.shuffled(random).take(count)

        // Build a mutable list of (itemId, weight) pairs; weight floored at 0.01
        // so items with weight=0 are effectively never chosen but don't break math.
        val weighted = pool.map { id ->
            id to (cache.get(id)?.purchaseWeight ?: 1.0f).coerceAtLeast(0.01f)
        }.toMutableList()

        val result = mutableListOf<Int>()
        repeat(count.coerceAtMost(weighted.size)) {
            val total = weighted.sumOf { (_, w) -> w.toDouble() }
            var r = random.nextDouble() * total
            // Walk the list consuming weight until r is exhausted
            val idx = weighted.indexOfFirst { (_, w) ->
                r -= w
                r <= 0.0
            }.let { if (it < 0) weighted.lastIndex else it }   // guard against float rounding
            result.add(weighted[idx].first)
            weighted.removeAt(idx)
        }
        return result
    }

    @SuppressLint("NewApi")
    private fun getCurrentTime(): Instant {
        return Instant.ofEpochMilli(System.currentTimeMillis())
    }

    // Replace Instant.now() with getCurrentTime()
    private fun buildTransaction(
        id: Int,
        transactionLines: List<TransactionLine>
    ): Transaction {
        val lines = transactionLines.map { line ->
            if (line.lostToOutOfStock) {
                // Keep original quantity so callers can compute (quantity - rungQty) = units lost.
                // Revenue is only charged for items that were actually rung (rungQty).
                line.copy(lineTotal = line.unitPrice * line.rungQty)
            } else {
                // Normal line: compact quantity down to what was rung
                line.copy(quantity = line.rungQty, lineTotal = line.unitPrice * line.rungQty)
            }
        }

        // Subtotal/tax/totalEarned only reflect rung lines — lost lines have lineTotal = ZERO
        val subtotal = lines.fold(Money(0)) { acc, line -> acc + line.lineTotal }
        val tax = Money.Companion.fromDollars(subtotal.toDouble() * salesTaxRate)
        val totalEarned = subtotal + tax

        return Transaction(
            id = id,
            lines = lines,
            subtotal = subtotal,
            tax = tax,
            totalEarned = totalEarned,
            completedAt = getCurrentTime()
        )
    }


    /**
     * Maybe create a pending RefundRequest and adjust inventory accordingly.
     */
    private fun maybeGenerateRefund(
        state: GameState,
        lines: List<TransactionLine>,
        inventory: Map<Int, InventoryState>
    ): GameState {
        if (random.nextDouble() >= refundChance) return state

        val refundable = lines.filter { it.rungQty > 0 && !it.lostToOutOfStock }
        if (refundable.isEmpty()) return state

        val numLinesToRefund = (1..refundable.size).random(random)
        val linesToRefund = refundable.shuffled(random).take(numLinesToRefund)

        var updatedInventory = inventory
        val refundLines = mutableListOf<TransactionLine>()
        var refundSubtotal = Money(0)

        for (line in linesToRefund) {
            val qtyToRefund = (1..line.rungQty).random(random)
            val itemId = line.itemId

            val inv = updatedInventory[itemId]
            if (inv != null) {
                // Refunded items are added as a new batch with current day as receivedDay
                val currentDay = state.currentTime.dayNumber
                val metadata = cache?.get(itemId)
                val expirationDay = if (metadata?.isPerishable == true) {
                    currentDay + (metadata.shelfLifeDays ?: 0)
                } else {
                    Int.MAX_VALUE
                }
                
                val newBatch = ItemBatch(
                    receivedDay = currentDay,
                    quantity = qtyToRefund,
                    expirationDay = expirationDay
                )
                
                val updatedShelfBatches = inv.mergeBatches(inv.shelfBatches + newBatch)
                updatedInventory = updatedInventory + (itemId to inv.copy(
                    shelfBatches = updatedShelfBatches
                ))
            }

            val itemPrice = line.unitPrice

            refundSubtotal += itemPrice * qtyToRefund

            refundLines += TransactionLine(
                itemId = itemId,
                quantity = -qtyToRefund,
                rungQty = -qtyToRefund,
                unitPrice = itemPrice,
                lineTotal = itemPrice * -qtyToRefund
            )
        }

        if (refundLines.isEmpty()) return state

        val refundTax = Money.Companion.fromDollars(refundSubtotal.toDouble() * salesTaxRate)

        val refundRequest = RefundRequest(
            id = state.nextRefundId,
            timestamp = System.currentTimeMillis(),
            originalTransactionId = state.currentTransaction.id,
            lines = refundLines.map { rl ->
                RefundLine(
                    itemId = rl.itemId,
                    quantity = -rl.quantity,
                    unitPrice = rl.unitPrice
                )
            },
            subtotal = refundSubtotal,
            tax = refundTax
        )

        return state.copy(
            inventory = updatedInventory,
            pendingRefunds = state.pendingRefunds + refundRequest,
            nextRefundId = state.nextRefundId + 1
        )
    }

    /**
     * Process an entire pending refund by id.
     */
    fun processRefund(state: GameState, refundId: Int): GameState {
        val refund = state.pendingRefunds.firstOrNull { it.id == refundId } ?: return state

        val soldLines = refund.lines.map { rl ->
            TransactionLine(
                itemId = rl.itemId,
                quantity = -rl.quantity,
                rungQty = -rl.quantity,
                unitPrice = rl.unitPrice,
                lineTotal = rl.unitPrice * -rl.quantity
            )
        }

        val refundTx = Transaction(
            id = -(state.salesHistory.count { it.id < 0 } + 1),
            lines = soldLines,
            subtotal = -refund.subtotal,
            tax = -refund.tax,
            totalEarned = -refund.subtotal,
            completedAt = getCurrentTime() // Use adjusted method
        )

        return state.copy(
            money = state.money - refund.subtotal,
            totalTaxCollected = state.totalTaxCollected - refund.tax,
            salesHistory = state.salesHistory + refundTx,
            pendingRefunds = state.pendingRefunds.filter { it.id != refundId }
        )
    }


    /**
     * Process a specific quantity of one line within a pending refund.
     */
    fun processRefundLine(
        state: GameState,
        refundId: Int,
        itemId: Int,
        qty: Int = 1
    ): GameState {
        if (qty <= 0) return state

        val refund = state.pendingRefunds.firstOrNull { it.id == refundId } ?: return state
        val lineIndex = refund.lines.indexOfFirst { it.itemId == itemId }
        if (lineIndex == -1) return state

        val line = refund.lines[lineIndex]
        val qtyToProcess = qty.coerceAtMost(line.quantity)
        if (qtyToProcess <= 0) return state

        val inv = state.inventory[itemId]
        val updatedInventory =
            if (inv != null) {
                // Refunded items are added as a new batch with current day as receivedDay
                val currentDay = state.currentTime.dayNumber
                val metadata = cache?.get(itemId)
                val expirationDay = if (metadata?.isPerishable == true) {
                    currentDay + (metadata.shelfLifeDays ?: 0)
                } else {
                    Int.MAX_VALUE
                }
                
                val newBatch = ItemBatch(
                    receivedDay = currentDay,
                    quantity = qtyToProcess,
                    expirationDay = expirationDay
                )
                
                val updatedShelfBatches = inv.mergeBatches(inv.shelfBatches + newBatch)
                state.inventory + (itemId to inv.copy(
                    shelfBatches = updatedShelfBatches
                ))
            } else {
                state.inventory
            }

        val lineRefundSubtotal = line.unitPrice * qtyToProcess
        val lineRefundTax = lineRefundSubtotal * salesTaxRate

        val soldLine = TransactionLine(
            itemId = itemId,
            quantity = -qtyToProcess,
            rungQty = -qtyToProcess,
            unitPrice = line.unitPrice,
            lineTotal = line.unitPrice * -qtyToProcess
        )

        val refundTx = Transaction(
            id = -(state.salesHistory.count { it.id < 0 } + 1),
            lines = listOf(soldLine),
            subtotal = -lineRefundSubtotal,
            tax = -lineRefundTax,
            totalEarned = -lineRefundSubtotal,
            completedAt = getCurrentTime() // Use adjusted method
        )

        val newRefundLines = refund.lines.toMutableList()
        val remainingQty = line.quantity - qtyToProcess

        if (remainingQty > 0) {
            newRefundLines[lineIndex] = line.copy(quantity = remainingQty)
        } else {
            newRefundLines.removeAt(lineIndex)
        }

        val newPending = state.pendingRefunds
            .map { if (it.id == refundId) it.copy(lines = newRefundLines) else it }
            .filter { it.lines.isNotEmpty() }

        return state.copy(
            inventory = updatedInventory,
            money = state.money - lineRefundSubtotal,
            totalTaxCollected = state.totalTaxCollected - lineRefundTax,
            salesHistory = state.salesHistory + refundTx,
            pendingRefunds = newPending
        )
    }

}
