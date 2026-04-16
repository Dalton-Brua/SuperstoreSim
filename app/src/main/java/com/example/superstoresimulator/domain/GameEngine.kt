package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.progression.ProgressionManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.traffic.TrafficManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameEngine(private val itemMetadataCache: ItemMetadataCache) {
    private val txEngine = TransactionEngine(itemMetadataCache = itemMetadataCache)
    private val timeManager = TimeManager()
    private val trafficManager = TrafficManager()  // Phase 2: autonomous customer generation
    private val progressionManager = ProgressionManager()
    private val storeController = StoreController()
    private val dayManager = DayManager()
    private val staffManager = StaffManager()
    private val playerActionHandler = PlayerActionHandler()
    private val inventoryManager = InventoryManager(itemMetadataCache)

    // Emit incremental changes instead of full state reconstructions
    private val _changes = MutableStateFlow<GameStateChange?>(null)
    val changes: StateFlow<GameStateChange?> = _changes.asStateFlow()

    var state = GameState(
        inventory = mutableMapOf(),
        currentTime = timeManager.currentTime,
        storeState = timeManager.getStoreState(),
        storeConfig = StoreConfig(
            backroomCapPerItem = com.example.superstoresimulator.domain.store.StoreSize.MOM_AND_POP.backroomCapPerItem
        )
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

    // Inventory operations — delegated to InventoryManager; change emission stays here.

    fun stockItemFromBackroom(itemId: Int) {
        val before = state.inventory[itemId]
        state = inventoryManager.stockItemFromBackroom(state, itemId)
        val after = state.inventory[itemId]
        if (after != null && after != before) {
            _changes.value = GameStateChange.InventoryUpdated(itemId, after)
        }
    }

    private fun stockRandomItemFromBackroom() {
        state = inventoryManager.stockRandomItemFromBackroom(state)
    }

    fun buyItemToBackroom(itemId: Int) {
        val moneyBefore = state.money
        val invBefore = state.inventory[itemId]
        state = inventoryManager.buyItemToBackroom(state, itemId)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
        val invAfter = state.inventory[itemId]
        if (invAfter != null && invAfter != invBefore) {
            _changes.value = GameStateChange.InventoryUpdated(itemId, invAfter)
        }
    }

    fun buyItemCasePacks(itemId: Int, numCasePacks: Int) {
        state = inventoryManager.buyItemCasePacks(state, itemId, numCasePacks)
    }

    fun placeBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) {
        val moneyBefore = state.money
        state = inventoryManager.placeBulkOrder(state, maxTotalQuantity, casePacksPerItem, categoryFilter)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
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
        state = staffManager.hireEntity(state, def, type)
    }
    fun upgradeEntity(entityId: Int) {
        state = staffManager.upgradeEntity(state, entityId)
    }
    fun fireEntity(entityId: Int) {
        state = staffManager.fireEntity(state, entityId)
    }
    fun updateStoreName(newName: String) {
        state = storeController.updateStoreName(state, newName)
    }
    fun upgradeStoreSize() {
        val moneyBefore = state.money
        state = storeController.upgradeStoreSize(state)
        if (state.money != moneyBefore) {
            _changes.value = GameStateChange.MoneyChanged(state.money)
        }
    }

    // Game tick (mostly staff actions)
    fun tick(deltaMilliseconds: Long) {
        // Phase 1: Only update time if player has not paused the game
        if (!state.playerPausedTime) {
            timeManager.update(deltaMilliseconds)
            state = state.copy(currentTime = timeManager.currentTime)

            // Phase 3: Detect day rollover (midnight)
            val newDayNumber = state.currentTime.dayNumber
            if (newDayNumber != dayManager.lastKnownDayNumber) {
                state = dayManager.rollOverDay(state, dayManager.lastKnownDayNumber)
                dayManager.advanceDay(newDayNumber)
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
                val wholeItems = staffManager.advanceCashierProgress(cashiers, delta, multiplier)
                repeat(wholeItems) { ringUpItem() }
            }

            val stockers = state.hiredEntityRegistry.countByEntity(EntityDef.STOCKER)
            val wholeStockActions = staffManager.advanceStockerProgress(stockers, delta, multiplier)
            repeat(wholeStockActions) { stockRandomItemFromBackroom() }

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
     * Player working as cashier: rings up items in the active transaction.
     * Progress math delegated to [PlayerActionHandler.calculateCashierWork];
     * ring-ups are performed here because they mutate live state between iterations.
     */
    private fun performPlayerCashierWork(deltaSeconds: Double) {
        val result = playerActionHandler.calculateCashierWork(state, deltaSeconds)
        var actionsLeft = result.actionsToTake
        while (actionsLeft > 0 && state.transactionActive) {
            ringUpItem()
            actionsLeft--
        }
        // Discard leftover progress if the transaction ended during this work cycle.
        val finalProgress = if (state.transactionActive) result.newProgress else 0f
        state = state.copy(playerCashierProgress = finalProgress)
    }

    /**
     * Player working as stocker: moves case-packs from backroom to shelves.
     * Progress math delegated to [PlayerActionHandler.calculateStockerWork];
     * stocking calls are performed here because they mutate live state between iterations.
     * Auto-returns the player to [PlayerRole.NONE] when the backroom empties.
     */
    private fun performPlayerStockerWork(deltaSeconds: Double) {
        val result = playerActionHandler.calculateStockerWork(state, deltaSeconds)
        repeat(result.actionsToTake) { stockRandomItemFromBackroom() }
        val backroomEmpty = state.inventory.values.none { it.backroomStock > 0 }
        if (backroomEmpty) {
            state = state.copy(playerRole = PlayerRole.NONE, playerStockerProgress = 0f)
        } else {
            state = state.copy(playerStockerProgress = result.newProgress)
        }
    }
    
    /**
     * Handle transitions between store states.
     * Delegates to [StoreController.handleStoreStateChange].
     */
    private fun handleStoreStateChange(newState: StoreState) {
        state = storeController.handleStoreStateChange(state, newState, trafficManager)
    }

    /**
     * Set game speed multiplier (for speed controls 1x, 2x, 4x, etc.)
     * @param multiplier The multiplier value (1.0f for 1x, 2.0f for 2x, 4.0f for 4x, etc.)
     */
    fun setGameSpeed(multiplier: Float) {
        timeManager.setSpeedMultiplier(multiplier)
        state = storeController.setGameSpeedState(state, multiplier)
    }

    /**
     * Toggle the store between open and closed.
     * Delegates to [StoreController.toggleTimePaused].
     */
    fun toggleTimePaused() {
        state = storeController.toggleTimePaused(state)
    }

    /**
     * Set the player's active work role.
     * Passing the currently active role toggles it OFF (back to NONE).
     * Delegates to [PlayerActionHandler.setPlayerRole].
     */
    fun setPlayerRole(role: PlayerRole) {
        state = playerActionHandler.setPlayerRole(state, role)
    }


    /** Called by the ViewModel when the player dismisses the end-of-day report dialog. */
    fun dismissEndOfDayReport() {
        state = dayManager.dismissEndOfDayReport(state)
    }


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
     * Delegates guard-checking and state mutation to [ProgressionManager.unlockNextTier].
     * On success, emits [GameStateChange.TierUnlocked] via [_changes].
     */
    fun unlockNextTier() {
        val previousTier = state.currentTier
        state = progressionManager.unlockNextTier(state)
        if (state.currentTier != previousTier) {
            _changes.value = GameStateChange.TierUnlocked(state.currentTier, previousTier)
        }
    }
}
