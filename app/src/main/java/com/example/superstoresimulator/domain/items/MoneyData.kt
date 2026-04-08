package com.example.superstoresimulator.domain.items

import androidx.room.ColumnInfo
import com.example.superstoresimulator.domain.Money

/**
 * Data class for storing Money in Room database using only primitives.
 * Room will store the cents value and automatically handle conversion.
 */
data class MoneyData(
    @ColumnInfo(name = "cents_value")
    val cents: Long = 0
) {
    fun toMoney(): Money = Money(cents)
    
    companion object {
        fun fromMoney(money: Money): MoneyData = MoneyData(money.cents)
    }
}

