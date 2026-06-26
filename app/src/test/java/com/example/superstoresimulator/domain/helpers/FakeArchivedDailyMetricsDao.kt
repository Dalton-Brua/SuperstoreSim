package com.example.superstoresimulator.domain.helpers

import com.example.superstoresimulator.domain.metrics.ArchivedDailyMetricsDao
import com.example.superstoresimulator.domain.metrics.ArchivedDailyMetricsEntity

class FakeArchivedDailyMetricsDao : ArchivedDailyMetricsDao {
    val inserted = mutableListOf<ArchivedDailyMetricsEntity>()

    override suspend fun insertAll(metrics: List<ArchivedDailyMetricsEntity>) {
        inserted.addAll(metrics)
    }

    override suspend fun getDay(dayNumber: Int): ArchivedDailyMetricsEntity? =
        inserted.firstOrNull { it.dayNumber == dayNumber }

    // Matches the real query's ORDER BY dayNumber DESC.
    override suspend fun getAllSummaries(): List<ArchivedDailyMetricsEntity> =
        inserted.sortedByDescending { it.dayNumber }

    override suspend fun deleteAll() {
        inserted.clear()
    }
}
