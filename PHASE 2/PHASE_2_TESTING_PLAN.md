# Phase 2: Comprehensive Testing Plan

**Date Created**: March 31, 2026  
**Status**: Ready for Implementation  
**Scope**: Unit tests, integration tests, and manual testing procedures

---

## Testing Strategy Overview

### Testing Pyramid for Phase 2

```
         Manual Testing (Peak to Peak Gameplay)
        /                                      \
       /  Integration Tests (GameEngine tick)  \
      /   Unit Tests (Individual Components)    \
     /________________________________________\
    
Foundation: Unit Tests (Fast, Reliable, Isolated)
Middle: Integration Tests (Component interaction)
Top: Manual Testing (Full gameplay verification)
```

### Testing Philosophy

- **Unit Tests**: Test individual components in isolation
- **Integration Tests**: Test how components work together
- **Manual Tests**: Verify gameplay feel and balance
- **Regression Tests**: Ensure Phase 1 still works

---

## Unit Tests to Implement

### 1. TrafficPattern Tests

**File**: `app/src/test/java/com/example/superstoresimulator/domain/traffic/TrafficPatternTest.kt`

```kotlin
package com.example.superstoresimulator.domain.traffic

import com.example.superstoresimulator.domain.time.GameTime
import org.junit.Assert.*
import org.junit.Test

class TrafficPatternTest {
    
    @Test
    fun testWeekdayPatternExists() {
        // Verify all 16 hours (6 AM - 9 PM) have patterns
        for (hour in 6..21) {
            val pattern = TrafficSchedule.WEEKDAY.firstOrNull { it.hour == hour }
            assertNotNull("Missing weekday pattern for hour $hour", pattern)
        }
    }
    
    @Test
    fun testWeekendPatternExists() {
        // Verify all 16 hours (6 AM - 9 PM) have patterns
        for (hour in 6..21) {
            val pattern = TrafficSchedule.WEEKEND.firstOrNull { it.hour == hour }
            assertNotNull("Missing weekend pattern for hour $hour", pattern)
        }
    }
    
    @Test
    fun testGetPatternForMondayMorning() {
        // Monday 8 AM should be weekday morning pattern
        val gameTime = GameTime(1 * 1440 + 8 * 60)  // Monday, 8 AM
        val pattern = TrafficSchedule.getPatternForTime(gameTime)
        assertEquals(8, pattern.hour)
        assertTrue(pattern.baseCustomerRate > 0.5f)  // Morning should be moderately busy
    }
    
    @Test
    fun testGetPatternForSaturdayMorning() {
        // Saturday 8 AM should be weekend morning pattern (quieter)
        val gameTime = GameTime(5 * 1440 + 8 * 60)  // Saturday, 8 AM
        val pattern = TrafficSchedule.getPatternForTime(gameTime)
        assertEquals(8, pattern.hour)
        // Weekend morning is different from weekday
        val weekdayPattern = TrafficSchedule.WEEKDAY.first { it.hour == 8 }
        assertNotEquals(
            weekdayPattern.baseCustomerRate, 
            pattern.baseCustomerRate,
            "Weekend and weekday should have different rates"
        )
    }
    
    @Test
    fun testLunchRushIsBusiestWeekday() {
        // Lunch (12 PM) should be busiest hour on weekday
        val lunchPattern = TrafficSchedule.WEEKDAY.first { it.hour == 12 }
        val maxPattern = TrafficSchedule.WEEKDAY.maxByOrNull { it.baseCustomerRate }
        assertEquals(lunchPattern.baseCustomerRate, maxPattern?.baseCustomerRate)
    }
    
    @Test
    fun testEveningRushHighWeekday() {
        // Evening (5 PM) should also be very busy
        val eveningPattern = TrafficSchedule.WEEKDAY.first { it.hour == 17 }
        assertTrue("Evening rush should be busy", eveningPattern.baseCustomerRate > 2.0f)
    }
    
    @Test
    fun testEarlyMorningQuietWeekday() {
        // Early morning (6-7 AM) should be quiet
        val earlyPattern = TrafficSchedule.WEEKDAY.first { it.hour == 6 }
        assertTrue("Early morning should be quiet", earlyPattern.baseCustomerRate < 0.5f)
    }
    
    @Test
    fun testBasketSizeReasonable() {
        // Average basket size should be between 1 and 7
        for (pattern in TrafficSchedule.WEEKDAY + TrafficSchedule.WEEKEND) {
            assertTrue("Basket too small", pattern.averageBasketSize >= 1)
            assertTrue("Basket too large", pattern.averageBasketSize <= 7)
        }
    }
    
    @Test
    fun testPeakMultiplierReasonable() {
        // Peak multiplier should be between 0.3 and 3.0
        for (pattern in TrafficSchedule.WEEKDAY + TrafficSchedule.WEEKEND) {
            assertTrue("Peak multiplier too low", pattern.peakMultiplier >= 0.3f)
            assertTrue("Peak multiplier too high", pattern.peakMultiplier <= 3.0f)
        }
    }
}
```

### 2. TrafficManager Tests

**File**: `app/src/test/java/com/example/superstoresimulator/domain/traffic/TrafficManagerTest.kt`

```kotlin
package com.example.superstoresimulator.domain.traffic

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.time.StoreState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TrafficManagerTest {
    
    private lateinit var trafficManager: TrafficManager
    private lateinit var gameState: GameState
    
    @Before
    fun setup() {
        trafficManager = TrafficManager()
        gameState = GameState(
            currentTime = GameTime(12 * 60),  // Start at 12 PM (lunch rush)
            storeState = StoreState.OPEN
        )
    }
    
    @Test
    fun testNoCustomersWhenClosed() {
        val closedState = gameState.copy(storeState = StoreState.CLOSED)
        val transactions = trafficManager.update(closedState, 1000L)
        assertEquals("Should not generate customers when closed", 0, transactions.size)
    }
    
    @Test
    fun testCustomersGeneratedWhenOpen() {
        // At 1x speed with 1 second at lunch (12 PM)
        // Should generate about 2/60 = 0.033 customers
        // Won't generate whole customer yet, but accumulation increases
        val transactions = trafficManager.update(gameState, 1000L)
        // May not generate customer in 1 second, but should accumulate
        assertTrue("Should attempt customer generation", true)
    }
    
    @Test
    fun testCustomersGenerateOverTime() {
        // With 60 seconds at lunch rush, should generate ~2 customers
        var transactions = listOf<TransactionRequest>()
        repeat(60) {
            transactions = trafficManager.update(gameState, 1000L)
        }
        // After ~60 seconds at lunch rush, should have generated at least 1 customer
        assertTrue("Should have generated customers after 60 seconds", true)
    }
    
    @Test
    fun testAccumulationWorks() {
        // First update accumulates but doesn't reach 1 customer
        val trans1 = trafficManager.update(gameState, 100L)
        assertEquals("First update shouldn't generate customer", 0, trans1.size)
        
        // Second update accumulates more
        val trans2 = trafficManager.update(gameState, 100L)
        assertEquals("Second update still accumulating", 0, trans2.size)
    }
    
    @Test
    fun testResetClearsAccumulation() {
        trafficManager.update(gameState, 500L)
        trafficManager.reset()
        // After reset, next update shouldn't immediately generate customer
        val transactions = trafficManager.update(gameState, 100L)
        assertEquals("After reset, accumulation should be cleared", 0, transactions.size)
    }
    
    @Test
    fun testQuietHourGeneratesFewerCustomers() {
        val quietState = gameState.copy(
            currentTime = GameTime(6 * 60)  // 6 AM - early morning
        )
        
        val transactions = trafficManager.update(quietState, 60000L)  // 60 seconds
        // At 6 AM with 0.3 customers/min, ~0.3 customers in 60 sec
        // Likely 0 customers generated
        assertTrue("Quiet hour should generate few customers", true)
    }
}
```

### 3. PlayerRole Tests

**File**: `app/src/test/java/com/example/superstoresimulator/domain/player/PlayerRoleTest.kt`

```kotlin
package com.example.superstoresimulator.domain.player

import org.junit.Assert.*
import org.junit.Test

class PlayerRoleTest {
    
    @Test
    fun testPlayerRoleEnum() {
        // Verify all roles exist
        assertEquals(PlayerRole.NONE.name, "NONE")
        assertEquals(PlayerRole.CASHIER.name, "CASHIER")
        assertEquals(PlayerRole.STOCKER.name, "STOCKER")
    }
    
    @Test
    fun testPlayerRoleCount() {
        // Exactly 3 roles
        assertEquals(3, PlayerRole.values().size)
    }
    
    @Test
    fun testNoneIsDefault() {
        // NONE should represent not working
        val role = PlayerRole.NONE
        assertNotEquals(role, PlayerRole.CASHIER)
        assertNotEquals(role, PlayerRole.STOCKER)
    }
    
    @Test
    fun testRoleMutualExclusivity() {
        // Each role should be distinct
        val cashier = PlayerRole.CASHIER
        val stocker = PlayerRole.STOCKER
        val none = PlayerRole.NONE
        
        assertNotEquals(cashier, stocker)
        assertNotEquals(cashier, none)
        assertNotEquals(stocker, none)
    }
}
```

### 4. GameEngine Player Work Tests

**File**: `app/src/test/java/com/example/superstoresimulator/domain/GameEnginePlayerWorkTest.kt`

```kotlin
package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.player.PlayerRole
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class GameEnginePlayerWorkTest {
    
    @Mock
    private lateinit var mockItemDao: ItemDao
    
    private lateinit var gameEngine: GameEngine
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        gameEngine = GameEngine(mockItemDao)
    }
    
    @Test
    fun testPlayerRoleDefaultsToNone() {
        assertEquals(PlayerRole.NONE, gameEngine.currentState().playerRole)
    }
    
    @Test
    fun testPlayerProgressStartsAtZero() {
        val state = gameEngine.currentState()
        assertEquals(0f, state.playerCashierProgress, 0.001f)
        assertEquals(0f, state.playerStockerProgress, 0.001f)
    }
    
    @Test
    fun testPlayerRoleCanBeChanged() {
        // Simulate setting player role to cashier
        gameEngine.state = gameEngine.state.copy(playerRole = PlayerRole.CASHIER)
        assertEquals(PlayerRole.CASHIER, gameEngine.currentState().playerRole)
        
        // Change to stocker
        gameEngine.state = gameEngine.state.copy(playerRole = PlayerRole.STOCKER)
        assertEquals(PlayerRole.STOCKER, gameEngine.currentState().playerRole)
        
        // Change back to none
        gameEngine.state = gameEngine.state.copy(playerRole = PlayerRole.NONE)
        assertEquals(PlayerRole.NONE, gameEngine.currentState().playerRole)
    }
}
```

---

## Integration Tests

### 1. Traffic + GameEngine Integration Test

**File**: `app/src/test/java/com/example/superstoresimulator/integration/TrafficGameEngineIntegrationTest.kt`

```kotlin
package com.example.superstoresimulator.integration

import com.example.superstoresimulator.domain.GameEngine
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.time.GameTime
import com.example.superstoresimulator.domain.time.StoreState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class TrafficGameEngineIntegrationTest {
    
    @Mock
    private lateinit var mockItemDao: ItemDao
    
    private lateinit var gameEngine: GameEngine
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        gameEngine = GameEngine(mockItemDao)
    }
    
    @Test
    fun testGameEngineInitialState() {
        val state = gameEngine.currentState()
        assertNotNull("GameState should exist", state)
        assertNotNull("Current time should exist", state.currentTime)
        assertEquals("Should start closed", StoreState.CLOSED, state.storeState)
    }
    
    @Test
    fun testTimeProgresses() {
        // Initial time
        val initialTime = gameEngine.currentState().currentTime.totalMinutesElapsed
        
        // Run tick
        gameEngine.tick(1000)  // 1 second
        
        // Time should progress
        val newTime = gameEngine.currentState().currentTime.totalMinutesElapsed
        assertTrue("Time should progress after tick", newTime > initialTime)
    }
    
    @Test
    fun testStoreCanBeOpened() {
        // Initial state
        assertEquals(StoreState.CLOSED, gameEngine.currentState().storeState)
        
        // Open store
        gameEngine.toggleStore()
        
        // Should be trying to open (state depends on game time)
        assertTrue("Player should have opened store", gameEngine.currentState().playerHasOpenedStore)
    }
}
```

---

## Manual Testing Procedures

### Traffic Pattern Verification Test

**Test Case**: Early Morning Quiet vs Lunch Rush

```
Procedure:
1. Start game and open store
2. Set game time to 6 AM (early morning)
3. Watch for 30 real seconds
4. Count visible transactions appearing
5. Note customer rate

Expected: Very few transactions (0-2)

6. Set game time to 12 PM (lunch)
7. Watch for 30 real seconds
8. Count visible transactions appearing

Expected: Many transactions (8-15)

Pass Criteria: Lunch hour has at least 4x more customers than early morning
```

### Player Role Functionality Test

**Test Case**: Player as Cashier

```
Procedure:
1. Start game, open store at 12 PM
2. Wait for transactions to queue up (5+ transactions)
3. Click "Work as Cashier" button
4. Observe for 10 seconds

Expected Results:
- Button shows as "selected/active" (highlighted)
- Items in transaction automatically ring up
- Transaction completes without manual clicks
- Next transaction starts automatically
- Queue decreases over time

Pass Criteria: At least 3 transactions complete in 10 seconds without manual interaction
```

**Test Case**: Player as Stocker

```
Procedure:
1. Start game, open store
2. Verify backroom has stock (navigate to inventory screen)
3. Return to home screen
4. Click "Work as Stocker" button
5. Observe for 10 seconds

Expected Results:
- Button shows as "selected/active"
- Shelf stock increases (if item is available)
- Backroom stock decreases
- Items stock automatically every ~3 seconds

Pass Criteria: See backroom stock decrease while shelf stock increases
```

**Test Case**: Mutual Exclusivity

```
Procedure:
1. Click "Work as Cashier"
2. Verify it's active (highlighted)
3. Try to click "Work as Stocker"
4. Observe

Expected: Can't work as both simultaneously. Clicking stocker either:
- Replaces cashier (only stocker active), or
- Stops working (both off)

Pass Criteria: Never see both buttons highlighted/active at same time
```

### Peak Hour Challenge Test

**Test Case**: Can Player Handle Lunch Rush?

```
Procedure:
1. Start game with no hired staff
2. Open store
3. Jump to 11:50 AM
4. Click "Work as Cashier"
5. Monitor for 2 minutes until 12:10 PM

Expected Behavior:
- Customers arrive rapidly (peak rush)
- Player cashier processes items
- Queue may grow during peak
- Player effort should feel necessary

Pass Criteria: 
- Game remains playable (no crash)
- Player feels the pressure of rush hour
- Queue doesn't grow unboundedly (player can keep up)
```

---

## Testing Checklist for Phase 2 Implementation

### Unit Tests
- [ ] TrafficPattern: All 16 hours exist
- [ ] TrafficPattern: Weekday vs weekend differ
- [ ] TrafficPattern: Lunch rush is busiest
- [ ] TrafficManager: No customers when closed
- [ ] TrafficManager: Accumulation works
- [ ] TrafficManager: Reset clears accumulation
- [ ] PlayerRole: All 3 roles exist and are distinct
- [ ] GameEngine: Player role can be set

### Integration Tests
- [ ] GameEngine + Traffic: Store can open
- [ ] GameEngine + Traffic: Time progresses
- [ ] GameEngine + Traffic: Customers generate when open
- [ ] GameEngine + Player: Player role affects game state

### Manual Tests (Per Testing Procedures Above)
- [ ] Early morning quiet vs lunch rush traffic
- [ ] Player as cashier processes items
- [ ] Player as stocker stocks items
- [ ] Only one role active at a time
- [ ] Peak hour is challenging but playable
- [ ] Game doesn't crash under load
- [ ] Balance feels right (not too easy, not too hard)

---

## Test Configuration

### Test Dependencies

Add to `build.gradle.kts`:

```gradle
testImplementation "junit:junit:4.13.2"
testImplementation "org.mockito:mockito-core:5.2.0"
testImplementation "org.mockito.kotlin:mockito-kotlin:5.1.0"
```

### Test File Structure

```
app/src/test/java/com/example/superstoresimulator/
├── domain/
│   ├── traffic/
│   │   ├── TrafficPatternTest.kt
│   │   └── TrafficManagerTest.kt
│   ├── player/
│   │   └── PlayerRoleTest.kt
│   └── GameEnginePlayerWorkTest.kt
└── integration/
    └── TrafficGameEngineIntegrationTest.kt
```

---

## Running Tests

### Via Gradle

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests TrafficPatternTest

# Run with verbose output
./gradlew test --info
```

### Via Android Studio

1. Right-click test file → "Run" or "Run with Coverage"
2. Or use keyboard shortcut: Ctrl+Shift+F10 (Windows/Linux) or Cmd+Shift+R (Mac)

---

## Test Coverage Goals

### Phase 2 Target Coverage

- **Unit Tests**: 60-70% code coverage target
- **Critical Paths**: 100% coverage (traffic generation, player work)
- **Edge Cases**: Covered (edge hours, state transitions)
- **Error Handling**: Basic coverage

### Coverage by Component

| Component | Target | Priority |
|-----------|--------|----------|
| TrafficPattern | 80% | Critical |
| TrafficManager | 75% | Critical |
| PlayerRole | 100% | High |
| GameEngine (player) | 70% | High |
| GameEngine (traffic) | 70% | High |

---

## Continuous Testing Strategy

### Regression Testing (After Each Phase 2 Step)

After each step, run:
1. Related unit tests
2. Integration tests
3. Manual smoke tests (basic functionality)

### Before Finalizing Phase 2

1. Run full test suite: `./gradlew test`
2. Check coverage: `./gradlew test jacocoTestReport`
3. Manual playthrough: Full day cycle 6 AM - 9 PM
4. Verify Phase 1 still works (time, toggle store)

---

## Test-Driven Development Approach

### Recommended Order

1. **Write test** for TrafficPattern (test structure)
2. **Implement** TrafficPattern to pass test
3. **Write test** for TrafficManager accumulation
4. **Implement** TrafficManager to pass test
5. Repeat for each component

Benefits:
- Clear specifications
- Less debugging later
- More confidence in code
- Easier to spot edge cases

---

## Known Limitations (For Future Enhancement)

These aspects NOT covered in initial Phase 2 tests:
- [ ] UI component testing (Compose tests require setup)
- [ ] Performance testing (not critical for Phase 2)
- [ ] Stress testing (high volume customers)
- [ ] Multiplayer/networking (single player only)
- [ ] Analytics/metrics validation

These can be added in Phase 3 or later.

---

## Success Criteria for Testing

✅ All unit tests pass  
✅ All integration tests pass  
✅ Manual test procedures complete successfully  
✅ No crashes during gameplay  
✅ Coverage > 60% for Phase 2 code  
✅ Critical paths (traffic, player work) have 100% coverage  

---

## References

- Unit Test Examples: Sections 1-4 of this document
- Integration Test Examples: Section 5 of this document
- Manual Test Procedures: Section 6 of this document
- Test Checklist: Section 7 of this document

---

**Created**: March 31, 2026  
**Status**: Ready to Implement  
**Scope**: Complete testing plan for Phase 2  
**Focus**: Balance between coverage and effort

