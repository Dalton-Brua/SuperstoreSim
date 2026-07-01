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

    // ponytail: SELECT * decodes the JSON event lists too; add a column-list projection back if that decode measurably hurts the history list.
    @Query("SELECT * FROM archived_daily_metrics ORDER BY dayNumber DESC")
    suspend fun getAllSummaries(): List<ArchivedDailyMetricsEntity>

    @Query("DELETE FROM archived_daily_metrics")
    suspend fun deleteAll()
}
