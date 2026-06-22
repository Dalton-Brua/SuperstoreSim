# Plan C: Second Locations (Empire Building)

## Context

Player opens additional store locations. Unlike the home store, secondary
locations are **not** micro-managed. The player has **broad strategic control
only** — no per-store inventory, registers, trucks, staff scheduling, or manual
cashiering.

For each secondary location the player can:
- Open its **dashboard** and read its main metrics.
- Buy **upgrades** for it (store size, and other capacity/efficiency upgrades).
- Choose its **strategic direction** (Aggressive, Balanced, Passive, Defensive)
  — *unless* a regional manager is running it (see Regional Manager).

The chosen direction drives a **simulated economic model** that produces the
store's pricing posture, customer traffic, revenue, and costs. The store runs
itself; the player steers it, not operates it.

Once the player owns enough stores, they can **hire a regional manager** who
takes over direction-setting across stores according to the manager's
personality.

### Two player loops — one-way transition

The game has **two distinct core loops**:
- **Loop 1 (operate)**: the single home store with full fine-grained control as
  it works today — inventory, registers, trucks, staff, manual cashiering.
- **Loop 2 (empire)**: managing many abstract, directed stores at the strategic
  level described in this plan.

Entering loop 2 is a **permanent, structural** choice: once you become a chain
you can never return to the simple single-store game. When the player buys the
empire upgrade, the **home store is converted into an abstract secondary store**
that runs itself by direction.

But loop 1 is **not thrown away**. From empire mode the player can **drop into
any store and operate it by hand** (full loop-1 machinery) whenever they like,
then pop back to empire mode. A hands-on visit leaves a lasting **operating
performance** mark on that store that shapes its simulated profit afterward and
fades over time. See **Operating a Store from Empire Mode** below.

So the irreversible part is *"you are now a chain, not a single shop"* — not
*"you can never run a store again."* All the loop-1 systems stay in use.

---

## Core Idea: Stores Are Simulated, Not Operated

Secondary stores do **not** reuse the full single-store machinery
(InventoryManager, TransactionEngine, TruckManager, registers, baskets, etc.).
Instead each secondary store is a lightweight economic entity whose outputs are
computed from its size, upgrades, direction, and global state (research, vendor
deals, reputation).

This avoids the massive `StoreInstance` extraction the old plan required. The
home store stays exactly as-is; secondary stores are a separate, much simpler
model.

---

## State Model

### StoreDirection

```kotlin
@Serializable
enum class StoreDirection {
    AGGRESSIVE,   // low margins, high volume — chase traffic & market share
    BALANCED,     // default — moderate pricing, steady growth
    PASSIVE,      // high margins, low volume — milk profit, minimal effort
    DEFENSIVE,    // protect against losses — cut costs, minimize risk
}
```

Each direction is a set of **modifiers** applied to the simulation:

| Direction  | Price level | Traffic | Revenue goal | Operating cost | Risk |
|------------|-------------|---------|--------------|----------------|------|
| Aggressive | low (−)     | high (+)| high         | high (+)       | high |
| Balanced   | normal      | normal  | moderate     | normal         | med  |
| Passive    | high (+)    | low (−) | low          | low (−)        | low  |
| Defensive  | normal      | low (−) | break-even   | lowest         | lowest |

(Exact multipliers tuned during implementation — see `StoreDirectionModifiers`.)

### SecondaryStore

```kotlin
@Serializable
data class SecondaryStore(
    val storeId: Int,
    val storeName: String,
    val regionId: Int,                               // which region this store sits in
    val storeSize: StoreSize,
    val upgrades: Set<StoreUpgrade> = emptySet(),
    val direction: StoreDirection = StoreDirection.BALANCED,
    val managedByRegionalManager: Boolean = false,   // true when a manager owns its direction
    val openedAtTime: GameTime,
    val operatingPerformance: Float = 1.0f,          // multiplier on sim profit from last hands-on visit (1.0 = sim baseline)
    val lastOperatedDay: Int? = null,                // dayIndex of last hands-on session, for decay
    val operatingState: StoreOperatingState? = null, // preserved loop-1 detail (null until first hands-on visit; reconstructed on entry if null)
    val currentDayMetrics: SimStoreMetrics,
    val completedDayMetrics: List<SimStoreMetrics> = emptyList(),
)
```

### StoreUpgrade

Per-store purchasable upgrades. Size is the headline one; others tune the sim.

```kotlin
@Serializable
enum class StoreUpgrade {
    // Size is stored as storeSize, upgraded via a dedicated "expand" purchase
    // that bumps StoreSize to the next tier (cost scales with tier).
    EXTRA_REGISTERS,    // +throughput → higher traffic ceiling
    LOGISTICS,          // −operating cost
    MARKETING,          // +traffic
    // ... extend as needed
}
```

Size upgrade = bump `storeSize` to next `StoreSize` tier (own cost curve). Other
upgrades are flags in `upgrades` that adjust sim multipliers.

### SimStoreMetrics

What the dashboard shows and what the sim produces per day:

```kotlin
@Serializable
data class SimStoreMetrics(
    val dayIndex: Int,
    val customers: Int,
    val revenue: Money,
    val operatingCost: Money,
    val netProfit: Money,
    val avgPriceLevel: Float,     // relative to baseline (1.0 = normal)
    val trafficVsGoal: Float,     // actual / goal
)
```

### GameState additions

```kotlin
data class GameState(
    // ... all existing home-store fields unchanged ...

    val empireModeActive: Boolean = false,           // one-way: home store converted to abstract
    val regions: List<Region> = emptyList(),         // unlocked/known markets
    val secondaryStores: List<SecondaryStore> = emptyList(),
    val nextSecondaryStoreId: Int = 1,
    val regionalManager: RegionalManager? = null,
)
```

`empireModeActive` gates the whole UI: false → loop-1 home-store screens;
true → loop-2 empire screens only. The flip is permanent.

### Region

See the **Regions + Market Saturation** section for the full model. Summary:

```kotlin
@Serializable
data class Region(
    val regionId: Int,
    val name: String,
    val demandProfile: DemandProfile,    // base spending power + category skew
    val capacity: Int,                   // soft cap of total "store weight" before saturation bites
    val unlockCost: Money,               // entry fee to open the first store here
    val unlocked: Boolean = false,
)
```

Money stays a **single shared bank account**. Secondary-store net profit flows
into `money` each sim day. No StoreInstance extraction, no flat-field
migration — purely additive.

---

## Simulation Loop

A new `SecondaryStoreSimManager` runs on the **day-rollover** boundary (simplest
and cheapest; no per-tick work).

Per store, per day:
1. Resolve `StoreDirectionModifiers` for the store's direction.
2. Compute **traffic** = base(storeSize) × direction.trafficMult ×
   upgradeFactors × globalFactors (research, reputation, vendor deals).
3. Compute **avg price level** from direction.priceMult.
4. **Revenue** = customers × avgBasket × priceLevel.
5. **Operating cost** = size-based fixed cost × direction.costMult ×
   upgradeFactors.
6. Apply the **operating performance** multiplier (decayed toward 1.0 by days
   since `lastOperatedDay` — see Operating a Store) to revenue/profit, so a store
   the player recently ran well keeps earning like it did, then drifts back to
   baseline.
7. **Net profit** = revenue × operatingPerformance − operatingCost; add to global
   `money`.
8. Write `SimStoreMetrics` into `currentDayMetrics`; on day rollover push to
   `completedDayMetrics`.

Closed-form economic estimate with light randomness. Cheap regardless of store
count. **Traffic is gated by region saturation** — see below.

---

## Time System (Loop 2)

Loop 1 runs minute-by-minute because the player operates second-to-second. Loop
2 has nothing at minute scale — the sim only resolves at **day rollover**.
Watching minutes tick with no detail is dead time. So empire mode uses a
coarser, **decision-driven clock**.

### Day-stepped time
- When `empireModeActive`, **suspend the minute tick**. The **day** is the atom;
  advance day-by-day. The sim already runs at day rollover and `SimStoreMetrics`
  is keyed by `dayIndex`, so this is mostly *removing* the minute driver.
- Underlying `GameTime` may still advance (jump whole days per step) so
  saves/aggregate stats stay consistent — just don't render minutes.

### Speed controls
- Pause / Normal (≈1 day per few seconds) / Fast (≈1 day per fraction-second).
- Player watches days flip and money/metrics move at a readable rate, not
  minutes.

### Decision-point auto-pause (anti-boredom core)
Time fast-forwards but **auto-pauses when the player's input matters**, so the
player steers instead of babysitting:
- A store turns unprofitable, or a region tips into saturation.
- A cash milestone is hit (can afford the next location/upgrade).
- The regional manager quits or underperforms.
- (Future) a regional event fires — see Future Work.
- Optional **"advance to next decision"** button = skip-ahead until something
  needs the player.

Rhythm: **make decisions → fast-forward → get pulled back when it matters.**

### Batched reporting
- Replace per-minute detail with **weekly / monthly summaries** (revenue, net
  profit, per-region saturation, best/worst stores). Daily detail stays in
  `completedDayMetrics` for charts; the player reads digests, not a live feed.

**Dropping into a store** (Operating a Store from Empire Mode) hard-pauses the
empire clock and hands the minute tick to that single store; leaving resumes the
empire clock. So the two time models never run at once.

State: a small `EmpireClock` (current speed, paused flag, pending
decision-pause reason) on `GameState` or the ViewModel.

---

## Regions + Market Saturation

Regions are the strategic board of loop 2. They turn "open more stores" from a
pure accumulation into a **where + how-dense** decision, because piling stores
into one region makes them steal each other's customers.

### Region model

```kotlin
@Serializable
data class Region(
    val regionId: Int,
    val name: String,
    val demandProfile: DemandProfile,
    val capacity: Int,           // total store-weight the region absorbs before saturation
    val unlockCost: Money,       // one-time entry fee to open your first store here
    val unlocked: Boolean = false,
)

@Serializable
data class DemandProfile(
    val baseSpendingPower: Float,    // multiplier on avgBasket for stores here (e.g. 0.8 rural … 1.4 affluent)
    val baseTraffic: Float,          // multiplier on base customer count
    val growthRate: Float,           // per-day drift in capacity/traffic (boom vs declining region)
)
```

Regions ship as a fixed authored list (start with ~4–6), each a different
risk/reward shape:
- **Home region** (where store 0 lives) — unlocked from the start, medium
  everything.
- A **small/rural** region — cheap unlock, low capacity, low spending power.
  Good early, saturates fast.
- A **dense/affluent** region — expensive unlock, high capacity + spending,
  attracts the most value but the most cannibalization risk if overbuilt.
- A **growing** region — modest now, high `growthRate`; rewards early entry.

`growthRate` lets `capacity` and traffic drift over time so a region's value is
not static — early bets in a growing region pay off, mature regions plateau.

### Store weight

Each store contributes **weight** to its region based on size + upgrades:

```kotlin
fun storeWeight(store: SecondaryStore): Float =
    sizeWeight(store.storeSize) *           // bigger store = more weight
    (1f + marketingUpgradeBonus(store))     // marketing pulls more demand → more weight
```

Bigger and more aggressively-marketed stores draw more customers — and so
contribute more to crowding the region.

### Saturation curve

Per region, per sim day, compute total weight and a **saturation factor**:

```kotlin
val totalWeight = stores.filter { it.regionId == region.id }.sumOf { storeWeight(it) }
val load = totalWeight / region.capacity            // 1.0 = exactly at capacity

// Below capacity: little/no penalty. Above: traffic per store falls off.
val saturationFactor = when {
    load <= 1.0f -> 1.0f
    else         -> 1.0f / (1.0f + (load - 1.0f) * SATURATION_STEEPNESS)
}
```

- `load <= 1.0` → no penalty; region has room.
- `load > 1.0` → each store's traffic is multiplied by `saturationFactor < 1`.
- Curve is **smooth and shared**: a new store in a crowded region drags down
  *every* store already there, not just itself. This is the cannibalization that
  punishes overbuilding.

`SATURATION_STEEPNESS` is the key tuning knob (start ~0.5).

### How it feeds the sim

In the per-store sim (step 2, traffic), multiply by the region's
`saturationFactor` and `DemandProfile`:

```
traffic = base(storeSize)
        × direction.trafficMult
        × upgradeFactors
        × region.demandProfile.baseTraffic
        × regionSaturationFactor
        × globalFactors
revenue = customers × avgBasket × region.demandProfile.baseSpendingPower × priceLevel
```

Saturation factor is computed **once per region per day** (sum weights, then
apply to each store), so cost stays O(stores), not O(stores²).

### Strategic consequences (why this is fun)

- **Expand wide vs deepen**: a 2nd store in a rich region may earn less than a
  1st store in a fresh region once saturation bites.
- **Size tradeoff**: upgrading a store's size raises its own revenue *and* its
  weight — over-upgrading in a tight region cannibalizes your other stores.
- **Direction interaction**: AGGRESSIVE raises traffic *and* weight (more
  crowding); DEFENSIVE/PASSIVE add less weight — useful to hold a saturated
  region without dragging neighbors down.
- **Region choice has a cost**: `unlockCost` is a real capital decision on top
  of the per-store scaling cost.

### Manager awareness

The regional manager's assignment algorithm must account for saturation: putting
every store in a crowded region on AGGRESSIVE can *lower* total profit via
cannibalization. The "flip worst offenders" step naturally handles this — a
store whose aggressive weight is tanking its neighbors shows up as a worst
offender and gets flipped to a lighter direction.

---

## Entering Empire Mode (one-way transition)

Buying the empire upgrade (the same gate as opening the 2nd location) is the
**point of no return**: it flips `empireModeActive = true` and permanently
retires loop 1.

### Confirmation dialog (required)
Before the purchase commits, show a confirmation dialog that clearly warns the
choice is irreversible. Example copy:

> **Go corporate?**
> Opening a second location makes you a regional operator — for good. Your stores
> will mostly run themselves by **direction**. You can still step in and run any
> store by hand whenever you want, but **you can never return to the simple
> single-store game.**
> [ Cancel ]  [ Open second location ]

Only on explicit confirm does the purchase + conversion run. Cancel = no state
change.

### Home-store conversion
On confirm:
1. Derive a `SecondaryStore` from the home store:
   - `regionId` = the home region (auto-unlocked).
   - `storeSize` = home store's current size.
   - `direction` = BALANCED.
   - Carry over relevant upgrades where they map to `StoreUpgrade`.
   - Seed `currentDayMetrics` from recent home-store performance (or zeroed).
2. Append it to `secondaryStores`; set `empireModeActive = true`.
3. The detailed home-store fields (inventory, registers, trucks, staff,
   schedules) stop driving the home-store UI by default — it now runs as a
   simulated store. They are **preserved** (not discarded) so the player can drop
   back in and operate it via the same path as any other store (see Operating a
   Store from Empire Mode).
4. Pay the 2nd-location scaling cost; player can now open more locations.

### UI gating
- `empireModeActive == false` → loop-1 screens as today; the empire upgrade
  appears as a purchasable (with the confirmation dialog).
- `empireModeActive == true` → default view is the empire store list / dashboards
  / manager panel. The loop-1 operational screens are no longer the *home*
  experience, but are reachable **on demand** by dropping into a store (next
  section). There is no path back to single-store-only mode.

---

## Operating a Store from Empire Mode

The player can **drop into any store and run it by hand** (full loop-1 machinery)
for as long as they like, then return to empire mode. This keeps all the loop-1
systems alive and gives the player a way to personally rescue or boost a store
instead of only steering it from afar.

### Drop in
1. From a store's dashboard, "Run this store myself."
2. **Pause the empire clock** (day-stepping stops; other stores don't sim while
   you're heads-down).
3. Spin up loop-1 operating state for that store:
   - If the store has preserved detailed state (e.g. the converted home store),
     resume from it.
   - Otherwise **reconstruct** plausible loop-1 state from the store's size +
     upgrades + direction (inventory levels, register count, staff, truck config)
     so any store is operable, not just the original.
4. **Resume the minute tick** scoped to this one store — loop 1 plays exactly as
   today.

### Drop out → performance carries over
On leaving the store:
1. Measure the player's **realized performance** during the session — e.g.
   profit rate / throughput / fill-rate relative to that store's sim baseline for
   its size+direction.
2. Convert it into `operatingPerformance` (clamped band, e.g. 0.7–1.5). Running
   it well → >1.0 (sim earns more, "like when you were running it"); running it
   poorly → <1.0.
3. Record `lastOperatedDay = currentDayIndex`.
4. Persist the store's detailed loop-1 state so a later visit resumes it.
5. **Resume the empire clock**; the store reverts to simulated, now scaled by the
   fresh `operatingPerformance`.

### Decay
`operatingPerformance` **drifts back toward 1.0** over days since
`lastOperatedDay` (e.g. linear/exponential over ~N sim days). A hands-on visit is
a temporary boost that fades, so the player has a reason to periodically tour
their best stores — but can't permanently out-earn the sim by visiting once.

This is the bridge between the two loops: empire decisions set the baseline;
hands-on operation perturbs it; decay returns it to baseline.

---

## Opening a New Location

> Note: the **first** location purchase also triggers the Entering Empire Mode
> transition above. Subsequent purchases are normal opens.

### Research gate
```kotlin
"second_location" — researchCost: 250, prereq: ["building_purchase"], requiredStoreSize: SUPERCENTER
```

### Scaling costs (unlimited locations)
| Location # | Cost |
|-----------|------|
| 2nd | $1,000,000 |
| 3rd | $2,500,000 |
| 4th | $5,000,000 |
| nth | `$1M × 2.5^(n-2)` |

Player may open **as many locations as they can afford**. Opening flow:
1. Pick a **region** (paying its one-time `unlockCost` if not yet unlocked).
2. Pay the location scaling cost (above).
New store starts at a base size with BALANCED direction in that region, then the
player buys upgrades / sets direction (or hands it to a manager). Region
saturation immediately affects the new store and its regional neighbors.

### Upgrades
Bought per store from its dashboard. Size-expansion bumps `StoreSize` up a tier;
other `StoreUpgrade` flags tune traffic/cost. All paid from shared `money`.

---

## Regional Manager

Once the player owns **enough stores** (gate: e.g. ≥ N secondary stores, tune
N), they may **hire a regional manager**. The manager automates direction-setting
across all stores it covers.

### State

```kotlin
@Serializable
data class RegionalManager(
    val name: String,
    val personality: ManagerPersonality,
    val salaryPerDay: Money,
    val hiredAtTime: GameTime,
)

@Serializable
enum class ManagerPersonality {
    AGGRESSIVE,   // wants stores on AGGRESSIVE direction
    PASSIVE,      // wants stores on PASSIVE direction
    DEFENSIVE,    // wants stores on DEFENSIVE direction
}
```

A manager's personality maps to a **preferred `StoreDirection`**.

### Behavior (runs at day rollover, before the sim)

Goal: **keep the empire profitable overall while putting as many stores as
possible on the manager's preferred direction.**

Algorithm:
1. Tentatively set **every managed store** to the preferred direction.
2. Estimate empire net profit (sum of per-store sim estimates − manager salary).
3. If empire net profit < 0 (or below a safety floor):
   - Sort stores by how much they hurt the bottom line on the preferred
     direction (worst first).
   - Flip the worst offenders to a profit-preserving direction (PASSIVE or
     DEFENSIVE) one at a time, re-estimating, until empire net profit ≥ floor.
4. Leave the remaining (max possible) stores on the preferred direction.

Result: as many stores as possible follow the personality; the rest are flipped
only as needed to stay profitable.

### Player control vs manager control
- No manager → player sets each store's `direction` manually.
- Manager hired → managed stores show manager-assigned direction (read-only),
  flagged `managedByRegionalManager = true`. Player can still hire/fire the
  manager and keep buying upgrades.
- Manager costs `salaryPerDay`, deducted at day rollover.

---

## UI

### Empire / store list
- **Grouped by region**: each region header shows name, load (totalWeight /
  capacity) as a saturation bar, demand profile, and a warning when over
  capacity.
- Per store: name, size, direction badge (with a "managed" marker if
  manager-set), today's revenue, net profit, traffic vs goal, loss alert.
- Region unlock entries for not-yet-entered regions (show unlockCost + profile).
- Manager section: hire/fire regional manager (gated by store count), shows
  personality, salary, and how many stores currently follow vs deviate from the
  preferred direction.

### Store dashboard
- Main metrics: customers, revenue, operating cost, net profit, price level,
  traffic vs goal, trend chart from `completedDayMetrics`.
- **Upgrades**: buy size expansion + other `StoreUpgrade`s.
- **Direction selector**: pick Aggressive / Balanced / Passive / Defensive —
  disabled (shows manager's choice) when manager-managed.
- **Run this store myself**: drop into loop-1 operation (see Operating a Store).
  Show current `operatingPerformance` and how long since last operated (decay).

No inventory/register/truck/staff screens for secondary stores — home-store-only.

---

## Why This Is Simpler Than the Old Plan

| Old plan (autonomous full stores) | New plan (abstract directed stores) |
|-----------------------------------|-------------------------------------|
| Extract `StoreInstance` from GameState (touches every manager) | Additive `secondaryStores` list; home store untouched |
| Every manager store-scoped | Managers unchanged; new `SecondaryStoreSimManager` + manager logic |
| Multi-store tick of full machinery | Cheap closed-form daily sim |
| Per-store inventory/registers/trucks UI | Dashboard + upgrades + direction selector |
| Flat→multi-store save migration | Additive fields with defaults |

---

## Sub-Phases (reference scope — same systems, no changes)

These are the original logical phases, kept for scope reference. **How they map onto
parallel tracks is in the next section** — the phases below describe *what* each
system is; the tracks describe *who builds what concurrently*.

| Phase | Scope |
|-------|-------|
| C1: State + sim | `StoreDirection`, `SecondaryStore`, `StoreUpgrade`, `SimStoreMetrics`, `SecondaryStoreSimManager`. Daily economic sim feeding global money. Unit tested. |
| C2: Regions + saturation | `Region`, `DemandProfile`, store weight, saturation curve; wire into sim traffic/revenue. Authored region list. Unit tested. |
| C3: Opening + upgrades | Research gate, **empire-mode transition + confirmation dialog + home-store conversion**, region unlock + selection, scaling cost, purchase action, per-store upgrade purchases (size + flags), `empireModeActive` UI gating. |
| C4: Regional manager | `RegionalManager`, personality, hire/fire gate, day-rollover assignment algorithm (profit floor + max-preferred, saturation-aware). |
| C5: Time system | Day-stepped empire clock, speed controls, decision-point auto-pause, weekly/monthly digest. Suspend minute tick when `empireModeActive`. |
| C5b: Operate-a-store | Drop-in/drop-out flow: pause empire clock, resume/reconstruct loop-1 state for any store, run minute tick scoped to it; on exit compute `operatingPerformance` + decay, persist detailed state. |
| C6: UI | Region-grouped store list, dashboard, upgrades, direction selector, manager hire/fire panel, saturation bars, clock/speed controls + digest screen. |
| C7: Tuning + global hooks | Wire research/reputation/vendor multipliers; balance direction + upgrade modifiers + saturation steepness + manager salary + clock speeds. |

---

## Parallel Execution Plan

Same systems, same end state. Reorganized so work happens in three **waves**; inside
Wave 1, five **tracks** run concurrently with no shared-file collisions. The trick:
one blocking Foundation wave defines every data type and every cross-track entry-point
signature up front, so each Wave-1 track edits only files it **owns** and compiles
against frozen contracts. Integration + tuning closes it out.

### Collision-avoidance rules (read first)
1. **Only Foundation touches `GameStateData.kt`.** All new state fields + serialization
   defaults land in Wave 0. No Wave-1 track adds fields to `GameState`.
2. **`GameEngine` / `GameViewModel` shared files:** Foundation adds *stub* methods
   (one per track entry point) returning TODO/no-op with final signatures. Each Wave-1
   track fills in **only its own stubs** — disjoint method bodies, so edits don't overlap.
   If a track needs more surface, it adds a **new** file (e.g. `EmpireClockController.kt`)
   and the stub just delegates to it.
3. **Each track owns new files exclusively.** No two tracks write the same `.kt`.
4. **Tuning constants are frozen as named placeholders in Wave 0** (`StoreDirectionModifiers`,
   `SATURATION_STEEPNESS`, scaling-cost curve, salary, clock speeds). Wave-1 tracks
   reference the names; only Wave 2 changes the values.

### Wave 0 — Foundation (1 agent, blocks everything)
Goal: freeze all types + contracts so Wave 1 can fan out.
- **All `@Serializable` data classes / enums** (new files): `StoreDirection.kt`,
  `SecondaryStore.kt`, `StoreUpgrade.kt`, `SimStoreMetrics.kt`, `Region.kt`,
  `DemandProfile.kt`, `RegionalManager.kt`, `StoreOperatingState.kt`.
- **`GameStateData.kt`**: add `empireModeActive`, `regions`, `secondaryStores`,
  `nextSecondaryStoreId`, `regionalManager`, and `EmpireClock` state — all with
  serialization defaults (additive, no migration).
- **Authored region registry** + **tuning-constants file** with placeholder values
  (named constants A & E both reference).
- **Contract stubs** in `GameEngine`/`GameViewModel` + `GameUiState` fields for: sim
  step, manager assignment, open/transition/upgrade actions, clock control, operate
  drop-in/out. Final signatures, empty bodies. Plus the C3 `ResearchUpgradeRegistry`
  `second_location` gate entry.
- **Gate:** project compiles; serialization round-trips an old save with defaults.
  Merge before starting Wave 1.

### Wave 1 — five parallel tracks (all start after Wave 0 merges)
Each is independent; none blocks another.

| Track | Covers (phases) | Owns (new files) | Touches (own stubs only) |
|-------|-----------------|------------------|--------------------------|
| **A — Sim Engine** | C1 + C2 + C4 sim + C7 hooks | `SecondaryStoreSimManager.kt` (daily sim, store-weight + per-region saturation pass, research/reputation/vendor multipliers), `RegionalManagerManager.kt` (saturation-aware assignment algorithm) | day-rollover hook stub |
| **B — Opening & Transition** | C3 logic | `EmpireTransitionActions.kt` (empire flip, home→`SecondaryStore` conversion, region unlock, scaling cost, open + upgrade purchase actions) | open/transition/upgrade stubs, `ResearchUpgradeRegistry` gate body |
| **C — Empire Clock** | C5 | `EmpireClockController.kt` (day-step driver, speed controls, decision-point auto-pause, weekly/monthly digest builder; suspend minute tick when `empireModeActive`) | clock-control stubs |
| **D — Operate-a-Store** | C5b | `OperateStoreController.kt` (drop-in/out, resume-or-reconstruct loop-1 state, scoped minute tick, `operatingPerformance` measure + decay) | operate drop-in/out stubs |
| **E — UI** | C6 | empire store-list screen, store dashboard, upgrade purchase, direction selector, manager panel, saturation bars, clock/speed controls, digest screen | reads `GameUiState` contract only |

Notes:
- **A** is fully standalone (pure logic + unit tests) — start immediately, highest value.
- **B/C/D** each touch only their own stubs in the shared files + their own new file →
  no overlap.
- **E** builds against the Wave-0 `GameUiState` contract, so it renders placeholder/live
  data without waiting on A–D; real data binds as each lands. Manager-managed
  read-only direction selector, "Run this store myself" button, saturation bars all
  key off contract fields defined in Wave 0.
- A→manager: `RegionalManagerManager` calls A's own per-store sim estimate — same track,
  no cross-track dep.

### Wave 2 — Integration + Tuning (1 agent)
- Wire any remaining cross-track seams (clock decision-pause reads sim/region state from A;
  operate drop-out feeds `operatingPerformance` the sim then consumes; UI binds final data).
- **C7 tuning**: set real values for direction modifiers, upgrade factors,
  `SATURATION_STEEPNESS`, scaling-cost curve, manager salary, clock speeds.
- End-to-end emulator verification (the two emulator checks in Verification).

### Critical path
`Wave 0` → (longest of A / B / C / D / E, run together) → `Wave 2`.
Foundation and Integration are the only serial bottlenecks; the five build tracks
collapse into one wall-clock span.

---

## File Summary (high-level)

| Area | Files affected |
|------|---------------|
| State model | `GameStateData.kt` (add `empireModeActive`, `regions`, `secondaryStores`, `nextSecondaryStoreId`, `regionalManager`), new `SecondaryStore.kt`, `StoreDirection.kt`, `StoreUpgrade.kt`, `SimStoreMetrics.kt`, `Region.kt`, `DemandProfile.kt`, `RegionalManager.kt`, `StoreOperatingState.kt` (optional loop-1 detail bundle: inventory/registers/trucks/staff for an operable store) |
| Transition | empire-mode entry + home-store→`SecondaryStore` conversion in `GameEngine`/ViewModel; confirmation dialog; loop-1 vs loop-2 UI gating |
| Time system | `EmpireClock` (speed/pause/decision-pause); suspend minute tick when `empireModeActive`; day-step driver; weekly/monthly digest |
| Operate-a-store | Drop-in/out in `GameEngine`/ViewModel: pause empire clock, resume/reconstruct loop-1 state, scoped minute tick; `operatingPerformance` measure + decay; persist per-store detailed state on the `SecondaryStore` |
| Sim | new `SecondaryStoreSimManager.kt` (incl. per-region saturation pass); hook into day rollover |
| Regions | authored region list (registry/constants); store-weight + saturation helpers |
| Manager | manager assignment algorithm (in sim manager or own `RegionalManagerManager.kt`) |
| Opening / upgrades | `ResearchUpgradeRegistry` gate, purchase + upgrade actions in `GameEngine` / ViewModel |
| ViewModel | `GameViewModel` / `GameUiState` — secondary store list, dashboard, manager state |
| UI | store-list screen, dashboard screen, upgrade purchase, direction selector, manager panel |
| Serialization | Additive only — new fields default; no migration. `operatingState` is a nullable, heavier loop-1 detail blob, populated only for stores the player has operated (most stay null) |
| Tests | Sim output per direction; upgrade effects; profit flows to money; metrics roll over; manager keeps empire profitable while maximizing preferred-direction stores |

## Verification

- Unit: each direction yields expected relative ordering (Aggressive traffic >
  Balanced > Passive).
- Unit: region under capacity → saturationFactor == 1; over capacity →
  factor < 1 and falls as weight rises.
- Unit: adding a store to a crowded region lowers traffic for the *existing*
  stores there (cannibalization), not just the new one.
- Unit: `DemandProfile` shifts traffic (baseTraffic) and basket
  (baseSpendingPower) as expected; `growthRate` drifts capacity over days.
- Unit: size + other upgrades shift sim outputs as expected.
- Unit: net profit (incl. manager salary) added to global `money` each sim day.
- Unit: `currentDayMetrics` rolls into `completedDayMetrics` on day boundary.
- Unit: manager puts **max stores** on preferred direction while empire net
  profit stays ≥ floor; flips only the worst offenders when needed.
- Unit: manager hire gated by store count; firing returns control to player.
- Serialization: old save (no secondary fields) loads with defaults.
- Unit: manager assignment is saturation-aware — an aggressive store tanking its
  region's neighbors is flagged a worst offender and flipped.
- Unit: confirming empire-mode flips `empireModeActive`, converts home store into
  a `SecondaryStore` (home region, carried size), and is irreversible; canceling
  leaves state unchanged.
- Unit: in empire mode the minute tick is suspended and time advances by whole
  days; at Fast speed multiple days resolve without minute steps.
- Unit: a decision-pause condition (store unprofitable / region saturated / cash
  milestone / manager quit) flips the clock to paused with the right reason.
- Unit: dropping into a store pauses the empire clock and gives a runnable loop-1
  state (resumed from preserved state, or reconstructed from size/upgrades for a
  store with none); dropping out resumes the empire clock.
- Unit: running a store well sets `operatingPerformance` > 1.0 (sim profit rises
  toward the realized rate); running it poorly sets < 1.0; value is clamped.
- Unit: `operatingPerformance` decays toward 1.0 over days since
  `lastOperatedDay`.
- Serialization: old save (no `empireModeActive`/`regions`/secondary fields)
  loads with defaults (empire mode off).
- Emulator: empire upgrade shows confirmation dialog; on confirm, loop-1 home
  screens disappear and the converted home store appears in the empire list.
- Emulator: unlock a region, open multiple locations in it, watch saturation bar
  fill and per-store traffic drop; buy size upgrade; hire manager; watch
  directions auto-assign and metrics change next day.

---

## Future Work (Loop 2 depth — not in this plan)

These extend the empire loop into a full strategy layer. Sequenced roughly by
impact; each is its own plan when scoped. The throughline: loop 2 needs tension
beyond accumulation — external pressure, variance, and capital scarcity.

1. **Rival chains** — AI competitors expand into regions and take market share,
   reacting to player directions. AGGRESSIVE triggers price wars (both bleed,
   winner takes the region); PASSIVE cedes share for margin. This is the main
   external pressure that makes directions genuine choices rather than a solvable
   optimization. Hooks directly into the region/saturation model (rival stores
   add competing weight).
2. **Manager roster + traits** — multiple regional managers, each with
   personality + skill level + traits (cost-cutter, growth-hawk, crisis-steady) +
   salary; assign to regions/groups; hire/fire/level; managers can underperform
   or quit. Turns "management" into real people-management.
3. **Events / crises (region-wide shocks)** — random events that can hit an
   **entire region at once** and drastically swing its supply and/or traffic for
   a duration:
   - **Natural disasters** (hurricane, flood, earthquake, wildfire) — slash
     traffic and may damage stores / spike operating cost across the region;
     panic-buying can briefly spike then crater demand.
   - **Supply shocks** — regional shortages or cost spikes (fuel, a key category)
     that raise operating cost and cap how much demand stores can serve.
   - **Local economic swings** — boom or recession shifting `DemandProfile`
     spending power and traffic region-wide.
   - **Social/PR events** — strikes, scandals, viral popularity.
   Modeled as timed modifiers layered onto a region's `DemandProfile` and the
   saturation/cost math (multipliers with a start day + duration), so they ride
   the existing per-region sim cleanly. Player picks a response; manager
   personality changes outcomes (DEFENSIVE weathers, AGGRESSIVE exposed). Each
   firing is a **decision-point auto-pause** (see Time System). Injects the
   variance that keeps the loop off autopilot.
4. **Finance layer** — loans/debt with interest to fund faster expansion →
   cash-flow management as the core tension. Company valuation enabling an
   **IPO / sell-the-empire** endgame.
5. **Distribution centers** — empire logistics tied into the existing vendor
   system; DCs cut operating cost for nearby stores. Capital-vs-payoff regional
   investment.
6. **Empire policies** — chain-wide marketing, loyalty program, and private-label
   expansion (reuse existing private-label system at empire scale).
7. **Store lifecycle** — stores decline, need renovation, can be sold/closed;
   underperformers become decisions, preventing pure accumulation.
8. **Store floorplan design** — now that the player operates many stores, let
   them author **reusable floorplans** that control how customers flow through a
   store: entrance/checkout placement, aisle/department layout, hot-zone and
   impulse-buy positioning, bottlenecks. A good layout becomes a sim modifier —
   higher traffic conversion / basket size / throughput; a bad one cuts them.
   - Floorplans are **templates** the player builds once and **assigns to any
     store** (optionally gated by store size), so the effort scales across the
     empire instead of being per-store busywork.
   - Feeds the sim as layout-derived multipliers on traffic conversion, avg
     basket, and register throughput — riding the existing closed-form model.
   - In **hands-on operation** (Operating a Store), the assigned layout shapes the
     real loop-1 floor so design choices show up live, not just in the abstract
     numbers.
   - Pairs with size upgrades (bigger store = more layout freedom) and marketing
     (drives traffic the layout then converts).
9. **Regional demand mix + empire product allocation** — deepen `DemandProfile`
   so regions differ by **what** their customers buy, not just how much:
   per-category demand weights (one region over-indexes on meat, another on
   produce, another on specific produce types or premium/private-label goods).
   - **Stocking to the region**: a store's revenue depends on how well its
     product mix matches its region's demand weights — wrong mix leaves sales on
     the table even with high traffic.
   - **Regional roll-outs**: the player chooses items/lines to launch in specific
     regions (e.g. a private-label cut of meat in a meat-heavy region), turning
     the existing private-label/vendor systems into a regional-targeting tool.
   - **Empire-wide product allocation**: with many stores, the player manages
     product **amounts across the whole empire** — distributing limited supply
     (or vendor-contracted volume) among regions, prioritizing where each item
     sells best. A light allocation layer, not per-store inventory micro.
   - Hooks into the sim as category-match multipliers on revenue/basket, and into
     the (future) distribution-center + vendor systems for where supply physically
     flows. Pairs with `ItemMetadataCache` categories already in the codebase.
10. **Endgame / win condition** — terminal goal (dominate N regions, reach $X
    valuation, IPO and cash out) so accumulation has a point and a "you won" beat.
