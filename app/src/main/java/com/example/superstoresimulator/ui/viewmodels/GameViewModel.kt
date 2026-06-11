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
import com.example.superstoresimulator.ui.state.mappers.buildProgressionUiState
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
import com.example.superstoresimulator.domain.store.StaffWageCalculator.calculateTotalWages

@HiltViewModel
class GameViewModel @Inject constructor(
    private val itemDao: ItemDao,
    @param:ApplicationContext private val context: Context,
    @param:TickDelta private val tickDelta: Long = 16,
    private val gameStateRepository: GameStateRepository,
    private val gameEngine: GameEngine,
    val itemMetadataCache: ItemMetadataCache,
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

    init {
        viewModelScope.launch {
            ItemDataLoader.loadItemsIfNeeded(context, itemDao)
            itemMetadataCache.initialize()

            val savedState = gameStateRepository.loadGameState()
            if (savedState != null) {
                gameEngine.loadState(savedState)
            } else {
                gameEngine.seedNewGame()
            }

            gameEngineInitialized = true

            val initialState = gameEngine.currentState()
            _uiState.value = initialUiState(initialState)
        }
        viewModelScope.launch {
            gameEngine.engineEvents.collect { event ->
                when (event) {
                    is GameEngine.EngineEvent.OrderScheduled -> {
                        val day = event.arrivalDay
                        val dayOfWeekName = when (day % 7) {
                            0 -> "Monday"; 1 -> "Tuesday"; 2 -> "Wednesday"; 3 -> "Thursday"
                            4 -> "Friday"; 5 -> "Saturday"; 6 -> "Sunday"
                            else -> "Day $day"
                        }
                        _snackbarMessage.tryEmit("Order placed — arriving $dayOfWeekName, Day ${day + 1}")
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
    
    /**
     * Get the current GameState from the engine.
     * Used by UI components that need fresh auto-order configuration and other domain state.
     */
    fun currentState(): GameState {
        if (!gameEngineInitialized) return GameState()
        return gameEngine.currentState()
    }

    fun onEvent(event: GameEvent) {
        // Only process events if gameEngine is initialized
        if (!gameEngineInitialized) return
        
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

            GameEvent.UnlockNextTier -> {
                gameEngine.unlockNextTier()
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

            GameEvent.DismissTierUnlock -> {
                _uiState.update { it?.copy(progression = it.progression.copy(justUnlockedTier = null)) }
                return  // Pure-UI: no engine call needed
            }

            is GameEvent.SelectStaffDef -> {
                _uiState.update { state ->
                    state?.copy(
                        staff = state.staff.copy(
                            selectedDef = event.staffDef
                        )
                    ) ?: return@update null
                }
                return
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
                gameEngine.updateShift(event.entityId, clampedHour, duration)
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

            GameEvent.Tick -> {
                if (!gameEngine.isSimulating) gameEngine.tick(tickDelta)
            }

        }
        
        if (gameEngineInitialized) {
            val newDomainState = gameEngine.currentState()
            
            if (shouldRebuildUiState(newDomainState)) {
                val newUiState = toUiState(newDomainState, _uiState.value)
                _uiState.value = newUiState
                lastUiState = newUiState
                lastDomainState = newDomainState
            }
        }

    }

    init {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay((tickDelta))
                onEvent(GameEvent.Tick)
            }
        }
    }
    private fun initialUiState(domain: GameState): GameUiState {
        val scheduleEntries = buildStaffScheduleEntries(domain)
        return GameUiState(
            app = AppUIState(
                storeName = domain.storeName,
                pendingRefunds = domain.pendingRefunds.size,
                transactionActive = domain.registers.firstOrNull()?.transactionActive ?: false,
                money = domain.money
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
                isDialogOpen = false,
                pendingRefunds = domain.pendingRefunds,
                pendingCustomers = domain.pendingCustomers,
                completedToday = domain.currentDayMetrics.transactionsCompleted,
            ),
            inventory = inventoryMapper.map(
                domain.inventory, domain.currentTier, domain.storeConfig.backroomCapPerItem,
                priceResolver = if (gameEngineInitialized) gameEngine::resolvePrice else null,
                gameState = domain,
            ).copy(
                selectedCategory = null,
            ),
            staff = StaffUIState(
                registry = domain.hiredEntityRegistry,
                selectedDef = null,
                scheduleEntries = scheduleEntries,
                currentHour = domain.currentTime.hour,
                employeeActivities = if (gameEngineInitialized) gameEngine.employeeActivities() else emptyMap(),
                cashierUtilization = if (gameEngineInitialized) gameEngine.cashierUtilization() else 0f,
                stockerUtilization = if (gameEngineInitialized) gameEngine.stockerUtilization() else 0f,
                freshUtilization = if (gameEngineInitialized) gameEngine.freshUtilization() else 0f,
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
            progression = buildProgressionUiState(domain, null),
            delivery = buildDeliveryUiState(domain, itemMetadataCache),
            registers = buildRegistersUiState(domain),
            pricing = buildPricingUiState(domain),
        )
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
                domain.inventory, domain.currentTier, domain.storeConfig.backroomCapPerItem,
                domain.scheduledTrucks,
                priceResolver = gameEngine::resolvePrice,
                gameState = domain,
            ).copy(
                selectedCategory = oldUi?.inventory?.selectedCategory,
                focusedItemId = oldUi?.inventory?.focusedItemId,
            ),
            staff = (oldUi?.staff?.copy(
                registry = domain.hiredEntityRegistry,
                scheduleEntries = scheduleEntries,
                currentHour = domain.currentTime.hour,
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
                selectedDef = null,
                scheduleEntries = scheduleEntries,
                currentHour = domain.currentTime.hour,
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
            progression = buildProgressionUiState(domain, oldUi?.progression),
            delivery = buildDeliveryUiState(domain, itemMetadataCache),
            registers = buildRegistersUiState(domain),
            pricing = buildPricingUiState(domain),
        )
    }

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
        dailyRent = domain.currentStoreSize.dailyRent,
        dailyWages = calculateTotalWages(domain.hiredEntityRegistry, domain.staffSchedules),
    )

    private fun buildMetricsUiState(domain: GameState) = MetricsUIState(
        completedDays = domain.completedDayMetrics.sortedByDescending { it.dayNumber },
        activeDay = domain.currentDayMetrics.copy(dayOfWeek = domain.currentTime.dayOfWeek),
        showEndOfDayReport = domain.showEndOfDayReport,
        lastReport = domain.lastEndOfDayReport,
    )

    // ── Pricing Controls ─────────────────────────────────────────────────────

    fun setCategoryMarkup(category: com.example.superstoresimulator.domain.items.ItemCategory, percent: Int) {
        if (!gameEngineInitialized) return
        gameEngine.setCategoryMarkup(category, percent)
    }

    fun setDefaultMarkup(percent: Int) {
        if (!gameEngineInitialized) return
        gameEngine.setDefaultMarkup(percent)
    }

    fun setItemPriceOverride(itemId: Int, percent: Int) {
        if (!gameEngineInitialized) return
        gameEngine.setItemPriceOverride(itemId, percent)
    }

    fun clearItemMarkdown(itemId: Int) {
        if (!gameEngineInitialized) return
        gameEngine.clearItemMarkdown(itemId)
    }

    /**
     * Saves the current game state to persistent storage.
     * Called automatically when the ViewModel is cleared (app closed/backgrounded).
     * Can also be called manually via GameEvent.SaveGame.
     */
    fun saveGameState() {
        if (!gameEngineInitialized) return
        viewModelScope.launch {
            val currentState = gameEngine.currentState()
            gameStateRepository.saveGameState(currentState)
        }
    }

    /**
     * Resets the game to initial state by clearing saved data and reinitializing the engine.
     * Called when user confirms reset from settings panel.
     */
    private fun resetGame() {
        viewModelScope.launch {
            gameStateRepository.clearSave()

            if (gameEngineInitialized) {
                gameEngine.resetState()

                lastDomainState = null
                lastUiState = null

                val freshState = gameEngine.currentState()
                _uiState.value = initialUiState(freshState)
            }
        }
    }

}

