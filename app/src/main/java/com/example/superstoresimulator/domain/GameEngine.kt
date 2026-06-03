package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.expiration.SpoilageManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.progression.ProgressionManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.traffic.TrafficManager
import com.example.superstoresimulator.domain.traffic.TrafficSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GameEngine(private val itemMetadataCache: ItemMetadataCache) {
    val pricingManager = PricingManager(itemMetadataCache)
    private val txEngine = TransactionEngine(cache = itemMetadataCache, pricingManager = pricingManager)

    private companion object {
        const val XP_PER_TRANSACTION = 5
        const val XP_PER_STOCK_ACTION = 1
        const val MANAGER_XP_PER_SUPERVISED_ACTION = 1
    }
    private val timeManager = TimeManager()
    private val trafficManager = TrafficManager()
    private val progressionManager = ProgressionManager()
    private val storeController = StoreController()
    private val dayManager = DayManager()
    private val staffManager = StaffManager()
    private val playerActionHandler = PlayerActionHandler()
    private val inventoryManager = InventoryManager(itemMetadataCache)
    private val spoilageManager = SpoilageManager(itemMetadataCache)
    private val truckManager = TruckManager(itemMetadataCache)
    private var lastPriceIndexHour = -1

    // Emit incremental changes instead of full state reconstructions
    private val _changes = MutableStateFlow<GameStateChange?>(null)
    val changes: StateFlow<GameStateChange?> = _changes.asStateFlow()

    var state = GameState(
        inventory = mutableMapOf(),
        currentTime = timeManager.currentTime,
        storeState = timeManager.getStoreState(),
        storeConfig = StoreConfig(
            backroomCapPerItem = StoreSize.MOM_AND_POP.backroomCapPerItem
        ),
        // Keep default engine startup deterministic for unit tests.
        money = Money.ZERO,
        // Start at TIER_1 (locked progression)
        currentTier = ItemUnlockTier.TIER_1,
        // No revenue earned yet
        totalRevenue = Money.ZERO,
    )
        internal set

    init {
        // Seed every item with baseline stock for deterministic tests/sim startup.
        val allDbItems = itemMetadataCache.getAllItems()
        if (allDbItems.isNotEmpty()) {
            val startingTier = ItemUnlockTier.TIER_2
            val inventory = mutableMapOf<Int, InventoryState>()
            val currentDay = state.currentTime.dayNumber
            allDbItems.forEach { (itemId, item) ->
                val metadata = itemMetadataCache.get(itemId)
                val itemTier = metadata?.tier ?: ItemUnlockTier.TIER_1
                if (itemTier.unlockAmount > startingTier.unlockAmount) {
                    return@forEach
                }
                val expirationDay = if (item.shelfLifeDays != null) {
                    currentDay + item.shelfLifeDays
                } else {
                    Int.MAX_VALUE
                }
                val startingBatch = ItemBatch(
                    receivedDay = currentDay,
                    quantity = 10,
                    expirationDay = expirationDay,
                )
                inventory[itemId] = InventoryState(
                    shelfBatches = listOf(startingBatch),
                    backroomBatches = listOf(startingBatch),
                )
            }
            state = state.copy(
                inventory = inventory,
                money = Money(5_000_000),
                currentTier = startingTier,
                currentStoreSize = StoreSize.SMALL_GROCERY,
                storeConfig = StoreConfig(backroomCapPerItem = StoreSize.SMALL_GROCERY.backroomCapPerItem))
        }
    }
    
    fun getDbItem(itemId: Int): Item? {
        return itemMetadataCache.getItem(itemId)
    }

    fun currentState(): GameState = state

    fun employeeActivities(): Map<Int, com.example.superstoresimulator.domain.staff.EmployeeActivity> =
        staffManager.employeeActivities

    fun cashierUtilization(): Float = staffManager.currentCashierUtilization()
    fun stockerUtilization(): Float = staffManager.currentStockerUtilization()
    fun freshUtilization(): Float = staffManager.currentFreshUtilization()

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

    private fun stockRandomFreshItemFromBackroom() {
        state = inventoryManager.stockRandomFreshItemFromBackroom(state)
    }

    fun buyItemToBackroom(itemId: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.buyItemToBackroom(state, itemId)
        state = result.state
        if (result.orderLines.isNotEmpty()) {
            val currentDay = state.currentTime.dayNumber
            val fresh = result.orderLines.filter { it.isFresh }
            val regular = result.orderLines.filter { !it.isFresh }
            if (regular.isNotEmpty()) state = truckManager.scheduleRegularOrderLines(state, regular, currentDay)
            if (fresh.isNotEmpty()) state = truckManager.scheduleFreshOrderLines(state, fresh, currentDay)
            val arrivalDay = state.scheduledTrucks
                .filter { t -> result.orderLines.any { l -> t.orders.any { o -> o.itemId == l.itemId } } }
                .minOfOrNull { it.scheduledArrivalDay } ?: (currentDay + 1)
            _changes.value = GameStateChange.OrderScheduled(arrivalDay)
        }
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    fun buyItemCasePacks(itemId: Int, numCasePacks: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.buyItemCasePacks(state, itemId, numCasePacks)
        state = result.state
        if (result.orderLines.isNotEmpty()) {
            val currentDay = state.currentTime.dayNumber
            val fresh = result.orderLines.filter { it.isFresh }
            val regular = result.orderLines.filter { !it.isFresh }
            if (regular.isNotEmpty()) state = truckManager.scheduleRegularOrderLines(state, regular, currentDay)
            if (fresh.isNotEmpty()) state = truckManager.scheduleFreshOrderLines(state, fresh, currentDay)
            val arrivalDay = state.scheduledTrucks
                .filter { t -> result.orderLines.any { l -> t.orders.any { o -> o.itemId == l.itemId } } }
                .minOfOrNull { it.scheduledArrivalDay } ?: (currentDay + 1)
            _changes.value = GameStateChange.OrderScheduled(arrivalDay)
        }
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    fun placeBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) {
        val moneyBefore = state.money
        val result = inventoryManager.placeBulkOrder(state, maxTotalQuantity, casePacksPerItem, categoryFilter)
        state = result.state
        if (result.orderLines.isNotEmpty()) {
            val currentDay = state.currentTime.dayNumber
            state = truckManager.scheduleRegularOrderLines(state, result.orderLines, currentDay)
            val arrivalDay = state.scheduledTrucks
                .filter { !it.isFreshTruck }.minOfOrNull { it.scheduledArrivalDay } ?: (currentDay + 1)
            _changes.value = GameStateChange.OrderScheduled(arrivalDay)
        }
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    // Transaction operations
    fun startTransaction() {
        // Only start a new transaction if one is not already in progress on the default register.
        // Previously had a second unconditional `if (storeState != CLOSED)` call here
        // which caused startNewTransaction to fire twice, skipping every other ID.
        val defaultRegisterId = state.registers.firstOrNull()?.registerId ?: 0
        val register = state.registers.findRegisterById(defaultRegisterId)
        if (register != null && !register.transactionActive && state.storeState != StoreState.CLOSED) {
            state = txEngine.startNewTransaction(state, defaultRegisterId)
        }
    }

    /**
     * Ring up a specific item on a specific register, tracking daily metrics.
     * This is the authoritative ring-up path — all other ring-up helpers delegate here.
     */
    private fun ringUpItemAndTrackMetrics(itemId: Int, registerId: Int) {
        val prevCompleted = state.totalTransactionsCompleted
        state = txEngine.ringUpSingleItem(state, itemId, registerId)
        // Clear expiry markdowns if no more expiring batches remain on shelf
        if (state.pricingState.activeMarkdowns.containsKey(itemId)) {
            val markdown = state.pricingState.activeMarkdowns[itemId]
            if (markdown?.reason == com.example.superstoresimulator.domain.pricing.MarkdownReason.EXPIRING_SOON) {
                val inv = state.inventory[itemId]
                val currentDay = state.currentTime.dayNumber
                val hasExpiring = inv?.shelfBatches?.any { batch ->
                    batch.expirationDay - currentDay <= PricingManager.EXPIRY_THRESHOLD_DAYS
                } ?: false
                if (!hasExpiring) {
                    state = pricingManager.clearMarkdown(state, itemId)
                }
            }
        }
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
                        effectivePrice = l.unitPrice,
                        basePrice = l.basePrice,
                    )
                }

            // Pricing metrics: markup extra revenue and markdown savings
            var txMarkupExtra = Money.ZERO
            var txMarkdownSaved = Money.ZERO
            for (line in tx.lines) {
                if (line.lostToOutOfStock || line.quantity <= 0) continue
                val diff = line.unitPrice.cents - line.basePrice.cents
                if (diff > 0) {
                    txMarkupExtra += Money(diff * line.quantity)
                }
                if (state.pricingState.activeMarkdowns.containsKey(line.itemId)) {
                    txMarkdownSaved += line.lineTotal
                }
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
                    markupExtraRevenue = acc.markupExtraRevenue + txMarkupExtra,
                    markdownsSaved = acc.markdownsSaved + txMarkdownSaved,
                )
            )

            // Grant XP to the hired cashier assigned to this register (not the player).
            val reg = state.registers.findRegisterById(tx.registerId)
            val cashierId = reg?.assignedCashierId
            if (cashierId != null) {
                state = state.copy(
                    hiredEntityRegistry = state.hiredEntityRegistry.grantXp(cashierId, XP_PER_TRANSACTION)
                )
            }
        }
    }

    /**
     * Ring up a random un-rung line from the active transaction on [registerId].
     * Used by AI cashiers during the tick.
     */
    private fun ringUpItemOnRegister(registerId: Int) {
        val register = state.registers.findRegisterById(registerId) ?: return
        val lines = register.currentTransaction.lines
        val ringable = lines.filter { it.rungQty < it.quantity && !it.lostToOutOfStock }
        if (ringable.isEmpty()) return
        val randomLine = ringable.random()
        ringUpItemAndTrackMetrics(randomLine.itemId, registerId)
    }

    /**
     * Public: ring up [itemId] on the player's assigned register (or register 0 as fallback).
     * Called from [GameViewModel] when the player manually taps an item.
     */
    fun ringUpItem(itemId: Int) {
        val registerId = state.playerAssignedRegisterId
            ?: state.registers.firstOrNull()?.registerId
            ?: return
        ringUpItemAndTrackMetrics(itemId, registerId)
    }

    /**
     * Public: ring up a random item on the player's active register.
     * Kept for compatibility with event-based ring-up (GameEvent.RingUp).
     */
    fun ringUpItem() {
        val registerId = state.playerAssignedRegisterId
            ?: state.registers.firstOrNull()?.registerId
            ?: return
        ringUpItemOnRegister(registerId)
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
    fun hireEntity(def: EntityDef) {
        state = staffManager.hireEntity(state, def)
        if (def == EntityDef.CASHIER) {
            var updatedRegisters = state.registers
            val unassignedCashiers = state.hiredEntityRegistry.hiredEntities.filter { entity ->
                entity.entityDefinition == EntityDef.CASHIER &&
                    entity.id !in state.manuallyUnassignedCashiers &&
                    updatedRegisters.none { reg -> reg.assignedCashierId == entity.id }
            }
            for (cashier in unassignedCashiers) {
                val freeRegister = updatedRegisters.firstOrNull { it.assignedCashierId == null }
                if (freeRegister != null) {
                    updatedRegisters = updatedRegisters.updateRegister(
                        freeRegister.copy(assignedCashierId = cashier.id)
                    )
                }
            }
            if (updatedRegisters != state.registers) {
                state = state.copy(registers = updatedRegisters)
            }
        }
    }
    fun promoteEntity(entityId: Int) {
        state = staffManager.promoteEntity(state, entityId)
    }
    fun fireEntity(entityId: Int) {
        state = staffManager.fireEntity(state, entityId)
        // Unassign the employee from any register they were covering.
        val updatedRegisters = state.registers.map { register ->
            if (register.assignedCashierId == entityId) register.copy(assignedCashierId = null)
            else register
        }
        if (updatedRegisters != state.registers) {
            state = state.copy(registers = updatedRegisters)
        }
    }

    /**
     * Update the shift start hour for employee [entityId].
     * Delegates to [StaffManager.updateShift]; invalid hours are silently dropped there.
     */
    fun updateShift(entityId: Int, newStartHour: Int, newDuration: Int = 8) {
        state = staffManager.updateShift(state, entityId, newStartHour, newDuration)
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

            // Phase 1.5: Process item expiration (remove expired batches)
            state = spoilageManager.processExpiration(state)
            // Clear expiry markdowns for items whose expiring batches just expired
            for (itemId in state.pricingState.activeMarkdowns.keys.toList()) {
                val md = state.pricingState.activeMarkdowns[itemId] ?: continue
                if (md.reason != com.example.superstoresimulator.domain.pricing.MarkdownReason.EXPIRING_SOON) continue
                val inv = state.inventory[itemId]
                val currentDay = state.currentTime.dayNumber
                val hasExpiring = inv?.shelfBatches?.any { batch ->
                    batch.expirationDay - currentDay <= PricingManager.EXPIRY_THRESHOLD_DAYS
                } ?: false
                if (!hasExpiring) {
                    state = pricingManager.clearMarkdown(state, itemId)
                }
            }

            // Phase 3: Detect day rollover (midnight)
            val newDayNumber = state.currentTime.dayNumber
            if (newDayNumber != dayManager.lastKnownDayNumber) {
                // Store Manager auto-actions (registers, shift rebalancing) before hiring
                state = staffManager.evaluateStoreManagerActions(state)
                // Auto-hire evaluation before day reset (uses today's accumulated metrics)
                state = staffManager.evaluateAutoHire(state)
                state = dayManager.rollOverDay(state, dayManager.lastKnownDayNumber)
                dayManager.advanceDay(newDayNumber)
                // Clear any transactions left open at midnight (e.g. from skip-day) and
                // reset cashier progress accumulators so stale fractional work doesn't
                // carry into the new day.
                state = state.copy(
                    registers = state.registers.map { it.copy(
                        currentTransaction = Transaction(),
                        transactionActive = false,
                    )},
                    manuallyUnassignedCashiers = emptySet(),
                )
                staffManager.reset()
                // Process truck arrivals at the start of each new day
                state = truckManager.processArrivals(state, newDayNumber)
            }

            // Phase 1: Update store state based on time
            val newStoreState = timeManager.getStoreState()
            if (newStoreState != state.storeState) {
                handleStoreStateChange(newStoreState)
            }
            state = state.copy(storeState = newStoreState)

            // Hourly price index EMA update
            val currentHourForPricing = state.currentTime.hour
            if (currentHourForPricing != lastPriceIndexHour) {
                lastPriceIndexHour = currentHourForPricing
                state = pricingManager.updateSmoothedPriceIndex(state)
            }

            val delta = deltaMilliseconds / 1000.0
            val speedMultiplier = state.storeConfig.gameSpeedMultiplier
            val currentHour = state.currentTime.hour

            // ── Global + department manager bonuses (Phase 5) ─────────────────
            val globalBonus = StaffManager.computeGlobalBonus(
                state.playerRole, currentHour, state.staffSchedules, state.hiredEntityRegistry
            )
            val cashierBonus = globalBonus * StaffManager.deptManagerBonus(
                EntityDef.CASHIER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            )
            val stockerBonus = globalBonus * StaffManager.deptManagerBonus(
                EntityDef.STOCKER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            )
            val freshBonus = globalBonus * StaffManager.deptManagerBonus(
                EntityDef.FRESH_HANDLER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            )

            // Unassign cashiers whose shift has ended, unless they have an active transaction.
             val registersAfterShiftCheck = state.registers.map { reg ->
                 val assignedId = reg.assignedCashierId ?: return@map reg
                 if (reg.transactionActive) return@map reg
                 val shift = state.staffSchedules.firstOrNull { it.entityId == assignedId }
                 if (shift != null && !shift.isOnShift(currentHour)) reg.copy(assignedCashierId = null)
                 else reg
             }
             if (registersAfterShiftCheck != state.registers) {
                 state = state.copy(registers = registersAfterShiftCheck)
             }

             // Reassign unassigned on-shift cashiers to free registers
             // (skip cashiers the player manually unassigned today)
             var registersAfterReassignment = state.registers
             val unassignedOnShiftCashiers = state.hiredEntityRegistry.hiredEntities.filter { cashier ->
                 cashier.entityDefinition == EntityDef.CASHIER &&
                 cashier.id !in state.manuallyUnassignedCashiers &&
                 registersAfterReassignment.none { reg -> reg.assignedCashierId == cashier.id } &&
                 state.staffSchedules.firstOrNull { it.entityId == cashier.id }?.isOnShift(currentHour) != false
             }
             for (cashier in unassignedOnShiftCashiers) {
                 val freeRegister = registersAfterReassignment.firstOrNull { it.assignedCashierId == null }
                 if (freeRegister != null) {
                     registersAfterReassignment = registersAfterReassignment.updateRegister(
                         freeRegister.copy(assignedCashierId = cashier.id)
                     )
                 }
             }
             if (registersAfterReassignment != state.registers) {
                 state = state.copy(registers = registersAfterReassignment)
             }

            // Phase 2: Traffic arrivals — add to the waiting queue (don't start directly)
            if (state.storeState == StoreState.OPEN) {
                val newCustomers = trafficManager.update(state, delta)
                if (newCustomers.isNotEmpty()) {
                    state = state.copy(pendingCustomers = state.pendingCustomers + newCustomers.size)
                }
            }

            // Phase 3: Dequeue waiting customers to free, staffed registers.
            // Iterate over all registers; each staffed idle register absorbs one customer.
            if (state.storeState == StoreState.OPEN && state.pendingCustomers > 0) {
                val registerIds = state.registers.map { it.registerId }
                for (registerId in registerIds) {
                    if (state.pendingCustomers <= 0) break
                    val reg = state.registers.findRegisterById(registerId) ?: continue
                    if (reg.transactionActive) continue
                    if (!isRegisterMannedAndOnShift(registerId, state, currentHour)) continue
                    val basketSize = (2..5).random()
                    state = txEngine.generateRandomTransaction(state, basketSize, registerId)
                    if (state.registers.findRegisterById(registerId)?.transactionActive == true) {
                        state = state.copy(pendingCustomers = (state.pendingCustomers - 1).coerceAtLeast(0))
                    }
                }
            }

            // Phase 3: Process hired cashiers per register.
            // Cashiers finish active transactions even during CLOSING/CLOSED.
            var totalEmployeeActions = 0
            if (state.registers.any { it.transactionActive }) {
                val registerIds = state.registers.map { it.registerId }
                for (registerId in registerIds) {
                    val reg = state.registers.findRegisterById(registerId) ?: continue
                    if (!reg.transactionActive) continue
                    val cashierWeight = getCashierWeightForRegister(registerId, state, currentHour)
                    if (cashierWeight <= 0f) continue
                    val actions = staffManager.advanceCashierProgressForRegister(
                        registerId, cashierWeight, delta, speedMultiplier * cashierBonus
                    )
                    repeat(actions) { ringUpItemOnRegister(registerId) }
                    totalEmployeeActions += actions
                }
            }

            // ── Zone decay (Phase 5B) ────────────────────────────────────────
            if (state.storeState == StoreState.OPEN) {
                val pattern = TrafficSchedule.getPatternForTime(state.currentTime)
                val trafficRate = (pattern.baseCustomerRate / 60f) * state.currentStoreSize.trafficMultiplier
                val decayAmount = StaffManager.ZONE_DECAY_RATE * trafficRate * delta.toFloat() * speedMultiplier
                if (decayAmount > 0.0001f) {
                    var changed = false
                    val updatedInventory = HashMap(state.inventory)
                    for ((itemId, inv) in state.inventory) {
                        if (inv.shelfStock > 0 && inv.zoneScore > 0f) {
                            val newScore = (inv.zoneScore - decayAmount).coerceAtLeast(0f)
                            if (newScore != inv.zoneScore) {
                                updatedInventory[itemId] = inv.copy(zoneScore = newScore)
                                changed = true
                            }
                        }
                    }
                    if (changed) state = state.copy(inventory = updatedInventory)
                }
            }

            // ── Stocker work: stocking or zoning ─────────────────────────────
            val hasActionableBackroom = state.inventory.any { (itemId, inv) ->
                val meta = itemMetadataCache.get(itemId)
                meta?.isPerishable != true && inv.backroomStock > 0
            }
            val hasUnzonedItems = state.inventory.any { (_, inv) ->
                inv.shelfStock > 0 && inv.zoneScore < 1.0f
            }

            val activeStockers = StaffManager.activeWeightedCount(
                EntityDef.STOCKER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            )

            if (hasActionableBackroom) {
                val wholeStockActions = staffManager.advanceStockerProgress(activeStockers, delta, speedMultiplier * stockerBonus)
                repeat(wholeStockActions) { stockRandomItemFromBackroom() }
                if (wholeStockActions > 0) {
                    val onShiftStockerIds = state.hiredEntityRegistry.getByDef(EntityDef.STOCKER)
                        .filter { e -> state.staffSchedules.any { s -> s.entityId == e.id && s.isOnShift(currentHour) } }
                        .map { it.id }
                    state = state.copy(
                        hiredEntityRegistry = state.hiredEntityRegistry.grantXpDistributed(onShiftStockerIds, wholeStockActions * XP_PER_STOCK_ACTION)
                    )
                    totalEmployeeActions += wholeStockActions
                }
            } else if (hasUnzonedItems && activeStockers > 0f) {
                advanceStockerZoning(currentHour, delta, speedMultiplier * stockerBonus)
            }

            // Process fresh handlers — weighted, on-shift only.
            val hasFreshBackroomStock = state.inventory.any { (itemId, inv) ->
                itemMetadataCache.get(itemId)?.isPerishable == true && inv.backroomStock > 0
            }
            val activeFreshHandlers = StaffManager.activeWeightedCount(
                EntityDef.FRESH_HANDLER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            )
            val wholeFreshActions = staffManager.advanceFreshHandlerProgress(activeFreshHandlers, delta, speedMultiplier * freshBonus)

            // Markdown expiring items first (higher priority than stocking)
            var remainingFreshActions = wholeFreshActions
            if (remainingFreshActions > 0) {
                val currentDay = state.currentTime.dayNumber
                for ((itemId, inv) in state.inventory) {
                    if (remainingFreshActions <= 0) break
                    val meta = itemMetadataCache.get(itemId) ?: continue
                    if (!meta.isPerishable) continue
                    if (state.pricingState.activeMarkdowns.containsKey(itemId)) continue
                    val hasExpiringBatch = inv.shelfBatches.any { batch ->
                        batch.expirationDay - currentDay <= PricingManager.EXPIRY_THRESHOLD_DAYS
                    }
                    if (hasExpiringBatch) {
                        state = pricingManager.applyExpiryMarkdown(state, itemId, currentDay)
                        remainingFreshActions--
                    }
                }
            }

            // Then stock with remaining actions
            repeat(remainingFreshActions) { stockRandomFreshItemFromBackroom() }
            if (wholeFreshActions > 0) {
                val onShiftFreshIds = state.hiredEntityRegistry.getByDef(EntityDef.FRESH_HANDLER)
                    .filter { e -> state.staffSchedules.any { s -> s.entityId == e.id && s.isOnShift(currentHour) } }
                    .map { it.id }
                state = state.copy(
                    hiredEntityRegistry = state.hiredEntityRegistry.grantXpDistributed(onShiftFreshIds, wholeFreshActions * XP_PER_STOCK_ACTION)
                )
                totalEmployeeActions += wholeFreshActions
            }

            // Grant XP to on-shift managers for supervising employee work
            if (totalEmployeeActions > 0) {
                val managerXp = totalEmployeeActions * MANAGER_XP_PER_SUPERVISED_ACTION
                val onShiftManagers = state.hiredEntityRegistry.getByDef(EntityDef.MANAGER).filter { mgr ->
                    state.staffSchedules.any { s -> s.entityId == mgr.id && s.isOnShift(currentHour) }
                }
                if (onShiftManagers.isNotEmpty()) {
                    val xpEach = (managerXp / onShiftManagers.size).coerceAtLeast(1)
                    var registry = state.hiredEntityRegistry
                    for (mgr in onShiftManagers) {
                        registry = registry.grantXp(mgr.id, xpEach)
                    }
                    state = state.copy(hiredEntityRegistry = registry)
                }
            }

            // If fresh handlers are active but have no fresh items in the backroom to stock,
            // attempt auto-ordering regardless of how many actions were accumulated.
            if (activeFreshHandlers > 0f && !hasFreshBackroomStock) {
                attemptFreshHandlerAutoOrder()
            }

            // ── Utilization tracking (Phase 5B) ──────────────────────────────
            if (state.hiredEntityRegistry.hiredEntities.isNotEmpty()) {
                val hasFreshWork = hasFreshBackroomStock ||
                    (state.freshAutoOrderConfig.enabled && state.inventory.any { (itemId, _) ->
                        inventoryManager.shouldAutoOrderFreshItem(state, itemId, state.freshAutoOrderConfig)
                    })
                staffManager.updateUtilization(
                    state, currentHour, hasActionableBackroom, hasUnzonedItems, hasFreshWork
                )
            }

            // Phase 2: Process player work (mutually exclusive roles)
            // If player is a cashier but has no register yet, try to claim a free one.
            if (state.playerRole == PlayerRole.CASHIER && state.playerAssignedRegisterId == null) {
                val freeReg = state.registers.firstOrNull { it.assignedCashierId == null }
                if (freeReg != null) {
                    state = state.copy(playerAssignedRegisterId = freeReg.registerId)
                }
            }
            when (state.playerRole) {
                PlayerRole.CASHIER -> performPlayerCashierWork(delta)
                PlayerRole.STOCKER -> performPlayerStockerWork(delta)
                PlayerRole.MANAGE -> {
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
     * Player working as cashier: rings up items on the player's assigned register.
     *
     * If no register is explicitly assigned ([GameState.playerAssignedRegisterId] is null),
     * falls back to the first register for backward compatibility with single-register games.
     *
     * Guard: if the player is explicitly assigned to a register that already has a hired
     * cashier, no player work is performed on that register (the cashier covers it).
     */
    private fun performPlayerCashierWork(deltaSeconds: Double) {
        val registerId = state.playerAssignedRegisterId ?: return

        val register = state.registers.findRegisterById(registerId) ?: return

        // If the player explicitly picked this register but a cashier is already on it, skip.
        if (state.playerAssignedRegisterId != null && register.assignedCashierId != null) {
            state = state.copy(playerCashierProgress = 0f)
            return
        }

        val result = playerActionHandler.calculateCashierWork(state, deltaSeconds)
        var actionsLeft = result.actionsToTake
        while (actionsLeft > 0) {
            val currentRegister = state.registers.findRegisterById(registerId) ?: break
            if (!currentRegister.transactionActive) break
            ringUpItemOnRegister(registerId)
            actionsLeft--
        }
        // Discard leftover progress if the transaction ended during this work cycle.
        val finalProgress =
            if (state.registers.findRegisterById(registerId)?.transactionActive == true) result.newProgress
            else 0f
        state = state.copy(playerCashierProgress = finalProgress)
    }

    /**
     * Player working as stocker: moves case-packs from backroom to shelves.
     * Progress math delegated to [PlayerActionHandler.calculateStockerWork];
     * stocking calls are performed here because they mutate live state between iterations.
     * When the backroom empties, zones unzoned shelf items instead.
     * Auto-returns the player to [PlayerRole.MANAGE] when nothing left to stock or zone.
     */
    private fun performPlayerStockerWork(deltaSeconds: Double) {
        val result = playerActionHandler.calculateStockerWork(state, deltaSeconds)
        val hasActionableBackroom = state.inventory.values.any { it.backroomStock > 0 }
        if (hasActionableBackroom) {
            repeat(result.actionsToTake) { stockRandomItemFromBackroom() }
            state = state.copy(playerStockerProgress = result.newProgress)
        } else {
            val unzonedItems = state.inventory.entries
                .filter { (_, inv) -> inv.shelfStock > 0 && inv.zoneScore < 1.0f }
                .sortedBy { (_, inv) -> inv.zoneScore }
            if (unzonedItems.isEmpty()) {
                state = state.copy(playerRole = PlayerRole.MANAGE, playerStockerProgress = 0f)
                return
            }
            var actionsLeft = result.actionsToTake
            var idx = 0
            while (actionsLeft > 0 && idx < unzonedItems.size) {
                val (itemId, inv) = unzonedItems[idx]
                val newScore = (inv.zoneScore + StaffManager.ZONE_PER_ACTION).coerceAtMost(1.0f)
                state = state.copy(
                    inventory = state.inventory + (itemId to inv.copy(zoneScore = newScore))
                )
                actionsLeft--
                if (newScore >= 1.0f) idx++
            }
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

    // ── Zoning helpers (Phase 5B) ───────────────────────────────────────────

    private fun advanceStockerZoning(currentHour: Int, delta: Double, multiplier: Float) {
        val onShiftStockers = state.hiredEntityRegistry.getByDef(EntityDef.STOCKER).filter { entity ->
            state.staffSchedules.firstOrNull { it.entityId == entity.id }?.isOnShift(currentHour) == true
        }
        if (onShiftStockers.isEmpty()) return

        // Assign targets to stockers without one (or whose target is complete)
        val unzonedItems = state.inventory.entries
            .filter { (_, inv) -> inv.shelfStock > 0 && inv.zoneScore < 1.0f }
            .sortedBy { (_, inv) -> inv.zoneScore }
            .map { (id, _) -> id }

        for (stocker in onShiftStockers) {
            val currentTarget = staffManager.getZoningTarget(stocker.id)
            if (currentTarget != null) {
                val inv = state.inventory[currentTarget]
                if (inv != null && inv.zoneScore < 1.0f && inv.shelfStock > 0) continue
                staffManager.clearZoningTarget(stocker.id)
            }
            val claimed = staffManager.allClaimedZoningTargets()
            val newTarget = unzonedItems.firstOrNull { it !in claimed }
            if (newTarget != null) {
                staffManager.assignZoningTarget(stocker.id, newTarget)
            }
        }

        // Advance zoning progress for each stocker
        for (stocker in onShiftStockers) {
            val weight = stocker.throughputWeight * stocker.levelMultiplier * stocker.trait.throughputMultiplier
            val actions = staffManager.advanceZoningForStocker(stocker.id, weight, delta, multiplier)
            for (action in actions) {
                val inv = state.inventory[action.targetItemId] ?: continue
                val newScore = (inv.zoneScore + StaffManager.ZONE_PER_ACTION).coerceAtMost(1.0f)
                state = state.copy(
                    inventory = state.inventory + (action.targetItemId to inv.copy(zoneScore = newScore))
                )
            }
        }
    }

    fun setAutoHireBudget(budget: Money) {
        state = state.copy(autoHireBudget = budget)
    }

    // ── Register helpers ──────────────────────────────────────────────────────

    /**
     * Returns true when [registerId] has at least one worker that can serve customers:
     * - A hired cashier explicitly assigned to it who is currently on shift, OR
     * - The player assigned to it with [PlayerRole.CASHIER] active, OR
     * - Manager mode: when [PlayerRole.MANAGE], unassigned on-shift cashiers are dynamically
     *   allocated to unassigned registers (in register-id order) by the player acting as manager.
     * - (Backward compat) Any on-shift cashier if this is the only register and it has
     *   no explicit assignment (mirrors pre-Phase-3 behaviour for single-register saves).
     */
    private fun isRegisterMannedAndOnShift(
        registerId: Int,
        state: GameState,
        currentHour: Int,
    ): Boolean {
        val register = state.registers.findRegisterById(registerId) ?: return false

        // Player-assigned check
        if (state.playerAssignedRegisterId == registerId && state.playerRole == PlayerRole.CASHIER) {
            return true
        }

        // Explicit cashier assignment check
        val assignedId = register.assignedCashierId
        if (assignedId != null) {
            val shift = state.staffSchedules.firstOrNull { it.entityId == assignedId }
            return shift?.isOnShift(currentHour) == true
        }

        // Manager mode: player with NONE role acts as manager, dynamically assigning
        // unassigned on-shift cashiers to unassigned registers (in register-id order).
        if (state.playerRole == PlayerRole.MANAGE) {
            val unassignedCount = StaffManager.unassignedOnShiftCashierCount(
                currentHour, state.staffSchedules, state.hiredEntityRegistry, state.registers
            )
            if (unassignedCount <= 0) return false
            val unassignedRegisters = state.registers
                .filter { it.assignedCashierId == null }
                .sortedBy { it.registerId }
            val indexInUnassigned = unassignedRegisters.indexOfFirst { it.registerId == registerId }
            return indexInUnassigned in 0 until unassignedCount
        }

        // Backward-compat fallback: single register with no assignment →
        // any on-shift cashier staffs it (matches pre-Phase-3 behaviour).
        if (state.registers.size == 1) {
            return StaffManager.activeWeightedCount(
                EntityDef.CASHIER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            ) > 0f || state.playerRole == PlayerRole.CASHIER
        }

        return false
    }

    /**
     * Returns the total cashier throughput weight available for [registerId].
     *
     * - For explicitly assigned registers: weight of the assigned cashier (if on shift).
     * - Manager mode: pool weight (unassigned on-shift cashiers) divided evenly across
     *   the manned unassigned registers (capped to the number of available cashiers).
     * - Backward-compat single-register fallback: sum of all on-shift cashiers.
     */
    private fun getCashierWeightForRegister(
        registerId: Int,
        state: GameState,
        currentHour: Int,
    ): Float {
        val register = state.registers.findRegisterById(registerId) ?: return 0f
        val assignedId = register.assignedCashierId

        if (assignedId != null) {
            val cashier = try {
                state.hiredEntityRegistry.getById(assignedId)
            } catch (e: NoSuchElementException) {
                return 0f
            }
            val shift = state.staffSchedules.firstOrNull { it.entityId == assignedId }
            val onShift = shift?.isOnShift(currentHour) == true
            // Off-shift cashiers still finish their active transaction at full speed
            return if (onShift || register.transactionActive)
                cashier.throughputWeight * cashier.levelMultiplier * cashier.trait.throughputMultiplier
            else 0f
        }

        // Manager mode: divide the unassigned cashier pool weight across the manned
        // unassigned registers (number of manned registers ≤ number of cashiers).
        if (state.playerRole == PlayerRole.MANAGE) {
            val unassignedCount = StaffManager.unassignedOnShiftCashierCount(
                currentHour, state.staffSchedules, state.hiredEntityRegistry, state.registers
            )
            if (unassignedCount <= 0) return 0f
            val unassignedRegisters = state.registers
                .filter { it.assignedCashierId == null }
                .sortedBy { it.registerId }
            val mannedCount = minOf(unassignedCount, unassignedRegisters.size)
            val indexInUnassigned = unassignedRegisters.indexOfFirst { it.registerId == registerId }
            if (indexInUnassigned !in 0 until mannedCount) return 0f
            val totalWeight = StaffManager.unassignedOnShiftCashierWeight(
                currentHour, state.staffSchedules, state.hiredEntityRegistry, state.registers
            )
            return totalWeight / mannedCount
        }

        // Backward-compat: single register with no assignment → global pool
        if (state.registers.size == 1) {
            return StaffManager.activeWeightedCount(
                EntityDef.CASHIER, currentHour, state.staffSchedules, state.hiredEntityRegistry
            )
        }

        return 0f
    }

    // ── Register management ───────────────────────────────────────────────────

    /**
     * Purchase one additional register, subject to store-size cap and affordability.
     * Delegates guards and state mutation to [StoreController.purchaseRegister].
     */
    fun purchaseRegister() {
        val moneyBefore = state.money
        state = storeController.purchaseRegister(state)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    /**
     * Assign (or unassign) a hired cashier to a register.
     *
     * Guards:
     * - [cashierId] must belong to an existing entity when non-null.
     * - The cashier must not already be assigned to a different register.
     * - [registerId] must exist.
     */
    fun assignCashierToRegister(cashierId: Int?, registerId: Int) {
        val register = state.registers.findRegisterById(registerId) ?: return
        if (cashierId != null) {
            // Validate cashier exists
            try { state.hiredEntityRegistry.getById(cashierId) } catch (e: NoSuchElementException) { return }
            // Must not be assigned elsewhere
            if (state.registers.any { it.registerId != registerId && it.assignedCashierId == cashierId }) return
        }
        val previousCashierId = register.assignedCashierId
        val updatedUnassigned = if (cashierId == null && previousCashierId != null) {
            state.manuallyUnassignedCashiers + previousCashierId
        } else if (cashierId != null) {
            state.manuallyUnassignedCashiers - cashierId
        } else {
            state.manuallyUnassignedCashiers
        }
        state = state.copy(
            registers = state.registers.updateRegister(register.copy(assignedCashierId = cashierId)),
            manuallyUnassignedCashiers = updatedUnassigned,
        )
    }

    /**
     * Assign (or unassign) the player to a register.
     *
     * Guards:
     * - Non-null [registerId] must exist.
     * - Cannot assign player to a register that already has a hired cashier.
     */
    fun assignPlayerToRegister(registerId: Int?) {
        if (registerId != null) {
            val register = state.registers.findRegisterById(registerId) ?: return
            if (register.assignedCashierId != null) return
        }
        if (registerId == null && state.playerRole == PlayerRole.CASHIER) {
            state = state.copy(
                playerAssignedRegisterId = null,
                playerRole = PlayerRole.MANAGE,
                playerCashierProgress = 0f,
            )
        } else {
            state = state.copy(playerAssignedRegisterId = registerId)
        }
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
            // Add empty inventory entries for newly unlocked items
            val newItems = itemMetadataCache.getAllItems().filter { (itemId, _) ->
                val meta = itemMetadataCache.get(itemId) ?: return@filter false
                meta.tier.unlockAmount > previousTier.unlockAmount &&
                    meta.tier.unlockAmount <= state.currentTier.unlockAmount &&
                    itemId !in state.inventory
            }
            if (newItems.isNotEmpty()) {
                val updatedInventory = state.inventory.toMutableMap()
                for ((itemId, _) in newItems) {
                    updatedInventory[itemId] = InventoryState()
                }
                state = state.copy(inventory = updatedInventory)
            }

            // New categories inherit defaultMarkup
            val defaultMarkup = state.pricingState.defaultMarkup
            if (defaultMarkup != 0) {
                val oldCategories = previousTier.unlockedSections
                val newCategories = state.currentTier.unlockedSections - oldCategories
                if (newCategories.isNotEmpty()) {
                    val updatedMarkups = state.pricingState.categoryMarkups.toMutableMap()
                    for (cat in newCategories) {
                        if (cat !in updatedMarkups) {
                            updatedMarkups[cat] = defaultMarkup
                        }
                    }
                    state = state.copy(
                        pricingState = state.pricingState.copy(categoryMarkups = updatedMarkups)
                    )
                }
            }
            _changes.value = GameStateChange.TierUnlocked(state.currentTier, previousTier)
        }
    }

    // ── Fresh Item Auto-Ordering ──────────────────────────────────────────


    /**
     * Place a fresh bulk order with lower discount tiers.
     */
    fun placeFreshBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.placeFreshBulkOrder(state, maxTotalQuantity, casePacksPerItem, state.currentTier)
        state = result.state
        if (result.orderLines.isNotEmpty()) {
            val currentDay = state.currentTime.dayNumber
            state = truckManager.scheduleFreshOrderLines(state, result.orderLines, currentDay)
            _changes.value = GameStateChange.OrderScheduled(currentDay + 1)
        }
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    /**
     * Update fresh item auto-ordering configuration.
     */
    fun updateFreshAutoOrderConfig(enabled: Boolean, threshold: Int, casePacks: Int) {
        state = state.copy(
            freshAutoOrderConfig = FreshAutoOrderConfig(
                enabled = enabled,
                minStockThreshold = threshold,
                casePacksPerItem = casePacks
            )
        )
    }

    /**
     * Immediately process fresh auto-orders for items that fall below the threshold
     * when fresh handlers are idle. Orders are processed on-the-spot:
     * - Affordable orders: inventory updated, money deducted, metrics updated immediately.
     * - Unaffordable orders: logged as incomplete for the daily report.
     *
     * Uses [DailyMetricsAccumulator.autoOrderedFreshItems] and
     * [DailyMetricsAccumulator.incompleteOrderedFreshItems] to prevent re-attempting
     * the same item more than once per day.
     */
    private fun attemptFreshHandlerAutoOrder() {
        if (!state.freshAutoOrderConfig.enabled) return

        // Build the set of item IDs already handled today (success OR failure)
        val alreadyHandled = (state.currentDayMetrics.autoOrderedFreshItems.map { it.itemId } +
            state.currentDayMetrics.incompleteOrderedFreshItems.map { it.itemId }).toSet()

        for ((itemId, _) in state.inventory) {
            if (itemId in alreadyHandled) continue
            if (!inventoryManager.shouldAutoOrderFreshItem(state, itemId, state.freshAutoOrderConfig)) continue

            val item = itemMetadataCache.getItem(itemId) ?: continue
            val casePacks = state.freshAutoOrderConfig.casePacksPerItem
            val totalCost = item.getCasePackCostAsMoney() * casePacks

            if (state.money >= totalCost) {
                // Route through truck system
                val result = inventoryManager.buyItemCasePacks(state, itemId, casePacks)
                state = result.state
                if (result.orderLines.isNotEmpty()) {
                    val currentDay = state.currentTime.dayNumber
                    state = truckManager.scheduleFreshOrderLines(state, result.orderLines, currentDay)
                }
                state = state.copy(
                    currentDayMetrics = state.currentDayMetrics.copy(
                        autoOrderedFreshItems = state.currentDayMetrics.autoOrderedFreshItems +
                            com.example.superstoresimulator.domain.metrics.FreshOrderLineItem(
                                itemId = itemId,
                                itemName = item.name,
                                casePacksOrdered = casePacks,
                                costPerCasePack = item.getCasePackCostAsMoney(),
                                totalCost = totalCost,
                            )
                    )
                )
                _changes.value = GameStateChange.MoneyChanged(state.money)
            } else {
                // Failure path: log as incomplete
                state = state.copy(
                    currentDayMetrics = state.currentDayMetrics.copy(
                        incompleteOrderedFreshItems = state.currentDayMetrics.incompleteOrderedFreshItems +
                            com.example.superstoresimulator.domain.metrics.IncompleteOrderLineItem(
                                itemId = itemId,
                                itemName = item.name,
                                casePacksRequested = casePacks,
                                costPerCasePack = item.getCasePackCostAsMoney(),
                                totalCost = totalCost,
                                reason = "Insufficient funds",
                            )
                    ),
                    incompleteFreshOrders = state.incompleteFreshOrders.filter { it.itemId != itemId } +
                        com.example.superstoresimulator.domain.IncompleteOrderRequest(
                            itemId = itemId,
                            casePacksRequested = casePacks,
                            requestedOnDay = state.currentTime.dayNumber,
                            reason = "Insufficient funds",
                        )
                )
            }
        }
    }

    /**
     * Manually order an incomplete fresh item.
     * Deducts cost from money if affordable.
     */
    fun orderIncompleteItem(itemId: Int, casePacksRequested: Int) {
        val item = itemMetadataCache.getItem(itemId) ?: return
        val totalCost = item.getCasePackCostAsMoney() * casePacksRequested
        
        if (state.money >= totalCost) {
            val result = inventoryManager.buyItemCasePacks(state, itemId, casePacksRequested)
            state = result.state
            if (result.orderLines.isNotEmpty()) {
                val currentDay = state.currentTime.dayNumber
                state = truckManager.scheduleFreshOrderLines(state, result.orderLines, currentDay)
                _changes.value = GameStateChange.OrderScheduled(currentDay + 1)
            }
            // Remove from incomplete orders
            state = state.copy(
                incompleteFreshOrders = state.incompleteFreshOrders.filter { it.itemId != itemId }
            )
            _changes.value = GameStateChange.MoneyChanged(state.money)
        }
    }

    // ── Truck Delivery System ─────────────────────────────────────────────────

    /**
     * Update truck delivery configuration (delivery days, capacities).
     * Already-scheduled trucks are untouched.
     */
    fun updateTruckConfig(deliveryDays: Set<Int>, regularCapacity: Int, freshCapacity: Int) {
        state = truckManager.updateConfig(
            state,
            TruckConfig(
                deliveryDays = deliveryDays,
                regularTruckCapacityCasePacks = regularCapacity,
                freshTruckCapacityCasePacks = freshCapacity,
            )
        )
    }

    /**
     * Cancel a pending order line and refund the cost.
     */
    fun cancelPendingOrderLine(itemId: Int, truckId: Int) {
        val moneyBefore = state.money
        state = truckManager.cancelPendingOrderLine(state, itemId, truckId, state.currentTime.dayNumber)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    /**
     * Remove one case pack for [itemId] from truck [truckId] and refund its cost.
     */
    fun decrementOrderLine(itemId: Int, truckId: Int) {
        val moneyBefore = state.money
        state = truckManager.decrementOrderLine(state, itemId, truckId, state.currentTime.dayNumber)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    /**
     * Purchase one extra weekly delivery-day slot for $100.
     * This allows the player to schedule one additional regular delivery day beyond the
     * store-size-based free limit (2 + storeSize.ordinal).
     * Guard: player must have ≥ $100.
     */
    fun purchaseExtraTruckSlot() {
        val moneyBefore = state.money
        state = truckManager.purchaseExtraTruckSlot(state)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    /**
     * Book an on-demand early truck for $100, arriving the next game day.
     */
    fun requestEarlyTruck() {
        val moneyBefore = state.money
        state = truckManager.requestEarlyTruck(state, state.currentTime.dayNumber)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    /**
     * Loads a previously saved GameState.
     * Used for restoring saved games.
     * Syncs/resets all manager internal state to match the loaded game state.
     */
    fun loadState(savedState: GameState) {
        state = savedState
        // Sync time manager to the loaded state's time
        timeManager.syncTime(savedState.currentTime)
        // Sync time manager config - set each property explicitly to ensure proper syncing
        timeManager.config = savedState.storeConfig.copy()
        // Also explicitly set the speed multiplier to ensure it's applied
        timeManager.setSpeedMultiplier(savedState.storeConfig.gameSpeedMultiplier)
        // Sync day manager to prevent double day rollover on first tick
        dayManager.syncDay(savedState.currentTime.dayNumber)
        // Reset staff manager accumulators (fractional work progress is transient)
        staffManager.reset()
        // Reset traffic manager (customers are transient, don't persist across saves)
        trafficManager.reset()
        // Recompute cached pricing multipliers from current pricing state
        state = pricingManager.updateSmoothedPriceIndex(state)
        lastPriceIndexHour = savedState.currentTime.hour
        // Clear any pending changes
        _changes.value = null
    }

    // ── Pricing System ───────────────────────────────────────────────────────

    fun setCategoryMarkup(category: com.example.superstoresimulator.domain.items.ItemCategory, percent: Int) {
        state = pricingManager.setCategoryMarkup(state, category, percent)
    }

    fun setDefaultMarkup(percent: Int) {
        state = pricingManager.setDefaultMarkup(state, percent)
    }

    fun setItemPriceOverride(itemId: Int, percent: Int) {
        state = pricingManager.setItemOverride(state, itemId, percent)
    }

    fun clearItemMarkdown(itemId: Int) {
        state = pricingManager.clearMarkdown(state, itemId)
    }
}
