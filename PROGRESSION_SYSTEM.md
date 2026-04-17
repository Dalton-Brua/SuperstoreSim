# Progression System — Design & Implementation Status

> **Last updated**: April 11, 2026
> **Status**: Core revenue-tier system implemented. Store Size system and secondary unlocks are future work.

---

## Overview

The progression system is built on **cumulative revenue**. As the player completes transactions the running total grows, gating access to new item categories and store features through four tiers.

Originally this document specified **two independent axes** — Revenue Tiers (automatic) and Store Size (manual purchase). During implementation the design evolved:

1. **Revenue Tiers** became a **manual purchase** (not automatic). The player must meet a revenue gate *and* pay an unlock cost.
2. **Store Size** has **not been implemented**. It remains a planned second axis (see [Future Work](#future-work--store-size-system)).

The items and staff currently available to a player are determined solely by their revenue tier and per-item tier tags.

---

## Chosen Metric: Cumulative Revenue ✅

**Why not current balance?**
- Current balance penalizes spending (hiring staff, buying inventory) — exactly what the player should be doing.
- It creates a frustrating "save up to unlock, then can't afford to stock" loop.

**Why Cumulative Revenue?**
- Rewards throughput: the more transactions completed, the faster progression.
- Never goes backward — spending money on staff/inventory doesn't block progression.
- Naturally scales with game activity (more cashiers + stockers → more revenue → faster progression).

**Implementation**: `GameState.totalRevenue: Money` — distinct from `money` (spendable balance). Only incremented inside `TransactionEngine.completeTransaction()`.

---

## Implemented Systems ✅

### 1. Revenue Tiers (`ItemUnlockTier`)

Four tiers gate item visibility and store features. Each tier is a **manual purchase**: the player must meet the revenue threshold *and* pay a one-time cash cost.

```
Revenue gate met  →  ProgressionUIState.availableTier becomes non-null
Player dispatches GameEvent.UnlockNextTier
    → gameEngine.unlockNextTier()
    → guard: totalRevenue ≥ nextTier.unlockAmount AND money ≥ nextTier.unlockCost
    → state.currentTier = nextTier; money -= unlockCost
    → GameStateChange.TierUnlocked emitted
```

> ⚠️ `checkAndAdvanceTier()` **does not exist** — it was removed when the design shifted to manual purchases. Never try to call it.

**Tier table** (all monetary values in cents internally):

| Tier | Revenue Gate | Unlock Cost | Newly Unlocked Categories | Narrative |
|------|-------------|-------------|--------------------------|-----------|
| `TIER_1` | $0 (start) | $0 | GROCERY, SNACKS, DRINKS | "Your store is open!" |
| `TIER_2` | $5,000 | $1,000 | + DAIRY | "Install Dairy Coolers" — also unlocks Bulk Ordering |
| `TIER_3` | $20,000 | $4,000 | + FROZEN, BAKERY, PRODUCE | "Add Fresh & Frozen Sections" |
| `TIER_GM` | $100,000 | $20,000 | + MEAT, HEALTH, HOUSEHOLD, PHARMACY, ELECTRONICS | "Open All Departments" |

**Unlock cost rationale**: Each tier's `unlockCost` is ≈ 1/5 of the revenue gate, creating a meaningful cash-management decision. The player must earn the threshold *and* keep enough cash on hand to invest.

**Category ordering rationale**:
- **TIER_1** — Shelf-stable, low-margin staples only. No fresh products, no specialty.
- **TIER_2** — DAIRY added once the player has a baseline store. Still low-complexity; no fresh produce yet.
- **TIER_3** — Fresh and frozen sections unlock together. PRODUCE demands active restocking, so it's gated behind the player having revenue to hire stockers.
- **TIER_GM** — High-margin and specialty departments arrive at full store maturity.

**Files**: `domain/items/ItemUnlockTier.kt` | `domain/GameEngine.kt` (`unlockNextTier`) | `ui/GameEvent.kt` (`UnlockNextTier`)

---

### 2. Per-Item Tier Gating

Items carry an **individual** `tier` field (stored as a string in the Room entity, resolved to `ItemUnlockTier` in `ItemMetadata`). This means items within the **same category** can require different tiers.

| Tier | Items Added | Breakdown |
|------|-------------|-----------|
| TIER_1 | 38 items | 23 GROCERY + 8 SNACKS + 7 DRINKS |
| TIER_2 | 49 items | 13 GROCERY + 9 SNACKS + 5 DRINKS + 10 DAIRY |
| TIER_3 | 30 items | 4 GROCERY + 10 FROZEN + 8 BAKERY + 12 PRODUCE |
| TIER_GM | 29 items | 8 MEAT + 6 HEALTH + 6 HOUSEHOLD + 5 PHARMACY + 4 ELECTRONICS |

**Total catalog**: 136 items (items 001–136).

**Filtering rule** (used everywhere):
```kotlin
itemMetadataCache.get(itemId)?.tier?.unlockAmount <= state.currentTier.unlockAmount
```

This filter is applied in:
- `MemoizedInventoryMapper.map()` — inventory screen visibility
- `TransactionEngine.startNewTransaction()` — random transaction item pools
- `TransactionEngine.generateRandomTransaction()` — autonomous customer baskets
- `GameEngine.placeBulkOrder()` — bulk order eligibility

> ❌ Never filter by `tier.unlockedSections` alone — this misses per-item tier gates within a category.
> The `unlockedSections` set on `ItemUnlockTier` is used by `UnlocksScreen` for **UI display only**.

**Files**: `domain/items/Item.kt` (`tier: String`) | `domain/items/ItemMetadata.kt` (`tier: ItemUnlockTier`)

---

### 3. GameState Fields

```kotlin
data class GameState(
    // ...existing fields...
    val totalRevenue: Money = Money.ZERO,                    // cumulative, never decreases
    val currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1, // advanced via unlockNextTier()
)
```

- `totalRevenue` is updated **only** in `TransactionEngine.completeTransaction()`.
- `currentTier` is updated **only** in `GameEngine.unlockNextTier()`.
- Neither field is affected by spending operations (buying inventory, hiring staff).

---

### 4. GameStateChange & UI State

**Change emission**:
```kotlin
data class TierUnlocked(
    val newTier: ItemUnlockTier,
    val previousTier: ItemUnlockTier
) : GameStateChange()
```

**ProgressionUIState** (in `GameUiState.progression`):
```kotlin
data class ProgressionUIState(
    val currentTier: ItemUnlockTier,
    val totalRevenue: Money,
    val nextTier: ItemUnlockTier?,            // null at top tier
    val revenueToNextTier: Money?,            // null at top tier
    val tierProgressFraction: Float,          // 0.0–1.0 within current tier band
    val justUnlockedTier: ItemUnlockTier?,    // non-null for ONE frame after unlock
    val availableTier: ItemUnlockTier?,       // non-null when revenue gate met but not yet paid
)
```

**Events**:
- `GameEvent.UnlockNextTier` → routes to `gameEngine.unlockNextTier()`
- `GameEvent.DismissTierUnlock` → pure UI: clears `justUnlockedTier`

**IncrementalUiStateBuilder** handles `TierUnlocked` with targeted `ProgressionUIState` update — no full UI rebuild.

**Files**: `domain/GameStateChange.kt` | `ui/state/GameUiState.kt` | `ui/state/builders/IncrementalUiStateBuilder.kt`

---

### 5. Backroom Cap (Interim)

A per-item cap on backroom stock prevents unlimited stockpiling.

```kotlin
data class StoreConfig(
    // ...
    val backroomCapPerItem: Int = 50,  // interim; will be StoreSize-driven later
)
```

**Enforcement**:
- `buyItemToBackroom()` — refuses if `backroomStock + casePack > cap`
- `buyItemCasePacks()` — clamps delivery to full case packs that fit within cap; charges only for delivered packs
- `placeBulkOrder()` — excludes items with no room; clamps per-item delivery; discount based on *actual* delivered cases

**UI**: `InventoryItemUI.backroomFull: Boolean` — disables the "Order" button when `backroomStock + casePack > cap`.

> When the Store Size system is implemented, `StoreConfig.backroomCapPerItem` will be superseded by `StoreSize.maxBackroomPerItem`.

**Files**: `domain/time/StoreConfig.kt` | `domain/GameEngine.kt` | `ui/state/mappers/MemoizedInventoryMapper.kt`

---

### 6. Bulk Ordering (Tier 2+)

Allows the player to restock an entire category (or all accessible categories) in one action with tiered volume discounts. Unlocked at TIER_2 and above.

**Event**: `GameEvent.BulkOrder(maxTotalQuantity, casePacksPerItem, categoryFilter)` → `gameEngine.placeBulkOrder()`

**Eligibility filter** (all must pass):
1. Item tier ≤ `currentTier` (per-item tier gate)
2. Category matches `categoryFilter` (or `null` = all categories)
3. `shelfStock + backroomStock ≤ maxTotalQuantity`
4. At least one full case pack fits within the backroom cap

**Volume discount tiers** (applied to total actual cases delivered, post-cap clamping):

| Total Case Packs | Discount |
|-----------------|---------|
| < 20 | 0% |
| ≥ 20 | 10% |
| ≥ 50 | 15% |
| ≥ 100 | 25% |

**Guard**: Does nothing if player cannot afford the discounted total.

**UI**: `BulkOrderDialog` — slider for max stock threshold, slider for case packs per item, category chip filter. Accessed via a button in `InventoryScreen` (only visible when `currentTier >= TIER_2`).

**Files**: `domain/GameEngine.kt` (`placeBulkOrder`) | `ui/dialogs/BulkOrderDialog.kt` | `ui/screens/inventory/InventoryScreen.kt`

---

### 7. UnlocksScreen UI

Full progression UI integrated into `StaffAndUnlocksScreen` as an "Unlocks" tab.

**Features**:
- Total revenue display
- Progress bar toward next tier with percentage
- "Revenue gate met" indicator when `availableTier` is non-null
- Per-tier cards showing narrative title/description, newly unlocked categories, and status badges (CURRENT / AVAILABLE / locked)
- Unlock button with cost display (disabled when player can't afford it)
- "Full Store Unlocked!" congratulations card at TIER_GM

**Files**: `ui/screens/staff/UnlocksScreen.kt`

---

### 8. Tests

| File | Focus |
|------|-------|
| `ProgressionTest.kt` | Revenue accumulation, `fromTotalEarned`, transaction revenue growth |
| `ItemUnlockTierTest.kt` | Tier thresholds, `nextTier`, `requiredTierForSection`, category completeness |
| `TierUnlockTest.kt` | `unlockNextTier()` guards, cost deduction, change emission, multi-tier paths |
| `BulkOrderTest.kt` | Discount boundaries, category/stock/tier filters, cap clamping, metrics |

---

## What Changed from the Original Design 🔀

This section documents intentional deviations made during implementation.

### Tier Advancement: Manual Purchase, Not Automatic

| Aspect | Original Design | Actual Implementation |
|--------|----------------|----------------------|
| Advancement trigger | `checkAndAdvanceTier()` called automatically after every transaction | Player dispatches `GameEvent.UnlockNextTier` explicitly |
| Cost | Free (automatic on revenue threshold) | `unlockCost` deducted from cash (1/5 of revenue gate) |
| UI signal | `TierUnlocked` emitted passively | `availableTier` shows when gate is met; player clicks "Unlock" button |
| Player agency | None — tiers advance without input | Full — player decides when to spend cash on upgrades |

**Rationale**: Manual purchase creates a meaningful decision point. The player must balance spending cash on tier unlocks vs. inventory/staff. The `availableTier` field lets the UI prompt the player without forcing advancement.

### Revenue Thresholds: 10× Higher

| Tier | Original Design | Actual |
|------|----------------|--------|
| TIER_1 | $0 | $0 |
| TIER_2 | $500 | $5,000 |
| TIER_3 | $2,000 | $20,000 |
| TIER_GM | $10,000 | $100,000 |

**Rationale**: The original values were placeholders ("⚠️ Thresholds are placeholder values. Tune them during playtesting."). The 10× increase creates real pacing — TIER_2 takes several game days to reach, TIER_GM is a late-game goal.

### Per-Item Tier Gating: More Granular than Category-Level

| Aspect | Original Design | Actual |
|--------|----------------|--------|
| Filtering granularity | Category-level (`unlockedSections`) | Per-item (`item.tier`) |
| Items per tier | Determined by category alone | 38/49/30/29 items across mixed categories |
| Filter rule | `meta.category in tier.unlockedSections` | `meta.tier.unlockAmount <= currentTier.unlockAmount` |

**Rationale**: Per-item gating allows more interesting progression within a single category. Premium grocery items can unlock at TIER_2 while basic ones are available from TIER_1. The `unlockedSections` set still exists on `ItemUnlockTier` and is used by `UnlocksScreen` to display which *sections* unlock at each tier.

### Bulk Ordering: Implemented Beyond Original Sketch

The original design described "Bulk Ordering Discount (Tier 2)" as a single-line item with a `bulkDiscountRate: Double` to be applied in `buyItemCasePacks()`. The actual implementation is a full standalone system:
- Dedicated `placeBulkOrder()` engine method
- 4-tier volume discount schedule
- Category and stock-threshold filtering
- Backroom-cap clamping with charge-for-delivered-only semantics
- `BulkOrderDialog` with interactive sliders
- Full test suite (`BulkOrderTest.kt`)

---

## Future Work: Store Size System

> This was the planned "second axis" of progression. It is **not implemented** and all references to `StoreSize` in the codebase are limited to the comment in `StoreConfig.kt`.

### Design Intent

Store Size is a **manual, paid upgrade** — the player actively spends money to grow their store. It provides a second ceiling that the revenue tier system cannot bypass on its own.

### Proposed Store Sizes

| Size | Upgrade Cost | Max Revenue Tier | Backroom/Item | Max Staff | New Sections |
|------|-------------|------------------|---------------|-----------|--------------|
| MOM_AND_POP | — (start)   | TIER_2 | 20 | 3 | GROCERY, SNACKS, DRINKS, DAIRY |
| SMALL_GROCERY | $1,000      | TIER_3 | 50 | 8 | + FROZEN, BAKERY, PRODUCE |
| GROCERY_STORE | $20,000     | TIER_GM | 100 | 20 | + MEAT, HEALTH, HOUSEHOLD |
| SUPERSTORE | $200,000    | TIER_GM | 300 | 50 | + PHARMACY |
| SUPERCENTER | $1,000,000  | TIER_GM | unlimited | unlimited | + ELECTRONICS |

> ⚠️ Costs are placeholder values. Tune them during playtesting.

### How Size and Tier Would Interact

The items visible in the inventory screen would be the **intersection** of both constraints:

```
effectiveSections = currentTier.unlockedSections ∩ storeSize.unlockedCategories
```

This means upgrading the store could immediately unlock items the player's revenue tier already earned but couldn't access yet — giving store upgrades a satisfying "instant payoff" moment.

### Caps Enforced by Store Size

- **Backroom cap** — `storeSize.maxBackroomPerItem` replaces `StoreConfig.backroomCapPerItem`
- **Staff cap** — `hireEntity()` refuses when `hiredEntityRegistry.totalCount() >= storeSize.maxStaff`
- **Tier cap** — `unlockNextTier()` clamps to `storeSize.maxTier`. Revenue earned beyond the cap is "banked" — the player advances immediately when the store is upgraded.

### Implementation Checklist (when ready to build)

1. Create `domain/StoreSize.kt` — enum with 5 sizes, caps, and `unlockedCategories`
2. Add `storeSize: StoreSize` to `GameState`
3. Add `GameEvent.UpgradeStore`, `GameEngine.upgradeStore()`
4. Add `GameStateChange.StoreSizeUpgraded` and `GameEvent.DismissStoreSizeUpgrade`
5. Replace `StoreConfig.backroomCapPerItem` with `storeSize.maxBackroomPerItem`
6. Add staff cap guard in `hireEntity()`
7. Add tier cap in `unlockNextTier()` (clamp to `storeSize.maxTier`)
8. Extend `MemoizedInventoryMapper.map()` to accept `storeSize` and filter by intersection
9. Extend `ProgressionUIState` with store size fields (`storeSize`, `nextStoreSize`, `storeSizeUpgradeCost`, `canAffordUpgrade`, `tierCapReached`)
10. Handle `StoreSizeUpgraded` in `IncrementalUiStateBuilder`
11. Build Store Upgrade UI (button + confirmation dialog)
12. Write tests

---

## Future Work: Staff Gating by Tier

Currently all staff types (CASHIER, STOCKER, FAST_CASHIER, FAST_STOCKER, CUSTOMER_SERVICE_REP) are available at all tiers. The original design gated staff by tier.

### Proposed Staff Gating

| Tier | Unlocked Staff |
|------|---------------|
| TIER_1 | `cashier` only |
| TIER_2 | + `stocker` |
| TIER_3 | + `fast_cashier`, `fast_stocker` |
| TIER_GM | + `customer_service_rep` |

### Implementation Checklist (when ready to build)

1. Add `unlockedStaffKeys: Set<String>` field to `ItemUnlockTier`
2. Guard `GameEngine.hireEntity()`: reject if `def.key !in state.currentTier.unlockedStaffKeys`
3. Add `unlockedEntityTypes: List<EntityType>` to `StaffUIState` (filtered by tier)
4. Show lock indicators in hire UI for staff types not yet unlocked (show what's coming, don't hide)
5. Write tests

> **Design note**: Staff gating interacts well with per-item tier gating. At TIER_1 the player can only hire cashiers — they must manually stock shelves. Once TIER_2 unlocks stockers the store becomes more autonomous, which is necessary because DAIRY requires active restocking.

---

## Future Work: Custom Store Hours (Tier 2)

The player can set their own open/close times once TIER_2 is reached. Before this, the store runs on fixed hours (6 AM – 9 PM per `StoreConfig` defaults).

### Design Rules

- `openTimeMinutes` range: 0 (midnight) – 1380 (11 PM), step of 30 min
- `closeTimeMinutes` must be at least 60 minutes after `openTimeMinutes`
- `closeTimeMinutes` max: 1440 (24-hour operation — store never closes within a day)
- Validation lives in `GameEngine.setStoreHours()`, never in the UI

### Implementation Checklist (when ready to build)

1. Add `customHoursUnlocked: Boolean` field to `ItemUnlockTier` (false for TIER_1, true for TIER_2+)
2. Add `GameEvent.SetStoreHours(openTimeMinutes, closeTimeMinutes)`
3. Add `GameEngine.setStoreHours()` with validation (guard on `currentTier.customHoursUnlocked`)
4. Add `customHoursUnlocked`, `openTimeMinutes`, `closeTimeMinutes` to `TimeUIState`
5. Build hours-picker UI component
6. Write tests

> **Design note**: Custom hours at Tier 2 is intentional — the player now has a stocker to handle overnight shelf-stocking, which makes extended/custom hours meaningful rather than cosmetic.

---

## Future Work: Secondary Progression Systems

These layer on top of the tier system and can be unlocked independently or tied to tiers. None are implemented. They are listed here for future reference and may be adjusted based on gameplay testing.

### 1. Price Optimization (Tier 3)

Let the player set per-item markup. Add `itemPriceOverrides: Map<Int, Money>` to `GameState`. `TransactionEngine` checks this map before defaulting to the item's base price. Gives a meaningful spend-to-earn lever.

### 2. Marketing Campaigns (Tier 3)

Increase customer traffic frequency or basket size. Add `marketingBoostMultiplier: Float` to `GameState` (default 1.0). Spending money on a campaign (new `GameEvent.RunMarketingCampaign`) sets this field for a duration tracked via game minutes. Decays back to 1.0 after N in-game hours.

### 3. Loyalty Program (Tier GM)

Reduce the refund chance. `TransactionEngine` constructor already accepts `refundChance: Double = 0.10`. Store a `loyaltyProgramActive: Boolean` in `GameState`; when true, inject a lower `refundChance`. Requires making `TransactionEngine` re-instantiated or parameterized per-call when the flag changes.

### 4. Self-Checkout Machines (Tier GM)

A purchasable upgrade that auto-rings items without counting against the cashier headcount. Implement as a new `EntityDef.SELF_CHECKOUT` with a high cost. `GameEngine.tick()` checks `countByEntity(EntityDef.SELF_CHECKOUT)` separately from regular cashiers.

### 5. Daily Bonus / Prestige

At the end of each game day, award a `dailyBonus: Money` based on transactions completed that day. The daily metrics infrastructure already tracks `transactionsCompleted` per day — this system would simply read from the `DailyMetricsAccumulator` during `rollOverDay()` and add a bonus to `money`. Creates a natural optimization loop: maximize each day to maximize the bonus.

---

## Existing Infrastructure (Do Not Rebuild)

`ItemUnlockTier.kt` provides the tier backbone:

```kotlin
enum class ItemUnlockTier(
    val unlockAmount: Long,          // cents threshold
    val unlockedSections: Set<ItemCategory>,
    val displayName: String,
    val unlockCost: Money,           // one-time purchase price
    val narrativeTitle: String,
    val narrativeDescription: String,
)
```

Key companion methods:
- `fromTotalEarned(totalEarned: Long)` — returns highest tier that `totalEarned` qualifies for
- `nextTier(current)` — returns next tier or null at cap
- `ordered` — list of all tiers sorted by `unlockAmount`

Top-level helper:
- `requiredTierForSection(section: ItemCategory)` — maps category → required tier (used by `UnlocksScreen` for display)

---

## Performance Considerations

| Concern | Solution |
|---------|----------|
| Tier check on every transaction | `fromTotalEarned()` scans 4 entries — O(1) |
| Inventory filter every tick | `MemoizedInventoryMapper` caches results; invalidates only on tier or backroom-cap change |
| `TierUnlocked` causes full UI rebuild | `IncrementalUiStateBuilder` handles it as a targeted `ProgressionUIState` update |
| Bulk order mutates many items at once | Single `state.copy()` with pre-built inventory map; one `MoneyChanged` emission |

---

## Extensibility Notes

- **New tiers**: Add a new enum entry to `ItemUnlockTier`. Update `requiredTierForSection()` if new categories are added. Populate items with the new tier string.
- **New items**: Add to the items JSON. Set the `tier` field to control when they become visible.
- **New staff types**: Add to `EntityDef`. When staff gating is implemented, add the key to the appropriate tier's `unlockedStaffKeys`.
- **New progression axes** (e.g., reputation, customer satisfaction): Add to `GameState`, compute in the appropriate engine, gate in `GameEngine`. Follow the same `totalRevenue`/`currentTier` pattern.
- **Threshold tuning**: Tier thresholds and unlock costs live in `ItemUnlockTier` — single-file changes.

---

## Files Summary

### Implemented Files

| File | Role |
|------|------|
| `domain/items/ItemUnlockTier.kt` | Tier enum with thresholds, costs, categories, narratives |
| `domain/items/Item.kt` | Room entity with `tier: String` field |
| `domain/items/ItemMetadata.kt` | Cached metadata with `tier: ItemUnlockTier` |
| `domain/GameStateData.kt` | `totalRevenue`, `currentTier` fields in `GameState` |
| `domain/TransactionEngine.kt` | Increments `totalRevenue` in `completeTransaction()` |
| `domain/GameEngine.kt` | `unlockNextTier()`, `placeBulkOrder()`, backroom cap enforcement |
| `domain/GameStateChange.kt` | `TierUnlocked(newTier, previousTier)` |
| `domain/time/StoreConfig.kt` | `backroomCapPerItem = 50` (interim) |
| `ui/GameEvent.kt` | `UnlockNextTier`, `DismissTierUnlock`, `BulkOrder` |
| `ui/state/GameUiState.kt` | `ProgressionUIState`, `InventoryItemUI.backroomFull` |
| `ui/state/mappers/MemoizedInventoryMapper.kt` | Per-item tier filtering, backroom-full flag |
| `ui/state/builders/IncrementalUiStateBuilder.kt` | Handles `TierUnlocked` change |
| `ui/viewmodels/GameViewModel.kt` | `buildProgressionUiState()`, event routing |
| `ui/screens/staff/UnlocksScreen.kt` | Full tier progression UI |
| `ui/dialogs/BulkOrderDialog.kt` | Bulk order UI |
| `ui/screens/inventory/InventoryScreen.kt` | Bulk order button (gated to TIER_2+) |

### Test Files

| File | Focus |
|------|-------|
| `ProgressionTest.kt` | Revenue accumulation, `fromTotalEarned`, transaction revenue growth |
| `ItemUnlockTierTest.kt` | Tier thresholds, `nextTier`, `requiredTierForSection`, category completeness |
| `TierUnlockTest.kt` | `unlockNextTier()` guards, cost deduction, change emission, multi-tier paths |
| `BulkOrderTest.kt` | Discount boundaries, category/stock/tier filters, cap clamping, metrics |

### Files to Create (Future Work)

| File | Purpose |
|------|---------|
| `domain/StoreSize.kt` | Store Size enum |
| `ui/dialogs/StoreUpgradeDialog.kt` | Store upgrade confirmation UI |
| `ui/components/StoreHoursPicker.kt` | Custom store hours UI |
