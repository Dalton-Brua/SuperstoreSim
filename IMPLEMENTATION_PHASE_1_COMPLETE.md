# Phase 1 Implementation Summary

**Date**: April 15, 2026  
**Status**: ✅ Core implementation complete

## What Was Implemented

### 1. Store Sizing & Rent System ✅

**New Files Created:**
- `domain/store/StoreSize.kt` — Enum with SMALL/MEDIUM/LARGE/XL store sizes, daily rent costs, and capacity limits

**GameState Changes:**
- Added `currentStoreSize: StoreSize = StoreSize.SMALL`

**Features:**
- Each store size has a daily rent deducted at end-of-day
- Rent ranges from $100/day (SMALL) to $3,000/day (XL)
- Store upgrades cost $1,000–$7,000 depending on target size
- Each size increases shelf capacity and backroom limit per item
- Player can upgrade store via `GameEvent.UpgradeStoreSize`

**Integration Points:**
- `GameEngine.upgradeStoreSize()` — routes upgrade events
- `DayManager.rollOverDay()` — deducts rent from cash balance
- `StoreController.upgradeStoreSize()` — manages upgrade logic

---

### 2. Staff Wages System ✅

**New Files Created:**
- `domain/store/StaffWageCalculator.kt` — Calculates daily wages for all hired staff

**Daily Wage Rates:**
- CASHIER: $50/day
- FAST_CASHIER: $100/day
- STOCKER: $50/day
- FAST_STOCKER: $100/day
- CUSTOMER_SERVICE_REP: $75/day

**DailyMetrics Changes:**
- Added `rentPaid: Money` field
- Added `wagesPaid: Money` field
- Updated `netRevenue` to include: `(revenue - refundAmount) - rentPaid - wagesPaid`

**Integration Points:**
- `DayManager.rollOverDay()` — calculates total wages using `StaffWageCalculator.calculateTotalWages()`
- Wages deducted alongside rent at end-of-day
- `DailyMetricsAccumulator` includes rent/wages for snapshot

---

### 3. Daily Objectives System ✅

**New Files Created:**
- `domain/objectives/ObjectiveGenerator.kt` — Generates and evaluates daily objectives
- Data class `DailyObjective` with `ObjectiveType` enum

**Objective Types:**
1. `SELL_ITEMS` — Sell X items today (40–100 target)
2. `SERVE_CUSTOMERS` — Serve X customers (15–50 target)
3. `EARN_REVENUE` — Earn $X in revenue (dynamic based on prior 3-day avg)
4. `STOCK_ITEMS` — Stock X items from backroom (30–80 target)
5. `REDUCE_OOS` — Have <2 out-of-stock events

**Bonuses for Completion:**
- Most objectives: $100 per completed
- Revenue/OOS objectives: $150 per completed
- Bonuses applied at end-of-day automatically

**GameState Changes:**
- Added `dailyObjectives: List<DailyObjective>`
- Added `objectiveBonusEarned: Money`

**Integration Points:**
- `GameEngine.init()` — generates initial objectives
- `DayManager.rollOverDay()` — evaluates completion, applies bonus, generates new objectives for tomorrow
- Objectives reset daily with fresh targets

---

## GameState Changes Summary

```kotlin
data class GameState(
    // ... existing fields ...
    
    // Phase 1: Store Sizing & Rent System
    val currentStoreSize: StoreSize = StoreSize.SMALL,

    // Phase 1: Daily Objectives System
    val dailyObjectives: List<DailyObjective> = emptyList(),
    val objectiveBonusEarned: Money = Money.ZERO,
)
```

## New Events

Added to `GameEvent.kt`:
- `data object UpgradeStoreSize : GameEvent` — Triggers store upgrade if affordable

## End-of-Day Flow (Updated)

```
1. Evaluate daily objectives → calculate bonus
2. Deduct rent from cash balance
3. Deduct wages from cash balance
4. Add objective bonus to cash balance
5. Snapshot metrics with rent/wages recorded
6. Generate new objectives for tomorrow
7. Show end-of-day report
8. Auto-pause time
```

**New Equation:**
```
newCash = previousCash - rent - wages + objectiveBonus
```

---

## Testing Checklist

- [ ] Store can be upgraded from SMALL → MEDIUM (costs $1,000)
- [ ] Store upgrade increases backroom capacity
- [ ] Store upgrade increases shelf capacity
- [ ] Rent is deducted daily ($100/day for SMALL store)
- [ ] Wages are calculated correctly (count staff, multiply by wage)
- [ ] Wages are deducted at end-of-day
- [ ] Daily objectives are generated fresh each day
- [ ] Objectives are evaluated at end-of-day
- [ ] Objective bonuses are applied to cash balance
- [ ] End-of-day report shows rent, wages, and objective bonuses
- [ ] Bankruptcy protection: alert when cash would go negative

---

## Known Limitations (Future Work)

1. **UI Not Updated Yet** — TimeUIState, MetricsUIState, Dashboard need updates to show:
   - Current store size and daily rent
   - Staff wages due
   - Objective progress and completion
   - Rent/wages/bonus breakdown in daily report

2. **Bankruptcy Handling** — No hard failure condition yet:
   - Should warn player when cash < daily rent + wages
   - Could add soft fail (game pauses, offers "game over")

3. **Store Upgrade UI** — No UI button to trigger upgrades yet:
   - Need StoreUpgradeDialog or dedicated screen
   - Should show next size cost and benefits

4. **Objective UI** — Daily objectives not displayed:
   - Need objectives panel in main screen
   - Should show progress bars (X/50 items sold)
   - Should display completion at end-of-day

---

## Next Steps (Phase 1 Polish)

1. **Update UI State to include:**
   - Store size display
   - Daily rent/wages display
   - Objective list with progress
   - End-of-day bonus breakdown

2. **Create UI Components:**
   - `StoreUpgradeDialog` or panel
   - `ObjectivesPanel` with progress indicators
   - Update `MetricsUIState` with rent/wages

3. **Add Bankruptcy Handling:**
   - Warn when cash balance < daily operating cost
   - Implement game-over condition (optional soft fail)

4. **Fine-tune Balancing:**
   - Verify rent/wages don't make early game impossible
   - Test objective difficulty and bonuses
   - Ensure mid/late game feels challenging but fair

---

## Files Modified

### Core Domain
- `domain/GameStateData.kt` — Added store size and objectives fields
- `domain/GameEngine.kt` — Added upgradeStoreSize() method, init objectives
- `domain/metrics/DailyMetrics.kt` — Added rent/wages fields, updated netRevenue
- `domain/metrics/DayManager.kt` — Deduct rent/wages, evaluate objectives
- `domain/store/StoreController.kt` — Added upgradeStoreSize() method
- `ui/GameEvent.kt` — Added UpgradeStoreSize event

### New Files
- `domain/store/StoreSize.kt` — Store size enum
- `domain/store/StaffWageCalculator.kt` — Wage calculation helper
- `domain/objectives/ObjectiveGenerator.kt` — Objective generation and evaluation

### ViewModel
- `ui/viewmodels/GameViewModel.kt` — Added UpgradeStoreSize event routing

---

## Performance Impact

- ✅ Minimal: Rent/wages calculated once per day
- ✅ Objective generation is O(n) where n=number of objectives (2–3)
- ✅ No database queries added
- ✅ No new state serialization overhead

---

## Backward Compatibility

- ✅ All changes are additive (new fields have defaults)
- ✅ Existing saved games will load with `currentStoreSize = SMALL` (default)
- ✅ New objective system initializes on first day
- ✅ No migration needed

---

**Status**: Ready for UI integration and playtesting 🎮

