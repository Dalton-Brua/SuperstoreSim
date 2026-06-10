package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.progression.ProgressionManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.tick.TickOrchestrator
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.traffic.TrafficManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameEngine @Inject constructor(
    private val itemMetadataCache: ItemMetadataCache,
    private val tickOrchestrator: TickOrchestrator,
    private val transactionEngine: TransactionEngine,
    private val inventoryManager: InventoryManager,
    private val registerManager: RegisterManager,
    private val staffManager: StaffManager,
    private val storeController: StoreController,
    private val playerActionHandler: PlayerActionHandler,
    private val progressionManager: ProgressionManager,
    private val pricingManager: PricingManager,
    private val dayManager: DayManager,
    private val timeManager: TimeManager,
    private val trafficManager: TrafficManager,
    private val truckManager: TruckManager,
) {
    private val _changes = MutableStateFlow<GameStateChange?>(null)
    val changes: StateFlow<GameStateChange?> = _changes.asStateFlow()

    var state = GameState(
        inventory = mutableMapOf(),
        currentTime = timeManager.currentTime,
        storeState = timeManager.getStoreState(),
        storeConfig = StoreConfig(backroomCapPerItem = StoreSize.MOM_AND_POP.backroomCapPerItem),
        money = Money.ZERO,
        currentTier = ItemUnlockTier.TIER_1,
        totalRevenue = Money.ZERO,
    )
        internal set

    init {
        val allDbItems = itemMetadataCache.getAllItems()
        if (allDbItems.isNotEmpty()) {
            val startingTier = ItemUnlockTier.TIER_2
            val inventory = mutableMapOf<Int, InventoryState>()
            val currentDay = state.currentTime.dayNumber
            allDbItems.forEach { (itemId, item) ->
                val metadata = itemMetadataCache.get(itemId)
                val itemTier = metadata?.tier ?: ItemUnlockTier.TIER_1
                if (itemTier.unlockAmount > startingTier.unlockAmount) return@forEach
                val expirationDay = if (item.shelfLifeDays != null) {
                    currentDay + item.shelfLifeDays
                } else Int.MAX_VALUE
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
                storeConfig = StoreConfig(backroomCapPerItem = StoreSize.SMALL_GROCERY.backroomCapPerItem),
            )
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    fun getDbItem(itemId: Int): Item? = itemMetadataCache.getItem(itemId)
    fun currentState(): GameState = state
    fun employeeActivities() = staffManager.employeeActivities
    fun cashierUtilization(): Float = staffManager.currentCashierUtilization()
    fun stockerUtilization(): Float = staffManager.currentStockerUtilization()
    fun freshUtilization(): Float = staffManager.currentFreshUtilization()

    // ── Tick ──────────────────────────────────────────────────────────────────

    fun tick(deltaMilliseconds: Long) {
        val result = tickOrchestrator.tick(state, deltaMilliseconds)
        state = result.state
        result.changes.lastOrNull()?.let { _changes.value = it }
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    fun stockItemFromBackroom(itemId: Int) {
        val before = state.inventory[itemId]
        state = inventoryManager.stockItemFromBackroom(state, itemId)
        val after = state.inventory[itemId]
        if (after != null && after != before) {
            _changes.value = GameStateChange.InventoryUpdated(itemId, after)
        }
    }

    fun buyItemToBackroom(itemId: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.scheduleAndEmitOrder(state, inventoryManager.buyItemToBackroom(state, itemId), moneyBefore)
        state = result.state
        result.changes.lastOrNull()?.let { _changes.value = it }
    }

    fun buyItemCasePacks(itemId: Int, numCasePacks: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.scheduleAndEmitOrder(state, inventoryManager.buyItemCasePacks(state, itemId, numCasePacks), moneyBefore)
        state = result.state
        result.changes.lastOrNull()?.let { _changes.value = it }
    }

    fun placeBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) {
        val moneyBefore = state.money
        val result = inventoryManager.scheduleAndEmitOrder(state, inventoryManager.placeBulkOrder(state, maxTotalQuantity, casePacksPerItem, categoryFilter), moneyBefore)
        state = result.state
        result.changes.lastOrNull()?.let { _changes.value = it }
    }

    fun placeFreshBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.scheduleAndEmitOrder(
            state,
            inventoryManager.placeFreshBulkOrder(state, maxTotalQuantity, casePacksPerItem, state.currentTier),
            moneyBefore,
        )
        state = result.state
        result.changes.lastOrNull()?.let { _changes.value = it }
    }

    fun orderIncompleteItem(itemId: Int, casePacksRequested: Int) {
        val result = inventoryManager.orderIncompleteItem(state, itemId, casePacksRequested)
        state = result.state
        result.changes.lastOrNull()?.let { _changes.value = it }
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    fun startTransaction() {
        val defaultRegisterId = state.registers.firstOrNull()?.registerId ?: 0
        val register = state.registers.findRegisterById(defaultRegisterId)
        if (register != null && !register.transactionActive && state.storeState != StoreState.CLOSED) {
            state = transactionEngine.startNewTransaction(state, defaultRegisterId)
        }
    }

    fun ringUpItem(itemId: Int) {
        val registerId = state.playerAssignedRegisterId
            ?: state.registers.firstOrNull()?.registerId
            ?: return
        state = transactionEngine.ringUpItemAndTrackMetrics(state, itemId, registerId)
    }

    fun ringUpItem() {
        val registerId = state.playerAssignedRegisterId
            ?: state.registers.firstOrNull()?.registerId
            ?: return
        state = transactionEngine.ringUpItemOnRegister(state, registerId)
    }

    fun processRefund(refundId: Int) {
        val refund = state.pendingRefunds.find { it.id == refundId }
        state = transactionEngine.processRefund(state, refundId)
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
        state = transactionEngine.processRefundLine(state, refundId, itemId, qty)
    }

    // ── Staff ─────────────────────────────────────────────────────────────────

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

    fun promoteEntity(entityId: Int) { state = staffManager.promoteEntity(state, entityId) }

    fun fireEntity(entityId: Int) {
        state = staffManager.fireEntity(state, entityId)
        val updatedRegisters = state.registers.map { register ->
            if (register.assignedCashierId == entityId) register.copy(assignedCashierId = null)
            else register
        }
        if (updatedRegisters != state.registers) {
            state = state.copy(registers = updatedRegisters)
        }
    }

    fun updateShift(entityId: Int, newStartHour: Int, newDuration: Int = 8) {
        state = staffManager.updateShift(state, entityId, newStartHour, newDuration)
    }

    fun setAutoHireBudget(budget: Money) { state = state.copy(autoHireBudget = budget) }

    // ── Store ─────────────────────────────────────────────────────────────────

    fun updateStoreName(newName: String) { state = storeController.updateStoreName(state, newName) }

    fun upgradeStoreSize() {
        val moneyBefore = state.money
        state = storeController.upgradeStoreSize(state)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    fun setGameSpeed(multiplier: Float) {
        timeManager.setSpeedMultiplier(multiplier)
        state = storeController.setGameSpeedState(state, multiplier)
    }

    fun toggleTimePaused() { state = storeController.toggleTimePaused(state) }

    // ── Player ────────────────────────────────────────────────────────────────

    fun setPlayerRole(role: PlayerRole) { state = playerActionHandler.setPlayerRole(state, role) }

    // ── Registers ─────────────────────────────────────────────────────────────

    fun purchaseRegister() {
        val moneyBefore = state.money
        state = registerManager.purchaseRegister(state)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    fun assignCashierToRegister(cashierId: Int?, registerId: Int) {
        state = registerManager.assignCashierToRegister(state, cashierId, registerId)
    }

    fun assignPlayerToRegister(registerId: Int?) {
        state = registerManager.assignPlayerToRegister(state, registerId)
    }

    // ── Day/Time ──────────────────────────────────────────────────────────────

    fun dismissEndOfDayReport() { state = dayManager.dismissEndOfDayReport(state) }

    fun simulateRestOfDay() {
        if (state.showEndOfDayReport) return
        val currentDayNumber = state.currentTime.dayNumber
        val nextMidnightMinutes = (currentDayNumber + 1).toLong() * 1440L
        state = state.copy(
            playerPausedTime = false,
            playerCashierProgress = 0f,
            playerStockerProgress = 0f,
        )
        val simulationDeltaMs = 500L
        while (state.currentTime.totalMinutesElapsed < nextMidnightMinutes && !state.showEndOfDayReport) {
            tick(simulationDeltaMs)
        }
    }

    // ── Progression ───────────────────────────────────────────────────────────

    fun unlockNextTier() {
        val previousTier = state.currentTier
        state = progressionManager.unlockNextTier(state)
        if (state.currentTier != previousTier) {
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
            _changes.value = GameStateChange.TierUnlocked(state.currentTier, previousTier)
        }
    }

    // ── Auto-Order Config ──────────────────────────────────────────────────────

    fun updateFreshAutoOrderConfig(enabled: Boolean, threshold: Int, casePacks: Int) {
        state = state.copy(
            freshAutoOrderConfig = FreshAutoOrderConfig(
                enabled = enabled,
                minStockThreshold = threshold,
                casePacksPerItem = casePacks,
            )
        )
    }

    fun updateNormalAutoOrderConfig(enabled: Boolean, threshold: Int, casePacks: Int) {
        state = state.copy(
            normalAutoOrderConfig = NormalAutoOrderConfig(
                enabled = enabled,
                minStockThreshold = threshold,
                casePacksPerItem = casePacks,
            )
        )
    }

    fun updateStoreManagerConfig(config: StoreManagerConfig) {
        state = state.copy(storeManagerConfig = config)
    }

    fun orderIncompleteNormalItem(itemId: Int, casePacksRequested: Int) {
        val result = inventoryManager.orderIncompleteNormalItem(state, itemId, casePacksRequested)
        state = result.state
        result.changes.lastOrNull()?.let { _changes.value = it }
    }

    // ── Truck Delivery ────────────────────────────────────────────────────────

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

    fun cancelPendingOrderLine(itemId: Int, truckId: Int) {
        val moneyBefore = state.money
        state = truckManager.cancelPendingOrderLine(state, itemId, truckId, state.currentTime.dayNumber)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    fun decrementOrderLine(itemId: Int, truckId: Int) {
        val moneyBefore = state.money
        state = truckManager.decrementOrderLine(state, itemId, truckId, state.currentTime.dayNumber)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    fun purchaseExtraTruckSlot() {
        val moneyBefore = state.money
        state = truckManager.purchaseExtraTruckSlot(state)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    fun requestEarlyTruck() {
        val moneyBefore = state.money
        state = truckManager.requestEarlyTruck(state, state.currentTime.dayNumber)
        if (state.money != moneyBefore) _changes.value = GameStateChange.MoneyChanged(state.money)
    }

    // ── Pricing ───────────────────────────────────────────────────────────────

    fun resolvePrice(itemId: Int) = pricingManager.resolvePrice(itemId, state)

    fun setCategoryMarkup(category: ItemCategory, percent: Int) {
        state = pricingManager.setCategoryMarkup(state, category, percent)
    }

    fun setDefaultMarkup(percent: Int) { state = pricingManager.setDefaultMarkup(state, percent) }

    fun setItemPriceOverride(itemId: Int, percent: Int) {
        state = pricingManager.setItemOverride(state, itemId, percent)
    }

    fun clearItemMarkdown(itemId: Int) { state = pricingManager.clearMarkdown(state, itemId) }

    // ── Save/Load ─────────────────────────────────────────────────────────────

    fun loadState(savedState: GameState) {
        state = savedState
        timeManager.syncTime(savedState.currentTime)
        timeManager.config = savedState.storeConfig.copy()
        timeManager.setSpeedMultiplier(savedState.storeConfig.gameSpeedMultiplier)
        dayManager.syncDay(savedState.currentTime.dayNumber)
        staffManager.reset()
        trafficManager.reset()
        state = pricingManager.updateSmoothedPriceIndex(state)
        _changes.value = null
    }

    fun resetState() {
        staffManager.reset()
        trafficManager.reset()
        timeManager.syncTime(com.example.superstoresimulator.domain.time.GameTime(0))
        timeManager.config = StoreConfig()
        timeManager.setSpeedMultiplier(1.0f)
        dayManager.syncDay(0)
        _changes.value = null

        state = GameState(
            inventory = mutableMapOf(),
            currentTime = timeManager.currentTime,
            storeState = timeManager.getStoreState(),
            storeConfig = StoreConfig(backroomCapPerItem = StoreSize.MOM_AND_POP.backroomCapPerItem),
            money = Money.ZERO,
            currentTier = ItemUnlockTier.TIER_1,
            totalRevenue = Money.ZERO,
        )

        val allDbItems = itemMetadataCache.getAllItems()
        if (allDbItems.isNotEmpty()) {
            val startingTier = ItemUnlockTier.TIER_2
            val inventory = mutableMapOf<Int, InventoryState>()
            val currentDay = state.currentTime.dayNumber
            allDbItems.forEach { (itemId, item) ->
                val metadata = itemMetadataCache.get(itemId)
                val itemTier = metadata?.tier ?: ItemUnlockTier.TIER_1
                if (itemTier.unlockAmount > startingTier.unlockAmount) return@forEach
                val expirationDay = if (item.shelfLifeDays != null) {
                    currentDay + item.shelfLifeDays
                } else Int.MAX_VALUE
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
                storeConfig = StoreConfig(backroomCapPerItem = StoreSize.SMALL_GROCERY.backroomCapPerItem),
            )
        }
    }
}
