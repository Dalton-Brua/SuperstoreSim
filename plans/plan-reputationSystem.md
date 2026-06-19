# Plan: Store Reputation System

## Context

The pricing system makes markups strictly superior to markdowns. This plan adds a **Store Reputation** system — a multi-factor score (0–200%) that tracks overall store quality and converts it into gameplay bonuses. 100% is neutral (baseline behavior). Bonuses and penalties accelerate quadratically at the extremes, creating high stakes at both ends.

Revenue is the primary driver, but out-of-stock events and store appearance (zone scores) also contribute. This creates meaningful tradeoffs: markup for short-term profit, or invest in volume/quality for long-term compounding benefits.

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

A composite score computed daily at rollover from 3 weighted factors. 100% = neutral. Each sub-score is 0–200%.

```
dailyScore = (revenueScore × 0.50) + (stockScore × 0.30) + (appearanceScore × 0.20)
```

The composite nudges the persistent `reputationScore` by 1–3 points per day (see Daily Nudge Smoothing below).

### Factor 1: Revenue Performance (50%)

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

### Factor 2: Stock Availability (30%)

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

Data source: `GameState.avgZoneScore` — computed property on `GameState` (line 330-341) from inventory zone scores. Also snapshotted in `DailyMetrics.avgZoneScore` (line 148). Use the `DailyMetrics` snapshot at rollover.

### Daily Nudge Smoothing (Rolling-Average Feel)

Reputation is **hard to move**. A single perfect or horrific day shifts it by only 1–3 points. Sustained performance over ~30 days is what actually changes reputation.

**Asymmetric nudge**: recovery is always faster than decline. Falling capped at -2 pts/day, rising goes up to +3.

```kotlin
fun smoothReputation(
    currentRep: Float,
    dailyComposite: Float,
    daysTracked: Int
): Float {
    if (daysTracked == 0) return NEUTRAL_REPUTATION // First day: start at 100

    val delta = dailyComposite - currentRep
    val absDelta = abs(delta)
    val isRising = delta > 0

    val nudgeMagnitude = when {
        absDelta < 25f -> 1f
        absDelta < 75f -> 2f
        else -> if (isRising) 3f else 2f  // Asymmetric: max fall = 2, max rise = 3
    }

    val nudge = sign(delta) * nudgeMagnitude
    return (currentRep + nudge).coerceIn(MIN_REPUTATION, MAX_REPUTATION)
}
```

| Day Quality | Daily Composite | Delta | Nudge (rising) | Nudge (falling) |
|---|---|---|---|---|
| Horrific | 0–25% | -75+ | — | **-2 pts** (capped) |
| Bad | 25–75% | -25 to -75 | — | -2 pts |
| Slightly off | 75–100% | 0 to -25 | — | -1 pt |
| Slightly good | 100–125% | 0 to +25 | +1 pt | — |
| Good | 125–175% | +25 to +75 | +2 pts | — |
| Perfect | 175–200% | +75+ | +3 pts | — |

**Implications:**
- Starting at 100%, 30 straight perfect days → ~190%. Never trivial to max out.
- Single bad day at 100% → 99%. Barely noticeable.
- Worst-case decline: -2 pts/day. From 100% → 50% takes **25 days** of consistently terrible play.
- Recovery at +3 pts/day. From 50% → 100% takes **~17 days** of perfect play.
- Asymmetry means recovery is always ~1.5x faster than decline. Player is never permanently stuck.
- This gives the "rolling 30-day average" feel without storing 30 days of history.

### Going Out of Business Sale (Death Spiral Safety Valve)

When reputation drops below **30%**, the store triggers a "Going Out of Business Sale" event — a temporary traffic surge that gives the player a lifeline to restock revenue.

```kotlin
data class ReputationState(
    ...
    val lastSaleEventDay: Int = -7,  // game day of last GOOB sale (-7 = eligible immediately)
)

fun checkGoobSaleEligible(reputationState: ReputationState, currentDay: Int): Boolean {
    return reputationState.reputationScore < GOOB_SALE_THRESHOLD
        && (currentDay - reputationState.lastSaleEventDay) >= GOOB_SALE_COOLDOWN_DAYS
}
```

**Mechanics:**
- **Trigger**: reputation < 30% at day rollover, and ≥7 days since last sale event
- **Effect**: next day gets **1.5x traffic multiplier** (stacks with reputation traffic mult) and **all items priced at cost** (0% markup forced)
- **Duration**: 1 day
- **Cooldown**: 7 days (max 1 per week)
- **Purpose**: generates enough revenue to restock shelves and start climbing back. Selling at cost means no profit, but prevents bankruptcy and feeds the revenue score.
- **UI**: banner notification "Desperate times! Going Out of Business Sale draws a crowd" at day start. End-of-day report shows "GOOB Sale: +50% traffic, items sold at cost."

**Why selling at cost works**: player gets zero profit margin but moves volume → revenue score recovers → stock score stays healthy from the restock → composite nudges reputation upward. Combined with asymmetric nudge (+3 vs -2), the player can claw back ~3 pts on sale day.

| Rep | Traffic (rep) | GOOB boost | Effective traffic | Revenue vs $800 target |
|---|---|---|---|---|
| 25% | 0.675x | 1.5x | 1.01x | Easily hit |
| 15% | 0.58x | 1.5x | 0.87x | Likely hit |
| 5% | 0.52x | 1.5x | 0.78x | Tight but possible |

Constants:
```kotlin
const val GOOB_SALE_THRESHOLD = 30f       // triggers below this reputation
const val GOOB_SALE_COOLDOWN_DAYS = 7     // max once per week
const val GOOB_SALE_TRAFFIC_BOOST = 1.5f  // stacks with rep traffic mult
```

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

Modifies the per-item purchase probability. High rep → customers tolerate higher prices. Low rep → hypersensitive.

The current pricing system uses a global `smoothedPriceIndex` to derive `priceTrafficMultiplier` and `basketSizeMultiplier` in `PricingState` (line 17-23). Per-item multipliers are computed in `PricingManager.computePricingData()` (line 185-196) using `PricingConfig.priceElasticity` (default 1.5).

Reputation modifies the **effective elasticity** used in per-item probability:

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
```

Applied in `PricingManager.computePricingData()` where per-item multipliers are calculated:
```kotlin
// Current (PricingManager.kt:192):
// (r.basePrice.cents.toDouble() / r.effectivePrice.cents.toDouble())
//     .pow(config.priceElasticity.toDouble()).toFloat()
//
// With reputation:
val effectiveElasticity = config.priceElasticity / state.reputationState.priceToleranceMultiplier
// ... .pow(effectiveElasticity.toDouble()).toFloat()
```

Also applied to the global traffic/basket elasticity in `PricingState`:
```kotlin
// PricingState.kt:17-23 — priceTrafficMultiplier and basketSizeMultiplier
// These use PricingConfig.DEFAULT.trafficElasticity and basketElasticity
// With reputation, divide those elasticities by priceToleranceMultiplier
```

| Rep | Tolerance | Effective Elasticity (item) |
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

Applied in `InventoryManager.placeBulkOrder()` (line 413-418) by adding to the tiered `discountFraction`:
```kotlin
// Current tiered discount: 0.10/0.15/0.25 based on total cases
// With reputation:
val repBonus = state.reputationState.supplierDiscountBonus.toDouble()
val finalDiscount = discountFraction + repBonus
val finalCost = Money((baseCost.cents * (1.0 - finalDiscount)).toLong())
```

Also applied in fresh bulk orders (`placeFreshBulkOrder()` if it exists with similar discount structure).

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
@Serializable
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

    // Going Out of Business Sale
    val lastSaleEventDay: Int = -7,        // game day of last GOOB sale
    val goobSaleActiveToday: Boolean = false,

    // Last day's breakdown (for End-of-Day report)
    val lastRevenueScore: Float = 0f,
    val lastStockScore: Float = 0f,
    val lastAppearanceScore: Float = 0f,
    val lastDailyComposite: Float = 0f,
)
```

Added to `GameState` (`GameStateData.kt`):
```kotlin
val reputationState: ReputationState = ReputationState(),
```

### StoreSize additions

Add `baseRevenueTarget: Money?` to `StoreSize` enum (`domain/store/StoreSize.kt`):

Current properties (line 7-22): `displayName`, `dailyRent`, `shelfCapacity`, `backroomCapPerItem`, `trafficMultiplier`, `basketSizeMultiplier`, `upgradeCost`, `maxRegisters`, `requiredUpgradeId`.

Add new property:
```kotlin
val baseRevenueTarget: Money? = null,
```

Values:
```kotlin
MOM_AND_POP(     ..., baseRevenueTarget = null)        // reputation inactive
SMALL_GROCERY(   ..., baseRevenueTarget = null)        // reputation inactive
GROCERY_STORE(   ..., baseRevenueTarget = Money(80_000))    // $800
SUPERSTORE(      ..., baseRevenueTarget = Money(300_000))   // $3,000
SUPERCENTER(     ..., baseRevenueTarget = Money(1_000_000)) // $10,000
```

---

## ReputationManager

New class in `domain/reputation/ReputationManager.kt`. Pure functions:

| Method | Purpose |
|---|---|
| `updateReputation(reputationState, snapshot, storeSize)` | Full day-rollover: compute sub-scores, composite, EMA smooth, adjust target, derive multipliers. Returns new `ReputationState`. |
| `computeRevenueScore(todayRevenue, currentTarget)` | Revenue vs target → 0–200 |
| `computeStockScore(itemsSold, itemsLostToOOS)` | OOS rate → 0–200 |
| `computeAppearanceScore(avgZoneScore)` | Zone score → 0–200 |
| `smoothReputation(currentRep, dailyComposite, daysTracked)` | Asymmetric nudge logic |
| `adjustTarget(currentTarget, todayRevenue, baseTarget)` | Hit/miss ratchet logic |
| `deriveTrafficMultiplier(score)` | Score → 0.5–2.0 (quadratic) |
| `derivePriceToleranceMultiplier(score)` | Score → 0.7–1.4 (quadratic) |
| `deriveSupplierBonus(score)` | Score → 0–0.10 (quadratic, above 100% only) |
| `initializeTarget(storeSize)` | Set initial target from store size base |
| `checkGoobSaleEligible(reputationState, currentDay)` | Returns true if rep < 30% and cooldown elapsed |
| `applyGoobSale(reputationState, currentDay)` | Sets `goobSaleActiveToday = true`, updates `lastSaleEventDay` |

Note: `avgZoneScore` is passed via `snapshot.avgZoneScore` (already stored in `DailyMetrics` at line 148).

Companion constants:
```kotlin
const val WEIGHT_REVENUE = 0.50f
const val WEIGHT_STOCK = 0.30f
const val WEIGHT_APPEARANCE = 0.20f
const val NUDGE_SMALL = 1f
const val NUDGE_MEDIUM = 2f
const val NUDGE_RISE_LARGE = 3f
const val NUDGE_FALL_MAX = 2f
const val NUDGE_THRESHOLD_SMALL = 25f
const val NUDGE_THRESHOLD_LARGE = 75f
const val GOOB_SALE_THRESHOLD = 30f
const val GOOB_SALE_COOLDOWN_DAYS = 7
const val GOOB_SALE_TRAFFIC_BOOST = 1.5f
const val MIN_REPUTATION = 0f
const val MAX_REPUTATION = 200f
const val NEUTRAL_REPUTATION = 100f
```

---

## Integration Points

### TrafficManager.update() — `domain/traffic/TrafficManager.kt:47-51`

Add `reputationState.trafficMultiplier` to the existing multiplication chain:

```kotlin
// Current (line 47-51):
val customerRatePerSecond =
    (pattern.baseCustomerRate / 60.0) *
    state.storeConfig.gameSpeedMultiplier *
    state.currentStoreSize.trafficMultiplier *
    state.pricingState.priceTrafficMultiplier

// With reputation:
val goobBoost = if (state.reputationState.goobSaleActiveToday) GOOB_SALE_TRAFFIC_BOOST.toDouble() else 1.0
val customerRatePerSecond =
    (pattern.baseCustomerRate / 60.0) *
    state.storeConfig.gameSpeedMultiplier *
    state.currentStoreSize.trafficMultiplier *
    state.pricingState.priceTrafficMultiplier *
    state.reputationState.trafficMultiplier *
    goobBoost
```

### PricingManager.computePricingData() — `domain/pricing/PricingManager.kt:185-196`

Modify per-item purchase probability multiplier to use tolerance:

```kotlin
// Current (line 192-193):
// .pow(config.priceElasticity.toDouble()).toFloat()

// With reputation:
val effectiveElasticity = config.priceElasticity / state.reputationState.priceToleranceMultiplier
// .pow(effectiveElasticity.toDouble()).toFloat()
```

### PricingState computed properties — `domain/pricing/PricingState.kt:17-23`

The global `priceTrafficMultiplier` and `basketSizeMultiplier` use hardcoded elasticity from `PricingConfig.DEFAULT`. To apply reputation tolerance here, these need to accept the reputation multiplier. Options:
1. Pass `priceToleranceMultiplier` into the computed properties (requires refactoring PricingState to non-computed)
2. Apply tolerance at the call site in `PricingManager.updateSmoothedPriceIndex()`
3. Store effective elasticities in `PricingState`

**Recommended**: Move the elasticity application to `PricingManager.updateSmoothedPriceIndex()` where `GameState` is available, computing `priceTrafficMultiplier` and `basketSizeMultiplier` there instead of as computed properties on `PricingState`.

### PricingManager.resolvePrice() — GOOB Sale override — `domain/pricing/PricingManager.kt:22-43`

During a GOOB sale, all items sell at base cost (0% effective markup):

```kotlin
fun resolvePrice(itemId: Int, state: GameState): ResolvedPrice {
    val meta = cache.get(itemId) ?: return ResolvedPrice(Money.ZERO, Money.ZERO, 0)

    // GOOB sale: sell at base price (cost to consumer, no markup)
    if (state.reputationState.goobSaleActiveToday) {
        return ResolvedPrice(meta.price, meta.price, 0)
    }

    // ... existing logic ...
}
```

### InventoryManager.placeBulkOrder() — `domain/inventory/InventoryManager.kt:413-419`

Add `supplierDiscountBonus` to the discount fraction:

```kotlin
// Current (line 413-418):
val discountFraction = when {
    totalCases >= 100 -> 0.25
    totalCases >= 50  -> 0.15
    totalCases >= 20  -> 0.10
    else              -> 0.0
}

// With reputation:
val repBonus = state.reputationState.supplierDiscountBonus.toDouble()
val totalDiscount = discountFraction + repBonus
val finalCost = Money((baseCost.cents * (1.0 - totalDiscount)).toLong())
```

### DayRolloverProcessor.process() — `domain/tick/DayRolloverProcessor.kt:36-96`

After `dayManager.rollOverDay()` (line 55) and before staff resets, update reputation:

```kotlin
// After line 55: s = dayManager.rollOverDay(s, dayManager.lastKnownDayNumber)
// The snapshot is in s.lastEndOfDayReport

val snapshot = s.lastEndOfDayReport
if (snapshot != null && s.currentStoreSize.baseRevenueTarget != null) {
    var updatedReputation = ReputationManager.updateReputation(
        reputationState = s.reputationState,
        snapshot = snapshot,
        storeSize = s.currentStoreSize,
    )

    // Check GOOB sale eligibility for next day
    val nextDay = s.currentTime.dayNumber
    if (ReputationManager.checkGoobSaleEligible(updatedReputation, nextDay)) {
        updatedReputation = ReputationManager.applyGoobSale(updatedReputation, nextDay)
    } else {
        updatedReputation = updatedReputation.copy(goobSaleActiveToday = false)
    }

    s = s.copy(reputationState = updatedReputation)
}
```

### Feature gating

System only active when `storeSize.baseRevenueTarget != null` (Grocery Store+). When inactive, all multipliers stay at default (1.0f traffic, 1.0f tolerance, 0f supplier bonus), and `reputationScore` stays at 100f (neutral). `ReputationManager.updateReputation()` returns state unchanged if gating fails.

`// TODO: gate on research revenue_reputation` comment at check site for future research integration.

---

## Research Gate

Add to `ResearchUpgradeRegistry` (`domain/research/ResearchUpgradeRegistry.kt`), under **Pricing** category (after `item_pricing` at line 795):

| ID | Name | Cost | Prerequisites | Gate Check | What it reveals |
|---|---|---|---|---|---|
| `revenue_reputation` | "Customer Loyalty Study" | 20 | `category_pricing` | `currentStoreSize >= GROCERY_STORE` | Store Reputation system |

```kotlin
put("revenue_reputation", ResearchableUpgrade(
    id = "revenue_reputation",
    displayName = "Customer Loyalty Study",
    description = "Unlocks the Store Reputation system — track and improve your store's reputation for traffic and pricing bonuses.",
    teaserDescription = "A good reputation brings customers back.",
    category = ResearchCategory.PRICING,
    researchCost = 20,
    prerequisites = listOf("category_pricing"),
    gateCheck = { s -> s.currentStoreSize.ordinal >= StoreSize.GROCERY_STORE.ordinal },
))
```

---

## UI

### End-of-Day Report (`ui/dialogs/EndOfDayReportDialog.kt`)

Add "Store Reputation" section (only when feature active):
- **Composite score**: "Reputation: 134%" with color indicator (red <75%, yellow 75-100%, green >100%)
- **Sub-score breakdown**:
  - Revenue: "$1,245 / $812 target" ✓ (score: 153%)
  - Stock: "3% OOS rate" (score: 188%)
  - Appearance: "Zone: 0.82" (score: 164%)
- **Active bonuses**: "Traffic: 1.18x | Tolerance: 1.06x | Supplier: +1.7%"
- **Target update**: "Tomorrow's target: $825" with ↑/↓ indicator
- **Streak**: "5-day target streak" or "Target missed (2 days)"

### Home Screen — `ui/components/cards/StoreOverviewCard.kt`

Add reputation line (when active):
- "Reputation: 134%" with color (red/yellow/green)
- "Traffic +18%" compact bonus indicator

### Pricing Panel (`ui/components/panels/SettingsPanel.kt`)

- "Revenue Target: $825"
- "Reputation: 134%"
- "Price Tolerance: +6%"

---

## Serialization

kotlinx.serialization is already in place (`GameStateSerializer.kt` — `Json { ignoreUnknownKeys = true; encodeDefaults = true }`).

### What to do

1. **Annotate `ReputationState` with `@Serializable`** — all fields are primitives or `Money` (value class → flat Long). No custom serializer needed.

2. **Add `reputationState: ReputationState = ReputationState()` to `GameState`** (`GameStateData.kt:218`) — default value means old saves missing the key deserialize cleanly via `ignoreUnknownKeys = true`.

3. **No version bump needed** — all new fields have safe defaults. No migration function required.

4. **Add `baseRevenueTarget` to `StoreSize` enum** — `StoreSize` is `@Serializable` (line 6). New property with default `null` won't break existing saves since enums serialize by name, not by constructor.

5. **`LegacyGameStateDeserializer`** (`domain/persistence/LegacyGameStateDeserializer.kt`) — handles pre-`saveVersion` saves. `ReputationState` will be absent from legacy saves; `GameState`'s default handles that. No legacy deserializer changes needed.

**Backward compat:** Missing `reputationState` → `ReputationState()` (score 100f, all multipliers neutral). No data loss on old saves.

---

## Files to Create/Modify

### New files
- `domain/reputation/ReputationState.kt`
- `domain/reputation/ReputationManager.kt`
- `test/.../domain/reputation/ReputationManagerTest.kt`

### Modified files
- `domain/GameStateData.kt` — add `reputationState` field to `GameState` (line ~320, before `simAccumulators`)
- `domain/store/StoreSize.kt` — add `baseRevenueTarget: Money?` property (line 7-22) and values per enum entry (lines 24-78)
- `domain/traffic/TrafficManager.kt` — multiply by `reputationState.trafficMultiplier` + GOOB boost (line 47-51)
- `domain/pricing/PricingManager.kt` — use `priceToleranceMultiplier` in `computePricingData()` (line 192) and GOOB override in `resolvePrice()` (line 22)
- `domain/pricing/PricingState.kt` — refactor `priceTrafficMultiplier`/`basketSizeMultiplier` to accept tolerance parameter (lines 17-23)
- `domain/inventory/InventoryManager.kt` — apply `supplierDiscountBonus` in `placeBulkOrder()` (line 413-419)
- `domain/tick/DayRolloverProcessor.kt` — call `ReputationManager.updateReputation()` after `dayManager.rollOverDay()` (after line 55)
- `domain/research/ResearchUpgradeRegistry.kt` — add `revenue_reputation` entry (after line 795)
- `ui/dialogs/EndOfDayReportDialog.kt` — reputation section in end-of-day report
- `ui/components/cards/StoreOverviewCard.kt` — reputation line on home screen
- `ui/components/panels/SettingsPanel.kt` — target + tolerance display
- `ui/viewmodels/GameViewModel.kt` — expose reputation UI state

### Tests

| Test | What it verifies |
|---|---|
| Revenue score: at target = 100, double = 200, zero = 0 | `computeRevenueScore` math |
| Stock score: 0% OOS = 200, 25% = 100, 50% = 0 | `computeStockScore` math |
| Appearance score: zone 1.0 = 200, 0.5 = 100, 0.0 = 0 | `computeAppearanceScore` scaling |
| Composite: weighted sum correct, clamped 0–200 | Factor weights sum to 1.0 (0.50 + 0.30 + 0.20) |
| Nudge: perfect day at 100% → 103% | +3 pt max rise nudge |
| Nudge: bad day at 100% → 99% | -1 pt small nudge |
| Nudge: horrific day at 100% → 98% | -2 pt max fall (asymmetric cap) |
| Nudge: 30 perfect days from 100% → ~190% | Never trivially maxed |
| Nudge: single horrific day at 150% → 148% | Resilient to one-offs, fall capped at 2 |
| Asymmetry: recovery always faster than decline | +3 max rise vs -2 max fall |
| GOOB sale triggers at rep < 30% | `checkGoobSaleEligible` returns true |
| GOOB sale respects 7-day cooldown | Returns false if last sale < 7 days ago |
| GOOB sale sets traffic boost + cost pricing | `goobSaleActiveToday` flag set |
| GOOB sale resets after day rollover | Flag cleared when not eligible |
| Death spiral recovery: 25% rep + GOOB sale → revenue target hit | End-to-end survival test |
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
2. **Integration**: Play at Grocery Store, verify all 3 sub-scores appear in End-of-Day report, multipliers change
3. **Balance**: Re-run pricing analysis with reputation factored in — confirm markdowns become viable when reputation traffic bonus is active
4. **Emulator**: Build and install, play ~5 days:
   - Reputation section in End-of-Day report with 3-factor breakdown
   - Target ratchets up on hits, down on misses
   - Traffic visibly changes with multiplier
   - Markup tolerance improves at high reputation
   - Feature hidden at Mom & Pop / Small Grocery
   - OOS events and zone score visibly affect reputation
   - Low reputation (below 100%) visibly punishes traffic
