# Plan: Store Reputation System

## Context

The pricing system makes markups strictly superior to markdowns. This plan adds a **Store Reputation** system — a multi-factor score (0–200%) that tracks overall store quality and converts it into gameplay bonuses. 100% is neutral (baseline behavior). Bonuses and penalties accelerate quadratically at the extremes, creating high stakes at both ends.

Revenue is the primary driver, but out-of-stock events, store appearance (zone scores), and customer service all contribute. This creates meaningful tradeoffs: markup for short-term profit, or invest in volume/quality for long-term compounding benefits.

**Gates**: Grocery Store size (3rd tier) + research unlock (`revenue_reputation`).

### Balance Analysis

At Grocery Store (4x traffic, ~194 customers/day, baseline $1,536/day profit):

| Reputation | Traffic | Tolerance | Daily Profit | vs Baseline |
|---|---|---|---|---|
| 0% | 0.50x | 0.70x | $768 | -50% |
| 50% | 0.82x | 0.89x | $1,265 | -18% |
| 100% | 1.00x | 1.00x | $1,536 | baseline |
| 150% | 1.35x | 1.14x | $2,079 | +35% |
| 200% | 2.00x | 1.40x | $3,073 | +100% |

With markups at high reputation:

| Markup | Rep 100% | Rep 150% | Rep 200% |
|---|---|---|---|
| 0% | $1,536 | $2,079 | $3,073 |
| 10% | $1,432 | $1,973 | $2,984 |
| 20% | $1,276 | $1,786 | $2,759 |
| 30% | $1,094 | $1,555 | $2,449 |

Markups become profitable above ~125% reputation. Below that, keep prices flat and build rep.

---

## Design

### Reputation Score (0–200%)

A composite score computed daily at rollover from 4 weighted factors. 100% = neutral. Each sub-score is 0–200%.

```
dailyScore = (revenueScore × 0.40) + (stockScore × 0.25) + (appearanceScore × 0.20) + (serviceScore × 0.15)
```

The composite is EMA-smoothed into a persistent `reputationScore`.

### Factor 1: Revenue Performance (40%)

A **dynamic revenue target** that ratchets with performance:

- **Hit target**: target increases 1–2% (scaled by overshoot)
- **Miss target**: target decreases 0.5–1.5% (scaled by undershoot), floored at store size base

```kotlin
if (todayRevenue >= currentTarget) {
    val overshoot = (todayRevenue.cents.toDouble() / currentTarget.cents - 1.0).coerceIn(0.0, 1.0)
    val growthRate = 0.01 + 0.01 * overshoot  // 1–2%
    newTarget = Money((currentTarget.cents * (1.0 + growthRate)).toLong())
} else {
    val undershoot = (1.0 - todayRevenue.cents.toDouble() / currentTarget.cents).coerceIn(0.0, 1.0)
    val decayRate = 0.005 + 0.01 * undershoot  // 0.5–1.5%
    newTarget = Money((currentTarget.cents * (1.0 - decayRate)).toLong())
}
newTarget = maxOf(newTarget, storeSize.baseRevenueTarget)
```

Revenue sub-score (0–200%):
```
revenueScore = clamp((todayRevenue / currentTarget) × 100, 0, 200)
```
At target = 100%. Double target = 200%. Zero revenue = 0%.

### Factor 2: Stock Availability (25%)

Measures how well the store avoids out-of-stock events:

```
outOfStockRate = itemsLostToOutOfStock / (itemsSold + itemsLostToOutOfStock)
stockScore = clamp(100 + (100 - outOfStockRate × 400), 0, 200)
```

- 0% OOS → 200% (perfect availability, bonus)
- 12.5% OOS → 150% (minor issues)
- 25% OOS → 100% (neutral)
- 50% OOS → 0% (severe penalty)

Data source: `DailyMetrics.itemsLostToOutOfStock` and `DailyMetrics.itemsSold` — already tracked.

### Factor 3: Store Appearance (20%)

Uses the existing `avgZoneScore` (0.0–1.0) from the zoning system:

```
appearanceScore = clamp(avgZoneScore × 200, 0, 200)
```

- 1.0 zone → 200% (perfectly maintained, bonus)
- 0.5 zone → 100% (neutral)
- 0.0 zone → 0% (neglected)

Data source: `GameState.avgZoneScore` — already computed. Sampled at day rollover.

### Factor 4: Customer Service (15%)

Measures checkout efficiency — customers served vs lost to queue overflow:

```
serviceRate = customersServed / (customersServed + customersLost)
serviceScore = clamp(serviceRate × 200, 0, 200)
```

- 100% served → 200% (no lost customers, bonus)
- 50% served → 100% (neutral)
- 0% served → 0% (everyone walks away)
- No customers at all → 100% (neutral, no penalty)

Data source: `DailyMetrics.customersServed` already tracked. Need to add `customersLost` tracking.

### EMA Smoothing

```kotlin
val alpha = if (daysTracked < 7) 0.4f else 0.2f
smoothedScore = alpha * dailyScore + (1 - alpha) * previousSmoothedScore
```

Alpha 0.2 = ~5-day half-life. Consistent quality rewarded, single bad days forgiven. Alpha 0.4 for first 7 days to bootstrap faster.

---

## What Reputation Affects

All bonus/penalty curves use **quadratic acceleration** (power 1.5) so effects are mild near 100% and dramatic at the extremes.

### 1. Traffic Multiplier (primary)

```kotlin
fun deriveTrafficMultiplier(rep: Float): Float {
    val n = rep / 100f  // 0..2
    return if (n >= 1f) {
        val excess = n - 1f  // 0..1
        1f + excess.pow(1.5f)  // 1.0x → 2.0x (quadratic acceleration)
    } else {
        val deficit = 1f - n  // 0..1
        1f - 0.5f * deficit.pow(1.5f)  // 1.0x → 0.5x
    }
}
```

| Rep | Traffic |
|---|---|
| 0% | 0.50x |
| 50% | 0.82x |
| 75% | 0.94x |
| 100% | 1.00x |
| 125% | 1.12x |
| 150% | 1.35x |
| 175% | 1.65x |
| 200% | 2.00x |

### 2. Price Tolerance

Modifies the per-item purchase probability elasticity. High rep → customers tolerate higher prices. Low rep → hypersensitive.

```kotlin
fun derivePriceToleranceMultiplier(rep: Float): Float {
    val n = rep / 100f
    return if (n >= 1f) {
        val excess = n - 1f
        1f + 0.4f * excess.pow(1.5f)  // 1.0x → 1.4x
    } else {
        val deficit = 1f - n
        1f - 0.3f * deficit.pow(1.5f)  // 1.0x → 0.7x
    }
}
// Applied: effectiveElasticity = PRICE_ELASTICITY / toleranceMultiplier
```

| Rep | Tolerance | Effective Elasticity |
|---|---|---|
| 0% | 0.70x | 2.14 (brutal) |
| 50% | 0.89x | 1.68 |
| 100% | 1.00x | 1.50 |
| 150% | 1.14x | 1.31 |
| 200% | 1.40x | 1.07 (lenient) |

### 3. Bulk Order Discount Bonus

Above 100% reputation, supplier relationships improve:

```kotlin
fun deriveSupplierBonus(rep: Float): Float {
    if (rep <= 100f) return 0f
    val excess = (rep - 100f) / 100f  // 0..1
    return 0.10f * excess.pow(1.5f)  // 0% → 10% extra discount
}
```

| Rep | Supplier Bonus |
|---|---|
| ≤100% | 0% |
| 125% | +1.4% |
| 150% | +3.5% |
| 175% | +6.5% |
| 200% | +10% |

---

## Data Model

### ReputationState

New data class in `domain/reputation/ReputationState.kt`:

```kotlin
data class ReputationState(
    val reputationScore: Float = 100f,          // EMA-smoothed composite (0–200)
    val currentRevenueTarget: Money = Money.ZERO, // 0 = uninitialized
    val daysTracked: Int = 0,

    // Cached multipliers (recomputed from score at day rollover)
    val trafficMultiplier: Float = 1.0f,
    val priceToleranceMultiplier: Float = 1.0f,
    val supplierDiscountBonus: Float = 0f,

    // Streak counters (UI display)
    val consecutiveTargetHits: Int = 0,
    val consecutiveTargetMisses: Int = 0,

    // Last day's breakdown (for End-of-Day report)
    val lastRevenueScore: Float = 0f,
    val lastStockScore: Float = 0f,
    val lastAppearanceScore: Float = 0f,
    val lastServiceScore: Float = 0f,
    val lastDailyComposite: Float = 0f,
)
```

Added to `GameState`:
```kotlin
val reputationState: ReputationState = ReputationState(),
```

### StoreSize additions

Add `baseRevenueTarget: Money?` to `StoreSize` enum:

```kotlin
MOM_AND_POP(     ..., baseRevenueTarget = null)
SMALL_GROCERY(   ..., baseRevenueTarget = null)
GROCERY_STORE(   ..., baseRevenueTarget = Money(80_000))    // $800
SUPERSTORE(      ..., baseRevenueTarget = Money(300_000))   // $3,000
SUPERCENTER(     ..., baseRevenueTarget = Money(1_000_000)) // $10,000
```

### Customers Lost tracking

Add `customersLost: Int = 0` to `DailyMetricsAccumulator` and `DailyMetrics`. Increment in `GameEngine.tick()` when a customer arrives but can't be queued (all registers full / queue overflow).

---

## ReputationManager

New class in `domain/reputation/ReputationManager.kt`. Pure functions:

| Method | Purpose |
|---|---|
| `updateReputation(reputationState, snapshot, avgZoneScore, storeSize)` | Full day-rollover: compute sub-scores, composite, EMA smooth, adjust target, derive multipliers. Returns new `ReputationState`. |
| `computeRevenueScore(todayRevenue, currentTarget)` | Revenue vs target → 0–200 |
| `computeStockScore(itemsSold, itemsLostToOOS)` | OOS rate → 0–200 |
| `computeAppearanceScore(avgZoneScore)` | Zone score → 0–200 |
| `computeServiceScore(customersServed, customersLost)` | Service ratio → 0–200 |
| `adjustTarget(currentTarget, todayRevenue, baseTarget)` | Hit/miss ratchet logic |
| `deriveTrafficMultiplier(score)` | Score → 0.5–2.0 (quadratic) |
| `derivePriceToleranceMultiplier(score)` | Score → 0.7–1.4 (quadratic) |
| `deriveSupplierBonus(score)` | Score → 0–0.10 (quadratic, above 100% only) |
| `initializeTarget(storeSize)` | Set initial target from store size base |

Companion constants:
```kotlin
const val WEIGHT_REVENUE = 0.40f
const val WEIGHT_STOCK = 0.25f
const val WEIGHT_APPEARANCE = 0.20f
const val WEIGHT_SERVICE = 0.15f
const val EMA_ALPHA = 0.2f
const val EMA_ALPHA_BOOTSTRAP = 0.4f
const val BOOTSTRAP_DAYS = 7
const val MIN_REPUTATION = 0f
const val MAX_REPUTATION = 200f
const val NEUTRAL_REPUTATION = 100f
```

---

## Integration Points

### TrafficManager.update() — line 52–56

Add `reputationState.trafficMultiplier`:

```kotlin
val customerRatePerSecond =
    (pattern.baseCustomerRate / 60.0) *
    state.storeConfig.gameSpeedMultiplier *
    state.currentStoreSize.trafficMultiplier *
    state.pricingState.priceTrafficMultiplier *
    state.reputationState.trafficMultiplier
```

### PricingManager — price elasticity

Modify `purchaseProbabilityMultiplier()` to use tolerance:

```kotlin
fun purchaseProbabilityMultiplier(itemId: Int, state: GameState): Float {
    val resolved = resolvePrice(itemId, state)
    if (resolved.basePrice.cents <= 0 || resolved.effectivePrice.cents <= 0) return 1.0f
    val ratio = resolved.basePrice.cents.toDouble() / resolved.effectivePrice.cents.toDouble()
    val effectiveElasticity = PRICE_ELASTICITY / state.reputationState.priceToleranceMultiplier
    return ratio.pow(effectiveElasticity.toDouble()).toFloat()
}
```

### InventoryManager — bulk order discount

In `processBulkOrder()`, add `state.reputationState.supplierDiscountBonus` to the discount fraction.

### DayManager.rollOverDay()

After snapshot, before returning:

```kotlin
val updatedReputation = ReputationManager.updateReputation(
    reputationState = processedState.reputationState,
    snapshot = snapshot,
    avgZoneScore = processedState.avgZoneScore,
    storeSize = processedState.currentStoreSize,
)
// Include in returned state.copy(reputationState = updatedReputation)
```

### GameEngine.tick() — customer lost tracking

When a customer arrives but can't be queued, increment `customersLost` in the daily metrics accumulator. Find the existing pending customer overflow logic and add tracking there.

### Feature gating

System only active when `storeSize.baseRevenueTarget != null` (Grocery Store+). When inactive, all multipliers stay at default (1.0f traffic, 1.0f tolerance, 0f supplier bonus), and `reputationScore` stays at 100f (neutral). `ReputationManager.updateReputation()` returns state unchanged if gating fails.

`// TODO: gate on research revenue_reputation` comment at check site for future research system.

---

## Research Gate

Add to `plans/plan-researchSystem.md`, under **Pricing** category:

| ID | Name | Cost | Prerequisites | Gate Check | What it reveals |
|---|---|---|---|---|---|
| `revenue_reputation` | "Customer Loyalty Study" | 20 | `category_pricing` | `currentStoreSize >= GROCERY_STORE` | Store Reputation system |

---

## UI

### End-of-Day Report (`EndOfDayReportDialog.kt`)

Add "Store Reputation" section (only when feature active):
- **Composite score**: "Reputation: 134%" with color indicator (red <75%, yellow 75-100%, green >100%)
- **Sub-score breakdown**:
  - Revenue: "$1,245 / $812 target" ✓ (score: 153%)
  - Stock: "3% OOS rate" (score: 188%)
  - Appearance: "Zone: 0.82" (score: 164%)
  - Service: "142/148 served" (score: 192%)
- **Active bonuses**: "Traffic: 1.18x | Tolerance: 1.06x | Supplier: +1.7%"
- **Target update**: "Tomorrow's target: $825" with ↑/↓ indicator
- **Streak**: "5-day target streak" or "Target missed (2 days)"

### Home Screen — `StoreOverviewCard.kt`

Add reputation line (when active):
- "Reputation: 134%" with color (red/yellow/green)
- "Traffic +18%" compact bonus indicator

### Pricing Panel (`SettingsPanel.kt`)

- "Revenue Target: $825"
- "Reputation: 134%"
- "Price Tolerance: +6%"

---

## Serialization

kotlinx.serialization is already in place (`Json { ignoreUnknownKeys = true; encodeDefaults = true }`).
No manual serialization code needed.

### What to do

1. **Annotate `ReputationState` with `@Serializable`** — all fields are primitives or `Money` (value class → flat Long). No custom serializer needed.

2. **Add `reputationState: ReputationState = ReputationState()` to `GameState`** — default value means old saves missing the key deserialize cleanly via `ignoreUnknownKeys = true`.

3. **Add `customersLost: Int = 0` to `DailyMetrics` and `DailyMetricsAccumulator`** — both already `@Serializable`. Default `= 0` handles old saves.

4. **No version bump needed** — all new fields have safe defaults. No migration function required.

5. **Add `customersLost` to `LegacyGameStateDeserializer`** — use `json.optInt("customersLost", 0)` in both `deserializeDailyMetrics()` and `deserializeDailyMetricsAccumulator()`. Legacy saves won't have `reputationState` at all, and `GameState`'s default handles that.

**Backward compat:** Missing `reputationState` → `ReputationState()` (score 100f, all multipliers neutral). Missing `customersLost` → 0. No data loss on old saves.

---

## Files to Create/Modify

### New files
- `domain/reputation/ReputationState.kt`
- `domain/reputation/ReputationManager.kt`
- `test/.../domain/reputation/ReputationManagerTest.kt`

### Modified files
- `domain/GameStateData.kt` — add `reputationState` field to `GameState`
- `domain/store/StoreSize.kt` — add `baseRevenueTarget: Money?`
- `domain/traffic/TrafficManager.kt` — multiply by `reputationState.trafficMultiplier`
- `domain/pricing/PricingManager.kt` — use `priceToleranceMultiplier` in elasticity calc
- `domain/inventory/InventoryManager.kt` — apply `supplierDiscountBonus` in bulk orders
- `domain/metrics/DayManager.kt` — call `ReputationManager.updateReputation()` in `rollOverDay()`
- `domain/metrics/DailyMetrics.kt` — add `customersLost` to `DailyMetrics` and `DailyMetricsAccumulator`
- `domain/GameEngine.kt` — track customer loss events in metrics accumulator
- `domain/persistence/GameStateSerializer.kt` — serialize/deserialize `ReputationState` + `customersLost`
- `ui/dialogs/EndOfDayReportDialog.kt` — reputation section
- `ui/components/cards/StoreOverviewCard.kt` — reputation line
- `ui/components/panels/SettingsPanel.kt` — target + tolerance display
- `ui/viewmodels/GameViewModel.kt` — expose reputation UI state
- `plans/plan-researchSystem.md` — add `revenue_reputation` entry

### Tests

| Test | What it verifies |
|---|---|
| Revenue score: at target = 100, double = 200, zero = 0 | `computeRevenueScore` math |
| Stock score: 0% OOS = 200, 25% = 100, 50% = 0 | `computeStockScore` math |
| Appearance score: zone 1.0 = 200, 0.5 = 100, 0.0 = 0 | `computeAppearanceScore` scaling |
| Service score: 100% served = 200, 50% = 100, 0% = 0 | `computeServiceScore` math |
| Composite: weighted sum correct, clamped 0–200 | Factor weights sum to 1.0 |
| EMA: converges toward daily score | Multi-day sequences |
| Target hit: grows 1–2% | Hit with various overshoot levels |
| Target miss: shrinks 0.5–1.5%, floored at base | Miss with various undershoot levels |
| Target floor: never below store size base | Repeated misses → stays at base |
| Traffic mult: 0%→0.5x, 100%→1.0x, 200%→2.0x | `deriveTrafficMultiplier` quadratic curve |
| Tolerance: 0%→0.7x, 100%→1.0x, 200%→1.4x | `derivePriceToleranceMultiplier` quadratic |
| Supplier bonus: ≤100%→0, 200%→10% | `deriveSupplierBonus` quadratic above 100 |
| Store size gating: Mom&Pop → no-op, returns unchanged | Returns unchanged state |
| Store upgrade: target adjusts to new base if needed | Target moves up if below new base |
| Initialization: first day sets target from store size | `daysTracked=0` → set target, score=100 |
| Streak tracking: hits/misses counted | Consecutive counters reset/increment |
| Serialization round-trip | Full `ReputationState` survives save/load |

---

## Verification

1. **Unit tests**: All ReputationManager pure functions — sub-scores, composite, EMA, target adjustment, multiplier derivation, gating
2. **Integration**: Play at Grocery Store, verify all 4 sub-scores appear in End-of-Day report, multipliers change
3. **Balance**: Re-run pricing analysis script with reputation factored in — confirm markdowns become viable when reputation traffic bonus is active
4. **Emulator**: Build and install, play ~5 days:
   - Reputation section in End-of-Day report with breakdown
   - Target ratchets up on hits, down on misses
   - Traffic visibly changes with multiplier
   - Markup tolerance improves at high reputation
   - Feature hidden at Mom & Pop / Small Grocery
   - OOS events and zone score visibly affect reputation
   - Low reputation (below 100%) visibly punishes traffic
