package com.example.superstoresimulator.domain.Transactions

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_lines",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("transactionId"), Index("itemId")]
)
data class TransactionLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Int,
    val itemId: Int,
    val quantity: Int,
    val rungQty: Int,
    val unitPriceCents: Long,
    val lineTotalCents: Long,
    val lostToOutOfStock: Boolean,
    val basePriceCents: Long,
    val priceModifier: Int,
    val weight: Float?,
)
