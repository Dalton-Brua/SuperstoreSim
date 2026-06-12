package com.example.superstoresimulator.domain.vendor

import com.example.superstoresimulator.domain.Money

data class CommissionTier(
    val minReputation: Int,
    val commissionPercent: Int,
    val restockIntervalDays: Int,
    val tierName: String,
)

data class VendorDef(
    val vendorId: String,
    val vendorName: String,
)

object VendorConfig {
    const val MAX_REPUTATION = 100
    const val UNITS_PER_REP_POINT = 10

    val COMMISSION_TIERS = listOf(
        CommissionTier(minReputation = 0, commissionPercent = 70, restockIntervalDays = 5, tierName = "New"),
        CommissionTier(minReputation = 25, commissionPercent = 60, restockIntervalDays = 4, tierName = "Familiar"),
        CommissionTier(minReputation = 50, commissionPercent = 50, restockIntervalDays = 3, tierName = "Trusted"),
        CommissionTier(minReputation = 75, commissionPercent = 40, restockIntervalDays = 2, tierName = "Preferred"),
        CommissionTier(minReputation = 100, commissionPercent = 30, restockIntervalDays = 1, tierName = "Partner"),
    )

    val VENDORS = listOf(
        VendorDef(vendorId = "vendor_freshbake", vendorName = "FreshBake Co."),
        VendorDef(vendorId = "vendor_snackco", vendorName = "SnackCo"),
        VendorDef(vendorId = "vendor_bevcorp", vendorName = "BevCorp"),
    )

    val VENDOR_TIER_UNLOCK_COSTS = listOf(
        Money.ZERO,
        Money(200_000),
        Money(800_000),
        Money(2_500_000),
    )

    val INVESTMENT_COSTS = listOf(
        Money(50_000) to 24,
        Money(100_000) to 49,
        Money(200_000) to 74,
        Money(400_000) to 99,
    )
    const val INVESTMENT_REP_GAIN = 5

    const val RESTOCK_CASE_PACK_MULTIPLIER = 2

    fun getCommissionTier(reputation: Int): CommissionTier =
        COMMISSION_TIERS.lastOrNull { reputation >= it.minReputation } ?: COMMISSION_TIERS.first()

    fun getCommissionRate(reputation: Int): Int =
        getCommissionTier(reputation).commissionPercent

    fun getRestockInterval(reputation: Int): Int =
        getCommissionTier(reputation).restockIntervalDays

    fun getInvestmentCost(reputation: Int): Money =
        INVESTMENT_COSTS.firstOrNull { reputation <= it.second }?.first ?: INVESTMENT_COSTS.last().first
}
