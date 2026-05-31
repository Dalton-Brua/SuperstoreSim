package com.example.superstoresimulator.domain.Transactions

import com.example.superstoresimulator.domain.Money
import java.time.Instant

data class Transaction(
    val id: Int,
    val lines: List<TransactionLine>,
    val subtotal: Money,
    val tax: Money,
    val totalEarned: Money,
    val completedAt: Instant? = null,
    val registerId: Int = 0,
    val gameDayNumber: Int = 0,
) {
    constructor(): this(0, emptyList(), Money(0), Money(0), Money(0))
}
data class TransactionLine(
    val itemId: Int,
    val quantity: Int,
    val rungQty: Int,
    val unitPrice: Money,
    val lineTotal: Money,
    /** True when the cashier could not ring this line because shelf stock was zero. */
    val lostToOutOfStock: Boolean = false,
) {
    constructor(id: Int, quantity: Int, price: Money) : this(
        itemId = id,
        quantity = quantity,
        unitPrice = price,
        lineTotal = price * quantity,
        rungQty = quantity,
    )
}
