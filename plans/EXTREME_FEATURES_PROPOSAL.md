# Extreme Features Proposal - Superstore Simulator

**Date**: April 19, 2026  
**Author**: Architecture Analysis  
**Status**: Proposal / Design Phase  
**Risk Level**: 🔴 HIGH - Major refactoring, architectural changes, and performance implications

---

## Executive Summary

Based on comprehensive analysis of:
- **Device Performance Tests** (Pixel 8a: 99.77% frame budget available at max load)
- **Architecture Reviews** (service-layer pattern, 99% fewer DB lookups, incremental UI updates)
- **Current Features** (136 items, 4 revenue tiers, autonomous traffic, daily metrics, save/load)

This document proposes **10 extreme features** that would transform Superstore Simulator from a management sim into a full-fledged **tycoon empire simulator**. Each feature is rated by:
- 🎯 **Feature Depth**: How much gameplay it adds
- 🔧 **Refactoring Required**: Major architectural changes needed
- ⚠️ **Performance Impact**: Expected degradation (with mitigation strategies)
- 📊 **Implementation Complexity**: Development effort

---

## Current Performance Headroom Analysis

### Pixel 8a Real Device Results
| Scenario | Tick Time | Frame Budget Used | Available Headroom |
|----------|-----------|-------------------|-------------------|
| **Baseline** (10 items, 0 staff) | 0.0045ms | 0.03% | **99.97%** ✅ |
| **Realistic** (500 items, 10 staff) | 0.0095ms | 0.06% | **99.94%** ✅ |
| **Maximum** (500 items, 50 staff) | 0.039ms | 0.23% | **99.77%** ✅ |

**Key Insight**: The game uses less than **0.25% of the frame budget** at maximum load. This means we could theoretically run **400× more complex logic** and still maintain 60 FPS.

### Architecture Strengths to Leverage
1. ✅ **ItemMetadataCache**: Single DB load for all items (99% fewer DB lookups)
2. ✅ **Service-Layer Managers**: Clean separation of concerns (8 specialized managers)
3. ✅ **Incremental UI Updates**: 99% fewer reconstructions via `GameStateChange`
4. ✅ **Immutable State**: Safe concurrency and time-travel debugging
5. ✅ **Complete Persistence**: JSON serialization with SharedPreferences

### Bottleneck Opportunities (From Architecture Analysis V2)
1. 🟡 Copy-on-Write Inventory (60-70% fewer allocations possible)
2. 🟡 Version-Based Change Detection (O(1) vs O(n) field comparisons)
3. 🟡 Debounced Time UI Updates (60× fewer time emissions)
4. 🟢 Transaction History Pagination (unbounded list growth)

---

## Extreme Feature #1: Multi-Store Chain Management 🏬🏬🏬

### Concept
Expand beyond a single store to manage a **chain of 5-20 locations** simultaneously, each with independent inventory, staff, traffic, and metrics.

### Feature Depth: 🎯🎯🎯🎯🎯 (5/5)
**Why**: Transforms the game from "manage one store" to "build an empire." Players juggle resource allocation, choose expansion locations, and optimize supply chains.

### Gameplay Additions
- **Store Locations**: 20 pre-defined markets (Downtown, Suburb, Airport, University, etc.)
- **Per-Store State**: Each store has independent `GameState` (inventory, staff, metrics, traffic patterns)
- **Resource Transfer**: Move inventory/staff between stores (truck delivery system with travel time)
- **Market Differences**: 
  - Downtown: High traffic, high rent ($5,000/day), fast turnover
  - Suburb: Medium traffic, low rent ($1,000/day), family-oriented products
  - Airport: Premium pricing, 24/7 hours, impulse purchases
- **Expansion Costs**: New store = $500,000 + $100,000/day operating cost
- **District Manager Role**: Hire AI managers to run stores autonomously (charge 10% of profit)

### Refactoring Required: 🔧🔧🔧🔧🔧 (5/5)

#### Major Architectural Changes

**1. GameState Refactoring** - Split into `GameWorld` + `StoreState`
```kotlin
// Current: Single store
data class GameState(
    val money: Money,
    val inventory: Map<Int, InventoryState>,
    val currentTime: GameTime,
    // ... 20+ fields
)

// NEW: Multi-store hierarchy
data class GameWorld(
    val playerId: String,
    val corporateMoney: Money,           // Shared cash pool
    val stores: Map<String, StoreState>,  // storeId -> store
    val currentTime: GameTime,            // Shared world clock
    val supplyChain: SupplyChainState,    // Truck deliveries between stores
    val corporateUnlocks: CorporateUnlocks, // Chain-wide upgrades
)

data class StoreState(
    val storeId: String,
    val locationName: String,
    val inventory: Map<Int, InventoryState>,
    val hiredStaff: HiredEntityRegistry,
    val currentDayMetrics: DailyMetricsAccumulator,
    val storeConfig: StoreConfig,
    val trafficMultiplier: Float,        // Market-specific
    val rentPerDay: Money,
)
```

**2. GameEngine Refactoring** - Extract `StoreEngine` from `GameEngine`
```kotlin
// Current: Monolithic engine
class GameEngine(cache: ItemMetadataCache) {
    var state: GameState
    // ... manages one store
}

// NEW: World coordinator + per-store engines
class GameWorldEngine(cache: ItemMetadataCache) {
    var world: GameWorld
    private val storeEngines: Map<String, StoreEngine>
    
    fun tick(deltaMs: Long) {
        // Update shared time ONCE
        world = timeManager.update(world, deltaMs)
        
        // Parallel tick all stores (future: Coroutines)
        storeEngines.forEach { (id, engine) ->
            val updatedStore = engine.tick(world.currentTime, deltaMs)
            world = world.copy(stores = world.stores + (id to updatedStore))
        }
        
        // Update supply chain (trucks moving between stores)
        world = supplyChainManager.tick(world, deltaMs)
    }
}

class StoreEngine(val storeId: String, cache: ItemMetadataCache) {
    // Manages ONE store (current GameEngine logic moves here)
    fun tick(worldTime: GameTime, deltaMs: Long): StoreState
}
```

**3. ViewModel Refactoring** - Multi-store UI state
```kotlin
// Current: Single store projection
class GameViewModel {
    private val gameEngine: GameEngine
    private val _uiState = MutableStateFlow<GameUiState>(...)
}

// NEW: World + selected store
class GameViewModel {
    private val worldEngine: GameWorldEngine
    private val _worldUiState = MutableStateFlow<WorldUiState>(...)
    private val _selectedStoreId = MutableStateFlow<String>("store_01")
    
    // Switch which store the player is viewing
    fun selectStore(storeId: String) { ... }
    
    // Dispatch events to specific stores
    fun onEvent(storeId: String, event: GameEvent) { ... }
}
```

**4. Persistence Refactoring** - Serialize 5-20 stores
```kotlin
// GameStateSerializer becomes GameWorldSerializer
class GameWorldSerializer {
    fun serialize(world: GameWorld): String {
        val json = JSONObject()
        json.put("corporateMoney", world.corporateMoney.cents)
        json.put("currentTime", serializeGameTime(world.currentTime))
        
        // Serialize ALL stores (5-20x data size)
        val storesArray = JSONArray()
        world.stores.forEach { (id, store) ->
            storesArray.put(serializeStore(store))
        }
        json.put("stores", storesArray)
        
        json.put("supplyChain", serializeSupplyChain(world.supplyChain))
        return json.toString()
    }
}
```

**5. UI Refactoring** - Add store switcher navigation
```kotlin
// New top-level screen: Store Selection Map
@Composable
fun StoreMapScreen(
    stores: List<StoreUiState>,
    onSelectStore: (String) -> Unit,
    onExpandToNewLocation: (LocationDef) -> Unit
) {
    // Grid of store cards showing:
    // - Store name + location
    // - Daily revenue (last completed day)
    // - Current profit/loss
    // - Staff count
    // - Alert badges (out of stock, low cash, etc.)
}

// Add "Change Store" button to main navigation
// Current: [GAME | INVENTORY | STAFF | HISTORY | METRICS]
// NEW:     [← PREV STORE | GAME | INVENTORY | STAFF | ... | NEXT STORE →]
```

### Performance Impact: ⚠️⚠️⚠️⚠️ (4/5 - SEVERE)

#### Degradation Estimates (5 stores active)

| Metric | Current | With 5 Stores | Degradation |
|--------|---------|---------------|-------------|
| **Tick Time** (realistic) | 0.0095ms | **0.048ms** (5×) | Still 0.28% of frame budget ✅ |
| **Tick Time** (maximum) | 0.039ms | **0.195ms** (5×) | Still 1.17% of frame budget ✅ |
| **Memory Usage** | ~50 MB | **~200 MB** (4×) | ⚠️ Approaches low-RAM device limits |
| **Save File Size** | ~50 KB | **~250 KB** (5×) | ✅ Still negligible for SharedPreferences |
| **Serialization Time** | ~5ms | **~25ms** (5×) | ⚠️ Noticeable on app pause |

#### Mitigation Strategies

**1. Lazy Store Activation**
```kotlin
// Only tick stores the player has visited in last 30 game-days
val activeStores = world.stores.filter { (id, store) ->
    store.lastVisited.dayNumber >= world.currentTime.dayNumber - 30
}

activeStores.forEach { (id, engine) ->
    engine.tick(...)
}

// Background stores accumulate "simulated days" and fast-forward on next visit
```

**2. Simplified AI Simulation for Inactive Stores**
```kotlin
// Instead of full tick(), use statistical model for inactive stores
class StoreForecastSimulator {
    fun simulateDay(store: StoreState): DailyMetrics {
        // Estimate revenue = avgTransactionValue × avgCustomers × trafficMultiplier
        // Estimate costs = rent + wages
        // Return snapshot WITHOUT full tick simulation
    }
}
```

**3. Background Coroutine Processing**
```kotlin
class GameWorldEngine {
    fun tick(deltaMs: Long) = viewModelScope.launch {
        // Parallel store ticks using Dispatchers.Default
        val updatedStores = storeEngines.map { (id, engine) ->
            async(Dispatchers.Default) {
                id to engine.tick(world.currentTime, deltaMs)
            }
        }.awaitAll()
        
        world = world.copy(stores = updatedStores.toMap())
    }
}
```
**Expected**: 3-5× speedup on multi-core devices (Pixel 8a has 9 cores)

**4. Store State Compression**
```kotlin
// Don't save full transaction history for inactive stores
data class StoreState(
    val salesHistory: List<Transaction> = emptyList(),  // ❌ 1000+ transactions
    val recentSalesHistory: List<Transaction> = emptyList(),  // ✅ Last 50 only
    val archivedMetrics: List<DailyMetrics> = emptyList(),   // Compressed summary
)
```

### Implementation Complexity: 📊📊📊📊📊 (5/5)

**Estimated Effort**: 60-80 hours
- GameState → GameWorld refactoring: 15 hours
- Multi-engine architecture: 20 hours
- Supply chain system: 15 hours
- UI store switcher + map: 10 hours
- Persistence updates: 10 hours
- Testing: 15 hours

### Breaking Changes
- ❌ Existing save files incompatible (need migration or fresh start)
- ❌ All tests need rewrite (GameEngine → StoreEngine + GameWorldEngine)
- ❌ ViewModel event routing changes (need storeId parameter)

---

## Extreme Feature #2: Real-Time Competitor Stores 🥊

### Concept
AI-controlled competitor stores in the same market compete for customers. Players can undercut prices, poach staff, or use marketing campaigns to steal traffic.

### Feature Depth: 🎯🎯🎯🎯 (4/5)

### Gameplay Additions
- **3-5 AI Stores** per location (Walmart, Target, Local Grocery, etc.)
- **Dynamic Pricing**: Set item prices above/below base (affects profit margin vs traffic)
- **Price Wars**: Competitors respond to your price changes (match or undercut)
- **Market Share**: Customer traffic split among stores based on:
  - Price competitiveness (30%)
  - Store size/selection (30%)
  - Customer service rating (20%)
  - Marketing spend (20%)
- **Espionage**: Pay $5,000 to see competitor inventory/pricing
- **Marketing Campaigns**: 
  - Radio ads: $10,000 → +20% traffic for 3 days
  - Coupon mailers: $5,000 → +10% traffic for 1 day
  - Grand opening: One-time +50% traffic on new store launch

### Refactoring Required: 🔧🔧🔧 (3/5)

**1. Traffic System Overhaul**
```kotlin
// Current: TrafficManager generates customers independently
class TrafficManager {
    fun update(state: GameState, deltaSeconds: Double): List<TransactionRequest>
}

// NEW: Market-based traffic distribution
data class MarketState(
    val locationId: String,
    val totalMarketDemand: Float,  // Base customers per hour
    val competitors: List<CompetitorStore>,
    val marketShare: Map<String, Float>,  // storeId -> % of traffic
)

class MarketTrafficManager {
    fun update(market: MarketState, deltaSeconds: Double): Map<String, List<TransactionRequest>> {
        val totalDemand = calculateDemand(market)
        
        // Distribute customers based on market share
        return market.competitors.associate { competitor ->
            val share = market.marketShare[competitor.id] ?: 0f
            val requests = generateCustomers(totalDemand * share, deltaSeconds)
            competitor.id to requests
        }
    }
    
    private fun calculateMarketShare(market: MarketState): Map<String, Float> {
        // Weighted scoring: price + selection + service + marketing
    }
}
```

**2. Dynamic Pricing System**
```kotlin
data class PricingStrategy(
    val itemPriceMultipliers: Map<Int, Float>,  // itemId -> multiplier (0.8 = 20% off)
    val globalPriceMultiplier: Float = 1.0f,     // Store-wide discount
    val activeCampaigns: List<MarketingCampaign>,
)

data class MarketingCampaign(
    val type: CampaignType,  // RADIO, COUPON, GRAND_OPENING
    val cost: Money,
    val trafficBoost: Float,
    val durationDays: Int,
    val startDay: Int,
)

// Modify TransactionEngine to use dynamic pricing
class TransactionEngine(
    private val pricingStrategy: PricingStrategy
) {
    fun calculatePrice(itemId: Int, basePrice: Money): Money {
        val itemMultiplier = pricingStrategy.itemPriceMultipliers[itemId] ?: 1.0f
        val globalMultiplier = pricingStrategy.globalPriceMultiplier
        return basePrice * itemMultiplier * globalMultiplier
    }
}
```

**3. AI Competitor Logic**
```kotlin
class CompetitorAI(
    val strategy: CompetitorStrategy  // AGGRESSIVE, CONSERVATIVE, PREMIUM
) {
    fun decideAction(market: MarketState, ownStore: CompetitorStore): CompetitorAction {
        return when (strategy) {
            AGGRESSIVE -> {
                // Match lowest price in market
                val lowestPrice = market.competitors.minOf { it.avgPrice }
                if (ownStore.avgPrice > lowestPrice * 1.05f) {
                    CompetitorAction.LowerPrices(target = lowestPrice * 0.98f)
                } else {
                    CompetitorAction.DoNothing
                }
            }
            CONSERVATIVE -> {
                // Only respond if losing >20% market share
                val ownShare = market.marketShare[ownStore.id] ?: 0f
                if (ownShare < 0.15f) {
                    CompetitorAction.MarketingCampaign(CampaignType.RADIO)
                } else {
                    CompetitorAction.DoNothing
                }
            }
            PREMIUM -> {
                // Never compete on price, focus on service
                CompetitorAction.HireStaff(EntityType.CUSTOMER_SERVICE_REPRESENTATIVES)
            }
        }
    }
}
```

### Performance Impact: ⚠️⚠️⚠️ (3/5 - MODERATE)

| Metric | Current | With Competitors | Degradation |
|--------|---------|------------------|-------------|
| **Tick Time** | 0.0095ms | **0.028ms** (3×) | Still 0.17% of frame budget ✅ |
| **AI Decision Overhead** | 0ms | **+0.05ms per location** | Runs once per game-hour, not per tick |
| **Memory** | ~50 MB | **~80 MB** | ✅ Acceptable |

**Mitigation**: AI decisions run on game-hour boundaries (not per tick), so overhead is minimal.

### Implementation Complexity: 📊📊📊📊 (4/5)

**Estimated Effort**: 40-50 hours
- Market share calculation: 10 hours
- Dynamic pricing system: 15 hours
- Competitor AI: 15 hours
- UI (pricing screen, market analysis): 10 hours

---

## Extreme Feature #3: Employee Personality & Morale System 😊😐😢

### Concept
Staff are no longer interchangeable—each has unique personality traits, morale that affects performance, and can quit if unhappy.

### Feature Depth: 🎯🎯🎯🎯 (4/5)

### Gameplay Additions
- **Personality System**: 12 traits beyond current `EntityTrait` (Cheerful, Grumpy, Lazy, Perfectionist, etc.)
- **Morale Meter** (0-100): Affects work speed (50 morale = 50% speed)
- **Morale Factors**:
  - Base wage vs. market rate (+10 if paid above average, -10 if below)
  - Workload (hours worked per week: -5 per hour over 40)
  - Store cleanliness (-20 if trash system added and not maintained)
  - Customer complaints (-10 per complaint)
  - Break room quality (+15 if upgraded break room purchased)
- **Quitting**: If morale < 20 for 3 consecutive days, employee quits with 1-day notice
- **Raises**: Pay +10% to boost morale by +20
- **Employee Requests**: "I need a raise" / "Can I work fewer hours?" (accept/decline)

### Refactoring Required: 🔧🔧🔧🔧 (4/5)

**1. Expand HiredEntity**
```kotlin
// Current: Minimal staff state
data class HiredEntity(
    val id: String,
    val name: String,
    val entityDefinition: EntityDef,
    val entityType: EntityType,
    val trait: EntityTrait,  // EFFICIENT, FRIENDLY, HARDWORKER
)

// NEW: Full personality + state
data class HiredEntity(
    val id: String,
    val name: String,
    val entityDefinition: EntityDef,
    val entityType: EntityType,
    val baseTrait: EntityTrait,
    
    // NEW FIELDS
    val personality: Set<PersonalityTrait>,  // Up to 3 traits
    val morale: Int = 100,                    // 0-100
    val wage: Money,                           // Hourly wage
    val hoursWorkedThisWeek: Float = 0f,
    val daysLowMorale: Int = 0,               // Consecutive days morale < 20
    val hasActiveRequest: EmployeeRequest? = null,
    val hiredOnDay: Int,
)

enum class PersonalityTrait {
    CHEERFUL,        // +5% customer satisfaction
    GRUMPY,          // -5% customer satisfaction, +10% work speed
    LAZY,            // -20% work speed, -10 morale loss from overwork
    PERFECTIONIST,   // +15% work speed, -5 morale if rushed
    SOCIAL,          // +10% morale from co-workers
    LONER,           // -10% morale from crowded shifts
    AMBITIOUS,       // Requests raise every 30 days
    LOYAL,           // Never quits regardless of morale
    // ... 8 total
}
```

**2. StaffManager Extension**
```kotlin
class StaffManager {
    // NEW METHODS
    fun updateMorale(state: GameState): GameState {
        val registry = state.hiredStaff
        val updatedEntities = registry.entities.map { entity ->
            val morale = calculateMorale(entity, state)
            val updated = entity.copy(
                morale = morale.coerceIn(0, 100),
                daysLowMorale = if (morale < 20) entity.daysLowMorale + 1 else 0
            )
            
            // Auto-quit if morale low for 3 days
            if (updated.daysLowMorale >= 3) {
                null  // Remove from registry
            } else {
                updated
            }
        }.filterNotNull()
        
        return state.copy(hiredStaff = registry.copy(entities = updatedEntities))
    }
    
    private fun calculateMorale(entity: HiredEntity, state: GameState): Int {
        var morale = entity.morale
        
        // Wage factor
        val avgWage = state.hiredStaff.entities
            .filter { it.entityType == entity.entityType }
            .map { it.wage.cents }
            .average()
        morale += when {
            entity.wage.cents > avgWage * 1.1 -> 10
            entity.wage.cents < avgWage * 0.9 -> -10
            else -> 0
        }
        
        // Workload factor
        val overtimeHours = (entity.hoursWorkedThisWeek - 40).coerceAtLeast(0f)
        morale -= (overtimeHours * 5).toInt()
        
        // Personality modifiers
        if (PersonalityTrait.LAZY in entity.personality) {
            morale += (overtimeHours * 2).toInt()  // Lazy employees hate overtime less
        }
        
        return morale
    }
    
    fun giveRaise(state: GameState, entityId: String, raisePercent: Float): GameState {
        val entity = state.hiredStaff.getById(entityId) ?: return state
        val newWage = entity.wage * (1 + raisePercent)
        val updated = entity.copy(
            wage = newWage,
            morale = (entity.morale + 20).coerceAtMost(100)
        )
        return state.copy(hiredStaff = state.hiredStaff.update(updated))
    }
}
```

**3. Work Speed Modifiers**
```kotlin
// Modify cashier/stocker progress accumulators
fun advanceCashierProgress(cashierCount: Int, ...): Int {
    val totalProgress = cashierCount * baseRate * deltaSeconds * multiplier
    
    // NEW: Apply morale modifiers
    val moraleAdjusted = state.hiredStaff.entities
        .filter { it.entityType == EntityType.CASHIERS }
        .sumOf { entity ->
            val moraleMultiplier = entity.morale / 100f  // 50 morale = 50% speed
            val personalityMultiplier = when {
                PersonalityTrait.GRUMPY in entity.personality -> 1.1f
                PersonalityTrait.LAZY in entity.personality -> 0.8f
                else -> 1.0f
            }
            baseRate * moraleMultiplier * personalityMultiplier
        }
    
    cashierProgress += moraleAdjusted * deltaSeconds * multiplier
    return cashierProgress.toInt().also { cashierProgress -= it }
}
```

### Performance Impact: ⚠️⚠️ (2/5 - MILD)

| Metric | Current | With Morale System | Degradation |
|--------|---------|-------------------|-------------|
| **Tick Time** (50 staff) | 0.039ms | **0.052ms** (1.3×) | Still 0.31% of frame budget ✅ |
| **Morale Calc** | 0ms | **+0.013ms per day rollover** | Only runs at midnight |

**Why Low Impact**: Morale calculations run once per day (midnight), not per tick. Per-tick overhead is minimal (just reading morale value).

### Implementation Complexity: 📊📊📊 (3/5)

**Estimated Effort**: 30-40 hours
- Personality trait system: 10 hours
- Morale calculation logic: 10 hours
- Raise/request UI dialogs: 10 hours
- Testing: 10 hours

---

## Extreme Feature #4: Crime & Security System 🚨

### Concept
Stores face theft (shoplifters, employee theft, break-ins) and must invest in security measures (cameras, guards, alarms).

### Feature Depth: 🎯🎯🎯🎯 (4/5)

### Gameplay Additions
- **Shoplifting**: 1-5% of customers attempt theft (increases with low staff-to-customer ratio)
- **Detection Rate**: Base 10% without security, up to 90% with full security
- **Loss Types**:
  - Caught shoplifters: Banned from store (traffic -1%), item returned
  - Uncaught shoplifters: Item lost, revenue lost
  - Employee theft: 1% chance per employee per week (morale < 30 increases to 5%)
  - Break-ins: 0.1% chance per night (closed hours), loses 10-50 items
- **Security Upgrades**:
  - Security cameras: $25,000 → +30% detection rate
  - Security guard (hired entity): $150/day wage, +40% detection, prevents break-ins
  - Alarm system: $10,000 → prevents 80% of break-ins
  - Receipt checkers: $50/day wage, +20% detection at exit

### Refactoring Required: 🔧🔧🔧 (3/5)

**1. Add Security State**
```kotlin
data class StoreState(
    // ... existing fields ...
    val securityConfig: SecurityConfig = SecurityConfig(),
    val theftHistory: List<TheftEvent> = emptyList(),
)

data class SecurityConfig(
    val hasCameras: Boolean = false,
    val hasAlarm: Boolean = false,
    val securityGuards: Int = 0,
    val receiptCheckers: Int = 0,
)

sealed class TheftEvent {
    data class ShopliftAttempt(
        val itemId: Int,
        val caught: Boolean,
        val customerId: String,
        val timestamp: GameTime,
    ) : TheftEvent()
    
    data class EmployeeTheft(
        val employeeId: String,
        val itemsStolen: List<Int>,
        val caught: Boolean,
        val timestamp: GameTime,
    ) : TheftEvent()
    
    data class BreakIn(
        val itemsLost: Map<Int, Int>,  // itemId -> quantity
        val valueLost: Money,
        val timestamp: GameTime,
    ) : TheftEvent()
}
```

**2. TrafficManager Extension**
```kotlin
class TrafficManager {
    fun generateTheftAttempts(
        state: GameState,
        transactionRequests: List<TransactionRequest>
    ): List<TheftAttempt> {
        val theftProbability = calculateTheftProbability(state)
        
        return transactionRequests.mapNotNull { request ->
            if (Random.nextFloat() < theftProbability) {
                val itemId = selectTheftTarget(state)
                TheftAttempt(
                    itemId = itemId,
                    detectionRate = calculateDetectionRate(state)
                )
            } else null
        }
    }
    
    private fun calculateTheftProbability(state: GameState): Float {
        var baseRate = 0.03f  // 3% base
        
        // Understaffing increases theft
        val customerCount = state.pendingCustomers
        val staffCount = state.hiredStaff.totalCount()
        val ratio = customerCount / staffCount.toFloat()
        if (ratio > 10) baseRate *= 1.5f  // High traffic, low staff = more theft
        
        return baseRate.coerceAtMost(0.10f)  // Cap at 10%
    }
    
    private fun calculateDetectionRate(state: GameState): Float {
        var rate = 0.10f  // 10% base (natural vigilance)
        
        if (state.securityConfig.hasCameras) rate += 0.30f
        rate += state.securityConfig.securityGuards * 0.40f
        rate += state.securityConfig.receiptCheckers * 0.20f
        
        return rate.coerceAtMost(0.90f)  // Cap at 90%
    }
}
```

**3. Theft Processing**
```kotlin
class SecurityManager {
    fun processTheftAttempt(state: GameState, attempt: TheftAttempt): GameState {
        val detected = Random.nextFloat() < attempt.detectionRate
        
        return if (detected) {
            // Caught: Return item, ban customer
            val event = TheftEvent.ShopliftAttempt(
                itemId = attempt.itemId,
                caught = true,
                customerId = attempt.customerId,
                timestamp = state.currentTime
            )
            state.copy(
                theftHistory = state.theftHistory + event,
                // No inventory loss
            )
        } else {
            // Not caught: Lose item
            val inventory = state.inventory[attempt.itemId] ?: return state
            val updated = inventory.copy(shelfStock = inventory.shelfStock - 1)
            val event = TheftEvent.ShopliftAttempt(
                itemId = attempt.itemId,
                caught = false,
                customerId = attempt.customerId,
                timestamp = state.currentTime
            )
            state.copy(
                inventory = state.inventory + (attempt.itemId to updated),
                theftHistory = state.theftHistory + event,
                currentDayMetrics = state.currentDayMetrics.copy(
                    theftLosses = state.currentDayMetrics.theftLosses + 1
                )
            )
        }
    }
    
    fun checkForBreakIn(state: GameState): GameState {
        // Only check when store is CLOSED
        if (state.storeState != StoreState.CLOSED) return state
        
        val probability = if (state.securityConfig.hasAlarm) 0.002f else 0.01f
        if (Random.nextFloat() > probability) return state
        
        // Break-in occurs
        val itemsToSteal = Random.nextInt(10, 50)
        val stolenItems = mutableMapOf<Int, Int>()
        var valueLost = Money.ZERO
        
        repeat(itemsToSteal) {
            val itemId = state.inventory.keys.random()
            val inventory = state.inventory[itemId] ?: return@repeat
            if (inventory.shelfStock > 0) {
                stolenItems[itemId] = stolenItems.getOrDefault(itemId, 0) + 1
                valueLost += itemMetadataCache.get(itemId)?.price ?: Money.ZERO
            }
        }
        
        // Update inventory
        val newInventory = state.inventory.mapValues { (itemId, inv) ->
            val stolen = stolenItems[itemId] ?: 0
            inv.copy(shelfStock = (inv.shelfStock - stolen).coerceAtLeast(0))
        }
        
        val event = TheftEvent.BreakIn(
            itemsLost = stolenItems,
            valueLost = valueLost,
            timestamp = state.currentTime
        )
        
        return state.copy(
            inventory = newInventory,
            theftHistory = state.theftHistory + event,
            currentDayMetrics = state.currentDayMetrics.copy(
                breakInLosses = valueLost
            )
        )
    }
}
```

### Performance Impact: ⚠️ (1/5 - MINIMAL)

| Metric | Current | With Crime System | Degradation |
|--------|---------|-------------------|-------------|
| **Tick Time** | 0.0095ms | **0.0105ms** (1.1×) | Still 0.06% of frame budget ✅ |
| **Break-In Check** | 0ms | **+0.001ms per midnight** | Negligible |

**Why Low Impact**: Theft attempts are probabilistic and rare (3% of customers), break-ins are 0.1% per night.

### Implementation Complexity: 📊📊📊 (3/5)

**Estimated Effort**: 25-35 hours
- Security state + upgrades: 10 hours
- Theft detection logic: 10 hours
- UI (security panel, theft alerts): 10 hours
- Testing: 5 hours

---

## Extreme Feature #5: Weather & Seasonal Events ⛈️🌞❄️

### Concept
Dynamic weather affects customer traffic, item demand, and store operations. Seasonal events (holidays, back-to-school) create demand spikes.

### Feature Depth: 🎯🎯🎯🎯🎯 (5/5)

### Gameplay Additions
- **Weather System**: 
  - Sunny (60% of days): Normal traffic
  - Rain (25% of days): +10% traffic (people avoid going out, stock up when they do)
  - Snow (10% in winter): +20% traffic for FROZEN, -30% overall traffic
  - Heatwave (5% in summer): +30% traffic for DRINKS, -10% overall
  - Storm (1%): -50% traffic, possible power outage (no sales for 1-4 hours)
- **Seasonal Demand Shifts**:
  - **Winter** (Dec-Feb): FROZEN +20%, PRODUCE -10%
  - **Spring** (Mar-May): PRODUCE +30%, BAKERY +15%
  - **Summer** (Jun-Aug): DRINKS +40%, FROZEN +25%, SNACKS +20%
  - **Fall** (Sep-Nov): BAKERY +20%, SNACKS +10%
- **Holiday Events** (30 pre-defined):
  - Christmas (Dec 25): +100% traffic, +50% basket size, BAKERY/FROZEN surge
  - Thanksgiving (Nov 25): +80% traffic, BAKERY/MEAT surge
  - Super Bowl Sunday (Feb): +40% traffic, SNACKS/DRINKS surge
  - Back-to-School (late Aug): +30% traffic, SNACKS surge
  - Valentine's Day (Feb 14): BAKERY +100% (chocolates, flowers)
  - Easter (Apr): BAKERY +80% (candy)
- **Event Preparation**: UI alerts 3 days before major event, suggest restocking
- **Power Outages**: 1% chance during storms, lose 1-4 hours of sales + spoilage risk for FROZEN/DAIRY/MEAT

### Refactoring Required: 🔧🔧🔧🔧 (4/5)

**1. Add World Environment State**
```kotlin
data class GameState(
    // ... existing fields ...
    val currentWeather: Weather = Weather.SUNNY,
    val currentSeason: Season,  // Derived from currentTime
    val upcomingEvents: List<HolidayEvent> = emptyList(),
    val powerOutage: PowerOutageState? = null,
)

enum class Weather {
    SUNNY, RAINY, SNOWY, HEATWAVE, STORM
}

enum class Season {
    WINTER, SPRING, SUMMER, FALL;
    
    companion object {
        fun fromGameTime(time: GameTime): Season {
            val month = ((time.totalMinutesElapsed / 1440) % 365) / 30
            return when (month) {
                in 0..2 -> WINTER
                in 3..5 -> SPRING
                in 6..8 -> SUMMER
                else -> FALL
            }
        }
    }
}

data class HolidayEvent(
    val name: String,
    val dayOfYear: Int,  // 1-365
    val trafficMultiplier: Float,
    val categoryBoosts: Map<ItemCategory, Float>,  // BAKERY -> 2.0 (100% increase)
    val basketSizeMultiplier: Float = 1.0f,
)

data class PowerOutageState(
    val startTime: GameTime,
    val durationMinutes: Int,
    val itemsSpoiled: Map<Int, Int> = emptyMap(),  // itemId -> quantity
)
```

**2. Weather Generation**
```kotlin
class WeatherManager {
    fun updateWeather(state: GameState): GameState {
        // Generate new weather at midnight
        val currentDay = state.currentTime.dayNumber
        if (currentDay == lastWeatherUpdateDay) return state
        
        val season = Season.fromGameTime(state.currentTime)
        val newWeather = generateWeather(season)
        
        lastWeatherUpdateDay = currentDay
        return state.copy(currentWeather = newWeather)
    }
    
    private fun generateWeather(season: Season): Weather {
        val roll = Random.nextFloat()
        return when (season) {
            Season.WINTER -> when {
                roll < 0.10 -> Weather.SNOWY
                roll < 0.30 -> Weather.RAINY
                roll < 0.35 -> Weather.STORM
                else -> Weather.SUNNY
            }
            Season.SUMMER -> when {
                roll < 0.05 -> Weather.HEATWAVE
                roll < 0.20 -> Weather.RAINY
                roll < 0.22 -> Weather.STORM
                else -> Weather.SUNNY
            }
            else -> when {
                roll < 0.25 -> Weather.RAINY
                roll < 0.26 -> Weather.STORM
                else -> Weather.SUNNY
            }
        }
    }
    
    fun checkForPowerOutage(state: GameState): GameState {
        if (state.currentWeather != Weather.STORM) return state
        if (state.powerOutage != null) return state  // Already in outage
        
        if (Random.nextFloat() < 0.01f) {  // 1% chance during storm
            val duration = Random.nextInt(60, 240)  // 1-4 hours
            return state.copy(
                powerOutage = PowerOutageState(
                    startTime = state.currentTime,
                    durationMinutes = duration
                )
            )
        }
        return state
    }
    
    fun resolvePowerOutage(state: GameState): GameState {
        val outage = state.powerOutage ?: return state
        val elapsed = state.currentTime.totalMinutesElapsed - outage.startTime.totalMinutesElapsed
        
        if (elapsed >= outage.durationMinutes) {
            // Calculate spoilage for perishables
            val spoilage = calculateSpoilage(state, outage.durationMinutes)
            
            return state.copy(
                powerOutage = null,
                inventory = applySpoilage(state.inventory, spoilage),
                currentDayMetrics = state.currentDayMetrics.copy(
                    spoilageLosses = spoilage.values.sum()
                )
            )
        }
        return state
    }
    
    private fun calculateSpoilage(state: GameState, outageMinutes: Int): Map<Int, Int> {
        // FROZEN/DAIRY/MEAT spoil if outage > 2 hours
        if (outageMinutes < 120) return emptyMap()
        
        val perishableCategories = setOf(
            ItemCategory.FROZEN,
            ItemCategory.DAIRY,
            ItemCategory.MEAT
        )
        
        return state.inventory
            .filter { (itemId, _) ->
                val category = itemMetadataCache.get(itemId)?.category
                category in perishableCategories
            }
            .mapValues { (_, inv) ->
                // Lose 50% of shelf stock (no backroom loss, it's refrigerated separately)
                inv.shelfStock / 2
            }
    }
}
```

**3. Traffic/Demand Modifiers**
```kotlin
class TrafficManager {
    fun update(state: GameState, deltaSeconds: Double): List<TransactionRequest> {
        val baseRate = getCurrentHourTrafficRate(state)
        
        // Apply weather modifier
        val weatherMultiplier = when (state.currentWeather) {
            Weather.SUNNY -> 1.0f
            Weather.RAINY -> 1.1f
            Weather.SNOWY -> 0.7f
            Weather.HEATWAVE -> 0.9f
            Weather.STORM -> 0.5f
        }
        
        // Apply seasonal modifier
        val seasonMultiplier = 1.0f  // Base
        
        // Apply holiday modifier
        val holidayMultiplier = getActiveHolidayMultiplier(state)
        
        // Power outage = no customers
        if (state.powerOutage != null) {
            return emptyList()
        }
        
        val finalRate = baseRate * weatherMultiplier * seasonMultiplier * holidayMultiplier
        
        // Generate customers
        return generateCustomers(finalRate, deltaSeconds, state)
    }
    
    private fun getActiveHolidayMultiplier(state: GameState): Float {
        val dayOfYear = (state.currentTime.totalMinutesElapsed / 1440) % 365
        val activeEvent = HOLIDAY_CALENDAR.find { it.dayOfYear == dayOfYear.toInt() }
        return activeEvent?.trafficMultiplier ?: 1.0f
    }
    
    companion object {
        val HOLIDAY_CALENDAR = listOf(
            HolidayEvent(
                name = "Christmas",
                dayOfYear = 359,  // Dec 25
                trafficMultiplier = 2.0f,
                categoryBoosts = mapOf(
                    ItemCategory.BAKERY to 1.5f,
                    ItemCategory.FROZEN to 1.3f
                ),
                basketSizeMultiplier = 1.5f
            ),
            HolidayEvent(
                name = "Thanksgiving",
                dayOfYear = 329,  // Nov 25
                trafficMultiplier = 1.8f,
                categoryBoosts = mapOf(
                    ItemCategory.BAKERY to 1.8f,
                    ItemCategory.MEAT to 2.0f
                ),
                basketSizeMultiplier = 1.4f
            ),
            // ... 28 more holidays
        )
    }
}
```

**4. Item Demand Modifier in Transaction Generation**
```kotlin
class TransactionEngine {
    fun generateRandomTransaction(state: GameState): GameState {
        val availableItems = getAvailableItems(state)
        
        // Apply weather/season/holiday boosts to purchase weights
        val adjustedWeights = availableItems.map { itemId ->
            val metadata = itemMetadataCache.get(itemId)!!
            var weight = metadata.purchaseWeight
            
            // Weather boost
            when (state.currentWeather) {
                Weather.HEATWAVE -> {
                    if (metadata.category == ItemCategory.DRINKS) weight *= 1.3f
                }
                Weather.SNOWY -> {
                    if (metadata.category == ItemCategory.FROZEN) weight *= 1.2f
                }
                else -> {}
            }
            
            // Season boost
            val season = Season.fromGameTime(state.currentTime)
            when (season) {
                Season.SUMMER -> {
                    if (metadata.category in setOf(ItemCategory.DRINKS, ItemCategory.FROZEN)) {
                        weight *= 1.4f
                    }
                }
                Season.WINTER -> {
                    if (metadata.category == ItemCategory.FROZEN) weight *= 1.2f
                }
                else -> {}
            }
            
            // Holiday boost
            val dayOfYear = (state.currentTime.totalMinutesElapsed / 1440) % 365
            val activeEvent = TrafficManager.HOLIDAY_CALENDAR.find { it.dayOfYear == dayOfYear.toInt() }
            activeEvent?.categoryBoosts?.get(metadata.category)?.let { boost ->
                weight *= boost
            }
            
            itemId to weight
        }.toMap()
        
        // Weighted random selection
        val selectedItems = weightedRandomSelection(adjustedWeights, basketSize)
        
        // ... rest of transaction logic
    }
}
```

### Performance Impact: ⚠️⚠️ (2/5 - MILD)

| Metric | Current | With Weather/Seasons | Degradation |
|--------|---------|---------------------|-------------|
| **Tick Time** | 0.0095ms | **0.0115ms** (1.2×) | Still 0.07% of frame budget ✅ |
| **Weather Check** | 0ms | **+0.002ms per midnight** | Negligible |
| **Demand Calculation** | Constant | **+15% per transaction** | Still sub-millisecond |

**Why Low Impact**: Weather updates once per day, demand modifiers are simple multipliers.

### Implementation Complexity: 📊📊📊📊 (4/5)

**Estimated Effort**: 45-55 hours
- Weather system: 15 hours
- Holiday calendar + logic: 15 hours
- Power outage + spoilage: 10 hours
- UI (weather display, event alerts): 10 hours
- Testing: 10 hours

---

## Extreme Feature #6: Store Layout & Optimization 🏗️

### Concept
Design the physical store layout—place shelves, checkouts, break rooms—to optimize customer flow and reduce theft. Bad layouts cause traffic jams and abandoned baskets.

### Feature Depth: 🎯🎯🎯🎯🎯 (5/5)
**Why**: Adds a spatial puzzle layer to the management sim. Players must balance aesthetics vs. efficiency.

### Gameplay Additions
- **Grid-Based Layout Editor**: 20×30 tile grid (600 tiles)
- **Placeable Objects**:
  - Shelves (1×3 tiles): Display items, require stocking
  - Checkouts (2×2 tiles): Cashier stations (max 1 per checkout)
  - Aisles (1×1 tiles): Customer pathfinding
  - Break Room (5×5 tiles): Boosts employee morale (+10)
  - Offices (3×3 tiles): Required for administrative tasks
  - Restrooms (3×3 tiles): +5% customer satisfaction
  - Security Cameras (1×1 tiles): Cover 5-tile radius
- **Layout Efficiency Metrics**:
  - **Customer Flow**: Pathfinding from entrance → aisles → checkouts
  - **Traffic Jams**: If >5 customers in same tile, -10% satisfaction
  - **Dead Zones**: Shelves not on customer paths have -50% sales
  - **Security Coverage**: Uncovered shelves have +50% theft rate
- **Store Size Limits**:
  - MOM_AND_POP: 10×15 grid (150 tiles)
  - SMALL_GROCERY: 15×20 grid (300 tiles)
  - GROCERY_STORE: 20×30 grid (600 tiles)
  - SUPERSTORE: 30×40 grid (1200 tiles)

### Refactoring Required: 🔧🔧🔧🔧🔧 (5/5 - MASSIVE)

**1. Add Spatial State**
```kotlin
data class StoreLayout(
    val gridWidth: Int,   // Tiles
    val gridHeight: Int,  // Tiles
    val tiles: Map<Coordinate, LayoutTile>,
    val objects: Map<String, PlacedObject>,  // objectId -> object
)

data class Coordinate(val x: Int, val y: Int)

sealed class LayoutTile {
    object Empty : LayoutTile()
    object Aisle : LayoutTile()
    data class Occupied(val objectId: String) : LayoutTile()
}

sealed class PlacedObject {
    abstract val id: String
    abstract val position: Coordinate
    abstract val size: Pair<Int, Int>  // width × height in tiles
    
    data class Shelf(
        override val id: String,
        override val position: Coordinate,
        override val size: Pair<Int, Int> = 1 to 3,
        val assignedCategory: ItemCategory,
        val capacity: Int = 50,  // Items per shelf
    ) : PlacedObject()
    
    data class Checkout(
        override val id: String,
        override val position: Coordinate,
        override val size: Pair<Int, Int> = 2 to 2,
        val assignedCashier: String? = null,  // HiredEntity.id
    ) : PlacedObject()
    
    data class BreakRoom(
        override val id: String,
        override val position: Coordinate,
        override val size: Pair<Int, Int> = 5 to 5,
        val quality: BreakRoomQuality = BreakRoomQuality.BASIC,
    ) : PlacedObject()
    
    // ... Office, Restroom, SecurityCamera
}

enum class BreakRoomQuality {
    BASIC,    // +5 morale
    UPGRADED, // +10 morale, costs $10,000
    PREMIUM   // +20 morale, costs $50,000
}
```

**2. Pathfinding System (A* Algorithm)**
```kotlin
class CustomerPathfinder {
    fun findPath(
        layout: StoreLayout,
        start: Coordinate,
        goal: Coordinate
    ): List<Coordinate>? {
        // A* pathfinding over Aisle tiles
        val openSet = PriorityQueue<PathNode>(compareBy { it.fScore })
        val cameFrom = mutableMapOf<Coordinate, Coordinate>()
        val gScore = mutableMapOf<Coordinate, Int>().withDefault { Int.MAX_VALUE }
        val fScore = mutableMapOf<Coordinate, Int>().withDefault { Int.MAX_VALUE }
        
        gScore[start] = 0
        fScore[start] = heuristic(start, goal)
        openSet.add(PathNode(start, 0, fScore[start]!!))
        
        while (openSet.isNotEmpty()) {
            val current = openSet.poll().coord
            
            if (current == goal) {
                return reconstructPath(cameFrom, current)
            }
            
            for (neighbor in getNeighbors(layout, current)) {
                val tentativeGScore = gScore.getValue(current) + 1
                
                if (tentativeGScore < gScore.getValue(neighbor)) {
                    cameFrom[neighbor] = current
                    gScore[neighbor] = tentativeGScore
                    fScore[neighbor] = tentativeGScore + heuristic(neighbor, goal)
                    
                    if (openSet.none { it.coord == neighbor }) {
                        openSet.add(PathNode(neighbor, tentativeGScore, fScore[neighbor]!!))
                    }
                }
            }
        }
        
        return null  // No path found
    }
    
    private fun heuristic(a: Coordinate, b: Coordinate): Int {
        return abs(a.x - b.x) + abs(a.y - b.y)  // Manhattan distance
    }
    
    private fun getNeighbors(layout: StoreLayout, coord: Coordinate): List<Coordinate> {
        return listOf(
            Coordinate(coord.x - 1, coord.y),
            Coordinate(coord.x + 1, coord.y),
            Coordinate(coord.x, coord.y - 1),
            Coordinate(coord.x, coord.y + 1),
        ).filter { neighbor ->
            layout.tiles[neighbor] is LayoutTile.Aisle  // Only walk on aisles
        }
    }
}

data class PathNode(val coord: Coordinate, val gScore: Int, val fScore: Int)
```

**3. Layout Efficiency Calculator**
```kotlin
class LayoutAnalyzer {
    fun analyzeLayout(layout: StoreLayout): LayoutAnalysis {
        val shelves = layout.objects.values.filterIsInstance<PlacedObject.Shelf>()
        val checkouts = layout.objects.values.filterIsInstance<PlacedObject.Checkout>()
        val entrance = Coordinate(layout.gridWidth / 2, 0)  // Top center
        
        val shelfEfficiency = shelves.map { shelf ->
            val shelfCenter = Coordinate(shelf.position.x + shelf.size.first / 2, shelf.position.y + shelf.size.second / 2)
            
            // Can customers reach this shelf?
            val pathFromEntrance = pathfinder.findPath(layout, entrance, shelfCenter)
            val isReachable = pathFromEntrance != null
            
            // Security coverage
            val cameras = layout.objects.values.filterIsInstance<PlacedObject.SecurityCamera>()
            val isCovered = cameras.any { camera ->
                distance(camera.position, shelfCenter) <= 5
            }
            
            ShelfEfficiency(
                shelfId = shelf.id,
                reachable = isReachable,
                covered = isCovered,
                pathLength = pathFromEntrance?.size ?: Int.MAX_VALUE
            )
        }
        
        val avgPathLength = shelfEfficiency.filter { it.reachable }.map { it.pathLength }.average()
        val coveragePercent = shelfEfficiency.count { it.covered } / shelfEfficiency.size.toFloat()
        
        return LayoutAnalysis(
            avgCustomerPathLength = avgPathLength,
            securityCoverage = coveragePercent,
            unreachableShelves = shelfEfficiency.count { !it.reachable },
            totalCheckouts = checkouts.size,
            efficiencyScore = calculateEfficiencyScore(avgPathLength, coveragePercent)
        )
    }
    
    private fun calculateEfficiencyScore(avgPath: Double, coverage: Float): Float {
        // Lower path = better (0-50 tiles normalized to 1.0-0.0)
        val pathScore = (1 - (avgPath / 50)).coerceIn(0.0, 1.0)
        
        // Higher coverage = better (0-1.0)
        val coverageScore = coverage
        
        return ((pathScore * 0.6 + coverageScore * 0.4) * 100).toFloat()  // 0-100 score
    }
}

data class LayoutAnalysis(
    val avgCustomerPathLength: Double,
    val securityCoverage: Float,
    val unreachableShelves: Int,
    val totalCheckouts: Int,
    val efficiencyScore: Float,  // 0-100
)
```

**4. UI: Drag-and-Drop Layout Editor**
```kotlin
@Composable
fun LayoutEditorScreen(
    layout: StoreLayout,
    analysis: LayoutAnalysis,
    onPlaceObject: (PlacedObject) -> Unit,
    onRemoveObject: (String) -> Unit,
) {
    Column {
        // Metrics panel
        LayoutMetricsPanel(analysis)
        
        // Grid editor
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.gridWidth),
            modifier = Modifier.fillMaxSize()
        ) {
            items(layout.gridHeight * layout.gridWidth) { index ->
                val x = index % layout.gridWidth
                val y = index / layout.gridWidth
                val coord = Coordinate(x, y)
                
                LayoutTile(
                    tile = layout.tiles[coord] ?: LayoutTile.Empty,
                    onClick = { /* place/remove object */ }
                )
            }
        }
        
        // Object palette (bottom toolbar)
        ObjectPalette(
            onSelectObject = { type -> /* start placement mode */ }
        )
    }
}
```

### Performance Impact: ⚠️⚠️⚠️⚠️⚠️ (5/5 - EXTREME)

| Metric | Current | With Spatial Layout | Degradation |
|--------|---------|-------------------|-------------|
| **Pathfinding** | 0ms | **+1-5ms per customer** | ⚠️ Major bottleneck |
| **Tick Time** (100 customers) | 0.0095ms | **+150ms pathfinding** | ⚠️ Exceeds 16ms frame budget! |
| **Memory** | ~50 MB | **+100 MB** (grid + paths) | ⚠️ High |
| **Rendering** | Simple lists | **Grid UI with 600-1200 tiles** | ⚠️ Complex layout |

#### Critical Issue: Pathfinding is O(n² log n)

**Why This Breaks 60 FPS**:
- A* pathfinding for 20×30 grid: ~10-50ms per path
- With 100 customers: 100 paths × 10ms = **1000ms per tick** ❌
- **60 FPS requires 16.67ms total** → this is 60× slower!

#### Mitigation Strategies

**1. Cached Pathfinding** (Best Option)
```kotlin
class LayoutPathCache {
    private val cache = mutableMapOf<Pair<Coordinate, Coordinate>, List<Coordinate>?>()
    
    fun getPath(layout: StoreLayout, start: Coordinate, goal: Coordinate): List<Coordinate>? {
        val key = start to goal
        return cache.getOrPut(key) {
            pathfinder.findPath(layout, start, goal)
        }
    }
    
    fun invalidate() {
        cache.clear()  // Call when layout changes
    }
}
```
**Effect**: Paths computed once, reused for all customers. O(1) lookup instead of O(n² log n) pathfinding.

**2. Flow Field Pathfinding** (Advanced)
```kotlin
// Instead of per-customer A*, compute a "flow field" once per layout change
class FlowFieldPathfinder {
    fun computeFlowField(layout: StoreLayout, goal: Coordinate): FlowField {
        // Dijkstra from goal to all tiles (run ONCE)
        // Store "next step" for each tile
        // Customers just follow arrows (O(1) per customer)
    }
}
```
**Effect**: 1 expensive computation per layout change → O(1) per customer.

**3. Async Background Pathfinding**
```kotlin
viewModelScope.launch(Dispatchers.Default) {
    val paths = customers.map { customer ->
        async { pathfinder.findPath(layout, customer.pos, checkout) }
    }.awaitAll()
    
    // Apply paths on main thread
}
```
**Effect**: Uses all CPU cores, doesn't block UI thread.

**4. Simplify Grid to Zones (Fallback)**
```kotlin
// Instead of 20×30 tiles, divide into 4×4 zones
// Customers "teleport" between zones (no pathfinding)
// Efficiency score based on zone adjacency
```
**Effect**: Removes pathfinding entirely, sacrifices spatial realism.

**Recommendation**: Use **Cached Flow Field** + **Async Computation**. Recompute field when layout changes (rare), customers follow precomputed arrows (O(1) per tick).

### Implementation Complexity: 📊📊📊📊📊 (5/5 - HIGHEST)

**Estimated Effort**: 80-100 hours
- Grid state + placement logic: 20 hours
- A*/Flow Field pathfinding: 25 hours
- Layout analysis system: 15 hours
- Drag-and-drop UI: 25 hours
- Performance optimization: 15 hours

### Breaking Changes
- ❌ Massive GameState refactoring (add `StoreLayout`)
- ❌ All inventory operations must reference shelf locations
- ❌ Cashier assignments tied to physical checkout objects
- ❌ Save file format changes (grid serialization)

---

## Extreme Feature #7: Item Expiration & Waste Management 🗑️

### Concept
Perishable items spoil after X days. Players must rotate stock (FIFO), manage waste disposal, and balance ordering vs. spoilage.

### Feature Depth: 🎯🎯🎯🎯 (4/5)

### Gameplay Additions
- **Expiration System**:
  - DAIRY: 7 days
  - PRODUCE: 5 days
  - BAKERY: 3 days
  - MEAT: 5 days
  - FROZEN: 30 days (if power maintained)
  - Others: No expiration
- **Batch Tracking**: Each delivery has a "received date" (day number)
- **FIFO Stocking**: Oldest batches sold first (automatic)
- **Spoilage**: Items past expiration → removed from inventory, loss recorded
- **Discount System**: Items within 1 day of expiration → 50% off (player can toggle)
- **Waste Disposal**: 
  - Free disposal: -5% customer satisfaction (smells bad)
  - Paid disposal: $50/day, no satisfaction penalty
  - Composting (unlock): $5,000 one-time, converts waste to +$10/day "compost sales"
- **Daily Spoilage Report**: Shows which items spoiled, value lost

### Refactoring Required: 🔧🔧🔧🔧 (4/5)

**1. Inventory Batch Tracking**
```kotlin
// Current: Simple quantity
data class InventoryState(
    val shelfStock: Int = 0,
    val backroomStock: Int = 0,
)

// NEW: Batch-tracked quantities
data class InventoryState(
    val shelfBatches: List<ItemBatch> = emptyList(),
    val backroomBatches: List<ItemBatch> = emptyList(),
) {
    val shelfStock: Int get() = shelfBatches.sumOf { it.quantity }
    val backroomStock: Int get() = backroomBatches.sumOf { it.quantity }
    
    fun oldestShelfBatch(): ItemBatch? = shelfBatches.minByOrNull { it.receivedDay }
}

data class ItemBatch(
    val batchId: String,
    val quantity: Int,
    val receivedDay: Int,  // Day number when ordered
    val expirationDay: Int,  // receivedDay + item.shelfLifeDays
)
```

**2. ItemMetadata Extension**
```kotlin
data class ItemMetadata(
    // ... existing fields ...
    val shelfLifeDays: Int? = null,  // null = non-perishable
    val isPerishable: Boolean get() = shelfLifeDays != null,
)

// In ItemDataLoader, set shelf life per category:
val shelfLife = when (category) {
    ItemCategory.DAIRY -> 7
    ItemCategory.PRODUCE -> 5
    ItemCategory.BAKERY -> 3
    ItemCategory.MEAT -> 5
    ItemCategory.FROZEN -> 30
    else -> null  // Non-perishable
}
```

**3. Spoilage Manager**
```kotlin
class SpoilageManager {
    fun checkForSpoilage(state: GameState): GameState {
        val currentDay = state.currentTime.dayNumber
        var totalSpoiled = 0
        var valueLost = Money.ZERO
        val spoilageEvents = mutableListOf<SpoilageEvent>()
        
        val updatedInventory = state.inventory.mapValues { (itemId, inv) ->
            val metadata = itemMetadataCache.get(itemId) ?: return@mapValues inv
            if (!metadata.isPerishable) return@mapValues inv
            
            // Remove expired batches
            val validShelfBatches = inv.shelfBatches.filter { batch ->
                if (batch.expirationDay <= currentDay) {
                    // Expired
                    totalSpoiled += batch.quantity
                    valueLost += metadata.price * batch.quantity
                    spoilageEvents.add(
                        SpoilageEvent(
                            itemId = itemId,
                            itemName = metadata.name,
                            quantity = batch.quantity,
                            value = metadata.price * batch.quantity
                        )
                    )
                    false
                } else {
                    true
                }
            }
            
            val validBackroomBatches = inv.backroomBatches.filter { batch ->
                batch.expirationDay > currentDay
            }
            
            inv.copy(
                shelfBatches = validShelfBatches,
                backroomBatches = validBackroomBatches
            )
        }
        
        return state.copy(
            inventory = updatedInventory,
            currentDayMetrics = state.currentDayMetrics.copy(
                itemsSpoiled = totalSpoiled,
                spoilageLosses = valueLost
            ),
            spoilageHistory = state.spoilageHistory + spoilageEvents
        )
    }
    
    fun applyDiscountToExpiringSoon(state: GameState): GameState {
        if (!state.storeConfig.discountExpiringSoon) return state
        
        val currentDay = state.currentTime.dayNumber
        val discountedItems = mutableSetOf<Int>()
        
        state.inventory.forEach { (itemId, inv) ->
            val metadata = itemMetadataCache.get(itemId) ?: return@forEach
            if (!metadata.isPerishable) return@forEach
            
            // Check if oldest batch expires within 1 day
            val oldestBatch = inv.oldestShelfBatch() ?: return@forEach
            if (oldestBatch.expirationDay - currentDay <= 1) {
                discountedItems.add(itemId)
            }
        }
        
        return state.copy(discountedItems = discountedItems)
    }
}

data class SpoilageEvent(
    val itemId: Int,
    val itemName: String,
    val quantity: Int,
    val value: Money,
)
```

**4. FIFO Stock Consumption**
```kotlin
class InventoryManager {
    fun stockItemFromBackroom(state: GameState, itemId: Int): GameState {
        val inv = state.inventory[itemId] ?: return state
        
        // Take from OLDEST backroom batch
        val oldestBatch = inv.backroomBatches.minByOrNull { it.receivedDay } ?: return state
        if (oldestBatch.quantity == 0) return state
        
        // Move 1 item from backroom to shelf (same batch)
        val updatedBackroomBatches = inv.backroomBatches.map { batch ->
            if (batch.batchId == oldestBatch.batchId) {
                batch.copy(quantity = batch.quantity - 1)
            } else {
                batch
            }
        }.filter { it.quantity > 0 }
        
        val updatedShelfBatches = if (inv.shelfBatches.any { it.batchId == oldestBatch.batchId }) {
            inv.shelfBatches.map { batch ->
                if (batch.batchId == oldestBatch.batchId) {
                    batch.copy(quantity = batch.quantity + 1)
                } else {
                    batch
                }
            }
        } else {
            inv.shelfBatches + oldestBatch.copy(quantity = 1)
        }
        
        val updated = inv.copy(
            shelfBatches = updatedShelfBatches,
            backroomBatches = updatedBackroomBatches
        )
        
        return state.copy(inventory = state.inventory + (itemId to updated))
    }
    
    fun buyItemToBackroom(state: GameState, itemId: Int): GameState {
        // ... existing affordability checks ...
        
        val metadata = itemMetadataCache.get(itemId) ?: return state
        val currentDay = state.currentTime.dayNumber
        
        // Create new batch
        val expirationDay = if (metadata.isPerishable) {
            currentDay + (metadata.shelfLifeDays ?: 0)
        } else {
            Int.MAX_VALUE  // Never expires
        }
        
        val newBatch = ItemBatch(
            batchId = UUID.randomUUID().toString(),
            quantity = metadata.casePack,
            receivedDay = currentDay,
            expirationDay = expirationDay
        )
        
        val inv = state.inventory[itemId] ?: InventoryState()
        val updated = inv.copy(
            backroomBatches = inv.backroomBatches + newBatch
        )
        
        return state.copy(
            inventory = state.inventory + (itemId to updated),
            money = state.money - metadata.unitCost * metadata.casePack
        )
    }
}
```

### Performance Impact: ⚠️⚠️⚠️ (3/5 - MODERATE)

| Metric | Current | With Batch Tracking | Degradation |
|--------|---------|-------------------|-------------|
| **Tick Time** | 0.0095ms | **0.018ms** (1.9×) | Still 0.11% of frame budget ✅ |
| **Spoilage Check** | 0ms | **+2ms per midnight** | Once per day |
| **Memory** | ~50 MB | **~120 MB** | ⚠️ 500 items × 10 batches each |

**Why Moderate**: Batch tracking adds ~10× inventory state complexity (500 items × 10 batches = 5000 objects vs. 500 simple structs).

**Mitigation**: Limit batches to 20 per item (merge older batches with same expiration date).

### Implementation Complexity: 📊📊📊📊 (4/5)

**Estimated Effort**: 50-60 hours
- Batch-tracked inventory refactoring: 20 hours
- FIFO stocking logic: 15 hours
- Spoilage manager: 10 hours
- UI (batch display, spoilage report): 10 hours
- Testing: 10 hours

---

## Extreme Feature #8: Online Ordering & Delivery 📦

### Concept
Customers can order online for pickup/delivery. Players must fulfill orders within time limits or lose reputation.

### Feature Depth: 🎯🎯🎯🎯🎯 (5/5)

### Gameplay Additions
- **Online Orders**: 5-20% of revenue shifts from in-store to online
- **Order Types**:
  - **Pickup**: Customer arrives, collect order (5-min window)
  - **Delivery**: Hire delivery driver, 30-min time limit
- **Order Fulfillment**:
  - Player or stocker must "pick" items from shelves
  - Picking time: 10 seconds per item (player) or 20 seconds (AI stocker)
  - If out of stock, refund item (but keep order processing fee)
- **Reputation System**:
  - On-time fulfillment: +1 reputation
  - Late fulfillment: -5 reputation
  - Unfulfilled (timeout): -20 reputation
  - Reputation affects online order volume (0-100% modifier)
- **Delivery Drivers**: New entity type
  - Cost: $50 per delivery (gig economy)
  - Speed: 30 min per delivery (fixed)
  - Need 1 driver per 10 concurrent orders
- **Delivery Fee**: Player sets fee ($2-$10), affects order volume

### Refactoring Required: 🔧🔧🔧🔧 (4/5)

**1. Online Order State**
```kotlin
data class GameState(
    // ... existing fields ...
    val onlineOrders: List<OnlineOrder> = emptyList(),
    val reputation: Int = 100,  // 0-100
    val deliveryFee: Money = Money(500),  // $5.00
)

data class OnlineOrder(
    val orderId: String,
    val orderType: OrderType,
    val items: List<OrderLine>,
    val placedAt: GameTime,
    val dueAt: GameTime,  // placedAt + 30 min (pickup) or 60 min (delivery)
    val status: OrderStatus,
    val fulfillmentProgress: Int = 0,  // Items picked so far
    val assignedPicker: String? = null,  // Player or stocker ID
)

enum class OrderType {
    PICKUP, DELIVERY
}

enum class OrderStatus {
    PENDING,      // Not started
    IN_PROGRESS,  // Being picked
    READY,        // Picked, awaiting pickup/delivery
    COMPLETED,    // Successfully delivered
    LATE,         // Past due, reputation penalty
    CANCELLED,    // Timeout or player cancellation
}

data class OrderLine(
    val itemId: Int,
    val quantity: Int,
    val pricePerUnit: Money,
)
```

**2. Online Order Generator**
```kotlin
class OnlineOrderManager {
    fun generateOrders(state: GameState, deltaSeconds: Double): GameState {
        // Order arrival rate based on store reputation
        val reputationMultiplier = state.reputation / 100f
        val baseRate = 0.05  // 5% of in-store traffic
        val effectiveRate = baseRate * reputationMultiplier
        
        val customerCount = calculateInStoreCustomers(state, deltaSeconds)
        val onlineOrderCount = (customerCount * effectiveRate).toInt()
        
        val newOrders = List(onlineOrderCount) {
            generateOnlineOrder(state)
        }
        
        return state.copy(onlineOrders = state.onlineOrders + newOrders)
    }
    
    private fun generateOnlineOrder(state: GameState): OnlineOrder {
        val orderType = if (Random.nextFloat() < 0.6f) OrderType.PICKUP else OrderType.DELIVERY
        val basketSize = Random.nextInt(3, 15)
        
        val items = List(basketSize) {
            val itemId = selectRandomItem(state)
            val metadata = itemMetadataCache.get(itemId)!!
            OrderLine(
                itemId = itemId,
                quantity = 1,
                pricePerUnit = metadata.price
            )
        }
        
        val dueTime = state.currentTime.addMinutes(
            if (orderType == OrderType.PICKUP) 30 else 60
        )
        
        return OnlineOrder(
            orderId = UUID.randomUUID().toString(),
            orderType = orderType,
            items = items,
            placedAt = state.currentTime,
            dueAt = dueTime,
            status = OrderStatus.PENDING,
        )
    }
}
```

**3. Order Fulfillment Logic**
```kotlin
class OrderFulfillmentManager {
    fun assignPickerToOrder(state: GameState, orderId: String, pickerId: String): GameState {
        val order = state.onlineOrders.find { it.orderId == orderId } ?: return state
        
        val updated = order.copy(
            status = OrderStatus.IN_PROGRESS,
            assignedPicker = pickerId
        )
        
        return state.copy(
            onlineOrders = state.onlineOrders.map { if (it.orderId == orderId) updated else it }
        )
    }
    
    fun progressOrderFulfillment(state: GameState, deltaSeconds: Double): GameState {
        val pickingSpeed = 1.0 / 10.0  // 10 seconds per item (player)
        
        val updatedOrders = state.onlineOrders.map { order ->
            if (order.status != OrderStatus.IN_PROGRESS) return@map order
            
            val itemsRemaining = order.items.size - order.fulfillmentProgress
            if (itemsRemaining == 0) {
                return@map order.copy(status = OrderStatus.READY)
            }
            
            // Progress picking
            val itemsPicked = (deltaSeconds * pickingSpeed).toInt().coerceAtMost(itemsRemaining)
            
            // Deduct from inventory
            val newInventory = deductItemsFromInventory(state.inventory, order.items, itemsPicked)
            
            order.copy(
                fulfillmentProgress = order.fulfillmentProgress + itemsPicked
            )
        }
        
        return state.copy(onlineOrders = updatedOrders)
    }
    
    fun checkForTimeouts(state: GameState): GameState {
        val currentTime = state.currentTime
        var reputationDelta = 0
        
        val updatedOrders = state.onlineOrders.map { order ->
            if (order.status == OrderStatus.COMPLETED || order.status == OrderStatus.CANCELLED) {
                return@map order
            }
            
            val isOverdue = currentTime.totalMinutesElapsed > order.dueAt.totalMinutesElapsed
            
            if (isOverdue && order.status == OrderStatus.READY) {
                // Late delivery
                reputationDelta -= 5
                order.copy(status = OrderStatus.LATE)
            } else if (isOverdue && order.status != OrderStatus.READY) {
                // Completely missed
                reputationDelta -= 20
                order.copy(status = OrderStatus.CANCELLED)
            } else {
                order
            }
        }
        
        return state.copy(
            onlineOrders = updatedOrders,
            reputation = (state.reputation + reputationDelta).coerceIn(0, 100)
        )
    }
}
```

**4. Delivery Driver System**
```kotlin
// Add new EntityType
object DELIVERY_DRIVERS : EntityType(
    displayName = "Delivery Drivers",
    description = "Deliver online orders to customers",
    icon = Icons.Default.DirectionsCar,
    entities = listOf(
        EntityDef(
            key = "delivery_driver",
            displayName = "Delivery Driver",
            cost = Money(0),  // Gig economy, pay per delivery
            description = "Delivers online orders within 30 minutes",
            icon = Icons.Default.DirectionsCar,
        )
    )
)

// Delivery cost deducted per order
fun completeDelivery(state: GameState, orderId: String): GameState {
    val order = state.onlineOrders.find { it.orderId == orderId } ?: return state
    
    val deliveryCost = Money(5000)  // $50 per delivery
    val orderTotal = order.items.sumOf { it.pricePerUnit.cents * it.quantity }
    val netRevenue = Money(orderTotal) - deliveryCost + state.deliveryFee
    
    return state.copy(
        money = state.money + netRevenue,
        reputation = (state.reputation + 1).coerceAtMost(100),
        onlineOrders = state.onlineOrders.filter { it.orderId != orderId }
    )
}
```

### Performance Impact: ⚠️⚠️ (2/5 - MILD)

| Metric | Current | With Online Orders | Degradation |
|--------|---------|-------------------|-------------|
| **Tick Time** | 0.0095ms | **0.013ms** (1.4×) | Still 0.08% of frame budget ✅ |
| **Order Generation** | 0ms | **+0.01ms per minute** | Minimal |
| **Fulfillment Logic** | 0ms | **+0.005ms per active order** | 10 orders = +0.05ms |

**Why Low Impact**: Orders are sparse (5-20% of revenue), fulfillment logic is simple (just increment counters).

### Implementation Complexity: 📊📊📊📊 (4/5)

**Estimated Effort**: 50-60 hours
- Order state + generation: 15 hours
- Fulfillment logic: 15 hours
- Reputation system: 10 hours
- UI (order queue, picker assignment): 15 hours
- Testing: 10 hours

---

## Extreme Feature #9: Multiplayer Co-op Mode 👥

### Concept
2-4 players manage the same store simultaneously in real-time. Each player can take different roles (cashier, stocker, manager).

### Feature Depth: 🎯🎯🎯🎯🎯 (5/5)
**Why**: Transforms single-player sim into a cooperative strategy game. Coordination challenges and social gameplay.

### Gameplay Additions
- **Networked Multiplayer**: Firebase Realtime Database or WebSockets
- **Role Assignment**: Each player picks a role
  - **Manager**: Sets prices, hires staff, controls store hours
  - **Cashier**: Ring up transactions (faster than AI)
  - **Stocker**: Stock shelves (faster than AI)
  - **Buyer**: Orders inventory, manages backroom
- **Shared State**: All players see same GameState (synced)
- **Conflict Resolution**: 
  - If 2 players stock same item → both succeed (optimistic concurrency)
  - If 2 players try to spend last $100 → first commit wins, second fails
- **Chat System**: In-game text chat
- **Session Hosting**: One player creates room, others join via code

### Refactoring Required: 🔧🔧🔧🔧🔧 (5/5 - MASSIVE)

**1. Networked State Synchronization**
```kotlin
// Current: Local state
class GameViewModel {
    private val gameEngine: GameEngine
    private val _uiState = MutableStateFlow<GameUiState>(...)
}

// NEW: Networked state
class MultiplayerGameViewModel(
    private val sessionId: String,
    private val playerId: String,
    private val firebaseDb: FirebaseDatabase
) {
    private val sessionRef = firebaseDb.getReference("sessions/$sessionId")
    
    init {
        // Listen for remote state changes
        sessionRef.child("gameState").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteState = snapshot.getValue(GameState::class.java) ?: return
                
                // Merge remote state with local optimistic updates
                gameEngine.state = mergeStates(gameEngine.state, remoteState)
                rebuildUiState()
            }
        })
    }
    
    fun onEvent(event: GameEvent) {
        // Apply optimistically (no network delay)
        gameEngine.onEvent(event)
        rebuildUiState()
        
        // Send to server
        val eventData = serializeEvent(event)
        sessionRef.child("events").push().setValue(eventData)
    }
    
    private fun mergeStates(local: GameState, remote: GameState): GameState {
        // Conflict resolution:
        // - Use remote money (authoritative)
        // - Merge inventory (both players' actions count)
        // - Use latest time
        return remote.copy(
            inventory = mergeInventories(local.inventory, remote.inventory),
            hiredStaff = remote.hiredStaff,  // Manager controls
        )
    }
}
```

**2. Firebase Schema**
```
sessions/
  {sessionId}/
    gameState: { ... serialized GameState ... }
    events/
      {eventId}: { playerId, eventType, payload, timestamp }
    players/
      {playerId}: { name, role, lastSeen }
    chat/
      {messageId}: { playerId, message, timestamp }
```

**3. Optimistic Concurrency Control**
```kotlin
class OptimisticConcurrencyManager {
    fun applyEventWithConflictCheck(
        localState: GameState,
        remoteState: GameState,
        event: GameEvent
    ): GameState {
        return when (event) {
            is GameEvent.BuyItem -> {
                val cost = calculateCost(event.itemId)
                if (remoteState.money < cost) {
                    // Conflict: Another player spent the money first
                    emit(GameStateChange.Error("Insufficient funds"))
                    return remoteState  // Rollback local change
                } else {
                    // Success
                    inventoryManager.buyItemToBackroom(remoteState, event.itemId)
                }
            }
            is GameEvent.StockItem -> {
                // No conflict: Multiple players can stock simultaneously
                inventoryManager.stockItemFromBackroom(remoteState, event.itemId)
            }
            // ... handle all events
        }
    }
}
```

**4. Role-Based Permissions**
```kotlin
enum class PlayerRole {
    MANAGER,   // Full control
    CASHIER,   // Ring-up only
    STOCKER,   // Stock/order only
    BUYER,     // Inventory orders only
}

fun canPlayerExecuteEvent(role: PlayerRole, event: GameEvent): Boolean {
    return when (event) {
        is GameEvent.RingUpItem -> role in setOf(PlayerRole.MANAGER, PlayerRole.CASHIER)
        is GameEvent.StockItem -> role in setOf(PlayerRole.MANAGER, PlayerRole.STOCKER)
        is GameEvent.BuyItem -> role in setOf(PlayerRole.MANAGER, PlayerRole.BUYER)
        is GameEvent.HireStaff -> role == PlayerRole.MANAGER
        else -> false
    }
}
```

### Performance Impact: ⚠️⚠️ (2/5 - MILD)

| Metric | Current | With Multiplayer | Degradation |
|--------|---------|-----------------|-------------|
| **Tick Time** | 0.0095ms | **0.0095ms** (no change) | ✅ Logic unchanged |
| **Network Latency** | 0ms | **50-200ms** | User-perceived delay |
| **State Sync** | N/A | **10-50ms per event** | Background async |

**Why Low Impact**: Game logic stays local (no change to tick performance). Network is async background process.

### Implementation Complexity: 📊📊📊📊📊 (5/5 - HIGHEST)

**Estimated Effort**: 100-120 hours
- Firebase integration: 30 hours
- State synchronization: 25 hours
- Conflict resolution: 20 hours
- Role-based permissions: 10 hours
- UI (room creation, chat): 20 hours
- Testing (4-player scenarios): 20 hours

### Breaking Changes
- ❌ Requires Firebase account + dependency
- ❌ ViewModel completely refactored
- ❌ Save system incompatible (local → cloud)
- ❌ All events must be serializable

---

## Extreme Feature #10: Franchise Management & Automation 🏢

### Concept
Once the player builds a successful single store, unlock the ability to franchise the brand. AI-controlled franchisees pay royalties, and the player becomes a **passive income tycoon**.

### Feature Depth: 🎯🎯🎯🎯🎯 (5/5)

### Gameplay Additions
- **Franchise Unlock**: At TIER_GM + $1,000,000 revenue
- **Franchise Mechanics**:
  - **Sell Franchise License**: $500,000 per location
  - **Royalty System**: Franchisee pays 5% of revenue per day
  - **AI Franchisees**: Fully autonomous (no player management)
  - **Performance Tiers**: 
    - Poor franchisee: $500-$1,000/day revenue → $25-$50 royalty
    - Average franchisee: $2,000-$5,000/day → $100-$250 royalty
    - Excellent franchisee: $10,000+/day → $500+ royalty
  - **Failure Risk**: 10% of franchisees close per year (lose royalty stream)
- **Franchise Control Panel**:
  - View all franchisees (name, location, daily revenue, days open)
  - Revoke license (if franchisee underperforms)
  - Mandatory policies (pricing rules, item selection)
- **Branding System**:
  - **Brand Reputation** (0-100): Affects franchise sale price
  - Reputation factors: Player store performance, franchisee scandals, marketing spend
- **Corporate Upgrades**:
  - **Franchise Training Program**: $100,000 → +10% franchisee revenue
  - **National Advertising**: $50,000/day → +20% all stores' traffic
  - **Supply Chain Optimization**: $250,000 → -10% item costs for all stores

### Refactoring Required: 🔧🔧🔧🔧 (4/5)

**1. Corporate State**
```kotlin
data class GameWorld(
    // ... existing fields ...
    val corporate: CorporateState,
)

data class CorporateState(
    val brandReputation: Int = 100,  // 0-100
    val franchises: List<Franchise> = emptyList(),
    val corporateUpgrades: Set<CorporateUpgrade> = emptySet(),
    val nationalAdSpend: Money = Money.ZERO,
)

data class Franchise(
    val franchiseId: String,
    val franchiseeName: String,
    val locationName: String,
    val openedOnDay: Int,
    val performanceTier: PerformanceTier,
    val dailyRevenue: Money,  // Simulated
    val daysOpen: Int,
)

enum class PerformanceTier {
    POOR,    // $500-$1,000/day
    AVERAGE, // $2,000-$5,000/day
    EXCELLENT  // $10,000+/day
}

enum class CorporateUpgrade {
    FRANCHISE_TRAINING,
    NATIONAL_ADVERTISING,
    SUPPLY_CHAIN_OPTIMIZATION,
}
```

**2. Franchise Simulation**
```kotlin
class FranchiseSimulator {
    fun simulateFranchiseDay(franchise: Franchise, world: GameWorld): Franchise {
        // Simulate revenue based on performance tier + brand reputation
        val baseRevenue = when (franchise.performanceTier) {
            PerformanceTier.POOR -> Money.fromDollars(Random.nextDouble(500.0, 1000.0))
            PerformanceTier.AVERAGE -> Money.fromDollars(Random.nextDouble(2000.0, 5000.0))
            PerformanceTier.EXCELLENT -> Money.fromDollars(Random.nextDouble(10000.0, 20000.0))
        }
        
        // Brand reputation multiplier
        val reputationMultiplier = world.corporate.brandReputation / 100f
        
        // Corporate upgrade bonuses
        val trainingBonus = if (CorporateUpgrade.FRANCHISE_TRAINING in world.corporate.corporateUpgrades) {
            1.1f
        } else {
            1.0f
        }
        
        val finalRevenue = baseRevenue * reputationMultiplier * trainingBonus
        
        // 10% annual closure rate = 0.027% per day
        val willClose = Random.nextFloat() < 0.00027f
        
        return franchise.copy(
            dailyRevenue = finalRevenue,
            daysOpen = if (willClose) -1 else franchise.daysOpen + 1  // -1 = closed marker
        )
    }
    
    fun collectRoyalties(world: GameWorld): Money {
        return world.corporate.franchises
            .filter { it.daysOpen >= 0 }  // Active franchises only
            .sumOf { it.dailyRevenue.cents * 0.05 }  // 5% royalty
            .let { Money(it.toLong()) }
    }
}
```

**3. Franchise UI**
```kotlin
@Composable
fun FranchiseManagementScreen(
    franchises: List<Franchise>,
    brandReputation: Int,
    corporateCash: Money,
    onSellFranchise: (locationName: String) -> Unit,
    onRevokeFranchise: (franchiseId: String) -> Unit,
    onBuyUpgrade: (CorporateUpgrade) -> Unit,
) {
    Column {
        // Brand stats
        BrandReputationCard(reputation = brandReputation)
        
        // Franchise list
        LazyColumn {
            items(franchises) { franchise ->
                FranchiseCard(
                    franchise = franchise,
                    onRevoke = { onRevokeFranchise(franchise.franchiseId) }
                )
            }
        }
        
        // Corporate upgrades
        CorporateUpgradesPanel(
            upgrades = CorporateUpgrade.values().toList(),
            onBuy = onBuyUpgrade
        )
    }
}
```

### Performance Impact: ⚠️ (1/5 - MINIMAL)

| Metric | Current | With Franchises | Degradation |
|--------|---------|----------------|-------------|
| **Tick Time** | 0.0095ms | **0.0095ms** (no change) | ✅ Franchises simulated once per day |
| **Daily Simulation** | 0ms | **+5ms per midnight** | 100 franchises × 0.05ms |

**Why Minimal**: Franchise simulation runs once per day (midnight), not per tick.

### Implementation Complexity: 📊📊📊 (3/5)

**Estimated Effort**: 35-45 hours
- Franchise state + simulation: 15 hours
- Royalty collection logic: 5 hours
- Corporate upgrades: 10 hours
- UI (franchise panel): 10 hours
- Testing: 5 hours

---

## Summary Table: All 10 Extreme Features

| # | Feature | Depth | Refactoring | Performance | Complexity | Estimated Effort |
|---|---------|-------|-------------|-------------|------------|------------------|
| 1 | **Multi-Store Chain** | 🎯🎯🎯🎯🎯 | 🔧🔧🔧🔧🔧 | ⚠️⚠️⚠️⚠️ | 📊📊📊📊📊 | **60-80 hrs** |
| 2 | **Competitor AI** | 🎯🎯🎯🎯 | 🔧🔧🔧 | ⚠️⚠️⚠️ | 📊📊📊📊 | **40-50 hrs** |
| 3 | **Employee Morale** | 🎯🎯🎯🎯 | 🔧🔧🔧🔧 | ⚠️⚠️ | 📊📊📊 | **30-40 hrs** |
| 4 | **Crime & Security** | 🎯🎯🎯🎯 | 🔧🔧🔧 | ⚠️ | 📊📊📊 | **25-35 hrs** |
| 5 | **Weather & Seasons** | 🎯🎯🎯🎯🎯 | 🔧🔧🔧🔧 | ⚠️⚠️ | 📊📊📊📊 | **45-55 hrs** |
| 6 | **Store Layout** | 🎯🎯🎯🎯🎯 | 🔧🔧🔧🔧🔧 | ⚠️⚠️⚠️⚠️⚠️ | 📊📊📊📊📊 | **80-100 hrs** |
| 7 | **Item Expiration** | 🎯🎯🎯🎯 | 🔧🔧🔧🔧 | ⚠️⚠️⚠️ | 📊📊📊📊 | **50-60 hrs** |
| 8 | **Online Ordering** | 🎯🎯🎯🎯🎯 | 🔧🔧🔧🔧 | ⚠️⚠️ | 📊📊📊📊 | **50-60 hrs** |
| 9 | **Multiplayer Co-op** | 🎯🎯🎯🎯🎯 | 🔧🔧🔧🔧🔧 | ⚠️⚠️ | 📊📊📊📊📊 | **100-120 hrs** |
| 10 | **Franchise System** | 🎯🎯🎯🎯🎯 | 🔧🔧🔧🔧 | ⚠️ | 📊📊📊 | **35-45 hrs** |

**Total Development Time (all features)**: **515-645 hours** (12-16 weeks full-time)

---

## Implementation Roadmap

### Phase 1: Foundation (Pick 2-3)
**Recommended**: Crime & Security (#4), Employee Morale (#3), Weather & Seasons (#5)
- **Rationale**: Low-to-moderate performance impact, add depth without massive refactoring
- **Total**: ~100-130 hours

### Phase 2: Expansion (Pick 2)
**Recommended**: Item Expiration (#7), Online Ordering (#8)
- **Rationale**: Both add new gameplay loops without breaking existing architecture
- **Total**: ~100-120 hours

### Phase 3: Advanced (Pick 1)
**Recommended**: Multi-Store Chain (#1) OR Store Layout (#6)
- **Rationale**: These require major refactoring but unlock entirely new game modes
- **Multi-Store**: Better for empire-building players
- **Store Layout**: Better for puzzle-loving players
- **Total**: ~60-100 hours

### Phase 4: End-Game (Pick 1)
**Recommended**: Franchise System (#10) OR Multiplayer (#9)
- **Rationale**: Late-game content for players who've mastered the core loop
- **Franchise**: Passive income / tycoon mode
- **Multiplayer**: Social / competitive mode
- **Total**: ~35-120 hours

**Total for Full Roadmap**: 295-470 hours (7-12 weeks)

---

## Performance Budget After All Features

| Scenario | Current | After All Features | Frame Budget Used |
|----------|---------|-------------------|-------------------|
| **Realistic Load** | 0.0095ms | **~0.25ms** | **1.5%** ✅ |
| **Maximum Load** | 0.039ms | **~1.2ms** | **7.2%** ✅ |
| **Extreme Load** (multi-store + layout) | 0.039ms | **~5ms** | **30%** ⚠️ |

**Conclusion**: Even with all 10 features, the game can maintain 60 FPS on Pixel 8a, though extreme scenarios (20 stores + complex layouts) may require optimization (lazy activation, async processing).

---

## Breaking Changes Summary

| Feature | Save File | GameState | ViewModel | UI | Tests |
|---------|-----------|-----------|-----------|----|----|
| **Multi-Store Chain** | ❌ | ❌❌❌ | ❌❌ | ❌❌ | ❌❌❌ |
| **Competitor AI** | ⚠️ | ❌ | ✅ | ⚠️ | ❌ |
| **Employee Morale** | ⚠️ | ❌ | ✅ | ⚠️ | ❌ |
| **Crime & Security** | ⚠️ | ❌ | ✅ | ⚠️ | ❌ |
| **Weather & Seasons** | ⚠️ | ❌ | ✅ | ⚠️ | ❌ |
| **Store Layout** | ❌ | ❌❌❌ | ❌ | ❌❌❌ | ❌❌ |
| **Item Expiration** | ❌ | ❌❌ | ✅ | ⚠️ | ❌❌ |
| **Online Ordering** | ⚠️ | ❌ | ✅ | ⚠️ | ❌ |
| **Multiplayer** | ❌ | ❌ | ❌❌❌ | ❌❌ | ❌❌ |
| **Franchise System** | ⚠️ | ❌ | ✅ | ⚠️ | ❌ |

**Legend**:
- ✅ No breaking changes
- ⚠️ Minor changes (migration possible)
- ❌ Breaking changes (requires rewrite)
- ❌❌ Major breaking changes
- ❌❌❌ Complete rewrite required

---

## Final Recommendation

Based on the analysis, here's the **optimal implementation order** that balances feature depth, performance impact, and development effort:

### Immediate (Next Sprint)
1. **Crime & Security** (#4) - Low performance impact, adds tension
2. **Employee Morale** (#3) - Depth without complexity

### Short-Term (1-2 months)
3. **Weather & Seasons** (#5) - High depth, moderate effort
4. **Item Expiration** (#7) - Adds realism, manageable refactoring

### Mid-Term (3-6 months)
5. **Online Ordering** (#8) - Modern feature, high player demand
6. **Multi-Store Chain** (#1) OR **Store Layout** (#6) - Pick based on player feedback

### Long-Term (6-12 months)
7. **Franchise System** (#10) - End-game content
8. **Multiplayer** (#9) - Only if community requests it

**Avoid**: Competitor AI (#2) unless you implement Multi-Store Chain first (they synergize well).

---

**Total Word Count**: ~11,500 words  
**Total Code Examples**: 40+  
**Total Estimated Implementation Time**: 515-645 hours

This proposal represents a **complete evolution** of Superstore Simulator from a simple tick-based management sim to a **AAA-tier tycoon empire game** rivaling titles like Game Dev Tycoon, Factorio, and Stardew Valley in depth and complexity.

