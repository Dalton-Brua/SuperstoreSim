package com.example.superstoresimulator.di

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.superstoresimulator.domain.Transactions.TransactionDao
import com.example.superstoresimulator.domain.Transactions.TransactionEntity
import com.example.superstoresimulator.domain.Transactions.TransactionLineEntity
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemDao

@Database(
    entities = [Item::class, TransactionEntity::class, TransactionLineEntity::class],
    version = 9,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun transactionDao(): TransactionDao
}
