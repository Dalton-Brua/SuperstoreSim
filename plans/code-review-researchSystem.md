# Code Review — feature/researchSystem

**Scope:** working-tree diff vs `HEAD` (44 changed files) + new untracked research/tutorial files.
**Base:** `dev` (merge-base == HEAD, so all review scope is uncommitted working tree).
**Effort:** high (7 finder angles × verify). **Result:** 10 confirmed findings.

Migration replaces the old tier system (`ItemUnlockTier`, `ProgressionManager`, `TierProgressCard`) with a research-gate system. Items migrated cleanly to a `researchGate` field consumed via `ItemMetadataCache.isItemAccessible`. Non-item effects (store size, registers, feature visibility, fresh hiring) were re-gated with scattered string checks — the source of several bugs below.

---

## Severity overview

| # | Severity | Finding | Location |
|---|----------|---------|----------|
| 1 | Critical (build) | Test source set fails to compile | `TestGameEngineFactory.kt:35` |
| 2 | High | `prod_fresh_basics` gate id doesn't exist | `StaffManager.kt:398` |
| 3 | High | Null-cache filter → zero transactions | `TransactionEngine.kt:45` |
| 4 | Med-High | `staff_scheduling` gate id doesn't exist | `TutorialManager.kt:70` |
| 5 | Medium | Singleton `prevState` never reset on load/reset | `ResearchTickProcessor.kt:12` |
| 6 | Medium | Store-expansion research gates nothing | `StoreController.kt:26` |
| 7 | Medium | Stale-day seeding spoils unlocked perishables | `ResearchManager.kt:120` |
| 8 | Medium | Dead `onTruckDelivery`/`onSpoilage` insight sources | `ResearchTickProcessor.kt:44` |
| 9 | Medium | Analyst `trait.throughputMultiplier` dropped | `ResearchManager.kt:49` |
| 10 | Medium | Vendor commission removed from report, still deducted | `EndOfDayReportDialog.kt:116` |

---

## 1. Test source set fails to compile — CRITICAL
**File:** `app/src/test/java/com/example/superstoresimulator/domain/TestGameEngineFactory.kt:35`

`TestGameEngineFactory` still imports + constructs the deleted `ProgressionManager` (L11/L35) and passes `progressionManager =` to `GameEngine`, which now takes `researchManager`. `TickOrchestrator` now also requires `researchTickProcessor` + `tutorialTickProcessor`, never supplied. Plus ~7 test files reference deleted `ProgressionManager` / `ItemUnlockTier` / `GameState.currentTier` (`ProgressionManagerTest`, `ItemUnlockTierTest`, `TierUnlockTest`, `BulkOrderTest`, `InventoryManagerTest`, `PricingIntegrationTest`, `ProgressionTest`).

**Impact:** `./gradlew test` fails at compilation — no unit test runs on this branch.

**Fix:** update `TestGameEngineFactory` to current `GameEngine`/`TickOrchestrator` constructors (build + pass `researchManager`, `researchTickProcessor`, `tutorialTickProcessor`); delete or rewrite the obsolete tier-era test files.

---

## 2. `prod_fresh_basics` research id doesn't exist — HIGH
**File:** `app/src/main/java/com/example/superstoresimulator/domain/staff/StaffManager.kt:398`
**Also:** `StaffScreen.kt:97,261`, `InventoryScreen.kt:612`

Four sites gate on `researchedUpgrades.contains("prod_fresh_basics")`. No upgrade with that id exists in `ResearchUpgradeRegistry` or as an item `researchGate`. Real produce ids: `prod_produce`, `prod_premium_produce`, `prod_herbs`, `prod_organic_produce`. Condition is always false.

**Impact:** fresh handlers can never be hired (manual or auto); Fresh tab never appears; perishables go un-zoned / un-restocked. Replaced a previously-reachable tier gate.

**Fix:** point gates at a real upgrade id (or add the intended `prod_fresh_basics` upgrade to the registry). Better: see "Root cause" below.

---

## 3. Null-cache filter drops every item → zero transactions — HIGH
**File:** `app/src/main/java/com/example/superstoresimulator/domain/Transactions/TransactionEngine.kt:45`

New code:
```kotlin
val meta = cache?.get(itemId) ?: return@filter false
```
Old code fell back to `TIER_1` (accessible) when cache was null. Now a null cache empties `availableItemIds` → early `return state` → no sale.

**Impact:** `TransactionEngineAdvancedTest` constructs `TransactionEngine` without a cache (L50,73,95,121,145,173,187,203,218); asserts on `lines[0]`/`lines.size` now hit empty lists (IndexOutOfBounds / failed asserts). Production DI always passes a non-null cache (`GameModule.kt:22`), so prod is currently safe — but the invariant is fragile.

**Fix:** restore the cache-absent fallback (treat item as accessible when `cache == null`), matching old behavior.

---

## 4. `staff_scheduling` research id doesn't exist — MED-HIGH
**File:** `app/src/main/java/com/example/superstoresimulator/domain/tutorial/TutorialManager.kt:70`

`FEATURE_STAFF_SCHEDULING -> tutorialComplete && "staff_scheduling" in researched`. No `staff_scheduling` upgrade exists in the registry (only in plan docs). Always false.

**Impact:** shift-scheduling UI permanently hidden, no in-game unlock path. No compile-time error because gates are raw strings.

**Fix:** add the upgrade to the registry or correct the id; validate gate ids against the registry (see Root cause).

---

## 5. Singleton `prevState` never reset on load/reset — MEDIUM
**File:** `app/src/main/java/com/example/superstoresimulator/domain/tick/ResearchTickProcessor.kt:12` (and `TutorialTickProcessor.kt:14`)

Both processors hold `@Singleton var prevState: GameState?` with no `reset()`. `GameEngine.loadState()` / `resetState()` / `restoreManagersFromState()` reset other managers but never these.

**Impact:** after a load/reset in the same process, first ticks compute `state.X - prev.X` against the prior game. Higher loaded counters → spurious insight burst / mis-advanced tutorial step; lower counters → research stalls that tick.

**Fix:** add `reset()` to both processors and call it from `restoreManagersFromState()` (or thread previous state through `TickOrchestrator` instead of per-processor mutable fields). *Already noted in `plan-research-review-fixes.md`.*

---

## 6. Store-expansion research gates nothing — MEDIUM
**File:** `app/src/main/java/com/example/superstoresimulator/domain/store/StoreController.kt:26`

`upgradeStoreSize()` guards only on `nextSize.upgradeCost` vs money; never reads `researchedUpgrades`. Registry defines `store_small_grocery`/`store_grocery`/`store_superstore`/`store_supercenter` advertised as "Unlocks the store size upgrade."

**Impact:** store upgrades on cash alone; the entire STORE_EXPANSION research branch is a no-op; analysts assigned there waste insight. `RegisterManager.purchaseRegister` has the same ungated-vs-UI-hidden mismatch for `register_expansion`.

**Fix:** check the required upgrade in `upgradeStoreSize()` / `purchaseRegister()` (see Root cause).

---

## 7. Stale-day seeding spoils unlocked perishables — MEDIUM
**File:** `app/src/main/java/com/example/superstoresimulator/domain/research/ResearchManager.kt:120`

`checkCompletions` uses `currentDay = simAccumulators.lastKnownDayNumber` for `expirationDay`; `GameEngine.seedInventory` uses `currentTime.dayNumber`. In `TickOrchestrator`, `simAccumulators` is rewritten only at tick end while research runs mid-tick, so on a day-rollover tick `lastKnownDayNumber` lags by a day (defaults to 0 on legacy saves).

**Impact:** a short-shelf-life item unlocked on a rollover tick gets `expirationDay <= true current day`; `SpoilageManager` expires the freshly granted starter stock the same tick. Player sees the unlock vanish immediately.

**Fix:** seed using `state.currentTime.dayNumber`; ideally route through the shared `GameEngine.seedInventory` helper. *Already noted in `plan-research-review-fixes.md`.*

---

## 8. Dead `onTruckDelivery` / `onSpoilage` insight sources — MEDIUM
**File:** `app/src/main/java/com/example/superstoresimulator/domain/tick/ResearchTickProcessor.kt:44`

`onTruckDelivery()` (DELIVERY_INSIGHT 1.5) and `onSpoilage()` (SPOILAGE_INSIGHT 0.2) have zero callers; `TickOrchestrator` wires only `process()`.

**Impact:** two of five documented insight sources contribute nothing; research accrues slower than tuned.

**Fix:** invoke them from the truck-arrival and spoilage sites (or fold into `process()` via existing per-tick delta counters). *Already noted in `plan-research-review-fixes.md`.*

---

## 9. Analyst `trait.throughputMultiplier` dropped from research weighting — MEDIUM
**File:** `app/src/main/java/com/example/superstoresimulator/domain/research/ResearchManager.kt:49`

`distributeInsightPoints` computes `effectiveWeight = throughputWeight * levelMultiplier`. Canonical `StaffManager.activeWeightedCountWithIds` (L835) uses `throughputWeight * levelMultiplier * trait.throughputMultiplier`.

**Impact:** analyst entity traits scale every other staff role but not research insight (or consulting cash) — latent balance bug from re-implementing instead of reusing `activeWeightedCountWithIds`.

**Fix:** call `StaffManager.activeWeightedCountWithIds` (or add a thin analyst-weights helper) so weighting lives in one place.

---

## 10. Vendor commission removed from report, still deducted — MEDIUM
**File:** `app/src/main/java/com/example/superstoresimulator/ui/dialogs/EndOfDayReportDialog.kt:116`

Removed `StatRow` for "Vendor Commission". `VendorManager` still accrues `vendorCommissionPaid` (`VendorManager.kt:148,153`) and `DailyMetrics.netRevenue` (`DailyMetrics.kt:176`) still subtracts it.

**Impact:** when commission > 0, displayed Operating Costs (Rent + Wages only) don't sum to the shown Net Revenue. (Note: `expiredWasteCost` is also unshown and widens the gap.)

**Fix:** restore the commission line (and consider an `expiredWasteCost` line) so line items reconcile with net.

---

## Root cause — stringly-typed research gates (#2, #4, #6)

Migration off `ItemUnlockTier` left the *unlock concept* half-migrated. Items use a typed `researchGate` consumed through one mechanism (good). Non-item effects were each re-gated with ad-hoc string literals scattered across `StoreController` / `RegisterManager` / `StaffManager` / `TutorialManager` / UI, with no compile-time link to the registry. Two ids (`prod_fresh_basics`, `staff_scheduling`) don't exist at all; two (store size, registers) point at real upgrades whose research is never checked by the domain action.

**Deeper fix:** store the required-upgrade id (or null) as a typed field on the feature/effect definition, resolved against `ResearchUpgradeRegistry.allUpgrades`, so a dangling id is detectable at startup/test time — instead of free-floating literals in `when` ladders. A single `applyUpgradeEffect(upgradeId)` / `isUpgradeActive(upgradeId)` path would let store-size, register, and feature gates share one enforcement point.

---

## Cleanup notes (non-blocking)

- `ProgressionUiMapper.buildResearchUiState` rescans all ~78 registry upgrades (invoking `gateCheck` lambdas) + allocates a fresh list every tick — no memoization. Mirror `MemoizedInventoryMapper`.
- `TutorialManager.featureVisibility()` rebuilds a 12-entry map via `associateWith` every tick, even post-tutorial when values are static.
- `ResearchUIState` carries both `analystAssignments` map and `analysts` list holding the same data; `analystAssignments` is unread (dead, derivable).
- Upgrade-visibility gate logic duplicated between `ResearchManager.isResearchVisible` and `buildResearchUiState` — already drifted (UI shows in-progress, manager hides researched).
- `ResearchManager.checkCompletions` re-parses `item.id.removePrefix("item_").toIntOrNull()` although `getAllItems()` is already keyed by that Int; per-completion O(380) scan could use a `researchGate -> itemIds` index on the cache.
