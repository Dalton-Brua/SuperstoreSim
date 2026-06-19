package com.example.superstoresimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemDataLoader
import com.example.superstoresimulator.ui.state.AppUIState
import com.example.superstoresimulator.ui.state.DashboardUIState
import com.example.superstoresimulator.ui.state.GameUiState
import com.example.superstoresimulator.ui.state.HistoryUIState
import com.example.superstoresimulator.ui.state.MetricsUIState
import com.example.superstoresimulator.ui.state.StaffUIState
import com.example.superstoresimulator.ui.state.TransactionUIState
import com.example.superstoresimulator.ui.state.TimeUIState
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.ui.state.mappers.MemoizedInventoryMapper
import com.example.superstoresimulator.ui.state.mappers.buildRegistersUiState
import com.example.superstoresimulator.ui.state.mappers.buildStaffScheduleEntries
import com.example.superstoresimulator.ui.state.mappers.countActiveStaff
import com.example.superstoresimulator.ui.state.mappers.buildDeliveryUiState
import com.example.superstoresimulator.ui.state.mappers.buildPricingUiState
import com.example.superstoresimulator.ui.state.mappers.buildVendorUiState
import com.example.superstoresimulator.ui.state.mappers.buildResearchUiState
import com.example.superstoresimulator.ui.state.mappers.buildReputationUiState
import com.example.superstoresimulator.ui.state.mappers.buildTutorialUiState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.example.superstoresimulator.di.TickDelta
import java.util.Locale
import android.content.Context
import com.example.superstoresimulator.domain.Entities.Tier
import com.example.superstoresimulator.domain.persistence.GameStateRepository
import com.example.superstoresimulator.domain.inventory.InventoryState
import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.Transactions.Transaction
import com.example.superstoresimulator.domain.offline.OfflineCatchUpRunner
import com.example.superstoresimulator.domain.offline.OfflineProgress
import com.example.superstoresimulator.domain.offline.OfflineState
import com.example.superstoresimulator.domain.store.StaffWageCalculator.calculateTotalWages
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.metrics.MetricsArchiver

@HiltViewModel
class GameViewModel @Inject constructor(
    private val itemDao: ItemDao,
    @param:ApplicationContext private val context: Context,
    @param:TickDelta private val tickDelta: Long = 16,
    private val gameStateRepository: GameStateRepository,
    private val gameEngine: GameEngine,
    private val itemMetadataCache: ItemMetadataCache,
    private val tutorialManager: com.example.superstoresimulator.domain.tutorial.TutorialManager,
    private val metricsArchiver: MetricsArchiver,
) : ViewModel() {

    private var gameEngineInitialized = false

    private val _uiState = MutableStateFlow<GameUiState?>(null)

    val uiState = _uiState.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    private var lastDomainState: GameState? = null
    private var lastUiState: GameUiState? = null

    private val inventoryMapper = MemoizedInventoryMapper(itemMetadataCache)

    private val _offlineState = MutableStateFlow<OfflineState>(OfflineState.Idle)
    val offlineState = _offlineState.asStateFlow()

    private var pausedWallClock: Long = 0L
    private var tickLoopActive = true
    private var archivedSummaries: List<com.example.superstoresimulator.domain.metrics.DailyMetrics> = emptyList()
    private var loadedArchivedDay: com.example.superstoresimulator.domain.metrics.DailyMetrics? = null

    companion object {
        private const val MIN_OFFLINE_THRESHOLD_MS = 2L * 60 * 1000
    }

    init {
        viewModelScope.launch {
            ItemDataLoader.loadItemsIfNeeded(context, itemDao)
            itemMetadataCache.initialize()

            val savedState = gameStateRepository.loadGameState()
            val lastSaveTime = gameStateRepository.getLastSaveTime()
            if (savedState != null) {
                gameEngine.loadState(savedState)
                migrateOldMetricsToRoom(savedState)
            } else {
                gameEngine.seedNewGame()
            }
            archivedSummaries = metricsArchiver.loadAllSummaries()

            gameEngineInitialized = true

            val initialState = gameEngine.currentState()
            _uiState.value = toUiState(initialState, null)

            // Cold-start catch-up: compute gap from last save time
            if (savedState != null && lastSaveTime != null) {
                maybeCatchUp(System.currentTimeMillis() - lastSaveTime, savedState)
            }
        }
        viewModelScope.launch {
            gameEngine.engineEvents.collect { event ->
                when (event) {
                    is GameEngine.EngineEvent.OrderScheduled -> {
                        val day = event.arrivalDay
                        _snackbarMessage.tryEmit("Order placed — arriving ${GameTime.fullDayName(day)}, Day ${day + 1}")
                    }
                    is GameEngine.EngineEvent.ResearchCompleted -> {
                        val itemsSuffix = if (event.unlockedItemCount > 0) {
                            " — ${event.unlockedItemCount} new item${if (event.unlockedItemCount == 1) "" else "s"} unlocked!"
                        } else ""
                        _snackbarMessage.tryEmit("Research Complete: ${event.displayName}$itemsSuffix")
                    }
                }
            }
        }
    }

    /**
     * Read-only helper for UI overlays that need full inventory batch details.
     * Returns null until the engine is initialized or when item is missing.
     */
    fun getInventoryStateForItem(itemId: Int): InventoryState? {
        if (!gameEngineInitialized) return null
        return gameEngine.currentState().inventory[itemId]
    }
    
    private fun currentState(): GameState {
        if (!gameEngineInitialized) return GameState()
        return gameEngine.currentState()
    }

    fun onEvent(event: GameEvent) {
        if (!gameEngineInitialized) return
        if (_offlineState.value !is OfflineState.Idle) return

        when (event) {
            GameEvent.RingUp -> gameEngine.ringUpItem()
            is GameEvent.RingUpItem -> gameEngine.ringUpItem(event.itemId)
            GameEvent.StartTransaction -> gameEngine.startTransaction()
            is GameEvent.StockItem -> gameEngine.stockItemFromBackroom(event.itemId)
            is GameEvent.BuyItem -> gameEngine.buyItemToBackroom(event.itemId)
            is GameEvent.HireStaff -> gameEngine.hireEntity(event.entityDef)
            is GameEvent.PromoteStaff -> gameEngine.promoteEntity(event.entityId)
            is GameEvent.FireStaff -> gameEngine.fireEntity(event.entityId)
            is GameEvent.ChangeStoreName -> gameEngine.updateStoreName(event.name)
            is GameEvent.ProcessRefund -> gameEngine.processRefund(event.refundId)
            is GameEvent.ProcessRefundLine -> {
                gameEngine.processRefundLine(event.refundId, event.itemId, event.quantity)
            }
            is GameEvent.SetGameSpeed -> {
                gameEngine.setGameSpeed(event.multiplier)
                return  // Don't update full UI state, just speed changed
            }
            GameEvent.ToggleStore -> {
                gameEngine.toggleTimePaused()
                // Will update UI state after this
            }

            GameEvent.UpgradeStoreSize -> {
                gameEngine.upgradeStoreSize()
            }

            is GameEvent.SetPlayerRole -> {
                gameEngine.setPlayerRole(event.role)
            }

            GameEvent.DismissEndOfDayReport -> {
                gameEngine.dismissEndOfDayReport()
            }

            GameEvent.SkipDay -> {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    gameEngine.simulateRestOfDay()
                }
                return
            }

            GameEvent.SkipWeek -> {
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    gameEngine.simulateWeek()
                }
                return
            }

            GameEvent.DismissEndOfWeekReport -> {
                gameEngine.dismissEndOfWeekReport()
            }

            is GameEvent.AssignAnalyst -> {
                gameEngine.assignAnalyst(event.entityId, event.assignment)
            }

            GameEvent.SkipTutorial -> {
                gameEngine.skipTutorial()
            }

            is GameEvent.BulkOrder -> {
                gameEngine.placeBulkOrder(
                    maxTotalQuantity = event.maxTotalQuantity,
                    casePacksPerItem = event.casePacksPerItem,
                    categoryFilter = event.categoryFilter,
                )
            }

            GameEvent.SaveGame -> {
                saveGameState()
                return  // No UI state update needed
            }

            GameEvent.ResetGame -> {
                resetGame()
                return  // No UI state update needed - will reload fresh state
            }


            is GameEvent.SelectItemCategory -> {
                _uiState.update { state ->
                    state?.copy(
                        inventory = state.inventory.copy(
                            selectedCategory = event.category,
                            focusedItemId = null
                        )

                    ) ?: return@update null
                }
                return // No need to update UI state from engine for this event, it's purely a UI selection
            }
            is GameEvent.FocusInventoryItem -> {
                val itemId = event.itemId
                viewModelScope.launch {
                    if (itemId == null) {
                        // Clear focus
                        _uiState.update { state ->
                            state?.copy(
                                inventory = state.inventory.copy(
                                    focusedItemId = null
                                )
                            ) ?: return@update null
                        }
                    } else {
                        // Convert integer itemId to database format "item_XXX"
                        val dbItemId = "item_" + String.format(Locale.US, "%03d", itemId)
                        val def = itemDao.getItemById(dbItemId)
                        _uiState.update { state ->
                            state?.copy(
                                inventory = state.inventory.copy(
                                    selectedCategory = def?.category,
                                    focusedItemId = itemId
                                )
                            ) ?: return@update null
                        }
                    }
                }
                return // No need to update UI state from engine for this event, it's UI-focused
            }

            is GameEvent.FreshBulkOrder -> {
                gameEngine.placeFreshBulkOrder(
                    maxTotalQuantity = event.maxTotalQuantity,
                    casePacksPerItem = event.casePacksPerItem,
                )
            }

            is GameEvent.UpdateFreshAutoOrderConfig -> {
                gameEngine.updateFreshAutoOrderConfig(
                    event.enabled,
                    event.minStockThreshold,
                    event.casePacksPerItem,
                )
            }

            is GameEvent.UpdateNormalAutoOrderConfig -> {
                gameEngine.updateNormalAutoOrderConfig(
                    event.enabled,
                    event.minStockThreshold,
                    event.casePacksPerItem,
                )
            }

            is GameEvent.OrderIncompleteItem -> {
                gameEngine.orderIncompleteItem(
                    event.itemId,
                    event.casePacksRequested,
                )
            }

            is GameEvent.OrderIncompleteNormalItem -> {
                gameEngine.orderIncompleteNormalItem(
                    event.itemId,
                    event.casePacksRequested,
                )
            }

            is GameEvent.UpdateTruckConfig -> {
                gameEngine.updateTruckConfig(
                    event.deliveryDays,
                    event.regularCapacityCasePacks,
                    event.freshCapacityCasePacks,
                )
            }

            is GameEvent.CancelPendingOrderLine -> {
                gameEngine.cancelPendingOrderLine(event.itemId, event.truckId)
            }

            is GameEvent.DecrementOrderLine -> {
                gameEngine.decrementOrderLine(event.itemId, event.truckId)
            }

            GameEvent.RequestEarlyTruck -> {
                gameEngine.requestEarlyTruck()
            }

            GameEvent.PurchaseExtraTruckSlot -> {
                gameEngine.purchaseExtraTruckSlot()
            }

            is GameEvent.UpdateShift -> {
                val duration = event.newDuration.coerceIn(2, 8)
                val clampedHour = event.newStartHour.coerceIn(6, 21 - duration)
                gameEngine.updateShift(event.entityId, clampedHour, duration, event.workDays)
            }

            // Register System (Phase 3)
            GameEvent.PurchaseRegister -> {
                gameEngine.purchaseRegister()
            }
            is GameEvent.AssignCashierToRegister -> {
                gameEngine.assignCashierToRegister(event.cashierId, event.registerId)
            }
            is GameEvent.AssignPlayerToRegister -> {
                gameEngine.assignPlayerToRegister(event.registerId)
            }

            is GameEvent.SetAutoHireBudget -> {
                gameEngine.setAutoHireBudget(event.budget)
            }

            // Pricing System
            is GameEvent.SetCategoryMarkup -> {
                gameEngine.setCategoryMarkup(event.category, event.percent)
            }
            is GameEvent.SetDefaultMarkup -> {
                gameEngine.setDefaultMarkup(event.percent)
            }
            is GameEvent.SetItemPriceOverride -> {
                gameEngine.setItemPriceOverride(event.itemId, event.percent)
            }
            is GameEvent.ClearItemMarkdown -> {
                gameEngine.clearItemMarkdown(event.itemId)
            }

            is GameEvent.UpdateStoreManagerConfig -> {
                gameEngine.updateStoreManagerConfig(event.config)
            }

            GameEvent.UnlockNextVendorTier -> {
                gameEngine.unlockNextVendorTier()
            }

            is GameEvent.InvestInVendor -> {
                gameEngine.investInVendor(event.vendorId)
            }

            GameEvent.PurchaseBuilding -> {
                gameEngine.purchaseBuilding()
            }

            GameEvent.PurchaseTruckUpgrade -> {
                gameEngine.purchaseTruckUpgrade()
            }

            GameEvent.Tick -> {
                if (!gameEngine.isSimulating) gameEngine.tick(tickDelta)
            }

            is GameEvent.LoadArchivedDayDetail -> {
                viewModelScope.launch {
                    loadedArchivedDay = metricsArchiver.loadArchivedDay(event.dayNumber)
                    refreshUiState()
                }
                return
            }

            GameEvent.ClearLoadedArchivedDay -> {
                loadedArchivedDay = null
            }
        }

        refreshUiState()
    }

    /** Rebuild the UI state from the engine if the domain state changed since the last rebuild. */
    private fun refreshUiState() {
        if (!gameEngineInitialized) return
        if (metricsArchiver.consumeSummariesDirty()) {
            viewModelScope.launch {
                archivedSummaries = metricsArchiver.loadAllSummaries()
                val ds = gameEngine.currentState()
                val ui = toUiState(ds, _uiState.value)
                _uiState.value = ui
                lastUiState = ui
                lastDomainState = ds
            }
        }
        val newDomainState = gameEngine.currentState()
        if (!shouldRebuildUiState(newDomainState)) return
        val newUiState = toUiState(newDomainState, _uiState.value)
        _uiState.value = newUiState
        lastUiState = newUiState
        lastDomainState = newDomainState
    }

    /** Run offline catch-up when the away gap is large enough and the player didn't pause intentionally. */
    private fun maybeCatchUp(elapsedMs: Long, state: GameState) {
        val shouldSkip = state.playerPausedTime && !state.pausedByEndOfDay
        if (elapsedMs >= MIN_OFFLINE_THRESHOLD_MS && !shouldSkip) {
            viewModelScope.launch { performOfflineCatchUp(elapsedMs) }
        }
    }

    init {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(tickDelta)
                if (tickLoopActive) {
                    onEvent(GameEvent.Tick)
                }
            }
        }
    }
    private fun toUiState(domain: GameState, oldUi: GameUiState?): GameUiState {
        val scheduleEntries = buildStaffScheduleEntries(domain)
        return GameUiState(
            app = AppUIState(
                storeName = domain.storeName,
                pendingRefunds = domain.pendingRefunds.size,
                transactionActive = domain.registers.firstOrNull()?.transactionActive ?: false,
                money = domain.money,
            ),
            dashboard = DashboardUIState(
                money = domain.money,
                totalStaff = domain.hiredEntityRegistry.totalCount(),
                activeStaff = countActiveStaff(domain),
                avgZoneScore = domain.avgZoneScore,
            ),
            transactions = TransactionUIState(
                current = domain.registers.firstOrNull()?.currentTransaction ?: Transaction(),
                totalCompleted = domain.totalTransactionsCompleted,
                isActive = domain.registers.firstOrNull()?.transactionActive ?: false,
                isDialogOpen = oldUi?.transactions?.isDialogOpen ?: false,
                pendingRefunds = domain.pendingRefunds,
                pendingCustomers = domain.pendingCustomers,
                completedToday = domain.currentDayMetrics.transactionsCompleted,
            ),
            inventory = inventoryMapper.map(
                currentInventory = domain.inventory,
                researchedUpgrades = domain.researchState.researchedUpgrades,
                backroomCap = domain.storeConfig.backroomCapPerItem,
                scheduledTrucks = domain.scheduledTrucks,
                priceResolver = gameEngine::resolvePrice,
                gameState = domain,
            ).copy(
                selectedCategory = oldUi?.inventory?.selectedCategory,
                focusedItemId = oldUi?.inventory?.focusedItemId,
            ).withDomainInventoryFields(domain),
            staff = (oldUi?.staff?.copy(
                registry = domain.hiredEntityRegistry,
                scheduleEntries = scheduleEntries,
                currentHour = domain.currentTime.hour,
                currentDayOfWeek = domain.currentTime.dayOfWeek,
                employeeActivities = gameEngine.employeeActivities(),
                cashierUtilization = gameEngine.cashierUtilization(),
                stockerUtilization = gameEngine.stockerUtilization(),
                freshUtilization = gameEngine.freshUtilization(),
                hasManagerOnStaff = domain.hiredEntityRegistry.getByDef(EntityDef.MANAGER).isNotEmpty(),
                hasSeniorManager = domain.hiredEntityRegistry.getByDef(EntityDef.MANAGER).any { it.tier != Tier.BASE },
                hasStoreManager = domain.hiredEntityRegistry.getByDef(EntityDef.MANAGER).any { it.isStoreManager },
                autoHireBudget = domain.autoHireBudget,
                storeManagerConfig = domain.storeManagerConfig,
            )) ?: StaffUIState(
                registry = domain.hiredEntityRegistry,
                scheduleEntries = scheduleEntries,
                currentHour = domain.currentTime.hour,
                currentDayOfWeek = domain.currentTime.dayOfWeek,
                employeeActivities = gameEngine.employeeActivities(),
                cashierUtilization = gameEngine.cashierUtilization(),
                stockerUtilization = gameEngine.stockerUtilization(),
                freshUtilization = gameEngine.freshUtilization(),
                hasManagerOnStaff = domain.hiredEntityRegistry.getByDef(EntityDef.MANAGER).isNotEmpty(),
                hasSeniorManager = domain.hiredEntityRegistry.getByDef(EntityDef.MANAGER).any { it.tier != Tier.BASE },
                hasStoreManager = domain.hiredEntityRegistry.getByDef(EntityDef.MANAGER).any { it.isStoreManager },
                autoHireBudget = domain.autoHireBudget,
                storeManagerConfig = domain.storeManagerConfig,
            ),
            history = HistoryUIState(
                salesHistory = domain.salesHistory,
                totalTaxCollected = domain.totalTaxCollected
            ),
            time = buildTimeUiState(domain),
            metrics = buildMetricsUiState(domain),
            research = buildResearchUiState(domain),
            tutorial = buildTutorialUiState(domain, tutorialManager),
            delivery = buildDeliveryUiState(domain, itemMetadataCache),
            registers = buildRegistersUiState(domain),
            pricing = buildPricingUiState(domain),
            vendors = buildVendorUiState(domain, itemMetadataCache),
            reputation = buildReputationUiState(domain),
        )
    }

    private fun com.example.superstoresimulator.ui.state.InventoryUIState.withDomainInventoryFields(
        domain: GameState
    ): com.example.superstoresimulator.ui.state.InventoryUIState = copy(
        hasFastStocker = domain.hiredEntityRegistry.getByDef(EntityDef.STOCKER)
            .any { it.canAutoReorder },
        hasStockingManager = domain.hiredEntityRegistry.getByDef(EntityDef.STOCKER)
            .any { it.isDeptManager },
        hasFreshHandler = domain.hiredEntityRegistry.countByDef(EntityDef.FRESH_HANDLER) > 0,
        freshAutoOrderConfig = domain.freshAutoOrderConfig,
        normalAutoOrderConfig = domain.normalAutoOrderConfig,
        incompleteFreshOrders = domain.incompleteFreshOrders,
        incompleteNormalOrders = domain.incompleteNormalOrders,
        incompleteFreshItemNames = domain.incompleteFreshOrders.associate { order ->
            order.itemId to (itemMetadataCache.getItem(order.itemId)?.name ?: "Item ${order.itemId}")
        },
        incompleteFreshItemCosts = domain.incompleteFreshOrders.associate { order ->
            order.itemId to (itemMetadataCache.getItem(order.itemId)?.getCasePackCostAsMoney()
                ?: com.example.superstoresimulator.domain.Money.ZERO)
        },
        incompleteNormalItemNames = domain.incompleteNormalOrders.associate { order ->
            order.itemId to (itemMetadataCache.getItem(order.itemId)?.name ?: "Item ${order.itemId}")
        },
        incompleteNormalItemCosts = domain.incompleteNormalOrders.associate { order ->
            order.itemId to (itemMetadataCache.getItem(order.itemId)?.getCasePackCostAsMoney()
                ?: com.example.superstoresimulator.domain.Money.ZERO)
        },
    )

    private fun shouldRebuildUiState(newDomainState: GameState): Boolean {
        return newDomainState != lastDomainState
    }

    private fun buildTimeUiState(domain: GameState) = TimeUIState(
        currentTime = domain.currentTime,
        storeState = domain.storeState,
        speedMultiplier = domain.storeConfig.gameSpeedMultiplier,
        playerPausedTime = domain.playerPausedTime,
        playerRole = domain.playerRole,
        playerCashierProgress = domain.playerCashierProgress,
        playerStockerProgress = domain.playerStockerProgress,
        currentStoreSize = domain.currentStoreSize,
        dailyRent = if (domain.buildingOwned) com.example.superstoresimulator.domain.Money.ZERO else domain.currentStoreSize.dailyRent,
        dailyWages = calculateTotalWages(domain.hiredEntityRegistry, domain.staffSchedules, dayOfWeek = domain.currentTime.dayOfWeek),
        buildingOwned = domain.buildingOwned,
    )

    private fun buildMetricsUiState(domain: GameState): MetricsUIState {
        val allDays = (domain.completedDayMetrics + archivedSummaries)
            .sortedByDescending { it.dayNumber }
        val archivedDayNumbers = archivedSummaries.map { it.dayNumber }.toSet()
        return MetricsUIState(
            completedDays = allDays,
            activeDay = domain.currentDayMetrics.copy(dayOfWeek = domain.currentTime.dayOfWeek),
            showEndOfDayReport = domain.showEndOfDayReport,
            lastReport = domain.lastEndOfDayReport,
            showEndOfWeekReport = domain.showEndOfWeekReport,
            weeklyReport = domain.lastEndOfWeekReport,
            itemNames = itemMetadataCache.getAllItemNames(),
            archivedDayNumbers = archivedDayNumbers,
            loadedArchivedDay = loadedArchivedDay,
        )
    }

    private suspend fun migrateOldMetricsToRoom(savedState: GameState) {
        val metrics = savedState.completedDayMetrics
        if (metrics.size <= MetricsArchiver.IN_MEMORY_DAYS) return
        val archiveCount = metricsArchiver.migrateExistingSave(metrics)
        if (archiveCount <= 0) return
        val trimmed = metrics.drop(archiveCount)
        val archived = metrics.take(archiveCount)
        val migratedState = savedState.copy(
            completedDayMetrics = trimmed,
            archivedCumulativeRevenueCents = savedState.archivedCumulativeRevenueCents +
                archived.sumOf { it.revenue.cents },
            archivedCumulativeExpiredItems = savedState.archivedCumulativeExpiredItems +
                archived.sumOf { it.itemsExpired },
            archivedCumulativeExpiredWasteCostCents = savedState.archivedCumulativeExpiredWasteCostCents +
                archived.sumOf { it.expiredWasteCost.cents },
        )
        gameEngine.loadState(migratedState)
        gameStateRepository.saveGameState(migratedState)
    }

    fun onAppPaused() {
        tickLoopActive = false
        pausedWallClock = System.currentTimeMillis()
        saveGameState()
    }

    fun onAppResumed() {
        tickLoopActive = true
        if (!gameEngineInitialized || pausedWallClock == 0L) return

        val elapsedMs = System.currentTimeMillis() - pausedWallClock
        pausedWallClock = 0L
        maybeCatchUp(elapsedMs, gameEngine.currentState())
    }

    fun dismissOfflineSummary() {
        _offlineState.value = OfflineState.Idle
        refreshUiState()
    }

    private suspend fun performOfflineCatchUp(elapsedRealMs: Long) {
        _offlineState.value = OfflineState.CatchingUp(
            OfflineProgress(0, 1, gameEngine.state.currentTime.dayNumber, 0f)
        )

        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val runner = OfflineCatchUpRunner(gameEngine)
            runner.simulate(elapsedRealMs) { progress ->
                _offlineState.value = OfflineState.CatchingUp(progress)
            }
        }

        // Save post-catch-up state
        gameStateRepository.saveGameState(gameEngine.currentState())
        refreshUiState()

        _offlineState.value = OfflineState.Summary(result)
    }

    fun saveGameState() {
        if (!gameEngineInitialized) return
        val currentState = gameEngine.currentState()
        gameStateRepository.saveGameState(currentState)
    }

    /**
     * Resets the game to initial state by clearing saved data and reinitializing the engine.
     * Called when user confirms reset from settings panel.
     */
    private fun resetGame() {
        viewModelScope.launch {
            gameStateRepository.clearSave()
            metricsArchiver.clearAll()
            archivedSummaries = emptyList()
            loadedArchivedDay = null

            if (gameEngineInitialized) {
                gameEngine.resetState()

                lastDomainState = null
                lastUiState = null

                val freshState = gameEngine.currentState()
                _uiState.value = toUiState(freshState, null)
            }
        }
    }

}

