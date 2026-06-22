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

    /** Advance every secondary store one simulated day and fold net profit into money. */
    fun simulateDay(state: GameState): GameState {
        // TODO(Track A): implement the daily sim described above.
        return state
    }

    /** Store weight contributed to its region (size + marketing). Used by saturation + manager. */
    fun storeWeight(store: SecondaryStore): Float {
        // TODO(Track A): EmpireTuning.sizeWeight(size) * (1 + marketing bonus)
        return EmpireTuning.sizeWeight(store.storeSize)
    }

    /** Saturation factor for a region given its total store weight. 1.0 at/under capacity. */
    fun saturationFactor(totalWeight: Float, capacity: Float): Float {
        // TODO(Track A): smooth falloff above capacity using SATURATION_STEEPNESS.
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
        // TODO(Track A): closed-form estimate matching simulateDay's math.
        return Money.ZERO
    }
}
