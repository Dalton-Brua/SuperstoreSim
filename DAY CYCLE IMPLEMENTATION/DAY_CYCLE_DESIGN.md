# Day Cycle System - Design Deep Dive

## Current State Analysis

### Existing Architecture
- **Transaction-based**: Game currently runs on transactions, not time
- **Continuous**: No concept of "open" vs "closed"
- **Stateless Time**: No real time progression, just events
- **Staff**: Cashiers/Stockers work continuously
- **Inventory**: Infinite supply (no ordering delays)

---

## Feature: Day Cycle with Opening/Closing Times & Traffic Patterns

### 1. CORE TIME SYSTEM IMPLICATIONS

#### 1.1 Time Representation
**Current Problem**: Need to track:
- Current time of day (0:00 - 23:59)
- Which day/week/month (for save states)
- Time progression rate (seconds → game minutes)
- Pause state (player interaction pause vs time passage)

**Proposed Solution**:
```kotlin
data class GameTime(
    val hour: Int,           // 0-23
    val minute: Int,         // 0-59
    val dayOfWeek: Int,      // 0-6 (Monday-Sunday)
    val weekNumber: Int,     // Week of year
    val dayNumber: Int,      // Total days elapsed
    val gameSpeedMultiplier: Float = 1.0f  // Player can speed up/slow down
) {
    fun getTotalMinutesOfDay(): Int = hour * 60 + minute
    fun isOpen(openTime: Int, closeTime: Int): Boolean {
        val currentMin = getTotalMinutesOfDay()
        return currentMin >= openTime && currentMin < closeTime
    }
}
```

**Impact**: 
- Requires new state field in `GameState`
- Needs UI timer display
- Affects save/load persistence

#### 1.2 Time Progression Mechanics
**Decision Points**:
- How fast does time pass? (1 real second = X game minutes?)
- Can player control speed?
- Does time pause during transactions?
- What happens when store closes?

**Implications**:
- Affects game difficulty (more/less time to manage)
- Affects staff efficiency metrics
- Affects player strategy (when to order inventory, hire staff)

---

### 2. OPENING/CLOSING MECHANICS

#### 2.1 Store States
```kotlin
enum class StoreState {
    CLOSED,              // Before opening time
    OPEN,                // During operating hours
    CLOSING,             // Grace period before close
    CLOSING_PROCEDURES   // Staff cleanup/restocking after close
}

data class StoreConfig(
    val openTime: Int = 360,         // 6:00 AM (in minutes from midnight)
    val closeTime: Int = 1260,       // 9:00 PM (21:00)
    val closingProcedureDuration: Int = 30,  // 30 minutes after close
    val allowTransactionsDuringClose: Boolean = false
)
```

#### 2.2 Implications by Store State

**CLOSED State**:
- ❌ Customers cannot enter
- ✅ Stockers CAN restock shelves (overnight stocking)
- ❌ Cashiers cannot ring up sales
- ⚠️ Orders can still arrive (scheduled for next morning)
- Question: Should orders arrive at 6 AM or continuously?

**OPEN State**:
- ✅ Normal operations
- Customer traffic follows pattern
- All staff work normally

**CLOSING State** (last 15-30 min):
- ⚠️ No new customers enter (or fewer)
- ✅ Existing customers can finish checking out
- Staff start cleanup procedures
- Should player still be able to order?

**CLOSING_PROCEDURES State** (after close):
- ❌ No customers at all
- ✅ Stockers do overnight restocking
- ✅ Managers can do administrative tasks
- Money counting, inventory checks, etc.

---

### 3. CUSTOMER TRAFFIC PATTERNS

#### 3.1 Traffic Model
```kotlin
data class TrafficPattern(
    val hour: Int,
    val baseCustomerRate: Float,  // Customers per minute
    val averageBasketSize: Int,   // Items per transaction
    val peakHourMultiplier: Float = 1.0f
)

// Example pattern
val WEEKDAY_TRAFFIC = listOf(
    TrafficPattern(6, 0.1f, 3),    // Early morning: few customers
    TrafficPattern(7, 0.3f, 4),    // Morning rush starts
    TrafficPattern(8, 0.8f, 5),    // Peak morning
    TrafficPattern(9, 0.6f, 4),    // Tail of morning rush
    TrafficPattern(12, 0.7f, 6),   // Lunch prep rush
    TrafficPattern(17, 1.2f, 8),   // Evening rush (5 PM)
    TrafficPattern(19, 0.5f, 4),   // Evening wind down
    TrafficPattern(21, 0.1f, 2),   // Late night minimal traffic
)

val WEEKEND_TRAFFIC = listOf(
    TrafficPattern(8, 0.2f, 3),    // Weekend opens later/slower
    TrafficPattern(10, 0.9f, 7),   // Weekend shoppers
    TrafficPattern(12, 1.0f, 8),   // All day shopping
    TrafficPattern(20, 0.3f, 3),
)
```

#### 3.2 Traffic Generation Implications

**Current Issue**: 
- Player controls when transactions happen
- No organic customer flow

**New Approach - Two Options**:

**Option A: Autonomous Traffic (Recommended)**
```kotlin
// Track fractional customers waiting
var customerQueue: Float = 0f

fun tickTraffic(deltaSeconds: Long) {
    if (!isOpen) return
    
    val pattern = getTrafficPattern(currentTime.hour)
    val customersPerSecond = pattern.baseCustomerRate / 60f
    
    customerQueue += customersPerSecond * deltaSeconds
    
    // Start new transactions for each whole customer
    while (customerQueue >= 1.0f) {
        startNewTransaction()  // Auto-generates customer
        customerQueue -= 1.0f
    }
}
```

**Implications**:
- Transactions happen automatically
- Player must manage staff to keep up
- Staff efficiency becomes critical
- Queues can form if not enough cashiers

**Option B: Player-Triggered with Probability**
- Player clicks "start transaction"
- Probability of success based on time/traffic pattern
- Simpler but less realistic

**Recommendation**: Option A - more interesting gameplay

---

### 4. STAFF SCHEDULING IMPLICATIONS

#### 4.1 Current Problem
Staff works 24/7. With day cycles, need:
- Work hours/shifts
- Break times
- On/off duty states
- Scheduling system

#### 4.2 Proposed Staff System

```kotlin
data class Shift(
    val startTime: Int,        // Minutes from midnight
    val endTime: Int,
    val staffId: Int,
    val breakDuration: Int = 30
)

data class StaffSchedule(
    val shifts: List<Shift>,
    val daysOff: Set<Int>  // Which days of week are off (0=Monday)
)

enum class StaffStatus {
    OFF_DUTY,         // Not working
    ON_BREAK,         // During shift but on break
    WORKING,          // Actively working
    CLOCKING_OUT      // End of shift
}
```

#### 4.3 Gameplay Implications

**Before Day Cycle**:
- Hire staff, they work forever
- Simple cost model: hire once, fixed salary

**After Day Cycle**:
- Must create daily/weekly schedules
- Staff expectations: "I work 9-5" or "I'm part-time"
- Daily payroll calculations
- Overtime (after closing time restocking)
- Staff fatigue (longer shifts = less efficient)
- Scheduling conflicts (not enough staff during peak hours)

**New Mechanics**:
```kotlin
// Player must ensure proper coverage
data class ScheduleRequirement {
    val hour: Int
    val minimumCashiers: Int
    val minimumStockers: Int
    val recommendedCashiers: Int
}

// Staff can request schedules
data class StaffRequest(
    val employeeId: Int,
    val preferredHours: String  // "9-5", "Part-time", etc.
    val daysOff: List<Int>
)
```

---

### 5. INVENTORY & ORDERING IMPLICATIONS

#### 5.1 Current State
- Order item → immediately arrives in backroom
- No supply chain delay
- Can order 24/7

#### 5.2 With Day Cycles

**Delivery Windows**:
```kotlin
data class InventoryOrder(
    val orderId: Int,
    val itemId: Int,
    val quantity: Int,
    val orderedAt: GameTime,
    val deliveryTime: GameTime,        // When truck arrives
    val status: OrderStatus = PENDING
)

enum class OrderStatus {
    PENDING,         // Order placed, waiting
    IN_TRANSIT,      // Delivery truck on way
    DELIVERED,       // At store
    STOCKED          // Put on shelves
}
```

**Delivery Rules**:
- Orders placed 6 PM → arrive 9 AM next day
- Orders placed early morning → arrive same day
- Weekend orders arrive Monday
- Perishables rot if not sold within X days

**Implications**:
- Player must plan ahead
- Running out of stock becomes real risk
- Strategy: order during off-peak hours
- Storage space limits (can't order too much)

---

### 6. EVENTS & RANDOMNESS

#### 6.1 Events That Only Happen During Day Cycle

**Customer-Related**:
- Lunch rush (12-1 PM)
- Evening rush (5-7 PM)
- Weekend shoppers
- Bad weather (fewer customers)
- Holiday periods (more customers)

**Staff-Related**:
- Staff calls in sick
- Staff requests day off
- Staff wants to extend shift
- Staff performance variations by time of day

**Operational**:
- Equipment breaks down during peak hours
- Delivery trucks late/early
- Unexpected inspections during open hours
- Price fluctuations by time of day

#### 6.2 Example: Lunch Rush

```kotlin
class LunchRush : DayEvent {
    override fun trigger(gameState: GameState): GameState {
        if (gameState.time.hour != 12) return gameState
        
        // Double customer traffic for 1 hour
        return gameState.copy(
            trafficMultiplier = 2.0f
        )
    }
    
    override fun duration(): Int = 60  // 1 real-time minute = 1 game hour
}
```

---

### 7. UI/UX IMPLICATIONS

#### 7.1 Time Display
```
Current Time: Mon 2:45 PM | Store: OPEN
Day Profit: $1,245 | Total: $52,100
Speed: [1x] [2x] [4x] [PAUSE]
```

#### 7.2 New Screens

**Daily Summary Screen**:
- Hourly transaction graph
- Peak hours analysis
- Staff utilization
- Inventory status
- Tomorrow's forecast

**Schedule Management Screen**:
- Visual calendar showing shifts
- Staff availability
- Gaps in coverage (red flags)
- Wage calculator

**Time Controls**:
- Play/pause
- Speed multiplier (1x, 2x, 4x, 8x)
- "Skip to closing" button
- "Open store" button

---

### 8. SAVE/LOAD & PERSISTENCE

#### 8.1 New Save Data Required
```kotlin
data class DaySave(
    val gameTime: GameTime,
    val storeState: StoreState,
    val inventory: Map<Int, InventoryState>,
    val pendingOrders: List<InventoryOrder>,
    val staffSchedules: Map<Int, StaffSchedule>,
    val dailyMetrics: DailyMetrics,
    val gameState: GameState
)

data class DailyMetrics(
    val date: String,
    val openingInventory: Int,
    val closingInventory: Int,
    val totalSales: Money,
    val totalExpenses: Money,
    val profit: Money,
    val transactionCount: Int,
    val customerCount: Int
)
```

#### 8.2 Archive Historical Data
```kotlin
data class HistoricalDay(
    val dateString: String,
    val metrics: DailyMetrics,
    val topItems: List<ItemSales>,
    val peakHour: Int
)

// Player can review past performance
val last30Days: List<HistoricalDay>
```

---

### 9. DIFFICULTY & BALANCING IMPLICATIONS

#### 9.1 Before: Pure Execution
- How fast can you click?
- Difficulty = transaction complexity

#### 9.2 After: Strategic Management
- Do you have enough staff?
- Did you order enough inventory?
- Are your prices competitive?
- Can you handle peak hours?
- Staff scheduling strategy
- Inventory forecasting

**Balancing Knobs**:
- Time speed multiplier
- Customer traffic intensity
- Staff salary costs
- Inventory costs
- Order delivery times

---

### 10. GAME PROGRESSION IMPLICATIONS

#### 10.1 New Progression Paths

**Short-term** (Single day):
- Handle 8-hour shift
- Learn scheduling
- Hit $X daily profit goal

**Medium-term** (Week):
- Optimize weekly schedule
- Build efficient workflows
- Track weekly trends

**Long-term** (Month/Quarter/Year):
- Seasonal patterns
- Growth trajectory
- Franchising?

#### 10.2 Unlock System Based on Time

```kotlin
val Unlocks = listOf(
    UnlockAfter(day = 7) { "Learn staff scheduling" },
    UnlockAfter(day = 30) { "Implement delivery scheduling" },
    UnlockAfter(revenue = 10000) { "Hire manager" },
    UnlockAfter(day = 90) { "Open second shift" }
)
```

---

### 11. PERFORMANCE IMPLICATIONS

#### 11.1 New Processing Each Tick

**Current Tick** (~60 FPS):
- Update animations
- Process user input
- Update UI

**New Tick** (Every game minute):
```
- Check current hour → adjust traffic pattern
- Generate random customers
- Process pending orders
- Update staff status
- Check for events
- Update UI time display
- Save periodic checkpoints
- Calculate running totals
```

**Impact**: Might need to separate UI ticks from game logic ticks

#### 11.2 Memory Implications
- Store daily metrics (30 days = minimal)
- Store pending orders (might be 50-100)
- Store staff schedules (might be 50 employees)
- Overall: minimal impact

---

### 12. MAJOR DECISION MATRIX

| Decision | Option A | Option B | Impact |
|----------|----------|----------|--------|
| **Time Speed** | Fixed rate | Player controlled | Difficulty |
| **Transactions** | Auto-generated | Player-triggered | Gameplay feel |
| **Delivery** | Next day | Immediate | Strategy |
| **Staff Scheduling** | Manual player schedule | AI scheduling | Complexity |
| **Perishables** | Yes (decay) | No (infinite) | Urgency |
| **Weather Events** | Yes | No | Randomness |
| **Store Expansion** | Multiple shifts | Single manager | Growth path |

---

### 13. RECOMMENDED IMPLEMENTATION PHASES

#### Phase 1: Time Foundation (1-2 weeks)
- [ ] Implement GameTime system
- [ ] Add time display UI
- [ ] Basic time progression (1 game minute = X real seconds)
- [ ] Open/close logic
- [ ] Save/load time state

#### Phase 2: Traffic & Transactions (1-2 weeks)
- [ ] Traffic pattern system
- [ ] Auto-generate customers during open hours
- [ ] Update UI to show queue
- [ ] Adjust difficulty for autonomous traffic

#### Phase 3: Scheduling System (1-2 weeks)
- [ ] Staff shift scheduling
- [ ] Coverage validation
- [ ] Daily payroll
- [ ] Scheduling UI

#### Phase 4: Inventory Integration (1 week)
- [ ] Delivery windows
- [ ] Perishable items
- [ ] Ordering forecasting
- [ ] Stock-out mechanics

#### Phase 5: Events & Polish (1 week)
- [ ] Random events
- [ ] Daily summary screens
- [ ] Audio cues for times
- [ ] Balance pass

#### Phase 6: Advanced Features (Ongoing)
- [ ] Seasonal patterns
- [ ] Staff fatigue/morale
- [ ] Equipment maintenance
- [ ] Multi-shift management

---

### 14. ARCHITECTURE CHANGES REQUIRED

```kotlin
// Current
class GameEngine(val itemDao: ItemDao) {
    var state = GameState(...)
}

// After
class GameEngine(val itemDao: ItemDao) {
    var state = GameState(...)
    val timeManager = TimeManager()
    val trafficManager = TrafficManager()
    val scheduleManager = ScheduleManager()
    val inventoryManager = InventoryManager()
    val eventManager = EventManager()
    
    fun tick(deltaMs: Long) {
        timeManager.update(deltaMs)
        trafficManager.update(state, deltaMs)
        scheduleManager.update(state)
        inventoryManager.processDeliveries(state)
        eventManager.processEvents(state)
        // ... existing logic
    }
}
```

**Benefits**:
- Separation of concerns
- Easier to test
- Easier to modify individual systems
- Aligns with TODO item to refactor GameEngine

---

### 15. PLAYER EXPERIENCE FLOW

```
Day 1:
┌─────────────────────────────────────┐
│ 6:00 AM: Store opens               │
│ - Traffic starts (few customers)   │
│ - Player must be ready             │
│ - Tutorial guides through first day│
├─────────────────────────────────────┤
│ 12:00 PM: Lunch rush               │
│ - Traffic surges                   │
│ - Stressed! Need quick decisions   │
├─────────────────────────────────────┤
│ 5:00 PM: Evening rush              │
│ - Another surge                    │
│ - Is staff enough? Hire more?      │
├─────────────────────────────────────┤
│ 9:00 PM: Store closes              │
│ - Closing procedures               │
│ - Overnight restocking             │
│ - Daily summary                    │
├─────────────────────────────────────┤
│ Day Summary:                        │
│ Revenue: $1,245                    │
│ Profit: $845                       │
│ Peak: 5 PM (24 customers)          │
│ Issues: Ran out of milk! (-$200)   │
└─────────────────────────────────────┘

Tomorrow → Next day cycle starts...
```

---

### 16. CRITICAL SUCCESS FACTORS

✅ **Must Have**:
- Time display and progression
- Open/close mechanics
- Basic traffic patterns
- Staff can't work 24/7

⚠️ **Should Have**:
- Delivery delays
- Event system
- Daily summary

❓ **Nice to Have**:
- Seasonal patterns
- Staff morale
- Equipment maintenance
- Franchising

---

## CONCLUSION

This feature transforms the game from a **clicker simulator** into a **real management sim**. It requires:

1. **New Systems**: Time, Traffic, Scheduling, Inventory Management
2. **Architecture Changes**: Refactor GameEngine into specialized managers
3. **UI Updates**: Time display, schedules, daily summaries
4. **Balance Adjustments**: Difficulty curves, staff economics
5. **Save System Updates**: Track time and daily metrics

The effort is significant but enables much deeper gameplay and aligns with your stated goal of transitioning to a "management sim with more complex interactions and decisions."

**Estimated Total Effort**: 3-4 weeks for full implementation including polish and balance.

