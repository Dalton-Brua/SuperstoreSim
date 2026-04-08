package com.example.superstoresimulator.di

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.superstoresimulator.domain.items.Item
import com.example.superstoresimulator.domain.items.ItemDao

@Database(entities = [Item::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
}