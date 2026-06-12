# Plan: Research & Tutorial System

## Overview

Add a **research system** and a **tutorial system** to SuperstoreSimulator. The research system introduces a new Market Analyst staff role that generates research points from store activity, gating the *visibility* of upgrades and features. The tutorial system guides new players through core mechanics step-by-step, naturally leading into the research system as the gateway to advanced features.

**Research**: Points don't purchase upgrades — they reveal them. The player sees nothing for unresearched upgrades. Once researched, the upgrade becomes visible with its cost, description, and unlock button, and the player spends money to actually acquire it.

**Tutorial**: A step-by-step guided sequence that teaches core mechanics (ordering, stocking, selling, hiring) and culminates in hiring the player's first Market Analyst — bridging into the research system for all further progression.

---

## Current State

- Upgrades/features are always visible: store size upgrades in `StoreSizeCard`, tier unlocks in `TierProgressCard`, registers in `RegistersCard`, truck slots in delivery settings
- `TierProgressCard` is displayed on the home screen (lines 162–175 of `StoreHomeScreen.kt`) — to be removed
- `StoreSizeCard` stays on home screen
- Staff roles: `CASHIER`, `STOCKER`, `FRESH_HANDLER`, `MANAGER` — defined in `EntityDef` companion, hired via `HiredEntityRegistry`
- Staff scheduling: `StaffShift` with `startHour`/`durationHours`, checked via `isOnShift(hour)`
- No research, discovery, or knowledge-gating mechanic exists
- `UnlocksScreen` shows all tiers including locked ones — player sees everything upfront
- No tutorial, onboarding, or guided flow exists — `playerHasOpenedStore` in `AppUIState` is the only "first time" flag
- All UI elements visible from game start — new player sees registers, pricing, deliveries, staff scheduling immediately (overwhelming)
- Navigation: 5 bottom tabs (Home, Inventory, Staff, History, Metrics) via `HorizontalPager` in `MainActivity`

---

## Design Principles

1. **Research reveals, money buys.** Research points make an upgrade visible. The player then spends cash to purchase it. Two-step: discover → acquire.
2. **Hidden until discovered.** Unresearched upgrades don't appear in the UI at all (or show as a "???" teaser). The player shouldn't be able to plan around upgrades they haven't discovered.
3. **Event-driven generation.** Market Analysts don't idle — they observe store activity (transactions, stocking, spoilage, orders, lost customers) and extract insights. No store activity = no research points. Busier stores generate faster research.
4. **Single currency.** One pool of research points, not per-category or per-department.

---

## Tutorial System

### Philosophy

The tutorial is a **progressive disclosure** system. Instead of showing everything and explaining it, the game starts stripped down — only the essentials visible — and the tutorial walks the player through each mechanic as it becomes relevant. The tutorial naturally terminates by introducing the Market Analyst, handing off progression gating to the research system.

### Tutorial State

```kotlin
data class TutorialState(
    val currentStep: TutorialStep = TutorialStep.WELCOME,
    val completedSteps: Set<TutorialStep> = emptySet(),
    val tutorialComplete: Boolean = false,
    val dismissedHints: Set<String> = emptySet(),  // hint IDs player has seen and dismissed
)
```

Added to `GameState`:
```kotlin
val tutorialState: TutorialState = TutorialState(),
```

### Tutorial Steps

The tutorial is a linear sequence. Each step has a trigger condition (when to show it), an objective (what the player must do), and a completion condition (how the game knows they did it).

```kotlin
enum class TutorialStep(
    val displayTitle: String,
    val instruction: String,
    val hintText: String,        // short contextual hint shown on the relevant screen
) {
    WELCOME(
        "Welcome to Your Store!",
        "Let's get your store set up. First, order some inventory to stock your shelves.",
        "Tap an item to place your first order."
    ),
    ORDER_FIRST_ITEM(
        "Order Inventory",
        "Go to the Inventory tab and order a case pack of any item.",
        "Tap 'Order' on any item to buy a case pack."
    ),
    WAIT_FOR_DELIVERY(
        "Wait for Delivery",
        "Your order is on the next truck. Open the store and wait for it to arrive!",
        "Open the store and play until your truck arrives."
    ),
    STOCK_SHELVES(
        "Stock the Shelves",
        "Items are in your backroom. Set your role to Stocker to put them on shelves, or hire a Stocker.",
        "Switch to Stocker role or hire a Stocker from the Staff tab."
    ),
    OPEN_STORE(
        "Open for Business",
        "Your shelves have stock! Open the store to let customers in.",
        "Tap the store state button to open."
    ),
    FIRST_SALE(
        "Your First Sale",
        "Customers are browsing. Set your role to Cashier or hire one to ring them up.",
        "Switch to Cashier role or hire a Cashier."
    ),
    COMPLETE_TRANSACTION(
        "Ring Up a Customer",
        "A customer is ready to check out! Complete the transaction.",
        "Process a transaction at the register."
    ),
    HIRE_STAFF(
        "Hire Help",
        "You can't do everything yourself. Go to the Staff tab and hire a Cashier or Stocker.",
        "Tap 'Hire' on the Staff tab."
    ),
    HIRE_ANALYST(
        "Hire a Market Analyst",
        "Market Analysts study your store's activity and discover new upgrades. Hire one to unlock new possibilities!",
        "Hire a Market Analyst from the Staff tab."
    ),
    TUTORIAL_COMPLETE(
        "You're on Your Own!",
        "Your analyst will discover new upgrades as your store operates. Check the Research tab to spend research points on discoveries.",
        ""
    ),
}
```

### Step Progression Logic

| Step | Trigger (when to show) | Completion (when to advance) |
|------|----------------------|------------------------------|
| `WELCOME` | Game start (fresh save) | Auto-advance after 3 seconds or tap |
| `ORDER_FIRST_ITEM` | After WELCOME | Any item ordered (single or bulk) |
| `WAIT_FOR_DELIVERY` | After ORDER_FIRST_ITEM | Truck delivery received (any items in backroom) |
| `STOCK_SHELVES` | After WAIT_FOR_DELIVERY | Any item has shelfStock > 0 |
| `OPEN_STORE` | After STOCK_SHELVES, store is CLOSED | Store state becomes OPEN |
| `FIRST_SALE` | After OPEN_STORE | pendingCustomers > 0 (auto-advance — just informational) |
| `COMPLETE_TRANSACTION` | After FIRST_SALE | totalTransactionsCompleted > 0 |
| `HIRE_STAFF` | After COMPLETE_TRANSACTION | hiredEntityRegistry.totalCount() >= 1 |
| `HIRE_ANALYST` | After HIRE_STAFF | Any Market Analyst hired |
| `TUTORIAL_COMPLETE` | After HIRE_ANALYST | Auto-complete, set `tutorialComplete = true` |

Some steps may be skipped if the player naturally does things out of order (e.g., hires staff before completing a transaction). The completion check runs every tick — if the condition is already met when a step activates, it immediately advances to the next unmet step.

### Skip Logic

If a player has already completed the action before reaching that tutorial step, skip it:

```kotlin
fun resolveCurrentStep(state: GameState): TutorialStep? {
    if (state.tutorialState.tutorialComplete) return null
    val steps = TutorialStep.entries
    for (step in steps) {
        if (step in state.tutorialState.completedSteps) continue
        if (isStepCompleted(step, state)) {
            // Mark as completed, continue to next
            continue
        }
        return step  // first uncompleted step
    }
    return null  // all done
}
```

### UI Presentation

#### Tutorial Banner

A dismissible card/banner at the top of the relevant screen showing:
- Step title (bold)
- Instruction text
- Optional "Skip Tutorial" button (skips all remaining steps, sets `tutorialComplete = true`)

The banner appears on whichever screen is relevant to the current step:
| Step | Screen |
|------|--------|
| WELCOME | Home |
| ORDER_FIRST_ITEM | Inventory |
| WAIT_FOR_DELIVERY | Home |
| STOCK_SHELVES | Home |
| OPEN_STORE | Home |
| FIRST_SALE | Home |
| COMPLETE_TRANSACTION | Home |
| HIRE_STAFF | Staff |
| HIRE_ANALYST | Staff |
| TUTORIAL_COMPLETE | Home (one-time celebration, then dismissed) |

#### Navigation Hints

During tutorial, the bottom nav bar highlights the tab the player should go to next (subtle pulse or dot indicator on the relevant tab icon). E.g., when step is ORDER_FIRST_ITEM, the Inventory tab gets a hint dot.

#### Feature Gating During Tutorial

Before the tutorial is complete, some features are hidden to avoid overwhelming the player:

| Feature | Visible During Tutorial? | When Revealed |
|---------|------------------------|---------------|
| Order items | After WELCOME step | ORDER_FIRST_ITEM step |
| Store open/close | After STOCK_SHELVES | OPEN_STORE step |
| Player role buttons | After OPEN_STORE | FIRST_SALE step |
| Hire staff | After COMPLETE_TRANSACTION | HIRE_STAFF step |
| Market Analyst hire | After HIRE_STAFF | HIRE_ANALYST step |
| Research tab | After tutorial complete | Always visible post-tutorial |
| Pricing UI | After tutorial complete | Research-gated |
| Delivery settings | After tutorial complete | Research-gated |
| Metrics tab | After tutorial complete | Always visible post-tutorial |
| Staff scheduling | After tutorial complete | Research-gated |
| Registers | After tutorial complete | Research-gated |
| Bulk ordering | After tutorial complete | Research-gated |

The idea: tutorial gates the basics, research gates the advanced. Tutorial is ~15-30 minutes of play. Research is the rest of the game.

### Tutorial Manager

Location: `domain/tutorial/TutorialManager.kt`

Pure function style — takes `GameState`, returns `GameState`.

| Method | Purpose |
|--------|---------|
| `checkStepCompletion(state)` | Check if current step's completion condition is met, advance if so |
| `getCurrentStep(state)` | Return current tutorial step, or null if complete |
| `skipTutorial(state)` | Mark all steps complete, set `tutorialComplete = true` |
| `dismissHint(state, hintId)` | Record that player has seen a hint |
| `isFeatureVisible(state, feature)` | Check if a feature should be shown given tutorial + research state |

### Interaction with Research System

The tutorial and research system form a two-phase gating pipeline:

```
Game Start → [Tutorial gates basics] → Tutorial Complete → [Research gates advanced] → Full Game
```

- During tutorial: only basic features visible (ordering, stocking, selling, hiring)
- After tutorial: research tab appears, player can spend points to discover advanced features
- Research requires Market Analyst — the tutorial's final step ensures the player has one

### Interaction with Existing Saves

Existing saves (pre-tutorial) on load:
- If `totalTransactionsCompleted > 0` → set `tutorialComplete = true` (player clearly knows how to play)
- Otherwise, start tutorial from WELCOME (rare edge case — saved before first transaction)

---

## Market Analyst Staff Role

### EntityDef

```kotlin
val MARKET_ANALYST = EntityDef(
    key = "market_analyst",
    displayName = "Market Analyst",
    cost = Money(3_000),              // $30 hiring cost — more than cashier, less than manager
    description = "Generates research points while on shift. Research unlocks new upgrades and features.",
    roleDescription = "Researches upgrades",
    icon = Icons.Default.Search,      // or Lightbulb / Science
    baseWage = Money(900),            // $9/hr — between regular staff ($7.25) and manager ($12)
)
```

Add to `EntityDef.allEntities` list.

### Research Point Generation — Event-Driven, Per-Assignment

Market Analysts don't passively generate points. They **observe store activity** and derive insights from it. Points are generated when store events happen *while an assigned analyst is on shift*. **Idle analysts produce nothing.**

#### Triggering Events

| Store Event | Base Points | Where Triggered |
|-------------|------------|-----------------|
| **Transaction completed** | `TRANSACTION_INSIGHT` | `TransactionEngine.completeTransaction()` |
| **Case pack stocked to shelf** | `STOCK_INSIGHT` | Stocker/Fresh Handler action in `GameEngine.tick()` |
| **Item spoiled** | `SPOILAGE_INSIGHT` | `SpoilageManager` batch expiration |
| **Truck delivery received** | `DELIVERY_INSIGHT` | Truck arrives and unloads in `GameEngine` |
| **Customer lost (walked away)** | `LOST_CUSTOMER_INSIGHT` | Customer leaves due to no open register / queue full |

#### Point Calculation — Per Topic

When a triggering event fires, group on-shift analysts by assignment and process each group:

```kotlin
fun distributeInsightPoints(
    baseEventPoints: Float,
    onShiftAnalysts: List<HiredEntity>,
    assignments: Map<Int, AnalystAssignment>,
    state: GameState
): GameState {
    var updated = state

    // Group assigned analysts by their topic
    val byTopic = mutableMapOf<String, MutableList<HiredEntity>>()
    val consultants = mutableListOf<HiredEntity>()

    for (analyst in onShiftAnalysts) {
        when (val assignment = assignments[analyst.id]) {
            is AnalystAssignment.Research -> byTopic.getOrPut(assignment.upgradeId) { mutableListOf() }.add(analyst)
            is AnalystAssignment.Consulting -> consultants.add(analyst)
            null -> { /* idle — skip */ }
        }
    }

    // Research: diminishing returns per topic (sqrt scaling)
    for ((upgradeId, analysts) in byTopic) {
        val effectiveCount = sqrt(analysts.size.toFloat())
        val avgThroughput = analysts.map { it.throughputWeight * it.levelMultiplier }.average().toFloat()
        val topicPoints = baseEventPoints * effectiveCount * avgThroughput
        // Add to researchProgress[upgradeId]
        updated = addResearchProgress(updated, upgradeId, topicPoints)
    }

    // Consulting: each analyst independently converts points to cash
    for (analyst in consultants) {
        val cashPoints = baseEventPoints * analyst.throughputWeight * analyst.levelMultiplier
        val cashEarned = Money((cashPoints * CONSULTING_CASH_PER_POINT.cents).toLong())
        updated = updated.copy(money = updated.money + cashEarned)
    }

    return updated
}
```

**Diminishing returns are per-topic**: 2 analysts on same topic = ~1.41x speed. But 2 analysts on different topics = full speed on both. Incentivizes spreading analysts across active research. Consulting analysts work independently (no diminishing returns — each converts their own output to cash).

#### Tuning Constants

| Constant | Value | Notes |
|----------|-------|-------|
| `TRANSACTION_INSIGHT` | 0.3 | Main driver — transactions are the core loop |
| `STOCK_INSIGHT` | 0.05 | Frequent but low-value — stocking happens often |
| `SPOILAGE_INSIGHT` | 0.2 | Learning from waste — less frequent, higher value |
| `DELIVERY_INSIGHT` | 1.5 | Supply chain insight — infrequent, high value |
| `LOST_CUSTOMER_INSIGHT` | 0.15 | Demand analysis — learning from failures |

#### Earning Rate Analysis (Per Analyst, Per Topic)

Base points per day from store activity (1 base analyst assigned to a single topic):

Typical early game (~20 transactions/day, ~50 stock actions, ~5 spoilage events, ~0.3 deliveries/day avg, ~2 lost customers):
- Transactions: 20 × 0.3 = 6.0 pts
- Stocking: 50 × 0.05 = 2.5 pts
- Spoilage: 5 × 0.2 = 1.0 pts
- Deliveries: 0.3 × 1.5 = 0.45 pts
- Lost customers: 2 × 0.15 = 0.3 pts
- **~10.25 pts/day** per topic with 1 base analyst assigned

Late game (~100 transactions/day, ~200 stock actions, ~20 spoilage, ~0.7 deliveries/day avg, ~10 lost customers):
- **~46.55 pts/day** per topic with 1 base analyst (before tier/level multipliers)

**Multi-analyst scenarios:**

| Setup | Topic A Rate | Topic B Rate |
|---|---|---|
| 1 analyst on A | 10.25 pts/day | — |
| 2 analysts on A | ~14.5 pts/day (√2) | — |
| 1 analyst on A, 1 on B | 10.25 pts/day | 10.25 pts/day |
| 2 on A, 1 on B | ~14.5 pts/day | 10.25 pts/day |
| 1 on consulting | — (earns ~$50/day) | — |

Research naturally accelerates as the store grows — busier stores generate more data for analysts to study. Early research (8–10 pts) takes ~1 day with a dedicated analyst. Late research (100 pts) takes ~2–3 days.

Research progress is tracked per-topic as `Float` in `ResearchState.researchProgress`, displayed as progress bars in UI.

### XP and Leveling

Market Analysts gain XP when they process an insight event (same pattern as other staff gaining XP on action). Higher levels → better insight extraction via `levelMultiplier`.

Each triggering event grants XP to all on-shift analysts (distributed via `grantXpDistributed`). Transaction events grant more XP than stocking events.

### Tier Promotion

Market Analysts promote through the standard `Tier.BASE → FAST → MANAGER` path. Higher tiers increase `throughputWeight`, meaning each insight event yields more points:
- **BASE**: 1.0x throughput
- **FAST**: 1.5x throughput
- **MANAGER tier**: 2.0x throughput

---

## Researchable Upgrades

### Data Model

```kotlin
enum class ResearchCategory {
    STORE_EXPANSION,      // store size upgrades, registers
    PRODUCT_LINES,        // tier unlocks (new item categories)
    OPERATIONS,           // truck slots, bulk ordering, auto-order
    PRICING,              // pricing system features (category markup, markdowns)
    STAFF_MANAGEMENT,     // manager hiring, auto-hire, scheduling features
}

data class ResearchableUpgrade(
    val id: String,                           // unique key, e.g. "prod_dairy_basics"
    val displayName: String,                  // "Dairy Section Study"
    val description: String,                  // shown after researching
    val teaserDescription: String,            // vague hint shown before researching (optional)
    val category: ResearchCategory,
    val researchCost: Int,                    // research points to discover
    val prerequisites: List<String> = emptyList(),  // ids of upgrades that must be researched first
    val gateCheck: ((GameState) -> Boolean)? = null, // additional condition (e.g., revenue threshold)
)
```

### Item Data Pipeline — `researchGate` Field

Replace the `tier` field on items with `researchGate`:

| Layer | Old Field | New Field | Default |
|-------|-----------|-----------|---------|
| `items.json` | `"tier": "TIER_1"` | `"researchGate": "prod_breakfast"` | absent = null (starter item) |
| `Item.kt` (Room entity) | `val tier: String` | `val researchGate: String? = null` | null |
| `ItemMetadata.kt` | `val tier: ItemUnlockTier` | `val researchGate: String? = null` | null |
| `ItemDataLoader.kt` | parse tier string → enum | parse researchGate string | null |
| `ItemMetadataCache.kt` | filter by current tier | filter by `researchedUpgrades` | show all starter items |

Items with `researchGate = null` are always available (starter items). Items with a `researchGate` value are hidden until `researchState.researchedUpgrades.contains(researchGate)`.

**Filtering**: `ItemMetadataCache` gains a method `getAccessibleItems(researchedUpgrades: Set<String>)` that returns only items whose `researchGate` is null or in the set. This replaces the old `getItemsForTier()` filtering. Used by:
- Inventory screen item list
- `TransactionEngine.weightedSample()` (customers only buy accessible items)
- Order placement (can't order items not yet researched)
- Bulk order (only includes accessible items)

```kotlin
// In ItemMetadataCache
fun isItemAccessible(itemId: Int, researchedUpgrades: Set<String>): Boolean {
    val meta = get(itemId) ?: return false
    return meta.researchGate == null || meta.researchGate in researchedUpgrades
}
```

### Research State

```kotlin
@Serializable
sealed interface AnalystAssignment {
    @Serializable data class Research(val upgradeId: String) : AnalystAssignment
    @Serializable data object Consulting : AnalystAssignment
}

data class ResearchState(
    val researchProgress: Map<String, Float> = emptyMap(),  // upgradeId → accumulated points
    val totalPointsEarned: Float = 0f,                      // lifetime, never decreases (for metrics)
    val researchedUpgrades: Set<String> = emptySet(),       // ids of completed research
    val analystAssignments: Map<Int, AnalystAssignment> = emptyMap(),  // entityId → assignment
)
```

Added to `GameState`:
```kotlin
val researchState: ResearchState = ResearchState(),
```

### Research Flow — Assignment-Based

Each Market Analyst is **individually assigned** to a research topic, consulting, or left idle. Points flow directly to the assigned topic. No global pool.

1. **Player assigns analysts**: On the Research Screen, player assigns each hired analyst to a specific research topic or consulting
2. **Point generation**: On store events, each on-shift assigned analyst generates points
   - **Research assignment** → points flow into `researchProgress[upgradeId]`
   - **Consulting assignment** → small cash generated (event points × consulting rate)
   - **Idle (no assignment)** → nothing generated, still costs wages
3. **Auto-completion**: When `researchProgress[id] >= researchCost`, topic auto-completes → added to `researchedUpgrades`, assigned analysts become idle
4. **Visibility unlocked**: UI checks `researchedUpgrades.contains(id)` before showing any upgrade option

**Multiple analysts on same topic**: Diminishing returns apply per-topic, not globally. If 2 analysts are assigned to the same topic, use sqrt scaling on the count for that topic:

```kotlin
// Per store event, for each unique assigned upgradeId:
val analystsOnTopic = onShiftAnalysts.filter { assignment[it.id] == Research(upgradeId) }
val effectiveCount = sqrt(analystsOnTopic.size.toFloat())
val avgThroughput = analystsOnTopic.map { it.throughputWeight * it.levelMultiplier }.average()
val topicPoints = baseEventPoints * effectiveCount * avgThroughput
researchProgress[upgradeId] += topicPoints
```

Two analysts on one topic = ~1.41x speed, not 2x. But two analysts on *different* topics = full speed on both. Player is rewarded for spreading analysts across multiple active topics.

### Consulting Assignment (Earn Cash)

Analysts assigned to consulting convert insight events into small cash instead of research points. **Not profitable** — designed as a less-bad alternative to idle, not a money-making strategy.

```kotlin
// Per store event, for each on-shift consulting analyst:
val cashEarned = baseEventPoints * analyst.throughputWeight * analyst.levelMultiplier * CONSULTING_CASH_PER_POINT
state.money += Money((cashEarned * 100).toLong())  // convert to cents
```

**Consulting rate**: `$5.00 per research-point-equivalent`

| | Analyst Cost | Consulting Earnings | Net |
|---|---|---|---|
| Early game (8hr shift) | $72/day wage | ~$50/day (10 pts equiv × $5) | **-$22/day** |
| Late game (8hr shift) | $72/day wage | ~$235/day (47 pts equiv × $5) | **+$163/day** |

Early game: consulting is a net loss but better than idle ($72 loss). Late game with high-level analysts: can become slightly profitable. Player trades progression speed for cash.

Constants:
```kotlin
val CONSULTING_CASH_PER_POINT = Money(500)  // $5.00 per research-point-equivalent
```

### Research Tree

Research has a flat list with prerequisites. Not a full tree — just "must research X before Y is visible."

#### Store Expansion
| ID | Name | Cost | Prerequisites | What it reveals |
|----|------|------|---------------|-----------------|
| `store_small_grocery` | "Expansion Study: Small Grocery" | 10 | — | Small Grocery store size upgrade |
| `store_grocery` | "Expansion Study: Grocery Store" | 25 | `store_small_grocery` | Grocery Store upgrade |
| `store_superstore` | "Expansion Study: Superstore" | 50 | `store_grocery` | Superstore upgrade |
| `store_supercenter` | "Expansion Study: Supercenter" | 100 | `store_superstore` | Supercenter upgrade |
| `register_expansion` | "Register Capacity Study" | 15 | — | Ability to purchase additional registers |

#### Product Lines (Granular Item Unlocks)

Items are now gated by individual research entries, not monolithic tiers. Each research unlocks a small thematic group of items (~3-8 items). The old `tier` field in `items.json` is replaced by a `researchGate` field pointing to a research ID.

**Starter items** (no research needed — `researchGate: null`):

| Items | Category | Count |
|-------|----------|-------|
| Bread, White Rice, Spaghetti, Canned Black Beans, Mac & Cheese, Ramen | GROCERY | 6 |
| Potato Chips, Pretzels, Granola Bar | SNACKS | 3 |
| Coca-Cola 2L, Water 24-Pack, Apple Juice | DRINKS | 3 |
| **Total starter items** | | **12** |

Enough to run a basic convenience store. Everything else requires research.

**Grocery research chain:**

| ID | Name | Cost | Prerequisites | Items Unlocked |
|----|------|------|---------------|----------------|
| `prod_breakfast` | "Breakfast Aisle Study" | 5 | — | Cereal, Oatmeal, Pancake Mix (3) |
| `prod_condiments` | "Condiment Market Study" | 5 | — | Peanut Butter, Jam, Ketchup, Mustard, Mayo, Hot Sauce (6) |
| `prod_baking` | "Baking Supplies Study" | 6 | `prod_breakfast` | Sugar, Flour, Salt, Pepper, Vegetable Oil (5) |
| `prod_canned` | "Canned Goods Study" | 6 | — | Canned Tomatoes, Chicken Noodle Soup, Canned Sweet Corn, Canned Green Beans, Chicken Broth (5) |
| `prod_premium_grocery` | "Premium Grocery Study" | 10 | `prod_condiments` | Soy Sauce, Honey, Applesauce, Olive Oil, Brown Rice, Whole Wheat Pasta (6) |
| `prod_organic` | "Organic & Specialty Study" | 15 | `prod_premium_grocery` | Organic Peanut Butter, Quinoa, Almond Butter, Balsamic Vinegar, Coconut Oil (5) |
| `prod_gourmet` | "Gourmet Foods Study" | 25 | `prod_organic` | Truffle Oil, Imported Penne Rigate, Artisanal Crackers, Sun-Dried Tomatoes (4) |

**Snack research chain:**

| ID | Name | Cost | Prerequisites | Items Unlocked |
|----|------|------|---------------|----------------|
| `prod_snack_variety` | "Snack Variety Study" | 5 | — | Tortilla Chips, Popcorn, Peanuts, Crackers, Sugar Cookie (5) |
| `prod_candy_treats` | "Candy & Treats Study" | 8 | `prod_snack_variety` | Chocolate Bar, Gummy Bears, Cheese Puffs (3) |
| `prod_premium_snacks` | "Premium Snacks Study" | 10 | `prod_snack_variety` | Trail Mix, Rice Cakes, Mixed Nuts, Organic Granola Bars (4) |

**Drink research chain:**

| ID | Name | Cost | Prerequisites | Items Unlocked |
|----|------|------|---------------|----------------|
| `prod_beverages` | "Beverage Expansion Study" | 5 | — | Orange Juice, Soda 12-Pack, Lemonade, Ground Coffee (4) |
| `prod_specialty_drinks` | "Specialty Drinks Study" | 10 | `prod_beverages` | Herbal Tea, Energy Drink, Sparkling Water, Cold Brew, Kombucha (5) |

**Dairy research chain:**

| ID | Name | Cost | Prerequisites | Items Unlocked |
|----|------|------|---------------|----------------|
| `prod_dairy_basics` | "Dairy Section Study" | 8 | — | Milk, Eggs, Salted Butter, Sour Cream (4) |
| `prod_dairy_cheese` | "Cheese & Yogurt Study" | 10 | `prod_dairy_basics` | Cheddar Cheese, Mozzarella, Cream Cheese, Strawberry Yogurt, Greek Yogurt, Heavy Cream (6) |

**Fresh & Frozen research chain:**

| ID | Name | Cost | Prerequisites | Items Unlocked |
|----|------|------|---------------|----------------|
| `prod_frozen_basics` | "Frozen Foods Study" | 12 | `prod_dairy_basics` | Frozen Pizza, Frozen Vegetables, Frozen Fries, Vanilla Ice Cream, Chicken Nuggets (5) |
| `prod_frozen_meals` | "Frozen Meals Study" | 10 | `prod_frozen_basics` | Frozen Burritos, Frozen Waffles, Frozen Mac & Cheese, Breakfast Sandwiches, Mango Sorbet (5) |
| `prod_bakery` | "In-Store Bakery Study" | 12 | `prod_dairy_basics` | Butter Croissant, Cinnamon Roll, Glazed Donut, Chocolate Chip Muffin, Sesame Bagel (5) |
| `prod_artisan_bread` | "Artisan Bread Study" | 8 | `prod_bakery` | Sourdough Loaf, French Baguette, Danish Pastry (3) |
| `prod_produce` | "Produce Section Study" | 12 | — | Bananas, Fuji Apples, Green Lettuce, Fresh Carrots, Yellow Onion, Avocado (6) |
| `prod_premium_produce` | "Premium Produce Study" | 10 | `prod_produce` | Ripe Tomato, Broccoli, Strawberries, Red Grapes, Baby Spinach, Bell Peppers (6) |

**Meat & Deli research chain:**

| ID | Name | Cost | Prerequisites | Items Unlocked |
|----|------|------|---------------|----------------|
| `prod_meat_counter` | "Meat Counter Study" | 15 | `prod_dairy_basics` | Chicken Breast, Ground Beef, Pork Chops, Bacon (4) |
| `prod_premium_meat` | "Premium Meat & Seafood Study" | 20 | `prod_meat_counter` | Ribeye Steak, Salmon Fillet, Shrimp, Deli Turkey (4) |

**Non-food departments:**

| ID | Name | Cost | Prerequisites | Items Unlocked |
|----|------|------|---------------|----------------|
| `prod_health_beauty` | "Health & Beauty Study" | 12 | — | Shampoo, Conditioner, Deodorant, Body Wash, Face Wash, Toothpaste (6) |
| `prod_household` | "Household Goods Study" | 12 | — | Laundry Detergent, Dish Soap, Hand Soap, Paper Towels, Toilet Paper, Trash Bags (6) |
| `prod_pharmacy` | "Pharmacy Study" | 15 | `prod_health_beauty` | Aspirin, Cold Medicine, Daily Vitamins, Antacid Tablets, Bandages (5) |
| `prod_electronics` | "Electronics Study" | 20 | `prod_household` | Headphones, USB Cable, Screen Protector, Portable Charger (4) |

**Total: 24 product research entries** unlocking 124 items in groups of 3-6.

> **Future expansion**: 31 additional product-line gates and ~218 new items (across existing and 8 new categories) are planned in [`plan-futureItems.md`](plan-futureItems.md). That document also defines 28 item affinity groups for realistic basket generation.

#### Relationship to Old Tier System

The old `ItemUnlockTier` system (TIER_1/2/3/GM with revenue gates + unlock costs) is **removed**. There are no more monolithic tier unlocks. Each product research entry replaces what was formerly a tier gate.

- Revenue gates and unlock costs → removed from progression
- `TierProgressCard`, `UnlocksScreen` tier cards → removed
- `ItemUnlockTier` enum → removed (or repurposed for backward-compat migration only)
- Item visibility is now: `researchState.researchedUpgrades.contains(item.researchGate)` OR `item.researchGate == null` (starter items)
- `ProgressionManager.unlockNextTier()` → removed
- `GameState.currentTier` → removed (migrate existing saves, see Migration section)

The `ProgressionUIState` simplifies to just tracking `totalRevenue` for display purposes.

#### Operations
| ID | Name | Cost | Prerequisites | What it reveals |
|----|------|------|---------------|-----------------|
| `bulk_ordering` | "Bulk Procurement Study" | 12 | `tier_2_dairy` | Bulk ordering feature |
| `extra_truck_slots` | "Logistics Optimization" | 15 | — | Ability to purchase extra truck delivery slots |
| `fresh_auto_order` | "Fresh Inventory Automation" | 18 | `tier_3_fresh` | Fresh auto-ordering config |
| `early_truck` | "Express Delivery Research" | 10 | `extra_truck_slots` | Early truck request option |

#### Pricing
| ID | Name | Cost | Prerequisites | What it reveals |
|----|------|------|---------------|-----------------|
| `category_pricing` | "Category Pricing Analysis" | 15 | — | Category-level markup sliders |
| `item_pricing` | "Item-Level Pricing Study" | 20 | `category_pricing` | Per-item price overrides |
| `default_markup` | "Store Pricing Strategy" | 10 | `category_pricing` | Store-wide default markup slider |

#### Staff Management
| ID | Name | Cost | Prerequisites | What it reveals |
|----|------|------|---------------|-----------------|
| `manager_hiring` | "Management Structure Study" | 20 | — | Ability to hire managers |
| `auto_hire` | "Automated HR Study" | 25 | `manager_hiring` | Auto-hire budget setting |
| `staff_scheduling` | "Workforce Planning Study" | 12 | — | Shift scheduling UI |

---

## Gate Checks (Additional Conditions)

Some research options should only appear after meeting game-state conditions, independent of prerequisites:

| Research ID | Gate Check | Rationale |
|-------------|-----------|-----------|
| `prod_dairy_basics` | `totalRevenue >= $2,500` | Don't show dairy research until store has some traction |
| `prod_frozen_basics` | `totalRevenue >= $10,000` | Frozen requires revenue maturity |
| `prod_meat_counter` | `totalRevenue >= $10,000` | Meat counter is a significant investment |
| `prod_pharmacy` | `totalRevenue >= $25,000` | Pharmacy requires established store |
| `prod_electronics` | `totalRevenue >= $25,000` | Electronics is a late-game department |
| `manager_hiring` | `hiredEntityRegistry.totalCount() >= 3` | Need some staff before needing managers |
| `store_small_grocery` | `totalRevenue >= $1,000` | Don't show expansion until store is running |

Gate checks determine whether the research *appears in the research screen at all*. Without the gate being met, the research is invisible — not locked, invisible.

---

## UI Changes

### Home Screen — Remove Tier Progress Card

Remove the `TierProgressCard` composable call from `StoreHomeScreen.kt` (lines 161–176). Keep `StoreSizeCard` in place.

Tier progression is replaced by the research system — no separate unlocks screen needed.

### New: Research Screen

New tab/screen accessible from the main navigation. Shows:

1. **Analyst Roster**: list of all hired Market Analysts with current assignment, tier, level, on-shift status
   - Each analyst has a dropdown/picker: "Idle" | "Consulting" | list of available research topics
   - Reassignment takes effect immediately
2. **Active Research**: topics with analysts assigned or progress > 0
   - Each shows: name, progress bar (X / Y pts), assigned analyst count, estimated time to completion
   - Topics auto-complete when progress hits cost
3. **Available Research**: topics the player can assign analysts to, grouped by `ResearchCategory`
   - Only shows upgrades where: prerequisites met AND gate check passes AND not already researched
   - Each entry shows: name, teaser description, research point cost, "Assign Analyst" button
4. **Completed Research**: collapsible section showing what's been discovered

### Upgrade Visibility Gating

Every upgrade in the game checks `researchState.researchedUpgrades.contains(relevantId)` before rendering:

**Feature upgrades:**

| UI Element | Research Gate |
|-----------|--------------|
| `StoreSizeCard` upgrade button (Small Grocery) | `store_small_grocery` |
| `StoreSizeCard` upgrade button (Grocery Store) | `store_grocery` |
| `StoreSizeCard` upgrade button (Superstore) | `store_superstore` |
| `StoreSizeCard` upgrade button (Supercenter) | `store_supercenter` |
| Register purchase button | `register_expansion` |
| Bulk order dialog | `bulk_ordering` |
| Extra truck slot purchase | `extra_truck_slots` |
| Early truck request | `early_truck` |
| Fresh auto-order config | `fresh_auto_order` |
| Category markup sliders | `category_pricing` |
| Item price override sliders | `item_pricing` |
| Default markup slider | `default_markup` |
| Manager hire button in staff screen | `manager_hiring` |
| Auto-hire budget setting | `auto_hire` |
| Schedule screen / shift sliders | `staff_scheduling` |

**Item visibility:**

| Item Group | Research Gate |
|-----------|--------------|
| 12 starter items | Always visible (no gate) |
| Cereal, Oatmeal, Pancake Mix | `prod_breakfast` |
| PB, Jam, Ketchup, Mustard, Mayo, Hot Sauce | `prod_condiments` |
| Sugar, Flour, Salt, Pepper, Vegetable Oil | `prod_baking` |
| Canned goods + soups | `prod_canned` |
| Soy Sauce, Honey, Olive Oil, etc. | `prod_premium_grocery` |
| Organic/specialty pantry | `prod_organic` |
| Gourmet items | `prod_gourmet` |
| Tortilla Chips, Popcorn, etc. | `prod_snack_variety` |
| Chocolate Bar, Gummy Bears, etc. | `prod_candy_treats` |
| Trail Mix, Mixed Nuts, etc. | `prod_premium_snacks` |
| OJ, Soda 12-Pack, Coffee, etc. | `prod_beverages` |
| Herbal Tea, Energy Drink, etc. | `prod_specialty_drinks` |
| Milk, Eggs, Butter, Sour Cream | `prod_dairy_basics` |
| Cheeses, Yogurt, Cream | `prod_dairy_cheese` |
| Frozen basics | `prod_frozen_basics` |
| Frozen meals | `prod_frozen_meals` |
| Bakery basics | `prod_bakery` |
| Artisan breads | `prod_artisan_bread` |
| Basic produce | `prod_produce` |
| Premium produce | `prod_premium_produce` |
| Basic meat | `prod_meat_counter` |
| Premium meat & seafood | `prod_premium_meat` |
| Health & beauty | `prod_health_beauty` |
| Household goods | `prod_household` |
| Pharmacy | `prod_pharmacy` |
| Electronics | `prod_electronics` |

For gated features/items that aren't yet researched: the UI element simply doesn't render. No "locked" icon, no hint. The player discovers these through the Research screen.

Items in inventory that haven't been researched don't appear in the inventory list, can't be ordered, and aren't included in transaction item pools. The item filtering happens at the `ItemMetadataCache` query level — unresearched items are excluded from all game systems.

### UnlocksScreen Removal

The old `UnlocksScreen` with tier cards is **removed entirely**. Item unlocking is now done through the Research screen. The `StaffAndUnlocksScreen` tab that combined staff + unlocks reverts to just a staff screen.

---

## ResearchManager

Location: `domain/research/ResearchManager.kt`

Pure function style — takes `GameState`, returns `GameState`.

| Method | Purpose |
|--------|---------|
| `distributeInsightPoints(state, baseEventPoints, onShiftAnalysts)` | Route points to assigned topics / consulting cash. Groups by assignment, applies sqrt scaling per topic |
| `checkCompletions(state)` | Scan `researchProgress` for any topic that hit its cost. Move to `researchedUpgrades`, idle those analysts |
| `assignAnalyst(state, entityId, assignment)` | Set analyst assignment (Research/Consulting/null=idle). Guards: analyst exists, topic is visible+unresearched |
| `isResearchVisible(state, upgradeId)` | Check if research option should appear in UI (prereqs + gate) |
| `isUpgradeResearched(state, upgradeId)` | Check if upgrade has been discovered |
| `getAvailableResearch(state)` | Return list of upgrades the player can currently see and assign analysts to |
| `getTopicRate(state, upgradeId)` | Calculate current points/hour for a specific topic based on assigned on-shift analysts |
| `getAnalystStatus(state, entityId)` | Return current assignment for an analyst (Research/Consulting/Idle) |

---

## GameEvent Additions

```kotlin
sealed interface GameEvent {
    // ... existing events ...
    
    // Research System — analyst assignment
    data class AssignAnalyst(val entityId: Int, val assignment: AnalystAssignment?) : GameEvent  // null = idle
    data class HireStaff(val entityDef: EntityDef) : GameEvent  // existing — now includes MARKET_ANALYST
    
    // Tutorial System
    data object SkipTutorial : GameEvent
    data object DismissTutorialStep : GameEvent           // dismiss current banner (step still tracked)
    data class DismissHint(val hintId: String) : GameEvent
}
```

---

## GameState Additions

```kotlin
data class GameState(
    // ... existing fields ...
    val researchState: ResearchState = ResearchState(),
    val tutorialState: TutorialState = TutorialState(),
)
```

---

## UI State Additions

```kotlin
data class ResearchUIState(
    val totalPointsEarned: Int = 0,
    val analysts: List<AnalystUIState> = emptyList(),
    val activeResearch: List<ResearchTopicUI> = emptyList(),    // topics with progress > 0 or analysts assigned
    val availableResearch: List<ResearchTopicUI> = emptyList(), // topics player can assign analysts to
    val completedResearch: List<ResearchTopicUI> = emptyList(),
)

data class AnalystUIState(
    val entityId: Int,
    val name: String,
    val tier: Tier,
    val level: Int,
    val isOnShift: Boolean,
    val assignment: String,       // "Idle", "Consulting", or upgrade displayName
    val assignmentId: String?,    // upgradeId, "consulting", or null
)

data class ResearchTopicUI(
    val id: String,
    val displayName: String,
    val description: String,
    val category: ResearchCategory,
    val cost: Int,
    val progress: Float,              // 0..cost
    val progressPercent: Float,       // 0..1
    val assignedAnalystCount: Int,
    val pointsPerHour: Float,         // current rate for this topic
)
```

Add to `GameUiState`:
```kotlin
val research: ResearchUIState = ResearchUIState(),
val tutorial: TutorialUIState = TutorialUIState(),
```

```kotlin
data class TutorialUIState(
    val currentStep: TutorialStep? = TutorialStep.WELCOME,
    val tutorialComplete: Boolean = false,
    val targetTab: Int? = null,       // nav bar index to highlight (0=Home, 1=Inventory, 2=Staff, etc.)
    val stepTitle: String = "",
    val stepInstruction: String = "",
    val stepHint: String = "",
)
```

---

## Serialization

kotlinx.serialization is already in place (`Json { ignoreUnknownKeys = true; encodeDefaults = true }`).
No manual serialization code needed for new types.

### What to do

1. **Annotate `ResearchState` with `@Serializable`** — fields include `Map<String, Float>`, `Set<String>`, `Map<Int, AnalystAssignment>`, and primitives. `AnalystAssignment` sealed interface needs `@Serializable` on itself and both subtypes (`Research`, `Consulting`). kotlinx.serialization handles sealed interfaces with a `type` discriminator automatically.

2. **Annotate `TutorialState` with `@Serializable`** — `TutorialStep` enum needs `@Serializable` too (serializes by name). `Set<TutorialStep>` and `Set<String>` handled automatically.

3. **Annotate `TutorialStep` enum with `@Serializable`**.

4. **Annotate `ResearchCategory` enum with `@Serializable`** (needed if `ResearchableUpgrade` is ever serialized; not strictly required since the upgrade registry is code-defined, but good hygiene).

5. **Add fields to `GameState`** with defaults:
   ```kotlin
   val researchState: ResearchState = ResearchState(),
   val tutorialState: TutorialState = TutorialState(),
   ```
   Old saves missing these keys deserialize cleanly via `ignoreUnknownKeys = true` + defaults.

6. **`LegacyGameStateDeserializer`** — no changes needed. Legacy saves won't have `researchState` or `tutorialState`, and `GameState`'s defaults handle that. The migration logic (setting `tutorialComplete = true` for existing saves with transactions) runs post-deserialization in application code, not in the deserializer.

7. **No version bump needed** — all new fields have safe defaults.

### `ResearchableUpgrade` is NOT serialized

The upgrade registry (24 product + ~16 feature entries) is defined in code, not saved. What IS persisted in `ResearchState`:
- `researchProgress: Map<String, Float>` — per-topic accumulated points
- `researchedUpgrades: Set<String>` — completed research IDs
- `analystAssignments: Map<Int, AnalystAssignment>` — per-analyst assignment (entityId → Research/Consulting)
- `totalPointsEarned: Float` — lifetime metric

This means research costs, prerequisites, and gate checks can be rebalanced without save migration. Analyst assignments reference upgrade IDs — if an ID is renamed, a migration would be needed (avoid renaming IDs).

### Migration for Existing Saves

Existing saves have no research state. On load:
- `ResearchState()` defaults apply — no research done
- **Problem**: existing players may already have tier 2/3/GM unlocked, store upgrades purchased, etc.
- **Solution**: on load, scan `GameState` and auto-populate `researchedUpgrades` for anything the player already has:
  - Scan `inventory` map: for each item with stock > 0, look up its `researchGate` and add it to `researchedUpgrades`
  - If `currentTier >= TIER_2` → add all `prod_*` entries whose items were in TIER_2 or below
  - If `currentTier >= TIER_3` → add all `prod_*` entries whose items were in TIER_3 or below
  - If `currentTier >= TIER_GM` → add all `prod_*` entries
  - If `currentStoreSize > MOM_AND_POP` → add corresponding `store_*` research
  - If `ownedRegisterCount > 1` → add `register_expansion`
  - If managers exist → add `manager_hiring`
  - Remove `currentTier` from GameState (migrated away)
  - Etc.
- **`items.json` migration**: Replace all `"tier"` fields with `"researchGate"` values. Items formerly tagged `TIER_1` that are starter items get no `researchGate` field (or `null`). All others get their appropriate research gate ID.
- **Tutorial migration**: if `totalTransactionsCompleted > 0` → set `tutorialComplete = true`, all steps completed

---

## Implementation Phases

### Phase 1: Tutorial System — Core
1. Create `TutorialStep` enum with all steps, display text, and hint text
2. Create `TutorialState` data class
3. Add `tutorialState` to `GameState`
4. Create `TutorialManager` with `checkStepCompletion()`, `getCurrentStep()`, `skipTutorial()`, `isFeatureVisible()`
5. Wire `checkStepCompletion()` into `GameEngine.tick()` — advance steps when completion conditions met
6. Add `SkipTutorial`, `DismissTutorialStep`, `DismissHint` to `GameEvent`, route through `GameEngine`
7. Annotate `TutorialState` and `TutorialStep` with `@Serializable` (kotlinx handles the rest)
8. Write unit tests: step progression, skip logic, out-of-order completion, feature visibility checks

### Phase 2: Tutorial UI
9. Add `TutorialUIState` to `GameUiState`
10. Build `TutorialBanner` composable — dismissible card with step title, instruction, "Skip Tutorial" button
11. Place `TutorialBanner` on Home, Inventory, and Staff screens (conditionally by current step)
12. Add nav tab highlight indicator (dot/pulse on target tab during tutorial)
13. Implement feature gating during tutorial:
    - Hide order buttons until ORDER_FIRST_ITEM step
    - Hide store open/close until OPEN_STORE step
    - Hide player role buttons until FIRST_SALE step
    - Hide hire buttons until HIRE_STAFF step
    - Hide Market Analyst in hire list until HIRE_ANALYST step
    - Hide Research tab, pricing UI, delivery settings, scheduling, registers, bulk ordering until tutorial complete

### Phase 3: Item Data Pipeline — Tier → ResearchGate
14. Add `researchGate: String?` field to `Item.kt` (Room entity), `ItemMetadata.kt`, `ItemDataLoader.kt`
15. Replace `tier` field with `researchGate` in `items.json` for all 136 items (12 starter items get null, rest get their research gate ID)
16. Update `ItemMetadataCache` with `isItemAccessible(itemId, researchedUpgrades)` and `getAccessibleItems(researchedUpgrades)`
17. Update `TransactionEngine.weightedSample()` to filter by accessible items
18. Update inventory screen, order placement, bulk order to filter by accessible items
19. Remove `ItemUnlockTier` enum, `requiredTierForSection()`, `ProgressionManager`, `GameState.currentTier`
20. Remove `TierProgressCard` from `StoreHomeScreen`
21. Remove `UnlocksScreen` / tier card UI
22. Write Room migration for schema change (tier column → researchGate column)

### Phase 4: Research System — Core Data Model & Market Analyst
23. Create `AnalystAssignment` sealed interface (`Research(upgradeId)`, `Consulting`)
24. Create `ResearchCategory` enum
25. Create `ResearchableUpgrade` data class with full upgrade registry (~40 entries: 24 product + 16 feature)
26. Create `ResearchState` data class (with `researchProgress`, `analystAssignments`, `researchedUpgrades`)
27. Add `researchState` to `GameState`
28. Create `MARKET_ANALYST` in `EntityDef` companion, add to `allEntities`
29. Create `ResearchManager` with `distributeInsightPoints()`, `checkCompletions()`, `assignAnalyst()`, `getAvailableResearch()`
30. Wire insight distribution into store events: `TransactionEngine.completeTransaction()`, stocker/fresh handler actions, `SpoilageManager`, truck delivery arrival, lost customer events
31. Add `AssignAnalyst` to `GameEvent`, route through `GameEngine`
32. Handle analyst removal: when firing an analyst, clean up their assignment from `analystAssignments`
33. Annotate `ResearchState`, `AnalystAssignment`, and `ResearchCategory` with `@Serializable`
34. Write unit tests: point distribution per-assignment, consulting cash, auto-completion, assignment guards, sqrt scaling

### Phase 5: Research Visibility Gating
33. Add `ResearchUIState` to `GameUiState`
34. Add `isUpgradeResearched()` checks throughout the UI (only applies after tutorial complete):
    - `StoreSizeCard` — gate upgrade button per store size
    - `StaffScreen` — gate manager hire button
    - `RegistersCard` — gate purchase button
    - Delivery settings — gate extra truck slot and early truck
    - Pricing UI — gate markup sliders
    - Schedule screen — gate shift controls
    - Bulk order dialog — gate visibility
    - Fresh auto-order config — gate visibility

### Phase 6: Research Screen UI
35. Build `ResearchScreen` composable:
    - **Analyst Roster** section: each analyst with assignment dropdown (Idle / Consulting / available topics)
    - **Active Research** section: topics with progress bars, assigned analyst count, ETA
    - **Available Research** grouped by category (Product Lines section is the largest)
    - **Completed Research** section
    - Product research shows which items will be unlocked (names + count)
36. Add Research tab to main navigation (visible only after tutorial complete)
37. Toast/notification when research auto-completes, listing newly unlocked items

### Phase 7: Migration & Polish
38. Implement save migration: scan existing inventory/tiers → auto-populate `researchedUpgrades` + set `tutorialComplete = true`
39. Add research points to end-of-day report (`DailyMetrics`)
40. Tune research costs and insight point values through playtesting
41. Test edge cases: no analysts hired, analyst fired mid-shift (assignment cleanup), all research completed (all analysts go idle), reassigning analyst mid-research (progress preserved), save/load round-trip, tutorial skip at various stages, item accessibility filtering correctness

---

## Architecture Notes

### Why a separate ResearchManager?
- Follows existing pattern: `PricingManager`, `ProgressionManager`, `StaffManager` — one manager per domain
- `ResearchManager` doesn't own state; it transforms `GameState.researchState` via `.copy()`

### Why assignment-based instead of a global pool?
- Player makes ongoing allocation decisions: which analyst works on what? Consulting for cash or research for progression?
- Multiple topics can progress simultaneously with multiple analysts
- Idle analysts cost wages but produce nothing — creates pressure to manage workforce
- Consulting assignment replaces the old "cash out" mechanic more naturally (it's a staffing decision, not a currency exchange)
- Progress bars per-topic give visible feedback on research advancement

### Why flat list with prerequisites instead of a skill tree?
- The upgrade space is small (~20 items). A visual tree would be overkill
- Prerequisites are linear within categories, no branching
- Can always add branching later by adding more `prerequisites` entries

### Why remove TierProgressCard from home screen?
- Tiers become a research-gated discovery. Showing tier progress before the player has researched tiers breaks the "hidden until discovered" principle
- The store size card stays because it's the player's current operational state, not a future unlock teaser

### Why remove the tier system entirely?
- Tiers unlocked 29-38 items at once — no discovery feel, just a dump of new content
- Research gates items in groups of 3-6, giving 24 "aha" moments instead of 4
- The tier revenue gates were a single axis of progression; research prerequisites create a web of choices
- Removing `ItemUnlockTier` / `ProgressionManager` simplifies the domain — research subsumes their role
- Items no longer need a category-level gate AND an item-level tier — just one `researchGate` field

### Why a linear tutorial instead of contextual tooltips?
- Linear sequence ensures the player learns mechanics in the right order (order → stock → sell → hire)
- Contextual tooltips require the player to find the feature first — hard when features are hidden
- The linear model naturally gates features: each step reveals the next mechanic
- "Skip Tutorial" covers experienced players or replays

### Why does the tutorial end with hiring an analyst?
- Creates a clean handoff: tutorial teaches "how to play," research system teaches "what to unlock"
- Guarantees every post-tutorial player has an analyst, so research points start flowing
- The analyst hire is the bridge mechanic between guided play and self-directed progression

---

## Tuning Constants

| Constant | Value | Controls |
|----------|-------|----------|
| `TRANSACTION_INSIGHT` | 0.3 | Points per completed transaction |
| `STOCK_INSIGHT` | 0.05 | Points per case stocked to shelf |
| `SPOILAGE_INSIGHT` | 0.2 | Points per spoilage event |
| `DELIVERY_INSIGHT` | 1.5 | Points per truck delivery received |
| `LOST_CUSTOMER_INSIGHT` | 0.15 | Points per customer lost |
| `ANALYST_HIRE_COST` | $30.00 | One-time hiring cost |
| `ANALYST_HOURLY_WAGE` | $9.00/hr | Operating cost |
| Research costs | 8–100 | See research tree tables above |
| `CONSULTING_CASH_PER_POINT` | $5.00 | Cash per research-point-equivalent when consulting |

### Earning Rate Analysis (Per Assigned Analyst)

Early game (~20 txns/day, ~50 stocks, ~5 spoilage, ~0.3 deliveries/day, ~2 lost):
- ~10.25 pts/day per topic with 1 base analyst assigned
- Small research (8 pts): ~1 day
- Consulting: ~$50/day (vs $72 wage = -$22 net)

Late game (~100 txns/day, ~200 stocks, ~20 spoilage, ~0.7 deliveries/day, ~10 lost):
- ~46.55 pts/day per topic with 1 base analyst (before tier/level multipliers)
- Large research (100 pts): ~2-3 days
- Consulting late game: ~$235/day (vs $72 wage = +$163 net)

Research scales with store activity. Same-topic analysts use sqrt scaling (2 = ~1.41x). Different-topic analysts run at full speed independently. Idle analysts produce nothing but still cost wages.

---

## Constraints

- **Batch rules**: Never access `shelfStock`/`backroomStock` as mutable ints. Always `.copy()` batches.
- **Manager rules**: Never call manager methods from UI. Route through `GameEngine` events.
- **Money precision**: Research points are `Float`, not `Money`. No cents involved.
- **Test pattern**: Use `FakeItemDao` boilerplate. No Mockito. No trivial tests.
- **EntityDef immutability**: `MARKET_ANALYST` is a companion object constant like `CASHIER`, `STOCKER`, etc.
- **Assignment cleanup**: When an analyst is fired, their entry in `analystAssignments` must be removed. When a topic completes, all analysts assigned to it must be set to idle.
- **Assignment storage**: Analyst assignments live in `ResearchState.analystAssignments`, NOT on `HiredEntity` (which uses a custom serializer and shouldn't be modified for research concerns).

---

## Testing Plan

### Tutorial Tests

Test file: `app/src/test/java/com/example/superstoresimulator/domain/tutorial/TutorialManagerTest.kt`

#### 1. Step Progression

| Test | Setup | Assert |
|------|-------|--------|
| **Fresh game starts at WELCOME** | New GameState | currentStep = WELCOME |
| **ORDER_FIRST_ITEM advances on order** | Step = ORDER_FIRST_ITEM, place an order | Step advances to WAIT_FOR_DELIVERY |
| **STOCK_SHELVES advances on shelf stock** | Step = STOCK_SHELVES, item.shelfStock = 5 | Step advances to OPEN_STORE |
| **COMPLETE_TRANSACTION advances** | Step = COMPLETE_TRANSACTION, totalTransactionsCompleted = 1 | Step advances to HIRE_STAFF |
| **HIRE_ANALYST advances on analyst hired** | Step = HIRE_ANALYST, hire MARKET_ANALYST | Step advances to TUTORIAL_COMPLETE |
| **TUTORIAL_COMPLETE sets flag** | Step = TUTORIAL_COMPLETE | tutorialComplete = true, getCurrentStep() = null |

#### 2. Skip and Out-of-Order

| Test | Setup | Assert |
|------|-------|--------|
| **Skip tutorial sets complete** | Any step, dispatch SkipTutorial | tutorialComplete = true, all steps in completedSteps |
| **Out-of-order skips met steps** | Player already has shelfStock > 0 and transactions > 0 before reaching those steps | Steps STOCK_SHELVES, OPEN_STORE, COMPLETE_TRANSACTION auto-skipped |
| **Already-hired staff skips HIRE_STAFF** | hiredEntityRegistry.totalCount() = 2 at HIRE_STAFF step | Immediately advances past HIRE_STAFF |

#### 3. Feature Visibility

| Test | Setup | Assert |
|------|-------|--------|
| **Order buttons hidden before ORDER_FIRST_ITEM** | Step = WELCOME | isFeatureVisible("order_items") = false |
| **Hire buttons hidden before HIRE_STAFF** | Step = OPEN_STORE | isFeatureVisible("hire_staff") = false |
| **Research tab hidden during tutorial** | tutorialComplete = false | isFeatureVisible("research_tab") = false |
| **All features visible after tutorial** | tutorialComplete = true | isFeatureVisible returns true for tutorial-gated features |

#### 4. Migration

| Test | Setup | Assert |
|------|-------|--------|
| **Existing save with transactions → tutorial complete** | totalTransactionsCompleted = 50, no TutorialState | tutorialComplete = true |
| **Fresh save with no transactions → tutorial starts** | totalTransactionsCompleted = 0, no TutorialState | currentStep = WELCOME |

---

### Research Tests

Test file: `app/src/test/java/com/example/superstoresimulator/domain/research/ResearchManagerTest.kt`

### 1. Point Generation (Assignment-Based)

| Test | Setup | Assert |
|------|-------|--------|
| **Assigned analyst generates topic points** | 1 analyst assigned to prod_breakfast, 1 transaction | researchProgress["prod_breakfast"] ≈ 0.3 |
| **Idle analyst generates nothing** | 1 analyst, no assignment, 1 transaction | researchProgress empty, money unchanged |
| **Consulting analyst generates cash** | 1 analyst on consulting, 1 transaction | money += $1.50 (0.3 × $5), no research progress |
| **Off-shift analyst ignored** | 1 analyst shift 6-14, event at hour 16 | No points regardless of assignment |
| **FAST tier boosts topic points** | FAST tier analyst (1.5x) assigned to topic, 1 transaction | researchProgress ≈ 0.45 |
| **Level multiplier applies** | Level 5 analyst (1.4x) assigned to topic, 1 transaction | researchProgress ≈ 0.42 |
| **2 analysts same topic → sqrt scaling** | 2 base analysts assigned to same topic, 1 transaction | researchProgress ≈ 0.42 (√2 × 0.3), not 0.6 |
| **2 analysts different topics → full speed** | 1 on topic A, 1 on topic B, 1 transaction | progress A ≈ 0.3, progress B ≈ 0.3 |
| **Consulting has no diminishing returns** | 2 analysts on consulting, 1 transaction | money += $3.00 (2 × 0.3 × $5) |
| **Points accumulate across events** | 1 analyst on topic, 10 transactions + 20 stocks | researchProgress ≈ 4.0 |

### 2. Item Accessibility

| Test | Setup | Assert |
|------|-------|--------|
| **Starter items always accessible** | No research done | Bread, Rice, Chips, Water etc. accessible |
| **Gated item hidden before research** | Cereal (researchGate = prod_breakfast), no research | isItemAccessible = false |
| **Gated item visible after research** | prod_breakfast in researchedUpgrades | Cereal accessible |
| **Transaction pool excludes gated items** | prod_dairy_basics not researched | Milk not in weightedSample pool |
| **Ordering blocked for gated items** | prod_breakfast not researched | Cannot order Cereal |
| **Bulk order skips gated items** | prod_condiments not researched | Peanut Butter excluded from bulk |
| **All items accessible when all researched** | All prod_* researched | All 136 items accessible |

### 3. Analyst Assignment

| Test | Setup | Assert |
|------|-------|--------|
| **Assign to research topic** | Analyst exists, topic is visible | analystAssignments[entityId] = Research("prod_breakfast") |
| **Assign to consulting** | Analyst exists | analystAssignments[entityId] = Consulting |
| **Set to idle (null)** | Analyst currently assigned | analystAssignments removes entityId |
| **Reject assignment to unresearchable topic** | prod_baking without prereq prod_breakfast | State unchanged |
| **Reject assignment to completed topic** | prod_breakfast already researched | State unchanged |
| **Reject assignment for non-analyst** | entityId is a Cashier | State unchanged |
| **Fired analyst auto-removed from assignments** | Fire analyst with active assignment | analystAssignments no longer contains entityId |

### 4. Auto-Completion

| Test | Setup | Assert |
|------|-------|--------|
| **Topic completes at cost threshold** | researchProgress["prod_breakfast"] = 5.0, cost = 5 | prod_breakfast in researchedUpgrades, progress removed |
| **Analysts idled on completion** | 2 analysts assigned to topic, topic completes | Both analysts removed from analystAssignments |
| **Completion unlocks items** | prod_condiments completes | PB, Jam, Ketchup, etc. now accessible |
| **Partial overshoot consumed** | Progress hits 5.3 on a cost-5 topic | Completes, excess lost (not transferred) |
| **Prerequisites met reveals next** | prod_breakfast completes | prod_baking appears in available list |

### 5. Available Research List

| Test | Setup | Assert |
|------|-------|--------|
| **Initial available list** | Fresh game state | Only gate-free, no-prereq items visible (prod_breakfast, prod_condiments, prod_canned, etc.) |
| **Completing prereq reveals next** | prod_breakfast completed | prod_baking appears in available list |
| **Gate blocks visibility** | totalRevenue < $2,500 | prod_dairy_basics not in available list |
| **Completed items not in available** | prod_breakfast already researched | Not in available, in completed |

### 6. Upgrade Visibility

| Test | Setup | Assert |
|------|-------|--------|
| **Unresearched upgrade is hidden** | No research done | `isUpgradeResearched("prod_dairy_basics")` = false |
| **Researched upgrade is visible** | prod_dairy_basics in researchedUpgrades | `isUpgradeResearched("prod_dairy_basics")` = true |

### 7. Save Migration

| Test | Setup | Assert |
|------|-------|--------|
| **Existing TIER_2 player gets product research** | Old save with currentTier = TIER_2, inventory has Milk | All prod_* gates for stocked items + all TIER_1/TIER_2 items' gates populated |
| **Existing TIER_GM player gets all product research** | Old save with currentTier = TIER_GM, SUPERSTORE size | All prod_* entries + corresponding store_* entries populated |
| **Stocked items auto-research their gate** | Inventory has Cereal (researchGate = prod_breakfast) | prod_breakfast in researchedUpgrades |
| **Fresh save gets empty research** | New game, no prior state | ResearchState() defaults, only starter items accessible |

### 7. Serialization Round-Trip

| Test | Setup | Assert |
|------|-------|--------|
| **ResearchState survives save/load** | Points, researched set, totalEarned | Deserialized equals original |
| **Empty ResearchState deserializes** | No research data in save JSON | Defaults to ResearchState() |
