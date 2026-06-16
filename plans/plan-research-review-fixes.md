# Plan: Research/Tutorial System — Code Review Fixes

**Created**: 2026-06-13
**Branch**: `feature/researchSystem`
**Scope reviewed**: uncommitted working-tree diff (research + tutorial system) — 33 modified files, 6 new files, 3 deleted (`ItemUnlockTier.kt`, `ProgressionManager.kt`, `TierProgressCard.kt`)
**Method**: max-effort `/code-review` — 9 finder angles + direct grep/read verification

---

## Summary

The tier progression system was replaced by a Research system (Market Analysts generate
points → unlock upgrades/items) plus a linear Tutorial. The refactor compiles main source
and is structurally sound, but ships four blocker-class bugs and several ungated/dead
paths. Findings below are ranked most-severe first.

| # | Severity | Finding | File |
|---|----------|---------|------|
| 1 | **Blocker** | Fresh subsystem gated on nonexistent id `prod_fresh_basics` | StaffManager.kt:383 |
| 2 | **Blocker** | Test source set won't compile (deleted symbols) | test/* |
| 3 | **High** | `prevState` leaks across load/reset in @Singleton processors | ResearchTickProcessor.kt:12 |
| 4 | **RESOLVED** | `onTruckDelivery`/`onSpoilage` dead (fire at midnight, no analyst on shift) — deleted | ResearchTickProcessor.kt:44 |
| 5 | **Med** | `visibleUpgrades` ignores storeSize/gateCheck → assign no-ops | ProgressionUiMapper.kt:60 |
| 6 | **High-impact (PENDING)** | items.json never migrated → all gating inert | res/raw/items.json |
| 7 | **Med** | Bulk Order gated on `if (true)` | InventoryScreen.kt:257 |
| 8 | **Med** | Pricing card shows all categories, ungated | StorePricingCard.kt:166 |
| 9 | **Med** | Debug-rich seed starts in tutorial, fresh locked | GameEngine.kt:404 |
| 10 | **Low (latent)** | `substitutionGroup=""` collides as mutual substitutes | ItemMetadataCache.kt:96 |
| 11 | Cleanup | Accessibility check inlined ×5 vs `isItemAccessible` | InventoryManager.kt:328 |
| 12 | Efficiency | `checkCompletions` full-catalog scan per completion | ResearchManager.kt:115 |
| 13 | Efficiency | Tutorial step sums all inventory every tick | TutorialTickProcessor.kt:41 |
| 14 | Cleanup | `getAccessibleItemIds` dead code | ItemMetadataCache.kt:73 |
| 15 | Cleanup | TutorialUIState + AssignAnalyst/SkipTutorial dead-on-arrival | ProgressionUiMapper.kt:28 |

---

## Blockers

### 1. Fresh subsystem permanently locked — `prod_fresh_basics` does not exist
**Files**: `StaffManager.kt:383`, `InventoryScreen.kt:612`, `StaffScreen.kt:220`
**Problem**: All three sites gate on `"prod_fresh_basics" in researchedUpgrades`, but
`ResearchUpgradeRegistry` defines no such id (closest: `prod_dairy_basics`, `prod_produce`,
`prod_frozen_basics`, `prod_bakery`, `prod_meat_counter`). The gate is unsatisfiable forever
→ Fresh tab never shows, fresh handlers can never be hired, fresh auto-hire never fires —
yet perishable items still enter inventory and spoil with no UI/staff to manage them.
**Fix**: Replace the magic string with a real gate (or set). Add a single typed source of
truth so a bad id is a compile error, e.g. in `ResearchUpgradeRegistry`:
```kotlin
val FRESH_GATES = setOf("prod_dairy_basics", "prod_produce", "prod_frozen_basics", "prod_bakery")
fun hasFreshUnlocked(researched: Set<String>) = researched.any { it in FRESH_GATES }
```
Call `ResearchUpgradeRegistry.hasFreshUnlocked(researchedUpgrades)` from all three sites.

### 2. Test source set won't compile
**Files**: `ItemUnlockTierTest.kt`, `BulkOrderTest.kt:8/92/259`,
`InventoryManagerTest.kt:12/129/359/397`, `TestGameEngineFactory.kt`
**Problem**: Tests reference deleted `ItemUnlockTier`, removed `GameState.currentTier`,
deleted `ProgressionManager`, and the old 14-arg `TickOrchestrator` constructor. Unresolved
references / missing-parameter errors → `./gradlew test` cannot build.
**Fix**:
- Delete `ItemUnlockTierTest.kt` and `ProgressionManagerTest.kt`/`TierUnlockTest.kt` (test deleted classes).
- `BulkOrderTest.kt` / `InventoryManagerTest.kt`: drop `currentTier =` args and `ItemUnlockTier` imports; switch tier-based eligibility assertions to `researchState.researchedUpgrades`.
- `TestGameEngineFactory.kt`: remove `ProgressionManager` construction; pass `researchManager`; add `researchTickProcessor`/`tutorialTickProcessor` to the `TickOrchestrator(...)` call.
- Add the pending `ResearchManagerTest` / `TutorialManagerTest` (plan Steps 2.9, 4.6) while here.

### 3. `prevState` leaks across game load/reset
**Files**: `ResearchTickProcessor.kt:12`, `TutorialTickProcessor.kt:14`
**Problem**: Both are `@Singleton` holding mutable `prevState: GameState?` with no reset hook.
`GameEngine.resetState()`/`seedNewGame()`/`loadState()` swap `state` but never clear
`prevState`. Loading save B (counters=500) after playing game A (counters=50) makes the next
tick compute `txDelta = 500 - 50 = 450` → ~+135 unearned research points (can auto-complete
cheap upgrades); tutorial diffs a stale backroom/shelf baseline and mis-advances/stalls.
**Fix**: Add `fun reset() { prevState = null }` to both processors and call them from
`GameEngine.loadState()` and `seedNewGame()`. (Deeper option: drive deltas off explicit
event counters carried in `GameState` so the computation is pure and reload-safe.)

### 4. `onTruckDelivery` / `onSpoilage` are dead code — RESOLVED (deleted)
**File**: `ResearchTickProcessor.kt:44,50`
**Problem**: Zero callers (verified by grep). `TickOrchestrator` only calls `process(s)`.
So `DELIVERY_INSIGHT` (1.5) and `SPOILAGE_INSIGHT` (0.2) never contribute.
**Original fix proposal was wrong**: "Wire them in at the truck-arrival / spoilage site"
would not work. Both events fire only at **day rollover** (`DayRolloverProcessor` —
spoilage keyed to `expirationDay <= currentDay`, trucks to `scheduledArrivalDay <= currentDay`),
which runs on the **hour-0 (midnight)** tick. `StaffShift` allows `startHour 6..20` ending
by 21:00, so **no analyst can ever be on shift at hour 0** — `distributeInsightPoints`
hits its `onShiftIds.isEmpty()` guard and returns zero regardless of wiring.
**Resolution**: Dropped both insight sources entirely — deleted `onTruckDelivery`/`onSpoilage`
methods and the `DELIVERY_INSIGHT`/`SPOILAGE_INSIGHT` constants. No retune; the remaining
three sources (transaction/stock/lost-customer) already pace research acceptably.

---

## Gating gaps (removed tier gates not re-established)

### 5. `visibleUpgrades` diverges from `isResearchVisible`
**File**: `ProgressionUiMapper.kt:60` (`buildResearchUiState`)
**Problem**: UI filter checks only prerequisites/progress; `ResearchManager.isResearchVisible`
additionally enforces `requiredStoreSize` and `gateCheck`. A Supercenter-only upgrade
(`prod_office_basics`) or revenue-gated one (`store_small_grocery`) shows as a researchable
card at a MOM_AND_POP/$0 store, but `assignAnalyst` → `isResearchVisible` rejects it → the
tap silently no-ops.
**Fix**: Make the UI consume the single predicate — `research.getAvailableResearch(state)` /
`isResearchVisible` — instead of re-deriving a weaker filter. One source of truth.

### 6. items.json never migrated to `researchGate` (PENDING — plan Step 1.6/1.7)
**File**: `app/src/main/res/raw/items.json`
**Problem**: Every item still carries only `"tier"`, so `researchGate == null` for all →
`isItemAccessible` true for everything; `seedInventory(starterOnly=true)` seeds ALL ~150
items on a new game; `checkCompletions` never finds an item matching a `completedId`, so
completing a study unlocks nothing. Makes every other research feature unobservable in-game.
**Fix**: Backfill `researchGate` per item (map old tier → gate per `plan-researchSystem.md`
lines 526–586), add the ~218 new items, and define the 12 starter items as `researchGate: null`.
This is the load-bearing data step — schedule it before further research QA.

### 7. Bulk Order gated on `if (true)`
**File**: `InventoryScreen.kt:257`
**Problem**: Old `if (currentTier.unlockAmount >= TIER_2.unlockAmount)` replaced with literal
`if (true)`. Button always shown; the `bulk_ordering` research upgrade is never enforced; dead
conditional misleads readers.
**Fix**: Gate on `"bulk_ordering" in researchedUpgrades` (param already plumbed), or delete the
`if` wrapper and de-indent if bulk ordering is intentionally always-on.

### 8. Pricing card shows all categories, ungated
**File**: `StorePricingCard.kt:166`
**Problem**: `val unlockedCategories = ItemCategory.entries.sortedBy { it.ordinal }` renders
default-markup + per-category sliders for every category (DELI, PET, BABY, AUTO, GARDEN,
SEASONAL…) regardless of `category_pricing` / `default_markup` research and even for
categories with no items.
**Fix**: Restrict to researched/stockable categories — derive from accessible items'
categories, and gate the default-markup slider behind `default_markup` research.

### 9. Debug-rich seed starts in tutorial with fresh locked (PARTIAL — plan Step 6.3)
**File**: `GameEngine.kt` `buildDebugRichState()` (~404–430)
**Problem**: Now seeds all items via `seedInventory(starterOnly=false)` but leaves
`researchState`/`tutorialState` at defaults → empty `researchedUpgrades` (Fresh tab hidden,
no store-size research) and `tutorialState` at WELCOME/`tutorialComplete=false`, so the "rich"
debug build launches into the new-player tutorial.
**Fix**:
```kotlin
researchState = ResearchState(researchedUpgrades = ResearchUpgradeRegistry.allUpgrades.keys.toSet()),
tutorialState = TutorialState(tutorialComplete = true, completedSteps = TutorialStep.entries.toSet()),
```

### 10. `substitutionGroup=""` collides as mutual substitutes (latent, data-dependent)
**File**: `ItemMetadataCache.kt:96` (`sharesSubstitutionGroup`) and index build ~line 53
**Problem**: `affinityGroups` filters `isNotEmpty()` during index build; `substitutionGroup`
does not. If any item has `"substitutionGroup": ""`, all such items land in one `""` bucket
and `sharesSubstitutionGroup` returns true via `s1 == s2`. In `TransactionEngine.pickWeighted`,
one blank-group pick applies the 0.1× `SUBSTITUTION_PENALTY` to every other blank-group item,
collapsing basket variety.
**Fix**: Treat blank as null — `item.substitutionGroup?.takeIf { it.isNotBlank() }` at index
build and in the accessor.

---

## Cleanup / efficiency

### 11. Accessibility predicate inlined ×5
**Files**: `InventoryManager.kt:328/415/554/663`, `GameEngine.kt:365`
**Problem**: `researchGate == null || researchGate in researchedUpgrades` is hand-copied at
five sites while `ItemMetadataCache.isItemAccessible(itemId, researchedUpgrades)` already
exists (and is used by TransactionEngine/MemoizedInventoryMapper). When the rule evolves the
copies diverge → order/auto-order eligibility stops matching what customers can buy.
**Fix**: Route all five through `cache.isItemAccessible(...)`.

### 12. `checkCompletions` full-catalog scan per completion
**File**: `ResearchManager.kt:115`
**Problem**: `getAllItems().filter { item.researchGate == completedId }` is O(items) plus
per-item `removePrefix`/`toIntOrNull` parsing, once per newly-completed upgrade, on a path
reachable every tick.
**Fix**: Build a one-time `Map<String /*gate*/, List<Int /*itemId*/>>` index in
`ItemMetadataCache.initialize()` (next to `affinityIndex`); look up only the gate's items.
Early-return in `checkCompletions` when `researchProgress` is empty.

### 13. Tutorial step sums all inventory every tick
**File**: `TutorialTickProcessor.kt:41–52`
**Problem**: WAIT_FOR_DELIVERY and STOCK_SHELVES do `prev.inventory.values.sumOf{...}` and
`state.inventory.values.sumOf{...}` each tick during early game just to detect a delta.
**Fix**: Compare existing aggregate counters in state (e.g. `currentDayMetrics.itemsStocked`)
for an O(1) check; only one step is active at a time.

### 14. `getAccessibleItemIds` dead code
**File**: `ItemMetadataCache.kt:73`
**Problem**: Newly added public helper with zero callers; duplicates `isItemAccessible` logic,
allocates an unused Set, invites confusion over the canonical accessor.
**Fix**: Delete until a caller exists.

### 15. TutorialUIState + AssignAnalyst/SkipTutorial dead-on-arrival
**Files**: `ProgressionUiMapper.kt:28` (`buildTutorialUiState`), `GameUiState.kt`, `GameEvent.kt`
**Problem**: `buildTutorialUiState()` runs every state emission building
`displayTitle`/`instruction`/`hintText` no composable reads; no UI emits `AssignAnalyst`/
`SkipTutorial`, so the event→engine→manager path is unreachable. Wasted per-frame work plus a
misleading "wired up" signal. (`ResearchUIState.totalRevenue`/`analystAssignments` similarly unread.)
**Fix**: Hold the mapper/state/events until the Research screen + Tutorial banner land (plan
Steps 5.2, 5.5), or build those screens now so the plumbing has a consumer.

---

## Verified, not guessed
- `prod_fresh_basics` absent from `ResearchUpgradeRegistry` (grep).
- `onTruckDelivery`/`onSpoilage` zero callers (grep).
- No `reset()` on either tick processor; `resetState()=seedNewGame()` doesn't clear `prevState`.
- Tests import `ItemUnlockTier` / set `currentTier` (grep).
- `GameState` uses auto kotlinx serialization (`encodeToString`/`decodeFromString<GameState>`),
  so new `@Serializable` `researchState`/`tutorialState` and sealed `AnalystAssignment` persist fine.

## Dropped (refuted / weak)
- Save corruption from sealed `AnalystAssignment` — kotlinx handles sealed hierarchies natively.
- Consulting-cash "100×" / "truncate-to-0" — math is correct ($5.00/point); only sub-cent
  per-tick truncation, immaterial.
- Completed upgrade "resurfaces as assignable" — guarded by `if (upgradeId in researchedUpgrades) return state`.
- `ItemMetadataCache` `toIntOrNull() ?: 0` id collision — pre-existing unchanged line; ids are well-formed `item_NNN`.

---

## Suggested fix order
1. **#2** (unblock test build) → **#1, #3, #4** (ship-blocker behavior) — small, high-value.
2. **#6** (items.json migration) — unblocks observing #5/#7/#8 end-to-end.
3. **#5, #7, #8, #9** (re-establish gates) — once #6 lands.
4. **#10–#15** (latent + cleanup) — opportunistic.
