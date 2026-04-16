package com.example.superstoresimulator.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.superstoresimulator.domain.Entities.EntityType
import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.items.ItemDataLoader
import com.example.superstoresimulator.ui.state.AppUIState
import com.example.superstoresimulator.ui.state.DashboardUIState
import com.example.superstoresimulator.ui.state.GameUiState
import com.example.superstoresimulator.ui.state.HistoryUIState
import com.example.superstoresimulator.ui.state.MetricsUIState
import com.example.superstoresimulator.ui.state.ProgressionUIState
import com.example.superstoresimulator.ui.state.StaffUIState
import com.example.superstoresimulator.ui.state.TransactionUIState
import com.example.superstoresimulator.ui.state.TimeUIState
import com.example.superstoresimulator.ui.GameEvent
import com.example.superstoresimulator.domain.items.ItemMetadataCache
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.ui.state.mappers.MemoizedInventoryMapper
import com.example.superstoresimulator.ui.state.builders.IncrementalUiStateBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import com.example.superstoresimulator.di.TickDelta
import java.util.Locale
import android.content.Context

@HiltViewModel
class GameViewModel @Inject constructor(
    private val itemDao: ItemDao,
    @ApplicationContext private val context: Context,
    @param:TickDelta private val tickDelta: Long = 16 // Milliseconds per tick
) : ViewModel() {

    // Lazy initialization of GameEngine - will be created after data loading
    private lateinit var gameEngine: GameEngine
    private var gameEngineInitialized = false

    // Expose an immutable UI state (GameUiState) via StateFlow so UI can collect it.
    private val _uiState = MutableStateFlow<GameUiState?>(null)

    val uiState = _uiState.asStateFlow()

    // Cache previous states to detect changes
    // Only rebuild UI state when domain state actually changes
    private var lastDomainState: GameState? = null
    private var lastUiState: GameUiState? = null

    // Metadata cache + memoized inventory mapper
    val itemMetadataCache = ItemMetadataCache(itemDao)
    private val inventoryMapper = MemoizedInventoryMapper(itemMetadataCache)
    
    // ✅ Problem #4: Incremental UI state builder
    // Reduces state reconstructions from 108,000 to ~1,200 per 30-min session
    private var incrementalBuilder: IncrementalUiStateBuilder? = null

    init {
        // Load items from JSON file BEFORE creating GameEngine
        viewModelScope.launch {
            ItemDataLoader.loadItemsIfNeeded(context, itemDao)
            
            // Initialize metadata cache for fast inventory mapping
            itemMetadataCache.initialize()
            
            // Now create GameEngine with the cached items (no additional database load)
            gameEngine = GameEngine(itemMetadataCache)
            gameEngineInitialized = true
            
            // Update UI state with the initialized engine
            val initialState = gameEngine.currentState()
            _uiState.value = initialUiState(initialState)
            
            // ✅ Problem #4: Initialize incremental builder with full state
            incrementalBuilder = IncrementalUiStateBuilder(_uiState.value!!)
            
            // Listen for incremental state changes and apply them
            gameEngine.changes.collect { change ->
                if (change != null && incrementalBuilder != null) {
                    // Pass the current _uiState.value as the base so that any
                    // pure-UI writes (SelectItemCategory, FocusInventoryItem, etc.)
                    // that bypassed the builder are not overwritten by a stale patch.
                    val newState = incrementalBuilder!!.applyChange(change, _uiState.value)
                    _uiState.value = newState
                }
            }
        }
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
            is GameEvent.HireStaff -> gameEngine.hireEntity(event.entityDef, event.entityType)
            is GameEvent.UpgradeStaff -> gameEngine.upgradeEntity(event.entityId)
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
                gameEngine.simulateRestOfDay()
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

            GameEvent.DismissTierUnlock -> {
                _uiState.update { it?.copy(progression = it.progression.copy(justUnlockedTier = null)) }
                return  // Pure-UI: no engine call needed
            }

            is GameEvent.SelectStaffType -> {
                _uiState.update { state ->
                    state?.copy(
                        staff = state.staff.copy(
                            selectedType = event.staffType
                        )
                    ) ?: return@update null
                }
                return // No need to update UI state from engine for this event, it's purely a UI selection
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
                return // No need to update UI state from engine for this event, it's UI-focused
            }

            GameEvent.Tick -> gameEngine.tick(tickDelta)

        }
        
        // ✅ Performance Optimization #1: Only update UI state if domain state changed
        if (gameEngineInitialized) {
            val newDomainState = gameEngine.currentState()
            
            // Check if domain state actually changed (structural equality)
            if (shouldRebuildUiState(newDomainState)) {
                // Use _uiState.value (not lastUiState) so that direct _uiState.update calls
                // from SelectStaffType / SelectItemCategory / FocusInventoryItem are never
                // overwritten. lastUiState is only updated by this code path and would be
                // stale after any of those early-return handlers fire.
                val newUiState = toUiState(newDomainState, _uiState.value)
                _uiState.value = newUiState
                lastUiState = newUiState
                lastDomainState = newDomainState
            }
            // If nothing changed, keep reusing the same UI state object (no StateFlow update)
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
    // Map domain GameState to UI-level GameUiState
    private fun initialUiState(domain: GameState): GameUiState {
        return GameUiState(
            app = AppUIState(
                storeName = domain.storeName,
                pendingRefunds = domain.pendingRefunds.size,
                transactionActive = domain.transactionActive,
                money = domain.money
            ),
            dashboard = DashboardUIState(
                money = domain.money,
                totalStaff = domain.hiredEntityRegistry.totalCount()
            ),
            transactions = TransactionUIState(
                current = domain.currentTransaction,
                totalCompleted = domain.totalTransactionsCompleted,
                isActive = domain.transactionActive,
                isDialogOpen = false,
                pendingRefunds = domain.pendingRefunds,
                pendingCustomers = domain.pendingCustomers,
                completedToday = domain.currentDayMetrics.transactionsCompleted,
            ),
            inventory = inventoryMapper.map(domain.inventory, domain.currentTier, domain.storeConfig.backroomCapPerItem).copy(
                selectedCategory = null,

            ),
            staff = StaffUIState(
                registry = domain.hiredEntityRegistry,
                selectedType = EntityType.NONE,
            ),
            history = HistoryUIState(
                salesHistory = domain.salesHistory,
                totalTaxCollected = domain.totalTaxCollected
            ),
            time = TimeUIState(
                currentTime = domain.currentTime,
                storeState = domain.storeState,
                speedMultiplier = domain.storeConfig.gameSpeedMultiplier,
                playerPausedTime = domain.playerPausedTime,
                playerRole = domain.playerRole,
                playerCashierProgress = domain.playerCashierProgress,
                playerStockerProgress = domain.playerStockerProgress,
                currentStoreSize = domain.currentStoreSize,
                dailyRent = domain.currentStoreSize.dailyRent,
                dailyWages = calculateDailyWages(domain),
            ),
            metrics = MetricsUIState(
                completedDays = domain.completedDayMetrics.sortedByDescending { it.dayNumber },
                showEndOfDayReport = domain.showEndOfDayReport,
                lastReport = domain.lastEndOfDayReport,
            ),
            progression = buildProgressionUiState(domain, null)
        )
    }

    private fun calculateDailyWages(domain: GameState): com.example.superstoresimulator.domain.Money {
        return com.example.superstoresimulator.domain.store.StaffWageCalculator.calculateTotalWages(domain.hiredEntityRegistry)
    }

    private fun buildProgressionUiState(domain: GameState, old: ProgressionUIState?): ProgressionUIState {
        val nextTier = ItemUnlockTier.nextTier(domain.currentTier)
        val tierStart = domain.currentTier.unlockAmount
        val tierEnd = nextTier?.unlockAmount
        val earned = domain.totalRevenue.cents
        // availableTier: next tier whose revenue gate is met but which hasn't been paid for yet
        val availableTier = nextTier?.takeIf { earned >= it.unlockAmount }
        return ProgressionUIState(
            currentTier = domain.currentTier,
            totalRevenue = domain.totalRevenue,
            nextTier = nextTier,
            revenueToNextTier = tierEnd?.let { com.example.superstoresimulator.domain.Money(it - earned) },
            tierProgressFraction = if (tierEnd != null && tierEnd > tierStart)
                ((earned - tierStart).toFloat() / (tierEnd - tierStart)).coerceIn(0f, 1f)
            else 1f,
            justUnlockedTier = old?.justUnlockedTier,
            availableTier = availableTier,
        )
    }

    private fun toUiState(domain: GameState, oldUi: GameUiState?): GameUiState {
        return GameUiState(
            app = AppUIState(
                storeName = domain.storeName,
                pendingRefunds = domain.pendingRefunds.size,
                transactionActive = domain.transactionActive,
                money = domain.money,
            ),
            dashboard = DashboardUIState(
                money = domain.money,
                totalStaff = domain.hiredEntityRegistry.totalCount()
            ),
            transactions = TransactionUIState(
                current = domain.currentTransaction,
                totalCompleted = domain.totalTransactionsCompleted,
                isActive = domain.transactionActive,
                isDialogOpen = oldUi?.transactions?.isDialogOpen ?: false,
                pendingRefunds = domain.pendingRefunds,
                pendingCustomers = domain.pendingCustomers,
                completedToday = domain.currentDayMetrics.transactionsCompleted,
            ),
            inventory = inventoryMapper.map(domain.inventory, domain.currentTier, domain.storeConfig.backroomCapPerItem).copy(
                selectedCategory = oldUi?.inventory?.selectedCategory,
                focusedItemId = oldUi?.inventory?.focusedItemId,
            ),
            staff = (oldUi?.staff?.copy(
                registry = domain.hiredEntityRegistry,
            )) ?: StaffUIState(
                registry = domain.hiredEntityRegistry,
                selectedType = EntityType.NONE,
            ),
            history = HistoryUIState(
                salesHistory = domain.salesHistory,
                totalTaxCollected = domain.totalTaxCollected
            ),
            time = TimeUIState(
                currentTime = domain.currentTime,
                storeState = domain.storeState,
                speedMultiplier = domain.storeConfig.gameSpeedMultiplier,  // Already the multiplier (1x, 2x, 4x)
                playerPausedTime = domain.playerPausedTime,
                playerRole = domain.playerRole,
                playerCashierProgress = domain.playerCashierProgress,
                playerStockerProgress = domain.playerStockerProgress,
                currentStoreSize = domain.currentStoreSize,
                dailyRent = domain.currentStoreSize.dailyRent,
                dailyWages = calculateDailyWages(domain),
            ),
            metrics = MetricsUIState(
                completedDays = domain.completedDayMetrics.sortedByDescending { it.dayNumber },
                showEndOfDayReport = domain.showEndOfDayReport,
                lastReport = domain.lastEndOfDayReport,
            ),
            progression = buildProgressionUiState(domain, oldUi?.progression),
        )
    }

    // ✅ Performance Optimization #1: Detect structural changes in domain state
    // Returns true only if significant fields changed, preventing unnecessary UI rebuilds
    private fun shouldRebuildUiState(newDomainState: GameState): Boolean {
        val oldDomainState = lastDomainState
        
        // First time - always rebuild
        oldDomainState ?: return true
        
        // Check only critical fields that affect UI rendering
        // (Skip checking fields that don't affect the UI)
        return newDomainState.money != oldDomainState.money ||
               newDomainState.inventory != oldDomainState.inventory ||
               newDomainState.currentTime != oldDomainState.currentTime ||
               newDomainState.currentTransaction != oldDomainState.currentTransaction ||
               newDomainState.hiredEntityRegistry != oldDomainState.hiredEntityRegistry ||
               newDomainState.storeState != oldDomainState.storeState ||
               newDomainState.storeName != oldDomainState.storeName ||
               newDomainState.transactionActive != oldDomainState.transactionActive ||
               newDomainState.totalTransactionsCompleted != oldDomainState.totalTransactionsCompleted ||
               newDomainState.totalTaxCollected != oldDomainState.totalTaxCollected ||
               newDomainState.salesHistory != oldDomainState.salesHistory ||
               newDomainState.pendingRefunds != oldDomainState.pendingRefunds ||
               newDomainState.playerPausedTime != oldDomainState.playerPausedTime ||
               newDomainState.playerRole != oldDomainState.playerRole ||
               newDomainState.pendingCustomers != oldDomainState.pendingCustomers ||
               newDomainState.showEndOfDayReport != oldDomainState.showEndOfDayReport ||
               newDomainState.completedDayMetrics.size != oldDomainState.completedDayMetrics.size ||
               newDomainState.currentTier != oldDomainState.currentTier ||
               newDomainState.totalRevenue != oldDomainState.totalRevenue ||
               newDomainState.currentStoreSize != oldDomainState.currentStoreSize
    }
}

