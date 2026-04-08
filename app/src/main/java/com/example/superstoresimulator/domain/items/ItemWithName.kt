package com.example.superstoresimulator.domain.items

import androidx.room.ColumnInfo

/**
 * Simple data class for mapping item id and name from database queries.
 */
data class ItemWithName(
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String
)

