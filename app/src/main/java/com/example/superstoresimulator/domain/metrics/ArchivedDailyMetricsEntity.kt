package com.example.superstoresimulator.domain.metrics

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "archived_daily_metrics")
data class ArchivedDailyMetricsEntity(
    @PrimaryKey val dayNumber: Int,
    val dayOfWeek: Int = 0,

    // Sales
    val subtotalCents: Long = 0,
    val taxCollectedCents: Long = 0,
    val transactionsCompleted: Int = 0,

    // Operating costs
    val rentPaidCents: Long = 0,
    val wagesPaidCents: Long = 0,

    // Refunds
    val refundsProcessed: Int = 0,
    val refundAmountCents: Long = 0,

    // Customers
    val customersServed: Int = 0,

    // Inventory
    val itemsSold: Int = 0,
    val itemsStocked: Int = 0,
    val itemsOrdered: Int = 0,

    // Out-of-stock
    val lostRevenueCents: Long = 0,
    val itemsLostToOutOfStock: Int = 0,

    // Expiration
    val itemsExpired: Int = 0,
    val expiredWasteCostCents: Long = 0,

    // Staff utilization
    val avgCashierUtilization: Float = 0f,
    val avgStockerUtilization: Float = 0f,
    val avgFreshUtilization: Float = 0f,
    val avgZoneScore: Float = 1f,

    // Pricing
    val markdownsSavedCents: Long = 0,
    val markupExtraRevenueCents: Long = 0,
    val itemsMarkedDown: Int = 0,

    // Vendor
    val vendorCommissionPaidCents: Long = 0,
    val vendorItemsSold: Int = 0,
    val vendorRevenueCents: Long = 0,

    // Event lists stored as JSON strings
    val soldItemEventsJson: String = "[]",
    val outOfStockEventsJson: String = "[]",
    val expiredItemEventsJson: String = "[]",
    val autoOrderedFreshItemsJson: String = "[]",
    val incompleteOrderedFreshItemsJson: String = "[]",
    val autoOrderedNormalItemsJson: String = "[]",
    val incompleteOrderedNormalItemsJson: String = "[]",
    val deliveredTrucksJson: String = "[]",
    val autoHireEventsJson: String = "[]",
)

data class ArchivedDailyMetricsSummary(
    val dayNumber: Int,
    val dayOfWeek: Int,
    val subtotalCents: Long,
    val taxCollectedCents: Long,
    val transactionsCompleted: Int,
    val rentPaidCents: Long,
    val wagesPaidCents: Long,
    val refundsProcessed: Int,
    val refundAmountCents: Long,
    val customersServed: Int,
    val itemsSold: Int,
    val itemsStocked: Int,
    val itemsOrdered: Int,
    val lostRevenueCents: Long,
    val itemsLostToOutOfStock: Int,
    val itemsExpired: Int,
    val expiredWasteCostCents: Long,
    val avgCashierUtilization: Float,
    val avgStockerUtilization: Float,
    val avgFreshUtilization: Float,
    val avgZoneScore: Float,
    val markdownsSavedCents: Long,
    val markupExtraRevenueCents: Long,
    val itemsMarkedDown: Int,
    val vendorCommissionPaidCents: Long,
    val vendorItemsSold: Int,
    val vendorRevenueCents: Long,
)
