package com.example.superstoresimulator.domain

import kotlinx.serialization.Serializable

@Serializable
data class RefundLine(
    val itemId: Int,
    val quantity: Int, // positive number of items to refund
    val unitPrice: Money
)

@Serializable
data class RefundRequest(
    val id: Int,
    val timestamp: Long,
    val originalTransactionId: Int,
    val lines: List<RefundLine>,
    val subtotal: Money,
    val tax: Money
)

