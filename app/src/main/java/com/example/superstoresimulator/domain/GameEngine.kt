package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DailyMetricsAccumulator
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.time.StoreState
import com.example.superstoresimulator.domain.traffic.TrafficManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameEngine(private val itemMetadataCache: ItemMetadataCache) {
    private val txEngine = TransactionEngine(itemMetadataCache = itemMetadataCache)
    private val timeManager = TimeManager()
    private val trafficManager = TrafficManager()  // Phase 2: autonomous customer generation

    // Emit incremental changes instead of full state reconstructions
    private val _changes = MutableStateFlow<GameStateChange?>(null)
    val changes: StateFlow<GameStateChange?> = _changes.asStateFlow()

    var state = GameState(
        inventory = mutableMapOf(),
        currentTime = timeManager.currentTime,
        storeState = timeManager.getStoreState()
    )
        internal set

    init {
        // Initialize inventory from cached items (already loaded in ItemMetadataCache)
        val allDbItems = itemMetadataCache.getAllItems()
        if (allDbItems.isNotEmpty()) {
            val inventory = mutableMapOf<Int, InventoryState>()
            allDbItems.forEach { (itemId, dbItem) ->
                inventory[itemId] = InventoryState(
                    shelfStock = 10,  // Start with 10 items on shelf
                    backroomStock = 10  // Start with 10 items in backroom
                )
            }
            state = state.copy(inventory = inventory)
        }
    }
    
    fun getDbItem(itemId: Int): Item? {
        return itemMetadataCache.getItem(itemId)
    }

    fun currentState(): GameState = state

    // Inventory operations
    fun stockItemFromBackroom(itemId: Int) {
        val dyn = state.inventory[itemId] ?: return
        if (dyn.backroomStock <= 0) return

        val updated = dyn.copy(
            shelfStock = dyn.shelfStock + 1,
            backroomStock = dyn.backroomStock - 1
        )

        state = state.copy(
            inventory = state.inventory + (itemId to updated),
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsStocked = state.currentDayMetrics.itemsStocked + 1
            )
        )
        
        // ✅ Problem #4: Emit change for inventory update
        _changes.value = GameStateChange.InventoryUpdated(itemId, updated)
    }
    private fun stockRandomItemFromBackroom() {
        val candidates = state.inventory
            .filter { (_, dyn) -> dyn.backroomStock > 0 }

        if (candidates.isEmpty()) return

        val lowestShelf = candidates.minOf { it.value.shelfStock }
        val lowestGroup = candidates.filter { it.value.shelfStock == lowestShelf }

        val targetId = lowestGroup.keys.random()
        
        // Stock an entire case pack instead of just 1 item
        stockCasePackFromBackroom(targetId)
    }
    
    /**
     * Stock an entire case pack from backroom to shelf
     * Used by stockers to move full case packs efficiently
     */
    private fun stockCasePackFromBackroom(itemId: Int) {
        val dyn = state.inventory[itemId] ?: return
        val dbItem = itemMetadataCache.getItem(itemId) ?: return
        
        if (dyn.backroomStock <= 0) return
        
        // Stock up to a full case pack (or whatever is available, if less than case pack)
        val casePackSize = dbItem.casePack
        val itemsToStock = minOf(casePackSize, dyn.backroomStock)
        
        val updated = dyn.copy(
            shelfStock = dyn.shelfStock + itemsToStock,
            backroomStock = dyn.backroomStock - itemsToStock
        )
        
        state = state.copy(
            inventory = state.inventory + (itemId to updated),
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsStocked = state.currentDayMetrics.itemsStocked + itemsToStock
            )
        )
    }
    fun buyItemToBackroom(itemId: Int) {
        val dyn = state.inventory[itemId] ?: return
        val dbItem = itemMetadataCache.getItem(itemId) ?: return

        val itemsInCasePack = dbItem.casePack

        // Backroom cap: refuse if a full case pack would exceed the per-item limit.
        // Player must stock shelves to free up backroom space before ordering more.
        val cap = state.storeConfig.backroomCapPerItem
        if (dyn.backroomStock + itemsInCasePack > cap) return

        // Calculate cost for one case pack
        val casePackCost = dbItem.getCasePackCostAsMoney()
        if (state.money < casePackCost) return

        // When ordering a case pack, add all items from the case to backroom
        val updated = dyn.copy(
            backroomStock = dyn.backroomStock + itemsInCasePack
        )

        state = state.copy(
            inventory = state.inventory + (itemId to updated),
            money = state.money - casePackCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + itemsInCasePack
            )
        )
        
        // ✅ Problem #4: Emit change for money and inventory
        _changes.value = GameStateChange.MoneyChanged(state.money)
        _changes.value = GameStateChange.InventoryUpdated(itemId, updated)
    }
    
    /**
     * Order multiple case packs at once.
     * Delivery is clamped to however many full case packs fit within the backroom cap.
     * The player is only charged for the case packs actually delivered.
     */
    fun buyItemCasePacks(itemId: Int, numCasePacks: Int) {
        val dyn = state.inventory[itemId] ?: return
        val dbItem = itemMetadataCache.getItem(itemId) ?: return
        
        if (numCasePacks <= 0) return

        // Clamp to however many full case packs still fit within the backroom cap.
        val cap = state.storeConfig.backroomCapPerItem
        val availableSpace = cap - dyn.backroomStock
        val actualCasePacks = minOf(numCasePacks, availableSpace / dbItem.casePack)
        if (actualCasePacks <= 0) return
        
        // Calculate total cost for actually delivered case packs
        val casePackCost = dbItem.getCasePackCostAsMoney()
        val totalCost = casePackCost * actualCasePacks
        
        if (state.money < totalCost) return
        
        // Add all items from delivered case packs to backroom
        val totalItemsAdded = dbItem.casePack * actualCasePacks
        val updated = dyn.copy(
            backroomStock = dyn.backroomStock + totalItemsAdded
        )
        
        state = state.copy(
            inventory = state.inventory + (itemId to updated),
            money = state.money - totalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItemsAdded
            )
        )
    }

    /**
     * Place a bulk order — buys [casePacksPerItem] case packs for every item whose
     * combined shelf + backroom stock is ≤ [maxTotalQuantity] and (optionally) whose
     * category matches [categoryFilter].
     *
     * Backroom cap: items with no room for even one full case pack are excluded.
     * Per-item delivery is clamped to however many full case packs fit in the remaining
     * backroom space; the player is only charged for case packs actually delivered.
     *
     * Volume discount tiers (applied to the total actual cases delivered):
     *   ≥ 20 cases  → 10% off
     *   ≥ 50 cases  → 15% off
     *   ≥ 100 cases → 25% off
     *
     * Does nothing if the player cannot afford the discounted total.
     * Only items whose tier ≤ [GameState.currentTier] are eligible.
     */
    fun placeBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: com.example.superstoresimulator.domain.items.ItemCategory?) {
        if (casePacksPerItem <= 0) return

        val cap = state.storeConfig.backroomCapPerItem

        // Collect matching items: tier-gated, category-filtered, below stock threshold,
        // AND must have room in the backroom for at least one full case pack.
        val matchingEntries = state.inventory.filter { (itemId, inv) ->
            val meta = itemMetadataCache.get(itemId) ?: return@filter false
            val totalQty = inv.shelfStock + inv.backroomStock
            val tierOk = meta.tier.unlockAmount <= state.currentTier.unlockAmount
            val categoryOk = categoryFilter == null || meta.category == categoryFilter
            val qtyOk = totalQty <= maxTotalQuantity
            val capOk = (cap - inv.backroomStock) >= meta.casePack  // ≥1 full case pack fits
            tierOk && categoryOk && qtyOk && capOk
        }

        if (matchingEntries.isEmpty()) return

        // Per-item: clamp to however many full case packs actually fit within the cap.
        // Only charge for (and deliver) the case packs that can actually be received.
        val itemsToAddMap = mutableMapOf<Int, Int>()   // itemId → units to add to backroom
        val itemCostMap   = mutableMapOf<Int, Money>() // itemId → cost for this item's order
        var totalCases = 0
        var baseCost = Money.ZERO

        matchingEntries.forEach { (itemId, inv) ->
            val dbItem = itemMetadataCache.getItem(itemId) ?: return@forEach
            val availableSpace = cap - inv.backroomStock
            val actualCasePacks = minOf(casePacksPerItem, availableSpace / dbItem.casePack)
            if (actualCasePacks <= 0) return@forEach
            val itemsToAdd = dbItem.casePack * actualCasePacks
            val cost = dbItem.getCasePackCostAsMoney() * actualCasePacks
            itemsToAddMap[itemId] = itemsToAdd
            itemCostMap[itemId] = cost
            totalCases += actualCasePacks
            baseCost += cost
        }

        if (itemsToAddMap.isEmpty()) return

        // Apply volume discount based on actual total cases delivered
        val discountFraction = when {
            totalCases >= 100 -> 0.25
            totalCases >= 50  -> 0.15
            totalCases >= 20  -> 0.10
            else              -> 0.0
        }
        val finalCost = Money((baseCost.cents * (1.0 - discountFraction)).toLong())

        if (state.money < finalCost) return

        // Apply stock additions
        var newInventory = state.inventory
        var totalItemsAdded = 0
        itemsToAddMap.forEach { (itemId, itemsToAdd) ->
            val inv = newInventory[itemId] ?: return@forEach
            newInventory = newInventory + (itemId to inv.copy(backroomStock = inv.backroomStock + itemsToAdd))
            totalItemsAdded += itemsToAdd
        }

        state = state.copy(
            inventory = newInventory,
            money = state.money - finalCost,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsOrdered = state.currentDayMetrics.itemsOrdered + totalItemsAdded
            )
        )

        _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    // Transaction operations
    fun startTransaction() {
        // Only start a new transaction if one is not already in progress.
        // Previously had a second unconditional `if (storeState != CLOSED)` call here
        // which caused startNewTransaction to fire twice, skipping every other ID.
        if (!state.transactionActive && state.storeState != StoreState.CLOSED) {
            state = txEngine.startNewTransaction(state)
        }
    }
    fun ringUpItem(itemId: Int) {
        val prevCompleted = state.totalTransactionsCompleted
        state = txEngine.ringUpSingleItem(state, itemId)
        // If a transaction just completed, record it in today's accumulator
        if (state.totalTransactionsCompleted > prevCompleted && state.salesHistory.isNotEmpty()) {
            val tx = state.salesHistory.last()
            val acc = state.currentDayMetrics

            // Compute out-of-stock losses for this transaction
            val lostLines = tx.lines.filter { it.lostToOutOfStock }
            val txLostRevenue = lostLines.fold(Money.ZERO) { m, l ->
                m + l.unitPrice * (l.quantity - l.rungQty)
            }
            val txLostItemCount = lostLines.sumOf { it.quantity - it.rungQty }
            val oosEvents = lostLines.map { l ->
                com.example.superstoresimulator.domain.metrics.OutOfStockEvent(
                    itemId = l.itemId,
                    itemName = itemMetadataCache.get(l.itemId)?.name ?: "Item ${l.itemId}",
                    quantityLost = l.quantity - l.rungQty,
                    revenueLost = l.unitPrice * (l.quantity - l.rungQty),
                )
            }

            // Record a SoldItemEvent for every successfully fulfilled line
            val soldEvents = tx.lines
                .filter { !it.lostToOutOfStock && it.quantity > 0 }
                .map { l ->
                    com.example.superstoresimulator.domain.metrics.SoldItemEvent(
                        itemId = l.itemId,
                        itemName = itemMetadataCache.get(l.itemId)?.name ?: "Item ${l.itemId}",
                        quantitySold = l.quantity,
                        revenue = l.lineTotal,
                    )
                }

            state = state.copy(
                currentDayMetrics = acc.copy(
                    revenue = acc.revenue + tx.totalEarned,
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
                )
            )
        }
    }

    fun ringUpItem() {
        val lines = state.currentTransaction.lines

        // Exclude lines that are already fully rung OR already marked lost to OOS
        val ringable = lines.filter { it.rungQty < it.quantity && !it.lostToOutOfStock }
        if (ringable.isEmpty()) return

        // Pick one at random
        val randomLine = ringable.random()

        // Ring up that specific item
        ringUpItem(randomLine.itemId)
    }

    fun processRefund(refundId: Int) {
        val refund = state.pendingRefunds.find { it.id == refundId }
        state = txEngine.processRefund(state, refundId)
        if (refund != null) {
            val refundTotal = refund.subtotal + refund.tax
            state = state.copy(
                currentDayMetrics = state.currentDayMetrics.copy(
                    refundsProcessed = state.currentDayMetrics.refundsProcessed + 1,
                    refundAmount = state.currentDayMetrics.refundAmount + refundTotal,
                )
            )
        }
    }
    fun processRefundLine(refundId: Int, itemId: Int, qty: Int = 1) {
        state = txEngine.processRefundLine(state, refundId, itemId, qty)
    }
    fun hireEntity(def: EntityDef, type: EntityType) {
        val cost = def.cost
        if (state.money < cost) return

        val updatedRegistry = state.hiredEntityRegistry.hireEntity(def, type)

        state = state.copy(
            hiredEntityRegistry = updatedRegistry,
            money = state.money - cost
        )
    }
    fun upgradeEntity(entityId: Int) {
        val cost = state.hiredEntityRegistry.getById(entityId).entityDefinition.nextUpgrade?.cost ?: Money(0)

        // Not enough money
        if (state.money < cost) return

        // Apply upgrade
        val updatedRegistry = state.hiredEntityRegistry.upgradeEntity(entityId)

        // Commit state
        state = state.copy(
            hiredEntityRegistry = updatedRegistry,
            money = state.money - cost
        )
    }
    fun fireEntity(entityId: Int) {
        val updatedRegistry = state.hiredEntityRegistry.fireEntity(entityId)
        state = state.copy(hiredEntityRegistry = updatedRegistry)
    }
    fun updateStoreName(newName: String) {
        state = state.copy(storeName = newName)
    }

    // Game tick (mostly staff actions)
    fun tick(deltaMilliseconds: Long) {
        // Phase 1: Only update time if player has not paused the game
        if (!state.playerPausedTime) {
            timeManager.update(deltaMilliseconds)
            state = state.copy(currentTime = timeManager.currentTime)

            // Phase 3: Detect day rollover (midnight)
            val newDayNumber = state.currentTime.dayNumber
            if (newDayNumber != lastKnownDayNumber) {
                rollOverDay(lastKnownDayNumber)
                lastKnownDayNumber = newDayNumber
            }

            // Phase 1: Update store state based on time
            val newStoreState = timeManager.getStoreState()
            if (newStoreState != state.storeState) {
                handleStoreStateChange(newStoreState)
            }
            state = state.copy(storeState = newStoreState)

            val delta = deltaMilliseconds / 1000.0
            val multiplier = state.storeConfig.gameSpeedMultiplier

            // Phase 2: Traffic arrivals — add to the waiting queue (don't start directly)
            if (state.storeState == StoreState.OPEN) {
                val newCustomers = trafficManager.update(state, delta)
                if (newCustomers.isNotEmpty()) {
                    state = state.copy(pendingCustomers = state.pendingCustomers + newCustomers.size)
                }
            }

            // Phase 2: Dequeue the next waiting customer when the register is free
            if (state.storeState == StoreState.OPEN &&
                !state.transactionActive &&
                state.pendingCustomers > 0
            ) {
                val basketSize = (2..5).random()
                state = txEngine.generateRandomTransaction(state, basketSize)
                // Only decrement if the transaction actually started
                if (state.transactionActive) {
                    state = state.copy(pendingCustomers = (state.pendingCustomers - 1).coerceAtLeast(0))
                }
            }

            // Process hired cashiers — ring up items in the active transaction
            if (state.storeState == StoreState.OPEN) {
                val cashiers = state.hiredEntityRegistry.countByEntity(EntityDef.CASHIER)

                if (cashiers > 0) {
                    val itemsPerSecondPerCashier = 0.5f
                    val itemsThisTick =
                        itemsPerSecondPerCashier * cashiers * delta.toFloat() * multiplier

                    autoClickProgress += itemsThisTick

                    val wholeItems = autoClickProgress.toInt()
                    autoClickProgress -= wholeItems

                    repeat(wholeItems) {
                        ringUpItem()
                    }
                }
            }

            val stockers = state.hiredEntityRegistry.countByEntity(EntityDef.STOCKER)

            if (stockers > 0) {
                val stockActionsPerSecondPerStocker = 0.1f
                val stockActionsThisTick =
                    stockActionsPerSecondPerStocker * stockers * delta.toFloat() * multiplier

                stockerProgress += stockActionsThisTick
                val wholeStockActions = stockerProgress.toInt()
                stockerProgress -= wholeStockActions

                repeat(wholeStockActions) {
                    stockRandomItemFromBackroom()
                }
            }

            // Phase 2: Process player work (mutually exclusive roles)
            when (state.playerRole) {
                PlayerRole.CASHIER -> performPlayerCashierWork(delta)
                PlayerRole.STOCKER -> performPlayerStockerWork(delta)
                PlayerRole.NONE -> {
                    // Reset accumulators when the player is not actively working
                    if (state.playerCashierProgress != 0f || state.playerStockerProgress != 0f) {
                        state = state.copy(
                            playerCashierProgress = 0f,
                            playerStockerProgress = 0f
                        )
                    }
                }
            }
        }
    }

    /**
     * Player working as cashier: automatically rings up items in the active transaction
     * at the same speed as a hired cashier (0.5 items/second).
     */
    private fun performPlayerCashierWork(deltaSeconds: Double) {
        val itemsPerSecond = 0.5f
        val multiplier = state.storeConfig.gameSpeedMultiplier
        var progress = state.playerCashierProgress +
                (itemsPerSecond * deltaSeconds.toFloat() * multiplier)

        while (progress >= 1.0f && state.transactionActive) {
            ringUpItem()  // reuses existing ring-up logic; mutates state
            progress -= 1.0f
        }

        // If the transaction completed during this work cycle, discard any leftover
        // progress so it doesn't burst-ring items when the next transaction starts.
        if (!state.transactionActive) {
            progress = 0f
        }

        state = state.copy(playerCashierProgress = progress.coerceIn(0f, 1f))
    }

    /**
     * Player working as stocker: automatically moves backroom stock to shelves
     * at the same speed as a hired stocker (0.1 case-packs/second).
     */
    private fun performPlayerStockerWork(deltaSeconds: Double) {
        val actionsPerSecond = 0.3f  // faster than hired stocker — player is hands-on
        val multiplier = state.storeConfig.gameSpeedMultiplier
        var progress = state.playerStockerProgress +
                (actionsPerSecond * deltaSeconds.toFloat() * multiplier)

        while (progress >= 1.0f) {
            stockRandomItemFromBackroom()  // mutates state
            progress -= 1.0f
        }

        // If the backroom is now empty there is nothing left to stock, so
        // automatically return the player to the NONE (manage) role.
        val backroomEmpty = state.inventory.values.none { it.backroomStock > 0 }
        if (backroomEmpty) {
            state = state.copy(
                playerRole = PlayerRole.NONE,
                playerStockerProgress = 0f,
            )
        } else {
            state = state.copy(playerStockerProgress = progress)
        }
    }
    
    /**
     * Handle transitions between store states
     */
    private fun handleStoreStateChange(newState: StoreState) {
        when (newState) {
            StoreState.OPEN -> {
                // Opening - could add procedures here later
            }
            StoreState.CLOSING -> {
                // Starting closing procedures
                // Stop accepting new transactions
            }
            StoreState.CLOSED -> {
                // Store closed — reset traffic accumulator and drain the waiting queue
                trafficManager.reset()
                if (state.pendingCustomers > 0) {
                    state = state.copy(pendingCustomers = 0)
                }
            }
            else -> {}
        }
    }
    
    /**
     * Set game speed multiplier (for speed controls 1x, 2x, 4x, etc.)
     * @param multiplier The multiplier value (1.0f for 1x, 2.0f for 2x, 4.0f for 4x, etc.)
     */
    fun setGameSpeed(multiplier: Float) {
        timeManager.setSpeedMultiplier(multiplier)
        state = state.copy(storeConfig = state.storeConfig.copy(gameSpeedMultiplier = multiplier))
    }
    
    /**
     * Toggle the store between open and closed
     * Clicking CLOSED will manually open the store (resume operations)
     * Clicking OPEN will manually close the store (pause operations)
     */
    fun toggleTimePaused() {
        state = state.copy(playerPausedTime = !state.playerPausedTime)
    }

    /**
     * Phase 2: Set the player's active work role.
     * Passing the currently active role toggles it OFF (back to NONE).
     */
    fun setPlayerRole(role: PlayerRole) {
        val newRole = if (state.playerRole == role) PlayerRole.NONE else role
        state = state.copy(
            playerRole = newRole,
            // Reset progress accumulators when switching roles
            playerCashierProgress = 0f,
            playerStockerProgress = 0f
        )
    }

    /**
     * Snapshot today's metrics accumulator into a [DailyMetrics] record,
     * append it to completedDayMetrics, and start a fresh accumulator for the new day.
     * Also sets [GameState.showEndOfDayReport] so the UI can surface the day summary.
     */
    private fun rollOverDay(dayNumber: Int) {
        val snapshot = state.currentDayMetrics.toSnapshot(dayOfWeek = dayNumber % 7)
        // Pause the game for the report. Only set the auto-pause flag when time wasn't
        // already paused by the player — so we don't accidentally unpause on dismiss.
        val wasAlreadyPaused = state.playerPausedTime
        state = state.copy(
            completedDayMetrics = state.completedDayMetrics + snapshot,
            currentDayMetrics = DailyMetricsAccumulator(dayNumber = dayNumber + 1),
            showEndOfDayReport = true,
            lastEndOfDayReport = snapshot,
            playerPausedTime = true,
            pausedByEndOfDay = !wasAlreadyPaused,
        )
    }

    /** Called by the ViewModel when the player dismisses the end-of-day report dialog. */
    fun dismissEndOfDayReport() {
        state = state.copy(
            showEndOfDayReport = false,
            // Only resume time if the engine auto-paused it; respect the player's own pause.
            playerPausedTime = if (state.pausedByEndOfDay) false else state.playerPausedTime,
            pausedByEndOfDay = false,
        )
    }

    // stored outside state; just internal accumulator
    private var autoClickProgress: Float = 0f
    private var stockerProgress: Float = 0f
    // Tracks the last day number we processed so we can detect midnight rollovers
    private var lastKnownDayNumber: Int = 0

    /**
     * Simulate the remainder of the current game day in a tight synchronous loop,
     * then surface the end-of-day report exactly as midnight would normally do.
     *
     * All hired staff (cashiers, stockers) and autonomous customer traffic operate
     * at their normal rates throughout the simulation.  The player role is suspended
     * for the duration so the result is deterministic regardless of which role the
     * player had selected.
     *
     * After this call, [GameState.showEndOfDayReport] is `true` and the ViewModel
     * will pick up the new state and display the report dialog.
     *
     * Safe to call on the main thread — pure computation, no I/O.
     */
    fun simulateRestOfDay() {
        // Nothing to do if the report is already waiting for the player to dismiss
        if (state.showEndOfDayReport) return

        val currentDayNumber = state.currentTime.dayNumber
        val nextMidnightMinutes = (currentDayNumber + 1).toLong() * 1440L

        // Temporarily unpause and clear the player role for a clean simulation.
        // rollOverDay() will set playerPausedTime=true and pausedByEndOfDay=true,
        // so dismissEndOfDayReport() will correctly resume time afterwards.
       state = state.copy(
            playerPausedTime = false,
            playerCashierProgress = 0f,
            playerStockerProgress = 0f,
        )

        // Using tick(500L) advances exactly 1 game-minute at 1× speed
        // (TimeManager: 500 × 120 × 1.0 = 60 000 accumulated ms → 1 game-minute).
        // At higher speed settings the TimeManager's multiplier causes proportionally
        // more minutes per call, but the loop condition on totalMinutesElapsed ensures
        // we stop at the correct midnight boundary regardless.
        val simulationDeltaMs = 500L

        while (
            state.currentTime.totalMinutesElapsed < nextMidnightMinutes &&
            !state.showEndOfDayReport
        ) {
            tick(simulationDeltaMs)
        }

    }

    /**
     * Purchase the next [ItemUnlockTier] with the player's cash balance.
     *
     * Guards:
     * - Does nothing if already at the top tier.
     * - Does nothing if [GameState.totalRevenue] has not yet met the next tier's revenue gate.
     * - Does nothing if [GameState.money] is less than the tier's [ItemUnlockTier.unlockCost].
     *
     * On success: deducts [ItemUnlockTier.unlockCost] from [GameState.money],
     * advances [GameState.currentTier], and emits [GameStateChange.TierUnlocked].
     */
    fun unlockNextTier() {
        val nextTier = ItemUnlockTier.nextTier(state.currentTier) ?: return
        if (state.totalRevenue.cents < nextTier.unlockAmount) return
        if (state.money < nextTier.unlockCost) return

        val previousTier = state.currentTier
        state = state.copy(
            currentTier = nextTier,
            money = state.money - nextTier.unlockCost,
        )
        _changes.value = GameStateChange.TierUnlocked(nextTier, previousTier)
    }
}
