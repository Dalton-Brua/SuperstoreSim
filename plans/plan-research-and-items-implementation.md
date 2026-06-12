# Implementation Plan: Research System + Future Items

**Created**: 2026-06-12
**Source plans**: `plan-researchSystem.md` (design), `plan-futureItems.md` (item data)
**Branch**: create `feature/researchSystem` from `main`

---

## Overview

This plan replaces the monolithic tier system (TIER_1/2/3/GM) with a granular research system:
- **Tutorial** guides new players through basics, ends by hiring a Market Analyst
- **Market Analyst** staff role generates research points from store events
- **Research gates** replace tiers — items and features unlock through 55 individual research entries
- **218 new items** across 8 new categories + expanded existing categories
- **28 affinity groups** make customer baskets realistic via cross-item weight boosting
- **Substitution groups** prevent unrealistic duplicates (e.g., buying large AND small chips) via cross-item weight suppression

The tier system (`ItemUnlockTier`, `ProgressionManager`, `TierProgressCard`, `UnlocksScreen`) is removed entirely.

---

## Codebase Reference (current state)

| Component | File | Key Details |
|-----------|------|-------------|
| GameState | `domain/GameStateData.kt:185` | Has `currentTier: ItemUnlockTier`, `totalRevenue`, `inventory`, `hiredEntityRegistry` |
| Item (Room) | `domain/items/Item.kt:9` | `tier: String = "TIER_1"`, `purchaseWeight`, `casePack` |
| ItemMetadata | `domain/items/ItemMetadata.kt:9` | `tier: ItemUnlockTier` enum field |
| ItemMetadataCache | `domain/items/ItemMetadataCache.kt:9` | Singleton, no tier filtering — filtering happens in TransactionEngine |
| ItemDataLoader | `domain/items/ItemDataLoader.kt:13` | Loads `R.raw.items` JSON, always clears+reloads |
| ItemUnlockTier | `domain/items/ItemUnlockTier.kt:6` | 4 tiers, `unlockAmount` gates, `requiredTierForSection()` |
| ItemCategory | `domain/items/ItemCategory.kt:6` | 12 enums: GROCERY..ELECTRONICS |
| EntityDef | `domain/Entities/EntityDef.kt:23` | 4 roles (CASHIER/STOCKER/FRESH_HANDLER/MANAGER), `allEntities` list |
| HiredEntity | `domain/Entities/HiredEntity.kt:44` | `tier: Tier` (BASE/FAST/MANAGER), `throughputWeight`, `xp`, `level` |
| HiredEntityRegistry | `domain/Entities/HiredEntityRegistry.kt:6` | `hireEntity()`, `fireEntity()`, `grantXp()` |
| ProgressionManager | `domain/progression/ProgressionManager.kt:11` | `unlockNextTier()` — tier unlock + adds items to inventory |
| TransactionEngine | `domain/Transactions/TransactionEngine.kt:44-52` | Filters items by `currentTier.unlockAmount` in `startNewTransaction()` and `generateRandomTransaction()` |
| TickOrchestrator | `domain/tick/TickOrchestrator.kt:19` | Sequential tick phases, integration point for tutorial/research processors |
| GameEngine | `domain/GameEngine.kt:36` | Event routing, `@Singleton`, has `ProgressionManager` injected |
| GameEvent | `ui/GameEvent.kt:9` | Sealed interface, ~40 events, has `UnlockNextTier`, `DismissTierUnlock` |
| GameUiState | `ui/state/GameUiState.kt:24` | 13 sub-states including `progression: ProgressionUIState` |
| Screen enum | `domain/Screen.kt:3` | GAME, INVENTORY, HISTORY, METRICS, STAFF, STAFF_ENTITY_LIST |
| BottomNavBar | `ui/navigation/BottomNavBar.kt:45` | 5 tabs: Store/Inventory/Manage/History/Metrics |
| MainScreenPager | `ui/root/MainScreenPager.kt:37` | HorizontalPager with 5 main screens |
| StoreHomeScreen | `ui/screens/home/StoreHomeScreen.kt` | Has TierProgressCard |
| UnlocksScreen | `ui/screens/staff/UnlocksScreen.kt` | Tier cards UI |
| TierProgressCard | `ui/components/cards/TierProgressCard.kt` | Revenue progress toward next tier |
| AppDatabase | `di/AppDatabase.kt:13` | Version 9, entities: Item, TransactionEntity, TransactionLineEntity |
| items.json | `res/raw/items.json` | `"tier": "TIER_1"` field, prices in cents |
| Serializer | `domain/persistence/GameStateSerializer.kt` | 28 lines, kotlinx.serialization, `ignoreUnknownKeys = true` |

**All file paths below are relative to `app/src/main/java/com/example/superstoresimulator/`** unless stated otherwise.

---

## Phase 1: Item Data Pipeline — tier → researchGate

**Goal**: Replace the `tier` string/enum with `researchGate: String?` across the entire item pipeline. This is the foundation everything else builds on.

### Step 1.1: Room Entity — `Item.kt`

**File**: `domain/items/Item.kt`

1. Add field `val researchGate: String? = null` (nullable — null = starter item, always accessible)
2. Add field `val affinityGroups: String? = null` (comma-separated string for Room storage)
3. Add field `val substitutionGroup: String? = null` (items in same group are substitutes — e.g., "doritos_size" for all Doritos sizes)
4. Keep `tier: String = "TIER_1"` temporarily for Room migration compatibility — mark `@Deprecated`
5. Increment Room DB version from 9 → 10 in `di/AppDatabase.kt:13`
6. Add Room migration: `ALTER TABLE items ADD COLUMN researchGate TEXT DEFAULT NULL`
7. Add Room migration: `ALTER TABLE items ADD COLUMN affinityGroups TEXT DEFAULT NULL`
8. Add Room migration: `ALTER TABLE items ADD COLUMN substitutionGroup TEXT DEFAULT NULL`

### Step 1.2: ItemMetadata — `ItemMetadata.kt`

**File**: `domain/items/ItemMetadata.kt`

1. Replace `val tier: ItemUnlockTier` with `val researchGate: String? = null`
2. Add `val affinityGroups: List<String> = emptyList()`
3. Add `val substitutionGroup: String? = null` — items sharing a substitution group suppress each other's purchase weight (e.g., large vs small chip bags)
4. Add `val isVendorItem: Boolean` — keep existing field
5. Remove the `tier` field entirely (it was only used for filtering, which now uses `researchGate`)

### Step 1.3: ItemMetadataCache — `ItemMetadataCache.kt`

**File**: `domain/items/ItemMetadataCache.kt`

1. Update `initialize()` at line 33–47 to map `item.researchGate` → `metadata.researchGate` instead of `ItemUnlockTier.valueOf(item.tier)`
2. Parse `item.affinityGroups?.split(",")?.map { it.trim() } ?: emptyList()` → `metadata.affinityGroups`
3. Add new methods:

```kotlin
fun isItemAccessible(itemId: Int, researchedUpgrades: Set<String>): Boolean {
    val meta = get(itemId) ?: return false
    return meta.researchGate == null || meta.researchGate in researchedUpgrades
}

fun getAccessibleItemIds(researchedUpgrades: Set<String>): Set<Int> =
    metadataCache.keys.filter { isItemAccessible(it, researchedUpgrades) }.toSet()

fun sharesAffinityGroup(id1: Int, id2: Int): Boolean {
    val g1 = get(id1)?.affinityGroups ?: return false
    val g2 = get(id2)?.affinityGroups ?: return false
    return g1.any { it in g2 }
}

fun sharesSubstitutionGroup(id1: Int, id2: Int): Boolean {
    val s1 = get(id1)?.substitutionGroup ?: return false
    val s2 = get(id2)?.substitutionGroup ?: return false
    return s1 == s2
}
```

4. Build a precomputed `affinityIndex: Map<String, Set<Int>>` at init time for O(1) affinity lookups
5. Build a precomputed `substitutionIndex: Map<String, Set<Int>>` at init time for O(1) substitution lookups

### Step 1.4: ItemDataLoader — `ItemDataLoader.kt`

**File**: `domain/items/ItemDataLoader.kt`

1. Parse `"researchGate"` field from JSON (nullable): `researchGate = obj.optString("researchGate", null)`
2. Parse `"affinityGroups"` field from JSON (nullable): `affinityGroups = obj.optString("affinityGroups", null)`
3. Parse `"substitutionGroup"` field from JSON (nullable): `substitutionGroup = obj.optString("substitutionGroup", null)`
4. Keep parsing `"tier"` for now (backward compat) — it stays in the DB column but is unused

### Step 1.5: TransactionEngine — Replace Tier Filtering

**File**: `domain/Transactions/TransactionEngine.kt`

Two locations to change (lines 44-52 and 105-113):

**Before** (both `startNewTransaction` and `generateRandomTransaction`):
```kotlin
val currentTierAmount = state.currentTier.unlockAmount
val availableItemIds = state.inventory.keys.filter { itemId ->
    val meta = cache?.get(itemId)
    val itemTier = meta?.tier ?: ItemUnlockTier.TIER_1
    if (itemTier.unlockAmount > currentTierAmount) return@filter false
    if (meta?.isVendorItem == true && meta.vendorTier > currentVendorTier) return@filter false
    true
}
```

**After**:
```kotlin
val researchedUpgrades = state.researchState.researchedUpgrades
val availableItemIds = state.inventory.keys.filter { itemId ->
    val meta = cache?.get(itemId) ?: return@filter false
    if (!cache.isItemAccessible(itemId, researchedUpgrades)) return@filter false
    if (meta.isVendorItem && meta.vendorTier > currentVendorTier) return@filter false
    true
}
```

**Also modify `weightedSample()`** to apply affinity boost and substitution suppression:
- After selecting each item, boost remaining items sharing an affinity group by `AFFINITY_BOOST = 2.0f`
- After selecting each item, suppress remaining items sharing a substitution group by `SUBSTITUTION_PENALTY = 0.1f` (near-zero — customer almost never buys both large and small of same product)
- Substitution penalty overrides affinity boost when both apply (penalty wins)
- Add `companion object { const val AFFINITY_BOOST = 2.0f; const val SUBSTITUTION_PENALTY = 0.1f }`

### Step 1.6: Update items.json

**File**: `app/src/main/res/raw/items.json`

For every item in the file:
1. Add `"researchGate": "<gate_id>"` field (or omit for starter items)
2. Add `"affinityGroups": "breakfast,dairy_staples"` field (comma-separated, or omit if none)
3. Add `"substitutionGroup": "doritos_size"` field (items that are size/variant substitutes, or omit if none)
4. Keep `"tier"` field for now (Room migration reads it)

**Starter items** (12 items, no `researchGate`):
- Bread, White Rice, Spaghetti, Canned Black Beans, Mac & Cheese, Ramen (GROCERY)
- Potato Chips, Pretzels, Granola Bar (SNACKS)
- Coca-Cola 2L, Water 24-Pack, Apple Juice (DRINKS)

**Gate assignments for existing 136 items**: Map from old tier to research gates per the tables in `plan-researchSystem.md` lines 526–586.

### Step 1.7: Add All New Items to items.json

Add ~218 new items from `plan-futureItems.md`:
- **Part 1**: ~44 items expanding existing gates
- **Part 2**: ~85 items across 20 new gates for existing categories
- **Part 3**: ~89 items across 8 new categories (DELI, PET, BABY, OFFICE, TOYS, AUTO, GARDEN, SEASONAL)

Each item needs: `id`, `name`, `price` (cents), `description`, `unitCost` (cents), `category`, `tier` (for backward compat — map to a sensible value), `casePack`, `purchaseWeight`, optional `shelfLifeDays`, `soldByWeight`, `researchGate`, `affinityGroups`, optional `substitutionGroup`.

**ID scheme**: Continue from the last existing item ID. Check `items.json` for the current max ID.

### Step 1.8: Add New ItemCategory Values

**File**: `domain/items/ItemCategory.kt`

Add 8 new categories:
```kotlin
DELI("Deli"),
PET("Pet"),
BABY("Baby"),
OFFICE("Office"),
TOYS("Toys"),
AUTO("Auto"),
GARDEN("Garden"),
SEASONAL("Seasonal"),
```

### Step 1.9: Tests

**File**: `app/src/test/.../domain/items/ItemMetadataCacheTest.kt` (new)

- `isItemAccessible` returns true for null-gate items
- `isItemAccessible` returns false for gated items when gate not researched
- `isItemAccessible` returns true for gated items when gate is in set
- `getAccessibleItemIds` returns correct subset
- `sharesAffinityGroup` returns true for items sharing a group
- `sharesAffinityGroup` returns false for unrelated items
- `sharesSubstitutionGroup` returns true for same-product size variants
- `sharesSubstitutionGroup` returns false when either item has no substitution group

---

## Phase 2: Research System — Domain Layer

**Goal**: Build `ResearchState`, `ResearchManager`, `ResearchableUpgrade` registry, and `MARKET_ANALYST` role.

### Step 2.1: Research Data Model

**New file**: `domain/research/ResearchState.kt`

```kotlin
@Serializable
sealed interface AnalystAssignment {
    @Serializable
    @SerialName("research")
    data class Research(val upgradeId: String) : AnalystAssignment
    
    @Serializable
    @SerialName("consulting")
    data object Consulting : AnalystAssignment
}

@Serializable
data class ResearchState(
    val researchProgress: Map<String, Float> = emptyMap(),
    val totalPointsEarned: Float = 0f,
    val researchedUpgrades: Set<String> = emptySet(),
    val analystAssignments: Map<Int, AnalystAssignment> = emptyMap(),
)
```

### Step 2.2: Research Upgrade Registry

**New file**: `domain/research/ResearchUpgradeRegistry.kt`

Define `ResearchableUpgrade` data class (NOT `@Serializable` — code-defined, not persisted):

```kotlin
data class ResearchableUpgrade(
    val id: String,
    val displayName: String,
    val description: String,
    val teaserDescription: String,
    val category: ResearchCategory,
    val researchCost: Int,
    val prerequisites: List<String> = emptyList(),
    val requiredStoreSize: StoreSize? = null,
    val gateCheck: ((GameState) -> Boolean)? = null,
)

enum class ResearchCategory {
    STORE_EXPANSION, PRODUCT_LINES, OPERATIONS, PRICING, STAFF_MANAGEMENT,
}
```

Define a singleton `object ResearchUpgradeRegistry` containing all ~55 product-line gates + ~16 feature gates as a `val allUpgrades: Map<String, ResearchableUpgrade>`.

**Product-line gates** (55 total): Use the exact IDs, costs, and prerequisites from `plan-researchSystem.md` lines 503–586 and `plan-futureItems.md` lines 718–761.

**Feature gates** (16 total): From `plan-researchSystem.md` lines 503–624:
- Store expansion: `store_small_grocery` (10), `store_grocery` (25), `store_superstore` (50), `store_supercenter` (100), `register_expansion` (15)
- Operations: `bulk_ordering` (12), `extra_truck_slots` (15), `fresh_auto_order` (18), `early_truck` (10)
- Pricing: `category_pricing` (15), `item_pricing` (20), `default_markup` (10)
- Staff: `manager_hiring` (20), `auto_hire` (25), `staff_scheduling` (12)

**Gate checks** (from plan lines 630–641):
- `prod_dairy_basics`: `totalRevenue >= $2,500`
- `prod_frozen_basics`: `totalRevenue >= $10,000`
- `prod_meat_counter`: `totalRevenue >= $10,000`
- `prod_pharmacy`: `totalRevenue >= $25,000`
- `prod_electronics`: `totalRevenue >= $25,000`
- `manager_hiring`: `hiredEntityRegistry.totalCount() >= 3`
- `store_small_grocery`: `totalRevenue >= $1,000`

**Store size requirements** (from `plan-futureItems.md`):
- DELI, PET, BABY gates: `requiredStoreSize = StoreSize.SUPERSTORE`
- OFFICE, TOYS, AUTO, GARDEN, SEASONAL gates: `requiredStoreSize = StoreSize.SUPERCENTER`

### Step 2.3: Market Analyst EntityDef

**File**: `domain/Entities/EntityDef.kt`

Add after `MANAGER` (line 94):
```kotlin
val MARKET_ANALYST = EntityDef(
    key = "market_analyst",
    displayName = "Market Analyst",
    cost = Money(3_000),
    description = "Generates research points while on shift. Research unlocks new upgrades and features.",
    roleDescription = "Researches upgrades",
    icon = Icons.Default.Search,
    baseWage = Money(900),
    tierPerks = mapOf(
        Tier.BASE to "Observes store activity and generates research points",
        Tier.FAST to "Faster insight generation (1.5x throughput)",
        Tier.MANAGER to "Lead Analyst — 2.0x throughput",
    ),
)
```

Add to `allEntities` list at line 96:
```kotlin
val allEntities: List<EntityDef> = listOf(CASHIER, STOCKER, FRESH_HANDLER, MANAGER, MARKET_ANALYST)
```

### Step 2.4: ResearchManager

**New file**: `domain/research/ResearchManager.kt`

```kotlin
@Singleton
class ResearchManager @Inject constructor(
    private val itemMetadataCache: ItemMetadataCache,
) {
```

**Methods** (all pure-function: take GameState, return GameState):

| Method | Purpose |
|--------|---------|
| `distributeInsightPoints(state, baseEventPoints)` | Find on-shift analysts, group by assignment, route points to topics (sqrt scaling per-topic) or cash (consulting). See plan lines 275-315 for algorithm. |
| `checkCompletions(state)` | For each `researchProgress` entry >= cost → move to `researchedUpgrades`, idle those analysts, add newly-accessible items to inventory |
| `assignAnalyst(state, entityId, assignment)` | Guards: entity exists, is MARKET_ANALYST, topic is visible+unresearched. Set `analystAssignments[entityId]` |
| `removeAnalystAssignment(state, entityId)` | Clean up on fire. Remove from `analystAssignments` |
| `isResearchVisible(state, upgradeId)` | Check prerequisites met + gate check passes + not completed + store size met |
| `isUpgradeResearched(state, upgradeId)` | `upgradeId in state.researchState.researchedUpgrades` |
| `getAvailableResearch(state)` | Filter registry by `isResearchVisible` |
| `getTopicRate(state, upgradeId)` | Calculate points/hour for a topic based on assigned on-shift analysts |

**Point distribution algorithm** (from plan lines 275-315):
```kotlin
// For each store event:
// 1. Find all on-shift Market Analysts
// 2. Group by assignment (Research(upgradeId), Consulting, idle)
// 3. For each Research group: effectiveCount = sqrt(count), avgThroughput = avg(throughputWeight * levelMultiplier), topicPoints = baseEventPoints * effectiveCount * avgThroughput
// 4. For each Consulting analyst: cashEarned = baseEventPoints * throughputWeight * levelMultiplier * CONSULTING_CASH_PER_POINT
// 5. Idle analysts: skip
```

**Insight constants**:
```kotlin
companion object {
    const val TRANSACTION_INSIGHT = 0.3f
    const val STOCK_INSIGHT = 0.05f
    const val SPOILAGE_INSIGHT = 0.2f
    const val DELIVERY_INSIGHT = 1.5f
    const val LOST_CUSTOMER_INSIGHT = 0.15f
    val CONSULTING_CASH_PER_POINT = Money(500) // $5.00
}
```

**Adding completed research items to inventory**: When `checkCompletions()` completes a product-line gate, query `ItemMetadataCache` for all items with matching `researchGate`, add them to `state.inventory` as `InventoryState()` (empty). This replaces the role of `ProgressionManager.unlockNextTier()`.

### Step 2.5: Add ResearchState to GameState

**File**: `domain/GameStateData.kt`

Add field with default:
```kotlin
val researchState: ResearchState = ResearchState(),
```

This is safe for old saves — `ignoreUnknownKeys = true` + default value means old saves deserialize cleanly.

### Step 2.6: Wire Insight Distribution into Store Events

Research points are generated when store events happen while an assigned analyst is on shift. Hook `ResearchManager.distributeInsightPoints()` into existing code:

| Event | Where to Hook | Base Points |
|-------|---------------|-------------|
| Transaction completed | `TransactionEngine.ringUpItemAndTrackMetrics()` — after transaction completion block | `TRANSACTION_INSIGHT` |
| Case pack stocked | `StaffTickProcessor` — after stocker/fresh handler action | `STOCK_INSIGHT` |
| Item spoiled | `SpoilageManager.processExpiration()` — after batch expiration | `SPOILAGE_INSIGHT` |
| Truck delivery | `DayRolloverProcessor` — truck arrival block | `DELIVERY_INSIGHT` |
| Customer lost | `TrafficProcessor` — when customer walks away (no register / queue full) | `LOST_CUSTOMER_INSIGHT` |

**Implementation approach**: Each hook site calls `researchManager.distributeInsightPoints(state, BASE_POINTS)` and overwrites `state`. Then calls `researchManager.checkCompletions(state)` to auto-complete topics.

**Important**: `ResearchManager` needs to be injected into `TickOrchestrator` and `TransactionEngine`. Both are already `@Singleton @Inject constructor(...)` — add `private val researchManager: ResearchManager` parameter.

Alternatively, add a `ResearchTickProcessor` to `TickOrchestrator` that runs after staff processing and handles insight accumulation from the tick's events. This is cleaner — batch all event counting per tick rather than hooking individual event sites.

**Recommended approach**: Create `domain/tick/ResearchTickProcessor.kt` injected into `TickOrchestrator`:
- Count events that happened during this tick by comparing pre/post state (e.g., `totalTransactionsCompleted` delta, `itemsExpired` delta, etc.)
- Call `researchManager.distributeInsightPoints()` with aggregated points
- Call `researchManager.checkCompletions()`

This avoids modifying `TransactionEngine`, `SpoilageManager`, etc. — the processor reads state deltas.

### Step 2.7: GameEngine Integration

**File**: `domain/GameEngine.kt`

1. Inject `ResearchManager` into constructor
2. Add method:
```kotlin
fun assignAnalyst(entityId: Int, assignment: AnalystAssignment?) {
    state = if (assignment != null) {
        researchManager.assignAnalyst(state, entityId, assignment)
    } else {
        researchManager.removeAnalystAssignment(state, entityId)
    }
}
```
3. Modify `fireEntity()` (line 164):
```kotlin
fun fireEntity(entityId: Int) {
    state = staffManager.fireEntity(state, entityId)
    state = registerManager.unassignEntity(state, entityId)
    state = researchManager.removeAnalystAssignment(state, entityId)
}
```

### Step 2.8: GameEvent Additions

**File**: `ui/GameEvent.kt`

Add:
```kotlin
data class AssignAnalyst(val entityId: Int, val assignment: AnalystAssignment?) : GameEvent
```

Route in `GameViewModel` event handler (wherever `when (event)` dispatches):
```kotlin
is GameEvent.AssignAnalyst -> engine.assignAnalyst(event.entityId, event.assignment)
```

### Step 2.9: Tests

**New file**: `app/src/test/.../domain/research/ResearchManagerTest.kt`

Test cases from plan lines 1105-1178:
- Assigned analyst generates topic points (0.3 per transaction)
- Idle analyst generates nothing
- Consulting analyst generates cash ($1.50 per transaction)
- Off-shift analyst ignored
- FAST tier boosts points (1.5x)
- 2 analysts same topic → sqrt scaling (~1.41x)
- 2 analysts different topics → full speed both
- Topic completes at cost threshold → added to `researchedUpgrades`, analysts idled
- Completion adds items to inventory
- Prerequisites chain works (prod_breakfast → prod_baking)
- Gate check blocks visibility (totalRevenue < threshold)
- Assignment rejected for non-analyst entity
- Assignment rejected for completed topic
- Fired analyst removed from assignments
- Serialization round-trip for `ResearchState`

---

## Phase 3: Remove Tier System

**Goal**: Delete `ItemUnlockTier`, `ProgressionManager`, and all tier UI. Research fully replaces tiers.

### Step 3.1: Remove from GameState

**File**: `domain/GameStateData.kt`

- Remove `val currentTier: ItemUnlockTier = ItemUnlockTier.TIER_1` (line 223)
- Keep `val totalRevenue: Money` (still needed for gate checks and display)
- Remove `justUnlockedTier: ItemUnlockTier? = null` if it exists

### Step 3.2: Remove ProgressionManager

- Delete file `domain/progression/ProgressionManager.kt`
- Remove injection from `GameEngine.kt` constructor
- Remove `unlockNextTier()` method from `GameEngine.kt` (line 243)
- Remove `UnlockNextTier` and `DismissTierUnlock` from `GameEvent.kt`
- Remove event routing in `GameViewModel`

### Step 3.3: Remove ItemUnlockTier

- Delete file `domain/items/ItemUnlockTier.kt`
- Remove `requiredTierForSection()` function
- Remove `tier` field from `ItemMetadata.kt`
- Remove `ItemUnlockTier.valueOf(item.tier)` conversion in `ItemMetadataCache.kt:42`
- Update all imports

### Step 3.4: Remove Tier UI

- Delete `ui/components/cards/TierProgressCard.kt`
- Delete `ui/screens/staff/UnlocksScreen.kt`
- Remove `TierProgressCard` call from `StoreHomeScreen.kt`
- Remove "Unlocks" tab from `StaffScreen.kt` (the combined StaffAndUnlocksScreen)
- Remove `ProgressionUIState` from `GameUiState.kt` (or simplify to just `totalRevenue` for display)
- Clean up `ProgressionUiMapper.kt` — either repurpose for research or delete

### Step 3.5: Update All Tier References

Search codebase for remaining `currentTier`, `ItemUnlockTier`, `unlockNextTier`, `TIER_1`, etc. and update or remove. Key locations:
- `GameEngine.kt:112` — `placeFreshBulkOrder()` passes `state.currentTier`
- `GameEngine.kt:350-354` — `seedInventory()` filters by tier
- `TransactionEngine.kt:44-52, 105-113` — already updated in Phase 1
- `InventoryManager` — may filter by tier for bulk orders

**For `seedInventory()`**: Replace tier filtering with showing only starter items (researchGate == null):
```kotlin
private fun seedInventory(quantity: Int, currentDay: Int): Map<Int, InventoryState> {
    val inventory = mutableMapOf<Int, InventoryState>()
    itemMetadataCache.getAllItems().forEach { (itemId, item) ->
        val meta = itemMetadataCache.get(itemId) ?: return@forEach
        if (meta.researchGate != null) return@forEach  // only starter items
        // ... rest stays same
    }
    return inventory
}
```

### Step 3.6: Update InventoryManager Bulk Orders

Anywhere `InventoryManager` filters items by `currentTier`, replace with research-based filtering:
```kotlin
val accessible = itemMetadataCache.getAccessibleItemIds(state.researchState.researchedUpgrades)
```

### Step 3.7: Clean Up Item.kt

The `tier: String` column in Room can remain for now (removing it requires a destructive migration or complex ALTER). Mark it `@Deprecated` and ignore it. `ItemDataLoader` can stop writing it on fresh loads — or keep writing it as `"TIER_1"` default.

---

## Phase 4: Tutorial System

**Goal**: Linear tutorial that guides new players through basics, ending with hiring a Market Analyst.

### Step 4.1: Tutorial Data Model

**New file**: `domain/tutorial/TutorialState.kt`

```kotlin
@Serializable
enum class TutorialStep(
    val displayTitle: String,
    val instruction: String,
    val hintText: String,
) {
    WELCOME(...), ORDER_FIRST_ITEM(...), WAIT_FOR_DELIVERY(...),
    STOCK_SHELVES(...), OPEN_STORE(...), FIRST_SALE(...),
    COMPLETE_TRANSACTION(...), HIRE_STAFF(...), HIRE_ANALYST(...),
    TUTORIAL_COMPLETE(...),
}

@Serializable
data class TutorialState(
    val currentStep: TutorialStep = TutorialStep.WELCOME,
    val completedSteps: Set<TutorialStep> = emptySet(),
    val tutorialComplete: Boolean = false,
    val dismissedHints: Set<String> = emptySet(),
)
```

Use exact step text from `plan-researchSystem.md` lines 69-118.

### Step 4.2: TutorialManager

**New file**: `domain/tutorial/TutorialManager.kt`

Pure-function manager:

| Method | Purpose |
|--------|---------|
| `checkStepCompletion(state)` | Check current step's completion condition, advance if met. Auto-skip already-met steps. Returns updated `GameState`. |
| `getCurrentStep(state)` | Return current `TutorialStep?` (null if complete) |
| `skipTutorial(state)` | Set `tutorialComplete = true`, all steps completed |
| `isFeatureVisible(state, feature: String)` | Check if a feature should be shown given tutorial + research state |

**Step completion conditions** (from plan lines 122-135):

| Step | Completion Condition |
|------|---------------------|
| WELCOME | Auto-advance (always completed immediately) |
| ORDER_FIRST_ITEM | Any item in `pendingOrders` or backroom |
| WAIT_FOR_DELIVERY | Any `backroomBatches` non-empty |
| STOCK_SHELVES | Any `shelfBatches` non-empty |
| OPEN_STORE | `storeState != CLOSED` |
| FIRST_SALE | `pendingCustomers > 0` or `totalTransactionsCompleted > 0` |
| COMPLETE_TRANSACTION | `totalTransactionsCompleted > 0` |
| HIRE_STAFF | `hiredEntityRegistry.hiredEntities.isNotEmpty()` |
| HIRE_ANALYST | Any hired entity with `def == MARKET_ANALYST` |
| TUTORIAL_COMPLETE | Auto-complete → set `tutorialComplete = true` |

**Feature visibility map** (from plan lines 188-203):

| Feature Key | Visible When |
|-------------|-------------|
| `order_items` | After WELCOME step completed |
| `store_toggle` | After STOCK_SHELVES completed |
| `player_roles` | After OPEN_STORE completed |
| `hire_staff` | After COMPLETE_TRANSACTION completed |
| `hire_analyst` | After HIRE_STAFF completed |
| `research_tab` | `tutorialComplete == true` |
| `pricing_ui` | `tutorialComplete && isUpgradeResearched("category_pricing")` |
| `delivery_settings` | `tutorialComplete && isUpgradeResearched("extra_truck_slots")` |
| `staff_scheduling` | `tutorialComplete && isUpgradeResearched("staff_scheduling")` |
| `registers` | `tutorialComplete && isUpgradeResearched("register_expansion")` |
| `bulk_ordering` | `tutorialComplete && isUpgradeResearched("bulk_ordering")` |
| `manager_hiring` | `tutorialComplete && isUpgradeResearched("manager_hiring")` |

### Step 4.3: Add TutorialState to GameState

**File**: `domain/GameStateData.kt`

```kotlin
val tutorialState: TutorialState = TutorialState(),
```

### Step 4.4: Wire into Tick

**New file**: `domain/tick/TutorialTickProcessor.kt`

Injected into `TickOrchestrator`. Runs once per tick (cheap — just checks conditions):
```kotlin
fun process(state: GameState): GameState {
    if (state.tutorialState.tutorialComplete) return state
    return tutorialManager.checkStepCompletion(state)
}
```

Add to `TickOrchestrator.tick()` after `updatePricing()` and before traffic processing.

### Step 4.5: GameEvent Additions

**File**: `ui/GameEvent.kt`

```kotlin
data object SkipTutorial : GameEvent
```

Route in `GameViewModel`:
```kotlin
is GameEvent.SkipTutorial -> engine.skipTutorial()
```

Add to `GameEngine.kt`:
```kotlin
fun skipTutorial() {
    state = tutorialManager.skipTutorial(state)
}
```

### Step 4.6: Tests

**New file**: `app/src/test/.../domain/tutorial/TutorialManagerTest.kt`

Test cases from plan lines 1065-1097:
- Fresh game starts at WELCOME
- Steps advance on completion conditions
- Skip tutorial sets tutorialComplete = true
- Out-of-order: already-met steps auto-skip
- Feature visibility checks (order_items hidden before WELCOME, etc.)
- Existing save migration: totalTransactionsCompleted > 0 → tutorialComplete = true

---

## Phase 5: UI Changes

### Step 5.1: Add Research Tab to Navigation

**File**: `domain/Screen.kt`

Add:
```kotlin
RESEARCH,
```

**File**: `ui/navigation/BottomNavBar.kt`

Add 6th tab (Research) — only visible when `tutorialComplete == true`:
- Icon: `Icons.Default.Science` or `Icons.Default.Search`
- Label: "Research"
- Position: after Manage, before History

**File**: `ui/root/MainScreenPager.kt`

Add `RESEARCH` to the pager screen list, conditionally included based on tutorial state.

### Step 5.2: Research Screen

**New file**: `ui/screens/research/ResearchScreen.kt`

Layout (from plan lines 654-668):

1. **Analyst Roster** — List of hired Market Analysts:
   - Name, tier badge, level, on-shift indicator
   - Assignment dropdown: "Idle" / "Consulting" / list of available research topic names
   - Dispatches `GameEvent.AssignAnalyst` on change

2. **Active Research** — Topics with `researchProgress > 0` or analysts assigned:
   - Progress bar (current / cost)
   - Assigned analyst count
   - Estimated time to completion (points remaining / current points/hour)

3. **Available Research** — Grouped by `ResearchCategory`:
   - Each entry: name, teaser description, cost, "Assign Analyst" button
   - Product-line entries show item count that will unlock
   - Only shows upgrades where prerequisites met + gate check passes

4. **Completed Research** — Collapsible section:
   - Researched topics with descriptions and which items/features they unlocked

### Step 5.3: Research UI State

**File**: `ui/state/GameUiState.kt`

Add to `GameUiState`:
```kotlin
val research: ResearchUIState = ResearchUIState(),
val tutorial: TutorialUIState = TutorialUIState(),
```

Define (from plan lines 788-833):
```kotlin
data class ResearchUIState(
    val totalPointsEarned: Int = 0,
    val analysts: List<AnalystUIState> = emptyList(),
    val activeResearch: List<ResearchTopicUI> = emptyList(),
    val availableResearch: List<ResearchTopicUI> = emptyList(),
    val completedResearch: List<ResearchTopicUI> = emptyList(),
)

data class TutorialUIState(
    val currentStep: TutorialStep? = TutorialStep.WELCOME,
    val tutorialComplete: Boolean = false,
    val targetTab: Int? = null,
    val stepTitle: String = "",
    val stepInstruction: String = "",
    val stepHint: String = "",
)
```

### Step 5.4: Research UI Mapper

**New file**: `ui/state/mappers/ResearchUiMapper.kt`

Maps `GameState.researchState` → `ResearchUIState` using `ResearchUpgradeRegistry` for display data.

### Step 5.5: Tutorial Banner

**New file**: `ui/components/TutorialBanner.kt`

Dismissible card composable:
- Step title (bold)
- Instruction text
- "Skip Tutorial" button → dispatches `GameEvent.SkipTutorial`

Place conditionally on:
- `StoreHomeScreen` — for WELCOME, WAIT_FOR_DELIVERY, STOCK_SHELVES, OPEN_STORE, FIRST_SALE, COMPLETE_TRANSACTION
- Inventory screen — for ORDER_FIRST_ITEM
- Staff screen — for HIRE_STAFF, HIRE_ANALYST

### Step 5.6: Feature Visibility Gating in UI

Throughout existing UI screens, wrap feature elements in visibility checks:

```kotlin
if (tutorialManager.isFeatureVisible(state, "feature_key")) { ... }
```

Or pass a `featureVisibility: Map<String, Boolean>` through UI state and check in composables.

**Key locations**:
- `StoreHomeScreen` — hide StoreSizeCard upgrade button until `store_X` researched
- `StaffScreen` — hide Manager hire until `manager_hiring` researched
- Inventory screen — hide bulk order button until `bulk_ordering` researched
- Delivery settings — hide extra truck slot until `extra_truck_slots` researched
- Pricing UI — hide markup sliders until `category_pricing` researched
- Register purchase — hide until `register_expansion` researched
- Staff scheduling — hide until `staff_scheduling` researched

### Step 5.7: Nav Tab Hint During Tutorial

Add a dot/badge to the bottom nav tab that the current tutorial step wants the player to navigate to:

| Step | Target Tab |
|------|-----------|
| ORDER_FIRST_ITEM | Inventory (index 1) |
| HIRE_STAFF, HIRE_ANALYST | Manage (index 2) |
| All others | Store (index 0) |

### Step 5.8: Research Completion Notification

When `checkCompletions()` completes a topic, show a toast or snackbar:
- "Research Complete: Dairy Section Study — 4 new items unlocked!"
- Store the completion event in a `justCompletedResearch: String?` field on ResearchState or UI state, clear after display

---

## Phase 6: Save Migration

**Goal**: Existing saves load cleanly. Players with progress get appropriate research auto-completed.

### Step 6.1: Post-Load Migration

In `GameEngine.loadState()` (line 324), after `repairMetricsIfNeeded()`, add migration logic:

```kotlin
private fun migrateToResearchSystem(saved: GameState): GameState {
    // Already migrated — has research state with data
    if (saved.researchState.researchedUpgrades.isNotEmpty()) return saved
    // New save — no migration needed
    if (saved.totalTransactionsCompleted == 0) return saved
    
    // Existing save with progress:
    val researched = mutableSetOf<String>()
    
    // 1. Auto-research gates for items already in inventory
    for (itemId in saved.inventory.keys) {
        val meta = itemMetadataCache.get(itemId) ?: continue
        val gate = meta.researchGate ?: continue
        researched.add(gate)
        // Also add all prerequisites recursively
        addPrerequisites(gate, researched)
    }
    
    // 2. Auto-research store size gates based on current store size
    if (saved.currentStoreSize >= StoreSize.SMALL_GROCERY) researched.add("store_small_grocery")
    if (saved.currentStoreSize >= StoreSize.GROCERY_STORE) researched.add("store_grocery")
    if (saved.currentStoreSize >= StoreSize.SUPERSTORE) researched.add("store_superstore")
    if (saved.currentStoreSize >= StoreSize.SUPERCENTER) researched.add("store_supercenter")
    
    // 3. Auto-research feature gates based on current capabilities
    if (saved.registers.size > 1) researched.add("register_expansion")
    if (saved.hiredEntityRegistry.getByDef(EntityDef.MANAGER).isNotEmpty()) researched.add("manager_hiring")
    // Add more based on feature usage...
    
    // 4. Tutorial complete for existing players
    return saved.copy(
        researchState = saved.researchState.copy(researchedUpgrades = researched),
        tutorialState = TutorialState(tutorialComplete = true, completedSteps = TutorialStep.entries.toSet()),
    )
}

private fun addPrerequisites(gateId: String, into: MutableSet<String>) {
    val upgrade = ResearchUpgradeRegistry.allUpgrades[gateId] ?: return
    for (prereq in upgrade.prerequisites) {
        if (into.add(prereq)) addPrerequisites(prereq, into)
    }
}
```

### Step 6.2: New Game Seeding

`GameEngine.buildSeededState()` — ensure new games start with:
- Only starter items (12 items with `researchGate == null`) in inventory
- Empty `ResearchState()`
- `TutorialState()` at WELCOME step
- No `currentTier` field (removed)

### Step 6.3: Debug Rich Seed Update

`GameEngine.buildDebugRichState()` — update to use research state:
```kotlin
researchState = ResearchState(
    researchedUpgrades = setOf("store_small_grocery", "store_grocery", "prod_dairy_basics", "prod_dairy_cheese", ...),
),
tutorialState = TutorialState(tutorialComplete = true, completedSteps = TutorialStep.entries.toSet()),
```

---

## Phase 7: Opportunistic Arch-Refactor — ViewModel Slimming

**Context**: GameViewModel is 601 lines. Adding tutorial + research event routing will grow it further unless we also extract.

### Step 7.1: Extract Event Router

Move the `when (event)` dispatch block from GameViewModel into a separate class:

**New file**: `ui/viewmodels/GameEventRouter.kt`

```kotlin
class GameEventRouter(private val engine: GameEngine) {
    fun dispatch(event: GameEvent) {
        when (event) {
            is GameEvent.BuyItem -> engine.buyItemToBackroom(event.itemId)
            is GameEvent.AssignAnalyst -> engine.assignAnalyst(event.entityId, event.assignment)
            // ... all events
        }
    }
}
```

This keeps `GameViewModel` focused on state flow and tick loop. New research/tutorial events go directly into the router.

### Step 7.2: Assess — Only Do If Natural

This refactor is purely opportunistic. If the ViewModel event block is small and manageable after adding research events (~5 new events), skip this step. Only extract if the event dispatch block grows past ~80 lines or becomes hard to navigate.

---

## Implementation Order

```
Phase 1: Item data pipeline (tier → researchGate + new items + affinity groups)
    ↓
Phase 2: Research domain layer (ResearchState, ResearchManager, Market Analyst, insight wiring)
    ↓
Phase 3: Remove tier system (ProgressionManager, ItemUnlockTier, tier UI)
    ↓
Phase 4: Tutorial system (TutorialState, TutorialManager, tick integration)
    ↓
Phase 5: UI (Research screen, tutorial banner, feature gating, nav changes)
    ↓
Phase 6: Save migration (existing saves, new game seed, debug seed)
    ↓
Phase 7: (Optional) ViewModel slimming
```

**Phase 1 and 2 can partially overlap** — ResearchState can be added to GameState in Phase 1 since TransactionEngine needs `state.researchState.researchedUpgrades` for item filtering.

**Phase 3 depends on Phase 1+2** — can't remove tiers until research filtering is in place.

**Phase 4 is independent of Phase 2-3** — tutorial only needs the TutorialState on GameState and a tick processor. Can be done in parallel with Phase 2 if two agents work simultaneously.

**Phase 5 depends on all prior phases** — UI needs all domain models in place.

**Phase 6 depends on all prior phases** — migration needs the full research registry and tutorial state.

---

## Constraints (from memory + plans)

- **Batch rules**: Never access `shelfStock`/`backroomStock` as mutable ints. Always `.copy()` batches.
- **Manager rules**: Never call manager methods from UI. Route through `GameEngine` events.
- **Money**: Use `Money` type for all currency. Research points are `Float`, not `Money`.
- **Tests**: Use `FakeItemDao` boilerplate. No Mockito. No trivial tests.
- **EntityDef**: `MARKET_ANALYST` is a companion object constant. Immutable.
- **Assignment cleanup**: Fired analyst → remove from `analystAssignments`. Completed topic → idle all assigned analysts.
- **Assignment storage**: `analystAssignments` lives in `ResearchState`, NOT on `HiredEntity`.
- **No `@Serializable` on `ResearchableUpgrade`**: The upgrade registry is code-defined, not saved. Only `ResearchState` (progress/assignments/completed) is serialized.
- **Serialization defaults**: All new `GameState` fields must have defaults so old saves load cleanly.
- **No save version bump needed**: All new fields have safe defaults via `ignoreUnknownKeys = true`.

---

## Files Created (New)

| File | Purpose |
|------|---------|
| `domain/research/ResearchState.kt` | ResearchState, AnalystAssignment data classes |
| `domain/research/ResearchManager.kt` | Point distribution, completion, assignment logic |
| `domain/research/ResearchUpgradeRegistry.kt` | All 55+ upgrade definitions |
| `domain/tutorial/TutorialState.kt` | TutorialState, TutorialStep enum |
| `domain/tutorial/TutorialManager.kt` | Step progression, feature visibility |
| `domain/tick/TutorialTickProcessor.kt` | Tutorial step checking per tick |
| `domain/tick/ResearchTickProcessor.kt` | Research point accumulation per tick |
| `ui/screens/research/ResearchScreen.kt` | Research tab UI |
| `ui/state/mappers/ResearchUiMapper.kt` | GameState → ResearchUIState mapping |
| `ui/components/TutorialBanner.kt` | Tutorial step banner composable |
| `test/.../research/ResearchManagerTest.kt` | Research system tests |
| `test/.../tutorial/TutorialManagerTest.kt` | Tutorial system tests |
| `test/.../items/ItemMetadataCacheTest.kt` | Item accessibility + affinity tests |

## Files Modified (Key)

| File | Changes |
|------|---------|
| `domain/GameStateData.kt` | Add `researchState`, `tutorialState`; remove `currentTier`, `justUnlockedTier` |
| `domain/GameEngine.kt` | Inject ResearchManager; add `assignAnalyst()`, `skipTutorial()`; update `fireEntity()`, `seedInventory()`, `loadState()`; remove `unlockNextTier()` |
| `domain/items/Item.kt` | Add `researchGate`, `affinityGroups` columns |
| `domain/items/ItemMetadata.kt` | Replace `tier` with `researchGate`, add `affinityGroups`, `substitutionGroup` |
| `domain/items/ItemMetadataCache.kt` | Add `isItemAccessible()`, `getAccessibleItemIds()`, `sharesAffinityGroup()`, `sharesSubstitutionGroup()`, affinity + substitution indexes |
| `domain/items/ItemDataLoader.kt` | Parse `researchGate`, `affinityGroups`, `substitutionGroup` from JSON |
| `domain/items/ItemCategory.kt` | Add 8 new categories |
| `domain/Entities/EntityDef.kt` | Add `MARKET_ANALYST`, update `allEntities` |
| `domain/Transactions/TransactionEngine.kt` | Replace tier filtering with research filtering; add affinity boost + substitution suppression to `weightedSample()` |
| `domain/tick/TickOrchestrator.kt` | Inject+call `TutorialTickProcessor`, `ResearchTickProcessor` |
| `ui/GameEvent.kt` | Add `AssignAnalyst`, `SkipTutorial`; remove `UnlockNextTier`, `DismissTierUnlock` |
| `ui/state/GameUiState.kt` | Add `research`, `tutorial` sub-states; simplify/remove `progression` |
| `ui/navigation/BottomNavBar.kt` | Add Research tab (conditional) |
| `ui/root/MainScreenPager.kt` | Add Research screen to pager |
| `domain/Screen.kt` | Add `RESEARCH` |
| `res/raw/items.json` | Add `researchGate`, `affinityGroups` to all items; add ~218 new items |
| `di/AppDatabase.kt` | Version 9 → 10, add migration for new columns |

## Files Deleted

| File | Reason |
|------|--------|
| `domain/items/ItemUnlockTier.kt` | Replaced by research gates |
| `domain/progression/ProgressionManager.kt` | Replaced by ResearchManager |
| `ui/components/cards/TierProgressCard.kt` | Tier system removed |
| `ui/screens/staff/UnlocksScreen.kt` | Tier system removed |
