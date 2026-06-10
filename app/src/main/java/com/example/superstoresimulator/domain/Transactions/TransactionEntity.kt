package com.example.superstoresimulator.domain.Transactions

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index("gameDayNumber")]
)
data class TransactionEntity(
    @PrimaryKey val id: Int,
    val subtotalCents: Long,
    val taxCents: Long,
    val totalEarnedCents: Long,
    val completedAtMillis: Long?,
    val registerId: Int,
    val gameDayNumber: Int,
)
