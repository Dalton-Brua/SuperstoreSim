package com.example.superstoresimulator.domain.store

import com.example.superstoresimulator.domain.Money

/**
 * Store size determines daily rent, shelf capacity, backroom storage limits, and customer traffic.
 * Player starts at MOM_AND_POP and can upgrade to larger sizes.
 *
 * Each size has:
 * - [dailyRent]: deducted at end-of-day
 * - [shelfCapacity]: total items that can sit on shelves
 * - [backroomCapPerItem]: max CASE PACKS of a single item in backroom (e.g., 2, 5, 10)
 *   This represents how many full case packs can be stored, not individual units.
 * - [trafficMultiplier]: multiplier applied to base customer traffic rate (3x per tier)
 */
enum class StoreSize(
    val displayName: String,
    val dailyRent: Money,
    val shelfCapacity: Int,
    val backroomCapPerItem: Int,  // In CASE PACKS, not units
    val trafficMultiplier: Float,  // Customer traffic multiplier
    val basketSizeMultiplier: Float,  // Basket size multiplier (+25% per tier)
    val upgradeCost: Money? = null,  // null for starting size
    /** Maximum number of registers that can be owned at this store size. */
    val maxRegisters: Int,
) {
    MOM_AND_POP(
        displayName = "Mom & Pop Store",
        dailyRent = Money(10_000),      // $100/day
        shelfCapacity = 2_000,
        backroomCapPerItem = 2,          // 2 case packs per item
        trafficMultiplier = 1.0f,        // Baseline traffic
        basketSizeMultiplier = 1.0f,     // Baseline basket
        upgradeCost = null,              // Starting size
        maxRegisters = 1
    ),
    SMALL_GROCERY(
        displayName = "Small Grocery",
        dailyRent = Money(30_000),      // $300/day
        shelfCapacity = 5_000,
        backroomCapPerItem = 5,          // 5 case packs per item (2.5x)
        trafficMultiplier = 2.0f,        // 2× traffic
        basketSizeMultiplier = 1.25f,    // +25% basket
        upgradeCost = Money(100_000),    // $1,000 to upgrade
        maxRegisters = 2
    ),
    GROCERY_STORE(
        displayName = "Grocery Store",
        dailyRent = Money(80_000),      // $800/day
        shelfCapacity = 10_000,
        backroomCapPerItem = 10,         // 10 case packs per item (5x)
        trafficMultiplier = 4.0f,        // 4× traffic (3² = 3×3)
        basketSizeMultiplier = 1.5f,     // +50% basket
        upgradeCost = Money(2_000_000),  // $20,000 to upgrade
        maxRegisters = 3
    ),
    SUPERSTORE(
        displayName = "Superstore",
        dailyRent = Money(150_000),     // $1,500/day
        shelfCapacity = 20_000,
        backroomCapPerItem = 30,         // 30 case packs per item (15x)
        trafficMultiplier = 8.0f,       // 8× traffic (3³ = 9×3)
        basketSizeMultiplier = 1.75f,    // +75% basket
        upgradeCost = Money(20_000_000), // $200,000 to upgrade
        maxRegisters = 5
    ),
    SUPERCENTER(
        displayName = "Supercenter",
        dailyRent = Money(300_000),     // $3,000/day
        shelfCapacity = 40_000,
        backroomCapPerItem = 999,        // Effectively unlimited case packs
        trafficMultiplier = 16.0f,       // 16× traffic (3⁴ = 27×3)
        basketSizeMultiplier = 2.0f,     // +100% basket
        upgradeCost = Money(100_000_000), // $1,000,000 to upgrade
        maxRegisters = 8
    );

    companion object {
        val ordered = entries.sortedBy { it.ordinal }

        fun nextSize(current: StoreSize): StoreSize? {
            val idx = ordered.indexOf(current)
            return if (idx >= 0 && idx < ordered.size - 1) ordered[idx + 1] else null
        }

        /**
         * Cost to purchase one additional register.
         * [ownedCount] is the number already owned (before this purchase).
         *
         *  1 → 2 : $200
         *  2 → 3 : $500
         *  3 → 4 : $1,000
         *  4+    : $2,000 each
         */
        fun nextRegisterCost(ownedCount: Int): Money = when (ownedCount) {
            1    -> Money(20_000L)   // $200
            2    -> Money(50_000L)   // $500
            3    -> Money(100_000L)  // $1,000
            else -> Money(200_000L)  // $2,000
        }
    }
}

