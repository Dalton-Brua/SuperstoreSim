package com.example.superstoresimulator.di

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.superstoresimulator.domain.Transactions.TransactionDao
import com.example.superstoresimulator.domain.Transactions.TransactionEntity
import com.example.superstoresimulator.domain.Transactions.TransactionLineEntity
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemDao
import com.example.superstoresimulator.domain.metrics.ArchivedDailyMetricsDao
import com.example.superstoresimulator.domain.metrics.ArchivedDailyMetricsEntity

@Database(
    entities = [Item::class, TransactionEntity::class, TransactionLineEntity::class, ArchivedDailyMetricsEntity::class],
    version = 11,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun transactionDao(): TransactionDao
    abstract fun archivedDailyMetricsDao(): ArchivedDailyMetricsDao
}
