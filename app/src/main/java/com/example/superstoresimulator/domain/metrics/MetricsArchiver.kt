package com.example.superstoresimulator.domain.metrics

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetricsArchiver @Inject constructor(
    private val dao: ArchivedDailyMetricsDao,
) {
    companion object {
        const val IN_MEMORY_DAYS = 14
        const val ARCHIVE_THRESHOLD = 21
    }

    @Volatile
    var pendingTrimCount: Int = 0
        private set

    @Volatile
    var summariesDirty: Boolean = false
        private set

    suspend fun archiveIfNeeded(completedDayMetrics: List<DailyMetrics>) {
        if (completedDayMetrics.size <= ARCHIVE_THRESHOLD) return
        val toArchive = completedDayMetrics.dropLast(IN_MEMORY_DAYS)
        dao.insertAll(toArchive.map { it.toEntity() })
        pendingTrimCount = toArchive.size
        summariesDirty = true
    }

    fun consumeSummariesDirty(): Boolean {
        val dirty = summariesDirty
        summariesDirty = false
        return dirty
    }

    fun consumeTrim(): Int {
        val count = pendingTrimCount
        pendingTrimCount = 0
        return count
    }

    suspend fun loadArchivedDay(dayNumber: Int): DailyMetrics? =
        dao.getDay(dayNumber)?.toDailyMetrics()

    suspend fun loadAllSummaries(): List<DailyMetrics> =
        dao.getAllSummaries().map { it.toDailyMetrics() }

    suspend fun migrateExistingSave(allDays: List<DailyMetrics>): Int {
        if (allDays.size <= IN_MEMORY_DAYS) return 0
        val toArchive = allDays.dropLast(IN_MEMORY_DAYS)
        dao.insertAll(toArchive.map { it.toEntity() })
        return toArchive.size
    }

    suspend fun archivedCount(): Int = dao.count()

    suspend fun clearAll() = dao.deleteAll()
}
