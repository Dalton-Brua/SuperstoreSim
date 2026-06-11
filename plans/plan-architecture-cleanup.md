# Architecture Cleanup Plan

Generated: 2026-06-10
Branch context: `feature/offlineProgress` — Phase 1 is a prerequisite for offline progress simulation.

Supersedes the architectural portions of `plan-arch-refactor.md` (2026-05-30), which predates
TickOrchestrator, the kotlinx.serialization migration, and Hilt constructor injection of
GameEngine. That plan's Task 6 (audit/expand GameStateChange emissions) is explicitly
**reversed** here: Phase 2 deletes the change-event pipeline instead of completing it.

---

## Findings Summary

| # | Issue | Severity | Phase |
|---|-------|----------|-------|
| 1 | Split-brain state: mutable singletons (TimeManager, StaffManager, TrafficManager, DayManager) hold simulation state outside GameState | High — blocks offline progress, save/load loses state silently | 1 |
| 2 | Dual UI pipeline: IncrementalUiStateBuilder + full `toUiState` rebuild both run; incremental path is mostly dead (TickOrchestrator always returns `emptyList()` changes) | Medium — wasted work, divergence risk | 2 |
| 3 | `GameStateChange` events routed through conflating `MutableStateFlow`; `result.changes.lastOrNull()` drops events at source | Medium — lossy by design | 2 |
| 4 | `shouldRebuildUiState` 25-field manual checklist; new GameState fields silently missing | Medium — staleness hazard | 2 |
| 5 | GameEngine `init` block seeds new game from ItemMetadataCache before cache is initialized — dead on cold start; seeding logic duplicated verbatim in `resetState()` | High if first-run is actually broken (verify) | 3 |
| 6 | `simulateRestOfDay` busy-loops ~1,440 synchronous ticks on the main thread | Low-Medium — UI freeze on skip-day | 4 |
| 7 | God files: GameViewModel (840 lines, 4 responsibilities), MainActivity (38KB inline nav/dialogs) | Low — maintainability | 6 |
| 8 | Minor: dead `onCleared` save (viewModelScope already cancelled — verified: MainActivity `onPause`/`onStop` cover saving), DI style split (half `@Inject constructor`, half manual `@Provides`) | Low | 6 |
| 9 | Broken facade: GameEngine contains inline domain logic instead of delegating — cashier auto-assign in `hireEntity` (GameEngine.kt:207-228), register cleanup in `fireEntity` (:232-241), refund metrics in `processRefund` (:187-199), new-tier inventory injection in `unlockNextTier` (:307-326), auto-order config writes as raw `state.copy` (:330-352) | Medium — logic unreachable from tests via managers, duplicates manager responsibilities | 5 |
| 10 | InventoryManager auto-order triplication: `attemptStockingManagerAutoOrder` / `attemptDayRolloverAutoOrder` / `attemptFreshHandlerAutoOrder` are ~70-line near-clones; `orderIncompleteItem` / `orderIncompleteNormalItem` likewise; `FreshAutoOrderConfig` / `NormalAutoOrderConfig` are identical twin classes | Medium — every auto-order bug must be fixed 3× | 5 |
| 11 | TransactionEngine: `startNewTransaction` / `generateRandomTransaction` near-duplicate bodies; `startNewTransaction` throws `IllegalStateException` on empty inventory (crash path reachable from UI RingUp button); engine also owns XP grants, register daily stats, and day-metrics accounting | Medium | 5 |
| 12 | Domain→infra side effect: DayRolloverProcessor owns a private `CoroutineScope(SupervisorJob() + Dispatchers.IO)` doing fire-and-forget Room writes mid-tick (DayRolloverProcessor.kt:30, :65-73) | Low-Medium — layering violation, writes lost if process dies before flush | 6 |
| 13 | UI reads domain directly: 124 `domain.*` imports across 38 ui/ files; composables call `viewModel.currentState()` mid-composition (MainActivity.kt:249-250 — non-reactive, won't recompose); InventoryScreen takes `ItemMetadataCache` as a parameter; GameViewModel exposes `currentState()` + public `itemMetadataCache` | Low — works today, but bypasses the UiState contract | 6 |

---

## Phase 1 — Consolidate Simulation State into GameState

**Do this before building offline progress.** Offline progress means "replay N hours of
simulation from the saved GameState." Today that is impossible deterministically because
simulation state lives partly in mutable singletons that are not saved and must be hand-synced
on load (`GameEngine.loadState` calls `syncTime`, `syncDay`, `staffManager.reset()`,
`trafficManager.reset()` — `GameEngine.kt:415-425`).

### State currently outside GameState

| Singleton | Mutable state | Lost on save/load? |
|-----------|--------------|--------------------|
| `TimeManager` | `currentTime`, `accumulatedMilliseconds`, its own `config` copy | accumulator lost; `config` can drift from `GameState.storeConfig` |
| `StaffManager` (lines 20-54) | cashier progress per register, stocker/manager/fresh progress accumulators, zoning state per stocker, 6 utilization tick counters, peak pending customers, unstaffed-register flag, hourly pending map | all lost (reset() on load) |
| `TrafficManager` | `accumulatedCustomers` fractional accumulator | lost |
| `DayManager` | tracked day number (synced via `syncDay`) | hand-synced |

### Steps

1. Add a `SimAccumulators` data class (or fold fields into existing sub-states):
   - `timeAccumulatorMs: Long`
   - `trafficAccumulator: Double`
   - `cashierProgressByRegister: Map<Int, Float>`
   - `stockerProgress`, `stockingManagerProgress`, `freshHandlerProgress: Float`
   - `zoningByStockerId: Map<Int, StockerZoningState>`
   - utilization counters + `peakPendingCustomers` + `hadUnstaffedRegisters` + `lastSampledHour` + `pendingCustomersByHour`
2. Add `simAccumulators: SimAccumulators = SimAccumulators()` to `GameState`.
   Follow `serialization_checklist` memory: `@Serializable`, defaults on every field, decide
   per-field whether to persist (utilization counters can default-reset; progress accumulators
   should persist for offline progress fidelity).
3. Convert `TimeManager.update` to a pure function: `(GameTime, accumulatorMs, deltaMs, config) -> (GameTime, accumulatorMs)`. Read `config` from `GameState.storeConfig` — delete TimeManager's own `config` var. TimeManager becomes stateless or disappears into a `TimeCalculator` object.
4. Convert `TrafficManager` accumulator the same way: tick reads/writes `state.simAccumulators.trafficAccumulator`.
5. Convert `StaffManager` per-tick fields: each processor method takes the relevant slice from
   `state.simAccumulators` and returns updated state. `employeeActivities` (UI display map) can
   move into GameState or stay as a derived/transient map — decide during implementation; if it
   stays, document it as display-only.
6. `DayManager.syncDay` becomes unnecessary once day tracking reads `state.currentTime.dayNumber` directly.
7. Delete `loadState`'s manual sync block — loading becomes `state = savedState` plus nothing.
8. Delete `staffManager.reset()` / `trafficManager.reset()` / `timeManager.syncTime` and their call sites.

### Verification

- All existing tests pass (`./gradlew test`).
- New test: run N ticks, serialize state, deserialize into a *fresh* engine, run M more ticks;
  compare against an uninterrupted N+M run. This is the determinism property offline progress needs.
  (Traffic randomness: inject seeded Random or assert on non-random fields.)
- Build + install to emulator, play a day, kill app, relaunch, confirm continuity (per emulator workflow memory).

### Risk

Largest phase. StaffManager is 37KB; touch one accumulator group at a time and keep tests green
between commits. Do NOT restructure the manager method signatures beyond threading state through —
behavior must be identical.

---

## Phase 2 — Collapse to a Single UI Pipeline

### Current state (the problem)

- `GameEngine._changes: MutableStateFlow<GameStateChange?>` → ViewModel collector → `IncrementalUiStateBuilder.applyChange`.
- `TickOrchestrator.tick` returns `TickResult(s, emptyList())` — the incremental path never fires for ticks.
- After every `onEvent` (including 16ms ticks), `shouldRebuildUiState` compares 25 hand-listed fields;
  `currentTime` changes nearly every tick, so the full `toUiState` rebuild runs ~60×/sec anyway.
- Net effect: incremental builder adds allocation + divergence risk for ~zero benefit; its
  "80-90% fewer allocations" header comment is false in practice.
- `_changes` is a conflating StateFlow and every emit site does `result.changes.lastOrNull()` —
  events are dropped by design. The snackbar for `OrderScheduled` is the only consumer that
  genuinely needs an *event* rather than state.

### Steps

1. Add a dedicated event flow for genuine one-shot events:
   `MutableSharedFlow<GameEvent>` (or keep it narrowly typed: `OrderScheduled` only, for the snackbar).
   Wire ViewModel snackbar to it.
2. Delete `GameStateChange` sealed class, `GameEngine._changes`, all `_changes.value = ...` emit
   sites, `result.changes` / `TickResult.changes` (TickResult becomes just `GameState` or keeps a
   slot for the new event type), and `IncrementalUiStateBuilder`.
3. Replace `shouldRebuildUiState`'s 25-field checklist with a single `newDomainState != oldDomainState`
   (GameState is a data class; equality is structural). Delete `lastUiState`/`lastDomainState`
   bookkeeping that exists only to serve the checklist.
4. Throttle UI mapping: tick loop keeps 16ms simulation cadence, but map domain→UI at most every
   ~100ms (or when a non-tick event fired). Simplest: track `lastUiMapTimeMs`; in the tick branch,
   skip `toUiState` unless 100ms elapsed or day rolled over. Non-tick events always map immediately.
5. Keep `MemoizedInventoryMapper` — it is the expensive part and already memoized.

### Verification

- Grep for `GameStateChange` returns zero hits.
- Manual emulator pass: money/inventory/time/staff panels update during play; snackbar still
  appears on truck order; tier-unlock banner still appears (it currently rides
  `GameStateChange.TierUnlocked` → confirm the full-rebuild path covers `justUnlockedTier`,
  which `buildProgressionUiState` already preserves via `old?.justUnlockedTier` — needs a
  replacement trigger: set it in `toUiState` when `currentTier` changed between domain states).
- Watch frame timing in Android Studio profiler before/after — expect flat or better.

### Risk

`TierUnlocked` and `OrderScheduled` are the two changes with real UI side-effects. Handle both
explicitly (step 1 and step 4 note) before deleting the enum.

---

## Phase 3 — Fix New-Game Seeding (verify first: likely first-run bug)

### Problem

`GameEngine` is constructed by Hilt as a `GameViewModel` constructor dependency — *before* the
ViewModel's init coroutine runs `itemMetadataCache.initialize()`. The `init` block
(`GameEngine.kt:64-95`) therefore sees an empty cache on cold start and skips seeding: no starting
inventory, `Money.ZERO`, TIER_1, MOM_AND_POP. Only `resetState()` (reachable solely from the reset
dialog) duplicates the same ~30 lines and works, because by then the cache is initialized.

### Steps

1. **Verify first** on a wiped emulator (uninstall + reinstall): if first launch shows $0 and empty
   store, this is a live bug; if not, find what masks it and document.
2. Extract `private fun buildSeededState(): GameState` containing the seeding logic (single copy).
3. Delete the `init` block entirely.
4. `resetState()` = sync managers (until Phase 1 removes that) + `state = buildSeededState()`.
5. In `GameViewModel.init`, after `itemMetadataCache.initialize()`:
   `savedState?.let { gameEngine.loadState(it) } ?: gameEngine.resetState()` (or a dedicated
   `seedNewGame()` that skips the redundant manager resets on first boot).

### Verification

Wiped emulator → first launch shows seeded store ($50,000.00, SMALL_GROCERY, TIER_2 items at qty 10
shelf + backroom). Reset-game flow still works. Save/load round-trip unaffected.

---

## Phase 4 — Move simulateRestOfDay off the Main Thread

### Problem

`GameEngine.simulateRestOfDay` (`GameEngine.kt:290-303`) while-loops ~1,440 ticks synchronously
inside `onEvent` on the main dispatcher. UI frozen for the duration of skip-day.

### Steps

1. Make the skip-day path a suspend function running on `Dispatchers.Default`, launched from the
   ViewModel; guard against concurrent ticks (pause the 16ms tick loop, or gate both behind a
   simple `isSimulating` flag — the tick loop and skip-day must never interleave since GameEngine
   state mutation is not thread-safe).
2. Show a lightweight progress indicator while simulating (optional; even without it, removing the
   ANR risk is the win).
3. Note for offline progress: this fast-forward loop is the same machinery offline progress needs.
   Build it as a reusable `fun simulateUntil(state, targetMinutes): GameState` so the offline
   feature calls the same code.

### Verification

Skip-day on emulator: UI stays responsive (clock/banner animates or at minimum no ANR dialog);
end-of-day report appears with correct numbers; tick loop resumes after.

---

## Phase 5 — Restore the GameEngine Facade and Manager Cohesion

GameEngine should be a thin facade: route action → manager, swap state, emit event. Today five
action methods embed domain logic inline (findings #9), and two managers carry duplicated or
misplaced logic (findings #10, #11). Do this after Phase 2 — deleting the `_changes` emit sites
first shrinks GameEngine and makes the remaining inline logic obvious and mechanically movable.

### Steps

1. **Push GameEngine inline logic down into managers** (behavior-preserving moves):
   - Cashier auto-assignment after hire → `RegisterManager.autoAssignUnassignedCashiers(state)`;
     `GameEngine.hireEntity` becomes `staffManager.hireEntity` + `registerManager.autoAssign...`.
   - Register unassignment on fire → fold into `StaffManager.fireEntity` or a
     `RegisterManager.unassignCashier(state, entityId)` call.
   - Refund metrics increment in `processRefund` → into `TransactionEngine.processRefund`
     (it already owns the rest of refund accounting).
   - New-tier inventory injection in `unlockNextTier` → into `ProgressionManager` (give it the
     `ItemMetadataCache`; it currently takes none, which is why the logic leaked upward).
   - Auto-order / store-manager config updates → trivial, can stay as `state.copy` one-liners,
     but group them; they are config setters, not logic.
2. **Deduplicate InventoryManager auto-ordering**: extract one
   `autoOrderItems(state, candidates, casePacks, schedule: (lines) -> GameState, recordAs: fresh|normal)`
   core; the three `attempt*AutoOrder` methods become thin parameterizations. Merge
   `orderIncompleteItem`/`orderIncompleteNormalItem` (only the truck-scheduling call and the
   cleared list differ). Merge `FreshAutoOrderConfig`/`NormalAutoOrderConfig` into one
   `AutoOrderConfig` type used twice — **serialization caution**: keep both GameState field names
   and add a migration default per serialization checklist memory, or keep twin typealiases.
3. **TransactionEngine cleanup**: extract the shared transaction-building core of
   `startNewTransaction`/`generateRandomTransaction`; replace the empty-inventory
   `IllegalStateException` with a no-op return (UI button can fire before seeding/after reset).
   Optionally split metrics/XP accounting (`ringUpItemAndTrackMetrics` tail) into a
   `TransactionMetricsRecorder` — do only if it falls out naturally; not worth a forced split.
4. **StaffManager split (optional, only if Phase 1 left it readable)**: auto-hire policy +
   store-manager policy (`evaluateAutoHire`, `evaluateStoreManagerActions`, ~240 lines) move to a
   `staff/StaffPolicy` class; StaffManager keeps tick-progress math and hire/fire/promote/shift CRUD.

### Verification

- Existing domain tests pass unchanged (moves, not rewrites — test through the same manager entry points).
- New tests cheap to add now: hire-cashier-auto-assigns-register via RegisterManager directly;
  one parameterized auto-order test replacing per-variant coverage.
- Grep: GameEngine action methods are ≤3 lines each (delegate + optional event emit).

### Risk

Step 2's config merge touches serialized state — follow the serialization checklist memory exactly
or defer the merge and dedupe only the methods. Everything else is mechanical relocation.

---

## Phase 6 — Decomposition and Minor Cleanups

Opportunistic; each item independent.

1. ✅ **GameViewModel mapper extraction** (795 → 590 lines): extracted `buildRegistersUiState`,
   `buildStaffScheduleEntries`, `countActiveStaff`, `buildDeliveryUiState`, `buildPricingUiState`,
   `buildProgressionUiState` into `ui/state/mappers/` as top-level functions. Time and metrics
   builders remain as small private methods. `MemoizedInventoryMapper` already modeled the pattern.
2. **MainActivity decomposition** (38KB): extract navigation host and dialog-host composables into
   `ui/navigation/`. Activity keeps lifecycle save hooks only.
3. **Delete dead `onCleared` save**: `viewModelScope` is cancelled before `onCleared` runs, so the
   `launch` inside never executes. `onPause`/`onStop` in MainActivity already cover saving.
4. ✅ **DI standardization**: 10 classes converted from manual `@Provides` to `@Inject constructor`
   + `@Singleton` (TimeManager, TrafficManager, StoreController, DayManager, StaffManager,
   PlayerActionHandler, ItemMetadataCache, PricingManager, SpoilageManager, TruckManager).
   `GameModule` shrunk from 12 providers to 2 (TransactionEngine, InventoryManager — kept because
   they use nullable constructor params for test flexibility).
5. **Move day-rollover Room sync out of the domain** (finding #12): DayRolloverProcessor stops
   owning a `CoroutineScope` and calling `transactionDao` directly. Options: (a) rollover sets a
   `pendingTransactionSync: List<Transaction>` slot the ViewModel/repository drains, or (b) the
   processor exposes the completed-day transactions via TickResult and the ViewModel persists.
   Either way domain stays side-effect-free and the write is observable/awaitable on save.
6. **Stop UI bypassing the UiState contract** (finding #13): make `GameViewModel.itemMetadataCache`
   private (route item names/details through `InventoryUIState` — `MemoizedInventoryMapper` already
   has the cache); replace composable-body `viewModel.currentState()` reads (MainActivity.kt:249-250,
   dialogs reading `truckConfig`/auto-order configs) with fields on the relevant UIState; remove the
   `ItemMetadataCache` parameter from InventoryScreen. Do not attempt to purge all 124 domain imports —
   value-type imports (Money, ItemCategory, enums) in UI are fine; the target is *live state* reads only.

### Verification

Build + install, smoke-test every screen. `./gradlew test`.

---

## Execution Order and Dependencies

```
Phase 3 (seeding fix)         ← small, do first; possible live first-run bug
Phase 1 (state consolidation) ← prerequisite for offline progress feature
Phase 4 (skip-day off-main)   ← after Phase 1; produces the reusable simulateUntil() that
                                 offline progress calls — ends the feature-critical path
─── offline progress feature is unblocked here ───
Phase 2 (pipeline collapse)   ← after Phase 1 (loadState/reset simplification feeds in)
Phase 5 (facade restoration)  ← after Phase 2 (emit-site deletion shrinks GameEngine first,
                                 making the inline logic moves mechanical)
Phase 6 (decomposition)       ← anytime after Phase 2; DI cleanup last (Phases 1/5 reshape managers)
```

Phase 4 was moved ahead of Phase 2: the branch goal is offline progress, and 3 → 1 → 4 is the
minimal path to it. Phases 2/5/6 are quality work that can land after (or interleave with) the
feature. One coordination note: Phase 2 touches the same `onEvent`/tick code as Phase 4's
tick-loop gating — preserve the `isSimulating` guard when collapsing the pipeline.

Each phase = its own commit (or commit series). Build + install to emulator after each (per
emulator workflow memory). Tests required for Phase 1 (determinism round-trip), Phase 2
(tier-unlock/snackbar behavior), and Phase 5 (auto-order dedup parameterized test); Phases 3/4/6
verified by emulator smoke tests plus existing suite.
