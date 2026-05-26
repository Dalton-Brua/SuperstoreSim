# AGENTS.md - Superstore Simulator Codebase Guide
## 🎯 Quick Facts
- **Project**: Management simulation game (Android, Kotlin, Jetpack Compose)
- **Architecture**: Tick-based game loop, immutable state, MVVM with Hilt DI
- **Tech Stack**: Kotlin, Jetpack Compose, Room DB, Hilt, Coroutines, JaCoCo coverage
- **Package**: com.example.superstoresimulator | **Min SDK**: 24 | **Target**: 36
---
## 🏗️ Essential Architecture Patterns
### **1. The Three-Layer Data Pipeline**
Every frame follows this flow:

```
User Action (GameEvent)
    ↓ [GameViewModel.onEvent()]
GameEngine.method() → mutates GameState
    ↓ [Immutable copy()]
new GameState object
    ↓ [GameViewModel.toUiState()]
Compose receives GameUiState
    ↓ [Recomposition if state changed]
Screen updates
```

**Swipe Navigation** (April 16, 2026): Main screens use `HorizontalPager` for swipe-based navigation:
- Five main screens: GAME, INVENTORY, STAFF, HISTORY, METRICS
- Pager state synced bidirectionally with bottom nav bar and currentScreen
- StaffAndUnlocksScreen also uses internal `HorizontalPager` with "Staff"/"Unlocks" tabs
- Pattern: `LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress)` prevents sync loops
- Use `isNavigatingProgrammatically` flag to prevent infinite sync loops when tapping nav buttons

**Key Files**: `domain/GameEngine.kt` | `ui/viewmodels/GameViewModel.kt` | `ui/state/GameUiState.kt` | `MainActivity.kt` (pager implementation)
---
### **2. Batch-Based Inventory with Expiration** ⭐ IMPLEMENTED (May 2026)

**Problem Solved**: Simple integer stock tracking could not support item expiration, FIFO consumption, or perishable inventory management.

**Solution**: Batch-based inventory system where every delivery creates an `ItemBatch` with tracking metadata.

**Architecture**:
```
InventoryState {
    shelfBatches: List<ItemBatch>        // Items on display (consumed first)
    backroomBatches: List<ItemBatch>     // Reserve stock
}

ItemBatch {
    receivedDay: Int                      // When ordered
    quantity: Int                         // Units in this batch
    expirationDay: Int                    // receivedDay + shelfLifeDays (Int.MAX_VALUE for non-perishables)
}
```

**Key Operations**:
- **FIFO Consumption**: `oldestShelfBatch()` / `oldestBackroomBatch()` — always consume oldest batches first
- **Batch Merging**: Batches with same `expirationDay` are automatically merged to reduce memory overhead
- **Stocking**: `stockItemFromBackroom()` takes from oldest backroom batch, adds to shelf maintaining batch identity
- **Purchasing**: `buyItemToBackroom()` / `buyItemCasePacks()` create new batches with `expirationDay = currentDay + shelfLifeDays`

**Perishable Items**:
- `ItemMetadata.shelfLifeDays: Int?` — null = non-perishable (never expires)
- `ItemMetadata.isPerishable: Boolean` — computed property from `shelfLifeDays != null`
- **SpoilageManager** (`domain/expiration/SpoilageManager.kt`):
  - Called once per tick by GameEngine
  - Removes batches where `expirationDay <= currentDay`
  - Tracks waste in `DailyMetricsAccumulator.itemsExpired`, `expiredWasteCost`, `expiredItemEvents`
  - Waste cost calculated at `unitCost`, not sale price (reflects inventory loss)

**Breaking Rules for AI Agents**:
- ❌ Never access `InventoryState.shelfStock` or `backroomStock` directly as mutable integers — they are computed properties (`shelfBatches.sumOf { it.quantity }`)
- ❌ Never create `InventoryState` with `shelfStock: Int` constructor — use `shelfBatches: List<ItemBatch>`
- ❌ Never modify batch quantities in-place — always use `.copy()` to create new batches
- ✅ Always use `InventoryManager` methods for inventory operations (handles FIFO and batch merging)
- ✅ Fresh items MUST have `shelfLifeDays` set in item data; non-perishables should have `shelfLifeDays = null`
- ✅ When adding test inventory, create batches: `ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)` for non-perishables

**Daily Metrics Integration**:
- `ExpiredItemEvent(itemId, itemName, quantity, valueLost)` — one per item type that expired this day
- Displayed in end-of-day report "Shrinkage" section
- Future enhancement slots: paid disposal ($50/day), composting ($5k one-time + $10/day revenue) — see `EXTREME_FEATURES_PROPOSAL.md`

**See**: `domain/inventory/InventoryState.kt` (`ItemBatch` data class) | `domain/expiration/SpoilageManager.kt` | `domain/inventory/InventoryManager.kt` (FIFO stocking) | `domain/metrics/DailyMetrics.kt` (`ExpiredItemEvent`) | `domain/items/ItemMetadata.kt` (`shelfLifeDays`, `isPerishable`)

---
### **3. Immutable State with Copy Semantics**
GameState is a data class. All updates use `.copy()`:

```kotlin
// CORRECT: Create new object
state = state.copy(
    inventory = state.inventory + (itemId to newInventoryState),
    money = state.money + transaction.total
)

// WRONG: Direct mutation violates pattern
state.inventory[itemId] = newInventoryState
```

**Why**: Enables time-travel debugging, prevents accidental side effects, makes Compose recomposition predictable.
---
### **4A. ItemMetadataCache: Single Source of Truth for Item Data** ⭐ NEW (April 3, 2026)

**Problem Solved**: Before this fix, the codebase made 3+ redundant `getAllItems()` database calls:
1. `ItemMetadataCache.initialize()` → loads for UI inventory mapping
2. `GameEngine` init block → loads to populate `dbItems` map
3. `InventoryScreen` → LaunchedEffect calling `itemDao.getAllItemsWithNames()`

**Solution**: Consolidate into single `ItemMetadataCache` with 3 cache layers:

```kotlin
// Single database load point (called ONCE on app startup)
itemMetadataCache.initialize() // calls itemDao.getAllItems() once

// Cache layers built from single load:
metadataCache: Map<Int, ItemMetadata>          // For fast UI rendering (name, price, category)
itemsCache: Map<Int, Item>                     // Full Item objects for GameEngine
itemNamesCache: Map<Int, String>               // For InventoryScreen, no separate query needed
```

**Architecture**:
```
GameViewModel.itemMetadataCache.initialize()
    ↓ (SINGLE DB CALL)
Populates 3 caches
    ↓
GameEngine(itemMetadataCache)        // No extra DB load
InventoryScreen(itemMetadataCache)   // No extra DB load
MemoizedInventoryMapper(cache)       // No extra DB load
```

**Key Methods**:
- `initialize()`: Async-safe, calls `itemDao.getAllItems()` once, idempotent
- `get(itemId)`: Returns ItemMetadata (O(1))
- `getItem(itemId)`: Returns full Item for GameEngine (O(1))
- `getAllItemNames()`: Returns Map<Int, String> for InventoryScreen
- `getAllItems()`: Returns Map<Int, Item> for GameEngine init

**Breaking Changes for AI Agents**:
- ❌ Never call `itemDao.getAllItems()` directly in components
- ❌ Never call `itemDao.getAllItemsWithNames()` from UI screens
- ✅ Always go through `itemMetadataCache.get()`, `getItem()`, `getAllItemNames()`, or `getAllItems()`
- ✅ GameEngine constructor now takes `ItemMetadataCache` instead of `ItemDao`
- ✅ Test mocks now mock `ItemMetadataCache` instead of `ItemDao`

**Performance Impact**:
- **Before**: 3 full `getAllItems()` calls × ~60 items per app launch = 180 item records loaded redundantly
- **After**: 1 `getAllItems()` call = 60 item records loaded
- **Improvement**: 2 redundant DB calls eliminated

**See**: `domain/items/ItemMetadataCache.kt` | Updated: `GameEngine.kt` | `GameViewModel.kt` | `InventoryScreen.kt` | All test files

---
### **4B. Service-Layer Manager Architecture** ⭐ IMPLEMENTED (April 15, 2026)

**Problem Solved**: GameEngine had grown to 400+ lines handling concerns across 6+ domains (inventory, staff, progression, day metrics, store state, player actions). Maintenance and testing were difficult.

**Solution**: Extract pure sub-systems into dedicated managers. GameEngine now orchestrates them via a **thin facade** pattern.

**Public vs. Private Methods**: Managers only expose methods that GameEngine calls from ViewModel-routed engine methods. All internal helpers (e.g., `TransactionEngine.ringUpSingleItem()`, `InventoryManager.stockCasePackFromBackroom()`) remain private. ❌ Never call private manager methods from outside code — always route through GameEngine.

**Architecture**:
```
GameViewModel.onEvent(event)
    ↓
Calls GameEngine public methods
    ↓
GameEngine routes to specialized managers:
  ├─ ProgressionManager.unlockNextTier()
  ├─ StaffManager.{hireEntity, upgradeEntity, fireEntity, advanceFreshHandlerProgress}
  ├─ StoreController.{toggleTimePaused, updateStoreName, setGameSpeedState, upgradeStoreSize}
  ├─ InventoryManager.{stockItemFromBackroom, buyItemToBackroom, placeBulkOrder, placeFreshBulkOrder, queueFreshOrder, shouldAutoOrderFreshItem}
  ├─ PlayerActionHandler.{calculateCashierWork, calculateStockerWork, setPlayerRole}
  ├─ DayManager.{rollOverDay, dismissEndOfDayReport}
  ├─ TrafficManager.update()
  ├─ TransactionEngine.{ringUpSingleItem, processRefund, etc}
  └─ SpoilageManager.processExpiration()
    ↓
Each returns new GameState via .copy()
    ↓
GameEngine.state = newState
Emit change via _changes.emit(...)
```

**Key Managers**:

1. **ProgressionManager** (`domain/progression/ProgressionManager.kt`)
   - `unlockNextTier(state)` → new state with advanced tier, deducted cost

3. **StaffManager** (`domain/staff/StaffManager.kt`)
   - `hireEntity(state, def, type)` → new state with hired entity
   - `upgradeEntity(state, entityId)` → new state with upgraded entity
   - `fireEntity(state, entityId)` → new state with dismissed entity
   - `advanceCashierProgress(cashierCount, deltaSeconds, multiplier)` → `Int` actions to perform this tick
   - `advanceStockerProgress(stockerCount, deltaSeconds, multiplier)` → `Int` actions to perform this tick
   - `advanceFreshHandlerProgress(freshHandlerCount, deltaSeconds, multiplier)` → `Int` actions to perform this tick (fresh handlers stock perishable items only)
   - Fractional accumulators (`cashierProgress`, `stockerProgress`, `freshHandlerProgress`) are **outside GameState** — internal engine counters, not player-visible

3. **StoreController** (`domain/store/StoreController.kt`)
   - `updateStoreName(state, newName)` → new state
   - `setGameSpeedState(state, multiplier)` → new state (updates `storeConfig.gameSpeedMultiplier`)
   - `upgradeStoreSize(state)` → new state (deducts `nextSize.upgradeCost`, updates `currentStoreSize`, syncs `storeConfig.backroomCapPerItem`)
   - **StoreSize tiers** (`domain/store/StoreSize.kt`): `MOM_AND_POP` ($100/day rent, cap 2) → `SMALL_GROCERY` ($300/day, $1k upgrade, cap 5, 2×traffic) → `GROCERY_STORE` ($800/day, $20k upgrade, cap 10, 4×traffic) → `SUPERSTORE` ($1500/day, $200k upgrade, cap 30, 8×traffic) → `SUPERCENTER` ($3000/day, $1M upgrade, cap 999, 16×traffic)
   - `maxDeliveryDaysAllowed = 2 + storeSize.ordinal + extraTruckSlotsUnlocked` (store upgrade increases free delivery days)
   - `handleStoreStateChange(state, newState, trafficManager)` → new state with side-effects (closes customers on CLOSED transition, resets traffic)
   - `toggleTimePaused(state)` → new state (flips `playerPausedTime` flag)

5. **InventoryManager** (`domain/inventory/InventoryManager.kt`)
   - `stockItemFromBackroom(state, itemId)` → new state (FIFO batch consumption)
   - `stockCasePackFromBackroom(state, itemId)` → new state
   - `stockRandomItemFromBackroom(state)` → new state (excludes fresh items; used by regular stockers)
   - `stockRandomFreshItemFromBackroom(state)` → new state (only fresh items; used by fresh handlers)
   - `buyItemToBackroom(state, itemId)` → **`BuyResult`** (deducts money; returns `orderLines` for TruckManager; refuses if total committed case packs ≥ cap)
   - `buyItemCasePacks(state, itemId, casePacks)` → **`BuyResult`** (deducts money; returns `orderLines`; supports multi-truck pre-ordering up to `MAX_TRUCKS_AHEAD(2) × cap`)
   - `placeBulkOrder(state, maxTotalQty, casePacksPerItem, categoryFilter)` → **`BuyResult`** with volume discounts applied (excludes fresh items)
   - `placeFreshBulkOrder(state, maxTotalQty, casePacksPerItem, currentTier)` → **`BuyResult`** (only fresh items; lower discount tiers)
   - `shouldAutoOrderFreshItem(state, itemId, config)` → Boolean (checks if fresh item below threshold)
   - ⚠️ `queueFreshOrder()` **no longer exists** — fresh orders now go directly through TruckManager
   - **In-transit cap**: `buyItemToBackroom` and `buyItemCasePacks` count scheduled truck orders toward the backroom cap to prevent spam-ordering during delivery

6. **PlayerActionHandler** (`domain/player/PlayerActionHandler.kt`)
   - `setPlayerRole(state, role)` → new state (toggles active role back to `NONE`)
   - `calculateCashierWork(state, deltaSeconds)` → `PlayerWorkResult`
   - `calculateStockerWork(state, deltaSeconds)` → `PlayerWorkResult`

7. **DayManager** (`domain/metrics/DayManager.kt`)
   - `rollOverDay(state, dayNumber)` → new state with metrics snapshot appended, daily costs applied, and end-of-day report surfaced
   - `dismissEndOfDayReport(state)` → new state with flag cleared
   - **Mutable counter**: `lastKnownDayNumber` — engine-internal, not in GameState
   - ⚠️ DayManager no longer calls `processFreshOrders()` — fresh orders are placed immediately during tick via `attemptFreshHandlerAutoOrder()`, not at midnight

8. **SpoilageManager** (`domain/expiration/SpoilageManager.kt`)
   - `processExpiration(state)` → new state with expired batches removed and metrics updated
   - Called once per tick; checks all inventory for `batch.expirationDay <= currentDay`
   - Tracks waste in `currentDayMetrics.itemsExpired`, `expiredWasteCost`, `expiredItemEvents`
   - Pure function: no side effects, no mutable state

**Breaking Rules for AI Agents**:
- ❌ Never call manager methods directly — GameEngine public methods orchestrate them
- ❌ Never instantiate managers outside GameEngine — managers are created and owned by the engine
- ❌ Never mutate manager state (accumulators like `staffManager.cashierProgress`) — only GameEngine writes
- ❌ Never call private manager methods (e.g., `stockCasePackFromBackroom()`) from outside code — always route through GameEngine public methods
- ❌ Never call `InventoryManager.buyItemToBackroom()` / `buyItemCasePacks()` and discard `BuyResult.orderLines` — items will not arrive without TruckManager scheduling
- ✅ When reading GameEngine code, understand managers are pure: all state flows through GameState.copy()
- ✅ Managers are testable in isolation by passing mocked GameState
- ✅ Each manager knows ONE domain; never cross-call between managers

**See**: `domain/progression/` | `domain/staff/` | `domain/store/` | `domain/inventory/` | `domain/player/` | `domain/metrics/` | `domain/expiration/`

---
### **5. Save/Load System** ⭐ IMPLEMENTED (April 19, 2026)

Complete persistence system for saving and loading game state using SharedPreferences and JSON serialization.

**Architecture**:
```
App Lifecycle
    ↓
MainActivity.onPause() / onStop()
    ↓
GameViewModel.saveGameState()
    ↓
GameStateRepository.saveGameState(state)
    ↓
GameStateSerializer.serialize(state) → JSON string
    ↓
SharedPreferences.putString(json)
```

**Key Components**:
1. **GameStateSerializer** (`domain/persistence/GameStateSerializer.kt`)
   - Serializes entire GameState to JSON using org.json (built into Android)
   - Deserializes JSON back to GameState
   - Handles all nested objects: transactions, inventory, staff, metrics, etc.
   - Compatible with API 24+ (no java.time.Instant dependencies)

2. **GameStateRepository** (`domain/persistence/GameStateRepository.kt`)
   - Manages SharedPreferences for save/load
   - Methods: `saveGameState()`, `loadGameState()`, `hasSavedGame()`, `getLastSaveTime()`, `clearSave()`
   - Returns null if save doesn't exist or deserialization fails

3. **GameEngine.loadState()** (`domain/GameEngine.kt`)
   - Replaces current engine state with loaded state
   - Syncs TimeManager.currentTime and TimeManager.config to loaded values
   - **Explicitly sets speed multiplier via setSpeedMultiplier()** ⭐ CRITICAL
   - **Syncs DayManager.lastKnownDayNumber to prevent double day rollover** ⭐ CRITICAL
   - Resets StaffManager and TrafficManager accumulators (transient data)
   - Ensures game time continues from saved point, not from engine creation

4. **GameViewModel Integration**
   - Auto-loads saved game on app startup (in init block)
   - Public `saveGameState()` method for manual/automatic saves
   - `onCleared()` saves as fallback (may not be called if process killed)

5. **MainActivity Lifecycle Hooks** ⭐ CRITICAL
   - `onPause()` → saves game when app goes to background
   - `onStop()` → additional save as safety measure
   - This ensures saves happen even when app is swiped away from recents

**Trigger Points**:
- ✅ **Automatic on background**: MainActivity.onPause() / onStop()
- ✅ **Automatic on close**: GameViewModel.onCleared() (fallback)
- ✅ **Manual save button**: Settings panel → "Save Game" → GameEvent.SaveGame

**Breaking Rules for AI Agents**:
- ❌ Never rely solely on ViewModel.onCleared() — process may be killed before it's called
- ❌ Never serialize with Instant.toEpochMilli() — requires API 26+ (min is 24)
- ❌ Never add manager internal counters without syncing them in GameEngine.loadState()
- ✅ Always save in MainActivity lifecycle callbacks (onPause/onStop)
- ✅ Use viewModelScope.launch for save operations (non-blocking)
- ✅ Serializer returns null on failure — always handle gracefully
- ✅ When adding manager counters like `lastKnownDayNumber`, add sync/reset methods

**Pattern — Adding New GameState Fields**:
```kotlin
// 1. Add field to GameState data class
data class GameState(
    // ...existing fields...
    val newField: MyType = defaultValue
)

// 2. Update GameStateSerializer.serialize()
fun serialize(state: GameState): String {
    val json = JSONObject()
    // ...existing serialization...
    json.put("newField", serializeMyType(state.newField))
}

// 3. Update GameStateSerializer.deserialize()
GameState(
    // ...existing deserialization...
    newField = deserializeMyType(json.getJSONObject("newField"))
)

// 4. Add helper methods if needed
private fun serializeMyType(obj: MyType): JSONObject { ... }
private fun deserializeMyType(json: JSONObject): MyType { ... }
```

**Save File Location**: `/data/data/com.example.superstoresimulator/shared_prefs/superstore_save_data.xml`

**See**: `domain/persistence/GameStateSerializer.kt` | `domain/persistence/GameStateRepository.kt` | `MainActivity.kt` (lifecycle hooks) | `GameViewModel.kt` (auto-load/save)

---
### **6. GameEngine: The Core State Machine (Facade)**
- **Role**: Thin orchestrator — receives GameEvents, routes to appropriate managers, emits changes
- **Single source of truth**: `var state: GameState`
- **Lifecycle**: Async-initialized (not in constructor)
  - ItemDataLoader loads JSON → ItemDao → ItemMetadataCache initialized
  - GameViewModel waits for async completion before creating engine
- **Helper method**: `getDbItem(itemId: Int): Item?` — public access to cached item metadata for UI components (returns full Item object from ItemMetadataCache)
- **Managers** (April 15, 2026): Delegates to 9 specialized managers (see sections 4B and 20):
  - ProgressionManager, StaffManager, StoreController, InventoryManager, PlayerActionHandler, DayManager, TrafficManager, SpoilageManager, **TruckManager**
  - Each manager is pure: receives GameState, returns new GameState
  - Each manager owns one domain of logic; GameEngine coordinates
- **Database Access**: Passes `ItemMetadataCache` to managers that need item lookups (InventoryManager, SpoilageManager, TruckManager)
  - Single database load: `itemDao.getAllItems()` called ONCE in `itemMetadataCache.initialize()`
- **Fresh Auto-Ordering** (May 2026): Idle fresh handlers auto-order immediately via truck system when fresh items fall below threshold
  - `attemptFreshHandlerAutoOrder()` — called during tick when fresh handlers have no backroom stock to process; routes through `TruckManager.scheduleFreshOrderLines()`
  - ⚠️ `processFreshOrders()` **no longer exists** — orders are placed immediately, not queued for end-of-day
  - Incomplete orders (insufficient funds) tracked in `state.incompleteFreshOrders` — can be manually ordered via `orderIncompleteItem()`
- **Truck arrivals**: Processed at each day rollover in tick via `TruckManager.processArrivals(state, newDayNumber)`
- **Change Emission**: `_changes: StateFlow<GameStateChange?>` for incremental UI updates (99% fewer reconstructions)
  - `GameStateChange` subtypes: `MoneyChanged`, `InventoryUpdated`, `TransactionCompleted`, `TransactionStarted`, `RefundRequested`, `RefundProcessed`, `StaffUpdated`, `StoreStateChanged`, `TimeUpdated`, `TierUnlocked(newTier, previousTier)`, **`ItemsExpired(totalExpired, wasteCost)`**, **`OrderScheduled(arrivalDay)`**

**See**: `domain/GameEngine.kt`
---
### **7. ViewModel Projection: GameState → GameUiState**
- **GameViewModel** transforms domain state into UI-safe state
- **Lazy init**: Engine created AFTER items loaded (ItemDataLoader handles async)
- **Automatic Tick Loop**: Init block spawns a coroutine that dispatches `GameEvent.Tick` every `@TickDelta` milliseconds (16ms default)
  - This drives the entire game loop: staff actions, traffic generation, player work, day rollover
  - Ticks are skipped when `playerPausedTime` is true
- **MemoizedInventoryMapper**: Caches item metadata, prevents redundant DB lookups
- **IncrementalUiStateBuilder**: Rebuilds only changed UI state blocks (99% reduction in reconstructions)
- **Structural Change Detection**: `shouldRebuildUiState()` compares only UI-relevant fields; skips rebuild if nothing changed
  - Prevents unnecessary Compose recompositions when intermediate state updates don't affect the UI
- **Event Routing Structure**:
The `GameViewModel.onEvent()` method has four routing patterns:

1. **GameEngine calls + rebuild** (most events): Route to engine method, then rebuild UI state if domain state changed
2. **Pure-UI updates** (`SelectItemCategory`, `FocusInventoryItem`, `SelectStaffType`, `DismissTierUnlock`): Call `_uiState.update {}` directly, then `return` early to skip domain state rebuild
3. **GameEngine call + early return** (`SetGameSpeed`): Route to `gameEngine.setGameSpeed(...)`, then return early to skip full UI rebuild
4. **Special handling** (`Tick`): Always route to engine, never skip domain state rebuild (drives the entire game loop)

This pattern ensures performance: pure-UI events don't trigger expensive GameState→GameUiState transformations, while domain events properly rebuild UI state through the incremental builder.

**Screen State Reset Pattern** (April 16, 2026):
When tapping the current bottom nav screen button, local screen state resets:
- **INVENTORY**: Clears category filter, focuses null item, increments `inventoryResetTrigger`, closes detail screen
- **STAFF**: Resets to tab 0 (Staff tab)
- Other screens: no reset behavior yet

**See**: ui/viewmodels/GameViewModel.kt | ui/state/mappers/MemoizedInventoryMapper.kt | ui/state/builders/IncrementalUiStateBuilder.kt | MainActivity.kt (reset logic)
---
### **8. Time System (Decoupled from Real Time)**
- **GameTime.kt**: Value class wrapping `totalMinutesElapsed` (immutable, single source of truth)
  - Computed: `hour`, `minute`, `dayNumber`, `dayOfWeek` (0-6: Mon-Sun)
  - 1440 minutes = 1 game day
- **TimeManager.kt**: Converts real Δt → game minutes
  - `update(deltaMilliseconds: Long)` called every tick (takes ms, NOT seconds)
  - Multiplier: `StoreConfig.gameSpeedMultiplier` (1x/2x/4x/8x)
- **StoreConfig.kt**: Opens 6 AM (360 min), closes 9 PM (1260 min)

**Pattern**: Never use `System.currentTimeMillis()`. Always go through TimeManager → GameState.currentTime.

**See**: `domain/time/GameTime.kt` | `domain/time/TimeManager.kt`
---
### **9. Money: Integer-Based (No Floating Point)**
- **Value class**: `@JvmInline value class Money(val cents: Long)`
- **Always cents**: `Money(100)` = $1.00
- **Construction**: `Money(cents: Long)` or `Money.fromDollars(123.45)`
- **Output**: `.toString()` → `"$123.45"` (formatted)
- **Arithmetic**: Operators overloaded (`+`, `-`, `*` [Int/Long/Double], unary `-`)
- **⚠️ No division operator**: `Money / Money` does not exist. Use a Double multiplier instead.

**Pattern**: Do all calculations in cents. Example tax (matches `TransactionEngine`):
```kotlin
// CORRECT: use Double multiplier or Money.fromDollars()
val tax = Money.fromDollars(subtotal.toDouble() * 0.0825)   // 8.25% tax
val tax = subtotal * 0.0825                                   // equivalent

// WRONG: Money has no `/` operator
val tax = subtotal * Money(7) / Money(100)
```

**See**: `domain/GameStateData.kt` (Money class)
---
### **10. Sealed Events & Command Pattern**
All user interactions dispatch `GameEvent` (sealed interface):

```kotlin
// Events in GameEvent.kt:
sealed interface GameEvent {
    data object Tick : GameEvent
    data class RingUpItem(val itemId: Int) : GameEvent
    data class HireStaff(val entityDef: EntityDef, val entityType: EntityType) : GameEvent
    data class SetGameSpeed(val multiplier: Float) : GameEvent
    // ... 16 more
}
```

GameViewModel.onEvent() routes to GameEngine methods:

```kotlin
when (event) {
    is GameEvent.RingUpItem → gameEngine.ringUpItem(event.itemId)
    is GameEvent.HireStaff → gameEngine.hireEntity(event.entityDef, event.entityType)
    // ...
}
```

**Pattern**: Never call GameEngine methods directly from UI. Always dispatch GameEvent.

**See**: `ui/GameEvent.kt` | `ui/viewmodels/GameViewModel.kt`
---
### **11. Database & Room Integration**
- **AppDatabase**: Room DB **v6** (`@Database(version = 6)`), single entity: `Item` (table: `items`)
- **ItemDao**: Query interface (`getAllItems()`, `getItemById()`, `insertItem()`)
- **Item Entity**:
  - `id` (String, PK): Format "item_001" (parsed to Int)
  - `name`, `description`: Strings
  - `price`, `unitCost`: MoneyData embedded (persists as cents)
  - `category`: ItemCategory enum
  - `casePack`: Int (items per box)
  - `purchaseWeight`: Float (weighted customer basket sampling)
  - `tier`: String enum name (per-item unlock tier; e.g., `TIER_1`)

- **Indexes**: `category` & `name` for filtered queries (50-70% fewer items loaded)
- **MoneyData**: Embedded Room type (stores Money as cents)
- **ItemMetadataCache**: Singleton caching all item metadata (99% fewer DB lookups)

**Pattern**: Increment `@Database(version = X)` for schema changes. Provide Migration if data must persist.

**See**: `domain/items/` | `di/AppDatabase.kt` | `di/DatabaseModule.kt`
---
### **12. Staff & Entity Registry Pattern**
- **EntityDef**: Purchasable employee definition
  - Fields: `key`, `displayName`, `cost` (Money), `description`, `icon` (ImageVector), optional `nextUpgrade`
  - Companion constants: `CASHIER`, `FAST_CASHIER`, `STOCKER`, `FAST_STOCKER`, `FRESH_HANDLER`, `FAST_FRESH_HANDLER`, `allEntities`
- **EntityType**: Data class grouping related `EntityDef`s into a hire category
  - Fields: `displayName`, `description`, `icon`, `entities: List<EntityDef>`
  - Companion constants: `NONE`, `CASHIERS`, `STOCKERS`, `FRESH_HANDLERS`, `allEntityTypes`
- **EntityTrait**: Enum — `EFFICIENT`, `FRIENDLY`, `HARDWORKER`; randomly assigned at hire time
- **HiredEntity**: Represents employed staff (immutable)
  - Fields: `id`, `name`, `entityDefinition`, `entityType`, `trait`
- **HiredEntityRegistry**: Manages staff collection (immutable list operations)
  - Methods: `hireEntity()`, `fireEntity()`, `upgradeEntity()`, `countByType(type)`, `countByEntity(def)`, `getById(entityId)`, `getByType(type)`, `totalCount()`

**Pattern**: Staff operations return new Registry, assigned back to state.copy().

**See**: `domain/Entities/` folder
---
### **13. Hilt Dependency Injection**
- **@HiltViewModel**: All ViewModels use this decorator
- **@TickDelta**: Custom qualifier for tick delta (16ms default)
- **@ApplicationContext**: App context provided by Hilt
- **DatabaseModule**: Provides `AppDatabase` and `ItemDao` singletons
- **No constructor injection for GameEngine**: Lazily created after data loading

**Pattern**: GameEngine is created lazily in `GameViewModel` after `ItemDataLoader` + `ItemMetadataCache.initialize()` complete; do not constructor-inject it with Hilt.

**See**: `di/DatabaseModule.kt` | `SuperstoreSimulatorApp.kt`
---
### **14. Phase 2: Player Role System** ⭐ IMPLEMENTED (April 4, 2026)

- **PlayerRole** enum: `NONE`, `CASHIER`, `STOCKER` — only one active at a time
- **Toggle behaviour**: Dispatching `SetPlayerRole(role)` with the already-active role returns to `NONE`
- **GameState fields** added:
  - `playerRole: PlayerRole` — current active role
  - `playerCashierProgress: Float` — fractional ring-up accumulator
  - `playerStockerProgress: Float` — fractional stock accumulator
- **Player cashier speed**: 0.5 items/sec (same as a hired `CASHIER`)
- **Player stocker speed**: 0.3 case-packs/sec (faster than hired `STOCKER` at 0.1)
- **Progress accumulators reset** when role switches or set to `NONE`
- **UI components**: `PlayerRoleButtons` (toggle row), `PlayerRoleIndicator` (status display)

**See**: `domain/player/PlayerRole.kt` | `domain/GameEngine.kt` (`performPlayerCashierWork`, `performPlayerStockerWork`, `setPlayerRole`)
---
### **15. Phase 2: Autonomous Customer Traffic** ⭐ IMPLEMENTED (April 4, 2026)

- **TrafficManager**: fractional-accumulation customer generator — same technique as `TimeManager`
  - `update(state, deltaSeconds)` → returns `List<TransactionRequest>` (one per arrived customer)
  - `reset()` called on store `CLOSED` — drains `pendingCustomers`
  - Effective arrival rate multiplies by both `storeConfig.gameSpeedMultiplier` and `currentStoreSize.trafficMultiplier`
- **TrafficPattern**: per-hour config (`baseCustomerRate`, `averageBasketSize`, `peakMultiplier`)
- **TrafficSchedule**: weekday vs. weekend hourly schedules
  - Weekday peaks: 12 PM (lunch rush, rate 12.0) · 5 PM (evening rush, rate 14.0)
  - Weekend: sustained mid-day 11 AM–6 PM (rate 7–10)
- **Customer queue**: `GameState.pendingCustomers: Int` — customers waiting for a free register
  - `TrafficManager.update()` → increments `pendingCustomers`
  - Each tick: if `!transactionActive && pendingCustomers > 0` → dequeue one customer → random transaction
- **TransactionRequest**: `data class(itemCount: Int, customerPattern: TrafficPattern)` — basket size derived from pattern
- **`TransactionUIState.pendingCustomers`**: exposed to UI; shown by `CustomerQueueIndicator`

**See**: `domain/traffic/TrafficManager.kt` | `domain/traffic/TrafficPattern.kt` | `ui/components/CustomerQueueIndicator.kt`
---
### **16. Phase 3: Daily Metrics System** ⭐ IMPLEMENTED (April 4, 2026)

**Architecture**: Two-class design — live accumulator in `GameState` + immutable snapshots in a list.

```
Every tick: GameEngine tracks events → updates DailyMetricsAccumulator in GameState
    ↓ (midnight / dayNumber changes)
rollOverDay() → toSnapshot() → appended to GameState.completedDayMetrics
    + showEndOfDayReport = true → EndOfDayReportDialog shown in MainActivity
```

- **`DailyMetricsAccumulator`** (live, in `GameState.currentDayMetrics`): updated via `.copy()` at each tracked event
- **`DailyMetrics`** (immutable snapshot): stored in `GameState.completedDayMetrics: List<DailyMetrics>`
- **Tracked metrics per day**:
  - `revenue`, `subtotal`, `taxCollected`, `transactionsCompleted`
  - `rentPaid`, `wagesPaid` (deducted at day rollover)
  - `refundsProcessed`, `refundAmount`
  - `customersServed`, `itemsSold` (units)
  - `itemsStocked` (units moved backroom → shelf), `itemsOrdered` (units bought to backroom)
  - `lostRevenue`, `itemsLostToOutOfStock`, `outOfStockEvents: List<OutOfStockEvent>` — items the cashier could not ring due to zero shelf stock
  - `soldItemEvents: List<SoldItemEvent>` — per-line sales breakdown; aggregated in dialogs
  - `itemsExpired`, `expiredWasteCost`, `expiredItemEvents: List<ExpiredItemEvent>` ⭐ NEW (May 2026) — items removed due to expiration; shown in end-of-day "Shrinkage" section
  - `autoOrderedFreshItems: List<FreshOrderLineItem>`, `incompleteOrderedFreshItems: List<IncompleteOrderLineItem>` ⭐ NEW (May 2026) — fresh auto-order tracking
  - Derived: `averageTransactionValue`, `averageBasketSize`, `netRevenue` (now includes expiredWasteCost deduction)

**`OutOfStockEvent`** (`domain/metrics/DailyMetrics.kt`): `itemId`, `itemName`, `quantityLost`, `revenueLost` — one entry per transaction line that hit zero shelf stock; grouped by `itemId` in the dialog.

**`SoldItemEvent`** (`domain/metrics/DailyMetrics.kt`): `itemId`, `itemName`, `quantitySold`, `revenue` — one entry per fulfilled transaction line; grouped and sorted in the dialog.
- **Integration points in GameEngine** (all auto-tracked, no manual calls needed):
  - `ringUpItem(itemId)` → detects `totalTransactionsCompleted` delta → records revenue/items/customers
  - `stockItemFromBackroom()` → +1 to `itemsStocked`
  - `stockCasePackFromBackroom()` → +`itemsToStock` to `itemsStocked`
  - `buyItemToBackroom()` + `buyItemCasePacks()` → adds to `itemsOrdered`
  - `processRefund()` → records refund count + amount
- **Day rollover**: `GameEngine.tick()` compares `state.currentTime.dayNumber` to `lastKnownDayNumber` — calls `rollOverDay()` on change
  - `DayManager.rollOverDay()` snapshots metrics, deducts daily rent + wages from `money`, and pauses time for the report
- **`GameEvent.DismissEndOfDayReport`**: dismisses the dialog → `gameEngine.dismissEndOfDayReport()` clears the flag

**UI**:
- `EndOfDayReportDialog` — auto-shown at midnight; displays full day breakdown in sections (Sales, Customers, Inventory)
- `OutOfStockReportDialog` — drill-down from MetricsScreen; shows items lost to zero shelf stock, sorted by revenue lost
- `SoldItemsReportDialog` — drill-down from MetricsScreen; shows per-item units sold and revenue, sorted by quantity
- `MetricsScreen` — searchable list of daily report cards (search by day number or weekday name); tap card for full dialog
- `Screen.METRICS` — new nav tab ("Metrics" with `BarChart` icon) in `BottomNavBar`

**GameState fields**:
```kotlin
val currentDayMetrics: DailyMetricsAccumulator  // live accumulator, resets at midnight
val completedDayMetrics: List<DailyMetrics>       // all past days, newest last
val showEndOfDayReport: Boolean                   // triggers dialog in MainActivity
val lastEndOfDayReport: DailyMetrics?             // passed to dialog
val pausedByEndOfDay: Boolean                     // true when rollOverDay() auto-paused time;
                                                  // dismissEndOfDayReport() only unpauses if this is true
// Fresh auto-ordering (May 2026)
val freshAutoOrderConfig: FreshAutoOrderConfig   // enabled, minStockThreshold, casePacksPerItem
// NOTE: queuedFreshOrders / FreshOrderRequest no longer exist — orders go directly through the truck system
val incompleteFreshOrders: List<IncompleteOrderRequest>  // failed orders (insufficient funds)
// Truck delivery system (May 2026)
val scheduledTrucks: List<ScheduledTruck> = emptyList()
val truckConfig: TruckConfig = TruckConfig()
val nextTruckId: Int = 1
val objectiveBonusEarned: Money = Money.ZERO  // bonus from completed objectives (reserved for future objective system)
```

**See**: `domain/metrics/DailyMetrics.kt` | `domain/GameEngine.kt` (`rollOverDay`, `dismissEndOfDayReport`) | `ui/screens/metrics/MetricsScreen.kt` | `ui/dialogs/EndOfDayReportDialog.kt`
---
### **17. Progression System** ⭐ IMPLEMENTED (April 7, 2026)

Revenue tiers gate which store sections the player can see. The system runs entirely inside the domain layer — UI just renders `ProgressionUIState`.

**Tiers** (`domain/items/ItemUnlockTier.kt`):

| Tier | Revenue threshold | `unlockCost` | Newly unlocked categories |
|------|------------------|-------------|--------------------------|
| `TIER_1` | $0 (start) | $0 | GROCERY, SNACKS, DRINKS |
| `TIER_2` | $5,000 | $1,000 | + DAIRY |
| `TIER_3` | $20,000 | $4,000 | + FROZEN, BAKERY, PRODUCE |
| `TIER_GM` | $100,000 | $20,000 | all remaining categories |

Each tier also carries `narrativeTitle: String` and `narrativeDescription: String` shown in `UnlocksScreen`.

**`GameState` fields added**:
```kotlin
val totalRevenue: Money = Money.ZERO    // cumulative, never decreases (not affected by spending)
val currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1
```

**Engine flow** — tier advancement is a **manual purchase**, NOT automatic:
```
ringUpItem() → TransactionEngine.completeTransaction()
    → state.totalRevenue += tx.totalEarned          // updated inside TransactionEngine

// Revenue gate met but tier does NOT auto-advance.
// Player must explicitly trigger:
GameEvent.UnlockNextTier → gameEngine.unlockNextTier()
    → guard: totalRevenue.cents >= nextTier.unlockAmount AND money >= nextTier.unlockCost
    → state.currentTier = nextTier
    → state.money -= nextTier.unlockCost
    → _changes.emit(GameStateChange.TierUnlocked(nextTier, previousTier))
```

> ⚠️ `checkAndAdvanceTier()` **no longer exists** — it was removed when tier advancement became a purchase. Never try to call it.

**`GameStateChange.TierUnlocked(newTier, previousTier)`** — emitted via `_changes` StateFlow so `IncrementalUiStateBuilder` can set `ProgressionUIState.justUnlockedTier` without a full rebuild.

**`ProgressionUIState`** (in `GameUiState.progression`):
```kotlin
data class ProgressionUIState(
    val currentTier: ItemUnlockTier,
    val totalRevenue: Money,
    val nextTier: ItemUnlockTier?,            // null at top tier
    val revenueToNextTier: Money?,            // null at top tier
    val tierProgressFraction: Float,          // 0.0–1.0 within current tier band
    val justUnlockedTier: ItemUnlockTier?,    // non-null for ONE frame after unlock; cleared by DismissTierUnlock
    val availableTier: ItemUnlockTier?,       // non-null when revenue gate is met but tier not yet paid for
)
```

**Inventory filtering**: `MemoizedInventoryMapper.map(inventory, currentTier, backroomCap)` — takes `currentTier` and `backroomCap`, then hides items whose **individual `tier` field** requires a higher tier than the player currently has.

**Per-item tier gating** ⭐ NEW (April 8, 2026): Items now carry a `tier: String` field in the Room entity and a `tier: ItemUnlockTier` field in `ItemMetadata`. This allows items in the **same category** to have different unlock requirements (e.g., 28 GROCERY items start visible at TIER_1; 8 more premium GROCERY items unlock at TIER_2; 4 specialty GROCERY items unlock at TIER_3).

**Total catalog**: 136 items (items 001–136).

| Tier | Items added | Breakdown |
|------|-------------|-----------|
| TIER_1 | 38 items | 23 GROCERY + 8 SNACKS + 7 DRINKS |
| TIER_2 | 49 items | 13 GROCERY + 9 SNACKS + 5 DRINKS + 10 DAIRY |
| TIER_3 | 30 items | 4 GROCERY + 10 FROZEN + 8 BAKERY + 12 PRODUCE |
| TIER_GM | 29 items | 8 MEAT + 6 HEALTH + 6 HOUSEHOLD + 5 PHARMACY + 4 ELECTRONICS |

**Breaking rules for AI agents**:
- ❌ Never filter by `tier.unlockedSections` alone — this misses per-item tier gates within a category
- ✅ Always filter with `itemTier.unlockAmount <= currentTier.unlockAmount` (where `itemTier = itemMetadataCache.get(itemId)?.tier`)
- ✅ `TransactionEngine.startNewTransaction()` and `generateRandomTransaction()` both use per-item tier filter
- ✅ `MemoizedInventoryMapper.map()` uses per-item tier filter
- ✅ `unlockedSections` on `ItemUnlockTier` still exists and is used by `UnlocksScreen` for UI display only

**Helpers on `ItemUnlockTier`**:
- `fromTotalEarned(cents: Long)` — returns highest unlocked tier
- `nextTier(current)` — returns next tier or null at cap
- `requiredTierForSection(category)` — top-level function in `ItemUnlockTier.kt`

**Breaking rules for AI agents**:
- ❌ Never add `totalRevenue` to spending operations (buying inventory, hiring staff)
- ❌ Never call `checkAndAdvanceTier()` — it does not exist; tiers are purchased, not auto-advanced
- ✅ `totalRevenue` is updated only inside `TransactionEngine.completeTransaction()`
- ✅ Always dispatch `GameEvent.UnlockNextTier` to advance a tier — this deducts `unlockCost` from `money`
- ✅ Always dispatch `GameEvent.DismissTierUnlock` to clear the unlock banner — never mutate `ProgressionUIState` directly
- ✅ Use `ProgressionUIState.availableTier` to know when the player CAN purchase the next tier (revenue gate met, not yet paid)

**See**: `domain/items/ItemUnlockTier.kt` | `domain/GameEngine.kt` (`unlockNextTier`) | `domain/Transactions/TransactionEngine.kt` (`completeTransaction`) | `ui/state/GameUiState.kt` (`ProgressionUIState`) | `domain/GameStateChange.kt` (`TierUnlocked`)

---
### **18. Skip Day** ⭐ IMPLEMENTED (April 8, 2026)

Fast-forwards the rest of the current game day in a synchronous tight loop, then surfaces the end-of-day report exactly as midnight would.

- **Event**: `GameEvent.SkipDay` → `gameEngine.simulateRestOfDay()`
- **Implementation**: calls `tick(500L)` in a while-loop until `totalMinutesElapsed >= nextMidnight`; `rollOverDay()` fires when the day boundary is crossed, setting `showEndOfDayReport = true` and auto-pausing time
- **Player role**: suspended during simulation (accumulators zeroed) — result is deterministic regardless of active role
- **Guard**: no-op if `showEndOfDayReport` is already `true` (report pending)
- **Thread-safety**: pure computation, no I/O — safe to call on the main thread
- **UI entry point**: `StoreHomeScreen` "Skip Day" button → `onSkipDay` callback

**Pattern**:
```kotlin
// ViewModel routes the event:
GameEvent.SkipDay -> gameEngine.simulateRestOfDay()
// After the call, showEndOfDayReport == true; the normal EndOfDayReportDialog handling takes over.
```

**See**: `domain/GameEngine.kt` (`simulateRestOfDay`) | `ui/screens/home/StoreHomeScreen.kt`

---
### **19. Bulk Order System** ⭐ IMPLEMENTED (April 8, 2026)

Allows the player to restock entire categories in one action with tiered volume discounts.

- **Regular Bulk Order** (unlocked at TIER_2):
  - **Event**: `GameEvent.BulkOrder(maxTotalQuantity, casePacksPerItem, categoryFilter)` → `gameEngine.placeBulkOrder()`
  - **Eligibility filter** (all three must pass):
    1. Item tier ≤ `currentTier` (per-item unlock tier)
    2. Category matches `categoryFilter` (or `null` = all categories)
    3. `shelfStock + backroomStock ≤ maxTotalQuantity`
  - **Volume discount tiers** (applied to the entire order's base cost):

    | Total case packs | Discount |
    |-----------------|---------|
    | < 20 | 0% |
    | ≥ 20 | 10% |
    | ≥ 50 | 15% |
    | ≥ 100 | 25% |

  - **Excludes**: Fresh/perishable items (handled by separate system)
  - **UI**: `BulkOrderDialog` (slider for max stock threshold, slider for case packs per item, category chip filter)

- **Fresh Bulk Order** ⭐ NEW (May 2026):
  - **Event**: `GameEvent.FreshBulkOrder(maxTotalQuantity, casePacksPerItem)` → `gameEngine.placeFreshBulkOrder()`
  - **Eligibility**: Only fresh items (`shelfLifeDays != null`), same stock threshold logic
  - **Lower discount tiers** (reflects perishability risk):

    | Total case packs | Discount |
    |-----------------|---------|
    | < 15 | 0% |
    | ≥ 15 | 5% |
    | ≥ 30 | 10% |
    | ≥ 50 | 15% |

  - **Creates batches with expiration**: `expirationDay = currentDay + shelfLifeDays`
  - **UI**: `FreshBulkOrderDialog` (same interface as regular bulk order)

- **Guard**: does nothing if player cannot afford the discounted total
- **Metrics**: `itemsOrdered` accumulator updated for all items added to backroom

**Pattern**:
```kotlin
// Dispatch from MainActivity:
onBulkOrder = { maxQty, casePacks, category ->
    viewModel.onEvent(GameEvent.BulkOrder(maxQty, casePacks, category))
}
onFreshBulkOrder = { maxQty, casePacks ->
    viewModel.onEvent(GameEvent.FreshBulkOrder(maxQty, casePacks))
}
```

**See**: `domain/GameEngine.kt` (`placeBulkOrder`, `placeFreshBulkOrder`) | `domain/inventory/InventoryManager.kt` | `ui/dialogs/BulkOrderDialog.kt` | `ui/dialogs/FreshBulkOrderDialog.kt` | `ui/screens/inventory/InventoryScreen.kt`

---
### **20. Truck Delivery System** ⭐ IMPLEMENTED (May 2026)

All inventory purchases are now **deferred deliveries** — items are no longer added to the backroom instantly. Instead, `InventoryManager` buy methods return a `BuyResult(state, orderLines)` and GameEngine schedules those lines onto trucks via `TruckManager`.

**Architecture**:
```
Player/staff orders item
    ↓
InventoryManager.buyItemToBackroom() / buyItemCasePacks() / placeBulkOrder() / placeFreshBulkOrder()
    → Returns BuyResult(newState, orderLines)  // money deducted; NO backroom change yet
    ↓
GameEngine routes orderLines to TruckManager:
    Regular items → TruckManager.scheduleRegularOrderLines(state, lines, currentDay)
    Fresh items   → TruckManager.scheduleFreshOrderLines(state, lines, currentDay)
    → state.scheduledTrucks updated; emits GameStateChange.OrderScheduled(arrivalDay)
    ↓
End-of-day (each new dayNumber during tick):
    TruckManager.processArrivals(state, newDayNumber)
    → Delivers lines as ItemBatch to backroom; appends DeliveredTruckRecord to currentDayMetrics
    → Arrived trucks removed from scheduledTrucks
```

**Key Data Classes** (`domain/GameStateData.kt`):
```kotlin
PendingOrderLine(itemId, quantity, casePacksCount, unitCost, orderedOnDay, isFresh)
ScheduledTruck(truckId, scheduledArrivalDay, capacityCasePacks, orders, isFreshTruck, isEarlyTruck)
TruckConfig(deliveryDays: Set<Int>, regularTruckCapacityCasePacks, freshTruckCapacityCasePacks, extraTruckSlotsUnlocked)
```

**GameState fields added**:
```kotlin
val scheduledTrucks: List<ScheduledTruck> = emptyList()
val truckConfig: TruckConfig = TruckConfig()   // default: Mon+Thu delivery, 2000 regular / 500 fresh cap
val nextTruckId: Int = 1
```

**TruckManager public API** (`domain/delivery/TruckManager.kt`):
- `scheduleRegularOrderLines(state, lines, currentDay)` → places lines on next regular truck(s); creates new truck if all full; prefers early truck for tomorrow if one is booked
- `scheduleFreshOrderLines(state, lines, currentDay)` → places lines on that day's fresh truck (arrives currentDay + 1), creating it if needed
- `processArrivals(state, currentDay)` → delivers all trucks where `scheduledArrivalDay ≤ currentDay`; creates `ItemBatch` with correct `expirationDay`; tracks `DeliveredTruckRecord` in `currentDayMetrics`
- `updateConfig(state, newConfig)` → updates `truckConfig`; silently clamps `deliveryDays` to `maxDeliveryDaysAllowed`; no-op if `deliveryDays` would be empty
- `purchaseExtraTruckSlot(state)` → costs `TruckConfig.EXTRA_SLOT_COST` ($100); increments `extraTruckSlotsUnlocked`
- `requestEarlyTruck(state, currentDay)` → costs $100; reschedules next regular truck to arrive tomorrow + marks `isEarlyTruck`; or creates empty early truck if none scheduled
- `cancelPendingOrderLine(state, itemId, truckId, currentDay)` → refunds `unitCost × quantity`; removes truck if it becomes empty; no-op for already-arrived trucks
- `decrementOrderLine(state, itemId, truckId, currentDay)` → removes one case pack from item's last line; refunds `unitCost × unitsPerCasePack`; guard: total case packs for item must be > 1

**Delivery day scheduling**:
- Default: Monday (0) and Thursday (3) per week
- `maxDeliveryDaysAllowed = TruckConfig.BASE_FREE_SLOTS(2) + storeSize.ordinal + extraTruckSlotsUnlocked`
- `findNextDeliveryDay(currentDay, deliveryDays)` — finds next absolute day where `day % 7 ∈ deliveryDays`

**Multi-truck pre-ordering**: `buyItemCasePacks` allows ordering up to `MAX_TRUCKS_AHEAD(2) × backroomCapPerItem` case packs total (backroom + in-transit). Orders that exceed a single truck's per-item cap are split across multiple trucks automatically.

**In-transit cap enforcement**: `buyItemToBackroom` and `buyItemCasePacks` both count in-transit case packs from `scheduledTrucks` toward the cap, preventing spam-ordering while deliveries are pending.

**New GameEvents**:
```kotlin
UpdateTruckConfig(deliveryDays, regularCapacityCasePacks, freshCapacityCasePacks)
CancelPendingOrderLine(itemId, truckId)
DecrementOrderLine(itemId, truckId)
RequestEarlyTruck
PurchaseExtraTruckSlot
```

**New GameStateChange subtype**: `OrderScheduled(arrivalDay: Int)` — emitted after any successful order scheduling.

**Daily Metrics**:
- `DeliveredTruckRecord(truckId, arrivalDay, isFreshTruck, isEarlyTruck, totalCasePacks, lines)` — one per truck that arrived
- `DeliveredItemLine(itemId, itemName, casePacks, quantity)` — one per line in the truck
- Both in `DailyMetrics.deliveredTrucks` and `DailyMetricsAccumulator.deliveredTrucks`

**Breaking Rules for AI Agents**:
- ❌ Never add items to backroom directly from `InventoryManager` buy methods — they now return `BuyResult` with `orderLines`; GameEngine is responsible for routing to `TruckManager`
- ❌ Never call `InventoryManager.buyItemToBackroom()` / `buyItemCasePacks()` and discard `orderLines` — inventory will not actually arrive
- ✅ Fresh items go to `scheduleFreshOrderLines()` (`isFresh = true`); regular items go to `scheduleRegularOrderLines()`
- ✅ `processArrivals()` is called automatically each tick at day rollover — never call it manually mid-day
- ✅ When adding to test inventory, still use `ItemBatch` directly on `InventoryState` (test setup bypasses the truck system)

**See**: `domain/delivery/TruckManager.kt` | `domain/GameStateData.kt` (`PendingOrderLine`, `ScheduledTruck`, `TruckConfig`) | `domain/inventory/InventoryManager.kt` (`BuyResult`) | `domain/metrics/DailyMetrics.kt` (`DeliveredTruckRecord`, `DeliveredItemLine`) | `domain/GameStateChange.kt` (`OrderScheduled`) | `app/src/test/…/domain/delivery/TruckManagerTest.kt`

---
### **21. Fresh Auto-Ordering System** ⭐ UPDATED (May 2026)

Automated inventory management for perishable items. Idle fresh handlers automatically queue orders when fresh items fall below a configurable threshold. Orders are processed at end-of-day to minimize deliveries and optimize freshness.

**Architecture** (updated — orders now route through the Truck Delivery System):
```
Tick loop:
    Fresh handlers idle (no backroom stock to process)
        ↓
    attemptFreshHandlerAutoOrder() checks all fresh items
        ↓
    Items below threshold and not already handled today:
        If affordable:
            inventoryManager.buyItemCasePacks() → BuyResult
            truckManager.scheduleFreshOrderLines() → truck scheduled for currentDay + 1
            currentDayMetrics.autoOrderedFreshItems += FreshOrderLineItem
        Else:
            currentDayMetrics.incompleteOrderedFreshItems += IncompleteOrderLineItem
            incompleteFreshOrders += IncompleteOrderRequest
```

> ⚠️ The `queuedFreshOrders` GameState field and `FreshOrderRequest` data class **no longer exist** — they were part of the old end-of-day processing approach. Fresh auto-orders are now placed immediately via the truck system, not queued for midnight.

**Configuration** (`FreshAutoOrderConfig` in `GameState`):
- `enabled: Boolean` — toggle auto-ordering on/off (default: true)
- `minStockThreshold: Int` — trigger when `shelfStock + backroomStock < threshold` (default: 5)
- `casePacksPerItem: Int` — how many case packs to order per item (default: 1)
- **Event**: `GameEvent.UpdateFreshAutoOrderConfig(enabled, minStockThreshold, casePacksPerItem)` → `gameEngine.updateFreshAutoOrderConfig()`

**Incomplete Orders**:
- Tracked in `GameState.incompleteFreshOrders: List<IncompleteOrderRequest>`
- Each entry: `itemId`, `casePacksRequested`, `requestedOnDay`, `reason`
- **Manual ordering**: `GameEvent.OrderIncompleteItem(itemId, casePacksRequested)` → `gameEngine.orderIncompleteItem()` — deducts cost if affordable, removes from incomplete list
- **UI**: `IncompleteOrdersDialog` lists all incomplete orders with item name, quantity, cost, and "Order Now" button

**Key Methods** (in `InventoryManager`):
- `shouldAutoOrderFreshItem(state, itemId, config)` → Boolean — checks config.enabled, item is fresh, stock < threshold

**Key Methods** (in `GameEngine`):
- `attemptFreshHandlerAutoOrder()` — private, called during tick when fresh handlers idle; immediately routes through truck system (no end-of-day queue)
- `orderIncompleteItem(itemId, casePacksRequested)` — public, manual ordering from incomplete list; routes through `truckManager.scheduleFreshOrderLines()`

**Daily Metrics Integration**:
- `FreshOrderLineItem(itemId, itemName, casePacksOrdered, costPerCasePack, totalCost)` — successful orders
- `IncompleteOrderLineItem(itemId, itemName, casePacksRequested, costPerCasePack, totalCost, reason)` — failed orders
- Displayed in end-of-day report "Fresh Orders" section

**Breaking Rules for AI Agents**:
- ❌ Never queue fresh orders manually — `queuedFreshOrders` / `FreshOrderRequest` no longer exist; orders now go directly through the truck system
- ❌ Never call `processFreshOrders()` — it was removed; auto-ordering is immediate
- ✅ Fresh auto-ordering only triggers when fresh handlers are idle (no backroom stock)
- ✅ Each item can only be auto-ordered once per day (tracked via `currentDayMetrics.autoOrderedFreshItems` + `incompleteOrderedFreshItems`)
- ✅ Auto-orders create a fresh truck scheduled for `currentDay + 1`

**See**: `domain/GameEngine.kt` (`attemptFreshHandlerAutoOrder`, `orderIncompleteItem`, `updateFreshAutoOrderConfig`) | `domain/inventory/InventoryManager.kt` (`shouldAutoOrderFreshItem`) | `domain/GameStateData.kt` (`FreshAutoOrderConfig`, `IncompleteOrderRequest`) | `domain/metrics/DailyMetrics.kt` (`FreshOrderLineItem`, `IncompleteOrderLineItem`) | `ui/dialogs/IncompleteOrdersDialog.kt`

---
**Event Types** (sealed interface GameEvent):
- **Core**: Tick, StartTransaction, RingUp, RingUpItem(itemId)
- **Inventory**: StockItem(itemId), BuyItem(itemId), SelectItemCategory(category), FocusInventoryItem(itemId)
- **Staff**: HireStaff(def, type), UpgradeStaff(id), FireStaff(id), SelectStaffType(staffType)
- **Time**: SetGameSpeed(multiplier), ToggleStore
- **Store Size**: UpgradeStoreSize
- **Other**: ProcessRefund(id), ProcessRefundLine(id, itemId, qty), ChangeStoreName(name)
- **Phase 2**: SetPlayerRole(role: PlayerRole)
- **Phase 3**: DismissEndOfDayReport
- **Progression**: DismissTierUnlock, UnlockNextTier
- **Skip Day**: SkipDay
- **Bulk Order**: BulkOrder(maxTotalQuantity, casePacksPerItem, categoryFilter)
- **Fresh System** ⭐ UPDATED (May 2026): FreshBulkOrder(maxTotalQuantity, casePacksPerItem), UpdateFreshAutoOrderConfig(enabled, minStockThreshold, casePacksPerItem), OrderIncompleteItem(itemId, casePacksRequested)
- **Save System** (April 19, 2026): SaveGame
- **Reset System** ⭐ NEW (May 2026): ResetGame
- **Truck Delivery System** ⭐ NEW (May 2026): UpdateTruckConfig(deliveryDays, regularCapacityCasePacks, freshCapacityCasePacks), CancelPendingOrderLine(itemId, truckId), DecrementOrderLine(itemId, truckId), RequestEarlyTruck, PurchaseExtraTruckSlot

**⚠️ Pure-UI events** (handled directly by ViewModel, no GameEngine call, no domain state rebuild):
- `SelectItemCategory`, `FocusInventoryItem`, `SelectStaffType`
- `DismissTierUnlock` → clears `ProgressionUIState.justUnlockedTier` via `_uiState.update {}`

**⚠️ Lightweight domain event** (calls GameEngine but returns early to skip full GameState→GameUiState rebuild):
- `SetGameSpeed` → `gameEngine.setGameSpeed(event.multiplier)`

**⚠️ Phase 2 events** (routed to GameEngine — NOT pure-UI):
- `SetPlayerRole` → `gameEngine.setPlayerRole(event.role)` — toggling the active role again returns to `PlayerRole.NONE`

**⚠️ Phase 3 events** (routed to GameEngine — NOT pure-UI):
- `DismissEndOfDayReport` → `gameEngine.dismissEndOfDayReport()` — clears `showEndOfDayReport` flag

**⚠️ Progression / store upgrade events** (routed to GameEngine — NOT pure-UI):
- `UpgradeStoreSize` → `gameEngine.upgradeStoreSize()` — advances to `StoreSize.nextSize(currentStoreSize)` when affordable; updates `storeConfig.backroomCapPerItem` and deducts `upgradeCost`
- `UnlockNextTier` → `gameEngine.unlockNextTier()` — revenue gate must be met AND player must have ≥ `nextTier.unlockCost` cash; deducts money, advances `currentTier`, emits `TierUnlocked`
- `SkipDay` → `gameEngine.simulateRestOfDay()` — pure computation, runs all staff + traffic at normal rates until midnight, then surfaces the end-of-day report; safe to call on the main thread
- `BulkOrder(maxTotalQuantity, casePacksPerItem, categoryFilter)` → `gameEngine.placeBulkOrder()` — buys `casePacksPerItem` case packs for every tier-gated, category-matched item whose combined stock ≤ `maxTotalQuantity`; volume discounts applied automatically
---
## 🧪 Testing & Quality

### **Test Infrastructure**
- **Framework**: JUnit 4 (no Mockito — see pattern below)
- **Location**: `app/src/test/java/com/example/superstoresimulator/`
- **Coverage**: JaCoCo 0.8.12 (generate: `./gradlew testCoverageReport`)
- **Report**: `app/build/reports/jacoco/testCoverageReport/html/index.html`
- **Test audit**: `TEST_ANALYSIS.md` — full classification of every test (vacuous / trivial / sound)

### **Key Tests**

| File | Focus |
|------|-------|
| `domain/GameEngineIntegrationTest.kt` | Engine flow integration (events + tick-driven state transitions) |
| `domain/CashierTransactionTest.kt` | Transaction lifecycle, store-open guards |
| `domain/StockRandomItemFromBackroomTest.kt` | Stocking logic, stocker tick behaviour |
| `domain/UpgradeEntityTest.kt` | Full upgrade paths, exact cost deduction, registry isolation |
| `domain/MoneyTest.kt` | Formatting, rounding, real-world scenarios |
| `domain/InventoryStateTest.kt` | Documents that InventoryState allows negative values (guard is in GameEngine) |
| `domain/ProgressionTest.kt` | Revenue accumulation, tier advancement via GameEngine |
| `domain/TierUnlockTest.kt` | Unlock purchase guards and tier state transitions |
| `domain/BulkOrderTest.kt` | Bulk ordering filters, discounts, and affordability guards |
| `domain/ItemUnlockTierTest.kt` | Tier thresholds, category boundaries, `requiredTierForSection` |
| `domain/store/StoreControllerTest.kt` | Store admin behavior (pause/speed/state-change/size upgrade) |
| `domain/inventory/InventoryManagerTest.kt` | Inventory manager pure-logic cases and cap rules |
| `domain/staff/StaffManagerTest.kt` | Staff manager hire/upgrade/fire + progress accumulators |
| `domain/player/PlayerActionHandlerTest.kt` | Player-role toggle and per-tick work math |
| `domain/metrics/DayManagerTest.kt` | Day rollover snapshots, cost deduction, pause restore semantics |
| `domain/progression/ProgressionManagerTest.kt` | Progression manager guard checks and state updates |
| `domain/time/GameTimeTest.kt` | Hour/minute/day/week computed properties, `isOpen`, formatting |
| `domain/time/TimeManagerTest.kt` | Fractional accumulation, speed ratio, non-regression |
| `domain/time/StoreConfigTest.kt` | Open/closing/closed boundaries, mutual exclusivity across 1440 minutes |
| `domain/Transactions/TransactionTest.kt` | Convenience constructor, multi-line subtotals, high-value totals |
| `domain/Transactions/TransactionEngineTest.kt` | Full ring-up / OOS / history / tax / immutability |
| `domain/Transactions/TransactionEngineAdvancedTest.kt` | Refund paths (100% chance), partial refunds, inventory restoration |
| `domain/delivery/TruckManagerTest.kt` | Truck scheduling, arrivals, early truck, cancellation, overflow splitting |
| `domain/ImmediateFreshAutoOrderTest.kt` | Fresh auto-order routes through truck system (not queued end-of-day) |

### **Test Pattern** — `FakeItemDao` (NOT Mockito)

> ⚠️ Mockito **cannot** subclass `ItemMetadataCache` in this JUnit + JaCoCo environment — byte-buddy conflicts with JaCoCo's instrumentation agent. All test files use an **in-memory `FakeItemDao`** instead.

```kotlin
// ── Reusable fake (copy into each test class) ─────────────────────────────
private class FakeItemDao(private val items: List<Item>) : ItemDao {
    override suspend fun getAllItems(): List<Item> = items
    override suspend fun getItemById(itemId: String): Item? =
        items.firstOrNull { it.id == itemId }
    override suspend fun insertItem(item: Item) {}
    override suspend fun deleteItem(itemId: String) {}
    override suspend fun deleteAll() {}
    override suspend fun getItemName(itemId: String): String? =
        items.firstOrNull { it.id == itemId }?.name
    override suspend fun getAllItemsWithNames(): List<ItemWithName> =
        items.map { ItemWithName(it.id, it.name) }
    override suspend fun getItemsByIds(itemIds: List<String>): List<Item> =
        items.filter { it.id in itemIds }
    override suspend fun insertBatch(items: List<Item>) {}
    override suspend fun getItemCount(): Int = items.size
    override suspend fun getItemsByCategory(category: String): List<Item> =
        items.filter { it.category.name == category }
    override suspend fun searchItemsByName(searchTerm: String): List<Item> =
        items.filter { it.name.contains(searchTerm, ignoreCase = true) }
    override suspend fun getItemsForInventory(itemIds: List<String>): List<Item> =
        items.filter { it.id in itemIds }
}

// ── Engine factory ────────────────────────────────────────────────────────
private fun newEngine(items: List<Item> = emptyList()): GameEngine {
    val cache = ItemMetadataCache(FakeItemDao(items))
    runBlocking { cache.initialize() }
    return GameEngine(cache)
}

// ── Money injection helper (use instead of state.copy()) ─────────────────
// state.copy(money = …) creates a LOCAL copy and never reaches the engine.
// Always write through the internal setter:
private fun setMoney(cents: Long) {
    gameEngine.state = gameEngine.state.copy(money = Money(cents))
}

// ── Example test ─────────────────────────────────────────────────────────
@Before
fun setUp() {
    gameEngine = newEngine(testItems)   // testItems = List<Item> defined per class
}

@Test
fun testYourFeature() {
    setMoney(10_000L)
    gameEngine.someMethod()
    assertEquals(Money(8_500), gameEngine.currentState().money)
}
```

### **Test Quality Rules** ⭐ (April 7, 2026)

All trivial tests were removed in the April 7 audit. Do not reintroduce them.

| ❌ Do NOT write | ✅ Write instead |
|----------------|-----------------|
| Asserting a constructor stored its argument | Test the computed result of a method |
| `assertTrue(true)` / `assertTrue(x >= 0)` | Assert a concrete expected value |
| Testing Kotlin `data class copy()` | Test GameEngine mutations through `onEvent` / engine methods |
| `if (pendingRefunds.isNotEmpty()) { … }` with default 10% refund chance | Use `TransactionEngine(refundChance = 1.0)` to guarantee the path |
| Setter test: `set(x); assert(field == x)` | Test the side-effect of the operation |
| Duplicate tests across files | Add to the single authoritative file only |

### **JaCoCo Configuration Notes** (April 7, 2026)

- **Version**: upgraded from 0.8.10 → **0.8.12** in `app/build.gradle.kts`
- **JDK exclusions** added to `JacocoTaskExtension` to prevent `IllegalClassFormatException` when JaCoCo's ASM encounters JDK-internal proxy/security classes compiled at Java-25 bytecode (major version 69):
  ```kotlin
  excludes = listOf("jdk.proxy*", "sun.*", "com.sun.*", "org.jcp.*")
  ```
- The project itself compiles to **Java 11** bytecode, so coverage collection is unaffected.
---
## 🔧 Developer Commands

```bash
./gradlew assembleDebug                              # Build APK
./gradlew test                                        # Run unit tests
./gradlew testCoverageReport                         # Generate JaCoCo coverage
./gradlew lint                                        # Check style/errors
./gradlew clean                                       # Clean (needed after Room schema changes)
adb shell am start -n com.example.superstoresimulator/.MainActivity  # Launch app
```

### **Git Workflow** (see `GIT_WORKFLOW.md` for full detail)
- **Branches**: `main` (production) | `dev` (active development)
- ❌ Never commit directly to `main` — merge via `dev`
- ✅ Run `./gradlew test` before merging into `main`
- **Commit prefixes**: `feat:` | `fix:` | `refac:` | `test:` | `docs:` | `chore:`

```bash
git checkout dev
git checkout -b feature/my-feature   # branch off dev
# ... work ...
git checkout dev
git merge feature/my-feature
```
---
## ⚡ Critical Gotchas

| Issue | Solution |
|-------|----------|
| Room DAO changes not recognized | `./gradlew clean` |
| Blocking I/O on UI thread | Use ItemDataLoader async, NOT `runBlocking {}` |
| Money loses precision | Always use cents (Long), never float |
| Money division needed | `Money` has no `/` operator — use `money * 0.0825` or `Money.fromDollars(money.toDouble() * rate)` |
| GameEngine is null | Await ItemDataLoader; check `gameEngineInitialized` flag |
| UI doesn't update | Pass GameUiState to composables, NOT GameState |
| Time doesn't advance | Call `GameViewModel.onEvent(GameEvent.Tick)` every 16ms |
| PlayerRole doesn't deactivate | Dispatching `SetPlayerRole(activeRole)` toggles it OFF — returns to `NONE` |
| Hired stocker vs. player stocker | Hired stocker: 0.1 case-packs/sec; Player stocker: 0.3 case-packs/sec |
| Fresh handler vs. regular stocker | Fresh handlers only stock perishable items (`shelfLifeDays != null`); regular stockers exclude them |
| `totalRevenue` not updating | Updated only in `TransactionEngine.completeTransaction()` — never in spending paths |
| Tier unlock banner stuck | Dispatch `GameEvent.DismissTierUnlock`; never mutate `ProgressionUIState` directly |
| Tier not advancing after revenue gate | Tiers are **purchased** — dispatch `GameEvent.UnlockNextTier` (costs `unlockCost`); check `ProgressionUIState.availableTier` to know when the gate is met |
| `checkAndAdvanceTier()` missing | It was removed — tiers are manually purchased via `unlockNextTier()` |
| Skip Day has no effect | Guard: `simulateRestOfDay()` is a no-op if `showEndOfDayReport` is already `true`; dismiss the current report first |
| Bulk Order not available | `BulkOrderDialog` only appears when `currentTier >= TIER_2` |
| Pager state sync loops | Use `isNavigatingProgrammatically` flag; check `!pagerState.isScrollInProgress` before syncing |
| Calling private manager methods | Never call private methods like `stockCasePackFromBackroom()` — route through GameEngine public methods |
| InventoryState stock is wrong ⭐ NEW | `shelfStock` and `backroomStock` are computed properties from batches — never set directly |
| Buying inventory doesn't create batches ⭐ NEW | All inventory operations must create `ItemBatch` with `expirationDay = currentDay + shelfLifeDays` (or `Int.MAX_VALUE` for non-perishables) |
| Fresh items never expire ⭐ NEW | Ensure `ItemMetadata.shelfLifeDays` is set; `null` = non-perishable |
| Fresh auto-orders not processing ⭐ NEW | Auto-orders are placed immediately during `attemptFreshHandlerAutoOrder()` tick and routed through the truck system — `queuedFreshOrders` / `FreshOrderRequest` no longer exist |
| Buying inventory doesn't appear in backroom ⭐ NEW | `InventoryManager` buy methods return `BuyResult` — GameEngine must pass `orderLines` to `TruckManager`; items arrive next delivery day, not instantly |
| Order placed but no truck scheduled | `buyItemToBackroom` / `buyItemCasePacks` return `BuyResult`; if `orderLines` is empty the cap/affordability guard rejected the order — check `scheduledTrucks` in-transit count |
| Test inventory setup fails ⭐ NEW | Use `ItemBatch(receivedDay = 0, quantity = 10, expirationDay = Int.MAX_VALUE)` for test data |
---
## 🗂️ File Organization

**domain/** — Business logic
- GameEngine.kt [Facade orchestrator, routes to managers]
- GameStateData.kt [GameState, Money, FreshAutoOrderConfig, IncompleteOrderRequest, PendingOrderLine, ScheduledTruck, TruckConfig]
- GameStateChange.kt [Incremental updates]
- InventoryState.kt [ItemBatch with receivedDay/quantity/expirationDay, shelfBatches, backroomBatches]
- RefundRequest.kt [RefundRequest, RefundLine]
- Screen.kt [Screen enum: GAME, INVENTORY, HISTORY, METRICS, STAFF, STAFF_ENTITY_LIST]
- Transactions/TransactionEngine.kt [Transaction logic, tax calculation]
- time/ [TimeManager, GameTime, StoreConfig, StoreState]
- items/ [Item, ItemDao, ItemMetadataCache, ItemDataLoader, ItemMetadata (has `purchaseWeight: Float` for weighted basket sampling, `shelfLifeDays: Int?` for expiration, `isPerishable: Boolean`), ItemCategory, ItemUnlockTier, ItemDefinition, ItemWithName, MoneyData, ItemRegistry (legacy)]
- Entities/ [HiredEntity, EntityDef (includes FRESH_HANDLER, FAST_FRESH_HANDLER), EntityType (includes FRESH_HANDLERS), EntityTrait, EntityUpgrades, HiredEntityRegistry]
- Transactions/ [Transaction.kt — contains both `Transaction` and `TransactionLine`; `TransactionEngine.kt`]
- **progression/** [ProgressionManager — tier unlock logic]
- **staff/** [StaffManager — hire/upgrade/fire, cashier/stocker/fresh-handler ticks]
- **store/** [StoreController — store name, speed, open/close state]
- **inventory/** [InventoryManager — stock/buy/bulk-order operations, FIFO batch consumption, fresh auto-order queuing]
- **player/** [PlayerRole.kt — enum: NONE, CASHIER, STOCKER] + [PlayerActionHandler — player work ticks]
- **metrics/** [DailyMetrics.kt — DailyMetrics (snapshot), DailyMetricsAccumulator (live), ExpiredItemEvent, FreshOrderLineItem, IncompleteOrderLineItem] + [DayManager — day rollover, end-of-day report, fresh order processing]
- **persistence/** (April 19, 2026) [GameStateSerializer.kt — JSON serialization; GameStateRepository.kt — SharedPreferences persistence]
- **expiration/** ⭐ NEW (May 2026) [SpoilageManager.kt — automatic expiration processing, batch removal]
- **delivery/** ⭐ NEW (May 2026) [TruckManager.kt — truck scheduling, arrivals, cancellation, early truck booking]
- traffic/ [TrafficManager.kt, TrafficPattern.kt — TrafficPattern, TrafficSchedule, TransactionRequest]

**ui/** — User interface (Compose)
- viewmodels/ [GameViewModel, ItemViewModel (used by SalesHistoryScreen for item name lookups)]
- state/ [GameUiState (AppUIState, DashboardUIState, TransactionUIState, InventoryUIState, StaffUIState, HistoryUIState, TimeUIState, MetricsUIState, **ProgressionUIState**), mappers, builders]
- screens/ [home/StoreHomeScreen, inventory/InventoryScreen, sales/SalesHistoryScreen, staff/StaffScreen (**StaffAndUnlocksScreen** composite with "Staff"/"Unlocks" tabs), staff/UnlocksScreen, **metrics/MetricsScreen**]
- components/ [buttons/, cards/, common/, panels/, CustomerQueueIndicator.kt, PlayerRoleButtons.kt, PlayerRoleIndicator.kt]
- dialogs/ [PendingRefundsDialog, TransactionDetailDialog, **EndOfDayReportDialog**, **OutOfStockReportDialog**, **SoldItemsReportDialog**, **BulkOrderDialog**, **FreshBulkOrderDialog** ⭐ NEW, **IncompleteOrdersDialog** ⭐ NEW, **ResetGameConfirmationDialog** ⭐ NEW]
- theme/ [Colors, Styles]

**di/** — Dependency injection
- AppDatabase.kt [Room definition, version 6]
- DatabaseModule.kt [Hilt providers, @TickDelta qualifier]
---
## 🎬 Feature Addition Checklist

1. Add to GameState (if needed) → `domain/GameStateData.kt`
2. Add GameEvent (if user-triggered) → `ui/GameEvent.kt`
3. **Implement logic in appropriate manager** (April 15, 2026):
   - Inventory ops → `domain/inventory/InventoryManager.kt`
   - Staff ops → `domain/staff/StaffManager.kt`
   - Progression → `domain/progression/ProgressionManager.kt`
   - Store admin (name, speed, open/close) → `domain/store/StoreController.kt`
   - Player work ticks → `domain/player/PlayerActionHandler.kt`
   - Day rollover → `domain/metrics/DayManager.kt`
   - Truck/delivery ops → `domain/delivery/TruckManager.kt`
   - Otherwise → implement in `domain/GameEngine.kt` and call from manager orchestration
4. Add/adjust GameEngine routing by calling the appropriate manager from the relevant GameEngine method
5. Add ViewModel handler → `GameViewModel.onEvent()`
6. Create UI State (if visible) → `ui/state/GameUiState.kt`
7. Create Composable → `ui/screens/` or `ui/components/`
8. Pass UI State only (not GameState)
9. Write tests → `app/src/test/java/`
10. Run coverage → `./gradlew testCoverageReport`
---
## 🚀 Performance Notes

✅ **Solved** (April 2, 2026):
- **99% fewer DB lookups**: ItemMetadataCache
- **99% fewer state reconstructions**: IncrementalUiStateBuilder
- **98.5% less memory**: GameStateChange incremental updates

⏳ **Future**: Batch transaction processing, lazy pagination
---
## 📚 Architecture Deep Dives

- `ARCHITECTURE_DATABASE_ANALYSIS.md` — Query patterns, caching, 7 problems
- `ARCHITECTURE_CODE_REUSE.md` — Service layer pattern, god object reduction
- `ARCHITECTURE_PERFORMANCE.md` — Frame rate, memory, optimization
- `PROGRESSION_SYSTEM.md` — Progression system design: metric choice, tier unlocks (items/staff/hours), secondary systems, file-by-file implementation plan
---
## 🔮 Phase 2

**Autonomous Customers & Player Roles** ✅ IMPLEMENTED (April 4, 2026)

See sections 11 & 12 above for the full architecture breakdown.

**Quick Start**: `PHASE_2/PHASE_2_QUICK_START.md` (5-min read)
**Development**: `PHASE_2/PHASE_2_DEVELOPER_CHECKLIST.md` (step-by-step)
**Technical**: `PHASE_2/PHASE_2_IMPLEMENTATION_GUIDE.md` (spec)
