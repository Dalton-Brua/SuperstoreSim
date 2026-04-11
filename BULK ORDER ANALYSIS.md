# Bulk Order Feature — Deep Analysis
*Analyzed: April 10, 2026 | Codebase state at time of analysis*

---

## Table of Contents
1. [Feature Overview](#1-feature-overview)
2. [Implementation Walkthrough](#2-implementation-walkthrough)
3. [Confirmed Bugs](#3-confirmed-bugs)
4. [Potential Exploits & Abuses](#4-potential-exploits--abuses)
5. [Design Fit Assessment](#5-design-fit-assessment)
6. [Missing Test Coverage](#6-missing-test-coverage)
7. [Summary & Recommendations](#7-summary--recommendations)

---

## 1. Feature Overview

Bulk Order allows the player to stock-up entire categories (or all unlocked categories) in one action, with volume discounts based on how many total case packs are ordered. It is gated behind **TIER_2** and surfaced via a floating dialog in `InventoryScreen`.

**Current discount thresholds** (`BulkOrderDialog.kt` + `GameEngine.placeBulkOrder()`):

| Total Case Packs | Discount |
|------------------|---------|
| < 20 | 0% |
| ≥ 20 | 10% |
| ≥ 50 | 15% |
| ≥ 100 | 25% |

**Eligibility filter (engine-side)**: Item's tier ≤ `currentTier` AND optional category match AND `shelfStock + backroomStock ≤ maxTotalQuantity`.

**Key files**:
- `domain/GameEngine.kt` — `placeBulkOrder()` (lines ~185–235)
- `ui/dialogs/BulkOrderDialog.kt` — full UI + preview computation
- `ui/GameEvent.kt` — `BulkOrder(maxTotalQuantity, casePacksPerItem, categoryFilter?)`
- `app/src/test/.../BulkOrderTest.kt` — existing test coverage

---

## 2. Implementation Walkthrough

### Data Flow
```
Player opens BulkOrderDialog
  ↓ allItems = state.items (tier-filtered InventoryItemUI list from MemoizedInventoryMapper)
  ↓ Dialog previews matchingItems, totalCases, baseCost, finalCost (all live via Compose)
Player confirms
  ↓ onBulkOrder(maxQty, casePacks, category) → MainActivity
  ↓ viewModel.onEvent(GameEvent.BulkOrder(...))
  ↓ gameEngine.placeBulkOrder(...) [re-derives matching items from live state.inventory]
  ↓ Deducts finalCost from state.money, adds itemsToAdd to each item's backroomStock
  ↓ Emits GameStateChange.MoneyChanged
  ↓ shouldRebuildUiState() → true (inventory changed) → full toUiState() rebuild
```

### Engine-Side Eligibility Check
```kotlin
val matchingEntries = state.inventory.filter { (itemId, inv) ->
    val meta = itemMetadataCache.get(itemId) ?: return@filter false
    val totalQty = inv.shelfStock + inv.backroomStock
    val tierOk = meta.tier.unlockAmount <= state.currentTier.unlockAmount
    val categoryOk = categoryFilter == null || meta.category == categoryFilter
    val qtyOk = totalQty <= maxTotalQuantity
    tierOk && categoryOk && qtyOk
}
```

### UI-Side Preview Computation
```kotlin
val matchingItems = remember(allItems, maxTotalQuantity, selectedCategory) {
    allItems.filter { item ->
        val totalQty = item.shelfStock + item.backroomStock
        val categoryOk = selectedCategory == null || item.category == selectedCategory
        totalQty <= maxTotalQuantity && categoryOk
    }
}
```

> **Note**: The UI does NOT re-apply the per-item tier filter here because `allItems` is already tier-filtered by `MemoizedInventoryMapper`. The engine DOES apply tier filtering directly. These should produce the same result but via different code paths.

---

## 3. Confirmed Bugs

### Bug 1 — Silent Failure on Rejected Order (No User Feedback)
**Severity: Medium**

If the engine rejects the order (e.g., the player's stock levels changed between opening the dialog and confirming), the dialog simply calls `onConfirm()`, immediately closes (`showBulkOrderDialog = false`), and the `placeBulkOrder()` guard returns silently:

```kotlin
if (state.money < finalCost) return   // Silent rejection
if (matchingEntries.isEmpty()) return  // Silent rejection
```

The player sees the button click, the dialog disappears, and nothing else happens. There is **no toast, snackbar, or error state** indicating why the order failed. This is especially problematic if the engine computed a different `finalCost` than the UI preview showed, making the order unaffordable.

**Relevant code**: `BulkOrderDialog.kt` line ~485, `GameEngine.kt` placeBulkOrder guards.

---

### Bug 2 — `placeBulkOrder()` Only Emits `MoneyChanged`, Not `InventoryUpdated`
**Severity: Low (functional, architectural inconsistency)**

After executing a bulk order that modifies backroom stock for potentially 136 items, the engine only emits:

```kotlin
_changes.value = GameStateChange.MoneyChanged(state.money)
```

No `GameStateChange.InventoryUpdated` events are emitted for any of the modified items. Compare this to `buyItemToBackroom()` which emits both `MoneyChanged` and `InventoryUpdated`.

The `IncrementalUiStateBuilder` receives a `MoneyChanged` signal and updates the money display. The inventory UI is only updated through the `shouldRebuildUiState()` fallback path (which does trigger correctly because `inventory != oldInventory`). The system works, but the incremental pipeline receives an incomplete signal. Any future consumer of the `changes` StateFlow that subscribes without the full rebuild fallback would see stale inventory data.

---

### Bug 3 — `Money.times(Double)` Operator Loses Precision (Unused in Critical Path but a Latent Bug)
**Severity: Low (does not affect bulk order calculations currently)**

```kotlin
operator fun times(multiplier: Double) = Money(cents/100 * (multiplier*100).toLong())
```

`cents / 100` performs integer division first, discarding the sub-dollar cents. Example:
- `Money(999) * 0.10` → `(999/100=9) * (0.10*100=10).toLong()` = `Money(90)` = $0.90
- Correct answer: `999 * 0.10 = 99.9` → `Money(99)` = $0.99

The bulk order discount calculations in both `BulkOrderDialog.kt` and `GameEngine.kt` use `Money((baseCost.cents * (1.0 - discountFraction)).toLong())` directly — bypassing this operator and avoiding the precision loss. However, the operator exists in `GameStateData.kt` and could cause subtle bugs if future code uses it for discount or cost calculations.

---

### Bug 4 — Cases-Per-Item Slider Cap Doesn't Match Domain Boundary
**Severity: Very Low (UI-domain inconsistency)**

The dialog slider enforces `valueRange = 1f..20f`, capping the user at 20 cases per item. The engine's `placeBulkOrder()` has no upper bound on `casePacksPerItem` — any positive integer is accepted. The domain contract is undocumented. If the event is ever dispatched programmatically or via a future test/admin path with a higher value, the behavior is technically valid but unintended.

---

### Bug 5 — Dialog Category Filter Always Resets to "All" Regardless of InventoryScreen Selection
**Severity: Low (UX inconsistency)**

`BulkOrderDialog`'s internal state initializes `selectedCategory = null` regardless of what category the player currently has selected in `InventoryScreen`. If a player is browsing "Dairy" and taps Bulk Order, the dialog defaults to showing "All" categories. This causes a small but disorienting UX inconsistency: the context that prompted the bulk order is lost.

The fix would be to pass the current `state.inventory.selectedCategory` as the initial value into the dialog.

---

### Bug 6 — `InventoryScreen` Category Bar Shows All Categories Including Locked Ones
**Severity: Low (UX, not specific to bulk order but interacts with it)**

```kotlin
InventoryCategoryBar(
    categories = ItemCategory.entries,  // ALL categories, including MEAT, PHARMACY, etc.
    ...
)
```

At TIER_1 or TIER_2, the category bar includes locked departments (FROZEN, MEAT, PHARMACY, etc.). Tapping them shows an empty list with no explanation. This also means the `selectedCategory` the player chooses could be a locked category, and if that is passed as `categoryFilter` to the engine, `placeBulkOrder` will correctly find zero matching items (tier-gated out) — but the UI just shows "No items match," which is misleading.

---

## 4. Potential Exploits & Abuses

### Exploit 1 — Skip Day Stacking (The "Set-and-Forget" Loop)
**Impact: Eliminates all active play**

The most powerful interaction in the game. Once TIER_2 is purchased and some cashiers/stockers are hired:

1. Open Bulk Order → Set threshold to 100, cases per item to 20 → Confirm
2. Skip Day
3. Dismiss end-of-day report → Collect revenue
4. Repeat

With enough cashiers and stockers, the player never needs to manually ring up an item or stock a shelf again. The game's entire active-play loop is reduced to two button presses per day. Because items never expire and there are no storage costs, the pre-stocked backroom from step 1 always guarantees zero out-of-stock events during the simulated day.

This fully trivializes the TIER_2+ experience.

---

### Exploit 2 — Maximum Discount Is Trivially Achievable at Every Tier
**Impact: Discounts lose strategic meaning**

The 25% discount requires 100 total case packs. The slider maxes at 20 cases per item.

| Tier | Items Available | Cases to hit 100 | Cases/item needed |
|------|----------------|-----------------|-------------------|
| TIER_1 | 38 items | 100 | 3 cases/item (≈ 3×) |
| TIER_2 | 87 items | 100 | 2 cases/item |
| TIER_3 | 117 items | 100 | 1 case/item |
| TIER_GM | 136 items | 100 | **Less than 1 case/item** — impossible to miss |

**At TIER_3 and above, every bulk order triggers the maximum 25% discount automatically**, even when ordering the minimum of 1 case per item. The tiered discount system becomes meaningless in the late game.

---

### Exploit 3 — Unbounded Backroom Stockpiling
**Impact: Eliminates supply chain management**

There is no maximum backroom stock cap, no per-item or total storage limit, and no holding cost. A player with sufficient cash can bulk-order 20 cases of every item repeatedly until the backroom holds thousands of units. This stock never depletes from spoilage or storage fees.

Practical example at TIER_GM: 136 items × 20 cases/item × avg. 6 units/case = **16,320 units** added per bulk order. At that volume, a store could theoretically run for weeks of game-time without reordering anything.

---

### Exploit 4 — Perishables Never Expire (TIER_3+)
**Impact: Removes the design challenge of fresh departments**

TIER_3 unlocks PRODUCE, BAKERY, and FROZEN items — conceptually perishable. The narrative description for TIER_3 specifically references "fresh fruits and vegetables" and "in-store baked items." However, no spoilage mechanic exists. Bulk-ordering 20 cases of croissants or strawberries is identical to ordering canned beans; the items sit in the backroom indefinitely.

This is particularly notable because the unlock cost for TIER_3 is $4,000 and the revenue gate is $20,000 — a significant investment that carries zero operational complexity once purchased.

---

### Exploit 5 — The "High Threshold" Trap for Other Players
**Impact: Misuse of the `maxTotalQuantity` parameter**

The stock threshold is labeled "Stock threshold: ≤ N units" and defaults to 20. Setting it to 100 includes items that already have significant stock. A player can use this to dump maximum case packs on items that are already well-stocked, artificially inflating `totalCases` to reach higher discount tiers without actually having a genuine restocking need.

Example: 10 items each with 95 units in stock, threshold set to 100, 10 cases/item = 100 total cases = 25% discount. The player has spent money on items they don't need yet, but got a major discount on the order. Since there's no storage cost, this is risk-free if the player has the cash.

---

### Exploit 6 — Consecutive Orders with No Cooldown
**Impact: Rapid-fire discounted purchasing**

There is no per-order cooldown or delivery delay. The player can:
1. Open dialog → confirm → dialog closes
2. Immediately reopen dialog → confirm again
3. Repeat indefinitely in the same game tick

Each order is processed synchronously in `onEvent()`. Because the inventory threshold filter is checked at engine-time, subsequent orders will quickly find zero matching items (stock exceeds threshold), but the player can manipulate the threshold up between orders to keep buying.

---

## 5. Design Fit Assessment

### Verdict: Feature is Premature and Structurally Undermines Core Gameplay

**The central problem**: Bulk Order, Skip Day, and automation (cashiers/stockers) are three features that, individually, each add value, but together they stack to eliminate almost all player decision-making. The game's loop depends on tension between supply and demand — stockouts, reordering timing, cash management. Bulk Order removes the supply-side entirely.

---

### Specific Design Concerns

**5.1 — Unlock Timing**

TIER_2 is unlocked at $5,000 total revenue, which happens early in a playthrough. At TIER_2, the player immediately has access to 87 items. They can:
- Place one bulk order at 87 items × 2 cases = 174 total cases → **25% discount on first use**
- Spend $1,000 on the tier upgrade and immediately "earn it back" with the discount on their next bulk order

The feature is at its most powerful the moment it unlocks, not gradually over time.

**5.2 — No Consequential Decision-Making**

Real wholesale/restocking decisions involve tradeoffs:
- **Tie up capital vs. risk stockouts**: Bulk ordering locks in cash that could be used for staff upgrades or tier advancement
- **Storage limits vs. ordering frequency**: Real storage has cost and space constraints
- **Perishable risk vs. freshness**: Fresh items expire; overbought stock is a loss

None of these tradeoffs exist. Bulk ordering is always correct (assuming you have the cash) because there is zero downside to having excess stock.

**5.3 — Interaction with the Metrics System**

The `currentDayMetrics.itemsOrdered` accumulator is updated correctly. But because there are no OOS events when you've bulk-stocked everything, the `OutOfStockReportDialog` and `lostRevenue` metrics become essentially meaningless once the player adopts a daily bulk-order routine. The metrics that were designed to surface inefficiency never fire.

**5.4 — Missing "Order Arrives Tomorrow" Pattern**

A delivery delay mechanic (e.g., items arrive at start of next game day) would:
- Force the player to forecast demand rather than react immediately
- Create genuine tension between ordering timing and stock levels
- Make Skip Day mutually exclusive with "I just placed a big order" (since skipping would forfeit the delivery window)

Without it, bulk order is essentially a "press a button to win supply chain" feature.

**5.5 — Cash Balance as Sole Constraint Is Weak**

The only guard on `placeBulkOrder` is `state.money < finalCost → return`. Cash is plentiful in TIER_2+ because cashier automation earns money continuously. By the time a player reaches TIER_2, they likely have $2,000–$5,000 on hand, and a full bulk order costs much less at 25% off. The feature effectively has no meaningful constraint for a player who has set up basic automation.

---

### Where the Feature Does Work Well

- **Category filter chip**: Clean UI for targeted restocking by department. Feels natural for a real store manager.
- **Preview panel**: Showing matching items, total cases, and discount before confirming is good UX.
- **Tier gating**: Correctly uses per-item tier gates (`meta.tier.unlockAmount <= state.currentTier.unlockAmount`), not just category-level gates. This is architecturally correct.
- **Metrics integration**: `itemsOrdered` is properly updated, keeping the daily report accurate.
- **UI hint system**: "Add N more case(s) to unlock 15% off" is a nice progressive disclosure of the discount tiers.

---

## 6. Missing Test Coverage

The existing `BulkOrderTest.kt` covers the happy path and basic guards well. The following scenarios are untested:

| Scenario | Risk |
|----------|------|
| Order succeeds with partial discount tier (stock changes reduce `totalCases` below threshold mid-tick) | Engine charges different amount than UI showed |
| Two consecutive bulk orders with threshold manipulation | Rapid-fire abuse |
| `casePacksPerItem` > 20 (above UI cap) dispatched programmatically | Undocumented domain behavior |
| `itemsOrdered` metric accumulates correctly across multiple bulk orders in one day | Regression risk |
| Order rejected because stock changed between open and confirm | Silent failure — no test verifies user-visible feedback |
| `MoneyChanged` change event emitted vs. absence of `InventoryUpdated` | IncrementalUiStateBuilder receiving incomplete signal |
| Bulk order at TIER_GM with all 136 items (tests use max 3 items) | Scale / performance at real catalog size |

---

## 7. Summary & Recommendations

### Critical Issues (Fix Before Shipping)

1. **Add failure feedback** — When `placeBulkOrder()` returns early, emit a `GameStateChange` subtype (e.g., `BulkOrderFailed(reason: String)`) or have the dialog observe a nullable result so the player receives a visible error.

2. **Emit `InventoryUpdated` changes** — For each item modified in `placeBulkOrder()`, emit `GameStateChange.InventoryUpdated(itemId, updatedInv)` so the incremental UI builder is fully informed and the `changes` StateFlow is self-consistent.

### Design Recommendations (Balance)

3. **Introduce a delivery delay** — Items ordered via bulk order arrive at the start of the **next game day**, not instantly. This creates genuine supply chain tension and prevents the "bulk order then skip day" loop from being lossless. Items ordered individually (single case packs) could still arrive instantly, preserving the current single-order UX.

4. **Add per-item spoilage for TIER_3+ perishables** — PRODUCE/BAKERY/FROZEN items older than N game days in the backroom should lose a percentage of value or be removed. This makes TIER_3 feel meaningfully different from shelf-stable staples.

5. **Cap backroom stock per item** — Introduce `InventoryState.backroomCapacity` (e.g., 50–100 units). Items above capacity can't be received. This creates a meaningful inventory management decision: do you stock the shelves before ordering more, or do you let items pile up?

6. **Tighten the discount thresholds for late-game tiers** — At TIER_3+ (117+ items), 1 case/item already exceeds 100 total cases. Consider scaling the threshold with the number of unlocked items, or adding a TIER_GM-only discount tier (e.g., ≥ 200 cases → 30%) so the discount remains meaningful at full catalog size.

7. **Pass the InventoryScreen's current category filter into the dialog** — Minor UX polish: initialize the dialog's `selectedCategory` to match whatever category the player was viewing when they tapped "Bulk Order."

### Code Quality

8. **Guard `casePacksPerItem` in the domain layer** — Add `require(casePacksPerItem in 1..20)` or document the domain contract explicitly so future callers don't send out-of-range values.

9. **Fix `Money.times(Double)` precision loss** — Rewrite as `Money((cents * multiplier).toLong())` to eliminate the integer-division precision loss before it creates an observable bug elsewhere.

10. **Filter locked categories from `InventoryScreen`'s category bar** — Pass `currentTier` to `InventoryCategoryBar` and only render categories whose `requiredTierForSection()` ≤ `currentTier`.

---

*End of analysis*

