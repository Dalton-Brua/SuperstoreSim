# Architecture Refactor Plan

Generated: 2026-05-30 | Reviewed: 2026-05-31  
Codebase: SuperstoreSimulator (Android / Kotlin / Jetpack Compose)

---

## Problems Summary

| File | Lines | Problem |
|------|-------|---------|
| `GameStateSerializer.kt` | 1,033 | Hand-coded JSON codec, no schema versioning, any rename breaks saves |
| `GameEngine.kt` | 1,046 | God object, tick phases implicit, ad-hoc change emission |
| `GameViewModel.kt` | 752 | Mixes event routing + UI mapping + tick loop + persistence |
| `GameStateData.kt` | 237 | Flat fields mixed with sub-objects — inconsistent grouping |
| `GameStateChange` | 66 | Incomplete — many mutations don't emit (e.g. `TransactionCompleted` exists but is never emitted), stale UI possible |

---

## Task 1 — Save Versioning + kotlinx.serialization (HIGH PRIORITY)

**Why first:** Schema changes break existing saves. Must add versioning before any state refactor.
The DB History Migration plan depends on this task being completed first — removing `salesHistory`
and `completedDayMetrics` from GameState is a breaking schema change that requires versioned saves.

**Steps:**
1. Add `"com.google.devtools.ksp"` + `"org.jetbrains.kotlin.plugin.serialization"` to `build.gradle`
2. **Handle kapt + KSP coexistence:** Hilt currently uses `kapt`. Either migrate Hilt from kapt → KSP
   (recommended — Hilt supports KSP since 2.48+), or accept running both processors (slower builds).
   Running both works but doubles annotation processing time.
3. Add `saveVersion: Int = SAVE_VERSION` to root `GameState`
4. Annotate all state data classes with `@Serializable`, use `@SerialName` for stable keys
5. Write migration function: `fun migrate(json: JSONObject, fromVersion: Int): JSONObject`
6. Replace `GameStateSerializer.kt` body with `Json.encodeToString` / `Json.decodeFromString`
7. Delete ~975 lines of hand-coded JSON paths

**Expected outcome:** Serializer shrinks from 1,033 lines to ~50. Field renames are safe via `@SerialName`.

---

## Task 2 — GameEngine Tick Phase Decomposition

**Why:** `tick()` runs all managers sequentially with no named boundaries. Hard to profile, test, or reason about ordering bugs.

**Target structure:**
```kotlin
fun tick(deltaMs: Long): GameState {
    state = tickTime(deltaMs)
    state = tickTraffic()
    state = tickTransactions()
    state = tickStocking()
    state = tickSpoilage()
    state = tickDeliveries()
    state = tickDayRollover()
    return state
}
```

**Steps:**
1. Extract each phase into a private `fun tickX(): GameState` method
2. Each phase only reads/writes its domain's sub-state
3. Add one unit test per phase exercising it in isolation
4. **Optional: add phase timing.** A simple `Map<String, Long>` tracking per-phase elapsed
   nanoseconds gives free profiling data. Especially useful for `tickTransactions()` when
   many registers are active simultaneously.

**Expected outcome:** `GameEngine.kt` drops significantly in apparent complexity; each phase is independently testable.

---

## Task 3 — GameState Sub-state Normalization + DB History Migration

**Why:** Some domains (inventory) already use sub-objects. Others dump flat fields onto root `GameState`. Inconsistency makes serialization and manager boundaries messy.

**Overlap note:** The DB History Migration plan (see `plan-db-history-migration.md`) also modifies
`GameStateData.kt` and `GameStateSerializer.kt` — specifically removing `salesHistory` and
`completedDayMetrics`. Do the DB migration as part of this task to avoid double-touching
these files. The target state below already reflects the removals.

**Target root state:**
```kotlin
data class GameState(
    val saveVersion: Int = SAVE_VERSION,
    val money: Money,
    val time: GameTimeState,
    val inventory: Map<Int, InventoryState>,   // already exists
    val staff: StaffState,
    val trucks: TruckState,
    val registers: RegisterState,
    val progression: ProgressionState,
    val metrics: MetricsState,
    // salesHistory and completedDayMetrics REMOVED — now in Room DB
    // currentDayMetrics stays (ephemeral, in-progress accumulator)
    // lastEndOfDayReport stays (dialog display)
)
```

**Steps:**
1. Audit `GameStateData.kt` — group flat fields into domain sub-states
2. Remove `salesHistory: List<Transaction>` and `completedDayMetrics: List<DailyMetrics>` (moved to Room — see DB migration plan)
3. Update each manager to accept/return only its own sub-state slice
4. Update `GameEngine` to compose sub-state back into root after each manager call
5. Re-run `./gradlew test` after each sub-state extracted

**Expected outcome:** Managers have clear ownership. `GameState` is readable at a glance. Enables Task 1 fully.

---

## Task 4 — GameViewModel Mapper Extraction

**Why:** ViewModel at 753 lines mixes event dispatch, UI state construction, and tick loop. Hard to test UI mapping logic.

**Target structure:**
```
ui/state/mappers/
  InventoryMapper.kt       ← already exists (MemoizedInventoryMapper)
  RegistersMapper.kt
  StaffMapper.kt
  DeliveryMapper.kt
  TransactionMapper.kt
  TimeMapper.kt
```

**Steps:**
1. For each `GameUiState` sub-section, extract a `fun GameState.toXxxUIState(): XxxUIState` mapper
2. Move mapper calls into `IncrementalUiStateBuilder`
3. ViewModel retains only: event routing, `onEvent()`, state flow exposure, tick loop

**Expected outcome:** ViewModel shrinks to ~300 lines. Mappers are unit-testable without a ViewModel.

---

## Task 5 — GameLoop Extraction (Tick Ownership)

**Why:** Tick loop in ViewModel ties simulation to UI lifecycle. Config changes (screen rotation) recreate ViewModel, potentially disrupting the game loop.

**⚠️ Conflict with current architecture:** GameEngine is currently created **lazily** in GameViewModel
after `ItemDataLoader` + `ItemMetadataCache.initialize()` complete. AGENTS.md explicitly says
"do not constructor-inject [GameEngine] with Hilt." Making `GameLoop` a Hilt `@Singleton` would
require `GameEngine` itself to be Hilt-managed, which means restructuring the async init chain.

**Recommended approach:** Instead of a Hilt singleton, extract the loop into a dedicated class
that the ViewModel owns. This decouples the loop logic without fighting the lazy-init pattern:

```kotlin
class GameLoop(
    private val engine: GameEngine,
    private val tickDelta: Long,
) {
    private var job: Job? = null
    fun start(scope: CoroutineScope) {
        job = scope.launch { while (isActive) { delay(tickDelta); engine.tick(tickDelta) } }
    }
    fun stop() { job?.cancel() }
}
```

ViewModel creates `GameLoop` after engine init, owns lifecycle. Config changes don't disrupt
because `@HiltViewModel` ViewModel survives rotation (scoped to `NavBackStackEntry` or Activity).

**Steps:**
1. Create `GameLoop.kt` (plain class, not Hilt singleton)
2. ViewModel instantiates it post-engine-init, starts in `init {}`, stops in `onCleared()`
3. Remove inline tick coroutine from `GameViewModel`

**Expected outcome:** Tick loop is a testable unit. ViewModel shrinks. No Hilt init chain changes needed.

---

## Task 6 — GameStateChange Audit (PREREQUISITE for DB Migration)

**Why:** `_changes.value` emitted ad-hoc throughout `GameEngine`. Many mutations don't emit. UI can show stale values.

**⚠️ Critical finding:** `TransactionCompleted` exists in the sealed class but is **never emitted**
anywhere in `GameEngine`. The `ringUpItemAndTrackMetrics()` method checks `totalTransactionsCompleted`
delta but never calls `_changes.value = GameStateChange.TransactionCompleted(...)`.
Similarly, `DayRolledOver` does not exist at all — `DayManager.rollOverDay()` emits nothing.

**The DB migration plan depends on both of these emissions** to trigger `HistoryRepository` writes.
This task must be completed (at least for those two events) before the DB migration can work.

**Steps:**
1. **Immediate:** Add `_changes.value = GameStateChange.TransactionCompleted(tx)` in
   `ringUpItemAndTrackMetrics()` after the transaction-completed block
2. **Immediate:** Add `DayRolledOver(dayNumber: Int, metrics: DailyMetrics)` to the sealed class
   and emit it in `tick()` at the day-rollover block (after `dayManager.rollOverDay()`)
3. List every `fun` in `GameEngine.kt`
4. For each: verify it emits the correct `GameStateChange` (or emits nothing intentionally)
5. Document intentional non-emitting mutations with `// no emit: transient only`
6. Consider replacing individual change types with domain-level batching: `GameStateChange.DomainChanged(domain: GameDomain)`

**Expected outcome:** No silent state mutations. Easier to reason about recomposition triggers.
DB migration plan's ViewModel listeners will function correctly.

---

## Execution Order

```
Task 6 (change audit) ← FIRST: fix TransactionCompleted + add DayRolledOver emission
                         (prerequisite for DB migration plan)
Task 1 (versioning)   ← before ANY state refactor to protect saves
Task 3 (sub-states    ← includes DB history migration (removes salesHistory/completedDayMetrics,
  + DB migration)        adds Room tables). Do together to avoid double-touching
                         GameStateData.kt and GameStateSerializer.kt.
Task 2 (tick phases)  ← easier once sub-states are clean
Task 4 (mappers)      ← parallel with Task 2, UI-only changes
Task 5 (GameLoop)     ← last, lowest risk impact
```

---

## Files Touched Per Task

| Task | Primary Files |
|------|--------------|
| 1 | `GameStateSerializer.kt`, `GameStateData.kt`, `build.gradle.kts` |
| 2 | `GameEngine.kt` |
| 3 | `GameStateData.kt`, all `*Manager.kt` files, + all DB migration files (see `plan-db-history-migration.md`) |
| 4 | `GameViewModel.kt`, `ui/state/mappers/*`, `IncrementalUiStateBuilder.kt` |
| 5 | `GameViewModel.kt`, new `GameLoop.kt` |
| 6 | `GameEngine.kt`, `GameStateChange.kt` |
