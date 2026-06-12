# Architecture Refactor Plan (Remaining Work)

Generated: 2026-05-30 | **Audited: 2026-06-12**
Codebase: SuperstoreSimulator (Android / Kotlin / Jetpack Compose)

---

## Completed Tasks (for reference)

| Task | Status | Evidence |
|------|--------|----------|
| Task 1 — kotlinx.serialization | **DONE** | `GameStateSerializer.kt` = 28 lines (was 1,033). 48 `@Serializable` annotations across 17 files. `saveVersion`/`SAVE_VERSION`, legacy fallback via `LegacyGameStateDeserializer`. |
| Task 2 — Tick phase decomposition | **DONE** | `TickOrchestrator` (111 lines) with named phases: `advanceTime`, `processSpoilage`, `dayRolloverProcessor`, `updateStoreState`, `updatePricing`, traffic/staff/player processors. Dedicated `TickOrchestratorTest`. `GameEngine` down from 1,046 → 448 lines. |
| Task 6 — GameStateChange audit | **DONE (deleted)** | Entire `GameStateChange` sealed class removed in commit `cc6b7c2`. Pipeline replaced with direct state reads. |

---

## Remaining Task A — DB History Migration (was Task 3, partial)

**Why:** `salesHistory: List<Transaction>` and `completedDayMetrics: List<DailyMetrics>` still live on `GameState`. They grow unboundedly and inflate save files. Should move to Room DB.

**Current usage:** ~30 call sites across `GameEngine`, `TransactionEngine`, `DayManager`, `DayRolloverProcessor`, `StaffManager`, `OfflineCatchUpRunner`, `GameViewModel`, UI screens.

**Steps:**
1. Create Room entities + DAO for transactions and daily metrics
2. Wire `DayRolloverProcessor` to flush `salesHistory` to Room at rollover (it already clears the list — just needs to write first)
3. Wire `DayManager.rollOverDay()` to persist `currentDayMetrics` snapshot to Room
4. Migrate UI reads (`SalesHistoryScreen`, `GameViewModel`, `RegisterDetailDialog`) to query Room instead of `GameState`
5. Migrate `OfflineCatchUpRunner` and `StaffManager` reads
6. Remove `salesHistory` and `completedDayMetrics` from `GameState` + bump `SAVE_VERSION`
7. Add migration in `GameStateSerializer` for old saves (drop the fields, they'll be empty on first load)

**Priority:** Low. Works fine in-memory for current game scale. Becomes important if save file size or history queries become a problem.

---

## Remaining Task B — ViewModel Slimming (was Task 4, partial)

**Status:** 7 mapper files extracted (`RegistersUiMapper`, `StaffUiMapper`, `PricingUiMapper`, `ProgressionUiMapper`, `DeliveryUiMapper`, `VendorUiMapper`, `MemoizedInventoryMapper`). GameViewModel still 601 lines (target was ~300).

**Remaining work:** Identify what's still inline in ViewModel that could move to mappers or `IncrementalUiStateBuilder`. Likely: transaction mapping, time/metrics mapping, event routing consolidation.

**Priority:** Low. ViewModel is functional, just larger than ideal.

---

## Remaining Task C — GameLoop Extraction (was Task 5, not started)

**Why:** Tick loop in ViewModel ties simulation to UI lifecycle. Extracting it makes the loop independently testable.

**Approach:** Plain class owned by ViewModel (not Hilt singleton — avoids lazy-init conflicts):

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

**Priority:** Low. Current setup works because `@HiltViewModel` survives rotation.
