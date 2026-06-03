package com.example.superstoresimulator.domain.Transactions

import android.annotation.SuppressLint
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RefundLine
import com.example.superstoresimulator.domain.RefundRequest
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.findRegisterById
import com.example.superstoresimulator.domain.updateRegister
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.staff.StaffManager
import java.time.Instant
import kotlin.random.Random

class TransactionEngine(
    private val salesTaxRate: Double = 0.0825,
    private val refundChance: Double = 0.00,
    private val random: Random = Random.Default,
    private val cache: ItemMetadataCache? = null,
) {

    /** Default register id: the first register in the list (or 0 if empty). */
    private fun defaultRegisterId(state: GameState): Int =
        state.registers.firstOrNull()?.registerId ?: 0

    fun startNewTransaction(
        state: GameState,
        registerId: Int = state.registers.firstOrNull()?.registerId ?: 0,
    ): GameState {
        val register = state.registers.findRegisterById(registerId) ?: return state

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

        val newTransaction = Transaction(
            id = register.currentTransaction.id + 1,
            lines = lines,
            subtotal = subtotal,
            tax = tax,
            totalEarned = totalEarned
        )

        return state.copy(
            registers = state.registers.updateRegister(
                register.copy(
                    currentTransaction = newTransaction,
                    transactionActive = true,
                )
            )
        )
    }

    /**
     * Generate a transaction for an autonomously arriving customer (Phase 2 / TrafficManager).
     *
     * Unlike [startNewTransaction], this method:
     * - Accepts a pre-determined [itemCount] from the customer's basket size
     * - Is a **no-op** when a transaction is already active on the target register
     * - Returns [state] unchanged if inventory is empty
     */
    fun generateRandomTransaction(
        state: GameState,
        itemCount: Int,
        registerId: Int = state.registers.firstOrNull()?.registerId ?: 0,
    ): GameState {
        val register = state.registers.findRegisterById(registerId) ?: return state
        // Don't interrupt an active transaction on this register
        if (register.transactionActive) return state

        val currentTierAmount = state.currentTier.unlockAmount
        val availableItemIds = state.inventory.keys.filter { itemId ->
            val itemTier = cache?.get(itemId)?.tier ?: ItemUnlockTier.TIER_1
            itemTier.unlockAmount <= currentTierAmount
        }
        if (availableItemIds.isEmpty()) return state

        val numLines = itemCount.coerceIn(1, availableItemIds.size.coerceAtMost(7))
        val zoneMultipliers = state.inventory.mapValues { (_, inv) ->
            StaffManager.zonePurchaseMultiplier(inv.zoneScore)
        }
        val chosen = weightedSample(availableItemIds, numLines, zoneMultipliers)
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

        val newTransaction = Transaction(
            id = register.currentTransaction.id + 1,
            lines = lines,
            subtotal = subtotal,
            tax = tax,
            totalEarned = totalEarned
        )

        return state.copy(
            registers = state.registers.updateRegister(
                register.copy(
                    currentTransaction = newTransaction,
                    transactionActive = true,
                )
            )
        )
    }

    /**
     * Ring up exactly one item (one unit on one line) on the specified register.
     */
    fun ringUpSingleItem(
        state: GameState,
        itemId: Int,
        registerId: Int = state.registers.firstOrNull()?.registerId ?: 0,
    ): GameState {
        val register = state.registers.findRegisterById(registerId) ?: return state

        val lines = register.currentTransaction.lines.toMutableList()
        if (lines.isEmpty()) {
            return startNewTransaction(state, registerId)
        }

        // Find the specific line for this item
        val lineIndex = lines.indexOfFirst { it.itemId == itemId }
        if (lineIndex == -1) return state

        val line = lines[lineIndex]

        // Skip lines already fully rung or already marked lost to OOS
        if (line.rungQty >= line.quantity || line.lostToOutOfStock) return state

        // If shelf stock is zero, mark the remaining quantity as lost (out-of-stock)
        // rather than blocking the transaction — the cashier moves on to the next item.
        if ((state.inventory[line.itemId]?.shelfStock ?: 0) == 0) {
            lines[lineIndex] = line.copy(lostToOutOfStock = true)
            return if (isTransactionComplete(lines)) {
                completeTransaction(state, lines, state.inventory, registerId)
            } else {
                updatePartialTransaction(state, lines, state.inventory, registerId)
            }
        }

        val updatedInventory = consumeShelfStock(state.inventory, itemId)

        // Increment rungQty for this specific line
        val updatedLines = incrementRungQty(lines, lineIndex)

        // Finish or continue the transaction
        return if (isTransactionComplete(updatedLines)) {
            completeTransaction(state, updatedLines, updatedInventory, registerId)
        } else {
            updatePartialTransaction(state, updatedLines, updatedInventory, registerId)
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
        inventory: Map<Int, InventoryState>,
        registerId: Int,
    ): GameState {
        val register = prev.registers.findRegisterById(registerId) ?: return prev

        val historyEntry = buildTransaction(
            id = register.currentTransaction.id,
            transactionLines = lines,
            registerId = registerId,
            gameDayNumber = prev.currentTime.dayNumber,
        )

        var newState = prev.copy(
            totalTransactionsCompleted = prev.totalTransactionsCompleted + 1,
            money = prev.money + historyEntry.totalEarned,
            totalRevenue = prev.totalRevenue + historyEntry.totalEarned,
            inventory = inventory,
            salesHistory = prev.salesHistory + historyEntry,
            totalTaxCollected = prev.totalTaxCollected + historyEntry.tax,
            registers = prev.registers.updateRegister(
                register.copy(
                    currentTransaction = register.currentTransaction.copy(lines = lines),
                    transactionActive = false,
                )
            ),
        )

        newState = maybeGenerateRefund(newState, lines, inventory, historyEntry.id)
        return newState
    }


    private fun updatePartialTransaction(
        prev: GameState,
        lines: List<TransactionLine>,
        inventory: Map<Int, InventoryState>,
        registerId: Int,
    ): GameState {
        val register = prev.registers.findRegisterById(registerId) ?: return prev
        return prev.copy(
            inventory = inventory,
            registers = prev.registers.updateRegister(
                register.copy(
                    currentTransaction = register.currentTransaction.copy(lines = lines)
                )
            ),
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
    private fun weightedSample(
        pool: List<Int>,
        count: Int,
        zoneMultipliers: Map<Int, Float>? = null,
    ): List<Int> {
        if (cache == null) return pool.shuffled(random).take(count)

        val weighted = pool.map { id ->
            val baseWeight = (cache.get(id)?.purchaseWeight ?: 1.0f).coerceAtLeast(0.01f)
            val zoneMultiplier = zoneMultipliers?.get(id) ?: 1.0f
            id to (baseWeight * zoneMultiplier)
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
        transactionLines: List<TransactionLine>,
        registerId: Int = 0,
        gameDayNumber: Int = 0,
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
            completedAt = getCurrentTime(),
            registerId = registerId,
            gameDayNumber = gameDayNumber,
        )
    }


    /**
     * Maybe create a pending RefundRequest and adjust inventory accordingly.
     * [completedTransactionId] is the id of the just-completed transaction (from its historyEntry).
     */
    private fun maybeGenerateRefund(
        state: GameState,
        lines: List<TransactionLine>,
        inventory: Map<Int, InventoryState>,
        completedTransactionId: Int,
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
            originalTransactionId = completedTransactionId,
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
