# Plan A: Building Purchase + Truck Fleet Upgrades

## Context

Player reaches Supercenter tier with $2,500/day rent and 2,000-cap trucks. Needs endgame money sinks beyond store expansion. Two "spend money → permanent upgrade" features using existing research-gate pattern.

**Implementation order: This plan first (A), then Private Label (B), then Second Locations (C).**

---

## A1: Building Purchase

**$500,000 one-time purchase eliminates daily rent permanently.** At Supercenter $2,500/day, ROI ~200 days. Research-gated behind Supercenter.

### State

`GameStateData.kt` — add to GameState:
```kotlin
val buildingOwned: Boolean = false
```

### Rent elimination

`DayManager.kt:55` — change rent calculation:
```kotlin
// Before:
val rentCost = processedState.currentStoreSize.dailyRent
// After:
val rentCost = if (processedState.buildingOwned) Money.ZERO else processedState.currentStoreSize.dailyRent
```

### Research gate

`ResearchUpgradeRegistry.kt` — new upgrade in STORE_EXPANSION:
```kotlin
"building_purchase" — "Property Acquisition Study"
researchCost: 150, prerequisites: ["store_supercenter"], requiredStoreSize: SUPERCENTER
```

`ResearchGates.kt` — add `BUILDING_PURCHASE = "building_purchase"`, add to `referencedUpgradeIds()`

### Purchase flow

`GameEngine.kt` — `purchaseBuilding(state)`:
- Guards: research complete, `!buildingOwned`, money >= $500K
- Deduct `Money(50_000_000L)`, set `buildingOwned = true`

`GameEvent.kt` — add `PurchaseBuilding` event

### UI

- Store expansion area: "Buy Building — $500,000" button (visible after research, greyed if broke)
- StoreHomeScreen daily expenses: "Rent: $0 (Building Owned)" when owned
- End-of-day report: rent line shows $0 with owned label

---

## A2: Truck Fleet Upgrades

**Tiered capacity: 2,000 → 3,000 → 5,000 case packs. Tier 1 costs $100K, Tier 2 costs $200K.** Fresh truck scales proportionally (25% of regular: 500 → 750 → 1,250).

### State

`GameStateData.kt` — add to TruckConfig:
```kotlin
val truckCapacityTier: Int = 0
```

New data class (in GameStateData.kt or TruckManager companion):
```kotlin
data class TruckCapacityTier(
    val tier: Int,
    val regularCapacity: Int,
    val freshCapacity: Int,
    val upgradeCost: Money?,
    val displayName: String,
)

val TRUCK_CAPACITY_TIERS = listOf(
    TruckCapacityTier(0, 2000,  500, null,                    "Standard Fleet"),
    TruckCapacityTier(1, 3000,  750, Money(10_000_000L),      "Enhanced Fleet"),   // $100K
    TruckCapacityTier(2, 5000, 1250, Money(20_000_000L),      "Heavy Fleet"),      // $200K
)
```

### Research gates

`ResearchUpgradeRegistry.kt`:
```kotlin
"truck_upgrade_enhanced" — researchCost: 40, prereq: ["extra_truck_slots"], requiredStoreSize: SUPERSTORE
"truck_upgrade_heavy"    — researchCost: 80, prereq: ["truck_upgrade_enhanced"], requiredStoreSize: SUPERCENTER
```

### Capacity resolution

`TruckManager.kt` — wherever new trucks are created:
- Replace `state.truckConfig.regularTruckCapacityCasePacks` with tier lookup
- Replace `state.truckConfig.freshTruckCapacityCasePacks` with tier lookup
- Already-scheduled trucks keep their original capacity (correct — upgrade doesn't change in-transit)

### Purchase flow

`GameEngine.kt` — `purchaseTruckUpgrade(state)`:
- Guards: next tier's research done, money >= cost, currentTier < 2
- Deduct cost, increment `truckCapacityTier`

### UI

- Delivery Settings screen: current fleet tier display, next upgrade button
- Truck cards: capacity badge reflects tier

---

## File Summary

| File | Changes |
|------|---------|
| `GameStateData.kt` | `buildingOwned`, `truckCapacityTier` in TruckConfig, `TruckCapacityTier` class |
| `DayManager.kt` | Rent = $0 when owned |
| `ResearchUpgradeRegistry.kt` | 3 new upgrades |
| `ResearchGates.kt` | 3 new constants + validation |
| `GameEngine.kt` | `purchaseBuilding()`, `purchaseTruckUpgrade()` |
| `GameEvent.kt` | 2 new events |
| `GameViewModel.kt` | Wire events, UI state |
| `TruckManager.kt` | Capacity from tier lookup |
| Store expansion UI | Building purchase button/status |
| Delivery settings UI | Fleet tier display/upgrade |

## Tests

### A1: Building Purchase — `BuildingPurchaseTest.kt` (new file in `domain/metrics/`)

Follows `DayManagerTest` pattern: construct `GameState` directly, call `DayManager.rollOverDay()`, assert results.

```kotlin
// Helper
private fun stateWith(
    buildingOwned: Boolean = false,
    storeSize: StoreSize = StoreSize.SUPERCENTER,
    money: Money = Money(500_000_000L),
): GameState = GameState(
    buildingOwned = buildingOwned,
    currentStoreSize = storeSize,
    money = money,
    currentDayMetrics = DailyMetrics(),
)
```

**Rent elimination:**
- `rollOverDay deducts zero rent when building is owned` — state with `buildingOwned = true`, SUPERCENTER. After rollover, money unchanged (no staff = no wages either).
- `rollOverDay deducts normal rent when building is not owned` — state with `buildingOwned = false`, SUPERCENTER. Money decreases by `StoreSize.SUPERCENTER.dailyRent`.
- `rollOverDay deducts normal rent for each store size when not owned` — loop all `StoreSize.entries`, assert `money - size.dailyRent`.
- `rollOverDay deducts zero rent for each store size when owned` — loop all entries with `buildingOwned = true`, assert money unchanged.

**Metrics snapshot:**
- `metrics snapshot records zero rent when building is owned` — `result.completedDayMetrics.last().rentPaid == Money.ZERO`
- `metrics snapshot records actual rent when building is not owned` — `rentPaid == StoreSize.SUPERCENTER.dailyRent`

**Default state:**
- `buildingOwned defaults to false in GameState` — `GameState().buildingOwned == false`

### A1: Building Purchase Flow — `BuildingPurchaseFlowTest.kt` (new file in `domain/store/`)

Tests the purchase function (wherever it lands — StoreController or GameEngine). Pure function: `GameState` in → `GameState` out.

```kotlin
// Helper: state at SUPERCENTER with building_purchase researched
private fun readyToBuy(money: Money = Money(100_000_000L)): GameState = GameState(
    currentStoreSize = StoreSize.SUPERCENTER,
    money = money,
    buildingOwned = false,
    researchState = ResearchState(
        researchedUpgrades = setOf("store_supercenter", "building_purchase"),
    ),
)
```

**Happy path:**
- `purchaseBuilding deducts cost and sets buildingOwned true` — money decreases by $500K, `buildingOwned == true`.

**Guards:**
- `purchaseBuilding no-op when already owned` — start with `buildingOwned = true`, state unchanged.
- `purchaseBuilding no-op when insufficient funds` — money = $100K (below $500K), state unchanged.
- `purchaseBuilding no-op when research not complete` — `researchedUpgrades` missing `"building_purchase"`, state unchanged.

### A2: Truck Fleet Upgrades — add to existing `TruckManagerTest.kt`

Tests that new trucks get capacity from tier, not flat constant.

```kotlin
// Helper: state with truck tier set
private fun stateOnDayWithTier(
    day: Int = 0,
    truckCapacityTier: Int = 0,
    money: Long = 1_000_000L,
): GameState = stateOnDay(
    day = day,
    money = money,
    truckConfig = TruckConfig(
        deliveryDays = setOf(0, 3),
        truckCapacityTier = truckCapacityTier,
    ),
)
```

**Capacity from tier:**
- `scheduleRegularOrderLines creates truck with tier 0 capacity (2000)` — schedule on tier-0 state, new truck `capacityCasePacks == 2000`.
- `scheduleRegularOrderLines creates truck with tier 1 capacity (3000)` — tier-1 state, `capacityCasePacks == 3000`.
- `scheduleRegularOrderLines creates truck with tier 2 capacity (5000)` — tier-2 state, `capacityCasePacks == 5000`.
- `scheduleFreshOrderLines creates fresh truck with tier-scaled capacity` — tier 0 → 500, tier 1 → 750, tier 2 → 1250.
- `requestEarlyTruck creates truck with current tier capacity` — tier-1 state, early truck `capacityCasePacks == 3000`.

**Existing trucks unaffected:**
- `upgrading tier does not change capacity of already-scheduled trucks` — schedule truck at tier 0 (cap 2000), then create new state with tier 1, verify original truck still has 2000 and new truck gets 3000.

### A2: Truck Upgrade Purchase — `TruckUpgradeTest.kt` (new file in `domain/delivery/`)

Tests the purchase function for truck tier upgrades.

```kotlin
private fun readyForTier1(): GameState = GameState(
    money = Money(50_000_000L), // well above $100K
    truckConfig = TruckConfig(truckCapacityTier = 0),
    researchState = ResearchState(
        researchedUpgrades = setOf("extra_truck_slots", "truck_upgrade_enhanced"),
    ),
)
```

**Happy path:**
- `purchaseTruckUpgrade advances from tier 0 to tier 1` — `truckCapacityTier == 1`, money decreased by $100K.
- `purchaseTruckUpgrade advances from tier 1 to tier 2` — start at tier 1 with `truck_upgrade_heavy` researched, tier becomes 2, money decreased by $200K.

**Guards:**
- `purchaseTruckUpgrade no-op at max tier` — `truckCapacityTier == 2`, state unchanged.
- `purchaseTruckUpgrade no-op when insufficient funds` — money below tier cost, state unchanged.
- `purchaseTruckUpgrade no-op when research not complete` — missing `"truck_upgrade_enhanced"` for tier 1, state unchanged.

### Research Gates — add to existing `ResearchGatesTest.kt`

Existing `every referenced gate id exists in the registry` test covers new upgrades automatically once they're added to the registry. Add:

- `building purchase gate exists in registry` — `"building_purchase" in ResearchUpgradeRegistry.allUpgrades`
- `truck upgrade gates exist in registry` — `"truck_upgrade_enhanced"` and `"truck_upgrade_heavy"` both in registry.
- `truck upgrade prerequisites form a valid chain` — `truck_upgrade_enhanced.prerequisites` contains `"extra_truck_slots"`, `truck_upgrade_heavy.prerequisites` contains `"truck_upgrade_enhanced"`.

### Serialization — add to existing serialization tests

- `GameState with buildingOwned true round-trips through JSON` — serialize → deserialize, assert `buildingOwned == true`.
- `GameState with truckCapacityTier 2 round-trips through JSON` — serialize → deserialize, assert `truckConfig.truckCapacityTier == 2`.
- `old save without buildingOwned deserializes with default false` — deserialize JSON missing the field, assert `buildingOwned == false`.
- `old save without truckCapacityTier deserializes with default 0` — deserialize JSON missing the field, assert `truckCapacityTier == 0`.

## Emulator Verification

- Research `building_purchase` → purchase building → verify $0 rent in next EOD report
- Research `truck_upgrade_enhanced` → purchase → verify next truck created with 3000 capacity
- Research `truck_upgrade_heavy` → purchase → verify next truck created with 5000 capacity
- Save game with upgrades → reload → verify building still owned and truck tier preserved
