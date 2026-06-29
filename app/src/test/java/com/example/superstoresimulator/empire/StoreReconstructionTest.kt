package com.example.superstoresimulator.empire

import com.example.superstoresimulator.domain.Entities.EntityDef
import com.example.superstoresimulator.domain.empire.StoreReconstruction
import com.example.superstoresimulator.domain.research.ResearchCategory
import com.example.superstoresimulator.domain.research.ResearchUpgradeRegistry
import com.example.superstoresimulator.domain.store.StoreSize
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class StoreReconstructionTest {

    private fun plans(size: StoreSize, n: Int = 50) =
        (0 until n).map { StoreReconstruction.plan(size, Random(it.toLong())) }

    @Test
    fun registerCount_withinBand_andCappedByStoreSize() {
        for (size in StoreSize.entries) {
            for (plan in plans(size)) {
                assertTrue("≥1 register", plan.registerCount >= 1)
                assertTrue(
                    "${size} register count ${plan.registerCount} exceeds cap ${size.maxRegisters}",
                    plan.registerCount <= size.maxRegisters,
                )
            }
        }
    }

    @Test
    fun research_isAlwaysPrerequisiteClosed() {
        for (size in StoreSize.entries) {
            for (plan in plans(size)) {
                for (id in plan.researchedUpgrades) {
                    val prereqs = ResearchUpgradeRegistry.allUpgrades[id]?.prerequisites ?: emptyList()
                    assertTrue(
                        "$id researched but missing prereqs $prereqs",
                        plan.researchedUpgrades.containsAll(prereqs),
                    )
                }
            }
        }
    }

    @Test
    fun research_unlocksLowestTierFirst() {
        // The cheapest upgrade overall should appear in every non-empty plan; an expensive
        // late-tier one should not show up on the smallest store.
        val cheapest = ResearchUpgradeRegistry.allUpgrades.values.minByOrNull { it.researchCost }!!.id
        val priciest = ResearchUpgradeRegistry.allUpgrades.values.maxByOrNull { it.researchCost }!!.id
        for (plan in plans(StoreSize.MOM_AND_POP)) {
            assertTrue("cheapest tier present", cheapest in plan.researchedUpgrades)
            assertTrue("priciest tier absent on smallest store", priciest !in plan.researchedUpgrades)
        }
    }

    @Test
    fun supercenter_unlocksAllNonProductLineResearch() {
        val nonProduct = ResearchUpgradeRegistry.allUpgrades.values
            .filter { it.category != ResearchCategory.PRODUCT_LINES }
            .map { it.id }
        for (plan in plans(StoreSize.SUPERCENTER)) {
            assertTrue(
                "supercenter missing non-product research",
                plan.researchedUpgrades.containsAll(nonProduct),
            )
        }
    }

    @Test
    fun supercenter_keepsMostButNotAlwaysAllProductLines() {
        val products = ResearchUpgradeRegistry.allUpgrades.values
            .filter { it.category == ResearchCategory.PRODUCT_LINES }
            .map { it.id }
        val productCounts = plans(StoreSize.SUPERCENTER, 100).map { plan ->
            products.count { it in plan.researchedUpgrades }
        }
        assertTrue("always ≥70% of product lines", productCounts.all { it >= products.size * 0.7 })
        assertTrue("sometimes fewer than all product lines", productCounts.any { it < products.size })
    }

    @Test
    fun staff_scalesWithSizeAndGatesManagerOnResearch() {
        val mom = StoreReconstruction.plan(StoreSize.MOM_AND_POP, Random(1))
        val center = StoreReconstruction.plan(StoreSize.SUPERCENTER, Random(1))
        assertTrue("supercenter has more staff", center.staff.size > mom.staff.size)
        for (size in StoreSize.entries) {
            for (plan in plans(size)) {
                if (EntityDef.MANAGER in plan.staff) {
                    assertTrue("manager hired without research", "manager_hiring" in plan.researchedUpgrades)
                }
            }
        }
    }
}
