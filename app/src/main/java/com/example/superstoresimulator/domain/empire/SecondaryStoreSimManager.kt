package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money

/**
 * TRACK A (Sim Engine) — fill the bodies. Wave-0 stub: no-ops that compile.
 *
 * Closed-form daily economic sim for every secondary store. Runs once per simulated
 * day (called by [EmpireClockController.advanceOneDay]). O(stores) cost.
 *
 * Per day:
 *  0. [RegionalManagerManager.assignDirections] first (manager owns direction if hired).
 *  1. Per region: sum store weight, compute saturationFactor (see [EmpireTuning.SATURATION_STEEPNESS]).
 *  2. Per store: traffic = base(size) × direction.trafficMult × upgradeFactors
 *       × region.demandProfile.baseTraffic × saturationFactor × globalFactors(research/reputation/vendor).
 *  3. avgPriceLevel from direction.priceMult.
 *  4. revenue = customers × avgBasket × region.baseSpendingPower × priceLevel.
 *  5. operatingCost = base(size) × direction.costMult × upgradeFactors (LOGISTICS reduces).
 *  6. apply decayed operatingPerformance to revenue/profit.
 *  7. netProfit = revenue × operatingPerformance − operatingCost; sum into global money,
 *       minus manager salary if hired.
 *  8. write SimStoreMetrics into currentDayMetrics; push prior day to completedDayMetrics.
 *  Region capacity/traffic drifts by demandProfile.growthRate over days.
 */
object SecondaryStoreSimManager {

    /** Most recent completed-day metrics retained per store. */
    private const val MAX_COMPLETED_METRICS = 60

    /** Advance every secondary store one simulated day and fold net profit into money. */
    fun simulateDay(state: GameState): GameState {
        if (!state.empireModeActive || state.secondaryStores.isEmpty()) return state

        // 1. Manager owns direction if hired.
        var working = RegionalManagerManager.assignDirections(state)

        // 2. Current day index.
        val currentDay = working.currentTime.dayNumber

        // 3. Apply hands-on-performance decay to each store.
        val decayedStores = working.secondaryStores.map { store ->
            OperateStoreController.decayOperatingPerformance(store, currentDay)
        }

        // 4. Per region saturation factor at current weights.
        val satByRegion: Map<Int, Float> = working.regions.associate { region ->
            val totalWeight = decayedStores
                .filter { it.regionId == region.regionId }
                .map { storeWeight(it) }
                .sum()
            region.regionId to saturationFactor(totalWeight, region.capacity)
        }

        // 5/6. Compute today's metrics and roll the per-store metric windows.
        var totalNet = Money.ZERO
        val updatedStores = decayedStores.map { store ->
            val region = working.regions.firstOrNull { it.regionId == store.regionId }
            val satFactor = satByRegion[store.regionId] ?: 1.0f
            val mods = EmpireTuning.modifiersFor(store.direction)

            if (region == null) {
                // No region → no output this day, but still roll metrics forward.
                val empty = SimStoreMetrics(dayIndex = currentDay)
                store.copy(
                    completedDayMetrics = rollCompleted(store, currentDay),
                    currentDayMetrics = empty,
                )
            } else {
                val customers = (
                    EmpireTuning.BASE_DAILY_CUSTOMERS *
                        store.storeSize.trafficMultiplier *
                        mods.trafficMult *
                        upgradeTrafficFactor(store) *
                        region.demandProfile.baseTraffic *
                        satFactor *
                        globalFactor(working)
                    ).toInt()

                val avgBasketCents = EmpireTuning.BASE_AVG_BASKET.cents *
                    store.storeSize.basketSizeMultiplier *
                    region.demandProfile.baseSpendingPower *
                    mods.priceMult

                val revenue = Money((customers * avgBasketCents).toLong())
                val operatingCost = Money(
                    (EmpireTuning.BASE_OPERATING_COST.cents *
                        EmpireTuning.sizeWeight(store.storeSize) *
                        mods.costMult *
                        upgradeCostFactor(store)
                        ).toLong()
                )
                val netProfit = Money((revenue.cents * store.operatingPerformance).toLong()) - operatingCost
                totalNet += netProfit

                val metrics = SimStoreMetrics(
                    dayIndex = currentDay,
                    customers = customers,
                    revenue = revenue,
                    operatingCost = operatingCost,
                    netProfit = netProfit,
                    avgPriceLevel = mods.priceMult,
                    trafficVsGoal = satFactor * mods.trafficMult,
                )
                store.copy(
                    completedDayMetrics = rollCompleted(store, currentDay),
                    currentDayMetrics = metrics,
                )
            }
        }

        // 7. Subtract manager salary if hired.
        if (working.regionalManager != null) {
            totalNet -= EmpireTuning.MANAGER_SALARY_PER_DAY
        }

        // 7b. Charge ongoing region-investment cost (scales with the region's population).
        working.regions.filter { it.investing }.forEach { region ->
            totalNet -= EmpireTuning.regionInvestDailyCost(region.demandProfile.baseTraffic)
        }

        // Drift each region's capacity by its growth rate, then compound any active investment
        // into both capacity (saturation headroom) and demand (traffic + spending).
        val driftedRegions = working.regions.map { region ->
            val grown = region.copy(capacity = region.capacity * (1f + region.demandProfile.growthRate))
            if (!region.investing) grown else {
                val g = EmpireTuning.REGION_INVEST_DAILY_GROWTH
                grown.copy(
                    capacity = grown.capacity * g,
                    demandProfile = grown.demandProfile.copy(
                        baseTraffic = grown.demandProfile.baseTraffic * g,
                        baseSpendingPower = grown.demandProfile.baseSpendingPower * g,
                    ),
                )
            }
        }

        working = working.copy(
            money = working.money + totalNet,
            secondaryStores = updatedStores,
            regions = driftedRegions,
        )
        return working
    }

    /**
     * Push the store's EXISTING currentDayMetrics into the completed window when it
     * belongs to a prior day (avoids duplicating the seed metrics on the first run),
     * keeping only the most recent [MAX_COMPLETED_METRICS] entries.
     */
    private fun rollCompleted(store: SecondaryStore, currentDay: Int): List<SimStoreMetrics> {
        if (store.currentDayMetrics.dayIndex >= currentDay) return store.completedDayMetrics
        val combined = store.completedDayMetrics + store.currentDayMetrics
        return if (combined.size > MAX_COMPLETED_METRICS) {
            combined.subList(combined.size - MAX_COMPLETED_METRICS, combined.size)
        } else {
            combined
        }
    }

    /** Store weight contributed to its region (size + marketing). Used by saturation + manager. */
    fun storeWeight(store: SecondaryStore): Float {
        val marketingBonus =
            if (StoreUpgrade.MARKETING in store.upgrades) EmpireTuning.MARKETING_WEIGHT_BONUS else 0f
        return EmpireTuning.sizeWeight(store.storeSize) * (1f + marketingBonus)
    }

    /** Saturation factor for a region given its total store weight. 1.0 at/under capacity. */
    fun saturationFactor(totalWeight: Float, capacity: Float): Float {
        val load = if (capacity > 0f) totalWeight / capacity else 0f
        return if (load <= 1f) 1f else 1f / (1f + (load - 1f) * EmpireTuning.SATURATION_STEEPNESS)
    }

    /** Traffic multiplier from per-store upgrade flags. */
    private fun upgradeTrafficFactor(store: SecondaryStore): Float {
        var factor = 1f
        if (StoreUpgrade.EXTRA_REGISTERS in store.upgrades) factor += EmpireTuning.EXTRA_REGISTERS_TRAFFIC_BONUS
        if (StoreUpgrade.MARKETING in store.upgrades) factor += EmpireTuning.MARKETING_TRAFFIC_BONUS
        return factor
    }

    /** Operating-cost multiplier from per-store upgrade flags (LOGISTICS reduces cost). */
    private fun upgradeCostFactor(store: SecondaryStore): Float {
        var factor = 1f
        if (StoreUpgrade.LOGISTICS in store.upgrades) factor -= EmpireTuning.LOGISTICS_COST_REDUCTION
        return factor
    }

    /** Global sim multiplier (research/reputation/vendor). */
    private fun globalFactor(state: GameState): Float {
        // ponytail: hook for research/reputation/vendor multipliers, 1.0 baseline
        return 1.0f
    }

    /**
     * Estimate one store's daily net profit under [overrideDirection] (or its current
     * direction if null) and a given region saturation factor. Used by the manager's
     * profit-floor search. Pure — no state mutation.
     */
    fun estimateStoreNetProfit(
        state: GameState,
        store: SecondaryStore,
        saturationFactor: Float,
        overrideDirection: StoreDirection? = null,
    ): Money {
        val dir = overrideDirection ?: store.direction
        val mods = EmpireTuning.modifiersFor(dir)
        val region = state.regions.firstOrNull { it.regionId == store.regionId } ?: return Money.ZERO

        val customers = (
            EmpireTuning.BASE_DAILY_CUSTOMERS *
                store.storeSize.trafficMultiplier *
                mods.trafficMult *
                upgradeTrafficFactor(store) *
                region.demandProfile.baseTraffic *
                saturationFactor *
                globalFactor(state)
            ).toInt()

        val avgBasketCents = EmpireTuning.BASE_AVG_BASKET.cents *
            store.storeSize.basketSizeMultiplier *
            region.demandProfile.baseSpendingPower *
            mods.priceMult

        val revenue = Money((customers * avgBasketCents).toLong())
        val operatingCost = Money(
            (EmpireTuning.BASE_OPERATING_COST.cents *
                EmpireTuning.sizeWeight(store.storeSize) *
                mods.costMult *
                upgradeCostFactor(store)
                ).toLong()
        )
        return Money((revenue.cents * store.operatingPerformance).toLong()) - operatingCost
    }
}
