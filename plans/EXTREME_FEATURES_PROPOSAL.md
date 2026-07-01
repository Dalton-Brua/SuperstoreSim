# Extreme Features Proposal - Superstore Simulator

**Date**: June 10, 2026 (Revised)  
**Status**: Proposal / Design Phase  
**Risk Level**: Variable per feature

---

## Executive Summary

Revised proposal based on current implementation state. The game already has:
- 136+ items across 12 categories with batch-tracked FIFO inventory and expiration
- 4 staff types (Cashier, Stocker, Fresh Handler, Manager) with XP, tiers, and promotion
- 5 store sizes (Mom & Pop → Supercenter) with progressive unlocks
- Truck delivery system with scheduled/fresh/early trucks and auto-ordering
- Dynamic pricing (markups/markdowns), zoning system, register management
- Store Manager automation (auto-hire, auto-order, auto-rebalance shifts)
- Daily metrics tracking (sales, wages, rent, OOS, expiration, refunds)
- Traffic patterns (weekday/weekend curves, basket sizes, store size multipliers)

This document proposes **12 features** that build on top of these existing systems.

---

## Current Performance Headroom

### Pixel 8a Real Device Results
| Scenario | Tick Time | Frame Budget Used | Available Headroom |
|----------|-----------|-------------------|-------------------|
| Baseline (10 items, 0 staff) | 0.0045ms | 0.03% | 99.97% |
| Realistic (500 items, 10 staff) | 0.0095ms | 0.06% | 99.94% |
| Maximum (500 items, 50 staff) | 0.039ms | 0.23% | 99.77% |

**Key Insight**: Less than 0.25% of frame budget used at maximum load. ~400x headroom before 60 FPS is threatened.

### Current Memory Baseline
| Component | Estimated Size |
|-----------|---------------|
| GameState (full inventory, 50 staff) | ~2-4 MB |
| ItemMetadataCache (136 items) | ~50 KB |
| UI State (Compose) | ~20-40 MB |
| App baseline (framework, Hilt, Room) | ~30-50 MB |
| **Total runtime** | **~60-90 MB** |

Android target: 256 MB heap on low-end devices, 512 MB+ on modern (Pixel 8a).

---

## Feature #1: Weather & Seasonal Events

### Concept
Dynamic weather affects customer traffic and item demand. Holidays create demand spikes that reward preparation.

### Gameplay Depth: 5/5
- Weather types: Sunny, Rain, Snow, Heatwave, Storm (with power outage risk)
- Seasonal demand shifts per category (e.g., FROZEN +25% in summer, BAKERY +20% in fall)
- 20-30 holiday events with category-specific surges (Christmas, Thanksgiving, Super Bowl, etc.)
- 3-day advance warnings for major events — rewards players who pre-stock
- Power outages during storms: no sales for 1-4 hours + spoilage risk for perishables

### Systems Affected
- **TrafficManager**: Weather/season/holiday multipliers on base traffic rate. Power outage = zero traffic.
- **TransactionEngine**: Category-weighted purchase selection based on active weather/season/holiday boosts.
- **GameState**: New fields — `currentWeather`, `upcomingEvents`, `powerOutage` state.
- **DayManager**: Weather rolls at midnight. Holiday calendar lookup.
- **InventoryManager**: Spoilage calculation during extended power outages (FROZEN/DAIRY/MEAT).
- **DailyMetrics**: Track weather-related losses (spoilage from outages).
- **UI**: Weather indicator on game screen, event calendar view, spoilage alerts.

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | ~0.012ms (+25%) | Multiplier lookups per transaction |
| Memory | baseline | +~5 KB | Weather state + holiday calendar (static data) |
| Midnight calc | 0ms | +0.5ms | Weather roll + spoilage check |
| Save file | baseline | +~200 bytes | Weather enum + outage state |

**Verdict**: Negligible. Weather updates once/day. Demand modifiers are simple float multiplies.

### Implementation Effort: 35-45 hours

### Breaking Changes
- GameState gains new fields (migration: add defaults)
- Save file compatible with migration

---

## Feature #2: Crime & Security System

### Concept
Shoplifting, employee theft, and break-ins create inventory shrinkage. Security investments reduce losses.

### Gameplay Depth: 4/5
- Shoplifting: 2-5% of customers attempt theft (higher with low staff-to-customer ratio)
- Detection rate: 10% base → up to 90% with full security stack
- Employee theft: Low-morale or low-wage employees have small daily theft chance
- Night break-ins: 0.1% chance per night when store closed, loses 10-50 items
- Security upgrades: Cameras ($25,000), alarm system ($10,000), security guards (new entity type)
- Caught shoplifters banned from store (minor traffic reduction)
- Theft history visible in metrics

### Systems Affected
- **TrafficManager**: Theft attempt generation (probabilistic per customer served).
- **GameState**: New `SecurityConfig` (cameras, alarm, guard count) + `theftHistory` list.
- **DailyMetrics**: New fields — `theftLosses`, `theftDetected`, `breakInLosses`.
- **InventoryManager**: Stock removal on undetected theft and break-ins.
- **StaffManager**: Potential new entity type (Security Guard) or security as upgrade.
- **DayManager**: Break-in check at store close / overnight.
- **StoreManagerConfig**: Auto-upgrade security toggle.
- **UI**: Security panel, theft alert notifications, loss reports.

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | ~0.011ms (+15%) | One RNG roll per transaction |
| Memory | baseline | +~10 KB | Security config + theft history (capped at 100 events) |
| Midnight calc | 0ms | +0.2ms | Break-in probability check |
| Save file | baseline | +~2 KB | Security state + recent theft log |

**Verdict**: Minimal. Theft is probabilistic and rare. Break-ins checked once per night.

### Implementation Effort: 25-35 hours

### Breaking Changes
- GameState new fields (migration: defaults)
- New DailyMetrics fields (migration: zeros)

---

## Feature #3: Employee Morale & Personality

### Concept
Staff have morale (0-100) that affects work speed and quit risk. Personality traits create unique employee management challenges.

### Gameplay Depth: 4/5
- Morale meter per employee: 50 morale = 50% work speed
- Morale factors: wage vs. market rate, overtime hours, store conditions, co-worker count
- Quitting: Morale < 20 for 3 consecutive days = employee quits with 1-day notice
- Personality traits (up to 2 per employee): Cheerful, Grumpy, Lazy, Perfectionist, Social, Loner, Ambitious, Loyal
- Raise requests from Ambitious employees every 30 days
- Break room upgrade: One-time purchase that gives passive morale boost
- Loyal employees never quit regardless of morale

### Systems Affected
- **HiredEntity**: New fields — `morale: Int`, `personality: Set<PersonalityTrait>`, `wage: Money`, `hoursWorkedThisWeek`, `daysLowMorale`.
- **StaffManager**: Morale calculation at day rollover. Quit processing. Raise logic.
- **TransactionEngine / InventoryManager**: Apply morale multiplier to cashier/stocker speed accumulators.
- **DayManager**: Trigger morale recalculation at midnight.
- **GameState**: Break room upgrade flag. Market wage reference.
- **StoreManagerConfig**: Auto-raise toggle, morale floor threshold.
- **UI**: Morale indicator on staff cards, quit warnings, raise request dialogs.

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | ~0.011ms (+15%) | Read morale value per staff in speed calc |
| Memory | baseline | +~2 KB (50 staff) | 40 bytes per entity (morale, traits, wage) |
| Midnight calc | 0ms | +1ms | Morale recalc for all staff |
| Save file | baseline | +~3 KB | Per-entity morale/personality fields |

**Verdict**: Minimal per-tick. Morale recalculates once/day. Per-tick cost is just reading an int.

### Implementation Effort: 30-40 hours

### Breaking Changes
- HiredEntity gains new fields (migration: morale=100, empty traits, current wage from EntityDef)
- Existing staff get random personality on migration

---

## Feature #4: Real-Time Competitor Stores

### Concept
AI-controlled competitor stores share the customer pool. Market share depends on pricing, selection, service quality, and marketing spend.

### Gameplay Depth: 4/5
- 3-5 AI competitors per market (named chains with distinct strategies)
- Market share calculation: Price (30%) + Selection (30%) + Service (20%) + Marketing (20%)
- Competitors respond to player pricing changes (match or undercut)
- Marketing campaigns: Radio ads, coupon mailers, grand opening events
- Espionage: Pay to see competitor inventory/pricing
- Player can drive competitors out of business (or get driven out)
- Competitor strategies: AGGRESSIVE (price war), CONSERVATIVE (steady), PREMIUM (high margin)

### Systems Affected
- **TrafficManager**: Total market demand split by market share instead of going entirely to player.
- **GameState**: New `MarketState` with competitor list and market share map.
- **PricingManager**: Price changes affect market share recalculation.
- **New CompetitorAI manager**: Runs competitor decisions once per game-hour.
- **DailyMetrics**: Market share %, competitor-related events.
- **UI**: Market analysis panel, competitor intel screen, marketing campaign launcher.

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | ~0.013ms (+35%) | Market share lookup per traffic calc |
| Memory | baseline | +~50 KB | 5 competitors × (pricing data + state) |
| Hourly AI calc | 0ms | +2ms | Competitor decisions (once per game-hour) |
| Save file | baseline | +~10 KB | Competitor state serialization |

**Verdict**: Low. AI decisions run hourly (not per-tick). Market share is a cached float lookup.

### Implementation Effort: 40-50 hours

### Breaking Changes
- TrafficManager fundamentally changes (traffic = marketShare × totalDemand)
- GameState gains MarketState
- Existing saves get 100% market share on migration (competitors spawn gradually)

---

## Feature #5: Customer Loyalty Program

### Concept
Implement a loyalty card system. Repeat customers spend more, visit more often, and provide stable revenue. Investment in loyalty perks grows the member base.

### Gameplay Depth: 4/5
- Loyalty tiers: Bronze (free), Silver (500 points), Gold (2000 points), Platinum (5000 points)
- Members spend 15-40% more per visit (tier-dependent)
- Members visit 20-60% more frequently
- Point earning: $1 spent = 1 point
- Perks unlock at tiers: member discounts (5-15%), priority checkout, exclusive items
- Loyalty program costs: $10,000 setup + $500/day operating cost
- Member count grows based on customer satisfaction and store reputation
- Churn: 2% of members leave per month if no visit

### Systems Affected
- **GameState**: New `LoyaltyProgram` state — member counts per tier, total points issued, program enabled flag.
- **TrafficManager**: Loyalty members generate bonus traffic (repeat visits).
- **TransactionEngine**: Apply loyalty discount. Track points earned. Larger basket multiplier for members.
- **DailyMetrics**: Loyalty revenue vs. non-loyalty, member signups, churn.
- **DayManager**: Daily membership growth/churn calculation.
- **UI**: Loyalty dashboard, tier breakdown chart, perk configuration.

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | ~0.010ms (+5%) | Bool check per transaction (is member?) |
| Memory | baseline | +~1 KB | Aggregate counts, not per-customer tracking |
| Midnight calc | 0ms | +0.3ms | Growth/churn calculation |
| Save file | baseline | +~500 bytes | Program state (4 tier counts + config) |

**Verdict**: Trivial. Loyalty is modeled as aggregate tiers (not individual customers). One extra multiplier per transaction.

### Implementation Effort: 20-30 hours

### Breaking Changes
- GameState new fields (migration: program disabled by default)

---

## Feature #6: Supply Chain & Vendor Negotiations

### Concept
Expand the truck system into a full supply chain with multiple vendors, bulk discount negotiations, exclusive deals, and supply disruptions.

### Gameplay Depth: 5/5
- Multiple vendors per category (3-5 each), varying in price, reliability, minimum order
- Vendor relationships: Order volume builds trust → unlocks bulk discounts (5-20% off)
- Exclusive deals: Vendor offers limited-time 30% discount on specific items (accept within 1 day)
- Supply disruptions: Random 1-3 day delays on specific vendors (diversification incentive)
- Contract system: Lock in prices for 30 days at slight premium (hedges against disruptions)
- Vendor quality tiers: Budget (cheap, unreliable), Standard, Premium (expensive, never disrupted)
- Integrates with existing TruckManager — vendors determine what's available on trucks

### Systems Affected
- **TruckManager**: Vendor selection per order line. Delivery reliability per vendor. Disruption delays.
- **GameState**: New `VendorState` — relationship levels, active contracts, pending deals.
- **InventoryManager**: Ordering now routes through vendor selection.
- **DayManager**: Vendor deal generation, disruption events, relationship decay.
- **DailyMetrics**: Vendor savings, disruption losses.
- **StoreManagerConfig**: Preferred vendor per category, auto-accept deals toggle.
- **UI**: Vendor management screen, deal notifications, contract negotiation dialog.

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | ~0.0095ms (unchanged) | Vendor logic runs at order-time only, not per-tick |
| Memory | baseline | +~30 KB | 12 categories × 4 vendors × relationship data |
| Order processing | ~1ms | ~3ms | Vendor lookup + discount calc (infrequent event) |
| Save file | baseline | +~8 KB | Vendor relationships + active contracts |

**Verdict**: Zero per-tick impact. Vendor logic only fires when player orders or trucks arrive.

### Implementation Effort: 40-50 hours

### Breaking Changes
- TruckManager order flow gains vendor parameter
- ScheduledTruck gains vendor ID field
- Migration: assign default vendor to existing orders

---

## Feature #7: Online Ordering & Delivery

### Concept
Customers order online for pickup or delivery. Players must fulfill orders within time limits or lose reputation.

### Gameplay Depth: 5/5
- Online orders: 5-20% of revenue shifts from in-store to online (scales with reputation)
- Order types: Pickup (customer arrives in 30 min) and Delivery (60 min window)
- Fulfillment: Stockers "pick" items from shelves (10 sec/item for player, 20 sec for AI)
- Out-of-stock items on orders = partial refund + reputation penalty
- Reputation system (0-100): On-time = +1, late = -5, missed = -20
- Delivery drivers: New gig-economy entity ($50 per delivery, no hourly wage)
- Player sets delivery fee ($2-$10) — higher fee = fewer orders but more profit per order
- Unlocks at GROCERY_STORE size

### Systems Affected
- **GameState**: New `onlineOrders: List<OnlineOrder>`, `onlineReputation: Int`, `deliveryFee: Money`.
- **New OnlineOrderManager**: Generates orders based on traffic × reputation. Tracks fulfillment progress.
- **InventoryManager**: Order picking consumes shelf stock (like transactions but slower).
- **StaffManager**: Stockers can be assigned to order fulfillment vs. regular stocking.
- **TrafficManager**: Online order rate derived from base traffic.
- **DailyMetrics**: Online revenue, fulfillment rate, reputation changes.
- **TimeManager**: Order timeout tracking (due times).
- **UI**: Order queue panel, picker assignment, reputation indicator, delivery fee slider.

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | ~0.013ms (+35%) | Progress fulfillment counters for active orders |
| Memory | baseline | +~20 KB | 50 concurrent orders × 400 bytes each |
| Order generation | 0ms | +0.5ms per game-minute | Probabilistic, sparse |
| Save file | baseline | +~5 KB | Active orders + reputation |

**Verdict**: Low. Orders are sparse (few per game-hour). Fulfillment is simple counter increment per tick.

### Implementation Effort: 45-55 hours

### Breaking Changes
- GameState new fields (migration: empty order list, reputation 50)
- StaffManager needs fulfillment assignment logic
- New UI tab/panel

---

## Feature #8: Loan & Investment System

### Concept
Take out loans for rapid expansion or invest surplus cash for returns. Financial management adds risk/reward decisions.

### Gameplay Depth: 3/5
- Loans: $10K-$500K available, 5-15% interest rate, 30-90 day terms
- Multiple concurrent loans allowed (up to 3)
- Missed payments: Interest rate doubles, then store assets seized (game over risk)
- Investment accounts: Park cash for 3-7% daily returns (locked for term)
- Credit score (300-850): Improves with on-time payments, affects available rates
- Emergency loan: High-interest (20%) but instant, no credit check
- Loan purposes: Expansion, inventory bulk buy, security upgrades, marketing
- Bank relationship: Consistent good behavior unlocks better rates over time

### Systems Affected
- **GameState**: New `FinancialState` — active loans, investments, credit score, bank relationship.
- **DayManager**: Daily interest accrual, payment due dates, investment returns.
- **Money**: Loan proceeds added, payments deducted automatically.
- **StoreController**: Loan required for early expansion (can't afford upgrades otherwise).
- **DailyMetrics**: Interest paid, investment returns, loan balance.
- **UI**: Bank/finance panel, loan application, investment dashboard, payment schedule.

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | unchanged | Loan logic runs at day rollover only |
| Memory | baseline | +~2 KB | 3 loans + 3 investments × state |
| Midnight calc | 0ms | +0.1ms | Interest calc + payment processing |
| Save file | baseline | +~1 KB | Financial state |

**Verdict**: Zero per-tick impact. All financial logic runs at midnight rollover.

### Implementation Effort: 20-30 hours

### Breaking Changes
- GameState new fields (migration: no loans, 650 credit score default)
- DayManager gains financial processing step

---

## Feature #9: Store Layout & Optimization

### Concept
Design physical store layout with placeable shelves, checkouts, and facilities. Layout quality affects customer flow, theft rate, and sales efficiency.

### Gameplay Depth: 5/5
- Grid-based editor: Size scales with store (Mom&Pop 10x15 → Supercenter 30x40)
- Placeable objects: Shelves (assigned to categories), checkouts, break room, restrooms, security cameras, entrances
- Customer flow simulation: Entrance → aisles → shelves → checkout path
- Layout efficiency metrics: avg path length, dead zones (shelves off-path = -50% sales), security coverage
- Traffic jams: >5 customers in same tile = satisfaction penalty
- Aisle width matters: 1-tile aisles = cramped (-satisfaction), 2-tile = normal, 3-tile = spacious (+satisfaction)
- End-cap displays: Premium shelf positions with +30% sales for placed items
- Store upgrades tied to layout (break room needs physical space, registers need checkout objects)

### Systems Affected
- **GameState**: New `StoreLayout` — grid dimensions, tile map, placed objects.
- **New LayoutManager**: Pathfinding, efficiency calculation, flow simulation.
- **TrafficManager**: Customer satisfaction affected by layout score.
- **InventoryManager**: Shelf assignments link items to physical locations.
- **StaffManager**: Cashiers assigned to specific checkout objects.
- **SecurityManager (Feature #2)**: Camera placement provides coverage radius.
- **StoreController**: Store size upgrade changes grid dimensions.
- **UI**: Full drag-and-drop grid editor (most complex UI in the game).

### Performance & Memory Impact

| Metric | Current | With Feature | Notes |
|--------|---------|--------------|-------|
| Tick time | 0.0095ms | ~0.010ms (+5%) | Layout score is pre-computed, just a float read |
| Memory (Mom&Pop) | baseline | +~15 KB | 150 tiles × 100 bytes |
| Memory (Supercenter) | baseline | +~120 KB | 1,200 tiles × 100 bytes |
| Layout recalc | N/A | 10-50ms | Only on layout edit (not per-tick) |
| Save file | baseline | +~5-20 KB | Grid serialization |

**Critical concern — Pathfinding:**
- A* per customer per tick: UNUSABLE (100 customers × 10ms = 1000ms)
- **Solution: Flow field pathfinding** — compute once per layout change, customers follow pre-computed arrows (O(1) per customer per tick)
- Flow field recomputation: ~50-100ms (only when player edits layout — completely off-tick)

**Verdict**: Safe with flow field approach. Per-tick cost is zero (pre-computed scores). Memory is modest. The expensive part is the UI editor (Compose grid rendering), not the simulation.

### Implementation Effort: 70-90 hours

### Breaking Changes
- GameState gains StoreLayout (migration: generate default layout per store size)
- Inventory shelving tied to physical locations
- Register system linked to checkout objects
- Heaviest UI work in the project

---

## Summary Table

| # | Feature | Depth | Perf Impact | Memory | Effort | Dependencies |
|---|---------|-------|-------------|--------|--------|--------------|
| 1 | Weather & Seasons | 5/5 | Negligible | +5 KB | 35-45 hrs | None |
| 2 | Crime & Security | 4/5 | Negligible | +10 KB | 25-35 hrs | None |
| 3 | Employee Morale | 4/5 | Minimal | +2 KB | 30-40 hrs | None |
| 4 | Competitor AI | 4/5 | Low | +50 KB | 40-50 hrs | None |
| 5 | Customer Loyalty | 4/5 | Trivial | +1 KB | 20-30 hrs | None |
| 6 | Supply Chain/Vendors | 5/5 | Zero per-tick | +30 KB | 40-50 hrs | None |
| 7 | Online Ordering | 5/5 | Low | +20 KB | 45-55 hrs | None |
| 8 | Loan & Investment | 3/5 | Zero per-tick | +2 KB | 20-30 hrs | None |
| 9 | Store Layout | 5/5 | Complex (mitigated) | +15-120 KB | 70-90 hrs | None |

---

## Performance Budget After All Features

| Scenario | Current | All Features (lazy) | All Features (aggressive) |
|----------|---------|--------------------|-----------------------------|
| Realistic Load | 0.0095ms | ~0.05ms | ~0.15ms |
| Maximum Load | 0.039ms | ~0.2ms | ~0.8ms |
| Frame Budget Used (max) | 0.23% | 1.2% | 4.8% |


## Implementation Roadmap

### Phase 1: Low-Hanging Fruit (Weeks 1-4)
Features: **Weather (#1)**, **Crime (#2)**, **Loyalty (#5)**

Rationale: All are independent, low-risk, add variety without major refactoring. Each is 20-45 hours. Total: ~100-135 hours.

### Phase 2: Depth Systems (Weeks 5-10)
Features: **Employee Morale (#3)**, **Supply Chain (#6)**, **Loans (#11)**

Rationale: Deepen existing mechanics. Morale makes staff management meaningful. Vendors make ordering strategic. Loans enable faster expansion. Total: ~90-120 hours.

### Phase 3: New Game Loops (Weeks 11-18)
Features: **Online Ordering (#7)**, **Competitor AI (#4)**

Rationale: New revenue streams and external pressure. Both require moderate refactoring but create compelling gameplay. Total: ~85-105 hours.

### Phase 4: Spatial (Optional / Post-Launch)
Feature: **Store Layout (#12)**

Rationale: Heaviest UI work. Adds a unique puzzle dimension but isn't required for tycoon loop. Best saved for a major content update. Total: ~70-90 hours.

---

## Breaking Changes Summary

| Feature | Save File | GameState | ViewModel | UI | Tests |
|---------|-----------|-----------|-----------|----|----|
| Weather & Seasons | Compatible | +fields | Minimal | +widget | New |
| Crime & Security | Compatible | +fields | Minimal | +panel | New |
| Employee Morale | Compatible | +entity fields | Minimal | +indicators | Update staff tests |
| Competitor AI | Compatible | +MarketState | Moderate | +screen | New |
| Customer Loyalty | Compatible | +fields | Minimal | +dashboard | New |
| Supply Chain | Compatible | +VendorState | Minimal | +screen | Update order tests |
| Online Ordering | Compatible | +OrderState | Moderate | +panel | New |
| Multi-Store Chain | **Migration required** | **Major rewrite** | **Major rewrite** | +switcher | **All tests update** |
| Franchise System | Compatible | +CorporateState | Minimal | +screen | New |
| Store Reputation | Compatible | +fields | Minimal | +display | New |
| Loan & Investment | Compatible | +FinancialState | Minimal | +panel | New |
| Store Layout | **Migration required** | +StoreLayout | Moderate | **New editor** | New |

All "Compatible" features use kotlinx.serialization defaults — existing saves load fine with new fields at default values.

---

## Final Notes

- All per-tick performance numbers assume current architecture (single-thread tick loop). Coroutine parallelism would reduce multi-store overhead by 3-5x on modern devices.
- Memory estimates are conservative (assume no compression, no lazy loading). Real-world usage would be 30-50% lower with standard optimizations.
- Features 1-7 and 9-11 require NO architectural changes to the engine — they extend existing systems with new fields and managers.
- Only Multi-Store (#8) and Store Layout (#12) require fundamental architecture changes.
