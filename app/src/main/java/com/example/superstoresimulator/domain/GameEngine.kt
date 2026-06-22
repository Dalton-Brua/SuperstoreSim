package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Transactions.TransactionEngine
import com.example.superstoresimulator.domain.delivery.TruckManager
import com.example.superstoresimulator.domain.inventory.InventoryManager
import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.inventory.ItemBatch
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.metrics.DayManager
import com.example.superstoresimulator.domain.player.PlayerActionHandler
import com.example.superstoresimulator.domain.player.PlayerRole
import com.example.superstoresimulator.domain.pricing.PricingManager
import com.example.superstoresimulator.domain.registers.RegisterManager
import com.example.superstoresimulator.domain.research.AnalystAssignment
import com.example.superstoresimulator.domain.research.ResearchManager
import com.example.superstoresimulator.domain.research.ResearchUpgradeRegistry
import com.example.superstoresimulator.domain.staff.StaffManager
import com.example.superstoresimulator.domain.store.StoreConfig
import com.example.superstoresimulator.domain.store.StoreController
import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.store.StoreState
import com.example.superstoresimulator.domain.tick.TickOrchestrator
import com.example.superstoresimulator.domain.time.TimeManager
import com.example.superstoresimulator.domain.traffic.TrafficManager
import com.example.superstoresimulator.domain.vendor.VendorManager
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
    private val researchManager: ResearchManager,
    private val pricingManager: PricingManager,
    private val dayManager: DayManager,
    private val timeManager: TimeManager,
    private val trafficManager: TrafficManager,
    private val truckManager: TruckManager,
    private val vendorManager: VendorManager,
) {
    sealed interface EngineEvent {
        data class OrderScheduled(val arrivalDay: Int) : EngineEvent
        data class ResearchCompleted(
            val upgradeId: String,
            val displayName: String,
            val unlockedItemCount: Int,
        ) : EngineEvent
    }

    private val _engineEvents = MutableSharedFlow<EngineEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val engineEvents: SharedFlow<EngineEvent> = _engineEvents.asSharedFlow()

    @Volatile
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

    fun tick(
        deltaMilliseconds: Long,
        offlineMode: Boolean = false,
        sampleUtilization: Boolean = true,
        skipAccumulators: Boolean = false,
    ) {
        val researchedBefore = state.researchState.researchedUpgrades
        state = tickOrchestrator.tick(state, deltaMilliseconds, offlineMode, sampleUtilization, skipAccumulators)
        emitNewlyCompletedResearch(researchedBefore)
    }

    /** Emit a [EngineEvent.ResearchCompleted] for each upgrade finished during the last tick. */
    private fun emitNewlyCompletedResearch(researchedBefore: Set<String>) {
        val newlyCompleted = state.researchState.researchedUpgrades - researchedBefore
        if (newlyCompleted.isEmpty()) return
        for (upgradeId in newlyCompleted) {
            val upgrade = ResearchUpgradeRegistry.allUpgrades[upgradeId] ?: continue
            val unlockedItemCount = itemMetadataCache.getAllItems()
                .count { (_, item) -> item.researchGate == upgradeId }
            _engineEvents.tryEmit(
                EngineEvent.ResearchCompleted(upgradeId, upgrade.displayName, unlockedItemCount)
            )
        }
    }

    // ── Inventory ─────────────────────────────────────────────────────────────

    fun stockItemFromBackroom(itemId: Int) {
        state = inventoryManager.stockItemFromBackroom(state, itemId)
    }

    private fun applyOrder(result: InventoryManager.OrderResult) {
        state = result.state
        result.orderArrivalDay?.let { _engineEvents.tryEmit(EngineEvent.OrderScheduled(it)) }
    }

    fun buyItemToBackroom(itemId: Int) {
        applyOrder(inventoryManager.scheduleAndEmitOrder(inventoryManager.buyItemToBackroom(state, itemId)))
    }

    fun buyItemCasePacks(itemId: Int, numCasePacks: Int) {
        applyOrder(inventoryManager.scheduleAndEmitOrder(inventoryManager.buyItemCasePacks(state, itemId, numCasePacks)))
    }

    fun placeBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int, categoryFilter: ItemCategory?) {
        applyOrder(inventoryManager.scheduleAndEmitOrder(inventoryManager.placeBulkOrder(state, maxTotalQuantity, casePacksPerItem, categoryFilter)))
    }

    fun placeFreshBulkOrder(maxTotalQuantity: Int, casePacksPerItem: Int) {
        applyOrder(
            inventoryManager.scheduleAndEmitOrder(
                inventoryManager.placeFreshBulkOrder(state, maxTotalQuantity, casePacksPerItem),
            )
        )
    }

    fun orderIncompleteItem(itemId: Int, casePacksRequested: Int) {
        applyOrder(inventoryManager.orderIncompleteItem(state, itemId, casePacksRequested))
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
        state = researchManager.removeAnalystAssignment(state, entityId)
    }

    fun assignAnalyst(entityId: Int, assignment: AnalystAssignment?) {
        state = if (assignment != null) {
            researchManager.assignAnalyst(state, entityId, assignment)
        } else {
            researchManager.removeAnalystAssignment(state, entityId)
        }
    }

    fun skipTutorial() {
        state = state.copy(
            tutorialState = state.tutorialState.copy(
                tutorialComplete = true,
            )
        )
    }

    fun updateShift(entityId: Int, newStartHour: Int, newDuration: Int = 8, workDays: Set<Int> = emptySet()) {
        state = staffManager.updateShift(state, entityId, newStartHour, newDuration, workDays)
    }

    fun setAutoHireBudget(budget: Money) { state = state.copy(autoHireBudget = budget) }

    // ── Store ─────────────────────────────────────────────────────────────────

    fun updateStoreName(newName: String) { state = storeController.updateStoreName(state, newName) }

    fun upgradeStoreSize() {
        state = storeController.upgradeStoreSize(state)
    }

    fun purchaseBuilding() {
        state = storeController.purchaseBuilding(state)
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
        internal set

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

    fun simulateWeek() {
        if (state.showEndOfDayReport || state.showEndOfWeekReport || isSimulating) return
        isSimulating = true
        try {
            val startingBalance = state.money
            val startDay = state.currentTime.dayNumber
            val completedBefore = state.completedDayMetrics.size

            state = state.copy(
                playerPausedTime = false,
                playerCashierProgress = 0f,
                playerStockerProgress = 0f,
            )
            val targetMinutes = (state.currentTime.dayNumber + 7).toLong() * 1440L
            var s = state
            while (s.currentTime.totalMinutesElapsed < targetMinutes) {
                if (s.showEndOfDayReport) {
                    s = dayManager.dismissEndOfDayReport(s)
                }
                val dayEnd = (s.currentTime.dayNumber + 1).toLong() * 1440L
                val stepTarget = minOf(dayEnd, targetMinutes)
                s = simulateUntil(s, stepTarget)
            }
            if (s.showEndOfDayReport) {
                s = dayManager.dismissEndOfDayReport(s)
            }

            val weeklyDailyReports = s.completedDayMetrics.drop(completedBefore)
            val report = com.example.superstoresimulator.domain.metrics.WeeklyReport(
                startDay = startDay,
                endDay = s.currentTime.dayNumber,
                startingBalance = startingBalance,
                endingBalance = s.money,
                dailyReports = weeklyDailyReports,
            )
            state = s.copy(
                showEndOfWeekReport = true,
                lastEndOfWeekReport = report,
                playerPausedTime = true,
                pausedByEndOfDay = true,
            )
        } finally {
            isSimulating = false
        }
    }

    fun dismissEndOfWeekReport() {
        state = state.copy(
            showEndOfWeekReport = false,
            lastEndOfWeekReport = null,
            playerPausedTime = if (state.pausedByEndOfDay) false else state.playerPausedTime,
            pausedByEndOfDay = false,
        )
    }

    fun simulateUntil(startState: GameState, targetMinutes: Long): GameState {
        var s = startState
        val simulationDeltaMs = 2000L
        while (s.currentTime.totalMinutesElapsed < targetMinutes && !s.showEndOfDayReport) {
            s = tickOrchestrator.tick(s, simulationDeltaMs, skipAccumulators = true)
        }
        return s.copy(simAccumulators = tickOrchestrator.snapshotAccumulators())
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
        applyOrder(inventoryManager.orderIncompleteNormalItem(state, itemId, casePacksRequested))
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

    fun purchaseTruckUpgrade() {
        state = truckManager.purchaseTruckUpgrade(state)
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
        state = repairMetricsIfNeeded(savedState)
        restoreManagersFromState()
        state = pricingManager.updateSmoothedPriceIndex(state)
    }

    private fun repairMetricsIfNeeded(saved: GameState): GameState {
        val currentDay = saved.currentTime.dayNumber
        val metricsDay = saved.currentDayMetrics.dayNumber
        // Fix currentDayMetrics.dayNumber if it's behind the actual day
        // (happens with saves predating simAccumulators)
        if (metricsDay < currentDay && saved.completedDayMetrics.any { it.dayNumber == metricsDay }) {
            return saved.copy(
                currentDayMetrics = saved.currentDayMetrics.copy(dayNumber = currentDay)
            )
        }
        return saved
    }

    fun seedNewGame() {
        state = buildSeededState()
        restoreManagersFromState()
    }

    fun resetState() = seedNewGame()

    private fun seedInventory(starterOnly: Boolean, quantity: Int, currentDay: Int): Map<Int, InventoryState> {
        val inventory = mutableMapOf<Int, InventoryState>()
        itemMetadataCache.getAllItems().forEach { (itemId, item) ->
            val meta = itemMetadataCache.get(itemId) ?: return@forEach
            if (starterOnly && meta.researchGate != null && !meta.isVendorItem) return@forEach
            val expirationDay = if (item.shelfLifeDays != null) {
                currentDay + item.shelfLifeDays
            } else Int.MAX_VALUE
            val startingBatch = ItemBatch(
                receivedDay = currentDay,
                quantity = quantity,
                expirationDay = expirationDay,
            )
            inventory[itemId] = InventoryState(
                shelfBatches = listOf(startingBatch),
                backroomBatches = listOf(startingBatch),
            )
        }
        return inventory
    }

    private fun buildSeededState(): GameState {
        if (DEBUG_RICH_SEED) return buildDebugRichState()

        val baseState = GameState()
        if (itemMetadataCache.getAllItems().isEmpty()) return baseState

        val seeded = baseState.copy(
            inventory = seedInventory(starterOnly = true, quantity = 10, baseState.currentTime.dayNumber),
            money = Money(100_000),
            // Start at 5:00 AM (300 minutes) so the player has lead time before opening.
            currentTime = com.example.superstoresimulator.domain.time.GameTime(300),
            // Truck deliveries on Tuesday (1) and Friday (4). 0 = Monday … 6 = Sunday.
            truckConfig = TruckConfig(deliveryDays = setOf(1, 4)),
            // Start paused; the first tutorial step asks the player to unpause to begin.
            playerPausedTime = true,
        )
        return vendorManager.initializeStartingVendors(seeded)
    }

    fun unlockNextVendorTier() {
        state = vendorManager.unlockNextVendorTier(state)
    }

    fun investInVendor(vendorId: String) {
        state = vendorManager.investInVendor(state, vendorId)
    }

    // ── Empire mode (loop 2) ────────────────────────────────────────────────────
    // Delegates to stateless controller objects in domain.empire. Each Wave-1 track
    // owns one object; GameEngine only routes.

    fun enterEmpireMode() {
        state = com.example.superstoresimulator.domain.empire.EmpireTransitionActions.enterEmpireMode(state)
    }

    fun unlockRegion(regionId: Int) {
        state = com.example.superstoresimulator.domain.empire.EmpireTransitionActions.unlockRegion(state, regionId)
    }

    fun openLocation(regionId: Int) {
        state = com.example.superstoresimulator.domain.empire.EmpireTransitionActions.openLocation(state, regionId)
    }

    fun setStoreDirection(storeId: Int, direction: com.example.superstoresimulator.domain.empire.StoreDirection) {
        state = com.example.superstoresimulator.domain.empire.EmpireTransitionActions.setStoreDirection(state, storeId, direction)
    }

    fun buyStoreUpgrade(storeId: Int, upgrade: com.example.superstoresimulator.domain.empire.StoreUpgrade) {
        state = com.example.superstoresimulator.domain.empire.EmpireTransitionActions.buyStoreUpgrade(state, storeId, upgrade)
    }

    fun expandStoreSize(storeId: Int) {
        state = com.example.superstoresimulator.domain.empire.EmpireTransitionActions.expandStoreSize(state, storeId)
    }

    fun hireRegionalManager(personality: com.example.superstoresimulator.domain.empire.ManagerPersonality) {
        state = com.example.superstoresimulator.domain.empire.RegionalManagerManager.hire(state, personality)
    }

    fun fireRegionalManager() {
        state = com.example.superstoresimulator.domain.empire.RegionalManagerManager.fire(state)
    }

    fun setEmpireSpeed(speed: com.example.superstoresimulator.domain.empire.EmpireSpeed) {
        state = com.example.superstoresimulator.domain.empire.EmpireClockController.setSpeed(state, speed)
    }

    /** Empire clock real-ms driver, called from the ViewModel tick loop in empire mode. */
    fun empireTick(deltaMs: Long) {
        state = com.example.superstoresimulator.domain.empire.EmpireClockController.tick(state, deltaMs)
    }

    fun advanceToNextDecision() {
        state = com.example.superstoresimulator.domain.empire.EmpireClockController.advanceToNextDecision(state)
    }

    fun dropIntoStore(storeId: Int) {
        state = com.example.superstoresimulator.domain.empire.OperateStoreController.dropIn(state, storeId)
    }

    fun exitOperatedStore() {
        state = com.example.superstoresimulator.domain.empire.OperateStoreController.dropOut(state)
    }

    private fun buildDebugRichState(): GameState {
        val baseState = GameState()
        if (itemMetadataCache.getAllItems().isEmpty()) return baseState

        val storeSize = StoreSize.GROCERY_STORE
        val inventory = seedInventory(starterOnly = false, quantity = 20, baseState.currentTime.dayNumber)

        var registry = baseState.hiredEntityRegistry
        // Hire staff: 2 cashiers, 2 stockers, 1 fresh handler, 1 manager
        val hireList = listOf(
            EntityDef.CASHIER, EntityDef.CASHIER,
            EntityDef.STOCKER, EntityDef.STOCKER,
            EntityDef.FRESH_HANDLER,
            EntityDef.MANAGER,
        )
        val staffShifts = mutableListOf<StaffShift>()
        for (def in hireList) {
            registry = registry.hireEntity(def)
            val newId = registry.hiredEntities.last().id
            staffShifts.add(StaffShift(entityId = newId, startHour = 6, durationHours = 8))
        }

        return baseState.copy(
            inventory = inventory,
            money = Money(50_000_000),
            currentStoreSize = storeSize,
            storeConfig = StoreConfig(backroomCapPerItem = storeSize.backroomCapPerItem),
            hiredEntityRegistry = registry,
            staffSchedules = staffShifts,
            registers = listOf(
                RegisterState(registerId = 0),
                RegisterState(registerId = 1),
                RegisterState(registerId = 2),
            ),
        )
    }

    companion object {
        // TEMP: Set to true for rich test seed with staff/inventory/grocery store
        const val DEBUG_RICH_SEED = false
    }

    private fun restoreManagersFromState() {
        val acc = state.simAccumulators
        timeManager.syncTime(state.currentTime)
        timeManager.accumulatedMilliseconds = acc.timeAccumulatorMs
        timeManager.config = state.storeConfig.copy()
        timeManager.setSpeedMultiplier(state.storeConfig.gameSpeedMultiplier)
        // Use currentTime.dayNumber as fallback for saves that predate simAccumulators
        val effectiveDayNumber = maxOf(acc.lastKnownDayNumber, state.currentTime.dayNumber)
        dayManager.syncDay(effectiveDayNumber)
        trafficManager.accumulatedCustomers = acc.trafficAccumulator
        staffManager.restoreAccumulators(acc)
        tickOrchestrator.resetTickProcessors()
    }
}
