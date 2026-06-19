package com.example.superstoresimulator.domain.metrics

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArchivedDailyMetricsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(metrics: List<ArchivedDailyMetricsEntity>)

    @Query("SELECT * FROM archived_daily_metrics WHERE dayNumber = :dayNumber")
    suspend fun getDay(dayNumber: Int): ArchivedDailyMetricsEntity?

    @Query(
        """SELECT dayNumber, dayOfWeek, subtotalCents, taxCollectedCents, transactionsCompleted,
        rentPaidCents, wagesPaidCents, refundsProcessed, refundAmountCents, customersServed,
        itemsSold, itemsStocked, itemsOrdered, lostRevenueCents, itemsLostToOutOfStock,
        itemsExpired, expiredWasteCostCents, avgCashierUtilization, avgStockerUtilization,
        avgFreshUtilization, avgZoneScore, markdownsSavedCents, markupExtraRevenueCents,
        itemsMarkedDown, vendorCommissionPaidCents, vendorItemsSold, vendorRevenueCents
        FROM archived_daily_metrics ORDER BY dayNumber DESC"""
    )
    suspend fun getAllSummaries(): List<ArchivedDailyMetricsSummary>

    @Query("SELECT COUNT(*) FROM archived_daily_metrics")
    suspend fun count(): Int

    @Query("DELETE FROM archived_daily_metrics")
    suspend fun deleteAll()
}
