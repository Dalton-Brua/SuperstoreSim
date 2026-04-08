# Progression System — Design & Implementation Guide

## Overview

This document specifies the full design and step-by-step implementation plan for a store progression system built on **two independent axes**:

1. **Revenue Tiers** (`ItemUnlockTier`) — automatic. Driven by cumulative revenue, unlocks item categories and staff types as the player earns more.
2. **Store Size** (`StoreSize`) — manual. The player spends money to physically upgrade the store (Mom-and-Pop → Small Grocery → Grocery Store → Superstore → Supercenter), which raises hard caps on backroom space, staff headcount, and the maximum revenue tier reachable.

These axes interact via intersection: the items and staff available to a player at any moment are limited by **both** what their revenue tier has unlocked **and** what their store size permits. A player who has earned TIER_3 revenue but hasn't upgraded past MOM_AND_POP is still capped at TIER_2 items and 3 staff members.

Store hours are **not** automatically set by either axis — instead, reaching Tier 2 unlocks the **ability for the player to set their own hours**, giving them direct agency over the store schedule.

---

## Chosen Metric: Cumulative Revenue

**Why not current balance?**
- Current balance penalizes spending (hiring staff, buying inventory) — exactly what the player should be doing
- It creates a frustrating "save up to unlock, then can't afford to stock" loop

**Why Cumulative Revenue?**
- Rewards throughput: the more transactions completed, the faster progression
- Never goes backward — spending money on staff/inventory doesn't block progression
- Naturally scales with game activity (more cashiers + stockers = more revenue = faster progression)
- `ItemUnlockTier.fromTotalEarned(totalEarned: Long)` is **already implemented** in `ItemUnlockTier.kt` and accepts this exact value

**Implementation note**: `GameState` needs a new `totalRevenue: Money` field. This is distinct from `money` (the current spendable balance) — it only ever increases. `TransactionEngine.completeTransaction()` adds to both.

---

## Existing Infrastructure (Do Not Rebuild)

`ItemUnlockTier.kt` already provides the tier backbone:

```kotlin
// Already exists — just needs extensions for staff/hours:
enum class ItemUnlockTier(
    val unlockAmount: Long,          // cents threshold (e.g., 50000L = $500)
    val unlockedSections: Set<ItemCategory>,
    val displayName: String
)

// Already exists:
ItemUnlockTier.fromTotalEarned(totalEarned: Long)  // resolves tier from revenue
ItemUnlockTier.nextTier(current)                    // returns next tier or null
requiredTierForSection(section: ItemCategory)       // maps category → required tier
```

Current thresholds (in cents):
| Tier | Threshold | Unlocked Categories |
|------|-----------|---------------------|
| TIER_1 | $0 | GROCERY, PRODUCE, SNACKS, DRINKS, HEALTH |
| TIER_2 | $10 | + DAIRY |
| TIER_3 | $50 | + FROZEN, BAKERY |
| TIER_GM | $200 | ALL sections |

> ⚠️ **Thresholds are placeholder values.** Tune them during playtesting.

> ⚠️ **Categories in existing code differ from this design.** The existing `ItemUnlockTier` entries place PRODUCE in TIER_1 and HEALTH in TIER_1. Step 1 changes both. The top-level `requiredTierForSection()` function in `ItemUnlockTier.kt` must also be updated to match — it currently maps `PRODUCE → TIER_1` and `HEALTH → TIER_GM`. After Step 1, the correct mapping is `PRODUCE → TIER_3` and `HEALTH → TIER_GM` (unchanged for HEALTH, but PRODUCE moves).

---

## Full Progression Design

### Tier Expansions (extend `ItemUnlockTier`)

Add two new fields to the enum: `unlockedStaffKeys: Set<String>` and `customHoursUnlocked: Boolean`.

| Tier | Threshold | New Sections | New Staff | Custom Hours |
|------|-----------|--------------|-----------|--------------|
| TIER_1 | $0 | GROCERY, SNACKS, DRINKS | `cashier` only | ❌ Fixed 6 AM – 9 PM |
| TIER_2 | $500 | + DAIRY | + `stocker` | ✅ Unlocked |
| TIER_3 | $2,000 | + FROZEN, BAKERY, PRODUCE | + `fast_cashier`, `fast_stocker` | ✅ Unlocked |
| TIER_GM | $10,000 | + MEAT, HEALTH, HOUSEHOLD, PHARMACY, ELECTRONICS | + `customer_service_rep` | ✅ Unlocked |

**Section ordering rationale**:
- **TIER_1** — shelf-stable, low-margin staples only. No fresh products, no health/specialty.
- **TIER_2** — DAIRY added once the player has a baseline store. Still low-complexity; no fresh produce yet.
- **TIER_3** — fresh and frozen sections unlock together. PRODUCE is a fresh category that demands active restocking, so it's gated behind having a stocker hired. FROZEN and BAKERY follow the same logic.
- **TIER_GM** — HEALTH (higher-margin supplements/vitamins), MEAT (fresh, high-labour), HOUSEHOLD and specialty departments arrive at full store maturity.

Staff gating means the hire UI shows lock indicators for staff types the player hasn't unlocked yet, rather than hiding them entirely. This lets players see what's coming.

Custom hours unlocking at Tier 2 is intentional: the player now has a stocker to handle overnight shelf-stocking, which makes extended/custom hours meaningful rather than just cosmetic.

---

## Store Size System

Store size is a **manual, paid upgrade** — the player actively spends money to grow their store. It provides a second ceiling that the revenue tier system cannot bypass on its own.

### Store Sizes

| Size | Upgrade Cost | Max Revenue Tier | Backroom/Item | Max Staff | Exclusive Sections |
|------|-------------|------------------|---------------|-----------|--------------------|
| MOM_AND_POP | — (start) | TIER_2 | 20 | 3 | GROCERY, SNACKS, DRINKS, DAIRY |
| SMALL_GROCERY | $5,000 | TIER_3 | 50 | 8 | + FROZEN, BAKERY, PRODUCE |
| GROCERY_STORE | $25,000 | TIER_GM | 100 | 20 | + MEAT, HEALTH, HOUSEHOLD |
| SUPERSTORE | $100,000 | TIER_GM | 300 | 50 | + PHARMACY |
| SUPERCENTER | $500,000 | TIER_GM | unlimited | unlimited | + ELECTRONICS |

> ⚠️ **Costs are placeholder values.** Tune them during playtesting.

**Design rationale by size**:
- **MOM_AND_POP** — a corner store. One or two cashiers, a tiny stockroom, basic dry goods and dairy only. No fresh departments.
- **SMALL_GROCERY** — a neighbourhood grocery. Stockers hired, a walk-in freezer and bakery now make sense. Produce added.
- **GROCERY_STORE** — a full-service grocery. Meat counter, health aisle, household goods. Full staff complement.
- **SUPERSTORE** — a large-format store. Pharmacy counter added; room for many more staff; large warehouse-style backroom.
- **SUPERCENTER** — maximum scale. Electronics department, unlimited backroom, unlimited staff.

### How Size and Tier Interact

The items visible in the inventory screen are the **intersection** of both constraints:

```
effectiveSections = currentTier.unlockedSections ∩ storeSize.unlockedCategories
```

Examples:
- Player at TIER_3 revenue + MOM_AND_POP size → only TIER_2 items show (GROCERY, SNACKS, DRINKS, DAIRY), because MOM_AND_POP caps at TIER_2
- Player at TIER_2 revenue + GROCERY_STORE size → only TIER_2 items show (GROCERY, SNACKS, DRINKS, DAIRY), because revenue hasn't unlocked more yet
- Player at TIER_GM revenue + GROCERY_STORE size → MEAT, HEALTH, HOUSEHOLD show; PHARMACY and ELECTRONICS do not (size cap)

This means **upgrading the store can immediately unlock items** the player's revenue tier already earned but couldn't access yet — giving store upgrades a satisfying "instant payoff" moment.

### Caps Enforced by Store Size

**Backroom cap** — `GameEngine.buyItemToBackroom()` and `buyItemCasePacks()` refuse to exceed `storeSize.maxBackroomPerItem` units per item. The UI should show a "backroom full" state when at the cap.

**Staff cap** — `GameEngine.hireEntity()` refuses to hire when `hiredEntityRegistry.totalCount() >= storeSize.maxStaff` (null = unlimited). The hire UI should show a "no positions available — upgrade store" message when at the cap.

**Tier cap** — `checkAndAdvanceTier()` clamps the resolved tier to `storeSize.maxTier`. Revenue earned beyond the cap is "banked" — the player immediately advances to the capped tier as soon as they upgrade the store.

### `StoreSize` Enum (`domain/StoreSize.kt`)

New file. Lives in `domain/` alongside `GameStateData.kt`.

```kotlin
enum class StoreSize(
    val displayName: String,
    val upgradeCost: Money,
    val maxTier: ItemUnlockTier,         // Highest tier reachable at this store size
    val maxBackroomPerItem: Int?,        // null = unlimited
    val maxStaff: Int?,                  // null = unlimited
    val unlockedCategories: Set<ItemCategory>,
) {
    MOM_AND_POP(
        displayName = "Mom-and-Pop",
        upgradeCost = Money.ZERO,
        maxTier = ItemUnlockTier.TIER_2,
        maxBackroomPerItem = 20,
        maxStaff = 3,
        unlockedCategories = setOf(
            ItemCategory.GROCERY, ItemCategory.SNACKS,
            ItemCategory.DRINKS, ItemCategory.DAIRY
        )
    ),
    SMALL_GROCERY(
        displayName = "Small Grocery",
        upgradeCost = Money.fromDollars(5_000.0),
        maxTier = ItemUnlockTier.TIER_3,
        maxBackroomPerItem = 50,
        maxStaff = 8,
        unlockedCategories = setOf(
            ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS,
            ItemCategory.DAIRY,
            ItemCategory.FROZEN, ItemCategory.BAKERY, ItemCategory.PRODUCE
        )
    ),
    GROCERY_STORE(
        displayName = "Grocery Store",
        upgradeCost = Money.fromDollars(25_000.0),
        maxTier = ItemUnlockTier.TIER_GM,
        maxBackroomPerItem = 100,
        maxStaff = 20,
        unlockedCategories = setOf(
            ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS,
            ItemCategory.DAIRY, ItemCategory.FROZEN, ItemCategory.BAKERY, ItemCategory.PRODUCE,
            ItemCategory.MEAT, ItemCategory.HEALTH, ItemCategory.HOUSEHOLD
        )
    ),
    SUPERSTORE(
        displayName = "Superstore",
        upgradeCost = Money.fromDollars(100_000.0),
        maxTier = ItemUnlockTier.TIER_GM,
        maxBackroomPerItem = 300,
        maxStaff = 50,
        unlockedCategories = setOf(
            ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS,
            ItemCategory.DAIRY, ItemCategory.FROZEN, ItemCategory.BAKERY, ItemCategory.PRODUCE,
            ItemCategory.MEAT, ItemCategory.HEALTH, ItemCategory.HOUSEHOLD,
            ItemCategory.PHARMACY
        )
    ),
    SUPERCENTER(
        displayName = "Supercenter",
        upgradeCost = Money.fromDollars(500_000.0),
        maxTier = ItemUnlockTier.TIER_GM,
        maxBackroomPerItem = null,       // unlimited
        maxStaff = null,                 // unlimited
        unlockedCategories = ItemCategory.entries.toSet()
    );

    companion object {
        fun nextSize(current: StoreSize): StoreSize? {
            val idx = entries.indexOf(current)
            return if (idx >= 0 && idx < entries.size - 1) entries[idx + 1] else null
        }
    }
}
```

---

## Additional Progression Systems

These layer on top of the tier system and can be unlocked independently or tied to tiers:

### 1. Custom Store Hours (Tier 2)
Unlocks at TIER_2. Before this point the store runs on fixed hours (6 AM – 9 PM, matching `StoreConfig` defaults). After unlocking, the player can set any open/close times they choose via a new settings UI.

**Design rules**:
- `openTimeMinutes` range: 0 (midnight) – 1380 (11 PM), step of 30 min
- `closeTimeMinutes` must be at least 60 minutes after `openTimeMinutes`
- `closeTimeMinutes` max: 1440 (represented as midnight the following day, i.e., 24-hour operation)
- If `closeTimeMinutes == 1440`, the store is considered 24-hour — `StoreState` never transitions to `CLOSED` within a single day
- Validation lives in `GameEngine.setStoreHours()`, never in the UI

**State**: `customStoreHoursUnlocked` is derived from `state.currentTier.customHoursUnlocked` — no separate boolean needed in `GameState`. Expose it through `TimeUIState.customHoursUnlocked` for the UI.

**Event flow**:
```
Player picks hours in UI
    ↓ GameEvent.SetStoreHours(openTimeMinutes, closeTimeMinutes)
    ↓ GameViewModel.onEvent()
    ↓ gameEngine.setStoreHours(open, close)   // validates + mutates storeConfig
    ↓ state.copy(storeConfig = storeConfig.copy(openTimeMinutes, closeTimeMinutes))
    ↓ GameStateChange.StoreStateChanged emitted (existing change type)
UI TimeUIState reflects new hours
```

### 2. Bulk Ordering Discount (Tier 2)
Lower `unitCost` multiplier per item when buying multiple case packs at once. Already partially supported by `GameEngine.buyItemCasePacks(itemId, numCasePacks)`. Add a `bulkDiscountRate: Double` to `StoreConfig` that `buyItemCasePacks` applies when `numCasePacks >= threshold`.

### 3. Price Optimization (Tier 3)
Let the player set per-item markup. Add an `itemPriceOverrides: Map<Int, Money>` to `GameState`. `TransactionEngine` checks this map before defaulting to the item's base price. Unlocking this at Tier 3 gives a meaningful spend-to-earn lever.

### 4. Marketing Campaigns (Tier 3)
Increase the maximum transaction size or frequency. Add `marketingBoostMultiplier: Float` to `GameState` (default 1.0). Spending money on a campaign (new `GameEvent.RunMarketingCampaign`) sets this field for a duration tracked via game minutes. Decays back to 1.0 after N in-game hours.

### 5. Loyalty Program (Tier GM)
Reduce the refund chance. `TransactionEngine` constructor already accepts `refundChance: Double = 0.10`. Store a `loyaltyProgramActive: Boolean` in `GameState`; when true, inject a lower `refundChance` into `TransactionEngine`. This requires making `TransactionEngine` re-instantiated (or parameterized per-call) when the flag changes.

### 6. Self-Checkout Machines (Tier GM)
A purchasable upgrade (`EntityDef`) that auto-rings items without counting against the cashier headcount formula. Implement as a new `EntityDef.SELF_CHECKOUT` with a high cost. `GameEngine.tick()` checks `countByEntity(EntityDef.SELF_CHECKOUT)` separately from regular cashiers.

### 7. Daily Bonus / Prestige
At the end of each game day (`StoreState` transitions to `CLOSED`), award a `dailyBonus: Money` based on transactions completed that day. Store `dailyTransactionCount: Int` in `GameState`, reset on `StoreState.OPEN`. This creates a natural loop: optimize each day to maximize the bonus.

---

## Implementation Plan

Follow the [Feature Addition Checklist](AGENTS.md) section by section.

### Step 1 — Extend `ItemUnlockTier` (`domain/items/ItemUnlockTier.kt`)

Add two fields to the enum. Keep backward compatibility — all existing code using only `unlockedSections` still compiles.

```kotlin
enum class ItemUnlockTier(
    val unlockAmount: Long,
    val unlockedSections: Set<ItemCategory>,
    val displayName: String,
    val unlockedStaffKeys: Set<String>,  // NEW
    val customHoursUnlocked: Boolean,    // NEW — player can set their own open/close times
) {
    TIER_1(
        unlockAmount = 0L,
        // Shelf-stable, low-margin staples only — no fresh products, no specialty
        unlockedSections = setOf(ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS),
        displayName = "Basics",
        unlockedStaffKeys = setOf("cashier"),
        customHoursUnlocked = false      // Fixed 6 AM – 9 PM until Tier 2
    ),
    TIER_2(
        unlockAmount = 50_000L,          // $500 in cents
        // Add DAIRY — still low-complexity, no fresh produce yet
        unlockedSections = setOf(
            ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS,
            ItemCategory.DAIRY
        ),
        displayName = "Dairy",
        unlockedStaffKeys = setOf("cashier", "stocker"),
        customHoursUnlocked = true
    ),
    TIER_3(
        unlockAmount = 200_000L,         // $2,000 in cents
        // Fresh and frozen unlock together — player now has stockers to manage them
        unlockedSections = setOf(
            ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS,
            ItemCategory.DAIRY,
            ItemCategory.FROZEN, ItemCategory.BAKERY, ItemCategory.PRODUCE
        ),
        displayName = "Fresh & Frozen",
        unlockedStaffKeys = setOf("cashier", "stocker", "fast_cashier", "fast_stocker"),
        customHoursUnlocked = true
    ),
    TIER_GM(
        unlockAmount = 1_000_000L,       // $10,000 in cents
        // Full store: high-margin and specialty departments
        unlockedSections = ItemCategory.entries.toSet(),
        displayName = "Full Store",
        unlockedStaffKeys = EntityDef.allEntities.map { it.key }.toSet(),
        customHoursUnlocked = true
    );
    // companion object stays unchanged
}
```

### Step 1b — Create `StoreSize` (`domain/StoreSize.kt`)

New file. Copy the `StoreSize` enum definition from the [Store Size System](#store-size-system) section above exactly as written. No other files need to import it yet — that happens in Steps 2 and 5b.

### Step 2 — Update `GameState` (`domain/GameStateData.kt`)

Add `totalRevenue`, `currentTier`, and `storeSize`. The tier is derived from `totalRevenue` but cached in state so the UI can observe tier changes directly.

```kotlin
data class GameState(
    // ...existing fields...
    val totalRevenue: Money = Money.ZERO,                    // NEW — cumulative, never decreases
    val currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1, // NEW — cached current tier
    val storeSize: StoreSize = StoreSize.MOM_AND_POP,        // NEW — current store size
)
```

### Step 3 — Update `TransactionEngine` (`domain/TransactionEngine.kt`)

In `completeTransaction()`, add `totalRevenue` to the state copy:

```kotlin
// Inside completeTransaction(), in the var newState = prev.copy(...) block:
var newState = prev.copy(
    totalTransactionsCompleted = prev.totalTransactionsCompleted + 1,
    money = prev.money + historyEntry.totalEarned,
    totalRevenue = prev.totalRevenue + historyEntry.totalEarned,  // NEW
    // ...rest unchanged
)
```

### Step 4 — Add new `GameStateChange` entries (`domain/GameStateChange.kt`)

```kotlin
sealed class GameStateChange {
    // ...existing entries...
    data class TierUnlocked(
        val newTier: ItemUnlockTier,
        val previousTier: ItemUnlockTier
    ) : GameStateChange()

    data class StoreSizeUpgraded(
        val newSize: StoreSize,
        val previousSize: StoreSize
    ) : GameStateChange()
}
```

### Step 5 — Add tier-check logic to `GameEngine` (`domain/GameEngine.kt`)

Call this after every transaction completion. Hook it into `ringUpItem()` — specifically at the point where a transaction completes (which `TransactionEngine` handles internally, so check after `txEngine.ringUpSingleItem()` returns).

```kotlin
// New private method in GameEngine:
private fun checkAndAdvanceTier() {
    val earnedTier = ItemUnlockTier.fromTotalEarned(state.totalRevenue.cents)

    // Cap advancement by store size — revenue beyond the cap is banked until the store is upgraded
    val resolved = if (earnedTier.unlockAmount <= state.storeSize.maxTier.unlockAmount)
        earnedTier
    else
        state.storeSize.maxTier

    if (resolved == state.currentTier) return

    val previousTier = state.currentTier
    // NOTE: storeConfig hours are NOT changed here — the player controls their own hours once unlocked.
    state = state.copy(currentTier = resolved)
    _changes.value = GameStateChange.TierUnlocked(resolved, previousTier)
}

// Call from ringUpItem(itemId: Int) — add after the txEngine call:
fun ringUpItem(itemId: Int) {
    state = txEngine.ringUpSingleItem(state, itemId)
    checkAndAdvanceTier()  // NEW
}
```

### Step 5a — Add `SetStoreHours` to `GameEngine` and `GameEvent` (`domain/GameEngine.kt`, `ui/GameEvent.kt`)

Add the event:

```kotlin
// In GameEvent.kt:
data class SetStoreHours(val openTimeMinutes: Int, val closeTimeMinutes: Int) : GameEvent
```

Add the engine method with full validation:

```kotlin
// In GameEngine.kt:
fun setStoreHours(openTimeMinutes: Int, closeTimeMinutes: Int) {
    // Guard: custom hours must be unlocked
    if (!state.currentTier.customHoursUnlocked) return

    // Validate ranges
    val validOpen = openTimeMinutes.coerceIn(0, 1380)          // midnight – 11 PM
    val validClose = closeTimeMinutes.coerceIn(0, 1440)        // up to midnight next day (24-hr)
    if (validClose - validOpen < 60) return                     // must be open at least 1 hour

    state = state.copy(
        storeConfig = state.storeConfig.copy(
            openTimeMinutes = validOpen,
            closeTimeMinutes = validClose
        )
    )
    _changes.value = GameStateChange.StoreStateChanged(state.storeState)  // reuse existing change type
}
```

Wire it in `GameViewModel.onEvent()`:

```kotlin
is GameEvent.SetStoreHours -> gameEngine.setStoreHours(event.openTimeMinutes, event.closeTimeMinutes)
```

### Step 5b — Add `UpgradeStore` event and `upgradeStore()` + backroom cap (`domain/GameEngine.kt`, `ui/GameEvent.kt`)

Add the event:

```kotlin
// In GameEvent.kt:
data object UpgradeStore : GameEvent
```

Add the engine method. After a successful upgrade, call `checkAndAdvanceTier()` immediately — the player's banked revenue may push the tier forward as soon as the size cap rises.

```kotlin
// In GameEngine.kt:
fun upgradeStore() {
    val nextSize = StoreSize.nextSize(state.storeSize) ?: return   // already at max
    if (state.money < nextSize.upgradeCost) return

    val previousSize = state.storeSize
    state = state.copy(
        storeSize = nextSize,
        money = state.money - nextSize.upgradeCost
    )
    _changes.value = GameStateChange.StoreSizeUpgraded(nextSize, previousSize)

    // Immediately re-evaluate tier — banked revenue may unlock a new tier now
    checkAndAdvanceTier()
}
```

Also enforce the backroom cap in `buyItemToBackroom()` and `buyItemCasePacks()`:

```kotlin
// In GameEngine.buyItemToBackroom():
fun buyItemToBackroom(itemId: Int) {
    val dyn = state.inventory[itemId] ?: return
    val dbItem = itemMetadataCache.getItem(itemId) ?: return

    val maxBackroom = state.storeSize.maxBackroomPerItem
    if (maxBackroom != null && dyn.backroomStock >= maxBackroom) return  // NEW — cap enforced

    val casePackCost = dbItem.getCasePackCostAsMoney()
    if (state.money < casePackCost) return

    val itemsInCasePack = dbItem.casePack
    // NEW — clamp so we don't overshoot the cap mid-case
    val spaceAvailable = if (maxBackroom != null) maxBackroom - dyn.backroomStock else itemsInCasePack
    val actualItemsToAdd = minOf(itemsInCasePack, spaceAvailable)
    if (actualItemsToAdd <= 0) return

    val updated = dyn.copy(backroomStock = dyn.backroomStock + actualItemsToAdd)
    state = state.copy(
        inventory = state.inventory + (itemId to updated),
        money = state.money - casePackCost
    )
    // ...existing change emissions
}
```

Wire `UpgradeStore` in `GameViewModel.onEvent()`:

```kotlin
GameEvent.UpgradeStore -> gameEngine.upgradeStore()
```

### Step 6 — Gate staff in `GameUiState` / `GameViewModel` (`ui/state/GameUiState.kt`, `ui/viewmodels/GameViewModel.kt`)

Add `unlockedEntityTypes` to `StaffUIState`:

```kotlin
data class StaffUIState(
    val registry: HiredEntityRegistry,
    val selectedType: EntityType = EntityType.NONE,
    val unlockedEntityTypes: List<EntityType> = EntityType.allEntityTypes, // NEW — filtered by tier
)
```

In `GameViewModel.toUiState()`, filter `EntityType.allEntityTypes` by the current tier's `unlockedStaffKeys`:

```kotlin
staff = StaffUIState(
    registry = domain.hiredEntityRegistry,
    selectedType = oldUi?.staff?.selectedType ?: EntityType.NONE,
    unlockedEntityTypes = EntityType.allEntityTypes.filter { entityType ->
        entityType.entities.any { def -> def.key in domain.currentTier.unlockedStaffKeys }
    }
),
```

`GameEngine.hireEntity()` should guard against hiring locked staff **and** against exceeding the store's staff cap:

```kotlin
fun hireEntity(def: EntityDef, type: EntityType) {
    if (def.key !in state.currentTier.unlockedStaffKeys) return  // tier lock

    // Store size staff cap (null = unlimited)
    val maxStaff = state.storeSize.maxStaff
    if (maxStaff != null && state.hiredEntityRegistry.totalCount() >= maxStaff) return  // NEW

    // ...rest unchanged
}
```

### Step 7 — Filter inventory by effective sections in `MemoizedInventoryMapper` (`ui/state/mappers/MemoizedInventoryMapper.kt`)

Pass both tier and store size into `map()`. The effective sections are the **intersection** of what both axes permit:

```kotlin
// Change signature:
fun map(inventory: Map<Int, InventoryState>, tier: ItemUnlockTier, storeSize: StoreSize): InventoryUIState {
    // Items must be unlocked by BOTH revenue tier AND store size
    val effectiveSections = tier.unlockedSections.intersect(storeSize.unlockedCategories)

    val items = inventory.entries
        .mapNotNull { (itemId, invState) ->
            val meta = cache.get(itemId) ?: return@mapNotNull null
            if (meta.category !in effectiveSections) return@mapNotNull null
            InventoryItemUI(/* ...map fields... */)
        }
    return InventoryUIState(items = items)
}
```

Update `GameViewModel.toUiState()` to pass both:
```kotlin
inventory = inventoryMapper.map(domain.inventory, domain.currentTier, domain.storeSize).copy(
    selectedCategory = oldUi?.inventory?.selectedCategory,
    focusedItemId = oldUi?.inventory?.focusedItemId,
),
```

### Step 8 — Add `ProgressionUIState` and extend `TimeUIState` (`ui/state/GameUiState.kt`)

```kotlin
data class ProgressionUIState(
    // Revenue tier progress
    val currentTier: ItemUnlockTier,
    val totalRevenue: Money,
    val nextTier: ItemUnlockTier?,
    val revenueToNextTier: Money?,
    val tierProgressFraction: Float,         // 0.0–1.0 within current tier band
    val tierCapReached: Boolean,             // true when currentTier == storeSize.maxTier
    val justUnlockedTier: ItemUnlockTier? = null,

    // Store size progress
    val storeSize: StoreSize,
    val nextStoreSize: StoreSize?,           // null if at max size
    val storeSizeUpgradeCost: Money?,        // null if at max size
    val canAffordUpgrade: Boolean,
    val justUpgradedSize: StoreSize? = null, // transient notification
)

// Add to GameUiState:
data class GameUiState(
    // ...existing fields...
    val progression: ProgressionUIState,
)
```

Also add `customHoursUnlocked` to the existing `TimeUIState`:

```kotlin
data class TimeUIState(
    val currentTime: GameTime,
    val storeState: StoreState,
    val speedMultiplier: Float = 1.0f,
    val playerPausedTime: Boolean = false,
    val customHoursUnlocked: Boolean = false,  // NEW
    val openTimeMinutes: Int = 360,             // NEW
    val closeTimeMinutes: Int = 1260,           // NEW
)
```

Build `ProgressionUIState` in `toUiState()`:
```kotlin
val nextTier = ItemUnlockTier.nextTier(domain.currentTier)
val tierStart = domain.currentTier.unlockAmount
val tierEnd = nextTier?.unlockAmount
val earned = domain.totalRevenue.cents
val nextSize = StoreSize.nextSize(domain.storeSize)

progression = ProgressionUIState(
    currentTier = domain.currentTier,
    totalRevenue = domain.totalRevenue,
    nextTier = nextTier,
    revenueToNextTier = tierEnd?.let { Money(it - earned) },
    tierProgressFraction = if (tierEnd != null && tierEnd > tierStart)
        ((earned - tierStart).toFloat() / (tierEnd - tierStart)).coerceIn(0f, 1f)
    else 1f,
    tierCapReached = domain.currentTier == domain.storeSize.maxTier,
    storeSize = domain.storeSize,
    nextStoreSize = nextSize,
    storeSizeUpgradeCost = nextSize?.upgradeCost,
    canAffordUpgrade = nextSize != null && domain.money >= nextSize.upgradeCost,
)
```

Build the `TimeUIState` fields in `toUiState()`:
```kotlin
time = TimeUIState(
    // ...existing fields...
    customHoursUnlocked = domain.currentTier.customHoursUnlocked,
    openTimeMinutes = domain.storeConfig.openTimeMinutes,
    closeTimeMinutes = domain.storeConfig.closeTimeMinutes,
)
```

### Step 9 — Handle new changes in `IncrementalUiStateBuilder` (`ui/state/builders/IncrementalUiStateBuilder.kt`)

```kotlin
is GameStateChange.TierUnlocked -> {
    currentState = currentState.copy(
        progression = currentState.progression.copy(
            currentTier = change.newTier,
            justUnlockedTier = change.newTier,
            tierCapReached = currentState.progression.storeSize.maxTier == change.newTier
        )
    )
}

is GameStateChange.StoreSizeUpgraded -> {
    currentState = currentState.copy(
        progression = currentState.progression.copy(
            storeSize = change.newSize,
            nextStoreSize = StoreSize.nextSize(change.newSize),
            storeSizeUpgradeCost = StoreSize.nextSize(change.newSize)?.upgradeCost,
            justUpgradedSize = change.newSize,
            tierCapReached = currentState.progression.currentTier == change.newSize.maxTier
        )
    )
}
```

### Step 10 — Add dismiss events (`ui/GameEvent.kt`)

Two pure-UI events clear the transient notification fields when the player taps the unlock banners:

```kotlin
data object DismissTierUnlock : GameEvent
data object DismissStoreSizeUpgrade : GameEvent
```

In `GameViewModel.onEvent()` (no engine calls):
```kotlin
GameEvent.DismissTierUnlock -> {
    _uiState.update { it?.copy(progression = it.progression.copy(justUnlockedTier = null)) }
    return
}
GameEvent.DismissStoreSizeUpgrade -> {
    _uiState.update { it?.copy(progression = it.progression.copy(justUpgradedSize = null)) }
    return
}
```

### Step 11 — Write Tests

Each progression concern gets its own test, following the established test pattern:

```kotlin
class ProgressionTest {
    @Mock private lateinit var mockItemMetadataCache: ItemMetadataCache

    @Before fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(mockItemMetadataCache.getAllItems()).thenReturn(emptyMap())
    }

    // Revenue tier tests
    @Test fun `tier advances when totalRevenue crosses threshold`() { /* ... */ }
    @Test fun `tier does not regress when money is spent`() { /* ... */ }
    @Test fun `tier is capped at storeSize maxTier`() { /* ... */ }
    @Test fun `banked revenue unlocks tier immediately on store upgrade`() { /* ... */ }
    @Test fun `hiring locked staff is rejected`() { /* ... */ }
    @Test fun `inventory items from locked categories are filtered`() { /* ... */ }
    @Test fun `items beyond store size cap are filtered even if tier allows them`() { /* ... */ }

    // Store size tests
    @Test fun `upgradeStore deducts cost from money`() { /* ... */ }
    @Test fun `upgradeStore is rejected when insufficient funds`() { /* ... */ }
    @Test fun `upgradeStore does nothing at max size`() { /* ... */ }
    @Test fun `backroom cap is enforced per store size`() { /* ... */ }
    @Test fun `hiring beyond staff cap is rejected`() { /* ... */ }
    @Test fun `staff cap increases after store upgrade`() { /* ... */ }

    // Custom hours tests
    @Test fun `setStoreHours is rejected before Tier 2`() { /* ... */ }
    @Test fun `setStoreHours updates storeConfig after Tier 2 unlock`() { /* ... */ }
    @Test fun `setStoreHours rejects invalid range (close before open)`() { /* ... */ }
    @Test fun `setStoreHours accepts 24-hour operation (closeTimeMinutes == 1440)`() { /* ... */ }
    @Test fun `storeConfig hours are NOT automatically changed on tier advance`() { /* ... */ }
}
```

---

## Performance Considerations

| Concern | Solution |
|---------|----------|
| Tier check runs every transaction | `checkAndAdvanceTier()` does a single O(1) lookup (`fromTotalEarned` scans 4 entries; `unlockAmount` comparison is O(1)) |
| Inventory filter re-runs every tick | `MemoizedInventoryMapper` already caches results; use `(tier, storeSize)` pair as cache key — invalidate only on tier or size change |
| `TierUnlocked` causes full UI rebuild | `IncrementalUiStateBuilder` handles it as a targeted change — only `ProgressionUIState` and `StaffUIState` fields change |
| `StoreSizeUpgraded` causes full UI rebuild | Same incremental builder path — only `ProgressionUIState` fields update |
| Store upgrade mutates storeConfig | Infrequent (5 upgrades total) — full rebuild acceptable |

---

## Extensibility Notes

- **New tiers**: Add a new enum entry to `ItemUnlockTier`. No other code changes required.
- **New store sizes**: Add a new enum entry to `StoreSize` with appropriate caps. `nextSize()` uses `entries.indexOf` so new entries are automatically ordered.
- **New staff types**: Add to `EntityDef`, add the key to the appropriate tier's `unlockedStaffKeys`. The hire UI filter updates automatically.
- **New progression axes** (e.g., reputation score, customer satisfaction): Add to `GameState`, compute in `TransactionEngine`, gate in `GameEngine`. Follow the same pattern as `totalRevenue`/`currentTier`.
- **New secondary unlocks** (marketing, loyalty, etc.): Add Boolean flags to `GameState` (e.g., `loyaltyProgramActive`), a `GameEvent` to activate them, and a cost check in `GameEngine`. They can be tied to `currentTier` as eligibility checks.
- **Threshold tuning**: Tier thresholds live in `ItemUnlockTier`; store size costs live in `StoreSize`. Both are single-file changes.

---

## Files to Modify (Summary)

| File | Change |
|------|--------|
| `domain/items/ItemUnlockTier.kt` | Add `unlockedStaffKeys`, `customHoursUnlocked` fields; update `unlockedSections` per tier (PRODUCE → TIER_3, HEALTH stays TIER_GM); update `requiredTierForSection()` to map `PRODUCE → TIER_3` |
| `domain/StoreSize.kt` | **New file** — `StoreSize` enum with 5 sizes, caps, and `unlockedCategories` |
| `domain/GameStateData.kt` | Add `totalRevenue: Money`, `currentTier: ItemUnlockTier`, `storeSize: StoreSize` to `GameState` |
| `domain/TransactionEngine.kt` | Increment `totalRevenue` in `completeTransaction()` |
| `domain/GameStateChange.kt` | Add `TierUnlocked(newTier, previousTier)`, `StoreSizeUpgraded(newSize, previousSize)` |
| `domain/GameEngine.kt` | Add `checkAndAdvanceTier()` with size cap; add `setStoreHours()` with validation; add `upgradeStore()`; add backroom cap to `buyItemToBackroom()`; guard `hireEntity()` against tier lock and staff cap |
| `ui/GameEvent.kt` | Add `SetStoreHours`, `UpgradeStore`, `DismissTierUnlock`, `DismissStoreSizeUpgrade` |
| `ui/state/GameUiState.kt` | Add `ProgressionUIState` (tier + store size fields); add to `GameUiState`; add `unlockedEntityTypes` to `StaffUIState`; add `customHoursUnlocked`, `openTimeMinutes`, `closeTimeMinutes` to `TimeUIState` |
| `ui/state/mappers/MemoizedInventoryMapper.kt` | Accept `tier` and `storeSize` params; filter by intersection of both `unlockedSections` sets |
| `ui/state/builders/IncrementalUiStateBuilder.kt` | Handle `TierUnlocked` and `StoreSizeUpgraded` changes |
| `ui/viewmodels/GameViewModel.kt` | Build `ProgressionUIState`; handle `SetStoreHours`, `UpgradeStore`, `DismissTierUnlock`, `DismissStoreSizeUpgrade`; pass both tier and storeSize to mapper; filter staff by tier |
| `app/src/test/.../ProgressionTest.kt` | New test class (see Step 11) |

