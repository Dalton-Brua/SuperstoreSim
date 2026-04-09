package com.example.superstoresimulator.domain.items

import com.example.superstoresimulator.domain.Money

/**
 * Tiers that unlock progressively more of the store. Each tier has a monetary
 * threshold (unlockAmount) and a set of store sections it makes available.
 *
 * [unlockCost] is the one-time purchase price the player pays from their cash
 * balance to activate the tier once its [unlockAmount] revenue gate is met.
 * It is set to 1/5 of the revenue threshold (rounded) so the player needs to
 * have earned the threshold AND have cash on hand to make the investment.
 */
enum class ItemUnlockTier(
    val unlockAmount: Long,
    val unlockedSections: Set<ItemCategory>,
    val displayName: String,
    val unlockCost: Money,
    val narrativeTitle: String,
    val narrativeDescription: String,
) {
	// Starting tier: shelf-stable, low-margin staples only — no fresh products, no specialty
	TIER_1(
		unlockAmount = 0L,
		unlockedSections = setOf(ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS),
		displayName = "Basics",
		unlockCost = Money(0L),
		narrativeTitle = "Your store is open!",
		narrativeDescription = "You start with shelf-stable grocery staples, snacks, and drinks. " +
				"Build up revenue to invest in new sections.",
	),

	// Dairy added once the player has a baseline store — still low-complexity, no fresh produce yet
	TIER_2(
		unlockAmount = 500_000L,  // $5000 in cents
		unlockedSections = setOf(
			ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS,
			ItemCategory.DAIRY
		),
		displayName = "Dairy",
		unlockCost = Money(100_000L),    // $1,000 — 1/5 of the revenue gate
		narrativeTitle = "Install Dairy Coolers",
		narrativeDescription = "Purchase and install commercial refrigeration units along the " +
				"back wall to stock milk, cheese, yogurt, and other dairy products. " +
				"Also unlocks Bulk Ordering — purchase entire categories at once with " +
				"tiered discounts: 10% off 20+ cases, 15% off 50+ cases, up to 25% off 100+ cases.",
	),

	// Fresh and frozen unlock together — PRODUCE demands active restocking like FROZEN and BAKERY
	TIER_3(
		unlockAmount = 2_000_000L,  // $20,000 in cents
		unlockedSections = setOf(
			ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS,
			ItemCategory.DAIRY,
			ItemCategory.FROZEN, ItemCategory.BAKERY, ItemCategory.PRODUCE
		),
		displayName = "Fresh & Frozen",
		unlockCost = Money(400_000L),    // $4,000 — 1/5 of the revenue gate
		narrativeTitle = "Add Fresh & Frozen Sections",
		narrativeDescription = "Install a walk-in freezer and fresh produce displays to carry " +
				"frozen goods, in-store baked items, and fresh fruits and vegetables.",
	),

	// Full store: high-margin and specialty departments
	TIER_GM(
		unlockAmount = 10_000_000L,  // $100,000 in cents
		unlockedSections = ItemCategory.entries.toSet(),
		displayName = "Full Store",
		unlockCost = Money(2_000_000L),  // $20,000 — 1/5 of the revenue gate
		narrativeTitle = "Open All Departments",
		narrativeDescription = "Build out specialty sections — a full-service meat counter, " +
				"pharmacy, health & beauty, household goods, and electronics — to complete " +
				"your superstore.",
	);

	companion object {
		// Ordered list from lowest to highest tier
		val ordered = entries.sortedBy { it.unlockAmount }

		/**
		 * Return the highest tier that is unlocked given totalEarned.
		 */
		fun fromTotalEarned(totalEarned: Long): ItemUnlockTier {
			return ordered.lastOrNull { totalEarned >= it.unlockAmount } ?: TIER_1
		}

		/**
		 * Return the next tier after the provided one, or null if at max.
		 */
		fun nextTier(current: ItemUnlockTier): ItemUnlockTier? {
			val idx = ordered.indexOf(current)
			return if (idx >= 0 && idx < ordered.size - 1) ordered[idx + 1] else null
		}
	}
}

fun requiredTierForSection(section: ItemCategory): ItemUnlockTier {
	return when (section) {
		ItemCategory.GROCERY, ItemCategory.SNACKS, ItemCategory.DRINKS -> ItemUnlockTier.TIER_1
		ItemCategory.DAIRY -> ItemUnlockTier.TIER_2
		ItemCategory.FROZEN, ItemCategory.BAKERY, ItemCategory.PRODUCE -> ItemUnlockTier.TIER_3
		ItemCategory.MEAT, ItemCategory.HEALTH, ItemCategory.HOUSEHOLD,
		ItemCategory.PHARMACY, ItemCategory.ELECTRONICS -> ItemUnlockTier.TIER_GM
	}
}
