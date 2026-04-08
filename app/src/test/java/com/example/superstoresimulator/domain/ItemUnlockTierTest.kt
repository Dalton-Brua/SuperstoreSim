package com.example.superstoresimulator.domain

import com.example.superstoresimulator.domain.items.ItemCategory
import com.example.superstoresimulator.domain.items.ItemUnlockTier
import com.example.superstoresimulator.domain.items.requiredTierForSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [ItemUnlockTier] and [requiredTierForSection].
 *
 * No mocking required — these are value/enum tests that run against the compiled
 * constants directly, so they pass even in environments where Mockito's byte-buddy
 * cannot instrument Java-25 class files.
 */
class ItemUnlockTierTest {

    // ── fromTotalEarned ───────────────────────────────────────────────────────

    @Test
    fun `fromTotalEarned returns TIER_1 at zero`() {
        assertEquals(ItemUnlockTier.TIER_1, ItemUnlockTier.fromTotalEarned(0L))
    }

    @Test
    fun `fromTotalEarned returns TIER_1 just below TIER_2 threshold`() {
        assertEquals(ItemUnlockTier.TIER_1, ItemUnlockTier.fromTotalEarned(499_999L))
    }

    @Test
    fun `fromTotalEarned returns TIER_2 exactly at its threshold`() {
        assertEquals(ItemUnlockTier.TIER_2, ItemUnlockTier.fromTotalEarned(500_000L))
    }

    @Test
    fun `fromTotalEarned returns TIER_2 just below TIER_3 threshold`() {
        assertEquals(ItemUnlockTier.TIER_2, ItemUnlockTier.fromTotalEarned(1_999_999L))
    }

    @Test
    fun `fromTotalEarned returns TIER_3 exactly at its threshold`() {
        assertEquals(ItemUnlockTier.TIER_3, ItemUnlockTier.fromTotalEarned(2_000_000L))
    }

    @Test
    fun `fromTotalEarned returns TIER_3 just below TIER_GM threshold`() {
        assertEquals(ItemUnlockTier.TIER_3, ItemUnlockTier.fromTotalEarned(9_999_999L))
    }

    @Test
    fun `fromTotalEarned returns TIER_GM exactly at its threshold`() {
        assertEquals(ItemUnlockTier.TIER_GM, ItemUnlockTier.fromTotalEarned(10_000_000L))
    }

    @Test
    fun `fromTotalEarned returns TIER_GM well above max threshold`() {
        assertEquals(ItemUnlockTier.TIER_GM, ItemUnlockTier.fromTotalEarned(999_999_999L))
    }

    // ── nextTier companion ────────────────────────────────────────────────────

    @Test
    fun `nextTier returns correct successor tiers`() {
        assertEquals(ItemUnlockTier.TIER_2, ItemUnlockTier.nextTier(ItemUnlockTier.TIER_1))
        assertEquals(ItemUnlockTier.TIER_3, ItemUnlockTier.nextTier(ItemUnlockTier.TIER_2))
        assertEquals(ItemUnlockTier.TIER_GM, ItemUnlockTier.nextTier(ItemUnlockTier.TIER_3))
        assertNull("nextTier of TIER_GM should be null", ItemUnlockTier.nextTier(ItemUnlockTier.TIER_GM))
    }


    // ── category boundary tests ───────────────────────────────────────────────

    @Test
    fun `TIER_1 contains GROCERY SNACKS and DRINKS only`() {
        val sections = ItemUnlockTier.TIER_1.unlockedSections
        assertTrue(ItemCategory.GROCERY in sections)
        assertTrue(ItemCategory.SNACKS in sections)
        assertTrue(ItemCategory.DRINKS in sections)
        assertFalse("DAIRY should not be in TIER_1", ItemCategory.DAIRY in sections)
        assertFalse("PRODUCE should not be in TIER_1", ItemCategory.PRODUCE in sections)
        assertFalse("FROZEN should not be in TIER_1", ItemCategory.FROZEN in sections)
        assertFalse("BAKERY should not be in TIER_1", ItemCategory.BAKERY in sections)
        assertFalse("MEAT should not be in TIER_1", ItemCategory.MEAT in sections)
        assertFalse("HEALTH should not be in TIER_1", ItemCategory.HEALTH in sections)
    }

    @Test
    fun `TIER_2 adds DAIRY and keeps all TIER_1 sections`() {
        val sections = ItemUnlockTier.TIER_2.unlockedSections
        assertTrue(ItemCategory.DAIRY in sections)
        assertTrue(ItemCategory.GROCERY in sections)
        assertTrue(ItemCategory.SNACKS in sections)
        assertTrue(ItemCategory.DRINKS in sections)
        assertFalse("PRODUCE should not be in TIER_2", ItemCategory.PRODUCE in sections)
        assertFalse("FROZEN should not be in TIER_2", ItemCategory.FROZEN in sections)
        assertFalse("BAKERY should not be in TIER_2", ItemCategory.BAKERY in sections)
    }

    @Test
    fun `TIER_3 adds PRODUCE FROZEN BAKERY to TIER_2 sections`() {
        val sections = ItemUnlockTier.TIER_3.unlockedSections
        assertTrue(ItemCategory.PRODUCE in sections)
        assertTrue(ItemCategory.FROZEN in sections)
        assertTrue(ItemCategory.BAKERY in sections)
        assertTrue(ItemCategory.DAIRY in sections)
        assertTrue(ItemCategory.GROCERY in sections)
        assertFalse("MEAT should not be in TIER_3", ItemCategory.MEAT in sections)
        assertFalse("HEALTH should not be in TIER_3", ItemCategory.HEALTH in sections)
        assertFalse("HOUSEHOLD should not be in TIER_3", ItemCategory.HOUSEHOLD in sections)
        assertFalse("PHARMACY should not be in TIER_3", ItemCategory.PHARMACY in sections)
        assertFalse("ELECTRONICS should not be in TIER_3", ItemCategory.ELECTRONICS in sections)
    }

    @Test
    fun `TIER_GM contains every ItemCategory`() {
        val sections = ItemUnlockTier.TIER_GM.unlockedSections
        ItemCategory.entries.forEach { category ->
            assertTrue("TIER_GM must include $category", category in sections)
        }
    }

    // ── tier is strictly additive (each tier is a superset of the one below) ──

    @Test
    fun `each tier is a strict superset of the previous tier`() {
        val ordered = ItemUnlockTier.ordered
        for (i in 1 until ordered.size) {
            val lower = ordered[i - 1]
            val higher = ordered[i]
            assertTrue(
                "${higher.name} must contain all sections from ${lower.name}",
                higher.unlockedSections.containsAll(lower.unlockedSections)
            )
        }
    }

    // ── requiredTierForSection ────────────────────────────────────────────────

    @Test
    fun `requiredTierForSection maps GROCERY SNACKS DRINKS to TIER_1`() {
        assertEquals(ItemUnlockTier.TIER_1, requiredTierForSection(ItemCategory.GROCERY))
        assertEquals(ItemUnlockTier.TIER_1, requiredTierForSection(ItemCategory.SNACKS))
        assertEquals(ItemUnlockTier.TIER_1, requiredTierForSection(ItemCategory.DRINKS))
    }

    @Test
    fun `requiredTierForSection maps DAIRY to TIER_2`() {
        assertEquals(ItemUnlockTier.TIER_2, requiredTierForSection(ItemCategory.DAIRY))
    }

    @Test
    fun `requiredTierForSection maps PRODUCE FROZEN BAKERY to TIER_3`() {
        assertEquals(ItemUnlockTier.TIER_3, requiredTierForSection(ItemCategory.PRODUCE))
        assertEquals(ItemUnlockTier.TIER_3, requiredTierForSection(ItemCategory.FROZEN))
        assertEquals(ItemUnlockTier.TIER_3, requiredTierForSection(ItemCategory.BAKERY))
    }

    @Test
    fun `requiredTierForSection maps specialty categories to TIER_GM`() {
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.MEAT))
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.HEALTH))
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.HOUSEHOLD))
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.PHARMACY))
        assertEquals(ItemUnlockTier.TIER_GM, requiredTierForSection(ItemCategory.ELECTRONICS))
    }

    // ── requiredTierForSection must be consistent with unlockedSections ──────

    @Test
    fun `requiredTierForSection is consistent with unlockedSections for all categories`() {
        ItemCategory.entries.forEach { category ->
            val required = requiredTierForSection(category)
            assertTrue(
                "$category should be in ${required.name}.unlockedSections",
                category in required.unlockedSections
            )
            // Also verify it's NOT in the tier below (if one exists)
            val lowerTier = ItemUnlockTier.ordered.getOrNull(ItemUnlockTier.ordered.indexOf(required) - 1)
            if (lowerTier != null) {
                assertFalse(
                    "$category should NOT be in ${lowerTier.name}.unlockedSections",
                    category in lowerTier.unlockedSections
                )
            }
        }
    }
}
