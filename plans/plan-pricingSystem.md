# Plan: Pricing System

## Overview

Add dynamic pricing to SuperstoreSimulator: category-level markup sliders, per-item player overrides, automated fresh-handler markdowns for expiring stock, store-wide traffic effects from pricing posture, basket size penalties for high markups, and per-lb weighted pricing for produce and meat counter items. All prices feed into purchase probability — higher prices reduce sales volume, lower prices increase it.

---

## Current State

- All prices static from `ItemMetadata.price` (loaded once from `items.json` → Room DB → `ItemMetadataCache`)
- `TransactionEngine.weightedSample()` selects items by `purchaseWeight` — no price factor
- `consumeShelfStock()` uses FIFO (`oldestShelfBatch()`) — customers always buy expiring stock first
- Fresh handlers only stock perishable items from backroom to shelf
- No markup, markdown, discount, or promotion system exists
- `TrafficManager` uses fixed `baseCustomerRate × storeSize.trafficMultiplier` — no price influence on traffic
- All items sold as fixed units — no per-lb weighed items despite several items described as "per lb" in `items.json`

---

## Price Resolution

New `PricingManager` resolves effective sell price at transaction time:

```
effectivePrice = basePrice × (1 + categoryMarkupPercent/100) × (1 + itemOverridePercent/100) × (1 - markdownPercent/100)
```

| Layer | Source | Range | Default |
|-------|--------|-------|---------|
| `basePrice` | `ItemMetadata.price` (DB) | immutable | — |
| `categoryMarkupPercent` | Player-set per `ItemCategory` | -50% to +100% | 0% |
| `itemOverridePercent` | Player-set per item ID | -75% to +200% | 0% |
| `markdownPercent` | System (fresh handler) or player | 0-100% | 0% |

All modifiers stack **multiplicatively**. 10% category markup + 20% item override on $3.00 = $3.00 × 1.10 × 1.20 = $3.96.

Slider granularity: **1% steps**.

**Arithmetic rule**: Never use `Money.times(Double)` — it truncates via integer division (`cents/100 * ...`), destroying sub-dollar prices. Always compute in cents as `Long`:
```kotlin
val combinedMultiplier = (1.0 + categoryMarkup / 100.0) * (1.0 + itemOverride / 100.0) * (1.0 - markdown / 100.0)
val effectiveCents = (basePrice.cents.toDouble() * combinedMultiplier).roundToLong()
val effectivePrice = Money(effectiveCents)
```
Compute the full multiplier as a single `Double`, apply once to `cents`, round (not truncate). One rounding step, no accumulated error.

**Sell-below-cost guard**: `resolvePrice()` floors the effective price at `ItemMetadata.unitCost`. Player can price items at cost (zero margin) but never below. UI shows a warning indicator when effective price is within 10% of unit cost ("Low Margin") and a hard floor indicator at cost ("At Cost — No Margin"). This prevents accidental bankruptcy from stacked markdowns while still allowing aggressive pricing strategies.

---

## Weighted Items (Sold by Weight)

Some items are priced per pound and sold by weight rather than as fixed units. Price resolution produces a per-lb effective price; the customer's random weight determines the line total.

### Data Model

Add `soldByWeight: Boolean = false` to the item data pipeline:

| Layer | Field | Default |
|-------|-------|---------|
| `items.json` | `"soldByWeight": true` | absent = false |
| `Item.kt` (Room entity) | `val soldByWeight: Boolean = false` | false |
| `ItemMetadata.kt` | `val soldByWeight: Boolean = false` | false |
| `ItemDataLoader.kt` | parse from JSON | false |
| `ItemMetadataCache.kt` | pass through | false |

### Which Items

Items with `soldByWeight = true` — loose produce and meat counter items priced "per lb":

| ID | Name | Category | Why weighted |
|----|------|----------|-------------|
| item_096 | Fuji Apples | PRODUCE | Loose produce, per lb |
| item_097 | Bananas | PRODUCE | Loose produce, per lb |
| item_099 | Ripe Tomato | PRODUCE | Loose produce, per lb |
| item_101 | Broccoli | PRODUCE | Loose produce, per lb |
| item_103 | Red Grapes | PRODUCE | Loose produce, per lb |
| item_108 | Chicken Breast | MEAT | Meat counter, per lb |
| item_110 | Pork Chops | MEAT | Meat counter, per lb |
| item_111 | Salmon Fillet | MEAT | Meat counter, per lb |
| item_114 | Ribeye Steak | MEAT | Meat counter, per lb |

**Not weighted** (pre-packaged at fixed weight despite "lb" in description): Ground Beef (1 lb tube), Bacon (1 lb pack), Shrimp (1 lb bag), Deli Turkey (1 lb sliced). Also not weighted: bagged/clamshell produce (carrots, spinach, strawberries, bell peppers, onions) and per-unit produce (avocado, lettuce head).

### Price Corrections for Weighted Items

Several weighted items had unrealistic per-lb prices or margins compared to real US supermarket pricing (2025–2026). Meat margins were especially thin (~20%) — real meat departments run 25–35%.

| Item | Old $/lb | New $/lb | Old cost/lb | New cost/lb | Old margin | New margin | Reason |
|------|----------|----------|-------------|-------------|------------|------------|--------|
| Bananas | $0.59 | $0.69 | $0.29 | $0.29 | 51% | 58% | Slight inflation adjustment |
| Ripe Tomato | $1.49 | $2.49 | $0.79 | $1.49 | 47% | 40% | Was unrealistically cheap for vine-ripened |
| Broccoli | $2.99 | $2.29 | $1.59 | $1.29 | 47% | 44% | Was above typical retail range |
| Chicken Breast | $6.99 | $4.49 | $5.49 | $2.99 | 21% | 33% | $6.99 is organic/premium; boneless skinless is $3.49–4.99 |
| Salmon Fillet | $13.99 | $11.99 | $10.99 | $8.49 | 21% | 29% | Slight reduction, margin was unsustainably thin |
| Ribeye Steak | $16.99 | $15.99 | $13.99 | $11.49 | 18% | 28% | Price fine, cost was too high leaving no margin |

**Unchanged** (already realistic): Fuji Apples ($1.99, 45%), Red Grapes ($2.99, 40%), Pork Chops ($4.99, 33% after cost fix to $2.99 from $3.89).

Note: Pork Chops price stays at $4.99 but cost drops from $3.89 to $2.99 — the old 22% margin was unrealistic for a meat department staple.

Produce margins (40–58%) are intentionally higher than meat (28–33%). This matches real supermarkets: produce markup is high but spoilage risk offsets it, meat margins are thin but volume is steady.

### Transaction Behavior

When `generateRandomTransaction()` builds a line for a weighted item:

```kotlin
if (meta.soldByWeight) {
    val weight = randomWeight(meta.category)  // e.g. 0.5–3.0 lbs
    val effectiveCents = (unitPriceCents.toDouble() * weight).roundToLong()
    TransactionLine(
        itemId = itemId,
        quantity = 1,                          // always consume 1 shelf unit
        unitPrice = unitPrice,                 // per-lb price (after pricing modifiers)
        weight = weight,
        lineTotal = Money(effectiveCents),
    )
} else {
    // existing: qty 1–3, lineTotal = unitPrice × qty
}
```

- **Inventory**: 1 shelf unit consumed per weighted line regardless of weight. A "unit" of bananas on the shelf represents ~1 lb; the weight randomizes what the customer pays, not how much stock is consumed. This avoids fractional inventory.
- **Weight range by category**:

| Category | Min (lbs) | Max (lbs) | Distribution |
|----------|-----------|-----------|-------------|
| PRODUCE | 0.5 | 3.0 | Uniform |
| MEAT | 0.75 | 2.5 | Uniform |

### Interaction with Pricing System

- **Price resolution**: Unchanged. `resolvePrice()` returns effective $/lb for weighted items, effective $/unit for fixed items. Weight is applied *after* resolution.
- **Sell-below-cost guard**: Applied per-lb before weight multiplication. A $5.49/lb chicken breast at cost floor $5.49/lb is correct regardless of weight.
- **Purchase probability**: Uses per-lb effective price vs per-lb base price. Weight doesn't affect *whether* a customer picks the item, only *how much they pay*.
- **Store Price Index**: Uses per-lb price ratios for weighted items. Weight variance is random noise, not a pricing decision — it shouldn't inflate/deflate the index.
- **TransactionLine display**: UI shows weight and $/lb for weighted items (e.g. "Bananas — 2.3 lbs @ $0.59/lb = $1.36") vs quantity for fixed items (e.g. "Cereal ×2 @ $4.99 = $9.98").

---

## Purchase Probability Impact

Add a price sensitivity multiplier to `weightedSample()`:

```
priceMultiplier = (basePrice / effectivePrice) ^ PRICE_ELASTICITY
```

- `PRICE_ELASTICITY = 1.5` (tuning constant)
- `effectivePrice > basePrice` → multiplier < 1.0 → less likely to buy
- `effectivePrice < basePrice` → multiplier > 1.0 → more likely to buy

Final weight: `purchaseWeight × zoneMultiplier × priceMultiplier`

Future extension: per-category elasticity (essentials like GROCERY at 0.8, luxuries like ELECTRONICS at 2.5).

---

## Store Price Index

Drives both traffic and basket size effects. Uses an **Exponential Moving Average (EMA)** to prevent price-flip exploits.

```
rawIndex = weightedAverage(effectivePrice / basePrice) across all stocked items
smoothedIndex = α × rawIndex + (1 - α) × previousSmoothedIndex
```

- `α = 0.3` (EMA smoothing factor — takes ~3 hourly updates to fully converge)
- Weighted by `purchaseWeight` (popular items matter more)
- Only items currently on shelf count
- Recomputed once per in-game hour
- `priceIndex = 1.0` → neutral, `0.8` → cheap store, `1.3` → expensive store
- On load/init, `smoothedIndex` starts at `rawIndex` (no history to blend)

---

## Traffic Multiplier

Store-wide pricing posture affects customer arrival rate:

```
trafficMultiplier = (1 / priceIndex) ^ TRAFFIC_ELASTICITY
```

`TRAFFIC_ELASTICITY = 0.5` (moderate):

| Price Index | Traffic Effect |
|-------------|---------------|
| 0.70 (-30%) | +20% more customers |
| 0.85 (-15%) | +8% more customers |
| 1.00 (base) | neutral |
| 1.15 (+15%) | -7% fewer customers |
| 1.30 (+30%) | -12% fewer customers |

Integration in `TrafficManager.update()`:
```kotlin
val adjustedRate = pattern.baseCustomerRate * storeSize.trafficMultiplier * pricingTrafficMultiplier
```

---

## Basket Size Reduction (Anti-Exploit)

Without this, extreme markup is a dominant strategy: fewer customers → less staff → lower costs, while revenue per customer increases. High price indices reduce average basket size to prevent this.

When `priceIndex > 1.0`:
```
basketMultiplier = (1 / priceIndex) ^ BASKET_ELASTICITY
adjustedBasketSize = floor(baseBasketSize × basketMultiplier).coerceAtLeast(1)
```

When `priceIndex <= 1.0`: basket size stays at base. Discounts bring more customers but don't make each customer buy more. Asymmetric by design.

`BASKET_ELASTICITY = 1.0`:

| Price Index | Basket Multiplier | Base 5 → |
|-------------|------------------|----------|
| 1.00 | 1.00 | 5 items |
| 1.10 | 0.91 | 4 items |
| 1.20 | 0.83 | 4 items |
| 1.30 | 0.77 | 3 items |
| 1.50 | 0.67 | 3 items |
| 2.00 | 0.50 | 2 items |

**Combined effect at 30% markup (priceIndex 1.3):**
- Traffic: -12% fewer customers
- Basket: -23% fewer items per customer
- Per-item revenue: +30%
- **Net revenue: ~91% of base** — slight loss, not exploitable

Sweet spot: **5-15% markup**. Enough margin boost without significant volume loss.

Integration in `TransactionEngine.generateTransaction()`:
```kotlin
val basketMultiplier = if (priceIndex > 1.0f) (1.0f / priceIndex).pow(BASKET_ELASTICITY) else 1.0f
val adjustedBasketSize = (baseBasketSize * basketMultiplier).toInt().coerceAtLeast(1)
```

---

## Fresh Handler Markdowns

Fresh handlers gain a second action: **mark down items expiring within 1 day**.

### Behavior
1. During fresh handler tick, check shelf batches where `expirationDay - currentDay <= 1` and `shelfLifeDays != null`
2. If expiring batches exist AND no markdown already applied → apply **fixed 30% markdown**
3. Each markdown **consumes one fresh handler action** (competes with stocking — gameplay tradeoff)
4. Priority: **markdown first, then stock** — clearing expiring stock is higher priority
5. Markdown auto-clears when all expiring batches for that item have expired or sold out
6. **Player receives a notification** when fresh handler marks down an item

### Why per-item (not per-batch)
`consumeShelfStock()` uses FIFO — customers always buy oldest batch first. No scenario where newer batches get purchased while expiring batches sit. Per-item markdowns are correct.

---

## Category Markup on Tier Unlock

When new tiers unlock new categories, they inherit `PricingState.defaultMarkup`. Player sets `defaultMarkup` as a store-wide baseline via a single slider; per-category overrides are offsets from that. No heuristic logic needed — new categories always get the baseline.

---

## Data Model

### New: `PricingState`

```kotlin
data class PricingState(
    val categoryMarkups: Map<ItemCategory, Int> = emptyMap(),   // percent, e.g. 10 = +10%
    val itemOverrides: Map<Int, Int> = emptyMap(),              // itemId → percent (+/-)
    val activeMarkdowns: Map<Int, Markdown> = emptyMap(),       // itemId → active markdown
    val defaultMarkup: Int = 0,                                  // store-wide baseline %, new categories inherit this
    val priceTrafficMultiplier: Float = 1.0f,                   // cached, recomputed (not serialized — recompute on load)
    val basketSizeMultiplier: Float = 1.0f,                     // cached, recomputed (not serialized — recompute on load)
    val priceHistory: List<PriceChangeEvent> = emptyList(),     // capped at MAX_PRICE_HISTORY_SIZE (200)
)

data class Markdown(
    val percentOff: Int,          // e.g. 30 = 30% off
    val reason: MarkdownReason,
    val appliedOnDay: Int,        // game day applied
    // Clearing is event-driven: cleared when last expiring batch is sold or expires.
    // For PLAYER_SALE markdowns, player clears manually via ClearItemMarkdown event.
)

enum class MarkdownReason {
    EXPIRING_SOON,   // fresh handler applied
    PLAYER_SALE,     // player-initiated promotion
}

data class PriceChangeEvent(
    val dayNumber: Int,
    val itemId: Int?,             // null = category-level change
    val category: ItemCategory?,  // null = item-level change
    val oldPercent: Int,
    val newPercent: Int,
    val source: MarkdownReason?,  // null = player markup/override
)
```

### GameState addition

```kotlin
data class GameState(
    // ... existing fields ...
    val pricingState: PricingState = PricingState(),
)
```

### TransactionLine addition

```kotlin
data class TransactionLine(
    // ... existing fields ...
    val basePrice: Money,        // original price before modifiers
    val priceModifier: Int,      // net % change applied (e.g. -30 or +10)
    val weight: Float? = null,   // lbs purchased (weighted items only, null = fixed unit)
)
```

### SoldItemEvent addition

```kotlin
data class SoldItemEvent(
    // ... existing fields ...
    val effectivePrice: Money,   // price item actually sold for
    val basePrice: Money,        // original price from ItemMetadata
    // margin contribution = effectivePrice - basePrice (computed, not stored)
)
```

### DailyMetrics addition

```kotlin
data class DailyMetrics(
    // ... existing fields ...
    val markdownsSaved: Money = Money.ZERO,      // revenue from markdown items that sold
    val markupExtraRevenue: Money = Money.ZERO,  // extra revenue from markups vs base price
    val itemsMarkedDown: Int = 0,                // count of items with active markdowns today
)
```

---

## PricingManager

Location: `domain/pricing/PricingManager.kt`

| Method | Returns | Purpose |
|--------|---------|---------|
| `resolvePrice(itemId, state)` | `ResolvedPrice` | Apply all modifiers, round to cent |
| `purchaseProbabilityMultiplier(itemId, state)` | `Float` | Price sensitivity for weighted sampling |
| `computeTrafficMultiplier(state)` | `Float` | Store-wide traffic effect from price index |
| `computeBasketMultiplier(state)` | `Float` | Basket size penalty for high markups |
| `computePriceIndex(state)` | `Float` | Raw weighted average price ratio |
| `updateSmoothedPriceIndex(state)` | `GameState` | EMA update: blends raw index into smoothed value |
| `setCategoryMarkup(state, category, percent)` | `GameState` | Player sets category markup [-50%, +100%] |
| `setDefaultMarkup(state, percent)` | `GameState` | Player sets store-wide baseline markup |
| `setItemOverride(state, itemId, percent)` | `GameState` | Player sets item override [-75%, +200%] |
| `applyExpiryMarkdown(state, itemId, currentDay)` | `GameState` | Fresh handler marks down expiring item (30%) |
| `clearMarkdown(state, itemId)` | `GameState` | Remove markdown (event-driven on last batch sold/expired, or player-initiated) |

```kotlin
data class ResolvedPrice(
    val effectivePrice: Money,
    val basePrice: Money,
    val modifierPercent: Int,   // net % change for display
)
```

---

## Integration Points

### TransactionEngine

1. `weightedSample()` — add `pricingMultipliers: Map<Int, Float>?` param alongside existing `zoneMultipliers`:
```kotlin
val baseWeight = (cache.get(id)?.purchaseWeight ?: 1.0f).coerceAtLeast(0.01f)
val zoneMultiplier = zoneMultipliers?.get(id) ?: 1.0f
val priceMultiplier = pricingMultipliers?.get(id) ?: 1.0f
id to (baseWeight * zoneMultiplier * priceMultiplier)
```

2. `generateRandomTransaction()` — resolve prices, apply basket multiplier, handle weighted items:
```kotlin
val resolved = pricingManager.resolvePrice(itemId, state)
val meta = cache?.get(itemId)

if (meta?.soldByWeight == true) {
    val weight = randomWeight(meta.category)
    val lineCents = (resolved.effectivePrice.cents.toDouble() * weight).roundToLong()
    TransactionLine(
        itemId = itemId, quantity = 1, rungQty = 0,
        unitPrice = resolved.effectivePrice, weight = weight,
        lineTotal = Money(lineCents),
        basePrice = resolved.basePrice, priceModifier = resolved.modifierPercent,
    )
} else {
    val qty = (1..3).random(random)
    TransactionLine(
        itemId = itemId, quantity = qty, rungQty = 0,
        unitPrice = resolved.effectivePrice, weight = null,
        lineTotal = resolved.effectivePrice * qty,
        basePrice = resolved.basePrice, priceModifier = resolved.modifierPercent,
    )
}
```

### GameEngine

- Instantiate `PricingManager` alongside other managers
- `clearExpiredMarkdowns()` is **event-driven**, not polled:
  - In `ringUpSingleItem()`: after `consumeShelfStock()`, if item is perishable and has active markdown, check if last expiring batch was consumed. If yes, call `clearMarkdown(state, itemId)`.
  - In `SpoilageManager`: when a batch expires, same check — if last expiring batch for that item, clear markdown.
- In fresh handler tick section: call `attemptFreshHandlerMarkdowns()` before stocking
- Recompute price index / traffic multiplier / basket multiplier using EMA (see Store Price Index section)
- Route `GameEvent`s for pricing

### Fresh Handler Tick

```kotlin
// Markdown expiring items first (higher priority than stocking)
var remainingActions = wholeFreshActions
if (remainingActions > 0) {
    val (newState, actionsUsed) = attemptFreshHandlerMarkdowns(state, currentDay, remainingActions)
    state = newState
    remainingActions -= actionsUsed
}
// Then stock with remaining actions
repeat(remainingActions) { stockRandomFreshItemFromBackroom() }

// Auto-order when fresh handlers are idle (existing check unchanged)
if (wholeFreshActions <= 0 && freshHandlers > 0) {
    attemptFreshHandlerAutoOrder()
}
```

> **Gameplay tradeoff**: Markdowns compete with stocking for fresh handler actions. If expiring items consume all actions, backroom stocking and auto-ordering are deferred. Solution for the player: hire more fresh handlers.

### TrafficManager

Read `pricingState.priceTrafficMultiplier` in `update()`:
```kotlin
val adjustedRate = pattern.baseCustomerRate * storeSize.trafficMultiplier * state.pricingState.priceTrafficMultiplier
```

### GameEvent Additions

```kotlin
sealed class GameEvent {
    data class SetDefaultMarkup(val percent: Int) : GameEvent()
    data class SetCategoryMarkup(val category: ItemCategory, val percent: Int) : GameEvent()
    data class SetItemPriceOverride(val itemId: Int, val percent: Int) : GameEvent()
    data class ClearItemMarkdown(val itemId: Int) : GameEvent()
}
```

### GameStateSerializer

Serialize/deserialize `PricingState`:
- `categoryMarkups` → JSON object `{ "GROCERY": 10, "DAIRY": 5 }`
- `itemOverrides` → JSON object `{ "1": -15, "42": 20 }`
- `activeMarkdowns` → JSON array of markdown objects
- `defaultMarkup` → int
- `priceHistory` → JSON array of price change events (capped at 200 entries, oldest dropped first)
- `priceTrafficMultiplier` / `basketSizeMultiplier` → **not serialized** — recomputed on load from current pricing state

### GameUiState

- Expose `PricingState` to UI
- Expose `resolvedPrices: Map<Int, ResolvedPrice>` for display
- Expose store price reputation label ("Budget" / "Standard" / "Premium")

---

## UI Components

### Category Markup Slider (Inventory Screen or new Pricing tab)
- Per-category row with slider: -50% to +100%, 1% steps
- Show current effective price range for category
- Color coding: red for markup, green for markdown

### Item Price Override (InventoryItemCard detail or popup)
- Per-item slider: -75% to +200%, 1% steps
- Show base price, effective price, estimated demand change
- Indicator when item has active fresh handler markdown
- Weighted items show "$/lb" suffix on price labels; fixed items show "$/ea" or no suffix

### Weighted Item Transaction Display
- Transaction lines for weighted items show: "Bananas — 2.3 lbs @ $0.59/lb = $1.36"
- Transaction lines for fixed items show: "Cereal ×2 @ $4.99 = $9.98"
- Inventory cards for weighted items show price as "$/lb"

### Sale Tag on Marked-Down Items
- Visual sale tag badge on inventory item cards with active markdowns
- Show markdown percentage and source (fresh handler / player)

### Active Markdowns List (Store Overview or Inventory)
- List of items currently marked down
- Source indicator (fresh handler vs player)
- Option to clear markdowns

### Store Price Reputation (Store Overview)
- Display: "Budget / Standard / Premium" based on price index
- Show traffic effect and basket size effect

### Price History Chart (Metrics screen)
- Track price changes over time per item/category
- Show correlation with sales volume

### End of Day Report
- Pricing section: markdown savings, markup extra revenue
- Items that expired despite markdown (waste analysis)
- Price reputation summary

### Fresh Handler Markdown Notification
- Toast/snackbar when fresh handler marks down an item
- Show item name and markdown percentage

---

## Implementation Phases

### Phase 1: Core Data & Resolution
1. Create `PricingState` (with `defaultMarkup`), `Markdown`, `MarkdownReason`, `PriceChangeEvent`, `ResolvedPrice` data classes
2. Add `pricingState` to `GameState`
3. Add `soldByWeight` to data pipeline: `items.json` → `Item.kt` → `ItemDataLoader.kt` → `ItemMetadataCache.kt` → `ItemMetadata.kt`
4. Add `"soldByWeight": true` to the 9 weighted items in `items.json`
5. Create `PricingManager` with `resolvePrice()` (cents-based arithmetic, `roundToLong()`, sell-below-cost floor), `purchaseProbabilityMultiplier()`, `computePriceIndex()`, `updateSmoothedPriceIndex()` (EMA), `computeTrafficMultiplier()`, `computeBasketMultiplier()`
6. Update `GameStateSerializer` for `PricingState` (exclude cached multipliers — recompute on load)
7. Write unit tests for price resolution, rounding, cost-floor guard, EMA convergence, multiplier math, and weighted item line total computation

### Phase 2: Transaction & Traffic Integration
8. Update `TransactionEngine.weightedSample()` to accept pricing multipliers alongside existing `zoneMultipliers`
9. Update `TransactionEngine.generateRandomTransaction()` to use resolved prices, basket multiplier, and weight-based line totals for `soldByWeight` items
10. Add `basePrice`, `priceModifier`, and `weight` to `TransactionLine`
11. Add `effectivePrice` and `basePrice` to `SoldItemEvent`
12. Wire `PricingManager` into `GameEngine`
13. Add traffic multiplier to `TrafficManager.update()`
14. Add hourly EMA price index update in `GameEngine.tick()`

### Phase 3: Player Controls
15. Add `GameEvent`s: `SetDefaultMarkup`, `SetCategoryMarkup`, `SetItemPriceOverride`, `ClearItemMarkdown`
16. Route events through `GameEngine` → `PricingManager`
17. Update `GameUiState` with pricing data
18. Build default markup slider (store-wide baseline)
19. Build category markup UI (slider per category, 1% steps)
20. Build item override UI (per-item slider, with "Low Margin" / "At Cost" warnings)
21. Add store price reputation display

### Phase 4: Fresh Handler Markdowns
22. Add markdown-first logic in fresh handler tick (markdowns consume actions before stocking)
23. Add event-driven markdown clearing in `ringUpSingleItem()` (after consuming last expiring batch) and `SpoilageManager` (after expiring last batch)
24. Add sale tag indicator to inventory item cards
25. Add fresh handler markdown notification (toast)

### Phase 5: Metrics & History
26. Update `DailyMetrics` with pricing-specific stats
27. Add pricing section to end-of-day report dialog (including margin analysis from SoldItemEvent data)
28. Implement `PriceChangeEvent` recording (capped at 200, oldest dropped)
29. Build price history chart UI

### Phase 6: Polish & Tuning
30. New categories on tier unlock inherit `defaultMarkup` automatically
31. Tune `PRICE_ELASTICITY`, `TRAFFIC_ELASTICITY`, `BASKET_ELASTICITY`, `EMA_ALPHA`, and weight ranges through playtesting
32. Test edge cases: all items marked down, all items at cost floor, all items marked up max, empty shelf, markdown + markup stacking, weighted item at minimum/maximum weight

---

## Architecture Notes

### Why a separate PricingManager?
- Follows existing pattern: one manager per domain
- Keeps pricing logic out of TransactionEngine (which handles transaction flow, not business rules)
- PricingManager doesn't own state; it transforms `GameState.pricingState` via `.copy()`

### Why multiplicative stacking?
- Additive stacking (10% markup + 30% markdown = 20% markdown) feels unintuitive for overlapping player/system modifiers
- Multiplicative lets each modifier act independently: category markup = store-wide strategy, item markdowns = tactical clearance

### Why per-item pricing (not per-batch)?
- `consumeShelfStock()` uses FIFO — customers always buy oldest batch first
- No scenario where newer batches get purchased while expiring batches sit
- Per-batch pricing would mean different shelf units of the same item at different prices — confusing

### Tuning Constants (starting values)

| Constant | Value | Controls |
|----------|-------|----------|
| `PRICE_ELASTICITY` | 1.5 | Per-item purchase probability sensitivity |
| `TRAFFIC_ELASTICITY` | 0.5 | Store-wide customer arrival sensitivity |
| `BASKET_ELASTICITY` | 1.0 | Basket size reduction for high markups |
| `FRESH_MARKDOWN_PERCENT` | 30 | Fixed markdown applied by fresh handlers |
| `EXPIRY_THRESHOLD_DAYS` | 1 | Days before expiration to trigger markdown |
| `EMA_ALPHA` | 0.3 | Price index smoothing (0=frozen, 1=instant) |
| `MAX_PRICE_HISTORY_SIZE` | 200 | Rolling cap on PriceChangeEvent list |
| `PRODUCE_WEIGHT_MIN` | 0.5 | Minimum lbs for weighted produce items |
| `PRODUCE_WEIGHT_MAX` | 3.0 | Maximum lbs for weighted produce items |
| `MEAT_WEIGHT_MIN` | 0.75 | Minimum lbs for weighted meat items |
| `MEAT_WEIGHT_MAX` | 2.5 | Maximum lbs for weighted meat items |

---

## Constraints

- **Money precision**: All price calculations in cents (Long). Never use `Money.times(Double)` — it truncates sub-dollar values. Compute combined multiplier as `Double`, apply once to `cents`, use `roundToLong()`. See Price Resolution section for the pattern.
- **Sell-below-cost floor**: `resolvePrice()` clamps effective price at `ItemMetadata.unitCost`. Player cannot sell below cost.
- **Performance**: Price resolution is O(1) per item. Price index recomputation O(n) over stocked items — run hourly with EMA smoothing.
- **Batch rules**: Never access `shelfStock`/`backroomStock` as mutable ints. Never create `InventoryState` with integer constructor. Always `.copy()` batches.
- **Manager rules**: Never call manager methods from UI. Route through `GameEngine` events.
- **Test pattern**: Use `FakeItemDao` boilerplate. No Mockito. No trivial tests.

---

## Testing Plan

Test file: `app/src/test/java/com/example/superstoresimulator/domain/pricing/PricingManagerTest.kt`

Uses project test patterns: JUnit 4, `FakeItemDao` boilerplate, `newEngine()` helper, `setMoney()` helper. No Mockito. No trivial tests (no constructor/setter assertions).

### 1. Price Resolution (`resolvePrice`)

| Test | Setup | Assert |
|------|-------|--------|
| **No modifiers returns base price** | Item at $5.00, no markups/overrides/markdowns | effectivePrice = $5.00, modifierPercent = 0 |
| **Category markup only** | $3.00 item, 10% category markup | effectivePrice = $3.30 |
| **Item override only** | $3.00 item, 20% item override | effectivePrice = $3.60 |
| **Markdown only** | $10.00 item, 30% markdown | effectivePrice = $7.00 |
| **All three stack multiplicatively** | $3.00 item, 10% category, 20% item, 0% markdown | $3.00 × 1.10 × 1.20 = $3.96 |
| **Markup + markdown stacking** | $10.00 item, 10% category markup, 30% markdown | $10.00 × 1.10 × 0.70 = $7.70 |
| **Negative category markup (discount)** | $4.00 item, -25% category | effectivePrice = $3.00 |
| **Negative item override** | $4.00 item, -50% item override | effectivePrice = $2.00 |
| **Max item override (+200%)** | $2.00 item, +200% override | effectivePrice = $6.00 |
| **Max category markup (+100%)** | $5.00 item, +100% category | effectivePrice = $10.00 |

### 2. Sell-Below-Cost Floor

| Test | Setup | Assert |
|------|-------|--------|
| **Floor clamps at unitCost** | $5.00 item, unitCost $3.00, -50% category markup → raw = $2.50 | effectivePrice = $3.00 (clamped) |
| **Stacked modifiers hit floor** | $4.00 item, unitCost $2.50, -30% category + -50% item → raw = $1.40 | effectivePrice = $2.50 |
| **Markdown alone hits floor** | $3.00 item, unitCost $2.80, 30% markdown → raw = $2.10 | effectivePrice = $2.80 |
| **Exact cost is allowed** | $5.00 item, unitCost $5.00, 0% everything | effectivePrice = $5.00 (no floor trigger) |
| **Markup above cost not affected** | $5.00 item, unitCost $3.00, +10% markup | effectivePrice = $5.50 (no clamp) |

### 3. Cents-Based Arithmetic Precision

| Test | Setup | Assert |
|------|-------|--------|
| **Sub-dollar price with markup** | $0.69 item (bananas), 10% category markup | effectivePrice = Money(76) — $0.76, not truncated |
| **Fractional cent rounds correctly** | $0.59 item, 15% markup → 67.85 cents | effectivePrice = Money(68), not Money(67) |
| **Chained small percentages** | $1.00 item, 3% category, 7% item → 1.00 × 1.03 × 1.07 = 1.1021 | effectivePrice = Money(110) — single round at end |
| **Large price no overflow** | $199.99 item, +100% category, +200% item → $199.99 × 2.0 × 3.0 = $1199.94 | Correct Long cents, no truncation |
| **Zero price stays zero** | $0.00 item (edge), any modifier | effectivePrice = Money(0) |

### 4. Purchase Probability Multiplier

| Test | Setup | Assert |
|------|-------|--------|
| **No markup → multiplier 1.0** | effectivePrice == basePrice | purchaseProbabilityMultiplier ≈ 1.0 |
| **Markup reduces probability** | $5.00 base, 20% markup → $6.00 effective | multiplier = (5.0/6.0)^1.5 ≈ 0.76 |
| **Markdown increases probability** | $5.00 base, 20% markdown → $4.00 effective | multiplier = (5.0/4.0)^1.5 ≈ 1.40 |
| **Large markup severely penalizes** | $5.00 base, 100% markup → $10.00 effective | multiplier = (5.0/10.0)^1.5 ≈ 0.35 |
| **At-cost floor price boosts max** | $5.00 base, floor at $3.00 unitCost | multiplier = (5.0/3.0)^1.5 ≈ 2.15 |

### 5. Store Price Index (Raw + EMA)

| Test | Setup | Assert |
|------|-------|--------|
| **All items at base → index 1.0** | 3 items, no markups, all on shelf | computePriceIndex ≈ 1.0 |
| **Uniform 10% markup → index 1.1** | 3 items, all +10% category markup | computePriceIndex ≈ 1.1 |
| **Mixed markups weighted by purchaseWeight** | Item A (weight 10) at +20%, Item B (weight 1) at -50% | Index close to 1.20, not midpoint of 0.85 |
| **Items not on shelf excluded** | 2 items on shelf (+10%), 1 item empty shelf | Only shelf items contribute |
| **EMA converges from neutral** | Initial smoothedIndex = 1.0, rawIndex = 1.3, α=0.3 | After 1 update: 1.09, after 2: 1.153, after 5: ~1.26 |
| **EMA first load initializes to raw** | No prior history | smoothedIndex = rawIndex (no blending) |
| **EMA damps price-flip exploit** | Alternate rawIndex between 0.5 and 1.5 each hour | smoothedIndex never reaches extremes, stays near 1.0 |

### 6. Traffic Multiplier

| Test | Setup | Assert |
|------|-------|--------|
| **Neutral price index → multiplier 1.0** | priceIndex = 1.0 | trafficMultiplier = 1.0 |
| **Cheap store (+20% traffic)** | priceIndex = 0.70 | trafficMultiplier ≈ 1.20 |
| **Expensive store (-12%)** | priceIndex = 1.30 | trafficMultiplier ≈ 0.88 |
| **Extreme cheap** | priceIndex = 0.50 | multiplier ≈ 1.41 — verify no overflow/runaway |
| **Extreme expensive** | priceIndex = 2.0 | multiplier ≈ 0.71 — traffic drops but doesn't zero out |

### 7. Basket Size Multiplier (Anti-Exploit)

| Test | Setup | Assert |
|------|-------|--------|
| **At or below 1.0 → no reduction** | priceIndex = 1.0 | basketMultiplier = 1.0 |
| **Discount does not increase basket** | priceIndex = 0.7 | basketMultiplier = 1.0 (asymmetric: discounts don't boost basket) |
| **10% markup → ~91% basket** | priceIndex = 1.10 | adjustedBasket from base 5 = 4 |
| **30% markup → ~77% basket** | priceIndex = 1.30 | adjustedBasket from base 5 = 3 |
| **100% markup → 50% basket** | priceIndex = 2.0 | adjustedBasket from base 5 = 2 |
| **Basket floors at 1** | priceIndex = 5.0, base basket 2 | adjustedBasket = 1, not 0 |

### 8. Weighted Items (Transaction)

| Test | Setup | Assert |
|------|-------|--------|
| **Weighted item consumes 1 shelf unit** | Bananas (soldByWeight=true), weight=2.3 lbs | inventory decremented by 1, not 2.3 |
| **Line total = effectivePrice × weight** | $0.69/lb × 2.0 lbs | lineTotal = Money(138) |
| **Weight within produce range** | PRODUCE item, many random transactions | All weights in [0.5, 3.0] |
| **Weight within meat range** | MEAT item, many random transactions | All weights in [0.75, 2.5] |
| **Non-weighted item ignores weight** | Cereal (soldByWeight=false), qty=2 | lineTotal = effectivePrice × 2, weight = null |
| **Pricing modifiers apply per-lb before weight** | $4.49/lb chicken, 10% markup → $4.94/lb, weight 1.5 | lineTotal = Money(741) |
| **Cost floor applies per-lb** | $4.49/lb chicken, unitCost $2.99, -50% category → raw $2.245 | effectivePrice floored at $2.99/lb, lineTotal = $2.99 × weight |

### 9. Player Controls (State Mutations via GameEvent)

| Test | Setup | Assert |
|------|-------|--------|
| **SetCategoryMarkup persists** | Dispatch SetCategoryMarkup(GROCERY, 15) | state.pricingState.categoryMarkups[GROCERY] == 15 |
| **SetCategoryMarkup clamps range** | Dispatch SetCategoryMarkup(DAIRY, 150) | Clamped to 100 (max) |
| **SetCategoryMarkup negative clamp** | Dispatch SetCategoryMarkup(DAIRY, -80) | Clamped to -50 (min) |
| **SetItemPriceOverride persists** | Dispatch SetItemPriceOverride(42, -15) | state.pricingState.itemOverrides[42] == -15 |
| **SetItemPriceOverride clamps range** | Dispatch SetItemPriceOverride(42, 300) | Clamped to 200 (max) |
| **SetDefaultMarkup applies to new categories** | Set defaultMarkup to 10%, unlock new tier with ELECTRONICS | categoryMarkups[ELECTRONICS] == 10 |
| **ClearItemMarkdown removes active** | Apply 30% markdown to item 5, then ClearItemMarkdown(5) | activeMarkdowns[5] == null |

### 10. Fresh Handler Markdowns

| Test | Setup | Assert |
|------|-------|--------|
| **Marks down item expiring in 1 day** | Shelf batch expires on day 5, currentDay = 4, 1 fresh handler | activeMarkdowns contains item with 30% EXPIRING_SOON |
| **Skips item already marked down** | Item already has active markdown | No duplicate markdown, action not consumed |
| **Skips non-perishable** | Item with shelfLifeDays = null, expiring batch | No markdown applied |
| **Markdown consumes action** | 2 expiring items, 1 fresh handler action available | Only 1 item marked down |
| **Markdown before stocking priority** | 1 expiring item + 1 item needs stocking, 1 action | Markdown applied, stocking deferred |
| **Auto-clears on last expiring batch sold** | Markdown active, sell last expiring batch via ringUpSingleItem | activeMarkdowns cleared for that item |
| **Auto-clears on last expiring batch expired** | Markdown active, batch expires via SpoilageManager | activeMarkdowns cleared |
| **Does not clear if more expiring batches remain** | 2 expiring batches, sell 1 | Markdown stays active |

### 11. Serialization Round-Trip

| Test | Setup | Assert |
|------|-------|--------|
| **PricingState survives serialize/deserialize** | State with categoryMarkups, itemOverrides, activeMarkdowns, defaultMarkup, priceHistory | Deserialized state equals original |
| **Cached multipliers recomputed on load** | Save state with priceTrafficMultiplier = 0.5 | After load, multiplier recomputed from pricing state, not from saved value |
| **Empty PricingState deserializes as defaults** | Save game with no pricing data (pre-pricing save) | Loads as PricingState() defaults |
| **Price history capped at 200** | Add 250 PriceChangeEvents, serialize, deserialize | Only 200 remain, oldest dropped |

### 12. Combined Revenue Scenario (Integration)

| Test | Setup | Assert |
|------|-------|--------|
| **30% markup net revenue ~91%** | 3+ items, 30% markup, run N transactions | Revenue within [85%, 97%] of base (traffic -12%, basket -23%, per-item +30%) |
| **5-15% markup sweet spot** | 10% markup, run N transactions | Net revenue > 100% of base (margin gain outweighs small volume loss) |
| **Full markdown stack** | Category -50%, item -75%, 30% fresh markdown → all hit cost floor | All items sell at unitCost |
| **Mixed strategy** | Some categories marked up, some down, some items overridden | Each item's resolved price matches manual calculation |

### 13. Edge Cases

| Test | Setup | Assert |
|------|-------|--------|
| **Empty shelf → price index handles gracefully** | No items on shelf | computePriceIndex returns 1.0 (neutral default) |
| **All items at cost floor** | Every item has modifiers pushing below cost | All resolve to unitCost, priceIndex reflects floor prices |
| **Item with no category markup entry** | Category not in categoryMarkups map | Uses 0% (default), not crash |
| **Item with no item override entry** | itemId not in itemOverrides map | Uses 0% (default), not crash |
| **Markdown on item with no shelf stock** | Apply markdown via event, item has 0 shelf units | Markdown stored but has no effect until restocked |
| **Concurrent markup + markdown display** | Item has +20% category markup AND 30% EXPIRING_SOON markdown | modifierPercent reflects net combined effect |

### Phase Mapping

| Phase | Tests to Write |
|-------|---------------|
| Phase 1 | Sections 1–6, 13 (core math, no engine wiring needed) |
| Phase 2 | Sections 7–8, 12 (transaction integration, weighted items) |
| Phase 3 | Section 9 (player controls via GameEvent) |
| Phase 4 | Section 10 (fresh handler markdowns) |
| Phase 5 | Section 11 (serialization) |
| Phase 6 | Remaining edge cases, tuning constant validation |

---

## Gameplay Strategies Enabled

| Strategy | How | Risk |
|----------|-----|------|
| **Loss leader** | Mark down popular items to boost traffic, markup niche items | Margin erosion if too many loss leaders |
| **Premium store** | Small markup (5-10%) across the board | Traffic + basket penalty at high markup |
| **Clearance push** | Fresh handler markdowns + player markdowns on expiring stock | Reduced revenue per unit |
| **Category specialization** | Different markup per department based on competition sensitivity | Complexity, must track each category |
| **Base pricing** | Keep everything at 0% | Miss margin opportunities, safe default |
