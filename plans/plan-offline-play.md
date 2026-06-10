# Offline Play: Performance Tests + Implementation

## Context

The game now has many autonomous systems (auto-ordering, auto-hire, spoilage, trucks, store manager actions, fresh handlers, stocking managers). The user wants **offline play** — the store keeps running while the app is closed. Before implementing, we need accurate performance benchmarks to gauge how fast we can simulate catch-up ticks.

**Offline design:** 0.25x game speed while offline (1 real second = 0.5 game minutes = 30 game seconds). Cap at 7 game days (10,080 game minutes). At 0.25x, the cap is hit after ~336 real minutes (~5.6 hours) away. Conversion: `elapsedRealMs * 120 * 0.25 / 60000 = game minutes`. Accelerated tick simulation using the existing `TickOrchestrator.tick()` in a tight loop.

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
- 10,080 ticks at deltaMs=500 (1 game-minute per tick)
- Dismiss `showEndOfDayReport` each day boundary (otherwise time pauses and simulation stalls)
- Pre-schedule trucks with orders so arrivals happen
- Set up staff shifts and registers
- Report per-day timing and total
- Threshold: < 10 seconds total (this IS the offline catch-up budget)

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

Add `offlineMode: Boolean = false` to `TickOrchestrator.tick()`. When true:
1. **Skip `playerTickProcessor.process()`** entirely — player is offline
2. **Reduce `utilizationTracker.sample()` frequency** — only call every 60 ticks (~1 game hour) instead of every tick. Auto-hire only reads daily snapshot so hourly sampling is sufficient. Track with a simple counter in the runner.
3. **Keep all DailyMetrics event lists active** — player can review daily metrics for offline days, so all event tracking stays on.

`GameEngine.tick()` passes `offlineMode` through to `TickOrchestrator.tick()`.

### 2C. Core simulation loop — `domain/offline/OfflineCatchUpRunner.kt`
- Takes `GameEngine`, runs simulation on caller's thread
- `simulate(elapsedRealMs, onProgress)` → `OfflineCatchUpResult`
- Converts real time to game minutes: `elapsedRealMs * 120 * 0.25 / 60000` (0.25x game speed)
- Caps result at 10,080 game minutes (7 game days)
- Forces `PlayerRole.MANAGE`, unpauses time
- Dismisses end-of-day reports automatically each day boundary
- Calls `onProgress` every 100 ticks for UI updates
- Snapshots start/end state to compute summary deltas
- Pattern matches existing `simulateRestOfDay()` in GameEngine

### 2D. ViewModel integration — `GameViewModel.kt`
- Add `_offlineState: MutableStateFlow<OfflineState>`
- **Gap detection in tick loop:** Track `lastTickWallClock`. If gap > 1 minute detected, trigger catch-up instead of normal tick. Handles both cold start (init) and warm start (backgrounded).
- Guard normal tick loop: skip ticks while `offlineState != Idle`
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

### 2G. Edge cases
- **End-of-day report pauses time:** Runner auto-dismisses every tick check
- **Player role:** Forced to MANAGE while offline, restored to previous role on return
- **Store hours:** Normal open/close cycle continues — no traffic while closed, staff work per their shifts
- **Warm vs cold start:** Gap detection in tick loop handles both
- **Minimum threshold:** Skip catch-up if < 1 minute offline
- **Thread safety:** Normal tick loop is guarded by `offlineState == Idle`, no concurrent engine access

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
- `app/src/main/.../domain/tick/TickOrchestrator.kt` — `offlineMode` parameter, conditional skip of player/utilization
- `app/src/main/.../domain/GameEngine.kt` — pass `offlineMode` through to tick()
- `app/src/main/.../ui/viewmodels/GameViewModel.kt` — offline state, gap detection, tick guard
- `app/src/main/.../MainActivity.kt` — offline UI overlay

---

## Verification

**Phase 1:** Run `./gradlew test --tests "*.PerformanceRegressionTest"` — all tests pass, offline benchmark reports timing
**Phase 2:** Build + install to emulator. Background app for 2+ minutes, reopen → verify catch-up screen shows, summary displays correct stats, game state advances appropriately
