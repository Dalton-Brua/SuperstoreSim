# Phase 2: Developer Onboarding Checklist

**Use this checklist when starting Phase 2 implementation**

---

## Pre-Implementation: Understanding the Codebase

- [ ] Read `AGENTS.md` - understand project architecture
- [ ] Read `PHASE_2_QUICK_START.md` - 5-minute orientation
- [ ] Read `PHASE_2_IMPLEMENTATION_GUIDE.md` - detailed specifications
- [ ] Read `PHASE_2_TESTING_PLAN.md` - testing strategy ⭐ NEW
- [ ] Run the app - understand current Phase 1 behavior:
  - [ ] App starts, time shows "CLOSED"
  - [ ] Click on "CLOSED" text, it becomes "OPEN"
  - [ ] Time advances (1 real second = 1 game minute at 1x speed)
  - [ ] Speed buttons (1x, 2x, 4x) work
- [ ] Explore codebase structure:
  - [ ] Look at `domain/GameEngine.kt` - see how tick() works
  - [ ] Look at `domain/time/TimeManager.kt` - see accumulation pattern
  - [ ] Look at `ui/GameViewModel.kt` - see event handling
  - [ ] Look at `ui/screens/home/StoreHomeScreen.kt` - see layout

---

## Implementation Checklist

### ✅ Step 1: Traffic Pattern System (2-3 hours)

**Files to create**:
- [ ] `domain/traffic/TrafficPattern.kt`
- [ ] `domain/traffic/TrafficManager.kt`

**Code tasks**:
- [ ] Copy `TrafficPattern` data class
- [ ] Copy `TrafficSchedule` object with weekday/weekend patterns
- [ ] Copy `TrafficManager` class with accumulation logic
- [ ] Verify imports are correct

**Testing**:
- [ ] Compile: `./gradlew build` - should succeed
- [ ] Create simple unit test:
  ```kotlin
  val pattern = TrafficSchedule.getPatternForTime(GameTime(6*60))
  assert(pattern.baseCustomerRate == 0.3f)
  ```
- [ ] Run test: `./gradlew test` - should pass

**Code review**:
- [ ] TrafficPattern has all required fields (hour, baseCustomerRate, averageBasketSize, peakMultiplier)
- [ ] TrafficSchedule has separate WEEKDAY and WEEKEND arrays
- [ ] TrafficManager.update() returns List<TransactionRequest>
- [ ] Accumulation logic similar to TimeManager

**Git commit**: "Phase 2: Add traffic pattern system"

---

### ⭐ UNIT TESTS FOR STEP 1

**Create**: `app/src/test/java/com/example/superstoresimulator/domain/traffic/TrafficPatternTest.kt`

- [ ] Copy TrafficPatternTest from `PHASE_2_TESTING_PLAN.md`
- [ ] Verify all test methods are included
- [ ] Run tests: `./gradlew test --tests TrafficPatternTest`
- [ ] All tests pass ✅

**Create**: `app/src/test/java/com/example/superstoresimulator/domain/traffic/TrafficManagerTest.kt`

- [ ] Copy TrafficManagerTest from `PHASE_2_TESTING_PLAN.md`
- [ ] Verify all test methods are included
- [ ] Run tests: `./gradlew test --tests TrafficManagerTest`
- [ ] All tests pass ✅

**Git commit**: "Phase 2: Add traffic system unit tests"

---

### ✅ Step 2: Player Role Enum (15 minutes)

**File to create**:
- [ ] `domain/player/PlayerRole.kt`

**Code tasks**:
- [ ] Copy enum with NONE, CASHIER, STOCKER
- [ ] Verify package name: `com.example.superstoresimulator.domain.player`

**Testing**:
- [ ] Compile: `./gradlew build`
- [ ] Verify: `PlayerRole.CASHIER.name == "CASHIER"`

**Git commit**: "Phase 2: Add PlayerRole enum"

---

### ⭐ UNIT TESTS FOR STEP 2

**Create**: `app/src/test/java/com/example/superstoresimulator/domain/player/PlayerRoleTest.kt`

- [ ] Copy PlayerRoleTest from `PHASE_2_TESTING_PLAN.md`
- [ ] Verify all test methods are included
- [ ] Run tests: `./gradlew test --tests PlayerRoleTest`
- [ ] All tests pass ✅

**Git commit**: "Phase 2: Add PlayerRole enum unit tests"

---

### ✅ Step 3: Update GameState (30 minutes)

**File to modify**:
- [ ] `domain/GameStateData.kt`

**Code tasks**:
- [ ] Add import: `import com.example.superstoresimulator.domain.player.PlayerRole`
- [ ] Add three fields to GameState data class:
  ```kotlin
  val playerRole: PlayerRole = PlayerRole.NONE,
  val playerCashierProgress: Float = 0f,
  val playerStockerProgress: Float = 0f,
  ```
- [ ] Verify indentation and placement

**Testing**:
- [ ] Compile: `./gradlew build`
- [ ] No errors

**Git commit**: "Phase 2: Add player role fields to GameState"

---

### ✅ Step 4: GameEngine - Add TrafficManager (1-2 hours)

**File to modify**:
- [ ] `domain/GameEngine.kt`

**Code tasks**:
- [ ] Add import: `import com.example.superstoresimulator.domain.traffic.TrafficManager`
- [ ] In `init()` block, add: `private val trafficManager = TrafficManager()`
- [ ] In `tick()` method, after time update, add customer generation:
  ```kotlin
  if (state.storeState == StoreState.OPEN) {
      val newCustomers = trafficManager.update(state, deltaSeconds)
      newCustomers.forEach { request ->
          state = txEngine.generateRandomTransaction(state, request.itemCount)
      }
  }
  ```

**Testing**:
- [ ] Compile: `./gradlew build`
- [ ] Run app, open store
- [ ] Wait 5 seconds at 12 PM game time (busy hour)
- [ ] Verify: Transactions appear in transaction summary card
- [ ] Check different hours (6 AM quiet, 12 PM busy)

**Debugging if needed**:
- [ ] Add log: `Log.d("TrafficManager", "Generated ${newCustomers.size} customers")`
- [ ] Check logcat for output

**Git commit**: "Phase 2: Integrate TrafficManager into GameEngine"

---

### ✅ Step 5: GameEngine - Add Player Work Methods (1.5 hours)

**File to modify**:
- [ ] `domain/GameEngine.kt`

**Code tasks**:
- [ ] Copy `performPlayerCashierWork(deltaSeconds: Double): GameState` method
- [ ] Copy `performPlayerStockerWork(deltaSeconds: Double): GameState` method
- [ ] In `tick()` method, add player work processing (after staff work):
  ```kotlin
  when (state.playerRole) {
      PlayerRole.CASHIER -> state = performPlayerCashierWork(deltaSeconds)
      PlayerRole.STOCKER -> state = performPlayerStockerWork(deltaSeconds)
      PlayerRole.NONE -> {
          state = state.copy(
              playerCashierProgress = 0f,
              playerStockerProgress = 0f
          )
      }
  }
  ```

**Testing**:
- [ ] Compile: `./gradlew build`
- [ ] Manually set `state.playerRole = PlayerRole.CASHIER` and verify items ring up
- [ ] Check progress accumulation

**Git commit**: "Phase 2: Add player work logic to GameEngine"

---

### ⭐ UNIT & INTEGRATION TESTS FOR STEPS 4-5

**Create**: `app/src/test/java/com/example/superstoresimulator/domain/GameEnginePlayerWorkTest.kt`

- [ ] Copy GameEnginePlayerWorkTest from `PHASE_2_TESTING_PLAN.md`
- [ ] Verify all test methods are included
- [ ] Run tests: `./gradlew test --tests GameEnginePlayerWorkTest`
- [ ] All tests pass ✅

**Create**: `app/src/test/java/com/example/superstoresimulator/integration/TrafficGameEngineIntegrationTest.kt`

- [ ] Copy TrafficGameEngineIntegrationTest from `PHASE_2_TESTING_PLAN.md`
- [ ] Verify all test methods are included
- [ ] Run tests: `./gradlew test --tests TrafficGameEngineIntegrationTest`
- [ ] All tests pass ✅

**Git commit**: "Phase 2: Add GameEngine and integration tests"

---

### ✅ Step 6: Create GameEvent (15 minutes)

**File to modify**:
- [ ] `ui/GameEvent.kt`

**Code tasks**:
- [ ] Add import: `import com.example.superstoresimulator.domain.player.PlayerRole`
- [ ] Add event: `data class SetPlayerRole(val role: PlayerRole) : GameEvent`

**Testing**:
- [ ] Compile: `./gradlew build`

**Git commit**: "Phase 2: Add SetPlayerRole event"

---

### ✅ Step 7: ViewModel Handler (15 minutes)

**File to modify**:
- [ ] `ui/viewmodels/GameViewModel.kt`

**Code tasks**:
- [ ] In `onEvent()` method, add handler:
  ```kotlin
  is GameEvent.SetPlayerRole -> {
      gameEngine.state = gameEngine.state.copy(playerRole = event.role)
  }
  ```

**Testing**:
- [ ] Compile: `./gradlew build`

**Git commit**: "Phase 2: Wire SetPlayerRole event handler"

---

### ✅ Step 8: Update GameUIState (15 minutes)

**File to modify**:
- [ ] `ui/state/GameUiState.kt`

**Code tasks**:
- [ ] Add import: `import com.example.superstoresimulator.domain.player.PlayerRole`
- [ ] Update `TimeUIState` to include:
  ```kotlin
  val playerRole: PlayerRole = PlayerRole.NONE
  ```

**Testing**:
- [ ] Compile: `./gradlew build`

**Git commit**: "Phase 2: Add playerRole to TimeUIState"

---

### ✅ Step 9: Create UI Components (2 hours)

**Files to create**:
- [ ] `ui/components/PlayerRoleIndicator.kt`
- [ ] `ui/components/PlayerRoleButtons.kt`

**Code tasks**:
- [ ] Copy PlayerRoleIndicator composable
- [ ] Copy PlayerRoleButtons composable
- [ ] Verify imports

**Testing**:
- [ ] Compile: `./gradlew build`
- [ ] Visual preview in Android Studio (if available)

**Git commit**: "Phase 2: Add player role UI components"

---

### ✅ Step 10: Integrate into Home Screen (1-2 hours)

**File to modify**:
- [ ] `ui/screens/home/StoreHomeScreen.kt`

**Code tasks**:
- [ ] Add imports for new components
- [ ] Add PlayerRoleIndicator to layout (near TimeDisplayBar)
- [ ] Add PlayerRoleButtons to layout
- [ ] Wire button callbacks:
  ```kotlin
  PlayerRoleButtons(
      currentRole = state.time?.playerRole ?: PlayerRole.NONE,
      hasPendingTransactions = state.transactions.current.lines.isNotEmpty(),
      hasBackroomItems = /* check inventory */,
      onRoleChanged = { role -> viewModel.onEvent(GameEvent.SetPlayerRole(role)) }
  )
  ```

**Testing**:
- [ ] Compile: `./gradlew build`
- [ ] Run app
- [ ] Buttons appear on home screen
- [ ] Clicking buttons toggles between roles
- [ ] Indicator shows current role

**Git commit**: "Phase 2: Integrate player role UI into home screen"

---

### ✅ Step 11: Playtest & Balance (2-3 hours)

**Testing scenarios**:
- [ ] Early morning (6-7 AM): Very few customers arrive
- [ ] Lunch time (12 PM): Many customers arrive (BUSY)
- [ ] Afternoon (2-4 PM): Medium traffic
- [ ] Evening (5-6 PM): Many customers arrive (BUSY)
- [ ] Late evening (8-9 PM): Few customers

**Balance checks**:
- [ ] Peak hours feel noticeably busier than quiet hours
- [ ] Player working as cashier can keep up with customers
- [ ] With no staff and not working, queue grows
- [ ] Game is still playable and fun

**If adjustments needed**:
- [ ] Modify traffic rates in `TrafficPattern.kt`
- [ ] Adjust `itemsPerSecond` in `performPlayerCashierWork()`
- [ ] Retest and repeat

**Git commit**: "Phase 2: Tune traffic rates and player work speed"

---

### ✅ Step 12: Remove Old Manual UI (Final Step - 1 hour)

**⚠️ ONLY DO THIS AFTER AUTONOMOUS SYSTEM IS FULLY TESTED**

**Files to modify**:
- [ ] `ui/GameEvent.kt`
  - [ ] Remove `data object RingUp : GameEvent`
  - [ ] Remove `data class RingUpItem(val itemId: Int) : GameEvent`
  - [ ] Remove `data object StartTransaction : GameEvent`

- [ ] `ui/GameViewModel.kt`
  - [ ] Remove handlers for RingUp, RingUpItem, StartTransaction
  - [ ] Remove related calls to `gameEngine.ringUpItem()`

- [ ] `ui/dialogs/TransactionDetailDialog.kt`
  - [ ] Remove `onRingUpItem` parameter
  - [ ] Remove "Ring Up Item" button
  - [ ] Keep "View Item" button

- [ ] `ui/screens/home/StoreHomeScreen.kt`
  - [ ] Remove RingUpButton component
  - [ ] Remove StartTransaction button (if exists)
  - [ ] Remove related callbacks

- [ ] `ui/components/RingUpButton.kt`
  - [ ] DELETE FILE

**Testing**:
- [ ] Compile: `./gradlew build`
- [ ] Run app
- [ ] No more manual Ring Up buttons
- [ ] Cashier/Stocker buttons work
- [ ] Transactions still process automatically

**Git commit**: "Phase 2: Remove manual player action UI (finalization)"

---

## ⭐ TESTING SUMMARY

### Unit Tests Created During Implementation

| Step | Test File | Test Count | Purpose |
|------|-----------|-----------|---------|
| Step 1 | TrafficPatternTest | 8 tests | Traffic patterns validation |
| Step 1 | TrafficManagerTest | 7 tests | Customer generation logic |
| Step 2 | PlayerRoleTest | 5 tests | Enum structure |
| Step 4-5 | GameEnginePlayerWorkTest | 4 tests | Player work logic |
| Step 4-5 | TrafficGameEngineIntegrationTest | 3 tests | Component integration |
| **Total** | **5 files** | **27 tests** | Core Phase 2 coverage |

### Test Running Commands

```bash
# Run all Phase 2 tests
./gradlew test

# Run specific test file
./gradlew test --tests TrafficPatternTest

# Run all traffic tests
./gradlew test --tests Traffic*

# Run with verbose output
./gradlew test --info

# Generate coverage report
./gradlew test jacocoTestReport
```

### Unit Testing Checklist

- [ ] All 27 unit tests created
- [ ] All tests compile without errors
- [ ] All tests pass: `./gradlew test` succeeds
- [ ] Test coverage > 60% for Phase 2 code
- [ ] Critical paths (traffic, player work) have tests

### Integration Testing Checklist

- [ ] TrafficGameEngineIntegrationTest tests component interaction
- [ ] GameEngine can open store and generate customers
- [ ] Player role affects game state correctly
- [ ] Tests pass: `./gradlew test --tests *Integration*`

### Manual Testing Checklist (See PHASE_2_TESTING_PLAN.md)

**Traffic Pattern Verification**:
- [ ] 6 AM: Very few customers (0-2 in 30 sec)
- [ ] 12 PM: Many customers (8-15 in 30 sec)
- [ ] Lunch is 4x+ busier than early morning

**Player Cashier Role**:
- [ ] Button toggles and shows active state
- [ ] Items process automatically
- [ ] Transaction completes without manual clicks
- [ ] 3+ transactions complete in 10 seconds

**Player Stocker Role**:
- [ ] Button toggles and shows active state
- [ ] Shelf stock increases
- [ ] Backroom stock decreases
- [ ] Items stock automatically every ~3 seconds

**Mutual Exclusivity**:
- [ ] Can't be cashier AND stocker simultaneously
- [ ] Only one button highlighted at a time
- [ ] Can toggle to NONE (stop working)

**Peak Hour Challenge**:
- [ ] Lunch hour (11:50-12:10) feels hectic
- [ ] Game remains playable
- [ ] Player can keep up with rush if working
- [ ] Queue management feels fair

### Full Test Run Procedure

Before marking Phase 2 complete:

```bash
# 1. Run all unit tests
./gradlew test

# 2. Check for compilation issues
./gradlew build

# 3. Run app manually
./gradlew installDebug
# Open app, complete manual tests

# 4. View coverage
./gradlew test jacocoTestReport
# Check report at: app/build/reports/jacoco/test/html/index.html
```

Expected Results:
- ✅ All 27 unit tests pass
- ✅ Build succeeds
- ✅ Manual gameplay feels good
- ✅ Coverage > 60%

---

## Final Testing Checklist

**Complete Playthrough Test**:
- [ ] Open app
- [ ] Store shows CLOSED, time shows current time
- [ ] Click CLOSED text
- [ ] Store shows OPEN, time advances
- [ ] Wait 30 seconds (reaches 12 PM game time = lunch rush)
- [ ] Transactions appear automatically in queue
- [ ] Click "Work as Cashier" button
- [ ] Items process automatically
- [ ] Queue empties as items ring up
- [ ] Click "Stop" button
- [ ] Queue starts growing again (no processing)
- [ ] Click "Work as Stocker" button
- [ ] Can't work as Cashier while Stocker (mutually exclusive)
- [ ] Click "Stop" button
- [ ] Switch time to early morning (6 AM)
- [ ] Very few customers arrive (quiet hour)
- [ ] Switch time to evening (5 PM)
- [ ] Many customers arrive (peak hour)

**Success**: If all above work, Phase 2 is complete! ✅

---

## Troubleshooting Guide

| Problem | Check | Solution |
|---------|-------|----------|
| App won't compile | Import statements | Verify all imports are correct, no typos |
| No customers | TrafficManager in tick() | Verify `trafficManager.update()` is being called |
| No customers | Store state | Verify `storeState == OPEN` |
| Buttons don't appear | Layout integration | Verify buttons added to StoreHomeScreen layout |
| Buttons don't work | Event wiring | Verify `onRoleChanged` callback implemented |
| Items don't process | Player work method | Verify `performPlayerCashierWork()` is called |
| Progress doesn't accumulate | Math | Verify progress += itemsPerSecond * deltaSeconds |
| Game crashes | Null reference | Check for null currentTransaction before accessing .lines |

---

## Success Criteria

✅ **Phase 2 is complete when**:
- Customers arrive autonomously based on game time
- Player can toggle "Work as Cashier"
- Items process automatically when player is cashier
- Only one player role active at a time
- Peak hours feel busier than quiet hours
- Old manual buttons are removed
- Game is playable and fun

---

## Time Tracking

Start time: ___________  
Target completion: ___________

- [ ] Step 1 completed: ___________
- [ ] Step 2 completed: ___________
- [ ] Step 3 completed: ___________
- [ ] Step 4 completed: ___________
- [ ] Step 5 completed: ___________
- [ ] Step 6 completed: ___________
- [ ] Step 7 completed: ___________
- [ ] Step 8 completed: ___________
- [ ] Step 9 completed: ___________
- [ ] Step 10 completed: ___________
- [ ] Step 11 completed: ___________
- [ ] Step 12 completed: ___________

Total time spent: ___________

---

**Need help?** Check PHASE_2_IMPLEMENTATION_GUIDE.md or PHASE_2_QUICK_START.md

**Ready to start?** Begin with Step 1 ✨

