package com.example.superstoresimulator.domain.research

import com.example.superstoresimulator.domain.store.StoreSize
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the non-item research gates against the dangling-string-literal class of bug
 * (`prod_fresh_basics` never existed in the registry, so the feature it gated was
 * unreachable forever). [ResearchGates.validate] resolves every
 * referenced id against [ResearchUpgradeRegistry]; this test is what makes a typo or a
 * removed upgrade a red build instead of a silently-disabled feature.
 */
class ResearchGatesTest {

    @Test
    fun `every referenced gate id exists in the registry`() {
        // Throws IllegalStateException listing any dangling ids.
        ResearchGates.validate()
    }

    @Test
    fun `store size required upgrades all exist in the registry`() {
        val known = ResearchUpgradeRegistry.allUpgrades.keys
        for (size in StoreSize.entries) {
            val gate = size.requiredUpgradeId ?: continue
            assertTrue("StoreSize $size gate '$gate' missing from registry", gate in known)
        }
    }

    @Test
    fun `fresh subsystem locked with no research, unlocked by any perishable department`() {
        assertFalse(ResearchGates.hasFreshSubsystem(emptySet()))
        assertFalse(ResearchGates.hasFreshSubsystem(setOf("prod_breakfast", "category_pricing")))
        assertTrue(ResearchGates.hasFreshSubsystem(setOf("prod_produce")))
        assertTrue(ResearchGates.hasFreshSubsystem(setOf("prod_dairy_basics")))
    }

    @Test
    fun `every tutorial feature gate maps to a real upgrade`() {
        val known = ResearchUpgradeRegistry.allUpgrades.keys
        for ((feature, gate) in ResearchGates.FEATURE_UPGRADE) {
            assertTrue("Feature '$feature' gate '$gate' missing from registry", gate in known)
        }
    }

    @Test
    fun `isResearched reflects set membership`() {
        assertTrue(ResearchGates.isResearched(setOf("register_expansion"), "register_expansion"))
        assertFalse(ResearchGates.isResearched(emptySet(), "register_expansion"))
    }

    @Test
    fun `building purchase gate exists in registry`() {
        assertTrue(
            "building_purchase missing from registry",
            "building_purchase" in ResearchUpgradeRegistry.allUpgrades,
        )
    }

    @Test
    fun `truck upgrade gates exist in registry`() {
        assertTrue(
            "truck_upgrade_enhanced missing",
            "truck_upgrade_enhanced" in ResearchUpgradeRegistry.allUpgrades,
        )
        assertTrue(
            "truck_upgrade_heavy missing",
            "truck_upgrade_heavy" in ResearchUpgradeRegistry.allUpgrades,
        )
    }

    @Test
    fun `truck upgrade prerequisites form a valid chain`() {
        val enhanced = ResearchUpgradeRegistry.allUpgrades["truck_upgrade_enhanced"]!!
        assertTrue("extra_truck_slots" in enhanced.prerequisites)
        val heavy = ResearchUpgradeRegistry.allUpgrades["truck_upgrade_heavy"]!!
        assertTrue("truck_upgrade_enhanced" in heavy.prerequisites)
    }
}
