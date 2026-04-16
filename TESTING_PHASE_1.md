# Phase 1 Implementation - Testing Guide

**Date**: April 15, 2026  
**Tester**: Manual playtesting guide

---

## Pre-Testing Setup

### Build the Project
```bash
./gradlew clean build
```

### Expected Behavior

The game should start with:
- Store size: **SMALL** (visible in state, not yet in UI)
- Daily rent: **$100** (deducted each midnight)
- Daily objectives: **2–3 random objectives** (not yet visible in UI)
- Staff wages: **$50–$100 each/day** (deducted each midnight)

---

## Manual Testing Scenarios

### Scenario 1: Store Initialization & Rent Deduction

**Steps:**
1. Start game (new save)
2. Check GameState.currentStoreSize (should be SMALL)
3. Start time running (click play)
4. Skip to next day using SkipDay button
5. Check end-of-day report

**Expected Results:**
- ✅ Time advances to midnight
- ✅ End-of-day report shows (auto-paused)
- ✅ Cash balance decreased by $100 (rent)
- ✅ No staff hired yet (wages = $0)
- ✅ Daily metrics show `rentPaid = $100`, `wagesPaid = $0`

**Debug Info:**
```
// In Logcat, should see:
Daily rollover: rent=$100, wages=$0, bonus=$0, newCash=[amount-100]
```

---

### Scenario 2: Staff Wages Deduction

**Steps:**
1. Hire 2 CASHIERS (cost: $1,500 each = $3,000 total)
2. Skip to next day
3. Check end-of-day report

**Expected Results:**
- ✅ Cash decreased by: $100 (rent) + $100 (2×$50 wages) = $200
- ✅ Daily metrics show `rentPaid = $100`, `wagesPaid = $100`
- ✅ Net revenue should be: (revenue - refunds - rent - wages)

**Debug Info:**
```
// After hiring:
GameState.money -= $3,000
GameState.hiredEntityRegistry.hiredEntities.size() == 2

// After next day:
wagesPaid should be $5,000 (in cents) per cashier × 2 = $100 (in dollars)
```

---

### Scenario 3: Objective Completion & Bonuses

**Steps:**
1. Start new game
2. Check objectives displayed (UI not yet implemented, but check state)
3. Play through a day, aiming to complete objectives
4. Skip to next day
5. Check bonus applied

**Expected Results:**
- ✅ Day starts with 2–3 objectives
- ✅ Objectives have targets (e.g., "Sell 50 items", "Serve 20 customers")
- ✅ At end-of-day, completed objectives grant +$100–$150 bonus each
- ✅ Bonus appears in net revenue calculation
- ✅ Next day shows fresh new objectives

**Example:**
```
Day 1:
- Objective 1: Sell 60 items → Completed! +$100
- Objective 2: Serve 30 customers → Completed! +$100
- Objective 3: Earn $500 → NOT completed
- Cash flow: +$500 (revenue) -$100 (rent) -$0 (wages) +$200 (bonus) = +$600
```

**Debug Info:**
```kotlin
// Check GameState in debugger:
state.dailyObjectives.size() == 2 or 3
state.objectiveBonusEarned == Money(20_000)  // if 2 objectives completed
state.currentDayMetrics.revenue should > 0
```

---

### Scenario 4: Store Upgrade (Future UI, but test logic)

**Steps:**
1. Earn $5,000 (play a few days, should happen naturally)
2. Call `gameEngine.upgradeStoreSize()` programmatically (or via UI once added)
3. Check GameState changes

**Expected Results:**
- ✅ If cash >= $1,000: upgrade succeeds
  - ✅ `currentStoreSize` changes to MEDIUM
  - ✅ `dailyRent` increases to $400/day
  - ✅ `backroomCapPerItem` increases to 50 (from 10)
  - ✅ Cash decreases by $1,000
- ✅ If cash < $1,000: upgrade is rejected (state unchanged)
- ✅ If already at XL: upgrade is rejected (state unchanged)

**Debug Info:**
```kotlin
// Before upgrade:
state.currentStoreSize == StoreSize.SMALL
state.money >= Money(100_000)  // $1,000

// After upgrade:
state.currentStoreSize == StoreSize.MEDIUM
state.money -= Money(100_000)
state.currentStoreSize.dailyRent == Money(40_000)  // $400/day
```

---

### Scenario 5: Cumulative Operating Costs (Mid-Game)

**Steps:**
1. Build a store to MEDIUM size
2. Hire 3 CASHIERS + 3 STOCKERS
3. Play 7 days
4. Check cumulative costs

**Expected Results:**

Daily costs:
- Rent: $400 (MEDIUM store)
- Wages: 3×$50 (cashiers) + 3×$50 (stockers) = $300/day
- **Total daily cost: $700/day**

After 7 days:
- Operating costs: 7 × $700 = $4,900
- Revenue minus costs should show net profit/loss

**Example:**
```
Day 1: revenue $500 - costs $700 = -$200 (net negative!)
Day 2: revenue $600 - costs $700 = -$100
Day 3–7: revenue $800–1000/day should start covering costs
Week net: $4,400 revenue - $4,900 costs = -$500 (possible bankruptcy risk)
```

---

### Scenario 6: Negative Cash Flow Warning (Future Bankruptcy System)

**Steps:**
1. Set up expensive store (XL, $3,000/day rent)
2. Hire many staff (e.g., 10 staff = $500+/day)
3. Don't generate enough revenue
4. Watch cash deplete

**Expected Results:**
- ✅ Cash balance goes negative (deducted anyway)
- ✅ Game continues (soft fail, not hard game over yet)
- ✅ Player can recover by:
  - Firing staff (wages decrease)
  - Downgrading store (rent decreases)
  - Earning more revenue (hire better cashiers, optimize transactions)

**Debug Check:**
```kotlin
// Can check state after negative day:
state.money.cents < 0  // e.g., Money(-50_000) = -$500
```

---

## Edge Cases to Test

### Edge Case 1: Hire staff, then skip day without revenue
- **Expected**: Wages deducted even if revenue = $0
- **Result**: Cash goes negative

### Edge Case 2: Hire expensive staff, then fire them before next day
- **Expected**: If fired before midnight, wages not deducted for that staff
- **Result**: Only active staff incur wage cost

### Edge Case 3: Multiple store upgrades on same day
- **Expected**: Cannot upgrade twice in one action
- **Result**: Multiple clicks ignored

### Edge Case 4: Objectives with impossible targets
- **Expected**: Generator excludes impossible targets (e.g., "sell 100 items" if only 20 available)
- **Result**: All objectives are achievable

### Edge Case 5: Load saved game
- **Expected**: Store size, daily objectives, wage rates persist
- **Result**: No data loss, defaults applied correctly

---

## Debug Commands (For Manual Testing)

If you add debug methods to GameEngine:

```kotlin
// Set cash to test scenarios
fun setMoney(cents: Long) {
    state = state.copy(money = Money(cents))
}

// Force store upgrade
fun forceUpgradeStore() {
    state = storeController.upgradeStoreSize(state)
}

// Force day rollover (without time passing)
fun forceDayRollover() {
    state = dayManager.rollOverDay(state, state.currentTime.dayNumber)
    dayManager.advanceDay(state.currentTime.dayNumber + 1)
}

// Print summary
fun printDailySummary() {
    println("""
        Store: ${state.currentStoreSize.displayName} (rent: ${state.currentStoreSize.dailyRent})
        Cash: ${state.money}
        Staff: ${state.hiredEntityRegistry.totalCount()}
        Objectives: ${state.dailyObjectives.size}
        Today's metrics:
          - Revenue: ${state.currentDayMetrics.revenue}
          - Rent: ${state.currentDayMetrics.rentPaid}
          - Wages: ${state.currentDayMetrics.wagesPaid}
          - Objectives bonus: ${state.objectiveBonusEarned}
    """.trimIndent())
}
```

---

## UI Testing Checklist (Once UI Added)

- [ ] Store size displayed in main screen
- [ ] Daily rent cost shown in dashboard
- [ ] Staff wages displayed in staff panel
- [ ] Objective list visible with progress bars
- [ ] End-of-day report shows rent/wages breakdown
- [ ] Objective bonuses shown in end-of-day report
- [ ] Store upgrade button available when affordable
- [ ] Store upgrade dialog shows cost and new capacity

---

## Known Issues / Limitations

### Current Limitations
1. **UI Not Yet Implemented** — store size, objectives, rent/wages don't show in UI
2. **No Bankruptcy Protection** — cash can go negative indefinitely
3. **No Soft-Fail Condition** — no "game over" screen for sustained negative cash
4. **Objective Difficulty Not Tuned** — targets may be too easy or too hard

### Mitigation for Testing
- Use debug methods to inspect state
- Check Logcat for rollover messages
- Read GameState directly in debugger

---

## Success Criteria for Phase 1

✅ **All of the following must pass:**

1. Rent deducted daily based on store size
2. Wages deducted daily based on hired staff
3. Daily objectives generated and evaluated
4. Objective bonuses applied to cash balance
5. Store can be upgraded (logic works, UI pending)
6. End-of-day metrics capture rent/wages
7. Game doesn't crash with operating costs
8. Multiple days can be played without issues
9. Save/load preserves store size and operating costs

---

## Next Steps After Testing

Once Phase 1 passes all tests:

1. **Add UI Components** (Priority 1)
   - Store size display
   - Daily rent/wages info panel
   - Objective progress tracker
   - End-of-day bonus breakdown

2. **Add Bankruptcy System** (Priority 2)
   - Warn when cash < daily cost
   - Implement game-over condition
   - Add recovery mechanics (fire staff, downgrade store)

3. **Fine-Tune Economics** (Priority 3)
   - Adjust wage rates if too high/low
   - Tune objective difficulty and bonuses
   - Verify difficulty curve (easy early → challenging late)

4. **Start Phase 2** (Once Phase 1 complete)
   - Delivery delays for orders
   - Customer reputation system
   - Seasonal traffic variations

---

**Last Updated**: April 15, 2026  
**Status**: Ready for testing 🧪

