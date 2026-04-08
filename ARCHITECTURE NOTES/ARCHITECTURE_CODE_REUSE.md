# Architecture Analysis: Code Reuse & Design Patterns

**Date**: April 2, 2026  
**Scope**: DRY violations, repeated patterns, and architectural improvements to reduce code reuse

---

## Current Architecture Overview

The Superstore Simulator currently has **moderate code duplication** across three main areas:

1. **GameEngine** - Monolithic god object (332 lines)
2. **TransactionEngine** - Transaction-specific logic (368 lines)
3. **State management** - Repeated UI state transformations

This analysis identifies these patterns and recommends refactoring using proven architectural patterns.

---

## Problem #1: God Object Anti-Pattern (GameEngine)

### Current Implementation

**GameEngine.kt** (332 lines) handles:
- ✅ Inventory operations (stockItemFromBackroom, buyItemToBackroom, buyItemCasePacks)
- ✅ Transaction operations (startTransaction, ringUpItem, processRefund)
- ✅ Staff management (hireEntity, upgradeEntity, fireEntity)
- ✅ Time management (tick, handleStoreStateChange, setGameSpeed)
- ✅ Store operations (updateStoreName, toggleTimePaused)
- ✅ Database access (getDbItem)

```kotlin
class GameEngine(private val itemDao: ItemDao) {
    // 332 lines of mixed concerns
    fun stockItemFromBackroom(itemId: Int) { /* ... */ }
    fun buyItemToBackroom(itemId: Int) { /* ... */ }
    fun startTransaction() { /* ... */ }
    fun ringUpItem(itemId: Int) { /* ... */ }
    fun hireEntity(def: EntityDef, type: EntityType) { /* ... */ }
    fun fireEntity(entityId: Int) { /* ... */ }
    fun tick(deltaMilliseconds: Long) { /* ... */ }
    // ... 15+ more methods
}
```

### Issues

1. **Single Responsibility Violation**: Mixes 6 different concerns
2. **Difficult to Test**: Every test must mock entire GameEngine
3. **Hard to Extend**: Adding Phase 2 autonomous customers will add 50+ lines to already-complex tick()
4. **Duplicate Logic**: Inventory updates repeated across 3 methods
5. **Hard to Reason About**: 332 lines of sequential method calls in main game loop

---

## Recommended Solution: Service Layer Pattern

### Architecture Diagram

```
GameEngine (Facade)
├── InventoryService
│   ├── stockItemFromBackroom()
│   ├── buyItemToBackroom()
│   └── updateInventorySnapshot()
├── TransactionService
│   ├── startTransaction()
│   ├── ringUpItem()
│   └── processRefund()
├── StaffService
│   ├── hireEntity()
│   ├── upgradeEntity()
│   └── fireEntity()
├── TimeService
│   ├── tick()
│   ├── handleStoreStateChange()
│   └── setGameSpeed()
└── StoreService
    ├── updateStoreName()
    └── toggleTimePaused()
```

### Implementation

#### 1. InventoryService

```kotlin
// domain/services/InventoryService.kt
interface InventoryService {
    fun stockItemFromBackroom(state: GameState, itemId: Int): GameState
    fun stockCasePackFromBackroom(state: GameState, itemId: Int): GameState
    fun buyItemToBackroom(state: GameState, itemId: Int): GameState
    fun buyItemCasePacks(state: GameState, itemId: Int, numCasePacks: Int): GameState
    fun consumeShelfStock(state: GameState, itemId: Int): GameState
}

class InventoryServiceImpl(
    private val itemDao: ItemDao
) : InventoryService {
    
    private fun getDbItem(itemId: Int): Item? {
        // Could be cached or loaded from DB
    }
    
    override fun stockItemFromBackroom(state: GameState, itemId: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        if (dyn.backroomStock <= 0) return state
        
        val updated = dyn.copy(
            shelfStock = dyn.shelfStock + 1,
            backroomStock = dyn.backroomStock - 1
        )
        return state.copy(inventory = state.inventory + (itemId to updated))
    }
    
    override fun stockCasePackFromBackroom(state: GameState, itemId: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        val dbItem = getDbItem(itemId) ?: return state
        if (dyn.backroomStock <= 0) return state
        
        val casePackSize = dbItem.casePack
        val itemsToStock = minOf(casePackSize, dyn.backroomStock)
        
        val updated = dyn.copy(
            shelfStock = dyn.shelfStock + itemsToStock,
            backroomStock = dyn.backroomStock - itemsToStock
        )
        return state.copy(inventory = state.inventory + (itemId to updated))
    }
    
    override fun buyItemToBackroom(state: GameState, itemId: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        val dbItem = getDbItem(itemId) ?: return state
        val casePackCost = dbItem.getCasePackCostAsMoney()
        
        if (state.money < casePackCost) return state
        
        val itemsInCasePack = dbItem.casePack
        val updated = dyn.copy(backroomStock = dyn.backroomStock + itemsInCasePack)
        
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            money = state.money - casePackCost
        )
    }
    
    override fun buyItemCasePacks(state: GameState, itemId: Int, numCasePacks: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        val dbItem = getDbItem(itemId) ?: return state
        if (numCasePacks <= 0) return state
        
        val casePackCost = dbItem.getCasePackCostAsMoney()
        val totalCost = casePackCost * numCasePacks
        if (state.money < totalCost) return state
        
        val totalItemsAdded = dbItem.casePack * numCasePacks
        val updated = dyn.copy(backroomStock = dyn.backroomStock + totalItemsAdded)
        
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            money = state.money - totalCost
        )
    }
    
    override fun consumeShelfStock(state: GameState, itemId: Int): GameState {
        val dyn = state.inventory[itemId] ?: return state
        if (dyn.shelfStock <= 0) return state
        
        val updated = dyn.copy(shelfStock = dyn.shelfStock - 1)
        return state.copy(inventory = state.inventory + (itemId to updated))
    }
}
```

#### 2. TransactionService

```kotlin
// domain/services/TransactionService.kt
interface TransactionService {
    fun startTransaction(state: GameState): GameState
    fun ringUpItem(state: GameState, itemId: Int): GameState
    fun processRefund(state: GameState, refundId: Int): GameState
    fun processRefundLine(state: GameState, refundId: Int, itemId: Int, qty: Int): GameState
}

class TransactionServiceImpl(
    private val transactionEngine: TransactionEngine,
    private val inventoryService: InventoryService
) : TransactionService {
    
    override fun startTransaction(state: GameState): GameState {
        if (!state.transactionActive) {
            return transactionEngine.startNewTransaction(state)
        }
        if (state.storeState != StoreState.CLOSED) {
            return transactionEngine.startNewTransaction(state)
        }
        return state
    }
    
    override fun ringUpItem(state: GameState, itemId: Int): GameState {
        // Combine inventory check + transaction update
        val inventoryUpdated = inventoryService.consumeShelfStock(state, itemId)
        return transactionEngine.ringUpSingleItem(inventoryUpdated, itemId)
    }
    
    override fun processRefund(state: GameState, refundId: Int): GameState {
        return transactionEngine.processRefund(state, refundId)
    }
    
    override fun processRefundLine(state: GameState, refundId: Int, itemId: Int, qty: Int): GameState {
        return transactionEngine.processRefundLine(state, refundId, itemId, qty)
    }
}
```

#### 3. StaffService

```kotlin
// domain/services/StaffService.kt
interface StaffService {
    fun hireEntity(state: GameState, def: EntityDef, type: EntityType): GameState
    fun upgradeEntity(state: GameState, entityId: Int): GameState
    fun fireEntity(state: GameState, entityId: Int): GameState
}

class StaffServiceImpl : StaffService {
    
    override fun hireEntity(state: GameState, def: EntityDef, type: EntityType): GameState {
        val cost = def.cost
        if (state.money < cost) return state
        
        val updatedRegistry = state.hiredEntityRegistry.hireEntity(def, type)
        return state.copy(
            hiredEntityRegistry = updatedRegistry,
            money = state.money - cost
        )
    }
    
    override fun upgradeEntity(state: GameState, entityId: Int): GameState {
        val entity = state.hiredEntityRegistry.getById(entityId)
        val cost = entity.entityDefinition.nextUpgrade?.cost ?: Money(0)
        if (state.money < cost) return state
        
        val updatedRegistry = state.hiredEntityRegistry.upgradeEntity(entityId)
        return state.copy(
            hiredEntityRegistry = updatedRegistry,
            money = state.money - cost
        )
    }
    
    override fun fireEntity(state: GameState, entityId: Int): GameState {
        val updatedRegistry = state.hiredEntityRegistry.fireEntity(entityId)
        return state.copy(hiredEntityRegistry = updatedRegistry)
    }
}
```

#### 4. Refactored GameEngine (Now 100 lines instead of 332)

```kotlin
// domain/GameEngine.kt (Facade Pattern)
class GameEngine(
    private val itemDao: ItemDao,
    private val inventoryService: InventoryService,
    private val transactionService: TransactionService,
    private val staffService: StaffService,
    private val timeService: TimeService,
) {
    private val txEngine = TransactionEngine()
    private val timeManager = TimeManager()
    
    var state = GameState(...)
        private set
    
    suspend fun initialize() {
        // Load items
    }
    
    fun currentState(): GameState = state
    
    // Delegated methods (thin wrappers)
    fun stockItemFromBackroom(itemId: Int) {
        state = inventoryService.stockItemFromBackroom(state, itemId)
    }
    
    fun buyItemToBackroom(itemId: Int) {
        state = inventoryService.buyItemToBackroom(state, itemId)
    }
    
    fun ringUpItem(itemId: Int) {
        state = transactionService.ringUpItem(state, itemId)
    }
    
    fun hireEntity(def: EntityDef, type: EntityType) {
        state = staffService.hireEntity(state, def, type)
    }
    
    fun tick(deltaMilliseconds: Long) {
        state = timeService.tick(state, deltaMilliseconds)
    }
}
```

### Benefits

✅ **Single Responsibility**: Each service handles one concern  
✅ **Testable**: Mock individual services, not entire GameEngine  
✅ **Extensible**: Add Phase 2 autonomous customers without bloating GameEngine  
✅ **Reusable**: Services can be used by other systems (e.g., AI for staff actions)  
✅ **Clear Boundaries**: Each service has well-defined inputs/outputs  

---

## Problem #2: Repeated State Transformation Logic

### Current Implementation

**GameViewModel.kt** (lines 150-244) manually reconstructs UI state from domain state:

```kotlin
private fun toUiState(domain: GameState, oldUi: GameUiState): GameUiState {
    return GameUiState(
        app = AppUIState(...),
        dashboard = DashboardUIState(...),
        transactions = TransactionUIState(...),
        inventory = InventoryUIState(...),  // ← 20+ lines
        staff = StaffUIState(...),
        history = HistoryUIState(...),
        time = TimeUIState(...)
    )
}
```

And similar mapping logic happens in ItemViewModel for different views.

### Issues

1. **Repeated Mapping**: Same field extractions in multiple places
2. **Fragile**: Changes to GameState require updates in all mappers
3. **No Composition**: Can't reuse mapping functions
4. **Hard to Test**: Can't test UI state transformation logic independently

---

## Recommended Solution: Mapper/Projection Pattern

### Implementation

#### 1. Create Mapper Interfaces

```kotlin
// ui/state/mappers/UiStateMapper.kt
interface UiStateMapper {
    fun mapToUiState(domain: GameState, oldUi: GameUiState?): GameUiState
}

interface PartialUiStateMapper<T> {
    fun map(domain: GameState): T
}
```

#### 2. Implement Composable Mappers

```kotlin
// ui/state/mappers/AppUiStateMapper.kt
class AppUiStateMapper : PartialUiStateMapper<AppUIState> {
    override fun map(domain: GameState): AppUIState {
        return AppUIState(
            storeName = domain.storeName,
            transactionActive = domain.transactionActive,
            pendingRefunds = domain.pendingRefunds.size,
            money = domain.money,
        )
    }
}

// ui/state/mappers/DashboardUiStateMapper.kt
class DashboardUiStateMapper : PartialUiStateMapper<DashboardUIState> {
    override fun map(domain: GameState): DashboardUIState {
        return DashboardUIState(
            money = domain.money,
            totalStaff = domain.hiredEntityRegistry.totalCount()
        )
    }
}

// ui/state/mappers/TransactionUiStateMapper.kt
class TransactionUiStateMapper : PartialUiStateMapper<TransactionUIState> {
    override fun map(domain: GameState): TransactionUIState {
        return TransactionUIState(
            current = domain.currentTransaction,
            totalCompleted = domain.totalTransactionsCompleted,
            isActive = domain.transactionActive,
            isDialogOpen = false,  // Preserve from old state
            pendingRefunds = domain.pendingRefunds,
        )
    }
}

// ui/state/mappers/InventoryUiStateMapper.kt
class InventoryUiStateMapper(
    private val itemMetadataCache: ItemMetadataCache
) : PartialUiStateMapper<InventoryUIState> {
    
    override fun map(domain: GameState): InventoryUIState {
        val items = domain.inventory.map { (itemId, dyn) ->
            val meta = itemMetadataCache.get(itemId) ?: fallbackMetadata(itemId)
            InventoryItemUI(
                id = itemId,
                name = meta.name,
                price = meta.price,
                unitCost = meta.unitCost,
                shelfStock = dyn.shelfStock,
                backroomStock = dyn.backroomStock,
                category = meta.category,
                casePack = meta.casePack,
                casePackCost = meta.casePackCost,
            )
        }
        return InventoryUIState(items = items)
    }
    
    private fun fallbackMetadata(itemId: Int): ItemMetadata {
        return ItemMetadata(
            id = itemId,
            name = "Item $itemId",
            price = Money.fromDollars(9.99),
            // ... other defaults
        )
    }
}

// ui/state/mappers/CompositeUiStateMapper.kt
class CompositeUiStateMapper(
    private val appMapper: PartialUiStateMapper<AppUIState>,
    private val dashboardMapper: PartialUiStateMapper<DashboardUIState>,
    private val transactionMapper: PartialUiStateMapper<TransactionUIState>,
    private val inventoryMapper: PartialUiStateMapper<InventoryUIState>,
    private val staffMapper: PartialUiStateMapper<StaffUIState>,
    private val historyMapper: PartialUiStateMapper<HistoryUIState>,
    private val timeMapper: PartialUiStateMapper<TimeUIState>,
) : UiStateMapper {
    
    override fun mapToUiState(domain: GameState, oldUi: GameUiState?): GameUiState {
        return GameUiState(
            app = appMapper.map(domain),
            dashboard = dashboardMapper.map(domain),
            transactions = transactionMapper.map(domain),
            inventory = inventoryMapper.map(domain),
            staff = staffMapper.map(domain),
            history = historyMapper.map(domain),
            time = timeMapper.map(domain),
        )
    }
}
```

#### 3. Inject in ViewModel

```kotlin
// ui/viewmodels/GameViewModel.kt
@HiltViewModel
class GameViewModel @Inject constructor(
    private val itemDao: ItemDao,
    @ApplicationContext private val context: Context,
    @param:TickDelta private val tickDelta: Long = 16,
    private val uiStateMapper: UiStateMapper,  // ✅ Injected
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<GameUiState?>(null)
    val uiState = _uiState.asStateFlow()
    
    fun onEvent(event: GameEvent) {
        when (event) {
            GameEvent.Tick -> gameEngine.tick(tickDelta)
            // ... other events ...
        }
        
        if (gameEngineInitialized) {
            _uiState.value = uiStateMapper.mapToUiState(
                gameEngine.currentState(),
                _uiState.value
            )
        }
    }
}
```

#### 4. Hilt Module for Mappers

```kotlin
// di/MapperModule.kt
@Module
@InstallIn(SingletonComponent::class)
object MapperModule {
    
    @Provides
    @Singleton
    fun provideAppUiStateMapper(): PartialUiStateMapper<AppUIState> {
        return AppUiStateMapper()
    }
    
    @Provides
    @Singleton
    fun provideDashboardUiStateMapper(): PartialUiStateMapper<DashboardUIState> {
        return DashboardUiStateMapper()
    }
    
    // ... other mappers ...
    
    @Provides
    @Singleton
    fun provideCompositeUiStateMapper(
        appMapper: PartialUiStateMapper<AppUIState>,
        dashboardMapper: PartialUiStateMapper<DashboardUIState>,
        transactionMapper: PartialUiStateMapper<TransactionUIState>,
        inventoryMapper: PartialUiStateMapper<InventoryUIState>,
        staffMapper: PartialUiStateMapper<StaffUIState>,
        historyMapper: PartialUiStateMapper<HistoryUIState>,
        timeMapper: PartialUiStateMapper<TimeUIState>,
    ): UiStateMapper {
        return CompositeUiStateMapper(
            appMapper,
            dashboardMapper,
            transactionMapper,
            inventoryMapper,
            staffMapper,
            historyMapper,
            timeMapper,
        )
    }
}
```

### Benefits

✅ **DRY**: Each mapper handles one state slice  
✅ **Testable**: Test each mapper independently  
✅ **Composable**: Reuse mappers across different ViewModels  
✅ **Maintainable**: Changes to mapping logic isolated to one mapper  
✅ **Type-safe**: Compiler checks all mappers are provided  

---

## Problem #3: Duplicate Inventory Update Logic

### Current Implementation

Inventory updates appear in 3 places with similar patterns:

```kotlin
// GameEngine.kt
fun stockItemFromBackroom(itemId: Int) {
    val dyn = state.inventory[itemId] ?: return
    if (dyn.backroomStock <= 0) return
    
    val updated = dyn.copy(
        shelfStock = dyn.shelfStock + 1,
        backroomStock = dyn.backroomStock - 1
    )
    state = state.copy(inventory = state.inventory + (itemId to updated))
}

// TransactionEngine.kt
private fun consumeShelfStock(
    inventory: Map<Int, InventoryState>,
    itemId: Int
): Map<Int, InventoryState> {
    val dyn = inventory[itemId] ?: return inventory
    val updated = dyn.copy(shelfStock = dyn.shelfStock - 1)
    return inventory + (itemId to updated)
}

// ItemViewModel.kt (implicit in business logic)
// Similar pattern for updating inventory display
```

---

## Recommended Solution: Builder/DSL Pattern

```kotlin
// domain/inventory/InventoryBuilder.kt
class InventoryBuilder(private val inventory: Map<Int, InventoryState>) {
    
    fun moveToShelf(itemId: Int, quantity: Int = 1): InventoryBuilder {
        val dyn = inventory[itemId] ?: return this
        if (dyn.backroomStock < quantity) return this  // Not enough stock
        
        val updated = dyn.copy(
            shelfStock = dyn.shelfStock + quantity,
            backroomStock = dyn.backroomStock - quantity
        )
        val newMap = inventory + (itemId to updated)
        return InventoryBuilder(newMap)
    }
    
    fun moveToBackroom(itemId: Int, quantity: Int = 1): InventoryBuilder {
        val dyn = inventory[itemId] ?: return this
        if (dyn.shelfStock < quantity) return this  // Not enough stock
        
        val updated = dyn.copy(
            shelfStock = dyn.shelfStock - quantity,
            backroomStock = dyn.backroomStock + quantity
        )
        val newMap = inventory + (itemId to updated)
        return InventoryBuilder(newMap)
    }
    
    fun addToBackroom(itemId: Int, quantity: Int): InventoryBuilder {
        val dyn = inventory[itemId] ?: InventoryState(0, 0)
        val updated = dyn.copy(backroomStock = dyn.backroomStock + quantity)
        val newMap = inventory + (itemId to updated)
        return InventoryBuilder(newMap)
    }
    
    fun consumeShelf(itemId: Int): InventoryBuilder {
        return moveToBackroom(itemId, 1)
    }
    
    fun build(): Map<Int, InventoryState> = inventory
}

// Usage in GameEngine:
fun stockItemFromBackroom(itemId: Int) {
    state = state.copy(
        inventory = InventoryBuilder(state.inventory)
            .moveToShelf(itemId, 1)
            .build()
    )
}

// Usage in TransactionEngine:
fun ringUpItem(state: GameState, itemId: Int): GameState {
    val newInventory = InventoryBuilder(state.inventory)
        .consumeShelf(itemId)
        .build()
    return state.copy(inventory = newInventory)
}

// Usage in InventoryService:
fun stockCasePackFromBackroom(state: GameState, itemId: Int): GameState {
    val dbItem = getDbItem(itemId) ?: return state
    val casePackSize = dbItem.casePack
    
    return state.copy(
        inventory = InventoryBuilder(state.inventory)
            .moveToShelf(itemId, casePackSize)
            .build()
    )
}
```

### Benefits

✅ **DRY**: Single source of truth for inventory operations  
✅ **Fluent**: Readable, chainable API  
✅ **Type-safe**: Compiler catches invalid operations  
✅ **Immutable**: Each operation returns new builder (no side effects)  

---

## Problem #4: Duplicate Stock Action Logic

### Current Implementation (GameEngine.kt, lines 260-280)

```kotlin
private fun stockRandomItemFromBackroom() {
    val candidates = state.inventory
        .filter { (_, dyn) -> dyn.backroomStock > 0 }
    
    if (candidates.isEmpty()) return
    
    val lowestShelf = candidates.minOf { it.value.shelfStock }
    val lowestGroup = candidates.filter { it.value.shelfStock == lowestShelf }
    val targetId = lowestGroup.keys.random()
    
    stockCasePackFromBackroom(targetId)
}
```

This logic for "find lowest-stocked item and stock it" will be duplicated in:
- Phase 2 autonomous stocker actions
- Staff AI for inventory management
- Optimization algorithms

### Recommended Solution: Strategy Pattern

```kotlin
// domain/inventory/StockingStrategy.kt
interface StockingStrategy {
    fun selectItemToStock(state: GameState): Int?
}

class LowestShelfStockingStrategy : StockingStrategy {
    override fun selectItemToStock(state: GameState): Int? {
        val candidates = state.inventory
            .filter { (_, dyn) -> dyn.backroomStock > 0 }
        
        if (candidates.isEmpty()) return null
        
        val lowestShelf = candidates.minOf { it.value.shelfStock }
        val lowestGroup = candidates.filter { it.value.shelfStock == lowestShelf }
        return lowestGroup.keys.randomOrNull()
    }
}

class HighestUnitCostStrategy(
    private val itemDao: ItemDao
) : StockingStrategy {
    override fun selectItemToStock(state: GameState): Int? {
        val candidates = state.inventory
            .filter { (_, dyn) -> dyn.backroomStock > 0 }
        
        if (candidates.isEmpty()) return null
        
        // Stock highest-value items first (maximize revenue)
        return candidates.maxByOrNull { (itemId, _) ->
            itemDao.getItemById(itemId)?.price?.cents ?: 0L
        }?.key
    }
}

class RandomStockingStrategy : StockingStrategy {
    override fun selectItemToStock(state: GameState): Int? {
        val candidates = state.inventory
            .filter { (_, dyn) -> dyn.backroomStock > 0 }
        return candidates.keys.randomOrNull()
    }
}
```

**Usage**:

```kotlin
class StaffService(
    private val stockingStrategy: StockingStrategy = LowestShelfStockingStrategy()
) : StaffService {
    
    fun performStockAction(state: GameState, stockerId: Int): GameState {
        val itemToStock = stockingStrategy.selectItemToStock(state) ?: return state
        return inventoryService.stockCasePackFromBackroom(state, itemToStock)
    }
}
```

### Benefits

✅ **DRY**: Stocking logic defined once  
✅ **Extensible**: Swap strategies without changing GameEngine  
✅ **Testable**: Mock different strategies  
✅ **Phase 2 Ready**: Autonomous stockers can use different strategy than player-controlled staff  

---

## Problem #5: Repeated Validation Logic

### Current Implementation

Money validation checks scattered throughout code:

```kotlin
// GameEngine.kt
fun hireEntity(def: EntityDef, type: EntityType) {
    val cost = def.cost
    if (state.money < cost) return  // ❌ Validation #1
    // ...
}

fun buyItemToBackroom(itemId: Int) {
    val casePackCost = dbItem.getCasePackCostAsMoney()
    if (state.money < casePackCost) return  // ❌ Validation #2
    // ...
}

fun buyItemCasePacks(itemId: Int, numCasePacks: Int) {
    val totalCost = casePackCost * numCasePacks
    if (state.money < totalCost) return  // ❌ Validation #3
    // ...
}
```

### Recommended Solution: Command Pattern with Validation

```kotlin
// domain/commands/GameCommand.kt
sealed class GameCommand {
    abstract val requiredMoney: Money
    abstract fun execute(state: GameState, engine: GameEngine): GameState
    
    fun canExecute(state: GameState): Boolean {
        return state.money >= requiredMoney && validatePreconditions(state)
    }
    
    protected open fun validatePreconditions(state: GameState): Boolean = true
}

// Specific commands
class HireEntityCommand(
    val entityDef: EntityDef,
    val entityType: EntityType
) : GameCommand() {
    
    override val requiredMoney: Money = entityDef.cost
    
    override fun validatePreconditions(state: GameState): Boolean {
        // Additional validation
        return true
    }
    
    override fun execute(state: GameState, engine: GameEngine): GameState {
        val updatedRegistry = state.hiredEntityRegistry.hireEntity(entityDef, entityType)
        return state.copy(
            hiredEntityRegistry = updatedRegistry,
            money = state.money - entityDef.cost
        )
    }
}

class BuyItemCommand(
    val itemId: Int,
    val numCasePacks: Int = 1
) : GameCommand() {
    
    override val requiredMoney: Money = Money(0)  // Calculated dynamically
    
    override fun validatePreconditions(state: GameState): Boolean {
        return state.inventory.containsKey(itemId)
    }
    
    override fun execute(state: GameState, engine: GameEngine): GameState {
        val dbItem = engine.getDbItem(itemId) ?: return state
        val totalCost = dbItem.getCasePackCostAsMoney() * numCasePacks
        
        if (state.money < totalCost) return state
        
        val totalItemsAdded = dbItem.casePack * numCasePacks
        val dyn = state.inventory[itemId] ?: return state
        val updated = dyn.copy(backroomStock = dyn.backroomStock + totalItemsAdded)
        
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            money = state.money - totalCost
        )
    }
}

// CommandExecutor
class CommandExecutor(private val gameEngine: GameEngine) {
    fun execute(command: GameCommand, currentState: GameState): Result<GameState> {
        return if (command.canExecute(currentState)) {
            Result.success(command.execute(currentState, gameEngine))
        } else {
            Result.failure(IllegalStateException("Command preconditions not met"))
        }
    }
}
```

**Usage**:

```kotlin
val executor = CommandExecutor(gameEngine)

val hireResult = executor.execute(
    HireEntityCommand(EntityDef.CASHIER, EntityType.CASHIERS),
    gameEngine.currentState()
)

hireResult.onSuccess { newState ->
    gameEngine.setState(newState)
}.onFailure { error ->
    showError("Cannot hire: ${error.message}")
}
```

### Benefits

✅ **DRY**: Validation logic centralized  
✅ **Auditable**: Command pattern enables undo/redo  
✅ **Testable**: Test commands independently  
✅ **Extensible**: Add new commands without modifying GameEngine  
✅ **Phase 2 Ready**: AI staff can execute commands with proper validation  

---

## Summary: Code Reuse Reduction

| Pattern | Before | After | Benefit |
|---------|--------|-------|---------|
| **God Object** | GameEngine (332 lines) | Services + GameEngine (100 lines) | ✅ Single responsibility |
| **State Mapping** | Repeated in ViewModel | Mapper pattern | ✅ DRY, testable |
| **Inventory Updates** | 3 places with same logic | InventoryBuilder | ✅ Centralized, fluent API |
| **Stocking Logic** | Duplicated in multiple services | Strategy pattern | ✅ Swappable, reusable |
| **Validation Logic** | Scattered throughout | Command pattern | ✅ Centralized, auditable |

---

## Implementation Priority

1. **Phase 1 (Next Sprint)**: 
   - Extract services from GameEngine (biggest impact)
   - Implement mapper pattern for UI state
   
2. **Phase 2 (Following Sprint)**:
   - InventoryBuilder for cleaner operations
   - Strategy pattern for stocking
   
3. **Phase 3 (Future)**:
   - Command pattern for full undo/redo support
   - EventSourcing for event-driven architecture


