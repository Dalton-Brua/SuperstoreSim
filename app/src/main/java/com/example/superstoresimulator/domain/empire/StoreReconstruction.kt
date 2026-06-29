package com.example.superstoresimulator.domain.empire

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.research.ResearchCategory
import com.example.superstoresimulator.domain.research.ResearchUpgradeRegistry
import com.example.superstoresimulator.domain.store.StoreSize
import kotlin.math.ceil
import kotlin.random.Random

/**
 * Pure planner that fabricates a plausible "already-running" state for a secondary store
 * the player has never operated by hand. Picks a register count, a researched-upgrade set,
 * and a staff roster from per-[StoreSize] min/max bands so a hands-on drop-in lands on a
 * working store instead of an empty shell.
 *
 * Research is unlocked lowest-tier-first (ascending [ResearchableUpgrade.researchCost],
 * then prerequisite-closed so the set is always valid). Supercenters always have every
 * non-product-line study (all Operations + register research, etc.) and a random — usually
 * most — slice of product lines.
 *
 * Deterministic given [Random]; [GameEngine] turns the plan into a full [StoreOperatingState]
 * (it needs the item catalog to seed inventory scoped to the researched product lines).
 */
object StoreReconstruction {

    data class StoreSeedPlan(
        val registerCount: Int,
        val researchedUpgrades: Set<String>,
        val staff: List<EntityDef>,
    )

    /** Inclusive [min, max] register band per size (capped at [StoreSize.maxRegisters]). */
    private fun registerBand(size: StoreSize): IntRange = when (size) {
        StoreSize.MOM_AND_POP   -> 1..1
        StoreSize.SMALL_GROCERY -> 1..2
        StoreSize.GROCERY_STORE -> 2..3
        StoreSize.SUPERSTORE    -> 3..5
        StoreSize.SUPERCENTER   -> 6..8
    }

    /** Inclusive [min, max] count of researched upgrades per size (ignored for supercenter). */
    private fun researchBand(size: StoreSize): IntRange = when (size) {
        StoreSize.MOM_AND_POP   -> 3..8
        StoreSize.SMALL_GROCERY -> 8..16
        StoreSize.GROCERY_STORE -> 18..30
        StoreSize.SUPERSTORE    -> 35..55
        StoreSize.SUPERCENTER   -> 0..0 // handled specially
    }

    fun plan(size: StoreSize, random: Random): StoreSeedPlan {
        val registers = registerBand(size).randomIn(random).coerceIn(1, size.maxRegisters)
        val research = researchFor(size, random)
        return StoreSeedPlan(
            registerCount = registers,
            researchedUpgrades = research,
            staff = staffFor(size, research),
        )
    }

    // ── Research selection ──────────────────────────────────────────────────────
    /** All upgrade ids ordered lowest-tier first (cost asc, id tiebreak for stability). */
    private val orderedByTier: List<String> =
        ResearchUpgradeRegistry.allUpgrades.values
            .sortedWith(compareBy({ it.researchCost }, { it.id }))
            .map { it.id }

    private fun researchFor(size: StoreSize, random: Random): Set<String> {
        if (size == StoreSize.SUPERCENTER) {
            // Everything except product lines is guaranteed; product lines are usually-most.
            val nonProduct = ResearchUpgradeRegistry.allUpgrades.values
                .filter { it.category != ResearchCategory.PRODUCT_LINES }
                .map { it.id }
            val products = ResearchUpgradeRegistry.allUpgrades.values
                .filter { it.category == ResearchCategory.PRODUCT_LINES }
                .map { it.id }
            val frac = 0.7f + random.nextFloat() * 0.3f // 70–100% of product lines
            val keep = ceil(products.size * frac).toInt().coerceIn(0, products.size)
            return prereqClosure((nonProduct + products.shuffled(random).take(keep)).toSet())
        }
        val n = researchBand(size).randomIn(random)
        return prereqClosure(orderedByTier.take(n).toSet())
    }

    /** Pull in every prerequisite transitively so the researched set is always valid. */
    private fun prereqClosure(ids: Set<String>): Set<String> {
        val out = ids.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            for (id in out.toList()) {
                ResearchUpgradeRegistry.allUpgrades[id]?.prerequisites?.forEach { p ->
                    if (out.add(p)) changed = true
                }
            }
        }
        return out
    }

    // ── Staff roster ────────────────────────────────────────────────────────────
    private fun staffFor(size: StoreSize, researched: Set<String>): List<EntityDef> {
        val (cashiers, stockers, fresh, managers) = when (size) {
            StoreSize.MOM_AND_POP   -> StaffCounts(1, 1, 0, 0)
            StoreSize.SMALL_GROCERY -> StaffCounts(2, 2, 0, 0)
            StoreSize.GROCERY_STORE -> StaffCounts(4, 4, 1, 1)
            StoreSize.SUPERSTORE    -> StaffCounts(6, 6, 1, 1)
            StoreSize.SUPERCENTER   -> StaffCounts(10, 10, 2, 1)
        }
        val roster = mutableListOf<EntityDef>()
        repeat(cashiers) { roster += EntityDef.CASHIER }
        repeat(stockers) { roster += EntityDef.STOCKER }
        repeat(fresh) { roster += EntityDef.FRESH_HANDLER }
        // Managers only matter once their research exists, mirroring loop-1 hiring gates.
        if ("manager_hiring" in researched) repeat(managers) { roster += EntityDef.MANAGER }
        return roster
    }

    private data class StaffCounts(val cashiers: Int, val stockers: Int, val fresh: Int, val managers: Int)

    private fun IntRange.randomIn(random: Random): Int =
        if (first >= last) first else random.nextInt(first, last + 1)
}
