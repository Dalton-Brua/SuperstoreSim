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

**Key Files**: `domain/GameEngine.kt` | `ui/viewmodels/GameViewModel.kt` | `ui/state/GameUiState.kt`
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
2. `GameEngine.__init__()` → loads to populate `dbItems` map
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
### **3. GameEngine: The Core State Machine**
- **Single source of truth**: `var state: GameState`
- **Integrates**: `TimeManager`, `TransactionEngine`, entity/inventory logic
- **Lifecycle**: Async-initialized (not in constructor)
  - ItemDataLoader loads JSON → ItemDao → GameEngine loads from DB
  - GameViewModel waits for async completion before accessing engine
- **Database Access**: ~~Maintains `dbItems: Map<Int, Item>` for metadata lookups~~ (OLD)
  - **NEW (April 3, 2026)**: `ItemMetadataCache` is the SINGLE source of truth for all item data
  - **Single database load**: `itemDao.getAllItems()` called ONCE in `itemMetadataCache.initialize()`
  - **3 cache layers**: metadata (name/price/category), full Items (for GameEngine), item names (for UI)
  - **Eliminates 3 redundant loads**: GameEngine, GameViewModel, InventoryScreen all used separate `getAllItems()` calls
  - **New pattern**: GameViewModel.itemMetadataCache → GameEngine → InventoryScreen (single load point)
  - **Performance**: 1 DB call instead of 3+ redundant calls per app launch
- **Change Emission**: `_changes: StateFlow<GameStateChange?>` for incremental UI updates (solves problem #4: 99% fewer reconstructions)
  - `GameStateChange` subtypes: `MoneyChanged`, `InventoryUpdated`, `TransactionCompleted`, `TransactionStarted`, `RefundRequested`, `RefundProcessed`, `StaffUpdated`, `StoreStateChanged`, `TimeUpdated`, `TierUnlocked(newTier, previousTier)`

**See**: `domain/GameEngine.kt`
---
### **4. ViewModel Projection: GameState → GameUiState**
- **GameViewModel** transforms domain state into UI-safe state
- **Lazy init**: Engine created AFTER items loaded (ItemDataLoader handles async)
- **MemoizedInventoryMapper**: Caches item metadata, prevents redundant DB lookups
- **IncrementalUiStateBuilder**: Rebuilds only changed UI state blocks (99% reduction in reconstructions)
- **Tick Loop**: @TickDelta (16ms) injected, updates every frame via coroutine
**See**: ui/viewmodels/GameViewModel.kt | ui/state/mappers/MemoizedInventoryMapper.kt | ui/state/builders/IncrementalUiStateBuilder.kt
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
    is GameEvent.HireStaff → gameEngine.hireStaff(event.entityDef, event.entityType)
    // ...
}
```

**Pattern**: Never call GameEngine methods directly from UI. Always dispatch GameEvent.

**See**: `ui/GameEvent.kt` | `ui/viewmodels/GameViewModel.kt`
---
### **8. Database & Room Integration**
- **AppDatabase**: Room DB **v4** (`@Database(version = 4)`), single entity: `Item` (table: `items`), db file `superstore-database-v4`
- **ItemDao**: Query interface (`getAllItems()`, `getItemById()`, `insertItem()`)
- **Item Entity**:
  - `id` (String, PK): Format "item_001" (parsed to Int)
  - `name`, `description`: Strings
  - `price`, `unitCost`: MoneyData embedded (persists as cents)
  - `category`: ItemCategory enum
  - `casePack`: Int (items per box)
- **Indexes**: `category` & `name` for filtered queries (50-70% fewer items loaded)
- **MoneyData**: Embedded Room type (stores Money as cents Long)
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

**Pattern**: Never `new GameEngine()`. Always use Hilt injection.

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

| Tier | Revenue threshold | Newly unlocked categories |
|------|------------------|--------------------------|
| `TIER_1` | $0 (start) | GROCERY, SNACKS, DRINKS |
| `TIER_2` | $5,000 | + DAIRY |
| `TIER_3` | $20,000 | + FROZEN, BAKERY, PRODUCE |
| `TIER_GM` | $100,000 | all remaining categories |

**`GameState` fields added**:
```kotlin
val totalRevenue: Money = Money.ZERO    // cumulative, never decreases (not affected by spending)
val currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1
```

**Engine flow**:
```
ringUpItem() → TransactionEngine.completeTransaction()
    → state.totalRevenue += tx.totalEarned          // updated inside TransactionEngine
    → GameEngine.checkAndAdvanceTier()               // private, called after every completed tx
        → ItemUnlockTier.fromTotalEarned(cents)
        → if new tier: state.currentTier = newTier
        → _changes.emit(GameStateChange.TierUnlocked(newTier, previousTier))
```

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
)
```

**Inventory filtering**: `MemoizedInventoryMapper.map(inventory, currentTier)` — takes `currentTier` as second argument and hides items whose **individual `tier` field** requires a higher tier than the player currently has.

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
- ✅ `totalRevenue` is updated only inside `TransactionEngine.completeTransaction()`
- ✅ Always dispatch `GameEvent.DismissTierUnlock` to clear the unlock banner — never mutate `ProgressionUIState` directly

**See**: `domain/items/ItemUnlockTier.kt` | `domain/GameEngine.kt` (`checkAndAdvanceTier`) | `domain/TransactionEngine.kt` (`completeTransaction`) | `ui/state/GameUiState.kt` (`ProgressionUIState`) | `domain/GameStateChange.kt` (`TierUnlocked`)

---
## 🎮 Game Events & User Interaction
**Event Types** (sealed interface GameEvent):
- **Core**: Tick, StartTransaction, RingUp, RingUpItem(itemId)
- **Inventory**: StockItem(itemId), BuyItem(itemId), SelectItemCategory(category), FocusInventoryItem(itemId)
- **Staff**: HireStaff(def, type), UpgradeStaff(id), FireStaff(id), SelectStaffType(staffType)
- **Time**: SetGameSpeed(multiplier), ToggleStore
- **Other**: ProcessRefund(id), ProcessRefundLine(id, itemId, qty), ChangeStoreName(name)
- **Phase 2**: SetPlayerRole(role: PlayerRole)
- **Phase 3**: DismissEndOfDayReport
- **Progression**: DismissTierUnlock

**⚠️ Pure-UI events** (handled directly by ViewModel, no GameEngine call, no domain state rebuild):
- `SelectItemCategory`, `FocusInventoryItem`, `SelectStaffType`, `SetGameSpeed`
- `DismissTierUnlock` → clears `ProgressionUIState.justUnlockedTier` via `_uiState.update {}`

**⚠️ Phase 2 events** (routed to GameEngine — NOT pure-UI):
- `SetPlayerRole` → `gameEngine.setPlayerRole(event.role)` — toggling the active role again returns to `PlayerRole.NONE`

**⚠️ Phase 3 events** (routed to GameEngine — NOT pure-UI):
- `DismissEndOfDayReport` → `gameEngine.dismissEndOfDayReport()` — clears `showEndOfDayReport` flag
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
| `domain/GameEngineTest.kt` | Engine init, time, speed, state immutability |
| `domain/GameEngineAdvancedTest.kt` | Inventory ops, transactions, entities, invariants |
| `domain/CashierTransactionTest.kt` | Transaction lifecycle, store-open guards |
| `domain/StockRandomItemFromBackroomTest.kt` | Stocking logic, stocker tick behaviour |
| `domain/UpgradeEntityTest.kt` | Full upgrade paths, exact cost deduction, registry isolation |
| `domain/MoneyTest.kt` | Formatting, rounding, real-world scenarios |
| `domain/InventoryStateTest.kt` | Documents that InventoryState allows negative values (guard is in GameEngine) |
| `domain/ProgressionTest.kt` | Revenue accumulation, tier advancement via GameEngine |
| `domain/ItemUnlockTierTest.kt` | Tier thresholds, category boundaries, `requiredTierForSection` |
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
---
## 🗂️ File Organization

**domain/** — Business logic
- GameEngine.kt [State machine]
- GameStateData.kt [GameState, Money]
- GameStateChange.kt [Incremental updates]
- InventoryState.kt [shelfStock, backroomStock per item]
- RefundRequest.kt [RefundRequest, RefundLine]
- Screen.kt [Screen enum: GAME, INVENTORY, HISTORY, METRICS, STAFF, STAFF_ENTITY_LIST]
- TransactionEngine.kt [Transaction logic, tax calculation]
- time/ [TimeManager, GameTime, StoreConfig, StoreState]
- items/ [Item, ItemDao, ItemMetadataCache, ItemDataLoader, ItemMetadata (has `purchaseWeight: Float` for weighted basket sampling), ItemCategory, ItemUnlockTier, ItemDefinition, ItemWithName, MoneyData, ItemRegistry (legacy)]
- Entities/ [HiredEntity, EntityDef, EntityType, EntityTrait, EntityUpgrades, HiredEntityRegistry]
- Transactions/ [Transaction.kt — contains both `Transaction` and `TransactionLine`]
- player/ [PlayerRole.kt — enum: NONE, CASHIER, STOCKER]
- traffic/ [TrafficManager.kt, TrafficPattern.kt — TrafficPattern, TrafficSchedule, TransactionRequest]
- metrics/ [DailyMetrics.kt — DailyMetrics (snapshot), DailyMetricsAccumulator (live)]

**ui/** — User interface (Compose)
- viewmodels/ [GameViewModel, ItemViewModel]
- state/ [GameUiState (AppUIState, DashboardUIState, TransactionUIState, InventoryUIState, StaffUIState, HistoryUIState, TimeUIState, MetricsUIState, **ProgressionUIState**), mappers, builders]
- screens/ [home/StoreHomeScreen, inventory/InventoryScreen, sales/SalesHistoryScreen, staff/StaffScreen, **metrics/MetricsScreen**]
- components/ [buttons/, cards/, common/, panels/, CustomerQueueIndicator.kt, PlayerRoleButtons.kt, PlayerRoleButtonsA.kt, PlayerRoleButtonsB.kt, PlayerRoleIndicator.kt]
- dialogs/ [PendingRefundsDialog, TransactionDetailDialog, **EndOfDayReportDialog**, **OutOfStockReportDialog**, **SoldItemsReportDialog**]
- theme/ [Colors, Styles]

**di/** — Dependency injection
- AppDatabase.kt [Room definition, version 4]
- DatabaseModule.kt [Hilt providers, @TickDelta qualifier]
---
## 🎬 Feature Addition Checklist

1. Add to GameState (if needed) → `domain/GameStateData.kt`
2. Add GameEvent (if user-triggered) → `ui/GameEvent.kt`
3. Implement in GameEngine → `domain/GameEngine.kt`
4. Add ViewModel handler → `GameViewModel.onEvent()`
5. Create UI State (if visible) → `ui/state/GameUiState.kt`
6. Create Composable → `ui/screens/` or `ui/components/`
7. Pass UI State only (not GameState)
8. Write tests → `app/src/test/java/`
9. Run coverage → `./gradlew testCoverageReport`
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
