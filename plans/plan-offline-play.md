# Offline Play: Performance Tests + Implementation

## Context

The game now has many autonomous systems (auto-ordering, auto-hire, spoilage, trucks, store manager actions, fresh handlers, stocking managers). The user wants **offline play** — the store keeps running while the app is closed. Before implementing, we need accurate performance benchmarks to gauge how fast we can simulate catch-up ticks.

**Offline design:** 0.25x game speed while offline (1 real second = 0.5 game minutes = 30 game seconds). Cap at 7 game days (10,080 game minutes). At 0.25x, the cap is hit after ~336 real minutes (~5.6 hours) away. Conversion: `elapsedRealMs * TimeManager.BASE_SPEED * 0.25 / 60000 = game minutes` (extract `BASE_SPEED = 120f` as a const in TimeManager — also fix its stale "60.0f" comment). Accelerated tick simulation using the existing `TickOrchestrator.tick()` in a tight loop.

**Design decisions (resolved):**
- **Backgrounding pauses the game.** The tick loop is lifecycle-gated: stops on `onPause`, and catch-up at 0.25x runs on resume. No more full-speed background simulation.
- **Player pause is respected.** If `playerPausedTime && !pausedByEndOfDay` at save time, skip catch-up entirely — the store stays frozen while away.
- **Summary only.** All offline end-of-day reports are auto-dismissed; `OfflineSummaryDialog` is the single recap (per-day details remain in Metrics screen). No trailing end-of-day report even if catch-up lands exactly on a rollover.
- **Speed is the only offline nerf.** Per-game-minute economy (traffic, revenue, spoilage) is identical to online; the 0.25x mapping + 7-day cap bound total gain.

---

## Phase 1: Update Performance Tests

### Problem
Current `PerformanceRegressionTest.kt` only covers cashiers + stockers with non-perishable items. Missing: fresh handlers, stocking managers, managers, perishable items, spoilage, day rollovers, auto-ordering, auto-hire, truck arrivals, store manager actions.

### Changes to `PerformanceRegressionTest.kt`

**1. Update `createLargeItemCatalog()`**
- Add `shelfLifeDays` to perishable categories: DAIRY (5d), BAKERY (3d), PRODUCE (4d), MEAT (5d)
- ~200 of 500 items become perishable (indices hitting those categories mod 10)

**2. Add helper: `createInventoryForCatalog()`**
- Takes item list + shelf/backroom quantities
- Creates perishable batches with realistic `expirationDay = currentDay + shelfLifeDays` (from item data)
- Non-perishable batches use `Int.MAX_VALUE` as before

**3. Update `createStaffRegistry()`**
- Accept all 4 types: `cashierCount`, `stockerCount`, `freshHandlerCount`, `managerCount`
- Accept optional `stockingManagerCount` — promotes that many stockers from BASE→FAST→MANAGER
- Accept optional `storeManagerCount` — promotes managers from BASE→FAST→MANAGER

**4. Update "realistic load" test**
- Add 2 fresh handlers + 1 manager to staff
- Enable `freshAutoOrderConfig`, `normalAutoOrderConfig`
- Items now include perishables (from updated catalog)
- Adjust threshold if needed (300ms → up to 500ms)

**5. Update "maximum load" test**
- Full staff: 25 cashiers, 17 stockers + 3 stocking managers, 10 fresh handlers, 2 managers (1 promoted to store manager)
- Enable all configs: auto-order, store manager config, truck config with 3 delivery days
- Set `currentStoreSize = StoreSize.SUPERSTORE`
- Adjust threshold if needed (500ms → up to 800ms)

**6. New test: "offline simulation benchmark — 7 game days"**
- 500 items (mix perishable/non-perishable), late-game staff, all autonomous configs enabled
- **Force `gameSpeedMultiplier = 1f`** — the "1 game-minute per tick" math only holds at 1x
- 10,080 ticks at deltaMs=500 (1 game-minute per tick)
- Dismiss via `dayManager.dismissEndOfDayReport()` each day boundary (rollover sets `playerPausedTime = true`, which makes `TickOrchestrator.tick()` early-return — clearing only the flag is not enough)
- Pre-schedule trucks with orders so arrivals happen
- Set up staff shifts and registers
- Report per-day timing and total
- Threshold: < 5 seconds total on desktop JVM. Desktop is 3–10x faster than a phone, so 10s desktop could mean 60s+ on device; 5s desktop keeps the device-side catch-up plausibly under ~30s worst case. Verify actual device timing in Phase 2 emulator testing.

**7. New test: "day rollover performance"**
- Fast-forward to near midnight, measure 14 consecutive day-boundary crossings
- Full autonomous state: auto-hire, truck arrivals, auto-ordering, metrics snapshot
- Threshold: < 500ms per rollover (10 ticks around midnight)

### Key insight from `GameEngine.simulateRestOfDay()`
Already uses `deltaMs = 500L` in a tight loop — same pattern we'll use. Also dismisses end-of-day report handling is required.

---

## Phase 2: Offline Play Implementation

### 2A. New data classes — `domain/offline/OfflineModels.kt`
- `OfflineCatchUpResult`: gameMinutesSimulated, daysCrossed, revenue, expenses, itemsExpired, trucksArrived, staffHired, completedDayMetrics
- `OfflineProgress`: currentMinute, totalMinutes, currentDay, fractionComplete
- `OfflineState` sealed interface: `Idle`, `CatchingUp(progress)`, `Summary(result)`

### 2B. Offline tick optimizations — `TickOrchestrator.tick()` gains `offlineMode` parameter

Add `offlineMode: Boolean = false` and `sampleUtilization: Boolean = true` to `TickOrchestrator.tick()`. When `offlineMode` is true:
1. **Skip `playerTickProcessor.process()`** entirely — player is offline (mostly redundant since role is forced to MANAGE, whose branch is near-free, but keeps intent explicit)
2. **Call `utilizationTracker.sample()` only when `sampleUtilization` is true** — the runner computes `tickIndex % 60 == 0` (~1 game hour) and passes it in, keeping the orchestrator stateless. Auto-hire reads accumulated busy/total tick ratios from `snapshotDailyMetrics()`, so sparse sampling keeps ratios unbiased.
3. **Keep all DailyMetrics event lists active** — player can review daily metrics for offline days, so all event tracking stays on.

`GameEngine.tick()` passes both parameters through to `TickOrchestrator.tick()`.

### 2C. Core simulation loop — `domain/offline/OfflineCatchUpRunner.kt`
- Takes `GameEngine`, runs simulation on caller's thread
- `simulate(elapsedRealMs, onProgress)` → `OfflineCatchUpResult`
- Converts real time to game minutes: `elapsedRealMs * TimeManager.BASE_SPEED * 0.25 / 60000` (0.25x game speed; reference the const, don't hardcode 120)
- Caps result at 10,080 game minutes (7 game days)
- **Forces `gameSpeedMultiplier` to 1x for the duration, restores the player's multiplier after.** Two speed paths must both be at 1x: `TimeManager.config` (drives `advanceTime`) and `state.storeConfig.gameSpeedMultiplier` (drives staff processing) — go through `gameEngine.setGameSpeed(1f)`, which sets both. At 4x, each 500ms tick = 4 game minutes and the tick-count math breaks.
- Sets `gameEngine.isSimulating = true` for the duration (same guard `SkipDay` uses) so the two simulation paths can't overlap
- Forces `PlayerRole.MANAGE`, restores previous role after. Unpauses time only when the pause came from an end-of-day report (`pausedByEndOfDay`) — an explicit player pause means catch-up was already skipped upstream (see 2D)
- Dismisses end-of-day reports each day boundary via `dayManager.dismissEndOfDayReport()` (which also restores `playerPausedTime` correctly — rollover auto-pauses time, so clearing only the flag would stall the loop)
- Calls `onProgress` every 100 ticks for UI updates
- Snapshots start/end state to compute summary deltas
- Pattern matches existing `simulateRestOfDay()` in GameEngine

### 2D. ViewModel integration — `GameViewModel.kt`
- Add `_offlineState: MutableStateFlow<OfflineState>`
- **Lifecycle-gate the tick loop.** Today's `while(true) { delay(16); Tick }` in `viewModelScope` keeps running while backgrounded (until process death), so a wall-clock gap would never appear on warm start — the game would silently simulate at full speed instead. Expose `onAppPaused()`/`onAppResumed()` on the ViewModel (called from MainActivity lifecycle, or drive the loop via `repeatOnLifecycle`): pause stops the tick loop and records `pausedWallClock`; resume computes the gap and triggers catch-up if above threshold.
- **Cold start:** in `init`, after `loadState()`, compute gap from the already-persisted `gameStateRepository.getLastSaveTime()` (written on every save) — no new tracking needed. Set `_uiState` first so the app can render, then enter `CatchingUp`.
- **Skip catch-up entirely if the save has an explicit player pause** (`playerPausedTime && !pausedByEndOfDay`) — pause means pause; the store stays frozen while away.
- **Minimum threshold: 5 real minutes** (= 2.5 game minutes at 0.25x). At 1 minute the overlay would flash for a single tick's worth of simulation on quick app switches.
- Guard **all of `onEvent`** (early return), not just the tick loop, while `offlineState != Idle` — the runner mutates `gameEngine.state` on `Dispatchers.Default`, and the overlay doesn't block every input path (back button, snackbar actions, lifecycle events)
- Make `GameEngine.state` `@Volatile` — the runner writes it from `Dispatchers.Default` while the main thread reads it; `isSimulating` already is
- `performOfflineCatchUp()` runs on `Dispatchers.Default`, updates progress flow, saves state after completion
- `dismissOfflineSummary()` returns to `Idle`

### 2E. UI — `ui/screens/offline/`
- `OfflineCatchUpScreen.kt`: Full-screen overlay with progress bar, "Day X", "Simulating..." Blocks all game interaction during catch-up
- `OfflineSummaryDialog.kt`: Dialog showing revenue, expenses, spoilage, trucks, staff hired. "Continue" button dismisses

### 2F. MainActivity integration
- Observe `offlineState` flow
- When `CatchingUp` → show full-screen overlay, block game UI
- When `Summary` → show dialog over normal game UI
- When `Idle` → normal game
- Wire `onPause`/`onResume` to the ViewModel's lifecycle hooks (see 2D)
- **`onPause`/`onStop` save during catch-up:** if the user backgrounds mid-catch-up, the existing save calls persist the intermediate state — safe (GameState is immutable, partial progress is kept), but `KEY_LAST_SAVE_TIME` resets to now, so the un-simulated remainder of the original gap is dropped. Accept this; document it in the runner.

### 2G. Edge cases
- **End-of-day report pauses time:** Runner auto-dismisses via `dayManager.dismissEndOfDayReport()` each day boundary. All offline reports are suppressed — `OfflineSummaryDialog` is the single recap, no trailing end-of-day report even when catch-up lands exactly on a rollover (per-day details remain in Metrics screen)
- **Player role:** Forced to MANAGE while offline, restored to previous role on return. Restoring CASHIER is safe — `PlayerTickProcessor` already resets player progress if a staff cashier took the register meanwhile
- **Player paused before leaving:** catch-up skipped entirely (`playerPausedTime && !pausedByEndOfDay`)
- **Store hours:** Normal open/close cycle continues — no traffic while closed, staff work per their shifts. (Possible later perf win: fast-skip closed overnight hours, ~35% fewer ticks — deferred, adds complexity)
- **Warm vs cold start:** Warm handled by lifecycle pause/resume hooks; cold handled in init via `getLastSaveTime()`
- **Minimum threshold:** Skip catch-up if < 5 real minutes offline
- **Thread safety:** All of `onEvent` guarded by `offlineState == Idle`; `isSimulating` set during catch-up; `GameEngine.state` marked `@Volatile`

---

## Files to modify

**Phase 1:**
- `app/src/test/.../PerformanceRegressionTest.kt` — all test updates

**Phase 2 (new files):**
- `app/src/main/.../domain/offline/OfflineModels.kt`
- `app/src/main/.../domain/offline/OfflineCatchUpRunner.kt`
- `app/src/main/.../ui/screens/offline/OfflineCatchUpScreen.kt`
- `app/src/main/.../ui/screens/offline/OfflineSummaryDialog.kt`

**Phase 2 (modified files):**
- `app/src/main/.../domain/tick/TickOrchestrator.kt` — `offlineMode` + `sampleUtilization` parameters, conditional skip of player/utilization
- `app/src/main/.../domain/GameEngine.kt` — pass parameters through to tick(); `@Volatile` on `state`
- `app/src/main/.../domain/time/TimeManager.kt` — extract `BASE_SPEED = 120f` const, fix stale comment
- `app/src/main/.../ui/viewmodels/GameViewModel.kt` — offline state, lifecycle hooks, cold-start gap via `getLastSaveTime()`, `onEvent` guard
- `app/src/main/.../MainActivity.kt` — offline UI overlay, lifecycle wiring

---

## Verification

**Phase 1:** Run `./gradlew test --tests "*.PerformanceRegressionTest"` — all tests pass, offline benchmark reports timing
**Phase 2:** Build + install to emulator. Background app for 2+ minutes, reopen → verify catch-up screen shows, summary displays correct stats, game state advances appropriately
