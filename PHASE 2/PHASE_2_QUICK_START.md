# Phase 2: Quick Start for New Developers

**Last Updated**: March 31, 2026  
**Phase 1 Status**: ✅ Complete (Time system working)  
**Phase 2 Status**: 🚀 Ready to start

---

## TL;DR - What is Phase 2?

**Goal**: Make customers arrive automatically and let player work as cashier/stocker

**Current State**: Customers need manual "Ring Up" clicks. You have to start transactions manually.

**Phase 2 State**: Customers appear automatically. You toggle "Work as Cashier" or "Work as Stocker" to process them.

---

## 5-Minute Orientation

### The Problem Phase 2 Solves
```
Before Phase 2:
  Player clicks "Start Transaction" 
  → Random items appear
  → Player clicks each item to "Ring Up"
  → Transaction done
  → Repeat manually

After Phase 2:
  Customers arrive automatically (based on time of day)
  → Transactions queue up
  → Player toggles "Work as Cashier" 
  → Items process automatically
  → Queue empties based on work speed
```

### What You Need to Know
- **TrafficManager** = Spawns customers based on hourly patterns
- **PlayerRole** = What job the player is doing (NONE, CASHIER, STOCKER)
- **GameEngine.tick()** = Already runs every frame - we add traffic + player work here
- **UI Buttons** = New "Work as Cashier" and "Work as Stocker" buttons on home screen

---

## Step-by-Step Implementation

### Step 1: Create Traffic Patterns (START HERE)
**Files**: `domain/traffic/TrafficPattern.kt` + `TrafficManager.kt`

Copy the code from **Section 1: TrafficPattern System** in the implementation guide.

**What it does**: 
- Defines how many customers arrive each hour
- Different rates for weekdays vs weekends
- Lunch rush (12 PM) and evening rush (5-6 PM) are busier

**Test it**: Create simple unit test:
```kotlin
val pattern = TrafficSchedule.getPatternForTime(GameTime(6*60))  // 6 AM
assertEquals(0.3f, pattern.baseCustomerRate)  // Should be quiet
```

---

### Step 2: Create Player Role System
**Files**: `domain/player/PlayerRole.kt`

Copy the code from **Section 2: Player Role System** in the implementation guide.

**What it does**: Enum for what the player is doing (NONE, CASHIER, STOCKER)

**Test it**: 
```kotlin
val role = PlayerRole.CASHIER
assertEquals("CASHIER", role.name)
```

---

### Step 3: Update GameState
**File**: `domain/GameStateData.kt`

Add these 3 fields:
```kotlin
val playerRole: PlayerRole = PlayerRole.NONE,
val playerCashierProgress: Float = 0f,
val playerStockerProgress: Float = 0f,
```

---

### Step 4: Integrate TrafficManager into GameEngine
**File**: `domain/GameEngine.kt`

In the `init()` block, add:
```kotlin
private val trafficManager = TrafficManager()
```

In the `tick()` method, add (after time update, before staff work):
```kotlin
if (state.storeState == StoreState.OPEN) {
    val newCustomers = trafficManager.update(state, deltaSeconds)
    newCustomers.forEach { request ->
        state = txEngine.generateRandomTransaction(state, request.itemCount)
    }
}
```

**Test it**: 
- Open store
- Wait a few seconds
- Transactions should auto-generate
- Check logcat: Should see new transactions appearing

---

### Step 5: Add Player Work Logic to tick()
**File**: `domain/GameEngine.kt`

Add this after staff work processing:
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

Then add the two methods (from Section 3 of implementation guide):
- `performPlayerCashierWork(deltaSeconds: Double): GameState`
- `performPlayerStockerWork(deltaSeconds: Double): GameState`

**Test it**: 
- Start game, toggle "Work as Cashier"
- Items should process automatically
- Check progress accumulation

---

### Step 6: Create GameEvent
**File**: `ui/GameEvent.kt`

Add:
```kotlin
data class SetPlayerRole(val role: PlayerRole) : GameEvent
```

---

### Step 7: Wire ViewModel Handler
**File**: `ui/viewmodels/GameViewModel.kt`

In `onEvent()`, add:
```kotlin
is GameEvent.SetPlayerRole -> {
    gameEngine.state = gameEngine.state.copy(playerRole = event.role)
}
```

---

### Step 8: Create UI Components
**File 1**: `ui/components/PlayerRoleIndicator.kt`
**File 2**: `ui/components/PlayerRoleButtons.kt`

Copy from implementation guide Section 7.

---

### Step 9: Add to Home Screen
**File**: `ui/screens/home/StoreHomeScreen.kt`

Add the indicator and buttons to the layout (similar to TimeDisplayBar).

---

### Step 10: Remove Old Manual UI (FINAL STEP)
**Files to delete**:
- `ui/components/RingUpButton.kt`

**Files to modify**:
- `ui/GameEvent.kt` - Remove `RingUp`, `RingUpItem`, `StartTransaction`
- `ui/viewmodels/GameViewModel.kt` - Remove handlers for those events
- `ui/dialogs/TransactionDetailDialog.kt` - Remove "Ring Up Item" button
- `ui/screens/home/StoreHomeScreen.kt` - Remove RingUpButton and StartTransaction button

---

## Code References

### Key Files Structure
```
√ Completed (Phase 1):
  domain/
    ├── GameEngine.kt
    ├── GameStateData.kt
    └── time/
        ├── TimeManager.kt
        ├── GameTime.kt
        └── StoreConfig.kt

⚙️ To Create (Phase 2):
  domain/
    ├── traffic/
    │   ├── TrafficPattern.kt      ← START HERE
    │   └── TrafficManager.kt
    └── player/
        └── PlayerRole.kt
        
  ui/
    └── components/
        ├── PlayerRoleIndicator.kt
        └── PlayerRoleButtons.kt
```

### Important Methods to Understand

**TrafficManager.update()**
```kotlin
fun update(state: GameState, deltaSeconds: Long): List<TransactionRequest>
```
Called every tick. Returns list of customers ready to start transactions.

**GameEngine.performPlayerCashierWork()**
```kotlin
private fun performPlayerCashierWork(deltaSeconds: Double): GameState
```
Automatically rings up items when player role is CASHIER.

**GameEngine.performPlayerStockerWork()**
```kotlin
private fun performPlayerStockerWork(deltaSeconds: Double): GameState
```
Automatically stocks items when player role is STOCKER.

---

## Common Issues & Solutions

**Problem**: "No customers showing up"
- Check `storeState == OPEN`
- Verify `trafficManager.update()` is being called in `tick()`
- Check time - 6-9 AM and 5-9 PM are quiet hours, try 12 PM (lunch)

**Problem**: "Items not ringing up when I toggle Cashier"
- Check `playerRole` is set correctly
- Verify `performPlayerCashierWork()` is called
- Check `currentTransaction.lines` has items

**Problem**: "Can be Cashier and Stocker at same time"
- PlayerRole should be mutually exclusive - fix SetPlayerRole handler
- Use `when` statement, not two separate `if` statements

**Problem**: "Game crashes when I toggle role"
- Likely NullPointerException in `performPlayerCashierWork()`
- Check that `currentTransaction` exists before accessing `.lines`

---

## Testing Your Work

### Test 1: Traffic Generation
```
1. Start game
2. Open store (PAUSED → OPEN)
3. Wait 10 seconds
4. Check: Transactions appear in queue
5. Expected: See different rates at different hours
```

### Test 2: Player Cashier Work
```
1. Make sure transactions exist
2. Click "Work as Cashier"
3. Check: Items process automatically
4. Expected: Transaction completes without manual clicks
```

### Test 3: Player Stocker Work
```
1. Make sure backroom has items
2. Click "Work as Stocker"
3. Check: Items move from backroom to shelf
4. Expected: Backroom count decreases, shelf count increases
```

### Test 4: Mutual Exclusivity
```
1. Click "Work as Cashier"
2. Try to click "Work as Stocker"
3. Expected: Can't do both - only one role active
```

---

## Success Criteria

When Phase 2 is complete, this should work:

✅ Open store → Time flows  
✅ Wait → Customers arrive automatically (rate varies by hour)  
✅ Click "Work as Cashier" → Items process automatically  
✅ Click "Work as Stocker" → Backroom items stock automatically  
✅ Only one role active at a time  
✅ Peak hours (12 PM, 5-6 PM) feel busier than quiet hours  
✅ Game is playable with zero hired staff (player working)  
✅ Old manual buttons removed  

---

## Getting Help

**Documentation**:
- Full guide: `PHASE_2_IMPLEMENTATION_GUIDE.md`
- Architecture: `DAY_CYCLE_DESIGN.md`
- Original design: `DAY_CYCLE_IMPLEMENTATION_GUIDE.md`

**Ask yourself**:
1. Is TrafficManager.update() being called?
2. Is playerRole actually changing when I toggle buttons?
3. Are transactions being generated?
4. Is the player work method being called?

---

## Time Estimates

| Task | Time | Difficulty |
|------|------|-----------|
| TrafficPattern & TrafficManager | 2-3h | Medium |
| PlayerRole enum | 15min | Easy |
| GameEngine integration | 2h | Medium |
| Player work methods | 1.5h | Medium |
| UI components | 2h | Easy |
| Testing & balancing | 2-3h | Medium |
| Remove old UI | 1h | Easy |
| **TOTAL** | **10-13h** | - |

---

## That's It!

You now have everything needed to implement Phase 2. Start with Step 1 (TrafficPattern), test it works, then move to Step 2. Don't skip testing - each component should work before moving to the next.

Good luck! 🚀

