package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.GameState
import com.example.superstoresimulator.domain.Money

/**
 * TRACK A (Sim Engine) — fill the bodies. Wave-0 stub: no-ops that compile.
 *
 * Regional manager hire/fire + the saturation-aware day-rollover assignment algorithm.
 *
 * [assignDirections] (runs at day rollover, before the sim, only if a manager is hired):
 *  1. Tentatively set EVERY managed store to manager.personality.preferred.
 *  2. Estimate empire net profit (sum [SecondaryStoreSimManager.estimateStoreNetProfit]
 *       per store, accounting for each region's saturationFactor at the tentative weights,
 *       minus manager salary).
 *  3. While empire net profit < [EmpireTuning.MANAGER_PROFIT_FLOOR]: sort stores by how
 *       much they hurt the bottom line on the preferred direction (worst first — an
 *       AGGRESSIVE store crowding its region shows up here), flip the worst to a
 *       profit-preserving direction (PASSIVE/DEFENSIVE) one at a time, re-estimating.
 *  4. Leave the max possible stores on the preferred direction; mark all managed stores
 *       managedByRegionalManager = true with the assigned direction.
 */
object RegionalManagerManager {

    /** Direction the manager falls back to when the preferred posture loses money. */
    private val FALLBACK_DIRECTION = StoreDirection.DEFENSIVE

    /** Hire a manager of [personality] (gated by store count + affordability). */
    fun hire(state: GameState, personality: ManagerPersonality): GameState {
        if (!state.empireModeActive) return state
        if (state.regionalManager != null) return state
        if (state.secondaryStores.size < EmpireTuning.MANAGER_HIRE_MIN_STORES) return state

        val manager = RegionalManager(
            name = "Regional Manager",
            personality = personality,
            hiredAtTime = state.currentTime,
        )
        return assignDirections(state.copy(regionalManager = manager))
    }

    /** Fire the manager; return direction control to the player. */
    fun fire(state: GameState): GameState {
        val released = state.secondaryStores.map { it.copy(managedByRegionalManager = false) }
        return state.copy(regionalManager = null, secondaryStores = released)
    }

    /** Reassign directions across managed stores (called by the sim each day). */
    fun assignDirections(state: GameState): GameState {
        val manager = state.regionalManager ?: return state
        val pref = manager.personality.preferred
        val salary = EmpireTuning.MANAGER_SALARY_PER_DAY

        // 1. Start with every managed store on the preferred direction.
        var stores = state.secondaryStores.map {
            it.copy(direction = pref, managedByRegionalManager = true)
        }

        // Saturation per region is computed from store weight, which is direction-independent,
        // so it is stable across direction flips below.
        fun satFor(regionId: Int): Float {
            val region = state.regions.firstOrNull { it.regionId == regionId } ?: return 1f
            val totalWeight = stores
                .filter { it.regionId == regionId }
                .map { SecondaryStoreSimManager.storeWeight(it) }
                .sum()
            return SecondaryStoreSimManager.saturationFactor(totalWeight, region.capacity)
        }

        fun empireNet(list: List<SecondaryStore>): Money {
            var sum = Money.ZERO
            for (s in list) {
                sum += SecondaryStoreSimManager.estimateStoreNetProfit(state, s, satFor(s.regionId))
            }
            return sum - salary
        }

        // 2/3. While below the profit floor, flip the worst preferred-direction store to the
        // fallback posture. Pick the flip that improves empire net the most each round.
        // ponytail: O(n^2) flip search, fine for small store counts
        while (empireNet(stores) < EmpireTuning.MANAGER_PROFIT_FLOOR) {
            val currentNet = empireNet(stores)
            var bestIdx = -1
            var bestNet = currentNet
            for (i in stores.indices) {
                if (stores[i].direction != pref) continue
                val candidate = stores.toMutableList()
                candidate[i] = candidate[i].copy(direction = FALLBACK_DIRECTION)
                val candidateNet = empireNet(candidate)
                if (candidateNet > bestNet) {
                    bestNet = candidateNet
                    bestIdx = i
                }
            }
            if (bestIdx < 0) break // no improving flip (or all already flipped) → stop
            stores = stores.toMutableList().also {
                it[bestIdx] = it[bestIdx].copy(direction = FALLBACK_DIRECTION)
            }
        }

        return state.copy(secondaryStores = stores)
    }
}
