package com.example.superstoresimulator.domain.Transactions

import android.annotation.SuppressLint
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money
import com.example.superstoresimulator.domain.RegisterState
import com.example.superstoresimulator.domain.findRegisterById
import com.example.superstoresimulator.domain.updateRegister
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.metrics.OutOfStockEvent
import com.example.superstoresimulator.domain.metrics.SoldItemEvent
import com.example.superstoresimulator.domain.pricing.MarkdownReason
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.vendor.VendorManager
import java.time.Instant
import kotlin.math.roundToLong
import kotlin.random.Random

class TransactionEngine(
    private val salesTaxRate: Double = DEFAULT_SALES_TAX_RATE,
    private val random: Random = Random.Default,
    private val cache: ItemMetadataCache? = null,
    private val pricingManager: PricingManager? = null,
    private val vendorManager: VendorManager? = null,
) {
    // Available-item cache (invalidated on research/vendor/inventory changes)
    private var cachedAvailableIds: List<Int>? = null
    private var cachedResearchUpgrades: Set<String>? = null
    private var cachedVendorTier: Int = -1
    private var cachedInventory: Map<Int, InventoryState>? = null

    private fun getAvailableItemIds(state: GameState): List<Int> {
        val upgrades = state.researchState.researchedUpgrades
        val vendorTier = state.vendorSystem.currentVendorTier

        // Key on the inventory map identity (===), not its size: a same-size key swap
        // (one item removed, another added) must still invalidate the cache.
        if (cachedAvailableIds != null &&
            upgrades === cachedResearchUpgrades &&
            vendorTier == cachedVendorTier &&
            state.inventory === cachedInventory) {
            return cachedAvailableIds!!
        }

        val result = state.inventory.keys.filter { itemId ->
            val meta = cache?.get(itemId) ?: return@filter true
            if (!cache.isItemAccessible(itemId, upgrades)) return@filter false
            if (meta.isVendorItem && meta.vendorTier > vendorTier) return@filter false
            true
        }
        cachedAvailableIds = result
        cachedResearchUpgrades = upgrades
        cachedVendorTier = vendorTier
        cachedInventory = state.inventory
        return result
    }

    /** Default register id: the first register in the list (or 0 if empty). */
    private fun defaultRegisterId(state: GameState): Int =
        state.registers.firstOrNull()?.registerId ?: 0

    fun startNewTransaction(
        state: GameState,
        registerId: Int = state.registers.firstOrNull()?.registerId ?: 0,
    ): GameState {
        val register = state.registers.findRegisterById(registerId) ?: return state

        val availableItemIds = getAvailableItemIds(state)
        if (availableItemIds.isEmpty()) return state

        val numLines = (1..availableItemIds.size.coerceAtMost(5)).random(random)
        val lines = mutableListOf<TransactionLine>()

        val pricingData = pricingManager?.computePricingData(state)
        val chosen = weightedSample(availableItemIds, numLines, pricingMultipliers = pricingData?.multipliers)

        for (itemId in chosen) {
            lines += buildTransactionLine(itemId, pricingData)
        }

        val subtotal = lines.fold(Money(0)) { acc, line -> acc + line.lineTotal }
        val tax = Money.Companion.fromDollars(subtotal.toDouble() * salesTaxRate)
        val totalEarned = subtotal + tax

        val newTransaction = Transaction(
            id = state.nextTransactionId,
            lines = lines,
            subtotal = subtotal,
            tax = tax,
            totalEarned = totalEarned
        )

        return state.copy(
            nextTransactionId = state.nextTransactionId + 1,
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
        cachedPricingData: PricingManager.PricingData? = null,
    ): GameState {
        val register = state.registers.findRegisterById(registerId) ?: return state
        // Don't interrupt an active transaction on this register
        if (register.transactionActive) return state

        val availableItemIds = getAvailableItemIds(state)
        if (availableItemIds.isEmpty()) return state

        val basketMult = state.pricingState.basketSizeMultiplier
        val adjustedItemCount = if (basketMult < 1.0f) {
            (itemCount * basketMult).toInt().coerceAtLeast(1)
        } else itemCount
        val numLines = adjustedItemCount.coerceIn(1, availableItemIds.size)
        val pricingData = cachedPricingData ?: pricingManager?.computePricingData(state)
        val chosen = weightedSample(availableItemIds, numLines, state.inventory, pricingData?.multipliers)
        val lines = mutableListOf<TransactionLine>()

        for (itemId in chosen) {
            lines += buildTransactionLine(itemId, pricingData)
        }

        val subtotal = lines.fold(Money(0)) { acc, line -> acc + line.lineTotal }
        val tax = Money.Companion.fromDollars(subtotal.toDouble() * salesTaxRate)
        val totalEarned = subtotal + tax

        val newTransaction = Transaction(
            id = state.nextTransactionId,
            lines = lines,
            subtotal = subtotal,
            tax = tax,
            totalEarned = totalEarned
        )

        return state.copy(
            nextTransactionId = state.nextTransactionId + 1,
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

    private fun buildTransactionLine(
        itemId: Int,
        pricingData: PricingManager.PricingData?,
    ): TransactionLine {
        val meta = cache?.get(itemId)
        val resolved = pricingData?.resolvePrice(itemId)

        val unitPrice = resolved?.effectivePrice ?: meta?.price ?: Money.Companion.fromDollars(9.99)
        val basePrice = resolved?.basePrice ?: unitPrice
        val modifier = resolved?.modifierPercent ?: 0

        return if (meta?.soldByWeight == true) {
            val weight = pricingManager?.randomWeight(meta.category, random) ?: 1.0f
            val lineCents = (unitPrice.cents.toDouble() * weight).roundToLong()
            TransactionLine(
                itemId = itemId, quantity = 1, rungQty = 0,
                unitPrice = unitPrice, lineTotal = Money(lineCents),
                basePrice = basePrice, priceModifier = modifier, weight = weight,
            )
        } else {
            val qty = (1..3).random(random)
            TransactionLine(
                itemId = itemId, quantity = qty, rungQty = 0,
                unitPrice = unitPrice, lineTotal = unitPrice * qty,
                basePrice = basePrice, priceModifier = modifier,
            )
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
        
        val newZone = (inv.zoneScore - StaffManager.ZONE_DECAY_PER_SALE).coerceAtLeast(0f)
        val updated = inv.copy(shelfBatches = updatedShelfBatches, zoneScore = newZone)
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

        if (lines.all { it.lostToOutOfStock && it.rungQty == 0 }) {
            val oosEvents = lines.map { l ->
                OutOfStockEvent(
                    itemId = l.itemId,
                    quantityLost = l.quantity,
                    revenueLost = l.unitPrice * l.quantity,
                )
            }
            val lostRevenue = oosEvents.fold(Money.ZERO) { m, e -> m + e.revenueLost }
            val lostCount = oosEvents.sumOf { it.quantityLost }
            val acc = prev.currentDayMetrics
            return prev.copy(
                registers = prev.registers.updateRegister(
                    register.copy(
                        currentTransaction = Transaction(),
                        transactionActive = false,
                    )
                ),
                currentDayMetrics = acc.copy(
                    lostRevenue = acc.lostRevenue + lostRevenue,
                    itemsLostToOutOfStock = acc.itemsLostToOutOfStock + lostCount,
                    outOfStockEvents = acc.outOfStockEvents + oosEvents,
                ),
            )
        }

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

        if (vendorManager != null) {
            for (line in lines) {
                if (line.lostToOutOfStock || line.rungQty == 0) continue
                vendorManager.getVendorForItem(line.itemId) ?: continue
                newState = vendorManager.deductVendorCommission(
                    newState, line.itemId, line.lineTotal, line.rungQty,
                )
            }
        }

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
        inventory: Map<Int, com.example.superstoresimulator.domain.inventory.InventoryState>? = null,
        pricingMultipliers: Map<Int, Float>? = null,
    ): List<Int> {
        if (cache == null) return pool.shuffled(random).take(count)

        val weighted = pool.map { id ->
            val baseWeight = (cache.get(id)?.purchaseWeight ?: 1.0f).coerceAtLeast(0.01f)
            val zoneMultiplier = inventory?.get(id)?.let { StaffManager.zonePurchaseMultiplier(it.zoneScore) } ?: 1.0f
            val priceMultiplier = pricingMultipliers?.get(id) ?: 1.0f
            id to (baseWeight * zoneMultiplier * priceMultiplier)
        }.toMutableList()

        val result = mutableListOf<Int>()
        repeat(count.coerceAtMost(weighted.size)) {
            // Apply affinity boost and substitution penalty based on already-selected items
            val adjustedWeights = weighted.map { (id, baseW) ->
                val hasAffinity = result.any { sel -> cache.sharesAffinityGroup(sel, id) }
                val hasSubstitution = result.any { sel -> cache.sharesSubstitutionGroup(sel, id) }
                val multiplier = when {
                    hasSubstitution -> SUBSTITUTION_PENALTY
                    hasAffinity -> AFFINITY_BOOST
                    else -> 1.0f
                }
                id to (baseW * multiplier)
            }

            val total = adjustedWeights.sumOf { (_, w) -> w.toDouble() }
            var r = random.nextDouble() * total
            val idx = adjustedWeights.indexOfFirst { (_, w) ->
                r -= w
                r <= 0.0
            }.let { if (it < 0) adjustedWeights.lastIndex else it }
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

    // ── Metrics-tracked ring-up (moved from GameEngine) ──────────────────────

    companion object {
        const val XP_PER_TRANSACTION = 5
        const val DEFAULT_SALES_TAX_RATE = 0.0825
        const val AFFINITY_BOOST = 2.0f
        const val SUBSTITUTION_PENALTY = 0.1f
    }

    fun ringUpItemOnRegister(state: GameState, registerId: Int): GameState {
        val register = state.registers.findRegisterById(registerId) ?: return state
        val lines = register.currentTransaction.lines
        val ringable = lines.filter { it.rungQty < it.quantity && !it.lostToOutOfStock }
        if (ringable.isEmpty()) return state
        val randomLine = ringable.random()
        return ringUpItemAndTrackMetrics(state, randomLine.itemId, registerId)
    }

    fun ringUpItemAndTrackMetrics(state: GameState, itemId: Int, registerId: Int): GameState {
        val prevCompleted = state.totalTransactionsCompleted
        var s = ringUpSingleItem(state, itemId, registerId)
        if (s.totalTransactionsCompleted <= prevCompleted || s.salesHistory.isEmpty()) return s

        val tx = s.salesHistory.last()
        val acc = s.currentDayMetrics

        val lostLines = tx.lines.filter { it.lostToOutOfStock }
        val txLostRevenue = lostLines.fold(Money.ZERO) { m, l ->
            m + l.unitPrice * (l.quantity - l.rungQty)
        }
        val txLostItemCount = lostLines.sumOf { it.quantity - it.rungQty }
        val oosEvents = lostLines.map { l ->
            OutOfStockEvent(
                itemId = l.itemId,
                quantityLost = l.quantity - l.rungQty,
                revenueLost = l.unitPrice * (l.quantity - l.rungQty),
            )
        }

        val soldEvents = tx.lines
            .filter { !it.lostToOutOfStock && it.quantity > 0 }
            .map { l ->
                SoldItemEvent(
                    itemId = l.itemId,
                    quantitySold = l.quantity,
                    revenue = l.lineTotal,
                )
            }

        var txMarkupExtra = Money.ZERO
        var txMarkdownSaved = Money.ZERO
        for (line in tx.lines) {
            if (line.lostToOutOfStock || line.quantity <= 0) continue
            val effectiveQty = line.weight?.toDouble() ?: line.quantity.toDouble()
            // Round, matching how lineTotal is computed (buildTransactionLine uses roundToLong),
            // so an unmarked weighted item doesn't fabricate a 1-cent markup/markdown.
            val baseCents = (line.basePrice.cents * effectiveQty).roundToLong()
            val diff = line.lineTotal.cents - baseCents
            if (diff > 0) {
                txMarkupExtra += Money(diff)
            } else if (diff < 0) {
                txMarkdownSaved += Money(-diff)
            }
        }

        val completedReg = s.registers.findRegisterById(tx.registerId)
        val updatedRegisters = if (completedReg != null) {
            s.registers.updateRegister(
                completedReg.copy(
                    dailyTransactions = completedReg.dailyTransactions + 1,
                    dailyRevenue = completedReg.dailyRevenue + tx.totalEarned,
                )
            )
        } else s.registers

        s = s.copy(
            currentDayMetrics = acc.copy(
                subtotal = acc.subtotal + tx.subtotal,
                taxCollected = acc.taxCollected + tx.tax,
                transactionsCompleted = acc.transactionsCompleted + 1,
                customersServed = acc.customersServed + 1,
                itemsSold = acc.itemsSold + tx.lines
                    .filter { !it.lostToOutOfStock }
                    .sumOf { it.quantity },
                lostRevenue = acc.lostRevenue + txLostRevenue,
                itemsLostToOutOfStock = acc.itemsLostToOutOfStock + txLostItemCount,
                outOfStockEvents = acc.outOfStockEvents + oosEvents,
                soldItemEvents = acc.soldItemEvents + soldEvents,
                markupExtraRevenue = acc.markupExtraRevenue + txMarkupExtra,
                markdownsSaved = acc.markdownsSaved + txMarkdownSaved,
            ),
            registers = updatedRegisters,
        )

        val reg = s.registers.findRegisterById(tx.registerId)
        val cashierId = reg?.assignedCashierId
        if (cashierId != null) {
            s = s.copy(
                hiredEntityRegistry = s.hiredEntityRegistry.grantXp(cashierId, XP_PER_TRANSACTION)
            )
        }

        for (line in tx.lines) {
            s = clearStaleExpiryMarkdown(s, line.itemId)
        }

        return s
    }

    fun clearStaleExpiryMarkdown(state: GameState, itemId: Int): GameState {
        val pm = pricingManager ?: return state
        val md = state.pricingState.activeMarkdowns[itemId] ?: return state
        if (md.reason != MarkdownReason.EXPIRING_SOON) return state
        val inv = state.inventory[itemId] ?: return state
        val currentDay = state.currentTime.dayNumber
        val hasExpiring = (inv.shelfBatches + inv.backroomBatches).any { batch ->
            batch.expirationDay != Int.MAX_VALUE &&
                batch.expirationDay - currentDay <= pm.config.expiryThresholdDays
        }
        if (!hasExpiring) {
            return pm.clearMarkdown(state, itemId)
        }
        return state
    }
}
