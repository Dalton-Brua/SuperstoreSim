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
### **2. Immutable State with Copy Semantics**
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
### **3A. ItemMetadataCache: Single Source of Truth for Item Data** ⭐ NEW (April 3, 2026)

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
### **3B. Service-Layer Manager Architecture** ⭐ IMPLEMENTED (April 15, 2026)

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
  ├─ StaffManager.{hireEntity, upgradeEntity, fireEntity}
  ├─ StoreController.{toggleTimePaused, updateStoreName, setGameSpeedState, upgradeStoreSize}
  ├─ InventoryManager.{stockItemFromBackroom, buyItemToBackroom, placeBulkOrder, etc}
  ├─ PlayerActionHandler.{calculateCashierWork, calculateStockerWork, setPlayerRole}
  ├─ DayManager.{rollOverDay, dismissEndOfDayReport}
  ├─ TrafficManager.update()
  └─ TransactionEngine.{ringUpSingleItem, processRefund, etc}
    ↓
Each returns new GameState via .copy()
    ↓
GameEngine.state = newState
Emit change via _changes.emit(...)
```

**Key Managers**:

1. **ProgressionManager** (`domain/progression/ProgressionManager.kt`)
   - `unlockNextTier(state)` → new state with advanced tier, deducted cost

2. **StaffManager** (`domain/staff/StaffManager.kt`)
   - `hireEntity(state, def, type)` → new state with hired entity
   - `upgradeEntity(state, entityId)` → new state with upgraded entity
   - `fireEntity(state, entityId)` → new state with dismissed entity
   - `advanceCashierProgress(cashierCount, deltaSeconds, multiplier)` → `Int` actions to perform this tick
   - `advanceStockerProgress(stockerCount, deltaSeconds, multiplier)` → `Int` actions to perform this tick
   - Fractional accumulators (`cashierProgress`, `stockerProgress`) are **outside GameState** — internal engine counters, not player-visible

3. **StoreController** (`domain/store/StoreController.kt`)
   - `updateStoreName(state, newName)` → new state
   - `setGameSpeedState(state, multiplier)` → new state (updates `storeConfig.gameSpeedMultiplier`)
   - `upgradeStoreSize(state)` → new state (deducts `nextSize.upgradeCost`, updates `currentStoreSize`, syncs `storeConfig.backroomCapPerItem`)
   - `handleStoreStateChange(state, newState, trafficManager)` → new state with side-effects (closes customers on CLOSED transition, resets traffic)
   - `toggleTimePaused(state)` → new state (flips `playerPausedTime` flag)

4. **InventoryManager** (`domain/inventory/InventoryManager.kt`)
   - `stockItemFromBackroom(state, itemId)` → new state
   - `stockCasePackFromBackroom(state, itemId)` → new state
   - `buyItemToBackroom(state, itemId)` → new state (refuses if full case pack exceeds backroom cap)
   - `buyItemCasePacks(state, itemId, casePacks)` → new state (clamps delivery to backroom cap)
   - `placeBulkOrder(state, maxTotalQty, casePacksPerItem, categoryFilter)` → new state with volume discounts applied
   - **Backroom cap**: `StoreConfig.backroomCapPerItem` is in **case packs per item** and is synced to `currentStoreSize` (starts at `StoreSize.MOM_AND_POP.backroomCapPerItem = 2`)

5. **PlayerActionHandler** (`domain/player/PlayerActionHandler.kt`)
   - `setPlayerRole(state, role)` → new state (toggles active role back to `NONE`)
   - `calculateCashierWork(state, deltaSeconds)` → `PlayerWorkResult`
   - `calculateStockerWork(state, deltaSeconds)` → `PlayerWorkResult`

6. **DayManager** (`domain/metrics/DayManager.kt`)
   - `rollOverDay(state, dayNumber)` → new state with metrics snapshot appended, daily costs applied, and end-of-day report surfaced
   - `dismissEndOfDayReport(state)` → new state with flag cleared
   - **Mutable counter**: `lastKnownDayNumber` — engine-internal, not in GameState

**Breaking Rules for AI Agents**:
- ❌ Never call manager methods directly — GameEngine public methods orchestrate them
- ❌ Never instantiate managers outside GameEngine — managers are created and owned by the engine
- ❌ Never mutate manager state (accumulators like `staffManager.cashierProgress`) — only GameEngine writes
- ❌ Never call private manager methods (e.g., `stockCasePackFromBackroom()`) from outside code — always route through GameEngine public methods
- ✅ When reading GameEngine code, understand managers are pure: all state flows through GameState.copy()
- ✅ Managers are testable in isolation by passing mocked GameState
- ✅ Each manager knows ONE domain; never cross-call between managers

**See**: `domain/progression/` | `domain/staff/` | `domain/store/` | `domain/inventory/` | `domain/player/` | `domain/metrics/`

---
### **3. GameEngine: The Core State Machine (Facade)**
- **Role**: Thin orchestrator — receives GameEvents, routes to appropriate managers, emits changes
- **Single source of truth**: `var state: GameState`
- **Lifecycle**: Async-initialized (not in constructor)
  - ItemDataLoader loads JSON → ItemDao → ItemMetadataCache initialized
  - GameViewModel waits for async completion before creating engine
- **Helper method**: `getDbItem(itemId: Int): Item?` — public access to cached item metadata for UI components (returns full Item object from ItemMetadataCache)
- **Managers** (April 15, 2026): Delegates to 6 specialized managers (see section 3B):
  - ProgressionManager, StaffManager, StoreController, InventoryManager, PlayerActionHandler, DayManager
  - Each manager is pure: receives GameState, returns new GameState
  - Each manager owns one domain of logic; GameEngine coordinates
- **Database Access**: Passes `ItemMetadataCache` to managers that need item lookups (InventoryManager, etc.)
  - Single database load: `itemDao.getAllItems()` called ONCE in `itemMetadataCache.initialize()`
- **Change Emission**: `_changes: StateFlow<GameStateChange?>` for incremental UI updates (99% fewer reconstructions)
  - `GameStateChange` subtypes: `MoneyChanged`, `InventoryUpdated`, `TransactionCompleted`, `TransactionStarted`, `RefundRequested`, `RefundProcessed`, `StaffUpdated`, `StoreStateChanged`, `TimeUpdated`, `TierUnlocked(newTier, previousTier)`

**See**: `domain/GameEngine.kt`
---
### **4. ViewModel Projection: GameState → GameUiState**
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
### **5. Time System (Decoupled from Real Time)**
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
### **6. Money: Integer-Based (No Floating Point)**
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
### **7. Sealed Events & Command Pattern**
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
### **8. Database & Room Integration**
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
### **9. Staff & Entity Registry Pattern**
- **EntityDef**: Purchasable employee definition
  - Fields: `key`, `displayName`, `cost` (Money), `description`, `icon` (ImageVector), optional `nextUpgrade`
  - Companion constants: `CASHIER`, `FAST_CASHIER`, `STOCKER`, `FAST_STOCKER`, `CUSTOMER_SERVICE_REP`, `allEntities`
- **EntityType**: Data class grouping related `EntityDef`s into a hire category
  - Fields: `displayName`, `description`, `icon`, `entities: List<EntityDef>`
  - Companion constants: `NONE`, `CASHIERS`, `STOCKERS`, `CUSTOMER_SERVICE_REPRESENTATIVES`, `allEntityTypes`
- **EntityTrait**: Enum — `EFFICIENT`, `FRIENDLY`, `HARDWORKER`; randomly assigned at hire time
- **HiredEntity**: Represents employed staff (immutable)
  - Fields: `id`, `name`, `entityDefinition`, `entityType`, `trait`
- **HiredEntityRegistry**: Manages staff collection (immutable list operations)
  - Methods: `hireEntity()`, `fireEntity()`, `upgradeEntity()`, `countByType(type)`, `countByEntity(def)`, `getById(entityId)`, `getByType(type)`, `totalCount()`

**Pattern**: Staff operations return new Registry, assigned back to state.copy().

**See**: `domain/Entities/` folder
---
### **10. Hilt Dependency Injection**
- **@HiltViewModel**: All ViewModels use this decorator
- **@TickDelta**: Custom qualifier for tick delta (16ms default)
- **@ApplicationContext**: App context provided by Hilt
- **DatabaseModule**: Provides `AppDatabase` and `ItemDao` singletons
- **No constructor injection for GameEngine**: Lazily created after data loading

**Pattern**: GameEngine is created lazily in `GameViewModel` after `ItemDataLoader` + `ItemMetadataCache.initialize()` complete; do not constructor-inject it with Hilt.

**See**: `di/DatabaseModule.kt` | `SuperstoreSimulatorApp.kt`
---
### **11. Phase 2: Player Role System** ⭐ IMPLEMENTED (April 4, 2026)

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
### **12. Phase 2: Autonomous Customer Traffic** ⭐ IMPLEMENTED (April 4, 2026)

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
### **13. Phase 3: Daily Metrics System** ⭐ IMPLEMENTED (April 4, 2026)

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
  - Derived: `averageTransactionValue`, `averageBasketSize`, `netRevenue`

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
```

**See**: `domain/metrics/DailyMetrics.kt` | `domain/GameEngine.kt` (`rollOverDay`, `dismissEndOfDayReport`) | `ui/screens/metrics/MetricsScreen.kt` | `ui/dialogs/EndOfDayReportDialog.kt`
---
### **14. Progression System** ⭐ IMPLEMENTED (April 7, 2026)

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
### **15. Skip Day** ⭐ IMPLEMENTED (April 8, 2026)

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
### **16. Bulk Order System** ⭐ IMPLEMENTED (April 8, 2026)

Allows the player to restock an entire category (or all accessible categories) in one action with tiered volume discounts.

- **Unlocked at**: TIER_2 and above (the `BulkOrderDialog` appears in `InventoryScreen`)
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

- **Guard**: does nothing if player cannot afford the discounted total
- **Metrics**: `itemsOrdered` accumulator updated for all items added to backroom
- **UI**: `BulkOrderDialog` (slider for max stock threshold, slider for case packs per item, category chip filter) is accessed via a button in `InventoryScreen`

**Pattern**:
```kotlin
// Dispatch from MainActivity:
onBulkOrder = { maxQty, casePacks, category ->
    viewModel.onEvent(GameEvent.BulkOrder(maxQty, casePacks, category))
}
```

**See**: `domain/GameEngine.kt` (`placeBulkOrder`) | `ui/dialogs/BulkOrderDialog.kt` | `ui/screens/inventory/InventoryScreen.kt`

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
| `totalRevenue` not updating | Updated only in `TransactionEngine.completeTransaction()` — never in spending paths |
| Tier unlock banner stuck | Dispatch `GameEvent.DismissTierUnlock`; never mutate `ProgressionUIState` directly |
| Tier not advancing after revenue gate | Tiers are **purchased** — dispatch `GameEvent.UnlockNextTier` (costs `unlockCost`); check `ProgressionUIState.availableTier` to know when the gate is met |
| `checkAndAdvanceTier()` missing | It was removed — tiers are manually purchased via `unlockNextTier()` |
| Skip Day has no effect | Guard: `simulateRestOfDay()` is a no-op if `showEndOfDayReport` is already `true`; dismiss the current report first |
| Bulk Order not available | `BulkOrderDialog` only appears when `currentTier >= TIER_2` |
| Pager state sync loops | Use `isNavigatingProgrammatically` flag; check `!pagerState.isScrollInProgress` before syncing |
| Calling private manager methods | Never call private methods like `stockCasePackFromBackroom()` — route through GameEngine public methods |
---
## 🗂️ File Organization

**domain/** — Business logic
- GameEngine.kt [Facade orchestrator, routes to managers]
- GameStateData.kt [GameState, Money]
- GameStateChange.kt [Incremental updates]
- InventoryState.kt [shelfStock, backroomStock per item]
- RefundRequest.kt [RefundRequest, RefundLine]
- Screen.kt [Screen enum: GAME, INVENTORY, HISTORY, METRICS, STAFF, STAFF_ENTITY_LIST]
- Transactions/TransactionEngine.kt [Transaction logic, tax calculation]
- time/ [TimeManager, GameTime, StoreConfig, StoreState]
- items/ [Item, ItemDao, ItemMetadataCache, ItemDataLoader, ItemMetadata (has `purchaseWeight: Float` for weighted basket sampling), ItemCategory, ItemUnlockTier, ItemDefinition, ItemWithName, MoneyData, ItemRegistry (legacy)]
- Entities/ [HiredEntity, EntityDef, EntityType, EntityTrait, EntityUpgrades, HiredEntityRegistry]
- Transactions/ [Transaction.kt — contains both `Transaction` and `TransactionLine`; `TransactionEngine.kt`]
- **progression/** ⭐ NEW [ProgressionManager — tier unlock logic]
- **staff/** ⭐ NEW [StaffManager — hire/upgrade/fire, cashier/stocker ticks]
- **store/** ⭐ NEW [StoreController — store name, speed, open/close state]
- **inventory/** ⭐ NEW [InventoryManager — stock/buy/bulk-order operations]
- **player/** [PlayerRole.kt — enum: NONE, CASHIER, STOCKER] + **PlayerActionHandler** ⭐ NEW [player work ticks]
- **metrics/** [DailyMetrics.kt — DailyMetrics (snapshot), DailyMetricsAccumulator (live)] + **DayManager** ⭐ NEW [day rollover, end-of-day report]
- traffic/ [TrafficManager.kt, TrafficPattern.kt — TrafficPattern, TrafficSchedule, TransactionRequest]

**ui/** — User interface (Compose)
- viewmodels/ [GameViewModel, ItemViewModel (used by SalesHistoryScreen for item name lookups)]
- state/ [GameUiState (AppUIState, DashboardUIState, TransactionUIState, InventoryUIState, StaffUIState, HistoryUIState, TimeUIState, MetricsUIState, **ProgressionUIState**), mappers, builders]
- screens/ [home/StoreHomeScreen, inventory/InventoryScreen, sales/SalesHistoryScreen, staff/StaffScreen (**StaffAndUnlocksScreen** composite with "Staff"/"Unlocks" tabs), staff/UnlocksScreen, **metrics/MetricsScreen**]
- components/ [buttons/, cards/, common/, panels/, CustomerQueueIndicator.kt, PlayerRoleButtons.kt, PlayerRoleIndicator.kt]
- dialogs/ [PendingRefundsDialog, TransactionDetailDialog, **EndOfDayReportDialog**, **OutOfStockReportDialog**, **SoldItemsReportDialog**, **BulkOrderDialog**]
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
