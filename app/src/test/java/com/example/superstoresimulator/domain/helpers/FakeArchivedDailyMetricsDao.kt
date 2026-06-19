package com.example.superstoresimulator.domain.helpers

import com.example.superstoresimulator.domain.metrics.ArchivedDailyMetricsDao
import com.example.superstoresimulator.domain.metrics.ArchivedDailyMetricsEntity
import com.example.superstoresimulator.domain.metrics.ArchivedDailyMetricsSummary

class FakeArchivedDailyMetricsDao : ArchivedDailyMetricsDao {
    val inserted = mutableListOf<ArchivedDailyMetricsEntity>()

    override suspend fun insertAll(metrics: List<ArchivedDailyMetricsEntity>) {
        inserted.addAll(metrics)
    }

    override suspend fun getDay(dayNumber: Int): ArchivedDailyMetricsEntity? =
        inserted.firstOrNull { it.dayNumber == dayNumber }

    override suspend fun getAllSummaries(): List<ArchivedDailyMetricsSummary> =
        inserted.map {
            ArchivedDailyMetricsSummary(
                dayNumber = it.dayNumber,
                dayOfWeek = it.dayOfWeek,
                subtotalCents = it.subtotalCents,
                taxCollectedCents = it.taxCollectedCents,
                transactionsCompleted = it.transactionsCompleted,
                rentPaidCents = it.rentPaidCents,
                wagesPaidCents = it.wagesPaidCents,
                refundsProcessed = it.refundsProcessed,
                refundAmountCents = it.refundAmountCents,
                customersServed = it.customersServed,
                itemsSold = it.itemsSold,
                itemsStocked = it.itemsStocked,
                itemsOrdered = it.itemsOrdered,
                lostRevenueCents = it.lostRevenueCents,
                itemsLostToOutOfStock = it.itemsLostToOutOfStock,
                itemsExpired = it.itemsExpired,
                expiredWasteCostCents = it.expiredWasteCostCents,
                avgCashierUtilization = it.avgCashierUtilization,
                avgStockerUtilization = it.avgStockerUtilization,
                avgFreshUtilization = it.avgFreshUtilization,
                avgZoneScore = it.avgZoneScore,
                markdownsSavedCents = it.markdownsSavedCents,
                markupExtraRevenueCents = it.markupExtraRevenueCents,
                itemsMarkedDown = it.itemsMarkedDown,
                vendorCommissionPaidCents = it.vendorCommissionPaidCents,
                vendorItemsSold = it.vendorItemsSold,
                vendorRevenueCents = it.vendorRevenueCents,
            )
        }

    override suspend fun count(): Int = inserted.size

    override suspend fun deleteAll() {
        inserted.clear()
    }
}
