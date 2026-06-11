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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    sealed interface EngineEvent {
        data class OrderScheduled(val arrivalDay: Int) : EngineEvent
    }

    private val _engineEvents = MutableSharedFlow<EngineEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val engineEvents: SharedFlow<EngineEvent> = _engineEvents.asSharedFlow()

    var state = GameState()
        internal set

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
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    fun stockItemFromBackroom(itemId: Int) {
        state = inventoryManager.stockItemFromBackroom(state, itemId)
    }

    fun buyItemToBackroom(itemId: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.scheduleAndEmitOrder(state, inventoryManager.buyItemToBackroom(state, itemId), moneyBefore)
        state = result.state
        result.orderArrivalDay?.let { _engineEvents.tryEmit(EngineEvent.OrderScheduled(it)) }
    }

    fun buyItemCasePacks(itemId: Int, numCasePacks: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.scheduleAndEmitOrder(state, inventoryManager.buyItemCasePacks(state, itemId, numCasePacks), moneyBefore)
        state = result.state
        result.orderArrivalDay?.let { _engineEvents.tryEmit(EngineEvent.OrderScheduled(it)) }
    }

    fun placeBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) {
        val moneyBefore = state.money
        val result = inventoryManager.scheduleAndEmitOrder(state, inventoryManager.placeBulkOrder(state, maxTotalQuantity, casePacksPerItem, categoryFilter), moneyBefore)
        state = result.state
        result.orderArrivalDay?.let { _engineEvents.tryEmit(EngineEvent.OrderScheduled(it)) }
    }

    fun placeFreshBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int) {
        val moneyBefore = state.money
        val result = inventoryManager.scheduleAndEmitOrder(
            state,
            inventoryManager.placeFreshBulkOrder(state, maxTotalQuantity, casePacksPerItem, state.currentTier),
            moneyBefore,
        )
        state = result.state
        result.orderArrivalDay?.let { _engineEvents.tryEmit(EngineEvent.OrderScheduled(it)) }
    }

    fun orderIncompleteItem(itemId: Int, casePacksRequested: Int) {
        val result = inventoryManager.orderIncompleteItem(state, itemId, casePacksRequested)
        state = result.state
        result.orderArrivalDay?.let { _engineEvents.tryEmit(EngineEvent.OrderScheduled(it)) }
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
        state = transactionEngine.processRefund(state, refundId)
    }

    fun processRefundLine(refundId: Int, itemId: Int, qty: Int = 1) {
        state = transactionEngine.processRefundLine(state, refundId, itemId, qty)
    }

    // ── Staff ─────────────────────────────────────────────────────────────────

    fun hireEntity(def: EntityDef) {
        state = staffManager.hireEntity(state, def)
        if (def == EntityDef.CASHIER) {
            state = registerManager.autoAssignUnassignedCashiers(state)
        }
    }

    fun promoteEntity(entityId: Int) { state = staffManager.promoteEntity(state, entityId) }

    fun fireEntity(entityId: Int) {
        state = staffManager.fireEntity(state, entityId)
        state = registerManager.unassignEntity(state, entityId)
    }

    fun updateShift(entityId: Int, newStartHour: Int, newDuration: Int = 8) {
        state = staffManager.updateShift(state, entityId, newStartHour, newDuration)
    }

    fun setAutoHireBudget(budget: Money) { state = state.copy(autoHireBudget = budget) }

    // ── Store ─────────────────────────────────────────────────────────────────

    fun updateStoreName(newName: String) { state = storeController.updateStoreName(state, newName) }

    fun upgradeStoreSize() {
        state = storeController.upgradeStoreSize(state)
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
        state = registerManager.purchaseRegister(state)
    }

    fun assignCashierToRegister(cashierId: Int?, registerId: Int) {
        state = registerManager.assignCashierToRegister(state, cashierId, registerId)
    }

    fun assignPlayerToRegister(registerId: Int?) {
        state = registerManager.assignPlayerToRegister(state, registerId)
    }

    // ── Day/Time ──────────────────────────────────────────────────────────────

    fun dismissEndOfDayReport() { state = dayManager.dismissEndOfDayReport(state) }

    @Volatile
    var isSimulating: Boolean = false
        private set

    fun simulateRestOfDay() {
        if (state.showEndOfDayReport || isSimulating) return
        isSimulating = true
        try {
            state = state.copy(
                playerPausedTime = false,
                playerCashierProgress = 0f,
                playerStockerProgress = 0f,
            )
            val targetMinutes = (state.currentTime.dayNumber + 1).toLong() * 1440L
            state = simulateUntil(state, targetMinutes)
        } finally {
            isSimulating = false
        }
    }

    fun simulateUntil(startState: GameState, targetMinutes: Long): GameState {
        var s = startState
        val simulationDeltaMs = 500L
        while (s.currentTime.totalMinutesElapsed < targetMinutes && !s.showEndOfDayReport) {
            val result = tickOrchestrator.tick(s, simulationDeltaMs)
            s = result.state
        }
        return s
    }

    // ── Progression ───────────────────────────────────────────────────────────

    fun unlockNextTier() {
        state = progressionManager.unlockNextTier(state)
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
        result.orderArrivalDay?.let { _engineEvents.tryEmit(EngineEvent.OrderScheduled(it)) }
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
        state = truckManager.cancelPendingOrderLine(state, itemId, truckId, state.currentTime.dayNumber)
    }

    fun decrementOrderLine(itemId: Int, truckId: Int) {
        state = truckManager.decrementOrderLine(state, itemId, truckId, state.currentTime.dayNumber)
    }

    fun purchaseExtraTruckSlot() {
        state = truckManager.purchaseExtraTruckSlot(state)
    }

    fun requestEarlyTruck() {
        state = truckManager.requestEarlyTruck(state, state.currentTime.dayNumber)
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
        restoreManagersFromState()
        state = pricingManager.updateSmoothedPriceIndex(state)
    }

    fun seedNewGame() {
        state = buildSeededState()
        restoreManagersFromState()
    }

    fun resetState() {
        state = buildSeededState()
        restoreManagersFromState()
    }

    private fun buildSeededState(): GameState {
        val baseState = GameState()
        val allDbItems = itemMetadataCache.getAllItems()
        if (allDbItems.isEmpty()) return baseState

        val startingTier = ItemUnlockTier.TIER_2
        val inventory = mutableMapOf<Int, InventoryState>()
        val currentDay = baseState.currentTime.dayNumber
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
        return baseState.copy(
            inventory = inventory,
            money = Money(5_000_000),
            currentTier = startingTier,
            currentStoreSize = StoreSize.SMALL_GROCERY,
            storeConfig = StoreConfig(backroomCapPerItem = StoreSize.SMALL_GROCERY.backroomCapPerItem),
        )
    }

    private fun restoreManagersFromState() {
        val acc = state.simAccumulators
        timeManager.syncTime(state.currentTime)
        timeManager.accumulatedMilliseconds = acc.timeAccumulatorMs
        timeManager.config = state.storeConfig.copy()
        timeManager.setSpeedMultiplier(state.storeConfig.gameSpeedMultiplier)
        dayManager.syncDay(acc.lastKnownDayNumber)
        trafficManager.accumulatedCustomers = acc.trafficAccumulator
        staffManager.restoreAccumulators(acc)
    }
}
