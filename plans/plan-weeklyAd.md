# Plan: Weekly Ad / Promotions System

## Context

The game has rich pricing (markups/markdowns/elasticity), traffic simulation, truck pre-stocking, and metrics — but no proactive demand lever. The player can only react to expiry via markdowns. This feature adds a **Weekly Ad**: the player builds a sale flyer (featured items + discounts), pays to publish it, and it runs for 7 game days. It boosts store traffic and featured-item demand, trading margin for volume — and rewards pre-stocking via trucks because featured items sell ~4–6× faster and hit the existing out-of-stock lost-revenue path if understocked.

Avoids overlap with existing plans (reputation, research, EXTREME_FEATURES list).

## Design

### Data model — new `domain/promotions/PromotionState.kt`

```kotlin
@Serializable
data class AdLineItem(val itemId: Int, val percentOff: Int)  // clamped 10..50 at publish

@Serializable
data class WeeklyAd(
    val adId: Int,
    val items: List<AdLineItem>,
    val startDay: Int,            // publish day + 1 (one-day pre-stock window)
    val durationDays: Int = 7,
    val publishCost: Money,
) {
    fun isLiveOn(day: Int): Boolean
    fun isScheduledOn(day: Int): Boolean
    fun discountFor(itemId: Int, day: Int): Int?
}

@Serializable
data class PromotionSystemState(
    val currentAd: WeeklyAd? = null,
    val nextAdId: Int = 1,
) {
    fun trafficMultiplier(day: Int): Float     // 1.25 while live, else 1.0
    fun demandMultiplier(itemId: Int, day: Int): Float  // 3.0 for featured while live
    fun backroomCapMultiplier(itemId: Int, day: Int): Int  // 3 for featured while scheduled OR live, else 1
    companion object {
        const val AD_TRAFFIC_BOOST = 1.25f
        const val AD_FEATURED_DEMAND_MULT = 3.0f
        const val AD_BACKROOM_CAP_MULT = 3      // "promo pallets" — pre-stock headroom
    }
}
```

GameState (`domain/GameStateData.kt`): add `val promotionState: PromotionSystemState = PromotionSystemState()` — defaulted per serialization checklist; legacy saves load clean. Ad-builder draft state is UI-only (`remember{}`), never persisted. No `restoreManagersFromState` change (no manager accumulators).

Balance note: ad discount already feeds `smoothedPriceIndex` elasticity (organic traffic + demand lift), so explicit constants stay moderate (1.25× / 3×) to avoid double-counting. Net effect ≈ 4–6× featured unit velocity.

### Manager — new `domain/promotions/PromotionManager.kt`

`@Singleton class PromotionManager @Inject constructor(private val cache: ItemMetadataCache)` — constructor injection like PricingManager, **no GameModule entry needed**.

- `isUnlocked(state)`: store size ≥ SMALL_GROCERY
- `maxFeaturedItems(size)`: SMALL_GROCERY 3, GROCERY_STORE 4, SUPERSTORE 6, SUPERCENTER 8
- `adCost(state, itemCount)`: `currentStoreSize.dailyRent + Money(2_500) × itemCount` (~$300 small ad → ~$2,700 supercenter ad)
- `publishAd(state, items)`: validate (unlocked, no existing ad, ≤ max items, items in inventory + current tier, funds), deduct cost, set `currentAd` with `startDay = today + 1`, record `adPublishCost` in currentDayMetrics
- `cancelScheduledAd(state)`: full refund only while scheduled (not live)
- `recordAdDayMetrics(state)`: pre-rollover — aggregate featured-item sold events into ad metric fields
- `expireAdIfNeeded(state, newDay)`: clear ad when `newDay >= startDay + durationDays`

One active ad at a time. No cancelling live ads (MVP).

### Integration points (verified against code)

1. **`PricingManager.resolvePrice` (lines 29–31)**: replace markdown term with
   ```kotlin
   val effectiveDiscount = maxOf(markdown, adDiscount)  // deeper cut wins, NO stacking
   ```
   where `adDiscount = state.promotionState.currentAd?.discountFor(itemId, state.currentTime.dayNumber) ?: 0`. Existing `-90` clamp and `unitCost` floor (line 35) guarantee never selling below cost. `computePriceIndex` + `markdownsSaved` metric pick the discount up automatically — zero extra pricing changes.

2. **`TrafficManager.update`**: multiply rate product by `state.promotionState.trafficMultiplier(state.currentTime.dayNumber)`.

3. **`TransactionEngine.weightedSample` (lines 355–367)**: add `promoMultipliers: Map<Int, Float>? = null` param; multiply into weight product alongside `zoneMultiplier × priceMultiplier`. Build map at both call sites (`startNewTransaction`, `generateRandomTransaction`) from live ad's featured items → `AD_FEATURED_DEMAND_MULT`. FIFO batches, OOS, refunds untouched — understocking punished via existing `lostToOutOfStock`/`lostRevenue` path.

4. **Backroom cap ("promo pallets")** — base cap (5 packs at SMALL_GROCERY) would block pre-stocking against 4–6× sale velocity. Featured items get cap × `AD_BACKROOM_CAP_MULT` (3) from publish moment (scheduled) through ad end. Add helper `effectiveBackroomCap(itemId, state) = storeConfig.backroomCapPerItem × promotionState.backroomCapMultiplier(itemId, day)` and use it at the three cap-read sites: `TruckManager.kt:173`, `InventoryManager.kt:258`, `InventoryManager.kt:320`. Cap raised immediately on publish → player can bulk-order same day, truck lands during 1-day lead; mid-week trucks keep refilling while live. On expiry, overflow stock remains (no disposal) — new orders just revert to normal cap and excess drains naturally. Helper lives on PromotionSystemState (no DI into InventoryManager/TruckManager).

5. **`DayRolloverProcessor.process` (lines 34–53)**: inject PromotionManager. `recordAdDayMetrics` **before** `dayManager.rollOverDay` (line 52, so stats land in finished day's snapshot); `expireAdIfNeeded` **after** `advanceDay` (line 53).

### Metrics — `domain/metrics/DailyMetrics.kt`

New defaulted fields: `adWasActive: Boolean`, `adPublishCost: Money`, `adFeaturedUnitsSold: Int`, `adFeaturedRevenue: Money`, `adDiscountGiven: Money`. Subtract `adPublishCost` in `netRevenue`. End-of-day report dialog gains "Ad Performance" section when active or cost > 0.

### GameEngine + UI

- **`domain/GameEngine.kt`**: inject PromotionManager; `publishWeeklyAd(items)`, `cancelScheduledAd()`, `weeklyAdCost(itemCount)`.
- **New `ui/components/cards/WeeklyAdCard.kt`** on StoreHomeScreen below StorePricingCard. States: locked ("Unlocks at Small Grocery"), empty ("Create Weekly Ad" button), scheduled ("Starts tomorrow" + chips + Cancel), live ("ON SALE — N days left" + featured items/sale prices + today's featured units sold).
- **New `ui/dialogs/WeeklyAdBuilderDialog.kt`** following BulkOrderDialog pattern: category chips + tier-filtered item list, tap to feature (≤ max), per-item discount Slider 10–50%, per-row stock display with low-stock warning ("sale items sell ~4× faster — stock up!") and boosted promo backroom cap shown (e.g., "cap 5 → 15 packs"), live cost line, confirm disabled if over cap/insufficient funds. Confirm → `onPublish(List<AdLineItem>)`.
- **New `ui/state/mappers/PromotionUiMapper.kt`** → `PromotionUiState` in GameUiState; callbacks through GameViewModel like pricing callbacks.

### Offline compatibility

All promo behavior is pure function of GameState inside tick pipeline; lifecycle in DayRolloverProcessor which OfflineCatchUpRunner exercises via `tick(offlineMode = true)`. No wall-clock, no accumulators. Publish/cancel are player actions only. Compatible as-is.

## Files

**Create:** `domain/promotions/PromotionState.kt`, `domain/promotions/PromotionManager.kt`, `ui/components/cards/WeeklyAdCard.kt`, `ui/dialogs/WeeklyAdBuilderDialog.kt`, `ui/state/mappers/PromotionUiMapper.kt`, androidTest `domain/promotions/PromotionManagerTest.kt` + `WeeklyAdIntegrationTest.kt`.

**Modify:** GameStateData.kt (field), PricingManager.kt (max-of discount), TrafficManager.kt (factor), TransactionEngine.kt (weightedSample + 2 call sites), InventoryManager.kt + TruckManager.kt (effectiveBackroomCap at 3 cap-read sites), DayRolloverProcessor.kt (hooks), DailyMetrics.kt (fields + netRevenue), GameEngine.kt (API), GameUiState/GameViewModel (state + callbacks), StoreHomeScreen.kt (card + dialog), EndOfDayReportDialog.kt (ad section).

No GameModule.kt change.

## Tests (androidTest, FakeItemDao, no Mockito, no trivial tests)

1. publishAd deducts cost, sets startDay = today+1, records adPublishCost
2. publishAd rejected: locked size / insufficient funds / ad exists / over item cap; discounts clamped 10–50
3. cancelScheduledAd full refund pre-live; no-op once live
4. expireAdIfNeeded clears exactly at startDay + duration, not earlier
5. recordAdDayMetrics aggregates only featured soldItemEvents
6. resolvePrice: live ad discounts featured item; markdown 30% + ad 20% → 30% (max-of); never below unitCost
7. Backroom cap: featured item accepts orders up to 3× cap while scheduled/live; non-featured stays at base cap; cap reverts on expiry but existing overflow stock untouched
8. Serialization round-trip with live ad; legacy JSON without promotionState → defaults

## Verification

1. Run tests above on emulator
2. Build + install to emulator: start fresh save, grind to SMALL_GROCERY, publish ad, verify: card states cycle (empty → scheduled → live → expired), discounted prices in transactions, visible traffic bump, end-of-day report ad section, low-stock featured item shows OOS losses
3. Save/load mid-ad → ad persists; offline catch-up across ad expiry → ad clears, metrics recorded

## Phasing

**MVP:** everything above.
**Polish (later):** ad history/lastAdReport, SALE badges in InventoryItemCard, OfflineEvent.AdExpired, StoreManagerConfig `autoAdEnabled` (algorithm below), traffic boost scaling with item count, traffic/demand statistical tests.

## Auto-Ad Algorithm (Store Manager, polish phase)

"Anchor + spotlight" model. Runs in StoreManagerConfig evaluation at day rollover when `autoAdEnabled` and no ad active. Designed to plug into affinity groups from `plans/plan-futureItems.md` (which adds `affinityGroups: List<String>` to ItemMetadata + `ItemMetadataCache.sharesAffinityGroup` + affinity term in `weightedSample`).

1. **Velocity** v(i): trailing 7-day units sold from `completedDayMetrics` sold events. Cold start: `purchaseWeight` proxy.
2. **Candidates**: current-tier, orderable, not already marked down.
3. **Slots**: `maxFeaturedItems(size)`; `ceil(slots/2)` anchors, rest spotlights.
4. **Anchors** (loss leaders, drive traffic): top by `v(i) × marginPerUnit(i)`; discount 25–35%.
5. **Spotlights** (velocity injection for slow items): top by `marginPerUnit(i) / (v(i)+1) × affinityBonus(i)`, where `affinityBonus = 2.0` if item shares affinity group with a selected anchor else 1.0. Discount 10–20% — exposure comes from `AD_FEATURED_DEMAND_MULT × affinityBoost` stacking in weightedSample, not margin sacrifice. Customers drawn by anchor; affinity spillover carries spotlight into basket.
6. **Diversity**: ≤2 items per category; spotlights span ≥2 affinity groups when possible.
7. **Budget gate**: cash ≥ publish cost + estimated pre-stock cost (autoHireBudget-style min-cash). Manager auto-orders featured items to promo cap during lead day via existing auto-order path.

Before affinity groups land, `affinityBonus ≡ 1.0` — algorithm degrades gracefully to pure velocity/margin selection; affinity slots in as one multiplicative term later.
