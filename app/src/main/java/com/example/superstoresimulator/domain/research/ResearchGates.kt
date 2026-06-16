package com.example.superstoresimulator.domain.research

import com.example.superstoresimulator.domain.store.StoreSize
import com.example.superstoresimulator.domain.tutorial.TutorialManager

/**
 * Single source of truth for non-item research gates — store size, registers, the
 * fresh subsystem, and tutorial feature visibility. Item gating lives on each item's
 * `researchGate` (consumed via `ItemMetadataCache.isItemAccessible`); this object
 * covers everything that used to be a free-floating string literal in a `when` ladder.
 *
 * Every upgrade id referenced here (plus [StoreSize.requiredUpgradeId]) is checked
 * against [ResearchUpgradeRegistry.allUpgrades] by [validate], so a typo or a removed
 * upgrade fails loudly at startup/test time instead of silently disabling a feature
 * forever.
 */
object ResearchGates {

    /**
     * Upgrades that introduce perishable inventory. Researching any one unlocks the
     * fresh subsystem: the Fresh inventory tab, fresh-handler hiring (manual + auto),
     * and fresh auto-ordering.
     */
    val FRESH_SUBSYSTEM_GATES: Set<String> = setOf(
        "prod_dairy_basics",
        "prod_produce",
        "prod_bakery",
        "prod_frozen_basics",
        "prod_meat_counter",
    )

    /** Required upgrade for purchasing additional registers. */
    const val REGISTER_EXPANSION = "register_expansion"

    /** Bulk-ordering UI (the Bulk Order button + dialog on the Inventory screen). */
    const val BULK_ORDERING = "bulk_ordering"

    /** Category-level markup sliders (gates the whole Store Pricing card via [FEATURE_UPGRADE]). */
    const val CATEGORY_PRICING = "category_pricing"

    /** Store-wide default markup slider. */
    const val DEFAULT_MARKUP = "default_markup"

    /** Per-item price override sliders on the item detail screen. */
    const val ITEM_PRICING = "item_pricing"

    /**
     * Tutorial feature key (a [TutorialManager] `FEATURE_*` constant) → the research
     * upgrade that unlocks it once the tutorial is complete. Features absent from this
     * map have no research gate.
     */
    val FEATURE_UPGRADE: Map<String, String> = mapOf(
        TutorialManager.FEATURE_PRICING_UI to DEFAULT_MARKUP,
        TutorialManager.FEATURE_DELIVERY_SETTINGS to "extra_truck_slots",
        TutorialManager.FEATURE_REGISTERS to REGISTER_EXPANSION,
        TutorialManager.FEATURE_BULK_ORDERING to BULK_ORDERING,
        TutorialManager.FEATURE_MANAGER_HIRING to "manager_hiring",
    )

    /** Whether [upgradeId] has been researched. */
    fun isResearched(researched: Set<String>, upgradeId: String): Boolean =
        upgradeId in researched

    /** Whether the fresh subsystem is unlocked (any perishable department researched). */
    fun hasFreshSubsystem(researched: Set<String>): Boolean =
        researched.any { it in FRESH_SUBSYSTEM_GATES }

    /** Every registry upgrade id the non-item gates depend on. */
    private fun referencedUpgradeIds(): Set<String> =
        FRESH_SUBSYSTEM_GATES +
            FEATURE_UPGRADE.values +
            setOf(CATEGORY_PRICING, DEFAULT_MARKUP, ITEM_PRICING) +
            StoreSize.entries.mapNotNull { it.requiredUpgradeId }

    /**
     * Assert every referenced gate id exists in [ResearchUpgradeRegistry]. Throws
     * [IllegalStateException] listing any dangling ids. Called at app startup and in a
     * unit test so a bad gate id is caught immediately rather than disabling a feature.
     */
    fun validate() {
        val known = ResearchUpgradeRegistry.allUpgrades.keys
        val dangling = referencedUpgradeIds() - known
        check(dangling.isEmpty()) {
            "ResearchGates references unknown upgrade ids: ${dangling.sorted()}"
        }
    }
}
