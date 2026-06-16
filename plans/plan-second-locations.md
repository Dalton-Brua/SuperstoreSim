# Plan C: Second Locations (Empire Building)

## Context

Player opens additional store locations. Each location is a **full autonomous store** — own size, staff, inventory, registers, trucks. Player can switch to any location and modify everything, but locations run themselves when player isn't looking. Unlimited locations with scaling cost.

---

## Architecture: Multi-Store State

### StoreInstance extraction

Current `GameState` is a flat bag of fields. Extract per-store state into a `StoreInstance`:

```kotlin
@Serializable
data class StoreInstance(
    val storeId: Int,
    val storeName: String,
    val storeSize: StoreSize,
    val buildingOwned: Boolean = false,
    val inventory: Map<Int, InventoryState>,
    val hiredEntityRegistry: HiredEntityRegistry,
    val staffSchedules: List<StaffShift>,
    val registers: List<RegisterState>,
    val scheduledTrucks: List<ScheduledTruck>,
    val truckConfig: TruckConfig,
    val nextTruckId: Int = 1,
    val pendingCustomers: Int = 0,
    val freshAutoOrderConfig: FreshAutoOrderConfig,
    val normalAutoOrderConfig: NormalAutoOrderConfig,
    val incompleteFreshOrders: List<IncompleteOrderRequest>,
    val incompleteNormalOrders: List<IncompleteOrderRequest>,
    val storeManagerConfig: StoreManagerConfig,
    val pricingState: PricingState,
    val storeConfig: StoreConfig,
    val storeState: StoreState,
    val currentDayMetrics: DailyMetrics,
    // ... other per-store fields
)
```

**GameState** becomes:
```kotlin
data class GameState(
    // ── Global (shared across all stores) ──
    val money: Money,                        // single bank account
    val currentTime: GameTime,
    val totalRevenue: Money,
    val researchState: ResearchState,
    val tutorialState: TutorialState,
    val privateLabelLaunched: Boolean,
    val privateLabelLoyalty: Map<String, Float>,
    val vendorSystem: VendorSystemState,
    val playerPausedTime: Boolean,
    val completedDayMetrics: List<DailyMetrics>,  // aggregated or per-store?

    // ── Multi-store ──
    val stores: List<StoreInstance>,
    val activeStoreId: Int = 0,              // which store the player is viewing
    val playerRole: PlayerRole,              // player works at active store only
    
    // Backwards compat: single-store saves migrate to stores = listOf(StoreInstance(...))
)
```

### Migration path
- Phase 1: Extract `StoreInstance` from existing fields, keep flat accessors as `get() = stores[activeStoreIndex].field` for backward compat during migration
- Phase 2: Update all managers to accept `storeId` parameter or operate on `StoreInstance`
- Phase 3: Multi-store tick loop

---

## Autonomous Operation

When player isn't viewing a store, it still ticks:
- **GameEngine.tick()** iterates all stores, not just active
- Each store's traffic, stocking, cashiering, spoilage, truck arrivals all process independently
- Player actions (manual cashiering, stocking) only affect active store
- Managers (store manager, stocking manager) handle non-active stores autonomously — same logic already exists for hired managers

---

## Opening a New Location

### Research gate
```kotlin
"second_location" — researchCost: 250, prereq: ["building_purchase"], requiredStoreSize: SUPERCENTER
```

### Scaling costs
| Location # | Cost |
|-----------|------|
| 2nd | $1,000,000 |
| 3rd | $2,500,000 |
| 4th | $5,000,000 |
| nth | `$1M × 2.5^(n-2)` |

New locations start as MOM_AND_POP size with:
- 1 register
- Empty inventory
- No staff
- Default truck config
- Player must build it up (or hire managers to auto-run it)

---

## Store Switching UI

- **Main screen**: store selector (tab bar or dropdown) showing all locations
- Each store shows: name, size, today's revenue, staff count, alert badges (OOS, empty registers)
- Tap to switch `activeStoreId` — UI rebuilds for that store's data
- All existing screens work unchanged — they read from `stores[activeStoreId]`

---

## Shared vs. Per-Store

| Shared (global) | Per-store |
|-----------------|-----------|
| Money (single bank) | Inventory |
| Research progress | Staff + schedules |
| Private label loyalty | Registers |
| Vendor relationships | Trucks + orders |
| Game time | Store size |
| Tutorial state | Pricing |
| Total revenue | Daily metrics |
| Player pause state | Building owned |

---

## Key Challenges

1. **Manager refactoring**: Every manager currently reads `state.inventory`, `state.registers`, etc. Must change to read from `state.stores[storeId]` or accept `StoreInstance`.
2. **Tick loop**: `GameEngine.tick()` must iterate all stores. Performance concern with many stores.
3. **Serialization**: `StoreInstance` needs `@Serializable`. Migration from flat GameState to multi-store.
4. **UI state**: `GameUiState` currently mirrors flat GameState. Must scope to active store.
5. **Metrics aggregation**: End-of-day report per store + aggregate across empire.

---

## Recommended Sub-Phases

| Phase | Scope |
|-------|-------|
| C1: StoreInstance extraction | Refactor GameState into StoreInstance + global fields. Single store still works. No new features. |
| C2: Multi-store tick | GameEngine ticks all stores. Autonomous operation for non-active stores. |
| C3: Store opening | Research gate, purchase flow, store selector UI. |
| C4: Empire dashboard | Aggregate metrics, per-store P&L, empire-wide stats. |

**C1 is the critical path** — pure refactor with no new features but touches nearly every file. Should be done carefully with full test coverage.

---

## File Summary (high-level)

| Area | Files affected |
|------|---------------|
| State model | `GameStateData.kt` (major restructure), new `StoreInstance.kt` |
| All managers | `InventoryManager`, `StaffManager`, `TruckManager`, `DayManager`, `TrafficManager`, `SpoilageManager`, `TransactionEngine`, etc. — each needs store-scoped access |
| GameEngine | Tick loop, store switching, new location purchase |
| ViewModel | `GameViewModel`, `GameUiState` — scope to active store |
| Serialization | Migration from flat → multi-store |
| UI | Store selector, empire dashboard, per-store metrics |
| Tests | All existing tests need StoreInstance wrapping |

## Verification

- Unit: StoreInstance extraction — existing tests pass with single-store wrapper
- Unit: multi-store tick processes all stores
- Unit: new store starts as MOM_AND_POP with empty state
- Emulator: open 2nd location, switch between, verify independence
- Serialization: migrate old single-store save to multi-store format
- Performance: tick time with 3+ stores at Supercenter scale
